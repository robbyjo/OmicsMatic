/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.alignment;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jlinalg.alignment.SambambaStreamingBamAudit.FileIdentity;

/**
 * Complete native BAI coverage and metadata checks against the actual raw BAM.
 * Every placed alignment must be reachable through a containing hierarchical
 * bin/chunk and through every potentially relevant linear-index start window.
 * Every referenced nonzero virtual offset must resolve to a real record boundary.
 * This neither changes an input nor independently classifies duplicate molecules.
 */
public final class BamBaiAudit {
    /** Explicit aggregate index capacities, including metadata chunks. */
    public record Limits(long indexBytes,long chunks,long linearEntries,long wallMillis) {
        public Limits {
            if(indexBytes<8||chunks<0||linearEntries<0||wallMillis<1||wallMillis>604_800_000)
                throw new IllegalArgumentException("Invalid BAI audit capacities");
        }
    }
    /** All records, present metadata, offsets and counts were checked before this is returned. */
    public record Summary(long records,long placedMapped,long placedUnmapped,long unplaced,
                          int references,long dataChunks,long linearEntries,int metadataReferences,
                          boolean globalUnplacedCountPresent,long uniqueOffsets,long equivalentOffsetAliases,
                          FileIdentity bam,FileIdentity bai) { }
    private static final class Point {
        final long raw;long normalized;boolean resolved,boundary;
        Point(long raw){this.raw=raw;}
        long value(){return resolved?normalized:raw;}
    }
    private record Chunk(Point begin,Point end) { }
    private record Meta(Point begin,Point end,long mapped,long unmapped,boolean emptyZeroBounds) { }
    private static final class Reference {
        final Map<Integer,List<Chunk>> bins=new HashMap<>();long[] linear,prefix;
        Meta meta;long mapped,unmapped,first,last;boolean placed;
    }
    private static final class Audit implements BamIndexStream.Observer {
        final Limits limits;final long deadline;
        final Map<Long,Point> points=new HashMap<>();
        final Map<Long,List<Point>> blocks=new HashMap<>(),pending=new HashMap<>();
        final List<Reference> references=new ArrayList<>();
        long chunks,dataChunks,linearEntries,aliases,unplaced;boolean noCoordinatePresent;long noCoordinate;
        FileIdentity indexIdentity;
        Audit(Limits limits){this.limits=limits;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(limits.wallMillis);}
        void check()throws IOException {
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("BAI audit interrupted; no accepted result");
            if(System.nanoTime()-deadline>=0)throw new IOException("BAI audit exceeded wallMillis");
        }
        Point point(long raw){
            return points.computeIfAbsent(raw,v->{Point p=new Point(v);blocks.computeIfAbsent(v>>>16,k->new ArrayList<>()).add(p);return p;});
        }
        @Override public void block(long address,int compressedBytes,int decodedBytes)throws IOException {
            check();List<Point> entries=blocks.remove(address);if(entries==null)return;
            for(Point p:entries){
                int offset=(int)(p.raw&65535);
                if(offset>decodedBytes)throw new IOException("BAI virtual offset exceeds its actual BGZF payload: "+Long.toUnsignedString(p.raw));
                p.normalized=decodedBytes>0&&offset==decodedBytes?(address+compressedBytes)<<16:p.raw;
                if(p.normalized!=p.raw)aliases++;
                p.resolved=true;pending.computeIfAbsent(p.normalized,k->new ArrayList<>()).add(p);
            }
        }
        @Override public void boundary(long offset){
            List<Point> entries=pending.remove(offset);if(entries!=null)for(Point p:entries)p.boundary=true;
        }
        void finishOffsets(long fileBytes,long logicalEnd)throws IOException {
            // Some producers use physical EOF after the terminal member for an
            // end pointer. Its logical BAM boundary is before that empty member.
            List<Point> eof=blocks.remove(fileBytes);
            if(eof!=null)for(Point p:eof){
                if((p.raw&65535)!=0)throw new IOException("BAI EOF offset has a nonzero within-block component");
                p.normalized=logicalEnd;p.resolved=p.boundary=true;if(p.raw!=logicalEnd)aliases++;
            }
            if(!blocks.isEmpty())throw new IOException("BAI virtual offset does not identify an actual BGZF block");
            if(!pending.isEmpty())throw new IOException("BAI virtual offset is not an actual BAM record boundary");
            for(Point p:points.values())if(!p.resolved||!p.boundary)throw new IOException("BAI virtual offset was not completely verified");
        }
        boolean covers(Reference ref,int bin,long begin,long end){
            List<Chunk> list=ref.bins.get(bin);if(list==null)return false;
            int low=0,high=list.size();
            while(low<high){int mid=(low+high)>>>1;if(Long.compareUnsigned(list.get(mid).begin.value(),begin)<=0)low=mid+1;else high=mid;}
            return low>0&&Long.compareUnsigned(list.get(low-1).end.value(),end)>=0;
        }
        void record(BamIndexStream.Row row)throws IOException {
            if((row.ordinal()&1023)==0)check();
            if(row.reference()<0){unplaced++;return;}
            Reference ref=references.get(row.reference());
            if(!ref.placed){ref.placed=true;ref.first=row.virtualStart();}ref.last=row.virtualEnd();
            if((row.flag()&4)==0)ref.mapped++;else ref.unmapped++;
            int last=(row.end()-1)>>>14;
            // A query can start in an earlier empty window and end after this
            // alignment starts. Checking only the read's own windows is unsound.
            if(ref.prefix.length>0&&Long.compareUnsigned(ref.prefix[Math.min(last,ref.prefix.length-1)],row.virtualStart())>0)
                throw new IOException("BAI linear index can skip alignment at ordinal "+row.ordinal());
            boolean found=covers(ref,0,row.virtualStart(),row.virtualEnd());
            int[] shifts={14,17,20,23,26},bases={4681,585,73,9,1};
            for(int i=0;i<shifts.length&&!found;i++)if((row.start()>>>shifts[i])==((row.end()-1)>>>shifts[i]))
                found=covers(ref,bases[i]+(row.start()>>>shifts[i]),row.virtualStart(),row.virtualEnd());
            if(!found)throw new IOException("BAI bins/chunks cannot retrieve complete alignment at ordinal "+row.ordinal());
        }
        void metadata()throws IOException {
            for(int i=0;i<references.size();i++){
                Reference ref=references.get(i);
                for(List<Chunk> list:ref.bins.values())for(Chunk c:list){
                    if(Long.compareUnsigned(c.begin.value(),c.end.value())>=0)throw new IOException("Empty/reversed normalized BAI data chunk");
                    if(!ref.placed||Long.compareUnsigned(c.begin.value(),ref.first)<0||Long.compareUnsigned(c.end.value(),ref.last)>0)
                        throw new IOException("BAI data chunk extends outside its actual reference record run: "+i);
                }
                Meta meta=ref.meta;if(meta==null)continue;
                if(meta.mapped!=ref.mapped||meta.unmapped!=ref.unmapped)throw new IOException("BAI mapped/placed-unmapped metadata count differs for reference "+i);
                if(ref.placed){
                    if(meta.emptyZeroBounds||meta.begin.value()!=ref.first||meta.end.value()!=ref.last)
                        throw new IOException("BAI metadata reference extent differs from its actual placed records: "+i);
                }else if(!meta.emptyZeroBounds&&meta.begin.value()!=meta.end.value())throw new IOException("Nonempty BAI metadata extent on an empty reference");
            }
            if(noCoordinatePresent&&noCoordinate!=unplaced)throw new IOException("BAI global unplaced-record count differs");
        }
    }
    private BamBaiAudit(){ }
    /** Audit complete data and all present optional metadata; no index or BAM is rewritten. */
    public static Summary audit(Path bam,Path bai,SambambaStreamingBamAudit.Limits bamLimits,Limits indexLimits)throws IOException {
        java.util.Objects.requireNonNull(bamLimits);java.util.Objects.requireNonNull(indexLimits);
        Audit audit=new Audit(indexLimits);readIndex(bai,audit);
        try(var records=new BamIndexStream(bam,bamLimits,audit)){
            if(audit.references.size()!=records.nr)throw new IOException("Complete BAM/BAI reference count differs");
            for(BamIndexStream.Row row;(row=records.next())!=null;)audit.record(row);
            FileIdentity identity=records.identity();audit.finishOffsets(identity.bytes(),records.logicalEnd);audit.metadata();audit.check();
            long mapped=0,unmapped=0;int metadata=0;
            for(Reference ref:audit.references){mapped+=ref.mapped;unmapped+=ref.unmapped;if(ref.meta!=null)metadata++;}
            return new Summary(records.records,mapped,unmapped,audit.unplaced,records.nr,audit.dataChunks,audit.linearEntries,
                metadata,audit.noCoordinatePresent,audit.points.size(),audit.aliases,identity,audit.indexIdentity);
        }
    }
    private static void readIndex(Path path,Audit audit)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>audit.limits.indexBytes)throw new IOException("Require a regular BAI within indexBytes");
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        try(var source=new Counted(Files.newInputStream(path),digest,audit.limits.indexBytes);var in=new BufferedInputStream(source)){
            if(!java.util.Arrays.equals(exact(in,4),new byte[]{'B','A','I',1}))throw new IOException("BAI magic differs; CSI/other formats require their own verifier");
            long count=u32(in);if(count>=16384)throw new IOException("BAI reference count exceeds this Sambamba-compatible profile");
            for(int i=0;i<count;i++){
                audit.check();Reference ref=new Reference();audit.references.add(ref);long bins=u32(in);
                if(bins>37451)throw new IOException("BAI bin count exceeds its format range");
                var seen=new java.util.HashSet<Integer>();
                for(long j=0;j<bins;j++){
                    long rawBin=u32(in),n=u32(in);
                    if(rawBin>37450||rawBin==37449||!seen.add((int)rawBin))throw new IOException("Invalid/repeated BAI bin");
                    if(n>audit.limits.chunks-audit.chunks||n>Integer.MAX_VALUE)throw new IOException("BAI chunk count exceeds declared capacity");
                    audit.chunks+=n;
                    if(rawBin==37450){
                        if(n!=2)throw new IOException("BAI metadata pseudo-bin requires exactly two entries");
                        long begin=i64(in),end=i64(in),mapped=i64(in),unmapped=i64(in);boolean zero=begin==0&&end==0;
                        ref.meta=new Meta(zero?null:audit.point(begin),zero?null:audit.point(end),mapped,unmapped,zero);
                    }else{
                        var list=new ArrayList<Chunk>();long previousEnd=0;boolean first=true;
                        for(long k=0;k<n;k++){
                            if((k&1023)==0)audit.check();long begin=i64(in),end=i64(in);
                            if(Long.compareUnsigned(begin,end)>=0||(!first&&Long.compareUnsigned(previousEnd,begin)>0))throw new IOException("Unsorted/overlapping/reversed BAI data chunks");
                            list.add(new Chunk(audit.point(begin),audit.point(end)));previousEnd=end;first=false;
                        }
                        ref.bins.put((int)rawBin,List.copyOf(list));audit.dataChunks+=n;
                    }
                }
                long linear=u32(in);
                if(linear>131072||linear>audit.limits.linearEntries-audit.linearEntries)throw new IOException("BAI linear index exceeds format/declared capacity");
                audit.linearEntries+=linear;ref.linear=new long[(int)linear];ref.prefix=new long[(int)linear];long maximum=0;
                for(int j=0;j<linear;j++){
                    long offset=i64(in);ref.linear[j]=offset;if(offset!=0)audit.point(offset);
                    if(Long.compareUnsigned(offset,maximum)>0)maximum=offset;ref.prefix[j]=maximum;
                }
            }
            int first=in.read();
            if(first>=0){byte[] raw=new byte[8];raw[0]=(byte)first;System.arraycopy(exact(in,7),0,raw,1,7);audit.noCoordinate=integer64(raw);audit.noCoordinatePresent=true;}
            if(in.read()!=-1)throw new IOException("Unexpected trailing BAI bytes");
            audit.indexIdentity=new FileIdentity(source.bytes,HexFormat.of().formatHex(digest.digest()));
        }
    }
    private static final class Counted extends java.io.FilterInputStream {
        final MessageDigest digest;final long maximum;long bytes;
        Counted(InputStream in,MessageDigest digest,long maximum){super(in);this.digest=digest;this.maximum=maximum;}
        private void add(int n)throws IOException{if(bytes>maximum-n)throw new IOException("BAI grew beyond indexBytes");bytes+=n;}
        @Override public int read()throws IOException{int b=in.read();if(b>=0){add(1);digest.update((byte)b);}return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);if(n>0){add(n);digest.update(b,off,n);}return n;}
    }
    private static byte[] exact(InputStream in,int size)throws IOException {byte[] b=in.readNBytes(size);if(b.length!=size)throw new EOFException("Truncated BAI/BAM data");return b;}
    private static long u32(InputStream in)throws IOException {byte[] b=exact(in,4);return (b[0]&255L)|((b[1]&255L)<<8)|((b[2]&255L)<<16)|((b[3]&255L)<<24);}
    private static long i64(InputStream in)throws IOException {return integer64(exact(in,8));}
    private static long integer64(byte[] b){long n=0;for(int i=0;i<8;i++)n|=(b[i]&255L)<<(8*i);return n;}
}
