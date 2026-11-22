package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.jlinalg.genetics.BigWigSignal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BigWigSignalTest {
    @TempDir Path temp;
    private static Path fixtures()throws Exception{return Path.of(Objects.requireNonNull(BigWigSignalTest.class.getResource("/igv/bigwig/provenance.json")).toURI()).getParent();}
    @Test void all351836IntervalsMatchOriginalPyBigWigAcrossEverySectionTypeAndBranchingTree()throws Exception {
        Map<?,?> provenance=(Map<?,?>)SimpleJson.parse(Files.readString(fixtures().resolve("provenance.json")));long total=0;var types=new HashSet<Integer>();int cases=0;
        for(Object value:(List<?>)provenance.get("cases")){Map<?,?> row=(Map<?,?>)value;Path file=fixtures().resolve((String)row.get("file"));assertEquals(row.get("sha256"),FollowupSupport.hash(file));var data=BigWigSignal.read(file,10_000_000,1_000_000,16_777_216);var fingerprint=MessageDigest.getInstance("SHA-256");for(var s:data.segments()){byte[] name=s.chromosome().getBytes(StandardCharsets.UTF_8);fingerprint.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(name.length).array());fingerprint.update(name);fingerprint.update(ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putLong(s.start()).putLong(s.end()).putFloat((float)s.value()).array());}assertEquals(row.get("original_interval_fingerprint_sha256"),HexFormat.of().formatHex(fingerprint.digest()),String.valueOf(row.get("case")));assertEquals(((Number)row.get("records")).intValue(),data.segments().size());var expected=new LinkedHashMap<String,Long>();for(Object chromosome:(List<?>)row.get("header")){List<?> pair=(List<?>)chromosome;expected.put((String)pair.get(0),((Number)pair.get(1)).longValue());}assertEquals(expected,data.dictionary());types.addAll(data.sectionTypes());total+=data.segments().size();cases++;assertTrue(data.compressed());assertEquals(ByteOrder.LITTLE_ENDIAN,data.byteOrder());if(file.getFileName().toString().equals("many-raw-blocks.bw"))assertTrue(data.rawBlocks()>1);}
        assertEquals(14,cases);assertEquals(351836,total);assertEquals(Set.of(1,2,3),types);
    }
    @Test void fileRecordAndDecompressionLimitsRejectOriginalFixtures()throws Exception {
        Path file=fixtures().resolve("bedgraph-3.bw");assertThrows(java.io.IOException.class,()->BigWigSignal.read(file,64,10000,16777216));assertThrows(java.io.IOException.class,()->BigWigSignal.read(file,10000,1,16777216));assertThrows(java.io.IOException.class,()->BigWigSignal.read(file,10000,1000,24));assertThrows(IllegalArgumentException.class,()->BigWigSignal.read(file,10,1,24));
    }
    @Test void malformedHeadersOffsetsTreesAndCompressedDataFail()throws Exception {
        byte[] source=Files.readAllBytes(fixtures().resolve("bedgraph-3.bw"));for(int field:new int[]{0,4,8,16,24,52}){byte[] raw=source.clone();Arrays.fill(raw,field,field+(field==4?2:field<8?4:field==52?4:8),(byte)0xff);Path file=temp.resolve("bad-"+field+".bw");Files.write(file,raw);assertThrows(java.io.IOException.class,()->BigWigSignal.read(file,100000,10000,16777216),Integer.toString(field));}
        Path truncated=temp.resolve("truncated.bw");Files.write(truncated,Arrays.copyOf(source,80));assertThrows(java.io.IOException.class,()->BigWigSignal.read(truncated,100000,10000,16777216));
        long data=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getLong(16);byte[] compressed=source.clone();compressed[Math.toIntExact(data+8)]^=0x7f;Path file=temp.resolve("broken-zlib.bw");Files.write(file,compressed);assertThrows(java.io.IOException.class,()->BigWigSignal.read(file,100000,10000,16777216));
    }
}
