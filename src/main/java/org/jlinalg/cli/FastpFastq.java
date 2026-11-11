/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Complete bounded FASTQ admission; declared validation policies are stricter than original fastp parsing. */
final class FastpFastq {
    private FastpFastq() { }
    enum Encoding { PHRED33(33), PHRED64(64); final int offset; Encoding(int offset) { this.offset=offset; } }
    enum Alphabet { UPPER_ACGTN, LITERAL_ASCII }
    enum Plus { EMPTY_ONLY, MATCH_FIRST_TOKEN_OR_EMPTY, SOURCE_IGNORED }
    enum Pairing { EXACT_FIRST_TOKEN, TERMINAL_MATE_SUFFIX, CASAVA_FIRST_TOKEN, ORDINAL_ONLY_UNVERIFIED }
    record Limits(long encodedBytes,long decodedCharacters,long reads,long bases,int readCharacters,int headerCharacters) {
        Limits {
            if(encodedBytes<1||encodedBytes>1_000_000_000_000L||decodedCharacters<1||decodedCharacters>4_000_000_000_000L
                    ||reads<1||reads>1_000_000_000L||bases<1||bases>1_000_000_000_000L||readCharacters<1||readCharacters>10_000_000
                    ||headerCharacters<1||headerCharacters>1_000_000)throw new IllegalArgumentException("Invalid explicit fastp FASTQ capacities");
        }
        static Limits defaults() { return new Limits(4_294_967_296L,17_179_869_184L,10_000_000,2_000_000_000L,1_000_000,16_384); }
    }
    record Read(long ordinal,String header,String sequence,String quality,String originalPlus) {
        String token() { return firstToken(header.substring(1)); }
    }
    record Stats(long reads,long bases,int minimumLength,int maximumLength,long totalPhred,long q20Bases,long q30Bases,
                 long uppercaseNs,long gcBases,long noncanonicalBases,long nonemptyPlus,long mismatchedPlus,
                 long encodedBytes,long decodedCharacters,boolean gzip,boolean unterminatedFinalLine) { }
    record Inspection(Stats read1,Stats read2,Pairing pairing,boolean mateIdentityVerified) { }

    /** Streaming physical-byte/line/member validation; callers must consume through next()==null. */
    static final class Reader implements AutoCloseable {
        private final Path source;
        private final Limits limits;
        private final Encoding encoding;
        private final Alphabet alphabet;
        private final Plus plus;
        private final boolean emptyReadAllowed;
        private final Runnable check;
        private final Counter encoded;
        private final BufferedReader input;
        private final boolean gzip;
        private long decoded,records,bases,totalPhred,q20,q30,ns,gc,noncanonical,nonemptyPlus,mismatchedPlus;
        private int minimum=Integer.MAX_VALUE,maximum;
        private boolean exhausted,closed,unterminated;
        Reader(Path source,Limits limits,Encoding encoding,Alphabet alphabet,Plus plus,boolean emptyReadAllowed,Runnable check)throws IOException {
            this.source=Objects.requireNonNull(source);this.limits=Objects.requireNonNull(limits);this.encoding=Objects.requireNonNull(encoding);
            this.alphabet=Objects.requireNonNull(alphabet);this.plus=Objects.requireNonNull(plus);this.emptyReadAllowed=emptyReadAllowed;this.check=Objects.requireNonNull(check);
            long reservation=Math.addExact(2_097_152L,Math.addExact(16L*limits.readCharacters(),16L*limits.headerCharacters()));
            if(reservation>Runtime.getRuntime().maxMemory()/2)throw invalid("Requested per-record storage exceeds the current Java heap admission; supply a larger heap or smaller explicit capacity");
            check.run();InputStream original=Files.newInputStream(source);Counter count=null;
            try {
                count=new Counter(original,limits.encodedBytes(),check);var magic=new PushbackInputStream(count,2);
                int a=magic.read(),b=magic.read();if(b>=0)magic.unread(b);if(a>=0)magic.unread(a);gzip=a==31&&b==139;
                InputStream body=gzip?new RepeatGzip(magic,check):magic;
                input=new BufferedReader(new InputStreamReader(body,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),8192);encoded=count;
            }catch(IOException|RuntimeException failed){original.close();throw failed;}
        }
        Read next()throws IOException {
            if(closed)throw invalid("FASTQ reader is closed");if(exhausted)return null;
            String header=line(limits.headerCharacters()+1);
            if(header==null){exhausted=true;return null;}
            if(records==limits.reads())throw invalid("Complete read population exceeds its declared read capacity");
            if(header.length()<2||header.charAt(0)!='@'||Character.isWhitespace(header.charAt(1)))throw invalid("Require an at-sign and nonempty first header token");
            headerText(header);
            String sequence=line(limits.readCharacters()),separator=line(limits.headerCharacters()+2),quality=line(limits.readCharacters());
            if(sequence==null||separator==null||quality==null||!separator.startsWith("+")||sequence.length()!=quality.length())throw invalid("Require a complete unwrapped four-line record and equal sequence/quality lengths");
            if(sequence.isEmpty()&&!emptyReadAllowed)throw invalid("Empty input reads are outside this explicitly nonempty input profile");
            if(!separator.equals("+")){
                nonemptyPlus++;headerText(separator);boolean matches=firstToken(separator.substring(1)).equals(firstToken(header.substring(1)));
                if(!matches)mismatchedPlus++;
                if(plus==Plus.EMPTY_ONLY||plus==Plus.MATCH_FIRST_TOKEN_OR_EMPTY&&!matches)throw invalid("Nonempty plus-header differs from the declared policy");
            }
            long nextBases=Math.addExact(bases,sequence.length());if(nextBases>limits.bases())throw invalid("Complete base population exceeds its declared capacity");
            for(int i=0;i<sequence.length();i++){
                char base=sequence.charAt(i),q=quality.charAt(i);
                if(base<33||base>126||alphabet==Alphabet.UPPER_ACGTN&&"ACGTN".indexOf(base)<0)throw invalid("Sequence character is outside the explicitly declared alphabet");
                if(q<encoding.offset||q>126)throw invalid("Quality character is outside the declared "+encoding+" encoding");
                int value=q-encoding.offset;totalPhred=Math.addExact(totalPhred,value);if(value>=20)q20++;if(value>=30)q30++;
                if(base=='N')ns++;if(base=='G'||base=='C')gc++;if("ACGTN".indexOf(base)<0)noncanonical++;
            }
            bases=nextBases;minimum=Math.min(minimum,sequence.length());maximum=Math.max(maximum,sequence.length());records++;check.run();
            return new Read(records,header,sequence,quality,separator);
        }
        Stats completeStats()throws IOException {
            if(!exhausted)throw invalid("Complete FASTQ statistics require physical EOF; a prefix is not admitted");
            return new Stats(records,bases,records==0?0:minimum,maximum,totalPhred,q20,q30,ns,gc,noncanonical,nonemptyPlus,mismatchedPlus,encoded.count,decoded,gzip,unterminated);
        }
        private String line(int maximumCharacters)throws IOException {
            var text=new StringBuilder(Math.min(maximumCharacters,256));
            for(;;){
                int c=input.read();
                if(c<0){if(text.length()==0)return null;unterminated=true;return text.toString();}
                decoded++;if(decoded>limits.decodedCharacters())throw invalid("Complete decoded FASTQ exceeds its character capacity");
                if((decoded&8191)==0)check.run();
                if(c=='\n')return text.toString();
                if(c=='\r'){
                    int next=input.read();if(next<0)throw invalid("Bare terminal carriage return is outside LF/CRLF policy");decoded++;
                    if(decoded>limits.decodedCharacters())throw invalid("Complete decoded FASTQ exceeds its character capacity");
                    if(next!='\n')throw invalid("Bare carriage return is outside LF/CRLF policy");return text.toString();
                }
                if(text.length()==maximumCharacters)throw invalid("FASTQ line exceeds its preallocation character capacity");text.append((char)c);
            }
        }
        private IOException invalid(String message){return new IOException("fastp FASTQ "+source+" near record "+(records+1)+": "+message);}
        private void headerText(String header)throws IOException {
            for(int i=0;i<header.length();i++)if(Character.isISOControl(header.charAt(i))&&header.charAt(i)!='\t')throw invalid("Control character in header");
        }
        @Override public void close()throws IOException { if(!closed){closed=true;input.close();} }
    }
    /** Complete streaming inspection with explicit pair identity policy; never retains a read prefix as success. */
    static Inspection inspect(Path first,Path second,Limits limits,Encoding encoding,Alphabet alphabet,Plus plus,Pairing pairing,boolean allowEmptyInput,Runnable check)throws IOException {
        Objects.requireNonNull(pairing);
        if(second!=null&&Files.isSameFile(first,second))throw new IOException("fastp mate inputs must be different files");
        try(var read1=new Reader(first,limits,encoding,alphabet,plus,false,check);var read2=second==null?null:new Reader(second,limits,encoding,alphabet,plus,false,check)){
            for(Read a=read1.next();a!=null;a=read1.next()){
                if(read2!=null){Read b=read2.next();if(b==null)throw new IOException("fastp mate2 has fewer reads than mate1");checkPair(a,b,pairing);}
            }
            if(read2!=null&&read2.next()!=null)throw new IOException("fastp mate2 has extra reads");
            Stats a=read1.completeStats(),b=read2==null?null:read2.completeStats();
            if(!allowEmptyInput&&a.reads()==0)throw new IOException("fastp empty complete input is not admitted by this declared profile");
            return new Inspection(a,b,pairing,second!=null&&pairing!=Pairing.ORDINAL_ONLY_UNVERIFIED);
        }
    }
    static void checkPair(Read first,Read second,Pairing pairing)throws IOException {
        if(first.ordinal()!=second.ordinal())throw new IOException("fastp mate ordinal mismatch");
        if(pairing==Pairing.ORDINAL_ONLY_UNVERIFIED)return;
        String a=first.token(),b=second.token();
        if(pairing==Pairing.CASAVA_FIRST_TOKEN){casava(first.header(),"1");casava(second.header(),"2");}
        if(pairing==Pairing.TERMINAL_MATE_SUFFIX){
            if(a.endsWith("/2")||b.endsWith("/1"))throw new IOException("fastp terminal mate labels are reversed or duplicated");
            boolean markedA=a.endsWith("/1"),markedB=b.endsWith("/2");if(markedA!=markedB)throw new IOException("fastp mates inconsistently declare terminal mate suffixes");
            if(markedA){a=a.substring(0,a.length()-2);b=b.substring(0,b.length()-2);}
        }
        if(!a.equals(b))throw new IOException("fastp mate identity/order mismatch at record "+first.ordinal());
    }
    private static void casava(String header,String expected)throws IOException {
        String value=header.substring(1);int end=firstToken(value).length();while(end<value.length()&&(value.charAt(end)==' '||value.charAt(end)=='\t'))end++;
        String[] fields=firstToken(value.substring(end)).split(":",-1);
        if(fields.length!=4||!fields[0].equals(expected)||!ListHolder.FILTERS.contains(fields[1])||!fields[2].matches("[0-9]+"))throw new IOException("fastp CASAVA mate metadata differs from the declared read1/read2 policy");
    }
    private static final class ListHolder { private static final java.util.Set<String> FILTERS=java.util.Set.of("Y","N"); }
    private static String firstToken(String value){int end=0;while(end<value.length()&&!Character.isWhitespace(value.charAt(end)))end++;return value.substring(0,end);}
    private static final class Counter extends FilterInputStream {
        private final long maximum;private final Runnable check;private long count;
        Counter(InputStream source,long maximum,Runnable check){super(source);this.maximum=maximum;this.check=check;}
        private void add(int n)throws IOException{check.run();if(n>0){count=Math.addExact(count,n);if(count>maximum)throw new IOException("fastp complete encoded FASTQ exceeds its byte capacity");}}
        @Override public int read()throws IOException{int value=in.read();add(value<0?0:1);return value;}
        @Override public int read(byte[] value,int offset,int length)throws IOException{int n=in.read(value,offset,length);add(n);return n;}
    }
}
