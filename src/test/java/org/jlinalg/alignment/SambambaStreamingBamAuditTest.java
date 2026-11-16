/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.alignment;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SambambaStreamingBamAuditTest {
    @TempDir Path temp;
    private static final SambambaStreamingBamAudit.Limits LIMITS=new SambambaStreamingBamAudit.Limits(
        1L<<40,8L<<40,1L<<40,16<<20,4<<20,4<<20);
    private static final String[] CASES={"empty","missing_base_qualities","missing_read_group","paired_and_fragment",
        "paired_quality","preexisting_flags","quality_tie","single_clipping_libraries_tags"};
    private Map<String,byte[]> files;
    private Map<String,byte[]> fixtures()throws Exception {
        if(files==null){
            files=new HashMap<>();
            try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream(
                "/org/jlinalg/alignment/sambamba-original-markdup-066-082.zip")))){
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),zip.readAllBytes());
            }
        }
        return files;
    }
    private Path file(String name)throws Exception {
        Path out=temp.resolve(name);Files.createDirectories(out.getParent());
        if(!Files.exists(out))Files.write(out,Objects.requireNonNull(fixtures().get(name),name));return out;
    }
    private String command(String name)throws Exception{return new String(fixtures().get(name),java.nio.charset.StandardCharsets.UTF_8);}
    private static String sha(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    private static int integer(byte[] bytes,int at){return ByteBuffer.wrap(bytes,at,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    private static List<Integer> records(byte[] raw){
        int at=8+integer(raw,4),n=integer(raw,at);at+=4;
        for(int i=0;i<n;i++)at+=4+integer(raw,at)+4;
        var rows=new ArrayList<Integer>();while(at<raw.length){rows.add(at+4);at+=4+integer(raw,at);}return rows;
    }
    private static byte[] decode(Path file)throws Exception{try(var gzip=new GZIPInputStream(Files.newInputStream(file))){return gzip.readAllBytes();}}
    private static byte[] encode(byte[] raw)throws Exception {
        var out=new ByteArrayOutputStream();
        for(int at=0;at<raw.length;at+=32000){
            int n=Math.min(32000,raw.length-at);var deflater=new Deflater(6,true);deflater.setInput(raw,at,n);deflater.finish();
            byte[] buffer=new byte[65536];int compressed=deflater.deflate(buffer);assertTrue(deflater.finished());deflater.end();
            byte[] header=HexFormat.of().parseHex("1f8b08040000000000ff0600424302000000");
            header[16]=(byte)((18+compressed+8-1)&255);header[17]=(byte)((18+compressed+8-1)>>>8);
            out.write(header);out.write(buffer,0,compressed);CRC32 crc=new CRC32();crc.update(raw,at,n);
            out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt((int)crc.getValue()).putInt(n).array());
        }
        out.write(HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000"));return out.toByteArray();
    }
    private SambambaStreamingBamAudit.Summary check(String name,String edition,SambambaStreamingBamAudit.DecisionSink sink)throws Exception {
        String p=name+"/"+edition+"/";
        return SambambaStreamingBamAudit.audit(file(name+"/input.bam"),file(p+"mark.bam"),file(p+"remove.bam"),edition,
            command(p+"mark.cl.txt"),command(p+"remove.cl.txt"),LIMITS,sink);
    }
    @Test void allOriginalRecordsMetricsHeadersAndIdentitiesMatchTheFrozenAuditor()throws Exception {
        int outputs=0;
        for(String name:CASES)for(String edition:List.of("0.6.6","0.8.2")){
            String p=name+"/"+edition+"/";var decisions=new ArrayList<SambambaStreamingBamAudit.Decision>();
            var streamed=check(name,edition,decisions::add);
            var before=SambambaBamAudit.audit(file(name+"/input.bam"),file(p+"mark.bam"),file(p+"remove.bam"),edition,
                command(p+"mark.cl.txt"),command(p+"remove.cl.txt"),SambambaBamAudit.Limits.defaults());
            assertEquals((long)before.records(),streamed.records());assertEquals((long)before.markedDuplicates(),streamed.markedDuplicates());
            assertEquals((long)before.newlyMarked(),streamed.newlyMarked());assertEquals((long)before.cleared(),streamed.cleared());
            assertEquals((long)before.retainedAfterRemoval(),streamed.retainedAfterRemoval());assertEquals(before.decisions().size(),decisions.size());
            for(int i=0;i<decisions.size();i++){
                var d=before.decisions().get(i);
                assertEquals(new SambambaStreamingBamAudit.Decision(d.ordinal(),d.name(),d.originalFlag(),d.markedFlag(),
                    d.scoredBySource(),d.sourceQualityScore(),d.sourceFivePrimeCoordinate()),decisions.get(i));
            }
            assertEquals(new SambambaStreamingBamAudit.FileIdentity(Files.size(file(name+"/input.bam")),sha(file(name+"/input.bam"))),streamed.input());
            assertEquals(new SambambaStreamingBamAudit.FileIdentity(Files.size(file(p+"mark.bam")),sha(file(p+"mark.bam"))),streamed.marked());
            assertEquals(new SambambaStreamingBamAudit.FileIdentity(Files.size(file(p+"remove.bam")),sha(file(p+"remove.bam"))),streamed.removed());
            assertEquals(streamed.records(),SambambaStreamingBamAudit.validateInput(file(name+"/input.bam"),LIMITS));
            assertEquals(streamed.records(),SambambaStreamingBamAudit.compareActualRuns(file(p+"mark.bam"),file(p+"mark.bam"),command(p+"mark.cl.txt"),command(p+"mark.cl.txt"),LIMITS));
            outputs+=2;
        }
        assertEquals(32,outputs);
    }
    @Test void duplicateTransitionsStillRequireGenuineClassificationReplay()throws Exception {
        Path input=file("paired_quality/input.bam"),marked=file("paired_quality/0.8.2/mark.bam");String cl=command("paired_quality/0.8.2/mark.cl.txt");
        byte[] raw=decode(marked);for(int at:records(raw))raw[at+15]^=4;
        Path counterfeit=temp.resolve("counterfeit.bam");Files.write(counterfeit,encode(raw));
        var observed=SambambaStreamingBamAudit.audit(input,counterfeit,null,"0.8.2",cl,null,LIMITS,d->{ });
        assertEquals(2L,observed.markedDuplicates());assertEquals(2L,observed.retainedAfterRemoval());assertNull(observed.removed());
        IOException refused=assertThrows(IOException.class,()->SambambaStreamingBamAudit.compareActualRuns(marked,counterfeit,cl,cl,LIMITS));
        assertTrue(refused.getMessage().contains("record differs"));
    }
    @Test void nonDuplicateAuxiliaryFloatBitsAndTrailingRemovalRecordsAreRejected()throws Exception {
        String prefix="single_clipping_libraries_tags/0.8.2/";Path input=file("single_clipping_libraries_tags/input.bam"),marked=file(prefix+"mark.bam");
        byte[] raw=decode(marked),needle=HexFormat.of().parseHex("514e664523c17f");int at=-1;
        for(int i=0;i<=raw.length-needle.length;i++)if(Arrays.equals(raw,i,i+needle.length,needle,0,needle.length)){at=i;break;}
        assertTrue(at>=0);raw[at+3]^=1;Path changed=temp.resolve("changed-nan-payload.bam");Files.write(changed,encode(raw));
        assertThrows(IOException.class,()->SambambaStreamingBamAudit.audit(input,changed,null,"0.8.2",command(prefix+"mark.cl.txt"),null,LIMITS,d->{ }));
        assertThrows(IOException.class,()->SambambaStreamingBamAudit.audit(input,marked,marked,"0.8.2",command(prefix+"mark.cl.txt"),command(prefix+"mark.cl.txt"),LIMITS,d->{ }));
    }
    @Test void allExplicitBudgetsAreEnforcedAndLongRecordCapacityIsPreserved()throws Exception {
        Path input=file("paired_quality/input.bam");
        for(var limits:List.of(
            new SambambaStreamingBamAudit.Limits(28,1L<<40,1L<<40,1<<20,1<<20,1<<20),
            new SambambaStreamingBamAudit.Limits(1L<<40,12,1L<<40,1<<20,1<<20,1<<20),
            new SambambaStreamingBamAudit.Limits(1L<<40,1L<<40,0,1<<20,1<<20,1<<20),
            new SambambaStreamingBamAudit.Limits(1L<<40,1L<<40,1L<<40,32,1<<20,1<<20),
            new SambambaStreamingBamAudit.Limits(1L<<40,1L<<40,1L<<40,1<<20,1,1<<20),
            new SambambaStreamingBamAudit.Limits(1L<<40,1L<<40,1L<<40,1<<20,1<<20,4)))
            assertThrows(IOException.class,()->SambambaStreamingBamAudit.validateInput(input,limits));
        assertEquals(1L<<40,LIMITS.records());assertEquals(4L,SambambaStreamingBamAudit.validateInput(input,LIMITS));
        assertThrows(IllegalArgumentException.class,()->new SambambaStreamingBamAudit.Limits(27,12,1,32,1,4));
    }
    @Test void completeBgzfIntegrityAndTerminalMemberRemainMandatory()throws Exception {
        byte[] original=Files.readAllBytes(file("paired_quality/input.bam"));
        byte[] crc=original.clone();crc[crc.length-28-8]^=1;
        for(byte[] invalid:List.of(crc,Arrays.copyOf(original,original.length-28),Arrays.copyOf(original,original.length+1))){
            Path damaged=temp.resolve("invalid-"+invalid.length+".bam");Files.write(damaged,invalid);
            assertThrows(IOException.class,()->SambambaStreamingBamAudit.validateInput(damaged,LIMITS));
        }
    }
    @Test void sinkFailuresAndPostOpenFileGrowthReturnNoSuccessfulSummary()throws Exception {
        Path input=file("paired_quality/input.bam"),marked=file("paired_quality/0.8.2/mark.bam");String cl=command("paired_quality/0.8.2/mark.cl.txt");
        long[] count={0};
        IOException sink=assertThrows(IOException.class,()->SambambaStreamingBamAudit.audit(input,marked,null,"0.8.2",cl,null,LIMITS,d->{count[0]++;if(d.ordinal()==1)throw new IOException("CONTROLLED_DECISION_SINK_FAILURE");}));
        assertEquals("CONTROLLED_DECISION_SINK_FAILURE",sink.getMessage());assertEquals(2L,count[0]);
        long maximum=Math.max(Files.size(input),Files.size(marked));var bound=new SambambaStreamingBamAudit.Limits(maximum,1L<<40,1L<<40,1<<20,1<<20,1<<20);
        IOException growth=assertThrows(IOException.class,()->SambambaStreamingBamAudit.audit(input,marked,null,"0.8.2",cl,null,bound,d->{
            if(d.ordinal()==0)Files.write(marked,new byte[Math.toIntExact(maximum-Files.size(marked)+1)],StandardOpenOption.APPEND);
        }));
        assertTrue(growth.getMessage().contains("Compressed BAM bytes"),growth.getMessage());
    }
}
