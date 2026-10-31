/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete ordinary RSEM .ti/.seq/.grp admission, with the .chrlist dictionary
 * for genome-derived references. Sequence bases are streamed and hashed rather
 * than retained. Exact names, exon structure, masks and original group order
 * remain available. No reference/annotation reconstruction is inferred.
 */
public final class RsemReference {
    private RsemReference() { }

    /**
     * Explicit whole-reference capacities; no transcript is sampled or dropped.
     * @param maxTranscripts maximum complete transcript population
     * @param maxChromosomes maximum complete genome dictionary population
     * @param maxBases maximum sum of total lengths, including added tails
     * @param maxExons maximum complete exon population
     * @param maxBytes maximum bytes in each source file
     * @param maxLineChars maximum characters in a source line
     */
    public record Limits(int maxTranscripts, int maxChromosomes, long maxBases, long maxExons, long maxBytes, int maxLineChars) {
        /**
         * Requires positive limits.
         * @param maxTranscripts maximum transcripts
         * @param maxChromosomes maximum chromosome dictionary entries
         * @param maxBases total bases
         * @param maxExons total exons
         * @param maxBytes per-file bytes
         * @param maxLineChars line capacity
         */
        public Limits {
            if (maxTranscripts < 1 || maxChromosomes < 1 || maxBases < 1 || maxExons < 1 || maxBytes < 1 || maxLineChars < 1)
                throw new IllegalArgumentException("Positive complete-reference capacities are required");
        }
    }

    /**
     * Exact reference metadata and sequence/mask identity for one transcript.
     * @param id raw transcript identifier
     * @param name original optional transcript display name
     * @param geneId raw gene identifier
     * @param geneName original optional gene display name
     * @param sequenceName chromosome name for genome-derived metadata, otherwise original sequence name
     * @param strand original plus/minus strand
     * @param length original pre-tail transcript length
     * @param totalLength length including added tail
     * @param exons flattened one-based inclusive start/end pairs in genomic order
     * @param annotationTail original unparsed final metadata line
     * @param sequenceSha256 digest of the exact canonical A/C/G/T/N sequence, including its added tail
     * @param maskWords original unsigned-32-bit mask words, represented as Java int bit patterns
     */
    public record Transcript(String id, String name, String geneId, String geneName,
                             String sequenceName, char strand, int length, int totalLength,
                             int[] exons, String annotationTail, String sequenceSha256, int[] maskWords) {
        /**
         * Copies structure and masks.
         * @param id exact transcript identifier
         * @param name original display name
         * @param geneId raw gene identifier
         * @param geneName original display name
         * @param sequenceName original chromosome/sequence name
         * @param strand original strand
         * @param length pre-tail length
         * @param totalLength tail-inclusive length
         * @param exons inclusive coordinate pairs
         * @param annotationTail unparsed metadata
         * @param sequenceSha256 sequence digest
         * @param maskWords unsigned mask bit patterns
         */
        public Transcript {exons = exons.clone(); maskWords = maskWords.clone();}
        /** @return a copy of all original exon pairs */
        @Override public int[] exons() {return exons.clone();}
        /** @return a copy of the original mask bit patterns */
        @Override public int[] maskWords() {return maskWords.clone();}
        /** @return number of explicitly added poly-A bases */
        public int polyATail() {return totalLength - length;}
        /**
         * Computes the original optional display label without stripping or merging identifiers.
         * @param appendNames original --append-names selection
         * @return exact expected isoform output label
         */
        public String displayedId(boolean appendNames) {return appendNames && !name.isEmpty() ? id + "_" + name : id;}
        /**
         * Computes the original per-isoform gene display label.
         * @param appendNames original --append-names selection
         * @return exact expected gene label in this isoform row
         */
        public String displayedGeneId(boolean appendNames) {return appendNames && !geneName.isEmpty() ? geneId + "_" + geneName : geneId;}
    }

    /**
     * Original complete contiguous gene group, with zero-based transcript bounds.
     * @param id exact gene identifier
     * @param begin first member, inclusive
     * @param end final member bound, exclusive
     */
    public record GeneGroup(String id, int begin, int end) { }

    /**
     * A completely consumed ordinary reference.
     * @param type original metadata type, zero for genome/GTF and one for transcript FASTA
     * @param transcripts exact original order
     * @param groups complete original group order
     * @param chromosomes genome dictionary, empty for direct transcript references
     * @param totalBases sum of all tail-inclusive sequence lengths
     * @param totalExons complete exon count
     * @param evidence all consumed source files
     */
    public record Reference(int type, List<Transcript> transcripts, List<GeneGroup> groups,
                            Map<String, Long> chromosomes, long totalBases, long totalExons,
                            List<RsemEvidence> evidence) {
        /**
         * Copies all populations and preserves chromosome order.
         * @param type original reference type
         * @param transcripts complete transcript order
         * @param groups complete gene groups
         * @param chromosomes exact chromosome dictionary
         * @param totalBases total bases
         * @param totalExons total exons
         * @param evidence complete source evidence
         */
        public Reference {
            transcripts = List.copyOf(transcripts); groups = List.copyOf(groups);
            chromosomes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(chromosomes)); evidence = List.copyOf(evidence);
        }
        /** @return immutable ordinary abundance metadata in the same transcript order */
        public List<RsemAbundance.Reference> abundanceReferences() {
            return transcripts.stream().map(t -> new RsemAbundance.Reference(t.id(), t.geneId(), t.length(), t.polyATail())).toList();
        }
    }

    private record Metadata(String id, String name, String gene, String geneName, String sequence,
                            char strand, int length, int[] exons, String annotation) { }

    /**
     * Reads every ordinary reference member using the original fixed suffixes.
     * Allele-specific type two requires a separate grouping/result contract and
     * is rejected explicitly. LF and exact UTF-8 bytes are required; no newline,
     * identifier, sequence or annotation normalization occurs.
     * @param prefix original reference prefix without a suffix
     * @param limits whole-source capacities
     * @return a complete cross-file-consistent reference
     * @throws IOException malformed, incomplete, changed or over-capacity source
     */
    public static Reference read(Path prefix, Limits limits) throws IOException {
        List<RsemEvidence> evidence = new ArrayList<>(); List<Metadata> metadata = new ArrayList<>();
        int type; long exons = 0;
        try (RsemText input = open(prefix, ".ti", limits)) {
            String[] header = RsemText.words(input.requiredLine("metadata header"));
            if (header.length != 2) throw input.error("Metadata header must contain transcript count and type");
            int count = (int) input.integer(header[0], "transcript count", 1, limits.maxTranscripts());
            type = (int) input.integer(header[1], "reference type", 0, 2);
            if (type == 2) throw input.error("Allele-specific references require a separate .gt/.ta and allele-result contract");
            Set<String> ids = new HashSet<>();
            for (int index = 0; index < count; index++) {
                String[] tx = named(input.requiredLine("transcript identifier"), input), gene = named(input.requiredLine("gene identifier"), input);
                if (!ids.add(tx[0])) throw input.error("Duplicate exact transcript identifier: " + tx[0]);
                String sequence = input.requiredLine("chromosome/sequence name"); identifier(sequence, input);
                String[] shape = RsemText.words(input.requiredLine("strand and transcript length"));
                if (shape.length != 2 || !(shape[0].equals("+") || shape[0].equals("-"))) throw input.error("Invalid strand/length metadata");
                int length = (int) input.integer(shape[1], "transcript length", 1, Integer.MAX_VALUE - 1);
                String[] structure = RsemText.words(input.requiredLine("complete exon structure"));
                if (structure.length == 0) throw input.error("Missing exon structure");
                long countExons = input.integer(structure[0], "exon count", 1, Math.min(Integer.MAX_VALUE / 2 - 1, limits.maxExons() - exons));
                if (structure.length != 1 + countExons * 2) throw input.error("Exon count differs from complete coordinate population");
                int[] coordinates = new int[(int) countExons * 2]; long measuredLength = 0; int previous = 0;
                for (int j = 0; j < countExons; j++) {
                    int start = (int) input.integer(structure[1 + j * 2], "exon start", 1, Integer.MAX_VALUE);
                    int end = (int) input.integer(structure[2 + j * 2], "exon end", start, Integer.MAX_VALUE);
                    if (start <= previous) throw input.error("Exon coordinates overlap or are not in original genomic order");
                    coordinates[j * 2] = start; coordinates[j * 2 + 1] = end; previous = end; measuredLength += (long) end - start + 1;
                }
                if (measuredLength != length) throw input.error("Inclusive exon lengths differ from the original transcript length");
                if (type == 1 && (coordinates.length != 2 || coordinates[0] != 1 || coordinates[1] != length || shape[0].charAt(0) != '+'))
                    throw input.error("Direct-transcript metadata does not retain its original single positive-strand interval");
                String annotation = input.requiredLine("original annotation tail");
                metadata.add(new Metadata(tx[0], tx[1], gene[0], gene[1], sequence, shape[0].charAt(0), length, coordinates, annotation));
                exons += countExons;
            }
            input.endLines(); evidence.add(input.evidence());
        }
        Map<String, Long> chromosomes = new LinkedHashMap<>();
        if (type == 0) try (RsemText input = open(prefix, ".chrlist", limits)) {
            String line;
            while ((line = input.line()) != null) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 2) throw input.error("Chromosome dictionary requires exactly name and length");
                identifier(fields[0], input); long length = input.integer(fields[1], "chromosome length", 1, Long.MAX_VALUE);
                if (chromosomes.size() >= limits.maxChromosomes() || chromosomes.putIfAbsent(fields[0], length) != null)
                    throw input.error("Duplicate or over-capacity chromosome dictionary");
            }
            if (chromosomes.isEmpty()) throw input.error("Genome-derived reference lacks its chromosome dictionary");
            evidence.add(input.evidence());
            for (Metadata item : metadata) {
                Long length = chromosomes.get(item.sequence());
                if (length == null || item.exons()[item.exons().length - 1] > length)
                    throw new IOException("Transcript has missing/out-of-bounds genome dictionary coordinates: " + item.id());
            }
        }
        List<Transcript> transcripts = new ArrayList<>(); long bases = 0;
        try (RsemText input = open(prefix, ".seq", limits)) {
            for (Metadata item : metadata) {
                String[] shape = RsemText.words(input.requiredLine("reference sequence lengths"));
                if (shape.length != 2) throw input.error("Invalid reference sequence length pair");
                int full = (int) input.integer(shape[0], "pre-tail sequence length", 1, Integer.MAX_VALUE - 1);
                int total = (int) input.integer(shape[1], "tail-inclusive sequence length", full, Integer.MAX_VALUE - 1);
                if (full != item.length()) throw input.error("Sequence and metadata lengths differ: " + item.id());
                if (!input.requiredLine("sequence identity").equals(item.id())) throw input.error("Reference sequence identity/order differs from metadata");
                if (total > limits.maxBases() - bases) throw input.error("Complete reference exceeds declared total-base capacity");
                String sequence = input.requiredLine("complete canonical reference sequence");
                if (sequence.length() != total) throw input.error("Reference sequence bases differ from declared length");
                for (int j = 0; j < total; j++) {
                    char base = sequence.charAt(j);
                    if (base != 'A' && base != 'C' && base != 'G' && base != 'T' && base != 'N') throw input.error("Noncanonical generated reference base");
                    if (j >= full && base != 'A') throw input.error("Declared added poly-A tail contains another base");
                }
                String[] masks = RsemText.words(input.requiredLine("complete mask words"));
                int size = (full - 1) / 32 + 1;
                if (masks.length != size) throw input.error("Mask-word population differs from full reference length");
                int[] bits = new int[size];
                for (int j = 0; j < size; j++) bits[j] = (int) input.integer(masks[j], "unsigned mask word", 0, 0xffffffffL);
                int remainder = full % 32;
                if (remainder != 0 && (bits[size - 1] >>> remainder) != 0) throw input.error("Mask word sets positions outside the pre-tail transcript");
                transcripts.add(new Transcript(item.id(), item.name(), item.gene(), item.geneName(), item.sequence(), item.strand(), full, total,
                        item.exons(), item.annotation(), sequenceDigest(sequence), bits)); bases += total;
            }
            input.endLines(); evidence.add(input.evidence());
        }
        List<GeneGroup> groups = new ArrayList<>();
        try (RsemText input = open(prefix, ".grp", limits)) {
            String first = input.optionalToken();
            if (first == null || input.integer(first, "first group bound", 1, 1) != 1) throw input.error("Original first group bound must be one");
            int begin = 0; Set<String> genes = new HashSet<>(); String word;
            while ((word = input.optionalToken()) != null) {
                int end = (int) input.integer(word, "exclusive group bound", begin + 2L, transcripts.size() + 1L) - 1;
                String gene = transcripts.get(begin).geneId();
                if (!genes.add(gene)) throw input.error("Gene occurs in multiple noncontiguous original groups");
                for (int j = begin; j < end; j++) if (!transcripts.get(j).geneId().equals(gene)) throw input.error("Original group contains another gene identifier");
                groups.add(new GeneGroup(gene, begin, end)); begin = end;
                if (begin == transcripts.size()) {
                    if (input.optionalToken() != null) throw input.error("Group bounds extend beyond the complete transcript population");
                    break;
                }
            }
            if (begin != transcripts.size()) throw input.error("Group bounds omit reference transcripts");
            evidence.add(input.evidence());
        }
        return new Reference(type, transcripts, groups, chromosomes, bases, exons, evidence);
    }

    private static RsemText open(Path prefix, String suffix, Limits limits) throws IOException {
        return new RsemText(Path.of(prefix.toString() + suffix), limits.maxBytes(), limits.maxLineChars());
    }
    private static String[] named(String line, RsemText input) throws IOException {
        int split = line.indexOf('\t'); String id = split < 0 ? line : line.substring(0, split), name = split < 0 ? "" : line.substring(split + 1);
        identifier(id, input);
        if (name.indexOf('\t') >= 0) throw input.error("Display name contains an extra structural tab");
        return new String[]{id, name};
    }
    private static void identifier(String value, RsemText input) throws IOException {
        if (value.isEmpty() || value.indexOf('\t') >= 0) throw input.error("Empty identifier or structural tab in identifier");
    }
    private static String sequenceDigest(String sequence) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sequence.getBytes(StandardCharsets.US_ASCII)));}
        catch (NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
