/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic rule planner over typed datasets and registered operations. */
public final class StudyPlanner {
    private StudyPlanner() { }

    public static StudyPlan compile(StudyContract resolved) {
        Map<String, Object> study = resolved.document();
        String resolvedHash = StudyProject.resolvedHash(study);
        Map<String, Object> project = StudyIO.map(study.get("project"), "project");
        Map<String, Map<String, Object>> datasets = byId(
            StudyIO.list(study.get("datasets"), "datasets"));
        List<Object> operations = new ArrayList<>();
        List<Object> decisions = pendingAssumptions(study);

        for (Object value : StudyIO.list(study.get("hypotheses"), "hypotheses")) {
            Map<String, Object> hypothesis = StudyIO.map(value, "hypothesis");
            if ("retired".equals(hypothesis.get("status"))) continue;
            String hypothesisId = StudyIO.requireString(
                hypothesis, "id", "hypothesis");
            Map<String, Object> inputs = StudyIO.map(
                hypothesis.get("inputs"), "hypothesis.inputs");
            List<String> allSelected = selected(inputs);
            List<String> selected = allSelected.stream().filter(id -> !StudyRareVariantPolicy.setInput(datasets.get(id))).toList();
            Map<String, Object> analysis = StudyIO.map(
                hypothesis.get("analysis"), "hypothesis.analysis");
            Set<String> goals = goals(analysis);
            Map<String, Object> bioinformatics = StudyIO.optionalMap(
                analysis.get("bioinformatics"), "hypothesis.analysis.bioinformatics");
            boolean automaticColocalization = !StudyIO.optionalMap(
                bioinformatics.get("colocalization"), "hypothesis.analysis.bioinformatics.colocalization").isEmpty();
            if (automaticColocalization) {
                goals.add("source_discovery");
                goals.add("colocalization");
            }
            Map<String, Object> assist = StudyIO.optionalMap(study.get("assistance"), "assistance");
            if (Boolean.TRUE.equals(assist.get("enabled")) && Boolean.TRUE.equals(assist.get("literature")))
                goals.add("literature_support");
            Counter counter = new Counter(hypothesisId);
            Map<String, String> supplied = new LinkedHashMap<>();
            Map<String, String> suppliedIds = new LinkedHashMap<>();
            List<String> mrContributions = new ArrayList<>();
            List<String> literatureContributions = new ArrayList<>();
            List<String> suppliedNetworks = new ArrayList<>();
            for (var definition : StudySuppliedResultPolicy.resolve(analysis)) {
                String kind = String.valueOf(definition.get("kind"));
                boolean primary = "primary".equals(definition.get("role"));
                var dependencies = StudyIO.list(definition.get("depends_on"), "depends_on").stream()
                    .map(id -> suppliedIds.get(String.valueOf(id))).toList();
                boolean contribution = "contribution".equals(definition.get("role"));
                var imported = operation(counter, StudySuppliedResultPolicy.operationType(definition), hypothesisId, List.of(), dependencies);
                imported.put("supplied_result_id", definition.get("id"));
                imported.put("allow_incomplete_dependencies", true);
                imported.put("scientific_role", StudySuppliedResultPolicy.partialPrimary(definition) ? "supplied_partial" : primary ? "supplied_primary" : contribution ? "supplied_contribution" : "supplied_sensitivity");
                operations.add(imported);
                String id = String.valueOf(imported.get("id"));
                suppliedIds.put(String.valueOf(definition.get("id")), id);
                if (StudySuppliedResultPolicy.ownsStage(definition)) supplied.putIfAbsent(kind, id);
                if (StudySuppliedResultPolicy.ownsStage(definition) && kind.equals("network_analysis")) suppliedNetworks.add(id);
                if (contribution&&kind.equals("mendelian_randomization")) { mrContributions.add(id); goals.add("mendelian_randomization"); }
                if (contribution&&kind.equals("literature_search")) { literatureContributions.add(id); goals.add("literature_search"); }
            }
            boolean rareEnabled = !supplied.containsKey("rare_variant_analysis") && (!supplied.containsKey("rare_set_omnibus")||!StudyRareOmnibusPolicy.primary(analysis)||goals.contains("rare_variant_analysis")) && (goals.contains("rare_variant_analysis") || Boolean.TRUE.equals(
                StudyIO.optionalMap(analysis.get("rare_variants"), "rare_variants").get("enabled")));
            List<String> rareDatasets = rareEnabled ? allSelected.stream().filter(id -> {
                var dataset = datasets.get(id);
                return StudyRareVariantPolicy.setInput(dataset) || Set.of("gwas", "genomics").contains(dataset.get("type"))
                    && Set.of("raw_quantified", "quantified_matrix", "processed_matrix").contains(dataset.get("stage"));
            }).toList() : List.of();
            List<String> scoreDatasets = rareDatasets.stream().filter(id -> !"set_association_results".equals(datasets.get(id).get("stage"))).toList();
            String masks = null;
            String variantAnnotation = supplied.get("variant_annotation");
            List<String> missingAnnotations = scoreDatasets.stream().filter(id -> !StudyIO.optionalMap(datasets.get(id).get("files"), "files").containsKey("variant_annotation")).toList();
            if (!missingAnnotations.isEmpty() && variantAnnotation == null) {
                var annotation = operation(counter, "variant_annotation", hypothesisId, missingAnnotations, List.of());
                operations.add(annotation); variantAnnotation = String.valueOf(annotation.get("id"));
            }
            if (!scoreDatasets.isEmpty()) {
                var operation = operation(counter, "rare_variant_masks", hypothesisId, scoreDatasets, variantAnnotation == null ? List.of() : List.of(variantAnnotation));
                operations.add(operation); masks = String.valueOf(operation.get("id"));
            }
            Map<String, String> prefitOperations = new LinkedHashMap<>();
            boolean interactionsRequested=goals.contains("subgroup_interactions")||Boolean.TRUE.equals(StudySubgroupInteractionPolicy.resolve(StudyIO.optionalMap(analysis.get("subgroup_interactions"),"subgroup_interactions")).get("enabled"));

            Map<String, Object> harmonize = operation(counter, "validate_harmonize",
                hypothesisId, selected, List.of());
            operations.add(harmonize);
            List<String> resultOperations = new ArrayList<>();
            for (String datasetId : selected) {
                Map<String, Object> dataset = datasets.get(datasetId);
                if (dataset == null || "retired".equals(dataset.get("status"))) continue;
                String stage = String.valueOf(dataset.get("stage"));
                boolean genotypeQc=Set.of("gwas","genomics").contains(dataset.get("type"))&&Boolean.TRUE.equals(StudyGenotypeSampleQcPolicy.resolve(StudyIO.optionalMap(analysis.get("genotype_sample_qc"),"genotype_sample_qc")).get("enabled"));
                boolean nativePhenotypeGsea=goals.contains("ranked_gsea")&&!supplied.containsKey("ranked_gsea")&&StudyIO.optionalMap(analysis.get("gsea"),"gsea").containsKey("phenotype");
                if ((goals.contains("association") || genotypeQc || nativePhenotypeGsea || rareDatasets.contains(datasetId) || interactionsRequested&&!supplied.containsKey("subgroup_interactions")) && Set.of(
                        "raw_quantified", "quantified_matrix",
                        "processed_matrix").contains(stage)) {
                    Map<String, Object> prefit = operation(counter, "participant_qc", hypothesisId, List.of(datasetId),
                        List.of(String.valueOf(harmonize.get("id"))));
                    prefit.put("dataset_id", datasetId);
                    operations.add(prefit);
                    prefitOperations.put(datasetId, String.valueOf(prefit.get("id")));
                    if (!goals.contains("association")) continue;
                    Map<String, Object> association = operation(counter,
                        "association", hypothesisId, List.of(datasetId),
                        List.of(String.valueOf(harmonize.get("id")), String.valueOf(prefit.get("id"))));
                    association.put("dataset_id", datasetId);
                    association.put("requires_prefit_qc", true);
                    operations.add(association);
                    resultOperations.add(String.valueOf(association.get("id")));
                } else if (stage.endsWith("_results")) {
                    resultOperations.add("dataset:" + datasetId);
                }
            }
            String rareAnalysis = supplied.get("rare_variant_analysis");
            String subgroupInteractions=supplied.get("subgroup_interactions");
            if(subgroupInteractions==null&&interactionsRequested) {
                var operation=operation(counter,"subgroup_interactions",hypothesisId,List.copyOf(prefitOperations.keySet()),new ArrayList<>(prefitOperations.values()));
                operation.put("allow_incomplete_dependencies",true);operations.add(operation);subgroupInteractions=String.valueOf(operation.get("id"));
            }
            List<String> rareScoreOperations = new ArrayList<>();
            if (rareEnabled) {
                List<String> rareDependencies = new ArrayList<>();
                if (masks != null) rareDependencies.add(masks);
                for (String id : scoreDatasets) if (!"rare_score_summary".equals(datasets.get(id).get("stage"))) {
                    List<String> dependencies = new ArrayList<>(List.of(masks));
                    if (prefitOperations.containsKey(id)) dependencies.add(prefitOperations.get(id));
                    var score = operation(counter, "rare_variant_scores", hypothesisId, List.of(id), dependencies);
                    score.put("dataset_id", id); operations.add(score); rareDependencies.add(String.valueOf(score.get("id")));
                    rareScoreOperations.add(String.valueOf(score.get("id")));
                }
                var operation = operation(counter, "rare_variant_analysis", hypothesisId,
                    rareDatasets.isEmpty() ? allSelected : rareDatasets, rareDependencies);
                operation.put("roles", StudyIO.copyMap(inputs));
                operations.add(operation); rareAnalysis = String.valueOf(operation.get("id"));
            }
            String effectComparison = supplied.get("effect_comparison");
            if (effectComparison == null && selected.size() > 1 && (!supplied.containsKey("meta_analysis") || !resultOperations.isEmpty() || goals.contains("effect_comparison"))) {
                Map<String, Object> operation = operation(counter,
                    "effect_comparison", hypothesisId, selected,
                    dependencies(harmonize, resultOperations));
                operations.add(operation);
                effectComparison = String.valueOf(operation.get("id"));
            }
            var overlapPolicy=StudyCohortPolicy.overlap(StudyIO.optionalMap(analysis.get("cohort_overlap"),"cohort_overlap"));
            boolean overlapRequested=Boolean.TRUE.equals(overlapPolicy.get("enabled"))&&resultOperations.size()>1;
            boolean reducedOverlap="discovery_subset".equals(overlapPolicy.get("validation_family"))||"analysis".equals(overlapPolicy.get("validation_family"))&&"discovery_subset".equals(StudyIO.optionalMap(analysis.get("replication"),"replication").get("adjustment_scope"));
            String discoveryPool = supplied.get("discovery_meta_analysis");
            List<String> discoveryIds = StudyIO.list(inputs.getOrDefault("discovery", List.of()), "inputs.discovery")
                    .stream().map(String::valueOf).filter(selected::contains).toList();
            if (discoveryPool == null && discoveryIds.size() > 1 && (goals.contains("discovery_replication") || overlapRequested && (reducedOverlap || "require_discovery".equals(overlapPolicy.get("direction"))))) {
                    List<String> poolDependencies = new ArrayList<>(List.of(String.valueOf(harmonize.get("id"))));
                    for (Object raw : operations) {
                        Map<String, Object> prior = StudyIO.map(raw, "operation");
                        if ("association".equals(prior.get("type")) && discoveryIds.contains(String.valueOf(prior.get("dataset_id"))))
                            poolDependencies.add(String.valueOf(prior.get("id")));
                    }
                    Map<String, Object> pool = operation(counter, "discovery_meta_analysis", hypothesisId, discoveryIds, poolDependencies);
                    Map<String, Object> poolConfiguration = operationConfiguration(analysis, "discovery_meta_analysis");
                    if (poolConfiguration.isEmpty()) poolConfiguration = operationConfiguration(analysis, "meta_analysis");
                    pool.put("configuration", poolConfiguration);
                    pool.put("scientific_role", "discovery_only");
                    operations.add(pool);
                    discoveryPool = String.valueOf(pool.get("id"));
                }
            String comparison = supplied.get("discovery_replication");
            if (comparison == null && goals.contains("discovery_replication") && !selected.isEmpty()) {
                Map<String, Object> operation = operation(counter,
                    "discovery_replication", hypothesisId, selected,
                    dependencies(harmonize, resultOperations));
                Map<String, Object> signedRoles = new LinkedHashMap<>();
                inputs.forEach((role, ids) -> signedRoles.put(role, StudyIO.list(ids, "role").stream().map(String::valueOf).filter(selected::contains).toList()));
                operation.put("roles", signedRoles);
                if (discoveryPool != null) {
                    List<String> withPool = new ArrayList<>(StudyIO.list(operation.get("depends_on"), "depends_on").stream().map(String::valueOf).toList());
                    withPool.add(discoveryPool);
                    operation.put("depends_on", withPool);
                    operation.put("pooled_discovery", true);
                }
                operations.add(operation);
                comparison = String.valueOf(operation.get("id"));
            }
            String overlap=supplied.get("cohort_overlap");
            if(overlap==null&&(overlapRequested||goals.contains("cohort_overlap"))){
                var overlapDependencies=new ArrayList<>(dependencies(harmonize,resultOperations));if(discoveryPool!=null)overlapDependencies.add(discoveryPool);
                var operation=operation(counter,"cohort_overlap",hypothesisId,selected,overlapDependencies);var overlapRoles=new LinkedHashMap<String,Object>();inputs.forEach((role,ids)->overlapRoles.put(role,StudyIO.list(ids,"role").stream().map(String::valueOf).filter(selected::contains).toList()));operation.put("roles",overlapRoles);operations.add(operation);overlap=String.valueOf(operation.get("id"));
            }
            String meta = supplied.get("meta_analysis");
            if (meta == null && goals.contains("meta_analysis") && !selected.isEmpty()) {
                Map<String, Object> operation = operation(counter,
                    "meta_analysis", hypothesisId, selected,
                    dependencies(harmonize, resultOperations));
                operation.put("configuration",
                    operationConfiguration(analysis, "meta_analysis"));
                operations.add(operation);
                meta = String.valueOf(operation.get("id"));
            }
            String heterogeneity=supplied.get("heterogeneity_review");
            if(heterogeneity==null&&(goals.contains("heterogeneity_review")||meta!=null&&(!supplied.containsKey("meta_analysis")||!"manual".equals(analysis.get("mode"))))&&Boolean.TRUE.equals(StudyCohortPolicy.heterogeneity(StudyIO.optionalMap(analysis.get("heterogeneity"),"heterogeneity")).get("enabled"))){
                var operation=operation(counter,"heterogeneity_review",hypothesisId,selected,meta==null?List.of():List.of(meta));operation.put("allow_incomplete_dependencies",true);operations.add(operation);heterogeneity=String.valueOf(operation.get("id"));
            }
            List<String> followupDependencies = new ArrayList<>();
            if (meta != null) followupDependencies.add(meta);
            else if (comparison != null) followupDependencies.add(comparison);
            else {
                for (String result : resultOperations)
                    if (!result.startsWith("dataset:"))
                        followupDependencies.add(result);
                if (followupDependencies.isEmpty())
                    followupDependencies.add(String.valueOf(harmonize.get("id")));
            }
            String rareFollowup=null;
            String rareOmnibus=supplied.get("rare_set_omnibus");
            if(rareOmnibus==null&&(goals.contains("rare_set_omnibus")||rareAnalysis!=null&&StudyRareOmnibusPolicy.primary(analysis))){var combined=operation(counter,"rare_set_omnibus",hypothesisId,allSelected,rareAnalysis==null?List.of():List.of(rareAnalysis));combined.put("allow_incomplete_dependencies",true);combined.put("configuration",Map.of("rare_omnibus",StudyRareOmnibusPolicy.resolve(StudyIO.optionalMap(analysis.get("rare_omnibus"),"rare_omnibus"))));operations.add(combined);rareOmnibus=String.valueOf(combined.get("id"));}
            String rareFollowupSource=StudyRareOmnibusPolicy.primary(analysis)?rareOmnibus:rareAnalysis;
            if(rareFollowupSource!=null&&Boolean.TRUE.equals(StudyRareFollowupPolicy.resolve(StudyIO.optionalMap(StudyIO.optionalMap(analysis.get("rare_variants"),"rare_variants").get("followup"),"rare_variants.followup")).get("enabled"))){
                var annotation=operation(counter,"rare_set_followup",hypothesisId,allSelected,List.of(rareFollowupSource));annotation.put("allow_incomplete_dependencies",true);operations.add(annotation);rareFollowup=String.valueOf(annotation.get("id"));
            }
            List<String> evidenceOperations = new ArrayList<>(suppliedIds.values());
            String rareOverlap=supplied.get("rare_cohort_overlap");
            List<String> rareOverlapDatasets=rareDatasets.isEmpty()?allSelected:rareDatasets;
            if(rareOverlap==null&&rareAnalysis!=null&&(Boolean.TRUE.equals(overlapPolicy.get("enabled"))&&rareOverlapDatasets.size()>1||goals.contains("rare_cohort_overlap"))){
                var operation=operation(counter,"rare_cohort_overlap",hypothesisId,rareOverlapDatasets,List.of(rareAnalysis));operation.put("roles",StudyIO.copyMap(inputs));operation.put("allow_incomplete_dependencies",true);operations.add(operation);rareOverlap=String.valueOf(operation.get("id"));
            }
            if(rareOverlap!=null&&!evidenceOperations.contains(rareOverlap))evidenceOperations.add(rareOverlap);
            if(overlap!=null&&!evidenceOperations.contains(overlap))evidenceOperations.add(overlap);if(heterogeneity!=null&&!evidenceOperations.contains(heterogeneity))evidenceOperations.add(heterogeneity);
            if (rareAnalysis != null) evidenceOperations.add(rareAnalysis);
            if (rareOmnibus != null&&!evidenceOperations.contains(rareOmnibus)) evidenceOperations.add(rareOmnibus);
            if (rareFollowup != null) evidenceOperations.add(rareFollowup);
            String irqtlReview = null;
            if (selected.stream().anyMatch(id -> StudyIO.optionalMap(datasets.get(id).get("files"), "files").containsKey("irqtl"))) {
                List<String> inputsForIrqtl = new ArrayList<>(followupDependencies);
                if (!inputsForIrqtl.contains(String.valueOf(harmonize.get("id")))) inputsForIrqtl.add(String.valueOf(harmonize.get("id")));
                Map<String, Object> irqtl = operation(counter, "irqtl_candidate_review", hypothesisId, selected, inputsForIrqtl);
                irqtl.put("allow_incomplete_dependencies", true);
                operations.add(irqtl);
                irqtlReview = String.valueOf(irqtl.get("id"));
                evidenceOperations.add(irqtlReview);
            }
            String sourceDiscovery = null;
            String phenotypeAdvice = null;
            if (automaticColocalization && Boolean.TRUE.equals(assist.get("enabled"))
                    && Boolean.TRUE.equals(assist.get("related_phenotypes"))) {
                List<String> inputsForAdvice = new ArrayList<>(followupDependencies);
                if (!inputsForAdvice.contains(String.valueOf(harmonize.get("id"))))
                    inputsForAdvice.add(String.valueOf(harmonize.get("id")));
                Map<String, Object> advice = operation(counter, "related_phenotype_advice", hypothesisId, selected, inputsForAdvice);
                advice.put("allow_incomplete_dependencies", true);
                operations.add(advice);
                phenotypeAdvice = String.valueOf(advice.get("id"));
                evidenceOperations.add(phenotypeAdvice);
            }
            for (String goal : List.of("source_discovery", "bioinformatics_investigation", "literature_search")) {
                if (supplied.containsKey(goal)) {
                    if (goal.equals("source_discovery")) sourceDiscovery = supplied.get(goal);
                    continue;
                }
                if (!goals.contains(goal)) continue;
                List<String> annotated = new ArrayList<>(followupDependencies);
                if (!annotated.contains(String.valueOf(harmonize.get("id")))) annotated.add(String.valueOf(harmonize.get("id")));
                for (String result : resultOperations)
                    if (!result.startsWith("dataset:") && !annotated.contains(result)) annotated.add(result);
                if(rareFollowup!=null&&!goal.equals("source_discovery"))annotated.add(rareFollowup);
                if(goal.equals("literature_search"))annotated.addAll(literatureContributions);
                if (goal.equals("source_discovery") && phenotypeAdvice != null && !annotated.contains(phenotypeAdvice))
                    annotated.add(phenotypeAdvice);
                Map<String, Object> followup = operation(counter, goal, hypothesisId, selected, annotated);
                followup.put("allow_incomplete_dependencies", true);
                operations.add(followup);
                evidenceOperations.add(String.valueOf(followup.get("id")));
                if (goal.equals("source_discovery")) sourceDiscovery = String.valueOf(followup.get("id"));
            }
            // Annotation assessment accompanies harmonized data. Preserve the single
            // upstream placeholder contract for explicitly configured CLI adapters.
            for (String goal : List.of("gene_ontology", "pathway_enrichment",
                    "variant_scoring", "fine_mapping", "colocalization",
                    "mendelian_randomization", "literature_support")) {
                if (supplied.containsKey(goal)) continue;
                if (!goals.contains(goal) && !(goals.contains("genetic_support")
                        && Set.of("variant_scoring", "fine_mapping",
                            "colocalization", "mendelian_randomization")
                            .contains(goal))) continue;
                Map<String, Object> operation = operation(counter, goal,
                    hypothesisId, selected, followupDependencies);
                operation.put("configuration", operationConfiguration(analysis, goal));
                if (!operationConfiguration(analysis, goal).containsKey("command")) {
                    List<String> annotated = new ArrayList<>(followupDependencies);
                    if (goal.equals("mendelian_randomization") && !mrContributions.isEmpty()) {
                        annotated.addAll(mrContributions); operation.put("allow_incomplete_dependencies", true);
                    }
                    if (!annotated.contains(String.valueOf(harmonize.get("id"))))
                        annotated.add(String.valueOf(harmonize.get("id")));
                    for (String result : resultOperations)
                        if (!result.startsWith("dataset:") && !annotated.contains(result))
                            annotated.add(result);
                    if (irqtlReview != null && Set.of("fine_mapping", "colocalization", "mendelian_randomization").contains(goal)
                            && !annotated.contains(irqtlReview)) {
                        annotated.add(irqtlReview);
                        operation.put("allow_incomplete_dependencies", true);
                    }
                    if(rareFollowup!=null&&Set.of("gene_ontology","pathway_enrichment").contains(goal)){annotated.add(rareFollowup);operation.put("allow_incomplete_dependencies",true);}
                    operation.put("depends_on", annotated);
                    if (goal.equals("variant_scoring") && variantAnnotation != null && !annotated.contains(variantAnnotation)) annotated.add(variantAnnotation);
                    if (goal.equals("variant_scoring")) for (String score : rareScoreOperations) if (!annotated.contains(score)) annotated.add(score);
                    if (sourceDiscovery != null && Set.of("fine_mapping", "colocalization", "mendelian_randomization").contains(goal)) {
                        annotated.add(sourceDiscovery);
                        operation.put("allow_incomplete_dependencies", true);
                    }
                }
                operations.add(operation);
                evidenceOperations.add(String.valueOf(operation.get("id")));
            }
            if(goals.contains("gwas_enrichment")&&!supplied.containsKey("gwas_enrichment")){
                var gwas=StudyGwasEnrichmentPolicy.resolve(StudyIO.optionalMap(analysis.get("gwas_enrichment"),"gwas_enrichment"));
                boolean external=StudyIO.optionalMap(gwas.get("summary"),"summary").containsKey("file");
                var deps=new ArrayList<String>();
                if(!external){if(meta!=null)deps.add(meta);deps.add(String.valueOf(harmonize.get("id")));for(String result:resultOperations)if(!result.startsWith("dataset:")&&!deps.contains(result))deps.add(result);}
                var enrichment=operation(counter,"gwas_enrichment",hypothesisId,external?List.of():selected,deps);enrichment.put("allow_incomplete_dependencies",true);operations.add(enrichment);evidenceOperations.add(String.valueOf(enrichment.get("id")));
            }
            if(goals.contains("ranked_gsea")&&!supplied.containsKey("ranked_gsea")) {
                var gsea=StudyGseaPolicy.resolve(StudyIO.optionalMap(analysis.get("gsea"),"gsea"));
                if(gsea.containsKey("phenotype")) {
                    // This registered participant-null analysis is cohort-specific;
                    // a pooled/meta null must reproduce that complete model externally.
                    for(String datasetId:selected.isEmpty()?List.of(""):selected){String prefit=prefitOperations.get(datasetId);var deps=new ArrayList<String>();if(prefit!=null)deps.add(prefit);var operation=operation(counter,"ranked_gsea",hypothesisId,datasetId.isEmpty()?List.of():List.of(datasetId),deps);operation.put("dataset_id",datasetId);if(prefit!=null)operation.put("participant_qc_producer",prefit);operation.put("configuration",operationConfiguration(analysis,"ranked_gsea"));operations.add(operation);evidenceOperations.add(String.valueOf(operation.get("id")));}
                } else if(gsea.containsKey("ranking")||meta!=null||!StudyPrimaryPolicy.resolve(StudyIO.optionalMap(analysis.get("primary_result"),"primary_result")).get("kind").equals("auto")||StudyIO.optionalMap(analysis.get("primary_result"),"primary_result").containsKey("dataset_id")||StudyIO.optionalMap(analysis.get("primary_result"),"primary_result").containsKey("operation_id")||StudyIO.optionalMap(analysis.get("primary_result"),"primary_result").containsKey("term")) {
                    List<String> deps = new ArrayList<>();
                    if (!gsea.containsKey("ranking")) {
                        if (meta != null) deps.add(meta); deps.add(String.valueOf(harmonize.get("id")));
                        for (String result : resultOperations) if (!result.startsWith("dataset:") && !deps.contains(result)) deps.add(result);
                    }
                    var operation=operation(counter,"ranked_gsea",hypothesisId,gsea.containsKey("ranking")?List.of():selected,deps);operation.put("configuration",operationConfiguration(analysis,"ranked_gsea"));operations.add(operation);evidenceOperations.add(String.valueOf(operation.get("id")));
                } else {
                    List<String> sources=selected.isEmpty()?List.of(""):selected;
                    for(String datasetId:sources){List<String> deps=new ArrayList<>();
                        for(Object raw:operations){var prior=StudyIO.map(raw,"operation");if(hypothesisId.equals(prior.get("hypothesis"))&&"association".equals(prior.get("type"))&&datasetId.equals(prior.get("dataset_id")))deps.add(String.valueOf(prior.get("id")));}
                        if(deps.isEmpty())deps.add(String.valueOf(harmonize.get("id")));
                        var operation=operation(counter,"ranked_gsea",hypothesisId,datasetId.isEmpty()?List.of():List.of(datasetId),deps);operation.put("dataset_id",datasetId);operation.put("configuration",operationConfiguration(analysis,"ranked_gsea"));operations.add(operation);evidenceOperations.add(String.valueOf(operation.get("id")));
                    }
                }
            }
            for (String goal : List.of("risk_score_evaluation", "protein_structure", "protein_structure_assessment", "variant_annotation")) {
                if (!goals.contains(goal) || supplied.containsKey(goal) || goal.equals("variant_annotation") && variantAnnotation != null) continue;
                boolean explicitProtein=goal.startsWith("protein_structure")&&StudyIO.optionalMap(analysis.get("protein_structure"),"protein_structure").containsKey("manifest");
                var operation = operation(counter, goal, hypothesisId, selected, goal.equals("risk_score_evaluation")||explicitProtein?List.of():followupDependencies);
                operation.put("configuration", operationConfiguration(analysis, goal));
                if (goal.startsWith("protein_structure") && !explicitProtein) {
                    var deps = new ArrayList<>(followupDependencies); if (!deps.contains(String.valueOf(harmonize.get("id")))) deps.add(String.valueOf(harmonize.get("id")));
                    for (String result : resultOperations) if (!result.startsWith("dataset:") && !deps.contains(result)) deps.add(result);
                    operation.put("depends_on", deps);
                }
                operations.add(operation); evidenceOperations.add(String.valueOf(operation.get("id")));
            }
            if(goals.contains("risk_score_related_phenotypes")&&!supplied.containsKey("risk_score_related_phenotypes")&&(supplied.containsKey("risk_score_evaluation")||!goals.contains("risk_score_evaluation"))){
                var score=operation(counter,"risk_score_related_phenotypes",hypothesisId,selected,supplied.containsKey("risk_score_evaluation")?List.of(supplied.get("risk_score_evaluation")):List.of());score.put("configuration",operationConfiguration(analysis,"risk_score_related_phenotypes"));operations.add(score);evidenceOperations.add(String.valueOf(score.get("id")));
            }
            List<String> networkSources = new ArrayList<>();
            networkSources.addAll(suppliedNetworks);
            boolean nullRequested=goals.contains("network_preservation_null")&&!supplied.containsKey("network_preservation_null");
            boolean preservationRequested=(goals.contains("network_preservation")||nullRequested)&&!supplied.containsKey("network_preservation");
            boolean preservationGraphsRequested=preservationRequested||nullRequested;
            List<String> moduleSources=new ArrayList<>();
            String providedModules=supplied.get("network_modules");
            boolean missingModules=goals.contains("network_modules")&&providedModules==null;
            boolean missingModuleGo=goals.contains("module_gene_ontology")&&!supplied.containsKey("module_gene_ontology");
            boolean networkRequested = preservationGraphsRequested || goals.contains("network_analysis") || !goals.contains("pathway_network")
                && (missingModules || missingModuleGo&&providedModules==null);
            if (networkRequested) {
                List<String> matrixIds = selected.stream().filter(id -> StudyNetworkPolicy.MOLECULAR.contains(String.valueOf(datasets.get(id).get("type")))
                    && Set.of("raw_quantified","quantified_matrix","processed_matrix").contains(datasets.get(id).get("stage"))).toList();
                var coverage=StudyNetworkReuse.coverage(analysis,datasets,selected);
                if(!suppliedNetworks.isEmpty()) matrixIds=coverage.resolved()&&Boolean.TRUE.equals(StudyNetworkPolicy.resolve(StudyIO.optionalMap(analysis.get("network"),"network")).get("complete_missing"))?matrixIds.stream().filter(id->!coverage.covered().contains(id)).toList():List.of();
                if (matrixIds.isEmpty() && suppliedNetworks.isEmpty()) matrixIds = List.of("");
                for (String datasetId : matrixIds) {
                    var network = operation(counter,"network_analysis",hypothesisId,datasetId.isEmpty()?List.of():List.of(datasetId),List.of());
                    network.put("dataset_id",datasetId);network.put("configuration",operationConfiguration(analysis,"network_analysis"));
                    operations.add(network);networkSources.add(String.valueOf(network.get("id")));evidenceOperations.add(String.valueOf(network.get("id")));
                }
            }
            List<String> molecularNetworkSources=List.copyOf(networkSources);
            var preservationReferences=new java.util.LinkedHashSet<String>();
            if(preservationGraphsRequested){var references=StudyIO.list(StudyNetworkPreservationPolicy.resolve(StudyIO.optionalMap(analysis.get("network_preservation"),"network_preservation")).get("references"),"references");for(String source:molecularNetworkSources){var producer=operations.stream().map(raw->StudyIO.map(raw,"operation")).filter(op->source.equals(op.get("id"))).findFirst().orElseThrow();String alias=producer.containsKey("supplied_result_id")?"supplied:"+producer.get("supplied_result_id"):"dataset:"+producer.get("dataset_id");if(references.isEmpty()||references.contains(alias))preservationReferences.add(source);}}
            if (goals.contains("pathway_network")) {
                String sourceKind = String.valueOf(StudyNetworkPolicy.resolve(StudyIO.optionalMap(analysis.get("network"),"network")).get("source_kind"));
                List<String> sources = operations.stream().map(raw -> StudyIO.map(raw,"operation")).filter(op -> hypothesisId.equals(op.get("hypothesis")) && sourceKind.equals(op.get("type"))).map(op -> String.valueOf(op.get("id"))).toList();
                List<List<String>> scopes=sources.isEmpty()?List.of(List.of()):sources.stream().map(List::of).toList();
                for(List<String> scope:scopes){var network = operation(counter,"pathway_network",hypothesisId,selected,scope);network.put("allow_incomplete_dependencies",true);
                    operations.add(network);networkSources.add(String.valueOf(network.get("id")));evidenceOperations.add(String.valueOf(network.get("id")));}
            }
            String moduleOwner=null;
            if(providedModules!=null){final String id=providedModules;var producer=operations.stream().map(raw->StudyIO.map(raw,"operation")).filter(op->id.equals(op.get("id"))).findFirst().orElseThrow();var owners=StudyIO.list(producer.get("depends_on"),"depends_on").stream().map(Object::toString).filter(networkSources::contains).toList();if(owners.size()==1)moduleOwner=owners.get(0);}
            if (preservationGraphsRequested || missingModules || missingModuleGo&&providedModules==null) for (String source : networkSources) {
                if(!preservationReferences.contains(source)&&!missingModules&&!(missingModuleGo&&providedModules==null))continue;
                String modulesId;
                if(source.equals(moduleOwner))modulesId=providedModules;
                else {var modules=operation(counter,"network_modules",hypothesisId,List.of(),List.of(source));operations.add(modules);evidenceOperations.add(String.valueOf(modules.get("id")));modulesId=String.valueOf(modules.get("id"));}
                if(preservationReferences.contains(source))moduleSources.add(modulesId);
                if(missingModuleGo&&providedModules==null){var moduleGo=operation(counter,"module_gene_ontology",hypothesisId,List.of(),List.of(modulesId));moduleGo.put("allow_incomplete_dependencies",true);operations.add(moduleGo);evidenceOperations.add(String.valueOf(moduleGo.get("id")));}
            }
            if(missingModuleGo&&providedModules!=null){var moduleGo=operation(counter,"module_gene_ontology",hypothesisId,List.of(),List.of(providedModules));moduleGo.put("allow_incomplete_dependencies",true);operations.add(moduleGo);evidenceOperations.add(String.valueOf(moduleGo.get("id")));}
            if(preservationRequested){var dependencies=new ArrayList<>(molecularNetworkSources);dependencies.addAll(moduleSources);var preservation=operation(counter,"network_preservation",hypothesisId,selected,dependencies);preservation.put("allow_incomplete_dependencies",true);preservation.put("configuration",operationConfiguration(analysis,"network_preservation"));operations.add(preservation);evidenceOperations.add(String.valueOf(preservation.get("id")));}
            if(nullRequested){var dependencies=new ArrayList<>(molecularNetworkSources);dependencies.addAll(moduleSources);if(supplied.containsKey("network_preservation"))dependencies.add(supplied.get("network_preservation"));var test=operation(counter,"network_preservation_null",hypothesisId,selected,dependencies);test.put("allow_incomplete_dependencies",true);operations.add(test);evidenceOperations.add(String.valueOf(test.get("id")));}
            List<String> reviewDependencies = new ArrayList<>(followupDependencies);
            for(String qc:prefitOperations.values())if(!reviewDependencies.contains(qc))reviewDependencies.add(qc);
            if(subgroupInteractions!=null)reviewDependencies.add(subgroupInteractions);
            boolean characteristicsRequested = !"manual".equals(analysis.get("mode")) || selected.stream().anyMatch(id ->
                StudyIO.optionalMap(datasets.get(id).get("columns"), "columns").containsKey("characteristics"));
            if (characteristicsRequested && selected.stream().anyMatch(id -> StudyIO.optionalMap(datasets.get(id).get("files"), "files").containsKey("phenotype"))) {
                Map<String, Object> characteristics = operation(counter, "participant_characteristics", hypothesisId, selected, List.of());
                operations.add(characteristics);
                reviewDependencies.add(String.valueOf(characteristics.get("id")));
            }
            if (!reviewDependencies.contains(String.valueOf(harmonize.get("id"))))
                reviewDependencies.add(String.valueOf(harmonize.get("id")));
            for (String result : resultOperations)
                if (!result.startsWith("dataset:") && !reviewDependencies.contains(result))
                    reviewDependencies.add(result);
            if (comparison != null && !reviewDependencies.contains(comparison))
                reviewDependencies.add(comparison);
            if (effectComparison != null
                    && !reviewDependencies.contains(effectComparison))
                reviewDependencies.add(effectComparison);
            if (meta != null && !reviewDependencies.contains(meta))
                reviewDependencies.add(meta);
            for (String evidence : evidenceOperations) if (!reviewDependencies.contains(evidence)) reviewDependencies.add(evidence);
            if(goals.contains("gwas_diagnostics")&&!supplied.containsKey("gwas_diagnostics")) {
                var calibration=operation(counter,"gwas_diagnostics",hypothesisId,selected,reviewDependencies);
                calibration.put("allow_incomplete_dependencies",true);operations.add(calibration);reviewDependencies.add(String.valueOf(calibration.get("id")));
            }
            if (goals.contains("candidate_prioritization")) {
                Map<String, Object> priority = operation(counter,
                    "candidate_prioritization", hypothesisId, selected,
                    reviewDependencies);
                priority.put("allow_incomplete_dependencies", true);
                operations.add(priority);
                reviewDependencies.add(String.valueOf(priority.get("id")));
            }
            Map<String, Object> assistance = StudyIO.optionalMap(
                study.get("assistance"), "assistance");
            Set<String> selectedCohorts = new LinkedHashSet<>();
            for (String datasetId : selected) {
                Map<String, Object> dataset = datasets.get(datasetId);
                if (dataset != null && dataset.get("cohort") instanceof String cohort
                        && !cohort.isBlank()) selectedCohorts.add(cohort);
            }
            var heterogeneityPolicy=StudyCohortPolicy.heterogeneity(StudyIO.optionalMap(analysis.get("heterogeneity"),"heterogeneity"));
            boolean automaticLeaveOneOut = meta != null && selectedCohorts.size() >= 3 && !"disabled".equals(heterogeneityPolicy.get("leave_one_cohort_out"));
            boolean automaticEffectQc = !Boolean.FALSE.equals(StudyIO.optionalMap(
                StudyIO.optionalMap(analysis.get("harmonization"), "harmonization").get("effect_outliers"),
                "effect_outliers").get("enabled")) && !resultOperations.isEmpty() && !supplied.containsKey("meta_analysis");
            boolean requestedSensitivity = !StudyIO.optionalList(
                assistance.get("allow_analyses"), "assistance.allow_analyses")
                .isEmpty();
            if (automaticLeaveOneOut || automaticEffectQc || requestedSensitivity) {
                Map<String, Object> sensitivity = operation(counter,
                    "sensitivity_analysis", hypothesisId, selected,
                    reviewDependencies);
                sensitivity.put("allow_incomplete_dependencies", true);
                Map<String, Object> configuration = new LinkedHashMap<>(
                    operationConfiguration(analysis, "sensitivity_analysis"));
                configuration.put("automatic_leave_one_cohort_out",
                    automaticLeaveOneOut);
                configuration.put("automatic_unfiltered_effect_qc", automaticEffectQc);
                sensitivity.put("configuration", configuration);
                operations.add(sensitivity);
                reviewDependencies.add(String.valueOf(sensitivity.get("id")));
            }
            Map<String, Object> diagnostics = operation(counter,
                "diagnostic_review", hypothesisId, selected,
                reviewDependencies.isEmpty()
                    ? List.of(String.valueOf(harmonize.get("id")))
                    : reviewDependencies);
            diagnostics.put("allow_incomplete_dependencies", true);
            operations.add(diagnostics);
            reviewDependencies.add(String.valueOf(diagnostics.get("id")));
            if (Boolean.TRUE.equals(assistance.get("enabled")) && Boolean.TRUE.equals(assistance.get("next_steps"))) {
                Map<String, Object> advisor = operation(counter, "advisor_review", hypothesisId, selected, reviewDependencies);
                advisor.put("allow_incomplete_dependencies", true);
                operations.add(advisor);
                reviewDependencies.add(String.valueOf(advisor.get("id")));
            }
            Map<String, Object> report = operation(counter, "report",
                hypothesisId, selected, reviewDependencies);
            report.put("allow_incomplete_dependencies", true);
            operations.add(report);
        }
        if (operations.isEmpty())
            throw new IllegalArgumentException("No active hypotheses can be planned");

        var mrFamilies=StudyMrFamilyPolicy.resolve(study);
        if(!mrFamilies.isEmpty()) {
            var reports=operations.stream().map(raw->StudyIO.map(raw,"operation")).filter(op->"report".equals(op.get("type"))).toList();
            operations.removeIf(raw->"report".equals(StudyIO.map(raw,"operation").get("type")));
            var additions=new LinkedHashMap<String,List<String>>();
            for(var family:mrFamilies){var dependencies=new ArrayList<String>();var members=StudyIO.list(family.get("hypotheses"),"hypotheses");
                for(Object h:members){var producers=operations.stream().map(raw->StudyIO.map(raw,"operation")).filter(op->h.equals(op.get("hypothesis"))&&"mendelian_randomization".equals(op.get("type"))).toList();if(producers.size()!=1)throw new IllegalArgumentException("MR testing family member requires exactly one planned primary MR producer: "+h);dependencies.add(String.valueOf(producers.get(0).get("id")));}
                var op=operation(new Counter("mr-family"),"mr_family_adjustment","__study__",List.of(),dependencies);String id="global-mr-family-"+family.get("id");op.put("id",id);op.put("scope","study");op.put("configuration",family);op.put("allow_incomplete_dependencies",true);operations.add(op);
                for(Object h:members)additions.computeIfAbsent(String.valueOf(h),ignored->new ArrayList<>()).add(id);
            }
            for(var report:reports){var dependencies=new ArrayList<>(StudyIO.list(report.get("depends_on"),"depends_on"));dependencies.addAll(additions.getOrDefault(String.valueOf(report.get("hypothesis")),List.of()));report.put("depends_on",dependencies);operations.add(report);}
        }

        Map<String, Object> assistance = StudyIO.optionalMap(study.get("assistance"), "assistance");
        boolean impactRequested = Boolean.TRUE.equals(assistance.get("enabled"))
            && (Boolean.TRUE.equals(assistance.get("impact")) || Boolean.TRUE.equals(assistance.get("manuscript")))
            || StudyIO.list(study.get("hypotheses"), "hypotheses").stream().map(raw -> StudyIO.map(raw, "hypothesis"))
                .filter(h -> !"retired".equals(h.get("status"))).anyMatch(h -> goals(StudyIO.map(h.get("analysis"), "analysis")).contains("impact_assessment"));
        boolean inventoryRequested=impactRequested||StudyIO.list(study.get("hypotheses"),"hypotheses").stream().map(raw->StudyIO.map(raw,"hypothesis")).filter(h->!"retired".equals(h.get("status"))).anyMatch(h->goals(StudyIO.map(h.get("analysis"),"analysis")).contains("finding_inventory"));
        if (inventoryRequested) {
            List<Map<String, Object>> reports = operations.stream().map(raw -> StudyIO.map(raw, "operation")).filter(op -> "report".equals(op.get("type"))).toList();
            operations.removeIf(raw -> "report".equals(StudyIO.map(raw, "operation").get("type")));
            List<String> dependencies = operations.stream().map(raw -> String.valueOf(StudyIO.map(raw, "operation").get("id"))).toList();
            List<String> selected = operations.stream().map(raw -> StudyIO.map(raw, "operation")).flatMap(op -> StudyIO.list(op.get("datasets"), "datasets").stream()).map(Object::toString).distinct().toList();
            Counter counter = new Counter("study-wide");
            var inventory = operation(counter, "finding_inventory", "__study__", selected, dependencies);
            inventory.put("id", "global-study-finding-inventory"); inventory.put("scope", "study"); inventory.put("allow_incomplete_dependencies", true); operations.add(inventory);
            List<String> publication = new ArrayList<>(List.of(String.valueOf(inventory.get("id"))));
            if(impactRequested){var impact = operation(counter, "impact_assessment", "__study__", selected, List.of(String.valueOf(inventory.get("id"))));
                impact.put("id", "global-study-impact-assessment"); impact.put("scope", "study"); operations.add(impact);publication.add(String.valueOf(impact.get("id")));}
            if (Boolean.TRUE.equals(assistance.get("enabled")) && Boolean.TRUE.equals(assistance.get("manuscript"))) {
                var draft = operation(counter, "manuscript_draft", "__study__", selected, publication);
                draft.put("id", "global-study-manuscript-draft"); draft.put("scope", "study"); operations.add(draft); publication.add(String.valueOf(draft.get("id")));
            }
            for (var report : reports) {
                var inputs = new ArrayList<>(StudyIO.list(report.get("depends_on"), "depends_on")); inputs.addAll(publication); report.put("depends_on", inputs); operations.add(report);
            }
        }

        StudyAssayQcPlan.attach(study, operations);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("schema_version", 1);
        plan.put("project_id", project.get("id"));
        plan.put("resolved_study_hash", resolvedHash);
        plan.put("status", "proposed");
        plan.put("operations", operations);
        plan.put("pending_decisions", decisions);
        String identity = StudyIO.hash(plan);
        plan.put("plan_id", "plan-" + identity.substring(0, 12));
        plan.put("seed", Long.parseUnsignedLong(identity.substring(0, 15), 16));
        plan.put("plan_hash", hashWithoutPlanHash(plan));
        return new StudyPlan(plan);
    }

    public static StudyPlan create(Path project, StudyProject.Actor actor)
            throws IOException {
        return StudyProjectLock.with(project, () -> createLocked(project, actor));
    }

    private static StudyPlan createLocked(Path project, StudyProject.Actor actor) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        StudyPlan plan = compile(StudyProject.resolved(root));
        Path path = root.resolve("plans").resolve(plan.id())
            .resolve("analysis-plan.yaml");
        if (!Files.exists(path)) StudyIO.writeYaml(path, plan.document());
        Map<String, Object> status = StudyProject.status(root);
        Object active = status.get("active_plan");
        if (active instanceof Map<?, ?> map && plan.id().equals(map.get("plan_id"))
                && plan.hash().equals(map.get("plan_hash")))
            return plan;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan_id", plan.id());
        payload.put("plan_hash", plan.hash());
        payload.put("resolved_study_hash",
            plan.document().get("resolved_study_hash"));
        payload.put("state", "waiting_for_plan_approval");
        payload.put("phase", "planning");
        payload.put("summary", "Compiled deterministic analysis plan " + plan.id());
        StudyProject.append(root, "plan_created", actor, payload);
        StudyQuestions.reconcile(root, plan, null);
        StudyEvidenceIndex.rebuild(root);
        return plan;
    }

    public static void approve(Path project, String planId,
            StudyProject.Actor approver, boolean acceptAssumptions)
            throws IOException {
        StudyProjectLock.with(project, () -> { approveLocked(project, planId, approver, acceptAssumptions); return null; });
    }

    private static void approveLocked(Path project, String planId,
            StudyProject.Actor approver, boolean acceptAssumptions) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        Path path = root.resolve("plans").resolve(planId)
            .resolve("analysis-plan.yaml");
        Map<String, Object> plan = StudyIO.load(path);
        if (!planId.equals(plan.get("plan_id")))
            throw new IllegalArgumentException("Plan ID does not match its path");
        String calculated = hashWithoutPlanHash(plan);
        if (!calculated.equals(plan.get("plan_hash")))
            throw new IllegalArgumentException("Plan hash is invalid");
        Map<String, Object> status = StudyProject.status(root);
        Map<String, Object> active = StudyIO.map(
            status.get("active_plan"), "active_plan");
        if (!planId.equals(active.get("plan_id"))
                || !calculated.equals(active.get("plan_hash"))
                || !"proposed".equals(active.get("status")))
            throw new IllegalArgumentException(
                "Only the current proposed plan can be approved");
        String currentStudyHash = StudyProject.resolvedHash(
            StudyProject.resolved(root).document());
        if (!currentStudyHash.equals(plan.get("resolved_study_hash")))
            throw new IllegalArgumentException(
                "Plan was compiled from a superseded resolved study");
        List<Object> decisions = StudyIO.optionalList(
            plan.get("pending_decisions"), "pending_decisions");
        List<Object> approvableAssumptions = decisions.stream()
            .filter(item -> !Boolean.TRUE.equals(StudyIO.map(item, "decision").get("requires_explicit_review")))
            .toList();
        if (!approvableAssumptions.isEmpty() && !acceptAssumptions)
            throw new IllegalArgumentException(
                "Plan has pending assumptions; review them and use --accept-assumptions to approve explicitly");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("plan_id", planId);
        payload.put("plan_hash", plan.get("plan_hash"));
        payload.put("accepted_assumptions", acceptAssumptions
            ? approvableAssumptions.stream().map(item -> StudyIO.map(item, "decision").get("id")).toList()
            : List.of());
        List<Object> gatedAssumptions = decisions.stream()
            .filter(item -> Boolean.TRUE.equals(StudyIO.map(item, "decision").get("requires_explicit_review")))
            .map(item -> StudyIO.map(item, "decision").get("id")).toList();
        if (!gatedAssumptions.isEmpty()) payload.put("assumptions_deferred_to_scientist_questions", gatedAssumptions);
        payload.put("state", "ready");
        payload.put("phase", "execution");
        payload.put("summary", "Approved analysis plan " + planId);
        StudyProject.append(root, "plan_approved", approver, payload);
    }

    public static StudyPlan loadApproved(Path project) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        Map<String, Object> status = StudyProject.status(root);
        Map<String, Object> active = StudyIO.map(
            status.get("active_plan"), "active_plan");
        if (!"approved".equals(active.get("status")))
            throw new IllegalArgumentException("The active plan is not approved");
        String id = String.valueOf(active.get("plan_id"));
        Map<String, Object> document = StudyIO.load(root.resolve("plans")
            .resolve(id).resolve("analysis-plan.yaml"));
        if (!hashWithoutPlanHash(document).equals(document.get("plan_hash")))
            throw new IllegalArgumentException("Approved plan hash is invalid");
        if (!String.valueOf(active.get("plan_hash")).equals(document.get("plan_hash")))
            throw new IllegalArgumentException("Approved plan differs from the stored plan");
        String currentStudyHash = StudyProject.resolvedHash(
            StudyProject.resolved(root).document());
        if (!currentStudyHash.equals(document.get("resolved_study_hash")))
            throw new IllegalArgumentException(
                "Approved plan was compiled from a superseded resolved study");
        return new StudyPlan(document);
    }

    static String hashWithoutPlanHash(Map<String, Object> plan) {
        Map<String, Object> copy = StudyIO.copyMap(plan);
        copy.remove("plan_hash");
        return StudyIO.hash(copy);
    }

    private static Map<String, Object> operation(Counter counter, String type,
            String hypothesis, List<String> datasets, List<String> dependencies) {
        StudyOperationRegistry.Descriptor descriptor =
            StudyOperationRegistry.descriptor(type);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("id", counter.next(type));
        operation.put("type", type);
        operation.put("hypothesis", hypothesis);
        operation.put("datasets", List.copyOf(datasets));
        operation.put("depends_on", List.copyOf(new LinkedHashSet<>(dependencies)));
        operation.put("executor", descriptor.executor());
        operation.put("outputs", descriptor.outputs());
        operation.put("diagnostics", descriptor.diagnostics());
        operation.put("status", "planned");
        return operation;
    }

    private static List<String> dependencies(Map<String, Object> harmonize,
            List<String> results) {
        List<String> dependencies = new ArrayList<>();
        dependencies.add(String.valueOf(harmonize.get("id")));
        for (String result : results)
            if (!result.startsWith("dataset:")) dependencies.add(result);
        return dependencies;
    }

    private static Map<String, Object> operationConfiguration(
            Map<String, Object> analysis, String type) {
        Map<String, Object> operations = StudyIO.optionalMap(
            analysis.get("operations"), "analysis.operations");
        return StudyIO.optionalMap(operations.get(type),
            "analysis.operations." + type);
    }

    private static Set<String> goals(Map<String, Object> analysis) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : StudyIO.list(analysis.get("goals"), "analysis.goals"))
            result.add(String.valueOf(value).toLowerCase(Locale.ROOT)
                .replace('-', '_'));
        return result;
    }

    private static List<String> selected(Map<String, Object> inputs) {
        List<String> result = new ArrayList<>();
        for (Object value : inputs.values()) {
            if (value instanceof String text) {
                if (!result.contains(text)) result.add(text);
            } else if (value instanceof Collection<?> values)
                for (Object item : values) {
                    String id = String.valueOf(item);
                    if (!result.contains(id)) result.add(id);
                }
        }
        return result;
    }

    private static Map<String, Map<String, Object>> byId(List<Object> values) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Object value : values) {
            Map<String, Object> item = StudyIO.map(value, "dataset");
            result.put(StudyIO.requireString(item, "id", "dataset"), item);
        }
        return result;
    }

    private static List<Object> pendingAssumptions(Map<String, Object> study) {
        List<Object> result = new ArrayList<>();
        for (Object value : StudyIO.optionalList(
                study.get("assumptions"), "assumptions")) {
            Map<String, Object> assumption = StudyIO.map(value, "assumption");
            if ("pending".equals(assumption.get("review_status"))) {
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("id", assumption.get("id"));
                decision.put("type", "assumption_approval");
                decision.put("statement", assumption.get("statement"));
                decision.put("requires_explicit_review",
                    "potentially_dependent".equals(assumption.get("type")));
                result.add(decision);
            }
        }
        return result;
    }

    static String operationId(String hypothesis, int number, String type) {
        return hypothesis + "-op-" + String.format(java.util.Locale.ROOT, "%03d", number) + "-" + type;
    }

    private static final class Counter {
        private final String hypothesis;
        private int value;

        private Counter(String hypothesis) {
            this.hypothesis = hypothesis;
        }

        String next(String type) {
            return operationId(hypothesis, ++value, type);
        }
    }
}
