/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Independent original HOMER 5.1 exon-part aggregation and raw/RPKM arithmetic.
 * Complete, independently audited part counts are required. Default copy-family
 * condensation sums counts but averages source copy lengths. It does not form a
 * genomic interval union or select the highest-expressed transcript. All source
 * coordinate contributors remain explicit: the original representative fields
 * can depend on Perl hash aggregation order and remain diagnostic metadata.
 */
public final class HomerRnaExpression {
    private HomerRnaExpression() { }

    /** Explicit source part/copy condensation choice. */
    public enum Mode { TRANSCRIPT_COPY_FAMILIES, DISTINCT_TRANSCRIPTS, EXON_PARTS }
    /** Raw source counts or valid-length original -rpkm arithmetic. */
    public enum Normalization { RAW, RPKM }

    /** Complete source coordinate contributor, without an inferred canonical owner. */
    public record Contributor(String executionId, String rawId, String contig,
                              int start1, int end1, char strand,
                              long effectiveLength, List<Long> halfCounts) {
        public Contributor { halfCounts = List.copyOf(halfCounts); }
    }

    /** One complete source output family, including immutable unrounded and printed values. */
    public record Row(String id, int copies, long summedSourceLength,
                      double effectiveLength, List<Long> halfCounts,
                      List<Double> values, List<String> printedValues,
                      List<Contributor> contributors) {
        public Row {
            halfCounts = List.copyOf(halfCounts);
            values = List.copyOf(values);
            printedValues = List.copyOf(printedValues);
            contributors = List.copyOf(contributors);
        }
    }

    /** Complete final population; rows preserve input-family encounter order only. */
    public record Table(Mode mode, Normalization normalization, int samples,
                        long inputTranscripts, long inputParts, List<Row> rows) {
        public Table { rows = List.copyOf(rows); }
    }

    /** Caller deadline, cancellation or immutable-input check. */
    @FunctionalInterface public interface Check { void run() throws IOException; }

    private static final class Group {
        private final String id;
        private final long[] counts;
        private final ArrayList<Contributor> contributors = new ArrayList<>();
        private long length;
        Group(String id, int samples) { this.id = id; counts = new long[samples]; }
    }

    /**
     * Bounded complete-population accumulator. A failed add/finalization latches
     * the instance so a previously accepted prefix cannot later become a result.
     * Limits bound records, not the JVM's total heap or process RSS.
     */
    public static final class Builder {
        private final int samples, maximumTranscripts, maximumRows, maximumContributors;
        private final long maximumParts;
        private final Mode mode;
        private final Check check;
        private final LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
        private final HashSet<String> executionIds = new HashSet<>();
        private long transcripts, parts;
        private boolean failed, finished;

        /**
         * @param samples exact sample count, from one to 32
         * @param mode explicit source condensation mode
         * @param maximumTranscripts complete reference transcript bound
         * @param maximumParts complete exon-part bound
         * @param maximumRows complete output-family bound
         * @param maximumContributors maximum source copies in one output family
         * @param check caller deadline/cancellation check
         */
        public Builder(int samples, Mode mode, int maximumTranscripts, long maximumParts,
                       int maximumRows, int maximumContributors, Check check) {
            if (samples < 1 || samples > 32 || mode == null || check == null
                    || maximumTranscripts < 1 || maximumTranscripts > 10_000_000
                    || maximumParts < 1 || maximumParts > 100_000_000
                    || maximumRows < 1 || maximumRows > 10_000_000
                    || maximumContributors < 1 || maximumContributors > 1_000_000) {
                throw new IllegalArgumentException("Invalid complete RNA expression population limits");
            }
            this.samples = samples;
            this.mode = mode;
            this.maximumTranscripts = maximumTranscripts;
            this.maximumParts = maximumParts;
            this.maximumRows = maximumRows;
            this.maximumContributors = maximumContributors;
            this.check = check;
        }

        /**
         * Add every source part of one transcript. Part arrays follow its exact
         * parser order and each contains all samples' exact half-unit counts.
         * @param transcript independently parsed complete source transcript
         * @param partHalfCounts one complete sample vector per exon part
         * @throws IOException if a capacity, caller or exact-arithmetic check fails
         */
        public void add(HomerRnaTranscripts.Transcript transcript, List<long[]> partHalfCounts) throws IOException {
            requireOpen();
            try {
                check.run();
                if (transcript == null || partHalfCounts == null
                        || partHalfCounts.size() != transcript.exons().size()) {
                    throw new IllegalArgumentException("Require every part count of the complete transcript");
                }
                if (++transcripts > maximumTranscripts
                        || (parts = Math.addExact(parts, partHalfCounts.size())) > maximumParts) {
                    throw new IOException("Complete RNA transcript/part aggregation capacity exceeded");
                }
                if (!executionIds.add(transcript.executionId())) {
                    throw new IllegalArgumentException("Repeated transcript execution identity in complete RNA counts");
                }
                long[] total = new long[samples];
                long sourceLength = 0;
                for (int p = 0; p < partHalfCounts.size(); p++) {
                    if ((p & 1023) == 0) check.run();
                    long[] vector = partHalfCounts.get(p);
                    sourceLength = Math.addExact(sourceLength, transcript.exons().get(p).effectiveLength());
                    if (vector == null || vector.length != samples) {
                        throw new IllegalArgumentException("Part count vector does not match the complete sample population");
                    }
                    for (int s = 0; s < samples; s++) {
                        if (vector[s] < 0 || vector[s] > (1L << 52)) {
                            throw new IllegalArgumentException("Unsupported negative or inexact RNA half-unit count");
                        }
                        total[s] = exactAdd(total[s], vector[s]);
                    }
                    if (mode == Mode.EXON_PARTS) {
                        var part = transcript.exons().get(p);
                        addMember(part.id(), new Contributor(part.id(), transcript.rawId(), transcript.contig(),
                                part.feature().start1(), part.feature().end1(), transcript.strand(),
                                part.effectiveLength(), boxed(vector)));
                    }
                }
                if (sourceLength != transcript.sourceEffectiveLength()) {
                    throw new IllegalArgumentException("Transcript source length differs from its complete exon population");
                }
                if (mode != Mode.EXON_PARTS) {
                    String id = mode == Mode.TRANSCRIPT_COPY_FAMILIES ? transcript.condensedId() : transcript.executionId();
                    addMember(id, new Contributor(transcript.executionId(), transcript.rawId(), transcript.contig(),
                            transcript.start1(), transcript.end1(), transcript.strand(),
                            transcript.sourceEffectiveLength(), boxed(total)));
                }
            } catch (IOException | RuntimeException ex) {
                failed = true;
                throw ex;
            }
        }

        private void addMember(String id, Contributor member) throws IOException {
            Group group = groups.get(id);
            if (group == null) {
                if (groups.size() >= maximumRows) throw new IOException("Complete RNA output-family capacity exceeded");
                group = new Group(id, samples);
                groups.put(id, group);
            }
            if (group.contributors.size() >= maximumContributors) {
                throw new IOException("RNA copy-family contributor capacity exceeded");
            }
            group.length = Math.addExact(group.length, member.effectiveLength());
            if (group.length < -(1L << 52) || group.length > (1L << 52)) throw new IOException("RNA source length exceeds exact double audit capacity");
            for (int s = 0; s < samples; s++) group.counts[s] = exactAdd(group.counts[s], member.halfCounts().get(s));
            group.contributors.add(member);
        }

        /**
         * Finalize all rows. RPKM refuses original nonpositive/short-length
         * fallbacks; no invalid source value is silently labeled valid RPKM.
         * @param wholeDirectoryMass complete original tagInfo mass per sample
         * @param normalization explicit raw or RPKM choice
         * @return complete immutable output population
         * @throws IOException if caller, capacity or valid-length checks fail
         */
        public Table finish(double[] wholeDirectoryMass, Normalization normalization) throws IOException {
            requireOpen();
            try {
                if (wholeDirectoryMass == null || wholeDirectoryMass.length != samples || normalization == null) {
                    throw new IllegalArgumentException("Require original whole-directory mass for every sample");
                }
                for (double mass : wholeDirectoryMass) {
                    if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("Invalid original RNA directory mass");
                }
                var result = new ArrayList<Row>(groups.size());
                for (var group : groups.values()) {
                    check.run();
                    int copies = group.contributors.size();
                    double length = (double)group.length / copies;
                    if (normalization == Normalization.RPKM && length < 1) {
                        throw new IOException("Original effective RNA length below one for " + group.id
                                + "; source fallback is not valid RPKM");
                    }
                    var values = new ArrayList<Double>(samples);
                    var printed = new ArrayList<String>(samples);
                    for (int s = 0; s < samples; s++) {
                        double raw = group.counts[s] / 2.0;
                        double value = normalization == Normalization.RAW ? raw
                                : HomerRnaCounts.rpkm(raw, wholeDirectoryMass[s], length);
                        values.add(value);
                        printed.add(HomerRnaCounts.printed(value));
                    }
                    result.add(new Row(group.id, copies, group.length, length, boxed(group.counts),
                            values, printed, group.contributors));
                }
                check.run();
                finished = true;
                return new Table(mode, normalization, samples, transcripts, parts, result);
            } catch (IOException | RuntimeException ex) {
                failed = true;
                throw ex;
            }
        }

        private void requireOpen() {
            if (failed || finished) throw new IllegalStateException("RNA expression accumulator is failed or finalized");
        }
    }

    private static long exactAdd(long left, long right) throws IOException {
        long result = Math.addExact(left, right);
        if (result > (1L << 52)) throw new IOException("RNA half-unit sum exceeds exact source-double capacity");
        return result;
    }
    private static List<Long> boxed(long[] values) {
        var result = new ArrayList<Long>(values.length);
        for (long value : values) result.add(value);
        return List.copyOf(result);
    }
}
