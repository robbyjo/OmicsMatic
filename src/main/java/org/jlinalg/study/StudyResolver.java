/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.study;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Expands a concise study contract into an explicit deterministic contract. */
public final class StudyResolver {
    private StudyResolver() { }

    public static StudyContract resolve(Path source) throws IOException {
        return resolve(StudyValidator.requireValid(source, false), List.of());
    }

    public static StudyContract resolve(StudyContract source,
            List<String> appliedAmendments) {
        Map<String, Object> resolved = source.document();
        Map<String, Object> project = StudyIO.map(resolved.get("project"), "project");
        project.putIfAbsent("objective", "publication");
        project.put("manuscript_policy", StudyWorkflowPolicy.manuscript(
            StudyIO.optionalMap(project.get("manuscript_policy"), "project.manuscript_policy")));
        project.put("finding_grouping",StudyFindingGroupingPolicy.resolve(StudyIO.optionalMap(project.get("finding_grouping"),"project.finding_grouping")));
        resolved.put("project", project);

        Map<String, Object> resources = StudyIO.optionalMap(
            resolved.get("resources"), "resources");
        resources.putIfAbsent("downloads", "automatic");
        Map<String, Object> ld = StudyIO.optionalMap(resources.get("ld"),
            "resources.ld");
        ld.putIfAbsent("database", "1000g-phase3");
        ld.putIfAbsent("population", "auto");
        ld.putIfAbsent("auto_download", Boolean.TRUE);
        resources.put("ld", ld);
        resolved.put("resources", resources);

        if (resolved.containsKey("assistance")) {
            Map<String, Object> assistance = StudyIO.optionalMap(resolved.get("assistance"), "assistance");
            assistance.putIfAbsent("provider", "browser");
            resolved.put("assistance", assistance);
        }

        List<Object> datasets = StudyIO.list(resolved.get("datasets"), "datasets");
        List<String> activeDatasetIds = new ArrayList<>();
        Map<String, Map<String, Object>> datasetById = new LinkedHashMap<>();
        for (Object value : datasets) {
            Map<String, Object> dataset = StudyIO.map(value, "dataset");
            StudyDataDictionary.apply(source.source() == null ? Path.of("").toAbsolutePath()
                : source.source().getParent(), dataset);
            dataset.putIfAbsent("stage", "processed_matrix");
            dataset.putIfAbsent("status", "active");
            String id = StudyIO.requireString(dataset, "id", "dataset");
            if (!"retired".equals(dataset.get("status"))) activeDatasetIds.add(id);
            datasetById.put(id, dataset);
        }
        resolved.put("datasets", new ArrayList<>(datasetById.values()));

        List<Object> hypotheses = StudyIO.list(resolved.get("hypotheses"), "hypotheses");
        List<Object> resolvedHypotheses = new ArrayList<>();
        Map<String,Object> followupApplicability = new LinkedHashMap<>();
        for (Object value : hypotheses) {
            Map<String, Object> hypothesis = StudyIO.map(value, "hypothesis");
            hypothesis.putIfAbsent("status", "approved");
            Map<String, Object> inputs = StudyIO.optionalMap(
                hypothesis.get("inputs"), "hypothesis.inputs");
            if (inputs.isEmpty() && !activeDatasetIds.isEmpty()) inputs.put("discovery", List.copyOf(activeDatasetIds));
            normalizeRoles(inputs);
            hypothesis.put("inputs", inputs);
            Map<String, Object> analysis = StudyIO.optionalMap(
                hypothesis.get("analysis"), "hypothesis.analysis");
            if (analysis.containsKey("supplied_results"))
                analysis.put("supplied_results", new ArrayList<>(StudySuppliedResultPolicy.resolve(analysis)));
            StudyAnalysisReferences.validate(hypothesis, analysis, datasetById);
            analysis.putIfAbsent("mode", "auto");
            analysis.putIfAbsent("direction", "two_sided");
            analysis.putIfAbsent("multiple_testing",
                new LinkedHashMap<>(Map.of("method", "BH", "alpha", 0.05)));
            Set<String> selectedForPolicy = new LinkedHashSet<>();
            inputs.values().forEach(role -> StudyIO.list(role, "inputs role").forEach(item -> selectedForPolicy.add(String.valueOf(item))));
            boolean genetic = selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull)
                .anyMatch(dataset -> Set.of("gwas", "genomics").contains(String.valueOf(dataset.get("type"))));
            StudyWorkflowPolicy.resolveAssociation(analysis, genetic);
            analysis.put("pooling", StudyPoolingPolicy.resolve(StudyIO.optionalMap(analysis.get("pooling"), "pooling")));
            analysis.put("primary_result",StudyPrimaryPolicy.resolve(StudyIO.optionalMap(analysis.get("primary_result"),"primary_result")));
            analysis.put("cohort_overlap",StudyCohortPolicy.overlap(StudyIO.optionalMap(analysis.get("cohort_overlap"),"cohort_overlap")));
            analysis.put("heterogeneity",StudyCohortPolicy.heterogeneity(StudyIO.optionalMap(analysis.get("heterogeneity"),"heterogeneity")));
            analysis.put("colocalization",StudyColocalizationPolicy.resolve(StudyIO.optionalMap(analysis.get("colocalization"),"colocalization")));
            analysis.put("network", StudyNetworkPolicy.resolve(StudyIO.optionalMap(analysis.get("network"), "network")));
            analysis.put("network_preservation", StudyNetworkPreservationPolicy.resolve(StudyIO.optionalMap(analysis.get("network_preservation"), "network_preservation")));
            analysis.put("network_preservation_null", StudyNetworkNullPolicy.resolve(StudyIO.optionalMap(analysis.get("network_preservation_null"), "network_preservation_null")));
            var omnibus=StudyIO.optionalMap(analysis.get("rare_omnibus"),"rare_omnibus");var omnibusSources=StudySuppliedResultPolicy.resolve(analysis);if(!omnibus.containsKey("selection")&&omnibusSources.stream().anyMatch(d->"rare_set_omnibus".equals(d.get("kind"))&&"primary".equals(d.get("role")))&&omnibusSources.stream().noneMatch(d->"rare_variant_analysis".equals(d.get("kind"))&&"primary".equals(d.get("role"))))omnibus.put("selection","primary");analysis.put("rare_omnibus",StudyRareOmnibusPolicy.resolve(omnibus));
            analysis.put("gwas_enrichment",StudyGwasEnrichmentPolicy.resolve(StudyIO.optionalMap(analysis.get("gwas_enrichment"),"gwas_enrichment")));
            analysis.put("gwas_diagnostics",StudyGwasDiagnosticPolicy.resolve(StudyIO.optionalMap(analysis.get("gwas_diagnostics"),"gwas_diagnostics")));
            for(String id:StudyIO.map(StudyIO.map(analysis.get("gwas_diagnostics"),"gwas_diagnostics").get("dataset_coverage"),"dataset_coverage").keySet())if(!selectedForPolicy.contains(id)||datasetById.get(id)==null||!Set.of("gwas","genomics").contains(datasetById.get(id).get("type")))throw new IllegalArgumentException("GWAS diagnostic coverage declarations must name selected genomic datasets");
            analysis.put("gsea",StudyGseaPolicy.resolve(StudyIO.optionalMap(analysis.get("gsea"),"gsea")));
            analysis.put("risk_score",StudyRiskScorePolicy.resolve(StudyIO.optionalMap(analysis.get("risk_score"),"risk_score")));
            analysis.put("protein_structure",StudyProteinPolicy.resolve(StudyIO.optionalMap(analysis.get("protein_structure"),"protein_structure")));
            analysis.put("subgroup_interactions",StudySubgroupInteractionPolicy.resolve(StudyIO.optionalMap(analysis.get("subgroup_interactions"),"subgroup_interactions")));
            var assayQc=StudyAssayQcPolicy.resolve(StudyIO.optionalMap(analysis.get("assay_qc"),"assay_qc"));analysis.put("assay_qc",assayQc);
            for(String scope:StudyIO.optionalMap(assayQc.get("dataset_overrides"),"assay overrides").keySet())if(!selectedForPolicy.contains(scope)||datasetById.get(scope)==null||!StudyAssayQcPolicy.MODALITIES.contains(datasetById.get(scope).get("type")))throw new IllegalArgumentException("Assay QC overrides must name selected bulk molecular datasets");
            var genotypeQc=StudyGenotypeSampleQcPolicy.resolve(StudyIO.optionalMap(analysis.get("genotype_sample_qc"),"genotype_sample_qc"));analysis.put("genotype_sample_qc",genotypeQc);
            for(String scope:StudyIO.optionalMap(genotypeQc.get("dataset_sources"),"dataset_sources").keySet())if(!selectedForPolicy.contains(scope)||datasetById.get(scope)==null||!Set.of("gwas","genomics").contains(datasetById.get(scope).get("type")))throw new IllegalArgumentException("Dataset QC source overrides must name selected genotype datasets");
            var pcCovariates=StudyGenotypeSampleQcPolicy.pcCovariates(genotypeQc);if(!pcCovariates.isEmpty()){
                for(String datasetId:selectedForPolicy){var d=datasetById.get(datasetId);if(d!=null&&Set.of("processed_matrix","quantified_matrix","raw_quantified").contains(d.get("stage"))&&!Set.of("gwas","genomics").contains(d.get("type")))throw new IllegalArgumentException("Generated genotype PCs require a separate hypothesis for non-genotype participant matrices");}
                var covariates=new ArrayList<>(StudyIO.optionalList(hypothesis.get("covariates"),"hypothesis.covariates"));for(String pc:pcCovariates)if(!covariates.contains(pc))covariates.add(pc);hypothesis.put("covariates",covariates);
            }
            if (genetic || analysis.containsKey("variant_followup")) analysis.put("variant_followup", StudyVariantFollowupPolicy.resolve(
                StudyIO.optionalMap(analysis.get("variant_followup"), "variant_followup")));
            boolean rareApplicable = selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull)
                .anyMatch(dataset -> StudyRareVariantPolicy.setInput(dataset) || Set.of("gwas", "genomics").contains(dataset.get("type"))
                    && Set.of("raw_quantified", "quantified_matrix", "processed_matrix").contains(dataset.get("stage")));
            boolean defaultRare=rareApplicable&&(!"manual".equals(analysis.get("mode"))||analysis.containsKey("rare_variants")||StudyIO.list(analysis.get("goals"),"analysis.goals").stream().anyMatch(goal->String.valueOf(goal).toLowerCase(java.util.Locale.ROOT).replace('-','_').equals("rare_variant_analysis")));
            if (rareApplicable || analysis.containsKey("rare_variants")) analysis.put("rare_variants", StudyRareVariantPolicy.resolve(
                StudyIO.optionalMap(analysis.get("rare_variants"), "rare_variants"), StudyIO.optionalMap(analysis.get("association"), "association"), defaultRare));
            if (inputs.containsKey("replication")) {
                Map<String, Object> replication = StudyIO.optionalMap(analysis.get("replication"), "analysis.replication");
                replication.putIfAbsent("adjustment_scope", "all_results");
                replication.putIfAbsent("require_same_sign", true);
                replication.putIfAbsent("threshold_comparison", "less_than");
                analysis.put("replication", replication);
            }
            List<Object> inferred = defaultGoals(inputs, datasetById,
                project.get("objective"));
            var proteinPolicy=StudyIO.map(analysis.get("protein_structure"),"protein_structure");
            boolean proteinApplicable=selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull).anyMatch(d->Set.of("proteomics","single_cell_protein","spatial_proteomics").contains(d.get("type")));
            if(Boolean.TRUE.equals(proteinPolicy.get("enabled"))&&(proteinApplicable||proteinPolicy.containsKey("manifest")))inferred.add("protein_structure");
            boolean molecularMatrix = selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull).anyMatch(dataset ->
                StudyNetworkPolicy.MOLECULAR.contains(String.valueOf(dataset.get("type"))) && Set.of("raw_quantified", "quantified_matrix", "processed_matrix").contains(dataset.get("stage")));
            if (molecularMatrix && Boolean.TRUE.equals(StudyIO.map(analysis.get("network"),"network").get("enabled"))) inferred.addAll(List.of("network_analysis","network_modules","module_gene_ontology"));
            if(Boolean.TRUE.equals(StudyIO.map(analysis.get("subgroup_interactions"),"subgroup_interactions").get("enabled")))inferred.add("subgroup_interactions");
            var gwasEnrichmentPolicy=StudyIO.map(analysis.get("gwas_enrichment"),"gwas_enrichment");
            var suppliedForApplicability=StudySuppliedResultPolicy.resolve(analysis);
            var suppliedFollowups=StudySuppliedFollowups.inspect(String.valueOf(hypothesis.get("id")),analysis);
            boolean suppliedGenetic=suppliedFollowups.variants();
            var suppliedGoals=StudySuppliedFollowups.goals(suppliedFollowups,analysis);
            inferred.addAll(suppliedGoals);
            var applicabilityReceipt=new LinkedHashMap<String,Object>(suppliedFollowups.receipt());
            applicabilityReceipt.put("inferred_followup_goals",suppliedGoals);
            applicabilityReceipt.put("automatic_goal_merge",!analysis.containsKey("goals")||"auto".equalsIgnoreCase(String.valueOf(analysis.get("mode"))));
            followupApplicability.put(String.valueOf(hypothesis.get("id")),applicabilityReceipt);
            boolean genomicAssociation=selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull).anyMatch(d->Set.of("gwas","genomics").contains(d.get("type"))&&(
                Set.of("raw_quantified","quantified_matrix","processed_matrix","association_results","annotated_results","meta_analysis_results").contains(d.get("stage"))
                ||StudyAnalysisReferences.isReference(d)&&suppliedForApplicability.stream().anyMatch(owner->"primary".equals(owner.get("role"))&&Set.of("meta_analysis","discovery_meta_analysis").contains(owner.get("kind"))&&owner.get("id").equals(d.get("supplied_result_id")))));
            if((genomicAssociation||suppliedGenetic)&&Boolean.TRUE.equals(StudyIO.map(analysis.get("gwas_diagnostics"),"gwas_diagnostics").get("enabled")))inferred.add("gwas_diagnostics");
            if(Boolean.TRUE.equals(gwasEnrichmentPolicy.get("enabled"))&&(genomicAssociation||StudyIO.optionalMap(gwasEnrichmentPolicy.get("summary"),"summary").containsKey("file")))inferred.add("gwas_enrichment");
            var gseaPolicy=StudyIO.map(analysis.get("gsea"),"gsea");
            boolean molecularResults=selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull).anyMatch(dataset -> StudyNetworkPolicy.MOLECULAR.contains(String.valueOf(dataset.get("type")))&&Set.of("association_results","annotated_results").contains(dataset.get("stage")));
            if(Boolean.TRUE.equals(gseaPolicy.get("enabled"))&&(molecularMatrix||molecularResults||gseaPolicy.containsKey("ranking"))) {
                inferred.add("ranked_gsea");if(Boolean.TRUE.equals(StudyIO.map(analysis.get("network"),"network").get("enabled")))inferred.addAll(List.of("pathway_network","network_modules","module_gene_ontology"));
            }
            Set<String> suppliedKinds = StudySuppliedResultPolicy.resolve(analysis).stream().filter(row -> "primary".equals(row.get("role")))
                .map(row -> String.valueOf(row.get("kind"))).collect(java.util.stream.Collectors.toSet());
            if(Boolean.TRUE.equals(StudyIO.map(analysis.get("risk_score"),"risk_score").get("enabled"))&&(molecularMatrix||StudyIO.map(analysis.get("risk_score"),"risk_score").containsKey("file")))inferred.add("risk_score_evaluation");
            if(suppliedKinds.contains("risk_score_evaluation")&&!suppliedKinds.contains("risk_score_related_phenotypes")&&!StudyIO.list(StudyIO.map(analysis.get("risk_score"),"risk_score").get("related_phenotypes"),"related_phenotypes").isEmpty())inferred.add("risk_score_related_phenotypes");
            var networkPolicy=StudyIO.map(analysis.get("network"),"network");
            if(Boolean.TRUE.equals(StudyIO.map(analysis.get("network_preservation_null"),"network_preservation_null").get("enabled")))inferred.add("network_preservation_null");
            boolean suppliedOmnibusPrimary=suppliedKinds.contains("rare_set_omnibus")&&StudyRareOmnibusPolicy.primary(analysis);
            if(suppliedOmnibusPrimary)inferred.remove("rare_variant_analysis");
            if(Boolean.TRUE.equals(StudyIO.map(analysis.get("rare_omnibus"),"rare_omnibus").get("enabled"))&&(suppliedKinds.contains("rare_variant_analysis")||suppliedKinds.contains("rare_set_omnibus")||Boolean.TRUE.equals(StudyIO.optionalMap(analysis.get("rare_variants"),"rare_variants").get("enabled"))))inferred.add("rare_set_omnibus");
            if(Boolean.TRUE.equals(networkPolicy.get("enabled"))&&Boolean.TRUE.equals(StudyIO.map(analysis.get("network_preservation"),"network_preservation").get("enabled"))){
                var graphCoverage=StudyNetworkReuse.coverage(analysis,datasetById,List.copyOf(selectedForPolicy));
                long suppliedGraphs=StudySuppliedResultPolicy.resolve(analysis).stream().filter(d->"network_analysis".equals(d.get("kind"))&&"primary".equals(d.get("role"))).count();
                long nativeGraphs=selectedForPolicy.stream().map(datasetById::get).filter(java.util.Objects::nonNull).filter(d->StudyNetworkPolicy.MOLECULAR.contains(String.valueOf(d.get("type")))&&Set.of("raw_quantified","quantified_matrix","processed_matrix").contains(d.get("stage")))
                    .filter(d->suppliedGraphs==0||graphCoverage.resolved()&&Boolean.TRUE.equals(networkPolicy.get("complete_missing"))&&!graphCoverage.covered().contains(String.valueOf(d.get("id")))).count();
                if(suppliedGraphs+nativeGraphs>=2)inferred.add("network_preservation");
            }
            if(Boolean.TRUE.equals(networkPolicy.get("enabled")) && suppliedKinds.contains(String.valueOf(networkPolicy.get("source_kind"))))
                inferred.addAll(List.of("pathway_network","network_modules","module_gene_ontology"));
            if (suppliedKinds.contains("meta_analysis")) {
                // Reusing a pooled result does not require reconstructing its
                // unavailable source associations. Explicit requests still win
                // when goals are merged below.
                boolean needsValidation = inputs.containsKey("replication") && !StudyIO.list(inputs.get("replication"), "replication").isEmpty()
                    && !suppliedKinds.contains("discovery_replication");
                if (!needsValidation) inferred.remove("association");
                if (!inputs.containsKey("replication")) inferred.remove("discovery_replication");
            }
            if (!analysis.containsKey("goals")) analysis.put("goals", inferred);
            else if ("auto".equalsIgnoreCase(String.valueOf(analysis.get("mode")))) {
                LinkedHashSet<Object> goals = new LinkedHashSet<>(
                    StudyIO.list(analysis.get("goals"), "analysis.goals"));
                goals.addAll(inferred);
                analysis.put("goals", new ArrayList<>(goals));
            }
            if (analysis.containsKey("bioinformatics") || StudyIO.list(analysis.get("goals"), "analysis.goals").contains("literature_search")
                    || StudyIO.list(analysis.get("goals"), "analysis.goals").contains("mendelian_randomization")) {
                var bio = StudyIO.optionalMap(analysis.get("bioinformatics"), "analysis.bioinformatics");
                bio.putIfAbsent("feature_source","auto");
                bio.putIfAbsent("literature_max_features", bio.getOrDefault("max_features", 100));
                bio.putIfAbsent("literature_max_queries", 1000);
                bio.putIfAbsent("literature_selection", "auto");
                if (bio.containsKey("mr") || bio.containsKey("mr_testing") || StudyIO.list(analysis.get("goals"), "analysis.goals").contains("mendelian_randomization"))
                    bio.put("mr_testing", StudyMrTestingPolicy.resolve(StudyIO.optionalMap(bio.get("mr_testing"), "mr_testing")));
                analysis.put("bioinformatics", bio);
            }
            hypothesis.put("analysis", analysis);
            resolvedHypotheses.add(hypothesis);
        }
        resolved.put("hypotheses", resolvedHypotheses);
        var assistance = StudyIO.optionalMap(resolved.get("assistance"), "assistance");
        if (Boolean.TRUE.equals(assistance.get("manuscript"))) { assistance.putIfAbsent("impact", true); assistance.putIfAbsent("manuscript_max_output_tokens", 24000); }
        if (Boolean.TRUE.equals(assistance.get("impact"))) {
            assistance.putIfAbsent("impact_batch_size", 8); assistance.putIfAbsent("impact_max_payload_chars", 120000);
            assistance.putIfAbsent("impact_max_calls", 100); assistance.putIfAbsent("impact_max_output_tokens", 8000);
        }
        if (!assistance.isEmpty()) resolved.put("assistance", assistance);

        List<Object> assumptions = assumptions(resolved, datasetById);
        resolved.put("assumptions", assumptions);

        Map<String, Object> resolution = new LinkedHashMap<>();
        resolution.put("schema_version", 1);
        resolution.put("defaults_version", 50);
        resolution.put("followup_applicability", followupApplicability);
        resolution.put("source_study_hash", source.hash());
        resolution.put("applied_amendments", List.copyOf(appliedAmendments));
        resolution.put("default_seed_policy", "derived_from_approved_plan_hash");
        resolved.put("_resolution", resolution);
        String resolvedHash = hashWithoutResolvedHash(resolved);
        resolution.put("resolved_study_hash", resolvedHash);

        StudyValidation validation = StudyValidator.validateDocument(
            resolved, source.source() == null ? Path.of("").toAbsolutePath()
                : source.source().getParent(), false);
        if (!validation.valid())
            throw new IllegalArgumentException(
                "Resolved study is invalid:\n" + validation.report());
        return new StudyContract(source.source(), resolved);
    }

    static String hashWithoutResolvedHash(Map<String, Object> document) {
        Map<String, Object> copy = StudyIO.copyMap(document);
        Map<String, Object> resolution = StudyIO.optionalMap(
            copy.get("_resolution"), "_resolution");
        resolution.remove("resolved_study_hash");
        copy.put("_resolution", resolution);
        return StudyIO.hash(copy);
    }

    private static void normalizeRoles(Map<String, Object> inputs) {
        for (String role : new ArrayList<>(inputs.keySet())) {
            Object value = inputs.get(role);
            if (value instanceof String text) inputs.put(role, List.of(text));
            else if (value instanceof Collection<?>)
                inputs.put(role, StudyIO.list(value, "inputs." + role));
        }
    }

    private static List<Object> defaultGoals(Map<String, Object> inputs,
            Map<String, Map<String, Object>> datasets, Object objective) {
        Set<String> selected = new LinkedHashSet<>();
        inputs.values().forEach(value -> {
            if (value instanceof Collection<?> items)
                items.forEach(item -> selected.add(String.valueOf(item)));
        });
        Set<String> types = new HashSet<>();
        boolean matrix = false;
        for (String id : selected) {
            Map<String, Object> dataset = datasets.get(id);
            if (dataset == null) continue;
            types.add(String.valueOf(dataset.get("type")).toLowerCase(Locale.ROOT));
            String stage = String.valueOf(dataset.get("stage"));
            matrix |= Set.of("raw_quantified", "quantified_matrix",
                "processed_matrix").contains(stage);
        }
        List<Object> goals = new ArrayList<>();
        if (matrix) goals.add("association");
        boolean hasReplication = inputs.containsKey("replication")
            && !StudyIO.list(inputs.get("replication"), "replication").isEmpty();
        if (hasReplication || selected.size() > 1) goals.add("discovery_replication");
        if (selected.size() > 1) goals.add("meta_analysis");
        if (types.stream().anyMatch(type -> Set.of("transcriptomics", "expression",
                "mirna", "lincrna", "lncrna", "extracellular_rna",
                "epigenomics", "ewas", "proteomics", "single_cell_rna",
                "spatial_transcriptomics").contains(type))) {
            goals.add("gene_ontology");
            goals.add("pathway_enrichment");
        }
        if (types.stream().anyMatch(type ->
                Set.of("gwas", "genomics").contains(type)))
            goals.add("variant_scoring");
        Set<String> objectives = new HashSet<>();
        if (objective instanceof Collection<?> values)
            values.forEach(value -> objectives.add(normalize(value)));
        else objectives.add(normalize(objective));
        if (objectives.stream().anyMatch(value -> value.contains("publication")
                || value.contains("drug") || value.contains("causal")))
            goals.add("genetic_support");
        goals.add("literature_support");
        goals.add("literature_search");
        goals.add("bioinformatics_investigation");
        if (objectives.stream().anyMatch(value -> value.contains("publication") || value.contains("manuscript") || value.contains("paper"))) goals.add("impact_assessment");
        if (goals.contains("genetic_support")) goals.add("source_discovery");
        if (objectives.stream().anyMatch(value -> value.contains("drug")))
            goals.add("candidate_prioritization");
        return goals;
    }

    private static String normalize(Object value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
    }

    private static List<Object> assumptions(Map<String, Object> document,
            Map<String, Map<String, Object>> datasets) {
        Map<String, Map<String, Object>> cohorts = new LinkedHashMap<>();
        for (Object value : StudyIO.optionalList(document.get("cohorts"), "cohorts")) {
            Map<String, Object> cohort = StudyIO.map(value, "cohort");
            cohorts.put(StudyIO.requireString(cohort, "id", "cohort"), cohort);
        }
        Set<String> explicit = new HashSet<>();
        for (Object value : StudyIO.optionalList(
                document.get("cohort_relationships"), "cohort_relationships")) {
            Map<String, Object> relationship = StudyIO.map(value, "relationship");
            List<Object> between = StudyIO.list(relationship.get("between"), "between");
            for (int left = 0; left < between.size(); left++)
                for (int right = left + 1; right < between.size(); right++)
                    explicit.add(pair(String.valueOf(between.get(left)),
                        String.valueOf(between.get(right))));
        }
        Set<String> used = new TreeSet<>();
        for (Map<String, Object> dataset : datasets.values()) {
            if ("retired".equals(dataset.get("status"))) continue;
            Object cohort = dataset.get("cohort");
            if (cohort instanceof String text) used.add(text);
            for (Object value : StudyIO.optionalList(
                    dataset.get("cohorts"), "dataset.cohorts"))
                used.add(String.valueOf(value));
        }
        List<String> ids = new ArrayList<>(used);
        List<Object> result = new ArrayList<>();
        for (Object value : StudyIO.optionalList(
                document.get("assumptions"), "assumptions")) {
            Map<String, Object> assumption = StudyIO.map(value, "assumption");
            assumption.putIfAbsent("review_status", "pending");
            result.add(assumption);
        }
        for (int left = 0; left < ids.size(); left++) {
            for (int right = left + 1; right < ids.size(); right++) {
                String a = ids.get(left), b = ids.get(right);
                if (explicit.contains(pair(a, b))) continue;
                String parentA = parent(cohorts, a);
                String parentB = parent(cohorts, b);
                boolean sameParent = parentA != null && parentA.equals(parentB);
                Map<String, Object> assumption = new LinkedHashMap<>();
                assumption.put("id", "relationship-" + safe(a) + "-" + safe(b));
                assumption.put("between", List.of(a, b));
                assumption.put("type", sameParent
                    ? "potentially_dependent" : "independent");
                assumption.put("statement", sameParent
                    ? a + " and " + b + " may be dependent because they share parent " + parentA
                    : a + " and " + b + " are assumed independent");
                assumption.put("source", sameParent
                    ? "shared_parent_requires_confirmation"
                    : "default_due_to_undeclared_relationship");
                assumption.put("review_status", "pending");
                result.add(assumption);
            }
        }
        return result;
    }

    private static String parent(Map<String, Map<String, Object>> cohorts,
            String id) {
        Map<String, Object> cohort = cohorts.get(id);
        return cohort == null || !(cohort.get("parent") instanceof String parent)
            ? null : parent;
    }

    private static String pair(String left, String right) {
        return left.compareTo(right) < 0 ? left + "\u0000" + right
            : right + "\u0000" + left;
    }

    private static String safe(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }
}
