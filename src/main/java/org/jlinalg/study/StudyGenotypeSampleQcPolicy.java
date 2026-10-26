/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prospectively declared common-autosomal hard-call sample QC, distinct from marker testing. */
public final class StudyGenotypeSampleQcPolicy {
    private StudyGenotypeSampleQcPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> configured) {
        var p=new LinkedHashMap<>(configured);Set<String> allowed=Set.of("enabled","genome_build","rationale","allow_unspecified_hard_calls","minimum_maf","minimum_marker_call_rate","minimum_panel_markers","panel_markers","seed","ld_window_bp","ld_r_squared","minimum_ld_samples","minimum_pair_markers","minimum_pair_heterozygotes","kinship_threshold","relatedness_action","maximum_excluded_fraction","heterozygosity_z","pca_components","include_pcs","pc_prefix","maximum_samples","maximum_source_cells","maximum_source_variants","maximum_genotype_cells","maximum_pair_word_work","maximum_pca_work","excluded_regions");
        var fields=new java.util.HashSet<>(allowed);fields.addAll(Set.of("source","pca_source","dataset_sources","maximum_import_samples","maximum_import_cells","maximum_import_bytes"));
        for(String key:p.keySet())if(!fields.contains(key))throw new IllegalArgumentException("Unknown genotype_sample_qc setting: "+key);
        for(String key:List.of("source","pca_source"))if(p.containsKey(key)&&!Set.of("native","supplied").contains(String.valueOf(p.get(key))))throw new IllegalArgumentException(key+" must be native or supplied");
        p.putIfAbsent("source","native");p.putIfAbsent("pca_source",p.get("source"));
        p.putIfAbsent("maximum_import_samples",1000000);p.putIfAbsent("maximum_import_cells",50000000L);p.putIfAbsent("maximum_import_bytes",1073741824L);
        var perDataset=StudyIO.optionalMap(p.get("dataset_sources"),"genotype_sample_qc.dataset_sources");for(var entry:perDataset.entrySet()){text(entry.getKey(),"QC dataset ID",100);var choice=StudyIO.map(entry.getValue(),"QC dataset sources");if(choice.isEmpty()||!Set.of("source","pca_source").containsAll(choice.keySet()))throw new IllegalArgumentException("Dataset QC sources require source and/or pca_source selectors");for(var v:choice.values())if(!Set.of("native","supplied").contains(String.valueOf(v)))throw new IllegalArgumentException("Dataset QC sources must be native or supplied");String sample=String.valueOf(choice.getOrDefault("source",p.get("source"))),pc=String.valueOf(choice.getOrDefault("pca_source",choice.containsKey("source")?sample:p.get("pca_source")));if(sample.equals("native")&&pc.equals("supplied"))throw new IllegalArgumentException("Native sample QC reuses existing PCs through declared phenotype covariates");}p.put("dataset_sources",perDataset);
        for(String key:List.of("source","pca_source"))if(!Set.of("native","supplied").contains(p.get(key)))throw new IllegalArgumentException(key+" must be native or supplied");
        if("native".equals(p.get("source"))&&"supplied".equals(p.get("pca_source")))throw new IllegalArgumentException("Native sample QC with supplied PCs uses ordinary declared phenotype covariates and pca_components: 0; the scoped supplied-PC adapter requires supplied sample QC");
        p.putIfAbsent("enabled",!configured.isEmpty());p.putIfAbsent("allow_unspecified_hard_calls",false);
        p.putIfAbsent("minimum_maf",.05);p.putIfAbsent("minimum_marker_call_rate",.98);p.putIfAbsent("minimum_panel_markers",1000);p.putIfAbsent("panel_markers",10000);p.putIfAbsent("seed",17);
        p.putIfAbsent("ld_window_bp",500000);p.putIfAbsent("ld_r_squared",.2);p.putIfAbsent("minimum_ld_samples",10);
        p.putIfAbsent("minimum_pair_markers",1000);p.putIfAbsent("minimum_pair_heterozygotes",100);p.putIfAbsent("kinship_threshold",Math.pow(2,-3.5));p.putIfAbsent("relatedness_action","review");p.putIfAbsent("maximum_excluded_fraction",.1);p.putIfAbsent("heterozygosity_z",4.0);
        p.putIfAbsent("pca_components",10);p.putIfAbsent("include_pcs",false);p.putIfAbsent("pc_prefix","GPC");p.putIfAbsent("maximum_samples",2000);p.putIfAbsent("maximum_source_cells",1000000000L);p.putIfAbsent("maximum_source_variants",2000000);p.putIfAbsent("maximum_genotype_cells",25000000L);p.putIfAbsent("maximum_pair_word_work",1000000000L);p.putIfAbsent("maximum_pca_work",250000000L);p.putIfAbsent("excluded_regions",List.of());
        for(String flag:List.of("enabled","include_pcs","allow_unspecified_hard_calls"))if(!(p.get(flag) instanceof Boolean))throw new IllegalArgumentException(flag+" must be boolean");
        if(Boolean.TRUE.equals(p.get("enabled"))){text(p.get("rationale"),"genotype_sample_qc.rationale",4000);if(!Set.of("GRCh37","GRCh38").contains(String.valueOf(p.get("genome_build"))))throw new IllegalArgumentException("Genotype sample QC requires declared GRCh37 or GRCh38 coordinates");}
        if(!Set.of("review","exclude","model").contains(p.get("relatedness_action")))throw new IllegalArgumentException("relatedness_action must be review, exclude or model");
        number(p,"minimum_maf",.000001,.499999);number(p,"minimum_marker_call_rate",.000001,1);number(p,"ld_r_squared",0,.999999);number(p,"kinship_threshold",0,.499999);number(p,"maximum_excluded_fraction",0,.999999);number(p,"heterozygosity_z",.000001,100000);
        for(String key:List.of("minimum_panel_markers","panel_markers","minimum_pair_markers","minimum_pair_heterozygotes","minimum_ld_samples"))integer(p,key,1,10000000);
        integer(p,"seed",0,Integer.MAX_VALUE);integer(p,"ld_window_bp",1,1000000000);integer(p,"pca_components",0,100);integer(p,"maximum_samples",3,100000);
        integer(p,"maximum_source_variants",1,20000000);
        integer(p,"maximum_import_samples",3,10000000);integer(p,"maximum_import_cells",1,9007199254740991L);integer(p,"maximum_import_bytes",1,9007199254740991L);
        for(String key:List.of("maximum_source_cells","maximum_genotype_cells","maximum_pair_word_work","maximum_pca_work"))integer(p,key,1,9007199254740991L);
        if(((Number)p.get("minimum_panel_markers")).intValue()>((Number)p.get("panel_markers")).intValue())throw new IllegalArgumentException("Panel cap is below the registered minimum");
        if(Boolean.TRUE.equals(p.get("include_pcs"))&&((Number)p.get("pca_components")).intValue()==0)throw new IllegalArgumentException("include_pcs requires positive pca_components");
        String prefix=text(p.get("pc_prefix"),"pc_prefix",100);if(!prefix.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IllegalArgumentException("PC prefix must be a safe column identifier");
        var regions=new ArrayList<Object>();for(Object raw:StudyIO.list(p.get("excluded_regions"),"excluded_regions")) {
            var region=StudyIO.map(raw,"excluded region");if(!Set.of("chromosome","start","end","rationale").containsAll(region.keySet()))throw new IllegalArgumentException("Unknown excluded region field");
            String chr=text(region.get("chromosome"),"region.chromosome",2);if(!chr.matches("[1-9]|1[0-9]|2[0-2]"))throw new IllegalArgumentException("Excluded QC regions must be human autosomes");integer(region,"start",1,1000000000);integer(region,"end",1,1000000000);if(((Number)region.get("end")).longValue()<((Number)region.get("start")).longValue())throw new IllegalArgumentException("Invalid excluded interval");text(region.get("rationale"),"region.rationale",4000);regions.add(region);
        }p.put("excluded_regions",regions);return p;
    }
    public static List<String> pcCovariates(Map<String,Object> policy) {
        if(!Boolean.TRUE.equals(policy.get("enabled"))||!Boolean.TRUE.equals(policy.get("include_pcs")))return List.of();
        var result=new ArrayList<String>();for(int k=1;k<=((Number)policy.get("pca_components")).intValue();k++)result.add(policy.get("pc_prefix")+Integer.toString(k));return result;
    }
    public static Map<String,Object> forDataset(Map<String,Object> policy,String dataset) {
        var p=new LinkedHashMap<>(policy);var choice=StudyIO.optionalMap(StudyIO.optionalMap(policy.get("dataset_sources"),"dataset_sources").get(dataset),"dataset QC source");
        p.putAll(choice);if(choice.containsKey("source")&&!choice.containsKey("pca_source"))p.put("pca_source",choice.get("source"));return resolve(p);
    }
    private static String text(Object value,String name,int max){if(!(value instanceof String s)||s.isBlank()||s.length()>max||s.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid "+name);return s;}
    private static double number(Map<String,Object> p,String name,double min,double max){if(!(p.get(name) instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IllegalArgumentException("Invalid "+name);return n.doubleValue();}
    private static void integer(Map<String,Object> p,String name,long min,long max){double value=number(p,name,min,max);if(value!=Math.rint(value))throw new IllegalArgumentException(name+" must be integral");}
}
