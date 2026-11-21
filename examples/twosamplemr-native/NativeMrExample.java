/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Locale;
import org.jlinalg.mr.TwoSampleMrStatistics;

/** Synthetic prepared effects; no data retrieval, selection or harmonization. */
public final class NativeMrExample {
    private NativeMrExample() { }
    public static void main(String[] args) {
        double[] exposure = {.13, -.21, .32, .17, -.27};
        double[] outcome = {.08, -.10, .17, .11, -.12};
        double[] exposureSe = {.025, .04, .015, .03, .08};
        double[] outcomeSe = {.035, .05, .04, .02, .045};
        var result = TwoSampleMrStatistics.evaluate(TwoSampleMrStatistics.Method.MR_IVW,
            exposure, outcome, exposureSe, outcomeSe);
        System.out.println("field\tkind\tvalue");
        result.statistics().forEach((name, value) -> System.out.printf(Locale.ROOT,
            "%s\t%s\t%.17g%n", name, value.kind(), value.value()));
        result.warnings().forEach(System.err::println);
        var point = TwoSampleMrStatistics.weightedMedianPoint(exposure, outcome, exposureSe, outcomeSe);
        System.out.printf(Locale.ROOT, "weighted_median_point\t%s\t%.17g%n", point.kind(), point.value());
    }
}
