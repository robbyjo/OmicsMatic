/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.jlinalg.epigenomics.DiffbindResults;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Complete table populations and full-model original-namespace projection checks. */
final class DiffbindOutputs {
    private DiffbindOutputs() { }
    static Map<String,Object> audit(Path engine,DiffbindRequest.Spec spec,Runnable check)throws IOException {
        var results=object(json(engine.resolve("RESULTS.json"),16777216));require("COMPLETE_ORIGINAL_DIFFBIND_WORKFLOW_PROCESS_OUTPUTS".equals(results.get("status")),"Original complete-workflow status absent");
        require(integer(results.get("samples"),1,10000)==spec.samples().size(),"Source sample population changed");
        long peaks=integer(results.get("peaks"),1,Integer.MAX_VALUE);var tables=object(results.get("tables"));
        var counts=new TreeMap<String,Object>();
        for(var entry:tables.entrySet()) {
            String name=member(text(entry.getKey()));require(!name.contains("/"),"Top-level original table required");var meta=object(entry.getValue());List<?> columns=list(meta.get("columns"));long rows=0;
            require(meta.get("source_null") instanceof Boolean,"Explicit original NULL/table distinction required");
            if(Boolean.TRUE.equals(meta.get("source_null"))){require(name.equals("report-selected.tsv")&&columns.isEmpty()&&integer(meta.get("rows"),0,0)==0&&Files.size(engine.resolve(name))==0,"Invalid source NULL report marker");counts.put(name,0L);continue;}
            try(var reader=Files.newBufferedReader(engine.resolve(name),StandardCharsets.UTF_8)){
                String header=reader.readLine();require(header!=null,"Empty original table");String[] names=header.split("\t",-1);require(names.length==columns.size(),"Original table width differs from ordinal schema");
                for(int i=0;i<names.length;i++){var c=object(columns.get(i));require(integer(c.get("ordinal"),1,names.length)==i+1&&names[i].equals(c.get("name")),"Original table schema/header mismatch");}
                for(String line=reader.readLine();line!=null;line=reader.readLine()) {check.run();String[] values=line.split("\t",-1);require(values.length==names.length,"Truncated original table row");for(int i=0;i<values.length;i++)if("source-number-token".equals(object(columns.get(i)).get("type")))sourceNumber(values[i]);rows++;}
            }
            require(rows==integer(meta.get("rows"),0,Integer.MAX_VALUE),"Incomplete original table population");counts.put(name,rows);
        }
        require(counts.keySet().containsAll(List.of("counted-peaks.tsv","report-all.tsv","report-selected.tsv","heatmap-correlations.tsv")),"Complete count/report/correlation tables required");require(((Long)counts.get("counted-peaks.tsv"))==peaks,"Counted peak population differs");
        require(((Long)counts.get("heatmap-correlations.tsv"))==spec.samples().size(),"Correlation population differs");
        for(String name:List.of("PCA.png","heatmap.png")){
            Path path=engine.resolve(name);require(Files.size(path)<=spec.outputBytes(),"Plot exceeds complete output capacity");var image=ImageIO.read(path.toFile());require(image!=null&&image.getWidth()==960&&image.getHeight()==720,"Incomplete source PNG plot");int first=image.getRGB(0,0);boolean drawn=false;for(int y=0;y<720&&!drawn;y++)for(int x=0;x<960;x++)if(image.getRGB(x,y)!=first){drawn=true;break;}require(drawn,"Blank original plot");
        }
        long projected=0;int cases=0;
        if("DESeq2".equals(spec.settings().get("engine")))try(var stream=Files.newDirectoryStream(engine,"projection-*-input.tsv")){
            for(Path path:stream){String name=path.getFileName().toString();projected+=projection(path,engine.resolve(name.replace("-input.tsv","-expected.tsv")));cases++;}
            require(cases>=2,"Missing full original namespace projection controls");
        }
        require(Files.size(engine.resolve("counted-object.rds"))>0&&Files.size(engine.resolve("model-object.rds"))>0,"Complete counted and fitted RDS objects required");
        Object warnings=json(engine.resolve("SOURCE-WARNINGS.json"),16777216);list(warnings);
        return map("status","PASS_COMPLETE_DIFFBIND_TABLES_PLOTS_AND_NATIVE_PROJECTION","peaks",peaks,"samples",spec.samples().size(),"tables",counts,"selected_source_report_null",object(tables.get("report-selected.tsv")).get("source_null"),"full_model_projection_cases",cases,"full_model_projection_rows",projected,"source_warnings",warnings,"model_binary_validation","complete fresh replay required; no user RDS is deserialized","whole_package_admitted",false);
    }
    static long projection(Path input,Path expected)throws IOException {
        var actual=DiffbindResults.fromDeseq2(rows(input));var want=rows(expected);require(actual.size()==want.size(),"Full namespace projection population differs");
        for(int i=0;i<want.size();i++){var a=actual.get(i);var b=want.get(i);require(a.id()==b.id()&&bits(a.pvalue())==bits(b.pvalue())&&bits(a.adjustedPvalue())==bits(b.adjustedPvalue())&&bits(a.log2FoldChange())==bits(b.log2FoldChange()),"Full namespace/native projection bits differ at row "+(i+1));}return actual.size();
    }
    private static long bits(double d){return Double.doubleToRawLongBits(d);}
    private static List<DiffbindResults.Row> rows(Path path)throws IOException {
        var rows=new ArrayList<DiffbindResults.Row>();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            require("id\tp_bits\tpadj_bits\tfold_bits".equals(reader.readLine()),"Invalid projection header");for(String line=reader.readLine();line!=null;line=reader.readLine()){
                String[] f=line.split("\t",-1);require(f.length==4,"Truncated projection row");try{rows.add(new DiffbindResults.Row(Long.parseLong(f[0]),number(f[1]),number(f[2]),number(f[3])));}catch(NumberFormatException e){throw new IOException("Malformed projection bits/ID",e);}
            }
        }return rows;
    }
    private static double number(String s)throws IOException {require(s.matches("[a-f0-9]{16}"),"Invalid IEEE-754 token");return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    private static double sourceNumber(String s)throws IOException {
        if(s.equals("NA")||s.equals("NaN"))return Double.NaN;if(s.equals("Inf"))return Double.POSITIVE_INFINITY;if(s.equals("-Inf"))return Double.NEGATIVE_INFINITY;
        try{double v=Double.parseDouble(s);require(Double.isFinite(v),"Unknown nonfinite source token");return v;}catch(NumberFormatException e){throw new IOException("Invalid source numeric token",e);}
    }
    static Map<String,Object> canonical(Path engine,DiffbindRequest.Spec spec,Runnable check)throws IOException {
        var result=new TreeMap<String,Object>();for(var e:tree(engine,spec.files(),spec.outputBytes(),spec.outputBytes(),check).entrySet()){
            String name=e.getKey();if(name.startsWith("inputs/")||name.startsWith("process/")||name.startsWith("cache/")||name.equals("session.txt")||name.equals("R-MAPS.txt"))continue;
            require(e.getValue().kind().equals("file"),"Nonregular canonical original output");result.put(name,map("bytes",e.getValue().pin().bytes(),"sha256",e.getValue().pin().sha256()));
        }return result;
    }
}
