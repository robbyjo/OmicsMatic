/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiffbindWorkflowTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private Map<String,Object> example()throws Exception {
        var v=(Map<String,Object>)SimpleJson.parse(NextflowJson.json(DiffbindToolsCli.example("analysis")));v.put("runtime",temp.resolve("runtime").toString());
        for(Object item:(List<?>)v.get("samples")){var sample=(Map<String,Object>)item;String id=sample.get("id").toString();sample.put("bam_reads",temp.resolve(id+".bam").toString());sample.put("peaks",temp.resolve(id+".bed").toString());}return v;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> settings(Map<String,Object> v){return (Map<String,Object>)v.get("settings");}
    @Test void completeTypedExampleAcceptsOnlyExplicitSourceFactors()throws Exception {
        var v=example();var spec=DiffbindRequest.parse(v);assertEquals(6,spec.samples().size());assertEquals(List.of("Tissue","Condition"),NextflowFiles.strings(spec.settings().get("design")));
        settings(v).put("design",List.of("Tissue","Condition;system('echo unsafe')"));assertThrows(IOException.class,()->DiffbindRequest.parse(v));
    }
    @Test void unsupportedParametersDoNotSilentlyUseDefaults()throws Exception {
        var v=example();settings(v).put("fitType","parametric");assertThrows(IOException.class,()->DiffbindRequest.parse(v));
    }
    @Test void insufficientReplicatesAndEngineNormalizationMismatchRefuseTheRequest()throws Exception {
        var insufficient=example();settings(insufficient).put("min_members",4);assertThrows(IOException.class,()->DiffbindRequest.parse(insufficient));
        var wrong=example();settings(wrong).put("normalization","TMM");assertThrows(IOException.class,()->DiffbindRequest.parse(wrong));
    }
    @Test void sourceAndRuntimeProfileArePackagedAndComplete()throws Exception {
        var p=DiffbindRuntime.profile();assertEquals("3.24.0",p.get("DiffBind"));assertEquals("4.6.2",p.get("R"));assertEquals(136,NextflowFiles.list(p.get("packages")).size());assertTrue(NextflowFiles.object(p.get("functions")).containsKey("pv.DESeq2ContrastResults"));assertTrue(DiffbindRuntime.resource("worker.R").length>5000);
    }
    @Test void fullFittedOriginalNamespaceMatchesEveryNativeProjectionBit()throws Exception {
        int cases=0;long rows=0;for(String shrink:List.of("shrink","unshrunk"))for(String fold:List.of("0","1.5")){
            String stem="projection-"+shrink+"-"+fold;Path input=temp.resolve(stem+"-input.tsv"),expected=temp.resolve(stem+"-expected.tsv");
            for(String suffix:List.of("-input.tsv","-expected.tsv"))try(var in=getClass().getResourceAsStream("/org/jlinalg/epigenomics/diffbind/model/"+stem+suffix)){assertNotNull(in);Files.copy(in,temp.resolve(stem+suffix));}
            rows+=DiffbindOutputs.projection(input,expected);cases++;
        }assertEquals(4,cases);assertEquals(1200,rows);
    }
    @Test void missingPermittedRuntimeHasAnActionableSkipContract()throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=DiffbindToolsCli.run(new String[]{"--bind",temp.resolve("absent.json").toString(),"--out",temp.resolve("binding").toString()},new PrintStream(out),new PrintStream(err));
        if(!System.getProperty("os.name").equals("Linux")){assertEquals(3,code);assertTrue(err.toString().contains("SKIP3 DEPENDENT DIFFBIND ANALYSIS SKIPPED"));assertTrue(err.toString().contains("Required action")||err.toString().contains("REQUIRED ACTION"));}else assertEquals(2,code);assertFalse(Files.exists(temp.resolve("binding")));
    }
}
