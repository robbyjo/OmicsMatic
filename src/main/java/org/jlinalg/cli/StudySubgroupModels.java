/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.Arrays;
import java.util.Map;
import jdistlib.ChiSquare;
import jdistlib.Normal;
import jdistlib.T;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.glm.Glm;
import org.jlinalg.glm.GlmFamilies;
import org.jlinalg.glm.GlmOptions;
import org.jlinalg.glm.GlmResult;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.regression.FirthLogistic;

/** Two-level adjusted effect modification; all nuisance terms remain in null fits. */
final class StudySubgroupModels {
    private StudySubgroupModels() { }
    static final class Unresolved extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        Unresolved(String reason){super(reason);}
    }
    record Fit(double beta,double se,double lower,double upper,double p,double logP,double statistic,double df,
            double referenceBeta,double referenceSe,double comparisonBeta,double comparisonSe,
            int parameters,int iterations,String inference,String effectScale) { }
    static int parameters(int covariates,String interactions) {
        return 4+covariates*(interactions.equals("all_two_way")?3:interactions.equals("group_only")?2:1);
    }
    static Fit fit(String method,double[] y,double[][] covariates,int[] group,double[] feature,double[] offset,Map<String,Object> policy) {
        int n=y.length,q=covariates[0].length,p=parameters(q,String.valueOf(policy.get("nuisance_interactions")));
        if(n<=p||p>((Number)policy.get("maximum_parameters")).intValue())throw new IllegalArgumentException("insufficient_residual_degrees_of_freedom_or_parameter_bound");
        String interactions=String.valueOf(policy.get("nuisance_interactions"));
        double[] means=new double[q],scales=new double[q];double mean=Arrays.stream(feature).average().orElseThrow(),scale=0;
        for(int i=0;i<n;i++){scale=Math.max(scale,Math.abs(feature[i]-mean));for(int j=0;j<q;j++)means[j]+=covariates[i][j]/n;}
        if(!(scale>0)||!Double.isFinite(scale))throw new IllegalArgumentException("constant_or_unrepresentable_feature");
        for(int i=0;i<n;i++)for(int j=0;j<q;j++)scales[j]=Math.max(scales[j],Math.abs(covariates[i][j]-means[j]));
        for(double s:scales)if(!(s>0)||!Double.isFinite(s))throw new IllegalArgumentException("constant_or_unrepresentable_covariate");
        double[][] x=new double[n][p];int featureTerm=2+q+(interactions.equals("none")?0:q),tested=p-1;
        for(int i=0;i<n;i++) {
            x[i][0]=1;x[i][1]=group[i];int at=2;
            for(int j=0;j<q;j++)x[i][at++]=(covariates[i][j]-means[j])/scales[j];
            if(!interactions.equals("none"))for(int j=0;j<q;j++)x[i][at++]=(covariates[i][j]-means[j])/scales[j]*group[i];
            double value=(feature[i]-mean)/scale;x[i][at++]=value;
            if(interactions.equals("all_two_way"))for(int j=0;j<q;j++)x[i][at++]=(covariates[i][j]-means[j])/scales[j]*value;
            x[i][at]=value*group[i];
        }
        if(method.equals("logit-lrt")||method.equals("firth-lrt")) {
            if(Arrays.stream(y).allMatch(v->v==0)||Arrays.stream(y).allMatch(v->v==1))throw new IllegalArgumentException("The complete interaction sample has only one outcome class");
            Ols.fit(new double[n],x,OlsOptions.defaults(),BackendPolicy.CPU); // Structural full-rank check precedes numerical optimization.
        }
        double[] beta,covariance;double prob,logP,statistic,df,critical;int iterations=0;String inference;
        if(method.startsWith("ols-")) {
            double[] response=y.clone();for(int i=0;i<n;i++)response[i]-=offset[i];
            var fit=Ols.fit(response,x,OlsOptions.defaults(),BackendPolicy.CPU);beta=fit.coefficients();covariance=fit.covariance();df=fit.residualDegreesOfFreedom();
            if(!(fit.residualVariance()>0)||!Double.isFinite(fit.residualVariance()))throw new IllegalArgumentException("zero_or_unrepresentable_residual_variance");
            if(method.equals("ols-hc3")) {
                double[] bread=covariance.clone();for(int j=0;j<bread.length;j++)bread[j]/=fit.residualVariance();
                covariance=hc3(x,fit.residuals(),bread);
                inference="HC3 heteroskedasticity-consistent covariance; residual-df t approximation, not an exact finite-sample law";
            } else inference="Homoskedastic Gaussian linear-model residual-df t test; validity requires the declared independent-error model";
            statistic=beta[tested]/Math.sqrt(covariance[tested*p+tested]);logP=Math.log(2)+T.cumulative(-Math.abs(statistic),df,true,true);prob=Math.min(1,Math.exp(logP));critical=T.quantile(.975,df,true,false);
        } else if(method.equals("firth-lrt")) {
            var options=new FirthLogistic.Options(((Number)policy.get("maximum_iterations")).intValue(),40,((Number)policy.get("tolerance")).doubleValue(),5);
            FirthLogistic.Test fit;
            try{fit=FirthLogistic.test(y,x,offset,tested,0,options);}catch(IllegalArgumentException invalid){throw new Unresolved("Firth_interaction_optimization_unresolved: "+invalid.getMessage());}
            if(!fit.converged())throw new Unresolved("Firth_full_or_full_design_constrained_fit_did_not_converge");
            beta=fit.full().coefficients();covariance=fit.full().covariance();prob=fit.pValue();logP=fit.logPValue();statistic=fit.likelihoodRatio();df=1;critical=Normal.quantile(.975,0,1,true,false);iterations=fit.full().iterations()+fit.constrained().iterations();
            inference="Full-design Jeffreys-penalized likelihood-ratio chi-square(1) asymptotic test; ordinary-Fisher SE and Wald intervals are separate approximations";
        } else if(method.equals("logit-lrt")) {
            var controls=GlmOptions.builder().maximumIterations(((Number)policy.get("maximum_iterations")).intValue()).relativeTolerance(((Number)policy.get("tolerance")).doubleValue()).build();
            var full=logistic(y,x,offset,controls);
            if(!full.converged()||Arrays.stream(full.coefficients()).anyMatch(b->Math.abs(b)>40))throw new Unresolved("Logistic_full_fit_nonconvergence_or_possible_separation_no_Firth_fallback");
            double[][] reduced=new double[n][p-1];for(int i=0;i<n;i++)System.arraycopy(x[i],0,reduced[i],0,p-1);
            var nullFit=logistic(y,reduced,offset,controls);
            if(!nullFit.converged()||Arrays.stream(nullFit.coefficients()).anyMatch(b->Math.abs(b)>40)||full.logLikelihood()+1e-8<nullFit.logLikelihood())throw new Unresolved("Logistic_constrained_fit_nonconvergence_or_invalid_likelihood_order");
            beta=full.coefficients();covariance=full.covariance();statistic=Math.max(0,2*(full.logLikelihood()-nullFit.logLikelihood()));logP=ChiSquare.cumulative(statistic,1,false,true);prob=Math.exp(logP);df=1;critical=Normal.quantile(.975,0,1,true,false);iterations=full.iterations()+nullFit.iterations();
            inference="Independent Bernoulli logit maximum-likelihood nested likelihood-ratio chi-square(1) asymptotic test; ordinary-Fisher Wald intervals";
        } else throw new Unresolved("No_native_adapter_for_registered_interaction_method");
        double variance=covariance[tested*p+tested],referenceVariance=covariance[featureTerm*p+featureTerm];
        double comparisonVariance=referenceVariance+variance+2*covariance[featureTerm*p+tested];
        if(!(variance>0)||!(referenceVariance>0)||!(comparisonVariance>0)||!Double.isFinite(variance)||!Double.isFinite(prob)||prob<0||prob>1||!Double.isFinite(logP)||logP>1e-12)throw new Unresolved("Unrepresentable_interaction_uncertainty_or_probability");
        double estimate=beta[tested]/scale,se=Math.sqrt(variance)/scale,lower=estimate-critical*se,upper=estimate+critical*se;
        double referenceBeta=beta[featureTerm]/scale,referenceSe=Math.sqrt(referenceVariance)/scale;
        double comparisonBeta=(beta[featureTerm]+beta[tested])/scale,comparisonSe=Math.sqrt(comparisonVariance)/scale;
        if(!(se>0)||!(referenceSe>0)||!(comparisonSe>0)||Arrays.stream(new double[]{estimate,se,lower,upper,referenceBeta,referenceSe,comparisonBeta,comparisonSe}).anyMatch(v->!Double.isFinite(v)))throw new Unresolved("Interaction_effect_or_uncertainty_unrepresentable_in_input_units");
        return new Fit(estimate,se,lower,upper,prob,logP,statistic,df,
            referenceBeta,referenceSe,comparisonBeta,comparisonSe,
            p,iterations,inference,method.startsWith("ols-")?"outcome_units_per_feature_unit":"conditional_log_odds_per_feature_unit");
    }
    private static double[] hc3(double[][] x,double[] residual,double[] bread) {
        int n=x.length,p=x[0].length;double[] covariance=new double[p*p];
        for(int i=0;i<n;i++) {
            double[] influence=new double[p];for(int j=0;j<p;j++)for(int k=0;k<p;k++)influence[j]+=bread[j*p+k]*x[i][k];
            double h=0;for(int j=0;j<p;j++)h+=x[i][j]*influence[j];
            if(!Double.isFinite(h)||h < -1e-7||h>=1-1e-10)throw new Unresolved("HC3_leverage_is_unresolved");
            double scale=residual[i]/(1-Math.max(0,h));
            for(int j=0;j<p;j++)for(int k=0;k<=j;k++)covariance[j*p+k]+=influence[j]*influence[k]*scale*scale;
        }
        for(int j=0;j<p;j++)for(int k=0;k<j;k++)covariance[k*p+j]=covariance[j*p+k];return covariance;
    }
    private static double[] flatten(double[][] x){int p=x[0].length;double[] out=new double[x.length*p];for(int i=0;i<x.length;i++)System.arraycopy(x[i],0,out,i*p,p);return out;}
    private static GlmResult logistic(double[] y,double[][] x,double[] offset,GlmOptions options) {
        try{return Glm.fit(y,flatten(x),y.length,x[0].length,GlmFamilies.binomial(),null,offset,options,BackendPolicy.CPU);}
        catch(IllegalArgumentException invalid){throw new Unresolved("Logistic_interaction_optimization_unresolved: "+invalid.getMessage());}
    }
}
