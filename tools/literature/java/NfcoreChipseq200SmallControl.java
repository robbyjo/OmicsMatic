/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;

/** Original tiny BAM / literal container-prefix checks. No alignment/model or biological claim. */
public final class NfcoreChipseq200SmallControl {
    private NfcoreChipseq200SmallControl() { }
    public static void main(String[] args) {
        try {
            if (args[0].equals("container")) { if (!NfcoreChipseq200ImageAudit.container(args[1]).equals(args[2])) throw new java.io.IOException("Unexpected literal container word"); }
            else if (args[0].equals("bam")) {
                String csv = "sample,fastq_1,fastq_2,antibody,control\nIP_R1,a1.fq.gz,a2.fq.gz,MARK,CTRL_R1\nIP_R1,b1.fq.gz,b2.fq.gz,MARK,CTRL_R1\nCTRL_R1,c1.fq.gz,c2.fq.gz,,\n";
                var plan = NfcoreChipseq200Mappings.inspect(new StringReader(csv), NfcoreChipseq200Mappings.Limits.defaults()); var technical = plan.reads().stream().filter(x -> x.libraryId().equals("IP_R1")).toList();
                var peaks = List.of(new GenomicIntervals.Interval("chr1", 10, 20, "A", "+"), new GenomicIntervals.Interval("chr1", 18, 20, "B", "+"), new GenomicIntervals.Interval("chr1", 17, 21, "C", "+"), new GenomicIntervals.Interval("chr1", 29, 40, "D", "+")); var result = NfcoreChipseq200BamAudit.inspect(Path.of(args[1]), technical, Map.of("chr1", 250L), peaks, null, 1000);
                if (result.records() != 8 || result.qcPassingMapped() != 6 || result.qcFailedMapped() != 2 || result.fraction().isEmpty() || result.fraction().getAsDouble() != 0.5) throw new java.io.IOException("Complete original eight-record BAM/BedTools family differs");
            } else throw new java.io.IOException("Unknown complete small control");
            System.out.println("PASS_COMPLETE_V2_SMALL_CONTROL");
        } catch (Exception failure) { System.err.println("FAIL2 NO V2 SMALL-CONTROL RESULT: " + failure + "; preserve originals and the named discrepancy; no namespace, RG or flag repair is inferred."); System.exit(2); }
    }
}
