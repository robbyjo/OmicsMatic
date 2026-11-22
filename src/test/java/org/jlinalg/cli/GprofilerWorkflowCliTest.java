/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jlinalg.gprofiler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.GprofilerWorkflowFiles.*;

class GprofilerWorkflowCliTest {
    @TempDir Path temp;
    static final String BUILD="e114_eg62_p19_9dc92ac8";
    record Outcome(int code,String stdout,String stderr) { }
    static Map<String,Object> payload(){return map("organism","hsapiens","query",List.of("TP53","BRCA1","RAD51"),
        "sources",List.of("GO:BP"),"user_threshold",0.05,"all_results",true,"ordered",false,"combined",false,
        "measure_underrepresentation",false,"no_iea",false,"domain_scope","annotated","numeric_ns","ENTREZGENE_ACC",
        "significance_threshold_method","g_SCS","background",null,"output","json","no_evidences",true,"highlight",false);}
    static Map<String,Object> request(Path out){return map("schema_version",1,"mode","RUN","out",out.toString(),"operation","enrichment",
        "service",map("archive","","required_build",BUILD),"payload",payload(),"allow_upload",true,"limits",map("request_interval_millis",0));}
    static Map<String,Object> document(Map<String,Object> payload){
        var echo=new LinkedHashMap<>(payload);Object query=echo.remove("query");echo.remove("background");echo.remove("output");
        var queries=query instanceof Map<?,?>?query:map("query_1",query);echo.put("queries",queries);
        var row=map("native","GO:0000001","source","GO:BP","name","synthetic term","parents",List.of(),
            "p_value",0.6,"significant",false,"effective_domain_size",10,"term_size",2,"query_size",3,"intersection_size",1,
            "precision",1.0/3,"recall",0.5,"query","query_1");
        return map("result",List.of(row),"meta",map("version",BUILD,"query_metadata",echo,"genes_metadata",
            map("failed",List.of(),"ambiguous",map(),"duplicates",List.of(),"query",map("query_1",map("ensgs",List.of("ENSG1","ENSG2","ENSG3"),"mapping",map())))));
    }
    static GprofilerGostClient.Reply reply(int code,Object body)throws IOException{return new GprofilerGostClient.Reply(code,Map.of(),json(body),GprofilerGostClient.State.COMPLETE,null);}
    static Function<GprofilerGostClient.Settings,GprofilerGostClient> fixture(AtomicInteger metadata,AtomicInteger genes,
            String preflightBuild,Map<String,Object> response){return settings->new GprofilerGostClient(settings,r->{
        if(r.uri().getPath().endsWith("data_versions")){metadata.incrementAndGet();assertEquals(map("organism","hsapiens"),object(GprofilerJson.read(r.body())));
            return reply(200,map("organism","hsapiens","gprofiler_version",preflightBuild));}
        genes.incrementAndGet();return reply(200,response);
    });}
    Outcome run(Map<String,Object> spec,Function<GprofilerGostClient.Settings,GprofilerGostClient> factory)throws IOException{
        Path p=temp.resolve("request-"+UUID.randomUUID()+".json");Files.write(p,json(spec));var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=GprofilerWorkflowCli.run(new String[]{"--request",p.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8),factory,"injected-fixture");
        return new Outcome(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    Outcome fixtureRun(Path out)throws IOException{return run(request(out),fixture(new AtomicInteger(),new AtomicInteger(),BUILD,document(payload())));}
    static Function<GprofilerGostClient.Settings,GprofilerGostClient> noNetwork(){return s->{throw new AssertionError("Offline mode attempted to create a live client");};}
    static void reseal(Path root)throws IOException{
        var tree=inventory(root);byte[] raw=json(map("schema_version",1,"profile",PROFILE,"files",tree.files(),"directories",tree.directories()));
        Files.write(root.resolve("manifest.json"),raw);Files.write(root.resolve("COMPLETED.json"),json(map("schema_version",1,"profile",PROFILE,"manifest_sha256",sha(raw),"authentication",false)));
    }
    @Test void completeRunVerifiesAndReplaysWithoutCreatingAnyNetworkClient()throws Exception{
        Path original=temp.resolve("original"),replay=temp.resolve("replay");var meta=new AtomicInteger();var gene=new AtomicInteger();
        var outcome=run(request(original),fixture(meta,gene,BUILD,document(payload())));assertEquals(0,outcome.code(),outcome.stderr());
        assertEquals(1,meta.get());assertEquals(1,gene.get());assertTrue(Files.isRegularFile(original.resolve("COMPLETED.json")));
        assertTrue(outcome.stderr().contains("WARNINGS / REQUIRED ACTIONS"));assertEquals(1,number(read(original.resolve("summary.json"),1000000),"reported_terms"));
        byte[] parentManifest=Files.readAllBytes(original.resolve("manifest.json"));
        assertEquals(0,run(map("schema_version",1,"mode","VERIFY","analysis",original.toString()),noNetwork()).code());
        var replayed=run(map("schema_version",1,"mode","REPLAY","analysis",original.toString(),"out",replay.toString()),noNetwork());
        assertEquals(0,replayed.code(),replayed.stderr());assertArrayEquals(Files.readAllBytes(original.resolve("result.json")),Files.readAllBytes(replay.resolve("result.json")));
        assertArrayEquals(parentManifest,Files.readAllBytes(original.resolve("manifest.json")));GprofilerWorkflowCli.verify(replay,true);
        var e=read(replay.resolve("execution.json"),100000);assertEquals(false,e.get("network_executed"));assertEquals("injected-fixture",e.get("captured_transport_origin"));
    }
    @Test void metadataDiscoveryUsesNoGeneSubmissionAndHasNoEnrichmentResult()throws Exception{
        Path out=temp.resolve("metadata");var r=request(out);r.put("operation","metadata");r.put("payload",map("organism","hsapiens"));r.put("service",map("archive","","required_build",null));r.put("allow_upload",false);
        var meta=new AtomicInteger();var genes=new AtomicInteger();var result=run(r,fixture(meta,genes,BUILD,document(payload())));
        assertEquals(0,result.code(),result.stderr());assertEquals(1,meta.get());assertEquals(0,genes.get());assertFalse(Files.exists(out.resolve("result.json")));GprofilerWorkflowCli.verify(out,true);
    }
    @Test void exactBuildPreflightStopsBeforeAnyGeneUploadAndPreservesOriginalReply()throws Exception{
        Path out=temp.resolve("skip");var meta=new AtomicInteger();var genes=new AtomicInteger();
        var result=run(request(out),fixture(meta,genes,"e114_eg62_p19_12345678",document(payload())));
        assertEquals(3,result.code());assertEquals(1,meta.get());assertEquals(0,genes.get());assertTrue(result.stderr().contains("SKIP3"));
        assertTrue(Files.exists(out.resolve("wire/001-response.body")));assertFalse(Files.exists(out.resolve("wire/002-request.body")));
        assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertFalse(Files.exists(out.resolve("result.json")));assertEquals(false,read(out.resolve("FAILURE.json"),100000).get("result_admitted"));
    }
    @Test void lateFailureKeepsBothExchangesAndAdmitsNothing()throws Exception{
        Path out=temp.resolve("late");AtomicInteger calls=new AtomicInteger();
        var result=run(request(out),s->new GprofilerGostClient(s,r->calls.incrementAndGet()==1?reply(200,map("organism","hsapiens","gprofiler_version",BUILD)):reply(503,map("message","original unavailable"))));
        assertEquals(3,result.code());assertEquals(2,calls.get());assertTrue(Files.exists(out.resolve("wire/002-response.body")));
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("provisional work"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }
    @Test void malformedOrContradictoryInputsSendNothing()throws Exception{
        List<Map<String,Object>> specs=new ArrayList<>();
        var denied=request(temp.resolve("denied"));denied.put("allow_upload",false);specs.add(denied);
        var unknown=request(temp.resolve("unknown"));unknown.put("orderd",true);specs.add(unknown);
        var ignoredBg=request(temp.resolve("ignored-bg"));object(ignoredBg.get("payload"));var p=payload();p.put("background",List.of("TP53"));ignoredBg.put("payload",p);specs.add(ignoredBg);
        var shortBuild=request(temp.resolve("short-build"));shortBuild.put("service",map("archive","","required_build","e114_eg62_p19"));specs.add(shortBuild);
        for(var spec:specs){var result=run(spec,noNetwork());assertEquals(2,result.code());assertFalse(Files.exists(Path.of((String)spec.get("out"))));}
    }
    @Test void organismMismatchInMetadataSendsNoGenes()throws Exception{
        Path out=temp.resolve("organism");AtomicInteger calls=new AtomicInteger();
        var result=run(request(out),s->new GprofilerGostClient(s,r->{calls.incrementAndGet();return reply(200,map("organism","mmusculus","gprofiler_version",BUILD));}));
        assertEquals(2,result.code());assertEquals(1,calls.get());assertFalse(Files.exists(out.resolve("wire/002-request.body")));
    }
    @Test void sourceNormalizationAndMappingChangesRemainExplicitAndCanBeRejected()throws Exception{
        var doc=document(payload());var meta=object(doc.get("meta"));var echo=object(meta.get("query_metadata"));echo.put("numeric_ns","");meta.put("query_metadata",echo);
        var mapping=object(meta.get("genes_metadata"));mapping.put("failed",List.of("SYNTHETIC_UNMAPPED"));meta.put("genes_metadata",mapping);doc.put("meta",meta);
        Path warn=temp.resolve("warn");assertEquals(0,run(request(warn),fixture(new AtomicInteger(),new AtomicInteger(),BUILD,doc)).code());
        assertTrue(Files.readString(warn.resolve("WARNINGS.md")).contains("failed, ambiguous or duplicate"));
        assertFalse(list(GprofilerJson.read(Files.readAllBytes(warn.resolve("normalization.json")))).isEmpty());
        Path strict=temp.resolve("strict");var r=request(strict);r.put("policies",map("server_normalization","fail"));
        assertEquals(2,run(r,fixture(new AtomicInteger(),new AtomicInteger(),BUILD,doc)).code());assertFalse(Files.exists(strict.resolve("COMPLETED.json")));
    }
    @Test void rowCapacityAndArithmeticFailuresRetainCompleteOriginalReplies()throws Exception{
        Path out=temp.resolve("row-cap");var r=request(out);r.put("limits",map("maximum_result_rows",0,"request_interval_millis",0));
        assertEquals(2,run(r,fixture(new AtomicInteger(),new AtomicInteger(),BUILD,document(payload()))).code());
        assertTrue(Files.exists(out.resolve("wire/002-response.body")));assertFalse(Files.exists(out.resolve("result.json")));
        var bad=document(payload());var row=object(list(bad.get("result")).get(0));row.put("intersection_size",4);bad.put("result",List.of(row));
        Path impossible=temp.resolve("impossible");assertEquals(2,run(request(impossible),fixture(new AtomicInteger(),new AtomicInteger(),BUILD,bad)).code());assertFalse(Files.exists(impossible.resolve("COMPLETED.json")));
    }
    @Test void completeEmptyResultIsPreservedAndPolicyCanRefuseIt()throws Exception{
        var empty=document(payload());empty.put("result",List.of());Path out=temp.resolve("empty");
        assertEquals(0,run(request(out),fixture(new AtomicInteger(),new AtomicInteger(),BUILD,empty)).code());assertEquals(0,Files.size(out.resolve("results.jsonl")));
        var r=request(temp.resolve("empty-strict"));r.put("policies",map("empty_results","fail"));assertEquals(2,run(r,fixture(new AtomicInteger(),new AtomicInteger(),BUILD,empty)).code());
    }
    @Test void combinedSourceAbsenceSentinelsAreNotRepaired()throws Exception{
        var p=payload();p.put("query",map("first",List.of("TP53"),"second",List.of("BRCA1")));p.put("combined",true);
        var doc=document(p);doc.put("result",List.of(map("native","GO:0000001","source","GO:BP","name","synthetic term","parents",List.of(),"term_size",2,"effective_domain_size",10,
            "query_sizes",List.of(1.0,-1.0),"intersection_sizes",List.of(1.0,-1.0),"p_values",List.of(0.2,1.0))));
        Path out=temp.resolve("combined");var r=request(out);r.put("payload",p);
        assertEquals(0,run(r,fixture(new AtomicInteger(),new AtomicInteger(),BUILD,doc)).code());assertEquals(1,number(read(out.resolve("summary.json"),100000),"original_combined_absence_cells"));
        assertTrue(Files.readString(out.resolve("results.jsonl")).contains("-1.0"));GprofilerWorkflowCli.verify(out,true);
    }
    @Test void resealedProtocolAndDerivedReportCorruptionAreRefused()throws Exception{
        for(String kind:List.of("request","row","report","extra")){
            Path out=temp.resolve("corrupt-"+kind);assertEquals(0,fixtureRun(out).code());
            if(kind.equals("request"))Files.writeString(out.resolve("wire/002-request.body"),"{}");
            if(kind.equals("row")){var d=read(out.resolve("wire/002-response.body"),1000000);var row=object(list(d.get("result")).get(0));row.put("p_value",2);d.put("result",List.of(row));Files.write(out.resolve("wire/002-response.body"),json(d));}
            if(kind.equals("report"))Files.writeString(out.resolve("results.jsonl"),"{}\n");
            if(kind.equals("extra"))Files.writeString(out.resolve("unexpected.txt"),"extra");
            reseal(out);assertThrows(IOException.class,()->GprofilerWorkflowCli.verify(out,true),kind);
        }
    }
    @Test void existingAndAncestorOutputsAreNeverChanged()throws Exception{
        Path out=temp.resolve("existing");Files.createDirectory(out);Files.writeString(out.resolve("sentinel"),"keep");
        assertEquals(2,fixtureRun(out).code());assertEquals("keep",Files.readString(out.resolve("sentinel")));
        Path parent=temp.resolve("parent");assertEquals(0,fixtureRun(parent).code());byte[] manifest=Files.readAllBytes(parent.resolve("manifest.json"));
        for(Path target:List.of(parent,parent.resolve("child"),temp)){
            assertEquals(2,run(map("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",target.toString()),noNetwork()).code());
            assertArrayEquals(manifest,Files.readAllBytes(parent.resolve("manifest.json")));
        }
        assertFalse(Files.exists(parent.resolve("child")));
    }
    @Test void failedParentCannotBeReplayedAndMissingRequestIsSkip3()throws Exception{
        Path parent=temp.resolve("failed");run(request(parent),fixture(new AtomicInteger(),new AtomicInteger(),"e114_eg62_p19_12345678",document(payload())));
        Path out=temp.resolve("bad-replay");assertEquals(2,run(map("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",out.toString()),noNetwork()).code());assertFalse(Files.exists(out));
        var sink=new PrintStream(OutputStream.nullOutputStream());assertEquals(3,GprofilerWorkflowCli.run(new String[]{"--request",temp.resolve("missing.json").toString()},sink,sink));
    }
    @Test void globalDispatchAdvertisesTheActualWorkflow(){
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"gprofiler-tools","--help"},new PrintStream(out),new PrintStream(err)));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Metadata preflight"));
    }
    @Test void observedCookieHeadersAreFlaggedWithoutPrintingTheirValues()throws Exception{
        Path out=temp.resolve("cookie-warning");String token="SYNTHETIC_COOKIE_VALUE_NOT_A_REAL_CREDENTIAL";
        var result=run(request(out),s->new GprofilerGostClient(s,r->{
            Object body=r.uri().getPath().endsWith("data_versions")?map("organism","hsapiens","gprofiler_version",BUILD):document(payload());
            return new GprofilerGostClient.Reply(200,Map.of("set-cookie",List.of("fixture="+token)),json(body),GprofilerGostClient.State.COMPLETE,null);
        }));
        assertEquals(0,result.code(),result.stderr());assertTrue(result.stderr().contains("Keep original wire traces private"));assertFalse(result.stderr().contains(token));
        assertEquals(true,read(out.resolve("summary.json"),100000).get("cookie_response_headers_recorded"));
        assertTrue(Files.readString(out.resolve("wire/001-exchange.json")).contains(token));GprofilerWorkflowCli.verify(out,true);
        Path failed=temp.resolve("cookie-failure");
        var late=run(request(failed),s->new GprofilerGostClient(s,r->{
            boolean first=r.uri().getPath().endsWith("data_versions");
            return new GprofilerGostClient.Reply(first?200:503,Map.of("set-cookie",List.of("fixture="+token)),
                json(first?map("organism","hsapiens","gprofiler_version",BUILD):map("message","unavailable")),GprofilerGostClient.State.COMPLETE,null);
        }));
        assertEquals(3,late.code());assertTrue(late.stderr().contains("Keep original wire traces private"));assertFalse(late.stderr().contains(token));
        assertTrue(Files.isRegularFile(failed.resolve("FAILURE.json")));assertFalse(Files.exists(failed.resolve("COMPLETED.json")));
    }
}
