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
final class RepeatProcessTest {
    @TempDir Path root;
    private static RepeatProcess.Limits limits(long workspace,int files){return new RepeatProcess.Limits(60000,1073741824,60,16777216,256,workspace,files,8,64,536870912,25);}
    @Test void invalidIndependentProcessCapacitiesAreRejected()throws Exception{
        assertNotNull(RepeatProcess.Limits.health());assertThrows(IllegalArgumentException.class,()->limits(0,64));assertThrows(IllegalArgumentException.class,()->limits(100000,3));
        assertThrows(IllegalArgumentException.class,()->new RepeatProcess.Limits(0,1073741824,60,1000,256,100000,64,8,64,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new RepeatProcess.Limits(1000,1073741824,60,1000,256,100000,64,8,0,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new RepeatProcess.Limits(1000,1073741824,60,1000,256,100000,64,8,64,0,25));
    }
    @Test void typedBudgetUsesTheTightestPreparedAndRunCapacity()throws Exception{
        var request=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");var spec=RepeatRecipe.read(RepeatTestRecipes.write(root,request));var actual=RepeatProcess.Limits.from(spec);
        assertEquals(64,actual.fileCount());assertEquals(2097152,actual.workspaceBytes());assertEquals(spec.process().threads(),actual.threads());
    }
    @Test void accountedRootsCannotOverlapEachOtherOrTheNewRun()throws Exception{
        Path prepared=Files.createDirectory(root.resolve("prepared")),nested=Files.createDirectory(prepared.resolve("nested")),work=root.resolve("run");
        assertEquals(List.of(prepared),RepeatProcess.accountingRoots(work,List.of(prepared)));
        for(List<Path> invalid:List.of(List.of(prepared,prepared),List.of(prepared,nested),List.of(root)))assertThrows(IOException.class,()->RepeatProcess.accountingRoots(work,invalid));
        assertThrows(IOException.class,()->RepeatProcess.accountingRoots(prepared.resolve("child"),List.of(prepared)));assertThrows(IOException.class,()->RepeatProcess.accountingRoots(work,List.of(root.resolve("missing"))));assertFalse(Files.exists(work));
    }
    @Test void preparedBytesAndMembersAreIncludedInWorkspaceLimits()throws Exception{
        Path prepared=Files.createDirectory(root.resolve("prepared")),run=Files.createDirectory(root.resolve("run"));Files.write(prepared.resolve("original"),new byte[128]);Files.write(run.resolve("result"),new byte[128]);
        assertEquals(256,RepeatProcess.workspaceBytes(run,limits(256,4),List.of(prepared)));assertThrows(IOException.class,()->RepeatProcess.workspaceBytes(run,limits(255,4),List.of(prepared)));
        Files.write(run.resolve("extra"),new byte[0]);assertThrows(IOException.class,()->RepeatProcess.workspaceBytes(run,limits(256,4),List.of(prepared)));
    }
    @Test void supportedHostExecutionNeverCreatesAScientificCompletionMarker()throws Exception{
        Path work=root.resolve("process");
        if(!System.getProperty("os.name").equals("Linux")){assertThrows(RepeatProcess.UnavailableDependency.class,()->RepeatProcess.executeOwned(List.of("/usr/bin/python3","-c","print('helper')"),Map.of("PATH","/usr/bin:/bin"),work,RepeatProcess.Limits.health(),()->{},()->{}));assertFalse(Files.exists(work));return;}
        var result=RepeatProcess.executeOwned(List.of("/usr/bin/python3","-c","print('helper')"),Map.of("PATH","/usr/bin:/bin"),work,RepeatProcess.Limits.health(),()->{},()->{});
        assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());assertFalse(Files.exists(work.resolve("COMPLETED.json")));assertEquals("completed-process-only",((Map<?,?>)SimpleJson.parse(Files.readString(result.ledger()))).get("status"));
    }
    @Test void inputIntegrityFailureIsVisibleAndPreventsOwnershipAndLaunch()throws Exception{
        Path work=root.resolve("refused");assertThrows(IOException.class,()->RepeatProcess.executeOwned(List.of("/usr/bin/python3","-c","print('never')"),Map.of(),work,RepeatProcess.Limits.health(),()->{throw new IOException("changed source");},()->{}));assertFalse(Files.exists(work));
    }
}
