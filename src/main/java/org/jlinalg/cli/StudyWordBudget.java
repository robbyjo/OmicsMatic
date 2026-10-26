/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.jlinalg.study.StudyWorkflowPolicy;

/** Deterministic, explicitly scoped manuscript main-text word accounting. */
final class StudyWordBudget {
    private StudyWordBudget() { }
    private static final Set<String> MAIN = Set.of("introduction", "methods", "results", "discussion", "conclusion");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.-][\\p{L}\\p{N}]+)*");

    static Map<String, Object> assess(Map<String, Object> revision) throws IOException {
        Map<String, Object> policy;
        try { policy = StudyWorkflowPolicy.manuscript(map(revision.get("manuscript_policy"))); }
        catch (IllegalArgumentException failure) { throw new IOException(failure.getMessage(), failure); }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Object raw : list(revision.get("sections"))) {
            Map<String, Object> paragraph = map(raw);
            String section = text(paragraph.get("section"));
            if (!MAIN.contains(section)) continue;
            if (!counts.containsKey(section)) counts.put(section, count(section));
            counts.merge(section, count(text(paragraph.get("title"))) + count(text(paragraph.get("text"))), Integer::sum);
        }
        return report(counts, policy, "structured paragraph text before rendering");
    }

    static Map<String, Object> assessRendered(String html, Map<String, Object> configured) throws IOException {
        Map<String, Object> policy;
        try { policy = StudyWorkflowPolicy.manuscript(configured); }
        catch (IllegalArgumentException failure) { throw new IOException(failure.getMessage(), failure); }
        Map<String, Integer> counts = new LinkedHashMap<>();
        var sections = Pattern.compile("(?s)<div data-manuscript-section=\"([a-z]+)\">(.*?)<!-- end-main-text:\\1 -->").matcher(html);
        while (sections.find()) {
            String section = sections.group(1);
            if (!MAIN.contains(section)) throw new IOException("Unknown counted manuscript section: " + section);
            String fragment = excludeSupplemental(sections.group(2));
            StringBuilder plain = new StringBuilder();
            new ParserDelegator().parse(new StringReader(fragment), new HTMLEditorKit.ParserCallback() {
                @Override public void handleText(char[] data, int position) { plain.append(data).append(' '); }
                @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) { plain.append(' '); }
            }, true);
            counts.merge(section, count(plain.toString()), Integer::sum);
        }
        if (counts.isEmpty()) throw new IOException("Rendered manuscript lacks counted main-text section boundaries");
        return report(counts, policy, "final rendered main text after numeric substitutions, citations and abbreviation expansion");
    }

    private static String excludeSupplemental(String html) {
        var tags = Pattern.compile("(?is)<(/?)(details|table|aside|script|style|nav)\\b[^>]*>").matcher(html);
        ArrayDeque<String> hidden = new ArrayDeque<>();
        StringBuilder visible = new StringBuilder(); int cursor = 0;
        while (tags.find()) {
            if (hidden.isEmpty()) visible.append(html, cursor, tags.start());
            String tag = tags.group(2).toLowerCase(java.util.Locale.ROOT);
            if (tags.group(1).isEmpty()) hidden.push(tag);
            else if (hidden.contains(tag)) { while (!hidden.pop().equals(tag)) { /* Skip nested excluded elements. */ } }
            cursor = tags.end();
        }
        if (hidden.isEmpty()) visible.append(html.substring(cursor));
        return visible.toString();
    }

    static Map<String, Object> assessSupplementRendered(String html) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        var sections = Pattern.compile("(?s)<section data-manuscript-supplement=\"([^\"]+)\">(.*?)<!-- end-supplement:\\1 -->").matcher(html);
        while (sections.find()) {
            StringBuilder plain = new StringBuilder();
            new ParserDelegator().parse(new StringReader(excludeSupplemental(sections.group(2))), new HTMLEditorKit.ParserCallback() {
                @Override public void handleText(char[] data, int position) { plain.append(data).append(' '); }
                @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) { plain.append(' '); }
            }, true);
            counts.put(sections.group(1), count(plain.toString()));
        }
        return Map.of("supplement_words", counts.values().stream().mapToInt(Integer::intValue).sum(),
            "section_words", counts, "length_ceiling", "none",
            "scope", "Authored supplementary section headings, prose and inline citations after value and abbreviation expansion; generated evidence previews, tables and the reference list excluded.");
    }

    private static Map<String, Object> report(Map<String, Integer> counts, Map<String, Object> policy, String stage) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int maximum = ((Number) policy.get("max_main_words")).intValue();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("policy", policy);
        result.put("main_text_words", total);
        result.put("section_words", counts);
        result.put("within_limit", total <= maximum);
        boolean strict = "strict".equals(policy.get("word_limit_mode"));
        result.put("limit_enforced", strict);
        result.put("blocks_publication", strict && total > maximum);
        result.put("disposition", total <= maximum ? "within_main_text_target"
            : strict ? "strict_main_text_limit_exceeded" : "over_main_text_target_review_supplements");
        result.put("supplement_policy", "Retain complete evidence and extended finding discussions in separately labeled, linked supplements. Exceeding a planning target requires editorial review, not truncation or deletion of evidence; declared strict main-text limits remain enforced.");
        result.put("counted_stage", stage);
        result.put("scope", "Introduction, Methods, Results, Discussion and Conclusion including headings, paragraph titles and inline citations. Title page, abstract, references, tables, legends and supplements excluded.");
        result.put("counting_method", "Unicode letter/number tokens; internal apostrophes, periods and hyphens join tokens. Unresolved templates are counted literally and are not publication-ready.");
        return result;
    }

    static int count(String text) {
        return Math.toIntExact(WORD.matcher(text).results().count());
    }

    static Map<String, Object> draft(String text, Map<String, Object> policy) {
        var headings = Pattern.compile("(?im)^(?:#{1,6}\\s*)?(Introduction|Materials and Methods|Methods|Results|Discussion|Conclusions?|References|Abstract|Supplementary.*)\\s*[:#]*\\s*$").matcher(text);
        Map<String, Integer> counts = new LinkedHashMap<>(); String previous = ""; int start = 0;
        int supplementaryWords = 0;
        while (headings.find()) {
            if (Set.of("introduction", "methods", "results", "discussion", "conclusion").contains(previous))
                counts.merge(previous, count(text.substring(start, headings.start()).replaceAll("(?m)^\\|.*$", "")), Integer::sum);
            if (previous.startsWith("supplementary")) supplementaryWords += count(text.substring(start, headings.start()).replaceAll("(?m)^\\|.*$", ""));
            previous = headings.group(1).toLowerCase(java.util.Locale.ROOT);
            if (previous.equals("materials and methods")) previous = "methods"; if (previous.equals("conclusions")) previous = "conclusion";
            if (Set.of("introduction", "methods", "results", "discussion", "conclusion").contains(previous)) counts.merge(previous, count(headings.group(1)), Integer::sum);
            start = headings.end();
        }
        if (Set.of("introduction", "methods", "results", "discussion", "conclusion").contains(previous)) counts.merge(previous, count(text.substring(start).replaceAll("(?m)^\\|.*$", "")), Integer::sum);
        if (previous.startsWith("supplementary")) supplementaryWords += count(text.substring(start).replaceAll("(?m)^\\|.*$", ""));
        if (counts.isEmpty()) throw new IllegalArgumentException("AI manuscript needs recognizable standalone main-section headings for word accounting");
        var result = report(counts, StudyWorkflowPolicy.manuscript(policy), "AI plain-text draft sections; native rendered revision is counted again after bindings/citations/abbreviations");
        result.put("supplement_words", supplementaryWords);
        result.put("supplement_counting_scope", "Prose under standalone Supplementary headings; pipe-table rows, headings and separately headed references excluded. No supplement length ceiling is imposed.");
        return result;
    }
}
