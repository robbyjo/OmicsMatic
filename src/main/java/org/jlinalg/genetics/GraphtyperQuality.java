/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Formulae and coefficients follow GraphTyper v2.7.1/v2.7.5 logistic_constants.hpp.
 * Original copyright (c) 2020 deCODE Genetics/AMGEN, MIT licensed.
 * See META-INF/licenses/GraphTyper-MIT.txt for the original notice.
 */
package org.jlinalg.genetics;

/**
 * Original GraphTyper allele-score and LOGF arithmetic on supplied unrounded features.
 * This component does not discover/genotype variants, derive the features, apply the
 * complete variant support gates/penalties, or implement author HQ filters.
 * Rounded VCF INFO values and missing-value sentinels cannot establish these original
 * internal features: the printed ABHom=-1 can accompany the internal default
 * 0.985. No value is repaired, and no probability is clamped after scoring.
 * Finite inputs and finite linear predictors are required; invalid values fail explicitly.
 */
public final class GraphtyperQuality {
    private GraphtyperQuality() { }
    private static final double[] AA_AB_HOM={0.0,1.304140117,1.681221065,2.214801195,3.930106559};
    private static final double[] LOGF_AB_HET={-6.03446,-6.03446,-1.35948,-0.84956,-0.28956,0.0,-1.05013,-1.35024,-1.34475,-3.74512,-3.74512};
    private static final double[] LOGF_SB_ALT={-0.32486,-0.32486,-0.25342,-0.32696,0.02442,0.0,-0.33522,-0.41332,-0.74043,-1.60844,-1.60844};

    /** The mapping-quality cap is an explicit original operation, not an input repair. */
    public record AlleleScore(double probability,double linearPredictor,int homozygousBalanceBin,
                              long suppliedMappingQuality,long originalUsedMappingQuality) {
        public boolean originalMappingQualityCapApplied(){return suppliedMappingQuality!=originalUsedMappingQuality;}
    }
    public record LogisticFilter(double probability,double linearPredictor) { }

    /** Equivalent to original get_aa_score for the declared finite prepared-feature domain. */
    public static AlleleScore alleleScore(double abHom,double strandBias,double mismatches,long scoreDifference,
                                         double qualityByDepth,double clippedBases,long mappingQuality) {
        finite(abHom,"ABHom");finite(strandBias,"strand bias");finite(mismatches,"mismatches");
        finite(qualityByDepth,"quality by depth");finite(clippedBases,"clipped bases");
        int bin=abHom<=0.85?0:abHom<=0.94?1:abHom<=0.98?2:abHom<=0.99?3:4;
        long usedMq=mappingQuality>60?60:mappingQuality;
        double power=-6.347426707+AA_AB_HOM[bin]+strandBias*-0.25233400+mismatches*-0.04129973
            +scoreDifference*0.014572295+qualityByDepth*0.065221319+clippedBases*-0.01934834+usedMq*0.055973424;
        finite(power,"original allele-score linear predictor");
        return new AlleleScore(1.0/(1.0+Math.exp(-power)),power,bin,mappingQuality,usedMq);
    }

    /**
     * Equivalent to original get_logf; bins are the original already-derived integers.
     * This is distinct from the newer allele-score classifier and its support penalties.
     */
    public static LogisticFilter logisticFilter(double abHom,double clippedReadsBySequenceDepth,double mappingQuality,
                                                double passRatio,double genotypeYield,double qualityByDepth,
                                                int heterozygousBalanceBin,int alternateStrandBiasBin) {
        finite(abHom,"ABHom");finite(clippedReadsBySequenceDepth,"clipped reads by sequence depth");
        finite(mappingQuality,"mapping quality");finite(passRatio,"PASS ratio");
        finite(genotypeYield,"genotype yield");finite(qualityByDepth,"quality by depth");
        if(heterozygousBalanceBin<0||heterozygousBalanceBin>10||alternateStrandBiasBin<0||alternateStrandBiasBin>10)
            throw new IllegalArgumentException("Original LOGF bins must be integers from 0 through 10");
        double power=-29.28908+abHom*23.12909+clippedReadsBySequenceDepth*-10.22658+mappingQuality*0.01024
            +passRatio*0.85320+genotypeYield*4.91178+qualityByDepth*0.23215
            +LOGF_AB_HET[heterozygousBalanceBin]+LOGF_SB_ALT[alternateStrandBiasBin];
        finite(power,"original LOGF linear predictor");
        return new LogisticFilter(1.0/(1.0+Math.max(0.0,Math.exp(-power))),power);
    }
    private static void finite(double value,String field) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite "+field+" required; preserve invalid data and resolve their original feature provenance");
    }
}
