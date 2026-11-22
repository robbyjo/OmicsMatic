/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.sequence.HomerPeakAnnotations;

/** Complete supplied-reference annotation admission and independent coordinate/TSS audit. */
final class HomerAnnotationData {
    private HomerAnnotationData() { }
    record Limits(long input,long workspace,long rss,int records,int segments,int line,int timeout) {
        static Limits read(Map<String,String> o)throws IOException{return new Limits(number(o,"maximum-input-bytes",268435456,1,1073741824),number(o,"workspace-bytes",536870912,1,1099511627776L),number(o,"maximum-upstream-memory-bytes",1073741824,1,1099511627776L),HomerCoreData.integer(o,"maximum-records",1000000,1,10000000),HomerCoreData.integer(o,"maximum-transcripts",1000000,1,10000000),HomerCoreData.integer(o,"maximum-line-characters",1048576,1,2097152),HomerCoreData.integer(o,"timeout",600,1,86400));}
    }
    record Peak(long ordinal,String alias,String sourceId,String contig,int start1,int end1,char strand,String score,String focus) { }
    record Data(List<Peak> peaks,HomerPeakAnnotations.Summary gtf,HomerPeakAnnotations.Index index,
                Map<Path,String> hashes,long duplicateIds,long plusAssignedPeaks,long missingContigs,long ties) {
        Data {peaks=List.copyOf(peaks);hashes=Map.copyOf(hashes);}
        void verify()throws IOException{for(var entry:hashes.entrySet())if(!FollowupSupport.hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Annotation source or staged input changed during analysis: "+entry.getKey());}
    }
    static Data stage(Map<String,String> o,Path work,Limits limits)throws Exception{
        var hashes=new LinkedHashMap<Path,String>();long input=0;
        for(String key:List.of("peaks","gtf","reference-lengths")){
            Path path=Path.of(FollowupSupport.required(o,key)).toAbsolutePath().normalize();HomerFasta.regular(path,limits.input);long size=Files.size(path);input=Math.addExact(input,size);if(input>limits.input)throw new IOException("Combined annotation input-byte capacity exceeded");
            String digest=FollowupSupport.hash(path);Path copy=work.resolve("input-"+key);Files.copy(path,copy);if(!digest.equals(FollowupSupport.hash(copy))||!digest.equals(FollowupSupport.hash(path)))throw new IOException("Annotation source changed while copied");hashes.put(path,digest);hashes.put(copy,digest);
        }
        if(Math.multiplyExact(input,2)>limits.workspace)throw new IOException("Annotation raw/staged input estimate exceeds workspace capacity");
        var lengths=new LinkedHashMap<String,Long>();
        try(var reader=HomerCoreAudit.reader(work.resolve("input-reference-lengths"))){
            for(String line;(line=next(reader,limits.line))!=null;){if(line.isBlank()||line.startsWith("#"))continue;String[] f=line.split("\t",-1);if(f.length!=2||f[0].isBlank()||f[0].chars().anyMatch(Character::isWhitespace)||lengths.size()>=1000000||lengths.putIfAbsent(f[0],number(f[1],1,1000000000))!=null)throw new IOException("Reference lengths require unique exact contig TAB positive bounded length rows");}
        }
        if(lengths.isEmpty())throw new IOException("Reference-length dictionary is empty");
        HomerPeakAnnotations.Summary gtf;
        try(var reader=HomerCoreAudit.reader(work.resolve("input-gtf"))){gtf=HomerPeakAnnotations.readGtf(reader,lengths,o.get("gtf-unknown-strand").equals("original-plus"),limits.records,limits.segments,limits.line);}
        Files.copy(work.resolve("input-gtf"),work.resolve("stage-genes.gtf"));hashes.put(work.resolve("stage-genes.gtf"),FollowupSupport.hash(work.resolve("stage-genes.gtf")));
        var index=new HomerPeakAnnotations.Index(gtf);var peaks=new ArrayList<Peak>();var seen=new HashSet<String>();long duplicates=0,dots=0,missing=0,ties=0;
        try(var reader=HomerCoreAudit.reader(work.resolve("input-peaks"));var staged=Files.newBufferedWriter(work.resolve("stage-peaks.pos"),StandardCharsets.UTF_8);var ledger=Files.newBufferedWriter(work.resolve("peaks.tsv"),StandardCharsets.UTF_8)){
            ledger.write("peak_ordinal\tstaged_alias\tsource_id_json\tcontig\tstart0\tend_exclusive\tstaged_strand\tsource_row_json\n");
            for(String line;(line=next(reader,limits.line))!=null;){
                if(line.isBlank()||line.startsWith("#"))continue;if(peaks.size()>=limits.records)throw new IOException("Annotation peak-record capacity exceeded");String[] f=line.split("\t",-1);
                boolean bed=o.get("peak-format").equals("bed6");if(bed?f.length!=6:f.length!=6&&f.length!=7)throw new IOException("Choose exactly BED6 or HOMER6/7 fields;no columns are silently discarded");
                String id=bed?f[3]:f[0],contig=bed?f[0]:f[1],direction=bed?f[5]:f[4],score=bed?f[4]:f[5],focus=f.length==7?f[6]:"NA";
                if(id.isBlank()||id.chars().anyMatch(Character::isISOControl)||contig.isBlank()||contig.chars().anyMatch(Character::isWhitespace))throw new IOException("Peak identities/contigs need nonempty control-free values");
                if(!seen.add(id)){duplicates++;if(o.get("duplicate-ids").equals("error"))throw new IOException("Duplicate peak ID;choose retain-ordinal explicitly to keep separate records");}
                Long length=lengths.get(contig);if(length==null)throw new IOException("Peak contig is absent from exact declared reference lengths: "+contig);
                long start=number(bed?f[1]:f[2],bed?0:1,1000000000),end=number(bed?f[2]:f[3],1,1000000000);if(bed)start++;
                if(end<start||end>length)throw new IOException("Peak interval is empty,reversed or outside the exact declared reference");
                if(!List.of("+","-",".").contains(direction))throw new IOException("Peak strand must be +,- or dot");
                if(direction.equals(".")){if(o.get("peak-unknown-strand").equals("error"))throw new IOException("Dot peak strand requires explicit original-plus permission");dots++;}
                finite(score);if(!focus.equals("NA"))finite(focus);char strand=direction.equals("-")?'-':'+';
                var nearest=index.nearest(contig,(int)start,(int)end);
                if(nearest.candidates().isEmpty()){missing++;if(o.get("missing-gtf-contig").equals("error"))throw new IOException("Peak contig has no selected GTF exon/TSS;choose retain explicitly for original NA annotation");}
                if(nearest.distinctAssignments()>1){ties++;if(o.get("tss-ties").equals("error"))throw new IOException("Equidistant transcript assignments require explicit retain-original TSS tie policy");}
                long ordinal=peaks.size()+1L;String alias=String.format(java.util.Locale.ROOT,"H%09d",ordinal);Peak peak=new Peak(ordinal,alias,id,contig,(int)start,(int)end,strand,score,focus);peaks.add(peak);
                staged.write(alias+"\t"+contig+"\t"+start+"\t"+end+"\t"+strand+"\t"+score+"\t"+focus+"\n");
                ledger.write(ordinal+"\t"+alias+"\t"+RegenieRuntime.json(id)+"\t"+contig+"\t"+(start-1)+"\t"+end+"\t"+strand+"\t"+RegenieRuntime.json(line)+"\n");
            }
        }
        if(peaks.isEmpty())throw new IOException("Annotation peak input is empty");hashes.put(work.resolve("stage-peaks.pos"),FollowupSupport.hash(work.resolve("stage-peaks.pos")));
        try(var writer=Files.newBufferedWriter(work.resolve("gtf-transcripts.tsv"),StandardCharsets.UTF_8)){
            writer.write("transcript_id_json\tgene_id_json\toriginal_metadata_gene_id_json\tcontig\tstrand\tsource_blocks\tstart1\tend1\ttss1\n");
            for(var t:gtf.transcripts())writer.write(RegenieRuntime.json(t.id())+"\t"+RegenieRuntime.json(t.gene())+"\t"+RegenieRuntime.json(t.metadataGeneId())+"\t"+t.contig()+"\t"+t.strand()+"\t"+t.sourceBlocks()+"\t"+t.start1()+"\t"+t.end1()+"\t"+t.tss1()+"\n");
        }
        HomerRuntime.bound(work,limits.workspace);return new Data(peaks,gtf,index,hashes,duplicates,dots,missing,ties);
    }
    static long audit(Data data,Path work,Limits limits)throws Exception{
        var peaks=new TreeMap<String,Peak>();data.peaks.forEach(p->peaks.put(p.alias,p));var seen=new HashSet<String>();long rows=0;
        try(var reader=HomerCoreAudit.reader(work.resolve("original.stdout"));var output=Files.newBufferedWriter(work.resolve("results.tsv"),StandardCharsets.UTF_8);var candidates=Files.newBufferedWriter(work.resolve("native-tss-candidates.tsv"),StandardCharsets.UTF_8)){
            String header=next(reader,limits.line);if(header==null)throw new IOException("Missing original annotation header");String[] labels=header.split("\t",-1);
            List<String> expected=List.of("Chr","Start","End","Strand","Peak Score","Focus Ratio/Region Size","Annotation","Detailed Annotation","Distance to TSS","Nearest PromoterID","Entrez ID","Nearest Unigene","Nearest Refseq","Nearest Ensembl","Gene Name","Gene Alias","Gene Description","Gene Type");
            if(labels.length!=19||!labels[0].startsWith("PeakID (cmd=annotatePeaks.pl ")||!expected.equals(List.of(labels).subList(1,19)))throw new IOException("Original annotation column schema differs");
            output.write("peak_ordinal\tsource_id_json\tstaged_alias\t"+String.join("\t",expected)+"\tnative_minimum_absolute_tss_distance\tnative_compatible_assignments\tnative_tss_audit\n");
            candidates.write("peak_ordinal\ttranscript_id_json\tgene_id_json\tsource_blocks\ttss1\tsigned_distance\tselected_compatible\n");
            for(String line;(line=next(reader,limits.line))!=null;){
                String[] f=line.split("\t",-1);if(f.length!=19||!peaks.containsKey(f[0])||!seen.add(f[0]))throw new IOException("Original annotation has malformed,unknown or duplicate peak rows");Peak p=peaks.get(f[0]);
                if(!List.of(p.contig,Integer.toString(p.start1),Integer.toString(p.end1),Character.toString(p.strand),p.score,p.focus).equals(List.of(f).subList(1,7)))throw new IOException("Original annotation changed peak coordinates,strand,score or focus metadata");
                var nearest=data.index.nearest(p.contig,p.start1,p.end1);boolean absent=nearest.candidates().isEmpty();String status;
                if(absent){if(!List.of(f[7],f[8],f[9],f[10]).equals(List.of("NA","NA","NA","NA")))throw new IOException("Unknown GTF contig received fabricated annotation/TSS");status="no-GTF-contig-retained-NA";}
                else{
                    long distance=signed(f[9]);if(!nearest.accepts(f[10],distance))throw new IOException("Original nearest-TSS ID/distance differs from complete native exact-contig candidates");
                    if(nearest.candidates().stream().noneMatch(c->c.transcript().id().equals(f[10])&&c.signedDistance()==distance&&c.transcript().metadataGeneId().equals(f[11])))throw new IOException("Original version-stripped transcript/gene metadata mapping differs from the supplied GTF");
                    if(f[7].isBlank()||f[8].isBlank()||f[7].equals("NA")||f[8].equals("NA"))throw new IOException("Original feature annotation is missing on a GTF-bearing contig");
                    status=nearest.distinctAssignments()>1?"compatible-original-tie-retained":"exact-nearest-assignment";
                    for(var c:nearest.candidates())candidates.write(p.ordinal+"\t"+RegenieRuntime.json(c.transcript().id())+"\t"+RegenieRuntime.json(c.transcript().gene())+"\t"+c.transcript().sourceBlocks()+"\t"+c.transcript().tss1()+"\t"+c.signedDistance()+"\t"+(c.transcript().id().equals(f[10])&&c.signedDistance()==distance)+"\n");
                }
                output.write(p.ordinal+"\t"+RegenieRuntime.json(p.sourceId)+"\t"+p.alias+"\t"+String.join("\t",List.of(f).subList(1,19))+"\t"+(absent?"NA":nearest.absoluteDistance())+"\t"+nearest.distinctAssignments()+"\t"+status+"\n");rows++;
            }
        }
        if(rows!=data.peaks.size())throw new IOException("Original annotation omitted supplied peaks;partial populations are not completed analyses");
        String stderr=Files.readString(work.resolve("original.stderr"),StandardCharsets.UTF_8);
        if(!stderr.contains("Done annotating peaks file")||stderr.contains("Can't ")||stderr.contains("Could not ")||stderr.contains("not found")||stderr.contains("uninitialized")||stderr.contains("Illegal ")||stderr.contains("ERROR"))throw new IOException("Original annotation did not finalize cleanly;inspect retained stderr");
        return rows;
    }
    private static String next(BufferedReader reader,int maximum)throws IOException{
        var text=new StringBuilder();int c;while((c=reader.read())!=-1&&c!='\n'){
            if(c=='\r'){int after=reader.read();if(after!='\n'&&after!=-1)throw new IOException("Embedded annotation carriage return");break;}
            if(text.length()>=maximum||c<32&&c!='\t')throw new IOException("Oversized/control-containing annotation line");text.append((char)c);
        }
        return c==-1&&text.isEmpty()?null:text.toString();
    }
    private static long signed(String text)throws IOException{try{return Long.parseLong(text);}catch(NumberFormatException failure){throw new IOException("Original TSS distance is not an exact signed integer",failure);}}
    private static long number(String text,long low,long high)throws IOException{long value=signed(text);if(value<low||value>high)throw new IOException("Annotation integer value outside declared bounds");return value;}
    private static long number(Map<String,String> o,String key,long fallback,long low,long high)throws IOException{return o.containsKey(key)?number(o.get(key),low,high):fallback;}
    private static void finite(String text)throws IOException{try{if(!Double.isFinite(Double.parseDouble(text)))throw new NumberFormatException();}catch(NumberFormatException failure){throw new IOException("Peak score/focus must be a finite number (or explicit NA focus)",failure);}}
}
