/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

class GenomicsemWorkflowTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path temporary;
    private Map<String,Object> request(String op)throws Exception {
        var options=map("estimation","DWLS","seed",1L,"covariance_scale","observed","sampling_order","lower-column-including-diagonal","input_reviewed",true,"smoothing_policy","FAIL","warning_policy","RETAIN_AND_FLAG");
        if(GenomicsemContracts.user(op))options.put("std_lv",false);
        if(op.equals("USERMODEL"))options.putAll(map("CFIcalc",true,"fix_resid",false,"tolerance",null,"Q_Factor",false));
        if(GenomicsemContracts.association(op)){options.putAll(map("cores",1L,"parallel",false,"GC","standard","SNPSE",false,"tolerance",false,"effects_aligned",true));if(GenomicsemContracts.user(op))options.putAll(map("fix_measurement",true,"Q_SNP",true,"sub",List.of()));}
        var inputs=map();for(String role:GenomicsemContracts.roles(op))inputs.put(role,GenomicsemContracts.input(temporary,role).toString());return GenomicsemContracts.validate(map("schema_version",1L,"mode","RUN","profile",GenomicsemContracts.PROFILE,"operation",op,"runtime_home",temporary.resolve("runtime").toString(),"out",temporary.resolve("new-output").toString(),"inputs",inputs,"options",options));
    }
    private void matrix(String role,List<String> labels)throws Exception {
        var text=new StringBuilder("id\t"+String.join("\t",labels)+"\n");for(int i=0;i<labels.size();i++){text.append(labels.get(i));for(int j=0;j<labels.size();j++)text.append('\t').append(i==j?"1":"0.1");text.append('\n');}Files.writeString(GenomicsemContracts.input(temporary,role),text);
    }
    private Map<String,Object> prepared(String op)throws Exception {
        Files.createDirectories(temporary.resolve("inputs"));var q=request(op);var traits=List.of("T1","T2","T3");matrix("covariance",traits);matrix("sampling_covariance",GenomicsemData.pairs(traits));
        if(GenomicsemContracts.association(op)){matrix("intercepts",traits);boolean tw=GenomicsemContracts.twas(op);Files.writeString(GenomicsemContracts.input(temporary,"associations"),(tw?"Gene\tPanel\tHSQ":"SNP\tCHR\tBP\tMAF\tA1\tA2")+"\tbeta.T1\tse.T1\tbeta.T2\tse.T2\tbeta.T3\tse.T3\n"+(tw?"Gene1\tPanel1\t0.2":"rs1\t1\t1001\t0.2\tA\tG")+"\t0.01\t0.02\t0.02\t0.02\t0.03\t0.02\n");}
        if(GenomicsemContracts.user(op))Files.writeString(GenomicsemContracts.input(temporary,"model"),"F =~ T1 + T2 + T3\nF ~ "+(GenomicsemContracts.twas(op)?"Gene":"SNP")+"\n");return q;
    }
    @Test void sixClosedOperationContracts()throws Exception {for(String op:List.of("COMMONFACTOR","USERMODEL","COMMONFACTOR_GWAS","USER_GWAS","COMMONFACTOR_TSEM","USER_TSEM"))assertEquals(op,request(op).get("operation"));}
    @Test void doesNotInventHistoricalEdition()throws Exception {var q=request("COMMONFACTOR");q.put("profile","GenomicSEM0.0.3f");assertThrows(GenomicsemRuntime.Unavailable.class,()->GenomicsemContracts.validate(q));}
    @Test void refusesUnknownScientificOption()throws Exception {var q=request("USERMODEL");var o=object(q.get("options"));o.put("silent_repair",true);q.put("options",o);assertThrows(IOException.class,()->GenomicsemContracts.validate(q));}
    @Test void rejectsImplicitGenomicControl()throws Exception {var q=request("USER_GWAS");var o=object(q.get("options"));o.remove("GC");q.put("options",o);assertThrows(IOException.class,()->GenomicsemContracts.validate(q));}
    @Test void rejectsFalseAlignmentReview()throws Exception {var q=request("USER_GWAS");var o=object(q.get("options"));o.put("effects_aligned",false);q.put("options",o);assertThrows(IOException.class,()->GenomicsemContracts.validate(q));}
    @Test void canonicalRecordIsPortableAndPreservesOptions()throws Exception {var q=request("USER_GWAS");var r=GenomicsemContracts.canonical(q);assertEquals(".",r.get("out"));assertEquals(q.get("options"),r.get("options"));assertEquals("inputs/S.tsv",object(r.get("inputs")).get("covariance"));}
    @Test void acceptsInterleavedAlignedEffectColumns()throws Exception {var q=prepared("USER_GWAS");var a=GenomicsemData.inputs(temporary,q);assertEquals(1,a.units().size());assertEquals(List.of("T1:T1","T1:T2","T1:T3","T2:T2","T2:T3","T3:T3"),GenomicsemData.pairs(a.traits()));}
    @Test void refusesReorderedPositionalMetadata()throws Exception {var q=prepared("USER_GWAS");Path f=GenomicsemContracts.input(temporary,"associations");Files.writeString(f,Files.readString(f).replace("SNP\tCHR\tBP\tMAF\tA1\tA2","SNP\tA1\tA2\tMAF\tCHR\tBP"));assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void refusesDiscordantSEOrder()throws Exception {var q=prepared("COMMONFACTOR_GWAS");Path f=GenomicsemContracts.input(temporary,"associations");Files.writeString(f,Files.readString(f).replace("se.T1","se.TMP").replace("se.T2","se.T1").replace("se.TMP","se.T2"));assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void refusesReorderedSamplingVech()throws Exception {var q=prepared("COMMONFACTOR");matrix("sampling_covariance",List.of("T1:T1","T2:T2","T3:T3","T1:T2","T1:T3","T2:T3"));assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void neverAveragesAsymmetricInput()throws Exception {var q=prepared("COMMONFACTOR");Path f=GenomicsemContracts.input(temporary,"covariance");Files.writeString(f,Files.readString(f).replaceFirst("1\\t0.1","1\t0.2"));assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void refusesZeroOrMissingEffectSE()throws Exception {var q=prepared("COMMONFACTOR_GWAS");Path f=GenomicsemContracts.input(temporary,"associations");Files.writeString(f,Files.readString(f).replace("0.01\t0.02","0.01\t0"));assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void combinedNumericCapacityIncludesMatricesAndEffects()throws Exception {var q=prepared("COMMONFACTOR_GWAS");var limits=object(q.get("limits"));limits.put("maximum_values",50L);q.put("limits",limits);assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void correlationScaleDoesNotSilentlyRescaleCovariances()throws Exception {var q=prepared("COMMONFACTOR");Path f=GenomicsemContracts.input(temporary,"covariance");Files.writeString(f,Files.readString(f).replaceFirst("T1\\t1","T1\t0.65"));var options=object(q.get("options"));options.put("covariance_scale","correlation");q.put("options",options);assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void preservesDistinctGenePanelsAndRefusesDuplicateUnit()throws Exception {var q=prepared("USER_TSEM");Path f=GenomicsemContracts.input(temporary,"associations");String text=Files.readString(f);String row=text.substring(text.indexOf('\n')+1);Files.writeString(f,text+row.replace("Panel1","Panel2"));assertEquals(2,GenomicsemData.inputs(temporary,q).units().size());Files.writeString(f,text+row);assertThrows(IOException.class,()->GenomicsemData.inputs(temporary,q));}
    @Test void existingOutputUniqueBytesRemainUntouched()throws Exception {var q=request("COMMONFACTOR");Path existing=temporary.resolve("existing");Files.createDirectory(existing);Files.writeString(existing.resolve("unique"),"keep");q.put("out",existing.toString());Path file=temporary.resolve("request.json");ScenicWorkflowFiles.write(file,q);assertEquals(2,GenomicsemToolsCli.run(new String[]{"--request",file.toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(OutputStream.nullOutputStream())));assertEquals("keep",Files.readString(existing.resolve("unique")));assertEquals(1,Files.list(existing).count());}
    @Test void mainRouterExposesExplicitScope()throws Exception {var b=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"genomicsem-tools","--help"},new PrintStream(b),new PrintStream(b)));assertTrue(b.toString().contains("COMMONFACTOR_TSEM"));assertTrue(b.toString().contains("SKIP3"));}
    @Test void scientificResourcesHaveExactClosedPins()throws Exception {assertEquals(2865,object(org.jlinalg.gprofiler.GprofilerJson.read(GenomicsemRuntime.resource("BINARY-PACKAGE-FILES.json"))).size());assertEquals(1056,object(org.jlinalg.gprofiler.GprofilerJson.read(GenomicsemRuntime.resource("NAMESPACE-FILES.json"))).size());assertNotNull(GenomicsemRuntime.metadata());}
}
