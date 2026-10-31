/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
final class EagleTestRecipes {
    private EagleTestRecipes() { }
    static Map<String,Object> source(Path temporary,boolean reference)throws IOException {
        Path input=temporary.resolve("inputs-α-测");if(!Files.exists(input))Files.createDirectory(input);
        for(String name:List.of("cohort-target.vcf","target.vcf.gz","reference.vcf.gz","genetic-map.txt")){
            Path path=input.resolve(name);if(Files.exists(path))continue;
            try(var stream=EagleTestRecipes.class.getResourceAsStream("/org/jlinalg/omics/eagle-runtime-setup/eagle-runtime/health/"+name)){
                if(stream==null)throw new IOException("Missing synthetic fixture");Files.write(path,stream.readAllBytes());
            }
        }
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("operation","check");result.put("profile",EagleRecipe.PROFILE);
        result.put("mode",reference?"REFERENCE":"COHORT");result.put("resource_root",input.getFileName().toString());result.put("assembly","synthetic-no-biological-reference");result.put("input_review","Synthetic diploid fixture only; no biological eligibility claim");
        result.put("target",new LinkedHashMap<>(Map.of("path",reference?"target.vcf.gz":"cohort-target.vcf","format","vcf")));result.put("map","genetic-map.txt");
        result.put("region",new LinkedHashMap<>(Map.of("contig","22","chromosome",22,"chromosome_x",23,"start",1,"end",Integer.MAX_VALUE,"selection","literal-pos-input-subset")));
        var policies=new LinkedHashMap<String,Object>();policies.put("missing","KEEP");policies.put("partial_missing","ALLOW_ORIGINAL_WHOLE_CALL_MISSING");policies.put("keep_missing_ploidy_x",false);policies.put("outside_map","SOURCE_BOUNDARIES");
        if(reference){result.put("reference",Map.of("path","reference.vcf.gz","format","vcf"));policies.put("allow_reference_alt_swap",false);policies.put("unmatched","REFUSE");policies.put("expansion","REFUSE");policies.put("reference_fallback","REFUSE");policies.put("sample_overlap","REFUSE");}
        result.put("policies",policies);result.put("parameters",new LinkedHashMap<>(Map.of("threads",1,"conditioning_haplotypes",10000,"pbwt_iterations",1,"seed",0,"algorithm",reference?"REFERENCE_PBWT":"COHORT_PBWT_ONLY")));
        result.put("limits",new LinkedHashMap<>(Map.of("preset","bounded-vcf-v1")));result.put("process_limits",new LinkedHashMap<>(Map.of("preset","bounded-process-v1","wall_millis",60000,"address_space_bytes",1073741824L,"cpu_seconds",60,"file_bytes",16777216,"workspace_bytes",134217728)));
        return result;
    }
    static Map<String,Object> section(Map<String,Object> value,String key){
        @SuppressWarnings("unchecked") var result=(Map<String,Object>)value.get(key);return result;
    }
    static Path write(Path root,Map<String,Object> source)throws IOException {Path path=root.resolve("recipe-α-测.json");Files.writeString(path,EagleJson.json(source)+"\n");return path;}
}
