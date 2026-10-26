/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.stats.Alternative;
import org.jlinalg.stats.StatisticalTests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VariantQualityDiagnosticsTest {
    @TempDir Path root;
    private static VariantRecord variant(double... values){return new VariantRecord("v","1",100,"A","G",values,.9);}
    private static VariantQualityOptions options(double hwe,String scope,int[] groups,boolean[] panel,double missingness,int states){return new VariantQualityOptions(hwe,scope,groups,panel,missingness,states,"auto",false);}
    @Test void subgroupAlleleCountsDoNotFlipMinorAlleleWithinStrata() {
        var v=variant(2,2,2,2,0,0);int[] groups={1,1,1,1,0,0};var d=VariantQualityDiagnostics.calculate(v,VariantStatistics.of(v),options(0,"all",groups,null,0,100));
        assertFalse(d.minorIsAlternate());assertEquals(0,d.cases().minorAlleleCount());assertEquals(4,d.controls().minorAlleleCount());assertEquals(4,d.cases().samples());assertEquals(1,d.missingnessP());
    }
    @Test void actualPloidyDeterminesAlleleNumberAndUnsupportedModelIsExcluded() {
        byte[] copies={1,2,1};var metadata=new VariantGenotypeMetadata(VariantGenotypeMetadata.Origin.HARD_CALL,false,copies);copies[0]=2;
        var v=new VariantRecord("haploid","X",100,"A","G",new double[]{1,2,0},Double.NaN,metadata);var s=VariantStatistics.of(v);
        assertEquals(4,s.calledAlleleNumber());assertEquals(.75,s.alternateAlleleFrequency());assertEquals(1,s.minorAlleleCount());assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.defaults()).reasons().contains(VariantFilterReason.NON_DIPLOID_GENOTYPES));
        byte[] returned=metadata.observedPloidies();returned[0]=2;assertEquals(1,metadata.declaredOrAssumedPloidy(0));
    }
    @Test void integerMacAtTheBoundaryDoesNotLoseCopiesThroughFrequencyRounding() {
        for(boolean alternateMinor:List.of(false,true)) {
            double[] values=new double[28];java.util.Arrays.fill(values,0,18,2);
            if(alternateMinor)for(int i=0;i<values.length;i++)values[i]=2-values[i];
            var v=variant(values);assertEquals(20,VariantStatistics.of(v).minorAlleleCount());
            assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().minimumMac(20).build()).included());
        }
    }
    @Test void hweScopeAndReferencePanelUseOnlyEligibleHardCalls() {
        var v=variant(0,0,1,1,2,2,1,1,1,1,1,1);int[] groups={0,0,0,0,0,0,1,1,1,1,1,1};
        var controls=options(.2,"controls",groups,null,0,100);var cases=options(.2,"cases",groups,null,0,100);
        var d=VariantQualityDiagnostics.calculate(v,VariantStatistics.of(v),controls);assertEquals(6,d.hweControls().samples());assertEquals(6,d.hweCases().samples());assertTrue(d.hweControls().pValue()>.2);assertTrue(d.hweCases().pValue()<.2);
        assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(controls).build()).included());assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(cases).build()).reasons().contains(VariantFilterReason.BELOW_MINIMUM_HWE_P));
        boolean[] panel={true,true,true,true,true,true,false,false,false,false,false,false};var selected=VariantQualityDiagnostics.calculate(v,VariantStatistics.of(v),options(.2,"all",groups,panel,0,100));assertEquals(6,selected.hweAll().samples());assertEquals(0,selected.hweCases().samples());assertEquals(d.hweControls().pValue(),selected.hweAll().pValue());
    }
    @Test void integerDosagesAreNotSilentlyRelabelledHardCalls() {
        var v=new VariantRecord("imputed","1",100,"A","G",new double[]{0,1,2,1},.9,new VariantGenotypeMetadata(VariantGenotypeMetadata.Origin.DOSAGE,false,new byte[]{2,2,2,2}));
        var d=VariantQualityDiagnostics.calculate(v,VariantStatistics.of(v),options(.01,"all",null,null,0,100));assertFalse(Double.isFinite(d.hweAll().pValue()));assertTrue(d.hweAll().method().contains("dosage-source"));assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(options(.01,"all",null,null,0,100)).build()).reasons().contains(VariantFilterReason.HWE_UNAVAILABLE));
    }
    @Test void aDeclaredHardCallInputCannotContainFractionalAlleleCalls() {
        var p=new VariantQualityOptions(0,"all",null,null,0,100,"hard_calls",false);
        assertTrue(VariantFilters.evaluate(variant(0,.25,1,2),VariantFilterOptions.builder().quality(p).build()).reasons().contains(VariantFilterReason.NON_HARD_CALL_VALUES));
    }
    @Test void hweUnavailableChromosomeAndSplitAllelesDoNotBecomePassingPValues() {
        for(String chromosome:List.of("X","MT","")) {
            var v=new VariantRecord("v",chromosome,100,"A","G",new double[]{0,1,2,1},.9);assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(options(.01,"all",null,null,0,100)).build()).reasons().contains(VariantFilterReason.HWE_UNAVAILABLE));
        }
        var split=new VariantRecord("v:G","1",100,"A","G",new double[]{0,1,2,1},.9,new VariantGenotypeMetadata(VariantGenotypeMetadata.Origin.HARD_CALL,true,null));
        var filtered=VariantFilters.evaluate(split,VariantFilterOptions.builder().quality(options(.01,"all",null,null,0,100)).build());assertTrue(filtered.reasons().contains(VariantFilterReason.HWE_UNAVAILABLE));assertTrue(filtered.reasons().contains(VariantFilterReason.MULTIALLELIC_SPLIT_REQUIRES_MODEL));
    }
    @Test void missingnessFisherUsesCalledCountsBeforeImputationAndHasAnExplicitBudget() {
        double[] values={Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,1,2,Double.NaN,0,1,2,0,1,2,0,1,2,0,1};
        int[] groups=new int[24];java.util.Arrays.fill(groups,0,12,1);var v=variant(values);
        var policy=options(0,"all",groups,null,.01,100);var d=VariantQualityDiagnostics.calculate(v,VariantStatistics.of(v),policy);assertEquals(9,d.cases().missing());assertEquals(1,d.controls().missing());assertTrue(d.missingnessP()<.01);assertEquals(3,d.cases().called());
        assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(policy).build()).reasons().contains(VariantFilterReason.BELOW_MINIMUM_MISSINGNESS_P));
        var budget=options(0,"all",groups,null,.01,1);var limited=VariantQualityDiagnostics.calculate(v,VariantStatistics.of(v),budget);assertFalse(Double.isFinite(limited.missingnessP()));assertEquals("exact-state-budget-exceeded",limited.missingnessMethod());assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(budget).build()).reasons().contains(VariantFilterReason.DIFFERENTIAL_MISSINGNESS_UNAVAILABLE));
    }
    @Test void centralFisherPOnlyMatchesIndependentReferenceAndKeepsAlternatives() {
        long[][] counts={{1,9},{11,3}};assertEquals(.0027594561852200836,StatisticalTests.fisherExactPValue(counts,Alternative.TWO_SIDED),1e-14);
        for(var alternative:Alternative.values())assertEquals(StatisticalTests.fisherExact(counts,1,alternative,.95).pValue(),StatisticalTests.fisherExactPValue(counts,alternative),1e-14);
        assertThrows(IllegalArgumentException.class,()->StatisticalTests.fisherExactPValue(new long[][]{{0,0},{0,0}},Alternative.TWO_SIDED));
    }
    @Test void subgroupAndPanelContractsAreDefensiveAndAligned() {
        int[] groups={0,1};boolean[] panel={true,false};var p=options(0,"all",groups,panel,0,100);groups[0]=1;panel[0]=false;assertArrayEquals(new int[]{0,1},p.caseControlGroups());assertArrayEquals(new boolean[]{true,false},p.hweSampleMask());
        assertThrows(IllegalArgumentException.class,()->VariantQualityDiagnostics.calculate(variant(0,1,2),VariantStatistics.of(variant(0,1,2)),p));
        assertThrows(IllegalArgumentException.class,()->options(.01,"controls",null,null,0,100));
    }
    @Test void vcfQualityIsAlleleSpecificAndPloidyMetadataFollowsSampleAlignment()throws Exception {
        Path file=root.resolve("variants.vcf");Files.writeString(file,"##fileformat=VCFv4.2\n##INFO=<ID=R2,Number=A,Type=Float,Description=\"Allele quality\">\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ta\tb\n1\t100\tmulti\tA\tG,T\t.\tPASS\tR2=0.7,0.95\tGT\t1\t1/2\n");
        var source=VariantSources.open(file);try(var reader=source.open(new int[]{0,1})){var variants=reader.read(10).variants();assertEquals(.7,variants.get(0).imputationQuality());assertEquals(.95,variants.get(1).imputationQuality());assertEquals(1,variants.get(0).genotypeMetadata().nonDiploidSamples());assertTrue(variants.get(0).genotypeMetadata().splitMultiallelic());assertEquals(3,VariantStatistics.of(variants.get(0)).calledAlleleNumber());}
        try(var reader=source.open(new int[]{1})){assertEquals(0,reader.read(10).variants().get(0).genotypeMetadata().nonDiploidSamples());}
    }
    @Test void aLoneMissingGtDoesNotInventObservedHaploidy()throws Exception {
        Path file=root.resolve("missing.vcf");Files.writeString(file,"##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ta\tb\n1\t100\tv\tA\tG\t.\tPASS\t.\tGT\t.\t0/1\n");
        try(var reader=VariantSources.open(file).open(new int[]{0,1})){var variant=reader.read(10).variants().get(0);assertEquals(0,variant.genotypeMetadata().nonDiploidSamples());assertEquals(1,VariantStatistics.of(variant).missingSamples());assertFalse(VariantFilters.evaluate(variant,VariantFilterOptions.defaults()).reasons().contains(VariantFilterReason.NON_DIPLOID_GENOTYPES));}
    }
}
