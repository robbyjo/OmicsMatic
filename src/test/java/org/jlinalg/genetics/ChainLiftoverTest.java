/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.GenomicIntervals.Interval;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChainLiftoverTest {
    @TempDir Path directory;
    private static final String GAPPED="chain 1000 chrS 1000 + 100 350 chrT 2000 + 500 740 1\n100 50 40\n100\n";
    private Path chain(String text)throws Exception {Path p=directory.resolve("test.chain");Files.writeString(p,text);return p;}
    private ChainLiftover engine(String text)throws Exception{return new ChainLiftover(chain(text),Map.of("chrS",1000L),Map.of("chrT",2000L));}
    private Interval query(long start,long end,String strand){return new Interval("chrS",start,end,"q",strand);}
    @Test void handCalculatedForwardBlocksAndGapFractionsUseExactThresholds()throws Exception {
        var mapper=engine(GAPPED);var point=mapper.map(query(110,120,"+"),1);assertEquals(ChainLiftover.Status.MAPPED,point.status());assertEquals(new Interval("chrT",510,520,"q","+"),point.target());
        var spanning=mapper.map(query(100,350,"+"),.8);assertEquals(new Interval("chrT",500,740,"q","+"),spanning.target());
        var threshold=mapper.map(query(100,350,"+"),Math.nextUp(.8));assertEquals(ChainLiftover.Status.BELOW_MIN_MATCH,threshold.status());assertNull(threshold.target());assertFalse(threshold.diagnostics().isEmpty());
        assertEquals(ChainLiftover.Status.MAPPED,mapper.map(query(100,350,"+"),.8).status());assertEquals(ChainLiftover.Status.BELOW_MIN_MATCH,mapper.map(query(100,350,"+"),.81).status());
        assertEquals(2,mapper.summary().blocks());assertEquals(1,mapper.summary().chains());
    }
    @Test void reverseChainUsesHalfOpenEndpointsAndXorsKnownStrands()throws Exception {
        var mapper=engine("chain 1000 chrS 1000 + 100 200 chrT 2000 - 300 400 1\n100\n");
        assertEquals(new Interval("chrT",1680,1690,"q","-"),mapper.map(query(110,120,"+"),1).target());
        assertEquals(new Interval("chrT",1680,1690,"q","+"),mapper.map(query(110,120,"-"),1).target());
        assertEquals(new Interval("chrT",1680,1690,"q","."),mapper.map(query(110,120,"."),1).target());
    }
    @Test void multipleHitsAndEntirelyUnalignedGapsNeverProduceAPreferredHit()throws Exception {
        var mapper=engine(GAPPED+"\nchain 1 chrS 1000 + 110 120 chrT 2000 + 1000 1010 2\n10\n");
        var ambiguous=mapper.map(query(110,120,"+"),1);assertEquals(ChainLiftover.Status.NOT_UNIQUELY_MAPPED,ambiguous.status());assertNull(ambiguous.target());assertEquals(2,ambiguous.diagnostics().size());
        assertEquals(ChainLiftover.Status.NOT_UNIQUELY_MAPPED,mapper.map(query(200,250,"+"),.5).status());assertEquals(ChainLiftover.Status.NO_CHAIN,mapper.map(query(0,1,"+"),1).status());
    }
    @Test void gzipBytesHaveIdenticalMappingAndReferenceDirectionIsStrict()throws Exception {
        Path plain=chain(GAPPED),compressed=directory.resolve("no-extension");try(var gzip=new GZIPOutputStream(Files.newOutputStream(compressed))){gzip.write(Files.readAllBytes(plain));}
        var mapper=new ChainLiftover(compressed,Map.of("chrS",1000L),Map.of("chrT",2000L));assertEquals(new Interval("chrT",510,520,"q","+"),mapper.map(query(110,120,"+"),1).target());
        assertTrue(assertThrows(java.io.IOException.class,()->new ChainLiftover(plain,Map.of("chrT",2000L),Map.of("chrS",1000L))).getMessage().contains("source/t"));
        assertTrue(assertThrows(java.io.IOException.class,()->new ChainLiftover(plain,Map.of("chrS",1000L),Map.of("chrT",1999L))).getMessage().contains("length differs"));
    }
    @Test void malformedChainGeometryAndDuplicateIdsFailWithoutChangingSource()throws Exception {
        String malformed=GAPPED.replace("100 50 40","100 49 40");Path p=chain(malformed);assertTrue(assertThrows(java.io.IOException.class,()->ChainLiftover.inspect(p)).getMessage().contains("endpoints"));assertEquals(malformed,Files.readString(p));
        Path duplicate=chain(GAPPED+"\n"+GAPPED);assertTrue(assertThrows(java.io.IOException.class,()->ChainLiftover.inspect(duplicate)).getMessage().contains("Duplicate chain ID"));
        Path incomplete=chain(GAPPED.substring(0,GAPPED.lastIndexOf("100\n")));assertTrue(assertThrows(java.io.IOException.class,()->ChainLiftover.inspect(incomplete)).getMessage().contains("unterminated"));
    }
    @Test void integerBoundaryAndInvalidQueriesDoNotOverflowOrGuessAliases()throws Exception {
        long max=Integer.MAX_VALUE;Path p=chain("chain 1 chrS "+max+" + "+(max-10)+" "+max+" chrT 2000 + 100 110 1\n10\n");var mapper=new ChainLiftover(p,Map.of("chrS",max),Map.of("chrT",2000L));assertEquals(new Interval("chrT",100,110,"q","+"),mapper.map(query(max-10,max,"+"),1).target());
        assertThrows(IllegalArgumentException.class,()->mapper.map(query(max-1,max+1,"+"),1));assertThrows(IllegalArgumentException.class,()->mapper.map(new Interval("S",1,2,"q","+"),1));
        for(double value:new double[]{0,-1,1.1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->mapper.map(query(110,120,"+"),value));
    }
    @Test void realUcscChainsAgreeWithIndependentPrimaryDbSnpCoordinatesBothDirections()throws Exception {
        String supplied=System.getenv("JLINALG_LIFTOVER_REFERENCE");assumeTrue(supplied!=null,"Set JLINALG_LIFTOVER_REFERENCE to the retained official chains/reference dictionaries for the independent public-coordinate test");Path root=Path.of(supplied);
        Map<String,Long> hg19=sizes(root.resolve("hg19.chrom.sizes")),hg38=sizes(root.resolve("hg38.chrom.sizes"));
        var forward=new ChainLiftover(root.resolve("hg19ToHg38.over.chain.gz"),hg19,hg38);var reverse=new ChainLiftover(root.resolve("hg38ToHg19.over.chain.gz"),hg38,hg19);
        Path fixture=Path.of(getClass().getResource("/liftover/dbsnp-coordinate-reference.tsv").toURI());var lines=Files.readAllLines(fixture);
        for(String line:lines.subList(1,lines.size())){String[] f=line.split("\t");long from=Long.parseLong(f[2]),to=Long.parseLong(f[3]);var original=new Interval(f[1],from,from+1,"rs"+f[0],"+");var expected=new Interval(f[1],to,to+1,"rs"+f[0],"+");assertEquals(expected,forward.map(original,1).target(),f[0]);assertEquals(original,reverse.map(expected,1).target(),f[0]);}
    }
    private Map<String,Long> sizes(Path path)throws Exception {var values=new java.util.LinkedHashMap<String,Long>();for(String line:Files.readAllLines(path)){String[] f=line.split("\t");values.put(f[0],Long.parseLong(f[1]));}return values;}
}
