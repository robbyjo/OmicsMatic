/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import org.jlinalg.workflow.NfcoreChipseq121Mappings;

/** Native mapping contracts plus literal projection of original upstream fixtures. */
public final class NfcoreChipseqMappingsControl {
    private NfcoreChipseqMappingsControl() { }
    private static int checks;
    private static final String RH = "sample_id,fastq_1,fastq_2\n";
    private static final String CH = "sample_id,control_id,antibody,replicatesExist,multipleGroups\n";
    private static void require(boolean value) { if (!value) throw new AssertionError("Mapping contract " + checks); checks++; }
    private static NfcoreChipseq121Mappings.Manifest read(String r, String c) throws IOException {
        return NfcoreChipseq121Mappings.read(new StringReader(r), new StringReader(c), NfcoreChipseq121Mappings.Limits.defaults());
    }
    private static void refused(String r, String c) throws IOException {
        try { read(r, c); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Invalid source mapping was admitted");
    }
    private static void portable() throws IOException {
        String r = RH + "IP_R2_T1,/raw/α's first read.fq.gz,/raw/second read.fq.gz\n"
            + "C_R1_T1,c1.fq.gz,c2.fq.gz\nIP_R1_T1,a.fq.gz,b.fq.gz\nIP_R1_T2,c.fq.gz,d.fq.gz\n";
        String c = CH + "IP_R1,C_R1,A,1,0\nIP_R2,C_R1,A,1,0\n";
        var data = read(r, c);
        require(data.reads().size() == 4 && data.controls().size() == 2 && data.libraries().size() == 3);
        require(data.libraries().get(0).id().equals("IP_R2"));
        require(data.libraries().get(2).technicalSamples().size() == 2);
        require(data.reads().get(0).read1().equals("/raw/α's first read.fq.gz"));
        require(data.rowsWithRead2() == 4 && data.selfControlPairs() == 0);
        require(read(RH + "IP_R0_T1,a.fq.gz,\n", CH + "IP_R0,IP_R0,A,0,0\n").selfControlPairs() == 1);
        refused(r.replace("sample_id", "sample ID"), c);
        refused(r, c.replace("multipleGroups", "multiple_groups"));
        refused(r + "IP_R1_T1,e.fq.gz,f.fq.gz\n", c);
        refused(r, c.replace("C_R1", "C_R2"));
        refused(r, c.replace(",1,0", ",0,0"));
        refused(r, c.replace(",1,0", ",1,1"));
        refused(r, c.replace(",1,0", ",true,0"));
        refused(r, c + "IP_R1,C_R1,A,1,0\n");
        refused(r.replace("IP_R1_T2", "IP_R1_T0"), c);
        refused(r.replace("a.fq.gz", "a.fastq"), c);
        refused(r.replace("a.fq.gz", "a,b.fq.gz"), c);
        refused(r, c.replace("IP_R1,C_R1", "IP_R1_,C_R1"));
        refused(RH, CH);
        require(read(RH + "IP_R1_T1,a.fq.gz,\n", CH + "IP_R1,IP_R1,A,0,0\n").rowsWithRead2() == 0);
        System.out.println("PASS_PORTABLE_LITERAL_MAPPING_CONTRACTS " + checks);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--portable")) { portable(); return; }
        try {
            var data = NfcoreChipseq121Mappings.read(Path.of(args[0]), Path.of(args[1]), NfcoreChipseq121Mappings.Limits.defaults());
            System.out.write((NextflowJson.json(data.document()) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException invalid) {
            System.err.println("FAIL2 source checked-mapping graph: " + invalid.getMessage()
                + "\nRequired action: preserve the upstream outputs and original IDs; resolve the named missing/invalid mapping before analysis. No correction or replacement mapping was inferred.");
            System.exit(2);
        }
    }
}
