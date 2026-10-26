/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

import java.util.ArrayList;
import java.util.List;

/** Cohort-aligned variant filtering with auditable exclusion reasons. */
public final class VariantFilters {
    private VariantFilters() { }

    public static VariantFilterResult evaluate(
            VariantRecord variant, VariantFilterOptions options) {
        if (variant == null || options == null)
            throw new IllegalArgumentException("variant and options are required");
        VariantStatistics statistics = VariantStatistics.of(variant);
        List<VariantFilterReason> reasons = new ArrayList<>();
        var quality=options.quality();quality.requireSamples(statistics.samples());
        if(quality.ploidyModel().equals("haploid")){double[] values=variant.dosagesView();for(int i=0;i<values.length;i++)if(Double.isFinite(values[i])&&variant.genotypeMetadata().declaredOrAssumedPloidy(i)!=1){reasons.add(VariantFilterReason.PLOIDY_MODEL_MISMATCH);break;}}
        else if(variant.genotypeMetadata().nonDiploidSamples()>0)reasons.add(VariantFilterReason.NON_DIPLOID_GENOTYPES);
        if(variant.genotypeMetadata().splitMultiallelic())reasons.add(VariantFilterReason.MULTIALLELIC_SPLIT_REQUIRES_MODEL);
        if(quality.genotypeKind().equals("hard_calls"))for(double value:variant.dosagesView())if(Double.isFinite(value)&&Math.abs(value-Math.rint(value))>1e-8){reasons.add(VariantFilterReason.NON_HARD_CALL_VALUES);break;}
        if(quality.autosomesOnly()&&!VariantQualityDiagnostics.autosomal(variant.chromosome()))reasons.add(variant.chromosome().isBlank()?VariantFilterReason.CHROMOSOME_UNAVAILABLE:VariantFilterReason.OUTSIDE_AUTOSOMAL_SCOPE);
        if (statistics.calledSamples() == 0) {
            reasons.add(VariantFilterReason.NO_CALLED_SAMPLES);
            return new VariantFilterResult(variant, statistics, reasons);
        }
        if (statistics.missingRate() > options.maximumMissingRate())
            reasons.add(VariantFilterReason.TOO_MANY_MISSING);
        if (statistics.minorAlleleFrequency() < options.minimumMaf())
            reasons.add(VariantFilterReason.BELOW_MINIMUM_MAF);
        if (statistics.minorAlleleFrequency() > options.maximumMaf())
            reasons.add(VariantFilterReason.ABOVE_MAXIMUM_MAF);
        if (statistics.minorAlleleCount() < options.minimumMac())
            reasons.add(VariantFilterReason.BELOW_MINIMUM_MAC);
        if (statistics.minorAlleleCount() > options.maximumMac())
            reasons.add(VariantFilterReason.ABOVE_MAXIMUM_MAC);
        if (!Double.isNaN(options.minimumImputationQuality())
                && (Double.isNaN(variant.imputationQuality())
                    || variant.imputationQuality()
                        < options.minimumImputationQuality()))
            reasons.add(Double.isNaN(variant.imputationQuality())?VariantFilterReason.IMPUTATION_QUALITY_UNAVAILABLE:VariantFilterReason.BELOW_IMPUTATION_QUALITY);
        if(quality.minimumHweP()>0||quality.minimumMissingnessP()>0) {
            var diagnostic=VariantQualityDiagnostics.calculate(variant,statistics,quality);
            if(quality.minimumHweP()>0) {
                if(!Double.isFinite(diagnostic.filterHwe().pValue()))reasons.add(VariantFilterReason.HWE_UNAVAILABLE);
                else if(diagnostic.filterHwe().pValue()<quality.minimumHweP())reasons.add(VariantFilterReason.BELOW_MINIMUM_HWE_P);
            }
            if(quality.minimumMissingnessP()>0) {
                if(!Double.isFinite(diagnostic.missingnessP()))reasons.add(VariantFilterReason.DIFFERENTIAL_MISSINGNESS_UNAVAILABLE);
                else if(diagnostic.missingnessP()<quality.minimumMissingnessP())reasons.add(VariantFilterReason.BELOW_MINIMUM_MISSINGNESS_P);
            }
        }
        if (options.excludeMonomorphic()
                && !(statistics.dosageVariance() > 0))
            reasons.add(VariantFilterReason.MONOMORPHIC);
        return new VariantFilterResult(variant, statistics, reasons);
    }
}
