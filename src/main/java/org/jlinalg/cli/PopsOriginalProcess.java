/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Owned JVM execution with an explicit heap and monitored work/time limits. */
final class PopsOriginalProcess {
    private record Child(ProcessHandle handle, Instant start) { }
    private static boolean live(Child c) { return c.handle.isAlive() && c.handle.info().startInstant().map(c.start::equals).orElse(false); }
    private static final class Owner {
        private final Process process;
        private final Map<Long, Child> descendants = new LinkedHashMap<>();
        Owner(Process process) { this.process = process; }
        synchronized void observe() {
            List<ProcessHandle> parents = new ArrayList<>(); parents.add(process.toHandle());
            for (Child child : descendants.values()) if (live(child)) parents.add(child.handle);
            for (ProcessHandle parent : parents) for (ProcessHandle child : parent.descendants().toList())
                child.info().startInstant().ifPresent(start -> descendants.putIfAbsent(child.pid(), new Child(child, start)));
        }
        synchronized void stop() {
            observe(); boolean interrupted = Thread.interrupted(); long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            do {
                for (Child child : descendants.values()) if (live(child)) child.handle.destroyForcibly();
                if (process.isAlive()) process.destroyForcibly();
                if (!process.isAlive() && descendants.values().stream().noneMatch(PopsOriginalProcess::live)) break;
                try { Thread.sleep(10); } catch (InterruptedException e) { interrupted = true; }
            } while (System.nanoTime() < end);
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
    private PopsOriginalProcess() { }
    static void capacity(Path root, PopsOriginalFiles.Config c) throws IOException {
        long bytes = 0, count = 0;
        try (var paths = Files.walk(root)) {
            var it = paths.iterator();
            while (it.hasNext()) {
                Path path = it.next();
                if (++count > c.i("max_files")) throw new IOException("Work entry count exceeds max_files; increase permitted capacity and retry NEW output");
                if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path.toAbsolutePath().normalize())) throw new IOException("Work aliases are not qualified: " + path);
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    bytes = Math.addExact(bytes, Files.size(path));
                    if (bytes > c.n("max_work_bytes")) throw new IOException("Work bytes exceed max_work_bytes; no accepted analysis. Increase permitted capacity and retry NEW output without dropping genes/features.");
                } else if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unqualified work entry: " + path);
            }
        }
    }
    static void execute(Path root, PopsOriginalFiles.Config c) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path jvm = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        if (!Files.isExecutable(jvm)) throw new Prerequisite("Worker JVM unavailable: " + jvm + ". Supply a permitted Java 17+ installation or skip this analysis.");
        var classpath = new LinkedHashSet<String>();
        for (Class<?> type : List.of(PopsOriginalWorker.class, org.jlinalg.genetics.PopsChunked.class, jdistlib.Normal.class))
            classpath.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        var resource = PopsOriginalProcess.class.getResource(PopsOriginalCode.AUDIT);
        if (resource != null && resource.getProtocol().equals("file")) {
            Path base = Path.of(resource.toURI()); for (int i = 0; i < Path.of(PopsOriginalCode.AUDIT.substring(1)).getNameCount(); i++) base = base.getParent();
            classpath.add(base.toString());
        }
        for (String value : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1))
            classpath.add(Path.of(value.isEmpty() ? "." : value).toAbsolutePath().normalize().toString());
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").equals("Linux") && Files.isExecutable(Path.of("/usr/bin/setpriv"))) command.addAll(List.of("/usr/bin/setpriv", "--pdeathsig", "KILL", "--"));
        command.addAll(List.of(jvm.toString(), "-Xmx" + c.get("java_heap_mb") + "m", "-cp", String.join(java.io.File.pathSeparator, classpath), PopsOriginalWorker.class.getName(), root.toString()));
        write(root.resolve("worker-command.txt"), String.join("\n", command) + "\n");
        Process child;
        try { child = new ProcessBuilder(command).directory(root.toFile()).redirectOutput(root.resolve("worker.stdout").toFile()).redirectError(root.resolve("worker.stderr").toFile()).start(); }
        catch (IOException | SecurityException e) { throw new Prerequisite("Worker execution is unavailable or forbidden: " + e + ". Supply a permitted complete Java installation or skip this analysis; no fallback analysis was run."); }
        long start = System.nanoTime(); Owner owner = new Owner(child);
        Thread shutdown = new Thread(owner::stop, "pops-owned-worker-shutdown"); boolean hooked = false, success = false;
        try {
            Runtime.getRuntime().addShutdownHook(shutdown); hooked = true;
            properties(root.resolve("worker-identity.properties"), Map.of("pid", Long.toString(child.pid()), "start", child.info().startInstant().map(Instant::toString).orElse("unavailable"), "heap_mb", c.get("java_heap_mb"), "wall_ms", c.get("wall_ms")));
            while (!child.waitFor(100, TimeUnit.MILLISECONDS)) {
                owner.observe(); capacity(root, c); time(start, c);
            }
            owner.observe(); capacity(root, c); time(start, c);
            if (child.exitValue() == 3) throw new Prerequisite("Worker prerequisites unavailable; read worker.stderr. Restore the named permitted prerequisite and retry NEW output or skip this analysis.");
            if (child.exitValue() != 0) throw new IOException("PoPS worker failed with exit " + child.exitValue() + ". " + diagnostic(root)
                + " Read worker.stdout, worker.stderr and WORKER_FAILURE.txt when present. For JVM heap errors adjust java_heap_mb to a permitted adequate value; otherwise correct the named cause. Retain this bundle and retry NEW output. No result is accepted.");
            success = true;
        } finally {
            if (!success) owner.stop();
            try {
                properties(root.resolve("worker-process.properties"), Map.of("exit", child.isAlive() ? "still-alive-unadmitted" : Integer.toString(child.exitValue()), "elapsed_ms", Long.toString(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)), "successful", Boolean.toString(success)));
            } finally { if (hooked) try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch (IllegalStateException ignored) { } }
        }
    }
    private static void time(long start, PopsOriginalFiles.Config c) throws IOException {
        if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > c.n("wall_ms")) throw new IOException("PoPS worker exceeded wall_ms; its provisional results are unadmitted. Increase permitted capacity and retry NEW output.");
    }
    private static String diagnostic(Path root) throws IOException {
        for (String name : List.of("WORKER_FAILURE.txt", "worker.stderr", "worker.stdout")) {
            Path path = root.resolve(name); if (!Files.isRegularFile(path)) continue;
            String text; try (var in = Files.newInputStream(path)) { text = new String(in.readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8).strip(); }
            if (!text.isEmpty()) return "Cause from " + name + ": " + text + "\n";
        }
        return "The worker supplied no nonempty diagnostic excerpt. Inspect its recorded command and exit. ";
    }
}
