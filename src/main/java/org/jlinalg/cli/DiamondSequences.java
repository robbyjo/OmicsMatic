/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.DiamondRequest.need;

/** Complete bounded FASTA/four-line FASTQ census; never edits identifiers or sequence bytes. */
final class DiamondSequences {
    private DiamondSequences() { }
    record Sequence(long index,String id,String title,long length) { }
    record Population(String format,boolean gzip,List<Sequence> records,long decodedBytes) { }
    static final class Lines implements AutoCloseable {
        private final Reader reader;private final int maximum;private final Runnable cancellation;
        Lines(Path path,int maximum,Runnable cancellation)throws IOException {this(Files.newInputStream(path),maximum,cancellation);}
        Lines(InputStream stream,int maximum,Runnable cancellation){reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),65536);this.maximum=maximum;this.cancellation=cancellation;}
        String next()throws IOException {cancellation.run();var line=new StringBuilder();for(int c=reader.read();c!=-1;c=reader.read()){if(c=='\n')return line.length()>0&&line.charAt(line.length()-1)=='\r'?line.substring(0,line.length()-1):line.toString();need(line.length()<maximum,"DIAMOND line character capacity exceeded");line.append((char)c);}return line.length()==0?null:line.toString();}
        @Override public void close()throws IOException{reader.close();}
    }
    private static final class Counted extends FilterInputStream {
        long count;private final long maximum;
        Counted(InputStream stream,long maximum){super(stream);this.maximum=maximum;}
        @Override public int read()throws IOException{int value=in.read();if(value!=-1)add(1);return value;}
        @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0)add(n);return n;}
        private void add(int n)throws IOException {count=Math.addExact(count,n);need(count<=maximum,"DIAMOND decoded input byte capacity exceeded");}
    }
    static Population read(Path path,boolean reference,DiamondRequest.Limits limits,Runnable cancellation)throws IOException {
        var raw=new BufferedInputStream(Files.newInputStream(path));raw.mark(2);int a=raw.read(),b=raw.read();raw.reset();boolean gzip=a==31&&b==139;Counted counted;try{counted=new Counted(gzip?new GZIPInputStream(raw,65536):raw,limits.decodedBytes());}catch(IOException failure){raw.close();throw failure;}var records=new ArrayList<Sequence>();String format;
        try(var lines=new Lines(counted,limits.lineCharacters(),cancellation)) {
            String first=lines.next();need(first!=null,"Empty DIAMOND sequence input");
            if(first.startsWith(">")) {
                format="fasta";String title=first.substring(1);long length=0;
                for(String line=lines.next();line!=null;line=lines.next()) {
                    if(line.startsWith(">")){add(records,title,length,limits);title=line.substring(1);length=0;}
                    else {for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c==' '||c=='\t'||c=='\r')continue;need(c>=33&&c<=126,"Unsupported non-ASCII sequence symbol; original bytes retained");length++;need(length<=limits.recordLength(),"DIAMOND record length capacity exceeded");}}
                }
                add(records,title,length,limits);
            }else {
                need(!reference&&first.startsWith("@"),"Require complete FASTA reference or FASTA/four-line FASTQ query");format="fastq";String header=first;
                while(header!=null) {
                    need(header.startsWith("@"),"Invalid FASTQ record header");String sequence=lines.next(),plus=lines.next(),quality=lines.next();need(sequence!=null&&plus!=null&&quality!=null&&plus.startsWith("+"),"Incomplete four-line FASTQ record");need(sequence.length()==quality.length(),"FASTQ sequence/quality lengths differ");need(sequence.chars().allMatch(c->c>=33&&c<=126)&&quality.chars().allMatch(c->c>=33&&c<=126),"Unsupported FASTQ symbol/quality");add(records,header.substring(1),sequence.length(),limits);header=lines.next();
                }
            }
        }
        return new Population(format,gzip,List.copyOf(records),counted.count);
    }
    private static void add(List<Sequence> records,String title,long length,DiamondRequest.Limits limits)throws IOException {
        need(records.size()<limits.records(),"DIAMOND record count capacity exceeded");need(length>0&&length<=limits.recordLength(),"Require nonempty bounded DIAMOND records");int at=0;while(at<title.length()&&" \u0007\b\f\n\r\t\u000b\u0001".indexOf(title.charAt(at))<0)at++;need(at>0,"Empty/leading-space identifier is outside this profile; no identifier repair performed");String id=title.substring(0,at);need(id.chars().noneMatch(c->c<32||c==127),"Unsupported identifier control character");records.add(new Sequence(records.size(),id,title,length));
    }
    static Map<String,List<Sequence>> byId(Population p) {var values=new LinkedHashMap<String,List<Sequence>>();for(Sequence s:p.records())values.computeIfAbsent(s.id(),k->new ArrayList<>()).add(s);return values;}
}
