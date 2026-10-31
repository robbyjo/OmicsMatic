/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class EagleToolsCliTest {
    @TempDir Path temporary;
    private record Call(int code,String stdout,String stderr) { }
    private Call call(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=EagleToolsCli.run(args,new PrintStream(out,true,StandardCharsets.US_ASCII),new PrintStream(err,true,StandardCharsets.US_ASCII));return new Call(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    private Map<?,?> read(Path path)throws Exception{return (Map<?,?>)SimpleJson.parse(Files.readString(path));}
    private void completed(Path out,boolean phase)throws Exception {
        var marker=read(out.resolve("COMPLETED.json"));assertEquals(phase,marker.get("scientific_result_admitted"));assertEquals(!phase,marker.get("input_check_completed"));assertEquals(FollowupSupport.hash(out.resolve("manifest.json")),marker.get("manifest_sha256"));
        var hashes=(Map<?,?>)read(out.resolve("manifest.json")).get("output_sha256");for(var entry:hashes.entrySet())assertEquals(entry.getValue(),FollowupSupport.hash(out.resolve(entry.getKey().toString())));
    }
    @Test void nativeCohortCheckKeepsCanonicalUnicodePathsAndRequiresNoRuntime()throws Exception {
        Path recipe=EagleTestRecipes.write(temporary,EagleTestRecipes.source(temporary,false)),out=temporary.resolve("cohort-α-测");var result=call("--recipe",recipe.toString(),"--out",out.toString());assertEquals(0,result.code(),result.stderr());completed(out,false);
        assertFalse(Files.exists(out.resolve("prepared/engine")));assertTrue(result.stdout().contains(out.toString()));assertTrue(result.stderr().contains("WARNINGS.md"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("INPUT CHECK ONLY"));
        var input=read(out.resolve("input-audit.json"));assertEquals(out.resolve("prepared").toString(),input.get("work"));assertTrue(Files.exists(Path.of(input.get("recipeSnapshot").toString())));
    }
    @Test void nativeReferenceCheckHasCompleteJoinAndOutputPlanWithoutLaunchingPhasing()throws Exception {
        Path recipe=EagleTestRecipes.write(temporary,EagleTestRecipes.source(temporary,true)),out=temporary.resolve("reference");var result=call("--recipe",recipe.toString(),"--out",out.toString());assertEquals(0,result.code(),result.stderr());completed(out,false);assertFalse(Files.exists(out.resolve("prepared/engine")));
        assertTrue(Files.size(out.resolve("prepared/expected-output-plan.tsv"))>100);var phase=read(out.resolve("results.json"));assertTrue(((List<?>)phase.get("engineArguments")).stream().anyMatch(x->x.toString().startsWith("--vcfRef=")));
    }
    @Test void absentPhaseRuntimeProducesProminentSkipAndNoCompletionMarker()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);source.put("operation","phase");Path recipe=EagleTestRecipes.write(temporary,source),out=temporary.resolve("skip");var result=call("--recipe",recipe.toString(),"--out",out.toString());assertEquals(3,result.code(),result.stderr());assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("SKIP3"));assertTrue(result.stderr().contains("SKIP3"));
    }
    @Test void mislabeledBcfAndUnknownFieldsFailWithRetainedRecipe()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"target").put("format","bcf");Path recipe=EagleTestRecipes.write(temporary,source),out=temporary.resolve("bcf");assertEquals(2,call("--recipe",recipe.toString(),"--out",out.toString()).code());assertTrue(Files.exists(out.resolve("recipe-request.json")));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
        source=EagleTestRecipes.source(temporary,false);source.put("extra_flags","--help");recipe=EagleTestRecipes.write(temporary,source);out=temporary.resolve("invalid");var result=call("--recipe",recipe.toString(),"--out",out.toString());assertEquals(2,result.code());assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));
    }
    @Test void existingOutputAndRuntimeHomeAreNeverModified()throws Exception {
        Path recipe=EagleTestRecipes.write(temporary,EagleTestRecipes.source(temporary,false)),existing=temporary.resolve("existing");Files.createDirectory(existing);Files.writeString(existing.resolve("sentinel"),"retain");assertEquals(2,call("--recipe",recipe.toString(),"--out",existing.toString()).code());try(var files=Files.list(existing)){assertEquals(1,files.count());}assertEquals("retain",Files.readString(existing.resolve("sentinel")));
        Path nested=existing.resolve("analysis");assertEquals(2,call("--recipe",recipe.toString(),"--out",nested.toString(),"--home",existing.toString()).code());assertFalse(Files.exists(nested));
    }
    @Test void partialCallRefusalRetainsFailedAdmissionWithoutClaimingResults()throws Exception {
        var source=EagleTestRecipes.source(temporary,false);EagleTestRecipes.section(source,"policies").put("partial_missing","REFUSE");Path recipe=EagleTestRecipes.write(temporary,source),out=temporary.resolve("partial-refused");assertEquals(2,call("--recipe",recipe.toString(),"--out",out.toString()).code());assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.exists(out.resolve("prepared/input-snapshots/target.vcf-source")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("FAILED"));
    }
    @Test void utf8OptionsFileAndConsolePreservePathsUnderAsciiPrintStreams()throws Exception {
        Path recipe=EagleTestRecipes.write(temporary,EagleTestRecipes.source(temporary,false)),out=temporary.resolve("options-α-测"),options=temporary.resolve("options.json");Files.writeString(options,EagleJson.json(Map.of("recipe",recipe.toString(),"out",out.toString())));var result=call("--options-file",options.toString());assertEquals(0,result.code(),result.stderr());completed(out,false);assertTrue(result.stdout().contains(out.toString()));
    }
    @Test void headerOnlyExclusionNeverBecomesAnEmptyEngineExclusionArgument()throws Exception {
        var source=EagleTestRecipes.source(temporary,true);Path inputs=temporary.resolve(source.get("resource_root").toString());Files.writeString(inputs.resolve("empty.vcf"),"##fileformat=VCFv4.2\n##contig=<ID=22>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");source.put("exclusion",Map.of("path","empty.vcf","format","vcf"));Path recipe=EagleTestRecipes.write(temporary,source),out=temporary.resolve("empty-exclusion");var result=call("--recipe",recipe.toString(),"--out",out.toString());assertEquals(0,result.code(),result.stderr());var args=(List<?>)read(out.resolve("results.json")).get("engineArguments");assertTrue(args.stream().noneMatch(x->x.toString().startsWith("--vcfExclude=")));
    }
    @Test void setupExportIsByteExactAndContainsNoRuntimeExecutable()throws Exception {
        Path out=temporary.resolve("setup");var result=call("--export-runtime-setup",out.toString());assertEquals(0,result.code(),result.stderr());assertTrue(Files.exists(out.resolve("install_eagle.py")));assertFalse(Files.exists(out.resolve("bin/eagle")));
        try(var resource=getClass().getResourceAsStream("/org/jlinalg/omics/eagle-runtime-lock.json")){assertNotNull(resource);assertArrayEquals(resource.readAllBytes(),Files.readAllBytes(out.resolve("eagle-runtime-lock.json")));}
        assertEquals(2,call("--export-runtime-setup",out.toString()).code());assertFalse(Files.exists(out.resolve("FINALIZATION_FAILED.md")));
    }
    @Test void helpExplainsCompletionAndFailureContracts() {
        var result=call("--help");assertEquals(0,result.code());assertTrue(result.stdout().contains("COMPLETED.json"));assertTrue(result.stdout().contains("SKIP3"));assertTrue(result.stdout().contains("FAIL2"));
    }
}
