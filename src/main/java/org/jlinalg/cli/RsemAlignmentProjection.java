/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.sequence.RsemSamAlignments;

/** Streaming independent expected original parser outputs, never replacement fitting input. */
final class RsemAlignmentProjection implements RsemSamAlignments.Sink, AutoCloseable {
    private static final String[] CATEGORIES = {"un", "alignable", "max"};
    private final Path home;
    private final RsemSamAlignments.Options options;
    private final long totalLimit, fileLimit;
    private final int fileCount;
    private final Runnable check;
    private final Map<String,OutputStream> outputs = new LinkedHashMap<>();
    private final long[] categories = new long[3];
    private long bytes, records, groups, hits;
    private boolean finished, closed;

    RsemAlignmentProjection(Path home, RsemSamAlignments.Options options, long totalBytes, long fileBytes, int files, Runnable check) throws IOException {
        if (!home.isAbsolute() || !home.normalize().equals(home) || home.getParent() == null || !home.getParent().toRealPath().equals(home.getParent()))
            throw new IOException("Expected alignment projection requires a canonical NEW directory");
        if (totalBytes < 100 || fileBytes < 100 || files < 3) throw new IOException("Insufficient complete expected-projection capacity");
        this.home = home; this.options = java.util.Objects.requireNonNull(options); totalLimit = totalBytes; fileLimit = fileBytes; fileCount = files; this.check = java.util.Objects.requireNonNull(check);
        check.run(); Files.createDirectory(home);
        try { text("expected.dat", " ".repeat(99) + "\n"); }
        catch (IOException | RuntimeException failure) { try { close(); } catch (IOException extra) { failure.addSuppressed(extra); } throw failure; }
    }
    @Override public void record(RsemSamAlignments.Record record) throws IOException {
        active(); if (record.ordinal() != records + 1) throw new IOException("Expected projection physical record population is noncontiguous"); records++;
    }
    @Override public void finding(RsemSamAlignments.Finding finding, long ordinal, String detail) throws IOException {
        check.run();
        if (finding.blocksFitting()) output("blocking-findings.jsonl").write(RsemJson.bytes(Map.of("finding", finding.name(), "physical_record", ordinal, "detail", detail)));
    }
    @Override public void group(RsemSamAlignments.Group group) throws IOException {
        active(); check.run();
        if (group.ordinal() != groups + 1 || group.category() < 0 || group.category() > 2) throw new IOException("Invalid complete expected parser group population");
        groups++; categories[group.category()]++;
        if (group.category() == 1) {
            hits += group.hits().size(); text("expected.dat", Integer.toString(group.hits().size()));
            for (var hit : group.hits()) {
                text("expected.dat", " " + hit.signedSid() + " " + hit.position());
                if (options.paired()) text("expected.dat", " " + hit.insertLength());
            }
            text("expected.dat", "\n");
        }
        read(group.category(), 0, group.name(), group.sequence(), group.quality());
        if (options.paired()) read(group.category(), 1, group.mate2Name(), group.mate2Sequence(), group.mate2Quality());
    }
    private void read(int category, int mate, String name, String sequence, String quality) throws IOException {
        if (name == null || sequence == null || options.qualities() && quality == null)
            throw new IOException("Expected original read export lacks source name/sequence/quality; no repair or quality imputation is admitted");
        String file = "expected_" + CATEGORIES[category] + (options.paired() ? "_" + (mate + 1) : "") + (options.qualities() ? ".fq" : ".fa");
        text(file, (options.qualities() ? "@" : ">") + name + "\n"); text(file, sequence); text(file, "\n");
        if (options.qualities()) { text(file, "+\n"); text(file, quality); text(file, "\n"); }
    }
    void finish(RsemSamAlignments.Summary summary) throws IOException {
        active(); check.run();
        var accounting = summary.accounting();
        if (summary.records() != records || summary.groups() != groups || categories[0] != accounting.unalignable()
                || categories[1] != accounting.alignable() || categories[2] != accounting.excluded() || hits != accounting.hits() || accounting.readType() != options.readType())
            throw new IOException("Complete native projection and accounting populations disagree");
        text("expected.cnt", accounting.originalText());
        output("expected.omit"); for (int sid : summary.omittedSids()) text("expected.omit", sid + "\n");
        IOException failure = closeOutputs(); if (failure != null) throw failure;
        String header = accounting.alignable() + " " + accounting.hits() + " " + options.readType();
        if (header.length() > 99) throw new IOException("Complete original data header exceeds its fixed reserved width");
        try (var file = Files.newByteChannel(home.resolve("expected.dat"), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer raw = ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII)); while (raw.hasRemaining()) file.write(raw);
        }
        finished = true;
    }
    Path home() { return home; }
    boolean finished() { return finished; }
    long bytes() { return bytes; }
    private void active() throws IOException { if (finished || closed) throw new IOException("Expected parser projection is already closed or completed"); }
    private void text(String name, String text) throws IOException { output(name).write(text.getBytes(StandardCharsets.UTF_8)); }
    private OutputStream output(String name) throws IOException {
        if (closed) throw new IOException("Expected parser projection is closed");
        OutputStream existing = outputs.get(name); if (existing != null) return existing;
        if (outputs.size() >= fileCount) throw new IOException("Complete expected parser file population exceeds capacity");
        var raw = Files.newOutputStream(home.resolve(name), StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS);
        var bounded = new OutputStream() {
            private long size;
            @Override public void write(int value) throws IOException { write(new byte[]{(byte)value}, 0, 1); }
            @Override public void write(byte[] value, int offset, int count) throws IOException {
                check.run();
                if (count > fileLimit - size || count > totalLimit - bytes) throw new IOException("Complete expected parser output bytes exceed capacity; no truncation is admitted");
                raw.write(value, offset, count); size += count; bytes += count;
            }
            @Override public void close() throws IOException { raw.close(); }
        };
        var buffered = new BufferedOutputStream(bounded, 65536); outputs.put(name, buffered); return buffered;
    }
    private IOException closeOutputs() {
        IOException failure = null;
        for (var output : outputs.values()) try { output.close(); } catch (IOException extra) { if (failure == null) failure = extra; else failure.addSuppressed(extra); }
        closed = true; return failure;
    }
    @Override public void close() throws IOException { if (!closed) { IOException failure = closeOutputs(); if (failure != null) throw failure; } }
}
