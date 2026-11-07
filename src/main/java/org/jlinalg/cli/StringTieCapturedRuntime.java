/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Portable captured runtime receipts; this never opens the recorded live host or producer paths. */
final class StringTieCapturedRuntime {
    private StringTieCapturedRuntime() { }
    static void verify(Path root, StringTieRecipe.Request request, String producer, String home, Runnable check) throws IOException {
        var limits = StringTieRuntime.Limits.defaults();
        Map<?, ?> complete = object(root.resolve("COMPLETED.json")), manifest = object(root.resolve("manifest.json"));
        NextflowFiles.keys(complete, "status", "manifest", "user_analysis_admitted");
        require("COMPLETE_NATIVE_RUNTIME_HEALTH_ONLY".equals(complete.get("status")) && Boolean.FALSE.equals(complete.get("user_analysis_admitted")), "Invalid captured StringTie runtime completion/scope");
        require(NextflowFiles.pin(root.resolve("manifest.json"), limits.setup().metadataBytes(), check).equals(NextflowFiles.fingerprint(complete.get("manifest"), limits.setup().metadataBytes())), "Captured runtime manifest hash differs");
        NextflowFiles.keys(manifest, "schema_version", "status", "profile", "version", "files", "user_analysis_admitted");
        require(NextflowFiles.integer(manifest.get("schema_version"), 1, 1) == 1 && request.profile().equals(manifest.get("profile")) && request.version().equals(manifest.get("version")) && "NATIVE_RUNTIME_HEALTH_ONLY".equals(manifest.get("status")) && Boolean.FALSE.equals(manifest.get("user_analysis_admitted")), "Captured exact StringTie runtime profile/scope differs");
        var before = new TreeMap<>(StringTieRuntime.tree(root, limits.process(), check)); before.remove("manifest.json"); before.remove("COMPLETED.json");
        require(StringTieWorkflow.same(BigWigFiles.portable(before), BigWigFiles.portable(manifest.get("files"))), "Complete captured StringTie runtime namespace/bytes differ");
        for (String name : before.keySet()) require(!name.contains("INCOMPLETE") && !name.contains("FAILURE") && !name.contains("UNADMITTED"), "Captured runtime contains a partial or failed member");
        var spec = StringTieSetupEvidence.profile(request.profile()); Map<?, ?> resources = NextflowFiles.object(StringTieSetupEvidence.lock().get("resources"));
        for (String name : java.util.List.of("reads.bam", "guide.gtf", "reference.sizes")) require(NextflowFiles.pin(root.resolve("inputs").resolve(name), 1048576, check).equals(NextflowFiles.fingerprint(resources.get("health-inputs/" + name), 1048576)), "Captured exact synthetic health input differs");
        Map<String, Object> health = new LinkedHashMap<>(StringTieHealth.audit(root.resolve("inputs"), root.resolve("health/estimation"), request.version(), NextflowFiles.object(spec.get("health")), check));
        var bam = new LinkedHashMap<String, Object>(); NextflowFiles.object(health.get("BAM")).forEach((k,v) -> bam.put(k.toString(), v)); bam.put("source", producer + "/runtime/inputs/reads.bam"); health.put("BAM", bam);
        require(StringTieWorkflow.same(health, object(root.resolve("metadata/health.json"))), "Complete captured synthetic native health audit differs");
        Map<?, ?> metadata = object(root.resolve("metadata/request.json"));
        NextflowFiles.keys(metadata, "profile", "home", "setup_report", "installation_performed", "user_analysis_admitted");
        require(request.profile().equals(metadata.get("profile")) && home.equals(metadata.get("home")) && (producer + "/setup-report").equals(metadata.get("setup_report")) && Boolean.FALSE.equals(metadata.get("installation_performed")) && Boolean.FALSE.equals(metadata.get("user_analysis_admitted")), "Captured runtime request binding/scope differs");
        Map<?, ?> host = object(root.resolve("metadata/host.json"));
        require(home.equals(host.get("home")) && request.profile().equals(host.get("profile")) && request.version().equals(host.get("version")), "Captured host edition/home differs");
        Map<?, ?> hostFiles = NextflowFiles.object(NextflowFiles.object(host.get("complete_host_files")).get("files"));
        Map<?, ?> executable = NextflowFiles.object(NextflowFiles.object(hostFiles.get(home + "/bin/stringtie")).get("file"));
        require(NextflowFiles.fingerprint(executable, 67108864).equals(NextflowFiles.fingerprint(NextflowFiles.object(spec.get("installed_files")).get("bin/stringtie"), 67108864)), "Captured native host did not bind the exact original executable");
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().equals("process.json")).toList()) {
                Map<?, ?> process = object(file); require("completed-process-only".equals(process.get("status")) && NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty(), "Captured runtime process was not completely cleaned up");
                long exit = NextflowFiles.integer(process.get("exit_code"), 0, 1); require(exit == 0 || "library-binding-inspection-only".equals(process.get("process_purpose")), "Captured runtime process failed");
            }
        }
        var after = new TreeMap<>(StringTieRuntime.tree(root, limits.process(), check)); after.remove("manifest.json"); after.remove("COMPLETED.json"); require(before.equals(after), "Captured runtime changed during verification");
    }
    private static Map<?, ?> object(Path file) throws IOException { return NextflowFiles.object(NextflowFiles.json(file, 1048576)); }
    private static void require(boolean value, String reason) throws IOException { if (!value) throw new IOException(reason); }
}
