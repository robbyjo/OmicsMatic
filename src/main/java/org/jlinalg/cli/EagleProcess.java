/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Owned Linux process primitive for the typed Eagle pipeline; no arbitrary-command CLI. */
final class EagleProcess {
    private EagleProcess() { }
    @FunctionalInterface interface Check { void verify() throws IOException; }
    record Limits(long wallMillis, long addressSpaceBytes, long cpuSeconds, long fileBytes,
            int openFiles, long workspaceBytes, int fileCount, int processes, int threads,
            long sampledRssBytes, int monitorMillis) {
        Limits {
            if (wallMillis < 1 || wallMillis > 604800000L || addressSpaceBytes < 16777216 || addressSpaceBytes > 1099511627776L
                    || cpuSeconds < 1 || cpuSeconds > 604800 || fileBytes < 1 || fileBytes > 1000000000000L
                    || openFiles < 8 || openFiles > 1048576 || workspaceBytes < 1 || workspaceBytes > 1000000000000L
                    || fileCount < 4 || fileCount > 1000000 || processes < 1 || processes > 4096
                    || threads < 1 || threads > 1048576 || sampledRssBytes < 1 || sampledRssBytes > 1099511627776L
                    || monitorMillis < 1 || monitorMillis > 1000)
                throw new IllegalArgumentException("Invalid explicit Eagle process capacities");
        }
        static Limits health() { return new Limits(60000, 1073741824, 60, 16777216, 256, 134217728, 256, 8, 64, 536870912, 25); }
    }
    record Identity(long pid, long startTicks, long parent, long group, long session, String state) { }
    record Result(Path work, Path stdout, Path stderr, Path ledger, int exitCode, long elapsedMillis,
            long peakWorkspaceBytes, long peakSampledRssBytes, int peakProcesses, int peakThreads,
            List<Identity> ownedProcesses, List<Long> ownedLiveAfterCleanup) {
        Result { ownedProcesses = List.copyOf(ownedProcesses); ownedLiveAfterCleanup = List.copyOf(ownedLiveAfterCleanup); }
    }

    static Result run(EagleRuntime.Context runtime, List<String> arguments, Path newWork, Limits limits,
            Check inputs, Runnable cancellation) throws IOException {
        Objects.requireNonNull(runtime, "runtime"); Objects.requireNonNull(arguments, "arguments");
        var command = new ArrayList<String>(); command.add(runtime.home().resolve("bin/eagle").toString());
        for (String argument : arguments) {
            if (argument == null || !argument.startsWith("--") || argument.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Eagle internal invocation requires complete long-option arguments");
            command.add(argument);
        }
        Check integrity = () -> { runtime.verify(); inputs.verify(); };
        var environment = new HashMap<String, String>();
        environment.put("PATH", "/usr/bin:/bin"); environment.put("LANG", "C"); environment.put("LC_ALL", "C");
        environment.put("OMP_NUM_THREADS", "1"); environment.put("OPENBLAS_NUM_THREADS", "1"); environment.put("MKL_NUM_THREADS", "1");
        environment.put("LD_LIBRARY_PATH", runtime.home().resolve("lib").toString());
        return executeOwned(command, environment, newWork, limits, integrity, cancellation);
    }

    /** Package-private seam for real controlled helper-process tests. */
    static Result executeOwned(List<String> command, Map<String, String> environment, Path newWork,
            Limits limits, Check integrity, Runnable cancellation) throws IOException {
        Objects.requireNonNull(limits, "limits"); Objects.requireNonNull(integrity, "integrity"); Objects.requireNonNull(cancellation, "cancellation");
        if (!System.getProperty("os.name").equals("Linux")) throw new EagleRuntime.Unavailable("Owned Eagle execution requires Linux Java");
        Path work = newWork.toAbsolutePath().normalize();
        if (work.getParent() == null || !work.getParent().toRealPath().equals(work.getParent())) throw new IOException("Require canonical parent and new owned process directory");
        cancellation.run(); integrity.verify(); Files.createDirectory(work);
        var invocation = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "--as=" + limits.addressSpaceBytes,
                "--cpu=" + limits.cpuSeconds, "--fsize=" + limits.fileBytes, "--nofile=" + limits.openFiles, "--"));
        invocation.addAll(List.copyOf(command));
        Path stdout = work.resolve("process.stdout"), stderr = work.resolve("process.stderr"), ledger = work.resolve("process.json");
        var record = new LinkedHashMap<String, Object>(); record.put("status", "launching"); record.put("command", invocation); record.put("limits", limits);
        record.put("scope", "Hard per-process AS/CPU/file/nofile limits; wall/workspace/process/thread and sampled aggregate RSS checks. Sampled RSS is not a hard aggregate RSS bound.");
        Process child = null; Guard guard = null; IOException failure = null; long started = System.nanoTime();
        try {
            guard = new Guard(work); // The hook exists before ProcessBuilder.start.
            var builder = new ProcessBuilder(invocation).directory(work.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            var env = builder.environment(); env.clear(); env.putAll(environment); env.put("HOME", work.toString()); env.put("TMPDIR", work.toString());
            child = guard.launch(builder); record.put("owned_launcher_pid", child.pid()); record.put("status", "running");
            write(work.resolve("launch.json"), record); guard.observe();
            for (;;) {
                cancellation.run(); guard.requireOpen(); guard.observe(); guard.enforce(limits);
                guard.workspace = Math.max(guard.workspace, workspace(work, limits));
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > limits.wallMillis) throw new IOException("Eagle wall-time capacity exceeded");
                if (child.waitFor(limits.monitorMillis, TimeUnit.MILLISECONDS)) break;
            }
            guard.observe(); guard.enforce(limits); guard.workspace = Math.max(guard.workspace, workspace(work, limits));
            record.put("exit_code", child.exitValue());
            if (child.exitValue() != 0) throw new IOException("Original Eagle process failed, exit " + child.exitValue());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); failure = new IOException("Eagle process interrupted; no result is admitted", interrupted);
        } catch (IOException invalid) { failure = invalid;
        } catch (RuntimeException cancelled) { failure = new IOException("Eagle process cancelled or invalid; no result is admitted", cancelled);
        } finally {
            try {
                if (guard != null) guard.cleanup();
                if (child != null && !child.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Owned Eagle launcher remains alive after cleanup");
            } catch (IOException | InterruptedException cleanup) {
                if (cleanup instanceof InterruptedException) Thread.currentThread().interrupt();
                if (failure == null) failure = new IOException("Eagle owned-process cleanup failed", cleanup); else failure.addSuppressed(cleanup);
            }
            try {
                // A successful exit alone never admits scientific results.
                integrity.verify(); cancellation.run();
                if (guard != null) guard.workspace = Math.max(guard.workspace, workspace(work, limits));
            } catch (IOException | RuntimeException changed) {
                if (failure == null) failure = new IOException("Eagle input/runtime/workspace integrity failed after execution", changed); else failure.addSuppressed(changed);
            }
            record.put("status", failure == null ? "completed-process-only" : "FAIL2-retained-no-result");
            if (failure != null) record.put("error", failure.toString());
            record.put("elapsed_millis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (guard != null) {
                record.put("owned_processes", guard.identities());
                try { record.put("owned_live_after_cleanup", guard.live()); }
                catch (IOException unknown) { if (failure == null) failure = unknown; else failure.addSuppressed(unknown); record.put("cleanup_state", "unknown-requires-review"); }
                record.put("peak_workspace_bytes", guard.workspace); record.put("peak_sampled_rss_bytes", guard.rss); record.put("peak_processes", guard.processes); record.put("peak_threads", guard.threads);
            }
            try {
                String provisional = EagleJson.json(record) + "\n";
                long additionalBytes = provisional.getBytes(StandardCharsets.UTF_8).length + warning(failure).getBytes(StandardCharsets.UTF_8).length;
                workspace(work, limits, additionalBytes, 2);
            } catch (IOException capacity) { if (failure == null) failure = capacity; else failure.addSuppressed(capacity); }
            record.put("status", failure == null ? "completed-process-only" : "FAIL2-retained-no-result");
            if (failure != null) { record.put("error", failure.toString()); record.put("suppressed", java.util.Arrays.stream(failure.getSuppressed()).map(Throwable::toString).toList()); }
            // Failed diagnostics remain even if their small final records put a
            // failed workspace over capacity. Such a run cannot return success.
            try { write(ledger, record); }
            catch (IOException diagnostic) { if (failure == null) failure = diagnostic; else failure.addSuppressed(diagnostic); }
            try { Files.writeString(work.resolve("WARNINGS.md"), warning(failure), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
            catch (IOException diagnostic) { if (failure == null) failure = diagnostic; else failure.addSuppressed(diagnostic);
                System.err.println("EAGLE FAIL2 DIAGNOSTIC WRITE FAILED: " + diagnostic + ". Required action: retain the owned process directory and inspect its logs before rerunning."); }
            if (guard != null) guard.close();
        }
        if (failure != null) throw failure;
        return new Result(work, stdout, stderr, ledger, Objects.requireNonNull(child).exitValue(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), guard.workspace, guard.rss, guard.processes, guard.threads, guard.identities(), guard.live());
    }

    private record Sample(Identity identity, long rssBytes, int threads) { }
    private static Sample sample(long pid) throws IOException {
        Path root = Path.of("/proc", Long.toString(pid));
        try {
            String stat = bounded(root.resolve("stat"), 65536); int end = stat.lastIndexOf(')');
            if (end < 1) throw new IOException("Malformed owned process identity"); String[] f = stat.substring(end + 2).trim().split(" +");
            if (f.length < 20) throw new IOException("Incomplete owned process identity");
            var identity = new Identity(pid, Long.parseLong(f[19]), Long.parseLong(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]), f[0]);
            long rss = 0; int threads = 0;
            for (String line : bounded(root.resolve("status"), 65536).split("\n")) {
                if (line.startsWith("VmRSS:")) { String[] fields = line.trim().split("\\s+"); if (fields.length != 3 || !fields[2].equals("kB")) throw new IOException("Unknown Linux RSS units"); rss = Math.multiplyExact(Long.parseLong(fields[1]), 1024); }
                if (line.startsWith("Threads:")) threads = Integer.parseInt(line.substring(8).trim());
            }
            if (threads < 1 || rss < 0) throw new IOException("Invalid process thread/RSS metrics");
            return new Sample(identity, rss, threads);
        } catch (NoSuchFileException vanished) { return null; }
        catch (NumberFormatException | ArithmeticException malformed) { throw new IOException("Invalid process identity/metrics", malformed); }
    }
    private static boolean terminal(Identity value) { return value.state.equals("Z") || value.state.equals("X"); }

    private static final class Guard implements AutoCloseable {
        private final Path work;
        private final Instant fence = Instant.now().minusSeconds(2);
        private final Map<Long, Identity> known = new HashMap<>();
        private final Thread hook;
        private final Object launchLock = new Object();
        private boolean launching, closing;
        private long workspace, rss; private int processes, threads;
        Guard(Path work) {
            this.work = work; hook = new Thread(this::shutdown, "eagle-owned-process-shutdown"); Runtime.getRuntime().addShutdownHook(hook);
        }
        Process launch(ProcessBuilder builder) throws IOException {
            synchronized (launchLock) { if (closing) throw new IOException("Eagle JVM shutdown began before launch"); launching = true; }
            try {
                Process result = builder.start();
                synchronized (this) { Sample initial = sample(result.pid()); if (initial != null) known.put(initial.identity.pid, initial.identity); }
                return result;
            } finally { synchronized (launchLock) { launching = false; launchLock.notifyAll(); } }
        }
        void requireOpen() throws IOException { synchronized (launchLock) { if (closing) throw new IOException("Eagle JVM shutdown began during execution"); } }
        synchronized void observe() throws IOException {
            var samples = new HashMap<Long, Sample>(); var active = new HashMap<Long, Identity>(); var sessions = new HashSet<Long>();
            try (var paths = Files.newDirectoryStream(Path.of("/proc"))) {
                int inspected = 0;
                for (Path path : paths) {
                    if (!path.getFileName().toString().matches("[0-9]+")) continue;
                    if (++inspected > 100000) throw new IOException("Linux process inventory exceeds supported inspection capacity");
                    long pid = Long.parseLong(path.getFileName().toString()); Sample value;
                    try { value = sample(pid); } catch (IOException unavailable) { if (known.containsKey(pid)) throw unavailable; continue; }
                    if (value == null) continue; samples.put(pid, value); Identity prior = known.get(pid);
                    if (prior != null && prior.startTicks == value.identity.startTicks) {
                        active.put(pid, value.identity);
                        if (value.identity.session != ProcessHandle.current().pid() && value.identity.session == value.identity.group
                                && (known.containsKey(value.identity.session) || value.identity.session == value.identity.pid)) sessions.add(value.identity.session);
                    }
                }
            }
            // The unique newly owned cwd covers the interval before start()
            // returns and ordinary orphan descendants after a short-lived parent.
            for (var entry : samples.entrySet()) {
                long pid = entry.getKey();
                if (known.containsKey(pid) && known.get(pid).startTicks == entry.getValue().identity.startTicks) continue;
                try {
                    var handle = ProcessHandle.of(pid);
                    if (handle.isPresent() && handle.get().info().startInstant().filter(start -> !start.isBefore(fence)).isPresent()
                            && Path.of("/proc", Long.toString(pid), "cwd").toRealPath().equals(work)) active.put(pid, entry.getValue().identity);
                } catch (IOException vanishedOrForeign) { /* Unowned/inaccessible cwd never authorizes a kill. */ }
            }
            boolean changed;
            do {
                changed = false;
                for (Sample value : samples.values()) {
                    Identity id = value.identity;
                    if (active.containsKey(id.pid)) {
                        if (id.session == id.pid && id.group == id.pid) sessions.add(id.session);
                        continue;
                    }
                    Identity parent = active.get(id.parent);
                    if ((parent != null && id.startTicks >= parent.startTicks) || sessions.contains(id.session)) {
                        active.put(id.pid, id); changed = true;
                    }
                }
            } while (changed);
            for (Identity identity : active.values()) known.put(identity.pid, identity);
        }
        synchronized void enforce(Limits limits) throws IOException {
            long observedRss = 0; int observedThreads = 0, observedProcesses = 0;
            for (Identity identity : known.values()) {
                Sample current = sample(identity.pid);
                if (current == null || current.identity.startTicks != identity.startTicks || terminal(current.identity)) continue;
                observedRss = Math.addExact(observedRss, current.rssBytes); observedThreads = Math.addExact(observedThreads, current.threads); observedProcesses++;
            }
            rss = Math.max(rss, observedRss); threads = Math.max(threads, observedThreads); processes = Math.max(processes, observedProcesses);
            if (observedRss > limits.sampledRssBytes || observedThreads > limits.threads || observedProcesses > limits.processes)
                throw new IOException("Eagle sampled RSS/thread/process capacity exceeded");
        }
        synchronized List<Identity> identities() { return known.values().stream().sorted(java.util.Comparator.comparingLong(Identity::pid)).toList(); }
        synchronized List<Long> live() throws IOException {
            var live = new ArrayList<Long>();
            for (Identity identity : known.values()) {
                Sample current = sample(identity.pid);
                if (current != null && current.identity.startTicks == identity.startTicks && !terminal(current.identity)) live.add(identity.pid);
            }
            return List.copyOf(live);
        }
        synchronized void cleanup() throws IOException {
            boolean wasInterrupted = Thread.interrupted();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                do {
                    observe();
                    for (Identity identity : known.values()) {
                        Sample current = sample(identity.pid);
                        if (current != null && current.identity.startTicks == identity.startTicks && !terminal(current.identity))
                            ProcessHandle.of(identity.pid).ifPresent(ProcessHandle::destroyForcibly);
                    }
                    if (live().isEmpty()) return;
                    try { Thread.sleep(10); } catch (InterruptedException interrupted) { wasInterrupted = true; }
                } while (System.nanoTime() < deadline);
                throw new IOException("Owned Eagle processes remain alive: " + live());
            } finally { if (wasInterrupted) Thread.currentThread().interrupt(); }
        }
        private void shutdown() {
            var record = new LinkedHashMap<String, Object>(); record.put("status", "shutdown-cleanup-started");
            try {
                synchronized (launchLock) {
                    closing = true; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (launching && System.nanoTime() < deadline) launchLock.wait(20);
                    if (launching) throw new IOException("Owned launch did not return during shutdown; inspect retained process state before rerunning");
                }
                cleanup(); record.put("status", "shutdown-owned-processes-terminal"); record.put("owned_processes", identities()); record.put("owned_live_after_cleanup", live());
            } catch (IOException | InterruptedException failure) { record.put("status", "shutdown-cleanup-failed"); record.put("error", failure.toString()); }
            record.put("required_action", "Interrupted phasing is incomplete. Keep this provisional directory and logs; inspect any recorded live process and rerun to a new output. Never admit interrupted results.");
            try {
                write(work.resolve("shutdown.json"), record);
                Files.writeString(work.resolve("SHUTDOWN_WARNINGS.md"), "# Eagle FAIL2 interrupted phasing\n\n"
                        + record.get("status") + "\n\nRequired action: keep the provisional directory, shutdown ledger and logs. Inspect any recorded nonterminal owned process before rerunning to a NEW output. Never admit interrupted results.\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            catch (IOException failure) { System.err.println("EAGLE SHUTDOWN CLEANUP DIAGNOSTIC FAILED: " + failure + ". Required action: inspect retained process state before rerunning."); }
        }
        @Override public void close() { try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException shuttingDown) { /* The hook owns final cleanup. */ } }
    }

    private static long workspace(Path root, Limits limits) throws IOException {
        return workspace(root, limits, 0, 0);
    }
    private static long workspace(Path root, Limits limits, long additionalBytes, int additionalFiles) throws IOException {
        long[] bytes = {0}; int[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (++count[0] > limits.fileCount || Files.isSymbolicLink(path)) throw new IOException("Owned Eagle workspace file-count/link capacity exceeded"); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || ++count[0] > limits.fileCount) throw new IOException("Owned Eagle output is linked/nonregular or exceeds file capacity");
                bytes[0] = Math.addExact(bytes[0], attrs.size());
                if (bytes[0] > limits.workspaceBytes) throw new IOException("Owned Eagle workspace byte capacity exceeded"); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException) return FileVisitResult.CONTINUE; throw failure;
            }
        });
        if (Math.addExact(bytes[0], additionalBytes) > limits.workspaceBytes || Math.addExact(count[0], additionalFiles) > limits.fileCount)
            throw new IOException("Owned Eagle workspace capacity including final diagnostics exceeded");
        return bytes[0];
    }
    private static String warning(IOException failure) {
        return "# Eagle process " + (failure == null ? "requires complete scientific output audit" : "FAIL2 failed")
                + "\n\n" + (failure == null ? "Process execution completed. Its exit code does not admit a scientific result; complete the prepared input/output audit." : failure.toString())
                + "\n\nRequired action: preserve this directory, stdout, stderr and process ledger. Admit results only after the complete scientific output audit. Correct any failure and rerun to a new output directory.\n";
    }
    private static String bounded(Path path, int maximum) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(maximum + 1); if (data.length > maximum) throw new IOException("Oversized Linux process record"); return new String(data, StandardCharsets.US_ASCII);
        }
    }
    private static void write(Path path, Object value) throws IOException {
        Files.writeString(path, EagleJson.json(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
