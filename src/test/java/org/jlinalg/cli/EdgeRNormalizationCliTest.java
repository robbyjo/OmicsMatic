/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

final class EdgeRNormalizationCliTest {
    private static final Map<String,Object> REF=reference();
    private static Path home(String name)throws IOException {
        Path base=Path.of(System.getProperty("omicsmatic.edger.test_root","build/edger-native-tests")).toAbsolutePath();Files.createDirectories(base);return Files.createTempDirectory(base,name+"-");
    }
    private record Run(int code,String console) { }
    private static Run call(String... args) {
        var bytes=new ByteArrayOutputStream();try(var console=new PrintStream(bytes,true,StandardCharsets.UTF_8)){
            int code=EdgeRNormalizationCli.run(args,console,console);return new Run(code,bytes.toString(StandardCharsets.UTF_8));
        }
    }
    private static Run run(Path h,Map<String,Object> r)throws IOException {Path request=h.resolve("request-"+UUID.randomUUID()+".json");json(request,r);return call("--request",request.toString());}
    private static Map<String,Object> request(Path h)throws IOException {
        Files.writeString(h.resolve("counts.tsv"),"feature_id\t a \ta\ts3\ng1\t2\t3\t8\ng2\t5\t7\t9\ng3\t1\t9\t4\n",StandardCharsets.UTF_8);
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("edition","3.42.4");r.put("method","TMM");r.put("counts","counts.tsv");r.put("out",h.resolve("analysis").toString());return r;
    }
    @TestFactory Stream<DynamicTest> originalFactorCasesThroughNativeFileWorkflow()throws IOException {
        return list(REF.get("normalizations"),"cases").stream().map(EdgeRNormalizationCliTest::map).filter(r->
            ((String)r.get("name")).startsWith("3.42.4/ties/")||((String)r.get("name")).matches("(?:3\\.32\\.1|3\\.36\\.0|3\\.40\\.2|4\\.10\\.7)/random-24/4"))
            .map(c->DynamicTest.dynamicTest((String)c.get("name"),()->{
                Path h=home("source-factor");var r=request(h);var d=map(map(REF.get("datasets")).get(c.get("dataset")));writeCounts(h,d);
                r.put("edition",((String)c.get("edition")).substring(6).replace('_','.'));r.put("transform","NONE");
                var o=map(c.get("options"));r.put("method",o.get("method"));r.put("logratio_trim",o.get("logratioTrim"));r.put("sum_trim",o.get("sumTrim"));r.put("weighting",o.get("doWeighting"));r.put("a_cutoff",o.get("Acutoff"));r.put("quantile",o.get("p"));
                if(o.get("reference_zero_based")!=null)r.put("reference_sample","s"+((Number)o.get("reference_zero_based")).intValue());
                if(d.get("libraries")!=null){writeVector(h.resolve("libraries.tsv"),"sample_id\tlibrary_size",values(d.get("libraries")),"s");r.put("libraries","libraries.tsv");}
                List<?> expected=values(c.get("factors"));boolean finite=expected.stream().allMatch(v->!Set.of("NA","NaN","Inf","-Inf").contains(v.toString()));
                var run=run(h,r);assertEquals(finite?0:2,run.code,run.console);
                var rows=Files.readAllLines(h.resolve("analysis/normalization.tsv"));assertEquals(expected.size()+1,rows.size());
                for(int j=0;j<expected.size();j++){double actual=Double.parseDouble(rows.get(j+1).split("\t")[2]);String value=expected.get(j).toString();
                    if(value.equals("NA")||value.equals("NaN"))assertTrue(Double.isNaN(actual));else if(value.equals("Inf"))assertEquals(Double.POSITIVE_INFINITY,actual);else if(value.equals("-Inf"))assertEquals(Double.NEGATIVE_INFINITY,actual);else assertEquals(Double.parseDouble(value),actual,4e-13*Math.max(1,Math.abs(actual)));}
                assertEquals(finite?0:2,call("--verify",h.resolve("analysis").toString()).code);
            }));
    }
    @TestFactory Stream<DynamicTest> actualOriginalCpmRpkmThroughFileWorkflow()throws IOException {
        return list(REF.get("transforms"),"transforms").stream().map(EdgeRNormalizationCliTest::map)
            .filter(r->((String)r.get("name")).startsWith("explicit-libraries/"))
            .map(c->DynamicTest.dynamicTest((String)c.get("name"),()->{
                Path h=home("source-transform");var r=request(h);var d=map(map(REF.get("datasets")).get(c.get("dataset")));writeCounts(h,d);
                r.put("edition","4.10.7");r.put("method","none");r.put("transform",((Boolean)c.get("log")?"LOG_":"")+c.get("operation"));r.put("prior_count",c.get("prior"));
                writeVector(h.resolve("libraries.tsv"),"sample_id\tlibrary_size",values(c.get("libraries")),"s");r.put("libraries","libraries.tsv");
                if(c.get("operation").equals("RPKM")){writeVector(h.resolve("lengths.tsv"),"feature_id\tlength_bp",values(c.get("gene_lengths")),"f");r.put("gene_lengths","lengths.tsv");}
                List<?> matrix=values(c.get("values"));boolean finite=matrix.stream().flatMap(v->values(v).stream()).allMatch(v->!Set.of("NA","NaN","Inf","-Inf").contains(v.toString()));
                var run=run(h,r);assertEquals(finite?0:2,run.code,run.console);var rows=Files.readAllLines(h.resolve("analysis/expression.tsv"));
                for(int i=0;i<matrix.size();i++){List<?> expected=values(matrix.get(i));String[] fields=rows.get(i+1).split("\t");for(int j=0;j<expected.size();j++){
                    double actual=Double.parseDouble(fields[j+1]);String value=expected.get(j).toString();if(value.equals("NA")||value.equals("NaN"))assertTrue(Double.isNaN(actual));else assertEquals(Double.parseDouble(value),actual,4e-13*Math.max(1,Math.abs(actual)));}}
                assertEquals(finite?0:2,call("--verify",h.resolve("analysis").toString()).code);
            }));
    }
    @Test void capturedInputsAndLiteralIdentitiesSurviveExternalInputChanges()throws IOException {
        Path h=home("literal");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis");
        assertTrue(Files.readString(a.resolve("normalization.tsv")).contains("\n a \t"));assertTrue(Files.readString(a.resolve("normalization.tsv")).contains("\na\t"));
        Files.writeString(h.resolve("counts.tsv"),"changed original source\n");assertEquals(0,call("--verify",a.toString()).code);
    }
    @Test void resealedNumericResultsDoNotPassArithmeticVerification()throws IOException {
        Path h=home("changed-result");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis");
        Files.writeString(a.resolve("expression.tsv"),Files.readString(a.resolve("expression.tsv")).replaceFirst("[0-9]+\\.[0-9]+","9876.54321"));EdgeRNormalizationCli.seal(a);
        assertEquals(2,call("--verify",a.toString()).code);
    }
    @Test void resealedScientificRequestCannotContradictOriginalRequest()throws IOException {
        Path h=home("changed-request");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis");var changed=read(a.resolve("request.json"));changed.put("prior_count",9);json(a.resolve("request.json"),changed);EdgeRNormalizationCli.seal(a);assertEquals(2,call("--verify",a.toString()).code);
    }
    @Test void extraPayloadIsRefusedAfterResealing()throws IOException {
        Path h=home("extra");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis");Files.writeString(a.resolve("unreferenced.txt"),"extra");EdgeRNormalizationCli.seal(a);assertEquals(2,call("--verify",a.toString()).code);
    }
    @Test void producerPinsAreCheckedAfterResealing()throws IOException {
        Path h=home("producer");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis");json(a.resolve("producer.json"),Map.of());EdgeRNormalizationCli.seal(a);assertEquals(2,call("--verify",a.toString()).code);
    }
    @Test void replayUsesNewSiblingAndLeavesParentVerifiable()throws IOException {
        Path h=home("replay");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis"),b=h.resolve("replay");
        var replay=Map.<String,Object>of("schema_version",1,"mode","REPLAY","analysis",a.toString(),"out",b.toString());var result=run(h,replay);assertEquals(0,result.code,result.console);
        assertArrayEquals(Files.readAllBytes(a.resolve("expression.tsv")),Files.readAllBytes(b.resolve("expression.tsv")));assertEquals(0,call("--verify",a.toString()).code);assertEquals(0,call("--verify",b.toString()).code);
    }
    @Test void nestedReplayIsRefusedBeforeCreatingOutput()throws IOException {
        Path h=home("nested");var r=request(h);assertEquals(0,run(h,r).code);Path a=h.resolve("analysis"),bad=a.resolve("nested/output");
        var result=run(h,Map.of("schema_version",1,"mode","REPLAY","analysis",a.toString(),"out",bad.toString()));assertEquals(2,result.code);assertFalse(Files.exists(a.resolve("nested")));assertEquals(0,call("--verify",a.toString()).code);
    }
    @Test void missingInputAndUnsupportedEditionAreProminentSkips()throws IOException {
        Path h=home("skip");var r=request(h);r.put("counts","missing.tsv");var result=run(h,r);assertEquals(3,result.code);assertTrue(result.console.contains("SKIP3"));assertFalse(Files.exists(h.resolve("analysis")));
        r.put("edition","3.22.3");result=run(h,r);assertEquals(3,result.code);assertTrue(result.console.contains("3.22.3"));
    }
    @Test void unknownDuplicateAndCodeFieldsAreRefused()throws IOException {
        Path h=home("contract");var r=request(h);r.put("r_code","stop('not allowed')");assertEquals(2,run(h,r).code);assertFalse(Files.exists(h.resolve("analysis")));
        Path p=h.resolve("duplicate.json");Files.writeString(p,"{\"mode\":\"RUN\",\"mode\":\"RUN\"}");assertEquals(2,call("--request",p.toString()).code);
    }
    @Test void existingOutputIsPreserved()throws IOException {
        Path h=home("existing");var r=request(h);Path a=h.resolve("analysis");Files.createDirectory(a);Files.writeString(a.resolve("sentinel"),"keep");assertEquals(2,run(h,r).code);assertEquals("keep",Files.readString(a.resolve("sentinel")));
    }
    @Test void duplicateSamplesAndFeaturesAreNotMadeUnique()throws IOException {
        for(String data:List.of("feature_id\ta\ta\ng1\t1\t2\n","feature_id\ta\tb\ng1\t1\t2\ng1\t2\t3\n")){
            Path h=home("duplicate-id");var r=request(h);Files.writeString(h.resolve("counts.tsv"),data);var result=run(h,r);assertEquals(2,result.code);assertTrue(result.console.contains("NO COMPLETE RESULT"));assertEquals(false,read(h.resolve("analysis/analysis-status.json")).get("complete_result"));}
    }
    @Test void nonfiniteCountsAndInvalidUtf8AreNotRepaired()throws IOException {
        for(byte[] data:List.of("feature_id\ta\tb\ng1\tNaN\t2\n".getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xc3,(byte)0x28})){
            Path h=home("invalid-data");var r=request(h);Files.write(h.resolve("counts.tsv"),data);assertEquals(2,run(h,r).code);assertArrayEquals(data,Files.readAllBytes(h.resolve("analysis/inputs/counts.tsv")));}
    }
    @Test void mismatchedLibraryOrderIsNotJoinedOrRecycled()throws IOException {
        Path h=home("library-order");var r=request(h);Files.writeString(h.resolve("libraries.tsv"),"sample_id\tlibrary_size\na\t40\n a \t30\ns3\t50\n");r.put("libraries","libraries.tsv");assertEquals(2,run(h,r).code);
    }
    @Test void capacityFailureRetainsInputsAndNoCompleteResult()throws IOException {
        Path h=home("capacity");var r=request(h);r.put("maximum_entries",1);var result=run(h,r);assertEquals(2,result.code);assertTrue(Files.isRegularFile(h.resolve("analysis/inputs/counts.tsv")));assertEquals(false,read(h.resolve("analysis/analysis-status.json")).get("complete_result"));
    }
    private static void writeCounts(Path h,Map<String,Object> d)throws IOException {
        int samples=((Number)d.get("samples")).intValue();StringBuilder b=new StringBuilder("feature_id");for(int j=0;j<samples;j++)b.append("\ts").append(j);b.append('\n');int i=0;
        for(Object row:values(d.get("counts"))){b.append('f').append(i++);for(Object value:values(row))b.append('\t').append(value);b.append('\n');}Files.writeString(h.resolve("counts.tsv"),b);
    }
    private static void writeVector(Path p,String header,List<?> values,String prefix)throws IOException {StringBuilder b=new StringBuilder(header).append('\n');for(int i=0;i<values.size();i++)b.append(prefix).append(i).append('\t').append(values.get(i)).append('\n');Files.writeString(p,b);}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static List<?> values(Object value){return value==null?List.of():value instanceof List<?> l?l:List.of(value);}
    private static Map<String,Object> reference() {
        try(var in=EdgeRNormalizationCliTest.class.getResourceAsStream("/org/jlinalg/differential/edger-normalization/reference-20261120-v2.zip")){
            if(in==null)throw new IOException("Original reference absent");byte[] raw=in.readAllBytes();if(!EdgeRNormalizationCli.sha(raw).equals("51fa06b23bc9530df7d0447e3391d54a237844c55001673316afcd2c3f4896e2"))throw new IOException("Original fixture changed");
            try(var z=new ZipInputStream(new ByteArrayInputStream(raw))){java.util.zip.ZipEntry e;while((e=z.getNextEntry())!=null)if(e.getName().equals("reference.json"))return map(SimpleJson.parse(new String(z.readAllBytes(),StandardCharsets.UTF_8)));}throw new IOException("Missing original reference.json");
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
