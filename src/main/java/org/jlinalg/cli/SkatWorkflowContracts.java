/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed typed settings for original autosomal SKAT matrix/null-model calls. */
final class SkatWorkflowContracts {
    private SkatWorkflowContracts(){ }
    static final String PROFILE="2.2.5-r4.6.1";
    static final List<String> INPUTS=List.of("phenotype","covariates","genotypes","sets","weights");
    static final Set<String> METHODS=Set.of("liu","davies","liu.mod","optimal","optimal.moment","optimal.mod","adjust","optimal.adj","optimal.moment.adj","SKAT","SKATO","Burden","SKATO.m","davies.M","optimal.adj.M");
    static final Set<String> KERNELS=Set.of("linear","linear.weighted","IBS","IBS.weighted","quadratic","2wayIX");
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","engine","profile","operation","out","rscript","r_libraries","phenotype","covariates","genotypes","sets","weights","null_model","test","seed","admission","limits"));
        require(integer(raw,"schema_version",0,1,1)==1,"Require schema_version1");require(text(raw,"mode").equals("RUN"),"Require RUN scientific request");require(text(raw,"engine").equals("original"),"Require explicit original engine");
        String profile=text(raw,"profile");if(!PROFILE.equals(profile))throw new Unavailable("No qualified standalone SKAT runtime profile: "+profile+";supply exact permitted "+PROFILE+" or retain the skipped analysis. No adjacent edition is substituted.");
        require(text(raw,"operation").equals("ASSOCIATE"),"Require ASSOCIATE");
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("mode","RUN");result.put("engine","original");result.put("profile",PROFILE);result.put("operation","ASSOCIATE");
        for(String key:List.of("out","rscript","phenotype","genotypes","sets"))result.put(key,pathText(text(raw,key)));
        for(String key:List.of("covariates","weights"))if(raw.containsKey(key))result.put(key,pathText(text(raw,key)));
        var libraries=new ArrayList<String>();for(String value:strings(raw.get("r_libraries"),"r_libraries",1,16))libraries.add(pathText(value));result.put("r_libraries",List.copyOf(libraries));
        require(raw.containsKey("seed"),"An explicit seed is required");result.put("seed",integer(raw,"seed",0,1,Integer.MAX_VALUE));
        var originalNull=object(raw.get("null_model"),"null_model");keys(originalNull,Set.of("function","out_type","intercept","missing_model","rank_policy","adjustment","n_resampling","resampling","kurtosis_adjustment","kurtosis_resamples"));
        var nullModel=new LinkedHashMap<String,Object>();String function=choice(originalNull,"function","SKAT_Null_Model",Set.of("SKAT_Null_Model","SKAT_Null_Model_MomentAdjust"));String outType=choice(originalNull,"out_type","",Set.of("C","D"));
        nullModel.put("function",function);nullModel.put("out_type",outType);nullModel.put("intercept",bool(originalNull,"intercept",true));nullModel.put("missing_model",choice(originalNull,"missing_model","reject",Set.of("reject","complete-cases")));nullModel.put("rank_policy",choice(originalNull,"rank_policy","require-full-rank",Set.of("require-full-rank","source-svd")));nullModel.put("n_resampling",integer(originalNull,"n_resampling",0,0,1000000));nullModel.put("resampling",choice(originalNull,"resampling","bootstrap",Set.of("bootstrap","bootstrap.fast","permutation","perturbation")));
        if(function.equals("SKAT_Null_Model")) {
            require(!originalNull.containsKey("kurtosis_adjustment")&&!originalNull.containsKey("kurtosis_resamples"),"Standard SKAT_Null_Model does not expose kurtosis controls;choose its source default or explicit MomentAdjust");nullModel.put("adjustment",bool(originalNull,"adjustment",true));
        }else {
            require(outType.equals("D"),"MomentAdjust is an explicitly binary null model");require(!originalNull.containsKey("adjustment"),"MomentAdjust uses kurtosis_adjustment,not the standard Adjustment argument");nullModel.put("kurtosis_adjustment",bool(originalNull,"kurtosis_adjustment",true));nullModel.put("kurtosis_resamples",integer(originalNull,"kurtosis_resamples",10000,1,1000000));
        }
        result.put("null_model",nullModel);
        var originalTest=object(raw.getOrDefault("test",Map.of()),"test");keys(originalTest,Set.of("method","kernel","weights_beta","impute","r_corr","check_genotype","dosage","missing_cutoff","max_maf","estimate_maf"));
        var test=new LinkedHashMap<String,Object>();test.put("method",choice(originalTest,"method","davies",METHODS));test.put("kernel",choice(originalTest,"kernel","linear.weighted",KERNELS));
        require(!raw.containsKey("weights")||!originalTest.containsKey("weights_beta"),"Supplied weights override beta weights in source;omit the unused weights_beta field");test.put("weights_beta",vector(originalTest.getOrDefault("weights_beta",List.of(1,25)),"weights_beta",2,2,0,Double.MAX_VALUE,false));
        test.put("impute",choice(originalTest,"impute","fixed",Set.of("fixed","bestguess","random")));test.put("r_corr",vector(originalTest.getOrDefault("r_corr",List.of(0)),"r_corr",1,101,0,1,true));test.put("check_genotype",bool(originalTest,"check_genotype",true));test.put("dosage",bool(originalTest,"dosage",false));test.put("missing_cutoff",real(originalTest,"missing_cutoff",.15,0,1,true));test.put("max_maf",real(originalTest,"max_maf",1,0,1,true));test.put("estimate_maf",integer(originalTest,"estimate_maf",1,1,2));
        if(Boolean.FALSE.equals(test.get("check_genotype"))&&Boolean.FALSE.equals(test.get("dosage"))&&test.get("kernel").toString().endsWith("weighted"))require(raw.containsKey("weights"),"Non-SNP weighted-kernel input requires explicit supplied weights");
        if(test.get("kernel").toString().startsWith("IBS"))require(Boolean.TRUE.equals(test.get("check_genotype"))&&Boolean.FALSE.equals(test.get("dosage")),"IBS kernels require declared discrete genotypes;the adapter does not silently integer-coerce dosages/features");
        result.put("test",test);
        var originalAdmission=object(raw.getOrDefault("admission",Map.of()),"admission");keys(originalAdmission,Set.of("allow_reported_fallback","allow_test_substitution","allow_adjustment_omission","allow_empty_sets","zero_policy"));
        var admission=new LinkedHashMap<String,Object>();for(String key:List.of("allow_reported_fallback","allow_test_substitution","allow_adjustment_omission","allow_empty_sets"))admission.put(key,bool(originalAdmission,key,false));admission.put("zero_policy",choice(originalAdmission,"zero_policy","refuse",Set.of("refuse","retain-source-bound")));result.put("admission",admission);
        var originalLimits=object(raw.getOrDefault("limits",Map.of()),"limits");keys(originalLimits,Set.of("timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_member_bytes","maximum_files","maximum_samples","maximum_variants","maximum_covariates","maximum_sets","maximum_memberships","maximum_cells","maximum_set_cells","maximum_resampling_cells","maximum_kernel_cells"));
        var limits=new LinkedHashMap<String,Object>();limit(limits,originalLimits,"timeout_seconds",600,1,86400);limit(limits,originalLimits,"maximum_input_bytes",134217728,1024,8589934592L);limit(limits,originalLimits,"maximum_output_bytes",536870912,1024,68719476736L);limit(limits,originalLimits,"maximum_member_bytes",268435456,1024,17179869184L);limit(limits,originalLimits,"maximum_files",20000,32,1000000);limit(limits,originalLimits,"maximum_samples",20000,3,1000000);limit(limits,originalLimits,"maximum_variants",200000,1,2000000);limit(limits,originalLimits,"maximum_covariates",256,0,4096);limit(limits,originalLimits,"maximum_sets",1000,1,100000);limit(limits,originalLimits,"maximum_memberships",1000000,1,10000000);limit(limits,originalLimits,"maximum_cells",10000000,1,1000000000L);limit(limits,originalLimits,"maximum_set_cells",20000000,1,2000000000L);limit(limits,originalLimits,"maximum_resampling_cells",20000000,1,2000000000L);limit(limits,originalLimits,"maximum_kernel_cells",25000000,1,2000000000L);result.put("limits",limits);
        return result;
    }
    static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
    private static void limit(Map<String,Object> result,Map<String,Object> source,String key,long fallback,long low,long high)throws IOException {result.put(key,integer(source,key,fallback,low,high));}
    static List<Double> vector(Object raw,String label,int low,int high,double minimum,double maximum,boolean inclusive)throws IOException {
        List<?> values=list(raw,label);require(values.size()>=low&&values.size()<=high,"Invalid vector length: "+label);var result=new ArrayList<Double>();
        for(Object value:values){require(value instanceof Number,"Require numeric vector: "+label);double x=((Number)value).doubleValue();require(Double.isFinite(x)&&x<=maximum&&(inclusive?x>=minimum:x>minimum),"Invalid finite vector value: "+label);result.add(x);}return List.copyOf(result);
    }
    static String pathText(String value)throws IOException {require(value.length()<=32768&&value.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Paths must be literal text without control/unpaired-surrogate characters");return value;}
    static String identifier(String value)throws IOException {require(!value.isBlank()&&value.length()<=512&&value.codePoints().noneMatch(c->c<32||c==127||c>=0xd800&&c<=0xdfff),"Require nonblank literal identifiers,at most512 UTF-16 units,without controls or unpaired surrogates");return value;}
    static Path resolvePath(Path parent,Map<String,Object> request,String key)throws IOException {return resolve(parent,text(request,key));}
}
