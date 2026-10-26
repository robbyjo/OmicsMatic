/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Externally produced analyses are scoped artifacts, never synthetic cohorts. */
public final class StudySuppliedResultPolicy {
    private StudySuppliedResultPolicy() { }
    public static final Set<String> KINDS = Set.of("meta_analysis", "discovery_meta_analysis", "discovery_replication", "effect_comparison", "cohort_overlap", "rare_cohort_overlap", "heterogeneity_review", "rare_variant_analysis",
        "gene_ontology", "pathway_enrichment", "ranked_gsea", "gwas_enrichment", "gwas_diagnostics", "network_analysis", "network_modules", "module_gene_ontology", "network_preservation", "network_preservation_null", "rare_set_omnibus", "risk_score_evaluation", "risk_score_related_phenotypes",
        "mendelian_randomization", "colocalization", "fine_mapping", "subgroup_interactions", "variant_annotation", "variant_scoring", "protein_structure", "literature_search", "source_discovery", "bioinformatics_investigation");
    private static final Set<String> FIELDS = Set.of("id", "kind", "role", "files", "columns", "scope", "method", "family",
        "provenance", "limitations", "depends_on", "evidence_grade", "reported_status");
    private static final Set<String> SCOPE = Set.of("phenotype", "exposure", "outcome", "cohorts", "datasets", "population", "ancestry",
        "tissue", "assay", "feature_ids", "species", "modality", "feature_id_type", "genome_build", "coordinate_system", "effect_contract", "sequence_sha256", "visit", "units", "n_samples", "overlap");

    /** Validate and topologically order imports within one hypothesis. */
    public static List<Map<String, Object>> resolve(Map<String, Object> analysis) {
        var definitions = new LinkedHashMap<String, Map<String, Object>>();
        var primaryKinds = new LinkedHashSet<String>();
        for (Object raw : StudyIO.optionalList(analysis.get("supplied_results"), "analysis.supplied_results")) {
            var definition = StudyIO.map(raw, "supplied result");
            unknown(definition, FIELDS, "supplied result");
            String id = text(definition.get("id"), "supplied result.id", 100);
            if (!id.matches("[A-Za-z][A-Za-z0-9_.-]*")) throw new IllegalArgumentException("supplied result.id must be a stable identifier");
            String kind = text(definition.get("kind"), "supplied result.kind", 100);
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported supplied result kind: " + kind);
            definition.putIfAbsent("role", "primary");
            if (!Set.of("primary", "sensitivity", "contribution").contains(String.valueOf(definition.get("role"))))
                throw new IllegalArgumentException("supplied result.role must be primary, sensitivity or contribution");
            if ("contribution".equals(definition.get("role")) && !Set.of("mendelian_randomization","literature_search").contains(kind))
                throw new IllegalArgumentException("Supplied contributions support mendelian_randomization and literature_search");
            if ("primary".equals(definition.get("role")) && !primaryKinds.add(kind) && !kind.equals("network_analysis"))
                throw new IllegalArgumentException("Only one supplied primary " + kind + " is allowed per hypothesis; use distinct compatible hypotheses for different scopes");
            definition.putIfAbsent("reported_status", "complete");
            if (!Set.of("complete", "partial", "not_estimable").contains(String.valueOf(definition.get("reported_status"))))
                throw new IllegalArgumentException("supplied result.reported_status must be complete, partial or not_estimable");
            if(kind.equals("literature_search")&&"not_estimable".equals(definition.get("reported_status")))throw new IllegalArgumentException("Supplied literature uses complete/partial with explicit query coverage; not_estimable cannot describe a completed search");
            if(kind.equals("literature_search")&&"primary".equals(definition.get("role"))&&"partial".equals(definition.get("reported_status")))throw new IllegalArgumentException("Partial supplied literature needs role: contribution and a source hash so missing native searches remain scheduled");
            var files = StudyIO.map(definition.get("files"), "supplied result.files");
            if (files.isEmpty()) throw new IllegalArgumentException("supplied result.files cannot be empty");
            for (var entry : files.entrySet()) {
                if (!entry.getKey().matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Supplied file roles must be safe identifiers");
                text(entry.getValue(), "supplied result.files." + entry.getKey(), 8000);
            }
            if (kind.equals("network_analysis")) {
                if (!files.containsKey("nodes") || !files.containsKey("edges"))
                    throw new IllegalArgumentException("Supplied networks require files.nodes and files.edges");
            } else if (!files.containsKey("results")) throw new IllegalArgumentException("Supplied " + kind + " requires files.results");
            var scope = StudyIO.map(definition.get("scope"), "supplied result.scope");
            unknown(scope, SCOPE, "supplied result.scope");
            for (String field : List.of("cohorts", "datasets", "feature_ids")) if (scope.containsKey(field)) strings(scope.get(field), "supplied result.scope." + field);
            var method = StudyIO.map(definition.get("method"), "supplied result.method");
            text(method.get("name"), "supplied result.method.name", 1000);
            var family = StudyIO.optionalMap(definition.get("family"), "supplied result.family");
            unknown(family, Set.of("id", "coverage", "adjustment", "tested_count", "alpha", "rationale", "adjusted_p_from_full_family"), "supplied result.family");
            if (!family.isEmpty()) {
                text(family.get("id"), "supplied result.family.id", 200);
                if (!Set.of("complete", "selected_only", "unknown").contains(String.valueOf(family.getOrDefault("coverage", "unknown"))))
                    throw new IllegalArgumentException("Supplied family coverage must be complete, selected_only or unknown");
                if (!Set.of("BH", "BY", "BONFERRONI", "NONE", "PROVIDED").contains(String.valueOf(family.getOrDefault("adjustment", "PROVIDED")).toUpperCase(java.util.Locale.ROOT)))
                    throw new IllegalArgumentException("Supplied family adjustment must be BH, Bonferroni, NONE or provided");
                if (family.containsKey("tested_count") && (!(family.get("tested_count") instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0 || n.doubleValue() != n.longValue()))
                    throw new IllegalArgumentException("Supplied family tested_count must be a nonnegative integer");
                if (family.containsKey("alpha") && (!(family.get("alpha") instanceof Number n) || !(n.doubleValue() > 0 && n.doubleValue() <= 1)))
                    throw new IllegalArgumentException("Supplied family alpha must be in (0,1]");
                if (family.containsKey("adjusted_p_from_full_family") && !(family.get("adjusted_p_from_full_family") instanceof Boolean))
                    throw new IllegalArgumentException("adjusted_p_from_full_family must be true or false");
            }
            StudyIO.optionalMap(definition.get("columns"), "supplied result.columns");
            StudyIO.optionalMap(definition.get("provenance"), "supplied result.provenance");
            if ("contribution".equals(definition.get("role"))&&kind.equals("literature_search")) {
                var provenance=StudyIO.optionalMap(definition.get("provenance"),"literature contribution.provenance");
                if(!(provenance.get("results_sha256") instanceof String hash)||!hash.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Literature contribution needs provenance.results_sha256");
            }
            if ("contribution".equals(definition.get("role"))&&kind.equals("mendelian_randomization")) {
                if (StudyIO.optionalMap(StudyIO.optionalMap(analysis.get("operations"), "operations").get("mendelian_randomization"), "MR operation").containsKey("command"))
                    throw new IllegalArgumentException("MR contribution completion uses the native MR executor; a custom command cannot claim scoped completion");
                if (StudyIO.list(scope.get("feature_ids"), "contribution.scope.feature_ids").isEmpty()) throw new IllegalArgumentException("MR contributions need explicit nonempty scope.feature_ids");
                for (String field : List.of("exposure", "outcome", "ancestry", "genome_build", "tissue", "assay")) text(scope.get(field), "MR contribution.scope." + field, 2000);
                if (!Set.of("GRCh37", "GRCh38").contains(scope.get("genome_build"))) throw new IllegalArgumentException("MR contribution needs an explicit GRCh37/GRCh38 build");
                var parameters = StudyIO.optionalMap(method.get("parameters"), "contribution.method.parameters");
                if (!Set.of("ivw-fixed", "ivw-random").contains(String.valueOf(parameters.get("primary_estimator")))) throw new IllegalArgumentException("MR contribution method.parameters.primary_estimator must declare ivw-fixed or ivw-random with the single-instrument Wald fallback");
                text(parameters.get("reuse_rationale"), "MR contribution.method.parameters.reuse_rationale", 4000);
                var provenance = StudyIO.optionalMap(definition.get("provenance"), "contribution.provenance");
                text(provenance.get("analysis_id"), "MR contribution.provenance.analysis_id", 1000);
                if (!(provenance.get("results_sha256") instanceof String hash) || !hash.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException("MR contribution needs provenance.results_sha256");
                if (family.isEmpty()) throw new IllegalArgumentException("MR contributions must preserve their source testing-family declaration");
            }
            if (definition.containsKey("evidence_grade")) text(definition.get("evidence_grade"), "supplied result.evidence_grade", 1000);
            if (definition.containsKey("limitations")) strings(definition.get("limitations"), "supplied result.limitations");
            definition.putIfAbsent("depends_on", List.of());
            strings(definition.get("depends_on"), "supplied result.depends_on");
            if (definitions.putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate supplied result ID: " + id);
        }
        for(String kind:List.of("mendelian_randomization","literature_search"))if (primaryKinds.contains(kind) && definitions.values().stream().anyMatch(d -> kind.equals(d.get("kind"))&&"contribution".equals(d.get("role"))))
            throw new IllegalArgumentException("A supplied full primary "+kind+" and its contributions cannot both own the same hypothesis; declare one completion strategy");
        StudyNetworkReuse.validatePrimaries(new ArrayList<>(definitions.values()));
        var ordered = new ArrayList<Map<String, Object>>();
        var visited = new LinkedHashSet<String>(); var active = new LinkedHashSet<String>();
        for (String id : definitions.keySet()) visit(id, definitions, visited, active, ordered);
        return List.copyOf(ordered);
    }

    public static Map<String, Object> definition(Map<String, Object> study, String hypothesis, String id) {
        for (Object raw : StudyIO.list(study.get("hypotheses"), "hypotheses")) {
            var candidate = StudyIO.map(raw, "hypothesis");
            if (!hypothesis.equals(candidate.get("id"))) continue;
            for (var definition : resolve(StudyIO.map(candidate.get("analysis"), "analysis")))
                if (id.equals(definition.get("id"))) return definition;
        }
        throw new IllegalArgumentException("Unknown supplied result " + hypothesis + "/" + id);
    }

    public static boolean hasImports(Map<String, Object> study) {
        return StudyIO.optionalList(study.get("hypotheses"), "hypotheses").stream().map(raw -> StudyIO.map(raw, "hypothesis"))
            .anyMatch(h -> !StudyIO.optionalList(StudyIO.optionalMap(h.get("analysis"), "analysis").get("supplied_results"), "supplied_results").isEmpty());
    }

    /** Aggregate bookkeeping safe for automatic reports; original prose stays local. */
    public static Map<String,Object> aggregateFamily(Map<String,Object> family) {
        var result=new LinkedHashMap<>(family);
        // Original scientific/provenance prose stays in the local source record.
        // Family identity, correction, coverage and numeric bookkeeping remain public.
        result.remove("rationale");
        return result;
    }

    /** A sealed import's family is authoritative; a selected preview is not a new family. */
    public static Map<String, Object> family(Path results) throws IOException {
        Path metadata = results.getParent().resolve("supplied-analysis.yaml");
        if (!Files.isRegularFile(metadata)) return Map.of();
        var source = StudyIO.loadArtifact(metadata);
        if (!results.getFileName().toString().equals(source.get("canonical_results"))) return Map.of();
        if (!Boolean.TRUE.equals(source.get("primary_selection_eligible")))
            throw new IOException("Supplied results are not eligible for primary statistical selection: " + results);
        Path manifestPath = results.getParent().resolve("manifest.yaml");
        if (!Files.isRegularFile(manifestPath)) throw new IOException("Supplied result selection requires a completed verified producer manifest");
        var manifest = StudyIO.loadArtifact(manifestPath);
        var hashes = StudyIO.optionalMap(manifest.get("output_sha256"), "output_sha256");
        if (!"complete".equals(manifest.get("state")) || !source.get("kind").equals(manifest.get("operation_type"))
                || !StudyIO.hash(metadata).equals(hashes.get("supplied-analysis.yaml"))
                || !StudyIO.hash(results).equals(hashes.get(results.getFileName().toString()))
                || !StudyIO.hash(results).equals(source.get("canonical_sha256")))
            throw new IOException("Missing, changed or incomplete supplied result producer: " + results);
        return StudyIO.optionalMap(source.get("resolved_family"), "resolved_family");
    }

    private static void visit(String id, Map<String, Map<String, Object>> definitions, Set<String> visited,
            Set<String> active, List<Map<String, Object>> ordered) {
        if (visited.contains(id)) return;
        if (!definitions.containsKey(id)) throw new IllegalArgumentException("Unknown supplied result dependency: " + id);
        if (!active.add(id)) throw new IllegalArgumentException("Cycle in supplied result dependencies: " + id);
        for (Object dependency : StudyIO.list(definitions.get(id).get("depends_on"), "depends_on"))
            visit(String.valueOf(dependency), definitions, visited, active, ordered);
        active.remove(id); visited.add(id); ordered.add(definitions.get(id));
    }
    private static void unknown(Map<String, Object> value, Set<String> allowed, String where) {
        for (String field : value.keySet()) if (!allowed.contains(field)) throw new IllegalArgumentException("Unknown " + where + " field: " + field);
    }
    private static String text(Object value, String where, int maximum) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > maximum || text.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(where + " must be nonblank bounded text without control characters");
        return text;
    }
    private static void strings(Object value, String where) {
        var seen = new LinkedHashSet<String>();
        for (Object item : StudyIO.list(value, where)) if (!seen.add(text(item, where, 4000)))
            throw new IllegalArgumentException(where + " cannot repeat values");
    }
}
