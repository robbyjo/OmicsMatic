/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DunnettCliTest {
    @TempDir Path temp;
    private record Run(int code,String stdout,String stderr) { }
    private Run run(Path request) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=PosthocToolsCli.run(new String[]{"dunnett","--request",request.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private Path request(Map<String,Object> changes)throws IOException {
        Path input=temp.resolve("input.tsv");if(!Files.exists(input))Files.writeString(input,"group\tvalue\nTreatment\t4\nControl\t1\nTreatment\t6\nControl\t3\n");
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("input","input.tsv");m.put("out","result");m.put("control","Control");m.put("design","independent-common-variance");m.put("confidence_level",.95);m.put("data_provenance","Synthetic independent observations");m.put("assumptions_declaration","Independent Gaussian common-variance model explicitly selected");m.put("r_home","missing-runtime");m.put("rscript","missing-runtime/bin/Rscript");m.put("library","missing-library");m.put("startup_warning_policy","FAIL");m.putAll(changes);
        Path path=temp.resolve("request.json");Files.writeString(path,NextflowJson.json(m));return path;
    }
    @Test void unavailableRuntimeIsProminentSkipWithRetainedNativeFit()throws Exception {
        var result=run(request(Map.of()));assertEquals(3,result.code());assertTrue(result.stderr().contains("SKIP3: DEPENDENT ANALYSIS SKIPPED"));assertTrue(result.stderr().contains("REQUIRED ACTION"));
        assertTrue(Files.isRegularFile(temp.resolve("result/native-comparisons.tsv")));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));assertTrue(Files.readString(temp.resolve("result/analysis.json")).contains("\"result_admitted\":false"));
    }
    @Test void repeatedMeasuresAreExplicitlySkipped()throws Exception {
        var result=run(request(Map.of("design","repeated-measures")));assertEquals(3,result.code());assertTrue(result.stderr().contains("different qualified method"));assertFalse(Files.exists(temp.resolve("result/native-comparisons.tsv")));
    }
    @Test void missingExactControlFailsWithoutNormalization()throws Exception {
        var result=run(request(Map.of("control","control")));assertEquals(2,result.code());assertTrue(result.stderr().contains("Exact control label absent"));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
    @Test void incompleteRowsAreNotDropped()throws Exception {
        Path r=request(Map.of());Files.writeString(temp.resolve("input.tsv"),"group\tvalue\nControl\t1\n\nTreatment\t2\n");assertEquals(2,run(r).code());assertArrayEquals(Files.readAllBytes(temp.resolve("input.tsv")),Files.readAllBytes(temp.resolve("result/source-input.tsv")));
    }
    @Test void rejectsUnknownSettingsAndPreservesExistingDestination()throws Exception {
        assertEquals(2,run(request(Map.of("infer_variance",true))).code());assertFalse(Files.exists(temp.resolve("result")));
        Path r=request(Map.of());Files.createDirectory(temp.resolve("result"));Files.writeString(temp.resolve("result/keep.txt"),"preserve");assertEquals(2,run(r).code());assertEquals("preserve",Files.readString(temp.resolve("result/keep.txt")));
    }
    @Test void exactControlCanContainUnicodeWithoutBeingRSource()throws Exception {
        Path r=request(Map.of("control","対照'\\x"));Files.writeString(temp.resolve("input.tsv"),"group\tvalue\n対照'\\x\t1\nTreatment\t4\n対照'\\x\t3\nTreatment\t6\n");assertEquals(3,run(r).code());assertTrue(Files.readString(temp.resolve("result/native-groups.tsv")).contains("対照'\\x"));
    }
    @Test void helpDescribesExternalInferenceAndOriginalWorkerIsPackaged()throws Exception {
        var buffer=new ByteArrayOutputStream();int code=PosthocToolsCli.run(new String[]{"dunnett","--help"},new PrintStream(buffer),System.err);assertEquals(0,code);assertTrue(buffer.toString().contains("original multcomp 1.4-29"));
        try(var worker=DunnettCli.class.getResourceAsStream("/org/jlinalg/stats/dunnett-worker.R")){assertNotNull(worker);assertTrue(new String(worker.readAllBytes(),StandardCharsets.UTF_8).contains("mcp(group=\"Dunnett\")"));}
    }
}
