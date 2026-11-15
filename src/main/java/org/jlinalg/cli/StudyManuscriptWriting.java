/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jlinalg.study.StudyEvidenceIndex;
import org.jlinalg.study.StudyManuscriptPolicy;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Local editorial cues, never a readability or scientific validity certificate. */
final class StudyManuscriptWriting {
    private StudyManuscriptWriting() { }
    static final String AVAILABILITY_PLACEHOLDER = "[SCIENTIST INPUT REQUIRED: provide one concise sentence with verified data/code locations and access conditions.]";
    private static final Pattern AVAILABILITY = Pattern.compile("(?im)^(?:#{1,6}[\\t ]*)?Data and (?:code|program) availability[\\t ]*:?\\r?$");
    private static final Pattern GENERIC_CAUTION = Pattern.compile(
        "(?i)\\b(?:does? not|cannot|neither|not)\\s+(?:by itself\\s+)?(?:establish|prove|demonstrate|validate)|\\bnot (?:independent validation|a validated|proof of)");
    private static final Pattern AUDIT_PROSE = Pattern.compile(
        "(?i)editorial rationale|source status:|not automatically verified support|within one auditable analysis|most impactful finding|second finding is|third finding is|audit trail|audit provenance|template version|checksums?|sha-?256|execution receipts?|cache (?:keys?|reuse)|version history");

    static void writePolicy(Path folder) throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("manuscript-writing-prompt.txt"), StudyManuscriptPolicy.instructions());
        write(folder.resolve("manuscript-writing-policy.yaml"), StudyManuscriptPolicy.identity());
    }

    static String withAvailability(String body) {
        if (AVAILABILITY.matcher(body).find()) return body;
        String statement = "\n\nData and code availability\n" + AVAILABILITY_PLACEHOLDER + "\n\n";
        var end = Pattern.compile("(?im)^(?:#{1,6}[\\t ]*)?(?:References|Supplementary[^\\r\\n]*)[\\t ]*:?\\r?$").matcher(body);
        return end.find() ? body.substring(0, end.start()) + statement + body.substring(end.start()) : body + statement;
    }

    static List<Object> localTables(Context context) throws IOException {
        List<Object> tables = new ArrayList<>();
        for (Path directory : context.dependencyDirectories().values().stream().distinct().toList()) {
            Path manifest = directory.resolve("manifest.yaml");
            if (!Files.isRegularFile(manifest)) continue;
            var source = StudyYaml.loadArtifact(manifest);
            if (!"participant_characteristics".equals(source.get("operation_type")) || !"complete".equals(source.get("state"))) continue;
            tables.addAll(list(map(StudyEvidenceIndex.keyFindings(directory, "participant_characteristics")).get("tables")));
        }
        return tables;
    }

    static Map<String, Object> populationReview(List<Object> tables) {
        List<String> needs = new ArrayList<>();
        if (tables.isEmpty()) needs.add("Table 1: supply participant summaries with verified analysis-sample membership.");
        boolean verified = !tables.isEmpty();
        for (Object raw : tables) {
            var table = map(raw); String id = text(table.getOrDefault("dataset", "population"));
            if (!Boolean.TRUE.equals(table.get("analysis_population_verified"))) {
                verified = false; needs.add(id + ": phenotype-file population is provisional; verify analysis membership.");
            }
            if (!(table.get("N") instanceof Number)) needs.add(id + ": supply the analyzed participant count and unit.");
            if (map(table.get("sex")).isEmpty()) needs.add(id + ": supply the recorded sex/gender summary or explain its unavailability.");
            if (map(table.get("continuous")).isEmpty() && map(table.get("binary")).isEmpty())
                needs.add(id + ": supply age, phenotype and relevant covariate summaries with denominators and units.");
        }
        return Map.of("status", needs.isEmpty() ? "available_scientific_review_required" : "needs_input",
            "analysis_population_verified", verified, "table_count", tables.size(), "needs", needs,
            "scientist_checks", List.of("Confirm analysis membership and analysis-specific sample sizes.",
                "Check age, primary phenotype, relevant covariates, units, missingness and denominators for every cohort.",
                "Confirm the recorded meaning of sex/gender and ancestry fields; do not infer categories."),
            "scope", "Local participant-summary artifacts only; no population values are added automatically to model requests.");
    }

    static Map<String, Object> assess(List<?> sections, List<Object> tables) {
        List<Object> cues = new ArrayList<>(); int total = 0;
        for (Object raw : sections) {
            var p = map(raw); String prose = text(p.get("text"));
            int caution = Math.toIntExact(GENERIC_CAUTION.matcher(prose).results().count()); total += caution;
            if (caution >= 2) cues.add(Map.of("paragraph_id", p.getOrDefault("id", "unclassified"),
                "cue", "repeated_generic_caution", "count", caution,
                "action", "Review for repetition. Keep every material limitation; qualify the specific claim and consolidate shared limitations."));
            if (!List.of("availability", "supplementary").contains(text(p.get("section"))) && AUDIT_PROSE.matcher(prose).find())
                cues.add(Map.of("paragraph_id", p.getOrDefault("id", "unclassified"),
                    "cue", "editorial_or_ranking_language", "action", "Relocate internal provenance to review records or a brief supplemental note through an attributed revision. Keep main-text availability concise."));
        }
        if (total >= 3) cues.add(Map.of("cue", "manuscript_caution_repetition", "count", total,
            "action", "Check repeated generic qualifications across paragraphs; this cue never authorizes automatic deletion."));
        var result = new LinkedHashMap<String, Object>();
        result.put("template", StudyManuscriptPolicy.identity()); result.put("status", "editorial_review_required");
        result.put("automated_cues", cues); result.put("population_review", populationReview(tables));
        result.put("motivation_review", StudyIntroduction.motivationReview());
        result.put("discussion_review", StudyResultsDiscussion.discussionReview());
        result.put("mathematics_review", StudyMathTypography.review(sections));
        result.put("reader_checks", List.of(
            "Opening establishes the phenotype/biological problem before hit genes or transcript accessions.",
            "The Introduction persuasively explains why the study needs to be done; topic sentences connect the scientist's biological question, measurement/proxy rationale, consequential gap and objectives.",
            "Potential scientific impact follows from resolving that gap: what could be learned or enabled, with appropriate conditions and no unsupported application promises.",
            "The last three Introduction paragraphs form explicit question-to-measurement, measurement-to-gap-and-impact, and gap-to-objective bridges; motivations match study history.",
            "Results orient the reader to the analyzed population and Table 1 before molecular findings.",
            "Discussion persuasively explains the contribution relative to primary studies and integrates findings, including tensions and dependent evidence, into an answer to the motivating question.",
            "Discussion develops evidence-grounded implications and potential impact; any claimed surprise has a documented prior expectation.",
            "Material weaknesses identify the affected inference and what remains supported; prioritized next steps explain how their outcomes could change the interpretation and what constraints would remain.",
            "The conclusion ties the findings together into a supported advance, its scope and the next justified question, without new results or stronger causal claims.",
            "Write/update the structured Background, Methods, Results, Conclusion abstract from the final body and place it once at the front.",
            "Main-text provenance is limited to a concise data/code availability statement; internal versions and audit details stay in review records or a brief supplement note.",
            "All material caveats remain; generic disclaimers and editorial metadata do not interrupt the argument."));
        result.put("limitation", "Lexical cues may be false positives and do not assess semantic coherence, source support, completeness or scientific validity. No prose was rewritten or removed.");
        return result;
    }

    static Map<String, Object> assessDraft(String answer, List<Object> tables) {
        var review = new LinkedHashMap<>(assess(List.of(Map.of("id", "ai-draft", "text", answer)), tables));
        var draft = StudyAbstract.splitDraft(answer);
        var abstractReview = StudyAbstract.review(draft.abstractText());
        abstractReview.put("input_abstract_count", draft.abstractCount());
        if (draft.abstractCount() != 1) {
            abstractReview.put("status", "needs_input");
            var needs = new ArrayList<Object>(list(abstractReview.get("needs")));
            needs.add("Return exactly one abstract; missing abstracts render as placeholders and repeated blocks are retained together for review.");
            abstractReview.put("needs", needs);
        }
        review.put("abstract_review", abstractReview);
        review.put("availability_statement_present", AVAILABILITY.matcher(draft.body()).find());
        int markers = Math.toIntExact(Pattern.compile(Pattern.quote("[TABLE_1]")).matcher(answer).results().count());
        review.put("table_1_marker_present", markers > 0);
        review.put("table_1_marker_count", markers);
        review.put("table_1_placement_review", markers == 1 ? "Verify the marker follows the opening Results population paragraph."
            : markers > 1 ? "Duplicate markers: table inserted at the first marker; later markers render as Table 1 references. Review placement."
            : "Marker missing: table inserted after a recognizable Results heading, or appended if none exists. Review placement.");
        return review;
    }
}
