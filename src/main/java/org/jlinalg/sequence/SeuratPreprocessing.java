/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source-informed Seurat 5.6.0 arithmetic. Seurat is MIT licensed; source pins
 * and upstream attribution are retained with the preprocessing reference. */
package org.jlinalg.sequence;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Native Seurat 5.6.0 matrix normalization and the unsplit, no-regression sparse
 * ScaleData fast path. This is not SCTransform, variable-feature selection,
 * assay construction, integration, PCA, clustering or statistical inference.
 * Inputs/outputs retain exact identities and explicitly stored sparse zeros.
 */
public final class SeuratPreprocessing {
    private SeuratPreprocessing() { }
    public static final String PROFILE = "Seurat-5.6.0-matrix-preprocessing";
    public static final String SOURCE_COMMIT = "75dfa8603befe31505e6287d32dc86c607732bd6";

    /** Whole-population capacities, checked before allocating copies or dense output. */
    public record Limits(int features, int cells, int storedValues, long denseEntries, long maximumWork) {
        public Limits {
            if (features < 1 || cells < 1 || storedValues < 0 || features > Integer.MAX_VALUE - 8
                    || cells > Integer.MAX_VALUE - 8 || storedValues > Integer.MAX_VALUE - 8
                    || denseEntries < 0 || maximumWork < 0) throw new IllegalArgumentException("Invalid preprocessing capacities");
        }
        public static Limits defaults() { return new Limits(5_000_000, 10_000_000, 100_000_000, 20_000_000, 1_000_000_000); }
    }

    /** Immutable finite-real CSC input: features by cells, sorted unique zero-based rows. */
    public static final class Matrix {
        private final List<String> features, cells;
        private final int[] pointers, rows;
        private final double[] values;
        public Matrix(List<String> features, List<String> cells, int[] pointers, int[] rows, double[] values) {
            this(features, cells, pointers, rows, values, Limits.defaults(), () -> false);
        }
        public Matrix(List<String> features, List<String> cells, int[] pointers, int[] rows,
                      double[] values, Limits limits, BooleanSupplier cancelled) {
            Objects.requireNonNull(features); Objects.requireNonNull(cells); Objects.requireNonNull(pointers);
            Objects.requireNonNull(rows); Objects.requireNonNull(values); Objects.requireNonNull(limits);
            Poll poll = new Poll(limits.maximumWork, cancelled); poll.check();
            capacity(features.size(), cells.size(), values.length, limits);
            workCapacity((long)features.size()+2L*cells.size()+values.length, limits);
            if (pointers.length != cells.size() + 1 || rows.length != values.length || pointers[0] != 0
                    || pointers[cells.size()] != values.length) throw new IllegalArgumentException("CSC dimensions/pointers disagree");
            this.features = identities(features, poll); this.cells = identities(cells, poll);
            this.pointers = pointers.clone(); this.rows = rows.clone(); this.values = values.clone();
            for (int c = 0; c < cells.size(); c++) {
                poll.step(); int start = this.pointers[c], end = this.pointers[c + 1], previous = -1;
                if (start < 0 || end < start || end > values.length) throw new IllegalArgumentException("Invalid CSC column pointers");
                for (int k = start; k < end; k++) {
                    poll.step(); int row = this.rows[k];
                    if (row <= previous || row >= features.size() || !Double.isFinite(this.values[k]))
                        throw new IllegalArgumentException("Require sorted unique in-range CSC rows and finite real values; no repair");
                    previous = row;
                }
            }
            poll.check();
        }
        private Matrix(Matrix input, double[] values) {
            this.features = input.features; this.cells = input.cells;
            this.pointers = input.pointers; this.rows = input.rows; this.values = values;
        }
        public List<String> features() { return features; }
        public List<String> cells() { return cells; }
        public int[] columnPointers() { return pointers.clone(); }
        public int[] rowIndices() { return rows.clone(); }
        public double[] values() { return values.clone(); }
        public int storedValues() { return values.length; }
    }

    /** A retained source convention or a failure requiring action before downstream use. */
    public record Warning(String code, long affected, String message, String requiredAction) { }
    public enum Method { LOG_NORMALIZE, RELATIVE_COUNTS, CLR_MARGIN_1, CLR_MARGIN_2 }

    public static final class NormalizationResult {
        private final Matrix input;
        private final double[] values;
        private final boolean usable;
        private final Method method;
        private final Double declaredScale, effectiveScale;
        private final List<Warning> warnings;
        private NormalizationResult(Matrix input, double[] values, boolean usable, Method method,
                                    Double declaredScale, Double effectiveScale, List<Warning> warnings) {
            this.input=input; this.values=values; this.usable=usable; this.method=method;
            this.declaredScale=declaredScale; this.effectiveScale=effectiveScale; this.warnings=List.copyOf(warnings);
        }
        /** Refuses all nonfinite/overflowed arithmetic; diagnostic values remain available separately. */
        public Matrix matrix() {
            if (!usable) throw new ArithmeticException("No complete normalized matrix: inspect preprocessing warnings and retained diagnostic values; no NaN/Inf repair");
            return new Matrix(input, values);
        }
        public double[] diagnosticValues() { return values.clone(); }
        public boolean usable() { return usable; }
        public Method method() { return method; }
        public Double declaredScale() { return declaredScale; }
        public Double effectiveScale() { return effectiveScale; }
        public List<Warning> warnings() { return warnings; }
    }

    /** Sparse source LogNorm uses an INTEGER scale and scale/sum before multiplication. */
    public static NormalizationResult logNormalize(Matrix input, double scaleFactor, Limits limits, BooleanSupplier cancelled) {
        if (!Double.isFinite(scaleFactor) || scaleFactor < 0 || scaleFactor > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Seurat 5.6.0 LogNorm requires a finite nonnegative scale within signed-int range");
        return normalize(input, Method.LOG_NORMALIZE, scaleFactor, (double)(int)scaleFactor, limits, cancelled);
    }

    /** RelativeCounts uses a real scale and (x/sum)*scale, distinct from LogNorm. */
    public static NormalizationResult relativeCounts(Matrix input, double scaleFactor, Limits limits, BooleanSupplier cancelled) {
        if (!Double.isFinite(scaleFactor) || scaleFactor < 0) throw new IllegalArgumentException("Require finite nonnegative relative-count scale");
        return normalize(input, Method.RELATIVE_COUNTS, scaleFactor, scaleFactor, limits, cancelled);
    }

    /** Margin 1 transforms each FEATURE across cells; margin 2 each CELL across features. */
    public static NormalizationResult clr(Matrix input, int margin, Limits limits, BooleanSupplier cancelled) {
        if (margin != 1 && margin != 2) throw new IllegalArgumentException("CLR margin must be 1 or 2");
        return normalize(input, margin == 1 ? Method.CLR_MARGIN_1 : Method.CLR_MARGIN_2, null, null, limits, cancelled);
    }

    private static NormalizationResult normalize(Matrix input, Method method, Double declared, Double effective,
                                                  Limits limits, BooleanSupplier cancelled) {
        Objects.requireNonNull(input); Objects.requireNonNull(limits);
        capacity(input.features.size(), input.cells.size(), input.values.length, limits);
        Poll poll = new Poll(limits.maximumWork, cancelled); poll.check();
        int nf=input.features.size(), nc=input.cells.size(); boolean clr=method==Method.CLR_MARGIN_1||method==Method.CLR_MARGIN_2;
        if ((method==Method.CLR_MARGIN_1&&nc==1&&nf>1)||(method==Method.CLR_MARGIN_2&&nf==1))
            throw new IllegalArgumentException("Seurat 5.6.0 CustomNormalize drops singleton dimensions and refuses this CLR shape; no shape repair");
        int groups=method==Method.CLR_MARGIN_1?nf:nc;
        workCapacity(2L*input.values.length+2L*nc+groups, limits);
        double[] totals=new double[groups], correction=clr?new double[groups]:null;
        for (int c=0;c<nc;c++) {
            poll.step();
            for (int k=input.pointers[c];k<input.pointers[c+1];k++) {
                poll.step(); double x=input.values[k];
                if (x<0) throw new IllegalArgumentException("Normalization requires nonnegative counts/abundances; signed prepared matrices are for ScaleData");
                int g=method==Method.CLR_MARGIN_1?input.rows[k]:c;
                if (clr) {
                    // R's sum accumulates positive log1p values with extended precision.
                    double value=Math.log1p(x), y=value-correction[g], sum=totals[g]+y;
                    correction[g]=(sum-totals[g])-y; totals[g]=sum;
                } else totals[g]+=x;
            }
        }
        long zero=0, badTotals=0; boolean usable=true;
        for (int g=0;g<groups;g++) {
            poll.step(); if (totals[g]==0) zero++;
            if (!Double.isFinite(totals[g])) {badTotals++; usable=false;}
            if (clr) totals[g]=Math.exp(totals[g]/(method==Method.CLR_MARGIN_1?nc:nf));
        }
        List<Warning> warnings=new ArrayList<>();
        if (!clr && zero>0) warnings.add(new Warning("ZERO_TOTAL_COLUMNS",zero,"Zero-total columns are retained; absent entries remain zero, but explicitly stored zero/zero arithmetic can be NaN.","Inspect the supplied count population; do not drop columns or erase stored zeros to manufacture success."));
        if (badTotals>0) warnings.add(new Warning("NONFINITE_TOTALS",badTotals,"Source sums overflowed; even finite-looking source output is unreportable.","Review input magnitudes/units and retain the failed attempt; no complete matrix is available."));
        if (method==Method.LOG_NORMALIZE && !declared.equals(effective)) warnings.add(new Warning("SOURCE_INTEGER_LOG_SCALE",1,"Seurat 5.6.0 coerces the declared LogNormalize scale to a signed integer: "+declared+" -> "+effective+".","Inspect the explicit declared and effective scales; choose an intended scale rather than assuming fractional scaling."));
        double[] output=new double[input.values.length]; long nonfinite=0;
        for (int c=0;c<nc;c++) {
            poll.step(); double multiplier=method==Method.LOG_NORMALIZE?effective/totals[c]:0;
            for (int k=input.pointers[c];k<input.pointers[c+1];k++) {
                poll.step(); double x=input.values[k];
                double value=clr?Math.log1p(x/totals[method==Method.CLR_MARGIN_1?input.rows[k]:c])
                        :method==Method.LOG_NORMALIZE?Math.log1p(x*multiplier):(x/totals[c])*effective;
                output[k]=value; if (!Double.isFinite(value)) nonfinite++;
            }
        }
        if (nonfinite>0) {usable=false; warnings.add(nonfiniteWarning(nonfinite));}
        poll.check(); return new NormalizationResult(input,output,usable,method,declared,effective,warnings);
    }

    public record ScaleOptions(boolean center, boolean scale, double maximum) {
        public ScaleOptions { if (Double.isNaN(maximum)) throw new IllegalArgumentException("ScaleData maximum cannot be NaN"); }
        public static ScaleOptions defaults() { return new ScaleOptions(true,true,10); }
    }
    public static final class DenseMatrix {
        private final List<String> features,cells;
        private final double[] values;
        private DenseMatrix(List<String> features,List<String> cells,double[] values) {this.features=features;this.cells=cells;this.values=values;}
        public List<String> features() { return features; }
        public List<String> cells() { return cells; }
        public double[] valuesColumnMajor() { return values.clone(); }
        public double value(int feature,int cell) {
            Objects.checkIndex(feature,features.size());Objects.checkIndex(cell,cells.size());return values[cell*features.size()+feature];
        }
    }
    public static final class ScaleResult {
        private final DenseMatrix matrix;
        private final double[] centers,sigmas;
        private final List<Warning> warnings;
        private final boolean usable;
        private ScaleResult(DenseMatrix matrix,double[] centers,double[] sigmas,List<Warning> warnings,boolean usable) {
            this.matrix=matrix;this.centers=centers;this.sigmas=sigmas;this.warnings=List.copyOf(warnings);this.usable=usable;
        }
        public DenseMatrix matrix() {
            if (!usable) throw new ArithmeticException("No complete scaled matrix: inspect undefined source statistics and retained diagnostic values; no NaN/Inf repair");
            return matrix;
        }
        public double[] diagnosticValuesColumnMajor() { return matrix.valuesColumnMajor(); }
        public double[] centers() { return centers.clone(); }
        public double[] sigmas() { return sigmas.clone(); }
        public List<Warning> warnings() { return warnings; }
        public boolean usable() { return usable; }
    }

    /**
     * Source sparse fast path only: no regressors/split groups are inferred.
     * null selectedFeatures means all; otherwise IDs must be exact, unique,
     * known and nonempty. Unlike the R wrapper, unknown/duplicate IDs are not
     * silently intersected away. Output retains the requested order.
     */
    public static ScaleResult scaleData(Matrix input,List<String> selectedFeatures,ScaleOptions options,
                                         Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(input);Objects.requireNonNull(options);Objects.requireNonNull(limits);
        capacity(input.features.size(),input.cells.size(),input.values.length,limits);
        Poll poll=new Poll(limits.maximumWork,cancelled);poll.check();
        List<String> features=selectedFeatures==null?input.features:identities(selectedFeatures,poll);
        long dense=Math.multiplyExact((long)features.size(),input.cells.size());
        if (dense>limits.denseEntries || dense>Integer.MAX_VALUE-8) throw new IllegalArgumentException("Complete selected-feature/cell matrix exceeds declared denseEntries or Java array capacity; no truncation");
        workCapacity(input.features.size()+2L*features.size()+2L*input.values.length+2*dense
                +(selectedFeatures==null?0:features.size()), limits);
        int n=features.size(),nc=input.cells.size();Map<String,Integer> index=new HashMap<>();
        for (int r=0;r<input.features.size();r++) {poll.step();index.put(input.features.get(r),r);}
        int[] map=new int[input.features.size()];Arrays.fill(map,-1);
        for (int r=0;r<n;r++) {poll.step();Integer original=index.get(features.get(r));if(original==null)throw new IllegalArgumentException("Unknown selected feature: "+features.get(r));map[original]=r;}
        double[] sums=new double[n],squares=new double[n];
        for (int k=0;k<input.values.length;k++) {
            poll.step();int row=map[input.rows[k]];
            if(row>=0) {double value=input.values[k];sums[row]+=value;squares[row]+=value*value;}
        }
        double[] centers=new double[n],sigmas=new double[n],inverse=new double[n],zeroValues=new double[n];boolean[] valid=new boolean[n];
        long zeroSigma=0,badStats=0;boolean usable=true;
        for (int r=0;r<n;r++) {
            poll.step();double mean=sums[r]/nc;centers[r]=options.center?mean:0;
            double numerator=options.center?squares[r]-(sums[r]*sums[r]/nc):squares[r];
            double sigma=options.scale?Math.sqrt(numerator/(nc-1)):1;sigmas[r]=sigma;
            if ((options.center&&!Double.isFinite(mean)) || (options.scale&&!Double.isFinite(sigma))) {badStats++;usable=false;}
            if (sigma==0) zeroSigma++;
            if (sigma>0) {valid[r]=true;inverse[r]=1/sigma;zeroValues[r]=clip((0-centers[r])*inverse[r],options.maximum);}
        }
        double[] output=new double[(int)dense];
        for (int c=0;c<nc;c++) {
            for (int r=0;r<n;r++) {poll.step();output[c*n+r]=zeroValues[r];}
            for (int k=input.pointers[c];k<input.pointers[c+1];k++) {
                poll.step();int r=map[input.rows[k]];
                if (r>=0&&valid[r]) output[c*n+r]=clip((input.values[k]-centers[r])*inverse[r],options.maximum);
            }
        }
        long nonfinite=0;for(double value:output) {poll.step();if(!Double.isFinite(value))nonfinite++;}
        List<Warning> warnings=new ArrayList<>();
        if(zeroSigma>0)warnings.add(new Warning("SOURCE_ZERO_SIGMA_ROWS",zeroSigma,"Original sparse ScaleData leaves zero-standard-deviation rows at zero.","Inspect constant features; this is the named source rule, not evidence of variable features or inferential significance."));
        if(badStats>0)warnings.add(new Warning("UNDEFINED_SOURCE_SCALE_STATISTICS",badStats,"Original scaling can leave undefined-sigma rows at zero or produce nonfinite values. Diagnostic source output is retained; no complete scaled matrix is available.","Inspect cell count, magnitudes and cancellation/overflow; do not reinterpret the source zero convention as a valid standard deviation."));
        if(options.maximum<=0)warnings.add(new Warning("NONPOSITIVE_UPPER_CAP",1,"The declared nonpositive upper cap is retained; clipping is upper-only, including when centering/scaling are both off.","Confirm the intended cap; negative values are not clipped symmetrically."));
        if(nonfinite>0) {usable=false;warnings.add(nonfiniteWarning(nonfinite));}
        poll.check();return new ScaleResult(new DenseMatrix(features,input.cells,output),centers,sigmas,warnings,usable);
    }

    private static Warning nonfiniteWarning(long count) {
        return new Warning("NONFINITE_SOURCE_VALUES",count,"Derived NaN/Inf source values are retained only as diagnostics; no complete matrix is admitted.","Inspect input units, zero totals and resource/numerical limits; retain the failed attempt and never impute or clip undefined values to manufacture success.");
    }
    private static double clip(double value,double maximum) {return maximum!=Double.POSITIVE_INFINITY&&value>maximum?maximum:value;}
    private static void capacity(int features,int cells,int values,Limits limits) {
        if(features<1||cells<1||features>limits.features||cells>limits.cells||values>limits.storedValues)
            throw new IllegalArgumentException("Complete sparse dimensions/entries exceed declared preprocessing capacities");
    }
    private static void workCapacity(long required,Limits limits) {
        if(required>limits.maximumWork)throw new IllegalArgumentException("Complete preprocessing requires "+required+" work units, exceeding maximumWork; no partial result");
    }
    private static List<String> identities(List<String> ids,Poll poll) {
        Objects.requireNonNull(ids);if(ids.isEmpty())throw new IllegalArgumentException("Require nonempty identities");
        List<String> copy=List.copyOf(ids);Set<String> unique=new HashSet<>();
        for(String id:copy) {poll.step();if(id.isBlank()||id.codePoints().anyMatch(Character::isISOControl)||!unique.add(id))throw new IllegalArgumentException("Require exact unique nonblank identities without controls; no name repair");}
        return copy;
    }
    private static final class Poll {
        private final long maximum;private final BooleanSupplier cancelled;private long work;
        private Poll(long maximum,BooleanSupplier cancelled) {this.maximum=maximum;this.cancelled=Objects.requireNonNull(cancelled);}
        private void step() {if(work==maximum)throw new IllegalArgumentException("Seurat preprocessing maximumWork exceeded; no partial result");if((++work&1023)==0)check();}
        private void check() {if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Seurat preprocessing cancelled; no partial result");}
    }
}
