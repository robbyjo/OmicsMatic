/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Statistical rules derived from MRCIEU/TwoSampleMR (MIT), commit
 * c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6, R/mr.R.
 * Copyright (c) 2019 Gibran Hemani. See LICENSES/TwoSampleMR-MIT.txt. */
package org.jlinalg.mr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdistlib.Binomial;
import jdistlib.ChiSquare;
import jdistlib.Normal;
import jdistlib.T;

/**
 * Prepared-data statistics for the pinned TwoSampleMR 0.7.14 source.
 * Inputs are complete finite, already harmonized independent-SNP vectors.
 * This API neither harmonizes alleles nor generates R-compatible random draws.
 * It does not change the separate {@link MendelianRandomization} API.
 */
public final class TwoSampleMrStatistics {
    /** Exact upstream source used for the statistical rules. */
    public static final String SOURCE_COMMIT = "c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6";
    /** Explicit dense input capacity. */
    public static final int MAX_VARIANTS = 1_000_000;
    /** Explicit capacity for each supplied bootstrap matrix. */
    public static final long MAX_BOOTSTRAP_CELLS = 20_000_000L;

    /** The source function to evaluate; MR_IVW and MR_IVW_MRE have different SE rules. */
    public enum Method {
        /** Single-SNP ratio. */ MR_WALD_RATIO,
        /** Direct first-order fixed estimate. */ MR_META_FIXED_SIMPLE,
        /** Multiplicative IVW with underdispersion correction. */ MR_IVW,
        /** Fixed IVW, including the source's sigma division. */ MR_IVW_FE,
        /** Multiplicative IVW without underdispersion correction. */ MR_IVW_MRE,
        /** Unweighted no-intercept regression. */ MR_UWR,
        /** Positively oriented Egger with Student t inference. */ MR_EGGER_REGRESSION,
        /** Exact sign concordance, which has no reported SE. */ MR_SIGN
    }

    /** Missing and nonfinite numerical results remain distinct. */
    public enum Kind { FINITE, NA, NAN, POSITIVE_INFINITY, NEGATIVE_INFINITY }

    /** A source statistic; NA is distinct from an arithmetic NaN. */
    public record Value(Kind kind, double value) {
        /** Checks the declared numerical kind. */
        public Value {
            if (kind == null || switch (kind) {
                case FINITE -> !Double.isFinite(value);
                case NA, NAN -> !Double.isNaN(value);
                case POSITIVE_INFINITY -> value != Double.POSITIVE_INFINITY;
                case NEGATIVE_INFINITY -> value != Double.NEGATIVE_INFINITY;
            }) throw new IllegalArgumentException("value differs from its declared numerical kind");
        }
        /** Represents the source's unavailable statistic. */
        public static Value na() { return new Value(Kind.NA, Double.NaN); }
        /** Retains a computed double without clamping it. */
        public static Value of(double value) {
            return new Value(Double.isNaN(value) ? Kind.NAN
                : value == Double.POSITIVE_INFINITY ? Kind.POSITIVE_INFINITY
                : value == Double.NEGATIVE_INFINITY ? Kind.NEGATIVE_INFINITY : Kind.FINITE, value);
        }
    }

    /** Named numerical fields only; R lm objects and R container attributes are not emulated. */
    public record Result(String method, Map<String, Value> statistics, List<String> warnings) {
        /** Owns the result population and diagnostics. */
        public Result {
            if (method == null || statistics == null || warnings == null)
                throw new IllegalArgumentException("method, statistics and warnings are required");
            statistics = Collections.unmodifiableMap(new LinkedHashMap<>(statistics));
            if (statistics.values().stream().anyMatch(v -> v == null))
                throw new IllegalArgumentException("null statistic");
            List<String> diagnostics = new ArrayList<>(warnings);
            List<String> unavailable = new ArrayList<>();
            for (String name : method.equals(Method.MR_SIGN.name()) ? List.of("b", "pval", "nsnp") : List.of("b", "se", "pval", "nsnp"))
                if (statistics.containsKey(name) && statistics.get(name).kind() != Kind.FINITE) unavailable.add(name);
            if (!unavailable.isEmpty() && diagnostics.stream().noneMatch(v -> v.startsWith("NO_COMPLETE_RESULT:")))
                diagnostics.add("NO_COMPLETE_RESULT: unavailable or nonfinite core statistics " + unavailable
                    + "; preserve the raw result and inspect the input count, design and numerical cause.");
            warnings = List.copyOf(diagnostics);
        }
    }

    private TwoSampleMrStatistics() { }

    /**
     * Evaluates a deterministic source method on complete finite vectors.
     * All standard errors must be positive. Empty vectors are accepted by the
     * source minimum-SNP guards except Wald, whose numeric(0) return is outside
     * this scalar API. Zero exposure effects are retained, not repaired.
     */
    public static Result evaluate(Method method, double[] exposure, double[] outcome,
            double[] exposureSe, double[] outcomeSe) {
        if (method == null) throw new IllegalArgumentException("method is required");
        Input in = input(exposure, outcome, exposureSe, outcomeSe);
        if (method == Method.MR_SIGN) return sign(in);
        if (method == Method.MR_WALD_RATIO) {
            if (in.size() == 0) throw new IllegalArgumentException("Wald numeric(0) output is outside this scalar API");
            if (in.size() > 1) return missing(method.name(), false,
                List.of("mr_wald_ratio requires exactly one SNP; source returns unavailable statistics."));
            double b = in.y[0] / in.x[0], se = in.sy[0] / Math.abs(in.x[0]);
            return result(method.name(), "b", b, "se", se, "pval", normal(Math.abs(b) / se), "nsnp", 1);
        }
        boolean egger = method == Method.MR_EGGER_REGRESSION;
        if (in.size() < (egger ? 3 : 2)) return missing(method.name(), egger, List.of());
        if (method == Method.MR_META_FIXED_SIMPLE) {
            double numerator = 0, denominator = 0;
            for (int i = 0; i < in.size(); i++) {
                numerator += in.x[i] * in.y[i] / (in.sy[i] * in.sy[i]);
                denominator += in.x[i] * in.x[i] / (in.sy[i] * in.sy[i]);
            }
            double b = numerator / denominator, se = Math.sqrt(1 / denominator);
            return result(method.name(), "b", b, "se", se, "pval", normal(Math.abs(b) / se), "nsnp", in.size());
        }
        double[] x = in.x.clone(), y = in.y.clone(), weights = new double[in.size()];
        for (int i = 0; i < in.size(); i++) {
            if (egger) { y[i] *= x[i] < 0 ? -1 : 1; x[i] = Math.abs(x[i]); }
            weights[i] = method == Method.MR_UWR ? 1 : 1 / (in.sy[i] * in.sy[i]);
            if (!(weights[i] > 0) || !Double.isFinite(weights[i]))
                throw new IllegalArgumentException("source regression weights exceed this finite positive-weight profile");
        }
        TwoSampleMrSmallQr.Fit fit = TwoSampleMrSmallQr.fit(x, y, weights, egger);
        if (fit.rank() != (egger ? 2 : 1)) {
            if (egger) return missing(method.name(), true,
                List.of("Collinearities in MR Egger, try LD pruning the exposure variables."));
            throw new IllegalArgumentException("source regression has no estimable b_exp coefficient");
        }
        int slope = egger ? 1 : 0, df = in.size() - (egger ? 2 : 1);
        double b = fit.coefficients()[slope];
        double se = Math.sqrt(fit.covarianceDiagonal()[slope] * fit.residualVariance());
        double divisor = method == Method.MR_IVW_FE ? fit.sigma()
            : method == Method.MR_IVW_MRE ? 1 : Math.min(1, fit.sigma());
        se /= divisor;
        double q = fit.sigma() * fit.sigma() * df;
        Result base = result(method.name(), "b", b, "se", se,
            "pval", egger ? student(Math.abs(b / se), df) : normal(Math.abs(b / se)),
            "nsnp", in.size(), "Q", q, "Q_df", df, "Q_pval", chi(q, df));
        if (fit.essentiallyPerfect()) {
            List<String> warnings = new ArrayList<>(base.warnings());
            warnings.add("Essentially perfect fit: numerical residual variance and inference may be unreliable; retain the raw values.");
            base = new Result(base.method(), base.statistics(), warnings);
        }
        if (!egger) return base;
        double intercept = fit.coefficients()[0];
        double interceptSe = Math.sqrt(fit.covarianceDiagonal()[0] * fit.residualVariance()) / divisor;
        Map<String, Value> fields = new LinkedHashMap<>(base.statistics());
        fields.put("b_i", Value.of(intercept)); fields.put("se_i", Value.of(interceptSe));
        fields.put("pval_i", Value.of(student(Math.abs(intercept / interceptSe), df)));
        return new Result(method.name(), fields, base.warnings());
    }

    /** Source weighted-median helper, including its unavailable boundary result. */
    public static Value weightedMedian(double[] ratios, double[] weights) {
        if (ratios == null || weights == null || ratios.length != weights.length
                || ratios.length > MAX_VARIANTS)
            throw new IllegalArgumentException("aligned ratio/weight vectors within capacity are required");
        Integer[] order = new Integer[ratios.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> ratios[i]));
        double sum = 0;
        for (int i : order) sum += weights[i];
        double[] middle = new double[order.length]; double cumulative = 0;
        int below = -1;
        for (int i = 0; i < order.length; i++) {
            cumulative += weights[order[i]];
            middle[i] = (cumulative - 0.5 * weights[order[i]]) / sum;
            if (middle[i] < 0.5) below = i;
        }
        if (below < 0 || below + 1 >= order.length) return Value.na();
        double first = ratios[order[below]], second = ratios[order[below + 1]];
        return Value.of(first + (second - first) * (0.5 - middle[below])
            / (middle[below + 1] - middle[below]));
    }

    /** Delta-method weighted-median point estimate; no bootstrap uncertainty is implied. */
    public static Value weightedMedianPoint(double[] exposure, double[] outcome,
            double[] exposureSe, double[] outcomeSe) {
        Input in = input(exposure, outcome, exposureSe, outcomeSe);
        if (in.size() < 3) return Value.na();
        return median(in, deltaWeights(in));
    }

    /**
     * Source simple/weighted-median inference from explicitly supplied sampled
     * effects. Matrices are bootstrap replicate by variant, not standard-normal
     * deviates. Original-input weights remain fixed for every replicate.
     * This does not generate or claim to reproduce R's RNG stream.
     */
    public static Result medianFromDraws(boolean weighted, double[] exposure, double[] outcome,
            double[] exposureSe, double[] outcomeSe, double[][] sampledExposure, double[][] sampledOutcome) {
        Input in = input(exposure, outcome, exposureSe, outcomeSe);
        String method = weighted ? "mr_weighted_median_supplied_draws" : "mr_simple_median_supplied_draws";
        if (in.size() < 3) return missing(method, false, List.of());
        if (sampledExposure == null || sampledOutcome == null || sampledExposure.length != sampledOutcome.length
                || (long) sampledExposure.length * in.size() > MAX_BOOTSTRAP_CELLS)
            throw new IllegalArgumentException("paired bootstrap matrices within capacity are required");
        double[] weights = weighted ? deltaWeights(in) : new double[in.size()];
        if (!weighted) Arrays.fill(weights, 1.0 / in.size());
        Value point = median(in, weights); Value[] replicates = new Value[sampledExposure.length];
        for (int b = 0; b < replicates.length; b++) {
            if (sampledExposure[b] == null || sampledOutcome[b] == null
                    || sampledExposure[b].length != in.size() || sampledOutcome[b].length != in.size())
                throw new IllegalArgumentException("bootstrap replicate has a different variant population");
            double[] ratios = new double[in.size()];
            for (int j = 0; j < ratios.length; j++) {
                if (!Double.isFinite(sampledExposure[b][j]) || !Double.isFinite(sampledOutcome[b][j]))
                    throw new IllegalArgumentException("supplied sampled effects must be finite; zero exposure draws are retained");
                ratios[j] = sampledOutcome[b][j] / sampledExposure[b][j];
            }
            replicates[b] = weightedMedian(ratios, weights);
        }
        Value se = standardDeviation(replicates);
        Map<String, Value> fields = new LinkedHashMap<>(); fields.put("b", point); fields.put("se", se);
        fields.put("pval", point.kind() == Kind.NA || se.kind() == Kind.NA ? Value.na()
            : Value.of(normal(Math.abs(point.value() / se.value()))));
        if (weighted) for (String name : List.of("Q", "Q_df", "Q_pval")) fields.put(name, Value.na());
        fields.put("nsnp", Value.of(in.size()));
        return new Result(method, fields, List.of("Supplied-effect bootstrap draws; no R RNG identity is implied."));
    }

    private static Value standardDeviation(Value[] values) {
        if (values.length < 2) return Value.na();
        double mean = 0;
        for (Value value : values) {
            if (value.kind() == Kind.NA || value.kind() == Kind.NAN) return Value.na();
            mean += value.value() / values.length;
        }
        double squares = 0, correction = 0;
        for (Value value : values) { double d = value.value() - mean; squares += d * d; correction += d; }
        return Value.of(Math.sqrt((squares - correction * correction / values.length) / (values.length - 1)));
    }

    private static double[] deltaWeights(Input in) {
        double[] weights = new double[in.size()];
        for (int i = 0; i < weights.length; i++) {
            double variance = (in.sy[i] * in.sy[i]) / (in.x[i] * in.x[i])
                + (in.y[i] * in.y[i]) * (in.sx[i] * in.sx[i]) / Math.pow(in.x[i], 4);
            weights[i] = 1 / variance;
        }
        return weights;
    }
    private static Value median(Input in, double[] weights) {
        double[] ratios = new double[in.size()];
        for (int i = 0; i < ratios.length; i++) ratios[i] = in.y[i] / in.x[i];
        return weightedMedian(ratios, weights);
    }
    private static Result sign(Input in) {
        int n = 0, concordant = 0;
        for (int i = 0; i < in.size(); i++) if (in.x[i] != 0 && in.y[i] != 0) {
            n++; if ((in.x[i] > 0) == (in.y[i] > 0)) concordant++;
        }
        if (n < 6) return missing(Method.MR_SIGN.name(), false, List.of());
        Map<String, Value> fields = new LinkedHashMap<>(); fields.put("b", Value.of(((double) concordant / n - 0.5) * 2));
        fields.put("se", Value.na()); fields.put("pval", Value.of(Math.min(1,
            2 * Binomial.cumulative(Math.min(concordant, n - concordant), n, 0.5, true, false))));
        fields.put("nsnp", Value.of(n)); return new Result(Method.MR_SIGN.name(), fields, List.of());
    }
    private static Input input(double[] x, double[] y, double[] sx, double[] sy) {
        if (x == null || y == null || sx == null || sy == null || x.length != y.length
                || x.length != sx.length || x.length != sy.length || x.length > MAX_VARIANTS)
            throw new IllegalArgumentException("four aligned vectors within capacity are required");
        x = x.clone(); y = y.clone(); sx = sx.clone(); sy = sy.clone();
        for (int i = 0; i < x.length; i++) if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])
                || !(sx[i] > 0) || !Double.isFinite(sx[i]) || !(sy[i] > 0) || !Double.isFinite(sy[i]))
            throw new IllegalArgumentException("prepared complete finite effects and positive standard errors are required; no rows are omitted or repaired");
        return new Input(x, y, sx, sy);
    }
    private record Input(double[] x, double[] y, double[] sx, double[] sy) { int size() { return x.length; } }
    private static Result missing(String method, boolean egger, List<String> warnings) {
        Map<String, Value> fields = new LinkedHashMap<>();
        for (String name : List.of("b", "se", "pval", "nsnp")) fields.put(name, Value.na());
        if (egger) for (String name : List.of("b_i", "se_i", "pval_i", "Q", "Q_df", "Q_pval")) fields.put(name, Value.na());
        return new Result(method, fields, warnings);
    }
    private static Result result(String method, Object... pairs) {
        Map<String, Value> fields = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) fields.put((String) pairs[i], Value.of(((Number) pairs[i + 1]).doubleValue()));
        return new Result(method, fields, List.of());
    }
    private static double normal(double statistic) { return 2 * Normal.cumulative(statistic, 0, 1, false, false); }
    private static double student(double statistic, int df) { return 2 * T.cumulative(statistic, df, false, false); }
    private static double chi(double statistic, int df) { return ChiSquare.cumulative(statistic, df, false, false); }
}
