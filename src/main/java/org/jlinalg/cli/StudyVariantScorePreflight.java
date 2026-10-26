/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import com.github.luben.zstd.ZstdInputStream;
import org.jlinalg.pipeline.DataFormat;

/** Admission scan before genotype decoding; verifies bounded physical framing, not biological QC. */
final class StudyVariantScorePreflight {
    record Inventory(int samples,long records,long variants,long decompressedBytes,long maximumRecordBytes) { }
    private StudyVariantScorePreflight() { }
    static Inventory scan(Path path,DataFormat format,Map<String,Object> g,long remainingVariants,long remainingCells)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>bytes(g,"maximum_source_mib"))throw new IllegalArgumentException("Variant source is missing or exceeds maximum_source_mib");
        Inventory inventory=format==DataFormat.BGEN?bgen(path,g):vcf(path,format,g);
        if(inventory.records>remainingVariants||inventory.variants>remainingVariants)throw new IllegalArgumentException("Complete source scan exceeds shared maximum_source_variants");
        if(Math.multiplyExact(inventory.variants,inventory.samples)>remainingCells)throw new IllegalArgumentException("Complete source scan exceeds shared maximum_cells");return inventory;
    }
    private static Inventory vcf(Path path,DataFormat format,Map<String,Object> g)throws IOException {
        try(var input=open(path,bytes(g,"maximum_decompressed_mib"))){
            long records=0,variants=0,maximum=0;int samples;long header=bytes(g,"maximum_header_mib"),record=bytes(g,"maximum_record_mib");
            if(format==DataFormat.BCF){
                byte[] magic=input.readNBytes(5);if(magic.length!=5||magic[0]!='B'||magic[1]!='C'||magic[2]!='F'||magic[3]!=2||(magic[4]!=1&&magic[4]!=2))throw new IOException("Invalid BCF2 header");
                long length=u32(input);admit(length,header,"BCF header");retainedHeader(length,g);byte[] text=input.readNBytes((int)length);if(text.length!=length)throw new EOFException("Truncated BCF header");samples=headerSamples(decode(text).replace("\0",""),g);
                while(true){int first=input.read();if(first<0)break;long shared=u32(input,first),individual=u32(input),size=Math.addExact(shared,individual);admit(size,record,"BCF record");if(shared<24)throw new IOException("Short BCF shared record");byte[] fixed=input.readNBytes(24);if(fixed.length!=24)throw new EOFException("Truncated BCF fixed record");long alleles=(integer(fixed,16)>>>16)&65535L;long n=integer(fixed,20)&0xffffffL;if(n!=samples||alleles<1)throw new IOException("BCF record dimensions differ from header");input.skipNBytes(size-24);records++;variants+=alleles-1;maximum=Math.max(maximum,size);counts(g,records,variants,samples);}
            }else{
                StringBuilder text=new StringBuilder();long headerBytes=0;String line;
                do{byte[] raw=line(input,Math.min(header,record));if(raw==null)throw new EOFException("VCF sample header is absent");headerBytes+=raw.length+1;admit(headerBytes,header,"VCF header");retainedHeader(headerBytes,g);line=decode(raw);if(!line.startsWith("#"))throw new IOException("VCF records precede sample header");text.append(line).append('\n');}while(!line.startsWith("#CHROM\t"));samples=headerSamples(text.toString(),g);
                for(byte[] raw;(raw=line(input,record))!=null;){if(raw.length==0)continue;String prefix=prefix(raw,9);String[] fields=prefix.split("\t",-1);int tabs=0;for(byte value:raw)if(value=='\t')tabs++;if(fields.length<9||tabs!=samples+8||raw[0]=='#')throw new IOException("VCF record/sample column count differs from header");long alternates=fields[4].equals(".")?0:1+fields[4].chars().filter(ch->ch==',').count();records++;variants+=alternates;maximum=Math.max(maximum,raw.length);counts(g,records,variants,samples);}
            }
            return new Inventory(samples,records,variants,input.count,maximum);
        }
    }
    private static Inventory bgen(Path path,Map<String,Object> g)throws IOException {
        try(var input=new RandomAccessFile(path.toFile(),"r")){
            long offset=u32(input),header=u32(input),variants=u32(input),sampleCount=u32(input);byte[] magic=new byte[4];input.readFully(magic);
            if(!(java.util.Arrays.equals(magic,new byte[]{'b','g','e','n'})||java.util.Arrays.equals(magic,new byte[4])))throw new IOException("Invalid BGEN magic");
            admit(header,bytes(g,"maximum_header_mib"),"BGEN header");if(header<20||offset<header||offset+4>input.length())throw new IOException("Invalid BGEN header/offset");if(sampleCount<1||sampleCount>limit(g,"maximum_samples"))throw new IllegalArgumentException("BGEN sample count exceeds maximum_samples");
            counts(g,variants,variants,(int)sampleCount);input.seek(header);long flags=u32(input);int compression=(int)(flags&3);if(compression>2||((flags>>>2)&15)!=2)throw new IOException("Only BGEN layout2 with uncompressed/zlib/zstd blocks is supported");
            if((flags&0x80000000L)!=0){input.seek(4+header);long size=u32(input),n=u32(input);admit(header+size,bytes(g,"maximum_header_mib"),"BGEN sample header");retainedHeader(header+size,g);if(4L*(header+size)+256L*sampleCount>bytes(g,"maximum_retained_mib"))throw new IllegalArgumentException("Estimated retained BGEN sample metadata exceeds maximum_retained_mib");if(n!=sampleCount||size<8||header+size>offset)throw new IOException("Invalid BGEN sample identifier block");for(int i=0;i<n;i++){long length=u16(input);skip(input,length);if(input.getFilePointer()>4+header+size)throw new IOException("BGEN sample strings exceed block");}}
            input.seek(offset+4);long decompressed=offset+4,maximum=0;if(decompressed>bytes(g,"maximum_decompressed_mib"))throw new IllegalArgumentException("BGEN source exceeds maximum_decompressed_mib");
            for(long j=0;j<variants;j++){
                long start=input.getFilePointer();for(int i=0;i<3;i++)skip(input,u16(input));u32(input);int alleles=u16(input);if(alleles!=2)throw new IOException("BGEN scoring reader requires biallelic source records");for(int i=0;i<alleles;i++){long length=u32(input);admit(length,bytes(g,"maximum_record_mib"),"BGEN allele");skip(input,length);}long stored=u32(input);admit(stored,bytes(g,"maximum_record_mib"),"BGEN compressed record");long raw=stored;
                if(compression==0){if(decompressed+(input.getFilePointer()-start)+stored>bytes(g,"maximum_decompressed_mib"))throw new IllegalArgumentException("BGEN source exceeds maximum_decompressed_mib");skip(input,stored);}
                else{
                    if(stored<4)throw new IOException("Short BGEN compressed record");raw=u32(input);admit(raw,bytes(g,"maximum_record_mib"),"BGEN decompressed record");if(decompressed+(input.getFilePointer()-start)+raw>bytes(g,"maximum_decompressed_mib"))throw new IllegalArgumentException("BGEN source exceeds maximum_decompressed_mib");var limited=new RafInput(input,stored-4);long actual=0;byte[] buffer=new byte[65536];try(InputStream decoder=compression==1?new InflaterInputStream(limited):new ZstdInputStream(limited)){for(int n;(n=decoder.read(buffer))>=0;){actual+=n;if(actual>raw)throw new IOException("BGEN decompression exceeds declared/registered length");}}if(actual!=raw||limited.remaining!=0)throw new IOException("BGEN compressed block size differs from declaration");
                }
                long physical=input.getFilePointer()-start;admit(physical,bytes(g,"maximum_record_mib"),"BGEN complete record");maximum=Math.max(maximum,Math.max(physical,raw));decompressed+=physical-stored+raw+(compression==0?0:4);if(decompressed>bytes(g,"maximum_decompressed_mib"))throw new IllegalArgumentException("BGEN source exceeds maximum_decompressed_mib");
            }
            if(input.getFilePointer()!=input.length())throw new IOException("BGEN trailing data disagree with declared variant count");return new Inventory((int)sampleCount,variants,variants,decompressed,maximum);
        }
    }
    private static void counts(Map<String,Object> g,long records,long variants,int samples){if(records>limit(g,"maximum_source_variants")||variants>limit(g,"maximum_source_variants"))throw new IllegalArgumentException("Variant inventory exceeds maximum_source_variants");if(Math.multiplyExact(variants,samples)>limit(g,"maximum_cells"))throw new IllegalArgumentException("Variant inventory exceeds maximum_cells");}
    private static int headerSamples(String text,Map<String,Object> g)throws IOException {String last=null;for(String line:text.split("\n"))if(line.startsWith("#CHROM\t")){if(last!=null)throw new IOException("Duplicate VCF sample header");last=line.stripTrailing();}if(last==null)throw new IOException("VCF/BCF sample header is absent");long declared=last.chars().filter(ch->ch=='\t').count()-8;if(declared<1||declared>limit(g,"maximum_samples"))throw new IllegalArgumentException("Variant sample count exceeds positive maximum_samples");if(4L*text.getBytes(StandardCharsets.UTF_8).length+256L*declared>bytes(g,"maximum_retained_mib"))throw new IllegalArgumentException("Estimated retained variant sample metadata exceeds maximum_retained_mib");String[] fields=last.split("\t",-1);int n=(int)declared;var unique=new HashSet<String>();for(int i=9;i<fields.length;i++)if(fields[i].isBlank()||!unique.add(fields[i]))throw new IOException("Variant sample IDs must be unique and nonblank");return n;}
    private static void retainedHeader(long bytes,Map<String,Object> g){if(bytes*4>bytes(g,"maximum_retained_mib"))throw new IllegalArgumentException("Estimated retained variant header exceeds maximum_retained_mib");}
    private static byte[] line(InputStream input,long maximum)throws IOException {var out=new ByteArrayOutputStream();for(int value;(value=input.read())>=0;){if(value=='\n')return out.toByteArray();if(out.size()>=maximum)throw new IllegalArgumentException("VCF line exceeds maximum_header_mib/maximum_record_mib");out.write(value);}return out.size()==0?null:out.toByteArray();}
    private static String prefix(byte[] raw,int tabs){int end=0;for(;end<raw.length;end++)if(raw[end]=='\t'&&--tabs==0)break;return new String(raw,0,end,StandardCharsets.UTF_8);}
    private static String decode(byte[] raw)throws IOException {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    private static void admit(long value,long maximum,String kind){if(value<0||value>maximum||value>Integer.MAX_VALUE)throw new IllegalArgumentException(kind+" exceeds registered byte bound");}
    private static long limit(Map<String,Object> g,String key){return ((Number)g.get(key)).longValue();}
    private static long bytes(Map<String,Object> g,String key){return limit(g,key)*1048576L;}
    private static long integer(byte[] data,int at){return (data[at]&255L)|((data[at+1]&255L)<<8)|((data[at+2]&255L)<<16)|((data[at+3]&255L)<<24);}
    private static long u32(InputStream input)throws IOException {int first=input.read();if(first<0)throw new EOFException("Truncated variant integer");return u32(input,first);}
    private static long u32(InputStream input,int first)throws IOException {byte[] tail=input.readNBytes(3);if(tail.length!=3)throw new EOFException("Truncated variant integer");return first|((tail[0]&255L)<<8)|((tail[1]&255L)<<16)|((tail[2]&255L)<<24);}
    private static long u32(RandomAccessFile input)throws IOException {return Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));}
    private static int u16(RandomAccessFile input)throws IOException {return Short.toUnsignedInt(Short.reverseBytes(input.readShort()));}
    private static void skip(RandomAccessFile input,long bytes)throws IOException {long end=Math.addExact(input.getFilePointer(),bytes);if(end>input.length())throw new EOFException("Truncated BGEN frame");input.seek(end);}
    private static Counted open(Path path,long maximum)throws IOException {var raw=new BufferedInputStream(Files.newInputStream(path),65536);raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();try{return new Counted(a==31&&b==139?new BufferedInputStream(new GZIPInputStream(raw,65536),65536):raw,maximum);}catch(IOException|RuntimeException invalid){raw.close();throw invalid;}}
    private static final class Counted extends FilterInputStream {
        long count;private final long maximum;
        Counted(InputStream input,long maximum){super(input);this.maximum=maximum;}
        private void add(long n){count+=n;if(count>maximum)throw new IllegalArgumentException("Variant stream exceeds maximum_decompressed_mib");}
        @Override public int read()throws IOException {int v=in.read();if(v>=0)add(1);return v;}
        @Override public int read(byte[] b,int off,int length)throws IOException {int n=in.read(b,off,length);if(n>0)add(n);return n;}
        @Override public long skip(long n)throws IOException {long v=in.skip(n);add(v);return v;}
    }
    private static final class RafInput extends InputStream {
        private final RandomAccessFile file;long remaining;
        RafInput(RandomAccessFile file,long remaining){this.file=file;this.remaining=remaining;}
        @Override public int read()throws IOException {if(remaining==0)return -1;int v=file.read();if(v<0)throw new EOFException("Truncated compressed BGEN payload");remaining--;return v;}
        @Override public int read(byte[] b,int offset,int length)throws IOException {if(remaining==0)return -1;int n=file.read(b,offset,(int)Math.min(length,remaining));if(n<0)throw new EOFException("Truncated compressed BGEN payload");remaining-=n;return n;}
    }
}
