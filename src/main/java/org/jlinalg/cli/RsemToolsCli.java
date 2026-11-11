/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Typed source setup and original alignment-mode expression fitting. */
final class RsemToolsCli {
    private RsemToolsCli() { }
    private static final String RECOVERY = "Preserve WARNINGS.md, analysis-status.json, original process logs, ledgers and partial scientific files. Correct the named input, reference, runtime, integrity or capacity problem and retry to NEW output. Missing or forbidden dependencies mean SKIP3 and no expression result. Setup requires explicit --allow-install; network downloads also require --allow-download. Analysis never installs software. Original execution requires Linux x86_64 Java; on Windows run the command inside WSL. Scientific checks do not certify convergence, biological suitability or whole-paper equivalence.";
    static int run(String[] args, PrintStream out, PrintStream err) {
        Path directory = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { out.print(help()); return 0; }
            var options = options(args); String method = required(options, "method");
            if (method.equals("limits")) { keys(options, Set.of("method")); out.write(RsemJson.bytes(RsemRecipe.limitValues())); return 0; }
            if (method.equals("setup")) keys(options, Set.of("method", "out", "version", "compiler-spec", "source-archive", "cache", "allow-install", "allow-download"));
            else if (method.equals("fit")) keys(options, Set.of("method", "out", "recipe"));
            else throw new IOException("Unknown RSEM method; choose setup, fit or limits");
            if (method.equals("setup") && !RsemPayload.profiles().containsKey(required(options, "version"))) throw new IOException("Unknown exact RSEM source release");
            directory = Path.of(required(options, "out")).toAbsolutePath().normalize();
            if (directory.getParent() == null || !directory.getParent().toRealPath().equals(directory.getParent())) throw new IOException("Require an existing canonical output parent and a NEW output directory");
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Existing RSEM output is preserved; choose a NEW directory");
            var fitSpec = method.equals("fit") ? RsemRecipe.read(Path.of(required(options, "recipe"))) : null;
            Path compilerSpec = method.equals("setup") ? Path.of(required(options, "compiler-spec")).toAbsolutePath().normalize() : null;
            byte[] compilerRaw = compilerSpec == null ? null : AdmixtureInputs.raw(compilerSpec, 2097152);
            var compilerTools = compilerSpec == null ? null : RsemSetup.tools(compilerSpec, compilerRaw);
            if (fitSpec != null) { disjoint(directory, fitSpec.build()); for (Path path : fitSpec.libraries()) disjoint(directory, path); }
            if (compilerTools != null) for (Path path : compilerTools.roots()) disjoint(directory, path);
            Files.createDirectory(directory); owned = true;
            write(directory.resolve("request.json"), options);
            if (!"Linux".equals(System.getProperty("os.name")) || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch"))) throw new RsemProcess.UnavailableDependency("Original RSEM requires Linux x86_64 Java. Use a permitted Linux host or WSL; otherwise skip dependent analysis.");
            RsemPayload.originalPath(directory);
            if (method.equals("setup")) {
                String pin = AdmixtureInputs.digest(compilerRaw);
                Files.write(directory.resolve("compiler-request.json"), compilerRaw, StandardOpenOption.CREATE_NEW);
                if (!pin.equals(RepeatInputs.hash(compilerSpec, 2097152, () -> { }))) throw new IOException("Compiler declaration changed while being read");
                var built = RsemBuild.build(required(options, "version"), optionalPath(options, "source-archive"), optionalPath(options, "cache"), options.containsKey("allow-download"), options.containsKey("allow-install"), compilerTools, directory.resolve("build"), RsemBuild.Limits.defaults(), () -> {
                    try { if (!pin.equals(RepeatInputs.hash(compilerSpec, 2097152, () -> { }))) throw new IOException("Compiler declaration changed during setup"); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                });
                built.verify(() -> { });
                write(directory.resolve("COMPLETED.json"), Map.of("operation", "setup", "status", "COMPLETED_EXACT_ORIGINAL_SOURCE_SETUP", "build_home", built.home(), "build_receipt_sha256", built.recordSha256(), "source_version", built.profile().version(), "library_directories", built.libraries(), "scientific_result_admitted", false));
                Files.writeString(directory.resolve("WARNINGS.md"), "# RSEM source setup complete; no analysis performed\n\nUse the exact build_home, build_receipt_sha256 and library_directories in COMPLETED.json in a reviewed fitting recipe.\n\n" + RECOVERY + "\n", StandardOpenOption.CREATE_NEW);
            } else {
                var spec = fitSpec; spec.verify();
                byte[] raw = AdmixtureInputs.raw(spec.recipe(), 2097152); if (!spec.sha256().equals(AdmixtureInputs.digest(raw))) throw new IOException("Recipe changed during protected copy");
                Files.write(directory.resolve("recipe-request.json"), raw, StandardOpenOption.CREATE_NEW);
                spec.limits().audit().heap(); spec.limits().fit().output().heap();
                var built = RsemSetup.open(spec.version(), spec.build(), spec.buildReceiptSha256(), spec.libraries(), directory.resolve("source-reverification.jsonl"), () -> { });
                var payload = RsemPayload.stageBuilt(built, spec.libraries(), directory.resolve("payload"), () -> { });
                var input = RsemInputs.prepare(spec.input(), directory.resolve("inputs"), spec.limits().inputs(), () -> { });
                RsemProcess.Check immutable = () -> { spec.verify(); input.verify(() -> { }); built.verify(() -> { }); };
                var runtime = RsemRuntime.bind(payload, directory.resolve("binding"), RsemBuild.Limits.defaults().host(), spec.limits().audit().wholeWallMillis(), immutable, () -> { });
                var audit = RsemInputAudit.inspect(runtime, input, directory.resolve("input-audit"), spec.limits().audit(), () -> { });
                var fit = RsemFit.run(audit, spec.settings(), directory.resolve("fit"), spec.limits().fit(), () -> { });
                fit.verify(() -> { }); immutable.verify();
                write(directory.resolve("COMPLETED.json"), Map.of("operation", "fit", "status", "COMPLETED_ORIGINAL_FITTING_AND_NATIVE_AUDITS", "source_version", spec.version(), "result", directory.resolve("fit/RESULT_ADMITTED.json"), "result_sha256", RepeatInputs.hash(directory.resolve("fit/RESULT_ADMITTED.json"), spec.limits().fit().metadataBytes(), () -> { }), "transcripts", fit.audit().results().transcripts(), "genes", fit.audit().results().genes(), "numeric_cells", fit.audit().results().numericCells(), "scientific_result_admitted", true));
                Files.writeString(directory.resolve("WARNINGS.md"), "# RSEM fitting completed: review warnings and original logs\n\nRead fit/WARNINGS.md, fit/RESULT_ADMITTED.json, input-audit/INPUT_AUDIT.json and all original stdout/stderr. Missing M5 and precision warnings require review. Confirm the declared source release, reference, alignment protocol and model settings suit this study.\n\n" + RECOVERY + "\n", StandardOpenOption.CREATE_NEW);
            }
            write(directory.resolve("analysis-status.json"), Map.of("status", "COMPLETED", "exit_code", 0, "operation", method));
            out.println("RSEM " + method + " completed: " + directory + ". Review WARNINGS.md and COMPLETED.json."); return 0;
        } catch (IOException | RuntimeException failure) {
            int code = failure instanceof RsemProcess.UnavailableDependency ? 3 : 2;
            String diagnostic = "# RSEM " + (code == 3 ? "SKIPPED" : "FAILED") + " - NO COMPLETED ANALYSIS\n\n" + failure + "\n\nRequired action: " + RECOVERY + "\n";
            if (owned) try {
                if (Files.exists(directory.resolve("COMPLETED.json"))) Files.move(directory.resolve("COMPLETED.json"), directory.resolve("UNADMITTED.json"));
                Files.writeString(directory.resolve("WARNINGS.md"), diagnostic, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                write(directory.resolve("analysis-status.json"), Map.of("status", code == 3 ? "SKIPPED" : "FAILED", "exit_code", code, "cause", failure.toString(), "required_action", RECOVERY));
            } catch (IOException extra) { failure.addSuppressed(extra); }
            err.print(diagnostic); return code;
        }
    }
    private static void write(Path path, Object value) throws IOException { Files.write(path, RsemJson.bytes(value), StandardOpenOption.CREATE_NEW); }
    private static void disjoint(Path output, Path source) throws IOException { if (output.startsWith(source) || source.startsWith(output)) throw new IOException("RSEM output must be disjoint from existing build and read-only compiler/library roots: " + source); }
    private static Path optionalPath(Map<String,String> options, String key) { return options.containsKey(key) ? Path.of(options.get(key)).toAbsolutePath().normalize() : null; }
    private static String required(Map<String,String> options, String key) throws IOException { String value = options.get(key); if (value == null || value.isBlank()) throw new IOException("Missing RSEM --" + key); return value; }
    private static void keys(Map<String,String> options, Set<String> allowed) throws IOException { for (String key : options.keySet()) if (!allowed.contains(key)) throw new IOException("Unused or unknown RSEM --" + key + " for this method"); }
    private static Map<String,String> options(String[] args) throws IOException {
        var result = new LinkedHashMap<String,String>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || args[i].length() < 3) throw new IOException("Require named RSEM options; see --help");
            String key = args[i].substring(2), value;
            if (Set.of("allow-install", "allow-download").contains(key)) value = "true";
            else { if (++i >= args.length || args[i].startsWith("--") || args[i].isBlank()) throw new IOException("Missing RSEM option value: --" + key); value = args[i]; }
            if (result.put(key, value) != null) throw new IOException("Duplicate RSEM option: --" + key);
        }
        return result;
    }
    private static String help() { return """
        RSEM original alignment-mode fitting (Linux x86_64 Java; Windows: use WSL)
          rsem-tools --method setup --version VERSION --compiler-spec TOOLS.json --source-archive ORIGINAL.tar.gz --out NEW_DIRECTORY --allow-install
          rsem-tools --method setup --version VERSION --compiler-spec TOOLS.json --cache PRIVATE_CACHE --out NEW_DIRECTORY --allow-install --allow-download
          rsem-tools --method fit --recipe FIT.json --out NEW_DIRECTORY
          rsem-tools --method limits
        Setup supports exact source releases 1.2.28,1.3.0,1.3.1,1.3.2,1.3.3.
        Fitting requires an existing matching ordinary transcript reference prefix and source-build receipt SHA256.
        SAM/BAM/CRAM remain unmodified. CRAM requires its explicit local uncompressed FASTA plus adjacent .fai.
        Recipe settings declare read type0..3, auxiliary tag (including empty), strand, seed, workers and posterior.
        Defaults have small complete-population capacities; inspect limits and raise the named capacities with sufficient -Xmx for larger studies. No rows or parameters are sampled or truncated.
        Existing outputs are preserved. Exit0=complete,2=failed,3=skipped dependency/platform. Read WARNINGS.md, analysis-status.json, COMPLETED.json and original logs.
        Setup never installs a compiler. Supply permitted working compiler/header/library roots; missing or forbidden dependencies skip dependent fitting.
        Public workflow validation is still in development; retained original controls and full warnings are required before toolbox credit.
        """; }
}
