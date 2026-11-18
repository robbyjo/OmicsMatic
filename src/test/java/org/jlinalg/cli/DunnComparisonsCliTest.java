/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DunnComparisonsCliTest {
    @TempDir Path temporary;
    private final ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
    private Map<String,Object> request()throws IOException {
        Files.writeString(temporary.resolve("values.tsv"),"group\tvalue\nβ control\t1\nβ control\t2\nβ control\t2\nTreat \"B\"\t2\nTreat \"B\"\t3\nTreat \"B\"\t4\nOther\t100\nOther\t200\n",StandardCharsets.UTF_8);
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("input","values.tsv");m.put("out","result");m.put("design","independent");m.put("adjustment","HOLM");m.put("family","all");m.put("rank_population_declaration","All8 original independent observations in3 explicitly named groups");m.put("data_provenance","Synthetic complete observed vectors; no transformation or omitted rows");return m;
    }
    private int run(Map<String,Object> m)throws IOException {Path p=temporary.resolve("request.json");Files.writeString(p,NextflowJson.json(m));return JLinAlgCli.run(new String[]{"posthoc-tools","dunn","--request",p.toString()},new PrintStream(stdout,true,StandardCharsets.UTF_8),new PrintStream(stderr,true,StandardCharsets.UTF_8));}
    private Map<?,?> report(String folder)throws IOException {return NextflowFiles.object(NextflowFiles.json(temporary.resolve(folder+"/analysis.json"),1048576));}
    @Test void completeNativeFamilyPreservesUnicodeLabelsInputsAndEveryManifestEntry()throws Exception {
        var m=request();byte[] raw=Files.readAllBytes(temporary.resolve("values.tsv"));assertEquals(0,run(m),stderr.toString(StandardCharsets.UTF_8));Path out=temporary.resolve("result");assertArrayEquals(raw,Files.readAllBytes(out.resolve("source-observations.tsv")));assertArrayEquals(raw,Files.readAllBytes(temporary.resolve("values.tsv")));var report=report("result");assertEquals(true,report.get("result_admitted"));assertEquals(3L,report.get("comparisons"));assertEquals(false,report.get("omnibus_significance_gate_applied"));assertTrue(Files.readString(out.resolve("groups.tsv")).contains("\"Treat \"\"B\"\"\""));
        var manifest=NextflowFiles.object(NextflowFiles.json(out.resolve("manifest.json"),1048576));for(var e:manifest.entrySet())assertEquals(NextflowFiles.fingerprint(e.getValue(),1<<20),NextflowFiles.pin(out.resolve((String)e.getKey()),1<<20,()->{}));assertTrue(Files.isRegularFile(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("asymptotic"));
    }
    @Test void controlFamilyAndSelectedPairsUseTheSameCompleteRankPopulation()throws Exception {
        var m=request();assertEquals(0,run(m));double variance=((Number)report("result").get("rank_variance")).doubleValue();m.put("out","control");m.put("family","control");m.put("control","β control");assertEquals(0,run(m));assertEquals(2L,report("control").get("comparisons"));assertEquals(variance,((Number)report("control").get("rank_variance")).doubleValue());
        Files.writeString(temporary.resolve("pairs.tsv"),"first_group\tsecond_group\nOther\tβ control\nTreat \"B\"\tβ control\n");m.put("out","selected");m.put("family","specified");m.remove("control");m.put("comparisons","pairs.tsv");assertEquals(0,run(m));assertEquals(variance,((Number)report("selected").get("rank_variance")).doubleValue());assertTrue(Files.readAllLines(temporary.resolve("selected/comparisons.tsv")).get(1).startsWith("\"Other\"\t\"β control\"\t-"));assertEquals(Files.readString(temporary.resolve("pairs.tsv")),Files.readString(temporary.resolve("selected/source-comparisons.tsv")));
    }
    @Test void allTiedOrNonfiniteObservationsCannotAdmitAPartialFamily()throws Exception {
        var m=request();Files.writeString(temporary.resolve("values.tsv"),"group\tvalue\nA\t0\nB\t-0\n");assertEquals(2,run(m));assertEquals(false,report("result").get("result_admitted"));assertTrue(Files.readString(temporary.resolve("result/FAILURE.txt")).contains("All observations are tied"));assertFalse(Files.exists(temporary.resolve("result/COMPLETED.json")));
        m.put("out","nonfinite");Files.writeString(temporary.resolve("values.tsv"),"group\tvalue\nA\t1\nB\t2\nB\tNaN\n");assertEquals(2,run(m));assertEquals(false,report("nonfinite").get("result_admitted"));assertTrue(Files.isRegularFile(temporary.resolve("nonfinite/source-observations.tsv")));assertFalse(Files.exists(temporary.resolve("nonfinite/comparisons.tsv")));
    }
    @Test void duplicateHypothesesAndFamilySettingConflictsAreRefused()throws Exception {
        var m=request();m.put("family","specified");m.put("comparisons","pairs.tsv");Files.writeString(temporary.resolve("pairs.tsv"),"first_group\tsecond_group\nOther\tβ control\nβ control\tOther\n");assertEquals(2,run(m));assertTrue(Files.readString(temporary.resolve("result/FAILURE.txt")).contains("reversed duplicate"));m.put("out","conflict");m.put("control","Other");assertEquals(2,run(m));assertFalse(Files.exists(temporary.resolve("conflict")));
    }
    @Test void missingInputsAndExplicitlyUnsupportedDesignsSkipWithRequiredActions()throws Exception {
        var m=request();m.put("input","missing.tsv");assertEquals(3,run(m));assertFalse(Files.exists(temporary.resolve("result")));m.put("input","values.tsv");m.put("design","paired");assertEquals(3,run(m));assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("separately qualified implementation"));assertFalse(Files.exists(temporary.resolve("result")));
    }
    @Test void existingOutputAndUnknownSettingsPreserveTheRequestedDestination()throws Exception {
        var m=request();Files.createDirectory(temporary.resolve("result"));Files.writeString(temporary.resolve("result/keep"),"original");assertEquals(2,run(m));assertEquals("original",Files.readString(temporary.resolve("result/keep")));m.put("out","unknown");m.put("infer_test",true);assertEquals(2,run(m));assertFalse(Files.exists(temporary.resolve("unknown")));
    }
    @Test void underflowedPValuesRemainExplicitAlongsideFiniteLogProbabilities()throws Exception {
        var m=request();StringBuilder input=new StringBuilder("group\tvalue\n");for(int group=0;group<3;group++)for(int row=0;row<800;row++)input.append(group).append('\t').append(group).append('\n');Files.writeString(temporary.resolve("values.tsv"),input.toString());assertEquals(0,run(m));assertEquals(true,report("result").get("numeric_zero_probability_observed"));assertTrue(Files.exists(temporary.resolve("result/NUMERIC-WARNING.txt")));assertTrue(Files.readString(temporary.resolve("result/comparisons.tsv")).contains("\t0.0\t-"));
    }
    @Test void brokenConsoleCannotPublishACompletionMarker()throws Exception {
        var m=request();Path p=temporary.resolve("request.json");Files.writeString(p,NextflowJson.json(m));var broken=new PrintStream(new OutputStream(){@Override public void write(int x)throws IOException {throw new IOException("synthetic broken console");}});assertEquals(2,DunnComparisonsCli.run(new String[]{"--request",p.toString()},broken,new PrintStream(stderr,true,StandardCharsets.UTF_8)));assertFalse(Files.exists(temporary.resolve("result/COMPLETED.json")));assertEquals(false,report("result").get("result_admitted"));assertTrue(Files.exists(temporary.resolve("result/unadmitted-analysis.json")));
    }
}
