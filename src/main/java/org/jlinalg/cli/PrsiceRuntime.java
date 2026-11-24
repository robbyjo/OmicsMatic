/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Exact original executable selection and an argument vector with no shell. */
final class PrsiceRuntime {
    private PrsiceRuntime() { }
    static byte[] resource(String name)throws IOException {
        try(var in=PrsiceRuntime.class.getResourceAsStream("/org/jlinalg/omics/prsice235/"+name)) {
            if(in==null)throw new PrsiceContracts.Unavailable("Missing packaged PRSice source/profile resource "+name+"; build the complete current executable JAR");
            return in.readAllBytes();
        }
    }
    static Map<String,Object> profile()throws IOException {return object(GprofilerJson.read(resource("SOURCE.json")));}
    static String platform()throws IOException {
        String os=System.getProperty("os.name").toLowerCase(Locale.ROOT),arch=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if(!Set.of("amd64","x86_64").contains(arch))throw new PrsiceContracts.Unavailable("Pinned original runtime requires x86_64; the declared platform is "+arch);
        if(os.startsWith("windows"))return "windows";
        if(os.startsWith("linux"))return "linux";
        throw new PrsiceContracts.Unavailable("Original runtime execution is qualified for Windows/Linux x86_64; use saved inspection on other supported Java hosts or supply a separately qualified original runtime");
    }
    static Path executable(Path home)throws IOException {
        if(!Files.isDirectory(home))throw new PrsiceContracts.Unavailable("Missing private PRSice runtime home "+home+"; install/stage original 2.3.5 only where permitted, otherwise keep dependent analysis skipped");
        require(home.toRealPath().equals(home.toAbsolutePath().normalize()),"Use a physical runtime home without aliases");
        String platform=platform(),name=platform.equals("windows")?"PRSice_win64.exe":"PRSice_linux";Path file=home.resolve(name);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new PrsiceContracts.Unavailable("Missing original executable "+file+"; restore the selected permitted 2.3.5 runtime");
        require(same(pin(file),object(profile().get("runtime_executables")).get(platform)),"Original PRSice executable bytes differ; restore the exact pinned binary, never refresh old hashes");
        if(!Files.isExecutable(file))throw new PrsiceContracts.Unavailable("Executable permission is unavailable for "+file+"; grant it only where operating policy permits, otherwise skip dependent analysis");
        return file;
    }
    static Path input(Path root,String role)throws IOException {String name=PrsiceContracts.INPUT_FILES.get(role);require(name!=null,"Unknown captured input role");return root.resolve("inputs").resolve(name);}
    static String value(Object raw)throws IOException {return Double.toString(PrsiceContracts.finite(raw,"source argument"));}
    private static void arg(List<String> command,String flag,Object value){command.add("--"+flag);command.add(value.toString());}
    static List<String> command(Path root,Path executable,Map<String,Object> request)throws IOException {
        var command=new ArrayList<String>();command.add(executable.toString());var p=object(request.get("parameters"));var base=object(p.get("base"));var columns=object(base.get("columns"));var clump=object(p.get("clumping"));var filters=object(p.get("filters"));String operation=text(request.get("operation"),"operation");boolean weights="PREPARED_WEIGHTS".equals(base.get("kind"));
        arg(command,"base",weights?root.resolve("worker/prepared-base.tsv"):input(root,"base"));arg(command,"target",root.resolve("inputs/target"));arg(command,"type","bed");command.add("--no-default");command.add("--"+("BETA".equals(base.get("effect_scale"))?"beta":"or"));
        var flags=Map.of("snp","snp","chromosome","chr","position","bp","a1","a1","a2","a2","effect","stat","pvalue","pvalue");var prepared=Map.of("snp","SNP","chromosome","CHR","position","BP","a1","A1","a2","A2","effect","EFFECT","pvalue","P");
        for(String field:List.of("snp","chromosome","position","a1","a2","effect","pvalue"))arg(command,flags.get(field),weights?prepared.get(field):columns.get(field));
        if(Boolean.TRUE.equals(p.get("ignore_fid")))command.add("--ignore-fid");else arg(command,"id-delim",p.get("id_delimiter"));
        if(Boolean.TRUE.equals(p.get("include_nonfounders")))command.add("--nonfounders");
        arg(command,"num-auto",22);arg(command,"seed",integer(p.get("seed"),0,4294967295L));arg(command,"thread",integer(p.get("threads"),1,256));arg(command,"memory",integer(p.get("memory_mib"),16,1048576)+"Mb");
        var target=object(filters.get("target"));arg(command,"geno",value(target.get("missing")));arg(command,"maf",value(target.get("maf")));
        var inputs=object(request.get("inputs"));
        if(inputs.containsKey("reference_bed")){arg(command,"ld",root.resolve("inputs/reference"));arg(command,"ld-type","bed");var reference=object(filters.get("reference"));arg(command,"ld-geno",value(reference.get("missing")));arg(command,"ld-maf",value(reference.get("maf")));}
        for(String field:List.of("base_info","base_maf"))if(filters.get(field)!=null){var filter=object(filters.get(field));arg(command,field.replace('_','-'),filter.get("column")+":"+value(filter.get("minimum")));}
        var selectionFlags=Map.of("target_keep","keep","target_remove","remove","reference_keep","ld-keep","reference_remove","ld-remove","extract","extract","exclude","exclude");
        for(String role:List.of("target_keep","target_remove","reference_keep","reference_remove","extract","exclude"))if(inputs.containsKey(role))arg(command,selectionFlags.get(role),input(root,role));
        if("KEEP_ORIGINAL_WITH_WARNING".equals(object(p.get("alleles")).get("palindromic")))command.add("--keep-ambig");
        if("NONE".equals(clump.get("mode")))command.add("--no-clump");else{arg(command,"clump-kb",integer(clump.get("window_kb"),1,1000000000L));arg(command,"clump-r2",value(clump.get("r2")));arg(command,"clump-p",value(clump.get("pvalue")));}
        command.add("--print-snp");arg(command,"out",root.resolve("worker/original"));
        if(operation.equals("CLUMP")){command.add("--clump-only");return command;}
        var scoring=object(p.get("scoring"));command.add("--all-score");arg(command,"model",scoring.get("model"));arg(command,"missing",scoring.get("missing"));arg(command,"score",scoring.get("scale"));
        if(!Boolean.TRUE.equals(scoring.get("cumulative")))command.add("--non-cumulate");if("REFERENCE".equals(scoring.get("frequency_source")))command.add("--use-ref-maf");
        var threshold=object(scoring.get("thresholds"));if("LEVELS".equals(threshold.get("kind"))){command.add("--fastscore");var levels=new ArrayList<String>();for(Object v:list(threshold.get("levels")))levels.add(value(v));arg(command,"bar-levels",String.join(",",levels));command.add("--no-full");}
        else{for(String field:List.of("lower","upper","interval"))arg(command,field,value(threshold.get(field)));if(!Boolean.TRUE.equals(threshold.get("include_full")))command.add("--no-full");}
        Map<String,Object> phenotype=p.get("phenotype")==null?null:object(p.get("phenotype"));arg(command,"binary-target",phenotype!=null&&"BINARY".equals(phenotype.get("kind"))?"T":"F");
        if(phenotype!=null){if("FILE".equals(phenotype.get("source"))){arg(command,"pheno",input(root,"phenotype"));arg(command,"pheno-col",phenotype.get("column"));}if(phenotype.get("prevalence")!=null)arg(command,"prevalence",value(phenotype.get("prevalence")));}
        if(operation.equals("SCORE"))command.add("--no-regress");else{var regression=object(p.get("regression"));if(inputs.containsKey("covariates")){arg(command,"cov",input(root,"covariates"));arg(command,"cov-col",join(regression.get("covariates")));if(!list(regression.get("categorical")).isEmpty())arg(command,"cov-factor",join(regression.get("categorical")));}long perm=integer(regression.get("permutations"),0,10000000);if(perm>0)arg(command,"perm",perm);if(Boolean.TRUE.equals(regression.get("logistic_permutations")))command.add("--logit-perm");}
        return command;
    }
    private static String join(Object values)throws IOException {var result=new ArrayList<String>();for(Object v:list(values))result.add(text(v,"column"));return String.join(",",result);}
    static Map<String,String> environment(Path root,Map<String,Object> request)throws IOException {
        var env=new LinkedHashMap<String,String>();if(platform().equals("windows"))for(String name:List.of("SystemRoot","WINDIR","COMSPEC")){String value=System.getenv(name);if(value!=null)env.put(name,value);}
        Path temporary=root.resolve("worker/tmp");Files.createDirectory(temporary);env.put("TMPDIR",temporary.toString());env.put("TMP",temporary.toString());env.put("TEMP",temporary.toString());env.put("LANG","C");env.put("LC_ALL","C");env.put("OMP_NUM_THREADS",Long.toString(integer(object(request.get("parameters")).get("threads"),1,256)));return env;
    }
}
