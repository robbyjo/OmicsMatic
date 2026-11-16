/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.alignment;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SambambaBamAuditTest {
    @TempDir Path temp;
    private static final SambambaBamAudit.Limits LIMITS=SambambaBamAudit.Limits.defaults();
    private static final String[] CASES={"empty","missing_base_qualities","missing_read_group","paired_and_fragment",
        "paired_quality","preexisting_flags","quality_tie","single_clipping_libraries_tags"};
    private Map<String,byte[]> files;
    private Map<String,byte[]> fixtures()throws Exception{
        if(files!=null)return files;files=new HashMap<>();
        try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream(
                "/org/jlinalg/alignment/sambamba-original-markdup-066-082.zip")))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),zip.readAllBytes());
        }return files;
    }
    private Path file(String name)throws Exception{
        Path out=temp.resolve(name);Files.createDirectories(out.getParent());
        if(!Files.exists(out))Files.write(out,Objects.requireNonNull(fixtures().get(name),name));return out;
    }
    private String command(String name)throws Exception{return new String(fixtures().get(name),StandardCharsets.UTF_8);}
    private SambambaBamAudit.Result check(String name,String version)throws Exception{
        String prefix=name+"/"+version+"/";
        return SambambaBamAudit.audit(file(name+"/input.bam"),file(prefix+"mark.bam"),file(prefix+"remove.bam"),version,
            command(prefix+"mark.cl.txt"),command(prefix+"remove.cl.txt"),LIMITS);
    }
    private static int get(byte[] raw,int at){return ByteBuffer.wrap(raw,at,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    private static void put(byte[] raw,int at,int value){ByteBuffer.wrap(raw,at,4).order(ByteOrder.LITTLE_ENDIAN).putInt(value);}
    private static byte[] decode(Path p)throws Exception{try(var gzip=new GZIPInputStream(Files.newInputStream(p))){return gzip.readAllBytes();}}
    private static List<Integer> records(byte[] raw){
        int at=8+get(raw,4),n=get(raw,at);at+=4;
        for(int i=0;i<n;i++)at+=4+get(raw,at)+4;
        var rows=new ArrayList<Integer>();while(at<raw.length){rows.add(at+4);at+=4+get(raw,at);}return rows;
    }
    private static byte[] encode(byte[] raw)throws Exception{
        var out=new ByteArrayOutputStream();
        for(int at=0;at<raw.length;at+=32000){
            int n=Math.min(32000,raw.length-at);var deflater=new Deflater(6,true);deflater.setInput(raw,at,n);deflater.finish();
            byte[] buffer=new byte[65536];int compressed=deflater.deflate(buffer);assertTrue(deflater.finished());deflater.end();
            byte[] header=java.util.HexFormat.of().parseHex("1f8b08040000000000ff0600424302000000");
            header[16]=(byte)((18+compressed+8-1)&255);header[17]=(byte)((18+compressed+8-1)>>>8);
            out.write(header);out.write(buffer,0,compressed);CRC32 crc=new CRC32();crc.update(raw,at,n);
            ByteBuffer tail=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt((int)crc.getValue()).putInt(n);out.write(tail.array());
        }
        out.write(java.util.HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000"));return out.toByteArray();
    }
    private Path changed(String label,Path original,UnaryOperator<byte[]> mutation)throws Exception{
        Path out=temp.resolve(label+".bam");Files.write(out,encode(mutation.apply(decode(original))));return out;
    }
    private static byte[] header(byte[] raw,UnaryOperator<String> mutation){
        int length=get(raw,4);byte[] next=mutation.apply(new String(raw,8,length,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        byte[] result=new byte[raw.length-length+next.length];System.arraycopy(raw,0,result,0,4);put(result,4,next.length);
        System.arraycopy(next,0,result,8,next.length);System.arraycopy(raw,8+length,result,8+next.length,raw.length-8-length);return result;
    }
    private void reject(Path input,Path marked,String cl)throws Exception{
        assertThrows(IOException.class,()->SambambaBamAudit.audit(input,marked,null,"0.8.2",cl,null,LIMITS));
    }

    @Test void allThirtyTwoOriginalBamsPassCompleteTransitionsAndHandExpectedFlags()throws Exception{
        Map<String,Integer> duplicates=Map.of("empty",0,"missing_base_qualities",1,"missing_read_group",1,"paired_and_fragment",1,
            "paired_quality",2,"preexisting_flags",2,"quality_tie",1,"single_clipping_libraries_tags",7);
        int originalOutputs=0;
        for(String name:CASES)for(String version:List.of("0.6.6","0.8.2")){
            var result=check(name,version);assertEquals(duplicates.get(name).intValue(),result.markedDuplicates(),name+version);
            assertEquals(result.records(),SambambaBamAudit.validateInput(file(name+"/input.bam"),LIMITS));
            assertEquals(result.records()-result.markedDuplicates(),result.retainedAfterRemoval());
            assertEquals(result.records(),result.decisions().size());originalOutputs+=2;
        }
        assertEquals(32,originalOutputs);
        var singles=check("single_clipping_libraries_tags","0.8.2");
        assertEquals(List.of("hard_lo","reverse_lo","reverse_soft_lo","same_library_other_RG","single_lo","soft_lo","tags_lo"),
            singles.decisions().stream().filter(d->(d.markedFlag()&1024)!=0).map(SambambaBamAudit.Decision::name).sorted().toList());
    }
    @Test void originalQuality255ClippingAndPreexistingFlagBehaviorAreRetained()throws Exception{
        var qualities=check("missing_base_qualities","0.8.2");
        var missing=qualities.decisions().stream().filter(d->d.name().equals("missing_quality")).findFirst().orElseThrow();
        assertTrue(missing.scoredBySource());assertEquals(2550,missing.sourceQualityScore());assertEquals(0,missing.markedFlag());
        var clips=check("single_clipping_libraries_tags","0.6.6").decisions();
        assertEquals(494,clips.stream().filter(d->d.name().equals("soft_hi")).findFirst().orElseThrow().sourceFivePrimeCoordinate());
        assertEquals(594,clips.stream().filter(d->d.name().equals("hard_hi")).findFirst().orElseThrow().sourceFivePrimeCoordinate());
        assertEquals(714,clips.stream().filter(d->d.name().equals("reverse_soft_hi")).findFirst().orElseThrow().sourceFivePrimeCoordinate());
        var old=check("preexisting_flags","0.8.2");assertEquals(2,old.cleared());assertEquals(0,old.newlyMarked());
        assertFalse(old.decisions().stream().filter(d->d.name().equals("secondary_old_duplicate")).findFirst().orElseThrow().scoredBySource());
    }
    @Test void aFlagOnlyTransitionIsAuditedWithoutPretendingToClassifyMolecules()throws Exception{
        String prefix="missing_read_group/0.8.2/";Path marked=file(prefix+"mark.bam");
        Path altered=changed("flag-only",marked,raw->{int p=records(raw).get(0);raw[p+15]^=4;return raw;});
        var result=SambambaBamAudit.audit(file("missing_read_group/input.bam"),altered,null,"0.8.2",command(prefix+"mark.cl.txt"),null,LIMITS);
        assertEquals(2,result.markedDuplicates());
        assertThrows(IOException.class,()->SambambaBamAudit.audit(file("missing_read_group/input.bam"),altered,file(prefix+"remove.bam"),"0.8.2",
            command(prefix+"mark.cl.txt"),command(prefix+"remove.cl.txt"),LIMITS));
    }
    @Test void nonduplicateFlagsSequenceQualitiesAndAuxiliaryNaNPayloadsAreNotNormalized()throws Exception{
        String prefix="single_clipping_libraries_tags/0.8.2/";Path input=file("single_clipping_libraries_tags/input.bam"),marked=file(prefix+"mark.bam");String cl=command(prefix+"mark.cl.txt");
        reject(input,changed("strand",marked,raw->{int p=records(raw).get(0);raw[p+14]^=16;return raw;}),cl);
        reject(input,changed("mapq",marked,raw->{raw[records(raw).get(0)+9]=42;return raw;}),cl);
        reject(input,changed("sequence",marked,raw->{int p=records(raw).get(0),seq=p+32+(raw[p+8]&255)+4*(ByteBuffer.wrap(raw,p+12,2).order(ByteOrder.LITTLE_ENDIAN).getShort()&65535);raw[seq]^=0x30;return raw;}),cl);
        reject(input,changed("qualities",marked,raw->{int p=records(raw).get(0),n=get(raw,p+16),q=p+32+(raw[p+8]&255)+4*(ByteBuffer.wrap(raw,p+12,2).order(ByteOrder.LITTLE_ENDIAN).getShort()&65535)+(n+1)/2;raw[q]^=1;return raw;}),cl);
        for(String tag:List.of("ZZf","QNf","NMi")){
            reject(input,changed("tag-"+tag,marked,raw->{
                byte[] pattern=tag.getBytes(StandardCharsets.US_ASCII);int found=-1;
                outer:for(int i=0;i<raw.length-pattern.length;i++){for(int j=0;j<pattern.length;j++)if(raw[i+j]!=pattern[j])continue outer;found=i;break;}
                assertTrue(found>=0);if(tag.equals("ZZf"))raw[found+6]^=(byte)0x80;else if(tag.equals("NMi"))raw[found+2]='I';else raw[found+3]^=1;return raw;
            }),cl);
        }
    }
    @Test void removalCannotDropRetainedReadsOrChangeThem()throws Exception{
        String prefix="paired_quality/0.8.2/";Path removed=file(prefix+"remove.bam");
        Path altered=changed("removed-mapq",removed,raw->{raw[records(raw).get(0)+9]=35;return raw;});
        assertThrows(IOException.class,()->SambambaBamAudit.audit(file("paired_quality/input.bam"),file(prefix+"mark.bam"),altered,"0.8.2",
            command(prefix+"mark.cl.txt"),command(prefix+"remove.cl.txt"),LIMITS));
        Path dropped=changed("dropped",removed,raw->{int start=records(raw).get(0)-4,n=get(raw,start);byte[] r=new byte[raw.length-4-n];System.arraycopy(raw,0,r,0,start);System.arraycopy(raw,start+4+n,r,start,raw.length-start-4-n);return r;});
        assertThrows(IOException.class,()->SambambaBamAudit.audit(file("paired_quality/input.bam"),file(prefix+"mark.bam"),dropped,"0.8.2",
            command(prefix+"mark.cl.txt"),command(prefix+"remove.cl.txt"),LIMITS));
    }
    @Test void deliberateHeaderVersionOnePointZeroAndExactCommandAreRequired()throws Exception{
        String prefix="paired_quality/0.8.2/";Path input=file("paired_quality/input.bam"),marked=file(prefix+"mark.bam");String cl=command(prefix+"mark.cl.txt");
        Path repaired=changed("wrong-version-repair",marked,raw->header(raw,h->h.replace("VN:1.0","VN:0.8.2")));reject(input,repaired,cl);
        Path wrongCommand=changed("wrong-command",marked,raw->header(raw,h->h.replace("markdup -t 1","markdup -t 9")));reject(input,wrongCommand,cl);
        Path lostLibrary=changed("changed-library",marked,raw->header(raw,h->h.replace("LB:L1","LB:LY")));reject(input,lostLibrary,cl);
        assertThrows(IOException.class,()->SambambaBamAudit.audit(input,marked,null,"0.6.6",cl,null,LIMITS));
    }
    @Test void syntheticCommandRelocationRequiresExactOldAndNewMetadataAndRecordEquality()throws Exception{
        String prefix="paired_quality/0.8.2/";Path marked=file(prefix+"mark.bam");String cl=command(prefix+"mark.cl.txt"),relocated=cl.replace("original-markdup-1","replay-directory");
        assertNotEquals(cl,relocated);
        Path copy=changed("relocated",marked,raw->header(raw,h->h.replace(cl,relocated)));
        SambambaBamAudit.compareActualRuns(marked,copy,cl,relocated,LIMITS);
        assertThrows(IOException.class,()->SambambaBamAudit.compareActualRuns(marked,copy,cl,cl,LIMITS));
        Path changedFlag=changed("relocated-and-reclassified",copy,raw->{raw[records(raw).get(0)+15]^=4;return raw;});
        assertThrows(IOException.class,()->SambambaBamAudit.compareActualRuns(marked,changedFlag,cl,relocated,LIMITS));
    }
    @Test void crcTerminalMarkerMalformedCigarAndResourceBoundsAreEnforced()throws Exception{
        String prefix="paired_quality/0.8.2/";Path input=file("paired_quality/input.bam"),marked=file(prefix+"mark.bam");String cl=command(prefix+"mark.cl.txt");
        byte[] compressed=Files.readAllBytes(marked);compressed[compressed.length-28-8]^=1;Path corrupt=temp.resolve("bad-crc.bam");Files.write(corrupt,compressed);reject(input,corrupt,cl);
        Path truncated=temp.resolve("missing-eof.bam");Files.write(truncated,Arrays.copyOf(Files.readAllBytes(marked),(int)Files.size(marked)-28));reject(input,truncated,cl);
        Path badCigar=changed("bad-cigar",marked,raw->{int p=records(raw).get(0),c=p+32+(raw[p+8]&255);put(raw,c,9<<4);return raw;});reject(input,badCigar,cl);
        for(var limit:List.of(new SambambaBamAudit.Limits(28,1<<20,100,1024,4096),new SambambaBamAudit.Limits(1<<20,12,100,1024,4096),
                new SambambaBamAudit.Limits(1<<20,1<<20,1,1024,4096),new SambambaBamAudit.Limits(1<<20,1<<20,100,32,4096),new SambambaBamAudit.Limits(1<<20,1<<20,100,1024,1)))
            assertThrows(IOException.class,()->SambambaBamAudit.audit(input,marked,null,"0.8.2",cl,null,limit));
    }
}
