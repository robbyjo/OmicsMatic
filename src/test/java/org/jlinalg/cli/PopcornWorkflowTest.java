/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;
class PopcornWorkflowTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path temp;
    @SuppressWarnings("unchecked") private Map<String,Object> request(String mode)throws Exception{return(Map<String,Object>)paths(SimpleJson.parse(NextflowJson.json(PopcornToolsCli.example(mode))));}
    private Object paths(Object value){if(value instanceof String s&&s.startsWith("/absolute/"))return temp.resolve(s.substring(10)).toAbsolutePath().toString();if(value instanceof List<?> l)return l.stream().map(this::paths).toList();if(value instanceof Map<?,?> m){var r=new LinkedHashMap<String,Object>();m.forEach((k,v)->r.put((String)k,paths(v)));return r;}return value;}
    @SuppressWarnings("unchecked") private Map<String,Object> options(Map<String,Object> request){return(Map<String,Object>)request.get("options");}
    @Test void allModeExamplesDescribeExactInputRoles()throws Exception {
        for(String mode:List.of("compute","fit","compute-fit")){var spec=PopcornRequest.parse(request(mode));assertEquals(!mode.equals("fit"),spec.compute());assertEquals(!mode.equals("compute"),spec.fit());}
    }
    @Test void sourceModeAndInputConflictsAreRefused()throws Exception {
        var r=request("fit");options(r).put("correlation","genetic-effect");assertThrows(IOException.class,()->PopcornRequest.parse(r));
        var a=request("compute");a.put("score_file",temp.resolve("scores").toString());assertThrows(IOException.class,()->PopcornRequest.parse(a));
        var b=request("fit");b.put("extract",temp.resolve("extract").toString());assertThrows(IOException.class,()->PopcornRequest.parse(b));
    }
    @Test void sourceBrokenAndAmbiguousModelsAreExplicit()throws Exception {
        var r=request("fit");options(r).put("intercept",false);assertThrows(IOException.class,()->PopcornRequest.parse(r));
        var s=request("compute");options(s).put("from_bp",100);assertThrows(IOException.class,()->PopcornRequest.parse(s));
        var t=request("compute-fit");options(t).put("h2weight",true);options(t).put("correlation","genetic-effect");assertThrows(IOException.class,()->PopcornRequest.parse(t));
    }
    @Test void completeRowReaderRejectsTruncationMalformedAndCapacity()throws Exception {
        Path p=temp.resolve("aligned.tsv");String h="N1\tN2\tZ1\tZ2\tscore1\tscore2\tscoreX\n";Files.writeString(p,h+"100\t200\t1\t2\t3\t4\t5\n100\t200\t2\t3\t4\t5\t6\n");assertEquals(2,PopcornBundle.readRows(p,2,256,()->{}).size());assertThrows(IOException.class,()->PopcornBundle.readRows(p,1,256,()->{}));Files.writeString(p,h+"100\t200\tNaN\t2\t3\t4\t5\n");assertThrows(IOException.class,()->PopcornBundle.readRows(p,10,256,()->{}));Files.writeString(p,h+"100\t200\t1\t2\t3\t4\n");assertThrows(IOException.class,()->PopcornBundle.readRows(p,10,256,()->{}));
    }
    @Test void outputReuseAndNoncanonicalPathsCannotOverwriteEvidence()throws Exception {
        assertThrows(IOException.class,()->PopcornRuntime.newRoot(temp));assertThrows(IOException.class,()->PopcornRuntime.absolute("relative"));assertThrows(IOException.class,()->PopcornRuntime.absolute("/tmp/../tmp/data"));
    }
    @Test void cliErrorsGiveProminentRequiredAction()throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=PopcornToolsCli.run(new String[]{"--unknown"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertEquals(2,code);assertTrue(err.toString(StandardCharsets.UTF_8).contains("FAIL2 NO COMPLETE POPCORN WORKFLOW"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("WARNINGS AND REQUIRED ACTION"));
    }
}
