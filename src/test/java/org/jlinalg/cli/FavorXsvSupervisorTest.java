/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorXsvSupervisorTest {
    @TempDir Path directory;
    private Map<String,Object> values(){var out=new LinkedHashMap<String,Object>();out.put("schema_version",1);out.put("work",directory.toAbsolutePath().normalize().toString());out.put("xsv",directory.resolve("unavailable-xsv").toAbsolutePath().normalize().toString());out.put("chromosome","22");out.put("shards","2");out.put("timeout_ms","60000");return out;}
    private String[] args(Map<String,Object> values,List<String> original)throws Exception {
        Path request=directory.resolve("supervisor.json");Files.writeString(request,RegenieRuntime.json(values)+"\n",StandardCharsets.UTF_8);var args=new ArrayList<String>(List.of("--request",request.toString(),"--request-sha256",FollowupSupport.hash(request),"--"));args.addAll(original);return args.toArray(String[]::new);
    }
    private record Fork(int code,String stdout,String stderr) { }
    private Fork fork(String[] args)throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin",windows?"java.exe":"java").toString(),"-cp",System.getProperty("java.class.path"),"org.jlinalg.cli.FavorXsvSupervisor"));command.addAll(List.of(args));Path stdout=directory.resolve("child.stdout"),stderr=directory.resolve("child.stderr");Process process=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();assertTrue(process.waitFor(30,TimeUnit.SECONDS));return new Fork(process.exitValue(),Files.readString(stdout),Files.readString(stderr));
    }
    private List<Map<?,?>> events()throws Exception {var out=new ArrayList<Map<?,?>>();Path folder=directory.resolve("xsv-events");if(Files.exists(folder))try(var paths=Files.list(folder)){for(Path p:paths.toList()){assertTrue(p.getFileName().toString().endsWith(".json"));out.add(RegenieRuntime.object(p));}}return out;}
    @Test void exactOfflineArgumentPlanHasOneJoinPerShardAndOneOrderedConcat()throws Exception {
        var plan=FavorXsvSupervisor.plan(22,2);assertEquals(3,plan.size());assertEquals(List.of("join","--left","VarInfo","./output/chr22/VarInfo_chr22_1.csv","variant_vcf","./database/chr22_1.csv"),plan.get(0));assertEquals(List.of("cat","rows","./output/chr22/Anno_chr22_1.csv","./output/chr22/Anno_chr22_2.csv"),plan.get(2));assertThrows(UnsupportedOperationException.class,()->plan.add(List.of()));assertThrows(UnsupportedOperationException.class,()->plan.get(0).clear());
    }
    @Test void unsupportedChromosomesAndSingleShardPlansAreRefused() {
        for(int chr:new int[]{0,23})assertThrows(Exception.class,()->FavorXsvSupervisor.plan(chr,2));for(int shards:new int[]{0,1,4097})assertThrows(Exception.class,()->FavorXsvSupervisor.plan(22,shards));
    }
    @Test void requestPinsExactBytesWorkingDirectoryAndTypedConfiguration()throws Exception {
        var args=args(values(),FavorXsvSupervisor.plan(22,2).get(0));var spec=FavorXsvSupervisor.request(args,directory);assertEquals(directory.toRealPath(),spec.work());assertEquals(22,spec.chromosome());assertEquals(2,spec.shards());assertEquals(60000,spec.timeoutMillis());assertEquals(FollowupSupport.hash(directory.resolve("supervisor.json")),spec.digest());
    }
    @Test void aChangedRequestCannotBeParsedUnderTheOriginalPin()throws Exception {
        var args=args(values(),FavorXsvSupervisor.plan(22,2).get(0));Files.writeString(directory.resolve("supervisor.json"),"{}\n");assertTrue(assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory)).getMessage().contains("SHA-256"));
    }
    @Test void unknownKeysOrNonStringRuntimeOptionsAreRefused()throws Exception {
        var input=values();input.put("allow-arbitrary-command",true);var first=args(input,List.of("cat"));assertThrows(Exception.class,()->FavorXsvSupervisor.request(first,directory));input=values();input.put("chromosome",22);var second=args(input,List.of("cat"));assertThrows(Exception.class,()->FavorXsvSupervisor.request(second,directory));
    }
    @Test void schemaTimeoutAndIntegralBoundsAreCheckedBeforeExecution()throws Exception {
        for(var entry:Map.of("schema_version","1","timeout_ms","0","chromosome","23","shards","1").entrySet()){var input=values();input.put(entry.getKey(),entry.getValue());var args=args(input,List.of("cat"));assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory));}
    }
    @Test void theActualCwdMustMatchTheRequest()throws Exception {
        var args=args(values(),FavorXsvSupervisor.plan(22,2).get(0));assertTrue(assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory.getParent())).getMessage().contains("working directory"));
    }
    @Test void relativeOrUnnormalizedPathsAreNotAdmitted()throws Exception {
        for(String key:List.of("work","xsv")){var input=values();input.put(key,"relative");var args=args(input,List.of("cat"));assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory));}
    }
    @Test void requestFilenameAndArgumentEnvelopeAreFixed()throws Exception {
        var args=args(values(),FavorXsvSupervisor.plan(22,2).get(0));Files.copy(directory.resolve("supervisor.json"),directory.resolve("other.json"));args[1]=directory.resolve("other.json").toString();assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory));args[0]="--input";assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory));
    }
    @Test void duplicateJsonKeysAreRejectedBeforeAnyChildIsLaunched()throws Exception {
        var args=args(values(),FavorXsvSupervisor.plan(22,2).get(0));Path p=directory.resolve("supervisor.json");String text=Files.readString(p);text=text.replace("\"shards\":\"2\"","\"shards\":\"2\",\"shards\":\"3\"");assertNotEquals(FollowupSupport.hash(p),AdmixtureInputs.digest(text.getBytes(StandardCharsets.UTF_8)));Files.writeString(p,text);args[3]=FollowupSupport.hash(p);assertThrows(Exception.class,()->FavorXsvSupervisor.request(args,directory));
    }
    @Test void missingOrUnsupportedRuntimeProducesATerminalUnavailableEvent()throws Exception {
        var result=fork(args(values(),FavorXsvSupervisor.plan(22,2).get(0)));assertEquals(3,result.code(),result.stderr());assertEquals("",result.stdout());assertTrue(result.stderr().contains("Required action:"));var events=events();assertEquals(1,events.size());assertEquals("unavailable",events.get(0).get("status"));assertEquals(3,((Number)events.get(0).get("exit_code")).intValue());assertFalse(events.get(0).containsKey("child_pid"));
    }
    @Test void unplannedCommandsFailBeforeRuntimeUseAndNeverPolluteCsvStdout()throws Exception {
        var result=fork(args(values(),List.of("search","secret-id","../../outside.csv")));assertEquals(2,result.code(),result.stderr());assertEquals("",result.stdout());assertTrue(result.stderr().contains("Required action:"));var event=events().get(0);assertEquals("failed",event.get("status"));assertEquals(2,((Number)event.get("exit_code")).intValue());assertFalse(event.containsKey("child_pid"));
    }
    @Test void malformedRequestsLeaveNoFalseCompletedEvents()throws Exception {
        var args=args(values(),FavorXsvSupervisor.plan(22,2).get(0));args[3]="0".repeat(64);var result=fork(args);assertEquals(2,result.code(),result.stderr());assertEquals("",result.stdout());assertTrue(events().isEmpty());assertTrue(result.stderr().contains("Required action:"));
    }
}
