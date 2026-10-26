/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GseaStatisticsTest {
    @Test void independentlyChecksSignedExtremaLeadingEdgesAndZeroMass() {
        double[] w={1,1,1,1};
        var positive=GseaStatistics.walk(w,new int[]{1,0},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO);
        assertEquals(1,positive.score());assertArrayEquals(new int[]{0,1},positive.leadingEdge());
        var negative=GseaStatistics.walk(w,new int[]{2,3},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO);
        assertEquals(-1,negative.score());assertArrayEquals(new int[]{3,2},negative.leadingEdge());
        var tied=GseaStatistics.walk(w,new int[]{0,3},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO);
        assertEquals(0,tied.score());assertEquals(0,tied.leadingEdge().length);
        assertEquals(-.5,GseaStatistics.walk(w,new int[]{0,3},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.NEGATIVE).score());
        assertEquals(.5,GseaStatistics.walk(w,new int[]{0,3},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.POSITIVE).score());
        var zero=GseaStatistics.walk(new double[]{0,0,2,2},new int[]{0,1},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO);
        assertTrue(zero.uniformZeroHitWeights());assertEquals(1,zero.score());
        assertEquals(Math.log(1.0/3),GseaStatistics.logHypergeometric(10,3,4,2),1e-14);
    }

    @Test void matchesActualOriginalFgseaAndEnrichitArithmetic() throws Exception {
        int rows=0,uniform=0,undefined=0;
        try(var reader=resource("gsea.tsv")) {
            assertNotNull(reader.readLine());String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1);double[] ranking=values(f[2]),weights=values(f[6]);double exponent=Double.parseDouble(f[3]);int[] selected=positions(f[4]);
                var tail=switch(f[5]){case "std"->GseaStatistics.Tail.STANDARD;case "pos"->GseaStatistics.Tail.POSITIVE;default->GseaStatistics.Tail.NEGATIVE;};
                boolean fgsea=f[1].equals("fgsea-integer");
                if(fgsea)assertArrayEquals(weights,GseaStatistics.integerWeights(ranking,exponent),0,f[0]);
                var result=fgsea?GseaStatistics.walk(weights,selected,tail,GseaStatistics.ExtremumTie.ZERO):GseaStatistics.rawWalk(weights,selected);
                if(result.uniformZeroHitWeights())uniform++;
                double expected=Double.parseDouble(f[7]);
                if(Double.isFinite(expected)) {
                    assertEquals(expected,result.score(),2e-12,f[0]);
                    assertEquals(Double.parseDouble(f[8]),result.maximum(),2e-12,f[0]);
                    assertEquals(Double.parseDouble(f[9]),result.minimum(),2e-12,f[0]);
                } else {assertFalse(fgsea);assertTrue(result.uniformZeroHitWeights());undefined++;}
                if(fgsea)assertArrayEquals(positions(f[10]),result.leadingEdge(),f[0]);
                rows++;
            }
        }
        assertEquals(768,rows);assertTrue(uniform>0);assertEquals(0,undefined);
    }

    @Test void matchesOriginalRHypergeometricLogTails() throws Exception {
        int rows=0;try(var reader=resource("ora.tsv")) {
            reader.readLine();String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t");double expected=Double.parseDouble(f[4]);
                assertEquals(expected,GseaStatistics.logHypergeometric(Integer.parseInt(f[0]),Integer.parseInt(f[1]),Integer.parseInt(f[2]),Integer.parseInt(f[3])),2e-10+2e-13*Math.abs(expected),line);rows++;
            }
        }
        assertEquals(94,rows);
    }

    @Test void matchesActualRAdjustmentFamiliesIncludingMissingValues() throws Exception {
        int rows=0;try(var reader=resource("adjust.tsv")) {
            reader.readLine();String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t");assertArrayEquals(values(f[3]),GseaStatistics.adjust(values(f[2]),GseaStatistics.Adjustment.valueOf(f[1].toUpperCase(java.util.Locale.ROOT))),2e-14,line);rows++;
            }
        }
        assertEquals(35,rows);
    }

    @Test void rejectsUndefinedRankingsInvalidMembersAndImpossibleTables() {
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.integerWeights(new double[]{0,0},1));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.integerWeights(new double[]{1,2},1));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.integerWeights(new double[]{1e308,1e307},2));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.integerWeights(new double[]{1,0},-1));
        for(int[] hits:new int[][]{{},{0,0},{0,1},{2}})assertThrows(IllegalArgumentException.class,()->GseaStatistics.walk(new double[]{1,1},hits,GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.walk(new double[]{1,-1},new int[]{0},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.logHypergeometric(10,9,9,1));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.adjust(new double[]{1.01},GseaStatistics.Adjustment.BH));
        assertArrayEquals(new double[0],GseaStatistics.adjust(new double[0],GseaStatistics.Adjustment.BH));
    }

    @Test void preservesInputsAndDefensivelyCopiesResults() {
        double[] input={3,2,1};double[] original=input.clone();GseaStatistics.integerWeights(input,1);assertArrayEquals(original,input);
        int[] selected={1,0};var result=GseaStatistics.walk(input,selected,GseaStatistics.Tail.POSITIVE,GseaStatistics.ExtremumTie.ZERO);
        assertArrayEquals(new int[]{1,0},selected);int[] leading=result.leadingEdge();leading[0]=99;assertNotEquals(99,result.leadingEdge()[0]);
        double[] tops=result.tops();tops[0]=99;assertNotEquals(99,result.tops()[0]);
    }
    @Test void reusesValidatedWeightsWithoutMutableInputAliases() {
        double[] weights={1,1,1,1};var prepared=GseaStatistics.prepareWeights(weights);weights[0]=Double.NaN;
        assertEquals(1,prepared.walk(new int[]{0,1},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO).score());
        assertEquals(-1,prepared.walk(new int[]{2,3},GseaStatistics.Tail.STANDARD,GseaStatistics.ExtremumTie.ZERO).score());
        assertEquals(-.5,prepared.rawWalk(new int[]{0,3}).score(),1e-15);
        assertThrows(IllegalArgumentException.class,()->prepared.walk(new int[]{1,1},GseaStatistics.Tail.POSITIVE,GseaStatistics.ExtremumTie.ZERO));
        assertThrows(IllegalArgumentException.class,()->GseaStatistics.prepareWeights(new double[]{1,Double.NaN}));
    }
    private static BufferedReader resource(String name){return new BufferedReader(new InputStreamReader(java.util.Objects.requireNonNull(GseaStatisticsTest.class.getResourceAsStream("/enrichment/"+name)),StandardCharsets.UTF_8));}
    private static double[] values(String s){return s.isEmpty()?new double[0]:Arrays.stream(s.split(";")).mapToDouble(Double::parseDouble).toArray();}
    private static int[] positions(String s){return s.isEmpty()?new int[0]:Arrays.stream(s.split(";")).mapToInt(Integer::parseInt).toArray();}
}
