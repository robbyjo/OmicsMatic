/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lossless readers for original SnpEff ANN and 3.1i TXT annotations.
 * These methods do not predict effects, collapse transcripts, or assign burden classes. */
public final class SnpEffAnnotations {
    private static final int MAX_LINE = 16 * 1024 * 1024;
    private static final Set<String> ADDED_INFO = Set.of("ANN", "LOF", "NMD");
    /** The original ANN field order, without HGVS decoding or identifier repair. */
    public static final List<String> ANN_FIELDS = List.of("allele", "annotation", "impact",
        "gene_name", "gene_id", "feature_type", "feature_id", "transcript_biotype", "rank",
        "hgvs_c", "hgvs_p", "cdna_position_length", "cds_position_length", "aa_position_length",
        "distance", "errors_warnings_info");
    /** Exact original 3.1i TXT column names, including its spelling of Trancript_ID. */
    public static final String TXT_HEADER = "# Chromo\tPosition\tReference\tChange\tChange_type\tHomozygous\tQuality\tCoverage\tWarnings\tGene_ID\tGene_name\tBio_type\tTrancript_ID\tExon_ID\tExon_Rank\tEffect\told_AA/new_AA\tOld_codon/New_codon\tCodon_Num(CDS)\tCodon_Degeneracy\tCDS_size\tCodons_around\tAAs_around\tCustom_interval_ID";

    /** Annotation counts and original diagnostic tokens; an error must prevent admission. */
    public record Report(long inputRecords, long annotationRows, long diagnosticRows, long errorRows) { }
    /** Original ANN fields. Empty fields and combined SO terms remain unchanged. */
    public record Annotation(List<String> fields) {
        public Annotation {
            fields = List.copyOf(fields);
            if (fields.size() != 16) throw new IllegalArgumentException("ANN requires exactly 16 fields");
            if (fields.get(0).isEmpty() || (fields.get(1).isEmpty() && !fields.get(15).contains("ERROR_")))
                throw new IllegalArgumentException("ANN requires an allele and a consequence, or a retained original ERROR diagnostic");
            if (!Set.of("HIGH", "MODERATE", "LOW", "MODIFIER").contains(fields.get(2)))
                throw new IllegalArgumentException("Unknown original ANN impact: " + fields.get(2));
        }
        public String allele() { return fields.get(0); }
        public String diagnostics() { return fields.get(15); }
    }
    private SnpEffAnnotations() { }

    /** Parse the original representation; do not infer missense pathogenicity from MODERATE. */
    public static List<Annotation> parseAnn(String value) {
        if (value.isEmpty()) throw new IllegalArgumentException("Empty ANN annotation");
        List<Annotation> result = new ArrayList<>();
        for (String item : value.split(",", -1))
            result.add(new Annotation(Arrays.asList(item.split("\\|", -1))));
        return List.copyOf(result);
    }

    /** Check every original VCF record and flatten every transcript annotation.
     * This profile accepts literal DNA SNP/MNP/small-indel alleles, without symbolic SVs.
     * Output preserves input columns, sample values, INFO values and header metadata,
     * except original 5.2 QUAL reserialization through its parsed Java Double.
     * Only original ANN/LOF/NMD additions are permitted; existing annotations are refused. */
    public static Report auditVcf(Path input, Path annotated, Path table, Path diagnostics) throws IOException {
        try (BufferedReader source = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedReader result = Files.newBufferedReader(annotated, StandardCharsets.UTF_8);
             BufferedWriter flat = Files.newBufferedWriter(table, StandardCharsets.UTF_8);
             BufferedWriter messages = Files.newBufferedWriter(diagnostics, StandardCharsets.UTF_8)) {
            List<String> before = header(source), after = header(result);
            Map<String,Integer> beforeCounts = new LinkedHashMap<>(), afterCounts = new LinkedHashMap<>();
            before.forEach(value -> beforeCounts.merge(value, 1, Integer::sum));
            after.forEach(value -> afterCounts.merge(value, 1, Integer::sum));
            for (Map.Entry<String,Integer> entry : beforeCounts.entrySet())
                if (afterCounts.getOrDefault(entry.getKey(), 0) < entry.getValue()) throw new IOException("Original VCF header metadata was lost or changed");
            if (!before.get(before.size()-1).equals(after.get(after.size()-1)))
                throw new IOException("Original VCF sample header changed");
            int columns = before.get(before.size()-1).split("\t", -1).length;
            flat.write("record\tchrom\tposition\tid\treference\talternate\t" + String.join("\t", ANN_FIELDS) + "\n");
            messages.write("record\tchrom\tposition\toriginal_diagnostic\n");
            long records = 0, annotations = 0, diagnosticRows = 0, errors = 0;
            for (String a; (a = line(source)) != null;) {
                String b = line(result);
                if (b == null) throw new IOException("Original engine dropped an input VCF record");
                String[] x = vcf(a, columns), y = vcf(b, columns);
                records++;
                for (int i=0; i<x.length; i++) if (i != 7 && !(i == 5 ? quality(x[i]) : x[i]).equals(y[i]))
                    throw new IOException("VCF column " + (i+1) + " changed at record " + records);
                Map<String,String> original = info(x[7]), changed = info(y[7]);
                for (String key : ADDED_INFO) if (original.containsKey(key))
                    throw new IOException("Input already has " + key + "; supply an explicitly prepared unannotated VCF");
                String ann = changed.remove("ANN");
                changed.remove("LOF"); changed.remove("NMD");
                if (!original.equals(changed)) throw new IOException("Original INFO values changed at record " + records);
                if (ann == null) throw new IOException("Original engine produced no ANN at record " + records);
                Set<String> alleles = new HashSet<>(Arrays.asList(x[4].split(",", -1)));
                Set<String> observed = new HashSet<>();
                List<Annotation> parsed;
                try { parsed = parseAnn(ann); }
                catch (IllegalArgumentException error) { throw new IOException("Malformed ANN at record " + records, error); }
                for (Annotation annotation : parsed) {
                    if (!alleles.contains(annotation.allele()))
                        throw new IOException("ANN allele is absent from input ALT at record " + records);
                    observed.add(annotation.allele()); annotations++;
                    flat.write(records + "\t" + String.join("\t", Arrays.copyOf(x, 5)) + "\t" + String.join("\t", annotation.fields()) + "\n");
                    if (!annotation.diagnostics().isEmpty()) {
                        diagnosticRows++;
                        if (annotation.diagnostics().contains("ERROR")) errors++;
                        messages.write(records + "\t" + x[0] + "\t" + x[1] + "\t" + annotation.diagnostics() + "\n");
                    }
                }
                if (!observed.equals(alleles)) throw new IOException("An input ALT lacks an original annotation at record " + records);
            }
            if (line(result) != null) throw new IOException("Original engine added an unexpected VCF record");
            if (records == 0) throw new IOException("No input variants; no analysis result admitted");
            return new Report(records, annotations, diagnosticRows, errors);
        }
    }

    /** Read the 24-column 3.1i TXT format without treating it as ANN or restoring chr prefixes.
     * The original engine may normalize names and expand changes; this is a format/diagnostic
     * audit, not an independent input-to-output consequence or coverage classification. */
    public static Report auditTxt(Path input, Path annotated, Path table, Path diagnostics) throws IOException {
        long records = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            for (String raw; (raw = line(reader)) != null;) {
                if (raw.isBlank() || raw.startsWith("#")) continue;
                String[] fields = raw.split("\t", -1);
                if (fields.length < 4 || fields.length > 8 || fields[0].isBlank() || fields[2].isEmpty() || fields[3].isEmpty())
                    throw new IOException("Legacy TXT requires 4 through 8 declared columns");
                positive(fields[1]);
                if (fields.length >= 5 && !Set.of("+", "-").contains(fields[4]))
                    throw new IOException("Legacy TXT strand must be explicitly + or - when supplied");
                records++;
            }
        }
        long rows = 0, warnings = 0, errors = 0;
        try (BufferedReader reader = Files.newBufferedReader(annotated, StandardCharsets.UTF_8);
             BufferedWriter flat = Files.newBufferedWriter(table, StandardCharsets.UTF_8);
             BufferedWriter messages = Files.newBufferedWriter(diagnostics, StandardCharsets.UTF_8)) {
            String first = line(reader), second = line(reader), third = line(reader);
            if (first == null || !first.startsWith("# SnpEff version SnpEff 3.1i (build 2012-12-12)") || second == null || !second.startsWith("# Command line: ") || !TXT_HEADER.equals(third))
                throw new IOException("Unexpected original 3.1i TXT header");
            flat.write(TXT_HEADER.substring(2) + "\n");
            messages.write("annotation_row\tchrom\tposition\toriginal_diagnostic\n");
            for (String raw; (raw = line(reader)) != null;) {
                if (raw.isEmpty()) throw new IOException("Original 3.1i produced an empty annotation row; an input may have been rejected. Read original.stderr and check the reference database/input chromosome names");
                String[] fields = raw.split("\t", -1);
                if (fields.length != 24) throw new IOException("Original 3.1i TXT requires exactly 24 output columns");
                positive(fields[1]); rows++;
                flat.write(raw + "\n");
                if (!fields[8].isEmpty()) {
                    warnings++; if (fields[8].contains("ERROR")) errors++;
                    messages.write(rows + "\t" + fields[0] + "\t" + fields[1] + "\t" + fields[8] + "\n");
                }
            }
        }
        if (records == 0 || rows == 0) throw new IOException("No original TXT annotations; no result admitted");
        return new Report(records, rows, warnings, errors);
    }

    private static List<String> header(BufferedReader reader) throws IOException {
        List<String> result = new ArrayList<>();
        for (String raw; (raw = line(reader)) != null;) {
            if (!raw.startsWith("#")) throw new IOException("VCF header is missing #CHROM");
            result.add(raw);
            if (raw.startsWith("#CHROM\t")) {
                String[] fields = raw.split("\t", -1);
                if (fields.length < 8 || !String.join("\t", Arrays.copyOf(fields, 8)).equals("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO"))
                    throw new IOException("Invalid VCF column header");
                return result;
            }
        }
        throw new IOException("Missing VCF header");
    }
    private static String[] vcf(String raw, int columns) throws IOException {
        String[] fields = raw.split("\t", -1);
        if (fields.length != columns || fields[0].isBlank()) throw new IOException("Invalid VCF field count or chromosome");
        positive(fields[1]);
        if (!fields[3].matches("[ACGTN]+")) throw new IOException("This profile requires literal uppercase DNA REF alleles");
        Set<String> unique = new HashSet<>();
        for (String allele : fields[4].split(",", -1)) {
            if (!allele.matches("[ACGTN]+") || allele.equals(fields[3]) || !unique.add(allele))
                throw new IOException("Supply distinct literal DNA ALT alleles; symbolic SV/gVCF/breakend profiles are separate");
        }
        return fields;
    }
    private static Map<String,String> info(String value) throws IOException {
        Map<String,String> result = new LinkedHashMap<>();
        if (value.equals(".")) return result;
        for (String item : value.split(";", -1)) {
            int split = item.indexOf('=');
            String key = split < 0 ? item : item.substring(0, split);
            String data = split < 0 ? null : item.substring(split+1);
            if (key.isEmpty() || result.containsKey(key)) throw new IOException("Empty or duplicate INFO tag");
            result.put(key, data);
        }
        return result;
    }
    private static String quality(String value) throws IOException {
        if (value.equals(".")) return value;
        try {
            double parsed = Double.parseDouble(value);
            if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?") || !Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
            // Exact upstream VcfEntry.java: quality = Gpr.parseDoubleSafe(qStr),
            // then output (quality != null ? quality + "" : "."). Never rewrite output.
            return Double.toString(parsed);
        } catch (NumberFormatException error) { throw new IOException("VCF QUAL must be a finite nonnegative decimal or '.'", error); }
    }
    private static void positive(String value) throws IOException {
        try { if (!value.matches("[1-9][0-9]*") || Long.parseLong(value) > Integer.MAX_VALUE) throw new NumberFormatException(); }
        catch (NumberFormatException error) { throw new IOException("Require an original one-based positive integer position: " + value, error); }
    }
    private static String line(BufferedReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        for (int c; (c = reader.read()) != -1;) {
            if (c == '\n') return text.toString();
            if (c == '\r') {
                reader.mark(1); if (reader.read() != '\n') reader.reset();
                return text.toString();
            }
            if (text.length() >= MAX_LINE) throw new IOException("Line exceeds the documented 16 MiB annotation audit limit; use a separately qualified larger-line profile");
            text.append((char)c);
        }
        return text.isEmpty() ? null : text.toString();
    }
}
