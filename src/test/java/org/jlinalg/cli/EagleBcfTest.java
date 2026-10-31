/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
final class EagleBcfTest {
    @TempDir Path temporary;
    private EagleVcf.Limits limits(){return new EagleVcf.Limits(new MinimacVcf.Limits(32,1000,10000,16777216,67108864,1048576,67108864,1000),1000,4194304,100,100,1048576);}
    private Path fixture(String name)throws Exception {
        Path path=temporary.resolve(name);if(Files.exists(path))return path;
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/cli/eagle-bcf/"+name)){assertNotNull(stream,name);Files.write(path,stream.readAllBytes());}return path;
    }
    private EagleBcf.Report convert(Path source,Path out)throws IOException{return EagleBcf.convert(source,out,limits(),134217728,67108864,()->{});}
    private List<String> gtRows(Path output)throws Exception {
        var lines=new ArrayList<String>();try(var reader=EagleVcf.open(output,limits(),()->{})){
            for(EagleVcf.Row row;(row=reader.next())!=null;){var fields=new ArrayList<>(List.of(row.contig(),Integer.toString(row.position()),row.id(),row.reference(),row.alternate()));for(var call:row.calls())fields.add(call.raw());lines.add(String.join("\t",fields));}
            assertTrue(reader.report().complete());
        }return lines;
    }
    @Test void completeRawAndBgzfCallsMatchIndependentOriginalBcftoolsQueries()throws Exception {
        for(String label:List.of("missing","rich"))for(String kind:List.of("raw","bgzf")){
            Path output=temporary.resolve(label+kind+".vcf");var report=convert(fixture(label+"-"+kind+".bcf"),output);
            assertTrue(report.complete());assertEquals(kind.equals("bgzf"),report.bgzf());assertEquals(4,report.samples());assertEquals(label.equals("missing")?128:16,report.genotypeCells());
            assertEquals(Files.readAllLines(fixture(label+"-gt.tsv")),gtRows(output));assertEquals(FollowupSupport.hash(output),report.outputSha256());
        }
    }
    @Test void rawAndCompressedRepresentationsHaveIdenticalDecodedTextAndGtDigests()throws Exception {
        Path a=temporary.resolve("a.vcf"),b=temporary.resolve("b.vcf");var raw=convert(fixture("rich-raw.bcf"),a);var bgzf=convert(fixture("rich-bgzf.bcf"),b);
        assertArrayEquals(Files.readAllBytes(a),Files.readAllBytes(b));assertEquals(raw.genotypeSha256(),bgzf.genotypeSha256());
    }
    @Test void interiorMissingNumericValuesAndFloat32BitsSurviveTextDecoding()throws Exception {
        Path output=temporary.resolve("rich.vcf");convert(fixture("rich-raw.bcf"),output);
        String row=Files.readAllLines(output).stream().filter(x->!x.startsWith("#")).findFirst().orElseThrow();String[] fields=row.split("\t",-1);
        assertTrue(fields[7].contains("I8=-2,.,3,."));assertTrue(fields[7].contains("I16=128,.,-129"));assertTrue(fields[7].contains("I32=65536,.,-32769"));assertTrue(fields[7].contains("TXT=alpha,.,beta"));
        assertEquals(Float.floatToRawIntBits(Float.parseFloat("0.123456789")),Float.floatToRawIntBits(Float.parseFloat(fields[5])));
        String[] info=Arrays.stream(fields[7].split(";")).filter(x->x.startsWith("F=")).findFirst().orElseThrow().substring(2).split(",",-1);
        assertEquals(".",info[1]);assertEquals(Float.floatToRawIntBits(Float.parseFloat("1e-30")),Float.floatToRawIntBits(Float.parseFloat(info[2])));assertEquals(0x80000000,Float.floatToRawIntBits(Float.parseFloat(info[3])));
        assertEquals(List.of("NaN","Inf","-Inf"),Arrays.asList(info).subList(4,7));assertTrue(fields[9].contains(":1,.,3,.:") );
    }
    @Test void sitesOnlyBcfRemainsEightColumnVcf()throws Exception {
        Path output=temporary.resolve("sites.vcf");var report=convert(fixture("sites-bgzf.bcf"),output);assertEquals(0,report.samples());assertEquals(0,report.genotypeCells());
        try(var reader=EagleVcf.openSites(output,limits(),()->{})){assertNotNull(reader.next());assertNull(reader.next());assertTrue(reader.report().complete());}
        assertEquals(8,Files.readAllLines(output).stream().filter(x->!x.startsWith("#")).findFirst().orElseThrow().split("\t").length);
    }
    private Path raw(int type,int[] gt)throws Exception {
        byte[] header=("##fileformat=VCFv4.3\n##contig=<ID=22,IDX=5>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"GT, quoted \\\"text\\\"\",IDX=7>\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS\n\0").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream shared=new ByteArrayOutputStream();for(int n:new int[]{5,0,1,0x7f800001,2<<16,(1<<24)|1})shared.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array());shared.writeBytes(new byte[]{7,0x17,'A',0x17,'C',0x11,0});
        ByteArrayOutputStream individual=new ByteArrayOutputStream();individual.writeBytes(new byte[]{0x11,7,(byte)((gt.length<<4)|type)});
        for(int n:gt){if(type==1)individual.write(n);else if(type==2)individual.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)n).array());else individual.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array());}
        ByteArrayOutputStream all=new ByteArrayOutputStream();all.writeBytes(new byte[]{'B','C','F',2,2});all.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(header.length).array());all.writeBytes(header);all.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(shared.size()).putInt(individual.size()).array());all.writeBytes(shared.toByteArray());all.writeBytes(individual.toByteArray());
        Path path=temporary.resolve("synthetic-"+type+"-"+Arrays.hashCode(gt)+".bcf");Files.write(path,all.toByteArray());return path;
    }
    @Test void integerGtWidthsExplicitDictionaryIndicesAndSingleDotPloidyArePreserved()throws Exception {
        for(int type=1;type<=3;type++){
            int end=type==1?-127:type==2?-32767:Integer.MIN_VALUE+1;Path output=temporary.resolve("width"+type+".vcf");convert(raw(type,new int[]{0,end}),output);assertEquals("22\t1\t.\tA\tC\t.",gtRows(output).get(0));assertFalse(Files.readString(output).contains("IDX="));
        }
        Path phased=temporary.resolve("phased.vcf");convert(raw(1,new int[]{0,5}),phased);assertEquals("22\t1\t.\tA\tC\t.|1",gtRows(phased).get(0));
    }
    @Test void invalidGtReservedValuesPaddingOutOfRangeAndPloidyAreRefused()throws Exception {
        for(int[] gt:new int[][]{{-128,2},{-127,2},{6,2},{2,2,2},{0,-126}}){Path source=raw(1,gt),out=temporary.resolve("refused-"+Arrays.hashCode(gt)+".vcf");assertThrows(IOException.class,()->convert(source,out));}
    }
    @Test void legacyFirstPhaseBitIsRecordedAndNewHeaderSemanticsAreUnavailable()throws Exception {
        Path source=raw(1,new int[]{3,5}),out=temporary.resolve("legacy.vcf");var result=convert(source,out);assertEquals(1,result.legacyFirstPhaseBits());assertEquals("22\t1\t.\tA\tC\t0|1",gtRows(out).get(0));
        byte[] bytes=Files.readAllBytes(source);byte[] before="VCFv4.3".getBytes(StandardCharsets.US_ASCII);for(int i=0;i<=bytes.length-before.length;i++){boolean match=true;for(int j=0;j<before.length;j++)if(bytes[i+j]!=before[j])match=false;if(match){bytes[i+6]='5';break;}}Files.write(source,bytes);assertThrows(EagleRecipe.UnavailableCapability.class,()->convert(source,temporary.resolve("newer.vcf")));
    }
    @Test void truncationCrcMissingEofAndTrailingDataCannotPassCompleteAdmission()throws Exception {
        byte[] raw=Files.readAllBytes(fixture("missing-raw.bcf"));for(int length:new int[]{1,8,raw.length-1}){Path path=temporary.resolve("short"+length+".bcf");Files.write(path,Arrays.copyOf(raw,length));assertThrows(IOException.class,()->convert(path,temporary.resolve("short"+length+".vcf")));}
        byte[] bgzf=Files.readAllBytes(fixture("missing-bgzf.bcf"));Path noEnd=temporary.resolve("no-eof.bcf");Files.write(noEnd,Arrays.copyOf(bgzf,bgzf.length-28));assertThrows(IOException.class,()->convert(noEnd,temporary.resolve("no-eof.vcf")));
        Path crc=temporary.resolve("crc.bcf");byte[] bad=bgzf.clone();int block=(bad[16]&255)|((bad[17]&255)<<8);bad[block+1-8]^=1;Files.write(crc,bad);assertThrows(IOException.class,()->convert(crc,temporary.resolve("crc.vcf")));
        Path tail=temporary.resolve("tail.bcf");Files.write(tail,bgzf);Files.write(tail,new byte[]{0},StandardOpenOption.APPEND);assertThrows(IOException.class,()->convert(tail,temporary.resolve("tail.vcf")));
    }
    @Test void sourceChangesOutputOverwriteAndOutputQuotaFailClosed()throws Exception {
        Path source=fixture("missing-raw.bcf"),out=temporary.resolve("existing.vcf");Files.writeString(out,"retain");assertThrows(IOException.class,()->convert(source,out));assertEquals("retain",Files.readString(out));
        assertThrows(IOException.class,()->EagleBcf.convert(source,temporary.resolve("small.vcf"),limits(),134217728,16,()->{}));
        var count=new AtomicInteger();assertThrows(IOException.class,()->EagleBcf.convert(source,temporary.resolve("changed.vcf"),limits(),134217728,67108864,()->{if(count.incrementAndGet()==20)try{Files.write(source,new byte[]{0},StandardOpenOption.APPEND);}catch(IOException failure){throw new IllegalStateException(failure);}}));assertTrue(count.get()>=20);
    }
}
