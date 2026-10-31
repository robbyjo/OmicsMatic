/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.TranscriptSiteMapping.Boundary;
import org.jlinalg.genetics.TranscriptSiteMapping.Limits;
import org.jlinalg.genetics.TranscriptSiteMapping.Missing;
import org.jlinalg.genetics.TranscriptSiteMapping.Options;
import org.jlinalg.genetics.TranscriptSiteMapping.Reference;
import org.jlinalg.genetics.TranscriptSiteMapping.Site;
import org.jlinalg.genetics.TranscriptSiteMapping.Status;
import org.jlinalg.genetics.TranscriptSiteMapping.Transcript;
import org.jlinalg.genetics.TranscriptSiteMapping.Variant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TranscriptSiteMappingTest {
    private static final Set<Site> BOTH=Set.of(Site.TSS,Site.TES);
    private static final Set<String> CODING=Set.of("protein_coding");
    private static BigDecimal decimal(String value){return new BigDecimal(value);}
    private static Variant v(String id,long position,String p){return new Variant("trait",id,"1",position,p==null?null:decimal(p));}
    private static Transcript t(String gene,String id,long start,long end,String strand){return new Transcript(gene,id,"1",start,end,strand,"protein_coding");}
    private static Reference reference(List<Transcript> rows){return new Reference("fixture-v1","GRCh38","gene-v1","transcript-v1",rows);}
    private static Options options(long radius,Boundary boundary,Set<Site> sites,Missing missing,Limits limits){return new Options("GRCh38",decimal("1e-5"),radius,boundary,sites,CODING,missing,limits);}
    private static TranscriptSiteMapping.Result run(List<Variant> variants,List<Transcript> transcripts,long radius,Boundary boundary){return TranscriptSiteMapping.map(variants,reference(transcripts),options(radius,boundary,BOTH,Missing.EXCLUDE,Limits.defaults()),()->{});}

    @Test void longGeneInteriorDoesNotBecomeASiteHit(){
        var r=run(List.of(v("middle",500_000,"1e-6")),List.of(t("A","A1",100,1_000_000,"+")),10_000,Boundary.INCLUSIVE);
        assertEquals(Status.UNMAPPED_IN_SUPPLIED_REFERENCE,r.variants().get(0).status());assertTrue(r.hits().isEmpty());assertNull(r.variants().get(0).nearestHitDistanceBp());
    }
    @Test void plusAndMinusSitesAndSignedDistancesAreStrandOriented(){
        var r=run(List.of(v("v",101,"1e-6")),List.of(t("plus","p",100,200,"+"),t("minus","m",100,200,"-")),1,Boundary.INCLUSIVE);
        assertEquals(List.of(Site.TSS,Site.TES),r.hits().stream().map(TranscriptSiteMapping.Hit::site).toList());
        assertEquals(List.of(1L,-1L),r.hits().stream().map(TranscriptSiteMapping.Hit::signedDistanceBp).toList());assertEquals(2,r.genes().size());
    }
    @Test void reverseTssUsesTheRightEndpoint(){
        var r=TranscriptSiteMapping.map(List.of(v("v",201,"1e-6")),reference(List.of(t("A","tx",100,200,"-"))),options(1,Boundary.INCLUSIVE,Set.of(Site.TSS),Missing.ERROR,Limits.defaults()),()->{});
        assertEquals(1,r.hits().size());assertEquals(200,r.hits().get(0).sitePosition());assertEquals(-1,r.hits().get(0).signedDistanceBp());
    }
    @Test void inclusiveRadiusIncludesBothExactEdgesButNotTheNextBase(){
        var r=run(List.of(v("left-out",89,"0"),v("left",90,"0"),v("right",110,"0"),v("right-out",111,"0")),List.of(t("A","tx",100,100,"+")),10,Boundary.INCLUSIVE);
        assertEquals(List.of(0,2,2,0),r.variants().stream().map(TranscriptSiteMapping.VariantResult::siteHits).toList());
    }
    @Test void strictRadiusExcludesBothExactEdges(){
        var r=run(List.of(v("left",90,"0"),v("inside",91,"0"),v("right",110,"0")),List.of(t("A","tx",100,100,"+")),10,Boundary.STRICT);
        assertEquals(List.of(0,2,0),r.variants().stream().map(TranscriptSiteMapping.VariantResult::siteHits).toList());
    }
    @Test void zeroInclusiveRadiusRetainsCoincidentTssAndTesAsTwoHits(){
        var r=run(List.of(v("v",100,"0")),List.of(t("A","tx",100,100,"+")),0,Boundary.INCLUSIVE);
        assertEquals(2,r.hits().size());assertEquals(List.of(Site.TSS,Site.TES),r.hits().stream().map(TranscriptSiteMapping.Hit::site).toList());
        assertEquals(1,r.genes().size());assertEquals(1,r.genes().get(0).transcripts());assertEquals(2,r.genes().get(0).siteHits());
    }
    @Test void zeroStrictRadiusHasNoHitsEvenAtAnExactSite(){
        var r=run(List.of(v("v",100,"0")),List.of(t("A","tx",100,100,"+")),0,Boundary.STRICT);assertTrue(r.hits().isEmpty());
    }
    @Test void positiveDecimalBelowBinary64RangeIsRetainedAndSelected(){
        var r=run(List.of(v("tiny",100,"1e-1000")),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE);
        assertEquals(Status.MAPPED,r.variants().get(0).status());assertEquals(decimal("1e-1000"),r.variants().get(0).variant().p());assertEquals(1,r.warnings().size());
    }
    @Test void decimalThresholdUsesExactInclusiveComparison(){
        var r=run(List.of(v("at",100,"0.0000100000000000000000000000"),v("above",100,"0.0000100000000000000000000001"),v("below",100,"0.0000099999999999999999999999")),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE);
        assertEquals(List.of(Status.MAPPED,Status.ABOVE_P_CUTOFF,Status.MAPPED),r.variants().stream().map(TranscriptSiteMapping.VariantResult::status).toList());
    }
    @Test void missingPExclusionRetainsTheOriginalRow(){
        var r=run(List.of(v("missing",100,null),v("v",100,"1e-6")),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE);
        assertEquals(2,r.variants().size());assertEquals(Status.MISSING_P_EXCLUDED,r.variants().get(0).status());assertEquals(2,r.hits().get(0).variantRow());assertTrue(r.warnings().stream().anyMatch(s->s.contains("MISSING P EXCLUSIONS: 1")));
    }
    @Test void missingPErrorRejectsTheWholePopulation(){
        assertTrue(assertThrows(IllegalArgumentException.class,()->TranscriptSiteMapping.map(List.of(v("v",100,null)),reference(List.of(t("A","tx",100,200,"+"))),options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,Limits.defaults()),()->{})).getMessage().contains("EXCLUDE"));
    }
    @Test void reportedZeroIsSelectedButHasAnExplicitRecoveryWarning(){
        var r=run(List.of(v("v",100,"0")),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE);
        assertEquals(Status.MAPPED,r.variants().get(0).status());assertTrue(r.warnings().stream().anyMatch(s->s.contains("REPORTED ZERO P: 1")&&s.contains("Required action:")));
    }
    @Test void unselectedBiotypesAndNoHitTranscriptsRemainInTheLedger(){
        var excluded=new Transcript("B","b","1",100,200,"+","lncRNA");
        var r=run(List.of(v("v",100,"1e-6")),List.of(excluded,t("A","a",500,600,"+")),0,Boundary.INCLUSIVE);
        assertEquals(2,r.transcripts().size());assertFalse(r.transcripts().get(0).selectedBiotype());assertTrue(r.transcripts().get(1).selectedBiotype());assertTrue(r.hits().isEmpty());assertEquals(0,r.transcripts().get(1).tssHits());
    }
    @Test void noSelectedBiotypeOrEligibleAssociationIsAnExplicitEmptyPopulation(){
        var r=run(List.of(v("v",100,"0.5")),List.of(new Transcript("A","a","1",100,200,"+","lncRNA")),0,Boundary.INCLUSIVE);
        assertEquals(Status.ABOVE_P_CUTOFF,r.variants().get(0).status());assertTrue(r.warnings().stream().anyMatch(s->s.contains("NO SELECTED TRANSCRIPTS")));assertTrue(r.warnings().stream().anyMatch(s->s.contains("NO ELIGIBLE ASSOCIATIONS")));
    }
    @Test void repeatedSitesRetainAllTranscriptsWithoutInflatingUniqueGeneMappings(){
        var r=run(List.of(v("v",100,"1e-6")),List.of(t("A","a1",100,200,"+"),t("A","a2",100,300,"+"),t("B","b",100,400,"+")),0,Boundary.INCLUSIVE);
        assertEquals(3,r.hits().size());assertEquals(2,r.genes().size());assertEquals(2,r.genes().get(0).transcripts());assertEquals(2,r.genes().get(0).siteHits());assertEquals(3,r.variants().get(0).transcripts());
    }
    @Test void siteChoiceIsIndependentOfBiotypeChoice(){
        var r=TranscriptSiteMapping.map(List.of(v("v",100,"1e-6")),reference(List.of(new Transcript("L","l","1",100,200,"-","lncRNA"))),new Options("GRCh38",decimal("1e-5"),0,Boundary.INCLUSIVE,Set.of(Site.TES),Set.of("lncRNA"),Missing.ERROR,Limits.defaults()),()->{});
        assertEquals(1,r.hits().size());assertEquals(Site.TES,r.hits().get(0).site());
    }
    @Test void inputOrderControlsHitOrderRatherThanCoordinateOrLexicalGeneOrder(){
        var rows=List.of(t("Z","z",200,200,"+"),t("A","a",100,100,"+"));
        var r=run(List.of(v("second-position",200,"0"),v("first-position",100,"0")),rows,100,Boundary.INCLUSIVE);
        assertEquals(List.of(1,1,2,2,1,1,2,2),r.hits().stream().map(TranscriptSiteMapping.Hit::transcriptRow).toList());assertEquals(List.of("Z","A","Z","A"),r.genes().stream().map(TranscriptSiteMapping.GeneResult::gene).toList());
    }
    @Test void sameVariantCanHaveDifferentPValuesInDifferentContexts(){
        var r=run(List.of(v("v",100,"1e-6"),new Variant("other","v","1",100,decimal(".1"))),List.of(t("A","tx",100,100,"+")),0,Boundary.INCLUSIVE);
        assertEquals(List.of(Status.MAPPED,Status.ABOVE_P_CUTOFF),r.variants().stream().map(TranscriptSiteMapping.VariantResult::status).toList());
    }
    @Test void duplicatedVariantContextRejectsEvenIfCoordinatesAreIdentical(){
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(List.of(v("v",100,"0"),v("v",100,"0")),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE)).getMessage().contains("Duplicate variant/context"));
    }
    @Test void reusedVariantIdsMustHaveConcordantCoordinatesAcrossContexts(){
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(List.of(v("v",100,"0"),new Variant("other","v","1",101,decimal("0"))),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE)).getMessage().contains("conflicting coordinates"));
    }
    @Test void transcriptIdsAreGloballyUniqueEvenForExcludedRows(){
        assertTrue(assertThrows(IllegalArgumentException.class,()->run(List.of(v("v",100,"0")),List.of(t("A","tx",100,200,"+"),new Transcript("B","tx","1",100,300,"+","lncRNA")),0,Boundary.INCLUSIVE)).getMessage().contains("Duplicate transcript"));
    }
    @Test void oneGeneCannotSilentlyChangeStrandChromosomeOrBiotype(){
        var a=t("A","a",100,200,"+");
        for(var other:List.of(t("A","b",100,300,"-"),new Transcript("A","b","2",100,300,"+","protein_coding"),new Transcript("A","b","1",100,300,"+","lncRNA")))assertTrue(assertThrows(IllegalArgumentException.class,()->run(List.of(v("v",100,"0")),List.of(a,other),0,Boundary.INCLUSIVE)).getMessage().contains("Gene has conflicting"));
    }
    @Test void assemblyAliasesAreNotAssumedEquivalent(){
        var opt=new Options("hg38",decimal("1e-5"),0,Boundary.INCLUSIVE,BOTH,CODING,Missing.ERROR,Limits.defaults());
        assertTrue(assertThrows(IllegalArgumentException.class,()->TranscriptSiteMapping.map(List.of(v("v",100,"0")),reference(List.of(t("A","tx",100,200,"+"))),opt,()->{})).getMessage().contains("builds differ"));
    }
    @Test void chromosomeAliasesAreNotNormalized(){
        var r=run(List.of(new Variant("trait","v","chr1",100,decimal("0"))),List.of(t("A","tx",100,100,"+")),0,Boundary.INCLUSIVE);assertTrue(r.hits().isEmpty());
    }
    @Test void maximumRadiusAndCoordinatesDoNotOverflow(){
        var r=run(List.of(v("low",1,"0"),v("high",Long.MAX_VALUE,"0")),List.of(t("A","a",1,1,"+"),t("B","b",Long.MAX_VALUE,Long.MAX_VALUE,"-")),Long.MAX_VALUE,Boundary.INCLUSIVE);
        assertEquals(8,r.hits().size());assertEquals(Long.MAX_VALUE-1,r.hits().get(2).distanceBp());assertEquals(Long.MAX_VALUE-1,r.hits().get(2).signedDistanceBp());
    }
    @Test void strictMaximumRadiusStillHandlesBothCoordinateExtremes(){
        var r=run(List.of(v("v",1,"0")),List.of(t("A","tx",Long.MAX_VALUE,Long.MAX_VALUE,"+")),Long.MAX_VALUE-1,Boundary.STRICT);assertTrue(r.hits().isEmpty());
    }
    @Test void aHitBudgetOfZeroPermitsACompleteEmptyResult(){
        var opt=options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,new Limits(2,0,1_000));
        assertTrue(TranscriptSiteMapping.map(List.of(v("v",500,"0")),reference(List.of(t("A","tx",100,200,"+"))),opt,()->{}).hits().isEmpty());
    }
    @Test void hitBudgetRejectsTheCompleteCoincidentSetInsteadOfTruncating(){
        var opt=options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,new Limits(2,1,1_000));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TranscriptSiteMapping.map(List.of(v("v",100,"0")),reference(List.of(t("A","tx",100,100,"+"))),opt,()->{})).getMessage().contains("hit budget"));
    }
    @Test void combinedInputBudgetCountsVariantsAndTranscripts(){
        var opt=options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,new Limits(1,2,1_000));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TranscriptSiteMapping.map(List.of(v("v",100,"0")),reference(List.of(t("A","tx",100,100,"+"))),opt,()->{})).getMessage().contains("input row budget"));
    }
    @Test void operationBudgetIncludesSortQueryAndOutputWork(){
        var variants=List.of(v("v",100,"0"));var ref=reference(List.of(t("A","tx",100,100,"+")));var full=TranscriptSiteMapping.map(variants,ref,options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,Limits.defaults()),()->{});
        assertEquals(full,TranscriptSiteMapping.map(variants,ref,options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,new Limits(2,2,full.operations())),()->{}));
        assertTrue(assertThrows(IllegalArgumentException.class,()->TranscriptSiteMapping.map(variants,ref,options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,new Limits(2,2,full.operations()-1)),()->{})).getMessage().contains("operation budget"));
    }
    @Test void cancellationIsObservedInIndexingAndQueryWork(){
        var count=new AtomicInteger();var variants=List.of(v("v",100,"0"));var ref=reference(List.of(t("A","tx",100,100,"+")));var opt=options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,Limits.defaults());
        var full=TranscriptSiteMapping.map(variants,ref,opt,count::incrementAndGet);assertEquals(full.operations(),count.get());
        for(int stop:new int[]{1,3,7,(int)full.operations()}){count.set(0);assertThrows(IllegalStateException.class,()->TranscriptSiteMapping.map(variants,ref,opt,()->{if(count.incrementAndGet()==stop)throw new IllegalStateException("cancelled");}));}
    }
    @Test void callerCollectionsAndResultLedgersAreImmutableSnapshots(){
        var rows=new ArrayList<>(List.of(t("A","tx",100,100,"+")));var ref=reference(rows);rows.clear();
        var r=TranscriptSiteMapping.map(List.of(v("v",100,"0")),ref,options(0,Boundary.INCLUSIVE,BOTH,Missing.ERROR,Limits.defaults()),()->{});
        assertEquals(1,ref.transcripts().size());assertThrows(UnsupportedOperationException.class,()->r.hits().clear());assertThrows(UnsupportedOperationException.class,()->r.variants().clear());assertThrows(UnsupportedOperationException.class,()->r.genes().clear());assertThrows(UnsupportedOperationException.class,()->r.transcripts().clear());assertThrows(UnsupportedOperationException.class,()->r.warnings().clear());
    }
    @Test void invalidCoordinatesProbabilitiesIdentitiesAndPoliciesReject(){
        assertThrows(IllegalArgumentException.class,()->v("v",0,"0"));assertThrows(IllegalArgumentException.class,()->v("v",1,"-0.1"));assertThrows(IllegalArgumentException.class,()->v("v",1,"1.1"));assertThrows(IllegalArgumentException.class,()->v("bad\tID",1,"0"));
        assertThrows(IllegalArgumentException.class,()->t("A","tx",100,99,"+"));assertThrows(IllegalArgumentException.class,()->t("A","tx",100,200,"."));
        assertThrows(IllegalArgumentException.class,()->options(-1,Boundary.INCLUSIVE,BOTH,Missing.ERROR,Limits.defaults()));assertThrows(IllegalArgumentException.class,()->options(1,Boundary.INCLUSIVE,Set.of(),Missing.ERROR,Limits.defaults()));
        assertThrows(IllegalArgumentException.class,()->new Limits(0,0,1));assertThrows(IllegalArgumentException.class,()->new Limits(1,-1,1));assertThrows(IllegalArgumentException.class,()->new Limits(1,0,0));
    }
    @Test void emptyAssociationOrReferencePopulationRejects(){
        assertThrows(IllegalArgumentException.class,()->run(List.of(),List.of(t("A","tx",100,200,"+")),0,Boundary.INCLUSIVE));assertThrows(IllegalArgumentException.class,()->run(List.of(v("v",100,"0")),List.of(),0,Boundary.INCLUSIVE));
    }
    @Test void transcriptCountsIncludeEveryAssociationContext(){
        var r=run(List.of(v("v",100,"0"),new Variant("other","v","1",100,decimal("1e-6"))),List.of(t("A","tx",100,100,"+")),0,Boundary.INCLUSIVE);
        assertEquals(2,r.transcripts().get(0).tssHits());assertEquals(2,r.transcripts().get(0).tesHits());assertEquals(2,r.genes().size());
    }
    @Test void nearestHitDistanceIsComputedOnlyAmongTheRequestedSites(){
        var r=TranscriptSiteMapping.map(List.of(v("v",101,"1e-6")),reference(List.of(t("A","tx",100,200,"+"))),options(100,Boundary.INCLUSIVE,Set.of(Site.TES),Missing.ERROR,Limits.defaults()),()->{});
        assertEquals(99,r.variants().get(0).nearestHitDistanceBp());assertEquals(99,r.genes().get(0).nearestHitDistanceBp());
    }
}
