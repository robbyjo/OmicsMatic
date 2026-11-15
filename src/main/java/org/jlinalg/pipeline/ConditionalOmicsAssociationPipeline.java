/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import org.jlinalg.association.AssociationEngineOptions;
import org.jlinalg.association.AssociationFailurePolicy;
import org.jlinalg.inference.AssociationStatistics;

/** Exact, complete-case per-feature association with mapped omics covariates. */
public final class ConditionalOmicsAssociationPipeline {
    private ConditionalOmicsAssociationPipeline() { }

    /** Fits phenotype ~ fixed + adjustments + feature, or (when
     * omicsResponse) feature ~ fixed + adjustments + phenotype. The last
     * coefficient is always the tested association. Shared fixed covariates
     * should already include any intercept, and must not include phenotype. */
    public static OmicsAssociationSummary scanTo(NumericMatrixSource source,
            List<String> sampleIds, double[] phenotype, double[][] fixed,
            MappedOmicsAdjustment adjustments, boolean omicsResponse,
            OmicsTransform transform, int blockSize,
            ConditionalAssociationFitter fitter, AssociationEngineOptions options,
            OmicsAssociationSink sink) throws IOException {
        if (source == null || phenotype == null || fixed == null
                || sampleIds == null || sampleIds.isEmpty() || adjustments == null
                || !sampleIds.equals(adjustments.sampleIds())
                || phenotype.length != sampleIds.size() || fixed.length != sampleIds.size()
                || fixed[0] == null || blockSize < 1 || transform == null
                || fitter == null || options == null || sink == null)
            throw new IllegalArgumentException("aligned conditional scan inputs are required");
        int columns = fixed[0].length;
        for (double[] row : fixed)
            if (row == null || row.length != columns)
                throw new IllegalArgumentException("fixed design must be rectangular");
        int[] order = SampleAlignment.requireOrder(source.metadata().sampleIds(), sampleIds);
        long total = 0, tested = 0, failed = 0;
        HashSet<String> seen = new HashSet<>();
        ForkJoinPool pool = options.parallelism() == 1 ? null
            : new ForkJoinPool(Math.min(options.parallelism(), blockSize));
        try (NumericBlockReader reader = source.open(order)) {
            for (NumericBlock block; (block = reader.read(blockSize)) != null;) {
                List<NumericRow> rows = block.rows();
                for (NumericRow row : rows)
                    if (!seen.add(row.id()))
                        throw new IllegalArgumentException("duplicate primary feature: " + row.id());
                Outcome[] outcomes = new Outcome[rows.size()];
                Runnable work = () -> IntStream.range(0, rows.size()).parallel().forEach(i -> {
                    try {
                        outcomes[i] = fit(rows.get(i), phenotype, fixed, adjustments,
                            omicsResponse, transform, fitter, options);
                    } catch (IOException error) { throw new UncheckedIOException(error); }
                });
                if (pool == null) {
                    for (int i = 0; i < rows.size(); i++)
                        outcomes[i] = fit(rows.get(i), phenotype, fixed, adjustments,
                            omicsResponse, transform, fitter, options);
                } else {
                    try { pool.submit(work).get(); }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IOException("conditional scan interrupted", error);
                    } catch (ExecutionException error) {
                        Throwable cause = error.getCause();
                        if (cause instanceof UncheckedIOException io) throw io.getCause();
                        if (cause instanceof RuntimeException runtime) throw runtime;
                        if (cause instanceof Error fatal) throw fatal;
                        throw new IOException("conditional scan failed", cause);
                    }
                }
                for (Outcome outcome : outcomes) {
                    total++;
                    if (outcome.failure() != null) { sink.acceptFailure(outcome.failure()); failed++; }
                    else { sink.acceptEstimate(outcome.estimate()); tested++; }
                }
            }
        } finally { if (pool != null) pool.shutdownNow(); }
        return new OmicsAssociationSummary(total, tested, failed);
    }

    private static Outcome fit(NumericRow row, double[] phenotype, double[][] fixed,
            MappedOmicsAdjustment adjustments, boolean omicsResponse,
            OmicsTransform transform, ConditionalAssociationFitter fitter,
            AssociationEngineOptions options) throws IOException {
        List<String> labels = adjustments.features(row.id()).stream()
            .map(MappedOmicsAdjustment.Feature::label).toList();
        int n = -1;
        try {
            if (row.valuesView().length != phenotype.length)
                throw new IllegalArgumentException("primary feature sample count does not match analysis cohort");
            List<double[]> changing = new ArrayList<>(adjustments.values(row.id()));
            double[] feature = transform.apply(row.values());
            if (feature == null || feature.length != phenotype.length)
                throw new IllegalArgumentException("transform must preserve sample count");
            changing.add(feature);
            int[] retained = new int[phenotype.length];
            n = 0;
            for (int i = 0; i < phenotype.length; i++) {
                boolean complete = Double.isFinite(phenotype[i]);
                for (double value : fixed[i]) complete &= Double.isFinite(value);
                for (double[] values : changing) complete &= Double.isFinite(values[i]);
                if (complete) retained[n++] = i;
            }
            int columns = fixed[0].length + changing.size();
            if (n <= columns)
                throw new IllegalArgumentException("insufficient complete observations: " + n
                    + " for " + columns + " coefficients");
            retained = Arrays.copyOf(retained, n);
            double[] response = new double[n], design = new double[Math.multiplyExact(n, columns)];
            for (int i = 0; i < n; i++) {
                int original = retained[i], start = i * columns;
                response[i] = omicsResponse ? feature[original] : phenotype[original];
                System.arraycopy(fixed[original], 0, design, start, fixed[0].length);
                for (int j = 0; j < changing.size() - 1; j++)
                    design[start + fixed[0].length + j] = changing.get(j)[original];
                design[start + columns - 1] = omicsResponse ? phenotype[original] : feature[original];
            }
            AssociationStatistics stats = fitter.fit(response, design, columns, retained,
                options.backendPolicy());
            int tested = columns - 1;
            double beta = stats.beta()[tested], se = stats.standardErrors()[tested], p = stats.pValues()[tested];
            if (!Double.isFinite(beta) || !Double.isFinite(se) || se <= 0 || !Double.isFinite(p))
                throw new IllegalArgumentException("association coefficient is not estimable after adjustment");
            double logP = stats.pValues(org.jlinalg.inference.PValueScale.LOG10)[tested];
            return new Outcome(new OmicsAssociationEstimate(row.id(), beta, se,
                stats.statistics()[tested], stats.degreesOfFreedom()[tested], p,
                logP, -logP, n, labels), null);
        } catch (RuntimeException error) {
            if (options.failurePolicy() == AssociationFailurePolicy.FAIL_FAST) throw error;
            return new Outcome(null, new AssociationPipelineFailure(row.id(),
                error.getClass().getSimpleName(), error.getMessage(), null, null,
                Map.of("n_samples", n < 0 ? "" : Integer.toString(n),
                    "adjustment_features", String.join(";", labels))));
        }
    }

    private record Outcome(OmicsAssociationEstimate estimate, AssociationPipelineFailure failure) { }
}
