package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.List;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemSamAlignments;
import org.junit.jupiter.api.Test;

final class RsemFitRequestTest {
    private static RsemSamAlignments.Options input(RsemAbundance.Release version, int type, String tag) { return new RsemSamAlignments.Options(version, type, tag, 25); }
    private static RsemFitRequest.Settings settings(RsemFitRequest.Strand strand, RsemFitRequest.Posterior posterior, RsemFitRequest.Fragment fragment) {
        return new RsemFitRequest.Settings(1618, 1, strand, posterior, false, false, fragment);
    }
    @Test void explicitEmptyTagCannotInheritTheOriginalScriptsXmDefault() throws Exception {
        var flags = RsemFitRequest.flags(input(RsemAbundance.Release.V1_3_3, 3, ""), settings(RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, null), 64);
        assertEquals("", flags.get(flags.indexOf("--tag") + 1)); assertTrue(flags.containsAll(List.of("--alignments", "--no-bam-output", "--keep-intermediate-files", "--paired-end")));
    }
    @Test void assessedTagAndAllFourOriginalReadTypesArePreserved() throws Exception {
        for (int type = 0; type < 4; type++) {
            var flags = RsemFitRequest.flags(input(RsemAbundance.Release.V1_3_3, type, "XM"), settings(RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, null), 64);
            assertEquals("XM", flags.get(flags.indexOf("--tag") + 1)); assertEquals(type >= 2, flags.contains("--paired-end")); assertEquals((type & 1) == 0, flags.contains("--no-qualities"));
        }
    }
    @Test void legacyAndModernStrandControlsSelectTheExactSourceInterface() throws Exception {
        for (var strand : RsemFitRequest.Strand.values()) {
            var old = RsemFitRequest.flags(input(RsemAbundance.Release.V1_2_28, 3, ""), settings(strand, RsemFitRequest.Posterior.NONE, null), 64);
            assertTrue(old.contains("--forward-prob")); assertFalse(old.contains("--strandedness"));
            assertEquals(switch (strand) { case NONE -> "0.5"; case FORWARD -> "1"; case REVERSE -> "0"; }, old.get(old.indexOf("--forward-prob") + 1));
            for (var version : List.of(RsemAbundance.Release.V1_3_0, RsemAbundance.Release.V1_3_1, RsemAbundance.Release.V1_3_2, RsemAbundance.Release.V1_3_3)) {
                var current = RsemFitRequest.flags(input(version, 3, ""), settings(strand, RsemFitRequest.Posterior.NONE, null), 64);
                assertEquals(strand.name().toLowerCase(java.util.Locale.ROOT), current.get(current.indexOf("--strandedness") + 1)); assertFalse(current.contains("--forward-prob"));
            }
        }
    }
    @Test void posteriorMeanAndIntervalRequestsRetainTheirDifferentOriginalOperations() throws Exception {
        for (var posterior : RsemFitRequest.Posterior.values()) {
            var flags = RsemFitRequest.flags(input(RsemAbundance.Release.V1_3_3, 1, ""), settings(RsemFitRequest.Strand.NONE, posterior, null), 64);
            assertEquals(posterior != RsemFitRequest.Posterior.NONE, flags.contains("--calc-pme")); assertEquals(posterior == RsemFitRequest.Posterior.INTERVAL, flags.contains("--calc-ci"));
        }
    }
    @Test void zeroSdRetainsTheValidOriginalFixedFragmentLengthModel() throws Exception {
        var flags = RsemFitRequest.flags(input(RsemAbundance.Release.V1_3_3, 1, ""), settings(RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, new RsemFitRequest.Fragment(200, 0)), 64);
        assertEquals("0.0", flags.get(flags.indexOf("--fragment-length-sd") + 1));
        assertThrows(IOException.class, () -> RsemFitRequest.flags(input(RsemAbundance.Release.V1_3_3, 3, ""), settings(RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, new RsemFitRequest.Fragment(200, 20)), 64));
    }
    @Test void nonfiniteAndInvalidFragmentParametersCannotReachTheOriginalCommand() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0}) assertThrows(IllegalArgumentException.class, () -> new RsemFitRequest.Fragment(value, 20));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) assertThrows(IllegalArgumentException.class, () -> new RsemFitRequest.Fragment(200, value));
    }
    @Test void fullUnsignedSeedRangeAndAggregateThreadReserveAreExplicit() throws Exception {
        var options = input(RsemAbundance.Release.V1_3_3, 3, "");
        for (long seed : new long[]{0, 4294967295L}) {
            var flags = RsemFitRequest.flags(options, new RsemFitRequest.Settings(seed, 1, RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, false, false, null), 4);
            assertEquals(Long.toString(seed), flags.get(flags.indexOf("--seed") + 1));
        }
        assertThrows(IOException.class, () -> RsemFitRequest.flags(options, settings(RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, null), 3));
        for (long seed : new long[]{-1, 4294967296L}) assertThrows(IllegalArgumentException.class, () -> new RsemFitRequest.Settings(seed, 1, RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, false, false, null));
    }
    @Test void aDifferentNativeDiagnosticSeedThresholdIsNotSilentlyTreatedAsAnOriginalParserSetting() {
        assertThrows(IOException.class, () -> RsemFitRequest.flags(new RsemSamAlignments.Options(RsemAbundance.Release.V1_3_3, 3, "", 30), settings(RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, null), 64));
    }
}
