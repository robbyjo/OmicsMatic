/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.alignment;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jlinalg.alignment.SambambaStreamingBamAudit.Limits;
import org.jlinalg.alignment.SambambaStreamingBamAudit.FileIdentity;

/** Native streaming coordinate/virtual-offset view for complete BAI audits. No BAM values are rewritten. */
final class BamIndexStream implements AutoCloseable {
    interface Observer {
        void block(long address,int compressedBytes,int decodedBytes)throws IOException;
        void boundary(long virtualOffset)throws IOException;
    }
    record Row(long ordinal,int reference,int start,int end,int flag,long virtualStart,long virtualEnd) { }
    private record Header(List<String> canonical,Map<String,Map<String,String>> programs) { }
    final Bgzf in;final Limits limits;final int nr;final List<String> names;final List<Integer> lengths;final Observer observer;
    int previousRef=-1,previousPos=-1;boolean unplaced,ended;long records,logicalEnd;
    BamIndexStream(Path path,Limits limits,Observer observer)throws IOException {
        this.limits=java.util.Objects.requireNonNull(limits);this.observer=java.util.Objects.requireNonNull(observer);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>limits.compressedBytes()||Files.size(path)>=(1L<<48))
            throw new IOException("Require a regular BAM within its declared byte bound and the48-bit BGZF address space");
        in=new Bgzf(path,limits,observer);
        try {
            if(!Arrays.equals(exact(in,4),new byte[]{'B','A','M',1}))throw new IOException("BAM magic differs");
            int hl=integer(in);if(hl<1||hl>limits.headerBytes())throw new IOException("BAM header size is outside limits");
            String text=utf8(exact(in,hl));long dictionaryBytes=4;
            nr=integer(in);if(nr<0||nr>=16384)throw new IOException("Require fewer than16384 references in this Sambamba-compatible profile");
            var labels=new ArrayList<String>();var seen=new HashSet<String>();var referenceLengths=new ArrayList<Integer>();
            for(int i=0;i<nr;i++) {
                int n=integer(in);
                if(n<2||n>limits.headerBytes()||dictionaryBytes+8+n>limits.dictionaryBytes())throw new IOException("Reference-name/dictionary size exceeds limits");
                byte[] name=exact(in,n);if(name[n-1]!=0)throw new IOException("Reference name lacks NUL termination");
                for(int j=0;j<n-1;j++)if(name[j]==0)throw new IOException("Embedded NUL reference name");
                String label=utf8(Arrays.copyOf(name,n-1));if(!seen.add(label))throw new IOException("Repeated binary reference name");
                int length=integer(in);if(length<1)throw new IOException("Invalid reference length");
                labels.add(label);referenceLengths.add(length);dictionaryBytes+=8+n;
            }
            names=List.copyOf(labels);lengths=List.copyOf(referenceLengths);parseHeader(text,names,lengths);
        }catch(IOException|RuntimeException|Error failure){try{in.close();}catch(IOException close){failure.addSuppressed(close);}throw failure;}
    }
    Row next()throws IOException {
        if(ended)return null;
        try {
            long begin=in.virtualOffset();int first=in.read();
            if(first<0){ended=true;logicalEnd=begin;observer.boundary(begin);return null;}
            byte[] sz={(byte)first,0,0,0};System.arraycopy(exact(in,3),0,sz,1,3);int count=i32(sz,0);
            if(count<32||count>limits.recordBytes()||records>=limits.records())throw new IOException("BAM record size/count exceeds bounds");
                byte[] raw=exact(in,count);int ref=i32(raw,0),pos=i32(raw,4),flag=u16(raw,14),nl=raw[8]&255,nc=u16(raw,12),seq=i32(raw,16);
                int mate=i32(raw,20),matePos=i32(raw,24);
                if(ref< -1||ref>=nr||pos< -1||mate< -1||mate>=nr||matePos< -1||seq<0||nl<2)
                    throw new IOException("Invalid BAM core fields");
                long end=32L+nl+4L*nc+(seq+1L)/2+seq;if(end>raw.length)throw new IOException("BAM sequence/CIGAR/quality bounds exceed the record");
                if(raw[32+nl-1]!=0)throw new IOException("Read name lacks NUL termination");
                for(int j=32;j<32+nl-1;j++)if((raw[j]&255)<33||(raw[j]&255)>126||raw[j]=='@')throw new IOException("Require a standard printable ASCII BAM read name");
                String name=new String(raw,32,nl-1,StandardCharsets.US_ASCII);
                int referenceSpan=0,querySpan=0,leftClip=0,rightClip=0;boolean clipping=true;
                for(int j=0;j<nc;j++){
                    int op=i32(raw,32+nl+4*j),code=op&15,length=op>>>4;
                    if(code>8||length==0)throw new IOException("Unsupported or zero-length CIGAR operation");
                    if(code==0||code==2||code==3||code==7||code==8)referenceSpan=Math.addExact(referenceSpan,length);
                    if(code==0||code==1||code==4||code==7||code==8)querySpan=Math.addExact(querySpan,length);
                    if(clipping&&(code==4||code==5))leftClip=Math.addExact(leftClip,length);else clipping=false;
                }
                for(int j=nc-1;j>=0;j--){int op=i32(raw,32+nl+4*j),code=op&15;if(code!=4&&code!=5)break;rightClip=Math.addExact(rightClip,op>>>4);}
                if(nc>0&&querySpan!=seq)throw new IOException("CIGAR query length differs from sequence length");
                if((flag&4)==0&&(ref<0||pos<0||nc==0||referenceSpan<1||(long)pos+referenceSpan>lengths.get(ref)))
                    throw new IOException("Mapped alignment is outside its exact reference geometry");
                if(ref<0||pos<0)unplaced=true;
                else{if(unplaced||ref<previousRef||(ref==previousRef&&pos<previousPos))throw new IOException("BAM records are not in their declared coordinate order");previousRef=ref;previousPos=pos;}
                long score=0;int qualities=32+nl+4*nc+(seq+1)/2;
                for(int j=0;j<seq;j++){int q=raw[qualities+j]&255;if(q>93&&q!=255)throw new IOException("Invalid raw base-quality byte");if(q>=15)score+=q;}
                aux(raw,(int)end);
                boolean eligible=ref>=0&&(flag&(4|256|2048))==0;
                int five=(flag&16)==0?pos-leftClip:pos+referenceSpan+rightClip;
            if((ref<0)!=(pos<0))throw new IOException("BAI audit requires reference and position to be both present or both absent; partial coordinates are not repaired");
            int endPosition=ref<0?-1:Math.toIntExact((long)pos+Math.max(1,(flag&4)==0?referenceSpan:0));
            if(ref>=0&&(endPosition>lengths.get(ref)||endPosition>(1<<29)))throw new IOException("Alignment lies outside its reference or the BAI512-Mbp coordinate range; use a separately qualified appropriate index format");
            long virtualEnd=in.virtualOffset();observer.boundary(begin);observer.boundary(virtualEnd);
            return new Row(records++,ref,pos,endPosition,flag,begin,virtualEnd);
        }catch(ArithmeticException|IndexOutOfBoundsException e){throw new IOException("Invalid/overflowing BAM structure; no data repaired",e);}
    }
    FileIdentity identity(){if(!ended)throw new IllegalStateException("Complete BAM EOF not verified");return in.identity();}
    @Override public void close()throws IOException{in.close();}
    private static Header parseHeader(String text,List<String> names,List<Integer> lengths)throws IOException {
        var canonical=new ArrayList<String>();var programs=new LinkedHashMap<String,Map<String,String>>();var observedNames=new ArrayList<String>();var groups=new HashSet<String>();boolean hd=false;
        String[] lines=text.split("\n",-1);
        for(int row=0;row<lines.length;row++){
            String line=lines[row];if(line.isEmpty()&&row==lines.length-1)continue;
            if(line.indexOf('\r')>=0||line.indexOf('\0')>=0)throw new IOException("Unsupported CR or NUL in the raw SAM header");
            String[] fields=line.split("\t",-1);String type=fields[0];
            if(type.equals("@CO")){canonical.add(line);continue;}
            if(!Set.of("@HD","@SQ","@RG","@PG").contains(type))throw new IOException("Unsupported SAM header record");
            var tags=new TreeMap<String,String>();
            for(int j=1;j<fields.length;j++){
                if(fields[j].length()<4||fields[j].charAt(2)!=':'||tags.putIfAbsent(fields[j].substring(0,2),fields[j].substring(3))!=null)
                    throw new IOException("Invalid or repeated SAM header tag");
            }
            if(type.equals("@HD")){if(hd||!"coordinate".equals(tags.get("SO")))throw new IOException("Require one coordinate-sort header");hd=true;}
            if(type.equals("@SQ")){
                int index=observedNames.size();if(index>=names.size()||!names.get(index).equals(tags.get("SN"))||!Integer.toString(lengths.get(index)).equals(tags.get("LN")))throw new IOException("Complete text/binary reference dictionary differs");
                observedNames.add(tags.get("SN"));
            }
            if(type.equals("@RG")&&(tags.get("ID")==null||!groups.add(tags.get("ID"))))throw new IOException("Repeated or absent read-group ID");
            if(type.equals("@PG")&&(tags.get("ID")==null||programs.putIfAbsent(tags.get("ID"),Map.copyOf(tags))!=null))throw new IOException("Repeated or absent program ID");
            canonical.add(canonical(type,tags));
        }
        if(!hd||observedNames.size()!=names.size())throw new IOException("Incomplete coordinate header/reference dictionary");
        canonical.sort(String::compareTo);return new Header(List.copyOf(canonical),Map.copyOf(programs));
    }
    private static String canonical(String type,Map<String,String> tags){var b=new StringBuilder(type);new TreeMap<>(tags).forEach((k,v)->b.append('\t').append(k).append(':').append(v));return b.toString();}
    private static void aux(byte[] raw,int at)throws IOException {
        var tags=new HashSet<Integer>();
        while(at<raw.length){
            if(raw.length-at<3)throw new IOException("Truncated auxiliary tag");int a=raw[at++]&255,b=raw[at++]&255,type=raw[at++]&255;
            if(!((a>='A'&&a<='Z')||(a>='a'&&a<='z'))||!Character.isLetterOrDigit((char)b)||!tags.add(a*256+b))throw new IOException("Invalid or repeated auxiliary tag");
            if(a=='C'&&b=='G')throw new IOException("Long-CIGAR CG semantics require a separate profile");
            int width=switch(type){case 'A','c','C'->1;case 's','S'->2;case 'i','I','f'->4;default->0;};
            if(width>0){if(at+width>raw.length)throw new IOException("Truncated numeric auxiliary field");at+=width;}
            else if(type=='Z'||type=='H'){
                int start=at;while(at<raw.length&&raw[at]!=0){if(type=='H'&&Character.digit((char)raw[at],16)<0)throw new IOException("Invalid H auxiliary value");at++;}
                if(at==raw.length||(type=='H'&&(at-start)%2!=0))throw new IOException("Unterminated or odd-length auxiliary string");at++;
            }else if(type=='B'){
                if(raw.length-at<5)throw new IOException("Truncated auxiliary array");int subtype=raw[at++]&255,n=i32(raw,at);at+=4;
                int size=switch(subtype){case 'c','C'->1;case 's','S'->2;case 'i','I','f'->4;default->0;};
                long end=(long)at+(long)n*size;if(size==0||n<0||end>raw.length)throw new IOException("Invalid auxiliary array bounds");at=(int)end;
            }else throw new IOException("Unsupported auxiliary type: "+type);
        }
    }
    private static byte[] exact(InputStream in,int n)throws IOException{byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException("Truncated BGZF/BAM data");return b;}
    private static int integer(InputStream in)throws IOException{return i32(exact(in,4),0);}
    private static int i32(byte[] b,int at){return ByteBuffer.wrap(b,at,4).order(ByteOrder.LITTLE_ENDIAN).getInt();}
    private static int u16(byte[] b,int at){return(b[at]&255)|((b[at+1]&255)<<8);}
    private static byte[] le(int n){return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array();}
    private static String utf8(byte[] b)throws IOException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}
    /** Counts and hashes actual compressed bytes, refusing growth beyond the declared bound. */
    private static final class Source extends InputStream {
        final InputStream in;final long maximum;final java.security.MessageDigest digest;long count;boolean ended;String hash;
        Source(Path path,long maximum)throws IOException {
            this.maximum=maximum;
            try{digest=java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
            in=Files.newInputStream(path);
        }
        private void count(int n)throws IOException{if(count>maximum-n)throw new IOException("Compressed BAM bytes exceed the declared limit");count+=n;}
        @Override public int read()throws IOException {
            int value=in.read();if(value<0){ended=true;return -1;}count(1);digest.update((byte)value);return value;
        }
        @Override public int read(byte[] b,int off,int len)throws IOException {
            java.util.Objects.checkFromIndexSize(off,len,b.length);if(len==0)return 0;
            int n=in.read(b,off,len);if(n<0){ended=true;return -1;}if(n>0){count(n);digest.update(b,off,n);}return n;
        }
        FileIdentity identity(){
            if(!ended)throw new IllegalStateException("Complete compressed EOF was not verified");
            if(hash==null)hash=java.util.HexFormat.of().formatHex(digest.digest());return new FileIdentity(count,hash);
        }
        @Override public void close()throws IOException{in.close();}
    }
    /** Strict BGZF member framing, CRC, ISIZE, bounds and terminal empty member. */
    private static final class Bgzf extends InputStream {
        private final InputStream raw;private final Source source;private final long maximum;private long decoded;private byte[] block=new byte[0];private int at;private boolean ended;private long nextAddress,blockAddress;private final Observer observer;
        Bgzf(Path path,Limits limits,Observer observer)throws IOException{this.observer=observer;source=new Source(path,limits.compressedBytes());raw=new BufferedInputStream(source);maximum=limits.decodedBytes();}
        long virtualOffset(){return at==block.length?nextAddress<<16:(blockAddress<<16)|at;}
        FileIdentity identity(){if(!ended)throw new IllegalStateException("Terminal BGZF member was not verified");return source.identity();}
        private boolean next()throws IOException {
            if(ended)return false;int first=raw.read();if(first<0)throw new EOFException("Missing terminal BGZF member");
            byte[] h=new byte[12];h[0]=(byte)first;System.arraycopy(exact(raw,11),0,h,1,11);
            if((h[0]&255)!=31||(h[1]&255)!=139||h[2]!=8||h[3]!=4)throw new IOException("Require standard BGZF with FEXTRA only");
            int extraLength=u16(h,10);if(extraLength<6)throw new IOException("BGZF extra fields are incomplete");byte[] extra=exact(raw,extraLength);int size=-1;
            for(int p=0;p<extra.length;){if(extra.length-p<4)throw new IOException("Truncated BGZF subfield");int n=u16(extra,p+2);if(p+4+n>extra.length)throw new IOException("Invalid BGZF subfield length");if(extra[p]=='B'&&extra[p+1]=='C'){if(size!=-1||n!=2)throw new IOException("Repeated/invalid BGZF BC subfield");size=u16(extra,p+4)+1;}p+=4+n;}
            int payload=size-12-extraLength-8;if(payload<2||size>65536)throw new IOException("Invalid BGZF block size");
            byte[] compressed=exact(raw,payload),tail=exact(raw,8);long length=Integer.toUnsignedLong(i32(tail,4));if(length>65536)throw new IOException("BGZF ISIZE exceeds 64 KiB");
            var inflater=new Inflater(true);byte[] data=new byte[65537];int written=0;
            try{inflater.setInput(compressed);while(!inflater.finished()&&written<data.length){int n=inflater.inflate(data,written,data.length-written);if(n==0&&!inflater.finished())throw new IOException("Unfinished BGZF deflate data");written+=n;}if(!inflater.finished()||inflater.getRemaining()!=0||written!=length)throw new IOException("BGZF deflate length/remaining data differs");}
            catch(DataFormatException e){throw new IOException("Invalid BGZF deflate data",e);}finally{inflater.end();}
            CRC32 crc=new CRC32();crc.update(data,0,written);if(crc.getValue()!=Integer.toUnsignedLong(i32(tail,0)))throw new IOException("BGZF CRC differs");
            blockAddress=nextAddress;nextAddress=Math.addExact(nextAddress,size);
            if(nextAddress>=(1L<<48))throw new IOException("BGZF compressed address exceeds the48-bit index range");
            observer.block(blockAddress,size,written);
            if(written==0){if(size!=28||raw.read()!=-1)throw new IOException("Nonterminal or invalid empty BGZF member");ended=true;return false;}
            if(decoded>maximum-written)throw new IOException("Decoded BAM bytes exceed the declared limit");decoded+=written;block=Arrays.copyOf(data,written);at=0;return true;
        }
        @Override public int read()throws IOException{if(at==block.length&&!next())return -1;return block[at++]&255;}
        @Override public int read(byte[] b,int off,int len)throws IOException{java.util.Objects.checkFromIndexSize(off,len,b.length);if(len==0)return 0;if(at==block.length&&!next())return -1;int n=Math.min(len,block.length-at);System.arraycopy(block,at,b,off,n);at+=n;return n;}
        @Override public void close()throws IOException{raw.close();}
    }
}
