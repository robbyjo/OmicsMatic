/* Copyright (C) 2026 JLinAlg contributors
 * Based on LDSC algorithms, Copyright (C) 2014-2015 Brendan Bulik-Sullivan
 * and Hilary Finucane. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Hard-call reference LD scores with selected CBIIT 3.0.2 window/chunk semantics.
 * The source is 6c673952cee74bd5c57aef1555a03b1c015399a0, ldscore/ldscore.py.
 * No ancestry, unrelatedness, genome assembly or annotation identity is inferred.
 */
public final class LdscLdScores {
    /** Variant-major 0/1/2 coded copies of the reference's second stored allele;
     * NaN means missing. readVariant may reuse its buffer after returning: the
     * algorithm immediately copies it. The underlying population must stay fixed.
     */
    public interface Genotypes {
        int samples();
        int variants();
        double[] readVariant(int index)throws IOException;
    }
    /** Whole-input/correlation/cache bounds. Memory is a conservative estimate,
     * not a replacement for sufficient JVM heap. No population is truncated. */
    public record Limits(int maximumVariants,int maximumSamples,int maximumAnnotations,
            long maximumOutputCells,long maximumCachedGenotypeCells,long maximumPairs,
            long maximumMultiplyAdds,long maximumEstimatedBytes) {
        public Limits {
            if(maximumVariants<1||maximumSamples<3||maximumAnnotations<1||maximumOutputCells<1
                    ||maximumCachedGenotypeCells<1||maximumPairs<1||maximumMultiplyAdds<1||maximumEstimatedBytes<1)
                throw new IllegalArgumentException("Positive complete LD-score limits and at least three sample capacity required");
        }
        public static Limits defaults(){return new Limits(1_000_000,1_000_000,256,100_000_000,
            128_000_000,1_000_000_000,2_000_000_000_000L,8L<<30);}
    }
    public record VariantAudit(int inputIndex,int called,int missing,Double allele2Frequency,
            Double maf,Double imputedMean,Double populationStandardDeviation,boolean retained,
            String decision,String genotypeSha256) { }
    /** All pairs within [start,end), plus pairs from [left,start) into this chunk.
     * The first chunk is the original algorithm's initial clique. */
    public record Chunk(int start,int end,int left) { }
    public record Result(int inputVariants,int samples,int annotations,int requestedChunkSize,
            int effectiveChunkSize,double window,double minimumMaf,boolean binaryAnnotations,
            int[] retainedInputIndices,int[] nominalWindowLefts,List<VariantAudit> variantAudit,
            List<Chunk> chunks,long correlations,long beyondNominalWindowPairs,
            long peakCachedGenotypeCells,long estimatedRetainedBytes,double[][] ldScores,
            double[] annotationCounts,double[] commonAnnotationCounts,int commonVariants,
            double[][] annotationOverlaps,double[][] commonAnnotationOverlaps,List<String> warnings) {
        public Result {
            retainedInputIndices=retainedInputIndices.clone();nominalWindowLefts=nominalWindowLefts.clone();
            variantAudit=List.copyOf(variantAudit);chunks=List.copyOf(chunks);ldScores=copy(ldScores);
            annotationCounts=annotationCounts.clone();commonAnnotationCounts=commonAnnotationCounts.clone();
            annotationOverlaps=annotationOverlaps==null?null:copy(annotationOverlaps);
            commonAnnotationOverlaps=commonAnnotationOverlaps==null?null:copy(commonAnnotationOverlaps);
            warnings=List.copyOf(warnings);
        }
        @Override public int[] retainedInputIndices(){return retainedInputIndices.clone();}
        @Override public int[] nominalWindowLefts(){return nominalWindowLefts.clone();}
        @Override public double[][] ldScores(){return copy(ldScores);}
        @Override public double[] annotationCounts(){return annotationCounts.clone();}
        @Override public double[] commonAnnotationCounts(){return commonAnnotationCounts.clone();}
        @Override public double[][] annotationOverlaps(){return annotationOverlaps==null?null:copy(annotationOverlaps);}
        @Override public double[][] commonAnnotationOverlaps(){return commonAnnotationOverlaps==null?null:copy(commonAnnotationOverlaps);}
    }
    private LdscLdScores(){ }
    private static double[][] copy(double[][] x){return Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);}
    private static void require(boolean condition,String why){if(!condition)throw new IllegalArgumentException(why);}
    private static double finite(double x){if(!Double.isFinite(x))throw new ArithmeticException("Unrepresentable LD-score intermediate/result; no result admitted");return x;}
    private static final class Sum {
        double value,correction;
        void add(double x){double adjusted=x-correction,next=finite(value+adjusted);correction=(next-value)-adjusted;value=next;}
    }
    private static void add(double[] sum,double[] correction,int column,double value){double adjusted=value-correction[column],next=finite(sum[column]+adjusted);correction[column]=(next-sum[column])-adjusted;sum[column]=next;}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("Required SHA-256 unavailable",impossible);}}
    private static String fingerprint(double[] calls,MessageDigest digest) {
        digest.reset();for(double value:calls){require(Double.isNaN(value)||value==0||value==1||value==2,"Only 0/1/2 hard calls or NaN missing calls are admitted; no dosage rounding");digest.update((byte)(Double.isNaN(value)?3:(int)value));}return HexFormat.of().formatHex(digest.digest());
    }
    private static double[] read(Genotypes source,int index,int n)throws IOException {
        double[] row=source.readVariant(index);require(row!=null&&row.length==n,"Every declared sample requires a hard-call field");return row.clone();
    }

    /** Computes selected source unbiased r² = r²-(1-r²)/(N-2), including negative
     * terms and self-correlations. Missing calls are observed-mean imputed before
     * population-SD normalization across all N declared individuals. Variants are
     * retained iff source MAF>minimumMaf and not all heterozygous/missing.
     * null annotations explicitly requests the unpartitioned all-ones column.
     * Nonnull annotations align to every input variant before this filter.
     */
    public static Result compute(Genotypes source,double[] coordinates,double[][] annotations,
            boolean binaryAnnotations,double window,int chunkSize,double minimumMaf,Limits limits)throws IOException {
        require(source!=null&&coordinates!=null&&limits!=null,"Complete genotype source, coordinates and limits required");
        int n=source.samples(),m=source.variants(),k=annotations==null?1:annotations.length==0?0:annotations[0]==null?0:annotations[0].length;
        require(n>=3&&n<=limits.maximumSamples()&&m>=1&&m<=limits.maximumVariants()&&coordinates.length==m,"Matching nonempty variant coordinates and 3..declared sample capacity required");
        require(k>=1&&k<=limits.maximumAnnotations()&&(annotations==null||annotations.length==m),"Matching complete annotation matrix required");
        require(Double.isFinite(window)&&window>=0&&chunkSize>0&&Double.isFinite(minimumMaf)&&minimumMaf>=0&&minimumMaf<=.5,"Finite nonnegative window, positive chunk size and MAF threshold in [0,.5] required");
        long cells=Math.multiplyExact((long)m,k);require(cells<=limits.maximumOutputCells(),"Complete annotation/output population exceeds cell capacity; no truncation");
        long baseBytes=Math.addExact(Math.multiplyExact(32,cells),Math.addExact(512L*m,24L*n));require(baseBytes<=limits.maximumEstimatedBytes(),"Complete LD-score population exceeds estimated retained-byte capacity");
        double[] coords=coordinates.clone();for(int i=0;i<m;i++)require(Double.isFinite(coords[i])&&(i==0||coords[i]>=coords[i-1]),"Finite nondecreasing coordinates required; no sorting or coordinate repair");finite(coords[m-1]-coords[0]);
        double[][] annot=new double[m][k];
        for(int i=0;i<m;i++) {
            require(annotations==null||annotations[i]!=null&&annotations[i].length==k,"Every input variant needs all annotation columns");
            for(int j=0;j<k;j++){double v=annotations==null?1:annotations[i][j];require(Double.isFinite(v)&&(!binaryAnnotations||v==0||v==1),"Finite annotations required; binary mode accepts only literal 0/1, with no thresholding");annot[i][j]=v;}
        }
        var audit=new ArrayList<VariantAudit>(m);int[] selected=new int[m];int retained=0;MessageDigest digest=digest();
        for(int variant=0;variant<m;variant++) {
            if(Thread.currentThread().isInterrupted())throw new IOException("LD-score filtering interrupted; no result admitted");
            double[] calls=read(source,variant,n);String hash=fingerprint(calls,digest);int called=0,heterozygous=0;long copies=0;
            for(double v:calls)if(!Double.isNaN(v)){called++;copies+=(int)v;if(v==1)heterozygous++;}
            Double frequency=called==0?null:(double)copies/(2L*called),maf=frequency==null?null:Math.min(frequency,1-frequency),mean=called==0?null:(double)copies/called;
            boolean keep=maf!=null&&maf>minimumMaf&&heterozygous+(n-called)<n;String decision=keep?"retained":called==0?"excluded-no-called-genotypes":maf<=minimumMaf?"excluded-maf-not-strictly-greater":"excluded-only-heterozygous-or-missing";Double sd=null;
            if(keep){Sum squared=new Sum();for(double v:calls)if(!Double.isNaN(v))squared.add((v-mean)*(v-mean));sd=finite(Math.sqrt(squared.value/n));if(!(sd>0))throw new ArithmeticException("Retained genotype has zero normalization SD; no result admitted");selected[retained++]=variant;}
            audit.add(new VariantAudit(variant,called,n-called,frequency,maf,mean,sd,keep,decision,hash));
        }
        require(retained>0,"All declared variants fail the explicit source MAF/variable-call filter; no LD scores admitted");selected=Arrays.copyOf(selected,retained);
        int[] lefts=new int[retained];int left=0;for(int i=0;i<retained;i++){while(left<i&&coords[selected[i]]-coords[selected[left]]>window)left++;lefts[i]=left;}
        int first=0;while(first<retained&&lefts[first]==0)first++;long rounded=((long)first+chunkSize-1)/chunkSize*chunkSize;int effective=chunkSize;
        if(rounded>retained){effective=1;first=retained;}else first=(int)rounded;
        var chunks=new ArrayList<Chunk>();chunks.add(new Chunk(0,first,0));long pairs=(long)first*(first+1L)/2;int maximumResident=first;
        for(int start=first;start<retained;) {
            int end=(int)Math.min((long)retained,(long)start+effective),span=start-lefts[start];int width=(int)(((long)span+effective-1)/effective*effective),from=start-width;
            require(from>=0&&from<=start,"Source chunk graph is inconsistent");chunks.add(new Chunk(start,end,from));int size=end-start;
            pairs=Math.addExact(pairs,Math.addExact((long)size*width,(long)size*(size+1L)/2));maximumResident=Math.max(maximumResident,end-from);start=end;
        }
        long cacheCells=Math.multiplyExact((long)maximumResident,n),work=Math.multiplyExact(pairs,n+2L*k);
        if(binaryAnnotations)work=Math.addExact(work,Math.multiplyExact((long)retained,k*(long)k));
        require(pairs<=limits.maximumPairs()&&work<=limits.maximumMultiplyAdds(),"Complete source chunk graph exceeds correlation/work capacity; no pairs or variants removed");
        require(cacheCells<=limits.maximumCachedGenotypeCells(),"Complete source chunk window exceeds normalized-genotype cache capacity");long bytes=Math.addExact(baseBytes,Math.multiplyExact(cacheCells,8));require(bytes<=limits.maximumEstimatedBytes(),"Complete source graph exceeds estimated retained-byte capacity");
        double[][] scores=new double[retained][k],corrections=new double[retained][k];double[] counts=new double[k],countCorrections=new double[k],common=new double[k],commonCorrections=new double[k];double[][] overlaps=binaryAnnotations?new double[k][k]:null,commonOverlaps=binaryAnnotations?new double[k][k]:null;int commonVariants=0;
        for(int i:selected) {
            if(Thread.currentThread().isInterrupted())throw new IOException("LD-score annotation counting interrupted; no result admitted");
            boolean isCommon=audit.get(i).maf()>.05;if(isCommon)commonVariants++;
            for(int a=0;a<k;a++){add(counts,countCorrections,a,annot[i][a]);if(isCommon)add(common,commonCorrections,a,annot[i][a]);}
            if(binaryAnnotations)for(int a=0;a<k;a++)if(annot[i][a]==1)for(int c=0;c<k;c++)if(annot[i][c]==1){overlaps[a][c]++;if(isCommon)commonOverlaps[a][c]++;}
        }
        TreeMap<Integer,double[]> cache=new TreeMap<>();int loaded=0;long beyond=0,computed=0,peak=0;
        for(Chunk chunk:chunks) {
            if(Thread.currentThread().isInterrupted())throw new IOException("LD-score accumulation interrupted; no result admitted");
            while(!cache.isEmpty()&&cache.firstKey()<chunk.left())cache.pollFirstEntry();
            while(loaded<chunk.end()) {
                if(Thread.currentThread().isInterrupted())throw new IOException("LD-score normalization interrupted; no result admitted");
                int original=selected[loaded];double[] calls=read(source,original,n);VariantAudit row=audit.get(original);
                if(!fingerprint(calls,digest).equals(row.genotypeSha256()))throw new IOException("Genotype calls changed after population filtering at input variant "+original+"; no result admitted");
                for(int sample=0;sample<n;sample++)calls[sample]=Double.isNaN(calls[sample])?0:finite((calls[sample]-row.imputedMean())/row.populationStandardDeviation());
                cache.put(loaded++,calls);
            }
            peak=Math.max(peak,(long)cache.size()*n);
            for(int i=chunk.start();i<chunk.end();i++) {
                if(Thread.currentThread().isInterrupted())throw new IOException("LD-score correlation accumulation interrupted; no result admitted");
                double[] x=cache.get(i),own=annot[selected[i]];
                for(int j=chunk.left();j<=i;j++) {
                    double[] y=cache.get(j);Sum dot=new Sum();for(int sample=0;sample<n;sample++)dot.add(x[sample]*y[sample]);double correlation=finite(dot.value/n),square=finite(correlation*correlation),unbiased=finite(square-(1-square)/(n-2));
                    double[] other=annot[selected[j]];for(int a=0;a<k;a++){add(scores[i],corrections[i],a,finite(unbiased*other[a]));if(i!=j)add(scores[j],corrections[j],a,finite(unbiased*own[a]));}
                    if(coords[selected[i]]-coords[selected[j]]>window)beyond++;computed++;
                }
            }
        }
        require(computed==pairs&&peak==cacheCells&&source.samples()==n&&source.variants()==m,"Complete source graph/provider population changed during analysis");cache.clear();
        var warnings=new ArrayList<String>();warnings.add("Reference hard calls use observed-mean imputation, population-SD normalization and unbiased r2 with the complete declared sample N. Negative contributions/scores are retained, not clipped. Filtering uses strict MAF>threshold and excludes all-heterozygous/missing variants, with every decision retained.");
        warnings.add("MAF and strict MAF>.05 counts follow the stored second-allele coding, including floating-point boundary behavior. Reference/sample ancestry, unrelatedness, map/annotation identity and genome assembly remain caller declarations. Genotype fingerprints are integrity checks, not authenticity signatures.");
        if(beyond>0)warnings.add("Selected upstream chunk semantics include "+beyond+" pairs beyond the nominal window. Chunk size is part of this estimator; it is not silently replaced by an exact-distance graph.");
        if(effective!=chunkSize)warnings.add("Selected source rounded the initial clique beyond the variant count and reset effective chunk size to 1 with the entire retained population as its initial clique. This source fallback is retained explicitly.");
        if(!binaryAnnotations)warnings.add("Continuous/signed annotations are retained. Binary overlap matrices are absent; annotation counts/sums must be interpreted under the declared annotation model.");
        return new Result(m,n,k,chunkSize,effective,window,minimumMaf,binaryAnnotations,selected,lefts,audit,chunks,pairs,beyond,peak,bytes,scores,counts,common,commonVariants,overlaps,commonOverlaps,warnings);
    }

    /** Existing BED reader supplies exact selected BIM order. Caller coordinates
     * and annotations must match that order; sample ordinals refer to its FAM.
     * A null sample list explicitly uses every FAM sample. BED A1 counts are
     * complemented to the selected original LDSC second-allele coding.
     * The caller keeps the dataset open and source files unchanged throughout.
     */
    public static Result fromPlink(PlinkBedDataset dataset,int[] sampleOrdinals,double[] coordinates,
            double[][] annotations,boolean binaryAnnotations,double window,int chunkSize,
            double minimumMaf,Limits limits)throws IOException {
        require(dataset!=null,"Open selected BED dataset required");var variants=dataset.selectedVariants();int total=dataset.samples().size();int[] keep;
        if(sampleOrdinals==null){keep=new int[total];for(int i=0;i<total;i++)keep[i]=i;}else keep=sampleOrdinals.clone();
        BitSet seen=new BitSet(total);for(int i:keep){require(i>=0&&i<total&&!seen.get(i),"Unique in-range FAM sample ordinals required");seen.set(i);}
        Genotypes source=new Genotypes(){public int samples(){return keep.length;}public int variants(){return variants.size();}public double[] readVariant(int index)throws IOException{double[] a1=dataset.readAllele1Dosages(variants.get(index)),a2=new double[keep.length];for(int i=0;i<keep.length;i++)a2[i]=2-a1[keep[i]];return a2;}};
        return compute(source,coordinates,annotations,binaryAnnotations,window,chunkSize,minimumMaf,limits);
    }
}
