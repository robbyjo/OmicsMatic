package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SaigeGroupCountsTest {
    private static final int[] Y={1,1,0,0};
    private static SaigeGroupCounts.Member member(String id,String annotation,double... g){return new SaigeGroupCounts.Member(id,annotation,SaigeGenotypes.calculate(g,Y,SaigeGenotypes.Missing.MEAN));}
    private static SaigeGroupCounts.Summary summarize(List<SaigeGroupCounts.Member> members,Set<String> annotations,double maxMaf,double maxMac,double collapse){return SaigeGroupCounts.summarize(members,annotations,0,.5,.15,maxMaf,maxMac,collapse,Y);}
    @Test void overlappingUltraRareCallsUseMaximumDosagePreservingHomozygotes() {
        var result=summarize(List.of(member("a","lof",2,0,1,0),member("b","lof",1,1,0,0)),Set.of("lof"),.5,0,3);assertEquals(List.of("a","b"),result.ultraRare());assertTrue(result.rare().isEmpty());assertEquals(4,result.minorCount());assertEquals(3,result.caseMinorCount());assertEquals(1,result.controlMinorCount());assertNotEquals(3,result.minorCount());assertNotEquals(5,result.minorCount());
    }
    @Test void rareDosagesAreAddedToOneCollapsedUltraRarePseudoMarker() {
        var result=summarize(List.of(member("rare","lof",1,1,1,1),member("ultra","lof",1,0,0,0)),Set.of("lof"),.5,0,1);assertEquals(List.of("rare"),result.rare());assertEquals(List.of("ultra"),result.ultraRare());assertEquals(5,result.minorCount());assertEquals(3,result.caseMinorCount());assertEquals(2,result.controlMinorCount());
    }
    @Test void exactMafAndMacBoundsAndExplicitAnnotationMasksControlMembership() {
        var members=List.of(member("a","lof",2,0,1,0),member("b","missense",1,0,0,0));assertEquals(List.of("a"),summarize(members,Set.of("lof"),.375,0,0).rare());assertEquals("empty-mask",summarize(members,Set.of("lof"),.3749,0,0).status());assertEquals(List.of("b"),summarize(members,Set.of("lof","missense"),.5,1,0).rare());
    }
    @Test void emptyMasksRemainExplicitAndQuantitativeCountsHaveNoCaseInterpretation() {
        var member=new SaigeGroupCounts.Member("a","lof",SaigeGenotypes.calculate(new double[]{0,1,0,0},null,SaigeGenotypes.Missing.MEAN));var result=SaigeGroupCounts.summarize(List.of(member),Set.of("missense"),0,.5,.15,.5,0,10,null);assertEquals("empty-mask",result.status());assertEquals(0,result.minorCount());assertTrue(Double.isNaN(result.caseMinorCount()));assertTrue(Double.isNaN(result.controlMinorCount()));
    }
    @Test void groupQcUsesImputedCountsWhileSingleMarkerQcUsesRawCounts() {
        var calls=SaigeGenotypes.calculate(new double[]{0,1,Double.NaN,0},Y,SaigeGenotypes.Missing.MEAN);assertFalse(SaigeGenotypes.passesOriginalQc(calls.summary(),0,1.2,.5));assertTrue(SaigeGenotypes.passesGroupQc(calls.summary(),0,1.2,.5));var result=SaigeGroupCounts.summarize(List.of(new SaigeGroupCounts.Member("a","lof",calls)),Set.of("lof"),0,1.2,.5,.5,0,10,Y);assertEquals("eligible",result.status());assertEquals(4.0/3,result.minorCount(),1e-14);
    }
    @Test void malformedMasksRepeatedIdsAndMismatchedCohortsFail() {
        var a=member("a","lof",0,1,0,0);assertThrows(IllegalArgumentException.class,()->summarize(List.of(a,a),Set.of("lof"),.5,0,10));assertThrows(IllegalArgumentException.class,()->summarize(List.of(a),Set.of(),.5,0,10));assertThrows(IllegalArgumentException.class,()->summarize(List.of(a),Set.of("lof"),.6,0,10));assertThrows(IllegalArgumentException.class,()->summarize(List.of(a),Set.of("lof"),.5,-1,10));assertThrows(IllegalArgumentException.class,()->SaigeGroupCounts.summarize(List.of(a),Set.of("lof"),0,.5,.15,.5,0,10,new int[]{1,0}));assertThrows(IllegalArgumentException.class,()->SaigeGroupCounts.summarize(List.of(a),Set.of("lof"),0,.5,.15,.5,0,10,new int[]{1,1,1,1}));
    }
}
