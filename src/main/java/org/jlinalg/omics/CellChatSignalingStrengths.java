/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.omics;

import java.util.Arrays;
import java.util.Comparator;

/** Prepared CellChat centrality-strength contrasts and native rank-sum statistics.
 * P-values are intentionally provided by the explicitly selected upstream R test.
 */
public final class CellChatSignalingStrengths {
    private CellChatSignalingStrengths() { }
    public enum DifferenceMode { ABSOLUTE, POSITIVE, NEGATIVE }
    public record Contrast(double treatedMinusControl,boolean positiveAboveThreshold,
            boolean negativeAboveThreshold,boolean absoluteAboveThreshold,boolean selected) { }
    public record RankSum(int controlSamples,int treatedSamples,double statistic,
            int tiedBlocks,int tiedObservations) { }
    private record Observation(double value,boolean control) { }

    /** No normalization or missing-value substitution is performed. */
    public static Contrast contrast(double control,double treated,double threshold,DifferenceMode mode) {
        require(Double.isFinite(control)&&control>=0&&Double.isFinite(treated)&&treated>=0,"Strengths must be finite and nonnegative");
        require(Double.isFinite(threshold)&&threshold>=0&&mode!=null,"Explicit finite nonnegative cutoff and direction required");
        double delta=treated-control;boolean positive=delta>threshold,negative=delta< -threshold,absolute=Math.abs(delta)>threshold;
        boolean selected=switch(mode){case ABSOLUTE->absolute;case POSITIVE->positive;case NEGATIVE->negative;};
        return new Contrast(delta,positive,negative,absolute,selected);
    }

    /** R's unpaired W statistic is the control midrank sum minus n1*(n1+1)/2. */
    public static RankSum rankSum(double[] control,double[] treated) {
        require(control!=null&&treated!=null&&control.length>0&&treated.length>0,"Both explicit sample populations are required");
        long total=(long)control.length+treated.length;require(total<=1000000,"Prepared rank population exceeds capacity");
        Observation[] values=new Observation[(int)total];int at=0;
        for(double x:control){require(Double.isFinite(x)&&x>=0,"Invalid control strength");values[at++]=new Observation(x,true);}
        for(double x:treated){require(Double.isFinite(x)&&x>=0,"Invalid treated strength");values[at++]=new Observation(x,false);}
        Arrays.sort(values,Comparator.comparingDouble(Observation::value));long twiceControlRanks=0;int tiedBlocks=0,tiedObservations=0;
        for(int begin=0;begin<values.length;) {
            int end=begin+1;while(end<values.length&&values[end].value==values[begin].value)end++;
            long twiceMidrank=(long)begin+1+end;
            for(int i=begin;i<end;i++)if(values[i].control)twiceControlRanks+=twiceMidrank;
            if(end-begin>1){tiedBlocks++;tiedObservations+=end-begin;}begin=end;
        }
        return new RankSum(control.length,treated.length,twiceControlRanks/2.0-(long)control.length*(control.length+1)/2.0,tiedBlocks,tiedObservations);
    }
    private static void require(boolean accepted,String message){if(!accepted)throw new IllegalArgumentException(message);}
}
