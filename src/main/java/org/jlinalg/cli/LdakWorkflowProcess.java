/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.LdakWorkflowFiles.*;
import org.jlinalg.cli.LdakWorkflowRequest.Config;

/** Fixed typed original-5.2 commands; no shell, implicit install or free-form flags. */
final class LdakWorkflowProcess {
    record Stage(String name, List<String> arguments, List<String> requiredOutputs) {
        Stage { arguments = List.copyOf(arguments); requiredOutputs = List.copyOf(requiredOutputs); }
    }
    private LdakWorkflowProcess() { }
    static String index(int i) { return String.format(Locale.ROOT, "%03d", i); }
    static List<Stage> plan(Config c) {
        var result = new ArrayList<Stage>();
        if (c.sumher()) {
            var args = new ArrayList<>(List.of("--sum-hers", "result", "--tagfile", "../../inputs/reference.tagging",
                "--summary", "../../inputs/summary", "--check-sums", c.get("check_sums"), "--allow-ambiguous", c.get("allow_ambiguous"),
                "--genomic-control", c.get("inflation").equals("multiplicative") ? "YES" : "NO",
                "--intercept", c.get("inflation").equals("additive") ? "YES" : "NO",
                "--chisq-solver", c.get("solver").equals("maximum-likelihood") ? "YES" : "NO"));
            if (!c.get("prevalence").equals("-")) args.addAll(List.of("--prevalence", c.get("prevalence"), "--ascertainment", c.get("ascertainment")));
            if (!c.get("cutoff").equals("-")) args.addAll(List.of("--cutoff", c.get("cutoff")));
            controls(args, c);
            var required = new ArrayList<>(List.of("result.cats", "result.cross", "result.enrich", "result.extra", "result.hers", "result.labels", "result.overlap", "result.share", "result.taus"));
            if (!c.get("prevalence").equals("-")) required.addAll(List.of("result.cats.liab", "result.hers.liab"));
            result.add(new Stage("sumher", args, required));
        } else {
            if (c.get("thinning").equals("YES")) {
                var args = new ArrayList<>(List.of("--thin", "result", "--bfile", "../../inputs/genotypes", "--window-prune", c.get("window_r2"), "--window-kb", c.get("window_kb")));
                if (!c.get("predictors").equals("-")) args.addAll(List.of("--extract", "../../inputs/predictors"));
                args.addAll(List.of("--max-threads", "1"));
                result.add(new Stage("thin", args, List.of("result.in", "result.out", "result.trivial")));
            }
            for (int i = 0; i < c.alphas().size(); i++) {
                String id = index(i);
                var args = new ArrayList<>(List.of("--calc-kins-direct", "result", "--bfile", "../../inputs/genotypes", "--power", c.alphas().get(i), "--hwe-stand", c.get("hwe_stand")));
                if (c.get("weights").equals("-")) args.addAll(List.of("--ignore-weights", "YES"));
                else args.addAll(List.of("--weights", "../../inputs/weights"));
                if (c.get("thinning").equals("YES")) args.addAll(List.of("--extract", "../thin/result.in"));
                else if (!c.get("predictors").equals("-")) args.addAll(List.of("--extract", "../../inputs/predictors"));
                args.addAll(List.of("--max-threads", "1"));
                result.add(new Stage("kinship-" + id, args, List.of("result.grm.adjust", "result.grm.bin", "result.grm.details", "result.grm.id")));
                args = new ArrayList<>(List.of("--reml", "result", "--grm", "../kinship-" + id + "/result", "--pheno", "../../inputs/phenotype", "--constrain", c.get("constrain")));
                if (!c.get("covariates").equals("-")) args.addAll(List.of("--covar", "../../inputs/covariates"));
                controls(args, c);
                result.add(new Stage("reml-" + id, args, List.of("result.coeff", "result.cross", "result.indi.blp", "result.indi.res", "result.reml", "result.share", "result.vars")));
            }
        }
        return List.copyOf(result);
    }
    private static void controls(List<String> args, Config c) {
        args.addAll(List.of("--max-iter", c.get("max_iterations"), "--tolerance", c.get("tolerance"), "--max-threads", "1"));
    }
    static void prerequisite(Config c) throws Prerequisite {
        if (!c.get("execution").equals("allow")) throw new Prerequisite("Original execution is forbidden by request; skip dependent LDAK analysis");
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch")))
            throw new Prerequisite("Original 5.2 execution requires permitted Linux x86_64/WSL; native verification remains available on other hosts");
    }
    static void execute(Path root, Config c, Stage stage) throws IOException, InterruptedException {
        Path folder = root.resolve("stages").resolve(stage.name());
        Files.createDirectory(folder);
        Path executable = root.resolve("runtime/ldak5.2.linux");
        try { Files.setPosixFilePermissions(executable, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE)); }
        catch (IOException | UnsupportedOperationException error) { throw new Prerequisite("Original runtime cannot be made executable in this output filesystem: " + error.getMessage()); }
        var command = new ArrayList<String>(); command.add(executable.toString()); command.addAll(stage.arguments());
        Properties started = new Properties(); started.setProperty("arguments", Integer.toString(command.size()));
        for (int i = 0; i < command.size(); i++) started.setProperty("argument." + i, command.get(i));
        started.setProperty("binary_sha256", pin(executable).sha256());
        started.setProperty("environment_policy", "remove-LD_PRELOAD-LD_LIBRARY_PATH-OMP_*-MKL_*;OMP_NUM_THREADS=1;MKL_NUM_THREADS=1;LC_ALL=C");
        store(folder.resolve("PROCESS-STARTED.properties"), started);
        ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE).redirectOutput(folder.resolve("original.stdout").toFile()).redirectError(folder.resolve("original.stderr").toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("OMP_") || key.startsWith("MKL_") || Set.of("LD_PRELOAD", "LD_LIBRARY_PATH").contains(key));
        builder.environment().put("OMP_NUM_THREADS", "1"); builder.environment().put("MKL_NUM_THREADS", "1"); builder.environment().put("LC_ALL", "C");
        long began = System.nanoTime();
        Process child;
        try { child = builder.start(); }
        catch (IOException error) { throw new Prerequisite("Original execution is unavailable/forbidden; use a permitted filesystem and Linux host: " + error.getMessage()); }
        boolean timedOut = false, budgetExceeded = false, interrupted = false;
        try {
            child.getOutputStream().close();
            while (!child.waitFor(250, TimeUnit.MILLISECONDS)) {
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) > c.number("wall_ms")) { timedOut = true; break; }
                try { budget(root, c); }
                catch (IOException exhausted) { budgetExceeded = true; break; }
            }
            if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) > c.number("wall_ms")) timedOut = true;
        } catch (InterruptedException stopped) { interrupted = true; throw stopped; }
        finally {
            if (child.isAlive()) {
                OwnedProcesses.descendants(child).forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
                child.destroyForcibly();
                if (!interrupted) child.waitFor(10, TimeUnit.SECONDS);
            }
            Properties finished = new Properties();
            finished.setProperty("exit_code", child.isAlive() ? "unknown" : Integer.toString(child.exitValue()));
            finished.setProperty("timed_out", Boolean.toString(timedOut)); finished.setProperty("budget_exceeded", Boolean.toString(budgetExceeded));
            finished.setProperty("interrupted", Boolean.toString(interrupted));
            finished.setProperty("pid", Long.toString(child.pid()));
            finished.setProperty("elapsed_ms", Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began)));
            store(folder.resolve("PROCESS-FINISHED.properties"), finished);
        }
        if (timedOut || budgetExceeded) throw new IOException("Original stage " + stage.name() + " exceeded its " + (timedOut ? "wall time" : "workspace budget") + "; retain partials and do not admit a result");
        budget(root, c);
    }
    /** Stat-only monitoring; this is not an OS disk quota and can overshoot between polls. */
    private static void budget(Path root, Config c) throws IOException {
        long[] counts = {0, 0};
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
            private void admit(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || (!attributes.isDirectory() && !attributes.isRegularFile())) throw new IOException("Linked/special workspace entry: " + path);
                if (++counts[1] > c.number("max_files")) throw new IOException("Workspace entry limit");
                if (attributes.isRegularFile()) counts[0] = Math.addExact(counts[0], attributes.size());
                if (counts[0] > c.number("max_bytes")) throw new IOException("Workspace byte limit");
            }
            @Override public java.nio.file.FileVisitResult preVisitDirectory(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException { admit(path, attributes); return java.nio.file.FileVisitResult.CONTINUE; }
            @Override public java.nio.file.FileVisitResult visitFile(Path path, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException { admit(path, attributes); return java.nio.file.FileVisitResult.CONTINUE; }
            @Override public java.nio.file.FileVisitResult visitFileFailed(Path path, IOException error) throws IOException {
                if (error instanceof java.nio.file.NoSuchFileException) return java.nio.file.FileVisitResult.CONTINUE;
                throw error;
            }
        });
    }
    static void validateStage(Path root, Config c, Stage stage) throws IOException {
        Path folder = root.resolve("stages").resolve(stage.name());
        Properties started = load(folder.resolve("PROCESS-STARTED.properties")), finished = load(folder.resolve("PROCESS-FINISHED.properties"));
        if (!"0".equals(finished.getProperty("exit_code")) || !"false".equals(finished.getProperty("timed_out"))
                || !"false".equals(finished.getProperty("budget_exceeded")) || !"false".equals(finished.getProperty("interrupted")))
            throw new IOException("Original stage failed or was interrupted: " + stage.name() + "; inspect both original consoles");
        if (!BINARY_SHA.equals(started.getProperty("binary_sha256")) || !Integer.toString(stage.arguments().size() + 1).equals(started.getProperty("arguments")))
            throw new IOException("Original process identity/argument count differs: " + stage.name());
        for (int i = 0; i < stage.arguments().size(); i++) if (!stage.arguments().get(i).equals(started.getProperty("argument." + (i + 1))))
            throw new IOException("Original command differs from captured typed request: " + stage.name());
        for (String name : stage.requiredOutputs()) if (!Files.isRegularFile(folder.resolve(name))) throw new IOException("Missing original output " + stage.name() + "/" + name);
    }
}
