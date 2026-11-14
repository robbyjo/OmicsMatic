/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import jdistlib.F;

/**
 * One-way Levene/Brown-Forsythe homogeneity-of-variance test. Computes the
 * ordinary one-way ANOVA of absolute deviations from each group's mean or
 * median. This is an independent implementation of the mathematical test;
 * it is not a port of the GPL-2-only rstatix/car R implementation or a general
 * R formula/linear-model evaluator. Caller-supplied labels remain literal.
 */
public final class LeveneTest {
    private LeveneTest() { }
    public enum Center { MEDIAN, MEAN }
    public enum MissingPolicy { FAIL, OMIT }

    /** Original input offsets are retained for every kept and omitted row. */
    public record Result(double statistic, double pValue, int numeratorDegrees,
                         int denominatorDegrees, Center center, List<String> levels,
                         List<Integer> groupSizes, List<Double> centers,
                         List<Integer> keptRows, List<Integer> omittedRows) {
        public Result {
            Objects.requireNonNull(center,"center");
            levels=List.copyOf(levels);groupSizes=List.copyOf(groupSizes);
            centers=List.copyOf(centers);keptRows=List.copyOf(keptRows);omittedRows=List.copyOf(omittedRows);
        }
    }

    /** Default median-centered test; incomplete rows must be handled explicitly. */
    public static Result test(double[] values,String[] groups) {
        return test(values,groups,Center.MEDIAN,MissingPolicy.FAIL);
    }

    /**
     * With OMIT, NaN values and null group labels are explicit missing data.
     * Infinity is always rejected, including in an otherwise missing row.
     * An empty string or the literal label "NA" is a real group, not missing.
     * Group order is first occurrence after omission. All retained groups,
     * including singleton groups, participate; unused factor levels are not
     * an input to this prepared-vector API. Input arrays are not modified.
     */
    public static Result test(double[] values,String[] groups,Center center,MissingPolicy missing) {
        Objects.requireNonNull(values,"values");Objects.requireNonNull(groups,"groups");
        Objects.requireNonNull(center,"center");Objects.requireNonNull(missing,"missing policy");
        if(values.length!=groups.length || values.length<3)
            throw new IllegalArgumentException("Matching value/group arrays with at least three input rows required");
        var byGroup=new LinkedHashMap<String,List<Integer>>();
        var kept=new ArrayList<Integer>();var omitted=new ArrayList<Integer>();
        double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
        for(int i=0;i<values.length;i++) {
            if(Double.isInfinite(values[i])) throw new IllegalArgumentException("Infinite value at input row "+i);
            if(Double.isNaN(values[i]) || groups[i]==null) {
                if(missing==MissingPolicy.FAIL) throw new IllegalArgumentException("Missing value/group at input row "+i+"; choose explicit OMIT or supply complete inputs");
                omitted.add(i);continue;
            }
            kept.add(i);byGroup.computeIfAbsent(groups[i],ignored->new ArrayList<>()).add(i);
            minimum=Math.min(minimum,values[i]);maximum=Math.max(maximum,values[i]);
        }
        int n=kept.size(),k=byGroup.size();
        if(k<2 || n<=k) throw new IllegalArgumentException("At least two retained groups and positive residual degrees of freedom required");
        double range=maximum-minimum;
        double commonLocation=Double.isFinite(range)?minimum+range/2:minimum/2+maximum/2;
        double scale=Math.max(Math.abs(minimum-commonLocation),Math.abs(maximum-commonLocation));
        if(!(scale>0)) throw new ArithmeticException("No finite positive deviation scale; no Levene result admitted");
        // A common location/scale avoids overflow and loss from a large shared
        // offset without changing the mathematical test statistic.
        var levels=new ArrayList<String>();var sizes=new ArrayList<Integer>();var centers=new ArrayList<Double>();
        var deviations=new ArrayList<double[]>();var means=new ArrayList<Double>();
        double grandSum=0;
        for(var entry:byGroup.entrySet()) {
            List<Integer> indices=entry.getValue();double[] sample=new double[indices.size()];
            for(int j=0;j<sample.length;j++) sample[j]=(values[indices.get(j)]-commonLocation)/scale;
            double location;
            if(center==Center.MEDIAN) {
                double[] sorted=sample.clone();Arrays.sort(sorted);int mid=sorted.length/2;
                location=(sorted.length%2==1)?sorted[mid]:sorted[mid-1]/2+sorted[mid]/2;
            } else location=mean(sample);
            double[] absolute=new double[sample.length];
            for(int j=0;j<sample.length;j++) absolute[j]=Math.abs(sample[j]-location);
            double deviationMean=mean(absolute);grandSum+=deviationMean*sample.length;
            levels.add(entry.getKey());sizes.add(sample.length);centers.add(commonLocation+location*scale);
            deviations.add(absolute);means.add(deviationMean);
        }
        double grandMean=grandSum/n,between=0,within=0,deviationEnergy=0;
        for(int g=0;g<k;g++) {
            double delta=means.get(g)-grandMean;between+=sizes.get(g)*delta*delta;
            for(double value:deviations.get(g)) {
                double residual=value-means.get(g);within+=residual*residual;deviationEnergy+=value*value;
            }
        }
        if(!(within>0) || !Double.isFinite(within) || within<=64*Math.ulp(1.0)*Math.ulp(1.0)*deviationEnergy)
            throw new ArithmeticException("Degenerate or numerically unresolved within-group deviation variance; no Levene result admitted");
        double statistic=(between/(k-1))/(within/(n-k));
        double probability=F.cumulative(statistic,k-1,n-k,false,false);
        if(!Double.isFinite(statistic) || statistic<0 || !Double.isFinite(probability) || probability<0 || probability>1)
            throw new ArithmeticException("Nonfinite/invalid Levene statistic or F-tail probability; no result admitted");
        return new Result(statistic,probability,k-1,n-k,center,levels,sizes,centers,kept,omitted);
    }

    private static double mean(double[] values) {
        double sum=0,correction=0;
        for(double value:values) {double adjusted=value-correction,next=sum+adjusted;correction=(next-sum)-adjusted;sum=next;}
        return sum/values.length;
    }
}
