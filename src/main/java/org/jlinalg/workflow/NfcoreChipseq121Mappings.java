/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read the literal mappings emitted by original nf-core/chipseq1.2.1 check_design.py.
 * Original input parsing, group sorting, last antibody assignment and control fallback
 * remain upstream operations. This class never repairs IDs, sorts samples or opens reads.
 */
public final class NfcoreChipseq121Mappings {
    private NfcoreChipseq121Mappings() { }
    /** Explicit complete-table capacities. */
    public record Limits(int rows, int lineCharacters) {
        public Limits { if (rows < 1 || rows > 1000000 || lineCharacters < 1 || lineCharacters > 2000000)
            throw new IllegalArgumentException("Require bounded positive mapping capacities"); }
        public static Limits defaults() { return new Limits(100000, 1048576); }
    }
    /** One upstream technical sample and its unchanged source filenames. */
    public record Read(String sampleId, String read1, String read2) {
        public Read {
            literal(sampleId); literal(read1); Objects.requireNonNull(read2);
            String last = sampleId.substring(sampleId.lastIndexOf('_') + 1);
            if (!last.matches("T[1-9][0-9]*") || sampleId.lastIndexOf('_') < 1
                    || !read1.endsWith(".fastq.gz") && !read1.endsWith(".fq.gz")
                    || !read2.isEmpty() && !read2.endsWith(".fastq.gz") && !read2.endsWith(".fq.gz"))
                throw new IllegalArgumentException("Require original technical sample and compressed FASTQ spellings");
            if (!read2.isEmpty()) literal(read2);
            groupOf(sampleId.substring(0, sampleId.lastIndexOf('_')));
        }
        /** Exact main.nf split('_')[0..-2].join('_') operation, retaining the biological replicate. */
        public String libraryId() { return sampleId.substring(0, sampleId.lastIndexOf('_')); }
    }
    /** One literal source IP/control pair and the flags written by the upstream checker. */
    public record Control(String sampleId, String controlId, String antibody,
                          boolean replicatesExist, boolean multipleGroups) {
        public Control { literal(sampleId); literal(controlId); literal(antibody); groupOf(sampleId); groupOf(controlId); }
    }
    /** Original encounter order; technical replicates remain distinct observations. */
    public record Library(String id, List<Read> technicalSamples) {
        public Library { literal(id); technicalSamples = List.copyOf(technicalSamples); }
    }
    /** Checked graph of literal mappings; it establishes no source authenticity or biological validity. */
    public record Manifest(List<Read> reads, List<Control> controls) {
        public Manifest {
            reads = List.copyOf(reads); controls = List.copyOf(controls);
            if (reads.isEmpty() || controls.isEmpty()) throw new IllegalArgumentException("Complete mappings require reads and controls");
            var ids = new java.util.HashSet<String>(); var libraries = new java.util.HashSet<String>();
            for (Read read : reads) {
                if (!ids.add(read.sampleId())) throw new IllegalArgumentException("Duplicate upstream technical sample ID");
                libraries.add(read.libraryId());
            }
            var ipIds = new java.util.HashSet<String>();
            var groups = new LinkedHashMap<String, Map<String, Integer>>();
            for (Control control : controls) {
                if (!libraries.contains(control.sampleId()) || !libraries.contains(control.controlId()))
                    throw new IllegalArgumentException("Upstream control mapping references an absent merged library; retain the original mapping without repair");
                if (!ipIds.add(control.sampleId())) throw new IllegalArgumentException("Duplicate upstream IP/control mapping");
                groups.computeIfAbsent(control.antibody(), key -> new LinkedHashMap<>()).merge(groupOf(control.sampleId()), 1, Integer::sum);
            }
            for (Control control : controls) {
                var selected = groups.get(control.antibody());
                boolean reps = selected.values().stream().anyMatch(n -> n > 1);
                if (control.replicatesExist() != reps || control.multipleGroups() != (selected.size() > 1))
                    throw new IllegalArgumentException("Upstream antibody-group flags disagree with the complete IP/control graph");
            }
        }
        /** Source encounter order, using the exact upstream technical-to-biological library relation. */
        public List<Library> libraries() {
            var result = new LinkedHashMap<String, List<Read>>();
            for (Read read : reads) result.computeIfAbsent(read.libraryId(), key -> new ArrayList<>()).add(read);
            return result.entrySet().stream().map(e -> new Library(e.getKey(), e.getValue())).toList();
        }
        /** Lookup preserves literal keys and original encounter order. */
        public Map<String, Library> libraryIndex() {
            var result = new LinkedHashMap<String, Library>();
            for (Library library : libraries()) result.put(library.id(), library);
            return Collections.unmodifiableMap(result);
        }
        public long rowsWithRead2() { return reads.stream().filter(r -> !r.read2().isEmpty()).count(); }
        public long selfControlPairs() { return controls.stream().filter(c -> c.sampleId().equals(c.controlId())).count(); }
        /** JSON-compatible literal metadata; no values are inferred from FASTQ filenames. */
        public Map<String, Object> document() {
            return Map.of("reads", reads.stream().map(r -> Map.of("sample_id", r.sampleId(), "fastq_1", r.read1(), "fastq_2", r.read2(), "library_id", r.libraryId())).toList(),
                "controls", controls.stream().map(c -> Map.of("sample_id", c.sampleId(), "control_id", c.controlId(), "antibody", c.antibody(), "replicatesExist", c.replicatesExist(), "multipleGroups", c.multipleGroups())).toList(),
                "library_ids", libraries().stream().map(Library::id).toList(), "rows_with_read2", rowsWithRead2(), "self_control_pairs", selfControlPairs(),
                "original_order_preserved", true, "filenames_or_identifiers_repaired", false, "FASTQ_contents_validated", false, "biological_accuracy_certified", false);
        }
    }
    /** Read complete files with strict UTF-8 decoding; no CSV quote convention is added to the original. */
    public static Manifest read(Path reads, Path controls, Limits limits) throws IOException {
        try (var r = Files.newBufferedReader(reads, StandardCharsets.UTF_8); var c = Files.newBufferedReader(controls, StandardCharsets.UTF_8)) {
            return read(r, c, limits);
        }
    }
    /** Caller owns the streams. Headers and fields remain literal; malformed mappings are refused. */
    public static Manifest read(Reader reads, Reader controls, Limits limits) throws IOException {
        Objects.requireNonNull(limits);
        var r = rows(reads, "sample_id,fastq_1,fastq_2", 3, limits);
        var c = rows(controls, "sample_id,control_id,antibody,replicatesExist,multipleGroups", 5, limits);
        try {
            var readRows = new ArrayList<Read>(); var controlRows = new ArrayList<Control>();
            for (String[] row : r) readRows.add(new Read(row[0], row[1], row[2]));
            for (String[] row : c) controlRows.add(new Control(row[0], row[1], row[2], flag(row[3]), flag(row[4])));
            return new Manifest(readRows, controlRows);
        } catch (IllegalArgumentException invalid) { throw new IOException("Invalid original ChIP-seq1.2.1 checked mapping: " + invalid.getMessage(), invalid); }
    }
    private static List<String[]> rows(Reader source, String header, int columns, Limits limits) throws IOException {
        var input = source instanceof BufferedReader b ? b : new BufferedReader(source);
        if (!header.equals(input.readLine())) throw new IOException("Original checked-mapping header differs: " + header);
        var rows = new ArrayList<String[]>(); String line;
        while ((line = input.readLine()) != null) {
            if (line.length() > limits.lineCharacters() || rows.size() >= limits.rows()) throw new IOException("Complete checked-mapping capacity exceeded");
            String[] fields = line.split(",", -1);
            if (fields.length != columns) throw new IOException("Original checked mapping requires literal comma-separated fields; quoted/comma-containing paths are unsupported");
            rows.add(fields);
        }
        return List.copyOf(rows);
    }
    private static boolean flag(String value) throws IOException {
        if (!value.equals("0") && !value.equals("1")) throw new IOException("Original checked-mapping flag must be0 or1");
        return value.equals("1");
    }
    private static void literal(String value) {
        if (value == null || value.isEmpty() || value.codePoints().anyMatch(c -> c < 32 || c == 127) || value.indexOf(',') >= 0)
            throw new IllegalArgumentException("Require nonempty unchanged literal mapping fields without control characters or commas");
    }
    private static String groupOf(String library) {
        int split = library.lastIndexOf('_');
        if (split < 0 || !library.substring(split + 1).matches("R[0-9]+"))
            throw new IllegalArgumentException("Require the upstream merged-library replicate suffix");
        return library.substring(0, split);
    }
}
