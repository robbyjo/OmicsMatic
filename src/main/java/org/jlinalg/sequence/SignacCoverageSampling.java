/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/** R 4.6.1 Mersenne-Twister/Rejection sample.int profile used by original CoverageTrack. */
public final class SignacCoverageSampling {
    private final int[] state=new int[624];
    private int at=624;
    public SignacCoverageSampling(int seed) {
        for(int j=0;j<50;j++)seed=69069*seed+1;
        for(int j=0;j<625;j++){seed=69069*seed+1;if(j>0)state[j-1]=seed;}
    }
    private double uniform() {
        if(at>=624){int k=0,y;for(;k<227;k++){y=(state[k]&0x80000000)|(state[k+1]&0x7fffffff);state[k]=state[k+397]^(y>>>1)^((y&1)==0?0:0x9908b0df);}for(;k<623;k++){y=(state[k]&0x80000000)|(state[k+1]&0x7fffffff);state[k]=state[k-227]^(y>>>1)^((y&1)==0?0:0x9908b0df);}y=(state[623]&0x80000000)|(state[0]&0x7fffffff);state[623]=state[396]^(y>>>1)^((y&1)==0?0:0x9908b0df);at=0;}
        int y=state[at++];y^=y>>>11;y^=(y<<7)&0x9d2c5680;y^=(y<<15)&0xefc60000;y^=y>>>18;
        double x=Integer.toUnsignedLong(y)*0x1.0p-32;return x<=0?0.5/4294967295.0:x>=1?1-0.5/4294967295.0:x;
    }
    private int index(int n) {
        int bits=n==1?0:32-Integer.numberOfLeadingZeros(n-1);long value;
        do{value=0;for(int bit=0;bit<=bits;bit+=16)value=65536*value+(int)Math.floor(uniform()*65536);value&=(1L<<bits)-1;}while(value>=n);
        return (int)value;
    }
    /** Ordered zero-based draws; bounded to the non-hash branch used by this native coverage profile. */
    public int[] sample(int population,int size) {
        if(population<0||population>2_000_000||size<0||size>population)throw new IllegalArgumentException("Require0..2million population and a valid without-replacement sample size");
        int[] result=new int[size];if(size<2){if(size==1)result[0]=index(population);return result;}
        int[] x=new int[population];for(int i=0;i<x.length;i++)x[i]=i;
        for(int i=0;i<size;i++){int j=index(population);result[i]=x[j];x[j]=x[--population];}return result;
    }
}
