/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared, source-grounded introduction policy for native drafts and LLM advice. */
final class StudyIntroduction {
    static final List<String> ROLES = List.of("field_background", "hypothesis_relevance",
        "importance_and_gap", "hypotheses_and_motivation");
    static final List<String> SEARCH_STRATEGIES = List.of("field_background", "hypothesis_relevance",
        "similar_studies", "field_conflicting_evidence");
    private StudyIntroduction() { }

    static String instructions() {
        return "Write the Introduction as one connected problem-to-objective argument, using four or five prose paragraphs as a starting plan, not a rigid quota or a set of mini-sections. "
            + "Start with the phenotype or biological problem and why it matters, then synthesize established human research. Explain unfamiliar molecular concepts before naming hit genes or transcript accessions. "
            + "The Introduction MUST persuasively motivate the study and explain its potential scientific impact. Anchor the argument in the scientist's stated biological question, not a replacement goal inferred from available data or significant hits. "
            + "Next connect that question to the biological process of interest, measured quantity or proxy, and the reason for choosing it over a relevant alternative. Explain how its interpretability helps answer the biological question; tissue and replication support this rationale rather than replacing it. "
            + "Then use the closest studies to explain the consequential unresolved question and what resolving it could teach us or enable, such as distinguishing mechanisms or guiding a specific experiment. "
            + "Keep potential impact prospective and evidence-grounded; do not turn it into an observed result, a generic application promise or a claim of methodological superiority. Cite comparisons and do not equate a difference with proven novelty. "
            + "End with one paragraph stating the overall hypotheses and their scientific motivation, using the declared study hypotheses "
            + "rather than retrospectively inventing motivations from significant findings. "
            + "Use paragraph roles field_background, hypothesis_relevance, importance_and_gap, hypotheses_and_motivation in that order. Read the topic sentences together: each must advance the preceding argument. Roles are connected reasoning functions, not independent prose assignments. "
            + "Audit the last three paragraphs as question-to-measurement, measurement-to-consequential-gap-and-impact, and gap-to-objective bridges. A reader must be able to explain why this study needs to be done and why its answer could matter. Rewrite weak bridges; role labels alone cannot demonstrate persuasiveness. "
            + "Search the field and similar studies, not only top-hit genes. Include contradictory findings. "
            + "Use only retrieved, cataloged references for factual and prior-work claims. A bounded, failed or empty search does not establish "
            + "that no similar research exists; do not assert first-ever novelty. Identify missing coverage as a research need. "
            + "Citation presence and paragraph-role checks do not verify that a source actually supports a claim. "
            + "Keep drafting instructions, source-review notes and software/audit details out of finished introduction prose; "
            + "put unresolved writing/research needs in the review checklist instead of inventing background.";
    }

    static Map<String, Object> policy() {
        return Map.of("version", 3, "paragraphs_target_min", 4, "paragraphs_target_max", 5,
            "ordered_roles", ROLES, "background_paragraphs", "one or two as a starting plan; expand roles when the argument requires it",
            "template", org.jlinalg.study.StudyManuscriptPolicy.identity(),
            "literature_search_strategies", SEARCH_STRATEGIES, "instructions", instructions());
    }

    /** A substantive reading requirement, never a lexical or structural pass certificate. */
    static Map<String, Object> motivationReview() {
        return Map.of("status", "substantive_review_required", "assessed_by_structural_checks", false,
            "required_argument", List.of("scientist_declared_biological_question", "consequential_knowledge_gap",
                "question_to_measurement_or_proxy", "measurement_choice_and_interpretability",
                "potential_scientific_impact", "gap_to_objective_and_design"),
            "reader_questions", List.of(
                "Why does this study need to be done, beyond collecting reproducible associations?",
                "Does the proposed motivation preserve the scientist's actual question and its history?",
                "Why does this measurement or proxy help investigate the biological process, compared with the relevant alternative?",
                "What could resolving this gap change in understanding or enable experimentally? Is that prospect grounded rather than promised?",
                "Does each of the last three paragraphs supply a reason for the next, ending in an objective that answers the original question?"),
            "editorial_action", "Identify the weakest logical bridge and any missing potential-impact argument; propose an attributed rewrite. If the scientist's motivation or supporting evidence is absent, request the specific missing input instead of inventing it.");
    }

    static Map<String, Object> assess(List<?> sections) throws IOException {
        List<String> needs = new ArrayList<>(); Map<String, Integer> counts = new LinkedHashMap<>();
        ROLES.forEach(role -> counts.put(role, 0));
        int previous = -1, unclassified = 0;
        for (Object raw : sections) {
            Map<String, Object> p = map(raw);
            if (!p.containsKey("introduction_role")) {
                if ("introduction".equals(p.get("section"))) unclassified++;
                continue;
            }
            if (!"introduction".equals(p.get("section"))) throw new IOException("introduction_role is only valid for introduction paragraphs");
            String role = text(p.get("introduction_role")); int position = ROLES.indexOf(role);
            if (position < 0) throw new IOException("Unknown introduction_role: " + role);
            if (position < previous) throw new IOException("Introduction paragraphs must follow field_background, hypothesis_relevance, importance_and_gap, hypotheses_and_motivation order");
            previous = position;
            String prose = text(p.get("text")).strip();
            int paragraphs = prose.isEmpty() ? 0 : prose.split("\\R\\s*\\R").length;
            counts.merge(role, paragraphs, Integer::sum);
            if (prose.isBlank() || prose.contains("[SCIENTIST INPUT REQUIRED")) needs.add(role + ": supply source-grounded prose");
            if (!role.equals("hypotheses_and_motivation") && StudyCitations.identifiers(p, "text").isEmpty())
                needs.add(role + ": cite cataloged literature supporting the field, rationale or prior-work comparison");
        }
        counts.forEach((role, count) -> { if (count == 0) needs.add(role + ": missing paragraph"); });
        if (unclassified > 0) needs.add("Assign introduction_role to " + unclassified + " existing introduction block(s); older prose is preserved, not silently classified or replaced");
        return Map.of("policy", policy(), "status", needs.isEmpty() ? "structure_complete_scientific_review_required" : "needs_input",
            "paragraph_counts", counts, "needs", needs, "motivation_review", motivationReview(), "scientific_review", List.of(
                "Read topic sentences as one argument: phenotype/problem, prior knowledge, molecular/tissue rationale, consequential gap and objectives. Avoid leading with result-selected genes.",
                "Assess literature coverage, relevance and source support, including contradictory studies and retrieval limits.",
                "Verify the actual comparisons and contribution; citation presence is not novelty verification.",
                "Confirm motivations and hypotheses against the study history; record changed hypotheses in an amendment."));
    }

    static Map<String, Object> template(String title, List<String> hypotheses) {
        List<Object> sections = new ArrayList<>();
        List<String> tasks = List.of("Begin with the phenotype or biological problem and why it matters; synthesize established human research before hit genes. Define the molecular concept in accessible language and lead into the unresolved question.",
            "Persuasively connect the scientist's biological question to the process, measurement or proxy and its interpretability. Explain the question-specific choice over a relevant alternative; tissue and population support this bridge, using reviewed references.",
            "Use the closest studies to establish the consequential remaining gap. Develop the potential scientific impact: what answering this question could change in understanding or enable in follow-up. Carry forward the measurement rationale without novelty or application hype.",
            "Close the same argument with the declared biological objective, chosen measurement and design that address that gap. Make the potential contribution clear. Preserve the scientific motivation in the study history; distinguish primary aims from exploratory follow-up.");
        for (int i = 0; i < ROLES.size(); i++) {
            Map<String, Object> p = new LinkedHashMap<>(); String role = ROLES.get(i);
            p.put("id", "intro-" + role.replace('_', '-')); p.put("section", "introduction"); p.put("introduction_role", role);
            String statement = i == 3 && !hypotheses.isEmpty() ? "The study hypotheses are: " + String.join(" ", hypotheses) + " " : "";
            p.put("text", statement + "[SCIENTIST INPUT REQUIRED: " + tasks.get(i) + "]");
            p.put("rationale", "Develop the " + role.replace('_', ' ') + " paragraph from reviewed study and literature evidence.");
            p.put("sources", List.of()); p.put("references", List.of()); sections.add(p);
        }
        return Map.of("schema_version", 1, "id", "introduction-revision", "title", title,
            "rationale", "Develop a field-to-hypothesis introduction using reviewed literature and the declared study hypotheses.", "sections", sections);
    }

    static String scaffold(List<?> sections) {
        StringBuilder html = new StringBuilder();
        for (Object raw : sections) html.append("<p>").append(StudyMathTypography.prose(text(map(raw).get("text")))).append("</p>");
        return html.toString();
    }
    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
