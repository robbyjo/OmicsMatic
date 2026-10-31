/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.List;
import org.jlinalg.genetics.EagleGenotypes.Audit;
import org.jlinalg.genetics.EagleGenotypes.Call;
import org.jlinalg.genetics.EagleGenotypes.Disposition;
import org.jlinalg.genetics.EagleGenotypes.Missing;
import org.jlinalg.genetics.EagleGenotypes.Mode;
import org.jlinalg.genetics.EagleGenotypes.Options;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EagleGenotypesTest {
    @Test void declaredSourceAlleleRepresentationKeepsOriginalAndActualIdentityDigestsDistinct() {
        var options=new EagleGenotypes.Options(EagleGenotypes.Mode.REFERENCE,false,EagleGenotypes.Missing.KEEP,false);
        var audit=new EagleGenotypes.Audit(java.util.List.of("A"),java.util.List.of("A"),options,1,1);
        var before=java.util.List.of(EagleGenotypes.parse("0/0",0));var after=java.util.List.of(EagleGenotypes.parse("0|0",1));
        audit.acceptDeclaredRepresentation("22\t100\tv\tA\t.",before,"22\t100\tv\tG\tC","22\t100\tv\tG\tC",after,EagleGenotypes.Disposition.PHASED);
        var result=audit.finish(1);assertEquals(1,result.fullyObservedInputCalls());assertNotEquals(result.inputSha256(),result.outputSha256());
    }
    @Test void ordinaryAuditNeverImplicitlyAcceptsSourceAlleleExpansion() {
        var options=new EagleGenotypes.Options(EagleGenotypes.Mode.REFERENCE,false,EagleGenotypes.Missing.KEEP,false);
        var audit=new EagleGenotypes.Audit(java.util.List.of("A"),java.util.List.of("A"),options,1,1);
        assertThrows(IllegalArgumentException.class,()->audit.accept("A/.",java.util.List.of(EagleGenotypes.parse("0/0",0)),"G/C",java.util.List.of(EagleGenotypes.parse("0|0",1)),EagleGenotypes.Disposition.PHASED));
        assertThrows(IllegalStateException.class,()->audit.finish(0));
    }
    @Test void actualOutputMustEqualTheExplicitlyDeclaredRepresentation() {
        var options=new EagleGenotypes.Options(EagleGenotypes.Mode.REFERENCE,false,EagleGenotypes.Missing.KEEP,false);
        var audit=new EagleGenotypes.Audit(java.util.List.of("A"),java.util.List.of("A"),options,1,1);
        assertThrows(IllegalArgumentException.class,()->audit.acceptDeclaredRepresentation("A/.",java.util.List.of(EagleGenotypes.parse("0/0",0)),"G/C","T/C",java.util.List.of(EagleGenotypes.parse("0|0",1)),EagleGenotypes.Disposition.PHASED));
        assertThrows(IllegalStateException.class,()->audit.finish(0));
    }
    private static List<Call> calls(String... values) { return java.util.Arrays.stream(values).map(v -> EagleGenotypes.parse(v, 1)).toList(); }
    private static Options options(Mode mode, boolean x, Missing missing, boolean keep) { return new Options(mode, x, missing, keep); }
    private static List<String> samples(int n) { return java.util.stream.IntStream.range(0, n).mapToObj(i -> "S" + i).toList(); }
    private static Audit audit(Options options, int n) { return new Audit(samples(n), samples(n), options, 100, 1000); }
    private static void row(Audit audit, String id, String[] input, String... output) { audit.accept(id, calls(input), id, calls(output), Disposition.PHASED); }
    private static Options cohort(Missing missing) { return options(Mode.COHORT, false, missing, false); }
    private static Options reference(Missing missing) { return options(Mode.REFERENCE, false, missing, false); }

    @Test void originalGtSeparatorsPloidyAndPartialMissingnessRemainDistinct() {
        var a = EagleGenotypes.parse("0/1", 1); assertEquals("0/1", a.raw()); assertFalse(a.phased()); assertEquals(List.of(0, 1), a.alleles());
        assertTrue(EagleGenotypes.parse("1|0", 1).phased()); assertEquals(1, EagleGenotypes.parse(".", 1).ploidy());
        assertEquals(2, EagleGenotypes.parse("./.", 1).ploidy()); assertTrue(EagleGenotypes.parse("0|.", 1).partiallyMissing());
        assertFalse(EagleGenotypes.parse(".|.", 1).partiallyMissing()); assertEquals(1, EagleGenotypes.parse(".|1", 1).observedAlleles());
    }
    @Test void malformedPolyploidMixedAndOutOfRangeCallsFail() {
        for (String s : new String[]{"", "0/", "|1", "0//1", "0|1/0", "0/1/1", "-1/0", "2/0", "2147483648", "0 1"})
            assertThrows(IllegalArgumentException.class, () -> EagleGenotypes.parse(s, 1), s);
    }
    @Test void rawIndexLexemesAndAlleleBoundsAreNotSilentlyAltered() {
        var call = EagleGenotypes.parse("00/02", 2); assertEquals("00/02", call.raw()); assertEquals(List.of(0, 2), call.alleles());
        assertThrows(UnsupportedOperationException.class, () -> call.alleles().set(0, 1));
        assertThrows(IllegalArgumentException.class, () -> EagleGenotypes.parse("2/0", 1));
    }
    @Test void cohortCodesMatchOriginalWholeCallMissingEncoding() {
        var r = EagleGenotypes.encodeTargets(calls("0/0", "0/1", "1/1", "0/.", "./."), cohort(Missing.IMPUTE));
        assertEquals(List.of(0, 1, 2, 3, 3), r.codes()); assertEquals(2, r.framePloidy());
        assertEquals(1, r.fullyMissingCalls()); assertEquals(1, r.partiallyMissingCalls()); assertEquals(1, r.observedAllelesIgnoredByInference());
    }
    @Test void referenceCodesUseNineForWholeCallMissingness() {
        assertEquals(List.of(0, 1, 2, 9, 9), EagleGenotypes.encodeTargets(calls("0|0", "1/0", "1|1", ".|1", "./."), reference(Missing.IMPUTE)).codes());
    }
    @Test void mixedXHaploidObservedCallsAreDoubledOnlyInternally() {
        var in = calls("0", "1", "0/1", "."); var r = EagleGenotypes.encodeTargets(in, options(Mode.COHORT, true, Missing.IMPUTE, true));
        assertEquals(List.of(0, 2, 1, 3), r.codes()); assertEquals(1, in.get(1).ploidy()); assertEquals("1", in.get(1).raw());
    }
    @Test void allHaploidFrameIsReferenceXOnly() {
        var in = calls("0", "1", ".");
        assertEquals(List.of(0, 2, 9), EagleGenotypes.encodeTargets(in, options(Mode.REFERENCE, true, Missing.IMPUTE, false)).codes());
        assertThrows(IllegalArgumentException.class, () -> EagleGenotypes.encodeTargets(in, options(Mode.COHORT, true, Missing.IMPUTE, false)));
        assertThrows(IllegalArgumentException.class, () -> EagleGenotypes.encodeTargets(in, reference(Missing.IMPUTE)));
    }
    @Test void observedAutosomalHaploidFailsButBareMissingCanOccupyDiploidFrame() {
        assertThrows(IllegalArgumentException.class, () -> EagleGenotypes.encodeTargets(calls("1", "0/1"), cohort(Missing.KEEP)));
        assertEquals(List.of(3, 1), EagleGenotypes.encodeTargets(calls(".", "0/1"), cohort(Missing.KEEP)).codes());
    }
    @Test void selectedTargetsCannotCollapseMultipleAltAlleles() {
        assertThrows(IllegalArgumentException.class, () -> EagleGenotypes.encodeTargets(List.of(EagleGenotypes.parse("2/0", 2)), reference(Missing.IMPUTE)));
    }
    @Test void fullyObservedDiploidsRetainDosageAndReceiveExplicitPhase() {
        var a = audit(cohort(Missing.IMPUTE), 3); row(a, "v1", new String[]{"0/0", "0/1", "1/1"}, "0|0", "1|0", "1|1");
        var r = a.finish(1); assertEquals(3, r.fullyObservedInputCalls()); assertEquals(3, r.calls()); assertEquals(0, r.outputMissingCalls());
    }
    @Test void suppliedTargetPhaseIsAllowedToChangeUnderOriginalUnconstrainedMode() {
        var a = audit(reference(Missing.IMPUTE), 1); row(a, "v", new String[]{"0|1"}, "1|0"); assertEquals(1, a.finish(1).calls());
    }
    @Test void changedObservedDosageFailsAndCannotBeHiddenByResuming() {
        var a = audit(cohort(Missing.IMPUTE), 1);
        assertThrows(IllegalArgumentException.class, () -> row(a, "v", new String[]{"0/1"}, "1|1"));
        assertThrows(IllegalStateException.class, () -> row(a, "v", new String[]{"0/1"}, "0|1"));
        assertThrows(IllegalStateException.class, () -> a.finish(0));
    }
    @Test void observedOutputMustHaveRequiredPloidyAndPhase() {
        for (String out : new String[]{"0/1", "0", "./.", "0|."}) {
            var a = audit(cohort(Missing.IMPUTE), 1);
            assertThrows(IllegalArgumentException.class, () -> row(a, "v", new String[]{"0/1"}, out), out);
        }
    }
    @Test void partialCallsAreEntirelyUnconstrainedDuringImputationWithExplicitAccounting() {
        var a = audit(cohort(Missing.IMPUTE), 1); row(a, "v", new String[]{"0/."}, "1|1");
        var r = a.finish(1); assertEquals(1, r.partiallyMissingInputCalls()); assertEquals(1, r.inputAllelesIgnoredByInference()); assertEquals(0, r.fullyObservedInputCalls());
    }
    @Test void imputationCannotSilentlyLeaveDiploidMissing() {
        var a = audit(reference(Missing.IMPUTE), 1);
        assertThrows(IllegalArgumentException.class, () -> row(a, "v", new String[]{"./."}, "./."));
    }
    @Test void cohortKeepMissingRetainsOriginalPartialAndMissingSeparators() {
        var a = audit(cohort(Missing.KEEP), 4); row(a, "v", new String[]{"0/.", ".|1", ".|.", "."}, "0/.", ".|1", ".|.", ".");
        var r = a.finish(1); assertEquals(4, r.outputMissingCalls()); assertEquals(2, r.inputAllelesIgnoredByInference()); assertEquals(0, r.inputObservedAllelesCleared());
    }
    @Test void referenceKeepMissingClearsPartialDiploidObservedAllelesAsOriginalDoes() {
        var a = audit(reference(Missing.KEEP), 2); row(a, "v", new String[]{"0|.", ".|."}, "./.", "./.");
        var r = a.finish(1); assertEquals(1, r.inputObservedAllelesCleared()); assertEquals(2, r.outputMissingCalls());
    }
    @Test void referenceAndCohortKeepMissingAreNotInterchangeable() {
        var ref = audit(reference(Missing.KEEP), 1); assertThrows(IllegalArgumentException.class, () -> row(ref, "v", new String[]{"0/."}, "0/."));
        var cohort = audit(cohort(Missing.KEEP), 1); assertThrows(IllegalArgumentException.class, () -> row(cohort, "v", new String[]{"0/."}, "./."));
    }
    @Test void bareAutosomalMissingCanExpandInReferenceOutputWithoutInventingInputPloidy() {
        var a = audit(reference(Missing.KEEP), 2); row(a, "v", new String[]{".", "0/1"}, "./.", "0|1");
        assertEquals(1, a.finish(1).sourcePloidyConversions());
    }
    @Test void xMissingPloidyDefaultsToDiploidBeforeAnyObservedHaploid() {
        var a = audit(options(Mode.REFERENCE, true, Missing.IMPUTE, false), 2);
        row(a, "v", new String[]{".", "0/1"}, "0|1", "1|0"); assertEquals(1, a.finish(1).sourcePloidyConversions());
    }
    @Test void keepMissingPloidyXPreservesExplicitSingleDotAsHaploid() {
        var a = audit(options(Mode.REFERENCE, true, Missing.IMPUTE, true), 2);
        row(a, "v", new String[]{".", "0/1"}, "1", "1|0"); assertEquals(0, a.finish(1).sourcePloidyConversions());
    }
    @Test void sourceXHistoryChangesLaterMissingPloidyWithoutKeepFlag() {
        var a = audit(options(Mode.REFERENCE, true, Missing.IMPUTE, false), 2);
        row(a, "v1", new String[]{"1", "0/1"}, "1", "0|1"); row(a, "v2", new String[]{".", "0/1"}, "0", "1|0");
        assertEquals(0, a.finish(2).sourcePloidyConversions());
    }
    @Test void allHaploidReferenceRowsRemainUnchangedEvenWhenImputationWasRequested() {
        var a = audit(options(Mode.REFERENCE, true, Missing.IMPUTE, true), 2);
        row(a, "v", new String[]{"1", "."}, "1", "."); var r = a.finish(1);
        assertEquals(2, r.allHaploidReferenceBypassCalls()); assertEquals(1, r.missingCallsBypassedInAllHaploidReferenceRows());
    }
    @Test void allHaploidBypassDoesNotUpdateSourcePloidyHistory() {
        var a = audit(options(Mode.REFERENCE, true, Missing.IMPUTE, false), 2);
        row(a, "v1", new String[]{"1", "."}, "1", "."); row(a, "v2", new String[]{".", "0/1"}, "0|0", "0|1");
        assertEquals(1, a.finish(2).sourcePloidyConversions());
    }
    @Test void bypassedHaploidMissingMustNotBeReportedAsAnImputedCall() {
        var a = audit(options(Mode.REFERENCE, true, Missing.IMPUTE, true), 2);
        assertThrows(IllegalArgumentException.class, () -> row(a, "v", new String[]{"1", "."}, "1", "0"));
    }
    @Test void explicitlyUnphasedTargetOnlyRowsRetainMissingAllelePhaseBits() {
        var a = audit(reference(Missing.IMPUTE), 4);
        a.accept("v", calls("0|1", "0|.", ".|1", ".|."), "v", calls("0/1", "0|.", "./1", ".|."), Disposition.UNPHASED_TARGET_ONLY);
        assertEquals(1, a.finish(1).unphasedRows());
    }
    @Test void targetOnlyUnphasingCanRetainMultiallelicCallsWithoutCollapsingThem() {
        var a = audit(reference(Missing.IMPUTE), 1);
        a.accept("v", List.of(EagleGenotypes.parse("2|1", 2)), "v", List.of(EagleGenotypes.parse("2/1", 2)), Disposition.UNPHASED_TARGET_ONLY);
        assertEquals(1, a.finish(1).fullyObservedInputCalls());
    }
    @Test void unphasedTargetOnlyModeIsNotCohortMode() {
        var a = audit(cohort(Missing.KEEP), 1);
        assertThrows(IllegalArgumentException.class, () -> a.accept("v", calls("0/1"), "v", calls("0/1"), Disposition.UNPHASED_TARGET_ONLY));
    }
    @Test void exactSampleNamesOrderAndUniquenessAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new Audit(List.of("A", "B"), List.of("B", "A"), cohort(Missing.IMPUTE), 2, 4));
        assertThrows(IllegalArgumentException.class, () -> new Audit(List.of("A", "A"), List.of("A", "A"), cohort(Missing.IMPUTE), 2, 4));
        assertThrows(IllegalArgumentException.class, () -> new Audit(List.of("A B"), List.of("A B"), cohort(Missing.IMPUTE), 2, 4));
    }
    @Test void completeVariantIdentityAndCallWidthCannotChange() {
        var a = audit(cohort(Missing.IMPUTE), 1); assertThrows(IllegalArgumentException.class, () -> a.accept("v1", calls("0/1"), "v2", calls("0|1"), Disposition.PHASED));
        var b = audit(cohort(Missing.IMPUTE), 2); assertThrows(IllegalArgumentException.class, () -> b.accept("v", calls("0/1"), "v", calls("0|1"), Disposition.PHASED));
    }
    @Test void rowAndCallCapacitiesDoNotTruncateAcceptedPopulations() {
        var a = new Audit(samples(1), samples(1), cohort(Missing.IMPUTE), 1, 10); row(a, "v1", new String[]{"0/1"}, "0|1");
        assertThrows(IllegalStateException.class, () -> row(a, "v2", new String[]{"0/1"}, "0|1"));
        var b = new Audit(samples(1), samples(1), cohort(Missing.IMPUTE), 10, 1); row(b, "v1", new String[]{"0/1"}, "0|1");
        assertThrows(IllegalStateException.class, () -> row(b, "v2", new String[]{"0/1"}, "0|1"));
    }
    @Test void incompletePopulationAndRepeatedFinishFail() {
        var a = audit(cohort(Missing.IMPUTE), 1); row(a, "v1", new String[]{"0/1"}, "0|1");
        assertThrows(IllegalStateException.class, () -> a.finish(2)); assertThrows(IllegalStateException.class, () -> a.finish(1));
        var b = audit(cohort(Missing.IMPUTE), 1); row(b, "v", new String[]{"0/1"}, "0|1"); b.finish(1);
        assertThrows(IllegalStateException.class, () -> b.finish(1));
    }
    @Test void orderedRawGtHashesRetainPhaseDifferencesWhileGenotypesStayConserved() {
        var a = audit(cohort(Missing.IMPUTE), 1); row(a, "v", new String[]{"0/1"}, "0|1"); var ar = a.finish(1);
        var b = audit(cohort(Missing.IMPUTE), 1); row(b, "v", new String[]{"0/1"}, "1|0"); var br = b.finish(1);
        assertEquals(ar.inputSha256(), br.inputSha256()); assertNotEquals(ar.outputSha256(), br.outputSha256());
    }
}
