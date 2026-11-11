/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.Arrays;
import java.util.Objects;

/**
 * Literal ranked-score and observed-null arithmetic from GSEApy's Rust backend.
 *
 * <p>Weights, ordered hit positions and complete original null draws are explicit
 * inputs. This class does not generate permutations, select a ranking, fit a
 * GSVA kernel or replace an original RNG. Sparse and dense extrema have distinct
 * source tie/rounding behavior; neither is replaced by a generic GSEA helper.</p>
 */
public final class GseapyRankedStatistics {
    private GseapyRankedStatistics(){ }
    /** Dense observed walk and original sparse score; arrays are defensive copies. */
    public record Walk(double[] running,double sparseScore,double singleSampleScore){
        public Walk {running=running.clone();}
        @Override public double[] running(){return running.clone();}
    }
    /** Complete source-family outcomes, conditional on the supplied original null draws. */
    public record NullStatistics(double[] normalizedScores,double[] nominalP,double[] fdr,
            double[] familywiseP,double[][] normalizedNulls){
        public NullStatistics {normalizedScores=normalizedScores.clone();nominalP=nominalP.clone();fdr=fdr.clone();familywiseP=familywiseP.clone();normalizedNulls=copy(normalizedNulls);}
        @Override public double[] normalizedScores(){return normalizedScores.clone();}
        @Override public double[] nominalP(){return nominalP.clone();}
        @Override public double[] fdr(){return fdr.clone();}
        @Override public double[] familywiseP(){return familywiseP.clone();}
        @Override public double[][] normalizedNulls(){return copy(normalizedNulls);}
    }

    /**
     * Original dense scan, strict-absolute-improvement sparse extrema and fast
     * ssGSEA equation. Zero hit/complement masses retain IEEE NaN/Inf diagnostics;
     * no uniform fallback, epsilon or arbitrary tied extremum is introduced.
     */
    public static Walk walk(double[] weights,int[] suppliedHits){
        Objects.requireNonNull(weights,"weights");Objects.requireNonNull(suppliedHits,"hit indices");
        if(weights.length==0||weights.length>10000000)throw new IllegalArgumentException("Require1..10000000 explicit weights");
        for(double weight:weights)if(!Double.isFinite(weight)||weight<0)throw new IllegalArgumentException("Prepared weights must be finite and nonnegative");
        int[] hits=suppliedHits.clone();Arrays.sort(hits);boolean[] tag=new boolean[weights.length];
        for(int i=0;i<hits.length;i++){int at=hits[i];if(at<0||at>=weights.length||i>0&&at==hits[i-1])throw new IllegalArgumentException("Hit indices must be unique and in range");tag[at]=true;}
        double mass=0;for(int i=0;i<weights.length;i++)mass+=(tag[i]?1.0:0.0)*weights[i];
        double hitNorm=1.0/mass,missNorm=1.0/(weights.length-hits.length);double[] running=new double[weights.length];double acc=0;
        for(int i=0;i<weights.length;i++){double marker=tag[i]?1:0;acc+=marker*weights[i]*hitNorm-(1-marker)*missNorm;running[i]=acc;}
        double sparse=0,current=0,last=-1;
        for(int at:hits){current-=missNorm*(at-last-1.0);if(Math.abs(current)>Math.abs(sparse))sparse=current;current+=hitNorm*weights[at];if(Math.abs(current)>Math.abs(sparse))sparse=current;last=at;}
        double numerator=0,selectedMass=0,positions=0,n=weights.length;
        for(int at:hits){numerator+=weights[at]*(n-at);selectedMass+=weights[at];positions+=n-at;}
        double single=numerator/selectedMass-(n*(n+1.0)/2.0-positions)/(n-hits.length);
        return new Walk(running,sparse,single);
    }

    /**
     * Literal dense helper on signed metrics. Original phenotype GSEA records
     * this raw signed walk while its observed/null ES uses absolute power
     * weights. These are different source quantities; do not repair one to
     * agree with the other. Zero/cancelling signed mass retains IEEE diagnostics.
     */
    public static double[] rawRunning(double[] metrics,int[] suppliedHits){
        Objects.requireNonNull(metrics,"signed metrics");Objects.requireNonNull(suppliedHits,"hit indices");if(metrics.length==0||metrics.length>10000000)throw new IllegalArgumentException("Require1..10000000 explicit signed metrics");for(double value:metrics)if(!Double.isFinite(value))throw new IllegalArgumentException("Raw signed metrics must be finite");int[] hits=suppliedHits.clone();Arrays.sort(hits);boolean[] tag=new boolean[metrics.length];
        for(int i=0;i<hits.length;i++){int at=hits[i];if(at<0||at>=metrics.length||i>0&&at==hits[i-1])throw new IllegalArgumentException("Hit indices must be unique and in range");tag[at]=true;}
        double mass=0;for(int i=0;i<metrics.length;i++)mass+=(tag[i]?1.0:0.0)*metrics[i];double hitNorm=1.0/mass,missNorm=1.0/(metrics.length-hits.length),acc=0;double[] out=new double[metrics.length];for(int i=0;i<metrics.length;i++){double marker=tag[i]?1:0;acc+=marker*metrics[i]*hitNorm-(1-marker)*missNorm;out[i]=acc;}return out;
    }

    /**
     * Source normalization, sign-conditional nominal P, pooled-tail FDR and
     * original columnwise FWER. Null arrays must form the complete rectangular
     * family with1..1000000 draws and at most25000000 total values.
     * Missing-sign means fall back to the observed ES; nominal P becomes one
     * when its sign has no draws. Negative FWER uses the source strict tail and
     * can remain NaN if no negative column minima exist. No add-one correction,
     * BH adjustment or monotonic FDR adjustment is substituted.
     */
    public static NullStatistics fromNulls(double[] observed,double[][] nulls){
        Objects.requireNonNull(observed,"observed scores");Objects.requireNonNull(nulls,"original null draws");
        if(observed.length==0||observed.length!=nulls.length)throw new IllegalArgumentException("Require the complete nonempty observed/null family");
        int permutations=Objects.requireNonNull(nulls[0],"null row").length;
        if(permutations==0||permutations>1000000||(long)permutations*observed.length>25000000)throw new IllegalArgumentException("Explicit permutation family exceeds the supported bound");
        int terms=observed.length;double[] nes=new double[terms],p=new double[terms],q=new double[terms],fwer=new double[terms];double[][] normalized=new double[terms][permutations];double[] pooled=new double[terms*permutations],maxima=new double[permutations],minima=new double[permutations];
        for(int i=0;i<terms;i++){
            double e=observed[i];if(!Double.isFinite(e)||nulls[i]==null||nulls[i].length!=permutations)throw new IllegalArgumentException("Require finite source ES and a rectangular complete null family");
            double positive=0,negative=0;int nPositive=0,nNegative=0,nExtreme=0;
            for(double value:nulls[i]){
                if(!Double.isFinite(value))throw new IllegalArgumentException("Original pooled-null sort cannot admit a nonfinite draw");
                if(value>=0){positive+=value;nPositive++;}else{negative+=value;nNegative++;}
                if(e<0?value<=e:value>=e)nExtreme++;
            }
            double posMean=nPositive>0?positive/nPositive:e,negMean=nNegative>0?negative/nNegative:e;
            nes[i]=e>=0?e/posMean:e/Math.abs(negMean);int denominator=e<0?nNegative:nPositive;p[i]=denominator==0?1.0:(double)nExtreme/denominator;
            if(!Double.isFinite(nes[i]))throw new IllegalArgumentException("Native normalized-score audit requires finite sort inputs; retain the original nonfinite state and its source-only diagnostic disposition");
            for(int j=0;j<permutations;j++){
                double v=nulls[i][j],scaled=v>=0?v/posMean:v/Math.abs(negMean);normalized[i][j]=scaled;pooled[i*permutations+j]=scaled;
                if(!Double.isFinite(scaled))throw new IllegalArgumentException("Native normalized-null audit requires finite sort inputs; retain the original nonfinite state and its source-only diagnostic disposition");
                if(scaled>=0)maxima[j]=Math.max(scaled,maxima[j]);else minima[j]=Math.min(scaled,minima[j]);
            }
        }
        // FWER is computed before the source FDR routine sorts pooled nulls.
        for(int i=0;i<terms;i++){
            int extreme=0,negativeColumns=0;
            for(int j=0;j<permutations;j++){
                if(nes[i]<0){if(minima[j]<nes[i])extreme++;if(minima[j]<0)negativeColumns++;}
                else if(maxima[j]>=nes[i])extreme++;
            }
            fwer[i]=(double)extreme/(nes[i]<0?negativeColumns:permutations);
        }
        double[] sorted=nes.clone();Arrays.sort(sorted);Arrays.sort(pooled);int nullNegative=lower(pooled,0),obsNegative=lower(sorted,0);
        for(int i=0;i<terms;i++){
            double e=nes[i];int nullTail,obsTail,nullSign,obsSign;
            if(e<0){nullTail=upper(pooled,e);obsTail=upper(sorted,e);nullSign=nullNegative;obsSign=obsNegative;}
            else{nullTail=pooled.length-lower(pooled,e);obsTail=sorted.length-lower(sorted,e);nullSign=pooled.length-nullNegative;obsSign=sorted.length-obsNegative;}
            double phiNorm=nullSign>0?(double)nullTail/nullSign:0,phiObs=obsSign>0?(double)obsTail/obsSign:0;
            q[i]=Math.max(-Double.MAX_VALUE,Math.min(1.0,phiNorm/phiObs));
        }
        return new NullStatistics(nes,p,q,fwer,normalized);
    }

    /** Original permutation-free ssGSEA normalization over all term/sample scores together. */
    public static double[] normalizeSingleSample(double[] scores){
        Objects.requireNonNull(scores,"complete term/sample score family");if(scores.length==0)throw new IllegalArgumentException("Require a nonempty score family");double maximum=-Double.MAX_VALUE,minimum=Double.MAX_VALUE;
        for(double score:scores){if(!Double.isNaN(score)){maximum=Math.max(maximum,score);minimum=Math.min(minimum,score);}}
        double range=maximum-minimum;double[] out=new double[scores.length];for(int i=0;i<out.length;i++)out[i]=scores[i]/range;return out;
    }
    private static int lower(double[] sorted,double value){int lo=0,hi=sorted.length;while(lo<hi){int mid=(lo+hi)>>>1;if(sorted[mid]<value)lo=mid+1;else hi=mid;}return lo;}
    private static int upper(double[] sorted,double value){int lo=0,hi=sorted.length;while(lo<hi){int mid=(lo+hi)>>>1;if(sorted[mid]<=value)lo=mid+1;else hi=mid;}return lo;}
    private static double[][] copy(double[][] source){double[][] out=new double[source.length][];for(int i=0;i<out.length;i++)out[i]=source[i].clone();return out;}
}
