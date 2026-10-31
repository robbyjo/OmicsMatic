/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.RepeatAnnotations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatMaskAuditTest {
    @TempDir Path directory;
    private int serial;
    private Path file(String text) throws IOException { Path path = directory.resolve("fasta-" + serial++); Files.writeString(path, text); return path; }
    private static RepeatAnnotations.Hit hit(String query, int start, int end, int length, String name, String kind, boolean marked) {
        return RepeatAnnotations.parseRow("50 0 0 0 " + query + " " + (start + 1) + " " + end + " (" + (length - end)
                + ") + " + name + " " + kind + " 1 " + (end - start) + " (0) 1" + (marked ? " *" : ""), "fixture.out", 1, RepeatAnnotations.Format.OUT_WITH_ID);
    }
    private RepeatMaskAudit.Report audit(Path original, Path masked, List<RepeatAnnotations.Hit> hits, RepeatMaskAudit.Selection selection, RepeatAnnotations.Mask mode) throws IOException {
        return RepeatMaskAudit.compare(original, RepeatFasta.Encoding.PLAIN_UTF8, masked, RepeatFasta.Encoding.PLAIN_UTF8,
                hits, selection, mode, RepeatFastaTest.limits(), 1048576, () -> { });
    }
    @Test void everyModeUsesAnExactOrderIndependentUnionAndRetainsNegativeQueries() throws Exception {
        Path input = file(">q description\nAcGTnnACGT\n>zero\nAcgT\n");
        var hits = List.of(hit("q", 5, 9, 10, "B", "SINE/Alu", false), hit("q", 1, 7, 10, "A", "DNA/hAT", true));
        for (var mode : RepeatAnnotations.Mask.values()) {
            String expected = mode == RepeatAnnotations.Mask.SOFT ? "AcgtnnacgT" : mode == RepeatAnnotations.Mask.N ? "ANNNNNNNNT" : "AXXXXXXXXT";
            var report = audit(input, file(">q description\n" + expected.substring(0, 3) + "\n" + expected.substring(3) + "\n>zero\nAcgT\n"), hits, RepeatMaskAudit.Selection.all(), mode);
            assertEquals(14, report.comparedBases()); assertEquals(8, report.coveredBases()); assertEquals(1, report.unionIntervals());
            assertEquals(0, report.sequences().get(1).coveredBases()); assertEquals(2, report.selectedRows());
        }
    }
    @Test void classNameAndMarkedOverlapFiltersSelectAnExplicitIntersection() throws Exception {
        Path input = file(">q\nAAAAAAAAAA\n");
        var hits = List.of(hit("q", 0, 2, 10, "wanted", "SINE/Alu", false), hit("q", 3, 5, 10, "other", "SINE/Alu", false),
                hit("q", 6, 8, 10, "wanted", "LINE/L1", false), hit("q", 8, 10, 10, "wanted", "SINE/Alu", true));
        var selected = new RepeatMaskAudit.Selection(RepeatMaskAudit.Choice.EXACT, Set.of("SINE"), RepeatMaskAudit.Choice.EXACT, Set.of("wanted"), RepeatMaskAudit.MarkedOverlap.EXCLUDE);
        var report = audit(input, file(">q\nNNAAAAAAAA\n"), hits, selected, RepeatAnnotations.Mask.N);
        assertEquals(1, report.selectedRows()); assertEquals(2, report.coveredBases());
        assertThrows(IllegalArgumentException.class, () -> new RepeatMaskAudit.Selection(RepeatMaskAudit.Choice.ALL, Set.of("SINE"), RepeatMaskAudit.Choice.ALL, Set.of(), RepeatMaskAudit.MarkedOverlap.INCLUDE));
    }
    @Test void headerDescriptionOrderMissingAndExtraQueryRecordsFail() throws Exception {
        Path input = file(">a description\nAC\n>b\nGT\n");
        for (String invalid : List.of(">a altered\nAC\n>b\nGT\n", ">b\nGT\n>a description\nAC\n", ">a description\nAC\n", ">a description\nAC\n>b\nGT\n>extra\nA\n")) {
            Path masked = file(invalid); assertThrows(IOException.class, () -> audit(input, masked, List.of(), RepeatMaskAudit.Selection.all(), RepeatAnnotations.Mask.N));
        }
    }
    @Test void wrongMaskAndTruncatedOrExtraBasesFailWithoutAdmittingAPrefix() throws Exception {
        Path input = file(">q\nACGT\n"); var hits = List.of(hit("q", 1, 3, 4, "R", "DNA/hAT", false));
        for (String invalid : List.of("ANGT", "ANN", "ANNTA", "annt")) {
            Path masked = file(">q\n" + invalid + "\n"); assertThrows(IOException.class, () -> audit(input, masked, hits, RepeatMaskAudit.Selection.all(), RepeatAnnotations.Mask.N));
        }
    }
    @Test void everyAnnotationHasAnExactKnownQueryBoundAndConsistentLengthHintEvenIfFiltered() throws Exception {
        Path input = file(">q\nACGT\n"); Path masked = file(">q\nACGT\n");
        var none = new RepeatMaskAudit.Selection(RepeatMaskAudit.Choice.EXACT, Set.of("absent"), RepeatMaskAudit.Choice.ALL, Set.of(), RepeatMaskAudit.MarkedOverlap.INCLUDE);
        for (var invalid : List.of(hit("Q", 0, 2, 4, "R", "DNA/hAT", false), hit("q", 0, 5, 5, "R", "DNA/hAT", false), hit("q", 0, 2, 5, "R", "DNA/hAT", false)))
            assertThrows(IOException.class, () -> audit(input, masked, List.of(invalid), none, RepeatAnnotations.Mask.N));
    }
    @Test void zeroRowsStillRequireCompleteUnchangedSequences() throws Exception {
        Path input = file(">a\nNnAC\n>b\nTT\n");
        var report = audit(input, file(">a\nNnAC\n>b\nTT\n"), List.of(), RepeatMaskAudit.Selection.all(), RepeatAnnotations.Mask.X);
        assertEquals(6, report.comparedBases()); assertEquals(0, report.coveredBases()); assertEquals(0, report.changedCharacters()); assertEquals(2, report.sequences().size());
    }
    @Test void indexCapacityIsCheckedBeforeAcceptingTheIntervalPopulation() throws Exception {
        Path input = file(">q\nACGT\n");
        assertThrows(IOException.class, () -> RepeatMaskAudit.compare(input, RepeatFasta.Encoding.PLAIN_UTF8, input, RepeatFasta.Encoding.PLAIN_UTF8,
                List.of(hit("q", 0, 4, 4, "R", "DNA/hAT", false)), RepeatMaskAudit.Selection.all(), RepeatAnnotations.Mask.SOFT,
                RepeatFastaTest.limits(), 65536, () -> { }));
    }
    @Test void cancellationIsAVisibleFailure() throws Exception {
        Path input = file(">q\nACGT\n");
        assertThrows(CancellationException.class, () -> RepeatMaskAudit.compare(input, RepeatFasta.Encoding.PLAIN_UTF8, input, RepeatFasta.Encoding.PLAIN_UTF8,
                List.of(), RepeatMaskAudit.Selection.all(), RepeatAnnotations.Mask.SOFT, RepeatFastaTest.limits(), 1048576, () -> { throw new CancellationException("stop"); }));
    }
    @Test void changingAnAlreadyMaskedSourceBaseStillFailsTheWholeSourceFence() throws Exception {
        Path input = file(">q\nACGT\n"), masked = file(">q\nNNNN\n"); var baseline = new AtomicInteger();
        RepeatFasta.read(input, RepeatFasta.Encoding.PLAIN_UTF8, RepeatFastaTest.limits(), baseline::incrementAndGet);
        var calls = new AtomicInteger();
        IOException failure = assertThrows(IOException.class, () -> RepeatMaskAudit.compare(input, RepeatFasta.Encoding.PLAIN_UTF8, masked, RepeatFasta.Encoding.PLAIN_UTF8,
                List.of(hit("q", 0, 4, 4, "R", "DNA/hAT", false)), RepeatMaskAudit.Selection.all(), RepeatAnnotations.Mask.N, RepeatFastaTest.limits(), 1048576, () -> {
                    if (calls.incrementAndGet() == baseline.get() + 1) {
                        try { Files.writeString(input, ">q\nCCGT\n"); } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
                    }
                }));
        assertTrue(failure.getMessage().contains("changed between"), failure.toString());
    }
    @Test void independentRandomBooleanCoverageOracleMatchesAllThreeModes() throws Exception {
        var random = new Random(712940);
        for (int population = 0; population < 40; population++) {
            StringBuilder source = new StringBuilder(); for (int i = 0; i < 300; i++) source.append("AaCcGgTtNn".charAt(random.nextInt(10)));
            boolean[] covered = new boolean[300]; var hits = new ArrayList<RepeatAnnotations.Hit>();
            for (int row = 0; row < 30; row++) {
                int begin = random.nextInt(300), end = begin + 1 + random.nextInt(300 - begin);
                hits.add(hit("q", begin, end, 300, "R" + row, "DNA/hAT", row % 3 == 0));
                for (int i = begin; i < end; i++) covered[i] = true;
            }
            Path original = file(">q oracle\n" + source + "\n");
            for (var mode : RepeatAnnotations.Mask.values()) {
                StringBuilder expected = new StringBuilder(source); long count = 0, changed = 0;
                for (int i = 0; i < 300; i++) if (covered[i]) {
                    count++; char base = mode == RepeatAnnotations.Mask.N ? 'N' : mode == RepeatAnnotations.Mask.X ? 'X' : Character.toLowerCase(source.charAt(i));
                    if (base != source.charAt(i)) changed++; expected.setCharAt(i, base);
                }
                var result = audit(original, file(">q oracle\n" + expected + "\n"), hits, RepeatMaskAudit.Selection.all(), mode);
                assertEquals(count, result.coveredBases()); assertEquals(changed, result.changedCharacters()); assertEquals(300, result.comparedBases());
            }
        }
    }
}
