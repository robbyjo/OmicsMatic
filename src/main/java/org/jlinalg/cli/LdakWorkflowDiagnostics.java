/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jlinalg.cli.LdakWorkflowRequest.Config;

/** Preserve original diagnostics, including upstream in-memory substitutions. */
final class LdakWorkflowDiagnostics {
    record Row(String stage, String stream, long line, String kind, String message) { }
    record Report(List<Row> rows, long warnings, long errors, long substitutionMessages,
            long zeroStatisticsReplaced, long taggingsFloored, long ambiguousIgnored) {
        Report { rows = List.copyOf(rows); }
        String table() {
            StringBuilder s = new StringBuilder("stage\tstream\tline\tkind\tmessage_base64url_utf8\n");
            var base64 = Base64.getUrlEncoder().withoutPadding();
            for (Row row : rows) s.append(row.stage()).append('\t').append(row.stream()).append('\t').append(row.line()).append('\t')
                .append(row.kind()).append('\t').append(base64.encodeToString(row.message().getBytes(StandardCharsets.UTF_8))).append('\n');
            return s.toString();
        }
        String warningsText(String notice) {
            StringBuilder text = new StringBuilder("# Observed original diagnostics and required review\n\n")
                .append("**Warnings: ").append(warnings).append("; errors: ").append(errors).append("; substitution messages: ").append(substitutionMessages)
                .append(".** Original zero test statistics replaced with 1e-6: **").append(zeroStatisticsReplaced)
                .append("**; tagging values floored at one: **").append(taggingsFloored).append("**; reported ambiguous predictors ignored: **").append(ambiguousIgnored).append("**.\n\n")
                .append("Read every original console and DIAGNOSTICS.tsv before dependent analysis. Substitutions are original program behavior, not proof that literal input values were used throughout fitting. ")
                .append("Warnings and partial files remain; no input is silently rewritten. The declared policy controls whether original substitutions are admitted.\n\n");
            for (Row row : rows) text.append("- ").append(row.stage()).append('/').append(row.stream()).append(':').append(row.line()).append(" [").append(row.kind()).append("] ")
                .append(row.message().replace("\r", "\\r").replace("\n", "\\n")).append('\n');
            return text.append("\n").append(notice).toString();
        }
    }
    private static final Pattern DIAGNOSTIC = Pattern.compile("^\\s*(Warning|Error|Fatal)(?:[, :]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZERO = Pattern.compile("^Warning, (\\d+) SNPs? have test statistic 0;.*replaced with 1e-6.*");
    private static final Pattern FLOOR = Pattern.compile("^Warning, (\\d+) SNPs? have tagging less than 1 .*replaced with 1.*");
    private static final Pattern AMBIGUOUS = Pattern.compile("^In total, (\\d+) predictors have ambiguous alleles.*");
    private LdakWorkflowDiagnostics() { }
    static Report collect(Path root, Config c) throws IOException {
        var rows = new ArrayList<Row>();
        long warnings = 0, errors = 0, substitutions = 0, zero = 0, floor = 0, ambiguous = 0;
        for (var stage : LdakWorkflowProcess.plan(c)) for (String stream : List.of("original.stdout", "original.stderr")) {
            Path path = root.resolve("stages").resolve(stage.name()).resolve(stream);
            if (!Files.isRegularFile(path)) continue;
            try (var lines = new LdakWorkflowFiles.Lines(path)) {
                long number = 0;
                for (String line; (line = lines.next()) != null;) {
                    number++;
                    var match = DIAGNOSTIC.matcher(line);
                    if (match.find()) {
                        String kind = match.group(1).toLowerCase(Locale.ROOT).equals("warning") ? "WARNING" : "ERROR";
                        if (kind.equals("WARNING")) warnings++; else errors++;
                        rows.add(new Row(stage.name(), stream, number, kind, line));
                        if (line.toLowerCase(Locale.ROOT).contains("replac")) substitutions++;
                    }
                    var m = ZERO.matcher(line); if (m.matches()) zero = Math.addExact(zero, Long.parseLong(m.group(1)));
                    m = FLOOR.matcher(line); if (m.matches()) floor = Math.addExact(floor, Long.parseLong(m.group(1)));
                    m = AMBIGUOUS.matcher(line);
                    if (m.matches()) {
                        ambiguous = Math.addExact(ambiguous, Long.parseLong(m.group(1)));
                        rows.add(new Row(stage.name(), stream, number, "AMBIGUOUS_COUNT", line));
                    }
                }
            }
        }
        return new Report(rows, warnings, errors, substitutions, zero, floor, ambiguous);
    }
    static void requireAdmissible(Config c, Report report) throws IOException {
        if (report.errors() != 0) throw new IOException("Original Error/Fatal diagnostics prevent completion, even with exit zero; inspect DIAGNOSTICS.tsv and consoles");
        if (c.get("upstream_adjustments").equals("forbid") && report.substitutionMessages() != 0)
            throw new IOException("Original substitutions occurred but upstream_adjustments=forbid. Retain this failed attempt; inspect the exact changes before choosing a NEW explicitly declared analysis");
    }
}
