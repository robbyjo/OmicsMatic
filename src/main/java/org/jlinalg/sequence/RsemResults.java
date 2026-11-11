/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Complete streaming audits of ordinary original RSEM gene/isoform result pairs.
 * Saved model/theta precision and result precision remain distinct. Compatibility
 * with those bounds does not recover hidden parameters or certify inference,
 * convergence, biological accuracy, or an arbitrary runtime installation.
 */
public final class RsemResults {
    private RsemResults() { }
    /** Requested original posterior output. No-alignment output remains EM-only. */
    public enum Posterior { NONE, MEAN, INTERVAL }

    /**
     * Whole-population and allocation limits.
     * @param maxTranscripts complete transcript capacity
     * @param maxGenes complete gene capacity
     * @param maxGroupTranscripts largest retained group
     * @param maxLengthSupport saved model length-support capacity
     * @param maxNumericCells combined result numeric-cell capacity
     * @param maxFileBytes per-result byte capacity
     * @param maxLineChars per-result line capacity
     */
    public record Limits(int maxTranscripts, int maxGenes, int maxGroupTranscripts,
                         int maxLengthSupport, long maxNumericCells, long maxFileBytes, int maxLineChars) {
        /** Requires positive complete-population capacities. */
        public Limits {
            if (maxTranscripts < 1 || maxGenes < 1 || maxGroupTranscripts < 1 || maxLengthSupport < 1
                    || maxNumericCells < 1 || maxFileBytes < 1 || maxLineChars < 1)
                throw new IllegalArgumentException("Positive complete result capacities required");
        }
    }

    /**
     * One source row, with canonical identities separate from display labels.
     * Numeric fields follow {@link #isoformColumns(Posterior)} or
     * {@link #geneColumns(Posterior)}, excluding the first two identifier columns.
     * @param index zero-based original population index
     * @param rawId exact transcript or gene identifier
     * @param rawAssociatedIds exact gene singleton or ordered transcript members
     * @param displayedId original first table field
     * @param displayedAssociatedIds original second table field, never comma-split
     * @param values every original numeric token and its serialization bounds
     */
    public record Row(int index, String rawId, List<String> rawAssociatedIds,
                      String displayedId, String displayedAssociatedIds, List<RsemPrintedNumber> values) {
        /** Copies both complete populations. */
        public Row {rawAssociatedIds = List.copyOf(rawAssociatedIds); values = List.copyOf(values);}
    }

    /**
     * Streaming destination. Callbacks can precede a later audit failure; retain
     * their output as partial until the complete audit returns successfully.
     */
    public interface Sink {
        /** @param row complete original isoform row @throws IOException destination failure */
        void isoform(Row row) throws IOException;
        /** @param row complete original gene row @throws IOException destination failure */
        void gene(Row row) throws IOException;
    }
    private static final Sink DISCARD = new Sink() {
        @Override public void isoform(Row row) { }
        @Override public void gene(Row row) { }
    };

    /**
     * Conservative sum bounds for printed, nonnegative source quantities.
     * @param lower minimum compatible sum
     * @param upper maximum compatible sum
     */
    public record Bounds(double lower, double upper) { }

    /**
     * Completed population audit, retaining requested/effective schemas and bytes.
     * @param release declared exact original release
     * @param requestedPosterior requested posterior output
     * @param actualPosterior effective output, NONE for original no-alignment runs
     * @param appendNames declared original display-name selection
     * @param transcripts complete transcript rows
     * @param genes complete gene rows
     * @param numericCells complete parsed result numeric fields
     * @param learnedModel whether original learned model parameters exist
     * @param precisionAmbiguousGroups groups whose saved precision straddles an EPSILON branch
     * @param expectedCounts conservative sum of printed EM expected-count intervals
     * @param emTpm conservative sum of printed EM TPM intervals
     * @param posteriorTpm printed posterior TPM sum, null for EM-only output
     * @param resultEvidence both complete result files
     * @param inputEvidence reference/count/model/theta evidence supplied to the audit
     * @param warnings explicit inference and precision limits
     */
    public record Audit(RsemAbundance.Release release, Posterior requestedPosterior, Posterior actualPosterior,
                        boolean appendNames, int transcripts, int genes, long numericCells, boolean learnedModel,
                        int precisionAmbiguousGroups, Bounds expectedCounts, Bounds emTpm, Bounds posteriorTpm,
                        List<RsemEvidence> resultEvidence, List<RsemEvidence> inputEvidence, List<String> warnings) {
        /** Copies all evidence and warnings. */
        public Audit {resultEvidence = List.copyOf(resultEvidence); inputEvidence = List.copyOf(inputEvidence); warnings = List.copyOf(warnings);}
    }

    private static final List<String> ISO = List.of("transcript_id", "gene_id", "length", "effective_length", "expected_count", "TPM", "FPKM", "IsoPct");
    private static final List<String> GENE = List.of("gene_id", "transcript_id(s)", "length", "effective_length", "expected_count", "TPM", "FPKM");
    private static final List<String> PME = List.of("posterior_mean_count", "posterior_standard_deviation_of_count", "pme_TPM", "pme_FPKM");
    private static final List<String> CI = List.of("TPM_ci_lower_bound", "TPM_ci_upper_bound", "TPM_coefficient_of_quartile_variation",
            "FPKM_ci_lower_bound", "FPKM_ci_upper_bound", "FPKM_coefficient_of_quartile_variation");
    /** @param posterior effective schema @return exact complete original isoform columns */
    public static List<String> isoformColumns(Posterior posterior) {return columns(true, posterior);}
    /** @param posterior effective schema @return exact complete original gene columns */
    public static List<String> geneColumns(Posterior posterior) {return columns(false, posterior);}
    private static List<String> columns(boolean isoform, Posterior mode) {
        Objects.requireNonNull(mode, "posterior"); var result = new ArrayList<>(isoform ? ISO : GENE);
        if (mode != Posterior.NONE) {result.addAll(PME); if (isoform) result.add("IsoPct_from_pme_TPM");}
        if (mode == Posterior.INTERVAL) result.addAll(CI);
        return List.copyOf(result);
    }

    /**
     * Audits every row without retaining an entire result matrix.
     * @param isoforms original isoforms.results
     * @param genes original genes.results
     * @param reference complete parsed ordinary reference
     * @param counts complete parsed original counts
     * @param fit complete count-bound saved model state
     * @param release declared exact original release
     * @param posterior requested original posterior output
     * @param appendNames original display-name selection
     * @param limits complete source capacities
     * @return complete audit only after both files and all families pass
     * @throws IOException incomplete or inconsistent original results
     */
    public static Audit audit(Path isoforms, Path genes, RsemReference.Reference reference, RsemCounts.Counts counts,
                              RsemModel.FitState fit, RsemAbundance.Release release, Posterior posterior,
                              boolean appendNames, Limits limits) throws IOException {
        return audit(isoforms, genes, reference, counts, fit, release, posterior, appendNames, limits, DISCARD);
    }

    /**
     * Audits and streams complete source rows. Sink output remains partial until success.
     * @param isoforms original isoform result file
     * @param genes original gene result file
     * @param reference complete parsed reference
     * @param counts complete parsed counts
     * @param fit complete count-bound fit state
     * @param release declared exact source release
     * @param posterior requested original posterior output
     * @param appendNames original name selection
     * @param limits complete source capacities
     * @param sink destination preserving every original member
     * @return completed cross-file audit
     * @throws IOException source, consistency, capacity or destination failure
     */
    public static Audit audit(Path isoforms, Path genes, RsemReference.Reference reference, RsemCounts.Counts counts,
                              RsemModel.FitState fit, RsemAbundance.Release release, Posterior posterior,
                              boolean appendNames, Limits limits, Sink sink) throws IOException {
        Objects.requireNonNull(reference, "reference"); Objects.requireNonNull(counts, "counts"); Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(release, "release"); Objects.requireNonNull(posterior, "posterior"); Objects.requireNonNull(limits, "limits"); Objects.requireNonNull(sink, "sink");
        int m = reference.transcripts().size(), g = reference.groups().size();
        if (reference.type() < 0 || reference.type() > 1 || m < 1 || g < 1 || m > limits.maxTranscripts() || g > limits.maxGenes())
            throw new IOException("Unsupported or over-capacity complete ordinary result population");
        if (fit.learned() != (counts.alignableGroups() > 0)) throw new IOException("Result/model state differs from complete alignment counts");
        if (!fit.learned() && release != RsemAbundance.Release.V1_3_2 && release != RsemAbundance.Release.V1_3_3)
            throw new IOException("Original historical release cannot complete no-alignable-read results");
        if (fit.learned() && (fit.model() == null || fit.theta() == null || fit.model().referenceTranscripts() != m
                || fit.model().type() != counts.readType() || fit.theta().polished().size() != m + 1))
            throw new IOException("Saved model/theta population or read type differs from reference/counts");
        Posterior actual = fit.learned() ? posterior : Posterior.NONE;
        List<String> ih = isoformColumns(actual), gh = geneColumns(actual);
        long cells = (long) m * (ih.size() - 2) + (long) g * (gh.size() - 2);
        if (cells > limits.maxNumericCells()) throw new IOException("Complete result numeric population exceeds declared capacity");
        int previous = 0;
        for (var group : reference.groups()) {
            if (group.begin() != previous || group.end() <= group.begin() || group.end() > m || group.end() - group.begin() > limits.maxGroupTranscripts())
                throw new IOException("Incomplete, unordered or over-capacity gene group");
            for (int j = group.begin(); j < group.end(); j++) if (!reference.transcripts().get(j).geneId().equals(group.id()))
                throw new IOException("Reference gene group mixes exact identifiers");
            previous = group.end();
        }
        if (previous != m) throw new IOException("Gene groups omit reference transcripts");
        ModelBounds model = modelBounds(reference, fit, release, limits);
        Range totalCount = Range.ZERO, totalTpm = Range.ZERO, totalPme = Range.ZERO, totalPmeCount = Range.ZERO;
        boolean positivePme = false, positiveEm = false; int ambiguous = 0;
        try (RsemText iso = new RsemText(isoforms, limits.maxFileBytes(), limits.maxLineChars());
             RsemText gene = new RsemText(genes, limits.maxFileBytes(), limits.maxLineChars())) {
            header(iso, ih); header(gene, gh);
            for (int gi = 0; gi < g; gi++) {
                var group = reference.groups().get(gi); List<Row> members = new ArrayList<>(group.end() - group.begin());
                for (int j = group.begin(); j < group.end(); j++) {
                    var ref = reference.transcripts().get(j);
                    Row row = row(iso, true, j, ref.id(), List.of(ref.geneId()), ref.displayedId(appendNames), ref.displayedGeneId(appendNames), actual, ih);
                    if (row.values().get(0).value() != ref.length()) throw iso.error("Transcript length differs from exact reference: " + ref.id());
                    require(iso, row, 1, model.effective()[j], "saved-model effective length");
                    Range eel = range(row, 1).intersect(model.effective()[j]);
                    if (fit.learned() && row.values().get(1).value() != 0 && eel.hi() < 1)
                        throw iso.error("Positive original transcript effective length is below one");
                    require(iso, row, 3, model.tpm()[j], "saved-theta EM TPM");
                    require(iso, row, 4, model.fpkm()[j], "saved-theta EM FPKM");
                    if (!fit.learned()) for (int k = 2; k < 6; k++) exactZero(iso, row, k, "original no-alignment field");
                    if (actual != Posterior.NONE && model.effective()[j].hi() == 0) {
                        exactZero(iso, row, 8, "zero-effective-length posterior TPM"); exactZero(iso, row, 9, "zero-effective-length posterior FPKM");
                        exactZero(iso, row, 10, "zero-effective-length posterior isoform percentage");
                        if (actual == Posterior.INTERVAL) for (int k = 11; k < 17; k++) exactZero(iso, row, k, "zero-effective-length posterior interval field");
                    }
                    checkIntervals(iso, row, true, actual);
                    totalCount = totalCount.plus(range(row, 2)); totalTpm = totalTpm.plus(range(row, 3));
                    positiveEm |= row.values().get(3).value() > 0;
                    if (actual != Posterior.NONE) {
                        totalPme = totalPme.plus(range(row, 8)); totalPmeCount = totalPmeCount.plus(range(row, 6));
                        if (row.values().get(7).lower() > Math.nextUp((double) counts.alignableGroups())) throw iso.error("Posterior count standard deviation exceeds the complete read/fragment population");
                        positivePme |= row.values().get(8).value() > 0;
                    }
                    members.add(row);
                }
                var first = reference.transcripts().get(group.begin()); List<String> ids = new ArrayList<>(), displayed = new ArrayList<>();
                for (int j = group.begin(); j < group.end(); j++) {var ref = reference.transcripts().get(j); ids.add(ref.id()); displayed.add(ref.displayedId(appendNames));}
                Row gr = row(gene, false, gi, group.id(), ids, first.displayedGeneId(appendNames), String.join(",", displayed), actual, gh);
                for (int k = 2; k <= 4; k++) require(gene, gr, k, sum(members, k), "complete isoform sum for " + gh.get(k + 2));
                Range geneTpm = Range.ZERO, geneFpkm = Range.ZERO;
                for (int j = group.begin(); j < group.end(); j++) {geneTpm = geneTpm.plus(model.tpm()[j]); geneFpkm = geneFpkm.plus(model.fpkm()[j]);}
                require(gene, gr, 3, geneTpm, "complete saved-theta gene TPM"); require(gene, gr, 4, geneFpkm, "complete saved-theta gene FPKM");
                if (!fit.learned()) for (int k = 2; k <= 4; k++) exactZero(gene, gr, k, "original no-alignment gene field");
                if (actual != Posterior.NONE) for (int k : new int[]{5,7,8})
                    require(gene, gr, k, sum(members, k == 5 ? 6 : k + 1), "complete isoform posterior sum for " + gh.get(k + 2));
                if (actual != Posterior.NONE) {
                    if (gr.values().get(6).lower() > Math.nextUp((double) counts.alignableGroups())) throw gene.error("Posterior gene count standard deviation exceeds complete read/fragment population");
                    boolean allZeroEffective = true;
                    for (int j = group.begin(); j < group.end(); j++) allZeroEffective &= model.effective()[j].hi() == 0;
                    if (allZeroEffective) {
                        exactZero(gene,gr,7,"zero-effective-length posterior gene TPM"); exactZero(gene,gr,8,"zero-effective-length posterior gene FPKM");
                        if (actual == Posterior.INTERVAL) for (int k = 9; k < 15; k++) exactZero(gene,gr,k,"zero-effective-length posterior gene interval field");
                    }
                }
                if (geneWeights(iso, gene, gr, members, group, reference, model, actual)) ambiguous++;
                checkIntervals(gene, gr, false, actual);
                for (Row member : members) sink.isoform(member);
                sink.gene(gr);
            }
            iso.endLines(); gene.endLines();
            if (totalCount.lo() > Math.nextUp((double) counts.alignableGroups())) throw iso.error("Expected-count population exceeds original alignable read/fragment groups");
            if (actual != Posterior.NONE && totalPmeCount.lo() > Math.nextUp((double) counts.alignableGroups())) throw iso.error("Posterior mean-count population exceeds original alignable read/fragment groups");
            if (model.positiveExpression() || positiveEm) compatible(iso, totalTpm, Range.point(1e6), "complete EM TPM family");
            if (actual != Posterior.NONE && positivePme) compatible(iso, totalPme, Range.point(1e6), "complete posterior TPM family");
            List<RsemEvidence> inputs = new ArrayList<>(reference.evidence()); inputs.add(counts.evidence()); inputs.addAll(fit.evidence());
            List<String> warnings = new ArrayList<>();
            warnings.add("Printed precision is retained as compatibility bounds; hidden unrounded parameters and inference convergence are not recovered or certified.");
            warnings.add("Posterior count standard deviations and interval endpoints are not additive and are not substituted for EM quantities.");
            if (!fit.learned()) warnings.add("Original no-alignable-read state: EM-only tables, raw-length effective lengths and no learned model/theta; requested posterior inference did not run.");
            if (ambiguous > 0) warnings.add("Saved precision straddles an original EPSILON gene-aggregation branch in " + ambiguous + " groups; the remaining alternatives are retained.");
            return new Audit(release, posterior, actual, appendNames, m, g, cells, fit.learned(), ambiguous,
                    totalCount.bounds(), totalTpm.bounds(), actual == Posterior.NONE ? null : totalPme.bounds(),
                    List.of(iso.evidence(), gene.evidence()), inputs, warnings);
        }
    }

    private static void header(RsemText input, List<String> expected) throws IOException {
        if (!input.requiredLine("complete result header").equals(String.join("\t", expected))) throw input.error("Unexpected complete original result schema");
    }
    private static Row row(RsemText input, boolean iso, int index, String rawId, List<String> associated,
                           String display, String displayAssociated, Posterior mode, List<String> columns) throws IOException {
        String[] fields = input.requiredLine("complete result row").split("\t", -1);
        if (fields.length != columns.size() || !fields[0].equals(display) || !fields[1].equals(displayAssociated))
            throw input.error("Result identity, original order, member join or field population differs: " + rawId);
        List<RsemPrintedNumber> values = new ArrayList<>(fields.length - 2); int ci = iso ? 13 : 11;
        for (int i = 2; i < fields.length; i++) try {
            var format = iso && i == 2 ? RsemPrintedNumber.Format.INTEGER
                    : mode == Posterior.INTERVAL && i >= ci ? RsemPrintedNumber.Format.GENERAL_6 : RsemPrintedNumber.Format.FIXED_2;
            values.add(RsemPrintedNumber.parse(fields[i], format));
        } catch (IllegalArgumentException e) {throw input.error("Invalid original numeric field " + columns.get(i) + " for " + rawId + ": " + e.getMessage());}
        return new Row(index, rawId, associated, fields[0], fields[1], values);
    }
    private static void checkIntervals(RsemText input, Row row, boolean iso, Posterior mode) throws IOException {
        if (mode != Posterior.INTERVAL) return;
        int base = iso ? 11 : 9;
        for (int offset : new int[]{0,3}) {
            double lo = row.values().get(base + offset).value(), hi = row.values().get(base + offset + 1).value();
            if (lo > hi) throw input.error("Reversed original posterior interval for " + row.rawId());
            double cqv = row.values().get(base + offset + 2).value();
            if (cqv > 1) throw input.error("Posterior coefficient of quartile variation exceeds one");
        }
        if (row.values().get(base).lower() > 1e6 || row.values().get(base + 1).lower() > 1e6)
            throw input.error("Posterior TPM interval exceeds its complete-family scale");
    }

    private static boolean geneWeights(RsemText iso, RsemText gene, Row gr, List<Row> rows,
                                       RsemReference.GeneGroup group, RsemReference.Reference reference,
                                       ModelBounds model, Posterior mode) throws IOException {
        Range gtpm = Range.ZERO, unweightedLength = Range.ZERO, unweightedEffective = Range.ZERO;
        for (int k = 0; k < rows.size(); k++) {
            int j = group.begin() + k; gtpm = gtpm.plus(model.tpm()[j]);
            unweightedLength = unweightedLength.plus(Range.point(reference.transcripts().get(j).length()).times(1.0 / rows.size()));
            unweightedEffective = unweightedEffective.plus(range(rows.get(k), 1).intersect(model.effective()[j]).times(1.0 / rows.size()));
        }
        Range weightedLength = Range.ZERO, weightedEffective = Range.ZERO; boolean uncertain = gtpm.lo() <= RsemAbundance.EPSILON && gtpm.hi() >= RsemAbundance.EPSILON;
        Range pmeSum = mode == Posterior.NONE ? Range.ZERO : sum(rows, 8);
        for (int k = 0; k < rows.size(); k++) {
            int j = group.begin() + k; Row row = rows.get(k);
            Range fraction = gtpm.hi() <= RsemAbundance.EPSILON ? Range.ZERO : model.tpm()[j].divide(gtpm).clip(0,1);
            if (uncertain) fraction = fraction.union(Range.ZERO);
            require(iso, row, 5, fraction.times(100), "within-gene EM TPM percentage");
            weightedLength = weightedLength.plus(fraction.times(reference.transcripts().get(j).length()));
            weightedEffective = weightedEffective.plus(fraction.multiply(range(row, 1).intersect(model.effective()[j])));
            if (mode != Posterior.NONE) {
                Range pf = pmeSum.hi() <= RsemAbundance.EPSILON ? Range.ZERO : range(row, 8).divide(pmeSum).clip(0,1);
                if (pmeSum.lo() <= RsemAbundance.EPSILON) pf = pf.union(Range.ZERO);
                require(iso, row, 10, pf.times(100), "within-gene posterior TPM percentage");
            }
        }
        Range length = gtpm.hi() < RsemAbundance.EPSILON ? unweightedLength : weightedLength;
        Range effective = gtpm.hi() < RsemAbundance.EPSILON ? unweightedEffective : weightedEffective;
        if (uncertain) {length = length.union(unweightedLength).union(Range.ZERO); effective = effective.union(unweightedEffective).union(Range.ZERO);}
        require(gene, gr, 0, length, "EM-TPM-weighted reference length or original zero-expression fallback");
        require(gene, gr, 1, effective, "EM-TPM-weighted effective length or original zero-expression fallback");
        return uncertain;
    }

    private record ModelBounds(Range[] effective, Range[] tpm, Range[] fpkm, boolean positiveExpression) { }
    private static ModelBounds modelBounds(RsemReference.Reference reference, RsemModel.FitState fit,
                                            RsemAbundance.Release release, Limits limits) throws IOException {
        int m = reference.transcripts().size(); Range[] eel = new Range[m], tpm = new Range[m], fpkm = new Range[m];
        if (!fit.learned()) {
            for (int j = 0; j < m; j++) {eel[j] = Range.point(reference.transcripts().get(j).length()); tpm[j] = fpkm[j] = Range.ZERO;}
            return new ModelBounds(eel, tpm, fpkm, false);
        }
        var lengths = fit.model().fragmentLengths(); int n = lengths.probabilities().size(), lb = lengths.lowerExclusive(), ub = lengths.upperInclusive();
        if (n < 1 || n > limits.maxLengthSupport() || (long) ub - lb != n) throw new IOException("Complete saved length support exceeds result-audit capacity or dimensions");
        Range[] cdf = new Range[n + 1], moment = new Range[n + 1]; cdf[0] = moment[0] = Range.ZERO;
        for (int i = 1; i <= n; i++) {
            Range pdf = Range.of(lengths.probabilities().get(i - 1)); cdf[i] = cdf[i - 1].plus(pdf);
            moment[i] = moment[i - 1].plus(pdf.times((double) lb + i));
        }
        Range denominator = Range.ZERO;
        for (int j = 0; j < m; j++) {
            var ref = reference.transcripts().get(j); int p1 = Math.max(Math.min(ref.polyATail() + 1, ub) - lb, 0), p2 = Math.max(Math.min(ref.totalLength(), ub) - lb, 0);
            Range value = p2 == 0 ? Range.ZERO : cdf[p1].times(ref.length()).plus(cdf[p2].minus(cdf[p1]).times((double) ref.totalLength() + 1).minus(moment[p2].minus(moment[p1])));
            value = value.clip(0,Double.POSITIVE_INFINITY);
            eel[j] = value.hi() < 1 ? Range.ZERO : value.lo() < 1 ? new Range(0,value.hi()) : value;
            Range theta = Range.of(fit.theta().polished().get(j + 1));
            if (eel[j].hi() == 0 && theta.lo() > 0) throw new IOException("Saved polished theta is positive for a zero-effective-length transcript");
            if (theta.hi() > 0) {eel[j] = eel[j].clip(1,Double.POSITIVE_INFINITY); denominator = denominator.plus(theta);}
        }
        boolean modern = release == RsemAbundance.Release.V1_3_2 || release == RsemAbundance.Release.V1_3_3;
        if (!modern && denominator.hi() < RsemAbundance.EPSILON) throw new IOException("Historical original expression normalization has no signal");
        Range normalizer = denominator.hi() < RsemAbundance.EPSILON ? Range.ONE : denominator;
        if (modern && denominator.lo() < RsemAbundance.EPSILON) normalizer = normalizer.union(Range.ONE);
        Range totalFpkm = Range.ZERO;
        for (int j = 0; j < m; j++) {
            Range theta = Range.of(fit.theta().polished().get(j + 1));
            fpkm[j] = theta.hi() == 0 || eel[j].hi() == 0 ? Range.ZERO : theta.divide(normalizer).times(1e9).divide(eel[j]);
            totalFpkm = totalFpkm.plus(fpkm[j]);
        }
        Range fpkmNormalizer = modern && totalFpkm.hi() < RsemAbundance.EPSILON ? Range.ONE : totalFpkm;
        if (modern && totalFpkm.lo() < RsemAbundance.EPSILON) fpkmNormalizer = fpkmNormalizer.union(Range.ONE);
        for (int j = 0; j < m; j++) tpm[j] = fpkm[j].hi() == 0 ? Range.ZERO : fpkm[j].divide(fpkmNormalizer).times(1e6);
        return new ModelBounds(eel, tpm, fpkm, totalFpkm.lo() >= RsemAbundance.EPSILON);
    }

    private static Range sum(List<Row> rows, int field) {Range result = Range.ZERO; for (Row row : rows) result = result.plus(range(row,field)); return result;}
    private static Range range(Row row, int field) {return Range.of(row.values().get(field));}
    private static void require(RsemText input, Row row, int field, Range expected, String role) throws IOException {compatible(input, range(row,field), expected, role + " for " + row.rawId());}
    private static void compatible(RsemText input, Range observed, Range expected, String role) throws IOException {
        if (observed.lo() > expected.hi() || expected.lo() > observed.hi()) throw input.error("Printed value is incompatible with " + role);
    }
    private static void exactZero(RsemText input, Row row, int field, String role) throws IOException {
        if (row.values().get(field).value() != 0) throw input.error("Nonzero " + role + " for " + row.rawId());
    }

    /** Outward binary64 bounds; exact zero is retained across identity operations. */
    private record Range(double lo, double hi) {
        private static final Range ZERO = new Range(0,0), ONE = new Range(1,1);
        static Range point(double x) {return new Range(x,x);}
        static Range of(RsemPrintedNumber x) {return new Range(x.lower(),x.upper());}
        static double down(double x) {return x == 0 ? 0 : Math.nextDown(x);}
        static double up(double x) {return x == 0 ? 0 : Math.nextUp(x);}
        boolean zero() {return lo == 0 && hi == 0;}
        Range plus(Range b) {if (zero()) return b; if (b.zero()) return this; return new Range(down(lo + b.lo),up(hi + b.hi));}
        Range minus(Range b) {if (b.zero()) return this; return new Range(down(lo - b.hi),up(hi - b.lo));}
        Range times(double x) {if (x == 0 || zero()) return ZERO; if (x == 1) return this; return new Range(down(lo*x),up(hi*x));}
        Range multiply(Range b) {if (zero() || b.zero()) return ZERO; return new Range(down(lo*b.lo),up(hi*b.hi));}
        Range divide(Range b) {if (zero()) return ZERO; return b.lo <= 0 ? new Range(0,Double.POSITIVE_INFINITY) : new Range(down(lo/b.hi),up(hi/b.lo));}
        Range union(Range b) {return new Range(Math.min(lo,b.lo),Math.max(hi,b.hi));}
        Range clip(double min, double max) {return new Range(Math.max(min,lo),Math.min(max,hi));}
        Range intersect(Range b) {return new Range(Math.max(lo,b.lo),Math.min(hi,b.hi));}
        Bounds bounds() {return new Bounds(lo,hi);}
    }
}
