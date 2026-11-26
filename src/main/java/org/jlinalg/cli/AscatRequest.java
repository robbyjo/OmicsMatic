/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.alignment.SambambaStreamingBamAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Explicit original array/HTS/targeted preparation, full cohorts and source parameters. */
final class AscatRequest {
    private AscatRequest() { }
    record Sample(String id,String sex,Path tumor,Path normal) { }
    record Reference(String chromosome,Path alleles,Path loci) { }
    record Panel(String patient,String id,String sex,Path hts) { }
    record Spec(Map<String,Object> value,String profile,Path runtime,String mode,List<Sample> samples,
                Map<String,Path> matrices,List<Reference> references,List<Panel> panel,Path bed,
                Path problemLoci,Path fasta,Map<?,?> correction,Map<?,?> settings,long inputBytes,
                long outputBytes,int files,long wallMillis,long nativeCells,
                NextflowProcess.Limits process,SambambaStreamingBamAudit.Limits bamLimits) { }
    static String scalar(Object raw)throws IOException {
        String value=text(raw);require(!value.isBlank()&&value.length()<=32768&&value.chars().noneMatch(c->c<32||c==127),"Explicit nonempty text without control characters required");return value;
    }
    static Path absolute(Object raw)throws IOException {
        Path path=Path.of(scalar(raw));require(path.isAbsolute()&&path.normalize().equals(path),"Require canonical absolute ASCAT input/runtime paths");return path;
    }
    private static Path optional(Object raw)throws IOException{return raw==null?null:absolute(raw);}
    private static String id(Object raw,boolean shell)throws IOException {
        String value=scalar(raw);require(value.length()<=160&&!value.contains("/")&&!value.contains("\\")&&!Set.of(".","..").contains(value),"Source sample IDs must form safe direct-child plot names");
        if(shell)require(value.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*"),"Original ASCAT shell preparation requires IDs containing letters, digits, underscore, dot or hyphen; supply an explicit safe ID, without silent repairs");return value;
    }
    private static String sex(Object raw)throws IOException {String value=text(raw);require(Set.of("XX","XY").contains(value),"Declare original XX or XY sex-chromosome handling for every sample");return value;}
    static boolean bool(Object raw)throws IOException{require(raw instanceof Boolean,"Explicit Boolean required");return(Boolean)raw;}
    static double real(Object raw,double low,double high)throws IOException {
        require(raw instanceof Number,"Finite explicit numeric parameter required");double value=((Number)raw).doubleValue();require(Double.isFinite(value)&&value>=low&&value<=high,"ASCAT parameter is outside the supported finite domain");return value;
    }
    static Spec read(Path path)throws IOException{return parse(json(path,16777216));}
    static Spec parse(Object raw)throws IOException {
        var value=object(raw);keys(value,"schema_version","profile","runtime","preparation","mode","samples","matrices","references","normal_panel","bed","problem_loci","reference_fasta","correction","settings","capacity","process");integer(value.get("schema_version"),1,1);scalar(value.get("preparation"));
        String profile=scalar(value.get("profile"));Map<?,?> source=AscatRuntime.profile(profile);boolean current="3.2.0".equals(source.get("ASCAT"));
        String mode=text(value.get("mode"));require(Set.of("matrices","hts","targeted").contains(mode),"Select matrices, hts or targeted original workflow");boolean sequencing=!mode.equals("matrices");require(current||!sequencing,"Original ASCAT2.5.2 accepts prepared matrices; its namespace does not contain the3.2 HTS/normal-panel APIs");
        var samples=new ArrayList<Sample>();var ids=new HashSet<String>();
        for(Object item:list(value.get("samples"))) {
            var row=object(item);keys(row,"id","sex","tumor","normal");String name=id(row.get("id"),sequencing);require(ids.add(name),"Duplicate source sample ID");
            Path tumor=optional(row.get("tumor")),normal=optional(row.get("normal"));require(sequencing? tumor!=null:tumor==null&&normal==null,"Matrix samples have no HTS paths; HTS samples require their complete tumor alignment");
            if(sequencing){hts(tumor);if(normal!=null)hts(normal);}samples.add(new Sample(name,sex(row.get("sex")),tumor,normal));
        }
        require(!samples.isEmpty()&&samples.size()<=10000,"Require the complete declared cohort,1..10000 samples");
        var matrices=new LinkedHashMap<String,Path>();
        if(!sequencing) {
            var rows=object(value.get("matrices"));keys(rows,"tumor_logR","tumor_BAF","normal_logR","normal_BAF");
            for(String key:List.of("tumor_logR","tumor_BAF","normal_logR","normal_BAF"))matrices.put(key,optional(rows.get(key)));
            require(matrices.get("tumor_logR")!=null&&matrices.get("tumor_BAF")!=null&&(matrices.get("normal_logR")==null)==(matrices.get("normal_BAF")==null),"Require complete tumor matrices and either both matched-normal matrices or neither");
        }else require(value.get("matrices")==null,"HTS preparation generates its own complete source matrices");
        var settings=object(value.get("settings"));keys(settings,"seed","chromosomes","sex_chromosomes","genome","is_targeted","segmentation","penalty","gamma","genotype_platform","y_limit","min_ploidy","max_ploidy","min_purity","max_purity","minimum_goodness_of_fit","manual_rho","manual_psi","joint_germline_policy","sample_weights","joint_algorithm","joint_refine","nthreads","min_count","panel_min_count","is_chr_based","min_base_qual","min_map_qual","required_flag","filtered_flag","loci_bin_size");
        integer(settings.get("seed"),0,Integer.MAX_VALUE);List<String> chromosomes=strings(settings.get("chromosomes")),sexChromosomes=strings(settings.get("sex_chromosomes"));
        require(!chromosomes.isEmpty()&&chromosomes.size()<=1000&&new HashSet<>(chromosomes).size()==chromosomes.size(),"Declare a nonempty unique chromosome order");
        for(String chromosome:chromosomes)require(chromosome.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,79}"),"Invalid explicit chromosome label");
        require(new HashSet<>(sexChromosomes).size()==sexChromosomes.size()&&!sexChromosomes.isEmpty(),"Declare distinct source sex-chromosome labels");
        require(Set.of("hg19","hg38","CHM13").contains(text(settings.get("genome"))),"Declare hg19, hg38 or CHM13; author builds are not inferred");
        boolean targeted=bool(settings.get("is_targeted"));require(current||!targeted,"Legacy2.5.2 has no isTargetedSeq parameter");if(sequencing)require(targeted==mode.equals("targeted"),"HTS/targeted load behavior must match the selected preparation");
        String segmenter=text(settings.get("segmentation"));require(Set.of("aspcf","asmultipcf").contains(segmenter)&&(!sequencing||segmenter.equals("aspcf")),"Select original segmentation; joint HTS analysis requires explicitly prepared cohort matrices");
        real(settings.get("penalty"),1,1000000000);real(settings.get("gamma"),Double.MIN_NORMAL,1000);real(settings.get("y_limit"),1,1000);real(settings.get("minimum_goodness_of_fit"),0,100);
        double minPloidy=real(settings.get("min_ploidy"),.5000001,1000),maxPloidy=real(settings.get("max_ploidy"),.5000001,1000),minPurity=real(settings.get("min_purity"),.01,1.05),maxPurity=real(settings.get("max_purity"),.01,1.05);
        require(minPloidy<maxPloidy&&minPurity<maxPurity,"Explicit original fitting bounds must increase");
        if(!current)require(minPloidy==1.5&&maxPloidy==5.5&&minPurity==.1&&maxPurity==1.05,"Legacy2.5.2 retains its fixed original source bounds; custom3.2 bounds are unsupported");
        Object platform=settings.get("genotype_platform");boolean unmatched=sequencing?samples.stream().anyMatch(sample->sample.normal()==null):matrices.get("normal_logR")==null;
        require(unmatched?platform!=null:platform==null,"Tumor-only analysis requires an explicit platform; matched-normal analysis does not predict substitute germline genotypes");
        if(platform!=null)require(strings(source.get("genotype_platforms")).contains(text(platform)),"Platform is absent from the selected original namespace's live branches");
        manual(settings.get("manual_rho"),settings.get("manual_psi"),samples.size(),sequencing,current);
        if(segmenter.equals("asmultipcf")) {
            require(samples.size()>1&&"source-first-normal".equals(settings.get("joint_germline_policy")),"Joint segmentation requires a cohort and explicit acknowledgment of the source's first-normal genotype policy");
            require(Set.of("exact","fast").contains(text(settings.get("joint_algorithm"))),"Declare the original joint algorithm");bool(settings.get("joint_refine"));
            if(settings.get("sample_weights")!=null){List<?> weights=list(settings.get("sample_weights"));require(weights.size()==samples.size(),"Every joint sample requires its declared weight");for(Object weight:weights)real(weight,Double.MIN_NORMAL,Double.MAX_VALUE);}
        }else require(settings.get("joint_germline_policy")==null&&settings.get("sample_weights")==null&&settings.get("joint_algorithm")==null&&settings.get("joint_refine")==null,"Single-sample segmentation cannot silently ignore joint-only settings");
        if(sequencing) {
            integer(settings.get("nthreads"),1,256);integer(settings.get("min_count"),1,Integer.MAX_VALUE);integer(settings.get("min_base_qual"),0,255);integer(settings.get("min_map_qual"),0,255);integer(settings.get("required_flag"),0,65535);integer(settings.get("filtered_flag"),0,65535);integer(settings.get("loci_bin_size"),1,Integer.MAX_VALUE);
            if(mode.equals("targeted")){integer(settings.get("panel_min_count"),1,Integer.MAX_VALUE);bool(settings.get("is_chr_based"));}
            else require(settings.get("panel_min_count")==null&&settings.get("is_chr_based")==null,"HTS mode cannot ignore targeted normal-panel settings");
        }else for(String field:List.of("nthreads","min_count","panel_min_count","is_chr_based","min_base_qual","min_map_qual","required_flag","filtered_flag","loci_bin_size"))require(settings.get(field)==null,"Prepared-matrix mode cannot ignore HTS-only setting: "+field);
        var references=new ArrayList<Reference>();var referenceChromosomes=new HashSet<String>();
        for(Object item:list(value.get("references"))) {
            var row=object(item);keys(row,"chromosome","alleles","loci");String chromosome=scalar(row.get("chromosome"));require(referenceChromosomes.add(chromosome),"Duplicate reference chromosome");Path loci=optional(row.get("loci"));
            require(mode.equals("hts")?loci!=null:loci==null,"HTS requires source loci files; targeted preparation generates loci from the capture design");references.add(new Reference(chromosome,absolute(row.get("alleles")),loci));
        }
        require(sequencing?referenceChromosomes.equals(new HashSet<>(chromosomes)):references.isEmpty(),"Capture every declared chromosome reference, or none for prepared matrices");
        var panel=new ArrayList<Panel>();var patients=new HashSet<String>();var normals=new HashSet<String>();
        for(Object item:list(value.get("normal_panel"))) {
            var row=object(item);keys(row,"patient_id","id","sex","hts");String patient=id(row.get("patient_id"),true),name=id(row.get("id"),true);require(patients.add(patient)&&normals.add(name),"Original targeted preparation requires one distinct normal per patient");Path hts=absolute(row.get("hts"));hts(hts);panel.add(new Panel(patient,name,sex(row.get("sex")),hts));
        }
        require(mode.equals("targeted")?panel.size()>=2:panel.isEmpty(),"Declare the complete normal panel (at least2) only for targeted preparation");
        Path bed=optional(value.get("bed")),problem=optional(value.get("problem_loci")),fasta=optional(value.get("reference_fasta"));require(!mode.equals("targeted")||bed!=null,"Targeted preparation requires the exact capture BED");require(sequencing||bed==null&&problem==null&&fasta==null,"Prepared-matrix mode cannot ignore HTS preparation files");
        boolean cram=samples.stream().anyMatch(sample->isCram(sample.tumor())||isCram(sample.normal()))||panel.stream().anyMatch(sample->isCram(sample.hts()));require(!cram||fasta!=null,"CRAM requires an explicit permitted complete FASTA and its existing .fai");
        var correction=object(value.get("correction"));keys(correction,"mode","gc","replication");String correctionMode=text(correction.get("mode"));require(Set.of("none","GC","GC-RT").contains(correctionMode),"Select explicit original correction mode");
        Path gc=optional(correction.get("gc")),replication=optional(correction.get("replication"));require(correctionMode.equals("none")?gc==null&&replication==null:gc!=null,"Correction requires its complete GC source; none cannot ignore correction files");require(correctionMode.equals("GC-RT")?current&&replication!=null:replication==null,"Only3.2 supports original GC plus replication-timing correction");
        var c=object(value.get("capacity"));keys(c,"input_bytes","output_bytes","files","wall_millis","native_cells","bam_decoded_bytes","bam_records","bam_record_bytes");long input=integer(c.get("input_bytes"),1,1000000000000L),output=integer(c.get("output_bytes"),1,1000000000000L),wall=integer(c.get("wall_millis"),1,604800000),nativeCells=integer(c.get("native_cells"),1,1000000000);int files=(int)integer(c.get("files"),64,1000000);
        var bam=new SambambaStreamingBamAudit.Limits(input,integer(c.get("bam_decoded_bytes"),12,1000000000000L),integer(c.get("bam_records"),0,Long.MAX_VALUE),(int)integer(c.get("bam_record_bytes"),32,1073741824),16777216,16777216);
        var p=object(value.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var limits=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,wall),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,output),(int)integer(p.get("open_files"),8,1048576),integer(p.get("workspace_bytes"),1,1000000000000L),(int)integer(p.get("file_count"),4,files),(int)integer(p.get("processes"),1,4096),(int)integer(p.get("threads"),1,1048576),integer(p.get("sampled_rss_bytes"),1,1099511627776L),(int)integer(p.get("monitor_millis"),1,1000));
        var copy=new LinkedHashMap<String,Object>();value.forEach((key,v)->copy.put(key.toString(),v));
        return new Spec(Collections.unmodifiableMap(copy),profile,absolute(value.get("runtime")),mode,List.copyOf(samples),Collections.unmodifiableMap(matrices),List.copyOf(references),List.copyOf(panel),bed,problem,fasta,correction,settings,input,output,files,wall,nativeCells,limits,bam);
    }
    private static void manual(Object rhos,Object psis,int samples,boolean sequencing,boolean current)throws IOException {
        require((rhos==null)==(psis==null),"Declare both original manual rho/psi arrays or neither");if(rhos==null)return;
        require(!sequencing,"Declare manual fitting through explicit prepared cohort matrices; HTS groups are independently fitted");List<?> rho=list(rhos),psi=list(psis);require(rho.size()==samples&&psi.size()==samples,"Every original sample requires a manual-override state");boolean anyMissing=false,anyManual=false;
        for(int i=0;i<samples;i++){require((rho.get(i)==null)==(psi.get(i)==null),"Manual rho/psi states differ");if(rho.get(i)==null)anyMissing=true;else{anyManual=true;real(rho.get(i),.01,1.05);real(psi.get(i),Double.MIN_NORMAL,1000);}}
        require(current||!anyMissing||!anyManual,"Original legacy manual fitting cannot safely select mixed missing/manual source branches");
    }
    private static boolean isCram(Path path){return path!=null&&path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cram");}
    private static void hts(Path path)throws IOException {String name=path.getFileName().toString().toLowerCase(Locale.ROOT);require(name.endsWith(".bam")||name.endsWith(".cram"),"Supply explicit BAM or CRAM inputs and existing indexes; formats are not guessed");}
}
