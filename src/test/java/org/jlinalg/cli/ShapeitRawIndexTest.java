/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitRawIndexTest {
    @TempDir Path temporary;
    private static final ShapeitRawIndex.Filter ALL=(c,p,l)->true;
    private static final Map<String,byte[]> FILES=capture();
    static Stream<String> cases(){return ShapeitBcfTest.cases();}
    private static ShapeitBcf.Limits rawLimits(){var d=ShapeitBcf.Limits.defaults();return new ShapeitBcf.Limits(d.samples(),d.records(),d.genotypeCells(),d.inputBytes(),d.decodedBytes(),d.retainedBytes(),d.headerLines(),d.headerCharacters(),d.lineCharacters(),30000,d.formatFields(),d.genotypeWidth(),d.valuesPerRecord());}
    private Path fixture(String name) throws IOException {Path p=temporary.resolve(name+".bcf");Files.write(p,FILES.get(name+".bcf"));return p;}
    private ShapeitRawIndex.Store build(Path p) throws IOException {return ShapeitRawIndex.build(p,temporary.resolve("spool-"+UUID.randomUUID()),rawLimits(),ShapeitRawIndex.Limits.defaults(),()->{});}
    private List<ShapeitBcf.Row> raw(Path p) throws IOException {var rows=new ArrayList<ShapeitBcf.Row>();assertTrue(ShapeitBcf.read(p,rawLimits(),rows::add,()->{}).complete());return rows;}

    @ParameterizedTest @MethodSource("cases")
    void completeDiskRoundTripPreservesEveryMetadataAndPayloadBit(String name) throws Exception {
        Path input=fixture(name);var original=raw(input);var store=build(input);
        assertTrue(store.report().complete());assertEquals(original.size(),store.report().records());
        try(var reader=store.open()){
            assertEquals(original.size(),reader.census(ALL));
            for(int i=original.size()-1;i>=0;i--){var entry=reader.entry(i);same(original.get(i),reader.row(entry));assertEquals(entry.ordinal(),i+1);}
        }
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("seen"),ALL)){
            for(var row:original)same(row,cursor.take(row.contig(),row.position(),row.rawWireSha256()));
            assertEquals(original.size(),cursor.finish(original.size()));
            assertThrows(IOException.class,()->cursor.finish(original.size()));
        }
        assertTrue(Files.readString(store.directory().resolve("status.txt")).startsWith("COMPLETE RAW INDEX"));
    }

    @Test void byteIdenticalDuplicatesConsumeSeparateOriginalOrdinals() throws Exception {
        Path input=repeated(3,false);var rows=raw(input);var store=build(input);
        assertEquals(1,rows.stream().map(ShapeitBcf.Row::rawWireSha256).distinct().count());
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("seen"),ALL)){
            for(int i=0;i<3;i++)assertEquals(i+1,cursor.take("20",rows.get(0).position(),rows.get(0).rawWireSha256()).ordinal());
            assertEquals(3,cursor.finish(3));
        }
    }
    @Test void missingDuplicateWrongIdentityAndExtraVisitInvalidateTheWholeCursor() throws Exception {
        Path input=repeated(3,false);var rows=raw(input);var store=build(input);var row=rows.get(0);
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("missing"),ALL)){
            cursor.take(row.contig(),row.position(),row.rawWireSha256());
            assertThrows(IOException.class,()->cursor.finish(1));assertThrows(IOException.class,()->cursor.take(row.contig(),row.position(),row.rawWireSha256()));
        }
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("wrong"),ALL)){
            assertThrows(IOException.class,()->cursor.take(row.contig(),row.position(),"0".repeat(64)));assertThrows(IOException.class,()->cursor.finish(0));
        }
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("extra"),ALL)){
            for(int i=0;i<3;i++)cursor.take(row.contig(),row.position(),row.rawWireSha256());
            assertThrows(IOException.class,()->cursor.take(row.contig(),row.position(),row.rawWireSha256()));assertThrows(IOException.class,()->cursor.finish(3));
        }
    }
    @Test void coordinateRevisitsCannotReplaceAnOmittedCoordinateEvenWithAResetBitmap() throws Exception {
        Path input=repeated(3,true);var rows=raw(input);var store=build(input);Path bitmap=temporary.resolve("seen");
        try(var cursor=new ShapeitRawIndex.Cursor(store,bitmap,ALL)){
            var first=rows.get(0);cursor.take(first.contig(),first.position(),first.rawWireSha256());
            var next=rows.get(1);cursor.take(next.contig(),next.position(),next.rawWireSha256());
            Files.write(bitmap,new byte[]{0});
            cursor.take(first.contig(),first.position(),first.rawWireSha256());
            assertThrows(IOException.class,()->cursor.finish(3));
        }
    }
    @Test void independentCensusDetectsEntirelyOmittedCoordinatesAndSupportsEmptyRegions() throws Exception {
        Path input=repeated(3,true);var rows=raw(input);var store=build(input);
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("missing"),ALL)){
            var row=rows.get(2);cursor.take(row.contig(),row.position(),row.rawWireSha256());assertThrows(IOException.class,()->cursor.finish(1));
        }
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("empty"),(c,p,l)->false)){assertEquals(0,cursor.finish(0));}
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("filtered"),(c,p,l)->p==2)){
            var row=rows.get(1);assertEquals(2,cursor.take(row.contig(),row.position(),row.rawWireSha256()).ordinal());assertEquals(1,cursor.finish(1));
        }
    }
    @Test void sourceAndSpoolMutationCannotProduceCompletion() throws Exception {
        for(String target:List.of("source","records.idx","records.dat")){
            Path input=fixture("initial-raw");var store=build(input);
            try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("seen-"+target),ALL)){
                for(var row:raw(input))cursor.take(row.contig(),row.position(),row.rawWireSha256());
                Path changed=target.equals("source")?input:store.directory().resolve(target);byte[] b=Files.readAllBytes(changed);b[b.length-1]^=1;Files.write(changed,b);
                assertThrows(IOException.class,()->cursor.finish(store.report().records()));
            }
            assertThrows(IOException.class,store::open);
        }
    }
    @Test void failedSpoolsStayRetainedAndNeverOverwriteAnExistingDirectory() throws Exception {
        Path input=fixture("initial-raw"),out=temporary.resolve("failed");byte[] b=Files.readAllBytes(input);Files.write(input,Arrays.copyOf(b,b.length-1));
        assertThrows(IOException.class,()->ShapeitRawIndex.build(input,out,rawLimits(),ShapeitRawIndex.Limits.defaults(),()->{}));
        assertTrue(Files.readString(out.resolve("status.txt")).startsWith("FAILED; dependent analysis SKIPPED"));
        byte[] status=Files.readAllBytes(out.resolve("status.txt"));assertThrows(FileAlreadyExistsException.class,()->ShapeitRawIndex.build(input,out,rawLimits(),ShapeitRawIndex.Limits.defaults(),()->{}));assertArrayEquals(status,Files.readAllBytes(out.resolve("status.txt")));
    }
    @Test void completeSpoolRecordAndCoordinateCapacitiesRefuseTruncation() throws Exception {
        Path input=repeated(3,false);var d=ShapeitRawIndex.Limits.defaults();
        for(var limit:List.of(new ShapeitRawIndex.Limits(100,d.recordBytes(),d.coordinateRows(),d.coordinateBytes()),new ShapeitRawIndex.Limits(d.spoolBytes(),32,d.coordinateRows(),d.coordinateBytes())))
            assertThrows(IOException.class,()->ShapeitRawIndex.build(input,temporary.resolve("limited-"+UUID.randomUUID()),rawLimits(),limit,()->{}));
        var store=ShapeitRawIndex.build(input,temporary.resolve("bucket"),rawLimits(),new ShapeitRawIndex.Limits(d.spoolBytes(),d.recordBytes(),2,d.coordinateBytes()),()->{});var row=raw(input).get(0);
        try(var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("seen"),ALL)){assertThrows(IOException.class,()->cursor.take(row.contig(),row.position(),row.rawWireSha256()));assertThrows(IOException.class,()->cursor.finish(0));}
    }
    @Test void unsortedSourceIsRefusedInsteadOfInventingANewRecordOrder() throws Exception {
        Path input=repeated(3,true);byte[] b=Files.readAllBytes(input);int first=9+ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(5);ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(first+12,100);Files.write(input,b);
        assertThrows(IOException.class,()->build(input));
    }
    @Test void thousandsOfRowsUseDiskLookupsAcrossTheWholePopulation() throws Exception {
        Path input=repeated(5000,true);var store=build(input);
        try(var reader=store.open()){
            assertEquals(5000,reader.census(ALL));
            for(int pos:List.of(5000,1,2499,2)){
                var entries=reader.at("20",pos,ALL);assertEquals(1,entries.size());assertEquals(pos,reader.row(entries.get(0)).ordinal());
            }
            assertTrue(reader.at("missing",1,ALL).isEmpty());
        }
        assertEquals(24+96L*5000,store.indexIdentity().bytes());
    }
    @Test void closedEmptyCursorCannotBeCertified() throws Exception {
        var store=build(fixture("initial-raw"));var cursor=new ShapeitRawIndex.Cursor(store,temporary.resolve("closed"),(c,p,l)->false);cursor.close();cursor.close();assertThrows(IOException.class,()->cursor.finish(0));
    }
    private Path repeated(int n,boolean distinctPositions) throws IOException {
        byte[] original=FILES.get("initial-raw.bcf");var b=ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);int start=9+b.getInt(5),end=start+8+b.getInt(start)+b.getInt(start+4);
        Path input=temporary.resolve("repeat-"+UUID.randomUUID()+".bcf");
        try(var output=Files.newOutputStream(input)){output.write(original,0,start);for(int i=0;i<n;i++){byte[] record=Arrays.copyOfRange(original,start,end);if(distinctPositions)ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).putInt(12,i);output.write(record);}}
        return input;
    }
    private static void same(ShapeitBcf.Row a,ShapeitBcf.Row b) throws Exception {
        assertEquals(a.ordinal(),b.ordinal());assertEquals(a.contig(),b.contig());assertEquals(a.position(),b.position());assertEquals(a.referenceLength(),b.referenceLength());assertEquals(a.id(),b.id());assertEquals(a.reference(),b.reference());assertEquals(a.alternates(),b.alternates());assertEquals(a.qualityBits(),b.qualityBits());assertEquals(a.filters(),b.filters());assertEquals(a.rawWireSha256(),b.rawWireSha256());
        assertEquals(new ArrayList<>(a.info().keySet()),new ArrayList<>(b.info().keySet()));for(String k:a.info().keySet())same(a.info().get(k),b.info().get(k));assertEquals(a.formats().size(),b.formats().size());for(int i=0;i<a.formats().size();i++)same(a.formats().get(i),b.formats().get(i));
    }
    private static void same(ShapeitBcf.Field a,ShapeitBcf.Field b){assertEquals(a.name(),b.name());assertEquals(a.type(),b.type());assertEquals(a.width(),b.width());assertEquals(a.cells(),b.cells());assertArrayEquals(a.payload(),b.payload());}
    private static Map<String,byte[]> capture(){
        try(var input=ShapeitRawIndexTest.class.getResourceAsStream("/org/jlinalg/cli/shapeit-bcf/raw-oracles-3.zip")){
            assertNotNull(input);var files=new HashMap<String,byte[]>();try(var zip=new ZipInputStream(input)){long total=0;for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){byte[] b=zip.readNBytes(8388609);total+=b.length;assertTrue(total<=8388608);files.put(e.getName(),b);}}return files;
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
}
