/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Model admission, dependency skips and exact indexed transport; no R needed. */
final class Deseq2InferenceContractsTest {
    @TempDir Path temp;
    private static Map<String,Object> base() {
        var value=new LinkedHashMap<String,Object>();value.putAll(Map.of("schema_version",1,"mode","ANALYZE","profile",Deseq2Contracts.PROFILE,"out","analysis","counts","counts.tsv","samples","samples.tsv","rscript","missing-Rscript.exe","r_libraries",List.of("missing-library")));
        value.put("variables",List.of(Map.of("name","condition","kind","factor","levels",List.of("对照","处理"))));
        value.put("design",Map.of("terms",List.of(List.of("condition"))));
        value.put("results",List.of(Map.of("id","difference","selector",Map.of("kind","factor","factor","condition","numerator","处理","denominator","对照"))));return value;
    }
    private static Map<String,Object> changed(String key,Object value){var r=base();r.put(key,value);return r;}
    @Test void explicitContrastsAndPreparationAreRetained()throws Exception {
        var r=Deseq2Contracts.validate(base());var result=Deseq2Contracts.object(((List<?>)r.get("results")).get(0),"result");
        var selector=Deseq2Contracts.object(result.get("selector"),"selector");assertEquals("处理",selector.get("numerator"));assertEquals("l2",selector.get("numerator_code"));assertEquals("l1",selector.get("denominator_code"));
        assertEquals("ratio",Deseq2Contracts.object(r.get("fit"),"fit").get("size_factor_type"));
        assertEquals("integer_only",r.get("count_preparation"));
        var explicit=changed("units","estimated_counts");explicit.put("count_preparation","R_round");assertEquals("R_round",Deseq2Contracts.validate(explicit).get("count_preparation"));
    }
    @TestFactory Stream<DynamicTest> unsafeUnknownAndContradictorySettingsAreRefused() {
        var invalid=new ArrayList<Map<String,Object>>();
        invalid.add(changed("profile","DESeq2-latest"));invalid.add(changed("schema_version",2));invalid.add(changed("unexpected",1));
        invalid.add(changed("units","TPM"));invalid.add(changed("count_preparation","R_round"));invalid.add(changed("r_libraries",List.of()));
        invalid.add(changed("variables",List.of(Map.of("name","x;system('cmd')","kind","numeric"))));
        invalid.add(changed("variables",List.of(Map.of("name","if","kind","numeric"))));
        invalid.add(changed("variables",List.of(Map.of("name","condition","kind","factor","levels",List.of("a","a")))));
        invalid.add(changed("design",Map.of("terms",List.of(List.of("unknown")))));
        invalid.add(changed("design",Map.of("terms",List.of("condition"))));
        invalid.add(changed("design",Map.of("terms",List.of(List.of("condition","condition")))));
        invalid.add(changed("design",Map.of("terms",List.of(List.of("condition"),List.of("condition")))));
        invalid.add(changed("design",Map.of("intercept",false,"terms",List.of())));
        invalid.add(changed("fit",Map.of("test","LRT")));invalid.add(changed("fit",Map.of("test","none")));
        invalid.add(changed("fit",Map.of("betaPriorr",true)));invalid.add(changed("fit",Map.of("size_factor_type","other")));
        invalid.add(changed("fit",Map.of("min_mu",0)));invalid.add(changed("fit",Collections.singletonMap("min_replicates_for_replace",null)));
        invalid.add(changed("timeout_seconds",0));invalid.add(changed("maximum_entries",1.5));invalid.add(changed("maximum_model_columns",0));
        invalid.add(changed("transforms",List.of(Map.of("id","bad","method","vst","beta_prior_variance",1))));
        invalid.add(changed("transforms",List.of(Map.of("id","bad","method","rlog","nsub",1))));
        invalid.add(changed("transforms",List.of(Map.of("id","../bad","method","rlog"))));
        var both=changed("size_factors","sf.tsv");both.put("normalization_factors","nf.tsv");invalid.add(both);
        var collapse=changed("collapse_by","condition");invalid.add(collapse);
        for(Map<String,Object> selector:List.of(Map.<String,Object>of("kind","coefficient","index",0),Map.<String,Object>of("kind","numeric","weights",List.of(0,0)),Map.<String,Object>of("kind","factor","factor","condition","numerator","missing","denominator","对照")))
            invalid.add(changed("results",List.of(Map.of("id","r","selector",selector))));
        var result=new LinkedHashMap<String,Object>();result.putAll(Map.of("id","r","selector",Map.of("kind","coefficient","index",2)));result.put("cooks_cutoff",null);invalid.add(changed("results",List.of(result)));
        invalid.add(changed("results",List.of(Map.of("id","r","selector",Map.of("kind","factor","factor","condition","numerator","处理","denominator","对照"),"shrink",Map.of("type","apeglm")))));
        var tests=new ArrayList<DynamicTest>();for(int i=0;i<invalid.size();i++){var r=invalid.get(i);tests.add(DynamicTest.dynamicTest("invalid-contract-"+i,()->assertThrows(IOException.class,()->Deseq2Contracts.validate(r))));}return tests.stream();
    }
    private void inputs(String counts,String metadata)throws IOException {
        Files.createDirectories(temp.resolve("inputs"));Files.writeString(temp.resolve("inputs/counts.tsv"),counts,StandardCharsets.UTF_8);Files.writeString(temp.resolve("inputs/samples.tsv"),metadata,StandardCharsets.UTF_8);
    }
    @Test void unicodeTransportRoundTripAndNoCountRounding()throws Exception {
        inputs("gene\t样本α\t样本β\n基因α\t1\t2\n基因β\t3\t4\n","sample\tcondition\n样本α\t对照\n样本β\t处理\n");
        var request=Deseq2Contracts.validate(base());var p=Deseq2Inputs.prepare(temp,request,false);
        assertEquals(List.of("基因α","基因β"),p.genes());assertEquals(List.of("样本α","样本β"),p.samples());
        assertTrue(Files.readString(temp.resolve("worker/counts.tsv")).contains("g1\t1.0\t2.0"));assertTrue(Files.readString(temp.resolve("worker/samples.tsv")).contains("s1\tl1"));
        Deseq2Inputs.prepare(temp,request,true);
        Files.writeString(temp.resolve("worker/counts.tsv"),"altered\n");assertThrows(IOException.class,()->Deseq2Inputs.prepare(temp,request,true));
    }
    @Test void fractionalRawCountsAndInconsistentTechnicalCovariatesFail()throws Exception {
        inputs("gene\tx\ty\ng\t1.5\t2\n","sample\tcondition\tx\t对照\ny\t处理\n");
        assertThrows(IOException.class,()->Deseq2Inputs.prepare(temp,Deseq2Contracts.validate(base()),false));
        Files.writeString(temp.resolve("inputs/counts.tsv"),"gene\tx\ty\ng\t1\t2\n");
        Files.writeString(temp.resolve("inputs/samples.tsv"),"sample\tcondition\tbio\nx\t对照\tgroup\ny\t处理\tgroup\n");
        var r=base();r.put("collapse_by","bio");assertThrows(IOException.class,()->Deseq2Inputs.prepare(temp,Deseq2Contracts.validate(r),false));
    }
    @Test void missingRuntimePublishesProminentSkipAndCannotVerifyAsSuccess()throws Exception {
        Files.writeString(temp.resolve("counts.tsv"),"gene\tx\ty\ng\t1\t2\n");Files.writeString(temp.resolve("samples.tsv"),"sample\tcondition\nx\t对照\ny\t处理\n",StandardCharsets.UTF_8);
        Deseq2Contracts.json(temp.resolve("request.json"),base());var bytes=new ByteArrayOutputStream();
        int code;try(var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8)){code=Deseq2InferenceCli.run(new String[]{"--request",temp.resolve("request.json").toString()},stream,stream);}
        assertEquals(3,code,bytes.toString(StandardCharsets.UTF_8));String warnings=Files.readString(temp.resolve("analysis/WARNINGS.md"));assertTrue(warnings.startsWith("# SKIP3"));assertTrue(warnings.contains("Required action:"));assertTrue(warnings.contains("installation is forbidden"));
        assertEquals(false,Deseq2Contracts.read(temp.resolve("analysis/analysis-status.json")).get("results_admitted"));
        assertThrows(IOException.class,()->Deseq2InferenceCli.verify(temp.resolve("analysis")));
        assertFalse(Files.exists(temp.resolve("analysis/normalized-counts.tsv")));
    }
    @Test void sourceResourcesCannotBeReplacedByAResealedCapture()throws Exception {
        Deseq2Runtime.sources(temp,false);Deseq2Runtime.sources(temp,true);
        Files.writeString(temp.resolve("source/reference/R/core.R"),"altered source\n");assertThrows(IOException.class,()->Deseq2Runtime.sources(temp,true));
    }
}
