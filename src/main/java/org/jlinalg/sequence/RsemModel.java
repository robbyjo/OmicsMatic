/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete readers for original RSEM types 0/1/2/3 and both theta vectors.
 * Every emitted scalar is consumed and retains its original precision. Zero
 * unused probability rows are preserved; they are never replaced by a uniform
 * model. This is model-file admission, not an EM/Gibbs fitter or convergence test.
 */
public final class RsemModel {
    private RsemModel() { }

    /**
     * Whole-model capacities before dimension-driven allocation.
     * @param maxTranscripts maximum reference population
     * @param maxLengthSpan maximum fragment/read-length probability population
     * @param maxStartBins maximum estimated start-position bins
     * @param maxReadLength maximum quality-free error-profile length
     * @param maxScalars maximum complete numeric scalar population
     * @param maxBytes per-file byte capacity
     * @param maxLineChars per-line character capacity
     */
    public record Limits(int maxTranscripts, int maxLengthSpan, int maxStartBins, int maxReadLength,
                         long maxScalars, long maxBytes, int maxLineChars) {
        /**
         * Requires positive model capacities.
         * @param maxTranscripts reference count capacity
         * @param maxLengthSpan length support capacity
         * @param maxStartBins start-bin capacity
         * @param maxReadLength error-profile capacity
         * @param maxScalars complete scalar capacity
         * @param maxBytes source byte capacity
         * @param maxLineChars line capacity
         */
        public Limits {
            if (maxTranscripts < 1 || maxLengthSpan < 1 || maxStartBins < 1 || maxReadLength < 1
                    || maxScalars < 1 || maxBytes < 1 || maxLineChars < 1)
                throw new IllegalArgumentException("Positive complete-model capacities are required");
        }
    }

    /**
     * Original exclusive lower and inclusive upper support, without trimming zeros.
     * @param lowerExclusive original excluded lower length
     * @param upperInclusive original included upper length
     * @param probabilities every original %.10g probability in order
     */
    public record LengthDistribution(int lowerExclusive, int upperInclusive, List<RsemPrintedNumber> probabilities) {
        /**
         * Copies the probability population.
         * @param lowerExclusive lower excluded length
         * @param upperInclusive upper included length
         * @param probabilities complete original probability mass
         */
        public LengthDistribution {probabilities = List.copyOf(probabilities);}
    }

    /**
     * Every stored field of an original ordinary model.
     * @param type zero/single/no-quality, one/single/quality, two/paired/no-quality, three/paired/quality
     * @param forwardProbability original orientation probability
     * @param fragmentLengths first original length distribution
     * @param readLengths optional single-end or required paired-end second distribution, null when omitted upstream
     * @param estimateStartDistribution original RSPD estimation flag
     * @param startProbabilities estimated bins, empty when the original stores only a uniform flag
     * @param qualityInitial original quality initial probabilities, empty for quality-free models
     * @param qualityTransitions original flattened 100 by 100 matrix, empty for quality-free models
     * @param errorProfileLength 100 quality states or original quality-free position count
     * @param errorProbabilities flattened profile-length by five by five matrix
     * @param noiseProbabilities flattened 100 by five quality matrix or five base probabilities
     * @param mappability original optional noise-plus-transcript weights, empty if absent
     * @param referenceTranscripts externally required reference population, checked against the stored tail when present
     * @param unusedProbabilityRows exact all-zero original probability vectors
     * @param scalarCount complete parsed scalar count
     * @param evidence complete source bytes
     */
    public record Model(int type, RsemPrintedNumber forwardProbability, LengthDistribution fragmentLengths,
                        LengthDistribution readLengths, boolean estimateStartDistribution,
                        List<RsemPrintedNumber> startProbabilities, List<RsemPrintedNumber> qualityInitial,
                        List<RsemPrintedNumber> qualityTransitions, int errorProfileLength,
                        List<RsemPrintedNumber> errorProbabilities, List<RsemPrintedNumber> noiseProbabilities,
                        List<RsemPrintedNumber> mappability, int referenceTranscripts,
                        int unusedProbabilityRows, long scalarCount, RsemEvidence evidence) {
        /**
         * Copies all stored parameter populations.
         * @param type original model type
         * @param forwardProbability orientation
         * @param fragmentLengths first length model
         * @param readLengths optional second length model
         * @param estimateStartDistribution RSPD flag
         * @param startProbabilities estimated start bins
         * @param qualityInitial quality initial states
         * @param qualityTransitions quality transition states
         * @param errorProfileLength original error-profile length
         * @param errorProbabilities complete error matrices
         * @param noiseProbabilities complete noise parameters
         * @param mappability optional complete reference weights
         * @param referenceTranscripts required reference population
         * @param unusedProbabilityRows original all-zero vectors
         * @param scalarCount complete parsed scalar count
         * @param evidence source evidence
         */
        public Model {
            startProbabilities = List.copyOf(startProbabilities); qualityInitial = List.copyOf(qualityInitial);
            qualityTransitions = List.copyOf(qualityTransitions); errorProbabilities = List.copyOf(errorProbabilities);
            noiseProbabilities = List.copyOf(noiseProbabilities); mappability = List.copyOf(mappability);
        }
    }

    /**
     * Both complete original %.15g theta populations.
     * @param unpolished original unpolished vector including noise at index zero
     * @param polished original corrected vector including noise at index zero
     * @param evidence complete source bytes
     */
    public record Theta(List<RsemPrintedNumber> unpolished, List<RsemPrintedNumber> polished, RsemEvidence evidence) {
        /**
         * Copies both original vectors.
         * @param unpolished original pre-correction values
         * @param polished corrected values
         * @param evidence complete source evidence
         */
        public Theta {unpolished = List.copyOf(unpolished); polished = List.copyOf(polished);}
    }

    /**
     * Original learned or verified no-alignable-read state. The latter stores
     * no learned model: original 1.3.2/1.3.3 intentionally write empty files.
     * @param learned true only when a complete original model and theta were read
     * @param model complete model, null for verified no-alignment output
     * @param theta both saved vectors, null for verified no-alignment output
     * @param evidence both complete model/theta files, including zero-byte files
     */
    public record FitState(boolean learned, Model model, Theta theta, List<RsemEvidence> evidence) {
        /**
         * Copies source evidence.
         * @param learned whether original fitting produced model parameters
         * @param model original learned model or null
         * @param theta original saved vectors or null
         * @param evidence complete source bytes
         */
        public FitState {evidence = List.copyOf(evidence);}
    }

    /**
     * Binds complete .cnt accounting to the saved model state. A no-alignment
     * result in 1.3.2/1.3.3 requires two genuinely empty files; a normalized
     * noise-only model is never invented. Earlier releases cannot complete that
     * original path. Empty files are rejected when alignable groups exist.
     * @param modelPath original model path
     * @param thetaPath original theta path
     * @param counts complete independently checked original alignment counts
     * @param expectedTranscripts complete reference population
     * @param release exact original release
     * @param limits complete source capacities
     * @return learned state or explicit absence of learned parameters
     * @throws IOException inconsistent, unsupported, incomplete or changed files
     */
    public static FitState readFitState(Path modelPath, Path thetaPath, RsemCounts.Counts counts,
                                        int expectedTranscripts, RsemAbundance.Release release, Limits limits) throws IOException {
        java.util.Objects.requireNonNull(counts, "counts"); java.util.Objects.requireNonNull(release, "release");
        if (counts.alignableGroups() > 0) {
            Model model = read(modelPath, expectedTranscripts, true, limits);
            if (model.type() != counts.readType()) throw new IOException("Saved model type differs from the original .cnt read type");
            Theta theta = readTheta(thetaPath, expectedTranscripts, limits);
            return new FitState(true, model, theta, List.of(model.evidence(), theta.evidence()));
        }
        if (release != RsemAbundance.Release.V1_3_2 && release != RsemAbundance.Release.V1_3_3)
            throw new IOException("Original RSEM through 1.3.1 cannot complete the no-alignable-read result path");
        List<RsemEvidence> evidence = new ArrayList<>();
        for (Path path : List.of(modelPath, thetaPath)) try (RsemText input = new RsemText(path, limits.maxBytes(), limits.maxLineChars())) {
            input.endTokens(); RsemEvidence item = input.evidence();
            if (item.bytes() != 0) throw input.error("Original no-alignment model/theta files must be empty, without invented parameters");
            evidence.add(item);
        }
        return new FitState(false, null, null, evidence);
    }

    /**
     * Consumes every original model field and validates dimensions and displayed
     * probability families. A present tail must match the exact reference count.
     * @param path original model source
     * @param expectedTranscripts complete reference count
     * @param requireMappability require the original completed-fit weight tail
     * @param limits complete model capacities
     * @return original model with all scalars and source precision retained
     * @throws IOException malformed, incomplete, inconsistent, changed or over-capacity source
     */
    public static Model read(Path path, int expectedTranscripts, boolean requireMappability, Limits limits) throws IOException {
        if (expectedTranscripts < 1 || expectedTranscripts >= Integer.MAX_VALUE || expectedTranscripts > limits.maxTranscripts()) throw new IllegalArgumentException("Reference count exceeds model capacity");
        try (RsemText input = new RsemText(path, limits.maxBytes(), limits.maxLineChars())) {
            Scalars scalars = new Scalars(input, limits.maxScalars());
            int type = input.integer("model type", 0, 3); boolean quality = (type & 1) == 1;
            RsemPrintedNumber orientation = scalars.probability(RsemPrintedNumber.Format.GENERAL_10, "orientation");
            LengthDistribution fragments = length(input, scalars, limits, "fragment/read length");
            LengthDistribution reads = null;
            if (type >= 2 || input.integer("optional read-length flag", 0, 1) == 1) reads = length(input, scalars, limits, "read length");
            boolean estimated = input.integer("estimated RSPD flag", 0, 1) == 1;
            List<RsemPrintedNumber> start = List.of();
            if (estimated) {int n = input.integer("RSPD bins", 1, limits.maxStartBins()); start = scalars.vector(n, RsemPrintedNumber.Format.GENERAL_10, "RSPD"); scalars.row(start, 0, n, true, "RSPD");}
            List<RsemPrintedNumber> initial = List.of(), transitions = List.of();
            if (quality) {
                if (input.integer("quality states", 100, 100) != 100) throw input.error("Original quality dimension differs");
                initial = scalars.vector(100, RsemPrintedNumber.Format.GENERAL_10, "quality initial"); scalars.row(initial, 0, 100, true, "quality initial");
                transitions = scalars.vector(10000, RsemPrintedNumber.Format.GENERAL_10, "quality transitions");
                for (int i = 0; i < 100; i++) scalars.row(transitions, i * 100, 100, true, "quality transition");
            }
            int profile = input.integer("error-profile dimension", quality ? 100 : 1, quality ? 100 : limits.maxReadLength());
            input.integer("error-profile base codes", 5, 5);
            long errorCount = (long) profile * 25;
            if (errorCount > Integer.MAX_VALUE) throw input.error("Error-profile scalar population exceeds Java indexing");
            List<RsemPrintedNumber> errors = scalars.vector((int) errorCount, RsemPrintedNumber.Format.GENERAL_10, "error profile");
            for (int i = 0; i < profile * 5; i++) scalars.row(errors, i * 5, 5, true, "error profile base row");
            if (quality) input.integer("noise quality dimension", 100, 100);
            input.integer("noise base codes", 5, 5);
            int noiseCount = quality ? 500 : 5;
            List<RsemPrintedNumber> noise = scalars.vector(noiseCount, RsemPrintedNumber.Format.GENERAL_10, "noise profile");
            for (int i = 0; i < noiseCount / 5; i++) scalars.row(noise, i * 5, 5, true, "noise base row");
            String tail = input.optionalToken(); List<RsemPrintedNumber> mw = List.of();
            if (tail != null) {
                input.integer(tail, "mappability transcript count", expectedTranscripts, expectedTranscripts);
                mw = scalars.vector(expectedTranscripts + 1, RsemPrintedNumber.Format.GENERAL_15, "mappability");
                if (mw.get(0).value() != 1) throw input.error("Original noise mappability must be one");
            } else if (requireMappability) throw input.error("Completed-fit mappability tail is absent");
            input.endTokens();
            return new Model(type, orientation, fragments, reads, estimated, start, initial, transitions, profile, errors, noise, mw,
                    expectedTranscripts, scalars.unusedRows, scalars.count, input.evidence());
        }
    }

    /**
     * Consumes and checks both saved theta vectors, including noise and exact dimensions.
     * @param path original theta source
     * @param expectedTranscripts complete reference count
     * @param limits explicit source capacities
     * @return both complete vectors with their original presentation bounds
     * @throws IOException malformed, incomplete, inconsistent or over-capacity theta
     */
    public static Theta readTheta(Path path, int expectedTranscripts, Limits limits) throws IOException {
        if (expectedTranscripts < 1 || expectedTranscripts >= Integer.MAX_VALUE || expectedTranscripts > limits.maxTranscripts())
            throw new IllegalArgumentException("Invalid complete theta population");
        try (RsemText input = new RsemText(path, limits.maxBytes(), limits.maxLineChars())) {
            int n = input.integer("theta population including noise", expectedTranscripts + 1, expectedTranscripts + 1);
            Scalars scalars = new Scalars(input, limits.maxScalars());
            List<RsemPrintedNumber> before = scalars.vector(n, RsemPrintedNumber.Format.GENERAL_15, "unpolished theta");
            scalars.row(before, 0, n, false, "unpolished theta");
            List<RsemPrintedNumber> after = scalars.vector(n, RsemPrintedNumber.Format.GENERAL_15, "polished theta");
            scalars.row(after, 0, n, false, "polished theta"); input.endTokens();
            return new Theta(before, after, input.evidence());
        }
    }

    private static LengthDistribution length(RsemText input, Scalars scalars, Limits limits, String role) throws IOException {
        int lower = input.integer(role + " lower excluded bound", 0, Integer.MAX_VALUE - 1);
        int upper = input.integer(role + " upper included bound", lower + 1, Integer.MAX_VALUE);
        int span = input.integer(role + " span", 1, limits.maxLengthSpan());
        if ((long) upper - lower != span) throw input.error("Length bounds differ from original span");
        List<RsemPrintedNumber> probabilities = scalars.vector(span, RsemPrintedNumber.Format.GENERAL_10, role);
        scalars.row(probabilities, 0, span, true, role);
        return new LengthDistribution(lower, upper, probabilities);
    }

    private static final class Scalars {
        private final RsemText input;
        private final long maximum;
        private long count;
        private int unusedRows;
        Scalars(RsemText input, long maximum) {this.input = input; this.maximum = maximum;}
        RsemPrintedNumber probability(RsemPrintedNumber.Format format, String role) throws IOException {
            if (count >= maximum) throw input.error("Complete model exceeds scalar capacity");
            count++;
            try {
                RsemPrintedNumber value = RsemPrintedNumber.parse(input.token(role), format);
                if (value.lower() > 1) throw input.error("Probability exceeds one: " + role);
                return value;
            } catch (IllegalArgumentException e) {throw input.error("Invalid probability in " + role + ": " + e.getMessage());}
        }
        List<RsemPrintedNumber> vector(int size, RsemPrintedNumber.Format format, String role) throws IOException {
            if (size < 1 || size > maximum - count) throw input.error("Declared model vector exceeds complete scalar capacity: " + role);
            List<RsemPrintedNumber> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) result.add(probability(format, role));
            return result;
        }
        void row(List<RsemPrintedNumber> values, int offset, int size, boolean allowUnused, String role) throws IOException {
            boolean zero = true; double lower = 0, upper = 0;
            for (int i = offset; i < offset + size; i++) {
                RsemPrintedNumber value = values.get(i); zero &= value.value() == 0;
                lower = Math.max(0, Math.nextDown(lower + value.lower())); upper = Math.nextUp(upper + value.upper());
            }
            if (zero && allowUnused) {unusedRows++; return;}
            if (lower > 1 || upper < 1) throw input.error("Original probability row cannot sum to one within its stored precision: " + role);
        }
    }
}
