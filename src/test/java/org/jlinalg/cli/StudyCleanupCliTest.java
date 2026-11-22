/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyExecutionEngine;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyCleanupCliTest {
    @TempDir Path root;
    private final StudyProject.Actor actor = StudyProject.Actor.human("cli-scientist");
    private record Reply(int status, String out, String error) { }

    @Test void previewApplyAndDecisionCommandsWorkThroughTheStudyCli() throws Exception {
        Files.writeString(root.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\nGENE1\t.5\t.1\t.001\n");
        Files.writeString(root.resolve("study.yaml"), """
            schema_version: 1
            project: {id: cli-cleanup, title: Cleanup, description: CLI cleanup fixture.}
            resources: {downloads: deny, network: offline}
            datasets:
              - id: results
                type: transcriptomics
                stage: association_results
                files: {results: results.tsv}
                feature_id_type: gene_symbol
            hypotheses:
              - id: H1
                description: Test result lifecycle.
                outcome: BMI
                analysis: {mode: manual, goals: [meta_analysis]}
            """);
        StudyProject.resolve(root.resolve("study.yaml"), root, actor);
        var plan = StudyPlanner.create(root, actor);
        StudyPlanner.approve(root, plan.id(), actor, true);
        StudyOperationExecutor executor = context -> {
            Files.writeString(context.outputDirectory().resolve("result.txt"), "fixture");
            return StudyOperationExecutor.Result.complete("Fixture", List.of("result.txt"));
        };
        String first = StudyExecutionEngine.run(root, actor, executor, true, true).runId();
        String second = StudyExecutionEngine.run(root, actor, executor, true, true).runId();
        List<?> operations = (List<?>) plan.document().get("operations");
        String operation = ((Map<?, ?>) operations.get(operations.size() - 1)).get("id").toString();
        String old = first + "/" + operation, newer = second + "/" + operation;
        assertEquals(0, invoke("pin", "--result", old, "--reason", "Cited result", "--actor-id", actor.id()).status);
        assertEquals(2, invoke("cleanup", "--dry-run", "--result", old).status);
        assertEquals(0, invoke("unpin", "--result", old, "--reason", "Updated citation", "--actor-id", actor.id()).status);
        assertEquals(0, invoke("supersede", "--result", old, "--replacement", newer,
            "--reason", "Reviewed newer result", "--actor-id", actor.id()).status);
        Path preview = root.resolve("cli-preview.yaml");
        Reply dry = invoke("cleanup", "--dry-run", "--result", old, "--out", preview.toString());
        assertEquals(0, dry.status, dry.error);
        assertTrue(dry.out.contains("No results deleted"));
        assertTrue(Files.isRegularFile(root.resolve("runs/" + first + "/operations/" + operation + "/result.txt")));
        assertEquals(2, invoke("cleanup", "--apply", preview.toString()).status, "Named actor required");
        assertEquals(2, invoke("cleanup", "--apply", preview.toString(), "--dry-run", "--actor-id", actor.id()).status);
        Reply applied = invoke("cleanup", "--apply", preview.toString(), "--actor-id", actor.id());
        assertEquals(0, applied.status, applied.error);
        assertTrue(applied.out.contains("Deleted 1 obsolete output"));
        assertFalse(Files.exists(root.resolve("runs/" + first + "/operations/" + operation + "/result.txt")));
        assertEquals(0, invoke("verify").status);
        assertTrue(invoke("help").out.contains("study cleanup"));
    }

    @Test void ambiguousOrIncompleteCleanupCommandsFailWithoutCreatingAProject() {
        assertEquals(2, invoke("cleanup").status);
        assertEquals(2, invoke("pin", "--result", "run/op", "--reason", "missing actor").status);
        assertEquals(2, invoke("supersede", "--result", "run/op", "--actor-id", actor.id()).status);
        assertEquals(2, invoke("cleanup", "--dry-run", "--apply", "missing.yaml").status);
    }

    private Reply invoke(String... arguments) {
        List<String> args = new ArrayList<>(List.of(arguments));
        args.add("--project"); args.add(root.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream(), error = new ByteArrayOutputStream();
        int status = StudyCli.run(args.toArray(String[]::new), new PrintStream(out), new PrintStream(error));
        return new Reply(status, out.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }
}
