/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrsCsRuntimeTest {
    @TempDir Path root;
    private static Path home() {
        String home = System.getenv("JLINALG_PRSCS_HOME");
        Assumptions.assumeTrue(home != null, "Supply exact private PRS-CS home for actual dependency tests");
        return Path.of(home);
    }

    @Test void threeActualOriginalProfilesHaveExplicitCapabilitiesAndCacheBypass() throws Exception {
        Path home = home();
        for (String profile : List.of("source-fbc9ec0","1.1.0","1.0.0")) {
            Path work = root.resolve(profile);Files.createDirectory(work);
            var session = new LinkedHashMap<String,Object>();
            var runtime = new PrsCsRuntime(home,profile,work,session);
            assertEquals(profile.equals("source-fbc9ec0"),runtime.supportsDraws());
            assertEquals(!profile.equals("1.0.0"),runtime.supportsSe());
            assertEquals(!profile.equals("1.0.0"),runtime.supportsPsi());
            assertEquals(!profile.equals("source-fbc9ec0"),runtime.legacy());
            assertEquals(profile.equals("source-fbc9ec0")?"FALSE":"False",runtime.bool(false));
            assertEquals(profile.equals("source-fbc9ec0")?"TRUE":"True",runtime.bool(true));
            assertEquals(Boolean.TRUE,session.get("python_cache_bypassed"));
            assertTrue(session.containsKey("original_source_commit"));
            assertEquals(0,session.get("imports_exit_code"));assertEquals(0,session.get("usage_exit_code"));
            var command = runtime.original(work,"future-analysis");
            assertTrue(command.contains("-I"));assertTrue(command.contains("-B"));
            assertTrue(command.contains("-W"));assertTrue(command.contains("default"));
            assertTrue(command.stream().anyMatch(s -> s.startsWith("pycache_prefix=")));
            assertTrue(command.stream().anyMatch(s -> s.endsWith("sources\\"+profile) || s.endsWith("sources/"+profile)));
            runtime.verify();
        }
    }

    @Test void missingRuntimeIsUnavailableAndDoesNotCreateIt() throws Exception {
        Path missing = root.resolve("absent"),work = root.resolve("work");Files.createDirectory(work);
        assertThrows(PrsCsRuntime.Unavailable.class, () -> new PrsCsRuntime(missing,"source-fbc9ec0",work,new LinkedHashMap<>()));
        assertFalse(Files.exists(missing));assertTrue(PrsCsRuntime.setup().contains("--allow-install"));
    }

    @Test void wrongMarkerPinIsRejectedBeforeAnyInterpreterRuns() throws Exception {
        Path actual = home();Path changed = root.resolve("changed");Files.createDirectory(changed);
        byte[] lock;try(var input=PrsCsRuntimeTest.class.getResourceAsStream("/org/jlinalg/omics/prscs-runtime-lock.json")){assertNotNull(input);lock=input.readAllBytes();}
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(lock));
        String marker = Files.readString(actual.resolve(".jlinalg-prscs.json"));assertTrue(marker.contains(digest));
        Files.writeString(changed.resolve(".jlinalg-prscs.json"),marker.replace(digest,"0".repeat(64)));
        Path work=root.resolve("work");Files.createDirectory(work);
        var failure=assertThrows(PrsCsRuntime.Unavailable.class, () -> new PrsCsRuntime(changed,"source-fbc9ec0",work,new LinkedHashMap<>()));
        assertTrue(failure.getMessage().contains("marker differs"));assertFalse(Files.exists(work.resolve("imports.stdout")));
    }

    @Test void actualOwnedPythonTimeoutTerminatesAndRetainsExitAndHashes() throws Exception {
        Path home=home(),work=root.resolve("timeout");Files.createDirectory(work);var session=new LinkedHashMap<String,Object>();
        var runtime=new PrsCsRuntime(home,"source-fbc9ec0",work,session);var command=runtime.flags(work,"timeout-child");
        command.addAll(List.of("-c","import time;time.sleep(30)"));
        var failure=assertThrows(IOException.class, () -> runtime.execute(command,work,"timeout-child",1,1));
        assertTrue(failure.getMessage().contains("owned process terminated"));assertEquals(Boolean.TRUE,session.get("timeout-child_timed_out"));
        long pid=((Number)session.get("timeout-child_owned_pid")).longValue();assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        assertTrue(session.containsKey("timeout-child_exit_code"));assertTrue(session.containsKey("timeout-child_stdout_sha256"));
        runtime.verify();
    }
}
