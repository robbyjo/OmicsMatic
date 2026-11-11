/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jlinalg.singlecell.CiceroPredictionSubset.*;
import org.jlinalg.singlecell.CiceroPredictionSubset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CiceroPredictionTest {
    @Test void strictHalfOpenBoundariesAndOverlapLength() {
        var index=new CiceroPredictionSubset.Index(List.of(new Peak("a",new Interval("chr1",10,20))));
        assertFalse(index.match(new Interval("chr1",20,30),1,new Budget(100)).accessible());
        assertTrue(index.match(new Interval("chr1",19,30),1,new Budget(100)).accessible());
        assertFalse(index.match(new Interval("chr1",19,30),2,new Budget(100)).accessible());
        assertFalse(index.match(new Interval("1",10,20),1,new Budget(100)).accessible());
        assertEquals(new Hit(true,"a",1),index.match(new Interval("chr1",10,20),10,new Budget(100)));
    }
    @Test void firstWitnessUsesInputOrderAndRetainsDuplicateCoordinates() {
        var index=new CiceroPredictionSubset.Index(List.of(new Peak("NA",new Interval("β",15,25)),new Peak(" original\" ",new Interval("β",10,25)),new Peak("z",new Interval("β",15,25))));
        assertEquals(new Hit(true,"NA",3),index.match(new Interval("β",16,20),1,new Budget(100)));
    }
    @Test void completeTreeMatchesIndependentBruteForce() {
        var random=new Random(20261107);
        var peaks=new ArrayList<Peak>();
        for(int i=0;i<1500;i++){long start=random.nextInt(5000);peaks.add(new Peak("p"+i,new Interval(i%3==0?"A":"B",start,start+1+random.nextInt(150))));}
        var index=new CiceroPredictionSubset.Index(peaks);var budget=new Budget(10000000);
        for(int q=0;q<600;q++){
            long start=random.nextInt(5100),end=start+1+random.nextInt(200),minimum=1+random.nextInt(50);String chr=q%3==0?"A":q%3==1?"B":"missing";int count=0;String first="";
            for(Peak peak:peaks)if(peak.interval().chromosome().equals(chr)&&Math.max(0,Math.min(end,peak.interval().end())-Math.max(start,peak.interval().start()))>=minimum){if(count==0)first=peak.id();count++;}
            assertEquals(new Hit(count>0,first,count),index.match(new Interval(chr,start,end),minimum,budget));
        }
    }
    @Test void fullLongCoordinateDomainHasNoEndpointOverflow() {
        var index=new CiceroPredictionSubset.Index(List.of(new Peak("max",new Interval("X",Long.MAX_VALUE-1,Long.MAX_VALUE)),new Peak("all",new Interval("X",0,Long.MAX_VALUE))));
        assertEquals(new Hit(true,"max",2),index.match(new Interval("X",Long.MAX_VALUE-1,Long.MAX_VALUE),1,new Budget(100)));
        assertEquals(new Hit(true,"all",1),index.match(new Interval("X",0,Long.MAX_VALUE),Long.MAX_VALUE,new Budget(100)));
    }
    @Test void malformedInputsAndDuplicateIdentifiersRefused() {
        assertThrows(IllegalArgumentException.class,()->new Interval("chr1",0,0));
        assertThrows(IllegalArgumentException.class,()->new Interval("chr1",-1,2));
        assertThrows(IllegalArgumentException.class,()->new Interval("x\ty",0,2));
        assertThrows(IllegalArgumentException.class,()->new CiceroPredictionSubset.Index(List.of(new Peak("a",new Interval("x",0,1)),new Peak("a",new Interval("x",2,3)))));
        var index=new CiceroPredictionSubset.Index(List.of());
        assertThrows(IllegalArgumentException.class,()->index.match(new Interval("x",0,1),0,new Budget(10)));
    }
    @Test void capacityAndInterruptionRefuseWork() {
        var index=new CiceroPredictionSubset.Index(List.of(new Peak("a",new Interval("x",0,1)),new Peak("b",new Interval("x",0,2))));
        assertThrows(IllegalStateException.class,()->index.match(new Interval("x",0,2),1,new Budget(1)));
        try{Thread.currentThread().interrupt();assertThrows(IllegalStateException.class,()->index.match(new Interval("x",0,2),1,new Budget(100)));}finally{Thread.interrupted();}
    }
    @Test void cliPreservesOriginalRowsAndOmitsExpressionFilter() throws Exception {
        Path root=Files.createTempDirectory("cicero-prediction-test-");
        try{
            Files.writeString(root.resolve("predictions.tsv"),"prediction_id\tsource_peak_id\ttarget_id\tcoaccess\texpressed\r\n p\"β \ts\tunmapped-gene\tNA\tfalse\r\np2\tt\tg2\tNaN\ttrue",StandardCharsets.UTF_8);
            Files.writeString(root.resolve("peaks.tsv"),"peak_id\tchromosome\tstart\tend\ns\tchr1\t0\t10\nt\tchr1\t10\t20\n");
            Files.writeString(root.resolve("accessible.tsv"),"cell_type\tpeak_id\tchromosome\tstart\tend\nβ\tNA\tchr1\t9\t10\n");
            var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","SUBSET");request.put("predictions","predictions.tsv");request.put("peak_metadata","peaks.tsv");request.put("accessible_peaks","accessible.tsv");request.put("out","result");request.put("coordinate_system","zero-based-half-open");request.put("accessibility_scope","source-only");request.put("cell_types",List.of("β","empty"));request.put("minimum_overlap_bp",1);
            NextflowFiles.writeJson(root.resolve("request.json"),request);
            assertEquals(0,CiceroPredictionCli.run(new String[]{"--request",root.resolve("request.json").toString()},System.out,System.err));
            assertEquals("prediction_id\tsource_peak_id\ttarget_id\tcoaccess\texpressed\r\n p\"β \ts\tunmapped-gene\tNA\tfalse\r\n",Files.readString(root.resolve("result/results/cell-type-0001.tsv")));
            assertEquals("prediction_id\tsource_peak_id\ttarget_id\tcoaccess\texpressed\r\n",Files.readString(root.resolve("result/results/cell-type-0002.tsv")));
            assertTrue(Files.readString(root.resolve("result/membership.tsv")).contains("not-tested"));
            assertTrue(Files.readString(root.resolve("result/WARNINGS.md")).contains("EMPTY SUBSET"));
            assertArrayEquals(Files.readAllBytes(root.resolve("request.json")),Files.readAllBytes(root.resolve("result/inputs/request.json")));
            assertEquals(2,CiceroPredictionCli.run(new String[]{"--request",root.resolve("request.json").toString()},System.out,System.err));
        }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
