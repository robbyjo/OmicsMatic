/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonReadsTest {
    @TempDir Path temporary;
    private static final FastpFastq.Limits FILE = new FastpFastq.Limits(10000000, 40000000, 100000, 10000000, 10000, 1024);
    private static final SalmonReads.Limits LIMITS = new SalmonReads.Limits(FILE, 20000000, 20000000);
    @Test void lowercaseIupacAndRawQualityBytesAreNeverRewrittenOrInterpreted() throws Exception {
        String raw = "@read description\nacguNRYX\n+other identity\n!@ABCDEFG\n".replace("!@ABCDEFG", "!@ABCDEF"); Path input = file("reads.fq", raw);
        var result = inspect(input, null, SalmonReads.Pairing.EXACT_SOURCE_TOKEN);
        assertEquals(1, result.firstStats().reads()); assertEquals(8, result.firstStats().bases()); assertEquals(1, result.firstStats().nonemptyPlusLines());
        assertEquals(NextflowFiles.sha(raw.getBytes(StandardCharsets.UTF_8)), result.firstPin().sha256()); assertEquals(raw, Files.readString(input));
        assertTrue(result.interpretation().get("quality_strings").toString().contains("no Phred")); assertFalse((Boolean) result.interpretation().get("biological_mate_identity_certified"));
    }
    @Test void terminalMateLabelsAndCompleteOrderAreVerified() throws Exception {
        Path first = file("r1.fq", record("x/1", "ACGT") + record("y/1", "TGCA")), second = file("r2.fq", record("x/2", "TGCA") + record("y/2", "ACGT"));
        var result = inspect(first, second, SalmonReads.Pairing.TERMINAL_MATE_SUFFIX); assertTrue(result.mateIdentityRuleVerified()); assertEquals(2, result.secondStats().reads());
    }
    @ParameterizedTest @ValueSource(strings={"reordered", "missing", "extra", "reversed-roles", "inconsistent-role"})
    void pairedDataCannotPassWithMissingExtraReorderedOrWrongRoleRecords(String condition) throws Exception {
        Path first = file("r1.fq", record("x/1", "ACGT") + record("y/1", "TGCA")); String raw = switch (condition) {
            case "reordered" -> record("y/2", "TGCA") + record("x/2", "ACGT");
            case "missing" -> record("x/2", "TGCA");
            case "extra" -> record("x/2", "TGCA") + record("y/2", "ACGT") + record("z/2", "ACGT");
            case "reversed-roles" -> record("x/1", "TGCA") + record("y/1", "ACGT");
            default -> record("x", "TGCA") + record("y/2", "ACGT");
        }; Path second = file("r2.fq", raw); assertThrows(IOException.class, () -> inspect(first, second, SalmonReads.Pairing.TERMINAL_MATE_SUFFIX));
    }
    @Test void casavaRolesRequireTheDeclaredReadOneAndReadTwo() throws Exception {
        Path first = file("r1.fq", record("read 1:N:0:ACGT", "ACGT")), second = file("r2.fq", record("read 2:N:0:ACGT", "TGCA"));
        assertTrue(inspect(first, second, SalmonReads.Pairing.CASAVA_ROLES).mateIdentityRuleVerified()); Files.writeString(second, record("read 1:N:0:ACGT", "TGCA"));
        assertThrows(IOException.class, () -> inspect(first, second, SalmonReads.Pairing.CASAVA_ROLES));
    }
    @Test void unicodeWhitespaceInsideSourceTokensDoesNotCollapseDistinctReadNames() throws Exception {
        Path first = file("r1.fq", record("read\u2003A", "ACGT")), second = file("r2.fq", record("read\u2003B", "TGCA"));
        assertThrows(IOException.class, () -> inspect(first, second, SalmonReads.Pairing.EXACT_SOURCE_TOKEN));
    }
    @Test void sourceOrdinalOnlyPairingRemainsExplicitlyUnverified() throws Exception {
        Path first = file("r1.fq", record("one", "ACGT")), second = file("r2.fq", record("different", "TGCA")); var result = inspect(first, second, SalmonReads.Pairing.SOURCE_ORDINAL_UNVERIFIED);
        assertFalse(result.mateIdentityRuleVerified()); assertTrue((Boolean) result.interpretation().get("source_ordinal_only_warning"));
    }
    @Test void literalSourceSymbolsAreASeparateDeclaredPolicy() throws Exception {
        Path input = file("reads.fq", record("x", "AC-T")); assertThrows(IOException.class, () -> inspect(input, null, SalmonReads.Pairing.EXACT_SOURCE_TOKEN));
        assertEquals(4, SalmonReads.inspect(input, null, LIMITS, SalmonReads.Alphabet.LITERAL_SOURCE_ASCII, SalmonReads.Pairing.EXACT_SOURCE_TOKEN, () -> { }).firstStats().bases());
    }
    @ParameterizedTest @ValueSource(strings={"", "@x\n\n+\n\n", "@x\nACGT\n+\nIII\n", "@x\nACGT\n+\nII I\n", "@x\nACGT\n+\n"})
    void incompleteEmptyAndMalformedQualityPopulationsCannotReturnAContext(String raw) throws Exception {
        Path input = file("invalid.fq", raw); assertThrows(IOException.class, () -> inspect(input, null, SalmonReads.Pairing.EXACT_SOURCE_TOKEN));
    }
    @Test void rawGzipMembersAndPhysicalEofAreFullyChecked() throws Exception {
        var bytes = new ByteArrayOutputStream(); bytes.write(gzip(record("x", "ACGT"))); bytes.write(gzip(record("y", "TGCA"))); Path input = temporary.resolve("gzip-without-extension").toAbsolutePath(); Files.write(input, bytes.toByteArray());
        var result = inspect(input, null, SalmonReads.Pairing.EXACT_SOURCE_TOKEN); assertTrue(result.firstStats().gzip()); assertEquals(2, result.firstStats().reads()); assertEquals(NextflowFiles.sha(bytes.toByteArray()), result.firstPin().sha256());
    }
    @ParameterizedTest @ValueSource(strings={"crc", "trailing"})
    void aValidReadPrefixCannotHideGzipTrailerDamageOrJunk(String fault) throws Exception {
        byte[] raw = gzip(record("x", "ACGT")); if (fault.equals("crc")) raw[raw.length - 8] ^= 1; else raw = java.util.Arrays.copyOf(raw, raw.length + 1);
        Path input = temporary.resolve("invalid.gz").toAbsolutePath(); Files.write(input, raw); assertThrows(IOException.class, () -> inspect(input, null, SalmonReads.Pairing.EXACT_SOURCE_TOKEN));
    }
    @Test void samePhysicalFileCannotSupplyBothMates() throws Exception {
        Path input = file("same.fq", record("x", "ACGT")); assertThrows(IOException.class, () -> inspect(input, input, SalmonReads.Pairing.EXACT_SOURCE_TOKEN));
    }
    @ParameterizedTest @ValueSource(strings={"bytes", "bases"})
    void combinedPopulationCapacityIsEnforced(String dimension) throws Exception {
        Path first = file("r1.fq", record("x", "ACGT")), second = file("r2.fq", record("x", "TGCA"));
        var limits = new SalmonReads.Limits(FILE, dimension.equals("bytes") ? 10 : 20000000, dimension.equals("bases") ? 7 : 20000000);
        assertThrows(IOException.class, () -> SalmonReads.inspect(first, second, limits, SalmonReads.Alphabet.IUPAC_DNA_RNA_OR_X, SalmonReads.Pairing.EXACT_SOURCE_TOKEN, () -> { }));
    }
    @Test void changedReadsInvalidateTheProtectedContext() throws Exception {
        Path input = file("reads.fq", record("x", "ACGT")); var result = inspect(input, null, SalmonReads.Pairing.EXACT_SOURCE_TOKEN); Files.writeString(input, record("y", "ACGT")); assertThrows(IOException.class, () -> result.verify(() -> { }));
    }
    @Test void cancellationNeverYieldsAReadCensus() throws Exception {
        Path input = file("reads.fq", record("x", "ACGT")); assertThrows(CancellationException.class, () -> SalmonReads.inspect(input, null, LIMITS, SalmonReads.Alphabet.IUPAC_DNA_RNA_OR_X, SalmonReads.Pairing.EXACT_SOURCE_TOKEN, () -> { throw new CancellationException(); }));
    }
    @Test void completeOriginalSyntheticPairedPopulationIsAcceptedUnchanged() throws Exception {
        Path first = resource("reads_1.fastq"), second = resource("reads_2.fastq"); var result = inspect(first, second, SalmonReads.Pairing.TERMINAL_MATE_SUFFIX);
        assertEquals(10020, result.firstStats().reads()); assertEquals(10020, result.secondStats().reads()); assertTrue(result.mateIdentityRuleVerified()); result.verify(() -> { });
    }
    private SalmonReads.Context inspect(Path first, Path second, SalmonReads.Pairing pairing) throws IOException { return SalmonReads.inspect(first, second, LIMITS, SalmonReads.Alphabet.IUPAC_DNA_RNA_OR_X, pairing, () -> { }); }
    private Path file(String name, String raw) throws IOException { Path path = temporary.resolve(name).toAbsolutePath(); Files.writeString(path, raw); return path.toRealPath(); }
    private Path resource(String name) throws IOException { Path target = temporary.resolve(name).toAbsolutePath(); try (var input = getClass().getResourceAsStream(SalmonRuntime.RESOURCE + "health-inputs/" + name)) { assertNotNull(input); Files.copy(input, target); } return target.toRealPath(); }
    private static String record(String header, String sequence) { return "@" + header + "\n" + sequence + "\n+\n" + "I".repeat(sequence.length()) + "\n"; }
    private static byte[] gzip(String text) throws IOException { var raw = new ByteArrayOutputStream(); try (var output = new GZIPOutputStream(raw)) { output.write(text.getBytes(StandardCharsets.UTF_8)); } return raw.toByteArray(); }
}
