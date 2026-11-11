/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete canonical-DNA site/matrix/diagram component audit for original MEME
 * classic discovery XML. The caller independently supplies the input records,
 * weights, resolved background and requested settings; producer paths in XML
 * remain inert. No EM fitting, motif E-value derivation, input-file parsing,
 * complete report-family verification or execution authentication is claimed.
 */
public final class MemeDiscoverySites {
    private MemeDiscoverySites() { }

    /** Named source behavior that needs a separately validated component. */
    public static final class UnsupportedInput extends IOException {
        private static final long serialVersionUID = 1L;
        /** @param message unsupported scope and recovery action */
        public UnsupportedInput(String message) { super(message); }
    }
    /** Original distribution of contributing sites within one sequence. */
    public enum Mode { OOPS, ZOOPS, ANR }

    /**
     * Independently prepared original record; no case/alias/weight normalization.
     * @param name original complete first FASTA name token
     * @param bases complete uppercase canonical input-oriented DNA
     * @param weight original positive finite sequence weight
     */
    public record Input(String name, String bases, double weight) {
        /** Preserves the literal record and refuses invalid scalar metadata. */
        public Input {
            Objects.requireNonNull(name); Objects.requireNonNull(bases);
            if (name.isEmpty() || name.codePoints().anyMatch(Character::isWhitespace)
                    || name.codePoints().anyMatch(Character::isISOControl) || !Double.isFinite(weight) || weight <= 0)
                throw new IllegalArgumentException("Require literal nonempty MEME input names and positive finite weights");
        }
    }

    /**
     * Independently supplied classic-DNA settings and complete original records.
     * Resolved background means after source strand handling, before output
     * rounding; no background is inferred from rounded XML.
     * @param version exactly 4.9.1, 5.5.4 or 5.5.9
     * @param mode original occurrence model
     * @param bothStrands original reverse-complement setting
     * @param requestedMotifs original requested maximum motif population
     * @param minimumWidth original minimum width
     * @param maximumWidth original maximum width
     * @param seed declared source seed; 4.9.1 has no CLI -seed mapping
     * @param beta original positive Dirichlet prior strength
     * @param background independently resolved positive normalized A/C/G/T frequencies
     * @param inputs complete original record population in input order
     */
    public record Expected(String version, Mode mode, boolean bothStrands, int requestedMotifs,
                           int minimumWidth, int maximumWidth, int seed, double beta,
                           double[] background, List<Input> inputs) {
        /** Defensively owns the complete supplied population and background. */
        public Expected {
            Objects.requireNonNull(version); Objects.requireNonNull(mode); Objects.requireNonNull(background); Objects.requireNonNull(inputs);
            if (requestedMotifs < 1 || requestedMotifs > 100_000 || minimumWidth < 1 || maximumWidth < minimumWidth
                    || maximumWidth > 512 || seed < 0 || !Double.isFinite(beta) || beta <= 0 || background.length != 4
                    || inputs.isEmpty() || inputs.size() > 1_000_000)
                throw new IllegalArgumentException("Invalid complete independently prepared MEME discovery settings");
            background = background.clone(); inputs = List.copyOf(inputs); double sum = 0;
            for (double p : background) {
                if (!Double.isFinite(p) || p < 1e-250 || p > 1) throw new IllegalArgumentException("Require supported positive resolved MEME background");
                sum += p;
            }
            if (Math.abs(sum - 1) > 1e-12) throw new IllegalArgumentException("Resolved MEME background is not normalized; no silent normalization");
        }
        /** @return defensive resolved background copy */
        @Override public double[] background() { return background.clone(); }
    }

    /**
     * Complete-population component bounds, not heap/OS quotas.
     * @param xml complete raw XML capacities
     * @param scoring per-model/per-sequence mathematical capacities
     * @param maximumSequences maximum complete input record population
     * @param maximumBases maximum total supplied bases
     * @param maximumContributingSites maximum complete contributing-site population
     * @param maximumTotalOperations cumulative geometry, matrix and scoring work
     */
    public record Limits(MemeXml.Limits xml, MemeDiscoveryScoring.Limits scoring, int maximumSequences,
                         long maximumBases, long maximumContributingSites, long maximumTotalOperations) {
        /** Refuses unsupported capacities. */
        public Limits {
            Objects.requireNonNull(xml); Objects.requireNonNull(scoring);
            if (maximumSequences < 1 || maximumSequences > 1_000_000 || maximumBases < 1 || maximumBases > 500_000_000
                    || maximumContributingSites < 1 || maximumContributingSites > 5_000_000
                    || maximumTotalOperations < 1 || maximumTotalOperations > 10_000_000_000L)
                throw new IllegalArgumentException("Invalid complete MEME discovery site audit capacities");
        }
        /** @return bounded defaults for complete canonical-DNA component audits */
        public static Limits defaults() {
            return new Limits(new MemeXml.Limits(67_108_864, 67_108_864, 2_000_000, 64, 128),
                    MemeDiscoveryScoring.Limits.defaults(), 100_000, 8_000_000, 1_000_000, 1_000_000_000);
        }
    }

    /**
     * Exact word/flanks are checked against input coordinates. The source P value
     * uses the best scoring orientation when both strands are enabled, which
     * need not be the EM-assigned contributing strand.
     * @param raw original complete contributing-site element
     * @param inputIndex zero-based original input ordinal
     * @param start zero-based inclusive coordinate on the input orientation
     * @param reverse original EM-assigned strand
     * @param word complete motif-oriented word
     * @param positionScore independently recomputed source-convention best-orientation score
     */
    public record Site(MemeXml.Node raw, int inputIndex, int start, boolean reverse, String word,
                       MemeDiscoveryScoring.Score positionScore) { }

    /**
     * One complete original motif and independently reconstructed weighted counts.
     * Motif significance attributes remain original raw values, not recalculated.
     * @param raw original complete motif element
     * @param sites all original contributing sites in original order
     * @param weightedCounts independent complete A/C/G/T weighted counts by position
     * @param totalWeight sum of original weights over contributing sites
     * @param scoreModel independent original-convention lattice and all null tails
     */
    public record Motif(MemeXml.Node raw, List<Site> sites, List<List<Double>> weightedCounts,
                        double totalWeight, MemeDiscoveryScoring.Model scoreModel) {
        /** Owns immutable collection copies. */
        public Motif { sites = List.copyOf(sites); weightedCounts = weightedCounts.stream().map(List::copyOf).toList(); }
    }

    /**
     * Complete source sequence diagram and its independent reconstruction.
     * @param raw original complete scanned_sites element
     * @param inputIndex zero-based original input ordinal
     * @param independent complete independently recomputed diagram
     */
    public record Scan(MemeXml.Node raw, int inputIndex, MemeDiscoveryScoring.Tiling independent) { }

    /**
     * Complete component receipt, not a scientific execution admission.
     * @param source original complete XML identity/tree
     * @param expected complete independent input/settings binding
     * @param motifs complete original motif population
     * @param scans complete original input-order sequence diagram population
     * @param operations cumulative counted geometry/matrix/scoring work
     * @param warnings interpretation and remaining admission gates
     */
    public record Audit(MemeXml.Document source, Expected expected, List<Motif> motifs, List<Scan> scans,
                        long operations, List<String> warnings) {
        /** Owns immutable collection copies. */
        public Audit { motifs = List.copyOf(motifs); scans = List.copyOf(scans); warnings = List.copyOf(warnings); }
    }

    /**
     * Reconstructs all contributing-site geometry, weighted matrices, prior-smoothed
     * centibit scores, position P values and complete input-order diagrams. OOPS,
     * ZOOPS and ANR occurrence constraints are checked per motif. No original
     * producer path is opened; the raw XML is checked again after computation.
     * @param path complete captured original MEME XML
     * @param expected independent original inputs, resolved background and settings
     * @param limits explicit complete-population capacities
     * @param cancellation caller cancellation check
     * @return complete component receipt
     * @throws IOException on unsupported, malformed, changed, inconsistent or excessive input
     */
    public static Audit read(Path path, Expected expected, Limits limits, Runnable cancellation) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(expected); Objects.requireNonNull(limits); Objects.requireNonNull(cancellation);
        if (!Set.of("4.9.1", "5.5.4", "5.5.9").contains(expected.version()))
            throw new UnsupportedInput("Unsupported MEME discovery edition; retain its outputs and use a separately validated edition audit");
        var budget = new Budget(limits, cancellation); budget.check();
        require(expected.inputs().size() <= limits.maximumSequences() && expected.requestedMotifs() <= limits.scoring().maximumMotifs()
                && expected.maximumWidth() <= limits.scoring().maximumWidth(), "Complete MEME input/settings population exceeds capacity");
        long bases = 0; var names = new HashSet<String>();
        for (Input input : expected.inputs()) {
            require(names.add(input.name()), "Duplicate original MEME input names require a separately tested disposition ledger");
            bases = Math.addExact(bases, input.bases().length());
            require(bases <= limits.maximumBases() && input.bases().length() <= limits.scoring().maximumSequenceLength(), "Complete MEME input base population exceeds capacity");
            require(input.bases().length() >= expected.maximumWidth(), "Short original inputs need a separate source disposition audit");
            for (int i = 0; i < input.bases().length(); i++) {
                budget.add(1);
                if ("ACGT".indexOf(input.bases().charAt(i)) < 0)
                    throw new UnsupportedInput("Aliases/ambiguities/case conversion require a separate original MEME prepared-input audit; preserve the complete original sequence and skip this component");
            }
        }
        MemeXml.Document document = MemeXml.read(path, limits.xml(), cancellation); MemeXml.Node root = document.root();
        require(root.name().equals("MEME") && root.attribute("version").equals(expected.version()), "Original MEME XML producer differs from the independently declared edition");
        MemeXml.Node training = root.one("training_set"), model = root.one("model"); boolean legacy = expected.version().equals("4.9.1");
        require(text(model.one("type")).equals(expected.mode().name().toLowerCase(java.util.Locale.ROOT)), "Original MEME occurrence model differs");
        require(text(model.one("strands")).equals(expected.bothStrands() ? "both" : "forward"), "Original MEME strand setting differs");
        require(integer(text(model.one("nmotifs"))) == expected.requestedMotifs()
                && integer(text(model.one("min_width"))) == expected.minimumWidth()
                && integer(text(model.one("max_width"))) == expected.maximumWidth()
                && integer(text(model.one("seed"))) == expected.seed(), "Original MEME requested counts/widths/seed differ");
        if (!text(model.one("prior")).equals("dirichlet") || !text(model.one("object_function")).equals("E-value of product of p-values"))
            throw new UnsupportedInput("This component supports original classic DNA discovery with a positive Dirichlet prior; retain other objectives/priors for their own source audit");
        printed(text(model.one("beta")), expected.beta(), 6);
        require(longInteger(text(model.one("num_positions"))) == bases, "Original MEME declared complete input positions differ");
        var alphabet = alphabet(training.one("alphabet"), legacy);
        var sourceSequences = training.named("sequence");
        require(sourceSequences.size() == expected.inputs().size(), "Complete MEME sequence table is missing or suppressed by brief output; no omitted records were inferred");
        for (int i = 0; i < sourceSequences.size(); i++) {
            budget.add(1); var node = sourceSequences.get(i); var input = expected.inputs().get(i);
            require(node.attribute("id").equals("sequence_" + i) && node.attribute("name").equals(input.name())
                    && integer(node.attribute("length")) == input.bases().length(), "Original MEME input-order sequence identity/length differs");
            fixed(node.attribute("weight"), input.weight(), 6);
        }
        require(integer(training.attribute(legacy ? "length" : "primary_count")) == expected.inputs().size(), "Original MEME primary sequence count differs");
        if (legacy) require(integer(text(model.one("num_sequences"))) == expected.inputs().size(), "Original legacy MEME complete sequence count differs");
        else require(longInteger(training.attribute("primary_positions")) == bases && integer(training.attribute("control_count")) == 0
                && longInteger(training.attribute("control_positions")) == 0, "Unexpected original MEME primary/control population");
        MemeXml.Node background = model.one("background_frequencies");
        if (!legacy && !background.attribute("order").equals("0")) throw new UnsupportedInput("Higher-order MEME discovery background requires its own prepared-input audit");
        var reportedBackground = matrixRow(background.one("alphabet_array"), alphabet);
        double[] bg = expected.background();
        for (int b = 0; b < 4; b++) fixed(reportedBackground.get(b), bg[b], 3);
        var motifCollection = root.one("motifs"); var all = motifCollection.named("motif");
        require(all.size() == motifCollection.children().size() && all.size() <= expected.requestedMotifs(), "Incomplete/invalid original MEME motif population");
        if (all.isEmpty()) throw new UnsupportedInput("Empty original MEME discovery populations need separately captured complete source controls");
        var motifs = new ArrayList<Motif>(); long siteCount = 0;
        for (int index = 0; index < all.size(); index++) {
            MemeXml.Node motif = all.get(index); budget.check();
            require(motif.attribute("id").equals("motif_" + (index + 1)), "Original MEME motif order/identity differs");
            int width = integer(motif.attribute("width")), declaredSites = integer(motif.attribute("sites"));
            require(width >= expected.minimumWidth() && width <= expected.maximumWidth(), "Original MEME motif width outside declared range");
            var sourceSites = motif.one("contributing_sites").named("contributing_site");
            require(sourceSites.size() == motif.one("contributing_sites").children().size() && sourceSites.size() == declaredSites && declaredSites > 0,
                    "Original MEME contributing-site population is incomplete or suppressed; no omitted sites were inferred");
            siteCount = Math.addExact(siteCount, declaredSites); require(siteCount <= limits.maximumContributingSites(), "Complete MEME contributing-site population exceeds capacity");
            double[][] counts = new double[width][4]; double totalWeight = 0; var geometries = new ArrayList<Geometry>();
            var perSequence = new HashMap<Integer, java.util.TreeMap<Integer, Integer>>();
            for (MemeXml.Node site : sourceSites) {
                budget.add(width * (expected.bothStrands() ? 4L : 3L));
                int input = sequence(site.attribute("sequence_id"), expected.inputs().size()), start = integer(site.attribute("position"));
                String strand = site.attribute("strand"); require(strand.equals("plus") || strand.equals("minus"), "Invalid original DNA contributing strand");
                boolean reverse = strand.equals("minus"); require(expected.bothStrands() || !reverse, "Reverse contributing site in forward-only MEME fit");
                Input original = expected.inputs().get(input); require(start <= original.bases().length() - width, "Original MEME contributing site outside complete input coordinates");
                var spans = perSequence.computeIfAbsent(input, k -> new java.util.TreeMap<>());
                var leftSpan = spans.floorEntry(start); var rightSpan = spans.ceilingEntry(start);
                require((leftSpan == null || leftSpan.getValue() <= start) && (rightSpan == null || start + width <= rightSpan.getKey()), "Overlapping/duplicate original MEME sites within one motif/sequence");
                require(expected.mode() == Mode.ANR || spans.isEmpty(), "Original OOPS/ZOOPS motif has multiple sites in one sequence"); spans.put(start, start + width);
                String word = original.bases().substring(start, start + width);
                String left = original.bases().substring(Math.max(0, start - 10), start);
                String right = original.bases().substring(start + width, Math.min(original.bases().length(), start + width + 10));
                if (reverse) { word = reverse(word); String old = left; left = reverse(right); right = reverse(old); }
                require(text(site.one("left_flank")).equals(left) && text(site.one("right_flank")).equals(right), "Original MEME oriented site flanks differ from complete input coordinates");
                var letters = site.one("site").children(); require(letters.size() == width, "Incomplete original MEME contributing word");
                for (int row = 0; row < width; row++) {
                    var letter = letters.get(row); require(letter.name().equals("letter_ref") && letter.children().isEmpty() && letter.text().isBlank(), "Invalid original MEME contributing letter reference");
                    Integer b = alphabet.get(letter.attribute("letter_id"));
                    require(b != null && "ACGT".charAt(b) == word.charAt(row), "Original MEME contributing word differs from complete input"); counts[row][b] += original.weight();
                }
                totalWeight += original.weight(); geometries.add(new Geometry(site, input, start, reverse, word));
            }
            require(expected.mode() != Mode.OOPS || perSequence.size() == expected.inputs().size(), "Original OOPS motif does not have exactly one site in every sequence");
            require(Double.isFinite(totalWeight) && totalWeight > 0, "Invalid original complete contributing weight");
            var probabilities = motif.one("probabilities").one("alphabet_matrix"); var scores = motif.one("scores").one("alphabet_matrix");
            require(probabilities.children().size() == width && scores.children().size() == width, "Incomplete original MEME matrix position population");
            int[][] centibits = new int[width][4]; var ownedCounts = new ArrayList<List<Double>>();
            for (int row = 0; row < width; row++) {
                List<String> p = matrixRow(probabilities.children().get(row), alphabet), s = matrixRow(scores.children().get(row), alphabet);
                var owned = new ArrayList<Double>();
                for (int b = 0; b < 4; b++) {
                    budget.add(4); BigDecimal originalProbability = number(p.get(b));
                    require(originalProbability.signum() >= 0 && originalProbability.compareTo(BigDecimal.ONE) <= 0, "Original MEME matrix probability outside [0,1]");
                    fixed(p.get(b), counts[row][b] / totalWeight, 6);
                    double theta = (counts[row][b] + expected.beta() * bg[b]) / (totalWeight + expected.beta());
                    double value = 100 * Math.log(theta / bg[b]) / Math.log(2);
                    require(Double.isFinite(value) && Math.abs(value) < 1_000_000, "Unsupported reconstructed MEME prior score magnitude");
                    int expectedScore = (int) (value >= 0 ? Math.floor(value + .5) : Math.ceil(value - .5));
                    centibits[row][b] = signedInteger(s.get(b)); require(centibits[row][b] == expectedScore, "Original MEME centibit score differs from independent weighted counts, prior and resolved background");
                    owned.add(counts[row][b]);
                }
                ownedCounts.add(owned);
            }
            MemeDiscoveryScoring.Model scoreModel;
            try { scoreModel = MemeDiscoveryScoring.build(centibits, bg, budget.scoring(), cancellation); }
            catch (IllegalArgumentException | ArithmeticException failure) { throw new IOException("Complete MEME source-convention score model refused: " + failure.getMessage(), failure); }
            budget.add(scoreModel.buildOperations()); var sites = new ArrayList<Site>();
            for (Geometry site : geometries) {
                Input input = expected.inputs().get(site.input()); String forwardWord = input.bases().substring(site.start(), site.start() + width);
                var score = scoreModel.score(forwardWord, expected.bothStrands(), cancellation); probability(site.raw().attribute("pvalue"), score.pValue());
                sites.add(new Site(site.raw(), site.input(), site.start(), site.reverse(), site.word(), score));
            }
            motifs.add(new Motif(motif, sites, ownedCounts, totalWeight, scoreModel));
        }
        var summary = root.one("scanned_sites_summary"); require(number(summary.attribute("p_thresh")).compareTo(new BigDecimal("0.0001")) == 0, "Original MEME diagram position threshold differs");
        var sourceScans = summary.named("scanned_sites");
        require(sourceScans.size() == summary.children().size() && sourceScans.size() == expected.inputs().size(), "Complete original MEME input-order diagram population is missing or suppressed");
        var scans = new ArrayList<Scan>(); var models = motifs.stream().map(Motif::scoreModel).toList();
        for (int index = 0; index < sourceScans.size(); index++) {
            var node = sourceScans.get(index); require(sequence(node.attribute("sequence_id"), expected.inputs().size()) == index, "Original MEME diagram sequence order differs");
            MemeDiscoveryScoring.Tiling independent;
            try { independent = MemeDiscoveryScoring.tile(expected.inputs().get(index).bases(), models, expected.bothStrands(), budget.scoring(), cancellation); }
            catch (IllegalArgumentException | ArithmeticException failure) { throw new IOException("Complete original-convention MEME diagram refused: " + failure.getMessage(), failure); }
            budget.add(independent.operations()); probability(node.attribute("pvalue"), independent.combinedPValue());
            require(integer(node.attribute("num_sites")) == independent.hits().size() && node.children().size() == independent.hits().size(), "Original MEME diagram is missing or adds independently selected hits");
            for (int i = 0; i < independent.hits().size(); i++) {
                var hit = node.children().get(i); var want = independent.hits().get(i);
                require(hit.name().equals("scanned_site") && hit.children().isEmpty() && hit.text().isBlank()
                        && hit.attribute("motif_id").equals("motif_" + (want.motifIndex() + 1)) && integer(hit.attribute("position")) == want.start()
                        && hit.attribute("strand").equals(want.reverse() ? "minus" : "plus"), "Original MEME complete diagram hit identity/coordinate/strand differs");
                probability(hit.attribute("pvalue"), want.pValue());
            }
            scans.add(new Scan(node, index, independent));
        }
        require(document.sha256().equals(MemeXml.read(path, limits.xml(), cancellation).sha256()), "Complete original MEME XML changed during site/matrix/score audit");
        var warnings = new ArrayList<String>();
        warnings.add("COMPONENT_ONLY: complete canonical-DNA contributing geometry, weighted matrices, prior scores, position tails and sequence diagrams were independently checked. EM fitting, motif significance and complete report/runtime/process gates remain separate.");
        warnings.add("SOURCE_SCORE_CONVENTIONS: discovery uses a 100-point lattice, independently rounded unrenormalized background, best orientation for contributing-site P values, strict diagram thresholds and ordered nonoverlap replacement. Source binary64 approximations/underflow remain unchanged; these are not FIMO scan semantics.");
        if (expected.version().equals("4.9.1")) warnings.add("LEGACY_EDITION: 4.9.1 patch level is not established by its XML version; the original MEME command has no -seed option. The supplied declared seed is not a newer-edition command mapping.");
        if (scans.stream().anyMatch(s -> s.independent().productUnderflow())) warnings.add("SOURCE_PRODUCT_UNDERFLOW: positive sequence factors multiplied to binary64 zero; the original zero was retained rather than replaced with inferred log-space inference.");
        warnings.add("REQUIRED ACTION: bind independently captured complete input files/settings and the exact source-built runtime, supervised process and complete requested report family before admitting discovery. Missing or forbidden dependencies require SKIP3 and skipping affected analysis.");
        return new Audit(document, expected, motifs, scans, budget.operations, warnings);
    }

    private record Geometry(MemeXml.Node raw, int input, int start, boolean reverse, String word) { }
    private static Map<String, Integer> alphabet(MemeXml.Node node, boolean legacy) throws IOException {
        var result = new HashMap<String, Integer>();
        for (int i = 0; i < 4; i++) {
            String symbol = "ACGT".substring(i, i + 1), id = legacy ? "letter_" + symbol : symbol;
            var matching = node.named("letter").stream().filter(x -> x.attributes().getOrDefault("id", "").equals(id)).toList();
            require(matching.size() == 1 && matching.get(0).attribute("symbol").equals(symbol), "Unsupported original MEME DNA alphabet/order"); result.put(id, i);
        }
        if (legacy) require(node.attribute("id").equals("nucleotide") && node.attribute("length").equals("4"), "Require original legacy DNA alphabet");
        else require(node.attribute("like").equals("dna"), "Require original MEME DNA alphabet");
        return Map.copyOf(result);
    }
    private static List<String> matrixRow(MemeXml.Node row, Map<String, Integer> alphabet) throws IOException {
        require(row.name().equals("alphabet_array") && row.children().size() == 4, "Incomplete original MEME DNA matrix row");
        var result = new ArrayList<String>();
        for (int b = 0; b < 4; b++) {
            var value = row.children().get(b);
            require(value.name().equals("value") && value.children().isEmpty() && Objects.equals(alphabet.get(value.attribute("letter_id")), b), "Original MEME DNA matrix base/order differs");
            result.add(text(value));
        }
        return List.copyOf(result);
    }
    private static String reverse(String value) {
        var result = new StringBuilder(value.length());
        for (int i = value.length() - 1; i >= 0; i--) result.append("TGCA".charAt("ACGT".indexOf(value.charAt(i))));
        return result.toString();
    }
    private static int sequence(String token, int count) throws IOException {
        require(token.startsWith("sequence_"), "Invalid original MEME sequence reference");
        int ordinal = integer(token.substring(9)); require(ordinal < count, "Unknown original MEME sequence reference"); return ordinal;
    }
    private static long longInteger(String token) throws IOException {
        require(token.matches("0|[1-9][0-9]{0,17}"), "Invalid bounded original MEME integer");
        try { return Long.parseLong(token); } catch (NumberFormatException failure) { throw new IOException("Invalid original MEME integer", failure); }
    }
    private static int integer(String token) throws IOException {
        long n = longInteger(token); require(n <= Integer.MAX_VALUE, "Original MEME integer exceeds supported range"); return (int) n;
    }
    private static int signedInteger(String token) throws IOException {
        require(token.matches("-?(?:0|[1-9][0-9]{0,8})"), "Invalid original MEME integer score"); return Integer.parseInt(token);
    }
    private static BigDecimal number(String token) throws IOException {
        require(token.length() <= 128 && token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,4})?"), "Invalid original MEME numeric token");
        try {
            BigDecimal value = new BigDecimal(token); require(Math.abs((long) value.scale()) <= 4096, "Original MEME numeric scale exceeds supported domain"); return value;
        } catch (NumberFormatException failure) { throw new IOException("Invalid original MEME numeric token", failure); }
    }
    private static void fixed(String token, double expected, int places) throws IOException {
        require(Double.isFinite(expected), "Nonfinite independent MEME matrix/weight/background value");
        BigDecimal radius = BigDecimal.ONE.scaleByPowerOfTen(-places).divide(BigDecimal.valueOf(2));
        radius = radius.add(BigDecimal.valueOf(Math.ulp(expected) * 8));
        require(number(token).subtract(BigDecimal.valueOf(expected)).abs().compareTo(radius) <= 0, "Original MEME fixed-precision value differs from independent input/counts");
    }
    private static void printed(String token, double expected, int digits) throws IOException {
        require(Double.isFinite(expected), "Nonfinite independent MEME printed-precision value");
        BigDecimal point = number(token), value = BigDecimal.valueOf(expected);
        require(point.signum() >= 0, "Negative original MEME probability/positive scalar");
        BigDecimal quantum = point.signum() == 0 ? new BigDecimal("1e-323") : BigDecimal.ONE.scaleByPowerOfTen(point.precision() - point.scale() - digits);
        BigDecimal radius = quantum.divide(BigDecimal.valueOf(2)).add(point.abs().multiply(new BigDecimal("1e-10")));
        require(point.subtract(value).abs().compareTo(radius) <= 0, "Original MEME probability/scalar differs from independent source-convention mathematics at printed precision");
    }
    private static void probability(String token, double expected) throws IOException {
        BigDecimal point = number(token); require(point.signum() >= 0 && point.compareTo(BigDecimal.ONE) <= 0, "Original MEME probability outside [0,1]");
        printed(token, expected, 3);
    }
    private static String text(MemeXml.Node node) throws IOException {
        require(node.children().isEmpty(), "Unexpected structured original MEME scalar"); return node.text().strip();
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
    private static final class Budget {
        final Limits limits; final Runnable cancellation; long operations;
        Budget(Limits limits, Runnable cancellation) { this.limits = limits; this.cancellation = cancellation; }
        void check() { cancellation.run(); }
        void add(long amount) throws IOException {
            require(amount <= limits.maximumTotalOperations() - operations, "Complete MEME site/matrix/score family exceeds cumulative operation capacity");
            long before = operations; operations += amount; if ((before >>> 10) != (operations >>> 10)) check();
        }
        MemeDiscoveryScoring.Limits scoring() throws IOException {
            long remaining = limits.maximumTotalOperations() - operations; require(remaining > 0, "Complete MEME scoring family exceeds cumulative work capacity");
            var s = limits.scoring(); return new MemeDiscoveryScoring.Limits(s.maximumWorkspaceBytes(), Math.min(s.maximumOperations(), remaining),
                    s.maximumWidth(), s.maximumMotifs(), s.maximumSequenceLength(), s.maximumSelectedHits());
        }
    }
}
