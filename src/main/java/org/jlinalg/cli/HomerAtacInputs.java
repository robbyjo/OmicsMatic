/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.sequence.HomerSamTags;

/** Immutable plain-SAM intake and complete bounded native tag-population ledgers. */
final class HomerAtacInputs {
    private HomerAtacInputs() { }
    record Limits(long input,long decoded,long workspace,long rss,long records,int line,int timeout,
                  int chromosomeTags,int regions,int coverageRows,int chunkKeys) {
        static Limits read(Map<String,String> o)throws IOException {
            var result=new Limits(number(o,"maximum-input-bytes",68719476736L,1,1099511627776L),number(o,"maximum-decoded-characters",100000000000L,1,1000000000000L),number(o,"workspace-bytes",137438953472L,1,1099511627776L),number(o,"maximum-upstream-memory-bytes",8589934592L,1,1099511627776L),number(o,"maximum-records",10000000,1,100000000),FollowupSupport.integer(o,"maximum-line-characters",65536,32,250000),FollowupSupport.integer(o,"timeout",600,1,86400),FollowupSupport.integer(o,"maximum-chromosome-tags",2000000,1,5000000),FollowupSupport.integer(o,"maximum-regions",1000000,1,1000000),FollowupSupport.integer(o,"maximum-coverage-rows",10000000,1,10000000),FollowupSupport.integer(o,"maximum-ledger-keys",100000,1,1000000));
            if((Math.multiplyExact(result.records,2)+result.chunkKeys-1)/result.chunkKeys>256)throw new IOException("Two-role tag audit needs at most256 disk chunks; raise maximum-ledger-keys with sufficient Java heap or lower maximum-records");return result;
        }
    }
    record Replicate(long ordinal,String id,String alias,Path source,Path staged,HomerSamTags.Summary summary,
                     Set<String> contigs,long duplicateFlagged,long supplementaryFlagged,long unpaired,
                     long improperPaired,long zeroTemplateLength) {
        Replicate {contigs=Set.copyOf(contigs);}
    }
    record Data(List<Replicate> replicates,Map<String,Long> reference,Map<Path,String> hashes,
                HomerTagLedger.Summary expected,Set<String> contigs,long totalRecords,double totalWeight,
                long identicalSourceFiles,Limits limits,Path work,long deadline,HomerAtacRuntime.Context runtime) {
        Data {replicates=List.copyOf(replicates);reference=Map.copyOf(reference);hashes=Map.copyOf(hashes);contigs=Set.copyOf(contigs);}
        void verify()throws IOException{check();for(var e:hashes.entrySet()){if(!FollowupSupport.hash(e.getKey()).equals(e.getValue()))throw new IOException("ATAC source/staged input changed: "+e.getKey());check();}}
        void check()throws IOException{if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("Native ATAC intake/audit timeout or interruption");runtime.markerCheck();HomerRuntime.bound(work,limits.workspace);}
    }
    private record Listed(String id,Path source) { }

    static Data stage(Map<String,String> o,Path work,Limits l,HomerAtacRuntime.Context runtime)throws Exception {
        long deadline=System.nanoTime()+Math.multiplyExact((long)l.timeout,1000000000L);long[] next={0};HomerTagLedger.Check check=()->{long now=System.nanoTime();if(Thread.currentThread().isInterrupted()||now>deadline)throw new IOException("Native ATAC intake/audit timeout or interruption");if(now>=next[0]){runtime.markerCheck();HomerRuntime.bound(work,l.workspace);next[0]=now+1000000000L;}};
        var hashes=new LinkedHashMap<Path,String>();long[] bytes={0};Path manifest=Path.of(FollowupSupport.required(o,"sam-list")).toAbsolutePath().normalize(),dictionary=Path.of(FollowupSupport.required(o,"reference-lengths")).toAbsolutePath().normalize();
        Path stagedList=copy(manifest,work.resolve("input-sam-list.tsv"),Math.min(l.input,8388608),l.input,bytes,hashes,check),stagedReference=copy(dictionary,work.resolve("input-reference-lengths.tsv"),Math.min(l.input,8388608),l.input,bytes,hashes,check);var reference=reference(stagedReference,l.line);var listed=list(stagedList,manifest.getParent(),l.line);var sourceHashes=new HashSet<String>();long identical=0;
        var ledger=new HomerTagLedger(work.resolve("native-expected-chunks"),l.chunkKeys,256,Math.multiplyExact(l.records,2),l.workspace,check);var replicates=new ArrayList<Replicate>();var allContigs=new TreeSet<String>();long[] totalRecords={0},decoded={0};double totalWeight=0;
        try(var rows=Files.newBufferedWriter(work.resolve("alignments.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);var reps=Files.newBufferedWriter(work.resolve("replicates.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            rows.write("replicate_alias\talignment_ordinal\tsource_line\tread_name\tflags\tdisposition\tcontig\tposition1\tstrand\tstored_length\tweight\n");reps.write("replicate_alias\treplicate_id_json\tsource_path_json\tstaged_SAM\tinput_sha256\talignments\taccepted_alignments\taccepted_weight\toriginal_all_primary_weight\tduplicate_flagged\tsupplementary_flagged\tunpaired\timproper_paired\tzero_template_length\n");
            for(int i=0;i<listed.size();i++) {
                var item=listed.get(i);String alias=String.format(java.util.Locale.ROOT,"R%04d",i+1);Path staged=copy(item.source,work.resolve("stage-"+alias+".sam"),l.input,l.input,bytes,hashes,check);String hash=hashes.get(staged);if(!sourceHashes.add(hash)){identical++;if(o.get("identical-replicates").equals("error"))throw new IOException("Identical SAM input bytes in different replicates; choose retain-explicit only if intended");}plain(staged);var samDictionary=header(staged,reference,l.line,check);
                var contigs=new TreeSet<String>();long[] flagged=new long[5];var options=new HomerSamTags.Options(HomerSamTags.Mapping.UNIQUE,HomerSamTags.Mates.BOTH,HomerSamTags.Length.PAIRED_TEMPLATE,HomerSamTags.SoftClips.ORIGINAL_DEFAULT,30,0,1000000000,false,false);HomerSamTags.Summary summary;
                try(var in=HomerCoreAudit.reader(staged)){
                    summary=HomerSamTags.read(in,reference,options,new HomerSamTags.Limits(l.records,l.decoded,l.line,100000),row->{
                        if(++totalRecords[0]>l.records)throw new IOException("Complete replicate alignment population exceeds maximum-records");if((totalRecords[0]&4095)==0)check.run();var tag=row.tag();
                        if(tag!=null){
                            if((row.flags()&1024)!=0){flagged[0]++;policy(o,"duplicate-alignments","retain-upstream","Accepted duplicate-flagged alignment requires explicit retention; perform intended upstream deduplication first");}
                            if((row.flags()&2048)!=0){flagged[1]++;policy(o,"supplementary-alignments","retain-upstream","Accepted supplementary alignment requires explicit retention");}
                            if((row.flags()&1)==0){flagged[2]++;policy(o,"unpaired-alignments","retain-upstream","Unpaired alignment in full-fragment mode requires explicit sequence-length fallback retention");}
                            else if((row.flags()&2)==0){flagged[3]++;policy(o,"improper-pairs","retain-upstream","Improper paired alignment requires explicit upstream-state retention");}
                            if(field(row.sourceText(),8).equals("0")){flagged[4]++;policy(o,"zero-template-length","use-sequence-length","Zero SAM TLEN requires explicit source sequence-length fallback");}
                            contigs.add(tag.contig());allContigs.add(tag.contig());long half=(long)(2*row.weight());ledger.add(new HomerTagLedger.Key(alias,tag.contig(),tag.position1(),tag.strand(),tag.storedLength()),half);ledger.add(new HomerTagLedger.Key("MERGED",tag.contig(),tag.position1(),tag.strand(),tag.storedLength()),half);
                        }
                        rows.write(alias+"\t"+row.ordinal()+"\t"+row.sourceLine()+"\t"+row.readName()+"\t"+row.flags()+"\t"+row.disposition()+"\t"+(tag==null?"NA\tNA\tNA\tNA\t0.0":tag.contig()+"\t"+tag.position1()+"\t"+tag.strand()+"\t"+tag.storedLength()+"\t"+row.weight())+"\n");
                    },check::run);
                }
                if(!samDictionary.containsAll(contigs))throw new IOException("Mapped eligible contig missing from SAM @SQ dictionary");decoded[0]=Math.addExact(decoded[0],summary.decodedCharacters());if(decoded[0]>l.decoded)throw new IOException("Complete replicate decoded-character capacity exceeded");if(summary.acceptedWeight()<1)throw new IOException("Eligible replicate weighted tag mass="+summary.acceptedWeight()+"; original5.1 requires at least1.0 and can exit0 without TagInfo below this value. Review filtered/half-weight mate population; no artificial rescaling is permitted");
                totalWeight+=summary.acceptedWeight();replicates.add(new Replicate(i+1,item.id,alias,item.source,staged,summary,contigs,flagged[0],flagged[1],flagged[2],flagged[3],flagged[4]));reps.write(alias+"\t"+RegenieRuntime.json(item.id)+"\t"+RegenieRuntime.json(item.source.toString())+"\t"+staged.getFileName()+"\t"+hash+"\t"+summary.alignmentRecords()+"\t"+summary.acceptedRecords()+"\t"+summary.acceptedWeight()+"\t"+summary.originalAllPrimaryWeight()+"\t"+flagged[0]+"\t"+flagged[1]+"\t"+flagged[2]+"\t"+flagged[3]+"\t"+flagged[4]+"\n");rows.flush();reps.flush();check.run();
            }
        }
        var expected=ledger.finish(work.resolve("native-expected-tags.tsv"));if(expected.roleHalfUnits().getOrDefault("MERGED",0L)!=2*totalWeight)throw new IOException("Native complete merged tag population counter mismatch");Path sizes=work.resolve("stage-chrom.sizes");try(var out=Files.newBufferedWriter(sizes,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){for(var e:new TreeMap<>(reference).entrySet())out.write(e.getKey()+"\t"+e.getValue()+"\n");}hashes.put(sizes,FollowupSupport.hash(sizes));hashes.put(work.resolve("native-expected-tags.tsv"),expected.sha256());var data=new Data(replicates,reference,hashes,expected,allContigs,totalRecords[0],totalWeight,identical,l,work,deadline,runtime);data.verify();return data;
    }
    private static void policy(Map<String,String> o,String key,String retain,String message)throws IOException{if(!retain.equals(o.get(key)))throw new IOException(message+"; review --"+key);}
    private static String field(String line,int index)throws IOException{int start=0;for(int i=0;i<index;i++){start=line.indexOf('\t',start);if(start<0)throw new IOException("Missing staged SAM field");start++;}int end=line.indexOf('\t',start);return end<0?line.substring(start):line.substring(start,end);}
    private static Path copy(Path source,Path target,long maximum,long totalMaximum,long[] total,Map<Path,String> hashes,HomerTagLedger.Check check)throws IOException {
        HomerFasta.regular(source,maximum);String before=FollowupSupport.hash(source);long length=0;try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){byte[] block=new byte[1048576];for(int n;(n=in.read(block))!=-1;){length=Math.addExact(length,n);total[0]=Math.addExact(total[0],n);if(length>maximum||total[0]>totalMaximum)throw new IOException("Complete ATAC source-byte capacity exceeded");out.write(block,0,n);check.run();}}
        if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(target)))throw new IOException("ATAC input changed during immutable copy");hashes.put(source,before);hashes.put(target,before);return target;
    }
    private static Map<String,Long> reference(Path path,int line)throws IOException {
        var result=new TreeMap<String,Long>();try(var in=HomerCoreAudit.reader(path)){for(String text;(text=HomerCoreAudit.next(in,line))!=null;){var f=text.split("\t",-1);if(f.length!=2||!f[0].matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")||result.size()>=100000||result.putIfAbsent(f[0],number(f[1],1,1000000000))!=null)throw new IOException("Require exact unique safe contig and positive length TSV; no header,comments or inferred aliases");}}if(result.isEmpty())throw new IOException("Empty exact reference dictionary");return result;
    }
    private static List<Listed> list(Path copy,Path parent,int line)throws IOException {
        var result=new ArrayList<Listed>();var seen=new HashSet<String>();try(var in=HomerCoreAudit.reader(copy)){if(!"replicate_id\tsam_path".equals(HomerCoreAudit.next(in,line)))throw new IOException("SAM list header must be replicate_id<TAB>sam_path");for(String text;(text=HomerCoreAudit.next(in,line))!=null;){var f=text.split("\t",-1);if(f.length!=2||f[0].isBlank()||f[0].length()>256||f[0].chars().anyMatch(Character::isISOControl)||f[1].isBlank()||!seen.add(f[0])||result.size()>=32)throw new IOException("Require1to32 unique replicate IDs and local SAM paths");result.add(new Listed(f[0],parent.resolve(f[1]).toAbsolutePath().normalize()));}}if(result.isEmpty())throw new IOException("SAM replicate list is empty");return result;
    }
    private static Set<String> header(Path path,Map<String,Long> reference,int line,HomerTagLedger.Check check)throws IOException {
        var seen=new HashSet<String>();int headers=0;try(var in=HomerCoreAudit.reader(path)){for(String text;(text=HomerCoreAudit.next(in,line))!=null&&text.startsWith("@");){if(++headers>1000000)throw new IOException("SAM header capacity exceeded");if((headers&4095)==0)check.run();if(!text.startsWith("@SQ\t"))continue;String name=null,length=null;for(String field:text.split("\t",-1)){if(field.startsWith("SN:")){if(name!=null)throw new IOException("Duplicate SAM @SQ name field");name=field.substring(3);}if(field.startsWith("LN:")){if(length!=null)throw new IOException("Duplicate SAM @SQ length field");length=field.substring(3);}}if(name==null||length==null||!seen.add(name)||!reference.containsKey(name)||reference.get(name)!=number(length,1,1000000000))throw new IOException("SAM @SQ dictionary differs from exact supplied reference");}}return seen;
    }
    private static void plain(Path path)throws IOException {try(var in=Files.newInputStream(path)){byte[] first=in.readNBytes(4);if(first.length>1&&(first[0]&255)==31&&(first[1]&255)==139||java.util.Arrays.equals(first,new byte[]{'B','A','M',1})||java.util.Arrays.equals(first,new byte[]{'C','R','A','M'}))throw new HomerRuntime.Unavailable("This ATAC profile accepts plain UTF8 SAM. Required action: prepare plain SAM using a permitted original SAMtools installation for BAM/CRAM, or decompress permitted gzip SAM, preserve both inputs/provenance and rerun. This profile does not perform SAM export, implicit conversion or partial parsing");}}
    private static long number(Map<String,String> o,String key,long fallback,long minimum,long maximum)throws IOException{return number(o.getOrDefault(key,Long.toString(fallback)),minimum,maximum);}
    private static long number(String text,long minimum,long maximum)throws IOException{try{long value=Long.parseLong(text);if(value<minimum||value>maximum)throw new NumberFormatException();return value;}catch(NumberFormatException failure){throw new IOException("ATAC integer outside explicit bounds: "+text,failure);}}
}
