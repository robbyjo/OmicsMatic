package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyIntroductionTest {
    private static List<Object> sections() {
        List<Object> result = new ArrayList<>();
        for (String role : StudyIntroduction.ROLES) result.add(new LinkedHashMap<>(Map.of(
            "section", "introduction", "introduction_role", role, "text", "Fixture prose for " + role,
            "references", List.of("pmid:123"))));
        return result;
    }
    @Test void fourOrFiveOrderedParagraphsRequireScientificReviewNotPublicationCertification() throws Exception {
        var sections = sections();
        assertEquals("structure_complete_scientific_review_required", StudyIntroduction.assess(sections).get("status"));
        var first = StudyPublicEvidence.map(sections.get(0));
        first.put("text", "First field paragraph.\r\n\r\nSecond field paragraph."); sections.set(0, first);
        var review = StudyIntroduction.assess(sections);
        assertEquals(2, StudyPublicEvidence.map(review.get("paragraph_counts")).get("field_background"));
        assertEquals("structure_complete_scientific_review_required", review.get("status"));
    }
    @Test void wrongOrderAndMisplacedRolesAreErrorsButParagraphTargetsAreFlexible() throws Exception {
        var order = sections(); java.util.Collections.swap(order, 0, 1);
        assertThrows(IOException.class, () -> StudyIntroduction.assess(order));
        for (var invalid : List.of(Map.of("section", "methods", "introduction_role", "field_background"),
                Map.of("section", "introduction", "introduction_role", "unknown")))
            assertThrows(IOException.class, () -> StudyIntroduction.assess(List.of(invalid)));
        var paragraphs = sections(); var rationale = StudyPublicEvidence.map(paragraphs.get(1));
        rationale.put("text", "Assay rationale.\n\nTissue rationale linked to the same question."); paragraphs.set(1, rationale);
        assertEquals("structure_complete_scientific_review_required", StudyIntroduction.assess(paragraphs).get("status"));
    }
    @Test void missingCitationsAndLegacyProseRemainExplicitNeeds() throws Exception {
        var sections = sections(); var first = StudyPublicEvidence.map(sections.get(0));
        first.remove("references"); sections.set(0, first);
        assertEquals("needs_input", StudyIntroduction.assess(sections).get("status"));
        var legacy = Map.of("section", "introduction", "text", "Existing prose must remain unchanged.");
        assertTrue(StudyIntroduction.assess(List.of(legacy)).get("needs").toString().contains("older prose is preserved"));
        assertEquals("Existing prose must remain unchanged.", legacy.get("text"));
    }
    @Test void orderedCitedParagraphsCannotCertifyPersuasiveMotivationOrImpact() throws Exception {
        var result = StudyIntroduction.assess(sections());
        assertEquals("structure_complete_scientific_review_required", result.get("status"));
        var motivation = StudyPublicEvidence.map(result.get("motivation_review"));
        assertEquals("substantive_review_required", motivation.get("status"));
        assertEquals(false, motivation.get("assessed_by_structural_checks"));
        assertTrue(StudyPublicEvidence.list(motivation.get("required_argument")).contains("potential_scientific_impact"));
    }
    @Test void templateUsesDeclaredHypothesesButDoesNotInventFieldOrMotivation() throws Exception {
        var template = StudyIntroduction.template("Fixture", List.of("Our declared hypothesis."));
        var sections = StudyPublicEvidence.list(template.get("sections"));
        assertEquals(4, sections.size());
        assertTrue(StudyPublicEvidence.map(sections.get(3)).get("text").toString().contains("Our declared hypothesis."));
        assertEquals("needs_input", StudyIntroduction.assess(sections).get("status"));
        assertTrue(StudyIntroduction.instructions().contains("do not assert first-ever novelty"));
        assertFalse(StudyIntroduction.scaffold(List.of(Map.of("text", "<script>alert(1)</script>"))).contains("<script>"));
    }
}
