/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BedGraphSignalsTest {
    @Test void exactLengthWeightingAndZeroCoverageUseExplicitReferenceUniverse(){
        var track=new BedGraphSignals.Track(Map.of("chr1",100L,"chr2",50L),List.of(new BedGraphSignals.Segment("chr1",0,10,2),new BedGraphSignals.Segment("chr1",20,30,4)));
        var window=track.summarize("chr1",5,25);assertEquals(20,window.bases());assertEquals(10,window.coveredBases());assertEquals(30,window.integral());assertEquals(1.5,window.meanIncludingZeros());assertEquals(3,window.meanOverCovered());
        var gap=track.summarize("chr1",10,20);assertEquals(0,gap.coveredBases());assertEquals(0,gap.meanIncludingZeros());assertTrue(Double.isNaN(gap.meanOverCovered()));
        var genome=track.genomeSummary();assertEquals(150,genome.bases());assertEquals(20,genome.coveredBases());assertEquals(.4,genome.meanIncludingZeros(),1e-15);assertEquals(0,track.summarize("chr2",0,50).integral());
    }
    @Test void randomWindowsMatchIndependentPerBaseIntegration(){
        var random=new Random(215134);var segments=new ArrayList<BedGraphSignals.Segment>();double[] truth=new double[3000];boolean[] observed=new boolean[3000];
        for(int start=0;start<3000;){int n=1+random.nextInt(10);n=Math.min(n,3000-start);if(random.nextBoolean()){double value=random.nextInt(100)-30;segments.add(new BedGraphSignals.Segment("chr",start,start+n,value));for(int j=start;j<start+n;j++){truth[j]=value;observed[j]=true;}}start+=n;}
        var track=new BedGraphSignals.Track(Map.of("chr",3000L),segments);
        for(int k=0;k<1000;k++){
            int a=random.nextInt(3000),b=a+1+random.nextInt(3000-a);double sum=0;long covered=0;for(int j=a;j<b;j++){sum+=truth[j];if(observed[j])covered++;}
            var result=track.summarize("chr",a,b);assertEquals(sum,result.integral(),1e-10);assertEquals(covered,result.coveredBases());
        }
        assertFalse(track.isNonnegative());
    }
    @Test void smallLateWindowDoesNotLoseSignalBySubtractingHugeGlobalPrefixes(){
        var track=new BedGraphSignals.Track(Map.of("chr",100L),List.of(new BedGraphSignals.Segment("chr",0,10,1e25),new BedGraphSignals.Segment("chr",20,30,1),new BedGraphSignals.Segment("chr",40,50,2),new BedGraphSignals.Segment("chr",60,70,3)));
        assertEquals(1,track.summarize("chr",25,26).integral());assertEquals(40,track.summarize("chr",25,65).integral());
        var signed=new BedGraphSignals.Track(Map.of("chr",3L),List.of(new BedGraphSignals.Segment("chr",0,1,1e20),new BedGraphSignals.Segment("chr",1,2,1),new BedGraphSignals.Segment("chr",2,3,-1e20)));
        assertEquals(1,signed.summarize("chr",0,3).integral());assertEquals(1,signed.genomeSummary().integral());
    }
    @Test void rejectsOverlapsUnknownContigsBoundsAndNonfiniteIntegrals(){
        assertThrows(IllegalArgumentException.class,()->new BedGraphSignals.Track(Map.of("chr",100L),List.of(new BedGraphSignals.Segment("chr",0,20,1),new BedGraphSignals.Segment("chr",10,30,2))));
        assertThrows(IllegalArgumentException.class,()->new BedGraphSignals.Track(Map.of("chr",100L),List.of(new BedGraphSignals.Segment("other",0,10,1))));
        assertThrows(IllegalArgumentException.class,()->new BedGraphSignals.Track(Map.of("chr",100L),List.of(new BedGraphSignals.Segment("chr",90,101,1))));
        assertThrows(IllegalArgumentException.class,()->new BedGraphSignals.Track(Map.of("chr",100L),List.of(new BedGraphSignals.Segment("chr",0,10,Double.MAX_VALUE))));
        var empty=new BedGraphSignals.Track(Map.of("chr",100L),List.of());assertEquals(0,empty.genomeSummary().coveredBases());assertThrows(IllegalArgumentException.class,()->empty.summarize("1",0,10));assertThrows(IllegalArgumentException.class,()->empty.summarize("chr",10,10));
    }
}
