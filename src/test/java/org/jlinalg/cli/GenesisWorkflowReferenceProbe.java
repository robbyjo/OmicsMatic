/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.SnprelateRequest.need;

/** Development control consumer only. Never creates an admitted analysis or a completion certificate. */
public final class GenesisWorkflowReferenceProbe {
    private GenesisWorkflowReferenceProbe() { }
    public static void main(String[] args) {
        int code = 2;
        try {
            need(args.length == 3 && (args[0].equals("prepare") || args[0].equals("audit")), "Require prepare|audit REQUEST NEW_CONTROL_ROOT");
            Path source = Path.of(args[1]).toAbsolutePath().normalize().toRealPath(), root = Path.of(args[2]).toAbsolutePath().normalize().toRealPath();
            var request = GenesisRequest.parse(NextflowParameters.parse(utf8(bytes(source, 1_048_576)), 1_048_576, 100_000), source.getParent());
            if (args[0].equals("prepare")) {
                Files.createDirectory(root.resolve("inputs"));
                for (var input : GenesisInputs.sources(request).entrySet()) {
                    Path original = input.getValue().toRealPath(); var before = pin(original, request.limits().inputBytes(), () -> { });
                    Path captured = root.resolve("inputs").resolve(input.getKey()); Files.copy(original, captured);
                    need(before.equals(pin(captured, before.bytes(), () -> { })) && before.equals(pin(original, before.bytes(), () -> { })), "Control source changed during capture");
                }
                var data = GenesisInputs.read(root, request, () -> { }); GenesisInputs.prepare(root, request, data, () -> { });
                var parameters = new StringBuilder("key\tvalue\n");
                for (var entry : GenesisRequest.parameters(request).entrySet()) parameters.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
                Files.writeString(root.resolve("parameters.tsv"), parameters, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                try (var resource = GenesisWorkflowToolsCli.class.getResourceAsStream("/org/jlinalg/omics/genesis/run_genesis_workflows.R")) {
                    need(resource != null, "Missing frozen control worker"); Files.copy(resource, root.resolve("worker.R"));
                }
                Files.createDirectory(root.resolve("worker")); Files.createDirectory(root.resolve("tmp"));
                System.out.println("PREPARED_TYPED_DEVELOPMENT_CONTROL_NO_ANALYSIS_ADMITTED");
            } else {
                var data = GenesisInputs.read(root, request, () -> { }); var audit = GenesisWorkflowAudit.outputs(root, request, data, () -> { }, true);
                writeJson(root.resolve("native-component-audit.json"), SnprelateAudit.map("result_admitted", false, "component_audit_passed", true,
                    "original_execution_supervised_by_public_CLI", false, "scientific_audit", audit, "new_catalog_credit", false, "whole_goal_complete", false));
                System.out.println("PASS_NATIVE_DEVELOPMENT_COMPONENT_CONTRACT_ONLY_NO_ANALYSIS_ADMITTED");
            }
            code = 0;
        } catch (Exception failure) {
            System.err.println("COMPONENT_FAIL2_NO_PROJECT_RESULT: " + failure); failure.printStackTrace(System.err);
        }
        System.exit(code);
    }
}
