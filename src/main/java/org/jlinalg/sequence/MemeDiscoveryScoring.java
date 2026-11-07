/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Independent canonical-DNA mathematics for MEME's reported discovery sites and
 * MAST-style nonoverlap diagrams. This uses printed centibit scores, the 100-point
 * lattice and three-significant-digit background convention tested against
 * original MEME 4.9.1, 5.5.4 and 5.5.9. It is distinct from FIMO's 1000-point
 * lattice. No EM fitting, motif significance, report parsing or runtime admission
 * is performed. Original binary64 products and underflow are not repaired.
 */
public final class MemeDiscoveryScoring {
    private MemeDiscoveryScoring() { }
    /** Original per-column discovery score range. */
    public static final int RANGE = 100;

    /**
     * Explicit per-call bounds; callers must also bound complete families.
     * @param maximumWorkspaceBytes conservative primitive/model/selected-hit workspace estimate
     * @param maximumOperations counted score, convolution and overlap operations
     * @param maximumWidth complete motif width, at most 512
     * @param maximumMotifs complete ordered motif population
     * @param maximumSequenceLength maximum supplied sequence length
     * @param maximumSelectedHits maximum complete nonoverlap hit population
     */
    public record Limits(long maximumWorkspaceBytes, long maximumOperations, int maximumWidth,
                         int maximumMotifs, int maximumSequenceLength, int maximumSelectedHits) {
        /** Rejects unsupported capacities. */
        public Limits {
            if (maximumWorkspaceBytes < 1 || maximumWorkspaceBytes > 4_294_967_296L
                    || maximumOperations < 1 || maximumOperations > 10_000_000_000L
                    || maximumWidth < 1 || maximumWidth > 512 || maximumMotifs < 1 || maximumMotifs > 100_000
                    || maximumSequenceLength < 1 || maximumSequenceLength > 100_000_000
                    || maximumSelectedHits < 1 || maximumSelectedHits > 10_000_000)
                throw new IllegalArgumentException("Invalid explicit MEME discovery scoring capacities");
        }
        /** @return bounded per-call defaults; these are not OS heap/CPU quotas */
        public static Limits defaults() { return new Limits(134_217_728, 500_000_000, 512, 1000, 1_000_000, 100_000); }
    }

    /**
     * Both-strand scores use the better orientation and retain plus on ties.
     * The position P value is not doubled for searching both strands.
     * @param forward forward lattice score
     * @param reverse reverse lattice score, or -1 when not requested
     * @param selected selected lattice score
     * @param reverseSelected whether the reverse score is strictly larger
     * @param pValue inclusive position null tail at the selected score
     */
    public record Score(int forward, int reverse, int selected, boolean reverseSelected, double pValue) { }

    /**
     * One surviving diagram hit. Coordinates refer to the input orientation.
     * @param motifIndex zero-based index in the complete caller-supplied motif order
     * @param start zero-based inclusive input coordinate
     * @param width complete motif width
     * @param reverse strand selected by the score comparison
     * @param score selected integer lattice score
     * @param pValue uncorrected position P value
     */
    public record Hit(int motifIndex, int start, int width, boolean reverse, int score, double pValue) { }

    /**
     * Complete greedy diagram and original-convention sequence product statistic.
     * The sequence statistic uses every scorable motif, including motifs without
     * a surviving hit; it is not a product of displayed hit P values.
     * @param hits all surviving nonoverlapping hits in input-coordinate order
     * @param combinedPValue product-of-uniforms sequence statistic
     * @param operations complete counted score/overlap operations
     * @param productUnderflow whether positive sequence factors multiplied to zero
     */
    public record Tiling(List<Hit> hits, double combinedPValue, long operations, boolean productUnderflow) {
        /** Makes an immutable hit list. */
        public Tiling { hits = List.copyOf(hits); }
    }

    /** Immutable complete score model; accessors return defensive array copies. */
    public static final class Model {
        private final int[][] scaled;
        private final double[] tails, roundedBackground;
        private final double scale;
        private final int offset;
        private final long operations, workspace;
        private Model(int[][] scaled, double[] tails, double[] roundedBackground, double scale, int offset, long operations, long workspace) {
            this.scaled = scaled; this.tails = tails; this.roundedBackground = roundedBackground;
            this.scale = scale; this.offset = offset; this.operations = operations; this.workspace = workspace;
        }
        /** @return complete motif width */
        public int width() { return scaled.length; }
        /** @return original-convention scale rounded to fourteen significant digits */
        public double scale() { return scale; }
        /** @return minimum printed centibit score */
        public int offset() { return offset; }
        /** @return all scaled A/C/G/T matrix cells */
        public int[][] integerMatrix() { return Arrays.stream(scaled).map(int[]::clone).toArray(int[][]::new); }
        /** @return every inclusive null tail from zero through width*100 */
        public double[] tailProbabilities() { return tails.clone(); }
        /** @return individually rounded, unrenormalized original-convention background */
        public double[] roundedBackground() { return roundedBackground.clone(); }
        /** @return counted complete model-building operations */
        public long buildOperations() { return operations; }
        /**
         * Scores one complete uppercase canonical DNA word. Callers scoring a
         * population must enforce cumulative work limits separately.
         * @param word complete input-oriented word
         * @param bothStrands whether to compare the reverse complement
         * @param cancellation caller cancellation check
         * @return source-convention selected score and position tail
         */
        public Score score(String word, boolean bothStrands, Runnable cancellation) {
            Objects.requireNonNull(word); Objects.requireNonNull(cancellation); cancellation.run();
            if (word.length() != width()) throw new IllegalArgumentException("Require a complete MEME discovery word");
            Score value = at(word, 0, bothStrands, this, null); cancellation.run(); return value;
        }
    }

    /**
     * Builds all null tails from a complete printed centibit matrix. The supplied
     * background is resolved before entry; the source's three-digit rounding is
     * applied separately per base with no renormalization.
     * @param centibits complete A/C/G/T printed integer score rows
     * @param resolvedBackground explicit positive normalized A/C/G/T probabilities
     * @param limits explicit complete-model capacities
     * @param cancellation caller cancellation callback
     * @return immutable original-convention score model
     */
    public static Model build(int[][] centibits, double[] resolvedBackground, Limits limits, Runnable cancellation) {
        Objects.requireNonNull(centibits); Objects.requireNonNull(resolvedBackground); Objects.requireNonNull(limits);
        var work = new Work(limits, cancellation); work.check();
        int width = centibits.length;
        if (width < 1 || width > limits.maximumWidth() || resolvedBackground.length != 4)
            throw new IllegalArgumentException("Complete MEME discovery matrix/background dimensions exceed capacity");
        long workspace = 4096L + 256L * width + 24L * (width * RANGE + 1);
        work.workspace(workspace);
        double sum = 0; double[] background = resolvedBackground.clone();
        for (int b = 0; b < 4; b++) {
            double p = background[b];
            if (!Double.isFinite(p) || p < 1e-250 || p > 1)
                throw new IllegalArgumentException("Require supported positive finite resolved MEME background; no epsilon substitution");
            sum += p; background[b] = significant(p, 3);
        }
        if (Math.abs(sum - 1) > 1e-12) throw new IllegalArgumentException("Resolved MEME background is not normalized; no silent normalization");
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int[] row : centibits) {
            if (row == null || row.length != 4) throw new IllegalArgumentException("Require every complete A/C/G/T centibit row");
            for (int v : row) {
                work.add(1);
                if (v < -1_000_000 || v > 1_000_000) throw new IllegalArgumentException("Unsupported MEME printed centibit magnitude");
                low = Math.min(low, v); high = Math.max(high, v);
            }
        }
        if (low == high && low != 0)
            throw new IllegalArgumentException("Unsupported constant nonzero MEME score matrix; preserve it without repairing the source degeneracy");
        double scale = low == high ? 0 : significant(RANGE / (double) (high - low), 14);
        int[][] integers = new int[width][4];
        for (int i = 0; i < width; i++) for (int b = 0; b < 4; b++) {
            work.add(1); integers[i][b] = (int) Math.floor((centibits[i][b] - low) * scale + .5);
            if (integers[i][b] < 0 || integers[i][b] > RANGE) throw new ArithmeticException("MEME lattice cell outside original score range");
        }
        double[] distribution = new double[width * RANGE + 1]; distribution[0] = 1;
        for (int row = 0; row < width; row++) {
            double[] next = new double[distribution.length];
            for (int base = 0; base < 4; base++) {
                int shift = integers[row][base];
                for (int score = 0; score <= row * RANGE; score++) {
                    work.add(1);
                    if (distribution[score] != 0) next[score + shift] += distribution[score] * background[base];
                }
            }
            distribution = next;
        }
        for (int i = distribution.length - 2; i >= 0; i--) {
            work.add(1); distribution[i] = Math.min(1, distribution[i] + distribution[i + 1]);
        }
        for (double p : distribution) if (!Double.isFinite(p) || p < 0 || p > 1)
            throw new ArithmeticException("Invalid MEME discovery null-tail arithmetic");
        work.check();
        return new Model(integers, distribution, background, scale, low, work.operations, workspace);
    }

    /**
     * Reconstructs a complete ordered MEME diagram on canonical DNA. Candidates
     * require position P strictly below 1e-4. A new hit replaces overlapping hits
     * only when its P is strictly smaller than their product. Original motif and
     * coordinate ordering, plus-strand ties, the small-P sequence approximation,
     * and binary64 product underflow remain explicit source conventions.
     * @param sequence complete uppercase input-oriented canonical DNA sequence
     * @param models complete ordered model population
     * @param bothStrands compare both orientations at every position
     * @param limits explicit complete-sequence capacities
     * @param cancellation caller cancellation callback
     * @return every surviving hit and the original-convention sequence statistic
     */
    public static Tiling tile(String sequence, List<Model> models, boolean bothStrands, Limits limits, Runnable cancellation) {
        Objects.requireNonNull(sequence); Objects.requireNonNull(models); Objects.requireNonNull(limits);
        var work = new Work(limits, cancellation); work.check();
        if (sequence.length() > limits.maximumSequenceLength() || models.size() > limits.maximumMotifs())
            throw new IllegalArgumentException("Complete MEME diagram population exceeds capacity; no truncation");
        models = List.copyOf(models);
        long modelWorkspace = 4096;
        for (Model model : models) {
            Objects.requireNonNull(model);
            if (model.width() > limits.maximumWidth()) throw new IllegalArgumentException("MEME diagram motif width exceeds capacity");
            modelWorkspace = Math.addExact(modelWorkspace, model.workspace);
        }
        work.workspace(modelWorkspace);
        for (int i = 0; i < sequence.length(); i++) { work.add(1); base(sequence.charAt(i)); }
        var selected = new TreeMap<Integer, Hit>(); double product = 1; int scorable = 0, widest = 0; boolean underflow = false;
        for (int m = 0; m < models.size(); m++) {
            Model model = models.get(m); int last = sequence.length() - model.width();
            if (last < 0) continue;
            scorable++; widest = Math.max(widest, model.width()); double bestP = 1;
            for (int start = 0; start <= last; start++) {
                Score score = at(sequence, start, bothStrands, model, work); double p = score.pValue(); bestP = Math.min(bestP, p);
                if (p >= 1e-4) continue;
                var overlaps = new ArrayList<Integer>(); double overlapProduct = 1; boolean accept = true;
                for (var entry : selected.subMap(start, true, start + model.width(), false).entrySet()) {
                    work.add(1); overlaps.add(entry.getKey()); overlapProduct *= entry.getValue().pValue();
                    if (p >= overlapProduct) { accept = false; break; }
                }
                if (accept) for (var entry : selected.subMap(Math.max(0, start - widest + 1), true, start, false).entrySet()) {
                    // Only starts within the widest processed motif can overlap.
                    work.add(1);
                    if (entry.getKey() + entry.getValue().width() > start) overlaps.add(entry.getKey());
                }
                if (accept) {
                    overlaps.sort(Integer::compareTo);
                    for (int old : overlaps) if (old < start) {
                        work.add(1); overlapProduct *= selected.get(old).pValue();
                        if (p >= overlapProduct) { accept = false; break; }
                    }
                }
                if (!accept) continue;
                for (int old : overlaps) { work.add(1); selected.remove(old); }
                selected.put(start, new Hit(m, start, model.width(), score.reverseSelected(), score.selected(), p));
                if (selected.size() > limits.maximumSelectedHits()) throw new IllegalArgumentException("Complete MEME diagram hit population exceeds capacity");
                work.workspace(Math.addExact(modelWorkspace, 256L * selected.size()));
            }
            long trials = ((long) last + 1) * (bothStrands ? 2 : 1);
            double small = bestP * trials;
            double seqP = small <= .001 ? small : 1 - Math.pow(1 - bestP, trials);
            double next = product * seqP;
            underflow |= product > 0 && seqP > 0 && next == 0; product = next;
        }
        double combined;
        if (scorable == 0) combined = 1;
        else if (product == 0) combined = 0;
        else {
            double x = -Math.log(product), term = product; combined = product;
            for (int i = 1; i < scorable; i++) { work.add(1); term *= x / i; combined += term; }
        }
        if (!Double.isFinite(combined) || combined < 0 || combined > 1 + 1e-12)
            throw new ArithmeticException("Invalid original-convention MEME sequence product statistic");
        work.check(); return new Tiling(new ArrayList<>(selected.values()), combined, work.operations, underflow);
    }

    private static Score at(CharSequence sequence, int start, boolean both, Model model, Work work) {
        int forward = 0, reverse = both ? 0 : -1, width = model.width();
        for (int row = 0; row < width; row++) {
            if (work != null) work.add(both ? 2 : 1);
            forward += model.scaled[row][base(sequence.charAt(start + row))];
            if (both) reverse += model.scaled[row][3 - base(sequence.charAt(start + width - row - 1))];
        }
        int selected = Math.max(forward, reverse);
        return new Score(forward, reverse, selected, reverse > forward, model.tails[selected]);
    }
    static int base(char letter) {
        int result = switch (letter) { case 'A' -> 0; case 'C' -> 1; case 'G' -> 2; case 'T' -> 3; default -> -1; };
        if (result < 0) throw new IllegalArgumentException("Require uppercase canonical A/C/G/T; aliases and ambiguities need their own source audit");
        return result;
    }
    static double significant(double value, int digits) {
        if (value == 0) return 0;
        double factor = Math.pow(10, Math.ceil(digits - 1 - Math.log10(Math.abs(value))));
        return Math.copySign(Math.rint(Math.abs(value) * factor) / factor, value);
    }
    private static final class Work {
        final Limits limits; final Runnable cancellation; long operations;
        Work(Limits limits, Runnable cancellation) { this.limits = limits; this.cancellation = Objects.requireNonNull(cancellation); }
        void check() { cancellation.run(); }
        void add(long amount) {
            if (amount > limits.maximumOperations() - operations) throw new IllegalArgumentException("Complete MEME score/diagram operation capacity exceeded; no states truncated");
            long before = operations; operations += amount; if ((before >>> 10) != (operations >>> 10)) check();
        }
        void workspace(long amount) {
            if (amount > limits.maximumWorkspaceBytes()) throw new IllegalArgumentException("Complete MEME score/diagram workspace estimate exceeds capacity");
        }
    }
}
