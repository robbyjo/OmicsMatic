/*
 * Copyright (C) 2026 JLinAlg contributors
 * Batch orchestration follows FinucaneLab/pops, commit
 * 76eb86cba10254490003c8f4dc7ff5ce492d3667, GPL-3.0.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import jdistlib.ChiSquare;
import jdistlib.accelerator.ComputeBackend;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/** PoPS v0.2 fitting with bounded feature batches and exact literal row mapping. */
public final class PopsChunked {
    private PopsChunked() { }

    /** A synchronously borrowed finite matrix in the source's prediction-row order. */
    public record Batch(int firstColumn, double[][] values) { }

    /**
     * Read-only, repeatable source. Batches must form a contiguous partition of
     * the original feature columns. Calls are synchronous and thread confined.
     * Every reread is fingerprinted; changed values or partitioning are rejected.
     */
    public interface Features {
        int rows();
        int columns();
        int batches();
        Batch read(int batch) throws IOException;
    }

    private static final class Reader {
        final Features source;
        final int rows, columns, batches;
        final String[] digests;
        final int[] starts, widths;
        Reader(Features source) {
            if (source == null || source.rows() < 1 || source.columns() < 1 || source.batches() < 1 || source.batches() > source.columns())
                throw new IllegalArgumentException("Nonempty bounded feature source required");
            this.source = source; rows = source.rows(); columns = source.columns(); batches = source.batches();
            digests = new String[batches]; starts = new int[batches]; widths = new int[batches];
        }
        Batch read(int b, int expectedStart) throws IOException {
            if (source.rows() != rows || source.columns() != columns || source.batches() != batches) throw new IOException("Feature source dimensions changed");
            Batch batch = source.read(b);
            if (batch == null || batch.values == null || batch.values.length != rows || batch.values[0] == null || batch.values[0].length == 0)
                throw new IOException("Feature batch has invalid row/column dimensions");
            int width = batch.values[0].length;
            if (batch.firstColumn < 0 || (long) batch.firstColumn + width > columns || (expectedStart >= 0 && batch.firstColumn != expectedStart))
                throw new IOException("Feature batches do not form the declared contiguous column partition");
            String digest = fingerprint(batch);
            if (digests[b] == null) { digests[b] = digest; starts[b] = batch.firstColumn; widths[b] = width; }
            else if (!digests[b].equals(digest) || starts[b] != batch.firstColumn || widths[b] != width) throw new IOException("Feature batch changed between PoPS passes: " + b);
            return batch;
        }
        int batchFor(int feature) {
            for (int b = 0; b < batches; b++) if (feature >= starts[b] && feature < starts[b] + widths[b]) return b;
            throw new IllegalArgumentException("Selected feature is outside the source partition");
        }
    }

    /**
     * Maps target row i to prediction-row ordinal targetRows[i]. The explicit
     * maximum is a capacity bound: exceeding it fails instead of dropping
     * selected features. Returned predictions cover every source row, while
     * projectedTarget retains the supplied target-row order.
     */
    public static Pops.Result fit(double[] target, Features features, int[] targetRows,
            double[][] covariates, PopsCovariance covariance, boolean[] projectionMask,
            boolean[] selectionMask, boolean[] trainingMask, Pops.Selection selection,
            Pops.Method method, BackendPolicy policy, int maximumSelectedFeatures) throws IOException {
        if (target == null || target.length < 2 || targetRows == null || targetRows.length != target.length
                || selection == null || method == null || policy == null || maximumSelectedFeatures < 1)
            throw new IllegalArgumentException("Target, explicit row mapping, settings, backend and selected-feature capacity required");
        Reader reader = new Reader(features); int n = target.length, p = reader.columns;
        int[] mapping = targetRows.clone(); boolean[] seen = new boolean[reader.rows];
        for (int i : mapping) {
            if (i < 0 || i >= reader.rows || seen[i]) throw new IllegalArgumentException("Target row mapping is duplicated or out of range");
            seen[i] = true;
        }
        double[] y = target.clone(); for (double value : y) PopsMath.finite(value, "PoPS target");
        double[][] cov = covariates == null ? null : PopsMath.copy(covariates, n, "PoPS target covariates");
        if (covariance != null && covariance.size() != n) throw new IllegalArgumentException("Covariance target dimension differs");
        int[] selectionRows = PopsMath.indices(selectionMask, n, "feature selection"), trainingRows = PopsMath.indices(trainingMask, n, "training");
        validate(selection, p);
        if (selection.forwardSteps() != null && selection.forwardSteps() > maximumSelectedFeatures)
            throw new IllegalArgumentException("Forward selection exceeds selected-feature capacity; increase capacity without changing scientific settings");
        try (var context = BackendContext.select(policy)) {
            ComputeBackend backend = context.backend();
            if (cov != null) y = projectTarget(y, cov, covariance, PopsMath.indices(projectionMask, n, "target projection"), backend);
            PopsBlockWhitening selectionWhitening = covariance == null ? null : PopsBlockWhitening.prepare(covariance, selectionRows, backend);
            Pops.Marginal[] marginals = marginalPass(reader, mapping, y, null, selectionRows, selectionWhitening, backend);
            boolean[] selected; int[] ranks = new int[p];
            if (selection.forwardSteps() != null) {
                selected = new boolean[p]; double[][] stepCov = PopsMath.intercept(n);
                for (int step = 1; step <= selection.forwardSteps(); step++) {
                    Pops.Marginal[] current = marginalPass(reader, mapping, y, stepCov, selectionRows, selectionWhitening, backend);
                    int[] remaining = new int[p - step + 1]; double[] values = new double[remaining.length];
                    for (int j = 0, k = 0; j < p; j++) if (!selected[j]) { remaining[k] = j; values[k++] = current[j].pValue(); }
                    int top = remaining[PopsOrder.argsort(values)[0]]; selected[top] = true; ranks[top] = step;
                    Batch batch = reader.read(reader.batchFor(top), -1); double[] added = new double[n];
                    for (int i = 0; i < n; i++) added[i] = batch.values[mapping[i]][top - batch.firstColumn];
                    stepCov = PopsMath.append(stepCov, added);
                }
            } else selected = select(marginals, selection);
            int count = 0; for (boolean value : selected) if (value) count++;
            if (count == 0) throw new IllegalArgumentException("PoPS selected no features; no fitted model");
            if (count > maximumSelectedFeatures) throw new IllegalArgumentException("Selected features exceed declared capacity; increase capacity and retry without discarding features");
            Math.multiplyExact(trainingRows.length, count);
            int[] columns = new int[count], columnPositions = new int[p]; Arrays.fill(columnPositions, -1);
            for (int j = 0, k = 0; j < p; j++) if (selected[j]) { columns[k] = j; columnPositions[j] = k++; }
            double[][] trainX = new double[trainingRows.length][count];
            for (int b = 0; b < reader.batches; b++) {
                Batch batch = reader.read(b, -1);
                for (int j = 0; j < batch.values[0].length; j++) {
                    int destination = columnPositions[batch.firstColumn + j]; if (destination < 0) continue;
                    for (int i = 0; i < trainingRows.length; i++) trainX[i][destination] = batch.values[mapping[trainingRows[i]]][j];
                }
            }
            double[][] trainY = PopsMath.column(y, trainingRows), intercept = PopsMath.intercept(trainingRows.length);
            if (covariance != null) {
                var whitening = PopsBlockWhitening.prepare(covariance, trainingRows, backend);
                trainX = whitening.apply(trainX); trainY = whitening.apply(trainY); intercept = whitening.apply(intercept);
            }
            trainX = PopsMath.project(trainX, intercept, backend); trainY = PopsMath.project(trainY, intercept, backend);
            double[] response = new double[trainY.length]; for (int i = 0; i < response.length; i++) response[i] = trainY[i][0];
            double[] beta, cvScores = new double[0]; double alpha = Double.NaN, bestScore = Double.NaN;
            if (method == Pops.Method.RIDGE) {
                var ridge = PopsMath.ridge(trainX, response, backend); beta = ridge.coefficients(); alpha = ridge.alpha(); bestScore = ridge.bestScore(); cvScores = ridge.scores();
            } else beta = PopsMath.minimumNorm(trainX, response, backend);
            double[] coefficients = new double[p]; for (int j = 0; j < count; j++) coefficients[columns[j]] = beta[j];
            double[] predictions = new double[reader.rows];
            for (int b = 0; b < reader.batches; b++) {
                Batch batch = reader.read(b, -1);
                for (int i = 0; i < reader.rows; i++) for (int j = 0; j < batch.values[i].length; j++)
                    if (selected[batch.firstColumn + j]) predictions[i] += batch.values[i][j] * coefficients[batch.firstColumn + j];
            }
            for (double value : predictions) PopsMath.finite(value, "PoPS prediction");
            return new Pops.Result(coefficients, predictions, y, marginals, columns, ranks, method, alpha, bestScore, cvScores, backend.id());
        }
    }

    private static double[] projectTarget(double[] y, double[][] covariates, PopsCovariance covariance, int[] rows, ComputeBackend backend) {
        double[][] cov = PopsMath.withInterceptIfNeeded(covariates), x = PopsMath.rows(cov, rows), target = PopsMath.column(y, rows);
        if (covariance != null) { var whitening = PopsBlockWhitening.prepare(covariance, rows, backend); x = whitening.apply(x); target = whitening.apply(target); }
        double[] response = new double[rows.length]; for (int i = 0; i < rows.length; i++) response[i] = target[i][0];
        double[] beta = PopsMath.minimumNorm(x, response, backend), result = y.clone();
        for (int i = 0; i < result.length; i++) { for (int j = 0; j < beta.length; j++) result[i] -= cov[i][j] * beta[j]; PopsMath.finite(result[i], "projected target"); }
        return result;
    }

    private static Pops.Marginal[] marginalPass(Reader reader, int[] mapping, double[] y, double[][] covariates,
            int[] rows, PopsBlockWhitening whitening, ComputeBackend backend) throws IOException {
        double[][] cov = covariates == null ? PopsMath.intercept(y.length) : PopsMath.withInterceptIfNeeded(covariates);
        double[][] subCov = PopsMath.rows(cov, rows), subY = PopsMath.column(y, rows);
        if (whitening != null) { subCov = whitening.apply(subCov); subY = whitening.apply(subY); }
        subY = PopsMath.project(subY, subCov, backend);
        double[] response = new double[rows.length]; for (int i = 0; i < rows.length; i++) response[i] = subY[i][0];
        double variance = PopsMath.variance(response); Pops.Marginal[] result = new Pops.Marginal[reader.columns]; int first = 0;
        for (int b = 0; b < reader.batches; b++) {
            Batch batch = reader.read(b, first); int width = batch.values[0].length;
            double[][] x = new double[rows.length][width];
            for (int i = 0; i < rows.length; i++) System.arraycopy(batch.values[mapping[rows[i]]], 0, x[i], 0, width);
            if (whitening != null) x = whitening.apply(x);
            x = PopsMath.project(x, subCov, backend);
            for (int j = 0; j < width; j++) {
                double sumSquares = 0, cross = 0;
                for (int i = 0; i < rows.length; i++) { sumSquares += x[i][j] * x[i][j]; cross += subY[i][0] * x[i][j]; }
                if (Math.abs(sumSquares) <= 1e-8) { result[first + j] = new Pops.Marginal(Double.NaN, Double.NaN, Double.NaN, Double.NaN); continue; }
                double beta = cross / sumSquares, mse = 0;
                for (int i = 0; i < rows.length; i++) { double residual = subY[i][0] - beta * x[i][j]; mse += residual * residual; }
                mse /= rows.length; double se = Math.sqrt(mse / sumSquares), z = beta / se;
                result[first + j] = new Pops.Marginal(beta, se, ChiSquare.cumulative(z * z, 1, false, false), 1 - mse / variance);
            }
            first += width;
        }
        if (first != reader.columns) throw new IOException("Feature batches omit declared columns");
        return result;
    }

    private static void validate(Pops.Selection selection, int p) {
        if (selection.forwardSteps() != null) { if (selection.forwardSteps() > p) throw new IllegalArgumentException("Forward steps exceed features"); return; }
        if (selection.controls() != null && selection.controls().length != p) throw new IllegalArgumentException("Control feature mask dimension differs");
        int[] subset = selection.subset();
        if (subset != null) { boolean[] seen = new boolean[p]; for (int j : subset) { if (j < 0 || j >= p || seen[j]) throw new IllegalArgumentException("Invalid or duplicate feature subset ordinal"); seen[j] = true; } }
    }

    private static boolean[] select(Pops.Marginal[] marginals, Pops.Selection selection) {
        int p = marginals.length; int[] subset = selection.subset(); boolean[] controls = selection.controls(), selected = new boolean[p];
        if (subset == null) { subset = new int[p]; for (int j = 0; j < p; j++) subset[j] = j; }
        List<Integer> candidates = new ArrayList<>();
        for (int j : subset) {
            if (controls != null && controls[j]) selected[j] = true;
            else if (selection.pValueCutoff() == null || marginals[j].pValue() < selection.pValueCutoff()) candidates.add(j);
        }
        if (selection.maximumFeatures() != null) {
            double[] values = new double[candidates.size()]; for (int i = 0; i < values.length; i++) values[i] = marginals[candidates.get(i)].pValue();
            int[] order = PopsOrder.argsort(values);
            for (int i = 0; i < Math.min(order.length, selection.maximumFeatures()); i++) selected[candidates.get(order[i])] = true;
        } else for (int j : candidates) selected[j] = true;
        for (int j = 0; j < p; j++) selected[j] &= !Double.isNaN(marginals[j].pValue());
        return selected;
    }

    private static String fingerprint(Batch batch) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); ByteBuffer buffer = ByteBuffer.allocate(65536);
            int width = batch.values[0].length;
            for (double[] row : batch.values) {
                if (row == null || row.length != width) throw new IOException("Feature batch is ragged");
                for (double value : row) {
                    if (!Double.isFinite(value)) throw new IOException("Finite prepared feature values required; no imputation is performed");
                    if (buffer.remaining() < 8) { digest.update(buffer.array(), 0, buffer.position()); buffer.clear(); }
                    buffer.putLong(Double.doubleToRawLongBits(value));
                }
            }
            digest.update(buffer.array(), 0, buffer.position()); return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
