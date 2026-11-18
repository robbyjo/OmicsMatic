/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LdscToolsCliTest {
    @TempDir Path temp;
    private Map<String,Object> settings() {
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("input","input.tsv");result.put("out","result");result.put("reference_variants",1000000);result.put("n_blocks",3);result.put("intercept","free");result.put("two_step",null);result.put("data_provenance","Synthetic numerical control");result.put("row_order_declaration","Declared synthetic consecutive row blocks");return result;
    }
    private Path prepare(Map<String,Object> request)throws IOException {
        var text=new StringBuilder("variant\tchi_square\tld_score\tweight_ld_score\tn\n");
        for(int i=0;i<18;i++)text.append("rs").append(i).append('\t').append(1.2+.02*i+(i%3)*.03).append('\t').append(2+i).append('\t').append(1+i/2.0).append('\t').append(50000+i*100).append('\n');
        Files.writeString(temp.resolve("input.tsv"),text);Path path=temp.resolve("request.json");Files.writeString(path,NextflowJson.json(request));return path;
    }
    private int run(Path request,ByteArrayOutputStream error){return LdscToolsCli.run(new String[]{"h2","--request",request.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error));}
    @Test void completeNativeFitRetainsOriginalInputsAndDiagnostics()throws Exception {
        Path request=prepare(settings());assertEquals(0,run(request,new ByteArrayOutputStream()));Path result=temp.resolve("result");assertTrue(Files.isRegularFile(result.resolve("COMPLETED.json")));assertTrue(Files.readString(result.resolve("analysis.json")).contains("\"result_admitted\":true"));assertArrayEquals(Files.readAllBytes(temp.resolve("input.tsv")),Files.readAllBytes(result.resolve("source-input.tsv")));assertTrue(Files.readString(result.resolve("weight-updates.tsv")).contains("one-step"));
    }
    @Test void fixedInterceptHasExplicitNaRatherThanAnEstimatedSe()throws Exception {
        var m=settings();m.put("intercept",1);Path request=prepare(m);assertEquals(0,run(request,new ByteArrayOutputStream()));assertTrue(Files.readString(temp.resolve("result/estimates.tsv")).contains("intercept_se\tNA"));assertTrue(Files.readString(temp.resolve("result/WARNINGS.md")).contains("because the intercept was fixed"));
    }
    @Test void twoStepExcludesOnlyTheInterceptPopulation()throws Exception {
        var m=settings();m.put("two_step",2);Path request=prepare(m);Files.writeString(temp.resolve("input.tsv"),"variant\tchi_square\tld_score\tweight_ld_score\tn\na\t30\t1\t1\t50000\nb\t1.1\t2\t2\t50000\nc\t1.3\t3\t3\t50000\nd\t1.4\t4\t4\t50000\ne\t1.6\t5\t5\t50000\nf\t1.7\t6\t6\t50000\ng\t1.8\t7\t7\t50000\nh\t35\t8\t8\t50000\n");
        assertEquals(0,run(request,new ByteArrayOutputStream()));String text=Files.readString(temp.resolve("result/analysis.json"));assertTrue(text.contains("\"rows\":8"));assertTrue(text.contains("\"step1_rows\":6"));assertEquals(9,Files.readAllLines(temp.resolve("result/row-audit.tsv")).size());
    }
    @Test void missingInputIsProminentSkip()throws Exception {
        Path request=prepare(settings());Files.delete(temp.resolve("input.tsv"));var error=new ByteArrayOutputStream();assertEquals(3,run(request,error));assertTrue(error.toString().contains("SKIP3: DEPENDENT ANALYSIS SKIPPED"));assertTrue(error.toString().contains("REQUIRED ACTION"));
    }
    @Test void duplicatedVariantAndNonfiniteRowsAreNeverRepaired()throws Exception {
        Path request=prepare(settings());Files.writeString(temp.resolve("input.tsv"),"variant\tchi_square\tld_score\tweight_ld_score\tn\na\t1\t2\t3\t4\na\t2\t3\t4\t5\n");var error=new ByteArrayOutputStream();assertEquals(2,run(request,error));assertTrue(error.toString().contains("Duplicate variant"));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
    @Test void fixedAndTwoStepConflictRefusesCompleteResult()throws Exception {
        var m=settings();m.put("intercept",1);m.put("two_step",30);Path request=prepare(m);assertEquals(2,run(request,new ByteArrayOutputStream()));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
    @Test void rootDispatcherProvidesMethodHelpBeforeProjectConfiguration() {
        var output=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"ldsc-tools","--help"},new PrintStream(output),System.err));assertTrue(output.toString().contains("unpartitioned observed-scale"));
    }
    @Test void geneticCorrelationRequiresItsOwnRequestRatherThanSubstitutingHsq() {
        var error=new ByteArrayOutputStream();assertEquals(3,LdscToolsCli.run(new String[]{"rg","--request","unread.json"},System.out,new PrintStream(error)));assertTrue(error.toString().contains("SKIP3: DEPENDENT ANALYSIS SKIPPED"));
    }
}
