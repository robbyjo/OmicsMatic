/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source equations: MIT-licensed hifiasm htab.h and Trio.cpp. Retain the
 * original notices in org/jlinalg/omics/hifiasm-source/original archives. */
package org.jlinalg.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Original hifiasm parental k-mer hashing, bounded counting, YAK2 exchange and
 * literal trio vote arithmetic. This does not implement assembly or graph
 * phasing and does not reproduce Yak's Bloom-filter admission or byte layout.
 * Supplied YAK files must remain unchanged beside any parsed logical view.
 */
public final class HifiasmParentalKmers {
    private HifiasmParentalKmers(){ }
    public static final int MAX_COUNT=1023;
    /** Hash is an unsigned 64-bit bit pattern; the original stored count uses ten bits. */
    public record Entry(long hash,int count){
        public Entry{if(count<0||count>MAX_COUNT)throw new IllegalArgumentException("Require an original ten-bit count");}
    }
    /** The exchange format does not encode total, ambiguous or saturated window counts. */
    public record Table(int k,int prefixBits,List<Entry> entries,OptionalLong windows,
            OptionalLong ambiguousBases,OptionalLong saturatedWindows){
        public Table{
            domain(k,prefixBits);entries=List.copyOf(entries);Objects.requireNonNull(windows);Objects.requireNonNull(ambiguousBases);Objects.requireNonNull(saturatedWindows);
            for(var value:List.of(windows,ambiguousBases,saturatedWindows))if(value.isPresent()&&value.getAsLong()<0)throw new IllegalArgumentException("Negative count provenance");
            if(entries.size()>25000000)throw new IllegalArgumentException("Complete distinct-hash family exceeds25000000");var seen=new HashSet<Long>();long mask=k<32?(1L<<(2*k))-1:-1L;
            for(Entry e:entries){if(!seen.add(e.hash()))throw new IllegalArgumentException("Duplicate parental hash");if((e.hash()&~mask)!=0)throw new IllegalArgumentException("Hash lies outside the declared short-k domain");}
        }
    }
    /** Exact source vote family; arrays are defensive copies. */
    public record Classification(char sourceClass,int validWindows,int[] flagCounts,int[] repeatRunScores){
        public Classification{flagCounts=flagCounts.clone();repeatRunScores=repeatRunScores.clone();}
        @Override public int[] flagCounts(){return flagCounts.clone();}
        @Override public int[] repeatRunScores(){return repeatRunScores.clone();}
    }
    private static void domain(int k,int prefixBits){if(k<1||k>63||prefixBits<10||prefixBits>20)throw new IllegalArgumentException("Declare k1..63 and prefix bits10..20");}

    /**
     * Count every valid source window without a Bloom filter, saturating at1023.
     * Byte mapping is literal hifiasm seq_nt4_table: A/C/G/T in either case,
     * U/u as T, and encoded bytes0..3; other bytes break a window. This is not
     * homopolymer compression and does not drop singleton hashes.
     */
    public static Table count(Iterable<byte[]> sequences,int k,int prefixBits,long maximumBases,int maximumDistinct){
        Objects.requireNonNull(sequences);domain(k,prefixBits);if(maximumBases<0||maximumDistinct<1||maximumDistinct>25000000)throw new IllegalArgumentException("Declare bounded base and distinct-hash capacities");
        var counts=new HashMap<Long,Integer>();long bases=0,windows=0,ambiguous=0,saturated=0;
        for(byte[] supplied:sequences){Objects.requireNonNull(supplied);if(supplied.length>maximumBases-bases)throw new IllegalArgumentException("Complete parental base capacity exceeded");bases+=supplied.length;byte[] sequence=supplied.clone();var state=new Rolling(k);
            for(byte b:sequence){int code=nt4(b);if(code>=4){ambiguous++;state.reset();continue;}state.add(code);if(state.length<k)continue;windows=Math.addExact(windows,1);long hash=state.hash();Integer old=counts.get(hash);
                if(old==null){if(counts.size()>=maximumDistinct)throw new IllegalArgumentException("Complete distinct parental hash capacity exceeded");counts.put(hash,1);}
                else if(old<MAX_COUNT)counts.put(hash,old+1);else saturated=Math.addExact(saturated,1);
            }
        }
        var entries=new ArrayList<Entry>();counts.forEach((hash,n)->entries.add(new Entry(hash,n)));entries.sort((a,b)->Long.compareUnsigned(a.hash(),b.hash()));return new Table(k,prefixBits,entries,OptionalLong.of(windows),OptionalLong.of(ambiguous),OptionalLong.of(saturated));
    }

    /** Exact source hash for one complete unambiguous window. */
    public static long hash(byte[] window){Objects.requireNonNull(window);domain(window.length,10);var state=new Rolling(window.length);for(byte b:window.clone()){int code=nt4(b);if(code>=4)throw new IllegalArgumentException("A hash window contains an ambiguous source byte");state.add(code);}return state.hash();}

    /** Write a NEW derived canonical YAK2 stream; the caller owns and closes the stream. */
    public static void write(Table table,OutputStream output)throws IOException{
        Objects.requireNonNull(table);Objects.requireNonNull(output);output.write(new byte[]{'Y','A','K',2});integer(output,table.k());integer(output,table.prefixBits());integer(output,10);var buckets=new HashMap<Integer,List<Entry>>();int mask=(1<<table.prefixBits())-1;
        for(Entry e:table.entries())buckets.computeIfAbsent((int)e.hash()&mask,key->new ArrayList<>()).add(e);
        for(int bucket=0;bucket<=mask;bucket++){var entries=buckets.get(bucket);int size=entries==null?0:entries.size();int capacity=0;if(size>0){capacity=4;while(capacity-(capacity>>>2)<size)capacity=Math.multiplyExact(capacity,2);}integer(output,capacity);integer(output,size);if(entries!=null)for(Entry e:entries)longword(output,(e.hash()>>>table.prefixBits()<<10)|e.count());}
    }

    /**
     * Strict bounded logical decode of the source little-endian YAK2 format.
     * Reject truncation, duplicate hashes, impossible capacities, unsupported
     * domains and trailing payload. Original physical order/empty slots are
     * not inferred; no source file is rewritten by this method.
     */
    public static Table read(InputStream input,long maximumBytes,int maximumDistinct)throws IOException{
        Objects.requireNonNull(input);if(maximumBytes<16||maximumDistinct<1||maximumDistinct>25000000)throw new IllegalArgumentException("Declare complete bounded YAK input capacities");var reader=new Reader(input,maximumBytes);for(int b:new int[]{'Y','A','K',2})if(reader.byteValue()!=b)throw new IOException("Original YAK2 magic differs");int k=reader.integer(),prefix=reader.integer(),counter=reader.integer();try{domain(k,prefix);}catch(IllegalArgumentException error){throw new IOException("Unsupported original YAK domain",error);}if(counter!=10)throw new IOException("Original parental counters require ten bits");var entries=new ArrayList<Entry>();var seen=new HashSet<Long>();
        for(int bucket=0;bucket<(1<<prefix);bucket++){long capacity=Integer.toUnsignedLong(reader.integer()),size=Integer.toUnsignedLong(reader.integer());if(capacity>1073741824||size>capacity||capacity!=0&&(capacity&(capacity-1))!=0||size>maximumDistinct-entries.size())throw new IOException("Original YAK complete bucket/hash capacity invalid or exceeded");for(long i=0;i<size;i++){long key=reader.longword();if((key>>>10>>>(64-prefix))!=0)throw new IOException("Original YAK key has bits outside its hash/prefix domain");long hash=(key>>>10<<prefix)|bucket;if(k<32&&(hash>>>2*k)!=0)throw new IOException("Original short-k hash exceeds its domain");if(!seen.add(hash))throw new IOException("Duplicate original YAK hash");entries.add(new Entry(hash,(int)(key&MAX_COUNT)));}}
        if(reader.optional()!=-1)throw new IOException("Trailing original YAK payload");return new Table(k,prefix,entries,OptionalLong.empty(),OptionalLong.empty(),OptionalLong.empty());
    }

    /** Source tb_classify equation, without inferred marker families or changed tie rules. */
    public static char classifyCounts(int k,int[] flags,int[] repeatRuns,double ratioThreshold){
        domain(k,10);Objects.requireNonNull(flags);Objects.requireNonNull(repeatRuns);if(flags.length!=16||repeatRuns.length!=2||!Double.isFinite(ratioThreshold)||ratioThreshold<0)throw new IllegalArgumentException("Declare16 vote counts,2 run scores and finite nonnegative source ratio");long total=0;for(int n:flags){if(n<0)throw new IllegalArgumentException("Negative source vote count");total+=n;}if(total>1000000000)throw new IllegalArgumentException("Source vote family exceeds the bounded integer domain");for(int n:repeatRuns)if(n<0||n>1000000000)throw new IllegalArgumentException("Source run score exceeds the bounded integer domain");int paternal=flags[2],maternal=flags[8],p=repeatRuns[0],m=repeatRuns[1];
        if(p==0&&m==0){if(paternal==maternal)return '0';if(paternal>=k-4+maternal&&(maternal<=1||paternal*0.05>maternal))return 'p';if(maternal>=k-4+paternal&&(paternal<=1||maternal*0.05>paternal))return 'm';return '0';}
        if(p>k&&m>k)return 'a';if(p>=k-4+m&&p*0.05>=m&&paternal*ratioThreshold>maternal)return 'p';if(m>=k-4+p&&m*0.05>=p&&maternal*ratioThreshold>paternal)return 'm';return 'a';
    }

    /**
     * Literal votes and repeat-run classification on explicitly supplied source
     * sequence bytes and matching parental tables. No graph phasing, read
     * correction, raw-input preprocessing or independent Yak reproduction.
     */
    public static Classification classify(byte[] supplied,Table paternal,Table maternal,int minimumCount,int repeatCount,double ratioThreshold){
        Objects.requireNonNull(supplied);Objects.requireNonNull(paternal);Objects.requireNonNull(maternal);if(supplied.length>10000000||paternal.k()!=maternal.k()||paternal.prefixBits()!=maternal.prefixBits()||minimumCount<0||minimumCount>MAX_COUNT||repeatCount<0||repeatCount>MAX_COUNT)throw new IllegalArgumentException("Require matching parental domains, bounded source read and ten-bit thresholds");byte[] read=supplied.clone();var p=map(paternal);var m=map(maternal);var state=new Rolling(paternal.k());int[] flags=new int[16],runs=new int[2],types=new int[read.length];int windows=0;
        for(int i=0;i<read.length;i++){int code=nt4(read[i]);if(code>=4){state.reset();continue;}state.add(code);if(state.length<paternal.k())continue;windows++;long hash=state.hash();int a=flag(p.get(hash),minimumCount,repeatCount),b=flag(m.get(hash),minimumCount,repeatCount);flags[a|(b<<2)]++;if(a==2&&b==0)types[i]=1;else if(b==2&&a==0)types[i]=2;}
        for(int start=0,i=1;i<=read.length;i++)if(i==read.length||types[i]!=types[start]){if(types[start]>0&&i-start>=paternal.k()-4)runs[types[start]-1]+=i-start;start=i;}
        return new Classification(classifyCounts(paternal.k(),flags,runs,ratioThreshold),windows,flags,runs);
    }
    private static HashMap<Long,Integer> map(Table table){var result=new HashMap<Long,Integer>();for(Entry e:table.entries())result.put(e.hash(),e.count());return result;}
    private static int flag(Integer count,int minimum,int repeat){return count==null?0:count>=repeat?2:count>=minimum?1:0;}
    private static int nt4(byte raw){int b=Byte.toUnsignedInt(raw);if(b<4)return b;return switch(b){case 'A','a'->0;case 'C','c'->1;case 'G','g'->2;case 'T','t','U','u'->3;default->4;};}
    private static final class Rolling{
        final int k,shift;final long mask;long forward0,forward1,reverse0,reverse1;int length;
        Rolling(int k){this.k=k;mask=k<32?(1L<<(2*k))-1:(1L<<k)-1;shift=k<32?2*(k-1):k-1;}
        void reset(){forward0=forward1=reverse0=reverse1=0;length=0;}
        void add(int c){if(k<32){forward0=((forward0<<2)|c)&mask;forward1=(forward1>>>2)|((long)(3-c)<<shift);}else{forward0=((forward0<<1)|(c&1))&mask;forward1=((forward1<<1)|(c>>>1))&mask;reverse0=(reverse0>>>1)|((long)(1-(c&1))<<shift);reverse1=(reverse1>>>1)|((long)(1-(c>>>1))<<shift);}if(length<k)length++;}
        long hash(){if(k<32)return hashMasked(Math.min(forward0,forward1),mask);return Long.compareUnsigned(forward1,reverse1)<0?hashFull(forward0)+hashFull(forward1):hashFull(reverse0)+hashFull(reverse1);}
    }
    private static long hashMasked(long key,long mask){key=(~key+(key<<21))&mask;key^=key>>>24;key=((key+(key<<3))+(key<<8))&mask;key^=key>>>14;key=((key+(key<<2))+(key<<4))&mask;key^=key>>>28;return (key+(key<<31))&mask;}
    private static long hashFull(long key){key=~key+(key<<21);key^=key>>>24;key=(key+(key<<3))+(key<<8);key^=key>>>14;key=(key+(key<<2))+(key<<4);key^=key>>>28;return key+(key<<31);}
    private static void integer(OutputStream out,int value)throws IOException{for(int i=0;i<4;i++)out.write(value>>>(i*8));}
    private static void longword(OutputStream out,long value)throws IOException{for(int i=0;i<8;i++)out.write((int)(value>>>(i*8)));}
    private static final class Reader{
        final InputStream input;final long maximum;long used;Reader(InputStream input,long maximum){this.input=input;this.maximum=maximum;}
        int optional()throws IOException{int value=input.read();if(value>=0&&++used>maximum)throw new IOException("Complete YAK byte capacity exceeded");return value;}
        int byteValue()throws IOException{int value=optional();if(value<0)throw new IOException("Truncated original YAK stream");return value;}
        int integer()throws IOException{int value=0;for(int i=0;i<4;i++)value|=byteValue()<<(i*8);return value;}
        long longword()throws IOException{long value=0;for(int i=0;i<8;i++)value|=(long)byteValue()<<(i*8);return value;}
    }
}
