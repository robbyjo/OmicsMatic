/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Unweighted, unbinned complete-population summaries with explicit R type-7 quantiles.
 * These are measurement-domain statistics, not a reproduction of FlowJo's binned statistics.
 */
public final class CytometryIntensitySummary {
    private CytometryIntensitySummary() { }
    public enum Statistic { MEAN, MEDIAN, SAMPLE_SD, CV_PERCENT, CENTRAL_68_26_HALF_WIDTH,
        CENTRAL_68_26_CV_PERCENT, MAD, MAD_PERCENT, GEOMETRIC_MEAN }
    public enum Status { DEFINED, EMPTY_POPULATION, INSUFFICIENT_COUNT, ZERO_DENOMINATOR,
        NONPOSITIVE_VALUES, NUMERIC_OVERFLOW }
    public record Value(Double value, Status status) {
        public Value {
            if (status==null || (status==Status.DEFINED)!=(value!=null) || value!=null&&!Double.isFinite(value))
                throw new IllegalArgumentException("Undefined statistics require null plus an explicit status");
        }
    }
    public record Result(int count, int nonpositiveValues, Map<Statistic,Value> statistics) {
        public Result { statistics=Collections.unmodifiableMap(new EnumMap<>(statistics)); }
    }
    public static Result summarize(double[] measurements) {
        if (measurements==null) throw new IllegalArgumentException("Supply the complete selected population");
        double[] x=measurements.clone(); int n=x.length, nonpositive=0;
        for(double v:x) { if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite measurement; no event drop, clipping or replacement");if(v<=0)nonpositive++; }
        var result=new EnumMap<Statistic,Value>(Statistic.class);
        if(n==0) { for(Statistic s:Statistic.values())result.put(s,undefined(Status.EMPTY_POPULATION));return new Result(0,0,result); }
        Arrays.sort(x);double median=quantileSorted(x,.5),mean=mean(x),halfWidth=quantileSorted(x,.8413)/2-quantileSorted(x,.1587)/2;
        Value sd=n<2?undefined(Status.INSUFFICIENT_COUNT):finite(sampleSd(x));
        result.put(Statistic.MEAN,finite(mean));result.put(Statistic.MEDIAN,finite(median));result.put(Statistic.SAMPLE_SD,sd);
        result.put(Statistic.CV_PERCENT,percent(sd,mean));result.put(Statistic.CENTRAL_68_26_HALF_WIDTH,finite(halfWidth));
        result.put(Statistic.CENTRAL_68_26_CV_PERCENT,percent(finite(halfWidth),median));
        double[] deviations=new double[n];
        for(int i=0;i<n;i++)deviations[i]=Math.abs(x[i]-median);
        // Overflowing outer deviations remain ordered at +infinity, never dropped;
        // a finite central order statistic can still be resolved from finite source values.
        Arrays.sort(deviations);Value mad=finite(quantileSorted(deviations,.5));
        result.put(Statistic.MAD,mad);result.put(Statistic.MAD_PERCENT,percent(mad,median));
        if(nonpositive>0)result.put(Statistic.GEOMETRIC_MEAN,undefined(Status.NONPOSITIVE_VALUES));
        else { double[] logs=new double[n];for(int i=0;i<n;i++)logs[i]=Math.log(x[i]);result.put(Statistic.GEOMETRIC_MEAN,finite(Math.exp(mean(logs)))); }
        return new Result(n,nonpositive,result);
    }
    /** Exact endpoint admission; no probability clipping, missing-value removal or interpolation default. */
    public static double quantileType7(double[] measurements,double probability) {
        if(measurements==null||measurements.length==0||!Double.isFinite(probability)||probability<0||probability>1)throw new IllegalArgumentException("Require a complete nonempty population and p in [0,1]");
        double[] x=measurements.clone();for(double v:x)if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite quantile input");Arrays.sort(x);return quantileSorted(x,probability);
    }
    private static double quantileSorted(double[] x,double p) {
        // Preserve R's one-based index arithmetic, including its floating-point rounding.
        double index=1+(x.length-1)*p;int floor=(int)Math.floor(index),lower=floor-1,upper=Math.min(floor,x.length-1);double fraction=index-floor;
        if(fraction==0||x[lower]==x[upper])return x[lower];
        // Convex form avoids overflow from an unnecessary opposite-sign difference.
        return (1-fraction)*x[lower]+fraction*x[upper];
    }
    private static double mean(double[] x) {
        double sum=0,correction=0;boolean overflow=false;
        for(double v:x) { double next=sum+v;correction+=Math.abs(sum)>=Math.abs(v)?(sum-next)+v:(v-next)+sum;sum=next;if(!Double.isFinite(sum)||!Double.isFinite(correction)){overflow=true;break;} }
        double total=sum+correction;
        if(!overflow&&Double.isFinite(total))return total/x.length;
        BigDecimal exact=BigDecimal.ZERO;for(double v:x)exact=exact.add(new BigDecimal(v));return exact.divide(BigDecimal.valueOf(x.length),MathContext.DECIMAL128).doubleValue();
    }
    private static double sampleSd(double[] x) {
        double origin=x[x.length/2];if(!Double.isFinite(x[0]-origin)||!Double.isFinite(x[x.length-1]-origin))origin=0;
        double scale=0;for(double v:x)scale=Math.max(scale,Math.abs(v-origin));if(scale==0)return 0;
        double[] normalized=new double[x.length];for(int i=0;i<x.length;i++)normalized[i]=(x[i]-origin)/scale;
        double center=mean(normalized),sum=0,correction=0;
        for(double v:normalized) { double d=v-center,square=d*d,next=sum+square;correction+=sum>=square?(sum-next)+square:(square-next)+sum;sum=next; }
        return scale*Math.sqrt((sum+correction)/(x.length-1));
    }
    private static Value percent(Value numerator,double denominator) {
        if(numerator.status()!=Status.DEFINED)return numerator;
        if(denominator==0)return undefined(Status.ZERO_DENOMINATOR);
        // Evaluate the defined signed percent ratio without avoidable intermediate overflow/underflow.
        double value=new BigDecimal(numerator.value()).multiply(BigDecimal.valueOf(100)).divide(new BigDecimal(denominator),MathContext.DECIMAL128).doubleValue();return finite(value);
    }
    private static Value finite(double v) { return Double.isFinite(v)?new Value(v,Status.DEFINED):undefined(Status.NUMERIC_OVERFLOW); }
    private static Value undefined(Status s) { return new Value(null,s); }
}
