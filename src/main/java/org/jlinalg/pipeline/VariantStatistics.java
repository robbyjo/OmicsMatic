/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

/** Aligned-cohort additive-dosage quality statistics. */
public record VariantStatistics(
        int samples,
        int calledSamples,
        int missingSamples,
        double missingRate,
        double alternateAlleleCount,
        double alternateAlleleFrequency,
        double minorAlleleCount,
        double minorAlleleFrequency,
        double dosageMean,
        double dosageVariance,
        double calledAlleleNumber) {
    public VariantStatistics(int samples,int calledSamples,int missingSamples,double missingRate,double alternateAlleleCount,double alternateAlleleFrequency,double minorAlleleCount,double minorAlleleFrequency,double dosageMean,double dosageVariance) {
        this(samples,calledSamples,missingSamples,missingRate,alternateAlleleCount,alternateAlleleFrequency,minorAlleleCount,minorAlleleFrequency,dosageMean,dosageVariance,2.0*calledSamples);
    }

    /** Uses observed ploidy for allele denominators; unspecified inputs declare diploid dosages. */
    public static VariantStatistics of(VariantRecord variant) {
        double[] values = variant.dosagesView();
        double sum = 0;
        double mean = 0;
        double centeredSquares = 0;
        int called = 0;
        double alleleNumber = 0;
        for (int index=0;index<values.length;index++) {
            double dosage=values[index];
            if (Double.isNaN(dosage)) continue;
            if (!Double.isFinite(dosage))
                throw new IllegalArgumentException("infinite dosage for variant " + variant.id());
            int ploidy=variant.genotypeMetadata().declaredOrAssumedPloidy(index);
            if (dosage < -1e-10 || dosage > ploidy + 1e-10)
                throw new IllegalArgumentException(
                    "variant '" + variant.id()
                    + "' contains an additive dosage outside [0,"+ploidy+"]: " + dosage);
            double bounded = Math.max(0, Math.min(ploidy, dosage));
            sum += bounded;
            alleleNumber+=ploidy;
            called++;
            double delta = bounded - mean;
            mean += delta / called;
            centeredSquares += delta * (bounded - mean);
        }
        int missing = values.length - called;
        if (called == 0) {
            return new VariantStatistics(values.length, 0, missing, 1,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN,0);
        }
        double frequency = sum / alleleNumber;
        double maf = Math.min(frequency, 1 - frequency);
        double variance = called > 1
            ? Math.max(0, centeredSquares / (called - 1))
            : 0;
        return new VariantStatistics(values.length, called, missing,
            missing / (double) values.length, sum, frequency,
            Math.min(sum,alleleNumber-sum), maf, mean, variance,alleleNumber);
    }
}
