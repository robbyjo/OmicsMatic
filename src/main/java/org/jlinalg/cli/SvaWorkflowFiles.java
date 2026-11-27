/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Bounded literal TSV intake, copied originals and deterministic float64 transport. */
final class SvaWorkflowFiles {
    private SvaWorkflowFiles() { }
    record Prepared(List<String> features,List<String> samples) { }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        int maxF=((Number)r.get("maximum_features")).intValue(),maxS=((Number)r.get("maximum_samples")).intValue();
        var t=SusieWorkflowFiles.table(resolve(root,text(r,"matrix")),maxF,maxS+1);
        if(!t.header()[0].equals("feature_id")||t.header().length<4||t.rows().size()<10)throw new IOException("Matrix requires feature_id, >=3 samples, >=10 features");
        var features=ids(t.rows().stream().map(x->x[0]).toList());var samples=ids(Arrays.asList(t.header()).subList(1,t.header().length));int g=features.size(),n=samples.size();
        if((long)g*n>((Number)r.get("maximum_entries")).longValue())throw new IOException("maximum_entries exceeded");
        boolean seq=r.get("operation").equals("SVASEQ");transport(root,"matrix.tsv",numeric(t,seq),compare);
        var design=design(root,r,"design",samples,compare);int d=design.header().length-1;
        if(r.containsKey("null_design"))design(root,r,"null_design",samples,compare);
        if(r.containsKey("vfilter")&&((Number)r.get("vfilter")).intValue()>g)throw new IOException("vfilter exceeds supplied feature count");
        if(r.containsKey("n_sv")&&r.get("n_sv") instanceof Number x&&x.longValue()>n-d)throw new IOException("n_sv exceeds residual sample dimension; no factor-count truncation");
        if(r.containsKey("controls")) {
            var controls=SusieWorkflowFiles.table(resolve(root,text(r,"controls")),g,2);
            if(!Arrays.equals(controls.header(),new String[]{"feature_id","probability"})||!controls.rows().stream().map(x->x[0]).toList().equals(features))throw new IOException("Controls require exact feature_id,probability population/order");
            for(var row:controls.rows()){double x=SusieWorkflowFiles.number(row[1]);if(x<0||x>1)throw new IOException("Control probabilities must be in [0,1]");}
            transport(root,"controls.tsv",numeric(controls,false),compare);
        }
        try {
            long passes=r.get("operation").equals("NUM_SV")?((Number)r.getOrDefault("permutations",10)).longValue():r.get("n_sv").equals("auto")?21:1;
            passes=Math.addExact(passes,((Number)r.getOrDefault("iterations",1)).longValue()*4);
            long work=Math.multiplyExact(Math.multiplyExact((long)g,n),Math.multiplyExact(n,passes));
            if(work>((Number)r.get("maximum_work")).longValue())throw new IOException("maximum_work exceeded by dense SVD estimate");
        }catch(ArithmeticException e){throw new IOException("Dense work overflows capacity",e);}
        var profile=object(SimpleJson.parse(new String(SvaWorkflowRuntime.resource("sva-workflow/SOURCE-PROFILE.json"),StandardCharsets.UTF_8)),"profile");
        var source=object(profile.get("sva_source"),"sva source");Path archive=resolve(root,text(r,"sva_source"));
        if(Files.size(archive)!=((Number)source.get("bytes")).longValue()||!FollowupSupport.hash(archive).equals(source.get("sha256")))throw new IOException("Original sva3.46.0 source archive differs; preserve evidence and restore the exact source, no edition substitution");
        var p=new LinkedHashMap<String,Object>();for(String key:List.of("operation","method","units","n_sv","num_sv_method","iterations","permutations","vfilter","constant","seed"))if(r.containsKey(key))p.put(key,r.get(key));
        for(String key:List.of("null_design","controls"))p.put("has_"+key,r.containsKey(key));
        p.put("features",g);p.put("samples",n);p.put("design_columns",d);transport(root,"parameters.json",new String(RsemJson.bytes(p),StandardCharsets.UTF_8),compare);
        return new Prepared(features,samples);
    }
    private static SusieWorkflowFiles.Table design(Path root,Map<String,Object> r,String key,List<String> samples,boolean compare)throws IOException {
        var t=SusieWorkflowFiles.table(resolve(root,text(r,key)),samples.size(),((Number)r.get("maximum_design_columns")).intValue()+1);
        if(!t.header()[0].equals("sample_id")||t.header().length<2||t.header().length>samples.size()||!t.rows().stream().map(x->x[0]).toList().equals(samples))throw new IOException("Design requires exact sample_id order and positive residual degrees of freedom");
        ids(Arrays.asList(t.header()).subList(1,t.header().length));transport(root,key+".tsv",numeric(t,false),compare);return t;
    }
    private static List<String> ids(List<String> values)throws IOException {var seen=new HashSet<String>();for(String id:values){SusieWorkflowFiles.exactIdentity(id);if(!seen.add(id))throw new IOException("Repeated literal identity");}return List.copyOf(values);}
    private static String numeric(SusieWorkflowFiles.Table table,boolean nonnegative)throws IOException {
        var out=new StringBuilder(String.join("\t",table.header())).append('\n');
        for(var row:table.rows()){out.append(row[0]);for(int j=1;j<row.length;j++){double x=SusieWorkflowFiles.number(row[j]);if(nonnegative&&x<0)throw new IOException("Negative SVASEQ counts; no repair");out.append('\t').append(x);}out.append('\n');}return out.toString();
    }
    private static void transport(Path root,String name,String value,boolean compare)throws IOException {Path p=root.resolve("worker/"+name);byte[] raw=value.getBytes(StandardCharsets.UTF_8);if(compare){if(!Arrays.equals(Files.readAllBytes(p),raw))throw new IOException("Prepared transport changed: "+name);}else Files.write(p,raw,StandardOpenOption.CREATE_NEW);}
}
