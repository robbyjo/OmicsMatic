/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed original-edgeR requests; numeric designs and contrasts are data, never R code. */
final class EdgeRWorkflowContracts {
    private EdgeRWorkflowContracts() { }
    static final Set<String> PROFILES=Set.of("edgeR-3.40.2-R4.6.1","edgeR-3.42.4-R4.6.1","edgeR-4.10.7-R4.6.1");
    static final List<String> INPUTS=List.of("counts","groups","design","libraries","norm_factors");
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","model","out","counts","groups","group_levels","design","libraries","norm_factors",
            "count_units","normalization","filter","dispersion","fit","results","adjust_method","rscript","r_libraries","seed",
            "timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_features","maximum_samples","maximum_entries","maximum_design_columns","maximum_work","preparation_note"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"RUN".equals(text(raw,"mode")))throw new IOException("Require schema_version1 and mode RUN");
        choice(raw,"profile","",PROFILES);String model=choice(raw,"model","",Set.of("EXACT","GLM_LRT","GLM_QLF"));
        var r=new LinkedHashMap<String,Object>(raw);
        for(String field:List.of("out","counts","groups","rscript"))text(raw,field);
        for(String field:List.of("libraries","norm_factors","preparation_note"))if(raw.containsKey(field))identity(text(raw,field));
        r.put("count_units",choice(raw,"count_units","",Set.of("raw-counts","estimated-counts")));
        var levels=strings(raw.get("group_levels"),"group_levels",model.equals("EXACT")?2:1,10000);r.put("group_levels",levels);
        if(model.equals("EXACT")){if(raw.containsKey("design"))throw new IOException("EXACT uses declared groups, not a design matrix");}
        else text(raw,"design");
        var n=object(raw.get("normalization"),"normalization");keys(n,Set.of("method","reference_column","logratio_trim","sum_trim","do_weighting","a_cutoff","p"));
        String method=choice(n,"method","",Set.of("TMM","TMMwsp","RLE","upperquartile","none","provided"));
        if(method.equals("provided")!=raw.containsKey("norm_factors"))throw new IOException("norm_factors is required exactly for provided normalization");
        var normalization=new LinkedHashMap<String,Object>();normalization.put("method",method);
        if(method.equals("TMM")||method.equals("TMMwsp")) {
            if(n.containsKey("reference_column"))normalization.put("reference_column",integer(n,"reference_column",0,1,10000));
            normalization.put("logratio_trim",real(n,"logratio_trim",.3,0,.5,true));normalization.put("sum_trim",real(n,"sum_trim",.05,0,.5,true));
            normalization.put("do_weighting",bool(n,"do_weighting",true));normalization.put("a_cutoff",real(n,"a_cutoff",-1e10,-Double.MAX_VALUE,Double.MAX_VALUE,true));
            if(n.containsKey("p"))throw new IOException("p is only used by upperquartile");
        }else if(method.equals("upperquartile")) {
            keys(n,Set.of("method","p"));normalization.put("p",real(n,"p",.75,0,1,false));
        }else keys(n,Set.of("method"));
        r.put("normalization",normalization);
        var f=object(raw.get("filter"),"filter");String filter=choice(f,"method","",Set.of("none","filterByExpr"));
        var filtering=new LinkedHashMap<String,Object>();filtering.put("method",filter);
        if(filter.equals("none"))keys(f,Set.of("method"));
        else {
            keys(f,Set.of("method","basis","order","keep_library_sizes","renormalize_after_filter","min_count","min_total_count","large_n","min_prop"));
            filtering.put("basis",choice(f,"basis","",model.equals("EXACT")?Set.of("group"):Set.of("group","design")));
            String order=choice(f,"order","",Set.of("before-normalization","after-normalization"));filtering.put("order",order);
            filtering.put("keep_library_sizes",bool(f,"keep_library_sizes",false));
            if(order.equals("before-normalization")&&f.containsKey("renormalize_after_filter"))throw new IOException("renormalize_after_filter is only used by after-normalization filtering");
            if(order.equals("after-normalization")) {
                boolean renormalize=bool(f,"renormalize_after_filter",false);
                if(renormalize&&method.equals("provided"))throw new IOException("Provided factors cannot be recomputed; use an explicit computed normalization method");
                filtering.put("renormalize_after_filter",renormalize);
            }
            filtering.put("min_count",real(f,"min_count",10,0,Double.MAX_VALUE,true));
            filtering.put("min_total_count",real(f,"min_total_count",15,0,Double.MAX_VALUE,true));
            filtering.put("large_n",real(f,"large_n",10,0,Double.MAX_VALUE,false));filtering.put("min_prop",real(f,"min_prop",.7,0,1,true));
        }
        r.put("filter",filtering);
        var d=object(raw.get("dispersion"),"dispersion");String dm=choice(d,"method","",Set.of("estimateDisp","provided","adjusted-QL-auto"));
        var dispersion=new LinkedHashMap<String,Object>();dispersion.put("method",dm);
        if(dm.equals("provided")) {keys(d,Set.of("method","value"));dispersion.put("value",real(d,"value",Double.NaN,0,Double.MAX_VALUE,true));if(!d.containsKey("value"))throw new IOException("Provided dispersion requires explicit value");}
        else if(dm.equals("adjusted-QL-auto"))keys(d,Set.of("method"));
        else {
            keys(d,Set.of("method","trend_method","robust","tagwise","prior_df","span","min_row_sum","grid_length","grid_range","winsor_tail_p","tolerance"));
            dispersion.put("trend_method",choice(d,"trend_method","locfit",Set.of("none","loess","locfit","movingave","locfit.mixed")));
            dispersion.put("robust",bool(d,"robust",false));dispersion.put("tagwise",bool(d,"tagwise",true));
            if(d.containsKey("prior_df"))dispersion.put("prior_df",real(d,"prior_df",Double.NaN,0,Double.MAX_VALUE,false));
            if(d.containsKey("span"))dispersion.put("span",real(d,"span",Double.NaN,0,1,false));
            dispersion.put("min_row_sum",real(d,"min_row_sum",5,0,Double.MAX_VALUE,true));dispersion.put("grid_length",integer(d,"grid_length",21,3,1001));
            dispersion.put("grid_range",pair(d.getOrDefault("grid_range",List.of(-10,10)),"grid_range",-1000,1000,false));
            dispersion.put("winsor_tail_p",pair(d.getOrDefault("winsor_tail_p",List.of(.05,.1)),"winsor_tail_p",0,1,true));
            dispersion.put("tolerance",real(d,"tolerance",1e-6,0,1,false));
        }
        r.put("dispersion",dispersion);
        var fit=object(raw.getOrDefault("fit",Map.of()),"fit");var fitting=new LinkedHashMap<String,Object>();
        if(model.equals("EXACT")) {
            keys(fit,Set.of("rejection_region","big_count","prior_count","dispersion_selector"));
            fitting.put("rejection_region",choice(fit,"rejection_region","doubletail",Set.of("doubletail","deviance","smallp")));
            fitting.put("big_count",real(fit,"big_count",900,0,Double.MAX_VALUE,false));
            if(dm.equals("estimateDisp"))fitting.put("dispersion_selector",choice(fit,"dispersion_selector","auto",Set.of("auto","common","trended","tagwise")));
            else if(fit.containsKey("dispersion_selector"))throw new IOException("dispersion_selector requires estimated dispersion");
        }else if(model.equals("GLM_LRT"))keys(fit,Set.of("prior_count"));
        else {
            keys(fit,Set.of("prior_count","ql_mode","abundance_trend","robust","winsor_tail_p","poisson_bound","dispersion_selector","top_proportion"));
            String qm=choice(fit,"ql_mode","",text(raw,"profile").equals("edgeR-4.10.7-R4.6.1")?Set.of("legacy","adjusted"):Set.of("legacy"));fitting.put("ql_mode",qm);
            fitting.put("abundance_trend",bool(fit,"abundance_trend",true));fitting.put("robust",bool(fit,"robust",false));
            fitting.put("winsor_tail_p",pair(fit.getOrDefault("winsor_tail_p",List.of(.05,.1)),"winsor_tail_p",0,1,true));fitting.put("poisson_bound",bool(fit,"poisson_bound",true));
            if(dm.equals("estimateDisp"))fitting.put("dispersion_selector",choice(fit,"dispersion_selector","",Set.of("common","trended")));
            else if(fit.containsKey("dispersion_selector"))throw new IOException("QL dispersion_selector requires estimateDisp");
            if(dm.equals("adjusted-QL-auto")) {
                if(!qm.equals("adjusted"))throw new IOException("adjusted-QL-auto requires current adjusted QL mode");
                if(fit.containsKey("top_proportion"))fitting.put("top_proportion",real(fit,"top_proportion",Double.NaN,0,1,false));
            }else if(fit.containsKey("top_proportion"))throw new IOException("top_proportion is only used with adjusted-QL-auto");
        }
        if(dm.equals("adjusted-QL-auto")&&!model.equals("GLM_QLF"))throw new IOException("adjusted-QL-auto requires GLM_QLF");
        fitting.put("prior_count",real(fit,"prior_count",.125,0,Double.MAX_VALUE,true));r.put("fit",fitting);
        var results=new ArrayList<Map<String,Object>>();var resultIds=new HashSet<String>();
        for(Object value:list(raw.get("results"),"results")) {
            var entry=object(value,"result");keys(entry,Set.of("id","pair","coefficients","contrast"));String id=text(entry,"id");identity(id);
            if(!resultIds.add(id))throw new IOException("Repeated result identity");var item=new LinkedHashMap<String,Object>();item.put("id",id);
            if(model.equals("EXACT")) {
                keys(entry,Set.of("id","pair"));var groups=strings(entry.get("pair"),"pair (denominator then numerator)",2,2);
                if(!levels.containsAll(groups))throw new IOException("Exact pair contains an undeclared group");item.put("pair",groups);
            }else {
                if(entry.containsKey("pair")||entry.containsKey("coefficients")==entry.containsKey("contrast"))throw new IOException("GLM result requires exactly coefficients or contrast");
                if(entry.containsKey("coefficients")) {
                    var coefficients=new ArrayList<Long>();for(Object c:list(entry.get("coefficients"),"coefficients"))coefficients.add(integer(scalar(c,"coefficients"),"v",0,1,1024));
                    if(coefficients.isEmpty()||new HashSet<>(coefficients).size()!=coefficients.size())throw new IOException("Require nonempty unique 1-based coefficient indices");item.put("coefficients",coefficients);
                }else {
                    var contrast=new ArrayList<Double>();boolean nonzero=false;
                    for(Object c:list(entry.get("contrast"),"contrast")){double x=real(scalar(c,"contrast"),"v",Double.NaN,-Double.MAX_VALUE,Double.MAX_VALUE,true);contrast.add(x);nonzero|=x!=0;}
                    if(!nonzero||contrast.size()>1024)throw new IOException("Require a nonzero finite contrast vector");item.put("contrast",contrast);
                }
            }results.add(item);
        }
        if(results.isEmpty()||results.size()>256)throw new IOException("Require1..256 results");r.put("results",results);
        r.put("adjust_method",choice(raw,"adjust_method","BH",Set.of("BH","BY","holm","hochberg","hommel","bonferroni","none")));
        r.put("seed",integer(raw,"seed",1,0,Integer.MAX_VALUE));r.put("r_libraries",strings(raw.get("r_libraries"),"r_libraries",1,16));
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512000000,1,20000000000L));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2000000000L,1,40000000000L));r.put("maximum_features",integer(raw,"maximum_features",1000000,1,2000000));
        r.put("maximum_samples",integer(raw,"maximum_samples",2000,2,10000));r.put("maximum_entries",integer(raw,"maximum_entries",10000000,1,200000000));
        r.put("maximum_design_columns",integer(raw,"maximum_design_columns",64,2,1024));r.put("maximum_work",integer(raw,"maximum_work",100000000000L,1,1000000000000000L));
        return r;
    }
    private static List<Double> pair(Object raw,String label,double min,double max,boolean probabilities)throws IOException {
        var values=list(raw,label);if(values.size()!=2)throw new IOException(label+" requires two values");var result=new ArrayList<Double>();
        for(Object value:values)result.add(real(scalar(value,label),"v",Double.NaN,min,max,true));
        if(probabilities?result.get(0)+result.get(1)>=1:result.get(0)>=result.get(1))throw new IOException("Invalid "+label);return result;
    }
    private static Map<String,Object> scalar(Object value,String label)throws IOException {if(!(value instanceof Number))throw new IOException("Require numeric entries in "+label);return Map.of("v",value);}
}
