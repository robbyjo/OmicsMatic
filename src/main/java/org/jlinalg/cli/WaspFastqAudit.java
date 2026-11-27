/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Complete generated FASTQ/name/quality census and original BWA record correspondence. */
final class WaspFastqAudit {
    private WaspFastqAudit() { }
    record Original(int start,int length,String quality,boolean paired) { }
    record Name(String original,int first,Integer second,int number,int total) { }
    record Record(String name,String sequence,String quality) { }
    record Report(Map<String,Object> audit,Map<String,Integer> expectedPrimaryRecords,List<String> warnings) { }
    private static final class Seen {final Set<Integer> numbers=new HashSet<>();int total;}

    static Report audit(Path candidates,Path single,Path first,Path second,WaspRequest.Spec spec,Runnable check)throws IOException {
        var originals=new HashMap<String,List<Original>>();long rows=0,characters=0;
        try(var bam=SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(candidates)) {
            for(var read:bam){check.run();require(++rows<=spec.records(),"Complete candidate BAM capacity exceeded");characters+=read.getReadLength();require(characters<=spec.nativeCharacters(),"Candidate profile character capacity exceeded");originals.computeIfAbsent(read.getReadName(),k->new ArrayList<>()).add(new Original(read.getAlignmentStart(),read.getReadLength(),read.getBaseQualityString(),read.getReadPairedFlag()));}
        }catch(RuntimeException e){throw new IOException("Invalid complete original to-remap BAM",e);}
        for(var entry:originals.entrySet()){var unit=entry.getValue();boolean paired=unit.get(0).paired();require(unit.size()==(paired?2:1)&&(spec.paired()||!paired),"Ambiguous original WASP candidate unit: "+entry.getKey());for(var read:unit)require(read.paired()==paired,"Mixed flags within candidate unit");}
        var seen=new HashMap<String,Seen>();var expected=new HashMap<String,Integer>();var files=new ArrayList<Object>();long[] decoded={0},generated={0};
        if(spec.paired()) {
            try(var one=new Fastq(first,spec,decoded,check);var two=new Fastq(second,spec,decoded,check)) {
                for(;;){Record a=one.next(),b=two.next();require((a==null)==(b==null),"Original paired FASTQ populations differ");if(a==null)break;require(a.name.equals(b.name),"Original paired FASTQ names/order differ");Name name=name(a.name);require(name.second!=null,"Paired FASTQ name lacks original paired coordinates");var unit=originals.get(name.original);require(unit!=null&&unit.size()==2,"Paired FASTQ lacks a unique original candidate pair");
                    require(name.first==Math.min(unit.get(0).start,unit.get(1).start)&&name.second==Math.max(unit.get(0).start,unit.get(1).start),"Generated FASTQ coordinates differ from original candidate BAM");
                    original(a,unit.get(0));original(b,unit.get(1));add(name,a.name,2,seen,expected,spec,generated);}
                files.add(one.summary());files.add(two.summary());
            }
        }
        try(var file=new Fastq(single,spec,decoded,check)) {
            for(Record record;(record=file.next())!=null;){Name name=name(record.name);var unit=originals.get(name.original);require(name.second==null&&unit!=null&&unit.size()==1&&!unit.get(0).paired,"Single FASTQ lacks a unique unpaired original candidate");require(name.first==unit.get(0).start,"Generated single FASTQ coordinate differs from original BAM");original(record,unit.get(0));add(name,record.name,1,seen,expected,spec,generated);}
            files.add(file.summary());
        }
        long zero=0,above=0;var counts=new TreeMap<String,Object>();
        for(var entry:originals.entrySet()) {
            Seen count=seen.get(entry.getKey());if(count==null){zero++;counts.put(entry.getKey(),0);continue;}
            require(count.numbers.size()==count.total,"Missing original alternative FASTQ number");counts.put(entry.getKey(),count.total);
            if(count.total>integer(spec.settings().get("max_sequences"),1,1000000))above++;
        }
        var warnings=new ArrayList<String>();
        if(zero>0)warnings.add("ORIGINAL SOURCE ZERO-ALTERNATIVE CANDIDATES: "+zero+" original read units were placed in to-remap BAM without generated FASTQ alternatives. Original filter will omit them; the adapter preserves and reports this source behavior without inventing reads.");
        if(above>0)warnings.add("ORIGINAL SOURCE SEQUENCE-LIMIT BOUNDARY: "+above+" units produced more alternatives than the nominal max_sequences value. All generated records remain counted; no truncation was applied.");
        warnings.add("Original WASP FASTQ writing preserves its original quality-string handling and orientation conventions, including unchanged quality order when the second paired sequence is reverse-complemented. Raw FASTQs and source warnings are retained; this is source behavior, not a corrected FASTQ writer.");
        return new Report(map("status","PASS_COMPLETE_ORIGINAL_GENERATED_FASTQ_CENSUS","files",files,"decoded_bytes",decoded[0],"generated_read_records",generated[0],"generated_name_units",expected.size(),"candidate_units",originals.size(),"zero_alternative_candidate_units",zero,"above_nominal_source_limit_units",above,"alternatives_per_original_unit",counts,"warnings",warnings),Map.copyOf(expected),List.copyOf(warnings));
    }
    static Map<String,Object> mapped(Path path,Report expected,WaspRequest.Spec spec,Runnable check)throws IOException {
        var counts=new HashMap<String,Integer>();long records=0;
        try(var bam=SamReaderFactory.makeDefault().enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS).validationStringency(ValidationStringency.STRICT).open(path)) {
            for(var read:bam){check.run();require(++records<=spec.records(),"Complete remapped BAM capacity exceeded");require(expected.expectedPrimaryRecords.containsKey(read.getReadName()),"BWA output has an unexpected generated FASTQ name");if(!read.isSecondaryOrSupplementary())counts.merge(read.getReadName(),1,Math::addExact);}
        }catch(RuntimeException e){throw new IOException("Invalid complete BWA remapping output",e);}
        require(counts.equals(expected.expectedPrimaryRecords),"Complete BWA primary alignment/unmapped-record population differs from every generated FASTQ unit");
        return map("status","PASS_COMPLETE_GENERATED_FASTQ_TO_BWA_RECORD_CORRESPONDENCE","alignment_records",records,"generated_units",counts.size(),"primary_records",counts.values().stream().mapToLong(Integer::longValue).sum());
    }
    private static void original(Record record,Original original)throws IOException {require(record.sequence.length()==original.length&&record.quality.equals(original.quality),"Original generated FASTQ length/quality differs from the source candidate BAM");}
    private static void add(Name name,String encoded,int mates,Map<String,Seen> seen,Map<String,Integer> expected,WaspRequest.Spec spec,long[] records)throws IOException {
        require(encoded.length()<=254,"Generated WASP name exceeds BWA/SAM QNAME capacity; no truncation or identifier repair admitted");Seen count=seen.computeIfAbsent(name.original,k->new Seen());
        require(name.total<=spec.records(),"Original FASTQ alternative total exceeds complete record capacity");if(count.total==0)count.total=name.total;require(count.total==name.total&&count.numbers.add(name.number),"Repeated or inconsistent original FASTQ alternative number/total");require(expected.put(encoded,mates)==null,"Repeated complete generated FASTQ name");records[0]+=mates;require(records[0]<=spec.records(),"Complete generated FASTQ record capacity exceeded");
    }
    private static Name name(String value)throws IOException {
        String[] fields=value.split("\\.",-1);require(fields.length>=4,"Malformed original WASP generated FASTQ name");String original=String.join(".",Arrays.copyOf(fields,fields.length-3));
        try{String[] pos=fields[fields.length-3].split("-",-1);require(pos.length==1||pos.length==2,"Malformed WASP FASTQ coordinates");int first=Integer.parseInt(pos[0]),number=Integer.parseInt(fields[fields.length-2]),total=Integer.parseInt(fields[fields.length-1]);Integer second=pos.length==2?Integer.valueOf(pos[1]):null;require(!original.isEmpty()&&first>0&&(second==null||second>0)&&number>=1&&number<=total,"Invalid original WASP FASTQ name values");return new Name(original,first,second,number,total);}catch(NumberFormatException e){throw new IOException("Invalid numeric original WASP FASTQ name",e);}
    }
    private static final class Fastq implements AutoCloseable {
        final Path path;final WaspRequest.Spec spec;final long[] decoded;final Runnable check;final MessageDigest digest;final InputStream stream;long records;
        Fastq(Path path,WaspRequest.Spec spec,long[] decoded,Runnable check)throws IOException {this.path=path;this.spec=spec;this.decoded=decoded;this.check=check;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}stream=new BufferedInputStream(new DigestInputStream(new GZIPInputStream(Files.newInputStream(path)),digest));}
        Record next()throws IOException {
            check.run();String header=line();if(header==null)return null;String sequence=line(),plus=line(),quality=line();require(sequence!=null&&plus!=null&&quality!=null&&header.startsWith("@")&&header.length()>1,"Incomplete original four-line FASTQ record");String name=header.substring(1);
            require(plus.equals("+"+name)&&sequence.length()==quality.length()&&!sequence.isEmpty()&&sequence.matches("[A-Z=]+")&&quality.chars().allMatch(c->c>=33&&c<=126),"Malformed original FASTQ separator, sequence or quality; no replacement/repair admitted");require(++records<=spec.records(),"Complete FASTQ record capacity exceeded");return new Record(name,sequence,quality);
        }
        String line()throws IOException{return WaspInputs.line(stream,spec.lineCharacters(),spec.decodedBytes(),decoded,check);}
        Map<String,Object> summary(){return map("file",path.getFileName().toString(),"records",records,"complete_decoded_SHA256",HexFormat.of().formatHex(digest.digest()));}
        @Override public void close()throws IOException {stream.close();}
    }
}
