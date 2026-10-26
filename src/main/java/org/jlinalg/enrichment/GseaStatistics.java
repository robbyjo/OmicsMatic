/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Native preranked GSEA arithmetic, separate from permutation-based inference.
 *
 * <p>The integer-weight preparation and hit extrema follow fgsea 1.38.0.
 * Explicit extrema-tie conventions distinguish fgsea's zero score from the
 * negative-extremum convention of enrichit's running-score helper. A running
 * score alone is neither a normalized enrichment score nor a P-value.</p>
 */
public final class GseaStatistics {
    private GseaStatistics() { }
    /** Tail of the ranked enrichment walk. */
    public enum Tail { STANDARD, POSITIVE, NEGATIVE }
    /** Treatment of equally large positive and negative extrema. */
    public enum ExtremumTie { ZERO, NEGATIVE, POSITIVE }
    /** Multiple-testing methods supported by the independent arithmetic audit. */
    public enum Adjustment { BH, BY, HOLM, BONFERRONI, NONE }

    /** Immutable validated weights for repeated walks without rescanning the full universe. */
    public static final class PreparedWeights {
        private final double[] values;
        private PreparedWeights(double[] supplied) {
            values=supplied.clone();
            if(values.length<2)throw new IllegalArgumentException("At least two ranked weights are required");
            for(double value:values)if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("GSEA weights must be finite and nonnegative");
        }
        /** Indexed O(selected log selected) score with the declared tail/tie convention. */
        public Walk walk(int[] selected,Tail tail,ExtremumTie tie){return GseaStatistics.walk(values,selected,tail,tie,false);}
        /** Dense O(universe) cumulative score with enrichit running-helper semantics. */
        public Walk rawWalk(int[] selected){return GseaStatistics.rawWalk(values,selected,false);}
    }
    /** Freeze a finite nonnegative weight vector for multiple independent term walks. */
    public static PreparedWeights prepareWeights(double[] weights){return new PreparedWeights(weights);}

    /** Hit extrema and exact ranked positions; all returned arrays are defensive copies. */
    public record Walk(double score,double maximum,double minimum,int extremum,
            int[] leadingEdge,double[] tops,double[] bottoms,boolean zeroHitWeight,boolean uniformZeroHitWeights) {
        public Walk { leadingEdge=leadingEdge.clone();tops=tops.clone();bottoms=bottoms.clone(); }
        @Override public int[] leadingEdge(){return leadingEdge.clone();}
        @Override public double[] tops(){return tops.clone();}
        @Override public double[] bottoms(){return bottoms.clone();}
    }

    /**
     * Prepare fgsea-style integer weights from an already descending ranking.
     * Weights are abs(statistic)^exponent, scaled to about 2^30 and rounded to
     * nearest even. Nonfinite power/sum or all-zero mass is rejected, rather
     * than silently changing the supplied statistic or exponent.
     */
    public static double[] integerWeights(double[] ranking,double exponent) {
        if(ranking.length<2||!Double.isFinite(exponent)||exponent<0)
            throw new IllegalArgumentException("Require at least two finite ranked statistics and a nonnegative finite exponent");
        double[] result=new double[ranking.length];double total=0;
        for(int i=0;i<ranking.length;i++) {
            if(!Double.isFinite(ranking[i])||i>0&&ranking[i]>ranking[i-1])
                throw new IllegalArgumentException("Ranking must be finite and descending");
            result[i]=Math.pow(Math.abs(ranking[i]),exponent);total+=result[i];
        }
        if(!(total>0)||!Double.isFinite(total))
            throw new IllegalArgumentException("GSEA power weights have zero/nonfinite total; revise the declared ranking/exponent without silently truncating values");
        double coefficient=0x1.0p30/total;
        if(coefficient>=1)coefficient=Math.floor(coefficient);
        for(int i=0;i<result.length;i++) {
            result[i]=Math.rint(result[i]*coefficient);
            if(!Double.isFinite(result[i])||result[i]<0||result[i]>Integer.MAX_VALUE)
                throw new IllegalArgumentException("GSEA integer weight is outside the original signed-32-bit representation");
        }
        return result;
    }

    /**
     * Compute a weighted running score for unique zero-based selected positions.
     * Zero hit-weight mass uses fgsea's explicit uniform-hit fallback. A set
     * covering the whole ranking has no complement and is undefined.
     */
    public static Walk walk(double[] weights,int[] selected,Tail tail,ExtremumTie tie) {
        return walk(weights,selected,tail,tie,true);
    }
    private static Walk walk(double[] weights,int[] selected,Tail tail,ExtremumTie tie,boolean checkWeights) {
        if(weights.length<2||selected.length<1||selected.length>=weights.length||tail==null||tie==null)
            throw new IllegalArgumentException("GSEA needs a nonempty proper ranked subset and explicit tail/tie conventions");
        if(checkWeights)for(double value:weights)if(!Double.isFinite(value)||value<0)
            throw new IllegalArgumentException("GSEA weights must be finite and nonnegative");
        int[] hits=selected.clone();Arrays.sort(hits);double mass=0;
        for(int i=0;i<hits.length;i++) {
            if(hits[i]<0||hits[i]>=weights.length||i>0&&hits[i]==hits[i-1])
                throw new IllegalArgumentException("Selected ranked positions must be unique and in range");
            mass+=weights[hits[i]];
        }
        if(!Double.isFinite(mass))throw new IllegalArgumentException("Selected GSEA weight sum is nonfinite");
        boolean uniform=mass==0;double[] tops=new double[hits.length],bottoms=new double[hits.length];
        double cumulative=0,maximum=Double.NEGATIVE_INFINITY,minimum=Double.POSITIVE_INFINITY;int atMax=0,atMin=0;
        for(int i=0;i<hits.length;i++) {
            cumulative+=weights[hits[i]];
            double fraction=uniform?(i+1.0)/hits.length:cumulative/mass;
            tops[i]=fraction-(double)(hits[i]-i)/(weights.length-hits.length);
            bottoms[i]=tops[i]-(uniform?1.0/hits.length:weights[hits[i]]/mass);
            if(tops[i]>maximum){maximum=tops[i];atMax=i;}
            if(bottoms[i]<minimum){minimum=bottoms[i];atMin=i;}
        }
        int direction=tail==Tail.POSITIVE?1:tail==Tail.NEGATIVE?-1:
            maximum>-minimum?1:maximum<-minimum?-1:tie==ExtremumTie.ZERO?0:tie==ExtremumTie.POSITIVE?1:-1;
        double score=direction>0?maximum:direction<0?minimum:0;
        int[] leading=direction>0?Arrays.copyOf(hits,atMax+1):direction<0?new int[hits.length-atMin]:new int[0];
        if(direction<0)for(int i=0;i<leading.length;i++)leading[i]=hits[hits.length-1-i];
        return new Walk(score,maximum,minimum,direction>0?hits[atMax]:direction<0?hits[atMin]-1:-1,leading,tops,bottoms,uniform,uniform);
    }

    /**
     * Raw-weight cumulative walk with enrichit 0.2.6 gseaScores semantics.
     * Separate hit/miss accumulators are advanced at every ranked position;
     * floating-point behavior near symmetric extrema therefore differs from
     * the indexed fgsea formula. Zero hit mass contributes no hit increments,
     * rather than fgsea's uniform fallback, and is explicitly flagged.
     */
    public static Walk rawWalk(double[] weights,int[] selected) {
        return rawWalk(weights,selected,true);
    }
    private static Walk rawWalk(double[] weights,int[] selected,boolean checkWeights) {
        // The indexed implementation validates the same domains and identities.
        Walk checked=walk(weights,selected,Tail.STANDARD,ExtremumTie.NEGATIVE,checkWeights);
        int[] hits=selected.clone();Arrays.sort(hits);double mass=0;
        for(int hit:hits)mass+=weights[hit];
        double hitMass=0,missMass=0,maximum=Double.NEGATIVE_INFINITY,minimum=Double.POSITIVE_INFINITY;
        double[] tops=new double[hits.length],bottoms=new double[hits.length];int j=0,maxAt=0,minAt=0;
        for(int i=0;i<weights.length;i++) {
            if(j<hits.length&&hits[j]==i) {
                bottoms[j]=hitMass-missMass;
                if(mass!=0)hitMass+=weights[i]/mass;
                tops[j]=hitMass-missMass;j++;
            } else missMass+=1.0/(weights.length-hits.length);
            double value=hitMass-missMass;
            if(value>maximum){maximum=value;maxAt=i;}
            if(value<minimum){minimum=value;minAt=i;}
        }
        boolean positive=Math.abs(maximum)>Math.abs(minimum);int at=positive?maxAt:minAt,count=0;int[] leading=new int[hits.length];
        if(positive){for(int hit:hits)if(hit<=at)leading[count++]=hit;}
        else for(int i=hits.length-1;i>=0;i--)if(hits[i]>at)leading[count++]=hits[i];
        return new Walk(positive?maximum:minimum,maximum,minimum,at,Arrays.copyOf(leading,count),tops,bottoms,checked.zeroHitWeight(),false);
    }

    /** One-sided log hypergeometric upper tail, P(X &gt;= overlap). */
    public static double logHypergeometric(int population,int selected,int term,int overlap) {
        long lower=Math.max(0L,(long)selected+term-population);
        if(population<1||selected<0||selected>population||term<0||term>population||overlap<lower||overlap>Math.min(selected,term))
            throw new IllegalArgumentException("Impossible ORA population/selection/term/overlap counts");
        return jdistlib.HyperGeometric.cumulative(overlap-1.0,term,population-term,selected,false,true);
    }

    /**
     * Adjust an explicitly supplied tested P-value family; NaNs remain NaN.
     * The finite family follows stats::p.adjust's default missing-value rule.
     * This method does not infer which terms belong in an engine's family.
     */
    public static double[] adjust(double[] p,Adjustment method) {
        if(method==null)throw new IllegalArgumentException("Adjustment method is required");
        Integer[] order=new Integer[p.length];int n=0;double[] result=new double[p.length];Arrays.fill(result,Double.NaN);
        for(int i=0;i<p.length;i++)if(!Double.isNaN(p[i])) {
            if(!Double.isFinite(p[i])||p[i]<0||p[i]>1)throw new IllegalArgumentException("P-values must be in [0,1] or explicitly undefined NaN");
            order[n++]=i;
        }
        order=Arrays.copyOf(order,n);Arrays.sort(order,Comparator.comparingDouble(i->p[i]));
        double harmonic=1;if(method==Adjustment.BY){harmonic=0;for(int i=1;i<=n;i++)harmonic+=1.0/i;}
        if(method==Adjustment.BH||method==Adjustment.BY) {
            double running=1;for(int i=n-1;i>=0;i--){running=Math.min(running,p[order[i]]*n/(i+1)*harmonic);result[order[i]]=running;}
        } else if(method==Adjustment.HOLM) {
            double running=0;for(int i=0;i<n;i++){running=Math.max(running,Math.min(1,p[order[i]]*(n-i)));result[order[i]]=running;}
        } else for(int i:order)result[i]=method==Adjustment.BONFERRONI?Math.min(1,p[i]*n):p[i];
        return result;
    }
}
