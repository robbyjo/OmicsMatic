/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TpmCalculatorWorkflowTest {
    @TempDir Path temp;
    Path fixture(String name,boolean edge)throws Exception {
        var original=new TpmCalculatorOriginalTest();original.temp=Files.createDirectory(temp.resolve("fixture"));return original.extract(name,edge);
    }
    Path request(Path fixture)throws Exception {
        Map<?,?> c=NextflowFiles.object(SimpleJson.parse(Files.readString(fixture.resolve("CASE.json"))));var samples=new ArrayList<Object>();
        for(Object name:NextflowFiles.list(c.get("sample_order")))samples.add(Map.of("name",name,"bam",fixture.resolve("bams/"+name+".bam").toString()));
        Map<String,Object> body=Map.of("schema",1,"profile",TpmCalculatorRequest.PROFILE,"annotation",fixture.resolve("annotation.gtf").toString(),"samples",samples,
            "options",Map.of("gene_key",c.containsKey("gene_key")?c.get("gene_key"):"gene_id","transcript_key",c.containsKey("transcript_key")?c.get("transcript_key"):"transcript_id","intron_cutoff",c.get("cutoff"),"minimum_mapq",c.get("mapq"),"minimum_overlap",c.get("overlap"),
                "properly_paired_only",c.get("proper"),"extended_output",c.containsKey("extended")?c.get("extended"):true,"all_features",c.containsKey("all")?c.get("all"):true),
            "limits",Map.of("input_bytes",100000000L,"bundle_bytes",100000000L,"files",2000,"wall_seconds",60,"heap_mib",256));
        Path p=temp.resolve("request.json");Files.writeString(p,NextflowJson.json(body)+"\n",StandardCharsets.UTF_8);return p;
    }
    static byte[] extraArchive()throws Exception {
        try(var input=TpmCalculatorWorkflowTest.class.getResourceAsStream("/org/jlinalg/sequence/tpmcalculator-original-workflow.zip")) {
            assertNotNull(input);byte[] b=input.readAllBytes();assertEquals("7c1012bbca813363d381df829e47857942c1b3c1429f56e5732efce706ad8594",NextflowFiles.sha(b));return b;
        }
    }
    static String extra(String name)throws Exception {
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(extraArchive()))) {
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals(name))return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }throw new IOException("Missing original workflow control "+name);
    }
    static Stream<String> extraCases()throws Exception {
        List<?> cases=(List<?>)SimpleJson.parse(extra("CASES.json"));assertEquals(7,cases.size());return cases.stream().map(c->(String)((Map<?,?>)c).get("name"));
    }
    Path extraFixture(String name)throws Exception {
        Path root=Files.createDirectory(temp.resolve("fixture"));Path folder=Files.createDirectory(root.resolve(name));
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(extraArchive()))) {
            for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().startsWith(name+"/")) {
                Path file=root.resolve(e.getName()).normalize();assertTrue(file.startsWith(folder));if(e.isDirectory())Files.createDirectories(file);else{Files.createDirectories(file.getParent());Files.copy(zip,file);}
            }
        }return folder;
    }
    @ParameterizedTest @MethodSource("extraCases")
    void originalCallerOptionsAndRawControlIdentifiersMatchCompleteSourceTables(String name)throws Exception {
        Path f=extraFixture(name),r=request(f),out=temp.resolve("analysis");
        assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));
        Map<?,?> c=NextflowFiles.object(SimpleJson.parse(Files.readString(f.resolve("CASE.json"))));int index=0;
        for(Object sample:NextflowFiles.list(c.get("sample_order"))) {
            String member=String.format(java.util.Locale.ROOT,"products/sample-%06d/",++index);
            for(String file:Boolean.TRUE.equals(c.get("extended"))?List.of("genes.out","genes.ent","genes.uni","transcripts.out","transcripts.ent"):List.of("genes.out","genes.ent","genes.uni"))
                assertEquals(-1L,Files.mismatch(f.resolve("original-main/"+sample+"_"+file),out.resolve(member+file)),name+" "+file);
        }
        assertEquals(0,TpmCalculatorCli.run(new String[]{"verify","--bundle",out.toString(),"--out",temp.resolve("verify").toString()}));
        if(name.equals("raw-control-identifiers"))assertTrue(Files.readString(out.resolve("WARNINGS.json")).contains("SOURCE_TABLE_RAW_CONTROL_IDENTIFIERS"));
    }
    @Test void allFiftyThousandOriginalOstreamRoundingAndExponentControlsMatch()throws Exception {
        int rows=0;for(String line:extra("original-number.stdout").split("\n")) {
            String[] fields=line.split("\t");double value=Double.longBitsToDouble(Long.parseUnsignedLong(fields[0],16));
            assertEquals(fields[1],TpmCalculatorProducts.sourceNumber(value),"Original defaultfloat raw bits "+fields[0]);rows++;
        }assertEquals(50049,rows);
        for(String file:List.of("OriginalNumber.cpp","make_workflow_controls.py"))assertEquals(Files.readString(Path.of("tools/literature/tpmcalculator_validation/"+file)),extra(file));
    }
    @SuppressWarnings("unchecked") Map<String,Object> edit(Path file)throws Exception{return (Map<String,Object>)SimpleJson.parse(Files.readString(file));}
    void save(Path p,Object value)throws Exception{Files.writeString(p,NextflowJson.json(value)+"\n",StandardCharsets.UTF_8);}
    void assertFailure(Path out,int code)throws Exception {
        assertFalse(Files.exists(out.resolve("COMPLETED.json")));Map<?,?> f=NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("FAILURE.json")));
        assertEquals(Boolean.FALSE,f.get("result_admitted"));assertTrue(f.get("status").toString().startsWith(code==3?"SKIP3":"FAIL2"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("NEW"));
    }
    @Test void missingBamSkipsBeforeWorkerAndNamesTheRequiredAction()throws Exception {
        Path f=fixture("flags",false),r=request(f);Files.move(f.resolve("bams/one.bam"),f.resolve("retained-one.bam"));Path out=temp.resolve("skipped");
        assertEquals(3,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,3);assertFalse(Files.exists(out.resolve("worker")));
    }
    @Test void invalidJsonAndUnknownOptionsFailWithoutAnAnalysis()throws Exception {
        Path f=fixture("flags",false),r=request(f);Map<String,Object> doc=edit(r);Map<String,Object> options=TpmCalculatorRecords.object(NextflowFiles.object(doc.get("options")));options.put("stranded",true);doc.put("options",options);save(r,doc);
        Path out=temp.resolve("unknown");assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,2);assertFalse(Files.exists(out.resolve("worker")));
        Files.writeString(r,"{\"schema\":1,\"schema\":1}\n");out=temp.resolve("duplicate");assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,2);
    }
    @Test void existingOutputIsPreservedByteForByte()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=Files.createDirectory(temp.resolve("already-there"));Files.writeString(out.resolve("original"),"preserve");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertEquals("preserve",Files.readString(out.resolve("original")));try(var paths=Files.list(out)){assertEquals(1,paths.count());}
    }
    @Test void malformedAnnotationFailsWithCompleteNativeErrorStreams()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("bad-annotation");Files.writeString(f.resolve("annotation.gtf"),"invalid\n");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,2);
        assertTrue(Files.readString(out.resolve("worker/stderr.bin")).contains("nine nonempty"));assertFalse(Files.exists(out.resolve("worker/RETURN.json")));
    }
    @Test void recordingFailureAfterCoreReturnsCannotAdmitPartialProducts()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("recording-failure");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()},(phase,dir)->{if(phase.equals("BEFORE_WORKER"))Files.writeString(dir.resolve("products"),"block output creation");}));
        assertFailure(out,2);assertTrue(Files.readString(out.resolve("worker/stdout.bin")).contains("COMPUTATION_RETURNED"));assertTrue(Files.readString(out.resolve("worker/stderr.bin")).contains("computed=true"));
    }
    @Test void parentFinalizationFailureAfterCompleteWorkerReturnStaysUnadmitted()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("finalization-failure");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()},(phase,dir)->{if(phase.equals("BEFORE_COMPLETE"))Files.writeString(dir.resolve("MANIFEST.json"),"retained obstruction");}));
        assertFailure(out,2);assertTrue(Files.isRegularFile(out.resolve("worker/RETURN.json")));assertEquals("retained obstruction",Files.readString(out.resolve("MANIFEST.json")));
    }
    @Test void failureAfterCompletionPromotionRevokesTheMarkerWithoutDeletingEvidence()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("promotion-failure");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()},(phase,dir)->{if(phase.equals("AFTER_COMPLETE"))throw new IOException("actual post-promotion recording guard");}));
        assertFailure(out,2);try(var paths=Files.list(out)){assertTrue(paths.anyMatch(p->p.getFileName().toString().startsWith("COMPLETED-UNADMITTED-")));}
        assertTrue(Files.isRegularFile(out.resolve("products/state-raw.jsonl")));
    }
    @Test void originalAndCapturedInputMutationsAfterWorkerReturnAreRejected()throws Exception {
        Path f=fixture("flags",false),r=request(f);
        for(boolean original:new boolean[]{true,false}) {
            Path out=temp.resolve(original?"source-mutation":"captured-mutation");byte[] before=Files.readAllBytes(f.resolve("annotation.gtf"));
            assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()},(phase,dir)->{
                if(phase.equals("AFTER_WORKER"))Files.writeString(original?f.resolve("annotation.gtf"):dir.resolve("inputs/annotation.gtf"),"#changed\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);
            }));assertFailure(out,2);Files.write(f.resolve("annotation.gtf"),before);
        }
    }
    Path copyBundle(Path source,String name)throws Exception {
        Path target=Files.createDirectory(temp.resolve(name));try(var paths=Files.walk(source)){
            for(Path p:paths.filter(p->!p.equals(source)).toList()){Path q=target.resolve(source.relativize(p));if(Files.isDirectory(p))Files.createDirectories(q);else Files.copy(p,q);}
        }return target;
    }
    void reseal(Path bundle)throws Exception {
        Map<String,Object> done=edit(bundle.resolve("COMPLETED.json"));Files.move(bundle.resolve("MANIFEST.json"),temp.resolve("prior-manifest-"+java.util.UUID.randomUUID()+".json"));
        var r=TpmCalculatorRequest.read(TpmCalculatorRecords.read(bundle.resolve("REQUEST.json")),true);TpmCalculatorRecords.manifest(bundle,r.limits());done.put("manifest_sha256",TpmCalculatorRecords.pin(bundle.resolve("MANIFEST.json"),r.limits().bundleBytes()).sha256());save(bundle.resolve("COMPLETED.json"),done);
    }
    @Test void resealedContradictoryWorkerRecordCannotBeVerified()throws Exception {
        Path f=fixture("flags",false),r=request(f),good=temp.resolve("good");assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",good.toString()}));
        Path bad=copyBundle(good,"bad");Map<String,Object> exit=edit(bad.resolve("worker/EXIT.json"));exit.put("exit_code",2);save(bad.resolve("worker/EXIT.json"),exit);reseal(bad);
        Path out=temp.resolve("verify");assertEquals(2,TpmCalculatorCli.run(new String[]{"verify","--bundle",bad.toString(),"--out",out.toString()}));assertFailure(out,2);assertFalse(Files.exists(out.resolve("worker")));
    }
    @Test void resealedWrongResultsRequireFreshComputationAndAreRejected()throws Exception {
        Path f=fixture("flags",false),r=request(f),good=temp.resolve("good");assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",good.toString()}));
        Path bad=copyBundle(good,"bad");Files.writeString(bad.resolve("products/sample-000001/genes.out"),"wrong scientific row\n",StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.APPEND);reseal(bad);
        Path out=temp.resolve("verify");assertEquals(2,TpmCalculatorCli.run(new String[]{"verify","--bundle",bad.toString(),"--out",out.toString()}));assertFailure(out,2);assertTrue(Files.isRegularFile(out.resolve("worker/RETURN.json")));
        assertEquals("SEMANTIC_REPLAY_COMPARISON",NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("FAILURE.json"))).get("phase"));
    }
    @Test void resealedRemovedWarningsAndInventedPaperClaimsCannotBeAdmitted()throws Exception {
        Path f=fixture("flags",false),r=request(f),good=temp.resolve("good");assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",good.toString()}));
        Path bad=copyBundle(good,"warnings-removed");Files.writeString(bad.resolve("WARNINGS.md"),"all clear\n");reseal(bad);Path verify=temp.resolve("verify-warnings");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"verify","--bundle",bad.toString(),"--out",verify.toString()}));assertFailure(verify,2);
        bad=copyBundle(good,"paper-claim");Map<String,Object> completion=edit(bad.resolve("COMPLETED.json"));completion.put("paper_reproduced",true);save(bad.resolve("COMPLETED.json"),completion);verify=temp.resolve("verify-claim");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"verify","--bundle",bad.toString(),"--out",verify.toString()}));assertFailure(verify,2);
    }
    @Test void duplicateSampleNamesAndInexactIntegerChoicesFailBeforeCounting()throws Exception {
        Path f=fixture("flags",false),r=request(f);String originalText=Files.readString(r);Map<String,Object> original=edit(r);var samples=new ArrayList<Object>(NextflowFiles.list(original.get("samples")));samples.add(samples.get(0));original.put("samples",samples);save(r,original);
        Path out=temp.resolve("duplicate");assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,2);
        Path r2=temp.resolve("inexact.json");String inexact=originalText.replace("\"minimum_overlap\":8","\"minimum_overlap\":8.0");assertNotEquals(inexact,originalText);Files.writeString(r2,inexact);out=temp.resolve("inexact");
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r2.toString(),"--out",out.toString()}));assertFailure(out,2);
    }
    @Test void insufficientFileCapacityStopsOnlyItsOwnedWorkerAndAdmitsNoResult()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("capacity");Map<String,Object> d=edit(r);Map<String,Object> limits=TpmCalculatorRecords.object(NextflowFiles.object(d.get("limits")));limits.put("files",5);d.put("limits",limits);save(r,d);
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,2);
        assertEquals(Boolean.FALSE,NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("worker/EXIT.json"))).get("owned_worker_alive"));
    }
    @Test void declaredWallLimitTerminatesNativeGeneModellingAndRetainsTheProcessRecord()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("timeout");
        var gtf=new StringBuilder();for(int k=0;k<60000;k++)gtf.append("chr1\tcontrol\texon\t").append(100+k*50).append('\t').append(120+k*50).append("\t.\t+\t.\tgene_id \"g").append(k).append("\"; transcript_id \"t").append(k).append("\";\n");
        Files.writeString(f.resolve("annotation.gtf"),gtf);Map<String,Object> d=edit(r);Map<String,Object> limits=TpmCalculatorRecords.object(NextflowFiles.object(d.get("limits")));limits.put("wall_seconds",1);d.put("limits",limits);save(r,d);
        assertEquals(2,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));assertFailure(out,2);
        assertTrue(Files.readString(out.resolve("FAILURE.json")).contains("wall_seconds"));assertEquals(Boolean.FALSE,NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("worker/EXIT.json"))).get("owned_worker_alive"));
    }
    @Test void savedVerificationCannotAlterItsProtectedBundle()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("analysis");assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));
        byte[] completion=Files.readAllBytes(out.resolve("COMPLETED.json"));
        assertEquals(2,TpmCalculatorCli.run(new String[]{"verify","--bundle",out.toString(),"--out",out.resolve("nested").toString()}));assertArrayEquals(completion,Files.readAllBytes(out.resolve("COMPLETED.json")));assertFalse(Files.exists(out.resolve("nested")));
    }
    @Test void changedNativeProfileRequiresTheRecordedArtifactAndSkipsReplay()throws Exception {
        Path f=fixture("flags",false),r=request(f),good=temp.resolve("good");assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",good.toString()}));
        Path bad=copyBundle(good,"different-engine");Map<String,Object> engine=edit(bad.resolve("ENGINE.json"));Map<String,Object> implementation=TpmCalculatorRecords.object(NextflowFiles.object(engine.get("implementation")));implementation.put("original_source_commit","different");engine.put("implementation",implementation);save(bad.resolve("ENGINE.json"),engine);reseal(bad);
        Path verify=temp.resolve("verify");assertEquals(3,TpmCalculatorCli.run(new String[]{"verify","--bundle",bad.toString(),"--out",verify.toString()}));assertFailure(verify,3);assertFalse(Files.exists(verify.resolve("worker")));
    }
    @Test void completeCapturedInputsRunAndFreshReplayDoNotNeedOriginalPaths()throws Exception {
        Path f=fixture("flags",false),r=request(f),out=temp.resolve("analysis"),verify=temp.resolve("verified");
        assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));
        assertTrue(Files.isRegularFile(out.resolve("COMPLETED.json")));assertFalse(Files.exists(out.resolve("FAILURE.json")));
        for(String file:List.of("genes.out","genes.ent","genes.uni","transcripts.out","transcripts.ent"))
            assertEquals(-1L,Files.mismatch(f.resolve("original-main/one_"+file),out.resolve("products/sample-000001/"+file)),file);
        Files.move(f.resolve("annotation.gtf"),f.resolve("original-annotation-retained.gtf"));Files.move(f.resolve("bams/one.bam"),f.resolve("original-bam-retained.bam"));
        assertEquals(0,TpmCalculatorCli.run(new String[]{"verify","--bundle",out.toString(),"--out",verify.toString()}));
        var completion=NextflowFiles.object(TpmCalculatorRecords.read(verify.resolve("COMPLETED.json")));
        assertEquals(Boolean.FALSE,completion.get("result_admitted"));assertEquals(Boolean.TRUE,completion.get("saved_replay_verified"));
    }
    @ParameterizedTest @MethodSource("org.jlinalg.cli.TpmCalculatorOriginalTest#cases")
    void everyCompleteOriginalCasePassesThePublicWorkflowAndOriginalTables(String name,boolean edge)throws Exception {
        Path f=fixture(name,edge),r=request(f),out=temp.resolve("analysis");
        assertEquals(0,TpmCalculatorCli.run(new String[]{"run","--request",r.toString(),"--out",out.toString()}));
        Map<?,?> c=NextflowFiles.object(SimpleJson.parse(Files.readString(f.resolve("CASE.json"))));int index=0;
        for(Object sample:NextflowFiles.list(c.get("sample_order"))) {
            String member=String.format(java.util.Locale.ROOT,"products/sample-%06d/",++index);
            for(String file:List.of("genes.out","genes.ent","genes.uni","transcripts.out","transcripts.ent"))
                assertEquals(-1L,Files.mismatch(f.resolve("original-main/"+sample+"_"+file),out.resolve(member+file)),name+" "+file);
        }
        assertFalse(Files.exists(out.resolve("FAILURE.json")));assertTrue(Files.isRegularFile(out.resolve("COMPLETED.json")));
    }
}
