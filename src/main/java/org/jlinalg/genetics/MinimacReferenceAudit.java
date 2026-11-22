/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Native audit of the MVCFv3 text exported from a Minimac4 MSAV reference. */
public final class MinimacReferenceAudit {
    private MinimacReferenceAudit() { }

    /** Immutable compressed haplotype mapping for one block, in original sample order. */
    public record Block(String contig, int start, int end, int variants, int representatives,
            List<List<Integer>> sampleMappings) {
        public Block { sampleMappings = sampleMappings.stream().map(List::copyOf).toList(); }
    }

    /** One biallelic reference row; absent HMM parameters remain absent. */
    public record Variant(Block block, int position, String id, String reference, String alternate,
            List<Integer> uniqueAlleles, int alternateCopies, int totalCopies,
            OptionalDouble error, OptionalDouble recombination, OptionalDouble centimorgans) {
        public Variant { uniqueAlleles = List.copyOf(uniqueAlleles); }

        /**
         * Expand one sample's ordered haplotypes, without inferring sex or phase.
         * @param sample zero-based index in the summary sample order
         * @return one or two explicit reference alleles, each 0 or 1
         */
        public List<Integer> phasedAlleles(int sample) {
            return block.sampleMappings().get(sample).stream().map(uniqueAlleles::get).toList();
        }
    }

    /** Contig-specific ploidy; a change within a contig is rejected and must be split explicitly. */
    public record Contig(String id, List<Integer> samplePloidies, long blocks, long variants) {
        public Contig { samplePloidies = List.copyOf(samplePloidies); }
    }

    /** Counts and a canonical identity/phase fingerprint; not an assembly or panel certification. */
    public record Summary(List<String> samples, List<Contig> contigs, long blocks, long variants,
            long haplotypeCells, long errorParameters, long recombinationParameters,
            String phasedContentSha256) {
        public Summary { samples = List.copyOf(samples); contigs = List.copyOf(contigs); }
    }

    /** A streaming consumer. Rows are provisional until the entire audit returns successfully. */
    @FunctionalInterface
    public interface Visitor {
        /**
         * Inspect a validated row; later rows can still fail the audit.
         * @param variant immutable compressed row and original ordered sample mapping
         * @throws IOException if downstream admission fails
         */
        void accept(Variant variant) throws IOException;
    }

    /**
     * Audit a complete UTF-8 MVCFv3 export without retaining all variants in memory.
     * @param exportedMvcf plain text from the original Savvy exporter with {@code --update-info never}
     * @return validated full-file inventory and phase fingerprint
     * @throws IOException on malformed, unsupported, truncated or inconsistent input
     */
    public static Summary audit(Path exportedMvcf) throws IOException {
        return read(exportedMvcf, variant -> { });
    }

    /**
     * Audit and stream a complete MVCFv3 export. Requires explicit full phasing,
     * biallelic literal alleles, complete haploid/diploid mappings, sorted contigs,
     * consistent per-contig ploidy, exact block extents/counts and AC/AN agreement.
     * Duplicate variant keys are rejected, including repeated block-boundary rows;
     * no deduplication or allele transformation is performed. Legacy M3VCF and the
     * binary MSAV container itself are not accepted by this text reader.
     *
     * @param exportedMvcf original plain UTF-8 MVCFv3 export; never recompressed or modified here
     * @param visitor streaming row consumer; its results remain provisional until success
     * @return full-file counts, sample/ploidy inventory and canonical phased-content hash
     * @throws IOException on invalid input or a consumer failure
     */
    public static Summary read(Path exportedMvcf, Visitor visitor) throws IOException {
        if (visitor == null) throw new IllegalArgumentException("Missing reference visitor");
        try (var input = new Lines(exportedMvcf)) {
            boolean mvcf = false, vcf = false, phase = false;
            String text;
            var samples = new ArrayList<String>();
            var headerKeys = new HashSet<String>();
            while ((text = input.next()) != null && text.startsWith("##")) {
                int equals = text.indexOf('=');
                if (equals < 3) throw input.error("Malformed metadata line");
                String key = text.substring(2, equals), value = text.substring(equals + 1);
                if (List.of("subfileformat", "fileformat", "phasing").contains(key)) {
                    if (!headerKeys.add(key)) throw input.error("Repeated format/phasing metadata");
                    switch (key) {
                        case "subfileformat" -> mvcf = value.equals("MVCFv3.0");
                        case "fileformat" -> vcf = value.equals("VCFv4.2") || value.equals("VCFv4.3");
                        case "phasing" -> phase = value.equals("full");
                        default -> throw new AssertionError(key);
                    }
                }
            }
            if (!mvcf || !vcf || !phase || text == null) throw input.error("Require MVCFv3.0, VCFv4.2/4.3 and phasing=full");
            String[] header = text.split("\t", -1);
            String[] fixed = {"#CHROM", "POS", "ID", "REF", "ALT", "QUAL", "FILTER", "INFO", "FORMAT"};
            if (header.length < 10 || header.length > 1000009) throw input.error("Require 1..1000000 reference samples");
            for (int i = 0; i < fixed.length; i++) if (!header[i].equals(fixed[i])) throw input.error("Invalid MVCF column header");
            var uniqueSamples = new HashSet<String>();
            for (int i = 9; i < header.length; i++) {
                token(header[i], input);
                if (!uniqueSamples.add(header[i])) throw input.error("Duplicate reference sample");
                samples.add(header[i]);
            }
            MessageDigest digest = digest();
            update(digest, "MVCFv3-phase-v1\n");
            for (String sample : samples) update(digest, sample + "\n");
            var contigs = new ArrayList<Contig>();
            var seenContigs = new HashSet<String>();
            String contig = null;
            List<Integer> ploidies = List.of();
            int previousPosition = 0;
            var keysAtPosition = new HashSet<String>();
            long blocks = 0, variants = 0, cells = 0, errors = 0, recombinations = 0, contigBlocks = 0, contigVariants = 0;
            while ((text = input.next()) != null) {
                String[] row = text.split("\t", -1);
                if (row.length != header.length || !row[4].equals("<BLOCK>") || !row[8].equals("UHM")) throw input.error("Require a complete UHM block header");
                token(row[0], input);
                int start = positive(row[1], input);
                Map<String,String> info = info(row[7], input);
                int end = positive(required(info, "END", input), input);
                int count = positive(required(info, "VARIANTS", input), input);
                int reps = positive(required(info, "REPS", input), input);
                if (end < start || reps > 2000000) throw input.error("Invalid block extent/representative bound");
                var mappings = new ArrayList<List<Integer>>();
                var nextPloidies = new ArrayList<Integer>();
                int[] multiplicities = new int[reps];
                int totalCopies = 0;
                for (int i = 9; i < row.length; i++) {
                    String[] entries = row[i].split(",", -1);
                    if (entries.length < 1 || entries.length > 2) throw input.error("Require explicit haploid or diploid UHM mappings");
                    var mapping = new ArrayList<Integer>();
                    for (String entry : entries) {
                        int representative = nonnegative(entry, input);
                        if (representative >= reps) throw input.error("UHM representative outside REPS");
                        multiplicities[representative]++;
                        totalCopies++;
                        mapping.add(representative);
                    }
                    mappings.add(mapping);
                    nextPloidies.add(entries.length);
                }
                if (!row[0].equals(contig)) {
                    if (!seenContigs.add(row[0])) throw input.error("Contig appears in disjoint sections");
                    if (contig != null) contigs.add(new Contig(contig, ploidies, contigBlocks, contigVariants));
                    contig = row[0]; ploidies = List.copyOf(nextPloidies);
                    previousPosition = 0; keysAtPosition.clear(); contigBlocks = 0; contigVariants = 0;
                    update(digest, "CONTIG\t" + contig + "\t" + ploidies + "\n");
                } else if (!ploidies.equals(nextPloidies)) throw input.error("Ploidy changes within contig; split PAR/non-PAR or other regions explicitly");
                if (start < previousPosition) throw input.error("Reference block positions are not sorted");
                Block block = new Block(contig, start, end, count, reps, mappings);
                int observedEnd = 0;
                for (int i = 0; i < count; i++) {
                    text = input.next();
                    if (text == null) throw input.error("Truncated reference block");
                    String[] f = text.split("\t", -1);
                    if (f.length != 8 || !f[0].equals(contig)) throw input.error("Require eight-field variant rows on block contig");
                    int position = positive(f[1], input);
                    token(f[2], input); literalAllele(f[3], input); literalAllele(f[4], input);
                    if (f[3].equals(f[4])) throw input.error("REF equals ALT");
                    long variantEnd = (long)position + Math.max(f[3].length(), f[4].length()) - 1;
                    if (position < previousPosition || position < start || variantEnd > end || i == 0 && position != start) throw input.error("Variant order or block extent differs");
                    if (position != previousPosition) keysAtPosition.clear();
                    if (!keysAtPosition.add(f[3] + ":" + f[4])) throw input.error("Duplicate variant key; no block-boundary deduplication is inferred");
                    previousPosition = position;
                    observedEnd = Math.max(observedEnd, (int)variantEnd);
                    Map<String,String> fields = info(f[7], input);
                    String[] alleles = required(fields, "UHA", input).split(",", -1);
                    if (alleles.length != reps) throw input.error("UHA length differs from REPS");
                    var uniqueAlleles = new ArrayList<Integer>();
                    int altCopies = 0;
                    for (int j = 0; j < reps; j++) {
                        int allele = nonnegative(alleles[j], input);
                        if (allele > 1) throw input.error("UHA must contain complete biallelic 0/1 alleles");
                        uniqueAlleles.add(allele); altCopies += allele * multiplicities[j];
                    }
                    if (nonnegative(required(fields, "AC", input), input) != altCopies
                            || positive(required(fields, "AN", input), input) != totalCopies) throw input.error("AC/AN differ from expanded reference haplotypes");
                    OptionalDouble error = parameter(fields, "ERR", true, input);
                    OptionalDouble recombination = parameter(fields, "RECOM", true, input);
                    OptionalDouble cm = parameter(fields, "CM", false, input);
                    Variant variant = new Variant(block, position, f[2], f[3], f[4], uniqueAlleles, altCopies, totalCopies, error, recombination, cm);
                    update(digest, contig + "\t" + position + "\t" + f[2] + "\t" + f[3] + "\t" + f[4] + "\t");
                    for (List<Integer> mapping : block.sampleMappings()) {
                        for (int index : mapping) digest.update((byte)('0' + uniqueAlleles.get(index)));
                        digest.update((byte)'\t');
                    }
                    digest.update((byte)'\n');
                    visitor.accept(variant);
                    variants++; contigVariants++; cells = Math.addExact(cells, totalCopies);
                    if (error.isPresent()) errors++;
                    if (recombination.isPresent()) recombinations++;
                }
                if (observedEnd != end) throw input.error("END differs from maximum reference allele extent");
                blocks++; contigBlocks++;
            }
            if (blocks == 0) throw input.error("Empty reference export");
            contigs.add(new Contig(contig, ploidies, contigBlocks, contigVariants));
            return new Summary(samples, contigs, blocks, variants, cells, errors, recombinations, HexFormat.of().formatHex(digest.digest()));
        } catch (ArithmeticException failure) {
            throw new IOException("Reference inventory exceeds exact integer capacity", failure);
        }
    }

    private static void token(String value, Lines input) throws IOException {
        if (value.isEmpty() || value.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) throw input.error("Invalid identifier/token");
    }
    private static void literalAllele(String value, Lines input) throws IOException {
        if (value.isEmpty() || !value.matches("[ACGTNacgtn]+")) throw input.error("Require biallelic literal DNA alleles; normalize other representations explicitly");
    }
    private static int positive(String value, Lines input) throws IOException {
        int result = nonnegative(value, input);
        if (result == 0) throw input.error("Require positive integer");
        return result;
    }
    private static int nonnegative(String value, Lines input) throws IOException {
        try {
            if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) throw input.error("Invalid exact nonnegative integer");
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) { throw input.error("Integer outside supported bounds"); }
    }
    private static Map<String,String> info(String text, Lines input) throws IOException {
        var fields = new HashMap<String,String>();
        for (String entry : text.split(";", -1)) {
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1 || fields.putIfAbsent(entry.substring(0, equals), entry.substring(equals + 1)) != null) throw input.error("Malformed or duplicate INFO field");
        }
        return fields;
    }
    private static String required(Map<String,String> fields, String key, Lines input) throws IOException {
        String result = fields.get(key);
        if (result == null) throw input.error("Missing " + key);
        return result;
    }
    private static OptionalDouble parameter(Map<String,String> fields, String key, boolean probability, Lines input) throws IOException {
        String value = fields.get(key);
        if (value == null || value.equals(".")) return OptionalDouble.empty();
        try {
            double result = Double.parseDouble(value);
            if (!Double.isFinite(result) || probability && (result < 0 || result > 1)) throw input.error("Invalid " + key + " parameter");
            return OptionalDouble.of(result);
        } catch (NumberFormatException failure) { throw input.error("Invalid " + key + " number"); }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void update(MessageDigest digest, String value) { digest.update(value.getBytes(StandardCharsets.UTF_8)); }
    private static final class Lines implements AutoCloseable {
        private final BufferedReader reader;
        private long line;
        Lines(Path path) throws IOException { reader = Files.newBufferedReader(path, StandardCharsets.UTF_8); }
        String next() throws IOException {
            // Bound allocation before accepting untrusted decompressed/exported text.
            var text = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1 && c != '\n') {
                if (text.length() >= 16777216) throw error("Export line exceeds 16 MiB character bound");
                if (c == 0) throw error("NUL in reference export");
                text.append((char)c);
            }
            if (c == -1 && text.isEmpty()) return null;
            line++;
            if (!text.isEmpty() && text.charAt(text.length() - 1) == '\r') text.setLength(text.length() - 1);
            return text.toString();
        }
        IOException error(String message) { return new IOException("MVCF line " + line + ": " + message); }
        @Override public void close() throws IOException { reader.close(); }
    }
}
