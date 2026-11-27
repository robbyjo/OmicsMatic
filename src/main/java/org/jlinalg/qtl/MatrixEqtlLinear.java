/* Copyright (C) 2026 OmicsMatic contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Source behavior: Andrey A. Shabalin, MatrixEQTL 2.3 (LGPL-3),
 * R/Matrix_eQTL_engine.R, Matrix_eQTL_main/modelLINEAR.
 * See docs/matrixeqtl-native.md and LICENSES/MatrixEQTL-NOTICE.md. */
package org.jlinalg.qtl;

import java.util.*;
import jdistlib.T;
import jdistlib.accelerator.CpuComputeBackend;
import jdistlib.accelerator.MatrixTranspose;

/** Native FP64 MatrixEQTL 2.3 additive linear kernel. Rows are features, columns samples.
 * Input dosage orientation is retained. This is a scoped numerical implementation;
 * categorical/interaction models and author-specific postprocessing are separate. */
public final class MatrixEqtlLinear {
    private MatrixEqtlLinear() { }
    private static final double EPS = Math.ulp(1.0);
    public record Position(String chromosome, long start, long end) {
        public Position {
            require(chromosome != null && !chromosome.isBlank(), "Chromosome required");
            require(start > 0 && end > 0, "Positive, one-based genomic coordinates required");
        }
        long left() { return Math.min(start, end); }
        long right() { return Math.max(start, end); }
    }
    public record Options(double threshold, double cisThreshold, long cisDistance,
                          int blockSize, long maximumPairs, long maximumRetained) {
        public Options {
            require(Double.isFinite(threshold) && threshold >= 0 && threshold <= 1
                && Double.isFinite(cisThreshold) && cisThreshold >= 0 && cisThreshold <= 1,
                "Thresholds must be in [0,1]");
            require(threshold > 0 || cisThreshold > 0, "At least one threshold must be positive");
            require(threshold == 0 || cisThreshold == 0 || threshold <= cisThreshold,
                "Trans threshold must not exceed cis threshold");
            require(cisDistance >= 0 && blockSize > 0 && blockSize <= 4096
                && maximumPairs > 0 && maximumRetained > 0, "Invalid distance/block/capacity");
        }
    }
    /** Source beta/t/p/FDR plus a separately derived SE. NaN SE means unidentifiable
     * predictor/response. Infinite source t and the source normal-minimum p floor remain. */
    public record Association(int snp, int gene, double beta, double statistic,
                              double pvalue, double fdr, double derivedSe,
                              boolean zeroSnp, boolean zeroGene) { }
    public record Family(long tests, List<Association> associations,
                         double[] minimumBySnp, double[] minimumByGene) { }
    public record Result(int samples, int residualDf, Family cis, Family transOrAll,
                         long imputedSnps, long imputedGenes, long imputedCovariates,
                         int zeroSnps, int zeroGenes, long evaluatedPairs) { }
    @FunctionalInterface public interface Check { void verify(); }
    private record Prepared(double[][] rows, double[] norms, boolean[] zero, long missing) { }

    /** Caller owns input storage; this method never mutates it. The explicit check is
     * called throughout preparation and blocks for cancellation/deadline/integrity. */
    public static Result scan(double[][] snps, double[][] genes, double[][] covariates,
                              Position[] snpPositions, Position[] genePositions,
                              double[][] errorCovariance, Options options, Check check) {
        Objects.requireNonNull(options); Objects.requireNonNull(check);
        require(snps != null && snps.length > 0 && genes != null && genes.length > 0,
            "Nonempty SNP and gene populations required");
        require(snps[0] != null, "Missing SNP row"); int n = snps[0].length;
        require(covariates != null && n > covariates.length + 2,
            "Insufficient samples for intercept, covariates and tested SNP");
        validate(snps, n); validate(genes, n); validate(covariates, n);
        long pairs = Math.multiplyExact((long) snps.length, genes.length);
        require(pairs <= options.maximumPairs(), "Complete pair capacity exceeded; no truncated scan");
        if (options.cisThreshold() > 0) {
            positions(snpPositions, snps.length); positions(genePositions, genes.length);
            for (Position p : snpPositions) require(p.start() == p.end(), "SNP position must be a point");
        }
        check.verify();
        try (var cpu = new CpuComputeBackend()) {
            double[] correction = whitening(errorCovariance, n, cpu, check);
            double[][] cv = new double[covariates.length + 1][n]; Arrays.fill(cv[0], 1);
            long missingCv = 0;
            for (int i = 0; i < covariates.length; i++) {
                cv[i + 1] = covariates[i].clone(); missingCv += impute(cv[i + 1]); check.verify();
            }
            if (correction != null) for (int i = 0; i < cv.length; i++) cv[i] = multiply(cv[i], correction, n, check);
            double[][] q = orthogonalBasis(cv, n, check);
            Prepared g = prepare(genes, q, correction, n, check);
            Prepared s = prepare(snps, q, correction, n, check);
            int df = n - cv.length - 1;
            var cis = new Accumulator(snps.length, genes.length);
            var other = new Accumulator(snps.length, genes.length);
            double cisCut = correlationThreshold(options.cisThreshold(), df);
            double otherCut = correlationThreshold(options.threshold(), df);
            long retained = 0, evaluated = 0;
            for (int ss = 0; ss < snps.length; ss += options.blockSize()) {
                int ns = Math.min(options.blockSize(), snps.length - ss);
                double[] sb = block(s.rows(), ss, ns, n);
                for (int gg = 0; gg < genes.length; gg += options.blockSize()) {
                    check.verify(); int ng = Math.min(options.blockSize(), genes.length - gg);
                    double[] gb = block(g.rows(), gg, ng, n), correlations = new double[ns * ng];
                    cpu.dgemm(MatrixTranspose.NONE, MatrixTranspose.TRANSPOSE,
                        ns, ng, n, 1, sb, gb, 0, correlations);
                    for (int i = 0; i < ns; i++) {
                        check.verify();
                        for (int j = 0; j < ng; j++) {
                            int si = ss + i, gi = gg + j;
                            boolean isCis = options.cisThreshold() > 0
                                && cis(snpPositions[si], genePositions[gi], options.cisDistance());
                            if (!isCis && options.threshold() == 0) continue;
                            evaluated++; double r = correlations[i * ng + j];
                            require(Double.isFinite(r), "Nonfinite source correlation; no repaired statistic");
                            var family = isCis ? cis : other; family.tests++;
                            family.bestSnp[si] = Math.max(family.bestSnp[si], Math.abs(r));
                            family.bestGene[gi] = Math.max(family.bestGene[gi], Math.abs(r));
                            if (Math.abs(r) < (isCis ? cisCut : otherCut)) continue;
                            require(++retained <= options.maximumRetained(), "Retained association capacity exceeded; no partial batch");
                            double stat = statistic(r, df), p = probability(stat, df);
                            double beta = r * g.norms()[gi] / s.norms()[si];
                            double se = s.zero()[si] || g.zero()[gi] ? Double.NaN
                                : (g.norms()[gi] / s.norms()[si]) * Math.sqrt((1 - Math.min(r * r, 1)) / df);
                            require(Double.isFinite(beta), "Nonfinite source beta; no result admitted");
                            family.hits.add(new Association(si, gi, beta, stat, p, Double.NaN,
                                se, s.zero()[si], g.zero()[gi]));
                        }
                    }
                }
            }
            check.verify();
            return new Result(n, df, cis.finish(df, check), other.finish(df, check),
                s.missing(), g.missing(), missingCv, count(s.zero()), count(g.zero()), evaluated);
        }
    }

    /** Original inclusive interval semantics. Chromosome strings must match exactly. */
    public static boolean cis(Position snp, Position gene, long distance) {
        if (!snp.chromosome().equals(gene.chromosome())) return false;
        long p = snp.start(), left = gene.left(), right = gene.right();
        return p >= left ? p <= right || p - right <= distance : left - p <= distance;
    }
    private static final class Accumulator {
        long tests; final List<Association> hits = new ArrayList<>();
        final double[] bestSnp, bestGene;
        Accumulator(int s, int g) { bestSnp = new double[s]; bestGene = new double[g]; }
        Family finish(int df, Check check) {
            // Original saver orders descending |t| before reverse-cumulative BH.
            hits.sort(Comparator.comparingDouble((Association a) -> Math.abs(a.statistic())).reversed()
                .thenComparingInt(Association::snp).thenComparingInt(Association::gene));
            double running = 1;
            for (int i = hits.size() - 1; i >= 0; i--) {
                if ((i & 1023) == 0) check.verify(); var a = hits.get(i);
                running = Math.min(running, a.pvalue() * tests / (i + 1.0));
                hits.set(i, new Association(a.snp(), a.gene(), a.beta(), a.statistic(),
                    a.pvalue(), running, a.derivedSe(), a.zeroSnp(), a.zeroGene()));
            }
            return new Family(tests, List.copyOf(hits), minima(bestSnp, df), minima(bestGene, df));
        }
    }
    private static double[] minima(double[] correlations, int df) {
        double[] result = new double[correlations.length];
        for (int i = 0; i < result.length; i++) result[i] = probability(statistic(correlations[i], df), df);
        return result;
    }
    private static double statistic(double r, int df) { return r * Math.sqrt(df / (1 - Math.min(r * r, 1))); }
    private static double probability(double t, int df) {
        double p = T.cumulative(-Math.abs(t), df, true, false) * 2;
        require(!Double.isNaN(p), "Undefined source t probability");
        return Math.max(p, Double.MIN_NORMAL);
    }
    private static double correlationThreshold(double p, int df) {
        if (p <= 0) return 1; if (p >= 1) return 0;
        double t = T.quantile(p / 2, df, false, false), squared = t * t;
        require(Double.isFinite(squared), "Threshold exceeds this numerical profile; use a representable threshold");
        return Math.sqrt(squared / (df + squared));
    }
    private static Prepared prepare(double[][] input, double[][] q, double[] correction, int n, Check check) {
        double[][] out = new double[input.length][]; double[] norms = new double[input.length];
        boolean[] zero = new boolean[input.length]; long missing = 0;
        for (int i = 0; i < input.length; i++) {
            check.verify(); double[] row = input[i].clone(); missing += impute(row);
            if (correction != null) row = multiply(row, correction, n, check);
            double original = dot(row, row), squared;
            double[] projections = new double[q.length];
            for (int k = 0; k < q.length; k++) projections[k] = dot(row, q[k]);
            for (int j = 0; j < n; j++) {
                double fitted = 0; for (int k = 0; k < q.length; k++) fitted += projections[k] * q[k][j];
                row[j] -= fitted;
            }
            squared = dot(row, row);
            require(Double.isFinite(original) && Double.isFinite(squared), "Row norm overflow; no repaired/rescaled source data");
            zero[i] = squared <= original * EPS;
            if (zero[i]) { Arrays.fill(row, 0); norms[i] = 1; }
            else { norms[i] = Math.sqrt(squared); for (int j = 0; j < n; j++) row[j] /= norms[i]; }
            out[i] = row;
        }
        return new Prepared(out, norms, zero, missing);
    }
    private static long impute(double[] row) {
        int count = 0; double sum = 0;
        for (double x : row) if (!Double.isNaN(x)) { sum += x; count++; }
        double mean = count == 0 ? 0 : sum / count;
        require(Double.isFinite(mean), "Nonfinite source row mean; no repaired input");
        long missing = 0;
        for (int i = 0; i < row.length; i++) if (Double.isNaN(row[i])) { row[i] = mean; missing++; }
        return missing;
    }
    /** Householder Q: all covariate columns are retained. Source rejection uses
     * absolute R diagonal < eps*n, not a generic regression rank cutoff. */
    private static double[][] orthogonalBasis(double[][] rows, int n, Check check) {
        int p = rows.length; double[][] a = new double[p][]; double[] auxiliary = new double[p];
        for (int j = 0; j < p; j++) a[j] = rows[j].clone();
        for (int j = 0; j < p; j++) {
            check.verify(); double norm = 0;
            for (int i = j; i < n; i++) norm = Math.hypot(norm, a[j][i]);
            require(Double.isFinite(norm) && norm >= EPS * n, "Colinear or zero covariates detected");
            norm = Math.copySign(norm, a[j][j]); double inverse = 1 / norm;
            for (int i = j; i < n; i++) a[j][i] *= inverse;
            a[j][j] += 1; auxiliary[j] = a[j][j];
            for (int k = j + 1; k < p; k++) {
                double dot = 0; for (int i = j; i < n; i++) dot += a[j][i] * a[k][i];
                double t = -dot / auxiliary[j]; for (int i = j; i < n; i++) a[k][i] += t * a[j][i];
            }
        }
        double[][] q = new double[p][n];
        for (int k = 0; k < p; k++) {
            q[k][k] = 1;
            for (int j = p - 1; j >= 0; j--) {
                double dot = 0; for (int i = j; i < n; i++) dot += a[j][i] * q[k][i];
                double t = -dot / auxiliary[j]; for (int i = j; i < n; i++) q[k][i] += t * a[j][i];
            }
        }
        return q;
    }
    private static double[] whitening(double[][] covariance, int n, CpuComputeBackend cpu, Check check) {
        if (covariance == null || covariance.length == 0) return null;
        require(covariance.length == n, "Covariance dimensions differ from sample population"); validate(covariance, n);
        double[] flat = new double[Math.multiplyExact(n, n)];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            require(Double.isFinite(covariance[i][j]) && covariance[i][j] == covariance[j][i],
                "Covariance must be finite and exactly symmetric"); flat[i * n + j] = covariance[i][j];
        }
        check.verify(); var eigen = cpu.dsyev(flat, n); check.verify();
        double[] d = eigen.eigenvalues(), v = eigen.eigenvectors(), correction = new double[flat.length];
        for (int k = 0; k < n; k++) require(Double.isFinite(d[k]) && d[k] > 0, "Covariance is not positive definite");
        for (int i = 0; i < n; i++) {
            check.verify();
            for (int j = 0; j < n; j++) {
                double value = 0; for (int k = 0; k < n; k++) value += v[i * n + k] / Math.sqrt(d[k]) * v[j * n + k];
                require(Double.isFinite(value), "Nonfinite covariance inverse square root"); correction[i * n + j] = value;
            }
        }
        return correction;
    }
    private static double[] multiply(double[] row, double[] matrix, int n, Check check) {
        double[] out = new double[n];
        for (int i = 0; i < n; i++) { check.verify(); for (int j = 0; j < n; j++) out[j] += row[i] * matrix[i * n + j]; }
        return out;
    }
    private static double[] block(double[][] rows, int start, int count, int n) {
        double[] out = new double[Math.multiplyExact(count, n)];
        for (int i = 0; i < count; i++) System.arraycopy(rows[start + i], 0, out, i * n, n);
        return out;
    }
    private static void validate(double[][] matrix, int n) {
        for (double[] row : matrix) {
            require(row != null && row.length == n, "Ragged matrix/sample population mismatch");
            for (double x : row) require(!Double.isInfinite(x), "Infinite input is not a missing-value token");
        }
    }
    private static void positions(Position[] positions, int count) {
        require(positions != null && positions.length == count, "Complete ordered positions required");
        for (Position p : positions) Objects.requireNonNull(p, "Missing position");
    }
    private static double dot(double[] a, double[] b) { double r = 0; for (int i = 0; i < a.length; i++) r += a[i] * b[i]; return r; }
    private static int count(boolean[] values) { int n = 0; for (boolean v : values) if (v) n++; return n; }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
}
