/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Training/tuning-only binomial elastic-net prediction with frozen preprocessing.
 * The objective is mean Bernoulli negative log likelihood plus elastic-net
 * penalties on standardized molecular features and a separately registered
 * positive ridge penalty on standardized non-intercept covariates. Intercepts
 * are unpenalized. The covariate-only comparator has its own fitted intercept
 * and the same nuisance ridge penalty. No coefficient inference is supplied. */
public final class BinaryPredictionScores {
    private BinaryPredictionScores() { }
    public record Options(int maximumIterations,double tolerance,double baselineLambda,long maximumWork) {
        public Options {if(maximumIterations<1||!Double.isFinite(tolerance)||tolerance<=0||tolerance>=.01||!Double.isFinite(baselineLambda)||baselineLambda<=0||maximumWork<1)throw new IllegalArgumentException("Invalid binary score optimization controls");}
        public static Options defaults(){return new Options(10000,1e-8,1,250000000);}
    }
    public record Diagnostics(int iterations,double maximumKktResidual,double objective) { }
    public record Frozen(double[] featureWeights,double[] featureMeans,double[] featureScales,double scoreIntercept,
            double[] covariateWeights,double[] baselineWeights,double lambda,double alpha,double baselineLambda,
            int developmentRows,boolean meanImpute,Diagnostics fullFit,Diagnostics baselineFit) {
        public Frozen {featureWeights=featureWeights.clone();featureMeans=featureMeans.clone();featureScales=featureScales.clone();covariateWeights=covariateWeights.clone();baselineWeights=baselineWeights.clone();
            if(featureWeights.length!=featureMeans.length||featureWeights.length!=featureScales.length||covariateWeights.length!=baselineWeights.length||covariateWeights.length<1||!Double.isFinite(scoreIntercept)||!Double.isFinite(lambda)||lambda<=0||!Double.isFinite(alpha)||alpha<0||alpha>1||!Double.isFinite(baselineLambda)||baselineLambda<=0||developmentRows<4)throw new IllegalArgumentException("Invalid frozen binary model dimensions/controls");
            for(double[] values:new double[][]{featureWeights,featureMeans,featureScales,covariateWeights,baselineWeights})for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite frozen binary model");for(double v:featureScales)if(v<=0)throw new IllegalArgumentException("Invalid frozen feature scale");}
        @Override public double[] featureWeights(){return featureWeights.clone();}
        @Override public double[] featureMeans(){return featureMeans.clone();}
        @Override public double[] featureScales(){return featureScales.clone();}
        @Override public double[] covariateWeights(){return covariateWeights.clone();}
        @Override public double[] baselineWeights(){return baselineWeights.clone();}
        public double[] score(double[][] x){double[] values=new double[x.length];for(int i=0;i<x.length;i++){if(x[i].length!=featureWeights.length)throw new IllegalArgumentException("Frozen feature width changed");double value=scoreIntercept;for(int j=0;j<featureWeights.length;j++){double xij=x[i][j];if(Double.isNaN(xij)&&meanImpute)xij=featureMeans[j];if(!Double.isFinite(xij))throw new IllegalArgumentException("Missing/nonfinite predictor under frozen binary preprocessing");value+=xij*featureWeights[j];}if(!Double.isFinite(value))throw new IllegalArgumentException("Unrepresentable binary score");values[i]=value;}return values;}
        public double[] baseline(double[][] covariates){return probabilities(covariates,baselineWeights,null);}
        public double[] predict(double[][] x,double[][] covariates){if(x.length!=covariates.length)throw new IllegalArgumentException("Unaligned frozen score inputs");return probabilities(covariates,covariateWeights,score(x));}
    }
    public record Fit(Frozen frozen,List<Double> tuningLogLoss,double selectedLambda,long work) {public Fit {tuningLogLoss=List.copyOf(tuningLogLoss);}}
    public static Fit train(double[] y,double[][] x,double[][] covariates,int[] training,int[] tuning,double[] lambdas,double alpha,
            boolean refit,boolean meanImpute,Options options) {
        if(y==null||x==null||covariates==null||x.length!=y.length||covariates.length!=y.length||training==null||tuning==null||training.length<4||lambdas==null||lambdas.length==0||options==null||!Double.isFinite(alpha)||alpha<0||alpha>1||lambdas.length>1&&tuning.length<4)throw new IllegalArgumentException("Require aligned binary inputs, training and independent tuning for a penalty search");
        var seen=new java.util.HashSet<Integer>();for(int[] rows:new int[][]{training,tuning})for(int i:rows)if(i<0||i>=y.length||!seen.add(i))throw new IllegalArgumentException("Invalid or overlapping training/tuning rows");
        double previous=Double.POSITIVE_INFINITY;for(double lambda:lambdas){if(!Double.isFinite(lambda)||lambda<=0||lambda>=previous)throw new IllegalArgumentException("Binary score penalties must be positive and strictly descending");previous=lambda;}
        requireBinary(y,training);if(tuning.length>0)requireBinary(y,tuning);var budget=new Budget(options.maximumWork);var losses=new ArrayList<Double>();Frozen best=null;double minimum=Double.POSITIVE_INFINITY,selected=Double.NaN;
        for(double lambda:lambdas){Frozen model=fit(y,x,covariates,training,lambda,alpha,meanImpute,options,budget);double loss=0;if(tuning.length>0){double[] scores=model.score(HeldOutScores.rows(x,tuning));for(int j=0;j<tuning.length;j++){double eta=dot(covariates[tuning[j]],model.covariateWeights)+scores[j];loss+=loss(y[tuning[j]],eta);}loss/=tuning.length;}losses.add(loss);if(best==null||loss<minimum){best=model;minimum=loss;selected=lambda;}}
        if(refit&&tuning.length>0){int[] development=Arrays.copyOf(training,training.length+tuning.length);System.arraycopy(tuning,0,development,training.length,tuning.length);best=fit(y,x,covariates,development,selected,alpha,meanImpute,options,budget);}
        return new Fit(best,losses,selected,budget.used);
    }
    private static Frozen fit(double[] y,double[][] x,double[][] covariates,int[] rows,double lambda,double alpha,boolean impute,Options options,Budget budget) {
        int n=rows.length,p=x[rows[0]].length,q=covariates[rows[0]].length;if(p<1||q<1)throw new IllegalArgumentException("Binary score needs features and an intercept baseline");budget.spend((long)n*(p+q));
        double[] means=new double[p],scales=new double[p],cmeans=new double[q],cscales=new double[q];double[][] design=new double[n][q+p];double[] response=HeldOutScores.values(y,rows);
        for(int i:rows){if(x[i].length!=p||covariates[i].length!=q||covariates[i][0]!=1)throw new IllegalArgumentException("Unaligned score design or missing baseline intercept");for(double v:covariates[i])if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite baseline covariate");}
        for(int j=0;j<p;j++){double[] values=new double[n];for(int k=0;k<n;k++)values[k]=x[rows[k]][j];var column=standardization(values,impute);means[j]=column[0];scales[j]=column[1];}
        cscales[0]=1;for(int j=1;j<q;j++){double[] values=new double[n];for(int k=0;k<n;k++)values[k]=covariates[rows[k]][j];var column=standardization(values,false);cmeans[j]=column[0];cscales[j]=column[1];}
        for(int k=0;k<n;k++){int i=rows[k];design[k][0]=1;for(int j=1;j<q;j++)design[k][j]=(covariates[i][j]-cmeans[j])/cscales[j];for(int j=0;j<p;j++)design[k][q+j]=(Double.isNaN(x[i][j])?0:(x[i][j]-means[j]))/scales[j];}
        for(double[] row:design)for(double v:row)if(!Double.isFinite(v))throw new IllegalArgumentException("Binary score standardization is unrepresentable");
        double[] l1=new double[q+p],l2=new double[q+p];for(int j=1;j<q;j++)l2[j]=options.baselineLambda;for(int j=q;j<q+p;j++){l1[j]=lambda*alpha;l2[j]=lambda*(1-alpha);}
        var full=optimize(response,design,l1,l2,options,budget);double[][] base=new double[n][q];for(int i=0;i<n;i++)System.arraycopy(design[i],0,base[i],0,q);
        var baseline=optimize(response,base,new double[q],Arrays.copyOf(l2,q),options,budget);double[] rawCov=Arrays.copyOf(full.coefficients,q),rawBase=baseline.coefficients.clone(),weights=new double[p];double intercept=0;
        for(int j=1;j<q;j++){rawCov[j]/=cscales[j];rawCov[0]-=cmeans[j]*rawCov[j];rawBase[j]/=cscales[j];rawBase[0]-=cmeans[j]*rawBase[j];}
        for(int j=0;j<p;j++){weights[j]=full.coefficients[q+j]/scales[j];intercept-=means[j]*weights[j];}
        var frozen=new Frozen(weights,means,scales,intercept,rawCov,rawBase,lambda,alpha,options.baselineLambda,n,impute,full.diagnostics,baseline.diagnostics);
        budget.spend((long)n*(p+q));double[] scores=frozen.score(HeldOutScores.rows(x,rows));
        for(int i=0;i<n;i++){double eta=dot(design[i],full.coefficients),saved=dot(covariates[rows[i]],rawCov)+scores[i];double baseEta=dot(base[i],baseline.coefficients),baseSaved=dot(covariates[rows[i]],rawBase);if(!Double.isFinite(saved)||!Double.isFinite(baseSaved)||Math.abs(eta-saved)>1e-7*(1+Math.abs(eta))||Math.abs(baseEta-baseSaved)>1e-7*(1+Math.abs(baseEta)))throw new IllegalArgumentException("Frozen raw coefficient representation lost training prediction precision; rescale the registered inputs");}
        return frozen;
    }
    /** Scaled summation and hypot avoid spurious variance underflow/overflow. */
    private static double[] standardization(double[] values,boolean impute) {
        int called=0;double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
        for(double value:values){if(Double.isFinite(value)){called++;minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);}else if(!Double.isNaN(value)||!impute)throw new IllegalArgumentException("Missing/nonfinite training predictor under binary preprocessing");}
        if(called==0)throw new IllegalArgumentException("Entirely missing binary training feature");if(minimum==maximum)return new double[]{minimum,1};
        double mean=0,correction=0;for(double value:values)if(Double.isFinite(value)){double term=value/called-correction,next=mean+term;correction=(next-mean)-term;mean=next;}
        double scale=0,rootN=Math.sqrt(values.length);for(double value:values)if(Double.isFinite(value))scale=Math.hypot(scale,(value-mean)/rootN);
        if(!Double.isFinite(mean)||!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Unrepresentable training mean/variance; rescale binary score predictors");return new double[]{mean,scale};
    }
    private record Optimized(double[] coefficients,Diagnostics diagnostics) { }
    /** Cyclic coordinate proximal-gradient steps use the global logistic 1/4 curvature bound. */
    private static Optimized optimize(double[] y,double[][] x,double[] l1,double[] l2,Options controls,Budget budget) {
        int n=y.length,p=l1.length;double[] beta=new double[p],eta=new double[n],curvature=new double[p];double mean=Arrays.stream(y).average().orElseThrow();beta[0]=Math.log(mean)-Math.log1p(-mean);Arrays.fill(eta,beta[0]);
        for(int j=0;j<p;j++){for(double[] row:x)curvature[j]+=.25*row[j]*row[j]/n;curvature[j]+=l2[j];}
        for(int iteration=1;iteration<=controls.maximumIterations;iteration++){
            for(int j=0;j<p;j++){budget.spend(n);if(curvature[j]==0)continue;double gradient=l2[j]*beta[j];for(int i=0;i<n;i++)gradient+=x[i][j]*(sigmoid(eta[i])-y[i])/n;double next=soft(beta[j]-gradient/curvature[j],l1[j]/curvature[j]),delta=next-beta[j];if(!Double.isFinite(next))throw new IllegalArgumentException("Binary coefficient is unrepresentable");if(delta!=0){for(int i=0;i<n;i++)eta[i]+=x[i][j]*delta;beta[j]=next;}}
            double kkt=0;for(int j=0;j<p;j++){budget.spend(n);double gradient=l2[j]*beta[j];for(int i=0;i<n;i++)gradient+=x[i][j]*(sigmoid(eta[i])-y[i])/n;kkt=Math.max(kkt,beta[j]==0?Math.max(0,Math.abs(gradient)-l1[j]):Math.abs(gradient+Math.copySign(l1[j],beta[j])));}
            if(!Double.isFinite(kkt))throw new IllegalArgumentException("Binary optimization lost numerical precision");
            if(kkt<=controls.tolerance){double objective=0;for(int i=0;i<n;i++)objective+=loss(y[i],eta[i])/n;for(int j=0;j<p;j++)objective+=l1[j]*Math.abs(beta[j])+.5*l2[j]*beta[j]*beta[j];if(!Double.isFinite(objective))throw new IllegalArgumentException("Binary objective is unrepresentable");return new Optimized(beta,new Diagnostics(iteration,kkt,objective));}
        }
        throw new IllegalArgumentException("Binary score optimization did not satisfy its KKT tolerance before maximum iterations");
    }
    private static void requireBinary(double[] y,int[] rows){int cases=0;for(int i:rows){if(y[i]!=0&&y[i]!=1)throw new IllegalArgumentException("Binary development/tuning outcomes must be individual 0/1 observations");cases+=(int)y[i];}if(cases==0||cases==rows.length)throw new IllegalArgumentException("Binary development/tuning partition needs both cases and controls");}
    public static double sigmoid(double eta){return eta>=0?1/(1+Math.exp(-eta)):Math.exp(eta)/(1+Math.exp(eta));}
    private static double loss(double y,double eta){if(!Double.isFinite(eta))throw new IllegalArgumentException("Nonfinite binary linear predictor");return Math.max(0,eta)-y*eta+Math.log1p(Math.exp(-Math.abs(eta)));}
    private static double soft(double value,double threshold){return Math.copySign(Math.max(0,Math.abs(value)-threshold),value);}
    private static double dot(double[] row,double[] weights){if(row.length!=weights.length||row[0]!=1)throw new IllegalArgumentException("Frozen binary covariate design changed");double value=0;for(int j=0;j<weights.length;j++){if(!Double.isFinite(row[j]))throw new IllegalArgumentException("Nonfinite frozen covariate");value+=row[j]*weights[j];}return value;}
    private static double[] probabilities(double[][] covariates,double[] weights,double[] score){double[] p=new double[covariates.length];for(int i=0;i<p.length;i++){double eta=dot(covariates[i],weights)+(score==null?0:score[i]);if(!Double.isFinite(eta))throw new IllegalArgumentException("Unrepresentable frozen binary prediction");p[i]=sigmoid(eta);}return p;}
    private static final class Budget {final long maximum;long used;Budget(long limit){maximum=limit;}void spend(long work){if(work<0||work>maximum-used)throw new IllegalArgumentException("Binary score optimizer work budget exceeded");used+=work;}}
}
