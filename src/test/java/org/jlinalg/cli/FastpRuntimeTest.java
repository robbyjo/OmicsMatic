/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FastpRuntimeTest {
    @TempDir Path temporary;
    @Test void exactlyFourLockedEditionsAndUnsupportedPatchIsNotResolved()throws Exception {
        assertEquals(4,FastpRuntime.profiles().size());
        for(String name:FastpRuntime.profiles())assertEquals(name,FastpRuntime.locked(name).get("profile"));
        assertThrows(FastpRuntime.Unavailable.class,()->FastpRuntime.locked("linux-x86_64-fastp-0.23"));
        assertThrows(FastpRuntime.Unavailable.class,()->FastpRuntime.locked("latest"));
    }
    @Test void malformedContentPinsAndNonintegerFieldsAreRejected() {
        assertThrows(IOException.class,()->FastpRuntime.fingerprint(Map.of("bytes",1L,"sha256","bad")));
        assertThrows(IOException.class,()->FastpRuntime.integer(1.0,0,10));
        assertThrows(IOException.class,()->FastpRuntime.object(List.of()));
    }
    @ParameterizedTest @ValueSource(strings={"0.22.0","0.23.2","0.25.0","1.0.1"})
    void realPinnedStaticRuntimeAndCompleteSetupBindBeforeOwnedExecution(String version)throws Exception {
        String root=System.getenv("JLINALG_FASTP_MANAGED_ROOT");assumeTrue(System.getProperty("os.name").equals("Linux")&&root!=null,"Require real Linux managed fastp setup controls");
        Path homes=Path.of(root);var context=FastpRuntime.open(homes.resolve("homes").resolve(version),"linux-x86_64-fastp-"+version,homes.resolve("reports").resolve(version),()->{});
        assertEquals(version,context.version());context.verify(()->{});
        var result=FastpProcess.executeOwned(java.util.stream.Stream.concat(context.command().stream(),java.util.stream.Stream.of("--version")).toList(),context.environment(),temporary.toRealPath().resolve("version"),new FastpProcess.Limits(30000,3L*1024*1024*1024,30,1048576,128,8388608,64,8,32,134217728,10),()->context.verify(()->{}),()->{});
        assertEquals("fastp "+version+"\n",Files.readString(result.stdout())+Files.readString(result.stderr()));assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());assertFalse(Files.exists(result.work().resolve("COMPLETED.json")));
    }
    @Test void unavailableHostOrMissingRuntimeNeverCreatesAHome()throws Exception {
        Path absent=temporary.resolve("absent");assertThrows(FastpRuntime.Unavailable.class,()->FastpRuntime.open(absent,"linux-x86_64-fastp-0.23.2",temporary.resolve("report"),()->{}));assertFalse(Files.exists(absent));
    }
}
