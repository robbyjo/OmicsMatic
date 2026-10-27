/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyAnalysisReferences;
import org.jlinalg.study.StudyNetworkPolicy;
import org.jlinalg.study.StudyNetworkReuse;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyPoolingPolicy;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Checks declared scientific scope without requiring unavailable upstream raw data. */
final class StudySuppliedScope {
    private static final Set<String> CONTEXT_ONLY=Set.of("protein_structure","variant_annotation","variant_scoring","literature_search","source_discovery","bioinformatics_investigation");
    private static final Set<String> EXTERNAL_POPULATION=Set.of("mendelian_randomization","colocalization","fine_mapping","risk_score_evaluation","risk_score_related_phenotypes");
    private static final Set<String> EXACT_POPULATION=Set.of("meta_analysis","discovery_meta_analysis","discovery_replication","effect_comparison","cohort_overlap","rare_cohort_overlap","heterogeneity_review","rare_variant_analysis","rare_set_omnibus","subgroup_interactions","gene_ontology","pathway_enrichment","gwas_enrichment","gwas_diagnostics","network_preservation","ranked_gsea","network_modules","module_gene_ontology");
    private static final Set<String> ROW_POPULATION=Set.of("subgroup_interactions","cohort_overlap","rare_cohort_overlap");
    private StudySuppliedScope() { }

    static final class Assessment {
        final Map<String,Object> report=new LinkedHashMap<>();
        final List<Object> issues=new ArrayList<>();
        final Set<String> missing=new LinkedHashSet<>();
        final List<String[]> rows=new ArrayList<>();
        final Map<String,Object> definition;
        final Set<String> allowedRelated;
        final String target;
        Assessment(Map<String,Object> d,Set<String> related,String expected){definition=d;allowedRelated=related;target=expected;}
        boolean compatible(){return issues.isEmpty();}
        void issue(String kind,String field){var issue=Map.of("kind",kind,"field",field);if(!issues.contains(issue))issues.add(issue);}
        void finish(Context c)throws IOException {
            report.put("schema_version",1);report.put("artifact_id",definition.get("id"));report.put("kind",definition.get("kind"));
            report.put("status",compatible()?missing.isEmpty()?"declared_compatible":"partially_declared_not_certified":"incompatible_or_unresolved");
            report.put("issues",issues);report.put("missing_context",List.copyOf(missing));
            report.put("required_manuscript_caveat","Scope checks compare supplied declarations with the registered target and available metadata. Missing context is not inferred, and compatible declarations do not independently reproduce source analyses or establish calibration, participant QC or scientific validity. Original methods, provenance and free-text limitations require local review.");
            write(c.outputDirectory().resolve("supplied-scope-validation.yaml"),report);
            FollowupSupport.table(c.outputDirectory().resolve("supplied-scope-comparisons.tsv"),List.of("field","source_value","registered_value","disposition"),rows);
        }
    }

    static Assessment assess(Context c,Map<String,Object> definition)throws IOException {
        var h=hypothesis(c);var analysis=map(h.get("analysis"));var definitions=StudySuppliedResultPolicy.resolve(analysis);
        var anchor=definitions.stream().filter(d->"primary".equals(d.get("role"))&&"meta_analysis".equals(d.get("kind"))).findFirst().orElse(Map.of());
        String kind=text(definition.get("kind"));var scope=map(definition.get("scope"));var related=new LinkedHashSet<String>();
        for(Object raw:list(map(analysis.get("risk_score")).get("related_phenotypes")))related.add(label(map(raw).get("column")));
        String target=label(h.get("outcome")),origin=target.isBlank()?"unavailable":"hypothesis.outcome";
        if(kind.startsWith("risk_score")&&!label(map(analysis.get("risk_score")).get("outcome")).isBlank()){target=label(map(analysis.get("risk_score")).get("outcome"));origin="risk_score.outcome";}
        if(target.isBlank()&&!anchor.isEmpty()){target=trait(map(anchor.get("scope")));origin=target.isBlank()?"unavailable":"registered_primary_supplied_scope";}
        var a=new Assessment(definition,Set.copyOf(related),target);a.report.put("target_origin",origin);
        for(String field:List.of("phenotype","outcome","genome_build","sequence_sha256","modality","assay","tissue","feature_id_type"))
            if(scope.get(field)!=null&&!(scope.get(field) instanceof String))a.issue("invalid_source_scope_value_type",field);
        a.report.put("trait_comparison_role",CONTEXT_ONLY.contains(kind)?"annotation_or_literature_context":"analysis_target");
        if(!CONTEXT_ONLY.contains(kind)) {
            if(target.isBlank())a.missing.add("registered_target_trait");
            boolean declared=false;
            for(String field:List.of("phenotype","outcome"))if(scope.containsKey(field)) {
                declared=true;String value=label(scope.get(field));
                boolean relatedAllowed=kind.equals("risk_score_related_phenotypes")&&related.contains(value);
                compare(a,field,value,target,relatedAllowed);
            }
            if(!declared)a.missing.add("source_trait");
        }
        var datasets=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:list(c.study().get("datasets"))){var d=map(raw);if(!"retired".equals(d.get("status")))datasets.put(text(d.get("id")),d);}
        var selected=selected(h,datasets);var expected=new LinkedHashSet<String>(selected);
        if(kind.equals("discovery_meta_analysis"))expected=new LinkedHashSet<>(strings(map(h.get("inputs")).get("discovery")));
        var expectedCohorts=cohorts(expected,datasets);
        if(expected.isEmpty()&&!anchor.isEmpty()&&!kind.equals("discovery_meta_analysis")) {
            expected.addAll(strings(map(anchor.get("scope")).get("datasets")));
            expectedCohorts.addAll(strings(map(anchor.get("scope")).get("cohorts")));
        }
        var sourceIds=new LinkedHashSet<>(strings(scope.get("datasets")));var sourceCohorts=new LinkedHashSet<>(strings(scope.get("cohorts")));
        if(!CONTEXT_ONLY.contains(kind)&&!EXTERNAL_POPULATION.contains(kind)) {
            boolean exact="primary".equals(definition.get("role"))&&(EXACT_POPULATION.contains(kind)||kind.equals("network_preservation_null"));
            membership(a,"datasets",sourceIds,expected,exact);
            membership(a,"cohorts",sourceCohorts,expectedCohorts,exact);
        }
        for(String id:selected) {
            var d=datasets.get(id);if(!StudyAnalysisReferences.isReference(d)||!definition.get("id").equals(d.get("supplied_result_id")))continue;
            if(!sourceCohorts.isEmpty()&&!sourceCohorts.containsAll(StudyPoolingAudit.cohorts(d)))a.issue("reference_cohort_outside_owner_scope","cohorts");
        }
        var scoped=new LinkedHashSet<String>();
        for(String id:selected) {
            var d=datasets.get(id);if(!sourceIds.isEmpty()?sourceIds.contains(id):!sourceCohorts.isEmpty()?sourceCohorts.containsAll(StudyPoolingAudit.cohorts(d)):true)scoped.add(id);
        }
        if(!CONTEXT_ONLY.contains(kind)&&!EXTERNAL_POPULATION.contains(kind)) {
            var pooling=StudyPoolingPolicy.resolve(map(analysis.get("pooling")));
            Map<String,Object> sourceEffect;
            try{sourceEffect=StudyPoolingPolicy.effectContract(map(scope.get("effect_contract")));}
            catch(IllegalArgumentException invalid){sourceEffect=Map.of();a.issue("invalid_source_effect_contract","effect_contract");}
            for(String field:StudyPoolingPolicy.EFFECT_FIELDS) {
                String value=label(sourceEffect.get(field));if(value.isBlank())a.missing.add("source:"+field);
                var targets=new LinkedHashSet<String>();
                for(String id:scoped){var effect=new LinkedHashMap<>(map(pooling.get("effect_contract")));effect.putAll(map(datasets.get(id).get("effect_contract")));if(!label(effect.get(field)).isBlank())targets.add(label(effect.get(field)));}
                context(a,pooling,field,value,targets);
            }
            for(String field:List.of("species","modality","assay","tissue","genome_build","feature_id_type")) {
                var targets=new LinkedHashSet<String>();
                for(String id:scoped){var d=datasets.get(id);String value=label(field.equals("modality")?d.getOrDefault("modality",d.get("type")):d.get(field));if(field.equals("modality")&&Set.of("generic","unknown","auto").contains(value))continue;if(!value.isBlank())targets.add(value);}
                context(a,pooling,field,label(scope.get(field)),targets);
            }
        }
        if(kind.equals("network_analysis")) {
            boolean hasMatrices=selected.stream().map(datasets::get).anyMatch(d->StudyNetworkPolicy.MOLECULAR.contains(text(d.get("type")))&&Set.of("raw_quantified","quantified_matrix","processed_matrix").contains(d.get("stage")));
            var coverage=StudyNetworkReuse.coverage(analysis,datasets,new ArrayList<>(selected));
            a.report.put("network_dataset_coverage",coverage.sources().getOrDefault(text(definition.get("id")),List.of()));
            a.report.put("network_coverage_resolved",coverage.resolved());
            if(hasMatrices&&Boolean.TRUE.equals(StudyNetworkPolicy.resolve(map(analysis.get("network"))).get("complete_missing"))&&!coverage.resolved())a.issue("network_population_mapping_required_before_missing_work","datasets_or_cohorts");
            if(StudySuppliedResultPolicy.partialPrimary(definition))a.report.put("partial_network_role","retained_evidence_without_population_coverage_or_completed_stage_ownership");
        }
        // Missing upstream files retain the historical lineage caveat. Known
        // scientific contradictions in a declared dependency cannot be waived.
        for(Object dependency:list(definition.get("depends_on"))) {
            var parent=definitions.stream().filter(d->dependency.equals(d.get("id"))).findFirst().orElseThrow();
            if(!target.isBlank()&&!CONTEXT_ONLY.contains(text(parent.get("kind")))) {
                String parentTrait=trait(map(parent.get("scope")));
                if(!parentTrait.isBlank()&&!parentTrait.equals(target)&&!related.contains(parentTrait))a.issue("upstream_trait_conflicts_with_registered_target","depends_on");
            }
        }
        for(Path directory:c.dependencyDirectories().values()) {
            Path manifest=directory.resolve("manifest.yaml"),metadata=directory.resolve("supplied-analysis.yaml");
            if(!Files.isRegularFile(manifest)||!Files.isRegularFile(metadata))continue;
            var m=StudyYaml.loadArtifact(manifest);var source=StudyYaml.loadArtifact(metadata);
            if(!list(definition.get("depends_on")).contains(source.get("artifact_id")))continue;
            if(!c.operation().get("hypothesis").equals(m.get("hypothesis")))throw new IOException("Supplied dependency belongs to another hypothesis");
            StudyFindingInventory.verified(directory,"supplied-analysis.yaml",text(map(m.get("output_sha256")).get("supplied-analysis.yaml")));
            if("incompatible_or_unresolved".equals(map(source.get("scope_validation")).get("status")))a.issue("upstream_scientific_scope_is_incompatible","depends_on");
        }
        a.report.put("registered_population_datasets",List.copyOf(expected));a.report.put("registered_population_cohorts",List.copyOf(expectedCohorts));
        return a;
    }

    static void rows(Assessment a,List<String> header,List<String[]> rows) {
        var scope=map(a.definition.get("scope"));String kind=text(a.definition.get("kind"));
        for(String[] row:rows) {
            for(String field:List.of("genome_build","sequence_sha256"))if(header.contains(field)&&!label(scope.get(field)).isBlank()) {
                String value=row[header.indexOf(field)];if(missing(value))continue;
                String declared=label(scope.get(field));
                if(!canonical(field,value).equals(canonical(field,declared)))a.issue("row_conflicts_with_declared_source_scope",field);
            }
            if(ROW_POPULATION.contains(kind))for(String field:List.of("dataset_id","cohort")) {
                String key=field.equals("dataset_id")?"datasets":"cohorts";
                if(header.contains(field)&&!list(scope.get(key)).isEmpty()&&!list(scope.get(key)).contains(row[header.indexOf(field)]))a.issue("row_outside_declared_source_population",field);
            }
            if(header.contains("phenotype")&&kind.equals("risk_score_evaluation")&&!a.target.isBlank())compare(a,"row.phenotype",row[header.indexOf("phenotype")],a.target,false);
            if(header.contains("phenotype")&&kind.equals("risk_score_related_phenotypes")&&!a.allowedRelated.isEmpty()&&!a.allowedRelated.contains(row[header.indexOf("phenotype")]))a.issue("unregistered_related_score_phenotype","phenotype");
        }
    }
    private static void compare(Assessment a,String field,String source,String expected,boolean exception) {
        boolean conflict=!source.isBlank()&&!expected.isBlank()&&!source.equals(expected)&&!exception;
        a.rows.add(new String[]{field,source.isBlank()?"NA":source,expected.isBlank()?"NA":expected,conflict?"conflicting":"declared_or_unavailable"});
        if(conflict)a.issue("source_trait_conflicts_with_registered_target",field);
        if(source.isBlank())a.missing.add("source:"+field);
    }
    private static void membership(Assessment a,String field,Set<String> source,Set<String> expected,boolean exact) {
        if(source.isEmpty()){a.missing.add("source:"+field);return;}if(expected.isEmpty()){a.missing.add("registered:"+field);return;}
        boolean matches=exact?source.equals(expected):expected.containsAll(source);
        a.rows.add(new String[]{field,String.join(",",source),String.join(",",expected),matches?exact?"exact":"subset":"conflicting"});
        if(!matches)a.issue("source_population_conflicts_with_registered_scope",field);
    }
    private static void context(Assessment a,Map<String,Object> policy,String field,String source,Set<String> expected) {
        if(source.isBlank()||expected.isEmpty()){if(!expected.isEmpty())a.missing.add("source:"+field);if(!source.isBlank()&&expected.isEmpty())a.missing.add("registered:"+field);return;}
        var canonical=new LinkedHashSet<String>();for(String value:expected)canonical.add(canonical(field,value));
        boolean matches=canonical.size()==1&&canonical.contains(canonical(field,source));
        if(!matches&&!map(policy.get("context_exceptions")).containsKey(field))a.issue("source_context_conflicts_with_registered_scope",field);
        a.rows.add(new String[]{field,source,String.join(" | ",expected),matches?"matches":map(policy.get("context_exceptions")).containsKey(field)?"registered_context_exception":"conflicting"});
    }
    private static Map<String,Object> hypothesis(Context c){for(Object raw:list(c.study().get("hypotheses"))){var h=map(raw);if(h.get("id").equals(c.operation().get("hypothesis")))return h;}return Map.of();}
    private static Set<String> selected(Map<String,Object> h,Map<String,Map<String,Object>> datasets){var result=new LinkedHashSet<String>();var inputs=map(h.get("inputs"));for(Object raw:inputs.values())result.addAll(strings(raw));if(inputs.isEmpty())result.addAll(datasets.keySet());return result;}
    private static Set<String> cohorts(Set<String> ids,Map<String,Map<String,Object>> datasets){var result=new LinkedHashSet<String>();for(String id:ids)if(datasets.containsKey(id))result.addAll(StudyPoolingAudit.cohorts(datasets.get(id)));return result;}
    private static List<String> strings(Object value){if(value instanceof String s)return List.of(s);return list(value).stream().map(StudyPublicEvidence::text).toList();}
    private static String label(Object value){return value instanceof String s&&!s.isBlank()&&s.length()<=4096&&!s.chars().anyMatch(Character::isISOControl)?s.strip():"";}
    private static String trait(Map<String,Object> scope){String value=label(scope.get("outcome"));return value.isBlank()?label(scope.get("phenotype")):value;}
    private static boolean missing(String value){return Set.of("","NA","NaN",".").contains(value);}
    private static String canonical(String field,String value){return field.equals("sequence_sha256")?value.toLowerCase(java.util.Locale.ROOT):StudyPoolingAudit.canonical(field,value);}
}
