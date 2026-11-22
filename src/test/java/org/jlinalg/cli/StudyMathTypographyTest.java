package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyMathTypographyTest {
    @Test void proseAndMathPrecisionAreReviewedWithoutChangingEvidenceOrExactCutoffs() {
        String source = "Scale 1.482602218505602; \\(c=1.348979500392164\\); P < 0.00000005; "
            + "`1.234567890` https://example.org/1.234567890 ENST000001.123456789 10.123456789/doi v1.234567.8";
        var review = StudyMathTypography.review(List.of(Map.of("id", "methods", "text", source)));
        String cues = review.get("cues").toString();
        assertTrue(cues.contains("1.482602218505602"));
        assertTrue(cues.contains("1.348979500392164"));
        assertTrue(cues.contains("Preserve exact thresholds"));
        assertFalse(cues.contains("1.234567890"));
        assertFalse(cues.contains("0.00000005"));
        assertFalse(cues.contains("123456789"));
        assertEquals(1, review.get("equation_count"));
        assertTrue(StudyMathTypography.prose(source).contains("1.482602218505602"));
        assertTrue(list(StudyMathTypography.review(List.of(Map.of("text",
            "Approximately 1.48; r=0.363; P < 0.00000005; \\(P=3.61\\times10^{-52}\\)."))).get("cues")).isEmpty());
        assertEquals(List.of("8.012839414774448E-21"), StudyReportNumbers.excessiveDecimals("P=8.012839414774448E-21"));
    }

    @Test void inclusiveOperatorsChangeOnlyPresentationAndCodeAndUrlsArePreserved() {
        String source = "P <= 0.05; N >= 10; x < 2; y > 3. `x<=y` https://example.org/?x<=3 <script>bad()</script>";
        String html = StudyMathTypography.prose(source);
        assertTrue(html.contains("P ≤ 0.05; N ≥ 10; x &lt; 2; y &gt; 3."));
        assertTrue(html.contains("`x&lt;=y` https://example.org/?x&lt;=3"));
        assertTrue(html.contains("&lt;script&gt;bad()&lt;/script&gt;"));
        assertFalse(html.contains("<script>"));
        assertTrue(source.contains("P <= 0.05"));
    }

    @Test void explicitEquationsRetainExactTexAndCurrencyIsNotMath() throws Exception {
        String formula = "\\hat{\\beta}=\\frac{\\sum_{i=1}^{K}w_i\\hat{\\beta}_i}{\\sum_{i=1}^{K}w_i}";
        String html = new StudyCitations(Map.of()).render("For $5 and $10, \\(r^2\\) and \\[" + formula + "\\] or $$x^2$$.", List.of());
        assertTrue(html.contains("For $5 and $10"));
        assertEquals(3, html.split("data-tex=", -1).length - 1);
        assertTrue(html.contains("data-tex=\"" + formula + "\""));
        assertTrue(html.contains("data-display=\"true\""));
        assertTrue(html.contains("data-display=\"false\""));
    }

    @Test void ambiguousPseudocodeAndBrokenDelimitersNeedReviewWithoutInventingAnEquation() {
        String source = "sum(w beta)/sum(w), sqrt(1/sum(w)), \\(x and N >= 10";
        var sections = List.of(Map.of("id", "methods", "text", source));
        var review = StudyMathTypography.review(sections);
        assertEquals(0, review.get("equation_count"));
        assertTrue(review.toString().contains("Unmatched math delimiter"));
        assertTrue(review.toString().contains("do not guess an ambiguous formula"));
        assertFalse(StudyMathTypography.prose(source).contains("data-tex"));
        assertEquals(source, sections.get(0).get("text"));
        assertTrue(list(StudyMathTypography.review(List.of(Map.of("text", "`sum(x)` https://example.org/sum(x)"))).get("cues")).isEmpty());
    }

    @Test void nestedTexGroupsAreNotConfusedWithUnresolvedEvidenceBindings() {
        assertFalse(StudyMathTypography.hasUnresolvedBinding("\\[\\frac{\\sum_i w_i}{{x}}\\]"));
        assertTrue(StudyMathTypography.hasUnresolvedBinding("Unresolved {{H#/count}}"));
        assertTrue(StudyMathTypography.hasUnresolvedBinding("Broken {{H#/count"));
        assertTrue(StudyMathTypography.hasUnresolvedBinding("Stray }}"));
        assertTrue(StudyMathTypography.hasUnresolvedBinding("\\(x + {{H#/count}\\)"));
    }

    @Test void offlineAssetsAreAddedOnceAndAbbreviationsDoNotAlterEquationsOrScripts() {
        String html = "<html><body>" + StudyMathTypography.prose("\\(\\mathrm{SE}(x)\\) SE") + "</body></html>";
        String expanded = StudyPublication.expandAbbreviations(html, Map.of());
        assertTrue(expanded.contains("data-tex=\"\\mathrm{SE}(x)\""));
        assertTrue(expanded.contains("\\(\\mathrm{SE}(x)\\)</span> standard error (SE)"));
        assertEquals(1, expanded.split("id=\"manuscript-math-renderer\"", -1).length - 1);
        assertFalse(expanded.contains("<script src="));
        assertEquals(expanded, StudyMathTypography.document(expanded));
        assertEquals(expanded, StudyPublication.expandAbbreviations(expanded, Map.of()));
    }

    @Test void aiDraftEquationsAreEscapedBeforeTrustedRenderingAndReviewed() {
        String source = "Results\nP <= 0.05. \\[\\frac{1}{\\sum_i w_i}\\]\n<script>alert(1)</script>";
        String html = StudyAssistance.manuscript(source, List.of());
        assertTrue(html.contains("P ≤ 0.05"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("id=\"manuscript-math-renderer\""));
        var review = map(StudyManuscriptWriting.assessDraft(source, List.of()).get("mathematics_review"));
        assertEquals(1, review.get("equation_count"));
        assertEquals("typesetting_review_required", review.get("status"));
    }
}
