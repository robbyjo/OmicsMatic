/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Native recovery curves, AUC, NES and exact leading-edge membership on prepared
 * cisTarget rankings. All supplied motif rows define the normalization population.
 * This component does not score DNA, read a database, annotate TFs or fit SCENIC.
 * Source profiles are explicit: ctxcore0.2.0 and RcisTarget1.29.0 iCisTarget
 * have different cutoff/curve coordinates and population-versus-sample SD.
 */
public final class CisTargetRecovery {
    private CisTargetRecovery() { }

    public enum Profile { CTXCORE_0_2_0, RCISTARGET_1_29_0_ICISTARGET }

    /** Targets use the caller's feature order, not NumPy's rank-sort display order. */
    public record Motif(double auc, double nes, int leadingRank, double leadingRecovery,
            double leadingExcess, int[] targetIndicesInInputOrder, String status) {
        public Motif { targetIndicesInInputOrder = targetIndicesInInputOrder.clone(); }
        @Override public int[] targetIndicesInInputOrder() { return targetIndicesInInputOrder.clone(); }
    }

    /** Owns newly calculated arrays; public accessors return defensive copies. */
    public static final class Result {
        private final Profile profile;
        private final int totalFeatures, selectedFeatures, roundedAucCount, sourceAucCutoff;
        private final int[] recoveryRanks;
        private final double[][] curves;
        private final double[] meanCurve, sdCurve, meanPlusTwoSd;
        private final double meanAuc, sdAuc;
        private final List<Motif> motifs;
        private final List<String> warnings;
        private Result(Profile profile, int totalFeatures, int selectedFeatures,
                int roundedAucCount, int sourceAucCutoff, int[] recoveryRanks,
                double[][] curves, double[] meanCurve, double[] sdCurve,
                double[] meanPlusTwoSd, double meanAuc, double sdAuc,
                List<Motif> motifs, List<String> warnings) {
            this.profile=profile; this.totalFeatures=totalFeatures; this.selectedFeatures=selectedFeatures;
            this.roundedAucCount=roundedAucCount; this.sourceAucCutoff=sourceAucCutoff;
            this.recoveryRanks=recoveryRanks; this.curves=curves; this.meanCurve=meanCurve;
            this.sdCurve=sdCurve; this.meanPlusTwoSd=meanPlusTwoSd;
            this.meanAuc=meanAuc; this.sdAuc=sdAuc; this.motifs=List.copyOf(motifs); this.warnings=List.copyOf(warnings);
        }
        public Profile profile() { return profile; }
        public int totalFeatures() { return totalFeatures; }
        public int selectedFeatures() { return selectedFeatures; }
        public int roundedAucCount() { return roundedAucCount; }
        public int sourceAucCutoff() { return sourceAucCutoff; }
        public int[] recoveryRanks() { return recoveryRanks.clone(); }
        public double[][] recoveryCurves() { return copy(curves); }
        public double[] meanCurve() { return meanCurve.clone(); }
        public double[] sdCurve() { return sdCurve.clone(); }
        public double[] meanPlusTwoSd() { return meanPlusTwoSd.clone(); }
        public double meanAuc() { return meanAuc; }
        public double sdAuc() { return sdAuc; }
        public List<Motif> motifs() { return motifs; }
        public List<String> warnings() { return warnings; }
    }

    /**
     * ctxcore0.2.0: zero-based integral ranks, explicit feature weights, AUC
     * fraction and exclusive recovery bound. The rounded AUC count is reduced
     * by one for integration; its unreduced value normalizes the area.
     */
    public static Result ctxcore(double[][] rankings, double[] weights,
            int totalFeatures, double aucFraction, int rankThreshold) {
        require(Double.isFinite(aucFraction) && aucFraction>0 && aucFraction<=1,
            "ctxcore AUC threshold must be an explicit fraction in(0,1]");
        require(rankThreshold>0 && rankThreshold<totalFeatures,
            "ctxcore recovery rank threshold must be positive and below total database features");
        int rounded=(int)Math.rint(aucFraction*totalFeatures);
        require(rounded>0 && rounded<=rankThreshold,
            "ctxcore rounded AUC count must be positive and no larger than the recovery rank threshold");
        return calculate(Profile.CTXCORE_0_2_0,rankings,weights,totalFeatures,rounded,rankThreshold);
    }

    /**
     * RcisTarget1.29.0 exact iCisTarget kernel: AUC parameter is an absolute raw
     * rank bound, with unit feature weights. R recovery coordinates are1..maxRank-1.
     * Raw rank numbers are used literally; no zero/one-based conversion is guessed.
     * The R approximate/smoothed leading-edge method is a separate operation.
     */
    public static Result rcisTarget(double[][] rankings, int totalFeatures,
            double aucMaxRank, int maxRank) {
        require(rankings!=null && rankings.length>0 && rankings[0]!=null,"Explicit motif/feature ranking matrix required");
        require(Double.isFinite(aucMaxRank) && aucMaxRank>0 && aucMaxRank<=totalFeatures,
            "RcisTarget absolute AUC rank must be positive and no larger than full database features");
        int rounded=(int)Math.rint(aucMaxRank);
        require(rounded>0,"RcisTarget rounded AUC count is zero; original normalization is undefined, no AUC admitted");
        require(maxRank>=2 && maxRank<=totalFeatures,
            "RcisTarget maxRank must leave at least one exact recovery point within the full database");
        double[] weights=new double[rankings[0].length];Arrays.fill(weights,1);
        return calculate(Profile.RCISTARGET_1_29_0_ICISTARGET,rankings,weights,totalFeatures,rounded,maxRank);
    }

    private static Result calculate(Profile profile,double[][] rankings,double[] weights,
            int totalFeatures,int rounded,int rankBound) {
        require(totalFeatures>=2,"Full database feature count must be explicit and at least two");
        require(rankings!=null && rankings.length>0 && weights!=null && weights.length>0,"Nonempty explicit motif ranks and selected-feature weights required");
        int m=rankings.length,n=weights.length;boolean python=profile==Profile.CTXCORE_0_2_0;
        int length=python?rankBound:rankBound-1;int cutoff=python?rounded-1:rounded;
        require(m<=100000 && n<=1000000 && (long)m*n<=250000000 && (long)m*length<=250000000,
            "Explicit ranking/recovery capacity exceeded; no motif or feature is truncated");
        for(double w:weights)require(Double.isFinite(w),"Feature weights must be finite");
        // recovery() appends a zero-weight sentinel before NumPy's reduction.
        double sumWeights=python?numpySum(Arrays.copyOf(weights,n+1),0,n+1):n;
        require(Double.isFinite(sumWeights) && sumWeights>0,"Source AUC weight sum must be finite and strictly positive");
        double denominator=rounded*sumWeights;require(Double.isFinite(denominator) && denominator>0,"Source maximum AUC overflow/underflow; no result admitted");
        double[][] curves=new double[m][length];double[] auc=new double[m];boolean infinity=false,negative=false;
        for(double w:weights)if(w<0)negative=true;
        for(int row=0;row<m;row++) {
            require(rankings[row]!=null && rankings[row].length==n,"Every motif must retain the same selected-feature order and width");
            double[] histogram=new double[rankBound];double rawRarea=0;
            for(int gene=0;gene<n;gene++) {
                double rank=rankings[row][gene];
                if(!python && rank==Double.POSITIVE_INFINITY){infinity=true;continue;}
                require(Double.isFinite(rank) && rank==Math.rint(rank) && rank>=0 && (python?rank<totalFeatures:rank<=totalFeatures),
                    "Ranks must be integral and in the declared database domain; only explicit R positive-infinity truncation is supported");
                if(rank<rankBound)histogram[(int)rank]+=weights[gene];
                if(!python && rank<cutoff)rawRarea+=(cutoff-rank)*weights[gene];
            }
            double recovered=histogram[0];int start=python?0:1;
            for(int coordinate=start;coordinate<rankBound;coordinate++) {
                if(coordinate>0)recovered+=histogram[coordinate];
                require(Double.isFinite(recovered),"Recovery sum overflow; no curve admitted");
                curves[row][coordinate-start]=recovered;
            }
            if(python)auc[row]=numpySum(curves[row],0,cutoff)/denominator;
            else auc[row]=rawRarea/denominator;
            require(Double.isFinite(auc[row]),"AUC overflow/undefined normalization; no numeric result admitted");
        }
        int degrees=python?m:m-1;double mean=python?numpySum(auc,0,m)/m:correctedMean(auc);double sd=python?numpySd(auc,mean):sd(auc,mean,degrees);
        double[] meanCurve=new double[length],sdCurve=new double[length],boundary=new double[length];
        for(int point=0;point<length;point++) {
            double sum=0;for(int row=0;row<m;row++)sum+=curves[row][point];meanCurve[point]=sum/m;
            double squares=0;for(int row=0;row<m;row++){double delta=curves[row][point]-meanCurve[point];squares+=delta*delta;}
            sdCurve[point]=degrees<=0?Double.NaN:Math.sqrt(squares/degrees);boundary[point]=meanCurve[point]+2*sdCurve[point];
            if(python && length==1) {
                double[] column=new double[m];for(int row=0;row<m;row++)column[row]=curves[row][point];
                meanCurve[point]=numpySum(column,0,m)/m;sdCurve[point]=numpySd(column,meanCurve[point]);boundary[point]=meanCurve[point]+2*sdCurve[point];
            }
            require(Double.isFinite(meanCurve[point]) && (degrees<=0 || Double.isFinite(boundary[point])),"Recovery-background moments overflow; no leading edge admitted");
        }
        int[] coordinates=new int[length];for(int point=0;point<length;point++)coordinates[point]=point+(python?0:1);
        var motifs=new ArrayList<Motif>();var warnings=new ArrayList<String>();
        for(int row=0;row<m;row++) {
            double nes=(auc[row]-mean)/sd;int peak=-1;double excess=Double.NaN;
            for(int point=0;point<length;point++) {
                double difference=curves[row][point]-boundary[point];
                if(Double.isFinite(difference) && (peak<0 || difference>excess)){peak=point;excess=difference;}
            }
            var targets=new ArrayList<Integer>();int rank=peak<0?-1:coordinates[peak];
            if(peak>=0)for(int gene=0;gene<n;gene++)if(rankings[row][gene]<=rank)targets.add(gene);
            motifs.add(new Motif(auc[row],nes,rank,peak<0?Double.NaN:curves[row][peak],excess,
                targets.stream().mapToInt(Integer::intValue).toArray(),Double.isFinite(nes)?"DEFINED_NES":"UNDEFINED_NES"));
        }
        if(!(sd>0) || !Double.isFinite(sd))warnings.add("NES is undefined for a zero/undefined motif-background SD. NaN is retained; it is not zero enrichment or a valid null result.");
        boolean identical=true;for(int row=1;row<m;row++)if(auc[row]!=auc[0])identical=false;
        if(python && identical && sd>0)warnings.add("SOURCE ROUNDING WARNING: all motif AUCs are identical, but NumPy1.26.4 pairwise mean/SD arithmetic gives a tiny nonzero SD and finite NES. The original finite values are retained; they are rounding artifacts, not biological enrichment. Inspect all-motifs.tsv before interpretation.");
        if(infinity)warnings.add("Explicit R positive-infinity ranks remain non-recovered/non-leading targets; original values are not imputed or relabeled as finite ranks.");
        if(negative)warnings.add("Supplied negative weights retain source arithmetic; recovery/AUC need not lie in ordinary positive-signature ranges. No clipping or absolute-value conversion is performed.");
        warnings.add("All supplied motif rows define the AUC and recovery background. Rank coordinates, source cutoff and SD convention are explicit; no rank-base conversion, database expansion or motif filtering is inferred.");
        warnings.add("Leading target indices are returned in caller feature order. Original NumPy rank-sorted display order is a separate representation; membership includes ties at the leading rank.");
        return new Result(profile,totalFeatures,n,rounded,cutoff,coordinates,curves,meanCurve,sdCurve,boundary,mean,sd,motifs,warnings);
    }

    /** ctxcore's independent weighted-step AUC route, retaining its reduced cutoff. */
    public static double ctxcoreDirectAuc(double[] ranking,double[] weights,int totalFeatures,double aucFraction) {
        require(ranking!=null && weights!=null && ranking.length==weights.length && weights.length>0,"Matching nonempty ranks/weights required");
        require(totalFeatures>=2 && Double.isFinite(aucFraction) && aucFraction>0 && aucFraction<=1,"Explicit database count and valid ctxcore AUC fraction required");
        int rounded=(int)Math.rint(aucFraction*totalFeatures);require(rounded>0 && rounded<totalFeatures,"Source rounded AUC count must be positive and below database size");
        int cutoff=rounded-1;double sumWeights=0;var order=new ArrayList<Integer>();
        for(int i=0;i<ranking.length;i++) {
            double rank=ranking[i];require(Double.isFinite(rank) && rank==Math.rint(rank) && rank>=0 && rank<totalFeatures && Double.isFinite(weights[i]),"Invalid source rank or weight");
            sumWeights+=weights[i];if(rank<cutoff)order.add(i);
        }
        sumWeights=numpySum(weights,0,weights.length);
        double maximum=rounded*sumWeights;require(Double.isFinite(maximum) && maximum>0,"Source maximum AUC must be finite and positive");
        order.sort((a,b)->Double.compare(ranking[a],ranking[b]));double area=0,cumulative=0;
        for(int j=0;j<order.size();j++) {
            int at=order.get(j);cumulative+=weights[at];double next=j+1<order.size()?ranking[order.get(j+1)]:cutoff;
            area+=(next-ranking[at])*cumulative;
        }
        double result=area/maximum;require(Double.isFinite(result),"Weighted-step AUC overflow; no result admitted");return result;
    }

    /** NumPy1.26.4 contiguous float64 reduction order, including its128-value blocks. */
    private static double numpySum(double[] values,int offset,int length) {
        if(length<8){double sum=-0.0;for(int i=0;i<length;i++)sum+=values[offset+i];return sum;}
        if(length<=128) {
            double[] lanes=Arrays.copyOfRange(values,offset,offset+8);int i=8;
            for(;i<length-length%8;i+=8)for(int lane=0;lane<8;lane++)lanes[lane]+=values[offset+i+lane];
            double sum=((lanes[0]+lanes[1])+(lanes[2]+lanes[3]))+((lanes[4]+lanes[5])+(lanes[6]+lanes[7]));
            for(;i<length;i++)sum+=values[offset+i];return sum;
        }
        int split=length/2;split-=split%8;return numpySum(values,offset,split)+numpySum(values,offset+split,length-split);
    }
    private static double numpySd(double[] values,double mean) {
        require(Double.isFinite(mean),"NumPy AUC/recovery background mean overflow");double[] squares=new double[values.length];
        for(int i=0;i<values.length;i++){double delta=values[i]-mean;squares[i]=delta*delta;}
        double sd=Math.sqrt(numpySum(squares,0,squares.length)/values.length);require(Double.isFinite(sd),"NumPy background SD overflow");return sd;
    }
    /** R's mean has a correction pass; it must not turn identical AUCs into finite NES. */
    private static double correctedMean(double[] x) {
        double sum=0,compensation=0;
        for(double v:x){double adjusted=v-compensation,next=sum+adjusted;compensation=(next-sum)-adjusted;sum=next;}
        double result=sum/x.length;require(Double.isFinite(result),"R AUC background mean overflow");
        double correction=0;for(double v:x)correction+=v-result;
        result+=correction/x.length;require(Double.isFinite(result),"R AUC mean correction overflow");return result;
    }
    private static double sd(double[] x,double mean,int degrees){if(degrees<=0)return Double.NaN;double sum=0;for(double v:x){double d=v-mean;sum+=d*d;}double result=Math.sqrt(sum/degrees);require(Double.isFinite(result),"AUC background SD overflow");return result;}
    private static double[][] copy(double[][] x){double[][] result=new double[x.length][];for(int i=0;i<x.length;i++)result[i]=x[i].clone();return result;}
    private static void require(boolean test,String message){if(!test)throw new IllegalArgumentException(message);}
}
