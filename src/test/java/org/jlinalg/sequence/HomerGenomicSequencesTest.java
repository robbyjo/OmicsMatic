package org.jlinalg.sequence;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HomerGenomicSequencesTest {
    private static SeqtkMethods.Bases bases(String dna) {
        return new SeqtkMethods.Bases() {
            @Override public long length() { return dna.length(); }
            @Override public char base(long position) { return dna.charAt((int) position); }
        };
    }
    @Test void numericSizePreservesOriginalInclusiveWidthAndHalfBaseFloor() {
        var a = new HomerGenomicSequences.Interval(10, 20, '+');
        var b = new HomerGenomicSequences.Interval(10, 21, '+');
        assertEquals(new HomerGenomicSequences.Interval(12, 18, '+'), HomerGenomicSequences.resize(a, 6, 0));
        assertEquals(new HomerGenomicSequences.Interval(12, 19, '+'), HomerGenomicSequences.resize(b, 7, 0));
        assertEquals(201, HomerGenomicSequences.resize(a, 200, 200).length());
    }
    @Test void movementIsRelativeToStrand() {
        assertEquals(new HomerGenomicSequences.Interval(32, 38, '+'), HomerGenomicSequences.resize(new HomerGenomicSequences.Interval(10, 20, '+'), 6, 20));
        assertEquals(new HomerGenomicSequences.Interval(82, 88, '-'), HomerGenomicSequences.resize(new HomerGenomicSequences.Interval(100, 110, '-'), 6, 20));
    }
    @Test void invalidBoundaryResizeFailsInsteadOfRepairingOriginalExtraction() {
        assertThrows(IllegalArgumentException.class, () -> HomerGenomicSequences.resize(new HomerGenomicSequences.Interval(1, 10, '+'), 200, 0));
        assertThrows(IllegalArgumentException.class, () -> new HomerGenomicSequences.Interval(0, 10, '+'));
        assertThrows(IllegalArgumentException.class, () -> new HomerGenomicSequences.Interval(2, 1, '+'));
        assertThrows(IllegalArgumentException.class, () -> new HomerGenomicSequences.Interval(1, 10, '.'));
    }
    @Test void extractionPreservesCasePolicyAndReverseComplementsAfterMasking() throws Exception {
        var reference = bases("TaaCGtRYnACG");
        assertEquals("AACGTNNN", HomerGenomicSequences.extract(reference, new HomerGenomicSequences.Interval(2, 9, '+'), false, 8));
        assertEquals("NNCGNNNN", HomerGenomicSequences.extract(reference, new HomerGenomicSequences.Interval(2, 9, '+'), true, 8));
        assertEquals("NNNACGTT", HomerGenomicSequences.extract(reference, new HomerGenomicSequences.Interval(2, 9, '-'), false, 8));
    }
    @Test void extractionEnforcesContigAllocationAndAlphabetBounds() {
        for (String dna : List.of("A1CGT", "A CGT", "A-CGT", "A\tCGT"))
            assertThrows(IOException.class, () -> HomerGenomicSequences.extract(bases(dna), new HomerGenomicSequences.Interval(1, 5, '+'), false, 5));
        assertThrows(IOException.class, () -> HomerGenomicSequences.extract(bases("ACGT"), new HomerGenomicSequences.Interval(1, 5, '+'), false, 5));
        assertThrows(IOException.class, () -> HomerGenomicSequences.extract(bases("ACGT"), new HomerGenomicSequences.Interval(1, 4, '+'), false, 3));
    }
    @Test void poorSequenceFilterRetainsExactThresholdAndRecordsEveryReason() {
        assertEquals(HomerGenomicSequences.Disposition.RETAINED, HomerGenomicSequences.disposition("ACGNNNNNNN", .7));
        assertEquals(HomerGenomicSequences.Disposition.EXCESS_N, HomerGenomicSequences.disposition("ACNNNNNNNN", .7));
        assertEquals(HomerGenomicSequences.Disposition.SHORTER_THAN_FIVE, HomerGenomicSequences.disposition("ACGT", .7));
        assertEquals(HomerGenomicSequences.Disposition.LONGER_THAN_100000, HomerGenomicSequences.disposition("A".repeat(100001), .7));
        assertThrows(IllegalArgumentException.class, () -> HomerGenomicSequences.disposition("acgtn", .7));
        assertThrows(IllegalArgumentException.class, () -> HomerGenomicSequences.disposition("ACGTN", Double.NaN));
    }
    @Test void frequencyExcludesNFromBothDenominatorsAndDoesNotBridgeNs() {
        var f = HomerGenomicSequences.frequency("ACGNCGNT");
        assertEquals(6, f.canonicalBases()); assertEquals(3, f.canonicalAdjacentPairs()); assertEquals(2, f.cgPairs());
        assertEquals(.666667, f.cpg()); assertEquals(.666667, f.gc()); assertEquals(.5, f.ag()); assertEquals(.5, f.ac());
        assertEquals(8, f.gcBin()); assertEquals(11, f.cpgBin());
    }
    @Test void isolatedCanonicalBasesHaveZeroCpgRatherThanDivisionByZero() {
        var f = HomerGenomicSequences.frequency("ANCNGNT");
        assertEquals(0, f.cpg()); assertEquals(.5, f.gc()); assertEquals(0, f.canonicalAdjacentPairs());
        assertThrows(IllegalArgumentException.class, () -> HomerGenomicSequences.frequency("NNNNN"));
    }
    @Test void exactFrequencyBoundaryFallsIntoNextBinAfterOriginalTextRounding() {
        assertEquals(2, HomerGenomicSequences.frequency("CAAA").gcBin());
        assertEquals(7, HomerGenomicSequences.frequency("CCAA").gcBin());
        assertEquals(3, HomerGenomicSequences.frequency("CCCAAAAAAA").gcBin());
        assertEquals(10, HomerGenomicSequences.frequency("CCCCAAAAAA").canonicalBases());
    }
    @Test void weightingRemovesBothRolesInUnsupportedBinsAndRecomputesPopulation() {
        var values = List.of(new HomerGenomicSequences.Member("t1", true, 0, 1), new HomerGenomicSequences.Member("t2", true, 0, 1),
                new HomerGenomicSequences.Member("b1", false, 0, 1), new HomerGenomicSequences.Member("t3", true, 1, 1),
                new HomerGenomicSequences.Member("b2", false, 1, 1), new HomerGenomicSequences.Member("b3", false, 1, 1),
                new HomerGenomicSequences.Member("unsupported-target", true, 2, 1), new HomerGenomicSequences.Member("unsupported-bg", false, 3, 1));
        var w = HomerGenomicSequences.weightBins(values);
        assertEquals(8, w.size()); assertEquals(2, w.get(2).weight()); assertEquals(.5, w.get(4).weight());
        assertEquals(3, w.stream().filter(x -> !x.member().target()).mapToDouble(HomerGenomicSequences.Weighted::weight).sum());
        assertFalse(w.get(6).retained()); assertFalse(w.get(7).retained()); assertEquals(0, w.get(6).weight());
    }
    @Test void weightedBinMinimumUsesUnitWeightRatherThanRecordCount() {
        var w = HomerGenomicSequences.weightBins(List.of(new HomerGenomicSequences.Member("t", true, 1, .9), new HomerGenomicSequences.Member("b", false, 1, 2)));
        assertTrue(w.stream().noneMatch(HomerGenomicSequences.Weighted::retained));
    }
    @Test void weightsAreOriginalFiveDecimalTextValues() {
        var w = HomerGenomicSequences.weightBins(List.of(new HomerGenomicSequences.Member("t", true, 1, 1), new HomerGenomicSequences.Member("b1", false, 1, 3),
                new HomerGenomicSequences.Member("t2", true, 2, 2), new HomerGenomicSequences.Member("b2", false, 2, 1)));
        assertEquals(1.33333, w.get(1).weight()); assertEquals(2.66667, w.get(3).weight());
    }
    @Test void duplicateAliasesAndInvalidWeightsCannotCollapseRecords() {
        var m = new HomerGenomicSequences.Member("same", true, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> HomerGenomicSequences.weightBins(List.of(m, m)));
        assertThrows(IllegalArgumentException.class, () -> new HomerGenomicSequences.Member("x", true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new HomerGenomicSequences.Member("x", true, 0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> HomerGenomicSequences.weightBins(List.of()));
    }
    @Test void originalSixDecimalFrequencyUsesBinaryHalfEvenTieRule(){assertEquals(.007812,HomerGenomicSequences.frequency("CG"+"A".repeat(127)).cpg());assertEquals(.007812,HomerGenomicSequences.frequency("C"+"A".repeat(127)).gc());}
    @Test void originalFiveDecimalWeightRoundingDoesNotUseShortestDecimalText(){var values=HomerGenomicSequences.weightBins(List.of(new HomerGenomicSequences.Member("target",true,0,2.000005),new HomerGenomicSequences.Member("background",false,0,1)));assertEquals(2.0,values.get(0).weight());}

}
