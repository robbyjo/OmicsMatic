/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.FumaVariantAnnotations.Annotation;
import org.jlinalg.genetics.FumaVariantAnnotations.Consequence;
import org.jlinalg.genetics.FumaVariantAnnotations.Gene;
import org.jlinalg.genetics.FumaVariantAnnotations.Ld;
import org.jlinalg.genetics.FumaVariantAnnotations.Limits;
import org.jlinalg.genetics.FumaVariantAnnotations.Reference;
import org.jlinalg.genetics.FumaVariantAnnotations.Region;
import org.jlinalg.genetics.FumaVariantAnnotations.Track;
import org.jlinalg.genetics.FumaVariantAnnotations.Variant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FumaVariantAnnotationsTest {
    private static final class Data {
        List<Variant> variants=new ArrayList<>(List.of(new Variant("u1","1",100,.01),new Variant("u2","1",200,Double.NaN)));
        List<Gene> genes=new ArrayList<>(List.of(new Gene("G1","ONE","1",100,199),new Gene("G2","TWO","1",200,300)));
        List<Annotation> annotations=new ArrayList<>(List.of(new Annotation("u1",10,"1a",List.of(2,10)),new Annotation("u2",Double.NaN,"",List.of(10,2))));
        List<String> states=List.of("s1","s2");
        List<Consequence> consequences=new ArrayList<>(List.of(new Consequence("u1","G1","exonic"),new Consequence("u2",null,"intergenic")));
        List<Ld> ld=new ArrayList<>(List.of(new Ld("u1","u1"),new Ld("u1","u2")));
        List<String> independent=new ArrayList<>(List.of("u1"));
        List<Reference> references=new ArrayList<>(List.of(new Reference("exonic",10,.5),new Reference("intergenic",10,.5)));
        List<Track> tracks=new ArrayList<>();Limits limits=Limits.defaults();Runnable check=()->{};
        FumaVariantAnnotations.Result run(){return FumaVariantAnnotations.annotate(variants,genes,annotations,states,consequences,ld,independent,references,tracks,limits,check);}
    }
    @Test void adjacentAndOverlappingGenesBothHaveZeroDistance(){
        var d=new Data();var r=d.run();assertEquals(List.of("G1","G2"),r.variants().get(1).nearest().stream().map(FumaVariantAnnotations.Nearest::gene).toList());assertTrue(r.variants().get(1).nearest().stream().allMatch(n->n.distance()==0));
    }
    @Test void allNearestTiesKeepOriginalGeneRowsRatherThanCoordinateOrder(){
        var d=new Data();d.variants.set(0,new Variant("u1","1",100,.01));d.genes=List.of(new Gene("RIGHT","R","1",110,120),new Gene("LEFT","L","1",80,90));var n=d.run().variants().get(0).nearest();assertEquals(List.of("RIGHT","LEFT"),n.stream().map(FumaVariantAnnotations.Nearest::gene).toList());assertEquals(List.of(9,9),n.stream().map(FumaVariantAnnotations.Nearest::distance).toList());
    }
    @Test void onlyLiteralGeneChromosomeXConvertsTo23(){
        var d=new Data();d.variants.set(0,new Variant("u1","23",100,.01));d.genes=List.of(new Gene("X","XGENE","X",90,110),new Gene("chrX","OTHER","chrX",90,110));assertEquals("X",d.run().variants().get(0).nearest().get(0).gene());d.variants.set(0,new Variant("u1","x",100,.01));assertTrue(d.run().variants().get(0).nearest().isEmpty());
    }
    @Test void largeCoordinatesDoNotOverflowTheAdjacentQuery(){
        var d=new Data();d.variants.set(0,new Variant("u1","1",Integer.MAX_VALUE,.01));d.genes=List.of(new Gene("G","G","1",1,1));assertEquals(Integer.MAX_VALUE-2,d.run().variants().get(0).nearest().get(0).distance());
    }
    @Test void stateTieUsesNumericOrderAndMissingnessPropagatesOnlyToMinimum(){
        var d=new Data();d.annotations.set(0,new Annotation("u1",10,"1a",Arrays.asList(10,null)));var r=d.run();assertNull(r.variants().get(0).minimumState());assertEquals(10,r.variants().get(0).commonState());assertEquals(2,r.variants().get(1).commonState());
    }
    @Test void allMissingStateModeRejectsGenuineSourceSerializationFailure(){
        var d=new Data();d.annotations.set(0,new Annotation("u1",10,"1a",Arrays.asList(null,null)));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("All chromatin states are NA"));
    }
    @Test void singletonStateKeepsMatrixShapeWithSourceDifferenceWarning(){
        var d=new Data();d.states=List.of("only");d.annotations=List.of(new Annotation("u1",1,"1",List.of(2)),new Annotation("u2",2,"2",List.of(10)));var r=d.run();assertEquals(2,r.variants().get(0).minimumState());assertEquals(10,r.variants().get(1).commonState());assertTrue(r.warnings().stream().anyMatch(s->s.contains("SOURCE DIMENSION DIFFERENCE")));
    }
    @Test void firstAnnotationMatchAndEveryUnusedDuplicateRowRemainAuditable(){
        var d=new Data();d.annotations.add(0,new Annotation("u1",123,"raw",List.of(3,3)));d.annotations.add(new Annotation("extra",999,"9",List.of(15,15)));var r=d.run();assertEquals(123,r.variants().get(0).cadd());assertEquals(1,r.annotationRows().stream().filter(a->a.uid().equals("u1")&&a.selected()).count());assertEquals("later-duplicate",r.annotationRows().get(1).reason());assertEquals("outside-candidates",r.annotationRows().get(3).reason());
    }
    @Test void absentScoreRowsCannotBecomeImputedMissingAnnotations(){
        var d=new Data();d.annotations.remove(0);assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("Missing annotation row"));
    }
    @Test void blankRdbBecomesMissingAndRawNonNumericRanksStayUnchanged(){
        var r=new Data().run();assertEquals("1a",r.variants().get(0).rdb());assertNull(r.variants().get(1).rdb());assertTrue(Double.isNaN(r.variants().get(1).cadd()));assertTrue(r.warnings().stream().anyMatch(s->s.contains("MISSING SUPPLIED ANNOTATIONS")));
    }
    @Test void firstGeneSymbolMatchFallbackAndBlankSymbolRemainDistinct(){
        var d=new Data();d.genes=List.of(new Gene("G1",null,"1",100,199),new Gene("G1","LATER","1",100,199),new Gene("G2","","1",200,300));d.consequences.add(new Consequence("u1","G2","intronic"));d.consequences.add(new Consequence("u1","UNKNOWN","splicing"));var r=d.run();assertEquals("G1",r.consequences().get(0).symbol());assertEquals("",r.consequences().get(2).symbol());assertEquals("UNKNOWN",r.consequences().get(3).symbol());assertEquals(3,r.variants().get(1).nearest().size());
    }
    @Test void consequenceOrderAndUniqueVariantCategoryCountsAreSeparate(){
        var d=new Data();d.consequences.add(0,new Consequence("u1","G1","intergenic"));d.consequences.add(new Consequence("u1","G2","exonic"));var r=d.run();assertEquals("intergenic:exonic",r.variants().get(0).function());assertEquals(4,r.consequences().size());assertEquals(1,r.categories().get(0).count());assertEquals(2,r.categories().get(1).count());assertFalse(r.consequences().get(3).firstVariantCategory());
    }
    @Test void missingCategoryCanLabelFunctionButNeverEntersCounts(){
        var d=new Data();d.consequences.add(new Consequence("u1",null,null));var r=d.run();assertEquals("exonic:NA",r.variants().get(0).function());assertEquals(2,r.categories().stream().mapToLong(FumaVariantAnnotations.Category::count).sum());
    }
    @Test void countsUseSuppliedReferenceProportionRatherThanCountFraction(){
        var d=new Data();d.references=List.of(new Reference("exonic",10,.1),new Reference("intergenic",10,.9));var c=d.run().categories().get(0);assertEquals(.5,c.proportion());assertEquals(5,c.enrichment());
    }
    @Test void noReferenceCategoryMatchesRetainsUndefinedProportionsAndDegenerateP(){
        var d=new Data();d.references=List.of(new Reference("unknown",20,1));var c=d.run().categories().get(0);assertEquals(0,c.count());assertTrue(Double.isNaN(c.proportion()));assertTrue(Double.isNaN(c.enrichment()));assertEquals(1,c.fisherP());
    }
    @Test void zeroReferenceProportionRetainsInfiniteEnrichment(){
        var d=new Data();d.references=List.of(new Reference("exonic",10,0),new Reference("intergenic",10,1));var r=d.run();assertEquals(Double.POSITIVE_INFINITY,r.categories().get(0).enrichment());assertTrue(r.warnings().stream().anyMatch(s->s.contains("UNDEFINED/INFINITE ENRICHMENT")));
    }
    @Test void twoSidedFisherMatchesIndependentSmallExactTable(){
        var d=new Data();d.variants=new ArrayList<>();d.annotations=new ArrayList<>();d.consequences=new ArrayList<>();for(int i=0;i<10;i++){String uid="v"+i;d.variants.add(new Variant(uid,"1",100+i,.01));d.annotations.add(new Annotation(uid,1,"1",List.of(1,1)));d.consequences.add(new Consequence(uid,"G1",i==0?"A":"B"));}d.ld=List.of(new Ld("v0","v0"));d.independent=List.of("v0");d.references=List.of(new Reference("A",12,.5),new Reference("B",12,.5));assertEquals(.0027594561852200836,d.run().categories().get(0).fisherP(),2e-15);
    }
    @Test void impossibleSelectedReferenceSubsetFailsWithoutPublishingProbabilities(){
        var d=new Data();d.references=List.of(new Reference("exonic",0,0),new Reference("intergenic",20,1));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("not a subset"));
    }
    @Test void largeAsymmetricComplementCategoriesMatchIndependentExactProduct(){
        var d=new Data();d.variants=new ArrayList<>();d.annotations=new ArrayList<>();d.consequences=new ArrayList<>();d.genes=List.of(new Gene("G1","ONE","1",1,5000));for(int i=0;i<5000;i++){String uid="v"+i;d.variants.add(new Variant(uid,"1",i+1,.01));d.annotations.add(new Annotation(uid,1,"1",List.of(1,1)));d.consequences.add(new Consequence(uid,"G1",i==0?"A":"B"));}d.ld=List.of(new Ld("v0","v0"));d.independent=List.of("v0");d.references=List.of(new Reference("A",1000,.00001),new Reference("B",99_999_000,.99999));var categories=d.run().categories();double exactProduct=.048772002157643783128551449529;assertEquals(exactProduct,categories.get(0).fisherP(),2e-16);assertEquals(categories.get(0).fisherP(),categories.get(1).fisherP(),1e-17);
    }
    @Test void zeroReferencePopulationIsAnExplicitAdmissionRejection(){
        var d=new Data();d.references=List.of(new Reference("unknown",0,0));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("positive"));
    }
    @Test void sourceLdCountsRowsRatherThanUniqueTargetsAndKeepsAbsentGroupsMissing(){
        var d=new Data();d.ld.add(new Ld("u1","u1"));d.ld.add(new Ld("u1","outside"));d.independent.add("u2");var rows=d.run().independent();assertEquals(4,rows.get(0).ldRows());assertEquals(2,rows.get(0).gwasLdRows());assertNull(rows.get(1).ldRows());assertNull(rows.get(1).gwasLdRows());
    }
    @Test void emptyOriginalLdOrGwasAggregatesReject(){
        var d=new Data();d.ld=List.of();assertThrows(IllegalArgumentException.class,d::run);d.ld=List.of(new Ld("u1","u2"));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("GWAS recount aggregate is empty"));
    }
    @Test void bedHalfOpenBoundsPreserveDuplicateHitsAndSourceRowOrder(){
        var d=new Data();d.tracks=List.of(new Track("Road/A.bed.gz",List.of(new Region("1",99,100),new Region("1",99,100),new Region("1",100,200))));var r=d.run();assertEquals(List.of(1,2,3),r.bedHits().stream().map(FumaVariantAnnotations.BedHit::regionRow).toList());assertEquals(List.of(1,1,2),r.bedHits().stream().map(FumaVariantAnnotations.BedHit::variantRow).toList());assertEquals(1,r.variants().get(0).bedFlags().get("Road_A"));assertEquals("Road/A",r.bedHits().get(0).dataset());
    }
    @Test void zeroHitTracksHaveExplicitStatusAndNoInventedSourceColumns(){
        var d=new Data();d.tracks=List.of(new Track("Z.bed.gz",List.of(new Region("9",0,10))));var r=d.run();assertTrue(r.bedHits().isEmpty());assertFalse(r.tracks().get(0).sourceColumnWritten());assertTrue(r.variants().get(0).bedFlags().isEmpty());
    }
    @Test void filenameDerivedColumnCollisionsAreRejected(){
        var d=new Data();d.tracks=List.of(new Track("A/B.bed.gz",List.of()),new Track("A_B.bed.gz",List.of()));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("colliding"));
    }
    @Test void unknownConsequenceCandidatesRejectMissingCoordinateProvenance(){
        var d=new Data();d.consequences.add(new Consequence("unknown","G1","exonic"));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("unknown candidate UID"));
    }
    @Test void completePopulationBudgetsRejectRatherThanTruncate(){
        var d=new Data();d.limits=new Limits(1,1000,100000,1000,1000);assertThrows(IllegalArgumentException.class,d::run);d.limits=new Limits(100,1,100000,1000,1000);assertThrows(IllegalArgumentException.class,d::run);d.limits=new Limits(100,1000,1,1000,1000);assertThrows(IllegalArgumentException.class,d::run);d.limits=new Limits(100,1000,100000,1,1000);assertThrows(IllegalArgumentException.class,d::run);d.limits=new Limits(100,1000,100000,1000,1);assertThrows(IllegalArgumentException.class,d::run);
    }
    @Test void cancellationCallbackAndResultImmutabilityAreEnforced(){
        var d=new Data();var count=new AtomicInteger();d.check=()->{count.incrementAndGet();throw new IllegalStateException("deadline");};assertEquals("deadline",assertThrows(IllegalStateException.class,d::run).getMessage());assertEquals(1,count.get());d.check=()->{};var r=d.run();assertThrows(UnsupportedOperationException.class,()->r.variants().clear());assertThrows(UnsupportedOperationException.class,()->r.variants().get(0).bedFlags().put("x",1));
    }
    @Test void invalidResourcesRejectBeforeAnnotation(){
        assertThrows(IllegalArgumentException.class,()->new Region("1",1,1));assertThrows(IllegalArgumentException.class,()->new Annotation("u",1,"1",List.of(16,1)));assertThrows(IllegalArgumentException.class,()->new Gene("G","G","1",0,1));var d=new Data();d.references.add(d.references.get(0));assertThrows(IllegalArgumentException.class,d::run);d=new Data();d.variants.add(d.variants.get(0));var duplicate=d;assertThrows(IllegalArgumentException.class,duplicate::run);var roles=new Data();roles.states=List.of("CADD","s2");assertThrows(IllegalArgumentException.class,roles::run);var overflow=new Data();overflow.references=List.of(new Reference("exonic",Long.MAX_VALUE,1));assertTrue(assertThrows(IllegalArgumentException.class,overflow::run).getMessage().contains("maxReferencePopulation"));
    }
}
