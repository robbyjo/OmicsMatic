/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.HtseqBinaryAccessibility;
import org.jlinalg.sequence.HtseqBinaryAccessibility.Entry;
import org.jlinalg.sequence.HtseqBinaryAccessibility.Limits;
import org.jlinalg.sequence.HtseqBinaryAccessibility.Read;
import org.jlinalg.sequence.HtseqFeatureAssignment;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Binary presence over actual original interval traces; not a recovered author script. */
final class HtseqBinaryAccessibilityTest {
    static List<?> cases()throws IOException{
        try(var in=new GZIPInputStream(HtseqBinaryAccessibilityTest.class.getResourceAsStream("/org/jlinalg/sequence/htseq-binary-accessibility/original-matrices.json.gz"))){return (List<?>)HtseqFileContract.object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases");}
    }
    static Stream<Arguments> matrices()throws IOException{var result=new ArrayList<Arguments>();for(Object v:cases()){var r=HtseqFileContract.object(v);if(r.get("matrix")!=null)result.add(Arguments.of(HtseqFileContract.object(r.get("case")).get("id"),r));}assertEquals(32,result.size());return result.stream();}
    static Stream<Arguments> originalRefusals()throws IOException{var result=new ArrayList<Arguments>();for(Object v:cases()){var r=HtseqFileContract.object(v);if(r.get("matrix")==null)result.add(Arguments.of(HtseqFileContract.object(r.get("case")).get("id"),r));}assertEquals(2,result.size());return result.stream();}
    private static Interval interval(Object value)throws IOException{var r=HtseqFileContract.object(value);return new Interval((String)r.get("chromosome"),(Long)r.get("start"),(Long)r.get("end"),((String)r.get("strand")).charAt(0));}
    private static List<String> strings(Object value){var result=new ArrayList<String>();for(Object v:(List<?>)value)result.add((String)v);return result;}
    private static HtseqBinaryAccessibility.Matrix matrix(Map<String,Object> c)throws IOException{
        var fs=new ArrayList<Feature>();for(Object v:(List<?>)c.get("features")){var r=HtseqFileContract.object(v);fs.add(new Feature(interval(r.get("interval")),(String)r.get("id")));}
        var model=HtseqBinaryAccessibility.model(fs,strings(c.get("peaks")),(Boolean)c.get("stranded"));
        var reads=new ArrayList<Read>();for(Object v:(List<?>)c.get("reads")){var r=HtseqFileContract.object(v);var blocks=new ArrayList<Interval>();for(Object b:(List<?>)r.get("blocks"))blocks.add(interval(b));reads.add(new Read((String)r.get("cell"),blocks));}
        return HtseqBinaryAccessibility.presence(model,strings(c.get("cells")),reads);
    }
    @ParameterizedTest(name="original binary matrix {0}") @MethodSource("matrices")
    void originalStepsYieldExactBinaryCoordinatesAndAxes(String id,Map<String,Object> r)throws Exception{
        var expected=HtseqFileContract.object(r.get("matrix"));var actual=matrix(HtseqFileContract.object(r.get("case")));
        assertEquals(strings(expected.get("peaks")),actual.peakIds());assertEquals(strings(expected.get("cells")),actual.cellIds());
        var entries=new ArrayList<Entry>();for(Object v:(List<?>)expected.get("entries")){var p=(List<?>)v;entries.add(new Entry(Math.toIntExact((Long)p.get(0)),Math.toIntExact((Long)p.get(1))));}
        assertEquals(entries,actual.entries());assertEquals(entries.size(),actual.nonzero());assertEquals((Long)expected.get("processed_reads"),actual.processedReads());
        assertEquals((Long)expected.get("reads_with_overlap"),actual.readsOverlappingAnyPeak());assertEquals((Long)expected.get("reads_without_overlap"),actual.readsWithoutPeakOverlap());
        for(int i=0;i<actual.peakIds().size();i++)for(int j=0;j<actual.cellIds().size();j++)assertEquals(entries.contains(new Entry(i,j)),actual.accessible(i,j));
    }
    @ParameterizedTest(name="original interval refusal {0}") @MethodSource("originalRefusals")
    void originalIntervalFailuresAdmitNoMatrix(String id,Map<String,Object> r)throws Exception{
        assertEquals("IndexError",HtseqFileContract.object(r.get("source_error")).get("type"));assertThrows(IllegalArgumentException.class,()->matrix(HtseqFileContract.object(r.get("case"))));
    }
    private static Feature peak(String name,long start,long end){return new Feature(new Interval("chr1",start,end,'+'),name);}
    private static Read read(String cell,long start,long end){return new Read(cell,List.of(new Interval("chr1",start,end,'+')));}
    private static final HtseqFeatureAssignment.Limits INTERVALS=HtseqFeatureAssignment.Limits.defaults();
    @Test void duplicateAndInvalidAxesAreRefused(){
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p","p"),false));
        var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false);
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell","cell"),List.of()));
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(model,List.of(""),List.of()));
        assertThrows(IllegalArgumentException.class,()->new Read("",List.of()));
    }
    @Test void missingPeakIdentityAndUnknownCellAreExplicitErrors(){
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("other"),false));
        var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false);
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(read("Cell",0,1))));
    }
    @Test void inputAxesAndBlocksAreSnapshotted(){
        var peaks=new ArrayList<>(List.of("p"));var features=new ArrayList<>(List.of(peak("p",0,10)));var model=HtseqBinaryAccessibility.model(features,peaks,false);peaks.clear();features.clear();
        var cells=new ArrayList<>(List.of("cell"));var blocks=new ArrayList<>(List.of(new Interval("chr1",0,1,'+')));var read=new Read("cell",blocks);blocks.clear();
        Iterable<Read> reads=()->new Iterator<>(){boolean next=true;public boolean hasNext(){return next;}public Read next(){next=false;cells.set(0,"changed");return read;}};
        var matrix=HtseqBinaryAccessibility.presence(model,cells,reads);assertEquals(List.of("cell"),matrix.cellIds());assertEquals(List.of("p"),matrix.peakIds());assertTrue(matrix.accessible(0,0));
    }
    @Test void returnedMatrixIsImmutableAndCoordinatesAreBounded(){
        var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false);var matrix=HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(read("cell",0,1)));
        assertThrows(UnsupportedOperationException.class,()->matrix.peakIds().clear());assertThrows(UnsupportedOperationException.class,()->matrix.cellIds().set(0,"repair"));assertThrows(UnsupportedOperationException.class,()->matrix.entries().set(0,new Entry(0,0)));
        assertThrows(IndexOutOfBoundsException.class,()->matrix.accessible(1,0));assertThrows(IndexOutOfBoundsException.class,()->matrix.accessible(0,-1));
    }
    @Test void completeReadAndNewCoordinateBudgetsNeverTruncate(){
        var limited=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false,new Limits(1,1,1,1,INTERVALS),()->false);
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(limited,List.of("cell"),List.of(read("cell",0,1),read("cell",0,1))));
        var two=HtseqBinaryAccessibility.model(List.of(peak("p",0,10),peak("q",0,10)),List.of("q","p"),false,new Limits(2,1,100,1,INTERVALS),()->false);
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(two,List.of("cell"),List.of(read("cell",0,1))));
        var repeat=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false,new Limits(1,1,150,1,INTERVALS),()->false);var reads=new ArrayList<Read>();for(int i=0;i<150;i++)reads.add(read("cell",0,1));
        var matrix=HtseqBinaryAccessibility.presence(repeat,List.of("cell"),reads);assertEquals(1,matrix.nonzero());assertEquals(150,matrix.processedReads());
    }
    @Test void peakCellAndFeatureRecordCapacitiesAreComplete(){
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false,new Limits(0,1,1,1,INTERVALS),()->false));
        var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false,new Limits(1,0,1,1,INTERVALS),()->false);assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of()));
        var noFeatures=new HtseqFeatureAssignment.Limits(0,10,10,10,10);assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false,new Limits(1,1,1,1,noFeatures),()->false));
    }
    @Test void intervalOverlapBudgetRefusesWithoutPartialMatrix(){
        var noMatches=new HtseqFeatureAssignment.Limits(1,10,0,10,10);var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false,new Limits(1,1,10,1,noMatches),()->false);
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(read("cell",0,1))));
        assertEquals(0,HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(new Read("cell",List.of()))).nonzero());
    }
    @Test void cancellationAndThreadInterruptionAdmitNoResult(){
        var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),false);
        assertThrows(CancellationException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(),()->true));
        Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of()));}finally{Thread.interrupted();}
        assertEquals(1,HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(read("cell",0,1))).nonzero());
    }
    @Test void strandedQueriesAndZeroLengthBlocksRemainExplicit(){
        var model=HtseqBinaryAccessibility.model(List.of(peak("p",0,10)),List.of("p"),true);
        assertThrows(IllegalArgumentException.class,()->HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(new Read("cell",List.of(new Interval("chr1",0,1,'.'))))));
        assertThrows(IllegalArgumentException.class,()->read("cell",0,0));
        assertFalse(HtseqBinaryAccessibility.presence(model,List.of("cell"),List.of(new Read("cell",List.of(new Interval("chr1",0,1,'-'))))).accessible(0,0));
    }
}
