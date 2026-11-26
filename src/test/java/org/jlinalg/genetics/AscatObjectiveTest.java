/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AscatObjectiveTest {
    private static double number(String bits){return Double.longBitsToDouble(Long.parseUnsignedLong(bits,16));}
    private static BufferedReader fixture(String name)throws Exception {
        var stream=AscatObjectiveTest.class.getResourceAsStream("/org/jlinalg/genetics/ascat/"+name+".gz");
        assertNotNull(stream);return new BufferedReader(new InputStreamReader(new GZIPInputStream(stream),StandardCharsets.UTF_8));
    }
    private static Map<String,List<AscatObjective.Segment>> segments()throws Exception {
        var input=new LinkedHashMap<String,List<double[]>>();
        try(var reader=fixture("input.tsv")) {
            assertEquals("case\tordinal\tlogR_bits\tBAF_bits",reader.readLine());String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1);assertEquals(4,f.length);
                var rows=input.computeIfAbsent(f[0],k->new ArrayList<>());
                assertEquals(rows.size()+1,Integer.parseInt(f[1]));rows.add(new double[]{number(f[2]),number(f[3])});
            }
        }
        var result=new LinkedHashMap<String,List<AscatObjective.Segment>>();
        for(var entry:input.entrySet()) {
            var rows=entry.getValue();double[] r=new double[rows.size()],b=new double[rows.size()];
            for(int i=0;i<r.length;i++){r[i]=rows.get(i)[0];b[i]=rows.get(i)[1];}
            result.put(entry.getKey(),AscatObjective.segments(r,b));
        }
        assertEquals(6,result.size());return result;
    }

    @Test void completeOriginalNamespaceSegmentReferencesPreserveBitsAndMissingRowOrder()throws Exception {
        var segments=segments();var counts=new LinkedHashMap<String,Integer>();int rows=0;
        try(var reader=fixture("segments.tsv")) {
            assertEquals("case\tordinal\tlogR_bits\tBAF_bits\tlength",reader.readLine());String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1);assertEquals(5,f.length);int ordinal=Integer.parseInt(f[1]);
                assertEquals(counts.getOrDefault(f[0],0)+1,ordinal);counts.put(f[0],ordinal);
                var actual=segments.get(f[0]).get(ordinal-1);
                assertEquals(Double.doubleToRawLongBits(number(f[2])),Double.doubleToRawLongBits(actual.logR()));
                assertEquals(Double.doubleToRawLongBits(number(f[3])),Double.doubleToRawLongBits(actual.baf()));
                assertEquals(Long.parseLong(f[4]),actual.length());rows++;
            }
        }
        assertEquals(22,rows);segments.forEach((name,value)->assertEquals(value.size(),counts.get(name)));
    }

    @Test void all58176OriginalDistanceCellsFromBothReleasesAgree()throws Exception {
        var segments=segments();int rows=0;
        try(var reader=fixture("distance.tsv")) {
            assertEquals("case\tpsi_bits\trho_bits\tgamma_bits\tdistance_bits",reader.readLine());String line;
            while((line=reader.readLine())!=null) {
                String[] f=line.split("\t",-1);assertEquals(5,f.length);double expected=number(f[4]);
                double actual=AscatObjective.distance(segments.get(f[0]),number(f[3]),number(f[1]),number(f[2]));
                assertEquals(expected,actual,2e-11+2e-12*Math.abs(expected),line);rows++;
            }
        }
        assertEquals(58176,rows);
    }

    @Test void explicitGridPreservesAxesAndBalancedWeight() {
        var s=List.of(new AscatObjective.Segment(0,.5,20));
        var grid=AscatObjective.grid(s,1,new double[]{1.5,2},new double[]{.5,1},()->{});
        assertEquals(2,grid.length);assertEquals(2,grid[0].length);
        assertEquals(.0625,grid[0][0],1e-15);assertEquals(.0625,grid[0][1],1e-15);
        assertEquals(0,grid[1][0]);assertEquals(0,grid[1][1]);
    }

    @Test void invalidOrUnreportablePopulationsAreRefusedRatherThanRepaired() {
        assertThrows(IllegalArgumentException.class,()->AscatObjective.segments(new double[0],new double[0]));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.segments(new double[]{Double.NaN},new double[]{.5}));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.segments(new double[]{0},new double[]{.5,.5}));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.segments(new double[]{Double.POSITIVE_INFINITY},new double[]{.5}));
        assertThrows(IllegalArgumentException.class,()->new AscatObjective.Segment(0,1.1,1));
        assertThrows(IllegalArgumentException.class,()->new AscatObjective.Segment(0,.5,0));
        var s=List.of(new AscatObjective.Segment(0,.5,1));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.distance(s,0,2,1));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.distance(s,1,2,0));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.distance(s,1,2,1.06));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.grid(s,1,new double[]{2,1},new double[]{.5},()->{}));
        assertThrows(IllegalArgumentException.class,()->AscatObjective.distance(List.of(new AscatObjective.Segment(2000,.8,1)),1,2,.5));
    }

    @Test void cancellationCannotReturnACompleteGridOrSegmentPopulation() {
        Runnable stop=()->{throw new CancellationException("test cancellation");};
        assertThrows(CancellationException.class,()->AscatObjective.segments(new double[]{0},new double[]{.5},stop));
        assertThrows(CancellationException.class,()->AscatObjective.grid(List.of(new AscatObjective.Segment(0,.5,1)),1,new double[]{2},new double[]{.5},stop));
    }
}
