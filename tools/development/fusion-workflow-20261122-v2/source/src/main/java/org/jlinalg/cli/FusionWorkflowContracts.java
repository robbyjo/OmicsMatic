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
            if(flag(options,"plot_eqtl")||flag(options,"plot_scatter")||flag(options,"plot_individual")||options.containsKey("plot_legend"))require(flag(options,"plot"),"Locus-plot suboptions require explicit plot=true; plot_corr can run independently");
            if(options.containsKey("plot_legend"))require(Set.of("all","joint").contains(options.get("plot_legend")),"Original plot_legend is all or joint");
            if(flag(options,"ldsc")||flag(options,"omnibus"))require(!flag(options,"plot")&&!flag(options,"plot_corr")&&!flag(options,"report")&&!flag(options,"save_loci"),"Original LDSC/omnibus modes exit before plotting, reporting or saving conditional loci; request these in a separate POSTPROCESS run");
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
        var compatibility=raw.containsKey("runtime_compatibility")?object(raw.get("runtime_compatibility")):map();keys(compatibility,Set.of("gcta_no_analysis_probe_exit1","gemma_working_directory_output"));
        Object acceptProbe=compatibility.getOrDefault("gcta_no_analysis_probe_exit1",false);require(acceptProbe instanceof Boolean,"Declare Boolean GCTA probe compatibility");
        if(Boolean.TRUE.equals(acceptProbe))require(operation.equals("TRAIN")&&binaries.containsKey("gcta")&&!options.containsKey("hsq_set"),"GCTA probe compatibility is only used for original heritability-estimating training");
        Object gemmaOutput=compatibility.getOrDefault("gemma_working_directory_output",false);require(gemmaOutput instanceof Boolean,"Declare Boolean GEMMA output routing compatibility");
        if(Boolean.TRUE.equals(gemmaOutput))require(operation.equals("TRAIN")&&binaries.containsKey("gemma")&&Arrays.stream(options.getOrDefault("models","blup,lasso,top1,enet").toString().split(",")).anyMatch(Set.of("blup","bslmm")::contains),"GEMMA output routing requires original BLUP/BSLMM training with declared GEMMA");
        result.put("runtime_compatibility",map("gcta_no_analysis_probe_exit1",acceptProbe,"gemma_working_directory_output",gemmaOutput));
        Set<String> needed=new TreeSet<>(Set.of("here","optparse","jsonlite"));
        if(operation.equals("TRAIN"))needed.add("glmnet");
        if(options.containsKey("coloc_P")||options.containsKey("coloc_susie_P"))needed.add("coloc");
        if(options.containsKey("coloc_susie_P"))needed.add("susieR");
        if(flag(options,"plot_corr"))needed.add("corrplot");
        require(expected.keySet().equals(needed),"Declare exactly required expected_packages: "+needed);
        var admission=raw.containsKey("admission")?object(raw.get("admission")):map();keys(admission,Set.of("allow_no_result_rows","allow_empty","allow_replay_differences"));var policies=map();
        for(String name:List.of("allow_no_result_rows","allow_empty","allow_replay_differences")){Object value=admission.getOrDefault(name,false);require(value instanceof Boolean,"Require Boolean "+name);policies.put(name,value);}result.put("admission",policies);
        var given=raw.containsKey("limits")?object(raw.get("limits")):map();keys(given,Set.of("timeout_seconds","maximum_input_bytes","maximum_output_bytes","maximum_member_bytes","maximum_files","maximum_features","maximum_samples","maximum_variants","maximum_cells","maximum_matrix_cells"));var limits=map();
        limits.put("timeout_seconds",bound(given,"timeout_seconds",600,1,86400));limits.put("maximum_input_bytes",bound(given,"maximum_input_bytes",536870912,1,17179869184L));limits.put("maximum_output_bytes",bound(given,"maximum_output_bytes",1073741824,1,34359738368L));limits.put("maximum_member_bytes",bound(given,"maximum_member_bytes",536870912,1,17179869184L));limits.put("maximum_files",bound(given,"maximum_files",10000,16,1000000));limits.put("maximum_features",bound(given,"maximum_features",50000,1,1000000));limits.put("maximum_samples",bound(given,"maximum_samples",100000,3,1000000));limits.put("maximum_variants",bound(given,"maximum_variants",1000000,1,10000000));limits.put("maximum_cells",bound(given,"maximum_cells",100000000,1,2000000000));result.put("limits",limits);
        limits.put("maximum_matrix_cells",bound(given,"maximum_matrix_cells",16000000,1,250000000));
        return result;
    }
    static boolean flag(Map<String,Object> values,String key){return Boolean.TRUE.equals(values.get(key));}
    @FunctionalInterface private interface AddressTransform {String apply(String value)throws IOException;}
    private static Map<String,Object> addresses(Map<String,Object> source,AddressTransform transform)throws IOException {
        var copy=new LinkedHashMap<>(source);
        for(String key:List.of("out","rscript"))copy.put(key,transform.apply(text(source.get(key),key)));
        var libraries=new ArrayList<String>();for(Object value:list(source.get("r_libraries")))libraries.add(transform.apply(text(value,"R library")));copy.put("r_libraries",libraries);
        var inputs=new LinkedHashMap<String,Object>(object(source.get("inputs")));
        for(var entry:inputs.entrySet())if(!entry.getKey().equals("chromosome"))entry.setValue(transform.apply(text(entry.getValue(),entry.getKey())));copy.put("inputs",inputs);
        var binaries=source.containsKey("binaries")?new LinkedHashMap<String,Object>(object(source.get("binaries"))):map();
        for(var entry:binaries.entrySet())entry.setValue(transform.apply(text(entry.getValue(),entry.getKey())));copy.put("binaries",binaries);return copy;
    }
    /** Normalize saved requests using their original path syntax, without accessing that host. */
    static Map<String,Object> recordedRequest(Map<String,Object> source,String base)throws IOException {
        var paths=new ArrayDeque<String>();String dummy=Path.of(".").toAbsolutePath().normalize().resolve("fusion-recorded-address").toString();
        var shadow=addresses(source,value->{paths.addLast(recordedPath(base,value));return dummy;});
        Map<String,Object> checked;
        try{checked=validate(shadow,Path.of(".").toAbsolutePath().normalize());}
        catch(Unavailable error){throw new IOException("Saved scientific request uses an invalid source profile",error);}
        var result=addresses(checked,value->{require(!paths.isEmpty(),"Saved path population differs");return paths.removeFirst();});
        require(paths.isEmpty(),"Saved path population differs");return result;
    }
    static void validateSaved(Map<String,Object> request)throws IOException {
        require(FusionWorkflowFiles.same(request,recordedRequest(request,text(request.get("out"),"saved output"))),"Saved canonical request is not a complete typed normalized request");
    }
    static String recordedPath(String base,String value)throws IOException {
        require(!base.isBlank()&&!value.isBlank()&&value.length()<=32768&&value.codePoints().noneMatch(c->c<32||c==127),"Invalid recorded path");
        boolean windows=base.matches("[A-Za-z]:[/\\\\].*")||base.startsWith("\\\\");
        require(windows||base.startsWith("/"),"Recorded source base must be absolute");
        String path;
        if(windows){
            String b=base.replace('\\','/'),v=value.replace('\\','/');
            if(v.matches("[A-Za-z]:/.*")||v.startsWith("//"))path=v;
            else if(v.startsWith("/")){require(b.matches("[A-Za-z]:/.*"),"Drive-relative path requires a drive base");path=b.substring(0,2)+v;}
            else{require(!v.matches("[A-Za-z]:.*"),"Unsupported recorded drive-relative path");path=b+"/"+v;}
        }else path=value.startsWith("/")?value:base+"/"+value;
        String prefix;String rest;
        if(windows&&path.startsWith("//")){
            String[] fields=path.substring(2).split("/",-1);require(fields.length>=2&&!fields[0].isEmpty()&&!fields[1].isEmpty(),"Invalid recorded UNC root");prefix="//"+fields[0]+"/"+fields[1]+"/";rest=path.substring(prefix.length()-1);
        }else if(windows){require(path.matches("[A-Za-z]:/.*"),"Invalid recorded drive root");prefix=path.substring(0,3);rest=path.substring(3);}
        else{prefix="/";rest=path.substring(1);}
        var parts=new ArrayDeque<String>();for(String part:rest.split("/",-1)){
            if(part.isEmpty()||part.equals("."))continue;
            if(part.equals("..")){if(!parts.isEmpty())parts.removeLast();}else parts.addLast(part);
        }
        String result=prefix+(parts.isEmpty()?"":(prefix.endsWith("/")?"":"/")+String.join("/",parts));return windows?result.replace('/','\\'):result;
    }
    static Path path(Path base,String value)throws IOException {require(!value.isBlank()&&value.length()<=32768&&value.codePoints().noneMatch(c->c<32||c==127),"Require literal path without controls");Path p=Path.of(value);return (p.isAbsolute()?p:base.resolve(p)).toAbsolutePath().normalize();}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    private static Map<String,String> types(String value){var result=new LinkedHashMap<String,String>();for(String part:value.split(" ")){String[] pair=part.split(":");result.put(pair[0],pair[1]);}return Collections.unmodifiableMap(result);}
}
