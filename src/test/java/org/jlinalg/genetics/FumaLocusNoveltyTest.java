/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.FumaLocusNovelty.Boundary;
import org.jlinalg.genetics.FumaLocusNovelty.Coverage;
import org.jlinalg.genetics.FumaLocusNovelty.Known;
import org.jlinalg.genetics.FumaLocusNovelty.Lead;
import org.jlinalg.genetics.FumaLocusNovelty.Limits;
import org.jlinalg.genetics.FumaLocusNovelty.Options;
import org.jlinalg.genetics.FumaLocusNovelty.Reference;
import org.jlinalg.genetics.FumaLocusNovelty.Status;
import org.jlinalg.genetics.FumaLocusNovelty.TraitPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FumaLocusNoveltyTest {
    private static final Lead LEAD=new Lead("L1","trait-a","1",500000);
    private static final Runnable CHECK=()->{};
    private static Reference reference(List<Known> known,List<Coverage> coverage){return new Reference("synthetic-known-v1","GRCh37",known,coverage);}
    private static Options options(int radius,Boundary boundary,TraitPolicy policy){return new Options("GRCh37",radius,boundary,policy,Limits.defaults());}
    private static FumaLocusNovelty.Result run(List<Lead> leads,List<Known> known,List<Coverage> coverage,int radius,Boundary boundary,TraitPolicy policy){return FumaLocusNovelty.compare(leads,reference(known,coverage),options(radius,boundary,policy),CHECK);}
    @Test void exact250kbEndpointsAreAnExplicitChoice(){
        var known=List.of(new Known("right","trait-a","1",750000),new Known("left","trait-a","1",250000),new Known("inside","trait-a","1",250001));
        var inclusive=run(List.of(LEAD),known,List.of(),250000,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        var strict=run(List.of(LEAD),known,List.of(),250000,Boundary.STRICT,TraitPolicy.EXACT);
        assertEquals(List.of(2,3,1),inclusive.matches().stream().map(FumaLocusNovelty.Match::knownRow).toList());assertEquals(List.of(3),strict.matches().stream().map(FumaLocusNovelty.Match::knownRow).toList());
        assertEquals(250000,inclusive.summaries().get(0).windowStart());assertEquals(749999,strict.summaries().get(0).windowEnd());assertEquals(249999L,strict.matches().get(0).distanceBp());
    }
    @Test void traitPolicyDoesNotInferSynonymsOrDiscardDeclaredAllTraitMatches(){
        var known=List.of(new Known("k","Trait-A","1",500000));
        var exact=run(List.of(LEAD),known,List.of(new Coverage("trait-a","1",1,1000000)),1,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        var all=run(List.of(LEAD),known,List.of(new Coverage("*","1",1,1000000)),1,Boundary.INCLUSIVE,TraitPolicy.ALL_KNOWN);
        assertEquals(Status.NOVEL_RELATIVE_TO_REFERENCE,exact.summaries().get(0).status());assertEquals(Status.KNOWN_WITHIN_WINDOW,all.summaries().get(0).status());
        assertThrows(IllegalArgumentException.class,()->run(List.of(LEAD),known,List.of(new Coverage("trait-a","1",1,1000000)),1,Boundary.INCLUSIVE,TraitPolicy.ALL_KNOWN));
    }
    @Test void chromosomesUseLiteralIdentityWithoutPrefixOrSexCodeConversion(){
        var known=List.of(new Known("k","trait-a","chr1",500000),new Known("x","trait-a","23",100));
        assertTrue(run(List.of(LEAD,new Lead("X","trait-a","X",100)),known,List.of(),1,Boundary.INCLUSIVE,TraitPolicy.EXACT).matches().isEmpty());
    }
    @Test void noMatchWithoutCompleteCoverageIsUnassessableRatherThanNovel(){
        var r=run(List.of(LEAD),List.of(),List.of(new Coverage("trait-a","1",499999,500000)),1,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertEquals(Status.UNASSESSABLE_REFERENCE_COVERAGE,r.summaries().get(0).status());assertNull(r.summaries().get(0).nearestDistanceBp());assertTrue(r.warnings().get(1).contains("never relabel them novel"));
    }
    @Test void aKnownMatchRemainsEvidenceWhenReferenceCoverageIsUnknown(){
        var r=run(List.of(LEAD),List.of(new Known("same-coordinate","trait-a","1",500000)),List.of(),1,Boundary.STRICT,TraitPolicy.EXACT);
        assertEquals(Status.KNOWN_WITHIN_WINDOW,r.summaries().get(0).status());assertFalse(r.summaries().get(0).referenceWindowCovered());assertEquals(0L,r.summaries().get(0).nearestDistanceBp());
    }
    @Test void adjacentCoverageIntervalsJoinButOneUncoveredBasePreventsAbsenceClaim(){
        var lead=new Lead("L","t","1",10);
        var covered=run(List.of(lead),List.of(),List.of(new Coverage("t","1",11,13),new Coverage("t","1",7,10)),3,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        var gap=run(List.of(lead),List.of(),List.of(new Coverage("t","1",12,13),new Coverage("t","1",7,10)),3,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertEquals(Status.NOVEL_RELATIVE_TO_REFERENCE,covered.summaries().get(0).status());assertEquals(Status.UNASSESSABLE_REFERENCE_COVERAGE,gap.summaries().get(0).status());
    }
    @Test void coverageUnionContainsNestedOverlappingAndRepeatedIntervals(){
        var r=run(List.of(new Lead("L","t","1",10)),List.of(),List.of(new Coverage("t","1",8,12),new Coverage("t","1",1,30),new Coverage("t","1",8,12)),9,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertTrue(r.summaries().get(0).referenceWindowCovered());
    }
    @Test void coordinateEdgesAndHugeRadiiNeverOverflow(){
        var leads=List.of(new Lead("first","t","1",1),new Lead("last","t","1",Integer.MAX_VALUE));
        var r=run(leads,List.of(new Known("first","t","1",1),new Known("last","t","1",Integer.MAX_VALUE)),List.of(new Coverage("t","1",1,Integer.MAX_VALUE)),Integer.MAX_VALUE,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertEquals(4,r.matches().size());assertEquals(2147483646L,r.matches().get(1).distanceBp());assertTrue(r.summaries().stream().allMatch(s->s.windowStart()==1&&s.windowEnd()==Integer.MAX_VALUE&&s.referenceWindowCovered()));
    }
    @Test void duplicateKnownVariantAssociationsRetainAllSourceRowsAndStableTies(){
        var known=List.of(new Known("same","t","1",12),new Known("same","t","1",10),new Known("same","t","1",10));
        var r=run(List.of(new Lead("L","t","1",10)),known,List.of(),2,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertEquals(List.of(2,3,1),r.matches().stream().map(FumaLocusNovelty.Match::knownRow).toList());assertEquals(3,r.summaries().get(0).knownMatches());
    }
    @Test void leadOrderIsPreservedAndDuplicateLocusKeysAreTraitSpecific(){
        var leads=List.of(new Lead("L","b","1",10),new Lead("L","a","1",10));
        var r=run(leads,List.of(new Known("k","a","1",10)),List.of(),1,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertEquals(leads,r.summaries().stream().map(FumaLocusNovelty.Summary::lead).toList());assertEquals(2,r.matches().get(0).leadRow());
        assertThrows(IllegalArgumentException.class,()->run(List.of(LEAD,LEAD),List.of(),List.of(),1,Boundary.STRICT,TraitPolicy.EXACT));
    }
    @Test void assemblyMismatchAndInvalidExplicitChoicesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->FumaLocusNovelty.compare(List.of(LEAD),new Reference("k","hg19",List.of(),List.of()),options(1,Boundary.INCLUSIVE,TraitPolicy.EXACT),CHECK));
        assertThrows(IllegalArgumentException.class,()->options(0,Boundary.INCLUSIVE,TraitPolicy.EXACT));assertThrows(IllegalArgumentException.class,()->options(1,null,TraitPolicy.EXACT));
        assertThrows(IllegalArgumentException.class,()->new Lead("L","t","1",0));assertThrows(IllegalArgumentException.class,()->new Known("k\n","t","1",1));assertThrows(IllegalArgumentException.class,()->new Coverage("t","1",2,1));
        assertThrows(IllegalArgumentException.class,()->run(List.of(),List.of(),List.of(),1,Boundary.INCLUSIVE,TraitPolicy.EXACT));
    }
    @Test void capacitiesRefuseCompleteOversizedPopulationsInsteadOfTruncating(){
        var known=List.of(new Known("a","trait-a","1",500000),new Known("b","trait-a","1",500000));
        for(Limits limits:List.of(new Limits(2,10,1000),new Limits(100,1,1000),new Limits(100,10,1))){
            var error=assertThrows(IllegalArgumentException.class,()->FumaLocusNovelty.compare(List.of(LEAD),reference(known,List.of()),new Options("GRCh37",1,Boundary.INCLUSIVE,TraitPolicy.EXACT,limits),CHECK));assertTrue(error.getMessage().contains("budget exceeded"));
        }
    }
    @Test void cooperativeCancellationIsPropagatedDuringIndexAndQueryWork(){
        var calls=new AtomicInteger();var marker=new IllegalStateException("cancelled");
        assertSame(marker,assertThrows(IllegalStateException.class,()->FumaLocusNovelty.compare(List.of(LEAD),reference(List.of(new Known("k","trait-a","1",500000)),List.of()),options(1,Boundary.INCLUSIVE,TraitPolicy.EXACT),()->{if(calls.incrementAndGet()==3)throw marker;})));
    }
    @Test void inputAndResultContainersAreDefensivelyCopied(){
        var known=new ArrayList<Known>();known.add(new Known("a","trait-a","1",500000));var reference=reference(known,List.of());known.clear();
        var result=FumaLocusNovelty.compare(List.of(LEAD),reference,options(1,Boundary.INCLUSIVE,TraitPolicy.EXACT),CHECK);assertEquals(1,result.matches().size());
        assertThrows(UnsupportedOperationException.class,()->reference.variants().clear());assertThrows(UnsupportedOperationException.class,()->result.matches().clear());assertThrows(UnsupportedOperationException.class,()->result.summaries().clear());
    }
    @Test void emptyKnownReferenceRequiresAnExplicitCoverageAssertionForRelativeNovelty(){
        var covered=run(List.of(LEAD),List.of(),List.of(new Coverage("trait-a","1",1,1000000)),250000,Boundary.INCLUSIVE,TraitPolicy.EXACT);
        assertEquals(Status.NOVEL_RELATIVE_TO_REFERENCE,covered.summaries().get(0).status());assertEquals(0,covered.summaries().get(0).knownMatches());assertTrue(covered.warnings().get(0).contains("catalog release"));
    }
    @Test void indexedResultsMatchIndependentExhaustiveDistanceAndPerBaseCoverageOracle(){
        var random=new Random(812331L);
        for(int repeat=0;repeat<150;repeat++)for(Boundary boundary:Boundary.values())for(TraitPolicy policy:TraitPolicy.values()){
            var leads=new ArrayList<Lead>();var known=new ArrayList<Known>();var coverage=new ArrayList<Coverage>();
            for(int i=0;i<12;i++)leads.add(new Lead("L"+i,"t"+random.nextInt(2),""+(1+random.nextInt(2)),5+random.nextInt(20)));
            for(int i=0;i<40;i++)known.add(new Known("k"+(i%7),"t"+random.nextInt(2),""+(1+random.nextInt(2)),1+random.nextInt(30)));
            for(int i=0;i<20;i++){int start=1+random.nextInt(30);coverage.add(new Coverage(policy==TraitPolicy.EXACT?"t"+random.nextInt(2):"*",""+(1+random.nextInt(2)),start,start+random.nextInt(5)));}
            int radius=1+random.nextInt(5);var actual=run(leads,known,coverage,radius,boundary,policy);int matches=0;
            for(int i=0;i<leads.size();i++){
                Lead lead=leads.get(i);var rows=new ArrayList<Integer>();
                for(int j=0;j<known.size();j++){Known k=known.get(j);long d=Math.abs((long)k.position()-lead.position());if(k.chromosome().equals(lead.chromosome())&&(policy==TraitPolicy.ALL_KNOWN||k.trait().equals(lead.trait()))&&(boundary==Boundary.INCLUSIVE?d<=radius:d<radius))rows.add(j+1);}
                var observed=new ArrayList<Integer>();for(var m:actual.matches())if(m.leadRow()==i+1)observed.add(m.knownRow());rows.sort(Integer::compareTo);observed.sort(Integer::compareTo);assertEquals(rows,observed);matches+=rows.size();
                boolean complete=true;
                for(int base=Math.max(1,lead.position()-radius);base<=lead.position()+radius;base++){
                    if(boundary==Boundary.STRICT&&Math.abs(base-lead.position())==radius)continue;
                    boolean found=false;for(Coverage c:coverage)if(c.chromosome().equals(lead.chromosome())&&(policy==TraitPolicy.ALL_KNOWN||c.trait().equals(lead.trait()))&&base>=c.start()&&base<=c.end())found=true;
                    if(!found)complete=false;
                }
                var summary=actual.summaries().get(i);assertEquals(complete,summary.referenceWindowCovered());assertEquals(rows.size(),summary.knownMatches());assertEquals(!rows.isEmpty()?Status.KNOWN_WITHIN_WINDOW:complete?Status.NOVEL_RELATIVE_TO_REFERENCE:Status.UNASSESSABLE_REFERENCE_COVERAGE,summary.status());
            }
            assertEquals(matches,actual.matches().size());
        }
    }
}
