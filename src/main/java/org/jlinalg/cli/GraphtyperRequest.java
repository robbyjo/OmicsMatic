/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.genetics.GraphtyperAlignmentAudit.Region;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit original edition, cohort, reference, target and graph-initialization semantics. */
final class GraphtyperRequest {
    private GraphtyperRequest() { }
    record Spec(Map<?,?> value,String profile,Path runtime,Map<?,?> settings,Map<?,?> capacity,
                List<Region> regions,Set<String> samples,long inputBytes,long outputBytes,int files,
                long wallMillis,NextflowProcess.Limits process,boolean legacy) {
        String mode()throws IOException{return text(settings.get("mode"));}
        String mergePolicy()throws IOException{return text(settings.get("legacy_merge_policy"));}
    }
    static Spec parse(Object raw)throws IOException {
        var v=object(raw);keys(v,"schema_version","profile","runtime","preparation","reference","reference_index","alignments","samples","regions","variant_sites","variant_sites_index","settings","capacity","process");integer(v.get("schema_version"),1,1);
        String id=text(v.get("profile"));var profile=GraphtyperRuntime.profile(id);boolean legacy=Boolean.TRUE.equals(profile.get("legacy"));Path runtime=GraphtyperRuntime.absolute(v.get("runtime"));
        String provenance=text(v.get("preparation"));require(!provenance.isBlank()&&provenance.length()<=32768&&provenance.chars().noneMatch(c->c<32&&c!='\n'&&c!='\t'),"Describe actual cohort, reference build, aligner, duplicate/coverage QC and target-selection provenance");
        var paths=new HashSet<Path>();for(String key:List.of("reference","reference_index"))require(paths.add(GraphtyperRuntime.absolute(v.get(key))),"Input roles cannot alias");
        var alignments=list(v.get("alignments"));require(!alignments.isEmpty()&&alignments.size()<=100000,"Supply the complete actual alignment population");
        for(Object item:alignments){var a=object(item);keys(a,"file","index","format");require(Set.of("bam","cram").contains(a.get("format")),"Explicit BAM/CRAM format required");require(!legacy||a.get("format").equals("bam"),"This legacy pipeline profile requires actual prepared BAMs; convert CRAM explicitly with provenance before capture");for(String key:List.of("file","index"))require(paths.add(GraphtyperRuntime.absolute(a.get(key))),"Complete input roles must be distinct files");}
        var samples=new LinkedHashSet<String>();for(String sample:strings(v.get("samples")))require(sample.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}")&&samples.add(sample),"Declare unique literal complete sample IDs safe for original per-sample filenames; no identifiers are repaired");require(!samples.isEmpty()&&samples.size()<=10000000,"Complete actual sample population required");
        var regions=new ArrayList<Region>();var prior=new HashMap<String,Integer>();
        for(Object item:list(v.get("regions"))){var r=object(item);keys(r,"contig","start","end");Region region=new Region(text(r.get("contig")),(int)integer(r.get("start"),1,Integer.MAX_VALUE),(int)integer(r.get("end"),1,Integer.MAX_VALUE));Integer previous=prior.put(region.contig(),region.end());require(previous==null||region.start()>previous,"Regions on a contig must be ordered, distinct and nonoverlapping; no target merging or coordinate repair");regions.add(region);require(regions.size()<=100000,"Complete target capacity exceeded");}
        require(!regions.isEmpty(),"Actual one-based inclusive target population required");
        var s=object(v.get("settings"));keys(s,"mode","threads","legacy_merge_policy","legacy_slice_bases","legacy_pad_bases","minimum_variant_support","minimum_variant_support_ratio");String mode=text(s.get("mode"));require(Set.of("discovery","discovery-with-prior","genotype-only").contains(mode),"Choose original discovery, discovery-with-prior or genotype-only explicitly");require(!id.startsWith("graphtyper-v1.4-")||!mode.equals("genotype-only"),"The source-matched v1.4 pipeline has no genotype-only mode; do not substitute the later pipeline");
        require(Set.of("original-node-no-sort","original-slices-sorted","not-applicable").contains(s.get("legacy_merge_policy")),"Explicit original legacy merge policy required");require(legacy?!s.get("legacy_merge_policy").equals("not-applicable"):s.get("legacy_merge_policy").equals("not-applicable"),"Legacy merge policy must match the selected source edition");
        integer(s.get("threads"),1,256);int slice=0;
        if(legacy){slice=(int)integer(s.get("legacy_slice_bases"),1,10000000);integer(s.get("legacy_pad_bases"),0,1000000);integer(s.get("minimum_variant_support"),1,1000000);double ratio=number(s.get("minimum_variant_support_ratio"));require(ratio>=0&&ratio<=1,"Finite original minimum-support ratio in [0,1] required");}
        else for(String key:List.of("legacy_slice_bases","legacy_pad_bases","minimum_variant_support","minimum_variant_support_ratio"))require(s.get(key)==null,"Modern workflow uses its original internal settings; legacy-only parameters must be null");
        if(legacy)for(var region:regions)require(((long)region.end()-region.start()+1)%slice==0,"Legacy exact targets require complete declared slices; choose exact targets explicitly, without silently extending the last slice");
        boolean sites=!mode.equals("discovery");require((v.get("variant_sites")!=null)==sites&&(v.get("variant_sites_index")!=null)==sites,"Original prior/genotype sites and actual index are required exactly for the selected initialization mode");if(sites)for(String key:List.of("variant_sites","variant_sites_index"))require(paths.add(GraphtyperRuntime.absolute(v.get(key))),"Input roles cannot alias");
        var c=object(v.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","alignment_records","sam_characters","line_characters","read_length","vcf_decoded_bytes","vcf_records","genotype_cells","vcf_header_lines","vcf_alleles");long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000);int files=(int)integer(c.get("files"),128,1000000);
        for(String key:List.of("alignment_records","sam_characters","vcf_decoded_bytes","vcf_records","genotype_cells"))integer(c.get(key),1,1000000000000L);integer(c.get("line_characters"),256,16777216);integer(c.get("read_length"),1,legacy?151:1000000);integer(c.get("vcf_header_lines"),1,1000000);integer(c.get("vcf_alleles"),1,1000000);
        var p=object(v.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var limits=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),128,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),1,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        return new Spec(v,id,runtime,s,c,List.copyOf(regions),Set.copyOf(samples),input,output,files,wall,limits,legacy);
    }
    static double number(Object value)throws IOException {
        double d;if(value instanceof Number n)d=n.doubleValue();else if(value instanceof NextflowParameters.NumberToken n){try{d=Double.parseDouble(n.text());}catch(NumberFormatException e){throw new IOException("Finite original numeric parameter required",e);}}else throw new IOException("Finite numeric parameter required");require(Double.isFinite(d),"Nonfinite original model parameter");return d;
    }
}
