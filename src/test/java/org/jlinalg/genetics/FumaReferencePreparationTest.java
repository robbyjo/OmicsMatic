package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FumaReferencePreparationTest {
    private static final Runnable CHECK=()->{};
    private static final FumaReferencePreparation.Limits LIMITS=new FumaReferencePreparation.Limits(1000,100000,10000000,16777216);
    private static byte[] pack(int... calls){byte[] r=new byte[(calls.length+3)/4];for(int i=0;i<calls.length;i++){int code=switch(calls[i]){case -1->1;case 0->3;case 1->2;case 2->0;default->throw new IllegalArgumentException();};r[i/4]|=(byte)(code<<(2*(i%4)));}return r;}
    private static PlinkBedDataset.Variant v(int row,int chromosome,int position){return new PlinkBedDataset.Variant(""+chromosome,"rs"+row,position,"G","A",row);}
    private static HardcallLd.Frame frame(int n){int[] a=new int[n];for(int i=0;i<n;i++)a[i]=i;return HardcallLd.frame("synthetic-explicit-frame",n,a,new HardcallLd.Limits(10000,10000,16777216,10000000),CHECK);}
    private static final class Sink implements FumaReferencePreparation.Sink {
        final List<byte[]> captured=new ArrayList<>();final List<FumaReferencePreparation.Assessment> decisions=new ArrayList<>();final List<String> pairs=new ArrayList<>();final List<HardcallLd.Pair> values=new ArrayList<>();final List<Boolean> stored=new ArrayList<>();
        public void capture(PlinkBedDataset.Variant v,byte[] packed){captured.add(packed);}
        public void assessment(FumaReferencePreparation.Assessment a){decisions.add(a);}
        public void pair(PlinkBedDataset.Variant a,PlinkBedDataset.Variant b,HardcallLd.Pair r,boolean keep){pairs.add(a.id()+"/"+b.id());values.add(r);stored.add(keep);}
    }
    private static FumaReferencePreparation.Result run(List<PlinkBedDataset.Variant> variants,int[][] calls,FumaReferencePreparation.Options options,FumaReferencePreparation.Limits limits,Sink sink)throws IOException{return FumaReferencePreparation.prepare(variants,frame(calls[0].length),v->pack(calls[(int)v.ordinal()]),options,limits,sink,CHECK);}
    private static FumaReferencePreparation.Options options(double maf,double floor,long bp,int gap){return new FumaReferencePreparation.Options(maf,floor,new FumaLocusDefinition.LdWindow(bp,gap));}
    @Test void inputCaptureAndGenomicRankOrderAreDistinctWithInclusiveWindows()throws Exception{
        var vs=List.of(v(0,1,1101),v(1,1,100),v(2,1,1100),v(3,2,100));int[][] calls={{0,1,2,0},{0,1,2,0},{0,1,2,0},{0,1,2,0}};var sink=new Sink();var result=run(vs,calls,options(0,1,1000,1),LIMITS,sink);
        assertEquals(List.of("rs1/rs2","rs2/rs0"),sink.pairs);assertEquals(List.of("rs1","rs2","rs0","rs3"),result.variants().stream().map(FumaLocusDefinition.ReferenceVariant::rsId).toList());assertEquals(vs,sink.decisions.stream().map(FumaReferencePreparation.Assessment::variant).toList());assertEquals(2,result.assessedPairs());assertEquals(2,result.storedPairs());assertEquals(66,result.wordWork());assertEquals(2,result.peakCachedRows());
    }
    @Test void recordsEveryExclusionWithoutConfusingAllHeterozygotesWithMafZero()throws Exception{
        var vs=List.of(v(0,1,1),v(1,1,2),v(2,1,3),v(3,1,4),v(4,1,5));int[][] calls={{-1,-1,-1,-1},{0,0,0,0},{1,1,1,1},{1,0,0,0},{0,1,2,1}};var s=new Sink();var r=run(vs,calls,options(.2,0,100,100),LIMITS,s);
        assertEquals(List.of(FumaReferencePreparation.Disposition.NO_OBSERVED_CALLS,FumaReferencePreparation.Disposition.ZERO_MAF,FumaReferencePreparation.Disposition.CONSTANT_CALLS,FumaReferencePreparation.Disposition.BELOW_MINIMUM_MAF,FumaReferencePreparation.Disposition.RETAINED),r.assessments().stream().map(FumaReferencePreparation.Assessment::disposition).toList());assertEquals(.5,r.assessments().get(2).frequencies().minorAlleleFrequency());assertEquals(5,s.captured.size());assertEquals(1,r.variants().size());assertEquals(0,r.assessedPairs());
    }
    @Test void filteringPrecedesIndexRanksAndMafBoundaryIsInclusive()throws Exception{
        int[][] c={{0,1,2,1},{0,0,0,0},{0,1,2,1}};var s=new Sink();var r=run(List.of(v(0,1,10),v(1,1,20),v(2,1,30)),c,options(.5,1,20,1),LIMITS,s);assertEquals(List.of("rs0/rs2"),s.pairs);assertEquals(.5,r.variants().get(0).maf());
    }
    @Test void zeroWindowAndZeroPairBudgetAdmitSingletonsAcrossChromosomes()throws Exception{
        var s=new Sink();var r=run(List.of(v(0,1,1),v(1,2,1)),new int[][]{{0,1,2},{0,1,2}},options(0,0,0,0),new FumaReferencePreparation.Limits(2,0,22,1000000),s);assertEquals(0,r.assessedPairs());assertEquals(22,r.wordWork());
    }
    @Test void undefinedEligiblePairIsRecordedThenFailsWholeFamily()throws Exception{
        int[][][] cases={{{0,1,-1,-1},{0,-1,1,2}},{{0,0,1,-1},{0,1,-1,2}},{{0,1,-1,2},{0,0,1,-1}},{{0,0,1,-1},{1,1,-1,2}}};var expected=List.of(HardcallLd.Status.INSUFFICIENT_PAIRED_CALLS,HardcallLd.Status.CONSTANT_FIRST,HardcallLd.Status.CONSTANT_SECOND,HardcallLd.Status.CONSTANT_BOTH);
        for(int i=0;i<cases.length;i++){final int index=i;var s=new Sink();var e=assertThrows(IllegalArgumentException.class,()->run(List.of(v(0,1,1),v(1,1,2)),cases[index],options(0,.99,100,100),LIMITS,s));assertEquals(expected.get(i),s.values.get(0).status());assertFalse(s.stored.get(0));assertTrue(Double.isNaN(s.values.get(0).rSquared()));assertTrue(e.getMessage().contains("Undefined LD is not below-floor LD"));}
    }
    @Test void allExcludedFailsWithCompleteMarginalAudit()throws Exception{var s=new Sink();assertThrows(IllegalArgumentException.class,()->run(List.of(v(0,1,1)),new int[][]{{1,1,1}},options(0,0,10,10),LIMITS,s));assertEquals(1,s.decisions.size());assertEquals(FumaReferencePreparation.Disposition.CONSTANT_CALLS,s.decisions.get(0).disposition());}
    @Test void wholePairAndWordBudgetsFailBeforeAnyPairEmission()throws Exception{
        var vs=List.of(v(0,1,1),v(1,1,2),v(2,1,3));int[][] c={{0,1,2},{0,1,2},{0,1,2}};
        for(var l:List.of(new FumaReferencePreparation.Limits(3,2,10000,1000000),new FumaReferencePreparation.Limits(3,3,65,1000000))){var s=new Sink();assertThrows(IllegalArgumentException.class,()->run(vs,c,options(0,0,10,10),l,s));assertEquals(3,s.captured.size());assertTrue(s.pairs.isEmpty());}
    }
    @Test void marginalWorkAndStorageAdmissionPrecedeAllOutput()throws Exception{for(var l:List.of(new FumaReferencePreparation.Limits(1,0,10,1000000),new FumaReferencePreparation.Limits(1,0,11,65536))){var s=new Sink();assertThrows(IllegalArgumentException.class,()->run(List.of(v(0,1,1)),new int[][]{{0,1,2}},options(0,0,0,0),l,s));assertTrue(s.captured.isEmpty());}}
    @Test void selectedCallsChangingBetweenPassesFail()throws Exception{var n=new AtomicInteger();var s=new Sink();assertThrows(IOException.class,()->FumaReferencePreparation.prepare(List.of(v(0,1,1)),frame(3),v->n.getAndIncrement()==0?pack(0,1,2):pack(2,1,0),options(0,0,10,10),LIMITS,s,CHECK));assertEquals(1,s.captured.size());}
    @Test void malformedCoordinatesOrdinalsAndAllelesFailBeforeCapture()throws Exception{
        var variants=List.of(List.of(v(0,1,1),v(1,1,1)),List.of(v(1,1,1),v(0,1,2)),List.of(v(0,23,1)),List.of(v(0,1,0)),List.of(new PlinkBedDataset.Variant("01","rs0",1,"G","A",0)),List.of(new PlinkBedDataset.Variant("1","rs0",1,"G","G",0)));
        for(var vs:variants){var s=new Sink();assertThrows(IllegalArgumentException.class,()->run(vs,new int[][]{{0,1,2},{0,1,2}},options(0,0,10,10),LIMITS,s));assertTrue(s.captured.isEmpty());}
    }
    @Test void sinkFailureAndCancellationPropagateWithoutCompletedValue()throws Exception{
        var failure=new IOException("disk full");var sink=new FumaReferencePreparation.Sink(){public void capture(PlinkBedDataset.Variant v,byte[] packed)throws IOException{throw failure;}public void pair(PlinkBedDataset.Variant a,PlinkBedDataset.Variant b,HardcallLd.Pair p,boolean s){fail();}};
        assertSame(failure,assertThrows(IOException.class,()->FumaReferencePreparation.prepare(List.of(v(0,1,1)),frame(3),v->pack(0,1,2),options(0,0,0,0),LIMITS,sink,CHECK)));
        var s=new Sink();assertThrows(IllegalStateException.class,()->FumaReferencePreparation.prepare(List.of(v(0,1,1)),frame(3),v->pack(0,1,2),options(0,0,0,0),LIMITS,s,()->{throw new IllegalStateException("cancel");}));assertTrue(s.captured.isEmpty());
    }
    @Test void exactOriginalThresholdFloorsAreRetained()throws Exception{for(int n:new int[]{6,19}){int[] a=new int[n],b=new int[n];a[0]=1;Arrays.fill(b,1,n==6?3:10,1);var s=new Sink();double threshold=n==6?.1:.05;run(List.of(v(0,1,1),v(1,1,2)),new int[][]{a,b},options(0,threshold,1,1),LIMITS,s);assertEquals(threshold,s.values.get(0).rSquared());assertEquals(List.of(true),s.stored);}}
    @Test void randomWindowFamiliesMatchIndependentCenteredOracle()throws Exception{
        var random=new Random(93017);for(int trial=0;trial<100;trial++){int count=3+random.nextInt(17);var vs=new ArrayList<PlinkBedDataset.Variant>();int[][] c=new int[count][12];for(int i=0;i<count;i++){vs.add(v(i,i<10?1:2,100+(count-i)*7));for(int j=0;j<12;j++)c[i][j]=j<3?j:random.nextInt(3);}long bp=random.nextInt(80);int gap=random.nextInt(7);double floor=List.of(0.,.05,.4,.8,1.).get(trial%5);var s=new Sink();var result=run(vs,c,options(0,floor,bp,gap),LIMITS,s);
            var sorted=new ArrayList<>(vs);sorted.sort(java.util.Comparator.comparing(PlinkBedDataset.Variant::chromosome).thenComparingLong(PlinkBedDataset.Variant::position));var expected=new java.util.HashMap<String,Double>();for(int j=0;j<count;j++)for(int i=0;i<j;i++){var a=sorted.get(i);var b=sorted.get(j);if(a.chromosome().equals(b.chromosome())&&b.position()-a.position()<=bp&&j-i<=gap){double[] stats=new double[3];double ma=Arrays.stream(c[(int)a.ordinal()]).average().orElseThrow(),mb=Arrays.stream(c[(int)b.ordinal()]).average().orElseThrow();for(int k=0;k<12;k++){double x=c[(int)a.ordinal()][k]-ma,y=c[(int)b.ordinal()][k]-mb;stats[0]+=x*y;stats[1]+=x*x;stats[2]+=y*y;}expected.put(a.id()+"/"+b.id(),stats[0]*stats[0]/(stats[1]*stats[2]));}}
            assertEquals(expected.keySet(),new java.util.HashSet<>(s.pairs));long retained=0;for(int i=0;i<s.pairs.size();i++){double oracle=expected.get(s.pairs.get(i));assertEquals(oracle,s.values.get(i).rSquared(),1e-12);assertEquals(s.values.get(i).rSquared()>=floor,s.stored.get(i));if(s.stored.get(i))retained++;}assertEquals(expected.size(),result.assessedPairs());assertEquals(retained,result.storedPairs());assertEquals(11L*(count+expected.size()),result.wordWork());
        }
    }
}
