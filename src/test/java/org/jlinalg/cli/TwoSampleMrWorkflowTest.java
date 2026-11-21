/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Actual source-bound captures, including semantic attacks after deliberate resealing. */
final class TwoSampleMrWorkflowTest {
    @TempDir Path temp;
    @BeforeEach void fixtures()throws IOException {
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/cli/twosamplemr-workflow-0.7.14.zip")) {
            assertNotNull(raw);try(var zip=new ZipInputStream(raw)) {
                int count=0;long total=0;java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    assertTrue(++count<500);Path target=temp.resolve(entry.getName()).normalize();assertTrue(target.startsWith(temp));
                    if(entry.isDirectory()){Files.createDirectories(target);continue;}Files.createDirectories(target.getParent());byte[] bytes=zip.readAllBytes();total+=bytes.length;assertTrue(total<64_000_000);Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
                }
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"harmonise-1","harmonise-2","harmonise-3","mr-default","mr-deterministic","multivariable","pipeline"})
    void genuineOriginalCapturesVerifyWithoutR(String name)throws IOException {TwoSampleMrWorkflowCli.verify(temp.resolve(name));}
    @Test void resealedNumericalEstimateIsRecomputed()throws IOException {
        Path root=temp.resolve("mr-deterministic");change(root,"mr.tsv","method","Inverse variance weighted","b","0.99");TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("deterministic statistic"));
    }
    @Test void providerMapCannotDisableNativeChecks()throws IOException {
        Path root=temp.resolve("mr-deterministic");change(root,"method-list.tsv","obj","mr_ivw","obj","mr_simple_mode");TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("method-to-provider"));
    }
    @Test void weightedMedianPointIsIndependentlyRecomputed()throws IOException {
        Path root=temp.resolve("mr-default");change(root,"mr.tsv","method","Weighted median","b","0.99");TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("median point"));
    }
    @Test void nonNativeNegativeStandardErrorIsRefused()throws IOException {
        Path root=temp.resolve("mr-default");change(root,"mr.tsv","method","Weighted mode","se","-1");TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("negative standard error"));
    }
    @Test void nonNativeInstrumentCountCannotExceedItsInput()throws IOException {
        Path root=temp.resolve("mr-default");change(root,"mr.tsv","method","Weighted mode","nsnp","999");TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("instrument count"));
    }
    @Test void missingNativeEstimateCannotBeResealedAsUnavailable()throws IOException {
        Path root=temp.resolve("mr-deterministic");Path worker=root.resolve("worker/mr.tsv");var lines=new ArrayList<>(Files.readAllLines(worker));var header=List.of(lines.get(0).split("\t",-1));int method=header.indexOf("method"),removed=-1;
        for(int i=1;i<lines.size();i++)if(lines.get(i).split("\t",-1)[method].equals("Inverse variance weighted")){removed=i;break;}
        assertTrue(removed>0);lines.remove(removed);writeBoth(root,"mr.tsv",lines);
        var report=read(root.resolve("worker/report.json"));var tables=object(report.get("tables"),"tables");var metadata=object(tables.get("mr.tsv"),"metadata");metadata.put("rows",lines.size()-1);var names=new ArrayList<>(list(metadata.get("row_names"),"row names"));names.remove(removed-1);metadata.put("row_names",names);tables.put("mr.tsv",metadata);report.put("tables",tables);json(root.resolve("worker/report.json"),report);
        change(root,"method-population.tsv","method","mr_ivw","status","SOURCE_NO_ESTIMATE");change(root,"method-population.tsv","method","mr_ivw","estimate_admitted","FALSE");TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("omitted or fabricated"));
    }
    @Test void harmonisationExclusionCannotBeFlipped()throws IOException {
        Path root=temp.resolve("harmonise-2");change(root,"harmonised.tsv","SNP","rs4","mr_keep","TRUE");var report=read(root.resolve("worker/report.json"));report.put("original_population",Map.of("rows",6,"kept",5,"excluded",1));json(root.resolve("worker/report.json"),report);TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("exclusion flags"));
    }
    @Test void originalNullMemberCannotBeTurnedIntoAnEstimate()throws IOException {
        Path root=temp.resolve("multivariable");var report=read(root.resolve("worker/report.json"));var members=object(report.get("mv_members"),"members");members.put("outcome_pval",Map.of("type","double","length",4,"dimensions",List.of()));report.put("mv_members",members);json(root.resolve("worker/report.json"),report);TwoSampleMrWorkflowCli.seal(root);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root)).getMessage().contains("availability"));
    }
    @Test void changedTraitLabelIsRefused()throws IOException {
        Path root=temp.resolve("mr-deterministic");change(root,"mr.tsv","method","Inverse variance weighted","exposure","Other exposure");TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void exactReferenceSourceIsRequired()throws IOException {
        Path root=temp.resolve("pipeline");Files.writeString(root.resolve("source/twosamplemr_workflow.R"),"# changed\n",StandardOpenOption.APPEND);TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void scientificRequestCannotDrift()throws IOException {
        Path root=temp.resolve("mr-default");var request=read(root.resolve("effective-request.json"));var parameters=object(request.get("parameters"),"parameters");parameters.put("nboot",26);request.put("parameters",parameters);json(root.resolve("effective-request.json"),request);TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void fullOriginalReturnCannotBeDiscarded()throws IOException {
        Path root=temp.resolve("mr-default");Files.delete(root.resolve("worker/objects/001-mr/original-return.rds"));TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void namespaceRewritingIsNotClaimedAsOriginal()throws IOException {
        Path root=temp.resolve("mr-default");var report=read(root.resolve("worker/report.json"));report.put("original_namespace_rewritten",true);json(root.resolve("worker/report.json"),report);TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void runtimeEditionIsExact()throws IOException {
        Path root=temp.resolve("mr-default");var runtime=read(root.resolve("worker/runtime-profile.json"));runtime.put("R","4.6.2");json(root.resolve("worker/runtime-profile.json"),runtime);TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void requiredWarningsCannotBeHidden()throws IOException {
        Path root=temp.resolve("mr-default");Files.writeString(root.resolve("WARNINGS.md"),"Everything is fine\n");TwoSampleMrWorkflowCli.seal(root);assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void unrecordedFileIsRefused()throws IOException {
        Path root=temp.resolve("mr-default");Files.writeString(root.resolve("silent-results.tsv"),"x\n0\n");assertThrows(IOException.class,()->TwoSampleMrWorkflowCli.verify(root));
    }
    @Test void historicalEditionIsSkippedWithoutSubstitution()throws IOException {
        Path root=temp.resolve("mr-default");var r=read(root.resolve("source-request.json"));r.put("profile","TwoSampleMR0.5.7");r.put("out",temp.resolve("unknown-edition").toString());Path request=temp.resolve("unknown.json");json(request,r);var err=new ByteArrayOutputStream();
        assertEquals(3,TwoSampleMrWorkflowCli.run(new String[]{"--request",request.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(err)));assertFalse(Files.exists(temp.resolve("unknown-edition")));assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3"));
    }
    @Test void replayCannotWriteWithinParent()throws IOException {
        Path root=temp.resolve("mr-default");String before=FollowupSupport.hash(root.resolve("capture-files.json"));Path request=temp.resolve("replay.json");json(request,Map.of("schema_version",1,"mode","REPLAY","analysis",root.toString(),"out",root.resolve("nested").toString()));
        assertEquals(2,TwoSampleMrWorkflowCli.run(new String[]{"--request",request.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));assertFalse(Files.exists(root.resolve("nested")));assertEquals(before,FollowupSupport.hash(root.resolve("capture-files.json")));TwoSampleMrWorkflowCli.verify(root);
    }
    @Test void normalRunCannotWriteWithinRetainedParent()throws IOException {
        Path root=temp.resolve("mr-default");var r=read(root.resolve("source-request.json"));r.put("out",root.resolve("nested").toString());Path request=temp.resolve("nested.json");json(request,r);String before=FollowupSupport.hash(root.resolve("capture-files.json"));
        assertEquals(2,TwoSampleMrWorkflowCli.run(new String[]{"--request",request.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));assertFalse(Files.exists(root.resolve("nested")));assertEquals(before,FollowupSupport.hash(root.resolve("capture-files.json")));
    }
    @Test void duplicateInstrumentIsNeverSilentlySelected()throws IOException {
        Path root=temp.resolve("mr-default");Path input=root.resolve("inputs/harmonised");var lines=Files.readAllLines(input);Files.writeString(input,lines.get(1)+"\n",StandardOpenOption.APPEND);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowFiles.prepare(root,read(root.resolve("effective-request.json")),false)).getMessage().contains("duplicate"));
    }
    @Test void keptMissingEffectIsNeverDropped()throws IOException {
        Path root=temp.resolve("mr-default");Path input=root.resolve("inputs/harmonised");var lines=new ArrayList<>(Files.readAllLines(input));var header=List.of(lines.get(0).split("\t",-1));String[] row=lines.get(1).split("\t",-1);row[header.indexOf("beta.exposure")]="NA";lines.set(1,String.join("\t",row));Files.write(input,lines);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowFiles.prepare(root,read(root.resolve("effective-request.json")),false)).getMessage().contains("no omission"));
    }
    @Test void capacityDoesNotChangeBootstrapCount()throws IOException {
        Path root=temp.resolve("mr-default");var r=read(root.resolve("effective-request.json"));r.put("maximum_work",1);
        assertTrue(assertThrows(IOException.class,()->TwoSampleMrWorkflowFiles.prepare(root,r,false)).getMessage().contains("maximum_work"));assertEquals(25L,((Number)object(r.get("parameters"),"parameters").get("nboot")).longValue());
    }
    private static void change(Path root,String file,String selector,String match,String column,String value)throws IOException {
        var lines=new ArrayList<>(Files.readAllLines(root.resolve("worker").resolve(file)));var header=List.of(lines.get(0).split("\t",-1));boolean found=false;
        for(int i=1;i<lines.size();i++){String[] row=lines.get(i).split("\t",-1);if(row[header.indexOf(selector)].equals(match)){row[header.indexOf(column)]=value;lines.set(i,String.join("\t",row));found=true;}}
        assertTrue(found);writeBoth(root,file,lines);
    }
    private static void writeBoth(Path root,String file,List<String> lines)throws IOException {Files.write(root.resolve("worker").resolve(file),lines);Files.write(root.resolve("results").resolve(file),lines);}
}
