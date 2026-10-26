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

/** Link scientist-edited prose to the full frozen inventory and recorded AI selection. */
final class StudyImpactNarrative {
    private StudyImpactNarrative() { }
    static Map<String, Object> impact(Map<String, Map<String, Object>> evidence) {
        var matches = evidence.entrySet().stream().filter(entry -> "impact_assessment".equals(entry.getValue().get("operation_type")) && "complete".equals(entry.getValue().get("state"))).toList();
        if (matches.size() > 1) throw new IllegalArgumentException("Ambiguous current study-wide impact assessments");
        if (matches.isEmpty()) return Map.of();
        var result = new LinkedHashMap<>(map(matches.get(0).getValue().get("key_findings"))); result.put("source", matches.get(0).getKey()); return result;
    }
    static Map<String, Object> review(Path project, Map<String, Object> revision, Map<String, Map<String, Object>> evidence) throws IOException {
        var impact = impact(evidence);
        if (impact.isEmpty()) {
            if (evidence.values().stream().anyMatch(entry -> "impact_assessment".equals(entry.get("operation_type"))))
                return Map.of("status", "needs_input", "needs", List.of("The current study-wide impact assessment is incomplete; a prior or manually entered selection has not been verified against it"));
            return Map.of("status", "not_requested", "interpretation", "No study-wide AI selection was requested; legacy prose is retained without inventing one");
        }
        var inventories = evidence.values().stream().filter(entry -> "finding_inventory".equals(entry.get("operation_type")) && "complete".equals(entry.get("state"))).toList();
        if (inventories.size() != 1) throw new IOException("One current verified finding inventory is required to review impact-linked prose");
        Path manifestPath = project.resolve(text(inventories.get(0).get("manifest"))).normalize();
        if (!manifestPath.startsWith(project)) throw new IOException("Unsafe finding inventory manifest path");
        var manifest = StudyYaml.loadArtifact(manifestPath);
        if(Boolean.FALSE.equals(map(map(manifest.get("details")).get("finding_grouping")).get("complete")))return Map.of("status","needs_input","needs",List.of("Registered finding LD grouping remains unresolved; do not finalize a distinct-story manuscript selection"));
        Path inventory = StudyFindingInventory.verified(manifestPath.getParent(), "finding-inventory.jsonl", text(map(manifest.get("output_sha256")).get("finding-inventory.jsonl")));
        Map<String, Map<String, Object>> findings = new LinkedHashMap<>();
        Set<String> requestedIds = new LinkedHashSet<>();
        for (Object raw : list(revision.get("sections"))) for (Object id : list(map(raw).get("finding_ids"))) requestedIds.add(text(id));
        try (var reader = Files.newBufferedReader(inventory)) { String line; while ((line = reader.readLine()) != null) { var row = map(SimpleJson.parse(line)); if (requestedIds.contains(text(row.get("finding_id")))) findings.put(text(row.get("finding_id")), row); } }
        List<String> selected = new ArrayList<>(), needs = new ArrayList<>(); Set<String> groups = new LinkedHashSet<>();
        Map<String, Map<String, Object>> results = new LinkedHashMap<>(); List<Object> claims = new ArrayList<>();
        for (Object raw : list(revision.get("sections"))) {
            var paragraph = map(raw);
            if (!paragraph.containsKey("finding_ids")) continue;
            if (!(paragraph.get("finding_ids") instanceof List<?> values) || values.stream().anyMatch(id -> !(id instanceof String) || !findings.containsKey(id))) throw new IOException("Manuscript finding_ids must identify known frozen inventory findings");
            if (new LinkedHashSet<>(values).size() != values.size()) throw new IOException("Duplicate manuscript finding_ids");
        }
        for (Object raw : list(revision.get("sections"))) { var paragraph = map(raw); if ("results".equals(paragraph.get("section"))) results.put(text(paragraph.get("id")), paragraph); }
        String limitations = list(revision.get("sections")).stream().map(StudyPublicEvidence::map).filter(row -> "limitations".equals(row.get("discussion_role"))).map(row -> text(row.get("text"))).collect(java.util.stream.Collectors.joining("\n"));
        for (Object raw : list(revision.get("sections"))) {
            var paragraph = map(raw); if (!"principal_finding".equals(paragraph.get("discussion_role"))) continue;
            var ids = list(paragraph.get("finding_ids"));
            if (ids.size() != 1) { needs.add(paragraph.get("id") + ": identify exactly one inventory finding_id for this principal story; group related signals in the declared finding_groups input"); continue; }
            String id = text(ids.get(0)); var finding = findings.get(id);
            if (finding == null || !Boolean.TRUE.equals(finding.get("candidate")) || !Boolean.TRUE.equals(finding.get("estimable"))) throw new IOException("Unknown, excluded or ineligible manuscript finding: " + id);
            if (selected.contains(id) || !groups.add(text(finding.get("story_group")))) throw new IOException("Distinct principal-finding slots cannot repeat one finding or story group");
            try{var proposed=new ArrayList<Map<String,Object>>();for(String previous:selected)proposed.add(findings.get(previous));proposed.add(finding);StudyFindingGrouping.distinct(proposed);}catch(IllegalArgumentException invalid){throw new IOException(invalid.getMessage(),invalid);}
            selected.add(id);
            Set<String> reported = new LinkedHashSet<>();
            for (Object result : list(paragraph.get("result_ids"))) for (Object reportedId : list(results.getOrDefault(text(result), Map.of()).get("finding_ids"))) reported.add(text(reportedId));
            if (!reported.contains(id)) needs.add(paragraph.get("id") + ": its linked Results paragraph must identify this same finding_id");
            for (Object caveat : list(finding.get("caveats"))) if ("partial".equals(map(caveat).get("kind")) && !limitations.contains(text(map(caveat).get("text"))))
                needs.add(paragraph.get("id") + ": retain the required partial-region colocalization caveat in main-text limitations");
            claims.add(Map.of("paragraph_id", paragraph.get("id"), "finding_id", id, "story_group", finding.get("story_group"), "mandatory_caveats", finding.get("caveats")));
        }
        var recommended = list(impact.get("selected_ids")).stream().map(StudyPublicEvidence::text).toList();
        String override = text(revision.get("impact_selection_override_reason"));
        if (revision.containsKey("impact_selection_override_reason") && (!(revision.get("impact_selection_override_reason") instanceof String) || override.isBlank())) throw new IOException("impact_selection_override_reason must be explanatory text");
        boolean changed = !recommended.equals(selected);
        if (changed && override.isBlank()) needs.add("Record impact_selection_override_reason for changes to the AI-selected findings or priority order");
        return Map.of("status", needs.isEmpty() ? "selection_linked_scientific_review_required" : "needs_input", "needs", needs, "ai_source", impact.get("source"),
            "ai_recommended_ids", recommended, "manuscript_selected_ids", selected, "selection_disposition", changed ? override.isBlank() ? "unexplained_difference" : "scientist_override_with_reason" : "follows_AI_recommendation",
            "override_reason", override, "claims", claims, "finding_inventory_sha256", FollowupSupport.hash(inventory), "scientific_review", "ID, grouping and source checks do not validate biological interpretation or source appraisal.");
    }
}
