/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared reporting model; ordering and evidence links do not certify scientific interpretation. */
final class StudyResultsDiscussion {
    static final List<String> RESULTS = StudyMethods.ROLES.stream()
        .filter(r -> !Set.of("cohorts", "reproducibility").contains(r)).toList();
    static final List<String> DISCUSSION = List.of("principal_finding", "future_potential", "next_steps", "limitations", "overall_conclusion");
    private StudyResultsDiscussion() { }

    static String instructions() {
        return "For registered cross-hypothesis MR families, distinguish joint_adjusted_p and joint_family_status from source adjustments. An unresolved joint family establishes no joint MR discoveries. Association significance cannot substitute for MR multiplicity or causal evidence. "
            + "Begin Results with the native participant-characteristics Table 1, then report applicable analyses in the same order as Methods: "
            + "association, discovery/replication, meta-analysis, enrichment/ontology, Mendelian randomization, network analysis, fine mapping, colocalization and sensitivity analyses. "
            + "Use results_role, not impact rank, to order Results. Include important null, discordant and sensitivity results; do not impose a five-paragraph cap. "
            + "Place relevant bioinformatics evidence alongside the finding it informs, using supporting_evidence entries with source and relationship. "
            + "Explain the exact feature/isoform/gene, tissue and phenotype match, direction, limitations and whether the evidence supports, contradicts or only contextualizes the finding. "
            + "For survival findings, distinguish log hazard ratios from odds/risk ratios and absolute risk. Report the endpoint/time-axis, event/censor counts, censoring/PH assumptions and source model. Treat survival_model_context and its separately adjusted or unavailable PH diagnostics as interpretation/sensitivity context, never additional primary discoveries or proof of causality. Preserve the distinction between source LRT P values and subsequent meta-analysis Wald inference. Distinguish finite coefficient existence from calibration and assumption checks; compatible supplied results do not imply a native certificate. Infinite coefficients invalidate ordinary Wald effect inference; a separately justified score or likelihood-ratio P-only result may still be usable under its own protocol.  "
            + "Do not attach all bioinformatics output to every result or treat a protein structure, sequence annotation, search hit or gene-level paper as isoform-specific causal validation. "
            + "Begin Discussion with the most impactful finding and why it matters, then develop three to five distinct principal findings across the manuscript with reasons. "
            + "Use discussion_role principal_finding, consecutive impact_rank values up to the configured maximum (default 5), result_ids referring to Results paragraph IDs, and an explicit impact_reason reflected in the prose. "
            + "Rank findings, not analysis types or smallest P-values: weigh effect size and uncertainty, replication, biological relevance, tissue/assay match, sensitivity and contrary evidence. "
            + "Distinct findings may arise from the same analysis; do not present the same finding three times. If fewer than three defensible findings exist, use fewer stories and record why in discussion_selection_note. Each story entry may contain several connected prose paragraphs. "
            + "Develop each principal finding thoroughly across as many paragraphs as its evidence requires. Explain the observed effect and uncertainty, exact gene/variant/isoform and tissue, relevant primary studies and their designs/results, the proposed mechanism step by step, which links are demonstrated versus inferred or unresolved, conflicting/null evidence and alternative explanations, practical relevance and concrete next experiments. "
            + "Aim for the depth of an integrated mechanistic research account such as Wickenhagen et al., Science 2021, doi:10.1126/science.abj3624; this is a depth benchmark, not evidence for a supplied study's findings. Never imply that experiments, MR, colocalization or structural validation were performed when they were not. "
            + "Keep a claim-level evidence ledger for each principal story, distinguishing this study's association, a primary source's reported results, inferred mechanistic links, hypotheses and experiments proposed for the future. Bind literature claims to the actual retrieved passages and state the source study's design, species, tissue, molecular trait, phenotype and limitations. A title or abstract excerpt does not establish that a full article was read. Do not invent inaccessible experimental details. The native finding_review contract checks prose/source bindings and depth facets locally; those checks do not certify scientific truth or approve source-text transmission. "
            + "Keep a substantial self-contained synthesis and all conclusion-changing caveats in the main text. Put the complete source-linked discussion, evidence/claim ledger, sensitivity details, alternative mechanisms and proposed validation in a linked finding-specific supplement. Do not truncate evidence to meet a provisional equal per-finding allowance. "
            + "When a study-wide impact assessment exists, use finding_ids to connect each principal story to the same frozen finding in its linked Results paragraphs. Record impact_selection_override_reason for a scientist's change to the selected findings or their priority order; excluded findings cannot be revived by an override. "
            + "Then discuss future_potential, next_steps and limitations, ending with overall_conclusion (either the final Discussion paragraph or the separate Conclusion, not both). "
            + "Keep future applications and proposed next steps conditional and distinct from work performed; explain how limitations constrain interpretation and conclusions. "
            + "Keep all colocalization signal pairs and distinguish their actual molecular/outcome traits, registered primary prior and sensitivity range; do not select only the largest H4 or count signal pairs as independent loci. Report observed pre-restriction coverage versus candidate matching and convergence/fallback. Source-declared complete regions are not independently certified, and H4 is not association FDR or causal direction. If any colocalization result has evidence_grade exploratory_partial_region_major_limitation, reproduce its required_manuscript_caveat in the main-text limitations: the posterior is conditional on observed selected variants, incomplete coverage can bias it, and a null result is not evidence against sharing. "
            + "Cite retrieved cataloged references where appropriate, retain major contradictory findings in the main text, and keep additional detail in the supplement. "
            + "Never invent results, novelty, causality or drug efficacy to fill the outline. Structural validation is not scientific validation; untagged older prose needs explicit review, not silent rewriting.";
    }
    static Map<String, Object> policy() {
        return Map.of("version", 3, "results_order", RESULTS, "discussion_order", DISCUSSION,
            "manuscript_policy", org.jlinalg.study.StudyWorkflowPolicy.manuscript(Map.of()),
            "first_result", "native participant-characteristics Table 1", "instructions", instructions());
    }
    static String resultRole(Map<String, Object> e) {
        String type = text(e.get("operation_type"));
        if (type.equals("validate_harmonize")) return map(map(e.get("key_findings")).get("primary_results")).isEmpty() ? "" : "association";
        String role = StudyMethods.role(type); return RESULTS.contains(role) ? role : "";
    }
    static boolean completed(Map<String, Object> e) {
        if (!"complete".equals(e.get("state"))) return false;
        // The harmonization method report describes optional effect screening.
        // Disabling that screen does not invalidate completed imported results.
        if ("validate_harmonize".equals(e.get("operation_type")))
            return !map(map(e.get("key_findings")).get("primary_results")).isEmpty();
        return !Boolean.FALSE.equals(map(map(e.get("details")).get("method_report")).get("performed"));
    }
    static boolean hasFindings(Map<String, Object> e) {
        Object f = e.get("key_findings"); return f instanceof Map<?, ?> m ? !m.isEmpty() : f instanceof List<?> l && !l.isEmpty();
    }
    static Map<String, Object> inventory(Map<String, Map<String, Object>> evidence) {
        List<Object> results = new ArrayList<>(), context = new ArrayList<>(), unresolved = new ArrayList<>();
        for (String role : RESULTS) evidence.forEach((id, e) -> {
            if (role.equals(resultRole(e)) && completed(e) && hasFindings(e))
                results.add(Map.of("source", id, "results_role", role, "summary", e.getOrDefault("summary", "")));
        });
        evidence.forEach((id, e) -> {
            if (Set.of("bioinformatics_investigation", "literature_search", "literature_support").contains(text(e.get("operation_type"))))
                context.add(Map.of("source", id, "state", e.getOrDefault("state", "unknown"),
                    "interpretation", "Candidate context only; an explicit finding-to-source relationship and source-level review are required."));
            if (!completed(e) && !"not_applicable".equals(e.get("state")))
                unresolved.add(Map.of("source", id, "state", e.getOrDefault("state", "unknown"), "summary", e.getOrDefault("summary", "")));
        });
        return Map.of("schema_version", 1, "results", results, "contextual_sources", context, "unresolved", unresolved, "policy", policy(),
            "interpretation", "Completed operations supply candidates, not an impact ranking. Inspect pair-level states and actual results; a successful query is not positive support. Empty previews are retained in the supplement, not promoted to findings.");
    }
    static Map<String, Object> template(String title, Map<String, Map<String, Object>> evidence) {
        List<Object> sections = new ArrayList<>(); int n = 0;
        var impact = StudyImpactNarrative.impact(evidence);
        List<Map<String, Object>> chosen = list(impact.get("selected_findings")).stream().map(StudyPublicEvidence::map).toList();
        Map<String, List<String>> linkedResults = new LinkedHashMap<>();
        for (Object raw : list(inventory(evidence).get("results"))) {
            var r = map(raw); String role = text(r.get("results_role"));
            var p = paragraph("results-" + (++n), "results", "[SCIENTIST INPUT REQUIRED: report " + role.replace('_', ' ')
                + " from " + r.get("source") + " with effect size, uncertainty and null or discordant findings; link relevant bioinformatics context explicitly.]", List.of(r.get("source")));
            p.put("results_role", role); p.put("title", role.replace('_', ' ')); p.put("supporting_evidence", List.of());
            List<String> findingIds = new ArrayList<>(); var source = evidence.get(r.get("source"));
            for (var finding : chosen) if (finding.get("hypothesis").equals(source.get("hypothesis")) && list(finding.get("observations")).stream().map(StudyPublicEvidence::map)
                    .anyMatch(row -> Boolean.TRUE.equals(row.get("primary")) && source.get("operation_type").equals(row.get("operation_type")))) {
                String id = text(finding.get("finding_id")); findingIds.add(id); linkedResults.computeIfAbsent(id, ignored -> new ArrayList<>()).add(text(p.get("id")));
            }
            if (!findingIds.isEmpty()) p.put("finding_ids", findingIds); sections.add(p);
        }
        // Empty slots are research needs, not an algorithmic ranking of available analyses.
        for (int rank = 1; rank <= (impact.isEmpty() ? 3 : chosen.size()); rank++) {
            var p = paragraph("discussion-finding-" + rank, "discussion", "[SCIENTIST INPUT REQUIRED: discuss the "
                + (rank == 1 ? "most" : rank == 2 ? "second most" : "third most")
                + " impactful finding and explain why, considering effect size, uncertainty, replication, biology and conflicting evidence.]", List.of());
            p.put("discussion_role", "principal_finding"); p.put("impact_rank", rank); p.put("result_ids", List.of());
            p.put("impact_reason", "[SCIENTIST INPUT REQUIRED: justify this priority; no automatic impact ranking.]");
            if (!impact.isEmpty()) {
                var finding = chosen.get(rank - 1); String id = text(finding.get("finding_id"));
                p.put("finding_ids", List.of(id)); p.put("result_ids", linkedResults.getOrDefault(id, List.of())); p.put("sources", List.of(impact.get("source")));
                var rubric = map(map(finding.get("prior_assessment")).get("rubric"));
                p.put("title", finding.get("feature_id")); p.put("impact_reason", "Unreviewed AI proposal: " + text(rubric.get("effect_relevance")) + " " + text(rubric.get("novelty")));
            }
            sections.add(p);
        }
        for (String role : DISCUSSION.subList(1, DISCUSSION.size())) {
            String task = switch (role) {
                case "future_potential" -> "Describe conditional scientific or translational potential, without implying clinical utility or drug efficacy.";
                case "next_steps" -> "Propose prioritized validation or sensitivity work, with rationale and required data; do not claim it was performed.";
                case "limitations" -> "Explain design, sample, confounding, uncertainty, tissue/assay and evidence gaps that constrain these conclusions.";
                default -> "Conclude with the main supported contribution and its scope; introduce no new finding or unsupported causal claim.";
            };
            var p = paragraph("discussion-" + role.replace('_', '-'), role.equals("overall_conclusion") ? "conclusion" : "discussion",
                "[SCIENTIST INPUT REQUIRED: " + task + "]", List.of());
            p.put("discussion_role", role); sections.add(p);
        }
        var result = new LinkedHashMap<String, Object>(); result.put("schema_version", 1); result.put("id", "results-discussion-revision"); result.put("title", title);
        result.put("rationale", "Align Results with Methods and develop a reasoned, source-linked Discussion. Review AI recommendations and preserve mandatory caveats; explain fewer findings and record any selection override."); result.put("sections", sections);
        if (!impact.isEmpty()) {
            result.put("manuscript_policy", impact.get("manuscript_policy"));
            if (!text(impact.get("selection_note")).isBlank()) result.put("discussion_selection_note", impact.get("selection_note"));
        }
        return result;
    }
    private static Map<String, Object> paragraph(String id, String section, String text, List<?> sources) {
        var p = new LinkedHashMap<String, Object>(); p.put("id", id); p.put("section", section); p.put("text", text);
        p.put("sources", sources); p.put("references", List.of()); p.put("rationale", "Develop evidence-grounded Results and Discussion without changing analyses."); return p;
    }
    private static boolean missing(Object value) { String s = text(value); return s.isBlank() || s.contains("[SCIENTIST INPUT REQUIRED"); }
    static Map<String, Object> assess(Map<String, Object> revision, Map<String, Map<String, Object>> evidence) throws IOException {
        return assess(revision, evidence, true);
    }
    static Map<String, Object> assess(Map<String, Object> revision, Map<String, Map<String, Object>> evidence, boolean enforceDraftBudget) throws IOException {
        Map<String, Object> wordBudget = StudyWordBudget.assess(revision);
        Map<String, Object> manuscriptPolicy = map(wordBudget.get("policy"));
        int minimumFindings = ((Number) manuscriptPolicy.get("minimum_findings")).intValue();
        int maximumFindings = ((Number) manuscriptPolicy.get("maximum_findings")).intValue();
        if (enforceDraftBudget && Boolean.TRUE.equals(wordBudget.get("blocks_publication")))
            throw new IOException("Manuscript main text exceeds " + manuscriptPolicy.get("max_main_words")
                + " words: " + wordBudget.get("main_text_words") + "; shorten prose or supply an explicit journal manuscript_policy");
        if (revision.containsKey("discussion_selection_note") && !(revision.get("discussion_selection_note") instanceof String)) throw new IOException("discussion_selection_note must be explanatory text");
        List<?> sections = list(revision.get("sections")); List<String> needs = new ArrayList<>();
        Map<String, Map<String, Object>> results = new LinkedHashMap<>(); Set<String> covered = new LinkedHashSet<>();
        int last = -1, legacy = 0;
        for (Object raw : sections) {
            var p = map(raw); String section = text(p.get("section"));
            if (section.equals("results")) results.put(text(p.get("id")), p);
            if (p.containsKey("results_role")) {
                if (!section.equals("results")) throw new IOException("results_role is only valid for Results paragraphs");
                String role = text(p.get("results_role")); int order = RESULTS.indexOf(role);
                if (order < 0 || order < last) throw new IOException("Results must follow the Methods results_role order: " + RESULTS);
                last = order;
                List<String> matched = list(p.get("sources")).stream().map(Object::toString)
                    .filter(id -> evidence.containsKey(id) && completed(evidence.get(id)) && hasFindings(evidence.get(id)) && role.equals(resultRole(evidence.get(id)))).toList();
                if (matched.isEmpty()) throw new IOException("Results role " + role + " requires matching completed analysis evidence with findings");
                covered.addAll(matched);
                if (missing(p.get("text"))) needs.add(p.get("id") + ": supply reviewed Results prose");
                if (list(p.get("supporting_evidence")).isEmpty() && evidence.values().stream().anyMatch(e -> "bioinformatics_investigation".equals(e.get("operation_type")) && hasFindings(e)))
                    needs.add(p.get("id") + ": review available bioinformatics context; link relevant evidence explicitly or explain why it does not support this finding");
            } else if (section.equals("results")) legacy++;
            if (p.containsKey("supporting_evidence")) {
                if (!section.equals("results") || !(p.get("supporting_evidence") instanceof List<?>)) throw new IOException("supporting_evidence must be a list on a Results paragraph");
                Set<String> seen = new LinkedHashSet<>();
                for (Object item : list(p.get("supporting_evidence"))) {
                    var s = map(item);
                    if (!s.keySet().containsAll(Set.of("source", "relationship")) || !Set.of("source", "relationship", "references", "citation_basis").containsAll(s.keySet())) throw new IOException("supporting_evidence entries require source and relationship, with optional references and citation_basis");
                    String id = text(s.get("source"));
                    if (!evidence.containsKey(id)) throw new IOException("Unknown supporting evidence source: " + id);
                    if (!seen.add(id)) throw new IOException("Duplicate supporting evidence source: " + id);
                    if (missing(s.get("relationship"))) throw new IOException("Supporting evidence needs an explicit finding-to-source relationship");
                    // An operation may have useful completed rows and still be awaiting other queries.
                    // Do not silently discard partial evidence or certify it from the operation state.
                    if (!completed(evidence.get(id)) || !hasFindings(evidence.get(id))) needs.add(p.get("id") + ": supporting source " + id + " is incomplete or empty; identify the usable source-level result, otherwise describe the gap, not positive support");
                    if (list(p.get("sources")).contains(id)) needs.add(p.get("id") + ": primary and supporting evidence share " + id + "; this is not independent validation");
                }
            }
        }
        for (Object raw : list(inventory(evidence).get("results"))) if (!covered.contains(map(raw).get("source")))
            needs.add(map(raw).get("source") + ": missing classified Results coverage or an explicit scientific explanation for supplemental-only reporting");
        if (legacy > 0) needs.add("Classify " + legacy + " existing Results block(s); older prose is preserved, not silently reordered");
        Set<String> roles = new LinkedHashSet<>(); Set<String> discussed = new LinkedHashSet<>(); int rank = 0; last = -1; legacy = 0;
        // Rendering groups sections, so a separate Conclusion always follows Discussion.
        for (String section : List.of("discussion", "conclusion")) for (Object raw : sections) {
            var p = map(raw); if (!section.equals(p.get("section"))) continue;
            if (!p.containsKey("discussion_role")) { legacy++; continue; }
            String role = text(p.get("discussion_role")); int order = DISCUSSION.indexOf(role);
            if (order < 0 || order < last || (section.equals("conclusion") && !role.equals("overall_conclusion"))) throw new IOException("Discussion must follow principal findings, future_potential, next_steps, limitations, overall_conclusion order");
            last = order;
            if (!role.equals("principal_finding") && !roles.add(role)) throw new IOException("Duplicate Discussion role: " + role);
            if (missing(p.get("text"))) needs.add(p.get("id") + ": supply reviewed Discussion prose");
            if (role.equals("principal_finding")) {
                roles.add(role); rank++;
                if (rank > maximumFindings || !Integer.valueOf(rank).equals(p.get("impact_rank")))
                    throw new IOException("Principal findings require consecutive integer impact_rank from 1 through " + maximumFindings);
                if (text(p.get("text")).strip().split("\\R\\s*\\R").length != 1) throw new IOException("Each principal finding must be one Discussion paragraph");
                if (missing(p.get("impact_reason"))) needs.add(p.get("id") + ": explain why this finding has this impact rank");
                if (!(p.get("result_ids") instanceof List<?>)) throw new IOException("principal_finding result_ids must be a list");
                if (list(p.get("result_ids")).isEmpty()) needs.add(p.get("id") + ": link the discussed finding to Results paragraph IDs");
                Set<String> seen = new LinkedHashSet<>();
                for (Object id : list(p.get("result_ids"))) {
                    if (!results.containsKey(text(id))) throw new IOException("Unknown Results paragraph in result_ids: " + id);
                    if (!seen.add(text(id))) throw new IOException("Duplicate Results paragraph in result_ids: " + id);
                    if (!discussed.add(text(id))) needs.add(p.get("id") + ": shares a Results paragraph with another priority; verify these are distinct findings, not repeated claims");
                }
            } else if (p.containsKey("impact_rank") || p.containsKey("impact_reason") || p.containsKey("result_ids")) throw new IOException("Impact fields are only valid for principal_finding paragraphs");
        }
        for (Object raw : sections) {
            var p = map(raw);
            if (p.containsKey("discussion_role") && !Set.of("discussion", "conclusion").contains(text(p.get("section")))) throw new IOException("discussion_role is only valid for Discussion or Conclusion paragraphs");
            if (!p.containsKey("discussion_role") && (p.containsKey("impact_rank") || p.containsKey("impact_reason") || p.containsKey("result_ids"))) throw new IOException("Impact fields require a principal_finding discussion_role");
        }
        if (rank < minimumFindings && missing(revision.get("discussion_selection_note"))) needs.add("Explain fewer than "
            + minimumFindings + " defensible principal findings in discussion_selection_note; do not invent findings to fill a quota");
        for (String role : DISCUSSION.subList(1, DISCUSSION.size())) if (!roles.contains(role)) needs.add(role + ": missing Discussion or Conclusion paragraph");
        if (legacy > 0) needs.add("Classify " + legacy + " existing Discussion/Conclusion block(s); older prose is preserved, not silently rewritten");
        return Map.of("policy", policy(), "status", needs.isEmpty() ? "structure_complete_scientific_review_required" : "needs_input", "needs", needs,
            "principal_finding_paragraphs", rank, "word_budget", wordBudget, "scientific_review", "Verify impact reasons, independence, source-level bioinformatics relevance, citations, adverse evidence and conclusions. Order and source-ID checks do not validate scientific claims.");
    }
}
