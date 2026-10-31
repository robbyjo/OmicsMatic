package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FumaLocusDefinitionTest {
    private static FumaLocusDefinition.Gwas g(int i,int pos,double p) {return new FumaLocusDefinition.Gwas(1,pos,"A","G","rs"+i,p);}
    private static FumaLocusDefinition.ReferenceVariant r(int i,int pos) {return new FumaLocusDefinition.ReferenceVariant(1,pos,"G","A","rs"+i,.25);}
    private static FumaLocusDefinition.LdPair ld(int a,int b,double r2) {return new FumaLocusDefinition.LdPair(1,a,b,r2);}
    private static FumaLocusDefinition.Options options(long distance,List<String> seeds,boolean add) {return new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,distance,true,null,List.of(),seeds,add,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,100000);}
    private static FumaLocusDefinition.Reference ref(List<FumaLocusDefinition.ReferenceVariant> rows,List<FumaLocusDefinition.LdPair> pairs) {return new FumaLocusDefinition.Reference("fixture-GRCh37-EUR",rows,pairs,.01);}
    @Test void strongLdSelectsCandidateButSuppressesAnotherIndependentVariant() {
        var result=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,200,2e-10)),ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.6))),options(0,List.of(),true));assertEquals(2,result.candidates().size());assertEquals(1,result.independent().size());assertEquals(g(1,100,1e-10).uid(),result.independent().get(0).variant().uid());assertEquals(1,result.loci().size());
    }
    @Test void secondLdThresholdGroupsIndependentVariants() {
        var result=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,2000,2e-10)),ref(List.of(r(1,100),r(2,2000)),List.of(ld(100,2000,.1))),options(0,List.of(),true));assertEquals(2,result.independent().size());assertEquals(1,result.leads().size());assertEquals(2,result.leads().get(0).independentUids().size());assertEquals(1,result.loci().size());
    }
    @Test void belowSecondThresholdLeavesSeparateLeadsAndLoci() {
        var result=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,2000,2e-10)),ref(List.of(r(1,100),r(2,2000)),List.of(ld(100,2000,Math.nextDown(.1)))),options(0,List.of(),true));assertEquals(2,result.leads().size());assertEquals(2,result.loci().size());
    }
    @Test void strictLeadPDoesNotAdmitEquality() {
        var reference=ref(List.of(r(1,100)),List.of());assertTrue(FumaLocusDefinition.define(List.of(g(1,100,5e-8)),reference,options(0,List.of(),true)).independent().isEmpty());assertEquals(1,FumaLocusDefinition.define(List.of(g(1,100,Math.nextDown(5e-8))),reference,options(0,List.of(),true)).independent().size());
    }
    @Test void preciseCandidateBoundaryAndOriginalDecimalMeaningArePreserved() {
        var reference=ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.8)));var low=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,200,Math.nextDown(.05))),reference,options(0,List.of(),true));var exact=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,200,.05)),reference,options(0,List.of(),true));assertEquals(2,low.candidates().size());assertEquals(1,exact.candidates().size());assertTrue(low.warnings().stream().anyMatch(w->w.contains("STRICT P BOUNDARY")));
    }
    @Test void predefinedSeedBypassesItsOwnPButNotNeighbors() {
        var seed=g(1,100,.7);var result=FumaLocusDefinition.define(List.of(seed,g(2,200,.05)),ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.9))),options(0,List.of(seed.uid()),false));assertEquals(1,result.candidates().size());assertEquals(.7,result.independent().get(0).variant().pValue());assertTrue(result.independent().get(0).predefined());
    }
    @Test void predefinedAndAutomaticModesRemainDistinct() {
        var a=g(1,100,.7);var b=g(2,2000,1e-10);var reference=ref(List.of(r(1,100),r(2,2000)),List.of());assertEquals(1,FumaLocusDefinition.define(List.of(a,b),reference,options(0,List.of(a.uid()),false)).independent().size());assertEquals(2,FumaLocusDefinition.define(List.of(a,b),reference,options(0,List.of(a.uid()),true)).independent().size());
    }
    @Test void forcedSeedsRetainTheirMutualLdExceptionWithAWarning() {
        var a=g(1,100,1e-10);var b=g(2,200,2e-10);var result=FumaLocusDefinition.define(List.of(a,b),ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.9))),options(0,List.of(a.uid(),b.uid()),false));assertEquals(2,result.independent().size());assertEquals(1,result.leads().size());assertEquals(0,result.independent().get(1).sourceNewCandidates());assertTrue(result.warnings().stream().anyMatch(w->w.contains("PREDEFINED SEED BYPASS")));
    }
    @Test void predefinedOnlyDoesNotRejectTiesInUnusedAutomaticPool() {
        var a=g(1,100,.7);var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,null,List.of(),List.of(a.uid()),false,FumaLocusDefinition.Ties.ERROR,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);var result=FumaLocusDefinition.define(List.of(a,g(2,200,1e-10),g(3,300,1e-10)),ref(List.of(r(1,100),r(2,200),r(3,300)),List.of()),o);assertEquals(1,result.independent().size());assertEquals(a.uid(),result.independent().get(0).variant().uid());
    }
    @Test void predefinedClaimedCandidatesDoNotCreateAnUnclaimedTieChoice() {
        var a=g(1,100,.7);var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,null,List.of(),List.of(a.uid()),true,FumaLocusDefinition.Ties.ERROR,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);var result=FumaLocusDefinition.define(List.of(a,g(2,200,1e-10),g(3,300,1e-10)),ref(List.of(r(1,100),r(2,200),r(3,300)),List.of(ld(100,200,.8),ld(100,300,.8),ld(200,300,.4))),o);assertEquals(1,result.independent().size());assertEquals(3,result.candidates().size());
    }
    @Test void sharedCandidatesHaveFullAndSourceNewCounts() {
        var result=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,300,2e-10)),ref(List.of(r(1,100),r(2,300),r(3,200)),List.of(ld(100,200,.8),ld(200,300,.8),ld(100,300,.4))),options(0,List.of(),true));assertEquals(2,result.independent().get(1).candidateUids().size());assertEquals(1,result.independent().get(1).sourceNewCandidates());assertEquals(3,result.loci().get(0).candidateUids().size());assertTrue(result.warnings().stream().anyMatch(w->w.contains("LEGACY INCREMENTAL COUNTS")));
    }
    @Test void overlappingLeadMembershipsAreNotMadeArtificiallyDisjoint() {
        var result=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,10000,2e-10),g(3,20000,3e-10)),ref(List.of(r(1,100),r(2,10000),r(3,20000)),List.of(ld(100,10000,.2),ld(10000,20000,.2))),options(0,List.of(),true));assertEquals(2,result.leads().size());assertTrue(result.leads().get(0).independentUids().contains(g(2,10000,2e-10).uid()));assertTrue(result.leads().get(1).independentUids().contains(g(2,10000,2e-10).uid()));assertEquals(3,result.loci().get(0).independentUids().size());
    }
    @Test void mergeDistanceIsStrictUnlessInclusiveIsExplicit() {
        var input=List.of(g(1,100,1e-10),g(2,1100,2e-10));var reference=ref(List.of(r(1,100),r(2,1100)),List.of());assertEquals(2,FumaLocusDefinition.define(input,reference,options(1000,List.of(),true)).loci().size());var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,1000,true,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.INCLUSIVE,10000);assertEquals(1,FumaLocusDefinition.define(input,reference,o).loci().size());
    }
    @Test void completeComponentsRepairMultipleBackwardBridges() {
        var gwas=List.of(g(1,100,1e-10),g(3,1000,2e-10),g(5,2000,3e-10),g(7,3000,4e-10));var refs=List.of(r(1,100),r(2,150),r(3,1000),r(4,1050),r(5,2000),r(6,2050),r(7,3000),r(8,50));var result=FumaLocusDefinition.define(gwas,ref(refs,List.of(ld(100,150,1),ld(1000,1050,1),ld(2000,2050,1),ld(3000,50,1))),options(100,List.of(),true));assertEquals(1,result.loci().size());var locus=result.loci().get(0);assertEquals(50,locus.start());assertEquals(3000,locus.end());assertEquals(4,locus.independentUids().size());assertEquals(8,locus.candidateUids().size());
    }
    @Test void laterMergeDoesNotLoseEarlierComponentMembers() {
        var gwas=List.of(g(1,100,1e-10),g(2,1000,2e-10),g(3,2000,3e-10),g(5,2500,4e-10));var result=FumaLocusDefinition.define(gwas,ref(List.of(r(1,100),r(2,1000),r(3,2000),r(4,50),r(5,2500),r(6,2400)),List.of(ld(2000,50,1),ld(2500,2400,1))),options(500,List.of(),true));assertEquals(1,result.loci().size());assertEquals(4,result.loci().get(0).independentUids().size());assertEquals(4,result.loci().get(0).leadUids().size());assertEquals(6,result.loci().get(0).candidateUids().size());assertEquals("rs1",result.loci().get(0).top().rsId());
    }
    @Test void inclusiveMhcEdgesAreExcluded() {
        var input=List.of(new FumaLocusDefinition.Gwas(6,99,"A","G","rs1",1e-10),new FumaLocusDefinition.Gwas(6,100,"A","G","rs2",2e-10),new FumaLocusDefinition.Gwas(6,200,"A","G","rs3",3e-10),new FumaLocusDefinition.Gwas(6,201,"A","G","rs4",4e-10));var refs=input.stream().map(g->new FumaLocusDefinition.ReferenceVariant(6,g.position(),"G","A",g.rsId(),.25)).toList();var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,new FumaLocusDefinition.Interval(6,100,200),List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);var result=FumaLocusDefinition.define(input,ref(refs,List.of()),o);assertEquals(List.of(99,201),result.candidates().stream().map(c->c.reference().position()).toList());
    }
    @Test void outsideRegionGwasCanReenterOnlyAsReferenceWithProminentWarning() {
        var a=g(1,100,1e-10);var b=g(2,200,.8);var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,null,List.of(new FumaLocusDefinition.Interval(1,100,100)),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);var result=FumaLocusDefinition.define(List.of(a,b),ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.8))),o);assertEquals(2,result.candidates().size());assertNull(result.candidates().get(1).gwas());assertEquals(b,result.candidates().get(1).originalInput());assertTrue(result.warnings().stream().anyMatch(w->w.contains("REGION FILTER RECLASSIFICATION")));
    }
    @Test void filteredMhcVariantsDoNotTurnBoundingIntervalsIntoClippedSegments() {
        var a=new FumaLocusDefinition.Gwas(6,99,"A","G","rs1",1e-10);var b=new FumaLocusDefinition.Gwas(6,201,"A","G","rs2",2e-10);var refs=List.of(new FumaLocusDefinition.ReferenceVariant(6,99,"G","A","rs1",.25),new FumaLocusDefinition.ReferenceVariant(6,201,"G","A","rs2",.25));var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,1000,true,new FumaLocusDefinition.Interval(6,100,200),List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);var result=FumaLocusDefinition.define(List.of(a,b),ref(refs,List.of()),o);assertEquals(1,result.loci().size());assertEquals(99,result.loci().get(0).start());assertEquals(201,result.loci().get(0).end());assertTrue(result.warnings().stream().anyMatch(w->w.contains("MHC FILTER IS NOT INTERVAL CLIPPING")));
    }
    @Test void referenceOnlyInclusionIsAnExplicitOption() {
        var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,false,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);assertEquals(1,FumaLocusDefinition.define(List.of(g(1,100,1e-10)),ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.8))),o).candidates().size());
    }
    @Test void referenceMafThresholdIsInclusiveAndZeroMafIsDisclosed() {
        var o=new FumaLocusDefinition.Options(5e-8,.05,.01,.6,.1,0,true,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);var result=FumaLocusDefinition.define(List.of(g(1,100,1e-10),g(2,200,2e-10)),ref(List.of(new FumaLocusDefinition.ReferenceVariant(1,100,"G","A","rs1",.01),new FumaLocusDefinition.ReferenceVariant(1,200,"G","A","rs2",Math.nextDown(.01))),List.of()),o);assertEquals(1,result.independent().size());assertTrue(FumaLocusDefinition.define(List.of(g(1,100,1e-10)),ref(List.of(new FumaLocusDefinition.ReferenceVariant(1,100,"G","A","rs1",0)),List.of()),options(0,List.of(),true)).warnings().stream().anyMatch(w->w.contains("ZERO-MAF")));
    }
    @Test void tiedAndZeroPRequireADeclaredDecisionAndAreWarned() {
        var input=List.of(g(1,100,0),g(2,200,-0.0));var reference=ref(List.of(r(1,100),r(2,200)),List.of());var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.ERROR,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,10000);assertThrows(IllegalArgumentException.class,()->FumaLocusDefinition.define(input,reference,o));var result=FumaLocusDefinition.define(input,reference,options(0,List.of(),true));assertTrue(result.warnings().stream().anyMatch(w->w.contains("ZERO GWAS P")));assertTrue(result.warnings().stream().anyMatch(w->w.contains("COORDINATE P-TIE POLICY")));
    }
    @Test void unknownAndAlleleMismatchedVariantsAreAudited() {
        var result=FumaLocusDefinition.define(List.of(new FumaLocusDefinition.Gwas(1,100,"A","T","rs1",1e-10)),ref(List.of(r(1,100)),List.of()),options(0,List.of(),true));assertTrue(result.candidates().isEmpty());assertEquals("absent-or-allele-mismatched-reference",result.dispositions().get(0).status());assertTrue(result.warnings().stream().anyMatch(w->w.contains("GWAS/REFERENCE MISMATCH")));
    }
    @Test void missingLdCoverageAndAmbiguousOrConflictingReferenceFail() {
        assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.Reference("x",List.of(r(1,100),r(2,200)),List.of(),0));var complete=new FumaLocusDefinition.Reference("x",List.of(r(1,100),r(2,200)),List.of(ld(100,200,0)),0);assertEquals(0,complete.storedRSquared(r(1,100).uid(),r(2,200).uid()).orElseThrow());assertThrows(IllegalArgumentException.class,()->ref(List.of(r(1,100),r(2,100)),List.of()));assertThrows(IllegalArgumentException.class,()->ref(List.of(r(1,100),r(2,200)),List.of(ld(100,200,.1),ld(200,100,.2))));var sparse=new FumaLocusDefinition.Reference("x",List.of(r(1,100)),List.of(),.2);assertThrows(IllegalArgumentException.class,()->FumaLocusDefinition.define(List.of(g(1,100,1e-10)),sparse,options(0,List.of(),true)));
    }
    @Test void badIdentitiesParametersAndWorkBudgetsFail() {
        var g=g(1,100,1e-10);assertThrows(IllegalArgumentException.class,()->FumaLocusDefinition.define(List.of(g,g),ref(List.of(r(1,100)),List.of()),options(0,List.of(),true)));assertThrows(IllegalArgumentException.class,()->FumaLocusDefinition.define(List.of(g),ref(List.of(r(1,100)),List.of()),options(0,List.of("unknown"),false)));assertThrows(IllegalArgumentException.class,()->new FumaLocusDefinition.Options(5e-8,1e-9,0,.6,.1,0,true,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,1000));var o=new FumaLocusDefinition.Options(5e-8,.05,0,.6,.1,0,true,null,List.of(),List.of(),true,FumaLocusDefinition.Ties.POSITION,FumaLocusDefinition.MergeBoundary.SOURCE_STRICT,1);assertThrows(IllegalArgumentException.class,()->FumaLocusDefinition.define(List.of(g,g(2,200,2e-10)),ref(List.of(r(1,100),r(2,200)),List.of()),o));
    }
}
