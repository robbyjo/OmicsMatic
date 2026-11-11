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
import static org.jlinalg.cli.NextflowFiles.*;

/** Portable immutable adapter result, separate from its retained local engine/cache session. */
final class NfcoreChipseq200Bundle {
    private NfcoreChipseq200Bundle() { }
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
        writeJson(payload.resolve("manifest.json"), Map.of("schema_version", 1, "pipeline_profile", NfcoreChipseq200Request.PROFILE, "files", files, "directories", directories));
        Pin manifest = pin(payload.resolve("manifest.json"), 67108864, () -> { });
        writeJson(payload.resolve("CANDIDATE.json"), Map.of("schema_version", 1, "pipeline_profile", NfcoreChipseq200Request.PROFILE, "manifest", manifest, "status", "candidate-declared-source-workflow", "biological_accuracy_certified", false));
        verifyPayload(payload, "CANDIDATE.json");
        writeJson(payload.resolve("COMPLETED.json"), Map.of("schema_version", 1, "pipeline_profile", NfcoreChipseq200Request.PROFILE, "manifest", manifest, "status", "completed-declared-source-workflow", "biological_accuracy_certified", false));
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
        if (!NfcoreChipseq200Request.PROFILE.equals(marker.get("pipeline_profile")) || !Boolean.FALSE.equals(marker.get("biological_accuracy_certified")) || !text(marker.get("status")).equals(markerName.equals("CANDIDATE.json") ? "candidate-declared-source-workflow" : "completed-declared-source-workflow")) throw new IOException("Adapter completion profile/scope differs");
        Pin expected = fingerprint(marker.get("manifest"), 67108864); if (!pin(payload.resolve("manifest.json"), 67108864, () -> { }).equals(expected)) throw new IOException("Adapter manifest changed");
        if (markerName.equals("COMPLETED.json")) {
            Map<?, ?> candidate = object(json(payload.resolve("CANDIDATE.json"), 1048576)); keys(candidate, "schema_version", "pipeline_profile", "manifest", "status", "biological_accuracy_certified"); integer(candidate.get("schema_version"), 1, 1);
            if (!NfcoreChipseq200Request.PROFILE.equals(candidate.get("pipeline_profile")) || !Boolean.FALSE.equals(candidate.get("biological_accuracy_certified")) || !"candidate-declared-source-workflow".equals(candidate.get("status")) || !fingerprint(candidate.get("manifest"), 67108864).equals(expected)) throw new IOException("Adapter candidate and completion scopes disagree");
        }
        Map<?, ?> manifest = object(json(payload.resolve("manifest.json"), 67108864)); keys(manifest, "schema_version", "pipeline_profile", "files", "directories"); integer(manifest.get("schema_version"), 1, 1); if (!NfcoreChipseq200Request.PROFILE.equals(manifest.get("pipeline_profile"))) throw new IOException("Adapter result profile differs");
        var actualFiles = new LinkedHashMap<String, Pin>(); var actualDirs = new ArrayList<String>(); inventory(payload, actualFiles, actualDirs, Set.of("manifest.json", "CANDIDATE.json", "COMPLETED.json"));
        List<String> recordedDirs = strings(manifest.get("directories")); Map<?, ?> recorded = object(manifest.get("files")); if (recordedDirs.size() != new HashSet<>(recordedDirs).size() || !actualFiles.keySet().equals(recorded.keySet()) || !new HashSet<>(actualDirs).equals(new HashSet<>(recordedDirs))) throw new IOException("Portable adapter result file/directory census differs");
        for (var row : actualFiles.entrySet()) if (!row.getValue().equals(fingerprint(recorded.get(row.getKey()), 1000000000000L))) throw new IOException("Portable adapter result member changed: " + row.getKey());
        var engine = NfcoreEngineBundle.verify(payload.resolve("engine"), () -> { }); var data = new NfcoreChipseq200Files(engine); data.verifySource(); var source = data.files; NfcoreChipseq200ImageAudit.audit(data, payload.resolve("image-set.json"));
        Map<?, ?> report = object(json(payload.resolve("workflow.json"), 67108864));
        if (!NfcoreChipseq200Request.PROFILE.equals(report.get("pipeline_profile")) || !Boolean.FALSE.equals(report.get("biological_accuracy_certified")) || !Boolean.FALSE.equals(report.get("whole_paper_reproduced")) || !Boolean.FALSE.equals(report.get("independent_whole_model_reimplementation")) || !Boolean.FALSE.equals(report.get("author_engine_inferred")) || !"complete-declared-original-source-workflow".equals(report.get("status")) || !NfcoreChipseq200Request.ENGINE.equals(report.get("engine_profile")) || !fingerprint(report.get("engine_manifest"), 67108864).equals(engine.manifest())) throw new IOException("Adapter workflow/engine scope or binding differs");
        if (integer(report.get("complete_published_files"), 0, 1000000) != engine.outputFiles() || integer(report.get("complete_published_bytes"), 0, 1000000000000L) != engine.outputBytes()) throw new IOException("Reported complete publication census differs");
        if (!fingerprint(report.get("implementation"), 1000000000L).equals(pin(payload.resolve("adapter.jar"), 1000000000L, () -> { })) || !fingerprint(report.get("source_image_declaration"), 1048576).equals(pin(payload.resolve("image-set.json"), 1048576, () -> { })) || integer(report.get("source_images_checked"), 33, 33) != 33) throw new IOException("Adapter implementation or exact original image binding differs");
        if (!pin(payload.resolve("engine-recipe.json"), 4194304, () -> { }).equals(pin(engine.attempt().resolve("request.json"), 4194304, () -> { }))) throw new IOException("Portable adapter recipe differs from original captured engine recipe");
        Map<?, ?> request = object(NextflowParameters.parse(utf8(bytes(payload.resolve("request.json"), 4194304)), 4194304, 100000));
        keys(request, "schema_version", "pipeline_profile", "engine_recipe", "container_home", "image_set", "peak_type", "minimum_replicates", "executor_cpus", "executor_memory_bytes"); integer(request.get("schema_version"), 1, 1);
        Map<?, ?> parameters = source.parameters();
        if (!NfcoreChipseq200Request.PROFILE.equals(request.get("pipeline_profile")) || !Set.of("broadPeak", "narrowPeak").contains(text(request.get("peak_type"))) || !Boolean.valueOf(request.get("peak_type").equals("narrowPeak")).equals(parameters.getOrDefault("narrow_peak", null) == null ? Boolean.FALSE : parameters.get("narrow_peak"))) throw new IOException("Portable typed peak mode differs from original engine selection");
        if (integer(request.get("minimum_replicates"), 1, 1000000) != integer(parameters.containsKey("min_reps_consensus") ? parameters.get("min_reps_consensus") : 1, 1, 1000000)) throw new IOException("Portable consensus minimum differs from original engine selection");
        String producer = text(object(json(engine.attempt().resolve("session.json"), 67108864)).get("producer_root"));
        if (!utf8(bytes(source.inputMember(source.alias("workflow") + "/runtime/host.config"), 1048576)).equals(overlay(producer, request, payload.resolve("image-set.json")))) throw new IOException("Portable typed executor/image overlay differs from the complete original captured source");
        var componentRecords = new LinkedHashMap<String, Map<?, ?>>();
        for (Object value : list(report.get("source_components"))) { Map<?, ?> row = object(value); if (componentRecords.putIfAbsent(text(row.get("component")), row) != null) throw new IOException("Repeated workflow component report"); }
        if (!componentRecords.keySet().equals(Set.of("reads-and-alignments", "peaks-and-annotation", "complexity", "saved-QC"))) throw new IOException("Incomplete workflow component report");
        process(payload.resolve("processes/engine"));
        runtimeHealth(payload.resolve("processes/runtime-admission"), payload.resolve("image-set.json"));
        for (String component : List.of("reads-and-alignments", "peaks-and-annotation", "complexity", "saved-QC")) {
            Path directory = payload.resolve("audits").resolve(component);
            Map<?, ?> completed = object(json(directory.resolve("COMPONENT_AUDIT_COMPLETED.json"), 1048576)); if (!Boolean.FALSE.equals(completed.get("scientific_result_admitted")) || !fingerprint(completed.get("audit"), 67108864).equals(pin(directory.resolve("audit.json"), 67108864, () -> { }))) throw new IOException("Required component completion/integrity differs: " + component);
            Map<?, ?> audit = object(json(directory.resolve("audit.json"), 67108864)); if (!fingerprint(audit.get("original_engine_manifest"), 67108864).equals(engine.manifest()) || !Boolean.FALSE.equals(audit.get("scientific_result_admitted")) || integer(audit.get("nfcore_tool_credit"), 0, 0) != 0) throw new IOException("Component engine/scope differs: " + component);
            Map<?, ?> row = componentRecords.get(component); if (!"complete-source-consistency-contract".equals(row.get("status")) || !fingerprint(row.get("audit"), 67108864).equals(fingerprint(completed.get("audit"), 67108864))) throw new IOException("Workflow component summary differs: " + component);
            process(payload.resolve("processes").resolve(component));
        }
        savedQc(data, payload.resolve("audits/saved-QC"));
        return new Verified(payload, expected, engine);
    }
    private static void runtimeHealth(Path root, Path images) throws IOException {
        process(root.resolve("version-process"));
        if (!utf8(bytes(root.resolve("version-process/process.stdout"), 1024)).equals("1.5.5-1\n")) throw new IOException("Saved runtime literal provider version differs");
        Map<?, ?> health = object(json(root.resolve("CONTAINER_HEALTH_COMPLETED.json"), 67108864));
        if (!"complete-source-image-namespace-health-only".equals(health.get("status")) || !Boolean.FALSE.equals(health.get("scientific_analysis_executed"))) throw new IOException("Complete saved image-health scope differs");
        Map<?, ?> declared = object(object(json(images, 1048576)).get("images")); Map<?, ?> profile;
        try (var input = NfcoreChipseq200Bundle.class.getResourceAsStream("/org/jlinalg/omics/nfcore-chipseq200/images.json")) { if (input == null) throw new IOException("Missing complete image profile"); profile = object(SimpleJson.parse(utf8(input.readNBytes(1048577)))); }
        Map<?, ?> expectedImages = object(profile.get("images")); var seen = new HashSet<String>(); int index = 0;
        for (Object value : list(health.get("images"))) {
            Map<?, ?> row = object(value); String uri = text(row.get("source_uri")); if (!seen.add(uri) || !expectedImages.containsKey(uri) || !declared.get(uri).equals(row.get("image")) || !fingerprint(row.get("pin"), 5000000000L).equals(fingerprint(object(expectedImages.get(uri)).get("pin"), 5000000000L))) throw new IOException("Saved complete health image binding differs");
            Path record = root.resolve(String.format(java.util.Locale.ROOT, "image-%03d-health", ++index)); process(record); if (!fingerprint(row.get("process"), 67108864).equals(pin(record.resolve("process.json"), 67108864, () -> { }))) throw new IOException("Saved image health process binding differs");
        }
        if (seen.size() != 33 || !seen.equals(expectedImages.keySet()) || !seen.equals(declared.keySet())) throw new IOException("Saved complete source image-health population differs");
    }
    private static void savedQc(NfcoreChipseq200Files data, Path root) throws IOException {
        var peaks = new NfcoreChipseq200Peaks(data, NfcoreChipseq200Peaks.mappings(data)); Map<?, ?> report = object(json(root.resolve("audit.json"), 67108864)); String transform = Boolean.FALSE.equals(data.files.parameters().get("deseq2_vst")) ? "rlog" : "vst";
        if (!transform.equals(report.get("actual_saved_transform"))) throw new IOException("Saved original transform differs from declared source selection");
        var eligible = new ArrayList<NfcoreChipseq200Peaks.Group>(); var omitted = new ArrayList<String>();
        for (var group : peaks.groups) if (group.multipleGroups() && group.replicates()) eligible.add(group); else omitted.add(group.antibody());
        if (!strings(report.get("source_ineligible_QC_antibodies")).equals(omitted) || list(report.get("antibodies")).size() != eligible.size()) throw new IOException("Saved complete source QC eligibility/population differs");
        if (eligible.isEmpty()) return;
        Path runtime = root.resolve("original-qc-runtime"); process(runtime.resolve("version-process")); process(runtime.resolve("qc-image-health"));
        if (!pin(runtime.resolve("inspect.R"), 1048576, () -> { }).equals(new Pin(6611, "b6f6121568079d61d8e33b6fb3b920e4072639625aa3076a6cc33b94e23867a6"))) throw new IOException("Saved original QC worker differs");
        for (int i = 0; i < eligible.size(); i++) {
            var group = eligible.get(i); Map<?, ?> recorded = object(list(report.get("antibodies")).get(i)); String relative = "original-qc-runtime/antibody-" + (i + 1) + "/worker"; if (!relative.equals(recorded.get("original_worker_relative_path"))) throw new IOException("Saved original QC worker path differs");
            Path caseRoot = root.resolve("original-qc-runtime/antibody-" + (i + 1)); process(caseRoot.resolve("process")); String prefix = group.prefix().substring(group.prefix().lastIndexOf('/') + 1), base = group.prefix().substring(0, group.prefix().lastIndexOf('/') + 1) + "deseq2/";
            Map<String, Path> sources = Map.of("original.dds.RData", data.files.output(base + prefix + ".dds.RData"), "original.rds", data.files.output(base + prefix + ".rds"), "featureCounts.txt", data.files.output(group.prefix() + ".featureCounts.txt"), "size_factors.RData", data.files.output(base + "size_factors/" + prefix + ".size_factors.RData"));
            for (var row : sources.entrySet()) if (!pin(row.getValue(), 1000000000L, () -> { }).equals(pin(caseRoot.resolve(row.getKey()), 1000000000L, () -> { }))) throw new IOException("Saved original QC input differs from the complete engine object: " + row.getKey());
            var replay = NfcoreChipseq200QcFiles.audit(data, group, caseRoot.resolve("worker"), transform); replay.put("original_worker_relative_path", relative);
            if (!object(SimpleJson.parse(NextflowJson.json(replay))).equals(recorded)) throw new IOException("Complete portable native QC replay differs from saved audit");
        }
    }
    private static String overlay(String producer, Map<?, ?> request, Path images) throws IOException {
        Map<?, ?> declared = object(object(json(images, 1048576)).get("images")); Map<?, ?> profile;
        try (var input = NfcoreChipseq200Bundle.class.getResourceAsStream("/org/jlinalg/omics/nfcore-chipseq200/images.json")) { if (input == null) throw new IOException("Missing complete image profile"); profile = object(SimpleJson.parse(utf8(input.readNBytes(1048577)))); }
        var value = new StringBuilder("process.executor = 'local'\nexecutor.cpus = ").append(integer(request.get("executor_cpus"), 1, 1024)).append("\nexecutor.memory = '").append(integer(request.get("executor_memory_bytes"), 16777216, 1099511627776L)).append(" B'\nsingularity.cacheDir = ").append(quote(producer + "/workspace/execution/container-cache")).append("\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nprocess {\n");
        for (var row : object(profile.get("images")).entrySet()) for (String name : strings(object(row.getValue()).get("process_names"))) value.append("  withName: ").append(quote(name)).append(" { container = ").append(quote(text(declared.get(row.getKey())))).append(" }\n");
        return value.append("}\n").toString();
    }
    private static String quote(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
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
