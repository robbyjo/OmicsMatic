/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.alignment;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipInputStream;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BamBaiWideAuditTest {
    @TempDir Path temp;
    private static final SambambaStreamingBamAudit.Limits BAM=new SambambaStreamingBamAudit.Limits(1L<<40,8L<<40,1L<<40,16<<20,4<<20,4<<20);
    private static final BamBaiAudit.Limits BAI=new BamBaiAudit.Limits(64L<<20,1_000_000,1_000_000,120_000);
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hash(Path path)throws Exception{return hash(Files.readAllBytes(path));}
    private void unpack()throws Exception {
        byte[] bytes;
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/org/jlinalg/alignment/bai-wide-original-native.zip"))){bytes=stream.readAllBytes();}
        assertEquals("39c2a275c9366faf45fb70b22ec33a7d4b7c20fb4bb5c86eade04cdfdd27ec0c",hash(bytes));
        int count=0;
        try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(bytes))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                Path path=temp.resolve(entry.getName()).normalize();assertTrue(path.startsWith(temp));assertFalse(Files.exists(path));
                Files.createDirectories(path.getParent());Files.write(path,zip.readAllBytes());count++;
            }
        }
        assertEquals(132,count);
    }
    private List<String[]> rows(String member)throws Exception {
        var result=new ArrayList<String[]>();var lines=Files.readAllLines(temp.resolve(member),StandardCharsets.UTF_8);
        for(int i=1;i<lines.size();i++)if(!lines.get(i).isEmpty())result.add(lines.get(i).split("\t",-1));return result;
    }
    private static SamReader reader(Path bam,Path bai)throws IOException {
        return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
            .enable(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX).disable(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES)
            .open(SamInputResource.of(bam).index(bai));
    }
    private long queries(Path bam,Path bai,Path queryFile)throws Exception {
        long count=0;
        try(var reader=reader(bam,bai);var input=Files.newBufferedReader(queryFile,StandardCharsets.UTF_8)){
            for(String line;(line=input.readLine())!=null;){
                String[] fields=line.split("\t",-1);assertEquals(5,fields.length);var actual=new ArrayList<String>();
                try(var iterator=reader.queryOverlapping(fields[1],Integer.parseInt(fields[2]),Integer.parseInt(fields[3]))){
                    while(iterator.hasNext()){var record=iterator.next();if(!record.getReadUnmappedFlag())actual.add(record.getReadName());}
                }
                assertEquals(fields[4],String.join(",",actual),bam+" query "+fields[0]+" "+fields[1]+":"+fields[2]+"-"+fields[3]);count++;
            }
        }
        return count;
    }
    @Test void allFiftyOneOriginalAndNativePairsRetainTheirExpectedAuditOutcomes()throws Exception {
        unpack();var rows=rows("PAIRS.tsv");assertEquals(51,rows.size());int accepted=0,rejected=0;
        for(String[] row:rows){
            assertEquals(9,row.length);Path bam=temp.resolve(row[3]),bai=temp.resolve(row[4]);String beforeBam=hash(bam),beforeIndex=hash(bai);
            if(Boolean.parseBoolean(row[6])){
                var result=BamBaiAudit.audit(bam,bai,BAM,BAI);assertEquals(Long.parseLong(row[8]),result.records(),row[0]);
                assertEquals(beforeBam,result.bam().sha256());assertEquals(beforeIndex,result.bai().sha256());accepted++;
            }else{
                IOException failure=assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bai,BAM,BAI),row[0]);
                assertTrue(failure.getMessage().contains(row[7]),row[0]+": "+failure);rejected++;
            }
            assertEquals(beforeBam,hash(bam));assertEquals(beforeIndex,hash(bai));
        }
        assertEquals(48,accepted);assertEquals(3,rejected);
    }
    @Test void allOriginalAndNativeWideIndexesMatchIndependentMappedQueryExpectations()throws Exception {
        unpack();long count=0;
        for(String[] row:rows("PAIRS.tsv"))count+=queries(temp.resolve(row[3]),temp.resolve(row[4]),temp.resolve(row[5]));
        assertEquals(30_070,count);
    }
    @Test void corruptIndexesAreRefusedAndEquivalentOffsetsRemainQueryableWithoutRewriting()throws Exception {
        unpack();int accepted=0,rejected=0;long equivalent=0;
        for(String[] row:rows("CONTROLS.tsv")){
            assertEquals(6,row.length);Path bam=temp.resolve(row[1]),bai=temp.resolve(row[2]);String original=hash(bai);
            if(Boolean.parseBoolean(row[4])){
                var summary=BamBaiAudit.audit(bam,bai,BAM,BAI);equivalent+=summary.equivalentOffsetAliases();
                assertEquals(511,queries(bam,bai,temp.resolve(row[3])));accepted++;
            }else{
                IOException failure=assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bai,BAM,BAI),row[0]);
                assertTrue(failure.getMessage().contains(row[5]),row[0]+": "+failure);rejected++;
                if(row[0].equals("linear_empty_gap_skips_long_alignment")){
                    // The wrong offset is in tile20, before the long read's own
                    // start tile30. It genuinely loses the long read from this query.
                    try(var reader=reader(bam,bai);var iterator=reader.queryOverlapping("chrA",327681,700001)){
                        assertFalse(iterator.hasNext(),"This controlled index must really lose the expected long_gap alignment");
                    }
                }
            }
            assertEquals(original,hash(bai));
        }
        assertEquals(4,accepted);assertEquals(9,rejected);assertTrue(equivalent>=2);
    }
    @Test void interruptedAuditReturnsNoAcceptedSummaryAndPreservesInputs()throws Exception {
        unpack();Path bam=temp.resolve("pairs/wide_no_tail/native-java-input/data.bam"),bai=temp.resolve("pairs/wide_no_tail/native-java-input/index.bai");
        String bamHash=hash(bam),indexHash=hash(bai);Thread.currentThread().interrupt();
        try{assertThrows(InterruptedIOException.class,()->BamBaiAudit.audit(bam,bai,BAM,BAI));}
        finally{Thread.interrupted();}
        assertEquals(bamHash,hash(bam));assertEquals(indexHash,hash(bai));
    }
}
