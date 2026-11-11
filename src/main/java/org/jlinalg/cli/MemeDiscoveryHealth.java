/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Fresh exact-edition discovery, independent output audit and original renderer replay. */
final class MemeDiscoveryHealth {
    private MemeDiscoveryHealth() { }
    static final String RESOURCE = "/org/jlinalg/omics/meme-discovery/";
    static final NextflowFiles.Pin PRIMARY = new NextflowFiles.Pin(6470, "20167f245b832891a9f5c1392f821a8b852010c18382727b11a651078ef353c5");
    static final NextflowFiles.Pin BACKGROUND = new NextflowFiles.Pin(28, "9baf37e722bec2132b06aac3c3c353e628d9d5cdb631c34804cc186c4e523805");
    static final NextflowFiles.Pin REFERENCE = new NextflowFiles.Pin(1232111, "d0d4a033be926485791119a3b7861ead76bcb386b79651130b8f60824003e534");

    static Map<String, Object> run(MemeDiscoveryRuntime.Context context, Runnable check) throws IOException {
        require(!context.healthy, "A MEME health receipt cannot replace a fresh requested health run");
        context.verify(check);
        Path root = context.root.resolve("health"), primary = root.resolve("original-primary.fa"), background = root.resolve("original-background.bg");
        Files.write(primary, MemeRuntimeFiles.resource(RESOURCE + "health-primary.fa", PRIMARY), java.nio.file.StandardOpenOption.CREATE_NEW);
        Files.write(background, MemeRuntimeFiles.resource(RESOURCE + "health-background.bg", BACKGROUND), java.nio.file.StandardOpenOption.CREATE_NEW);
        var recipe = recipe(context.build.profile(), primary.toString(), background.toString());
        Path source = root.resolve("request.json"); MemeRuntimeFiles.write(source, recipe);
        var request = MemeDiscoveryRequest.read(source, false, check);
        var inputs = MemeDiscoveryInputs.capture(root.resolve("inputs"), request, check);
        Path fit = context.root.resolve("probes/health-fit");
        var command = request.arguments(context.home, inputs.primary(), inputs.backgroundFile(), fit.resolve("result"), inputs.population().records().size());
        var fitProcess = context.nativeRun(command, fit, request.processLimits(), () -> inputs.verify(true, check), context.accounting(), check);
        context.completed.add(fit);
        var output = MemeDiscoveryOutput.read(fit.resolve("result"), request, inputs, command, check);
        Path report = context.root.resolve("probes/health-report");
        var reportCommand = context.htmlArguments(fit.resolve("result/meme.xml"), report.resolve("replayed.html"));
        var reportProcess = context.nativeRun(reportCommand, report, request.processLimits(), () -> {
            inputs.verify(true, check); output.verify(request, check);
        }, context.accounting(), check);
        context.completed.add(report);
        MemeDiscoveryOutput.replay(output, report.resolve("replayed.html"), request, check);
        var references = NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(MemeRuntimeFiles.resource(RESOURCE + "health-reference.json", REFERENCE))));
        require(references.keySet().equals(java.util.Set.copyOf(MemeDiscoveryRequest.PROFILES)), "Complete MEME health edition population differs");
        var reference = NextflowFiles.object(references.get(context.build.profile()));
        String observed = NextflowJson.json(MemeDiscoveryOutput.projection(output.scientific()));
        require(observed.equals(NextflowJson.json(reference.get("scientific_projection"))), "Fresh original MEME scientific health differs from the complete exact-edition motif/site/matrix/diagram reference; preserve this attempt and skip user execution");
        inputs.verify(true, check); output.verify(request, check); context.verify(check);
        var receipt = Map.<String, Object>of("profile", context.build.profile(), "reference_resource", MemeRuntimeFiles.pin(REFERENCE),
            "original_reference_XML", reference.get("source_XML"), "primary", MemeRuntimeFiles.pin(PRIMARY), "background", MemeRuntimeFiles.pin(BACKGROUND),
            "fit_exit", fitProcess.exitCode(), "report_exit", reportProcess.exitCode(), "audit", output.document(),
            "complete_scientific_projection_sha256", NextflowFiles.sha(observed.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "scope", "Fresh original source-built exact-edition EM fit, complete independent XML site/count/score/diagram audit, text matrices, decoded figure carriers and byte-identical original-helper HTML replay; all learned scientific fields match the pinned exact-edition reference. Source motif significance is reference matched, not independently reimplemented. No biological or paper reproduction certificate.");
        MemeRuntimeFiles.write(context.root.resolve("metadata/health.json"), receipt);
        context.healthy = true;
        context.verify(check);
        return receipt;
    }
    static Map<String, Object> recipe(String profile, String primary, String background) {
        var result = MemeDiscoveryRequest.template(profile, primary, background);
        result.put("minimum_width", 10); result.put("maximum_width", 10);
        result.put("limits", Map.of("bases", 100000, "wall_seconds", 120, "cpu_seconds", 120,
            "address_space_bytes", 2147483648L, "sampled_rss_bytes", 1073741824L,
            "file_bytes", 67108864L, "workspace_bytes", 1073741824L));
        return result;
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
}
