/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NewickTreeTest {
    @Test void retainsEveryIdentitySupportCommentAndBranchLexeme() {
        String raw="[&R] ((A_B:1e-3,'a b':.2)99/100[&CI={1,2}]:+0.30,C:4.00,D:0):7; [tail[nested]]\n";
        var tree=NewickTree.parse(raw);
        assertEquals(raw,tree.originalText());assertEquals(0,tree.root());
        assertEquals(Set.of("A_B","a b","C","D"),tree.taxa());
        assertEquals(6,tree.nodes().size());assertEquals("99/100",tree.nodes().get(1).label());
        assertEquals(7,tree.nodes().get(0).branchLength());
        assertEquals("[&R]",tree.nodes().get(0).comments().get(0));
        assertEquals("[&CI={1,2}]",tree.nodes().get(1).comments().get(0));
        assertEquals(java.util.List.of("[tail[nested]]"),tree.trailingComments());
        assertEquals(.201,tree.distance("A_B","a b"),1e-15);
        assertEquals(4.301,tree.distance("A_B","C"),1e-15);
    }

    @Test void exactUnrootedSplitsDoNotDependOnRootPlacementOrChildOrder() {
        var first=NewickTree.parse("((A:.1,B:.1):.2,(C:.1,(D:.1,E:.1):.2):.2);");
        var second=NewickTree.parse("(E:.1,D:.1,(C:.1,(B:.1,A:.1):.4):.2);");
        assertEquals(Set.of(Set.of("A","B"),Set.of("D","E")),first.bipartitions(1000));
        assertEquals(first.bipartitions(1000),second.bipartitions(1000));
        assertEquals(first.distance("A","E"),second.distance("A","E"),1e-15);
        assertEquals(Set.of(Set.of("A","B")),NewickTree.parse("((A:1,B:1):1,(C:1,D:1):1);").bipartitions(100));
    }

    @Test void relabelsOnlyCompleteTipSpansAndPreservesEveryOtherSourceCharacter() {
        String raw="[&R] ('X''one'[tip]:1.000e-04,B:.2)'internal support'[node]:-0.0;[doc]";
        var tree=NewickTree.parse(raw);
        String restored=tree.relabelLeaves(Map.of("X'one","sample (alpha), α's","B","patient_B"));
        assertEquals("[&R] ('sample (alpha), α''s'[tip]:1.000e-04,patient_B:.2)'internal support'[node]:-0.0;[doc]",restored);
        assertEquals(Set.of("sample (alpha), α's","patient_B"),NewickTree.parse(restored).taxa());
        assertEquals(raw,tree.originalText());
        assertThrows(IllegalArgumentException.class,()->tree.relabelLeaves(Map.of("B","b")));
        assertThrows(IllegalArgumentException.class,()->tree.relabelLeaves(Map.of("X'one","same","B","same")));
        assertThrows(IllegalArgumentException.class,()->tree.relabelLeaves(Map.of("X'one","a\nb","B","B")));
    }

    @Test void doesNotTranslateUnderscoresOrNormalizeDistinctUnicodeIdentities() {
        var tree=NewickTree.parse("(A_B:1,'A B':2,'é':3,'é':4);");
        assertEquals(4,tree.taxa().size());assertNotEquals(tree.leafNodes().get("A_B"),tree.leafNodes().get("A B"));
        assertNotEquals(tree.leafNodes().get("é"),tree.leafNodes().get("é"));
    }

    @Test void missingAndNegativeLengthsRemainVisibleButCannotBecomeDistances() {
        var missing=NewickTree.parse("(A,B:1);");assertNull(missing.nodes().get(1).branchLength());
        assertThrows(IllegalArgumentException.class,()->missing.distance("A","B"));
        var negative=NewickTree.parse("(A:-.1,B:1);");assertEquals(-.1,negative.nodes().get(1).branchLength());
        assertThrows(IllegalArgumentException.class,negative::requireMetricLengths);
        var stem=NewickTree.parse("(A:0,B:-0.0):-123;");assertEquals(0,stem.distance("A","B"));
        assertEquals(Double.doubleToRawLongBits(-0.0),Double.doubleToRawLongBits(stem.nodes().get(2).branchLength()));
        assertThrows(IllegalArgumentException.class,()->stem.distance("absent","B"));
    }

    @Test void reportsPathOverflowRatherThanReturningAnInfiniteMetric() {
        var tree=NewickTree.parse("(A:1e308,B:1e308);");
        assertThrows(ArithmeticException.class,()->tree.distance("A","B"));
    }

    @Test void sharedAncestralStemsAreExcludedBeforePathLengthsAreSummed() {
        var tree=NewickTree.parse("(((A:1,B:2):1e308):1e308,C:1);");
        assertEquals(0,tree.distance("A","A"));assertEquals(3,tree.distance("A","B"));assertEquals(3,tree.distance("B","A"));
        assertThrows(ArithmeticException.class,()->tree.distance("A","C"));
    }

    @Test void deepTreesParseAndRelabelWithoutRecursiveCallStackGrowth() {
        int depth=20000;String raw="(".repeat(depth)+"tip:1"+")".repeat(depth)+";";
        var tree=NewickTree.parse(raw);assertEquals(depth+1,tree.nodes().size());
        assertEquals(Set.of("tip"),tree.taxa());
        assertEquals("(".repeat(depth)+"other:1"+")".repeat(depth)+";",tree.relabelLeaves(Map.of("tip","other")));
    }

    @Test void allCapacitiesFailExplicitlyWithoutReturningPartialTaxaOrSplits() {
        var base=NewickTree.Limits.defaults();
        assertThrows(IllegalArgumentException.class,()->NewickTree.parse("(A:1,B:1);",new NewickTree.Limits(5,base.nodes(),base.depth(),base.labelCharacters(),base.commentCharacters())));
        assertThrows(IllegalArgumentException.class,()->NewickTree.parse("(A:1,B:1);",new NewickTree.Limits(100,2,10,10,10)));
        assertThrows(IllegalArgumentException.class,()->NewickTree.parse("((A:1,B:1):1,C:1);",new NewickTree.Limits(100,10,1,10,10)));
        assertThrows(IllegalArgumentException.class,()->NewickTree.parse("(long:1,B:1);",new NewickTree.Limits(100,10,10,3,10)));
        assertThrows(IllegalArgumentException.class,()->NewickTree.parse("[abcdef](A:1,B:1);",new NewickTree.Limits(100,10,10,10,5)));
        var tree=NewickTree.parse("((A:1,B:1):1,(C:1,D:1):1);");
        assertThrows(IllegalArgumentException.class,()->tree.bipartitions(1));
        assertThrows(IllegalArgumentException.class,()->tree.relabelLeaves(Map.of("A","a","B","b","C","c","D","d"),4));
        assertEquals(Set.of("A","B","C","D"),tree.taxa());
    }

    @Test void everyExposedCollectionIsImmutable() {
        var tree=NewickTree.parse("(A:1,B:2);");
        assertThrows(UnsupportedOperationException.class,()->tree.nodes().clear());
        assertThrows(UnsupportedOperationException.class,()->tree.nodes().get(0).children().clear());
        assertThrows(UnsupportedOperationException.class,()->tree.leafNodes().clear());
        assertThrows(UnsupportedOperationException.class,()->tree.taxa().clear());
        assertThrows(UnsupportedOperationException.class,()->tree.trailingComments().clear());
    }

    @Test void singletonAndPolytomyAreRetainedWithoutInventingInternalEdges() {
        var single=NewickTree.parse("'single taxon';");assertEquals(1,single.nodes().size());assertEquals(0,single.distance("single taxon","single taxon"));
        assertTrue(single.bipartitions(10).isEmpty());
        var star=NewickTree.parse("(A:1,B:2,C:3,D:4,E:5);");assertTrue(star.bipartitions(100).isEmpty());assertEquals(7,star.distance("B","E"));
    }

    @ParameterizedTest
    @ValueSource(strings={"", ";", "();", "(A,,B);", "(A,B", "(A,B);(C,D);", "(A,B); junk", "(A:NaN,B:1);", "(A:Infinity,B:1);", "(A:1e9999,B:1);", "(A:0x1.0p2,B:1);", "(A:,B:1);", "(A:1:2,B:1);", "(A B,C);", "('A,B);", "(A'[x],B);", "(A,B)[unclosed;", "(A,A);", "('a b','a b');", "('',B);", "(A,B):1;X", "(A,B)] ;", "(A:\u0000,B:1);", "('a\nb',B);", "('\ud800',B);"})
    void invalidOrAmbiguousTreesNeverProducePartialObjects(String raw) {
        assertThrows(IllegalArgumentException.class,()->NewickTree.parse(raw));
    }
}
