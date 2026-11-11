/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Coherent bounded /proc identity and metrics; no filtering, installation or process signalling. */
public final class LinuxProcessSnapshot {
    private LinuxProcessSnapshot() { }
    private static final int MAX_BYTES = 65536;

    /** Stable identity and final observed lifecycle state. */
    public record Identity(long pid, long startTicks, long parent, long group, long session, String state) { }
    /** Exit races have no live gauge: zero is a placeholder, never an imputed live count.
     * Raw strings use ISO-8859-1 so every original proc byte remains reversible, including task names.
     */
    public record Snapshot(Identity identity, long rssBytes, int threads, boolean liveMetrics,
                           boolean exitRace, String initialStat, String status, String finalStat) { }

    /** Complete raw evidence for a refused live reading; the caller can retain it in its owned audit. */
    public static final class ReadFailure extends IOException {
        private static final long serialVersionUID = 1L;
        private final long pid;
        private final String initialStat, status, finalStat;
        private ReadFailure(long pid, String initialStat, String status, String finalStat, IOException cause) {
            super("Process " + pid + " metadata refused: " + cause.getMessage(), cause);
            this.pid = pid; this.initialStat = initialStat; this.status = status; this.finalStat = finalStat;
        }
        public long pid() { return pid; }
        public String initialStat() { return initialStat; }
        public String status() { return status; }
        public String finalStat() { return finalStat; }
        public Identity initialIdentity() throws IOException { return identity(pid, initialStat); }
        public boolean unstableLifecycle() { return getCause() instanceof UnstableReading; }
    }
    private static final class UnstableReading extends IOException {
        private static final long serialVersionUID = 1L;
        private UnstableReading(String message) { super(message); }
    }

    /** Read through an anchored proc directory to prevent a reused PID redirecting subsequent opens. */
    public static Snapshot read(long pid) throws IOException {
        if (pid < 1) throw new IllegalArgumentException("Require a positive process ID");
        if (!System.getProperty("os.name").equals("Linux"))
            throw new UnsupportedOperationException("Live process snapshots require Linux /proc");
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(Path.of("/proc", Long.toString(pid)))) {
            if (!(directory instanceof SecureDirectoryStream<Path> secure))
                throw new UnsupportedOperationException("This filesystem cannot anchor process metadata opens");
            String before = read(secure, "stat");
            String status = read(secure, "status");
            String after = read(secure, "stat");
            try { return reconcile(pid, before, status, after); }
            catch (IOException refused) { throw new ReadFailure(pid, before, status, after, refused); }
        } catch (NoSuchFileException gone) {
            return null;
        } catch (FileSystemException error) {
            // An anchored descriptor cannot refer to a replacement process. Other IO errors stay errors.
            if ("No such process".equals(error.getReason())) return null;
            throw error;
        } catch (IOException error) {
            // FileChannel.read can report ESRCH as a plain IOException rather than a path exception.
            // These operations used one anchored proc directory, so retirement cannot target a reused PID.
            if (error.getClass() == IOException.class && "No such process".equals(error.getMessage())) return null;
            throw error;
        }
    }

    /** Identity-only discovery/cleanup remains usable when a live resource gauge cannot be assessed. */
    public static Identity readIdentity(long pid) throws IOException {
        if (pid < 1) throw new IllegalArgumentException("Require a positive process ID");
        if (!System.getProperty("os.name").equals("Linux")) throw new UnsupportedOperationException("Live process identity reads require Linux /proc");
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(Path.of("/proc", Long.toString(pid)))) {
            if (!(directory instanceof SecureDirectoryStream<Path> secure))
                throw new UnsupportedOperationException("This filesystem cannot anchor process identity opens");
            return identity(pid, read(secure, "stat"));
        } catch (IOException error) {
            if (retired(error)) return null;
            throw error;
        }
    }

    /** A zombie leader with other group threads is still conservatively live. No RSS value is inferred. */
    public static boolean terminalGroup(Identity expected) throws IOException {
        if (!System.getProperty("os.name").equals("Linux")) throw new UnsupportedOperationException("Live process lifecycle reads require Linux /proc");
        long pid = expected.pid();
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(Path.of("/proc", Long.toString(pid)))) {
            if (!(directory instanceof SecureDirectoryStream<Path> secure))
                throw new UnsupportedOperationException("This filesystem cannot anchor process lifecycle opens");
            Identity current = identity(pid, read(secure, "stat"));
            if (current.startTicks() != expected.startTicks()) return true;
            if (!terminal(current.state())) return false;
            Map<String, String> status = fields(read(secure, "status"));
            Identity after = identity(pid, read(secure, "stat"));
            if (after.startTicks() != expected.startTicks()) return true;
            if (!terminal(after.state())) return false;
            return nonnegative(status.get("Threads"), "terminal group thread count") <= 1;
        } catch (IOException error) {
            if (retired(error)) return true;
            throw error;
        }
    }

    private static boolean retired(IOException error) {
        return error instanceof NoSuchFileException
            || error instanceof FileSystemException path && "No such process".equals(path.getReason())
            || error.getClass() == IOException.class && "No such process".equals(error.getMessage());
    }

    private static String read(SecureDirectoryStream<Path> directory, String name) throws IOException {
        var options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = directory.newByteChannel(Path.of(name), options)) {
            ByteBuffer bytes = ByteBuffer.allocate(MAX_BYTES + 1);
            while (bytes.hasRemaining() && channel.read(bytes) != -1) { }
            if (bytes.position() > MAX_BYTES) throw new IOException("Process metadata exceeds complete byte capacity");
            return new String(bytes.array(), 0, bytes.position(), StandardCharsets.ISO_8859_1);
        }
    }

    /** Reconcile complete raw readings; portable seam for lifecycle, malformed-data and PID-reuse controls. */
    public static Snapshot reconcile(long pid, String initialStat, String status, String finalStat) throws IOException {
        Identity before = identity(pid, initialStat), after = identity(pid, finalStat);
        if (before.startTicks() != after.startTicks())
            throw new IOException("Process identity changed between anchored stat readings; no metrics admitted");
        Map<String, String> fields = fields(status);
        String statusState = fields.get("State");
        boolean terminal = terminal(after.state());
        if (terminal(before.state()) && !terminal)
            throw new UnstableReading("Process lifecycle changed within metadata reads; a coherent observation is required");
        if (fields.containsKey("Pid") && nonnegative(fields.get("Pid"), "Pid") != pid)
            throw new IOException("Process status PID differs from its anchored stat identity");
        if (fields.containsKey("Tgid") && nonnegative(fields.get("Tgid"), "Tgid") != pid)
            throw new IOException("Require a process leader /proc entry, not a thread alias");
        long rss = 0;
        if (fields.containsKey("VmRSS")) {
            String[] parts = fields.get("VmRSS").split("\\s+", -1);
            if (parts.length != 2 || !parts[1].equals("kB")) throw new IOException("Unknown Linux VmRSS units");
            try { rss = Math.multiplyExact(nonnegative(parts[0], "VmRSS"), 1024); }
            catch (ArithmeticException overflow) { throw new IOException("Process VmRSS exceeds signed64 bytes", overflow); }
        }
        long threadCount = fields.containsKey("Threads") ? nonnegative(fields.get("Threads"), "Threads") : 0;
        if (threadCount > Integer.MAX_VALUE) throw new IOException("Process thread count exceeds signed32 capacity");
        if (terminal) {
            boolean race = !terminal(before.state()) || threadCount == 0 || !fields.containsKey("Threads");
            return new Snapshot(after, 0, 0, false, race, initialStat, status, finalStat);
        }
        if (!fields.containsKey("Pid") || !fields.containsKey("Tgid") || statusState == null
                || !statusState.matches("[A-Za-z](?:\\s+\\([^\\r\\n]*\\))?")
                || terminal(statusState.substring(0, 1)) || threadCount < 1
                || !fields.containsKey("VmRSS") && !"1".equals(fields.get("Kthread")))
            throw new UnstableReading("Live process identity/thread metrics are absent or inconsistent; no metrics admitted: pid=" + pid
                + ", Threads=" + threadCount + ", VmRSS_present=" + fields.containsKey("VmRSS")
                + ", Kthread=" + fields.get("Kthread") + ", status_state=" + statusState + ", final_state=" + after.state());
        return new Snapshot(after, rss, (int) threadCount, true, false, initialStat, status, finalStat);
    }

    private static boolean terminal(String state) { return state.equals("Z") || state.equals("X") || state.equals("x"); }
    private static Map<String, String> fields(String status) throws IOException {
        if (status == null || status.length() > MAX_BYTES) throw new IOException("Invalid complete process status size");
        var result = new HashMap<String, String>();
        for (String line : status.split("\n", -1)) {
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon < 1) throw new IOException("Malformed process status field");
            if (result.putIfAbsent(line.substring(0, colon), line.substring(colon + 1).trim()) != null)
                throw new IOException("Duplicate process status field");
        }
        return Map.copyOf(result);
    }
    private static long nonnegative(String text, String label) throws IOException {
        try {
            if (text == null || !text.matches("[0-9]+")) throw new NumberFormatException();
            return Long.parseLong(text);
        } catch (NumberFormatException error) { throw new IOException("Invalid nonnegative process " + label, error); }
    }
    private static Identity identity(long pid, String stat) throws IOException {
        if (pid < 1 || stat == null || stat.length() > MAX_BYTES) throw new IOException("Invalid complete process stat size/ID");
        int open = stat.indexOf('('), end = stat.lastIndexOf(')');
        if (open < 2 || end <= open || !stat.substring(0, open).equals(pid + " ") || end + 2 >= stat.length())
            throw new IOException("Malformed process stat identity");
        String[] fields = stat.substring(end + 2).trim().split(" +");
        if (fields.length < 20 || !fields[0].matches("[A-Za-z]")) throw new IOException("Incomplete process stat identity");
        return new Identity(pid, nonnegative(fields[19], "start ticks"), nonnegative(fields[1], "parent"),
            nonnegative(fields[2], "process group"), nonnegative(fields[3], "session"), fields[0]);
    }
}
