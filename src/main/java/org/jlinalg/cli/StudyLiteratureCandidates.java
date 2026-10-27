/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public bibliographic candidates, with query lineage and no inferred claim support. */
final class StudyLiteratureCandidates {
    private StudyLiteratureCandidates() { }
    static final String CAVEAT = "These are source-reported or retrieved bibliographic candidates, not evidence that a paper supports this finding. Read the primary text, review study context, corrections and metadata conflicts, and bind substantive claims to source passages. Repeated query appearances are not independent studies. Abstracts, full texts, query prose and private source notes are not included in this projection.";

    static Map<String,Object> project(List<Map<String,Object>> inputs, String scope) {
        var queries = new LinkedHashMap<String,Map<String,Object>>();
        var rows = new ArrayList<Object>(); long withheld = 0;
        for (var input : inputs) {
            String id = text(input.get("query_id"));
            var articles = list(input.get("articles"));
            if (!Boolean.TRUE.equals(input.get("candidate_context_eligible"))) { withheld += articles.size(); continue; }
            var safe = new ArrayList<Object>();
            for (Object raw : articles) safe.add(article(map(raw)));
            if (safe.isEmpty()) continue;
            rows.add(Map.of("source_operation", id, "articles", safe));
            queries.putIfAbsent(id, select(input, List.of("query_id", "source_ids", "match_basis", "search_strategy", "execution_origin", "reporting_roles", "reported_complete", "search_limited", "producer_accepted")));
        }
        if (rows.isEmpty() && withheld == 0) return Map.of();
        var catalog = StudyReferenceCatalog.build(rows); var candidates = new ArrayList<Object>();
        for (Object raw : list(catalog.get("references"))) {
            var reference = map(raw); var candidate = new LinkedHashMap<>(StudyReferenceCatalog.publicMetadata(reference));
            candidate.remove("excerpt"); candidate.remove("excerpt_scope"); candidate.remove("excerpt_status");
            var ids = new LinkedHashSet<String>(); var sources = new LinkedHashSet<String>();
            for (Object occurrence : list(reference.get("retrieval_context"))) {
                String id = text(map(occurrence).get("source_operation")); ids.add(id);
                for (Object source : list(queries.getOrDefault(id, Map.of()).get("source_ids"))) sources.add(text(source));
            }
            candidate.put("query_ids", List.copyOf(ids)); candidate.put("source_ids", List.copyOf(sources));
            candidates.add(candidate);
        }
        var sourceIds = new LinkedHashSet<String>(); for (var query : queries.values()) for (Object id : list(query.get("source_ids"))) sourceIds.add(text(id));
        var result = new LinkedHashMap<String,Object>(); result.put("schema_version", 1); result.put("scope", scope);
        result.put("references", candidates); result.put("queries", List.copyOf(queries.values())); result.put("unique_candidate_references", candidates.size());
        result.put("withheld_article_records", withheld); result.put("source_ids", List.copyOf(sourceIds));
        result.put("source_support_established", false); result.put("full_text_appraisal_established", false);
        result.put("scientific_review", "required"); result.put("required_manuscript_caveat", CAVEAT); return result;
    }

    private static Map<String,Object> article(Map<String,Object> source) {
        var result = new LinkedHashMap<String,Object>();
        for (String field : List.of("doi", "pmid", "pmcid", "id", "source", "title", "authorString", "pubYear")) {
            Object value = source.get(field); if (value instanceof String text && !text.isBlank()) result.put(field, text);
            else if (field.equals("pubYear") && value instanceof Number number && Double.isFinite(number.doubleValue())) result.put(field, number.toString());
        }
        String journal = text(map(map(source.get("journalInfo")).get("journal")).get("title"));
        if (!journal.isBlank()) result.put("journalInfo", Map.of("journal", Map.of("title", journal)));
        var corrections = new ArrayList<Object>();
        for (Object raw : list(map(source.get("commentCorrectionList")).get("commentCorrection"))) {
            var correction = new LinkedHashMap<String,Object>();
            for (String field : List.of("id", "source", "type")) if (map(raw).get(field) instanceof String value) correction.put(field, value);
            if (!correction.isEmpty()) corrections.add(correction);
        }
        if (!corrections.isEmpty()) result.put("commentCorrectionList", Map.of("commentCorrection", corrections));
        result.put("metadata_origins", List.of("producer_integrity_verified_metadata_unreviewed"));
        return result;
    }

    private static Map<String,Object> select(Map<String,Object> source, List<String> fields) {
        var result = new LinkedHashMap<String,Object>();
        for (String field : fields) if (source.containsKey(field)) result.put(field, source.get(field));
        return result;
    }

    static Map<String,Object> forFindings(Map<String,Object> background, List<Map<String,Object>> findings) {
        var result = new LinkedHashMap<String,Object>(); var hypotheses = new java.util.TreeSet<String>();
        for (var finding : findings) hypotheses.add(text(finding.get("hypothesis")));
        for (String hypothesis : hypotheses) if (background.containsKey(hypothesis)) result.put(hypothesis, background.get(hypothesis));
        return result;
    }

    static Set<String> sourceIds(Map<String,Object> background) {
        var result = new LinkedHashSet<String>();
        for (Object value : background.values()) for (Object id : list(map(value).get("source_ids"))) result.add(text(id));
        return result;
    }
}
