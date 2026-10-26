/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import java.util.Set;

/** Aligned binary coding and independently declared QC filters; no diagnostic selects a phenotype model. */
public record VariantQualityOptions(double minimumHweP,String hweScope,int[] caseControlGroups,
        boolean[] hweSampleMask,double minimumMissingnessP,int maximumMissingnessStates,
        String genotypeKind,boolean autosomesOnly,String ploidyModel) {
    public VariantQualityOptions(double minimumHweP,String hweScope,int[] caseControlGroups,boolean[] hweSampleMask,double minimumMissingnessP,int maximumMissingnessStates,String genotypeKind,boolean autosomesOnly){this(minimumHweP,hweScope,caseControlGroups,hweSampleMask,minimumMissingnessP,maximumMissingnessStates,genotypeKind,autosomesOnly,"diploid");}
    public VariantQualityOptions {
        probability(minimumHweP);probability(minimumMissingnessP);
        if(!Set.of("all","cases","controls").contains(hweScope))throw new IllegalArgumentException("HWE scope must be all, cases or controls");
        if(!Set.of("auto","hard_calls","imputed","sequenced").contains(genotypeKind))throw new IllegalArgumentException("Invalid genotype kind");
        if(!Set.of("diploid","haploid","human_par_GRCh37","human_par_GRCh38","human_xx_GRCh37","human_xx_GRCh38").contains(ploidyModel))throw new IllegalArgumentException("Unknown genotype ploidy model");
        if(ploidyModel.equals("haploid")&&minimumHweP>0)throw new IllegalArgumentException("Diploid HWE is undefined for a haploid model");
        if(maximumMissingnessStates<1)throw new IllegalArgumentException("Missingness exact-state bound must be positive");
        caseControlGroups=caseControlGroups==null?null:caseControlGroups.clone();
        hweSampleMask=hweSampleMask==null?null:hweSampleMask.clone();
        if(caseControlGroups!=null)for(int group:caseControlGroups)if(group!=0&&group!=1)throw new IllegalArgumentException("Aligned QC groups must contain only 0 and 1");
        if(caseControlGroups==null&&(minimumMissingnessP>0||minimumHweP>0&&!hweScope.equals("all")))throw new IllegalArgumentException("Case/control QC needs recognized binary coding");
        if(caseControlGroups!=null&&hweSampleMask!=null&&caseControlGroups.length!=hweSampleMask.length)throw new IllegalArgumentException("HWE panel and binary groups must align");
    }
    public static VariantQualityOptions defaults(){return new VariantQualityOptions(0,"all",null,null,0,100000,"auto",false);}
    @Override public int[] caseControlGroups(){return caseControlGroups==null?null:caseControlGroups.clone();}
    @Override public boolean[] hweSampleMask(){return hweSampleMask==null?null:hweSampleMask.clone();}
    int[] groupsView(){return caseControlGroups;}
    boolean[] maskView(){return hweSampleMask;}
    void requireSamples(int n){if(caseControlGroups!=null&&caseControlGroups.length!=n||hweSampleMask!=null&&hweSampleMask.length!=n)throw new IllegalArgumentException("QC groups/panel do not match aligned genotypes");}
    boolean diploidHweRegion(VariantRecord variant){if(VariantQualityDiagnostics.autosomal(variant.chromosome()))return !ploidyModel.equals("haploid");if(ploidyModel.startsWith("human_")){var region=HumanGenomeRegions.classify(ploidyModel.substring(ploidyModel.lastIndexOf('_')+1),variant.chromosome(),variant.position());return ploidyModel.startsWith("human_par_")?(region==HumanGenomeRegions.Region.X_PAR1||region==HumanGenomeRegions.Region.X_PAR2):region==HumanGenomeRegions.Region.X_NONPAR;}return false;}
    private static void probability(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("QC probabilities must be in [0,1]");}
}
