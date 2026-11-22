/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.jlinalg.genetics.ThresholdPeaks.*;

class ThresholdPeaksTest {
    private static BedGraphSignals.Segment segment(long start,long end,double value){return new BedGraphSignals.Segment("chr1",start,end,value);}
    @Test void geometryAndSummitsMatch129ActualUpstreamCallsIncludingTiesAndBoundaryRounding()throws Exception{
        try(var stream=getClass().getResourceAsStream("/track-peaks/macs3-3.1.0-bdgpeakcall.json")){
            assertNotNull(stream);Map<?,?> root=new org.yaml.snakeyaml.Yaml().load(stream);var cases=(List<?>)root.get("cases");assertEquals(129,cases.size());
            for(var item:cases){
                var row=(Map<?,?>)item;var reference=new java.util.LinkedHashMap<String,Long>();
                for(var entry:((Map<?,?>)row.get("reference")).entrySet())reference.put((String)entry.getKey(),((Number)entry.getValue()).longValue());
                var input=new ArrayList<BedGraphSignals.Segment>();for(var raw:(List<?>)row.get("segments")){
                    var values=(List<?>)raw;input.add(new BedGraphSignals.Segment((String)values.get(0),((Number)values.get(1)).longValue(),((Number)values.get(2)).longValue(),((Number)values.get(3)).doubleValue()));
                }
                var output=call(reference,input,((Number)row.get("cutoff")).doubleValue(),((Number)row.get("min_length")).longValue(),((Number)row.get("max_gap")).longValue(),Precision.FLOAT32,Missing.ERROR).peaks();
                var expected=(List<?>)row.get("expected");assertEquals(expected.size(),output.size(),row.get("id").toString());
                for(int i=0;i<expected.size();i++){
                    var e=(List<?>)expected.get(i);var actual=output.get(i);assertEquals(e.get(0),actual.chromosome());assertEquals(((Number)e.get(1)).longValue(),actual.start());assertEquals(((Number)e.get(2)).longValue(),actual.end());assertEquals(((Number)e.get(3)).longValue(),actual.summit()-actual.start(),row.get("id").toString());
                }
            }
        }
    }
    @Test void spanGapInclusionAndPlateauCoalescingHaveIndependentKnownAnswers(){
        var input=List.of(segment(0,10,0),segment(10,20,3),segment(20,30,3),segment(30,40,0),segment(40,70,3),segment(70,100,0));
        var peak=call(Map.of("chr1",100L),input,3,60,10,Precision.FLOAT64,Missing.ERROR).peaks().get(0);
        assertEquals(new Peak("chr1",10,70,20,3,50),peak);
        assertTrue(call(Map.of("chr1",100L),input,3,61,10,Precision.FLOAT64,Missing.ERROR).peaks().isEmpty());
        var separated=call(Map.of("chr1",100L),input,3,1,9,Precision.FLOAT64,Missing.ERROR).peaks();assertEquals(2,separated.size());assertEquals(55,separated.get(1).summit());
        var ties=List.of(segment(0,100,3),segment(100,101,0),segment(101,102,3),segment(102,103,0),segment(103,110,3),segment(110,111,0),segment(111,113,3));
        assertEquals(101,call(Map.of("chr1",113L),ties,3,1,1,Precision.FLOAT64,Missing.ERROR).peaks().get(0).summit());
    }
    @Test void float32ChangesBoundaryMembershipBeforeCoalescingButFloat64RetainsInput(){
        var input=List.of(segment(0,10,1.00000001),segment(10,20,0),segment(20,30,1.0000001));
        var single=call(Map.of("chr1",30L),input,1.00000002,1,0,Precision.FLOAT32,Missing.ERROR);
        assertEquals(1,single.effectiveCutoff());assertEquals(2,single.roundedSegments());assertEquals(2,single.peaks().size());
        var full=call(Map.of("chr1",30L),input,1.00000002,1,0,Precision.FLOAT64,Missing.ERROR);assertEquals(1,full.peaks().size());assertEquals(20,full.peaks().get(0).start());
    }
    @Test void sparseUniverseIsExplicitAndZeroSignedSummitsUseTheMathematicalMaximum(){
        var result=call(Map.of("chr1",20L,"chr2",10L),List.of(segment(5,10,3)),0,1,0,Precision.FLOAT64,Missing.ZERO);
        assertEquals(25,result.zeroFilledBases());assertEquals(2,result.peaks().size());assertEquals(new Peak("chr1",0,20,7,3,20),result.peaks().get(0));
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",20L),List.of(segment(5,10,3)),2,1,0,Precision.FLOAT64,Missing.ERROR));
        var signed=call(Map.of("chr1",20L),List.of(segment(0,10,0),segment(10,20,-1)),-2,1,0,Precision.FLOAT64,Missing.ERROR).peaks().get(0);assertEquals(0,signed.height());assertEquals(5,signed.summit());
        assertTrue(call(Map.of("chr1",20L),List.of(),1,1,0,Precision.FLOAT64,Missing.ZERO).peaks().isEmpty());
    }
    @Test void longCoordinatesAndLargeFiniteScoresDoNotRequireGenomeSizeArraysOrIntegrals(){
        long length=Long.MAX_VALUE;var result=call(Map.of("chr1",length),List.of(segment(length-11,length,1e300)),1,11,0,Precision.FLOAT64,Missing.ZERO);
        assertEquals(length-6,result.peaks().get(0).summit());assertEquals(length-11,result.zeroFilledBases());assertEquals(1e300,result.peaks().get(0).height());
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",length),List.of(segment(length-11,length,1e300)),1,1,0,Precision.FLOAT32,Missing.ZERO));
    }
    @Test void ambiguousInvalidAndNonfiniteContractsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",100L),List.of(segment(0,10,1),segment(9,20,2)),1,1,0,Precision.FLOAT64,Missing.ZERO));
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr2",100L),List.of(segment(0,10,1)),1,1,0,Precision.FLOAT64,Missing.ZERO));
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",5L),List.of(segment(0,10,1)),1,1,0,Precision.FLOAT64,Missing.ZERO));
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",5L),List.of(),Double.NaN,1,0,Precision.FLOAT64,Missing.ZERO));
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",5L),List.of(),1,0,0,Precision.FLOAT64,Missing.ZERO));
        assertThrows(IllegalArgumentException.class,()->call(Map.of("chr1",5L),List.of(),1,1,-1,Precision.FLOAT64,Missing.ZERO));
    }
}
