/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScviGeneProjectionTest {
    @Test void exactOrderDoesNotSilentlySortOrRelabelGenes() {
        var p = ScviGeneProjection.plan(List.of("β", "ENSG.1"), List.of("β", "ENSG.1"), ScviGeneProjection.Policy.EXACT_ORDER);
        assertArrayEquals(new int[]{0,1}, p.queryColumnForOutput());
        assertThrows(IllegalArgumentException.class, () -> ScviGeneProjection.plan(List.of("a","b"), List.of("b","a"), ScviGeneProjection.Policy.EXACT_ORDER));
        assertThrows(IllegalArgumentException.class, () -> ScviGeneProjection.plan(List.of("ENSG.1"), List.of("ENSG"), ScviGeneProjection.Policy.EXACT_ORDER));
    }
    @Test void zeroPaddingKeepsFullReferenceOrderAndNamesEveryMissingAndExtraGene() {
        var p = ScviGeneProjection.plan(List.of("β","b","c","d"), List.of("extra2","d","β","extra1"), ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA);
        assertEquals(List.of("β","b","c","d"), p.outputGenes());
        assertArrayEquals(new int[]{2,-1,-1,1}, p.queryColumnForOutput());
        assertArrayEquals(new int[]{-1,3,0,-1}, p.outputColumnForQuery());
        assertEquals(List.of("b","c"), p.missingReferenceGenes());
        assertEquals(List.of("extra2","extra1"), p.extraQueryGenes());
    }
    @Test void sharedSelectionIsExplicitlyABeforeTrainingPolicy() {
        var p = ScviGeneProjection.plan(List.of("a","b","c","d"), List.of("c","extra","a"), ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING);
        assertEquals(List.of("a","c"), p.outputGenes());
        assertArrayEquals(new int[]{2,0}, p.queryColumnForOutput());
        assertArrayEquals(new int[]{1,-1,0}, p.outputColumnForQuery());
        assertEquals(List.of("b","d"), p.missingReferenceGenes());
        assertEquals(List.of("extra"), p.extraQueryGenes());
    }
    @Test void identifiersAreCaseSensitiveAndAreNotAutomaticOrthologyMappings() {
        var p = ScviGeneProjection.plan(List.of("GENE","ENSG.1"), List.of("gene","ENSG.2"), ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA);
        assertArrayEquals(new int[]{-1,-1}, p.queryColumnForOutput());
        assertEquals(List.of("GENE","ENSG.1"), p.missingReferenceGenes());
        assertEquals(List.of("gene","ENSG.2"), p.extraQueryGenes());
    }
    @Test void noSharedGenesCannotBecomeAnEmptyTrainingModel() {
        assertThrows(IllegalArgumentException.class, () -> ScviGeneProjection.plan(List.of("a"), List.of("b"), ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING));
        var p = ScviGeneProjection.plan(List.of("a"), List.of("b"), ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA);
        assertArrayEquals(new int[]{-1},p.queryColumnForOutput());
        // A later count/model admission must reject zero-library cells; a projection records the truth.
    }
    @Test void duplicateBlankControlAndWhitespaceIdentifiersAreRejected() {
        for (var invalid : List.of(List.<String>of(),List.of("a","a"),List.of(""),List.of("a b"),List.of("a\tb"),List.of("a\u0000b"))) {
            assertThrows(IllegalArgumentException.class, () -> ScviGeneProjection.plan(invalid,List.of("a"),ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA));
            assertThrows(IllegalArgumentException.class, () -> ScviGeneProjection.plan(List.of("a"),invalid,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA));
        }
    }
    @Test void inputAndReturnedCollectionsCannotMutateAPlan() {
        var ref = new ArrayList<>(List.of("a","b")); var query = new ArrayList<>(List.of("b","a"));
        var p = ScviGeneProjection.plan(ref,query,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA);
        ref.set(0,"changed");query.clear();p.queryColumnForOutput()[0]=999;p.outputColumnForQuery()[0]=999;
        assertEquals(List.of("a","b"),p.outputGenes());assertArrayEquals(new int[]{1,0},p.queryColumnForOutput());assertArrayEquals(new int[]{1,0},p.outputColumnForQuery());
        assertThrows(UnsupportedOperationException.class, () -> p.outputGenes().set(0,"changed"));
    }
    @Test void inconsistentOrOutOfRangeBidirectionalMapsCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new ScviGeneProjection.Plan(ScviGeneProjection.Policy.EXACT_ORDER,List.of("a"),new int[]{0},new int[]{-1},List.of(),List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ScviGeneProjection.Plan(ScviGeneProjection.Policy.EXACT_ORDER,List.of("a"),new int[]{2},new int[]{0},List.of(),List.of()));
    }
}
