/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.math.BigInteger;

/** Selected x86 R4.6.1 colSums/colMeans arithmetic:64-bit binary significand.
 * Integer arithmetic makes the selected extended-precision accumulation explicit
 * on every JVM. Inputs are already rounded doubles,as in R's matrix operations.
 * This is not a general R numeric runtime or an arbitrary-precision inference API.
 */
final class RLongDoubleSum {
    private static final BigInteger ONE=BigInteger.ONE;
    private BigInteger significand=BigInteger.ZERO;
    private int exponent;
    private double special;

    void add(double value) {
        if(Double.isNaN(special))return;
        if(!Double.isFinite(value)) {
            if(Double.isNaN(value)||special!=0&&special!=value)special=Double.NaN;
            else special=value;
            return;
        }
        if(special!=0||value==0)return;
        long bits=Double.doubleToRawLongBits(value),fraction=bits&0x000f_ffff_ffff_ffffL;
        int field=(int)((bits>>>52)&0x7ff),incomingExponent=field==0?-1074:field-1075;
        BigInteger incoming=BigInteger.valueOf(field==0?fraction:fraction|(1L<<52));
        if(bits<0)incoming=incoming.negate();
        if(significand.signum()==0){significand=incoming;exponent=incomingExponent;return;}
        int common=Math.min(exponent,incomingExponent);
        significand=significand.shiftLeft(exponent-common).add(incoming.shiftLeft(incomingExponent-common));exponent=common;
        int shift=significand.abs().bitLength()-64;
        if(shift>0){significand=signedRoundShift(significand,shift);exponent+=shift;}
        if(significand.abs().bitLength()>64){significand=significand.shiftRight(1);exponent++;}
    }

    double value(){return mean(1);}

    double mean(int count) {
        if(count<1)throw new IllegalArgumentException("Positive column count required");
        if(special!=0||Double.isNaN(special))return special;
        BigInteger value=significand;int power=exponent;
        if(count!=1&&value.signum()!=0) {
            BigInteger magnitude=value.abs(),divisor=BigInteger.valueOf(count);
            int degree=magnitude.bitLength()-divisor.bitLength();
            if(degree>=0?magnitude.compareTo(divisor.shiftLeft(degree))<0:magnitude.shiftLeft(-degree).compareTo(divisor)<0)degree--;
            int shift=63-degree;BigInteger numerator=magnitude.shiftLeft(shift);
            BigInteger[] quotient=numerator.divideAndRemainder(divisor);BigInteger q=quotient[0];int comparison=quotient[1].shiftLeft(1).compareTo(divisor);
            if(comparison>0||comparison==0&&q.testBit(0))q=q.add(ONE);
            power+=degree-63;
            if(q.bitLength()>64){q=q.shiftRight(1);power++;}
            value=value.signum()<0?q.negate():q;
        }
        return asDouble(value,power);
    }

    private static BigInteger signedRoundShift(BigInteger value,int shift) {
        BigInteger absolute=value.abs(),q=absolute.shiftRight(shift),remainder=absolute.subtract(q.shiftLeft(shift));
        int comparison=remainder.compareTo(ONE.shiftLeft(shift-1));
        if(comparison>0||comparison==0&&q.testBit(0))q=q.add(ONE);
        return value.signum()<0?q.negate():q;
    }

    private static double asDouble(BigInteger value,int exponent) {
        if(value.signum()==0)return 0;
        long sign=value.signum()<0?Long.MIN_VALUE:0;BigInteger absolute=value.abs();int power=absolute.bitLength()-1+exponent;
        if(power>1023)return Double.longBitsToDouble(sign|0x7ff0_0000_0000_0000L);
        if(power< -1022) {
            int shift=-1074-exponent;BigInteger q=shift>0?signedRoundShift(absolute,shift):absolute.shiftLeft(-shift);
            return Double.longBitsToDouble(sign|q.longValueExact());
        }
        int shift=absolute.bitLength()-53;BigInteger q=shift>0?signedRoundShift(absolute,shift):absolute.shiftLeft(-shift);
        if(q.bitLength()>53){q=q.shiftRight(1);power++;}
        if(power>1023)return Double.longBitsToDouble(sign|0x7ff0_0000_0000_0000L);
        return Double.longBitsToDouble(sign|((long)(power+1023)<<52)|(q.longValue()&0x000f_ffff_ffff_ffffL));
    }
}
