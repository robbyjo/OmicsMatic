/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FumaDegEnrichmentTest {
    private static final List<String> BG=List.of("A","B","C","D");
    private static final FumaDegEnrichment.Limits LIMITS=new FumaDegEnrichment.Limits(20000,100,100000);
    private static FumaDegEnrichment.DegSet set(String name,String...members){return new FumaDegEnrichment.DegSet(name,members.length,List.of(members));}
    private static FumaDegEnrichment.Result test(List<String> query,List<FumaDegEnrichment.DegSet> sets){return FumaDegEnrichment.test(query,BG,sets,FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.ERROR,LIMITS,()->{});}
    @Test void bonferroniFamiliesAreSeparateAndEmptySetsStillCount() {
        var result=test(List.of("A","B"),List.of(set("all","A","B","C","D"),set("Liver.up","A","B"),set("Empty.up"),set("Liver.down","A","B"),set("Liver.twoside","A","B")));
        assertEquals(4,result.rows().size());var up=result.rows().get(0);assertEquals(1.0/6,up.pValue(),2e-15);assertEquals(1.0/3,up.adjustedPValue(),3e-15);assertEquals(2,up.familyTests());assertEquals("Liver",up.label());assertEquals(1,result.rows().get(1).pValue());assertEquals(1.0/6,result.rows().get(2).adjustedPValue(),2e-15);assertEquals(1.0/6,result.rows().get(3).adjustedPValue(),2e-15);assertTrue(up.finitePopulationModel());
    }
    @Test void expressedBackgroundUsesAllRowsUnionWithoutChangingQuerySilently() {
        var sets=List.of(set("all","A","B"),set("all","C"),set("T.up","A","B"));
        var result=test(List.of("A","B"),sets);assertEquals(List.of("A","B","C"),result.expressedBackground());assertEquals(2,result.allRows());assertEquals(1.0/3,result.rows().get(0).pValue(),2e-15);assertEquals(3,result.rows().get(0).backgroundSize());
    }
    @Test void sourceOutsideQueryChangesDrawSizeAndHasExplicitInvalidModelDiagnostic() {
        var sets=List.of(set("all","A","B","C","D"),set("T.up","A","B"));assertThrows(IllegalArgumentException.class,()->test(List.of("A","B","OUTSIDE"),sets));
        var result=FumaDegEnrichment.test(List.of("A","B","OUTSIDE"),BG,sets,FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.RETAIN_SOURCE_SIZE,LIMITS,()->{});assertEquals(List.of("OUTSIDE"),result.queryOutsideExpressedBackground());var row=result.rows().get(0);assertEquals(3,row.querySize());assertEquals(.5,row.pValue(),2e-15);assertFalse(row.finitePopulationModel());assertEquals(List.of("A","B"),row.overlapGenes());
    }
    @Test void queryMultiplicityIsNotDeduplicatedOrMistakenForValidSampling() {
        var sets=List.of(set("all","A","B","C","D"),set("T.up","A"));assertThrows(IllegalArgumentException.class,()->test(List.of("A","A"),sets));
        var r=FumaDegEnrichment.test(List.of("A","A"),BG,sets,FumaDegEnrichment.Multiplicity.RETAIN_SOURCE,FumaDegEnrichment.OutsideQuery.ERROR,LIMITS,()->{});assertEquals(1,r.duplicateQueryEntries());var row=r.rows().get(0);assertEquals(2,row.overlap());assertEquals(List.of("A","A"),row.overlapGenes());assertEquals(0,row.pValue());assertEquals(Double.NEGATIVE_INFINITY,row.logPValue());assertFalse(row.finitePopulationModel());assertFalse(row.underflowedP());
    }
    @Test void repeatedSetMembersAffectOriginalSizeButDoNotMultiplyEachQueryMatch() {
        var sets=List.of(set("all","A","B","C","D"),set("T.up","A","A"));assertThrows(IllegalArgumentException.class,()->test(List.of("A"),sets));
        var r=FumaDegEnrichment.test(List.of("A"),BG,sets,FumaDegEnrichment.Multiplicity.RETAIN_SOURCE,FumaDegEnrichment.OutsideQuery.ERROR,LIMITS,()->{});var row=r.rows().get(0);assertEquals(2,row.setSize());assertEquals(1,row.duplicateSetMembers());assertEquals(1,row.overlap());assertEquals(.5,row.pValue(),2e-15);assertFalse(row.finitePopulationModel());
    }
    @Test void sourceNameMatchingCanPlaceOneRowInMultipleFamilies() {
        var result=test(List.of("A","B"),List.of(set("all","A","B","C","D"),set("T.up.down","A","B"),set("Other.upper","C"),set("End.twoside.","A")));
        assertEquals(4,result.rows().size());assertEquals("T.up",result.rows().get(0).label());assertEquals(2,result.rows().get(0).familyTests());assertEquals(FumaDegEnrichment.Category.DOWN,result.rows().get(2).category());assertEquals("End",result.rows().get(3).label());assertEquals(1,result.rows().get(3).familyTests());
    }
    @Test void duplicateRowNamesKeepOriginalOrdinalsAndCorrectionMultiplicity() {
        var r=test(List.of("A","B"),List.of(set("all","A","B","C","D"),set("T.up","A","B"),set("T.up","A","B")));assertEquals(List.of(2,3),r.rows().stream().map(FumaDegEnrichment.Row::sourceRow).toList());for(var row:r.rows()){assertEquals(2,row.familyTests());assertEquals(1.0/3,row.adjustedPValue(),3e-15);}
    }
    @Test void declaredSourceSizeIsRetainedButRecomputedAfterBackgroundIntersection() {
        var r=test(List.of("A","B"),List.of(set("all","A","B","C","D"),new FumaDegEnrichment.DegSet("T.up",999,List.of("A","B","OUTSIDE"))));var row=r.rows().get(0);assertEquals(999,row.declaredSize());assertEquals(3,row.inputMembers());assertEquals(2,row.setSize());assertEquals(1.0/6,row.pValue(),2e-15);assertEquals(7,r.sourceMembers());
    }
    @Test void sourceAllRowIsMandatoryAndInvalidDrawsAreNeverRepaired() {
        assertThrows(IllegalArgumentException.class,()->test(List.of("A"),List.of(set("T.up","A"))));assertThrows(IllegalArgumentException.class,()->FumaDegEnrichment.test(List.of("A","B"),BG,List.of(set("all","A"),set("T.up","A")),FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.RETAIN_SOURCE_SIZE,LIMITS,()->{}));assertThrows(IllegalArgumentException.class,()->FumaDegEnrichment.test(List.of("A"),BG,List.of(set("all","OUTSIDE")),FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.RETAIN_SOURCE_SIZE,LIMITS,()->{}));
    }
    @Test void nativeLogsDoNotRescueAnUnderflowedSourceAdjustedDouble() {
        var bg=new ArrayList<String>();for(int i=0;i<10000;i++)bg.add("G"+i);var query=List.copyOf(bg.subList(0,5000));var r=FumaDegEnrichment.test(query,bg,List.of(new FumaDegEnrichment.DegSet("all",10000,bg),new FumaDegEnrichment.DegSet("Rare.up",5000,query)),FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.ERROR,LIMITS,()->{});var row=r.rows().get(0);assertEquals(0,row.pValue());assertEquals(0,row.adjustedPValue());assertTrue(row.underflowedP());assertTrue(Double.isFinite(row.logPValue()));assertTrue(row.logPValue()<Math.log(Double.MIN_VALUE));assertTrue(row.finitePopulationModel());
    }
    @Test void boundsAndCallerInterruptionAbortTheCompleteOperation() {
        var sets=List.of(set("all","A","B","C","D"),set("T.up","A"));assertThrows(IllegalArgumentException.class,()->FumaDegEnrichment.test(List.of("A"),BG,sets,FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.ERROR,new FumaDegEnrichment.Limits(4,1,100),()->{}));var calls=new AtomicInteger();assertThrows(IllegalStateException.class,()->FumaDegEnrichment.test(List.of("A"),BG,sets,FumaDegEnrichment.Multiplicity.ERROR,FumaDegEnrichment.OutsideQuery.ERROR,LIMITS,()->{if(calls.incrementAndGet()>3)throw new IllegalStateException("caller deadline");}));
    }
    @Test void resultPopulationsAreImmutableAndCallerInputsAreNotMutated() {
        var query=new ArrayList<>(List.of("A","B"));var members=new ArrayList<>(List.of("A","B"));var r=test(query,List.of(set("all","A","B","C","D"),new FumaDegEnrichment.DegSet("T.up",2,members)));query.clear();members.clear();assertEquals(List.of("A","B"),r.rows().get(0).overlapGenes());assertThrows(UnsupportedOperationException.class,()->r.rows().clear());assertThrows(UnsupportedOperationException.class,()->r.expressedBackground().clear());assertThrows(UnsupportedOperationException.class,()->r.rows().get(0).overlapGenes().clear());
    }
    @Test void emptyFamiliesReturnNoInventedRowsAndMalformedIDsFail() {
        var r=test(List.of("A"),List.of(set("all","A","B","C","D"),set("Unmatched","A")));assertTrue(r.rows().isEmpty());assertThrows(IllegalArgumentException.class,()->test(List.of("A:B"),List.of(set("all","A","B","C","D"))));assertThrows(IllegalArgumentException.class,()->new FumaDegEnrichment.DegSet("bad\tlabel",1,List.of("A")));
    }
}
