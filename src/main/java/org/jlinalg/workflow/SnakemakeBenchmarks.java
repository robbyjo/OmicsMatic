/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Native aggregation of Snakemake 9.27.1 TSV benchmarks: sum job CPU seconds
 * and take the maximum job RSS in MiB, separately for every declared replicate.
 * This does not execute a workflow, measure processes, infer missing jobs,
 * estimate concurrent pipeline memory, or pool benchmark repeats.
 */
public final class SnakemakeBenchmarks {
    /** Exact standard TSV header emitted by the selected upstream edition. */
    public static final List<String> STANDARD = List.of("s", "h:m:s", "max_rss", "max_vms", "max_uss", "max_pss", "io_in", "io_out", "mean_load", "cpu_time");
    /** Exact extended TSV header; the additional fields are descriptive metadata. */
    public static final List<String> EXTENDED;
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    static {
        var fields = new ArrayList<>(STANDARD);
        fields.addAll(List.of("jobid", "rule_name", "wildcards", "params", "threads", "cpu_usage", "resources", "input_size_mb"));
        EXTENDED = List.copyOf(fields);
    }

    /** Limits apply to complete files/rows; exceeding them never truncates input. */
    public record Limits(long maxBytes, int maxRows, int maxLineCharacters) {
        public Limits {
            if (maxBytes < 1 || maxRows < 1 || maxLineCharacters < 1) throw new IllegalArgumentException("Positive benchmark input capacities required");
        }
    }

    /** Values are the decimals actually recorded upstream, without inferred precision. */
    public record Measurement(BigDecimal wallSeconds, BigDecimal cpuSeconds, BigDecimal maxRssMiB) {
        public Measurement { nonnegative(wallSeconds); nonnegative(cpuSeconds); nonnegative(maxRssMiB); }
    }

    /** A one-based TSV data row assigned explicitly to a job and pipeline replicate. */
    public record Assignment(String pipeline, String replicate, String job, String table, int row) {
        public Assignment {
            for (String value : List.of(pipeline, replicate, job, table)) identifier(value);
            if (row < 1) throw new IllegalArgumentException("Benchmark data rows are one-based");
        }
    }

    /** One declared pipeline replicate; identifiers retain their exact supplied spelling. */
    public record Group(String pipeline, String replicate) implements Comparable<Group> {
        public Group { identifier(pipeline); identifier(replicate); }
        @Override public int compareTo(Group other) {
            int c = pipeline.compareTo(other.pipeline); return c == 0 ? replicate.compareTo(other.replicate) : c;
        }
    }

    /** Maximum job RSS is not the peak memory of concurrently running jobs. */
    public record Summary(int jobs, BigDecimal cpuSeconds, BigDecimal maxJobRssMiB, BigDecimal summedJobWallSeconds) { }
    private record SelectedRow(String table, int row) { }
    private record Job(Group group, String job) { }
    private record TableOwner(String pipeline, String job) { }
    private SnakemakeBenchmarks() { }

    /** Read an entire selected-edition standard or extended TSV, retaining row order. */
    public static List<Measurement> read(Path path, Limits limits) throws IOException {
        Objects.requireNonNull(limits);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Regular benchmark TSV required: " + path);
        if (Files.size(path) > limits.maxBytes()) throw new IOException("Benchmark exceeds maxBytes; supply adequate permitted capacity, never truncate rows: " + path);
        try (InputStream input = new Capped(Files.newInputStream(path), limits.maxBytes());
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                 .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) { return read(reader, limits); }
    }

    /** Parse a caller-owned reader; this overload does not close it or impose a byte limit. */
    public static List<Measurement> read(Reader reader, Limits limits) throws IOException {
        Objects.requireNonNull(reader); Objects.requireNonNull(limits);
        String first = line(reader, limits.maxLineCharacters());
        if (first == null) throw new IOException("Empty Snakemake benchmark table");
        List<String> header = List.of(first.split("\t", -1));
        if (!header.equals(STANDARD) && !header.equals(EXTENDED)) throw new IOException("Require exact standard/extended Snakemake 9.27.1 TSV header; no inferred column or unit conversion");
        var result = new ArrayList<Measurement>(); String text;
        while ((text = line(reader, limits.maxLineCharacters())) != null) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Benchmark parsing interrupted");
            if (result.size() >= limits.maxRows()) throw new IOException("Benchmark exceeds maxRows; no partial table admitted");
            String[] fields = text.split("\t", -1); int row = result.size() + 1;
            if (fields.length != header.size()) throw new IOException("Benchmark row " + row + " has an unexpected field count");
            BigDecimal wall = decimal(fields[0], "s", row), rss = decimal(fields[2], "max_rss", row), cpu = decimal(fields[9], "cpu_time", row);
            for (int index = 3; index < 9; index++)
                if (!Set.of("NA", "-").contains(fields[index])) decimal(fields[index], header.get(index), row);
            result.add(new Measurement(wall, cpu, rss));
        }
        if (result.isEmpty()) throw new IOException("Benchmark header has no data rows");
        return List.copyOf(result);
    }

    /**
     * Every supplied table row must be assigned exactly once. Repeats remain
     * separate through explicit replicate labels; duplicate jobs within a group
     * are rejected. The caller must establish that the supplied job set is complete.
     */
    public static Map<Group, Summary> aggregate(Map<String, List<Measurement>> tables, List<Assignment> assignments) {
        Objects.requireNonNull(tables); Objects.requireNonNull(assignments);
        if (tables.isEmpty() || assignments.isEmpty()) throw new IllegalArgumentException("A complete nonempty benchmark table/assignment set is required");
        var copy = new LinkedHashMap<String, List<Measurement>>(); long rows = 0;
        for (var entry : tables.entrySet()) {
            identifier(entry.getKey()); List<Measurement> values = List.copyOf(entry.getValue());
            if (values.isEmpty()) throw new IllegalArgumentException("An empty benchmark table is not an executed job");
            rows = Math.addExact(rows, values.size()); copy.put(entry.getKey(), values);
        }
        var selected = new HashSet<SelectedRow>(); var jobs = new HashSet<Job>(); var owners = new java.util.HashMap<String, TableOwner>(); var result = new TreeMap<Group, Summary>();
        for (Assignment assignment : List.copyOf(assignments)) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Benchmark aggregation interrupted");
            List<Measurement> values = copy.get(assignment.table());
            if (values == null || assignment.row() > values.size()) throw new IllegalArgumentException("Assigned benchmark row/table is absent: " + assignment);
            if (!selected.add(new SelectedRow(assignment.table(), assignment.row()))) throw new IllegalArgumentException("A benchmark row cannot be reused across jobs or replicates: " + assignment);
            TableOwner owner = new TableOwner(assignment.pipeline(), assignment.job()), previous = owners.putIfAbsent(assignment.table(), owner);
            if (previous != null && !previous.equals(owner)) throw new IllegalArgumentException("Rows of one Snakemake benchmark file are repeats of one job; do not relabel them as different jobs/pipelines: " + assignment);
            Group group = new Group(assignment.pipeline(), assignment.replicate());
            if (!jobs.add(new Job(group, assignment.job()))) throw new IllegalArgumentException("Duplicate job in pipeline replicate: " + assignment);
            Measurement value = values.get(assignment.row() - 1); Summary old = result.get(group);
            result.put(group, old == null ? new Summary(1, value.cpuSeconds(), value.maxRssMiB(), value.wallSeconds())
                : new Summary(Math.addExact(old.jobs(), 1), old.cpuSeconds().add(value.cpuSeconds()), old.maxJobRssMiB().max(value.maxRssMiB()), old.summedJobWallSeconds().add(value.wallSeconds())));
        }
        if (selected.size() != rows) throw new IllegalArgumentException("Unassigned benchmark rows remain; declare every repeat explicitly, never select a convenient subset");
        return java.util.Collections.unmodifiableSortedMap(result);
    }

    private static BigDecimal decimal(String text, String column, int row) throws IOException {
        if (text.length() > 128 || !NUMBER.matcher(text).matches()) throw new IOException("Benchmark " + column + " at row " + row + " is missing or nonnumeric (" + text + "). No zero imputation or partial aggregate. Retain raw TSV and obtain complete permitted measurements.");
        try {
            BigDecimal value = new BigDecimal(text);
            if (Math.abs((long) value.scale()) > 1000 || value.signum() < 0) throw new NumberFormatException("Nonnegative bounded decimal required");
            return value;
        } catch (NumberFormatException error) { throw new IOException("Invalid/nonfinite/negative or excessive benchmark decimal in " + column + " row " + row + "; retain input and correct the named cause", error); }
    }
    private static void nonnegative(BigDecimal value) {
        Objects.requireNonNull(value); if (value.signum() < 0 || Math.abs((long) value.scale()) > 1000 || value.precision() > 128) throw new IllegalArgumentException("Nonnegative bounded decimal measurement required");
    }
    private static void identifier(String value) {
        if (value == null || value.isEmpty() || value.length() > 4096 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Nonempty bounded identifier without control characters required");
    }
    private static String line(Reader reader, int maximum) throws IOException {
        StringBuilder result = new StringBuilder(); int ch;
        while ((ch = reader.read()) != -1 && ch != '\n') {
            if (result.length() >= maximum) throw new IOException("Benchmark line exceeds maxLineCharacters; no truncated row admitted");
            result.append((char) ch);
        }
        if (ch == -1) {
            if (result.isEmpty()) return null;
            throw new IOException("Unterminated benchmark line; retain raw input and obtain the complete upstream TSV");
        }
        if (!result.isEmpty() && result.charAt(result.length() - 1) == '\r') result.setLength(result.length() - 1);
        return result.toString();
    }
    private static final class Capped extends FilterInputStream {
        private final long maximum;
        private long count;
        Capped(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        private void consumed(int n) throws IOException {
            if (n > 0) { count = Math.addExact(count, n); if (count > maximum) throw new IOException("Benchmark grew beyond maxBytes during reading; no prefix result admitted"); }
        }
        @Override public int read() throws IOException { int value = in.read(); consumed(value == -1 ? 0 : 1); return value; }
        @Override public int read(byte[] values, int offset, int length) throws IOException { int n = in.read(values, offset, length); consumed(n); return n; }
    }
}
