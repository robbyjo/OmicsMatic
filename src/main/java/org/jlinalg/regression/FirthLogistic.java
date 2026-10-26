/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.regression;

import jdistlib.ChiSquare;
import jdistlib.accelerator.CholeskyFactor;

/** Independent Bernoulli logit regression with the Jeffreys/Firth penalty.
 * No ridge, genotype rounding, ML-null prerequisite, population correction or
 * relatedness adjustment is implicit. Constrained tests retain the full design
 * in the penalty; deleting a tested column defines a different objective. */
public final class FirthLogistic {
    private FirthLogistic() { }
    /** Step and coefficient tolerance use max-absolute-value scaled design coordinates. */
    public record Options(int maximumIterations,int maximumStepHalvings,double tolerance,double maximumStep) {
        public Options {
            if(maximumIterations<1||maximumStepHalvings<1||!Double.isFinite(tolerance)||tolerance<=0||tolerance>=.1
                    ||!Double.isFinite(maximumStep)||maximumStep<=0)throw new IllegalArgumentException("Invalid Firth fitting controls");
        }
        public static Options defaults(){return new Options(300,40,1e-8,5);}
    }
    public record Fit(double[] coefficients,double[] covariance,double penalizedLogLikelihood,
            double logLikelihood,boolean converged,int iterations,String reason,double maximumStandardizedScore) {
        public Fit { coefficients=coefficients.clone();covariance=covariance.clone(); }
        @Override public double[] coefficients(){return coefficients.clone();}
        @Override public double[] covariance(){return covariance.clone();}
    }
    public record Test(Fit full,Fit constrained,int coefficient,double nullValue,double likelihoodRatio,
            double pValue,double logPValue,String status) {
        public boolean converged(){return full.converged()&&constrained.converged()&&status.equals("converged");}
        public double beta(){return full.coefficients[coefficient];}
        /** Inverse ordinary Fisher information at the penalized estimate; not LRT-derived. */
        public double standardError(){return Math.sqrt(full.covariance[coefficient*full.coefficients.length+coefficient]);}
    }
    public static Fit fit(double[] response,double[][] design,double[] offset,Options options) {
        return new Problem(response,design,offset,options).fit(-1,0,null);
    }
    public static Test test(double[] response,double[][] design,double[] offset,int coefficient,double nullValue,Options options) {
        Problem problem=new Problem(response,design,offset,options);
        if(coefficient<0||coefficient>=problem.p||!Double.isFinite(nullValue))throw new IllegalArgumentException("A finite coefficient hypothesis is required");
        Fit full=problem.fit(-1,0,null);
        Fit constrained=problem.fit(coefficient,nullValue,full.coefficients);
        if(!full.converged||!constrained.converged)return new Test(full,constrained,coefficient,nullValue,Double.NaN,Double.NaN,Double.NaN,"nonconverged");
        if(full.penalizedLogLikelihood+1e-8<constrained.penalizedLogLikelihood) {
            full=problem.fit(-1,0,constrained.coefficients);
            if(!full.converged||full.penalizedLogLikelihood+1e-8<constrained.penalizedLogLikelihood)
                return new Test(full,constrained,coefficient,nullValue,Double.NaN,Double.NaN,Double.NaN,"full_fit_below_constrained");
        }
        double lr=Math.max(0,2*(full.penalizedLogLikelihood-constrained.penalizedLogLikelihood));
        double logP=ChiSquare.cumulative(lr,1,false,true);
        return new Test(full,constrained,coefficient,nullValue,lr,Math.exp(logP),logP,"converged");
    }
    private static final class Problem {
        final double[] y,offset,scales;final double[][] x;final int n,p;final Options options;final double logScale;
        Problem(double[] response,double[][] design,double[] offsets,Options controls) {
            if(response==null||design==null||response.length==0||design.length!=response.length||design[0]==null||design[0].length==0||controls==null)
                throw new IllegalArgumentException("Finite Bernoulli responses, full-rank design and fitting controls are required");
            n=response.length;p=design[0].length;if(n<p)throw new IllegalArgumentException("Firth design has more columns than observations");
            if(offsets!=null&&offsets.length!=n)throw new IllegalArgumentException("Offsets must align with observations");
            y=response.clone();offset=offsets==null?new double[n]:offsets.clone();options=controls;x=new double[n][p];scales=new double[p];int cases=0;
            for(int i=0;i<n;i++) {
                if(y[i]!=0&&y[i]!=1||design[i]==null||design[i].length!=p||!Double.isFinite(offset[i]))throw new IllegalArgumentException("Independent individual 0/1 responses, finite offsets and a rectangular design are required");
                cases+=y[i]==1?1:0;
                for(int j=0;j<p;j++){if(!Double.isFinite(design[i][j]))throw new IllegalArgumentException("Firth design must be finite");scales[j]=Math.max(scales[j],Math.abs(design[i][j]));}
            }
            if(cases==0||cases==n)throw new IllegalArgumentException("Both outcome classes are required");
            double correction=0;
            for(int j=0;j<p;j++){if(!(scales[j]>0))throw new IllegalArgumentException("Firth design is rank deficient");correction+=Math.log(scales[j]);for(int i=0;i<n;i++)x[i][j]=design[i][j]/scales[j];}
            logScale=correction;
            // Validate rank at finite probabilities before any optimizer changes.
            state(new double[p]);
        }
        Fit fit(int fixed,double value,double[] initial) {
            double[] beta=new double[p];if(initial!=null)for(int j=0;j<p;j++)beta[j]=initial[j]*scales[j];
            else {
                boolean intercept=true;for(int i=1;i<n;i++)intercept&=x[i][0]==x[0][0];
                if(intercept&&x[0][0]!=0){double sum=0;for(double v:y)sum+=v;beta[0]=Math.log((sum+.5)/(n-sum+.5))/x[0][0];}
            }
            if(fixed>=0)beta[fixed]=value*scales[fixed];
            State current=state(beta);int free=p-(fixed>=0?1:0);int[] indexes=new int[free];for(int j=0,k=0;j<p;j++)if(j!=fixed)indexes[k++]=j;
            double score=0;int iteration=0;String reason="iteration_limit";boolean converged=false;
            for(;iteration<options.maximumIterations;iteration++) {
                double[] sub=new double[free*free],gradient=new double[free];score=0;
                for(int a=0;a<free;a++){int j=indexes[a];gradient[a]=current.score[j];score=Math.max(score,Math.abs(gradient[a])/Math.sqrt(current.information[j*p+j]));for(int b=0;b<free;b++)sub[a*free+b]=current.information[j*p+indexes[b]];}
                double[] step=free==0?new double[0]:factor(sub,free).solve(gradient);double largest=0;
                for(double s:step)largest=Math.max(largest,Math.abs(s));
                if(score<=options.tolerance&&largest<=options.tolerance){converged=true;reason="adjusted_score_and_step";break;}
                double multiplier=largest>options.maximumStep?options.maximumStep/largest:1;
                boolean accepted=false;
                for(int half=0;half<options.maximumStepHalvings;half++,multiplier*=.5) {
                    double[] candidate=beta.clone();for(int a=0;a<free;a++)candidate[indexes[a]]+=multiplier*step[a];
                    try {
                        State trial=state(candidate);
                        if(trial.penalized>=current.penalized-1e-12){beta=candidate;current=trial;accepted=true;break;}
                    }catch(ArithmeticException numerical){ /* Reject a singular/overflowing trial, never ridge-repair it. */ }
                }
                if(!accepted){reason="step_halving_limit";break;}
            }
            double[] coefficients=beta.clone(),covariance=current.inverse.clone();
            for(int j=0;j<p;j++) {
                coefficients[j]/=scales[j];if(!Double.isFinite(coefficients[j]))throw new ArithmeticException("Firth coefficient is unrepresentable in input units");
                for(int k=0;k<p;k++){covariance[j*p+k]=covariance[j*p+k]/scales[j]/scales[k];if(!Double.isFinite(covariance[j*p+k]))throw new ArithmeticException("Firth covariance is unrepresentable in input units");}
                if(!(covariance[j*p+j]>0))throw new ArithmeticException("Firth variance is unrepresentable in input units");
            }
            return new Fit(coefficients,covariance,current.penalized+logScale,current.logLikelihood,converged,iteration,reason,score);
        }
        State state(double[] beta) {
            double[] probability=new double[n],weight=new double[n],information=new double[p*p];double ll=0;
            for(int i=0;i<n;i++) {
                double eta=offset[i];for(int j=0;j<p;j++)eta=Math.fma(x[i][j],beta[j],eta);
                if(!Double.isFinite(eta))throw new ArithmeticException("Nonfinite Firth linear predictor");
                double e=Math.exp(-Math.abs(eta));probability[i]=eta>=0?1/(1+e):e/(1+e);weight[i]=e/((1+e)*(1+e));
                ll-=y[i]==1?softplus(-eta):softplus(eta);
                for(int j=0;j<p;j++)for(int k=0;k<=j;k++)information[j*p+k]+=weight[i]*x[i][j]*x[i][k];
            }
            for(int j=0;j<p;j++)for(int k=0;k<j;k++)information[k*p+j]=information[j*p+k];
            CholeskyFactor chol=factor(information,p);double[] identity=new double[p*p];for(int j=0;j<p;j++)identity[j*p+j]=1;
            double[] inverse=chol.solve(identity,p),score=new double[p];
            for(int i=0;i<n;i++) {
                double h=0;for(int j=0;j<p;j++)for(int k=0;k<p;k++)h+=weight[i]*x[i][j]*inverse[j*p+k]*x[i][k];
                if(!Double.isFinite(h)||h < -1e-7||h>1+1e-7)throw new ArithmeticException("Invalid Firth leverage; information is ill-conditioned");
                h=Math.max(0,Math.min(1,h));double residual=y[i]-probability[i]+h*(.5-probability[i]);
                for(int j=0;j<p;j++)score[j]+=x[i][j]*residual;
            }
            return new State(information,inverse,score,ll+.5*chol.logDeterminant(),ll);
        }
    }
    private record State(double[] information,double[] inverse,double[] score,double penalized,double logLikelihood) { }
    private static double softplus(double x){return Math.max(0,x)+Math.log1p(Math.exp(-Math.abs(x)));}
    private static CholeskyFactor factor(double[] a,int p) {
        double[] lower=new double[p*p];
        for(int i=0;i<p;i++)for(int j=0;j<=i;j++) {
            double value=a[i*p+j];for(int k=0;k<j;k++)value-=lower[i*p+k]*lower[j*p+k];
            if(i==j){if(!(value>1e-12*a[i*p+i])||!Double.isFinite(value))throw new ArithmeticException("Firth information is singular or ill-conditioned");lower[i*p+j]=Math.sqrt(value);}
            else lower[i*p+j]=value/lower[j*p+j];
        }
        return new CholeskyFactor(p,lower);
    }
}
