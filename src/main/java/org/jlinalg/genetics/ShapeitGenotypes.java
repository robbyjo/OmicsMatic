/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Original SHAPEIT4 4.1.3 and SHAPEIT5 c34d4db initial target-reader encoding.
 * Sources: SHAPEIT4 genotype_reader2.cpp and SHAPEIT5 phase_common's
 * genotype_reader_reading.cpp. The upstream MIT notices are retained separately.
 *
 * <p>The original readers compare missing GT values with the literal BCF value
 * zero. A phase-marked missing allele has value one and therefore can be read as
 * a reference allele. These methods preserve and count that behavior. They also
 * distinguish an actual missing allele from a diploid buffer's vector-end pad.
 * They do not replace either source's subsequent inference, haploid processing,
 * scaffold/reference processing, rare phasing, file readers or variant selection.
 */
public final class ShapeitGenotypes {
    private ShapeitGenotypes() { }

    /** HTSlib's int32 vector-end sentinel, distinct from both missing GT encodings. */
    public static final int VECTOR_END = Integer.MIN_VALUE + 1;

    /**
     * One complete biallelic BCF GT cell before row-width padding.
     * Values 0/1 are unphased/phased missing, 2/3 encode REF and 4/5 ALT.
     * An explicitly supplied second-slot vector-end retains its storage width.
     * @param values exact one/two integer GT values, not allele indices
     */
    public record WireCall(List<Integer> values) {
        /**
         * Retain the complete original cell and reject unsupported GT encodings.
         * @param values exact original biallelic BCF values
         */
        public WireCall {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.isEmpty() || values.size() > 2) throw new IllegalArgumentException("Require one or two complete BCF GT values");
            for (int i = 0; i < values.size(); i++) {
                int code = values.get(i);
                if (!(code >= 0 && code <= 5) && !(i == 1 && code == VECTOR_END))
                    throw new IllegalArgumentException("Require biallelic GT values; vector-end is permitted only in the second slot");
            }
        }
        /**
         * Actual encoded ploidy, without counting a vector-end pad.
         * @return one or two encoded alleles
         */
        public int ploidy() { return values.size() == 1 || values.get(1) == VECTOR_END ? 1 : 2; }
        /**
         * Count supplied observed alleles, excluding padding.
         * @return complete observed allele count
         */
        public int observedAlleles() { return (int)values.stream().filter(v -> v >= 2 && v <= 5).count(); }
        /**
         * Count actual missing alleles, including the phase-marked form.
         * @return complete missing allele count
         */
        public int missingAlleles() { return ploidy() - observedAlleles(); }
    }

    /**
     * Encode a biallelic VCF GT field as HTSlib does, retaining separators and ploidy.
     * First-allele phase bits that exist only in a BCF buffer require WireCall.
     * @param token complete GT text
     * @return original wire representation, without diploid padding
     */
    public static WireCall parseVcf(String token) {
        if (token == null || token.isEmpty()) throw new IllegalArgumentException("Require a nonempty GT token");
        boolean pipe = token.indexOf('|') >= 0, slash = token.indexOf('/') >= 0;
        if (pipe && slash) throw new IllegalArgumentException("Mixed GT separators are not admitted");
        String[] fields = token.split(pipe ? "\\|" : "/", -1);
        if (fields.length < 1 || fields.length > 2) throw new IllegalArgumentException("Require haploid or diploid GT");
        var values = new ArrayList<Integer>(fields.length);
        for (int i = 0; i < fields.length; i++) {
            String field = fields[i];
            if (!field.equals(".") && !field.matches("[0-9]+"))
                throw new IllegalArgumentException("Selected SHAPEIT GT must contain missing or nonnegative integer allele indices");
            final int allele;
            try { allele = field.equals(".") ? -1 : Integer.parseInt(field); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("GT allele index exceeds signed32 capacity", invalid); }
            if (allele > 1) throw new IllegalArgumentException("Selected SHAPEIT GT must be biallelic");
            int value = allele == -1 ? 0 : 2 * (allele + 1);
            values.add(value | (pipe && i > 0 ? 1 : 0));
        }
        return new WireCall(values);
    }

    /** Immutable initial reader state, before phase inference or haploid adjustments. */
    public static final class Cell {
        private final WireCall source;
        private final int packed;
        private Cell(WireCall source) {
            this.source = source;
            int first = source.values().get(0);
            int second = source.values().size() == 2 ? source.values().get(1) : VECTOR_END;
            boolean a0 = (first >> 1) - 1 == 1, a1 = (second >> 1) - 1 == 1;
            boolean missing = first == 0 || second == 0;
            packed = (missing ? 1 : a0 != a1 ? 2 : 0) | (a0 ? 4 : 0) | (a1 ? 8 : 0);
        }
        /**
         * Unchanged original cell, including explicit storage width.
         * @return original wire cell
         */
        public WireCall source() { return source; }
        /**
         * Original four-bit initial state (type, hap0, hap1).
         * @return original packed nibble
         */
        public int packed() { return packed; }
        /**
         * Original left haplotype bit.
         * @return whether the ALT bit is set
         */
        public boolean haplotype0() { return (packed & 4) != 0; }
        /**
         * Original right haplotype bit.
         * @return whether the ALT bit is set
         */
        public boolean haplotype1() { return (packed & 8) != 0; }
        /**
         * Original literal-zero missingness test.
         * @return whether the reader marks the whole call missing
         */
        public boolean missing() { return (packed & 3) == 1; }
        /**
         * Original reader's heterozygous state.
         * @return whether the call is nonmissing and heterozygous
         */
        public boolean heterozygous() { return (packed & 3) == 2; }
        /**
         * Original reader's homozygous state.
         * @return whether the call is nonmissing and homozygous
         */
        public boolean homozygous() { return (packed & 3) == 0; }
        /**
         * Detect actual missing alleles that escape the reader's missing flag.
         * @return whether the original reader treats missing data as reference
         */
        public boolean missingAlleleReadAsReference() { return source.missingAlleles() > 0 && !missing(); }
        /**
         * Detect a vector-end second slot in the diploid storage frame.
         * @return whether the source cell has only one actual allele slot
         */
        public boolean vectorEndInSecondSlot() { return source.ploidy() == 1; }
        /**
         * Initial biallelic dosage, including bits on missing cells.
         * @return sum of original ALT haplotype bits
         */
        public int dosage() { return (haplotype0() ? 1 : 0) + (haplotype1() ? 1 : 0); }
    }

    /**
     * Decode one complete selected target row with an actual diploid storage frame.
     * SHAPEIT4 asserts that frame width; this method never infers a supported
     * all-haploid workflow from a buffer assumption. Explicit padded BCF calls
     * and VCF haploid cells in an otherwise diploid row remain distinguishable.
     * @param calls complete exact sample-order row
     * @param maximumSamples caller-selected complete row capacity
     * @return every initial reader cell in the original order
     */
    public static List<Cell> decodeDiploidStorageRow(List<WireCall> calls, int maximumSamples) {
        Objects.requireNonNull(calls, "calls");
        if (maximumSamples < 1 || calls.isEmpty() || calls.size() > maximumSamples)
            throw new IllegalArgumentException("Complete SHAPEIT row exceeds the declared sample capacity or is empty");
        if (calls.stream().noneMatch(c -> Objects.requireNonNull(c, "call").values().size() == 2))
            throw new IllegalArgumentException("The supplied GT buffer has no diploid storage frame; do not invent a second allele");
        return calls.stream().map(Cell::new).toList();
    }

    /**
     * Complete diploid-output audit; it does not establish phase accuracy or file EOF.
     * @param rows complete accepted selected rows
     * @param calls complete accepted genotype cells
     * @param readerMissingCalls cells marked missing by the original reader
     * @param heterozygousCalls original nonmissing heterozygous cells
     * @param homozygousCalls original nonmissing homozygous cells
     * @param observedInputAllelesIgnoredInMissingCalls supplied alleles ignored in reader-missing cells
     * @param missingAlleleReadAsReferenceCalls cells with missing alleles escaping the reader's missing flag
     * @param vectorEndInputCalls input cells with a vector-end second slot
     * @param inputSha256 complete ordered input-wire matrix digest
     * @param outputSha256 complete ordered output-wire matrix digest
     */
    public record Report(long rows, long calls, long readerMissingCalls, long heterozygousCalls,
            long homozygousCalls, long observedInputAllelesIgnoredInMissingCalls,
            long missingAlleleReadAsReferenceCalls, long vectorEndInputCalls,
            String inputSha256, String outputSha256) { }

    /**
     * Complete, ordered selected-row contract for diploid output.
     * A surrounding workflow must independently supply its complete selection,
     * verify file EOF, sample/allele identities, runtime and process completion,
     * and use a separate audited contract for haploid/scaffold/rare modes.
     * Any rejected row permanently invalidates this audit.
     */
    public static final class Audit {
        private final int samples;
        private final long maximumRows, maximumCalls;
        private final MessageDigest input = digest(), output = digest();
        private long rows, calls, missing, heterozygous, homozygous, ignored, phaseMissing, vectorEnd;
        private boolean failed, finished;

        /**
         * Create a complete ordered audit with explicit population capacities.
         * @param inputSamples complete exact original sample order
         * @param outputSamples complete exact output sample order
         * @param maximumRows declared complete selected-row capacity
         * @param maximumCalls declared complete genotype-cell capacity
         */
        public Audit(List<String> inputSamples, List<String> outputSamples, long maximumRows, long maximumCalls) {
            var names = List.copyOf(Objects.requireNonNull(inputSamples, "inputSamples"));
            if (names.isEmpty() || !names.equals(outputSamples) || new HashSet<>(names).size() != names.size()
                    || maximumRows < 1 || maximumCalls < names.size())
                throw new IllegalArgumentException("Require identical complete sample order, unique names and adequate declared capacities");
            this.samples = names.size(); this.maximumRows = maximumRows; this.maximumCalls = maximumCalls;
            number(input, samples); number(output, samples);
            for (String name : names) { identity(name); text(input, name); text(output, name); }
        }

        /**
         * Check every cell, preserving reader-defined observed dosage and permitting
         * inferred calls only where the original reader marks a missing genotype.
         * Missing/phase and vector-end departures are retained in the final counters.
         * @param sourceIdentity full original variant identity, including alleles
         * @param original complete raw BCF/VCF row
         * @param outputIdentity full output variant identity
         * @param phasedOutput complete exact sample-order GT output row
         */
        public void accept(String sourceIdentity, List<WireCall> original, String outputIdentity, List<String> phasedOutput) {
            try {
                requireActive(); identity(sourceIdentity);
                if (!sourceIdentity.equals(outputIdentity) || original.size() != samples || phasedOutput.size() != samples)
                    throw new IllegalArgumentException("SHAPEIT output changed the complete variant/sample population or order");
                long newRows = Math.addExact(rows, 1), newCalls = Math.addExact(calls, samples);
                if (newRows > maximumRows || newCalls > maximumCalls)
                    throw new IllegalArgumentException("Complete SHAPEIT output exceeds declared capacities; no rows are truncated");
                var states = decodeDiploidStorageRow(original, samples);
                var emitted = new ArrayList<WireCall>(samples);
                for (int i = 0; i < samples; i++) {
                    String token = phasedOutput.get(i); WireCall call = parseVcf(token);
                    if (call.ploidy() != 2 || call.missingAlleles() != 0 || token.indexOf('|') < 0)
                        throw new IllegalArgumentException("Require a complete, phased diploid output GT");
                    Cell source = states.get(i);
                    int dosage = (call.values().get(0) >> 1) - 1 + (call.values().get(1) >> 1) - 1;
                    if (!source.missing() && dosage != source.dosage())
                        throw new IllegalArgumentException("SHAPEIT output changed an original reader-defined observed genotype");
                    emitted.add(call);
                }
                text(input, sourceIdentity); text(output, outputIdentity);
                for (int i = 0; i < samples; i++) {
                    Cell source = states.get(i); WireCall raw = original.get(i);
                    number(input, raw.values().size()); for (int value : raw.values()) number(input, value);
                    number(output, 2); for (int value : emitted.get(i).values()) number(output, value);
                    if (source.missing()) { missing++; ignored += raw.observedAlleles(); }
                    if (source.heterozygous()) heterozygous++;
                    if (source.homozygous()) homozygous++;
                    if (source.missingAlleleReadAsReference()) phaseMissing++;
                    if (source.vectorEndInSecondSlot()) vectorEnd++;
                }
                rows = newRows; calls = newCalls;
            } catch (RuntimeException invalid) { failed = true; throw invalid; }
        }

        /**
         * Finish a nonempty, complete accepted population.
         * @return final complete counters and ordered wire-matrix hashes
         */
        public Report finish() {
            requireActive();
            if (rows == 0) { failed = true; throw new IllegalStateException("An empty SHAPEIT audit is not completed output"); }
            finished = true;
            return new Report(rows, calls, missing, heterozygous, homozygous, ignored, phaseMissing, vectorEnd,
                    HexFormat.of().formatHex(input.digest()), HexFormat.of().formatHex(output.digest()));
        }
        private void requireActive() { if (failed || finished) throw new IllegalStateException("SHAPEIT audit is already failed or finished"); }
    }

    private static void identity(String value) {
        if (value == null || value.isEmpty() || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Require an exact nonempty single-line identity");
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void number(MessageDigest digest, int value) {
        digest.update((byte)(value >>> 24)); digest.update((byte)(value >>> 16));
        digest.update((byte)(value >>> 8)); digest.update((byte)value);
    }
    private static void text(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); number(digest, bytes.length); digest.update(bytes);
    }
}
