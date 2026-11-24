/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Explicit original preparation APIs; no automatic input repair or installation. */
final class GenomicsemPrepContracts {
    private GenomicsemPrepContracts() { }
    static final String PROFILE="genomicsem-preparation-da95d43-r461-win64-lavaan0619-v1";
    static final String COMPLETE="COMPLETE_SCOPED_ORIGINAL_GENOMICSEM_PREPARATION";
    static final Set<String> OPERATIONS=Set.of("MUNGE","LDSC","SUMSTATS","READ_FUSION","S_LDSC","ENRICH");
    static final String ACTION="REVIEW REQUIRED: read WARNINGS.md, scientific-audit.json, original logs and lossless typed exports before using products. "
        +"The original pinned source controls column interpretation, odds-ratio detection, allele alignment, frequency folding, scaling, filtering, block overrides and annotation selection. These are recorded, never silently replaced by Java rules. "
        +"Supply the correct LD/reference ancestry, genome build, trait order, total/effective sample sizes, prevalences, effect/SE scales and FUSION weight definitions. A full joint sampling covariance is not assembled from independent pairwise LDSC fits. "
        +"Original enrichment P values are one-sided upper tails for enrichment above1. No historical paper-edition equivalence, independent optimizer or whole-paper/biological qualification is established. "
        +"SKIP3: dependent analysis skipped; obtain the exact named permitted runtime, add-on or input using an allowed installation method. If installation is prohibited, retain the skip. "
        +"Unavailable source fields stay unavailable. Under RETAIN_SOURCE_AND_FLAG only explicitly admitted usable units may be used; raw munged files are intermediates,not finite association results. "
        +"FAIL2: no complete admitted result; preserve raw/partial output, resolve the stated input, numerical, capacity, process or integrity cause, and retry NEW output. "
        +"Analysis never downloads, installs, overwrites an existing output or upgrades a runtime. Windows R4.6.1 execution is qualified separately from portable saved inspection. Hashes establish integrity, not authenticity or host attestation. Limits are monitored, not OS job/RAM quotas.";
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        var fields=new HashSet<>(Set.of("schema_version","mode","profile","operation","runtime_home","runtime_addons","out","bundle","paths","options"));
        require(raw.keySet().equals(fields)||raw.keySet().equals(union(fields,Set.of("limits"))),"Declare schema_version,mode,profile,operation,runtime_home,runtime_addons,out,bundle,paths,options and optional limits");
        integer(raw.get("schema_version"),1,1);require("RUN".equals(raw.get("mode")),"Require RUN or a separate VERIFY/REPLAY control request");
        if(!PROFILE.equals(raw.get("profile")))throw new GenomicsemRuntime.Unavailable("Unsupported original preparation profile; no substitution for historical paper editions");
        String operation=choice(raw,"operation",OPERATIONS);
        for(String k:List.of("runtime_home","out","bundle"))literal(text(raw.get(k),k),k);
        if(raw.get("runtime_addons")!=null)literal(text(raw.get("runtime_addons"),"runtime_addons"),"runtime_addons");
        var paths=object(raw.get("paths"));Set<String> expected=switch(operation){case "MUNGE"->Set.of("files","hm3");case "LDSC"->Set.of("traits","ld","wld");case "SUMSTATS"->Set.of("files","reference");case "READ_FUSION"->Set.of("files");case "S_LDSC"->Set.of("traits","ld_prefixes","weights_prefix","frequency_prefix");case "ENRICH"->Set.of("stratified","model");default->throw new IOException("Unknown operation");};
        require(paths.keySet().equals(expected),"Declare exactly source paths "+expected);
        var options=object(raw.get("options"));var optionNames=new HashSet<>(Set.of("trait_names","seed","upstream_behavior_reviewed","warning_policy","unavailable_output_policy"));
        switch(operation){
            case "MUNGE"->optionNames.addAll(Set.of("N","info_filter","maf_filter","column_names","parallel","cores"));
            case "LDSC"->optionNames.addAll(Set.of("sample_prevalence","population_prevalence","separate_weights","chromosomes","blocks","standardized","select","chisq_max"));
            case "SUMSTATS"->optionNames.addAll(Set.of("N","OLS","linprob","se_logit","betas","info_filter","maf_filter","keep_indel","parallel","cores","remove_ambiguous","direction_filter"));
            case "READ_FUSION"->optionNames.addAll(Set.of("N","binary","permutation"));
            case "S_LDSC"->optionNames.addAll(Set.of("sample_prevalence","population_prevalence","blocks","exclude_continuous"));
            case "ENRICH"->optionNames.addAll(Set.of("annotations","parameters","fix","std_lv","remove_flanking","tau","include_baseline","tolerance","fixed_parameters","smoothing_policy"));
            default->throw new IOException("Unknown operation");
        }
        require(options.keySet().equals(optionNames),"Declare exactly scientific options "+optionNames);
        var traits=list(options.get("trait_names"));require(traits.size()>=(Set.of("LDSC","S_LDSC","ENRICH").contains(operation)?2:1)&&traits.size()<=64,"Require the complete ordered1..64 trait list,at least2 for joint covariance/enrichment");Set<String> unique=new HashSet<>();
        for(Object value:traits){String s=text(value,"trait name");require(s.matches("[A-Za-z][A-Za-z0-9_]{0,63}")&&!Set.of("SNP","Gene").contains(s)&&unique.add(s),"Require unique simple lavaan trait identifiers,excluding SNP/Gene");}
        int n=traits.size();integer(options.get("seed"),1,Integer.MAX_VALUE);require(bool(options,"upstream_behavior_reviewed"),"Require upstream_behavior_reviewed=true after reviewing source transformations and study suitability");choice(options,"warning_policy",Set.of("RETAIN_AND_FLAG","FAIL_OPERATION_WARNINGS"));choice(options,"unavailable_output_policy",Set.of("FAIL","RETAIN_SOURCE_AND_FLAG"));
        for(var e:paths.entrySet()){
            if(Set.of("files","traits","ld_prefixes").contains(e.getKey())){
                var array=list(e.getValue());require(!array.isEmpty()&&array.size()<=256,"Invalid path population");if(!e.getKey().equals("ld_prefixes"))require(array.size()==n,"File and trait order/length must agree");Set<String> seen=new HashSet<>();for(Object p:array)require(seen.add(relative(text(p,"relative input"))),"Duplicated source path");
            }else relative(text(e.getValue(),e.getKey()));
        }
        if(Set.of("MUNGE","SUMSTATS").contains(operation)){
            numericArray(options,"N",n,true,Double.POSITIVE_INFINITY);fraction(options,"info_filter",true);fraction(options,"maf_filter",true);long cores=integer(options.get("cores"),1,64);require(bool(options,"parallel")||cores==1,"Sequential preparation requires cores=1");
        }
        if(operation.equals("MUNGE")){
            var columns=object(options.get("column_names"));keys(columns,Set.of("SNP","A1","A2","effect","INFO","P","N","MAF","Z"));for(Object c:columns.values())literal(text(c,"source column name"),"source column name");
        }
        if(Set.of("LDSC","S_LDSC").contains(operation)){
            var sample=numericArray(options,"sample_prevalence",n,true,1);var pop=numericArray(options,"population_prevalence",n,true,1);for(int i=0;i<n;i++)require((sample.get(i)==null)==(pop.get(i)==null),"Declare both prevalences or two nulls for each continuous trait");integer(options.get("blocks"),2,10000);
        }
        if(operation.equals("LDSC")){
            long chromosomes=integer(options.get("chromosomes"),1,22);bool(options,"separate_weights");bool(options,"standardized");Object select=options.get("select");
            if(!Boolean.FALSE.equals(select)&&!Set.of("ODD","EVEN").contains(select instanceof String?select:"")){
                var a=list(select);require(!a.isEmpty()&&a.size()<=22,"Empty/excessive chromosome selection");Set<Long> seen=new HashSet<>();for(Object c:a)require(seen.add(integer(c,1,chromosomes)),"Duplicated selected chromosome");
            }Object max=options.get("chisq_max");require(max==null||GenomicsemContracts.finite(max)>0,"chisq_max must be null(original adaptive threshold) or positive");
        }
        if(operation.equals("SUMSTATS")){
            var ols=booleanArray(options,"OLS",n);var lin=booleanArray(options,"linprob",n);var se=booleanArray(options,"se_logit",n);var betas=list(options.get("betas"));require(betas.size()==n,"betas length must equal trait count");for(Object v:betas)require(v instanceof Boolean||v instanceof String&&!((String)v).isBlank(),"betas entries must be literal logical values or original character markers");
            for(int i=0;i<n;i++)require((Boolean.TRUE.equals(ols.get(i))?1:0)+(Boolean.TRUE.equals(lin.get(i))?1:0)+(Boolean.TRUE.equals(se.get(i))?1:0)<=1,"OLS,linprob and se_logit must not request conflicting effect transformations");
            for(String k:List.of("keep_indel","remove_ambiguous","direction_filter"))bool(options,k);
        }
        if(operation.equals("READ_FUSION")){numericArray(options,"N",n,false,Double.POSITIVE_INFINITY);booleanArray(options,"binary",n);bool(options,"permutation");}
        if(operation.equals("S_LDSC"))bool(options,"exclude_continuous");
        if(operation.equals("ENRICH")){
            Object annotations=options.get("annotations");if(!"ALL".equals(annotations)){var a=list(annotations);require(!a.isEmpty()&&a.size()<=10000,"Require ALL or an explicit ordered annotation list including the original baseline first");Set<String> seen=new HashSet<>();for(Object v:a){String label=text(v,"annotation label");literal(label,"annotation label");require(seen.add(label),"Duplicated selected annotation");}}
            pathsArray(options,"parameters",false);pathsArray(options,"fixed_parameters",true);choice(options,"fix",Set.of("regressions","covariances","variances"));for(String k:List.of("std_lv","remove_flanking","tau","include_baseline"))bool(options,k);Object tol=options.get("tolerance");require(tol==null||GenomicsemContracts.finite(tol)>0,"Enrichment tolerance must be null or positive");choice(options,"smoothing_policy",Set.of("FAIL","ALLOW_UPSTREAM"));
        }
        var defaults=map("maximum_input_bytes",107374182400L,"maximum_member_bytes",34359738368L,"maximum_output_bytes",214748364800L,"maximum_files",10000L,"timeout_seconds",86400L,"maximum_rows",10000000L,"maximum_values",200000000L,"maximum_line_characters",16777216L,"maximum_result_rows",100000000L,"maximum_uncompressed_bytes",214748364800L);
        var supplied=raw.containsKey("limits")?object(raw.get("limits")):map();keys(supplied,defaults.keySet());var limits=map();for(var e:defaults.entrySet())limits.put(e.getKey(),integer(supplied.getOrDefault(e.getKey(),e.getValue()),1,Long.parseLong(e.getValue().toString())));var result=new LinkedHashMap<>(raw);result.put("limits",limits);return result;
    }
    private static Set<String> union(Set<String>a,Set<String>b){var c=new HashSet<>(a);c.addAll(b);return c;}
    static boolean bool(Map<String,Object>m,String key)throws IOException{return GenomicsemContracts.bool(m,key);}
    static String relative(String value)throws IOException {
        literal(value,"bundle-relative path");require(value.length()<=4096&&!value.contains("\\")&&!value.contains(":")&&!value.matches(".*[\\*?\\[\\]{}].*"),"Use literal slash-separated bundle-relative paths without glob syntax");Path p=Path.of(value);require(!p.isAbsolute()&&!p.normalize().startsWith("..")&&p.normalize().equals(p)&&!value.startsWith("/"),"Unsafe bundle-relative path");return value;
    }
    private static void fraction(Map<String,Object>m,String key,boolean inclusive)throws IOException {double v=GenomicsemContracts.finite(m.get(key));require(v>=0&&(inclusive?v<=1:v<1),"Require probability option "+key);}
    private static List<?> numericArray(Map<String,Object>m,String key,int n,boolean nullable,double max)throws IOException {var a=list(m.get(key));require(a.size()==n,"Require one "+key+" per ordered trait");for(Object v:a)if(v==null)require(nullable,"Missing numeric option: "+key);else{double d=GenomicsemContracts.finite(v);require(d>0&&d<max,"Require positive "+key+(Double.isFinite(max)?" below"+max:""));}return a;}
    private static List<?> booleanArray(Map<String,Object>m,String key,int n)throws IOException {var a=list(m.get(key));require(a.size()==n&&a.stream().allMatch(v->v instanceof Boolean),"Require one explicit "+key+" boolean per ordered trait");return a;}
    private static void pathsArray(Map<String,Object>m,String key,boolean empty)throws IOException {var a=list(m.get(key));require((empty||!a.isEmpty())&&a.size()<=256,"Invalid requested parameter list");Set<String> seen=new HashSet<>();for(Object v:a){String p=text(v,key).replaceAll("\\s+","");require(p.matches("[A-Za-z][A-Za-z0-9_]*(?:~~|=~|~)[A-Za-z][A-Za-z0-9_]*")&&seen.add(p),"Require unique literal lavaan parameter paths");}}
    static Map<String,Object> canonical(Map<String,Object>request){var r=new LinkedHashMap<>(request);r.put("bundle","inputs/bundle");r.put("out",".");r.put("runtime_home","external-runtime");r.put("runtime_addons",request.get("runtime_addons")==null?null:"external-addons");return r;}
}
