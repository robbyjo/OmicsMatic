/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EagleReferenceJoinTest {
    private static EagleReferenceJoin.Marker m(long row,String ref,String alt){return new EagleReferenceJoin.Marker(row,"v"+row,ref,alt);}
    private static EagleReferenceJoin.Result join(List<EagleReferenceJoin.Marker> ref,List<EagleReferenceJoin.Marker> target,List<EagleReferenceJoin.Marker> excluded,boolean swap){return EagleReferenceJoin.join(ref,target,excluded,swap,EagleReferenceJoin.Limits.defaults());}
    private static EagleReferenceJoin.Decision decision(String rr,String ra,String tr,String ta,boolean swap){
        var joined=join(List.of(m(1,rr,ra)),List.of(m(1,tr,ta)),List.of(),swap);
        return joined.events().stream().filter(e->e.target()!=null).map(e->EagleReferenceJoin.decide(e,swap)).findFirst().orElseThrow();
    }
    @Test void exactAllelesPairRegardlessOfMarkerIds() {
        var target=new EagleReferenceJoin.Marker(2,"different-ID","A","C");var r=join(List.of(m(1,"A","C")),List.of(target),List.of(),false);
        assertEquals(1,r.events().size());assertEquals(target,r.events().get(0).target());assertTrue(EagleReferenceJoin.decide(r.events().get(0),false).phased());
    }
    @Test void exactModeDoesNotPairSwappedSnps() {assertEquals(EagleReferenceJoin.Reason.TARGET_ONLY,decision("A","C","C","A",false).reason());}
    @Test void explicitSwapPairsSnpsAndRecordsReferenceInversion() {
        var d=decision("A","C","C","A",true);assertTrue(d.phased());assertTrue(d.referenceAltSwap());assertEquals("C",d.expectedTarget().reference());assertEquals("A",d.expectedTarget().alternate());
    }
    @Test void indelPairingDoesNotAuthorizeAnIndelAlleleSwap() {var d=decision("A","AT","AT","A",true);assertEquals(EagleReferenceJoin.Reason.ALLELE_MISMATCH,d.reason());assertFalse(d.referenceAltSwap());}
    @Test void BothRefPairingCanPairDifferentSnpAllelesBeforeEagleRejectsThem() {assertEquals(EagleReferenceJoin.Reason.ALLELE_MISMATCH,decision("A","C","A","G",true).reason());}
    @Test void exactPairingLeavesMonomorphicTargetUnpaired() {assertEquals(EagleReferenceJoin.Reason.TARGET_ONLY,decision("A","C","A",".",false).reason());}
    @Test void originalMonomorphicExpansionCanReplaceTargetReferenceToo() {
        var d=decision("G","C","A",".",true);assertTrue(d.phased());assertTrue(d.monomorphicTargetExpanded());assertEquals("G",d.expectedTarget().reference());assertEquals("C",d.expectedTarget().alternate());
    }
    @Test void monomorphicReferenceIsRejectedBeforeTargetExpansion() {
        var d=decision("A",".","A",".",true);assertEquals(EagleReferenceJoin.Reason.MONOMORPHIC_REFERENCE,d.reason());assertFalse(d.monomorphicTargetExpanded());
    }
    @Test void targetMultiallelicFilterPrecedesReferenceMonomorphicFilter() {
        var e=new EagleReferenceJoin.Event(m(1,"A","."),m(1,"A","C,G"),null);assertEquals(EagleReferenceJoin.Reason.MULTIALLELIC_TARGET,EagleReferenceJoin.decide(e,true).reason());
    }
    @Test void referenceMultiallelicRejectionRetainsEarlierMonomorphicTargetExpansion() {
        var d=decision("A","C,G","A",".",true);assertEquals(EagleReferenceJoin.Reason.MULTIALLELIC_REFERENCE,d.reason());assertTrue(d.monomorphicTargetExpanded());assertEquals("C,G",d.expectedTarget().alternate());
    }
    @Test void exclusionRunsBeforeAnyTargetAlleleTransformation() {
        var d=EagleReferenceJoin.decide(new EagleReferenceJoin.Event(m(1,"G","C"),m(1,"A","."),m(1,"A",".")),true);
        assertEquals(EagleReferenceJoin.Reason.EXCLUDED_BY_SITE,d.reason());assertEquals("A",d.expectedTarget().reference());assertEquals(".",d.expectedTarget().alternate());
    }
    @Test void exactExclusionCanLeaveDifferentAlleleTargetEligible() {
        var r=join(List.of(m(1,"A","C")),List.of(m(1,"A","C")),List.of(m(1,"A","G")),false);
        assertEquals(2,r.events().size());assertTrue(EagleReferenceJoin.decide(r.events().get(0),false).phased());assertNull(r.events().get(1).target());
    }
    @Test void swapModeCanExcludeASiteWithDifferentAlternateAlleles() {
        var r=join(List.of(m(1,"A","C")),List.of(m(1,"A","C")),List.of(m(1,"A","G")),true);
        assertEquals(1,r.events().size());assertEquals(EagleReferenceJoin.Reason.EXCLUDED_BY_SITE,EagleReferenceJoin.decide(r.events().get(0),true).reason());
    }
    @Test void withinPositionOutputOrderFollowsSourceGroupingRatherThanTargetFileOrder() {
        var r=join(List.of(m(1,"A","C"),m(2,"A","G")),List.of(m(1,"A","G"),m(2,"A","C")),List.of(),false);
        assertEquals(List.of(2L,1L),r.events().stream().map(e->e.target().ordinal()).toList());
    }
    @Test void matchedVariantIsEmittedBeforeAnEarlierUnmatchedTargetAtTheSamePosition() {
        var r=join(List.of(m(1,"A","C")),List.of(m(1,"A","G"),m(2,"A","C")),List.of(),false);
        assertEquals(List.of(2L,1L),r.events().stream().map(e->e.target().ordinal()).toList());
    }
    @Test void unsignedSnpPriorityOutranksMonomorphicPairing() {
        var r=join(List.of(m(1,"A","C"),m(2,"A",".")),List.of(m(1,"A","G")),List.of(),true);
        assertEquals("C",r.events().get(0).reference().alternate());assertNotNull(r.events().get(0).target());assertNull(r.events().get(1).target());
    }
    @Test void noTwoVariantsFromTheSameRoleAreMerged() {
        var r=join(List.of(m(1,"A","C"),m(2,"A","G")),List.of(m(1,"A","T")),List.of(),true);
        assertEquals(2,r.events().size());assertEquals(2,r.events().stream().filter(e->e.reference()!=null).count());assertEquals(1,r.events().stream().filter(e->e.target()!=null).count());
    }
    @Test void sameAlleleSetsCanPairInDifferentAlternateOrderBeforeTheTargetFilter() {
        var d=decision("A","C,G","A","G,C",false);assertEquals(EagleReferenceJoin.Reason.MULTIALLELIC_TARGET,d.reason());
    }
    @Test void caseInsensitiveHtsPairStillFailsCaseSensitiveEagleAlleleCheck() {assertEquals(EagleReferenceJoin.Reason.ALLELE_MISMATCH,decision("A","C","a","c",false).reason());}
    @Test void mixedVariantTypesUseTheOriginalHighestPermittedTypePriority() {
        var d=decision("A","C,<DEL>","A","G",true);assertEquals(EagleReferenceJoin.Reason.MULTIALLELIC_REFERENCE,d.reason());
    }
    @Test void duplicateAlleleKeysAndOrdinalsWithinOneRoleRequireExplicitResolution() {
        assertThrows(IllegalArgumentException.class,()->join(List.of(m(1,"A","C"),m(2,"A","C")),List.of(),List.of(),false));
        assertThrows(IllegalArgumentException.class,()->join(List.of(m(1,"A","C"),m(1,"A","G")),List.of(),List.of(),false));
    }
    @Test void recordAndCharacterCapacitiesNeverAdmitAPrefix() {
        assertThrows(IllegalArgumentException.class,()->EagleReferenceJoin.join(List.of(m(1,"A","C")),List.of(m(1,"A","C")),List.of(),false,new EagleReferenceJoin.Limits(1,100,100)));
        assertThrows(IllegalArgumentException.class,()->EagleReferenceJoin.join(List.of(m(1,"A","C")),List.of(),List.of(),false,new EagleReferenceJoin.Limits(10,1,100)));
    }
    @Test void repeatedReferenceStringsInMultiallelicKeysAreBudgetedBeforeExpansion() {
        var marker=m(1,"A".repeat(10000),String.join(",",IntStream.range(0,200).mapToObj(i->"C"+i).toList()));
        assertThrows(IllegalArgumentException.class,()->EagleReferenceJoin.join(List.of(marker),List.of(),List.of(),false,new EagleReferenceJoin.Limits(10,100000,100)));
    }
    @Test void algorithmicWorkCapacityAndCancellationPreventPublishingAResult() {
        assertThrows(IllegalArgumentException.class,()->EagleReferenceJoin.join(List.of(m(1,"A","C")),List.of(m(1,"A","G")),List.of(),true,new EagleReferenceJoin.Limits(10,1000,1)));
        var n=new AtomicInteger();assertThrows(IllegalStateException.class,()->EagleReferenceJoin.join(List.of(m(1,"A","C")),List.of(m(1,"A","G")),List.of(),true,EagleReferenceJoin.Limits.defaults(),()->{if(n.incrementAndGet()>3)throw new IllegalStateException("cancel");}));
    }
    @Test void markerAndLimitValidationRetainsExplicitUnsupportedBoundaries() {
        for(String allele:List.of("","β","A C","A\tC"))assertThrows(IllegalArgumentException.class,()->m(1,"A",allele));
        for(String alt:List.of("C,C","C,.","A",",C","C,"))assertThrows(IllegalArgumentException.class,()->m(1,"A",alt));
        assertThrows(IllegalArgumentException.class,()->m(0,"A","C"));assertThrows(IllegalArgumentException.class,()->new EagleReferenceJoin.Limits(0,1,1));
    }
    @Test void emptyLocusAndImmutableResultAreExplicit() {
        assertEquals(0,join(List.of(),List.of(),List.of(),false).events().size());var r=join(List.of(m(1,"A","C")),List.of(),List.of(),false);
        assertThrows(UnsupportedOperationException.class,()->r.events().clear());assertEquals(EagleReferenceJoin.Reason.REFERENCE_ONLY,EagleReferenceJoin.decide(r.events().get(0),false).reason());
    }
    @Test void originalHtsVariantClassificationIncludesSourceSymbolicAndCaseQuirks() {
        for(Object[] c:new Object[][]{{"A","C",1},{"A","a",1},{"AA","aa",0},{"AA","CC",2},{"A","AT",4},{"AT","A",4},{"AC","GTG",8},{"A","<DEL>",8},{"A","<X>tail",0},{"A","<*>tail",0},{"A","*",0},{"A","X",0},{"A",".",0},{"A","A]1:100]",16},{"AC","ATC",4},{"AC","AGT",8},{"A","C,<DEL>",9}})
            assertEquals(c[2],EagleReferenceJoin.sourceVariantTypes(m(1,(String)c[0],(String)c[1])),c[0]+"/"+c[1]);
    }
    @Test void symbolicReferenceTypesCannotBeReclassifiedAsSnpPairs() {assertEquals(EagleReferenceJoin.Reason.TARGET_ONLY,decision("A","<DEL>","A","C",true).reason());}
}
