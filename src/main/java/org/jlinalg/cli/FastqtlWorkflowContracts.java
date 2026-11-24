/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Closed prepared-data interface to one named distribution edition, not FastQTL 2.165. */
final class FastqtlWorkflowContracts {
    private FastqtlWorkflowContracts() { }
    static final String PROFILE="fastqtl-2.184-v7-ubuntu-noble-prepared-v1";
    static final String COMPLETE="COMPLETE_SCOPED_FASTQTL_OPERATION";
    static final String ACTION="REVIEW REQUIRED: this is Ubuntu 2.184+v7+dfsg-4build3 (v2.184_gtex), not historical 2.165 or QTLtools. Read WARNINGS.md, input-audit.json, scientific-audit.json and original worker logs. Supply prepared single-chromosome biallelic SNV VCF/BED with matching TBI indexes and identical sample sets; numeric covariates are optional. Confirm assembly, BED start+1 anchors, sample identities, unrelatedness/model suitability and all upstream preprocessing. Original float arithmetic, DS-before-GT selection, mean imputation, normalization, covariate correction, rounded-dosage MAF and beta fitting remain upstream. No Java repair, FDR/q-values, automatic TSS/peak conversion, PEER fitting, conditional mapping or whole-paper reproduction is supplied. SKIP3: install/restore the named permitted runtime or missing inputs, or retain the skipped analysis. FAIL2: retain raw/partial output, fix the named cause and retry a NEW output. Hashes check integrity, not authentication.";
    static Map<String,Object> validate(Map<String,Object> raw,Path source)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","runtime_home","out","assembly","region","inputs","options","limits"));
        integer(raw.get("schema_version"),1,1);
        require("RUN".equals(raw.get("mode")),"Require RUN or a fresh VERIFY/REPLAY request");
        if(!PROFILE.equals(raw.get("profile")))throw new FastqtlWorkflowRuntime.Unavailable("Unsupported FastQTL edition/profile; restore the exact requested edition. No 2.165/2.184 substitution");
        String operation=text(raw.get("operation"),"operation");require(Set.of("NOMINAL","PERMUTATION").contains(operation),"Only prepared NOMINAL and PERMUTATION are qualified");
        String assembly=text(raw.get("assembly"),"assembly");require(assembly.matches("[A-Za-z0-9_.-]{1,128}"),"Declare an explicit assembly label");
        String region=text(raw.get("region"),"region");require(region.matches("[A-Za-z0-9_]+:[1-9][0-9]*-[1-9][0-9]*"),"Region must be literal chromosome:start-end (1-based inclusive)");
        String[] r=region.split("[:-]");long start=Long.parseLong(r[1]),end=Long.parseLong(r[2]);require(start<=end&&end<=2000000000L,"Invalid/beyond-capacity region");
        var result=new LinkedHashMap<>(raw);
        for(String key:List.of("runtime_home","out"))result.put(key,resolve(source,text(raw.get(key),key)).toString());
        var inputs=object(raw.get("inputs"));var roles=new HashSet<>(Set.of("vcf","vcf_index","phenotypes","phenotype_index"));if(inputs.containsKey("covariates"))roles.add("covariates");require(inputs.keySet().equals(roles),"Declare exactly VCF, phenotype BED, both indexes and optional numeric covariates");
        var normalized=map();for(String role:new TreeSet<>(roles))normalized.put(role,resolve(source,text(inputs.get(role),role)).toString());result.put("inputs",normalized);
        var options=object(raw.get("options"));var expected=new HashSet<>(Set.of("seed","window","normal","maf_threshold","ma_sample_threshold","anchor_policy","input_reviewed"));expected.add(operation.equals("NOMINAL")?"threshold":"permutations");require(options.keySet().equals(expected),"Declare exactly options "+expected+"; arbitrary flags are not accepted");
        integer(options.get("seed"),1,Integer.MAX_VALUE);long window=integer(options.get("window"),0,100000000);require(end+window<=Integer.MAX_VALUE,"Expanded genotype interval exceeds upstream integer coordinates");
        require(options.get("normal") instanceof Boolean,"normal must explicitly be true or false");double maf=finite(options.get("maf_threshold"));require(maf>=0&&maf<.5,"maf_threshold must lie in [0,0.5)");integer(options.get("ma_sample_threshold"),0,100000);
        require("bed-start-plus-one".equals(options.get("anchor_policy"))&&Boolean.TRUE.equals(options.get("input_reviewed")),"Declare anchor_policy=bed-start-plus-one and input_reviewed=true; coordinates and scientific eligibility are not inferred");
        if(operation.equals("NOMINAL")){double threshold=finite(options.get("threshold"));require(threshold>0&&threshold<=1,"Nominal threshold must lie in (0,1]");}
        else {
            require(options.get("permutations") instanceof List<?>,"permutations requires an integer array");var p=(List<?>)options.get("permutations");require(p.size()>=1&&p.size()<=3,"Use original fixed [N], adaptive [better,max] or [min,better,max] permutations");
            for(Object v:p)integer(v,1,1000000);if(p.size()>1)require(integer(p.get(0),1,1000000)<=integer(p.get(p.size()-1),1,1000000),"Permutation minimum/better exceeds maximum");
        }
        var defaults=map("maximum_input_bytes",4294967296L,"maximum_member_bytes",4294967296L,"maximum_output_bytes",8589934592L,"maximum_files",256L,"timeout_seconds",3600L,"maximum_rows",1000000L,"maximum_samples",100000L,"maximum_values",100000000L,"maximum_line_characters",16777216L,"maximum_decompressed_bytes",8589934592L);
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();keys(limits,defaults.keySet());var bounded=map();for(var e:defaults.entrySet())bounded.put(e.getKey(),integer(limits.getOrDefault(e.getKey(),e.getValue()),1,Long.parseLong(e.getValue().toString())));result.put("limits",bounded);return result;
    }
    static double finite(Object value)throws IOException {double result=decimal(value).doubleValue();require(Double.isFinite(result),"Require finite numeric option");return result;}
    static Path resolve(Path source,String value){return source.getParent().resolve(value).toAbsolutePath().normalize();}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static Path input(Path root,String role){return root.resolve("inputs/"+switch(role){case "vcf"->"genotypes.vcf.gz";case "vcf_index"->"genotypes.vcf.gz.tbi";case "phenotypes"->"phenotypes.bed.gz";case "phenotype_index"->"phenotypes.bed.gz.tbi";case "covariates"->"covariates.txt";default->throw new IllegalArgumentException(role);});}
}
