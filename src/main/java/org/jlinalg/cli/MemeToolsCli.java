/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Closed MEME discovery operations; no arbitrary command, script or process arguments. */
public final class MemeToolsCli {
    private MemeToolsCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream error) {
        try {
            if (args.length == 0 || args.length == 1 && args[0].equals("--help")) { out.print(HELP); return 0; }
            if (args.length == 1 && args[0].equals("--profiles")) { out.println(String.join("\n", MemeDiscoveryRequest.PROFILES)); return 0; }
            if (args.length == 2 && args[0].equals("--template")) {
                require(MemeDiscoveryRequest.PROFILES.contains(args[1]), "Unknown exact MEME source profile");
                out.println(RsemJson.json(MemeDiscoveryRequest.template(args[1], "/absolute/input/primary.fa", "/absolute/input/background.bg"))); return 0;
            }
            require(args.length == 2 && args[0].equals("--request"), "Use --help or --request OPERATION.json");
            Path source = Path.of(args[1]).toAbsolutePath().normalize();
            var requestPin = NextflowFiles.pin(source, 2097152, () -> { });
            var operation = NextflowFiles.object(NextflowFiles.json(source, 2097152));
            NextflowFiles.integer(operation.get("schema_version"), 1, 1);
            String name = NextflowFiles.text(operation.get("operation"));
            Runnable check = () -> {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("MEME operation interrupted");
            };
            RsemProcess.Check operationCheck = () -> require(requestPin.equals(NextflowFiles.pin(source, 2097152, check)), "Original MEME operation request changed");
            operationCheck.verify();
            if (name.equals("DISCOVER")) {
                NextflowFiles.keys(operation, "schema_version", "operation", "recipe", "runtime_home", "build_evidence", "out");
                Path result = path(operation.get("out"));
                var output = MemeDiscoveryWorkflow.run(path(operation.get("recipe")), path(operation.get("runtime_home")), path(operation.get("build_evidence")), result, check, operationCheck);
                out.println("COMPLETE_ORIGINAL_MEME_DISCOVERY_ANALYSIS: " + output.root());
                out.println("Motifs=" + output.output().scientific().motifs().size() + "; read WARNINGS.md and keep the complete bundle. Require portable VERIFY before downstream use.");
                if (Files.size(result.resolve("native/fit/process.stderr")) > 0 || Files.size(result.resolve("native/render/process.stderr")) > 0)
                    error.println("WARNING: Original MEME fitting/report generation emitted diagnostics. Read native/fit/process.stderr, native/render/process.stderr and WARNINGS.md; exit0 does not clear these warnings.");
                return 0;
            }
            require(name.equals("VERIFY"), "Unknown MEME operation; use DISCOVER or VERIFY");
            NextflowFiles.keys(operation, "schema_version", "operation", "bundle");
            var verified = MemeDiscoveryWorkflow.verify(path(operation.get("bundle")), check);
            operationCheck.verify();
            out.println("VERIFIED_CAPTURED_MEME_DISCOVERY: " + verified.root());
            out.println("Complete captured raw input and scientific output replay passed. Producer paths were not opened; no current-host runtime ticket or biological certificate was created."); return 0;
        } catch (IOException | RuntimeException failure) {
            boolean skip = failure instanceof MemeDiscoveryRuntime.Unavailable || failure instanceof MemeDiscoveryInputs.Unsupported;
            error.println((skip ? "SKIP3: MEME ANALYSIS SKIPPED: " : "FAIL2: MEME NO RESULT ADMITTED: ") + failure);
            error.println("REQUIRED ACTION: " + MemeDiscoveryWorkflow.ACTION); return skip ? 3 : 2;
        }
    }
    private static Path path(Object raw) throws IOException {
        String value = NextflowFiles.text(raw); require(!value.isEmpty() && value.length() <= 32768 && value.chars().noneMatch(c -> c < 32 || c == 127), "Invalid bounded MEME path");
        try { Path path = Path.of(value); require(path.isAbsolute() && path.normalize().equals(path), "MEME operation paths must be normalized absolute paths"); return path; }
        catch (java.nio.file.InvalidPathException failure) { throw new IOException("Invalid MEME operation path", failure); }
    }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
    private static final String HELP = """
        MEME discovery development adapter
          --profiles
          --template MEME_5_5_9
          --request OPERATION.json

        DISCOVER operation (all paths absolute; output must be NEW):
        {"schema_version":1,"operation":"DISCOVER","recipe":"/data/recipe.json",
         "runtime_home":"/absolute/source-built/meme-home",
         "build_evidence":"/absolute/source-build-evidence","out":"/data/new-result"}

        Portable VERIFY operation, including on Windows:
        {"schema_version":1,"operation":"VERIFY","bundle":"/data/completed-result"}

        Use --template PROFILE to emit the separate complete scientific recipe.
        Supports exact original 4.9.1 base/patch2 and 5.5.4/5.5.9 profiles,
        canonical DNA, explicit positive order-zero background, Dirichlet prior,
        OOPS/ZOOPS/ANR and either strand choice. Raw inputs remain unchanged.
        Original Linux execution requires a source-built exact profile plus its
        completed build evidence, current dependencies and fresh scientific/report
        health. Analysis does not install software. Download/install only where
        permitted; unavailable/forbidden dependencies mean SKIP3 and skip analysis.
        FAIL2 admits no partial result. Read WARNINGS.md and retain process logs.
        Original MEME's educational/research/nonprofit license scope applies.
        This adapter does not infer paper inputs/settings, reproduce MEME-ChIP
        pipelines, independently reimplement motif significance or certify biology.
        """;
}
