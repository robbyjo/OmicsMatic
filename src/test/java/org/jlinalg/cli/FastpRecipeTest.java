/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FastpRecipeTest {
    @TempDir Path temporary;
    private record Run(int code,String out,String err) { }
    private static Run run(String... args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=FastpToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static Map<String,Object> recipe()throws IOException {return map(SimpleJson.parse(FastpJson.json(FastpRecipe.example())));}
    private FastpRecipe.Spec parse(Map<String,Object> r)throws IOException {return FastpRecipe.parse(FastpJson.json(r).getBytes(StandardCharsets.UTF_8),temporary.resolve("recipe.json"));}
    @ParameterizedTest @ValueSource(strings={"0.22.0","0.23.2","0.25.0","1.0.1"})
    void exactEditionExampleBindsEveryTypedChoiceWithoutOpeningARuntime(String version)throws Exception {
        var r=recipe();r.put("profile","linux-x86_64-fastp-"+version);var spec=parse(r);assertEquals("linux-x86_64-fastp-"+version,spec.profile());assertEquals(FastpParameters.Options.defaults(1),spec.options());assertEquals(FastpRecipe.defaults(),spec.limits());assertEquals(temporary.resolve("read2.fastq"),spec.second());
    }
    @Test void unknownEditionNeverSilentlyResolvesToASupportedPatch()throws Exception {var r=recipe();r.put("profile","linux-x86_64-fastp-0.23");assertThrows(FastpRuntime.Unavailable.class,()->parse(r));}
    @Test void unsupportedCapabilityIsNotSilentlyIgnored()throws Exception {var r=recipe();r.put("umi",true);assertThrows(FastpRuntime.Unavailable.class,()->parse(r));}
    @Test void unknownAndOmittedNestedChoicesFail()throws Exception {
        var r=recipe();map(r.get("options")).put("passThroughCommand","ignored");assertThrows(IOException.class,()->parse(r));var missing=recipe();map(missing.get("policies")).remove("encoding");assertThrows(IOException.class,()->parse(missing));
    }
    @Test void invalidTypesAndCapacityBoundsAreNotCoerced()throws Exception {
        var r=recipe();map(r.get("options")).put("correction","false");assertThrows(IOException.class,()->parse(r));var low=recipe();map(map(map(low.get("limits")).get("audit")).get("sorting")).put("memoryBytes",100L);assertThrows(IOException.class,()->parse(low));
    }
    @Test void pairedOnlyChoicesCannotRunOnASingleFile()throws Exception {var r=recipe();map(r.get("inputs")).remove("read2");map(r.get("options")).put("correction",true);assertThrows(IOException.class,()->parse(r));}
    @Test void replayExpandsDefaultLimitsAndChangesOnlyCapturedInputReferences()throws Exception {
        var r=recipe();r.remove("limits");byte[] raw=FastpJson.json(r).getBytes(StandardCharsets.UTF_8);var spec=FastpRecipe.parse(raw,temporary.resolve("recipe.json"));var pin=new FastpFiles.Pin(10,"a".repeat(64));var id=new FastpFiles.Identity(pin,"key","time");var captures=List.of(new FastpFiles.Capture("read1","/source/a",id,"inputs/read1.fastq.gz"),new FastpFiles.Capture("read2","/source/b",id,"inputs/read2.fastq"));byte[] replay=FastpRecipe.replay(raw,spec,captures);var owned=FastpRecipe.parse(replay,temporary.resolve("recipe-replay.json"));assertEquals(temporary.resolve("processing/inputs/read1.fastq.gz"),owned.first());assertEquals(spec.options(),owned.options());assertEquals(spec.limits(),owned.limits());assertEquals(spec.profile(),owned.profile());
    }
    @Test void largeCapturedFastqPinUsesItsDeclaredInputCapacityInsteadOfBinaryLimit()throws Exception {
        Map<String,Object> pin=Map.of("bytes",4_294_967_296L,"sha256","a".repeat(64));assertEquals(4_294_967_296L,FastpOutputAudit.capturedPin(pin,4_294_967_296L).bytes());assertThrows(IOException.class,()->FastpOutputAudit.capturedPin(pin,67_108_864));assertThrows(IOException.class,()->FastpRuntime.fingerprint(pin));
    }
    @Test void recordedLinuxInputResolutionWorksIndependentlyOfVerifierOperatingSystem()throws Exception {
        assertEquals("/producer/data/reads.fq",FastpRecipe.originalInput("/producer/recipes/source.json","../data/reads.fq"));assertEquals("/data/reads.fq",FastpRecipe.originalInput("/producer/source.json","/data/./reads.fq"));assertThrows(IOException.class,()->FastpRecipe.originalInput("relative/source.json","reads.fq"));
    }
    @Test void publicHelpExampleAndProfilesAreAvailableWithoutInstallation()throws Exception {
        assertEquals(0,run("--help").code());var example=run("--example");assertEquals(0,example.code());assertNotNull(FastpRecipe.parse(example.out().getBytes(StandardCharsets.UTF_8),temporary.resolve("recipe.json")));assertEquals(FastpRuntime.profiles(),SimpleJson.parse(run("--runtime-profiles").out()));
    }
    @Test void missingManagedRuntimeProducesProminentSkipAndPreservesRequest()throws Exception {
        Path request=temporary.resolve("recipe.json");Files.writeString(request,FastpJson.json(recipe()));Path out=temporary.resolve("skipped");var result=run("--recipe",request.toString(),"--home",temporary.resolve("missing-home").toString(),"--setup-report",temporary.resolve("missing-setup").toString(),"--out",out.toString());assertEquals(3,result.code(),result.err());assertTrue(result.err().contains("SKIP3"));assertTrue(result.err().contains("Required action"));assertTrue(Files.exists(out.resolve("recipe-request.json")));assertTrue(Files.exists(out.resolve("FAILURE.md")));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void existingResultTreeAndCompletionAreNeverOverwritten()throws Exception {
        Path out=Files.createDirectory(temporary.resolve("existing"));Files.writeString(out.resolve("COMPLETED.json"),"prior-owner\n");var result=run("--recipe",temporary.resolve("missing-recipe").toString(),"--out",out.toString());assertEquals(2,result.code());assertEquals("prior-owner\n",Files.readString(out.resolve("COMPLETED.json")));try(var files=Files.list(out)){assertEquals(1,files.count());}
    }
    @Test void setupExportIsSelfContainedBytePinnedAndDoesNotInstallOrAdmitAnalysis()throws Exception {
        Path out=temporary.resolve("export");var result=run("--export-runtime-setup",out.toString());assertEquals(0,result.code(),result.err());assertEquals("e77621208a1fb878a41e1f6054f4623865da5607c4c3cbd172b5063fcd9af84b",FastpFiles.pin(out.resolve("tools/literature/install_fastp.py"),1_048_576,()->{}).sha256());assertTrue(Files.exists(out.resolve("src/main/resources/org/jlinalg/omics/fastp-MIT-NOTICE.txt")));assertEquals("EXPORTED_SETUP_NOT_RUN",FastpRuntime.object(FastpFiles.json(out.resolve("export.json"),65536)).get("status"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertEquals(2,run("--export-runtime-setup",out.toString()).code());
    }
    @Test void duplicateAndUnknownCommandOptionsFailBeforeCreatingAResult()throws Exception {
        Path out=temporary.resolve("not-created");assertEquals(2,run("--recipe","a","--recipe","b","--out",out.toString()).code());assertFalse(Files.exists(out));assertEquals(2,run("--shell-command","ignored").code());
    }
    @Test void verificationFailureLeavesTheSourceBundleUntouched()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("forged"));Files.writeString(root.resolve("COMPLETED.json"),"{}\n");var result=run("--verify",root.toString());assertEquals(2,result.code());assertTrue(result.err().contains("FAIL2"));assertEquals("{}\n",Files.readString(root.resolve("COMPLETED.json")));assertFalse(Files.exists(root.resolve("FAILURE.json")));
    }
}
