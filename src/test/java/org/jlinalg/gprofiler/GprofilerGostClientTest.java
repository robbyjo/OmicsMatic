/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.gprofiler;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.gprofiler.GprofilerGostClient.*;

class GprofilerGostClientTest {
    static final String BUILD = "e114_eg62_p19_9dc92ac8";
    static final Service CURRENT = new Service("", BUILD);
    static final Settings SETTINGS = new Settings(Duration.ofSeconds(2), 65536, 65536, 1000, Duration.ZERO);
    static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static Map<String,Object> payload() {
        var p = new LinkedHashMap<String,Object>();
        p.put("organism","hsapiens"); p.put("query",List.of("TP53","TP53","BRCA1"));
        p.put("sources",List.of("GO:BP")); p.put("user_threshold",0.05);
        for (String f : List.of("all_results","ordered","combined","measure_underrepresentation","no_iea","highlight")) p.put(f,false);
        p.put("no_evidences",true); p.put("domain_scope","annotated"); p.put("numeric_ns","ENTREZGENE_ACC");
        p.put("significance_threshold_method","g_SCS"); p.put("background",null); p.put("output","json"); return p;
    }
    static Reply reply(int code, String body) { return new Reply(code,Map.of(),utf8(body),State.COMPLETE,null); }
    static String response(String build) { return "{\"result\":[{\"p_value\":1e-400}],\"meta\":{\"version\":\""+build+"\"}}"; }

    @Test void losslessJsonRoundTripPreservesNullsUnicodeAndExtremeProbability() throws Exception {
        byte[] raw = utf8("{\"ids\":[\" gene \",\"🧬\\t\\n\",null,\"\"],\"p\":1e-400,\"negativeZero\":-0}");
        Object a = GprofilerJson.read(raw);
        assertEquals(a,GprofilerJson.read(GprofilerJson.write(a,4096)));
        assertTrue(new String(GprofilerJson.write(a,4096),StandardCharsets.UTF_8).contains("1e-400"));
    }
    @Test void invalidJsonNeverRepairsDuplicatesNonfiniteNumbersOrUnicode() {
        for (String invalid : List.of("{\"a\":null,\"a\":1}","NaN","Infinity","-Infinity","01","[1,]","\"\\ud800\"","\"\\u+041\"","\"\\u-041\"","true false"))
            assertThrows(IOException.class,()->GprofilerJson.read(utf8(invalid)),invalid);
        assertThrows(IOException.class,()->GprofilerJson.read(new byte[]{(byte)0xc3,0x28}));
        assertThrows(IOException.class,()->GprofilerJson.write(Double.NaN,4096));
        assertThrows(IOException.class,()->GprofilerJson.write("\ud800",4096));
        assertThrows(IOException.class,()->GprofilerJson.write("🧬",3));
    }
    @Test void jsonWriterNeverProducesStructuresBeyondItsReaderCapacity() throws Exception {
        Object value="leaf";
        for(int i=0;i<63;i++)value=List.of(value);
        assertEquals(value,GprofilerJson.read(GprofilerJson.write(value,4096)));
        Object tooDeep=List.of(value);
        assertThrows(IOException.class,()->GprofilerJson.write(tooDeep,4096));
    }
    @Test void malformedMetadataRetainsItsOriginalWireExchange() throws Exception {
        try(var client=new GprofilerGostClient(SETTINGS,r->reply(200,"{}"))){
            var failure=assertThrows(Failure.class,()->client.openSession().versions(new Service("",null),"hsapiens"));
            assertEquals(2,failure.suggestedExitCode());assertEquals(1,failure.exchanges().size());
            assertArrayEquals(utf8("{}"),failure.exchanges().get(0).reply().body());
        }
    }
    @Test void queryOrderDuplicatesAndWireBytesAreRetainedDefensively() throws Exception {
        List<Request> sent = new ArrayList<>(); byte[] raw = utf8(response(BUILD));
        try (var client = new GprofilerGostClient(SETTINGS,r->{sent.add(r);return new Reply(200,Map.of(),raw,State.COMPLETE,null);})) {
            var session=client.openSession();var result=session.profile(CURRENT,payload(),true);
            assertEquals(1,sent.size());assertEquals("https://biit.cs.ut.ee/gprofiler/api/gost/profile/",sent.get(0).uri().toString());
            var parsed=(Map<?,?>)GprofilerJson.read(sent.get(0).body());
            assertEquals(List.of("TP53","TP53","BRCA1"),parsed.get("query"));
            assertEquals(1,result.reportedTerms());assertEquals(BUILD,result.observedBuild());
            assertArrayEquals(raw,result.response());result.response()[0]=0;assertArrayEquals(raw,result.response());
            byte[] request=sent.get(0).body();request[0]=0;assertNotEquals(0,sent.get(0).body()[0]);
            byte[] copy=session.exchanges().get(0).reply().body();copy[0]=0;assertArrayEquals(raw,session.exchanges().get(0).reply().body());
        }
    }
    @Test void metadataDiscoveryIsGeneFreeAndBuildMismatchIsASkippedDependency() throws Exception {
        List<Request> sent=new ArrayList<>();String observed="e111_eg58_p18_28c0ce60";
        try(var client=new GprofilerGostClient(SETTINGS,r->{sent.add(r);return reply(200,"{\"gprofiler_version\":\""+observed+"\"}");})){
            var session=client.openSession();var service=new Service("e111_eg58_p18",null);
            assertEquals(observed,session.versions(service,"hsapiens").observedBuild());
            assertEquals(Map.of("organism","hsapiens"),GprofilerJson.read(sent.get(0).body()));
            assertTrue(sent.get(0).uri().getPath().startsWith("/gprofiler_archive3/e111_eg58_p18/"));
            var failed=assertThrows(Failure.class,()->session.versions(new Service("e111_eg58_p18","e111_eg58_p18_30541362"),"hsapiens"));
            assertEquals(3,failed.suggestedExitCode());assertEquals(2,failed.exchanges().size());
            assertTrue(failed.getMessage().contains("no build substituted"));
        }
    }
    @Test void reportedArchiveBuildIsNeverSilentlyReplaced() throws Exception {
        AtomicInteger calls=new AtomicInteger();String actual="e111_eg58_p18_28c0ce60";
        try(var client=new GprofilerGostClient(SETTINGS,r->{calls.incrementAndGet();return reply(200,response(actual));})){
            var failure=assertThrows(Failure.class,()->client.openSession().profile(new Service("e111_eg58_p18","e111_eg58_p18_30541362"),payload(),true));
            assertEquals(3,failure.suggestedExitCode());assertEquals(1,calls.get());
            assertArrayEquals(utf8(response(actual)),failure.exchanges().get(0).reply().body());
        }
    }
    @Test void uploadRefusalMissingBuildUnknownSettingsAndCapacitySendNothing() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        try(var client=new GprofilerGostClient(SETTINGS,r->{calls.incrementAndGet();return reply(200,response(BUILD));})){
            var session=client.openSession();
            assertThrows(IOException.class,()->session.profile(CURRENT,payload(),false));
            assertThrows(IOException.class,()->session.profile(new Service("",null),payload(),true));
            var unknown=payload();unknown.put("orderd",true);assertThrows(IOException.class,()->session.profile(CURRENT,unknown,true));
            var missing=payload();missing.remove("ordered");assertThrows(IOException.class,()->session.profile(CURRENT,missing,true));
            var bad=payload();bad.put("user_threshold",Double.NaN);assertThrows(IOException.class,()->session.profile(CURRENT,bad,true));
            assertEquals(0,calls.get());
        }
        try(var client=new GprofilerGostClient(new Settings(Duration.ofSeconds(1),4,65536,2,Duration.ZERO),r->{calls.incrementAndGet();return reply(200,response(BUILD));})){
            assertThrows(IOException.class,()->client.openSession().profile(CURRENT,payload(),true));assertEquals(0,calls.get());
        }
    }
    @Test void namedRankedUnderrepresentationAndCustomBackgroundRemainExplicit() throws Exception {
        var p=payload();var queries=new LinkedHashMap<String,Object>();queries.put("second",List.of("BRCA1","TP53"));queries.put("first",List.of("RAD51"));
        p.put("query",queries);p.put("ordered",true);p.put("measure_underrepresentation",true);p.put("domain_scope","custom_annotated");
        p.put("background",List.of("RAD51","TP53","BRCA1","BRCA1"));p.put("significance_threshold_method","bonferroni");
        try(var client=new GprofilerGostClient(SETTINGS,r->{
            var got=(Map<?,?>)GprofilerJson.read(r.body());assertEquals(List.of("second","first"),new ArrayList<>(((Map<?,?>)got.get("query")).keySet()));
            assertEquals(p.get("background"),got.get("background"));assertEquals(true,got.get("ordered"));
            assertEquals(true,got.get("measure_underrepresentation"));assertEquals("bonferroni",got.get("significance_threshold_method"));
            return reply(200,"{\"result\":[],\"meta\":{\"version\":\""+BUILD+"\"}}");})){
            assertEquals(0,client.openSession().profile(CURRENT,p,true).reportedTerms());
        }
    }
    @Test void httpAndProtocolFailuresKeepRawBytesAndNeverRetry() throws Exception {
        for(int code:new int[]{400,401,403,404,408,429,500,503,302}){
            AtomicInteger calls=new AtomicInteger();
            try(var client=new GprofilerGostClient(SETTINGS,r->{calls.incrementAndGet();return reply(code,"raw diagnostic");})){
                var failure=assertThrows(Failure.class,()->client.openSession().profile(CURRENT,payload(),true));
                assertEquals(code==400?2:3,failure.suggestedExitCode());assertEquals(1,calls.get());
                assertArrayEquals(utf8("raw diagnostic"),failure.exchanges().get(0).reply().body());
            }
        }
        for(String body:List.of("{\"result\":[],\"result\":[]}","{\"result\":[],\"meta\":{}}","{\"message\":\"bad input\"}","<html>not JSON</html>")){
            try(var client=new GprofilerGostClient(SETTINGS,r->reply(200,body))){
                var failure=assertThrows(Failure.class,()->client.openSession().profile(CURRENT,payload(),true));assertEquals(2,failure.suggestedExitCode());
                assertArrayEquals(utf8(body),failure.exchanges().get(0).reply().body());
            }
        }
    }
    @Test void incompleteTransfersAndClosedClientCannotAdmitResults() throws Exception {
        for(State state:List.of(State.CAPACITY,State.TIMEOUT,State.INTERRUPTED,State.UNAVAILABLE)){
            try(var client=new GprofilerGostClient(SETTINGS,r->new Reply(200,Map.of(),utf8("prefix"),state,"source condition"))){
                var failure=assertThrows(Failure.class,()->client.openSession().profile(CURRENT,payload(),true));
                assertEquals(state==State.UNAVAILABLE?3:2,failure.suggestedExitCode());assertArrayEquals(utf8("prefix"),failure.exchanges().get(0).reply().body());
            }
        }
        var client=new GprofilerGostClient(SETTINGS,r->reply(200,response(BUILD)));var session=client.openSession();client.close();
        assertThrows(IllegalStateException.class,client::openSession);assertThrows(IOException.class,()->session.profile(CURRENT,payload(),true));
    }
    @Test void realHttpTransportRetainsCapacityPrefixAndDoesNotFollowRedirects() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger followed=new AtomicInteger();
        server.createContext("/big",e->{byte[] body=utf8("x".repeat(4096));e.sendResponseHeaders(200,body.length);try(var out=e.getResponseBody()){out.write(body);}catch(IOException expected){};});
        server.createContext("/redirect",e->{e.getResponseHeaders().add("Location","/target");e.sendResponseHeaders(302,-1);e.close();});
        server.createContext("/target",e->{followed.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        try(var transport=new GprofilerHttpTransport(Duration.ofSeconds(3))){
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            Reply big=transport.send(new Request(URI.create(base+"/big"),utf8("{}"),Duration.ofSeconds(3),32));
            assertEquals(State.CAPACITY,big.state());assertArrayEquals(utf8("x".repeat(32)),big.body());
            Reply redirect=transport.send(new Request(URI.create(base+"/redirect"),utf8("{}"),Duration.ofSeconds(3),32));
            assertEquals(302,redirect.status());assertEquals(State.COMPLETE,redirect.state());assertEquals(0,followed.get());
        }finally{server.stop(0);}
    }
    @Test void realTimeoutRetainsPartialResponse() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newSingleThreadExecutor();var release=new CountDownLatch(1);
        server.setExecutor(executor);server.createContext("/stall",e->{
            e.sendResponseHeaders(200,1000);
            try(var out=e.getResponseBody()){
                out.write(utf8("partial"));out.flush();release.await(10,TimeUnit.SECONDS);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(IOException expected){}
        });server.start();
        try(var transport=new GprofilerHttpTransport(Duration.ofSeconds(3))){
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/stall");
            Reply timed=transport.send(new Request(uri,utf8("{}"),Duration.ofSeconds(2),4096));
            assertEquals(State.TIMEOUT,timed.state());assertArrayEquals(utf8("partial"),timed.body());
        }finally{release.countDown();server.stop(0);executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
}
