/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Frozen, additive diploid score application; no target-dependent model changes. */
public final class StudyGeneticScorePolicy {
    private StudyGeneticScorePolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> input) {
        var p=new LinkedHashMap<String,Object>(input);
        var allowed=Set.of("sources","weights_genome_build","source_column","fid_column","iid_column","weight_columns","coefficient_scale","missing","frequency_provenance","minimum_observed_fraction","allele_alignment","alignment_rationale","score_center","score_scale","prediction_link","intercept","prediction_linear_column","prediction_rationale","maximum_model_variants","maximum_source_variants","maximum_samples","maximum_cells","maximum_weights_mib","maximum_metadata_mib","maximum_retained_mib","maximum_line_bytes","maximum_source_mib","maximum_decompressed_mib","maximum_header_mib","maximum_record_mib");
        if(!allowed.containsAll(p.keySet()))throw new IllegalArgumentException("Unknown risk_score.genotypes setting");
        text(p,"weights_genome_build");choice(p,"weights_genome_build",Set.of("GRCh37","GRCh38"));
        p.putIfAbsent("fid_column","fid");p.putIfAbsent("iid_column","iid");
        p.putIfAbsent("coefficient_scale","additive");p.putIfAbsent("missing","error");p.putIfAbsent("minimum_observed_fraction",.98);
        p.putIfAbsent("allele_alignment","reject_palindromic");p.putIfAbsent("score_center",0.0);p.putIfAbsent("score_scale",1.0);p.putIfAbsent("prediction_link","none");
        p.putIfAbsent("maximum_model_variants",1000000);p.putIfAbsent("maximum_source_variants",50000000);p.putIfAbsent("maximum_samples",1000000);p.putIfAbsent("maximum_cells",1000000000L);p.putIfAbsent("maximum_weights_mib",256);p.putIfAbsent("maximum_metadata_mib",1024);p.putIfAbsent("maximum_retained_mib",512);p.putIfAbsent("maximum_line_bytes",65536);
        p.putIfAbsent("maximum_source_mib",65536);p.putIfAbsent("maximum_decompressed_mib",131072);p.putIfAbsent("maximum_header_mib",64);p.putIfAbsent("maximum_record_mib",64);
        choice(p,"coefficient_scale",Set.of("additive","log_odds","odds_ratio","log_hazard","hazard_ratio"));choice(p,"missing",Set.of("error","reference_mean"));choice(p,"allele_alignment",Set.of("reject_palindromic","forward_declared"));choice(p,"prediction_link",Set.of("none","identity","logit"));
        for(String key:Set.of("source_column","fid_column","iid_column","frequency_provenance","alignment_rationale","prediction_linear_column","prediction_rationale"))if(p.containsKey(key))text(p,key);
        if(p.get("missing").equals("reference_mean"))text(p,"frequency_provenance");
        if(p.get("allele_alignment").equals("forward_declared"))text(p,"alignment_rationale");
        finite(p,"score_center");if(!(finite(p,"score_scale")>0))throw new IllegalArgumentException("Genetic score_scale must be positive");
        double observed=finite(p,"minimum_observed_fraction");if(observed<0||observed>1)throw new IllegalArgumentException("Genetic minimum_observed_fraction must be in [0,1]");
        if(!p.get("prediction_link").equals("none")){finite(p,"intercept");text(p,"prediction_rationale");}
        else if(p.containsKey("intercept")||p.containsKey("prediction_linear_column")||p.containsKey("prediction_rationale"))throw new IllegalArgumentException("Frozen prediction fields require an explicit identity or logit link");
        integer(p,"maximum_model_variants",1,1000000);integer(p,"maximum_source_variants",1,50000000);integer(p,"maximum_samples",1,1000000);integer(p,"maximum_cells",1,50000000000000L);integer(p,"maximum_weights_mib",1,4096);integer(p,"maximum_metadata_mib",1,8192);integer(p,"maximum_retained_mib",1,4096);integer(p,"maximum_line_bytes",64,1048576);
        integer(p,"maximum_source_mib",1,1048576);integer(p,"maximum_decompressed_mib",1,1048576);integer(p,"maximum_header_mib",1,1024);integer(p,"maximum_record_mib",1,1024);
        var columns=StudyIO.map(p.getOrDefault("weight_columns",Map.of()),"weight_columns");if(!Set.of("variant","chromosome","position","ea","oa","weight","ea_frequency","genome_build").containsAll(columns.keySet()))throw new IllegalArgumentException("Unknown genetic weight column role");for(String k:columns.keySet())text(columns,k);if(columns.values().stream().distinct().count()!=columns.size())throw new IllegalArgumentException("Genetic weight column mappings must be distinct");p.put("weight_columns",columns);
        var sources=new ArrayList<Object>();var ids=new java.util.HashSet<String>();
        for(Object raw:StudyIO.list(p.get("sources"),"genotype sources")){
            var source=StudyIO.map(raw,"genotype source");if(!Set.of("id","bfile","file","format","samples","genome_build","diploid_dosage_declaration").containsAll(source.keySet()))throw new IllegalArgumentException("Unknown genetic score source field");
            for(String k:source.keySet())text(source,k);
            for(String k:Set.of("id","genome_build"))text(source,k);
            if(source.containsKey("bfile")==source.containsKey("file"))throw new IllegalArgumentException("Genetic score source requires exactly one BED bfile prefix or VCF/BCF/BGEN file");
            if(source.containsKey("bfile")&&(source.containsKey("format")||source.containsKey("samples")||source.containsKey("diploid_dosage_declaration")))throw new IllegalArgumentException("File-format/sample/dosage declarations apply only to variant-file score sources");
            if(source.containsKey("file")){var format=format(source);if(source.containsKey("samples")&&format!=org.jlinalg.pipeline.DataFormat.BGEN)throw new IllegalArgumentException("External sample sidecars apply only to BGEN score sources");source.put("format",format.name());}
            if(source.get("id").toString().chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Genotype source IDs cannot contain control characters");
            if(!ids.add(source.get("id").toString()))throw new IllegalArgumentException("Genotype score source IDs must be unique");
            if(!source.get("genome_build").equals(p.get("weights_genome_build")))throw new IllegalArgumentException("Genotype and weight genome builds must match; no implicit liftover");sources.add(source);
        }
        if(sources.isEmpty()||sources.size()>100)throw new IllegalArgumentException("Require 1..100 frozen genotype score sources");if(sources.size()>1)text(p,"source_column");p.put("sources",sources);return p;
    }
    public static org.jlinalg.pipeline.DataFormat format(Map<String,Object> source){
        org.jlinalg.pipeline.DataFormat result;
        try{result=source.containsKey("format")?org.jlinalg.pipeline.DataFormat.valueOf(source.get("format").toString().toUpperCase(java.util.Locale.ROOT)):org.jlinalg.pipeline.DataFormat.infer(java.nio.file.Path.of(source.get("file").toString()));}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Genetic score variant file requires VCF, VCF_GZ, BCF or BGEN format",invalid);}
        if(!Set.of(org.jlinalg.pipeline.DataFormat.VCF,org.jlinalg.pipeline.DataFormat.VCF_GZ,org.jlinalg.pipeline.DataFormat.BCF,org.jlinalg.pipeline.DataFormat.BGEN).contains(result))throw new IllegalArgumentException("Genetic score variant file requires VCF, VCF_GZ, BCF or BGEN format");return result;
    }
    private static void text(Map<String,Object> p,String key){if(!(p.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("Genetic score "+key+" requires nonblank text");}
    private static void choice(Map<String,Object> p,String key,Set<String> choices){if(!choices.contains(p.get(key)))throw new IllegalArgumentException("Unsupported genetic score "+key);}
    private static double finite(Map<String,Object> p,String key){if(!(p.get(key) instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Genetic score "+key+" must be finite numeric");return n.doubleValue();}
    private static void integer(Map<String,Object> p,String key,long min,long max){double v=finite(p,key);if(v!=Math.rint(v)||v<min||v>max)throw new IllegalArgumentException("Genetic score "+key+" outside integer bounds");}
}
