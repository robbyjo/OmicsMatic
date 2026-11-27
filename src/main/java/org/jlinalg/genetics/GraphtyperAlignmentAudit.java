/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import htsjdk.samtools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.sequence.FastaIndexes;

/** Complete aligned-read census and independent index-query comparison; no read filtering or calling. */
public final class GraphtyperAlignmentAudit {
    private GraphtyperAlignmentAudit() { }
    public record Region(String contig,int start,int end) {
        public Region {if(contig==null||!contig.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")||start<1||end<start)
            throw new IllegalArgumentException("Literal contig and one-based inclusive region required");}
        @Override public String toString(){return contig+":"+start+"-"+end;}
    }
    public record Limits(long records,long samCharacters,int lineCharacters,int readLength) {
        public Limits {if(records<1||samCharacters<1||lineCharacters<256||readLength<1)throw new IllegalArgumentException("Positive complete alignment capacities required");}
    }
    public record Reference(Map<String,Long> lengths,Map<String,String> upperCaseSequenceMd5) {
        public Reference{lengths=Collections.unmodifiableMap(new LinkedHashMap<>(lengths));upperCaseSequenceMd5=Map.copyOf(upperCaseSequenceMd5);}
    }
    public record Query(String region,long records,String allSamFieldsSha256) { }
    public record Result(long records,long mappedRecords,long samCharacters,int maximumReadLength,
                         List<String> sampleIds,Map<String,String> readGroups,String allSamFieldsSha256,List<Query> queries) {
        public Result {sampleIds=List.copyOf(sampleIds);readGroups=Map.copyOf(readGroups);queries=List.copyOf(queries);}
    }
    /** Validate actual FASTA offsets and widths; MD5 follows the SAM uppercase-sequence convention. */
    public static Reference reference(Path fasta,Path fai,int lineCharacters,Runnable check)throws IOException {
        if(lineCharacters<256)throw new IllegalArgumentException("Reference line capacity required");
        FastaIndexes.validate(fasta,fai);var lengths=new LinkedHashMap<String,Long>();
        for(var entry:FastaIndexes.build(fasta))if(lengths.putIfAbsent(entry.getContig(),entry.getSize())!=null)throw new IOException("Duplicate literal FASTA sequence name");
        var hashes=new LinkedHashMap<String,String>();String name=null;MessageDigest md5=null;long bases=0;
        try(var in=new BufferedReader(Files.newBufferedReader(fasta,StandardCharsets.US_ASCII),65536)){
            for(String line;(line=line(in,lineCharacters))!=null;){check.run();
                if(line.startsWith(">")){
                    if(name!=null)finish(name,bases,md5,lengths,hashes);
                    String header=line.substring(1);int end=0;while(end<header.length()&&!Character.isWhitespace(header.charAt(end)))end++;
                    name=header.substring(0,end);if(name.isEmpty()||hashes.containsKey(name))throw new IOException("Empty or duplicate original reference name");md5=digest("MD5");bases=0;
                }else{
                    if(name==null||line.isEmpty())throw new IOException("Invalid or empty original FASTA sequence line");
                    for(int i=0;i<line.length();i++){char c=line.charAt(i);if("ACGTRYSWKMBDHVNacgtryswkmbdhvn".indexOf(c)<0)throw new IOException("Unsupported reference sequence character; no sequence repair");md5.update((byte)Character.toUpperCase(c));}
                    bases=Math.addExact(bases,line.length());
                }
            }
        }
        if(name!=null)finish(name,bases,md5,lengths,hashes);if(!hashes.keySet().equals(lengths.keySet()))throw new IOException("Complete reference population disagrees with actual FAI");return new Reference(lengths,hashes);
    }
    private static void finish(String name,long bases,MessageDigest digest,Map<String,Long> lengths,Map<String,String> hashes)throws IOException {
        if(!Objects.equals(lengths.get(name),bases)||hashes.putIfAbsent(name,HexFormat.of().formatHex(digest.digest()))!=null)throw new IOException("Original reference sequence length/name population differs: "+name);
    }
    private static String line(BufferedReader in,int limit)throws IOException {
        var s=new StringBuilder();for(int c;(c=in.read())!=-1;){if(c=='\n'){if(!s.isEmpty()&&s.charAt(s.length()-1)=='\r')s.setLength(s.length()-1);return s.toString();}if(s.length()>=limit)throw new IOException("Complete reference line capacity exceeded");s.append((char)c);}return s.isEmpty()?null:s.toString();
    }
    private static MessageDigest digest(String algorithm){try{return MessageDigest.getInstance(algorithm);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static void append(MessageDigest digest,String text){digest.update(text.getBytes(StandardCharsets.UTF_8));if(!text.endsWith("\n"))digest.update((byte)'\n');}
    private static SamReader open(Path bam,Path index,Path fasta){return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).referenceSequence(fasta).open(SamInputResource.of(bam).index(index));}
    /** All sequential records and every requested indexed-overlap population must agree. */
    public static Result audit(Path bam,Path index,Path fasta,Reference reference,List<Region> regions,Limits limits,Runnable check)throws IOException {
        for(var region:regions){Long length=reference.lengths.get(region.contig);if(length==null||region.end>length)throw new IOException("Requested region outside actual reference: "+region);}
        long count=0,mapped=0,characters=0;int maximum=0,lastRef=-1,lastPosition=-1;boolean unplaced=false;
        var all=digest("SHA-256");var queryHashes=new ArrayList<MessageDigest>();long[] queryCounts=new long[regions.size()];for(var region:regions)queryHashes.add(digest("SHA-256"));
        var groups=new LinkedHashMap<String,String>();var samples=new TreeSet<String>();
        try(var reader=open(bam,index,fasta)){
            if(!reader.hasIndex()||reader.getFileHeader().getSortOrder()!=SAMFileHeader.SortOrder.coordinate)throw new IOException("Actual indexed coordinate-sorted alignment required");
            var header=reader.getFileHeader();var seen=new HashSet<String>();
            for(var sequence:header.getSequenceDictionary().getSequences()){
                String name=sequence.getSequenceName();if(!seen.add(name)||!Objects.equals(reference.lengths.get(name),(long)sequence.getSequenceLength()))throw new IOException("BAM dictionary disagrees with actual reference sequence: "+name);
                String md5=sequence.getMd5();if(md5!=null&&!md5.equalsIgnoreCase(reference.upperCaseSequenceMd5.get(name)))throw new IOException("BAM M5 disagrees with actual uppercase reference sequence: "+name);
            }
            for(var region:regions)if(!seen.contains(region.contig))throw new IOException("Requested actual contig absent from BAM dictionary: "+region.contig);
            for(var rg:header.getReadGroups()){
                String id=rg.getId(),sample=rg.getSample();if(id==null||id.isEmpty()||sample==null||sample.isEmpty()||sample.chars().anyMatch(Character::isWhitespace)||groups.putIfAbsent(id,sample)!=null)throw new IOException("Actual unique RG IDs and literal SM sample identifiers required");samples.add(sample);
            }
            if(samples.isEmpty())throw new IOException("No actual RG/SM sample population in BAM");
            for(var record:reader){
                check.run();if(++count>limits.records)throw new IOException("Complete alignment record capacity exceeded");
                String sam=record.getSAMString();characters=Math.addExact(characters,sam.length());if(characters>limits.samCharacters||sam.length()>limits.lineCharacters)throw new IOException("Complete SAM field capacity exceeded");append(all,sam);
                maximum=Math.max(maximum,record.getReadLength());if(maximum>limits.readLength)throw new IOException("Read exceeds declared original-edition read-length bound; no trimming is performed");
                int ref=record.getReferenceIndex(),position=record.getAlignmentStart();if(ref<0)unplaced=true;else{if(unplaced||ref<lastRef||ref==lastRef&&position<lastPosition)throw new IOException("Actual alignment records are not coordinate sorted");lastRef=ref;lastPosition=position;}
                if(!record.getReadUnmappedFlag()){
                    mapped++;String rg=record.getStringAttribute("RG");if(!groups.containsKey(rg))throw new IOException("Mapped read has absent or unknown actual RG identity");
                    for(int i=0;i<regions.size();i++){var region=regions.get(i);if(record.getReferenceName().equals(region.contig)&&position<=region.end&&record.getAlignmentEnd()>=region.start){queryCounts[i]++;append(queryHashes.get(i),sam);}}
                }
            }
        }catch(RuntimeException failure){throw new IOException("Invalid original aligned-read stream: "+failure.getMessage(),failure);}
        var queries=new ArrayList<Query>();
        try(var reader=open(bam,index,fasta)){
            for(int i=0;i<regions.size();i++){
                var region=regions.get(i);var actual=digest("SHA-256");long n=0,chars=0;
                try(var iterator=reader.queryOverlapping(region.contig,region.start,region.end)){
                    while(iterator.hasNext()){check.run();var record=iterator.next();if(record.getReadUnmappedFlag())continue;if(++n>limits.records)throw new IOException("Indexed read population exceeds capacity");String sam=record.getSAMString();chars=Math.addExact(chars,sam.length());if(chars>limits.samCharacters||sam.length()>limits.lineCharacters)throw new IOException("Indexed SAM capacity exceeded");append(actual,sam);}
                }
                String expected=HexFormat.of().formatHex(queryHashes.get(i).digest());if(n!=queryCounts[i]||!expected.equals(HexFormat.of().formatHex(actual.digest())))throw new IOException("Actual alignment index query disagrees with complete sequential overlap population: "+region);
                queries.add(new Query(region.toString(),n,expected));
            }
        }catch(RuntimeException failure){throw new IOException("Invalid original alignment index: "+failure.getMessage(),failure);}
        return new Result(count,mapped,characters,maximum,new ArrayList<>(samples),groups,HexFormat.of().formatHex(all.digest()),queries);
    }
}
