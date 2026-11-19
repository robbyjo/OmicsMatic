/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlinkRelatednessOwnedProcessTest {
    @TempDir Path temporary;

    private static String fixtureClasspath() {
        try {
            return Path.of(PlinkRelatednessProcessFixture.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException error) { throw new IllegalStateException(error); }
    }

    private ProcessBuilder fixture(Path ready) {
        String executable = ProcessHandle.current().info().command().orElseThrow();
        return new ProcessBuilder(executable, "-cp", fixtureClasspath(),
                PlinkRelatednessProcessFixture.class.getName(), "parent", ready.toString());
    }

    private static long awaitChild(Path ready) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!Files.isRegularFile(ready) && System.nanoTime() - deadline < 0) Thread.sleep(20);
        assertTrue(Files.isRegularFile(ready), "Owned fixture did not start its child");
        String text = "";
        while (text.isEmpty() && System.nanoTime() - deadline < 0) {
            text = Files.readString(ready).strip();
            if (text.isEmpty()) Thread.sleep(20);
        }
        return Long.parseLong(text);
    }

    private static void assertStopped(Map<String, Object> record, long child) {
        assertFalse(ProcessHandle.of(((Number) record.get("owned_pid")).longValue())
                .map(ProcessHandle::isAlive).orElse(false), "Owned parent survived cleanup");
        assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false),
                "Owned child survived cleanup");
        assertEquals(false, record.get("owned_alive_after_cleanup"));
        assertEquals(java.util.List.of(), record.get("unverified_descendants_not_killed"));
        assertEquals(java.util.List.of(), record.get("owned_descendants_alive_after_cleanup"));
    }

    @Test void timeoutStopsOwnedDescendantAndKeepsUnrelatedProcessAlive() throws Exception {
        Path stage = Files.createDirectory(temporary.resolve("timeout"));
        Files.createDirectory(stage.resolve("worker"));
        Path ready = stage.resolve("child.pid");
        var limits = PlinkRelatednessContracts.validate(PlinkRelatednessContractsTest.request()).limits();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("started_at", Instant.now().toString());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                PlinkRelatednessRuntime.executeOwned(fixture(ready), stage, "fixture", limits,
                        new PlinkRelatednessSupport.Clock(5), record);
            } catch (Throwable error) { failure.set(error); }
        });
        Process sentinel = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(),
                "-cp", fixtureClasspath(),
                PlinkRelatednessProcessFixture.class.getName(), "leaf").start();
        try {
            runner.start(); long child = awaitChild(ready); runner.join(40_000);
            assertFalse(runner.isAlive(), "Timeout cleanup did not finish");
            assertInstanceOf(PlinkRelatednessSupport.LimitExceeded.class, failure.get());
            assertStopped(record, child); assertTrue(sentinel.isAlive());
            var saved = PlinkRelatednessSupport.read(stage.resolve("worker/fixture-process.json"));
            assertEquals(false, saved.get("owned_alive_after_cleanup"));
            assertEquals(false, saved.get("process_returned_before_monitor_failure"));
            PlinkRelatednessSupport.json(stage.resolve("test-assertions.json"), Map.of(
                    "owned_parent_stopped", true, "owned_child_stopped", true,
                    "unrelated_process_pid", sentinel.pid(), "unrelated_process_alive", sentinel.isAlive()));
        } finally {
            if (runner.isAlive()) { runner.interrupt(); runner.join(35_000); }
            sentinel.destroyForcibly(); sentinel.waitFor();
        }
    }

    @Test void interruptionWritesRecordRestoresFlagAndStopsOwnedDescendant() throws Exception {
        Path stage = Files.createDirectory(temporary.resolve("interrupt"));
        Files.createDirectory(stage.resolve("worker")); Path ready = stage.resolve("child.pid");
        var limits = PlinkRelatednessContracts.validate(PlinkRelatednessContractsTest.request()).limits();
        Map<String, Object> record = new LinkedHashMap<>();
        AtomicReference<Throwable> failure = new AtomicReference<>(); AtomicBoolean restored = new AtomicBoolean();
        Thread runner = new Thread(() -> {
            try {
                PlinkRelatednessRuntime.executeOwned(fixture(ready), stage, "fixture", limits,
                        new PlinkRelatednessSupport.Clock(60), record);
            } catch (Throwable error) { failure.set(error); }
            finally { restored.set(Thread.currentThread().isInterrupted()); }
        });
        try {
            runner.start(); long child = awaitChild(ready); runner.interrupt(); runner.join(40_000);
            assertFalse(runner.isAlive(), "Interrupted cleanup did not finish");
            assertInstanceOf(InterruptedIOException.class, failure.get());
            assertTrue(restored.get(), "Caller interruption was lost"); assertStopped(record, child);
            assertTrue(Files.isRegularFile(stage.resolve("worker/fixture-process.json")));
            assertEquals(false, PlinkRelatednessSupport.read(stage.resolve("worker/fixture-process.json"))
                    .get("owned_alive_after_cleanup"));
            PlinkRelatednessSupport.json(stage.resolve("test-assertions.json"), Map.of(
                    "owned_parent_stopped", true, "owned_child_stopped", true,
                    "caller_interruption_restored", restored.get()));
        } finally {
            if (runner.isAlive()) { runner.interrupt(); runner.join(35_000); }
        }
    }
}
