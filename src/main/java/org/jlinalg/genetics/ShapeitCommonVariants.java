/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Original SHAPEIT5 c34d4db common-stage selection on an already synchronized stream.
 * Derived from genotype_reader_scaning.cpp and the original XCF reader. Their MIT
 * notices are retained in META-INF/licenses/SHAPEIT5-MIT.txt and
 * SHAPEIT5-XCF-MIT.txt. This API does not reconstruct HTSlib joins, inspect files,
 * decode binary genotype payloads or estimate haplotypes.
 *
 * <p>AC/AN logical getter cardinality is checked for every biallelic panel record
 * before exclusion filters. Signed values are converted to uint32, and the MAF
 * comparison uses Float32 arithmetic with equality retained. Original scanner
 * departures, including a kept site with an unusable scaffold record, remain
 * visible. A successful source mask is not evidence of valid scaffold data,
 * genotype payloads or scientifically usable counts. A protected caller must
 * independently validate source identity, EOF, join completeness and admission.
 */
public final class ShapeitCommonVariants {
    private ShapeitCommonVariants() { }

    /** Original XCF file classification, distinct from per-record type codes. */
    public enum FileKind {
        /** Ordinary sites-only file without sample data or an associated binary file. */
        VOID,
        /** Ordinary VCF/BCF with samples. */
        BCF,
        /** XCF file with associated binary and sample metadata. */
        BINARY
    }

    /**
     * Verified source header classification.
     * @param samples count reported by the original XCF reader, including FAM samples
     * @param kind original source file kind
     */
    public record Header(int samples, FileKind kind) {
        /** Validate the relationship between original file kind and sample count. */
        public Header {
            Objects.requireNonNull(kind);
            if (samples < 0 || kind == FileKind.VOID && samples != 0 || kind == FileKind.BCF && samples == 0)
                throw new IllegalArgumentException("Invalid original XCF file-kind/sample relationship");
        }
    }

    /**
     * Exact scanner filter settings; nonpositive and NaN thresholds disable MAF filtering.
     * @param snpsOnly original uppercase A/T/G/C-only filter
     * @param minimumMaf threshold already converted to the scanner's float field
     */
    public record Options(boolean snpsOnly, float minimumMaf) {
        /**
         * Preserve the source's double-to-float setter and internal default.
         * @param snpsOnly whether the SNP filter is enabled
         * @param threshold supplied double or null for the original internal default
         * @return exact scanner settings; this is not CLI argument admission
         */
        public static Options fromDouble(boolean snpsOnly, Double threshold) {
            return new Options(snpsOnly, threshold == null ? -1.0f : threshold.floatValue());
        }
    }

    /**
     * Complete-population limits, never a sampling policy.
     * @param samples maximum samples in each configured panel
     * @param events maximum complete synchronized events
     * @param retainedBytes maximum estimated retained decision storage
     */
    public record Limits(int samples, long events, long retainedBytes) {
        /** Require positive complete-population capacities. */
        public Limits {
            if (samples < 1 || events < 1 || retainedBytes < 1) throw new IllegalArgumentException("Require positive complete SHAPEIT selection capacities");
        }
        /**
         * Use explicit bounded defaults.
         * @return capacities callers may raise with sufficient Java heap
         */
        public static Limits defaults() { return new Limits(1000000, 1000000, 536870912L); }
    }

    /**
     * One original HTSlib INFO/int32 getter result, before uint32 conversion.
     * @param returned logical getter cardinality or original negative error code
     * @param first first normalized int32 value, meaningful only when returned is positive
     */
    public record CountValue(int returned, int first) { }

    /**
     * Original record identity, retaining allele case and all alternate alleles.
     * @param contig exact contig name
     * @param position positive one-based coordinate
     * @param id original identifier
     * @param alleles REF followed by every ALT, without collapsing duplicates
     */
    public record Site(String contig, int position, String id, List<String> alleles) {
        /** Retain immutable original identity fields. */
        public Site {
            Objects.requireNonNull(contig); Objects.requireNonNull(id); alleles = List.copyOf(alleles);
            if (contig.isEmpty() || position < 1 || alleles.isEmpty() || alleles.stream().anyMatch(String::isEmpty))
                throw new IllegalArgumentException("Invalid original SHAPEIT record identity");
        }
    }

    /**
     * Physical panel record supplied by a verified original join decoder.
     * @param site complete original record identity
     * @param recordType original XCF record type; unknown codes remain inspectable
     * @param ac original AC getter result; ignored for non-biallelic records
     * @param an original AN getter result; ignored for non-biallelic records
     * @param gtPresent whether a physical GT field exists, distinct from binary genotype data
     */
    public record PanelRow(Site site, int recordType, CountValue ac, CountValue an, boolean gtPresent) {
        /** Require physical record identity. */
        public PanelRow { Objects.requireNonNull(site); }
        private boolean biallelic() { return site.alleles().size() == 2; }
    }

    /**
     * One complete original synchronized event; null means no physical row in that file.
     * @param target target row or null
     * @param reference reference row or null
     * @param scaffold scaffold row or null
     */
    public record Event(PanelRow target, PanelRow reference, PanelRow scaffold) { }

    /** Original branch that decides the site's mask value. */
    public enum Reason {
        /** Original scanner keeps the site. */
        KEEP,
        /** No configured panel contributes a biallelic record. */
        ALL_NONBIALLELIC,
        /** Target site has no matching reference record. */
        NO_REFERENCE_OVERLAP,
        /** Reference record has no target counterpart. */
        REFERENCE_ONLY,
        /** Scaffold record has no target counterpart. */
        SCAFFOLD_ONLY,
        /** Target type is neither ordinary GT nor binary genotype. */
        UNSUPPORTED_TARGET_FORMAT,
        /** Reference type is not accepted by the original scanner. */
        UNSUPPORTED_REFERENCE_FORMAT,
        /** At least one allele is not an uppercase A, T, G or C. */
        NOT_UPPERCASE_SNP,
        /** Float32 MAF is strictly below the enabled threshold. */
        BELOW_FLOAT32_MAF
    }

    /** Source behavior requiring separate admission or scientific review. */
    public enum Risk {
        /** A signed int32 count was converted to uint32. */
        SIGNED_COUNT_CONVERSION,
        /** A typed missing int32 count was interpreted as uint32. */
        MISSING_COUNT_VALUE,
        /** Declared AN is zero. */
        ZERO_AN,
        /** Declared unsigned AC exceeds AN. */
        AC_EXCEEDS_AN,
        /** Enabled MAF arithmetic produced a non-finite value. */
        NONFINITE_MAF,
        /** Original scaffold-format counter increments without necessarily excluding the site. */
        UNSUPPORTED_SCAFFOLD_FORMAT,
        /** A kept site has an unsupported scaffold record. */
        KEPT_WITH_UNSUPPORTED_SCAFFOLD,
        /** A kept ordinary-GT target record has no physical GT field. */
        KEPT_TARGET_WITHOUT_GT,
        /** A kept ordinary-GT reference record has no physical GT field. */
        KEPT_REFERENCE_WITHOUT_GT,
        /** A kept ordinary-GT scaffold record has no physical GT field. */
        KEPT_SCAFFOLD_WITHOUT_GT
    }

    /** Actionable source failure; a failed audit can never return a complete report. */
    public static final class SourceInputException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        /**
         * Stable diagnostic code.
         * @serial original source failure classification
         */
        private final String code;
        /**
         * Read the source failure classification.
         * @return stable diagnostic code
         */
        public String code() { return code; }
        private SourceInputException(String code, String action) { super(action); this.code = code; }
    }

    /**
     * Immutable source-mask decision; derived NaN payload bits are not an inference contract.
     * @param ordinal zero-based original synchronized event ordinal
     * @param site first biallelic panel identity, or null when none exists
     * @param reason exact source exclusion branch or KEEP
     * @param mafEvaluated whether the original enabled MAF expression is reached
     * @param minorAlleleFrequency derived Float32 MAF, or NaN when not evaluated
     * @param risks source departures requiring separate review
     */
    public record Decision(long ordinal, Site site, Reason reason, boolean mafEvaluated, float minorAlleleFrequency, Set<Risk> risks) {
        /** Retain an immutable ordered risk set. */
        public Decision { Objects.requireNonNull(reason); risks = immutableRisks(risks); }
        /**
         * Read the original mask value.
         * @return true exactly for the KEEP branch
         */
        public boolean kept() { return reason == Reason.KEEP; }
    }

    /**
     * Complete source-mask report; this does not certify a file or phasing analysis.
     * @param decisions every original synchronized event, in original order
     * @param selectedSites every retained site, in original order
     * @param reasonCounts complete event counts by source branch
     * @param sourceWithScaffold original pre-filter scaffold-data counter
     * @param unsupportedScaffoldFormats original pre-filter scaffold-format counter
     * @param selectedWithAcceptedScaffoldType retained sites with a source-accepted scaffold type
     * @param risks union of all observed source departures
     */
    public record Report(List<Decision> decisions, List<Site> selectedSites, Map<Reason, Long> reasonCounts,
                         long sourceWithScaffold, long unsupportedScaffoldFormats, long selectedWithAcceptedScaffoldType, Set<Risk> risks) {
        /** Defensively retain complete populations and counters. */
        public Report {
            decisions = List.copyOf(decisions); selectedSites = List.copyOf(selectedSites);
            var counts = new EnumMap<Reason, Long>(Reason.class); counts.putAll(reasonCounts); reasonCounts = Collections.unmodifiableMap(counts);
            risks = immutableRisks(risks);
        }
    }

    /** A single-use, failure-sticky audit of the complete original joined stream. */
    public static final class Audit {
        private final Header target, reference, scaffold;
        private final Options options;
        private final Limits limits;
        private final List<Decision> decisions = new ArrayList<>();
        private final List<Site> selected = new ArrayList<>();
        private final EnumMap<Reason, Long> counts = new EnumMap<>(Reason.class);
        private final EnumSet<Risk> risks = EnumSet.noneOf(Risk.class);
        private long retained, withScaffold, invalidScaffold, selectedScaffold;
        private boolean failed, finished;

        /**
         * Configure the exact source sample guard and file-header boundary checks.
         * @param target verified target header
         * @param reference verified reference header, or null if not configured
         * @param scaffold verified scaffold header, or null if not configured
         * @param options original scanner settings
         * @param limits explicit complete-population capacities
         */
        public Audit(Header target, Header reference, Header scaffold, Options options, Limits limits) {
            this.target = Objects.requireNonNull(target); this.reference = reference; this.scaffold = scaffold;
            this.options = Objects.requireNonNull(options); this.limits = Objects.requireNonNull(limits);
            boolean gap = false;
            for (Header header : new Header[] {target, reference, scaffold}) if (header != null) {
                if (header.samples() > limits.samples()) throw source("SAMPLE_CAPACITY", "Complete panel sample count exceeds capacity. Raise the explicit sample limit and heap; do not sample a successful prefix.");
                if (header.kind() == FileKind.VOID) gap = true;
                else if (gap) throw source("VOID_HEADER_GAP", "Original XCF file registration writes outside its sample-name vector when an ordinary sites-only file precedes a data-bearing file. Skip dependent phasing; supply appropriate nonempty target/panel inputs or separately validate an upstream header-handling fix.");
            }
            if ((long)target.samples() + (reference == null ? 0 : reference.samples()) < 50)
                throw source("FEWER_THAN_50_SAMPLES", "Original common-stage selection requires at least 50 target plus reference samples. Scaffold samples do not count. Supply an appropriate permitted reference or target population; skip dependent phasing while this guard is unmet.");
            for (Reason reason : Reason.values()) counts.put(reason, 0L);
        }

        /**
         * Audit the next original joined event. Decisions are provisional until finish.
         * @param event complete original synchronized event
         * @return immutable source decision
         */
        public Decision accept(Event event) {
            ready();
            try {
                Objects.requireNonNull(event);
                if (reference == null && event.reference() != null || scaffold == null && event.scaffold() != null)
                    throw source("UNCONFIGURED_PANEL", "A joined event contains a row from a panel that was not configured. Rebuild the complete original join; skip dependent analysis.");
                PanelRow[] rows = {event.target(), event.reference(), event.scaffold()}; Site primary = null; boolean any = false;
                EnumSet<Risk> observed = EnumSet.noneOf(Risk.class); Site coordinate = null;
                for (int panel = 0; panel < rows.length; panel++) {
                    PanelRow row = rows[panel]; if (row == null) continue; any = true;
                    if (coordinate == null) coordinate = row.site();
                    else if (!coordinate.contig().equals(row.site().contig()) || coordinate.position() != row.site().position())
                        throw source("JOIN_COORDINATE_MISMATCH", "Panel rows do not belong to one original synchronized coordinate. Preserve the input rows and rebuild the verified join before analysis.");
                    if (!row.biallelic()) continue;
                    if (primary == null) primary = row.site();
                    count(row.ac(), "AC", panel); count(row.an(), "AN", panel);
                    if (row.ac().first() < 0 || row.an().first() < 0) observed.add(Risk.SIGNED_COUNT_CONVERSION);
                    if (row.ac().first() == Integer.MIN_VALUE || row.an().first() == Integer.MIN_VALUE) observed.add(Risk.MISSING_COUNT_VALUE);
                    long ac = Integer.toUnsignedLong(row.ac().first()), an = Integer.toUnsignedLong(row.an().first());
                    if (an == 0) observed.add(Risk.ZERO_AN);
                    if (ac > an) observed.add(Risk.AC_EXCEEDS_AN);
                }
                if (!any) throw source("EMPTY_JOIN_EVENT", "An original synchronized event must contain at least one physical record. Supply the complete verified joined stream.");
                if (decisions.size() >= limits.events()) throw source("EVENT_CAPACITY", "Complete synchronized-event capacity exceeded. Raise the explicit capacity rather than truncating events.");
                boolean main = present(event.target()), ref = present(event.reference()), scaf = present(event.scaffold());
                Reason reason; boolean evaluated = false, acceptedScaffoldType = false; float maf = Float.NaN;
                if (!main && !ref && !scaf) reason = Reason.ALL_NONBIALLELIC;
                else if (reference != null && main && !ref) reason = Reason.NO_REFERENCE_OVERLAP;
                else if (reference != null && !main && ref) reason = Reason.REFERENCE_ONLY;
                else if (scaffold != null && !main && scaf) reason = Reason.SCAFFOLD_ONLY;
                else if (!main || !targetType(event.target().recordType())) reason = Reason.UNSUPPORTED_TARGET_FORMAT;
                else if (reference != null && !referenceType(event.reference().recordType())) reason = Reason.UNSUPPORTED_REFERENCE_FORMAT;
                else {
                    if (scaffold != null && scaf) {
                        acceptedScaffoldType = scaffoldType(event.scaffold().recordType());
                        if (acceptedScaffoldType) withScaffold++;
                        else { invalidScaffold++; observed.add(Risk.UNSUPPORTED_SCAFFOLD_FORMAT); }
                    }
                    if (options.snpsOnly() && (!nucleotide(primary.alleles().get(0)) || !nucleotide(primary.alleles().get(1)))) reason = Reason.NOT_UPPERCASE_SNP;
                    else {
                        if (options.minimumMaf() > 0) {
                            evaluated = true;
                            float af = (float)Integer.toUnsignedLong(event.target().ac().first()) / (float)Integer.toUnsignedLong(event.target().an().first());
                            float complement = 1.0f - af;
                            maf = af < complement ? af : complement;
                            if (!Float.isFinite(maf)) observed.add(Risk.NONFINITE_MAF);
                        }
                        reason = evaluated && maf < options.minimumMaf() ? Reason.BELOW_FLOAT32_MAF : Reason.KEEP;
                    }
                }
                if (reason == Reason.KEEP) {
                    if (observed.contains(Risk.UNSUPPORTED_SCAFFOLD_FORMAT)) observed.add(Risk.KEPT_WITH_UNSUPPORTED_SCAFFOLD);
                    if (event.target().recordType() == 1 && !event.target().gtPresent()) observed.add(Risk.KEPT_TARGET_WITHOUT_GT);
                    if (reference != null && event.reference().recordType() == 1 && !event.reference().gtPresent()) observed.add(Risk.KEPT_REFERENCE_WITHOUT_GT);
                    if (scaf && event.scaffold().recordType() == 1 && !event.scaffold().gtPresent()) observed.add(Risk.KEPT_SCAFFOLD_WITHOUT_GT);
                    if (acceptedScaffoldType) selectedScaffold++;
                }
                long bytes = 192;
                if (primary != null) {
                    long characters = (long)primary.contig().length() + primary.id().length();
                    for (String allele : primary.alleles()) characters = Math.addExact(characters, allele.length());
                    bytes = Math.addExact(bytes, Math.multiplyExact(2L, characters));
                }
                retained = Math.addExact(retained, bytes);
                if (retained > limits.retainedBytes() || retained > Runtime.getRuntime().maxMemory() / 2)
                    throw source("RETAINED_CAPACITY", "Complete selection decisions exceed retained capacity or Java heap. Raise the explicit retainedBytes limit and -Xmx; do not return a sampled or partial analysis.");
                Decision decision = new Decision(decisions.size(), primary, reason, evaluated, maf, observed);
                decisions.add(decision); if (decision.kept()) selected.add(primary);
                counts.put(reason, Math.addExact(counts.get(reason), 1)); risks.addAll(observed); return decision;
            } catch (RuntimeException failure) { failed = true; throw failure; }
        }

        /**
         * Close against the independently completed original join census.
         * @param expectedEvents complete source-join event count, not a sampled prefix
         * @return complete source mask and diagnostics
         */
        public Report finish(long expectedEvents) {
            ready();
            try {
                if (expectedEvents != decisions.size()) throw source("INCOMPLETE_JOIN_STREAM", "Visited synchronized events differ from the complete source census. Preserve provisional decisions, correct the source/decoder failure and rerun; no complete analysis is available.");
                if (selected.isEmpty()) throw source("NO_VARIANTS", "The original scanner has no variants to phase. Inspect the requested region, reference overlap, supported formats and filters; do not report a successful empty phasing analysis.");
                finished = true; return new Report(decisions, selected, counts, withScaffold, invalidScaffold, selectedScaffold, risks);
            } catch (RuntimeException failure) { failed = true; throw failure; }
        }

        private void ready() {
            if (failed) throw new IllegalStateException("SHAPEIT selection audit is permanently failed; no complete report can be produced");
            if (finished) throw new IllegalStateException("SHAPEIT selection audit is already finished");
        }
    }

    private static boolean present(PanelRow row) { return row != null && row.biallelic(); }
    private static boolean targetType(int code) { return code == 1 || code == 4; }
    private static boolean referenceType(int code) { return code == 1 || code == 3 || code == 5; }
    private static boolean scaffoldType(int code) { return code == 1 || code == 5; }
    private static boolean nucleotide(String value) { return value.equals("A") || value.equals("T") || value.equals("G") || value.equals("C"); }
    private static void count(CountValue value, String tag, int panel) {
        if (value == null || value.returned() != 1)
            throw source(tag + "_INFO_CARDINALITY", "Original panel " + panel + " requires Integer INFO/" + tag + " with exactly one logical value, even at records later excluded by filters (getter result " + (value == null ? "absent" : value.returned()) + "). Skip dependent analysis; supply correctly typed, source-verified AC/AN metadata. Do not silently derive replacement counts from GT.");
    }
    private static Set<Risk> immutableRisks(Set<Risk> supplied) {
        var copy = EnumSet.noneOf(Risk.class); copy.addAll(supplied); return Collections.unmodifiableSet(copy);
    }
    private static SourceInputException source(String code, String action) { return new SourceInputException(code, action); }
}
