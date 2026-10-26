/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HumanPloidyVariantSourceTest {
    @TempDir Path root;
    private HumanPloidyProfile profile(String region,String scale,String invalid){return new HumanPloidyProfile(region,"GRCh38",scale,invalid,"error");}
    private VariantSource source(String rows)throws Exception {
        Path path=root.resolve("data.vcf");Files.writeString(path,"##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n##FORMAT=<ID=DS,Number=1,Type=Float,Description=\"Dosage\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ta\tb\tc\td\n"+rows);return VariantSources.open(path);
    }
    @Test void allGrcParEndpointsAreClosedAndBuildSpecific() {
        for(String build:List.of("GRCh37","GRCh38")) {
            boolean old=build.equals("GRCh37");
            long[][] intervals=old?new long[][]{{60001,2699520},{154931044,155260560},{10001,2649520},{59034050,59363566}}:new long[][]{{10001,2781479},{155701383,156030895},{10001,2781479},{56887903,57217415}};
            var kinds=new HumanGenomeRegions.Region[]{HumanGenomeRegions.Region.X_PAR1,HumanGenomeRegions.Region.X_PAR2,HumanGenomeRegions.Region.Y_PAR1,HumanGenomeRegions.Region.Y_PAR2};
            for(int i=0;i<4;i++)for(long p:new long[]{intervals[i][0]-1,intervals[i][0],intervals[i][1],intervals[i][1]+1}) {
                boolean inside=p>=intervals[i][0]&&p<=intervals[i][1];String chr=i<2?"chrX":"chrY";
                assertEquals(inside?kinds[i]:i<2?HumanGenomeRegions.Region.X_NONPAR:HumanGenomeRegions.Region.Y_NONPAR,HumanGenomeRegions.classify(build,chr,p));
            }
            assertEquals(HumanGenomeRegions.Region.X_PAR1,HumanGenomeRegions.classify(build,"PAR1",intervals[0][0]));
            assertEquals(HumanGenomeRegions.Region.OTHER,HumanGenomeRegions.classify(build,"PAR2",intervals[0][0]));
            assertEquals(HumanGenomeRegions.Region.X_NONPAR,HumanGenomeRegions.classify(build,"23",3000000));
            assertEquals(HumanGenomeRegions.Region.Y_NONPAR,HumanGenomeRegions.classify(build,"24",3000000));
        }
        assertEquals(HumanGenomeRegions.Region.OTHER,HumanGenomeRegions.classify("GRCh38","X",0));
        assertThrows(IllegalArgumentException.class,()->HumanGenomeRegions.classify("hg19","X",100));
    }
    @Test void physicalHaploidCountsFollowSampleOrderAndLeaveTheSourceUnmodified()throws Exception {
        var raw=source("X\t3000000\tv\tA\tG\t.\tPASS\t.\tGT\t1\t0\t1\t.\n");var audit=new ArrayList<HumanPloidyVariantSource.Audit>();
        var wrapped=new HumanPloidyVariantSource(raw,profile("x_nonpar_xy","observed","error"),false,audit::add);
        try(var reader=wrapped.open(new int[]{2,1,3})){var v=reader.read(2).variants().get(0);assertArrayEquals(new double[]{1,0,Double.NaN},v.dosages());var s=VariantStatistics.of(v);assertEquals(2,s.calledAlleleNumber());assertEquals(1,s.minorAlleleCount());assertEquals(.5,s.alternateAlleleFrequency());assertEquals("registered_human_haploid",v.genotypeMetadata().ploidyStatus());assertEquals(2,audit.get(0).calledAlleles());
            var quality=new VariantQualityOptions(0,"all",null,null,0,100,"hard_calls",false,"haploid");
            assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().minimumMac(1).quality(quality).build()).included());
            assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.defaults()).reasons().contains(VariantFilterReason.NON_DIPLOID_GENOTYPES));
        }
        try(var reader=raw.open(null)){assertEquals("observed_non_diploid",reader.read(2).variants().get(0).genotypeMetadata().ploidyStatus());}
    }
    @Test void sourceScalingIsExplicitAndHeterozygousHaploidHardCallsCannotBecomeHalfAlleles()throws Exception {
        var raw=source("X\t3000000\tv\tA\tG\t.\tPASS\t.\tGT\t1/1\t0/0\t0/1\t.\n");var calls=new ArrayList<HumanPloidyVariantSource.InvalidCall>();
        try(var reader=new HumanPloidyVariantSource(raw,profile("x_nonpar_xy","diploid","missing"),false,ignored->{},calls::add).open(new int[]{2,0,1})){var v=reader.read(10).variants().get(0);assertArrayEquals(new double[]{Double.NaN,1,0},v.dosages());assertEquals("c",calls.get(0).sampleId());assertEquals(2,calls.get(0).observedPloidy());assertEquals(2,VariantStatistics.of(v).calledAlleleNumber());}
        try(var reader=new HumanPloidyVariantSource(raw,profile("x_nonpar_xy","observed","error"),false,ignored->{}).open(null)){assertThrows(IOException.class,()->reader.read(10));}
    }
    @Test void dosageOnlyValuesRequireAnExplicitContractAndKeepFractionalExpectedCopies()throws Exception {
        var raw=source("Y\t3000000\tv\tA\tG\t.\tPASS\t.\tDS\t0.6\t1.8\t0\t.\n");
        try(var reader=new HumanPloidyVariantSource(raw,profile("y_nonpar_xy","diploid","error"),false,ignored->{}).open(null)){var v=reader.read(10).variants().get(0);assertArrayEquals(new double[]{.3,.9,0,Double.NaN},v.dosages(),1e-12);assertEquals(3,VariantStatistics.of(v).calledAlleleNumber());assertEquals(1.2,VariantStatistics.of(v).alternateAlleleCount(),1e-12);}
        try(var reader=new HumanPloidyVariantSource(raw,profile("y_nonpar_xy","observed","error"),false,ignored->{}).open(null)){assertThrows(IOException.class,()->reader.read(10));}
    }
    @Test void parUsesOnlyXCopiesAndCannotHideHaploidOrPolyploidCalls()throws Exception {
        var raw=source("Y\t10001\tydup\tA\tG\t.\tPASS\t.\tGT\t1/1\t0/0\t0/1\t1/1\nPAR1\t10001\tx\tA\tG\t.\tPASS\t.\tGT\t1/1\t0/0\t0/1\t0/0\nX\t3000000\tnonpar\tA\tG\t.\tPASS\t.\tGT\t1\t0\t1\t0\n");var audit=new ArrayList<HumanPloidyVariantSource.Audit>();
        try(var reader=new HumanPloidyVariantSource(raw,profile("par","observed","error"),false,audit::add).open(null)){var variants=reader.read(1).variants();assertEquals(1,variants.size());var v=variants.get(0);assertEquals("x",v.id());assertEquals("X",v.chromosome());assertEquals(8,VariantStatistics.of(v).calledAlleleNumber());var q=new VariantQualityOptions(.0001,"all",null,null,0,100,"hard_calls",false,"human_par_GRCh38");assertTrue(VariantFilters.evaluate(v,VariantFilterOptions.builder().quality(q).build()).included());assertNull(reader.read(1));assertEquals(3,audit.size());}
        var bad=source("X\t10001\tbad\tA\tG\t.\tPASS\t.\tGT\t1\t0/0\t0/1\t1/1/1\n");
        try(var reader=new HumanPloidyVariantSource(bad,profile("par","observed","error"),false,ignored->{}).open(null)){assertThrows(IOException.class,()->reader.read(10));}
    }
    @Test void hweRequiresTheRegisteredDiploidRegionAndNeverRunsOnHaploidCalls() {
        var xx=new VariantRecord("x","X",3000000,"A","G",new double[]{0,1,1,2},.9,new VariantGenotypeMetadata(VariantGenotypeMetadata.Origin.HARD_CALL,false,new byte[]{2,2,2,2},true));
        var q=new VariantQualityOptions(.0001,"all",null,null,0,100,"hard_calls",false,"human_xx_GRCh38");assertTrue(VariantFilters.evaluate(xx,VariantFilterOptions.builder().quality(q).build()).included());
        var wrong=new VariantQualityOptions(.0001,"all",null,null,0,100,"hard_calls",false,"human_par_GRCh38");assertTrue(VariantFilters.evaluate(xx,VariantFilterOptions.builder().quality(wrong).build()).reasons().contains(VariantFilterReason.HWE_UNAVAILABLE));
        var haploid=new VariantQualityOptions(0,"all",null,null,0,100,"auto",false,"haploid");assertTrue(VariantFilters.evaluate(xx,VariantFilterOptions.builder().quality(haploid).build()).reasons().contains(VariantFilterReason.PLOIDY_MODEL_MISMATCH));
        assertThrows(IllegalArgumentException.class,()->new VariantQualityOptions(.001,"all",null,null,0,100,"hard_calls",false,"haploid"));
    }
}
