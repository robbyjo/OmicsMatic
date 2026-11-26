/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.assembly;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Complete FASTA/FASTQ counting audit; preserves repeated record identifiers and literal inputs. */
public final class MerquryFastx {
    private MerquryFastx() { }
    @FunctionalInterface public interface SequenceObserver {
        void inspect(Sequence record,String literalSequence)throws IOException;
    }
    public record Limits(long decodedBytes,int lineCharacters,int recordBases,long records,long bases,int distinctKmers) {
        public Limits {if(decodedBytes<1||lineCharacters<1||recordBases<1||records<1||bases<1||distinctKmers<1)throw new IllegalArgumentException("Explicit positive complete FASTX capacities required");}
    }
    public record Sequence(long ordinal,String id,long bases,long validWindows,long absentReadWindows) { }
    public record Result(Map<String,Long> counts,List<Sequence> sequences,long bases,long ambiguousBases,long uracilBases,long decodedBytes) {
        public Result {counts=Collections.unmodifiableMap(new TreeMap<>(counts));sequences=List.copyOf(sequences);}
    }
    /** The input owner supplies encoded-byte bounds and keeps the exact original file. */
    public static Result count(InputStream raw,int k,Set<String> readKeys,Limits limits,Runnable check)throws IOException {
        return count(raw,k,readKeys,limits,check,(record,sequence)->{});
    }
    /** The observer sees each COMPLETE literal sequence in original record order, including repeated IDs. */
    public static Result count(InputStream raw,int k,Set<String> readKeys,Limits limits,Runnable check,SequenceObserver observer)throws IOException {
        return parse(raw,k,readKeys,limits,check,observer,false);
    }
    public static Result countFasta(InputStream raw,int k,Set<String> readKeys,Limits limits,Runnable check,SequenceObserver observer)throws IOException {
        return parse(raw,k,readKeys,limits,check,observer,true);
    }
    private static Result parse(InputStream raw,int k,Set<String> readKeys,Limits limits,Runnable check,SequenceObserver observer,boolean fastaOnly)throws IOException {
        Objects.requireNonNull(raw);Objects.requireNonNull(limits);Objects.requireNonNull(check);
        Objects.requireNonNull(observer);
        if(k<1)throw new IllegalArgumentException("Positive k required");var table=new TreeMap<String,Long>();var records=new ArrayList<Sequence>();long bases=0,ambiguous=0,uracil=0,decodedBytes=0;
        try(var reader=new Lines(raw,limits,check)) {
            String pending=reader.line(),format=null;
            while(pending!=null) {
                check.run();if(pending.isEmpty()||(pending.charAt(0)!='>'&&pending.charAt(0)!='@'))throw new IOException("Complete FASTA/FASTQ header required");
                char marker=pending.charAt(0);String current=marker=='>'?"FASTA":"FASTQ";
                if(fastaOnly&&marker!='>')throw new IOException("Assembly input must contain complete FASTA records, not FASTQ");
                if(format==null)format=current;else if(!format.equals(current))throw new IOException("Mixed FASTA/FASTQ formats in one file");
                String header=pending.substring(1);int stop=0;while(stop<header.length()&&header.charAt(stop)!=' '&&header.charAt(stop)!='\t')stop++;
                if(stop==0||header.chars().anyMatch(c->c<32&&c!='\t'||c==127))throw new IOException("Nonblank literal FASTX identity required");String id=header.substring(0,stop);
                var seq=new StringBuilder();pending=null;String line;
                if(marker=='>') {
                    while((line=reader.line())!=null){if(line.startsWith(">")){pending=line;break;}append(seq,line,limits.recordBases());}
                }else {
                    boolean plus=false;
                    while((line=reader.line())!=null){if(line.startsWith("+")){plus=true;break;}append(seq,line,limits.recordBases());}
                    if(!plus)throw new IOException("Missing FASTQ + separator");long quality=0;
                    while(quality<seq.length()&&(line=reader.line())!=null) {
                        if(line.chars().anyMatch(c->c<33||c>126))throw new IOException("Invalid literal FASTQ quality character");quality+=line.length();
                    }
                    if(quality!=seq.length())throw new IOException("FASTQ qualities do not match sequence length");pending=reader.line();
                }
                if(records.size()>=limits.records())throw new IOException("Complete FASTX record capacity exceeded");
                bases=Math.addExact(bases,seq.length());if(bases>limits.bases())throw new IOException("Complete FASTX base capacity exceeded");
                var window=new StringBuilder(k);long valid=0,absent=0;
                for(int i=0;i<seq.length();i++) {
                    if((i&4095)==0)check.run();char c=seq.charAt(i);if(c>='a'&&c<='z')c=(char)(c-'a'+'A');
                    // Original Meryl 1.0/1.3/1.4.1 break windows on U; no RNA-to-DNA repair.
                    if(c=='U')uracil++;
                    if(c!='A'&&c!='C'&&c!='G'&&c!='T'){ambiguous++;window.setLength(0);continue;}
                    window.append(c);if(window.length()<k)continue;
                    String key=MerquryAssessment.canonical(window.toString());valid=Math.addExact(valid,1);if(readKeys!=null&&!readKeys.contains(key))absent=Math.addExact(absent,1);
                    Long old=table.get(key);if(old==null){if(table.size()>=limits.distinctKmers())throw new IOException("Complete distinct k-mer capacity exceeded");table.put(key,1L);}else table.put(key,Math.addExact(old,1));
                    window.deleteCharAt(0);
                }
                var record=new Sequence(records.size(),id,seq.length(),valid,absent);
                observer.inspect(record,seq.toString());records.add(record);
            }
            decodedBytes=reader.decodedBytes;
        }
        check.run();if(records.isEmpty())throw new IOException("Complete FASTX input has no records");return new Result(table,records,bases,ambiguous,uracil,decodedBytes);
    }
    private static void append(StringBuilder sequence,String line,int maximum)throws IOException {
        if(line.chars().anyMatch(c->"ACGTRYSWKMBDHVNUacgtryswkmbdhvnu".indexOf(c)<0))throw new IOException("Sequence must contain literal DNA/RNA IUPAC symbols; unknown bytes are not repaired");
        if(line.length()>maximum-sequence.length())throw new IOException("Complete FASTX record base capacity exceeded");sequence.append(line);
    }
    private static final class Lines implements AutoCloseable {
        final PushbackReader input;final Limits limits;final Runnable check;long decodedBytes;
        Lines(InputStream raw,Limits limits,Runnable check)throws IOException {
            this.limits=limits;this.check=check;var peek=new PushbackInputStream(new BufferedInputStream(raw,65536),2);int a=peek.read(),b=peek.read();if(b>=0)peek.unread(b);if(a>=0)peek.unread(a);
            InputStream decoded=a==31&&b==139?new GZIPInputStream(peek,65536):peek;
            var bounded=new FilterInputStream(decoded){private void add(int n)throws IOException{check.run();if(n>0){decodedBytes=Math.addExact(decodedBytes,n);if(decodedBytes>limits.decodedBytes())throw new IOException("Complete FASTX decoded byte capacity exceeded");}}@Override public int read()throws IOException{int c=in.read();if(c>=0)add(1);return c;}@Override public int read(byte[]b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}};
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            input=new PushbackReader(new BufferedReader(new InputStreamReader(bounded,decoder),65536),1);
        }
        String line()throws IOException {
            check.run();var s=new StringBuilder();for(int c=input.read();c!=-1;c=input.read()) {
                if(c=='\n')return s.toString();if(c=='\r'){int n=input.read();if(n!=-1&&n!='\n')input.unread(n);return s.toString();}
                if(s.length()>=limits.lineCharacters())throw new IOException("Complete FASTX line capacity exceeded");s.append((char)c);
            }
            return s.isEmpty()?null:s.toString();
        }
        @Override public void close()throws IOException{input.close();}
    }
}
