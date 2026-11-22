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
import org.jlinalg.sequence.HomerNamedFeatures;
import org.jlinalg.sequence.HomerNamedTss;

/** Complete named-resource native feature, nearest-TSS and population audit. */
final class HomerNamedData {
    private HomerNamedData() { }
    record Limits(long input,long workspace,long rss,int records,int line,int timeout,long resourceRecords,int candidates,long operations) {
        static Limits read(Map<String,String> o)throws IOException{return new Limits(number(o,"maximum-input-bytes",268435456,1,1073741824),number(o,"workspace-bytes",1073741824,1,1099511627776L),number(o,"maximum-upstream-memory-bytes",8589934592L,1,1099511627776L),HomerCoreData.integer(o,"maximum-records",100000,1,1000000),HomerCoreData.integer(o,"maximum-line-characters",1048576,32,1048576),HomerCoreData.integer(o,"timeout",600,1,86400),number(o,"maximum-resource-records",20000000,1,100000000),HomerCoreData.integer(o,"maximum-candidate-rows",1000000,1,10000000),number(o,"maximum-operations",200000000,1,Long.MAX_VALUE));}
    }
    record Peak(long ordinal,String alias,String sourceId,HomerNamedFeatures.Query query,String score,String focus) { }
    record Data(List<Peak> peaks,Map<Path,String> hashes,HomerNamedFeatures.Summary basic,HomerNamedFeatures.Summary full,
                HomerNamedTss.Dataset tss,HomerNamedTss.Index index,long duplicateIds,long plusAssigned,long featureTies,long tssTies,long missingTss) {
        Data {peaks=List.copyOf(peaks);hashes=Map.copyOf(hashes);}
        void verify()throws IOException{for(var entry:hashes.entrySet())if(!FollowupSupport.hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Named source/staged input changed: "+entry.getKey());}
    }
    static Data stage(Map<String,String> o,Path work,Limits l,HomerNamedRuntime.Context runtime)throws Exception {
        long deadline=System.nanoTime()+Math.multiplyExact((long)l.timeout,1000000000L);HomerNamedFeatures.Check check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("Native named-resource audit timeout/interruption");runtime.markerCheck();};
        Path source=Path.of(FollowupSupport.required(o,"peaks")).toAbsolutePath().normalize();HomerFasta.regular(source,l.input);if(Math.multiplyExact(Files.size(source),2)>l.workspace)throw new IOException("Named raw/staged input workspace capacity exceeded");String digest=FollowupSupport.hash(source);Path copy=work.resolve("input-peaks");Files.copy(source,copy);if(!digest.equals(FollowupSupport.hash(source))||!digest.equals(FollowupSupport.hash(copy)))throw new IOException("Named peaks changed while copied");var hashes=new LinkedHashMap<Path,String>();hashes.put(source,digest);hashes.put(copy,digest);
        String genome=o.get("genome");Path dictionary=runtime.genomeFile(genome,"chrom.sizes");var lengths=new LinkedHashMap<String,Long>();try(var in=HomerCoreAudit.reader(dictionary)){
            for(String line;(line=next(in,l.line))!=null;){if(line.isBlank()||line.startsWith("#"))continue;var f=line.split("\t",-1);if(f.length!=2||f[0].isBlank()||f[0].chars().anyMatch(Character::isWhitespace)||lengths.putIfAbsent(f[0],number(f[1],1,1000000000))!=null)throw new IOException("Invalid exact named reference dictionary");}
        }
        if(lengths.isEmpty())throw new IOException("Empty exact named reference dictionary");Files.copy(dictionary,work.resolve("reference-lengths.tsv"));hashes.put(work.resolve("reference-lengths.tsv"),FollowupSupport.hash(dictionary));
        var peaks=new ArrayList<Peak>();var ids=new HashSet<String>();long duplicates=0,dots=0;
        try(var in=HomerCoreAudit.reader(copy);var staged=Files.newBufferedWriter(work.resolve("stage-peaks.pos"),StandardCharsets.UTF_8);var ledger=Files.newBufferedWriter(work.resolve("peaks.tsv"),StandardCharsets.UTF_8)){
            ledger.write("peak_ordinal\tstaged_alias\tsource_id_json\tcontig\tstart0\tend_exclusive\tstaged_strand\tsource_row_json\n");
            for(String line;(line=next(in,l.line))!=null;){
                if(line.isBlank()||line.startsWith("#"))continue;if(peaks.size()>=l.records)throw new IOException("Named peak-record capacity exceeded");var f=line.split("\t",-1);boolean bed=o.get("peak-format").equals("bed6");if(bed?f.length!=6:f.length!=6&&f.length!=7)throw new IOException("Require exactly BED6 or HOMER6/7 fields");
                String id=bed?f[3]:f[0],contig=bed?f[0]:f[1],direction=bed?f[5]:f[4],score=bed?f[4]:f[5],focus=f.length==7?f[6]:"NA";
                if(id.isBlank()||id.chars().anyMatch(Character::isISOControl)||contig.isBlank()||contig.chars().anyMatch(Character::isWhitespace))throw new IOException("Invalid named peak identity/contig");
                if(!ids.add(id)){duplicates++;if(o.get("duplicate-ids").equals("error"))throw new IOException("Duplicate peak IDs require explicit retain-ordinal policy");}
                Long length=lengths.get(contig);if(length==null)throw new IOException("Peak contig absent from pinned named reference: "+contig);long start=number(bed?f[1]:f[2],bed?0:1,1000000000),end=number(bed?f[2]:f[3],1,1000000000);if(bed)start++;
                if(end<start||end>length)throw new IOException("Peak is empty, reversed or outside pinned named reference");if(!List.of("+","-",".").contains(direction))throw new IOException("Peak strand must be +,- or dot");if(direction.equals(".")){if(o.get("peak-unknown-strand").equals("error"))throw new IOException("Dot peak strand requires explicit original-plus policy");dots++;}
                finite(score);if(!focus.equals("NA"))finite(focus);long ordinal=peaks.size()+1L;String alias=String.format(java.util.Locale.ROOT,"N%09d",ordinal);char strand=direction.equals("-")?'-':'+';var query=new HomerNamedFeatures.Query(alias,contig,(int)start,(int)end,strand);peaks.add(new Peak(ordinal,alias,id,query,score,focus));
                staged.write(alias+"\t"+contig+"\t"+start+"\t"+end+"\t"+strand+"\t"+score+"\t"+focus+"\n");ledger.write(ordinal+"\t"+alias+"\t"+RegenieRuntime.json(id)+"\t"+contig+"\t"+(start-1)+"\t"+end+"\t"+strand+"\t"+RegenieRuntime.json(line)+"\n");if((peaks.size()&4095)==0)check.run();
            }
        }
        if(peaks.isEmpty())throw new IOException("Empty named peak input");hashes.put(work.resolve("stage-peaks.pos"),FollowupSupport.hash(work.resolve("stage-peaks.pos")));var queries=peaks.stream().map(Peak::query).toList();var featureLimits=new HomerNamedFeatures.Limits(l.resourceRecords,l.records,l.candidates,100000,l.line,l.operations,o.get("resource-bounds").equals("retain-original"));
        HomerNamedFeatures.Summary basic,full;try(var in=HomerCoreAudit.reader(runtime.genomeFile(genome,genome+".basic.annotation"))){basic=HomerNamedFeatures.annotate(in,lengths,queries,featureLimits,check);}try(var in=HomerCoreAudit.reader(runtime.genomeFile(genome,genome+".full.annotation"))){full=HomerNamedFeatures.annotate(in,lengths,queries,featureLimits,check);}
        long featureTies=basic.geometryTiedQueries()+full.geometryTiedQueries();if(featureTies>0&&o.get("feature-ties").equals("error"))throw new IOException("Original feature geometry ties require explicit retain-original policy");
        HomerNamedTss.Dataset tss;try(var in=HomerCoreAudit.reader(runtime.genomeFile(genome,genome+".tss"))){tss=HomerNamedTss.read(in,lengths,1000000,l.line,o.get("tss-resource-anchors").equals("retain-original"),false,check);}var index=new HomerNamedTss.Index(tss);long ties=0,missing=0,totalCandidates=0;
        try(var ledger=Files.newBufferedWriter(work.resolve("resource-tss.tsv"),StandardCharsets.UTF_8)){
            ledger.write("source_ordinal\traw_id_json\toriginal_promoter_id_json\tcontig\twindow_start1\twindow_end1\tstrand\tanchor1\n");for(var t:tss.records())ledger.write(t.sourceOrdinal()+"\t"+RegenieRuntime.json(t.rawId())+"\t"+RegenieRuntime.json(t.originalPromoterId())+"\t"+t.contig()+"\t"+t.windowStart1()+"\t"+t.windowEnd1()+"\t"+t.strand()+"\t"+t.position1()+"\n");
        }
        for(var p:peaks){var near=index.nearest(p.query);totalCandidates=Math.addExact(totalCandidates,near.compatible().size());if(totalCandidates>l.candidates)throw new IOException("Native named TSS candidate capacity exceeded");if(near.distinctOriginalAssignments()>1){ties++;if(o.get("tss-ties").equals("error"))throw new IOException("Original TSS ties require explicit retain-original policy");}if(near.compatible().isEmpty()){missing++;if(o.get("missing-tss-contig").equals("error"))throw new IOException("No original TSS on peak contig; explicit retain-NA policy required");}}
        check.run();HomerRuntime.bound(work,l.workspace);return new Data(peaks,hashes,basic,full,tss,index,duplicates,dots,featureTies,ties,missing);
    }
    static long audit(Data data,Path work,Limits limits,boolean statistics)throws Exception {
        var peaks=new TreeMap<String,Peak>();var basic=new TreeMap<String,HomerNamedFeatures.Match>();var full=new TreeMap<String,HomerNamedFeatures.Match>();data.peaks.forEach(p->peaks.put(p.alias,p));data.basic.matches().forEach(m->basic.put(m.query().id(),m));data.full.matches().forEach(m->full.put(m.query().id(),m));var basicCounts=new TreeMap<String,Long>();var fullCounts=new TreeMap<String,Long>();var seen=new HashSet<String>();long rows=0;
        try(var in=HomerCoreAudit.reader(work.resolve("original.stdout"));var results=Files.newBufferedWriter(work.resolve("results.tsv"),StandardCharsets.UTF_8);var tssOut=Files.newBufferedWriter(work.resolve("native-tss-candidates.tsv"),StandardCharsets.UTF_8);var featureOut=Files.newBufferedWriter(work.resolve("native-feature-candidates.tsv"),StandardCharsets.UTF_8)){
            String header=next(in,limits.line);if(header==null)throw new IOException("Missing original named annotation header");var fields=header.split("\t",-1);var labels=List.of("Chr","Start","End","Strand","Peak Score","Focus Ratio/Region Size","Annotation","Detailed Annotation","Distance to TSS","Nearest PromoterID","Entrez ID","Nearest Unigene","Nearest Refseq","Nearest Ensembl","Gene Name","Gene Alias","Gene Description","Gene Type");
            if(fields.length!=19||!fields[0].startsWith("PeakID (cmd=annotatePeaks.pl ")||!labels.equals(List.of(fields).subList(1,19)))throw new IOException("Original named output schema differs");
            results.write("peak_ordinal\tsource_id_json\tstaged_alias\t"+String.join("\t",labels)+"\tnative_feature_audit\tnative_tss_audit\n");tssOut.write("peak_ordinal\traw_tss_id_json\toriginal_promoter_id_json\tcontig\tanchor1\tstrand\tsigned_distance\tselected_compatible\n");featureOut.write("peak_ordinal\tresource\tsource_ordinal\traw_name_json\toriginal_display_name_json\tcontig\tstart1\tend1\tstrand\tcategory\tconstruction_priority\tselected_compatible\n");
            for(String line;(line=next(in,limits.line))!=null;){
                var f=line.split("\t",-1);if(f.length!=19||!peaks.containsKey(f[0])||!seen.add(f[0]))throw new IOException("Original named rows malformed, unknown or duplicated");Peak p=peaks.get(f[0]);var q=p.query;
                if(!List.of(q.contig(),Integer.toString(q.start1()),Integer.toString(q.end1()),Character.toString(q.strand()),p.score,p.focus).equals(List.of(f).subList(1,7)))throw new IOException("Original named peak geometry/metadata changed");
                feature(p,basic.get(p.alias),"basic",f[7],basicCounts,featureOut);feature(p,full.get(p.alias),"full",f[8],fullCounts,featureOut);var near=data.index.nearest(q);String status;
                if(near.compatible().isEmpty()){if(!f[9].equals("NA")||!f[10].equals("NA"))throw new IOException("Missing named TSS contig received fabricated promoter/distance");status="retained-no-TSS-NA";}
                else{long distance=signed(f[9]);if(!near.acceptsOriginal(f[10],distance))throw new IOException("Original named nearest promoter/distance differs from complete native candidates");status=near.distinctOriginalAssignments()>1?"compatible-original-tie-retained":"exact-compatible-assignment";for(var c:near.compatible()){var t=c.source();tssOut.write(p.ordinal+"\t"+RegenieRuntime.json(t.rawId())+"\t"+RegenieRuntime.json(t.originalPromoterId())+"\t"+t.contig()+"\t"+t.position1()+"\t"+t.strand()+"\t"+c.signedDistance()+"\t"+(t.originalPromoterId().equals(f[10])&&c.signedDistance()==distance)+"\n");}}
                results.write(p.ordinal+"\t"+RegenieRuntime.json(p.sourceId)+"\t"+p.alias+"\t"+String.join("\t",List.of(f).subList(1,19))+"\tcompatible-source-geometry\t"+status+"\n");rows++;
            }
        }
        if(rows!=data.peaks.size())throw new IOException("Original named annotation omitted supplied peaks");String stderr=Files.readString(work.resolve("original.stderr"),StandardCharsets.UTF_8);
        if(!stderr.contains("Done annotating peaks file")||List.of("Can't ","Could not ","not found","uninitialized","Illegal ","ERROR","Segmentation fault").stream().anyMatch(stderr::contains))throw new IOException("Original named annotation did not finalize cleanly; inspect retained stderr");
        writePopulation(work.resolve("native-category-populations.tsv"),data.basic,data.full,basicCounts,fullCounts);if(statistics)auditStats(work.resolve("original-annotation-stats.tsv"),data.basic,data.full,basicCounts,fullCounts,limits.line);return rows;
    }
    private static void feature(Peak p,HomerNamedFeatures.Match match,String resource,String observed,Map<String,Long> counts,java.io.Writer out)throws IOException {
        if(match.compatible().isEmpty()){if(!observed.equals("NA"))throw new IOException("Original feature has no native source candidate");return;}
        if(!match.acceptsDisplayName(observed))throw new IOException("Original named feature differs from complete native candidates");var categories=new HashSet<String>();
        for(var f:match.compatible()){boolean selected=f.originalDisplayName().equals(observed);if(selected)categories.add(f.category());out.write(p.ordinal+"\t"+resource+"\t"+f.sourceOrdinal()+"\t"+RegenieRuntime.json(f.name())+"\t"+RegenieRuntime.json(f.originalDisplayName())+"\t"+f.contig()+"\t"+f.start1()+"\t"+f.end1()+"\t"+f.strand()+"\t"+f.category()+"\t"+f.constructionPriority()+"\t"+selected+"\n");}
        if(categories.size()!=1)throw new IOException("Observed tied feature display name has ambiguous source categories; cannot certify its category count");counts.merge(categories.iterator().next(),1L,Math::addExact);
    }
    private static String category(String raw){return switch(raw){case "P"->"Promoter";case "E"->"Exon";case "N"->"Intergenic";case "I"->"Intron";default->raw;};}
    private static void writePopulation(Path path,HomerNamedFeatures.Summary basic,HomerNamedFeatures.Summary full,Map<String,Long> bc,Map<String,Long> fc)throws IOException{
        try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){out.write("resource\tcategory\tassigned_peaks\toriginal_query_contig_inclusive_background_bp\n");for(int i=0;i<2;i++){var summary=i==0?basic:full;var counts=i==0?bc:fc;for(var e:new TreeMap<>(summary.queryContigBackgroundBases()).entrySet())out.write((i==0?"basic":"full")+"\t"+e.getKey()+"\t"+counts.getOrDefault(e.getKey(),0L)+"\t"+e.getValue()+"\n");}}
    }
    private static void auditStats(Path path,HomerNamedFeatures.Summary basic,HomerNamedFeatures.Summary full,Map<String,Long> bc,Map<String,Long> fc,int lineLimit)throws IOException{
        var blocks=new ArrayList<Map<String,List<Long>>>();Map<String,List<Long>> current=null;
        try(var in=HomerCoreAudit.reader(path)){for(String line;(line=next(in,lineLimit))!=null;){if(line.startsWith("Annotation\t")){current=new TreeMap<>();blocks.add(current);}else if(!line.isBlank()){var f=line.split("\t",-1);if(current==null||f.length<3||!f[1].matches("[0-9]+(?:\\.0+)?"))throw new IOException("Original category statistics malformed");long count=Long.parseLong(f[1].split("\\.",2)[0]);if(current.putIfAbsent(f[0],List.of(count,signed(f[2])))!=null)throw new IOException("Duplicate original statistics category");}}}
        if(blocks.size()!=2)throw new IOException("Require both complete original category statistics blocks");
        for(int i=0;i<2;i++){var summary=i==0?basic:full;var counts=i==0?bc:fc;var expected=new TreeMap<String,List<Long>>();for(var e:summary.queryContigBackgroundBases().entrySet())if(expected.putIfAbsent(category(e.getKey()),List.of(counts.getOrDefault(e.getKey(),0L),e.getValue()))!=null)throw new IOException("Original category display names collide");if(!expected.equals(blocks.get(i)))throw new IOException("Original category counts/background bp differ from complete native query-contig populations");}
    }
    private static String next(BufferedReader in,int maximum)throws IOException{var line=new StringBuilder();int c;while((c=in.read())!=-1&&c!='\n'){if(c=='\r'){int after=in.read();if(after!='\n'&&after!=-1)throw new IOException("Embedded named carriage return");break;}if(line.length()>=maximum||c<32&&c!='\t')throw new IOException("Oversized/control-containing named line");line.append((char)c);}return c==-1&&line.isEmpty()?null:line.toString();}
    private static long signed(String text)throws IOException{try{return Long.parseLong(text);}catch(NumberFormatException failure){throw new IOException("Expected exact integer: "+text,failure);}}
    private static long number(String text,long low,long high)throws IOException{long value=signed(text);if(value<low||value>high)throw new IOException("Named integer outside declared bounds");return value;}
    private static long number(Map<String,String> o,String key,long fallback,long low,long high)throws IOException{return o.containsKey(key)?number(o.get(key),low,high):fallback;}
    private static void finite(String text)throws IOException{try{if(!Double.isFinite(Double.parseDouble(text)))throw new NumberFormatException();}catch(NumberFormatException failure){throw new IOException("Named peak score/focus must be finite",failure);}}
}
