/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Abstract assembly and review; missing scientific content is never synthesized locally. */
final class StudyAbstract {
    static final List<String> ROLES = List.of("background", "methods", "results", "conclusion");
    private static final Pattern LABEL = Pattern.compile("(?im)^[\\t ]*(?:#{1,6}[\\t ]*)?(Background|Methods?|Results?|Conclusions?)[\\t ]*(?::[\\t ]*|\\r?$)");
    private static final Pattern HEADING = Pattern.compile("(?im)^[\\t ]*(?:#{1,6}[\\t ]*)?Abstract[\\t ]*:?\\r?$");
    private static final Pattern END = Pattern.compile("(?im)^[\\t ]*(?:#{1,6}[\\t ]*)?(Introduction|References|Data and (?:code|program) availability|Supplementary[^\\r\\n]*|Abstract)[\\t ]*:?\\r?$");
    private StudyAbstract() { }

    static List<Object> template() {
        List<Object> result = new ArrayList<>();
        var tasks = List.of("State the biological problem, gap and objective from the finished manuscript.",
            "Summarize the final design, population, verified analysis N and main approach.",
            "Summarize the final principal quantitative findings and material replication or null results.",
            "State the supported contribution and a grounded, forward-looking implication.");
        for (int i = 0; i < ROLES.size(); i++) result.add(Map.of("id", "abstract-" + ROLES.get(i), "section", "abstract",
            "abstract_role", ROLES.get(i), "text", "[SCIENTIST INPUT REQUIRED: " + tasks.get(i) + "]",
            "sources", List.of(), "rationale", "Write the abstract after finalizing the manuscript body."));
        return result;
    }

    static List<Map<String, Object>> blocks(List<?> sections) {
        return sections.stream().map(StudyPublicEvidence::map).filter(p -> "abstract".equals(p.get("section"))).toList();
    }

    static void validateRoles(List<?> sections) throws IOException {
        for (Object raw : sections) {
            var p = map(raw);
            if (p.containsKey("abstract_role") && (!"abstract".equals(p.get("section")) || !ROLES.contains(text(p.get("abstract_role")))))
                throw new IOException("abstract_role requires section abstract and one of background, methods, results, conclusion");
        }
    }

    static String labeledText(List<?> sections) {
        return String.join("\n\n", blocks(sections).stream().map(p ->
            (p.containsKey("abstract_role") ? label(text(p.get("abstract_role"))) + ": " : "") + text(p.get("text"))).toList());
    }

    static Map<String, Object> review(String abstractText) {
        List<String> needs = new ArrayList<>(), roles = new ArrayList<>();
        var labels = LABEL.matcher(abstractText); int contentStart = -1;
        while (labels.find()) {
            if (contentStart >= 0 && abstractText.substring(contentStart, labels.start()).isBlank()) needs.add("Supply content for " + roles.get(roles.size() - 1) + ".");
            String role = labels.group(1).toLowerCase(java.util.Locale.ROOT);
            role = switch (role) { case "method" -> "methods"; case "result" -> "results"; case "conclusions" -> "conclusion"; default -> role; };
            roles.add(role); contentStart = labels.end();
        }
        if (contentStart >= 0 && abstractText.substring(contentStart).isBlank()) needs.add("Supply content for " + roles.get(roles.size() - 1) + ".");
        if (!roles.equals(ROLES)) needs.add("Include exactly Background, Methods, Results and Conclusion, in that order.");
        if (abstractText.isBlank()) needs.add("Write the structured abstract after finalizing the manuscript body, then place it before Introduction.");
        if (abstractText.contains("[SCIENTIST INPUT REQUIRED") || abstractText.contains("[VERIFY]")) needs.add("Resolve missing abstract inputs against the final manuscript.");
        return new LinkedHashMap<>(Map.of("status", needs.isEmpty() ? "structure_complete_review_required" : "needs_input", "needs", needs,
            "roles", roles, "checks", List.of("Reconcile every abstract count, estimate and conclusion with the final manuscript.",
                "Confirm the abstract was written or updated after the body; ordering and authorship chronology cannot be inferred from formatting.",
                "Keep the implication constructive, grounded and free of internal audit language.")));
    }

    static Map<String, Object> reviewRevision(List<?> sections, String manuscript, String previousManuscript) {
        var result = review(labeledText(sections));
        String bodyHash = hash(mainBody(manuscript));
        boolean stale = previousManuscript != null && !bodyHash.equals(hash(mainBody(previousManuscript)))
            && abstractHtml(manuscript).equals(abstractHtml(previousManuscript)) && !blocks(sections).isEmpty();
        result.put("main_text_sha256", bodyHash); result.put("body_changed_without_abstract_update", stale);
        if (stale) {
            result.put("status", "needs_input");
            var needs = new ArrayList<Object>(list(result.get("needs"))); needs.add("The body changed while the abstract stayed unchanged; reconcile the abstract with this revision."); result.put("needs", needs);
        }
        return result;
    }

    private static String mainBody(String html) {
        var matches = Pattern.compile("(?s)<div data-manuscript-section=\"[a-z]+\">.*?<!-- end-main-text:[a-z]+ -->").matcher(html);
        return String.join("\n", matches.results().map(java.util.regex.MatchResult::group).toList());
    }
    private static String abstractHtml(String html) {
        var match = Pattern.compile("(?s)<div data-manuscript-abstract=\"true\">(.*?)</div>").matcher(html);
        return match.find() ? match.group(1) : "";
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    record Draft(String body, String abstractText, int abstractCount) { }
    static Draft splitDraft(String answer) {
        var headings = HEADING.matcher(answer); var body = new StringBuilder(); List<String> abstracts = new ArrayList<>(); int cursor = 0;
        while (headings.find()) {
            if (headings.start() < cursor) continue;
            var end = END.matcher(answer); int stop = end.find(headings.end()) ? end.start() : answer.length();
            body.append(answer, cursor, headings.start()); abstracts.add(answer.substring(headings.end(), stop).strip()); cursor = stop;
        }
        body.append(answer.substring(cursor));
        return new Draft(body.toString().strip(), String.join("\n\n", abstracts), abstracts.size());
    }
    static String label(String role) { return Character.toUpperCase(role.charAt(0)) + role.substring(1); }
}
