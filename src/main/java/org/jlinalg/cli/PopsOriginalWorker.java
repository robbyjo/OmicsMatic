/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Complete upstream collection, under an owned worker; no standalone admission. */
public final class PopsOriginalWorker {
    private PopsOriginalWorker() { }
    public static void main(String[] args) {
        int exit = 2; Path root = null;
        try {
            if (args.length != 1) throw new IllegalArgumentException("Owned original workflow root required"); root = Path.of(args[0]).toRealPath();
            var c = PopsOriginalFiles.loadConfig(root.resolve("effective.properties")); PopsOriginalInputs.check(root, c); PopsOriginalCode.check(root); PopsOriginalRuntime.sourcesUnchanged(root);
            System.out.println("Checking complete adopted runtime membership and bytes before any source model");
            var runtime = PopsOriginalRuntime.check(c, true); Map<String, String> identity = PopsOriginalRuntime.identity(runtime);
            if (!Files.readString(root.resolve("runtime-identity.tsv")).equals(PopsWorkflowCode.render(identity))) throw new IOException("Original runtime identity differs from the captured parent identity");
            Files.createDirectory(root.resolve("raw")); Files.createDirectory(root.resolve("runtime-logs")); Files.createDirectory(root.resolve("tmp"));
            List<PopsOriginalRunner.Outcome> outcomes = new ArrayList<>();
            for (var job : PopsOriginalCommands.jobs(root, c)) {
                System.out.println("Running unchanged original " + c.get("edition") + " stage " + job.name());
                outcomes.add(PopsOriginalRunner.execute(root, c, runtime, job.name(), root.resolve("upstream").resolve(job.script()), job.arguments()));
            }
            Path audit = root.resolve("original-audit.py");
            try (var in = PopsOriginalWorker.class.getResourceAsStream(PopsOriginalCode.AUDIT)) { if (in == null) throw new Prerequisite("Packaged result auditor missing; build complete current source or skip analysis"); Files.copy(in, audit); }
            String auditHash = sha(audit); write(root.resolve("control.tsv"), control(c));
            outcomes.add(PopsOriginalRunner.execute(root, c, runtime, "audit", audit, List.of(root.toString())));
            if (!auditHash.equals(sha(audit))) throw new IOException("Captured result auditor changed");
            StringBuilder warnings = new StringBuilder("code\tcount\taction\n");
            for (var outcome : outcomes) if (outcome.warnings() > 0) warnings.append(outcome.name().equals("audit") ? "AUDIT_WARNING_" : "SOURCE_WARNING_").append(outcome.name()).append('\t').append(outcome.warnings()).append("\tInspect runtime-logs/").append(outcome.name()).append(".stderr; raw warnings are retained and source parameters were not changed.\n");
            write(root.resolve("results/warnings.tsv"), warnings.toString());
            properties(root.resolve("results/declarations.properties"), Map.of("profile", PopsOriginalFiles.PROFILE, "edition", c.get("edition"), "task", c.get("task"), "genome_build", c.get("genome_build"), "feature_set_id", c.get("feature_set_id"), "target_source_id", c.get("target_source_id"), "original_source_modified", "false", "author_environment_reproduced", "false"));
            System.out.println("Checking complete adopted runtime after source execution");
            PopsOriginalRuntime.check(c, true); if (!identity.equals(PopsOriginalRuntime.identity(runtime))) throw new IOException("Adopted original runtime changed during execution");
            PopsOriginalInputs.check(root, c); PopsOriginalRuntime.sourcesUnchanged(root); PopsOriginalCode.check(root);
            properties(root.resolve("WORKER_DONE.properties"), Map.of("profile", PopsOriginalFiles.PROFILE, "edition", c.get("edition"), "task", c.get("task"), "source_jobs", Integer.toString(outcomes.size() - 1), "explicit_convergence_warnings", "0"));
            System.out.println("Original stages completed provisionally; only the parent can admit the complete workflow."); System.out.flush(); if (System.out.checkError() || System.err.checkError()) throw new IOException("Worker console recording failed"); exit = 0;
        } catch (Throwable error) {
            exit = error instanceof Prerequisite || error instanceof LinkageError ? 3 : 2;
            if (root != null) try { write(root.resolve("WORKER_FAILURE.txt"), error + "\nNo complete workflow result. Retain raw source outputs and resolve the named cause; retry NEW output.\n"); } catch (Throwable recording) { System.err.println("FAIL2_RECORDING_UNAVAILABLE " + recording); exit = 2; }
            System.err.println((exit == 3 ? "SKIP3" : "FAIL2") + "_ORIGINAL_POPS_WORKER " + error); error.printStackTrace(System.err);
        }
        System.exit(exit);
    }
    private static String control(PopsOriginalFiles.Config c) { StringBuilder out = new StringBuilder("key\tvalue\n"); for (var e : c.values().entrySet()) out.append(e.getKey()).append('\t').append(e.getValue()).append('\n'); return out.toString(); }
}
