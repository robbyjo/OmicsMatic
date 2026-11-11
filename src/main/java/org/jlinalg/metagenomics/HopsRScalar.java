/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/** Restricted decimal/scalar conversion of the original Linux x86_64 R4.0.5.
 * Source algorithms: R Core Team, src/main/util.c R_strtod5 and scalar formatReal,
 * GPL-2.0-or-later, used here under GPL-3.0-or-later. Binary64 Java parsing cannot
 * replace R's64-significand-bit intermediate arithmetic at decimal extremes.
 * This is not a general R parser/formatter. Caller validates decimal grammar.
 */
final class HopsRScalar {
    private HopsRScalar() { }
    private static final MathContext FIFTEEN=new MathContext(15,RoundingMode.HALF_EVEN);
    private static final Binary TEN=new Binary(BigInteger.TEN,0),ONE=new Binary(BigInteger.ONE,0),ZERO=new Binary(BigInteger.ZERO,0);

    static Double mixed(Double value) {
        if(value==null || !Double.isFinite(value))return value;if(value==0)return 0.0;
        BigDecimal exact=new BigDecimal(Math.abs(value)),rounded=exact.round(FIFTEEN).stripTrailingZeros();
        int significant=rounded.precision(),power=significant-rounded.scale()-1,left=power+1;
        // Original scalar formatReal: prefer fixed when it occupies no more
        // space; fixed may retain integer digits discarded by scientific output.
        if(power>0 && power<=27 && Math.abs(value)<Math.pow(10,power)-0.5/Math.pow(10,Math.max(0,Math.min(27,15-power))))left--;
        int right=Math.max(0,significant-left),fixedWidth=Math.max(1,left)+right+(right==0?0:1);
        int exponentDigits=left>100 || left<=-99?3:2;
        int scientificWidth=significant+(significant>1?1:0)+2+exponentDigits;
        BigDecimal printed=fixedWidth<=scientificWidth?exact.setScale(right,RoundingMode.HALF_EVEN).stripTrailingZeros():rounded;
        String token;
        if(fixedWidth<=scientificWidth)token=printed.toPlainString();
        else {String digits=printed.unscaledValue().abs().toString();int exponent=digits.length()-printed.scale()-1;
            token=digits.charAt(0)+(digits.length()>1?"."+digits.substring(1):"")+"e"+(exponent>=0?"+":"")+exponent;}
        return decimal((value<0?"-":"")+token);
    }

    static double decimal(String text) {
        if(text.length()>1024)throw new IllegalArgumentException("Original numeric token exceeds explicit1024-character assessment capacity");
        int i=0,sign=1;if(text.charAt(i)=='-' || text.charAt(i)=='+') {if(text.charAt(i)=='-')sign=-1;i++;}
        Binary answer=ZERO;int digits=0,exponent=0;
        while(i<text.length() && digit(text.charAt(i))) {answer=answer.multiply(TEN).add(integer(text.charAt(i++)-'0'));digits++;}
        if(i<text.length() && text.charAt(i)=='.') {
            i++;while(i<text.length() && digit(text.charAt(i))) {answer=answer.multiply(TEN).add(integer(text.charAt(i++)-'0'));digits++;exponent--;}
        }
        if(digits==0)throw new IllegalArgumentException("No decimal digits");
        if(i<text.length() && (text.charAt(i)=='e' || text.charAt(i)=='E')) {
            i++;int exponentSign=1;if(i<text.length() && (text.charAt(i)=='-' || text.charAt(i)=='+')) {if(text.charAt(i)=='-')exponentSign=-1;i++;}
            int n=0;while(i<text.length() && digit(text.charAt(i))) {if(n<9999)n=n*10+text.charAt(i)-'0';i++;}exponent+=exponentSign*n;
        }
        if(i!=text.length())throw new IllegalArgumentException("Unsupported decimal syntax");
        if(exponent+digits< -300) {for(int n=0;n<digits;n++)answer=answer.divide(TEN);exponent+=digits;}
        Binary factor=ONE,power=TEN;
        if(exponent< -307) {
            for(int n=-exponent;n!=0;n>>=1,power=power.multiply(power))if((n&1)!=0)factor=factor.divide(power);
            answer=answer.multiply(factor);
        }else if(exponent<0) {
            for(int n=-exponent;n!=0;n>>=1,power=power.multiply(power))if((n&1)!=0)factor=factor.multiply(power);
            answer=answer.divide(factor);
        }else if(answer.mantissa().signum()!=0) {
            for(int n=exponent;n!=0;n>>=1,power=power.multiply(power))if((n&1)!=0)factor=factor.multiply(power);
            answer=answer.multiply(factor);
        }
        return sign*answer.binary64();
    }
    private static boolean digit(char c) {return c>='0' && c<='9';}
    private static Binary integer(int value) {return value==0?ZERO:new Binary(BigInteger.valueOf(value),0);}
    /** Positive binary floating arithmetic with64 significant bits, ties-to-even. */
    private record Binary(BigInteger mantissa,int exponent) {
        private static Binary normalized(BigInteger value,int exponent) {
            int shift=Math.max(0,value.bitLength()-64);if(shift>0)value=roundShift(value,shift);
            if(value.bitLength()>64) {value=value.shiftRight(1);shift++;}return new Binary(value,Math.addExact(exponent,shift));
        }
        Binary add(Binary other) {
            int common=Math.min(exponent,other.exponent);return normalized(mantissa.shiftLeft(exponent-common).add(other.mantissa.shiftLeft(other.exponent-common)),common);
        }
        Binary multiply(Binary other) {return normalized(mantissa.multiply(other.mantissa),Math.addExact(exponent,other.exponent));}
        Binary divide(Binary other) {
            if(other.mantissa.signum()==0)throw new IllegalArgumentException("Zero divisor in original decimal conversion");if(mantissa.signum()==0)return ZERO;
            int power=mantissa.bitLength()-other.mantissa.bitLength();
            if(power>=0?mantissa.compareTo(other.mantissa.shiftLeft(power))<0:mantissa.shiftLeft(-power).compareTo(other.mantissa)<0)power--;
            int shift=63-power;BigInteger numerator=mantissa,denominator=other.mantissa;
            if(shift>=0)numerator=numerator.shiftLeft(shift);else denominator=denominator.shiftLeft(-shift);
            BigInteger[] division=numerator.divideAndRemainder(denominator);int relation=division[1].shiftLeft(1).compareTo(denominator);
            BigInteger quotient=relation>0 || relation==0 && division[0].testBit(0)?division[0].add(BigInteger.ONE):division[0];return normalized(quotient,Math.subtractExact(Math.subtractExact(exponent,other.exponent),shift));
        }
        double binary64() {
            if(mantissa.signum()==0)return 0.0;int power=mantissa.bitLength()-1+exponent;
            if(power>1023)return Double.POSITIVE_INFINITY;if(power< -1075)return 0.0;
            if(power==1023) {BigInteger max=BigInteger.ONE.shiftLeft(53).subtract(BigInteger.ONE);int common=Math.min(exponent,971);
                if(mantissa.shiftLeft(exponent-common).compareTo(max.shiftLeft(971-common))>0)return Double.POSITIVE_INFINITY;}
            int shift=Math.max(Math.max(0,mantissa.bitLength()-53),-1074-exponent);
            BigInteger rounded=shift==0?mantissa:roundShift(mantissa,shift);return Math.scalb(rounded.doubleValue(),exponent+shift);
        }
    }
    private static BigInteger roundShift(BigInteger value,int shift) {
        BigInteger quotient=value.shiftRight(shift),remainder=value.subtract(quotient.shiftLeft(shift));int relation=remainder.shiftLeft(1).compareTo(BigInteger.ONE.shiftLeft(shift));
        return relation>0 || relation==0 && quotient.testBit(0)?quotient.add(BigInteger.ONE):quotient;
    }
}
