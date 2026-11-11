/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Compare native computation to original sort/mergeBed/Python/awk outputs, not Java-created expected tables. */
public final class NfcoreConsensusControl {
    private NfcoreConsensusControl() { }
    private static int checks;
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void refused(Checked test) throws Exception {
        try { test.run(); } catch (IOException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Invalid consensus input/output admitted");
    }
    private static void require(boolean value) { if (!value) throw new AssertionError("Native consensus check " + checks); checks++; }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--portable")) {
            var limits = NfcoreChipseq121Consensus.Limits.defaults();
            String peak = "chr1\t10\t20\tA_R1_peak_1\t20\t.\t-0.0\t2e-4\t3\n";
            var p = NfcoreChipseq121Consensus.read(new StringReader(peak), NfcoreChipseq121Consensus.Kind.BROAD, limits);
            require(Double.doubleToRawLongBits(p.get(0).fold()) == Double.doubleToRawLongBits(-0.0));
            var plan = NfcoreChipseq121Consensus.compute(List.of(new NfcoreChipseq121Consensus.PeakSet("A_R1", p)), NfcoreChipseq121Consensus.Kind.BROAD, 1, limits);
            require(plan.rows().size() == 1 && plan.rows().get(0).start() == 10 && plan.rows().get(0).end() == 20);
            require(NfcoreChipseq121Consensus.compute(List.of(new NfcoreChipseq121Consensus.PeakSet("A_R1", p)), NfcoreChipseq121Consensus.Kind.BROAD, 2, limits).rows().isEmpty());
            refused(() -> NfcoreChipseq121Consensus.compute(List.of(new NfcoreChipseq121Consensus.PeakSet("A_R1", p)), NfcoreChipseq121Consensus.Kind.BROAD, 0, limits));
            refused(() -> new NfcoreChipseq121Consensus.PeakSet("B_R1", p));
            refused(() -> NfcoreChipseq121Consensus.read(new StringReader(peak.replace("-0.0", "NaN")), NfcoreChipseq121Consensus.Kind.BROAD, limits));
            refused(() -> NfcoreChipseq121Consensus.read(new StringReader(peak + peak), NfcoreChipseq121Consensus.Kind.BROAD, new NfcoreChipseq121Consensus.Limits(1, 10, 1000, 10000)));
            refused(() -> NfcoreChipseq121Consensus.read(new StringReader(peak), NfcoreChipseq121Consensus.Kind.BROAD, new NfcoreChipseq121Consensus.Limits(10, 10, 5, 10000)));
            refused(() -> NfcoreChipseq121Consensus.read(new StringReader(peak), NfcoreChipseq121Consensus.Kind.BROAD, new NfcoreChipseq121Consensus.Limits(10, 10, 1000, 5)));
            refused(() -> NfcoreChipseq121Consensus.read(new StringReader(peak.replace("10\t20", "10\t10")), NfcoreChipseq121Consensus.Kind.BROAD, limits));
            refused(() -> NfcoreChipseq121Consensus.compute(List.of(new NfcoreChipseq121Consensus.PeakSet("A_R1", p), new NfcoreChipseq121Consensus.PeakSet("A_R1", p)), NfcoreChipseq121Consensus.Kind.BROAD, 1, limits));
            refused(() -> NfcoreChipseq121Consensus.compute(List.of(new NfcoreChipseq121Consensus.PeakSet("A_R1", p)), NfcoreChipseq121Consensus.Kind.NARROW, 1, limits));
            System.out.println("PASS " + checks + " portable native consensus checks"); return;
        }
        try {
            Path directory = Path.of(args[0]); var kind = NfcoreChipseq121Consensus.Kind.valueOf(args[1]); int minimum = Integer.parseInt(args[2]);
            var limits = NfcoreChipseq121Consensus.Limits.defaults(); var sets = new ArrayList<NfcoreChipseq121Consensus.PeakSet>();
            for (String row : Files.readAllLines(directory.resolve("samples.tsv"), StandardCharsets.UTF_8)) {
                String[] fields = row.split("\t", -1);
                sets.add(new NfcoreChipseq121Consensus.PeakSet(fields[0], NfcoreChipseq121Consensus.read(directory.resolve(fields[1]), kind, limits)));
            }
            var plan = NfcoreChipseq121Consensus.compute(sets, kind, minimum, limits);
            try (var b = Files.newBufferedReader(directory.resolve("original.boolean.txt"), StandardCharsets.UTF_8);
                 var bed = Files.newBufferedReader(directory.resolve("original.bed"), StandardCharsets.UTF_8);
                 var saf = Files.newBufferedReader(directory.resolve("original.saf"), StandardCharsets.UTF_8);
                 var combinations = Files.newBufferedReader(directory.resolve("original.boolean.intersect.txt"), StandardCharsets.UTF_8)) {
                NfcoreChipseq121Consensus.auditBoolean(b, plan, limits);
                NfcoreChipseq121Consensus.auditIntervals(bed, saf, plan, limits);
                NfcoreChipseq121Consensus.auditIntersections(combinations, plan, limits);
            }
            // Meaningful late-row corruption: geometry and complete membership have to match throughout.
            String original = Files.readString(directory.resolve("original.boolean.txt"), StandardCharsets.UTF_8);
            if (!plan.rows().isEmpty()) {
                String id = plan.rows().get(plan.rows().size() - 1).id();
                refused(() -> NfcoreChipseq121Consensus.auditBoolean(new StringReader(original.replace("\t" + id + "\t", "\tCORRUPTED_INTERVAL\t")), plan, limits));
                refused(() -> NfcoreChipseq121Consensus.auditBoolean(new StringReader(original + "EXTRA\n"), plan, limits));
                String saf = Files.readString(directory.resolve("original.saf"), StandardCharsets.UTF_8);
                String bed = Files.readString(directory.resolve("original.bed"), StandardCharsets.UTF_8);
                var row = plan.rows().get(0);
                String repaired = saf.replace(row.id() + "\t" + row.contig() + "\t" + row.start() + "\t", row.id() + "\t" + row.contig() + "\t" + (row.start() + 1) + "\t");
                refused(() -> NfcoreChipseq121Consensus.auditIntervals(new StringReader(bed), new StringReader(repaired), plan, limits));
            }
            System.out.println("PASS_COMPLETE_ORIGINAL_CONSENSUS_COMPARISON rows=" + plan.rows().size() + " source_peaks=" + plan.sourcePeaks() + " merged=" + plan.mergedBeforeFiltering());
        } catch (IOException | IllegalArgumentException invalid) {
            System.err.println("FAIL2 NO CONSENSUS RESULT: " + invalid + "\nRequired action: retain original inputs and tables, correct the named identity/capacity/output discrepancy and use a new control path. No coordinate or numeric repair is performed.");
            System.exit(2);
        }
    }
}
