/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.jlinalg.genetics.ShapeitGenotypes;
import org.junit.jupiter.api.Test;

class ShapeitCommonCallsTest {
    private static ShapeitGenotypes.WireCall call(int first,int second){return new ShapeitGenotypes.WireCall(List.of(first,second));}
    private static ShapeitCommonCalls.Header header(List<String> target,List<String> ref,List<String> scaffold){return new ShapeitCommonCalls.Header(target,ref,scaffold,ShapeitCommonCalls.Limits.defaults());}
    @Test void sourceMissingPhaseAndPaddingStatesRemainDistinct(){
        var h=header(List.of("partial","phase-missing","padded"),List.of(),List.of());
        var row=ShapeitCommonCalls.decode(h,List.of(call(4,0),call(1,4),call(4,ShapeitGenotypes.VECTOR_END)),List.of(),null);
        assertEquals(List.of(5,10,6),row.target().stream().map(ShapeitCommonCalls.Target::initialPacked).toList());assertEquals(1,row.counts().missing());assertEquals(2,row.counts().heterozygous());assertEquals(1,row.counts().ignoredObservedAllelesInMissingCalls());
        assertTrue(row.risks().containsAll(Set.of(ShapeitCommonCalls.Risk.TARGET_PHASE_MARKED_MISSING_READ_AS_REFERENCE,ShapeitCommonCalls.Risk.TARGET_VECTOR_END,ShapeitCommonCalls.Risk.OBSERVED_ALLELES_IGNORED_IN_TARGET_MISSING_CALL)));
    }
    @Test void referenceChecksSecondPhaseBitWhileScaffoldChecksEitherBit(){
        var h=header(List.of("M"),List.of("R"),List.of("M"));
        assertThrows(IllegalArgumentException.class,()->ShapeitCommonCalls.decode(h,List.of(call(2,4)),List.of(call(5,2)),List.of(call(5,2))));
        var row=ShapeitCommonCalls.decode(h,List.of(call(2,4)),List.of(call(2,5)),List.of(call(5,2)));
        assertEquals(List.of(false,true),row.referenceHaplotypes());assertEquals(7,row.target().get(0).scaffoldPacked());assertEquals(1,row.counts().scaffoldApplied());
    }
    @Test void literalMissingReferenceIsRejectedButPhaseMarkedMissingSourceDepartureIsExposed(){
        var h=header(List.of("M"),List.of("R"),List.of());
        assertThrows(IllegalArgumentException.class,()->ShapeitCommonCalls.decode(h,List.of(call(2,4)),List.of(call(0,5)),null));
        var row=ShapeitCommonCalls.decode(h,List.of(call(2,4)),List.of(call(1,5)),null);assertEquals(List.of(false,true),row.referenceHaplotypes());assertTrue(row.risks().contains(ShapeitCommonCalls.Risk.REFERENCE_PHASE_MARKED_MISSING_READ_AS_REFERENCE));
    }
    @Test void originalDuplicateNameMappingAndAlreadyScaffoldedFirstMatchRemainVisible(){
        var h=header(List.of("same","same"),List.of(),List.of("same","same"));var row=ShapeitCommonCalls.decode(h,List.of(call(2,4),call(2,4)),List.of(),List.of(call(5,2),call(2,5)));
        assertEquals(1,h.duplicateTargetNames());assertEquals(1,h.duplicateScaffoldNames());assertEquals(List.of(7,10),row.target().stream().map(ShapeitCommonCalls.Target::scaffoldPacked).toList());assertEquals(ShapeitCommonCalls.ScaffoldReason.TARGET_NOT_HETEROZYGOUS,row.scaffold().get(1).reason());assertEquals(1,row.counts().scaffoldApplied());
    }
    @Test void ignoredScaffoldReasonsFollowTheOriginalConjunctionOrder(){
        var h=header(List.of("hom","het","missing","unphased"),List.of(),List.of("unknown","hom","het","missing","unphased"));
        var row=ShapeitCommonCalls.decode(h,List.of(call(2,2),call(2,4),call(2,4),call(2,4)),List.of(),List.of(call(3,5),call(3,5),call(3,3),call(0,5),call(2,4)));
        assertEquals(List.of(ShapeitCommonCalls.ScaffoldReason.UNKNOWN_SAMPLE,ShapeitCommonCalls.ScaffoldReason.TARGET_NOT_HETEROZYGOUS,ShapeitCommonCalls.ScaffoldReason.NOT_HETEROZYGOUS,ShapeitCommonCalls.ScaffoldReason.LITERAL_MISSING,ShapeitCommonCalls.ScaffoldReason.UNPHASED),row.scaffold().stream().map(ShapeitCommonCalls.Scaffold::reason).toList());
        assertEquals(0,row.counts().scaffoldApplied());assertEquals(1,h.unmatchedScaffoldNames());
    }
    @Test void sourceReferenceAndScaffoldPaddingAreNotSilentlyConvertedToMissing(){
        var h=header(List.of("M"),List.of("R"),List.of("M"));var row=ShapeitCommonCalls.decode(h,List.of(call(2,4)),List.of(call(4,ShapeitGenotypes.VECTOR_END)),List.of(call(5,ShapeitGenotypes.VECTOR_END)));
        assertEquals(List.of(true,false),row.referenceHaplotypes());assertEquals(7,row.target().get(0).scaffoldPacked());assertTrue(row.risks().contains(ShapeitCommonCalls.Risk.REFERENCE_VECTOR_END));assertTrue(row.risks().contains(ShapeitCommonCalls.Risk.SCAFFOLD_VECTOR_END));
    }
    @Test void dimensionsAndCapacitiesRejectWholeRowLoss(){
        var h=header(List.of("M"),List.of(),List.of());assertThrows(IllegalArgumentException.class,()->ShapeitCommonCalls.decode(h,List.of(),List.of(),null));assertThrows(IllegalArgumentException.class,()->new ShapeitCommonCalls.Header(List.of("M"),List.of(),List.of(),new ShapeitCommonCalls.Limits(1,1)));
        assertThrows(IllegalArgumentException.class,()->ShapeitCommonCalls.decode(h,List.of(new ShapeitGenotypes.WireCall(List.of(2))),List.of(),null));
    }
    @Test void returnedStateAndHeaderNamesAreImmutable(){
        var names=new ArrayList<>(List.of("M"));var h=header(names,List.of(),List.of());names.set(0,"changed");assertEquals(List.of("M"),h.target());var row=ShapeitCommonCalls.decode(h,List.of(call(2,4)),List.of(),null);
        assertThrows(UnsupportedOperationException.class,()->row.target().clear());assertThrows(UnsupportedOperationException.class,()->row.risks().clear());assertThrows(UnsupportedOperationException.class,()->h.target().clear());
    }
}
