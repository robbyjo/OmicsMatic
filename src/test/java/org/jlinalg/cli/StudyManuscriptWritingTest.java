package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyManuscriptPolicy;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyManuscriptWritingTest {
    @TempDir Path directory;

    @Test void savedPromptIdentityBindsExactInstructions() throws Exception {
        assertTrue(StudyManuscriptPolicy.instructions().startsWith("OmicsMatic manuscript writing template " + StudyManuscriptPolicy.VERSION));
        StudyManuscriptWriting.writePolicy(directory);
        assertEquals(StudyManuscriptPolicy.instructions(), Files.readString(directory.resolve("manuscript-writing-prompt.txt")));
        assertEquals(FollowupSupport.hash(directory.resolve("manuscript-writing-prompt.txt")), StudyManuscriptPolicy.identity().get("prompt_sha256"));
        assertEquals(StudyManuscriptPolicy.VERSION, StudyYaml.loadArtifact(directory.resolve("manuscript-writing-policy.yaml")).get("version"));
    }

    @Test void literalPrecisionRuleReachesWritingPolicyAndDraftReview() {
        String policy = StudyManuscriptPolicy.instructions();
        assertTrue(policy.contains("including literal numbers"));
        assertTrue(policy.contains("approximately 1.48"));
        assertTrue(policy.contains("Preserve exact decision cutoffs"));
        var review = map(StudyManuscriptWriting.assessDraft(
            "Methods\nThe scale was 1.482602218505602 times MAD.", List.of()).get("mathematics_review"));
        assertTrue(review.get("cues").toString().contains("excessive decimal precision"));
    }

    @Test void repeatedCautionIsReviewOnlyAndNeverDeletesScientificProse() {
        String text = "An association does not establish causality. This assay does not establish isoform identity. The result is not independent validation.";
        var sections = List.of(Map.of("id", "finding", "text", text));
        var review = StudyManuscriptWriting.assess(sections, List.of());
        assertFalse(list(review.get("automated_cues")).isEmpty());
        assertEquals(text, sections.get(0).get("text"));
        assertEquals("needs_input", map(review.get("population_review")).get("status"));
        assertEquals("editorial_review_required", review.get("status"));
        assertEquals("substantive_review_required", map(review.get("motivation_review")).get("status"));
        assertEquals(false, map(review.get("motivation_review")).get("assessed_by_structural_checks"));
        assertEquals("substantive_review_required", map(review.get("discussion_review")).get("status"));
        assertEquals(false, map(review.get("discussion_review")).get("assessed_by_structural_checks"));
    }

    @Test void populationReviewKeepsProvisionalAndVerifiedPopulationsDistinct() {
        var provisional = Map.of("dataset", "d", "N", 10, "continuous", Map.of("Age", Map.of("n", 10)), "sex", Map.of("denominator", 10));
        assertFalse((Boolean) StudyManuscriptWriting.populationReview(List.of(provisional)).get("analysis_population_verified"));
        var verified = new java.util.LinkedHashMap<String, Object>(provisional); verified.put("analysis_population_verified", true);
        assertEquals("available_scientific_review_required", StudyManuscriptWriting.populationReview(List.of(verified)).get("status"));
        assertEquals("needs_input", StudyManuscriptWriting.populationReview(List.of(provisional, verified)).get("status"));
    }

    @Test void modelHtmlIsEscapedAndLocalTableIsInsertedEvenWhenMarkerIsMissing() {
        var tables = List.<Object>of(Map.of("dataset", "d", "cohort", "c", "N", 31));
        for (String answer : List.of("Introduction\n<script>bad()</script>\nResults\n[ TABLE ]", "Results\n[TABLE_1]\nObserved result.")) {
            String html = StudyAssistance.manuscript(answer, tables);
            assertTrue(html.contains("id=\"table-1\"")); assertTrue(html.contains("<td>31</td>"));
            assertFalse(html.contains("<script>")); assertFalse(html.contains("Supplied result preview"));
            assertTrue(html.indexOf("Results") < html.indexOf("id=\"table-1\""));
        }
        String empty = StudyAssistance.manuscript("Results\nNo supplied summaries.", List.of());
        assertTrue(empty.contains("Table 1. Participant characteristics"));
        assertTrue(empty.contains("SCIENTIST INPUT REQUIRED"));
        String duplicate = StudyAssistance.manuscript("Results\n[TABLE_1]\nSee [TABLE_1].", tables);
        assertEquals(1, duplicate.split("id=\"table-1\"", -1).length - 1);
        assertTrue(duplicate.contains("See Table 1."));
        assertEquals(2, StudyManuscriptWriting.assessDraft("[TABLE_1]\n[TABLE_1]", tables).get("table_1_marker_count"));
    }

    @Test void localTableReadUsesCompletedSummaryArtifactsWithoutTouchingPatientRows() throws Exception {
        Path source = directory.resolve("summary"); Files.createDirectory(source);
        write(source.resolve("manifest.yaml"), Map.of("operation_type", "participant_characteristics", "state", "complete"));
        write(source.resolve("participant-characteristics.yaml"), Map.of("tables", List.of(Map.of("dataset", "d", "N", 23))));
        var context = new Context(directory, directory, Map.of(), Map.of(), Map.of(), Map.of("summary", source));
        assertEquals(23, map(StudyManuscriptWriting.localTables(context).get(0)).get("N"));
        write(source.resolve("manifest.yaml"), Map.of("operation_type", "participant_characteristics", "state", "waiting_for_input"));
        assertTrue(StudyManuscriptWriting.localTables(context).isEmpty());
    }
}
