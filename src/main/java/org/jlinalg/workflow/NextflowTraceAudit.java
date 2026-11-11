/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded native inspection of complete, single-attempt Nextflow TSV traces.
 *
 * <p>The expected row count must come from an independent caller declaration or
 * execution receipt. This reader cannot discover unrecorded tasks. Its success
 * describes the observed trace and engine exit; it does not certify pipeline
 * outputs, biological results, runtime provenance or a complete workflow bundle.
 * Retries with repeated task IDs require a separate attempt-aware contract and
 * are rejected rather than silently dropping failed-attempt evidence.</p>
 */
public final class NextflowTraceAudit {
    private NextflowTraceAudit() { }

    public record Limits(long maxBytes, int maxLineChars, int maxRows, int maxColumns) {
        public Limits {
            if (maxBytes < 1 || maxLineChars < 1 || maxRows < 0 || maxColumns < 5)
                throw new IllegalArgumentException("Invalid Nextflow trace capacities");
        }
        public static Limits defaults() { return new Limits(64L * 1024 * 1024, 32768, 100000, 128); }
    }

    public enum Outcome { SUCCESSFUL_OBSERVED_TASKS, FAILED_OR_INCOMPLETE, EMPTY }

    public record Summary(long taskRows, long completed, long cached, long other,
            int engineExitCode, Outcome outcome, long bytes, String sha256) {
        public boolean allObservedTasksSuccessful() { return outcome == Outcome.SUCCESSFUL_OBSERVED_TASKS; }
    }

    private static final Set<String> STATUSES = Set.of("NEW", "SUBMITTED", "RUNNING", "COMPLETED", "CACHED", "FAILED", "ABORTED");

    /** Reads to EOF or throws; no partial summary is returned on a malformed or bounded-out trace. */
    public static Summary read(Path trace, long expectedRows, int engineExitCode,
            Limits limits, Runnable cancellation) throws IOException {
        Objects.requireNonNull(trace); Objects.requireNonNull(limits); Objects.requireNonNull(cancellation);
        if (expectedRows < 0 || expectedRows > limits.maxRows || engineExitCode < 0)
            throw new IllegalArgumentException("Declare the complete expected row count and actual engine exit code");
        cancellation.run();
        BasicFileAttributes before = Files.readAttributes(trace, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > limits.maxBytes)
            throw new IOException("Nextflow trace is linked/nonregular or exceeds byte capacity");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        var ids = new HashSet<Long>();
        long rows = 0, completed = 0, cached = 0, other = 0;
        long bytes;
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var input = new BoundedInput(Files.newInputStream(trace, LinkOption.NOFOLLOW_LINKS), limits.maxBytes);
             var reader = new BufferedReader(new InputStreamReader(new DigestInputStream(input, digest), decoder))) {
            String header = line(reader, limits.maxLineChars, cancellation);
            if (header == null) throw new IOException("Missing complete Nextflow trace header");
            String[] fields = header.split("\t", -1);
            if (fields.length > limits.maxColumns) throw new IOException("Nextflow trace column capacity exceeded");
            Map<String,Integer> columns = new HashMap<>();
            for (int i = 0; i < fields.length; i++)
                if (fields[i].isBlank() || columns.putIfAbsent(fields[i], i) != null)
                    throw new IOException("Blank or duplicate Nextflow trace column");
            for (String required : Set.of("task_id", "hash", "name", "status", "exit"))
                if (!columns.containsKey(required)) throw new IOException("Missing Nextflow trace column: " + required);
            String value;
            while ((value = line(reader, limits.maxLineChars, cancellation)) != null) {
                cancellation.run();
                if (++rows > limits.maxRows || rows > expectedRows) throw new IOException("Nextflow trace exceeds declared complete row count/capacity");
                String[] cells = value.split("\t", -1);
                if (cells.length != fields.length) throw new IOException("Incomplete or extra Nextflow trace cells at row " + rows);
                long id = positiveId(cells[columns.get("task_id")]);
                if (!ids.add(id)) throw new IOException("Repeated task_id requires an attempt-aware trace contract: " + id);
                String status = cells[columns.get("status")], name = cells[columns.get("name")], hash = cells[columns.get("hash")];
                if (!STATUSES.contains(status) || name.isBlank() || name.chars().anyMatch(c -> c < 32 || c == 127))
                    throw new IOException("Unknown status or invalid task name at row " + rows);
                boolean successful = status.equals("COMPLETED") || status.equals("CACHED");
                if (!hash.matches("[0-9a-f]{2}/[0-9a-f]{6,64}") && (successful || !hash.equals("-")))
                    throw new IOException("Invalid Nextflow task hash at row " + rows);
                String exit = cells[columns.get("exit")];
                int taskExit = exit.equals("-") ? -1 : exitCode(exit);
                if (successful && taskExit != 0) throw new IOException("Successful task has missing/nonzero exit at row " + rows);
                if (status.equals("COMPLETED")) completed++;
                else if (status.equals("CACHED")) cached++;
                else other++;
            }
            bytes = input.count;
        }
        if (rows != expectedRows) throw new IOException("Incomplete Nextflow trace: expected " + expectedRows + " rows, found " + rows);
        BasicFileAttributes after = Files.readAttributes(trace, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || before.size() != bytes || after.size() != bytes
                || !before.lastModifiedTime().equals(after.lastModifiedTime()) || !Objects.equals(before.fileKey(), after.fileKey()))
            throw new IOException("Nextflow trace changed while reading");
        cancellation.run();
        Outcome outcome = engineExitCode != 0 || other != 0 ? Outcome.FAILED_OR_INCOMPLETE
                : rows == 0 ? Outcome.EMPTY : Outcome.SUCCESSFUL_OBSERVED_TASKS;
        return new Summary(rows, completed, cached, other, engineExitCode, outcome, bytes, HexFormat.of().formatHex(digest.digest()));
    }

    private static long positiveId(String value) throws IOException {
        try {
            if (!value.matches("[1-9][0-9]*")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException failure) { throw new IOException("Invalid Nextflow task_id", failure); }
    }

    private static int exitCode(String value) throws IOException {
        try {
            if (!value.matches("0|[1-9][0-9]*")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) { throw new IOException("Invalid Nextflow task exit code", failure); }
    }

    private static String line(BufferedReader input, int maximum, Runnable cancellation) throws IOException {
        var value = new StringBuilder();
        for (;;) {
            int c = input.read();
            if (c == -1) {
                if (!value.isEmpty()) throw new IOException("Unterminated Nextflow trace line; no prefix is admitted");
                return null;
            }
            if (c == '\n') {
                if (!value.isEmpty() && value.charAt(value.length() - 1) == '\r') value.setLength(value.length() - 1);
                if (value.indexOf("\r") >= 0) throw new IOException("Embedded carriage return in Nextflow trace");
                return value.toString();
            }
            if (value.length() >= maximum) throw new IOException("Nextflow trace line capacity exceeded");
            value.append((char)c);
            if ((value.length() & 1023) == 0) cancellation.run();
        }
    }

    private static final class BoundedInput extends FilterInputStream {
        private final long maximum;
        private long count;
        BoundedInput(InputStream input, long maximum) { super(input); this.maximum = maximum; }
        private void add(int n) throws IOException {
            if (n > 0) { count += n; if (count > maximum) throw new IOException("Nextflow trace byte capacity exceeded"); }
        }
        @Override public int read() throws IOException { int c = in.read(); if (c != -1) add(1); return c; }
        @Override public int read(byte[] b, int offset, int length) throws IOException { int n = in.read(b, offset, length); add(n); return n; }
    }
}
