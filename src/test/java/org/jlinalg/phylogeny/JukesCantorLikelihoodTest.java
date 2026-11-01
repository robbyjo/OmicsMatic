/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.phylogeny;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JukesCantorLikelihoodTest {
    private static double transition(int from,int to,double length) {
        return .25+(from==to?.75:-.25)*StrictMath.exp(-4*length/3);
    }

    @Test void twoTipLikelihoodMatchesClosedFormUnderAReversibleStationaryModel() {
        var tree=NewickTree.parse("(A:.1,B:.2);");
        var result=JukesCantorLikelihood.evaluate(tree,Map.of("A","ACGT","B","ACGA"));
        double same=StrictMath.log(.25*transition(0,0,.3));double other=StrictMath.log(.25*transition(0,1,.3));
        assertArrayEquals(new double[]{same,same,same,other},result.siteLogLikelihoods(),1e-14);
        assertEquals(3*same+other,result.logLikelihood(),1e-14);assertEquals(0,result.zeroProbabilitySites());
    }

    @Test void fourTaxonPruningMatchesIndependentEnumerationOfAllAncestralAssignments() {
        var tree=NewickTree.parse("((A:.1,B:.2):.3,C:.4,D:.5);");
        Map<String,String> data=Map.of("A","ACGTACGTACGT","B","ACGTACGTACGA","C","TCGTCCGTTCGT","D","TCGTCCGTACGT");
        var result=JukesCantorLikelihood.evaluate(tree,data);double total=0;
        for (int site=0;site<12;site++) {
            int a="ACGT".indexOf(data.get("A").charAt(site)),b="ACGT".indexOf(data.get("B").charAt(site));
            int c="ACGT".indexOf(data.get("C").charAt(site)),d="ACGT".indexOf(data.get("D").charAt(site));
            double probability=0;
            for (int root=0;root<4;root++) for (int internal=0;internal<4;internal++)
                probability+=.25*transition(root,internal,.3)*transition(internal,a,.1)*transition(internal,b,.2)*transition(root,c,.4)*transition(root,d,.5);
            assertEquals(StrictMath.log(probability),result.siteLogLikelihoods()[site],2e-14);total+=StrictMath.log(probability);
        }
        assertEquals(total,result.logLikelihood(),5e-14);
    }

    @Test void rerootingAndChildOrderLeaveTheReversibleLikelihoodUnchanged() {
        Map<String,String> data=Map.of("A","ATGCCG","B","ATGTCG","C","CTGAAA","D","CTGAGA");
        var a=JukesCantorLikelihood.evaluate(NewickTree.parse("((A:.1,B:.2):.3,C:.4,D:.5);"),data);
        var b=JukesCantorLikelihood.evaluate(NewickTree.parse("(B:.2,A:.1,(D:.5,C:.4):.3);"),data);
        assertEquals(a.logLikelihood(),b.logLikelihood(),5e-14);assertArrayEquals(a.siteLogLikelihoods(),b.siteLogLikelihoods(),2e-14);
    }

    @Test void ambiguitiesAreSumsOverAllowedObservationsAndGapsAreUnobserved() {
        var tree=NewickTree.parse("(A:.1,B:.2);");
        double ambiguity=JukesCantorLikelihood.evaluate(tree,Map.of("A","R","B","C")).logLikelihood();
        double a=JukesCantorLikelihood.evaluate(tree,Map.of("A","A","B","C")).logLikelihood();
        double g=JukesCantorLikelihood.evaluate(tree,Map.of("A","G","B","C")).logLikelihood();
        assertEquals(StrictMath.log(StrictMath.exp(a)+StrictMath.exp(g)),ambiguity,1e-14);
        assertEquals(-StrictMath.log(4),JukesCantorLikelihood.evaluate(tree,Map.of("A","N","B","C")).logLikelihood(),1e-14);
        var missing=JukesCantorLikelihood.evaluate(tree,Map.of("A","N?-n","B","-NN?"));
        assertEquals(0,missing.logLikelihood());assertArrayEquals(new double[4],missing.siteLogLikelihoods());
    }

    @Test void lowercaseAndAllIupacCodesHaveTheirDeclaredSetProbabilities() {
        var tree=NewickTree.parse("tip;");
        String symbols="ACGTRYSWKMBDHVN?-acgtryswkmbdhvn";
        var values=JukesCantorLikelihood.evaluate(tree,Map.of("tip",symbols)).siteLogLikelihoods();
        for (int i=0;i<symbols.length();i++) {
            char symbol=Character.toUpperCase(symbols.charAt(i));int size="ACGT".indexOf(symbol)>=0?1:"RYSWKM".indexOf(symbol)>=0?2:"BDHV".indexOf(symbol)>=0?3:4;
            assertEquals(StrictMath.log(size/4.0),values[i],1e-15);
        }
    }

    @Test void zeroBranchContradictionsAreReportedAsZeroProbabilityWithoutLosingOtherSites() {
        var tree=NewickTree.parse("(A:0,B:0);");var result=JukesCantorLikelihood.evaluate(tree,Map.of("A","AACN","B","ACCN"));
        assertEquals(Double.NEGATIVE_INFINITY,result.logLikelihood());assertEquals(1,result.zeroProbabilitySites());
        assertArrayEquals(new double[]{-StrictMath.log(4),Double.NEGATIVE_INFINITY,-StrictMath.log(4),0},result.siteLogLikelihoods());
    }

    @Test void positiveSubnormalBranchesRemainFiniteInLogSpace() {
        double tiny=Double.MIN_VALUE;
        var tree=NewickTree.parse("(A:"+tiny+",B:"+tiny+");");
        var result=JukesCantorLikelihood.evaluate(tree,Map.of("A","A","B","C"));
        assertEquals(StrictMath.log(tiny)-StrictMath.log(6),result.logLikelihood(),2e-13);
        assertEquals(0,result.zeroProbabilitySites());assertTrue(Double.isFinite(result.logLikelihood()));
    }

    @Test void largePolytomyDoesNotUnderflowItsWholeProduct() {
        int perState=1000;StringBuilder source=new StringBuilder("(");Map<String,String> sequences=new HashMap<>();
        for (int i=0;i<4*perState;i++) { if (i>0) source.append(',');source.append('T').append(i).append(":.01");sequences.put("T"+i,String.valueOf("ACGT".charAt(i%4))); }
        source.append(");");var result=JukesCantorLikelihood.evaluate(NewickTree.parse(source.toString()),sequences);
        double expected=perState*StrictMath.log(transition(0,0,.01))+3*perState*StrictMath.log(transition(0,1,.01));
        assertEquals(expected,result.logLikelihood(),1e-8);assertEquals(0,result.zeroProbabilitySites());
    }

    @Test void veryLongBranchesConvergeToIndependentStationaryObservations() {
        var tree=NewickTree.parse("(A:1e308,B:1e308);");
        assertEquals(-StrictMath.log(16),JukesCantorLikelihood.evaluate(tree,Map.of("A","A","B","C")).logLikelihood(),1e-14);
    }

    @Test void deepUnaryPathsRemainIterativeAndRootStemDoesNotChangeLikelihood() {
        int depth=5000;var tree=NewickTree.parse("(".repeat(depth)+"tip:1"+"):1".repeat(depth)+";");
        assertEquals(-StrictMath.log(4),JukesCantorLikelihood.evaluate(tree,Map.of("tip","A")).logLikelihood(),1e-12);
        var stem=NewickTree.parse("(A:.1,B:.2):-100;");var plain=NewickTree.parse("(A:.1,B:.2);");
        assertEquals(JukesCantorLikelihood.evaluate(plain,Map.of("A","A","B","C")).logLikelihood(),JukesCantorLikelihood.evaluate(stem,Map.of("A","A","B","C")).logLikelihood());
    }

    @Test void completeMatrixAndOperationCapacitiesNeverReturnSubsetScores() {
        var tree=NewickTree.parse("(A:1,B:2);");Map<String,String> data=Map.of("A","AC","B","AT");
        assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(tree,data,new JukesCantorLikelihood.Limits(1,10,100,100)));
        assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(tree,data,new JukesCantorLikelihood.Limits(10,1,100,100)));
        assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(tree,data,new JukesCantorLikelihood.Limits(10,10,3,100)));
        assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(tree,data,new JukesCantorLikelihood.Limits(10,10,100,5)));
    }

    @Test void invalidTaxaStatesOrEdgesAreRefusedBeforeAResultIsProduced() {
        var tree=NewickTree.parse("(A:1,B:2);");
        for (Map<String,String> data:java.util.List.of(Map.of("A","AC"),Map.of("A","AC","B","A"),Map.of("A","","B",""),Map.of("A","AC","B","AU"),Map.of("A","AC","B","A."),Map.of("A","AC","B","AT","extra","AC")))
            assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(tree,data));
        assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(NewickTree.parse("(A,B:1);"),Map.of("A","A","B","C")));
        assertThrows(IllegalArgumentException.class,()->JukesCantorLikelihood.evaluate(NewickTree.parse("(A:-1,B:1);"),Map.of("A","A","B","C")));
    }

    @Test void resultArraysAreDefensiveCopies() {
        var result=JukesCantorLikelihood.evaluate(NewickTree.parse("tip;"),Map.of("tip","A"));
        double expected=result.siteLogLikelihoods()[0];var copy=result.siteLogLikelihoods();copy[0]=999;
        assertEquals(expected,result.siteLogLikelihoods()[0]);
    }
}
