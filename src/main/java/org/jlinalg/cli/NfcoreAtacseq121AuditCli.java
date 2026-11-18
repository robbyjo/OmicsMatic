/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Portable source-output consistency audit of an already complete captured engine result. */
public final class NfcoreAtacseq121AuditCli {
    private NfcoreAtacseq121AuditCli() { }
    private static final String ACTION = "Required action: require a complete captured original engine result and inspect WARNINGS.md and original logs. This component checks source, sample mapping, tasks and selected outputs; it does not execute a pipeline, authenticate its image/runtime, refit models or admit a biological/whole-workflow result. FAIL2 means incomplete/changed/unassessable data or a failed component contract: retain all evidence, resolve the named cause and retry a NEW audit path. A successful component audit does not convert an earlier failed adapter attempt into a completed one. Use the separately qualified typed workflow for execution. Hashes establish integrity, not authenticity.";
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    static int run(String[] args, PrintStream output, PrintStream error) {
        Path root = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                emit(output, "nfcore-tools ATACSEQ-AUDIT-1.2.1 --engine-result COMPLETE_SESSION_OR_ATTEMPT --out NEW_AUDIT\n"+ACTION); return output.checkError() ? 2 : 0;
            }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i+1 >= args.length || !Set.of("--engine-result", "--out").contains(args[i]) || options.putIfAbsent(args[i], args[i+1]) != null) throw new IOException("Require complete unique --engine-result and --out options");
            if (options.size() != 2) throw new IOException("Require complete unique --engine-result and --out options");
            root = Path.of(options.get("--out")).toAbsolutePath().normalize();
            Files.createDirectory(root); owned = true;
            var engine = NfcoreEngineBundle.verify(Path.of(options.get("--engine-result")).toAbsolutePath().normalize(), () -> { });
            var report = new LinkedHashMap<String, Object>(NfcoreAtacseq121Audit.read(engine));
            report.put("scientific_result_admitted", false); report.put("source_component_only", true); report.put("runtime_image_authenticated_by_this_component", false);
            writeJson(root.resolve("audit.json"), report);
            Files.writeString(root.resolve("WARNINGS.md"), "# ATAC source-output component only\n\n"+ACTION+"\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            writeJson(root.resolve("COMPONENT_AUDIT_COMPLETED.json"), Map.of("audit", pin(root.resolve("audit.json"), 67108864, () -> { }), "scientific_result_admitted", false, "original_engine_manifest", engine.manifest()));
            emit(output, "Complete original ATAC source-output consistency component checked: "+root+"\n"+ACTION);
            if (output.checkError()) throw new IOException("Component completion output failed; retain the candidate audit and failure evidence");
            return 0;
        } catch (Exception | LinkageError failure) {
            emit(error, "FAIL2 NO COMPONENT RESULT: "+failure+"\n"+ACTION);
            if (owned && root != null) try {
                writeJson(root.resolve("FAILURE.json"), Map.of("status", "FAIL2_NO_COMPONENT_RESULT", "error", failure.toString(), "required_action", ACTION));
                Path warning = root.resolve(Files.exists(root.resolve("WARNINGS.md")) ? "WARNINGS_FAILURE.md" : "WARNINGS.md");
                Files.writeString(warning, "# FAIL2 NO COMPONENT RESULT\n\n"+failure+"\n\n"+ACTION+"\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { emit(error, "Retain the audit directory; diagnostic write failed: "+diagnostic); }
            return 2;
        }
    }
    private static void emit(PrintStream stream, String value) { stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8)); }
}
