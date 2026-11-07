/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonMetadataTest {
    @Test void exactOriginalNoDecoySentinelIsRetainedBeyondSignedLong() throws Exception {
        byte[] raw = original("no-decoys/index/info.json"); var metadata = SalmonMetadata.parse(new String(raw, StandardCharsets.UTF_8), 8192, 1000);
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), SalmonMetadata.unsigned(metadata.get("first_decoy_index")));
        assertEquals(0, SalmonMetadata.integer(metadata.get("num_decoys"), Long.MAX_VALUE));
        assertTrue(NextflowJson.json(metadata).contains("18446744073709551615"));
        assertThrows(IOException.class, () -> SalmonMetadata.integer(metadata.get("first_decoy_index"), Long.MAX_VALUE));
    }
    @ParameterizedTest @ValueSource(strings={"0", "4294967295", "9007199254740993", "9223372036854775808", "18446744073709551615"})
    void integersAreNeverRoundedThroughDouble(String token) throws Exception {
        var metadata = SalmonMetadata.parse("{\"n\":" + token + "}", 1000, 10); assertEquals(new BigInteger(token), SalmonMetadata.unsigned(metadata.get("n")));
        assertEquals("{\"n\":" + token + "}", NextflowJson.json(metadata));
    }
    @ParameterizedTest @ValueSource(strings={"-1", "-0", "1.0", "1e2", "18446744073709551616", "true", "null", "\"12\""})
    void unsignedMetadataHasItsExactSourceTypeAndRange(String token) throws Exception {
        var metadata = SalmonMetadata.parse("{\"n\":" + token + "}", 1000, 10); assertThrows(IOException.class, () -> SalmonMetadata.unsigned(metadata.get("n")));
    }
    @Test void tinyDecimalsAndNegativeZeroTokensRemainLossless() throws Exception {
        var metadata = SalmonMetadata.parse("{\"tiny\":1.2300e-400,\"zero\":-0.000}", 1000, 10);
        assertEquals(new BigDecimal("1.2300e-400"), SalmonMetadata.decimal(metadata.get("tiny")));
        assertEquals("{\"tiny\":1.2300e-400,\"zero\":-0.000}", NextflowJson.json(metadata));
    }
    @Test void exactSignedLongBoundsAreEnforcedWithoutWrapping() throws Exception {
        var metadata = SalmonMetadata.parse("{\"n\":9223372036854775807}", 1000, 10);
        assertEquals(Long.MAX_VALUE, SalmonMetadata.integer(metadata.get("n"), Long.MAX_VALUE));
        assertThrows(IOException.class, () -> SalmonMetadata.integer(metadata.get("n"), Long.MAX_VALUE - 1));
    }
    @ParameterizedTest @ValueSource(strings={"{\"n\":1,\"n\":2}", "{\"n\":NaN}", "{\"n\":01}", "{\"n\":+1}", "{\"n\":1}tail", "[1,2]"})
    void duplicateMalformedOrNonobjectMetadataIsRejected(String raw) { assertThrows(IOException.class, () -> SalmonMetadata.parse(raw, 1000, 100)); }
    @Test void metadataCapacityIsExplicit() {
        assertThrows(IOException.class, () -> SalmonMetadata.parse("{\"n\":1}", 2, 100));
        assertThrows(IOException.class, () -> SalmonMetadata.parse("{\"n\":[1,2,3]}", 100, 2));
    }
    @Test void unsupportedDecimalScaleIsNamedRatherThanRoundedToInfinity() throws Exception {
        var metadata = SalmonMetadata.parse("{\"n\":1e999999999999}", 1000, 10); assertThrows(IOException.class, () -> SalmonMetadata.decimal(metadata.get("n")));
    }
    static byte[] original(String member) throws Exception {
        byte[] raw; try (var source = SalmonMetadataTest.class.getResourceAsStream("/org/jlinalg/cli/salmon-index-boundaries/original-boundaries.zip")) { assertNotNull(source); raw = source.readAllBytes(); }
        assertEquals("eeacd48f2531f0dae7952ad18fa0b8fa668b2b0740f15b9468510593fa60c7f5", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        try (var zip = new ZipInputStream(new ByteArrayInputStream(raw))) { for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) if (entry.getName().equals(member)) return zip.readAllBytes(); }
        throw new IOException("Missing original boundary fixture: " + member);
    }
}
