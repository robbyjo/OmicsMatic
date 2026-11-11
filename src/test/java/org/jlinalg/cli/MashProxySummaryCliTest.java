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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class MashProxySummaryCliTest {
    @TempDir static Path temp;
    private static Path source;
    private static final Runnable CHECK=()->{};
    private record Run(int code,String out,String err) { }
    private static Run run(String...args){return runWith(()->{},args);}
    private static Run runWith(Runnable hook,String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=MashSummaryCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8),hook);return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    private static Path source()throws Exception {
        if(source!=null)return source;
        Path exported=temp.resolve("native-example");var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(0,MashToolsCli.run(new String[]{"--export-example",exported.toString()},new PrintStream(out),new PrintStream(err)),err.toString(StandardCharsets.UTF_8));source=temp.resolve("native");
        assertEquals(0,MashToolsCli.run(new String[]{"--input",exported.resolve("fit-input").toString(),"--out",source.toString()},new PrintStream(out),new PrintStream(err)),err.toString(StandardCharsets.UTF_8));return source;
    }
    private static Path request(String name)throws Exception {
        Path dir=Files.createDirectory(temp.resolve(name+"-request"));
        Files.writeString(dir.resolve("settings.tsv"),"key\tvalue\nschema_version\t1\nlfsr_threshold\t0.05\nsignificance_boundary\tstrict\nsharing_factor\t0.5\nsharing_transform\tidentity\ncomposite_mode\tindependent_marginal_proxy\nproxy_assumption\tindependent_marginal_proxies\nscore_threshold\t0.05\nscore_boundary\tinclusive\n");
        Files.writeString(dir.resolve("significance-conditions.tsv"),"condition\nA\nB\n");
        Files.writeString(dir.resolve("patterns.tsv"),"pattern\tmode\nboth\tall\neither\tat_least_one\nonly-first\tall\n");
        Files.writeString(dir.resolve("selectors.tsv"),"pattern\trole\tcondition\nboth\tinclude\tA\nboth\tinclude\tB\neither\tinclude\tA\neither\tinclude\tB\nonly-first\tinclude\tA\nonly-first\texclude\tB\n");return dir;
    }
    private static Run produce(Path request,Path root)throws Exception {return run("--native-bundle",source().toString(),"--input",request.toString(),"--out",root.toString());}
    private static Path admitted(String name)throws Exception {Path root=temp.resolve(name);Run r=produce(request(name),root);assertEquals(0,r.code(),r.err());assertTrue(Files.exists(root.resolve("COMPLETED.json")));assertFalse(Files.exists(root.resolve("FAILURE.json")));return root;}
    private static List<String[]> rows(Path root)throws IOException {return Files.readAllLines(root.resolve("results/independent-marginal-proxies.tsv")).stream().skip(1).map(x->x.split("\t",-1)).toList();}
    private static Map<String,Object> object(Object value){assertInstanceOf(Map.class,value);var result=new LinkedHashMap<String,Object>();for(var e:((Map<?,?>)value).entrySet()){assertInstanceOf(String.class,e.getKey());result.put((String)e.getKey(),e.getValue());}return result;}
    private static Map<String,Object> json(Path path)throws IOException {return object(MashFiles.json(path,8_388_608));}
    private static void write(Path path,Object value)throws IOException {Files.writeString(path,MashJson.json(value)+"\n");}
    private static void replace(Path path,String before,String after)throws IOException {String text=Files.readString(path);assertTrue(text.contains(before));Files.writeString(path,text.replace(before,after));}
    private static void reseal(Path root)throws IOException {var m=json(root.resolve("manifest.json"));var inventory=MashFiles.inventory(root,MashSummaryLimits.defaults().files(),CHECK);var f=new TreeMap<>(inventory.files());f.remove("manifest.json");f.remove("COMPLETED.json");m.put("files",f);m.put("directories",new java.util.TreeSet<>(inventory.directories()));write(root.resolve("manifest.json"),m);var marker=json(root.resolve("COMPLETED.json"));marker.put("manifest",MashFiles.pin(root.resolve("manifest.json"),8_388_608,CHECK));write(root.resolve("COMPLETED.json"),marker);}
    private static void rejectUnchanged(Path root)throws IOException {var before=MashFiles.inventory(root,MashSummaryLimits.defaults().files(),CHECK);Run r=run("--verify",root.toString());assertEquals(2,r.code(),r.err());assertTrue(r.err().contains("FAIL2"));assertTrue(r.err().contains("Required action"));assertEquals(before,MashFiles.inventory(root,MashSummaryLimits.defaults().files(),CHECK));}

    @Test void completeProxyScoresUseCapturedPosteriorAndLiteralFormulas()throws Exception {
        Path root=admitted("formula");var data=rows(root);assertEquals(3,data.size());String[] lfsr=Files.readString(root.resolve("native/execution/posterior/lfsr.tsv")).strip().split("\t");double a=1-Double.parseDouble(lfsr[0]),b=1-Double.parseDouble(lfsr[1]);
        assertEquals(a*b,Double.parseDouble(data.get(0)[2]),1e-15);assertEquals(1-(1-a)*(1-b),Double.parseDouble(data.get(1)[2]),1e-15);assertEquals(a*(1-b),Double.parseDouble(data.get(2)[2]),1e-15);
        assertEquals("0001",data.get(0)[0]);assertEquals("both",data.get(0)[1]);assertEquals(0,run("--verify",root.toString()).code());
        assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("INDEPENDENT MARGINAL PROXY"));assertEquals("independent_marginal_proxies",object(json(root.resolve("session.json")).get("proxy_method")).get("assumption"));assertEquals(Boolean.FALSE,json(root.resolve("COMPLETED.json")).get("joint_probability_inferred"));assertFalse(Files.exists(root.resolve("results/marginal-sign-products.tsv")));
    }
    @Test void emptyIdentitiesAndEqualThresholdBoundariesAreExplicit()throws Exception {
        for(String boundary:List.of("inclusive","strict"))for(String mode:List.of("all","at_least_one")){
            String name="empty-"+boundary+mode;Path req=request(name);Files.writeString(req.resolve("patterns.tsv"),"pattern\tmode\nempty\t"+mode+"\n");Files.writeString(req.resolve("selectors.tsv"),"pattern\trole\tcondition\n");replace(req.resolve("settings.tsv"),"score_threshold\t0.05","score_threshold\t"+(mode.equals("all")?"1":"0"));replace(req.resolve("settings.tsv"),"score_boundary\tinclusive","score_boundary\t"+boundary);
            Path root=temp.resolve(name);Run r=produce(req,root);assertEquals(0,r.code(),r.err());String[] p=rows(root).get(0);assertEquals(mode.equals("all")?1:0,Double.parseDouble(p[2]));assertEquals(boundary.equals("inclusive"),Boolean.parseBoolean(p[7]));assertEquals(mode.equals("all")?"DEFINED":"EXACT_ZERO",p[6]);
        }
    }
    @Test void repeatsAndOverlapPreserveLiteralMultiplicationAndDiagnostics()throws Exception {
        Path req=request("repeat");Files.writeString(req.resolve("patterns.tsv"),"pattern\tmode\nx\tall\n");Files.writeString(req.resolve("selectors.tsv"),"pattern\trole\tcondition\nx\tinclude\tA\nx\tinclude\tA\nx\texclude\tA\n");Path root=temp.resolve("repeat");assertEquals(0,produce(req,root).code());String[] p=rows(root).get(0);double q=1-Double.parseDouble(Files.readString(root.resolve("native/execution/posterior/lfsr.tsv")).strip().split("\t")[0]);assertEquals(q*q*(1-q),Double.parseDouble(p[2]),1e-15);assertEquals("1",p[10]);assertEquals("1",p[11]);assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("overlappingScoreCells=1"));
    }
    @Test void portableVerificationDoesNotReopenProducerOrRequest()throws Exception {
        Path req=request("portable"),root=temp.resolve("portable");assertEquals(0,produce(req,root).code());Files.move(req,temp.resolve("portable-request-retained"));Path old=source();Files.move(old,temp.resolve("portable-native-retained"));source=null;
        assertEquals(0,run("--verify",root.toString()).code());
        // Recreate the shared fixture at new paths for subsequent tests.
        Files.move(temp.resolve("native-example"),temp.resolve("portable-native-example-retained"));
    }
    @Test void missingAssumptionFailsBeforeNativeCapture()throws Exception {Path req=request("assumption");replace(req.resolve("settings.tsv"),"proxy_assumption\tindependent_marginal_proxies\n","");Path root=temp.resolve("assumption");Run r=produce(req,root);assertEquals(2,r.code());assertFalse(Files.exists(root.resolve("native")));assertTrue(r.err().contains("Required action"));}
    @Test void nominalAndProxyModesCannotBeSilentlyCombined()throws Exception {Path req=request("unused-nominal");Files.writeString(req.resolve("nominal-p.tsv"),"feature\tA\tB\n0001\t0.01\t0.01\n");Path root=temp.resolve("unused-nominal");assertEquals(2,produce(req,root).code());assertFalse(Files.exists(root.resolve("native")));assertFalse(Files.exists(root.resolve("COMPLETED.json")));}
    @Test void completePatternFamilyCapacityRejectsWithoutAdmittedPrefix()throws Exception {
        Path req=request("capacity");StringBuilder patterns=new StringBuilder("pattern\tmode\n");for(int i=0;i<100;i++)patterns.append("p").append(i).append("\tall\n");Files.writeString(req.resolve("patterns.tsv"),patterns);Files.writeString(req.resolve("selectors.tsv"),"pattern\trole\tcondition\n");
        Path caps=temp.resolve("capacity.json");var limits=object(SimpleJson.parse(MashJson.json(MashSummaryLimits.defaults())));var summary=object(limits.get("summary"));summary.put("inputCells",8);limits.put("summary",summary);write(caps,limits);Path root=temp.resolve("capacity");Run r=run("--native-bundle",source().toString(),"--input",req.toString(),"--out",root.toString(),"--limits",caps.toString());assertEquals(2,r.code());assertFalse(Files.exists(root.resolve("results")));assertFalse(Files.exists(root.resolve("COMPLETED.json")));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("FAIL2"));
    }
    @Test void finalizationTamperingRevokesProxyAdmission()throws Exception {Path req=request("finalization"),root=temp.resolve("finalization");Run r=runWith(()->{try{Files.writeString(root.resolve("results/independent-marginal-proxies.tsv"),"changed\n");}catch(IOException error){throw new java.io.UncheckedIOException(error);}},"--native-bundle",source().toString(),"--input",req.toString(),"--out",root.toString());assertEquals(2,r.code());assertFalse(Files.exists(root.resolve("COMPLETED.json")));assertTrue(Files.exists(root.resolve("REJECTED-COMPLETION.json")));}
    @Test void cancellationReturnsNoAdmittedProxyResult()throws Exception {Path req=request("cancel"),nativePath=source(),root=temp.resolve("cancel");Thread.currentThread().interrupt();try{assertEquals(2,run("--native-bundle",nativePath.toString(),"--input",req.toString(),"--out",root.toString()).code());assertFalse(Files.exists(root.resolve("COMPLETED.json")));}finally{Thread.interrupted();}}
    @Test void unnamedFormalMethodRemainsSkippedWithoutProxyFallback()throws Exception {Path req=request("formal");replace(req.resolve("settings.tsv"),"independent_marginal_proxy\n","formal_composite\n");Path root=temp.resolve("formal");Run r=produce(req,root);assertEquals(3,r.code());assertTrue(r.err().contains("SKIP3"));assertFalse(Files.exists(root.resolve("native")));assertFalse(Files.exists(root.resolve("COMPLETED.json")));}
    @TestFactory Stream<DynamicTest> invalidTypedProxyRequests(){
        var result=new ArrayList<DynamicTest>();for(String name:List.of("mode","duplicate-pattern","unknown-pattern","regex","role","ragged","utf8","empty-family","missing-selectors","boundary","threshold","wrong-assumption"))result.add(DynamicTest.dynamicTest("proxy-invalid-"+name,()->{
            Path req=request("invalid-"+name);switch(name){
                case "mode"->replace(req.resolve("patterns.tsv"),"both\tall","both\tunknown");
                case "duplicate-pattern"->Files.writeString(req.resolve("patterns.tsv"),"pattern\tmode\nboth\tall\nboth\tat_least_one\n");
                case "unknown-pattern"->replace(req.resolve("selectors.tsv"),"both\tinclude","unknown\tinclude");
                case "regex"->replace(req.resolve("selectors.tsv"),"\tA\n","\tA.*\n");
                case "role"->replace(req.resolve("selectors.tsv"),"\tinclude\t","\tignore\t");
                case "ragged"->Files.writeString(req.resolve("selectors.tsv"),"pattern\trole\tcondition\nboth\tinclude\tA\textra\n");
                case "utf8"->Files.write(req.resolve("patterns.tsv"),new byte[]{(byte)0xc3,(byte)0x28});
                case "empty-family"->Files.writeString(req.resolve("patterns.tsv"),"pattern\tmode\n");
                case "missing-selectors"->Files.move(req.resolve("selectors.tsv"),req.getParent().resolve("missing-selectors-retained.tsv"));
                case "boundary"->replace(req.resolve("settings.tsv"),"score_boundary\tinclusive","score_boundary\tunknown");
                case "threshold"->replace(req.resolve("settings.tsv"),"score_threshold\t0.05","score_threshold\tNaN");
                case "wrong-assumption"->replace(req.resolve("settings.tsv"),"proxy_assumption\tindependent_marginal_proxies","proxy_assumption\tjoint_posterior");
                default->fail(name);
            }Path root=temp.resolve("invalid-"+name);Run r=produce(req,root);assertEquals(2,r.code(),r.err());assertFalse(Files.exists(root.resolve("COMPLETED.json")));assertTrue(r.err().contains("FAIL2"));assertTrue(r.err().contains("Required action"));
        }));return result.stream();
    }
    @TestFactory Stream<DynamicTest> resealedProxyScienceAndScopeTampering(){
        var result=new ArrayList<DynamicTest>();for(String name:List.of("score","include","exclude","log","positive-log","state","decision","missing","rounding","repeated","overlap","loss","pattern","warning","scope"))result.add(DynamicTest.dynamicTest("proxy-resealed-"+name,()->{
            Path root=admitted("tamper-"+name);if(name.equals("warning"))Files.writeString(root.resolve("WARNINGS.md"),"unqualified posterior\n");else if(name.equals("scope")){var s=json(root.resolve("session.json"));s.put("proxy_method",Map.of("method","joint_posterior"));write(root.resolve("session.json"),s);}else {
                Path file=root.resolve("results/independent-marginal-proxies.tsv");var lines=new ArrayList<>(Files.readAllLines(file));String[] p=lines.get(1).split("\t",-1);int column=switch(name){case "pattern"->1;case "score"->2;case "include"->3;case "exclude"->4;case "log","positive-log"->5;case "state"->6;case "decision"->7;case "missing"->8;case "rounding"->9;case "repeated"->10;case "overlap"->11;case "loss"->12;default->throw new AssertionError(name);};
                p[column]=column==1?"changed":column==6?"UNKNOWN":column==7||column==12?Boolean.toString(!Boolean.parseBoolean(p[column])):column>=8?Integer.toString(Integer.parseInt(p[column])+1):name.equals("positive-log")?"0.0000000000001":"-0.25";lines.set(1,String.join("\t",p));Files.write(file,lines);
            }reseal(root);rejectUnchanged(root);
        }));return result.stream();
    }
}
