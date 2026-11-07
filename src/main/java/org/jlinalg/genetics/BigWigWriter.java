/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Native bigWig v4 bedGraph writer with full raw data, chromosome/R-tree indexes and double total summaries.
 * No zoom approximations are stored. Original zero-based half-open intervals remain distinct from missing bases.
 * Input values follow double-to-float32 conversion; this is not byte identity with the UCSC converter.
 */
public final class BigWigWriter {
    private BigWigWriter() { }
    private static final long UINT32=0xffffffffL;
    private static final int MAGIC=0x888ffc26;
    private static final Comparator<String> NAMES=(a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    /** Explicit storage, precision, dictionary and output capacities. Inputs are bounded to ten million records. */
    public record Options(boolean compressed,int itemsPerSection,int treeFanout,long maximumBytes,
                          boolean requireExactFloat32,boolean includeUnusedChromosomes) {
        /** Validate options before a new file is created. */
        public Options {if(itemsPerSection<1||itemsPerSection>65535||treeFanout<2||treeFanout>1024||maximumBytes<256)throw new IllegalArgumentException("Invalid bigWig storage/capacity options");}
        /** Compressed raw sections, 1024 records/section, 256-way indexes, explicit rounding and used chromosomes. */
        public static Options defaults(){return new Options(true,1024,256,1073741824L,false,false);}
    }
    /** Complete on-disk dictionary, raw population, precision changes and length-weighted total summary.
     * Empty declared tracks have zero covered bases and zero stored summary fields, not measured zero signal.
     */
    public record Report(Map<String,Long> dictionary,int records,int sections,long coveredBases,double minimum,double maximum,
                         double sum,double sumSquares,int roundedValues,int underflowedValues,int maximumRawBlock,long bytes) {
        /** Retain immutable chromosome order. */
        public Report {dictionary=Collections.unmodifiableMap(new LinkedHashMap<>(dictionary));}
    }
    /** Write only to a NEW file. Cancellation/failure preserves any partial file and throws; it admits no result.
     * Chromosomes may occur in any order, each in one contiguous block; intervals within each block must be sorted
     * and nonoverlapping. The binary dictionary/data are ordered by UTF-8 bytes without changing raw intervals.
     * Unused reference chromosomes are omitted only when explicitly selected by the options.
     * @param output NEW binary file
     * @param reference exact positive chromosome lengths, up to unsigned32
     * @param input complete finite bedGraph records
     * @param options explicit storage/precision/dictionary capacities
     * @param check cancellation/integrity callback
     * @return complete stored population and native total summary
     * @throws IOException invalid input, capacity, cancellation callback I/O or file failure
     */
    public static Report write(Path output,Map<String,Long> reference,List<BedGraphSignals.Segment> input,Options options,Runnable check)throws IOException {
        Plan plan=plan(reference,input,options,check);
        try(var file=FileChannel.open(output,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
            var sink=new Sink(file,options.maximumBytes(),false,check);sink.put(new byte[104]);writeChromosomes(sink,plan);require(sink.position()==plan.dataOffset,"Native chromosome tree geometry differs");sink.put(buffer(8).putLong(plan.blocks.size()).array());
            for(var block:plan.blocks){check.run();byte[] raw=raw(plan,block);byte[] data=options.compressed()?compress(raw):raw;block.offset=sink.position();block.bytes=data.length;sink.put(data);}
            long index=sink.position();writeIndex(sink,plan,index);sink.put(buffer(4).putInt(MAGIC).array());long size=sink.position();sink.seek(0);sink.put(header(plan,index));sink.put(summary(plan));require(size==file.size(),"Native bigWig final size differs");return report(plan,size);
        }
    }
    /** Verify every byte/structure of this native profile against complete supplied inputs and options.
     * Compressed blocks are independently inflated and compared as raw values, so valid cross-JVM zlib encodings
     * are accepted. This does not authenticate execution or accept arbitrary external bigWig layouts/zoom levels.
     * @param file complete native output
     * @param reference exact original reference
     * @param input complete original signal
     * @param options original explicit storage/precision/dictionary options
     * @param check cancellation/integrity callback
     * @return verified complete report
     * @throws IOException malformed/changed output or declared capacity failure
     */
    public static Report verify(Path file,Map<String,Long> reference,List<BedGraphSignals.Segment> input,Options options,Runnable check)throws IOException {
        Plan plan=plan(reference,input,options,check);
        try(var channel=FileChannel.open(file,StandardOpenOption.READ)){
            long size=channel.size();require(size>=256&&size<=options.maximumBytes(),"Native bigWig file outside complete byte capacity");ByteBuffer head=read(channel,0,64);long index=head.getLong(24);require(index>=plan.dataOffset+8&&index<size-4,"Invalid native raw-index offset");require(Arrays.equals(head.array(),header(plan,index)),"Native bigWig complete header differs");
            require(Arrays.equals(read(channel,64,40).array(),summary(plan)),"Native stored-value total summary differs");var sink=new Sink(channel,options.maximumBytes(),true,check);sink.seek(104);writeChromosomes(sink,plan);require(sink.position()==plan.dataOffset,"Native chromosome tree extent differs");require(read(channel,plan.dataOffset,8).getLong()==plan.blocks.size(),"Native raw-section count differs");
            Node tree=tree(plan.blocks.size(),options.treeFanout());assign(tree,index+48,options.treeFanout(),32,24);long end=collectBlocks(channel,tree,plan,0);require(end==plan.blocks.size(),"Complete native raw-index population differs");long cursor=plan.dataOffset+8;
            for(var block:plan.blocks){check.run();require(block.offset==cursor&&block.bytes>0&&block.bytes<=2L*plan.maxRaw+128&&block.offset<=index-block.bytes,"Noncontiguous/escaping native raw block");byte[] stored=read(channel,block.offset,Math.toIntExact(block.bytes)).array(),expected=raw(plan,block);byte[] decoded=options.compressed()?inflate(stored,expected.length):stored;require(Arrays.equals(decoded,expected),"Complete native raw interval/float32 population differs");cursor+=block.bytes;}
            require(cursor==index,"Native raw payload has extra/missing bytes");sink.seek(index);writeIndex(sink,plan,index);sink.put(buffer(4).putInt(MAGIC).array());require(sink.position()==size,"Native bigWig has late/truncated bytes");return report(plan,size);
        }
    }
    private static Plan plan(Map<String,Long> reference,List<BedGraphSignals.Segment> supplied,Options options,Runnable check)throws IOException {
        if(reference==null||supplied==null||options==null||check==null)throw new IllegalArgumentException("Require complete bigWig inputs/options/check");require(!reference.isEmpty()&&reference.size()<=100000&&supplied.size()<=10000000,"Native reference/record population exceeds its capacity");var lengths=new HashMap<String,Long>();for(var e:reference.entrySet()){check.run();name(e.getKey());require(e.getValue()!=null&&e.getValue()>0&&e.getValue()<=UINT32,"Chromosome length outside unsigned32");lengths.put(e.getKey(),e.getValue());}
        var groups=new HashMap<String,List<BedGraphSignals.Segment>>();var closed=new HashSet<String>();String previous=null;long previousEnd=0;int rounded=0,underflowed=0;long bases=0;double minimum=0,maximum=0;var sum=new Sum();var squares=new Sum();
        for(var segment:supplied){check.run();require(segment!=null,"Missing native signal record");Long length=lengths.get(segment.chromosome());require(length!=null&&segment.start()>=0&&segment.start()<segment.end()&&segment.end()<=length&&Double.isFinite(segment.value()),"Signal chromosome/coordinate/value differs from exact reference");
            if(!segment.chromosome().equals(previous)){if(previous!=null)closed.add(previous);require(!closed.contains(segment.chromosome()),"Every signal chromosome must occur in one contiguous input block");previous=segment.chromosome();previousEnd=0;}
            require(segment.start()>=previousEnd,"Unsorted/overlapping signal intervals; no automatic clipping, summing or sorting");previousEnd=segment.end();float value=(float)segment.value();require(Float.isFinite(value),"Float32 overflow; native bigWig cannot retain this finite float64 value");boolean changed=Double.doubleToLongBits(segment.value())!=Double.doubleToLongBits((double)value);if(changed){rounded++;require(!options.requireExactFloat32(),"Float32 precision loss is forbidden by the explicit exact policy");}if(segment.value()!=0&&value==0)underflowed++;
            long count=segment.end()-segment.start();if(bases==0){minimum=value;maximum=value;}else{minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);}bases=Math.addExact(bases,count);sum.add((double)value*count);squares.add((double)value*value*count);groups.computeIfAbsent(segment.chromosome(),ignored->new ArrayList<>()).add(segment);
        }
        require(!supplied.isEmpty()||options.includeUnusedChromosomes(),"An empty signal requires explicit inclusion of the declared reference; it is not measured zero coverage");var names=new ArrayList<>(options.includeUnusedChromosomes()?lengths.keySet():groups.keySet());names.sort(NAMES);var chromosomes=new ArrayList<Chromosome>();var records=new ArrayList<BedGraphSignals.Segment>();var blocks=new ArrayList<Block>();int keySize=1,maxRaw=24;
        for(String chr:names){check.run();int id=chromosomes.size();byte[] key=chr.getBytes(StandardCharsets.UTF_8);keySize=Math.max(keySize,key.length);chromosomes.add(new Chromosome(chr,key,lengths.get(chr),id));var values=groups.getOrDefault(chr,List.of());for(int from=0;from<values.size();from+=options.itemsPerSection()){int count=Math.min(options.itemsPerSection(),values.size()-from),first=records.size();records.addAll(values.subList(from,from+count));blocks.add(new Block(id,first,count,values.get(from).start(),values.get(from+count-1).end()));maxRaw=Math.max(maxRaw,24+12*count);}}
        Node chromTree=tree(chromosomes.size(),options.treeFanout());long data=assign(chromTree,136,options.treeFanout(),keySize+8,keySize+8);return new Plan(options,List.copyOf(chromosomes),List.copyOf(records),List.copyOf(blocks),keySize,chromTree,data,maxRaw,bases,minimum,maximum,sum.value(),squares.value(),rounded,underflowed);
    }
    private record Chromosome(String name,byte[] key,long length,int id) { }
    private record Plan(Options options,List<Chromosome> chromosomes,List<BedGraphSignals.Segment> records,List<Block> blocks,int keySize,Node chromTree,long dataOffset,int maxRaw,long bases,double minimum,double maximum,double sum,double squares,int rounded,int underflowed) { }
    private static final class Block {
        final int chromosome,first,count;final long start,end;long offset,bytes;
        Block(int chromosome,int first,int count,long start,long end){this.chromosome=chromosome;this.first=first;this.count=count;this.start=start;this.end=end;}
    }
    private static final class Node {
        final int first,count;final List<Node> children;long offset,end;
        Node(int first,int count,List<Node> children){this.first=first;this.count=count;this.children=List.copyOf(children);}
        boolean leaf(){return children.isEmpty();}
    }
    private static Node tree(int count,int fanout){var level=new ArrayList<Node>();for(int i=0;i<count;i+=fanout)level.add(new Node(i,Math.min(fanout,count-i),List.of()));if(level.isEmpty())level.add(new Node(0,0,List.of()));while(level.size()>1){var next=new ArrayList<Node>();for(int i=0;i<level.size();i+=fanout){var children=level.subList(i,Math.min(level.size(),i+fanout));int n=children.stream().mapToInt(v->v.count).sum();next.add(new Node(children.get(0).first,n,children));}level=next;}return level.get(0);}
    private static long assign(Node node,long offset,int fanout,int leafWidth,int innerWidth){node.offset=offset;long cursor=Math.addExact(offset,4L+(long)fanout*(node.leaf()?leafWidth:innerWidth));for(var child:node.children)cursor=assign(child,cursor,fanout,leafWidth,innerWidth);node.end=cursor;return cursor;}
    private static void writeChromosomes(Sink sink,Plan plan)throws IOException {
        sink.put(buffer(32).putInt(0x78ca8c91).putInt(plan.options.treeFanout()).putInt(plan.keySize).putInt(8).putLong(plan.chromosomes.size()).putLong(0).array());writeChromNode(sink,plan,plan.chromTree);
    }
    private static void writeChromNode(Sink sink,Plan plan,Node node)throws IOException {
        require(sink.position()==node.offset,"Native chromosome node offset differs");var data=buffer(4+plan.options.treeFanout()*(plan.keySize+8));int entries=node.leaf()?node.count:node.children.size();data.put((byte)(node.leaf()?1:0)).put((byte)0).putShort((short)entries);
        for(int i=0;i<entries;i++){int first=node.leaf()?node.first+i:node.children.get(i).first;var chr=plan.chromosomes.get(first);data.put(chr.key);data.position(data.position()+plan.keySize-chr.key.length);if(node.leaf())data.putInt(chr.id).putInt((int)chr.length);else data.putLong(node.children.get(i).offset);}sink.put(data.array());for(var child:node.children)writeChromNode(sink,plan,child);
    }
    private static byte[] raw(Plan plan,Block block){var data=buffer(24+12*block.count);data.putInt(block.chromosome).putInt((int)block.start).putInt((int)block.end).putInt(0).putInt(0).put((byte)1).put((byte)0).putShort((short)block.count);for(int i=0;i<block.count;i++){var record=plan.records.get(block.first+i);data.putInt((int)record.start()).putInt((int)record.end()).putFloat((float)record.value());}return data.array();}
    private static byte[] header(Plan plan,long index){return buffer(64).putInt(MAGIC).putShort((short)4).putShort((short)0).putLong(104).putLong(plan.dataOffset).putLong(index).putShort((short)0).putShort((short)0).putLong(0).putLong(64).putInt(plan.options.compressed()?plan.maxRaw:0).putLong(0).array();}
    private static byte[] summary(Plan plan){return buffer(40).putLong(plan.bases).putDouble(plan.minimum).putDouble(plan.maximum).putDouble(plan.sum).putDouble(plan.squares).array();}
    private static void writeIndex(Sink sink,Plan plan,long offset)throws IOException {
        Node tree=tree(plan.blocks.size(),plan.options.treeFanout());assign(tree,offset+48,plan.options.treeFanout(),32,24);var header=buffer(48).putInt(0x2468ace0).putInt(plan.options.treeFanout()).putLong(plan.blocks.size());bounds(header,plan,0,plan.blocks.size());header.putLong(offset).putInt(1).putInt(0);sink.put(header.array());writeIndexNode(sink,plan,tree);
    }
    private static void writeIndexNode(Sink sink,Plan plan,Node node)throws IOException {
        require(sink.position()==node.offset,"Native raw-index node offset differs");var data=buffer(4+plan.options.treeFanout()*(node.leaf()?32:24));int entries=node.leaf()?node.count:node.children.size();data.put((byte)(node.leaf()?1:0)).put((byte)0).putShort((short)entries);
        for(int i=0;i<entries;i++){int first=node.leaf()?node.first+i:node.children.get(i).first,count=node.leaf()?1:node.children.get(i).count;bounds(data,plan,first,count);if(node.leaf()){var block=plan.blocks.get(first);data.putLong(block.offset).putLong(block.bytes);}else data.putLong(node.children.get(i).offset);}sink.put(data.array());for(var child:node.children)writeIndexNode(sink,plan,child);
    }
    private static void bounds(ByteBuffer data,Plan plan,int first,int count){if(count==0){data.putLong(0).putLong(0);return;}var a=plan.blocks.get(first);var b=plan.blocks.get(first+count-1);data.putInt(a.chromosome).putInt((int)a.start).putInt(b.chromosome).putInt((int)b.end);}
    private static int collectBlocks(FileChannel channel,Node node,Plan plan,int visited)throws IOException {
        if(node.leaf()){for(int i=0;i<node.count;i++){var data=read(channel,node.offset+4L+32L*i+16,16);var block=plan.blocks.get(node.first+i);block.offset=data.getLong();block.bytes=data.getLong();visited++;}}else for(var child:node.children)visited=collectBlocks(channel,child,plan,visited);return visited;
    }
    private static Report report(Plan plan,long bytes){var dictionary=new LinkedHashMap<String,Long>();for(var chr:plan.chromosomes)dictionary.put(chr.name,chr.length);return new Report(dictionary,plan.records.size(),plan.blocks.size(),plan.bases,plan.minimum,plan.maximum,plan.sum,plan.squares,plan.rounded,plan.underflowed,plan.maxRaw,bytes);}
    private static byte[] compress(byte[] raw){var deflater=new Deflater();try{deflater.setInput(raw);deflater.finish();byte[] data=new byte[raw.length+raw.length/100+128];int count=deflater.deflate(data);if(!deflater.finished())throw new IllegalStateException("Native zlib bound was insufficient");return Arrays.copyOf(data,count);}finally{deflater.end();}}
    private static byte[] inflate(byte[] stored,int expected)throws IOException {var inflater=new Inflater();try{inflater.setInput(stored);byte[] raw=new byte[expected];int cursor=0;while(!inflater.finished()&&cursor<raw.length){int n=inflater.inflate(raw,cursor,raw.length-cursor);require(n>0||inflater.finished(),"Native zlib block is truncated/invalid");cursor+=n;}require(inflater.finished()&&cursor==expected&&inflater.getRemaining()==0,"Native compressed block size or complete stream differs");return raw;}catch(DataFormatException failure){throw new IOException("Invalid native zlib block",failure);}finally{inflater.end();}}
    private static ByteBuffer buffer(int size){return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);}
    private static ByteBuffer read(FileChannel file,long offset,int bytes)throws IOException {require(offset>=0&&bytes>=0&&offset<=file.size()-bytes,"Native bigWig has a truncated/escaping field");var result=buffer(bytes);while(result.hasRemaining()){int n=file.read(result,offset+result.position());require(n>0,"Truncated native bigWig field");}result.flip();return result;}
    private static final class Sink {
        final FileChannel channel;final long maximum;final boolean compare;final Runnable check;long position;
        Sink(FileChannel channel,long maximum,boolean compare,Runnable check){this.channel=channel;this.maximum=maximum;this.compare=compare;this.check=check;}
        long position(){return position;}
        void seek(long target)throws IOException {require(target>=0&&target<=maximum,"Native bigWig offset exceeds capacity");position=target;}
        void put(byte[] bytes)throws IOException {check.run();require(position<=maximum-bytes.length,"Native bigWig exceeds complete output byte capacity");if(compare)require(Arrays.equals(read(channel,position,bytes.length).array(),bytes),"Native bigWig complete index/structure differs");else{var data=ByteBuffer.wrap(bytes);while(data.hasRemaining()){check.run();int count=channel.write(data,position+data.position());require(count>0,"Native bigWig output write made no progress");}}position+=bytes.length;}
    }
    private static final class Sum {
        double sum,correction;
        void add(double value){double next=sum+value;correction+=Math.abs(sum)>=Math.abs(value)?(sum-next)+value:(value-next)+sum;sum=next;}
        double value(){return sum+correction;}
    }
    private static void name(String value)throws IOException {require(value!=null&&!value.isEmpty()&&value.length()<=2048&&value.codePoints().noneMatch(c->c<=32||Character.isWhitespace(c)||Character.isISOControl(c))&&StandardCharsets.UTF_8.newEncoder().canEncode(value)&&value.getBytes(StandardCharsets.UTF_8).length<=2048,"Invalid exact bigWig chromosome name");}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
