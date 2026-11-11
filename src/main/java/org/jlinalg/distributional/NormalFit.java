/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.distributional;

import java.util.*;
import jdistlib.Normal;

/** Unweighted analytical normal MLE and explicit normal-tail/BH families.
 * This does not replay fitdistrplus's numerical optimizer or choose a paper's estimator. */
public final class NormalFit {
    private NormalFit() { }
    public enum Tail { UPPER, LOWER, TWO_SIDED }
    public record Fit(int observations,double mean,double standardDeviation,double variance,double logLikelihood,double aic,double bic) { }
    public record Test(double value,double pValue,double logPValue,double qValue,double logQValue,boolean meetsCutoff) { }
    public static final class Budget {
        private final long maximum; private long used;
        public Budget(long maximum){if(maximum<1)throw new IllegalArgumentException("Require positive operation budget");this.maximum=maximum;}
        public long used(){return used;}
        private void use(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Normal fit interrupted");if(used==maximum)throw new IllegalStateException("Normal-fit operation budget exceeded");used++;}
    }
    private static final class Sum {
        double total,correction;
        void add(double value){double next=total+value;correction+=Math.abs(total)>=Math.abs(value)?(total-next)+value:(value-next)+total;total=next;}
        double value(){return total+correction;}
    }
    public static Fit fit(double[] data){return fit(data,new Budget(Long.MAX_VALUE));}
    public static Fit fit(double[] data,Budget budget){
        Objects.requireNonNull(data,"data");Objects.requireNonNull(budget,"budget");
        if(data.length<2||data.length>10000000)throw new IllegalArgumentException("Require 2..10000000 complete observations");
        Sum first=new Sum();for(double value:data){budget.use();finite(value,"Every observation must be finite; missing values are not omitted");first.add(value);}
        double mean=first.value()/data.length;finite(mean,"Mean is not representable");
        Sum residual=new Sum();for(double value:data){budget.use();residual.add(value-mean);}mean+=residual.value()/data.length;finite(mean,"Mean is not representable");
        Sum squares=new Sum();for(double value:data){budget.use();double difference=value-mean;squares.add(difference*difference);}
        double variance=squares.value()/data.length;
        if(!Double.isFinite(variance)||variance<=0)throw new IllegalArgumentException("Require positive finite representable population variance; no constant-data or scaling substitution");
        double sd=Math.sqrt(variance);finite(sd,"Standard deviation is not representable");
        Sum likelihood=new Sum();for(double value:data){budget.use();double term=Normal.density(value,mean,sd,true);finite(term,"Normal log density is not representable");likelihood.add(term);}
        double loglik=likelihood.value(),aic=-2*loglik+4,bic=-2*loglik+2*Math.log(data.length);
        finite(loglik,"Log likelihood is not representable");finite(aic,"AIC is not representable");finite(bic,"BIC is not representable");
        return new Fit(data.length,mean,sd,variance,loglik,aic,bic);
    }
    public static List<Test> tests(Fit fit,double[] values,Tail tail,double cutoff,boolean inclusive){return tests(fit,values,tail,cutoff,inclusive,new Budget(Long.MAX_VALUE));}
    public static List<Test> tests(Fit fit,double[] values,Tail tail,double cutoff,boolean inclusive,Budget budget){
        Objects.requireNonNull(fit,"fit");Objects.requireNonNull(values,"values");Objects.requireNonNull(tail,"tail");Objects.requireNonNull(budget,"budget");
        if(!Double.isFinite(fit.mean)||!Double.isFinite(fit.standardDeviation)||fit.standardDeviation<=0)throw new IllegalArgumentException("Require finite normal parameters with positive SD");
        if(values.length<1||values.length>10000000||!Double.isFinite(cutoff)||cutoff<=0||cutoff>=1)throw new IllegalArgumentException("Require a complete nonempty family and 0<cutoff<1");
        int n=values.length;double[] p=new double[n],logp=new double[n];Integer[] order=new Integer[n];
        for(int i=0;i<n;i++){
            budget.use();finite(values[i],"Every tested value must be finite; missing values are not omitted");order[i]=i;
            if(tail==Tail.TWO_SIDED){double z=-Math.abs((values[i]-fit.mean)/fit.standardDeviation);p[i]=Math.min(1,2*Normal.cumulative(z,0,1,true,false));logp[i]=Math.min(0,Math.log(2)+Normal.cumulative(z,0,1,true,true));}
            else{boolean lower=tail==Tail.LOWER;p[i]=Normal.cumulative(values[i],fit.mean,fit.standardDeviation,lower,false);logp[i]=Normal.cumulative(values[i],fit.mean,fit.standardDeviation,lower,true);}
            if(!Double.isFinite(p[i])||p[i]<0||p[i]>1||Double.isNaN(logp[i])||logp[i]>0)throw new IllegalArgumentException("Invalid normal tail result");
        }
        Arrays.sort(order,(i,j)->{budget.use();return Double.compare(p[i],p[j]);});
        double[] q=new double[n],logq=new double[n];double minimum=1;
        for(int at=n-1;at>=0;at--){budget.use();int i=order[at];minimum=Math.min(minimum,((double)n/(at+1))*p[i]);q[i]=minimum;}
        Arrays.sort(order,(i,j)->{budget.use();return Double.compare(logp[i],logp[j]);});minimum=0;
        for(int at=n-1;at>=0;at--){budget.use();int i=order[at];minimum=Math.min(minimum,Math.log(n)-Math.log(at+1)+logp[i]);logq[i]=minimum;}
        var results=new ArrayList<Test>(n);for(int i=0;i<n;i++){budget.use();results.add(new Test(values[i],p[i],logp[i],q[i],logq[i],inclusive?q[i]<=cutoff:q[i]<cutoff));}
        return List.copyOf(results);
    }
    private static void finite(double value,String message){if(!Double.isFinite(value))throw new IllegalArgumentException(message);}
}
