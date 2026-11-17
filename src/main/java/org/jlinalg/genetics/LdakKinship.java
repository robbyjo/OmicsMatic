/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jdistlib.accelerator.MatrixTranspose;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/**
 * Native Java construction of the selected LDAK 5.2 hard-call kinship model.
 *
 * <p>Each selected BIM A1 dosage is centered on its observed mean; missing calls
 * become zero after centering. Predictors are scaled by {@code V^(power/2)} and
 * {@code sqrt(weight)}. With HWE standardization, V is mean*(1-mean/2). Otherwise
 * V is the variance <em>after mean imputation</em>, with the full sample count
 * as denominator. The matrix is normalized to mean diagonal one. These rules
 * differ from an ordinary average of standardized variant outer products.
 *
 * <p>This class neither thins variants nor estimates heritability. Caller-selected
 * predictors and weights define the model; calling it with all variants does
 * not establish the LDAK-Thin model. No ancestry, ploidy or reference panel is
 * inferred. Input rows must be diploid hard calls, as provided by PLINK BED.
 * Corresponding original-runtime fixtures are under genetics/ldak52.
 */
public final class LdakKinship {
    /** The estimate bounds principal numeric buffers, not total JVM/backend RSS. */
    public record Options(double power, boolean hweStandardization, int blockVariants,
            long maximumNumericBytes) {
        public Options {
            if (!Double.isFinite(power) || blockVariants < 1 || maximumNumericBytes < 1)
                throw new IllegalArgumentException("Finite power and positive block/buffer bounds required");
        }
    }

    /** Omitted predictors remain visible with a reason; no genotype is repaired. */
    public enum Status { USED, ZERO_WEIGHT, NO_CALLS, CONSTANT }

    /** Scaling excludes sqrt(weight), matching the original .grm.details convention. */
    public record Predictor(String id, long sourceOrdinal, int calledSamples, double mean,
            double imputedVariance, double scaling, double weight, Status status) { }

    /** Immutable sample-aligned matrix and all selected-predictor dispositions. */
    public static final class Result {
        private final List<LdakBedDataset.Sample> samples;
        private final List<Predictor> predictors;
        private final double[] matrix;
        private final double denominator;
        private final int predictorsUsed;
        private Result(List<LdakBedDataset.Sample> samples, List<Predictor> predictors,
                double[] matrix, double denominator, int predictorsUsed) {
            this.samples = List.copyOf(samples);
            this.predictors = List.copyOf(predictors);
            this.matrix = matrix;
            this.denominator = denominator;
            this.predictorsUsed = predictorsUsed;
        }
        public List<LdakBedDataset.Sample> samples() { return samples; }
        public List<Predictor> predictors() { return predictors; }
        /** Row-major FP64 matrix in exact FAM order; the caller owns this copy. */
        public double[] matrix() { return matrix.clone(); }
        public double denominator() { return denominator; }
        public int predictorsUsed() { return predictorsUsed; }
    }

    private LdakKinship() { }

    /**
     * Computes on the caller's admitted BED selection, without modifying inputs.
     * Null weights gives every selected predictor weight one. A supplied map
     * assigns zero to unlisted selected IDs, as in the original weights profile;
     * unknown map IDs are rejected so an identifier mismatch cannot be hidden.
     * Only the selected rows are decoded. Metadata and returned matrix copies
     * are separate from the principal numeric-buffer admission bound.
     */
    public static Result calculate(LdakBedDataset data, Options options,
            Map<String, Double> weights) throws IOException {
        Objects.requireNonNull(data, "BED dataset");
        Objects.requireNonNull(options, "Kinship options");
        List<LdakBedDataset.Variant> variants = data.selectedVariants();
        if (variants.isEmpty()) throw new IllegalArgumentException("Select at least one predictor explicitly");
        Map<String, Double> admitted = weights == null ? null : Map.copyOf(weights);
        if (admitted != null) {
            var ids = new HashSet<String>();
            for (var variant : variants) ids.add(variant.id());
            if (!ids.containsAll(admitted.keySet()))
                throw new IllegalArgumentException("Weight IDs must belong to the admitted predictor selection");
            for (double weight : admitted.values()) if (!Double.isFinite(weight) || weight < 0)
                throw new IllegalArgumentException("Predictor weights must be finite and nonnegative");
        }
        int samples = data.samples().size();
        int capacity = Math.min(options.blockVariants(), variants.size());
        long elements;
        try {
            elements = Math.addExact(Math.multiplyExact((long) samples, samples),
                Math.multiplyExact(2L * samples, (long) capacity + 1));
            if (Math.multiplyExact(elements, 8L) > options.maximumNumericBytes())
                throw new IllegalArgumentException("Principal numeric-buffer limit exceeded; do not truncate samples or predictors");
            Math.toIntExact(Math.multiplyExact((long) samples, samples));
            Math.toIntExact(Math.multiplyExact((long) samples, capacity));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Dense kinship dimensions exceed Java array/buffer bounds", overflow);
        }
        double[] matrix = new double[Math.multiplyExact(samples, samples)];
        double[] block = new double[Math.multiplyExact(samples, capacity)];
        var details = new ArrayList<Predictor>(variants.size());
        double denominator = 0;
        int filled = 0, used = 0;
        try (BackendContext context = BackendContext.select(BackendPolicy.CPU)) {
            for (var variant : variants) {
                double weight = admitted == null ? 1 : admitted.getOrDefault(variant.id(), 0.0);
                if (weight == 0) {
                    details.add(new Predictor(variant.id(), variant.ordinal(), 0, Double.NaN,
                        Double.NaN, 0, weight, Status.ZERO_WEIGHT));
                    continue;
                }
                double[] dosage = data.readAllele1Dosages(variant);
                int called = 0;
                double sum = 0;
                for (double value : dosage) {
                    if (Double.isNaN(value)) continue;
                    if (value != 0 && value != 1 && value != 2)
                        throw new IllegalArgumentException("Only diploid hard calls or NaN are admitted: " + variant.id());
                    called++;
                    sum += value;
                }
                if (called == 0) {
                    details.add(new Predictor(variant.id(), variant.ordinal(), 0, Double.NaN,
                        Double.NaN, 0, weight, Status.NO_CALLS));
                    continue;
                }
                double mean = sum / called, squared = 0;
                for (double value : dosage) if (!Double.isNaN(value)) squared += (value - mean) * (value - mean);
                double observed = squared / samples;
                if (observed == 0) {
                    details.add(new Predictor(variant.id(), variant.ordinal(), called, mean,
                        observed, 0, weight, Status.CONSTANT));
                    continue;
                }
                double variance = options.hweStandardization() ? mean * (1 - mean / 2) : observed;
                double scaling = Math.pow(variance, options.power() / 2);
                double multiplier = scaling * Math.sqrt(weight);
                double contribution = observed * multiplier * multiplier;
                if (!(contribution > 0) || !Double.isFinite(contribution))
                    throw new IllegalArgumentException("Nonfinite/underflowed predictor scaling: " + variant.id());
                denominator += contribution;
                if (!Double.isFinite(denominator)) throw new IllegalArgumentException("Kinship denominator overflow");
                for (int sample = 0; sample < samples; sample++)
                    block[filled * samples + sample] = Double.isNaN(dosage[sample]) ? 0 : (dosage[sample] - mean) * multiplier;
                details.add(new Predictor(variant.id(), variant.ordinal(), called, mean,
                    observed, scaling, weight, Status.USED));
                used++;
                filled++;
                if (filled == capacity) {
                    context.backend().dgemm(MatrixTranspose.TRANSPOSE, MatrixTranspose.NONE,
                        samples, samples, filled, 1, block, block, 1, matrix);
                    filled = 0;
                }
            }
            if (filled != 0) {
                double[] partial = Arrays.copyOf(block, Math.multiplyExact(samples, filled));
                context.backend().dgemm(MatrixTranspose.TRANSPOSE, MatrixTranspose.NONE,
                    samples, samples, filled, 1, partial, partial, 1, matrix);
            }
        }
        if (used == 0 || !(denominator > 0))
            throw new IllegalArgumentException("No positive-weight variable predictors remain; no kinship result");
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] /= denominator;
            if (!Double.isFinite(matrix[i])) throw new IllegalArgumentException("Nonfinite kinship result");
        }
        return new Result(data.samples(), details, matrix, denominator, used);
    }
}
