/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeToolsCliTest {
    @TempDir Path temp;
    private record Result(int code,String output,String error) { }
    private static Result run(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(out),new PrintStream(err));return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    @Test void mainDispatchExposesWorkflowsAndPreservesNativeBenchmarkHelp(){
        var help=run("snakemake-tools","--help");assertEquals(0,help.code());for(String text:List.of("run --request","verify --bundle","replay --bundle","allow_install","SKIP3","FAIL2"))assertTrue(help.output().contains(text),text);
        var benchmark=run("snakemake-tools","benchmark","--help");assertEquals(0,benchmark.code());assertTrue(benchmark.output().contains("Native Java aggregation"));assertTrue(benchmark.output().contains("max_rss"));
    }
    @Test void existingOutputAndItsContentsArePreserved()throws Exception {
        Path source=Files.createDirectory(temp.resolve("input")),out=Files.createDirectory(temp.resolve("existing")),sentinel=Files.writeString(out.resolve("sentinel"),"untouched");var result=run("snakemake-tools","verify","--bundle",source.toString(),"--out",out.toString());assertEquals(2,result.code());assertEquals("untouched",Files.readString(sentinel));try(var paths=Files.list(out)){assertEquals(1,paths.count());}
    }
    @Test void incompleteBundleProducesProminentFailureWithoutChangingTheSource()throws Exception {
        Path source=Files.createDirectory(temp.resolve("incomplete")),sentinel=Files.writeString(source.resolve("retained"),"original");Path out=temp.resolve("verification");var result=run("snakemake-tools","verify","--bundle",source.toString(),"--out",out.toString());assertEquals(2,result.code());assertTrue(result.error().contains("FAIL2"));assertEquals("original",Files.readString(sentinel));assertTrue(Files.readString(out.resolve("FAILURE_WARNINGS.md")).contains("never refresh"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void unavailableExecutionPrerequisitesSkipBeforeAnEngineOrDeployerRuns()throws Exception {
        temp=temp.toRealPath();Path request=temp.resolve("request.json");Files.writeString(request,SnakemakeOriginalJson.json(request(temp.resolve("workflow"))));Path out=temp.resolve("run");var result=run("snakemake-tools","run","--request",request.toString(),"--runtime",temp.resolve("missing-runtime").toString(),"--out",out.toString());assertEquals(3,result.code());assertTrue(result.error().contains("SKIP3"));assertTrue(Files.readString(out.resolve("FAILURE_WARNINGS.md")).contains("NEW paths"));assertFalse(Files.exists(out.resolve("process")));assertFalse(Files.exists(out.resolve("bundle/COMPLETED.json")));
    }
    @Test void mismatchedOrDuplicateOptionsDoNotCreateOutput()throws Exception {
        Path out=temp.resolve("absent");for(String[] args:List.of(new String[]{"snakemake-tools","run","--bundle","input","--runtime","runtime","--out",out.toString()},new String[]{"snakemake-tools","verify","--bundle","input","--out",out.toString(),"--out",out.toString()})){assertEquals(2,run(args).code());assertFalse(Files.exists(out));}
    }
    @Test void outputInsideAnInputIsRejectedBeforeTheInputDirectoryChanges()throws Exception {
        temp=temp.toRealPath();Path workflow=Files.createDirectory(temp.resolve("protected-workflow")),sentinel=Files.writeString(workflow.resolve("original"),"unchanged");Path recipe=Files.writeString(temp.resolve("overlap.json"),SnakemakeOriginalJson.json(request(workflow))),out=workflow.resolve("forbidden-output");var result=run("snakemake-tools","run","--request",recipe.toString(),"--runtime",temp.resolve("missing-runtime").toString(),"--out",out.toString());assertEquals(2,result.code());assertTrue(result.error().contains("overlaps"));assertFalse(Files.exists(out));assertEquals("unchanged",Files.readString(sentinel));try(var entries=Files.list(workflow)){assertEquals(1,entries.count());}
    }
    private static Map<String,Object> request(Path source){
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("engine_profile",SnakemakeOriginalRuntime.PROFILE);result.put("workflow",Map.of("path",source.toString(),"snakefile","Snakefile","configfiles",List.of(),"targets",List.of("all")));result.put("resources",Map.of());result.put("parameters",Map.of());result.put("environment",Map.of());result.put("required_tools",List.of());result.put("outputs",List.of(Map.of("root","published","path","result.tsv","kind","file","allow_empty",false,"comparison","exact")));result.put("execution",Map.of("cores",1,"scheduler","greedy","retries",0,"latency_wait_seconds",1,"notemp",false,"benchmark_extended",false,"deployment","preinstalled","allow_install",false,"require_jobs",true));
        String[] keys={"capture_files","capture_bytes","file_bytes","workspace_bytes","wall_seconds","address_space_bytes","cpu_seconds","output_file_bytes","open_files","processes","threads","sampled_rss_bytes","poll_millis","metadata_bytes","event_rows","log_bytes"};long[] values={5000,268435456,67108864,1073741824,120,8589934592L,120,67108864,4096,16,128,2147483648L,100,4194304,100000,67108864};var limits=new LinkedHashMap<String,Object>();for(int index=0;index<keys.length;index++)limits.put(keys[index],values[index]);result.put("limits",limits);return result;
    }
}
