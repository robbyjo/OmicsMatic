/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Complete native bundle accounting and read-only completion/hash verification. */
final class RepeatBundle {
    private RepeatBundle() { }
    static final long FAILURE_RESERVE = 65536;
    static final long FINALIZATION_RESERVE = 262144;
    static final int FINALIZATION_MEMBERS = 8;
    static final String TOOL = "RepeatMasker-native";
    record Limits(long bytes, int members) {
        Limits {
            if (bytes < 65536 || bytes > 1000000000000L || members < 8 || members > 1000000)
                throw new IllegalArgumentException("Require bounded native bundle byte/member capacities");
        }
    }
    record Inventory(long bytes, int members, Map<String,String> hashes) {
        Inventory { hashes = Map.copyOf(hashes); }
    }
    record Verification(String operation, String scope, long bytes, int members, int verifiedFiles) { }
    static byte[] json(Object value) throws IOException { return (RepeatJson.json(value)+"\n").getBytes(StandardCharsets.UTF_8); }
    static Map<?,?> object(Path path, long maximum) throws IOException {
        return object(AdmixtureInputs.raw(path, maximum));
    }
    static Map<?,?> object(byte[] raw) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        if (!(SimpleJson.parse(text) instanceof Map<?,?> result)) throw new IOException("Expected a complete UTF-8 JSON object");
        return result;
    }
    static Inventory inventory(Path suppliedRoot, Limits limits, boolean hashes, Runnable check) throws IOException {
        Path root = suppliedRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !root.toRealPath().equals(root))
            throw new IOException("Native bundle root must be a canonical real directory");
        var files = new TreeMap<String,String>(); long[] bytes = {0}; int[] members = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            private void member() throws IOException { check.run(); if (++members[0] > limits.members()) throw new IOException("Complete native bundle member capacity exceeded"); }
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                member(); if (!path.toRealPath().equals(path)) throw new IOException("Native bundle contains a linked directory");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                member(); if (!attributes.isRegularFile()) throw new IOException("Native bundle contains a linked/nonregular member");
                bytes[0] = Math.addExact(bytes[0], attributes.size());
                if (bytes[0] > limits.bytes()) throw new IOException("Complete native bundle byte capacity exceeded");
                if (hashes) files.put(root.relativize(path).toString().replace('\\','/'), RepeatInputs.hash(path, limits.bytes(), check));
                return FileVisitResult.CONTINUE;
            }
        });
        return new Inventory(bytes[0],members[0],files);
    }
    static void write(Path root, String name, byte[] data, Limits limits, long reservedBytes, int reservedMembers,
            Runnable check) throws IOException {
        if (!name.matches("[A-Za-z0-9_.-]+")) throw new IOException("Native metadata needs a fixed root filename");
        Inventory used = inventory(root,limits,false,check);
        if (Math.addExact(used.bytes(), Math.addExact(data.length,reservedBytes)) > limits.bytes()
                || Math.addExact(used.members(),1+reservedMembers) > limits.members())
            throw new IOException("Native bundle metadata exceeds combined capacity;failure reserve retained");
        Files.write(root.resolve(name),data,StandardOpenOption.CREATE_NEW);
    }
    static Map<String,Boolean> flags(RepeatRecipe.Operation operation) throws IOException {
        if (operation == RepeatRecipe.Operation.SEARCH) throw new IOException("Original SEARCH cannot use a native completion marker");
        return Map.of("input_check_completed",operation == RepeatRecipe.Operation.CHECK,
                "native_mask_completed",operation == RepeatRecipe.Operation.MASK,
                "supplied_mask_audit_completed",operation == RepeatRecipe.Operation.AUDIT,
                "upstream_search_completed",false,"external_annotation_completeness_certified",false,"biological_absence_certified",false);
    }
    static String scope(RepeatRecipe.Operation operation) throws IOException {
        return switch (operation) {
            case CHECK -> "structural-input-check-only";
            case MASK -> "complete-native-annotation-mask-only";
            case AUDIT -> "complete-supplied-mask-comparison-only";
            case SEARCH -> throw new IOException("Original SEARCH is outside native completion admission");
        };
    }
    static void complete(Path root, RepeatRecipe.Operation operation, Limits limits, Runnable check) throws IOException {
        var manifest = new LinkedHashMap<String,Object>();
        manifest.put("schema_version",1); manifest.put("tool",TOOL); manifest.put("operation",operation);
        manifest.put("exit_code",0); manifest.put("scope",scope(operation)); manifest.put("completion_flags",flags(operation));
        manifest.put("workspace_bytes",limits.bytes()); manifest.put("member_limit",limits.members());
        manifest.put("output_sha256",inventory(root,limits,true,check).hashes());
        byte[] raw = json(manifest);
        write(root,"manifest.json",raw,limits,FAILURE_RESERVE,2,check);
        write(root,"COMPLETED.json",json(Map.of("schema_version",1,"tool",TOOL,"operation",operation,"scope",scope(operation),
                "completion_flags",flags(operation),"manifest_sha256",AdmixtureInputs.digest(raw))),limits,FAILURE_RESERVE,1,check);
        verify(root,check);
    }
    static Verification verify(Path suppliedRoot, Runnable check) throws IOException {
        Path root = suppliedRoot.toAbsolutePath().normalize();
        String markerHash = RepeatInputs.hash(root.resolve("COMPLETED.json"),65536,check);
        Map<?,?> marker = object(root.resolve("COMPLETED.json"),65536), manifest = object(root.resolve("manifest.json"),2097152);
        if (!marker.keySet().equals(Set.of("schema_version","tool","operation","scope","completion_flags","manifest_sha256"))
                || !manifest.keySet().equals(Set.of("schema_version","tool","operation","exit_code","scope","completion_flags","workspace_bytes","member_limit","output_sha256")))
            throw new IOException("Unknown or incomplete native completion/manifest schema");
        if (!Long.valueOf(1).equals(marker.get("schema_version")) || !Long.valueOf(1).equals(manifest.get("schema_version"))
                || !TOOL.equals(marker.get("tool")) || !TOOL.equals(manifest.get("tool")) || !Long.valueOf(0).equals(manifest.get("exit_code")))
            throw new IOException("Native bundle is not a completed schema1 result");
        RepeatRecipe.Operation operation;
        try { operation = RepeatRecipe.Operation.valueOf(String.valueOf(manifest.get("operation"))); }
        catch (IllegalArgumentException invalid) { throw new IOException("Unknown native bundle operation",invalid); }
        if (!operation.name().equals(marker.get("operation")) || !scope(operation).equals(manifest.get("scope")) || !scope(operation).equals(marker.get("scope"))
                || !flags(operation).equals(marker.get("completion_flags")) || !flags(operation).equals(manifest.get("completion_flags")))
            throw new IOException("Native completion scope/flags differ or imply unsupported original completion");
        if (!RepeatInputs.hash(root.resolve("manifest.json"),2097152,check).equals(marker.get("manifest_sha256")))
            throw new IOException("Native completion manifest hash differs");
        if (!(manifest.get("workspace_bytes") instanceof Long bytes) || bytes < 65536 || bytes > 1000000000000L
                || !(manifest.get("member_limit") instanceof Long members) || members < 8 || members > 1000000)
            throw new IOException("Invalid native bundle literal integer capacities");
        Inventory actual = inventory(root,new Limits(bytes,members.intValue()),true,check);
        var observed = new TreeMap<>(actual.hashes()); observed.remove("manifest.json"); observed.remove("COMPLETED.json");
        if (!observed.equals(manifest.get("output_sha256"))) throw new IOException("Native bundle has missing,changed or unexpected files");
        for (String name : Set.of("recipe-request.json","cli-request.json","session.json","analysis-status.json","results.json","WARNINGS.md","prepared/preparation.json"))
            if (!observed.containsKey(name)) throw new IOException("Native bundle is missing required evidence: "+name);
        Set<String> operationFiles = operation == RepeatRecipe.Operation.MASK
                ? Set.of("native-mask/native-masked.fa","native-mask/native-coverage.tsv","native-mask/native-mask.json","native-mask/WARNINGS.md")
                : operation == RepeatRecipe.Operation.AUDIT ? Set.of("audit-coverage.tsv") : Set.of();
        if (!observed.keySet().containsAll(operationFiles)) throw new IOException("Native bundle is missing operation-specific outputs");
        if (observed.keySet().stream().anyMatch(name -> name.endsWith("FAILED.txt") || name.equals("FINALIZATION_FAILED.md") || name.equals("INTERRUPTED.md")))
            throw new IOException("Native bundle retains a failure/interruption diagnostic and cannot be admitted");
        Map<?,?> status = object(root.resolve("analysis-status.json"),65536), result = object(root.resolve("results.json"),33554432);
        if (!Long.valueOf(0).equals(status.get("exit_code")) || !"COMPLETED_NATIVE".equals(status.get("status"))
                || !flags(operation).equals(status.get("completion_flags")) || !operation.name().equals(result.get("operation"))
                || !scope(operation).equals(result.get("scope")) || !flags(operation).equals(result.get("completion_flags")))
            throw new IOException("Native status/result and completion flags do not agree");
        if (!markerHash.equals(RepeatInputs.hash(root.resolve("COMPLETED.json"),65536,check))
                || !RepeatInputs.hash(root.resolve("manifest.json"),2097152,check).equals(marker.get("manifest_sha256")))
            throw new IOException("Native completion metadata changed during verification");
        return new Verification(operation.name(),scope(operation),actual.bytes(),actual.members(),observed.size());
    }
}
