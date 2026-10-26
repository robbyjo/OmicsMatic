/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyAnalysisReferences;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudyPoolingPolicy;

/** Bind an imported discovery pool to its registered scientific scope before validation. */
final class StudyDiscoveryScope {
    private StudyDiscoveryScope() { }
    record Audit(Map<String,Object> receipt, List<StudyNeed> needs) {
        Result waiting() {
            var details=new LinkedHashMap<String,Object>(receipt);
            details.put("needs",needs.stream().map(StudyNeed::document).toList());
            details.put("primary_selection_eligible",false);
            return new Result(State.WAITING_FOR_INPUT,"Resolve the supplied discovery pool's scope before validation",
                List.of("discovery-scope-audit.yaml","discovery-scope-compatibility.tsv","primary-result-selection.yaml"),details);
        }
    }

    static Audit assess(Context c, StudyPrimaryResults.Selection source, Map<String,Path> results,
            List<String> discovery, List<String> validation) throws IOException {
        var issues=new ArrayList<Object>();var missing=new LinkedHashSet<String>();var rows=new ArrayList<String[]>();
        var receipt=new LinkedHashMap<String,Object>();
        receipt.put("schema_version",1);receipt.put("source_sha256",source.source().sha256());
        receipt.put("source_metadata_sha256",source.receipt().get("source_metadata_sha256"));
        receipt.put("discovery_datasets",discovery);receipt.put("validation_datasets",validation);
        String metadataSnapshot=text(source.receipt().get("source_metadata_snapshot"));
        boolean external=!metadataSnapshot.equals("not_supplied");
        receipt.put("external_scope_checked",external);
        if(!external) {
            receipt.put("status","native_pool_scope_uses_registered_execution");
            return finish(c,receipt,issues,missing,rows,0,0);
        }
        Path metadataPath=c.outputDirectory().resolve(metadataSnapshot);
        if(!FollowupSupport.hash(metadataPath).equals(source.receipt().get("source_metadata_sha256")))
            throw new IOException("Frozen discovery metadata snapshot changed before scope validation");
        var metadata=StudyYaml.loadArtifact(metadataPath);var scope=map(metadata.get("scope"));
        if(!"discovery_meta_analysis".equals(metadata.get("kind"))||!"primary".equals(metadata.get("role")))
            issue(issues,"not_a_primary_discovery_pool");
        var expected=new LinkedHashSet<>(discovery);var validationIds=new LinkedHashSet<>(validation);
        if(expected.size()!=discovery.size()||validationIds.size()!=validation.size())issue(issues,"duplicate_role_dataset");
        var sharedIds=new LinkedHashSet<>(expected);sharedIds.retainAll(validationIds);
        if(!sharedIds.isEmpty())issue(issues,"discovery_validation_dataset_overlap");
        var datasets=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:list(c.study().get("datasets"))){var d=map(raw);datasets.put(text(d.get("id")),d);}
        var discoveryCohorts=new LinkedHashSet<String>();var validationCohorts=new LinkedHashSet<String>();
        for(String id:joined(discovery,validation)) {
            var d=datasets.get(id);
            if(d==null||"retired".equals(d.get("status"))){issues.add(Map.of("kind","unknown_or_retired_role_dataset","dataset",id));continue;}
            var cohorts=StudyPoolingAudit.cohorts(d);
            if(cohorts.isEmpty())issues.add(Map.of("kind","unresolved_cohort_membership","dataset",id));
            if(expected.contains(id))discoveryCohorts.addAll(cohorts);else validationCohorts.addAll(cohorts);
            if(StudyAnalysisReferences.isReference(d)) {
                if(!expected.contains(id))issues.add(Map.of("kind","pooled_reference_cannot_supply_validation_estimates","dataset",id));
                if(!metadata.get("artifact_id").equals(d.get("supplied_result_id")))issues.add(Map.of("kind","reference_owned_by_another_pool","dataset",id));
            }
        }
        var sharedCohorts=new LinkedHashSet<>(discoveryCohorts);sharedCohorts.retainAll(validationCohorts);
        if(!sharedCohorts.isEmpty())issue(issues,"discovery_validation_cohort_overlap");
        for(String left:discoveryCohorts)for(String right:validationCohorts) {
            boolean declared=false;
            for(Object raw:list(c.study().get("cohort_relationships"))) {
                var relationship=map(raw);var between=list(relationship.get("between"));
                if("retired".equals(relationship.get("status"))||!"independent".equals(relationship.get("type"))||!between.contains(left)||!between.contains(right))continue;
                boolean observed=relationship.get("shared_participants") instanceof Number n&&n.doubleValue()>0
                    ||relationship.get("overlap_fraction") instanceof Number fraction&&fraction.doubleValue()>0;
                if(!observed)declared=true;
            }
            if(!declared)issues.add(Map.of("kind","source_validation_independence_not_explicitly_declared","cohorts",List.of(left,right)));
        }
        boolean datasetScope=scope.containsKey("datasets"),cohortScope=scope.containsKey("cohorts");
        if(!datasetScope&&!cohortScope)issue(issues,"missing_discovery_membership_scope");
        if(datasetScope)membership(issues,rows,"datasets",list(scope.get("datasets")),expected);
        if(cohortScope)membership(issues,rows,"cohorts",list(scope.get("cohorts")),discoveryCohorts);
        Map<String,Object> hypothesis=Map.of();
        for(Object raw:list(c.study().get("hypotheses")))if(map(raw).get("id").equals(c.operation().get("hypothesis")))hypothesis=map(raw);
        boolean traitDeclared=false;
        for(String field:List.of("phenotype","outcome","exposure"))if(scope.containsKey(field)) {
            String value=label(scope.get(field));var targets=new LinkedHashSet<String>();
            if(field.equals("phenotype")){for(String target:List.of("outcome","exposure"))if(!label(hypothesis.get(target)).isBlank())targets.add(label(hypothesis.get(target)));}
            else if(!label(hypothesis.get(field)).isBlank())targets.add(label(hypothesis.get(field)));
            boolean matches=!value.isBlank()&&targets.contains(value);
            rows.add(new String[]{"source",field,value,String.join(" | ",targets),matches?"matches":"unresolved_or_conflicting"});
            if(!matches)issues.add(Map.of("kind","unresolved_or_conflicting_source_trait","field",field));
            traitDeclared=true;
        }
        if(!traitDeclared)issue(issues,"missing_source_trait_scope");
        var policy=StudyPoolingPolicy.resolve(map(StudyResultTables.analysis(c).get("pooling")));
        boolean strict=Boolean.TRUE.equals(policy.get("require_complete_contract"));
        Map<String,Object> sourceEffect;
        try{sourceEffect=StudyPoolingPolicy.effectContract(map(scope.get("effect_contract")));}
        catch(IllegalArgumentException invalid){sourceEffect=Map.of();issue(issues,"invalid_source_effect_contract");}
        for(String field:StudyPoolingPolicy.EFFECT_FIELDS) {
            String value=label(sourceEffect.get(field));
            if(value.isBlank())missing.add("source:"+field);
            for(String id:joined(discovery,validation)) {
                var d=datasets.get(id);if(d==null)continue;
                var effect=new LinkedHashMap<>(map(policy.get("effect_contract")));effect.putAll(map(d.get("effect_contract")));
                compare(issues,rows,policy,id,field,value,label(effect.get(field)));
            }
        }
        for(String field:List.of("species","modality","assay","tissue","genome_build","feature_id_type")) {
            String value=label(scope.get(field));
            if(scope.containsKey(field)&&value.isBlank())issues.add(Map.of("kind","invalid_source_context","field",field));
            for(String id:joined(discovery,validation)) {
                var d=datasets.get(id);if(d==null)continue;
                String target=label(field.equals("modality")?d.getOrDefault("modality",d.get("type")):d.get(field));
                compare(issues,rows,policy,id,field,value,target);
            }
        }
        boolean genetic="genomics".equals(StudyPoolingAudit.canonical("modality",label(scope.get("modality"))))
            ||joined(discovery,validation).stream().map(datasets::get).filter(java.util.Objects::nonNull).anyMatch(d->Set.of("gwas","genomics").contains(d.get("type")));
        if(genetic&&label(scope.get("genome_build")).isBlank())missing.add("source:genome_build");
        long sharedFeatures=0,comparedFields=0;
        if(genetic)for(String id:validation) {
            Path path=results.get(id);if(path==null)continue;
            var table=DelimitedData.readResult(path);var right=new LinkedHashMap<String,String[]>();
            for(String[] row:table.rows())if(right.putIfAbsent(row[table.column("feature_id")],row)!=null)throw new IOException("Ambiguous validation variant identity: "+id);
            var left=source.table();
            for(String[] row:left.rows()) {
                String feature=row[left.column("feature_id")];if(!source.probabilities().containsKey(feature))continue;
                for(String field:List.of("effect_allele","other_allele"))if(value(left,row,field).isBlank())missing.add("source:"+feature+":"+field);
                String[] other=right.get(feature);if(other==null)continue;sharedFeatures++;
                for(String field:List.of("chromosome","position","reference_allele","alternate_allele","effect_allele","other_allele","genome_build")) {
                    String a=value(left,row,field),b=value(table,other,field);
                    if(a.isBlank()||b.isBlank())continue;comparedFields++;
                    if(!variant(field,a).equals(variant(field,b)))issues.add(Map.of("kind","conflicting_source_validation_variant_coding","dataset",id,"feature_id",feature,"field",field));
                }
            }
        }
        if(strict&&!missing.isEmpty())issue(issues,"incomplete_source_effect_contract");
        receipt.put("source_artifact_id",metadata.get("artifact_id"));receipt.put("source_discovery_cohorts",List.copyOf(discoveryCohorts));
        receipt.put("validation_cohorts",List.copyOf(validationCohorts));
        return finish(c,receipt,issues,missing,rows,sharedFeatures,comparedFields);
    }

    private static Audit finish(Context c,Map<String,Object> receipt,List<Object> issues,Set<String> missing,
            List<String[]> rows,long shared,long fields)throws IOException {
        var needs=new ArrayList<StudyNeed>();
        if(!issues.isEmpty())needs.add(StudyNeed.review("discovery-scope:"+c.operation().get("hypothesis"),"Resolve supplied discovery scope",
            "See discovery-scope-audit.yaml. Bind the original discovery datasets/cohorts and trait, resolve coding/estimand differences or register separate compatible hypotheses. Validation samples cannot enter the discovery pool. No replacement discovery fit was selected.",true));
        receipt.put("status",issues.isEmpty()?receipt.getOrDefault("status",missing.isEmpty()?"declared_compatible":"partially_declared_not_certified"):"incompatible_or_unresolved");
        receipt.put("issues",issues);receipt.put("missing_source_effect_fields",List.copyOf(missing));
        receipt.put("shared_variant_rows",shared);receipt.put("compared_variant_fields",fields);
        receipt.put("needs",needs.stream().map(StudyNeed::document).toList());
        receipt.put("required_manuscript_caveat","Scope, cohort independence and effect contracts are source/study declarations requiring scientific review. Accepted membership does not independently reproduce the pool or prove participant disjointness, source completeness, calibration or causal independence. Missing source effect fields remain unresolved context; no automatic allele flip or cohort substitution was performed.");
        write(c.outputDirectory().resolve("discovery-scope-audit.yaml"),receipt);
        FollowupSupport.table(c.outputDirectory().resolve("discovery-scope-compatibility.tsv"),List.of("subject","field","source_value","registered_value","disposition"),rows);
        return new Audit(Map.copyOf(receipt),List.copyOf(needs));
    }
    private static void membership(List<Object> issues,List<String[]> rows,String field,List<?> supplied,Set<String> expected) {
        var found=new LinkedHashSet<String>();for(Object id:supplied)found.add(text(id));
        boolean same=found.size()==supplied.size()&&!found.isEmpty()&&found.equals(expected);
        rows.add(new String[]{"source",field,String.join(",",found),String.join(",",expected),same?"exact_membership":"mismatched_or_ambiguous_membership"});
        if(!same)issues.add(Map.of("kind","source_discovery_"+field+"_mismatch"));
    }
    private static void compare(List<Object> issues,List<String[]> rows,Map<String,Object> policy,String id,String field,String source,String registered) {
        String a=StudyPoolingAudit.canonical(field,source),b=StudyPoolingAudit.canonical(field,registered);
        boolean different=!a.isBlank()&&!b.isBlank()&&!a.equals(b),exception=map(policy.get("context_exceptions")).containsKey(field);
        rows.add(new String[]{id,field,source.isBlank()?"NA":source,registered.isBlank()?"NA":registered,different?exception?"registered_context_exception":"conflicting":"declared_or_unavailable"});
        if(different&&!exception)issues.add(Map.of("kind","source_registered_context_conflict","dataset",id,"field",field));
    }
    private static List<String> joined(List<String> left,List<String> right){var result=new ArrayList<>(left);result.addAll(right);return result;}
    private static void issue(List<Object> issues,String kind){issues.add(Map.of("kind",kind));}
    private static String label(Object value){return value instanceof String s&&!s.isBlank()&&s.length()<=4096&&!s.chars().anyMatch(Character::isISOControl)?s.strip():"";}
    private static String value(DelimitedData table,String[] row,String field){if(!table.header().contains(field))return "";String value=row[table.column(field)];return Set.of("","NA","NaN",".").contains(value)?"":value;}
    private static String variant(String field,String value){if(field.endsWith("allele"))return value.toUpperCase(java.util.Locale.ROOT);if(field.equals("chromosome"))return value.replaceFirst("(?i)^chr","");if(field.equals("position"))return new java.math.BigDecimal(value).stripTrailingZeros().toPlainString();return StudyPoolingAudit.canonical(field,value);}
}
