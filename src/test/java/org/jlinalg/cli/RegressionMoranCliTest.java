/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressionMoranCliTest {
    @TempDir Path temp;
    private static PrintStream stream(ByteArrayOutputStream bytes){return new PrintStream(bytes,true,StandardCharsets.UTF_8);}
    private Map<String,Object> inputs()throws IOException {
        var ids=new ArrayList<String>();for(int i=0;i<20;i++)ids.add(i==0?" cell\"β ":i==1?"NA":"C"+i);
        StringBuilder design=new StringBuilder("\ufeffcell_id\tintercept\tposition\n"),weights=new StringBuilder("from_cell\tto_cell\tweight\n"),expression=new StringBuilder("gene_id\t"+String.join("\t",ids)+"\n");
        for(int i=0;i<20;i++){design.append(ids.get(i)).append("\t1\t").append(i/20.0).append('\n');for(int j:new int[]{(i+1)%20,(i+19)%20})weights.append(ids.get(i)).append('\t').append(ids.get(j)).append("\t1\n");}
        for(int g=0;g<3;g++){expression.append(g==0?" gene\"β ":"G"+g);for(int i=0;i<20;i++)expression.append('\t').append(Math.sin(i*(.2+g*.1))+.1*Math.cos(i));expression.append('\n');}
        Files.writeString(temp.resolve("design.tsv"),design);Files.writeString(temp.resolve("weights.tsv"),weights);Files.writeString(temp.resolve("expression.tsv"),expression);
        var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("design","design.tsv");request.put("weights","weights.tsv");request.put("expression","expression.tsv");request.put("response_units","prepared-linear-response");request.put("response_provenance","Explicit deterministic synthetic prepared responses");request.put("population_declaration","All20 synthetic cells and3 genes");request.put("zero_policy",false);request.put("alternative","greater");request.put("out","result");return request;
    }
    private int run(Map<String,Object> request,ByteArrayOutputStream errors)throws IOException {NextflowFiles.writeJson(temp.resolve("request.json"),request);return JLinAlgCli.run(new String[]{"monocle-tools","MORAN-LM","--request",temp.resolve("request.json").toString()},stream(new ByteArrayOutputStream()),stream(errors));}
    private Map<?,?> analysis()throws IOException{return NextflowFiles.object(SimpleJson.parse(Files.readString(temp.resolve("result/analysis.json"))));}

    @Test void rootHelpAndCompleteLiteralInputPopulationAreAvailableWithoutR()throws Exception {
        var help=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"monocle-tools","MORAN-LM","--help"},stream(help),stream(new ByteArrayOutputStream())));assertTrue(help.toString(StandardCharsets.UTF_8).contains("No R runtime"));
        var request=inputs();assertEquals(0,run(request,new ByteArrayOutputStream()));assertEquals(Boolean.TRUE,analysis().get("result_admitted"));
        assertArrayEquals(Files.readAllBytes(temp.resolve("design.tsv")),Files.readAllBytes(temp.resolve("result/inputs/design.tsv")));
        assertTrue(Files.readString(temp.resolve("result/tests.tsv")).contains("\" gene\"\"β \""));
        var rows=NextflowFiles.list(SimpleJson.parse(Files.readString(temp.resolve("result/tests.json"))));assertEquals(3,rows.size());assertEquals(" gene\"β ",NextflowFiles.object(rows.get(0)).get("gene_id"));
        assertEquals(Boolean.TRUE,NextflowFiles.object(analysis().get("utf8_bom_present")).get("design"));assertTrue(Files.isRegularFile(temp.resolve("result/COMPLETED.json")));
    }
    @Test void laterUndefinedGeneRefusesTheWholeFamilyAndKeepsCapturedInputs()throws Exception {
        var request=inputs();String text=Files.readString(temp.resolve("expression.tsv"));text=text.substring(0,text.indexOf('\n',text.indexOf('\n')+1)+1)+"bad"+"\t1".repeat(20)+"\n";Files.writeString(temp.resolve("expression.tsv"),text);
        var errors=new ByteArrayOutputStream();assertEquals(2,run(request,errors));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Gene bad"));assertEquals(Boolean.FALSE,analysis().get("result_admitted"));
        assertFalse(Files.exists(temp.resolve("result/tests.tsv")));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));assertArrayEquals(Files.readAllBytes(temp.resolve("expression.tsv")),Files.readAllBytes(temp.resolve("result/inputs/expression.tsv")));
    }
    @Test void missingInputSkipsAndExistingOutputsAreNeverAdopted()throws Exception {
        var request=inputs();request.put("weights","missing.tsv");var errors=new ByteArrayOutputStream();assertEquals(3,run(request,errors));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertFalse(Files.exists(temp.resolve("result")));
        Files.createDirectory(temp.resolve("occupied"));Files.writeString(temp.resolve("occupied/sentinel"),"retain");var other=new LinkedHashMap<>(request);other.put("weights","weights.tsv");other.put("out","occupied");NextflowFiles.writeJson(temp.resolve("occupied-request.json"),other);
        assertEquals(2,JLinAlgCli.run(new String[]{"monocle-tools","MORAN-LM","--request",temp.resolve("occupied-request.json").toString()},stream(new ByteArrayOutputStream()),stream(new ByteArrayOutputStream())));assertEquals("retain",Files.readString(temp.resolve("occupied/sentinel")));try(var files=Files.list(temp.resolve("occupied"))){assertEquals(1,files.count());}
    }
    @Test void reorderedAxesAndDuplicateWeightsAreRefusedWithoutRepair()throws Exception {
        var request=inputs();String original=Files.readString(temp.resolve("expression.tsv"));String text=original.replace("gene_id\t cell\"β \tNA\t","gene_id\tNA\t cell\"β \t");Files.writeString(temp.resolve("expression.tsv"),text);assertEquals(2,run(request,new ByteArrayOutputStream()));assertEquals(Boolean.FALSE,analysis().get("result_admitted"));assertFalse(Files.exists(temp.resolve("result/tests.tsv")));Files.writeString(temp.resolve("expression.tsv"),original);
        String weights=Files.readString(temp.resolve("weights.tsv"));Files.writeString(temp.resolve("weights.tsv"),weights+weights.lines().skip(1).findFirst().orElseThrow()+"\n");var other=new LinkedHashMap<>(request);other.put("out","duplicate-result");NextflowFiles.writeJson(temp.resolve("duplicate-request.json"),other);assertEquals(2,JLinAlgCli.run(new String[]{"monocle-tools","MORAN-LM","--request",temp.resolve("duplicate-request.json").toString()},stream(new ByteArrayOutputStream()),stream(new ByteArrayOutputStream())));assertTrue(Files.readString(temp.resolve("duplicate-result/FAILURE.txt")).contains("Duplicate directed arc"));
    }
    @Test void unknownFieldsAndWholeFamilyOperationLimitsCannotAdmitPrefixes()throws Exception {
        var request=inputs();request.put("maximum_operations",1);assertEquals(2,run(request,new ByteArrayOutputStream()));assertFalse(Files.exists(temp.resolve("result/tests.tsv")));
        request.put("formula","expression~sex");request.put("out","unknown");NextflowFiles.writeJson(temp.resolve("unknown.json"),request);assertEquals(2,JLinAlgCli.run(new String[]{"monocle-tools","MORAN-LM","--request",temp.resolve("unknown.json").toString()},stream(new ByteArrayOutputStream()),stream(new ByteArrayOutputStream())));assertFalse(Files.exists(temp.resolve("unknown")));
    }
    @Test void failedConsoleRecordingRetainsUnadmittedStatisticsAndNoCompletion()throws Exception {
        var request=inputs();NextflowFiles.writeJson(temp.resolve("request.json"),request);var broken=new PrintStream(new OutputStream(){@Override public void write(int b)throws IOException{throw new IOException("fixture");}});
        assertEquals(2,RegressionMoranCli.run(new String[]{"--request",temp.resolve("request.json").toString()},broken,stream(new ByteArrayOutputStream())));assertEquals(Boolean.FALSE,analysis().get("result_admitted"));assertTrue(Files.isRegularFile(temp.resolve("result/unadmitted-analysis.json")));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
}
