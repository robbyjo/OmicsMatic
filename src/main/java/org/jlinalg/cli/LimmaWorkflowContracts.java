/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed data/options for original limma workflows; no formulas or R expressions. */
final class LimmaWorkflowContracts {
    private LimmaWorkflowContracts() { }
    static final String PROFILE="limma-3.68.5-edgeR-4.10.7-statmod-1.5.2-R4.6.1";
    static final Set<String> PROFILES=Set.of(PROFILE);
    static final List<String> INPUTS=List.of("matrix","design","weights","libraries","norm_factors","block","batch","batch2","covariates","trend_covariate","statistics","gene_sets");
    static boolean fitted(String op){return Set.of("LM","VOOM","EDGER_VOOM_LMFIT").contains(op);}
    static boolean counts(String op){return Set.of("VOOM","EDGER_VOOM_LMFIT").contains(op);}
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","out","matrix","design","weights","libraries","norm_factors","block","block_levels","batch","batch_levels","batch2","batch2_levels","covariates","trend_covariate","statistics","gene_sets","units","normalization","voom","correlation","moderation","contrasts","adjust_method","adjustment_scope","camera","normalize_method","rscript","r_libraries","seed","timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_features","maximum_samples","maximum_entries","maximum_design_columns","maximum_sets","maximum_memberships","maximum_work","preparation_note"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(text(raw,"mode")))throw new IOException("Require schema_version1 and mode RUN");
        choice(raw,"profile","",PROFILES);String op=choice(raw,"operation","",Set.of("LM","VOOM","EDGER_VOOM_LMFIT","BATCH","CAMERAPR","NORMALIZE"));
        var r=new LinkedHashMap<String,Object>(raw);for(String field:List.of("out","rscript"))text(raw,field);
        for(String field:INPUTS)if(raw.containsKey(field))text(raw,field);
        var allowedInputs=new HashSet<String>();
        if(op.equals("CAMERAPR")) {
            for(String key:List.of("statistics","gene_sets"))text(raw,key);allowedInputs.addAll(List.of("statistics","gene_sets"));
            r.put("units",choice(raw,"units","",Set.of("ranked-statistic")));
            var c=object(raw.getOrDefault("camera",Map.of()),"camera");keys(c,Set.of("use_ranks","inter_gene_cor","directional","sort"));var camera=new LinkedHashMap<String,Object>();
            camera.put("use_ranks",bool(c,"use_ranks",false));camera.put("directional",bool(c,"directional",true));camera.put("sort",bool(c,"sort",true));
            Object corr=c.getOrDefault("inter_gene_cor",.01);
            if(corr instanceof Number)camera.put("inter_gene_cor",real(Map.of("v",corr),"v",Double.NaN,-1,1,false));
            else {var vector=new ArrayList<Double>();for(Object x:list(corr,"inter_gene_cor"))vector.add(number(x,-1,1,false));if(vector.isEmpty())throw new IOException("Empty per-set correlation vector");camera.put("inter_gene_cor",vector);}
            if(corr instanceof Number v&&Math.abs(v.doubleValue())>=1)throw new IOException("inter_gene_cor must be strictly between -1 and1");
            if(camera.get("inter_gene_cor") instanceof List<?> values&&values.stream().anyMatch(v->Math.abs(((Number)v).doubleValue())>=1))throw new IOException("Every per-set correlation must be strictly between -1 and1");r.put("camera",camera);
        }else {
            text(raw,"matrix");allowedInputs.add("matrix");r.put("units",choice(raw,"units","",counts(op)?Set.of("raw-counts","estimated-counts"):Set.of("log2-expression","log2-values","continuous-values","methylation-M","methylation-beta")));
            if(!op.equals("NORMALIZE")) {text(raw,"design");allowedInputs.add("design");if(raw.containsKey("weights"))allowedInputs.add("weights");}
        }
        if(fitted(op)) {
            var contrasts=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();
            for(Object value:list(raw.get("contrasts"),"contrasts")) {
                var row=object(value,"contrast");keys(row,Set.of("id","values"));String id=text(row,"id");identity(id);if(!seen.add(id))throw new IOException("Repeated contrast id");
                var values=new ArrayList<Double>();boolean nonzero=false;for(Object x:list(row.get("values"),"contrast values")){double v=number(x,-Double.MAX_VALUE,Double.MAX_VALUE,true);nonzero|=v!=0;values.add(v);}
                if(values.isEmpty()||values.size()>1024||!nonzero)throw new IOException("Require1..1024 finite contrast entries with a nonzero entry");contrasts.add(Map.of("id",id,"values",values));
            }
            if(contrasts.isEmpty()||contrasts.size()>256)throw new IOException("Require1..256 contrasts");r.put("contrasts",contrasts);
            r.put("adjust_method",choice(raw,"adjust_method","BH",Set.of("BH","BY","holm","hochberg","hommel","bonferroni","none")));
            r.put("adjustment_scope",choice(raw,"adjustment_scope","",Set.of("per-contrast","all-contrasts")));
            var m=object(raw.get("moderation"),"moderation");keys(m,Set.of("method","trend","legacy","robust","winsor_tail_p","span","proportion","stdev_coef_lim","lfc"));var moderation=new LinkedHashMap<String,Object>();
            String method=choice(m,"method","",Set.of("EBAYES","TREAT")),trend=choice(m,"trend","none",method.equals("TREAT")?Set.of("none","Amean"):Set.of("none","Amean","provided"));
            moderation.put("method",method);moderation.put("trend",trend);moderation.put("legacy",choice(m,"legacy","auto",Set.of("auto","true","false")));
            boolean robust=bool(m,"robust",false);moderation.put("robust",robust);
            if(robust)moderation.put("winsor_tail_p",pair(m.getOrDefault("winsor_tail_p",List.of(.05,.1)),0,1,true));
            else if(m.containsKey("winsor_tail_p"))throw new IOException("Explicit winsor_tail_p requires robust=true; no unused setting");
            if(trend.equals("provided")){text(raw,"trend_covariate");allowedInputs.add("trend_covariate");}
            if(method.equals("TREAT")) {
                if(m.containsKey("span")||m.containsKey("proportion")||m.containsKey("stdev_coef_lim"))throw new IOException("TREAT does not accept span/proportion/stdev_coef_lim");
                if(!m.containsKey("lfc"))throw new IOException("TREAT requires explicit nonnegative log2 fold-change threshold lfc");moderation.put("lfc",real(m,"lfc",Double.NaN,0,Double.MAX_VALUE,true));
            }else {
                if(m.containsKey("lfc"))throw new IOException("lfc requires TREAT");
                double proportion=real(m,"proportion",.01,0,1,false);if(proportion>=1)throw new IOException("proportion must be strictly between0 and1");moderation.put("proportion",proportion);moderation.put("stdev_coef_lim",pair(m.getOrDefault("stdev_coef_lim",List.of(.1,4)),0,Double.MAX_VALUE,false));
                if(m.containsKey("span")) {if(moderation.get("legacy").equals("true"))throw new IOException("span forces modern estimation in original source; choose auto/false explicitly");moderation.put("span",real(m,"span",Double.NaN,0,1,false));}
            }r.put("moderation",moderation);
            if(raw.containsKey("block")) {
                allowedInputs.add("block");r.put("block_levels",strings(raw.get("block_levels"),"block_levels",2,10000));
            }
            if(!op.equals("EDGER_VOOM_LMFIT")) {
                var c=object(raw.getOrDefault("correlation",Map.of("mode","none")),"correlation");keys(c,Set.of("mode","value","trim","voom_passes"));String mode=choice(c,"mode","",Set.of("none","fixed","estimate"));var correlation=new LinkedHashMap<String,Object>();correlation.put("mode",mode);
                if(mode.equals("none")){keys(c,Set.of("mode"));if(raw.containsKey("block"))throw new IOException("Block requires fixed/estimated correlation");}
                else {
                    if(!raw.containsKey("block"))throw new IOException("Correlation requires block input");
                    if(mode.equals("fixed")){keys(c,Set.of("mode","value"));correlation.put("value",real(c,"value",Double.NaN,-1,1,false));if(!c.containsKey("value"))throw new IOException("Fixed correlation requires value");}
                    else {
                        correlation.put("trim",real(c,"trim",.15,0,.5,true));if(c.containsKey("value"))throw new IOException("Estimated correlation does not accept value");
                        if(op.equals("VOOM")){if(!c.containsKey("voom_passes"))throw new IOException("Estimated VOOM correlation requires explicit voom_passes1 or2");correlation.put("voom_passes",integer(c,"voom_passes",0,1,2));}
                        else if(c.containsKey("voom_passes"))throw new IOException("voom_passes requires VOOM");
                    }
                }r.put("correlation",correlation);
            }else if(raw.containsKey("correlation"))throw new IOException("Original edgeR voomLmFit estimates its own block correlation; no external correlation override");
        }
        if(counts(op)) {
            allowedInputs.add("libraries");allowedInputs.add("norm_factors");var n=object(raw.get("normalization"),"normalization");keys(n,Set.of("method","reference_column","logratio_trim","sum_trim","do_weighting","a_cutoff","p"));var normalized=new LinkedHashMap<String,Object>();String method=choice(n,"method","",Set.of("none","TMM","TMMwsp","RLE","upperquartile","provided"));normalized.put("method",method);
            if(method.equals("provided")!=raw.containsKey("norm_factors"))throw new IOException("norm_factors required exactly for provided normalization");
            if(method.equals("TMM")||method.equals("TMMwsp")) {
                normalized.put("logratio_trim",real(n,"logratio_trim",.3,0,.5,true));normalized.put("sum_trim",real(n,"sum_trim",.05,0,.5,true));normalized.put("do_weighting",bool(n,"do_weighting",true));normalized.put("a_cutoff",real(n,"a_cutoff",-1e10,-Double.MAX_VALUE,Double.MAX_VALUE,true));
                if(((Number)normalized.get("logratio_trim")).doubleValue()>=.5||((Number)normalized.get("sum_trim")).doubleValue()>=.5)throw new IOException("Trimming fractions must be below0.5");
                if(n.containsKey("reference_column"))normalized.put("reference_column",integer(n,"reference_column",0,1,10000));if(n.containsKey("p"))throw new IOException("p requires upperquartile");
            }else if(method.equals("upperquartile")){keys(n,Set.of("method","p"));normalized.put("p",real(n,"p",.75,0,1,false));}else keys(n,Set.of("method"));r.put("normalization",normalized);
            var v=object(raw.getOrDefault("voom",Map.of()),"voom");keys(v,op.equals("EDGER_VOOM_LMFIT")?Set.of("span","adaptive_span","normalize_method","sample_weights"):Set.of("span","adaptive_span","normalize_method"));var voom=new LinkedHashMap<String,Object>();boolean adaptive=bool(v,"adaptive_span",true);
            if(adaptive&&v.containsKey("span"))throw new IOException("Explicit span requires adaptive_span=false; original adaptive selection would override it");
            voom.put("span",real(v,"span",.5,0,1,false));voom.put("adaptive_span",adaptive);voom.put("normalize_method",choice(v,"normalize_method","none",Set.of("none","quantile","scale","cyclicloess")));
            if(op.equals("EDGER_VOOM_LMFIT")){boolean sampleWeights=bool(v,"sample_weights",false);if(sampleWeights&&raw.containsKey("weights"))throw new IOException("Original edgeR refuses supplied weights and sample-weight estimation together");voom.put("sample_weights",sampleWeights);}r.put("voom",voom);
        }
        if(op.equals("BATCH")) {
            if(!raw.containsKey("batch")&&!raw.containsKey("batch2")&&!raw.containsKey("covariates"))throw new IOException("BATCH requires batch, batch2 or covariates");
            for(String key:List.of("batch","batch2"))if(raw.containsKey(key)){allowedInputs.add(key);r.put(key+"_levels",strings(raw.get(key+"_levels"),key+"_levels",2,10000));}
            if(raw.containsKey("covariates"))allowedInputs.add("covariates");
        }
        if(op.equals("NORMALIZE"))r.put("normalize_method",choice(raw,"normalize_method","",Set.of("none","quantile","scale","cyclicloess")));
        for(String key:INPUTS)if(raw.containsKey(key)&&!allowedInputs.contains(key))throw new IOException("Unused input for "+op+": "+key);
        if(!fitted(op))for(String key:List.of("contrasts","moderation","correlation","adjust_method","adjustment_scope"))if(raw.containsKey(key))throw new IOException("Unused fitted-model field: "+key);
        if(!counts(op))for(String key:List.of("normalization","voom"))if(raw.containsKey(key))throw new IOException("Unused count-model field: "+key);
        if(!op.equals("CAMERAPR")&&raw.containsKey("camera")||!op.equals("NORMALIZE")&&raw.containsKey("normalize_method"))throw new IOException("Unused operation options");
        for(String key:List.of("block","batch","batch2"))if(raw.containsKey(key+"_levels")&&!allowedInputs.contains(key))throw new IOException("Unused factor levels for "+key);
        if(raw.containsKey("preparation_note"))text(raw,"preparation_note");r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));r.put("seed",integer(raw,"seed",1,0,Integer.MAX_VALUE));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,20000000000L));r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000L,1,40000000000L));
        r.put("maximum_features",integer(raw,"maximum_features",1000000,1,2000000));r.put("maximum_samples",integer(raw,"maximum_samples",2000,2,10000));r.put("maximum_entries",integer(raw,"maximum_entries",10000000,1,200000000));r.put("maximum_design_columns",integer(raw,"maximum_design_columns",64,1,1024));
        r.put("maximum_sets",integer(raw,"maximum_sets",10000,1,100000));r.put("maximum_memberships",integer(raw,"maximum_memberships",1000000,1,20000000));r.put("maximum_work",integer(raw,"maximum_work",100000000000L,1,1000000000000000L));return r;
    }
    private static double number(Object x,double min,double max,boolean inclusive)throws IOException {if(!(x instanceof Number))throw new IOException("Require numeric values");return real(Map.of("v",x),"v",Double.NaN,min,max,inclusive);}
    private static List<Double> pair(Object value,double min,double max,boolean probabilities)throws IOException {
        var x=list(value,"numeric pair");if(x.size()!=2)throw new IOException("Require two values");var out=List.of(number(x.get(0),min,max,true),number(x.get(1),min,max,true));
        if(probabilities?out.get(0)+out.get(1)>=1:out.get(0)>out.get(1))throw new IOException("Invalid numeric pair");return out;
    }
}
