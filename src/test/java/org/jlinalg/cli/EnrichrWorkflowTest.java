/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.enrichr.EnrichrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnrichrWorkflowTest {
    @TempDir Path temporary;
    record Run(int code,String out,String err) { }
    static final class Source implements EnrichrClient.Transport {
        final List<EnrichrClient.Request> requests=new ArrayList<>();
        final Map<String,Integer> downloads=new HashMap<>();
        boolean unavailable,changedLibrary,empty,echoChange,unknownHit,interrupt,partialTimeout;
        String p="1e-400",odds="1.25",score="2.50";
        @Override public EnrichrClient.Reply send(EnrichrClient.Request request)throws IOException,InterruptedException{
            requests.add(request);String path=request.uri().getPath();String body;
            if(interrupt)throw new InterruptedException("controlled workflow interruption");
            if(partialTimeout)return new EnrichrClient.Reply(200,Map.of(),utf8("partial-timeout"),EnrichrClient.TransferState.UNAVAILABLE,"controlled timeout");
            if(unavailable)return new EnrichrClient.Reply(503,Map.of(),utf8("maintenance"),true,null);
            if(path.endsWith("datasetStatistics"))body="{\"statistics\":[{\"libraryName\":\"LIB A\"},{\"libraryName\":\"LIB-B\"}]}";
            else if(path.endsWith("geneSetLibrary")){
                String name=parameter(request.uri().getRawQuery(),"libraryName");int number=downloads.merge(name,1,Integer::sum);
                body="term \"α\"\tdefinition\tTP53\tBRCA1\t\nother\tdesc\tRAD51\n";
                if(changedLibrary&&number>1)body+="changed\tdesc\tATM\n";
            }else if(path.endsWith("addList"))body="{\"userListId\":"+(path.contains("speedrichr")?202:101)+",\"shortId\":\"synthetic\"}";
            else if(path.endsWith("addbackground"))body="{\"backgroundid\":\"bg\"}";
            else if(path.endsWith("view"))body=echoChange?"{\"genes\":[\"BRCA1\",\"STAT1\"]}":"{\"genes\":[\"BRCA1\",\"TP53\"]}";
            else if(path.endsWith("enrich")||path.endsWith("backgroundenrich")){
                String query=request.method().equals("POST")?new String(request.body(),StandardCharsets.UTF_8):request.uri().getRawQuery();
                String name=parameter(query,"backgroundType");
                body="{"+NextflowJson.json(name)+":"+(empty?"[]":"[[1,\"term \\\"α\\\"\","+p+","+odds+","+score+",[\""+(unknownHit?"STAT1":"TP53")+"\",\"BRCA1\"],0.2,0,0]]")+"}";
            }else throw new IOException("Unexpected synthetic endpoint "+path);
            return new EnrichrClient.Reply(200,Map.of("content-type",List.of("application/json")),utf8(body),true,null);
        }
        private static String parameter(String form,String name)throws IOException{
            for(String pair:form.split("&")){String[] p=pair.split("=",2);if(URLDecoder.decode(p[0],StandardCharsets.UTF_8).equals(name))return URLDecoder.decode(p[1],StandardCharsets.UTF_8);}
            throw new IOException("Missing form field "+name);
        }
    }
    private static byte[] utf8(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private Path workspace()throws IOException{
        String retained=System.getProperty("omicsmatic.enrichr.workflow.test_root");
        if(retained==null)return temporary;
        Path root=Path.of(retained).resolve("case-"+UUID.randomUUID());Files.createDirectories(root);return root;
    }
    private static Map<String,Object> request(Path root,boolean custom,int libraries)throws IOException{
        Files.writeString(root.resolve("query.txt"),"TP53\nBRCA1\n",StandardCharsets.UTF_8);
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("out",root.resolve("result").toString());
        r.put("service_profile",EnrichrWorkflowContracts.PROFILE);r.put("operation","enrichment");r.put("allow_upload",true);
        r.put("libraries",libraries==1?List.of("LIB A"):List.of("LIB A","LIB-B"));r.put("query",Map.of("file","query.txt","format","lines"));
        if(custom){Files.writeString(root.resolve("background.txt"),"TP53\nBRCA1\nRAD51\n");r.put("background",Map.of("kind","explicit","file","background.txt","format","lines"));r.put("allow_unobserved_custom_normalization",true);}
        else r.put("background",Map.of("kind","server-default"));
        r.put("limits",Map.of("timeout_seconds",2,"request_interval_millis",0,"maximum_output_bytes",10000000));return r;
    }
    private Run run(Path root,Map<String,Object> request,Source source)throws IOException{
        Path path=root.resolve("request-"+UUID.randomUUID()+".json");Files.write(path,EnrichrWorkflowFiles.json(request));
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=EnrichrWorkflowCli.run(new String[]{"--request",path.toString()},new PrintStream(out),new PrintStream(err),settings->new EnrichrClient(settings,source),"injected-fixture");
        return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private Run verify(Path bundle){
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=EnrichrWorkflowCli.run(new String[]{"--verify",bundle.toString()},new PrintStream(out),new PrintStream(err),settings->{throw new AssertionError("Offline verify must not create a live client");},"injected-fixture");
        return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private static Map<String,Object> read(Path path)throws IOException{return EnrichrWorkflowFiles.read(path,2000000);}
    private static void reseal(Path root)throws IOException{
        var tree=EnrichrWorkflowFiles.inventory(root);
        byte[] manifest=EnrichrWorkflowFiles.json(Map.of("schema_version",1,"profile",EnrichrWorkflowContracts.PROFILE,"files",tree.files(),"directories",tree.directories()));
        Files.write(root.resolve("manifest.json"),manifest);
        Files.write(root.resolve("COMPLETED.json"),EnrichrWorkflowFiles.json(Map.of("schema_version",1,"profile",EnrichrWorkflowContracts.PROFILE,"manifest_sha256",EnrichrWorkflowFiles.sha(manifest),"authentication",false)));
    }
    @Test void standardMultiLibraryRunIsCompleteAndOfflineVerifyPreservesAllBytes()throws Exception{
        Path root=workspace();var r=request(root,false,2);var source=new Source();var run=run(root,r,source);assertEquals(0,run.code(),run.err());
        Path result=root.resolve("result");assertTrue(Files.isRegularFile(result.resolve("COMPLETED.json")));assertEquals(9,source.requests.size());
        assertTrue(Files.readString(result.resolve("terms.tsv")).contains("\t1e-400\t"));
        assertTrue(Files.readString(result.resolve("terms.tsv")).contains("\"term \"\"α\"\"\""));
        assertTrue(Files.readString(result.resolve("results/001.json")).contains("\"p_value_token\":\"1e-400\""));
        assertEquals(2L,read(result.resolve("summary.json")).get("reported_terms"));
        var before=EnrichrWorkflowFiles.inventory(result);var verified=verify(result);assertEquals(0,verified.code(),verified.err());
        assertEquals(before,EnrichrWorkflowFiles.inventory(result));assertEquals(9,source.requests.size());
    }
    @Test void customBackgroundUsesPostAndDeclaresUnobservedNormalization()throws Exception{
        Path root=workspace();var r=request(root,true,1);var source=new Source();var run=run(root,r,source);assertEquals(0,run.code(),run.err());
        assertTrue(run.err().contains("effective populations"));
        var posts=source.requests.stream().filter(p->p.uri().getPath().endsWith("backgroundenrich")).toList();
        assertEquals(1,posts.size());assertEquals("POST",posts.get(0).method());
        var status=read(root.resolve("result/analysis-status.json"));
        assertEquals(Boolean.TRUE,((Map<?,?>)status.get("scientific_flags")).get("custom_normalization_unobserved"));
        assertEquals(0,verify(root.resolve("result")).code());
    }
    @Test void libraryResourceModeUploadsNoGenesAndPreservesEmptyFields()throws Exception{
        Path root=workspace();var r=request(root,false,2);r.put("operation","library-download");r.put("allow_upload",false);r.remove("query");r.remove("background");
        var source=new Source();var run=run(root,r,source);assertEquals(0,run.code(),run.err());
        assertTrue(source.requests.stream().allMatch(p->p.method().equals("GET")));assertEquals(3,source.requests.size());
        assertEquals(0L,read(root.resolve("result/summary.json")).get("reported_terms"));
        assertTrue(Files.readString(root.resolve("result/memberships.tsv")).contains("1\t1\t3\t\n"));
        assertEquals(0,verify(root.resolve("result")).code());
    }
    @Test void replayUsesSavedProtocolInNewBundleWithoutUploadsOrParentMutation()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();var original=run(root,r,source);assertEquals(0,original.code(),original.err());
        Path parent=root.resolve("result");var before=EnrichrWorkflowFiles.inventory(parent);int sent=source.requests.size();
        var control=Map.<String,Object>of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",root.resolve("replayed").toString());
        var replay=run(root,control,source);assertEquals(0,replay.code(),replay.err());assertEquals(sent,source.requests.size());assertEquals(before,EnrichrWorkflowFiles.inventory(parent));
        Path child=root.resolve("replayed");assertEquals(Boolean.FALSE,read(child.resolve("execution.json")).get("network_executed"));
        assertArrayEquals(Files.readAllBytes(parent.resolve("terms.tsv")),Files.readAllBytes(child.resolve("terms.tsv")));
        assertEquals(0,verify(child).code());
    }
    @Test void uploadAcknowledgementAndCustomAuditAcknowledgementAreRequired()throws Exception{
        Path root=workspace();var r=request(root,false,1);r.put("allow_upload",false);var source=new Source();assertEquals(2,run(root,r,source).code());assertTrue(source.requests.isEmpty());
        var c=request(root,true,1);c.remove("allow_unobserved_custom_normalization");assertEquals(2,run(root,c,source).code());assertTrue(source.requests.isEmpty());
    }
    @Test void exactLibraryAbsenceSkipsBeforeAnyGeneUpload()throws Exception{
        Path root=workspace();var r=request(root,false,1);r.put("libraries",List.of("missing-year"));var source=new Source();var run=run(root,r,source);
        assertEquals(3,run.code(),run.err());assertEquals(1,source.requests.size());assertEquals("GET",source.requests.get(0).method());
        assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));assertFalse((Boolean)read(root.resolve("result/analysis-status.json")).get("result_admitted"));
        assertTrue(run.err().contains("missing-year"));
    }
    @Test void missingSourceSkipsAndPreservesOriginalRequestWithoutNetwork()throws Exception{
        Path root=workspace();var r=request(root,false,1);r.put("query",Map.of("file","absent.txt","format","lines"));var source=new Source();var run=run(root,r,source);
        assertEquals(3,run.code(),run.err());assertTrue(source.requests.isEmpty());assertTrue(Files.exists(root.resolve("result/source-request.json")));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void invalidLiteralInputsAreRefusedBeforeNetworkAndRetained()throws Exception{
        for(String value:List.of("\ufeffTP53\n"," TP53\n","TP53\n\nBRCA1\n","TP53,0.5\n","TP53\rBRCA1")){
            Path root=workspace().resolve("input-"+UUID.randomUUID());Files.createDirectory(root);var r=request(root,false,1);Files.writeString(root.resolve("query.txt"),value);
            var source=new Source();var run=run(root,r,source);assertEquals(2,run.code(),run.err());assertTrue(source.requests.isEmpty());
            assertArrayEquals(utf8(value),Files.readAllBytes(root.resolve("result/inputs/query.txt")));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
        }
    }
    @Test void unavailableServiceSkipsAndKeepsRawErrorBody()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.unavailable=true;var run=run(root,r,source);
        assertEquals(3,run.code(),run.err());assertEquals("maintenance",Files.readString(root.resolve("result/wire/0001.response")));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void nonfiniteProbabilityFailsWithRawResponseRetained()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.p="NaN";var run=run(root,r,source);
        assertEquals(2,run.code(),run.err());assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
        try(var list=Files.list(root.resolve("result/wire"))){assertTrue(list.filter(p->p.toString().endsWith(".response")).anyMatch(p->{try{return Files.readString(p).contains("NaN");}catch(IOException e){throw new UncheckedIOException(e);}}));}
    }
    @Test void zeroAndInfiniteScoresRemainVisibleInFilesAndConsole()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.p="0";source.odds="Infinity";source.score="Infinity";var run=run(root,r,source);
        assertEquals(0,run.code(),run.err());assertTrue(run.err().contains("P=0"));assertTrue(run.err().contains("infinite source scores"));
        assertTrue(Files.readString(root.resolve("result/terms.tsv")).contains("\t0\tInfinity\tInfinity\t"));assertEquals(0,verify(root.resolve("result")).code());
    }
    @Test void infiniteScoreFailPolicyPreventsCompleteResult()throws Exception{
        Path root=workspace();var r=request(root,false,1);r.put("policies",Map.of("infinite_scores","fail"));var source=new Source();source.odds="Infinity";
        var run=run(root,r,source);assertEquals(2,run.code(),run.err());assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void serverIdentityChangesFailByDefaultOrRemainExplicitUnderWarnPolicy()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.echoChange=true;assertEquals(2,run(root,r,source).code());
        Path next=root.resolve("warning");Files.createDirectory(next);var w=request(next,false,1);w.put("policies",Map.of("identity_changes","warn"));var changed=new Source();changed.echoChange=true;
        var run=run(next,w,changed);assertEquals(0,run.code(),run.err());assertTrue(run.err().contains("Server query identity"));
        assertTrue(Files.readString(next.resolve("result/server-query.tsv")).contains("STAT1"));assertTrue(Files.readString(next.resolve("result/query-ledger.tsv")).contains("TP53"));
    }
    @Test void emptyFamiliesAreExplicitOrCanBeRefused()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.empty=true;var run=run(root,r,source);assertEquals(0,run.code(),run.err());assertEquals(0L,read(root.resolve("result/summary.json")).get("reported_terms"));
        Path next=root.resolve("refuse");Files.createDirectory(next);var f=request(next,false,1);f.put("policies",Map.of("empty_results","fail"));var empty=new Source();empty.empty=true;assertEquals(2,run(next,f,empty).code());
    }
    @Test void libraryChangeAfterAnalysisInvalidatesTheWholeBatch()throws Exception{
        Path root=workspace();var r=request(root,false,2);var source=new Source();source.changedLibrary=true;var run=run(root,r,source);
        assertEquals(2,run.code(),run.err());assertTrue(run.err().contains("Library bytes changed"));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));assertTrue(Files.exists(root.resolve("result/results/001.json")));
    }
    @Test void existingOutputAndReplayInsideParentNeverModifyExistingFiles()throws Exception{
        Path root=workspace();var r=request(root,false,1);Path old=root.resolve("result");Files.createDirectory(old);Files.writeString(old.resolve("KEEP"),"unchanged");
        var source=new Source();assertEquals(2,run(root,r,source).code());assertEquals(List.of("KEEP"),Files.list(old).map(p->p.getFileName().toString()).toList());assertTrue(source.requests.isEmpty());
        r.put("out",root.resolve("valid").toString());assertEquals(0,run(root,r,source).code());
        Path parent=root.resolve("valid");var before=EnrichrWorkflowFiles.inventory(parent);
        var control=Map.<String,Object>of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",parent.resolve("child").toString());
        assertEquals(2,run(root,control,source).code());assertEquals(before,EnrichrWorkflowFiles.inventory(parent));
    }
    @Test void rehashedTermTableTamperingStillFailsSemanticVerification()throws Exception{
        Path root=workspace();var r=request(root,false,1);assertEquals(0,run(root,r,new Source()).code());Path result=root.resolve("result");
        Files.writeString(result.resolve("terms.tsv"),"tampered\n",StandardOpenOption.APPEND);reseal(result);var before=EnrichrWorkflowFiles.inventory(result);
        assertEquals(2,verify(result).code());assertEquals(before,EnrichrWorkflowFiles.inventory(result));
    }
    @Test void rehashedQueryBodyTamperingFailsEvenWithUpdatedWireAndManifestHashes()throws Exception{
        Path root=workspace();var r=request(root,false,1);assertEquals(0,run(root,r,new Source()).code());Path result=root.resolve("result");
        Path metadata;
        try(var list=Files.list(result.resolve("wire"))){metadata=list.filter(p->p.toString().endsWith(".json")).filter(p->{try{return ((String)read(p).get("uri")).endsWith("addList");}catch(IOException e){throw new UncheckedIOException(e);}}).findFirst().orElseThrow();}
        Path body=metadata.resolveSibling(metadata.getFileName().toString().replace(".json",".request"));Files.writeString(body,Files.readString(body).replace("TP53","STAT1"));
        var m=new LinkedHashMap<>(read(metadata));m.put("request",EnrichrWorkflowFiles.pin(body));Files.write(metadata,EnrichrWorkflowFiles.json(m));reseal(result);
        assertEquals(2,verify(result).code());
    }
    @Test void extraFilesAndDirectoriesAreRefusedEvenAfterResealing()throws Exception{
        for(boolean directory:List.of(false,true)){
            Path root=workspace().resolve("extra-"+UUID.randomUUID());Files.createDirectory(root);var r=request(root,false,1);assertEquals(0,run(root,r,new Source()).code());Path result=root.resolve("result");
            if(directory)Files.createDirectory(result.resolve("EXTRA"));else Files.writeString(result.resolve("EXTRA"),"untracked");reseal(result);assertEquals(2,verify(result).code());
        }
    }
    @Test void outputCapacityLeavesNoCompletionAndRetainsCapturedInput()throws Exception{
        Path root=workspace();var r=request(root,false,1);r.put("limits",Map.of("maximum_output_bytes",128,"request_interval_millis",0));var source=new Source();var run=run(root,r,source);
        assertEquals(2,run.code(),run.err());assertTrue(source.requests.isEmpty());assertArrayEquals(Files.readAllBytes(root.resolve("query.txt")),Files.readAllBytes(root.resolve("result/inputs/query.txt")));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void interruptionRecordsTheAttemptBeforeRestoringInterruptStatus()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.interrupt=true;
        Run run;
        try{run=run(root,r,source);assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
        assertEquals(2,run.code(),run.err());assertEquals("INTERRUPTED",read(root.resolve("result/wire/0001.json")).get("transfer_state"));
        assertTrue(Files.readString(root.resolve("result/WARNINGS.md")).contains("FAIL2"));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void partialTimeoutReplyIsRetainedAsSkippedDependentAnalysis()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();source.partialTimeout=true;var run=run(root,r,source);
        assertEquals(3,run.code(),run.err());assertEquals("partial-timeout",Files.readString(root.resolve("result/wire/0001.response")));
        assertEquals(Boolean.FALSE,read(root.resolve("result/wire/0001.json")).get("complete"));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void requestByteCapacityFailsBeforeAnyQuerySubmission()throws Exception{
        Path root=workspace();var r=request(root,false,1);r.put("limits",Map.of("maximum_request_bytes",64,"request_interval_millis",0));var source=new Source();var run=run(root,r,source);
        assertEquals(2,run.code(),run.err());assertTrue(source.requests.stream().noneMatch(p->p.uri().getPath().endsWith("addList")));assertFalse(Files.exists(root.resolve("result/COMPLETED.json")));
    }
    @Test void combinedOutputQuotaRetainsItsGeneratedPrefix()throws Exception{
        Path root=workspace();var generated=new EnrichrWorkflowFiles.Generated(root,new EnrichrWorkflowFiles.Budget(4,0));
        assertThrows(IOException.class,()->generated.bytes("partial.bin",utf8("123456")));
        assertArrayEquals(utf8("1234"),Files.readAllBytes(root.resolve("partial.bin")));
    }
    @Test void replayRejectsAnAliasIntoItsParent()throws Exception{
        Path root=workspace();var r=request(root,false,1);var source=new Source();assertEquals(0,run(root,r,source).code());Path parent=root.resolve("result"),alias=root.resolve("alias");
        try{Files.createSymbolicLink(alias,parent);}catch(UnsupportedOperationException|FileSystemException denied){org.junit.jupiter.api.Assumptions.assumeTrue(false,"This filesystem cannot create a directory symlink; run the contract on native Linux");}
        var before=EnrichrWorkflowFiles.inventory(parent);
        var control=Map.<String,Object>of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",alias.resolve("nested").toString());
        assertEquals(2,run(root,control,source).code());assertEquals(before,EnrichrWorkflowFiles.inventory(parent));assertFalse(Files.exists(parent.resolve("nested")));
    }
    @Test void mainCommandDispatchesTheExplicitWorkflowHelp()throws Exception{
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"enrichr-tools","--help"},new PrintStream(out),new PrintStream(err)));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains(EnrichrWorkflowContracts.PROFILE));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("offline"));
    }
}
