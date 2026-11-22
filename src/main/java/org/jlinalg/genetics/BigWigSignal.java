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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Bounded local BigWig v4 raw-interval reader;zoom summaries are never substituted. */
public final class BigWigSignal {
    private BigWigSignal() { }
    /** Complete raw signal and on-disk dictionary,with original float32 values promoted exactly. */
    public record Data(Map<String,Long> dictionary,List<BedGraphSignals.Segment> segments,
                       int rawBlocks,int rawSections,Set<Integer> sectionTypes,boolean compressed,ByteOrder byteOrder) {
        public Data {dictionary=Collections.unmodifiableMap(new LinkedHashMap<>(dictionary));segments=List.copyOf(segments);sectionTypes=Set.copyOf(sectionTypes);}
    }
    /** Read every raw data block with explicit file,record and decompression limits. */
    public static Data read(Path path,long maximumBytes,int maximumRecords,int maximumBlockBytes)throws IOException {
        if(maximumBytes<64||maximumRecords<1||maximumRecords>10_000_000||maximumBlockBytes<24||maximumBlockBytes>67_108_864)throw new IllegalArgumentException("Invalid BigWig resource bounds");
        try(var channel=FileChannel.open(path,StandardOpenOption.READ)) {
            Reader reader=new Reader(channel,maximumBytes,maximumRecords,maximumBlockBytes);return reader.read();
        }catch(ArithmeticException|IllegalArgumentException failure){throw new IOException("Invalid BigWig geometry/numeric structure:"+failure.getMessage(),failure);}
    }
    private record Chromosome(String name,long length) { }
    private static final class Reader {
        final FileChannel channel;final long size;final int maximumRecords,maximumBlockBytes;
        ByteOrder order;long dataOffset,indexOffset;int inflateBound,treeBlock,keySize,valueSize;long chromosomesExpected;
        final Map<Integer,Chromosome> chromosomes=new TreeMap<>();final Set<Long> visited=new HashSet<>();
        final TreeMap<Long,Long> blocks=new TreeMap<>();int treeNodes;
        Reader(FileChannel channel,long maximumBytes,int maximumRecords,int maximumBlockBytes)throws IOException {
            this.channel=channel;size=channel.size();this.maximumRecords=maximumRecords;this.maximumBlockBytes=maximumBlockBytes;
            require(size>=64&&size<=maximumBytes,"BigWig is missing/truncated or exceeds maximum bytes");
        }
        Data read()throws IOException {
            ByteBuffer header=bytes(0,64);int magic=header.order(ByteOrder.LITTLE_ENDIAN).getInt(0);
            if(magic==0x888ffc26)order=ByteOrder.LITTLE_ENDIAN;else if(magic==Integer.reverseBytes(0x888ffc26))order=ByteOrder.BIG_ENDIAN;else throw new IOException("Not a binary BigWig file");
            header.order(order);int version=Short.toUnsignedInt(header.getShort(4)),zooms=Short.toUnsignedInt(header.getShort(6));require(version==4,"Native BigWig profile requires version4");
            long chromosomeOffset=positive(header.getLong(8));dataOffset=positive(header.getLong(16));indexOffset=positive(header.getLong(24));long autoSql=header.getLong(36),summary=header.getLong(44),extension=header.getLong(56);
            require(chromosomeOffset>=64L+24L*zooms&&chromosomeOffset<dataOffset&&dataOffset+8<=indexOffset&&indexOffset+48<=size,"Invalid BigWig primary offsets");
            require(header.getShort(32)==0&&header.getShort(34)==0&&autoSql==0,"BigWig field/schema declarations unsupported");require(summary>=0&&summary<size&&extension>=0&&extension<size,"Invalid BigWig optional offsets");
            long buffer=Integer.toUnsignedLong(header.getInt(52));require(buffer<=maximumBlockBytes,"BigWig declared decompression buffer exceeds bound");inflateBound=(int)buffer;
            ByteBuffer tree=bytes(chromosomeOffset,32);require(tree.getInt()==0x78ca8c91,"Invalid chromosome B+tree magic");treeBlock=bounded(tree.getInt(),1,65535);keySize=bounded(tree.getInt(),1,2048);valueSize=tree.getInt();require(valueSize==8,"BigWig chromosome value size must be8");chromosomesExpected=tree.getLong();require(chromosomesExpected>0&&chromosomesExpected<=100000,"Invalid BigWig chromosome count");
            chromosomeNode(chromosomeOffset+32,0);require(chromosomes.size()==chromosomesExpected,"Chromosome dictionary count differs");var dictionary=new LinkedHashMap<String,Long>();for(var entry:chromosomes.entrySet()){var chr=entry.getValue();require(dictionary.put(chr.name,chr.length)==null,"Duplicate chromosome name");}
            visited.clear();treeNodes=0;ByteBuffer rtree=bytes(indexOffset,48);require(rtree.getInt()==0x2468ace0,"Invalid raw-data R-tree magic");treeBlock=bounded(rtree.getInt(),1,65535);long indexedItems=rtree.getLong();require(indexedItems>=0&&indexedItems<=maximumRecords,"Excessive R-tree item count");rawNode(indexOffset+48,0);
            long priorEnd=dataOffset+8;for(var block:blocks.entrySet()){require(block.getKey()>=priorEnd&&block.getValue()>0&&block.getKey()<=indexOffset-block.getValue(),"Overlapping/escaping raw data blocks");priorEnd=block.getKey()+block.getValue();}
            long expectedSections=bytes(dataOffset,8).getLong();require(expectedSections>=0&&expectedSections<=maximumRecords,"Invalid raw section count");int sections=0;var types=new HashSet<Integer>();var values=new ArrayList<BedGraphSignals.Segment>();
            for(var entry:blocks.entrySet()) {
                require(entry.getValue()<=maximumBlockBytes,"Compressed/raw block exceeds bound");byte[] raw=bytes(entry.getKey(),entry.getValue().intValue()).array();ByteBuffer data=ByteBuffer.wrap(inflateBound==0?raw:inflate(raw)).order(order);
                while(data.hasRemaining()) {
                    require(data.remaining()>=24,"Truncated raw section header");int id=data.getInt();long lower=u32(data),upper=u32(data),step=u32(data),span=u32(data);int type=Byte.toUnsignedInt(data.get());data.get();int count=Short.toUnsignedInt(data.getShort());Chromosome chr=chromosomes.get(id);require(chr!=null&&lower<=upper&&upper<=chr.length,"Raw section chromosome/bounds differ");require(type>=1&&type<=3,"Unsupported raw BigWig section type");types.add(type);require(++sections<=maximumRecords,"Too many raw sections");
                    require(values.size()<=maximumRecords-count,"BigWig exceeds raw record bound");int width=type==1?12:type==2?8:4;require(data.remaining()>=(long)count*width,"Truncated raw section values");
                    for(int i=0;i<count;i++) {
                        long start=type==3?Math.addExact(lower,Math.multiplyExact(step,i)):u32(data),end=type==1?u32(data):Math.addExact(start,span);float value=data.getFloat();require(start>=lower&&start<end&&end<=upper&&Float.isFinite(value),"Invalid/nonfinite raw BigWig interval");values.add(new BedGraphSignals.Segment(chr.name,start,end,value));
                    }
                }
            }
            require(sections==expectedSections,"Raw section count differs from data header");values.sort(Comparator.comparing(BedGraphSignals.Segment::chromosome).thenComparingLong(BedGraphSignals.Segment::start).thenComparingLong(BedGraphSignals.Segment::end));BedGraphSignals.Segment previous=null;
            for(var value:values){if(previous!=null&&previous.chromosome().equals(value.chromosome()))require(previous.end()<=value.start(),"Overlapping raw BigWig intervals are ambiguous");previous=value;}
            return new Data(dictionary,values,blocks.size(),sections,types,inflateBound!=0,order);
        }
        void chromosomeNode(long offset,int depth)throws IOException {
            node(offset,depth);ByteBuffer head=bytes(offset,4);int leaf=Byte.toUnsignedInt(head.get());head.get();int count=Short.toUnsignedInt(head.getShort());require((leaf==0||leaf==1)&&count>0&&count<=treeBlock,"Invalid chromosome tree node");require((long)count*(keySize+8)<=maximumBlockBytes,"Chromosome tree node exceeds memory bound");ByteBuffer entries=bytes(offset+4,Math.multiplyExact(count,keySize+8));
            for(int i=0;i<count;i++){byte[] key=new byte[keySize];entries.get(key);int n=0;while(n<key.length&&key[n]!=0)n++;for(int j=n;j<key.length;j++)require(key[j]==0,"Nonzero bytes after chromosome key terminator");String name=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(key,0,n)).toString();require(!name.isBlank()&&name.chars().noneMatch(Character::isISOControl),"Invalid chromosome name");if(leaf==1){int id=entries.getInt();long length=u32(entries);require(id>=0&&length>0&&length<=Integer.MAX_VALUE&&chromosomes.put(id,new Chromosome(name,length))==null,"Invalid/duplicate chromosome identity");}else chromosomeNode(positive(entries.getLong()),depth+1);}
        }
        void rawNode(long offset,int depth)throws IOException {
            node(offset,depth);ByteBuffer head=bytes(offset,4);int leaf=Byte.toUnsignedInt(head.get());head.get();int count=Short.toUnsignedInt(head.getShort());require((leaf==0||leaf==1)&&count<=treeBlock,"Invalid raw R-tree node");require((long)count*(leaf==1?32:24)<=maximumBlockBytes,"R-tree node exceeds memory bound");ByteBuffer entries=bytes(offset+4,Math.multiplyExact(count,leaf==1?32:24));
            for(int i=0;i<count;i++){long firstChr=u32(entries),firstBase=u32(entries),lastChr=u32(entries),lastBase=u32(entries);require(firstChr<=lastChr&&firstChr<=Integer.MAX_VALUE&&lastChr<=Integer.MAX_VALUE&&chromosomes.containsKey((int)firstChr)&&chromosomes.containsKey((int)lastChr)&&firstBase<=chromosomes.get((int)firstChr).length&&lastBase<=chromosomes.get((int)lastChr).length,"Invalid R-tree range");long child=positive(entries.getLong());if(leaf==1){long length=positive(entries.getLong());Long prior=blocks.putIfAbsent(child,length);require(prior==null||prior.longValue()==length,"Conflicting raw block size");require(blocks.size()<=maximumRecords,"Too many raw blocks");}else rawNode(child,depth+1);}
        }
        void node(long offset,int depth)throws IOException {require(depth<=64&&++treeNodes<=Math.min(1_000_000,maximumRecords+1000)&&visited.add(offset),"Cyclic/excessive binary tree");}
        byte[] inflate(byte[] raw)throws IOException {
            Inflater inflater=new Inflater();try{inflater.setInput(raw);byte[] result=new byte[inflateBound];int used=0;while(!inflater.finished()){require(used<result.length,"Raw block exceeds declared decompression bound");int n=inflater.inflate(result,used,result.length-used);require(n>0||inflater.finished(),"Truncated/invalid zlib raw block");used+=n;}require(inflater.getRemaining()==0,"Trailing compressed block bytes");return java.util.Arrays.copyOf(result,used);}catch(DataFormatException failure){throw new IOException("Invalid zlib raw block",failure);}finally{inflater.end();}
        }
        ByteBuffer bytes(long offset,int length)throws IOException {
            require(offset>=0&&length>=0&&offset<=size-length,"Truncated/escaping BigWig offset");ByteBuffer value=ByteBuffer.allocate(length).order(order==null?ByteOrder.LITTLE_ENDIAN:order);while(value.hasRemaining()){int n=channel.read(value,offset+value.position());require(n>0,"Truncated BigWig read");}value.flip();return value;
        }
        long positive(long value)throws IOException {require(value>=0&&value<size,"Invalid unsigned64 file offset/length");return value;}
        long u32(ByteBuffer value){return Integer.toUnsignedLong(value.getInt());}
        int bounded(int value,int minimum,int maximum)throws IOException {require(value>=minimum&&value<=maximum,"Invalid binary tree size");return value;}
    }
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
