/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaltExtractWorkflowCliTest {
    @TempDir Path temporary;
    private String[] arguments(Path out)throws IOException {
        Path resources=temporary.resolve("resources");if(!Files.exists(resources)){Files.createDirectory(resources);Files.writeString(resources.resolve("ncbi.map"),"9606\tHomo sapiens\n");Files.writeString(resources.resolve("ncbi.tre"),"9606:1;");Files.writeString(temporary.resolve("source.rma6"),"retained complete input bytes");Files.writeString(temporary.resolve("targets.txt"),"Homo sapiens\n");Files.writeString(temporary.resolve("samples.tsv"),"sample_id\trma6\ncontrol\tsource.rma6\n");}
        return new String[]{"--runtime",temporary.resolve("missing-provider").toString(),"--resources",resources.toString(),"--targets",temporary.resolve("targets.txt").toString(),"--rma-list",temporary.resolve("samples.tsv").toString(),"--profile","source-default","--input-provenance","supplied","--out",out.toString()};
    }
    @Test void helpExplainsExplicitProfilesOriginalStatisticsAndSkipRecovery() {
        var out=new ByteArrayOutputStream();assertEquals(0,MaltExtractWorkflowCli.run(new String[]{"--help"},new PrintStream(out),System.err));String text=out.toString();for(String token:new String[]{"source-default","complete-def-anc","NaN/Inf","Required action:","skip dependent analysis","sample_id<TAB>rma6"})assertTrue(text.contains(token),token);
    }
    @Test void missingRuntimeSkipsAndRetainsAllCapturedInputsWithProminentRecovery()throws Exception {
        Path out=temporary.resolve("skip");var console=new PrintStream(new ByteArrayOutputStream());assertEquals(3,MaltExtractWorkflowCli.run(arguments(out),console,console));assertTrue(Files.readString(out.resolve("WARNINGS_FAILURE.md")).startsWith("# SKIP3 DEPENDENT ANALYSIS SKIPPED"));assertTrue(Files.readString(out.resolve("bundle/FAILURE.json")).contains("Required action:"));assertEquals("retained complete input bytes",Files.readString(out.resolve("bundle/inputs/rma/control.rma6")));assertFalse(Files.exists(out.resolve("bundle/COMPONENT_COMPLETED.json")));
    }
    @Test void existingOutputIsRetainedWithoutNewDiagnostics()throws Exception {
        Path out=Files.createDirectory(temporary.resolve("prior"));Files.writeString(out.resolve("keep.txt"),"prior evidence");var console=new PrintStream(new ByteArrayOutputStream());assertEquals(2,MaltExtractWorkflowCli.run(arguments(out),console,console));try(var files=Files.list(out)){assertEquals(1,files.count());}assertEquals("prior evidence",Files.readString(out.resolve("keep.txt")));
    }
    @Test void duplicateUnknownAndInexactCapacityOptionsFailBeforeCreatingOutput()throws Exception {
        var console=new PrintStream(new ByteArrayOutputStream());assertEquals(2,MaltExtractWorkflowCli.run(new String[]{"--runtime","a","--runtime","b"},console,console));Path out=temporary.resolve("never");var args=new java.util.ArrayList<>(java.util.List.of(arguments(out)));args.addAll(java.util.List.of("--maximum-reads","NaN"));assertEquals(2,MaltExtractWorkflowCli.run(args.toArray(String[]::new),console,console));assertFalse(Files.exists(out));
    }
}
