/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.ScdrsJson.*;

/** Explicit historical source options; no arbitrary shell/program arguments. */
final class ScdrsRequest {
    private ScdrsRequest() { }
    record Limits(long inputBytes,long outputBytes,long workingBytes,int files,int seconds,int cells,int genes,
                  long nonzeros,long matrixElements,int traits) { }
    record Request(Path recipe,Path profile,Path out,String version,String operation,Map<String,Path> inputs,
                   Map<?,?> expression,Map<?,?> scoring,Map<?,?> downstream,Map<?,?> geneSets,Limits limits) { }
    static String choice(Object value,String... options)throws IOException {
        String text=text(value);need(Set.of(options).contains(text),"Unsupported explicit scDRS choice: "+text);return text;
    }
    static Path path(Path base,Object value)throws IOException {
        String s=text(value);need(!s.isEmpty()&&s.chars().noneMatch(c->c<32||c==127),"Invalid scDRS path");
        return base.resolve(s).toAbsolutePath().normalize();
    }
    static List<String> names(Object value)throws IOException {
        var names=new ArrayList<String>();var seen=new HashSet<String>();
        for(Object x:list(value)){
            String s=text(x);need(!s.isEmpty()&&s.length()<=120&&s.chars().noneMatch(c->c<32||c==127||c=='/'||c=='\\'||c==','),"Invalid annotation name");
            need(seen.add(s),"Duplicate annotation");names.add(s);
        }
        need(names.size()<=100,"Annotation count capacity exceeded");return List.copyOf(names);
    }
    static void probability(Object value)throws IOException {if(value!=null){double p=number(value);need(p>0&&p<1,"Threshold must be in(0,1)");}}
    static Request read(Path source)throws IOException {
        return read(source,false);
    }
    static Request read(Path source,boolean saved)throws IOException {
        source=source.toAbsolutePath().normalize();Path base=source.getParent();
        Map<?,?> r=object(ScdrsJson.read(source,1048576));
        keys(r,"schema_version","version","runtime_profile","output_dir","operation","inputs","expression","scoring","downstream","gene_sets","limits");
        integer(r.get("schema_version"),1,1);
        String version=choice(r.get("version"),"1.0.0","1.0.1","1.0.2");
        String operation=choice(r.get("operation"),"munge-gs","score","downstream","score-and-downstream");
        boolean score=operation.equals("score")||operation.equals("score-and-downstream");
        boolean down=operation.equals("downstream")||operation.equals("score-and-downstream");
        boolean munge=operation.equals("munge-gs");
        Map<?,?> input=object(r.get("inputs"));keys(input,"h5ad","gene_sets","covariates","scores","gene_statistics");
        var inputs=new java.util.LinkedHashMap<String,Path>();
        for(String key:List.of("h5ad","gene_sets","covariates","scores","gene_statistics"))if(input.get(key)!=null)inputs.put(key,path(base,input.get(key)));
        need(inputs.containsKey("h5ad")==!munge&&inputs.containsKey("gene_sets")==score&&inputs.containsKey("scores")==operation.equals("downstream")&&inputs.containsKey("gene_statistics")==munge,"Input roles do not match operation");
        need(!inputs.containsKey("covariates")||score,"Covariates apply only to scoring");
        need(new HashSet<>(inputs.values()).size()==inputs.size(),"One file cannot silently supply multiple input roles");
        Map<?,?> expression=munge?null:object(r.get("expression"));need(!munge||r.get("expression")==null,"No expression options for gene-set munging");
        if(expression!=null){keys(expression,"species","raw_counts","filter_data");choice(expression.get("species"),"human","mouse","hsapiens","mmusculus");bool(expression.get("raw_counts"));bool(expression.get("filter_data"));}
        Map<?,?> scoring=score?object(r.get("scoring")):null;need(score||r.get("scoring")==null,"No scoring block for this operation");
        if(scoring!=null){
            keys(scoring,"gene_set_species","controls","match","weighting","adjust_proportions","return_control_raw","return_control_normalized");
            choice(scoring.get("gene_set_species"),"human","mouse","hsapiens","mmusculus");integer(scoring.get("controls"),2,10000);
            choice(scoring.get("match"),"mean","mean_var");choice(scoring.get("weighting"),"uniform","vs","inv_std","od");
            if(scoring.get("adjust_proportions")!=null)names(List.of(scoring.get("adjust_proportions")));
            bool(scoring.get("return_control_raw"));bool(scoring.get("return_control_normalized"));
            need(!down||bool(scoring.get("return_control_normalized")),"Downstream requires normalized controls; no omitted controls inferred");
        }
        Map<?,?> downstream=down?object(r.get("downstream")):null;need(down||r.get("downstream")==null,"No downstream block for this operation");
        if(downstream!=null){
            keys(downstream,"groups","correlations","gene_analysis","knn_neighbors","knn_pcs","group_bh","group_fdr");
            List<String> groups=names(downstream.get("groups")),correlations=names(downstream.get("correlations"));
            need(!groups.isEmpty()||!correlations.isEmpty()||bool(downstream.get("gene_analysis")),"Choose at least one downstream analysis");
            var outputNames=new HashSet<String>();for(String s:groups){String name=s.replace(' ','_').replace(',','_');ScdrsRuntime.member("trait.scdrs_group."+name);need(outputNames.add(name),"Group output names collide under original source formatting");}
            bool(downstream.get("gene_analysis"));integer(downstream.get("knn_neighbors"),2,1000);integer(downstream.get("knn_pcs"),1,1000);
            need(!bool(downstream.get("group_bh"))||!groups.isEmpty(),"Group BH needs group association tests");probability(downstream.get("group_fdr"));need(downstream.get("group_fdr")!=null,"Declare the group FDR threshold explicitly");
        }
        Map<?,?> geneSets=munge?object(r.get("gene_sets")):null;need(munge||r.get("gene_sets")==null,"No gene-set construction block for this operation");
        if(geneSets!=null){
            keys(geneSets,"input_kind","weighting","minimum_genes","maximum_genes","fdr","fwer");
            choice(geneSets.get("input_kind"),"pvalue","zscore");choice(geneSets.get("weighting"),"zscore","uniform");
            long min=integer(geneSets.get("minimum_genes"),1,200000),max=integer(geneSets.get("maximum_genes"),1,200000);need(min<=max,"Minimum gene count exceeds maximum");
            probability(geneSets.get("fdr"));probability(geneSets.get("fwer"));need(geneSets.get("fdr")==null||geneSets.get("fwer")==null,"FDR and FWER are mutually exclusive");
        }
        Map<?,?> l=object(r.get("limits"));keys(l,"input_bytes","output_bytes","working_bytes","files","wall_seconds","cells","genes","nonzeros","matrix_elements","traits");
        Limits limits=new Limits(integer(l.get("input_bytes"),1,100_000_000_000L),integer(l.get("output_bytes"),1,200_000_000_000L),
            integer(l.get("working_bytes"),1,500_000_000_000L),(int)integer(l.get("files"),20,100000),(int)integer(l.get("wall_seconds"),1,604800),
            (int)integer(l.get("cells"),2,1_000_000),(int)integer(l.get("genes"),10,200000),integer(l.get("nonzeros"),1,10_000_000_000L),
            integer(l.get("matrix_elements"),1,200_000_000_000L),(int)integer(l.get("traits"),1,10000));
        Path out=path(base,r.get("output_dir")),profile=path(base,r.get("runtime_profile"));
        if(!saved){
            need(!source.startsWith(out)&&!profile.startsWith(out),"Output contains original request or runtime profile");
            for(Path file:inputs.values())need(!file.startsWith(out)&&!out.startsWith(file),"Output overlaps an original input");
        }else{
            need(out.equals(base)&&profile.equals(base.resolve("runtime-profile.json")),"Saved bundle roots differ");
            for(Path file:inputs.values())need(file.startsWith(base.resolve("inputs")),"Saved input escapes bundle");
        }
        return new Request(source,profile,out,version,operation,Map.copyOf(inputs),expression,scoring,downstream,geneSets,limits);
    }
}
