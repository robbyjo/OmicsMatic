/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mr;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.jlinalg.mr.LinkedVariantMr.Association;
import org.jlinalg.mr.LinkedVariantMr.Policy;
import org.jlinalg.mr.LinkedVariantMr.Proxy;

class LinkedVariantMrTest {
    private static final Policy POLICY = new Policy(5e-8, 10, "minimac_rsq", .8, .8, .95);
    private static Association x(String id, double beta, double p) {
        return a(id, "A", "G", beta, .02, p, "sequenced", "", Double.NaN);
    }
    private static Association a(String id, String ea, String oa, double beta, double se, double p,
            String measured, String metric, double quality) {
        return new Association(id, "1", id.equals("lead") ? 100 : 200, ea, oa, beta, se, p, .2, 10000, true, measured, metric, quality);
    }
    private static Association y(String id, double beta, double quality) {
        return a(id, "A", "G", beta, .1, .9, "imputed", "minimac_rsq", quality);
    }

    @Test void reliableNullWinsOverSignificantProxy() {
        var r = LinkedVariantMr.analyze(List.of(x("proxy", .3, 1e-12), x("lead", .4, 1e-30)),
            List.of(y("lead", 0, .99), y("proxy", 3, 1)), List.of(new Proxy("lead", "proxy", .99)), "1", 1, 1000, POLICY);
        assertEquals("exact", r.status()); assertEquals("lead", r.selectedVariant());
        assertEquals(0, r.estimate().estimate()); assertEquals(1, r.estimate().pValue());
        assertTrue(r.decisions().stream().noneMatch(d -> d.stage().equals("proxy")));
    }

    @Test void poorOriginalReplacedWithOwnProxyEffectsAndAlleleFlip() {
        var reversed = a("proxy", "G", "A", -.15, .03, 1e-6, "imputed", "minimac_rsq", .95);
        var r = LinkedVariantMr.analyze(List.of(x("lead", .8, 1e-30), x("proxy", .3, 1e-12)),
            List.of(y("lead", 2, .2), reversed), List.of(new Proxy("lead", "proxy", .9)), "1", 1, 1000, POLICY);
        assertEquals("proxy_same_variant_effects", r.status()); assertEquals("lead", r.leadVariant());
        assertEquals("imputation_quality_below_threshold", r.originalOutcomeDisposition());
        assertEquals(.5, r.estimate().estimate(), 1e-14); // .15/.3, NOT .15/.8
        assertEquals(.1, r.estimate().standardError(), 1e-14);
        assertEquals(.8, r.instrument().outcomeEffectAlleleFrequency(), 1e-14);
        assertTrue(r.instrument().outcomeEffectFlipped());
        assertEquals(.5 - 1.959963984540054 * .1, r.estimate().confidenceLower(), 1e-12);
    }

    @Test void outcomeEffectsAndPValuesCannotChangeProxyRanking() {
        var exposure = List.of(x("lead", .4, 1e-30), x("p1", .3, 1e-12), x("p2", .3, 1e-12));
        var ld = List.of(new Proxy("lead", "p1", .95), new Proxy("lead", "p2", .9));
        for (double effect : new double[]{0, 30, -30}) {
            var r = LinkedVariantMr.analyze(exposure, List.of(y("p1", effect, .9),
                a("p2", "A", "G", 30, .001, 1e-300, "imputed", "minimac_rsq", 1)), ld, "1", 1, 1000, POLICY);
            assertEquals("p1", r.selectedVariant());
        }
    }

    @Test void qualityBreaksLdTiesWithoutUsingOutcomeAssociation() {
        var r = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30), x("p1", .3, 1e-12), x("p2", .3, 1e-12)),
            List.of(y("p1", 99, .8), y("p2", 0, .95)), List.of(new Proxy("lead", "p1", .9), new Proxy("lead", "p2", .9)), "1", 1, 1000, POLICY);
        assertEquals("p2", r.selectedVariant()); assertEquals(1, r.estimate().pValue());
    }

    @Test void noNearbySubstitutionWithoutLdAndStrongProxyExposure() {
        var r = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30), x("proxy", .01, .2)),
            List.of(y("proxy", 10, .99)), List.of(new Proxy("lead", "proxy", .99)), "1", 1, 1000, POLICY);
        assertEquals("not_testable_no_reliable_outcome", r.status()); assertNull(r.estimate());
        var absent = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30), x("proxy", .3, 1e-12)),
            List.of(y("proxy", 10, .99)), List.of(), "1", 1, 1000, POLICY);
        assertNull(absent.estimate());
    }

    @Test void imputationQualityAndLdAreIndependentRequirements() {
        for (double[] q : new double[][]{{.99, .7}, {.4, .99}}) {
            var r = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30), x("proxy", .3, 1e-12)),
                List.of(y("proxy", 1, q[0])), List.of(new Proxy("lead", "proxy", q[1])), "1", 1, 1000, POLICY);
            assertNull(r.estimate());
        }
    }

    @Test void unknownQualityAndDifferentMetricAreNotNullResults() {
        for (Association outcome : List.of(a("lead", "A", "G", 0, .1, 1, "imputed", "", Double.NaN),
                a("lead", "A", "G", 0, .1, 1, "imputed", "impute_info", .99))) {
            var r = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30)), List.of(outcome), List.of(), "1", 1, 1000, POLICY);
            assertEquals("not_testable_no_reliable_outcome", r.status()); assertNull(r.estimate());
        }
    }

    @Test void strandComplementAndCoordinateMismatchAreHandled() {
        var valid = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30)),
            List.of(a("lead", "T", "C", .2, .1, .04, "genotyped", "", Double.NaN)), List.of(), "1", 1, 1000, POLICY);
        assertEquals(.5, valid.estimate().estimate()); assertTrue(valid.instrument().strandComplemented());
        var wrong = new Association("lead", "1", 101, "A", "G", .2, .1, .04, .2, 10000, true, "genotyped", "", Double.NaN);
        var invalid = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30)), List.of(wrong), List.of(), "1", 1, 1000, POLICY);
        assertEquals("coordinate_mismatch", invalid.originalOutcomeDisposition()); assertNull(invalid.estimate());
    }

    @Test void exposureStrengthDoesNotImproveSingleSnpOutcomeP() {
        double first = Double.NaN;
        for (double exposure : new double[]{.4, 4, 40}) {
            var r = LinkedVariantMr.analyze(List.of(x("lead", exposure, 1e-30)), List.of(y("lead", .1, .99)), List.of(), "1", 1, 1000, POLICY);
            assertEquals(.3173105078629141, r.estimate().pValue(), 1e-14);
            if (Double.isFinite(first)) assertEquals(first, r.estimate().pValue());
            first = r.estimate().pValue();
        }
    }

    @Test void measurementQualityDoesNotRescaleEffect() {
        for (double quality : new double[]{.8, .9, 1}) {
            var r = LinkedVariantMr.analyze(List.of(x("lead", .4, 1e-30)), List.of(y("lead", .2, quality)), List.of(), "1", 1, 1000, POLICY);
            assertEquals(.5, r.estimate().estimate()); assertEquals(.25, r.estimate().standardError());
        }
    }

    @Test void deterministicUnderflowTieAndCisRestriction() {
        var r = LinkedVariantMr.analyze(List.of(x("lead", .4, 0), x("proxy", .6, 0)),
            List.of(y("lead", .2, .99), y("proxy", .2, .99)), List.of(), "1", 1, 150, POLICY);
        assertEquals("lead", r.leadVariant());
        var tied = LinkedVariantMr.analyze(List.of(x("lead", .4, 0), x("proxy", .6, 0)),
            List.of(y("lead", .2, .99), y("proxy", .2, .99)), List.of(), "1", 1, 1000, POLICY);
        assertEquals("proxy", tied.leadVariant());
    }

    @Test void malformedInputsFailAndEmptyExposureIsExplicitlyUntestable() {
        assertThrows(IllegalArgumentException.class, () -> LinkedVariantMr.analyze(List.of(x("lead", .4, 0), x("lead", .5, 0)), List.of(), List.of(), "1", 1, 1000, POLICY));
        assertThrows(IllegalArgumentException.class, () -> new Proxy("lead", "proxy", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Policy(5e-8, 10, "minimac_rsq", .8, .7, .95));
        var r = LinkedVariantMr.analyze(List.of(), List.of(), List.of(), "1", 1, 1000, POLICY);
        assertEquals("not_testable_no_strong_exposure", r.status());
    }

    @Test void fullFamilyCorrectionIncludesUnestimableHypotheses() {
        assertEquals(.01, LinkedVariantMr.bonferroni(1e-6, 10000), 1e-15);
        assertEquals(1, LinkedVariantMr.bonferroni(1e-6, 1000000));
        assertThrows(IllegalArgumentException.class, () -> LinkedVariantMr.bonferroni(.01, 0));
    }
}
