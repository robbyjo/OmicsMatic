/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
final class ScviProcessTest {
    @TempDir Path root;
    private static ScviProcess.Limits limits(long workspace,int files){return new ScviProcess.Limits(60000,1073741824,60,16777216,256,workspace,files,8,64,536870912,25);}
    @Test void invalidIndependentProcessCapacitiesAreRejected()throws Exception{
        assertNotNull(ScviProcess.Limits.health());assertThrows(IllegalArgumentException.class,()->limits(0,64));assertThrows(IllegalArgumentException.class,()->limits(100000,3));
        assertThrows(IllegalArgumentException.class,()->new ScviProcess.Limits(0,1073741824,60,1000,256,100000,64,8,64,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new ScviProcess.Limits(1000,1073741824,60,1000,256,100000,64,8,0,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new ScviProcess.Limits(1000,1073741824,60,1000,256,100000,64,8,64,0,25));
    }
    @Test void accountedRootsCannotOverlapEachOtherOrTheNewRun()throws Exception{
        Path prepared=Files.createDirectory(root.resolve("prepared")),nested=Files.createDirectory(prepared.resolve("nested")),work=root.resolve("run");
        assertEquals(List.of(prepared),ScviProcess.accountingRoots(work,List.of(prepared)));
        for(List<Path> invalid:List.of(List.of(prepared,prepared),List.of(prepared,nested),List.of(root)))assertThrows(IOException.class,()->ScviProcess.accountingRoots(work,invalid));
        assertThrows(IOException.class,()->ScviProcess.accountingRoots(prepared.resolve("child"),List.of(prepared)));assertThrows(IOException.class,()->ScviProcess.accountingRoots(work,List.of(root.resolve("missing"))));assertFalse(Files.exists(work));
    }
    @Test void preparedBytesAndMembersAreIncludedInWorkspaceLimits()throws Exception{
        Path prepared=Files.createDirectory(root.resolve("prepared")),run=Files.createDirectory(root.resolve("run"));Files.write(prepared.resolve("original"),new byte[128]);Files.write(run.resolve("result"),new byte[128]);
        assertEquals(256,ScviProcess.workspaceBytes(run,limits(256,4),List.of(prepared)));assertThrows(IOException.class,()->ScviProcess.workspaceBytes(run,limits(255,4),List.of(prepared)));
        Files.write(run.resolve("extra"),new byte[0]);assertThrows(IOException.class,()->ScviProcess.workspaceBytes(run,limits(256,4),List.of(prepared)));
    }
    @Test void supportedHostExecutionNeverCreatesAScientificCompletionMarker()throws Exception{
        Path work=root.resolve("process");
        if(!System.getProperty("os.name").equals("Linux")){assertThrows(ScviProcess.UnavailableDependency.class,()->ScviProcess.executeOwned(List.of("/usr/bin/python3","-c","print('helper')"),Map.of("PATH","/usr/bin:/bin"),work,ScviProcess.Limits.health(),()->{},()->{}));assertFalse(Files.exists(work));return;}
        var result=ScviProcess.executeOwned(List.of("/usr/bin/python3","-c","print('helper')"),Map.of("PATH","/usr/bin:/bin"),work,ScviProcess.Limits.health(),()->{},()->{});
        assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());assertFalse(Files.exists(work.resolve("COMPLETED.json")));assertEquals("completed-process-only",((Map<?,?>)SimpleJson.parse(Files.readString(result.ledger()))).get("status"));
    }
    @Test void inputIntegrityFailureIsVisibleAndPreventsOwnershipAndLaunch()throws Exception{
        Path work=root.resolve("refused");assertThrows(IOException.class,()->ScviProcess.executeOwned(List.of("/usr/bin/python3","-c","print('never')"),Map.of(),work,ScviProcess.Limits.health(),()->{throw new IOException("changed source");},()->{}));assertFalse(Files.exists(work));
    }
}
