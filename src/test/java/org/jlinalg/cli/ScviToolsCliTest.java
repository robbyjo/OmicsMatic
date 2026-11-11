/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScviToolsCliTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private Map<String,Object> example(String name)throws IOException{
        Path base=Path.of("examples/scvi-workflow").toAbsolutePath();var recipe=(Map<String,Object>)SimpleJson.parse(Files.readString(base.resolve(name+".json")));
        for(String role:List.of("reference_data","query_data"))if(recipe.get(role) instanceof Map<?,?> data)for(String key:List.of("matrix","genes","cells","metadata")){var source=(Map<String,Object>)data.get(key);source.put("path",base.resolve((String)source.get("path")).toString());}
        return recipe;
    }
    private Path recipe(Map<String,Object> value)throws IOException{Path path=temp.resolve("recipe-"+UUID.randomUUID()+".json");Files.writeString(path,ScviJson.json(value)+"\n");return path;}
    private record Invocation(int code,String output,String error) { }
    private static Invocation run(String... args){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));return new Invocation(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Map<String,Object> parent,String key){return (Map<String,Object>)parent.get(key);}
    @Test void mainRouteHelpExplainsProfileTrustAndExitRecovery(){var result=run("scvi-tools","--help");assertEquals(0,result.code);assertTrue(result.output.contains("TRANSFER"));assertTrue(result.output.contains("Exit3"));assertTrue(result.output.contains("pickle"));assertTrue(result.output.contains("WSL"));}
    @Test void allRunnableExamplesHaveClosedTypedRequests()throws Exception{
        for(String name:List.of("check","fit-scvi","transfer-scanvi-shared","fit-sysvi-normal","fit-sysvi-vamp")){var selected=ScviRecipe.read(recipe(example(name)));assertNotNull(selected.limits());if(selected.fit()!=null)selected.fit().validateMetadata(selected.reference().covariates());}
    }
    @Test void missingRuntimeIsProminentSkippedAnalysisWithPersistentRecovery()throws Exception{
        var q=example("check");q.put("python",temp.resolve("absent/bin/python").toString());Path out=temp.resolve("skipped");var result=run("scvi-tools","--recipe",recipe(q).toString(),"--out",out.toString());assertEquals(3,result.code);assertTrue(result.error.contains("ANALYSIS SKIPPED"));assertTrue(result.error.contains("Required action"));String status=Files.readString(out.resolve("analysis-status.json"));assertTrue(status.contains("\"model_analysis_completed\":false"));assertFalse(Files.exists(out.resolve("manifest.json")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("NEW --out"));assertTrue(Files.isRegularFile(out.resolve("source/recipe.json")));
    }
    @Test void unsupportedHistoricalReleaseSkipsWithoutSubstitutingCurrentRuntime()throws Exception{
        var q=example("check");q.put("release","1.0.3");Path out=temp.resolve("historical");var result=run("scvi-tools","--recipe",recipe(q).toString(),"--out",out.toString());assertEquals(3,result.code);assertTrue(result.error.contains("no historical substitution"));assertFalse(Files.exists(out.resolve("runtime")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("ANALYSIS SKIPPED"));
    }
    @Test void existingOutputCannotBeChangedByAnyFailureHandler()throws Exception{
        Path out=Files.createDirectory(temp.resolve("existing")),sentinel=out.resolve("WARNINGS.md");Files.writeString(sentinel,"original evidence");var result=run("scvi-tools","--recipe",recipe(example("check")).toString(),"--out",out.toString());assertEquals(2,result.code);assertEquals("original evidence",Files.readString(sentinel));try(var members=Files.list(out)){assertEquals(List.of("WARNINGS.md"),members.map(p->p.getFileName().toString()).sorted().toList());}
    }
    @Test void duplicateJsonKeysFailWithRetainedActionableStatus()throws Exception{
        Path input=temp.resolve("duplicate.json");Files.writeString(input,"{\"schema\":\"one\",\"schema\":\"two\"}");Path out=temp.resolve("bad");var result=run("scvi-tools","--recipe",input.toString(),"--out",out.toString());assertEquals(2,result.code);assertTrue(result.error.contains("duplicate object key"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("ANALYSIS FAILED"));assertFalse(Files.exists(out.resolve("runtime")));
    }
    @Test void arbitraryUpstreamKwargsAndExecutableKeysAreRejectedBeforeRuntime()throws Exception{
        var q=example("fit-scvi");object(object(q,"fit"),"network").put("python_expression","__import__('os')");var failure=assertThrows(IOException.class,()->ScviRecipe.read(recipe(q)));assertTrue(failure.getMessage().contains("Unknown scVI recipe key"));
    }
    @Test void fractionalEpochCountCannotBecomeAnImplicitTruncation()throws Exception{
        var q=example("fit-scvi");object(object(q,"fit"),"training").put("epochs",1.25);assertThrows(IOException.class,()->ScviRecipe.read(recipe(q)));
    }
    @Test void fractionalSeedCannotBeRoundedIntoAValidUint32Seed()throws Exception{
        String json=ScviJson.json(example("fit-scvi"));assertTrue(json.contains("\"seed\":68001"));Path source=temp.resolve("fractional-seed.json");Files.writeString(source,json.replace("\"seed\":68001","\"seed\":4294967295.0000001"));assertThrows(IOException.class,()->ScviRecipe.read(source));
    }
    @Test void exactDecimalIntegerAndScientificNotationRemainValid()throws Exception{
        String json=ScviJson.json(example("fit-scvi"));Path source=temp.resolve("exact-seed.json");Files.writeString(source,json.replace("\"seed\":68001","\"seed\":6.8001e4"));assertEquals(68001,ScviRecipe.read(source).fit().training().seed());
    }
    @Test void wholeValueBeyondLongRangeCannotSaturateToLongMax()throws Exception{
        String json=ScviJson.json(example("check"));Path source=temp.resolve("overflow.json");Files.writeString(source,json.replace("\"preset\":\"bounded-scvi-v1\"","\"preset\":\"bounded-scvi-v1\",\"input_bytes\":9.223372036854776e18"));assertThrows(IOException.class,()->ScviRecipe.read(source));
    }
    @Test void signedExpressionCannotBeSentToTheCountNetwork()throws Exception{
        var q=example("fit-scvi");object(q,"reference_data").put("kind","SIGNED_EXPRESSION");assertThrows(IOException.class,()->ScviRecipe.read(recipe(q)));
    }
    @Test void sysviWarmupCannotBeSilentlyResetByOriginalTraining()throws Exception{
        var q=example("fit-sysvi-normal");object(object(object(q,"fit"),"training"),"warmup").putAll(Map.of("unit","EPOCHS","count",1));var failure=assertThrows(IOException.class,()->ScviRecipe.read(recipe(q)));assertTrue(failure.getMessage().contains("SysVI"));
    }
    @Test void declaredRecipeSourceCannotChangeAfterTypedAdmission()throws Exception{
        Path source=recipe(example("fit-scvi"));var selected=ScviRecipe.read(source);Files.writeString(source,Files.readString(source)+" ");assertThrows(IOException.class,()->selected.verify(()->{}));
    }
    @Test void declinedSavedModelTrustStopsBeforeRuntimeOrModelOpening()throws Exception{
        var q=example("query-saved-scanvi.template");var model=object(q,"reference_model");model.put("sha256","a".repeat(64));model.put("path",temp.resolve("never-created-model.pt").toString());Path out=temp.resolve("declined");var result=run("scvi-tools","--recipe",recipe(q).toString(),"--out",out.toString());assertEquals(3,result.code);assertTrue(result.error.contains("trust was declined"));assertFalse(Files.exists(out.resolve("runtime")));assertFalse(Files.exists(out.resolve("reviewed-reference")));assertFalse(Files.exists(out.resolve("manifest.json")));
    }
    @Test void fixedReferenceQueryCannotShrinkToSharedGenes()throws Exception{
        var q=example("query-saved-scanvi.template");object(q,"reference_model").put("sha256","a".repeat(64));object(q,"query").put("gene_policy","SHARED_BEFORE_TRAINING");assertThrows(IOException.class,()->ScviRecipe.read(recipe(q)));
    }
    @Test void unknownBooleanAndMalformedPrecisionFailAsTypedInput()throws Exception{
        var q=example("fit-scvi");object(object(q,"fit"),"network").put("encode_covariates","true");Path first=recipe(q);assertThrows(IOException.class,()->ScviRecipe.read(first));q=example("fit-scvi");object(q,"reference_data").put("precision","BEST_EFFORT");Path supplied=recipe(q);assertThrows(IOException.class,()->ScviRecipe.read(supplied));
    }
}
