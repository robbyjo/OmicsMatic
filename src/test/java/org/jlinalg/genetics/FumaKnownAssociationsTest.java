/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.FumaKnownAssociations.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FumaKnownAssociationsTest {
    private static final Runnable CHECK=()->{};
    private static FumaKnownAssociations.Test row(String family,String id,String gene,String trait,String context,Double p){return new FumaKnownAssociations.Test(family,id,gene,trait,context,p);}
    private static Family family(String id,String source,List<Member> members,Encoding encoding,Adjustment adjustment,Missing missing,ReportedZero zero,Duplicates duplicates){return new Family(id,source,members,encoding,adjustment,missing,zero,duplicates,"Declared synthetic source test/adjustment; no direction conversion");}
    private static Family family(String id,String source,List<Member> members){return family(id,source,members,Encoding.P_VALUE,Adjustment.BH,Missing.ERROR,ReportedZero.ERROR,Duplicates.ERROR);}
    private static Options options(Matching matching,Absence absence){return new Options(.05,matching,absence,Limits.defaults());}
    private static final class Data {
        List<Source> sources=new ArrayList<>(List.of(new Source("magma-v1",Role.BASELINE),new Source("bulk-v1",Role.BASELINE),new Source("sn-v1",Role.TARGET)));
        List<Family> families=new ArrayList<>(List.of(
            family("A","magma-v1",List.of(new Member("T1","magma",2),new Member("T2","magma",2))),
            family("B","bulk-v1",List.of(new Member("T1","bulk",1),new Member("T2","bulk",2))),
            family("C","sn-v1",List.of(new Member("T1","class1",2),new Member("T2","class2",2)))));
        List<FumaKnownAssociations.Test> rows=new ArrayList<>(List.of(
            row("A","a1","G1","T1","magma",.001),row("A","a2","G2","T1","magma",.04),row("A","a3","G3","T2","magma",.03),row("A","a4","G4","T2","magma",.9),
            row("B","b1","G2","T1","bulk",.001),row("B","b2","G1","T2","bulk",.002),row("B","b3","G3","T2","bulk",.5),
            row("C","c1","G1","T1","class1",.001),row("C","c2","G3","T2","class2",.002),row("C","c3","G4","T1","class1",.03),row("C","c4","G2","T2","class2",.8)));
        Options options=options(Matching.GENE_TRAIT,Absence.REQUIRE_BASELINE_TESTS);
        FumaKnownAssociations.Result run(){return FumaKnownAssociations.review(sources,families,rows,options,CHECK);}
    }
    @Test void completeFamiliesPrecedeKnownUnionAndEveryTargetStateRemainsVisible(){
        var result=new Data().run();assertEquals(11,result.tests().size());assertEquals(List.of(new Key("G1","T1"),new Key("G2","T1"),new Key("G1","T2")),result.known().stream().map(KnownUnion::key).toList());
        assertEquals(List.of(Decision.KNOWN,Decision.NOVEL_RELATIVE_TO_BASELINES,Decision.UNASSESSABLE_BASELINE,Decision.TARGET_NOT_SELECTED),result.targets().stream().map(TargetDecision::decision).toList());
        assertEquals(java.util.Arrays.asList(1,0,0,null),new ArrayList<>(result.targets().stream().map(TargetDecision::matchedKnownTests).toList()));assertEquals(List.of(new Link(8,1)),result.links());assertEquals(6,result.coverage().size());assertEquals(.004,result.tests().get(0).adjustedP(),1e-17);assertTrue(result.warnings().stream().anyMatch(s->s.contains("never relabel them novel")));
    }
    @Test void matchingAcrossTraitsChangesOnlyKeysAndEvidenceNotAdjustmentFamilies(){
        var d=new Data();var exact=d.run();d.options=options(Matching.GENE_ACROSS_TRAITS,Absence.REQUIRE_BASELINE_TESTS);var all=d.run();assertEquals(List.of(1,6),all.known().get(0).baselineRows());assertNull(all.known().get(0).key().trait());assertEquals(List.of(new Link(8,1),new Link(8,6)),all.links());
        for(int i=0;i<exact.tests().size();i++){assertEquals(exact.tests().get(i).adjustedP(),all.tests().get(i).adjustedP());assertEquals(exact.tests().get(i).selected(),all.tests().get(i).selected());}assertEquals(exact.families(),all.families());
    }
    @Test void explicitlyPartitioningTraitsCanChangeTheSelectedKnownUnion(){
        var sources=List.of(new Source("base",Role.BASELINE),new Source("target",Role.TARGET));var input=new ArrayList<FumaKnownAssociations.Test>();for(int i=0;i<3;i++)input.add(row("pool","a"+i,"g"+i,"a","c",.001*(i+1)));input.add(row("pool","b1","weak","b","c",.04));input.add(row("pool","b2","null","b","c",.9));input.add(row("target","t","weak","b","c",.001));
        var target=family("target","target",List.of(new Member("b","c",1)));var pooled=FumaKnownAssociations.review(sources,List.of(family("pool","base",List.of(new Member("a","c",3),new Member("b","c",2))),target),input,options(Matching.GENE_TRAIT,Absence.REQUIRE_BASELINE_TESTS),CHECK);
        var split=new ArrayList<FumaKnownAssociations.Test>();for(var r:input)split.add(row(r.family().equals("pool")?r.trait():r.family(),r.id(),r.gene(),r.trait(),r.context(),r.value()));var separate=FumaKnownAssociations.review(sources,List.of(family("a","base",List.of(new Member("a","c",3))),family("b","base",List.of(new Member("b","c",2))),target),split,options(Matching.GENE_TRAIT,Absence.REQUIRE_BASELINE_TESTS),CHECK);
        assertEquals(.05,pooled.tests().get(3).adjustedP());assertEquals(.08,separate.tests().get(3).adjustedP());assertEquals(Decision.KNOWN,pooled.targets().get(0).decision());assertEquals(Decision.NOVEL_RELATIVE_TO_BASELINES,separate.targets().get(0).decision());
    }
    @Test void incompleteOrUndeclaredMemberPopulationsAreRefused(){
        var d=new Data();d.rows.remove(3);assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("Incomplete declared family"));d=new Data();var prior=d.rows.get(0);d.rows.set(0,row("A",prior.id(),prior.gene(),"other",prior.context(),prior.value()));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("Undeclared trait/context"));
    }
    @Test void missingValueExclusionPreservesDeclaredRowsButChangesAdjustmentSize(){
        var d=new Data();var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),a.members(),a.encoding(),a.adjustment(),Missing.EXCLUDE,a.reportedZero(),a.duplicates()));d.rows.set(2,row("A","a3","G3","T2","magma",null));var result=d.run();assertEquals(4,result.families().get(0).actualRows());assertEquals(3,result.families().get(0).adjustmentSize());assertEquals(1,result.families().get(0).missingRows());assertNull(result.tests().get(2).adjustedP());assertEquals(Decision.UNASSESSABLE_BASELINE,result.targets().get(1).decision());assertTrue(result.warnings().stream().anyMatch(s->s.contains("excluded from the correction denominator")));
    }
    @Test void missingValuesRequireExplicitAdmissionAndNeverBecomePValues(){
        var d=new Data();d.rows.set(0,row("A","a1","G1","T1","magma",null));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("Missing value"));
        for(Double p:List.of(Double.NaN,-.1,1.1,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)){var bad=new Data();bad.rows.set(0,row("A","a1","G1","T1","magma",p));assertThrows(IllegalArgumentException.class,bad::run);}
    }
    @Test void oneKnownBaselineMatchRemainsSufficientWhenAnotherBaselineValueIsMissing(){
        var d=new Data();var b=d.families.get(1);d.families.set(1,family(b.id(),b.source(),b.members(),b.encoding(),b.adjustment(),Missing.EXCLUDE,b.reportedZero(),b.duplicates()));d.rows.set(4,row("B","b1","G1","T1","bulk",null));d.rows.set(8,row("C","c2","G1","T1","class2",.002));d.families.set(2,family("C","sn-v1",List.of(new Member("T1","class1",2),new Member("T1","class2",1),new Member("T2","class2",1))));
        var result=d.run();assertEquals(Decision.KNOWN,result.targets().get(1).decision());assertEquals(1,result.coverage().get(1).missingRows());
    }
    @Test void selectedReferenceAbsenceIsExplicitAndDoesNotOverrideMatchingMissingValues(){
        var d=new Data();d.options=options(Matching.GENE_TRAIT,Absence.SELECTED_REFERENCE);assertEquals(Decision.NOVEL_RELATIVE_TO_BASELINES,d.run().targets().get(2).decision());var b=d.families.get(1);d.families.set(1,family(b.id(),b.source(),b.members(),b.encoding(),b.adjustment(),Missing.EXCLUDE,b.reportedZero(),b.duplicates()));d.rows.set(6,row("B","b3","G3","T2","bulk",null));assertEquals(Decision.UNASSESSABLE_BASELINE,d.run().targets().get(1).decision());
    }
    @Test void allMissingOrEmptyBaselineSourcesCannotEstablishAbsence(){
        var sources=List.of(new Source("B",Role.BASELINE),new Source("T",Role.TARGET));
        for(boolean empty:List.of(false,true)){var baseline=family("B","B",List.of(new Member("t","b",empty?0:1)),Encoding.P_VALUE,Adjustment.BH,Missing.EXCLUDE,ReportedZero.ERROR,Duplicates.ERROR);var rows=new ArrayList<FumaKnownAssociations.Test>();if(!empty)rows.add(row("B","b","g","t","b",null));rows.add(row("T","t","g","t","c",.01));var result=FumaKnownAssociations.review(sources,List.of(baseline,family("T","T",List.of(new Member("t","c",1)))),rows,options(Matching.GENE_TRAIT,Absence.SELECTED_REFERENCE),CHECK);assertEquals(Decision.UNASSESSABLE_BASELINE,result.targets().get(0).decision());assertEquals(0,result.families().get(0).adjustmentSize());assertTrue(result.warnings().stream().anyMatch(s->s.contains("EMPTY AVAILABLE CORRECTION FAMILY")));}
    }
    @Test void reportedZeroNeedsPermissionAndNeverInventsAFiniteLog(){
        var d=new Data();d.rows.set(0,row("A","a1","G1","T1","magma",0.0));assertThrows(IllegalArgumentException.class,d::run);var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),a.members(),a.encoding(),a.adjustment(),a.missing(),ReportedZero.ALLOW_REPORTED,a.duplicates()));var result=d.run();assertEquals(0.0,result.tests().get(0).adjustedP());assertEquals(Double.NEGATIVE_INFINITY,result.tests().get(0).adjustedLogP());assertEquals(1,result.families().get(0).reportedZeros());assertTrue(result.warnings().stream().anyMatch(s->s.contains("lost log values are never reconstructed")));
    }
    @Test void finiteNaturalLogsSurviveProbabilityUnderflowAndUseLogSelection(){
        var d=new Data();var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),a.members(),Encoding.NATURAL_LOG,a.adjustment(),a.missing(),a.reportedZero(),a.duplicates()));for(int i=0;i<4;i++){var r=d.rows.get(i);d.rows.set(i,row(r.family(),r.id(),r.gene(),r.trait(),r.context(),i==0?-1000.0:Math.log(r.value())));}var result=d.run();assertEquals(0.0,result.tests().get(0).adjustedP());assertTrue(Double.isFinite(result.tests().get(0).adjustedLogP()));assertEquals(-1000+Math.log(4),result.tests().get(0).adjustedLogP());assertEquals(1,result.families().get(0).adjustedProbabilityUnderflows());
    }
    @Test void inclusiveProbabilityCutoffDistinguishesAdjacentBinary64Values(){
        var d=new Data();var c=d.families.get(2);d.families.set(2,family(c.id(),c.source(),c.members(),c.encoding(),Adjustment.SUPPLIED_ADJUSTED,c.missing(),c.reportedZero(),c.duplicates()));double[] p={Math.nextDown(.05),.05,Math.nextUp(.05),.9};for(int i=0;i<4;i++){var r=d.rows.get(7+i);d.rows.set(7+i,row(r.family(),r.id(),r.gene(),r.trait(),r.context(),p[i]));}var result=d.run();assertTrue(result.tests().get(7).selected());assertTrue(result.tests().get(8).selected());assertFalse(result.tests().get(9).selected());assertEquals(Math.nextUp(.05),result.tests().get(9).adjustedP());assertTrue(result.warnings().stream().anyMatch(s->s.contains("CUTOFF ROUNDING REVIEW")));
    }
    @Test void suppliedAdjustedValuesArePreservedRatherThanAdjustedAgain(){
        var d=new Data();var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),a.members(),a.encoding(),Adjustment.SUPPLIED_ADJUSTED,a.missing(),a.reportedZero(),a.duplicates()));var result=d.run();for(int i=0;i<4;i++)assertEquals(d.rows.get(i).value(),result.tests().get(i).adjustedP());assertTrue(result.tests().get(1).selected());assertEquals(4,result.families().get(0).availableRows());assertNull(result.families().get(0).adjustmentSize());assertTrue(result.warnings().stream().anyMatch(s->s.contains("does not certify its original computation")));
    }
    @Test void repeatedHypothesesNeedPermissionAndKeepEveryUnionAndMatchRow(){
        var d=new Data();d.rows.set(1,row("A","a2","G1","T1","magma",.001));assertThrows(IllegalArgumentException.class,d::run);var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),a.members(),a.encoding(),a.adjustment(),a.missing(),a.reportedZero(),Duplicates.RETAIN));var r=d.run();assertEquals(1,r.tests().get(1).duplicateOfInputRow());assertEquals(List.of(1,2),r.known().get(0).baselineRows());assertEquals(List.of(new Link(8,1),new Link(8,2),new Link(9,3)),r.links());assertEquals(2,r.targets().get(0).matchedKnownTests());assertEquals(Decision.KNOWN,r.targets().get(1).decision());
    }
    @Test void duplicateTestIdsRemainInvalidEvenUnderRetainPolicy(){
        var d=new Data();var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),a.members(),a.encoding(),a.adjustment(),a.missing(),a.reportedZero(),Duplicates.RETAIN));d.rows.set(1,row("A","a1","G2","T1","magma",.04));assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("Duplicate test ID"));
    }
    @Test void opaqueGeneVersionAndTraitStringsAreNotNormalized(){
        var d=new Data();d.rows.set(7,row("C","c1","G1.1","T1","class1",.001));assertEquals(Decision.UNASSESSABLE_BASELINE,d.run().targets().get(0).decision());d=new Data();d.rows.set(0,row("A","a1","G1","T1","magma",.001));d.rows.set(7,row("C","c1","G1","t1","class1",.001));assertThrows(IllegalArgumentException.class,d::run);
    }
    @Test void targetMissingAndUnselectedStatesDoNotClaimZeroKnownEvidence(){
        var d=new Data();var c=d.families.get(2);d.families.set(2,family(c.id(),c.source(),c.members(),c.encoding(),c.adjustment(),Missing.EXCLUDE,c.reportedZero(),c.duplicates()));d.rows.set(7,row("C","c1","G1","T1","class1",null));var r=d.run();assertEquals(Decision.MISSING_TARGET_VALUE,r.targets().get(0).decision());assertNull(r.targets().get(0).matchedKnownTests());assertNull(r.targets().get(3).matchedKnownTests());assertTrue(r.links().isEmpty());
    }
    @Test void everySelectedTargetHasEvidenceForEachBaselineInSourceOrder(){
        var result=new Data().run();assertEquals(List.of("magma-v1","bulk-v1","magma-v1","bulk-v1","magma-v1","bulk-v1"),result.coverage().stream().map(BaselineCoverage::source).toList());assertEquals(1,result.coverage().get(0).matchingRows());assertEquals(1,result.coverage().get(0).availableRows());assertEquals(0,result.coverage().get(1).matchingRows());assertEquals(3,result.coverage().get(1).sourceAvailableRows());
    }
    @Test void invalidSourceFamilyAndDomainDeclarationsAreRefused(){
        var d=new Data();d.sources.add(d.sources.get(0));assertThrows(IllegalArgumentException.class,d::run);d=new Data();d.families.add(d.families.get(0));assertThrows(IllegalArgumentException.class,d::run);d=new Data();var a=d.families.get(0);d.families.set(0,family(a.id(),a.source(),List.of(new Member("T1","magma",2),new Member("T1","magma",2))));assertThrows(IllegalArgumentException.class,d::run);d=new Data();d.sources.add(new Source("no-family",Role.BASELINE));assertThrows(IllegalArgumentException.class,d::run);assertThrows(IllegalArgumentException.class,()->new Source("bad\n",Role.BASELINE));assertThrows(IllegalArgumentException.class,()->new Member("t","c",-1));
    }
    @Test void capacitiesRefuseWholeResultsWithoutSilentEvidenceTruncation(){
        for(Limits limits:List.of(new Limits(5,100,1000),new Limits(100,1,1000),new Limits(100,100,1))){var d=new Data();d.options=new Options(.05,Matching.GENE_TRAIT,Absence.REQUIRE_BASELINE_TESTS,limits);assertTrue(assertThrows(IllegalArgumentException.class,d::run).getMessage().contains("budget exceeded"));}
    }
    @Test void cooperativeCancellationPropagatesItsOriginalCause(){
        var d=new Data();var marker=new IllegalStateException("cancel");var count=new AtomicInteger();assertSame(marker,assertThrows(IllegalStateException.class,()->FumaKnownAssociations.review(d.sources,d.families,d.rows,d.options,()->{if(count.incrementAndGet()==4)throw marker;})));
    }
    @Test void nestedFamilyAndResultContainersAreImmutableCopies(){
        var members=new ArrayList<>(List.of(new Member("t","c",0)));var f=family("f","s",members);members.clear();assertEquals(1,f.members().size());assertThrows(UnsupportedOperationException.class,()->f.members().clear());var r=new Data().run();assertThrows(UnsupportedOperationException.class,()->r.tests().clear());assertThrows(UnsupportedOperationException.class,()->r.known().get(0).baselineRows().clear());assertThrows(UnsupportedOperationException.class,()->r.targets().clear());
    }
    @Test void completeOutputOrderKeepsInputProvenanceRatherThanSortedPOrder(){
        var d=new Data();var r=d.run();for(int i=0;i<d.rows.size();i++){assertEquals(i+1,r.tests().get(i).inputRow());assertEquals(d.rows.get(i),r.tests().get(i).test());}assertEquals(List.of("A","B","C"),r.families().stream().map(FamilyAudit::family).toList());assertEquals(List.of(8,9,10,11),r.targets().stream().map(TargetDecision::inputRow).toList());
    }
    @Test void allMissingTargetsProduceNoEligibleComparisonWithoutInventingNulls(){
        var d=new Data();var c=d.families.get(2);d.families.set(2,family(c.id(),c.source(),c.members(),c.encoding(),c.adjustment(),Missing.EXCLUDE,c.reportedZero(),c.duplicates()));for(int i=7;i<11;i++){var r=d.rows.get(i);d.rows.set(i,row(r.family(),r.id(),r.gene(),r.trait(),r.context(),null));}var r=d.run();assertTrue(r.targets().stream().allMatch(t->t.decision()==Decision.MISSING_TARGET_VALUE));assertEquals(3,r.known().size());assertTrue(r.coverage().isEmpty());assertTrue(r.warnings().stream().anyMatch(s->s.contains("NO SELECTED TARGET TESTS")));
    }
    @Test void rawAndLogCorrectionsMatchAll785CompleteIndependentReferenceRows()throws Exception {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var stream=getClass().getResourceAsStream("/fuma-known-associations/reference.tsv")){assertNotNull(stream);try(var input=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){input.readLine();for(String line;(line=input.readLine())!=null;){var fields=line.split("\t",-1);groups.computeIfAbsent(fields[0],ignored->new ArrayList<>()).add(fields);}}}
        assertEquals(50,groups.size());int count=0;
        for(var expected:groups.values()){
            String method=expected.get(0)[1];Adjustment adjustment=switch(method){case "BH"->Adjustment.BH;case "BY"->Adjustment.BY;case "holm"->Adjustment.HOLM;case "bonferroni"->Adjustment.BONFERRONI;default->Adjustment.SUPPLIED_ADJUSTED;};Encoding encoding=Encoding.valueOf(expected.get(0)[2]);var rows=new ArrayList<FumaKnownAssociations.Test>();
            for(int i=0;i<expected.size();i++)rows.add(row("B","b"+i,"g"+i,"t","base",number(expected.get(i)[4])));rows.add(row("T","t","g0","t","target",.9));
            var result=FumaKnownAssociations.review(List.of(new Source("base",Role.BASELINE),new Source("target",Role.TARGET)),List.of(family("B","base",List.of(new Member("t","base",expected.size())),encoding,adjustment,Missing.EXCLUDE,ReportedZero.ALLOW_REPORTED,Duplicates.ERROR),family("T","target",List.of(new Member("t","target",1)))),rows,options(Matching.GENE_TRAIT,Absence.REQUIRE_BASELINE_TESTS),CHECK);
            for(int i=0;i<expected.size();i++){var actual=result.tests().get(i);close(number(expected.get(i)[5]),actual.adjustedP(),expected.get(i)[0]+" P "+i);close(number(expected.get(i)[6]),actual.adjustedLogP(),expected.get(i)[0]+" logP "+i);assertEquals(Boolean.parseBoolean(expected.get(i)[7]),actual.selected(),expected.get(i)[0]+" selection "+i);count++;}
        }
        assertEquals(785,count);
    }
    private static Double number(String text){return text.equals("NA")?null:text.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.valueOf(text);}
    private static void close(Double expected,Double actual,String context){if(expected==null){assertNull(actual,context);return;}assertNotNull(actual,context);if(!Double.isFinite(expected)||expected==0){assertTrue(actual.doubleValue()==expected.doubleValue(),context+": "+actual+" vs "+expected);return;}assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)/Math.abs(expected)<=2e-14,context+": "+actual+" vs "+expected);}
}
