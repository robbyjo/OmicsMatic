/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit annotation/prediction routes and budgets; no P-value selection. */
public final class StudyVariantFollowupPolicy {
    private StudyVariantFollowupPolicy() { }
    /** Controlled feature scope; genomic gene/set summaries are not variant catalogues. */
    public static boolean variantScope(String modality, String namespace) {
        return Set.of("gwas", "genomics").contains(modality)
            && Set.of("", "auto", "variant", "snp", "rsid").contains(namespace);
    }
    public static Map<String, Object> resolve(Map<String, Object> supplied) {
        if (!Set.of("consequences", "alphagenome", "reference_fasta", "reference_normalized", "maximum_variants_per_batch").containsAll(supplied.keySet()))
            throw new IllegalArgumentException("Unknown variant_followup setting");
        var result = new LinkedHashMap<>(supplied);
        var consequences = StudyIO.optionalMap(result.get("consequences"), "variant_followup.consequences");
        if (!Set.of("enabled", "engine", "annotations", "format", "release", "citation", "executable", "perl", "database", "protocol", "operation", "cache", "cache_version", "fasta", "timeout_seconds", "installation").containsAll(consequences.keySet()))
            throw new IllegalArgumentException("Unknown variant_followup.consequences setting");
        consequences.putIfAbsent("enabled", true); consequences.putIfAbsent("engine", "vep-rest");
        consequences.putIfAbsent("format", "canonical"); consequences.putIfAbsent("timeout_seconds", 120);
        consequences.putIfAbsent("release", "service_as_retrieved");
        if (!Set.of("import", "annovar", "vep", "vep-rest").contains(consequences.get("engine"))) throw new IllegalArgumentException("Unsupported consequence engine");
        if (!Set.of("canonical", "vep", "annovar-vcf").contains(consequences.get("format"))) throw new IllegalArgumentException("Unsupported consequence import format");
        if (Boolean.TRUE.equals(consequences.get("enabled")) && !consequences.get("engine").equals("vep-rest") && consequences.get("release").equals("service_as_retrieved"))
            throw new IllegalArgumentException("Imported/installed consequences require an explicit annotation release");
        var genome = StudyIO.optionalMap(result.get("alphagenome"), "variant_followup.alphagenome");
        if (!Set.of("enabled", "engine", "scores", "resource_version", "citation", "python", "api_key_env", "scorers", "prediction_scorers", "ontology_terms", "model_version", "sequence_length", "maximum_queries", "maximum_predictions", "timeout_seconds", "maximum_rows", "installation").containsAll(genome.keySet()))
            throw new IllegalArgumentException("Unknown variant_followup.alphagenome setting");
        consequences.put("installation", StudyVariantToolInstallationPolicy.resolve(consequences.get("installation"), "annovar", "annovar".equals(consequences.get("engine")) && !consequences.containsKey("executable")));
        if (!"annovar".equals(consequences.get("engine")) && "automatic".equals(StudyIO.map(consequences.get("installation"), "installation").get("mode")))
            throw new IllegalArgumentException("Managed consequence software setup supports ANNOVAR; use external installation mode for other engines");
        genome.put("installation", StudyVariantToolInstallationPolicy.resolve(genome.get("installation"), "alphagenome", !genome.containsKey("python")));
        genome.putIfAbsent("enabled", true); genome.putIfAbsent("engine", "atlas"); genome.putIfAbsent("python", "python");
        genome.putIfAbsent("api_key_env", "ALPHAGENOME_API_KEY"); genome.putIfAbsent("scorers", List.of("AVI_SCORE"));
        genome.putIfAbsent("prediction_scorers", List.of("RNA_SEQ", "SPLICE_SITES", "SPLICE_SITE_USAGE", "SPLICE_JUNCTIONS"));
        genome.putIfAbsent("ontology_terms", List.of()); genome.putIfAbsent("resource_version", "service_as_retrieved");
        genome.putIfAbsent("model_version", "ALL_FOLDS"); genome.putIfAbsent("sequence_length", 1048576);
        genome.putIfAbsent("maximum_queries", 1000000); genome.putIfAbsent("maximum_predictions", 1000);
        genome.putIfAbsent("timeout_seconds", 120); genome.putIfAbsent("maximum_rows", 1000000);
        if (!Set.of("import", "atlas", "predict", "atlas_then_predict").contains(genome.get("engine"))) throw new IllegalArgumentException("Unsupported AlphaGenome engine");
        if (!(genome.get("api_key_env") instanceof String key) || !key.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("AlphaGenome api_key_env must name an environment variable");
        if (!(genome.get("sequence_length") instanceof Number length) || length.doubleValue() != length.intValue() || !Set.of(16384, 131072, 524288, 1048576).contains(length.intValue())) throw new IllegalArgumentException("Unsupported AlphaGenome sequence length");
        result.putIfAbsent("reference_normalized", false); result.putIfAbsent("maximum_variants_per_batch", 100);
        for (Object enabled : List.of(consequences.get("enabled"), genome.get("enabled"), result.get("reference_normalized")))
            if (!(enabled instanceof Boolean)) throw new IllegalArgumentException("Variant follow-up enabled/reference_normalized flags must be booleans");
        positive(consequences, "timeout_seconds", 3600); positive(genome, "timeout_seconds", 3600); positive(result, "maximum_variants_per_batch", 1000);
        positive(genome, "maximum_queries", 10000000); positive(genome, "maximum_predictions", 1000000); positive(genome, "maximum_rows", 10000000);
        for (String field : List.of("scorers", "prediction_scorers", "ontology_terms")) {
            var values = StudyIO.list(genome.get(field), "alphagenome." + field);
            if ((!field.equals("ontology_terms") && values.isEmpty()) || values.size() > (field.equals("prediction_scorers") ? 20 : 100) || values.stream().anyMatch(value -> !(value instanceof String text) || !text.matches("[A-Za-z0-9_:.+-]{1,150}")))
                throw new IllegalArgumentException("AlphaGenome " + field + " must be a bounded list of identifiers");
            if (new java.util.LinkedHashSet<>(values).size() != values.size()) throw new IllegalArgumentException("Duplicate AlphaGenome " + field);
        }
        for (var group : List.of(consequences, genome)) for (var entry : group.entrySet()) if (!Set.of("enabled", "scorers", "prediction_scorers", "ontology_terms", "sequence_length", "maximum_queries", "maximum_predictions", "timeout_seconds", "maximum_rows", "installation").contains(entry.getKey()))
            if (!(entry.getValue() instanceof String value) || value.isBlank() || value.length() > 4096 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Variant provider settings must be bounded strings: " + entry.getKey());
        if (result.containsKey("reference_fasta") && (!(result.get("reference_fasta") instanceof String path) || path.isBlank() || path.length() > 4096 || path.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("variant_followup.reference_fasta must be a nonblank path");
        if (!Set.of("ALL_FOLDS", "FOLD_0", "FOLD_1", "FOLD_2", "FOLD_3").contains(genome.get("model_version"))) throw new IllegalArgumentException("Unsupported AlphaGenome model version");
        result.put("consequences", consequences); result.put("alphagenome", genome); return result;
    }
    private static void positive(Map<String, Object> config, String field, int maximum) {
        if (!(config.get(field) instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.intValue() || number.intValue() < 1 || number.intValue() > maximum)
            throw new IllegalArgumentException("variant_followup." + field + " must be an integer in [1," + maximum + "]");
    }
}
