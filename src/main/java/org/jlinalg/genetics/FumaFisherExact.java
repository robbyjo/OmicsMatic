/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Root interpolation adapted from R 4.2.3 stats/src/zeroin.c,
 * Copyright (C) 1999-2016 The R Core Team, GPL-2.0-or-later.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import jdistlib.HyperGeometric;

/**
 * Bounded central two-sided Fisher inference for an explicitly supplied 2-by-2
 * table. Rows are finer-resolution and comparator groups; columns are novel and
 * known associations. Pseudocounts are explicit and original counts remain visible.
 * Conditional odds estimates and central exact confidence limits use the R 4.2.3
 * default root protocol, including its finite tolerance and inverse parameterization.
 */
public final class FumaFisherExact {
    private FumaFisherExact() { }
    /** R's default uniroot tolerance, the fourth root of binary64 epsilon. */
    public static final double ROOT_TOLERANCE=Math.pow(Math.ulp(1.0),.25);
    /** Original count cells, in row order: finer novel/known, comparator novel/known. */
    public record Counts(long finerNovel,long finerKnown,long comparatorNovel,long comparatorKnown) {
        public Counts {if(finerNovel<0||finerKnown<0||comparatorNovel<0||comparatorKnown<0)throw new IllegalArgumentException("Fisher count cells must be nonnegative integers");}
        /** Exact total; overflow refuses admission. */
        public long total(){return Math.addExact(Math.addExact(finerNovel,finerKnown),Math.addExact(comparatorNovel,comparatorKnown));}
    }
    /** Positive input/support/work/iteration admission limits, not operating-system quotas. */
    public record Limits(int maximumPopulation,int maximumSupport,long maximumOperations,int maximumRootIterations) {
        public Limits {if(maximumPopulation<1||maximumSupport<1||maximumOperations<1||maximumRootIterations<1||maximumRootIterations>100000)throw new IllegalArgumentException("Require positive bounded Fisher capacities");}
        /** Per-table capacities; a family runner must also bound cumulative work and support. */
        public static Limits defaults(){return new Limits(10_000_000,200_000,100_000_000L,1000);}
    }
    /** Explicit integer pseudocount for every cell, confidence coverage and capacities. */
    public record Options(int pseudocount,double confidenceLevel,Limits limits) {
        public Options {if(pseudocount<0||!Double.isFinite(confidenceLevel)||confidenceLevel<=0||confidenceLevel>=1||limits==null)throw new IllegalArgumentException("Declare nonnegative Fisher pseudocount,confidence level in (0,1),and capacities");}
    }
    /** Solver parameterization and remaining bracket width expose finite root resolution. */
    public record RootAudit(String quantity,boolean inverse,double root,double bracketWidth,int iterations) { }
    /** Complete original/tested counts and statistical output, with no correction-family inference. */
    public record Result(Counts original,Counts tested,int pseudocount,double pValue,double logPValue,
            double oddsRatio,double lowerConfidence,double upperConfidence,double confidenceLevel,
            int supportStates,List<RootAudit> roots,long operations,List<String> warnings) {
        public Result {roots=List.copyOf(roots);warnings=List.copyOf(warnings);}
    }
    private static final class Work {
        final Limits limits;final Runnable callback;long operations;
        Work(Limits limits,Runnable callback){this.limits=limits;this.callback=callback;}
        void check(){callback.run();}
        void add(long count){if(count>limits.maximumOperations()-operations)throw new IllegalArgumentException("Complete Fisher operation budget exceeded;no support states were truncated");long before=operations;operations+=count;if((before>>>10)!=(operations>>>10))check();}
    }
    private static final class Sum {
        double value,correction;
        void add(double term){double y=term-correction,next=value+y;correction=(next-value)-y;value=next;}
    }
    private record Root(double value,double width,int iterations) { }
    private static final class Distribution {
        final Work work;final int lower,upper,observed,m,n,k;final double[] logDensity,probabilities;
        final List<RootAudit> roots=new ArrayList<>();
        Distribution(Counts counts,Work work){
            this.work=work;m=Math.toIntExact(Math.addExact(counts.finerNovel(),counts.comparatorNovel()));n=Math.toIntExact(Math.addExact(counts.finerKnown(),counts.comparatorKnown()));k=Math.toIntExact(Math.addExact(counts.finerNovel(),counts.finerKnown()));observed=Math.toIntExact(counts.finerNovel());lower=Math.max(0,k-n);upper=Math.min(k,m);
            long support=(long)upper-lower+1;if(support>work.limits.maximumSupport())throw new IllegalArgumentException("Complete Fisher support exceeds maximumSupport;no states were truncated");logDensity=new double[(int)support];probabilities=new double[(int)support];
            for(int i=0;i<logDensity.length;i++){work.add(1);double value=HyperGeometric.density(lower+i,m,n,k,true);if(!Double.isFinite(value))throw new ArithmeticException("Nonfinite Fisher log density inside the admitted support");logDensity[i]=value;}work.check();
        }
        void density(double odds){
            double maximum=Double.NEGATIVE_INFINITY,logOdds=Math.log(odds);
            for(int i=0;i<logDensity.length;i++){work.add(1);probabilities[i]=logDensity[i]+logOdds*(lower+i);maximum=Math.max(maximum,probabilities[i]);}
            if(!Double.isFinite(maximum))throw new ArithmeticException("No finite Fisher noncentral normalization");var sum=new Sum();
            for(int i=0;i<probabilities.length;i++){work.add(1);probabilities[i]=Math.exp(probabilities[i]-maximum);sum.add(probabilities[i]);}
            for(int i=0;i<probabilities.length;i++){work.add(1);probabilities[i]/=sum.value;}
        }
        double mean(double odds){
            work.add(1);if(odds==0)return lower;if(odds==Double.POSITIVE_INFINITY)return upper;density(odds);var sum=new Sum();for(int i=0;i<probabilities.length;i++){work.add(1);sum.add((lower+i)*probabilities[i]);}return sum.value;
        }
        double tail(double odds,boolean upperTail){
            work.add(1);if(odds==1)return HyperGeometric.cumulative(upperTail?observed-1.0:observed,m,n,k,!upperTail,false);
            if(odds==0)return (upperTail?observed<=lower:observed>=lower)?1:0;
            if(odds==Double.POSITIVE_INFINITY)return (upperTail?observed<=upper:observed>=upper)?1:0;
            density(odds);var sum=new Sum();for(int i=0;i<probabilities.length;i++){work.add(1);if(upperTail?lower+i>=observed:lower+i<=observed)sum.add(probabilities[i]);}return Math.min(1,sum.value);
        }
        double fit(String quantity,boolean inverse,DoubleUnaryOperator function){
            Root result=root(function,inverse?Math.ulp(1.0):0,1,work);roots.add(new RootAudit(quantity,inverse,result.value(),result.width(),result.iterations()));return inverse?1/result.value():result.value();
        }
        double estimate(){
            if(observed==lower)return 0;if(observed==upper)return Double.POSITIVE_INFINITY;double mean=mean(1);
            if(mean>observed)return fit("conditional-odds-ratio",false,t->mean(t)-observed);
            if(mean<observed)return fit("conditional-odds-ratio",true,t->mean(1/t)-observed);return 1;
        }
        double lower(double alpha){
            if(observed==lower)return 0;double p=tail(1,true);
            if(p>alpha)return fit("lower-confidence-limit",false,t->tail(t,true)-alpha);
            if(p<alpha)return fit("lower-confidence-limit",true,t->tail(1/t,true)-alpha);return 1;
        }
        double upper(double alpha){
            if(observed==upper)return Double.POSITIVE_INFINITY;double p=tail(1,false);
            if(p<alpha)return fit("upper-confidence-limit",false,t->tail(t,false)-alpha);
            if(p>alpha)return fit("upper-confidence-limit",true,t->tail(1/t,false)-alpha);return 1;
        }
        double[] twoSided(){
            density(1);double observedProbability=probabilities[observed-lower],threshold=observedProbability*(1+1e-7),logThreshold=logDensity[observed-lower]+Math.log1p(1e-7);var selected=new boolean[probabilities.length];var tail=new Sum();double selectedMaximum=Double.NEGATIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;int selectedStates=0;
            for(int i=0;i<probabilities.length;i++){work.add(1);maximum=Math.max(maximum,logDensity[i]);selected[i]=observedProbability==0?logDensity[i]<=logThreshold:probabilities[i]<=threshold;if(selected[i]){selectedStates++;tail.add(probabilities[i]);selectedMaximum=Math.max(selectedMaximum,logDensity[i]);}}
            if(selectedStates==probabilities.length)return new double[]{1,0};
            if(!Double.isFinite(selectedMaximum))throw new ArithmeticException("Fisher minimum-likelihood tail lost its observed table");var selectedSum=new Sum();var total=new Sum();
            for(int i=0;i<logDensity.length;i++){work.add(1);total.add(Math.exp(logDensity[i]-maximum));if(selected[i])selectedSum.add(Math.exp(logDensity[i]-selectedMaximum));}
            double logP=selectedMaximum+Math.log(selectedSum.value)-maximum-Math.log(total.value);if(logP>1e-12||!Double.isFinite(logP))throw new ArithmeticException("Invalid finite Fisher log tail");return new double[]{Math.min(1,tail.value),Math.min(0,logP)};
        }
    }
    /**
     * Test explicitly supplied counts, adding the declared integer pseudocount to
     * all four cells. Conditional inference fixes tested margins. This method
     * never constructs groups, infers overlap, selects associations, or adjusts P.
     *
     * @param counts original integer 2-by-2 population
     * @param options explicit pseudocount, confidence level and limits
     * @param cancellation cooperative cancellation/deadline check
     * @return complete counts, P/log-P, conditional odds estimate, interval and root audit
     */
    public static Result test(Counts counts,Options options,Runnable cancellation){
        if(counts==null||options==null||cancellation==null)throw new IllegalArgumentException("Require Fisher counts,explicit options and cancellation");var work=new Work(options.limits(),cancellation);work.check();counts.total();long add=options.pseudocount();var tested=new Counts(Math.addExact(counts.finerNovel(),add),Math.addExact(counts.finerKnown(),add),Math.addExact(counts.comparatorNovel(),add),Math.addExact(counts.comparatorKnown(),add));
        if(tested.total()>options.limits().maximumPopulation())throw new IllegalArgumentException("Tested Fisher population including pseudocounts exceeds maximumPopulation");var distribution=new Distribution(tested,work);double[] p=distribution.twoSided();double estimate=distribution.estimate(),alpha=(1-options.confidenceLevel())/2,lower=distribution.lower(alpha),upper=distribution.upper(alpha);if(Double.isNaN(estimate)||Double.isNaN(lower)||Double.isNaN(upper)||lower>upper)throw new ArithmeticException("Invalid Fisher conditional estimate/confidence interval");
        var warnings=new ArrayList<String>();warnings.add("DECLARED FISHER TABLE REVIEW: rows are finer/comparator and columns novel/known. Required action: verify all original counts,selection and key aggregation,group overlap and correction-family membership separately; this primitive does not establish independent observations,replication,or historical paper policy.");
        warnings.add("R DEFAULT ROOT PROTOCOL: conditional odds estimates and central confidence limits use the R4.2.3 odds/inverse-odds root protocol with tolerance="+ROOT_TOLERANCE+". Required action: inspect root parameterizations and remaining bracket widths; this finite root tolerance is not a relative accuracy guarantee for odds ratios or their reciprocals.");
        warnings.add("FLOATING-POINT REFERENCE BOUNDARY: the source protocol does not imply bitwise identity to R arithmetic. The frozen reference audit retains11 synthetic P/odds/interval field differences beyond2e-14 relative,all within1e-13;published-count cases pass the strict gate. Required action: preserve original counts,unrounded P/log-P and root diagnostics,and independently review threshold-sensitive findings.");
        if(add>0)warnings.add("EXPLICIT PSEUDOCOUNT: "+add+" added to every cell before fixed-margin inference. Required action: retain original and tested counts and justify this choice; pseudocounts are not observed associations.");
        if(Math.addExact(counts.finerNovel(),counts.finerKnown())==0||Math.addExact(counts.comparatorNovel(),counts.comparatorKnown())==0)warnings.add("EMPTY ORIGINAL GROUP: at least one original row has no observations. Required action: retain its empty status; pseudocount-created observations do not establish a measured group comparison.");
        if(distribution.lower==distribution.upper)warnings.add("DEGENERATE FIXED MARGINS: odds are unidentifiable; source-style boundary estimate and interval are retained. Required action: do not interpret these as an estimated biological effect.");
        if(p[0]==0)warnings.add("FISHER PROBABILITY UNDERFLOW: displayed P is zero but the computed log probability is finite. Required action: preserve counts and finite log-P; this is not an exact zero probability.");
        if(distribution.roots.stream().anyMatch(r->r.bracketWidth()>Math.abs(r.root())*.01))warnings.add("COARSE ROOT RESOLUTION: at least one remaining parameter bracket is large relative to its root. Required action: review the retained inverse/ordinary root audit before interpreting odds/interval precision; no more accurate source reproduction is claimed.");
        work.check();return new Result(counts,tested,options.pseudocount(),p[0],p[1],estimate,lower,upper,options.confidenceLevel(),distribution.logDensity.length,distribution.roots,work.operations,warnings);
    }
    // R_zeroin2 interpolation and stopping protocol, R4.2.3; original GPL source:
    // https://svn.r-project.org/R/tags/R-4-2-3/src/library/stats/src/zeroin.c
    private static Root root(DoubleUnaryOperator function,double ax,double bx,Work work){
        double a=ax,b=bx,fa=function.applyAsDouble(a),fb=function.applyAsDouble(b),c=a,fc=fa;if(!Double.isFinite(fa)||!Double.isFinite(fb))throw new ArithmeticException("Nonfinite Fisher root endpoint");if(fa==0)return new Root(a,0,0);if(fb==0)return new Root(b,0,0);if(Math.signum(fa)==Math.signum(fb))throw new ArithmeticException("Unable to bracket Fisher conditional root under the source protocol");
        for(int iteration=0;iteration<=work.limits.maximumRootIterations();iteration++){
            work.add(1);work.check();double previous=b-a;if(Math.abs(fc)<Math.abs(fb)){a=b;b=c;c=a;fa=fb;fb=fc;fc=fa;}double tolerance=2*Math.ulp(1.0)*Math.abs(b)+ROOT_TOLERANCE/2,step=(c-b)/2;
            if(Math.abs(step)<=tolerance||fb==0)return new Root(b,Math.abs(c-b),iteration);
            if(Math.abs(previous)>=tolerance&&Math.abs(fa)>Math.abs(fb)){
                double p,q,cb=c-b;if(a==c){double t=fb/fa;p=cb*t;q=1-t;}else{q=fa/fc;double t1=fb/fc,t2=fb/fa;p=t2*(cb*q*(q-t1)-(b-a)*(t1-1));q=(q-1)*(t1-1)*(t2-1);}if(p>0)q=-q;else p=-p;if(p<.75*cb*q-Math.abs(tolerance*q)/2&&p<Math.abs(previous*q/2))step=p/q;
            }
            if(Math.abs(step)<tolerance)step=step>0?tolerance:-tolerance;a=b;fa=fb;b+=step;fb=function.applyAsDouble(b);if(!Double.isFinite(fb))throw new ArithmeticException("Nonfinite Fisher root evaluation");if(fb>0&&fc>0||fb<0&&fc<0){c=a;fc=fa;}
        }
        throw new IllegalArgumentException("Fisher root iteration budget exceeded;no unconverged estimate was admitted");
    }
}
