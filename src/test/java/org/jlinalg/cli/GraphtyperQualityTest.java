package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jlinalg.genetics.GraphtyperQuality;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GraphtyperQualityTest {
    @Test void everyOriginalCppHeaderExpectationMatches()throws Exception {
        int count=0;
        try(var in=getClass().getResourceAsStream("/org/jlinalg/genetics/graphtyper-quality/original-quality-oracles.tsv")){
            assertNotNull(in);try(var reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
                for(String line;(line=reader.readLine())!=null;){String[] f=line.split("\t",-1);double actual;
                    if(f[0].equals("AA")){assertEquals(9,f.length);actual=GraphtyperQuality.alleleScore(d(f[1]),d(f[2]),d(f[3]),Long.parseLong(f[4]),d(f[5]),d(f[6]),Long.parseLong(f[7])).probability();}
                    else {assertEquals("LOGF",f[0]);assertEquals(10,f.length);actual=GraphtyperQuality.logisticFilter(d(f[1]),d(f[2]),d(f[3]),d(f[4]),d(f[5]),d(f[6]),Integer.parseInt(f[7]),Integer.parseInt(f[8])).probability();}
                    double expected=d(f[f.length-1]);assertEquals(expected,actual,expected==0||expected==1?0:16*Math.ulp(expected),"Original C++ oracle row "+count);count++;
                }
            }
        }
        assertEquals(712,count);
    }
    @Test void sourceBalanceBinsKeepTheirExactBoundarySides() {
        double[] edges={.85,.94,.98,.99};
        for(int i=0;i<edges.length;i++){
            assertEquals(i,GraphtyperQuality.alleleScore(edges[i],0,0,0,0,0,60).homozygousBalanceBin());
            assertEquals(i+1,GraphtyperQuality.alleleScore(Math.nextUp(edges[i]),0,0,0,0,0,60).homozygousBalanceBin());
        }
    }
    @Test void originalMappingQualityCapIsExposed() {
        var source=GraphtyperQuality.alleleScore(.99,.2,.3,40,20,.1,60);var high=GraphtyperQuality.alleleScore(.99,.2,.3,40,20,.1,100);
        assertEquals(source.probability(),high.probability());assertFalse(source.originalMappingQualityCapApplied());assertTrue(high.originalMappingQualityCapApplied());assertEquals(100,high.suppliedMappingQuality());assertEquals(60,high.originalUsedMappingQuality());
    }
    @Test void nonfiniteInputsCannotBecomeAdmittedProbabilities() {
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.alleleScore(bad,0,0,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.alleleScore(1,bad,0,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.alleleScore(1,0,bad,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.alleleScore(1,0,0,0,bad,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.alleleScore(1,0,0,0,0,bad,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(bad,0,0,0,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,bad,0,0,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,0,bad,0,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,0,0,bad,0,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,0,0,0,bad,0,0,0));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,0,0,0,0,bad,0,0));
        }
    }
    @Test void sourceBinsAndFinitePredictorAreRequired() {
        for(int bad:new int[]{-1,11}){
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,0,60,1,1,10,bad,5));
            assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(1,0,60,1,1,10,5,bad));
        }
        assertThrows(IllegalArgumentException.class,()->GraphtyperQuality.logisticFilter(Double.MAX_VALUE,0,60,1,1,10,5,5));
    }
    private static double d(String text){return Double.parseDouble(text);}
}
