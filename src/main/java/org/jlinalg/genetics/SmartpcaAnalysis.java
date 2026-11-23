/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Original EIG 7.2.1 behavior and BSD notice: org/jlinalg/omics/smartpca-4f7e9f0/.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Complete declared native PCA passes, permanent marker exclusions and optional shrinkmode. */
public final class SmartpcaAnalysis {
    private SmartpcaAnalysis() { }
    public record Options(SmartpcaPrepared.Options pca,int maximumOutlierIterations,int outlierAxes,
            double outlierThreshold,SmartpcaOutliers.Mode outlierMode,boolean shrinkage) {
        public Options {
            Objects.requireNonNull(pca,"pca");Objects.requireNonNull(outlierMode,"outlierMode");
            require(maximumOutlierIterations>=0 && maximumOutlierIterations<=100 && outlierAxes>=0,
                "Outlier iterations must be in0..100 and axis count nonnegative");
            require(Double.isFinite(outlierThreshold)&&outlierThreshold>0,"Positive finite outlier threshold required");
        }
    }
    public record Removal(int originalSampleIndex,int iteration,int zeroBasedAxis,double signedDeviation) { }
    public record Pass(int iteration,int[] basisIndices,int[] markerIndices,int usedMarkers,
            double[] eigenvalues,List<Removal> removals,boolean nonfiniteOutlierDiagnostics) {
        public Pass { basisIndices=basisIndices.clone();markerIndices=markerIndices.clone();eigenvalues=eigenvalues.clone();removals=List.copyOf(removals); }
        @Override public int[] basisIndices(){return basisIndices.clone();}
        @Override public int[] markerIndices(){return markerIndices.clone();}
        @Override public double[] eigenvalues(){return eigenvalues.clone();}
    }
    public enum SampleStatus { COMPUTED, INSUFFICIENT_NONMISSING_MARKERS, REMOVED_OUTLIER,
        SOURCE_SHRINKAGE_OUTPUT_FOR_REMOVED_OUTLIER }
    public record Result(int[] basisIndices,int[] markerIndices,SmartpcaPrepared.Result finalBasis,
            List<Pass> passes,List<Removal> removals,double[] eigenvalues,double[][] scores,
            List<SampleStatus> sampleStatus,int[] observedUsedMarkers,SmartpcaShrinkage.Result shrinkage) {
        public Result {
            basisIndices=basisIndices.clone();markerIndices=markerIndices.clone();passes=List.copyOf(passes);removals=List.copyOf(removals);
            eigenvalues=eigenvalues.clone();scores=copy(scores);sampleStatus=List.copyOf(sampleStatus);observedUsedMarkers=observedUsedMarkers.clone();
        }
        @Override public int[] basisIndices(){return basisIndices.clone();}
        @Override public int[] markerIndices(){return markerIndices.clone();}
        @Override public double[] eigenvalues(){return eigenvalues.clone();}
        @Override public double[][] scores(){return copy(scores);}
        @Override public int[] observedUsedMarkers(){return observedUsedMarkers.clone();}
    }
    public static Result analyze(byte[][] calls,int[] basis,Options options) { return analyze(calls,basis,options,pass->{ }); }

    /** Observer receives completed immutable pass diagnostics before a later numerical refusal. */
    public static Result analyze(byte[][] calls,int[] selected,Options options,Consumer<Pass> observer) {
        Objects.requireNonNull(calls,"calls");Objects.requireNonNull(selected,"basis");Objects.requireNonNull(options,"options");Objects.requireNonNull(observer,"observer");
        require(calls.length>0&&calls[0]!=null&&calls[0].length>0&&selected.length>=2,"Nonempty complete panel and fitting basis required");
        admit(calls.length,calls[0].length,selected.length,options);
        int samples=calls[0].length;int[] basis=selected.clone();int[] markerIndices=new int[calls.length];
        for(int i=0;i<markerIndices.length;i++)markerIndices[i]=i;
        var passes=new ArrayList<Pass>();var removals=new ArrayList<Removal>();boolean[] removed=new boolean[samples];
        SmartpcaPrepared.Result fit=null;byte[][] current=null;
        for(int iteration=1;iteration<=options.maximumOutlierIterations()+1;iteration++) {
            interrupted();require(basis.length>=2,"Source outlier removal left fewer than two fitting individuals; no complete result");
            current=new byte[markerIndices.length][];for(int i=0;i<current.length;i++)current[i]=calls[markerIndices[i]];
            int outAxes=(options.outlierMode()==SmartpcaOutliers.Mode.DISABLED||iteration>options.maximumOutlierIterations())?0:Math.min(options.outlierAxes(),basis.length-1);
            int axes=Math.max(options.pca().components(),outAxes);
            var p=options.pca();var primary=new SmartpcaPrepared.Options(axes,p.useNorm(),p.alternateNormStyle(),p.minimumAlleleCount(),p.maximumMissing(),SmartpcaPrepared.Projection.ORTHOGONAL,p.limits());
            fit=SmartpcaPrepared.fit(current,basis,primary);
            SmartpcaOutliers.Result detected=outAxes==0?null:SmartpcaOutliers.detect(fit.eigenvectors(),outAxes,options.outlierThreshold(),options.outlierMode(),
                new SmartpcaOutliers.Limits(p.limits().maximumProducts(),p.limits().maximumRetainedBytes()));
            var thisPass=new ArrayList<Removal>();
            if(detected!=null)for(var hit:detected.hits()) {
                var removal=new Removal(basis[hit.sampleOrdinal()],iteration,hit.zeroBasedAxis(),hit.signedDeviation());
                thisPass.add(removal);removals.add(removal);removed[removal.originalSampleIndex()]=true;
            }
            int used=(int)fit.markers().stream().filter(marker->marker.status()==SmartpcaPrepared.MarkerStatus.USED).count();
            var pass=new Pass(iteration,basis,markerIndices,used,fit.eigenvalues(),thisPass,detected!=null&&detected.nonfiniteDiagnostics());passes.add(pass);observer.accept(pass);
            if(thisPass.isEmpty())break;
            basis=Arrays.stream(basis).filter(index->!removed[index]).toArray();
            // loadsnpx on the next original pass permanently omits markers ignored
            // on the preceding pass, even if a missingness count later decreases.
            int[] next=new int[used];int at=0;
            for(var marker:fit.markers())if(marker.status()==SmartpcaPrepared.MarkerStatus.USED)next[at++]=markerIndices[marker.ordinal()];
            markerIndices=next;
        }
        require(fit!=null&&current!=null,"No completed native PCA basis");
        var p=options.pca();SmartpcaShrinkage.Result shrink=null;double[][] scores;double[] eigenvalues;
        var statuses=new ArrayList<SampleStatus>();
        for(int i=0;i<samples;i++)statuses.add(SampleStatus.COMPUTED);
        if(options.shrinkage()) {
            shrink=SmartpcaShrinkage.fit(current,basis,p);scores=shrink.scores();eigenvalues=shrink.eigenvalues();
            for(int i=0;i<samples;i++)if(removed[i])statuses.set(i,SampleStatus.SOURCE_SHRINKAGE_OUTPUT_FOR_REMOVED_OUTLIER);
        } else {
            SmartpcaPrepared.Result projected;
            if(p.projection()==SmartpcaPrepared.Projection.ORTHOGONAL)projected=fit;
            else {
                byte[][] masked=current;
                if(!removals.isEmpty()) {
                    masked=new byte[current.length][];
                    for(int s=0;s<current.length;s++){masked[s]=current[s].clone();for(int sample=0;sample<samples;sample++)if(removed[sample])masked[s][sample]=9;}
                }
                projected=SmartpcaPrepared.fit(masked,basis,p);
            }
            double[][] complete=projected.scores();scores=new double[samples][p.components()];eigenvalues=projected.eigenvalues();
            for(int i=0;i<samples;i++) {
                if(removed[i]){statuses.set(i,SampleStatus.REMOVED_OUTLIER);Arrays.fill(scores[i],Double.NaN);}
                else if(projected.scoreStatus().get(i)==SmartpcaPrepared.ScoreStatus.INSUFFICIENT_NONMISSING_MARKERS){statuses.set(i,SampleStatus.INSUFFICIENT_NONMISSING_MARKERS);Arrays.fill(scores[i],Double.NaN);}
                else System.arraycopy(complete[i],0,scores[i],0,p.components());
            }
        }
        int[] observed=new int[samples];
        for(var marker:fit.markers())if(marker.status()==SmartpcaPrepared.MarkerStatus.USED)
            for(int i=0;i<samples;i++)if(current[marker.ordinal()][i]!=9)observed[i]++;
        return new Result(basis,markerIndices,fit,passes,removals,eigenvalues,scores,statuses,observed,shrink);
    }
    private static void admit(int m,int samples,int n,Options options) {
        try {
            int k=Math.max(options.pca().components(),Math.min(options.outlierAxes(),n-1));
            long nn=Math.multiplyExact((long)n,n),n3=Math.multiplyExact(nn,n),mk=Math.multiplyExact((long)m,k);
            long perPass=Math.addExact(Math.multiplyExact(2L*m,nn),Math.addExact(Math.multiplyExact(8L,n3),Math.multiplyExact(4L*samples,Math.multiplyExact(mk,k))));
            long products=Math.multiplyExact(perPass,options.maximumOutlierIterations()+2L);
            if(options.shrinkage()) {
                long q=options.pca().components(),q2=Math.multiplyExact(q,q),q3=Math.multiplyExact(q2,q),q4=Math.multiplyExact(q3,q);
                products=Math.addExact(products,Math.multiplyExact(4*q,Math.multiplyExact(nn,m)));
                products=Math.addExact(products,Math.multiplyExact(4L*m,Math.multiplyExact(n,q3)));
                products=Math.addExact(products,Math.multiplyExact(4L*n,q4));
                products=Math.addExact(products,Math.multiplyExact(4*q,n3));
            }
            require(products<=options.pca().limits().maximumProducts(),"Complete declared PCA/outlier/shrinkage passes exceed explicit work bound; supply sufficient resources without automatic subsetting");
        }catch(ArithmeticException overflow){throw new IllegalArgumentException("PCA analysis dimensions exceed representable work bounds",overflow);}
    }
    private static void interrupted(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Native smartpca analysis interrupted; no complete result");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static double[][] copy(double[][] matrix){return Arrays.stream(matrix).map(double[]::clone).toArray(double[][]::new);}
}
