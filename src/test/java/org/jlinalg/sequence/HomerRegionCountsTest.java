package org.jlinalg.sequence;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerRegionCountsTest {
    private static HomerCoverage.Tag tag(int p,char d,float v){return new HomerCoverage.Tag(p,d,100,v);}
    private static HomerRegionCounts.Region region(String id,int a,int b){return new HomerRegionCounts.Region(id,a,b);}
    private static HomerRegionCounts.Counts run(List<HomerCoverage.Tag> tags,List<HomerRegionCounts.Region> regions,int frag,double total,double norm)throws Exception{return HomerRegionCounts.count(tags,regions,100000,frag,total,norm,1000,1000,()->{});}
    @Test void sourceCentersShiftBothStrandsBeforeInclusiveCounting()throws Exception{var r=run(List.of(tag(100,'+',1),tag(200,'-',1),tag(1000,'+',1)),List.of(region("x",125,175)),150,3,10000000);assertEquals(2,r.regions().get(0).rawTags());assertEquals("6666666.5",r.regions().get(0).printedNormalizedTags());}
    @Test void sourceCenterClampUsesInputTagExtrema()throws Exception{var r=run(List.of(tag(100,'-',1),tag(200,'+',1)),List.of(region("min",100,100),region("max",200,200)),150,2,2);assertEquals(List.of(1.0,1.0),r.regions().stream().map(HomerRegionCounts.Count::rawTags).toList());}
    @Test void publishedInclusiveEndpointsAndZeroCountRegionsRemainDistinct()throws Exception{var r=run(List.of(tag(100,'+',0.5f),tag(101,'-',1),tag(103,'+',0.5f)),List.of(region("a",100,101),region("gap",102,102),region("b",103,103)),1,2,2);assertEquals(List.of(1.5,0.0,0.5),r.regions().stream().map(HomerRegionCounts.Count::rawTags).toList());assertEquals("0.000",r.regions().get(1).printedNormalizedTags());}
    @Test void oddFragmentCenterUsesIntegerFloorAndNotStoredTemplateLength()throws Exception{var r=run(List.of(tag(100,'+',1),tag(1000,'-',1)),List.of(region("x",174,174)),149,2,2);assertEquals(1,r.totalInRegions());}
    @Test void normalizationUsesDirectoryMassInsteadOfSelectedRegionMass()throws Exception{var r=run(List.of(tag(100,'+',1),tag(200,'+',1)),List.of(region("x",100,100)),1,100,100);assertEquals(1,r.regions().get(0).normalizedTags());assertEquals("1.00",r.regions().get(0).printedNormalizedTags());}
    @Test void peakPrintfChangesPrecisionAtSourceThresholds()throws Exception{var tags=List.of(tag(100,'+',0.5f));var regions=List.of(region("x",100,100));assertEquals("0.500",run(tags,regions,1,1,1).regions().get(0).printedNormalizedTags());assertEquals("5.00",run(tags,regions,1,1,10).regions().get(0).printedNormalizedTags());assertEquals("50.0",run(tags,regions,1,1,100).regions().get(0).printedNormalizedTags());}
    @Test void centerShiftsCanReorderTagsWithoutLosingWeight()throws Exception{var r=run(List.of(tag(100,'+',0.5f),tag(120,'-',1),tag(1000,'+',0.5f)),List.of(region("a",100,100),region("b",175,175)),150,2,2);assertEquals(List.of(1.0,0.5),r.regions().stream().map(HomerRegionCounts.Count::rawTags).toList());}
    @Test void overlapAndEqualStartQueriesAreOutsideDisjointRegionProfile(){var tags=List.of(tag(100,'+',1));assertThrows(IllegalArgumentException.class,()->run(tags,List.of(region("a",100,101),region("b",101,102)),1,1,1));assertThrows(IllegalArgumentException.class,()->run(tags,List.of(region("a",100,100),region("b",100,100)),1,1,1));}
    @Test void arbitraryFractionsUnsortedTagsAndWrongMassCannotPassHalfUnitAudit(){assertThrows(IllegalArgumentException.class,()->run(List.of(tag(100,'+',0.1f)),List.of(),1,1,1));assertThrows(IllegalArgumentException.class,()->run(List.of(tag(200,'+',1),tag(100,'+',1)),List.of(),1,2,1));assertThrows(IllegalArgumentException.class,()->run(List.of(tag(100,'+',1)),List.of(),1,0.5,1));}
    @Test void emptyDiscoveredRegionsAreAValidFinalPopulationAndCallbacksPropagate()throws Exception{var tags=List.of(tag(100,'+',1));assertEquals(0,run(tags,List.of(),1,1,1).totalInRegions());assertThrows(java.io.IOException.class,()->HomerRegionCounts.count(tags,List.of(),100000,150,1,1,10,10,()->{throw new java.io.IOException("deadline");}));assertThrows(UnsupportedOperationException.class,()->run(tags,List.of(),1,1,1).regions().clear());}
}
