/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SignacFragmentsTest {
    @TempDir Path directory;
    private static final Map<String,byte[]> FIXTURE=fixture();
    private static Map<String,byte[]> fixture(){try{
        byte[] bytes;try(var in=SignacFragmentsTest.class.getResourceAsStream("/org/jlinalg/sequence/signac-fragments/original-controls.zip")){assertNotNull(in);bytes=in.readAllBytes();}
        assertEquals("c2fca62598dc0f28003eae614e4a08704b8319ad743590cba845b2a6a39f0b77",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Map<String,byte[]> files=new HashMap<>();try(var z=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=z.getNextEntry())!=null){assertFalse(e.isDirectory());assertFalse(e.getName().contains(".."));byte[] data=z.readNBytes(8_000_001);assertTrue(data.length<=8_000_000);assertNull(files.put(e.getName(),data));}}
        assertEquals(990,files.size());return Map.copyOf(files);
    }catch(Exception error){throw new ExceptionInInitializerError(error);}}
    private static String text(String name){return new String(Objects.requireNonNull(FIXTURE.get(name),name),StandardCharsets.UTF_8);}
    private static List<String> lines(String name){return text(name).lines().toList();}
    private static List<String> csv(String text){return text.isEmpty()?List.of():List.of(text.split(",",-1));}
    private static long integer(String value){return new BigDecimal(value).longValueExact();}
    private static int[] ints(String text){return csv(text.strip()).stream().mapToInt(Integer::parseInt).toArray();}
    private static long[] longs(String text){return csv(text.strip()).stream().mapToLong(SignacFragmentsTest::integer).toArray();}
    private static double number(String text){return switch(text){case "Inf"->Double.POSITIVE_INFINITY;case "NA","NaN"->Double.NaN;default->Double.parseDouble(text);};}
    private static SignacFragments.Limits limits(int memory){return new SignacFragments.Limits(10000,10000,100000,100000,1000000,10000000,4094,memory);}
    private Path input(String name,String format)throws IOException{
        String suffix=switch(format){case "plain"->".tsv";case "bgzf"->".tsv.gz";case "gzip"->".ordinary.gz";default->throw new AssertionError();};Path path=directory.resolve(name+suffix);Files.write(path,FIXTURE.get("input/"+name+suffix));return path;
    }
    private List<SignacFragments.Source> sources(String caseId,String format)throws IOException{
        List<SignacFragments.Source> sources=new ArrayList<>();for(String line:lines("matrix/"+caseId+"/sources.tsv")){
            String[] f=line.split("\t",-1);List<SignacFragments.Cell> map=null;
            if(!f[1].equals("NULL")){map=new ArrayList<>();for(String pair:csv(f[1])){String[] p=pair.split("=",-1);map.add(new SignacFragments.Cell(p[0],p[1]));}}
            sources.add(new SignacFragments.Source(input(f[0],format),map));
        }return sources;
    }
    private List<SignacFragments.Region> regions(String id){return lines("matrix/"+id+"/regions.tsv").stream().map(line->{String[] f=line.split("\t");return new SignacFragments.Region(f[0],Integer.parseInt(f[1]),Integer.parseInt(f[2]));}).toList();}
    static Stream<Arguments> countCases(){List<Arguments> cases=new ArrayList<>();for(String id:lines("count-ids.tsv"))for(String format:List.of("plain","gzip","bgzf"))cases.add(Arguments.of(id,format));assertEquals(189,cases.size());return cases.stream();}
    @ParameterizedTest(name="original CountFragments {0} {1}") @MethodSource("countCases")
    void originalCountsOrderSamplingAndSupportSumsMatch(String id,String format)throws Exception{
        String[] settings=lines("count/"+id+"/settings.tsv").get(0).split("\t",-1);List<Path> paths=new ArrayList<>();for(String file:csv(settings[0]))paths.add(input(file,format));
        var result=SignacFragments.countFragments(paths,settings[1].equals("NULL")?null:csv(settings[1]),Long.parseLong(settings[2]),limits(1000),()->false);
        List<String> expected=lines("count/"+id+"/expected.tsv");assertEquals(expected.size(),result.counts().size());
        for(int row=0;row<expected.size();row++){String[] f=expected.get(row).split("\t",-1);var actual=result.counts().get(row);assertEquals(f[0],actual.barcode());assertEquals(integer(f[1]),actual.frequencyCount());assertEquals(integer(f[2]),actual.mononucleosomal());assertEquals(integer(f[3]),actual.nucleosomeFree());assertEquals(integer(f[4]),actual.readsCount());}
        if(settings[2].equals("0"))assertTrue(result.scans().stream().noneMatch(SignacFragments.Scan::prefixOnly));
        assertEquals(!format.equals("plain"),result.scans().get(0).compressed());
    }
    static Stream<Arguments> matrixCases(){List<Arguments> cases=new ArrayList<>();for(String id:lines("matrix-ids.tsv"))for(String format:List.of("plain","gzip","bgzf"))for(int memory:new int[]{16,100000})cases.add(Arguments.of(id,format,memory));assertEquals(384,cases.size());return cases.stream();}
    @ParameterizedTest(name="original FeatureMatrix {0} {1} sort={2}") @MethodSource("matrixCases")
    void originalFeatureMatrixMatchesIncludingChunkAndMissingChromosomeBehavior(String id,String format,int memory)throws Exception{
        String[] settings=lines("matrix/"+id+"/settings.tsv").get(0).split("\t",-1);var sources=sources(id,format);var regions=regions(id);List<String> cells=settings[0].equals("NULL")?null:csv(settings[0]);Path scratch=directory.resolve("owned-sort");
        if(FIXTURE.containsKey("matrix/"+id+"/ERROR.txt")){assertThrows(IOException.class,()->SignacFragments.featureMatrix(sources,regions,cells,Boolean.parseBoolean(settings[1]),Integer.parseInt(settings[2]),scratch,limits(memory),()->false));assertFalse(Files.exists(scratch));return;}
        var result=SignacFragments.featureMatrix(sources,regions,cells,Boolean.parseBoolean(settings[1]),Integer.parseInt(settings[2]),scratch,limits(memory),()->false);
        assertEquals(lines("matrix/"+id+"/features.tsv"),result.features());assertEquals(lines("matrix/"+id+"/cells.tsv"),result.cells());
        assertArrayEquals(ints(text("matrix/"+id+"/pointers.tsv")),result.pointers());assertArrayEquals(ints(text("matrix/"+id+"/indices.tsv")),result.rows());assertArrayEquals(longs(text("matrix/"+id+"/values.tsv")),result.values());
        assertFalse(Files.exists(scratch));assertTrue(result.scans().stream().noneMatch(SignacFragments.Scan::prefixOnly));
        if(result.cells().isEmpty())assertThrows(IllegalStateException.class,result::asCounts);else assertEquals(result.cells(),result.asCounts().cells());
    }
    static Stream<Arguments> qcCases(){List<Arguments> cases=new ArrayList<>();for(String id:lines("qc-ids.tsv"))for(String format:List.of("plain","gzip","bgzf"))cases.add(Arguments.of(id,format));assertEquals(99,cases.size());return cases.stream();}
    private static void same(String kind,String raw,SignacFragmentQc.Value value){assertEquals(SignacFragmentQc.NumberKind.valueOf(kind),value.kind());double expected=number(raw);if(!Double.isFinite(expected))assertEquals(expected,value.value());else assertEquals(expected,value.value(),Math.max(Math.ulp(expected)*8,Math.abs(expected)*1e-15));}
    @ParameterizedTest(name="original NucleosomeSignal {0} {1}") @MethodSource("qcCases")
    void originalRatiosEcdfMissingKindsAndSampledBarcodesMatch(String id,String format)throws Exception{
        String[] settings=lines("qc/"+id+"/settings.tsv").get(0).split("\t",-1);var sources=sources(settings[0],format);List<String> cells=csv(settings[2]);long max=Long.parseLong(settings[1]);
        if(FIXTURE.containsKey("qc/"+id+"/ERROR.txt")){assertThrows(IOException.class,()->SignacFragmentQc.nucleosomeSignal(sources,cells,max,limits(1000),()->false));return;}
        var result=SignacFragmentQc.nucleosomeSignal(sources,cells,max,limits(1000),()->false);List<String> expected=lines("qc/"+id+"/expected.tsv");assertEquals(expected.size(),result.scores().size());
        for(int row=0;row<expected.size();row++){String[] f=expected.get(row).split("\t",-1);var actual=result.scores().get(row);assertEquals(f[0],actual.cell());same(f[1],f[2],actual.ratio());same(f[3],f[4],actual.percentile());}
    }
    static Stream<Arguments> binaryCases(){assertEquals(36,lines("binary-ids.tsv").size());return lines("binary-ids.tsv").stream().map(Arguments::of);}
    @ParameterizedTest(name="original sparse BinarizeCounts {0}") @MethodSource("binaryCases")
    void sourceSparseBinarizationIncludesStoredZeros(String id){
        String p="binary/"+id+"/";double[] values=csv(text(p+"input.tsv").strip()).stream().mapToDouble(Double::parseDouble).toArray();
        var input=new SignacNormalization.Counts(lines(p+"features.tsv"),lines(p+"cells.tsv"),ints(text(p+"pointers.tsv")),ints(text(p+"indices.tsv")),values);
        var result=SignacFragmentQc.binarizeCsc(input,()->false);assertArrayEquals(csv(text(p+"expected.tsv").strip()).stream().mapToDouble(Double::parseDouble).toArray(),result.counts().values());assertArrayEquals(values,input.values());assertEquals(Arrays.stream(values).filter(x->x==0).count(),result.storedZerosChanged());assertEquals(input.features(),result.counts().features());assertArrayEquals(input.columnPointers(),result.counts().columnPointers());assertArrayEquals(input.rowIndices(),result.counts().rowIndices());
    }
    @Test void all20100OriginalRRankFractionsUseTheSameTwoDigitRounding(){int n=0;for(String row:lines("original/rounding.tsv").subList(1,20101)){String[] f=row.split("\t");double x=(double)Integer.parseInt(f[1])/Integer.parseInt(f[0]);assertEquals(Double.parseDouble(f[3]),SignacFragmentQc.roundPercentile(x));n++;}assertEquals(20100,n);}
    private Path plain(String data)throws IOException{Path path=directory.resolve("hand.tsv");Files.writeString(path,data,StandardCharsets.UTF_8);return path;}
    @Test void independentLengthBoundariesAndPCRSupportRemainDifferentStatistics()throws Exception{
        Path path=plain("chr1\t0\t146\tA\t10\nchr1\t200\t347\tA\t10\nchr1\t400\t548\tA\t10\nchr1\t600\t893\tA\t10\nchr1\t900\t1194\tA\t10\n");var c=SignacFragments.countFragments(List.of(path),null,0,limits(100),()->false).counts().get(0);
        assertEquals(5,c.frequencyCount());assertEquals(2,c.mononucleosomal());assertEquals(1,c.nucleosomeFree());assertEquals(50,c.readsCount());
    }
    @Test void wholeFragmentsOverlapEvenWhenNeitherEndpointIsInTheFeature()throws Exception{
        Path path=plain("chr1\t0\t500\tA\t50\nchr1\t110\t120\tA\t7\n");var result=SignacFragments.featureMatrix(List.of(new SignacFragments.Source(path,null)),List.of(new SignacFragments.Region("chr1",100,110)),List.of("A"),false,2000,directory.resolve("sort"),limits(2),()->false);assertArrayEquals(new long[]{1},result.values());
    }
    @Test void cellDiscoveryUsesFeatureOrderRatherThanPhysicalFragmentOrder()throws Exception{
        Path path=plain("chr1\t0\t10\tA\t1\nchr1\t100\t110\tB\t1\n");var result=SignacFragments.featureMatrix(List.of(new SignacFragments.Source(path,null)),List.of(new SignacFragments.Region("chr1",101,110),new SignacFragments.Region("chr1",1,10)),null,false,2000,directory.resolve("sort"),limits(2),()->false);assertEquals(List.of("B","A"),result.cells());
    }
    @Test void sampledLinesIncludeBarcodesOutsideTheWhitelist()throws Exception{
        Path path=plain("chr1\t0\t1\tB\t9\nchr1\t2\t3\tA\t8\n");var result=SignacFragments.countFragments(List.of(path),List.of("A"),1,limits(100),()->false);assertEquals(0,result.counts().get(0).frequencyCount());assertTrue(result.scans().get(0).prefixOnly());assertTrue(result.warnings().stream().anyMatch(w->w.code().equals("PREFIX_ONLY")));
    }
    @Test void noAutomaticPoolingOfAmbiguousObjectCellsInNucleosomeMetadata()throws Exception{
        Path path=plain("chr1\t0\t1\tA\t1\n");var source=new SignacFragments.Source(path,List.of(new SignacFragments.Cell("cell","A")));
        assertThrows(IOException.class,()->SignacFragmentQc.nucleosomeSignal(List.of(source,source),List.of("cell"),0,limits(100),()->false));
        assertThrows(IllegalArgumentException.class,()->new SignacFragments.Source(path,List.of(new SignacFragments.Cell("one","A"),new SignacFragments.Cell("two","A"))));
    }
    @Test void counterAllowsUnsortedInputButIndexedSourceCompatibilityRequiresSortedRecords()throws Exception{
        Path path=plain("chr1\t100\t110\tA\t1\nchr1\t0\t1\tA\t1\n");assertEquals(2,SignacFragments.countFragments(List.of(path),null,0,limits(100),()->false).counts().get(0).frequencyCount());Path scratch=directory.resolve("sort");assertThrows(IOException.class,()->SignacFragments.featureMatrix(List.of(new SignacFragments.Source(path,null)),List.of(new SignacFragments.Region("chr1",1,200)),null,false,2000,scratch,limits(2),()->false));assertFalse(Files.exists(scratch));
    }
    @Test void fullGzipCRCFailuresAreRejected()throws Exception{
        var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write("chr1\t0\t1\tA\t1\n".getBytes(StandardCharsets.UTF_8));}byte[] bad=bytes.toByteArray();bad[bad.length-8]^=1;Path path=directory.resolve("bad.gz");Files.write(path,bad);assertThrows(IOException.class,()->SignacFragments.countFragments(List.of(path),null,0,limits(100),()->false));
    }
    @Test void existingScratchAndCallerInputsAreNeverReplaced()throws Exception{
        Path path=plain("chr1\t0\t1\tA\t1\n");byte[] before=Files.readAllBytes(path);Path scratch=Files.createDirectory(directory.resolve("owned"));Path protectedFile=scratch.resolve("caller");Files.writeString(protectedFile,"keep");assertThrows(IOException.class,()->SignacFragments.featureMatrix(List.of(new SignacFragments.Source(path,null)),List.of(new SignacFragments.Region("chr1",1,2)),null,false,2000,scratch,limits(2),()->false));assertEquals("keep",Files.readString(protectedFile));assertArrayEquals(before,Files.readAllBytes(path));
    }
    @Test void cancellationIsObservedBeforeReadingOrCreatingScratch(){
        Path missing=directory.resolve("missing");assertThrows(CancellationException.class,()->SignacFragments.countFragments(List.of(missing),null,0,limits(100),()->true));assertThrows(CancellationException.class,()->SignacFragments.featureMatrix(List.of(new SignacFragments.Source(missing,null)),List.of(new SignacFragments.Region("chr1",1,2)),null,false,2000,directory.resolve("scratch"),limits(2),()->true));assertFalse(Files.exists(directory.resolve("scratch")));
    }
    @Test void outputArraysAndScoreCollectionsAreImmutable()throws Exception{
        Path path=plain("chr1\t0\t1\tA\t1\n");var matrix=SignacFragments.featureMatrix(List.of(new SignacFragments.Source(path,null)),List.of(new SignacFragments.Region("chr1",1,2)),null,false,2000,directory.resolve("sort"),limits(2),()->false);matrix.values()[0]=90;matrix.rows()[0]=90;matrix.pointers()[0]=90;assertArrayEquals(new long[]{1},matrix.values());assertArrayEquals(new int[]{0},matrix.rows());assertArrayEquals(new int[]{0,1},matrix.pointers());assertThrows(UnsupportedOperationException.class,()->matrix.cells().add("x"));
    }
    @ParameterizedTest @ValueSource(strings={"chr1\t-1\t2\tA\t1","chr1\t1\t1\tA\t1","chr1\t0\t2\tA\t0.5","chr1\t0\t2147483648\tA\t1","chr1\t0\t2\tA","chr1\t0\t2\tA\t1\t?","chr1\t0\t2\tA\t1\n# late header","chr1\t0\t2\tA\t1\nchr1\t3\t4\tA\t1\t+"})
    void malformedFragmentFieldsFail(String text)throws Exception{Path path=plain(text+"\n");assertThrows(IOException.class,()->SignacFragments.countFragments(List.of(path),null,0,limits(100),()->false));}
    @Test void lineByteAndDecodedByteLimitsAreEnforced()throws Exception{
        Path first=plain("chr1\t0\t1\t"+"A".repeat(4095)+"\t1\n");assertThrows(IOException.class,()->SignacFragments.countFragments(List.of(first),null,0,limits(100),()->false));Path second=plain("chr1\t0\t1\tA\t1\n");var bound=new SignacFragments.Limits(10,10,10,10,10,5,4094,2);assertThrows(IOException.class,()->SignacFragments.countFragments(List.of(second),null,0,bound,()->false));
    }
}
