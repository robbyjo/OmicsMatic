/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RepeatAnnotationsTest {
    private final RepeatAnnotations.Limits limits = new RepeatAnnotations.Limits(1000000, 1000, 10000, 10000000);
    private static final String PLUS = "1264 28.1 13.8 1.8 Human 75513 76236 (923764) + L2a LINE/L2 2401 3210 (216) 102";
    private static final String REVERSE = "207 18.4 18.4 0.0 Human 75437 75485 (924515) C L2c LINE/L2 (48) 3339 3282 101 *";
    private RepeatAnnotations.Hit out(String line) { return RepeatAnnotations.parseRow(line, "source", 1, RepeatAnnotations.Format.OUT_WITH_ID); }
    private String ucsc(boolean bin) { return (bin ? "585\t" : "") + "1012\t246\t43\t4\tchr1\t469\t714\t-274329818\t-\tPre0_SS\tSINE\ttRNA\t-5\t255\t1\t1"; }
    private RepeatAnnotations.Hit hit(String query, int start, int end, int length, int id) {
        return out("100 0.0 0.0 0.0 " + query + " " + (start + 1) + " " + end + " (" + (length - end)
                + ") + R SINE/Alu 1 " + (end - start) + " (0) " + id);
    }
    @Test void documentedForwardAndReverseCoordinatesAndRawFieldsRemainDistinct() {
        var a = out(PLUS); var b = out(REVERSE);
        assertEquals(75512, a.start()); assertEquals(76236, a.end()); assertEquals(1000000, a.queryLengthHint());
        assertEquals(new RepeatAnnotations.Consensus(2400, 3210, 3426), a.consensus());
        assertEquals(75436, b.start()); assertEquals(75485, b.end()); assertEquals("-", b.strand());
        assertEquals(new RepeatAnnotations.Consensus(3281, 3339, 3387), b.consensus());
        assertTrue(b.markedOverlap()); assertEquals("(48)", b.rawFields().get(11)); assertEquals("3282", b.rawFields().get(13));
        assertEquals("101", b.insertionId()); assertEquals(new BigDecimal("18.4"), b.divergencePercent());
    }
    @Test void ucscGenomicCoordinatesAndPerMilleValuesPreserveSignedConsensusSlots() {
        for (boolean bin : List.of(false, true)) {
            var value = RepeatAnnotations.parseRow(ucsc(bin), "susScr11-rmsk", 7,
                    bin ? RepeatAnnotations.Format.UCSC_RMSK_WITH_BIN : RepeatAnnotations.Format.UCSC_RMSK_NO_BIN);
            assertEquals(469, value.start()); assertEquals(714, value.end()); assertEquals(274330532, value.queryLengthHint());
            assertEquals(new BigDecimal("24.6"), value.divergencePercent()); assertEquals(new BigDecimal("0.4"), value.insertionPercent());
            assertNull(value.consensus()); assertEquals("-5", value.rawFields().get(bin ? 13 : 12));
            assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.consensusCoverage(List.of(value)));
        }
    }
    @Test void binAndIdLayoutsMustBeExplicitRatherThanGuessed() {
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.parseRow(ucsc(true), "rmsk", 1, RepeatAnnotations.Format.UCSC_RMSK_NO_BIN));
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.parseRow(ucsc(false), "rmsk", 1, RepeatAnnotations.Format.UCSC_RMSK_WITH_BIN));
        String noId = PLUS.substring(0, PLUS.lastIndexOf(' '));
        assertThrows(IllegalArgumentException.class, () -> out(noId));
        var value = RepeatAnnotations.parseRow(noId + " *", "source", 1, RepeatAnnotations.Format.OUT_WITHOUT_ID);
        assertNull(value.insertionId()); assertTrue(value.markedOverlap());
    }
    @Test void labelsAndClassFamilySuffixesAreNotAliasedOrCollapsed() {
        var value = out(PLUS.replace("LINE/L2", "LINE/L2/retained"));
        assertEquals("LINE", value.repeatClass()); assertEquals("L2/retained", value.repeatFamily());
        var simple = out(PLUS.replace("LINE/L2", "Simple_repeat"));
        assertEquals("Simple_repeat", simple.repeatClass()); assertEquals("Simple_repeat", simple.repeatFamily());
        assertEquals(0, RepeatAnnotations.coverage(List.of(value), "human", 0, 1000000).coveredBases());
    }
    @Test void extendedOrfCoordinatesRemainRawWithoutInventingOrdinaryConsensusBounds() {
        var value = out(PLUS.replace("2401 3210 (216)", "-20 3210 (216)"));
        assertNull(value.consensus()); assertEquals("-20", value.rawFields().get(11));
        assertEquals(724, RepeatAnnotations.coverage(List.of(value), "Human", 75512, 76236).coveredBases());
    }
    @Test void boundedReadRetainsPhysicalSourceLineNumbersAndCrLfCharacterCounts() throws Exception {
        String input = "   SW  perc perc perc\r\nscore  div. del. ins.\r\n\r\n" + PLUS + "\r\n" + REVERSE + "\r\n";
        var table = RepeatAnnotations.read(new StringReader(input), "source", RepeatAnnotations.Format.OUT_WITH_ID, limits);
        assertEquals(2, table.hits().size()); assertEquals(4, table.hits().get(0).sourceLine());
        assertEquals(5, table.sourceLines()); assertEquals(input.length(), table.sourceCharacters()); assertEquals(2, table.headerOrCommentLines());
        assertThrows(UnsupportedOperationException.class, () -> table.hits().clear());
    }
    @Test void ucscHeadersValidateColumnOrderAndDeclaredBinShape() throws Exception {
        String header = "#bin\tswScore\tmilliDiv\tmilliDel\tmilliIns\tgenoName\tgenoStart\tgenoEnd\tgenoLeft\tstrand\trepName\trepClass\trepFamily\trepStart\trepEnd\trepLeft\tid\n";
        assertEquals(1, RepeatAnnotations.read(new StringReader(header + ucsc(true)), "source", RepeatAnnotations.Format.UCSC_RMSK_WITH_BIN, limits).hits().size());
        assertThrows(IOException.class, () -> RepeatAnnotations.read(new StringReader(header.replace("repClass\trepFamily", "repFamily\trepClass") + ucsc(true)), "source", RepeatAnnotations.Format.UCSC_RMSK_WITH_BIN, limits));
        assertThrows(IOException.class, () -> RepeatAnnotations.read(new StringReader(header + ucsc(false)), "source", RepeatAnnotations.Format.UCSC_RMSK_NO_BIN, limits));
    }
    @Test void noHitTextCannotPrecedeOrFollowDataAndUnknownTextIsNotDiscarded() throws Exception {
        String message = "There were no repetitive sequences detected in controlled.fa\n";
        assertTrue(RepeatAnnotations.read(new StringReader(message), "source", RepeatAnnotations.Format.OUT_WITH_ID, limits).noHitsMessage());
        for (String text : List.of(message + PLUS, PLUS + "\n" + message, message + message, "corrupt table\n"))
            assertThrows(IOException.class, () -> RepeatAnnotations.read(new StringReader(text), "source", RepeatAnnotations.Format.OUT_WITH_ID, limits));
    }
    @Test void rowCharacterLineAndRetainedCapacitiesRefuseWithoutTruncating() {
        String two = PLUS + "\n" + PLUS;
        for (var tight : List.of(new RepeatAnnotations.Limits(10, 100, 10000, 10000000),
                new RepeatAnnotations.Limits(1000000, 1, 10000, 10000000),
                new RepeatAnnotations.Limits(1000000, 100, 10, 10000000),
                new RepeatAnnotations.Limits(1000000, 100, 10000, 100)))
            assertThrows(IOException.class, () -> RepeatAnnotations.read(new StringReader(two), "source", RepeatAnnotations.Format.OUT_WITH_ID, tight));
    }
    @Test void unionCoverageRetainsOverlappingDuplicateAndMarkedContributors() {
        var a = hit("Q", 0, 5, 20, 1); var b = hit("Q", 3, 10, 20, 2); var c = hit("Q", 10, 12, 20, 3);
        var value = RepeatAnnotations.coverage(List.of(c, a, b, a, hit("OTHER", 0, 20, 20, 4)), "Q", 2, 15);
        assertEquals(10, value.coveredBases()); assertEquals(13, value.denominatorBases()); assertEquals(4, value.contributors().size());
        assertEquals(0, RepeatAnnotations.coverage(List.of(a), "Q", 5, 10).coveredBases());
    }
    @Test void compatibleInsertionFragmentsUseConsensusUnionRatherThanSummedLengths() {
        var a = out("100 0 0 0 Q 1 50 (50) + R LINE/L2 1 50 (50) 7");
        var b = out("100 0 0 0 Q 61 100 (0) + R LINE/L2 41 80 (20) 7");
        var coverage = RepeatAnnotations.consensusCoverage(List.of(a, b));
        assertEquals(80, coverage.coveredBases()); assertEquals(100, coverage.denominatorBases());
        assertEquals(90, RepeatAnnotations.coverage(List.of(a, b), "Q", 0, 100).coveredBases());
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.consensusCoverage(List.of(a, out("100 0 0 0 Q 61 100 (0) + R LINE/L2 41 80 (20) 8"))));
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.consensusCoverage(List.of(a, out("100 0 0 0 Q 61 100 (0) + R LINE/L2 41 80 (30) 7"))));
    }
    @Test void exactSeventyFiveAndTwentyPercentBoundariesAndLongProductsDoNotRound() {
        assertTrue(RepeatAnnotations.meetsFraction(75, 100, 3, 4, true)); assertFalse(RepeatAnnotations.meetsFraction(75, 100, 3, 4, false));
        assertFalse(RepeatAnnotations.meetsFraction(20, 100, 1, 5, false)); assertTrue(RepeatAnnotations.meetsFraction(21, 100, 1, 5, false));
        assertTrue(RepeatAnnotations.meetsFraction(Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE, true));
        assertFalse(RepeatAnnotations.meetsFraction(Long.MAX_VALUE - 1, Long.MAX_VALUE, 1, 1, true));
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.meetsFraction(101, 100, 3, 4, true));
    }
    @Test void twoHundredRandomMaskAndUnionPopulationsMatchIndependentPositionOracles() {
        Random random = new Random(79231); String alphabet = "ACGTNnXxacgtry";
        for (int trial = 0; trial < 200; trial++) {
            int length = 1 + random.nextInt(100); var input = new StringBuilder();
            for (int i = 0; i < length; i++) input.append(alphabet.charAt(random.nextInt(alphabet.length())));
            boolean[] selected = new boolean[length]; var hits = new ArrayList<RepeatAnnotations.Hit>();
            for (int j = 0; j < 15; j++) {
                int first = random.nextInt(length), last = first + 1 + random.nextInt(length - first);
                hits.add(hit("Q", first, last, length, j)); for (int p = first; p < last; p++) selected[p] = true;
            }
            for (var mode : RepeatAnnotations.Mask.values()) {
                char[] expected = input.toString().toCharArray(); long covered = 0, changed = 0;
                for (int p = 0; p < length; p++) if (selected[p]) {
                    covered++; char original = expected[p]; expected[p] = mode == RepeatAnnotations.Mask.N ? 'N'
                            : mode == RepeatAnnotations.Mask.X ? 'X' : Character.toLowerCase(original);
                    if (original != expected[p]) changed++;
                }
                var result = RepeatAnnotations.mask(input.toString(), "Q", hits, mode);
                assertEquals(new String(expected), result.sequence()); assertEquals(covered, result.coveredBases()); assertEquals(changed, result.changedCharacters());
            }
        }
    }
    @Test void invalidDataCannotBeReinterpretedAsAnotherLayoutOrSilentlyClamped() {
        for (String invalid : List.of(PLUS.replace("28.1", "NaN"), PLUS.replace("75513 76236", "0 76236"),
                PLUS.replace("(923764)", "923764"), PLUS.replace("+ L2a", "- L2a"), PLUS + " unexpected"))
            assertThrows(IllegalArgumentException.class, () -> out(invalid));
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.parseRow(ucsc(false).replace("1012\t", "4294967296\t"), "source", 1, RepeatAnnotations.Format.UCSC_RMSK_NO_BIN));
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.mask("AC-G", "Q", List.of(), RepeatAnnotations.Mask.SOFT));
        assertThrows(IllegalArgumentException.class, () -> RepeatAnnotations.mask("ACGT", "Q", List.of(hit("Q", 0, 5, 5, 1)), RepeatAnnotations.Mask.N));
    }
}
