/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.Deseq2SizeFactors;

/** Complete native normalization/linear-geometry checks for original v2 saved QC objects.
 * Rows are genes, columns are samples; PCA scores have samples in rows. VST/rlog
 * values are original saved assays, never a native replacement for their nonlinear fit.
 * Callers retain labels and source row order separately and must not mutate arrays.
 */
public final class NfcoreChipseq200Qc {
    private NfcoreChipseq200Qc() { }
    public record Limits(long matrixEntries, long arithmeticVisits, int samples) {
        public Limits {
            if (matrixEntries < 1 || matrixEntries > 100000000L || arithmeticVisits < 1 || arithmeticVisits > 1000000000L || samples < 2 || samples > 4096) throw new IllegalArgumentException("Require bounded positive complete-QC capacities");
        }
        public static Limits defaults() { return new Limits(20000000, 100000000, 1024); }
    }
    public record Result(int genes, int samples, int components, long normalizedCells,
                         long completeDistanceCells, double centeredGramTrace, List<String> warnings) {
        public Result { warnings = List.copyOf(warnings); }
        public Map<String, Object> document() {
            return Map.of("genes", genes, "samples", samples, "complete_PCs", components, "normalized_cells_checked", normalizedCells,
                "complete_distance_cells_checked", completeDistanceCells, "centered_Gram_trace", centeredGramTrace,
                "all_genes_used", true, "nonlinear_transform_independently_recomputed", false, "negative_binomial_model_refitted", false, "warnings", warnings);
        }
    }
    public static Result audit(int[][] counts, double[] factors, double[][] normalized, double[][] transformed,
                               double[][] scores, double[] sdev, double[] fractions, double[][] distances, Limits limits) {
        if (limits == null || counts == null || counts.length < 2 || counts[0] == null || counts[0].length < 2) fail("Require at least two complete genes and samples; no PCA substitute is returned");
        int genes = counts.length, samples = counts[0].length, components = Math.min(genes, samples);
        if (samples > limits.samples() || (long) genes * samples > limits.matrixEntries() || (long) genes * samples * samples > limits.arithmeticVisits()) fail("Complete QC capacity exceeded; no truncated matrix is returned");
        if (factors == null || factors.length != samples || sdev == null || sdev.length != components || fractions == null || fractions.length != components) fail("Complete QC factor/component dimensions differ");
        shape(normalized, genes, samples); shape(transformed, genes, samples); shape(scores, samples, components); shape(distances, samples, samples);
        var normalLimits = new Deseq2SizeFactors.Limits(limits.matrixEntries(), Math.max(2L * genes * samples, limits.arithmeticVisits()), genes);
        var expected = Deseq2SizeFactors.estimate(counts, Deseq2SizeFactors.Type.RATIO, null, null, normalLimits, () -> false);
        if (!expected.usableForFit()) fail("Original ratio factors are unassessable; no missing/nonfinite value is replaced");
        double[] nativeFactors = expected.factors();
        for (int sample = 0; sample < samples; sample++) { if (!(factors[sample] > 0) || !Double.isFinite(factors[sample])) fail("Original size factor is unassessable"); close(nativeFactors[sample], factors[sample], 2e-11, "Complete original ratio factor differs"); }
        double[][] centered = new double[genes][samples];
        for (int gene = 0; gene < genes; gene++) {
            var mean = new Sum(); for (double value : transformed[gene]) mean.add(value);
            double center = mean.value() / samples; finite(center);
            for (int sample = 0; sample < samples; sample++) { close(counts[gene][sample] / factors[sample], normalized[gene][sample], 2e-11, "Complete normalized-count cell differs"); centered[gene][sample] = transformed[gene][sample] - center; finite(centered[gene][sample]); }
        }
        double[][] gram = new double[samples][samples]; var trace = new Sum();
        for (int a = 0; a < samples; a++) for (int b = a; b < samples; b++) {
            var product = new Sum(); var square = new Sum();
            for (int gene = 0; gene < genes; gene++) { product.add(centered[gene][a] * centered[gene][b]); double d = transformed[gene][a] - transformed[gene][b]; square.add(d * d); }
            gram[a][b] = gram[b][a] = product.value(); finite(gram[a][b]);
            double distance = Math.sqrt(square.value()); close(distance, distances[a][b], 2e-11, "Complete Euclidean distance differs"); close(distance, distances[b][a], 2e-11, "Complete symmetric Euclidean distance differs");
            if (a == b) trace.add(gram[a][a]);
        }
        double total = trace.value(); if (!(total > 0) || !Double.isFinite(total)) fail("All-gene centered variance is unassessable; no PCA is manufactured");
        // Verify the entire SVD basis by its Gram reconstruction, centered eigenvectors,
        // orthogonality and eigenvalues. Signs and rotations within equal eigenspaces
        // are allowed. No unique axis sign or invented component is required.
        var spectrumTotal = new Sum();
        for (int pc = 0; pc < components; pc++) { finite(sdev[pc]); if (sdev[pc] < 0 || pc > 0 && sdev[pc] > sdev[pc - 1] + 2e-11 * Math.max(1, sdev[pc - 1])) fail("PCA spectrum is negative or not descending"); spectrumTotal.add((samples - 1.0) * sdev[pc] * sdev[pc]); }
        absolute(spectrumTotal.value(), total, total, "Complete PCA spectrum does not reconcile with all-gene variance");
        for (int pc = 0; pc < components; pc++) {
            double eigenvalue = (samples - 1.0) * sdev[pc] * sdev[pc]; close(eigenvalue / total, fractions[pc], 2e-11, "PCA variance fraction differs"); double[] axis = new double[samples]; for (int s = 0; s < samples; s++) axis[s] = scores[s][pc]; auditAxis(gram, axis, eigenvalue, total);
            for (int previous = 0; previous < pc; previous++) { var dot = new Sum(); for (int s = 0; s < samples; s++) dot.add(scores[s][pc] * scores[s][previous]); absolute(dot.value(), 0, total, "PCA columns are not orthogonal"); }
        }
        for (int a = 0; a < samples; a++) for (int b = a; b < samples; b++) { var reconstructed = new Sum(); for (int pc = 0; pc < components; pc++) reconstructed.add(scores[a][pc] * scores[b][pc]); absolute(reconstructed.value(), gram[a][b], total, "Complete PCA basis does not reconstruct all-gene centered Gram matrix"); }
        return new Result(genes, samples, components, (long) genes * samples, (long) samples * samples, total, expected.warnings());
    }
    /** Audit published PC1/PC2 independently of arbitrary SVD signs/degenerate rotations. */
    public static void auditPublishedAxes(double[][] transformed, double[][] published, double[] sdev, Limits limits) {
        if (transformed == null || transformed.length < 2 || transformed[0] == null || transformed[0].length < 2 || limits == null) fail("Unassessable published PCA population");
        int samples = transformed[0].length, genes = transformed.length;
        if (samples > limits.samples() || (long) genes * samples > limits.matrixEntries() || (long) genes * samples * samples > limits.arithmeticVisits()) fail("Published PCA capacity exceeded");
        shape(transformed, genes, samples); shape(published, samples, 2); if (sdev == null || sdev.length < 2) fail("Missing published PCA spectrum");
        double[][] gram = new double[samples][samples]; var trace = new Sum();
        for (int g = 0; g < genes; g++) { var sum = new Sum(); for (double v : transformed[g]) sum.add(v); double mean = sum.value() / samples; for (int a = 0; a < samples; a++) for (int b = 0; b < samples; b++) gram[a][b] += (transformed[g][a] - mean) * (transformed[g][b] - mean); }
        for (int s = 0; s < samples; s++) trace.add(gram[s][s]); double total = trace.value(); if (!(total > 0) || !Double.isFinite(total)) fail("Unassessable all-gene published PCA variance");
        for (int pc = 0; pc < 2; pc++) { finite(sdev[pc]); if (sdev[pc] < 0) fail("Negative published PCA spectrum"); double[] axis = new double[samples]; for (int s = 0; s < samples; s++) axis[s] = published[s][pc]; auditAxis(gram, axis, (samples - 1.0) * sdev[pc] * sdev[pc], total); }
        var dot = new Sum(); for (int s = 0; s < samples; s++) dot.add(published[s][0] * published[s][1]); absolute(dot.value(), 0, total, "Published PCA columns are not orthogonal");
    }
    private static void auditAxis(double[][] gram, double[] axis, double eigenvalue, double trace) {
        var norm = new Sum(); var center = new Sum(); for (double value : axis) { finite(value); norm.add(value * value); center.add(value); }
        absolute(norm.value(), eigenvalue, trace, "PCA coordinate norm differs from source eigenvalue"); absolute(center.value(), 0, Math.sqrt(trace), "PCA axis is not centered");
        for (int a = 0; a < axis.length; a++) { var product = new Sum(); for (int b = 0; b < axis.length; b++) product.add(gram[a][b] * axis[b]); absolute(product.value(), eigenvalue * axis[a], trace * Math.sqrt(trace), "PCA coordinates are not an all-gene eigenvector"); }
    }
    private static void shape(double[][] matrix, int rows, int columns) { if (matrix == null || matrix.length != rows) fail("Complete QC matrix row count differs"); for (double[] row : matrix) { if (row == null || row.length != columns) fail("Complete QC matrix is ragged"); for (double value : row) finite(value); } }
    private static void finite(double number) { if (!Double.isFinite(number)) fail("Nonfinite original QC value; no imputation or repair"); }
    private static void close(double a, double b, double relative, String message) { finite(a); finite(b); if (Math.abs(a - b) > relative * Math.max(1, Math.max(Math.abs(a), Math.abs(b)))) fail(message); }
    private static void absolute(double a, double b, double scale, String message) { finite(a); finite(b); finite(scale); if (Math.abs(a - b) > 2e-10 * Math.max(1, Math.abs(scale))) fail(message); }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
    private static final class Sum { private double sum, correction; void add(double v) { finite(v); double next = sum + v; correction += Math.abs(sum) >= Math.abs(v) ? (sum - next) + v : (v - next) + sum; sum = next; } double value() { return sum + correction; } }
}
