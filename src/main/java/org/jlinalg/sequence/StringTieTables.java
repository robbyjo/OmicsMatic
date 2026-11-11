/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Complete original StringTie gene/Ballgown table parsing and reference-topology
 * audits. It preserves source values; it does not estimate expression or certify
 * read-count/coverage statistics. Feature sharing replays the original ordered
 * insertion rule, including its unknown-strand wildcard, rather than replacing
 * that rule with ordinary interval equality.
 */
public final class StringTieTables {
    private StringTieTables() { }
    private static final String GENE = "Gene ID\tGene Name\tReference\tStrand\tStart\tEnd\tCoverage\tFPKM\tTPM";
    private static final String TRANSCRIPT = "t_id\tchr\tstrand\tstart\tend\tt_name\tnum_exons\tlength\tgene_id\tgene_name\tcov\tFPKM";
    private static final String EXON = "e_id\tchr\tstrand\tstart\tend\trcount\tucount\tmrcount\tcov\tcov_sd\tmcov\tmcov_sd";
    private static final String INTRON = "i_id\tchr\tstrand\tstart\tend\trcount\tucount\tmrcount";
    private static final Pattern UINT = Pattern.compile("(?:0|[1-9][0-9]*)");

    /** All six complete source Readers. The caller owns their lifetimes and strict byte decoding. */
    public record Inputs(Reader genes, Reader transcripts, Reader exons, Reader introns, Reader exonLinks, Reader intronLinks) {
        /** Require the complete captured family, including both relation tables. */
        public Inputs {
            Objects.requireNonNull(genes); Objects.requireNonNull(transcripts); Objects.requireNonNull(exons);
            Objects.requireNonNull(introns); Objects.requireNonNull(exonLinks); Objects.requireNonNull(intronLinks);
        }
    }
    /** Complete shared capacities for all six tables; none permits truncation. */
    public record Limits(long maximumRows, int maximumLineCharacters, long maximumCharacters,
                         int maximumFeatures, long maximumTopologyWork) {
        /** Validate positive bounded capacities; these do not guarantee available heap. */
        public Limits {
            if (maximumRows < 6 || maximumRows > 50_000_000 || maximumLineCharacters < 1 || maximumLineCharacters > 4_000_000
                    || maximumCharacters < 1 || maximumCharacters > 1_000_000_000
                    || maximumFeatures < 1 || maximumFeatures > 5_000_000
                    || maximumTopologyWork < 1 || maximumTopologyWork > 100_000_000_000L)
                throw new IllegalArgumentException("Invalid complete StringTie table capacities");
        }
    }
    /** Exact source text and line ending, with all header/data cells retained. */
    public record Line(long number, String text, String ending, List<String> cells) {
        /** Copy the complete source cell population. */
        public Line { cells = List.copyOf(cells); }
    }
    /** Exact original table, including its required header and original row order. */
    public record Table(String name, List<Line> lines) {
        /** Copy every source line. */
        public Table { lines = List.copyOf(lines); }
    }
    /** One original transcript-table row; source identity and printed quantities are unchanged. */
    public record TranscriptRow(int id, String transcriptId, String geneId, String geneName,
                                String contig, char strand, int start1, int end1, int exonCount, long length,
                                StringTieGtf.Printed coverage, StringTieGtf.Printed fpkm) { }
    /** Original exon/intron geometry and every count/statistic; introns have no coverage moments. */
    public record FeatureRow(int id, String contig, char strand, int start1, int end1, long readCount,
                             long uniqueReadCount, StringTieGtf.Printed weightedReadCount,
                             List<StringTieGtf.Printed> coverageMoments) {
        /** Copy every original moment in cov,cov_sd,mcov,mcov_sd order. */
        public FeatureRow { coverageMoments = List.copyOf(coverageMoments); }
    }
    /** One exact source gene row; coverage is not summed transcript coverage. */
    public record GeneRow(String id, String name, String contig, char strand, int start1, int end1,
                          StringTieGtf.Printed coverage, StringTieGtf.Printed fpkm, StringTieGtf.Printed tpm) { }
    /** Source relation order, including wildcard links without changing either strand. */
    public record Link(int featureId, int transcriptId) { }
    /**
     * Differences between printed gene values and printed transcript reductions.
     * They are retained diagnostics, not equality gates or inference certificates.
     * Weighted coverage residual is geneCoverage*unionExonLength minus the sum of
     * transcriptCoverage*transcriptLength; its units include nucleotide length.
     */
    public record GeneRelation(String geneId, long unionExonLength, BigDecimal weightedCoverageResidual,
                                BigDecimal printedFpkmResidual, BigDecimal printedTpmResidual,
                                int emittedGtfTranscripts, int omittedGtfTranscripts) { }
    /** Only a complete checked parse can construct this result. */
    public static final class Audit {
        private final List<Table> tables;
        private final List<TranscriptRow> transcripts;
        private final List<FeatureRow> exons, introns;
        private final List<GeneRow> genes;
        private final List<Link> exonLinks, intronLinks;
        private final List<GeneRelation> geneRelations;
        private final StringTieGtf.ReferenceAudit reference;
        private final long characters, topologyWork;
        private final int wildcardLinks;
        private Audit(List<Table> tables, List<TranscriptRow> transcripts, List<FeatureRow> exons, List<FeatureRow> introns,
                      List<GeneRow> genes, List<Link> exonLinks, List<Link> intronLinks, List<GeneRelation> relations,
                      StringTieGtf.ReferenceAudit reference, Budget budget, int wildcardLinks) {
            this.tables = List.copyOf(tables); this.transcripts = List.copyOf(transcripts); this.exons = List.copyOf(exons);
            this.introns = List.copyOf(introns); this.genes = List.copyOf(genes); this.exonLinks = List.copyOf(exonLinks);
            this.intronLinks = List.copyOf(intronLinks); this.geneRelations = List.copyOf(relations); this.reference = reference;
            this.characters = budget.characters; this.topologyWork = budget.work; this.wildcardLinks = wildcardLinks;
        }
        /** Every original table, in genes/transcripts/exons/introns/exon-links/intron-links order. */
        public List<Table> tables() { return tables; }
        /** Every original transcript row. */
        public List<TranscriptRow> transcripts() { return transcripts; }
        /** Every original exon row. */
        public List<FeatureRow> exons() { return exons; }
        /** Every original intron row. */
        public List<FeatureRow> introns() { return introns; }
        /** Every original gene row. */
        public List<GeneRow> genes() { return genes; }
        /** Complete original exon-to-transcript relations. */
        public List<Link> exonLinks() { return exonLinks; }
        /** Complete original intron-to-transcript relations. */
        public List<Link> intronLinks() { return intronLinks; }
        /** Explicit reference output coverage policy and every omission. */
        public StringTieGtf.ReferenceAudit reference() { return reference; }
        /** Printed-value reduction discrepancies, preserved without forcing equality. */
        public List<GeneRelation> geneRelations() { return geneRelations; }
        /** Complete decoded UTF-16 code-unit count over all six tables. */
        public long characters() { return characters; }
        /** Actual bounded topology work; large insertion families can be quadratic. */
        public long topologyWork() { return topologyWork; }
        /** Relations with differing retained feature/transcript strands permitted by the source wildcard rule. */
        public int wildcardLinks() { return wildcardLinks; }
    }

    /**
     * Parse every gene/Ballgown table and replay the original reference-feature
     * topology for a supplied complete original estimation. All reference names,
     * parents, spans, exon counts/lengths, feature identities, ordered relation
     * populations and source decimal formats are checked. No supplied source row
     * is ignored. Coverage/FPKM fit readback and normalization require the separate
     * edition-aware StringTie normalization audit; count/coverage inference and
     * gene printed reductions are not certified by this method.
     * @param inputs all six complete captured source tables
     * @param annotation complete native ANNOTATION parse of the exact supplied guide
     * @param estimation complete native ESTIMATION parse of original output
     * @param policy explicit handling of omitted original GTF transcripts
     * @param limits shared complete capacities
     * @param checkpoint cancellation/progress hook; unchecked cancellation propagates
     * @return complete structural/reference audit with original values and diagnostics
     * @throws IOException on missing/extra/invalid scientific rows or exceeded capacities
     */
    public static Audit auditEstimation(Inputs inputs, StringTieGtf.Document annotation, StringTieGtf.Document estimation,
                                        StringTieGtf.PopulationPolicy policy, Limits limits, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(inputs); Objects.requireNonNull(limits); Objects.requireNonNull(checkpoint);
        var coverage = StringTieGtf.auditEstimation(estimation, annotation, policy);
        Budget budget = new Budget(limits, checkpoint);
        List<Table> tables = List.of(read(inputs.genes(), "genes", GENE, 9, budget),
                read(inputs.transcripts(), "t_data", TRANSCRIPT, 12, budget), read(inputs.exons(), "e_data", EXON, 12, budget),
                read(inputs.introns(), "i_data", INTRON, 8, budget), read(inputs.exonLinks(), "e2t", "e_id\tt_id", 2, budget),
                read(inputs.intronLinks(), "i2t", "i_id\tt_id", 2, budget));
        var references = new LinkedHashMap<String, StringTieGtf.Transcript>();
        annotation.transcripts().forEach(t -> references.put(t.id(), t));
        var transcripts = new ArrayList<TranscriptRow>();
        Set<String> seen = new HashSet<>(), contigs = new HashSet<>(); String activeContig = null;
        Table source = tables.get(1);
        for (Line line : data(source)) {
            checkpoint.run(); List<String> c = line.cells();
            int id = positive(c.get(0), source, line); String name = c.get(5);
            if (id != transcripts.size() + 1 || !seen.add(name)) throw error(source, line, "Require contiguous original transcript IDs and unique exact names");
            StringTieGtf.Transcript t = references.get(name);
            if (t == null) throw error(source, line, "Unexpected reference transcript: " + name);
            String contig = c.get(1); char strand = strand(c.get(2), source, line);
            int start = positive(c.get(3), source, line), end = positive(c.get(4), source, line);
            int exonCount = positive(c.get(6), source, line); long length = unsigned(c.get(7), Integer.MAX_VALUE, source, line);
            String gene = c.get(8), geneName = c.get(9), expectedName = geneName(t);
            if (!contig.equals(t.contig()) || strand != t.strand() || start != t.start1() || end != t.end1()
                    || !gene.equals(t.geneId()) || exonCount != t.exons().size() || length != t.exonicLength()
                    || !geneName.equals(expectedName == null ? "." : expectedName))
                throw error(source, line, "Original transcript identity/geometry/name differs from the complete reference: " + name);
            if (!contig.equals(activeContig)) {
                if (!contigs.add(contig)) throw error(source, line, "Reference contig reappears in original transcript order");
                activeContig = contig;
            }
            transcripts.add(new TranscriptRow(id, name, gene, geneName, contig, strand, start, end, exonCount, length,
                    printed(c.get(10), 6, false, source, line), printed(c.get(11), 6, false, source, line)));
        }
        if (!seen.equals(references.keySet())) throw new IOException("Original Ballgown transcript population omits reference identities; tables are never zero-filled");
        var exons = features(tables.get(2), true, budget);
        var introns = features(tables.get(3), false, budget);
        Topology topology = new Topology(budget);
        for (TranscriptRow row : transcripts) topology.transcript(row, references.get(row.transcriptId()));
        List<Link> exonLinks = topology.check(tables.get(4), exons, true);
        List<Link> intronLinks = topology.check(tables.get(5), introns, false);
        List<GeneRow> genes = genes(tables.get(0), references, budget);
        List<GeneRelation> relations = geneRelations(genes, transcripts, estimation, references, budget);
        checkpoint.run();
        return new Audit(tables, transcripts, exons, introns, genes, exonLinks, intronLinks, relations,
                coverage, budget, topology.wildcardLinks);
    }

    private static List<GeneRow> genes(Table table, Map<String, StringTieGtf.Transcript> reference, Budget budget) throws IOException {
        var groups = new LinkedHashMap<String, ArrayList<StringTieGtf.Transcript>>();
        for (var t : reference.values()) groups.computeIfAbsent(t.geneId(), k -> new ArrayList<>()).add(t);
        var rows = new ArrayList<GeneRow>(); var seen = new HashSet<String>();
        for (Line line : data(table)) {
            budget.checkpoint.run();
            List<String> c = line.cells(); String id = c.get(0); var members = groups.get(id);
            if (members == null || !seen.add(id)) throw error(table, line, "Unexpected/duplicate original reference gene: " + id);
            String contig = c.get(2); char strand = strand(c.get(3), table, line);
            int start = positive(c.get(4), table, line), end = positive(c.get(5), table, line);
            String expectedName = null;
            for (var t : members) {
                String name = geneName(t);
                if (name != null && expectedName != null && !expectedName.equals(name)) throw error(table, line, "Reference gene has conflicting names: " + id);
                if (name != null) expectedName = name;
                if (!t.contig().equals(contig) || t.strand() != strand) throw error(table, line, "Original gene contig/strand differs from reference: " + id);
            }
            if (start != members.stream().mapToInt(StringTieGtf.Transcript::start1).min().orElseThrow()
                    || end != members.stream().mapToInt(StringTieGtf.Transcript::end1).max().orElseThrow()
                    || (expectedName == null ? !Set.of("-", ".").contains(c.get(1)) : !expectedName.equals(c.get(1))))
                throw error(table, line, "Original gene span/name differs from reference: " + id);
            rows.add(new GeneRow(id, c.get(1), contig, strand, start, end,
                    printed(c.get(6), 6, true, table, line), printed(c.get(7), 6, true, table, line), printed(c.get(8), 6, true, table, line)));
        }
        if (!seen.equals(groups.keySet())) throw new IOException("Original gene table omits reference gene identities");
        return rows;
    }
    private static String geneName(StringTieGtf.Transcript t) throws IOException {
        String name = t.declaredTranscript() == null ? null : t.declaredTranscript().attribute("gene_name");
        for (var exon : t.exons()) {
            String value = exon.attribute("gene_name");
            if (value != null && name != null && !name.equals(value)) throw new IOException("Conflicting exact reference gene_name for " + t.id());
            if (value != null) name = value;
        }
        return name;
    }
    private static List<GeneRelation> geneRelations(List<GeneRow> genes, List<TranscriptRow> rows, StringTieGtf.Document estimation,
                                                    Map<String, StringTieGtf.Transcript> references, Budget budget) {
        var outputs = new HashMap<String, StringTieGtf.Transcript>();
        estimation.transcripts().forEach(t -> outputs.put(t.id(), t));
        var grouped = new LinkedHashMap<String, ArrayList<TranscriptRow>>();
        for (TranscriptRow row : rows) grouped.computeIfAbsent(row.geneId(), k -> new ArrayList<>()).add(row);
        var result = new ArrayList<GeneRelation>();
        for (GeneRow gene : genes) {
            budget.checkpoint.run();
            var intervals = new ArrayList<StringTieGtf.Feature>();
            BigDecimal weighted = BigDecimal.ZERO, fpkm = BigDecimal.ZERO, tpm = BigDecimal.ZERO;
            int emitted = 0, omitted = 0;
            for (TranscriptRow row : grouped.get(gene.id())) {
                intervals.addAll(references.get(row.transcriptId()).exons());
                weighted = weighted.add(row.coverage().value().multiply(BigDecimal.valueOf(row.length())));
                fpkm = fpkm.add(row.fpkm().value());
                var output = outputs.get(row.transcriptId());
                if (output != null) { tpm = tpm.add(output.abundance().tpm().value()); emitted++; }
                else omitted++;
            }
            intervals.sort(Comparator.comparingInt(StringTieGtf.Feature::start1).thenComparingInt(StringTieGtf.Feature::end1));
            long union = 0; int start = 0, end = 0;
            for (var exon : intervals) {
                if (start == 0) { start = exon.start1(); end = exon.end1(); }
                else if ((long)exon.start1() <= (long)end + 1) end = Math.max(end, exon.end1());
                else { union += (long)end - start + 1; start = exon.start1(); end = exon.end1(); }
            }
            if (start != 0) union += (long)end - start + 1;
            result.add(new GeneRelation(gene.id(), union, gene.coverage().value().multiply(BigDecimal.valueOf(union)).subtract(weighted),
                    gene.fpkm().value().subtract(fpkm), gene.tpm().value().subtract(tpm), emitted, omitted));
        }
        return result;
    }
    private static List<FeatureRow> features(Table table, boolean exon, Budget budget) throws IOException {
        var result = new ArrayList<FeatureRow>();
        for (Line line : data(table)) {
            budget.checkpoint.run(); List<String> c = line.cells(); int id = positive(c.get(0), table, line);
            if (id != result.size() + 1) throw error(table, line, "Require contiguous original feature IDs");
            int start = positive(c.get(3), table, line), end = positive(c.get(4), table, line);
            if (end < start) throw error(table, line, "Reversed original feature geometry");
            long count = unsigned(c.get(5), 0xffffffffL, table, line), unique = unsigned(c.get(6), 0xffffffffL, table, line);
            var weighted = printed(c.get(7), 2, false, table, line);
            if (unique > count || weighted.value().subtract(new BigDecimal("0.005")).compareTo(BigDecimal.valueOf(count)) > 0
                    || weighted.value().add(new BigDecimal("0.005")).compareTo(BigDecimal.valueOf(unique)) < 0)
                throw error(table, line, "Inconsistent original raw/unique/weighted read counts");
            var moments = new ArrayList<StringTieGtf.Printed>();
            if (exon) for (int i = 8; i < 12; i++) moments.add(printed(c.get(i), 4, false, table, line));
            if (result.size() >= budget.limits.maximumFeatures()) throw error(table, line, "Complete feature capacity exceeded");
            result.add(new FeatureRow(id, c.get(1), strand(c.get(2), table, line), start, end, count, unique, weighted, moments));
        }
        return result;
    }

    private static final class Topology {
        private final Budget budget;
        private final ArrayList<Expected> allExons = new ArrayList<>(), allIntrons = new ArrayList<>();
        private final ArrayList<Expected> sortedExons = new ArrayList<>(), sortedIntrons = new ArrayList<>();
        private String contig;
        private int wildcardLinks;
        private Topology(Budget budget) { this.budget = budget; }
        private void transcript(TranscriptRow row, StringTieGtf.Transcript reference) throws IOException {
            budget.checkpoint.run();
            if (!row.contig().equals(contig)) { sortedExons.clear(); sortedIntrons.clear(); contig = row.contig(); }
            int[] exonCache = {sortedExons.size() - 1}, intronCache = {sortedIntrons.size() - 1};
            int last = 0;
            for (var exon : reference.exons()) {
                add(row, exon.start1(), exon.end1(), sortedExons, allExons, exonCache);
                if (last != 0) {
                    if ((long)last + 1 >= exon.start1())
                        throw new IOException("Adjacent reference exons require separate original loader-normalization admission: " + reference.id());
                    add(row, last + 1, exon.start1() - 1, sortedIntrons, allIntrons, intronCache);
                }
                last = exon.end1();
            }
        }
        private void add(TranscriptRow row, int start, int end, ArrayList<Expected> sorted, ArrayList<Expected> all, int[] cache) throws IOException {
            budget.step(1); var segment = new Expected(row.contig(), row.strand(), start, end, row.id());
            int fit = cache[0] < 0 ? sorted.size() - 1 : cache[0]; Expected found = null;
            if (!sorted.isEmpty()) {
                if (less(segment, sorted.get(fit))) {
                    while (less(segment, sorted.get(fit)) || equal(segment, sorted.get(fit))) {
                        budget.step(1);
                        if (equal(segment, sorted.get(fit))) { found = sorted.get(fit); break; }
                        if (--fit < 0) break;
                    }
                    if (found == null) fit++;
                } else {
                    while (less(sorted.get(fit), segment) || equal(segment, sorted.get(fit))) {
                        budget.step(1);
                        if (equal(segment, sorted.get(fit))) { found = sorted.get(fit); break; }
                        if (++fit == sorted.size()) break;
                    }
                }
            }
            if (found == null) {
                if ((long)allExons.size() + allIntrons.size() >= budget.limits.maximumFeatures())
                    throw new IOException("Complete joint exon/intron topology capacity exceeded");
                fit = Math.max(0, fit); budget.step(sorted.size() - fit + 1L);
                sorted.add(fit, segment); all.add(segment); cache[0] = fit;
            } else {
                found.parents.add(row.id()); // Original source leaves cache unchanged on a match.
                if (found.strand != row.strand()) wildcardLinks++;
            }
        }
        private boolean less(Expected a, Expected b) throws IOException {
            budget.step(1);
            if (a.start != b.start) return a.start < b.start;
            if (a.end != b.end) return a.end < b.end;
            return a.strand != '.' && b.strand != '.' && a.strand < b.strand;
        }
        private boolean equal(Expected a, Expected b) throws IOException {
            budget.step(1);
            return a.start == b.start && a.end == b.end && (a.strand == b.strand || a.strand == '.' || b.strand == '.');
        }
        private List<Link> check(Table table, List<FeatureRow> observed, boolean exons) throws IOException {
            var expected = exons ? allExons : allIntrons;
            if (expected.size() != observed.size()) throw new IOException("Original feature population differs from replayed reference topology: " + table.name());
            var links = new ArrayList<Link>();
            for (int i = 0; i < expected.size(); i++) {
                budget.checkpoint.run(); Expected e = expected.get(i); FeatureRow o = observed.get(i);
                if (!e.contig.equals(o.contig()) || e.strand != o.strand() || e.start != o.start1() || e.end != o.end1())
                    throw new IOException("Original feature identity/strand differs from source insertion replay at " + table.name() + " feature " + (i + 1));
                for (int parent : e.parents) links.add(new Link(i + 1, parent));
            }
            List<Line> rows = data(table);
            if (rows.size() != links.size()) throw new IOException("Incomplete/extra original feature-to-transcript relation population: " + table.name());
            for (int i = 0; i < rows.size(); i++) {
                Line line = rows.get(i); var link = new Link(positive(line.cells().get(0), table, line), positive(line.cells().get(1), table, line));
                if (!link.equals(links.get(i))) throw error(table, line, "Original ordered feature-to-transcript relation differs from source topology");
            }
            return links;
        }
    }
    private static final class Expected {
        private final String contig; private final char strand; private final int start, end;
        private final ArrayList<Integer> parents = new ArrayList<>();
        private Expected(String contig, char strand, int start, int end, int parent) {
            this.contig = contig; this.strand = strand; this.start = start; this.end = end; parents.add(parent);
        }
    }

    private static Table read(Reader reader, String name, String header, int columns, Budget budget) throws IOException {
        var input = new BufferedReader(reader, 65536); var lines = new ArrayList<Line>();
        while (true) {
            budget.checkpoint.run(); var text = new StringBuilder(); String ending = ""; boolean eof = false;
            while (true) {
                int c = next(input, budget);
                if (c == -1) { eof = true; break; }
                if (c == '\n') { ending = "\n"; break; }
                if (c == '\r') { if (next(input, budget) != '\n') throw new IOException("Bare CR in original StringTie table: " + name); ending = "\r\n"; break; }
                if (c < 32 && c != '\t' || c == 127) throw new IOException("Control character in original StringTie table: " + name);
                if (text.length() >= budget.limits.maximumLineCharacters()) throw new IOException("Complete table line capacity exceeded: " + name);
                text.append((char)c);
            }
            if (eof && text.isEmpty()) break;
            if (++budget.rows > budget.limits.maximumRows()) throw new IOException("Complete shared StringTie table row capacity exceeded");
            String raw = text.toString();
            if (!StandardCharsets.UTF_8.newEncoder().canEncode(raw)) throw new IOException("Invalid Unicode in original table: " + name);
            String[] fields = raw.split("\t", -1);
            if (fields.length != columns || lines.isEmpty() && !raw.equals(header)) throw new IOException("Invalid original table header/column population: " + name);
            lines.add(new Line(lines.size() + 1L, raw, ending, List.of(fields)));
            if (eof) break;
        }
        if (lines.isEmpty()) throw new IOException("Missing complete original StringTie table header: " + name);
        return new Table(name, lines);
    }
    private static int next(Reader reader, Budget budget) throws IOException {
        int c = reader.read();
        if (c != -1 && ++budget.characters > budget.limits.maximumCharacters()) throw new IOException("Complete shared StringTie table character capacity exceeded");
        if ((budget.characters & 8191) == 0) budget.checkpoint.run();
        return c;
    }
    private static List<Line> data(Table table) { return table.lines().subList(1, table.lines().size()); }
    private static int positive(String value, Table table, Line line) throws IOException {
        long number = unsigned(value, Integer.MAX_VALUE, table, line);
        if (number == 0) throw error(table, line, "Require positive signed32 original identity/coordinate");
        return (int)number;
    }
    private static long unsigned(String value, long maximum, Table table, Line line) throws IOException {
        if (value.length() > 10 || !UINT.matcher(value).matches()) throw error(table, line, "Invalid original unsigned integer");
        try { long number = Long.parseLong(value); if (number > maximum) throw new NumberFormatException(); return number; }
        catch (NumberFormatException invalid) { throw error(table, line, "Original unsigned integer exceeds its domain"); }
    }
    private static char strand(String value, Table table, Line line) throws IOException {
        if (!Set.of("+", "-", ".").contains(value)) throw error(table, line, "Invalid original strand");
        return value.charAt(0);
    }
    private static StringTieGtf.Printed printed(String value, int scale, boolean literalZero, Table table, Line line) throws IOException {
        if (value.length() > 1024) throw error(table, line, "Complete original decimal capacity exceeded");
        try {
            var result = new StringTieGtf.Printed(value, new BigDecimal(value));
            if (result.value().scale() != scale && !(literalZero && value.equals("0.0"))) throw new IllegalArgumentException();
            return result;
        } catch (IllegalArgumentException invalid) { throw error(table, line, "Invalid original finite nonnegative decimal/precision"); }
    }
    private static IOException error(Table table, Line line, String reason) { return new IOException(reason + " at " + table.name() + " line " + line.number()); }
    private static final class Budget {
        private final Limits limits; private final Runnable checkpoint;
        private long characters, rows, work;
        private Budget(Limits limits, Runnable checkpoint) { this.limits = limits; this.checkpoint = checkpoint; }
        private void step(long count) throws IOException {
            if (count < 0 || count > limits.maximumTopologyWork() - work) throw new IOException("Complete source topology work capacity exceeded");
            long before = work; work += count;
            if ((work >>> 10) != (before >>> 10)) checkpoint.run();
        }
    }
}
