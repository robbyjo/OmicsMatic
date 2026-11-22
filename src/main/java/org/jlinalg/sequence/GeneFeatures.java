/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Exact-reference GTF/SAF features, union lengths and transcript splice boundaries. */
public final class GeneFeatures {
    private GeneFeatures() { }
    /** Coordinates are one-based inclusive, as in GTF and SAF. */
    public record Feature(String id, String chrom, int start, int end, String strand, String transcript) { }
    public record Annotation(List<Feature> features, Map<String, Long> unionLengths) {
        public Annotation { if (features.isEmpty()||unionLengths.isEmpty()) throw new IllegalArgumentException("Annotation requires features and union lengths"); features = List.copyOf(features); unionLengths = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(unionLengths)); }
    }
    /** HISAT2 flanking exonic bases, both zero-based; right is not an exclusive intron end. */
    public record SpliceSite(String chrom, int left, int right, String strand) { }

    public static Annotation read(Path file, String format, Set<String> types, String attribute,
                                  Map<String, Long> reference) throws IOException {
        if (!Set.of("GTF", "SAF").contains(format) || reference.isEmpty()) throw new IllegalArgumentException("Require GTF or SAF and an exact reference dictionary");
        if (types.isEmpty() || !attribute.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Require selected feature types and a valid GTF grouping attribute");
        var features = new ArrayList<Feature>();
        Pattern grouping=attributePattern(attribute),transcripts=attributePattern("transcript_id");
        long n = 0;
        try (var input = reader(file)) {
            String line; boolean header = false;
            while ((line = input.readLine()) != null) {
                n++;
                if (line.isBlank() || line.startsWith("#")) continue;
                if (line.length() > 2_000_000 || features.size() >= 20_000_000) throw error(n, "Annotation exceeds row/feature bounds");
                String[] f = line.split("\t", -1);
                if (format.equals("SAF") && !header) {
                    if (!line.equals("GeneID\tChr\tStart\tEnd\tStrand")) throw error(n, "SAF requires exact GeneID, Chr, Start, End, Strand header");
                    header = true; continue;
                }
                if (f.length != (format.equals("GTF") ? 9 : 5)) throw error(n, "Invalid annotation field count");
                String chrom = f[format.equals("GTF") ? 0 : 1];
                int start = coordinate(f[format.equals("GTF") ? 3 : 2], n), end = coordinate(f[format.equals("GTF") ? 4 : 3], n);
                String strand = f[format.equals("GTF") ? 6 : 4];
                Long length = reference.get(chrom);
                if (length == null) throw error(n, "Annotation contig is absent from the exact reference: " + chrom);
                if (end < start || end > length || !Set.of("+", "-", ".").contains(strand)) throw error(n, "Invalid annotation interval or strand");
                if (format.equals("GTF") && !types.contains(f[2])) continue;
                String id = format.equals("GTF") ? attribute(f[8], attribute, grouping, true, n) : f[0];
                String transcript = format.equals("GTF") ? attribute(f[8], "transcript_id", transcripts, false, n) : null;
                if (id.isBlank() || id.chars().anyMatch(Character::isWhitespace) || id.indexOf(';') >= 0) throw error(n, "Feature IDs must be nonempty without whitespace or semicolons");
                features.add(new Feature(id, chrom, start, end, strand, transcript));
            }
        }
        if (features.isEmpty()) throw new IOException("Annotation has no selected features");
        return new Annotation(features, unionLengths(features));
    }
    private static BufferedReader reader(Path file) throws IOException {
        var raw = Files.newInputStream(file);
        try { return new BufferedReader(new InputStreamReader(file.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gz") ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8)); }
        catch (IOException | RuntimeException failure) { raw.close(); throw failure; }
    }
    private static Pattern attributePattern(String name) { return Pattern.compile("(?:^|;)\\s*" + Pattern.quote(name) + "\\s+\"([^\"]+)\"\\s*(?=;|$)"); }
    private static String attribute(String text, String name, Pattern pattern, boolean required, long n) throws IOException {
        var matcher = pattern.matcher(text);
        if (!matcher.find()) { if (required) throw error(n, "Selected GTF feature lacks quoted " + name + "; GFF3 is not GTF"); return null; }
        String value = matcher.group(1);
        if (matcher.find()) throw error(n, "Duplicate GTF attribute " + name);
        return value;
    }
    private static int coordinate(String text, long n) throws IOException {
        try { int value = Integer.parseInt(text); if (value < 1) throw new NumberFormatException(); return value; }
        catch (NumberFormatException failure) { throw error(n, "Coordinates must be positive signed-32-bit integers"); }
    }
    private static Map<String, Long> unionLengths(List<Feature> features) {
        var groups = new LinkedHashMap<String, Map<String, List<Feature>>>();
        for (var f : features) groups.computeIfAbsent(f.id(), k -> new HashMap<>()).computeIfAbsent(f.chrom(), k -> new ArrayList<>()).add(f);
        var lengths = new LinkedHashMap<String, Long>();
        for (var gene : groups.entrySet()) {
            long length = 0;
            for (var intervals : gene.getValue().values()) {
                intervals.sort(Comparator.comparingInt(Feature::start).thenComparingInt(Feature::end));
                int start = intervals.get(0).start(), end = intervals.get(0).end();
                for (var f : intervals.subList(1, intervals.size())) {
                    if ((long)f.start() <= (long)end + 1) end = Math.max(end, f.end());
                    else { length = Math.addExact(length, (long)end - start + 1); start = f.start(); end = f.end(); }
                }
                length = Math.addExact(length, (long)end - start + 1);
            }
            lengths.put(gene.getKey(), length);
        }
        return lengths;
    }
    /** Merge overlapping/adjacent transcript exons; suppress introns shorter than minimumIntron bases.
     * Conflicting transcript parents/contigs/strands are rejected, rather than silently mixed.
     */
    public static List<SpliceSite> spliceSites(Annotation annotation, int minimumIntron) throws IOException {
        if (minimumIntron < 1) throw new IllegalArgumentException("minimumIntron must be positive");
        var transcripts = new LinkedHashMap<String, List<Feature>>();
        for (var f : annotation.features()) {
            if (f.transcript() == null || f.transcript().isBlank() || !Set.of("+", "-").contains(f.strand())) throw new IOException("Splice extraction requires transcript_id and known +/- strand for every selected exon");
            transcripts.computeIfAbsent(f.transcript(), k -> new ArrayList<>()).add(f);
        }
        var result = new TreeSet<SpliceSite>(Comparator.comparing(SpliceSite::chrom).thenComparingInt(SpliceSite::left).thenComparingInt(SpliceSite::right).thenComparing(SpliceSite::strand));
        for (var exons : transcripts.values()) {
            var first = exons.get(0);
            if (exons.stream().anyMatch(f -> !f.id().equals(first.id()) || !f.chrom().equals(first.chrom()) || !f.strand().equals(first.strand()))) throw new IOException("Transcript has conflicting gene, contig or strand: " + first.transcript());
            exons.sort(Comparator.comparingInt(Feature::start).thenComparingInt(Feature::end));
            int end = exons.get(0).end();
            for (var f : exons.subList(1, exons.size())) {
                if ((long)f.start() - end - 1 >= minimumIntron) result.add(new SpliceSite(f.chrom(), end - 1, f.start() - 1, f.strand()));
                end = Math.max(end, f.end());
            }
        }
        return List.copyOf(result);
    }
    public static void writeSaf(Annotation annotation, Path output) throws IOException {
        try (var out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            out.write("GeneID\tChr\tStart\tEnd\tStrand\n");
            for (var f : annotation.features()) out.write(f.id()+"\t"+f.chrom()+"\t"+f.start()+"\t"+f.end()+"\t"+f.strand()+"\n");
        }
    }
    public static void writeSpliceSites(List<SpliceSite> sites, Path output) throws IOException {
        try (var out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (var s : sites) out.write(s.chrom()+"\t"+s.left()+"\t"+s.right()+"\t"+s.strand()+"\n");
        }
    }
    /** HISAT2 graph-index exon union: zero-based inclusive bounds, merged across genes/strands.
     * The representative strand is retained for the upstream four-column helper format.
     */
    public static void writeIndexExons(Annotation annotation, Path output) throws IOException {
        var features=new ArrayList<>(annotation.features());
        features.sort(Comparator.comparing(Feature::chrom).thenComparingInt(Feature::start).thenComparingInt(Feature::end).thenComparing(Feature::strand));
        try (var out=Files.newBufferedWriter(output,StandardCharsets.UTF_8)) {
            Feature first=features.get(0);String chrom=first.chrom(),strand=first.strand();int start=first.start(),end=first.end();
            for (var f:features.subList(1,features.size())) {
                if (f.chrom().equals(chrom)&&f.start()<=end) { end=Math.max(end,f.end()); if (strand.equals(".")) strand=f.strand(); }
                else { out.write(chrom+"\t"+(start-1)+"\t"+(end-1)+"\t"+strand+"\n");chrom=f.chrom();strand=f.strand();start=f.start();end=f.end(); }
            }
            out.write(chrom+"\t"+(start-1)+"\t"+(end-1)+"\t"+strand+"\n");
        }
    }
    private static IOException error(long n, String text) { return new IOException(text+" at annotation line "+n); }
}
