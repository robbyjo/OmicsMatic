/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * MR-MEGA ancestry-marker selection and classical MDS on prepared frequencies.
 * Inputs must already be merged by variant and harmonized to the same alleles
 * and cohort order. This component does not infer that preparation from files.
 */
public final class MrMegaAncestry {
    private MrMegaAncestry() { }

    /** Source-ordered variant; frequency -1 explicitly denotes an absent cohort. */
    public record Variant(String id, int chromosome, int position, double[] frequencies) {
        public Variant {
            require(id != null && !id.isBlank() && frequencies != null, "Explicit variant identifier and frequency population required");
            require(chromosome >= 0 && chromosome <= 25 && position >= 0, "Require source chromosome codes0..25 and nonnegative int32 positions");
            frequencies = frequencies.clone();
        }
        @Override public double[] frequencies() { return frequencies.clone(); }
    }

    /** Every supplied variant retains an explicit ancestry-selection disposition. */
    public record Disposition(int sourceOrdinal, String id, String reason, int sourceMatrixSlot) { }

    /** Complete selected population and source float32 frequency values. */
    public record Selection(int cohorts, int eligibleVariants, int[] sourceOrdinals,
            List<String> ids, double[][] storedFrequencies, double[][] squaredDistances,
            List<Disposition> dispositions, List<String> warnings) {
        public Selection {
            sourceOrdinals = sourceOrdinals.clone(); ids = List.copyOf(ids);
            storedFrequencies = copy(storedFrequencies); squaredDistances = copy(squaredDistances);
            dispositions = List.copyOf(dispositions); warnings = List.copyOf(warnings);
        }
        @Override public int[] sourceOrdinals() { return sourceOrdinals.clone(); }
        @Override public double[][] storedFrequencies() { return copy(storedFrequencies); }
        @Override public double[][] squaredDistances() { return copy(squaredDistances); }
    }

    /** Signed Gram eigenvalues are retained, including unselected rounding noise. */
    public record Axes(double[][] coordinates, double[][] centeredGram,
            double[] gramEigenvalues, int jacobiSweeps, double largestRemainingOffDiagonal,
            double numericalResolution, List<String> warnings) {
        public Axes {
            coordinates = copy(coordinates); centeredGram = copy(centeredGram);
            gramEigenvalues = gramEigenvalues.clone(); warnings = List.copyOf(warnings);
        }
        @Override public double[][] coordinates() { return copy(coordinates); }
        @Override public double[][] centeredGram() { return copy(centeredGram); }
        @Override public double[] gramEigenvalues() { return gramEigenvalues.clone(); }
    }

    /**
     * Reproduces the source-ordered bin selector, including its zero-index
     * sentinel, last-write rule, float32 storage and column300 slot alias.
     * Original variant chromosome/position fields are never rewritten.
     */
    public static Selection select(List<Variant> variants, int cohorts) {
        require(variants != null && !variants.isEmpty() && variants.size() <= 10000000,
            "Require a complete nonempty source-ordered variant population within capacity");
        require(cohorts >= 2 && cohorts <= 512, "Explicit ancestry cohort capacity is2..512");
        int[] slots = new int[30 * 300]; boolean[] written = new boolean[slots.length];
        String[] reasons = new String[variants.size()]; int[] locations = new int[variants.size()];
        Arrays.fill(locations, -1); int eligible = 0; var seen = new HashSet<String>(); var warnings = new ArrayList<String>();
        for (int ordinal = 0; ordinal < variants.size(); ordinal++) {
            Variant variant = variants.get(ordinal); require(variant != null && seen.add(variant.id), "Prepared variant IDs must be unique; do not silently merge or repair them");
            require(variant.frequencies.length == cohorts, "All prepared frequency rows must match the exact cohort population");
            boolean common = true, maf = true;
            for (double value : variant.frequencies) {
                require(Double.isFinite(value) && (value == -1 || value >= 0 && value <= 1), "Prepared frequencies must be finite0..1 or explicit missing -1");
                double stored = (float)value;
                if (stored == -1) common = false;
                if (stored < .01 || stored > .99) maf = false;
            }
            if (!common) { reasons[ordinal] = "NOT_PRESENT_IN_ALL_COHORTS"; continue; }
            if (!maf) { reasons[ordinal] = "OUTSIDE_SOURCE_FLOAT32_MAF_BOUNDS"; continue; }
            if (variant.chromosome == 0 || variant.position == 0) { reasons[ordinal] = "MISSING_SOURCE_CHROMOSOME_OR_POSITION"; continue; }
            eligible++;
            int bin = variant.position / 1000000;
            if (bin > 300) { reasons[ordinal] = "OUTSIDE_SOURCE_MATRIX_COLUMNS"; continue; }
            int location = variant.chromosome * 300 + bin; locations[ordinal] = location;
            if (written[location]) reasons[slots[location]] = "OVERWRITTEN_BY_LATER_SOURCE_ORDERED_VARIANT";
            slots[location] = ordinal; written[location] = true;
            reasons[ordinal] = ordinal == 0 ? "SOURCE_ZERO_INDEX_SENTINEL" : "OUTSIDE_SOURCE_SELECTED_CHROMOSOMES_OR_BINS";
            if (bin == 300) warnings.add(variant.id + ": source matrix column300 aliases the next chromosome's slot0; original variant coordinates remain unchanged.");
        }
        var indices = new ArrayList<Integer>(); var ids = new ArrayList<String>();
        for (int chromosome = 1; chromosome <= 23; chromosome++) for (int bin = 0; bin < 299; bin++) {
            int ordinal = slots[chromosome * 300 + bin];
            if (ordinal > 0) { indices.add(ordinal); ids.add(variants.get(ordinal).id); reasons[ordinal] = "SELECTED"; }
        }
        require(!indices.isEmpty(), "No source-selected ancestry markers; retain dispositions and supply valid complete frequencies, not invented ancestry axes");
        double[][] frequencies = new double[indices.size()][cohorts];
        for (int row = 0; row < indices.size(); row++) for (int col = 0; col < cohorts; col++)
            frequencies[row][col] = (float)variants.get(indices.get(row)).frequencies[col];
        double[][] distances = new double[cohorts][cohorts];
        for (int i = 0; i < cohorts - 1; i++) for (int j = i + 1; j < cohorts; j++) {
            double sum = 0; for (double[] row : frequencies) sum += Math.pow(row[i] - row[j], 2);
            distances[i][j] = distances[j][i] = sum / frequencies.length;
        }
        var disposition = new ArrayList<Disposition>();
        for (int i = 0; i < variants.size(); i++) disposition.add(new Disposition(i, variants.get(i).id, reasons[i], locations[i]));
        warnings.add("Source ancestry selection omits an unoverwritten ordinal0, keeps the last eligible variant per slot, and reads only chromosomes1..23/bins0..298. Source float32 .01/.99 boundary behavior is retained.");
        return new Selection(cohorts, eligible, indices.stream().mapToInt(Integer::intValue).toArray(), ids, frequencies, distances, disposition, warnings);
    }

    /** Infers the explicitly requested count; source-default disagreements are not guessed. */
    public static Axes infer(Selection selection, int count) {
        require(selection != null, "A complete prepared ancestry selection is required");
        return inferFromSquaredDistances(selection.squaredDistances, count);
    }

    /**
     * Classical MDS with deterministic column signs. The Gram matrix is PSD for
     * source frequency distances. Coordinate signs/bases are not identified;
     * compare geometry/model statistics or supply original axes for exact signs.
     */
    public static Axes inferFromSquaredDistances(double[][] distances, int count) {
        require(distances != null && distances.length >= 2 && distances.length <= 512, "Squared-distance cohort capacity is2..512");
        int n = distances.length; require(count > 0 && count < n, "Explicit axis count must be positive and less than cohort count");
        double[] means = new double[n]; double grand = 0;
        for (int i = 0; i < n; i++) {
            require(distances[i] != null && distances[i].length == n, "Squared-distance matrix must be square");
            for (int j = 0; j < n; j++) {
                require(Double.isFinite(distances[i][j]) && distances[i][j] >= 0, "Squared distances must be finite and nonnegative");
                means[i] += distances[i][j];
            }
            require(distances[i][i] == 0, "Squared-distance diagonal must be zero; it is not repaired");
            means[i] /= n; grand += means[i];
        }
        grand /= n;
        double[][] gram = new double[n][n]; double magnitude = 0;
        for (int i = 0; i < n; i++) for (int j = i; j < n; j++) {
            require(distances[i][j] == distances[j][i], "Squared distances must be symmetric; they are not averaged");
            gram[i][j] = gram[j][i] = -.5 * (distances[i][j] - means[i] - means[j] + grand);
            magnitude = Math.max(magnitude, Math.abs(gram[i][j]));
        }
        double resolution = 64.0 * n * Math.ulp(magnitude);
        double[][] a = copy(gram), vectors = new double[n][n]; for (int i = 0; i < n; i++) vectors[i][i] = 1;
        int sweeps = 0; double off = offDiagonal(a);
        while (off > resolution && sweeps < 128) {
            for (int p = 0; p < n - 1; p++) for (int q = p + 1; q < n; q++) {
                double value = a[p][q]; if (Math.abs(value) <= resolution) continue;
                double tau = (a[q][q] - a[p][p]) / (2 * value);
                double t = Math.copySign(1, tau) / (Math.abs(tau) + Math.hypot(1, tau));
                double c = 1 / Math.hypot(1, t), s = t * c;
                a[p][p] -= t * value; a[q][q] += t * value; a[p][q] = a[q][p] = 0;
                for (int i = 0; i < n; i++) {
                    if (i != p && i != q) {
                        double ip = a[i][p], iq = a[i][q];
                        a[i][p] = a[p][i] = c * ip - s * iq; a[i][q] = a[q][i] = s * ip + c * iq;
                    }
                    double ip = vectors[i][p], iq = vectors[i][q];
                    vectors[i][p] = c * ip - s * iq; vectors[i][q] = s * ip + c * iq;
                }
            }
            sweeps++; off = offDiagonal(a);
        }
        require(off <= resolution, "Native symmetric MDS eigensolver did not converge; no coordinate result admitted");
        Integer[] order = new Integer[n]; for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.<Integer>comparingDouble(i -> a[i][i]).reversed());
        double[] eigenvalues = new double[n]; for (int i = 0; i < n; i++) eigenvalues[i] = a[order[i]][order[i]];
        require(eigenvalues[n - 1] >= -resolution * n, "Squared distances do not yield a positive-semidefinite source Gram matrix");
        require(eigenvalues[count - 1] > resolution * n, "Requested ancestry axes exceed resolved positive rank; no numerical noise axes invented");
        require(Math.abs(eigenvalues[count - 1] - eigenvalues[count]) > resolution * n,
            "Axis cutoff splits a numerically tied eigenspace; supply explicit ancestry coordinates or select the complete eigenspace");
        double[][] coordinates = new double[n][count]; var warnings = new ArrayList<String>();
        for (int axis = 0; axis < count; axis++) {
            int column = order[axis], pivot = 0;
            for (int i = 1; i < n; i++) if (Math.abs(vectors[i][column]) > Math.abs(vectors[pivot][column])) pivot = i;
            double sign = vectors[pivot][column] < 0 ? -1 : 1, scale = Math.sqrt(eigenvalues[axis]);
            for (int i = 0; i < n; i++) coordinates[i][axis] = sign * vectors[i][column] * scale;
            if (axis > 0 && Math.abs(eigenvalues[axis - 1] - eigenvalues[axis]) <= resolution * n)
                warnings.add("Included eigenvalues are tied: the complete included eigenspace is defined, but individual coordinate directions are not unique.");
        }
        warnings.add("Native coordinate signs use the largest-magnitude loading as positive. Original SVD signs and any tied-eigenspace bases remain separate; no original values are relabelled.");
        return new Axes(coordinates, gram, eigenvalues, sweeps, off, resolution, warnings);
    }

    private static double offDiagonal(double[][] values) {
        double maximum = 0; for (int i = 0; i < values.length; i++) for (int j = i + 1; j < values.length; j++) maximum = Math.max(maximum, Math.abs(values[i][j])); return maximum;
    }
    private static double[][] copy(double[][] values) {
        double[][] result = new double[values.length][]; for (int i = 0; i < values.length; i++) result[i] = values[i].clone(); return result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
