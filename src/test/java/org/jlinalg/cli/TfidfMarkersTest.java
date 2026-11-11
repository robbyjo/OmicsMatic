/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.singlecell.TfidfMarkers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

class TfidfMarkersTest {
    record Case(String version,Map<String,Object> value){@Override public String toString(){return version+":"+value.get("id");}}
    static Stream<Case> originals()throws IOException {
        var cases=new ArrayList<Case>();
        for(String version:List.of("1.6.2","1.3.0")){
            String resource="/org/jlinalg/singlecell/soupx-markers/original-"+version+"-v2.json.gz";
            try(var input=new GZIPInputStream(Objects.requireNonNull(TfidfMarkersTest.class.getResourceAsStream(resource)))){
                var root=object(NextflowParameters.parse(NextflowFiles.utf8(input.readAllBytes()),16<<20,2000000));assertEquals(version,text(root.get("version")));assertEquals(Boolean.TRUE,root.get("source_bodies_and_formals_checked_before_and_after"));
                for(Object value:list(root.get("cases")))cases.add(new Case(version,object(value)));
            }
        }assertEquals(166,cases.size());return cases.stream();
    }
    static TfidfMarkers.Result calculate(Map<String,Object> value)throws IOException {
        var genes=strings(value.get("genes"),10000);var cells=strings(value.get("cells"),10000);var clusters=strings(value.get("clusters"),10000);var entries=new ArrayList<TfidfMarkers.Entry>();
        for(Object o:list(value.get("entries"))){var e=object(o);entries.add(new TfidfMarkers.Entry((int)integer(e.get("gene"),0,10000),(int)integer(e.get("cell"),0,10000),finite(e.get("value"),0,Double.MAX_VALUE)));}
        return TfidfMarkers.calculate(genes,cells,clusters,entries,"ALL".equals(value.get("N"))?-1:(int)integer(value.get("N"),1,10000),finite(value.get("FDR"),0,1),finite(value.get("expressCut"),0,100),1000000,new TfidfMarkers.Budget(100000000));
    }
    @ParameterizedTest(name="{0}") @MethodSource("originals") void matchesUnchangedOriginalMarkersAndEveryStatistic(Case c)throws IOException {
        var original=object(c.value.get("original"));
        if(!bool(c.value.get("native_domain"))){assertFalse(bool(original.get("returned")));assertThrows(IllegalArgumentException.class,()->calculate(c.value));return;}
        assertTrue(bool(original.get("returned")));var result=calculate(c.value);var expected=list(original.get("rows"));assertEquals(expected.size(),result.markers().size());
        for(int i=0;i<expected.size();i++)compare(object(expected.get(i)),result.markers().get(i));
        var all=list(original.get("all_statistics"));assertEquals(all.size(),result.statistics().size());var byKey=new HashMap<List<String>,TfidfMarkers.Row>();for(var row:result.statistics())assertNull(byKey.put(List.of(row.gene(),row.cluster()),row));
        for(Object o:all){var row=object(o);compare(row,Objects.requireNonNull(byKey.remove(List.of(text(row.get("gene")),text(row.get("cluster"))))));}assertTrue(byKey.isEmpty());
        double fdr=finite(c.value.get("FDR"),0,1);assertEquals(result.markers().stream().filter(r->r.qValue()>=fdr).count(),result.selectedOutsideFdr());
        for(var row:result.statistics())assertEquals(result.markers().contains(row),row.selected());
    }
    static void compare(Map<String,Object> expected,TfidfMarkers.Row row)throws IOException {
        assertEquals(text(expected.get("gene")),row.gene());assertEquals(text(expected.get("cluster")),row.cluster());assertEquals(text(expected.get("secondBestClusterName")),row.secondBestCluster());
        same(expected.get("geneFrequency"),row.frequency());same(expected.get("geneFrequencyOutsideCluster"),row.outsideFrequency());same(expected.get("geneFrequencySecondBest"),row.secondBestFrequency());same(expected.get("geneFrequencyGlobal"),row.globalFrequency());same(expected.get("tfidf"),row.tfidf());same(expected.get("idf"),row.idf());same(expected.get("qval"),row.qValue());
    }
    static void same(Object expected,double actual){String s=expected instanceof NextflowParameters.NumberToken n?n.text():expected.toString();double d=s.equals("Inf")?Double.POSITIVE_INFINITY:s.equals("-Inf")?Double.NEGATIVE_INFINITY:Double.parseDouble(s);if(!Double.isFinite(d)||d==0)assertEquals(d,actual);else assertEquals(d,actual,Math.max(64*Math.ulp(d),3e-12*Math.abs(d)));}
    static TfidfMarkers.Result small(List<TfidfMarkers.Entry> entries,int n,double fdr,double cut,long pairs,long work){return TfidfMarkers.calculate(List.of("b","a"),List.of("c1","c2"),List.of("NA","A"),entries,n,fdr,cut,pairs,new TfidfMarkers.Budget(work));}
    static List<TfidfMarkers.Entry> valid(){return List.of(new TfidfMarkers.Entry(0,0,1),new TfidfMarkers.Entry(1,1,1));}
    @Test void rejectsRepeatedCoordinatesEvenExplicitZero(){var entries=new ArrayList<>(valid());entries.add(new TfidfMarkers.Entry(0,0,0));assertThrows(IllegalArgumentException.class,()->small(entries,1,1,0,100,10000));}
    @Test void rejectsInvalidOrNonfiniteSparseValues(){for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}){var e=new ArrayList<>(valid());e.set(0,new TfidfMarkers.Entry(0,0,value));assertThrows(IllegalArgumentException.class,()->small(e,1,1,0,100,10000));}assertThrows(IllegalArgumentException.class,()->small(List.of(new TfidfMarkers.Entry(2,0,1)),1,1,0,100,10000));}
    @Test void capacitiesAndInterruptionRefusePartialCalculation(){assertThrows(IllegalArgumentException.class,()->small(valid(),1,1,0,3,10000));assertThrows(IllegalStateException.class,()->small(valid(),1,1,0,100,1));Thread.currentThread().interrupt();try{assertThrows(IllegalStateException.class,()->small(valid(),1,1,0,100,10000));}finally{Thread.interrupted();}}
    @Test void refusesDuplicateAxesAndInvalidChoices(){assertThrows(IllegalArgumentException.class,()->TfidfMarkers.calculate(List.of("a","a"),List.of("1","2"),List.of("A","B"),valid(),1,1,0,100,new TfidfMarkers.Budget(10000)));for(int n:new int[]{0,-2})assertThrows(IllegalArgumentException.class,()->small(valid(),n,1,0,100,10000));for(double fdr:new double[]{0,-1,1.1,Double.NaN})assertThrows(IllegalArgumentException.class,()->small(valid(),1,fdr,0,100,10000));assertThrows(IllegalArgumentException.class,()->small(valid(),1,1,-.1,100,10000));}
    @Test void strictExpressionCutoffAndFdrAreLiteral(){var result=small(valid(),-1,1,0,100,10000);assertThrows(IllegalArgumentException.class,()->small(valid(),1,1,1,100,10000));double q=result.statistics().get(0).qValue();assertTrue(q>0&&q<=1);assertEquals(0,small(valid(),-1,q,0,100,10000).markers().size());}
}
