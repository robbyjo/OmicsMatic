/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrsCsMathTest {
    private static byte[] resource(String name) throws IOException {
        try (var stream = PrsCsMathTest.class.getResourceAsStream("/org/jlinalg/genetics/prscs-math/"+name)) {
            if (stream == null) throw new IOException("Missing actual-original PRS-CS fixture: "+name);
            return stream.readAllBytes();
        }
    }
    private static List<String[]> rows(String name) throws IOException {
        return new String(resource(name), StandardCharsets.UTF_8).lines().skip(1).map(s -> s.split("\t", -1)).toList();
    }
    private static PrsCsMath.Alleles pair(String text) { return new PrsCsMath.Alleles(text.charAt(0), text.charAt(1)); }
    private static double radius(String text) {
        BigDecimal value = new BigDecimal(text);
        return value.signum() == 0 ? 0 : value.ulp().doubleValue()/2;
    }

    @Test void frozenReferenceBytesMatchTheOriginalCaptureManifest() throws Exception {
        var lines = new String(resource("SHA256SUMS"), StandardCharsets.UTF_8).lines().toList();
        assertEquals(4, lines.size());
        for (String line : lines) {
            String[] f = line.split("  ", 2);
            assertEquals(f[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(f[1]))), f[1]);
        }
    }

    @Test void all1728AlleleTriplesMatchThreeActualOriginalParsersIncludingPalindromes() throws Exception {
        int matched = 0;
        for (String[] r : rows("alignment.tsv")) {
            var result = PrsCsMath.align(pair(r[0]), pair(r[1]), pair(r[2]));
            assertEquals(Boolean.parseBoolean(r[4]), result.matched(), String.join("/", r));
            assertEquals(Integer.parseInt(r[5]), result.summarySign());
            assertEquals(Integer.parseInt(r[6]), result.referenceSign());
            if (result.matched()) {
                matched++;
                assertEquals(Double.parseDouble(r[7]), PrsCsMath.effectAlleleFrequency(Double.parseDouble(r[3]), result));
                double expected = Double.parseDouble(r[8]);
                assertEquals(expected, PrsCsMath.fromBetaP(.125, .02, 20000, result.summarySign(), PrsCsMath.PValuePolicy.ERROR), 3e-15);
            }
        }
        assertEquals(1728, rows("alignment.tsv").size());
        assertEquals(144, matched);
    }

    @Test void all592ActualOriginalMarginalTransformsMatchNativeJava() throws Exception {
        var data = rows("transforms.tsv");
        assertEquals(592, data.size());
        for (String[] r : data) {
            double effect = Double.parseDouble(r[2]), uncertainty = Double.parseDouble(r[3]);
            int sign = Integer.parseInt(r[4]); long n = Long.parseLong(r[5]);
            double actual = switch (r[1]) {
                case "beta-se" -> PrsCsMath.fromBetaSe(effect, uncertainty, n, sign);
                case "or-se" -> PrsCsMath.fromOddsRatioSe(effect, uncertainty, n, sign);
                case "beta-p" -> PrsCsMath.fromBetaP(effect, uncertainty, n, sign, PrsCsMath.PValuePolicy.ORIGINAL_FLOOR_WITH_WARNING);
                case "or-p" -> PrsCsMath.fromOddsRatioP(effect, uncertainty, n, sign, PrsCsMath.PValuePolicy.ORIGINAL_FLOOR_WITH_WARNING);
                default -> throw new AssertionError("Unexpected original input format");
            };
            double expected = Double.parseDouble(r[6]);
            assertEquals(expected, actual, Math.max(3e-15, 3e-14*Math.abs(expected)), String.join("/", r));
        }
    }

    @Test void all1040OriginalPosteriorUnitRelationshipsRespectPrintingResolution() throws Exception {
        var data = rows("posterior-scaling.tsv");
        assertEquals(1040, data.size());
        for (String[] r : data) {
            double frequency = Double.parseDouble(r[7]), std = Double.parseDouble(r[8]), allele = Double.parseDouble(r[9]);
            double deviation = PrsCsMath.genotypeStandardDeviation(frequency);
            double bound = radius(r[8])/deviation + radius(r[9]) + 16*Math.ulp(allele);
            assertEquals(allele, PrsCsMath.perAllele(std, frequency), bound, String.join("/", r));
            assertEquals(std, PrsCsMath.standardized(allele, frequency), bound*deviation + 16*Math.ulp(std));
        }
    }

    @Test void palindromeBranchOrderDoesNotPretendToResolveBiologicalStrand() {
        var result = PrsCsMath.align(pair("AT"), pair("TA"), pair("AT"));
        assertTrue(result.palindromic()); assertTrue(result.summaryComplement());
        assertFalse(result.referenceComplement()); assertEquals(1, result.summarySign());
        assertEquals(-1, result.referenceSign()); assertEquals(.8, PrsCsMath.effectAlleleFrequency(.2, result));
        var unambiguous = PrsCsMath.align(pair("AG"), pair("CT"), pair("GA"));
        assertTrue(unambiguous.matched()); assertFalse(unambiguous.palindromic());
        assertEquals(-1, unambiguous.referenceSign()); assertTrue(unambiguous.referenceComplement());
        assertEquals(1, unambiguous.summarySign()); assertTrue(unambiguous.summaryComplement());
    }

    @Test void betaPUsesDirectionWhileSeUsesMagnitudeAndOrSeUsesLogOdds() {
        var policy = PrsCsMath.PValuePolicy.ERROR;
        assertEquals(PrsCsMath.fromBetaP(.01, .05, 100, 1, policy), PrsCsMath.fromBetaP(100, .05, 100, 1, policy));
        assertEquals(.1959963984540054, PrsCsMath.fromBetaP(3, .05, 100, 1, policy), 2e-15);
        assertEquals(-.1, PrsCsMath.fromBetaSe(.2, .2, 100, -1));
        assertEquals(Math.log(2)/2, PrsCsMath.fromOddsRatioSe(2, .2, 100, 1), Math.ulp(Math.log(2)/2));
        assertEquals(0, PrsCsMath.fromOddsRatioP(1, 1e-8, 100, 1, policy));
        assertEquals(0, PrsCsMath.fromBetaP(.1, 1, 100, 1, policy));
    }

    @Test void originalPvalueFloorRequiresAnExplicitChoiceAndNeverProducesInfinity() {
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaP(.1, 0, 100, 1, PrsCsMath.PValuePolicy.ERROR));
        double floored = PrsCsMath.fromBetaP(.1, 0, 100, 1, PrsCsMath.PValuePolicy.ORIGINAL_FLOOR_WITH_WARNING);
        assertTrue(Double.isFinite(floored)); assertTrue(floored > 3);
        assertEquals(floored, PrsCsMath.fromBetaP(.1, PrsCsMath.UPSTREAM_MINIMUM_P, 100, 1, PrsCsMath.PValuePolicy.ERROR));
    }

    @Test void thinningChecksActualRetainedIterationsInsteadOfOnlyTheQuotient() {
        assertEquals(100, PrsCsMath.retainedSamples(1000, 500, 5));
        assertEquals(2, PrsCsMath.retainedSamples(11, 1, 5));
        assertEquals(1, PrsCsMath.retainedSamples(1, 0, 1));
        for (int[] r : List.of(new int[]{12,3,5}, new int[]{12,0,5}, new int[]{13,1,5}, new int[]{1,0,2}, new int[]{2,2,1}, new int[]{1,0,0}))
            assertThrows(IllegalArgumentException.class, () -> PrsCsMath.retainedSamples(r[0], r[1], r[2]));
    }

    @Test void invalidAllelesFrequenciesEffectsSampleSizesAndUnmatchedResultsFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> pair("AA"));
        assertThrows(IllegalArgumentException.class, () -> pair("aG"));
        for (double p : new double[]{0,1,-.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> PrsCsMath.perAllele(.1, p));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaSe(Double.NaN, .1, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaSe(1, 0, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaSe(1, .1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaSe(1, .1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromOddsRatioSe(0, .1, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaP(1, -1, 100, 1, PrsCsMath.PValuePolicy.ERROR));
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.fromBetaSe(1, .1, 100, 0));
        var mismatch = PrsCsMath.align(pair("AG"), pair("AC"), pair("AC"));
        assertFalse(mismatch.matched());
        assertThrows(IllegalArgumentException.class, () -> PrsCsMath.effectAlleleFrequency(.2, mismatch));
        assertThrows(IllegalArgumentException.class, () -> new PrsCsMath.Alignment(true, 0, 1, false, false, false));
    }
}
