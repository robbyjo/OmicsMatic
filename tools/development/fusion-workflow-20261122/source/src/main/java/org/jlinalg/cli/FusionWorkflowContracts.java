/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Typed original-script settings; scientific defaults belong to the pinned source. */
final class FusionWorkflowContracts {
    private FusionWorkflowContracts() { }
    static final String REVISION="9346c1222bffbb34499fa7a8e23c1b701b55cb05";
    static final String PROFILE="fusion-"+REVISION;
    static final String SCOPE="Original FUSION scripts with recorded R/package/tool versions. Current source is not substituted for unreported historical paper editions. No whole-paper reproduction or biological calibration. Read original conditions, skipped/no-result rows and all raw exports. VERIFY checks saved integrity and relationships; REPLAY re-executes original R with captured inputs into NEW output.";
    static final String ACTION="SKIP3: DEPENDENT ANALYSIS SKIPPED. Restore the named exact permitted input/runtime/package/tool, installing privately where allowed; otherwise retain the skip. FAIL2: NO COMPLETE RESULT. Keep raw, partial, failed, null and superseded evidence, fix the named cause and retry NEW output. No IDs, NA/NaN/Inf or scientific settings are silently repaired. Require successful VERIFY and a valid completion marker. Integrity hashes do not authenticate coordinated replacement.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    private static final Map<String,String> ASSOC=types(
        "force_model:s max_impute:p min_r2pred:p perm:i perm_minp:p coloc_P:p coloc_susie_P:p GWASN:i caviar:b");
    private static final Map<String,String> TRAIN=types(
        "resid:b hsq_p:p hsq_set:p crossval:i verbose:i noclean:b rn:b save_hsq:b models:s");
    private static final Map<String,String> POST=types(
        "minp_input:p max_r2:p min_r2:p locus_win:i max_cz_increase:n plot:b plot_legend:s plot_corr:b plot_individual:b plot_eqtl:b plot_scatter:b report:b omnibus:b omnibus_corr:s eqtl_model:s ldsc:b save_loci:b verbose:i zthresh:n");
    static Map<String,String> optionTypes(String operation)throws IOException {
        return switch(operation){case "ASSOCIATE"->ASSOC;case "TRAIN"->TRAIN;case "POSTPROCESS"->POST;default->throw new IOException("Unknown FUSION operation");};
    }
    static Map<String,Object> validate(Map<String,Object> raw,Path base)throws IOException {
        keys(raw,Set.of("schema_version","mode","out","profile","operation","rscript","r_libraries","expected_R","expected_packages","inputs","options","binaries","runtime_compatibility","seed","admission","limits"));
        require(integer(raw.get("schema_version"),1,1)==1&&"RUN".equals(raw.get("mode")),"Require schema_version1 and mode RUN");
        if(!PROFILE.equals(raw.get("profile")))throw new Unavailable("No qualified source profile "+raw.get("profile")+"; use the explicit full FUSION revision "+PROFILE);
        String operation=choice(raw,"operation",Set.of("ASSOCIATE","TRAIN","POSTPROCESS"));
        var result=map("schema_version",1,"mode","RUN","profile",PROFILE,"operation",operation);
        for(String name:List.of("out","rscript"))result.put(name,path(base,text(raw.get(name),name)).toString());
        var libraries=new ArrayList<String>();for(Object value:list(raw.get("r_libraries")))libraries.add(path(base,text(value,"R library")).toString());
        require(!libraries.isEmpty()&&libraries.size()<=16,"Declare 1..16 private/existing R library roots");result.put("r_libraries",libraries);
        String version=text(raw.get("expected_R"),"expected_R");require(version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"),"Declare exact expected_R version");result.put("expected_R",version);
        var declaredPackages=object(raw.get("expected_packages"));var expected=new LinkedHashMap<String,Object>();
        for(var entry:declaredPackages.entrySet()){
            require(entry.getKey().matches("[A-Za-z][A-Za-z0-9.]*"),"Invalid expected package name");String v=text(entry.getValue(),"expected package version");require(v.matches("[0-9][A-Za-z0-9.\\-]*"),"Declare literal package versions");expected.put(entry.getKey(),v);
        }
        result.put("expected_packages",expected);result.put("seed",integer(raw.get("seed"),1,Integer.MAX_VALUE));
        var input=object(raw.get("inputs"));Set<String> allowed=switch(operation){case "ASSOCIATE"->Set.of("sumstats","weights","weights_dir","ref_ld_prefix","chromosome","panel_sizes");case "TRAIN"->Set.of("bfile","phenotype","covariates");default->Set.of("association","sumstats","ref_ld_prefix","chromosome","weights_base","gene_annotation");};
        keys(input,allowed);var inputs=new LinkedHashMap<String,Object>();
        for(var entry:input.entrySet()){
            String value=text(entry.getValue(),entry.getKey());
            if(entry.getKey().equals("chromosome")){require(value.matches("[A-Za-z0-9_.-]{1,32}"),"Declare a literal chromosome label");inputs.put(entry.getKey(),value);}
            else inputs.put(entry.getKey(),path(base,value).toString());
        }
        List<String> required=switch(operation){case "ASSOCIATE"->List.of("sumstats","weights","weights_dir","ref_ld_prefix","chromosome");case "TRAIN"->List.of("bfile");default->List.of("association","sumstats","ref_ld_prefix","chromosome","weights_base");};
        require(inputs.keySet().containsAll(required),"Missing required "+operation+" inputs: "+required);result.put("inputs",inputs);
        var supplied=object(raw.get("options"));var options=new LinkedHashMap<String,Object>();var types=optionTypes(operation);keys(supplied,types.keySet());
        for(var entry:supplied.entrySet()){
            Object value=entry.getValue();String type=types.get(entry.getKey());
            switch(type){case "b"->require(value instanceof Boolean,"Require Boolean "+entry.getKey());case "p"->probability(value);case "i"->integer(value,0,Integer.MAX_VALUE);case "n"->require(decimal(value).signum()>=0,"Require finite nonnegative "+entry.getKey());default->require(!text(value,entry.getKey()).isBlank(),"Require nonblank "+entry.getKey());}
            options.put(entry.getKey(),value);
        }
        if(operation.equals("TRAIN")){
            require(Boolean.TRUE.equals(options.get("noclean")),"TRAIN requires explicit noclean=true; original temporary/failed evidence is preserved");
            require(integer(result.get("seed"),1,1)==1,"Original training resets its RNG seed to1; declare seed1");
            if(options.containsKey("models")){
                String[] models=text(options.get("models"),"models").split(",",-1);require(models.length>0&&Arrays.stream(models).allMatch(Set.of("blup","lasso","top1","enet","bslmm")::contains),"Unknown original prediction model; SuSiE colocalization is not a FUSION prediction model");
            }
            if(options.containsKey("crossval"))integer(options.get("crossval"),0,1000);
        }
        if(operation.equals("POSTPROCESS")){
            require(!(flag(options,"ldsc")&&(flag(options,"omnibus")||options.containsKey("omnibus_corr"))),"LDSC mode disables other tests; choose one original mode");
            if(options.containsKey("omnibus_corr"))require(flag(options,"omnibus"),"Original omnibus_corr is used only with omnibus=true; otherwise the source ignores it");
            if(flag(options,"plot_eqtl")||flag(options,"plot_scatter")||flag(options,"plot_corr")||flag(options,"plot_individual"))require(flag(options,"plot"),"Plot suboptions require explicit plot=true");
            if(flag(options,"plot"))require(inputs.containsKey("gene_annotation"),"Plotting requires the explicit original gene annotation input");
        }
        result.put("options",options);
        var tools=raw.containsKey("binaries")?object(raw.get("binaries")):map();keys(tools,Set.of("plink","gcta","gemma"));var binaries=map();
        for(var entry:tools.entrySet())binaries.put(entry.getKey(),path(base,text(entry.getValue(),entry.getKey())).toString());
        if(operation.equals("TRAIN")){
            require(binaries.containsKey("plink"),"Training requires declared PLINK");
            String models=options.getOrDefault("models","blup,lasso,top1,enet").toString();
            if(!options.containsKey("hsq_set"))require(binaries.containsKey("gcta"),"Original heritability estimation requires declared GCTA");
            if(Arrays.stream(models.split(",")).anyMatch(Set.of("blup","bslmm")::contains))require(binaries.containsKey("gemma"),"Selected original BLUP/BSLMM training requires declared GEMMA");
        }else require(binaries.isEmpty(),"External binary bindings are only used by TRAIN");
        result.put("binaries",binaries);
        var compatibility=raw.containsKey("runtime_compatibility")?object(raw.get("runtime_compatibility")):map();keys(compatibility,Set.of("gcta_no_analysis_probe_exit1"));
        Object acceptProbe=compatibility.getOrDefault("gcta_no_analysis_probe_exit1",false);require(acceptProbe instanceof Boolean,"Declare Boolean GCTA probe compatibility");
        if(Boolean.TRUE.equals(acceptProbe))require(operation.equals("TRAIN")&&binaries.containsKey("gcta")&&!options.containsKey("hsq_set"),"GCTA probe compatibility is only used for original heritability-estimating training");
        result.put("runtime_compatibility",map("gcta_no_analysis_probe_exit1",acceptProbe));
        Set<String> needed=new TreeSet<>(Set.of("here","optparse","jsonlite"));
        if(operation.equals("TRAIN"))needed.add("glmnet");
        if(options.containsKey("coloc_P")||options.containsKey("coloc_susie_P"))needed.add("coloc");
        if(options.containsKey("coloc_susie_P"))needed.add("susieR");
        if(flag(options,"plot_corr"))needed.add("corrplot");
        require(expected.keySet().equals(needed),"Declare exactly required expected_packages: "+needed);
        var admission=raw.containsKey("admission")?object(raw.get("admission")):map();keys(admission,Set.of("allow_no_result_rows","allow_empty","allow_replay_differences"));var policies=map();
        for(String name:List.of("allow_no_result_rows","allow_empty","allow_replay_differences")){Object value=admission.getOrDefault(name,false);require(value instanceof Boolean,"Require Boolean "+name);policies.put(name,value);}result.put("admission",policies);
        var given=raw.containsKey("limits")?object(raw.get("limits")):map();keys(given,Set.of("timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_member_bytes","maximum_files","maximum_features","maximum_samples","maximum_variants","maximum_cells"));var limits=map();
        limits.put("timeout_seconds",bound(given,"timeout_seconds",600,1,86400));limits.put("maximum_input_bytes",bound(given,"maximum_input_bytes",536870912,1,17179869184L));limits.put("maximum_output_bytes",bound(given,"maximum_output_bytes",1073741824,1,34359738368L));limits.put("maximum_member_bytes",bound(given,"maximum_member_bytes",536870912,1,17179869184L));limits.put("maximum_files",bound(given,"maximum_files",10000,16,1000000));limits.put("maximum_features",bound(given,"maximum_features",50000,1,1000000));limits.put("maximum_samples",bound(given,"maximum_samples",100000,3,1000000));limits.put("maximum_variants",bound(given,"maximum_variants",1000000,1,10000000));limits.put("maximum_cells",bound(given,"maximum_cells",100000000,1,2000000000));result.put("limits",limits);
        return result;
    }
    static boolean flag(Map<String,Object> values,String key){return Boolean.TRUE.equals(values.get(key));}
    static Path path(Path base,String value)throws IOException {require(!value.isBlank()&&value.length()<=32768&&value.codePoints().noneMatch(c->c<32||c==127),"Require literal path without controls");Path p=Path.of(value);return (p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    private static Map<String,String> types(String value){var result=new LinkedHashMap<String,String>();for(String part:value.split(" ")){String[] pair=part.split(":");result.put(pair[0],pair[1]);}return Collections.unmodifiableMap(result);}
}
