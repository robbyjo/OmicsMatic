/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete regular-file input snapshots and separate original-path working copies. */
final class Pbmm2Capture {
    private Pbmm2Capture() { }
    record Captured(Map<String, Pin> files, Set<String> directories, long bytes) {
        Captured { files = Collections.unmodifiableMap(new TreeMap<>(files)); directories = Collections.unmodifiableSet(new TreeSet<>(directories)); }
    }

    static Captured census(Path root, long maximumBytes, long maximumFileBytes, int maximumFiles) throws IOException {
        directory(root);
        var files = new TreeMap<String, Pin>(); var dirs = new TreeSet<String>(); long total = 0;
        try (var stream = Files.walk(root)) {
            for (var iterator = stream.iterator(); iterator.hasNext();) {
                Path p = iterator.next();
                if (p.equals(root)) continue;
                String relative = member(root.relativize(p).toString().replace('\\', '/'));
                if (Files.isSymbolicLink(p)) throw new NextflowProcess.Unavailable("This prepared-input snapshot profile does not qualify symbolic links. Supply complete permitted regular-file roots or retain the skipped analysis; no link target is silently substituted: " + p);
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) { directory(p); dirs.add(relative); }
                else {
                    if (files.size() >= maximumFiles) throw new IOException("Complete pbmm2 file-count capacity exceeded; no file subset");
                    Pin value = pin(p, maximumFileBytes, () -> { });
                    total = Math.addExact(total, value.bytes());
                    if (total > maximumBytes) throw new IOException("Complete pbmm2 input-byte capacity exceeded; no reference/read subset");
                    files.put(relative, value);
                }
            }
        }
        return new Captured(files, dirs, total);
    }

    static Captured copyWhole(Path original, Path destination, long maximumBytes, long maximumFileBytes, int maximumFiles) throws IOException {
        if (!Files.exists(original, LinkOption.NOFOLLOW_LINKS)) throw new NextflowProcess.Unavailable("Required complete pbmm2 input root is missing: " + original + "; restore the permitted exact input or retain the skipped analysis");
        if (Files.isSymbolicLink(original)) throw new NextflowProcess.Unavailable("A literal root symlink is outside this regular-file snapshot profile; declare a complete permitted physical root without changing the scientific inputs, or retain the skip");
        Captured before = census(original, maximumBytes, maximumFileBytes, maximumFiles);
        Files.createDirectory(destination);
        for (String name : before.directories()) Files.createDirectories(destination.resolve(name));
        for (var entry : before.files().entrySet()) {
            Path to = destination.resolve(entry.getKey()); Files.createDirectories(to.getParent());
            Files.copy(original.resolve(entry.getKey()), to);
            if (!pin(to, maximumFileBytes, () -> { }).equals(entry.getValue())) throw new IOException("Complete pbmm2 input copy differs: " + entry.getKey());
        }
        if (!before.equals(census(original, maximumBytes, maximumFileBytes, maximumFiles)) || !before.equals(census(destination, maximumBytes, maximumFileBytes, maximumFiles))) throw new IOException("Complete pbmm2 input changed during capture; no analysis admitted");
        return before;
    }

    static Map<String, Object> json(Captured value) { return Map.of("files", value.files(), "directories", value.directories(), "bytes", value.bytes()); }

    static Captured decode(Object value) throws IOException {
        var row = object(value); keys(row, "files", "directories", "bytes"); var files = new TreeMap<String, Pin>(); long total = 0;
        for (var entry : object(row.get("files")).entrySet()) {
            Pin pin = fingerprint(entry.getValue(), 1000000000000L); files.put(member(text(entry.getKey())), pin); total = Math.addExact(total, pin.bytes());
        }
        var dirs = new TreeSet<String>(); for (String name : strings(row.get("directories"))) if (!dirs.add(member(name))) throw new IOException("Repeated input snapshot directory");
        if (integer(row.get("bytes"), 0, 1000000000000L) != total) throw new IOException("Whole pbmm2 snapshot byte mass differs");
        return new Captured(files, dirs, total);
    }

    static Map<String, Captured> prepare(Path root, Pbmm2Request.Request request) throws IOException {
        return prepare(root, request, null);
    }
    static Map<String, Captured> prepare(Path root, Pbmm2Request.Request request, Path replay) throws IOException {
        Path inputs = Files.createDirectory(root.resolve("inputs")), staged = Files.createDirectory(root.resolve("staged"));
        var captures = new LinkedHashMap<String, Captured>(); long total = 0; int files = 0;
        for (var resource : request.resources()) {
            Path source = replay == null ? Path.of(resource.original()) : replay.resolve("inputs").resolve(resource.id());
            Captured capture = copyWhole(source, inputs.resolve(resource.id()), request.inputBytes() - total, request.fileBytes(), request.files() - files);
            total = Math.addExact(total, capture.bytes()); files = Math.addExact(files, capture.files().size());
            copyWhole(inputs.resolve(resource.id()), staged.resolve(resource.id()), request.inputBytes(), request.fileBytes(), request.files());
            captures.put(resource.id(), capture);
        }
        var metadata = new LinkedHashMap<String, Object>();
        for (var entry : captures.entrySet()) metadata.put(entry.getKey(), json(entry.getValue()));
        writeJson(root.resolve("input-capture.json"), Map.of("schema_version", 1, "resources", metadata, "total_bytes", total, "total_files", files, "original_locator_strings_rewritten", false));
        verify(root, request, captures, false);
        return captures;
    }

    static Map<String, Captured> saved(Path root, Pbmm2Request.Request request) throws IOException {
        var capture = object(NextflowFiles.json(root.resolve("input-capture.json"), 67108864));
        keys(capture, "schema_version", "resources", "total_bytes", "total_files", "original_locator_strings_rewritten"); integer(capture.get("schema_version"), 1, 1);
        if (!Boolean.FALSE.equals(capture.get("original_locator_strings_rewritten"))) throw new IOException("Original native resource locators were rewritten");
        var result = new LinkedHashMap<String, Captured>(); long total = 0; int files = 0;
        for (var resource : request.resources()) {
            Captured value = decode(object(capture.get("resources")).get(resource.id())); result.put(resource.id(), value);
            total = Math.addExact(total, value.bytes()); files = Math.addExact(files, value.files().size());
        }
        if (!result.keySet().equals(object(capture.get("resources")).keySet()) || total != integer(capture.get("total_bytes"), 0, request.inputBytes()) || files != integer(capture.get("total_files"), 0, request.files())) throw new IOException("Whole pbmm2 resource-root population differs");
        return result;
    }

    static void verify(Path root, Pbmm2Request.Request request, Map<String, Captured> captures, boolean permitGenerated) throws IOException {
        for (var resource : request.resources()) {
            Captured expected = captures.get(resource.id());
            if (expected == null || !expected.equals(census(root.resolve("inputs").resolve(resource.id()), request.inputBytes(), request.fileBytes(), request.files()))) throw new IOException("Immutable complete pbmm2 input snapshot differs: " + resource.id());
            Captured working = census(root.resolve("staged").resolve(resource.id()), request.process().workspaceBytes(), request.process().fileBytes(), request.process().fileCount());
            if (!working.files().entrySet().containsAll(expected.files().entrySet()) || !working.directories().containsAll(expected.directories())) throw new IOException("Original staged pbmm2 bytes were modified; no complete result admitted");
            if (!permitGenerated && !expected.equals(working)) throw new IOException("Unexpected preparation artifact in original-path staging");
        }
    }
}
