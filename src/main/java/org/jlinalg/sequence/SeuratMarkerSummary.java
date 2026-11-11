/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Native sparse FoldChange.Assay summaries and FindMarkers pre/post-processing.
 * Statistical tests, cell sampling and assay preparation are separate operations.
 * Profiles preserve reported source differences; they are not historical runtimes.
 */
public final class SeuratMarkerSummary {
    private SeuratMarkerSummary() { }

    public enum Profile { V4_1_0, V4_3_0, V5_0_0, V5_1_0, V5_2_1, V5_6_0 }
    public enum Slot { COUNTS, DATA, SCALE_DATA }
    public enum Normalization { UNRECORDED, LOG_NORMALIZE, OTHER }

    /** Immutable finite real CSC input; implicit entries are zero. IDs are opaque. */
    public static final class Matrix {
        private final List<String> features,cells;
        private final int[] pointers,rows;
        private final double[] values;
        public Matrix(List<String> features,List<String> cells,int[] pointers,int[] rows,double[] values) {
            this.features=identities(features);this.cells=identities(cells);
            this.pointers=pointers.clone();this.rows=rows.clone();this.values=values.clone();
            if(this.pointers.length!=cells.size()+1||this.rows.length!=this.values.length||this.pointers[0]!=0||this.pointers[cells.size()]!=values.length)
                throw new IllegalArgumentException("CSC dimensions/pointers disagree");
            for(int c=0;c<cells.size();c++) {
                int a=this.pointers[c],b=this.pointers[c+1],previous=-1;
                if(a<0||b<a||b>values.length)throw new IllegalArgumentException("Invalid CSC pointers");
                for(int k=a;k<b;k++) {
                    if(this.rows[k]<=previous||this.rows[k]>=features.size()||!Double.isFinite(this.values[k]))
                        throw new IllegalArgumentException("Require sorted unique rows and finite real values; no repair");
                    previous=this.rows[k];
                }
            }
        }
        public List<String> features(){return features;}
        public List<String> cells(){return cells;}
        public int storedValues(){return values.length;}
    }
    public record Options(Profile profile,Slot slot,Normalization normalization,double pseudocount,double logBase) {
        public Options {
            Objects.requireNonNull(profile);Objects.requireNonNull(slot);Objects.requireNonNull(normalization);
            if(!Double.isFinite(pseudocount)||pseudocount<0||!Double.isFinite(logBase)||logBase<=0||logBase==1)
                throw new IllegalArgumentException("Require finite pseudocount>=0 and positive log base other than one");
        }
        public static Options current(Slot slot){return new Options(Profile.V5_6_0,slot,Normalization.UNRECORDED,1,2);}
    }
    public record Feature(String id,int originalIndex,double foldChange,double fraction1,double fraction2) { }
    public record Summary(List<Feature> features,int group1Size,int group2Size,Options options,List<String> warnings) {
        public Summary {features=List.copyOf(features);warnings=List.copyOf(warnings);Objects.requireNonNull(options);}
    }
    public record Filters(double minimumFraction,double minimumFractionDifference,double logFoldThreshold,boolean onlyPositive) {
        public Filters {
            if(!Double.isFinite(minimumFraction)||minimumFraction<0||minimumFraction>1||Double.isNaN(minimumFractionDifference)
                    ||minimumFractionDifference==Double.POSITIVE_INFINITY||minimumFractionDifference>1||!Double.isFinite(logFoldThreshold)||logFoldThreshold<0)
                throw new IllegalArgumentException("Invalid marker prefilter thresholds");
        }
    }
    public record Marker(Feature feature,double pValue,double adjustedP) { }

    /** O(features+cells+stored values) time/space; scans each selected CSC cell once. */
    public static Summary summarize(Matrix matrix,int[] group1,int[] group2,Options options,long maximumWork,BooleanSupplier cancelled) {
        Objects.requireNonNull(matrix);Objects.requireNonNull(options);Poll poll=new Poll(maximumWork,cancelled);poll.check();
        int[] membership=new int[matrix.cells.size()];select(group1,1,membership,poll);select(group2,2,membership,poll);
        int n=matrix.features.size();double[][] sums=new double[2][n];int[][] detected=new int[2][n];
        boolean transform=exponentiate(options);
        for(int cell=0;cell<membership.length;cell++) {
            poll.step();int group=membership[cell]-1;if(group<0)continue;
            for(int k=matrix.pointers[cell];k<matrix.pointers[cell+1];k++) {
                poll.step();int row=matrix.rows[k];double x=matrix.values[k];
                if(x>0)detected[group][row]++;
                sums[group][row]+=transform?Math.expm1(x):x;
            }
        }
        List<Feature> result=new ArrayList<>(n);int nonfinite=0;
        for(int row=0;row<n;row++) {
            poll.step();double mean1=mean(sums[0][row],group1.length,options),mean2=mean(sums[1][row],group2.length,options);
            double fc=mean1-mean2;if(!Double.isFinite(fc))nonfinite++;
            result.add(new Feature(matrix.features.get(row),row,fc,roundedDetection(detected[0][row],group1.length),roundedDetection(detected[1][row],group2.length)));
        }
        List<String> warnings=new ArrayList<>();
        if(nonfinite>0)warnings.add("NONFINITE_FOLD_CHANGES: "+nonfinite+" derived NaN/Inf values retained. Review signed inputs, pseudocount and exponentiation; no clipping/imputation occurs.");
        if(options.slot==Slot.DATA&&transform)warnings.add("SOURCE_EXPM1_MEAN: this profile's data-slot mean applies expm1 even to explicitly supplied TF-IDF values. No log normalization is inferred or performed.");
        if(options.profile==Profile.V4_3_0&&options.slot==Slot.DATA&&options.normalization!=Normalization.LOG_NORMALIZE)
            warnings.add("SOURCE_4_3_UNRECORDED_NORMALIZATION: data-slot summary uses arithmetic values unless LogNormalize is explicitly recorded; current-v5 behavior differs.");
        if(options.profile.ordinal()>=Profile.V5_0_0.ordinal())warnings.add("SOURCE_V5_PSEUDOCOUNT: pseudocount is added to each group sum before division by group size, not to each mean.");
        poll.check();return new Summary(result,group1.length,group2.length,options,warnings);
    }
    public static Summary summarize(Matrix matrix,int[] group1,int[] group2,Options options) {
        return summarize(matrix,group1,group2,options,100_000_000,()->false);
    }
    /** Selected before upstream sampling/testing; comparisons use rounded detection fractions. */
    public static List<Feature> prefilter(Summary summary,Filters filters,Slot testingSlot) {
        Objects.requireNonNull(summary);Objects.requireNonNull(filters);Objects.requireNonNull(testingSlot);List<Feature> selected=new ArrayList<>();
        for(Feature f:summary.features) {
            double max=Math.max(f.fraction1,f.fraction2),difference=max-Math.min(f.fraction1,f.fraction2);
            if(!(max>=filters.minimumFraction&&difference>=filters.minimumFractionDifference))continue;
            if(testingSlot!=Slot.SCALE_DATA&&!(filters.onlyPositive?f.foldChange>=filters.logFoldThreshold:Math.abs(f.foldChange)>=filters.logFoldThreshold))continue;
            selected.add(f);
        }
        return List.copyOf(selected);
    }
    /**
     * Bonferroni over all original assay features, then edition-specific ordering.
     * pValues are supplied original test results in tested-feature order; this
     * method does not perform a Wilcoxon/logistic test or certify their provenance.
     */
    public static List<Marker> finish(Summary summary,List<Feature> tested,double[] pValues,boolean onlyPositive) {
        Objects.requireNonNull(summary);Objects.requireNonNull(tested);Objects.requireNonNull(pValues);
        if(tested.size()!=pValues.length)throw new IllegalArgumentException("Test-result dimensions differ");
        Set<Integer> used=new HashSet<>();List<Marker> result=new ArrayList<>();
        for(int i=0;i<tested.size();i++) {
            Feature f=tested.get(i);if(f.originalIndex<0||f.originalIndex>=summary.features.size()||!f.equals(summary.features.get(f.originalIndex))||!used.add(f.originalIndex))
                throw new IllegalArgumentException("Tested features must be unique exact summary records");
            double p=pValues[i];if(!Double.isNaN(p)&&(!Double.isFinite(p)||p<0||p>1))throw new IllegalArgumentException("P values must be probabilities or explicit NaN");
            if(onlyPositive&&Double.isNaN(f.foldChange))throw new IllegalArgumentException("Original only.pos would create a missing-identity placeholder row from undefined fold change; this native identity domain cannot admit it");
            if(onlyPositive&&!(f.foldChange>0))continue;
            result.add(new Marker(f,p,Math.min(1,p*summary.features.size())));
        }
        Profile profile=summary.options.profile;
        result.sort((a,b)->{
            int p=compare(a.pValue,b.pValue);if(p!=0)return p;
            double x=tie(a.feature,profile),y=tie(b.feature,profile);return compare(-x,-y);
        });
        return List.copyOf(result);
    }
    private static double tie(Feature f,Profile p) {
        // v4's second order key repeats -p_val (column one), so equal-p rows
        // keep tested-feature order. It is not a fold-change tie breaker.
        return switch(p){case V4_1_0,V4_3_0->0;case V5_0_0->Math.abs(f.foldChange);default->Math.abs(f.fraction1-f.fraction2);};
    }
    /** R4.6 fround specialized to three digits and a nonnegative fraction. */
    public static double roundedDetection(int detected,int groupSize) {
        if(groupSize<1||detected<0||detected>groupSize)throw new IllegalArgumentException("Invalid detection population");
        double x=(double)detected/groupSize,scaled=x*1000,lowerInteger=Math.floor(scaled);
        double lower=lowerInteger/1000,upper=Math.ceil(scaled)/1000;
        double upDistance=upper-x,downDistance=x-lower;
        return upDistance<downDistance||(lowerInteger%2==1&&upDistance==downDistance)?upper:lower;
    }
    private static int compare(double a,double b){if(a==b)return 0;if(Double.isNaN(a))return Double.isNaN(b)?0:1;if(Double.isNaN(b))return -1;return Double.compare(a,b);}
    private static boolean exponentiate(Options o) {
        if(o.slot!=Slot.DATA)return false;
        return switch(o.profile){case V4_1_0->true;case V4_3_0->o.normalization==Normalization.LOG_NORMALIZE;default->o.normalization!=Normalization.OTHER;};
    }
    private static double mean(double sum,int n,Options o) {
        // In v5 a recorded non-LogNormalize method chooses the count mean even
        // for scale.data. This unusual source branch must remain explicit.
        boolean scaled=o.slot==Slot.SCALE_DATA&&(o.profile.ordinal()<Profile.V5_0_0.ordinal()||o.normalization!=Normalization.OTHER);
        if(scaled)return sum/n;
        double value=o.profile.ordinal()<Profile.V5_0_0.ordinal()?sum/n+o.pseudocount:(sum+o.pseudocount)/n;
        return Math.log(value)/Math.log(o.logBase);
    }
    private static void select(int[] selected,int group,int[] membership,Poll poll) {
        Objects.requireNonNull(selected);if(selected.length==0)throw new IllegalArgumentException("Each explicit cell group must be nonempty");
        for(int c:selected){poll.step();if(c<0||c>=membership.length||membership[c]!=0)throw new IllegalArgumentException("Cell groups must be unique, disjoint and in range");membership[c]=group;}
    }
    private static List<String> identities(List<String> ids) {
        Objects.requireNonNull(ids);if(ids.isEmpty())throw new IllegalArgumentException("Require nonempty identities");List<String> copy=List.copyOf(ids);Set<String> unique=new HashSet<>();
        for(String id:copy)if(id.isBlank()||id.codePoints().anyMatch(Character::isISOControl)||!unique.add(id))throw new IllegalArgumentException("Require exact unique nonblank identities without controls");return copy;
    }
    private static final class Poll {
        private final BooleanSupplier cancelled;private final long maximum;private long work;
        Poll(long maximum,BooleanSupplier cancelled){if(maximum<0)throw new IllegalArgumentException("Negative work capacity");this.maximum=maximum;this.cancelled=Objects.requireNonNull(cancelled);}
        void step(){if(work==maximum)throw new IllegalArgumentException("Marker summary maximumWork exceeded");if((++work&1023)==0)check();}
        void check(){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Marker summary cancelled");}
    }
}
