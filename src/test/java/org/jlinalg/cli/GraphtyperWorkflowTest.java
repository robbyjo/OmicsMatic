/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphtyperWorkflowTest {
    private static Map<String,Object> copy(Object value)throws IOException {var result=new LinkedHashMap<String,Object>();for(var row:NextflowFiles.object(value).entrySet())result.put(NextflowFiles.text(row.getKey()),row.getValue());return result;}
    private static Object localPaths(Object value)throws IOException {
        if(value instanceof String s&&s.startsWith("/absolute/"))return Path.of("illustrative-input-root").toAbsolutePath().normalize().resolve(s.substring(1)).toString();
        if(value instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();for(var row:map.entrySet())result.put(NextflowFiles.text(row.getKey()),localPaths(row.getValue()));return result;}
        if(value instanceof List<?> list){var result=new ArrayList<Object>();for(Object item:list)result.add(localPaths(item));return result;}return value;
    }
    @Test void explicitEditionAndModeExamplesParse()throws Exception {
        for(String mode:List.of("analysis","legacy-analysis","v1.4-analysis")){
            var raw=SimpleJson.parse(NextflowJson.json(localPaths(GraphtyperToolsCli.example(mode))));var s=GraphtyperRequest.parse(raw);assertEquals(mode.contains("-"),s.legacy());assertEquals("discovery",s.mode());assertEquals(1,s.samples().size());assertEquals(1000000000000L,s.inputBytes());
        }
    }
    @Test void legacyModeCannotBorrowLaterPipelineAndModernFlagsCannotBeIgnored()throws Exception {
        var v=copy(localPaths(GraphtyperToolsCli.example("v1.4-analysis")));var settings=copy(v.get("settings"));settings.put("mode","genotype-only");v.put("settings",settings);assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(v)).getMessage().contains("no genotype-only"));
        var modern=copy(localPaths(GraphtyperToolsCli.example("analysis")));var s=copy(modern.get("settings"));s.put("minimum_variant_support",3);modern.put("settings",s);assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(modern)).getMessage().contains("legacy-only"));
    }
    @Test void actualDistinctInputSampleAndTargetPopulationsRequired()throws Exception {
        var v=copy(localPaths(GraphtyperToolsCli.example("analysis")));v.put("samples",List.of("duplicate","duplicate"));assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(v)).getMessage().contains("unique literal"));
        var a=copy(localPaths(GraphtyperToolsCli.example("analysis")));a.put("reference_index",a.get("reference"));assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(a)).getMessage().contains("cannot alias"));
        var b=copy(localPaths(GraphtyperToolsCli.example("analysis")));b.put("regions",List.of(Map.of("contig","20","start",500,"end",9500),Map.of("contig","20","start",9500,"end",10000)));assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(b)).getMessage().contains("nonoverlapping"));
    }
    @Test void initializationAndCompleteLegacySliceBoundariesMustBeExplicit()throws Exception {
        var v=copy(localPaths(GraphtyperToolsCli.example("analysis")));var s=copy(v.get("settings"));s.put("mode","discovery-with-prior");v.put("settings",s);assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(v)).getMessage().contains("initialization mode"));
        var legacy=copy(localPaths(GraphtyperToolsCli.example("legacy-analysis")));legacy.put("regions",List.of(Map.of("contig","20","start",500,"end",9500)));assertTrue(assertThrows(IOException.class,()->GraphtyperRequest.parse(legacy)).getMessage().contains("complete declared slices"));
    }
    @Test void profilesRetainActualFourSourceIdentitiesAndOldInternalVersion()throws Exception {
        var all=NextflowFiles.object(NextflowFiles.object(GraphtyperRuntime.profiles()).get("profiles"));assertEquals(4,all.size());var v14=GraphtyperRuntime.profile("graphtyper-v1.4-ebd4855d");assertEquals("1.3",v14.get("internal_version"));assertEquals("GPL-3.0",v14.get("licence"));assertEquals(List.of("results"),v14.get("final_roles"));assertEquals("e7ec5ab56a1086ca00cbc88a0e75cf060f960c8e",v14.get("pipeline_commit"));assertEquals("MIT",GraphtyperRuntime.profile("graphtyper-v2.7.5-ddc58a70").get("licence"));
    }
    @Test void frontDoorExplainsScopeAndRecovery()throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=GraphtyperToolsCli.run(new String[]{"--help"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertEquals(0,code);String help=out.toString(StandardCharsets.UTF_8);for(String word:List.of("SKIP3","FAIL2","NEW","--verify","install nothing","Native raw quality"))assertTrue(help.contains(word),word);
        out.reset();code=GraphtyperToolsCli.run(new String[]{"--example","unknown"},new PrintStream(out),new PrintStream(err));assertEquals(2,code);assertTrue(err.toString(StandardCharsets.UTF_8).contains("NO COMPLETE"));
    }
}
