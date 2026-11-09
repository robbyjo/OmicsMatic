/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.assembly;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Bounded, streaming FASTA/FASTQ input admission for the hifiasm adapter.
 * Plain/gzip detection uses bytes, not file extensions. Wrapped sequence and
 * quality lines are accepted. Literal case, U, ambiguity, headers and qualities
 * are retained; this class does not emulate upstream read correction.
 */
public final class HifiasmReads {
    private HifiasmReads(){ }
    public record Limits(long encodedBytes,long decodedBytes,int lineBytes,int readBases,int records,long bases,long headerBytes){
        public Limits{if(encodedBytes<1||decodedBytes<1||lineBytes<1||readBases<1||records<1||bases<1||headerBytes<1)throw new IllegalArgumentException("Declare positive bounded complete read capacities");}
    }
    public record Read(String header,String id,String sequence,String quality){
        public Read{Objects.requireNonNull(header);Objects.requireNonNull(id);Objects.requireNonNull(sequence);if(!id.equals(identity(header))||sequence.isEmpty()||!sequence.matches("[ACGTRYSWKMBDHVNUacgtryswkmbdhvnu]+")||quality!=null&&(quality.length()!=sequence.length()||quality.chars().anyMatch(c->c<33||c>126)))throw new IllegalArgumentException("Invalid literal read/header/Phred33 family");}
        public boolean fastq(){return quality!=null;}
    }
    public record ReadInfo(String header,int bases,boolean qualityPresent){ }
    /** Per-file complete identity census. Original sequence/quality bytes remain in the input file. */
    public record Inspection(String format,Map<String,ReadInfo> reads,long bases,int minimumLength,int maximumLength,long lowercaseBases,long uracilBases,long ambiguousBases,long encodedBytes,long decodedBytes){
        public Inspection{reads=Collections.unmodifiableMap(new LinkedHashMap<>(reads));}
    }
    public record Pair(String first,String second,String pairId){ }
    public record PairedInspection(Inspection first,Inspection second,List<Pair> pairs){public PairedInspection{pairs=List.copyOf(pairs);}}

    /** Inspect all records; duplicate original kseq identities fail rather than being renamed. */
    public static Inspection inspect(InputStream supplied,Limits limits,boolean requireFastq)throws IOException{
        try(Reader reader=new Reader(supplied,limits,requireFastq)){while(reader.next()!=null){/* Census is built by the bounded reader. */}return reader.inspection();}
    }
    /** Validate complete synchronized identity/order; only the explicit terminal /1,/2 convention is recognized. */
    public static PairedInspection inspectPair(InputStream first,InputStream second,Limits limits,boolean requireFastq)throws IOException{
        try(Reader a=new Reader(first,limits,requireFastq);Reader b=new Reader(second,limits,requireFastq)){
            var pairs=new ArrayList<Pair>();var seen=new HashSet<String>();
            for(;;){Read x=a.next(),y=b.next();if(x==null||y==null){if(x!=null||y!=null)throw new IOException("Hi-C mates have different complete record counts");break;}String id=pair(x.id(),1);if(!id.equals(pair(y.id(),2))||!seen.add(id))throw new IOException("Hi-C pair identity/order is inconsistent or duplicated at record "+(pairs.size()+1));pairs.add(new Pair(x.id(),y.id(),id));}
            return new PairedInspection(a.inspection(),b.inspection(),pairs);
        }
    }
    private static String pair(String id,int mate)throws IOException{if(id.endsWith("/1")||id.endsWith("/2")){if(!id.endsWith("/"+mate))throw new IOException("Hi-C terminal mate suffix disagrees with its declared input");return id.substring(0,id.length()-2);}return id;}
    private static String identity(String header){
        Objects.requireNonNull(header);if(header.isEmpty()||header.charAt(0)==' '||header.charAt(0)=='\t')throw new IllegalArgumentException("Read header requires a literal first identity token");int end=header.length();for(int i=0;i<header.length();i++){char c=header.charAt(i);if(c<32&&c!='\t'||c>126)throw new IllegalArgumentException("Read headers require printable ASCII or tab");if(end==header.length()&&(c==' '||c=='\t'))end=i;}if(end==0)throw new IllegalArgumentException("Empty read identity");return header.substring(0,end);
    }

    /** Owns and closes the supplied stream. Call next through EOF before requesting the complete census. */
    public static final class Reader implements AutoCloseable {
        private final Limits limits;
        private final Counted encoded,decoded;
        private final Map<String,ReadInfo> census=new LinkedHashMap<>();
        private final boolean requireFastq;
        private String pending,format;
        private long bases,headers,lowercase,uracil,ambiguous;
        private int minimum=Integer.MAX_VALUE,maximum;
        private boolean ended,closed;
        public Reader(InputStream supplied,Limits limits,boolean requireFastq)throws IOException{
            this.limits=Objects.requireNonNull(limits);this.requireFastq=requireFastq;encoded=new Counted(Objects.requireNonNull(supplied),limits.encodedBytes(),"encoded");
            try{var peek=new PushbackInputStream(new BufferedInputStream(encoded,65536),2);int a=peek.read(),b=peek.read();if(b>=0)peek.unread(b);if(a>=0)peek.unread(a);InputStream content=a==31&&b==139?new GZIPInputStream(peek,65536):peek;decoded=new Counted(new BufferedInputStream(content,65536),limits.decodedBytes(),"decoded");}
            catch(IOException|RuntimeException failure){encoded.close();throw failure;}
        }
        public Read next()throws IOException{
            if(closed)throw new IOException("Read stream is closed");if(ended)return null;
            try{
                String line=pending==null?line():pending;pending=null;if(line==null){ended=true;if(census.isEmpty())throw new IOException("Empty input is not an admitted assembly read set");return null;}
                if(line.isEmpty()||line.charAt(0)!='>'&&line.charAt(0)!='@')throw new IOException("Require a complete FASTA/FASTQ header");String current=line.charAt(0)=='>'?"FASTA":"FASTQ";if(format==null)format=current;if(!format.equals(current)||requireFastq&&!current.equals("FASTQ"))throw new IOException("Mixed formats or FASTA supplied to a FASTQ-required mode");String header=line.substring(1),id=identity(header);headers=Math.addExact(headers,header.length());if(headers>limits.headerBytes()||census.size()>=limits.records()||census.containsKey(id))throw new IOException("Complete read identity/header census exceeds capacity or contains a duplicate: "+id);
                var sequence=new StringBuilder();String quality=null;
                if(current.equals("FASTA")){
                    while((line=line())!=null){if(line.startsWith(">")){pending=line;break;}append(sequence,line,false);}if(sequence.isEmpty())throw new IOException("Empty FASTA record");
                }else{
                    boolean plus=false;while((line=line())!=null){if(line.startsWith("+")){if(line.length()>1&&!identity(line.substring(1)).equals(id))throw new IOException("Repeated FASTQ + identity differs from original header");plus=true;break;}append(sequence,line,false);}if(!plus||sequence.isEmpty())throw new IOException("Truncated/empty FASTQ sequence or missing + separator");var scores=new StringBuilder();while(scores.length()<sequence.length()&&(line=line())!=null)append(scores,line,true);if(scores.length()!=sequence.length())throw new IOException("FASTQ quality length differs from original sequence");quality=scores.toString();
                }
                bases=Math.addExact(bases,sequence.length());if(bases>limits.bases())throw new IOException("Complete read base capacity exceeded");for(int i=0;i<sequence.length();i++){char c=sequence.charAt(i);if(c>='a'&&c<='z')lowercase++;if(c=='U'||c=='u')uracil++;if("ACGTUacgtu".indexOf(c)<0)ambiguous++;}minimum=Math.min(minimum,sequence.length());maximum=Math.max(maximum,sequence.length());Read result=new Read(header,id,sequence.toString(),quality);census.put(id,new ReadInfo(header,sequence.length(),quality!=null));return result;
            }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("Invalid complete literal read state",error);}
        }
        private void append(StringBuilder target,String line,boolean quality)throws IOException{
            if(line.isEmpty()||(long)target.length()+line.length()>limits.readBases())throw new IOException("Blank sequence/quality line or complete read capacity exceeded");for(int i=0;i<line.length();i++){char c=line.charAt(i);if(quality?(c<33||c>126):"ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(c)<0)throw new IOException("Invalid literal sequence or Phred33 character; no normalization is performed");}target.append(line);
        }
        private String line()throws IOException{
            var raw=new ByteArrayOutputStream(Math.min(limits.lineBytes(),4096));int value;while((value=decoded.read())>=0){if(value=='\n')break;if(raw.size()>=limits.lineBytes())throw new IOException("Decoded read line exceeds its byte capacity");raw.write(value);}if(value<0&&raw.size()==0)return null;byte[] bytes=raw.toByteArray();int n=bytes.length;if(n>0&&bytes[n-1]=='\r')n--;for(int i=0;i<n;i++)if((bytes[i]&255)>126||bytes[i]=='\r'||bytes[i]==0)throw new IOException("Invalid non-ASCII/control byte in read input");return new String(bytes,0,n,StandardCharsets.US_ASCII);
        }
        public Inspection inspection()throws IOException{if(!ended||census.isEmpty())throw new IOException("Complete input census requires successful nonempty EOF");return new Inspection(format,census,bases,minimum,maximum,lowercase,uracil,ambiguous,encoded.count,decoded.count);}
        @Override public void close()throws IOException{closed=true;decoded.close();}
    }
    private static final class Counted extends InputStream {
        final InputStream input;final long maximum;final String kind;long count;
        Counted(InputStream input,long maximum,String kind){this.input=input;this.maximum=maximum;this.kind=kind;}
        private void add(long amount)throws IOException{count=Math.addExact(count,amount);if(count>maximum)throw new IOException("Complete "+kind+" input byte capacity exceeded");}
        @Override public int read()throws IOException{int value=input.read();if(value>=0)add(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=input.read(bytes,offset,length);if(n>0)add(n);return n;}
        @Override public void close()throws IOException{input.close();}
    }
}
