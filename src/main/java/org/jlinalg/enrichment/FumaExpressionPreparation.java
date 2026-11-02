/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Sample-level TPM/RPKM preparation following the documented FUMA expression
 * transformation: cap at50, log2(x+1), per-label means and per-gene sample centering.
 * This is not a DEG test, ID conversion, batch correction or historical resource.
 */
public final class FumaExpressionPreparation {
    private FumaExpressionPreparation() { }
    public record Sample(String id,String label) {
        public Sample {token(id);token(label);}
    }
    public record Limits(int maximumInputSamples,int maximumLabels,long maximumOwnedBytes) {
        public Limits {if(maximumInputSamples<1||maximumInputSamples>1000000||maximumLabels<2||maximumLabels>100000||maximumOwnedBytes<65536)throw new IllegalArgumentException("Invalid complete expression preparation capacities");}
        public static Limits defaults(){return new Limits(25000,1000,268435456);}
    }
    /** One immutable explicit selection. Selected indices retain MATRIX column order. */
    public static final class Frame {
        private final List<String> input,labels;
        private final int[] selected,labelIndex,counts;
        private final Limits limits;
        private Frame(List<String> input,List<String> labels,int[] selected,int[] labelIndex,int[] counts,Limits limits){this.input=List.copyOf(input);this.labels=List.copyOf(labels);this.selected=selected.clone();this.labelIndex=labelIndex.clone();this.counts=counts.clone();this.limits=limits;}
        public List<String> inputSamples(){return input;}
        public List<String> labels(){return labels;}
        public int[] selectedIndices(){return selected.clone();}
        public int[] selectedLabelIndices(){return labelIndex.clone();}
        public int[] labelCounts(){return counts.clone();}
        public int selectedSamples(){return selected.length;}
    }
    /** Complete selection with an explicit policy for unselected input columns. */
    public static Frame frame(List<String> input,List<Sample> selected,List<String> labels,boolean allowUnselected,Limits limits,Runnable check){
        Objects.requireNonNull(input);Objects.requireNonNull(selected);Objects.requireNonNull(labels);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        if(input.isEmpty()||input.size()>limits.maximumInputSamples()||selected.isEmpty()||selected.size()>input.size()||labels.size()<2||labels.size()>limits.maximumLabels())throw new IllegalArgumentException("Expression frame requires admitted samples and at least two explicitly ordered labels");
        admit(estimatedBytes(input.size(),labels.size()),limits);
        var names=new HashSet<String>();for(String id:input){check.run();token(id);if(!names.add(id))throw new IllegalArgumentException("Duplicate input expression sample");}
        var labelLookup=new HashMap<String,Integer>();for(int i=0;i<labels.size();i++){token(labels.get(i));if(labelLookup.put(labels.get(i),i)!=null)throw new IllegalArgumentException("Duplicate ordered expression label");}
        var selectedLookup=new HashMap<String,String>();for(var s:selected){check.run();if(!names.contains(s.id())||!labelLookup.containsKey(s.label())||selectedLookup.put(s.id(),s.label())!=null)throw new IllegalArgumentException("Unknown/duplicate selected sample or undeclared expression label");}
        if(!allowUnselected&&selected.size()!=input.size())throw new IllegalArgumentException("Unselected expression columns require the explicit exclude policy");
        int[] indices=new int[selected.size()],groups=new int[selected.size()],counts=new int[labels.size()];int n=0;
        for(int i=0;i<input.size();i++){String label=selectedLookup.get(input.get(i));if(label!=null){indices[n]=i;groups[n]=labelLookup.get(label);counts[groups[n]]++;n++;}}
        for(int count:counts)if(count==0)throw new IllegalArgumentException("Every declared expression label requires at least one selected sample");
        check.run();return new Frame(input,labels,indices,groups,counts,limits);
    }
    /** Marginal mean raw expression >1 in ANY label admits a gene (strict boundary). */
    public enum Disposition { RETAINED, NOT_EXPRESSED }
    public static final class Row {
        private final String gene;
        private final Disposition disposition;
        private final double[] rawMeans,log2Means,centeredMeans;
        private final double sampleMeanLog2,labelMeanLog2;
        private final int capped;
        private Row(String gene,Disposition disposition,double[] rawMeans,double[] log2Means,double[] centeredMeans,double sampleMeanLog2,double labelMeanLog2,int capped){this.gene=gene;this.disposition=disposition;this.rawMeans=rawMeans.clone();this.log2Means=log2Means.clone();this.centeredMeans=centeredMeans.clone();this.sampleMeanLog2=sampleMeanLog2;this.labelMeanLog2=labelMeanLog2;this.capped=capped;}
        public String gene(){return gene;}
        public Disposition disposition(){return disposition;}
        public double[] rawMeans(){return rawMeans.clone();}
        public double[] log2Means(){return log2Means.clone();}
        public double[] centeredMeans(){return centeredMeans.clone();}
        /** Sample-weighted center, used before averaging normalized values by label. */
        public double sampleMeanLog2(){return sampleMeanLog2;}
        /** Unweighted mean of LABEL means, the separate MAGMA Average covariate. */
        public double labelMeanLog2(){return labelMeanLog2;}
        public int cappedSelectedValues(){return capped;}
    }
    /**
     * Validate the complete input row, including excluded columns. Values are
     * finite nonnegative expression measurements, never missing/imputed counts.
     * Gene/version strings and label order are preserved. The caller owns values;
     * this method neither changes nor retains that array.
     */
    public static Row prepare(String gene,double[] values,Frame frame,Runnable check){
        token(gene);Objects.requireNonNull(values);Objects.requireNonNull(frame);Objects.requireNonNull(check);check.run();
        if(values.length!=frame.input.size())throw new IllegalArgumentException("Complete expression row width differs from its sample frame");
        admit(estimatedBytes(values.length,frame.labels.size()),frame.limits);
        for(int i=0;i<values.length;i++){if((i&255)==0)check.run();if(!Double.isFinite(values[i])||values[i]<0)throw new IllegalArgumentException("Nonfinite or negative raw expression value; no missing-value imputation is performed");}
        int m=frame.labels.size(),n=frame.selected.length,capped=0;double[] maxima=new double[m];var logs=sums(m);var total=new Sum();
        for(int i=0;i<n;i++){if((i&255)==0)check.run();double v=values[frame.selected[i]];int g=frame.labelIndex[i];maxima[g]=Math.max(maxima[g],v);if(v>50)capped++;double log=transform(v);logs[g].add(log);total.add(log);}
        double center=total.value()/n;var scaled=sums(m);var centered=sums(m);
        for(int i=0;i<n;i++){if((i&255)==0)check.run();int g=frame.labelIndex[i];double v=values[frame.selected[i]];if(maxima[g]>0)scaled[g].add(v/maxima[g]);centered[g].add(transform(v)-center);}
        double[] raw=new double[m],log=new double[m],norm=new double[m];boolean expressed=false;var average=new Sum();
        for(int g=0;g<m;g++){
            check.run();double fraction=scaled[g].value()/frame.counts[g];if(!Double.isFinite(fraction)||fraction<0||fraction>1)throw new ArithmeticException("Unreportable raw-expression scaled mean");
            raw[g]=fraction*maxima[g];log[g]=logs[g].value()/frame.counts[g];norm[g]=centered[g].value()/frame.counts[g];
            if(!Double.isFinite(raw[g])||!Double.isFinite(log[g])||!Double.isFinite(norm[g]))throw new ArithmeticException("Unreportable prepared expression mean");
            expressed|=raw[g]>1;average.add(log[g]);
        }
        check.run();return new Row(gene,expressed?Disposition.RETAINED:Disposition.NOT_EXPRESSED,raw,log,norm,center,average.value()/m,capped);
    }
    /** Storage estimate excludes caller/sink objects retained across multiple rows. */
    public static long estimatedBytes(int inputSamples,int labels){return Math.addExact(65536,Math.addExact(Math.multiplyExact(512L,inputSamples),Math.multiplyExact(1024L,labels)));}
    private static double transform(double value){return StrictMath.log(Math.min(value,50)+1)/StrictMath.log(2);}
    private static Sum[] sums(int n){var s=new Sum[n];for(int i=0;i<n;i++)s[i]=new Sum();return s;}
    private static final class Sum {double sum,correction;void add(double value){double next=sum+value;if(Math.abs(sum)>=Math.abs(value))correction+=(sum-next)+value;else correction+=(value-next)+sum;sum=next;}double value(){return sum+correction;}}
    private static void admit(long bytes,Limits limits){if(bytes>limits.maximumOwnedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete expression frame/row storage exceeds its declared budget or half the heap");}
    private static void token(String s){if(s==null||s.isBlank()||s.length()>4096||s.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Require bounded exact expression identifiers/labels without whitespace or controls");}
}
