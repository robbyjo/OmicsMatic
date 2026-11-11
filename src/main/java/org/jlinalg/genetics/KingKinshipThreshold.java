/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Exact decimal threshold comparisons for explicit KING count formulas.
 *
 * <p>This evaluates counts supplied by the caller. It does not repair an original
 * KING result, infer genotype/sample coverage, choose relatives for removal or
 * reproduce a paper's unspecified selection policy. In particular, rounded
 * printed source coefficients are not inputs to this API.</p>
 */
public final class KingKinshipThreshold {
    private KingKinshipThreshold() { }

    /** The caller explicitly chooses the statistical context for each pair. */
    public enum Formula {
        /** Minimum jointly called heterozygosity denominator, between families. */
        BETWEEN_MINIMUM,
        /** Sum of jointly called heterozygosities, within a pedigree family. */
        WITHIN_AVERAGE
    }
    /** Exact comparison, or a reason why no relationship decision can be made. */
    public enum Comparison { BELOW, EQUAL, ABOVE, NO_JOINT_CALLS, ZERO_HETEROZYGOSITY }
    /** A paper's strict or inclusive threshold must be selected explicitly. */
    public enum Rule { GREATER_THAN, GREATER_THAN_OR_EQUAL }

    /** Immutable exact ratio/comparison, without rounding or clamping. */
    public static final class Evaluation {
        private final long numerator,denominator;
        private final Comparison comparison;
        private Evaluation(long numerator,long denominator,Comparison comparison) {
            this.numerator=numerator;this.denominator=denominator;this.comparison=comparison;
        }
        /** Signed native count numerator; negative finite estimates are preserved. */
        public long numerator(){return numerator;}
        /** Exact count denominator; zero means that relationship inference is unassessable. */
        public long denominator(){return denominator;}
        /** Exact relation to the caller's decimal cutoff, or an explicit missing-information state. */
        public Comparison comparison(){return comparison;}
        /**
         * Apply an explicit strict/inclusive rule. Unassessable pairs throw;
         * they can never silently become an absent relatedness edge.
         * @param rule explicit threshold comparator
         * @return whether the finite exact ratio passes the selected cutoff rule
         */
        public boolean passes(Rule rule) {
            Objects.requireNonNull(rule,"rule");
            if(comparison==Comparison.NO_JOINT_CALLS||comparison==Comparison.ZERO_HETEROZYGOSITY)
                throw new IllegalStateException("Unassessable KING pair; skip dependent relationship selection and inspect complete counts");
            return comparison==Comparison.ABOVE||comparison==Comparison.EQUAL&&rule==Rule.GREATER_THAN_OR_EQUAL;
        }
    }

    /**
     * Compare an exact hard-call ratio with a bounded decimal cutoff in0..0.5.
     * No double conversion or division is used in the comparison.
     *
     * @param counts complete jointly called categories for the pair
     * @param formula explicit within-family or between-family formula
     * @param decimalCutoff literal decimal or scientific notation, at most4096 characters
     * @return exact ratio/comparison; no source-output correction or sample-removal decision
     */
    public static Evaluation evaluate(KingRobustKinship.Counts counts,Formula formula,String decimalCutoff) {
        Objects.requireNonNull(counts,"counts");Objects.requireNonNull(formula,"formula");Objects.requireNonNull(decimalCutoff,"decimalCutoff");
        if(decimalCutoff.isEmpty()||decimalCutoff.length()>4096||!decimalCutoff.equals(decimalCutoff.strip()))
            throw new IllegalArgumentException("Require an explicit bounded decimal KING cutoff");
        BigDecimal cutoff;
        try{cutoff=new BigDecimal(decimalCutoff);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid exact decimal KING cutoff",invalid);}
        if(cutoff.precision()>1024||Math.abs((long)cutoff.scale())>1024||cutoff.signum()<0||cutoff.compareTo(new BigDecimal("0.5"))>0)
            throw new IllegalArgumentException("Require KING cutoff0..0.5, precision/absolute scale at most1024");
        long numerator,denominator;
        if(formula==Formula.BETWEEN_MINIMUM) {
            long minimum=counts.smallerHeterozygoteCount();
            numerator=2*minimum-counts.ibs1()-4*counts.oppositeHomozygotes();denominator=4*minimum;
        } else {
            numerator=counts.bothHeterozygous()-2*counts.oppositeHomozygotes();
            denominator=counts.firstHeterozygotes()+counts.secondHeterozygotes();
        }
        if(counts.jointlyCalled()==0)return new Evaluation(numerator,denominator,Comparison.NO_JOINT_CALLS);
        if(denominator==0)return new Evaluation(numerator,denominator,Comparison.ZERO_HETEROZYGOSITY);
        int result=BigDecimal.valueOf(numerator).compareTo(cutoff.multiply(BigDecimal.valueOf(denominator)));
        return new Evaluation(numerator,denominator,result<0?Comparison.BELOW:result>0?Comparison.ABOVE:Comparison.EQUAL);
    }
}
