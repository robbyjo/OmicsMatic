/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyWordBudgetTest {
    @Test void aiDraftUsesMainSectionsAndPreservesTheDeclaredCeiling() {
        String draft = "Title outside the paper\nAbstract\nIgnored abstract\nIntroduction\nOne two\nMethods\nThree\nResults\nFour five\n| ignored table |\nDiscussion\nSix\nConclusion\nSeven\nReferences\nIgnored reference";
        var policy = org.jlinalg.study.StudyWorkflowPolicy.manuscript(Map.of("max_main_words", 6));
        var counts = StudyWordBudget.draft(draft, policy);
        assertEquals(12, counts.get("main_text_words")); assertEquals(false, counts.get("within_limit"));
        assertThrows(IllegalArgumentException.class, () -> StudyWordBudget.draft("Unscoped AI prose", policy));
    }
    @Test void finalRenderedWordsIncludeEntitiesAndExcludeNestedAuditAndTables() throws Exception {
        String html = """
            <p>Metadata outside the paper must not be counted.</p>
            <div data-manuscript-section="results"><h2>Results</h2><p>One &amp; two.</p>
            <details><summary>Audit</summary>Hidden<details>Nested hidden</details>Still hidden</details>
            <aside><h3>Table legend</h3><table><tr><td>Counts not prose</td></tr></table></aside>
            <p>Three.</p></div><!-- end-main-text:results -->
            <div data-manuscript-section="methods"><h2>Methods</h2><p>Four.</p></div><!-- end-main-text:methods -->
            <h2>References</h2><p>Excluded bibliography.</p>
            """;
        var result = StudyWordBudget.assessRendered(html, Map.of());
        assertEquals(6, result.get("main_text_words"));
        assertEquals(Map.of("results", 4, "methods", 2), result.get("section_words"));
        assertTrue(result.get("counted_stage").toString().contains("final rendered"));
    }

    @Test void renderedExpansionCannotBypassTheLimitAndAnExplicitJournalLimitIsRecorded() throws Exception {
        String html = "<div data-manuscript-section=\"results\"><p>" + "word ".repeat(8001)
            + "</p></div><!-- end-main-text:results -->";
        assertEquals(false, StudyWordBudget.assessRendered(html, Map.of()).get("within_limit"));
        var changed = StudyWordBudget.assessRendered(html, Map.of("max_main_words", 9000));
        assertEquals(true, changed.get("within_limit"));
        assertEquals(9000, StudyPublicEvidence.map(changed.get("policy")).get("max_main_words"));
    }

    @Test void missingBoundariesAndInvalidPoliciesFailInsteadOfInventingACount() {
        assertThrows(java.io.IOException.class, () -> StudyWordBudget.assessRendered("<p>Unscoped paper.</p>", Map.of()));
        assertThrows(java.io.IOException.class, () -> StudyWordBudget.assessRendered("", Map.of("max_main_words", -1)));
    }

    @Test void defaultTargetRecordsOverageWithoutBlockingAndLegacyExplicitLimitsRemainStrict() throws Exception {
        String html = "<div data-manuscript-section=\"results\"><p>" + "word ".repeat(8001)
            + "</p></div><!-- end-main-text:results -->";
        var target = StudyWordBudget.assessRendered(html, Map.of());
        assertEquals(false, target.get("within_limit"));
        assertEquals(false, target.get("blocks_publication"));
        assertEquals("over_main_text_target_review_supplements", target.get("disposition"));
        var strict = StudyWordBudget.assessRendered(html, Map.of("max_main_words", 8000));
        assertEquals(true, strict.get("blocks_publication"));
        assertEquals("strict", StudyPublicEvidence.map(strict.get("policy")).get("word_limit_mode"));
        var explicitTarget = StudyWordBudget.assessRendered(html,
            Map.of("max_main_words", 8000, "word_limit_mode", "target"));
        assertEquals(false, explicitTarget.get("blocks_publication"));
    }

    @Test void extensiveSupplementIsCountedSeparatelyWithoutReducingFindingDetail() {
        String extended = "Complete mechanistic evidence ".repeat(6000);
        var report = StudyWordBudget.draft("Results\nObserved evidence.\nDiscussion\nFull synthesis.\nSupplementary Note 1\n"
            + extended, Map.of("max_main_words", 6, "word_limit_mode", "strict"));
        assertEquals(6, report.get("main_text_words"));
        assertEquals(18000, report.get("supplement_words"));
        assertEquals(false, report.get("blocks_publication"));
        assertEquals(true, StudyWordBudget.draft("Results\n" + "word ".repeat(9),
            Map.of("max_main_words", 6)).get("blocks_publication"));
    }

    @Test void unrecognizedOrNullLimitModesCannotBypassAnExplicitJournalLimit() throws Exception {
        assertThrows(java.io.IOException.class, () -> StudyWordBudget.assessRendered("",
            Map.of("word_limit_mode", "ignore")));
        var malformed = new java.util.LinkedHashMap<String, Object>();
        malformed.put("word_limit_mode", null);
        assertThrows(java.io.IOException.class, () -> StudyWordBudget.assessRendered("", malformed));
    }
}
