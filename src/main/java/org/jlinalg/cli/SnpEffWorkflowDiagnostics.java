/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.jlinalg.genetics.SnpEffAnnotations;

/** Surface original diagnostic counts without collapsing or reclassifying source tokens. */
final class SnpEffWorkflowDiagnostics {
    private SnpEffWorkflowDiagnostics() { }
    static void write(Path out, SnpEffAnnotations.Report report, String notice) throws IOException {
        Map<String,Long> counts = new TreeMap<>(); long rows = 0;
        try (var reader = Files.newBufferedReader(out.resolve("diagnostics.tsv"), StandardCharsets.UTF_8)) {
            if (reader.readLine() == null) throw new IOException("Native diagnostic table has no header");
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 4 || fields[3].isEmpty()) throw new IOException("Native diagnostic table contains a malformed row");
                counts.merge(fields[3], 1L, Math::addExact); rows++;
            }
        }
        if (rows != report.diagnosticRows()) throw new IOException("Observed diagnostic counts contradict the native audit");
        StringBuilder tsv = new StringBuilder("original_diagnostic_literal\tannotation_rows\n");
        StringBuilder warning = new StringBuilder("# Observed original annotation diagnostics\n\n");
        warning.append("**").append(rows).append(" annotation rows contain original diagnostics; ").append(report.errorRows()).append(" contain ERROR tokens.**\n\n");
        warning.append("Counts are per annotation row; a variant can contribute multiple transcripts. Combined diagnostic strings remain combined. Information messages and warnings keep their original names.\n\n");
        if (!counts.isEmpty()) warning.append("| Original diagnostic literal | Annotation rows |\n|---|---:|\n");
        for (Map.Entry<String,Long> entry : counts.entrySet()) {
            tsv.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
            String safe = entry.getKey().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("|", "&#124;").replace("`", "&#96;");
            warning.append("| ").append(safe).append(" | ").append(entry.getValue()).append(" |\n");
        }
        warning.append("\nRead `diagnostics.tsv` and `original.stderr`, and review each original diagnostic before downstream interpretation. See the [upstream diagnostic definitions](https://pcingola.github.io/SnpEff/snpeff/inputoutput/#errors-and-warnings). Check the declared reference and transcript models when a transcript warning matters to your analysis; retain the original output and document any later reference change in a new run. ERROR tokens prevent completion. These predictions do not establish pathogenicity or paper reproduction.\n\n");
        warning.append(notice);
        Files.writeString(out.resolve("SOURCE-DIAGNOSTICS.tsv"), tsv, StandardCharsets.UTF_8);
        Files.writeString(out.resolve("WARNINGS.md"), warning, StandardCharsets.UTF_8);
    }
}
