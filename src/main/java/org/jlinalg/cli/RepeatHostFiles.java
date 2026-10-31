/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Complete bounded host-file census primitive; this alone does not admit a scientific runtime. */
final class RepeatHostFiles {
    private RepeatHostFiles() { }

    record Limits(long fileBytes, long logicalBytes, int members, int roots, long metadataBytes) {
        Limits {
            if (fileBytes < 1 || fileBytes > 1099511627776L || logicalBytes < 1 || logicalBytes > 1099511627776L
                    || members < 1 || members > 100000 || roots < 1 || roots > 128
                    || metadataBytes < 1 || metadataBytes > 67108864L)
                throw new IllegalArgumentException("Require explicit bounded complete host census capacities");
        }
    }
    record FilePin(String realPath, long bytes, String sha256, int mode, boolean readable, boolean executable) { }
    record Entry(String kind, String realPath, String linkTarget, String targetKind, FilePin file, int mode) { }
    record Snapshot(List<String> requestedRoots, List<String> requestedFiles, List<String> requestedNodes, Map<String, Entry> roots,
            Map<String, Map<String, Entry>> trees, Map<String, Entry> files, Map<String,Entry> nodes, long logicalBytes,
            int members, long metadataReservation, long physicalHashes) {
        Snapshot {
            requestedRoots = List.copyOf(requestedRoots); requestedFiles = List.copyOf(requestedFiles);requestedNodes=List.copyOf(requestedNodes);
            roots = ordered(roots); files = ordered(files);nodes=ordered(nodes);
            var copy = new TreeMap<String, Map<String, Entry>>();
            trees.forEach((key, value) -> copy.put(key, ordered(value)));
            trees = Collections.unmodifiableMap(copy);
        }
        boolean sameContents(Snapshot other) {
            // The number of physical reads is observational, not a file/content identity.
            return requestedRoots.equals(other.requestedRoots) && requestedFiles.equals(other.requestedFiles) &&requestedNodes.equals(other.requestedNodes)
                && roots.equals(other.roots) && trees.equals(other.trees) && files.equals(other.files) &&nodes.equals(other.nodes)
                && logicalBytes == other.logicalBytes && members == other.members
                && metadataReservation == other.metadataReservation;
        }
    }
    private record Stamp(Object key, long size, FileTime modified, FileTime created, FileTime changed, int mode) { }

    static Snapshot capture(List<Path> requestedRoots, List<Path> requestedFiles, Set<Path> treeTargets,
            Set<Path> fileTargets, Limits limits, Runnable check) throws IOException {
        return capture(requestedRoots,requestedFiles,List.of(),treeTargets,fileTargets,limits,check);
    }
    /** Declared nodes record presence/type/mode without recursively reading directory contents. */
    static Snapshot capture(List<Path> requestedRoots,List<Path> requestedFiles,List<Path> requestedNodes,Set<Path> treeTargets,
            Set<Path> fileTargets,Limits limits,Runnable check)throws IOException{
        var capture = new Capture(limits, treeTargets, fileTargets, check);
        return capture.read(requestedRoots, requestedFiles,requestedNodes);
    }
    static void verify(Snapshot expected, Set<Path> treeTargets, Set<Path> fileTargets,
            Limits limits, Runnable check) throws IOException {
        Snapshot actual = capture(expected.requestedRoots().stream().map(Path::of).toList(),
            expected.requestedFiles().stream().map(Path::of).toList(),expected.requestedNodes().stream().map(Path::of).toList(), treeTargets, fileTargets, limits, check);
        if (!expected.sameContents(actual)) throw new IOException("Complete declared host files, roots, links, permissions or membership changed");
    }
    static byte[] bytes(Snapshot snapshot, Limits limits) throws IOException {
        byte[] raw = RepeatBundle.json(snapshot);
        if (raw.length > limits.metadataBytes()) throw new IOException("Complete host census metadata exceeds its byte capacity");
        return raw;
    }
    private static <T> Map<String, T> ordered(Map<String, T> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }
    private static final class Capture {
        final Limits limits;
        final Set<Path> treeTargets, fileTargets;
        final Runnable check;
        final Map<Path, Stamp> stability = new LinkedHashMap<>();
        final Map<Stamp, String> hashes = new HashMap<>();
        final ArrayDeque<Path> pending = new ArrayDeque<>();
        final Map<String, Map<String, Entry>> trees = new TreeMap<>();
        long logicalBytes, metadata = 1024, physicalHashes;
        int members;
        Capture(Limits limits, Set<Path> treeTargets, Set<Path> fileTargets, Runnable check) throws IOException {
            this.limits = java.util.Objects.requireNonNull(limits); this.check = java.util.Objects.requireNonNull(check);
            this.treeTargets = targets(treeTargets); this.fileTargets = targets(fileTargets);
            if (metadata > limits.metadataBytes()) throw new IOException("Complete host census header exceeds its metadata capacity");
        }
        Snapshot read(List<Path> rootPaths, List<Path> filePaths,List<Path> nodePaths) throws IOException {
            List<String> requestedRoots = names(rootPaths), requestedFiles = names(filePaths),requestedNodes=names(nodePaths);
            if (requestedRoots.size() > limits.roots()) throw new IOException("Complete host tree-root capacity exceeded");
            var roots = new TreeMap<String, Entry>(); var files = new TreeMap<String, Entry>();var nodes=new TreeMap<String,Entry>();
            for (String name : requestedRoots) {
                Entry entry = describe(Path.of(name), treeTargets);
                if (entry.kind().equals("REGULAR") || entry.targetKind().equals("REGULAR"))
                    throw new IOException("Host module root must be a directory or absent: " + name);
                roots.put(name, entry); queue(entry);
            }
            for (String name : requestedFiles) {
                Entry entry = describe(Path.of(name), fileTargets);
                if (entry.kind().equals("DIRECTORY") || entry.targetKind().equals("DIRECTORY"))
                    throw new IOException("Host helper/configuration must be a regular file or absent: " + name);
                files.put(name, entry);
            }
            for(String name:requestedNodes)nodes.put(name,describe(Path.of(name),fileTargets));
            while (!pending.isEmpty()) {
                Path root = pending.removeFirst(); String key = root.toString();
                if (trees.containsKey(key)) continue;
                if (trees.size() >= limits.roots()) throw new IOException("Complete linked host tree-root capacity exceeded");
                var entries = new TreeMap<String, Entry>(); trees.put(key, entries);
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    private void add(Path path) throws IOException {
                        String relative = root.relativize(path).toString();
                        if (relative.isEmpty()) relative = ".";
                        Entry entry = describe(path, treeTargets);
                        if (entries.putIfAbsent(relative, entry) != null) throw new IOException("Duplicate host module member");
                        if (entry.kind().equals("SYMLINK")) queue(entry);
                    }
                    @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                        add(path); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                        add(path); return FileVisitResult.CONTINUE;
                    }
                });
            }
            // Catch membership/permission/content changes occurring after an earlier directory or file was visited.
            for (var entry : stability.entrySet()) {
                check.run();
                if (!entry.getValue().equals(stamp(entry.getKey())))
                    throw new IOException("Host member changed during its complete census: " + entry.getKey());
            }
            var result = new Snapshot(requestedRoots, requestedFiles,requestedNodes, roots, trees, files,nodes,
                logicalBytes, members, metadata, physicalHashes);
            bytes(result, limits); check.run(); return result;
        }
        private void queue(Entry entry) {
            if (entry.kind().equals("DIRECTORY") || entry.targetKind().equals("DIRECTORY")) pending.add(Path.of(entry.realPath()));
        }
        private Entry describe(Path supplied, Set<Path> allowed) throws IOException {
            check.run(); Path path = absolute(supplied); String name = path.toString();
            if (++members > limits.members()) throw new IOException("Complete host member capacity exceeded");
            BasicFileAttributes attr;
            try { attr = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException absent) {
                Path resolved = missingTarget(path); allow(resolved, allowed); reserve(name, resolved.toString(), "");
                return new Entry("ABSENT", resolved.toString(), "", "ABSENT", null, -1);
            }
            remember(path); int mode = mode(path);
            if (attr.isSymbolicLink()) {
                Path rawTarget = Files.readSymbolicLink(path); text(rawTarget);
                Path target;
                try { target = path.toRealPath(); }
                catch (NoSuchFileException absent) { target = missingTarget(path); }
                allow(target, allowed); String kind; FilePin file = null;
                try {
                    var targetAttributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (targetAttributes.isRegularFile()) { kind = "REGULAR"; file = fingerprint(target); }
                    else if (targetAttributes.isDirectory()) { kind = "DIRECTORY"; remember(target); }
                    else throw new IOException("Unsupported linked host member: " + path);
                } catch (NoSuchFileException absent) { kind = "ABSENT"; }
                reserve(name, target.toString(), rawTarget.toString());
                return new Entry("SYMLINK", target.toString(), rawTarget.toString(), kind, file, mode);
            }
            Path real = path.toRealPath(); allow(real, allowed); reserve(name, real.toString(), "");
            if (attr.isDirectory()) return new Entry("DIRECTORY", real.toString(), "", "DIRECTORY", null, mode);
            if (attr.isRegularFile()) return new Entry("REGULAR", real.toString(), "", "REGULAR", fingerprint(real), mode);
            throw new IOException("Unsupported nonregular host module member: " + path);
        }
        private FilePin fingerprint(Path path) throws IOException {
            check.run(); Stamp before = stamp(path); remember(path);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().equals(path))
                throw new IOException("Host file must resolve to a canonical regular file");
            if (before.size() > limits.fileBytes()) throw new IOException("Complete host file exceeds its byte capacity: " + path);
            logicalBytes = Math.addExact(logicalBytes, before.size());
            if (logicalBytes > limits.logicalBytes()) throw new IOException("Complete logical host bytes exceed their capacity");
            // A fresh Capture owns this cache. It can never survive an intervening original process.
            String sha = before.key() != null && before.changed() != null ? hashes.get(before) : null;
            if (sha == null) { sha = RepeatInputs.hash(path, limits.fileBytes(), check); physicalHashes++; }
            if (!before.equals(stamp(path))) throw new IOException("Host file changed across its complete read: " + path);
            if (before.key() != null && before.changed() != null) hashes.put(before, sha);
            return new FilePin(path.toString(), before.size(), sha, before.mode(), Files.isReadable(path), Files.isExecutable(path));
        }
        private void reserve(String path, String real, String target) throws IOException {
            metadata = Math.addExact(metadata, 1024L + 12L * (path.length() + real.length() + target.length()));
            if (metadata > limits.metadataBytes()) throw new IOException("Complete host metadata reservation exceeds its capacity");
        }
        private void remember(Path path) throws IOException {
            Stamp current = stamp(path), before = stability.putIfAbsent(path, current);
            if (before != null && !before.equals(current)) throw new IOException("Host member changed between census aliases: " + path);
        }
    }
    private static Set<Path> targets(Set<Path> values) throws IOException {
        var result = new java.util.HashSet<Path>();
        for (Path path : values) result.add(absolute(path));
        if (result.isEmpty()) throw new IOException("Explicit host target roots are required");
        return Set.copyOf(result);
    }
    private static List<String> names(List<Path> paths) throws IOException {
        var result = new ArrayList<String>(); var seen = new java.util.HashSet<Path>();
        for (Path supplied : paths) {
            Path path = absolute(supplied);
            if (!seen.add(path)) throw new IOException("Duplicate requested host path: " + path);
            result.add(path.toString());
        }
        return List.copyOf(result);
    }
    private static Path absolute(Path path) throws IOException {
        if (!path.isAbsolute() || !path.normalize().equals(path)) throw new IOException("Require absolute normalized host paths");
        text(path); return path;
    }
    private static void text(Path path) throws IOException {
        String name = path.toString();
        if (name.length() > 4096 || name.chars().anyMatch(c -> c < 32 || c == 127) || !Path.of(name).equals(path))
            throw new IOException("Unsupported non-roundtrippable/control/oversized host path");
    }
    private static void allow(Path path, Set<Path> roots) throws IOException {
        absolute(path);
        if (roots.stream().noneMatch(path::startsWith)) throw new IOException("Host member resolves outside declared roots: " + path);
    }
    private static Path missingTarget(Path path) throws IOException {
        // Resolve the longest existing prefix; do not silently classify access errors or loops as absence.
        var suffix = new ArrayDeque<Path>(); Path prefix = path;
        while (prefix != null) {
            try {
                Path real = prefix.toRealPath();
                while (!suffix.isEmpty()) real = real.resolve(suffix.removeFirst());
                return real.normalize();
            } catch (NoSuchFileException absent) {
                if (Files.isSymbolicLink(prefix)) {
                    Path link = Files.readSymbolicLink(prefix);
                    Path candidate = link.isAbsolute() ? link : prefix.getParent().resolve(link);
                    // Dangling targets are recorded conservatively; a later resolution is re-derived in verify.
                    while (!suffix.isEmpty()) candidate = candidate.resolve(suffix.removeFirst());
                    return candidate.toAbsolutePath().normalize();
                }
                if (prefix.getFileName() != null) suffix.addFirst(prefix.getFileName());
                prefix = prefix.getParent();
            }
        }
        throw new IOException("Cannot resolve a missing host path prefix: " + path);
    }
    private static Stamp stamp(Path path) throws IOException {
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        FileTime changed = null;
        try { changed = (FileTime) Files.getAttribute(path, "unix:ctime", LinkOption.NOFOLLOW_LINKS); }
        catch (UnsupportedOperationException | IllegalArgumentException unsupported) { /* No cross-alias hash cache. */ }
        return new Stamp(a.fileKey(), a.size(), a.lastModifiedTime(), a.creationTime(), changed, mode(path));
    }
    private static int mode(Path path) throws IOException {
        try {
            Object value=Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS);
            if(value instanceof Number number)return number.intValue()&07777;
        }catch(UnsupportedOperationException|IllegalArgumentException unsupported){/* Portable permission fallback. */}
        try {
            int result = 0;
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            for (PosixFilePermission p : permissions) result |= switch (p) {
                case OWNER_READ -> 0400; case OWNER_WRITE -> 0200; case OWNER_EXECUTE -> 0100;
                case GROUP_READ -> 0040; case GROUP_WRITE -> 0020; case GROUP_EXECUTE -> 0010;
                case OTHERS_READ -> 0004; case OTHERS_WRITE -> 0002; case OTHERS_EXECUTE -> 0001;
            };
            return result;
        } catch (UnsupportedOperationException unsupported) { return -1; }
    }
}
