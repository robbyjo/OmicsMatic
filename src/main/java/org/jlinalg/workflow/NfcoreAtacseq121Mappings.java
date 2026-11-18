/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source semantics: nf-core/atacseq 1.2.1, bin/check_design.py and main.nf,
 * Copyright (c) Harshil Patel, MIT. See the bundled original license. */
package org.jlinalg.workflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Independent complete-design reconstruction of the original ATAC sample mapping.
 * Technical replicates, original Python sorting/stripping and canonical integer
 * replicate labels follow check_design.py. No biological IDs are invented from filenames.
 */
public final class NfcoreAtacseq121Mappings {
    private NfcoreAtacseq121Mappings() { }
    /** Complete logical input bounds, with no prefix or partial-data result. */
    public record Limits(int rows, int lineCharacters) {
        public Limits {
            if (rows < 1 || rows > 1_000_000 || lineCharacters < 1 || lineCharacters > 2_000_000)
                throw new IllegalArgumentException("Require positive bounded complete-design capacities");
        }
        public static Limits defaults() { return new Limits(100_000, 1_048_576); }
    }
    /** One exact checked row; read2 is empty for the source single-end branch. */
    public record Read(String sampleId, String read1, String read2, String libraryId, String group, int replicate) { }
    /** Original sorted source row order and the two main.nf branch conditions. */
    public record Design(List<Read> reads, boolean paired, boolean replicatesExist, boolean multipleGroups) {
        public Design { reads = List.copyOf(reads); }
        public List<String> libraries() { return reads.stream().map(Read::libraryId).distinct().toList(); }
        public List<String> groups() { return reads.stream().map(Read::group).distinct().toList(); }
        public String checkedCsv() {
            var result = new StringBuilder("sample_id,fastq_1,fastq_2\n");
            for (Read row : reads) result.append(row.sampleId()).append(',').append(row.read1()).append(',').append(row.read2()).append('\n');
            return result.toString();
        }
        public Map<String, Object> document() {
            return Map.of("reads", reads.stream().map(r -> Map.of("sample_id", r.sampleId(), "fastq_1", r.read1(), "fastq_2", r.read2(), "library_id", r.libraryId(), "group", r.group(), "replicate", r.replicate())).toList(),
                "libraries", libraries(), "groups", groups(), "paired", paired, "replicates_exist", replicatesExist,
                "multiple_groups", multipleGroups, "whole_paper_reproduced", false);
        }
    }
    // Java UTF-16 String order differs from Python code-point order for supplementary characters.
    private static final Comparator<String> PYTHON_ORDER = (left, right) -> {
        var a = left.codePoints().iterator(); var b = right.codePoints().iterator();
        while (a.hasNext() && b.hasNext()) { int compare = Integer.compare(a.nextInt(), b.nextInt()); if (compare != 0) return compare; }
        return Boolean.compare(a.hasNext(), b.hasNext());
    };
    /** Read the original four-column design and reconstruct every original output row.
     * The caller owns the stream. Replicate labels are bounded ASCII decimal integers;
     * invalid, empty, mixed-mode and noncontiguous designs admit no mapping.
     */
    public static Design read(Reader source, Limits limits) throws IOException {
        Objects.requireNonNull(limits); var input = source instanceof BufferedReader b ? b : new BufferedReader(source);
        if (!"group,replicate,fastq_1,fastq_2".equals(strip(line(input, limits)))) throw new IOException("Require the original ATAC1.2.1 design header");
        var groups = new TreeMap<String, TreeMap<Integer, List<List<String>>>>(PYTHON_ORDER);
        Boolean paired = null; String value; int rows = 0;
        while ((value = line(input, limits)) != null) {
            if (++rows > limits.rows()) throw new IOException("Complete ATAC design row capacity exceeded; no partial result");
            var fields = new ArrayList<String>();
            for (String cell : strip(value).split(",", -1)) if (!cell.isEmpty()) fields.add(strip(cell));
            if (fields.size() != 3 && fields.size() != 4) throw new IOException("Original ATAC checker requires three single-end or four paired-end nonempty cells");
            String group = fields.get(0), label = fields.get(1);
            if (group.isEmpty() || group.indexOf(' ') >= 0 || group.codePoints().anyMatch(c -> c < 32 || c == 127)) throw new IOException("Unsupported/invalid original ATAC group field; no identifier repair");
            if (!label.matches("[0-9]{1,9}")) throw new IOException("This mapping supports bounded ASCII-decimal source replicate labels");
            int replicate = Integer.parseInt(label);
            if (replicate < 1 || replicate > limits.rows()) throw new IOException("Source replicate label is invalid or exceeds complete-design capacity");
            boolean current = fields.size() == 4;
            if (paired != null && paired != current) throw new IOException("Original ATAC design mixes single-end and paired-end reads");
            paired = current; var reads = new ArrayList<String>();
            for (int i = 2; i < fields.size(); i++) {
                String path = fields.get(i);
                if ((!path.endsWith(".fastq.gz") && !path.endsWith(".fq.gz")) || path.codePoints().anyMatch(c -> c < 32 || c == 127)) throw new IOException("Require original literal compressed FASTQ spelling");
                reads.add(path);
            }
            groups.computeIfAbsent(group, key -> new TreeMap<>()).computeIfAbsent(replicate, key -> new ArrayList<>()).add(List.copyOf(reads));
        }
        if (rows == 0) throw new IOException("Original ATAC design contains no samples");
        var result = new ArrayList<Read>(); var ids = new HashSet<String>(); boolean reps = false;
        for (var group : groups.entrySet()) {
            if (group.getValue().size() != group.getValue().lastKey()) throw new IOException("Original ATAC replicate IDs must form 1..number-of-replicates in each group");
            reps |= group.getValue().lastKey() > 1;
            for (var replicate : group.getValue().entrySet()) {
                String library = group.getKey()+"_R"+replicate.getKey(); int technical = 0;
                for (List<String> reads : replicate.getValue()) {
                    String id = library+"_T"+(++technical);
                    if (!ids.add(id)) throw new IOException("Repeated generated original sample ID");
                    result.add(new Read(id, reads.get(0), reads.size() == 2 ? reads.get(1) : "", library, group.getKey(), replicate.getKey()));
                }
            }
        }
        return new Design(result, Boolean.TRUE.equals(paired), reps, groups.size() > 1);
    }
    private static String line(BufferedReader input, Limits limits) throws IOException {
        var result = new StringBuilder(); int c;
        while ((c = input.read()) != -1 && c != '\n') {
            if (c == '\r') { input.mark(1); int next = input.read(); if (next != '\n' && next != -1) input.reset(); break; }
            if (result.length() >= limits.lineCharacters()) throw new IOException("Complete ATAC design line capacity exceeded");
            result.append((char)c);
        }
        return c == -1 && result.isEmpty() ? null : result.toString();
    }
    private static String strip(String value) {
        if (value == null) return null;
        int start = 0, end = value.length();
        while (start < end && space(value.codePointAt(start))) start += Character.charCount(value.codePointAt(start));
        while (start < end && space(value.codePointBefore(end))) end -= Character.charCount(value.codePointBefore(end));
        return value.substring(start, end);
    }
    private static boolean space(int codePoint) { return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85; }
}
