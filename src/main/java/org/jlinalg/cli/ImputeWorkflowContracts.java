/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Closed autosomal IMPUTE2 requests. Editions 4 and 5 are separate capabilities. */
final class ImputeWorkflowContracts {
    private ImputeWorkflowContracts() { }
    static final String PROFILE="impute2-2.3.2-static-autosomal-v1";
    static final String COMPLETE="COMPLETE_SCOPED_IMPUTE2_OPERATION";
    static final String ACTION="PRIVATE LICENSED CAPTURE: keep the unmodified Oxford runtime and raw vendor traces private under your accepted terms. Read WARNINGS.md, input-audit.json and scientific-audit.json. Confirm assembly, autosomal diploidy, column identities, cohort eligibility, reference suitability, strand alignment and intended parameters/QC. Allele1 is the file's second allele, not necessarily genome ALT. Source INFO is not Minimac Rsq. All-zero GEN codes are missing, never zero dosage; probabilities are not normalized by Java. This operation does not establish biological accuracy, a whole-paper reproduction or catalog admission. SKIP3: restore the exact permitted private runtime/input or retain the skipped dependent analysis. FAIL2: preserve failed/partial evidence, fix the named cause and retry NEW output. SHA256 checks integrity, not execution authentication.";
    static Set<String> roles(String operation)throws IOException {
        return switch(operation) {
            case "PREPHASE" -> Set.of("map","study_genotypes");
            case "IMPUTE_GENOTYPES" -> Set.of("map","reference_haps","reference_legend","study_genotypes","strand");
            case "IMPUTE_PREPHASED" -> Set.of("map","reference_haps","reference_legend","study_haps","strand");
            case "MERGE_REFERENCE" -> Set.of("map","reference_haps","reference_legend","second_reference_haps","second_reference_legend");
            default -> throw new IOException("Unsupported IMPUTE operation; no version or method substitution");
        };
    }
    static Map<String,Object> validate(Map<String,Object> raw,Path source)throws IOException {
        keys(raw,Set.of("schema_version","mode","profile","operation","runtime_home","out","assembly","chromosome","inputs","options","limits","license_accepted"));
        integer(raw.get("schema_version"),1,1);
        require("RUN".equals(raw.get("mode"))&&PROFILE.equals(raw.get("profile")),"Require schema1 RUN and exact IMPUTE2 v2.3.2 profile; IMPUTE4/5 are unsupported");
        String operation=text(raw.get("operation"),"operation");Set<String> roles=roles(operation);
        require(Boolean.TRUE.equals(raw.get("license_accepted")),"Require license_accepted=true after reviewing the pinned archive's bundled Oxford terms; the analysis never accepts terms or installs for you");
        literal(text(raw.get("assembly"),"assembly"),"assembly");
        String chromosome=text(raw.get("chromosome"),"chromosome");
        require(chromosome.matches("(?:[1-9]|1[0-9]|2[0-2])"),"Only explicit human autosomes 1..22 with diploid samples are supported; X/PAR/other ploidies are not inferred");
        var result=new LinkedHashMap<>(raw);
        for(String key:List.of("runtime_home","out"))result.put(key,resolve(source,text(raw.get(key),key)).toString());
        var inputs=object(raw.get("inputs"));require(inputs.keySet().equals(roles),"Declare exactly input roles "+roles);var normalized=map();
        for(String role:roles)normalized.put(role,resolve(source,text(inputs.get(role),role)).toString());result.put("inputs",normalized);
        var options=object(raw.get("options"));var expected=new java.util.HashSet<>(Set.of("seed","Ne","interval_start","interval_end","parameter_policy","sample_identity_policy","input_reviewed","genotype_audit"));
        if(operation.equals("IMPUTE_GENOTYPES"))expected.add("typed_genotype_policy");
        require(options.keySet().equals(expected),"Declare exactly scientific options "+expected+"; arbitrary upstream flags are not accepted");
        integer(options.get("seed"),1,Integer.MAX_VALUE);integer(options.get("Ne"),1,1000000000);
        long start=integer(options.get("interval_start"),1,Integer.MAX_VALUE),end=integer(options.get("interval_end"),1,Integer.MAX_VALUE);
        require(start<=end,"Interval start exceeds end");
        require("original-v2.3.2-defaults".equals(options.get("parameter_policy")),"Only original v2.3.2 defaults are qualified; no MCMC or template reduction is inferred");
        require("unlabelled-ordered-columns".equals(options.get("sample_identity_policy")),"Declare unlabelled-ordered-columns: the format alone does not identify people or independence");
        require(Boolean.TRUE.equals(options.get("input_reviewed")),"Confirm input_reviewed=true after checking assembly/ploidy/identity/strand/reference suitability");
        choice(options,"genotype_audit",Set.of("summary","rounding-bounds"));
        if(operation.equals("IMPUTE_GENOTYPES"))choice(options,"typed_genotype_policy",Set.of("preserve-input","reimpute-all","fill-missing-at-source-threshold"));
        var defaults=map("maximum_input_bytes",4294967296L,"maximum_member_bytes",4294967296L,"maximum_output_bytes",8589934592L,"maximum_files",128L,"timeout_seconds",3600L,"maximum_rows",10000000L,"maximum_samples",100000L,"maximum_genotypes",100000000L,"maximum_line_characters",16777216L,"maximum_decompressed_bytes",8589934592L);
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();keys(limits,defaults.keySet());var bounded=map();
        for(var e:defaults.entrySet())bounded.put(e.getKey(),integer(limits.getOrDefault(e.getKey(),e.getValue()),1,Long.parseLong(e.getValue().toString())));
        result.put("limits",bounded);return result;
    }
    static Path resolve(Path source,String value){return source.getParent().resolve(value).toAbsolutePath().normalize();}
    static long cap(Map<String,Object> request,String key)throws IOException{return number(object(request.get("limits")),key);}
    static String operation(Map<String,Object> request)throws IOException{return text(request.get("operation"),"operation");}
    static Path input(Path root,String role){return root.resolve("inputs/"+role);}
}
