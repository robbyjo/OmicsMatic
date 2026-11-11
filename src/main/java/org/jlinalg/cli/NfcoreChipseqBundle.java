/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;
import static org.jlinalg.cli.NextflowFiles.*;

/** Portable immutable adapter result, separate from its retained local engine/cache session. */
final class NfcoreChipseqBundle {
    private NfcoreChipseqBundle() { }
    record Verified(Path payload, Pin manifest, NfcoreEngineBundle.Verified engine) { }
    static void copy(Path source, Path target) throws IOException {
        directory(source); if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Result copy target already exists"); Files.createDirectory(target);
        try (var paths = Files.walk(source)) {
            var iterator = paths.iterator(); int count = 0;
            while (iterator.hasNext()) { Path path = iterator.next(); if (++count > 1000000) throw new IOException("Complete result copy exceeds entry capacity");
                if (path.equals(source)) continue; Path output = target.resolve(source.relativize(path));
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(output);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.toRealPath().equals(path)) {
                    Pin before = pin(path, 1000000000000L, () -> { }); Files.copy(path, output, StandardCopyOption.COPY_ATTRIBUTES);
                    if (!pin(output, 1000000000000L, () -> { }).equals(before) || !pin(path, 1000000000000L, () -> { }).equals(before)) throw new IOException("Complete result copy changed: " + source.relativize(path));
                } else throw new IOException("Portable result contains an unexpected link or nonregular member: " + path);
            }
        }
    }
    static void finish(Path payload, Map<String, Object> report) throws IOException {
        writeJson(payload.resolve("workflow.json"), report);
        var files = new LinkedHashMap<String, Pin>(); var directories = new ArrayList<String>(); inventory(payload, files, directories, Set.of());
        writeJson(payload.resolve("manifest.json"), Map.of("schema_version", 1, "pipeline_profile", NfcoreChipseqRequest.PROFILE, "files", files, "directories", directories));
        Pin manifest = pin(payload.resolve("manifest.json"), 67108864, () -> { });
        writeJson(payload.resolve("CANDIDATE.json"), Map.of("schema_version", 1, "pipeline_profile", NfcoreChipseqRequest.PROFILE, "manifest", manifest, "status", "candidate-declared-source-workflow", "biological_accuracy_certified", false));
        verifyPayload(payload, "CANDIDATE.json");
        writeJson(payload.resolve("COMPLETED.json"), Map.of("schema_version", 1, "pipeline_profile", NfcoreChipseqRequest.PROFILE, "manifest", manifest, "status", "completed-declared-source-workflow", "biological_accuracy_certified", false));
        verifyPayload(payload, "COMPLETED.json");
    }
    static Verified verify(Path selected) throws IOException {
        Path root = directory(selected.toAbsolutePath().normalize());
        if (Files.exists(root.resolve("latest.json"), LinkOption.NOFOLLOW_LINKS)) {
            Map<?, ?> latest = object(json(root.resolve("latest.json"), 1048576)); keys(latest, "attempt", "status");
            String id = text(latest.get("attempt")); if (!id.matches("[0-9]{6}") || !"COMPLETED".equals(latest.get("status"))) throw new IOException("Latest adapter attempt is incomplete/failed; no earlier-success fallback");
            root = directory(root.resolve("results").resolve(id));
        }
        return verifyPayload(root, "COMPLETED.json");
    }
    private static Verified verifyPayload(Path payload, String markerName) throws IOException {
        directory(payload); Map<?, ?> marker = object(json(payload.resolve(markerName), 1048576)); keys(marker, "schema_version", "pipeline_profile", "manifest", "status", "biological_accuracy_certified"); integer(marker.get("schema_version"), 1, 1);
        if (!NfcoreChipseqRequest.PROFILE.equals(marker.get("pipeline_profile")) || !Boolean.FALSE.equals(marker.get("biological_accuracy_certified")) || !text(marker.get("status")).equals(markerName.equals("CANDIDATE.json") ? "candidate-declared-source-workflow" : "completed-declared-source-workflow")) throw new IOException("Adapter completion profile/scope differs");
        Pin expected = fingerprint(marker.get("manifest"), 67108864); if (!pin(payload.resolve("manifest.json"), 67108864, () -> { }).equals(expected)) throw new IOException("Adapter manifest changed");
        if (markerName.equals("COMPLETED.json")) {
            Map<?, ?> candidate = object(json(payload.resolve("CANDIDATE.json"), 1048576)); keys(candidate, "schema_version", "pipeline_profile", "manifest", "status", "biological_accuracy_certified"); integer(candidate.get("schema_version"), 1, 1);
            if (!NfcoreChipseqRequest.PROFILE.equals(candidate.get("pipeline_profile")) || !Boolean.FALSE.equals(candidate.get("biological_accuracy_certified")) || !"candidate-declared-source-workflow".equals(candidate.get("status")) || !fingerprint(candidate.get("manifest"), 67108864).equals(expected)) throw new IOException("Adapter candidate and completion scopes disagree");
        }
        Map<?, ?> manifest = object(json(payload.resolve("manifest.json"), 67108864)); keys(manifest, "schema_version", "pipeline_profile", "files", "directories"); integer(manifest.get("schema_version"), 1, 1); if (!NfcoreChipseqRequest.PROFILE.equals(manifest.get("pipeline_profile"))) throw new IOException("Adapter result profile differs");
        var actualFiles = new LinkedHashMap<String, Pin>(); var actualDirs = new ArrayList<String>(); inventory(payload, actualFiles, actualDirs, Set.of("manifest.json", "CANDIDATE.json", "COMPLETED.json"));
        List<String> recordedDirs = strings(manifest.get("directories")); Map<?, ?> recorded = object(manifest.get("files")); if (recordedDirs.size() != new HashSet<>(recordedDirs).size() || !actualFiles.keySet().equals(recorded.keySet()) || !new HashSet<>(actualDirs).equals(new HashSet<>(recordedDirs))) throw new IOException("Portable adapter result file/directory census differs");
        for (var row : actualFiles.entrySet()) if (!row.getValue().equals(fingerprint(recorded.get(row.getKey()), 1000000000000L))) throw new IOException("Portable adapter result member changed: " + row.getKey());
        var engine = NfcoreEngineBundle.verify(payload.resolve("engine"), () -> { }); var source = new NfcoreScientificFiles(engine); source.verifyChipseq121Source();
        Map<?, ?> report = object(json(payload.resolve("workflow.json"), 67108864));
        if (!NfcoreChipseqRequest.PROFILE.equals(report.get("pipeline_profile")) || !Boolean.FALSE.equals(report.get("biological_accuracy_certified")) || !Boolean.FALSE.equals(report.get("whole_paper_reproduced")) || !Boolean.FALSE.equals(report.get("independent_whole_model_reimplementation")) || !Boolean.FALSE.equals(report.get("author_engine_inferred")) || !"complete-declared-original-source-workflow".equals(report.get("status")) || !NfcoreChipseqRequest.ENGINE.equals(report.get("engine_profile")) || !fingerprint(report.get("engine_manifest"), 67108864).equals(engine.manifest())) throw new IOException("Adapter workflow/engine scope or binding differs");
        if (integer(report.get("complete_published_files"), 0, 1000000) != engine.outputFiles() || integer(report.get("complete_published_bytes"), 0, 1000000000000L) != engine.outputBytes()) throw new IOException("Reported complete publication census differs");
        if (!fingerprint(report.get("implementation"), 1000000000L).equals(pin(payload.resolve("adapter.jar"), 1000000000L, () -> { })) || !fingerprint(report.get("original_image"), 2000000000L).equals(new Pin(1131143168L, "50bd3f475b82608dd305138dff30b4789973d930994a5f710a7e5dd8acbb1e38"))) throw new IOException("Adapter implementation or exact original image binding differs");
        if (!pin(payload.resolve("engine-recipe.json"), 4194304, () -> { }).equals(pin(engine.attempt().resolve("request.json"), 4194304, () -> { }))) throw new IOException("Portable adapter recipe differs from original captured engine recipe");
        Map<?, ?> request = object(NextflowParameters.parse(utf8(bytes(payload.resolve("request.json"), 4194304)), 4194304, 100000));
        keys(request, "schema_version", "pipeline_profile", "engine_recipe", "container_home", "image", "peak_type", "read_mode", "minimum_replicates", "executor_cpus", "executor_memory_bytes"); integer(request.get("schema_version"), 1, 1);
        Map<?, ?> parameters = source.parameters();
        if (!NfcoreChipseqRequest.PROFILE.equals(request.get("pipeline_profile")) || !Set.of("paired", "single").contains(text(request.get("read_mode"))) || !Set.of("broadPeak", "narrowPeak").contains(text(request.get("peak_type"))) || !Boolean.valueOf(request.get("read_mode").equals("single")).equals(parameters.getOrDefault("single_end", null) == null ? Boolean.FALSE : parameters.get("single_end")) || !Boolean.valueOf(request.get("peak_type").equals("narrowPeak")).equals(parameters.getOrDefault("narrow_peak", null) == null ? Boolean.FALSE : parameters.get("narrow_peak"))) throw new IOException("Portable typed peak/read mode differs from original engine selection");
        if (integer(request.get("minimum_replicates"), 1, 1000000) != integer(parameters.containsKey("min_reps_consensus") ? parameters.get("min_reps_consensus") : 1, 1, 1000000)) throw new IOException("Portable consensus minimum differs from original engine selection");
        var componentRecords = new LinkedHashMap<String, Map<?, ?>>();
        for (Object value : list(report.get("source_components"))) { Map<?, ?> row = object(value); if (componentRecords.putIfAbsent(text(row.get("component")), row) != null) throw new IOException("Repeated workflow component report"); }
        if (!componentRecords.keySet().equals(Set.of("reads-and-alignments", "consensus", "complexity", "annotation-QC", "differential"))) throw new IOException("Incomplete workflow component report");
        for (String stage : List.of("engine", "runtime-admission/version-process", "runtime-admission/container-health")) process(payload.resolve("processes").resolve(stage));
        var mappings = NfcoreChipseq121Mappings.read(source.output("pipeline_info/design_reads.csv"), source.output("pipeline_info/design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults()); boolean differentialRequested = mappings.controls().stream().anyMatch(row -> row.replicatesExist() && row.multipleGroups());
        for (String component : List.of("reads-and-alignments", "consensus", "complexity", "annotation-QC", "differential")) {
            Path directory = payload.resolve("audits").resolve(component);
            if (Files.exists(directory.resolve("NOT_REQUESTED.json"))) {
                if (!component.equals("differential") || differentialRequested) throw new IOException("Required source component was marked unrequested"); Map<?, ?> skipped = object(json(directory.resolve("NOT_REQUESTED.json"), 1048576));
                if (!"original-replicates-and-multiple-groups-condition-false".equals(skipped.get("reason")) || !Boolean.FALSE.equals(skipped.get("negative_finding")) || !fingerprint(skipped.get("engine_manifest"), 67108864).equals(engine.manifest()) || !"source-not-requested".equals(componentRecords.get(component).get("status")) || !Boolean.FALSE.equals(componentRecords.get(component).get("negative_finding"))) throw new IOException("Source-unrequested differential phase scope differs");
            } else {
                Map<?, ?> completed = object(json(directory.resolve("COMPONENT_AUDIT_COMPLETED.json"), 1048576)); if (!Boolean.FALSE.equals(completed.get("scientific_result_admitted")) || !fingerprint(completed.get("audit"), 67108864).equals(pin(directory.resolve("audit.json"), 67108864, () -> { }))) throw new IOException("Required component completion/integrity differs: " + component);
                Map<?, ?> audit = object(json(directory.resolve("audit.json"), 67108864)); if (!fingerprint(audit.get("original_engine_manifest"), 67108864).equals(engine.manifest())) throw new IOException("Component used another original engine result: " + component);
                Map<?, ?> row = componentRecords.get(component); if (!"complete-source-consistency-contract".equals(row.get("status")) || !fingerprint(row.get("audit"), 67108864).equals(fingerprint(completed.get("audit"), 67108864))) throw new IOException("Workflow component summary differs: " + component);
                process(payload.resolve("processes").resolve(component));
            }
        }
        return new Verified(payload, expected, engine);
    }
    private static void process(Path directory) throws IOException {
        Map<?, ?> ledger = object(json(directory.resolve("process.json"), 67108864));
        if (!"completed-process-only".equals(ledger.get("status")) || integer(ledger.get("exit_code"), 0, 255) != 0 || !list(ledger.get("owned_live_after_cleanup")).isEmpty() || ledger.containsKey("error") || ledger.containsKey("cleanup_state") || ledger.containsKey("suppressed") && !list(ledger.get("suppressed")).isEmpty()) throw new IOException("Required original/component process did not complete cleanly: " + directory);
        bytes(directory.resolve("process.stdout"), 67108864); bytes(directory.resolve("process.stderr"), 67108864);
    }
    private static void inventory(Path root, Map<String, Pin> files, List<String> directories, Set<String> exclude) throws IOException {
        long[] total = {0}; int count = 0;
        try (var paths = Files.walk(root)) {
            var iterator = paths.iterator(); while (iterator.hasNext()) { Path path = iterator.next();
                if (++count > 1000000) throw new IOException("Complete portable adapter result exceeds entry capacity"); if (path.equals(root)) continue;
                String name = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"); member(name); if (exclude.contains(name)) continue;
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && path.toRealPath().equals(path)) directories.add(name);
                else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.toRealPath().equals(path)) { Pin value = pin(path, 1000000000000L, () -> { }); total[0] = Math.addExact(total[0], value.bytes()); if (total[0] > 1000000000000L) throw new IOException("Complete portable adapter result exceeds byte capacity"); files.put(name, value); }
                else throw new IOException("Unexpected linked/nonregular portable adapter member");
            }
        }
    }
}
