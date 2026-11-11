/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.jlinalg.workflow.LinuxProcessSnapshot;

/** Portable reconciliation and real Linux process admission/cleanup controls. */
public final class NfcoreProcessSnapshotControl {
    private NfcoreProcessSnapshotControl() { }
    private static int checks;
    private static String stat(long pid, String state, long ticks) {
        String[] fields = new String[20]; Arrays.fill(fields, "0");
        fields[0] = state; fields[1] = "1"; fields[2] = "10"; fields[3] = "10"; fields[19] = Long.toString(ticks);
        return pid + " (name ) with spaces) " + String.join(" ", fields) + "\n";
    }
    private static String status(String state, String threads) {
        return "Name:\tcontrol\nState:\t" + state + "\nPid:\t101\nTgid:\t101\nVmRSS:\t4 kB\nThreads:\t" + threads + "\n";
    }
    private static void require(boolean result) { if (!result) throw new AssertionError("Snapshot control " + checks); checks++; }
    private interface Check { void run() throws Exception; }
    private static void refused(Check action) throws Exception {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Malformed or inconsistent live process was admitted");
    }
    private static void portable() throws Exception {
        String running = stat(101, "R", 77), sleeping = stat(101, "S", 77), zombie = stat(101, "Z", 77);
        var live = LinuxProcessSnapshot.reconcile(101, running, status("S (sleeping)", "2"), sleeping);
        require(live.liveMetrics() && !live.exitRace() && live.rssBytes() == 4096 && live.threads() == 2);
        require(live.identity().pid() == 101 && live.identity().startTicks() == 77 && live.identity().state().equals("S"));
        for (String value : new String[]{status("Z (zombie)", "0"), status("Z (zombie)", "1"), ""}) {
            var exited = LinuxProcessSnapshot.reconcile(101, running, value, zombie);
            require(!exited.liveMetrics() && exited.exitRace() && exited.rssBytes() == 0 && exited.threads() == 0);
            require(exited.initialStat().equals(running) && exited.status().equals(value) && exited.finalStat().equals(zombie));
        }
        var dead = LinuxProcessSnapshot.reconcile(101, zombie, status("Z (zombie)", "1"), zombie);
        require(!dead.liveMetrics() && !dead.exitRace());
        for (String bad : new String[]{"0", "-1", "NaN", "Inf", "1.0", "2147483648", "9223372036854775808", ""})
            refused(() -> LinuxProcessSnapshot.reconcile(101, running, status("R (running)", bad), running));
        String valid = status("R (running)", "2");
        for (String bad : new String[]{valid.replace("4 kB", "-4 kB"), valid.replace("4 kB", "4 MB"),
                valid.replace("4 kB", "9223372036854775807 kB"), valid + "Threads:\t3\n",
                valid.replace("Pid:\t101", "Pid:\t102"), valid.replace("Tgid:\t101", "Tgid:\t102"),
                valid.replace("VmRSS:\t4 kB\n", ""), valid.replace("Pid:\t101\n", ""),
                valid.replace("State:\tR (running)", "State:\tZ (zombie)"), valid.replace("Threads:\t2\n", ""), ""})
            refused(() -> LinuxProcessSnapshot.reconcile(101, running, bad, running));
        refused(() -> LinuxProcessSnapshot.reconcile(101, running, valid, stat(101, "R", 78)));
        refused(() -> LinuxProcessSnapshot.reconcile(101, running, valid, stat(102, "R", 77)));
        refused(() -> LinuxProcessSnapshot.reconcile(101, zombie, valid, running));
        refused(() -> LinuxProcessSnapshot.reconcile(101, "malformed", valid, running));
        refused(() -> LinuxProcessSnapshot.reconcile(101, running, valid, "malformed"));
        refused(() -> LinuxProcessSnapshot.reconcile(101, running, "a".repeat(65537), running));
        System.out.println("PASS_PORTABLE_PROCESS_RECONCILIATION " + checks);
    }
    public static void main(String[] args) throws Exception {
        portable();
        if (args.length == 1 && args[0].equals("--portable")) return;
        Path owned = Path.of(args[0]); Files.createDirectory(owned);
        for (int i = 0; i < 100; i++) {
            var sample = LinuxProcessSnapshot.read(ProcessHandle.current().pid());
            require(sample != null && sample.liveMetrics() && sample.threads() > 0 && sample.rssBytes() > 0);
        }
        var limits = new NextflowProcess.Limits(120000, 1073741824, 120, 16777216, 256,
            134217728, 4096, 64, 128, 268435456, 5);
        String churn = "for ((j=0;j<80;j++)); do for ((i=0;i<16;i++)); do true & done; wait; done";
        var result = NfcoreProcess.executeOwned(java.util.List.of("/usr/bin/bash", "-c", churn),
            Map.of("PATH", "/usr/bin:/bin"), owned.resolve("churn"), owned, limits, () -> { }, () -> { });
        require(result.exitCode() == 0 && result.ownedLiveAfterCleanup().isEmpty());
        for (String mode : new String[]{"nonzero", "capacity", "integrity-refusal"}) {
            Path work = owned.resolve(mode);
            var selected = mode.equals("capacity") ? new NextflowProcess.Limits(120000, 1073741824, 120, 16777216,
                256, 134217728, 4096, 64, 128, 1, 5) : limits;
            boolean failed = false;
            try { NfcoreProcess.executeOwned(java.util.List.of("/usr/bin/bash", "-c", mode.equals("nonzero") ? "exit 7" : "sleep 20"),
                    Map.of("PATH", "/usr/bin:/bin"), work, owned, selected,
                    () -> { if (mode.equals("integrity-refusal") && Files.exists(work.resolve("process.stdout")))
                        throw new IOException("Controlled monitor refusal after owned launch"); }, () -> { }); }
            catch (IOException expected) { failed = true; }
            require(failed);
            var ledger = NextflowFiles.object(NextflowFiles.json(work.resolve("process.json"), 2097152));
            require(NextflowFiles.list(ledger.get("owned_live_after_cleanup")).isEmpty());
            require(!Files.exists(work.resolve("COMPLETED.json")));
        }
        if (args.length == 2) {
            Path work = owned.resolve("leader-exit");
            boolean refused = false;
            try { NfcoreProcess.executeOwned(java.util.List.of(args[1], owned.resolve("leader-ready").toString()),
                    Map.of("PATH", "/usr/bin:/bin"), work, owned, limits,
                    () -> { if (Files.exists(owned.resolve("leader-ready"))) throw new IOException("Controlled refusal with a retired leader and live worker"); }, () -> { }); }
            catch (IOException expected) { refused = true; }
            require(refused);
            var ledger = NextflowFiles.object(NextflowFiles.json(work.resolve("process.json"), 2097152));
            require(NextflowFiles.list(ledger.get("owned_live_after_cleanup")).isEmpty());
        }
        System.out.println("PASS_REAL_LINUX_PROCESS_MONITOR_AND_CLEANUP " + checks);
    }
}
