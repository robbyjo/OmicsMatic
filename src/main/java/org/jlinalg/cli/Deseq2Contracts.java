/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Typed requests for original DESeq2 execution. No user R expressions are accepted. */
final class Deseq2Contracts {
    private Deseq2Contracts() { }
    static final String PROFILE="DESeq2-1.52.0-R4.6.1";
    static final List<String> INPUTS=List.of("counts","samples","keep_genes","size_factors","normalization_factors","geometric_means","control_genes");
    private static final Set<String> RESERVED=Set.of("if","else","repeat","while","function","for","in","next","break","TRUE","FALSE","NULL","Inf","NaN","NA","NA_integer_","NA_real_","NA_complex_","NA_character_","sample_index","collapse_group");
    static Map<String,Object> read(Path path)throws IOException {
        return read(path,2_000_000);
    }
    static Map<String,Object> read(Path path,long maximumBytes)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maximumBytes)throw new IOException("Require a bounded regular UTF-8 JSON document");
        return object(SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8)),"request");
    }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","out","counts","samples","variables","design","reduced_design","fit","results","transforms","units","count_preparation","keep_genes","size_factors","normalization_factors","geometric_means","control_genes","collapse_by","rscript","r_libraries","timeout_seconds","maximum_entries","maximum_input_bytes","maximum_output_bytes","maximum_samples","maximum_genes","maximum_model_columns","seed","save_fitted_object"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"ANALYZE".equals(raw.get("mode"))||!PROFILE.equals(raw.get("profile")))throw new IOException("Require schema_version 1, mode ANALYZE and profile "+PROFILE);
        Map<String,Object> r=new LinkedHashMap<>(raw);
        for(String name:List.of("out","counts","samples","rscript"))text(raw,name);
        for(String name:INPUTS)if(raw.containsKey(name))text(raw,name);
        String units=choice(raw,"units","raw_counts",Set.of("raw_counts","estimated_counts"));
        String preparation=choice(raw,"count_preparation","integer_only",Set.of("integer_only","R_round"));
        if(units.equals("raw_counts")&&!preparation.equals("integer_only"))throw new IOException("R_round requires explicit estimated_counts units; raw counts are never rounded");
        r.put("units",units);r.put("count_preparation",preparation);
        var libraries=list(raw.get("r_libraries"),"r_libraries");
        if(libraries.isEmpty()||libraries.size()>16)throw new IOException("Declare 1..16 explicit R library paths");
        for(Object lib:libraries)if(!(lib instanceof String s)||s.isBlank()||s.indexOf('\0')>=0)throw new IOException("Invalid R library path");
        r.put("timeout_seconds",integer(raw,"timeout_seconds",3600,1,86400));
        r.put("maximum_entries",integer(raw,"maximum_entries",20_000_000,1,200_000_000));
        r.put("maximum_input_bytes",integer(raw,"maximum_input_bytes",512_000_000,1,20_000_000_000L));
        r.put("maximum_output_bytes",integer(raw,"maximum_output_bytes",2_000_000_000L,1,40_000_000_000L));
        r.put("maximum_samples",integer(raw,"maximum_samples",50_000,2,1_000_000));
        r.put("maximum_genes",integer(raw,"maximum_genes",2_000_000,1,20_000_000));
        r.put("maximum_model_columns",integer(raw,"maximum_model_columns",128,1,1024));
        r.put("seed",integer(raw,"seed",1,0,Integer.MAX_VALUE));
        r.put("save_fitted_object",bool(raw,"save_fitted_object",true));
        var variables=new ArrayList<Map<String,Object>>();var names=new HashSet<String>();
        for(Object item:list(raw.get("variables"),"variables")) {
            var v=object(item,"variable");keys(v,Set.of("name","kind","levels"));String name=text(v,"name");identifier(name);
            if(!names.add(name))throw new IOException("Duplicate variable: "+name);
            String kind=choice(v,"kind","",Set.of("factor","numeric"));
            Map<String,Object> normalized=new LinkedHashMap<>(Map.of("name",name,"kind",kind));
            if(kind.equals("factor")) {
                var levels=list(v.get("levels"),"factor levels");if(levels.isEmpty()||levels.size()>128)throw new IOException("Require 1..128 ordered factor levels");
                var unique=new HashSet<String>();for(Object level:levels){if(!(level instanceof String s))throw new IOException("Factor levels must be strings");identity(s);if(!unique.add(s))throw new IOException("Duplicate factor level");}
                normalized.put("levels",List.copyOf(levels));
            }else if(v.containsKey("levels"))throw new IOException("Numeric variables do not have factor levels");
            variables.add(normalized);
        }
        if(variables.size()>32)throw new IOException("At most 32 typed variables are supported");
        r.put("variables",variables);
        var design=design(object(raw.get("design"),"design"),names);r.put("design",design);
        var fit=raw.containsKey("fit")?object(raw.get("fit"),"fit"):Map.<String,Object>of();
        keys(fit,Set.of("test","fit_type","size_factor_type","beta_prior","min_replicates_for_replace","use_t","min_mu"));
        String test=choice(fit,"test","Wald",Set.of("Wald","LRT","none"));
        String fitType=choice(fit,"fit_type","parametric",Set.of("parametric","local","mean","glmGamPoi"));
        var settings=new LinkedHashMap<String,Object>();settings.put("test",test);settings.put("fit_type",fitType);
        settings.put("size_factor_type",choice(fit,"size_factor_type","ratio",Set.of("ratio","poscounts","iterate")));
        settings.put("beta_prior",bool(fit,"beta_prior",false));settings.put("use_t",bool(fit,"use_t",false));
        settings.put("min_mu",real(fit,"min_mu",fitType.equals("glmGamPoi")?1e-6:.5,0,1e6,false));
        Object replace=fit.getOrDefault("min_replicates_for_replace",7);
        if(replace==null)throw new IOException("Null min_replicates_for_replace");
        if(!"Inf".equals(replace))replace=integer(Map.of("value",replace),"value",7,0,1_000_000);
        settings.put("min_replicates_for_replace",replace);r.put("fit",settings);
        if(test.equals("LRT"))r.put("reduced_design",design(object(raw.get("reduced_design"),"reduced_design"),names));
        else if(raw.containsKey("reduced_design"))throw new IOException("reduced_design requires an LRT fit");
        var results=new ArrayList<Map<String,Object>>();var ids=new HashSet<String>();
        for(Object item:list(raw.getOrDefault("results",List.of()),"results")) {
            var result=object(item,"result");keys(result,Set.of("id","selector","alpha","lfc_threshold","alternative","independent_filtering","cooks_cutoff","p_adjust_method","add_mle","test","shrink"));
            String id=text(result,"id");fileId(id);if(!ids.add(id))throw new IOException("Duplicate result ID");
            var n=new LinkedHashMap<String,Object>();n.put("id",id);n.put("selector",selector(object(result.get("selector"),"selector"),variables));
            n.put("alpha",real(result,"alpha",.1,0,1,false));
            n.put("lfc_threshold",real(result,"lfc_threshold",0,0,1e6,true));
            n.put("alternative",choice(result,"alternative","greaterAbs",Set.of("greaterAbs","greaterAbsUPSHOT","lessAbs","greater","less","greaterAbs2014")));
            n.put("independent_filtering",bool(result,"independent_filtering",true));n.put("add_mle",bool(result,"add_mle",false));
            n.put("p_adjust_method",choice(result,"p_adjust_method","BH",Set.of("holm","hochberg","hommel","bonferroni","BH","BY","fdr","none")));
            n.put("test",choice(result,"test",test,Set.of("Wald","LRT")));
            Object cutoff=result.getOrDefault("cooks_cutoff","source-default");
            if(cutoff==null)throw new IOException("Null cooks_cutoff");
            if(!Boolean.FALSE.equals(cutoff)&&!Set.of("source-default","Inf").contains(cutoff))cutoff=real(Map.of("value",cutoff),"value",0,0,1e12,true);
            n.put("cooks_cutoff",cutoff);
            if(result.containsKey("shrink")) {
                var shrink=object(result.get("shrink"),"shrink");keys(shrink,Set.of("type","lfc_threshold","svalue"));
                var s=new LinkedHashMap<String,Object>();String type=choice(shrink,"type","",Set.of("normal","apeglm","ashr"));s.put("type",type);
                s.put("lfc_threshold",real(shrink,"lfc_threshold",0,0,1e6,true));s.put("svalue",bool(shrink,"svalue",false));
                if(type.equals("apeglm")&&!"coefficient".equals(object(n.get("selector"),"selector").get("kind")))throw new IOException("apeglm requires a coefficient selector so the fitted coefficient and base result agree");
                n.put("shrink",s);
            }
            results.add(n);
        }
        if(results.size()>128||test.equals("none")&&!results.isEmpty())throw new IOException("At most 128 results; no testing results for fit.test=none");
        r.put("results",results);
        var transforms=new ArrayList<Map<String,Object>>();ids.clear();
        for(Object item:list(raw.getOrDefault("transforms",List.of()),"transforms")) {
            var t=object(item,"transform");keys(t,Set.of("id","method","blind","fit_type","nsub","beta_prior_variance"));
            String id=text(t,"id");fileId(id);if(!ids.add(id))throw new IOException("Duplicate transform ID");
            String method=choice(t,"method","",Set.of("vst","varianceStabilizingTransformation","rlog"));
            var n=new LinkedHashMap<String,Object>();n.put("id",id);n.put("method",method);n.put("blind",bool(t,"blind",true));
            n.put("fit_type",choice(t,"fit_type","parametric",Set.of("parametric","local","mean","glmGamPoi")));
            if(method.equals("vst"))n.put("nsub",integer(t,"nsub",1000,1,20_000_000));else if(t.containsKey("nsub"))throw new IOException("nsub only applies to vst");
            if(t.containsKey("beta_prior_variance")){if(!method.equals("rlog"))throw new IOException("beta_prior_variance only applies to rlog");n.put("beta_prior_variance",real(t,"beta_prior_variance",1,0,1e12,false));}
            transforms.add(n);
        }
        if(transforms.size()>16)throw new IOException("At most 16 requested transforms");r.put("transforms",transforms);
        if(raw.containsKey("collapse_by")){String name=text(raw,"collapse_by");identifier(name);if(names.contains(name))throw new IOException("Collapse grouping must be separate from modeled variables");
            if(raw.containsKey("size_factors")||raw.containsKey("normalization_factors"))throw new IOException("Collapse before supplying sample size/normalization factors; no ambiguous factor aggregation is performed");}
        if(raw.containsKey("size_factors")&&raw.containsKey("normalization_factors"))throw new IOException("Supply size_factors or normalization_factors, not both");
        if((raw.containsKey("size_factors")||raw.containsKey("normalization_factors"))&&(raw.containsKey("geometric_means")||raw.containsKey("control_genes")))throw new IOException("Explicit factors cannot be combined with size-factor estimation controls/means");
        if("iterate".equals(settings.get("size_factor_type"))&&(raw.containsKey("geometric_means")||raw.containsKey("control_genes")))throw new IOException("The original iterative estimator does not use explicit means/control genes; no setting is silently ignored");
        return r;
    }
    private static Map<String,Object> selector(Map<String,Object> s,List<Map<String,Object>> variables)throws IOException {
        String kind=text(s,"kind");
        switch(kind) {
            case "factor" -> {
                keys(s,Set.of("kind","factor","numerator","denominator"));String name=text(s,"factor");
                var variable=variables.stream().filter(v->name.equals(v.get("name"))&&"factor".equals(v.get("kind"))).findFirst().orElseThrow(()->new IOException("Unknown factor selector: "+name));
                var levels=list(variable.get("levels"),"levels");String a=text(s,"numerator"),b=text(s,"denominator");
                if(!levels.contains(a)||!levels.contains(b))throw new IOException("Contrast levels are not declared for the selected factor");
                return Map.of("kind",kind,"factor",name,"numerator",a,"denominator",b,"numerator_code","l"+(levels.indexOf(a)+1),"denominator_code","l"+(levels.indexOf(b)+1));
            }
            case "coefficient" -> {keys(s,Set.of("kind","index"));return Map.of("kind",kind,"index",integer(s,"index",0,1,4096));}
            case "numeric" -> {
                keys(s,Set.of("kind","weights"));var weights=list(s.get("weights"),"contrast weights");if(weights.isEmpty()||weights.size()>4096)throw new IOException("Require 1..4096 contrast weights");
                var values=new ArrayList<Double>();for(Object weight:weights)values.add(real(Collections.singletonMap("value",weight),"value",0,-1e12,1e12,true));
                if(values.stream().allMatch(v->v==0))throw new IOException("A numeric contrast must contain a nonzero weight");return Map.of("kind",kind,"weights",values);
            }
            default -> throw new IOException("Unknown selector kind; use factor, coefficient or numeric");
        }
    }
    private static Map<String,Object> design(Map<String,Object> d,Set<String> variables)throws IOException {
        keys(d,Set.of("intercept","terms"));boolean intercept=bool(d,"intercept",true);var terms=list(d.get("terms"),"design terms");if(terms.size()>128)throw new IOException("At most 128 model terms");
        var normalized=new ArrayList<List<String>>();var unique=new HashSet<Set<String>>();
        for(Object item:terms){var term=list(item,"term");if(term.isEmpty()||term.size()>8)throw new IOException("Require 1..8 variables per term");var names=new ArrayList<String>();
            for(Object value:term){if(!(value instanceof String name)||!variables.contains(name)||names.contains(name))throw new IOException("Unknown/repeated model variable: "+value);names.add(name);}
            if(!unique.add(Set.copyOf(names)))throw new IOException("Duplicate model term");normalized.add(List.copyOf(names));}
        if(terms.isEmpty()&&!intercept)throw new IOException("A model must have an intercept or terms");return Map.of("intercept",intercept,"terms",normalized);
    }
    static Map<String,Object> object(Object value,String label)throws IOException {
        if(!(value instanceof Map<?,?> raw))throw new IOException("Require object: "+label);var result=new LinkedHashMap<String,Object>();
        for(var entry:raw.entrySet()){if(!(entry.getKey() instanceof String name))throw new IOException("Object key must be text");result.put(name,entry.getValue());}return result;
    }
    static List<?> list(Object value,String label)throws IOException {if(!(value instanceof List<?> l))throw new IOException("Require array: "+label);return l;}
    static void keys(Map<String,Object> value,Set<String> allowed)throws IOException {for(String key:value.keySet())if(!allowed.contains(key))throw new IOException("Unknown field: "+key);}
    static String text(Map<String,Object> value,String key)throws IOException {if(!(value.get(key) instanceof String s)||s.isBlank()||s.indexOf('\0')>=0)throw new IOException("Require nonblank text: "+key);return s;}
    static boolean bool(Map<String,Object> value,String key,boolean fallback)throws IOException {if(!value.containsKey(key))return fallback;if(!(value.get(key) instanceof Boolean b))throw new IOException("Require boolean: "+key);return b;}
    static long integer(Map<String,Object> value,String key,long fallback,long min,long max)throws IOException {if(!value.containsKey(key))return fallback;Object v=value.get(key);if(!(v instanceof Number))throw new IOException("Require integer: "+key);try{long n=new BigDecimal(v.toString()).longValueExact();if(n<min||n>max)throw new ArithmeticException();return n;}catch(ArithmeticException e){throw new IOException("Integer outside declared range: "+key,e);}}
    static double real(Map<String,Object> value,String key,double fallback,double min,double max,boolean includeMin)throws IOException {if(!value.containsKey(key))return fallback;Object v=value.get(key);if(!(v instanceof Number n))throw new IOException("Require number: "+key);double d=n.doubleValue();if(!Double.isFinite(d)||d>max||(includeMin?d<min:d<=min))throw new IOException("Numeric setting outside declared range: "+key);return d;}
    static String choice(Map<String,Object> value,String key,String fallback,Set<String> allowed)throws IOException {String s=value.containsKey(key)?text(value,key):fallback;if(!allowed.contains(s))throw new IOException("Unsupported "+key+": "+s);return s;}
    static void identifier(String value)throws IOException {if(!value.matches("[A-Za-z][A-Za-z0-9_]{0,63}")||RESERVED.contains(value))throw new IOException("Require a plain model-column identifier, not R code: "+value);}
    static void fileId(String value)throws IOException {if(!value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}"))throw new IOException("Require a plain result/transform ID");}
    static void identity(String value)throws IOException {if(value.isBlank()||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require exact nonblank identities without control characters");}
    static Path resolve(Path directory,String value){Path p=Path.of(value);return(p.isAbsolute()?p:directory.resolve(p)).toAbsolutePath().normalize();}
    static void json(Path path,Object value)throws IOException {Files.write(path,RsemJson.bytes(value));}
}
