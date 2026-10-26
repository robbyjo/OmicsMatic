/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import static org.jlinalg.cli.StudyRareSupport.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.pipeline.*;
import org.jlinalg.settest.ConditionalSetNull;
import org.jlinalg.study.StudyBinaryRarePolicy;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Local participant-level conditional data, never relabeled as Gaussian summary scores. */
final class StudyBinaryRareScores {
    static final String KIND="jlinalg-conditional-binary-set-data-v1";
    Result execute(Context c)throws IOException{
        var config=policy(c);var association=map(StudyResultTables.analysis(c).get("association"));var binary=StudyBinaryRarePolicy.resolve(map(config.get("binary")),association);var dataset=datasets(c).get(0);var files=map(dataset.get("files"));var columns=map(dataset.get("columns"));var h=hypothesis(c);
        if(!"glm".equals(association.get("model"))||!"binomial".equals(association.get("family"))||association.containsKey("link")&&!"logit".equals(association.get("link")))return waiting(c,"binary-logit-model","Conditional binary rare sets require an explicitly declared glm/binomial/logit model");
        if(files.containsKey("grm")||files.containsKey("pedigree")||Boolean.TRUE.equals(map(dataset.get("metadata")).get("related_participants")))return waiting(c,"binary-relatedness","Conditional binary set allocation assumes independent Bernoulli participants. GRM/pedigree/related designs need a separately calibrated set analysis; fixed case counts do not adjust kinship.");
        var covColumns=map(columns.get("covariates"));String stratum=column(binary,"strata_column",covColumns),offsetName=column(binary,"offset_column",covColumns);var coefficients=map(binary.get("known_covariate_coefficients"));
        Set<String> declared=new HashSet<>(strings(h.get("covariates")));
        for(String key:coefficients.keySet())if(!declared.contains(key))return waiting(c,"binary-known-covariates","A known-effect slope must refer to a declared hypothesis covariate: "+key);
        for(String key:declared){String physical=text(covColumns.getOrDefault(key,key));if(!physical.equals(stratum)&&!coefficients.containsKey(key))return waiting(c,"binary-estimated-covariates","Conditional binary sets cannot ignore or estimate the declared slope for "+key+". Condition on its registered stratum or supply a scientifically justified fixed known slope, or import a calibrated model-specific set analysis.");}
        if(!offsetName.isBlank()||!coefficients.isEmpty())if(!Boolean.TRUE.equals(binary.get("fixed_effects"))||map(binary.get("known_effects_provenance")).isEmpty())return waiting(c,"binary-known-offset-provenance","Nonzero known-effect/offset contracts need binary.fixed_effects: true and known_effects_provenance. Same-outcome fitted coefficients do not establish a finite conditional null.");
        Path membership=StudyResultTables.artifact(c,"rare_variant_masks","mask-membership.tsv"),phenotype=StudyResultTables.artifact(c,"participant_qc","analysis-phenotype.tsv");
        if(membership==null||phenotype==null)return waiting(c,"binary-prefit-masks","Verified prefit-selected participants and outcome-independent masks must precede binary rare-set export");
        var mt=DelimitedData.readResult(membership);if(mt.rows().isEmpty())return emptyScores(c);var requested=new java.util.TreeSet<String>();var locus=new HashMap<String,String>();for(String[] row:mt.rows()){var v=Variant.parse(row[mt.column("variant_id")]);requested.add(v.toString());locus.put(v.chromosome()+":"+v.position(),v.toString());}
        Path matrix=c.project().resolve(text(files.get("matrix")));String sampleRole=files.containsKey("sample")?"sample":"samples";List<String> external=files.containsKey(sampleRole)?SampleFiles.read(c.project().resolve(text(files.get(sampleRole)))):null;
        VariantSource source=VariantSources.open(matrix,DataFormat.infer(matrix),external);var people=DelimitedData.read(phenotype);int idColumn=people.column(text(columns.getOrDefault("sample_id","id")));String outcomeKey=text(h.get("outcome")),outcome=text(map(columns.get("phenotypes")).getOrDefault(outcomeKey,outcomeKey));int outcomeColumn=people.column(outcome);if(!stratum.isBlank())people.column(stratum);if(!offsetName.isBlank())people.column(offsetName);
        var lookup=new HashMap<String,String[]>();for(String[] row:people.rows())if(lookup.put(row[idColumn],row)!=null)throw new IOException("Binary rare sets need unique prefit participant IDs");
        var ids=new ArrayList<String>();var order=new ArrayList<Integer>();var response=new ArrayList<Double>();var offsets=new ArrayList<Double>();var strata=new ArrayList<Integer>();var levels=new LinkedHashMap<String,Integer>();var allIds=source.metadata().sampleIds();
        Object caseValue=association.getOrDefault("case_value",1),controlValue=association.getOrDefault("control_value",0);
        try{
            for(int i=0;i<allIds.size();i++){String[] row=lookup.get(allIds.get(i));if(row==null)continue;double y=matches(row[outcomeColumn],caseValue)?1:matches(row[outcomeColumn],controlValue)?0:Double.NaN;if(!Double.isFinite(y))return waiting(c,"binary-outcome-coding","Binary rare outcomes must match the explicit case/control labels; no continuous outcome was dichotomized");
                double offset=offsetName.isBlank()?0:number(row[people.column(offsetName)],"known offset");for(var e:coefficients.entrySet()){String name=text(covColumns.getOrDefault(e.getKey(),e.getKey()));offset=Math.fma(((Number)e.getValue()).doubleValue(),number(row[people.column(name)],"known covariate"),offset);}
                if(!Double.isFinite(offset))throw new IllegalArgumentException("Nonfinite known binary predictor");String label=stratum.isBlank()?"__cohort__":row[people.column(stratum)];if(label.isBlank()||Set.of("NA","NaN",".").contains(label))throw new IllegalArgumentException("Missing conditional stratum");
                ids.add(allIds.get(i));order.add(i);response.add(y);offsets.add(offset);strata.add(levels.computeIfAbsent(label,ignored->levels.size()));
            }
        }catch(IllegalArgumentException invalid){return waiting(c,"binary-null-data",invalid.getMessage());}
        int n=ids.size(),cases=(int)response.stream().mapToDouble(Double::doubleValue).sum();if(n<2||cases==0||cases==n)return waiting(c,"binary-case-control-coverage","At least one case and one control must remain in each native binary rare-set cohort");
        double[] y=response.stream().mapToDouble(Double::doubleValue).toArray(),off=offsets.stream().mapToDouble(Double::doubleValue).toArray();int[] str=strata.stream().mapToInt(Integer::intValue).toArray();ConditionalSetNull.Model model;
        try{model=ConditionalSetNull.prepare(y,str,off,((Number)binary.get("maximum_work")).longValue());}catch(IllegalArgumentException invalid){return waiting(c,"binary-null-numerics",invalid.getMessage());}
        int informative=0;for(int s=0;s<levels.size();s++){int count=0,k=0;for(int i=0;i<n;i++)if(str[i]==s){count++;k+=(int)y[i];}if(k>0&&k<count)informative++;}if(informative==0)return waiting(c,"binary-informative-strata","No registered stratum contains both cases and controls; conditioned totals leave no association information");
        var filters=VariantFilterOptions.builder().excludeMonomorphic(false).minimumMac(((Number)config.get("minimum_mac")).doubleValue()).maximumMissingRate(((Number)config.get("maximum_marker_missing")).doubleValue()).minimumImputationQuality(config.containsKey("minimum_info")?((Number)config.get("minimum_info")).doubleValue():Double.NaN).build();
        double hweCutoff=((Number)config.get("minimum_hwe_p")).doubleValue();var variants=new java.util.TreeMap<String,double[]>();var observed=new HashSet<String>();var qc=new ArrayList<String[]>();int[] indices=order.stream().mapToInt(Integer::intValue).toArray();long examined=0,lowMac=0;
        try(var reader=source.open(indices)){
            VariantBlock block;while((block=reader.read(1024))!=null)for(var variant:block.variants()){
                examined++;String position=org.jlinalg.raremetal.RareMetalStudy.chromosome(variant.chromosome())+":"+variant.position();if(!locus.containsKey(position))continue;String key=Variant.parse(position+":"+variant.referenceAllele()+":"+variant.alternateAllele()).toString();if(!requested.contains(key))throw new IOException("Binary rare genotype alleles conflict with mask annotation at "+position);if(!observed.add(key))throw new IOException("Duplicate binary rare genotype variant: "+key);
                var result=VariantFilters.evaluate(variant,filters);var stats=result.statistics();var reasons=new ArrayList<>(result.reasons().stream().map(Enum::name).toList());double hwe=VariantQualityDiagnostics.calculate(variant,stats,filters.quality()).hweAll().pValue();if(hweCutoff>0&&(!Double.isFinite(hwe)||hwe<hweCutoff))reasons.add("HWE_BELOW_THRESHOLD_OR_UNAVAILABLE");
                double[] g=variant.dosages();double sum=0;int called=0;for(double v:g)if(Double.isFinite(v)){sum+=v;called++;}if(called==0)reasons.add("NO_CALLED_GENOTYPES");boolean keep=reasons.isEmpty();
                if(keep){if(variants.size()>=((Number)config.get("maximum_variants")).intValue()||(long)(variants.size()+1)*n>((Number)binary.get("maximum_cells")).longValue())return waiting(c,"binary-genotype-budget","Conditional genotype export exceeds maximum_variants/maximum_cells; no marker truncation was performed");double mean=sum/called;for(int i=0;i<g.length;i++)if(!Double.isFinite(g[i]))g[i]=mean;variants.put(key,g);if(stats.minorAlleleCount()<20)lowMac++;}
                qc.add(new String[]{key,String.valueOf(keep),String.join(";",reasons),String.valueOf(stats.minorAlleleCount()),String.valueOf(stats.minorAlleleFrequency()),String.valueOf(1-stats.missingRate()),finite(variant.imputationQuality()),finite(hwe),String.valueOf(n-called),String.valueOf(called),String.valueOf(sum)});
            }
        }
        FollowupSupport.table(c.outputDirectory().resolve("variant-qc.tsv"),List.of("variant_id","retained","reason","minor_allele_count","minor_allele_frequency","call_rate","imputation_quality","hwe_p","mean_imputed_samples","called_samples","alt_allele_count"),qc);
        var absent=new java.util.TreeSet<>(requested);absent.removeAll(observed);FollowupSupport.table(c.outputDirectory().resolve("unobserved-variants.tsv"),List.of("variant_id","disposition"),absent.stream().map(key->new String[]{key,"not_observed_unknown_genotype_coverage"}).toList());
        var header=List.of("sample_id","stratum_id","outcome","known_offset","profile_null_mean");var sampleRows=new ArrayList<String[]>();double[] means=model.nullMeans();
        for(int i=0;i<n;i++)sampleRows.add(new String[]{ids.get(i),String.valueOf(str[i]),String.valueOf((int)y[i]),String.valueOf(off[i]),String.valueOf(means[i])});FollowupSupport.table(c.outputDirectory().resolve("conditional-samples.tsv"),header,sampleRows);
        try(var writer=Files.newBufferedWriter(c.outputDirectory().resolve("conditional-genotypes.tsv"))){writer.write("variant_id");for(int i=0;i<n;i++)writer.write("\trow_"+i);writer.write('\n');for(var entry:variants.entrySet()){writer.write(entry.getKey());for(double g:entry.getValue())writer.write("\t"+g);writer.write('\n');}}
        var details=new LinkedHashMap<String,Object>();details.put("kind",KIND);details.put("dataset",dataset.get("id"));details.put("genome_build",dataset.get("genome_build"));details.put("trait_id",config.getOrDefault("trait_id",outcomeKey));details.put("case_value",caseValue);details.put("control_value",controlValue);details.put("analyzed_samples",n);details.put("n_cases",cases);details.put("n_controls",n-cases);details.put("strata",levels.size());details.put("informative_strata",informative);details.put("retained_variants",variants.size());details.put("retained_variants_with_mac_below_20",lowMac);details.put("unobserved_variants",absent.size());details.put("source_variants_examined",examined);details.put("single_variant_mac_cutoff_applied",false);details.put("binary_null",binary);details.put("declared_covariates",strings(h.get("covariates")).stream().sorted().toList());details.put("effective_policy",config);details.put("mask_membership_sha256",FollowupSupport.hash(membership));details.put("privacy","conditional-samples.tsv contains local participant outcomes, strata and offsets; conditional-genotypes.tsv contains their row-aligned genotypes. Neither file may enter remote assistance payloads");details.put("null_model","Independent Bernoulli outcomes; fixed cohort/stratum case counts; declared known log-odds offsets/slopes. Intercepts profile to those fixed totals and no unknown slope is estimated. Mean-imputed dosages are fixed observed predictors, with genotype/imputation uncertainty unpropagated.");
        details.put("method_report",StudyMethods.record("Local fixed-null binary conditional set data",Map.of("binary_null",binary,"sample_count",n,"case_count",cases,"control_count",n-cases,"set_qc",config,"genotype_missingness","fixed prefit-sample per-variant mean imputation; no single-marker MAC 20 filter"),list(config.get("references")),config.get("rationale"),List.of("conditional binary rare-set association")));write(c.outputDirectory().resolve("scores.yaml"),details);
        return new Result(State.COMPLETE,"Prepared private conditional binary set inputs with independent set eligibility",outputs(c),details);
    }
    private static String column(Map<String,Object> p,String key,Map<String,Object> names){return p.containsKey(key)?text(names.getOrDefault(text(p.get(key)),p.get(key))):"";}
    private static boolean matches(String value,Object code){String expected=String.valueOf(code).strip(),actual=value.strip();if(actual.equals(expected))return true;try{return Double.parseDouble(actual)==Double.parseDouble(expected);}catch(NumberFormatException invalid){return false;}}
    private static double number(String value,String label){double d;try{d=Double.parseDouble(value);}catch(NumberFormatException invalid){throw new IllegalArgumentException("Invalid "+label);}if(!Double.isFinite(d))throw new IllegalArgumentException("Nonfinite "+label);return d;}
    private static String finite(double value){return Double.isFinite(value)?String.valueOf(value):"NA";}
}
