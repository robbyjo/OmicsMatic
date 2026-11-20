/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source-informed analytic offset-model normalization from Scanpy 1.10.4/1.13.0;
 * contiguous-axis pairwise reduction follows the NumPy grouping structure.
 * Original BSD-3-Clause source/notices and reference controls accompany the component. */
package org.jlinalg.singlecell;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;

/** Native float64 complete-matrix Pearson normalization, not a fitted SCTransform model.
 * The immutable features-by-cells CSC input is the transpose storage of source
 * cells-by-genes CSR. DENSE requires every entry, including zeros, explicitly
 * stored. Input dtype/storage/edition are declared, never inferred or repaired.
 */
public final class ScanpyPearsonResiduals {
    private ScanpyPearsonResiduals() { }
    public enum Profile { SCANPY_1_10_4, SCANPY_1_13_0 }
    public enum Storage { DENSE, CSR }
    public record Options(double theta,Double clip,boolean checkValues) {
        public Options {
            if(Double.isNaN(theta)||theta<=0)throw new IllegalArgumentException("Require positive theta or +Infinity");
            if(clip!=null&&(Double.isNaN(clip)||clip<0))throw new IllegalArgumentException("Require clip >= 0 or null for sqrt(number of cells)");
        }
        public static Options defaults(){return new Options(100,null,true);}
    }
    public record Limits(int features,int cells,int storedValues,long denseEntries,long workingBytes,long maximumWork) {
        public Limits {
            if(features<1||cells<1||storedValues<0||features>Integer.MAX_VALUE-8||cells>Integer.MAX_VALUE-8
                    ||storedValues>Integer.MAX_VALUE-8||denseEntries<0||workingBytes<0||maximumWork<0)
                throw new IllegalArgumentException("Invalid Pearson normalization capacities");
        }
        public static Limits defaults(){return new Limits(1_000_000,10_000_000,10_000_000,20_000_000,1_073_741_824L,1_000_000_000);}
    }
    public record Warning(String code,long affected,String message,String requiredAction) { }
    private static final String ACTION="Retain raw counts, declared edition/options and diagnostic values. No complete matrix is admitted for undefined/nonfinite or lost positive model terms, even if clipping leaves finite values. Correct the named data/capacity/numerical cause and retry a NEW analysis; do not replace source values, silently switch editions or add a variance floor.";

    public static final class Result {
        private final List<String> features,cells;
        private final Profile profile;
        private final Storage storage;
        private final Options options;
        private final Limits limits;
        private final double[] residuals,geneTotals,cellTotals;
        private final double total,clip;
        private final boolean usable;
        private final List<Warning> warnings;
        private Result(Matrix input,Profile profile,Storage storage,Options options,Limits limits,double[] residuals,
                       double[] geneTotals,double[] cellTotals,double total,double clip,boolean usable,List<Warning> warnings) {
            features=input.features();cells=input.cells();this.profile=profile;this.storage=storage;this.options=options;
            this.limits=limits;this.residuals=residuals;this.geneTotals=geneTotals;this.cellTotals=cellTotals;
            this.total=total;this.clip=clip;this.usable=usable;this.warnings=List.copyOf(warnings);
        }
        public Profile profile(){return profile;}
        public Storage inputStorage(){return storage;}
        public Options options(){return options;}
        public boolean usable(){return usable;}
        public double effectiveClip(){return clip;}
        public double grandTotal(){return total;}
        public double[] featureTotals(){return geneTotals.clone();}
        public double[] cellTotals(){return cellTotals.clone();}
        /** Complete cell-major dense source outputs, including failed diagnostics. */
        public double[] diagnosticValues(){return residuals.clone();}
        public List<Warning> warnings(){return warnings;}
        public double expectedCount(int feature,int cell) {
            Objects.checkIndex(feature,features.size());Objects.checkIndex(cell,cells.size());
            double denominator=profile==Profile.SCANPY_1_13_0&&total==0?1:total;
            return (cellTotals[cell]*geneTotals[feature])/denominator;
        }
        /** Returns a complete dense, fully stored matrix only when all model arithmetic is admitted. */
        public Matrix matrix() {
            if(!usable)throw new ArithmeticException("No complete Pearson matrix: inspect warnings and diagnostic values. "+ACTION);
            if(Thread.currentThread().isInterrupted())throw new CancellationException("Pearson output materialization interrupted");
            int nf=features.size(),nc=cells.size();int[] pointers=new int[nc+1],rows=new int[residuals.length];
            for(int c=0;c<nc;c++){if(Thread.currentThread().isInterrupted())throw new CancellationException("Pearson output materialization interrupted");pointers[c]=c*nf;for(int g=0;g<nf;g++)rows[c*nf+g]=g;}pointers[nc]=residuals.length;
            var shared=new org.jlinalg.sequence.SeuratPreprocessing.Limits(limits.features,limits.cells,
                Math.max(limits.storedValues,residuals.length),limits.denseEntries,limits.maximumWork);
            return new Matrix(features,cells,pointers,rows,residuals,shared,()->false);
        }
    }

    public static Result normalize(Matrix matrix,Storage storage,Profile profile,Options options,
                                   Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(matrix);Objects.requireNonNull(storage);Objects.requireNonNull(profile);
        Objects.requireNonNull(options);Objects.requireNonNull(limits);Poll poll=new Poll(limits.maximumWork,cancelled);poll.check();
        int nf=matrix.features().size(),nc=matrix.cells().size(),nnz=matrix.storedValues();long entries=(long)nf*nc;
        if(nf<1||nc<1||nf>limits.features||nc>limits.cells||nnz>limits.storedValues
                ||entries>limits.denseEntries||entries>Integer.MAX_VALUE-8)
            throw new IllegalArgumentException("Complete Pearson input/output exceeds declared dimensions/storage/denseEntries; no truncation");
        long bytes=32*entries+24L*nnz+64L*(nf+(long)nc);
        long work=12*entries+8L*nnz+16L*(nf+(long)nc);
        if(bytes>limits.workingBytes||work>limits.maximumWork)
            throw new IllegalArgumentException("Complete Pearson estimate exceeds workingBytes/maximumWork; no partial analysis");
        if(storage==Storage.DENSE&&nnz!=entries)throw new IllegalArgumentException("DENSE requires every cell/feature entry, including zeros");
        int[] pointers=matrix.columnPointers(),rows=matrix.rowIndices();double[] values=matrix.values();
        double[] geneTotals=new double[nf],cellTotals=new double[nc];long fractional=0;
        for(int c=0;c<nc;c++) {
            poll.step();double sum=0;
            if(storage==Storage.DENSE&&pointers[c]!=(long)c*nf)throw new IllegalArgumentException("Dense storage declaration differs");
            for(int k=pointers[c];k<pointers[c+1];k++) {
                poll.step();double value=values[k];if(value<0)throw new IllegalArgumentException("Require finite nonnegative count input; negative counts are not repaired");
                int g=rows[k];if(storage==Storage.DENSE&&g!=k-pointers[c])throw new IllegalArgumentException("Dense feature order differs");
                if(value!=Math.rint(value))fractional++;geneTotals[g]+=value;sum+=value;
            }
            cellTotals[c]=storage==Storage.DENSE?contiguousSum(values,pointers[c],nf,poll):sum;
        }
        // Source sums the per-gene totals, not a differently ordered sum of cell totals.
        double total=contiguousSum(geneTotals,0,nf,poll);
        double denominator=profile==Profile.SCANPY_1_13_0&&total==0?1:total;
        double clip=options.clip==null?Math.sqrt(nc):options.clip;
        double[] result=new double[(int)entries];long zero=0,badMu=0,badVariance=0,badRaw=0,badResult=0,lost=0,badTotals=0;
        for(double value:geneTotals)if(!Double.isFinite(value))badTotals++;
        for(double value:cellTotals)if(!Double.isFinite(value))badTotals++;
        if(!Double.isFinite(total))badTotals++;
        for(int c=0;c<nc;c++) {
            int index=pointers[c];
            for(int g=0;g<nf;g++) {
                poll.step();double value=0;if(index<pointers[c+1]&&rows[index]==g)value=values[index++];
                double mu=(cellTotals[c]*geneTotals[g])/denominator;
                // Preserve the source product/power/division order. In particular,
                // mu*mu/+Inf must not be silently simplified after overflow.
                double variance=mu+(mu*mu)/options.theta;
                double raw=profile==Profile.SCANPY_1_13_0&&mu==0?0:(value-mu)/Math.sqrt(variance);
                double residual=Math.max(-clip,Math.min(clip,raw));result[c*nf+g]=residual;
                if(mu==0)zero++;
                if(mu==0&&cellTotals[c]>0&&geneTotals[g]>0)lost++;
                if(!Double.isFinite(mu))badMu++;
                if(!Double.isFinite(variance))badVariance++;
                if(!Double.isFinite(raw))badRaw++;
                if(!Double.isFinite(residual))badResult++;
            }
        }
        List<Warning> warnings=new ArrayList<>();
        if(clip==0)warnings.add(new Warning("ZERO_CLIP",entries,"The declared zero clipping bound forces every finite residual to zero.","Retain the requested clipping value; downstream models need usable variation. Do not infer zero raw counts from clipped residuals."));
        if(fractional>0&&options.checkValues)warnings.add(new Warning("NONINTEGER_COUNTS",fractional,"The source count check warns on non-integer values and still computes the selected arithmetic.","Confirm raw-count provenance; no integer rounding is applied."));
        if(zero>0&&profile==Profile.SCANPY_1_13_0)warnings.add(new Warning("SOURCE_ZERO_EXPECTATION_CONVENTION",zero,"Scanpy1.13.0 assigns zero residuals where the source expected count is zero, including an all-zero input.","Retain this source convention and raw counts; a zero residual is not positive evidence of expression."));
        add(warnings,"NONFINITE_TOTALS",badTotals,"Source marginal/grand-total arithmetic is nonfinite.");
        add(warnings,"NONFINITE_EXPECTED_COUNTS",badMu,"Source expected counts are nonfinite.");
        add(warnings,"POSITIVE_EXPECTATION_UNDERFLOW",lost,"A positive cell/gene population lost its positive expected count to zero; literal source diagnostics are retained.");
        add(warnings,"NONFINITE_VARIANCE",badVariance,"Source variance is nonfinite; clipping does not make its arithmetic valid.");
        add(warnings,"NONFINITE_UNCLIPPED_RESIDUALS",badRaw,"Unclipped source residuals are nonfinite, including the historical zero-expectation division.");
        add(warnings,"NONFINITE_RESIDUALS",badResult,"Clipped source residuals remain nonfinite.");
        boolean usable=badTotals+badMu+lost+badVariance+badRaw+badResult==0;
        poll.check();return new Result(matrix,profile,storage,options,limits,result,geneTotals,cellTotals,total,clip,usable,warnings);
    }
    private static void add(List<Warning> warnings,String code,long count,String message){if(count>0)warnings.add(new Warning(code,count,message,ACTION));}

    /** Contiguous-axis pairwise reduction used by the declared float64 source path. */
    private static double contiguousSum(double[] values,int start,int size,Poll poll) {
        if(size<8){double sum=0;for(int i=0;i<size;i++){poll.step();sum+=values[start+i];}return sum;}
        if(size>128){int half=(size/2)&~7;return contiguousSum(values,start,half,poll)+contiguousSum(values,start+half,size-half,poll);}
        double[] partial=new double[8];System.arraycopy(values,start,partial,0,8);int stop=size-(size%8),i=8;
        for(;i<stop;i+=8)for(int j=0;j<8;j++){poll.step();partial[j]+=values[start+i+j];}
        double sum=((partial[0]+partial[1])+(partial[2]+partial[3]))+((partial[4]+partial[5])+(partial[6]+partial[7]));
        for(;i<size;i++){poll.step();sum+=values[start+i];}return sum;
    }
    private static final class Poll {
        private final long maximum;private final BooleanSupplier cancelled;private long work;
        Poll(long maximum,BooleanSupplier cancelled){this.maximum=maximum;this.cancelled=Objects.requireNonNull(cancelled);}
        void step(){if(++work>maximum)throw new IllegalArgumentException("Pearson maximumWork exceeded");if((work&1023)==0)check();}
        void check(){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Pearson normalization interrupted/cancelled; no complete result");}
    }
}
