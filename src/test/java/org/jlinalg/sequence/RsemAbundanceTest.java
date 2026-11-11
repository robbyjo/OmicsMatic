package org.jlinalg.sequence;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RsemAbundanceTest {
    private static RsemAbundance.Reference ref(String id, String gene, int length, int tail) {
        return new RsemAbundance.Reference(id, gene, length, tail);
    }
    private static double[] a(double... values) { return values; }
    private static double[] numbers(String text) {
        String[] words = text.split(",", -1); double[] result = new double[words.length];
        for (int i = 0; i < words.length; i++) result[i] = Double.parseDouble(words[i]);
        return result;
    }
    private static void near(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
            assertEquals(expected[i], actual[i], Math.max(4 * Math.ulp(expected[i]), Math.abs(expected[i]) * 4e-12), "entry " + i);
    }

    @Test void fixedFragmentLengthRetainsThePlusOnePosition() {
        assertArrayEquals(a(81), RsemAbundance.effectiveLengths(List.of(ref("t", "g", 100, 0)), new RsemAbundance.FragmentLengths(20, a(1))));
    }
    @Test void tailPositionsAreCappedByTheOriginalTranscriptLength() {
        assertArrayEquals(a(100), RsemAbundance.effectiveLengths(List.of(ref("t", "g", 100, 125)), new RsemAbundance.FragmentLengths(20, a(1))));
    }
    @Test void tooShortAndSubunitEffectiveLengthsAreZero() {
        var refs = List.of(ref("short", "g", 9, 0), ref("subunit", "g", 10, 0));
        assertArrayEquals(a(0, 0), RsemAbundance.effectiveLengths(refs, new RsemAbundance.FragmentLengths(10, a(0.5, 0.5))));
    }
    @Test void exactlyOneEffectivePositionIsRetained() {
        assertArrayEquals(a(1), RsemAbundance.effectiveLengths(List.of(ref("t", "g", 10, 0)), new RsemAbundance.FragmentLengths(10, a(1))));
    }
    @Test void probabilityMassOutsideTheTranscriptIsNotRenormalized() {
        assertArrayEquals(a(1), RsemAbundance.effectiveLengths(List.of(ref("t", "g", 11, 0)), new RsemAbundance.FragmentLengths(10, a(0.5, 0, 0.5))));
    }
    @Test void noiseIsExcludedFromTheExpressionDenominator() {
        var e = RsemAbundance.expression(a(0.5, 0.25, 0.25), a(100, 200));
        near(a(2e6 / 3, 1e6 / 3), e.tpm()); near(a(5e6, 2.5e6), e.fpkm());
    }
    @Test void allNoiseRemainsACompleteZeroLibrary() {
        var e = RsemAbundance.expression(a(1, 0, 0), a(100, 0));
        assertArrayEquals(a(0, 0), e.tpm()); assertArrayEquals(a(0, 0), e.fpkm());
    }
    @Test void historicalRequestsRetainTheOriginalNoAlignableReadFailure() {
        for (var release : List.of(RsemAbundance.Release.V1_2_28, RsemAbundance.Release.V1_3_0, RsemAbundance.Release.V1_3_1))
            assertThrows(IllegalArgumentException.class, () -> RsemAbundance.expression(a(1, 0), a(100), release));
        for (var release : List.of(RsemAbundance.Release.V1_3_2, RsemAbundance.Release.V1_3_3))
            assertArrayEquals(a(0), RsemAbundance.expression(a(1, 0), a(100), release).tpm());
    }
    @Test void zeroEffectiveTranscriptsDoNotEnterTheDenominator() {
        var e = RsemAbundance.expression(a(0.2, 0.4, 0.4), a(0, 100));
        assertArrayEquals(a(0, 1e6), e.tpm()); assertArrayEquals(a(0, 1e7), e.fpkm());
    }
    @Test void polishingIncludesNoiseAndDividesByMappability() {
        near(a(1.0 / 7, 4.0 / 7, 2.0 / 7), RsemAbundance.polishTheta(a(0.25, 0.5, 0.25), a(100, 100), a(1, 0.5, 0.5)));
    }
    @Test void polishingMasksZeroEffectiveOrUnmappableTranscripts() {
        assertArrayEquals(a(1, 0, 0), RsemAbundance.polishTheta(a(0.2, 0.3, 0.5), a(0, 100), a(1, 1, 0)));
    }
    @Test void polishingNeverInventsASignalForAnEmptyNormalizer() {
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.polishTheta(a(0, 1), a(0), a(1, 1)));
    }
    @Test void geneLengthsAndIsoformPercentUseTpmRatherThanCounts() {
        var refs = List.of(ref("t.1", "g", 100, 0), ref("t.2", "g", 300, 125));
        var result = RsemAbundance.aggregate(refs, a(80, 280), a(99, 1), new RsemAbundance.Expression(a(25, 75), a(2, 6)));
        assertEquals(250, result.genes().get(0).length()); assertEquals(230, result.genes().get(0).effectiveLength());
        assertEquals(100, result.genes().get(0).expectedCount()); assertEquals(25, result.isoforms().get(0).isoformPercent());
        assertEquals(List.of("t.1", "t.2"), result.genes().get(0).transcriptIds());
    }
    @Test void zeroExpressionGenesUseTheUnweightedMeanIncludingZeroEffectiveIsoforms() {
        var result = RsemAbundance.aggregate(List.of(ref("a", "g", 10, 0), ref("b", "g", 100, 0)), a(0, 80), a(0, 0), new RsemAbundance.Expression(a(0, 0), a(0, 0)));
        assertEquals(55, result.genes().get(0).length()); assertEquals(40, result.genes().get(0).effectiveLength());
        assertEquals(0, result.isoforms().get(0).isoformPercent());
    }
    @Test void exactGeneTpmThresholdRetainsTheOriginalStrictBoundary() {
        var r = RsemAbundance.aggregate(List.of(ref("a", "g", 100, 0)), a(80), a(1), new RsemAbundance.Expression(a(RsemAbundance.EPSILON), a(0)));
        assertEquals(0, r.genes().get(0).length()); assertEquals(0, r.isoforms().get(0).isoformPercent());
    }
    @Test void genesAndVersionedTranscriptsKeepExactOrderAndIdentity() {
        var refs = List.of(ref("t.1", "β", 100, 0), ref("t.2", "β", 200, 0), ref("t.3", "g", 300, 0));
        var r = RsemAbundance.aggregate(refs, a(50, 150, 250), a(1, 2, 3), new RsemAbundance.Expression(a(1, 2, 3), a(2, 4, 6)));
        assertEquals(List.of("β", "g"), r.genes().stream().map(RsemAbundance.Gene::geneId).toList());
        assertEquals(refs, r.isoforms().stream().map(RsemAbundance.Isoform::reference).toList());
    }
    @Test void repeatedNoncontiguousGenesAndDuplicateTranscriptsFail() {
        var e = new RsemAbundance.Expression(a(1, 1, 1), a(1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.aggregate(List.of(ref("a", "g", 100, 0), ref("b", "h", 100, 0), ref("c", "g", 100, 0)), a(80, 80, 80), a(1, 1, 1), e));
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.effectiveLengths(List.of(ref("a", "g", 100, 0), ref("a", "h", 100, 0)), new RsemAbundance.FragmentLengths(10, a(1))));
    }
    @Test void invalidProbabilityMassAndIntegerOverflowFail() {
        assertThrows(IllegalArgumentException.class, () -> new RsemAbundance.FragmentLengths(0, a(1)));
        assertThrows(IllegalArgumentException.class, () -> new RsemAbundance.FragmentLengths(1, a(0.9)));
        assertThrows(IllegalArgumentException.class, () -> new RsemAbundance.FragmentLengths(1, a(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> ref("a", "g", Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> ref("a", "g", 100, -1));
    }
    @Test void incompleteThetaInvalidWeightsAndNonfiniteCountsFail() {
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.expression(a(0.5), a(100)));
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.expression(a(0.1, 0.1), a(100)));
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.expression(a(0, 1), a(0.5)));
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.polishTheta(a(0, 1), a(100), a(0.5, 1)));
        assertThrows(IllegalArgumentException.class, () -> RsemAbundance.aggregate(List.of(ref("a", "g", 100, 0)), a(80), a(Double.POSITIVE_INFINITY), new RsemAbundance.Expression(a(1), a(1))));
    }
    @Test void arraysAndReturnedPopulationsAreImmutable() {
        double[] mass = a(1), tpm = a(1), fpkm = a(2);
        var d = new RsemAbundance.FragmentLengths(10, mass); var e = new RsemAbundance.Expression(tpm, fpkm);
        mass[0] = 0; tpm[0] = 0; fpkm[0] = 0; d.probabilities()[0] = 0; e.tpm()[0] = 0;
        assertArrayEquals(a(1), d.probabilities()); assertArrayEquals(a(1), e.tpm()); assertArrayEquals(a(2), e.fpkm());
        var r = RsemAbundance.aggregate(List.of(ref("a", "g", 100, 0)), a(80), a(1), e);
        assertThrows(UnsupportedOperationException.class, () -> r.genes().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.genes().get(0).transcriptIds().clear());
    }
    @Test void originalReleaseFunctionsMatchEveryFrozenVector() throws Exception {
        vectors("arithmetic.tsv", false, 136);
    }
    @Test void historicalOriginalFunctionsMatchEveryFrozenVector() throws Exception {
        vectors("historical.tsv", true, 532);
    }
    private void vectors(String resource, boolean historical, int expectedCases) throws Exception {
        var input = getClass().getResourceAsStream("/org/jlinalg/sequence/rsem-reference/" + resource);
        assertNotNull(input); int cases = 0;
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            assertEquals((historical ? "release\t" : "") + "case\tlengths\ttails\tminimum\tpmf\ttheta\tmappability\teffective\tpolished\ttpm\tfpkm", reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] raw = line.split("\t", -1); assertEquals(historical ? 12 : 11, raw.length);
                var release = historical ? RsemAbundance.Release.valueOf("V" + raw[0].replace('.', '_')) : RsemAbundance.Release.V1_3_3;
                String[] f = historical ? java.util.Arrays.copyOfRange(raw, 1, raw.length) : raw;
                double[] lengths = numbers(f[1]), tails = numbers(f[2]); var refs = new java.util.ArrayList<RsemAbundance.Reference>();
                for (int i = 0; i < lengths.length; i++) refs.add(ref("t" + i, "g", (int) lengths[i], (int) tails[i]));
                double[] effective = RsemAbundance.effectiveLengths(refs, new RsemAbundance.FragmentLengths(Integer.parseInt(f[3]), numbers(f[4])));
                near(numbers(f[7]), effective); double[] polished = RsemAbundance.polishTheta(numbers(f[5]), effective, numbers(f[6]));
                near(numbers(f[8]), polished); var expression = RsemAbundance.expression(polished, effective, release);
                near(numbers(f[9]), expression.tpm()); near(numbers(f[10]), expression.fpkm()); cases++;
            }
        }
        assertEquals(expectedCases, cases, "Complete original-function vector population required");
    }
}
