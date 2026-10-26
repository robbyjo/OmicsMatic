/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import org.jlinalg.stats.Alternative;

/** Finite conditional tests of an additive 0/1/2 genotype coefficient.
 * Conditions on each stratum's case count. Known offsets weight assignments;
 * no other nuisance covariates, case weights or implicit kinship model are used.
 * Missing genotypes are excluded before variant-specific margins are computed.
 * Exact conditional likelihood fitting and its asymptotic tests are different
 * procedures. This class sums the finite null distribution of the score. */
public final class ExactConditionalLogit {
    private ExactConditionalLogit() { }
    public record Options(int maximumStates,long maximumWork,double probabilityOrderingTolerance) {
        public Options {
            if(maximumStates<1||maximumWork<1||!Double.isFinite(probabilityOrderingTolerance)||probabilityOrderingTolerance<0||probabilityOrderingTolerance>.001)
                throw new IllegalArgumentException("Invalid exact conditional resource/precision controls");
        }
        public static Options defaults(){return new Options(200000,50000000,1e-7);}
    }
    public record Distribution(long minimumScore,long observedScore,double[] logProbabilities) {
        public Distribution {logProbabilities=logProbabilities.clone();}
        @Override public double[] logProbabilities(){return logProbabilities.clone();}
        public long maximumScore(){return minimumScore+logProbabilities.length-1;}
    }
    public record Result(String status,String reason,double pValue,double logPValue,double centeredScore,
            double nullMean,double nullVariance,int observations,int cases,int strata,int informativeStrata,
            long work,int maximumStates,Distribution distribution,int[] analyzedRows) {
        public Result {analyzedRows=analyzedRows.clone();}
        @Override public int[] analyzedRows(){return analyzedRows.clone();}
        public boolean estimable(){return status.equals("complete");}
    }
    public static Result test(double[] response,double[] genotype,int[] strata,double[] offset,Alternative alternative,Options options) {
        if(response==null||genotype==null||response.length!=genotype.length||response.length==0||alternative==null||options==null
                ||strata!=null&&strata.length!=response.length||offset!=null&&offset.length!=response.length)
            throw new IllegalArgumentException("Aligned individual Bernoulli, hard-call genotype, stratum and known offset arrays are required");
        var groups=new LinkedHashMap<Integer,List<Integer>>();var kept=new ArrayList<Integer>();int cases=0;long observed=0;
        for(int i=0;i<response.length;i++) {
            if(response[i]!=0&&response[i]!=1||offset!=null&&!Double.isFinite(offset[i]))throw new IllegalArgumentException("Exact conditional inference requires individual 0/1 outcomes and finite known offsets");
            double g=genotype[i];if(Double.isNaN(g))continue;
            if(!Double.isFinite(g)||g<0||g>2||g!=Math.rint(g))throw new IllegalArgumentException("Exact conditional inference requires observed integer 0/1/2 calls; dosages are not rounded");
            kept.add(i);groups.computeIfAbsent(strata==null?0:strata[i],ignored->new ArrayList<>()).add(i);if(response[i]==1){cases++;observed+=(int)g;}
        }
        int[] analyzed=kept.stream().mapToInt(Integer::intValue).toArray();var budget=new Budget(options);int informative=0;
        try {
            budget.spend(response.length);Dist combined=new Dist(0,new double[]{0});
            for(var indexes:groups.values()) {
                Dist local=stratum(response,genotype,offset,indexes,budget);
                if(finiteStates(local.logs)>1)informative++;
                combined=convolve(combined,local,budget);
            }
            double meanIndex=0;for(int i=0;i<combined.logs.length;i++)meanIndex+=i*Math.exp(combined.logs[i]);
            double variance=0;for(int i=0;i<combined.logs.length;i++)variance+=Math.exp(combined.logs[i])*(i-meanIndex)*(i-meanIndex);
            var distribution=new Distribution(combined.minimum,observed,combined.logs);
            if(informative==0)return new Result("no_information","No within-stratum outcome/genotype information",Double.NaN,Double.NaN,Double.NaN,combined.minimum+meanIndex,variance,kept.size(),cases,groups.size(),0,budget.work,budget.peak,distribution,analyzed);
            int index=Math.toIntExact(observed-combined.minimum);
            if(index<0||index>=combined.logs.length||!Double.isFinite(combined.logs[index]))throw new Numerical("Observed score is outside representable conditional support");
            double logP=Double.NEGATIVE_INFINITY,limit=combined.logs[index]+Math.log1p(options.probabilityOrderingTolerance);
            for(int i=0;i<combined.logs.length;i++)if(switch(alternative){case LESS->i<=index;case GREATER->i>=index;case TWO_SIDED->combined.logs[i]<=limit;})logP=add(logP,combined.logs[i]);
            logP=Math.min(0,logP);
            return new Result("complete","Finite conditional score distribution; "+(alternative==Alternative.TWO_SIDED?"probability-ordering two-sided tail":alternative.name().toLowerCase()+" score tail"),Math.exp(logP),logP,index-meanIndex,combined.minimum+meanIndex,variance,kept.size(),cases,groups.size(),informative,budget.work,budget.peak,distribution,analyzed);
        }catch(Limit limit) {
            return unavailable("budget_exceeded",limit.getMessage(),kept.size(),cases,groups.size(),informative,budget,analyzed);
        }catch(Numerical numerical) {
            return unavailable("numerical_unavailable",numerical.getMessage(),kept.size(),cases,groups.size(),informative,budget,analyzed);
        }
    }
    private static Result unavailable(String status,String reason,int n,int cases,int strata,int informative,Budget budget,int[] rows) {
        return new Result(status,reason,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,n,cases,strata,informative,budget.work,budget.peak,null,rows);
    }
    private static Dist stratum(double[] y,double[] g,double[] offset,List<Integer> rows,Budget budget) {
        int n=rows.size(),cases=0,minG=2,maxG=0;long total=0,observed=0;boolean constantOffset=true;
        double first=offset==null?0:offset[rows.get(0)];
        for(int i:rows){int value=(int)g[i];total+=value;minG=Math.min(minG,value);maxG=Math.max(maxG,value);if(y[i]==1){cases++;observed+=value;}constantOffset&=offset==null||offset[i]==first;}
        if(cases==0||cases==n||minG==maxG)return new Dist(observed,new double[]{0});
        boolean controls=cases>n-cases,flip=total>n;int selected=controls?n-cases:cases;int[] counts=new int[3];long primeTotal=0;
        for(int i:rows){int value=flip?2-(int)g[i]:(int)g[i];counts[value]++;primeTotal+=value;}
        Dist distribution=constantOffset?counts(counts,selected,budget):weighted(y,g,offset,rows,selected,controls,flip,primeTotal,budget);
        if(flip)distribution=reflect(distribution,2L*selected);
        if(controls)distribution=reflect(distribution,total);
        return distribution;
    }
    /** Relative binomial recurrences avoid subtracting huge log-gamma values. */
    private static Dist counts(int[] n,int selected,Budget budget) {
        int total=n[0]+n[1]+n[2];int[] low=new int[3],high=new int[3];double[][] choose=new double[3][];
        for(int j=0;j<3;j++){low[j]=Math.max(0,selected-(total-n[j]));high[j]=Math.min(n[j],selected);int length=high[j]-low[j]+1;budget.states(length);budget.spend(length);choose[j]=new double[length];for(int k=low[j];k<high[j];k++)choose[j][k-low[j]+1]=choose[j][k-low[j]]+Math.log(n[j]-k)-Math.log(k+1.0);}
        long minimum=minimumScore(n,selected),maximum=2L*selected-minimumScore(new int[]{n[2],n[1],n[0]},selected);
        int length=budget.states(maximum-minimum+1);double[] logs=new double[length];Arrays.fill(logs,Double.NEGATIVE_INFINITY);
        for(int one=low[1];one<=high[1];one++) {
            int from=Math.max(low[2],selected-one-high[0]),to=Math.min(high[2],selected-one-low[0]);budget.spend(1);
            if(to>=from)budget.ensure(to-(long)from+1);
            for(int two=from;two<=to;two++) {
                budget.spend(1);int zero=selected-one-two;int index=Math.toIntExact(one+2L*two-minimum);
                logs[index]=add(logs[index],choose[0][zero-low[0]]+choose[1][one-low[1]]+choose[2][two-low[2]]);
            }
        }
        normalize(logs);return new Dist(minimum,logs);
    }
    private static long minimumScore(int[] n,int selected){int remaining=Math.max(0,selected-n[0]),one=Math.min(n[1],remaining);return one+2L*(remaining-one);}
    /** Elementary-symmetric recursion jointly indexes selected count and allele score. */
    private static Dist weighted(double[] y,double[] g,double[] offset,List<Integer> rows,int selected,boolean controls,boolean flip,long total,Budget budget) {
        int width=budget.states(Math.min(2L*selected,total)+1);budget.states((selected+1L)*width);
        double[][] dp=new double[selected+1][width];for(double[] row:dp)Arrays.fill(row,Double.NEGATIVE_INFINITY);dp[0][0]=0;
        double maximum=Double.NEGATIVE_INFINITY;for(int i:rows)maximum=Math.max(maximum,controls?-offset[i]:offset[i]);int seen=0;
        for(int i:rows) {
            double logWeight=(controls?-offset[i]:offset[i])-maximum;if(!Double.isFinite(logWeight))throw new Numerical("Known offset contrast is unrepresentable");
            int value=flip?2-(int)g[i]:(int)g[i];seen++;
            for(int k=Math.min(selected,seen);k>=1;k--)for(int score=Math.min(width-1,2*k);score>=value;score--) {
                budget.spend(1);double previous=dp[k-1][score-value];
                if(Double.isFinite(previous)){double term=previous+logWeight;if(!Double.isFinite(term))throw new Numerical("Conditional log weight is unrepresentable");dp[k][score]=add(dp[k][score],term);}
            }
        }
        double[] result=dp[selected];normalize(result);return trim(new Dist(0,result));
    }
    private static Dist reflect(Dist d,long constant){double[] logs=new double[d.logs.length];for(int i=0;i<logs.length;i++)logs[i]=d.logs[logs.length-1-i];return new Dist(constant-d.minimum-logs.length+1,logs);}
    private static Dist trim(Dist d){int start=0,end=d.logs.length;while(start<end&&!Double.isFinite(d.logs[start]))start++;while(end>start&&!Double.isFinite(d.logs[end-1]))end--;if(start==end)throw new Numerical("Conditional support has no finite mass");return start==0&&end==d.logs.length?d:new Dist(d.minimum+start,Arrays.copyOfRange(d.logs,start,end));}
    private static Dist convolve(Dist a,Dist b,Budget budget) {
        if(a.logs.length==1)return new Dist(a.minimum+b.minimum,b.logs);
        if(b.logs.length==1)return new Dist(a.minimum+b.minimum,a.logs);
        int length=budget.states(a.logs.length+(long)b.logs.length-1);double[] logs=new double[length];Arrays.fill(logs,Double.NEGATIVE_INFINITY);
        for(int i=0;i<a.logs.length;i++)if(Double.isFinite(a.logs[i]))for(int j=0;j<b.logs.length;j++){budget.spend(1);if(Double.isFinite(b.logs[j])){double term=a.logs[i]+b.logs[j];if(!Double.isFinite(term))throw new Numerical("Conditional convolution log mass is unrepresentable");logs[i+j]=add(logs[i+j],term);}}
        normalize(logs);return new Dist(a.minimum+b.minimum,logs);
    }
    private static void normalize(double[] logs){double sum=Double.NEGATIVE_INFINITY;for(double value:logs)sum=add(sum,value);if(!Double.isFinite(sum))throw new Numerical("Conditional normalization is unrepresentable");double check=Double.NEGATIVE_INFINITY;for(int i=0;i<logs.length;i++){logs[i]-=sum;check=add(check,logs[i]);}if(!Double.isFinite(check)||Math.abs(check)>1e-8)throw new Numerical("Conditional normalization lost numerical precision");}
    private static double add(double a,double b){if(a==Double.NEGATIVE_INFINITY)return b;if(b==Double.NEGATIVE_INFINITY)return a;double maximum=Math.max(a,b);return maximum+Math.log1p(Math.exp(Math.min(a,b)-maximum));}
    private static int finiteStates(double[] values){int n=0;for(double v:values)if(Double.isFinite(v))n++;return n;}
    private record Dist(long minimum,double[] logs) { }
    private static final class Budget {
        final Options options;long work;int peak;Budget(Options options){this.options=options;}
        void ensure(long n){if(n<0||n>options.maximumWork-work)throw new Limit("Exact conditional work budget exceeded");}
        void spend(long n){ensure(n);work+=n;}
        int states(long n){if(n<1||n>options.maximumStates)throw new Limit("Exact conditional support/table state budget exceeded");peak=Math.max(peak,(int)n);return (int)n;}
    }
    private static final class Limit extends RuntimeException {private static final long serialVersionUID=1L;Limit(String message){super(message);}}
    private static final class Numerical extends RuntimeException {private static final long serialVersionUID=1L;Numerical(String message){super(message);}}
}
