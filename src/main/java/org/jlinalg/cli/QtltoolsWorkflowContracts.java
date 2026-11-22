/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Typed edition/operation requests. No arbitrary flags, inferred editions or data repair. */
final class QtltoolsWorkflowContracts {
    private QtltoolsWorkflowContracts() { }
    static final Set<String> OPERATIONS=Set.of("CIS_NOMINAL","CIS_PERMUTE","CIS_CONDITIONAL","TRANS_NOMINAL","TRANS_PERMUTE","TRANS_SAMPLE","TRANS_ADJUST","MBV","QUAN","FDENSITY");
    static final Set<String> FILTERS=Set.of("include_samples","exclude_samples","include_sites","exclude_sites","include_positions","exclude_positions","include_phenotypes","exclude_phenotypes","include_covariates","exclude_covariates");
    static final Map<String,String> SUFFIX=Map.ofEntries(Map.entry("VCF_GZ",".vcf.gz"),Map.entry("VCF",".vcf"),Map.entry("BCF",".bcf"),Map.entry("BED_GZ",".bed.gz"),Map.entry("BED",".bed"),Map.entry("BAM",".bam"),Map.entry("SAM",".sam"),Map.entry("TEXT",".txt"),Map.entry("TEXT_GZ",".txt.gz"),Map.entry("GTF",".gtf"),Map.entry("GTF_GZ",".gtf.gz"));
    record Input(String key,Map<String,Object> descriptor,String captured,String capturedIndex) { }

    static Map<String,Object> validate(Map<String,Object> raw)throws IOException {
        keys(raw,Set.of("schema_version","mode","edition","engine","operation","out","inputs","parameters","runtime","acknowledge_original_handling","limits"));
        if(integer(raw,"schema_version",0,1,1)!=1||!text(raw,"mode").equals("RUN"))throw new IOException("Require schema_version1 and mode RUN");
        String edition=choice(raw,"edition","",Set.of("1.1","1.2","1.3.1")),engine=choice(raw,"engine","",Set.of("original","native")),operation=choice(raw,"operation","",OPERATIONS);text(raw,"out");
        if(!bool(raw,"acknowledge_original_handling",false))throw new IOException("Explicitly acknowledge original sample intersection,missing-value handling,filters and transformations;no adapter cleaning is performed");
        if(engine.equals("native")&&(!operation.equals("FDENSITY")||!edition.equals("1.3.1")))throw new IOException("Native engine is the selected1.3.1 fdensity component only;no automatic engine fallback");
        if(engine.equals("original")) {
            var runtime=object(raw.get("runtime"),"runtime");keys(runtime,Set.of("executable","library_directories","wsl_distribution"));text(runtime,"executable");
            if(runtime.containsKey("wsl_distribution")){identity(text(runtime,"wsl_distribution"));if(text(runtime,"wsl_distribution").startsWith("-"))throw new IOException("Invalid WSL distribution");}
            if(runtime.containsKey("library_directories"))for(Object value:list(runtime.get("library_directories"),"library_directories")){if(!(value instanceof String path)||path.isBlank()||path.indexOf(0)>=0)throw new IOException("Require explicit library directory paths");}
        }else if(raw.containsKey("runtime"))throw new IOException("Native execution cannot carry an unused original runtime binding");
        var inputs=object(raw.get("inputs"),"inputs");var allowed=new LinkedHashSet<String>();var required=new LinkedHashSet<String>();
        if(operation.startsWith("CIS_")||operation.startsWith("TRANS_")){required.addAll(Set.of("genotypes","phenotypes"));allowed.addAll(FILTERS);allowed.add("covariates");}
        if(operation.equals("CIS_CONDITIONAL"))required.add("thresholds");
        if(operation.equals("TRANS_ADJUST"))required.add("adjustment");
        if(operation.equals("MBV"))required.addAll(Set.of("genotypes","alignment"));
        if(operation.equals("QUAN"))required.addAll(Set.of("gtf","alignments"));
        if(operation.equals("FDENSITY"))required.addAll(Set.of("qtl","annotations"));
        allowed.addAll(required);keys(inputs,allowed);if(!inputs.keySet().containsAll(required))throw new IOException("Missing operation-specific input(s): "+required);
        for(Input input:inputs(raw)) {
            var descriptor=input.descriptor();keys(descriptor,Set.of("path","format","index","index_format"));text(descriptor,"path");String format=text(descriptor,"format");if(!SUFFIX.containsKey(format))throw new IOException("Unsupported declared input format");
            Set<String> formats=switch(input.key().replaceAll("alignment[0-9]+","alignment")) {
                case "genotypes" -> operation.startsWith("CIS_")?Set.of("VCF_GZ","VCF","BCF","BED_GZ","BED"):Set.of("VCF_GZ","VCF","BCF");
                case "phenotypes","qtl","annotations" -> Set.of("BED_GZ","BED");
                case "alignment" -> Set.of("BAM","SAM");case "gtf" -> Set.of("GTF","GTF_GZ");default -> Set.of("TEXT","TEXT_GZ");
            };
            if(!formats.contains(format))throw new IOException("Input format does not match its role: "+input.key());
            if(descriptor.containsKey("index")!=descriptor.containsKey("index_format"))throw new IOException("Index path and declared format must be supplied together");
            if(descriptor.containsKey("index")) {
                text(descriptor,"index");String index=text(descriptor,"index_format");
                if(!(format.equals("BAM")?Set.of("BAI","CSI"):format.equals("BCF")?Set.of("CSI"):Set.of("TBI","CSI")).contains(index)||Set.of("TEXT","TEXT_GZ","GTF","GTF_GZ","SAM","BED","VCF").contains(format))throw new IOException("Index type does not match the declared file format");
            }
        }
        if(operation.equals("QUAN")&&list(inputs.get("alignments"),"alignments").size()>1&&!edition.equals("1.1"))throw new IOException("Only1.1 quan accepts multiple BAM/SAM inputs in one call");
        var params=parameters(raw,edition,operation);var limits=raw.containsKey("limits")?object(raw.get("limits"),"limits"):Map.<String,Object>of();keys(limits,Set.of("timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_rows","maximum_line_bytes","maximum_bins"));
        var caps=new LinkedHashMap<String,Object>();caps.put("timeout_seconds",integer(limits,"timeout_seconds",3600,1,86400));caps.put("maximum_input_bytes",integer(limits,"maximum_input_bytes",1_000_000_000L,1,100_000_000_000_000L));caps.put("maximum_output_bytes",integer(limits,"maximum_output_bytes",2_000_000_000L,1,100_000_000_000_000L));caps.put("maximum_rows",integer(limits,"maximum_rows",10_000_000,1,1_000_000_000));caps.put("maximum_line_bytes",integer(limits,"maximum_line_bytes",8_000_000,100,256_000_000));caps.put("maximum_bins",integer(limits,"maximum_bins",100_000,1,1_000_000));
        if(operation.startsWith("TRANS_")&&((Number)params.get("bins")).longValue()>((Number)caps.get("maximum_bins")).longValue())throw new IOException("Requested trans bins exceed maximum_bins;increase the explicit capacity without changing the analysis");
        if(operation.equals("FDENSITY")&&(2L*((Number)params.get("window")).longValue()+((Number)params.get("bin")).longValue()-1)/((Number)params.get("bin")).longValue()>((Number)caps.get("maximum_bins")).longValue())throw new IOException("Requested fdensity bins exceed maximum_bins");
        var result=new LinkedHashMap<>(raw);result.put("parameters",params);result.put("limits",caps);return result;
    }

    private static Map<String,Object> parameters(Map<String,Object> raw,String edition,String operation)throws IOException {
        var p=object(raw.get("parameters"),"parameters");var allowed=new LinkedHashSet<String>();var result=new LinkedHashMap<String,Object>();
        allowed.add("seed");result.put("seed",integer(p,"seed",15112011,0,2_147_483_647));
        if(operation.startsWith("CIS_")||operation.startsWith("TRANS_")) {
            allowed.addAll(Set.of("normal","window","region","chunk"));result.put("normal",bool(p,"normal",false));result.put("window",integer(p,"window",operation.startsWith("CIS_")?1_000_000:5_000_000,1,1_000_000_000));
            if(p.containsKey("region")) {if(operation.startsWith("TRANS_"))throw new IOException("Trans supports chunk selection,not the cis --region option");result.put("region",argument(text(p,"region")));}
            if(p.containsKey("chunk")){if(operation.equals("TRANS_SAMPLE"))throw new IOException("Original trans --sample cannot be combined with --chunk");var chunk=list(p.get("chunk"),"chunk");if(chunk.size()!=2)throw new IOException("chunk requires two integers");var values=new ArrayList<Long>();for(Object value:chunk)values.add(integer(Map.of("x",value),"x",-1,0,1_000_000));if(values.get(0)>values.get(1)||values.get(1)==0)throw new IOException("Invalid chunk coordinates");result.put("chunk",values);}
        }
        if(operation.startsWith("CIS_")) {allowed.addAll(Set.of("grouping","standard_error"));result.put("grouping",choice(p,"grouping","none",Set.of("none","best","pca1","mean")));boolean error=bool(p,"standard_error",false);if(error&&!edition.equals("1.3.1"))throw new IOException("--std-err is unavailable in the selected historical edition");result.put("standard_error",error);}
        if(operation.equals("CIS_NOMINAL")){allowed.add("nominal_p");result.put("nominal_p",real(p,"nominal_p",1,0,1,true));}
        if(operation.equals("CIS_PERMUTE")){allowed.add("permutations");result.put("permutations",integer(p,"permutations",1000,10,1_000_000_000));}
        if(operation.startsWith("TRANS_")){allowed.addAll(Set.of("threshold","bins"));result.put("threshold",real(p,"threshold",1e-5,0,1,false));result.put("bins",integer(p,"bins",1000,1,1_000_000));if(operation.equals("TRANS_SAMPLE")){allowed.add("sample");result.put("sample",integer(p,"sample",1000,1,1_000_000_000));}}
        if(operation.equals("MBV")) {
            allowed.addAll(Set.of("region","mapping_quality","base_quality","binomial_p","minimal_coverage","imputation_quality","imputation_probability","keep_duplicates"));if(p.containsKey("region"))result.put("region",argument(text(p,"region")));
            result.put("mapping_quality",integer(p,"mapping_quality",10,0,255));result.put("base_quality",integer(p,"base_quality",5,0,255));result.put("minimal_coverage",integer(p,"minimal_coverage",10,0,1_000_000));result.put("binomial_p",real(p,"binomial_p",.05,0,1,true));result.put("imputation_quality",real(p,"imputation_quality",.9,0,1,true));result.put("imputation_probability",real(p,"imputation_probability",.99,0,1,true));result.put("keep_duplicates",bool(p,"keep_duplicates",false));
        }
        if(operation.equals("QUAN")) {
            allowed.addAll(Set.of("sample_names","rpkm","tpm","gene_types","mapping_quality","mismatch","mismatch_total","proper_pairs","consistency","no_merge","legacy_options","failed_qc","minimum_exon","remove_duplicates","region","filename_hash","maximum_read_length"));
            int count=list(object(raw.get("inputs"),"inputs").get("alignments"),"alignments").size();var names=strings(p.get("sample_names"),"sample_names",count,count);for(String name:names)argument(name);result.put("sample_names",names);
            for(String key:List.of("rpkm","tpm","proper_pairs","consistency","no_merge","legacy_options","failed_qc","remove_duplicates"))result.put(key,bool(p,key,false));
            if(edition.equals("1.1")&&Boolean.TRUE.equals(result.get("tpm")))throw new IOException("TPM is unavailable in original1.1 quan;no edition substitution");
            String hashing=choice(p,"filename_hash","source",Set.of("source","content","none"));if(edition.equals("1.1")&&!hashing.equals("source"))throw new IOException("Original1.1 quan has no1.2 filename-hash options");result.put("filename_hash",hashing);
            if(p.containsKey("gene_types")){var types=strings(p.get("gene_types"),"gene_types",1,100);for(String type:types)argument(type);result.put("gene_types",types);}
            result.put("mapping_quality",integer(p,"mapping_quality",10,0,255));result.put("minimum_exon",integer(p,"minimum_exon",0,0,2_147_483_647));result.put("mismatch",real(p,"mismatch",-1,-1,2_147_483_647,true));result.put("mismatch_total",real(p,"mismatch_total",-1,-1,2_147_483_647,true));
            if(p.containsKey("maximum_read_length")){if(!edition.equals("1.1"))throw new IOException("maximum_read_length is the historical1.1 quan grouping option");result.put("maximum_read_length",integer(p,"maximum_read_length",1000,1,2_147_483_647));}
            if(p.containsKey("region")){if(Boolean.TRUE.equals(result.get("rpkm"))||Boolean.TRUE.equals(result.get("tpm")))throw new IOException("Original quan cannot combine regional counting with RPKM/TPM");result.put("region",argument(text(p,"region")));}
        }
        if(operation.equals("FDENSITY")){allowed.addAll(Set.of("window","bin"));result.put("window",integer(p,"window",1_000_000,1,2_147_483_647));result.put("bin",integer(p,"bin",1000,1,2_147_483_647));}
        keys(p,allowed);return result;
    }
    static String argument(String value)throws IOException {identity(value);if(value.startsWith("-")||value.indexOf('\n')>=0)throw new IOException("A value cannot be an option token");return value;}
    static long limit(Map<String,Object> r,String name){return ((Number)((Map<?,?>)r.get("limits")).get(name)).longValue();}
    static List<Input> inputs(Map<String,Object> request)throws IOException {
        var result=new ArrayList<Input>();var files=object(request.get("inputs"),"inputs");
        for(var entry:files.entrySet()) {
            if(entry.getKey().equals("alignments")){var array=list(entry.getValue(),"alignments");if(array.isEmpty()||array.size()>10000)throw new IOException("Require1..10000 alignment files");for(int i=0;i<array.size();i++)result.add(input("alignment"+i,object(array.get(i),"alignment")));}
            else result.add(input(entry.getKey(),object(entry.getValue(),entry.getKey())));
        }return result;
    }
    private static Input input(String key,Map<String,Object> descriptor)throws IOException {
        String suffix=SUFFIX.get(text(descriptor,"format"));if(suffix==null)throw new IOException("Unsupported declared format");String captured="inputs/"+key+suffix;String index=descriptor.containsKey("index_format")?captured+"."+text(descriptor,"index_format").toLowerCase(java.util.Locale.ROOT):null;return new Input(key,descriptor,captured,index);
    }
    static Map<String,Object> paths(Map<String,Object> raw,Path parent,boolean capture)throws IOException {
        var result=new LinkedHashMap<>(raw);var files=new LinkedHashMap<String,Object>();
        for(var entry:object(raw.get("inputs"),"inputs").entrySet()) {
            if(entry.getKey().equals("alignments")){var values=new ArrayList<Map<String,Object>>();var array=list(entry.getValue(),"alignments");for(int i=0;i<array.size();i++)values.add(path("alignment"+i,object(array.get(i),"alignment"),parent,capture));files.put(entry.getKey(),values);}
            else files.put(entry.getKey(),path(entry.getKey(),object(entry.getValue(),entry.getKey()),parent,capture));
        }result.put("inputs",files);if(capture)result.put("out",".");return result;
    }
    private static Map<String,Object> path(String key,Map<String,Object> source,Path parent,boolean capture)throws IOException {
        var result=new LinkedHashMap<>(source);var input=input(key,source);result.put("path",capture?input.captured():resolve(parent,text(source,"path")).toString());if(source.containsKey("index"))result.put("index",capture?input.capturedIndex():resolve(parent,text(source,"index")).toString());return result;
    }
}
