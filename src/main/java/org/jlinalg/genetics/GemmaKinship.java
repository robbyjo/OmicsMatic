/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded Java implementation of the PLINK hard-call kinship rules in original
 * GEMMA 0.98.1/0.98.3/0.98.5 (ReadFile_bed, PlinkKin and CalcHWE).
 *
 * <p>The caller supplies an explicit complete-case QC sample mask and design in
 * FAM order. QC uses that subset; the resulting matrix uses ALL FAM samples.
 * Missing genotypes are mean-imputed separately in these two populations.
 * Standardization uses observed variance after mean imputation, not 2p(1-p).
 * Matrices are averages over used variants, without trace normalization.
 * This component does not select phenotype/covariate columns, fit associations,
 * infer sex/ploidy, perform LOCO or reproduce an entire GEMMA analysis.</p>
 *
 * <p>Original algorithms: Zhou et al., GPL-3.0, exact source commits recorded in
 * docs/literature/GEMMA_WORKFLOWS.md. HWE deliberately uses the original strict
 * probability ordering; HardyWeinberg's separate near-tie policy is not used.</p>
 */
public final class GemmaKinship {
    public enum Mode { CENTERED, STANDARDIZED }
    public enum Status { USED, MISSINGNESS, FREQUENCY, CONSTANT, HWE, COVARIATE_R2 }

    /** Explicit original QC settings and conservative numeric/work admission bounds. */
    public record Options(Mode mode, double minimumFrequency, double maximumMissingness,
            double minimumHwe, double maximumCovariateFraction,
            long maximumNumericBytes, long maximumMultiplyAdds) {
        public Options {
            Objects.requireNonNull(mode,"Kinship mode");
            if (!Double.isFinite(minimumFrequency) || minimumFrequency != -1 && (minimumFrequency < 0 || minimumFrequency > .5)
                    || !Double.isFinite(maximumMissingness) || maximumMissingness < 0 || maximumMissingness > 1
                    || !Double.isFinite(minimumHwe) || minimumHwe < 0 || minimumHwe > 1
                    || !Double.isFinite(maximumCovariateFraction) || maximumCovariateFraction < 0 || maximumCovariateFraction > 1
                    || maximumNumericBytes < 1 || maximumMultiplyAdds < 1)
                throw new IllegalArgumentException("Invalid explicit GEMMA kinship settings or capacities");
        }
    }

    /** QC frequency counts BIM A1; it is not folded to a minor-allele frequency. */
    public record Predictor(String id, long sourceOrdinal, int qcCalled, int qcMissing,
            double qcAllele1Frequency, double hweP, double covariateFraction,
            int allCalled, double allMean, double allImputedVariance, Status status) { }

    public static final class Result {
        private final List<PlinkBedDataset.Sample> samples;
        private final List<Predictor> predictors;
        private final double[] matrix;
        private final int used, qcSamples;
        private Result(List<PlinkBedDataset.Sample> samples,List<Predictor> predictors,
                double[] matrix,int used,int qcSamples) {
            this.samples=List.copyOf(samples); this.predictors=List.copyOf(predictors);
            this.matrix=matrix; this.used=used; this.qcSamples=qcSamples;
        }
        public List<PlinkBedDataset.Sample> samples(){return samples;}
        public List<Predictor> predictors(){return predictors;}
        public int predictorsUsed(){return used;}
        public int qcSamples(){return qcSamples;}
        /** Returns a caller-owned row-major matrix copy. Its allocation is separate. */
        public double[] matrix(){return matrix.clone();}
        public double value(int row,int column){
            int n=samples.size();
            if(row<0||column<0||row>=n||column>=n)throw new IndexOutOfBoundsException("Kinship matrix coordinate");
            return matrix[row*n+column];
        }
    }

    private GemmaKinship(){ }

    /**
     * Calculate in exact FAM order from the dataset's explicit BIM selection.
     * Design has one row per FAM sample and an explicit first column of ones on
     * admitted QC samples. Other columns must be finite, nonconstant and full
     * rank there. No implicit intercept insertion or column removal is made.
     * Values in excluded design rows are ignored. Caller inputs are not changed.
     * The capacity estimate covers principal numeric arrays, not JVM/metadata RSS.
     */
    public static Result calculate(PlinkBedDataset data,boolean[] qcSamples,
            double[][] design,Options options)throws IOException {
        return calculate(data,qcSamples,design,options,()->{});
    }

    /** As above, with a caller cancellation/integrity callback during bounded work. */
    public static Result calculate(PlinkBedDataset data,boolean[] qcSamples,
            double[][] design,Options options,Runnable check)throws IOException {
        Objects.requireNonNull(data);Objects.requireNonNull(qcSamples);Objects.requireNonNull(design);
        Objects.requireNonNull(options);Objects.requireNonNull(check);check.run();
        int n=data.samples().size(),q=0,k=-1;
        if(qcSamples.length!=n||design.length!=n)throw new IOException("QC mask/design must match every FAM row");
        boolean[] mask=qcSamples.clone();
        for(int i=0;i<n;i++)if(mask[i]){
            if(design[i]==null||design[i].length==0)throw new IOException("Empty QC design row");
            if(k==-1)k=design[i].length;
            if(design[i].length!=k||design[i][0]!=1)throw new IOException("Require rectangular QC design with explicit first column of ones");
            for(double v:design[i])if(!Double.isFinite(v))throw new IOException("Nonfinite QC design value");
            q++;
        }
        if(q<2||k<1||k>=q)throw new IOException("Require at least two complete QC samples and fewer design columns than QC samples");
        List<PlinkBedDataset.Variant> variants=data.selectedVariants();
        if(variants.isEmpty())throw new IOException("Require an explicit nonempty variant selection");
        long cells,bytes,work;
        try{
            cells=Math.multiplyExact((long)n,n);
            bytes=Math.multiplyExact(8L,Math.addExact(cells,Math.addExact(Math.multiplyExact((long)q,k),10L*n+4L*q)));
            work=Math.addExact(Math.multiplyExact(Math.multiplyExact(4L*q,k),k),
                Math.multiplyExact((long)variants.size(),Math.addExact(cells,Math.addExact(10L*n,Math.multiplyExact(4L*q,k)))));
        }catch(ArithmeticException e){throw new IOException("GEMMA kinship numeric/work dimensions overflow",e);}
        if(cells>Integer.MAX_VALUE-8||bytes>options.maximumNumericBytes()||work>options.maximumMultiplyAdds())
            throw new IOException("GEMMA kinship numeric/work capacity exceeded before matrix allocation");
        double[][] basis=new double[k][q];
        for(int i=0,r=0;i<n;i++)if(mask[i]){for(int c=0;c<k;c++)basis[c][r]=design[i][c];r++;}
        // Two-pass orthogonalization admits a stable, explicit full-rank design.
        for(int c=0;c<k;c++){
            double original=dot(basis[c],basis[c]);
            for(int pass=0;pass<2;pass++)for(int previous=0;previous<c;previous++){
                double factor=dot(basis[c],basis[previous]);
                for(int r=0;r<q;r++)basis[c][r]-=factor*basis[previous][r];
            }
            double norm=Math.sqrt(dot(basis[c],basis[c]));
            if(!Double.isFinite(original)||!Double.isFinite(norm)||norm<=1e-12*Math.sqrt(original))
                throw new IOException("Constant, rank-deficient or numerically unresolved QC design column "+c);
            for(int r=0;r<q;r++)basis[c][r]/=norm;
        }
        double[] matrix=new double[(int)cells];var details=new ArrayList<Predictor>();int used=0;
        for(var variant:variants){
            check.run();double[] g=data.readAllele1Dosages(variant),subset=new double[q];
            int[] counts=new int[3];int missing=0;
            for(int i=0,r=0;i<n;i++)if(mask[i]){
                subset[r++]=g[i];if(Double.isNaN(g[i]))missing++;else counts[(int)g[i]]++;
            }
            int called=q-missing;double af=called==0?Double.NaN:(counts[1]+2.0*counts[2])/(2.0*called);
            double hwe=Double.NaN,projection=Double.NaN,mean=Double.NaN,variance=Double.NaN;int allCalled=0;
            Status status=Status.USED;
            if(missing/(double)q>options.maximumMissingness())status=Status.MISSINGNESS;
            else if(options.minimumFrequency()!=-1&&(af<options.minimumFrequency()||af>1-options.minimumFrequency()))status=Status.FREQUENCY;
            else if(counts[0]+counts[1]==0||counts[1]+counts[2]==0||counts[2]+counts[0]==0)status=Status.CONSTANT;
            else if(options.minimumHwe()!=0&&options.minimumFrequency()!=-1){
                hwe=exactHwe(counts[0],counts[1],counts[2]);if(hwe<options.minimumHwe())status=Status.HWE;
            }
            if(status==Status.USED){
                for(int i=0;i<q;i++)if(Double.isNaN(subset[i]))subset[i]=2*af;
                double numerator=0,denominator=dot(subset,subset);
                for(double[] b:basis){double v=dot(b,subset);numerator+=v*v;}
                projection=numerator/denominator;
                if(!Double.isFinite(projection))throw new IOException("Nonfinite QC projection for "+variant.id());
                if(k!=1&&projection>options.maximumCovariateFraction())status=Status.COVARIATE_R2;
            }
            if(status==Status.USED){
                double sum=0,squares=0;
                for(double v:g)if(!Double.isNaN(v)){sum+=v;squares+=v*v;allCalled++;}
                mean=sum/allCalled;variance=(squares+mean*mean*(n-allCalled))/n-mean*mean;
                if(!Double.isFinite(variance)||variance<=0)throw new IOException("Nonpositive/nonfinite all-sample genotype variance for admitted "+variant.id());
                double scale=options.mode()==Mode.STANDARDIZED?1/Math.sqrt(variance):1;
                for(int i=0;i<n;i++)g[i]=Double.isNaN(g[i])?0:(g[i]-mean)*scale;
                for(int i=0;i<n;i++){
                    if((i&63)==0)check.run();
                    for(int j=0;j<=i;j++)matrix[i*n+j]+=g[i]*g[j];
                }
                used++;
            }
            details.add(new Predictor(variant.id(),variant.ordinal(),called,missing,af,hwe,projection,allCalled,mean,variance,status));
        }
        if(used==0)throw new IOException("No selected variant passes explicit GEMMA QC; no kinship matrix admitted");
        for(int i=0;i<n;i++){
            check.run();for(int j=0;j<=i;j++){
                double v=matrix[i*n+j]/used;if(!Double.isFinite(v))throw new IOException("Nonfinite kinship entry");
                matrix[i*n+j]=v;matrix[j*n+i]=v;
            }
        }
        check.run();return new Result(data.samples(),details,matrix,used,q);
    }

    private static double dot(double[] a,double[] b){double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]*b[i];return sum;}

    // Original CalcHWE probability ordering has no epsilon expansion for ties.
    private static double exactHwe(int hom1,int het,int hom2){
        int rare=Math.min(hom1,hom2),copies=2*rare+het,n=hom1+het+hom2;
        double[] probabilities=new double[copies+1];
        int mid=(int)((long)copies*(2L*n-copies)/(2L*n));if((mid&1)!=(copies&1))mid++;
        int h=mid,r=(copies-mid)/2,c=n-h-r;probabilities[mid]=1;double sum=1;
        for(;h>1;h-=2){probabilities[h-2]=probabilities[h]*h*(h-1.0)/(4.0*(r+1.0)*(c+1.0));sum+=probabilities[h-2];r++;c++;}
        h=mid;r=(copies-mid)/2;c=n-h-r;
        for(;h<=copies-2;h+=2){probabilities[h+2]=probabilities[h]*4.0*r*c/((h+2.0)*(h+1.0));sum+=probabilities[h+2];r--;c--;}
        for(int i=0;i<probabilities.length;i++)probabilities[i]/=sum;
        double p=0;for(double value:probabilities)if(value<=probabilities[het])p+=value;
        return Math.min(1,p);
    }
}
