/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit original input formats, source model units and resource capacities. */
final class RfmixRequest {
    private RfmixRequest() { }
    record Genotypes(Path path,String format,Path index) { }
    record Spec(Map<String,Object> value,String profile,Path runtime,Genotypes query,Genotypes reference,
                Path sampleMap,boolean compressedMap,Path geneticMap,Map<?,?> settings,
                long inputBytes,long outputBytes,int files,long wallMillis,int variants,int samples,int populations,
                long cells,int lineCharacters,long decodedBytes,NextflowProcess.Limits process) {
        String chromosome()throws IOException{return text(settings.get("chromosome"));}
        double crfSpacing()throws IOException{return real(object(settings.get("crf_spacing")).get("value"),0,Double.MAX_VALUE,false);}
        int em()throws IOException{return (int)integer(settings.get("em_iterations"),0,Integer.MAX_VALUE);}
        boolean reanalyze()throws IOException{return bool(settings.get("reanalyze_reference"));}
        boolean autoWeight()throws IOException{return text(object(settings.get("crf_weight")).get("mode")).equals("calibrate");}
        double missing()throws IOException{return real(settings.get("max_missing"),0,1,true);}
        int[] range()throws IOException {
            if(settings.get("range")==null)return new int[]{0,Integer.MAX_VALUE};
            var range=object(settings.get("range"));return new int[]{(int)(real(range.get("start_mbp"),0,2147.483647,true)*1e6),(int)(real(range.get("end_mbp"),0,2147.483647,true)*1e6)};
        }
        List<String> arguments()throws IOException {
            var args=new ArrayList<String>();
            args.add("--chromosome="+chromosome());
            for(var row:new TreeMap<>(Map.of("rf_window","rf-window-size","crf_spacing","crf-spacing")).entrySet())args.add("--"+row.getValue()+"="+number(object(settings.get(row.getKey())).get("value")));
            var weight=object(settings.get("crf_weight"));args.add("--crf-weight="+(autoWeight()?"-1":number(weight.get("value"))));
            for(var row:new TreeMap<>(Map.of("generations","generations","em_iterations","em-iterations","trees","trees","node_size","node-size","max_missing","max-missing","bootstrap_mode","bootstrap-mode","min_snps","rf-minimum-snps","threads","n-threads","seed","random-seed")).entrySet())args.add("--"+row.getValue()+"="+number(settings.get(row.getKey())));
            if(reanalyze())args.add("--reanalyze-reference");
            if(settings.get("range")!=null)args.add(RfmixRange.argument(object(settings.get("range"))));
            return List.copyOf(args);
        }
    }
    static String scalar(Object value)throws IOException {
        String text=text(value);require(!text.isBlank()&&text.length()<=32768&&text.chars().noneMatch(c->c<32||c==127),"Explicit nonempty text without controls required");return text;
    }
    static Path absolute(Object value)throws IOException {Path path=Path.of(scalar(value));require(path.isAbsolute()&&path.normalize().equals(path),"Require canonical absolute RFMix paths");return path;}
    static boolean bool(Object value)throws IOException {require(value instanceof Boolean,"Explicit Boolean required");return(Boolean)value;}
    static double real(Object value,double low,double high,boolean inclusiveLow)throws IOException {
        require(value instanceof Number,"Explicit numeric RFMix value required");double number=((Number)value).doubleValue();require(Double.isFinite(number)&&(inclusiveLow?number>=low:number>low)&&number<=high,"RFMix numeric parameter outside its finite source domain");return number;
    }
    static String number(Object value)throws IOException {require(value instanceof Number,"Explicit numeric token required");double n=((Number)value).doubleValue();require(Double.isFinite(n),"Nonfinite source parameter");return value.toString();}
    private static Genotypes genotypes(Object raw)throws IOException {
        var value=object(raw);keys(value,"path","format","index");String format=text(value.get("format"));require(Set.of("vcf","vcf-gzip","bcf").contains(format),"Declare vcf, vcf-gzip or bcf input format");Path index=value.get("index")==null?null:absolute(value.get("index"));
        if(format.equals("bcf")&&index==null)throw new RfmixRuntime.Unavailable("BCF requires its existing permitted CSI index; supply an explicit index path or retain this analysis skipped");
        require(format.equals("bcf")||index==null,"Only BCF uses an explicit CSI index");return new Genotypes(absolute(value.get("path")),format,index);
    }
    private static void window(Object raw,boolean rf)throws IOException {
        var value=object(raw);keys(value,"unit","value");String unit=text(value.get("unit"));double n=real(value.get("value"),0,Integer.MAX_VALUE,false);
        require(Set.of("snps","cM").contains(unit),"Declare source SNP-count or cM units");
        if(unit.equals("snps"))require(n==Math.rint(n)&&n>=(rf?2:1),"Original RF SNP count must be an integer >=2; CRF SNP count an integer >=1");
        else require(n<(rf?2:1),"Original source interprets RF values >=2 and CRF values >=1 as SNP counts; the declared cM value cannot be represented");
    }
    static Spec read(Path path)throws IOException{return parse(json(path,16777216));}
    static Spec parse(Object raw)throws IOException {
        var value=object(raw);keys(value,"schema_version","profile","runtime","preparation","query","reference","sample_map","genetic_map","settings","capacity","process");integer(value.get("schema_version"),1,1);scalar(value.get("preparation"));String profile=scalar(value.get("profile"));RfmixRuntime.profile(profile);
        var settings=object(value.get("settings"));keys(settings,"chromosome","rf_window","crf_spacing","crf_weight","generations","em_iterations","reanalyze_reference","trees","node_size","max_missing","bootstrap_mode","min_snps","range","threads","seed");String chromosome=scalar(settings.get("chromosome"));require(chromosome.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,199}"),"Declare a literal supported chromosome identifier without renaming");window(settings.get("rf_window"),true);window(settings.get("crf_spacing"),false);
        var weight=object(settings.get("crf_weight"));keys(weight,"mode","value");String mode=text(weight.get("mode"));require(Set.of("fixed","calibrate").contains(mode),"Select fixed or original internal-calibration CRF weight");if(mode.equals("fixed"))real(weight.get("value"),0,Double.MAX_VALUE,false);else require(weight.get("value")==null,"Calibration has no ignored fixed weight");
        real(settings.get("generations"),0,Double.MAX_VALUE,false);integer(settings.get("em_iterations"),0,Integer.MAX_VALUE);bool(settings.get("reanalyze_reference"));integer(settings.get("trees"),10,Integer.MAX_VALUE);integer(settings.get("node_size"),2,Integer.MAX_VALUE);real(settings.get("max_missing"),0,1,true);integer(settings.get("bootstrap_mode"),0,2);integer(settings.get("min_snps"),1,Integer.MAX_VALUE);long threads=integer(settings.get("threads"),1,65536);integer(settings.get("seed"),Integer.MIN_VALUE,Integer.MAX_VALUE);
        if(settings.get("range")!=null){var range=object(settings.get("range"));keys(range,"start_mbp","end_mbp");double start=real(range.get("start_mbp"),0,2147.483647,true),end=real(range.get("end_mbp"),0,2147.483647,true);require(start<=end&&start*1e6<=Integer.MAX_VALUE&&end*1e6<=Integer.MAX_VALUE,"Source Mbp-to-int range exceeds ordered signed-coordinate domain");}
        var sampleMap=object(value.get("sample_map"));keys(sampleMap,"path","format");String format=text(sampleMap.get("format"));require(Set.of("text","gzip").contains(format),"Declare text or gzip sample-map format");
        var c=object(value.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","variants","samples","populations","native_cells","line_characters","decoded_bytes");long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000),cells=integer(c.get("native_cells"),1,1000000000000L),decoded=integer(c.get("decoded_bytes"),1,1000000000000L);int files=(int)integer(c.get("files"),64,1000000),variants=(int)integer(c.get("variants"),1,Integer.MAX_VALUE),samples=(int)integer(c.get("samples"),1,1000000),populations=(int)integer(c.get("populations"),1,128),line=(int)integer(c.get("line_characters"),64,268435456);
        var p=object(value.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var limits=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),4,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),threads,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        var copy=new LinkedHashMap<String,Object>();value.forEach((key,v)->copy.put(key.toString(),v));return new Spec(Collections.unmodifiableMap(copy),profile,absolute(value.get("runtime")),genotypes(value.get("query")),genotypes(value.get("reference")),absolute(sampleMap.get("path")),format.equals("gzip"),absolute(value.get("genetic_map")),settings,input,output,files,wall,variants,samples,populations,cells,line,decoded,limits);
    }
}
