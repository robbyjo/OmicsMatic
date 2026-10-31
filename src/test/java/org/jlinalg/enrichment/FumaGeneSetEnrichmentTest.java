package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FumaGeneSetEnrichmentTest {
    private static List<FumaGeneSetEnrichment.GeneRow> annotation() {
        List<FumaGeneSetEnrichment.GeneRow> rows=new ArrayList<>();
        for (int i=1;i<=20;i++) rows.add(new FumaGeneSetEnrichment.GeneRow("ENSG"+i,""+i,"G"+i,"protein_coding","1",i,i+1));return rows;
    }
    private static FumaGeneSetEnrichment.Prepared prepared() {return FumaGeneSetEnrichment.prepare(annotation(),List.of("1","2","3","4"),null,Set.of("all"),false);}
    private static FumaGeneSetEnrichment.Term term(String name,String... ids) {return new FumaGeneSetEnrichment.Term(name,"https://example.org/term",List.of(ids));}
    private static double[] adjust(double[] p,FumaGeneSetEnrichment.Adjustment method) {
        return java.util.Arrays.stream(FumaGeneSetEnrichment.adjustLog(java.util.Arrays.stream(p).map(Math::log).toArray(),method,10000)).map(Math::exp).toArray();
    }
    @Test void inspectionRetainsNamespaceAndAuditBeforeEligibilityButPrepareStillRejectsSingleton() {
        var result=FumaGeneSetEnrichment.inspect(annotation(),List.of("G1","ENSG2"),null,Set.of("all"),false);
        assertEquals("symbol",result.queryNamespace());assertEquals(List.of("1"),result.selected());
        assertEquals(List.of(),result.queryMapping().get(1).entrez());
        assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.prepare(annotation(),List.of("G1","ENSG2"),null,Set.of("all"),false));
    }
    @Test void inspectionRetainsEmptyBiotypeFilteredUniverseWithoutAdmittingATest() {
        var result=FumaGeneSetEnrichment.inspect(annotation(),List.of("ENSG1","ENSG2"),null,Set.of("absent"),false);
        assertTrue(result.universe().isEmpty());assertTrue(result.selected().isEmpty());
        assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.prepare(annotation(),List.of("ENSG1","ENSG2"),null,Set.of("absent"),false));
    }
    @Test void bhUsesEntireFamilyIncludingZeroHitsBeforeOverlapFilter() {
        var rows=FumaGeneSetEnrichment.test(prepared(),List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("hit","1","2"),term("null","19"),term("outside","999")))),FumaGeneSetEnrichment.Adjustment.BH,1,2,FumaGeneSetEnrichment.Duplicates.ERROR,10000);
        assertEquals(3,rows.size());var hit=rows.get(0);assertEquals(6.0/190,hit.pValue(),1e-15);assertEquals(18.0/190,hit.adjustedPValue(),1e-15);assertTrue(hit.retained());assertFalse(rows.get(1).retained());assertEquals(1,rows.get(2).pValue());
    }
    @Test void independentGmtFilesKeepSeparateCorrectionFamilies() {
        var rows=FumaGeneSetEnrichment.test(prepared(),List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("hit","1","2"))),new FumaGeneSetEnrichment.Family("b",List.of(term("null","19")))),FumaGeneSetEnrichment.Adjustment.BONFERRONI,1,0,FumaGeneSetEnrichment.Duplicates.ERROR,10000);
        assertEquals(rows.get(0).pValue(),rows.get(0).adjustedPValue());assertFalse(rows.get(1).retained());
    }
    @Test void duplicatePolicyPreservesOriginalCountOrFailsExplicitly() {
        var family=List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("dup","1","1","2"))));
        assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.test(prepared(),family,FumaGeneSetEnrichment.Adjustment.BH,1,0,FumaGeneSetEnrichment.Duplicates.ERROR,100));
        var r=FumaGeneSetEnrichment.test(prepared(),family,FumaGeneSetEnrichment.Adjustment.BH,1,0,FumaGeneSetEnrichment.Duplicates.ORIGINAL_COUNT,100).get(0);assertEquals(3,r.setSize());assertEquals(2,r.overlap());assertEquals(1,r.duplicateMembers());assertEquals(List.of("G1","G2"),r.genes());
    }
    @Test void shortRowsAreIgnoredButZeroBackgroundRowsAreTested() {
        var rows=FumaGeneSetEnrichment.test(prepared(),List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("ignored"),term("outside","999")))),FumaGeneSetEnrichment.Adjustment.BH,1,0,FumaGeneSetEnrichment.Duplicates.ERROR,100);assertEquals(1,rows.size());assertEquals(2,rows.get(0).row());assertEquals(0,rows.get(0).setSize());assertEquals(1,rows.get(0).pValue());
    }
    @Test void singletonGmtRowHasBlankLink() {
        var r=FumaGeneSetEnrichment.test(prepared(),List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("one","1")))),FumaGeneSetEnrichment.Adjustment.BH,1,0,FumaGeneSetEnrichment.Duplicates.ERROR,100).get(0);assertEquals("",r.link());
    }
    @Test void selectedSymbolsFollowAnnotationOrderAndRetainDuplicateEntrezRows() {
        var rows=annotation();rows.add(new FumaGeneSetEnrichment.GeneRow("ENSG1B","1","ALIAS1","protein_coding","1",1,2));var p=FumaGeneSetEnrichment.prepare(rows,List.of("1","2"),null,Set.of("all"),false);
        var r=FumaGeneSetEnrichment.test(p,List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("hit","2","1")))),FumaGeneSetEnrichment.Adjustment.BH,1,0,FumaGeneSetEnrichment.Duplicates.ERROR,100).get(0);assertEquals(List.of("G1","G2","ALIAS1"),r.genes());assertEquals(2,r.overlap());
    }
    @Test void namespacePriorityIsGlobalAndDoesNotMergeMixedIdTypes() {
        var p=FumaGeneSetEnrichment.prepare(annotation(),List.of("g1","ENSG2","3","g4"),null,Set.of("all"),false);assertEquals("symbol",p.queryNamespace());assertEquals(List.of("1","4"),p.selected());assertTrue(p.queryMapping().get(1).entrez().isEmpty());
    }
    @Test void customBackgroundSelectsItsOwnGlobalNamespace() {
        var p=FumaGeneSetEnrichment.prepare(annotation(),List.of("ENSG1","ENSG2","ENSG3"),List.of("g1","g2","3"),null,false);assertEquals("ensembl",p.queryNamespace());assertEquals("symbol",p.backgroundNamespace());assertEquals(List.of("1","2"),p.universe());assertEquals(p.universe(),p.selected());
    }
    @Test void mhcUsesFilteredAnchorsAndRetainsTouchingBoundaries() {
        var rows=List.of(new FumaGeneSetEnrichment.GeneRow("E1","1","MOG","pc","6",100,110),new FumaGeneSetEnrichment.GeneRow("E2","2","COL11A2","pc","6",200,210),new FumaGeneSetEnrichment.GeneRow("E3","3","IN","pc","6",150,160),new FumaGeneSetEnrichment.GeneRow("E4","4","LEFT","pc","6",90,100),new FumaGeneSetEnrichment.GeneRow("E5","5","OTHER","pc","7",150,160));
        var p=FumaGeneSetEnrichment.prepare(rows,List.of("1","2","3","4","5"),null,Set.of("pc"),true);assertEquals(List.of("2","4","5"),p.universe());assertEquals(2,p.mhcRemovedRows());assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.prepare(annotation(),List.of("1","2"),null,Set.of("all"),true));
    }
    @Test void tooFewMatchedGenesAndAmbiguousBackgroundFail() {
        assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.prepare(annotation(),List.of("G1","ENSG2"),null,Set.of("all"),false));assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.prepare(annotation(),List.of("1","2"),List.of("1","2"),Set.of("all"),false));
    }
    @Test void bkyPartialStageUsesFixedAlphaAndEstimatedNullFraction() {
        double[] p={.001,.1,.9};assertArrayEquals(new double[]{.0021,.105,.63},adjust(p,FumaGeneSetEnrichment.Adjustment.TWO_STAGE_BKY),1e-14);assertArrayEquals(new double[]{.002,.1,.6},adjust(p,FumaGeneSetEnrichment.Adjustment.TWO_STAGE_BH),1e-14);
    }
    @Test void bkyZeroAndAllRejectionBranchesDoNotScaleNullCountToZero() {
        assertArrayEquals(new double[]{.42,.84},adjust(new double[]{.2,.8},FumaGeneSetEnrichment.Adjustment.TWO_STAGE_BKY),1e-14);assertArrayEquals(new double[]{.0021,.0021},adjust(new double[]{.001,.002},FumaGeneSetEnrichment.Adjustment.TWO_STAGE_BKY),1e-14);
    }
    @Test void bkyFirstStageBoundaryUsesRankThresholdBeforeAdjustmentRounding() {
        assertArrayEquals(new double[]{1.0/30,.21,.63},adjust(new double[]{.05/1.05/3,.2,.9},FumaGeneSetEnrichment.Adjustment.TWO_STAGE_BKY),1e-14);
    }
    @Test void correctionsIncludeUnsortedTiesAndFamilyWiseMethods() {
        double[] p={.9,.01,.01};assertArrayEquals(new double[]{1,.03,.03},adjust(p,FumaGeneSetEnrichment.Adjustment.BONFERRONI),1e-14);assertArrayEquals(new double[]{.9,.03,.03},adjust(p,FumaGeneSetEnrichment.Adjustment.HOLM),1e-14);assertArrayEquals(new double[]{.9,.02,.02},adjust(p,FumaGeneSetEnrichment.Adjustment.SIMES_HOCHBERG),1e-14);assertArrayEquals(new double[]{.9,.02,.02},adjust(p,FumaGeneSetEnrichment.Adjustment.HOMMEL),1e-14);
    }
    @Test void logSpaceRetainsUnderflowAndStableSidak() {
        for (var method:FumaGeneSetEnrichment.Adjustment.values()) {double[] a=FumaGeneSetEnrichment.adjustLog(new double[]{-1000,-900,0},method,100);assertTrue(Double.isFinite(a[0]));assertEquals(0,Math.exp(a[0]));assertTrue(a[0]>=-1000);}
        assertEquals(-1000+Math.log(2),FumaGeneSetEnrichment.adjustLog(new double[]{-1000,0},FumaGeneSetEnrichment.Adjustment.SIDAK,100)[0]);
    }
    @Test void cutoffIsStrictAndZeroCutoffRetainsNothing() {
        var families=List.of(new FumaGeneSetEnrichment.Family("a",List.of(term("full","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20"))));var rows=FumaGeneSetEnrichment.test(prepared(),families,FumaGeneSetEnrichment.Adjustment.BH,1,0,FumaGeneSetEnrichment.Duplicates.ERROR,100);assertFalse(rows.get(0).retained());assertFalse(FumaGeneSetEnrichment.test(prepared(),families,FumaGeneSetEnrichment.Adjustment.BH,0,0,FumaGeneSetEnrichment.Duplicates.ERROR,100).get(0).retained());
    }
    @Test void invalidProbabilitiesResourcesAndWorkBudgetFailClearly() {
        assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.adjustLog(new double[]{Double.NaN},FumaGeneSetEnrichment.Adjustment.BH,100));assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.adjustLog(new double[]{0,0},FumaGeneSetEnrichment.Adjustment.HOMMEL,3));assertThrows(IllegalArgumentException.class,()->FumaGeneSetEnrichment.test(prepared(),List.of(new FumaGeneSetEnrichment.Family("a",List.of())),FumaGeneSetEnrichment.Adjustment.BH,.05,2,FumaGeneSetEnrichment.Duplicates.ERROR,100));assertThrows(IllegalArgumentException.class,()->new FumaGeneSetEnrichment.GeneRow("E","NA","G","pc","1",1,2));
    }
}
