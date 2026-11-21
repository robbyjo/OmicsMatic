/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Canonical package tables, with no implicit format_data, identifier or population repair. */
final class TwoSampleMrHistoricalFiles {
    private TwoSampleMrHistoricalFiles() { }
    record Table(List<String> columns,List<List<String>> rows) {
        String cell(List<String> row,String column)throws IOException {int at=columns.indexOf(column);if(at<0)throw new IOException("Missing column: "+column);return row.get(at);}
    }
    static boolean numeric(String column){return column.matches("(?:beta|se|eaf|pval|samplesize|ncase|ncontrol|pos)[.](?:exposure|outcome)");}
    static boolean logical(String column){return Set.of("mr_keep","mr_keep.exposure","mr_keep.outcome","remove","palindromic","ambiguous","proxy.outcome").contains(column);}
    static double number(String value)throws IOException {
        if(Set.of("NA","NaN","Inf","-Inf").contains(value))return switch(value){case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.NaN;};
        if(!value.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new IOException("Require literal decimal or explicit NA/NaN/Inf token");
        try{double d=Double.parseDouble(value);if(!Double.isFinite(d))throw new IOException("Decimal input overflow; use explicit nonfinite token for a retained failed attempt");return d;}catch(NumberFormatException error){throw new IOException("Invalid numeric token",error);}
    }
    static Table table(Path path,Map<String,Object> r)throws IOException {
        var lines=Files.readAllLines(path,StandardCharsets.UTF_8);if(lines.isEmpty())throw new IOException("Require a canonical TSV header");
        var columns=List.of(lines.get(0).split("\t",-1));if(columns.size()>((Number)r.get("maximum_columns")).longValue()||new HashSet<>(columns).size()!=columns.size())throw new IOException("Duplicate columns or maximum_columns exceeded");for(String name:columns)identity(name);
        var rows=new ArrayList<List<String>>();for(int i=1;i<lines.size();i++) {
            if(rows.size()>=((Number)r.get("maximum_rows")).longValue()||(long)(rows.size()+1)*columns.size()>((Number)r.get("maximum_entries")).longValue())throw new IOException("Input capacity exceeded; no truncation");
            var row=List.of(lines.get(i).split("\t",-1));if(row.size()!=columns.size())throw new IOException("TSV width differs at row "+i);
            for(int j=0;j<columns.size();j++) {
                String value=row.get(j),column=columns.get(j);
                if(value.length()>4094||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("TSV cell contains controls or exceeds the text capacity");
                if(Set.of("SNP","id.exposure","id.outcome","exposure","outcome").contains(column))identity(value);
                if(numeric(column)) {
                    double d=number(value);if(column.startsWith("se.")&&Double.isFinite(d)&&d<=0)throw new IOException("Standard errors must be positive");
                    if((column.startsWith("eaf.")||column.startsWith("pval."))&&Double.isFinite(d)&&(d<0||d>1))throw new IOException("Frequency/probability outside [0,1]; no clamping");
                }else if(logical(column)&&!Set.of("TRUE","FALSE","NA").contains(value))throw new IOException("Logical cells must be TRUE/FALSE/NA");
            }rows.add(row);
        }return new Table(columns,List.copyOf(rows));
    }
    static void prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        if(text(r,"operation").equals("FORMAT")) {
            Table input=rawTable(root.resolve("inputs/data"),r);long work=Math.multiplyExact(input.rows().size(),input.columns().size());
            if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("Formatting maximum_work exceeded; no source row pruning");
            byte[] bytes=RsemJson.bytes(Map.of("data",Map.of("columns",input.columns(),"rows",input.rows().size(),"sha256",FollowupSupport.hash(root.resolve("inputs/data"))),"operation","FORMAT","source_transformation_policy",r.get("format_policy")));
            Path target=root.resolve("prepared.json");if(compare){if(!Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Literal formatter intake differs");}else Files.write(target,bytes,StandardOpenOption.CREATE_NEW);return;
        }
        var summary=new LinkedHashMap<String,Object>();long total=0;var joins=new HashMap<String,Map<String,Long>>();
        for(String key:TwoSampleMrHistoricalContracts.INPUTS)if(r.containsKey(key)) {
            Table t=table(root.resolve(text(r,key)),r);Set<String> required=new HashSet<>(List.of("SNP"));
            if(key.equals("harmonised"))required.addAll(List.of("id.exposure","id.outcome","exposure","outcome","beta.exposure","beta.outcome","se.exposure","se.outcome","mr_keep"));
            else {required.addAll(List.of(key,"id."+key,"beta."+key,"se."+key,"effect_allele."+key,"other_allele."+key,"eaf."+key));if(r.get("operation").equals("MV_HARMONISE")&&key.equals("exposure"))required.add("pval.exposure");}
            if(!new HashSet<>(t.columns).containsAll(required))throw new IOException("Missing canonical columns for "+key+": "+required);
            var unique=new HashSet<List<String>>();var labels=new HashMap<List<String>,String>();var snps=new HashMap<String,Long>();long kept=0;
            for(var row:t.rows) {
                var id=new ArrayList<String>();id.add(t.cell(row,"SNP"));for(String side:List.of("exposure","outcome"))if(t.columns.contains("id."+side)) {String value=t.cell(row,"id."+side);if(value.equals("NA"))throw new IOException("Missing trait identity");id.add(value);}
                if(id.get(0).equals("NA")||!unique.add(id))throw new IOException("Missing SNP or duplicate (SNP, trait) row; resolve explicitly without selecting an arbitrary duplicate");
                snps.merge(id.get(0),1L,Long::sum);for(String side:List.of("exposure","outcome"))if(t.columns.contains("id."+side)&&t.columns.contains(side)){var trait=List.of(side,t.cell(row,"id."+side));String label=t.cell(row,side),old=labels.putIfAbsent(trait,label);if(old!=null&&!old.equals(label))throw new IOException("Conflicting labels for one trait identity; no arbitrary first-label repair");}
                if(key.equals("harmonised")) {
                    String keep=t.cell(row,"mr_keep");if(keep.equals("NA"))throw new IOException("mr_keep cannot be missing");
                    if(keep.equals("TRUE")){kept++;for(String name:List.of("beta.exposure","beta.outcome","se.exposure","se.outcome"))if(!Double.isFinite(number(t.cell(row,name))))throw new IOException("Kept MR rows require finite effects and SEs; no omission or imputation");}
                }
            }
            total+=t.rows.size();summary.put(key,Map.of("columns",t.columns,"rows",t.rows.size(),"literal_kept_MR_rows",kept,"sha256",FollowupSupport.hash(root.resolve(text(r,key)))));
            joins.put(key,snps);
        }
        if(joins.containsKey("exposure")) {
            long candidates=0;for(var entry:joins.get("exposure").entrySet())candidates=Math.addExact(candidates,Math.multiplyExact(entry.getValue(),joins.get("outcome").getOrDefault(entry.getKey(),0L)));
            if(candidates>((Number)r.get("maximum_rows")).longValue())throw new IOException("Candidate source join exceeds maximum_rows; choose explicit capacities, no population pruning");summary.put("maximum_candidate_join_rows",candidates);
        }
        if(TwoSampleMrHistoricalContracts.mr(text(r,"operation"))) {
            long boot=((Number)object(r.get("effective_parameters"),"effective parameters").get("nboot")).longValue();long methods=list(r.get("methods"),"methods").size();long work=Math.multiplyExact(Math.multiplyExact(total,boot),Math.multiplyExact(methods,1L+list(r.get("sensitivity"),"sensitivity").size()));
            if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded; choose explicit capacities, no method or bootstrap reduction");summary.put("conservative_work",work);
        }
        byte[] bytes=RsemJson.bytes(summary);Path path=root.resolve("prepared.json");if(compare){if(!Arrays.equals(Files.readAllBytes(path),bytes))throw new IOException("Literal intake summary differs");}else Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
    static Table rawTable(Path path,Map<String,Object> r)throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String header=reader.readLine();if(header==null)throw new IOException("Require raw formatter TSV header");var columns=List.of(header.split("\t",-1));
            if(columns.size()>((Number)r.get("maximum_columns")).longValue()||new HashSet<>(columns).size()!=columns.size())throw new IOException("Raw formatter column capacity/duplicates");for(String name:columns)identity(name);
            var rows=new ArrayList<List<String>>();String line;while((line=reader.readLine())!=null) {
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Formatter intake interrupted");
                if(rows.size()>=((Number)r.get("maximum_rows")).longValue()||(long)(rows.size()+1)*columns.size()>((Number)r.get("maximum_entries")).longValue())throw new IOException("Formatter input capacity exceeded");
                var row=List.of(line.split("\t",-1));if(row.size()!=columns.size())throw new IOException("Raw formatter TSV width differs");
                for(String value:row)if(value.length()>4094||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Raw formatter cell contains controls or exceeds capacity");rows.add(row);
            }return new Table(columns,List.copyOf(rows));
        }
    }
}
