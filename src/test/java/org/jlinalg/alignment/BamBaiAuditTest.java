/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.alignment;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BamBaiAuditTest {
    @TempDir Path temp;
    private static final SambambaStreamingBamAudit.Limits BAM=new SambambaStreamingBamAudit.Limits(1L<<40,8L<<40,1L<<40,16<<20,4<<20,4<<20);
    private static final BamBaiAudit.Limits BAI=new BamBaiAudit.Limits(64L<<20,1000000,1000000,120000);
    private static final String[] CASES={"empty","missing_base_qualities","missing_read_group","paired_and_fragment","paired_quality","preexisting_flags","quality_tie","single_clipping_libraries_tags"};
    private Map<String,byte[]> files;
    private Map<String,byte[]> fixtures()throws Exception {
        if(files==null){files=new HashMap<>();try(var z=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/org/jlinalg/alignment/sambamba-original-markdup-066-082.zip")))){
            for(var e=z.getNextEntry();e!=null;e=z.getNextEntry())files.put(e.getName(),z.readAllBytes());
        }}return files;
    }
    private Path file(String name)throws Exception {
        Path p=temp.resolve(name);Files.createDirectories(p.getParent());if(!Files.exists(p))Files.write(p,Objects.requireNonNull(fixtures().get(name),name));return p;
    }
    private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    @Test void allThirtyTwoOriginalIndexesAreAssessedWithoutChangingThem()throws Exception {
        int passed=0,knownMetadataFailures=0;
        for(String name:CASES)for(String edition:List.of("0.6.6","0.8.2"))for(String action:List.of("mark","remove")){
            Path bam=file(name+"/"+edition+"/"+action+".bam"),bai=file(name+"/"+edition+"/"+action+".bai");String before=sha(bai);
            if(name.equals("preexisting_flags")&&edition.equals("0.6.6")){
                IOException rejected=assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bai,BAM,BAI));
                assertTrue(rejected.getMessage().contains("metadata reference extent"),rejected.getMessage());knownMetadataFailures++;
            }else{
                var result=BamBaiAudit.audit(bam,bai,BAM,BAI);assertEquals(SambambaStreamingBamAudit.validateInput(bam,BAM),result.records());
                assertEquals(sha(bam),result.bam().sha256());assertEquals(before,result.bai().sha256());passed++;
            }
            assertEquals(before,sha(bai));
        }
        assertEquals(30,passed);assertEquals(2,knownMetadataFailures);
    }
    @Test void existingNativeHtsjdkIndexerProducesAVerifiedAlternativeForTheKnownBadSidecar()throws Exception {
        Path bam=file("preexisting_flags/0.6.6/mark.bam"),original=file("preexisting_flags/0.6.6/mark.bai"),fresh=temp.resolve("fresh.bai");
        String bamHash=sha(bam),originalHash=sha(original);
        try(var reader=htsjdk.samtools.SamReaderFactory.makeDefault().validationStringency(htsjdk.samtools.ValidationStringency.SILENT)
            .enable(htsjdk.samtools.SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS,htsjdk.samtools.SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX).open(bam)){
            htsjdk.samtools.BAMIndexer.createIndex(reader,fresh);
        }
        var result=BamBaiAudit.audit(bam,fresh,BAM,BAI);assertEquals(5,result.records());assertEquals(4,result.placedMapped());assertEquals(1,result.unplaced());
        assertEquals(bamHash,sha(bam));assertEquals(originalHash,sha(original));assertNotEquals(originalHash,sha(fresh));
    }
    @Test void missingCoverageWrongMetadataAndInteriorOffsetsCannotBeResealedIntoAValidIndex()throws Exception {
        Path bam=file("paired_quality/0.8.2/mark.bam");byte[] original=Files.readAllBytes(file("paired_quality/0.8.2/mark.bai"));
        // The first ordinary bin starts at byte12, its one chunk at byte20.
        assertEquals(4681,ByteBuffer.wrap(original,12,4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        var mutations=new ArrayList<byte[]>();
        byte[] interior=original.clone();ByteBuffer.wrap(interior).order(ByteOrder.LITTLE_ENDIAN).putLong(20,ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).getLong(20)+1);mutations.add(interior);
        byte[] wrongBin=original.clone();ByteBuffer.wrap(wrongBin).order(ByteOrder.LITTLE_ENDIAN).putInt(12,4682);mutations.add(wrongBin);
        byte[] wrongMapped=original.clone();ByteBuffer.wrap(wrongMapped).order(ByteOrder.LITTLE_ENDIAN).putLong(60,99);mutations.add(wrongMapped);
        byte[] wrongNoCoordinate=original.clone();ByteBuffer.wrap(wrongNoCoordinate).order(ByteOrder.LITTLE_ENDIAN).putLong(wrongNoCoordinate.length-8,1);mutations.add(wrongNoCoordinate);
        for(int i=0;i<mutations.size();i++){
            Path bad=temp.resolve("bad-"+i+".bai");Files.write(bad,mutations.get(i));assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bad,BAM,BAI));
        }
    }
    @Test void malformedAndOverBudgetIndexesNeverReturnASuccessfulSummary()throws Exception {
        Path bam=file("paired_quality/0.8.2/mark.bam"),bai=file("paired_quality/0.8.2/mark.bai");byte[] raw=Files.readAllBytes(bai);
        for(byte[] bad:List.of(java.util.Arrays.copyOf(raw,raw.length-1),java.util.Arrays.copyOf(raw,raw.length+1),new byte[12])){
            Path path=temp.resolve("malformed-"+bad.length+".bai");Files.write(path,bad);assertThrows(IOException.class,()->BamBaiAudit.audit(bam,path,BAM,BAI));
        }
        assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bai,BAM,new BamBaiAudit.Limits(8,100,100,120000)));
        assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bai,BAM,new BamBaiAudit.Limits(1<<20,0,100,120000)));
        assertThrows(IOException.class,()->BamBaiAudit.audit(bam,bai,BAM,new BamBaiAudit.Limits(1<<20,100,0,120000)));
    }
}
