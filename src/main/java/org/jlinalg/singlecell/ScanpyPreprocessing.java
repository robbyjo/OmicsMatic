/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source-informed Scanpy 1.9.3 / 1.13.0 and fast-array-utils arithmetic.
 * BSD-3-Clause notices, exact source pins and original controls accompany this component. */
package org.jlinalg.singlecell;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;

/**
 * Explicit float64 dense/CSR Scanpy preprocessing profiles. The shared immutable
 * features-by-cells CSC container is the transpose view of cells-by-genes CSR:
 * its pointers/indices/data require no numerical conversion. DENSE declares a
 * fully stored cell-major matrix, including all zero entries. No identifier,
 * dtype, log base, normalization target or author workflow is inferred.
 */
public final class ScanpyPreprocessing {
    private ScanpyPreprocessing() { }
    public enum Profile { SCANPY_1_9_3, SCANPY_1_13_0 }
    public enum Storage { DENSE, CSR }
    public enum Axis { CELLS, GENES }
    public enum Criterion { MIN_COUNTS, MAX_COUNTS, MIN_DETECTED, MAX_DETECTED }

    public record Limits(int features, int cells, int storedValues, long denseEntries,
                         long workingBytes, long maximumWork) {
        public Limits {
            if (features<1 || cells<1 || storedValues<0 || denseEntries<0 || workingBytes<0 || maximumWork<0
                    || features>Integer.MAX_VALUE-8 || cells>Integer.MAX_VALUE-8 || storedValues>Integer.MAX_VALUE-8)
                throw new IllegalArgumentException("Invalid Scanpy capacities");
        }
        public static Limits defaults() { return new Limits(1_000_000,10_000_000,10_000_000,20_000_000,1_073_741_824L,1_000_000_000); }
    }
    public record Warning(String code, long affected, String message, String requiredAction) { }
    public record NormalizeOptions(Double targetSum, boolean excludeHighlyExpressed, double maxFraction) {
        public NormalizeOptions {
            if (targetSum!=null && (!Double.isFinite(targetSum)||targetSum<=0))
                throw new IllegalArgumentException("Declare a positive finite target_sum, or null for the source positive-count median");
            if (!Double.isFinite(maxFraction)||maxFraction<0||maxFraction>1)
                throw new IllegalArgumentException("max_fraction must be finite and in [0,1]");
        }
        public static NormalizeOptions defaults() { return new NormalizeOptions(null,false,.05); }
    }
    public record ScaleOptions(boolean zeroCenter, Double maxValue) {
        public ScaleOptions { if(maxValue!=null&&Double.isNaN(maxValue))throw new IllegalArgumentException("max_value cannot be NaN"); }
        public static ScaleOptions defaults() { return new ScaleOptions(true,null); }
    }

    /** Diagnostic source values/stats are available even when no finite result is admitted. */
    public static final class Result {
        private final Matrix input;
        private final Profile profile;
        private final boolean dense, usable;
        private final double[] values, totals, factors, means, std;
        private final boolean[] excluded;
        private final Double target;
        private final List<Warning> warnings;
        private final Limits limits;
        private Result(Matrix input,Profile profile,boolean dense,double[] values,double[] totals,double[] factors,
                       boolean[] excluded,Double target,double[] means,double[] std,List<Warning> warnings,
                       boolean usable,Limits limits) {
            this.input=input;this.profile=profile;this.dense=dense;this.values=values;this.totals=totals;
            this.factors=factors;this.excluded=excluded;this.target=target;this.means=means;this.std=std;
            this.warnings=List.copyOf(warnings);this.usable=usable;this.limits=limits;
        }
        public Profile profile() { return profile; }
        public boolean denseOutput() { return dense; }
        public boolean usable() { return usable; }
        public double[] diagnosticValues() { return values.clone(); }
        public double[] normalizationTotals() { return totals.clone(); }
        /** Exact edition-specific returned norm_factor, not an invented common convention. */
        public double[] returnedNormFactors() { return factors.clone(); }
        public boolean[] excludedFeatures() { return excluded.clone(); }
        public Double effectiveTargetSum() { return target; }
        public double[] means() { return means.clone(); }
        public double[] standardDeviations() { return std.clone(); }
        public List<Warning> warnings() { return warnings; }
        public Matrix matrix() {
            if(!usable)throw new ArithmeticException("No complete Scanpy matrix: inspect diagnostic values/statistics and warnings; no NaN/Inf repair");
            int[] pointers,rows;
            if(dense) {
                int nf=input.features().size(),nc=input.cells().size();pointers=new int[nc+1];rows=new int[values.length];
                for(int c=0;c<nc;c++){pointers[c]=c*nf;for(int g=0;g<nf;g++)rows[c*nf+g]=g;}pointers[nc]=values.length;
            } else {pointers=input.columnPointers();rows=input.rowIndices();}
            var shared=new org.jlinalg.sequence.SeuratPreprocessing.Limits(limits.features,limits.cells,
                Math.max(limits.storedValues,values.length),limits.denseEntries,limits.maximumWork);
            return new Matrix(input.features(),input.cells(),pointers,rows,values,shared,()->false);
        }
    }
    public static final class FilterResult {
        private final Axis axis;
        private final Criterion criterion;
        private final boolean[] retained;
        private final double[] metric;
        private FilterResult(Axis axis,Criterion criterion,boolean[] retained,double[] metric) {
            this.axis=axis;this.criterion=criterion;this.retained=retained;this.metric=metric;
        }
        public Axis axis() { return axis; }
        public Criterion criterion() { return criterion; }
        public boolean[] retained() { return retained.clone(); }
        public double[] metric() { return metric.clone(); }
    }

    private record Input(Matrix matrix,Storage storage,int nf,int nc,int[] pointers,int[] rows,double[] data) { }
    private static Input input(Matrix matrix,Storage storage,Limits limits,Poll poll,boolean denseOutput) {
        Objects.requireNonNull(matrix);Objects.requireNonNull(storage);Objects.requireNonNull(limits);poll.check();
        int nf=matrix.features().size(),nc=matrix.cells().size(),nnz=matrix.storedValues();
        if(nf>limits.features||nc>limits.cells||nnz>limits.storedValues)throw new IllegalArgumentException("Complete Scanpy input exceeds declared dimensions/storedValues");
        long dense=(long)nf*nc;
        if((denseOutput||storage==Storage.DENSE)&&(dense>limits.denseEntries||dense>Integer.MAX_VALUE-8))
            throw new IllegalArgumentException("Complete dense Scanpy population exceeds denseEntries/Java array capacity; no truncation");
        long memory=32L*nnz+64L*(nf+(long)nc)+(denseOutput?32*dense:0);
        if(memory>limits.workingBytes)throw new IllegalArgumentException("Estimated complete Scanpy workspace exceeds workingBytes; no partial matrix");
        long work=10L*nnz+16L*(nf+(long)nc)+(denseOutput?8*dense:0);
        if(work>limits.maximumWork)throw new IllegalArgumentException("Complete Scanpy operation exceeds maximumWork; no partial matrix");
        int[] pointers=matrix.columnPointers(),rows=matrix.rowIndices();double[] values=matrix.values();
        if(storage==Storage.DENSE) {
            if(nnz!=dense)throw new IllegalArgumentException("DENSE requires every cell/feature entry, including zeros, explicitly stored");
            for(int c=0;c<nc;c++) {
                poll.step();if(pointers[c]!=(long)c*nf)throw new IllegalArgumentException("Invalid complete dense declaration");
                for(int g=0;g<nf;g++){poll.step();if(rows[c*nf+g]!=g)throw new IllegalArgumentException("Invalid dense feature order");}
            }
        }
        return new Input(matrix,storage,nf,nc,pointers,rows,values);
    }

    public static Result normalizeTotal(Matrix matrix,Storage storage,Profile profile,NormalizeOptions options,
                                        Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(profile);Objects.requireNonNull(options);Poll poll=new Poll(limits,cancelled);
        Input x=input(matrix,storage,limits,poll,false);nonnegative(x,poll);
        double[] totals=new double[x.nc];boolean[] excluded=new boolean[x.nf];
        for(int c=0;c<x.nc;c++)for(int k=x.pointers[c];k<x.pointers[c+1];k++){poll.step();totals[c]+=x.data[k];}
        if(options.excludeHighlyExpressed) {
            for(int c=0;c<x.nc;c++)for(int k=x.pointers[c];k<x.pointers[c+1];k++) {
                poll.step();if(x.data[k]>options.maxFraction*totals[c])excluded[x.rows[k]]=true;
            }
            Arrays.fill(totals,0);
            for(int c=0;c<x.nc;c++)for(int k=x.pointers[c];k<x.pointers[c+1];k++){poll.step();if(!excluded[x.rows[k]])totals[c]+=x.data[k];}
        }
        double target;
        if(options.targetSum!=null)target=options.targetSum;
        else {
            double[] positive=new double[x.nc];int count=0;
            for(double total:totals){poll.step();if(total>0)positive[count++]=total;}
            Arrays.sort(positive,0,count);target=count==0?Double.NaN:count%2==1?positive[count/2]:(positive[count/2-1]+positive[count/2])/2;
        }
        double[] returned=new double[x.nc],divisors=new double[x.nc],values=new double[x.data.length];
        long zero=0,bad=0,badDivisor=0,lostPositiveFactor=0;
        for(int c=0;c<x.nc;c++) {
            poll.step();if(totals[c]==0)zero++;if(!Double.isFinite(totals[c]))bad++;
            if(profile==Profile.SCANPY_1_9_3) {
                returned[c]=totals[c]==0?1:totals[c];divisors[c]=returned[c]/target;
            } else {returned[c]=totals[c]/target;divisors[c]=returned[c]==0?1:returned[c];}
            if(!Double.isFinite(divisors[c]))badDivisor++;
            double rawDivisor=profile==Profile.SCANPY_1_9_3?divisors[c]:returned[c];
            if(totals[c]>0&&Double.isFinite(totals[c])&&rawDivisor==0)lostPositiveFactor++;
        }
        for(int c=0;c<x.nc;c++)for(int k=x.pointers[c];k<x.pointers[c+1];k++) {
            poll.step();values[k]=storage==Storage.CSR&&profile==Profile.SCANPY_1_9_3
                ?x.data[k]*(1/divisors[c]):x.data[k]/divisors[c];
        }
        List<Warning> warnings=new ArrayList<>();boolean usable=bad==0&&badDivisor==0&&lostPositiveFactor==0&&Double.isFinite(target)&&target>0;
        if(zero>0)warnings.add(warning("ZERO_NORMALIZATION_TOTALS",zero,"The source retains zero-total cells; exclusion can also make totals zero. Edition-specific zero-divisor handling is retained.","Inspect the input population, target and excluded-gene list; do not drop cells or change source rules silently."));
        if(bad>0||!Double.isFinite(target)||target<=0)warnings.add(warning("UNDEFINED_NORMALIZATION_STATISTICS",bad+(!Double.isFinite(target)||target<=0?1:0),"Source totals/target are nonfinite or undefined, even if some sparse output values look finite.","Retain diagnostics and correct magnitudes or the declared population. No complete matrix is admitted."));
        if(badDivisor>0)warnings.add(warning("NONFINITE_NORMALIZATION_DIVISORS",badDivisor,"Source normalization divisors are nonfinite even when source output appears finite.","Inspect the target, units and floating-point range; retain source diagnostics. No complete normalized matrix is admitted."));
        if(lostPositiveFactor>0)warnings.add(warning("POSITIVE_FACTOR_UNDERFLOW",lostPositiveFactor,"A positive source total underflowed to a zero normalization factor; the edition-specific output is retained only as diagnostics.","Review the target and input units. Do not interpret an unchanged or zero-valued matrix as successfully normalized."));
        usable=finite(values,warnings,poll)&&usable;poll.check();
        return result(x,profile,storage==Storage.DENSE,values,totals,returned,excluded,target,new double[0],new double[0],warnings,usable,limits);
    }

    public static Result log1p(Matrix matrix,Storage storage,Profile profile,Double base,Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(profile);
        if(base!=null&&(!Double.isFinite(base)||base<=0||base==1))throw new IllegalArgumentException("Declare a positive finite logarithm base other than one, or null for natural log");
        Poll poll=new Poll(limits,cancelled);Input x=input(matrix,storage,limits,poll,false);double[] values=new double[x.data.length];
        for(int k=0;k<values.length;k++){poll.step();values[k]=Math.log1p(x.data[k]);if(base!=null)values[k]/=Math.log(base);}
        List<Warning> warnings=new ArrayList<>();boolean usable=finite(values,warnings,poll);poll.check();
        return result(x,profile,storage==Storage.DENSE,values,new double[0],new double[0],new boolean[0],null,new double[0],new double[0],warnings,usable,limits);
    }

    public static Result scale(Matrix matrix,Storage storage,Profile profile,ScaleOptions options,Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(profile);Objects.requireNonNull(options);Poll poll=new Poll(limits,cancelled);Input x=input(matrix,storage,limits,poll,true);
        if(x.nc==1&&profile==Profile.SCANPY_1_9_3)throw new ArithmeticException("Scanpy 1.9.3 source raises ZeroDivisionError for one-cell variance; no scaled result");
        double[] mean=new double[x.nf],var=new double[x.nf],std=new double[x.nf];
        for(int k=0;k<x.data.length;k++){poll.step();mean[x.rows[k]]+=x.data[k];}
        for(int g=0;g<x.nf;g++){poll.step();mean[g]/=x.nc;}
        boolean legacySparse=profile==Profile.SCANPY_1_9_3&&storage==Storage.CSR&&!options.zeroCenter;
        if(legacySparse) {
            int[] stored=new int[x.nf];
            for(int k=0;k<x.data.length;k++){poll.step();int g=x.rows[k];double diff=x.data[k]-mean[g];var[g]+=diff*diff;stored[g]++;}
            for(int g=0;g<x.nf;g++){poll.step();var[g]+=(x.nc-stored[g])*(mean[g]*mean[g]);var[g]/=x.nc;}
        } else {
            for(int k=0;k<x.data.length;k++){poll.step();double v=x.data[k];var[x.rows[k]]+=v*v;}
            for(int g=0;g<x.nf;g++){poll.step();var[g]=var[g]/x.nc-mean[g]*mean[g];}
        }
        long constant=0,bad=0;
        for(int g=0;g<x.nf;g++) {
            poll.step();if(x.nc>1)var[g]*=(double)x.nc/(x.nc-1);std[g]=Math.sqrt(var[g]);
            if(std[g]==0){constant++;std[g]=1;}
            if(!Double.isFinite(mean[g])||!Double.isFinite(std[g]))bad++;
        }
        double[] values=new double[x.nf*x.nc];
        boolean sparseUncentered=storage==Storage.CSR&&!options.zeroCenter;
        // Older sparse clipping can also fill implicit zeros for a negative cap.
        for(int c=0;c<x.nc;c++)for(int g=0;g<x.nf;g++) {
            poll.step();double v=options.zeroCenter?-mean[g]/std[g]:0;
            values[c*x.nf+g]=sparseUncentered&&profile==Profile.SCANPY_1_13_0?0:clip(v,options.maxValue,options.zeroCenter,profile);
        }
        for(int c=0;c<x.nc;c++)for(int k=x.pointers[c];k<x.pointers[c+1];k++) {
            poll.step();int g=x.rows[k];double v=options.zeroCenter?(x.data[k]-mean[g])/std[g]
                :profile==Profile.SCANPY_1_9_3&&storage==Storage.CSR?x.data[k]*(1/std[g]):x.data[k]/std[g];
            if(sparseUncentered&&profile==Profile.SCANPY_1_13_0&&options.maxValue!=null)
                v=v<options.maxValue?v:options.maxValue; // Source min(cap, value): retains its NaN masking convention.
            else v=clip(v,options.maxValue,options.zeroCenter,profile);
            values[c*x.nf+g]=v;
        }
        List<Warning> warnings=new ArrayList<>();
        if(constant>0)warnings.add(warning("SOURCE_ZERO_STD_TO_ONE",constant,"The source substitutes a standard deviation of one for zero-variance features.","Inspect constant features; this source convention is not evidence of variability."));
        if(bad>0)warnings.add(warning("UNDEFINED_SCALE_STATISTICS",bad,"Source mean/standard deviation is nonfinite; a source clipping path can hide NaN behind finite output.","Retain source diagnostics and review sample count, magnitudes and cancellation. No complete scaled matrix is admitted."));
        if(options.maxValue!=null&&options.maxValue<=0)warnings.add(warning("NONPOSITIVE_SOURCE_CAP",1,"The literal nonpositive cap and edition/storage-specific clipping are retained.","Confirm the intended cap and inspect diagnostics; no cap repair or implicit-zero rewriting is inferred."));
        boolean usable=finite(values,warnings,poll)&&bad==0;poll.check();
        return result(x,profile,true,values,new double[0],new double[0],new boolean[0],null,mean,std,warnings,usable,limits);
    }

    public static FilterResult filter(Matrix matrix,Storage storage,Axis axis,Criterion criterion,double threshold,
                                      Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(axis);Objects.requireNonNull(criterion);
        if(!Double.isFinite(threshold)||threshold<0)throw new IllegalArgumentException("Require a finite nonnegative filter threshold");
        boolean detected=criterion==Criterion.MIN_DETECTED||criterion==Criterion.MAX_DETECTED;
        if(detected&&threshold!=Math.rint(threshold))throw new IllegalArgumentException("Detected-cell/gene threshold must be an integer");
        Poll poll=new Poll(limits,cancelled);Input x=input(matrix,storage,limits,poll,false);nonnegative(x,poll);double[] metric=new double[axis==Axis.CELLS?x.nc:x.nf];
        for(int c=0;c<x.nc;c++)for(int k=x.pointers[c];k<x.pointers[c+1];k++) {
            poll.step();metric[axis==Axis.CELLS?c:x.rows[k]]+=detected?(x.data[k]>0?1:0):x.data[k];
        }
        boolean[] retained=new boolean[metric.length];boolean minimum=criterion==Criterion.MIN_COUNTS||criterion==Criterion.MIN_DETECTED;
        for(int i=0;i<metric.length;i++){poll.step();if(!Double.isFinite(metric[i]))throw new ArithmeticException("Nonfinite Scanpy filter statistic; no complete mask");retained[i]=minimum?metric[i]>=threshold:metric[i]<=threshold;}
        poll.check();return new FilterResult(axis,criterion,retained,metric);
    }

    private static Result result(Input x,Profile profile,boolean dense,double[] values,double[] totals,double[] factors,
                                 boolean[] excluded,Double target,double[] means,double[] std,List<Warning> warnings,boolean usable,Limits limits) {
        return new Result(x.matrix,profile,dense,values,totals,factors,excluded,target,means,std,warnings,usable,limits);
    }
    private static double clip(double value,Double cap,boolean centered,Profile profile) {
        if(cap==null)return value;
        if(value>cap)return cap;
        if(profile==Profile.SCANPY_1_13_0&&centered&&value<-cap)return -cap;
        return value;
    }
    private static Warning warning(String code,long affected,String message,String action) {return new Warning(code,affected,message,action);}
    private static boolean finite(double[] values,List<Warning> warnings,Poll poll) {
        long bad=0;for(double value:values){poll.step();if(!Double.isFinite(value))bad++;}
        if(bad>0)warnings.add(warning("NONFINITE_SOURCE_VALUES",bad,"Source NaN/Inf values are retained only as diagnostics.","Inspect input units, log domain, population and source arithmetic; no complete matrix and no silent imputation."));
        return bad==0;
    }
    private static void nonnegative(Input x,Poll poll) {
        for(double v:x.data){poll.step();if(v<0)throw new IllegalArgumentException("This normalization/filter profile requires nonnegative counts/abundances; signed prepared values are allowed for scale/log1p only");}
    }
    private static final class Poll {
        private final long maximum;private final BooleanSupplier cancelled;private long work;
        private Poll(Limits limits,BooleanSupplier cancelled){this.maximum=Objects.requireNonNull(limits).maximumWork;this.cancelled=Objects.requireNonNull(cancelled);}
        private void step(){if(work==maximum)throw new IllegalArgumentException("Scanpy maximumWork exceeded; no partial result");if((++work&1023)==0)check();}
        private void check(){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Scanpy preprocessing cancelled; no complete result");}
    }
}
