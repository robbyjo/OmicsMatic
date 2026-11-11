/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Algorithm translated from OpenGene fastp's MIT-licensed src/filter.cpp.
 * Copyright (c) 2016 OpenGene - Open Source Genetics Toolbox.
 * The original notice is distributed in fastp-MIT-NOTICE.txt. */
package org.jlinalg.sequence;

import java.util.Objects;

/**
 * Per-read fastp 0.22.0/0.23.2/0.25.0/1.0.1 cutting and filter predicates.
 * These primitives operate at the source filter stage. Adapter/overlap/poly-G/X,
 * correction, UMI, deduplication and whole-file processing are separate stages.
 * Literal source window boundaries, uppercase N handling and filter precedence
 * are retained. The maximum supported read length is ten million characters.
 */
public final class FastpReadFilter {
    private FastpReadFilter() { }
    /** Upper bound keeps the original signed-int quality arithmetic representable. */
    public static final int MAX_READ_LENGTH = 10_000_000;

    /** One explicitly enabled or disabled quality-cut operation. */
    public record Cut(boolean enabled, int window, int meanPhred) {
        public Cut {
            if (window < 1 || window > 1000 || meanPhred < 1 || meanPhred > 30)
                throw new IllegalArgumentException("fastp cutting requires window 1..1000 and mean Phred 1..30");
        }
        /** Disabled source-default window/threshold; no cutting is performed. */
        public static Cut disabled() { return new Cut(false, 4, 20); }
    }
    /** Global front/tail removal and ordered front/right/tail quality cutting. */
    public record Trimming(int front, int tail, Cut cutFront, Cut cutRight, Cut cutTail) {
        public Trimming {
            if (front < 0 || front > 30 || tail < 0 || tail > 100)
                throw new IllegalArgumentException("fastp global front/tail limits are 0..30 and 0..100");
            Objects.requireNonNull(cutFront); Objects.requireNonNull(cutRight); Objects.requireNonNull(cutTail);
        }
        /** No global removal and no quality cutting, matching source defaults. */
        public static Trimming defaults() { return new Trimming(0, 0, Cut.disabled(), Cut.disabled(), Cut.disabled()); }
    }
    /**
     * Source trim result and zero-based, half-open original interval.
     * A null source read is represented by present=false, empty strings and -1
     * coordinates; it differs from a present empty read. sourceFrontTrimmed is
     * the source out-parameter, which remains zero when the source returns null.
     */
    public record Trimmed(boolean present, String sequence, String quality, int start,
                          int end, int sourceFrontTrimmed) {
        public Trimmed {
            validate(sequence, quality);
            if (sourceFrontTrimmed < 0
                    || (present ? start < 0 || end < start || end - start != sequence.length() || sourceFrontTrimmed != start
                                : !sequence.isEmpty() || start != -1 || end != -1 || sourceFrontTrimmed != 0))
                throw new IllegalArgumentException("Invalid fastp trim result");
        }
    }
    /** Ordered read-filter settings; maximumLength=0 disables the upper length bound. */
    public record Filters(boolean qualityEnabled, int qualifiedPhred, int unqualifiedPercent,
                          int minimumAveragePhred, int maximumNs, boolean lengthEnabled,
                          int minimumLength, int maximumLength, boolean complexityEnabled,
                          int minimumComplexityPercent) {
        public Filters {
            if (qualifiedPhred < 0 || qualifiedPhred > 93 || unqualifiedPercent < 0 || unqualifiedPercent > 100
                    || minimumAveragePhred < 0 || minimumAveragePhred > 93 || maximumNs < 0 || maximumNs > 50
                    || minimumLength < 0 || maximumLength < 0 || minimumComplexityPercent < 0 || minimumComplexityPercent > 100)
                throw new IllegalArgumentException("Invalid explicit fastp filter settings");
        }
        /** Source defaults: Q15, at most 40% poor bases and five uppercase Ns, length at least 15. */
        public static Filters defaults() { return new Filters(true, 15, 40, 0, 5, true, 15, 0, false, 30); }
    }
    /** First failed source predicate; NULL and zero-length reads fail length unconditionally. */
    public enum Reason { PASS, QUALITY, N_BASE, TOO_SHORT, TOO_LONG, COMPLEXITY }
    /** Complete source-stage predicate counts and first disposition. */
    public record Assessment(Reason reason, int length, int poorBases, int uppercaseNs,
                             int totalPhred, int adjacentChanges) { }

    /**
     * Reproduce global and quality cutting, retaining the source's strict loop
     * endpoints. Enabling right cutting suppresses tail cutting. No adapter,
     * poly-tail or maximum-length stage is performed here.
     * @param sequence printable ASCII sequence, preserved literally
     * @param quality equal-length Phred+33 ASCII quality string
     * @param options explicit ordered cutting settings
     * @return present interval, present empty read, or source-null disposition
     */
    public static Trimmed trimAndCut(String sequence, String quality, Trimming options) {
        validate(sequence, quality); Objects.requireNonNull(options);
        int front = options.front(), tail = options.tail(), length = sequence.length();
        boolean cutting = options.cutFront().enabled() || options.cutRight().enabled() || options.cutTail().enabled();
        if (front == 0 && tail == 0 && !cutting) return kept(sequence, quality, 0, length, 0);
        int remaining = length - front - tail;
        if (remaining < 0) return missing();
        if (!cutting) return kept(sequence, quality, front, front + remaining, front);
        if (options.cutFront().enabled()) {
            int window = options.cutFront().window(), s = front;
            if (length - front - tail - window <= 0) return missing();
            int total = 0;
            for (int i = 0; i < window - 1; i++) total += quality.charAt(s + i);
            for (s = front; s + window < length - tail; s++) {
                total += quality.charAt(s + window - 1);
                if (s > front) total -= quality.charAt(s - 1);
                if ((double) total / window >= 33 + options.cutFront().meanPhred()) break;
            }
            if (s > 0) s += window - 1;
            while (s < length && sequence.charAt(s) == 'N') s++;
            front = s; remaining = length - front - tail;
        }
        if (options.cutRight().enabled()) {
            int window = options.cutRight().window(), s = front;
            if (length - front - tail - window <= 0) return missing();
            int total = 0;
            for (int i = 0; i < window - 1; i++) total += quality.charAt(s + i);
            boolean found = false;
            for (s = front; s + window < length - tail; s++) {
                total += quality.charAt(s + window - 1);
                if (s > front) total -= quality.charAt(s - 1);
                if ((double) total / window < 33 + options.cutRight().meanPhred()) { found = true; break; }
            }
            if (found) {
                while (s < length - 1 && quality.charAt(s) >= 33 + options.cutRight().meanPhred()) s++;
                remaining = s - front;
            }
        }
        if (!options.cutRight().enabled() && options.cutTail().enabled()) {
            int window = options.cutTail().window();
            if (length - front - tail - window <= 0) return missing();
            int total = 0, t = length - tail - 1;
            for (int i = 0; i < window - 1; i++) total += quality.charAt(t - i);
            for (t = length - tail - 1; t - window >= front; t--) {
                total += quality.charAt(t - window + 1);
                if (t < length - tail - 1) total -= quality.charAt(t + 1);
                if ((double) total / window >= 33 + options.cutTail().meanPhred()) break;
            }
            if (t < length - 1) t = t - window + 1;
            while (t >= 0 && sequence.charAt(t) == 'N') t--;
            remaining = t - front + 1;
        }
        if (remaining <= 0 || front >= length - 1) return missing();
        return kept(sequence, quality, front, front + remaining, front);
    }

    /**
     * Apply the separate final max_len stage, after any caller-provided adapter,
     * correction or poly-tail stage. A value of zero leaves the interval intact.
     * @param read present or source-null trimmed read
     * @param maximumLength zero or a nonnegative maximum retained length
     * @return original or truncated result
     */
    public static Trimmed maximumLength(Trimmed read, int maximumLength) {
        Objects.requireNonNull(read);
        if (maximumLength < 0) throw new IllegalArgumentException("Negative fastp maximum length");
        if (!read.present() || maximumLength == 0 || read.sequence().length() <= maximumLength) return read;
        return new Trimmed(true, read.sequence().substring(0, maximumLength), read.quality().substring(0, maximumLength),
                read.start(), read.start() + maximumLength, read.sourceFrontTrimmed());
    }

    /**
     * Evaluate postprocessing read filters in source order. The source integer
     * mean and uppercase-only N counting are preserved. Counts describe every
     * character even when an earlier predicate fails; they are an added ledger,
     * not a claim that all downstream predicates were executed by the source.
     * @param read present or source-null read at the filtering stage
     * @param options explicit quality, length and complexity predicates
     * @return first disposition and complete literal counts
     */
    public static Assessment assess(Trimmed read, Filters options) {
        Objects.requireNonNull(read); Objects.requireNonNull(options);
        validate(read.sequence(), read.quality());
        int length = read.sequence().length(), poor = 0, ns = 0, total = 0, changes = 0;
        for (int i = 0; i < length; i++) {
            int q = read.quality().charAt(i) - 33; total += q;
            if (q < options.qualifiedPhred()) poor++;
            if (read.sequence().charAt(i) == 'N') ns++;
            if (i > 0 && read.sequence().charAt(i - 1) != read.sequence().charAt(i)) changes++;
        }
        Reason reason = Reason.PASS;
        if (!read.present() || length == 0) reason = Reason.TOO_SHORT;
        else if (options.qualityEnabled()) {
            if (poor > options.unqualifiedPercent() * length / 100.0
                    || options.minimumAveragePhred() > 0 && total / length < options.minimumAveragePhred()) reason = Reason.QUALITY;
            else if (ns > options.maximumNs()) reason = Reason.N_BASE;
        }
        if (reason == Reason.PASS && options.lengthEnabled()) {
            if (length < options.minimumLength()) reason = Reason.TOO_SHORT;
            else if (options.maximumLength() > 0 && length > options.maximumLength()) reason = Reason.TOO_LONG;
        }
        if (reason == Reason.PASS && options.complexityEnabled()
                && (length <= 1 || (double) changes / (length - 1) < options.minimumComplexityPercent() / 100.0)) reason = Reason.COMPLEXITY;
        return new Assessment(reason, length, poor, ns, total, changes);
    }
    private static Trimmed missing() { return new Trimmed(false, "", "", -1, -1, 0); }
    private static Trimmed kept(String sequence, String quality, int from, int to, int sourceFront) {
        return new Trimmed(true, sequence.substring(from, to), quality.substring(from, to), from, to, sourceFront);
    }
    private static void validate(String sequence, String quality) {
        Objects.requireNonNull(sequence); Objects.requireNonNull(quality);
        if (sequence.length() != quality.length() || sequence.length() > MAX_READ_LENGTH)
            throw new IllegalArgumentException("fastp requires equal complete strings within the ten-million-character read limit");
        for (int i = 0; i < sequence.length(); i++)
            if (sequence.charAt(i) < 33 || sequence.charAt(i) > 126 || quality.charAt(i) < 33 || quality.charAt(i) > 126)
                throw new IllegalArgumentException("fastp native filter requires literal printable ASCII sequence and Phred+33 qualities");
    }
}
