/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class HopsAssessmentWorkflowCliTest {
    @TempDir Path temporary;
    private List<String> arguments(Path out)throws IOException {
        Path resources=temporary.resolve("resources");if(!Files.exists(resources)) {
            Files.createDirectory(resources);Files.writeString(resources.resolve("ncbi.map"),"9606\tHomo sapiens\n");Files.writeString(resources.resolve("ncbi.tre"),"9606:1;\n");
            Files.writeString(temporary.resolve("source.rma6"),"retained prerequisite-control input\n");Files.writeString(temporary.resolve("targets.txt"),"Homo sapiens\n");Files.writeString(temporary.resolve("samples.tsv"),"sample_id\trma6\ncontrol\tsource.rma6\n");
        }
        return new ArrayList<>(List.of("--maltextract-runtime",temporary.resolve("missing-malt").toString(),"--post-runtime",temporary.resolve("missing-r").toString(),"--resources",resources.toString(),"--targets",temporary.resolve("targets.txt").toString(),"--rma-list",temporary.resolve("samples.tsv").toString(),"--profile","complete-def-anc","--input-provenance","supplied","--emit-json","true","--out",out.toString()));
    }
    private static int run(List<String> args,ByteArrayOutputStream output,ByteArrayOutputStream errors) {
        return HopsAssessmentWorkflowCli.run(args.toArray(String[]::new),new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(errors,true,StandardCharsets.UTF_8));
    }
    @Test void helpExplainsExplicitMethodOriginalProvidersAndRecovery() {
        var out=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();assertEquals(0,run(List.of("--help"),out,error));
        String text=out.toString(StandardCharsets.UTF_8);for(String token:List.of("complete-def-anc","--reader-runtime","--maltextract-runtime","--post-runtime","Required action:","skip dependent assessment","No MALT alignment/index creation"))assertTrue(text.contains(token),token);assertEquals(0,error.size());
    }
    @Test void unavailableExtractionSkipsDependentAssessmentAndRetainsCompleteInputs()throws Exception {
        Path result=temporary.resolve("skip");var error=new ByteArrayOutputStream();assertEquals(3,run(arguments(result),new ByteArrayOutputStream(),error));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("SKIP3 DEPENDENT ASSESSMENT SKIPPED"));assertTrue(error.toString(StandardCharsets.UTF_8).contains("Required action:"));
        assertTrue(Files.readString(result.resolve("WARNINGS_FAILURE.md")).contains("original MaltExtract extraction"));
        assertEquals(Files.readString(temporary.resolve("source.rma6")),Files.readString(result.resolve("extraction/bundle/inputs/rma/control.rma6")));
        assertFalse(Files.exists(result.resolve("assessment")));assertFalse(Files.exists(result.resolve("COMPONENT_COMPLETED.json")));
    }
    @Test void priorOutputIsPreservedWithoutOverwritingItsEvidence()throws Exception {
        Path result=Files.createDirectory(temporary.resolve("prior"));Files.writeString(result.resolve("KEEP.txt"),"prior evidence\n");assertEquals(2,run(arguments(result),new ByteArrayOutputStream(),new ByteArrayOutputStream()));
        try(var files=Files.list(result)){assertEquals(1,files.count());}assertEquals("prior evidence\n",Files.readString(result.resolve("KEEP.txt")));
    }
    @Test void aDifferentExtractionMethodIsNotSilentlySubstituted()throws Exception {
        Path result=temporary.resolve("method");var args=arguments(result);args.set(args.indexOf("--profile")+1,"source-default");var error=new ByteArrayOutputStream();assertEquals(2,run(args,new ByteArrayOutputStream(),error));assertFalse(Files.exists(result));assertTrue(error.toString(StandardCharsets.UTF_8).contains("no filter/method fallback"));
    }
    @Test void unknownAndDuplicateOptionsRefuseBeforeCreatingOutput()throws Exception {
        Path result=temporary.resolve("options");var args=arguments(result);args.addAll(List.of("--unknown","x"));assertEquals(2,run(args,new ByteArrayOutputStream(),new ByteArrayOutputStream()));assertFalse(Files.exists(result));
        args=arguments(result);args.addAll(List.of("--emit-json","false"));assertEquals(2,run(args,new ByteArrayOutputStream(),new ByteArrayOutputStream()));assertFalse(Files.exists(result));
    }
    @Test void outputCannotBeCreatedInsideInputResources()throws Exception {
        var args=arguments(temporary.resolve("resources/nested"));assertEquals(2,run(args,new ByteArrayOutputStream(),new ByteArrayOutputStream()));assertFalse(Files.exists(temporary.resolve("resources/nested")));
    }
    @Test void inexactResourceDeclarationsFailBeforeAnyPostprocessing()throws Exception {
        Path result=temporary.resolve("capacity");var args=arguments(result);args.addAll(List.of("--threads","2.5"));var error=new ByteArrayOutputStream();assertEquals(2,run(args,new ByteArrayOutputStream(),error));assertTrue(error.toString(StandardCharsets.UTF_8).contains("FAIL2 NO RESULT ADMITTED"));assertFalse(Files.exists(result.resolve("assessment")));assertFalse(Files.exists(result.resolve("COMPONENT_COMPLETED.json")));
    }
    @Test void jsonChoiceMustBeExplicitAndExact()throws Exception {
        Path result=temporary.resolve("json");var args=arguments(result);args.set(args.indexOf("--emit-json")+1,"maybe");assertEquals(2,run(args,new ByteArrayOutputStream(),new ByteArrayOutputStream()));assertFalse(Files.exists(result));
    }
}
