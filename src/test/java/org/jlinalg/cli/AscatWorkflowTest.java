/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AscatWorkflowTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private Map<String,Object> example(String mode)throws Exception {
        var value=(Map<String,Object>)SimpleJson.parse(NextflowJson.json(AscatToolsCli.example(mode)));value.put("runtime",temp.resolve("runtime").toString());
        if(mode.equals("matrices")){var files=(Map<String,Object>)value.get("matrices");for(String key:files.keySet())files.put(key,temp.resolve(key+".txt").toString());}
        else {
            for(Object item:(List<?>)value.get("samples")){var sample=(Map<String,Object>)item;sample.put("tumor",temp.resolve("tumor.bam").toString());sample.put("normal",temp.resolve("normal.bam").toString());}
            for(Object item:(List<?>)value.get("references")){var ref=(Map<String,Object>)item;ref.put("alleles",temp.resolve("alleles"+ref.get("chromosome")+".txt").toString());if(mode.equals("hts"))ref.put("loci",temp.resolve("loci"+ref.get("chromosome")+".txt").toString());}
            if(mode.equals("targeted")){value.put("bed",temp.resolve("capture.bed").toString());for(Object item:(List<?>)value.get("normal_panel")){var sample=(Map<String,Object>)item;sample.put("hts",temp.resolve(sample.get("id")+".bam").toString());}}
        }return value;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> settings(Map<String,Object> value){return(Map<String,Object>)value.get("settings");}
    @Test void everyPreparationModeHasAnExplicitCompleteRequest()throws Exception {
        for(String mode:List.of("matrices","hts","targeted")){var spec=AscatRequest.parse(example(mode));assertEquals(mode,spec.mode());assertEquals(1,spec.samples().size());assertEquals(mode.equals("targeted")?20:0,spec.panel().size());}
    }
    @Test void unmatchedTumorCannotSilentlySubstituteGermlineOrIgnoreThePlatform()throws Exception {
        var value=example("matrices");@SuppressWarnings("unchecked")var files=(Map<String,Object>)value.get("matrices");files.put("normal_logR",null);files.put("normal_BAF",null);assertThrows(IOException.class,()->AscatRequest.parse(value));
        var platforms=NextflowFiles.strings(AscatRuntime.profile("ASCAT-3.2.0-R4.6.2").get("genotype_platforms"));assertFalse(platforms.isEmpty());settings(value).put("genotype_platform",platforms.get(0));assertDoesNotThrow(()->AscatRequest.parse(value));settings(value).put("genotype_platform","commented-or-invented-platform");assertThrows(IOException.class,()->AscatRequest.parse(value));
    }
    @Test void legacyCannotSilentlyUseCurrentPreparationOrCustomBounds()throws Exception {
        var current=example("hts");current.put("profile","ASCAT-2.5.2-R4.0.2");assertThrows(IOException.class,()->AscatRequest.parse(current));var matrix=example("matrices");matrix.put("profile","ASCAT-2.5.2-R4.0.2");assertDoesNotThrow(()->AscatRequest.parse(matrix));settings(matrix).put("min_ploidy",2);assertThrows(IOException.class,()->AscatRequest.parse(matrix));
    }
    @Test void sourceSpecificOrUnusedSettingsAreNotIgnored()throws Exception {
        var unknown=example("matrices");settings(unknown).put("fit_algorithm","replacement");assertThrows(IOException.class,()->AscatRequest.parse(unknown));var unused=example("matrices");settings(unused).put("nthreads",2);assertThrows(IOException.class,()->AscatRequest.parse(unused));var bad=example("targeted");bad.put("normal_panel",List.of());assertThrows(IOException.class,()->AscatRequest.parse(bad));
    }
    @Test void completeMatrixAuditRejectsMissingSamplesAndMismatchedCoordinates()throws Exception {
        var spec=AscatRequest.parse(example("matrices"));Path engine=Files.createDirectory(temp.resolve("engine"));Files.createDirectory(engine.resolve("inputs"));var matrices=new LinkedHashMap<String,Object>();
        for(String name:List.of("tumor_logR","tumor_BAF","normal_logR","normal_BAF")){String file="inputs/"+name+".txt";matrices.put(name,file);Files.writeString(engine.resolve(file),"\tchr\tpos\tT1\na\t1\t10\t0.5\nb\tX\t20\tNA\n");}
        NextflowFiles.writeJson(engine.resolve("effective.json"),Map.of("matrices",matrices));assertNotNull(AscatBundle.auditInputs(engine,spec,()->{}));Files.writeString(engine.resolve("inputs/normal_BAF.txt"),"\tchr\tpos\tT1\na\t1\t11\t0.5\nb\tX\t20\tNA\n");assertThrows(IOException.class,()->AscatBundle.auditInputs(engine,spec,()->{}));Files.writeString(engine.resolve("inputs/normal_BAF.txt"),"\tchr\tpos\tOTHER\na\t1\t10\t0.5\n");assertThrows(IOException.class,()->AscatBundle.auditInputs(engine,spec,()->{}));
    }
    @Test void packagedProfilesContainOriginalNamespaceAndPreparationSourcePopulations()throws Exception {
        for(String id:List.of("ASCAT-3.2.0-R4.6.2","ASCAT-2.5.2-R4.0.2")){var p=AscatRuntime.profile(id);var functions=NextflowFiles.object(p.get("functions"));assertTrue(functions.containsKey("ascat.runAscat"));assertTrue(functions.containsKey("create_distance_matrix"));assertEquals(id.contains("3.2.0")?48:33,NextflowFiles.object(p.get("source_files")).size());assertEquals(id.contains("3.2.0")?52:34,functions.size());assertEquals(id,p.get("profile_id"));}assertTrue(AscatRuntime.resource("worker.R").length>10000);
    }
    @Test void windowsExecutionSkipsBeforeParsingLinuxPaths()throws Exception {
        if(System.getProperty("os.name").equals("Linux"))return;var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=AscatToolsCli.run(new String[]{"--request",temp.resolve("absent.json").toString(),"--out",temp.resolve("capture").toString()},new PrintStream(out),new PrintStream(err));assertEquals(3,code);assertTrue(err.toString().contains("SKIP3 DEPENDENT ASCAT ANALYSIS SKIPPED"));assertTrue(err.toString().contains("REQUIRED ACTION"));assertFalse(Files.exists(temp.resolve("capture")));
    }
}
