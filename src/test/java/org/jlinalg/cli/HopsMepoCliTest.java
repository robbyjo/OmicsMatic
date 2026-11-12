/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class HopsMepoCliTest {
    @TempDir Path temp;
    @Test void upstreamDriverZeroCannotMaskFailedChild()throws Exception {
        Path log=temp.resolve("failed-child.stderr");Files.writeString(log,"INFO: Process Exited with status: 0\nINFO: Process Exited with status: 1\n");
        var failure=assertThrows(IOException.class,()->HopsMepoCli.children(log));assertTrue(failure.getMessage().contains("driver zero exit alone is insufficient"));
    }
    @Test void missingExtraMalformedOrFatalChildReceiptsAreRefused()throws Exception {
        List<String> cases=List.of("INFO: Process Exited with status: 0\n","INFO: Process Exited with status: 0\n".repeat(3),"INFO: Process Exited with status: 0\nINFO: Process Exited with status: invalid\n","SEVERE: error\n"+"INFO: Process Exited with status: 0\n".repeat(2));
        for(int i=0;i<cases.size();i++){Path log=temp.resolve("bad"+i);Files.writeString(log,cases.get(i));assertThrows(IOException.class,()->HopsMepoCli.children(log));}
    }
    @Test void bothOriginalChildSuccessesAreRequired()throws Exception {
        Path log=temp.resolve("success.stderr");Files.writeString(log,"Nov 12, 2026 ProcessExecutor run\nINFO: Process Exited with status: 0\nINFO: null device\nINFO: Process Exited with status: 0\nINFO: HOPS run submitted to slurm!\n");assertEquals(List.of(0,0),HopsMepoCli.children(log));
    }
    @Test void scientificProfilesAreExplicitAndDoNotFallback()throws Exception {
        assertEquals("source-def-anc",HopsMepoCli.profile("source-def-anc"));assertEquals("complete-def-anc",HopsMepoCli.profile("complete-def-anc"));
        for(String s:List.of("source-default","default","def_anc","", "SOURCE-DEF-ANC"))assertThrows(IOException.class,()->HopsMepoCli.profile(s));
    }
    @Test void helpMakesOriginalMethodAndPrerequisiteRecoveryVisible()throws Exception {
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();assertEquals(0,HopsMepoCli.run(new String[]{"--help"},stream(stdout),stream(stderr)));String text=stdout.toString(StandardCharsets.UTF_8);assertTrue(text.contains("--profile source-def-anc|complete-def-anc"));assertTrue(text.contains("SKIP3"));assertTrue(text.contains("Required action:"));assertEquals("",stderr.toString(StandardCharsets.UTF_8));
    }
    @Test void inexactResourceDeclarationFailsBeforeAnyExecution()throws Exception {
        Path post=Files.createDirectory(temp.resolve("post"));var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();Path out=temp.resolve("new");
        int code=HopsMepoCli.run(new String[]{"--out",out.toString(),"--post-runtime",post.toString(),"--profile","source-def-anc","--input-provenance","supplied","--threads","2.5"},stream(stdout),stream(stderr));
        assertEquals(2,code);assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Positive exact integer required: --threads"));assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("NO RESULT ADMITTED"));assertFalse(Files.exists(out));
    }
    @Test void existingAttemptIsRetained()throws Exception {
        Path out=Files.createDirectory(temp.resolve("prior"));Path keep=out.resolve("KEEP");Files.writeString(keep,"retain\n");var stderr=new ByteArrayOutputStream();assertEquals(2,HopsMepoCli.run(new String[]{"--out",out.toString()},stream(new ByteArrayOutputStream()),stream(stderr)));assertEquals("retain\n",Files.readString(keep));try(var files=Files.list(out)){assertEquals(List.of(keep),files.toList());}
    }
    @Test void originalFatalLogCannotBeAdmittedBySuccessfulExitCounters()throws Exception {
        Path original=Files.createDirectory(temp.resolve("original"));Files.writeString(original.resolve("error.txt"),"SEVERE: original extraction failed\n");
        var caps=new MaltExtractWorkflowCli.Caps(1000000000,4000000000L,1000000,10000000,100,10000,10000000,1000000000,5000000,600,4096,2,2);
        assertTrue(assertThrows(IOException.class,()->HopsMepoCli.originalErrors(original,caps)).getMessage().contains("fatal error despite possible zero exit"));
    }
    private static PrintStream stream(ByteArrayOutputStream bytes){return new PrintStream(bytes,true,StandardCharsets.UTF_8);}
}
