/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Original-edition StringTie finalization arithmetic and conservative printed
 * normalization consistency checks. It never infers read counts or fitted
 * transcript abundances. Compatible denominator intervals are not recovered
 * exact denominators, confidence intervals or a biological accuracy certificate.
 */
public final class StringTieNormalization {
    private StringTieNormalization() { }
    private static final Pattern FIXED = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private static final MathContext DOWN = new MathContext(80, RoundingMode.FLOOR);
    private static final MathContext UP = new MathContext(80, RoundingMode.CEILING);
    private static final BigDecimal HALF_SIX = new BigDecimal("0.0000005");

    /** Exact supported original edition; arithmetic is never guessed from the latest release. */
    public enum Edition {
        V1_3_0("1.3.0", true, false), V1_3_6("1.3.6", false, false),
        V2_1_2("2.1.2", true, true), V2_1_7("2.1.7", true, true),
        V2_2_1("2.2.1", true, true), V3_0_0("3.0.0", true, true), V3_0_3("3.0.3", false, true);
        private final String version; private final boolean floatScalar, guarded;
        Edition(String version, boolean floatScalar, boolean guarded) { this.version = version; this.floatScalar = floatScalar; this.guarded = guarded; }
        /** Exact source edition string. */
        public String version() { return version; }
        /** Whether tcov and finalized values use binary32. */
        public boolean floatScalar() { return floatScalar; }
        /** Resolve only an exact explicitly supported edition. */
        public static Edition of(String version) {
            for (Edition edition : values()) if (edition.version.equals(version)) return edition;
            throw new IllegalArgumentException("Supply an exact supported StringTie edition; do not substitute for unreported versions");
        }
    }
    /** Source nonfinite classes are preserved rather than represented as missing or zero. */
    public enum NumberClass { FINITE, POSITIVE_INFINITY, NEGATIVE_INFINITY, NAN }
    /**
     * One native source-arithmetic value. fixed6 is absent for nonfinite values;
     * NaN payload/sign and C-library nonfinite spelling are not portable claims.
     */
    public record Value(double value, NumberClass numberClass, String fixed6) { }
    /** Source parse/clamp and finalization, with original input and explicit supplied denominators. */
    public record Finalized(Edition edition, String coverageToken, double fragmentLengthDenominator,
                            double coverageSumDenominator, Value readbackCoverage, Value fpkm, Value tpm) { }
    /** Whether a common active normalization denominator can be constrained. */
    public enum Resolution { CONSTRAINED, ALL_ZERO_UNRESOLVED, NO_POSITIVE_NUMERATOR }
    /** Inclusive conservative active-branch bounds; null maximum means unbounded above. */
    public record Bounds(BigDecimal minimum, BigDecimal maximum) {
        /** Validate a nonnegative, ordered interval. */
        public Bounds {
            Objects.requireNonNull(minimum);
            if (minimum.signum() < 0 || maximum != null && maximum.compareTo(minimum) < 0)
                throw new IllegalArgumentException("Invalid normalization denominator interval");
        }
        /** Check an exact supplied finite nonnegative binary64 denominator. */
        public boolean contains(double value) {
            if (!Double.isFinite(value) || value < 0) return false;
            BigDecimal exact = new BigDecimal(value);
            return minimum.compareTo(exact) <= 0 && (maximum == null || maximum.compareTo(exact) >= 0);
        }
    }
    /**
     * Complete common-denominator constraint. ALL_ZERO_UNRESOLVED retains only
     * conditional active-branch bounds: a guarded zero branch may also explain
     * the output. No denominator is identified when all numerators are zero.
     */
    public record Denominator(Resolution resolution, Bounds activeBranchBounds, int rows, int positiveOutputs,
                               int positiveNumerators, double activeBranchThreshold) { }
    /** One emitted original GTF transcript and its matched complete table row. */
    public record Readback(String transcriptId, String originalCoverage, Value sourceCoverage,
                            String originalTableCoverage, String originalFpkm, String originalTpm,
                            double fpkmNumerator, double tpmNumerator) { }
    /** Only the complete checker can construct an audit. */
    public static final class Audit {
        private final Edition edition; private final List<Readback> rows; private final List<String> omitted;
        private final Denominator fpkm, tpm;
        private Audit(Edition edition, List<Readback> rows, List<String> omitted, Denominator fpkm, Denominator tpm) {
            this.edition = edition; this.rows = List.copyOf(rows); this.omitted = List.copyOf(omitted); this.fpkm = fpkm; this.tpm = tpm;
        }
        /** Exact explicitly selected original edition. */
        public Edition edition() { return edition; }
        /** Every emitted source transcript, in GTF order. */
        public List<Readback> rows() { return rows; }
        /** Every absent original GTF reference identity, without invented values. */
        public List<String> omittedTranscriptIds() { return omitted; }
        /** Conservative source Frag_Len constraint, not an inferred fragment count. */
        public Denominator fpkmDenominator() { return fpkm; }
        /** Conservative source Cov_Sum constraint, not forced one-million TPM normalization. */
        public Denominator tpmDenominator() { return tpm; }
    }

    /**
     * Reproduce extracted original finalization expressions under round-to-nearest
     * IEEE arithmetic. Negative coverage behavior, signed zero, guard thresholds,
     * binary32 intermediate products and nonfinite results remain explicit.
     * Finite fixed6 values use exact binary-value decimal rounding to nearest-even.
     * This is arithmetic, not a fitted StringTie analysis.
     * @param edition explicit original edition
     * @param coverageToken bounded original fixed-point token; negative source controls are permitted
     * @param fragmentLengthDenominator explicitly supplied finite nonnegative original Frag_Len
     * @param coverageSumDenominator explicitly supplied finite nonnegative original Cov_Sum
     * @return source-arithmetic values, including nonfinite classes without fabricated finite strings
     */
    public static Finalized finalizeCoverage(Edition edition, String coverageToken, double fragmentLengthDenominator,
                                             double coverageSumDenominator) {
        Objects.requireNonNull(edition);
        if (!Double.isFinite(fragmentLengthDenominator) || fragmentLengthDenominator < 0
                || !Double.isFinite(coverageSumDenominator) || coverageSumDenominator < 0)
            throw new IllegalArgumentException("Supply explicit finite nonnegative original normalization denominators");
        double cov = readback(edition, coverageToken);
        double fpkm = edition.guarded && !(fragmentLengthDenominator > 0.001) ? 0.0
                : quotient(edition, numerator(edition, cov, 1000000000), fragmentLengthDenominator);
        double tpm = edition.guarded && !(coverageSumDenominator > 0.00001) ? 0.0
                : quotient(edition, numerator(edition, cov, 1000000), coverageSumDenominator);
        return new Finalized(edition, coverageToken, fragmentLengthDenominator, coverageSumDenominator, value(cov), value(fpkm), value(tpm));
    }

    /**
     * Check complete captured original estimation/table readback and common
     * transcript normalization factors. GTF/table identity and complete reference
     * topology must already have passed StringTieTables.auditEstimation. This
     * method independently binds the supplied GTF to that checked population.
     * Missing GTF identities remain explicit. Gene reductions, alignment counts,
     * exact hidden denominators and abundance fitting are not certified here.
     * @param edition exact original edition; never inferred for an unreported paper run
     * @param estimation complete native ESTIMATION GTF document
     * @param tables complete checked gene/Ballgown family
     * @param checkpoint cancellation/progress hook
     * @return complete readback checks and conservative conditional denominator intervals
     * @throws IOException for identity/readback/numeric inconsistency or an incompatible common factor
     */
    public static Audit auditEstimation(Edition edition, StringTieGtf.Document estimation, StringTieTables.Audit tables,
                                        Runnable checkpoint) throws IOException {
        Objects.requireNonNull(edition); Objects.requireNonNull(estimation); Objects.requireNonNull(tables); Objects.requireNonNull(checkpoint);
        if (estimation.profile() != StringTieGtf.Profile.ESTIMATION) throw new IllegalArgumentException("Require original ESTIMATION GTF output");
        var expected = new HashMap<String, StringTieTables.TranscriptRow>();
        for (var row : tables.transcripts()) expected.put(row.transcriptId(), row);
        var reference = new HashMap<String, StringTieGtf.ReferenceRow>();
        for (var row : tables.reference().rows()) reference.put(row.transcriptId(), row);
        var linkedExons = new HashMap<Integer, ArrayList<StringTieTables.FeatureRow>>();
        for (var link : tables.exonLinks()) linkedExons.computeIfAbsent(link.transcriptId(), k -> new ArrayList<>()).add(tables.exons().get(link.featureId() - 1));
        for (var exons : linkedExons.values()) exons.sort(Comparator.comparingInt(StringTieTables.FeatureRow::start1).thenComparingInt(StringTieTables.FeatureRow::end1));
        var readbacks = new ArrayList<Readback>(); var seen = new HashSet<String>();
        var fp = new Constraint(edition, edition.guarded ? 0.001 : 0.0);
        var tp = new Constraint(edition, edition.guarded ? 0.00001 : 0.0);
        for (var transcript : estimation.transcripts()) {
            checkpoint.run(); var table = expected.get(transcript.id()); var disposition = reference.get(transcript.id());
            if (table == null || disposition == null || disposition.disposition() != StringTieGtf.Disposition.EMITTED
                    || !seen.add(transcript.id()) || !table.geneId().equals(transcript.geneId()) || !table.contig().equals(transcript.contig())
                    || table.strand() != transcript.strand() || table.start1() != transcript.start1() || table.end1() != transcript.end1()
                    || table.exonCount() != transcript.exons().size() || table.length() != transcript.exonicLength()
                    || disposition.outputOrdinal() != readbacks.size() + 1)
                throw new IOException("Supplied normalization GTF differs from the checked reference/table population: " + transcript.id());
            var originalExons = linkedExons.get(table.id());
            if (originalExons == null || originalExons.size() != transcript.exons().size())
                throw new IOException("Normalization GTF exon population differs from the checked topology: " + transcript.id());
            for (int i = 0; i < originalExons.size(); i++) if (originalExons.get(i).start1() != transcript.exons().get(i).start1()
                    || originalExons.get(i).end1() != transcript.exons().get(i).end1())
                throw new IOException("Normalization GTF internal exon geometry differs from the checked topology: " + transcript.id());
            var abundance = transcript.abundance();
            requireOutput(abundance.coverage()); requireOutput(abundance.fpkm()); requireOutput(abundance.tpm());
            Value parsed = value(readback(edition, abundance.coverage().text()));
            if (parsed.numberClass() != NumberClass.FINITE || !parsed.fixed6().equals(table.coverage().text()))
                throw new IOException("Original GTF-to-Ballgown coverage readback differs for " + transcript.id());
            if (abundance.fpkm().value().compareTo(table.fpkm().value()) != 0)
                throw new IOException("Original GTF/Ballgown finalized FPKM differs for " + transcript.id());
            double fn = numerator(edition, parsed.value(), 1000000000), tn = numerator(edition, parsed.value(), 1000000);
            fp.add(fn, abundance.fpkm(), transcript.id()); tp.add(tn, abundance.tpm(), transcript.id());
            readbacks.add(new Readback(transcript.id(), abundance.coverage().text(), parsed, table.coverage().text(),
                    abundance.fpkm().text(), abundance.tpm().text(), fn, tn));
        }
        var omitted = new ArrayList<String>();
        for (var row : tables.reference().rows()) {
            checkpoint.run();
            if (row.disposition() == StringTieGtf.Disposition.OMITTED) {
                if (seen.contains(row.transcriptId())) throw new IOException("Omitted reference identity reappears in normalization GTF");
                omitted.add(row.transcriptId());
            } else if (!seen.contains(row.transcriptId())) throw new IOException("Normalization GTF lost a previously emitted reference identity");
        }
        return new Audit(edition, readbacks, omitted, fp.finish(), tp.finish());
    }

    private static void requireOutput(StringTieGtf.Printed value) throws IOException {
        if (value.value().scale() != 6 && !value.text().equals("0.0")) throw new IOException("Original normalization output requires six decimals or the explicit literal zero shortcut");
    }
    private static double readback(Edition edition, String text) {
        Objects.requireNonNull(text);
        if (text.length() > 1024 || !FIXED.matcher(text).matches()) throw new IllegalArgumentException("Require a bounded original fixed-point coverage token");
        double parsed = edition.floatScalar ? Float.parseFloat(text) : Double.parseDouble(text);
        if (edition == Edition.V1_3_6 ? parsed <= 0.0 : edition != Edition.V1_3_0 && parsed < 0.0) parsed = 0.0;
        return parsed;
    }
    private static double numerator(Edition edition, double coverage, int scale) {
        return edition.floatScalar ? (float)coverage * scale : coverage * scale;
    }
    private static double quotient(Edition edition, double numerator, double denominator) {
        double result = numerator / denominator;
        return edition.floatScalar ? (float)result : result;
    }
    private static Value value(double number) {
        if (Double.isNaN(number)) return new Value(number, NumberClass.NAN, null);
        if (number == Double.POSITIVE_INFINITY) return new Value(number, NumberClass.POSITIVE_INFINITY, null);
        if (number == Double.NEGATIVE_INFINITY) return new Value(number, NumberClass.NEGATIVE_INFINITY, null);
        BigDecimal printed = new BigDecimal(number).setScale(6, RoundingMode.HALF_EVEN);
        String text = printed.toPlainString();
        if (printed.signum() == 0 && Double.doubleToRawLongBits(number) < 0) text = "-" + text;
        return new Value(number, NumberClass.FINITE, text);
    }
    private static final class Constraint {
        private final Edition edition; private final double threshold;
        private BigDecimal minimum = BigDecimal.ZERO, maximum;
        private int rows, positiveOutputs, positiveNumerators;
        private Constraint(Edition edition, double threshold) { this.edition = edition; this.threshold = threshold; }
        private void add(double numerator, StringTieGtf.Printed output, String id) throws IOException {
            rows++;
            if (!Double.isFinite(numerator) || numerator < 0)
                throw new IOException("Nonfinite/negative source numerator cannot supply a finite normalization audit for " + id + "; preserve source output and skip dependent normalization analysis");
            if (output.value().signum() > 0) positiveOutputs++;
            if (numerator == 0) {
                if (output.value().signum() != 0) throw new IOException("Positive finalized abundance has zero original numerator for " + id);
                return;
            }
            positiveNumerators++;
            if (output.text().equals("0.0")) throw new IOException("Literal unbundled zero conflicts with positive original coverage for " + id);
            BigDecimal lower = output.value().subtract(HALF_SIX).max(BigDecimal.ZERO), upper = output.value().add(HALF_SIX);
            double magnitude = upper.doubleValue();
            double sourceUlp = edition.floatScalar ? Math.ulp((float)magnitude) : Math.ulp(magnitude);
            if (!Double.isFinite(sourceUlp)) throw new IOException("Printed abundance exceeds the selected source arithmetic domain for " + id);
            BigDecimal castAllowance = new BigDecimal(sourceUlp).divide(BigDecimal.valueOf(2));
            // A binary64 division precedes the possible binary32 final cast.
            double inflated = upper.add(castAllowance).doubleValue();
            if (!Double.isFinite(inflated)) inflated = Double.MAX_VALUE;
            BigDecimal allowance = castAllowance.add(new BigDecimal(Math.ulp(inflated)));
            lower = lower.subtract(allowance).max(BigDecimal.ZERO); upper = upper.add(allowance);
            BigDecimal exactNumerator = new BigDecimal(numerator);
            BigDecimal lo = exactNumerator.divide(upper, DOWN), hi = lower.signum() == 0 ? null : exactNumerator.divide(lower, UP);
            minimum = minimum.max(lo);
            if (hi != null) maximum = maximum == null ? hi : maximum.min(hi);
            if (maximum != null && maximum.compareTo(minimum) < 0) throw new IOException("No common original normalization denominator is compatible with all printed transcripts at " + id);
        }
        private Denominator finish() throws IOException {
            if (positiveNumerators == 0) return new Denominator(Resolution.NO_POSITIVE_NUMERATOR, null, rows, positiveOutputs, 0, threshold);
            minimum = minimum.max(new BigDecimal(threshold));
            if (maximum != null && maximum.compareTo(minimum) < 0) throw new IOException("Original active normalization denominator cannot exceed its source branch threshold");
            return new Denominator(positiveOutputs > 0 ? Resolution.CONSTRAINED : Resolution.ALL_ZERO_UNRESOLVED,
                    new Bounds(minimum, maximum), rows, positiveOutputs, positiveNumerators, threshold);
        }
    }
}
