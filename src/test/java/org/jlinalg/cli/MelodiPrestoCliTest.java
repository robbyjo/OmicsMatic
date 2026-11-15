/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class MelodiPrestoCliTest {
    @TempDir Path temp;
    HttpServer server;String endpoint;AtomicInteger calls;
    static String enrichment(){return "{\"query\":\"x\",\"triple\":\"literal\",\"subject_name\":\"Gene/α\",\"subject_type\":\"gngm\",\"subject_id\":\"C01\",\"predicate\":\"ASSOCIATED_WITH\",\"object_name\":\"disease\",\"object_type\":\"dsyn\",\"object_id\":\"C02\",\"localCount\":\"2\",\"localTotal\":10,\"globalCount\":5,\"globalTotal\":1000,\"odds\":null,\"pval\":0.00000000000000000123456789123456789,\"pmids\":\"123 456\",\"extra_original\":9007199254740993}";}
    static String overlap()throws Exception{
        var out=new LinkedHashMap<String,Object>();var row=MelodiPrestoCli.object(NextflowParameters.parse(enrichment(),10000,1000));
        for(String suffix:List.of("_x","_y")){for(var field:row.entrySet())if(!field.getKey().equals("query"))out.put(field.getKey()+suffix,field.getValue());out.put("set"+suffix,"literal_query");}
        return "{\"count\":1,\"data\":["+NextflowJson.parameters(out)+"],\"unknown_server_field\":\"kept\"}";
    }
    interface Handler{void serve(com.sun.net.httpserver.HttpExchange exchange)throws Exception;}
    void start(Handler handler)throws Exception{
        calls=new AtomicInteger();server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/",exchange->{calls.incrementAndGet();try{handler.serve(exchange);}catch(Exception e){throw new java.io.IOException(e);}finally{exchange.close();}});
        server.start();endpoint="http://"+(server.getAddress().getAddress() instanceof Inet6Address?"[::1]":"127.0.0.1")+":"+server.getAddress().getPort();
    }
    void reply(int status,String type,String text)throws Exception{start(e->{e.getResponseHeaders().add("Content-Type",type);byte[] raw=text.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(status,raw.length);e.getResponseBody().write(raw);});}
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    Path request(String name,String operation,String extra)throws Exception{
        String payload=switch(operation){case "enrich"->"{\"query\":\"TP53\"}";case "overlap"->"{\"x\":[\"TP53\"],\"y\":[\"colorectal cancer\"]}";default->"{\"pmid\":\"123456\"}";};
        Path p=temp.resolve(name+".json");Files.writeString(p,"{\"schema\":1,\"operation\":\""+operation+"\",\"payload\":"+payload+",\"server_snapshot\":\"live-unversioned\",\"endpoint\":\""+endpoint+"\""+extra+"}");return p;
    }
    int run(Path request,Path out,boolean network){return MelodiPrestoCli.run(network?new String[]{"run","--request",request.toString(),"--out",out.toString(),"--allow-network"}:new String[]{"run","--request",request.toString(),"--out",out.toString()});}
    void failed(Path out,int code)throws Exception{assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("FAILURE.json")).contains(code==3?"SKIP3":"FAIL2"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("NEW paths"));}

    @ParameterizedTest @ValueSource(strings={"enrich","overlap","sentence"})
    void preservesEntireResponseLiteralRequestAndUnknownPrecisionAndReplays(String operation)throws Exception{
        String response=switch(operation){case "enrich"->"["+enrichment()+"]";case "overlap"->overlap();default->"{\"count\":1,\"data\":[{\"SENTENCE\":\"α & literal\"}],\"title\":\"original\"}";};
        start(e->{assertEquals("POST",e.getRequestMethod());assertEquals("/api/"+operation+"/",e.getRequestURI().getPath());
            String sent=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);assertTrue(sent.contains(operation.equals("sentence")?"123456":"TP53"));
            byte[] raw=response.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("Content-Type","application/json; charset=utf-8");e.sendResponseHeaders(200,raw.length);e.getResponseBody().write(raw);});
        Path source=request("request",operation,"");byte[] original=Files.readAllBytes(source);Path out=temp.resolve("out");
        assertEquals(0,run(source,out,true));assertArrayEquals(response.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(out.resolve("response.bin")));
        assertArrayEquals(original,Files.readAllBytes(out.resolve("source-request.json")));assertArrayEquals(original,Files.readAllBytes(source));
        assertEquals(0,MelodiPrestoCli.run(new String[]{"verify","--bundle",out.toString(),"--out",temp.resolve("verification").toString()}));assertEquals(1,calls.get());
    }
    @Test void forbidsTransmissionAndPreservesInputWithoutNetworkPermission()throws Exception{
        reply(200,"application/json","[]");Path out=temp.resolve("out");assertEquals(3,run(request("request","enrich",""),out,false));failed(out,3);assertEquals(0,calls.get());
    }
    @Test void refusesHistoricalSnapshotRatherThanUseCurrentData()throws Exception{
        reply(200,"application/json","[]");Path req=request("request","enrich","");Files.writeString(req,Files.readString(req).replace("live-unversioned","2024-04-03"));Path out=temp.resolve("out");assertEquals(2,run(req,out,true));failed(out,2);assertEquals(0,calls.get());
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,429,500,503,302})
    void retainsFullNonSuccessResponsesAndClassifiesServiceAvailability(int status)throws Exception{
        String raw="{\"upstream_diagnostic\":\"retain exact text\"}";reply(status,"application/json",raw);Path out=temp.resolve("out");int expected=status==401||status==403||status==429||status>=500?3:2;
        assertEquals(expected,run(request("request","enrich",""),out,true));failed(out,expected);assertEquals(raw,Files.readString(out.resolve("response.bin")));assertEquals(1,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"html","malformed","duplicate","error","count","missing-fields","nonfinite"})
    void rejectsIncompleteMalformedAndOriginalErrorRecordsWithoutRepair(String kind)throws Exception{
        String response=switch(kind){case "html"->"<html>unavailable</html>";case "malformed"->"{\"count\":";case "duplicate"->"{\"count\":0,\"count\":0,\"data\":[]}";
            case "error"->"{\"count\":0,\"data\":[],\"Error\":\"no overlaps\"}";case "count"->"{\"count\":2,\"data\":[]}";case "nonfinite"->"{\"count\":0,\"data\":[],\"x\":NaN}";default->"{\"count\":1,\"data\":[{}]}";};
        reply(200,kind.equals("html")?"text/html":"application/json",response);Path out=temp.resolve("out");assertEquals(2,run(request("request","overlap",""),out,true));failed(out,2);assertEquals(response,Files.readString(out.resolve("response.bin")));
    }
    @Test void emptyCompleteResultIsRecordedWithoutClaimingBiologicalAbsence()throws Exception{
        reply(200,"application/json","{\"count\":0,\"data\":[]}");Path out=temp.resolve("out");assertEquals(0,run(request("request","overlap",""),out,true));assertTrue(Files.readString(out.resolve("COMPLETED.json")).contains("\"biological_accuracy_established\":false"));
    }
    @Test void preservesActualElasticsearchSentenceCountObject()throws Exception{
        String response="{\"count\":{\"value\":1,\"relation\":\"eq\"},\"data\":[{\"SENTENCE\":\"kept\"}],\"title\":\"original\"}";
        reply(200,"application/json",response);Path out=temp.resolve("out");assertEquals(0,run(request("request","sentence",""),out,true));assertEquals(response,Files.readString(out.resolve("response.bin")));
        assertEquals(0,MelodiPrestoCli.run(new String[]{"verify","--bundle",out.toString(),"--out",temp.resolve("verified").toString()}));
    }
    @ParameterizedTest @ValueSource(strings={"gte","unknown","truncated"})
    void refusesLowerBoundOrTruncatedSentenceTotals(String kind)throws Exception{
        String response="{\"count\":{\"value\":"+(kind.equals("truncated")?"2":"1")+",\"relation\":\""+(kind.equals("truncated")?"eq":kind)+"\"},\"data\":[{}],\"title\":\"original\"}";
        reply(200,"application/json",response);Path out=temp.resolve("out");assertEquals(2,run(request("request","sentence",""),out,true));failed(out,2);assertEquals(response,Files.readString(out.resolve("response.bin")));
    }
    @Test void fullBodyDeadlineActuallyStopsStalledResponse()throws Exception{
        start(e->{e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(200,100);e.getResponseBody().write('[');e.getResponseBody().flush();Thread.sleep(2500);});
        Path out=temp.resolve("out");long before=System.nanoTime();assertEquals(3,run(request("request","enrich",",\"timeout_seconds\":1"),out,true));assertTrue((System.nanoTime()-before)/1e9<2.3);failed(out,3);assertTrue(Files.exists(out.resolve("response.bin")));
    }
    @Test void capacityExceededDoesNotAdmitTruncatedResponse()throws Exception{
        reply(200,"application/json","["+enrichment()+"]");Path out=temp.resolve("out");assertEquals(2,run(request("request","enrich",",\"max_response_bytes\":100"),out,true));failed(out,2);assertTrue(Files.size(out.resolve("response.bin"))<100);
    }
    @Test void recordingFailureAfterWholeResponsePreservesRawPayloadAndAdmitsNothing()throws Exception{
        String response="["+enrichment()+"]";Path out=temp.resolve("out");
        start(e->{Files.createDirectory(out.resolve("runtime.json"));byte[] raw=response.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("Content-Type","application/json");e.sendResponseHeaders(200,raw.length);e.getResponseBody().write(raw);});
        assertEquals(2,run(request("request","enrich",""),out,true));failed(out,2);assertEquals(response,Files.readString(out.resolve("response.bin")));
    }
    @Test void preservedOutputAndTamperedBundleCannotBeAdmitted()throws Exception{
        reply(200,"application/json","[]");Path out=temp.resolve("out"),req=request("request","enrich","");assertEquals(0,run(req,out,true));byte[] done=Files.readAllBytes(out.resolve("COMPLETED.json"));
        assertEquals(2,run(req,out,true));assertArrayEquals(done,Files.readAllBytes(out.resolve("COMPLETED.json")));assertEquals(1,calls.get());
        Files.writeString(out.resolve("response.bin"),"[{}]");Path verify=temp.resolve("verification");assertEquals(2,MelodiPrestoCli.run(new String[]{"verify","--bundle",out.toString(),"--out",verify.toString()}));failed(verify,2);assertEquals(1,calls.get());
    }
}
