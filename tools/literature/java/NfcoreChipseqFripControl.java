/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.GenomicIntervals;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;

/** Actual original BAM/BEDTools fixture bridge; no simulated source scalar replaces upstream output. */
public final class NfcoreChipseqFripControl {
    private NfcoreChipseqFripControl() { }
    private static final String TECH = "IP_R1_T1";
    private static SAMRecord record(SAMFileHeader header, String name, int start1, String cigar, boolean qcFailed) {
        var row = new SAMRecord(header); row.setReadName(name); row.setAttribute("RG", TECH);
        int bases = cigar.equals("2M20N3M") ? 5 : cigar.equals("30M") ? 30 : 5;
        row.setReadString("A".repeat(bases)); row.setBaseQualityString("I".repeat(bases));
        row.setReadFailsVendorQualityCheckFlag(qcFailed);
        if (start1 == 0) { row.setReadUnmappedFlag(true); row.setCigarString("*"); }
        else { row.setReferenceName("chr1"); row.setAlignmentStart(start1); row.setCigarString(cigar); row.setMappingQuality(60); }
        return row;
    }
    private static void make(String mode, Path folder) throws Exception {
        var header = new SAMFileHeader(); header.setSortOrder(SAMFileHeader.SortOrder.unsorted);
        header.addSequence(new SAMSequenceRecord("chr1", 2100000000));
        var group = new SAMReadGroupRecord(TECH); group.setSample(mode.equals("wrong-sample") ? "OTHER_R1" : "IP_R1"); header.addReadGroup(group);
        var rows = new ArrayList<SAMRecord>(); String bed;
        switch (mode) {
            case "overlap-and-qcfail", "wrong-sample" -> {
                rows.add(record(header, "a", 11, "5M", false)); rows.add(record(header, "b", 26, "5M", false));
                rows.add(record(header, "c", 11, "5M", true)); rows.add(record(header, "unmapped", 0, "*", false));
                bed = "chr1\t10\t14\tp1\nchr1\t12\t16\tp2\nchr1\t29\t30\tp3\n";
            }
            case "fraction-boundary" -> { rows.add(record(header, "a", 101, "30M", false)); bed = "chr1\t100\t106\texact20percent\nchr1\t100\t105\tbelow20percent\n"; }
            case "bookended" -> { rows.add(record(header, "a", 17, "5M", false)); bed = "chr1\t10\t16\tnoOverlap\n"; }
            case "unsplit-cigar-span" -> { rows.add(record(header, "a", 1, "2M20N3M", false)); bed = "chr1\t5\t10\tinsideSkippedReferenceSpan\n"; }
            case "b-fraction-default" -> { rows.add(record(header, "a", 2000000000, "5M", false)); bed = "chr1\t0\t2000000000\toneBaseFailsDefaultBFraction\n"; }
            case "undefined-denominator" -> { rows.add(record(header, "a", 11, "5M", true)); bed = "chr1\t10\t14\tp1\n"; }
            case "contig-alias-refused" -> { rows.add(record(header, "a", 11, "5M", false)); bed = "1\t10\t14\tdifferentLiteralContig\n"; }
            default -> throw new IllegalArgumentException("Unknown controlled fixture");
        }
        Files.writeString(folder.resolve("peaks.bed"), bed, StandardCharsets.UTF_8);
        try (var writer = new SAMFileWriterFactory().setCreateIndex(false).makeBAMWriter(header, true, folder.resolve("input.bam"))) {
            for (var row : rows) writer.addAlignment(row);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--make")) { make(args[1], Path.of(args[2])); return; }
        Path folder = Path.of(args[0]); var peaks = new ArrayList<GenomicIntervals.Interval>();
        for (String line : Files.readAllLines(folder.resolve("peaks.bed"), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t", -1); peaks.add(new GenomicIntervals.Interval(f[0], Long.parseLong(f[1]), Long.parseLong(f[2]), f[3], "."));
        }
        var library = new NfcoreChipseq121Mappings.Library("IP_R1", List.of(new NfcoreChipseq121Mappings.Read(TECH, "r1.fq.gz", "r2.fq.gz")));
        try {
            var result = NfcoreChipseq121BamAudit.inspect(folder.resolve("input.bam"), library, Map.of("chr1", 2100000000L), peaks, 1000000);
            System.out.write((NextflowJson.json(result.frip().document()) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException invalid) {
            System.err.println("FAIL2 native original ChIP-seq BAM/FRiP audit: " + invalid.getMessage()
                + "\nRequired action: preserve original BAM, mappings, peaks and flags. Resolve the named structural/reference inconsistency without repairing identifiers or substituting a ratio.");
            System.exit(2);
        }
    }
}
