/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Original Eagle 2.4.1 biallelic target encoding and genotype-output contracts.
 * Source: GenoData.cpp, SyncedVcfData.cpp and Eagle.cpp in the official release.
 * Raw unphased, phased, missing and mixed-ploidy representations remain distinct.
 * This component does not infer phase or establish biological phasing accuracy.
 * File readers must independently verify complete EOF, variant selection,
 * reference-panel eligibility, source hashes and actual process success.
 */
public final class EagleGenotypes {
    private EagleGenotypes() { }

    /** Original cohort-only or reference-panel phasing path. */
    public enum Mode { COHORT, REFERENCE }
    /** Whether original --noImpMissing is requested. */
    public enum Missing { KEEP, IMPUTE }
    /** Phased analysis row, or explicitly retained reference-mode target-only row. */
    public enum Disposition { PHASED, UNPHASED_TARGET_ONLY }

    /** Explicit original mode, selected chromosome class and missing-ploidy policy. */
    public record Options(Mode mode, boolean sexChromosome, Missing missing, boolean keepMissingPloidyX) {
        public Options { Objects.requireNonNull(mode, "mode"); Objects.requireNonNull(missing, "missing"); }
    }

    /** Immutable original GT, with -1 representing a missing allele. */
    public static final class Call {
        private final String raw;
        private final List<Integer> alleles;
        private final char separator;
        private Call(String raw, List<Integer> alleles, char separator) {
            this.raw = raw; this.alleles = List.copyOf(alleles); this.separator = separator;
        }
        /** @return original GT text, including its original separators */
        public String raw() { return raw; }
        /** @return encoded alleles in their original order; -1 is missing */
        public List<Integer> alleles() { return alleles; }
        /** @return original encoded ploidy, without padding a single dot */
        public int ploidy() { return alleles.size(); }
        /** @return true only for an explicitly pipe-separated diploid call */
        public boolean phased() { return separator == '|'; }
        /** @return whether at least one encoded allele is missing */
        public boolean missing() { return alleles.contains(-1); }
        /** @return whether some, but not all, encoded alleles are missing */
        public boolean partiallyMissing() { return missing() && alleles.stream().anyMatch(a -> a >= 0); }
        /** @return count of supplied nonmissing allele copies */
        public int observedAlleles() { return (int)alleles.stream().filter(a -> a >= 0).count(); }
    }

    /**
     * Parse haploid or diploid GT without inventing phase, alleles or ploidy.
     * Multi-allelic indices can be retained for explicitly unphased rows; selected
     * target encoding separately requires biallelic indices 0 and 1.
     * @param raw original GT field
     * @param maximumAllele highest allele index declared by REF/ALT
     * @return immutable original call
     * @throws IllegalArgumentException invalid GT, unsupported ploidy or out-of-range index
     */
    public static Call parse(String raw, int maximumAllele) {
        if (raw == null || raw.isEmpty() || maximumAllele < 0) throw new IllegalArgumentException("Require a nonempty GT and declared allele bound");
        boolean slash = raw.indexOf('/') >= 0, pipe = raw.indexOf('|') >= 0;
        if (slash && pipe) throw new IllegalArgumentException("Mixed GT separators are not admitted");
        char separator = slash ? '/' : pipe ? '|' : 0;
        String[] fields = separator == 0 ? new String[]{raw} : raw.split(separator == '|' ? "\\|" : "/", -1);
        if (fields.length < 1 || fields.length > 2) throw new IllegalArgumentException("Require one or two explicit GT allele fields");
        var alleles = new ArrayList<Integer>(fields.length);
        for (String field : fields) {
            if (field.equals(".")) { alleles.add(-1); continue; }
            if (!field.matches("[0-9]+")) throw new IllegalArgumentException("Invalid GT allele index");
            final int value;
            try { value = Integer.parseInt(field); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("GT allele index exceeds signed32 capacity", invalid); }
            if (value > maximumAllele) throw new IllegalArgumentException("GT allele index exceeds declared REF/ALT");
            alleles.add(value);
        }
        return new Call(raw, alleles, separator);
    }

    /** Original target frame width and input interpretation, including partial missingness. */
    public record Encoding(int framePloidy, List<Integer> codes, long fullyMissingCalls,
            long partiallyMissingCalls, long observedAllelesIgnoredByInference) {
        public Encoding { codes = List.copyOf(codes); }
    }

    /**
     * Reproduce the original target dosage/missing codes for a complete selected row.
     * Cohort code 3 and reference code 9 mean the entire call is missing, even
     * when an individual allele was supplied. Haploid observed X calls are doubled
     * internally, while their original external representation remains unchanged.
     * @param calls complete sample-order row
     * @param options original mode and chromosome class
     * @return complete original codes and missingness inventory
     * @throws IllegalArgumentException incompatible row frame, ploidy or allele indices
     */
    public static Encoding encodeTargets(List<Call> calls, Options options) {
        Objects.requireNonNull(options, "options");
        if (calls == null || calls.isEmpty()) throw new IllegalArgumentException("Require a complete nonempty target row");
        int frame = 1; for (Call call : calls) frame = Math.max(frame, Objects.requireNonNull(call, "call").ploidy());
        if (frame != 2 && (options.mode() == Mode.COHORT || !options.sexChromosome()))
            throw new IllegalArgumentException("Original selected row requires a diploid storage frame; all-haploid rows are admitted only in reference-mode X");
        long full = 0, partial = 0, ignored = 0; var codes = new ArrayList<Integer>(calls.size());
        for (Call call : calls) {
            if (call.alleles().stream().anyMatch(a -> a > 1)) throw new IllegalArgumentException("Selected Eagle target row must be biallelic");
            if (call.missing()) {
                if (call.partiallyMissing()) { partial++; ignored += call.observedAlleles(); } else full++;
                codes.add(options.mode() == Mode.COHORT ? 3 : 9);
            } else {
                if (call.ploidy() == 1 && !options.sexChromosome())
                    throw new IllegalArgumentException("Original observed haploid target requires the declared X chromosome");
                codes.add(call.ploidy() == 1 ? 2 * call.alleles().get(0) : call.alleles().get(0) + call.alleles().get(1));
            }
        }
        return new Encoding(frame, codes, full, partial, ignored);
    }

    /** Complete accepted-row counters and ordered raw-GT matrix hashes. */
    public record Report(long rows, long phasedRows, long unphasedRows, long calls,
            long fullyObservedInputCalls, long fullyMissingInputCalls, long partiallyMissingInputCalls,
            long outputMissingCalls, long sourcePloidyConversions, long allHaploidReferenceBypassCalls,
            long missingCallsBypassedInAllHaploidReferenceRows, long inputAllelesIgnoredByInference,
            long inputObservedAllelesCleared, String inputSha256, String outputSha256) { }

    /**
     * Stateful original output contract, preserving chromosome-X ploidy history.
     * Callers supply exact complete sample identities and variant identities for
     * every expected output row. Missing/excluded source rows, reference matching,
     * file EOF and process/runtime admission remain the surrounding workflow's job.
     * Any failed row permanently invalidates this audit; it cannot be resumed.
     */
    public static final class Audit {
        private final List<String> samples;
        private final Options options;
        private final long maximumRows, maximumCalls;
        private final int[] recentPloidy;
        private final MessageDigest inputDigest = digest(), outputDigest = digest();
        private long rows, phasedRows, unphasedRows, calls, observed, fullMissing, partialMissing,
                outputMissing, conversions, allHaploidBypass, bypassedMissing, ignoredAlleles, clearedAlleles;
        private boolean failed, finished;

        /**
         * @param inputSamples complete exact source sample order
         * @param outputSamples complete exact output sample order
         * @param options explicit original mode and missing/ploidy choices
         * @param maximumRows complete accepted-row capacity
         * @param maximumCalls complete accepted genotype-call capacity
         */
        public Audit(List<String> inputSamples, List<String> outputSamples, Options options, long maximumRows, long maximumCalls) {
            if (inputSamples == null || inputSamples.isEmpty() || inputSamples.size() > 1_000_000
                    || !inputSamples.equals(outputSamples) || new HashSet<>(inputSamples).size() != inputSamples.size())
                throw new IllegalArgumentException("Complete exact unique sample identities and order must agree");
            for (String sample : inputSamples) if (sample == null || sample.isEmpty()
                    || sample.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
                throw new IllegalArgumentException("Invalid sample identity token");
            if (maximumRows < 1 || maximumCalls < inputSamples.size()) throw new IllegalArgumentException("Invalid complete genotype audit capacities");
            this.samples = List.copyOf(inputSamples); this.options = Objects.requireNonNull(options, "options");
            this.maximumRows = maximumRows; this.maximumCalls = maximumCalls;
            recentPloidy = new int[samples.size()]; Arrays.fill(recentPloidy, 2);
            for (String sample : samples) { frame(inputDigest, sample); frame(outputDigest, sample); }
        }

        /**
         * Validate one complete paired row and then advance its source history.
         * Raw GT hashes retain separators and encoded missingness. Identity fields
         * must include the caller's original contig, position, REF, ALT and ID.
         * @param inputIdentity original canonical variant identity
         * @param input complete sample-order input calls
         * @param outputIdentity corresponding output identity
         * @param output complete sample-order output calls
         * @param disposition expected original phasing/passthrough selection
         * @throws IllegalArgumentException changed identities, calls or original output contract
         * @throws IllegalStateException failed, finished or over-capacity audit
         */
        public void accept(String inputIdentity, List<Call> input, String outputIdentity, List<Call> output, Disposition disposition) {
            acceptDeclaredRepresentation(inputIdentity, input, inputIdentity, outputIdentity, output, disposition);
        }

        /**
         * Validate calls after a separately admitted, explicitly declared source
         * allele-representation change, such as original reference-mode expansion
         * of a monomorphic target. The input digest retains the original identity;
         * the output digest retains the actual changed identity. This checks GT
         * indices and source call behavior, not preservation of nucleotide alleles
         * when the declared representation changes. The file-level caller must
         * independently validate and record the exact permitted transformation.
         * @param inputIdentity complete original source identity
         * @param input complete original sample-order calls
         * @param expectedOutputIdentity complete identity from the admitted transformation plan
         * @param outputIdentity complete actual output identity
         * @param output complete actual output calls
         * @param disposition declared original phasing or passthrough selection
         */
        public void acceptDeclaredRepresentation(String inputIdentity, List<Call> input, String expectedOutputIdentity,
                String outputIdentity, List<Call> output, Disposition disposition) {
            if (failed || finished) throw new IllegalStateException("Failed or completed genotype audits cannot continue");
            try {
                if (inputIdentity == null || inputIdentity.isEmpty() || expectedOutputIdentity == null
                        || expectedOutputIdentity.isEmpty() || !expectedOutputIdentity.equals(outputIdentity))
                    throw new IllegalArgumentException("Complete variant identity/order differs");
                if (input == null || output == null || input.size() != samples.size() || output.size() != samples.size())
                    throw new IllegalArgumentException("Genotype row width differs from complete sample population");
                Objects.requireNonNull(disposition, "disposition");
                if (rows == maximumRows || calls > maximumCalls - samples.size()) throw new IllegalStateException("Complete genotype audit capacity exceeded");
                if (disposition == Disposition.UNPHASED_TARGET_ONLY && options.mode() != Mode.REFERENCE)
                    throw new IllegalArgumentException("Target-only unphased output belongs to reference mode");
                Encoding encoding = disposition == Disposition.PHASED ? encodeTargets(input, options) : null;
                boolean bypass = encoding != null && options.mode() == Mode.REFERENCE && encoding.framePloidy() == 1;
                int[] nextPloidy = recentPloidy.clone();
                for (int s = 0; s < samples.size(); s++) {
                    Call before = Objects.requireNonNull(input.get(s), "input call"), after = Objects.requireNonNull(output.get(s), "output call");
                    if (disposition == Disposition.UNPHASED_TARGET_ONLY) {
                        char expected = before.ploidy() == 2 && before.alleles().get(1) >= 0 ? '/' : before.separator;
                        same(before.alleles(), expected, after, "Original target-only unphasing differs");
                    } else if (bypass) {
                        same(before.alleles(), before.separator, after, "Original all-haploid reference row is written unchanged");
                    } else validateCall(before, after, s, nextPloidy);
                }
                frame(inputDigest, inputIdentity); frame(outputDigest, outputIdentity);
                for (int s = 0; s < samples.size(); s++) {
                    Call before = input.get(s), after = output.get(s);
                    frame(inputDigest, before.raw()); frame(outputDigest, after.raw());
                    if (!before.missing()) observed++; else if (before.partiallyMissing()) partialMissing++; else fullMissing++;
                    if (after.missing()) outputMissing++;
                    if (before.ploidy() != after.ploidy()) conversions++;
                    if (bypass) { allHaploidBypass++; if (before.missing()) bypassedMissing++; }
                    if (disposition == Disposition.PHASED && before.partiallyMissing()) ignoredAlleles += before.observedAlleles();
                    if (before.partiallyMissing() && after.observedAlleles() == 0) clearedAlleles += before.observedAlleles();
                }
                System.arraycopy(nextPloidy, 0, recentPloidy, 0, recentPloidy.length);
                rows++; calls += samples.size(); if (disposition == Disposition.PHASED) phasedRows++; else unphasedRows++;
            } catch (RuntimeException failure) { failed = true; throw failure; }
        }

        private void validateCall(Call before, Call after, int sample, int[] nextPloidy) {
            if (after.alleles().stream().anyMatch(a -> a > 1)) throw new IllegalArgumentException("Phased target output is not biallelic");
            boolean firstMissing = before.alleles().get(0) == -1;
            if (options.sexChromosome() && options.keepMissingPloidyX() && firstMissing) nextPloidy[sample] = before.ploidy();
            boolean diploid = !options.sexChromosome() || firstMissing && nextPloidy[sample] == 2 || before.ploidy() == 2;
            nextPloidy[sample] = diploid ? 2 : 1;
            if (before.missing() && options.missing() == Missing.KEEP && options.mode() == Mode.COHORT) {
                same(before.alleles(), before.separator, after, "Original cohort noImpMissing preserves the original missing/partial call"); return;
            }
            if (!diploid) {
                if (!before.missing() || options.missing() == Missing.KEEP)
                    same(before.alleles(), before.separator, after, "Original haploid call differs");
                else requireObserved(after, 1, false);
                return;
            }
            if (before.missing()) {
                if (options.missing() == Missing.KEEP) same(List.of(-1, -1), '/', after, "Original reference noImpMissing clears the entire diploid call");
                else requireObserved(after, 2, true);
            } else {
                requireObserved(after, 2, true);
                if (before.alleles().get(0) + before.alleles().get(1) != after.alleles().get(0) + after.alleles().get(1))
                    throw new IllegalArgumentException("Observed original genotype dosage changed");
            }
        }

        /**
         * Close the complete expected population and publish final ordered hashes.
         * @param expectedRows complete number of expected output rows from the admitted file/selection plan
         * @return complete genotype audit, without a phase-accuracy claim
         * @throws IllegalStateException failed, already finished or incomplete population
         */
        public Report finish(long expectedRows) {
            if (failed || finished) throw new IllegalStateException("Failed or completed genotype audits cannot finish");
            if (expectedRows != rows) { failed = true; throw new IllegalStateException("Complete expected output row population differs"); }
            finished = true;
            return new Report(rows, phasedRows, unphasedRows, calls, observed, fullMissing, partialMissing,
                    outputMissing, conversions, allHaploidBypass, bypassedMissing, ignoredAlleles, clearedAlleles,
                    HexFormat.of().formatHex(inputDigest.digest()), HexFormat.of().formatHex(outputDigest.digest()));
        }
    }

    private static void same(List<Integer> alleles, char separator, Call actual, String reason) {
        if (!alleles.equals(actual.alleles()) || separator != actual.separator) throw new IllegalArgumentException(reason);
    }
    private static void requireObserved(Call actual, int ploidy, boolean phased) {
        if (actual.ploidy() != ploidy || actual.missing() || phased && !actual.phased())
            throw new IllegalArgumentException("Original output requires the declared observed ploidy/phase");
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    private static void frame(MessageDigest digest, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
}
