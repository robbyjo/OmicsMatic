/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jlinalg.pipeline.HumanPloidyProfile;

/** Explicit one-contrast human chromosome model; no sex inference or automatic pooling. */
public final class StudyHumanPloidyPolicy {
    private StudyHumanPloidyPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> association) {
        var input=StudyIO.optionalMap(association.get("human_ploidy"),"association.human_ploidy");if(input.isEmpty())return Map.of();var p=new LinkedHashMap<String,Object>(input);
        for(String key:p.keySet())if(!Set.of("region","genome_build","haploid_source_scale","invalid_calls","unknown_samples").contains(key))throw new IllegalArgumentException("Unknown human_ploidy field: "+key);
        p.putIfAbsent("haploid_source_scale","observed");p.putIfAbsent("invalid_calls","error");p.putIfAbsent("unknown_samples","error");
        var profile=profile(p);if(profile.haploid()&&association.get("minimum_hwe_p") instanceof Number n&&n.doubleValue()>0)throw new IllegalArgumentException("Diploid HWE cannot filter a haploid contrast");
        if(association.containsKey("model")&&!Set.of("ols","glm").contains(association.get("model")))throw new IllegalArgumentException("Human ploidy contrasts require independent OLS/GLM");
        if("human_autosomes".equals(association.get("chromosome_scope")))throw new IllegalArgumentException("Choose human_ploidy.region instead of a second chromosome_scope filter");return p;
    }
    public static HumanPloidyProfile profile(Map<String,Object> p){return new HumanPloidyProfile(value(p,"region"),value(p,"genome_build"),value(p,"haploid_source_scale"),value(p,"invalid_calls"),value(p,"unknown_samples"));}
    public static void dataset(Map<String,Object> dataset,Map<String,Object> p){if(p.isEmpty())return;if(!Set.of("gwas","genomics").contains(String.valueOf(dataset.get("type")))||!Set.of("human","homo sapiens","9606").contains(String.valueOf(dataset.get("species")).toLowerCase(java.util.Locale.ROOT)))throw new IllegalArgumentException("Human ploidy requires an explicitly human genotype dataset");if(!p.get("genome_build").equals(dataset.get("genome_build")))throw new IllegalArgumentException("Human ploidy and dataset genome_build must match explicitly");if(!StudyIO.optionalMap(dataset.get("files"),"dataset.files").containsKey("ploidy_samples"))throw new IllegalArgumentException("Human ploidy requires dataset.files.ploidy_samples");}
    private static String value(Map<String,Object> p,String key){if(!(p.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("human_ploidy."+key+" must be supplied");return s;}
}
