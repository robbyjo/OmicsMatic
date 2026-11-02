/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HardcallLdTest {
    private static final Runnable CHECK=()->{};
    private static int[] all(int n){return java.util.stream.IntStream.range(0,n).toArray();}
    private static HardcallLd.Frame frame(int n){return HardcallLd.frame("synthetic",n,all(n),HardcallLd.Limits.defaults(),CHECK);}
    // -1 denotes a missing diploid call; observed integers count BIM allele1.
    private static byte[] packed(int...x){byte[] b=new byte[(x.length+3)/4];for(int i=0;i<x.length;i++){int code=switch(x[i]){case -1->1;case 0->3;case 1->2;case 2->0;default->throw new AssertionError();};b[i/4]|=(byte)(code<<(2*(i%4)));}return b;}
    private static HardcallLd.Calls calls(HardcallLd.Frame f,int...x){return HardcallLd.encode(f,packed(x),CHECK);}
    private static void centeredOracle(int[] a,int[] b,int[] selected,HardcallLd.Pair got){
        int n=0;double ma=0,mb=0;for(int i:selected)if(a[i]>=0&&b[i]>=0){n++;ma+=a[i];mb+=b[i];}assertEquals(n,got.pairedCalls());
        if(n<2){assertEquals(HardcallLd.Status.INSUFFICIENT_PAIRED_CALLS,got.status());assertTrue(Double.isNaN(got.rSquared()));return;}
        ma/=n;mb/=n;double va=0,vb=0,cov=0;for(int i:selected)if(a[i]>=0&&b[i]>=0){double x=a[i]-ma,y=b[i]-mb;va+=x*x;vb+=y*y;cov+=x*y;}
        if(va==0||vb==0){assertEquals(va==0&&vb==0?HardcallLd.Status.CONSTANT_BOTH:va==0?HardcallLd.Status.CONSTANT_FIRST:HardcallLd.Status.CONSTANT_SECOND,got.status());assertTrue(Double.isNaN(got.correlation()));assertTrue(Double.isNaN(got.rSquared()));}
        else {assertEquals(HardcallLd.Status.DEFINED,got.status());double r=cov/Math.sqrt(va*vb);assertEquals(r,got.correlation(),2e-14);assertEquals(r*r,got.rSquared(),2e-14);assertTrue(got.rSquared()>=0&&got.rSquared()<=1);}
    }
    @Test void exactCountsSignsAndMarginalFrequencies(){
        var f=frame(4);HardcallLd.Calls a=calls(f,2,1,0,-1),b=calls(f,0,1,2,-1);var p=HardcallLd.pair(a,b,CHECK);
        assertEquals(3,p.pairedCalls());assertEquals(-6,p.covarianceNumerator());assertEquals(6,p.varianceNumeratorFirst());assertEquals(6,p.varianceNumeratorSecond());assertEquals(-1,p.correlation());assertEquals(1,p.rSquared());
        assertEquals(new HardcallLd.Frequencies(3,1,3,3,.5,.5,HardcallLd.MinorAllele.TIED),a.frequencies());assertFalse(p.exactRatioRecovery());
    }
    @Test void pairedMeansDoNotUseMarginalMeansOrImputation(){
        var f=frame(4);HardcallLd.Calls a=calls(f,2,1,0,-1),b=calls(f,2,1,2,0);var p=HardcallLd.pair(a,b,CHECK);
        assertEquals(3,p.pairedCalls());assertEquals(5,p.sumSecond());assertEquals(9,p.sumSquaresSecond());assertEquals(0,p.covarianceNumerator());assertEquals(0,p.rSquared());assertEquals(.625,b.frequencies().allele1Frequency());assertEquals(.375,b.frequencies().minorAlleleFrequency());assertEquals(HardcallLd.MinorAllele.ALLELE2,b.frequencies().minorAllele());
    }
    @Test void pairwiseConstantCanOccurForMarginallyPolymorphicRows(){var f=frame(4);var p=HardcallLd.pair(calls(f,0,2,-1,-1),calls(f,0,0,1,2),CHECK);assertEquals(HardcallLd.Status.CONSTANT_SECOND,p.status());assertEquals(2,p.pairedCalls());assertTrue(Double.isNaN(p.rSquared()));}
    @Test void unobservedAndSingletonPairsRemainUndefined(){
        var f=frame(3);HardcallLd.Calls a=calls(f,-1,-1,-1);assertEquals(HardcallLd.MinorAllele.UNOBSERVED,a.frequencies().minorAllele());assertTrue(Double.isNaN(a.frequencies().minorAlleleFrequency()));assertEquals(0,HardcallLd.pair(a,a,CHECK).pairedCalls());
        var p=HardcallLd.pair(calls(f,2,-1,0),calls(f,1,0,-1),CHECK);assertEquals(HardcallLd.Status.INSUFFICIENT_PAIRED_CALLS,p.status());assertEquals(1,p.pairedCalls());
    }
    @Test void allConstantStatesAreSeparate(){var f=frame(3);HardcallLd.Calls c=calls(f,1,1,1),v=calls(f,0,1,2);assertEquals(HardcallLd.Status.CONSTANT_FIRST,HardcallLd.pair(c,v,CHECK).status());assertEquals(HardcallLd.Status.CONSTANT_SECOND,HardcallLd.pair(v,c,CHECK).status());assertEquals(HardcallLd.Status.CONSTANT_BOTH,HardcallLd.pair(c,c,CHECK).status());}
    @Test void exhaustiveThreeSampleDiploidAndMissingTables(){
        var f=frame(3);int[] indices=all(3);int pairs=0;
        for(int x=0;x<64;x++)for(int y=0;y<64;y++){int[] a=new int[3],b=new int[3];for(int i=0;i<3;i++){a[i]=((x>>(2*i))&3)-1;b[i]=((y>>(2*i))&3)-1;}centeredOracle(a,b,indices,HardcallLd.pair(calls(f,a),calls(f,b),CHECK));pairs++;}assertEquals(4096,pairs);
    }
    @Test void selectedWordBoundariesAndAlleleFlipInvariance(){
        var random=new Random(20261102);
        for(int n:new int[]{1,2,3,4,63,64,65,127,128,129,2504}){
            int[] a=new int[n],b=new int[n],flip=new int[n];for(int i=0;i<n;i++){a[i]=random.nextInt(4)-1;b[i]=random.nextInt(4)-1;flip[i]=a[i]<0?-1:2-a[i];}
            int[] selected=java.util.stream.IntStream.range(0,n).filter(i->i%3!=1).map(i->n-1-i).toArray();var f=HardcallLd.frame("ordered-"+n,n,selected,HardcallLd.Limits.defaults(),CHECK);HardcallLd.Calls x=calls(f,a),y=calls(f,b);var p=HardcallLd.pair(x,y,CHECK);centeredOracle(a,b,selected,p);
            var reversed=HardcallLd.pair(calls(f,flip),y,CHECK);if(p.status()==HardcallLd.Status.DEFINED){assertEquals(p.rSquared(),reversed.rSquared());assertEquals(-p.correlation(),reversed.correlation(),1e-15);}
        }
    }
    @Test void frameIdentityAndOrderCannotBeGuessed(){HardcallLd.Frame a=frame(3),b=frame(3);assertThrows(IllegalArgumentException.class,()->HardcallLd.pair(calls(a,0,1,2),calls(b,0,1,2),CHECK));int[] indices={2,0};var f=HardcallLd.frame("explicit",3,indices,HardcallLd.Limits.defaults(),CHECK);indices[0]=1;assertArrayEquals(new int[]{2,0},f.selectedIndices());int[] leaked=f.selectedIndices();leaked[0]=1;assertArrayEquals(new int[]{2,0},f.selectedIndices());}
    @Test void capturedPackedCallsPreserveSelectedOrderAndInputs(){int[] x={0,-1,1,2,0};byte[] input=packed(x),before=input.clone();var f=HardcallLd.frame("selection",5,new int[]{3,1,0},HardcallLd.Limits.defaults(),CHECK);var row=HardcallLd.encode(f,input,CHECK);assertArrayEquals(packed(2,-1,0),row.packedCalls());assertArrayEquals(before,input);byte[] output=row.packedCalls();Arrays.fill(output,(byte)0);assertArrayEquals(packed(2,-1,0),row.packedCalls());}
    @Test void invalidIndicesDimensionsAndPaddingFail(){var l=HardcallLd.Limits.defaults();assertThrows(IllegalArgumentException.class,()->HardcallLd.frame("",3,all(3),l,CHECK));assertThrows(IllegalArgumentException.class,()->HardcallLd.frame("x",3,new int[]{1,1},l,CHECK));assertThrows(IllegalArgumentException.class,()->HardcallLd.frame("x",3,new int[]{3},l,CHECK));assertThrows(IllegalArgumentException.class,()->HardcallLd.frame("x",3,new int[]{-1},l,CHECK));var f=frame(3);assertThrows(IllegalArgumentException.class,()->HardcallLd.encode(f,new byte[0],CHECK));assertThrows(IllegalArgumentException.class,()->HardcallLd.encode(f,new byte[2],CHECK));assertThrows(IllegalArgumentException.class,()->HardcallLd.encode(f,new byte[]{(byte)0xc0},CHECK));}
    @Test void emptyFrameHasNoInventedFrequency(){var f=HardcallLd.frame("empty",0,new int[0],HardcallLd.Limits.defaults(),CHECK);var row=HardcallLd.encode(f,new byte[0],CHECK);assertEquals(0,row.frequencies().called());assertTrue(Double.isNaN(row.frequencies().allele1Frequency()));assertEquals(HardcallLd.Status.INSUFFICIENT_PAIRED_CALLS,HardcallLd.pair(row,row,CHECK).status());}
    @Test void completeFrameAndPairCapacitiesAreEnforced(){assertThrows(IllegalArgumentException.class,()->HardcallLd.frame("too-large",100,all(100),new HardcallLd.Limits(100,100,65536,100),CHECK));var f=HardcallLd.frame("word-limit",3,all(3),new HardcallLd.Limits(10,10,1048576,1),CHECK);var row=calls(f,0,1,2);assertThrows(IllegalArgumentException.class,()->HardcallLd.pair(row,row,CHECK));}
    @Test void cancellationNeverReturnsAPrefix(){
        var f=frame(130);int[] a=new int[130];Arrays.fill(a,1);byte[] input=packed(a),before=input.clone();AtomicInteger checks=new AtomicInteger();Runnable cancel=()->{if(checks.incrementAndGet()==3)throw new CancellationException("test");};assertThrows(CancellationException.class,()->HardcallLd.encode(f,input,cancel));assertArrayEquals(before,input);checks.set(0);var row=calls(f,a);assertThrows(CancellationException.class,()->HardcallLd.pair(row,row,cancel));
    }
    @Test void exactCommonLdFloorsRetainInclusiveDecisions(){
        for(int[] definition:new int[][]{{6,2},{19,9}}){int n=definition[0],count=definition[1];int[] a=new int[n],b=new int[n];a[0]=1;for(int i=1;i<=count;i++)b[i]=1;var f=frame(n);double expected=n==6?.1:.05;var p=HardcallLd.pair(calls(f,a),calls(f,b),CHECK);assertEquals(expected,p.rSquared());assertTrue(p.rSquared()>=expected);assertFalse(p.rSquared()>expected);}
    }
}
