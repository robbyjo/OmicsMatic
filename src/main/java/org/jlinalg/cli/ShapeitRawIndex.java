/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Disk-backed, complete raw-record identities. No join reconstruction or phasing. */
final class ShapeitRawIndex {
    private ShapeitRawIndex() { }
    private static final byte[] INDEX_MAGIC = "OMICS-BCF-IDX1\n\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DATA_MAGIC = "OMICS-BCF-DAT1\n\0".getBytes(StandardCharsets.US_ASCII);
    private static final int INDEX_HEADER = 24, ENTRY_BYTES = 96;

    record Limits(long spoolBytes, int recordBytes, int coordinateRows, long coordinateBytes) {
        Limits {
            if (spoolBytes < INDEX_HEADER + DATA_MAGIC.length || recordBytes < 1 || recordBytes > Integer.MAX_VALUE-65536
                    || coordinateRows < 1 || coordinateBytes < 1) throw new IllegalArgumentException("Require positive complete disk-index capacities");
        }
        static Limits defaults() { return new Limits(17179869184L, 67108864, 250000, 268435456L); }
    }
    record Identity(long bytes, String sha256) { }
    record Entry(int rid, int position, int referenceLength, int blockBytes, long ordinal, long offset,
                 String wireSha256, String blockSha256) { }
    @FunctionalInterface interface Filter { boolean includes(String contig, int position, int referenceLength); }

    static final class Store {
        private final Path source, directory, index, data;
        private final ShapeitBcf.HeaderView header;
        private final ShapeitBcf.Report report;
        private final Identity sourceIdentity, indexIdentity, dataIdentity;
        private final Limits limits;
        private final Runnable check;
        private Store(Path source, Path directory, ShapeitBcf.HeaderView header, ShapeitBcf.Report report,
                      Identity sourceIdentity, Identity indexIdentity, Identity dataIdentity, Limits limits, Runnable check) {
            this.source=source;this.directory=directory;this.index=directory.resolve("records.idx");this.data=directory.resolve("records.dat");
            this.header=header;this.report=report;this.sourceIdentity=sourceIdentity;this.indexIdentity=indexIdentity;this.dataIdentity=dataIdentity;
            this.limits=limits;this.check=check;
        }
        Path source() { return source; }
        Path directory() { return directory; }
        ShapeitBcf.HeaderView header() { return header; }
        ShapeitBcf.Report report() { return report; }
        Identity sourceIdentity() { return sourceIdentity; }
        Identity indexIdentity() { return indexIdentity; }
        Identity dataIdentity() { return dataIdentity; }
        void verify() throws IOException {
            if (!sourceIdentity.equals(identity(source,sourceIdentity.bytes(),check))
                    || !indexIdentity.equals(identity(index,indexIdentity.bytes(),check))
                    || !dataIdentity.equals(identity(data,dataIdentity.bytes(),check)))
                throw new IOException("SHAPEIT raw source/index changed; SKIP dependent analysis, preserve this spool and rebuild in a fresh directory");
        }
        Reader open() throws IOException { verify(); return new Reader(this); }
    }

    static Store build(Path supplied, Path destination, ShapeitBcf.Limits rawLimits, Limits limits, Runnable check) throws IOException {
        Objects.requireNonNull(rawLimits);Objects.requireNonNull(limits);Objects.requireNonNull(check);
        Path source=supplied.toAbsolutePath().normalize(), directory=destination.toAbsolutePath().normalize();
        canonicalParent(directory);Files.createDirectory(directory);
        Path index=directory.resolve("records.idx"),data=directory.resolve("records.dat");
        try {
            Files.createFile(index);Files.createFile(data);
            ShapeitBcf.HeaderView[] header=new ShapeitBcf.HeaderView[1];
            ShapeitBcf.Report report;
            try (var idx=new RandomAccessFile(index.toFile(),"rw");var dat=new RandomAccessFile(data.toFile(),"rw")) {
                idx.write(INDEX_MAGIC);idx.writeLong(0);dat.write(DATA_MAGIC);
                var rids=new HashMap<String,Integer>();int[] previous={-1,-1};long[] rows={0};
                report=ShapeitBcf.read(source,rawLimits,new ShapeitBcf.Visitor() {
                    @Override public void header(ShapeitBcf.HeaderView value) throws IOException {
                        header[0]=value;
                        for (var entry:value.contigs().entrySet()) if(rids.put(entry.getValue(),entry.getKey())!=null)
                            throw new IOException("Repeated contig name cannot form an unambiguous coordinate index");
                    }
                    @Override public void row(ShapeitBcf.Row row) throws IOException {
                        check.run();Integer rid=rids.get(row.contig());
                        if(rid==null||rid<previous[0]||rid==previous[0]&&row.position()<previous[1])
                            throw new IOException("SHAPEIT raw BCF is not sorted in its own contig dictionary; preserve the input and supply a separately verified sorted BCF/index");
                        previous[0]=rid;previous[1]=row.position();
                        byte[] serialized=encode(row,limits),compressed=compress(serialized,limits);
                        int blockBytes=Math.addExact(4,compressed.length);
                        long needed=Math.addExact(Math.addExact(idx.getFilePointer(),ENTRY_BYTES),Math.addExact(dat.getFilePointer(),blockBytes));
                        if(needed>limits.spoolBytes()) throw new IOException("Complete SHAPEIT disk spool capacity exceeded; raise the declared capacity with sufficient disk space; no partial analysis is complete");
                        long offset=dat.getFilePointer();dat.writeInt(serialized.length);dat.write(compressed);
                        idx.writeInt(rid);idx.writeInt(row.position());idx.writeInt(row.referenceLength());idx.writeInt(blockBytes);
                        idx.writeLong(row.ordinal());idx.writeLong(offset);idx.write(HexFormat.of().parseHex(row.rawWireSha256()));
                        var digest=hasher();digest.update(intBytes(serialized.length));digest.update(compressed);idx.write(digest.digest());
                        if(row.ordinal()!=++rows[0]) throw new IOException("Raw BCF record ordinal is incomplete");
                    }
                },check);
                if(!report.complete()||header[0]==null||report.records()!=rows[0])throw new IOException("Incomplete SHAPEIT raw input cannot form a disk index");
                idx.seek(INDEX_MAGIC.length);idx.writeLong(report.records());
                idx.getFD().sync();dat.getFD().sync();
            }
            Identity original=identity(source,rawLimits.inputBytes(),check);
            if(!original.sha256().equals(report.sourceSha256()))throw new IOException("SHAPEIT source changed between raw decoding and disk-index completion");
            Store result=new Store(source,directory,header[0],report,original,identity(index,limits.spoolBytes(),check),identity(data,limits.spoolBytes(),check),limits,check);
            if(Math.addExact(result.indexIdentity.bytes(),result.dataIdentity.bytes())>limits.spoolBytes())throw new IOException("Complete SHAPEIT spool exceeded its declared capacity");
            Files.writeString(directory.resolve("status.txt"),"COMPLETE RAW INDEX\nNo join, phasing admission or analysis completion is certified.\n",StandardOpenOption.CREATE_NEW);
            return result;
        } catch(IOException|RuntimeException error) {
            try {Files.writeString(directory.resolve("status.txt"),"FAILED; dependent analysis SKIPPED\n"+error+"\nPreserve this directory and original inputs; correct the cause and use a fresh spool directory.\n",StandardOpenOption.CREATE_NEW);}
            catch(IOException statusError){error.addSuppressed(statusError);}
            throw new IOException("SHAPEIT disk-index build failed; dependent analysis SKIPPED. Preserve "+directory+" and correct the cause before rebuilding in a fresh directory",error);
        }
    }

    static final class Reader implements AutoCloseable {
        private final Store store;
        private final RandomAccessFile index,data;
        private final Map<String,Integer> rids=new HashMap<>();
        private boolean closed;
        private Reader(Store store) throws IOException {
            this.store=store;RandomAccessFile idx=null,dat=null;
            try {
                idx=new RandomAccessFile(store.index.toFile(),"r");dat=new RandomAccessFile(store.data.toFile(),"r");
                byte[] magic=new byte[16];idx.readFully(magic);
                if(!Arrays.equals(magic,INDEX_MAGIC)||idx.readLong()!=store.report.records()
                        ||idx.length()!=Math.addExact(INDEX_HEADER,Math.multiplyExact((long)ENTRY_BYTES,store.report.records())))throw new IOException("Invalid complete raw-index framing");
                dat.readFully(magic);if(!Arrays.equals(magic,DATA_MAGIC))throw new IOException("Invalid complete raw-data framing");
                for(var item:store.header.contigs().entrySet())rids.put(item.getValue(),item.getKey());
                index=idx;data=dat;
            } catch(IOException|RuntimeException error) {
                if(idx!=null)try{idx.close();}catch(IOException ignored){error.addSuppressed(ignored);}
                if(dat!=null)try{dat.close();}catch(IOException ignored){error.addSuppressed(ignored);}
                throw error;
            }
        }
        Entry entry(long zeroOrdinal) throws IOException {
            requireOpen();store.check.run();
            if(zeroOrdinal<0||zeroOrdinal>=store.report.records())throw new IOException("Raw-index ordinal outside the complete population");
            index.seek(Math.addExact(INDEX_HEADER,Math.multiplyExact(zeroOrdinal,ENTRY_BYTES)));
            int rid=index.readInt(),position=index.readInt(),length=index.readInt(),block=index.readInt();long ordinal=index.readLong(),offset=index.readLong();
            byte[] wire=new byte[32],sha=new byte[32];index.readFully(wire);index.readFully(sha);
            if(!store.header.contigs().containsKey(rid)||position<1||length<1||block<5||block>(long)store.limits.recordBytes()+65536
                    ||ordinal!=zeroOrdinal+1||offset<DATA_MAGIC.length||offset>store.dataIdentity.bytes()-block)
                throw new IOException("Invalid raw-index entry; preserve failed state and rebuild from the verified source");
            return new Entry(rid,position,length,block,ordinal,offset,HexFormat.of().formatHex(wire),HexFormat.of().formatHex(sha));
        }
        List<Entry> at(String contig,int position,Filter filter) throws IOException {
            requireOpen();Integer rid=rids.get(contig);if(rid==null)return List.of();
            long low=0,high=store.report.records();
            while(low<high){long mid=low+(high-low)/2;Entry e=entry(mid);if(e.rid()<rid||e.rid()==rid&&e.position()<position)low=mid+1;else high=mid;}
            var rows=new ArrayList<Entry>();
            for(long i=low;i<store.report.records();i++){
                Entry e=entry(i);if(e.rid()!=rid||e.position()!=position)break;
                if(!filter.includes(contig,e.position(),e.referenceLength()))continue;
                long retained=Math.multiplyExact(rows.size()+1L,1024L);
                if(rows.size()>=store.limits.coordinateRows()||retained>store.limits.coordinateBytes()||retained>Runtime.getRuntime().maxMemory()/2)
                    throw new IOException("Complete SHAPEIT coordinate bucket exceeds capacity; raise coordinate/heap limits without truncating duplicate records");
                rows.add(e);
            }
            return rows;
        }
        long census(Filter filter) throws IOException {
            long total=0;for(long i=0;i<store.report.records();i++){Entry e=entry(i);if(filter.includes(store.header.contigs().get(e.rid()),e.position(),e.referenceLength()))total++;}return total;
        }
        ShapeitBcf.Row row(Entry e) throws IOException {
            requireOpen();store.check.run();memory(Math.multiplyExact(8L,e.blockBytes()));
            byte[] block=new byte[e.blockBytes()];data.seek(e.offset());data.readFully(block);
            if(!hex(block).equals(e.blockSha256()))throw new IOException("Raw-record spool block changed or is incomplete");
            var input=new DataInputStream(new ByteArrayInputStream(block));int size=input.readInt();
            if(size<1||size>store.limits.recordBytes())throw new IOException("Raw-record decoded spool capacity exceeded");
            memory(Math.multiplyExact(8L,Math.max(size,e.blockBytes())));
            byte[] serialized;
            try(var inflated=new InflaterInputStream(input)){
                serialized=inflated.readNBytes(size);
                if(serialized.length!=size||inflated.read()!=-1)throw new IOException("Incomplete or excessive compressed raw-record payload");
            }
            ShapeitBcf.Row row=decode(serialized);
            if(row.ordinal()!=e.ordinal()||!row.contig().equals(store.header.contigs().get(e.rid()))||row.position()!=e.position()
                    ||row.referenceLength()!=e.referenceLength()||!row.rawWireSha256().equals(e.wireSha256()))throw new IOException("Raw-record DTO and physical index identity disagree");
            return row;
        }
        private void requireOpen() throws IOException {if(closed)throw new IOException("Raw-index reader is closed");}
        @Override public void close() throws IOException {if(!closed){closed=true;try{index.close();}finally{data.close();}}}
    }

    /** One bounded coordinate bucket and an on-disk occurrence bitmap, never a WGS hash map. */
    static final class Cursor implements AutoCloseable {
        private final Store store;private final Reader reader;private final RandomAccessFile seen;private final Filter filter;
        private final Map<String,ArrayDeque<Entry>> bucket=new HashMap<>();
        private String contig;private int position;private long remaining,visits;private boolean failed,finished,closed;
        Cursor(Store store,Path bitmap,Filter filter) throws IOException {
            this.store=store;this.filter=Objects.requireNonNull(filter);reader=store.open();
            RandomAccessFile file=null;
            try {canonicalParent(bitmap.toAbsolutePath().normalize());Files.createFile(bitmap);file=new RandomAccessFile(bitmap.toFile(),"rw");file.setLength((store.report.records()+7)/8);seen=file;}
            catch(IOException|RuntimeException error){reader.close();if(file!=null)file.close();throw error;}
        }
        ShapeitBcf.Row take(String name,int pos,String wireSha256) throws IOException {
            if(failed||finished||closed)throw new IOException("Raw-link cursor is no longer provisional");
            try {
                if(!Objects.equals(contig,name)||position!=pos){seal();bucket.clear();contig=name;position=pos;
                    for(Entry e:reader.at(name,pos,filter)){bucket.computeIfAbsent(e.wireSha256(),k->new ArrayDeque<>()).add(e);remaining++;}}
                var queue=bucket.get(wireSha256);if(queue==null||queue.isEmpty())throw new IOException("Original join record does not match an unconsumed raw BCF occurrence at the requested coordinate");
                Entry e=queue.removeFirst();long byteOffset=(e.ordinal()-1)>>>3;int mask=1<<((e.ordinal()-1)&7);
                seen.seek(byteOffset);int value=seen.readUnsignedByte();if((value&mask)!=0)throw new IOException("Original join repeated a physical raw BCF occurrence");
                ShapeitBcf.Row row=reader.row(e);seen.seek(byteOffset);seen.writeByte(value|mask);remaining--;visits++;return row;
            } catch(IOException|RuntimeException error){failed=true;throw error;}
        }
        long finish(long originalVisits) throws IOException {
            if(failed||finished||closed)throw new IOException("Raw-link cursor cannot certify completion after failure, close or repeated finish");
            try {seal();long expected=reader.census(filter);if(visits!=expected||visits!=originalVisits)throw new IOException("Incomplete SHAPEIT join census: expected "+expected+", linked "+visits+", original footer "+originalVisits+"; dependent analysis SKIPPED");
                if(seen.length()!=(store.report.records()+7)/8)throw new IOException("Raw-link occurrence bitmap changed length");
                seen.seek(0);long marked=0;for(long i=0;i<seen.length();i++){int value=seen.readUnsignedByte();if(i==seen.length()-1&&store.report.records()%8!=0&&(value>>> (store.report.records()%8))!=0)throw new IOException("Raw-link bitmap contains out-of-population occurrences");marked+=Integer.bitCount(value);}
                if(marked!=visits)throw new IOException("Raw-link occurrence bitmap and complete visit count disagree");
                store.verify();finished=true;return visits;
            } catch(IOException|RuntimeException error){failed=true;throw error;}
        }
        private void seal() throws IOException {if(remaining!=0)throw new IOException("Original join omitted raw records at a visited coordinate; dependent analysis SKIPPED");}
        @Override public void close() throws IOException {if(!closed){closed=true;try{reader.close();}finally{seen.close();}}}
    }

    static Identity identity(Path supplied,long maximum,Runnable check) throws IOException {
        Path path=supplied.toAbsolutePath().normalize();
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Require a canonical regular SHAPEIT resource: "+path);
        var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(before.size()>maximum)throw new IOException("SHAPEIT resource exceeds declared capacity: "+path);
        MessageDigest hash=hasher();long bytes=0;
        try(var input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1){check.run();bytes=Math.addExact(bytes,n);if(bytes>maximum)throw new IOException("SHAPEIT resource capacity exceeded");hash.update(buffer,0,n);}}
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(bytes!=before.size()||bytes!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey())||!after.isRegularFile())throw new IOException("SHAPEIT resource changed during fingerprinting");
        return new Identity(bytes,HexFormat.of().formatHex(hash.digest()));
    }
    private static void canonicalParent(Path path) throws IOException {Path parent=path.getParent();if(parent==null||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS)||!parent.toRealPath().equals(parent))throw new IOException("Require an existing canonical parent directory: "+path);}
    private static MessageDigest hasher(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static String hex(byte[] bytes){return HexFormat.of().formatHex(hasher().digest(bytes));}
    private static byte[] intBytes(int value){return new byte[]{(byte)(value>>>24),(byte)(value>>>16),(byte)(value>>>8),(byte)value};}
    private static void memory(long estimate) throws IOException {if(estimate>Runtime.getRuntime().maxMemory()/2)throw new IOException("Complete SHAPEIT spool record needs more Java heap; raise -Xmx without dropping fields or samples");}
    private static byte[] encode(ShapeitBcf.Row row,Limits limits) throws IOException {
        long estimate=256L+3L*(row.contig().length()+row.id().length()+row.reference().length());
        for(String s:row.alternates())estimate=Math.addExact(estimate,4L+3L*s.length());
        for(String s:row.filters())estimate=Math.addExact(estimate,4L+3L*s.length());
        for(ShapeitBcf.Field f:row.info().values())estimate=Math.addExact(estimate,24L+3L*f.name().length()+f.payloadBytes());
        for(ShapeitBcf.Field f:row.formats())estimate=Math.addExact(estimate,24L+3L*f.name().length()+f.payloadBytes());
        memory(Math.multiplyExact(8L,estimate));
        var bytes=new ByteArrayOutputStream((int)Math.min(estimate,limits.recordBytes()));
        try(var output=new DataOutputStream(new Limited(bytes,limits.recordBytes()))){
            output.writeInt(1);output.writeLong(row.ordinal());text(output,row.contig());output.writeInt(row.position());output.writeInt(row.referenceLength());
            text(output,row.id());text(output,row.reference());strings(output,row.alternates());output.writeInt(row.qualityBits());strings(output,row.filters());
            output.writeInt(row.info().size());for(var f:row.info().values())field(output,f);
            output.writeInt(row.formats().size());for(var f:row.formats())field(output,f);output.write(HexFormat.of().parseHex(row.rawWireSha256()));
        }
        return bytes.toByteArray();
    }
    private static byte[] compress(byte[] bytes,Limits limits) throws IOException {
        var output=new ByteArrayOutputStream();try(var zipped=new DeflaterOutputStream(new Limited(output,(long)limits.recordBytes()+65532))){zipped.write(bytes);}return output.toByteArray();
    }
    private static ShapeitBcf.Row decode(byte[] bytes) throws IOException {
        try(var input=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(input.readInt()!=1)throw new IOException("Unsupported raw spool record version");long ordinal=input.readLong();String contig=text(input);int pos=input.readInt(),length=input.readInt();
            String id=text(input),ref=text(input);List<String> alts=strings(input);int qual=input.readInt();List<String> filters=strings(input);
            var info=new LinkedHashMap<String,ShapeitBcf.Field>();int n=count(input);for(int i=0;i<n;i++){var f=field(input);if(info.put(f.name(),f)!=null)throw new IOException("Repeated INFO spool field");}
            var formats=new ArrayList<ShapeitBcf.Field>();var names=new HashSet<String>();n=count(input);for(int i=0;i<n;i++){var f=field(input);if(!names.add(f.name()))throw new IOException("Repeated FORMAT spool field");formats.add(f);}
            byte[] hash=input.readNBytes(32);if(hash.length!=32||input.read()!=-1||ordinal<1||contig.isEmpty()||pos<1||length<1||ref.isEmpty())throw new IOException("Incomplete raw spool record");
            return new ShapeitBcf.Row(ordinal,contig,pos,length,id,ref,alts,qual,filters,info,formats,HexFormat.of().formatHex(hash));
        }catch(IllegalArgumentException error){throw new IOException("Invalid raw spool field",error);}
    }
    private static int count(DataInputStream in) throws IOException {int n=in.readInt();if(n<0||n>in.available()/4)throw new IOException("Spool field count exceeds its complete record");return n;}
    private static void text(DataOutputStream out,String value) throws IOException {byte[] b=value.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length);out.write(b);}
    private static String text(DataInputStream in) throws IOException {int n=in.readInt();if(n<0||n>in.available())throw new IOException("Spool text exceeds its complete record");byte[] b=in.readNBytes(n);return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b)).toString();}
    private static void strings(DataOutputStream out,List<String> values) throws IOException {out.writeInt(values.size());for(String s:values)text(out,s);}
    private static List<String> strings(DataInputStream in) throws IOException {var values=new ArrayList<String>();int n=count(in);for(int i=0;i<n;i++)values.add(text(in));return values;}
    private static void field(DataOutputStream out,ShapeitBcf.Field f) throws IOException {text(out,f.name());out.writeInt(f.type());out.writeInt(f.width());out.writeInt(f.cells());out.writeInt(f.payloadBytes());out.write(f.payload());}
    private static ShapeitBcf.Field field(DataInputStream in) throws IOException {String name=text(in);int type=in.readInt(),width=in.readInt(),cells=in.readInt(),n=in.readInt();if(n<0||n>in.available())throw new IOException("Spool payload exceeds its complete record");return new ShapeitBcf.Field(name,type,width,cells,in.readNBytes(n));}
    private static final class Limited extends OutputStream {
        private final OutputStream output;private final long maximum;private long count;
        Limited(OutputStream output,long maximum){this.output=output;this.maximum=maximum;}
        private void reserve(int n) throws IOException {if(n<0||(count=Math.addExact(count,n))>maximum)throw new IOException("Complete SHAPEIT spool record exceeds declared capacity; raise the limit without truncating fields");}
        @Override public void write(int b) throws IOException {reserve(1);output.write(b);}
        @Override public void write(byte[] b,int offset,int length) throws IOException {reserve(length);output.write(b,offset,length);}
    }
}
