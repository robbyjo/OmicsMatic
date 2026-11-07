/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.sequence.SignacFragments;
import org.jlinalg.sequence.SignacPeakPreparation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignacPeakPreparationTest {
    @TempDir Path temp;
    private static Map<?,?> object(Object value){return (Map<?,?>)value;}
    private static List<?> list(Object value){return (List<?>)value;}
    private static String text(Map<?,?> value,String key){return (String)value.get(key);}
    private static Map<?,?> cases()throws Exception {
        try(var in=SignacPeakPreparationTest.class.getResourceAsStream("/org/jlinalg/sequence/signac-peak-preparation/original-cases.json")){
            assertNotNull(in);return object(object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases"));
        }
    }
    static Stream<Arguments> originalCases()throws Exception {return cases().keySet().stream().flatMap(n->Stream.of(Arguments.of(n,false),Arguments.of(n,true)));}
    @ParameterizedTest @MethodSource("originalCases") void exactUnchangedLinuxOriginalBytes(String name,boolean gzip)throws Exception {
        var source=object(cases().get(name));List<SignacFragments.Source> inputs=new ArrayList<>();int ordinal=0;
        for(Object item:list(source.get("inputs"))){var row=object(item);Path file=temp.resolve("source"+ordinal+++(gzip?".gz":".tsv"));byte[] bytes=text(row,"text").getBytes(StandardCharsets.UTF_8);
            if(gzip)try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write(bytes);}else Files.write(file,bytes);
            List<SignacFragments.Cell> map=new ArrayList<>();for(Object m:list(row.get("map"))){var v=object(m);map.add(new SignacFragments.Cell(text(v,"cell"),text(v,"barcode")));}inputs.add(new SignacFragments.Source(file,map));
        }
        List<SignacPeakPreparation.CellGroup> cells=new ArrayList<>();for(Object item:list(source.get("cells"))){var row=object(item);cells.add(new SignacPeakPreparation.CellGroup(text(row,"cell"),text(row,"group")));}
        var result=SignacPeakPreparation.split(inputs,cells,temp.resolve("split"),SignacPeakPreparation.Limits.defaults(),()->false);assertEquals(list(source.get("source_names")),result.groups().stream().map(SignacPeakPreparation.Group::sourceName).toList());
        long total=0;for(var group:result.groups()){byte[] expected=HexFormat.of().parseHex(text(object(source.get("expected_hex")),group.sourceName()));assertArrayEquals(expected,Files.readAllBytes(group.file()),name);total+=expected.length;assertTrue(group.file().getFileName().toString().matches("g[1-9][0-9]*\\.bed"));}
        assertEquals(total,result.outputBytes());assertEquals(inputs.size(),result.scans().size());assertTrue(result.scans().stream().noneMatch(SignacFragments.Scan::prefixOnly));
        if(name.equals("name_collision")){assertEquals(List.of("grp A","grp/A"),result.groups().get(0).originalLabels());assertTrue(result.warnings().stream().anyMatch(w->w.code().equals("SOURCE_GROUP_LABEL_COLLISION")));}
    }
    static Stream<String> malformed(){return Stream.of("", "# header only\n", "chr1\t0\t5\ta\n", "chr1\t-1\t5\ta\t1\n", "chr1\t0\t0\ta\t1\n", "chr1\t0\t5\ta\t0x1\n", "chr1\t0\t5\ta\t2147483648\n", "chr1\t0\t5\ta\t1\tX\n", "chr1\t0\t5\ta\t1\n# late header\n", "chr1\t0\t5\ta\t1\nchr1\t0\t5\ta\t1\t+\n", "chr1\t0\t5\t"+"a".repeat(256)+"\t1\n", "chr1\t0.0\t5\ta\t1\n");}
    private SignacFragments.Source source(String content)throws Exception {Path path=temp.resolve("input.tsv");Files.writeString(path,content);return new SignacFragments.Source(path,null);}
    private List<SignacPeakPreparation.CellGroup> cells(){return List.of(new SignacPeakPreparation.CellGroup("a","A"),new SignacPeakPreparation.CellGroup("b","B"));}
    @ParameterizedTest @MethodSource("malformed") void malformedOrTruncatedRecordsAreNotRepaired(String content)throws Exception {var input=source(content);assertThrows(IOException.class,()->SignacPeakPreparation.split(List.of(input),cells(),temp.resolve("out"),SignacPeakPreparation.Limits.defaults(),()->false));}
    @Test void explicitRawIdentityMapAndSupportCountsAreNotWeights()throws Exception {var input=source("chr1\t0\t5\ta\t999\nchr1\t5\t10\ta\t0\n");var r=SignacPeakPreparation.split(List.of(input),cells(),temp.resolve("out"),SignacPeakPreparation.Limits.defaults(),()->false);assertEquals(2,r.groups().get(0).records());assertEquals(0,r.groups().get(1).records());assertEquals(Files.readString(input.path()),Files.readString(r.groups().get(0).file()));}
    @Test void existingDirectoryIsRefusedAndPreserved()throws Exception {var input=source("chr1\t0\t5\ta\t1\n");Path out=temp.resolve("out");Files.createDirectory(out);Files.writeString(out.resolve("sentinel"),"preserve");assertThrows(FileAlreadyExistsException.class,()->SignacPeakPreparation.split(List.of(input),cells(),out,SignacPeakPreparation.Limits.defaults(),()->false));assertEquals("preserve",Files.readString(out.resolve("sentinel")));assertEquals(1,Objects.requireNonNull(out.toFile().list()).length);}
    @Test void capacityFailureRetainsOwnedPartialFiles()throws Exception {var input=source("chr1\t0\t5\ta\t1\n");var cap=new SignacPeakPreparation.Limits(2,2,256,100,1000,1);assertThrows(IOException.class,()->SignacPeakPreparation.split(List.of(input),cells(),temp.resolve("out"),cap,()->false));assertTrue(Files.exists(temp.resolve("out/g1.bed")));}
    @Test void duplicateCellsAndGroupCapacityAreRejectedBeforeOutputCreation()throws Exception {var input=source("chr1\t0\t5\ta\t1\n");assertThrows(IllegalArgumentException.class,()->SignacPeakPreparation.split(List.of(input),List.of(cells().get(0),cells().get(0)),temp.resolve("out"),SignacPeakPreparation.Limits.defaults(),()->false));assertFalse(Files.exists(temp.resolve("out")));var cap=new SignacPeakPreparation.Limits(2,1,256,100,1000,1000);assertThrows(IllegalArgumentException.class,()->SignacPeakPreparation.split(List.of(input),cells(),temp.resolve("out"),cap,()->false));}
    @Test void preCancelledAndMidScanCancellationPreserveInputs()throws Exception {var input=source("chr1\t0\t5\ta\t1\n".repeat(100));byte[] before=Files.readAllBytes(input.path());assertThrows(CancellationException.class,()->SignacPeakPreparation.split(List.of(input),cells(),temp.resolve("pre"),SignacPeakPreparation.Limits.defaults(),()->true));assertFalse(Files.exists(temp.resolve("pre")));var calls=new AtomicInteger();assertThrows(CancellationException.class,()->SignacPeakPreparation.split(List.of(input),cells(),temp.resolve("mid"),SignacPeakPreparation.Limits.defaults(),()->calls.incrementAndGet()>20));assertArrayEquals(before,Files.readAllBytes(input.path()));assertTrue(Files.exists(temp.resolve("mid/g1.bed")));}
    @Test void corruptGzipAndInvalidUtf8CannotProduceSuccessfulScans()throws Exception {Path bad=temp.resolve("bad.gz");Files.write(bad,new byte[]{31,(byte)139,0});assertThrows(IOException.class,()->SignacPeakPreparation.split(List.of(new SignacFragments.Source(bad,null)),cells(),temp.resolve("gzip"),SignacPeakPreparation.Limits.defaults(),()->false));Path text=temp.resolve("bad.tsv");Files.write(text,new byte[]{(byte)255,10});assertThrows(IOException.class,()->SignacPeakPreparation.split(List.of(new SignacFragments.Source(text,null)),cells(),temp.resolve("utf8"),SignacPeakPreparation.Limits.defaults(),()->false));}
}
