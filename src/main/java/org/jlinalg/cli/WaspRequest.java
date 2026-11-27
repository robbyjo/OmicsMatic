/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit original BAM, variant mode, remapping settings and complete-population capacities. */
final class WaspRequest {
    private WaspRequest() { }
    record Spec(Map<String,Object> value,String profile,Path runtime,Path bam,Path reference,boolean gzipReference,
            Map<?,?> variants,Map<?,?> settings,long inputBytes,long outputBytes,int files,long wallMillis,
            int records,int variantRows,int lineCharacters,long decodedBytes,long nativeCharacters,long hdfCells,
            NextflowProcess.Limits process) {
        boolean paired() { return settings.get("read_layout").equals("paired-with-optional-single-reads"); }
        boolean dedup() { return settings.get("duplicate_policy").equals("original-random-paired"); }
        int threads()throws IOException { return(int)integer(settings.get("threads"),1,256); }
        long hashSeed()throws IOException { return integer(settings.get("python_hash_seed"),0,4294967295L); }
        long randomSeed()throws IOException { return integer(settings.get("duplicate_random_seed"),0,Long.MAX_VALUE); }
        boolean csi() { return settings.get("bam_index_format").equals("csi"); }
        String mode()throws IOException { return text(variants.get("mode")); }
        List<String> bwaArguments()throws IOException {
            var args=new ArrayList<String>(List.of("mem","-t",Integer.toString(threads())));
            if(bool(settings.get("mark_shorter_split_hits_secondary")))args.add("-M");
            if(settings.get("insert_size")!=null) {
                var insert=object(settings.get("insert_size"));String value=number(insert.get("mean"))+","+number(insert.get("standard_deviation"));
                if(insert.get("maximum")!=null)value+=","+number(insert.get("maximum"))+","+number(insert.get("minimum"));
                args.add("-I");args.add(value);
            }
            for(Object item:list(settings.get("additional_bwa_options")))args.add(text(item));
            return args;
        }
    }
    static Spec parse(Object raw)throws IOException {
        var value=object(raw);keys(value,"schema_version","profile","runtime","preparation","bam","reference","variants","settings","capacity","process");integer(value.get("schema_version"),1,1);
        String profile=scalar(value.get("profile"));require(profile.equals("WASP-0.3.4-d3b8447f-BWA-0.7.17"),"Choose the explicit standalone WASP/BWA source profile");scalar(value.get("preparation"));
        var reference=object(value.get("reference"));keys(reference,"path","format","alt_contigs");String format=text(reference.get("format"));require(Set.of("fasta","fasta-gzip").contains(format),"Declare fasta or fasta-gzip reference bytes");if(reference.get("alt_contigs")!=null)absolute(reference.get("alt_contigs"));
        var variants=object(value.get("variants"));String mode=text(variants.get("mode"));
        if(mode.equals("text")) { keys(variants,"mode","directory");absolute(variants.get("directory")); }
        else if(mode.equals("hdf5")) { keys(variants,"mode","snp_table","snp_index","haplotypes","samples");for(String key:List.of("snp_table","snp_index","haplotypes"))absolute(variants.get(key));samples(variants.get("samples")); }
        else if(mode.equals("vcf-to-hdf5")) {
            keys(variants,"mode","chromosomes","vcfs","samples");absolute(variants.get("chromosomes"));samples(variants.get("samples"));
            var vcfs=list(variants.get("vcfs"));require(!vcfs.isEmpty()&&vcfs.size()<=10000,"Supply the complete explicit per-chromosome original VCF population");var names=new HashSet<String>();
            for(Object item:vcfs){Path path=absolute(item);String name=path.getFileName().toString();member(name);require(names.add(name)&&!name.startsWith("-"),"VCF basenames must be unique and literal");}
        } else throw new IOException("Choose text, hdf5 or vcf-to-hdf5 original variant mode");
        var s=object(value.get("settings"));keys(s,"read_layout","max_sequences","max_snps","threads","mark_shorter_split_hits_secondary","insert_size","additional_bwa_options","mapping_provenance","python_hash_seed","duplicate_policy","duplicate_random_seed","bam_index_format","minimum_final_records","hdf_contig_policy","explicit_no_variant_contigs");
        require(Set.of("single","paired-with-optional-single-reads").contains(text(s.get("read_layout"))),"Declare the complete single or paired/mixed WASP read domain");integer(s.get("max_sequences"),1,1000000);integer(s.get("max_snps"),1,64);long threads=integer(s.get("threads"),1,256);bool(s.get("mark_shorter_split_hits_secondary"));scalar(s.get("mapping_provenance"));
        integer(s.get("python_hash_seed"),0,4294967295L);integer(s.get("duplicate_random_seed"),0,Long.MAX_VALUE);
        String duplicate=text(s.get("duplicate_policy"));require(Set.of("none","original-random-paired").contains(duplicate),"Declare none or original-random-paired duplicate policy. Original single-read rmdup omits its final group and is not admitted; do not substitute or repair it silently");
        require(!duplicate.equals("original-random-paired")||s.get("read_layout").equals("paired-with-optional-single-reads"),"Original rmdup_pe requires the explicit paired/mixed read domain");
        require(Set.of("bai","csi").contains(text(s.get("bam_index_format"))),"Declare final BAI or CSI index");integer(s.get("minimum_final_records"),0,Integer.MAX_VALUE);
        var omitted=strings(s.get("explicit_no_variant_contigs"));require(new HashSet<>(omitted).size()==omitted.size(),"Explicit no-variant contigs must be unique literal names");for(String name:omitted)scalar(name);
        if(mode.equals("text"))require(s.get("hdf_contig_policy")==null&&omitted.isEmpty(),"Text mode has explicit per-contig files; do not supply ignored HDF policies");
        else require(Set.of("literal","original-add-remove-chr").contains(text(s.get("hdf_contig_policy"))),"Explicit literal or original-add-remove-chr HDF contig policy required");
        if(s.get("insert_size")!=null) {
            var insert=object(s.get("insert_size"));keys(insert,"mean","standard_deviation","maximum","minimum");double mean=real(insert.get("mean"),0,1000000000,false),sd=real(insert.get("standard_deviation"),0,1000000000,false);
            require(mean+4*sd+.499<Integer.MAX_VALUE&&mean-4*sd+.499>Integer.MIN_VALUE,"Original BWA computes signed-integer insert bounds before optional limits; the supplied mean/deviation would overflow");
            require((insert.get("maximum")==null)==(insert.get("minimum")==null),"Supply both maximum and minimum insert sizes or neither");
            if(insert.get("maximum")!=null)require(integer(insert.get("minimum"),0,Integer.MAX_VALUE)<=integer(insert.get("maximum"),0,Integer.MAX_VALUE),"Ordered integer source insert-size limits required");
        }
        bwaOptions(list(s.get("additional_bwa_options")));
        var c=object(value.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","records","variant_rows","line_characters","decoded_bytes","native_sam_characters","hdf_cells");
        long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000),decoded=integer(c.get("decoded_bytes"),1,1000000000000L),characters=integer(c.get("native_sam_characters"),1,100000000000L),hdf=integer(c.get("hdf_cells"),1,1000000000000L);
        int files=(int)integer(c.get("files"),128,1000000),records=(int)integer(c.get("records"),1,Integer.MAX_VALUE),rows=(int)integer(c.get("variant_rows"),1,Integer.MAX_VALUE),line=(int)integer(c.get("line_characters"),64,16777216);
        var p=object(value.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var process=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),128,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),threads,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        var copy=new LinkedHashMap<String,Object>();value.forEach((k,v)->copy.put(k.toString(),v));return new Spec(Collections.unmodifiableMap(copy),profile,absolute(value.get("runtime")),absolute(value.get("bam")),absolute(reference.get("path")),format.equals("fasta-gzip"),variants,s,input,output,files,wall,records,rows,line,decoded,characters,hdf,process);
    }
    private static void samples(Object raw)throws IOException {
        if(raw==null)return;var list=list(raw);require(!list.isEmpty()&&list.size()<=1000000,"Choose null for all samples or an explicit nonempty sample population");var names=new HashSet<String>();
        for(Object item:list){String name=scalar(item);require(name.chars().allMatch(c->c>32&&c<127)&&names.add(name),"Sample names must be unique literal ASCII tokens without whitespace");}
    }
    private static void bwaOptions(List<?> values)throws IOException {
        require(values.size()<=128,"Too many explicit BWA option tokens");
        Set<String> switches=Set.of("-a","-Y","-S","-P","-5","-q","-V","-1","-j","-C");
        Set<String> numeric=Set.of("-k","-w","-d","-r","-y","-c","-D","-W","-m","-A","-B","-O","-E","-L","-U","-T","-h","-K","-s","-G","-N","-X");
        var seen=new HashSet<String>();
        for(int i=0;i<values.size();i++) {
            String option=text(values.get(i));require(seen.add(option),"Repeated BWA option refused");
            if(switches.contains(option))continue;
            require(numeric.contains(option)&&++i<values.size(),"Unsupported BWA option or missing explicit numeric argument: "+option);
            String number=text(values.get(i));boolean real=Set.of("-r","-D","-X").contains(option),pair=Set.of("-O","-E","-L","-h").contains(option);
            require(number.matches(real?"[0-9]+(?:\\.[0-9]+)?":pair?"[0-9]+(?:,[0-9]+)?":"[0-9]+"),"BWA integer/real option domain or comma arity differs from the original parser");
            for(String token:number.split(","))if(real)require(Double.isFinite(Double.parseDouble(token)),"Finite BWA option required");else require(Long.parseLong(token)<=(option.equals("-y")?Long.MAX_VALUE:Integer.MAX_VALUE),"Original BWA integer option overflows its source domain");
        }
    }
    static Path absolute(Object value)throws IOException {Path path=Path.of(scalar(value));require(path.isAbsolute()&&path.normalize().equals(path),"Require canonical absolute WASP paths");return path;}
    static String scalar(Object value)throws IOException {String text=text(value);require(!text.isBlank()&&text.length()<=32768&&text.chars().noneMatch(c->c<32||c==127),"Explicit bounded WASP text without controls required");return text;}
    static boolean bool(Object value)throws IOException {require(value instanceof Boolean,"Explicit Boolean required");return(Boolean)value;}
    static double real(Object value,double minimum,double maximum,boolean inclusive)throws IOException {require(value instanceof Number,"Explicit WASP number required");double x=((Number)value).doubleValue();require(Double.isFinite(x)&&(inclusive?x>=minimum:x>minimum)&&x<=maximum,"WASP numeric parameter outside finite source range");return x;}
    static String number(Object value)throws IOException {real(value,0,Double.MAX_VALUE,true);return value.toString();}
}
