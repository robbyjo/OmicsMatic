/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * R-compatible profile: Mersenne-Twister, Inversion normals and explicit
 * Rounding/Rejection sampling. Retains R's unsigned MT state and draw order.
 * This component is not a complete MR-PRESSO implementation or qualification. */
package org.jlinalg.mrpresso;

import java.util.Arrays;
import java.util.Objects;
import jdistlib.Normal;

/** Mutable, source-profiled R random stream for original MR-PRESSO resampling. */
public final class MrPressoRandom {
    /** Explicit R sample.kind; never inferred from an unreported paper date. */
    public enum Sampling { ROUNDING, REJECTION }

    private static final int LENGTH=624;
    private final int[] mt=new int[LENGTH];
    private final Sampling sampling;
    private int at;

    /** Equivalent to an explicit RNGkind profile followed by R set.seed(integer). */
    public MrPressoRandom(int seed,Sampling sampling) {
        if(seed==Integer.MIN_VALUE)throw new IllegalArgumentException("R NA_integer_ is not a valid set.seed value");
        this.sampling=Objects.requireNonNull(sampling,"Explicit sampling kind required");
        for(int i=0;i<50;i++)seed=69069*seed+1;
        for(int i=0;i<=LENGTH;i++){seed=69069*seed+1;if(i>0)mt[i-1]=seed;}
        at=LENGTH;
    }

    /** Restores an exact R .Random.seed of kind403 or10403, including its cursor. */
    public MrPressoRandom(int[] state) {
        if(state==null||state.length!=626||(state[0]!=403&&state[0]!=10403)||state[1]<0||state[1]>LENGTH)
            throw new IllegalArgumentException("Require626-integer R Mersenne-Twister/Inversion state of kind403 or10403");
        sampling=state[0]==403?Sampling.ROUNDING:Sampling.REJECTION;at=state[1];
        System.arraycopy(state,2,mt,0,LENGTH);
        if(Arrays.stream(mt).allMatch(v->v==0))throw new IllegalArgumentException("All-zero MT state is outside this explicit continuation profile");
    }

    /** One R uniform draw, including R's endpoint fixup. */
    public double uniform() {
        if(at>=LENGTH)twist();
        int value=mt[at++];value^=value>>>11;value^=(value<<7)&0x9d2c5680;
        value^=(value<<15)&0xefc60000;value^=value>>>18;
        double result=Integer.toUnsignedLong(value)*0x1.0p-32;
        return result<=0?0.5/4294967295.0:result>=1?1-0.5/4294967295.0:result;
    }

    /** R's Inversion normal consumes two uniforms; uses the pinned JDistlib qnorm. */
    public double standardNormal() {
        double high=Math.floor(134217728.0*uniform());
        return Normal.quantile((high+uniform())/134217728.0,0,1,true,false);
    }

    /** R rnorm scalar edge semantics; constant/invalid cases consume no draws. */
    public double normal(double mean,double standardDeviation) {
        if(Double.isNaN(mean)||!Double.isFinite(standardDeviation)||standardDeviation<0)return Double.NaN;
        if(standardDeviation==0||!Double.isFinite(mean))return mean;
        return mean+standardDeviation*standardNormal();
    }

    /**
     * Ordered zero-based sample.int without replacement, non-hash R branch.
     * The explicit two-million-element capacity refuses rather than truncates.
     * Full permutations consume all original draws, even when only the first
     * sampled value is subsequently needed by MR-PRESSO's distortion function.
     */
    public int[] sampleWithoutReplacement(int population,int size) {
        if(population<0||population>2_000_000||size<0||size>population)
            throw new IllegalArgumentException("Require0..2000000 population and valid sample size");
        int[] answer=new int[size];
        if(size<2){if(size==1)answer[0]=index(population);return answer;}
        int[] remaining=new int[population];for(int i=0;i<population;i++)remaining[i]=i;
        for(int i=0;i<size;i++){int j=index(population);answer[i]=remaining[j];remaining[j]=remaining[--population];}
        return answer;
    }

    /** Defensive .Random.seed copy suitable for exact continuation. */
    public int[] state() {
        int[] result=new int[626];result[0]=sampling==Sampling.ROUNDING?403:10403;result[1]=at;
        System.arraycopy(mt,0,result,2,LENGTH);return result;
    }

    private int index(int population) {
        if(sampling==Sampling.ROUNDING)return (int)Math.floor(population*uniform());
        int bits=population==1?0:32-Integer.numberOfLeadingZeros(population-1);long value;
        do{
            value=0;
            for(int bit=0;bit<=bits;bit+=16)value=65536*value+(long)Math.floor(uniform()*65536);
            value&=(1L<<bits)-1;
        }while(value>=population);
        return (int)value;
    }

    private void twist() {
        int i=0,value;
        for(;i<227;i++){
            value=(mt[i]&0x80000000)|(mt[i+1]&0x7fffffff);
            mt[i]=mt[i+397]^(value>>>1)^((value&1)==0?0:0x9908b0df);
        }
        for(;i<623;i++){
            value=(mt[i]&0x80000000)|(mt[i+1]&0x7fffffff);
            mt[i]=mt[i-227]^(value>>>1)^((value&1)==0?0:0x9908b0df);
        }
        value=(mt[623]&0x80000000)|(mt[0]&0x7fffffff);
        mt[623]=mt[396]^(value>>>1)^((value&1)==0?0:0x9908b0df);at=0;
    }
}
