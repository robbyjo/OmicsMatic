/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Closed typed original annotation requests; no R code, implicit assemblies or input RDS. */
final class ChipSeekerAnnotationRequest {
    private ChipSeekerAnnotationRequest() { }
    static final Set<String> VERSIONS=Set.of("1.22.1","1.26.2","1.34.1","1.48.1");
    static final List<String> PRIORITY=List.of("Promoter","5UTR","3UTR","Exon","Intron","Downstream","Intergenic");
    private static final Set<String> ROOT_KEYS=Set.of("schema_version","mode","version","peaks","txdb","txdb_sha256","reference_build","coordinates","out","rscript","r_libraries","analyses","maximum_input_bytes","maximum_peak_file_bytes","maximum_peaks","maximum_features","maximum_output_bytes","maximum_json_bytes","maximum_native_operations","timeout_seconds");
    private static final Set<String> CASE_KEYS=Set.of("id","tss_region","level","annotation_priority","downstream_distance","add_flank_gene_info","flank_distance","same_strand","ignore_overlap","ignore_upstream","ignore_downstream","overlap","select_annotation_prefixes");
    record Analysis(String id,List<Long> tssRegion,String level,List<String> priority,int downstream,boolean addFlanks,int flankDistance,
                    boolean sameStrand,boolean ignoreOverlap,boolean ignoreUpstream,boolean ignoreDownstream,String overlap,List<String> selection) {
        Map<String,Object> json(){
            var m=new LinkedHashMap<String,Object>();m.put("id",id);m.put("tss_region",tssRegion);m.put("level",level);m.put("annotation_priority",priority);
            m.put("downstream_distance",downstream);m.put("add_flank_gene_info",addFlanks);m.put("flank_distance",flankDistance);m.put("same_strand",sameStrand);
            m.put("ignore_overlap",ignoreOverlap);m.put("ignore_upstream",ignoreUpstream);m.put("ignore_downstream",ignoreDownstream);m.put("overlap",overlap);
            if(selection!=null)m.put("select_annotation_prefixes",selection);return m;
        }
    }
    record Request(String version,Path peaks,Path txdb,String expectedTxdb,String build,Path out,Path rscript,List<Path> libraries,List<Analysis> analyses,
                   long inputBytes,int peakBytes,int maximumPeaks,int maximumFeatures,long outputBytes,int jsonBytes,long nativeOperations,int timeout) {
        Map<String,Object> parameters(){return Map.of("version",version,"analyses",analyses.stream().map(Analysis::json).toList(),"maximum_features",maximumFeatures);}
        Map<String,Object> effective(){
            var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("mode","ANNOTATE");m.put("version",version);
            m.put("peaks",peaks.toString());m.put("txdb",txdb.toString());if(expectedTxdb!=null)m.put("txdb_sha256",expectedTxdb);
            m.put("reference_build",build);m.put("coordinates","one-based-inclusive");m.put("out",out.toString());m.put("rscript",rscript.toString());
            m.put("r_libraries",libraries.stream().map(Path::toString).toList());m.put("analyses",analyses.stream().map(Analysis::json).toList());
            m.put("maximum_input_bytes",inputBytes);m.put("maximum_peak_file_bytes",peakBytes);m.put("maximum_peaks",maximumPeaks);m.put("maximum_features",maximumFeatures);
            m.put("maximum_output_bytes",outputBytes);m.put("maximum_json_bytes",jsonBytes);m.put("maximum_native_operations",nativeOperations);m.put("timeout_seconds",timeout);return m;
        }
    }
    static Request parse(Map<String,Object> raw,Path parent)throws IOException{
        allowed(raw,ROOT_KEYS);integer(raw.get("schema_version"),1,1);require(text(raw.get("mode")).equals("ANNOTATE"),"Require mode ANNOTATE");
        String version=text(raw.get("version"));require(VERSIONS.contains(version),"Choose exact ChIPseeker 1.22.1, 1.26.2, 1.34.1 or 1.48.1");
        require(text(raw.get("coordinates")).equals("one-based-inclusive"),"Declare one-based-inclusive coordinates; no implicit BED conversion");
        String build=label(raw.get("reference_build"),512);String expected=null;
        if(raw.containsKey("txdb_sha256")){expected=text(raw.get("txdb_sha256"));require(expected.matches("[a-f0-9]{64}"),"txdb_sha256 must be a lowercase SHA-256");}
        var libraries=new ArrayList<Path>();for(Object p:list(raw.get("r_libraries"))){require(libraries.size()<64,"Too many declared R libraries");libraries.add(resolve(parent,label(p,32768)));}
        require(!libraries.isEmpty(),"Declare private/reference r_libraries explicitly");
        List<?> supplied=raw.containsKey("analyses")?list(raw.get("analyses")):List.of(Map.of("id","annotation"));require(!supplied.isEmpty()&&supplied.size()<=128,"Require 1..128 annotation configurations");
        var analyses=new ArrayList<Analysis>();var ids=new HashSet<String>();
        for(Object value:supplied){
            var m=object(value);allowed(m,CASE_KEYS);String id=text(m.get("id"));require(id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")&&ids.add(id.toLowerCase(Locale.ROOT)),"Analysis IDs must be unique portable directory names, including case-folding");
            List<?> region=m.containsKey("tss_region")?list(m.get("tss_region")):List.of(-3000L,3000L);require(region.size()==2,"tss_region needs two exact integers");
            long a=integer(region.get(0),-1000000,1000000),b=integer(region.get(1),-1000000,1000000);require(a<=b,"Reversed promoter window");
            String level=m.containsKey("level")?text(m.get("level")):"transcript";require(Set.of("gene","transcript").contains(level),"level must be gene or transcript");
            List<String> priority=m.containsKey("annotation_priority")?strings(m.get("annotation_priority"),32,64):PRIORITY;
            require(!priority.isEmpty()&&priority.stream().allMatch(PRIORITY::contains),"Unsupported annotation priority name; original order/duplicates are preserved");
            String overlap=m.containsKey("overlap")?text(m.get("overlap")):"TSS";require(Set.of("TSS","all").contains(overlap),"overlap must be TSS or all");
            List<String> selection=m.containsKey("select_annotation_prefixes")?strings(m.get("select_annotation_prefixes"),32,512):null;
            require(selection==null||!selection.isEmpty(),"Selection needs explicit nonempty literal annotation prefixes; no enhancer criterion is inferred");
            analyses.add(new Analysis(id,List.of(a,b),level,priority,(int)number(m,"downstream_distance",3000,1,1000000),bool(m,"add_flank_gene_info",false),
                (int)number(m,"flank_distance",5000,0,1000000),bool(m,"same_strand",false),bool(m,"ignore_overlap",false),bool(m,"ignore_upstream",false),bool(m,"ignore_downstream",false),overlap,selection));
        }
        return new Request(version,resolve(parent,label(raw.get("peaks"),32768)),resolve(parent,label(raw.get("txdb"),32768)),expected,build,
            resolve(parent,label(raw.get("out"),32768)),resolve(parent,label(raw.get("rscript"),32768)),List.copyOf(libraries),List.copyOf(analyses),
            number(raw,"maximum_input_bytes",536870912L,1,17179869184L),(int)number(raw,"maximum_peak_file_bytes",67108864,1,268435456),
            (int)number(raw,"maximum_peaks",1000000,1,2000000),(int)number(raw,"maximum_features",1000000,1,2000000),
            number(raw,"maximum_output_bytes",268435456,1,4294967296L),(int)number(raw,"maximum_json_bytes",67108864,1,268435456),
            number(raw,"maximum_native_operations",50000000L,1,10000000000L),(int)number(raw,"timeout_seconds",600,1,86400));
    }
    static Map<String,Object> object(Object x)throws IOException{var raw=NextflowFiles.object(x);var m=new LinkedHashMap<String,Object>();for(var e:raw.entrySet())m.put(text(e.getKey()),e.getValue());return m;}
    static List<?> list(Object x)throws IOException{return NextflowFiles.list(x);}
    static String text(Object x)throws IOException{return NextflowFiles.text(x);}
    static long integer(Object x,long min,long max)throws IOException{return NextflowFiles.integer(x,min,max);}
    static long number(Map<String,Object> m,String key,long fallback,long min,long max)throws IOException{return m.containsKey(key)?integer(m.get(key),min,max):fallback;}
    static boolean bool(Map<String,Object> m,String key,boolean fallback)throws IOException{if(!m.containsKey(key))return fallback;require(m.get(key) instanceof Boolean,key+" must be boolean");return (Boolean)m.get(key);}
    static String label(Object x,int max)throws IOException{String s=text(x);require(!s.isEmpty()&&s.length()<=max&&s.codePoints().noneMatch(Character::isISOControl),"Empty/excessive label or path, or control character");return s;}
    static List<String> strings(Object x,int max,int width)throws IOException{var result=new ArrayList<String>();for(Object value:list(x)){require(result.size()<max,"Excessive string population");result.add(label(value,width));}return List.copyOf(result);}
    static Path resolve(Path parent,String s){Path p=Path.of(s);return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static void allowed(Map<String,Object> m,Set<String> keys)throws IOException{for(String key:m.keySet())require(keys.contains(key),"Unknown annotation request field: "+key);}
    static void require(boolean v,String s)throws IOException{if(!v)throw new IOException(s);}
}
