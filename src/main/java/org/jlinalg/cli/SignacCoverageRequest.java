/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed native coverage requests and byte-preserving input/source capture. */
final class SignacCoverageRequest {
    private SignacCoverageRequest() { }
    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","source_profile","genome_build","out","region","sources","cell_metadata","group_levels","scale_factor","window","downsample_rate","maximum_downsample","ymax","annotation","annotation_mode","title","plot_width","maximum_plot_height","maximum_input_bytes","maximum_output_bytes","maximum_signal_values","maximum_smoothing_operations","maximum_annotations","maximum_segments","maximum_groups","maximum_cells","maximum_fragment_records","maximum_decoded_bytes","maximum_contributions"));
        if(integer(raw,"schema_version",0,1,1)!=1||!"COVERAGE".equals(text(raw,"mode")))throw new IOException("Require schema1 mode COVERAGE");var r=new LinkedHashMap<String,Object>(raw);
        text(raw,"out");text(raw,"cell_metadata");identity(text(raw,"genome_build"));r.put("source_profile",choice(raw,"source_profile","",Set.of("Signac-1.9.0","Signac-1.14.0","Signac-1.17.1")));
        var region=object(raw.get("region"),"region");keys(region,Set.of("chromosome","start","end"));identity(text(region,"chromosome"));long start=integer(region,"start",0,1,536870912),end=integer(region,"end",0,start,536870912);if(end-start+1>2000000)throw new IOException("Coverage region width exceeds2million");
        var sources=list(raw.get("sources"),"sources");if(sources.isEmpty()||sources.size()>256)throw new IOException("Require1..256 explicit fragment sources");for(Object o:sources){var s=object(o,"source");keys(s,Set.of("fragments","map"));text(s,"fragments");if(s.containsKey("map"))text(s,"map");}
        if(raw.containsKey("group_levels")){var levels=strings(raw.get("group_levels"),"group_levels",1,512);if(new HashSet<>(levels).size()!=levels.size())throw new IOException("Duplicate group levels");for(String n:levels)identity(n);}
        if(raw.containsKey("scale_factor"))r.put("scale_factor",real(raw,"scale_factor",0,0,1e15,true));
        r.put("window",raw.containsKey("window")&&raw.get("window")==null?null:integer(raw,"window",100,1,2000000));r.put("downsample_rate",real(raw,"downsample_rate",0.1,0,1,true));r.put("maximum_downsample",integer(raw,"maximum_downsample",3000,0,2000000));
        String mode=choice(raw,"annotation_mode",raw.containsKey("annotation")?"gene":"none",Set.of("none","gene","transcript"));if(raw.containsKey("annotation")){text(raw,"annotation");if(mode.equals("none"))throw new IOException("Refuse unused annotation with mode none");}else if(!mode.equals("none"))throw new IOException("Gene/transcript models require explicit annotation exons");r.put("annotation_mode",mode);
        r.put("title",raw.containsKey("title")?text(raw,"title"):"Chromatin accessibility");identity(text(r,"title"));r.put("plot_width",integer(raw,"plot_width",1200,600,4000));r.put("maximum_plot_height",integer(raw,"maximum_plot_height",20000,300,20000));
        r.put("maximum_groups",integer(raw,"maximum_groups",128,1,512));r.put("maximum_cells",integer(raw,"maximum_cells",2000000,1,2000000));r.put("maximum_annotations",integer(raw,"maximum_annotations",100000,1,2000000));r.put("maximum_segments",integer(raw,"maximum_segments",2000000,1,10000000));
        for(var e:Map.of("maximum_input_bytes",1000000000L,"maximum_output_bytes",2000000000L,"maximum_signal_values",20000000L,"maximum_smoothing_operations",250000000L,"maximum_fragment_records",1000000000L,"maximum_decoded_bytes",250000000000L,"maximum_contributions",250000000L).entrySet())r.put(e.getKey(),integer(raw,e.getKey(),e.getValue(),1,e.getKey().equals("maximum_signal_values")?100000000:1000000000000L));
        try{options(r);}catch(IllegalArgumentException e){throw new IOException("Invalid native coverage options",e);}return r;
    }
    static long value(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
    static SignacCoverage.Options options(Map<String,Object> r){return new SignacCoverage.Options(r.get("window")==null?null:((Number)r.get("window")).intValue(),((Number)r.get("downsample_rate")).doubleValue(),((Number)r.get("maximum_downsample")).intValue(),r.get("ymax"),value(r,"maximum_signal_values"),value(r,"maximum_smoothing_operations"));}
    static SignacFragments.Region region(Map<String,Object> r)throws IOException{var p=object(r.get("region"),"region");return new SignacFragments.Region(text(p,"chromosome"),((Number)p.get("start")).intValue(),((Number)p.get("end")).intValue());}
    static Map<String,Object> captured(Map<String,Object> raw)throws IOException{var r=new LinkedHashMap<String,Object>(raw);r.put("out",".");r.put("cell_metadata","inputs/cells.tsv");if(raw.containsKey("annotation"))r.put("annotation","inputs/annotation.tsv");List<Object> sources=new ArrayList<>();int i=0;for(Object o:list(raw.get("sources"),"sources")){var s=new LinkedHashMap<>(object(o,"source"));s.put("fragments","inputs/s"+(++i)+".fragments");if(s.containsKey("map"))s.put("map","inputs/map"+i+".tsv");sources.add(s);}r.put("sources",sources);return r;}
    static Map<String,String> capture(Map<String,Object> raw,Map<String,Object> captured,Path parent,Path root,long cap)throws IOException{
        Files.createDirectory(root.resolve("inputs"));Map<String,String> pins=new TreeMap<>();long[] remaining={cap};
        for(String key:List.of("cell_metadata","annotation"))if(raw.containsKey(key))copy(resolve(parent,text(raw,key)),root,text(captured,key),remaining,pins);
        var a=list(raw.get("sources"),"sources");var b=list(captured.get("sources"),"sources");for(int i=0;i<a.size();i++){var original=object(a.get(i),"source");var next=object(b.get(i),"source");for(String key:List.of("fragments","map"))if(original.containsKey(key))copy(resolve(parent,text(original,key)),root,text(next,key),remaining,pins);}return pins;
    }
    private static void copy(Path input,Path root,String name,long[] remaining,Map<String,String> pins)throws IOException{
        if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS)||Files.size(input)>remaining[0])throw new IOException("Input unavailable, linked or beyond complete input capacity: "+input);String before=FollowupSupport.hash(input);long copied=0;
        try(var in=Files.newInputStream(input);var out=Files.newOutputStream(root.resolve(name),StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){check();if(n>remaining[0]-copied)throw new IOException("Complete captured input byte capacity exceeded");out.write(buffer,0,n);copied+=n;}}
        if(!before.equals(FollowupSupport.hash(input))||!before.equals(FollowupSupport.hash(root.resolve(name))))throw new IOException("Input changed during coverage capture");remaining[0]-=copied;pins.put(name,before);
    }
    static List<String[]> table(Path path,String header,int columns,int capacity)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular coverage metadata/annotation/map");List<String[]> rows=new ArrayList<>();try(var in=SeuratDifferentialInputs.reader(path)){if(!header.equals(SeuratDifferentialInputs.line(in)))throw new IOException("Require exact TSV header: "+header);String line;while((line=SeuratDifferentialInputs.line(in))!=null){check();if(rows.size()>=capacity)throw new IOException("Complete metadata/annotation/map row capacity exceeded");String[] f=line.split("\t",-1);if(f.length!=columns)throw new IOException("Wrong literal coverage TSV width");for(String x:f)identity(x);rows.add(f);}}return rows;
    }
    static void check(){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Native coverage interrupted");}
}
