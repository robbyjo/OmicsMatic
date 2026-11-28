/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed requests and literal prepared tables; never accepts executable R expressions. */
final class WgcnaToolsRequest {
    private WgcnaToolsRequest() { }
    private static final java.util.regex.Pattern NUMBER=java.util.regex.Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
    static final String PROFILE="WGCNA-1.72-1-R4.2.3-linux64";
    record Table(List<String> rows,List<String> columns,double[][] values,long missing,long infinite) { }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","backend","profile","operation","matrix","modules","eigengenes","out","weights","ties","na_last","p_value_method","calculate_qvalue","scale_by_max","power","network_type","tom_type","cor_type","max_p_outliers","deep_split","min_module_size","merge_cut_height","detect_cut_height","reassign_threshold","pam_respects_dendro","seed","threads","rscript","r_libraries","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_rows","maximum_columns","maximum_entries","maximum_estimated_bytes","maximum_work"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");
        var r=new LinkedHashMap<String,Object>(raw);String backend=choice(raw,"backend","",Set.of("NATIVE","ORIGINAL_R")),op=choice(raw,"operation","",Set.of("RANK_PVALUE","CONNECTIVITY","NETWORK","SIGNED_KME"));
        for(String key:List.of("matrix","out"))text(raw,key);
        Set<String> used=new HashSet<>(Set.of("schema_version","mode","backend","operation","matrix","out","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_rows","maximum_columns","maximum_entries","maximum_estimated_bytes","maximum_work"));
        if(op.equals("RANK_PVALUE")) {
            used.addAll(Set.of("weights","ties","na_last","p_value_method","calculate_qvalue"));
            r.put("ties",choice(raw,"ties","average",Set.of("average","min","max","first","last")));
            r.put("na_last",choice(raw,"na_last","keep",Set.of("keep","first","last")));
            r.put("p_value_method",choice(raw,"p_value_method","all",Set.of("rank","scale","all")));
            if(!(raw.get("calculate_qvalue")instanceof Boolean))throw new IOException("Explicit calculate_qvalue boolean required");
            if(backend.equals("NATIVE")&&Boolean.TRUE.equals(raw.get("calculate_qvalue")))throw new IOException("Native rankPvalue supports calculate_qvalue=false only; choose ORIGINAL_R for original qvalue. No BH substitution");
            if(raw.containsKey("weights")){var values=new ArrayList<Double>();for(Object x:list(raw.get("weights"),"weights")){if(!(x instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0)throw new IOException("Require finite nonnegative weights");values.add(n.doubleValue());}r.put("weights",values);}
        }else if(op.equals("CONNECTIVITY")){text(raw,"modules");used.addAll(Set.of("modules","scale_by_max"));r.put("scale_by_max",bool(raw,"scale_by_max",false));}
        else if(op.equals("SIGNED_KME")){if(backend.equals("NATIVE"))throw new IOException("SIGNED_KME requires original R");text(raw,"eigengenes");used.addAll(Set.of("eigengenes","cor_type","max_p_outliers"));cor(r,raw);}
        else {
            if(backend.equals("NATIVE"))throw new IOException("NETWORK fitting requires original R");
            used.addAll(Set.of("power","network_type","tom_type","cor_type","max_p_outliers","deep_split","min_module_size","merge_cut_height","detect_cut_height","reassign_threshold","pam_respects_dendro","seed","threads"));
            if(!raw.containsKey("power"))throw new IOException("Declare power explicitly; no study power inferred");
            r.put("power",real(raw,"power",6,0,100,true));r.put("network_type",choice(raw,"network_type","signed",Set.of("signed","unsigned","signed hybrid")));r.put("tom_type",choice(raw,"tom_type","signed",Set.of("signed","unsigned")));cor(r,raw);
            r.put("deep_split",integer(raw,"deep_split",2,0,4));r.put("min_module_size",integer(raw,"min_module_size",30,2,100000));r.put("merge_cut_height",real(raw,"merge_cut_height",.15,0,2,true));r.put("detect_cut_height",real(raw,"detect_cut_height",.995,0,2,true));r.put("seed",integer(raw,"seed",54321,0,Integer.MAX_VALUE));r.put("threads",integer(raw,"threads",1,1,64));
            r.put("reassign_threshold",real(raw,"reassign_threshold",1e-6,0,1,true));r.put("pam_respects_dendro",bool(raw,"pam_respects_dendro",true));
        }
        if(backend.equals("ORIGINAL_R")){used.addAll(Set.of("profile","rscript","r_libraries"));if(!PROFILE.equals(raw.get("profile")))throw new SvaWorkflowRuntime.Unavailable("Exact WGCNA profile unavailable: "+raw.get("profile")+"; use the separately qualified exact profile "+PROFILE+" or retain skip");text(raw,"rscript");r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));}
        for(String key:raw.keySet())if(!used.contains(key))throw new IOException("Unused option for selected operation/backend: "+key);
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,2000000000));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000,1,20000000000L));r.put("maximum_rows",integer(raw,"maximum_rows",1000000,2,2000000));r.put("maximum_columns",integer(raw,"maximum_columns",10000,1,100000));r.put("maximum_entries",integer(raw,"maximum_entries",20000000,1,100000000));r.put("maximum_estimated_bytes",integer(raw,"maximum_estimated_bytes",8000000000L,1,64000000000L));r.put("maximum_work",integer(raw,"maximum_work",1000000000000L,1,100000000000000L));return r;
    }
    private static void cor(Map<String,Object> r,Map<String,Object> raw)throws IOException{r.put("cor_type",choice(raw,"cor_type","pearson",Set.of("pearson","bicor")));r.put("max_p_outliers",real(raw,"max_p_outliers",1,0,1,true));if(r.get("cor_type").equals("pearson")&&raw.containsKey("max_p_outliers"))throw new IOException("max_p_outliers requires bicor");}
    static Table table(Path path,Map<String,Object> r)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>number(r,"maximum_input_bytes"))throw new IOException("Require bounded regular matrix input");
        try(var input=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String line=input.readLine();if(line==null||line.length()>16000000)throw new IOException("Missing/oversized header");String[] header=line.split("\t",-1);if(header.length<2||!header[0].equals("id")||header.length-1>number(r,"maximum_columns"))throw new IOException("Require id header and bounded score/feature columns");
            List<String> columns=List.of(Arrays.copyOfRange(header,1,header.length));unique(columns);List<String> rows=new ArrayList<>();List<double[]> values=new ArrayList<>();long missing=0,infinite=0;
            while((line=input.readLine())!=null){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("WGCNA intake interrupted");if(line.length()>16000000||rows.size()>=number(r,"maximum_rows")||(long)(rows.size()+1)*columns.size()>number(r,"maximum_entries"))throw new IOException("Matrix row/line/entry capacity exceeded; no truncation");String[] cells=line.split("\t",-1);if(cells.length!=header.length)throw new IOException("Matrix row width differs");identity(cells[0]);rows.add(cells[0]);double[] v=new double[columns.size()];for(int j=0;j<v.length;j++){v[j]=numeric(cells[j+1]);if(Double.isNaN(v[j]))missing++;else if(!Double.isFinite(v[j]))infinite++;}values.add(v);}
            if(rows.size()<2)throw new IOException("Require >=2 rows");unique(rows);return new Table(List.copyOf(rows),columns,values.toArray(double[][]::new),missing,infinite);
        }
    }
    static List<String> modules(Path path,Table matrix)throws IOException {
        List<String> result=new ArrayList<>();try(var input=Files.newBufferedReader(path,StandardCharsets.UTF_8)){if(!"id\tmodule".equals(input.readLine()))throw new IOException("Require id/module header");String line;while((line=input.readLine())!=null){int i=result.size();String[] cells=line.split("\t",-1);if(cells.length!=2||i>=matrix.rows.size()||!cells[0].equals(matrix.rows.get(i)))throw new IOException("Module identities/order differ from complete adjacency");identity(cells[1]);result.add(cells[1]);}}if(result.size()!=matrix.rows.size())throw new IOException("Missing module rows");return List.copyOf(result);
    }
    static void dimensions(Table t,Map<String,Object> r)throws IOException {
        String op=text(r,"operation");long n=t.rows.size(),p=t.columns.size(),estimate=16*n*p+64*n+64*p,work=n*p;
        if(op.equals("CONNECTIVITY")&&(n!=p||!t.rows.equals(t.columns)))throw new IOException("Adjacency must be square with identical ordered row/column identities");
        if(op.equals("RANK_PVALUE")&&r.containsKey("weights")&&list(r.get("weights"),"weights").size()!=p)throw new IOException("Weights do not match matrix columns");
        if(op.equals("NETWORK")){estimate=Math.addExact(estimate,Math.multiplyExact(96,Math.multiplyExact(p,p)));work=Math.addExact(Math.multiplyExact(n,Math.multiplyExact(p,p)),Math.multiplyExact(p,Math.multiplyExact(p,p)));if(t.infinite>0)throw new IOException("Network expression input requires finite values or explicit NA; Inf is not repaired");}
        if(estimate>number(r,"maximum_estimated_bytes")||work>number(r,"maximum_work"))throw new IOException("Declared estimated memory/work capacity exceeded; no data reduction or block/model substitution");
    }
    static long number(Map<String,Object> r,String key){return((Number)r.get(key)).longValue();}
    private static void unique(List<String> values)throws IOException{Set<String> names=new HashSet<>();for(String name:values){identity(name);if(!names.add(name))throw new IOException("Duplicate literal identity: "+name);}}
    static double numeric(String token)throws IOException {return switch(token){case "NA","NaN"->Double.NaN;case "Inf","+Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->{if(!NUMBER.matcher(token).matches())throw new IOException("Invalid numeric token: "+token);double v;try{v=Double.parseDouble(token);}catch(NumberFormatException e){throw new IOException("Invalid number",e);}if(!Double.isFinite(v))throw new IOException("Numeric overflow is not an Inf input token");yield v;}};}
}
