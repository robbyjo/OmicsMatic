/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class MetaxcanWorkflowTest {
    @TempDir Path temporary;
    record Result(int code,String out,String err){ }
    Result call(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();var argv=new ArrayList<>(List.of("metaxcan-tools"));argv.addAll(List.of(args));int code=JLinAlgCli.run(argv.toArray(String[]::new),new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    @Test void helpProfilesExamplesAndMissingResultExposeScopeAndRequiredActions()throws Exception {
        var h=call("--help");assertEquals(0,h.code());for(String item:List.of("summary-joint","--verify","PredictDB","SKIP3","FAIL2","Required action:","Cell Ranger"))assertTrue(h.out().contains(item),item);var p=call("--profiles");assertEquals(0,p.code());assertTrue(p.out().contains("\"original_execution_qualified\":false"));assertTrue(p.out().contains("\"model_training_implemented\":false"));
        for(String mode:List.of("summary","summary-joint")){var e=call("--example",mode);assertEquals(0,e.code());var spec=MetaxcanRequest.parse(object(SimpleJson.parse(e.out())),false);assertTrue(spec.nativeEngine());assertEquals(mode,spec.mode());assertNull(spec.process());}
        assertEquals(2,call("--verify",temporary.resolve("absent").toString()).code());assertEquals(2,call("--request").code());assertEquals(2,call("--example","train").code());
    }
    Map<String,Object> request(String mode,String edition,boolean calibrated)throws Exception {
        var r=MetaxcanToolsCli.example(mode);r.put("edition",edition);var data=copy(object(r.get("data")));var models=new ArrayList<Object>();for(String name:List.of("TissueA","TissueB"))models.add(Map.of("name",name,"file",temporary.resolve(name+".db").toAbsolutePath().toString()));data.put("models",models);
        var gwas=copy(object(data.get("gwas")));gwas.put("file",temporary.resolve("gwas.tsv").toAbsolutePath().toString());gwas.put("compression","none");data.put("gwas",gwas);data.put("covariance",Map.of("file",temporary.resolve("covariance.tsv").toAbsolutePath().toString(),"compression","none"));if(mode.equals("summary-joint"))data.put("model_product",Map.of("file",temporary.resolve("product.tsv").toAbsolutePath().toString(),"compression","none"));r.put("data",data);
        var settings=copy(object(r.get("settings")));if(calibrated)settings.put("calibration",Map.of("mode","source-phi","gwas_samples",10000,"gwas_heritability",.3));r.put("settings",settings);r.put("capacity",MetaxcanInputTest.request().get("capacity"));return r;
    }
    static Map<String,Object> copy(Map<?,?> source)throws IOException{var result=new LinkedHashMap<String,Object>();for(var row:source.entrySet())result.put(NextflowFiles.text(row.getKey()),row.getValue());return result;}
    Path inputs(String name)throws Exception {
        Path root=temporary.resolve(name);Files.createDirectory(root);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("inputs/models"));Files.createDirectory(root.resolve("outputs"));
        for(String file:List.of("models/TissueA.db","models/TissueB.db","gwas.tsv","covariance.tsv","product.tsv"))try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/metaxcan/workflow/inputs/"+file)){assertNotNull(in);String target=file.equals("product.tsv")?"model-product.tsv":file;Files.copy(in,root.resolve("inputs/"+target));}
        return root;
    }
    Map<String,Map<String,String>> table(Path file,String id)throws Exception {var result=new LinkedHashMap<String,Map<String,String>>();try(var t=new MetaxcanTable(file,false,"tab",100,new MetaxcanTable.Budget(1048576,()->{}),true)){for(List<String> row;(row=t.next())!=null;){var map=new LinkedHashMap<String,String>();for(int i=0;i<row.size();i++)map.put(t.header().get(i),row.get(i));assertNull(result.put(map.get(id),map));}}return result;}
    void numeric(Map<?,?> original,Map<String,String> actual,String source,String target){double expected=Double.parseDouble((String)original.get(source));assertEquals(expected,Double.parseDouble(actual.get(target)),Math.max(1e-12,Math.abs(expected)*2e-10),source+":"+target);}
    @Test void completeNativeSummaryAndJointMatchAllTenOriginalCommandsAndReplayExactly()throws Exception {
        Map<?,?> fixture;try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/metaxcan/workflow/original-outputs.json")){assertNotNull(in);fixture=object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}
        int checked=0;
        for(String edition:List.of("tag-0.7.3","commit-0.7.4","tag-0.8.1")) {
            Path root=inputs(edition);var spec=MetaxcanRequest.parse(request("summary-joint",edition,false),false);var report=MetaxcanSummary.run(root,spec,false,()->{});assertEquals(4L,report.get("summary_results"));assertEquals(2L,report.get("joint_results"));assertEquals(NextflowJson.json(report),NextflowJson.json(MetaxcanSummary.run(root,spec,true,()->{})));
            var joint=table(root.resolve("outputs/joint.tsv"),"gene");Map<String,Map<String,String>> summary=new LinkedHashMap<>();try(var t=new MetaxcanTable(root.resolve("outputs/summary.tsv"),false,"tab",100,new MetaxcanTable.Budget(1048576,()->{}),true)){for(List<String> row;(row=t.next())!=null;){var m=new LinkedHashMap<String,String>();for(int i=0;i<row.size();i++)m.put(t.header().get(i),row.get(i));summary.put(m.get("model")+":"+m.get("gene"),m);}}
            for(Object raw:list(fixture.get("records"))) {var record=object(raw);if(!edition.equals(record.get("source"))||record.get("case").equals("calibrated"))continue;
                for(Object golden:list(record.get("rows"))) {var row=object(golden);String gene=(String)row.get("gene");boolean isJoint=record.get("case").equals("joint");var actual=isJoint?joint.get(gene):summary.get(record.get("case")+":"+gene);assertNotNull(actual);numeric(row,actual,"pvalue","pvalue");if(isJoint){for(String field:List.of("n","n_indep","p_i_best","p_i_worst","eigen_max","eigen_min","eigen_min_kept","z_min","z_max","z_mean","z_sd","tmi","status"))numeric(row,actual,field,field);}else for(String field:List.of("zscore","effect_size","var_g","n_snps_used","n_snps_in_cov"))numeric(row,actual,field,field);}
                checked++;
            }
        }
        Path root=inputs("phi");var spec=MetaxcanRequest.parse(request("summary","tag-0.8.1",true),false);var report=MetaxcanSummary.run(root,spec,false,()->{});assertEquals(2L,report.get("negative_phi_replaced_by_zero"));
        var rows=new ArrayList<Map<String,String>>();try(var t=new MetaxcanTable(root.resolve("outputs/summary.tsv"),false,"tab",100,new MetaxcanTable.Budget(1048576,()->{}),true)){for(List<String> row;(row=t.next())!=null;){if(!row.get(0).equals("TissueA"))continue;var m=new LinkedHashMap<String,String>();for(int i=0;i<row.size();i++)m.put(t.header().get(i),row.get(i));rows.add(m);}}
        for(Object raw:list(fixture.get("records"))){var record=object(raw);if(!record.get("case").equals("calibrated"))continue;for(Object g:list(record.get("rows"))){var golden=object(g);var actual=rows.stream().filter(x->x.get("gene").equals(golden.get("gene"))).findFirst().orElseThrow();for(String field:List.of("zscore","pvalue","uncalibrated_zscore","uncalibrated_pvalue"))numeric(golden,actual,field,field);}checked++;}assertEquals(10,checked);
    }
    @Test void resealedNumericalChangesAndMissingJointTissuesAreRejected()throws Exception {
        Path root=inputs("numeric-change");var spec=MetaxcanRequest.parse(request("summary-joint","tag-0.8.1",false),false);MetaxcanSummary.run(root,spec,false,()->{});Path result=root.resolve("outputs/summary.tsv");String text=Files.readString(result);Files.writeString(result,text.replaceFirst("0\\.[0-9]+","0.987654321"));assertThrows(IOException.class,()->MetaxcanSummary.run(root,spec,true,()->{}));
        Path other=inputs("missing-tissue");Path product=other.resolve("inputs/model-product.tsv");Files.writeString(product,Files.readString(product).lines().filter(line->!line.contains("TissueB")).reduce("",(a,b)->a+b+"\n"));assertThrows(IOException.class,()->MetaxcanSummary.run(other,spec,false,()->{}));
    }
    @Test void missingPairsAndCapacityFailuresNeverProduceNullSubstitutes()throws Exception {
        Path root=inputs("missing-pair");Path covariance=root.resolve("inputs/covariance.tsv");Files.writeString(covariance,Files.readString(covariance).lines().filter(line->!line.contains("rs1\trs2")).reduce("",(a,b)->a+b+"\n"));var spec=MetaxcanRequest.parse(request("summary","tag-0.8.1",false),false);assertTrue(assertThrows(IOException.class,()->MetaxcanSummary.run(root,spec,false,()->{})).getMessage().contains("Missing original matrix pair"));
        Path small=inputs("capacity");var r=request("summary","tag-0.8.1",false);var cap=copy(object(r.get("capacity")));cap.put("numeric_bytes",1024);r.put("capacity",cap);var limited=MetaxcanRequest.parse(r,false);assertThrows(IOException.class,()->MetaxcanSummary.run(small,limited,false,()->{}));
    }
    @Test void noCompletedResultCanBeReportedThroughBrokenOutput()throws Exception {try(var stream=new PrintStream(new OutputStream(){@Override public void write(int b)throws IOException{throw new IOException("controlled output failure");}})){assertEquals(2,MetaxcanToolsCli.run(new String[]{"--help"},stream,stream));}}
}
