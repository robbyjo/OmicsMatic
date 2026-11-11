/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jlinalg.workflow.NfcoreChipseq121Consensus;

/** Run the native full-population audit on exact original output fixtures or declared mutations. */
public final class NfcoreDeseq126Control {
    private NfcoreDeseq126Control() { }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        try {
            var settings = new java.util.Properties();
            if (Files.exists(root.resolve("case.properties"))) try (var input = Files.newBufferedReader(root.resolve("case.properties"), StandardCharsets.UTF_8)) { settings.load(input); }
            var libraries = Files.readAllLines(root.resolve("libraries.txt"), StandardCharsets.UTF_8);
            var inputs = new ArrayList<NfcoreChipseq121Consensus.PeakSet>();
            for (String sample : libraries) inputs.add(new NfcoreChipseq121Consensus.PeakSet(sample, NfcoreChipseq121Consensus.read(root.resolve("peaks/" + sample + "_peaks.broadPeak"), NfcoreChipseq121Consensus.Kind.BROAD, NfcoreChipseq121Consensus.Limits.defaults())));
            var plan = NfcoreChipseq121Consensus.compute(inputs, NfcoreChipseq121Consensus.Kind.BROAD, 1, NfcoreChipseq121Consensus.Limits.defaults());
            var report = NfcoreDeseq126Audit.audit(root.resolve("featureCounts.txt"), plan.rows(), libraries, settings.getProperty("bam_suffix", ".mLb.clN.bam"), root.resolve("worker"),
                settings.getProperty("published_directory", "bwa/mergedLibrary/macs/broadPeak/consensus/SPT5/deseq2"), settings.getProperty("model_prefix", "SPT5.consensus_peaks"), name -> root.resolve("published").resolve(NextflowFiles.member(name)));
            if (!Integer.valueOf(settings.getProperty("genes", "1472")).equals(report.get("complete_genes"))) throw new AssertionError("Original complete gene population differs");
            NextflowFiles.writeJson(Path.of(args[1]), report);
            System.out.println("PASS_COMPLETE_ORIGINAL_DESEQ126_MODEL_OUTPUT_AUDIT");
        } catch (java.io.IOException invalid) {
            System.err.println("FAIL2 NO MODEL COMPONENT RESULT: " + invalid + "\nRequired action: retain complete originals and the failed audit, inspect the named identity/model/contrast/FDR discrepancy and use a new path after correction. No value, identifier, filter alpha or coordinate is repaired.");
            System.exit(2);
        }
    }
}
