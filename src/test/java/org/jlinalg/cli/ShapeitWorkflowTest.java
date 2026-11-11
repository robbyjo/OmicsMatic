/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class ShapeitWorkflowTest {
    @TempDir Path temp;
    private Map<String,Object> recipe(){var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("profile",ShapeitRuntime.PROFILE);value.put("target","target.bcf");value.put("contig","20");value.put("region","20:1-200000");value.put("parameters",new LinkedHashMap<>(Map.of("snps_only",false,"seed",15052011,"threads",1,"count_policy","REQUIRE_SAMPLE_COUNTS","missing_policy","SOURCE_WHOLE_GENOTYPE_IMPUTATION")));return value;}
    private Path write(Map<String,Object> source)throws Exception{Path p=temp.resolve("recipe-"+UUID.randomUUID()+".json");Files.writeString(p,ShapeitJson.json(source));return p;}
    private record Call(int code,String stdout,String stderr) { }
    private Call call(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=ShapeitToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Call(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    static Stream<String> invalid(){return Stream.of("extra-engine-option","program-path","fractional-capacity","unknown-capacity","zero-wall","too-large-integer","string-capacity","negative-maf","nan-maf","unknown-count-policy","invalid-thread","unsupported-profile");}
    @ParameterizedTest @MethodSource("invalid")
    void unsafeOrUnimplementedRequestsAreRejectedBeforeAnyRuntimeExecution(String mode)throws Exception{
        var r=recipe();var parameters=new LinkedHashMap<String,Object>();for(var e:((Map<?,?>)r.get("parameters")).entrySet())parameters.put(e.getKey().toString(),e.getValue());r.put("parameters",parameters);
        switch(mode){
            case "extra-engine-option"->r.put("extra_args",List.of("--mcmc-noinit"));case "program-path"->r.put("program","/tmp/other");
            case "fractional-capacity"->r.put("limits",Map.of("ledgerBytes",24.5));case "unknown-capacity"->r.put("limits",Map.of("raw",Map.of("sample_first",10)));
            case "zero-wall"->r.put("limits",Map.of("process",Map.of("wallMillis",0)));case "too-large-integer"->r.put("limits",Map.of("ledgerBytes",10000000000000000L));
            case "string-capacity"->r.put("limits",Map.of("tableBytes","1024"));case "negative-maf"->parameters.put("minimum_maf",-0.1);case "nan-maf"->parameters.put("minimum_maf","NaN");
            case "unknown-count-policy"->parameters.put("count_policy","RETAG_INPUT");case "invalid-thread"->parameters.put("threads",0);case "unsupported-profile"->r.put("profile","shapeit5-5.0.0-contig");default->fail(mode);
        }
        Path path=write(r);assertThrows(Exception.class,()->ShapeitRecipe.read(path,temp.resolve("runtime")));assertFalse(Files.exists(temp.resolve("runtime")));
    }
    @Test void relativeInputsAndExplicitPoliciesAreRetainedWithoutInventingAReference()throws Exception{
        var spec=ShapeitRecipe.read(write(recipe()),temp.resolve("runtime"));assertEquals(temp.resolve("target.bcf"),spec.request().target());assertNull(spec.request().reference());assertNull(spec.request().scaffold());assertNull(spec.request().map());assertNull(spec.request().minimumMaf());assertEquals(ShapeitCommonPhasing.MissingPolicy.SOURCE_WHOLE_GENOTYPE_IMPUTATION,spec.request().missing());assertEquals(86400000,spec.request().limits().process().wallMillis());spec.verify();
    }
    @Test void nestedCapacityOverridesPreserveEveryOtherCompletePopulationLimit()throws Exception{
        var r=recipe();r.put("limits",Map.of("raw",Map.of("samples",200000),"process",Map.of("wallMillis",300000),"tableBytes",2000000));var limits=ShapeitRecipe.read(write(r),temp.resolve("runtime")).request().limits();assertEquals(200000,limits.raw().samples());assertEquals(300000,limits.process().wallMillis());assertEquals(1000000,limits.raw().records());assertEquals(2000000,limits.tableBytes());
    }
    @Test void changedOrDuplicateRecipeCannotBeAdmitted()throws Exception{
        Path path=write(recipe());var spec=ShapeitRecipe.read(path,temp.resolve("runtime"));Files.writeString(path," ",StandardOpenOption.APPEND);assertThrows(IOException.class,spec::verify);
        Path duplicate=temp.resolve("duplicate.json");Files.writeString(duplicate,"{\"schema_version\":1,\"schema_version\":1}");assertThrows(IOException.class,()->ShapeitRecipe.read(duplicate,temp.resolve("runtime")));
    }
    @Test void setupExportHasExactResourcesAndCannotOverwriteAnExistingDirectory()throws Exception{
        Path output=temp.resolve("setup");assertEquals(0,call("--export-runtime-setup",output.toString()).code());assertEquals(2,call("--export-runtime-setup",output.toString()).code());
        for(String name:List.of("install_shapeit.py","shapeit-runtime-lock.json"))try(var input=getClass().getResourceAsStream(name.endsWith(".py")?"/org/jlinalg/omics/shapeit-runtime-setup/"+name:"/org/jlinalg/omics/"+name)){assertNotNull(input);assertArrayEquals(input.readAllBytes(),Files.readAllBytes(output.resolve(name)));}
        assertFalse(Files.exists(output.resolve("bin")));assertTrue(Files.readString(output.resolve("README.txt")).contains("does not download"));
    }
    @Test void missingRuntimeProducesProminentSkipAndNoCompletionMarker()throws Exception{
        Path output=temp.resolve("skip");var result=call("--recipe",write(recipe()).toString(),"--out",output.toString());assertEquals(3,result.code());assertTrue(result.stderr().contains("SKIP3"));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("Required action"));assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.exists(output.resolve("manifest.json")));
    }
    @Test void unavailableHostOrHomeIsASkipAndDoesNotClaimScience()throws Exception{
        Path output=temp.resolve("unavailable");var result=call("--recipe",write(recipe()).toString(),"--home",temp.resolve("missing").toString(),"--out",output.toString());assertEquals(3,result.code(),result.stderr());assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.readString(output.resolve("session.json")).contains("\"scientific_result_admitted\":false"));
    }
    @Test void malformedRecipeIsAFailureWithClearRecovery()throws Exception{
        Path path=temp.resolve("invalid.json");Files.writeString(path,"{}");Path output=temp.resolve("fail");var result=call("--recipe",path.toString(),"--home",temp.resolve("missing").toString(),"--out",output.toString());assertEquals(2,result.code());assertTrue(result.stderr().contains("FAIL2"));assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("NEW directory"));
    }
    @Test void existingOutputAndRuntimeOverlapAreRefusedWithoutChangingTheirContents()throws Exception{
        Path existing=Files.createDirectory(temp.resolve("existing"));Files.writeString(existing.resolve("keep"),"original");assertEquals(2,call("--recipe","unused","--out",existing.toString()).code());assertEquals("original",Files.readString(existing.resolve("keep")));try(var paths=Files.list(existing)){assertEquals(1,paths.count());}
        Path nested=temp.resolve("runtime/new");Files.createDirectory(temp.resolve("runtime"));assertEquals(2,call("--recipe","unused","--home",temp.resolve("runtime").toString(),"--out",nested.toString()).code());assertFalse(Files.exists(nested));
    }
    @Test void publicHelpExplainsScientificLimitsAndExactSetup()throws Exception{
        var result=call("--help");assertEquals(0,result.code());for(String phrase:List.of("COMPLETED.json","SKIP3","FAIL2","does not download","phase accuracy","SHAPEIT2/4"))assertTrue(result.stdout().contains(phrase),phrase);
    }
    @Test void diagnosticJsonIsDeterministicEscapedFiniteAndClosedToForeignObjects()throws Exception{
        String raw=ShapeitJson.json(new LinkedHashMap<>(Map.of("z","quote\"\n\\","a",List.of(1,2))));assertTrue(raw.startsWith("{\"a\":"));assertEquals("quote\"\n\\",((Map<?,?>)SimpleJson.parse(raw)).get("z"));assertThrows(IOException.class,()->ShapeitJson.json(Double.NaN));assertThrows(IOException.class,()->ShapeitJson.json(new Object()));assertThrows(IOException.class,()->ShapeitJson.json(Map.of(1,"not-string")));
    }
}
