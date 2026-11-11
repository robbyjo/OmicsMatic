/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Isolated synthetic original-software traces; never creates a public runtime or analysis ticket. */
public final class MemeNoMotifSourceTrace {
    private MemeNoMotifSourceTrace() { }
    public static void main(String[] args) throws Exception {
        Path planPath = Path.of(args[0]), out = Path.of(args[1]);
        var plan = NextflowFiles.object(NextflowFiles.json(planPath, 2097152));
        Path area = Files.createTempDirectory(Path.of("/var/tmp"), "omicsmatic-meme-empty-source-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
        Path data = Files.createDirectory(area.resolve("data"));
        Files.write(data.resolve("primary.fa"), MemeRuntimeFiles.resource(MemeDiscoveryHealth.RESOURCE + "health-primary.fa", MemeDiscoveryHealth.PRIMARY), StandardOpenOption.CREATE_NEW);
        Files.write(data.resolve("background.bg"), MemeRuntimeFiles.resource(MemeDiscoveryHealth.RESOURCE + "health-background.bg", MemeDiscoveryHealth.BACKGROUND), StandardOpenOption.CREATE_NEW);
        System.out.println("RETAINED_NATIVE_AREA=" + area); System.out.flush();
        var cases = new ArrayList<Map<String, Object>>();
        for (Object raw : NextflowFiles.list(plan.get("profiles"))) {
            var row = NextflowFiles.object(raw); String profile = NextflowFiles.text(row.get("profile"));
            if (!MemeDiscoveryRequest.PROFILES.contains(profile)) throw new IllegalArgumentException("Unknown original source profile");
            Path home = Path.of(NextflowFiles.text(row.get("home"))), evidence = Path.of(NextflowFiles.text(row.get("evidence")));
            Path root = Files.createDirectory(area.resolve(profile)), prepared = Files.createDirectory(root.resolve("prepared"));
            var source = MemeDiscoveryBuild.read(profile, home, evidence, () -> { });
            source.capture(root.resolve("build-evidence"), () -> { });
            var recipe = MemeDiscoveryHealth.recipe(profile, data.resolve("primary.fa").toString(), data.resolve("background.bg").toString());
            recipe.put("maximum_motif_evalue", new BigDecimal("1e-300"));
            if (!profile.startsWith("MEME_4_9_1_")) recipe.put("motifs", 1000);
            MemeRuntimeFiles.write(prepared.resolve("request.json"), recipe);
            var request = MemeDiscoveryRequest.read(prepared.resolve("request.json"), false, () -> { });
            var inputs = MemeDiscoveryInputs.capture(prepared.resolve("inputs"), request, () -> { });
            Path process = root.resolve("process");
            var command = request.arguments(home, inputs.primary(), inputs.backgroundFile(), process.resolve("result"), inputs.population().records().size());
            var record = new LinkedHashMap<String, Object>(); record.put("profile", profile); record.put("command", command);
            record.put("analysis_admitted", false); record.put("purpose", "Original zero-motif source behavior only; no complete live-host dependency census or public workflow admission.");
            Map<String, String> environment = Map.of("PATH", home + "/bin:" + home + "/libexec/meme-" + request.version() + ":/usr/bin:/bin", "LC_ALL", "C", "LANG", "C", "TZ", "UTC");
            try {
                var completed = RsemProcess.executeOwned(command, environment, process, request.processLimits(), () -> {
                    source.verify(() -> { }); inputs.verify(true, () -> { });
                }, () -> { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException(); }, List.of(data, prepared, root.resolve("build-evidence")));
                record.put("original_exit_code", completed.exitCode()); record.put("supervised_process_completed", true);
            } catch (java.io.IOException error) {
                record.put("supervised_process_completed", false); record.put("error", error.toString());
            }
            source.verify(() -> { }); inputs.verify(true, () -> { });
            Path result = process.resolve("result");
            if (Files.isDirectory(result)) {
                record.put("output_inventory", MemeRuntimeFiles.tree(result, MemeDiscoveryInputs.treeLimits(request), () -> { }));
                Path xml = result.resolve("meme.xml");
                if (Files.isRegularFile(xml)) {
                    var document = org.jlinalg.sequence.MemeXml.read(xml, request.outputLimits().xml(), () -> { });
                    record.put("original_XML", Map.of("bytes", document.bytes(), "sha256", document.sha256()));
                    record.put("reported_motifs", document.root().one("motifs").children().size());
                    record.put("scanned_summary_populations", document.root().named("scanned_sites_summary").stream().map(n -> n.children().size()).toList());
                    record.put("effective_motif_limit", document.root().one("model").one("nmotifs").text().strip());
                    record.put("stopping_reason", document.root().one("model").one("reason_for_stopping").text().strip());
                }
            }
            MemeRuntimeFiles.write(root.resolve("TRACE.json"), record); cases.add(record);
        }
        MemeRuntimeFiles.write(out.resolve("SOURCE_TRACES.json"), Map.of("status", "CAPTURED_ORIGINAL_ZERO_MOTIF_SOURCE_TRACES_NOT_ANALYSES", "native_area", area.toString(), "cases", cases,
            "runtime_admitted", false, "analysis_admitted", false, "scope", "Synthetic raw-source behavior. Original source/build/payload and unchanged inputs are checked around owned supervised calls; the complete current-host dependency/health workflow is a separate gate."));
    }
}
