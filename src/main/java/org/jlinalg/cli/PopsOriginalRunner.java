/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Runs the unchanged Python source as separate address-bounded child processes. */
final class PopsOriginalRunner {
    record Outcome(String name, int exit, int warnings, int convergenceWarnings) { }
    private PopsOriginalRunner() { }
    static Outcome execute(Path root, PopsOriginalFiles.Config c, PopsOriginalRuntime.Runtime runtime, String name, Path script, List<String> arguments) throws Exception {
        long bytes = Math.multiplyExact(c.n("python_address_space_mb"), 1048576L);
        List<String> command = new ArrayList<>(List.of("/usr/bin/prlimit", "--as=" + bytes, "--", "/usr/bin/setpriv", "--pdeathsig", "KILL", "--", runtime.python().toString(), "-I", "-B", script.toString())); command.addAll(arguments);
        Path logs = root.resolve("runtime-logs"); write(logs.resolve(name + ".command.txt"), String.join("\n", command) + "\n");
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectOutput(logs.resolve(name + ".stdout").toFile()).redirectError(logs.resolve(name + ".stderr").toFile());
        for (String key : List.of("PYTHONPATH", "PYTHONHOME", "PYTHONSTARTUP", "LD_PRELOAD", "LD_AUDIT", "LD_LIBRARY_PATH", "OPENBLAS_CORETYPE")) builder.environment().remove(key);
        for (String key : List.of("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS", "NUMEXPR_NUM_THREADS", "BLIS_NUM_THREADS")) builder.environment().put(key, "1");
        builder.environment().put("PYTHONDONTWRITEBYTECODE", "1"); builder.environment().put("TMPDIR", root.resolve("tmp").toString());
        long started = System.nanoTime(); Process child;
        try { child = builder.start(); } catch (IOException | SecurityException e) { throw new Prerequisite("Original Python execution unavailable or forbidden: " + e + ". Supply permitted setup or skip dependent analysis."); }
        int exit;
        try { exit = child.waitFor(); } catch (InterruptedException e) { child.destroyForcibly(); Thread.currentThread().interrupt(); throw e; }
        int warnings = 0, convergence = 0;
        try (var in = Files.newBufferedReader(logs.resolve(name + ".stderr"))) {
            for (String line; (line = in.readLine()) != null;) { if (line.contains("Warning:") || line.startsWith("WARNING:")) warnings++; if (line.contains("ConvergenceWarning:")) convergence++; }
        }
        properties(logs.resolve(name + ".process.properties"), Map.of("exit", Integer.toString(exit), "elapsed_ms", Long.toString((System.nanoTime() - started) / 1000000), "warnings", Integer.toString(warnings), "convergence_warnings", Integer.toString(convergence), "address_space_bytes", Long.toString(bytes)));
        if (exit != 0) throw new IOException("Original PoPS stage " + name + " exited " + exit + ". " + diagnostic(logs, name) + " Retain raw outputs. For address-space errors increase permitted python_address_space_mb; otherwise resolve the named source/input cause and retry NEW output. No complete result is accepted; no source was patched.");
        if (convergence > 0) throw new IOException("Original PoPS stage " + name + " reported " + convergence + " ConvergenceWarning(s). Finite raw coefficients are UNADMITTED. " + diagnostic(logs, name) + " The selected source defaults were not changed; retain this bundle and skip dependent interpretation unless a separately reviewed scientific profile resolves convergence.");
        return new Outcome(name, exit, warnings, convergence);
    }
    private static String diagnostic(Path root, String name) throws IOException {
        for (String suffix : List.of(".stderr", ".stdout")) {
            Path path = root.resolve(name + suffix); if (!Files.isRegularFile(path) || Files.size(path) == 0) continue;
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            try (var channel = Files.newByteChannel(path)) { channel.position(Math.max(0, channel.size() - buffer.capacity())); while (buffer.hasRemaining() && channel.read(buffer) >= 0) { } }
            buffer.flip(); return "Diagnostic tail from runtime-logs/" + name + suffix + ": " + StandardCharsets.UTF_8.decode(buffer) + "\n";
        }
        return "No nonempty source diagnostic was recorded. Inspect its exact command and exit. ";
    }
}
