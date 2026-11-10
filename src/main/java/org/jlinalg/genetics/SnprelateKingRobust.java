/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.Objects;

/**
 * Native scalar profile of SNPRelate1.46.0's KING-robust calculation.
 *
 * <p>The arithmetic/normalization follows {@code src/genKING.cpp},
 * Copyright (C) 2011-2024 Xiuwen Zheng, distributed under GPL-3.0.
 * It is distinct from the existing PLINK2 between-family profile and original
 * standalone KING. Complete caller-selected hard-call counts are required;
 * this class performs no filtering, ID mapping, sample selection or inference
 * of family membership. It does not reproduce GENESIS PC-AiR.</p>
 */
public final class SnprelateKingRobust {
    private SnprelateKingRobust() { }
    /** Explicit family context; absent/unknown family labels use the between-family formula. */
    public enum Context { WITHIN_FAMILY, BETWEEN_OR_SINGLETONS }
    /** Information state for the chosen formula, without a biological validity claim. */
    public enum Disposition { FINITE, NO_JOINT_CALLS, ZERO_HETEROZYGOSITY_DENOMINATOR }
    /** Original source diagonal conventions, even for an uninformative participant. */
    public static final double DIAGONAL_KINSHIP=0.5;
    /** Original source IBS0 diagonal convention. */
    public static final double DIAGONAL_IBS0=0.0;

    /** Immutable pair result retaining both pre-normalization arithmetic and source-profile values. */
    public static final class Estimate {
        private final KingRobustKinship.Counts counts;
        private final Context context;
        private final long sumSquaredDifference;
        private final double denominator,rawKinship,kinship,ibs0;
        private final Disposition disposition;
        private Estimate(KingRobustKinship.Counts counts,Context context,long sumSquaredDifference,
                double denominator,double rawKinship,double kinship,double ibs0,Disposition disposition) {
            this.counts=counts;this.context=context;this.sumSquaredDifference=sumSquaredDifference;
            this.denominator=denominator;this.rawKinship=rawKinship;this.kinship=kinship;this.ibs0=ibs0;this.disposition=disposition;
        }
        /** Complete jointly called disjoint categories supplied by the caller. */
        public KingRobustKinship.Counts counts(){return counts;}
        /** Explicit formula context supplied by the caller. */
        public Context context(){return context;}
        /** Exact squared hard-call difference sum, IBS1+4*IBS0. */
        public long sumSquaredDifference(){return sumSquaredDifference;}
        /** The source formula's positive or zero count denominator. */
        public double denominator(){return denominator;}
        /** IEEE arithmetic before SNPRelate's explicit nonfinite-to-NaN normalization. */
        public double rawKinship(){return rawKinship;}
        /** Source-profile coefficient; every nonfinite arithmetic result becomes NaN. */
        public double kinship(){return kinship;}
        /** Opposite-homozygote proportion; no joint calls produce NaN. */
        public double ibs0(){return ibs0;}
        /** Whether source nonfinite normalization was applied. */
        public boolean sourceNonfiniteNormalization(){return !Double.isFinite(rawKinship);}
        /** Information disposition for the explicitly selected denominator. */
        public Disposition disposition(){return disposition;}
    }

    /**
     * Evaluate a distinct-person pair in the exact source arithmetic order.
     * Count combinations that would overflow the original unsigned32-bit SumSq
     * counter are rejected; wrapped counts are never silently repaired or used.
     * @param counts complete hard-call count population
     * @param context explicit within-family or between/singleton formula
     * @return source-profile coefficient/proportion with raw arithmetic and disposition
     */
    public static Estimate estimate(KingRobustKinship.Counts counts,Context context) {
        Objects.requireNonNull(counts,"counts");Objects.requireNonNull(context,"context");
        long squared=counts.ibs1()+4*counts.oppositeHomozygotes();
        if(squared>0xffff_ffffL)
            throw new IllegalArgumentException("SNPRelate1.46 SumSq unsigned32-bit counter capacity exceeded; admit no result and do not repair wrapped source counts");
        double denominator=context==Context.WITHIN_FAMILY
                ?2.0*(counts.firstHeterozygotes()+counts.secondHeterozygotes())
                :4.0*counts.smallerHeterozygoteCount();
        double raw=0.5-squared/denominator;
        double coefficient=Double.isFinite(raw)?raw:Double.NaN;
        double ibs0=counts.jointlyCalled()>0?(double)counts.oppositeHomozygotes()/counts.jointlyCalled():Double.NaN;
        Disposition disposition=counts.jointlyCalled()==0?Disposition.NO_JOINT_CALLS
                :denominator==0?Disposition.ZERO_HETEROZYGOSITY_DENOMINATOR:Disposition.FINITE;
        return new Estimate(counts,context,squared,denominator,raw,coefficient,ibs0,disposition);
    }
}
