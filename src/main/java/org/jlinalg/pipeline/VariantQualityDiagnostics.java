/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import org.jlinalg.genetics.HardyWeinberg;
import org.jlinalg.stats.Alternative;
import org.jlinalg.stats.StatisticalTests;

/** QC statistics before genotype imputation; subgroup counts use one cohort-minor allele orientation. */
public record VariantQualityDiagnostics(Group cases,Group controls,boolean minorIsAlternate,
        HardyWeinberg.Result hweAll,HardyWeinberg.Result hweCases,HardyWeinberg.Result hweControls,
        HardyWeinberg.Result filterHwe,double missingnessP,String missingnessMethod) {
    public record Group(int samples,int called,int missing,double minorAlleleCount) { }
    public static VariantQualityDiagnostics calculate(VariantRecord variant,VariantStatistics statistics,VariantQualityOptions options) {
        double[] dosage=variant.dosagesView();options.requireSamples(dosage.length);
        boolean minorAlternate=statistics.alternateAlleleFrequency()<=.5;
        int[] groups=options.groupsView();
        Group cases=group(variant,groups,1,minorAlternate),controls=group(variant,groups,0,minorAlternate);
        var all=hwe(variant,options,-1);var caseHwe=groups==null?unavailable("binary-groups-unavailable"):hwe(variant,options,1);
        var controlHwe=groups==null?unavailable("binary-groups-unavailable"):hwe(variant,options,0);
        var selected=switch(options.hweScope()){case "cases"->caseHwe;case "controls"->controlHwe;default->all;};
        double missingness=Double.NaN;String method="binary-groups-unavailable";
        if(cases.samples()>0&&controls.samples()>0) {
            int totalMissing=cases.missing()+controls.missing();
            long states=(long)Math.min(cases.samples(),totalMissing)-Math.max(0,totalMissing-controls.samples())+1;
            if(states>options.maximumMissingnessStates())method="exact-state-budget-exceeded";
            else {
                missingness=StatisticalTests.fisherExactPValue(new long[][]{{cases.missing(),cases.called()},{controls.missing(),controls.called()}},Alternative.TWO_SIDED);
                method="fisher-exact-two-sided-probability-ordering";
            }
        }else if(groups!=null)method="one-outcome-group-empty";
        return new VariantQualityDiagnostics(cases,controls,minorAlternate,all,caseHwe,controlHwe,selected,missingness,method);
    }
    public static boolean autosomal(String chromosome) {
        String value=chromosome.replaceFirst("(?i)^chr","");
        try{int n=Integer.parseInt(value);return n>=1&&n<=22;}catch(NumberFormatException invalid){return false;}
    }
    private static Group group(VariantRecord variant,int[] groups,int selected,boolean minorAlternate) {
        if(groups==null)return new Group(0,0,0,Double.NaN);
        int n=0,called=0;double minor=0;double[] values=variant.dosagesView();
        for(int i=0;i<values.length;i++)if(groups[i]==selected){n++;if(Double.isFinite(values[i])){called++;int ploidy=variant.genotypeMetadata().declaredOrAssumedPloidy(i);double value=Math.max(0,Math.min(ploidy,values[i]));minor+=minorAlternate?value:ploidy-value;}}
        return new Group(n,called,n-called,minor);
    }
    private static HardyWeinberg.Result hwe(VariantRecord variant,VariantQualityOptions options,int selected) {
        if(options.ploidyModel().equals("haploid"))return unavailable("diploid-HWE-undefined-for-haploid-model");
        if(!options.diploidHweRegion(variant))return unavailable(variant.chromosome().isBlank()?"chromosome-unavailable":"non-autosomal-model-required");
        var metadata=variant.genotypeMetadata();
        if(metadata.nonDiploidSamples()>0)return unavailable("non-diploid-model-required");
        if(metadata.splitMultiallelic())return unavailable("split-multiallelic-source");
        if(metadata.origin()==VariantGenotypeMetadata.Origin.DOSAGE||metadata.origin()==VariantGenotypeMetadata.Origin.MIXED||options.genotypeKind().equals("imputed"))return unavailable("dosage-source-without-hard-call-test");
        double[] values=variant.dosages();boolean[] mask=options.maskView();
        if(mask!=null)for(int i=0;i<values.length;i++)if(!mask[i])values[i]=Double.NaN;
        return HardyWeinberg.calculate(values,options.groupsView(),selected);
    }
    private static HardyWeinberg.Result unavailable(String method){return new HardyWeinberg.Result(Double.NaN,0,method);}
}
