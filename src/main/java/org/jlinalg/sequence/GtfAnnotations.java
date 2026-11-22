/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Streaming GTF structural/exon validation against an explicitly supplied reference dictionary. */
public final class GtfAnnotations {
    private GtfAnnotations() { }
    private static final Pattern GENE = Pattern.compile("(?:^|;)\\s*gene_id\\s+\"([^\"]+)\"\\s*(?=;|$)");
    private static final Pattern TRANSCRIPT = Pattern.compile("(?:^|;)\\s*transcript_id\\s+\"([^\"]+)\"\\s*(?=;|$)");
    public record Summary(long records, long exons, int genes, int transcripts, long unknownStrandExons, Set<String> geneIds) {
        public Summary { geneIds = Set.copyOf(geneIds); }
    }
    /** Validate GTF9 rows and required gene/transcript identifiers on exon features; never infer contig aliases. */
    public static Summary inspect(Path file, Map<String, Long> referenceLengths) throws IOException {
        if (referenceLengths.isEmpty()) throw new IllegalArgumentException("Reference dictionary is empty");
        long records = 0, exons = 0, unknown = 0, lineNumber = 0;
        var genes = new HashSet<String>(); var transcripts = new HashSet<String>();
        try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) continue;
                if (line.length() > 2_000_000 || ++records > 20_000_000) throw error(lineNumber, "GTF exceeds two million characters per row or twenty million records");
                String[] fields = line.split("\t", -1);
                if (fields.length != 9) throw error(lineNumber, "Require nine tab-delimited GTF fields");
                Long length = referenceLengths.get(fields[0]);
                if (length == null) throw error(lineNumber, "GTF contig is absent from the exact reference: " + fields[0]);
                long start, end;
                try { start = Long.parseLong(fields[3]); end = Long.parseLong(fields[4]); }
                catch (NumberFormatException failure) { throw error(lineNumber, "GTF coordinates must be integers"); }
                if (start < 1 || end < start || end > length) throw error(lineNumber, "GTF one-based inclusive interval is outside the reference");
                if (!Set.of("+", "-", ".").contains(fields[6])) throw error(lineNumber, "GTF strand must be +, - or .");
                if (!fields[2].equals("exon")) continue;
                var gene = GENE.matcher(fields[8]); var transcript = TRANSCRIPT.matcher(fields[8]);
                if (!gene.find() || !transcript.find()) throw error(lineNumber, "Each exon requires quoted gene_id and transcript_id attributes; GFF3 is not GTF");
                String geneId = gene.group(1), transcriptId = transcript.group(1);
                if (geneId.chars().anyMatch(Character::isWhitespace) || transcriptId.chars().anyMatch(Character::isWhitespace)
                        || gene.find() || transcript.find()) throw error(lineNumber, "Exon parent identifiers must be unique attributes without whitespace");
                genes.add(geneId); transcripts.add(transcriptId); exons++;
                if (fields[6].equals(".")) unknown++;
            }
        }
        if (exons == 0) throw new IOException("GTF has no lowercase exon features with gene_id/transcript_id parents");
        return new Summary(records, exons, genes.size(), transcripts.size(), unknown, genes);
    }
    private static IOException error(long line, String text) { return new IOException(text + " at GTF line " + line); }
}
