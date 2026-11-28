/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Portable complete file census and scientific-contract recomputation before atomic admission. */
final class Pbmm2Bundle {
    private Pbmm2Bundle() { }
    static void finish(Path root) throws IOException {
        writeJson(root.resolve("manifest.json"), manifest(root));
        Pin manifest = pin(root.resolve("manifest.json"), 67108864, () -> { });
        writeJson(root.resolve("CANDIDATE.json"), marker(manifest, "candidate"));
        verify(root, "CANDIDATE.json");
        writeJson(root.resolve("completion.new.json"), marker(manifest, "complete"));
        Files.delete(root.resolve("CANDIDATE.json"));
        Files.move(root.resolve("completion.new.json"), root.resolve("COMPLETED.json"), StandardCopyOption.ATOMIC_MOVE);
    }
    static Map<String, Object> marker(Pin manifest, String state) {
        return Map.of("schema_version", 1, "profile", "pbmm2-original-prepared-inputs-v1", "status", state + "-selected-original-operation-and-cigar-contracts", "manifest", manifest,
                "whole_paper_reproduced", false, "complete_binary_source_equivalence_certified", false);
    }
    static Map<String, Object> verify(Path supplied) throws IOException { return verify(supplied.toAbsolutePath().normalize(), "COMPLETED.json"); }
    private static Map<String, Object> verify(Path root, String markerName) throws IOException {
        directory(root);
        if (Files.exists(root.resolve("FAILURE.json"))) throw new IOException("Failed pbmm2 attempt admits no result");
        Pin manifest = pin(root.resolve("manifest.json"), 67108864, () -> { });
        var marker = object(json(root.resolve(markerName), 1048576));
        if (!NextflowJson.json(marker(manifest, markerName.equals("CANDIDATE.json") ? "candidate" : "complete")).equals(NextflowJson.json(marker))) throw new IOException("Original pbmm2 completion scope/manifest differs");
        if (!NextflowJson.json(manifest(root)).equals(NextflowJson.json(json(root.resolve("manifest.json"), 67108864)))) throw new IOException("Complete pbmm2 attempt file/directory census differs");
        var request = Pbmm2Request.read(root.resolve("request.json"));
        if (!pin(root.resolve("runtime/pbmm2"), 1000000000L, () -> { }).equals(Pbmm2Profiles.executable(request.version())) || !pin(root.resolve("runtime/carrier.sif"), 5000000000L, () -> { }).equals(Pbmm2Profiles.image())) throw new IOException("Captured exact original binary/image differs");
        var context = object(json(root.resolve("context.json"), 1048576));
        keys(context, "schema_version", "producer_root", "producer", "replay_of_manifest"); integer(context.get("schema_version"), 1, 1); Pbmm2Request.path(context.get("producer_root"));
        if (!pin(root.resolve("adapter.jar"), 1000000000L, () -> { }).equals(fingerprint(context.get("producer"), 1000000000L))) throw new IOException("Original producing JAR differs");
        Pbmm2Workflow.verifyCommand(root, request, text(context.get("producer_root")));
        var captures = Pbmm2Capture.saved(root, request); Pbmm2Capture.verify(root, request, captures, true);
        var computed = Pbmm2Audit.run(root.resolve("output"), request);
        if (!NextflowJson.json(computed).equals(NextflowJson.json(json(root.resolve("audit.json"), 67108864)))) throw new IOException("Original complete BAM/CIGAR arithmetic replay differs");
        if (!utf8(bytes(root.resolve("WARNINGS.md"), 1048576)).equals(Pbmm2ToolsCli.warnings(request.version()))) throw new IOException("Required original pbmm2 warning/action scope differs");
        for (String process : List.of("version-process", "science-process")) {
            var ledger = object(json(root.resolve(process + "/process.json"), 67108864));
            if (!"completed-process-only".equals(ledger.get("status")) || integer(ledger.get("exit_code"), 0, 255) != 0 || !list(ledger.get("owned_live_after_cleanup")).isEmpty()) throw new IOException("Original process lacks terminal successful ownership evidence: " + process);
        }
        return Map.of("manifest", manifest, "audit", computed);
    }
    private static Map<String, Object> manifest(Path root) throws IOException {
        var files = new TreeMap<String, Pin>(); var dirs = new TreeSet<String>();
        try (var stream = Files.walk(root)) {
            for (var iterator = stream.iterator(); iterator.hasNext();) {
                Path path = iterator.next(); if (path.equals(root)) continue;
                String name = member(root.relativize(path).toString().replace('\\', '/'));
                if (Set.of("manifest.json", "CANDIDATE.json", "COMPLETED.json", "completion.new.json").contains(name)) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("Linked pbmm2 bundle member: " + name);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) { directory(path); dirs.add(name); }
                else files.put(name, pin(path, 1000000000000L, () -> { }));
            }
        }
        return Map.of("schema_version", 1, "files", files, "directories", dirs);
    }
}
