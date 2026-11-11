/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.jlinalg.sequence.SalmonDecoyAssignment;
import org.jlinalg.sequence.SalmonIndexReferences;
import org.jlinalg.sequence.SalmonQuantification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonDecoyAssignmentTest {
    @ParameterizedTest @ValueSource(strings={"mixed-default", "mixed-keep", "gencode-keep"})
    void completeOriginalRuntimeRowsMatchSourceLedgerWhileDeclaredOmissionsRemainVisible(String name) throws Exception {
        var references = original(name); var info = SalmonMetadata.parse(text(name + "/index/info.json"), 8192, 1000);
        Set<String> fullNames = new HashSet<>(references.references().stream().map(SalmonIndexReferences.Reference::name).toList());
        Set<String> retainedDecoys = new HashSet<>(text(name + "/inputs/decoys.txt").lines().toList()); retainedDecoys.retainAll(fullNames);
        var ledger = SalmonDecoyAssignment.assign(references, (int) SalmonMetadata.integer(info.get("k"), 31), SalmonMetadata.integer(info.get("num_decoys"), 1000),
            SalmonMetadata.unsigned(info.get("first_decoy_index")), retainedDecoys, () -> { });
        assertFalse(ledger.matchesDeclaredPopulation()); assertTrue(ledger.activeBoundaryHasContigId());
        assertEquals(List.of(name.equals("gencode-keep") ? "ENST3.3" : "shortAtK"), ledger.disagreements());
        var meta = SalmonMetadata.parse(text(name + "/quant/aux_info/meta_info.json"), 8192, 1000);
        assertEquals(SalmonMetadata.integer(meta.get("num_decoy_targets"), 1000), ledger.originalRuntimeDecoys());
        var table = SalmonQuantification.read(new StringReader(text(name + "/quant/quant.sf")), ledger.originalRuntimeNondecoys(), 3, 100000, 8192, () -> { });
        var arithmetic = SalmonQuantification.audit(table, 160, () -> { }); assertEquals(160, arithmetic.expectedAssignedFragments());
        assertEquals(SalmonMetadata.integer(meta.get("num_valid_targets"), 1000), table.rows().size());
        assertThrows(IOException.class, () -> SalmonQuantification.read(new StringReader(text(name + "/quant/quant.sf")), ledger.declaredNondecoys(), 3, 100000, 8192, () -> { }));
        assertEquals(ledger.declaredNondecoys().size() - 1, ledger.originalRuntimeNondecoys().size());
    }
    @Test void exactNoDecoySentinelIsInactiveAndNeverCastsToNegativeOne() throws Exception {
        var references = original("no-decoys"); var info = SalmonMetadata.parse(text("no-decoys/index/info.json"), 8192, 1000);
        var boundary = SalmonMetadata.unsigned(info.get("first_decoy_index")); var ledger = SalmonDecoyAssignment.assign(references, 15, 0, boundary, Set.of(), () -> { });
        assertEquals(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), ledger.firstGraphDecoy());
        assertTrue(ledger.matchesDeclaredPopulation()); assertEquals(0, ledger.originalRuntimeDecoys()); assertEquals(2, ledger.originalRuntimeNondecoys().size());
    }
    @Test void aShortNondecoyAtTheBoundaryUsesThePreincrementSourceCounter() throws Exception {
        var ledger = SalmonDecoyAssignment.assign(mixed(), 15, 1, BigInteger.ONE, Set.of("decoy"), () -> { });
        assertEquals(List.of("shortBoundary"), ledger.disagreements());
        assertEquals(List.of("shortFirst", "long"), ledger.originalRuntimeNondecoys().stream().map(SalmonQuantification.Target::name).toList());
        assertEquals(List.of("shortFirst", "long", "shortBoundary"), ledger.declaredNondecoys().stream().map(SalmonQuantification.Target::name).toList());
        assertThrows(UnsupportedOperationException.class, () -> ledger.assignments().clear());
    }
    @Test void noDecoysKeepTheWholeShortReferencePopulation() throws Exception {
        var ledger = SalmonDecoyAssignment.assign(mixed(), 15, 0, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.valueOf(3)), Set.of(), () -> { });
        assertTrue(ledger.matchesDeclaredPopulation()); assertEquals(4, ledger.originalRuntimeNondecoys().size());
    }
    @Test void absentContigIdAtAnActiveDecoyBoundaryRemainsExplicit() throws Exception {
        var refs = summary(List.of(new SalmonIndexReferences.Reference(0, "long", 50, 50, 0), new SalmonIndexReferences.Reference(1, "decoy", 3, 3, -1)), 1);
        var ledger = SalmonDecoyAssignment.assign(refs, 15, 1, BigInteger.ONE, Set.of("decoy"), () -> { }); assertFalse(ledger.activeBoundaryHasContigId()); assertTrue(ledger.matchesDeclaredPopulation());
    }
    @Test void retainedDeclaredNamesMustMatchTheIndexCount() {
        assertThrows(IOException.class, () -> SalmonDecoyAssignment.assign(mixed(), 15, 2, BigInteger.ONE, Set.of("decoy"), () -> { }));
        assertThrows(IOException.class, () -> SalmonDecoyAssignment.assign(mixed(), 15, 1, BigInteger.ONE, Set.of("absent"), () -> { }));
    }
    @Test void boundaryMustMatchTheProtectedDeclaredGraphPopulation() {
        assertThrows(IOException.class, () -> SalmonDecoyAssignment.assign(mixed(), 15, 1, BigInteger.ZERO, Set.of("decoy"), () -> { }));
    }
    @Test void declaredDecoysCannotBeFollowedByNondecoys() {
        assertThrows(IOException.class, () -> SalmonDecoyAssignment.assign(mixed(), 15, 1, BigInteger.ONE, Set.of("long"), () -> { }));
    }
    @Test void kMustAgreeWithTheCompleteShortReferenceIdProjection() {
        assertThrows(IOException.class, () -> SalmonDecoyAssignment.assign(mixed(), 1, 1, BigInteger.ONE, Set.of("decoy"), () -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"-1", "18446744073709551616"})
    void boundaryMustRemainAnExactUint64(String boundary) { assertThrows(IOException.class, () -> SalmonDecoyAssignment.assign(mixed(), 15, 1, new BigInteger(boundary), Set.of("decoy"), () -> { })); }
    @Test void cancellationCannotYieldAPartialLedger() {
        assertThrows(CancellationException.class, () -> SalmonDecoyAssignment.assign(mixed(), 15, 1, BigInteger.ONE, Set.of("decoy"), () -> { throw new CancellationException(); }));
    }
    private static SalmonIndexReferences.Summary mixed() {
        return summary(List.of(new SalmonIndexReferences.Reference(0, "shortFirst", 7, 7, -1), new SalmonIndexReferences.Reference(1, "long", 50, 50, 0),
            new SalmonIndexReferences.Reference(2, "shortBoundary", 9, 9, -1), new SalmonIndexReferences.Reference(3, "decoy", 60, 60, 1)), 2);
    }
    private static SalmonIndexReferences.Summary summary(List<SalmonIndexReferences.Reference> refs, int indexed) {
        var pin = new SalmonIndexReferences.FileReceipt(0, "0".repeat(64)); return new SalmonIndexReferences.Summary(refs, indexed, 0, 0, 262144, pin, pin, pin);
    }
    private static String text(String member) throws Exception { return new String(SalmonMetadataTest.original(member), StandardCharsets.UTF_8); }
    private static SalmonIndexReferences.Summary original(String name) throws Exception {
        return SalmonIndexReferences.read(new ByteArrayInputStream(SalmonMetadataTest.original(name + "/index/ctable.bin")),
            new ByteArrayInputStream(SalmonMetadataTest.original(name + "/index/complete_ref_lens.bin")), new ByteArrayInputStream(SalmonMetadataTest.original(name + "/index/reflengths.bin")),
            new SalmonIndexReferences.Limits(100, 1000, 8192, 100000, 1000000, 16777216), (o, c, f, n, p, fw) -> { }, () -> { });
    }
}
