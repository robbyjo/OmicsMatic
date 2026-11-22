/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Presentation-only notation; original authored text and numerical evidence remain unchanged. */
final class StudyMathTypography {
    // Explicit math delimiters avoid interpreting currency as an equation. Protect code and URLs.
    private static final Pattern TOKENS = Pattern.compile(
        "(?s)```.*?```|`[^`\\r\\n]+`|https?://[^\\s]+|\\\\\\((.*?)\\\\\\)|\\\\\\[(.*?)\\\\\\]|\\$\\$(.*?)\\$\\$");
    private static final Pattern PSEUDOCODE = Pattern.compile("(?i)\\b(?:sum|sqrt)\\s*\\(");
    private StudyMathTypography() { }

    static String prose(String source) {
        var tokens = TOKENS.matcher(source); StringBuilder html = new StringBuilder(); int offset = 0;
        while (tokens.find()) {
            html.append(escape(symbols(source.substring(offset, tokens.start()))));
            String tex = tokens.group(1) != null ? tokens.group(1) : tokens.group(2) != null ? tokens.group(2) : tokens.group(3);
            if (tex == null) html.append(escape(tokens.group()));
            else {
                boolean display = tokens.group(1) == null;
                html.append("<span class=\"manuscript-math")
                    .append(display ? " manuscript-math-display" : "")
                    .append("\" data-math-status=\"pending\" data-display=\"").append(display)
                    .append("\" data-tex=\"").append(escape(symbols(tex)))
                    .append("\">").append(escape(tokens.group())).append("</span>");
            }
            offset = tokens.end();
        }
        return html.append(escape(symbols(source.substring(offset)))).toString();
    }

    private static String symbols(String value) { return value.replace("<=", "≤").replace(">=", "≥"); }
    static boolean hasUnresolvedBinding(String source) {
        // Nested TeX groups legitimately contain {{ or }}. Evidence bindings still
        // resolve before typesetting, including bindings inside an equation.
        var tokens = TOKENS.matcher(source); StringBuilder outside = new StringBuilder(); int offset = 0;
        while (tokens.find()) {
            outside.append(source, offset, tokens.start());
            if (tokens.group(1) == null && tokens.group(2) == null && tokens.group(3) == null) outside.append(tokens.group());
            offset = tokens.end();
        }
        outside.append(source.substring(offset));
        return outside.indexOf("{{") >= 0 || outside.indexOf("}}") >= 0
            || Pattern.compile("\\{\\{[^{}\\r\\n]*#").matcher(source).find();
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static Map<String, Object> review(List<?> sections) {
        List<Object> cues = new ArrayList<>(); int equations = 0;
        for (Object raw : sections) {
            var paragraph = StudyPublicEvidence.map(raw);
            String text = StudyPublicEvidence.text(paragraph.get("text"));
            var tokens = TOKENS.matcher(text); StringBuilder plain = new StringBuilder(), numerical = new StringBuilder(); int offset = 0;
            while (tokens.find()) {
                plain.append(text, offset, tokens.start());
                numerical.append(text, offset, tokens.start()).append(' ');
                String tex = tokens.group(1) != null ? tokens.group(1) : tokens.group(2) != null ? tokens.group(2) : tokens.group(3);
                if (tex != null) { equations++; numerical.append(tex).append(' '); }
                offset = tokens.end();
            }
            plain.append(text.substring(offset)); String prose = plain.toString();
            numerical.append(text.substring(offset));
            List<String> needs = new ArrayList<>();
            var decimals = StudyReportNumbers.excessiveDecimals(numerical.toString());
            if (!decimals.isEmpty()) needs.add("Review excessive decimal precision: " + String.join(", ", decimals)
                + ". Use concise display precision (normally two decimal places; three significant figures for scientific notation). Label rounded method constants as approximate. Preserve exact thresholds and full-precision source values; do not round indiscriminately.");
            if (prose.contains("<=") || prose.contains(">=")) needs.add("ASCII comparison operators render as ≤ and ≥; verify the intended inclusive threshold.");
            if (PSEUDOCODE.matcher(prose).find()) needs.add("Replace formula pseudocode with explicit TeX math, preserving the estimator, indices and denominator; do not guess an ambiguous formula.");
            if (prose.contains("\\(") || prose.contains("\\)") || prose.contains("\\[") || prose.contains("\\]") || prose.contains("$$"))
                needs.add("Unmatched math delimiter: complete the equation before export.");
            if (!needs.isEmpty()) cues.add(Map.of("paragraph_id", paragraph.getOrDefault("id", "unclassified"), "needs", needs));
        }
        return Map.of("status", "typesetting_review_required", "equation_count", equations, "cues", cues,
            "renderer", "Bundled KaTeX 0.19.0 to native MathML; no remote scripts or fonts.",
            "review", "Check symbols, subscripts, superscripts, fractions, sums, units and defined indices against the source methods. Inspect the rendered HTML/PDF and require every equation's data-math-status to be complete. Formatting does not validate mathematical correctness.");
    }

    static String document(String html) {
        if (!html.contains("data-tex=\"") || html.contains("id=\"manuscript-math-renderer\"")) return html;
        String assets = "<style>"
            + ".manuscript-math{white-space:normal;overflow-wrap:normal}"
            + ".manuscript-math-display{display:block;text-align:center;margin:1em 0;break-inside:avoid;overflow-x:auto}"
            + ".manuscript-math math{font-family:'Cambria Math','STIX Two Math',math}"
            + ".manuscript-math[data-math-status=error]{color:#9b1c1c;border-bottom:2px solid;white-space:pre-wrap}"
            + "@media print{.manuscript-math-display{overflow:visible}}"
            + "</style><script id=\"manuscript-math-renderer\">/*\n" + Assets.LICENSE + "\n*/\n"
            + Assets.KATEX + "\n</script><script>" + Assets.RENDER + "</script>";
        int end = html.lastIndexOf("</body>");
        if (end < 0) end = html.lastIndexOf("</html>");
        if (end < 0) end = html.length();
        return html.substring(0, end) + assets + html.substring(end);
    }

    private static final class Assets {
        static final String KATEX = load("katex/katex.min.js");
        static final String LICENSE = load("katex/LICENSE");
        static final String RENDER = load("render.js");
        private static String load(String name) {
            try (var input = StudyMathTypography.class.getResourceAsStream("/org/jlinalg/study/math/" + name)) {
                if (input == null) throw new IllegalStateException("Missing manuscript math resource: " + name);
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
        }
    }
}
