/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Signal arithmetic adapted from MIT-licensed deepTools 3.5.6.
 * See META-INF/NOTICE-deepTools.txt and LICENSE-deepTools.txt. */
package org.jlinalg.genetics;

import java.util.Objects;

/** Explicit supplied-signal arithmetic; read counting, normalization inputs and I/O are separate. */
public final class DeepToolsSignals {
    private DeepToolsSignals() { }

    /** Comparison operations supported by upstream bigwigCompare. */
    public enum Operation { RATIO, LOG2, RECIPROCAL_RATIO, SUBTRACT, ADD, FIRST, SECOND, MEAN }
    /** bamCoverage normalization choices; NONE uses only the explicit multiplier. */
    public enum Normalization { NONE, RPKM, CPM, BPM, RPGC }
    /** Missing inputs and undefined arithmetic remain distinguishable from finite measurements. */
    public enum Status { FINITE, MISSING_INPUT, NONFINITE_RESULT }

    /** A result may carry NaN/infinity; callers must retain its status before exporting a track. */
    public record Result(double value, Status status) {
        public Result {
            Objects.requireNonNull(status);
            if ((status==Status.FINITE)!=Double.isFinite(value))
                throw new IllegalArgumentException("Finite status must agree with the result value");
            if (status==Status.MISSING_INPUT&&!Double.isNaN(value))
                throw new IllegalArgumentException("Missing-input results must be NaN");
        }
    }

    /** Scale first, then add pseudocounts only for ratio operations, as upstream does. */
    public record Comparison(Operation operation,double firstScale,double secondScale,
                             double firstPseudocount,double secondPseudocount) {
        public Comparison {
            Objects.requireNonNull(operation);
            finite(firstScale,"first scale");finite(secondScale,"second scale");
            if(!Double.isFinite(firstPseudocount)||firstPseudocount<0
                    ||!Double.isFinite(secondPseudocount)||secondPseudocount<0)
                throw new IllegalArgumentException("Pseudocounts must be finite and nonnegative");
        }
        /** NaN in either input propagates even for FIRST/SECOND; infinities are not input measurements. */
        public Result apply(double first,double second) {
            if(Double.isInfinite(first)||Double.isInfinite(second))
                throw new IllegalArgumentException("Input signals must be finite or NaN for missing data");
            if(Double.isNaN(first)||Double.isNaN(second))return new Result(Double.NaN,Status.MISSING_INPUT);
            finite(first,"first signal");finite(second,"second signal");
            double a=firstScale*first,b=secondScale*second;
            double value=switch(operation){
                case SUBTRACT->a-b;
                case ADD->a+b;
                case FIRST->a;
                case SECOND->b;
                case MEAN->(a+b)/2.0;
                case RATIO,LOG2,RECIPROCAL_RATIO->{
                    double ratio=(a+firstPseudocount)/(b+secondPseudocount);
                    yield operation==Operation.LOG2?Math.log(ratio)/Math.log(2.0)
                            :operation==Operation.RECIPROCAL_RATIO?(ratio>=1?ratio:-1.0/ratio):ratio;
                }
            };
            return new Result(value,Double.isFinite(value)?Status.FINITE:Status.NONFINITE_RESULT);
        }
    }

    /**
     * Return a multiplier for supplied per-bin alignment counts. Kept alignments, nominal bin width,
     * effective genome size and represented read/fragment length must be independently established.
     * This does not estimate any of them, inspect BAM flags or implement deepTools sampling/blacklists.
     * BPM follows the original bamCoverage scaling function, rather than an inferred sum-of-bin TPM.
     * NONE follows bamCoverage's direct branch and never applies a fraction-kept correction.
     */
    public static double coverageScale(Normalization method,double keptAlignments,long binSize,
                                       long effectiveGenomeBases,double representedLength,double multiplier) {
        Objects.requireNonNull(method);finite(multiplier,"explicit multiplier");
        if(!Double.isFinite(keptAlignments)||keptAlignments<0||binSize<1)
            throw new IllegalArgumentException("Require nonnegative finite kept alignment count and positive nominal bin width");
        if(method==Normalization.NONE)return multiplier;
        if(keptAlignments==0)throw new IllegalArgumentException("Normalized coverage requires positive kept alignments; do not invent a denominator");
        double kb=binSize/1000.0;
        double scale=switch(method){
            case NONE->throw new AssertionError("Handled above");
            case CPM->1.0/(keptAlignments/1e6);
            case RPKM->1.0/((keptAlignments/1e6)*kb);
            case BPM->1.0/(((keptAlignments/kb)/1e6)*kb);
            case RPGC->{
                if(effectiveGenomeBases<1||!Double.isFinite(representedLength)||representedLength<=0)
                    throw new IllegalArgumentException("RPGC requires declared positive effective genome bases and represented read/fragment length");
                yield 1.0/((keptAlignments*representedLength)/effectiveGenomeBases);
            }
        };
        double result=multiplier*scale;
        if(!Double.isFinite(scale)||scale<=0||!Double.isFinite(result)||(result==0&&multiplier!=0))
            throw new IllegalArgumentException("Coverage multiplier exceeds finite nonzero range; check counts,units and declared normalization inputs");
        return result;
    }
    private static void finite(double n,String label) {
        if(!Double.isFinite(n))throw new IllegalArgumentException(label+" must be finite");
    }
}
