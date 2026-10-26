/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.jlinalg.ols.Ols;

/** Training-only Gaussian scores, explicit tuning partitions and conditional held-out metrics. */
public final class HeldOutScores {
    private HeldOutScores() { }
    public record Frozen(PredictionScores.Model model,double[] featureMeans,double[] baselineWeights,int developmentRows,boolean meanImpute) {
        public Frozen {featureMeans=featureMeans.clone();baselineWeights=baselineWeights.clone();}
        @Override public double[] featureMeans(){return featureMeans.clone();}
        @Override public double[] baselineWeights(){return baselineWeights.clone();}
        public double[] score(double[][] x){if(!meanImpute)for(double[] row:x)for(double value:row)if(Double.isNaN(value))throw new IllegalArgumentException("Missing application predictor under the frozen error policy");return model.predict(impute(x,featureMeans));}
        public double[] baseline(double[][] covariates){var result=new double[covariates.length];for(int i=0;i<result.length;i++){if(covariates[i].length!=baselineWeights.length)throw new IllegalArgumentException("Baseline design width changed");for(int j=0;j<baselineWeights.length;j++)result[i]+=covariates[i][j]*baselineWeights[j];if(!Double.isFinite(result[i]))throw new IllegalArgumentException("Nonfinite frozen baseline");}return result;}
    }
    public record Fit(Frozen frozen,List<Double> tuningMse,double selectedLambda) {public Fit{tuningMse=List.copyOf(tuningMse);}}
    public static Fit train(double[] y,double[][] x,double[][] covariates,int[] training,int[] tuning,double[] lambdas,double alpha,boolean refit,boolean meanImpute) {
        if(training.length<4||lambdas.length<1||lambdas.length>1&&tuning.length<4)throw new IllegalArgumentException("Require training and an independent tuning partition for a lambda search");
        var seen=new java.util.HashSet<Integer>();for(int row:training)if(row<0||row>=y.length||!seen.add(row))throw new IllegalArgumentException("Invalid/duplicate training row");for(int row:tuning)if(row<0||row>=y.length||!seen.add(row))throw new IllegalArgumentException("Training/tuning overlap");
        var errors=new ArrayList<Double>();Frozen best=null;double minimum=Double.POSITIVE_INFINITY,selected=Double.NaN;
        for(double lambda:lambdas){Frozen fit=fit(y,x,covariates,training,lambda,alpha,meanImpute);double error=0;if(tuning.length>0){var scores=fit.score(rows(x,tuning));var baseline=fit.baseline(rows(covariates,tuning));for(int i=0;i<tuning.length;i++)error+=Math.pow(y[tuning[i]]-baseline[i]-scores[i],2);error/=tuning.length;}errors.add(error);if(best==null||error<minimum){minimum=error;best=fit;selected=lambda;}}
        if(refit&&tuning.length>0){int[] development=Arrays.copyOf(training,training.length+tuning.length);System.arraycopy(tuning,0,development,training.length,tuning.length);best=fit(y,x,covariates,development,selected,alpha,meanImpute);}
        return new Fit(best,errors,selected);
    }
    private static Frozen fit(double[] y,double[][] x,double[][] covariates,int[] train,double lambda,double alpha,boolean meanImpute) {
        double[][] features=rows(x,train),design=rows(covariates,train);double[] response=values(y,train),means=new double[x[0].length];
        for(int j=0;j<means.length;j++){int count=0;for(double[] row:features){if(Double.isFinite(row[j])){means[j]+=row[j];count++;}else if(!meanImpute)throw new IllegalArgumentException("Missing training predictor under missing:error");}if(count==0)throw new IllegalArgumentException("A feature is entirely missing in the actual training partition");means[j]/=count;}
        var baseline=Ols.fit(response,design,org.jlinalg.ols.OlsOptions.defaults(),org.jlinalg.compute.BackendPolicy.CPU);double[] residual=baseline.residuals();
        var model=PredictionScores.train(residual,impute(features,means),new double[]{lambda},alpha,2,1);
        return new Frozen(model,means,baseline.coefficients(),train.length,meanImpute);
    }
    private static double[][] impute(double[][] x,double[] means){double[][] result=new double[x.length][means.length];for(int i=0;i<x.length;i++){if(x[i].length!=means.length)throw new IllegalArgumentException("Feature design width changed");for(int j=0;j<means.length;j++){if(Double.isInfinite(x[i][j]))throw new IllegalArgumentException("Infinite predictor");result[i][j]=Double.isNaN(x[i][j])?means[j]:x[i][j];}}return result;}
    public static double[][] rows(double[][] x,int[] indices){double[][] result=new double[indices.length][];for(int i=0;i<indices.length;i++)result[i]=x[indices[i]].clone();return result;}
    public static double[] values(double[] x,int[] indices){double[] result=new double[indices.length];for(int i=0;i<indices.length;i++)result[i]=x[indices[i]];return result;}

    /** Validate each declared grouping independently, including sample identities, before any fit. */
    public static void partitions(String[] ids,String[] roles,Map<String,String[]> grouping) {
        if(ids.length!=roles.length)throw new IllegalArgumentException("Unaligned partition columns");var unique=new java.util.HashSet<String>();
        for(int i=0;i<ids.length;i++){if(ids[i]==null||ids[i].isBlank()||!unique.add(ids[i]))throw new IllegalArgumentException("Sample identities must be unique and nonblank");if(!List.of("training","tuning","evaluation","excluded").contains(roles[i]))throw new IllegalArgumentException("Unknown participant partition: "+roles[i]);}
        for(var column:grouping.entrySet()){if(column.getValue().length!=ids.length)throw new IllegalArgumentException("Unaligned grouping column");var membership=new LinkedHashMap<String,String>();for(int i=0;i<ids.length;i++){String group=column.getValue()[i];if(group==null||group.isBlank())throw new IllegalArgumentException("Missing group identifier: "+column.getKey());if(roles[i].equals("excluded"))continue;String previous=membership.putIfAbsent(group,roles[i]);if(previous!=null&&!previous.equals(roles[i]))throw new IllegalArgumentException("Group crosses development/tuning/evaluation boundaries: "+column.getKey());}}
    }
    /** Raw scores provide AUC only; probability metrics require explicitly supplied probabilities. */
    public static Map<String,Double> metrics(double[] y,double[] score,double[] prediction,double[] baseline,boolean binary,boolean probability) {
        if(y.length<4||score.length!=y.length||prediction.length!=y.length||baseline.length!=y.length)throw new IllegalArgumentException("Require at least four aligned evaluation records");
        var result=new LinkedHashMap<String,Double>();double error=0,base=0,log=0,baseLog=0;int cases=0;
        for(int i=0;i<y.length;i++){if(!Double.isFinite(y[i])||!Double.isFinite(score[i]))throw new IllegalArgumentException("Nonfinite evaluation record");if(binary){if(y[i]!=0&&y[i]!=1)throw new IllegalArgumentException("Binary outcomes must be 0/1");cases+=(int)y[i];}if(!binary||probability){if(!Double.isFinite(prediction[i])||!Double.isFinite(baseline[i]))throw new IllegalArgumentException("Need frozen finite prediction and baseline");error+=Math.pow(y[i]-prediction[i],2);base+=Math.pow(y[i]-baseline[i],2);if(binary){if(prediction[i]<0||prediction[i]>1||baseline[i]<0||baseline[i]>1)throw new IllegalArgumentException("Probability predictions/baselines must lie in [0,1]");double p=Math.max(1e-15,Math.min(1-1e-15,prediction[i])),b=Math.max(1e-15,Math.min(1-1e-15,baseline[i]));log-=y[i]*Math.log(p)+(1-y[i])*Math.log1p(-p);baseLog-=y[i]*Math.log(b)+(1-y[i])*Math.log1p(-b);}}}
        if(binary){if(cases==0||cases==y.length)throw new IllegalArgumentException("AUC requires both cases and controls");result.put("auc",auc(y,probability?prediction:score));if(probability){result.put("score_auc",auc(y,score));result.put("baseline_auc",auc(y,baseline));result.put("brier",error/y.length);result.put("baseline_brier",base/y.length);result.put("log_loss",log/y.length);result.put("baseline_log_loss",baseLog/y.length);}}
        else {result.put("rmse",Math.sqrt(error/y.length));result.put("baseline_rmse",Math.sqrt(base/y.length));if(base>0)result.put("incremental_predictive_r2",1-error/base);}
        if(!binary||probability){double[] predictor=prediction.clone();if(binary)for(int i=0;i<predictor.length;i++){double p=Math.max(1e-15,Math.min(1-1e-15,predictor[i]));predictor[i]=Math.log(p)-Math.log1p(-p);}double[][] design=new double[y.length][2];for(int i=0;i<y.length;i++){design[i][0]=1;design[i][1]=predictor[i];}try{double[] coefficients;if(binary){if(separatedCalibration(y,predictor))throw new IllegalArgumentException("Complete/quasi separation has no finite logistic calibration slope");var fitted=org.jlinalg.glm.Glm.fit(y,design,org.jlinalg.glm.GlmFamilies.binomial(),null,null,org.jlinalg.glm.GlmOptions.defaults(),org.jlinalg.compute.BackendPolicy.CPU);if(!fitted.converged())throw new IllegalArgumentException("Calibration did not converge");coefficients=fitted.coefficients();}else coefficients=Ols.fit(y,design,org.jlinalg.ols.OlsOptions.defaults(),org.jlinalg.compute.BackendPolicy.CPU).coefficients();if(Double.isFinite(coefficients[0])&&Double.isFinite(coefficients[1])){result.put("calibration_intercept",coefficients[0]);result.put("calibration_slope",coefficients[1]);}}catch(IllegalArgumentException unestimable){/* Constant/separated calibration is unavailable; predictions stay frozen. */}}
        return result;
    }
    private static double auc(double[] y,double[] score){Integer[] order=new Integer[y.length];int cases=0;for(int i=0;i<y.length;i++){order[i]=i;cases+=(int)y[i];}Arrays.sort(order,java.util.Comparator.comparingDouble(i->score[i]));double ranks=0;for(int begin=0;begin<order.length;){int end=begin+1;while(end<order.length&&score[order[begin]]==score[order[end]])end++;double rank=(begin+1.0+end)/2;for(int i=begin;i<end;i++)if(y[order[i]]==1)ranks+=rank;begin=end;}return (ranks-cases*(cases+1.0)/2)/(cases*(double)(y.length-cases));}
    private static boolean separatedCalibration(double[] y,double[] predictor){double minCase=Double.POSITIVE_INFINITY,maxCase=Double.NEGATIVE_INFINITY,minControl=Double.POSITIVE_INFINITY,maxControl=Double.NEGATIVE_INFINITY;for(int i=0;i<y.length;i++){if(y[i]==1){minCase=Math.min(minCase,predictor[i]);maxCase=Math.max(maxCase,predictor[i]);}else{minControl=Math.min(minControl,predictor[i]);maxControl=Math.max(maxControl,predictor[i]);}}return minCase>=maxControl||maxCase<=minControl;}
    /** Cluster percentile bootstrap conditions on the frozen score; failed resamples remain counted. */
    public static Map<String,double[]> intervals(double[] y,double[] score,double[] prediction,double[] baseline,String[] groups,boolean binary,boolean probability,int replicates,long seed) {
        if(groups.length!=y.length)throw new IllegalArgumentException("Unaligned resampling units");var byGroup=new LinkedHashMap<String,List<Integer>>();for(int i=0;i<groups.length;i++){if(groups[i]==null||groups[i].isBlank())throw new IllegalArgumentException("Missing resampling unit");byGroup.computeIfAbsent(groups[i],key->new ArrayList<>()).add(i);}if(byGroup.size()<2)throw new IllegalArgumentException("Need at least two independent resampling units");
        var units=new ArrayList<>(byGroup.values());var draws=new LinkedHashMap<String,List<Double>>();Random random=new Random(seed);
        for(int iteration=0;iteration<replicates;iteration++){var indices=new ArrayList<Integer>();for(int i=0;i<units.size();i++)indices.addAll(units.get(random.nextInt(units.size())));int[] rows=indices.stream().mapToInt(Integer::intValue).toArray();try{for(var entry:metrics(values(y,rows),values(score,rows),values(prediction,rows),values(baseline,rows),binary,probability).entrySet())if(Double.isFinite(entry.getValue()))draws.computeIfAbsent(entry.getKey(),key->new ArrayList<>()).add(entry.getValue());}catch(IllegalArgumentException invalid){/* Single-class/insufficient resamples are retained in the attempted count. */}}
        var result=new LinkedHashMap<String,double[]>();for(var entry:draws.entrySet()){double[] sorted=entry.getValue().stream().mapToDouble(Double::doubleValue).sorted().toArray();boolean enough=sorted.length>=Math.max(20,(int)Math.ceil(replicates*.8));result.put(entry.getKey(),new double[]{enough?quantile(sorted,.025):Double.NaN,enough?quantile(sorted,.975):Double.NaN,sorted.length,replicates});}return result;
    }
    private static double quantile(double[] sorted,double p){double at=p*(sorted.length-1);int lo=(int)at;return sorted[lo]+(sorted[Math.min(lo+1,sorted.length-1)]-sorted[lo])*(at-lo);}
}
