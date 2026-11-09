/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Wire layout: unchanged MIT-licensed hifiasm Trio.cpp; original notices are
 * retained in org/jlinalg/omics/hifiasm-source/original archives. */
package org.jlinalg.assembly;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Complete bucket-wise YAK2 inspection. Only one bucket's hash words are held
 * for duplicate detection; total database entries need not fit a Java table.
 * The original stream is never rewritten and caller owns its lifetime.
 */
public final class HifiasmYak {
    private HifiasmYak(){ }
    public record Limits(long bytes,long entries,int entriesPerBucket){
        public Limits{if(bytes<16||entries<1||entriesPerBucket<1||entriesPerBucket>25000000)throw new IllegalArgumentException("Declare positive byte/entry limits and at most25M entries per bucket");}
    }
    /** Count histogram is literal ten-bit stored counts; window/Bloom provenance is not encoded. */
    public record Inspection(int k,int prefixBits,long entries,int occupiedBuckets,int maximumBucketEntries,long bytes,OptionalLong minimumCount,OptionalLong maximumCount,List<Long> countHistogram,String unsignedHashSum,String unsignedHashXor){
        public Inspection{Objects.requireNonNull(minimumCount);Objects.requireNonNull(maximumCount);countHistogram=List.copyOf(countHistogram);if(countHistogram.size()!=1024)throw new IllegalArgumentException("Require complete ten-bit counter histogram");}
    }
    public static Inspection inspect(InputStream supplied,Limits limits)throws IOException{
        Objects.requireNonNull(supplied);Objects.requireNonNull(limits);var counted=new Counted(supplied,limits.bytes());var input=new DataInputStream(new BufferedInputStream(counted,65536));long total=0,sum=0,xor=0;int occupied=0,maximumBucket=0;long[] counts=new long[1024];
        try{
            if(input.readUnsignedByte()!='Y'||input.readUnsignedByte()!='A'||input.readUnsignedByte()!='K'||input.readUnsignedByte()!=2)throw new IOException("Original YAK2 magic differs");int k=Integer.reverseBytes(input.readInt()),prefix=Integer.reverseBytes(input.readInt()),counter=Integer.reverseBytes(input.readInt());if(k<1||k>63||prefix<10||prefix>20||counter!=10)throw new IOException("Unsupported original YAK k/prefix/ten-bit counter domain");
            for(int bucket=0;bucket<(1<<prefix);bucket++){
                long capacity=Integer.toUnsignedLong(Integer.reverseBytes(input.readInt())),size=Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));if(capacity>1073741824||size>capacity||capacity!=0&&(capacity&(capacity-1))!=0||size>limits.entriesPerBucket()||size>limits.entries()-total)throw new IOException("Original YAK bucket capacity or declared complete entry limit exceeded");int n=(int)size;long[] hashes=new long[n];if(n>0)occupied++;maximumBucket=Math.max(maximumBucket,n);total+=n;
                for(int i=0;i<n;i++){long key=Long.reverseBytes(input.readLong()),high=key>>>10;if((high>>>(64-prefix))!=0)throw new IOException("Original YAK stored key exceeds its prefix/hash domain");long hash=high<<prefix|bucket;if(k<32&&(hash>>>(2*k))!=0)throw new IOException("Original YAK short-k hash exceeds its complete domain");hashes[i]=high;counts[(int)(key&1023)]++;sum+=hash;xor^=hash;}
                // Private scratch order only. Full hashes in distinct buckets cannot
                // duplicate because their low prefix bits are the bucket identity.
                Arrays.sort(hashes);for(int i=1;i<n;i++)if(hashes[i-1]==hashes[i])throw new IOException("Duplicate original YAK hash in bucket "+bucket);
            }
            if(input.read()!=-1)throw new IOException("Trailing original YAK payload");var histogram=new ArrayList<Long>(1024);OptionalLong minimum=OptionalLong.empty(),maximum=OptionalLong.empty();for(int i=0;i<counts.length;i++){histogram.add(counts[i]);if(counts[i]>0){if(minimum.isEmpty())minimum=OptionalLong.of(i);maximum=OptionalLong.of(i);}}return new Inspection(k,prefix,total,occupied,maximumBucket,counted.count,minimum,maximum,histogram,Long.toUnsignedString(sum),Long.toUnsignedString(xor));
        }catch(EOFException truncated){throw new IOException("Truncated complete original YAK header/bucket/entry family",truncated);}
    }
    private static final class Counted extends InputStream{
        final InputStream input;final long maximum;long count;
        Counted(InputStream input,long maximum){this.input=input;this.maximum=maximum;}
        private void add(int n)throws IOException{count=Math.addExact(count,n);if(count>maximum)throw new IOException("Complete original YAK byte capacity exceeded");}
        @Override public int read()throws IOException{int value=input.read();if(value>=0)add(1);return value;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=input.read(bytes,offset,length);if(n>0)add(n);return n;}
    }
}
