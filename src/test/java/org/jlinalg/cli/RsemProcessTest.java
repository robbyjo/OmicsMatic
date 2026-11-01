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
final class RsemProcessTest {
    @TempDir Path root;
    private static RsemProcess.Limits limits(long workspace,int files){return new RsemProcess.Limits(60000,1073741824,60,16777216,256,workspace,files,8,64,536870912,25);}
    @Test void invalidIndependentProcessCapacitiesAreRejected()throws Exception{
        assertNotNull(RsemProcess.Limits.health());assertThrows(IllegalArgumentException.class,()->limits(0,64));assertThrows(IllegalArgumentException.class,()->limits(100000,3));
        assertThrows(IllegalArgumentException.class,()->new RsemProcess.Limits(0,1073741824,60,1000,256,100000,64,8,64,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new RsemProcess.Limits(1000,1073741824,60,1000,256,100000,64,8,0,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new RsemProcess.Limits(1000,1073741824,60,1000,256,100000,64,8,64,0,25));
    }
    @Test void accountedRootsCannotOverlapEachOtherOrTheNewRun()throws Exception{
        Path prepared=Files.createDirectory(root.resolve("prepared")),nested=Files.createDirectory(prepared.resolve("nested")),work=root.resolve("run");
        assertEquals(List.of(prepared),RsemProcess.accountingRoots(work,List.of(prepared)));
        for(List<Path> invalid:List.of(List.of(prepared,prepared),List.of(prepared,nested),List.of(root)))assertThrows(IOException.class,()->RsemProcess.accountingRoots(work,invalid));
        assertThrows(IOException.class,()->RsemProcess.accountingRoots(prepared.resolve("child"),List.of(prepared)));assertThrows(IOException.class,()->RsemProcess.accountingRoots(work,List.of(root.resolve("missing"))));assertFalse(Files.exists(work));
    }
    @Test void preparedBytesAndMembersAreIncludedInWorkspaceLimits()throws Exception{
        Path prepared=Files.createDirectory(root.resolve("prepared")),run=Files.createDirectory(root.resolve("run"));Files.write(prepared.resolve("original"),new byte[128]);Files.write(run.resolve("result"),new byte[128]);
        assertEquals(256,RsemProcess.workspaceBytes(run,limits(256,4),List.of(prepared)));assertThrows(IOException.class,()->RsemProcess.workspaceBytes(run,limits(255,4),List.of(prepared)));
        Files.write(run.resolve("extra"),new byte[0]);assertThrows(IOException.class,()->RsemProcess.workspaceBytes(run,limits(256,4),List.of(prepared)));
    }
    @Test void supportedHostExecutionNeverCreatesAScientificCompletionMarker()throws Exception{
        Path work=root.resolve("process");
        if(!System.getProperty("os.name").equals("Linux")){assertThrows(RsemProcess.UnavailableDependency.class,()->RsemProcess.executeOwned(List.of("/usr/bin/python3","-c","print('helper')"),Map.of("PATH","/usr/bin:/bin"),work,RsemProcess.Limits.health(),()->{},()->{}));assertFalse(Files.exists(work));return;}
        var result=RsemProcess.executeOwned(List.of("/usr/bin/python3","-c","print('helper')"),Map.of("PATH","/usr/bin:/bin"),work,RsemProcess.Limits.health(),()->{},()->{});
        assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());assertFalse(Files.exists(work.resolve("COMPLETED.json")));assertEquals("completed-process-only",((Map<?,?>)SimpleJson.parse(Files.readString(result.ledger()))).get("status"));
    }
    @Test void inputIntegrityFailureIsVisibleAndPreventsOwnershipAndLaunch()throws Exception{
        Path work=root.resolve("refused");assertThrows(IOException.class,()->RsemProcess.executeOwned(List.of("/usr/bin/python3","-c","print('never')"),Map.of(),work,RsemProcess.Limits.health(),()->{throw new IOException("changed source");},()->{}));assertFalse(Files.exists(work));
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void existing_process_outputs_remain_byte_identical_after_a_rejected_rerun()throws Exception{
        Path work=root.resolve("existing");RsemProcess.executeOwned(List.of("/usr/bin/true"),Map.of("PATH","/usr/bin:/bin"),work,RsemProcess.Limits.health(),()->{},()->{});
        var before=new java.util.TreeMap<String,String>();try(var paths=Files.walk(work)){
            for(Path path:paths.filter(Files::isRegularFile).toList())before.put(work.relativize(path).toString(),AdmixtureInputs.digest(Files.readAllBytes(path)));
        }
        assertThrows(java.nio.file.FileAlreadyExistsException.class,()->RsemProcess.executeOwned(List.of("/usr/bin/true"),Map.of("PATH","/usr/bin:/bin"),work,RsemProcess.Limits.health(),()->{},()->{}));
        var after=new java.util.TreeMap<String,String>();try(var paths=Files.walk(work)){
            for(Path path:paths.filter(Files::isRegularFile).toList())after.put(work.relativize(path).toString(),AdmixtureInputs.digest(Files.readAllBytes(path)));
        }
        assertEquals(before,after);assertTrue(Files.readString(work.resolve("launch.json")).contains("--core=0"));
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void configuration_basename_probes_are_opaque_and_do_not_relax_normal_execution()throws Exception{
        Path source=Files.createDirectory(root.resolve("source"));Files.writeString(source.resolve("target"),"input");
        Files.createSymbolicLink(source.resolve("alias"),Path.of("target"));Files.createSymbolicLink(source.resolve("self"),Path.of("self"));
        Files.createSymbolicLink(source.resolve("missing-alias"),Path.of("absent"));
        IOException strict=assertThrows(IOException.class,()->RsemProcess.executeOwned(List.of("/usr/bin/true"),Map.of(),root.resolve("strict"),limits(1048576,128),()->{},()->{},List.of(source)));
        assertTrue(strict.getMessage().contains(source.toString()));
        var result=RsemProcess.executeConfiguration(List.of("/usr/bin/true"),Map.of(),root.resolve("configured"),limits(1048576,128),()->{},()->{},List.of(source),List.of(source));
        assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());
        assertTrue(Files.readString(result.ledger()).contains("configuration_basename_link_roots"));
        assertThrows(IOException.class,()->RsemProcess.workspaceBytes(result.work(),limits(1048576,128),List.of(source)));
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void configuration_probe_targets_cannot_escape_or_traverse_another_directory()throws Exception{
        int ordinal=0;
        for(String target:List.of("/tmp/outside","../outside","subdir/target",".","..")){
            Path source=Files.createDirectory(root.resolve("source-"+ordinal)),link=source.resolve("alias");Files.createSymbolicLink(link,Path.of(target));
            Path work=root.resolve("refused-"+ordinal++);
            IOException failure=assertThrows(IOException.class,()->RsemProcess.executeConfiguration(List.of("/usr/bin/true"),Map.of(),work,limits(1048576,128),()->{},()->{},List.of(source),List.of(source)));
            assertTrue(failure.getMessage().contains("contained relative basename"));assertTrue(failure.getMessage().contains(link.toString()));
            assertFalse(Files.exists(work.resolve("launch.json")));
        }
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void configuration_probe_roots_must_be_declared_canonical_source_subtrees()throws Exception{
        Path source=Files.createDirectory(root.resolve("source")),nested=Files.createDirectory(source.resolve("nested")),outside=Files.createDirectory(root.resolve("outside"));int ordinal=0;
        for(List<Path> invalid:List.of(List.of(outside),List.of(source,source),List.of(source,nested),List.of(source.resolve("missing")))){
            Path work=root.resolve("unlaunched-"+ordinal++);
            assertThrows(IOException.class,()->RsemProcess.executeConfiguration(List.of("/usr/bin/true"),Map.of(),work,limits(1048576,128),()->{},()->{},List.of(source),invalid));assertFalse(Files.exists(work));
        }
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void configuration_scope_never_accepts_a_fifo_or_reads_its_contents()throws Exception{
        Path source=Files.createDirectory(root.resolve("source")),fifo=source.resolve("probe-fifo"),work=root.resolve("fifo");
        IOException failure=assertThrows(IOException.class,()->RsemProcess.executeConfiguration(List.of("/usr/bin/python3","-c","import os,sys,time;os.mkfifo(sys.argv[1]);time.sleep(3)",fifo.toString()),Map.of("PATH","/usr/bin:/bin"),work,limits(1048576,128),()->{},()->{},List.of(source),List.of(source)));
        assertTrue(failure.getMessage().contains(fifo.toString()));assertTrue(failure.getMessage().contains("other=true"));
        Map<?,?> ledger=(Map<?,?>)SimpleJson.parse(Files.readString(work.resolve("process.json")));assertEquals(List.of(),ledger.get("owned_live_after_cleanup"));
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void configuration_probe_permission_does_not_cover_process_work_directory()throws Exception{
        Path source=Files.createDirectory(root.resolve("source")),work=root.resolve("outside-probe");
        IOException failure=assertThrows(IOException.class,()->RsemProcess.executeConfiguration(List.of("/usr/bin/python3","-c","import os,time;os.symlink('absent','work-link');time.sleep(3)"),Map.of("PATH","/usr/bin:/bin"),work,limits(1048576,128),()->{},()->{},List.of(source),List.of(source)));
        assertTrue(failure.getMessage().contains(work.resolve("work-link").toString()));
        Map<?,?> ledger=(Map<?,?>)SimpleJson.parse(Files.readString(work.resolve("process.json")));assertEquals(List.of(),ledger.get("owned_live_after_cleanup"));
    }
}
