/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class QtltoolsFunctionalDensityTest {
    private static GenomicIntervals.Interval interval(String chromosome, long start, long end, String strand) {
        return new GenomicIntervals.Interval(chromosome,start,end,"literal-id",strand);
    }
    private static final GenomicIntervals.Interval Q = interval("1",100,101,"+");
    private static final GenomicIntervals.Interval A = interval("1",100,105,".");

    @TestFactory List<DynamicTest> actualOriginalExecutableCases() throws Exception {
        var input=getClass().getResourceAsStream("/org/jlinalg/genetics/qtltools-fdensity/original-controls.tsv.gz");
        assertNotNull(input,"Restore the pinned actual original-executable fixture");
        var tests=new ArrayList<DynamicTest>();
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(input),StandardCharsets.UTF_8))) {
            String id=null;int window=0,width=0;boolean failed=false;
            var qtls=new ArrayList<GenomicIntervals.Interval>();var annotations=new ArrayList<GenomicIntervals.Interval>();var expected=new ArrayList<QtltoolsFunctionalDensity.Bin>();
            for(String line;(line=reader.readLine())!=null;) {
                String[] fields=line.split("\t",-1);
                switch(fields[0]) {
                    case "C" -> { assertNull(id);id=fields[1];window=Integer.parseInt(fields[2]);width=Integer.parseInt(fields[3]);failed=fields[4].equals("ERROR");qtls.clear();annotations.clear();expected.clear(); }
                    case "Q" -> qtls.add(new GenomicIntervals.Interval(fields[1],Long.parseLong(fields[2]),Long.parseLong(fields[3]),fields[4],fields[6]));
                    case "A" -> annotations.add(new GenomicIntervals.Interval(fields[1],Long.parseLong(fields[2]),Long.parseLong(fields[3]),"annotation-"+annotations.size(),"."));
                    case "B" -> expected.add(new QtltoolsFunctionalDensity.Bin(Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Integer.parseInt(fields[3])));
                    case "E" -> {
                        assertNotNull(id);var q=List.copyOf(qtls);var a=List.copyOf(annotations);var bins=List.copyOf(expected);int w=window,b=width;boolean error=failed;
                        tests.add(DynamicTest.dynamicTest(id,()->{
                            if(error)assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(q,a,w,b,100_000));
                            else { var result=QtltoolsFunctionalDensity.calculate(q,a,w,b,100_000);assertEquals(bins,result.bins());assertEquals(q.size(),result.qtlRows());assertEquals(a.size(),result.annotationRows()); }
                        }));id=null;
                    }
                    default -> fail("Unexpected original fixture record");
                }
            }
            assertNull(id,"Truncated original case");
        }
        assertEquals(576,tests.size());return tests;
    }

    @Test void countsQtlPresencesAndKeepsDuplicateQtlRows() {
        var result=QtltoolsFunctionalDensity.calculate(List.of(Q,Q),List.of(A,A,interval("1",101,104,".")),20,10,100);
        assertEquals(List.of(new QtltoolsFunctionalDensity.Bin(-20,-11,0),new QtltoolsFunctionalDensity.Bin(-10,-1,0),new QtltoolsFunctionalDensity.Bin(0,9,2),new QtltoolsFunctionalDensity.Bin(10,19,0)),result.bins());
        assertEquals(6,result.candidateOverlaps());assertEquals(3,result.chromosomeMappedAnnotationRows());
    }

    @Test void partialLastBinDoesNotExpandTheCandidateWindow() {
        var outside=interval("1",111,113,".");
        var onlyOutside=QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(outside),10,6,100);
        assertEquals(new QtltoolsFunctionalDensity.Bin(8,13,0),onlyOutside.bins().get(3));assertEquals(0,onlyOutside.candidateOverlaps());
        var touching=QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(interval("1",110,112,"."),outside),10,6,100);
        assertEquals(new QtltoolsFunctionalDensity.Bin(8,13,1),touching.bins().get(3));assertEquals(1,touching.candidateOverlaps());
    }

    @Test void qtlEndIsNotTheAnchorAndDotFollowsOriginalPlusHandling() {
        var expected=QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(A),20,10,100).bins();
        assertEquals(expected,QtltoolsFunctionalDensity.calculate(List.of(interval("1",100,1000,".")),List.of(A),20,10,100).bins());
        var reversed=QtltoolsFunctionalDensity.calculate(List.of(interval("1",100,101,"-")),List.of(A),20,10,100).bins();
        assertEquals(List.of(0,1,1,0),reversed.stream().map(QtltoolsFunctionalDensity.Bin::qtlCount).toList());
    }

    @Test void doesNotNormalizeChromosomeLabels() {
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(interval("chr1",100,105,".")),20,10,100));
    }

    @Test void preservesOriginalSmallTreeOrderGuardWithoutChangingInput() {
        var far=interval("1",1000,1001,".");
        var unsorted=QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(far,A),20,10,100);
        assertTrue(unsorted.bins().stream().allMatch(bin->bin.qtlCount()==0));assertEquals(1,unsorted.sourceOrderGuardedQtlRows());
        var sorted=QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(A,far),20,10,100);
        assertEquals(1,sorted.bins().get(2).qtlCount());assertEquals(0,sorted.sourceOrderGuardedQtlRows());
    }

    @Test void rejectsMissingRowsAndNullElements() {
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(null,List.of(A),20,10,100));
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(Q),null,20,10,100));
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(),List.of(A),20,10,100));
        var invalid=new ArrayList<GenomicIntervals.Interval>();invalid.add(null);
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(invalid,List.of(A),20,10,100));
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(Q),invalid,20,10,100));
    }

    @Test void refusesInvalidParametersCapacityAndOriginalIntegerOverflow() {
        for(int[] values:new int[][]{{0,10,100},{20,0,100},{20,10,0},{20,10,1},{20,10,1_000_001},{1_100_000_000,Integer.MAX_VALUE,100}})
            assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(A),values[0],values[1],values[2]));
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(interval("1",Integer.MAX_VALUE-1L,Integer.MAX_VALUE,"+")),List.of(A),1,1,100));
        assertThrows(IllegalArgumentException.class,()->QtltoolsFunctionalDensity.calculate(List.of(Q),List.of(interval("1",0,Long.MAX_VALUE,".")),1,1,100));
    }

    @Test void resultBinsAreImmutableAndCallerListOrderIsPreserved() {
        var annotations=new ArrayList<>(List.of(interval("1",120,125,"."),A));var before=List.copyOf(annotations);
        var result=QtltoolsFunctionalDensity.calculate(List.of(Q),annotations,30,10,100);
        assertEquals(before,annotations);assertThrows(UnsupportedOperationException.class,()->result.bins().clear());
    }
}
