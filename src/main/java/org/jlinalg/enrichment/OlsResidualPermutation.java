/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.ols.Ols;
import org.jlinalg.ols.OlsOptions;

/**
 * Shared Freedman-Lane permutations for independent homoscedastic OLS errors.
 * Each draw permutes the reduced-model response residual, adds the fixed
 * nuisance fit, and refits the full partial-t statistic for every predictor.
 * With arbitrary nuisance covariates this is an approximate randomization
 * procedure; enumeration does not remove that approximation.
 */
public final class OlsResidualPermutation {
    private final double[][] design, predictors;
    private final double[] fitted, residual;
    private final int degreesOfFreedom, draws;
    private final boolean exact;
    private final List<int[]> groups;
    private final int[] permutation;
    private final Random random;
    private final double[] observed;
    private int next;

    public OlsResidualPermutation(double[] response,double[][] nuisance,
            double[][] featureVectors,List<String> exchangeabilityStrata,
            int permutations,int maximumExactStates,long seed) {
        if(response==null||nuisance==null||featureVectors==null||response.length<4
                ||nuisance.length!=response.length||nuisance[0].length<1
                ||featureVectors.length<2||permutations<1||maximumExactStates<0)
            throw new IllegalArgumentException("Require finite independent-unit response, nuisance design and at least two predictors");
        design=new double[nuisance.length][];
        for(int i=0;i<design.length;i++){if(nuisance[i].length!=nuisance[0].length)throw new IllegalArgumentException("Ragged nuisance design");design[i]=nuisance[i].clone();}
        double[] y=scaled(response);var reduced=Ols.fit(y,design,OlsOptions.defaults(),BackendPolicy.CPU);
        if(reduced.rank()!=design[0].length)throw new IllegalArgumentException("GSEA nuisance design must have full column rank");
        degreesOfFreedom=response.length-reduced.rank()-1;
        if(degreesOfFreedom<2)throw new IllegalArgumentException("OLS GSEA requires at least two full-model residual degrees of freedom");
        residual=reduced.residuals();fitted=new double[y.length];
        for(int i=0;i<y.length;i++)fitted[i]=y[i]-residual[i];
        if(!(norm(residual)>1e-24*y.length))throw new IllegalArgumentException("Outcome is constant or explained by nuisance covariates");
        predictors=new double[featureVectors.length][];
        for(int j=0;j<predictors.length;j++){
            if(featureVectors[j].length!=y.length)throw new IllegalArgumentException("Predictors must share the complete analyzed sample universe");
            double[] x=scaled(featureVectors[j]);double[] r=Ols.fit(x,design,OlsOptions.defaults(),BackendPolicy.CPU).residuals();double ss=norm(r);
            if(!(ss>Math.max(Double.MIN_NORMAL,1e-24*r.length)))throw new IllegalArgumentException("Constant or nuisance-explained predictor at index "+j);
            double scale=Math.sqrt(ss);for(int i=0;i<r.length;i++)r[i]/=scale;predictors[j]=r;
        }
        observed=statistics(residual);
        if(exchangeabilityStrata!=null&&exchangeabilityStrata.size()!=y.length)throw new IllegalArgumentException("Exchangeability strata must match analysis samples");
        var byStratum=new LinkedHashMap<String,List<Integer>>();
        for(int i=0;i<y.length;i++){String key=exchangeabilityStrata==null?"all":exchangeabilityStrata.get(i);if(key==null||key.isBlank())throw new IllegalArgumentException("Missing exchangeability stratum");byStratum.computeIfAbsent(key,k->new ArrayList<>()).add(i);}
        groups=new ArrayList<>();long states=1;
        for(var group:byStratum.values()){int[] g=group.stream().mapToInt(Integer::intValue).toArray();groups.add(g);for(int j=2;j<=g.length;j++){if(states>maximumExactStates/(long)j){states=(long)maximumExactStates+1;break;}states*=j;}}
        if(groups.stream().allMatch(g->g.length==1))throw new IllegalArgumentException("Exchangeability strata permit no nonidentity permutation");
        exact=maximumExactStates>0&&states<=maximumExactStates;draws=exact?(int)states:permutations;
        permutation=new int[y.length];for(int i=0;i<permutation.length;i++)permutation[i]=i;random=new Random(seed);
    }
    public int drawCount(){return draws;}
    /** Complete enumeration of the declared residual permutations, not exact nuisance-model inference. */
    public boolean exhaustive(){return exact;}
    public int residualDegreesOfFreedom(){return degreesOfFreedom;}
    public double[] observedStatistics(){return observed.clone();}
    /** Sequential, deterministic draw; shared across every predictor. */
    public double[] nextStatistics(){
        if(next>=draws)throw new IllegalStateException("All declared null draws have been consumed");
        if(!exact){for(int i=0;i<permutation.length;i++)permutation[i]=i;for(int[] group:groups)for(int j=group.length-1;j>0;j--){int k=random.nextInt(j+1),value=permutation[group[j]];permutation[group[j]]=permutation[group[k]];permutation[group[k]]=value;}}
        double[] y=new double[residual.length];for(int i=0;i<y.length;i++)y[i]=fitted[i]+residual[permutation[i]];
        double[] result=statistics(Ols.fit(y,design,OlsOptions.defaults(),BackendPolicy.CPU).residuals());
        next++;if(exact&&next<draws)advance();return result;
    }
    /** Mixed-radix lexicographic census: first encountered stratum advances fastest. */
    private void advance(){for(int[] group:groups){int i=group.length-2;while(i>=0&&permutation[group[i]]>=permutation[group[i+1]])i--;if(i<0){for(int j=0;j<group.length;j++)permutation[group[j]]=group[j];continue;}int j=group.length-1;while(permutation[group[j]]<=permutation[group[i]])j--;int v=permutation[group[i]];permutation[group[i]]=permutation[group[j]];permutation[group[j]]=v;for(int a=i+1,b=group.length-1;a<b;a++,b--){v=permutation[group[a]];permutation[group[a]]=permutation[group[b]];permutation[group[b]]=v;}return;}throw new IllegalStateException("Permutation census ended early");}
    private double[] statistics(double[] y){
        double ss=norm(y);if(!(ss>Double.MIN_NORMAL)||!Double.isFinite(ss))throw new IllegalArgumentException("A complete null draw has no estimable outcome variation; the family was not selectively reduced");
        double[] result=new double[predictors.length];
        for(int j=0;j<result.length;j++){
            double dot=0;for(int i=0;i<y.length;i++)dot+=predictors[j][i]*y[i];
            double error=ss-dot*dot;
            if(!(error>ss*1e-14))throw new IllegalArgumentException("Observed/null OLS rank is numerically unestimable (perfect or unresolved fit); no draw/feature was discarded");
            result[j]=dot*Math.sqrt(degreesOfFreedom/error);
            if(!Double.isFinite(result[j]))throw new IllegalArgumentException("Nonfinite complete OLS rank");
        }
        return result;
    }
    private static double[] scaled(double[] input){double max=0;for(double v:input){if(!Double.isFinite(v))throw new IllegalArgumentException("Finite OLS response and predictors are required");max=Math.max(max,Math.abs(v));}double[] r=input.clone();if(max>0)for(int i=0;i<r.length;i++)r[i]/=max;return r;}
    private static double norm(double[] values){double sum=0;for(double v:values)sum+=v*v;return sum;}
}
