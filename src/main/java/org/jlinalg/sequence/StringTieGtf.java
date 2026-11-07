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
 * Complete bounded structural audit of local GTF and original StringTie
 * transcript/exon output. Original lines, line endings, attribute order, repeated
 * auxiliary attributes and printed decimals remain visible. This class does not
 * assemble alignments, estimate expression or reproduce StringTie's merge model.
 * GFF3 and backslash-escaped GTF attribute values require separate contracts.
 */
public final class StringTieGtf {
    private StringTieGtf() { }
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.:-]*");
    private static final Pattern UINT = Pattern.compile("[0-9]+");
    private static final Pattern FIXED = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private static final Pattern SCORE = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Set<String> SINGLETON = Set.of("gene_id", "transcript_id", "gene_name", "ref_gene_id",
            "ref_gene_name", "reference_id", "exon_number", "cov", "FPKM", "TPM");

    /** Expected scientific role; a merge lacking abundances is never zero-filled. */
    public enum Profile { ANNOTATION, ASSEMBLY, ESTIMATION, MERGE }
    /** Complete line census, including preserved non-feature input. */
    public enum Kind { BLANK, COMMENT, FEATURE }
    /** Whether a missing original reference transcript rejects the estimation. */
    public enum PopulationPolicy { COMPLETE, RETAIN_OMISSIONS }
    /** Every reference identity receives an explicit result disposition. */
    public enum Disposition { EMITTED, OMITTED }

    /**
     * Complete capacities, never truncation settings. Retained text/objects also
     * consume heap; these limits do not guarantee an available JVM heap.
     */
    public record Limits(long maximumLines, int maximumFeatures, int maximumTranscripts,
                         int maximumExonsPerTranscript, int maximumAttributesPerFeature,
                         int maximumLineCharacters, long maximumCharacters) {
        /** Validate explicit positive, bounded capacities. */
        public Limits {
            if (maximumLines < 1 || maximumLines > 20_000_000 || maximumFeatures < 1 || maximumFeatures > 10_000_000
                    || maximumTranscripts < 1 || maximumTranscripts > 1_000_000
                    || maximumExonsPerTranscript < 1 || maximumExonsPerTranscript > 100_000
                    || maximumAttributesPerFeature < 1 || maximumAttributesPerFeature > 10_000
                    || maximumLineCharacters < 1 || maximumLineCharacters > 4_000_000
                    || maximumCharacters < 1 || maximumCharacters > 1_000_000_000)
                throw new IllegalArgumentException("Invalid complete StringTie GTF capacities");
        }
    }

    /** One exact quoted attribute value, in original source order. */
    public record Attribute(String name, String value) { }
    /** One GTF feature; raw tokens and exact attribute list are retained. */
    public record Feature(long lineNumber, String contig, String source, String type, int start1, int end1,
                          String score, char strand, String phase, List<Attribute> attributes) {
        /** Copy the complete attribute population. */
        public Feature { attributes = List.copyOf(attributes); }
        /** Return a unique attribute; absent is null, and repeated auxiliary keys are not collapsed. */
        public String attribute(String name) {
            String found = null;
            for (Attribute a : attributes) if (a.name().equals(name)) {
                if (found != null) throw new IllegalArgumentException("Repeated auxiliary GTF attribute: " + name);
                found = a.value();
            }
            return found;
        }
    }
    /** Exact original line, including LF/CRLF or absence of a final terminator. */
    public record Line(long number, Kind kind, String text, String ending, Feature feature) { }
    /** An original nonnegative finite fixed-point decimal; signed zero is retained in text. */
    public record Printed(String text, BigDecimal value) {
        /** Bind exact decimal tokens without converting through binary64. */
        public Printed {
            Objects.requireNonNull(text); Objects.requireNonNull(value);
            if (!FIXED.matcher(text).matches() || !new BigDecimal(text).equals(value)
                    || value.signum() < 0 || !Double.isFinite(value.doubleValue()))
                throw new IllegalArgumentException("Invalid original StringTie nonnegative printed value");
        }
    }
    /** Complete quantitative triple, or null when no complete triple was supplied. */
    public record Abundance(Printed coverage, Printed fpkm, Printed tpm) { }
    /**
     * One transcript in first-encounter order; exons are in genomic order while
     * original feature/line order remains in Document.lines. Annotation-only
     * spans may be derived from exons when no transcript feature was supplied.
     */
    public record Transcript(String id, String geneId, String contig, char strand, int start1, int end1,
                             Feature declaredTranscript, List<Feature> exons, long exonicLength,
                             Abundance abundance) {
        /** Copy all exons. */
        public Transcript { exons = List.copyOf(exons); }
    }
    /** Parsed documents can only be constructed through the complete reader. */
    public static final class Document {
        private final Profile profile;
        private final List<Line> lines;
        private final List<Transcript> transcripts;
        private final long characters;
        private Document(Profile profile, List<Line> lines, List<Transcript> transcripts, long characters) {
            this.profile = profile; this.lines = List.copyOf(lines); this.transcripts = List.copyOf(transcripts); this.characters = characters;
        }
        /** Declared input/output role. */
        public Profile profile() { return profile; }
        /** Every source line in source order. */
        public List<Line> lines() { return lines; }
        /** Every complete transcript in first-encounter order. */
        public List<Transcript> transcripts() { return transcripts; }
        /** All consumed UTF-16 code units, including original terminators. */
        public long characters() { return characters; }
    }
    /** One reference population row; omitted values remain absent, never measured zero. */
    public record ReferenceRow(String transcriptId, int referenceOrdinal, int outputOrdinal, Disposition disposition) { }
    /** Complete reference population plus its explicit coverage policy. */
    public record ReferenceAudit(PopulationPolicy policy, List<ReferenceRow> rows, int emitted, int omitted) {
        /** Copy every disposition. */
        public ReferenceAudit { rows = List.copyOf(rows); }
    }

    /**
     * Read the complete supplied GTF against an exact local reference dictionary.
     * Coordinates are one-based inclusive in the positive signed-32-bit domain.
     * Duplicate parent identities, contig/strand conflicts, missing/overlapping
     * exons, malformed numeric values and partial quantitative output are errors.
     * Unknown annotation features and repeated non-reserved attributes are
     * retained. Original StringTie output profiles admit transcript/exon only.
     * Caller owns byte limits, strict byte decoding and the Reader's lifetime.
     *
     * @param input complete decoded input
     * @param referenceLengths exact contig names and positive lengths, without aliases
     * @param profile explicit annotation/assembly/estimation/merge role
     * @param limits complete capacities
     * @param checkpoint cancellation/progress hook; unchecked cancellation propagates
     * @return complete structurally audited input, including empty output when declared
     * @throws IOException on syntax, identity, geometry, precision or capacity failure
     */
    public static Document read(Reader input, Map<String, Long> referenceLengths, Profile profile,
                                Limits limits, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(profile); Objects.requireNonNull(limits); Objects.requireNonNull(checkpoint);
        Map<String, Long> reference = Map.copyOf(referenceLengths);
        if (reference.isEmpty()) throw new IllegalArgumentException("Supply a complete nonempty reference dictionary");
        for (var e : reference.entrySet()) if (!identifier(e.getKey()) || e.getValue() < 1 || e.getValue() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid exact StringTie signed32 reference dictionary");
        var cursor = new Cursor(input, limits, checkpoint);
        var lines = new ArrayList<Line>();
        var builders = new LinkedHashMap<String, Builder>();
        int featureCount = 0;
        String[] raw;
        while ((raw = cursor.line()) != null) {
            long number = lines.size() + 1L;
            if (number > limits.maximumLines()) throw error(number, "Complete line capacity exceeded");
            String text = raw[0];
            if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw error(number, "Invalid Unicode in GTF");
            if (text.isBlank()) { lines.add(new Line(number, Kind.BLANK, text, raw[1], null)); continue; }
            if (text.startsWith("#")) { lines.add(new Line(number, Kind.COMMENT, text, raw[1], null)); continue; }
            if (++featureCount > limits.maximumFeatures()) throw error(number, "Complete feature capacity exceeded");
            Feature f = feature(text, number, reference, limits.maximumAttributesPerFeature());
            lines.add(new Line(number, Kind.FEATURE, text, raw[1], f));
            boolean transcript = f.type().equals("transcript"), exon = f.type().equals("exon");
            if (!transcript && !exon) {
                if (profile != Profile.ANNOTATION) throw error(number, "Original output requires transcript/exon features; retain other modes separately");
                continue;
            }
            String gene = requiredId(f, "gene_id"), id = requiredId(f, "transcript_id");
            Builder b = builders.get(id);
            if (b == null) {
                if (builders.size() >= limits.maximumTranscripts()) throw error(number, "Complete transcript capacity exceeded");
                b = new Builder(id, gene, f.contig(), f.strand()); builders.put(id, b);
            }
            if (!b.gene.equals(gene) || !b.contig.equals(f.contig()) || b.strand != f.strand())
                throw error(number, "Transcript parent/contig/strand conflict for " + id);
            if (transcript) {
                if (b.header != null) throw error(number, "Duplicate transcript feature for " + id);
                b.header = f;
            } else {
                if (b.exons.size() >= limits.maximumExonsPerTranscript()) throw error(number, "Complete per-transcript exon capacity exceeded");
                b.exons.add(f);
            }
        }
        var transcripts = new ArrayList<Transcript>();
        var genes = new HashMap<String, String>();
        for (Builder b : builders.values()) {
            checkpoint.run();
            if (b.exons.isEmpty()) throw error(b.header.lineNumber(), "Transcript has no complete exon population: " + b.id);
            b.exons.sort(Comparator.comparingInt(Feature::start1).thenComparingInt(Feature::end1));
            int previous = 0; long length = 0;
            for (int i = 0; i < b.exons.size(); i++) {
                Feature exon = b.exons.get(i);
                if (exon.start1() <= previous) throw error(exon.lineNumber(), "Duplicate/overlapping exons for " + b.id);
                previous = exon.end1(); length = Math.addExact(length, (long)exon.end1() - exon.start1() + 1);
                String ordinal = exon.attribute("exon_number");
                if (ordinal != null && (!UINT.matcher(ordinal).matches() || ordinal.length() > 10 || Long.parseLong(ordinal) < 1))
                    throw error(exon.lineNumber(), "Invalid positive exon_number");
                if (profile != Profile.ANNOTATION && (ordinal == null || Long.parseLong(ordinal) != i + 1L))
                    throw error(exon.lineNumber(), "Original output exon_number must follow genomic exon order");
            }
            int start = b.exons.get(0).start1(), end = b.exons.get(b.exons.size() - 1).end1();
            if (b.header == null && profile != Profile.ANNOTATION) throw error(b.exons.get(0).lineNumber(), "Output exon has no transcript feature: " + b.id);
            if (b.header != null && (b.header.start1() != start || b.header.end1() != end))
                throw error(b.header.lineNumber(), "Transcript span differs from its complete exon span: " + b.id);
            String locus = b.contig + "\t" + b.strand;
            String old = genes.putIfAbsent(b.gene, locus);
            if (old != null && !old.equals(locus)) throw error(b.exons.get(0).lineNumber(), "Gene parent has conflicting contig/strand: " + b.gene);
            Abundance abundance = abundance(b.header, profile);
            transcripts.add(new Transcript(b.id, b.gene, b.contig, b.strand, start, end, b.header, b.exons, length, abundance));
        }
        if (profile == Profile.ANNOTATION && transcripts.isEmpty())
            throw new IOException("Reference annotation has no complete exon-bearing transcripts");
        checkpoint.run();
        return new Document(profile, lines, transcripts, cursor.characters);
    }

    /**
     * Match every original estimation identity and exon to its supplied reference.
     * Extra identities or modified geometry always reject the result. COMPLETE
     * also rejects missing targets; RETAIN_OMISSIONS emits their explicit absent
     * disposition without inventing expression values.
     * @param output complete ESTIMATION result
     * @param reference complete ANNOTATION input
     * @param policy explicit omission handling
     * @return complete reference ledger in original reference encounter order
     * @throws IOException for population/geometry mismatch
     */
    public static ReferenceAudit auditEstimation(Document output, Document reference, PopulationPolicy policy) throws IOException {
        Objects.requireNonNull(output); Objects.requireNonNull(reference); Objects.requireNonNull(policy);
        if (output.profile() != Profile.ESTIMATION || reference.profile() != Profile.ANNOTATION)
            throw new IllegalArgumentException("Require original ESTIMATION output and its ANNOTATION reference");
        var expected = new LinkedHashMap<String, Transcript>();
        reference.transcripts().forEach(t -> expected.put(t.id(), t));
        var observed = new HashMap<String, Integer>();
        for (int i = 0; i < output.transcripts().size(); i++) {
            Transcript t = output.transcripts().get(i), e = expected.get(t.id());
            if (e == null) throw new IOException("Unexpected original estimation transcript: " + t.id());
            if (!t.geneId().equals(e.geneId()) || !t.contig().equals(e.contig()) || t.strand() != e.strand()
                    || t.start1() != e.start1() || t.end1() != e.end1() || t.exons().size() != e.exons().size())
                throw new IOException("Estimation transcript identity/geometry differs from reference: " + t.id());
            for (int j = 0; j < t.exons().size(); j++) if (t.exons().get(j).start1() != e.exons().get(j).start1()
                    || t.exons().get(j).end1() != e.exons().get(j).end1())
                throw new IOException("Estimation exon differs from reference: " + t.id());
            observed.put(t.id(), i + 1);
        }
        var rows = new ArrayList<ReferenceRow>(); int missing = 0;
        for (Transcript t : reference.transcripts()) {
            Integer ordinal = observed.get(t.id());
            if (ordinal == null) missing++;
            rows.add(new ReferenceRow(t.id(), rows.size() + 1, ordinal == null ? 0 : ordinal, ordinal == null ? Disposition.OMITTED : Disposition.EMITTED));
        }
        if (missing > 0 && policy == PopulationPolicy.COMPLETE)
            throw new IOException("Original estimation omitted " + missing + " reference transcripts; preserve output, never infer zero");
        return new ReferenceAudit(policy, rows, observed.size(), missing);
    }

    private static Abundance abundance(Feature header, Profile profile) throws IOException {
        if (header == null) return null;
        Printed cov = printed(header, "cov"), fpkm = printed(header, "FPKM"), tpm = printed(header, "TPM");
        boolean all = cov != null && fpkm != null && tpm != null, none = cov == null && fpkm == null && tpm == null;
        // A guide may carry only selected annotations (for example a source
        // FPKM threshold). Keep all validated attributes without inventing the
        // absent members of a quantitative triple.
        if (profile == Profile.ANNOTATION) return all ? new Abundance(cov, fpkm, tpm) : null;
        if (!all && !none) throw error(header.lineNumber(), "Partial transcript cov/FPKM/TPM attributes");
        if (!all && (profile == Profile.ASSEMBLY || profile == Profile.ESTIMATION))
            throw error(header.lineNumber(), "Original quantitative output lacks complete cov/FPKM/TPM");
        if (all && profile == Profile.MERGE) throw error(header.lineNumber(), "Merge output unexpectedly reports abundance; do not conflate modes");
        return all ? new Abundance(cov, fpkm, tpm) : null;
    }
    private static Printed printed(Feature f, String name) throws IOException {
        String text = f.attribute(name);
        if (text == null) return null;
        if (text.length() > 1024) throw error(f.lineNumber(), "Printed quantitative token exceeds complete decimal capacity");
        try { return new Printed(text, new BigDecimal(text)); }
        catch (IllegalArgumentException invalid) { throw error(f.lineNumber(), "Invalid nonnegative finite " + name + " attribute"); }
    }
    private static Feature feature(String text, long number, Map<String, Long> reference, int maximumAttributes) throws IOException {
        String[] f = text.split("\t", 10);
        if (f.length != 9) throw error(number, "Require exactly nine tab-delimited GTF fields; GFF3 is not GTF");
        Long length = reference.get(f[0]);
        if (length == null) throw error(number, "Contig is absent from the exact reference: " + f[0]);
        if (!identifier(f[1]) || !NAME.matcher(f[2]).matches()) throw error(number, "Invalid GTF source/feature token");
        int start = coordinate(f[3], number), end = coordinate(f[4], number);
        if (end < start || end > length) throw error(number, "GTF interval is outside the exact reference");
        if (!f[5].equals(".")) {
            if (f[5].length() > 128 || !SCORE.matcher(f[5]).matches()) throw error(number, "GTF score must be a bounded decimal or missing dot");
            try { if (!Double.isFinite(Double.parseDouble(f[5]))) throw new NumberFormatException(); }
            catch (NumberFormatException invalid) { throw error(number, "GTF score must be finite or missing dot"); }
        }
        if (!Set.of("+", "-", ".").contains(f[6]) || !Set.of(".", "0", "1", "2").contains(f[7]))
            throw error(number, "Invalid GTF strand/phase");
        List<Attribute> attributes = attributes(f[8], number, maximumAttributes);
        Feature result = new Feature(number, f[0], f[1], f[2], start, end, f[5], f[6].charAt(0), f[7], attributes);
        // Every supplied quantitative field is validated, including exon cov and
        // unrelated annotation feature attributes. No malformed value disappears.
        for (String name : List.of("cov", "FPKM", "TPM")) printed(result, name);
        for (String name : List.of("gene_id", "transcript_id")) {
            String value = result.attribute(name);
            if (value != null && !identifier(value)) throw error(number, "Invalid unique parent identifier: " + name);
        }
        return result;
    }
    private static List<Attribute> attributes(String text, long number, int maximum) throws IOException {
        var values = new ArrayList<Attribute>(); var seen = new HashSet<String>(); int i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') i++;
            if (i == text.length()) break;
            int start = i;
            while (i < text.length() && text.charAt(i) != ' ') i++;
            String name = text.substring(start, i);
            if (!NAME.matcher(name).matches() || i == text.length()) throw error(number, "Malformed quoted GTF attribute key");
            while (i < text.length() && text.charAt(i) == ' ') i++;
            if (i == text.length() || text.charAt(i++) != '"') throw error(number, "Require quoted GTF attributes; GFF3 is not GTF");
            start = i;
            while (i < text.length() && text.charAt(i) != '"') {
                char c = text.charAt(i++);
                if (c == '\\' || Character.isISOControl(c)) throw error(number, "Escaped/control GTF attribute values need a separate contract");
            }
            if (i == text.length()) throw error(number, "Unterminated quoted GTF attribute");
            String value = text.substring(start, i++);
            while (i < text.length() && text.charAt(i) == ' ') i++;
            if (i == text.length() || text.charAt(i++) != ';') throw error(number, "Every quoted GTF attribute requires a terminating semicolon");
            if (SINGLETON.contains(name) && !seen.add(name)) throw error(number, "Duplicate reserved GTF attribute: " + name);
            if (values.size() >= maximum) throw error(number, "Complete attribute capacity exceeded");
            values.add(new Attribute(name, value));
        }
        if (values.isEmpty()) throw error(number, "GTF attribute population is empty");
        return values;
    }
    private static int coordinate(String text, long number) throws IOException {
        if (text.length() > 10 || !UINT.matcher(text).matches()) throw error(number, "Require positive signed32 integer GTF coordinates");
        try { int value = Integer.parseInt(text); if (value < 1) throw new NumberFormatException(); return value; }
        catch (NumberFormatException invalid) { throw error(number, "Require positive signed32 integer GTF coordinates"); }
    }
    private static String requiredId(Feature f, String name) throws IOException {
        String id = f.attribute(name);
        if (id == null || !identifier(id)) throw error(f.lineNumber(), "Require unique quoted " + name);
        return id;
    }
    private static boolean identifier(String text) {
        return text != null && !text.isEmpty() && StandardCharsets.UTF_8.newEncoder().canEncode(text)
                && text.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c) || c == '"' || c == '\\');
    }
    private static IOException error(long line, String reason) { return new IOException(reason + " at GTF line " + line); }
    private static final class Builder {
        private final String id, gene, contig; private final char strand;
        private Feature header; private final ArrayList<Feature> exons = new ArrayList<>();
        private Builder(String id, String gene, String contig, char strand) { this.id = id; this.gene = gene; this.contig = contig; this.strand = strand; }
    }
    private static final class Cursor {
        private final Reader reader; private final Limits limits; private final Runnable checkpoint;
        private long characters;
        private Cursor(Reader reader, Limits limits, Runnable checkpoint) {
            this.reader = new BufferedReader(reader, 65536); this.limits = limits; this.checkpoint = checkpoint;
        }
        private int next() throws IOException {
            int c = reader.read();
            if (c != -1 && ++characters > limits.maximumCharacters()) throw new IOException("Complete GTF character capacity exceeded");
            if ((characters & 8191) == 0) checkpoint.run();
            return c;
        }
        private String[] line() throws IOException {
            checkpoint.run(); var text = new StringBuilder();
            while (true) {
                int c = next();
                if (c == -1) return text.isEmpty() ? null : new String[] {text.toString(), ""};
                if (c == '\n') return new String[] {text.toString(), "\n"};
                if (c == '\r') {
                    if (next() != '\n') throw new IOException("Require LF or CRLF GTF line endings; bare CR is not accepted");
                    return new String[] {text.toString(), "\r\n"};
                }
                if (c < 32 && c != '\t' || c == 127) throw new IOException("Control character in GTF line");
                if (text.length() >= limits.maximumLineCharacters()) throw new IOException("Complete GTF line character capacity exceeded");
                text.append((char)c);
            }
        }
    }
}
