package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jlinalg.omics.methylation.AllcoolsRegionQuantifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllcoolsRegionQuantifierTest {
    private static long[] longs(String s){return Arrays.stream(s.split(",")).mapToLong(Long::parseLong).toArray();}
    private static double[] doubles(String s){return Arrays.stream(s.split(",")).mapToDouble(Double::parseDouble).toArray();}
    @Test void matchesEveryOriginalPreparedRegionScoreAndSaturatedCount()throws Exception {
        int cases=0;long values=0;var discrepancies=new ArrayList<String>();
        try(var in=new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/org/jlinalg/omics/allcools-region/cases.tsv")),StandardCharsets.UTF_8))) {
            assertEquals("case\thypo_cutoff\thyper_cutoff\tmc\tcov\tpooled_probability\tcount_mc\tcount_cov\thypo\thyper",in.readLine());String line;
            while((line=in.readLine())!=null) {
                String[] f=line.split("\t",-1);assertEquals(10,f.length);cases++;
                var r=AllcoolsRegionQuantifier.calculate(longs(f[3]),longs(f[4]),Double.parseDouble(f[1]),Double.parseDouble(f[2]),()->{});
                assertEquals(Double.parseDouble(f[5]),r.pooledProbability(),f[0]+" pooled probability");
                assertArrayEquals(longs(f[6]),r.countsMc(),f[0]);assertArrayEquals(longs(f[7]),r.countsCov(),f[0]);values+=r.countsMc().length*2L+1;
                for(int column=0;column<2;column++) {
                    double[] expected=doubles(f[8+column]),actual=column==0?r.hypo():r.hyper();assertEquals(expected.length,actual.length);
                    for(int i=0;i<expected.length;i++) {
                        values++;if(expected[i]!=actual[i]&&discrepancies.size()<50)discrepancies.add(f[0]+" column="+column+" feature="+i+" expected="+expected[i]+" actual="+actual[i]);
                    }
                }
            }
        }
        assertEquals(1000,cases);assertTrue(values>150000);assertTrue(discrepancies.isEmpty(),String.join("\n",discrepancies));
    }
    @Test void binary16TiesAreEvenAndSubnormalsRetained() {
        assertEquals(0,AllcoolsRegionQuantifier.half(0x1.0p-25));
        assertEquals(0x1.0p-24,AllcoolsRegionQuantifier.half(Math.nextUp(0x1.0p-25)));
        assertEquals(0x1.0p-23,AllcoolsRegionQuantifier.half(3*0x1.0p-25));
        assertEquals(.5,AllcoolsRegionQuantifier.half(.5+0x1.0p-12));
        assertEquals(.5+0x1.0p-10,AllcoolsRegionQuantifier.half(.5+3*0x1.0p-12));
        assertEquals(0.89990234375,AllcoolsRegionQuantifier.half(.9));
    }
    @Test void allBinary16ProbabilitiesAreFixedPoints() {
        for(int bits=0;bits<=0x3c00;bits++) {
            int exp=(bits>>>10)&31,frac=bits&1023;
            double value=exp==0?Math.scalb((double)frac,-24):Math.scalb(1+frac/1024.,exp-15);
            assertEquals(value,AllcoolsRegionQuantifier.half(value));
        }
    }
    @Test void scoresUseUncappedCountsAndEmptyRegionsHaveHyperOne() {
        var r=AllcoolsRegionQuantifier.calculate(new long[]{6000000000L,0,3},new long[]{8000000000L,0,9},0,0,()->{});
        assertEquals(6000000003L,r.sumMc());assertEquals(8000000009L,r.sumCov());
        assertEquals(4294967295L,r.countsMc()[0]);assertEquals(4294967295L,r.countsCov()[0]);
        assertEquals(1,r.saturatedMc());assertEquals(1,r.saturatedCov());assertEquals(1,r.zeroCoverage());
        assertEquals(0,r.hypo()[1]);assertEquals(1,r.hyper()[1]);
    }
    @Test void resultDoesNotExposeMutableArrays() {
        long[] mc={1,2},cov={3,4};var r=AllcoolsRegionQuantifier.calculate(mc,cov,.9,.9,()->{});
        r.countsMc()[0]=99;r.hyper()[0]=99;assertEquals(1,r.countsMc()[0]);assertNotEquals(99,r.hyper()[0]);assertArrayEquals(new long[]{1,2},mc);assertArrayEquals(new long[]{3,4},cov);
    }
    @Test void sumOverflowAndInvalidCountsCutoffsAxesAndCancellationAreRefused() {
        assertThrows(ArithmeticException.class,()->AllcoolsRegionQuantifier.calculate(new long[]{Long.MAX_VALUE,1},new long[]{Long.MAX_VALUE,1},0,0,()->{}));
        assertThrows(IllegalArgumentException.class,()->AllcoolsRegionQuantifier.calculate(new long[0],new long[0],0,0,()->{}));
        assertThrows(IllegalArgumentException.class,()->AllcoolsRegionQuantifier.calculate(new long[]{1},new long[]{0},0,0,()->{}));
        assertThrows(IllegalArgumentException.class,()->AllcoolsRegionQuantifier.calculate(new long[]{-1},new long[]{0},0,0,()->{}));
        for(double cutoff:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})assertThrows(IllegalArgumentException.class,()->AllcoolsRegionQuantifier.calculate(new long[]{0},new long[]{0},cutoff,0,()->{}));
        assertThrows(IllegalStateException.class,()->AllcoolsRegionQuantifier.calculate(new long[]{0},new long[]{0},0,0,()->{throw new IllegalStateException("cancel");}));
    }
}
