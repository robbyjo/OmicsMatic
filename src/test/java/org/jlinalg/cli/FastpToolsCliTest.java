/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class FastpToolsCliTest {
    @TempDir static Path temporary;
    static Path fixture;
    private record Run(int code,String out,String err) { }
    @BeforeAll static void stage()throws IOException {fixture=temporary.resolve("public-controls.zip");try(var in=FastpToolsCliTest.class.getResourceAsStream("fastp-workflow/public-controls.zip")){assertNotNull(in);Files.copy(in,fixture);}}
    private static Run run(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=FastpToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static Path extract(String name)throws IOException {
        Path root=Files.createTempDirectory(temporary,"bundle-").toRealPath();String prefix=name+"/result/";
        try(var z=new ZipFile(fixture.toFile())){for(var e:z.stream().filter(e->e.getName().startsWith(prefix)).toList()){String member=FastpFiles.member(e.getName().substring(prefix.length()));Path target=root.resolve(member);Files.createDirectories(target.getParent());try(var in=z.getInputStream(e)){Files.copy(in,target);}}}assertTrue(Files.exists(root.resolve("COMPLETED.json")));return root;
    }
    private static void write(Path path,Object value)throws IOException {Files.writeString(path,FastpJson.json(value)+"\n");}
    private static Map<String,Object> json(Path path)throws IOException {return map(FastpFiles.json(path,16_777_216));}
    private static void reseal(Path root)throws IOException {
        var manifest=json(root.resolve("manifest.json"));var limits=FastpRecipe.limits(manifest.get("limits"));var current=FastpBundle.inventory(root,limits.stage(),()->{});var files=new java.util.TreeMap<>(current.files());files.remove("manifest.json");files.remove("COMPLETED.json");manifest.put("files",files);manifest.put("directories",new java.util.TreeSet<>(current.directories()));write(root.resolve("manifest.json"),manifest);var marker=json(root.resolve("COMPLETED.json"));marker.put("manifest",FastpFiles.pin(root.resolve("manifest.json"),16_777_216,()->{}));write(root.resolve("COMPLETED.json"),marker);
    }
    private static void rejected(Path root){var result=run("--verify",root.toString());assertEquals(2,result.code(),result.out());assertTrue(result.err().contains("FAIL2"),result.err());assertTrue(result.err().contains("Required action"));assertFalse(Files.exists(root.resolve("FAILURE.json")));}
    @TestFactory Stream<DynamicTest> all57ActualPublicOriginalWorkflowsVerifyPortably()throws Exception {
        List<?> names;try(var z=new ZipFile(fixture.toFile());var in=z.getInputStream(z.getEntry("CASES.json"))){names=FastpMetrics.array(FastpRuntime.object(SimpleJson.parse(FastpFiles.utf8(in.readAllBytes()))).get("cases"));}assertEquals(57,names.size());
        return names.stream().map(o->{String name=(String)o;return DynamicTest.dynamicTest(name,()->{Path root=extract(name);var result=run("--verify",root.toString());assertEquals(0,result.code(),result.err());assertTrue(result.out().contains("native population/report/envelope replay"));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("Required action"));});});
    }
    @Test void dispatcherExposesThePublicTypedCommand()throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"fastp-tools","--help"},new PrintStream(out),new PrintStream(err)));assertTrue(out.toString(StandardCharsets.UTF_8).contains("--setup-report"));
    }
    @Test void relocatedBundleNeedsNeitherOriginalInputsNorInstalledRuntime()throws Exception {Path root=extract("0.23.2/pe-store-orphans"),moved=temporary.resolve("relocated-without-producer");Files.move(root,moved);assertEquals(0,run("--verify",moved.toString()).code());}
    @Test void changedCycleAndKmerReportsFailEvenAfterEveryManifestHashIsUpdated()throws Exception {
        for(String field:List.of("quality_curves","kmer_count")){
            Path root=extract("0.23.2/se-default"),path=root.resolve("processing/original/fastp.json");var report=json(path);var detail=map(report.get("read1_before_filtering"));
            if(field.equals("quality_curves")){var curves=map(detail.get(field));var mean=new ArrayList<Object>((List<?>)curves.get("mean"));mean.set(0,((Number)mean.get(0)).doubleValue()+1);curves.put("mean",mean);}else {var kmers=map(detail.get(field));String key=kmers.keySet().iterator().next();kmers.put(key,((Number)kmers.get(key)).longValue()+1);}
            write(path,report);reseal(root);rejected(root);
        }
    }
    @Test void unchangedOutputMultisetCannotHideWrongOriginalHeaderAssignments()throws Exception {
        Path root=extract("0.23.2/se-default"),path=root.resolve("processing/original/read1.fastq");var lines=new ArrayList<>(Files.readAllLines(path));assertTrue(lines.size()>=8);for(int i:new int[]{1,3}){String value=lines.get(i);lines.set(i,lines.get(i+4));lines.set(i+4,value);}Files.writeString(path,String.join("\n",lines)+"\n");reseal(root);rejected(root);
    }
    @Test void mateOrderCannotBeChangedDespitePreservedCompletePopulations()throws Exception {
        Path root=extract("0.23.2/pe-overlap-correction"),path=root.resolve("processing/original/read2.fastq");var lines=new ArrayList<>(Files.readAllLines(path));for(int i=0;i<4;i++){String value=lines.get(i);lines.set(i,lines.get(i+4));lines.set(i+4,value);}Files.writeString(path,String.join("\n",lines)+"\n");reseal(root);rejected(root);
    }
    @Test void missingOrphanFileAndExtraEmptyDirectoryCannotBecomeValidByResealing()throws Exception {
        Path missing=extract("0.23.2/pe-store-orphans");Files.delete(missing.resolve("processing/original/unpaired1.fastq"));reseal(missing);rejected(missing);
        Path extra=extract("0.23.2/se-default");Files.createDirectory(extra.resolve("unexpected-empty"));reseal(extra);rejected(extra);
    }
    @Test void changedRecipeAndReplayMustStillMatchTheOriginalProcessingRequest()throws Exception {
        Path root=extract("0.23.2/se-default"),path=root.resolve("recipe-replay.json");var recipe=json(path);map(recipe.get("options")).put("threads",2L);write(path,recipe);reseal(root);rejected(root);
    }
    @Test void originalRecipePathBindingCannotBeReassignedToADifferentInput()throws Exception {
        Path root=extract("0.23.2/se-default"),path=root.resolve("recipe-request.json");var recipe=json(path);map(recipe.get("inputs")).put("read1","unrelated.fastq");write(path,recipe);var receipt=json(root.resolve("recipe-capture.json"));map(receipt.get("identity")).put("pin",FastpFiles.pin(path,FastpRecipe.MAXIMUM_BYTES,()->{}));write(root.resolve("recipe-capture.json"),receipt);reseal(root);rejected(root);
    }
    @Test void requiredWarningsAndScopeClaimsAreVerifiedSemantically()throws Exception {
        Path root=extract("0.23.2/pe-dedup");Files.writeString(root.resolve("WARNINGS.md"),"All absent reads are definitely duplicates.\n");reseal(root);rejected(root);
        Path forged=extract("0.23.2/se-default");var marker=json(forged.resolve("COMPLETED.json"));marker.put("whole_paper_reproduced",true);write(forged.resolve("COMPLETED.json"),marker);rejected(forged);
    }
    @Test void retainedDispositionAndIndexFilesAreComparedToNativeReplay()throws Exception {
        for(String name:List.of("audit/read-dispositions.tsv","audit/complete-index.tsv","audit/sort/sorted.tsv")){Path root=extract("0.23.2/se-default");Files.writeString(root.resolve(name),"invented\n");reseal(root);rejected(root);}
    }
    @Test void runtimeSetupEvidenceAndCapturedLicenseRemainBoundToTheExactEdition()throws Exception {
        Path root=extract("0.23.2/se-default");Files.writeString(root.resolve("runtime-evidence/LICENSE"),"unrelated terms\n");reseal(root);rejected(root);
        Path incomplete=extract("0.23.2/se-default");Files.delete(incomplete.resolve("runtime-evidence/setup/processed.fq"));reseal(incomplete);rejected(incomplete);
    }
    @Test void sourceFailureCannotBePromotedByARehashedCompletionMarker()throws Exception {
        Path root=extract("0.23.2/se-default");Files.writeString(root.resolve("processing/FAILURE.json"),"{}\n");reseal(root);rejected(root);
    }
    @Test void validZeroMainReadQcResultExplicitlySkipsDependentAnalyses()throws Exception {
        Path root=extract("0.23.2/se-all-filtered");assertEquals(0,run("--verify",root.toString()).code());assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("NO_MAIN_PASSING_READS"));assertEquals(Boolean.FALSE,json(root.resolve("session.json")).get("downstream_usable"));
    }
}
