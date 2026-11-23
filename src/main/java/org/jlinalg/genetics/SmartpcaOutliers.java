/*
 * Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source behavior: EIG 7.2.1 smartsubs.c, 4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1.
 * Original copyright (c) 2006-2016 Broad Institute, Inc. and Harvard Medical
 * School; the BSD notice is retained in org/jlinalg/omics/smartpca-4f7e9f0/.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Original strict smartpca outlier rules, including its leave-one-out denominator. */
public final class SmartpcaOutliers {
    private SmartpcaOutliers() { }
    public enum Mode { ALL_SAMPLES, LEAVE_ONE_OUT, DISABLED }
    public record Limits(long maximumProducts,long maximumRetainedBytes) {
        public Limits { require(maximumProducts>0&&maximumRetainedBytes>0,"Positive outlier work/storage limits required"); }
        public static Limits defaults(){return new Limits(2_000_000_000L,1_073_741_824L);}
    }
    /** Nonfinite diagnostic scores are retained; they are never repaired to zero. */
    public record Hit(int sampleOrdinal, int zeroBasedAxis, double signedDeviation) { }
    public record Result(List<Hit> hits, double[][] deviations, boolean nonfiniteDiagnostics,
            boolean skippedSmallBasis, boolean disabled) {
        public Result { hits=List.copyOf(hits);deviations=copy(deviations); }
        @Override public double[][] deviations() { return copy(deviations); }
    }

    /**
     * Input is sample-by-axis. Each sample is marked at most once, using its
     * first qualifying axis; hit order follows sample order. Source mode1
     * excludes the tested sample from the mean but divides variance by n.
     */
    public static Result detect(double[][] vectors, int axes, double threshold, Mode mode) {
        return detect(vectors,axes,threshold,mode,Limits.defaults());
    }
    public static Result detect(double[][] vectors, int axes, double threshold, Mode mode,Limits limits) {
        Objects.requireNonNull(vectors,"vectors");Objects.requireNonNull(mode,"mode");
        Objects.requireNonNull(limits,"limits");
        require(vectors.length>0 && axes>=0 && Double.isFinite(threshold) && threshold>0,
            "Require a nonempty basis, nonnegative axis count and positive finite outlier threshold");
        int n=vectors.length;
        admit(n,axes,mode,limits);
        for(double[] row:vectors) {
            require(row!=null && row.length>=axes,"Incomplete outlier axis matrix");
            for(int c=0;c<axes;c++)require(Double.isFinite(row[c]),"Nonfinite input principal axis; no outlier normalization repair");
        }
        if(mode==Mode.DISABLED || n<3)return new Result(List.of(),new double[n][0],false,n<3,mode==Mode.DISABLED);
        double[][] deviations=new double[n][axes];int[] first=new int[n];Arrays.fill(first,-1);
        double[] firstScore=new double[n];boolean nonfinite=false;
        for(int c=0;c<axes;c++) {
            interrupted();
            if(mode==Mode.ALL_SAMPLES) {
                double mean=0;for(int i=0;i<n;i++)mean+=vectors[i][c];mean/=n;
                double variance=0;for(int i=0;i<n;i++){double value=vectors[i][c]-mean;variance+=value*value;}
                double reciprocal=1/Math.sqrt(variance/n);
                for(int i=0;i<n;i++)deviations[i][c]=(vectors[i][c]-mean)*reciprocal;
            } else {
                for(int i=0;i<n;i++) {
                    if((i&63)==0)interrupted();
                    double mean=0;for(int j=0;j<n;j++)if(j!=i)mean+=vectors[j][c];mean/=(n-1);
                    double variance=0;for(int j=0;j<n;j++)if(j!=i){double value=vectors[j][c]-mean;variance+=value*value;}
                    deviations[i][c]=(vectors[i][c]-mean)/Math.sqrt(variance/n);
                }
            }
            for(int i=0;i<n;i++) {
                double score=deviations[i][c];nonfinite|=!Double.isFinite(score);
                // Preserve source NaN comparisons and signed infinite diagnostics.
                if(Math.abs(score)>threshold && first[i]<0){first[i]=c;firstScore[i]=score;}
            }
        }
        var hits=new ArrayList<Hit>();
        for(int i=0;i<n;i++)if(first[i]>=0)hits.add(new Hit(i,first[i],firstScore[i]));
        return new Result(hits,deviations,nonfinite,false,false);
    }
    private static void admit(int n,int axes,Mode mode,Limits limits) {
        try {
            long cells=Math.multiplyExact((long)n,axes),products=Math.multiplyExact(4L,cells);
            if(mode==Mode.LEAVE_ONE_OUT)products=Math.multiplyExact(products,n);
            long bytes=Math.addExact(Math.multiplyExact(cells,24),Math.multiplyExact(128L,n));
            require(products<=limits.maximumProducts()&&bytes<=limits.maximumRetainedBytes(),
                "Complete outlier calculation exceeds explicit work/storage limits; supply sufficient resources without automatic subsetting");
        }catch(ArithmeticException overflow){throw new IllegalArgumentException("Outlier dimensions exceed representable work/storage bounds",overflow);}
    }
    private static void interrupted(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Smartpca outlier calculation interrupted; no complete result");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static double[][] copy(double[][] matrix){return Arrays.stream(matrix).map(double[]::clone).toArray(double[][]::new);}
}
