/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import org.jlinalg.cli.NextflowProcess.Unavailable;
import org.jlinalg.cli.NextflowProcess.Check;
import org.jlinalg.cli.NextflowProcess.Limits;
import org.jlinalg.cli.NextflowProcess.Identity;
import org.jlinalg.cli.NextflowProcess.Result;
import org.jlinalg.workflow.LinuxProcessSnapshot;

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

/** Separate owned Linux process primitive with anchored coherent proc sampling.
 * Existing metadata records, limits, containment and local cleanup remain in use.
 * FIFOs/sockets are observed as metadata only. A successful process still needs
 * the complete Nextflow task, input, output and bundle admission checks.
 */
final class NfcoreProcess {
    private NfcoreProcess() { }
    /** Package-private seam for real controlled helper-process tests. */
    static Result executeOwned(List<String> command, Map<String, String> environment, Path newWork,
            Path allowedRoot, Limits limits, Check integrity, Runnable cancellation) throws IOException {
        return execute(command,environment,newWork,newWork,allowedRoot,newWork,limits,integrity,cancellation);
    }

    /** Stable engine cwd/cache across attempts; each attempt owns new diagnostics. */
    static Result executeSession(List<String> command, Map<String,String> environment,Path newRecord,
            Path workingDirectory,Path allowedRoot,Limits limits,Check integrity,Runnable cancellation)throws IOException {
        NextflowFiles.directory(workingDirectory);
        if(!workingDirectory.startsWith(allowedRoot))throw new IOException("Nextflow session cwd escapes its owned root");
        return execute(command,environment,newRecord,workingDirectory,allowedRoot,allowedRoot,limits,integrity,cancellation);
    }

    private static Result execute(List<String> command,Map<String,String> environment,Path newWork,Path workingDirectory,
            Path allowedRoot,Path observedRoot,Limits limits,Check integrity,Runnable cancellation)throws IOException {
        Objects.requireNonNull(limits, "limits"); Objects.requireNonNull(integrity, "integrity"); Objects.requireNonNull(cancellation, "cancellation");
        if (!System.getProperty("os.name").equals("Linux")) throw new Unavailable("Owned Nextflow execution requires Linux Java");
        Path work = newWork.toAbsolutePath().normalize();
        if (!allowedRoot.isAbsolute() || !allowedRoot.normalize().equals(allowedRoot)
                || !allowedRoot.toRealPath().equals(allowedRoot) || !work.startsWith(allowedRoot))
            throw new IOException("Require the canonical owned bundle root for staged-link containment");
        if (work.getParent() == null || !work.getParent().toRealPath().equals(work.getParent())) throw new IOException("Require canonical parent and new owned process directory");
        cancellation.run(); integrity.verify(); Files.createDirectory(work);
        var invocation = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "--as=" + limits.addressSpaceBytes(),
                "--cpu=" + limits.cpuSeconds(), "--fsize=" + limits.fileBytes(), "--nofile=" + limits.openFiles(), "--"));
        invocation.addAll(List.copyOf(command));
        Path stdout = work.resolve("process.stdout"), stderr = work.resolve("process.stderr"), ledger = work.resolve("process.json");
        var record = new LinkedHashMap<String, Object>(); record.put("status", "launching"); record.put("command", invocation); record.put("limits", limits);
        record.put("working_directory",workingDirectory.toAbsolutePath().normalize().toString());
        record.put("workspace_root",observedRoot.toAbsolutePath().normalize().toString());
        record.put("scope", "Hard per-process AS/CPU/file/nofile limits; wall/workspace/process/thread and sampled aggregate RSS checks. Sampled RSS is not a hard aggregate RSS bound.");
        Process child = null; Guard guard = null; IOException failure = null; long started = System.nanoTime();
        try {
            guard = new Guard(work); // The hook exists before ProcessBuilder.start.
            var cwd=workingDirectory.toAbsolutePath().normalize();
            var builder = new ProcessBuilder(invocation).directory(cwd.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            var env = builder.environment(); env.clear(); env.putAll(environment); env.put("HOME", cwd.toString()); env.put("TMPDIR", cwd.toString());
            child = guard.launch(builder); record.put("owned_launcher_pid", child.pid()); record.put("status", "running");
            write(work.resolve("launch.json"), record); guard.observe();
            for (;;) {
                cancellation.run(); guard.requireOpen(); guard.observe(); guard.enforce(limits);
                guard.workspace = Math.max(guard.workspace, workspace(observedRoot, limits, allowedRoot));
                if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > limits.wallMillis()) throw new IOException("Nextflow wall-time capacity exceeded");
                if (child.waitFor(limits.monitorMillis(), TimeUnit.MILLISECONDS)) break;
            }
            guard.observe(); guard.enforce(limits); guard.workspace = Math.max(guard.workspace, workspace(observedRoot, limits, allowedRoot));
            record.put("exit_code", child.exitValue());
            if (child.exitValue() != 0) throw new IOException("Original Nextflow process failed, exit " + child.exitValue());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); failure = new IOException("Nextflow process interrupted; no result is admitted", interrupted);
        } catch (IOException invalid) { failure = invalid;
        } catch (RuntimeException cancelled) { failure = new IOException("Nextflow process cancelled or invalid; no result is admitted", cancelled);
        } finally {
            try {
                if (guard != null) guard.cleanup();
                if (child != null && !child.waitFor(5, TimeUnit.SECONDS)) throw new IOException("Owned Nextflow launcher remains alive after cleanup");
            } catch (IOException | InterruptedException cleanup) {
                if (cleanup instanceof InterruptedException) Thread.currentThread().interrupt();
                if (failure == null) failure = new IOException("Nextflow owned-process cleanup failed", cleanup); else failure.addSuppressed(cleanup);
            }
            try {
                // A successful exit alone never admits scientific results.
                integrity.verify(); cancellation.run();
                if (guard != null) guard.workspace = Math.max(guard.workspace, workspace(observedRoot, limits, allowedRoot));
            } catch (IOException | RuntimeException changed) {
                if (failure == null) failure = new IOException("Nextflow input/runtime/workspace integrity failed after execution", changed); else failure.addSuppressed(changed);
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
                String provisional = NextflowJson.json(record) + "\n";
                long additionalBytes = provisional.getBytes(StandardCharsets.UTF_8).length + warning(failure).getBytes(StandardCharsets.UTF_8).length;
                workspace(observedRoot, limits, allowedRoot, additionalBytes, 2);
            } catch (IOException capacity) { if (failure == null) failure = capacity; else failure.addSuppressed(capacity); }
            record.put("status", failure == null ? "completed-process-only" : "FAIL2-retained-no-result");
            if (failure != null) { record.put("error", failure.toString()); record.put("suppressed", java.util.Arrays.stream(failure.getSuppressed()).map(Throwable::toString).toList()); }
            // Failed diagnostics remain even if their small final records put a
            // failed workspace over capacity. Such a run cannot return success.
            try { write(ledger, record); }
            catch (IOException diagnostic) { if (failure == null) failure = diagnostic; else failure.addSuppressed(diagnostic); }
            try { Files.writeString(work.resolve("WARNINGS.md"), warning(failure), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
            catch (IOException diagnostic) { if (failure == null) failure = diagnostic; else failure.addSuppressed(diagnostic);
                System.err.println("NEXTFLOW FAIL2 DIAGNOSTIC WRITE FAILED: " + diagnostic + ". Required action: retain the owned process directory and inspect its logs before rerunning."); }
            if (guard != null) guard.close();
        }
        if (failure != null) throw failure;
        return new Result(work, stdout, stderr, ledger, Objects.requireNonNull(child).exitValue(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), guard.workspace, guard.rss, guard.processes, guard.threads, guard.identities(), guard.live());
    }

    private record Sample(Identity identity, long rssBytes, int threads, LinuxProcessSnapshot.Snapshot raw) { }
    private static Sample readSample(long pid) throws IOException {
        LinuxProcessSnapshot.Snapshot value;
        try { value = LinuxProcessSnapshot.read(pid); }
        catch (UnsupportedOperationException unavailable) { throw new Unavailable(unavailable.getMessage()); }
        if (value == null) return null;
        var id = value.identity();
        return new Sample(new Identity(id.pid(), id.startTicks(), id.parent(), id.group(), id.session(), id.state()), value.rssBytes(), value.threads(), value);
    }
    private static Identity identify(long pid) throws IOException {
        LinuxProcessSnapshot.Identity value;
        try { value = LinuxProcessSnapshot.readIdentity(pid); }
        catch (UnsupportedOperationException unavailable) { throw new Unavailable(unavailable.getMessage()); }
        return value == null ? null : new Identity(value.pid(), value.startTicks(), value.parent(), value.group(), value.session(), value.state());
    }
    private static boolean terminal(Identity value) throws IOException {
        if (!Set.of("Z", "X", "x").contains(value.state())) return false;
        return LinuxProcessSnapshot.terminalGroup(new LinuxProcessSnapshot.Identity(value.pid(), value.startTicks(), value.parent(), value.group(), value.session(), value.state()));
    }

    private static final class Guard implements AutoCloseable {
        private final Path work;
        private final Instant fence = Instant.now().minusSeconds(2);
        private final Map<Long, Identity> known = new HashMap<>();
        private final Set<String> retainedSamples = new HashSet<>();
        private final Thread hook;
        private final Object launchLock = new Object();
        private boolean launching, closing;
        private long workspace, rss; private int processes, threads;
        Guard(Path work) {
            this.work = work; hook = new Thread(this::shutdown, "eagle-owned-process-shutdown"); Runtime.getRuntime().addShutdownHook(hook);
        }
        private Sample sample(long pid) throws IOException {
            Identity owner = known.get(pid);
            try {
                Sample value = readSample(pid);
                if (value != null && owner != null && owner.startTicks() == value.identity.startTicks() && value.raw.exitRace())
                    retainSample(pid, owner.startTicks(), "TERMINAL_EXIT_NO_LIVE_GAUGE", "", value.raw.initialStat(), value.raw.status(), value.raw.finalStat());
                return value;
            } catch (LinuxProcessSnapshot.ReadFailure refused) {
                if (owner != null && owner.startTicks() != refused.initialIdentity().startTicks()) return null;
                if (owner != null)
                    retainSample(pid, owner.startTicks(), "REFUSED_PROCESS_GAUGE", refused.toString(), refused.initialStat(), refused.status(), refused.finalStat());
                throw refused;
            }
        }
        private void retainSample(long pid, long start, String classification, String error,
                                  String before, String status, String after) throws IOException {
            String key = classification + ":" + pid + ":" + start;
            if (retainedSamples.contains(key)) return;
            var event = Map.of("pid", pid, "verified_owned_start_ticks", start, "classification", classification,
                "error", error, "raw_encoding", "ISO-8859-1", "initial_stat", before, "status", status, "final_stat", after);
            byte[] bytes = (NextflowJson.json(event) + "\n").getBytes(StandardCharsets.UTF_8);
            Path evidence = work.resolve("owned-process-samples.jsonl");
            if ((Files.exists(evidence) ? Files.size(evidence) : 0) + bytes.length > 67108864)
                throw new IOException("Complete owned-process diagnostic evidence exceeds64MB capacity");
            Files.write(evidence, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            retainedSamples.add(key);
        }
        Process launch(ProcessBuilder builder) throws IOException {
            synchronized (launchLock) { if (closing) throw new IOException("Nextflow JVM shutdown began before launch"); launching = true; }
            try {
                Process result = builder.start();
                synchronized (this) { Identity initial = identify(result.pid()); if (initial != null) known.put(initial.pid(), initial); }
                return result;
            } finally { synchronized (launchLock) { launching = false; launchLock.notifyAll(); } }
        }
        void requireOpen() throws IOException { synchronized (launchLock) { if (closing) throw new IOException("Nextflow JVM shutdown began during execution"); } }
        synchronized void observe() throws IOException {
            var samples = new HashMap<Long, Identity>(); var active = new HashMap<Long, Identity>(); var sessions = new HashSet<Long>();
            try (var paths = Files.newDirectoryStream(Path.of("/proc"))) {
                int inspected = 0;
                for (Path path : paths) {
                    if (!path.getFileName().toString().matches("[0-9]+")) continue;
                    if (++inspected > 100000) throw new IOException("Linux process inventory exceeds supported inspection capacity");
                    long pid = Long.parseLong(path.getFileName().toString()); Identity value;
                    try { value = identify(pid); } catch (IOException unavailable) { if (known.containsKey(pid)) throw unavailable; continue; }
                    if (value == null) continue; samples.put(pid, value); Identity prior = known.get(pid);
                    if (prior != null && prior.startTicks() == value.startTicks()) {
                        active.put(pid, value);
                        if (value.session() != ProcessHandle.current().pid() && value.session() == value.group()
                                && (known.containsKey(value.session()) || value.session() == value.pid())) sessions.add(value.session());
                    }
                }
            }
            // The unique newly owned cwd covers the interval before start()
            // returns and ordinary orphan descendants after a short-lived parent.
            for (var entry : samples.entrySet()) {
                long pid = entry.getKey();
                if (known.containsKey(pid) && known.get(pid).startTicks() == entry.getValue().startTicks()) continue;
                try {
                    var handle = ProcessHandle.of(pid);
                    if (handle.isPresent() && handle.get().info().startInstant().filter(start -> !start.isBefore(fence)).isPresent()
                            && Path.of("/proc", Long.toString(pid), "cwd").toRealPath().equals(work)) active.put(pid, entry.getValue());
                } catch (IOException vanishedOrForeign) { /* Unowned/inaccessible cwd never authorizes a kill. */ }
            }
            boolean changed;
            do {
                changed = false;
                for (Identity id : samples.values()) {
                    if (active.containsKey(id.pid())) {
                        if (id.session() == id.pid() && id.group() == id.pid()) sessions.add(id.session());
                        continue;
                    }
                    Identity parent = active.get(id.parent());
                    if ((parent != null && id.startTicks() >= parent.startTicks()) || sessions.contains(id.session())) {
                        active.put(id.pid(), id); changed = true;
                    }
                }
            } while (changed);
            for (Identity identity : active.values()) known.put(identity.pid(), identity);
        }
        synchronized void enforce(Limits limits) throws IOException {
            long observedRss = 0; int observedThreads = 0, observedProcesses = 0;
            for (Identity identity : known.values()) {
                Sample current = assess(identity, limits);
                if (current == null || current.identity.startTicks() != identity.startTicks() || terminal(current.identity)) continue;
                if (!current.raw.liveMetrics()) {
                    retainSample(identity.pid(), identity.startTicks(), "LIVE_THREAD_GROUP_NO_LEADER_GAUGE", "No live gauge imputed", current.raw.initialStat(), current.raw.status(), current.raw.finalStat());
                    throw new IOException("Owned thread group has live members but no complete leader resource gauge; no metrics or result admitted");
                }
                observedRss = Math.addExact(observedRss, current.rssBytes); observedThreads = Math.addExact(observedThreads, current.threads); observedProcesses++;
            }
            rss = Math.max(rss, observedRss); threads = Math.max(threads, observedThreads); processes = Math.max(processes, observedProcesses);
            if (observedRss > limits.sampledRssBytes() || observedThreads > limits.threads() || observedProcesses > limits.processes())
                throw new IOException("Nextflow sampled RSS/thread/process capacity exceeded");
        }
        private Sample assess(Identity expected, Limits limits) throws IOException {
            long started = System.nanoTime(), deadline = started + TimeUnit.MILLISECONDS.toNanos(limits.monitorMillis());
            int observations = 0; String outcome = "UNASSESSABLE";
            try {
                while (true) {
                    observations++;
                    try {
                        Sample value = sample(expected.pid());
                        if (value == null || value.identity.startTicks() != expected.startTicks()) { outcome = "RETIRED"; return null; }
                        if (value.raw.liveMetrics()) { outcome = "COHERENT_LIVE_GAUGE"; return value; }
                        if (terminal(value.identity)) { outcome = "TERMINAL_GROUP"; return value; }
                        retainSample(expected.pid(), expected.startTicks(), "WAITING_FOR_COHERENT_GROUP_GAUGE", "No live gauge imputed", value.raw.initialStat(), value.raw.status(), value.raw.finalStat());
                        if (System.nanoTime() >= deadline) throw new IOException("Owned thread group remains unassessable after one requested polling interval; no live gauge or result admitted");
                    } catch (LinuxProcessSnapshot.ReadFailure transientRead) {
                        if (!transientRead.unstableLifecycle() || System.nanoTime() >= deadline) throw transientRead;
                    }
                    try { Thread.sleep(1); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        var error = new java.io.InterruptedIOException("Coherent process observation interrupted");
                        error.initCause(interrupted); throw error;
                    }
                }
            } finally {
                if (observations > 1) {
                    var event = Map.of("pid", expected.pid(), "verified_owned_start_ticks", expected.startTicks(),
                        "observations", observations, "elapsed_nanos", System.nanoTime() - started,
                        "poll_budget_millis", limits.monitorMillis(), "outcome", outcome, "live_gauge_imputed", false);
                    byte[] bytes = (NextflowJson.json(event) + "\n").getBytes(StandardCharsets.UTF_8);
                    Path journal = work.resolve("lifecycle-resamples.jsonl");
                    if ((Files.exists(journal) ? Files.size(journal) : 0) + bytes.length > 67108864)
                        throw new IOException("Complete bounded process observation journal exceeds64MB capacity");
                    Files.write(journal, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
        }
        synchronized List<Identity> identities() { return known.values().stream().sorted(java.util.Comparator.comparingLong(Identity::pid)).toList(); }
        synchronized List<Long> live() throws IOException {
            var live = new ArrayList<Long>();
            for (Identity identity : known.values()) {
                Identity current = identify(identity.pid());
                if (current != null && current.startTicks() == identity.startTicks() && !terminal(current)) live.add(identity.pid());
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
                        Identity current = identify(identity.pid());
                        if (current != null && current.startTicks() == identity.startTicks())
                            ProcessHandle.of(identity.pid()).ifPresent(ProcessHandle::destroyForcibly);
                    }
                    if (live().isEmpty()) return;
                    try { Thread.sleep(10); } catch (InterruptedException interrupted) { wasInterrupted = true; }
                } while (System.nanoTime() < deadline);
                throw new IOException("Owned Nextflow processes remain alive: " + live());
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
            record.put("required_action", "Interrupted workflow is incomplete. Keep this provisional directory and logs; inspect any recorded live process and rerun to a new output. Never admit interrupted results.");
            try {
                write(work.resolve("shutdown.json"), record);
                Files.writeString(work.resolve("SHUTDOWN_WARNINGS.md"), "# Nextflow FAIL2 interrupted workflow\n\n"
                        + record.get("status") + "\n\nRequired action: keep the provisional directory, shutdown ledger and logs. Inspect any recorded nonterminal owned process before rerunning to a NEW output. Never admit interrupted results.\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            catch (IOException failure) { System.err.println("NEXTFLOW SHUTDOWN CLEANUP DIAGNOSTIC FAILED: " + failure + ". Required action: inspect retained process state before rerunning."); }
        }
        @Override public void close() { try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException shuttingDown) { /* The hook owns final cleanup. */ } }
    }

    private static long workspace(Path root, Limits limits, Path allowedRoot) throws IOException {
        return workspace(root, limits, allowedRoot, 0, 0);
    }
    static void checkCapacity(Path root,Limits limits,long additionalBytes,int additionalFiles)throws IOException {
        workspace(root,limits,root,additionalBytes,additionalFiles);
    }
    private static long workspace(Path root, Limits limits, Path allowedRoot, long additionalBytes, int additionalFiles) throws IOException {
        long[] bytes = {0}; int[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (++count[0] > limits.fileCount() || Files.isSymbolicLink(path)) throw new IOException("Owned Nextflow workspace file-count/link capacity exceeded"); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                if (++count[0] > limits.fileCount()) throw new IOException("Owned Nextflow file capacity exceeded");
                if (attrs.isSymbolicLink()) {
                    try {
                        Path target = Files.readSymbolicLink(path);
                        if (!path.getParent().resolve(target).normalize().startsWith(allowedRoot))
                            throw new IOException("Nextflow staged link escapes its captured bundle: " + path);
                        try {
                            if (!path.toRealPath().startsWith(allowedRoot))
                                throw new IOException("Nextflow staged link resolves outside its captured bundle: " + path);
                        } catch (NoSuchFileException notYetPresent) { /* A staged target may still be produced. */ }
                        bytes[0] = Math.addExact(bytes[0], target.toString().getBytes(StandardCharsets.UTF_8).length);
                    } catch (NoSuchFileException disappeared) { return FileVisitResult.CONTINUE; }
                } else if (attrs.isRegularFile()) {
                    bytes[0] = Math.addExact(bytes[0], attrs.size());
                } else {
                    // Real workflows may use transient FIFOs or local sockets.
                    // Never open/follow them during capacity observation. Their
                    // presence does not admit them as captured result files.
                    try {
                        int kind = ((Number)Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue() & 0170000;
                        if (kind != 0010000 && kind != 0140000) throw new IOException("Unexpected Nextflow workspace special file: " + path);
                    } catch (NoSuchFileException disappeared) { return FileVisitResult.CONTINUE; }
                }
                if (bytes[0] > limits.workspaceBytes()) throw new IOException("Owned Nextflow workspace byte capacity exceeded"); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException) return FileVisitResult.CONTINUE; throw failure;
            }
        });
        if (Math.addExact(bytes[0], additionalBytes) > limits.workspaceBytes() || Math.addExact(count[0], additionalFiles) > limits.fileCount())
            throw new IOException("Owned Nextflow workspace capacity including final diagnostics exceeded");
        return bytes[0];
    }
    private static String warning(IOException failure) {
        return "# Nextflow process " + (failure == null ? "requires complete scientific output audit" : "FAIL2 failed")
                + "\n\n" + (failure == null ? "Process execution completed. Its exit code does not admit a scientific result; complete the prepared input/output audit." : failure.toString())
                + "\n\nRequired action: preserve this directory, stdout, stderr and process ledger. Admit results only after the complete scientific output audit. Correct any failure and rerun to a new output directory.\n";
    }
    private static String bounded(Path path, int maximum) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(maximum + 1); if (data.length > maximum) throw new IOException("Oversized Linux process record"); return new String(data, StandardCharsets.US_ASCII);
        }
    }
    private static void write(Path path, Object value) throws IOException {
        Files.writeString(path, NextflowJson.json(value) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
