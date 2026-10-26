/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.pipeline.DataFormat;
import org.jlinalg.pipeline.VariantBlock;
import org.jlinalg.pipeline.VariantSource;
import org.jlinalg.pipeline.VariantSources;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Observed-call eligibility cannot be replaced by the completeness of imputed dosages. */
final class StudyGenotypeCallRate {
    private StudyGenotypeCallRate() { }
    record Selection(List<String[]> retained,List<String[]> exclusions,List<String> problems,Map<String,Object> summary,List<String> outputs,long examined,long missingQuality,boolean qualityAbsent) { }
    record Quality(String status,long examined,long missing,List<String> problems) { }
    static Quality quality(Map<String,Object> hypothesis,VariantSource main,List<String> mainIds,List<String> ids,Map<String,Object> options,Map<String,Object> policy)throws IOException {
        boolean required="imputed".equals(options.get("genotype_kind"))&&list(map(hypothesis.get("analysis")).get("goals")).stream().anyMatch(goal->List.of("association","rare_variant_analysis","subgroup_interactions").contains(text(goal)));
        if(!required)return new Quality("not_required_for_requested_scope",0,0,List.of());
        String status="absent";long examined=0,missing=0;var problems=new ArrayList<String>();var positions=new HashMap<String,Integer>();for(int i=0;i<mainIds.size();i++)positions.put(mainIds.get(i),i);
        try(var reader=main.open(ids.stream().mapToInt(positions::get).toArray())){VariantBlock block;outer:while((block=reader.read(128))!=null)for(var variant:block.variants()) {
            if(examined>=integer(policy,"maximum_source_variants")||(examined+1)*ids.size()>integer(policy,"maximum_source_cells")){status="unresolved";problems.add("Imputation-quality availability probe exhausted its registered bound");break outer;}examined++;
            if(Double.isFinite(variant.imputationQuality())){status="present_existence_probe";break outer;}missing++;
        }}
        if(status.equals("absent"))problems.add("Declared imputed association source contains no finite imputation-quality annotation; provide the source quality field before association");
        return new Quality(status,examined,missing,problems);
    }
    static Selection select(Context c,Map<String,Object> dataset,Map<String,Object> hypothesis,DelimitedData phenotype,List<String[]> rows,VariantSource main,List<String> mainIds,Map<String,Object> options,Map<String,Object> policy)throws IOException {
        var files=map(dataset.get("files"));boolean separate=files.containsKey("qc_genotypes");VariantSource panel=main;List<String> panelIds=mainIds;
        if(separate){Path path=c.project().resolve(text(files.get("qc_genotypes")));panel=VariantSources.open(path,DataFormat.infer(path),files.containsKey("qc_samples")?SampleFiles.read(c.project().resolve(text(files.get("qc_samples")))):null);panelIds=panel.metadata().sampleIds();}
        int idAt=phenotype.column(text(map(dataset.get("columns")).getOrDefault("sample_id","id")));var ids=rows.stream().map(row->row[idAt]).toList();var positions=new HashMap<String,Integer>();for(int i=0;i<panelIds.size();i++)if(positions.put(panelIds.get(i),i)!=null)throw new IOException("Duplicate QC panel sample ID");
        var problems=new ArrayList<String>();long examined=0,sites=0,qualityProbed=0,missingQuality=0;long[] called=new long[ids.size()];String qualityStatus="not_required_for_requested_scope";
        if(!positions.keySet().containsAll(ids))problems.add("QC hard-call panel is missing one or more selected analysis sample IDs; no population intersection was silently substituted");
        if(problems.isEmpty())try(var reader=panel.open(ids.stream().mapToInt(positions::get).toArray())) {
            VariantBlock block;outer:while((block=reader.read(128))!=null)for(var variant:block.variants()) {
                if(examined>=integer(policy,"maximum_source_variants")||(examined+1)*ids.size()>integer(policy,"maximum_source_cells")){problems.add("Initial observed sample call-rate source bound exhausted; no truncated panel selected participants");break outer;}examined++;
                if(!StudyGenotypeSampleQc.siteReason(variant,policy).isEmpty())continue;
                double[] values=variant.dosages();boolean valid=true;for(double value:values)if(!Double.isNaN(value)&&(!Double.isFinite(value)||value!=Math.rint(value)||value<0||value>2)){valid=false;break;}if(!valid)continue;
                sites++;for(int i=0;i<values.length;i++)if(Double.isFinite(values[i]))called[i]++;
            }
        }
        boolean observedSourceComplete=problems.isEmpty();
        if(problems.isEmpty()){var q=quality(hypothesis,main,mainIds,ids,options,policy);qualityStatus=q.status();qualityProbed=q.examined();missingQuality=q.missing();problems.addAll(q.problems());}
        var retained=new ArrayList<String[]>();var excluded=new ArrayList<String[]>();var records=new ArrayList<String[]>();double minimum=options.get("minimum_sample_call_rate") instanceof Number v?v.doubleValue():.98;
        for(int i=0;i<rows.size();i++){double rate=sites==0?Double.NaN:called[i]/(double)sites;boolean pass=problems.isEmpty()&&(sites==0||rate>=minimum);if(pass)retained.add(rows.get(i));else if(problems.isEmpty())excluded.add(new String[]{ids.get(i),"sample_call_rate",Double.toString(rate)});
            records.add(new String[]{ids.get(i),Long.toString(called[i]),Long.toString(sites),Double.isFinite(rate)?Double.toString(rate):"NA",problems.isEmpty()&&sites>0?Boolean.toString(pass):"NA",problems.isEmpty()?sites>0?"complete":"not_estimable_no_eligible_hard_call_sites":"unresolved"});}
        if(!problems.isEmpty())retained=new ArrayList<>(rows);
        Files.createDirectories(c.outputDirectory().resolve("genotype-qc"));FollowupSupport.table(c.outputDirectory().resolve("genotype-qc/initial-call-rates.tsv"),List.of("sample_id","observed_calls","eligible_sites","call_rate","passes_registered_threshold","status"),records);
        var summary=new LinkedHashMap<String,Object>();summary.put("panel_scope","Declared observed diploid biallelic autosomal hard-call SNPs, with registered region exclusions, before within-run sample/MAF/marker-call-rate/LD filters");summary.put("separate_hard_call_panel",separate);summary.put("examined_source_variants",examined);summary.put("eligible_call_rate_sites",sites);summary.put("source_complete",observedSourceComplete);summary.put("selection_complete",problems.isEmpty()&&sites>0);summary.put("threshold",minimum);summary.put("excluded_samples",problems.isEmpty()?excluded.size():0);summary.put("imputation_quality_status",qualityStatus);summary.put("imputation_quality_probe_variants",qualityProbed);summary.put("imputation_quality_probe_missing",missingQuality);summary.put("imputation_quality_interpretation","Availability probe only; a finite value does not mean every marker passes. Association consumers enforce registered per-variant quality filters.");summary.put("problems",problems);
        return new Selection(retained,problems.isEmpty()?excluded:List.of(),problems,summary,List.of("genotype-qc/initial-call-rates.tsv"),examined,missingQuality,qualityStatus.equals("absent"));
    }
    private static long integer(Map<String,Object> p,String key){return ((Number)p.get(key)).longValue();}
}
