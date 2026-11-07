/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Independent statistical component audit of original STREME 5.5.4/5.5.9 XML.
 * Every reported motif's training and held-out Fisher fields and final-family
 * E-value multiplier are checked at the original printed precision. The complete
 * raw XML is retained, but motif matrices, site scores, threshold optimization,
 * sequence partitions, output-family completeness and execution provenance are
 * separate gates. This component alone never admits a discovery analysis.
 */
public final class StremeFisherAudit {
    private StremeFisherAudit() { }

    /** Named unsupported edition/objective; callers should skip this audit, not substitute Fisher. */
    public static final class UnsupportedInput extends IOException {
        private static final long serialVersionUID = 1L;
        /** @param message original unsupported scope and required action */
        public UnsupportedInput(String message) { super(message); }
    }

    /**
     * Explicit complete-document, per-table and cumulative arithmetic bounds.
     * @param xml complete raw XML capacities
     * @param fisher per-table exact-tail capacities
     * @param maximumMotifs maximum complete reported motif population
     * @param maximumTotalOperations cumulative integer operations across every table
     */
    public record Limits(MemeXml.Limits xml, StremeEnrichment.Limits fisher,
                         int maximumMotifs, long maximumTotalOperations) {
        /** Rejects unsupported capacities. */
        public Limits {
            Objects.requireNonNull(xml); Objects.requireNonNull(fisher);
            if (maximumMotifs < 1 || maximumMotifs > 100_000 || maximumTotalOperations < 1
                    || maximumTotalOperations > 100_000_000)
                throw new IllegalArgumentException("Invalid complete STREME Fisher audit capacities");
        }
        /** @return bounded complete-family component defaults */
        public static Limits defaults() {
            return new Limits(new MemeXml.Limits(16_777_216, 16_777_216, 500_000, 64, 128),
                              StremeEnrichment.Limits.defaults(), 1000, 20_000_000);
        }
    }

    /**
     * One original motif and its independently recalculated tables. All original
     * scalar tokens remain in raw; heldOut is a sentinel table without a holdout.
     * @param raw original complete motif element, including matrices and source metadata
     * @param training independent exact training table (a selection score)
     * @param heldOut independent exact held-out table, or the source empty-table sentinel
     */
    public record Motif(MemeXml.Node raw, StremeEnrichment.FisherTail training,
                        StremeEnrichment.FisherTail heldOut) { }

    /**
     * Statistical component receipt, not a motif-discovery admission certificate.
     * @param source complete original XML identity/tree
     * @param motifs every original motif in file order
     * @param hasHoldout whether the source declares nonempty held-out populations
     * @param operations cumulative exact integer arithmetic operations
     * @param warnings source interpretation and remaining gates
     */
    public record Audit(MemeXml.Document source, List<Motif> motifs, boolean hasHoldout,
                        long operations, List<String> warnings) {
        /** Makes immutable collection copies. */
        public Audit { motifs = List.copyOf(motifs); warnings = List.copyOf(warnings); }
    }

    /**
     * Audits all Fisher fields and the actual reported-family E-value multiplier.
     * Producer paths stay inert strings. The original XML is checked again after
     * arithmetic; changed, truncated, unsupported or excessive inputs are refused.
     * @param path complete original captured XML
     * @param expectedVersion caller-declared exact supported producer version
     * @param limits explicit complete-population capacities
     * @param cancellation caller cancellation callback
     * @return statistical component receipt with prominent interpretation warnings
     * @throws IOException on inconsistent, unsupported, changed or malformed input
     */
    public static Audit read(Path path, String expectedVersion, Limits limits, Runnable cancellation) throws IOException {
        Objects.requireNonNull(path); Objects.requireNonNull(limits); Objects.requireNonNull(cancellation);
        if (!Set.of("5.5.4", "5.5.9").contains(Objects.requireNonNull(expectedVersion)))
            throw new UnsupportedInput("Unsupported STREME edition; retain its source and use a separately validated edition audit");
        MemeXml.Document document = MemeXml.read(path, limits.xml(), cancellation);
        MemeXml.Node root = document.root();
        require(root.name().equals("STREME") && root.attribute("version").equals(expectedVersion),
                "Require the explicitly declared original STREME XML producer version");
        MemeXml.Node model = root.one("model");
        if (!text(model.one("objfun")).equals("Differential Enrichment")
                || !text(model.one("test")).equals("Fisher Exact Test"))
            throw new UnsupportedInput("This is not a STREME differential Fisher test. Binomial and central-distance results require their own audits; skip this Fisher audit");
        int trainPos = count(model.one("train_positives").attribute("count"));
        int trainNeg = count(model.one("train_negatives").attribute("count"));
        int testPos = count(model.one("test_positives").attribute("count"));
        int testNeg = count(model.one("test_negatives").attribute("count"));
        require((testPos == 0) == (testNeg == 0), "Unpaired STREME Fisher holdout populations");
        boolean holdout = testPos > 0;
        MemeXml.Node collection = root.one("motifs"); List<MemeXml.Node> all = collection.named("motif");
        require(all.size() == collection.children().size() && all.size() <= limits.maximumMotifs(),
                "Complete STREME motif population contains unsupported nodes or exceeds capacity");
        var ids = new HashSet<String>(); var motifs = new ArrayList<Motif>(); long operations = 0;
        for (MemeXml.Node motif : all) {
            cancellation.run();
            require(!motif.attribute("id").isBlank() && ids.add(motif.attribute("id")), "Duplicate/empty original STREME motif identity");
            StremeEnrichment.FisherTail training = tail(motif, "train", trainPos, trainNeg, limits, operations, cancellation);
            operations = Math.addExact(operations, training.operations());
            StremeEnrichment.FisherTail test = tail(motif, "test", testPos, testNeg, limits, operations, cancellation);
            operations = Math.addExact(operations, test.operations());
            probability(motif.attribute("train_pvalue"), rational(training), false);
            logarithm(motif.attribute("train_log_pvalue"), training.log10Probability());
            probability(motif.attribute("test_pvalue"), rational(test), false);
            logarithm(motif.attribute("test_log_pvalue"), test.log10Probability());
            probability(motif.attribute("test_evalue"), rational(test).multiply(BigDecimal.valueOf(all.size())), true);
            logarithm(motif.attribute("test_log_evalue"), test.log10Probability() + Math.log10(all.size()));
            motifs.add(new Motif(motif, training, test));
        }
        require(document.sha256().equals(MemeXml.read(path, limits.xml(), cancellation).sha256()),
                "Original STREME XML changed during independent statistical audit");
        var warnings = new ArrayList<String>();
        warnings.add("STATISTICAL_COMPONENT_ONLY: exact fixed-margin inclusive right tails and reported-family E-value multipliers were checked. Source printed rounding is retained; no native STREME discovery fit or source floating-point identity is claimed.");
        warnings.add(holdout
                ? "HOLDOUT_DECLARED: training probabilities are selection scores. The XML's held-out fields were checked arithmetically; independence and the original sequence partition are not established by this audit."
                : "NO_HOLDOUT: training values are selection scores, not validated significance. Empty test-table P=1 and E=reported motif count are source sentinels; do not interpret them as held-out inference.");
        warnings.add("REQUIRED ACTION: before admitting a discovery analysis, separately verify complete captured inputs, sequence partitions, motif/site/score contracts, original runtime, supervised process, requested settings and the complete output family. Missing or forbidden dependencies require SKIP3 and skipping the affected analysis.");
        return new Audit(document, motifs, holdout, operations, warnings);
    }

    private static StremeEnrichment.FisherTail tail(MemeXml.Node motif, String prefix, int positives, int negatives,
            Limits limits, long used, Runnable check) throws IOException {
        long remaining = limits.maximumTotalOperations() - used;
        require(remaining > 0, "Complete STREME Fisher family exceeds cumulative operation capacity");
        var perTable = limits.fisher();
        var bounded = new StremeEnrichment.Limits(perTable.maximumPopulation(), perTable.maximumTailTerms(),
                perTable.maximumIntegerBits(), Math.min(remaining, perTable.maximumOperations()));
        try {
            return StremeEnrichment.fisher(new StremeEnrichment.Table(positives, negatives,
                    count(motif.attribute(prefix + "_pos_count")), count(motif.attribute(prefix + "_neg_count"))), bounded, check);
        } catch (IllegalArgumentException | ArithmeticException failure) {
            throw new IOException("Complete STREME " + prefix + " Fisher table refused: " + failure.getMessage(), failure);
        }
    }

    private static BigDecimal rational(StremeEnrichment.FisherTail value) {
        return new BigDecimal(value.numerator()).divide(new BigDecimal(value.denominator()), MathContext.DECIMAL128);
    }

    private static BigDecimal number(String token) throws IOException {
        require(token.length() <= 128 && token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,7})?"),
                "Invalid original STREME numeric token");
        try { return new BigDecimal(token); }
        catch (NumberFormatException failure) { throw new IOException("Invalid original STREME numeric token", failure); }
    }

    private static void probability(String token, BigDecimal expected, boolean evalue) throws IOException {
        BigDecimal reported = number(token);
        require(reported.signum() > 0 && (evalue || reported.compareTo(BigDecimal.ONE) <= 0),
                "Original STREME probability/E-value is outside its supported range");
        // %3.1fe%+04.0f has two significant digits. STREME also rounds its
        // natural log to ten significant digits before formatting the fields.
        BigDecimal radius = quantum(expected, 2).divide(BigDecimal.valueOf(2))
                .add(expected.abs().multiply(new BigDecimal("1e-8")));
        require(reported.compareTo(expected.subtract(radius)) >= 0 && reported.compareTo(expected.add(radius)) <= 0,
                "Original STREME probability/E-value differs from its independent exact table at source precision");
    }

    private static void logarithm(String token, double expected) throws IOException {
        require(Double.isFinite(expected), "Nonfinite independently calculated STREME log10 field");
        BigDecimal point = BigDecimal.valueOf(expected), reported = number(token);
        BigDecimal radius = expected == 0 ? new BigDecimal("1e-9")
                : quantum(point.abs(), 6).divide(BigDecimal.valueOf(2))
                    .add(point.abs().multiply(new BigDecimal("1e-8"))).add(new BigDecimal("1e-9"));
        require(reported.compareTo(point.subtract(radius)) >= 0 && reported.compareTo(point.add(radius)) <= 0,
                "Original STREME log10 field differs from its independent exact table at source precision");
    }

    private static BigDecimal quantum(BigDecimal value, int digits) {
        return BigDecimal.ONE.scaleByPowerOfTen(value.precision() - value.scale() - digits);
    }
    private static int count(String token) throws IOException {
        require(token.matches("0|[1-9][0-9]{0,9}"), "Invalid original STREME sequence count");
        try { return Integer.parseInt(token); }
        catch (NumberFormatException failure) { throw new IOException("Excessive original STREME sequence count", failure); }
    }
    private static String text(MemeXml.Node node) throws IOException {
        require(node.children().isEmpty(), "Unexpected structured STREME scalar"); return node.text().strip();
    }
    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
