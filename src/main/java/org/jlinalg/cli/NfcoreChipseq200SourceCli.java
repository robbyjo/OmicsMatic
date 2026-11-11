/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete original source execution control. Native 2.0 output contracts remain required. */
public final class NfcoreChipseq200SourceCli {
    private NfcoreChipseq200SourceCli() { }
    static final String ACTION = "Required action: this is an original ChIP-seq2.0.0 source execution/namespace control, with zero catalog-tool or biological admission. Require the remaining complete source-specific scientific output contracts before downstream claims. Read WARNINGS.md, every image/engine process ledger, engine log and complete task trace. SKIP3 means a missing/forbidden exact runtime/image/capability: dependent analysis skipped; perform separate permitted setup or use an allowed environment. FAIL2 means invalid input, changed integrity, process/cleanup/capacity or source output failure: no control result admitted. Preserve complete original data, all warnings and failed attempts; resolve the named cause and use NEW paths. Source image hashes establish observed-byte integrity, not authenticity, author history or whole-paper reproduction. No installation or security-setting changes occur during analysis. Cell Ranger remains excluded.";
    public static void main(String[] args) {
        Path out = null; boolean owned = false;
        try {
            if (args.length == 1 && args[0].equals("--help")) { emit(System.out, "--recipe ENGINE_RECIPE --image-set DECLARED_IMAGES --container-home EXACT_APPTAINER_HOME --home EXACT_NEXTFLOW22_HOME --setup-report CURRENT_REPORT --executor-cpus N --executor-memory-bytes N --out NEW_DIRECTORY\nAdd --health-only true to check the exact complete image set without launching a workflow.\n" + ACTION); return; }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--recipe", "--image-set", "--container-home", "--home", "--setup-report", "--executor-cpus", "--executor-memory-bytes", "--out", "--health-only").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require complete unique source-control options; see --help");
            if (!options.keySet().containsAll(Set.of("--recipe", "--image-set", "--container-home", "--home", "--setup-report", "--executor-cpus", "--executor-memory-bytes", "--out"))) throw new IOException("Declare every exact source-control prerequisite explicitly; see --help");
            if (!System.getProperty("os.name").equals("Linux")) throw new NextflowProcess.Unavailable("Use Linux Java including WSL; dependent source execution skipped.");
            out = Path.of(options.get("--out")).toAbsolutePath().normalize();
            if (out.getParent() == null || !out.getParent().toRealPath().equals(out.getParent()) || out.toString().chars().anyMatch(c -> c < 33 || c > 126 || c == ',' || c == ':')) throw new IOException("Use a NEW canonical ASCII control path without whitespace, comma or colon"); Files.createDirectory(out); owned = true;
            Path recipeFile = capture(Path.of(options.get("--recipe")).toAbsolutePath().normalize(), out.resolve("engine-recipe.json")), imageFile = capture(Path.of(options.get("--image-set")).toAbsolutePath().normalize(), out.resolve("image-set.json"));
            var recipe = NextflowRecipe.read(recipeFile); if (!recipe.profile().equals("linux-x86_64-nextflow-22.10.6")) throw new NextflowProcess.Unavailable("This source-control profile explicitly selects exact Nextflow22.10.6. No author engine is inferred; unsupported engine skipped.");
            int cpus = Integer.parseInt(options.get("--executor-cpus")); long memory = Long.parseLong(options.get("--executor-memory-bytes"));
            Path containerHome = Path.of(options.get("--container-home")).toAbsolutePath().normalize(); var containers = new NfcoreContainerSet(containerHome, imageFile, Files.createDirectory(out.resolve("container-admission")), recipe.process());
            validateSource(recipe, containers.overlay(out.resolve("engine"), cpus, memory), containerHome, cpus);
            var managed = NextflowRuntime.open(Path.of(options.get("--home")).toAbsolutePath().normalize(), recipe.profile(), Path.of(options.get("--setup-report")).toAbsolutePath().normalize(), () -> { });
            containers.health(); boolean healthOnly = false; if (options.containsKey("--health-only")) { if (!Set.of("true", "false").contains(options.get("--health-only"))) throw new IOException("Require explicit health-only boolean"); healthOnly = options.get("--health-only").equals("true"); }
            if (healthOnly) { writeJson(out.resolve("HEALTH_ONLY_COMPLETED.json"), Map.of("status", "source-image-health-only", "scientific_analysis_executed", false, "new_tool_credit", 0)); emit(System.out, "All 33 complete source-declared images passed namespace health only.\n" + ACTION); return; }
            Path own = Path.of(NfcoreChipseq200SourceCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize(); if (!Files.isRegularFile(own) || !own.toString().endsWith(".jar")) throw new NextflowProcess.Unavailable("Use the packaged development artifact; unbound classpath source execution skipped"); Pin ownPin = pin(own, 1000000000L, () -> { }), requestPin = pin(recipeFile, 4194304, () -> { }), imagesPin = pin(imageFile, 1048576, () -> { });
            NextflowProcess.Check guard = () -> { containers.verify(); managed.verify(() -> { }); if (!pin(own, 1000000000L, () -> { }).equals(ownPin) || !pin(recipeFile, 4194304, () -> { }).equals(requestPin) || !pin(imageFile, 1048576, () -> { }).equals(imagesPin)) throw new IOException("Source execution implementation/request/image declaration integrity changed"); };
            List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Xmx" + recipe.heapMib() + "m", "-XX:ActiveProcessorCount=" + recipe.cpus(), "-cp", own.toString(), "org.jlinalg.cli.NfcoreCapturedToolsCli", "--recipe", recipeFile.toString(), "--out", out.resolve("engine").toString(), "--home", managed.home().toString(), "--setup-report", Path.of(options.get("--setup-report")).toAbsolutePath().normalize().toString());
            NfcoreProcess.executeSession(command, containers.environment(), out.resolve("engine-process"), out, out, recipe.process(), guard, () -> { if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Source control interrupted"); });
            var result = NfcoreEngineBundle.verify(out.resolve("engine"), () -> { }); guard.verify();
            writeJson(out.resolve("SOURCE_EXECUTION_COMPLETED.json"), Map.of("status", "original-chipseq200-source-execution-control-only", "engine_manifest", result.manifest(), "published_files", result.outputFiles(), "published_bytes", result.outputBytes(), "scientific_output_contracts_complete", false, "biological_accuracy_certified", false, "new_tool_credit", 0));
            Files.writeString(out.resolve("WARNINGS.md"), "# Original source execution control only\n\n" + ACTION + "\n\n" + utf8(bytes(result.attempt().resolve("WARNINGS.md"), 1048576)), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); emit(System.out, "Complete original source execution/trace/publication control completed; native 2.0 output contracts remain required.\n" + ACTION);
        } catch (Exception | LinkageError failure) {
            int code = failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2; emit(System.err, (code == 3 ? "SKIP3 ANALYSIS SKIPPED: " : "FAIL2 NO RESULT: ") + failure + "\n" + ACTION);
            if (owned && out != null) try { writeJson(out.resolve("FAILURE.json"), Map.of("status", code == 3 ? "SKIP3" : "FAIL2", "error", failure.toString(), "required_action", ACTION)); Files.writeString(out.resolve("WARNINGS.md"), "# " + (code == 3 ? "SKIP3" : "FAIL2") + "\n\n" + failure + "\n\n" + ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); } catch (IOException diagnostic) { emit(System.err, "Retain the complete failed control; diagnostic write failed: " + diagnostic); }
            System.exit(code);
        }
    }
    private static Path capture(Path source, Path dest) throws IOException { Pin before = pin(source, 4194304, () -> { }); Files.copy(source, dest); if (!pin(dest, 4194304, () -> { }).equals(before) || !pin(source, 4194304, () -> { }).equals(before)) throw new IOException("Complete source request changed during capture"); return dest; }
    private static void validateSource(NextflowRecipe.Request recipe, String overlay, Path home, int cpus) throws IOException {
        if (!recipe.workflow().entrypoint().equals("main.nf") || !recipe.workflow().configs().equals(List.of("nextflow.config", "runtime/host.config")) || !recipe.workflow().profiles().equals(List.of("singularity")) || !recipe.requireTasks()) throw new IOException("Require the exact original primary source configuration, profile and observed tasks");
        byte[] raw; try (var input = NfcoreChipseq200SourceCli.class.getResourceAsStream("/org/jlinalg/omics/nfcore/nfcore-source-lock.json")) { if (input == null) throw new IOException("Missing source lock"); raw = input.readNBytes(134642); }
        if (raw.length != 134641 || !sha(raw).equals("f5ef123c1d212061c202a1b5ef2d9fef62aa3f7cc764834d40c0c94799bf8646")) throw new IOException("Original source lock differs"); Map<?, ?> files = object(object(object(object(SimpleJson.parse(utf8(raw))).get("pipelines")).get("chipseq-2.0.0")).get("files")); var expected = new HashSet<String>();
        for (var row : files.entrySet()) { String name = member(text(row.getKey())); if (!pin(recipe.workflow().source().resolve(name), 1000000000L, () -> { }).equals(fingerprint(row.getValue(), 1000000000L))) throw new IOException("Original ChIP-seq2.0.0 source differs: " + name); expected.add(name); if (name.startsWith("bin/") && !Files.isExecutable(recipe.workflow().source().resolve(name))) throw new NextflowProcess.Unavailable("Original source helper execution is missing/forbidden: " + name + "; dependent analysis skipped."); }
        expected.add("runtime/host.config"); var actual = tree(recipe.workflow().source(), 10000, 1000000000L, 1000000000L, () -> { }); if (!actual.keySet().equals(expected) || actual.values().stream().anyMatch(v -> !v.kind().equals("file"))) throw new IOException("Unexpected source members/links beyond the explicit runtime binding");
        if (!utf8(bytes(recipe.workflow().source().resolve("runtime/host.config"), 1048576)).equals(overlay)) throw new IOException("Module/alias runtime image overlay differs from the complete exact source image binding");
        Map<?, ?> parameters = object(recipe.parameters()); for (var row : parameters.entrySet()) if (text(row.getKey()).startsWith("skip_") && Boolean.TRUE.equals(row.getValue())) throw new NextflowProcess.Unavailable("Complete source-control admission does not omit original required branches: " + row.getKey() + "; requested analysis skipped.");
        if (parameters.containsKey("max_cpus") && integer(parameters.get("max_cpus"), 1, 1024) > cpus) throw new IOException("Source task CPU maximum exceeds explicit executor capacity");
        if (recipe.tools().size() != 1 || !recipe.tools().get(0).name().equals("singularity") || !recipe.tools().get(0).executable().equals(home.resolve("bin/singularity"))) throw new IOException("Declare exact admitted private singularity launcher only");
    }
    private static void emit(java.io.PrintStream output, String text) { output.writeBytes((text + "\n").getBytes(StandardCharsets.UTF_8)); }
}
