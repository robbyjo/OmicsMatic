/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Complete bounded text/gzip decoding and literal precision/reference ledgers. */
final class BigWigText {
    private BigWigText() { }
    private record Reference(int ordinal,long line,String chromosome,String token,long length) { }
    record Data(Map<String,Long> reference,List<org.jlinalg.genetics.BedGraphSignals.Segment> rows,Map<String,Object> audit) {
        Data {reference=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(reference));rows=List.copyOf(rows);audit=Map.copyOf(audit);}
    }
    static Data read(Path root,BigWigRequest.Spec request,boolean compare,long ledgerCapacity,Runnable check)throws IOException {
        var limit=new Decoded(request.decodedBytes(),check);var references=new ArrayList<Reference>();var lengths=new LinkedHashMap<String,Long>();long comments=0,headers=0;int largeReferences=0;
        try(var lines=new Text(Files.newInputStream(root.resolve("inputs/reference.sizes")),request.lineChars(),limit)){
            for(String line=lines.next();line!=null;line=lines.next()){check.run();String text=strip(line);if(text.isEmpty()||text.startsWith("#")){comments++;continue;}String[] fields=fields(text);require(fields.length==2,"Reference requires two columns at line "+lines.line);name(fields[0]);long length=coordinate(fields[1]);require(length>0,"Reference length must be positive at line "+lines.line);require(lengths.putIfAbsent(fields[0],length)==null,"Duplicate reference chromosome at line "+lines.line);require(references.size()<100000,"Reference exceeds100000 chromosomes");references.add(new Reference(references.size()+1,lines.line,fields[0],fields[1],length));if(length>Integer.MAX_VALUE)largeReferences++;}
        }
        require(!lengths.isEmpty(),"Exact chromosome-size input is empty");var rows=new ArrayList<org.jlinalg.genetics.BedGraphSignals.Segment>();var counts=new HashMap<String,Integer>();var closed=new HashSet<String>();String previous=null;long previousEnd=0;int decimalChanges=0,doubleChanges=0,doubleUnderflow=0,floatUnderflow=0,largeCoordinates=0;long valueLedgerBytes;
        try(var ledger=new BigWigFiles.Lines(root.resolve("VALUE-LEDGER.tsv"),compare,Math.min(ledgerCapacity,request.ledgerBytes()),check);var lines=new Text(signal(root.resolve(request.member()),request.format().equals("gzip"),check),request.lineChars(),limit)){
            ledger.line("ordinal\tsource_line\tchromosome\tstart_token\tend_token\tvalue_token\tfloat64_hex\tfloat32_hex\tfloat32_bits\tliteral_equals_float32\tliteral_equals_float64\tunderflow_stage");
            for(String line=lines.next();line!=null;line=lines.next()){check.run();String text=strip(line);if(text.isEmpty()||text.startsWith("#")){comments++;continue;}String[] f=fields(text);boolean coordinates=f.length>=3&&f[1].matches("[0-9]+")&&f[2].matches("[0-9]+");if(!coordinates&&!lengths.containsKey(f[0])&&(f[0].equals("track")||f[0].equals("browser"))){require(rows.isEmpty(),"Track/browser metadata is permitted only before the first signal row; line "+lines.line);headers++;continue;}
                require(f.length==4,"bedGraph requires four columns at source line "+lines.line);require(rows.size()<request.records(),"Complete signal exceeds declared record capacity at line "+lines.line);name(f[0]);Long length=lengths.get(f[0]);require(length!=null,"Signal chromosome absent from exact sizes at line "+lines.line);long start=coordinate(f[1]),end=coordinate(f[2]);require(start<end&&end<=length,"Require0<=start<end<=declared length at source line "+lines.line);
                if(!f[0].equals(previous)){if(previous!=null)closed.add(previous);require(!closed.contains(f[0]),"Chromosome reappears after another input block at line "+lines.line);previous=f[0];previousEnd=0;}require(start>=previousEnd,"Unsorted/overlapping bedGraph interval at source line "+lines.line);previousEnd=end;
                require(f[3].matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require a finite decimal signal token at line "+lines.line);double value;BigDecimal original;try{value=Double.parseDouble(f[3]);original=new BigDecimal(f[3]);}catch(NumberFormatException invalid){throw new IOException("Invalid bounded signal decimal at source line "+lines.line,invalid);}require(Math.abs((long)original.scale())<=10000&&Double.isFinite(value),"Signal decimal scale/float64 range exceeds supported bounds at line "+lines.line);float stored=(float)value;require(Float.isFinite(stored),"Signal overflows float32 at line "+lines.line);
                boolean sameFloat=original.compareTo(new BigDecimal((double)stored))==0,sameDouble=original.compareTo(new BigDecimal(value))==0;require(!request.precision().equals("exact")||sameFloat,"Literal decimal loses precision in float32 at line "+lines.line+"; exact policy refuses conversion. Review the value or explicitly choose round.");String underflow="NONE";if(!sameFloat)decimalChanges++;if(!sameDouble)doubleChanges++;if(original.signum()!=0&&value==0){doubleUnderflow++;underflow="FLOAT64";}else if(value!=0&&stored==0){floatUnderflow++;underflow="FLOAT32";}if(end>Integer.MAX_VALUE)largeCoordinates++;
                rows.add(new org.jlinalg.genetics.BedGraphSignals.Segment(f[0],start,end,value));counts.merge(f[0],1,Integer::sum);ledger.line(rows.size()+"\t"+lines.line+"\t"+f[0]+"\t"+f[1]+"\t"+f[2]+"\t"+f[3]+"\t"+Double.toHexString(value)+"\t"+Float.toHexString(stored)+"\t"+java.util.HexFormat.of().toHexDigits(Float.floatToRawIntBits(stored))+"\t"+sameFloat+"\t"+sameDouble+"\t"+underflow);
            }
            ledger.end();valueLedgerBytes=ledger.bytes();
        }
        require(!rows.isEmpty()||request.dictionary().equals("all"),"Empty signal requires explicitly declared all-reference dictionary; missing signal is not measured zero");var storedNames=new ArrayList<>(request.dictionary().equals("all")?lengths.keySet():counts.keySet());storedNames.sort((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));var ids=new HashMap<String,Integer>();for(String chr:storedNames)ids.put(chr,ids.size());
        try(var ledger=new BigWigFiles.Lines(root.resolve("REFERENCE-LEDGER.tsv"),compare,Math.min(request.ledgerBytes(),ledgerCapacity-valueLedgerBytes),check)){ledger.line("ordinal\tsource_line\tchromosome\tlength_token\tdeclared_bases\tstored\tchromosome_id\tsignal_records");for(var r:references){check.run();Integer id=ids.get(r.chromosome);ledger.line(r.ordinal+"\t"+r.line+"\t"+r.chromosome+"\t"+r.token+"\t"+r.length+"\t"+(id!=null)+"\t"+(id==null?"NA":id.toString())+"\t"+counts.getOrDefault(r.chromosome,0));}ledger.end();}
        var audit=new LinkedHashMap<String,Object>();audit.put("complete_reference_chromosomes",lengths.size());audit.put("stored_reference_chromosomes",ids.size());audit.put("omitted_unused_reference_chromosomes",lengths.size()-ids.size());audit.put("complete_signal_records",rows.size());audit.put("decoded_UTF8_bytes",limit.used);audit.put("source_comments_or_blank_lines",comments);audit.put("initial_track_or_browser_lines",headers);audit.put("literal_decimal_values_changed_in_float32",decimalChanges);audit.put("literal_decimal_values_changed_in_float64",doubleChanges);audit.put("literal_values_underflowed_in_float64",doubleUnderflow);audit.put("float64_values_underflowed_in_float32",floatUnderflow);audit.put("reference_lengths_above_signed31",largeReferences);audit.put("signal_records_above_signed31",largeCoordinates);audit.put("source_records_filtered",0);audit.put("source_identity_aliases_applied",false);return new Data(lengths,rows,audit);
    }
    private static String strip(String value)throws IOException {require(value.chars().noneMatch(c->c<32&&c!='\t'||c==127),"Unsupported source control character; raw source retained");int a=0,b=value.length();while(a<b&&(value.charAt(a)==' '||value.charAt(a)=='\t'))a++;while(b>a&&(value.charAt(b-1)==' '||value.charAt(b-1)=='\t'))b--;return value.substring(a,b);}
    private static String[] fields(String line){return line.split("[ \\t]+",5);}
    private static void name(String name)throws IOException {require(!name.isEmpty()&&name.getBytes(StandardCharsets.UTF_8).length<=2048&&name.codePoints().noneMatch(c->c<=32||Character.isWhitespace(c)||Character.isISOControl(c)),"Invalid literal chromosome identity");}
    private static long coordinate(String token)throws IOException {require(token.matches("[0-9]+"),"Require unsigned decimal coordinates/lengths");try{long n=Long.parseLong(token);require(n<=0xffffffffL,"Coordinate/length exceeds unsigned32");return n;}catch(NumberFormatException invalid){throw new IOException("Coordinate/length overflow",invalid);}}
    private static InputStream signal(Path path,boolean gzip,Runnable check)throws IOException {InputStream raw=Files.newInputStream(path);try{return gzip?new StrictGzip(raw,check):raw;}catch(IOException|RuntimeException failure){raw.close();throw failure;}}
    private static final class Decoded {final long maximum;final Runnable check;long used;Decoded(long maximum,Runnable check){this.maximum=maximum;this.check=check;}void add(int n)throws IOException {check.run();used+=n;require(used<=maximum,"Complete decoded input/reference UTF8 bytes exceed declared capacity");}}
    private static final class Text implements AutoCloseable {
        final BufferedReader reader;final int maximum;long line;boolean skipLf;
        Text(InputStream input,int maximum,Decoded limit){this.maximum=maximum;var counted=new java.io.FilterInputStream(input){@Override public int read()throws IOException {int n=in.read();if(n>=0)limit.add(1);return n;}@Override public int read(byte[] b,int off,int len)throws IOException {int n=in.read(b,off,len);if(n>0)limit.add(n);return n;}};reader=new BufferedReader(new InputStreamReader(counted,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),8192);}
        String next()throws IOException {var value=new StringBuilder();while(true){int c=reader.read();if(c<0){if(value.length()==0)return null;line++;return value.toString();}if(skipLf){skipLf=false;if(c=='\n')continue;}if(c=='\r'||c=='\n'){skipLf=c=='\r';line++;return value.toString();}require(value.length()<maximum,"Complete source line exceeds declared character capacity");value.append((char)c);}}
        @Override public void close()throws IOException {reader.close();}
    }
    /** Complete concatenated gzip members; CRC, size, optional header CRC and exact compressed EOF are mandatory. */
    private static final class StrictGzip extends InputStream {
        final PushbackInputStream source;final Inflater inflater=new Inflater(true);final byte[] compressed=new byte[8192],one=new byte[1];final CRC32 crc=new CRC32();final Runnable check;int inputLength,members;long decoded;boolean active,complete,closed;
        StrictGzip(InputStream raw,Runnable check)throws IOException {source=new PushbackInputStream(raw,compressed.length);this.check=check;try{header();}catch(IOException|RuntimeException failure){inflater.end();throw failure;}}
        int required()throws IOException {int n=source.read();if(n<0)throw new EOFException("Truncated gzip member");return n;}
        int headerByte(CRC32 header,int[] count)throws IOException {require(++count[0]<=1048576,"Complete gzip header exceeds1MiB capacity");if((count[0]&1023)==0)check.run();int n=required();header.update(n);return n;}
        void header()throws IOException {check.run();require(++members<=100000,"Gzip member population exceeds capacity");var header=new CRC32();int[] count={0};require(headerByte(header,count)==31&&headerByte(header,count)==139&&headerByte(header,count)==8,"Invalid gzip member or trailing non-gzip bytes");int flags=headerByte(header,count);require((flags&224)==0,"Reserved gzip header flags");for(int i=0;i<6;i++)headerByte(header,count);if((flags&4)!=0){int n=headerByte(header,count)|headerByte(header,count)<<8;for(int i=0;i<n;i++)headerByte(header,count);}for(int flag:new int[]{8,16})if((flags&flag)!=0)while(headerByte(header,count)!=0){/* bounded above */}if((flags&2)!=0)require((required()|required()<<8)==(header.getValue()&65535),"Gzip header CRC differs");crc.reset();decoded=0;inflater.reset();active=true;}
        long uint32()throws IOException {long n=0;for(int i=0;i<4;i++)n|=(long)required()<<(8*i);return n;}
        void footer()throws IOException {int remaining=inflater.getRemaining();if(remaining>0)source.unread(compressed,inputLength-remaining,remaining);require(uint32()==crc.getValue()&&uint32()==decoded,"Gzip payload CRC or size differs");active=false;int next=source.read();if(next<0)complete=true;else{source.unread(next);header();}}
        @Override public int read()throws IOException {return read(one,0,1)<0?-1:one[0]&255;}
        @Override public int read(byte[] b,int off,int len)throws IOException {java.util.Objects.checkFromIndexSize(off,len,b.length);require(!closed,"Closed gzip input");if(len==0)return 0;if(complete)return -1;while(true){check.run();if(inflater.finished()){footer();if(complete)return -1;}if(inflater.needsInput()){inputLength=source.read(compressed);if(inputLength<0)throw new EOFException("Truncated gzip deflate payload");if(inputLength==0)continue;inflater.setInput(compressed,0,inputLength);}try{int n=inflater.inflate(b,off,len);if(n>0){crc.update(b,off,n);decoded=(decoded+n)&0xffffffffL;return n;}require(!inflater.needsDictionary()&&(inflater.needsInput()||inflater.finished()),"Invalid gzip deflate state");}catch(DataFormatException invalid){throw new IOException("Invalid gzip deflate payload",invalid);}}}
        @Override public void close()throws IOException {if(!closed){closed=true;inflater.end();source.close();}}
    }
    private static void require(boolean condition,String message)throws IOException {BigWigFiles.require(condition,message);}
}
