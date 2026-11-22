package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyAbstractTest {
    private static final String ABSTRACT = "Background: A defined question.\nMethods: A specified design.\nResults: A measured effect.\nConclusion: A supported implication.";

    @Test void requiresFourNonemptyLabelsInOrder() {
        assertEquals("structure_complete_review_required", StudyAbstract.review(ABSTRACT).get("status"));
        for (String bad : List.of("", "An unstructured abstract.", ABSTRACT.replace("Methods: A specified design.", "Methods:"),
                ABSTRACT.replace("Background:", "Results:"), ABSTRACT + "\nConclusion: Duplicate."))
            assertEquals("needs_input", StudyAbstract.review(bad).get("status"));
        assertEquals("needs_input", StudyAbstract.review(StudyAbstract.labeledText(StudyAbstract.template())).get("status"));
    }

    @Test void abstractLabelsCannotBecomeMainTextHeadingsOrReceiveTableOne() {
        String body = "Introduction\nA question.\nMethods\nA design.\nResults\nAn effect.\nDiscussion\nAn implication.";
        for (String text : List.of("Abstract\n" + ABSTRACT + "\n" + body, body + "\nAbstract\n" + ABSTRACT)) {
            var parts = StudyAbstract.splitDraft(text);
            assertEquals(body, parts.body()); assertEquals(ABSTRACT, parts.abstractText()); assertEquals(1, parts.abstractCount());
            assertEquals(StudyWordBudget.draft(body, Map.of()).get("main_text_words"), StudyWordBudget.draft(text, Map.of()).get("main_text_words"));
            String html = StudyAssistance.manuscript(text, List.of(Map.of("dataset", "d", "N", 4)));
            assertEquals(1, html.split("<h2>Abstract</h2>", -1).length - 1);
            assertTrue(html.indexOf("<h2>Abstract") < html.indexOf("Introduction"));
            assertTrue(html.indexOf("id=\"table-1\"") > html.indexOf("Introduction"));
            assertFalse(html.contains("Template version")); assertFalse(html.contains("Model provenance")); assertFalse(html.contains("<nav>"));
        }
    }

    @Test void missingOrRepeatedAbstractsRemainReviewNeedsAndNeverInventValues() {
        String missing = StudyAssistance.manuscript("Introduction\nA question.\nResults\nAn effect.", List.of());
        assertTrue(missing.contains("Background:")); assertTrue(missing.contains("[SCIENTIST INPUT REQUIRED:"));
        String duplicate = "Abstract\n" + ABSTRACT + "\nIntroduction\nA question.\nAbstract\n" + ABSTRACT;
        var review = StudyPublicEvidence.map(StudyManuscriptWriting.assessDraft(duplicate, List.of()).get("abstract_review"));
        assertEquals("needs_input", review.get("status")); assertEquals(2, review.get("input_abstract_count"));
        assertEquals(2, StudyAbstract.splitDraft(duplicate).abstractText().split("A measured effect", -1).length - 1);
    }

    @Test void changedBodyRequiresReconciliationOfUnchangedAbstract() {
        var sections = List.of(Map.of("section", "abstract", "text", ABSTRACT));
        String first = "<div data-manuscript-abstract=\"true\">Abstract text</div><div data-manuscript-section=\"results\">1 result</div><!-- end-main-text:results -->";
        var changed = StudyAbstract.reviewRevision(sections, first.replace("1 result", "2 results"), first);
        assertEquals(true, changed.get("body_changed_without_abstract_update")); assertEquals("needs_input", changed.get("status"));
        assertEquals(false, StudyAbstract.reviewRevision(sections, first, first).get("body_changed_without_abstract_update"));
    }

    @Test void invalidRoleTagsAreRejectedAndAvailabilityIsOutsideMainBudget() throws Exception {
        StudyAbstract.validateRoles(StudyAbstract.template());
        assertThrows(java.io.IOException.class, () -> StudyAbstract.validateRoles(List.of(Map.of("section", "results", "abstract_role", "results"))));
        assertThrows(java.io.IOException.class, () -> StudyAbstract.validateRoles(List.of(Map.of("section", "abstract", "abstract_role", "unknown"))));
        String body = "Results\nObserved effect.\nData and code availability\nVerified access information.\nReferences\nA reference.";
        assertEquals(3, StudyWordBudget.draft(body, Map.of()).get("main_text_words"));
        assertEquals(body, StudyManuscriptWriting.withAvailability(body));
    }
}
