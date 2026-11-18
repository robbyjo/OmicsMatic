/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CytometryGateCliTest {
    @TempDir Path temporary;
    private final ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
    private Path fixture(String resource,String id,String name)throws IOException {
        String text;try(var input=getClass().getResourceAsStream("/org/jlinalg/cytometry/"+resource)){assertNotNull(input);text=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        String[] row=text.lines().filter(s->s.startsWith(id+"\t")).findFirst().orElseThrow().split("\t",-1);return Files.write(temporary.resolve(name),Base64.getDecoder().decode(row[row.length-1]));
    }
    private Map<String,Object> request()throws IOException {
        fixture("gating-input-controls.tsv","gain-and-spill","input.fcs");
        Files.writeString(temporary.resolve("gates.xml"),"<g:Gating-ML xmlns:g=\"http://www.isac-net.org/std/Gating-ML/v2.0/gating\" xmlns:d=\"http://www.isac-net.org/std/Gating-ML/v2.0/datatypes\"><g:RectangleGate g:id=\"Positive\"><g:dimension g:compensation-ref=\"FCS\" g:min=\"0\" g:max=\"1\"><d:fcs-dimension d:name=\"X\"/></g:dimension></g:RectangleGate></g:Gating-ML>");
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("fcs","input.fcs");m.put("gating_ml","gates.xml");m.put("out","result");m.put("legacy_charset","UTF-8");m.put("text_policy","EXACT");m.put("compensation","AUTO");m.put("legacy_zero_log_minimum_as_one",false);m.put("svd_relative_cutoff",1e-12);m.put("population_declaration","Both original synthetic source events in acquisition order");m.put("gating_provenance","Independent explicit X interval [0,1) after gain and spillover");return m;
    }
    private int run(Map<String,Object> m)throws IOException {m.putIfAbsent("numeric_policy","EXACT");Path request=temporary.resolve("request.json");Files.writeString(request,NextflowJson.json(m));return JLinAlgCli.run(new String[]{"cytometry-tools","GATE","--request",request.toString()},new PrintStream(stdout,true,StandardCharsets.UTF_8),new PrintStream(stderr,true,StandardCharsets.UTF_8));}
    @Test void completeNativeRawFileToGatesCapturesInputsAndPinsEveryResult()throws Exception {
        var m=request();byte[] original=Files.readAllBytes(temporary.resolve("input.fcs"));assertEquals(0,run(m),stderr.toString(StandardCharsets.UTF_8));Path out=temporary.resolve("result");assertArrayEquals(original,Files.readAllBytes(out.resolve("inputs/source.fcs")));assertArrayEquals(original,Files.readAllBytes(temporary.resolve("input.fcs")));
        assertEquals("event_index\t\"Positive\"\n1\t1\n2\t0\n",Files.readString(out.resolve("memberships.tsv")));assertTrue(Files.readString(out.resolve("counts.tsv")).contains("\"Positive\"\t\"\"\t1\t2\t2\t50.0\t50.0"));
        var report=NextflowFiles.object(NextflowFiles.json(out.resolve("analysis.json"),1048576));assertEquals(true,report.get("result_admitted"));assertEquals(false,report.get("whole_vendor_tool_qualified"));assertEquals("SPILL",report.get("selected_compensation_keyword"));
        var manifest=NextflowFiles.object(NextflowFiles.json(out.resolve("manifest.json"),1048576));for(var entry:manifest.entrySet())assertEquals(NextflowFiles.fingerprint(entry.getValue(),1<<20),NextflowFiles.pin(out.resolve((String)entry.getKey()),1<<20,()->{}));assertTrue(Files.isRegularFile(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("SKIP3"));
    }
    @Test void undefinedEventRetainsPartialDataWithoutAdmittingAPopulation()throws Exception {
        var m=request();fixture("storage-controls.tsv","nonfinite","nonfinite.fcs");m.put("fcs","nonfinite.fcs");String gate=Files.readString(temporary.resolve("gates.xml")).replace("d:name=\"X\"","d:name=\"channel1\"").replace("g:compensation-ref=\"FCS\"","g:compensation-ref=\"uncompensated\"");Files.writeString(temporary.resolve("gates.xml"),gate);
        assertEquals(2,run(m));Path out=temporary.resolve("result");assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("Source event 1"));assertEquals(false,NextflowFiles.object(NextflowFiles.json(out.resolve("analysis.json"),1048576)).get("result_admitted"));assertTrue(Files.exists(out.resolve("memberships.tsv")));
    }
    @Test void unsupportedWorkspaceSkipsCompleteAnalysisAndExplainsRequiredExport()throws Exception {
        var m=request();Files.writeString(temporary.resolve("gates.xml"),"<Workspace/>");assertEquals(3,run(m));Path out=temporary.resolve("result");assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("SKIP3"));assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("supported complete FCS/Gating-ML export"));
    }
    @Test void missingInputSkipsAndExistingOutputRemainsUntouched()throws Exception {
        var m=request();m.put("fcs","absent.fcs");assertEquals(3,run(m));assertFalse(Files.exists(temporary.resolve("result")));m.put("fcs","input.fcs");Files.createDirectory(temporary.resolve("result"));Files.writeString(temporary.resolve("result/keep"),"original");assertEquals(2,run(m));assertEquals("original",Files.readString(temporary.resolve("result/keep")));assertFalse(Files.exists(temporary.resolve("result/analysis.json")));
    }
    @Test void completePopulationCapacityAndUnknownRequestSettingsFailBeforeEvents()throws Exception {
        var m=request();m.put("maximum_event_gate_pairs",1);assertEquals(2,run(m));assertFalse(Files.exists(temporary.resolve("result/memberships.tsv")));m.put("out","unknown-output");m.remove("maximum_event_gate_pairs");m.put("infer_gate",true);assertEquals(2,run(m));assertFalse(Files.exists(temporary.resolve("unknown-output")));
    }
    @Test void brokenConsoleCannotLeaveACompletionMarker()throws Exception {
        var m=request();m.put("numeric_policy","EXACT");Path request=temporary.resolve("request.json");Files.writeString(request,NextflowJson.json(m));var broken=new PrintStream(new OutputStream(){@Override public void write(int value)throws IOException {throw new IOException("control console failure");}});
        assertEquals(2,CytometryGateCli.run(new String[]{"GATE","--request",request.toString()},broken,new PrintStream(stderr,true,StandardCharsets.UTF_8)));assertFalse(Files.exists(temporary.resolve("result/COMPLETED.json")));assertEquals(false,NextflowFiles.object(NextflowFiles.json(temporary.resolve("result/analysis.json"),1048576)).get("result_admitted"));assertTrue(Files.exists(temporary.resolve("result/unadmitted-analysis.json")));
    }
}
