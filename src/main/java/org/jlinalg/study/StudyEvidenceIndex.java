/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.study;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Portable JSON and HTML search index over project evidence artifacts. */
public final class StudyEvidenceIndex {
    private StudyEvidenceIndex() { }

    public static int rebuild(Path project) throws IOException {
        return StudyProjectLock.with(project, () -> rebuildLocked(project));
    }

    private static int rebuildLocked(Path project) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        Map<String, Object> index = snapshot(root);
        List<Object> entries = StudyIO.list(index.get("entries"), "entries");
        StudyIO.atomicWrite(root.resolve("index/index.json"), StudyIO.canonicalJson(index) + "\n");
        StudyIO.atomicWrite(root.resolve("index/evidence-summary.json"), StudyIO.canonicalJson(index) + "\n");
        StudyIO.atomicWrite(root.resolve("index/LLM_CONTEXT.md"), llmContext(index, entries));
        StudyIO.atomicWrite(root.resolve("index/index.html"), html(index, entries));
        return entries.size();
    }

    /** Reconstruct an index from authoritative artifacts without writing derived views.
     * Callers must verify the project and guard against concurrent source changes. */
    public static Map<String, Object> snapshot(Path project) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        Object currentRun = StudyProject.status(root).get("run_id");
        List<Object> entries = new ArrayList<>();
        Path runs = root.resolve("runs");
        if (Files.isDirectory(runs)) {
            try (var paths = Files.walk(runs)) {
                for (Path path : paths
                        .filter(candidate -> candidate.getFileName().toString()
                            .equals("manifest.yaml") && !candidate.getParent().getFileName().toString().startsWith(".operation-"))
                        .sorted(Comparator.naturalOrder()).toList()) {
                    Map<String, Object> manifest = StudyIO.loadArtifact(path);
                    Map<String, Object> entry = new LinkedHashMap<>();
                    String operation = String.valueOf(
                        manifest.getOrDefault("operation_id", "unknown"));
                    String run = String.valueOf(
                        manifest.getOrDefault("run_id", "unknown"));
                    entry.put("result_id", run + "/" + operation);
                    entry.put("current", run.equals(currentRun));
                    copy(manifest, entry, "run_id", "operation_id",
                        "operation_type", "hypothesis", "state", "summary",
                        "cache_key", "input_sha256", "output_sha256", "details");
                    entry.put("manifest", portable(root, path));
                    Object findings = keyFindings(path.getParent(),
                        String.valueOf(manifest.get("operation_type")));
                    if (findings != null) entry.put("key_findings", findings);
                    if(Set.of("protein_structure","protein_structure_assessment").contains(manifest.get("operation_type")))entry.put("details",findings==null?Map.of("interpretation","See the retained protein sources; raw sequences, peptides and coordinates are excluded from aggregate previews"):findings);
                    List<Object> artifacts = new ArrayList<>();
                    Map<String, Object> hashes = StudyIO.optionalMap(
                        manifest.get("output_sha256"), "output_sha256");
                    for (Map.Entry<String, Object> output : hashes.entrySet()) {
                        Map<String, Object> artifact = new LinkedHashMap<>();
                        artifact.put("path", portable(root,
                            path.getParent().resolve(output.getKey())));
                        artifact.put("sha256", output.getValue());
                        artifacts.add(artifact);
                    }
                    entry.put("artifacts", artifacts);
                    entries.add(entry);
                }
            }
        }
        StudyContract resolved = StudyProject.resolved(root);
        Map<String, Object> study = resolved.document();
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("schema_version", 1);
        index.put("project", StudyIO.map(study.get("project"), "project"));
        index.put("hypotheses", StudyIO.list(
            study.get("hypotheses"), "hypotheses"));
        index.put("project_status", StudyProject.status(root));
        index.put("audit_files", List.of("study.yaml", "resolved-study.yaml",
            "SESSION_HISTORY.md", "ledger/events.jsonl",
            "state/project-status.yaml"));
        Map<String, Object> questions = StudyQuestions.read(root);
        if (!StudyIO.optionalList(questions.get("questions"), "questions").isEmpty()) {
            index.put("scientist_questions", questions);
            index.put("audit_files", List.of("study.yaml", "resolved-study.yaml", "SESSION_HISTORY.md",
                "QUESTIONS.md", "ledger/events.jsonl", "state/project-status.yaml", "state/questions.yaml"));
        }
        index.put("entries", entries);
        annotateFindings(root, entries);
        index.put("blockers", entries.stream()
            .map(value -> StudyIO.map(value, "index entry"))
            .filter(entry -> Boolean.TRUE.equals(entry.get("current")))
            .filter(entry -> !Set.of("complete", "not_applicable").contains(entry.get("state")))
            .toList());
        return index;
    }

    private static String llmContext(Map<String, Object> index,
            List<Object> entries) {
        Map<String, Object> project = StudyIO.map(index.get("project"),
            "project");
        StringBuilder result = new StringBuilder("# JLinAlg evidence context: ")
            .append(project.get("title")).append("\n\n")
            .append("This is a retrieval index for an auditable research project, "
                + "not a substitute for the underlying manifests and result tables. "
                + "Trace every numerical claim to the listed result ID, artifact, "
                + "and SHA-256 hash. Treat waiting or failed branches as unavailable "
                + "evidence, never as negative evidence.\n\n")
            .append("## Study\n\n")
            .append("- Project ID: `").append(project.get("id")).append("`\n")
            .append("- Objective: ").append(project.get("objective")).append("\n")
            .append("- Description: ").append(project.get("description"))
            .append("\n\n## Hypotheses\n\n");
        for (Object value : StudyIO.list(index.get("hypotheses"), "hypotheses")) {
            Map<String, Object> hypothesis = StudyIO.map(value, "hypothesis");
            result.append("- `").append(hypothesis.get("id")).append("`: ")
                .append(hypothesis.get("description")).append("\n");
        }
        if (index.containsKey("scientist_questions"))
            result.append("\nScientist question and answer records are local in QUESTIONS.md and state/questions.yaml. "
                + "Their free-text answers and evidence snapshots are deliberately omitted from this LLM context. "
                + "Do not upload those files without reviewing disclosure policy.\n");
        result.append("\n## Evidence operations\n\n");
        for (Object value : entries) {
            Map<String, Object> entry = StudyIO.map(value, "index entry");
            if (!Boolean.TRUE.equals(entry.get("current"))) continue;
            result.append("### ").append(entry.get("result_id")).append("\n\n")
                .append("- Operation: `").append(entry.get("operation_type"))
                .append("`\n- Hypothesis: `").append(entry.get("hypothesis"))
                .append("`\n- State: **").append(entry.get("state"))
                .append("**\n- Summary: ").append(entry.get("summary"))
                .append("\n- Manifest: `").append(entry.get("manifest"))
                .append("`\n");
            if (entry.containsKey("key_findings"))
                result.append("- Key findings (trace to the manifest and "
                    + "artifacts below):\n\n```json\n")
                    .append(StudyIO.canonicalJson(entry.get("key_findings")))
                    .append("\n```\n");
            for (Object artifactValue : StudyIO.optionalList(
                    entry.get("artifacts"), "artifacts")) {
                Map<String, Object> artifact = StudyIO.map(
                    artifactValue, "artifact");
                result.append("- Artifact: `").append(artifact.get("path"))
                    .append("` (SHA-256 `").append(artifact.get("sha256"))
                    .append("`)\n");
            }
            if (!Set.of("complete", "not_applicable").contains(entry.get("state")))
                result.append("- Blocker details: ").append(entry.get("details"))
                    .append("\n");
            result.append('\n');
        }
        result.append("## Audit trail\n\n")
            .append("Read `SESSION_HISTORY.md` for the human-readable notebook, "
                + "`ledger/events.jsonl` for immutable events, and "
                + "`index/index.json` for machine retrieval. Run `study verify` "
                + "before drafting or prioritization. This index contains aggregate "
                + "results and provenance; it does not authorize transmission to an LLM.\n");
        return result.toString();
    }

    public static Object keyFindings(Path operationDirectory,
            String operationType) throws IOException {
        if(Set.of("cohort_overlap","heterogeneity_review").contains(operationType)){
            String file=operationType.equals("cohort_overlap")?"cohort-overlap.yaml":"heterogeneity-review.yaml";Path source=operationDirectory.resolve(file);if(!Files.isRegularFile(source))return null;var data=StudyIO.loadArtifact(source);var result=new LinkedHashMap<String,Object>();
            for(String key:List.of("execution_origin","analysis_executed_by_jlinalg","source_alert_rule","selection_origin","alert_assessment_complete","registered_common_family_policy","set_order","observed_union_features","common_estimable_features","analysis_universe","universe_features","validation_family","direction_rule","threshold_comparison","discovery_alpha","validation_alpha","frozen_discovery_features","selected_union_features","all_sets_selected_features","observed_patterns","nonempty_patterns","displayed_patterns","zero_overlap_alerts","plot_disposition","metric","requested_metric","q_test_definitions","q_adjustment","q_testing_denominator","q_family_complete","source_family_complete","assessment_complete","feature_scope","association_alpha","candidate_multisource_features","eligible_features","scope_features","high_features","observed_high_features","high_fraction","widespread_alert","alert_disposition","interpretation","required_manuscript_caveat"))if(data.containsKey(key))result.put(key,data.get(key));result.put("full_summary_artifact",file);return jsonSafe(result);
        }
        if(Set.of("protein_structure","protein_structure_assessment").contains(operationType)&&Files.isRegularFile(operationDirectory.resolve("protein-analysis.yaml"))){var data=StudyIO.loadArtifact(operationDirectory.resolve("protein-analysis.yaml"));var summary=new LinkedHashMap<String,Object>();for(String key:List.of("source_scope","assessment_only","requested_targets","completed_targets","assessed_models","structure_retrievals","new_prediction_processes","prediction_batches_reused","interpretation","required_manuscript_caveat"))if(data.containsKey(key))summary.put(key,data.get(key));summary.put("full_summary_artifact","protein-analysis.yaml");return jsonSafe(summary);}
        Path imported = operationDirectory.resolve("supplied-analysis.yaml");
        if(operationType.equals("mr_family_adjustment")){
            Path familyManifest=operationDirectory.resolve("manifest.yaml");boolean failedFamily=Files.isRegularFile(familyManifest)&&!Set.of("complete","waiting_for_input").contains(String.valueOf(StudyIO.loadArtifact(familyManifest).get("state")));
            if(failedFamily||!Files.isRegularFile(operationDirectory.resolve("mr-family.yaml")))return Map.of("status","unavailable","interpretation","The MR family producer has no promoted summary; inspect its state and execution error. No joint discoveries are established.");
            var data=StudyIO.loadArtifact(operationDirectory.resolve("mr-family.yaml"));var summary=new LinkedHashMap<String,Object>();
            for(String key:List.of("status","coverage","family_size","known_member_test_count","estimable_primary_tests","observed_rows","source_adjustments_preserved","required_manuscript_caveat"))if(data.containsKey(key))summary.put(key,data.get(key));
            var policy=StudyIO.optionalMap(data.get("policy"),"policy");var selected=new LinkedHashMap<String,Object>();for(String key:List.of("id","hypotheses","adjustment","alpha"))if(policy.containsKey(key))selected.put(key,policy.get(key));summary.put("policy",selected);summary.put("full_results_artifact","mr-family-results.tsv");return jsonSafe(summary);
        }
        if (Files.isRegularFile(imported) && (!operationType.equals("meta_analysis") || !Files.isRegularFile(operationDirectory.resolve("meta-analysis.tsv")))) {
            var metadata = StudyIO.loadArtifact(imported);
            var summary = new LinkedHashMap<String, Object>();
            for (String field : List.of("artifact_id", "kind", "role", "reported_status", "execution_origin", "analysis_executed_by_jlinalg", "scope", "resolved_family", "result_rows", "nodes", "edges", "evidence_grade", "required_manuscript_caveat", "primary_selection_eligible"))
                if (metadata.containsKey(field)) summary.put(field, metadata.get(field));
            var method = StudyIO.optionalMap(metadata.get("method"), "method");
            var externalMethod = new LinkedHashMap<String, Object>();
            for (String field : List.of("name", "software", "version", "citation")) if (method.containsKey(field)) externalMethod.put(field, method.get(field));
            summary.put("external_method", externalMethod);
            if(operationType.equals("literature_search")&&metadata.get("evidence") instanceof List<?>) {
                for(String field:List.of("evidence","reference_records","query_records","public_metadata_declared"))if(metadata.containsKey(field))summary.put(field,metadata.get(field));
            }
            Object result = metadata.get("canonical_results");
            if (result instanceof String name && !name.contains("/") && !name.contains("\\")) {
                summary.put("supplied_result_preview", topRows(operationDirectory.resolve(name), 20, List.of()));
                summary.put("preview_rule", "At most 20 rows from the typed imported result. Immutable complete source and canonical table remain in the operation manifest; preview length is not the tested denominator.");
            }
            return summary;
        }
        if (Set.of("variant_annotation", "variant_scoring").contains(operationType)) {
            Path path = operationDirectory.resolve("variant-followup.yaml");
            if (Files.isRegularFile(path)) return jsonSafe(StudyIO.loadArtifact(path));
        }
        if (Set.of("network_analysis","pathway_network","network_modules","module_gene_ontology").contains(operationType)) {
            String filename=switch(operationType){case "network_modules"->"module-analysis.yaml";case "module_gene_ontology"->"module-go.yaml";default->"network-analysis.yaml";};
            Path path=operationDirectory.resolve(filename);if(!Files.isRegularFile(path))return null;
            var source=StudyIO.loadArtifact(path);var summary=new LinkedHashMap<String,Object>();
            for(String field:List.of("network_type","dataset_id","species","nodes","edges","analysis_samples","eligible_features","correlation","threshold","bootstrap_replicates","source_kind","membership_basis","connected_components","maximal_cliques","clique_search_complete","search_limit","module_origin","memberships","modules","background_genes","mapped_nodes","unmapped_nodes","tested_module_term_pairs","testing_family","adjustment","required_manuscript_caveat"))if(source.containsKey(field))summary.put(field,source.get(field));
            summary.put("full_summary_artifact",filename);return jsonSafe(summary);
        }
        if(Set.of("risk_score_evaluation","risk_score_related_phenotypes").contains(operationType)){
            Path path=operationDirectory.resolve("risk-score-analysis.yaml");if(!Files.isRegularFile(path))return null;var data=StudyIO.loadArtifact(path);var summary=new LinkedHashMap<String,Object>();
            for(String key:List.of("score_id","mode","family","analysis_stage","score_evaluation_performed","training_rows","tuning_rows","development_rows","evaluation_rows_declared","evaluation_rows","evaluation_independent_units","selected_lambda","feature_count","model_refitted","preprocessing_fit_scope","independence_evidence_status","evaluation_metrics","bootstrap_replicates","related_phenotypes_registered","related_testing_family_complete","related_adjustment","related_testing_family","interpretation","required_manuscript_caveat"))if(data.containsKey(key))summary.put(key,data.get(key));
            summary.put("full_summary_artifact","risk-score-analysis.yaml");return jsonSafe(summary);
        }
        if(operationType.equals("ranked_gsea")){
            Path path=operationDirectory.resolve("gsea-analysis.yaml");if(!Files.isRegularFile(path))return null;var data=StudyIO.loadArtifact(path);var summary=new LinkedHashMap<String,Object>();
            for(String key:List.of("null_model","gene_id_type","species","dataset_id","ranking_origin","rank_interpretation","ranked_genes","source_estimable_features","mapped_genes","genes_with_multiple_features","tested_terms","complete_testing_family","adjustment","tied_genes","work_units","score_rule","p_value_rule","normalization","analysis_performed","diagnostic_curve_count","interpretation","required_manuscript_caveat"))if(data.containsKey(key))summary.put(key,data.get(key));
            summary.put("full_summary_artifact","gsea-analysis.yaml");return jsonSafe(summary);
        }
        if (Set.of("finding_inventory", "impact_assessment").contains(operationType)) {
            Path path = operationDirectory.resolve(operationType.equals("finding_inventory") ? "finding-inventory.yaml" : "impact-assessment.yaml");
            if (!Files.isRegularFile(path)) return null;
            var data = StudyIO.loadArtifact(path); var result = new LinkedHashMap<String, Object>();
            for (String field : List.of("scope", "findings", "candidates", "scientific_scope", "literature_coverage", "candidate_count", "assessed_candidate_count", "selected_findings", "selected_ids", "selection_note", "review_status", "manuscript_policy", "method_limitations")) if (data.containsKey(field)) result.put(field, data.get(field));
            result.put("full_artifact", operationType.equals("finding_inventory") ? "finding-inventory.jsonl" : "impact-assessment.yaml");
            return result;
        }
        if (operationType.equals("participant_characteristics") || operationType.equals("irqtl_candidate_review")) {
            Path summary = operationDirectory.resolve(operationType.equals("participant_characteristics")
                ? "participant-characteristics.yaml" : "irqtl-review.yaml");
            return Files.isRegularFile(summary) ? jsonSafe(StudyIO.loadArtifact(summary)) : null;
        }
        if (Set.of("source_discovery", "bioinformatics_investigation", "literature_search").contains(operationType)) {
            // The manifest contains the same evidence document, serialized by
            // the canonical study writer without YAML aliases. Prefer it so
            // older public summaries with shared cached metadata remain
            // indexable without rewriting evidence or relaxing contract safety.
            Path manifestPath = operationDirectory.resolve("manifest.yaml");
            if (Files.isRegularFile(manifestPath)) {
                Map<String, Object> manifest = StudyIO.loadArtifact(manifestPath);
                if (operationType.equals(manifest.get("operation_type"))
                        && manifest.get("details") instanceof Map<?, ?>)
                    return jsonSafe(manifest.get("details"));
            }
            Path summary = operationDirectory.resolve(operationType.replace('_', '-') + ".yaml");
            return Files.isRegularFile(summary) ? jsonSafe(StudyIO.loadArtifact(summary)) : null;
        }
        Path genetic = operationDirectory.resolve("genetic-evidence.yaml");
        if (Set.of("fine_mapping", "colocalization", "mendelian_randomization").contains(operationType) && Files.isRegularFile(genetic))
            return jsonSafe(StudyIO.loadArtifact(genetic));
        if (operationType.equals("validate_harmonize")) {
            Path summary = operationDirectory.resolve("annotation-summary.yaml");
            Map<String, Object> findings = new LinkedHashMap<>();
            Path standardized = operationDirectory.resolve("standard-results");
            if (Files.isDirectory(standardized)) {
                Map<String, Object> primary = new LinkedHashMap<>();
                try (var paths = Files.list(standardized)) {
                    for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                        primary.put(path.getFileName().toString(), associationFindings(path));
                }
                findings.put("primary_results", primary);
            }
            if (Files.isRegularFile(summary)) findings.put("annotation", jsonSafe(StudyIO.loadArtifact(summary)));
            return findings;
        }
        if (operationType.equals("association")) {
            Path standard = operationDirectory.resolve("standard-results");
            if (!Files.isDirectory(standard)) return null;
            Map<String, Object> findings = new LinkedHashMap<>();
            try (var paths = Files.list(standard)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                    findings.put(path.getFileName().toString(), associationFindings(path));
            }
            return findings;
        }
        if (operationType.equals("meta_analysis"))
            return associationFindings(operationDirectory.resolve("meta-analysis.tsv"));
        if (operationType.equals("rare_variant_analysis")) {
            Path summaryPath = operationDirectory.resolve("rare-analysis.yaml");
            if (!Files.isRegularFile(summaryPath)) return null;
            var summary = StudyIO.loadArtifact(summaryPath);
            @SuppressWarnings("unchecked") List<Object> rows = (List<Object>) topRows(operationDirectory.resolve("rare-results.tsv"), Integer.MAX_VALUE, List.of());
            var primary = rows.stream().map(row -> StudyIO.map(row, "set result"))
                .filter(row -> "primary".equals(row.get("scope")) && "true".equals(row.get("is_primary_test")) && numeric(row.get("p_value")) <= 1).toList();
            List<Object> preview = new ArrayList<>();
            for (var row : primary.subList(0, Math.min(20, primary.size()))) {
                var displayed = new LinkedHashMap<>(row);
                if ("unsigned_set".equals(row.get("result_type"))) { displayed.remove("beta"); displayed.remove("se"); displayed.remove("direction"); }
                preview.add(displayed);
            }
            double alpha = .05;
            Map<String, Object> method = StudyIO.optionalMap(summary.get("method_report"), "method_report");
            Map<String, Object> parameters = StudyIO.optionalMap(method.get("parameters"), "parameters");
            if (parameters.get("alpha") instanceof Number value) alpha = value.doubleValue();
            double threshold = alpha;
            return Map.of("primary_identity", summary.get("primary_identity"), "families", summary.get("families"),
                "tested_primary_sets", primary.size(), "significant_primary_sets", primary.stream().filter(row -> numeric(row.get("adjusted_p")) < threshold).count(),
                "cohort_influence", summary.getOrDefault("cohort_influence", Map.of("performed", false, "reason", "Not available in this historical artifact")),
                "top_primary_sets", preview, "interpretation", summary.get("unsigned_semantics"),
                "display_rule", "Top 20 primary-test sets by P; adjusted_p is retained from the complete joint gene/mask/test family, never recomputed on the preview or primary-test subset.");
        }
        if (operationType.equals("effect_comparison"))
            return topRows(operationDirectory.resolve("effect-comparisons.tsv"),
                100, List.of());
        if (operationType.equals("discovery_replication"))
            return topRows(operationDirectory.resolve("replication.tsv"), 20, List.of());
        if (operationType.equals("diagnostic_review")) {
            Path diagnostics = operationDirectory.resolve("diagnostics.yaml");
            return Files.isRegularFile(diagnostics)
                ? jsonSafe(StudyIO.loadArtifact(diagnostics)) : null;
        }
        if (operationType.equals("gene_ontology"))
            return topRows(operationDirectory.resolve("gene-ontology.tsv"),
                10, List.of("term_id", "term_name", "selected_genes",
                    "term_genes", "overlap_genes",
                    "fold_enrichment_unweighted", "p_value", "FDR"));
        if (operationType.equals("pathway_enrichment"))
            return topRows(operationDirectory.resolve(
                    "pathway-enrichment.tsv"), 10,
                List.of("term_id", "term_name", "selected_genes",
                    "term_genes", "overlap_genes",
                    "fold_enrichment_unweighted", "p_value", "FDR"));
        if (operationType.equals("candidate_prioritization"))
            return topRows(operationDirectory.resolve(
                "candidate-prioritization.tsv"), 20, List.of());
        if (Set.of("advisor_review", "literature_support", "manuscript_draft", "sensitivity_analysis").contains(operationType)) {
            Path decision = operationDirectory.resolve("decision.yaml");
            return Files.isRegularFile(decision) ? StudyIO.loadArtifact(decision) : null;
        }
        return null;
    }

    private static Object topRows(Path path, int limit,
            List<String> selectedColumns) throws IOException {
        if (!Files.isRegularFile(path)) return null;
        try (var reader = Files.newBufferedReader(path,
                StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null) return List.of();
            List<String> header = List.of(first.split("\\t", -1)).stream()
                .map(StudyEvidenceIndex::unquote).toList();
            List<String> columns = selectedColumns.isEmpty()
                ? header : selectedColumns;
            List<Object> rows = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\\t", -1);
                int selected = header.indexOf("discovery_selected");
                if (selected >= 0 && (selected >= fields.length || !"true".equals(unquote(fields[selected])))) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                for (String column : columns) {
                    int index = header.indexOf(column);
                    if (index >= 0 && index < fields.length)
                        row.put(column, unquote(fields[index]));
                }
                if (!row.isEmpty()) rows.add(row);
            }
            String probability = columns.contains("p_value") ? "p_value"
                : columns.contains("discovery_adjusted_p") ? "discovery_adjusted_p" : "";
            if (!probability.isEmpty()) rows.sort(Comparator.comparingDouble(value ->
                numeric(StudyIO.map(value, "row").get(probability))));
            return new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
        }
    }

    private static Object associationFindings(Path path) throws IOException {
        @SuppressWarnings("unchecked")
        List<Object> rows = (List<Object>) topRows(path, Integer.MAX_VALUE, List.of());
        if (rows == null) return null;
        List<Map<String, Object>> tested = rows.stream().map(row -> StudyIO.map(row, "row"))
            .filter(row -> Set.of("ok", "single_cohort", "tested").contains(String.valueOf(row.getOrDefault("status", "ok"))))
            .filter(row -> Set.of("pooled", "NA", "").contains(String.valueOf(row.getOrDefault("term", "pooled"))))
            .filter(row -> numeric(row.get("p_value")) >= 0 && numeric(row.get("p_value")) <= 1).toList();
        double previous = 1;
        var importedFamily = StudySuppliedResultPolicy.family(path);
        boolean imported = !importedFamily.isEmpty();
        boolean bhFamily = !imported || "BH".equalsIgnoreCase(String.valueOf(importedFamily.get("adjustment")));
        for (int i = tested.size() - 1; i >= 0; i--) {
            previous = Math.min(previous, numeric(tested.get(i).get("p_value")) * tested.size() / (i + 1.0));
            if (imported) {
                double supplied = numeric(tested.get(i).get("adjusted_p"));
                if (!(supplied >= 0 && supplied <= 1)) throw new IOException("Invalid source-adjusted P value in supplied primary results");
                if (bhFamily) tested.get(i).put("BH_FDR", supplied);
            } else tested.get(i).put("BH_FDR", previous);
        }
        Map<String, Object> findings = new LinkedHashMap<>();
        findings.put("tested_features", imported ? importedFamily.getOrDefault("tested_count", tested.size()) : tested.size());
        if (!imported || bhFamily && "complete".equals(importedFamily.get("coverage")))
            findings.put("BH_FDR_0_05_features", tested.stream().filter(row -> numeric(row.get("BH_FDR")) <= 0.05).count());
        if (imported) {
            findings.put("supplied_eligible_features", tested.size());
            findings.put("supplied_significant_adjusted_0_05_features", tested.stream().filter(row -> numeric(row.get("adjusted_p")) <= .05).count());
            findings.put("testing_family", importedFamily);
        }
        findings.put("display_rule", imported
            ? "Top 20 among supplied eligible rows; source full-family adjusted P values and declared tested_count are retained. Counts of supplied significant rows are not total family discovery counts when only selected rows are available. External methods and QC were not independently reproduced."
            : "Top 20 eligible features by p-value; BH_FDR is descriptive across all eligible features in this table at the displayed 0.05 threshold. For meta-analysis only pooled rows are included; study selection rules remain in the plan.");
        List<Object> preview = new ArrayList<>();
        for (Map<String, Object> row : tested.subList(0, Math.min(20, tested.size()))) {
            Map<String, Object> selected = new LinkedHashMap<>();
            copy(row, selected, "feature_id", "beta", "se", "p_value", "BH_FDR", "adjusted_p", "n_cohorts", "direction", "i_squared");
            preview.add(selected);
        }
        findings.put("top_features", preview);
        return findings;
    }

    private static double numeric(Object value) {
        try { double number = Double.parseDouble(String.valueOf(value)); return Double.isFinite(number) ? number : Double.POSITIVE_INFINITY; }
        catch (NumberFormatException invalid) { return Double.POSITIVE_INFINITY; }
    }

    private static void annotateFindings(Path root, List<Object> entries) throws IOException {
        Map<String, Map<String, List<Map<String, Object>>>> annotations = new LinkedHashMap<>();
        for (Object value : entries) {
            Map<String, Object> entry = StudyIO.map(value, "entry");
            String key = entry.get("run_id") + "/" + entry.get("hypothesis");
            for (Object raw : StudyIO.optionalList(entry.get("artifacts"), "artifacts")) {
                Map<String, Object> artifact = StudyIO.map(raw, "artifact");
                String path = String.valueOf(artifact.get("path"));
                if (!path.endsWith("/feature-annotation.tsv")) continue;
                @SuppressWarnings("unchecked")
                List<Object> rows = (List<Object>) topRows(root.resolve(path), Integer.MAX_VALUE, List.of());
                if (rows == null) continue;
                Map<String, List<Map<String, Object>>> features = annotations.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
                for (Object rowValue : rows) {
                    Map<String, Object> row = StudyIO.map(rowValue, "annotation");
                    features.computeIfAbsent(String.valueOf(row.get("feature_id")), ignored -> new ArrayList<>()).add(row);
                }
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> entry = StudyIO.map(entries.get(i), "entry");
            if (entry.containsKey("key_findings")) entry.put("key_findings",
                attachAnnotation(entry.get("key_findings"), annotations.getOrDefault(
                    entry.get("run_id") + "/" + entry.get("hypothesis"), Map.of())));
            entries.set(i, entry);
        }
    }

    private static Object attachAnnotation(Object value, Map<String, List<Map<String, Object>>> annotations) {
        if (value instanceof Map<?, ?>) {
            Map<String, Object> row = StudyIO.map(value, "finding");
            row.replaceAll((key, child) -> attachAnnotation(child, annotations));
            List<Map<String, Object>> matches = annotations.get(String.valueOf(row.get("feature_id")));
            if (matches != null) {
                Map<String, Object> annotated = new LinkedHashMap<>();
                annotated.put("feature_id", row.get("feature_id"));
                for (String column : List.of("gene_id", "gene_symbol", "mapping_status"))
                    annotated.put(column, matches.stream().map(item -> String.valueOf(item.getOrDefault(column, "")))
                        .filter(text -> !text.isBlank()).distinct().sorted().collect(java.util.stream.Collectors.joining(";")));
                annotated.putAll(row);
                return annotated;
            }
            return row;
        } else if (value instanceof List<?> list)
            return list.stream().map(item -> attachAnnotation(item, annotations)).toList();
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"")
                && value.endsWith("\""))
            return value.substring(1, value.length() - 1)
                .replace("\"\"", "\"");
        return value;
    }

    private static Object jsonSafe(Object value) {
        if (value instanceof Double number && !Double.isFinite(number))
            return "NA";
        if (value instanceof Float number && !Float.isFinite(number))
            return "NA";
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key),
                jsonSafe(item)));
            return result;
        }
        if (value instanceof List<?> list)
            return list.stream().map(StudyEvidenceIndex::jsonSafe).toList();
        return value;
    }

    /** Display-only projection. Complete evidence and scalar bindings remain unchanged. */
    public static Object displayPreview(Object value) {
        return displayPreview(value, new int[] {1000}, 0);
    }

    private static Object displayPreview(Object value, int[] budget, int depth) {
        if (--budget[0] < 0 || depth > 10) return "Preview omitted; follow the complete artifact link.";
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            int count = 0;
            for (var entry : map.entrySet()) {
                if (count++ >= 40 || budget[0] <= 0) {
                    result.put("_display_preview", "Additional fields omitted; complete artifact is unchanged.");
                    break;
                }
                result.put(String.valueOf(entry.getKey()), displayPreview(entry.getValue(), budget, depth + 1));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            int count = 0;
            for (Object child : list) {
                if (count >= 20 || budget[0] <= 0) break;
                result.add(displayPreview(child, budget, depth + 1));
                count++;
            }
            if (count < list.size()) result.add("Display preview: " + count + " of " + list.size()
                + " entries; follow the complete artifact link.");
            return result;
        }
        if (value instanceof String text && text.length() > 2048)
            return text.substring(0, 2048) + " [display preview truncated]";
        return value;
    }

    static String html(Map<String, Object> index, List<Object> entries) {
        StringBuilder rows = new StringBuilder();
        StringBuilder findings = new StringBuilder();
        StringBuilder questions = new StringBuilder();
        if (index.containsKey("scientist_questions")) {
            questions.append("<h2>Scientist questions and decisions</h2><p>Documented for review; answers do not authorize execution or disclosure. ")
                .append(link("QUESTIONS.md", "Question notebook")).append(" · ").append(link("state/questions.yaml", "Machine-readable questions"))
                .append("</p>");
            for (Object raw : StudyIO.optionalList(StudyIO.map(index.get("scientist_questions"), "questions").get("questions"), "questions")) {
                Map<String, Object> question = StudyIO.map(raw, "question");
                questions.append("<section data-entry data-historical=\"false\" data-search=\"").append(escape(String.valueOf(question.get("question_id"))))
                    .append("\"><h3>").append(escape(String.valueOf(question.get("question_id")))).append(" — ")
                    .append(escape(String.valueOf(question.get("state")))).append("</h3>")
                    .append(link(String.valueOf(question.get("artifact")), "Immutable question record"))
                    .append(render(displayPreview(question))).append("</section>");
            }
        }
        for (Object value : entries) {
            Map<String, Object> entry = StudyIO.map(value, "index entry");
            // Do not duplicate complete nested audits into an HTML attribute.
            String search = escape(String.join(" ", List.of("result_id", "operation_type", "hypothesis", "state", "summary")
                .stream().map(key -> String.valueOf(entry.get(key))).toList()));
            String historical = Boolean.TRUE.equals(entry.get("current")) ? "false" : "true";
            rows.append("<tr data-entry data-historical=\"").append(historical).append("\" data-search=\"").append(search)
                .append("\"><td>").append(escape(String.valueOf(entry.get("result_id"))))
                .append("</td><td>").append(escape(String.valueOf(entry.get("operation_type"))))
                .append("</td><td>").append(escape(String.valueOf(entry.get("hypothesis"))))
                .append("</td><td>").append(escape(String.valueOf(entry.get("state"))))
                .append("</td><td>").append(link(String.valueOf(entry.get("manifest")), "Manifest"))
                .append("</td></tr>\n");
            findings.append("<section data-entry data-historical=\"").append(historical)
                .append("\" data-search=\"").append(search).append("\"><h2>")
                .append(escape(String.valueOf(entry.get("hypothesis")))).append(" — ")
                .append(escape(String.valueOf(entry.get("operation_type")))).append("</h2><p><strong>")
                .append(escape(String.valueOf(entry.get("state")))).append("</strong>: ")
                .append(escape(String.valueOf(entry.get("summary")))).append("</p>")
                .append("<p>Result: ").append(escape(String.valueOf(entry.get("result_id")))).append(" · ")
                .append(link(String.valueOf(entry.get("manifest")), "Audit manifest")).append("</p>");
            if (entry.containsKey("key_findings")) findings.append(render(displayPreview(entry.get("key_findings"))));
            if (!Set.of("complete", "not_applicable").contains(entry.get("state")))
                findings.append("<h3>What is needed next?</h3>").append(render(displayPreview(entry.get("details"))));
            findings.append("<details><summary>Full results and SHA-256 hashes</summary><ul>");
            for (Object raw : StudyIO.optionalList(entry.get("artifacts"), "artifacts")) {
                Map<String, Object> artifact = StudyIO.map(raw, "artifact");
                findings.append("<li>").append(link(String.valueOf(artifact.get("path")), String.valueOf(artifact.get("path"))))
                    .append(" <code>").append(escape(String.valueOf(artifact.get("sha256")))).append("</code></li>");
            }
            findings.append("</ul></details></section>");
        }
        return """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>JLinAlg project evidence index</title>
            <style>
            body{font-family:system-ui,sans-serif;max-width:1100px;margin:2rem auto;padding:0 1rem}
            input{width:100%;padding:.7rem;margin:1rem 0}table{border-collapse:collapse;width:100%}
            th,td{border:1px solid #ccc;padding:.5rem;text-align:left;vertical-align:top}th{background:#f3f3f3}
            section{border-top:2px solid #ddd;margin:2rem 0;padding-top:1rem}.scroll{overflow-x:auto}
            dt{font-weight:600;margin-top:.5rem}dd{margin-left:1rem}code{overflow-wrap:anywhere}
            td{max-width:32rem;overflow-wrap:anywhere}details{margin:1rem 0}[hidden]{display:none!important}
            </style></head><body>
            <h1>Project evidence index</h1>
            <p>Each result ID resolves to an operation manifest with input and output hashes.</p>
            <p>Major findings, annotated feature lists, and proposed next steps appear below. Tables are bounded previews;
            follow artifact links for full results. Search covers operation identity, state and summary, not complete variant audits.
            Annotation gaps, search not run, and unavailable evidence are not negative findings.
            The current attempt is shown by default; previous attempts remain auditable.</p>
            <label><input id="history" type="checkbox" style="width:auto"> Show historical attempts</label>
            <label>Search <input id="search" type="search" placeholder="hypothesis, operation, status, result ID"></label>
            <table><thead><tr><th>Result ID</th><th>Operation</th><th>Hypothesis</th><th>State</th><th>Manifest</th></tr></thead>
            <tbody>
            """ + rows + """
            </tbody></table>
            """ + "<h2>Study</h2>" + render(index.get("project")) + questions + "<h2>Major findings and next steps</h2>" + findings + """
            <script>
            const q=document.getElementById('search');
            const history=document.getElementById('history');
            const filter=()=>{const term=q.value.toLowerCase();
              document.querySelectorAll('[data-entry]').forEach(row=>{
                row.hidden=(!history.checked&&row.dataset.historical==='true')||!row.dataset.search.toLowerCase().includes(term);
              });
            };
            q.addEventListener('input',filter);history.addEventListener('change',filter);filter();
            </script></body></html>
            """;
    }

    private static String render(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder html = new StringBuilder("<dl>");
            map.forEach((key, item) -> html.append("<dt>").append(escape(String.valueOf(key)))
                .append("</dt><dd>").append(render(item)).append("</dd>"));
            return html.append("</dl>").toString();
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return "<p>No entries.</p>";
            if (list.stream().allMatch(item -> item instanceof Map<?, ?>)) {
                java.util.LinkedHashSet<String> columns = new java.util.LinkedHashSet<>();
                list.forEach(item -> ((Map<?, ?>) item).keySet().forEach(key -> columns.add(String.valueOf(key))));
                StringBuilder html = new StringBuilder("<div class=\"scroll\"><table><thead><tr>");
                columns.forEach(column -> html.append("<th>").append(escape(column)).append("</th>"));
                html.append("</tr></thead><tbody>");
                for (Object item : list) {
                    Map<?, ?> row = (Map<?, ?>) item;
                    html.append("<tr>");
                    columns.forEach(column -> html.append("<td>").append(render(row.get(column))).append("</td>"));
                    html.append("</tr>");
                }
                return html.append("</tbody></table></div>").toString();
            }
            StringBuilder html = new StringBuilder("<ul>");
            list.forEach(item -> html.append("<li>").append(render(item)).append("</li>"));
            return html.append("</ul>").toString();
        }
        if (value == null) return "";
        String displayed = String.valueOf(value);
        if (displayed.startsWith("https://") || displayed.startsWith("http://")) {
            try {
                java.net.URI uri = java.net.URI.create(displayed);
                if (uri.getHost() != null && uri.getUserInfo() == null)
                    return "<a rel=\"noreferrer noopener\" href=\"" + escape(displayed) + "\">" + escape(displayed) + "</a>";
            } catch (IllegalArgumentException invalid) { /* Display malformed citations as text. */ }
        }
        if (displayed.matches("-?\\d+\\.\\d+(?:[eE][+-]?\\d+)?") && Double.isFinite(numeric(value)))
            displayed = String.format(java.util.Locale.ROOT, "%.5g", numeric(value));
        return escape(displayed);
    }

    private static String link(String path, String label) {
        String encoded = java.util.Arrays.stream(path.replace('\\', '/').split("/", -1))
            .map(part -> java.net.URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(java.util.stream.Collectors.joining("/"));
        return "<a href=\"../" + escape(encoded) + "\">" + escape(label) + "</a>";
    }

    private static void copy(Map<String, Object> source,
            Map<String, Object> target, String... keys) {
        for (String key : keys)
            if (source.containsKey(key)) target.put(key, source.get(key));
    }

    private static String portable(Path root, Path path) {
        try {
            return root.relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        } catch (IllegalArgumentException differentRoot) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
