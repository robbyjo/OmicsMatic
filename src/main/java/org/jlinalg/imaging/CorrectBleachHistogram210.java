/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.imaging;

/**
 * Complete integer-histogram lookup semantics of CorrectBleach 2.1.0's
 * histogram2.HistogramMatcher and Util.Cdf, including signed-int overflow,
 * zero totals, ties and the original untested final reference bin.
 * <p>The original source and notices are retained with the versioned workflow.
 * Sorted finite reference CDF prefixes use binary search; other prefixes use
 * the literal original reverse scan. No histogram bins are omitted or repaired.
 * This component supplies a lookup, not a biological normalization guarantee.
 */
public final class CorrectBleachHistogram210 {
    private CorrectBleachHistogram210() { }

    /** Complete original mapping with no caller comparison limit. */
    public static int[] matchHistograms(int[] target,int[] reference){
        return matchHistograms(target,reference,Long.MAX_VALUE);
    }

    /**
     * Complete mapping with an explicit comparison budget. Inputs are not modified.
     * @throws IllegalArgumentException if the budget is invalid or is exhausted;
     *         no partial mapping is returned
     */
    public static int[] matchHistograms(int[] target,int[] reference,long maximumComparisons){
        if(maximumComparisons<1)throw new IllegalArgumentException("Require a positive complete histogram comparison budget");
        int bins=target.length;double[] a=cdf(target),r=cdf(reference);int[] result=new int[bins];
        boolean sorted=r.length>=bins-1;
        if(sorted)for(int i=0;i<bins-1;i++)if(!Double.isFinite(r[i])||(i>0&&r[i]<r[i-1])){sorted=false;break;}
        long comparisons=0;
        for(int i=0;i<bins;i++){
            if(sorted){
                int low=0,high=bins-1;
                while(low<high){
                    if(comparisons==maximumComparisons)throw exhausted();comparisons++;
                    int middle=low+(high-low)/2;
                    if(a[i]<=r[middle])high=middle;else low=middle+1;
                }
                result[i]=low;
            }else{
                int j=bins-1;
                do{
                    result[i]=j;j--;
                    if(j<0)break;
                    if(comparisons==maximumComparisons)throw exhausted();comparisons++;
                    if(!(a[i]<=r[j]))break;
                }while(true);
            }
        }
        return result;
    }

    /** Original complete CDF with Java signed-int summation and raw double division. */
    public static double[] cdf(int[] histogram){
        int bins=histogram.length,total=0;for(int value:histogram)total+=value;
        double[] result=new double[bins];int cumulative=histogram[0];result[0]=(double)cumulative/total;
        for(int i=1;i<bins;i++){cumulative+=histogram[i];result[i]=(double)cumulative/total;}
        return result;
    }
    private static IllegalArgumentException exhausted(){return new IllegalArgumentException("Complete original histogram comparison budget exceeded; no prefix mapping accepted");}
}
