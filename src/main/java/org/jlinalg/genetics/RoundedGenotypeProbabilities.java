/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bounds on diploid genotype moments from explicitly rounded probabilities.
 *
 * <p>The three supplied numbers represent probabilities of zero, one and two
 * copies of the caller's allele1. Allele1 need not be the genome ALT allele.
 * The default contract places every probability within half a declared decimal
 * output unit. An explicit alternative allows probabilities below one output
 * unit to be censored to zero before nearest rounding. Boundary ties are included,
 * and the underlying probabilities must sum to one. Reported
 * values are never renormalized. This is a rounding audit, not an imputation,
 * phasing, calibration or reconstruction of the original posterior.</p>
 *
 * <p>A format-specific all-zero missing code must be handled by the caller.
 * It is not evidence for a zero-copy genotype. This class considers only
 * probability distributions compatible with the declared rounding contract.</p>
 */
public final class RoundedGenotypeProbabilities {
    private RoundedGenotypeProbabilities() { }
    private static final BigDecimal ONE=BigDecimal.ONE, TWO=BigDecimal.valueOf(2), FOUR=BigDecimal.valueOf(4);

    /** Caller-declared quantization assumptions, not inferred from printed tokens. */
    public enum Quantization {
        /** Every component is rounded to its nearest decimal grid point. */
        NEAREST_DECIMAL,
        /** Values below one output unit are zeroed; remaining values are rounded to nearest. */
        NEAREST_WITH_SUBUNIT_ZERO_CENSORING
    }

    /** Outward-rounded finite bounds; endpoints are inclusive. */
    public record Range(double lower,double upper) {
        public Range {
            if(!Double.isFinite(lower)||!Double.isFinite(upper)||lower>upper)
                throw new IllegalArgumentException("Invalid finite rounding bounds");
        }
    }

    /** Feasible marginal probabilities, allele1 dosage, posterior variance and certainty. */
    public record Bounds(List<Range> probabilities,Range allele1Dosage,
                         Range posteriorVariance,Range maximumProbability) {
        public Bounds { probabilities=List.copyOf(probabilities); }
    }

    /**
     * Bound all probability distributions consistent with three rounded tokens.
     *
     * @param zero probability of zero allele1 copies
     * @param one probability of one allele1 copy
     * @param two probability of two allele1 copies
     * @param decimalPlaces explicitly declared fixed decimal precision, 0 through 15
     * @return conservative moment bounds including the sum-to-one constraint
     * @throws IllegalArgumentException for invalid tokens, precision or infeasible mass
     */
    public static Bounds bounds(String zero,String one,String two,int decimalPlaces) {
        return bounds(zero,one,two,decimalPlaces,Quantization.NEAREST_DECIMAL);
    }

    /**
     * Bound unit-mass distributions under an explicit quantization contract.
     * These are conditional bounds: a file can contain subunit genotype mass or
     * follow another quantizer, in which case the claimed premise is not established.
     */
    public static Bounds bounds(String zero,String one,String two,int decimalPlaces,Quantization quantization) {
        if(decimalPlaces<0||decimalPlaces>15)throw new IllegalArgumentException("Declare 0..15 decimal places");
        if(quantization==null)throw new IllegalArgumentException("Declare the quantization contract");
        BigDecimal half=BigDecimal.valueOf(5).scaleByPowerOfTen(-decimalPlaces-1);
        BigDecimal unit=ONE.scaleByPowerOfTen(-decimalPlaces);
        var lower=new BigDecimal[3];var upper=new BigDecimal[3];String[] tokens={zero,one,two};
        for(int i=0;i<3;i++) {
            BigDecimal value=number(tokens[i],decimalPlaces);
            lower[i]=value.subtract(half).max(BigDecimal.ZERO);
            upper[i]=value.add(half).min(ONE);
            if(quantization==Quantization.NEAREST_WITH_SUBUNIT_ZERO_CENSORING) {
                if(value.signum()==0)upper[i]=unit;
                else lower[i]=lower[i].max(unit);
            }
        }
        if(sum(lower).compareTo(ONE)>0||sum(upper).compareTo(ONE)<0)
            throw new IllegalArgumentException("No unit-mass distribution fits the declared rounding; no normalization performed");
        var vertices=new ArrayList<List<BigDecimal>>();
        for(int free=0;free<3;free++) {
            int a=(free+1)%3,b=(free+2)%3;
            for(int mask=0;mask<4;mask++) {
                BigDecimal[] p=new BigDecimal[3];p[a]=(mask&1)==0?lower[a]:upper[a];p[b]=(mask&2)==0?lower[b]:upper[b];
                p[free]=ONE.subtract(p[a]).subtract(p[b]);
                if(p[free].compareTo(lower[free])>=0&&p[free].compareTo(upper[free])<=0)
                    vertices.add(List.of(p));
            }
        }
        if(vertices.isEmpty())throw new IllegalArgumentException("No feasible rounding vertices");
        BigDecimal minDose=null,maxDose=null,minVariance=null,maxVariance=null;
        for(List<BigDecimal> p:vertices) {
            BigDecimal dose=dose(p),variance=second(p).subtract(dose.multiply(dose));
            minDose=min(minDose,dose);maxDose=max(maxDose,dose);minVariance=min(minVariance,variance);maxVariance=max(maxVariance,variance);
        }
        // Variance is concave. Its minimum is at a vertex; its maximum can lie
        // inside an edge. There is no interior stationary point in (p1,p2).
        for(int i=0;i<vertices.size();i++)for(int j=i+1;j<vertices.size();j++) {
            List<BigDecimal> a=vertices.get(i),b=vertices.get(j);
            BigDecimal start=dose(a),delta=dose(b).subtract(start),slope=second(b).subtract(second(a));
            BigDecimal square=delta.multiply(delta),linear=slope.subtract(TWO.multiply(start).multiply(delta));
            if(square.signum()>0&&linear.signum()>0&&linear.compareTo(TWO.multiply(square))<0) {
                BigDecimal v0=second(a).subtract(start.multiply(start));
                BigDecimal peak=v0.add(linear.multiply(linear).divide(FOUR.multiply(square),80,RoundingMode.CEILING));
                maxVariance=max(maxVariance,peak);
            }
        }
        var probabilities=new ArrayList<Range>();BigDecimal maximumLower=BigDecimal.ZERO,maximumUpper=BigDecimal.ZERO;
        for(int i=0;i<3;i++) {
            BigDecimal lo=lower[i].max(ONE.subtract(upper[(i+1)%3]).subtract(upper[(i+2)%3]));
            BigDecimal hi=upper[i].min(ONE.subtract(lower[(i+1)%3]).subtract(lower[(i+2)%3]));
            probabilities.add(range(lo,hi));maximumLower=maximumLower.max(lo);maximumUpper=maximumUpper.max(hi);
        }
        var caps=new ArrayList<>(List.of(upper));caps.sort(Comparator.naturalOrder());BigDecimal fixed=BigDecimal.ZERO;
        for(int i=0;i<3;i++) {
            BigDecimal remaining=BigDecimal.valueOf(3L-i),mass=ONE.subtract(fixed);
            if(mass.compareTo(caps.get(i).multiply(remaining))<=0) {
                maximumLower=maximumLower.max(mass.divide(remaining,80,RoundingMode.FLOOR));break;
            }
            fixed=fixed.add(caps.get(i));
        }
        return new Bounds(probabilities,range(minDose,maxDose),range(minVariance,maxVariance),range(maximumLower,maximumUpper));
    }

    private static BigDecimal number(String token,int digits) {
        try {
            if(token==null||token.length()>128||!token.matches("[+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))
                throw new NumberFormatException();
            BigDecimal p=new BigDecimal(token);
            if(p.signum()<0||p.compareTo(ONE)>0||p.stripTrailingZeros().scale()>digits)throw new NumberFormatException();
            return p;
        } catch(NumberFormatException failure) {throw new IllegalArgumentException("Invalid probability at declared precision: "+token,failure);}
    }
    private static BigDecimal sum(BigDecimal[] p) {return p[0].add(p[1]).add(p[2]);}
    private static BigDecimal dose(List<BigDecimal> p) {return p.get(1).add(TWO.multiply(p.get(2)));}
    private static BigDecimal second(List<BigDecimal> p) {return p.get(1).add(FOUR.multiply(p.get(2)));}
    private static BigDecimal min(BigDecimal a,BigDecimal b) {return a==null?b:a.min(b);}
    private static BigDecimal max(BigDecimal a,BigDecimal b) {return a==null?b:a.max(b);}
    private static Range range(BigDecimal low,BigDecimal high) {
        double lo=low.doubleValue(),hi=high.doubleValue();
        if(new BigDecimal(lo).compareTo(low)>0)lo=Math.nextDown(lo);
        if(new BigDecimal(hi).compareTo(high)<0)hi=Math.nextUp(hi);
        return new Range(lo,hi);
    }
}
