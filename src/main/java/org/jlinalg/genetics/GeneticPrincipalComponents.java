/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import jdistlib.accelerator.ComputeBackend;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/**
 * Exact, bounded genetic PCA for explicitly oriented diploid dosages.
 *
 * <p>Rows are markers, columns are ordered samples, and NaN alone denotes missing
 * dosage. A supplied frequency refers to the marker's counted allele. Frequency
 * reference samples and fitted basis samples are independent choices. This class
 * performs no QC, LD pruning, allele harmonization, pedigree selection or ancestry
 * classification. Founder status alone does not establish unrelatedness.</p>
 *
 * <p>The standardized value is {@code (dosage-2*p)/sqrt(2*p*(1-p))}.
 * The relationship matrix divides its cross products either by all submitted
 * markers (mean imputation) or by jointly observed markers for each sample pair.
 * The latter matrix can be indefinite: its negative eigenvalues are retained,
 * and a symmetric eigensolver, rather than SVD, is used for both modes.</p>
 */
public final class GeneticPrincipalComponents {
    private GeneticPrincipalComponents() { }

    /** Relationship-matrix missingness; neither mode drops submitted markers. */
    public enum Missingness { MEAN_IMPUTE, PAIRWISE_COMPLETE }

    /** Exact, case-sensitive sample identity; components are not concatenated. */
    public record SampleId(String familyId, String individualId) {
        public SampleId { token(familyId, "Family ID"); token(individualId, "Individual ID"); }
    }

    /** Ordered biallelic marker identity and explicit dosage orientation. */
    public record Marker(String id, String countedAllele, String otherAllele) {
        public Marker {
            token(id, "Marker ID"); token(countedAllele, "Counted allele"); token(otherAllele, "Other allele");
            if (List.of("0", ".").contains(countedAllele) || List.of("0", ".").contains(otherAllele)
                    || countedAllele.equalsIgnoreCase(otherAllele))
                throw new IllegalArgumentException("Marker alleles must resolve to distinct codes: " + id);
        }
    }

    /**
     * Admission bounds checked before dense allocations. Estimated bytes cover
     * this API's arrays with a conservative dense-solver allowance; caller-owned
     * inputs and backend-specific native workspace are additional. Cubic eigen
     * work and marker-by-basis-pair work are bounded independently.
     */
    public record Limits(int maximumVariants, int maximumSamples, int maximumBasisSamples,
            long maximumInputCells, long maximumPairProducts, long maximumEigenWork,
            long maximumEstimatedBytes) {
        public Limits {
            if (maximumVariants < 1 || maximumSamples < 1 || maximumBasisSamples < 2
                    || maximumInputCells < 1 || maximumPairProducts < 1 || maximumEigenWork < 1
                    || maximumEstimatedBytes < 1)
                throw new IllegalArgumentException("Genetic PCA resource limits must be positive; basis limit must be at least two");
        }
        public static Limits defaults() {
            return new Limits(100_000, 100_000, 1_000, 25_000_000L,
                500_000_000L, 1_000_000_000L, 256L * 1_048_576);
        }
    }

    /** Relative rank and boundary-tie tolerances are explicit analysis settings. */
    public record Options(int components, Missingness missingness, double relativeRankTolerance,
            double relativeBoundaryTolerance, Limits limits) {
        public Options {
            Objects.requireNonNull(missingness, "PCA missingness"); Objects.requireNonNull(limits, "PCA limits");
            if (components < 1 || !Double.isFinite(relativeRankTolerance) || relativeRankTolerance <= 0
                    || relativeRankTolerance >= 1 || !Double.isFinite(relativeBoundaryTolerance)
                    || relativeBoundaryTolerance <= 0 || relativeBoundaryTolerance >= 1)
                throw new IllegalArgumentException("PCA needs positive components and finite relative tolerances in (0,1)");
        }
        public static Options defaults(int components, Missingness missingness) {
            return new Options(components, missingness, 1e-10, 1e-8, Limits.defaults());
        }
    }

    /** Independently estimated counted-allele frequencies, with call-count provenance. */
    public static final class Frequencies {
        private final double[] values;
        private final int[] observedSamples;
        private final int[] referenceSamples;
        private Frequencies(double[] values, int[] observed, int[] reference) {
            this.values = values; observedSamples = observed; referenceSamples = reference.clone();
        }
        public double[] values() { return values.clone(); }
        public int[] observedSamples() { return observedSamples.clone(); }
        public int[] referenceSamples() { return referenceSamples.clone(); }
    }

    /**
     * Estimates frequencies only in the explicitly selected reference columns.
     * Monomorphic estimates (0/1) are returned for inspection, but cannot be fitted.
     * An entirely missing reference marker is an error, rather than a guessed AF.
     */
    public static Frequencies estimateFrequencies(double[][] dosages, int[] referenceSamples, Limits limits) {
        int samples = validateDosages(dosages, limits);
        int[] reference = indices(referenceSamples, samples, 1, "Frequency reference");
        if (product(dosages.length, 32, "Frequency estimate bytes") + product(reference.length, 16, "Frequency reference bytes")
                > limits.maximumEstimatedBytes())
            throw new IllegalArgumentException("Frequency estimate storage limit exceeded");
        double[] frequencies = new double[dosages.length]; int[] observed = new int[dosages.length];
        for (int marker = 0; marker < dosages.length; marker++) {
            double sum = 0;
            for (int sample : reference) if (!Double.isNaN(dosages[marker][sample])) {
                sum += dosages[marker][sample]; observed[marker]++;
            }
            if (observed[marker] == 0)
                throw new IllegalArgumentException("No observed frequency-reference calls at marker ordinal " + (marker + 1));
            frequencies[marker] = sum / (2.0 * observed[marker]);
        }
        return new Frequencies(frequencies, observed, reference);
    }

    /** Fits using the portable Java CPU backend. Inputs are neither mutated nor retained. */
    public static Model fit(List<Marker> markers, List<SampleId> samples, double[][] dosages,
            int[] basisSamples, double[] frequencies, Options options) {
        Objects.requireNonNull(options, "PCA options");
        // Reject shape/work requests before selecting a backend or allocating its workspace.
        preflight(markers, samples, dosages, basisSamples, frequencies, options);
        try (BackendContext context = BackendContext.select(BackendPolicy.CPU)) {
            return fit(markers, samples, dosages, basisSamples, frequencies, options, context.backend());
        }
    }

    /** Fits with an explicitly selected backend; the caller retains backend ownership. */
    public static Model fit(List<Marker> markers, List<SampleId> samples, double[][] dosages,
            int[] basisSamples, double[] frequencies, Options options, ComputeBackend backend) {
        preflight(markers, samples, dosages, basisSamples, frequencies, options);
        Objects.requireNonNull(backend, "PCA backend");
        int m = markers.size(), n = basisSamples.length, k = options.components();
        double[][] z = new double[m][n]; int[] observed = new int[n];
        for (int marker = 0; marker < m; marker++) {
            double p = frequencies[marker], scale = Math.sqrt(2 * p * (1 - p)); int called = 0;
            for (int sample = 0; sample < n; sample++) {
                double dosage = dosages[marker][basisSamples[sample]];
                z[marker][sample] = Double.isNaN(dosage) ? Double.NaN : (dosage - 2 * p) / scale;
                if (!Double.isNaN(dosage)) { observed[sample]++; called++; }
                if (!Double.isNaN(dosage) && !Double.isFinite(z[marker][sample]))
                    throw new IllegalArgumentException("Nonfinite standardized dosage at marker " + markers.get(marker).id());
            }
            if (called == 0) throw new IllegalArgumentException("No fitted-basis calls at marker " + markers.get(marker).id());
        }
        for (int sample = 0; sample < n; sample++) if (observed[sample] == 0)
            throw new IllegalArgumentException("No observed fitted-basis genotypes for " + samples.get(basisSamples[sample]));
        double[] matrix = new double[Math.multiplyExact(n, n)];
        int[] joint = options.missingness() == Missingness.PAIRWISE_COMPLETE ? new int[matrix.length] : null;
        for (double[] row : z) for (int i = 0; i < n; i++) if (!Double.isNaN(row[i]))
            for (int j = 0; j <= i; j++) if (!Double.isNaN(row[j])) {
                matrix[i * n + j] += row[i] * row[j];
                if (joint != null) joint[i * n + j]++;
            }
        for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) {
            int denominator = joint == null ? m : joint[i * n + j];
            if (denominator == 0) throw new IllegalArgumentException("No jointly observed markers for basis sample ordinals " + (i + 1) + ", " + (j + 1));
            double value = matrix[i * n + j] / denominator;
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite genetic relationship matrix; inspect supplied frequencies");
            matrix[i * n + j] = matrix[j * n + i] = value;
        }
        var decomposition = backend.dsyev(matrix.clone(), n);
        double[] values = decomposition.eigenvalues(), vectors = decomposition.eigenvectors();
        if (values.length != n || vectors.length != matrix.length)
            throw new IllegalArgumentException("Unexpected symmetric eigensolver dimensions");
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(values[i])) throw new IllegalArgumentException("Nonfinite genetic PCA eigenvalue");
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[b], values[a]));
        double[] spectrum = new double[n]; for (int i = 0; i < n; i++) spectrum[i] = values[order[i]];
        double spectralScale = Arrays.stream(spectrum).map(Math::abs).max().orElseThrow();
        if (!(spectralScale > 0) || !(spectrum[k - 1] > options.relativeRankTolerance() * spectralScale))
            throw new IllegalArgumentException("Requested positive genetic PCA rank is unavailable; reduce components or review the panel");
        if (k < n && spectrum[k - 1] - spectrum[k] <= options.relativeBoundaryTolerance() * spectralScale)
            throw new IllegalArgumentException("Requested PCA boundary splits a numerically tied eigenspace; include the whole eigenspace or reduce components");
        double[][] fitted = new double[n][k], weights = new double[m][k];
        for (int component = 0; component < k; component++) {
            int column = order[component], pivot = 0;
            for (int i = 0; i < n; i++) {
                fitted[i][component] = vectors[i * n + column];
                if (!Double.isFinite(fitted[i][component])) throw new IllegalArgumentException("Nonfinite genetic PCA eigenvector");
                if (Math.abs(fitted[i][component]) > Math.abs(fitted[pivot][component])) pivot = i;
            }
            double sign = fitted[pivot][component] < 0 ? -1 : 1;
            for (int i = 0; i < n; i++) fitted[i][component] *= sign;
            // Native positive orientation: projection coefficient = Z*v/(M*lambda).
            // This is deliberately distinct from original PLINK2 allele-weight files.
            for (int marker = 0; marker < m; marker++) {
                double sum = 0;
                for (int i = 0; i < n; i++) if (!Double.isNaN(z[marker][i])) sum += z[marker][i] * fitted[i][component];
                weights[marker][component] = sum / m / spectrum[component];
                if (!Double.isFinite(weights[marker][component])) throw new IllegalArgumentException("Nonfinite genetic PCA projection weight");
            }
        }
        double maxResidual = 0, maxOrthogonalityError = 0, matrixInfinityNorm = 0;
        for (int i = 0; i < n; i++) {
            double rowNorm = 0; for (int j = 0; j < n; j++) rowNorm += Math.abs(matrix[i * n + j]);
            matrixInfinityNorm = Math.max(matrixInfinityNorm, rowNorm);
            for (int c = 0; c < k; c++) {
                double product = 0; for (int j = 0; j < n; j++) product += matrix[i * n + j] * fitted[j][c];
                maxResidual = Math.max(maxResidual, Math.abs(product - spectrum[c] * fitted[i][c]));
            }
        }
        for (int c = 0; c < k; c++) for (int d = 0; d <= c; d++) {
            double product = 0; for (int i = 0; i < n; i++) product += fitted[i][c] * fitted[i][d];
            maxOrthogonalityError = Math.max(maxOrthogonalityError, Math.abs(product - (c == d ? 1 : 0)));
        }
        double numericalTolerance = 8192 * Math.ulp(1.0) * n;
        if (!Double.isFinite(maxResidual) || !Double.isFinite(matrixInfinityNorm)
                || maxResidual > numericalTolerance * Math.max(spectralScale, matrixInfinityNorm)
                || !Double.isFinite(maxOrthogonalityError) || maxOrthogonalityError > numericalTolerance)
            throw new IllegalArgumentException("Genetic PCA eigensolver residual/orthogonality verification failed");
        List<SampleId> basis = Arrays.stream(basisSamples).mapToObj(samples::get).toList();
        return new Model(List.copyOf(markers), basis, frequencies.clone(), options,
            spectrum, fitted, weights, matrix, maxResidual, maxOrthogonalityError);
    }

    /** Projected data quality; an entirely missing target has unreportable coordinates. */
    public enum ProjectionStatus { COMPLETE, PARTIALLY_IMPUTED, NO_OBSERVED_DOSAGES }

    /** Immutable projection result, in the submitted target sample order. */
    public static final class Projection {
        private final List<SampleId> samples;
        private final double[][] coordinates;
        private final int[] observed;
        private final List<ProjectionStatus> statuses;
        private Projection(List<SampleId> samples, double[][] coordinates, int[] observed, List<ProjectionStatus> statuses) {
            this.samples = List.copyOf(samples); this.coordinates = coordinates; this.observed = observed; this.statuses = List.copyOf(statuses);
        }
        public List<SampleId> samples() { return samples; }
        public double[][] coordinates() { return copy(coordinates); }
        public int[] observedVariants() { return observed.clone(); }
        public List<ProjectionStatus> statuses() { return statuses; }
    }

    /** Immutable fitted transform; repeated eigenvalues within the retained space may rotate. */
    public static final class Model {
        private final List<Marker> markers;
        private final List<SampleId> basis;
        private final double[] frequencies, spectrum, relationship;
        private final double[][] fitted, weights;
        private final Options options;
        private final double residual, orthogonality;
        private Model(List<Marker> markers, List<SampleId> basis, double[] frequencies, Options options,
                double[] spectrum, double[][] fitted, double[][] weights, double[] relationship,
                double residual, double orthogonality) {
            this.markers = markers; this.basis = basis; this.frequencies = frequencies; this.options = options;
            this.spectrum = spectrum; this.fitted = fitted; this.weights = weights; this.relationship = relationship;
            this.residual = residual; this.orthogonality = orthogonality;
        }
        public List<Marker> markers() { return markers; }
        public List<SampleId> basisSamples() { return basis; }
        public Options options() { return options; }
        public double[] frequencies() { return frequencies.clone(); }
        /** Full descending spectrum, including meaningful negative pairwise eigenvalues. */
        public double[] spectrum() { return spectrum.clone(); }
        /** Unit-norm fitted eigenvectors: basis samples by retained components. */
        public double[][] fittedCoordinates() { return copy(fitted); }
        /** Row-major symmetric GRM, in fitted-basis order. */
        public double[] relationshipMatrix() { return relationship.clone(); }
        /** Native positive-orientation coefficients, Z*v/(M*lambda), by marker/component. */
        public double[][] projectionWeights() { return copy(weights); }
        public double maximumEigenResidual() { return residual; }
        public double maximumOrthogonalityError() { return orthogonality; }

        /**
         * Applies fixed frequencies and native weights; missing centered dosages
         * contribute zero. Markers must match exact identity, order and alleles.
         * This is a linear mean-imputed projection even for pairwise-fitted PCA;
         * in that case training projections need not equal fitted eigenvectors.
         * No marker omission, allele flipping or new-frequency estimation occurs.
         */
        public Projection project(List<Marker> targetMarkers, List<SampleId> targetSamples,
                double[][] dosages, Limits limits) {
            Objects.requireNonNull(targetMarkers, "Projection markers");
            if (!markers.equals(targetMarkers)) throw new IllegalArgumentException("Projection panel must match fitted marker identities, order and counted/other alleles exactly");
            int n = validateDosages(dosages, limits), m = markers.size(), k = options.components();
            identities(targetSamples, n, "Projection samples");
            if (dosages.length != m) throw new IllegalArgumentException("Projection marker dimension differs");
            long cells = product(n, k, "Projection coordinates");
            if (cells > Integer.MAX_VALUE || product(cells, 16, "Projection bytes") + 128L * n > limits.maximumEstimatedBytes()
                    || product(product(m, n, "Projection work"), k, "Projection work") > limits.maximumPairProducts())
                throw new IllegalArgumentException("Projection work/storage limit exceeded; increase explicit limits or use smaller batches");
            double[][] coordinates = new double[n][k]; int[] observed = new int[n];
            for (int marker = 0; marker < m; marker++) {
                double p = frequencies[marker], scale = Math.sqrt(2 * p * (1 - p));
                for (int sample = 0; sample < n; sample++) if (!Double.isNaN(dosages[marker][sample])) {
                    observed[sample]++; double z = (dosages[marker][sample] - 2 * p) / scale;
                    for (int c = 0; c < k; c++) coordinates[sample][c] += z * weights[marker][c];
                }
            }
            var statuses = new java.util.ArrayList<ProjectionStatus>(n);
            for (int sample = 0; sample < n; sample++) {
                if (observed[sample] == 0) Arrays.fill(coordinates[sample], Double.NaN);
                else for (double value : coordinates[sample]) if (!Double.isFinite(value))
                    throw new IllegalArgumentException("Nonfinite PCA projection for " + targetSamples.get(sample));
                statuses.add(observed[sample] == 0 ? ProjectionStatus.NO_OBSERVED_DOSAGES
                    : observed[sample] == m ? ProjectionStatus.COMPLETE : ProjectionStatus.PARTIALLY_IMPUTED);
            }
            return new Projection(targetSamples, coordinates, observed, statuses);
        }
    }

    private static void preflight(List<Marker> markers, List<SampleId> samples, double[][] dosages,
            int[] basisSamples, double[] frequencies, Options options) {
        Objects.requireNonNull(options, "PCA options"); int sampleCount = validateDosages(dosages, options.limits());
        identities(markers, dosages.length, "Markers"); identities(samples, sampleCount, "Samples");
        // Marker ID uniqueness also excludes conflicting allele records for the same ID.
        if (markers.stream().map(Marker::id).distinct().count() != markers.size())
            throw new IllegalArgumentException("Marker IDs must be unique");
        int n = indices(basisSamples, sampleCount, 2, "Fitted basis").length, m = dosages.length, k = options.components();
        if (n > options.limits().maximumBasisSamples() || k > n)
            throw new IllegalArgumentException("PCA basis/component dimensions exceed declared bounds");
        Objects.requireNonNull(frequencies, "Counted-allele frequencies");
        if (frequencies.length != m) throw new IllegalArgumentException("Frequency and marker dimensions differ");
        for (int i = 0; i < m; i++) if (!Double.isFinite(frequencies[i]) || frequencies[i] <= 0 || frequencies[i] >= 1)
            throw new IllegalArgumentException("PCA requires a finite counted-allele frequency in (0,1) for marker " + markers.get(i).id());
        long square = product(n, n, "PCA matrix"), zCells = product(m, n, "PCA standardized data");
        if (square > Integer.MAX_VALUE || zCells > Integer.MAX_VALUE)
            throw new IllegalArgumentException("PCA dense arrays exceed Java array indexing limits");
        long retainedCells = zCells + 6 * square + 2 * product(m, k, "PCA weights") + 3 * product(n, k, "PCA coordinates");
        long bytes = product(retainedCells, 8, "PCA estimated bytes") + 128L * (m + (long) n);
        if (product(m, square, "PCA pair work") > options.limits().maximumPairProducts()
                || product(n, square, "PCA eigen work") > options.limits().maximumEigenWork()
                || bytes > options.limits().maximumEstimatedBytes())
            throw new IllegalArgumentException("Exact genetic PCA work/storage limit exceeded; reduce the declared panel/basis or increase explicit limits");
    }
    private static int validateDosages(double[][] dosages, Limits limits) {
        Objects.requireNonNull(dosages, "Marker-by-sample dosages"); Objects.requireNonNull(limits, "PCA limits");
        if (dosages.length < 1 || dosages.length > limits.maximumVariants()) throw new IllegalArgumentException("PCA marker count exceeds positive limits");
        Objects.requireNonNull(dosages[0], "First marker dosages"); int n = dosages[0].length;
        if (n < 1 || n > limits.maximumSamples() || product(dosages.length, n, "Input cells") > limits.maximumInputCells())
            throw new IllegalArgumentException("PCA sample/input-cell limit exceeded");
        for (double[] row : dosages) {
            Objects.requireNonNull(row, "Marker dosages");
            if (row.length != n) throw new IllegalArgumentException("Ragged marker-by-sample dosages");
            for (double value : row) if (!Double.isNaN(value) && (!Double.isFinite(value) || value < 0 || value > 2))
                throw new IllegalArgumentException("Diploid dosage must be NaN or finite in [0,2]");
        }
        return n;
    }
    private static int[] indices(int[] indices, int samples, int minimum, String label) {
        Objects.requireNonNull(indices, label + " indices");
        if (indices.length < minimum) throw new IllegalArgumentException(label + " needs at least " + minimum + " explicitly selected samples");
        var unique = new HashSet<Integer>();
        for (int index : indices) if (index < 0 || index >= samples || !unique.add(index))
            throw new IllegalArgumentException(label + " sample indices must be unique and in range");
        return indices.clone();
    }
    private static void identities(List<?> values, int count, String label) {
        Objects.requireNonNull(values, label);
        if (values.size() != count || values.stream().anyMatch(Objects::isNull) || new HashSet<>(values).size() != count)
            throw new IllegalArgumentException(label + " must have exactly one unique nonnull identity per column/row");
    }
    private static long product(long a, long b, String label) {
        try { return Math.multiplyExact(a, b); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException(label + " overflows integer work/storage counters", overflow); }
    }
    private static double[][] copy(double[][] input) { return Arrays.stream(input).map(double[]::clone).toArray(double[][]::new); }
    private static void token(String value, String label) {
        if (value == null || value.isEmpty() || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
            throw new IllegalArgumentException(label + " must be a nonempty token without whitespace/control characters");
    }
}
