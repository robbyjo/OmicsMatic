/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

/**
 * Streaming binomial ancestry-mixture likelihood on supplied Q and P estimates.
 * P describes the counted allele, which must agree with the supplied calls.
 * This class does not infer ancestry labels, fit a multi-component model, choose K,
 * or certify calibration. Printed-parameter intervals are retained explicitly.
 */
public final class AncestryMixtureLikelihood {
    private AncestryMixtureLikelihood() { }

    /** Single-component binomial maximum-likelihood estimate.
     * @param observedSamples samples with nonmissing calls
     * @param countedAlleles copies of the counted allele
     * @param observedAlleles total observed allele copies
     * @param frequency maximum-likelihood counted-allele frequency
     * @param logLikelihood likelihood without the constant binomial coefficient
     */
    public record SingleComponent(long observedSamples,long countedAlleles,long observedAlleles,
                                  double frequency,double logLikelihood) { }

    /**
     * Estimate the one-component model. Calls use PLINK-style 0/1/2 copy coding;
     * a genuinely haploid call must be doubled to 0/2 and uses ploidy1 here.
     * A pseudohaploid call analyzed with ploidy2 is a distinct, explicit model.
     * @param calls counted-allele calls; NaN means missing
     * @param ploidy model ploidy, 1 or 2
     * @return exact-count single-component estimate
     */
    public static SingleComponent estimateOneComponent(double[] calls,int ploidy) {
        checkPloidy(ploidy);long n=0,count=0,total=0;
        for(double call:calls) {
            if(Double.isNaN(call))continue;double g=alleles(call,ploidy);
            n=Math.addExact(n,1);count=Math.addExact(count,(long)g);total=Math.addExact(total,ploidy);
        }
        if(n==0)throw new IllegalArgumentException("All calls are missing; no allele-frequency estimate exists");
        double p=(double)count/total;return new SingleComponent(n,count,total,p,term(count,total,p));
    }

    /** Accumulated likelihood and conservative printed-parameter uncertainty.
     * @param markers admitted marker rows
     * @param observedCells nonmissing genotype cells
     * @param missingCells omitted missing genotype cells
     * @param observedAlleles total model allele copies
     * @param countedAlleles total counted-allele copies
     * @param logLikelihood likelihood at supplied rounded values, possibly negative infinity
     * @param likelihoodLower lower printed-parameter bound, possibly negative infinity
     * @param likelihoodUpper upper printed-parameter bound
     * @param objective likelihood minus the declared Q sparsity penalty
     * @param objectiveLower lower penalized bound
     * @param objectiveUpper upper penalized bound
     * @param precisionUnboundedCells cells whose lower likelihood bound is negative infinity
     * @param centralProbabilityClips rounded central probabilities clipped to their valid range
     */
    public record Summary(long markers,long observedCells,long missingCells,long observedAlleles,long countedAlleles,
                          double logLikelihood,double likelihoodLower,double likelihoodUpper,double objective,
                          double objectiveLower,double objectiveUpper,long precisionUnboundedCells,long centralProbabilityClips) {
        /** Whether a reported unpenalized likelihood intersects its printed-precision interval.
         * @param reported finite reported likelihood
         * @param absoluteError nonnegative rounding/error radius of that report
         * @return whether the intervals intersect
         */
        public boolean containsLikelihood(double reported,double absoluteError) {
            if(!Double.isFinite(reported)||!Double.isFinite(absoluteError)||absoluteError<0)
                throw new IllegalArgumentException("Require finite reported likelihood and nonnegative precision");
            return reported+absoluteError>=likelihoodLower&&reported-absoluteError<=likelihoodUpper;
        }
    }

    /** Reusable marker-stream evaluator; Q is copied and never renormalized. */
    public static final class Evaluator {
        private final int samples,components;
        private final double[] q,lowQ,highQ;
        private final boolean binomialCoefficient;
        private final Sum likelihood=new Sum(),lower=new Sum(),upper=new Sum();
        private long markers,observed,missing,observedAlleles,counted,unbounded,clips;

        /**
         * @param q sample-major Q matrix
         * @param samples sample count
         * @param components component count, at most1024
         * @param absoluteQError absolute printed-Q error radius
         * @param includeBinomialCoefficient whether to include log(2) for diploid heterozygotes
         */
        public Evaluator(double[] q,int samples,int components,double absoluteQError,boolean includeBinomialCoefficient) {
            if(samples<1||components<1||components>1024||q.length!=Math.multiplyExact(samples,components))
                throw new IllegalArgumentException("Invalid bounded Q dimensions");
            error(absoluteQError);this.samples=samples;this.components=components;this.q=q.clone();
            this.lowQ=new double[q.length];this.highQ=new double[q.length];this.binomialCoefficient=includeBinomialCoefficient;
            for(int i=0;i<samples;i++) {
                double lo=0,hi=0;
                for(int k=0;k<components;k++) {
                    int j=i*components+k;probability(q[j]);lowQ[j]=downProbability(q[j]-absoluteQError);highQ[j]=upProbability(q[j]+absoluteQError);lo+=lowQ[j];hi+=highQ[j];
                }
                double guard=64*components*Math.ulp(1.0);
                if(lo>1+guard||hi<1-guard)throw new IllegalArgumentException("Q row cannot sum to one within declared precision: "+i);
            }
        }

        /** Admit one marker with a common declared ploidy.
         * @param calls sample-ordered counted-allele calls in 0/1/2 coding; NaN is missing
         * @param p component-ordered counted-allele frequencies
         * @param ploidy model ploidy, 1 or 2
         * @param absolutePError absolute printed-P error radius
         */
        public void addMarker(double[] calls,double[] p,int ploidy,double absolutePError) {
            checkPloidy(ploidy);add(calls,p,ploidy,null,absolutePError);
        }

        /** Admit one marker with explicitly supplied sample-specific ploidies.
         * @param calls sample-ordered counted-allele calls in 0/1/2 coding; NaN is missing
         * @param p component-ordered counted-allele frequencies
         * @param ploidies sample-ordered model ploidies, each1 or2
         * @param absolutePError absolute printed-P error radius
         */
        public void addMarker(double[] calls,double[] p,int[] ploidies,double absolutePError) {
            if(ploidies.length!=samples)throw new IllegalArgumentException("Ploidy/sample dimension changed");
            for(int h:ploidies)checkPloidy(h);add(calls,p,0,ploidies,absolutePError);
        }

        private void add(double[] calls,double[] p,int fixed,int[] ploidies,double error) {
            if(calls.length!=samples||p.length!=components)throw new IllegalArgumentException("Marker sample/component dimension changed");
            error(error);double[] lp=new double[components],hp=new double[components];
            for(int k=0;k<components;k++){probability(p[k]);lp[k]=downProbability(p[k]-error);hp[k]=upProbability(p[k]+error);}
            // Validate before changing the accumulator, including genotypes after any missing cell.
            for(int i=0;i<samples;i++)if(!Double.isNaN(calls[i]))alleles(calls[i],ploidies==null?fixed:ploidies[i]);
            markers=Math.addExact(markers,1);
            for(int i=0;i<samples;i++) {
                if(Double.isNaN(calls[i])){missing=Math.addExact(missing,1);continue;}
                int h=ploidies==null?fixed:ploidies[i];double g=alleles(calls[i],h),f=0,lo=0,hi=0;
                for(int k=0;k<components;k++){int j=i*components+k;f+=q[j]*p[k];lo=Math.nextDown(lo+lowQ[j]*lp[k]);hi=Math.nextUp(hi+highQ[j]*hp[k]);}
                lo=Math.max(0,lo);hi=Math.min(1,hi);if(f<0||f>1)clips=Math.addExact(clips,1);f=Math.max(0,Math.min(1,f));
                double center=term(g,h,f),a=term(g,h,lo),b=term(g,h,hi),l=Math.min(a,b),maximum=g/h;
                double u=term(g,h,Math.max(lo,Math.min(hi,maximum))),constant=binomialCoefficient&&h==2&&g==1?Math.log(2):0;
                if(l==Double.NEGATIVE_INFINITY)unbounded=Math.addExact(unbounded,1);
                likelihood.add(center+constant);lower.add(outward(l+constant,false));upper.add(outward(u+constant,true));
                observed=Math.addExact(observed,1);observedAlleles=Math.addExact(observedAlleles,h);counted=Math.addExact(counted,(long)g);
            }
        }

        /**
         * Finish with the declared ADMIXTURE-style Q sparsity penalty.
         * A zero lambda reports the ordinary unpenalized model.
         * @param lambda nonnegative finite penalty strength
         * @param epsilon finite penalty scale strictly between0 and1
         * @return immutable likelihood, precision bounds and counts
         */
        public Summary summary(double lambda,double epsilon) {
            if(!Double.isFinite(lambda)||lambda<0||!Double.isFinite(epsilon)||epsilon<=0||epsilon>=1)
                throw new IllegalArgumentException("Declare finite lambda>=0 and epsilon in(0,1)");
            if(observed==0)throw new IllegalStateException("No observed genotype cells were admitted");
            Sum penalty=new Sum(),penaltyLow=new Sum(),penaltyHigh=new Sum();double denominator=logRatio(1,epsilon);
            if(lambda>0)for(int j=0;j<q.length;j++){penalty.add(lambda*(logRatio(q[j],epsilon)/denominator));penaltyLow.add(lambda*(logRatio(lowQ[j],epsilon)/denominator));penaltyHigh.add(lambda*(logRatio(highQ[j],epsilon)/denominator));}
            double l=outward(lower.value,false),u=outward(upper.value,true);
            return new Summary(markers,observed,missing,observedAlleles,counted,likelihood.value,l,u,likelihood.value-penalty.value,
                    outward(l-penaltyHigh.value,false),outward(u-penaltyLow.value,true),unbounded,clips);
        }
    }

    private static double term(double g,double h,double p){return (g==0?0:g*Math.log(p))+(h==g?0:(h-g)*Math.log1p(-p));}
    private static double logRatio(double x,double epsilon){if(x==0)return 0;double ratio=x/epsilon;return Double.isFinite(ratio)?Math.log1p(ratio):Math.log(x)+Math.log1p(epsilon/x)-Math.log(epsilon);}
    private static void probability(double p){if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("Require finite allele/mixture probabilities in[0,1]");}
    private static void error(double e){if(!Double.isFinite(e)||e<0||e>.01)throw new IllegalArgumentException("Declare a finite printed-parameter radius in[0,0.01]");}
    private static void checkPloidy(int p){if(p!=1&&p!=2)throw new IllegalArgumentException("Declare model ploidy1 or2");}
    private static double alleles(double call,int ploidy){if(call!=0&&call!=1&&call!=2||ploidy==1&&call==1)throw new IllegalArgumentException("Require 0/1/2 hard calls;haploid calls must be homozygously coded0/2");return ploidy==1?call/2:call;}
    private static double downProbability(double x){return Math.max(0,Math.min(1,Math.nextDown(x)));}
    private static double upProbability(double x){return Math.max(0,Math.min(1,Math.nextUp(x)));}
    private static double outward(double value,boolean up){if(!Double.isFinite(value))return value;double guard=32*Math.ulp(Math.abs(value));return up?value+guard:value-guard;}
    private static final class Sum {
        double value,correction;
        void add(double x){if(Double.isNaN(x)||x==Double.POSITIVE_INFINITY)throw new ArithmeticException("Mixture likelihood/penalty exceeds finite arithmetic range");if(value==Double.NEGATIVE_INFINITY)return;if(x==Double.NEGATIVE_INFINITY){value=x;correction=0;return;}double y=x-correction,t=value+y;if(!Double.isFinite(t))throw new ArithmeticException("Mixture likelihood/penalty sum exceeds finite arithmetic range");correction=(t-value)-y;value=t;}
    }
}
