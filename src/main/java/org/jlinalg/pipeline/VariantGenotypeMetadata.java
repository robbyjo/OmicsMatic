/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

/** Observed source representation; unknown ploidy retains the caller's diploid dosage contract. */
public record VariantGenotypeMetadata(Origin origin, boolean splitMultiallelic, byte[] observedPloidies,boolean normalizedHumanPloidy) {
    public VariantGenotypeMetadata(Origin origin,boolean splitMultiallelic,byte[] observedPloidies){this(origin,splitMultiallelic,observedPloidies,false);}
    public enum Origin { UNSPECIFIED, HARD_CALL, DOSAGE, MIXED }
    public VariantGenotypeMetadata {
        if(origin==null)throw new IllegalArgumentException("genotype origin is required");
        observedPloidies=observedPloidies==null?null:observedPloidies.clone();
        if(observedPloidies!=null)for(byte value:observedPloidies)if(value<0||value>63)throw new IllegalArgumentException("Observed ploidy must be in [0,63]");
    }
    public static VariantGenotypeMetadata unspecified(){return new VariantGenotypeMetadata(Origin.UNSPECIFIED,false,null);}
    @Override public byte[] observedPloidies(){return observedPloidies==null?null:observedPloidies.clone();}
    public int declaredOrAssumedPloidy(int index){return observedPloidies==null||observedPloidies[index]==0?2:observedPloidies[index];}
    public int nonDiploidSamples(){int count=0;if(observedPloidies!=null)for(byte value:observedPloidies)if(value>0&&value!=2)count++;return count;}
    public String ploidyStatus(){if(normalizedHumanPloidy)return nonDiploidSamples()>0?"registered_human_haploid":"registered_human_diploid";return nonDiploidSamples()>0?"observed_non_diploid":observedPloidies==null?"assumed_diploid":hasUnknown()?"partly_assumed_diploid":"observed_diploid";}
    private boolean hasUnknown(){for(byte value:observedPloidies)if(value==0)return true;return false;}
    void requireSamples(int samples){if(observedPloidies!=null&&observedPloidies.length!=samples)throw new IllegalArgumentException("Ploidy metadata must align with genotype samples");}
}
