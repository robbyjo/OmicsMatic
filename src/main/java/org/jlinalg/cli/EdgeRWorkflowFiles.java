/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Literal input audit and explicit float64/ASCII transport for the original package. */
final class EdgeRWorkflowFiles {
    private EdgeRWorkflowFiles() { }
    record Prepared(List<String> features,List<String> samples,List<String> coefficients,List<String> resultIds) { }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        int maxF=((Number)r.get("maximum_features")).intValue(),maxS=((Number)r.get("maximum_samples")).intValue();
        var counts=SusieWorkflowFiles.table(resolve(root,text(r,"counts")),maxF,maxS+1);
        if(counts.header().length<3||!counts.header()[0].equals("feature_id")||counts.rows().isEmpty())throw new IOException("Counts require feature_id, at least two sample columns and nonempty features");
        var samples=identities(Arrays.asList(counts.header()).subList(1,counts.header().length));
        var features=identities(counts.rows().stream().map(row->row[0]).toList());int n=samples.size(),g=features.size();
        if((long)n*g>((Number)r.get("maximum_entries")).longValue())throw new IOException("maximum_entries exceeded");
        var out=new StringBuilder("feature_index");for(int j=0;j<n;j++)out.append("\ts").append(j+1);out.append('\n');
        double[] totals=new double[n];boolean raw=r.get("count_units").equals("raw-counts");
        for(int i=0;i<g;i++) {
            out.append('f').append(i+1);String[] row=counts.rows().get(i);
            for(int j=0;j<n;j++) {
                double x=SusieWorkflowFiles.number(row[j+1]);
                if(x<0||raw&&(x!=Math.rint(x)||x>9007199254740991d))throw new IOException("Declared raw counts require nonnegative exact integers <=2^53-1; estimated-counts permits explicit fractional nonnegative inputs");
                if(raw)try{new BigDecimal(row[j+1]).toBigIntegerExact();}catch(ArithmeticException fractional){throw new IOException("Raw-count decimal token is fractional before float64 transport; no rounding is permitted",fractional);}
                totals[j]+=x;if(!Double.isFinite(totals[j]))throw new IOException("Count library total overflow");out.append('\t').append(x);
            }out.append('\n');
        }
        transport(root,"counts.tsv",out.toString(),compare);
        var groups=SusieWorkflowFiles.table(resolve(root,text(r,"groups")),n,2);
        if(!Arrays.equals(groups.header(),new String[]{"sample_id","group"})||groups.rows().size()!=n)throw new IOException("Groups require exact sample_id,group and count sample population/order");
        var levels=strings(r.get("group_levels"),"group_levels",1,10000);var used=new HashSet<String>();out=new StringBuilder("sample_index\tgroup\n");
        for(int i=0;i<n;i++) {
            var row=groups.rows().get(i);if(!row[0].equals(samples.get(i)))throw new IOException("Group sample identity/order differs");
            int level=levels.indexOf(row[1]);if(level<0)throw new IOException("Undeclared literal group");used.add(row[1]);out.append('s').append(i+1).append("\tg").append(level+1).append('\n');
        }
        if(used.size()!=levels.size())throw new IOException("Unused declared group levels are refused, never dropped silently");transport(root,"groups.tsv",out.toString(),compare);
        List<String> coefficients=List.of();int d=0;
        if(r.containsKey("design")) {
            var design=SusieWorkflowFiles.table(resolve(root,text(r,"design")),n,((Number)r.get("maximum_design_columns")).intValue()+1);
            if(!design.header()[0].equals("sample_id")||design.rows().size()!=n||design.header().length<3)throw new IOException("Design requires sample_id, at least two numeric coefficient columns and exact count samples/order");
            coefficients=identities(Arrays.asList(design.header()).subList(1,design.header().length));d=coefficients.size();
            if(d>=n)throw new IOException("Design requires positive residual sample degrees of freedom");
            out=new StringBuilder("sample_index");for(int j=0;j<d;j++)out.append("\td").append(j+1);out.append('\n');
            for(int i=0;i<n;i++) {
                var row=design.rows().get(i);if(!row[0].equals(samples.get(i)))throw new IOException("Design sample identity/order differs");out.append('s').append(i+1);
                for(int j=0;j<d;j++)out.append('\t').append(SusieWorkflowFiles.number(row[j+1]));out.append('\n');
            }transport(root,"design.tsv",out.toString(),compare);
        }
        if(r.containsKey("libraries"))sidecar(root,r,"libraries","library_size",samples,true,compare);
        else for(double x:totals)if(!(x>0))throw new IOException("Every computed library must be positive; provide an explicitly justified positive libraries sidecar or correct the data");
        if(r.containsKey("norm_factors"))sidecar(root,r,"norm_factors","norm_factor",samples,true,compare);
        var norm=object(r.get("normalization"),"normalization");
        if(norm.containsKey("reference_column")&&((Number)norm.get("reference_column")).intValue()>n)throw new IOException("Normalization reference_column exceeds sample population");
        var resultIds=new ArrayList<String>();var results=new ArrayList<Map<String,Object>>();
        for(Object value:list(r.get("results"),"results")) {
            var item=object(value,"result");resultIds.add(text(item,"id"));var transformed=new LinkedHashMap<String,Object>(item);transformed.put("id","r"+resultIds.size());
            if(item.containsKey("pair"))transformed.put("pair",list(item.get("pair"),"pair").stream().map(v->"g"+(levels.indexOf(v)+1)).toList());
            if(item.containsKey("coefficients"))for(Object c:list(item.get("coefficients"),"coefficients"))if(((Number)c).intValue()>d)throw new IOException("Test coefficient index exceeds declared design");
            if(item.containsKey("contrast")&&list(item.get("contrast"),"contrast").size()!=d)throw new IOException("Contrast length must exactly match design columns; no recycling");results.add(transformed);
        }
        try {
            long work=Math.multiplyExact(Math.multiplyExact((long)g,n),Math.max(1L,(long)d*d));
            long grid=object(r.get("dispersion"),"dispersion").get("grid_length") instanceof Number number?number.longValue():1;
            work=Math.multiplyExact(work,Math.multiplyExact(grid,results.size()+1L));
            if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded by declared dense-model estimate");
        }catch(ArithmeticException overflow){throw new IOException("Dense-model work estimate overflows integer capacity",overflow);}
        var parameters=new LinkedHashMap<String,Object>();
        for(String key:List.of("profile","model","count_units","normalization","filter","dispersion","fit","adjust_method","seed"))parameters.put(key,r.get(key));
        parameters.put("results",results);parameters.put("features",g);parameters.put("samples",n);parameters.put("design_columns",d);
        parameters.put("group_levels",java.util.stream.IntStream.rangeClosed(1,levels.size()).mapToObj(i->"g"+i).toList());
        parameters.put("has_libraries",r.containsKey("libraries"));parameters.put("has_norm_factors",r.containsKey("norm_factors"));
        transport(root,"parameters.json",new String(RsemJson.bytes(parameters),StandardCharsets.UTF_8),compare);
        return new Prepared(features,samples,coefficients,List.copyOf(resultIds));
    }
    private static void sidecar(Path root,Map<String,Object> r,String key,String field,List<String> samples,boolean positive,boolean compare)throws IOException {
        var table=SusieWorkflowFiles.table(resolve(root,text(r,key)),samples.size(),2);
        if(!Arrays.equals(table.header(),new String[]{"sample_id",field})||table.rows().size()!=samples.size())throw new IOException("Require exact sample_id,"+field+" sidecar population");
        var out=new StringBuilder("sample_index\t").append(field).append('\n');
        for(int i=0;i<samples.size();i++) {
            var row=table.rows().get(i);if(!row[0].equals(samples.get(i)))throw new IOException("Sidecar sample identity/order differs: "+key);
            double x=SusieWorkflowFiles.number(row[1]);if(positive&&!(x>0))throw new IOException("Sidecar values must be positive: "+key);out.append('s').append(i+1).append('\t').append(x).append('\n');
        }transport(root,key+".tsv",out.toString(),compare);
    }
    private static List<String> identities(List<String> ids)throws IOException {
        if(ids.isEmpty())throw new IOException("Empty identity population");var seen=new HashSet<String>();
        for(String id:ids){SusieWorkflowFiles.exactIdentity(id);if(!seen.add(id))throw new IOException("Repeated literal identity");}return List.copyOf(ids);
    }
    private static void transport(Path root,String name,String content,boolean compare)throws IOException {
        var path=root.resolve("worker/"+name);byte[] raw=content.getBytes(StandardCharsets.UTF_8);
        if(compare){if(!Arrays.equals(raw,Files.readAllBytes(path)))throw new IOException("Prepared transport differs: "+name);}
        else Files.write(path,raw,StandardOpenOption.CREATE_NEW);
    }
}
