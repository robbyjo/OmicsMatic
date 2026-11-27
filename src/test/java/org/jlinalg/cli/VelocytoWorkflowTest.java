/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static org.junit.jupiter.api.Assertions.*;
class VelocytoWorkflowTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path root;
    @SuppressWarnings("unchecked") private Map<String,Object> request(String mode)throws Exception{return(Map<String,Object>)paths(SimpleJson.parse(NextflowJson.json(VelocytoToolsCli.example(mode))));}
    private Object paths(Object value){if(value instanceof String s&&s.startsWith("/absolute/"))return root.resolve(s.substring(10)).toAbsolutePath().toString();if(value instanceof List<?> l)return l.stream().map(this::paths).toList();if(value instanceof Map<?,?> m){var r=new LinkedHashMap<String,Object>();m.forEach((k,v)->r.put((String)k,paths(v)));return r;}return value;}
    @SuppressWarnings("unchecked") private Map<String,Object> settings(Map<String,Object> r){return(Map<String,Object>)r.get("settings");}
    @Test void bothCounterExamplesAreExplicitAndDistinct()throws Exception {assertFalse(VelocytoRequest.parse(request("analysis")).wide());assertTrue(VelocytoRequest.parse(request("analysis-uint64")).wide());}
    @Test void missingPreparationAndAmbiguousIntegerModeRefuse()throws Exception {var a=request("analysis");a.put("preparation","");assertThrows(IOException.class,()->VelocytoRequest.parse(a));var b=request("analysis");settings(b).put("counter_engine","native-default");assertThrows(IOException.class,()->VelocytoRequest.parse(b));var c=request("analysis-uint64");settings(c).put("output_dtype","uint16");assertThrows(IOException.class,()->VelocytoRequest.parse(c));}
    @Test void duplicateInputsAndUnboundedSourceFlagsCannotPass()throws Exception {var a=request("analysis");a.put("bams",List.of(root.resolve("a.bam").toString(),root.resolve("a.bam").toString()));assertThrows(IOException.class,()->VelocytoRequest.parse(a));var b=request("analysis");settings(b).put("additional_flags",List.of("--anything"));assertThrows(IOException.class,()->VelocytoRequest.parse(b));var c=request("analysis");settings(c).put("logic","SmartSeq2");assertThrows(IOException.class,()->VelocytoRequest.parse(c));}
    @Test void unsafeSampleIdAndUndeclaredUmiRewritingRefuse()throws Exception {var a=request("analysis");settings(a).put("sample_id","../../outside");assertThrows(IOException.class,()->VelocytoRequest.parse(a));var b=request("analysis");settings(b).put("umi_tag","ANY_TAG");assertThrows(IOException.class,()->VelocytoRequest.parse(b));}
    @Test void resourceCapacitiesAreRequiredAndOutputsCannotOverwrite()throws Exception {var a=request("analysis");a.remove("capacity");assertThrows(IOException.class,()->VelocytoRequest.parse(a));assertThrows(IOException.class,()->VelocytoRuntime.newRoot(root));}
    @Test void prominentFailureIncludesConcreteRecoveryPolicy()throws Exception {var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=VelocytoToolsCli.run(new String[]{"--invalid"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertEquals(2,code);String text=err.toString(StandardCharsets.UTF_8);assertTrue(text.contains("FAIL2 NO COMPLETE VELOCYTO COUNT WORKFLOW"));assertTrue(text.contains("WARNINGS AND REQUIRED ACTION"));assertTrue(text.contains("NEW binding/capture paths"));}
}
