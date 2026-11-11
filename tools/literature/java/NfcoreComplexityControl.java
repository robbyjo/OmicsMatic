/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.jlinalg.sequence.FastaIndexes;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;

/** Full single-library original fixture or declared derivative; no engine-bundle authenticity claim. */
public final class NfcoreComplexityControl {
    private NfcoreComplexityControl() { }
    public static void main(String[] args) throws Exception {
        try {
            Path root = Path.of(args[0]); String id = Files.readString(root.resolve("library.txt"), StandardCharsets.UTF_8).strip();
            var mapping = NfcoreChipseq121Mappings.read(root.resolve("design_reads.csv"), root.resolve("design_controls.csv"), NfcoreChipseq121Mappings.Limits.defaults());
            var library = mapping.libraries().stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
            var settings = new java.util.Properties();
            if (Files.exists(root.resolve("case.properties"))) try (var input = Files.newBufferedReader(root.resolve("case.properties"), StandardCharsets.UTF_8)) { settings.load(input); }
            if (!java.util.Set.of("true", "false").contains(settings.getProperty("preseq-requested", "true"))) throw new IllegalArgumentException("Declare original curve branch explicitly");
            var lengths = new LinkedHashMap<String, Long>(); for (var entry : FastaIndexes.build(root.resolve("genome.fa"))) lengths.put(entry.getContig(), entry.getSize());
            var report = NfcoreChipseq121Complexity.audit(root.resolve("marked.bam"), root.resolve("metrics.txt"), settings.getProperty("preseq-requested", "true").equals("true") ? root.resolve("curve.txt") : null, library, lengths);
            NextflowFiles.writeJson(Path.of(args[1]), report); System.out.println("PASS_COMPLETE_COMPLEXITY_COMPONENT_POPULATION");
        } catch (Exception failed) {
            System.err.println("FAIL2 NO COMPLEXITY COMPONENT RESULT: " + failed + "\nRequired action: retain complete original BAM, metric and curve inputs and failed diagnostics; inspect the named source discrepancy, correct the cause and retry a NEW path. No counter, confidence bound or missing estimate is repaired."); System.exit(2);
        }
    }
}
