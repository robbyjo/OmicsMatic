/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeOriginalDevelopmentTest {
    @TempDir Path temp;
    @BeforeEach void canonical()throws Exception {temp=temp.toRealPath();}
    private Map<String,Object> request() {
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("engine_profile",SnakemakeOriginalRuntime.PROFILE);
        value.put("workflow",Map.of("path",temp.resolve("source").toString(),"snakefile","Snakefile","configfiles",List.of("a.yaml","b.json"),"targets",List.of("all")));
        value.put("resources",Map.of());value.put("parameters",Map.of("huge",new SnakemakeOriginalParameters.NumberToken("9007199254740993"),"tiny",new SnakemakeOriginalParameters.NumberToken("1e-300"),"result",Map.of("$output","result.tsv")));value.put("environment",Map.of());value.put("required_tools",List.of());
        value.put("outputs",List.of(Map.of("root","published","path","result.tsv","kind","file","allow_empty",false,"comparison","exact")));
        value.put("execution",Map.of("cores",2,"scheduler","greedy","retries",1,"latency_wait_seconds",1,"notemp",false,"benchmark_extended",false,"deployment","preinstalled","allow_install",false,"require_jobs",true));
        String[] keys={"capture_files","capture_bytes","file_bytes","workspace_bytes","wall_seconds","address_space_bytes","cpu_seconds","output_file_bytes","open_files","processes","threads","sampled_rss_bytes","poll_millis","metadata_bytes","event_rows","log_bytes"};
        long[] numbers={5000,268435456,67108864,1073741824,120,8589934592L,120,67108864,4096,16,128,2147483648L,100,4194304,100000,67108864};var limits=new LinkedHashMap<String,Object>();for(int i=0;i<keys.length;i++)limits.put(keys[i],numbers[i]);value.put("limits",limits);return value;
    }
    private Path save(Map<String,Object> value)throws IOException {return Files.writeString(temp.resolve("request.json"),SnakemakeOriginalJson.json(value));}
    private static Map<String,Object> section(Map<String,Object> value,String key)throws IOException {var result=new LinkedHashMap<String,Object>();for(var entry:SnakemakeOriginalFiles.object(value.get(key)).entrySet())result.put((String)entry.getKey(),entry.getValue());return result;}
    @Test void explicitParametersRetainLargeIntegerTinyNumberAndOutputBinding()throws Exception {
        var parsed=SnakemakeOriginalRecipe.read(save(request()));Object bound=SnakemakeOriginalParameters.bind(parsed.parameters(),Map.of(),temp.resolve("published"));String json=SnakemakeOriginalJson.parameters(bound);
        assertTrue(json.contains("9007199254740993"));assertTrue(json.contains("1e-300"));assertTrue(json.contains("result.tsv"));assertEquals("published",parsed.outputs().get(0).root());
        var args=SnakemakeOriginalWorkflow.arguments(temp.resolve("bundle"),temp.resolve("captured-workflow"),parsed);assertEquals("--",args.get(args.size()-2));assertEquals("all",args.get(args.size()-1));assertTrue(args.contains("--forceall"));assertTrue(args.indexOf(temp.resolve("captured-workflow/a.yaml").toString())<args.indexOf(temp.resolve("captured-workflow/b.json").toString()));
    }
    @Test void portableRecipeUsesCanonicalRecordedLinuxPathsWithoutOpeningThem()throws Exception {
        var value=request();var workflow=section(value,"workflow");workflow.put("path","/unavailable-producer/workflow");value.put("workflow",workflow);value.put("resources",Map.of("data",Map.of("path","/unavailable-producer/data")));value.put("required_tools",List.of(Map.of("name","cat","path","/unavailable-producer/bin/cat")));
        var parsed=SnakemakeOriginalRecipe.recorded(value);assertEquals("/unavailable-producer/workflow",parsed.workflow().source());assertEquals("/unavailable-producer/data",parsed.sources().get("data"));assertEquals("/unavailable-producer/bin/cat",parsed.tools().get(0).executable());
        for(String path:List.of("C:\\workflow","relative/workflow","/a/../workflow","/a//workflow")){workflow.put("path",path);assertThrows(IOException.class,()->SnakemakeOriginalRecipe.recorded(value),path);}
    }
    @Test void permittedExplicitLocaleAndTimezoneOverrideRecordedDefaults()throws Exception {
        var value=request();value.put("environment",Map.of("TZ","America/New_York","LANG","C","LC_ALL","C"));var parsed=SnakemakeOriginalRecipe.read(save(value));var environment=SnakemakeOriginalEvidence.environment("/run","/runtime",parsed);
        assertEquals("America/New_York",environment.get("TZ"));assertEquals("C",environment.get("LANG"));assertEquals("C",environment.get("LC_ALL"));assertEquals("/run/workspace/bin:/runtime/bin:/usr/bin:/bin",environment.get("PATH"));
    }
    @Test void independentPolicyAuditRejectsProfileDeploymentAndPartialDagEvenWithSuccessClaim()throws Exception {
        var parsed=SnakemakeOriginalRecipe.read(save(request()));var policy=SnakemakeOriginalPolicy.request("/captured-run","/captured-run/workflow",parsed);var effective=SnakemakeOriginalPolicy.disabled();
        for(var entry:SnakemakeOriginalFiles.object(policy.get("expected")).entrySet())effective.put((String)entry.getKey(),entry.getValue());
        var driver=new LinkedHashMap<String,Object>();driver.put("policy",policy);driver.put("effective_policy",effective);driver.put("policy_status","PASS_EFFECTIVE_ORIGINAL_PARSER_POLICY");driver.put("upstream_api_called",true);driver.put("option_sources",Map.of("command_line",Map.of("",Map.of("destination","targets","value",List.of("all")))));
        assertDoesNotThrow(()->SnakemakeOriginalPolicy.audit(driver));
        for(var change:List.of(Map.entry("software_deployment_method",List.of("CONDA")),Map.entry("until",List.of("make")),Map.entry("mode","SUBPROCESS"),Map.entry("use_conda",true),Map.entry("summary",true))){var altered=new java.util.TreeMap<>(effective);altered.put(change.getKey(),change.getValue());driver.put("effective_policy",altered);assertThrows(IOException.class,()->SnakemakeOriginalPolicy.audit(driver),change.getKey());}
        driver.put("effective_policy",effective);driver.put("upstream_api_called",false);assertThrows(IOException.class,()->SnakemakeOriginalPolicy.audit(driver));
    }
    @Test void protectedEngineEnvironmentAndReservedResourceCannotOverrideBindings()throws Exception {
        for(String key:List.of("PATH","PYTHONPATH","SNAKEMAKE_PROFILE","LD_PRELOAD","OMICS_SNAKEMAKE_EVENTS")){var value=request();value.put("environment",Map.of(key,"injected"));assertThrows(IOException.class,()->SnakemakeOriginalRecipe.read(save(value)),key);}
        var value=request();value.put("resources",Map.of("published",Map.of("path",temp.toString())));assertThrows(IOException.class,()->SnakemakeOriginalRecipe.read(save(value)));
    }
    @Test void literalTargetsAndCapturedConfigPathsCannotBecomeFlagsOrTraversal()throws Exception {
        for(var change:List.of(Map.of("targets",List.of("--dry-run")),Map.of("configfiles",List.of("../escape.yaml")))){var value=request();var workflow=section(value,"workflow");workflow.putAll(change);value.put("workflow",workflow);assertThrows(IOException.class,()->SnakemakeOriginalRecipe.read(save(value)));}
    }
    @Test void unavailableRuntimeRetainsActionableSkipWithoutSourceExecution()throws Exception {
        Path recipe=save(request()),out=temp.resolve("missing-runtime");assertThrows(SnakemakeOriginalRuntime.Unavailable.class,()->SnakemakeOriginalWorkflow.execute(recipe,temp.resolve("absent-runtime"),out,()->{}));
        assertTrue(Files.readString(out.resolve("FAILURE_WARNINGS.md")).contains("SKIP3"));assertTrue(Files.isRegularFile(out.resolve("REQUEST.json")));assertFalse(Files.exists(out.resolve("process")));assertFalse(Files.exists(out.resolve("EXECUTED.json")));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void existingOutputIsPreservedAndManagedDeploymentNeverFallsBack()throws Exception {
        Path recipe=save(request()),out=Files.createDirectory(temp.resolve("existing")),sentinel=Files.writeString(out.resolve("sentinel"),"preserve");assertThrows(IOException.class,()->SnakemakeOriginalWorkflow.execute(recipe,temp.resolve("runtime"),out,()->{}));assertEquals("preserve",Files.readString(sentinel));try(var members=Files.list(out)){assertEquals(1,members.count());}
        var value=request();var execution=section(value,"execution");execution.put("deployment","conda");value.put("execution",execution);Path changed=save(value),skipped=temp.resolve("deployment-skipped");assertThrows(SnakemakeOriginalRuntime.Unavailable.class,()->SnakemakeOriginalWorkflow.execute(changed,temp.resolve("runtime"),skipped,()->{}));assertTrue(Files.readString(skipped.resolve("FAILURE.json")).contains("SKIP3"));assertFalse(Files.exists(skipped.resolve("workspace")));
    }
    @Test void completeRootOutputDirectoryCanBeNonemptyButAnEmptyOneIsRejected()throws Exception {
        var root=new SnakemakeOriginalOutputs.Value("directory",null,"/recorded/root",0755);var file=new SnakemakeOriginalOutputs.Value("file",new SnakemakeOriginalFiles.Pin(1,"0".repeat(64)),"/recorded/root/value",0644);var expected=List.of(new SnakemakeOriginalRecipe.Output("published","","directory",false,"exact"));var limits=new SnakemakeOriginalCapture.Limits(100,1000,1000);
        assertDoesNotThrow(()->SnakemakeOriginalOutputs.validate(new SnakemakeOriginalOutputs.Family(Map.of("",root,"value",file),1,1),expected,limits));assertThrows(IOException.class,()->SnakemakeOriginalOutputs.validate(new SnakemakeOriginalOutputs.Family(Map.of("",root),0,0),expected,limits));
    }
}
