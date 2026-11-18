/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DunnComparisonsTest {
    private static List<String[]> rows(String file)throws Exception {
        try(var stream=DunnComparisonsTest.class.getResourceAsStream("/org/jlinalg/stats/dunn/"+file)){assertNotNull(stream);return new String(stream.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(x->x.split("\t",-1)).toList();}
    }
    private static double number(String x){return Double.parseDouble(x);}
    private static void close(double expected,double actual,String label) {
        if(expected==0||expected>0&&expected<Double.MIN_NORMAL)assertEquals(expected,actual,8*Double.MIN_VALUE,label);
        else assertEquals(expected,actual,3e-11*Math.abs(expected)+3e-13,label);
    }
    @Test void all4557IndependentReferenceComparisonsAndGlobalRankDiagnosticsMatch()throws Exception {
        var models=new LinkedHashMap<String,List<double[]>>();for(String[] row:rows("inputs.tsv")) {var groups=models.computeIfAbsent(row[0],x->new ArrayList<>());assertEquals(groups.size(),Integer.parseInt(row[1]));groups.add(Arrays.stream(row[2].split(",")).mapToDouble(Double::parseDouble).toArray());}
        var expected=new LinkedHashMap<String,List<String[]>>();for(String[] row:rows("expected.tsv"))expected.computeIfAbsent(String.join("/",row[0],row[1],row[2]),x->new ArrayList<>()).add(row);
        int comparisons=0;
        for(var entry:expected.entrySet()) {
            List<String[]> reference=entry.getValue();String[] first=reference.get(0);double[][] values=models.get(first[0]).toArray(double[][]::new);var family=reference.stream().map(x->new DunnComparisons.Contrast(Integer.parseInt(x[3]),Integer.parseInt(x[4]))).toList();
            var result=DunnComparisons.compare(values,family,DunnComparisons.Adjustment.valueOf(first[2]));assertEquals(reference.size(),result.comparisons().size());
            for(int i=0;i<reference.size();i++) {String[] e=reference.get(i);var a=result.comparisons().get(i);String label=entry.getKey()+" pair"+i;assertEquals(Integer.parseInt(e[3]),a.first());assertEquals(Integer.parseInt(e[4]),a.second());close(number(e[5]),a.meanRankDifference(),label);close(number(e[6]),a.standardError(),label);close(number(e[7]),a.z(),label);close(number(e[8]),a.pValue(),label);assertEquals(number(e[9]),a.logPValue(),3e-10,label);close(number(e[10]),a.adjustedPValue(),label);assertEquals(number(e[11]),a.logAdjustedPValue(),3e-10,label);comparisons++;}
        }
        assertEquals(4557,comparisons);assertEquals(36,models.size());
        var summaries=new LinkedHashMap<String,DunnComparisons.Result>();for(var model:models.entrySet())summaries.put(model.getKey(),DunnComparisons.allPairs(model.getValue().toArray(double[][]::new),DunnComparisons.Adjustment.NONE));
        for(String[] e:rows("groups.tsv")) {var r=summaries.get(e[0]);var group=r.groups().get(Integer.parseInt(e[1]));assertEquals(Integer.parseInt(e[2]),group.size());close(number(e[3]),group.meanRank(),e[0]);close(number(e[4]),r.rankVariance(),e[0]);close(number(e[5]),r.tieCorrection(),e[0]);close(number(e[6]),r.kruskalWallis(),e[0]);close(number(e[7]),r.omnibusPValue(),e[0]);assertEquals(number(e[8]),r.logOmnibusPValue(),3e-10,e[0]);}
    }
    @Test void completeFamilyAndUndefinedRankPopulationsAreExplicitlyRefused() {
        double[][] values={{1,2},{3,4},{5,6}};var a=DunnComparisons.Adjustment.BONFERRONI;
        assertThrows(IllegalArgumentException.class,()->DunnComparisons.allPairs(new double[][]{{1}},a));assertThrows(IllegalArgumentException.class,()->DunnComparisons.allPairs(new double[][]{{1},{}},a));assertThrows(IllegalArgumentException.class,()->DunnComparisons.allPairs(new double[][]{{0,-0.},{0}},a));assertThrows(IllegalArgumentException.class,()->DunnComparisons.allPairs(new double[][]{{1,Double.NaN},{2}},a));assertThrows(IllegalArgumentException.class,()->DunnComparisons.allPairs(new double[][]{{Double.POSITIVE_INFINITY},{2}},a));
        for(List<DunnComparisons.Contrast> family:List.of(List.<DunnComparisons.Contrast>of(),List.of(new DunnComparisons.Contrast(0,0)),List.of(new DunnComparisons.Contrast(0,4)),List.of(new DunnComparisons.Contrast(0,1),new DunnComparisons.Contrast(1,0))))assertThrows(IllegalArgumentException.class,()->DunnComparisons.compare(values,family,a));
        assertThrows(IllegalArgumentException.class,()->DunnComparisons.versusControl(values,-1,a));assertThrows(IllegalArgumentException.class,()->DunnComparisons.allPairs(new double[2000][],a));
    }
    @Test void selectedComparisonsRetainAllGroupsForRankingAndLiteralCallerDirection() {
        double[][] values={{1,2,2},{2,3,4},{100,200}};double[][] original=Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);var contrast=List.of(new DunnComparisons.Contrast(1,0));var all=DunnComparisons.allPairs(values,DunnComparisons.Adjustment.NONE);var selected=DunnComparisons.compare(values,contrast,DunnComparisons.Adjustment.NONE);
        assertEquals(-all.comparisons().get(0).z(),selected.comparisons().get(0).z());assertEquals(all.rankVariance(),selected.rankVariance());assertNotEquals(DunnComparisons.allPairs(Arrays.copyOf(values,2),DunnComparisons.Adjustment.NONE).comparisons().get(0).standardError(),selected.comparisons().get(0).standardError());
        for(int i=0;i<values.length;i++)assertArrayEquals(original[i],values[i]);assertThrows(UnsupportedOperationException.class,()->selected.groups().clear());assertThrows(UnsupportedOperationException.class,()->selected.comparisons().clear());
        double[][] increasing=Arrays.stream(values).map(g->Arrays.stream(g).map(x->3*x+7).toArray()).toArray(double[][]::new);assertEquals(selected,DunnComparisons.compare(increasing,contrast,DunnComparisons.Adjustment.NONE));
    }
    @Test void subnormalAndUnderflowedTailsKeepTheirLogProbabilities() {
        double[] zero=new double[730],one=new double[730];Arrays.fill(one,1);
        for(var method:DunnComparisons.Adjustment.values()) {var c=DunnComparisons.allPairs(new double[][]{zero,one},method).comparisons().get(0);assertTrue(c.pValue()>0&&c.pValue()<Double.MIN_NORMAL);assertEquals(c.logPValue(),c.logAdjustedPValue());}
        double[] first=new double[488],second=new double[488],third=new double[488];Arrays.fill(second,1);Arrays.fill(third,2);var extreme=DunnComparisons.allPairs(new double[][]{first,second,third},DunnComparisons.Adjustment.SIDAK).comparisons().get(1);assertTrue(extreme.pValue()>0&&extreme.pValue()<Double.MIN_NORMAL);assertEquals(extreme.logPValue()+Math.log(3),extreme.logAdjustedPValue(),2e-13);
        zero=new double[800];one=new double[800];double[] two=new double[800];Arrays.fill(one,1);Arrays.fill(two,2);var c=DunnComparisons.allPairs(new double[][]{zero,one,two},DunnComparisons.Adjustment.HOLM).comparisons().get(1);assertEquals(0,c.pValue());assertEquals(0,c.adjustedPValue());assertTrue(Double.isFinite(c.logPValue())&&c.logPValue()<-745);
    }
    @Test void nearlyAllTiedAtTheCompleteObservationLimitRetainsExactVariance() {
        int n=DunnComparisons.MAXIMUM_OBSERVATIONS;double[][] values={new double[n-1],new double[]{1}};var r=DunnComparisons.allPairs(values,DunnComparisons.Adjustment.BONFERRONI);
        assertEquals(n,r.observations());assertEquals(n/4.0,r.rankVariance());assertEquals(n-1,r.largestTie());assertEquals(1,r.tiedValueGroups());assertEquals(n-1.,r.kruskalWallis(),1e-8);assertEquals(n-1.,r.comparisons().get(0).z()*r.comparisons().get(0).z(),1e-8);assertTrue(Double.isFinite(r.comparisons().get(0).logPValue()));
    }
}
