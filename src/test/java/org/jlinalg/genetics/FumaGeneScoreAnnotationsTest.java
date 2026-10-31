package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.jlinalg.genetics.FumaGeneScoreAnnotations.*;

class FumaGeneScoreAnnotationsTest {
    private static Resource resource(List<List<String>> rows){return new Resource("synthetic-GRCh37-scores",List.of("ensg","symbol","chr","pos","pLI","mis_z"),rows);}
    private static List<String> row(String id,String p,String z){return List.of(id,"SYMBOL","1","100",p,z);}
    private static Result join(List<String> ids,Resource resource){return FumaGeneScoreAnnotations.join(ids,resource,Duplicates.FIRST,Missing.RETAIN,1000);}
    @Test void joinsByExactIdInTargetOrder(){var r=join(List.of("G2","G1"),resource(List.of(row("G1",".1","-1"),row("G2",".2","2"))));assertEquals(List.of(".2","2"),r.joined().get(0).values());assertEquals(3,r.joined().get(0).sourceLine());assertEquals(List.of("score.pLI","score.mis_z"),r.outputColumns());}
    @Test void firstDuplicateIsAnExplicitOrderDependentPolicy(){var data=resource(List.of(row("G1",".9","9"),row("G1",".1","1")));var r=join(List.of("G1"),data);assertEquals(".9",r.joined().get(0).values().get(0));assertEquals("duplicate-ignored-first",r.resourceRows().get(1).status());assertEquals(2,r.resourceRows().get(1).firstSourceLine());assertEquals(2,r.resourceRows().get(1).occurrences());}
    @Test void errorRejectsDuplicatesEvenWhenUnused(){assertThrows(IllegalArgumentException.class,()->FumaGeneScoreAnnotations.join(List.of("G1"),resource(List.of(row("UNUSED","1","1"),row("UNUSED","2","2"))),Duplicates.ERROR,Missing.RETAIN,100));}
    @Test void missingRetainUsesNaNotZero(){var r=join(List.of("G2"),resource(List.of(row("G1","0","0"))));assertEquals(List.of("NA","NA"),r.joined().get(0).values());assertEquals(0,r.joined().get(0).sourceLine());assertEquals("unmatched-filled-NA",r.joined().get(0).status());}
    @Test void missingErrorDoesNotInventAnAnnotation(){assertThrows(IllegalArgumentException.class,()->FumaGeneScoreAnnotations.join(List.of("G2"),resource(List.of(row("G1","0","0"))),Duplicates.FIRST,Missing.ERROR,100));}
    @Test void highPrecisionAndSubnormalLexemesAreNeverParsed(){var r=join(List.of("G1"),resource(List.of(row("G1","0.123456789012345678901234567890","1e-320"))));assertEquals(List.of("0.123456789012345678901234567890","1e-320"),r.joined().get(0).values());}
    @Test void textAndEmptyCellsRemainRaw(){var r=join(List.of("G1"),resource(List.of(row("G1","category-A",""))));assertEquals(List.of("category-A",""),r.joined().get(0).values());assertEquals("matched-with-missing-values",r.joined().get(0).status());}
    @Test void sourceNaCellsAreDistinctFromAnUnmatchedGene(){var r=join(List.of("G1"),resource(List.of(row("G1","NA","NA"))));assertEquals(2,r.joined().get(0).sourceLine());assertEquals("matched-with-missing-values",r.joined().get(0).status());}
    @Test void oneScoreKeepsItsDeclaredHeader(){var r=join(List.of("G1"),new Resource("one",List.of("ensg","symbol","chr","pos","pLI"),List.of(List.of("G1","S","1","1",".1"))));assertEquals(List.of("score.pLI"),r.outputColumns());assertEquals(List.of(".1"),r.joined().get(0).values());}
    @Test void keyCanBeElsewhereAndPayloadStillStartsAtColumnFive(){var r=join(List.of("G1"),new Resource("key-last",List.of("symbol","chr","pos","meta","ensg","pLI"),List.of(List.of("S","1","100","m","G1",".1"))));assertEquals(List.of("score.ensg","score.pLI"),r.outputColumns());assertEquals(List.of("G1",".1"),r.joined().get(0).values());}
    @Test void blankNaAndWhitespaceKeysAreUnmatchableAndAudited(){var r=join(List.of("G1"),resource(List.of(row("NA","1","1"),row("","2","2"),row(" G1 ","3","3"),row("G1","4","4"))));assertEquals("4",r.joined().get(0).values().get(0));assertEquals(3,r.resourceRows().stream().filter(v->v.status().equals("unmatchable-ID-ignored")).count());}
    @Test void caseAndEnsemblVersionsAreNotNormalized(){var r=join(List.of("G1","G2"),resource(List.of(row("g1","1","1"),row("G2.1","2","2"))));assertTrue(r.joined().stream().allMatch(v->v.sourceLine()==0));}
    @Test void repeatedTargetsArePreservedWithoutDuplicateResourceRows(){var r=join(List.of("G1","G1"),resource(List.of(row("G1","1","2"))));assertEquals(2,r.joined().size());assertEquals(2,r.joined().get(1).targetRow());assertEquals(r.joined().get(0).values(),r.joined().get(1).values());}
    @Test void unusedRowsRemainInTheResourceLedger(){var r=join(List.of("G1"),resource(List.of(row("G1","1","1"),row("G2","2","2"))));assertEquals("unused-first",r.resourceRows().get(1).status());}
    @Test void emptyResourceCanExplicitlyRetainNaTargets(){var r=join(List.of("G1"),resource(List.of()));assertEquals(List.of("NA","NA"),r.joined().get(0).values());}
    @Test void emptyTargetQueryKeepsHeadersAndResourceAudit(){var r=FumaGeneScoreAnnotations.join(List.of(),resource(List.of(row("G1","1","1"))),Duplicates.ERROR,Missing.ERROR,0);assertTrue(r.joined().isEmpty());assertEquals(2,r.outputColumns().size());assertEquals("unused-first",r.resourceRows().get(0).status());}
    @Test void schemaRequiresAtLeastFiveDistinctColumnsAndExactKey(){assertThrows(IllegalArgumentException.class,()->new Resource("r",List.of("ensg","symbol","chr","pos"),List.of()));assertThrows(IllegalArgumentException.class,()->new Resource("r",List.of("gene","symbol","chr","pos","pLI"),List.of()));assertThrows(IllegalArgumentException.class,()->new Resource("r",List.of("ensg","symbol","chr","pos","pLI","pLI"),List.of()));}
    @Test void cellBudgetIsCheckedBeforeAnyTargetPayloadAllocation(){assertThrows(IllegalArgumentException.class,()->FumaGeneScoreAnnotations.join(List.of("G1","G2"),resource(List.of()),Duplicates.FIRST,Missing.RETAIN,3));}
    @Test void resourcesAndReturnedValuesAreImmutable(){var row=new ArrayList<>(row("G1",".1","1"));var rows=new ArrayList<List<String>>();rows.add(row);var data=resource(rows);row.set(4,"changed");assertEquals(".1",join(List.of("G1"),data).joined().get(0).values().get(0));assertThrows(UnsupportedOperationException.class,()->data.rows().get(0).set(4,"bad"));}
    @Test void controlCharactersAndMissingTargetIdsFailExplicitly(){assertThrows(IllegalArgumentException.class,()->resource(List.of(row("G1","a\tb","1"))));assertThrows(IllegalArgumentException.class,()->join(List.of("NA"),resource(List.of())));}
}
