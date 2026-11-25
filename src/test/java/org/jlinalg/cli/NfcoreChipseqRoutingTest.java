/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NfcoreChipseqRoutingTest {
    @TempDir Path temporary;
    static final List<String> ACTIONS=List.of("CHIPSEQ-1.2.1","CHIPSEQ-2.0.0");
    record Result(int code,String output,String error) { }
    Result call(String action,String... tail){
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();var args=new ArrayList<>(List.of("nfcore-tools",action));args.addAll(List.of(tail));
        int code=JLinAlgCli.run(args.toArray(String[]::new),new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));
        return new Result(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    @Test void rootRoutesPreserveExactEditionsAndRequiredActions(){
        for(String action:ACTIONS){var result=call(action,"--help");assertEquals(0,result.code());assertTrue(result.output().contains(action.endsWith("1.2.1")?"EXACT_NEXTFLOW21_HOME":"EXACT_NEXTFLOW22_HOME"));
            for(String text:List.of("--request","--resume","--verify","--export-runtime-setup","SKIP3","FAIL2","Required action:","Cell Ranger"))assertTrue(result.output().contains(text),text);
            assertEquals("",result.error());}
    }
    @Test void missingRuntimeReturnsToCallerWithoutCreatingAnAnalysis(){
        for(String action:ACTIONS){Path out=temporary.resolve(action);var result=call(action,"--request",temporary.resolve("request.json").toString(),"--out",out.toString());assertEquals(3,result.code());assertTrue(result.error().contains("SKIP3 ANALYSIS SKIPPED"));assertTrue(result.error().contains("current completed host setup report"));assertFalse(Files.exists(out));}
    }
    @Test void malformedOptionsAndUnlistedEditionsDoNotFallBack(){
        for(String action:ACTIONS){assertEquals(2,call(action,"--request").code());assertEquals(2,call(action,"--request","a","--request","b").code());}
        for(String action:List.of("CHIPSEQ","CHIPSEQ-latest","CHIPSEQ-2.0.1")){var result=call(action,"--help");assertEquals(2,result.code());assertTrue(result.error().contains("not inferred or substituted"));}
    }
    @Test void bothSetupExportsAreExactAndDoNotInstall()throws Exception{
        for(String action:ACTIONS){Path out=temporary.resolve(action);assertEquals(0,call(action,"--export-runtime-setup",out.toString()).code());assertArrayEquals(NextflowRuntime.resource("install_nextflow.py",NextflowRuntime.INSTALLER),Files.readAllBytes(out.resolve("install_nextflow.py")));assertTrue(Files.readString(out.resolve("README.txt")).contains("--allow-install"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));byte[] before=Files.readAllBytes(out.resolve("export.json"));assertEquals(2,call(action,"--export-runtime-setup",out.toString()).code());assertArrayEquals(before,Files.readAllBytes(out.resolve("export.json")));}
    }
    @Test void directReusableInterfacesReturnWithoutExitingTheHost(){
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes,true,StandardCharsets.UTF_8);
        assertEquals(0,NfcoreChipseqToolsCli.run(new String[]{"--help"},stream,stream));assertEquals(0,NfcoreChipseq200ToolsCli.run(new String[]{"--help"},stream,stream));assertTrue(bytes.size()>0);
    }
    @Test void brokenHelpStreamCannotReportSuccess()throws Exception{
        try(var stream=new PrintStream(new OutputStream(){@Override public void write(int value)throws IOException{throw new IOException("forced unavailable stream");}})){
            for(String action:ACTIONS)assertEquals(2,NfcoreToolsCli.run(new String[]{action,"--help"},stream,stream));
        }
    }
    @Test void missingSavedResultCannotBeAdmitted(){
        for(String action:ACTIONS){var result=call(action,"--verify",temporary.resolve("missing").toString());assertEquals(2,result.code());assertTrue(result.error().contains("FAIL2 NO RESULT"));assertFalse(Files.exists(temporary.resolve("missing")));}
    }
}
