/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Concave finite-mixture weight fitting by EM, with a simplex dual-gap
 * certificate. This fits supplied component likelihoods; it does not learn
 * covariance matrices or select a representative training population.
 */
public final class MashMixtureWeights {
    private MashMixtureWeights() { }
    /** An explicit optimization limit never becomes a convergence claim. */
    public record Options(int maximumIterations,double relativeDualGap,boolean history) {
        public Options {if(maximumIterations<1||maximumIterations>1_000_000||!Double.isFinite(relativeDualGap)||relativeDualGap<=0||relativeDualGap>=1)throw new IllegalArgumentException("Invalid mixture optimization options");}
        public static Options defaults(){return new Options(10000,1e-8,true);}
    }
    /** Complete input, allocation and actual evaluated-cell budgets. */
    public record Limits(int rows,int components,long cells,long evaluatedCells,long estimatedOwnedBytes) {
        public Limits {if(rows<1||components<1||cells<1||evaluatedCells<1||estimatedOwnedBytes<65536)throw new IllegalArgumentException("Invalid mixture optimization capacities");}
        public static Limits defaults(){return new Limits(1_000_000,4096,16_000_000,10_000_000_000L,268_435_456);}
    }
    /** The objective omits the fixed row offsets and the Dirichlet normalizing constant. */
    public record Iteration(int index,double relativeObjective,double relativeDualGap,double maximumWeightChange) { }
    /** Complete fit state, including an explicit nonconverged state when the iteration limit is reached. */
    public record Fit(boolean converged,int iterations,double[] proportions,double logLikelihood,double relativeLogLikelihood,
                      double rowOffsetSum,double relativeObjective,double relativeDualGap,long evaluatedCells,
                      List<Iteration> history,List<String> numericalNotes) {
        public Fit {proportions=proportions.clone();history=List.copyOf(history);numericalNotes=List.copyOf(numericalNotes);}
        @Override public double[] proportions(){return proportions.clone();}
        /** Require before scientific admission; retain the fit state and recovery message on failure. */
        public Fit requireConverged(){if(!converged)throw new IllegalStateException("NONCONVERGED: complete mixture fit reached its iteration limit;retain diagnostics and increase the permitted iteration/work budget or use a separately validated optimizer. No converged model is admitted.");return this;}
    }
    /**
     * Fit every supplied row and component. Dirichlet parameters must be at
     * least one so the objective is concave. Initial proportions must be
     * strictly positive and sum to one; a null initial vector means uniform.
     * Row-specific constants cancel from optimization and are restored in the
     * reported log likelihood. Negative-infinite component log likelihoods
     * represent zero support; an all-zero-support row is rejected.
     */
    public static Fit fit(double[][] logLikelihoods,double[] dirichlet,double[] initial,Options options,Limits limits,Runnable check){
        Objects.requireNonNull(logLikelihoods);Objects.requireNonNull(dirichlet);Objects.requireNonNull(options);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        int rows=logLikelihoods.length,k=dirichlet.length;
        if(rows<1||rows>limits.rows()||k<1||k>limits.components()||Math.multiplyExact((long)rows,k)>limits.cells())throw new IllegalArgumentException("Complete mixture input dimensions exceed capacity");
        long bytes=Math.addExact(65536,Math.addExact(Math.multiplyExact(16L,Math.multiplyExact((long)rows,k)),Math.addExact(256L*k+64L*rows,options.history()?64L*(options.maximumIterations()+1):0)));
        if(bytes>limits.estimatedOwnedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete mixture input/history exceeds declared storage or half the Java heap;no row prefix is fitted");
        double[] pseudo=dirichlet.clone();Sum total=new Sum();total.add(rows);
        for(int i=0;i<k;i++){if(!Double.isFinite(pseudo[i])||pseudo[i]<1)throw new IllegalArgumentException("Require finite Dirichlet parameters >=1 for the concave objective");pseudo[i]-=1;total.add(pseudo[i]);}
        double lambda=total.value();if(!Double.isFinite(lambda))throw new ArithmeticException("Unreportable Dirichlet concentration");
        double[] weights=initial==null?new double[k]:initial.clone();if(initial==null)Arrays.fill(weights,1./k);
        if(weights.length!=k)throw new IllegalArgumentException("Initial proportions differ from complete component count");
        Sum initialSum=new Sum();for(double p:weights){if(!Double.isFinite(p)||p<=0||p>1)throw new IllegalArgumentException("Initial proportions must be strictly positive probabilities");initialSum.add(p);}
        if(Math.abs(initialSum.value()-1)>1e-10)throw new IllegalArgumentException("Initial proportions must sum to one;arbitrary normalization is not inferred");
        for(int i=0;i<k;i++)weights[i]/=initialSum.value();
        var notes=new ArrayList<String>();if(initialSum.value()!=1)notes.add("INITIAL_SIMPLEX_ROUNDOFF: normalized the supplied sum within the explicit1e-10 tolerance.");
        double[][] scores=new double[rows][k];Sum offsets=new Sum();boolean underflow=false;
        for(int r=0;r<rows;r++){
            check.run();double[] source=logLikelihoods[r];if(source==null||source.length!=k)throw new IllegalArgumentException("Ragged complete log-likelihood matrix");double max=Double.NEGATIVE_INFINITY;
            for(double value:source){if(Double.isNaN(value)||value==Double.POSITIVE_INFINITY)throw new IllegalArgumentException("Component log likelihoods cannot be NaN or positive infinity");max=Math.max(max,value);}
            if(!Double.isFinite(max))throw new IllegalArgumentException("An observation has zero support under every component");offsets.add(max);
            for(int i=0;i<k;i++){scores[r][i]=source[i]-max;underflow|=Double.isFinite(source[i])&&!Double.isFinite(scores[r][i]);}
        }
        if(!Double.isFinite(offsets.value()))throw new ArithmeticException("Unreportable complete row-offset sum");
        if(underflow)notes.add("LOG_DIFFERENCE_UNDERFLOW: an extreme finite component difference became negative infinity;its representable responsibility is zero.");
        var history=new ArrayList<Iteration>();Work work=new Work(limits.evaluatedCells());Evaluation current=evaluate(scores,pseudo,weights,lambda,work,check);int iteration=0;
        if(options.history())history.add(new Iteration(0,current.objective(),current.gap(),0));
        while(current.gap()>options.relativeDualGap()&&iteration<options.maximumIterations()){
            check.run();double[] next=new double[k];Sum sum=new Sum();double change=0;
            for(int i=0;i<k;i++){next[i]=(current.counts()[i]+pseudo[i])/lambda;sum.add(next[i]);}
            if(!(sum.value()>0)||!Double.isFinite(sum.value()))throw new ArithmeticException("Invalid complete EM update");
            for(int i=0;i<k;i++){next[i]/=sum.value();change=Math.max(change,Math.abs(next[i]-weights[i]));}
            Evaluation candidate=evaluate(scores,pseudo,next,lambda,work,check);
            double rounding=64*Math.ulp(Math.max(1,Math.max(Math.abs(current.objective()),Math.abs(candidate.objective()))))*Math.max(1,Math.log(rows+k)/Math.log(2));
            if(candidate.objective()<current.objective()-rounding)throw new ArithmeticException("EM penalized objective decreased beyond its explicit accumulation tolerance;retain diagnostics and do not admit the fit");
            if(candidate.objective()<current.objective()&&notes.stream().noneMatch(s->s.startsWith("OBJECTIVE_ROUNDOFF:")))notes.add("OBJECTIVE_ROUNDOFF: an EM objective decrease occurred within the explicit floating-point accumulation tolerance;the dual-gap convergence gate remains required.");
            current=candidate;weights=next;iteration++;
            if(options.history())history.add(new Iteration(iteration,current.objective(),current.gap(),change));
        }
        boolean converged=current.gap()<=options.relativeDualGap();if(!converged)notes.add("NONCONVERGED: iteration limit reached;increase permitted iteration/work capacity or use a separately validated optimizer. No converged model is admitted.");
        double likelihood=offsets.value()+current.likelihood();if(!Double.isFinite(likelihood))throw new ArithmeticException("Unreportable full mixture log likelihood");
        check.run();return new Fit(converged,iteration,weights,likelihood,current.likelihood(),offsets.value(),current.objective(),current.gap(),work.used,history,notes);
    }
    private record Evaluation(double likelihood,double objective,double gap,double[] counts) { }
    private static Evaluation evaluate(double[][] scores,double[] pseudo,double[] pi,double lambda,Work work,Runnable check){
        int k=pi.length;work.add(Math.multiplyExact(2L*k,scores.length));double[] logPi=new double[k],counts=new double[k],logGradient=new double[k];Arrays.fill(logGradient,Double.NEGATIVE_INFINITY);
        for(int i=0;i<k;i++)logPi[i]=pi[i]==0?Double.NEGATIVE_INFINITY:Math.log(pi[i]);Sum likelihood=new Sum();
        for(double[] row:scores){
            check.run();double max=Double.NEGATIVE_INFINITY;for(int i=0;i<k;i++)max=Math.max(max,row[i]+logPi[i]);if(!Double.isFinite(max))throw new ArithmeticException("A mixture update lost all support for a complete observation");
            Sum norm=new Sum();for(int i=0;i<k;i++)norm.add(Math.exp(row[i]+logPi[i]-max));double denominator=max+Math.log(norm.value());likelihood.add(denominator);
            for(int i=0;i<k;i++){counts[i]+=Math.exp(row[i]+logPi[i]-denominator);logGradient[i]=logAdd(logGradient[i],row[i]-denominator);}
        }
        Sum penalty=new Sum();double maximum=Double.NEGATIVE_INFINITY;
        for(int i=0;i<k;i++){
            if(pseudo[i]>0){if(pi[i]==0)throw new ArithmeticException("A positive Dirichlet pseudocount lost its component support");penalty.add(pseudo[i]*logPi[i]);logGradient[i]=logAdd(logGradient[i],Math.log(pseudo[i])-logPi[i]);}
            maximum=Math.max(maximum,logGradient[i]);
        }
        double objective=likelihood.value()+penalty.value();if(!Double.isFinite(objective))throw new ArithmeticException("Unreportable complete penalized mixture objective");
        // Concavity gives F(optimum)-F(pi) <= max(gradient)-pi'gradient.
        // Euler's identity gives pi'gradient=N+sum(prior-1)=lambda.
        double gap=Math.max(0,Math.expm1(maximum-Math.log(lambda)));return new Evaluation(likelihood.value(),objective,gap,counts);
    }
    private static double logAdd(double x,double y){if(x==Double.NEGATIVE_INFINITY)return y;if(y==Double.NEGATIVE_INFINITY)return x;double max=Math.max(x,y);return max+Math.log1p(Math.exp(Math.min(x,y)-max));}
    private static final class Sum {private double value,correction;void add(double x){double adjusted=x-correction,next=value+adjusted;correction=(next-value)-adjusted;value=next;}double value(){return value;}}
    private static final class Work {private final long limit;private long used;Work(long limit){this.limit=limit;}void add(long amount){used=Math.addExact(used,amount);if(used>limit)throw new IllegalArgumentException("Complete mixture optimization exceeds evaluated-cell capacity;no partial fit is admitted");}}
}
