/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StratifiedLdscCliTest {
    @TempDir Path temp;
    record Run(int code,String stdout,String stderr) { }
    static Map<String,String> fixtures()throws Exception {
        var entries=new HashMap<String,String>();try(var raw=StratifiedLdscCliTest.class.getResourceAsStream("/org/jlinalg/xwas/ldsc-stratified/reference-fixtures.zip")){assertNotNull(raw);try(var zip=new ZipInputStream(raw)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())entries.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}}return entries;
    }
    static Map<String,Object> request(Path dir,Map<String,String> fixtures,String config)throws Exception {
        Files.createDirectories(dir);String[] c=config.split("\t");String id=c[0];int k=Integer.parseInt(c[2]);var labels=new ArrayList<String>();for(int j=0;j<k;j++)labels.add("A"+j);var input=new StringBuilder("variant\tchi_square\tweight_ld_score\tn\t"+String.join("\t",labels)+"\n");int index=0;for(String line:fixtures.get(id+"/input.tsv").lines().toList())input.append("rs").append(index++).append('\t').append(line).append('\n');Files.writeString(dir.resolve("input.tsv"),input.toString(),StandardCharsets.UTF_8);
        var overlap=new StringBuilder("annotation\t"+String.join("\t",labels)+"\n");index=0;for(String line:fixtures.get(id+"/overlap.tsv").lines().toList())overlap.append(labels.get(index++)).append('\t').append(line).append('\n');Files.writeString(dir.resolve("overlap.tsv"),overlap.toString(),StandardCharsets.UTF_8);
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("analysis","partitioned-hsq");m.put("weighting","source-fixed-initial");m.put("input","input.tsv");m.put("out","result");m.put("annotations",labels);m.put("annotation_counts",Arrays.stream(c[6].split(",")).map(Double::valueOf).toList());m.put("n_blocks",Integer.parseInt(c[3]));m.put("intercept",c[4].equals("free")?"free":Double.valueOf(c[4]));m.put("overlap","overlap.tsv");m.put("reference_universe_variants",Double.valueOf(c[5]));m.put("data_provenance","Synthetic unchanged-original reference "+id);m.put("row_order_declaration","Exact generated reference order, not a study genome");m.put("annotation_provenance","Synthetic binary resource; matching counts/overlaps and ordered LD columns");return m;
    }
    Path save(Path dir,Map<String,Object> request)throws Exception{Path p=dir.resolve("request.json");Files.writeString(p,NextflowJson.json(request)+"\n",StandardCharsets.UTF_8);return p;}
    Run run(Path request)throws Exception{var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=StratifiedLdscCli.run(new String[]{"--request",request.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    static Map<?,?> json(Path p)throws Exception{return NextflowFiles.object(NextflowParameters.parse(Files.readString(p),64<<20,2000000));}
    static void flatten(String key,Object value,Map<String,Object> flat)throws Exception {
        if(value instanceof Map<?,?> m){for(var e:m.entrySet())flatten(key.isEmpty()?e.getKey().toString():key+"."+e.getKey(),e.getValue(),flat);}
        else if(value instanceof List<?> list){for(int i=0;i<list.size();i++)flatten(key+"."+i,list.get(i),flat);}
        else flat.put(key,value);
    }
    @Test void completeFileWorkflowMatchesAll48OriginalModelsAnd22596Fields()throws Exception {
        var fixtures=fixtures();int models=0,fields=0;
        for(String config:fixtures.get("cases.tsv").lines().skip(1).toList()) {
            String id=config.split("\t")[0];Path dir=temp.resolve(id);var request=request(dir,fixtures,config);Path req=save(dir,request);byte[] reqBefore=Files.readAllBytes(req),inputBefore=Files.readAllBytes(dir.resolve("input.tsv")),overlapBefore=Files.readAllBytes(dir.resolve("overlap.tsv"));Run run=run(req);assertEquals(0,run.code,run.stderr);assertTrue(run.stdout.contains("READ"));assertEquals("",run.stderr);Path output=dir.resolve("result");assertTrue(Files.exists(output.resolve("COMPLETED.json")));assertEquals(Boolean.TRUE,json(output.resolve("analysis.json")).get("result_admitted"));
            assertArrayEquals(reqBefore,Files.readAllBytes(output.resolve("source-request.json")));assertArrayEquals(inputBefore,Files.readAllBytes(output.resolve("source-input.tsv")));assertArrayEquals(overlapBefore,Files.readAllBytes(output.resolve("source-overlap.tsv")));assertArrayEquals(reqBefore,Files.readAllBytes(req));
            var model=json(output.resolve("model.json"));var flat=new HashMap<String,Object>();flatten("fit",model.get("fit"),flat);flatten("overlap",model.get("overlap"),flat);
            List<?> counts=(List<?>)request.get("annotation_counts");Map<?,?> fit=(Map<?,?>)model.get("fit");var covarianceFlat=new HashMap<String,Object>();flatten("cov",fit.get("proportionCovariance"),covarianceFlat);double absCov=covarianceFlat.values().stream().mapToDouble(v->{try{return Math.abs(LdscToolsCli.number(v));}catch(IOException e){throw new UncheckedIOException(e);}}).sum();
            for(String line:fixtures.get(id+"/expected.tsv").lines().skip(1).toList()) {
                String[] entry=line.split("\t");String key=entry[0],label=id+" "+key;assertTrue(flat.containsKey(key),label);Object value=flat.get(key);fields++;
                if(entry[1].equals("NA")){assertTrue(value==null||value.equals("NA"),label);continue;}assertNotNull(value,label);double expected=Double.parseDouble(entry[1]),observed=LdscToolsCli.number(value);double floor=key.contains("covariance")||key.contains("Covariance")?1e-20:key.startsWith("fit.estimates.")||key.startsWith("fit.deleteValues.")||key.startsWith("overlap.coefficients.")||key.startsWith("overlap.coefficientStandardErrors.")?1e-16:2e-11;double tolerance=floor+5e-8*Math.abs(expected);
                if(key.startsWith("overlap.h2ProportionStandardErrors.")||key.startsWith("overlap.enrichmentStandardErrors.")){int j=Integer.parseInt(key.substring(key.lastIndexOf('.')+1));double factor=key.contains("enrichment")?((Number)request.get("reference_universe_variants")).doubleValue()/((Number)counts.get(j)).doubleValue():1;tolerance=Math.max(tolerance,Math.sqrt(1e-12*absCov)*factor);}assertEquals(expected,observed,tolerance,label);
            }
            verifyManifest(output);assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("REQUIRED ACTIONS"));models++;
        }
        assertEquals(48,models);assertEquals(22596,fields);System.out.println("Packaged-file contract: "+models+" original models, "+fields+" reference fields");
    }
    static void verifyManifest(Path output)throws Exception {
        var manifest=json(output.resolve("manifest.json"));for(var e:manifest.entrySet()){var expected=NextflowFiles.object(e.getValue());var actual=NextflowFiles.pin(output.resolve(e.getKey().toString()),256L<<20,()->{});assertEquals(actual.sha256(),expected.get("sha256"));assertEquals(actual.bytes(),(long)LdscToolsCli.number(expected.get("bytes")));}var completed=json(output.resolve("COMPLETED.json"));var pin=NextflowFiles.object(completed.get("manifest"));assertEquals(NextflowFiles.pin(output.resolve("manifest.json"),1048576,()->{}).sha256(),pin.get("sha256"));
    }
    Map<String,Object> first(Path dir)throws Exception{var f=fixtures();return request(dir,f,f.get("cases.tsv").lines().skip(1).findFirst().orElseThrow());}
    void rejected(Path dir,Run result,int code)throws Exception{assertEquals(code,result.code,result.stderr);assertTrue(result.stderr.contains("REQUIRED ACTIONS"));assertFalse(Files.exists(dir.resolve("result/COMPLETED.json")));if(Files.exists(dir.resolve("result/analysis.json")))assertEquals(Boolean.FALSE,json(dir.resolve("result/analysis.json")).get("result_admitted"));}
    @Test void noOverlapAndBomAndLiteralVariantAreRecorded()throws Exception {
        Path dir=temp.resolve("bom");var m=first(dir);m.put("overlap",null);m.put("reference_universe_variants",null);Path in=dir.resolve("input.tsv");Files.writeString(in,"\ufeff"+Files.readString(in).replaceFirst("rs0\\t","literal,variant\t"));Run result=run(save(dir,m));assertEquals(0,result.code,result.stderr);Map<?,?> report=json(dir.resolve("result/analysis.json"));assertEquals(Boolean.TRUE,report.get("input_utf8_bom_present"));assertEquals(Boolean.FALSE,report.get("overlap_summary_requested"));assertNull(json(dir.resolve("result/model.json")).get("overlap"));assertTrue(Files.readString(dir.resolve("result/row-audit.tsv")).contains("literal,variant"));assertFalse(Files.exists(dir.resolve("result/overlap-summary.tsv")));verifyManifest(dir.resolve("result"));
    }
    @Test void duplicatesMalformedRowsAndRankDeficiencyRefuseWithoutRepair()throws Exception {
        for(String kind:List.of("duplicate","blank","nonfinite","rank")) {
            Path dir=temp.resolve(kind);var m=first(dir);Path in=dir.resolve("input.tsv");String text=Files.readString(in);
            if(kind.equals("duplicate"))text=text.replaceFirst("rs1\\t","rs0\t");if(kind.equals("blank"))text+="\n";if(kind.equals("nonfinite"))text=text.replaceFirst("rs0\\t[^\\t]+","rs0\tNaN");
            if(kind.equals("rank")){var rows=new StringBuilder();for(String line:text.lines().toList()){String[] f=line.split("\t");if(!f[0].equals("variant"))f[5]=f[4];rows.append(String.join("\t",f)).append('\n');}text=rows.toString();}
            Files.writeString(in,text);byte[] before=Files.readAllBytes(in);rejected(dir,run(save(dir,m)),2);assertArrayEquals(before,Files.readAllBytes(dir.resolve("result/source-input.tsv")));assertTrue(Files.exists(dir.resolve("result/FAILURE.txt")));
        }
    }
    @Test void invalidOverlapRetainsUnadmittedComponentWithFailure()throws Exception {
        Path dir=temp.resolve("overlap");var m=first(dir);Path overlap=dir.resolve("overlap.tsv");String[] rows=Files.readString(overlap).split("\n");String[] cells=rows[1].split("\t");cells[1]="0";rows[1]=String.join("\t",cells);Files.writeString(overlap,String.join("\n",rows)+"\n");rejected(dir,run(save(dir,m)),2);assertTrue(Files.exists(dir.resolve("result/component-model.json")));assertEquals(Boolean.FALSE,json(dir.resolve("result/component-model.json")).get("complete_analysis_admitted"));assertFalse(Files.exists(dir.resolve("result/model.json")));
    }
    @Test void missingAndUnsupportedMethodsSkipDependentAnalysis()throws Exception {
        for(String kind:List.of("missing","analysis","weighting")){Path dir=temp.resolve(kind);var m=first(dir);if(kind.equals("missing"))m.put("input","unavailable.tsv");else m.put(kind,kind.equals("analysis")?"liability-hsq":"irwls");rejected(dir,run(save(dir,m)),3);assertFalse(Files.exists(dir.resolve("result")));}
    }
    @Test void existingOutputAndUnknownKeysAndInconsistentDeclarationsRefuse()throws Exception {
        Path existing=temp.resolve("existing");var m=first(existing);Files.createDirectory(existing.resolve("result"));Path sentinel=existing.resolve("result/keep.txt");Files.writeString(sentinel,"prior attempt");rejected(existing,run(save(existing,m)),2);assertEquals("prior attempt",Files.readString(sentinel));try(var files=Files.list(existing.resolve("result"))){assertEquals(1,files.count());}
        for(String kind:List.of("unknown","counts","universe","fractional-count","fractional-overlap")){Path dir=temp.resolve(kind);var q=first(dir);if(kind.equals("unknown"))q.put("two_step",30);if(kind.equals("counts"))q.put("annotation_counts",List.of(1));if(kind.equals("universe"))q.put("reference_universe_variants",null);if(kind.equals("fractional-count"))q.put("reference_universe_variants",1200.5);if(kind.equals("fractional-overlap")){Path ov=dir.resolve("overlap.tsv");Files.writeString(ov,Files.readString(ov).replaceFirst("(?m)^A0\\t[^\\t\\r\\n]+","A0\t1200.5"));}rejected(dir,run(save(dir,q)),2);}
    }
    @Test void failedConsoleNeverAdmitsTheCompletedAnalysis()throws Exception {
        Path dir=temp.resolve("console");var m=first(dir);Path req=save(dir,m);var errors=new ByteArrayOutputStream();var broken=new PrintStream(new OutputStream(){@Override public void write(int b)throws IOException{throw new IOException("controlled closed console");}});int code=StratifiedLdscCli.run(new String[]{"--request",req.toString()},broken,new PrintStream(errors));rejected(dir,new Run(code,"",errors.toString()),2);assertTrue(Files.exists(dir.resolve("result/unadmitted-analysis.json")));
    }
    @Test void helpAndUsageAreVisibleAndDoNotRunAnAnalysis()throws Exception {
        var out=new ByteArrayOutputStream();assertEquals(0,StratifiedLdscCli.run(new String[]{"--help"},new PrintStream(out),System.err));assertTrue(out.toString().contains("SKIP3"));assertTrue(out.toString().contains("source-fixed-initial"));var err=new ByteArrayOutputStream();assertEquals(2,StratifiedLdscCli.run(new String[]{"--unknown"},System.out,new PrintStream(err)));assertTrue(err.toString().contains("FAIL2"));
    }
}
