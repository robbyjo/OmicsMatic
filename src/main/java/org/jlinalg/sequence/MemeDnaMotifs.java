/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict, bounded reader for the documented minimal MEME DNA probability format. */
public final class MemeDnaMotifs {
    private MemeDnaMotifs() { }
    private static final Pattern PROPERTY = Pattern.compile("([A-Za-z]+)\\s*=\\s*([^\\s]+)");

    /**
     * Immutable motif with a defensive probability matrix.
     * @param id unique motif identifier
     * @param alternateId optional alternate identifier, or empty
     * @param sites positive site count; documented default 20
     * @param frequencies resolved A/C/G/T position probabilities
     * @param normalizedRows rows explicitly normalized by the reader
     */
    public record Motif(String id, String alternateId, double sites, double[][] frequencies, int normalizedRows) {
        /** Copies the supplied matrix. */
        public Motif { frequencies = copy(frequencies); }
        /** @return a defensive matrix copy */
        @Override public double[][] frequencies() { return copy(frequencies); }
    }

    /**
     * Parsed document; declared discovery strands are recorded, not applied to scanning.
     * @param motifs ordered motifs
     * @param background declared A/C/G/T background, or null if absent
     * @param declaredStrands declared discovery strand field, or unspecified
     * @param normalizedBackground whether explicit normalization changed the background
     */
    public record Document(List<Motif> motifs, double[] background, String declaredStrands, boolean normalizedBackground) {
        /** Makes defensive copies. */
        public Document { motifs = List.copyOf(motifs); background = background == null ? null : background.clone(); }
        /** @return a defensive background copy, or null */
        @Override public double[] background() { return background == null ? null : background.clone(); }
    }

    /**
     * Reads at most 16 MiB, 1000 motifs and 512 positions per motif. Only minimal,
     * plain-text DNA probability matrices with explicit width/alphabet are accepted.
     * Unrecognized sections fail; HTML, counts, log-odds and custom alphabets are not inferred.
     * @param path motif document
     * @param normalize explicit permission to normalize positive row/background sums
     * @return resolved motifs and declared metadata
     * @throws IOException on invalid or unsupported input
     */
    public static Document read(Path path, boolean normalize) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 16_777_216) throw new IOException("Require a regular minimal MEME file of at most 16 MiB");
        String text = Files.readString(path, StandardCharsets.US_ASCII);
        String[] lines = text.split("\\R", -1); var motifs = new ArrayList<Motif>(); var ids = new HashSet<String>();
        boolean version = false, alphabet = false, normalizedBackground = false; double[] background = null;
        String strands = "unspecified";
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim(); if (line.isEmpty() || line.startsWith("#")) continue;
            if (!version) {
                if (!line.matches("MEME version [0-9]+(?:\\.[0-9]+)*(?:\\s.*)?")) throw bad(i, "Expected MEME version header");
                version = true; continue;
            }
            if (line.startsWith("ALPHABET")) {
                if (alphabet || !motifs.isEmpty() || !line.matches("ALPHABET\\s*=\\s*ACGT")) throw bad(i, "Require one explicit ALPHABET= ACGT before motifs");
                alphabet = true; continue;
            }
            if (line.startsWith("strands:")) {
                if (!strands.equals("unspecified") || !motifs.isEmpty()) throw bad(i, "Duplicate or late strands field");
                strands = line.substring(8).trim().replaceAll("\\s+", " ");
                if (!strands.equals("+") && !strands.equals("+ -")) throw bad(i, "Unsupported discovery strand field");
                continue;
            }
            if (line.startsWith("Background letter frequencies")) {
                if (background != null || !motifs.isEmpty()) throw bad(i, "Duplicate or late background");
                var tokens = new ArrayList<String>();
                while (tokens.size() < 8 && ++i < lines.length) {
                    String row = lines[i].trim(); if (row.isEmpty() || row.startsWith("#")) continue;
                    tokens.addAll(List.of(row.split("\\s+")));
                }
                if (tokens.size() != 8) throw bad(i, "Require exactly A/C/G/T background pairs");
                double[] raw = new double[4];
                for (int b = 0; b < 4; b++) {
                    if (!tokens.get(2 * b).equals("ACGT".substring(b, b + 1))) throw bad(i, "Background must be ordered A C G T");
                    raw[b] = probability(tokens.get(2 * b + 1), i);
                    if (raw[b] == 0) throw bad(i, "Background probabilities must be positive");
                }
                double sum = sum(raw); normalizedBackground = normalize && sum != 1;
                background = resolve(raw, normalize, i); continue;
            }
            if (line.startsWith("URL ") && !motifs.isEmpty()) continue;
            if (!line.startsWith("MOTIF ")) throw bad(i, "Unsupported section; require minimal DNA probability format");
            if (!alphabet) throw bad(i, "Explicit ALPHABET= ACGT required");
            if (motifs.size() >= 1000) throw bad(i, "Maximum 1000 motifs per document");
            String[] names = line.split("\\s+"); if (names.length < 2 || names.length > 3) throw bad(i, "Require motif ID and optional alternate ID");
            for (int k = 1; k < names.length; k++) if (!names[k].matches("[!-~&&[^=]]+")) throw bad(i, "Require printable ASCII motif identifiers without '='");
            if (!ids.add(names[1])) throw bad(i, "Duplicate motif identifier: " + names[1]);
            do { i++; } while (i < lines.length && (lines[i].isBlank() || lines[i].stripLeading().startsWith("#")));
            if (i == lines.length || !lines[i].trim().startsWith("letter-probability matrix:")) throw bad(i, "Require a letter-probability matrix");
            String header = lines[i].trim().substring("letter-probability matrix:".length());
            var properties = properties(header,i);
            if (!properties.containsKey("w") || !"4".equals(properties.get("alength"))) throw bad(i, "Explicit alength=4 and w=width required");
            int width;
            try { width = Integer.parseInt(properties.get("w")); } catch (NumberFormatException failure) { throw bad(i, "Invalid motif width"); }
            if (width < 1 || width > 512) throw bad(i, "Motif width must be 1..512");
            double sites = properties.containsKey("nsites") ? positive(properties.get("nsites"), i) : 20;
            double[][] matrix = new double[width][4]; int normalizedRows = 0;
            for (int row = 0; row < width; row++) {
                if (++i >= lines.length) throw bad(i, "Truncated probability matrix");
                String[] values = lines[i].trim().split("\\s+"); if (values.length != 4) throw bad(i, "Require four probabilities per position");
                double[] raw = new double[4]; for (int b = 0; b < 4; b++) raw[b] = probability(values[b], i);
                if (normalize && sum(raw) != 1) normalizedRows++;
                matrix[row] = resolve(raw, normalize, i);
            }
            motifs.add(new Motif(names[1], names.length == 3 ? names[2] : "", sites, matrix, normalizedRows));
        }
        if (!version || !alphabet || motifs.isEmpty()) throw new IOException("Require MEME version, explicit DNA alphabet and at least one motif");
        return new Document(motifs, background, strands, normalizedBackground);
    }

    private static double[][] copy(double[][] values) { double[][] result = new double[values.length][]; for (int i = 0; i < values.length; i++) result[i] = values[i].clone(); return result; }
    private static IOException bad(int line, String message) { return new IOException("MEME line " + (line + 1) + ": " + message); }
    private static Map<String,String> properties(String header,int line) throws IOException {
        var result=new LinkedHashMap<String,String>();var matcher=PROPERTY.matcher(header);int end=0;
        while(matcher.find()) {
            if(!header.substring(end,matcher.start()).isBlank()) throw bad(line,"Unrecognized matrix header text");
            String key=matcher.group(1),value=matcher.group(2);
            if(!Set.of("alength","w","nsites","E","S").contains(key) || result.putIfAbsent(key,value)!=null) throw bad(line,"Unknown or duplicate matrix header property: "+key);
            if(key.equals("E") || key.equals("S")) {
                try { double score=Double.parseDouble(value);if(!Double.isFinite(score) || key.equals("E") && score<0) throw new NumberFormatException(); }
                catch(NumberFormatException failure) { throw bad(line,"Invalid motif metadata "+key); }
            }
            end=matcher.end();
        }
        if(!header.substring(end).isBlank()) throw bad(line,"Unrecognized matrix header text");
        return result;
    }
    private static double sum(double[] values) { double result = 0; for (double v : values) result += v; return result; }
    private static double positive(String text, int line) throws IOException {
        try { double value = Double.parseDouble(text); if (Double.isFinite(value) && value > 0) return value; } catch (NumberFormatException ignored) { }
        throw bad(line, "Require positive finite nsites");
    }
    private static double probability(String text, int line) throws IOException {
        try { double value = Double.parseDouble(text); if (Double.isFinite(value) && value >= 0 && value <= 1) return value; } catch (NumberFormatException ignored) { }
        throw bad(line, "Require a finite probability in [0,1]");
    }
    private static double[] resolve(double[] raw, boolean normalize, int line) throws IOException {
        double sum = sum(raw);
        if (!(sum > 0) || !normalize && Math.abs(sum - 1) > 1e-12) throw bad(line, "Probabilities must sum to one; rounded rows require explicit --normalize-probabilities true");
        if (normalize) for (int i = 0; i < raw.length; i++) raw[i] /= sum;
        return raw;
    }
}
