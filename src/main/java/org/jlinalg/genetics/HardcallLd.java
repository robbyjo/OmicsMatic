/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.HashSet;
import java.util.Objects;

/**
 * Unphased diploid allele-count frequencies and pairwise-complete Pearson LD.
 * The caller explicitly selects aligned samples; this class does not infer
 * founders, ancestry, ploidy, phasing, imputation or eligibility windows.
 */
public final class HardcallLd {
    private HardcallLd() { }

    /** Bounds include frame admission, each encoded row and one complete pair. */
    public record Limits(int totalSamples,int selectedSamples,long ownedBytes,long pairWordWork) {
        public Limits {
            if(totalSamples<1||totalSamples>10_000_000||selectedSamples<1||selectedSamples>totalSamples||ownedBytes<65536||pairWordWork<1)
                throw new IllegalArgumentException("Invalid diploid LD capacities; at most ten million input samples");
        }
        public static Limits defaults(){return new Limits(1_000_000,1_000_000,67_108_864,1_000_000);}
    }

    /** One immutable ordered sample selection, shared by every row being paired. */
    public static final class Frame {
        private final String identity;
        private final int total;
        private final int[] selected;
        private final Limits limits;
        private Frame(String identity,int total,int[] selected,Limits limits){this.identity=identity;this.total=total;this.selected=selected.clone();this.limits=limits;}
        public String identity(){return identity;}
        public int totalSamples(){return total;}
        public int selectedSamples(){return selected.length;}
        public int[] selectedIndices(){return selected.clone();}
    }

    public enum MinorAllele { ALLELE1, ALLELE2, TIED, UNOBSERVED }
    /** Counts are diploid; undefined frequencies are NaN when no call is observed. */
    public record Frequencies(int called,int missing,long allele1Count,long allele2Count,
                              double allele1Frequency,double minorAlleleFrequency,MinorAllele minorAllele) { }
    public enum Status { DEFINED, INSUFFICIENT_PAIRED_CALLS, CONSTANT_FIRST, CONSTANT_SECOND, CONSTANT_BOTH }
    /**
     * Exact integer sufficient statistics precede floating point division.
     * Undefined correlations/r-squared remain NaN, never an observed zero.
     */
    public record Pair(Status status,int pairedCalls,long sumFirst,long sumSecond,
                       long sumSquaresFirst,long sumSquaresSecond,long sumProduct,
                       long covarianceNumerator,long varianceNumeratorFirst,long varianceNumeratorSecond,
                       double correlation,double rSquared,boolean exactRatioRecovery) { }

    /** Immutable selected calls. Each mask bit refers to the same frame position. */
    public static final class Calls {
        private final Frame frame;
        private final long[] valid,one,two;
        private final Frequencies frequencies;
        private Calls(Frame frame,long[] valid,long[] one,long[] two,Frequencies frequencies){this.frame=frame;this.valid=valid;this.one=one;this.two=two;this.frequencies=frequencies;}
        public Frame frame(){return frame;}
        public Frequencies frequencies(){return frequencies;}
        /** Fresh canonical SNP-major BED row for the selected sample order. */
        public byte[] packedCalls(){
            byte[] packed=new byte[(frame.selected.length+3)/4];
            for(int i=0;i<frame.selected.length;i++){
                long bit=1L<<(i%64);int w=i/64;
                int code=(valid[w]&bit)==0?1:(one[w]&bit)!=0?2:(two[w]&bit)!=0?0:3;
                packed[i/4]|=(byte)(code<<(2*(i%4)));
            }return packed;
        }
    }

    /** The exact ordered indices are retained; duplicate or unknown indices fail. */
    public static Frame frame(String identity,int totalSamples,int[] selected,Limits limits,Runnable check){
        Objects.requireNonNull(identity);Objects.requireNonNull(selected);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        if(identity.isBlank()||totalSamples<0||totalSamples>limits.totalSamples()||selected.length>limits.selectedSamples())
            throw new IllegalArgumentException("Invalid complete LD sample frame");
        long bytes=Math.addExact(65536,Math.multiplyExact(80L,selected.length));admit(bytes,limits);
        var seen=new HashSet<Integer>();
        for(int index:selected){check.run();if(index<0||index>=totalSamples||!seen.add(index))throw new IllegalArgumentException("Unknown or duplicate LD sample index");}
        check.run();return new Frame(identity,totalSamples,selected,limits);
    }

    /**
     * BED codes 00/10/11 count two/one/zero BIM A1 alleles; 01 is missing.
     * This method owns its masks, preserves the input and rejects nonzero padding.
     */
    public static Calls encode(Frame frame,byte[] packed,Runnable check){
        Objects.requireNonNull(frame);Objects.requireNonNull(packed);Objects.requireNonNull(check);check.run();
        if(packed.length!=(frame.total+3L)/4)throw new IllegalArgumentException("Truncated or oversized complete BED row");
        int remainder=frame.total%4;
        if(remainder!=0&&((packed[packed.length-1]&255) >>> (2*remainder))!=0)throw new IllegalArgumentException("Nonzero unused BED padding bits");
        int words=(frame.selected.length+63)/64;admit(Math.addExact(65536+8L*frame.selected.length,24L*words),frame.limits);
        long[] valid=new long[words],one=new long[words],two=new long[words];int called=0;long a1=0;
        for(int i=0;i<frame.selected.length;i++){
            if((i&63)==0)check.run();int index=frame.selected[i],code=(packed[index/4] >>> (2*(index%4)))&3;
            if(code==1)continue;long bit=1L<<(i%64);int w=i/64;valid[w]|=bit;called++;
            if(code==2){one[w]|=bit;a1++;}else if(code==0){two[w]|=bit;a1+=2;}
        }
        long a2=2L*called-a1;double frequency=called==0?Double.NaN:(double)a1/(2L*called);
        Frequencies frequencies=new Frequencies(called,frame.selected.length-called,a1,a2,frequency,
                called==0?Double.NaN:(double)Math.min(a1,a2)/(2L*called),
                called==0?MinorAllele.UNOBSERVED:a1==a2?MinorAllele.TIED:a1<a2?MinorAllele.ALLELE1:MinorAllele.ALLELE2);
        check.run();return new Calls(frame,valid,one,two,frequencies);
    }

    /** Paired calls use the intersection of nonmissing samples, without imputation. */
    public static Pair pair(Calls first,Calls second,Runnable check){
        Objects.requireNonNull(first);Objects.requireNonNull(second);Objects.requireNonNull(check);check.run();
        if(first.frame!=second.frame)throw new IllegalArgumentException("LD rows require the same immutable ordered sample frame");
        long work=Math.multiplyExact(11L,first.valid.length);if(work>first.frame.limits.pairWordWork())throw new IllegalArgumentException("Complete LD pair exceeds declared word-work capacity");
        long n=0,sx=0,sy=0,sxx=0,syy=0,sxy=0;
        for(int i=0;i<first.valid.length;i++){
            check.run();long vx=first.valid[i],vy=second.valid[i],x1=first.one[i],x2=first.two[i],y1=second.one[i],y2=second.two[i];
            n+=Long.bitCount(vx&vy);int ax=Long.bitCount(x1&vy),bx=Long.bitCount(x2&vy),ay=Long.bitCount(y1&vx),by=Long.bitCount(y2&vx);
            sx+=ax+2L*bx;sxx+=ax+4L*bx;sy+=ay+2L*by;syy+=ay+4L*by;
            sxy+=Long.bitCount(x1&y1)+2L*Long.bitCount(x1&y2)+2L*Long.bitCount(x2&y1)+4L*Long.bitCount(x2&y2);
        }
        // The ten-million sample bound keeps every sufficient-statistic product
        // below long overflow; no continuous-dosage rounding is involved.
        long covariance=Math.subtractExact(Math.multiplyExact(n,sxy),Math.multiplyExact(sx,sy));
        long dx=Math.subtractExact(Math.multiplyExact(n,sxx),Math.multiplyExact(sx,sx)),dy=Math.subtractExact(Math.multiplyExact(n,syy),Math.multiplyExact(sy,sy));
        if(dx<0||dy<0)throw new ArithmeticException("Negative exact LD variance numerator");
        Status status=n<2?Status.INSUFFICIENT_PAIRED_CALLS:dx==0&&dy==0?Status.CONSTANT_BOTH:dx==0?Status.CONSTANT_FIRST:dy==0?Status.CONSTANT_SECOND:Status.DEFINED;
        double r=Double.NaN,r2=Double.NaN;boolean recovery=false;
        if(status==Status.DEFINED){
            r2=((double)covariance*covariance)/((double)dx*dy);
            if(r2>1){
                BigInteger numerator=BigInteger.valueOf(covariance).pow(2),denominator=BigInteger.valueOf(dx).multiply(BigInteger.valueOf(dy));
                if(numerator.compareTo(denominator)>0)throw new ArithmeticException("Exact LD statistics violate Cauchy-Schwarz");
                r2=new BigDecimal(numerator).divide(new BigDecimal(denominator),MathContext.DECIMAL128).doubleValue();recovery=true;
            }
            if(!Double.isFinite(r2)||r2<0||r2>1)throw new ArithmeticException("Unreportable complete LD ratio");
            r=Math.copySign(Math.sqrt(r2),covariance);
        }
        check.run();return new Pair(status,Math.toIntExact(n),sx,sy,sxx,syy,sxy,covariance,dx,dy,r,r2,recovery);
    }

    private static void admit(long bytes,Limits limits){if(bytes>limits.ownedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("LD frame/row storage exceeds declared capacity or half the current heap");}
}
