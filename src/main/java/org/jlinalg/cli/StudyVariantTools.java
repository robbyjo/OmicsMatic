/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Native workflow binding to managed software and its execution-cache identity. */
public final class StudyVariantTools {
    private StudyVariantTools() { }
    static Path home(Path project, String tool, Map<String,Object> settings) {
        return settings.containsKey("home") ? project.resolve(text(settings.get("home"))).toAbsolutePath().normalize() : VariantToolRuntime.defaultHome(tool).toAbsolutePath().normalize();
    }
    static Map<String,Object> prepare(Context context, String tool, Map<String,Object> configuration) throws IOException {
        var settings = map(configuration.get("installation"));
        if (!"automatic".equals(settings.get("mode"))) return configuration;
        var options = new LinkedHashMap<String,String>();
        for (var entry : Map.of("archive", "source-file", "source_url_env", "source-url-env", "sha256", "expected-sha256", "accept_license", "accept-license").entrySet())
            if (settings.containsKey(entry.getKey())) options.put(entry.getValue(), text(settings.get(entry.getKey())));
        if (options.containsKey("source-file")) options.put("source-file", context.project().resolve(options.get("source-file")).toAbsolutePath().normalize().toString());
        Object bootstrap = tool.equals("alphagenome") && !"python".equals(configuration.get("python")) ? configuration.getOrDefault("python", VariantToolRuntime.defaultPython()) : VariantToolRuntime.defaultPython();
        String python = text(settings.getOrDefault("python", bootstrap));
        Path local = context.project().resolve(python).normalize(); if (Files.isRegularFile(local)) python = local.toAbsolutePath().toString();
        var runtime = VariantToolRuntime.install(tool, home(context.project(), tool, settings), python, options, resourceInstallBlocked(context.study()).isBlank(), null);
        var result = new LinkedHashMap<>(configuration);
        result.put(tool.equals("alphagenome") ? "python" : "executable", runtime.executable().toString());
        result.put("managed_runtime_marker_sha256", runtime.markerSha256());
        write(context.outputDirectory().resolve(tool + "-software-setup.yaml"), Map.of("tool", tool, "home", runtime.home().toString(), "executable", runtime.executable().toString(), "version", runtime.metadata().get("version"), "marker_sha256", runtime.markerSha256(), "software_scope", runtime.metadata().get("software_scope")));
        return result;
    }
    /** Validate an installed profile before admitting cached scientific work. */
    public static Map<String,String> inputHashes(Path project, Map<String,Object> study, Map<String,Object> operation) {
        var result = new LinkedHashMap<String,String>();
        if (!Set.of("variant_annotation", "variant_scoring").contains(operation.get("type"))) return result;
        for (Object raw : list(study.get("hypotheses"))) {
            var hypothesis = map(raw); if (!hypothesis.get("id").equals(operation.get("hypothesis"))) continue;
            var followup = map(map(hypothesis.get("analysis")).get("variant_followup"));
            for (String tool : List.of("alphagenome", "annovar")) {
                var configuration = map(followup.get(tool.equals("annovar") ? "consequences" : tool)); var settings = map(configuration.get("installation"));
                if (!Boolean.TRUE.equals(configuration.get("enabled")) || !"automatic".equals(settings.get("mode")) || "import".equals(configuration.get("engine")) || tool.equals("annovar") && !"annovar".equals(configuration.get("engine"))) continue;
                Path path = home(project, tool, settings);
                try { result.put("variant_software:" + tool, "verified:" + VariantToolRuntime.open(tool, path).markerSha256()); }
                catch (IOException invalid) { result.put("variant_software:" + tool, Files.exists(path.resolve(VariantToolRuntime.MARKER)) ? "invalid_managed_runtime" : "not_installed"); }
            }
        }
        return result;
    }
}
