/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Planning applicability from a registered supplied primary, never statistical eligibility. */
final class StudySuppliedFollowups {
    private StudySuppliedFollowups() { }
    private static final Set<String> GENES = Set.of("gene_symbol", "ensembl_gene");
    private static final Set<String> PROTEINS = Set.of("protein", "uniprot", "uniprot_accession", "uniprot_isoform");
    private static final Set<String> PROTEOMICS = Set.of("proteomics", "single_cell_protein", "spatial_proteomics");

    record Applicability(boolean variants, boolean molecular, boolean genes, boolean protein,
            Map<String,Object> receipt) { }

    static Applicability inspect(String hypothesis, Map<String,Object> analysis,
            Map<String,Map<String,Object>> datasets, Set<String> selected) {
        var policy = StudyPrimaryPolicy.resolve(StudyIO.optionalMap(analysis.get("primary_result"), "primary_result"));
        if ("association".equals(policy.get("kind")) || policy.containsKey("dataset_id"))
            return absent("registered_primary_association");
        int index = 0;
        for (var definition : StudySuppliedResultPolicy.resolve(analysis)) {
            ++index;
            if (!"primary".equals(definition.get("role")) || !"meta_analysis".equals(definition.get("kind"))) continue;
            String operation = StudyPlanner.operationId(hypothesis, index, StudySuppliedResultPolicy.operationType(definition));
            if (policy.containsKey("operation_id") && !operation.equals(policy.get("operation_id"))) continue;
            var scope = StudyIO.map(definition.get("scope"), "scope");
            String modality = String.valueOf(scope.getOrDefault("modality", "unknown"));
            String modalityOrigin = scope.containsKey("modality") ? "supplied_scope" : "undeclared";
            if (!scope.containsKey("modality")) {
                var references = new java.util.LinkedHashSet<String>();
                for (String id : selected) {
                    var dataset = datasets.get(id);
                    if (StudyAnalysisReferences.isReference(dataset) && definition.get("id").equals(dataset.get("supplied_result_id")))
                        references.add(String.valueOf(dataset.get("type")));
                }
                if (references.size() == 1) {
                    modality = references.iterator().next(); modalityOrigin = "registered_analysis_references";
                }
            }
            String namespace = String.valueOf(scope.getOrDefault("feature_id_type", ""));
            boolean variants = StudyVariantFollowupPolicy.variantScope(modality, namespace);
            boolean protein = PROTEOMICS.contains(modality) || PROTEINS.contains(namespace);
            boolean molecular = StudyNetworkPolicy.MOLECULAR.contains(modality) && !"generic".equals(modality) || protein;
            boolean genes = GENES.contains(namespace) || molecular && !"metabolomics".equals(modality);
            var receipt = new LinkedHashMap<String,Object>();
            receipt.put("status", "supplied_primary_metadata");
            receipt.put("source_id", definition.get("id")); receipt.put("operation_id", operation);
            receipt.put("modality", modality); receipt.put("feature_id_type", namespace);
            receipt.put("modality_origin", modalityOrigin);
            receipt.put("reported_status", definition.get("reported_status"));
            receipt.put("variant_followups_applicable", variants);
            receipt.put("gene_followups_applicable", genes);
            receipt.put("ranked_gsea_applicable", molecular || GENES.contains(namespace));
            receipt.put("protein_structure_applicable", protein);
            receipt.put("runtime_primary_validation_required", true);
            return new Applicability(variants, molecular || GENES.contains(namespace), genes, protein, receipt);
        }
        return absent("no_matching_supplied_primary_meta_analysis");
    }

    private static Applicability absent(String reason) {
        return new Applicability(false, false, false, false, Map.of("status", reason,
            "runtime_primary_validation_required", true));
    }

    static List<String> goals(Applicability a, Map<String,Object> analysis) {
        var goals = new java.util.ArrayList<String>();
        if (a.genes()) goals.addAll(List.of("gene_ontology", "pathway_enrichment"));
        if (a.variants()) {
            var variant = StudyIO.map(analysis.get("variant_followup"), "variant_followup");
            if (Boolean.TRUE.equals(StudyIO.map(variant.get("consequences"), "consequences").get("enabled"))
                    || Boolean.TRUE.equals(StudyIO.map(variant.get("alphagenome"), "alphagenome").get("enabled"))) goals.add("variant_scoring");
            if (enabled(analysis,"gwas_diagnostics")) goals.add("gwas_diagnostics");
            if (enabled(analysis,"gwas_enrichment")) goals.add("gwas_enrichment");
        }
        if (a.molecular() && enabled(analysis,"gsea")) {
            goals.add("ranked_gsea");
            if (enabled(analysis,"network")) goals.addAll(List.of("pathway_network", "network_modules", "module_gene_ontology"));
        }
        if (a.protein() && enabled(analysis,"protein_structure")) goals.add("protein_structure");
        return List.copyOf(goals);
    }

    private static boolean enabled(Map<String,Object> analysis, String key) {
        return Boolean.TRUE.equals(StudyIO.map(analysis.get(key), key).get("enabled"));
    }
}
