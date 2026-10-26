/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit development/held-out boundaries for molecular and imported genetic scores. */
public final class StudyRiskScorePolicy {
    private StudyRiskScorePolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input) {
        var p=new LinkedHashMap<String,Object>(input);
        var allowed=Set.of("enabled","mode","file","score_id","id_column","partition_column","group_columns","independent_rows","resampling_group","outcome","family","features","covariates","categorical_covariates","lambdas","alpha","refit","missing","weights","model","alleles","score_column","prediction_column","baseline_column","training_ids","external_independence","provenance","preprocessing_provenance","related_phenotypes","subgroups","bootstraps","seed","adjustment","minimum_samples","max_features","max_samples","max_work","prediction_scale","binary_baseline_lambda","binary_maximum_iterations","binary_tolerance","nested_validation","genotypes");
        for(String key:p.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown risk_score field: "+key);
        p.putIfAbsent("enabled",true);p.putIfAbsent("mode","train");p.putIfAbsent("score_id","score");p.putIfAbsent("id_column","sample_id");p.putIfAbsent("partition_column","partition");p.putIfAbsent("family","quantitative");
        p.putIfAbsent("group_columns",List.of());p.putIfAbsent("independent_rows",false);p.putIfAbsent("features",List.of());p.putIfAbsent("covariates",List.of());p.putIfAbsent("categorical_covariates",List.of());p.putIfAbsent("lambdas",List.of(1.0));p.putIfAbsent("alpha",0.0);p.putIfAbsent("refit",true);p.putIfAbsent("missing","error");
        p.putIfAbsent("related_phenotypes",List.of());p.putIfAbsent("subgroups",List.of());p.putIfAbsent("bootstraps",200);p.putIfAbsent("seed",1);p.putIfAbsent("adjustment","BH");p.putIfAbsent("minimum_samples",10);p.putIfAbsent("max_features",2000);p.putIfAbsent("max_samples",100000);p.putIfAbsent("max_work",250000000);p.putIfAbsent("prediction_scale","binary".equals(p.get("family"))&&Set.of("train","model").contains(p.get("mode"))?"probability":"raw");
        if("binary".equals(p.get("family"))&&"train".equals(p.get("mode"))){p.putIfAbsent("binary_baseline_lambda",1.0);p.putIfAbsent("binary_maximum_iterations",10000);p.putIfAbsent("binary_tolerance",1e-8);}
        for(String key:List.of("enabled","independent_rows","refit"))if(!(p.get(key) instanceof Boolean))throw new IllegalArgumentException("risk_score."+key+" must be boolean");
        choice(p,"mode",Set.of("train","weights","scores","model"));choice(p,"family",Set.of("quantitative","binary","survival"));choice(p,"missing",Set.of("error","training_mean"));choice(p,"adjustment",Set.of("BH","BY","NONE"));choice(p,"prediction_scale",Set.of("raw","probability"));
        for(String key:List.of("file","outcome","score_id","id_column","partition_column","weights","model","alleles","score_column","prediction_column","baseline_column","training_ids","external_independence","resampling_group","preprocessing_provenance"))if(p.containsKey(key)&&(!(p.get(key) instanceof String s)||s.isBlank()))throw new IllegalArgumentException("risk_score."+key+" must be nonblank text");
        for(String key:List.of("features","covariates","categorical_covariates","group_columns","subgroups"))p.put(key,names(p.get(key),key));
        if(list(p,"features").size()>2000||list(p,"covariates").size()>100||list(p,"group_columns").size()>20||list(p,"subgroups").size()>20)throw new IllegalArgumentException("Score feature/covariate/group/subgroup counts exceed storage/work bounds");
        if(!list(p,"covariates").containsAll(list(p,"categorical_covariates")))throw new IllegalArgumentException("categorical_covariates must also be covariates");
        if(p.containsKey("provenance"))StudyIO.map(p.get("provenance"),"risk_score.provenance");
        double previous=Double.POSITIVE_INFINITY;var lambdas=new ArrayList<Double>();for(Object raw:StudyIO.list(p.get("lambdas"),"lambdas")){if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>=previous)throw new IllegalArgumentException("lambdas must be finite nonnegative strictly descending");previous=n.doubleValue();lambdas.add(previous);}if(lambdas.isEmpty()||lambdas.size()>100)throw new IllegalArgumentException("Require 1..100 lambdas");p.put("lambdas",lambdas);
        if(!(p.get("alpha") instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>1)throw new IllegalArgumentException("alpha must be in [0,1]");
        if(p.keySet().stream().anyMatch(key->key.startsWith("binary_"))&&!("binary".equals(p.get("family"))&&"train".equals(p.get("mode"))))throw new IllegalArgumentException("Binary optimizer controls require family: binary and mode: train");
        if("binary".equals(p.get("family"))&&"train".equals(p.get("mode"))){integer(p,"binary_maximum_iterations",1,100000);new org.jlinalg.xwas.BinaryPredictionScores.Options(((Number)p.get("binary_maximum_iterations")).intValue(),numeric(p,"binary_tolerance"),numeric(p,"binary_baseline_lambda"),(long)numeric(p,"max_work"));if(lambdas.stream().anyMatch(value->value<=0))throw new IllegalArgumentException("Binary training requires positive feature penalties");}
        for(String key:List.of("bootstraps","seed"))integer(p,key,0,key.equals("bootstraps")?2000:Integer.MAX_VALUE);integer(p,"minimum_samples",4,100000);integer(p,"max_features",1,2000);integer(p,"max_samples",4,1000000);integer(p,"max_work",1,2000000000);
        var related=new ArrayList<Object>();var unique=new java.util.HashSet<String>();
        for(Object raw:StudyIO.list(p.get("related_phenotypes"),"related_phenotypes")){var r=StudyIO.map(raw,"related phenotype");for(String key:r.keySet())if(!Set.of("column","family","label").contains(key))throw new IllegalArgumentException("Unknown related phenotype field: "+key);if(!(r.get("column") instanceof String s)||s.isBlank()||!unique.add(s))throw new IllegalArgumentException("Related phenotype columns must be unique names");r.putIfAbsent("family","quantitative");choice(r,"family",Set.of("quantitative","binary"));related.add(r);}if(related.size()>1000)throw new IllegalArgumentException("At most 1000 registered related phenotypes");p.put("related_phenotypes",related);
        if(p.containsKey("genotypes")){
            if(!"weights".equals(p.get("mode"))||!p.containsKey("file")||!p.containsKey("weights")||p.containsKey("alleles"))throw new IllegalArgumentException("risk_score.genotypes requires mode: weights, explicit participant file and weights; allele metadata come from the genotype source");
            p.put("genotypes",StudyGeneticScorePolicy.resolve(StudyIO.map(p.get("genotypes"),"risk_score.genotypes")));
        }
        if(p.containsKey("nested_validation")){
            if(!"train".equals(p.get("mode"))||!Set.of("quantitative","binary").contains(p.get("family"))||!Boolean.TRUE.equals(p.get("refit")))throw new IllegalArgumentException("nested_validation requires native quantitative/binary mode: train and refit: true");
            var cv=StudyIO.map(p.get("nested_validation"),"risk_score.nested_validation");
            if(!Set.of("outer_folds","inner_folds","seed","maximum_fits","maximum_fold_rows","outer_fold_column","inner_fold_column").containsAll(cv.keySet()))throw new IllegalArgumentException("Unknown nested_validation setting");
            cv.putIfAbsent("outer_folds",5);cv.putIfAbsent("inner_folds",5);cv.putIfAbsent("seed",p.get("seed"));cv.putIfAbsent("maximum_fits",10000);cv.putIfAbsent("maximum_fold_rows",2000000);
            integer(cv,"outer_folds",2,20);integer(cv,"inner_folds",2,20);integer(cv,"seed",0,Integer.MAX_VALUE);integer(cv,"maximum_fits",1,100000);integer(cv,"maximum_fold_rows",1,20000000);
            for(String key:List.of("outer_fold_column","inner_fold_column"))if(cv.containsKey(key)){
                if(!(cv.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("Nested fold columns must be nonblank names");
                String name=cv.get(key).toString();if(list(p,"features").contains(name)||list(p,"covariates").contains(name)||name.equals(p.get("outcome"))||name.equals(p.get("id_column"))||name.equals(p.get("partition_column")))throw new IllegalArgumentException("Nested administrative fold columns cannot be predictors, covariates, outcome, ID or partition columns");
            }
            if(cv.containsKey("outer_fold_column")&&cv.get("outer_fold_column").equals(cv.get("inner_fold_column")))throw new IllegalArgumentException("Outer and inner fold columns must differ");
            p.put("nested_validation",cv);
        }
        return p;
    }
    private static List<String> names(Object value,String key){var result=new ArrayList<String>();for(Object raw:StudyIO.list(value,key)){if(!(raw instanceof String s)||s.isBlank()||result.contains(s))throw new IllegalArgumentException(key+" requires unique nonblank names");result.add(s);}return result;}
    private static double numeric(Map<String,Object> p,String key){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException(key+" must be finite numeric");return n.doubleValue();}
    public static List<String> list(Map<String,Object> p,String key){return StudyIO.list(p.get(key),key).stream().map(String::valueOf).toList();}
    private static void choice(Map<String,Object> p,String key,Set<String> choices){if(!choices.contains(p.get(key)))throw new IllegalArgumentException("Unsupported risk_score."+key);}
    private static void integer(Map<String,Object> p,String key,int min,int max){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw new IllegalArgumentException("risk_score."+key+" outside integer bounds");}
}
