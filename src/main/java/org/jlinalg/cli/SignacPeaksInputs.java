/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SignacPeaksContracts.value;

/** Exact captured source populations and original Linux grouping before MACS. */
final class SignacPeaksInputs {
    private SignacPeaksInputs() { }
    record Job(String id,String sourceName,List<String> originalLabels,List<String> inputs,long records) {
        Job {originalLabels=List.copyOf(originalLabels);inputs=List.copyOf(inputs);}
    }
    record Prepared(List<Job> jobs,List<String> warnings,Map<String,Object> summary) {
        Prepared{jobs=List.copyOf(jobs);warnings=List.copyOf(warnings);}
    }
    static Map<String,Object> capturedRequest(Map<String,Object> original)throws IOException {
        var captured=new LinkedHashMap<String,Object>(original);captured.put("out",".");List<Map<String,Object>> sources=new ArrayList<>();int index=0;
        for(Object raw:list(original.get("sources"),"sources")){var s=new LinkedHashMap<>(object(raw,"source"));s.put("fragments","inputs/s"+(++index)+".fragments");if(s.containsKey("map"))s.put("map","inputs/map"+index+".tsv");sources.add(s);}captured.put("sources",sources);if(original.containsKey("cell_groups"))captured.put("cell_groups","inputs/groups.tsv");return captured;
    }
    static Map<String,String> capture(Map<String,Object> original,Map<String,Object> captured,Path parent,Path root,long cap)throws IOException {
        Files.createDirectory(root.resolve("inputs"));Map<String,String> pins=new TreeMap<>();var originals=list(original.get("sources"),"sources");var copies=list(captured.get("sources"),"sources");
        for(int i=0;i<originals.size();i++){var a=object(originals.get(i),"source");var b=object(copies.get(i),"source");for(String key:List.of("fragments","map"))if(a.containsKey(key)){String name=text(b,key);cap-=copy(resolve(parent,text(a,key)),root.resolve(name),cap);pins.put(name,FollowupSupport.hash(root.resolve(name)));}}
        if(original.containsKey("cell_groups")){String name=text(captured,"cell_groups");copy(resolve(parent,text(original,"cell_groups")),root.resolve(name),cap);pins.put(name,FollowupSupport.hash(root.resolve(name)));}return pins;
    }
    private static long copy(Path input,Path output,long cap)throws IOException {
        if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)||Files.size(input)>cap)throw new IOException("Input unavailable, linked or beyond maximum_input_bytes: "+input);String before=FollowupSupport.hash(input);long total=0;
        try(var in=Files.newInputStream(input);var out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){check();if(n>cap-total)throw new IOException("Captured inputs exceeded byte capacity");out.write(buffer,0,n);total+=n;}}
        if(!before.equals(FollowupSupport.hash(input))||!before.equals(FollowupSupport.hash(output)))throw new IOException("Input changed during capture");return total;
    }
    static Prepared prepare(Path root,Map<String,Object> r,boolean compare)throws IOException {
        Path temporary=compare?Files.createTempDirectory("omicsmatic-peaks-verify-"):null;Path destination=compare?temporary.resolve("prepared"):root.resolve("prepared");
        try {
            List<Job> jobs=new ArrayList<>();List<String> warnings=new ArrayList<>();List<Object> scans=new ArrayList<>();List<SignacFragments.Source> sources=new ArrayList<>();var rawSources=list(r.get("sources"),"sources");
            for(Object raw:rawSources){var s=object(raw,"source");List<SignacFragments.Cell> map=null;if(s.containsKey("map")){map=new ArrayList<>();for(String[] row:table(root.resolve(text(s,"map")),"cell\tbarcode",2,(int)value(r,"maximum_cells")))map.add(new SignacFragments.Cell(row[0],row[1]));}sources.add(new SignacFragments.Source(root.resolve(text(s,"fragments")),map));}
            if(r.get("mode").equals("GROUPED")) {
                List<SignacPeakPreparation.CellGroup> cells=new ArrayList<>();for(String[] row:table(root.resolve(text(r,"cell_groups")),"cell\tgroup",2,(int)value(r,"maximum_cells")))cells.add(new SignacPeakPreparation.CellGroup(row[0],row[1]));
                var result=SignacPeakPreparation.split(sources,cells,destination,new SignacPeakPreparation.Limits((int)value(r,"maximum_cells"),(int)value(r,"maximum_groups"),256,value(r,"maximum_fragment_records"),value(r,"maximum_decoded_bytes"),value(r,"maximum_output_bytes")),()->Thread.currentThread().isInterrupted());
                int index=0;for(var g:result.groups()){String id="g"+(++index);jobs.add(new Job(id,g.sourceName(),g.originalLabels(),List.of("prepared/"+id+".bed"),g.records()));}for(var scan:result.scans())scans.add(scan(scan));for(var warning:result.warnings())warnings.add(warning.code()+": "+warning.message()+" Required action: "+warning.requiredAction());
            } else {
                Files.createDirectory(destination);List<String> paths=new ArrayList<>();long records=0;for(Object raw:rawSources){String path=text(object(raw,"source"),"fragments");var scan=SignacPeakOutput.validateFragments(root.resolve(path),value(r,"maximum_fragment_records"),value(r,"maximum_decoded_bytes"),()->Thread.currentThread().isInterrupted());scans.add(scan(scan));records=Math.addExact(records,scan.records());paths.add(path);}
                String original=text(r,"name"),name=SignacPeakPreparation.linuxSourceName(original);jobs.add(new Job("g1",name,List.of(original),paths,records));warnings.add("POOLED_ALL_FRAGMENT_ROWS: all records in every supplied source are used, including cells outside any external object/QC selection. Supply a declared GROUPED cell population when filtering is intended; no barcode restriction is inferred.");if(!name.equals(original))warnings.add("SOURCE_NAME_SUBSTITUTION: Linux Signac replaces spaces/slashes by underscores: "+original+" -> "+name+". Original identity remains recorded.");
            }
            warnings.add("SOURCE_FORMAT: "+r.get("format")+" is explicit. Five-column BED uses forward-strand tag starts and declared extension/shift; BEDPE uses full fragment intervals. Read-support values do not multiply records; MACS duplicate policy is separate.");
            warnings.add("INDEXED_FILE_TRANSPORT: safe g# output prefixes avoid shell/filename name handling; source-normalized group/peak names are restored explicitly. Review original_labels and source_name in prepared-summary.json, including source label collisions.");
            Map<String,Object> summary=new LinkedHashMap<>();summary.put("scans",scans);summary.put("jobs",jobs.stream().map(j->Map.of("id",j.id,"source_name",j.sourceName,"original_labels",j.originalLabels,"inputs",j.inputs,"records",j.records)).toList());summary.put("warnings",warnings);
            if(compare){if(!same(summary,read(root.resolve("prepared-summary.json"),64_000_000)))throw new IOException("Native peak preparation metadata/warnings differ");if(r.get("mode").equals("GROUPED")){for(Job j:jobs)if(!FollowupSupport.hash(destination.resolve(j.id+".bed")).equals(FollowupSupport.hash(root.resolve("prepared/"+j.id+".bed"))))throw new IOException("Native split bytes differ from captured inputs");}}
            else json(root.resolve("prepared-summary.json"),summary);
            return new Prepared(jobs,warnings,summary);
        } finally {if(temporary!=null)try(var paths=Files.walk(temporary)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList()){if(!p.startsWith(temporary))throw new IOException("Temporary cleanup escaped owned directory");Files.delete(p);}}}
    }
    private static Map<String,Object> scan(SignacFragments.Scan s){return Map.of("records",s.records(),"decoded_bytes",s.decodedBytes(),"compressed",s.compressed(),"prefix_only",s.prefixOnly(),"chromosomes",s.chromosomes());}
    private static List<String[]> table(Path file,String header,int columns,int limit)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular metadata/map file");List<String[]> result=new ArrayList<>();
        try(var in=SeuratDifferentialInputs.reader(file)){if(!header.equals(SeuratDifferentialInputs.line(in)))throw new IOException("Require exact metadata/map header: "+header);String line;while((line=SeuratDifferentialInputs.line(in))!=null){check();if(result.size()>=limit)throw new IOException("Metadata/map row capacity exceeded");String[] f=line.split("\t",-1);if(f.length!=columns)throw new IOException("Wrong metadata/map width");for(String v:f)identity(v);result.add(f);}}
        if(result.isEmpty())throw new IOException("Require nonempty explicit metadata/map rows");return result;
    }
    static void check(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Peak preparation interrupted");}
}
