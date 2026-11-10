/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Objects;

/**
 * Native SNPRelate1.46 allele/missing-rate statistics and explicit QC selection.
 *
 * <p>Follows the source's {@code dGenGWAS.cpp} and {@code R/Internal.R}
 * (SNPRelate, Xiuwen Zheng, GPL-3.0). GDS layout affects the floating-point
 * missing-rate expression. The source bypasses QC entirely when monomorphic
 * removal is false and both numeric cutoffs are nonfinite; all-missing SNPs
 * remain selected in that bypass. Any active QC rejects nonfinite MAF.</p>
 */
public final class SnprelateVariantQc {
    private SnprelateVariantQc() { }
    /** Physical GDS orientation, selected explicitly rather than inferred. */
    public enum Layout { SNP_BY_SAMPLE, SAMPLE_BY_SNP }
    /** Native hard-call dosage summary, without imputation or population filtering. */
    public record Counts(int samples,int called,long alleleCopies) {
        /** Require a nonempty sample population and valid diploid dosage counts. */
        public Counts {
            if(samples<1||called<0||called>samples||alleleCopies<0||alleleCopies>2L*called)
                throw new IllegalArgumentException("Invalid complete diploid SNP dosage counts");
        }
    }
    /** Literal source parameters; nonfinite cutoffs disable those thresholds, including infinities. */
    public record Filters(boolean removeMonomorphic,double minorAlleleFrequency,double missingRate) {
        /** SNPRelate1.46 KING defaults; they are not inferred to be a paper's settings. */
        public static Filters defaults146(){return new Filters(true,Double.NaN,0.01);}
        /** Explicitly bypass all source QC, retaining even all-missing SNPs. */
        public static Filters bypass(){return new Filters(false,Double.NaN,Double.NaN);}
        /** Whether the source invokes its frequency/missingness selection routine. */
        public boolean active(){return removeMonomorphic||Double.isFinite(minorAlleleFrequency)||Double.isFinite(missingRate);}
    }
    /** Source-order first rejecting condition, or an explicit selected state. */
    public enum Decision { SELECTED_QC_BYPASSED, SELECTED_AFTER_QC, NONFINITE_FREQUENCY, MONOMORPHIC, BELOW_MAF, ABOVE_MISSING_RATE }
    /** Layout-bound statistics over exactly the supplied sample population. */
    public record Statistics(Counts counts,Layout layout,double alleleFrequency,double minorAlleleFrequency,double missingRate) {
        /** Reject statistics inconsistent with their complete count population or layout. */
        public Statistics {
            Objects.requireNonNull(counts,"counts");Objects.requireNonNull(layout,"layout");capacity(counts);
            double af=counts.called()>0?(double)counts.alleleCopies()/(2*counts.called()):Double.NaN;
            double mr=layout==Layout.SNP_BY_SAMPLE?(double)(counts.samples()-counts.called())/counts.samples()
                    :1-(double)counts.called()/counts.samples();
            if(Double.doubleToLongBits(af)!=Double.doubleToLongBits(alleleFrequency)
                    ||Double.doubleToLongBits(Math.min(af,1-af))!=Double.doubleToLongBits(minorAlleleFrequency)
                    ||Double.doubleToLongBits(mr)!=Double.doubleToLongBits(missingRate))
                throw new IllegalArgumentException("SNPRelate statistics differ from their complete counts/layout; do not substitute values");
        }
    }
    private static void capacity(Counts counts) {
        if(counts.called()>Integer.MAX_VALUE/2||counts.alleleCopies()>Integer.MAX_VALUE)
            throw new IllegalArgumentException("SNPRelate1.46 signed32-bit allele-count/denominator capacity exceeded; admit no statistic");
    }

    /**
     * Summarize native hard calls0/1/2 and missing-1. Invalid codes fail rather
     * than becoming missing calls. No source participant is removed.
     * @param calls caller-selected sample order, one variant
     * @return exact dosage counts
     */
    public static Counts count(byte[] calls) {
        Objects.requireNonNull(calls,"calls");int called=0;long copies=0;
        for(byte value:calls) {
            if(value==KingRobustKinship.MISSING)continue;
            if(value<0||value>2)throw new IllegalArgumentException("Require hard calls0/1/2 or explicit missing-1; no invalid-code repair");
            called++;copies+=value;
        }
        return new Counts(calls.length,called,copies);
    }
    /**
     * Calculate the original layout-specific statistics. Count combinations that
     * would overflow the source signed32-bit allele-count/denominator arithmetic
     * fail explicitly instead of silently using corrected or wrapped values.
     * @param counts exact complete sample dosage counts
     * @param layout explicit physical layout of the reference GDS
     * @return source-profile floating-point statistics, retaining NaN where appropriate
     */
    public static Statistics statistics(Counts counts,Layout layout) {
        Objects.requireNonNull(counts,"counts");Objects.requireNonNull(layout,"layout");
        capacity(counts);
        double af=counts.called()>0?(double)counts.alleleCopies()/(2*counts.called()):Double.NaN;
        double maf=Math.min(af,1-af);
        double missing=layout==Layout.SNP_BY_SAMPLE?(double)(counts.samples()-counts.called())/counts.samples()
                :1-(double)counts.called()/counts.samples();
        return new Statistics(counts,layout,af,maf,missing);
    }
    /**
     * Apply source QC exactly, including bypass and nonfinite-threshold semantics.
     * Finite out-of-physical-range thresholds retain the source's ordinary comparison
     * behavior; they are not clamped or silently replaced.
     * @param statistics source-profile statistics over the declared population/layout
     * @param filters exact original parameters
     * @return explicit selection/disposition without dropping any input records
     */
    public static Decision decide(Statistics statistics,Filters filters) {
        Objects.requireNonNull(statistics,"statistics");Objects.requireNonNull(filters,"filters");
        if(!filters.active())return Decision.SELECTED_QC_BYPASSED;
        double maf=statistics.minorAlleleFrequency();
        if(!Double.isFinite(maf))return Decision.NONFINITE_FREQUENCY;
        if(filters.removeMonomorphic()&&maf<=0)return Decision.MONOMORPHIC;
        if(Double.isFinite(filters.minorAlleleFrequency())&&maf<filters.minorAlleleFrequency())return Decision.BELOW_MAF;
        if(Double.isFinite(filters.missingRate())&&statistics.missingRate()>filters.missingRate())return Decision.ABOVE_MISSING_RATE;
        return Decision.SELECTED_AFTER_QC;
    }
    /** Whether a disposition selects the variant, including the explicit QC bypass. */
    public static boolean selected(Decision decision) {
        Objects.requireNonNull(decision,"decision");return decision==Decision.SELECTED_QC_BYPASSED||decision==Decision.SELECTED_AFTER_QC;
    }
}
