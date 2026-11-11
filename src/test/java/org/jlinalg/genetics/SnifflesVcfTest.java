/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Standalone scientific-format controls, including literal historical source quirks. */
public final class SnifflesVcfTest {
    private SnifflesVcfTest() { }
    private static int checks;
    private static final String HEAD = "##fileformat=VCFv4.2\n##source=Sniffles2_2.4\n" +
            "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tsample B\tsample B\n";
    private static final String RECORD = "chr1\t100\tcall1\tN\t<DEL>\t60\tPASS\tPRECISE;SVTYPE=DEL;SVLEN=-120;END=220;SUPPORT=8;PHASE=1,10,8,8,PASS,PASS;OTHER=nan\tGT:GQ:DR:DV:PS\t1|0:40:9:8:10\t0/1:20:4:3\n";

    public static void main(String[] args) throws Exception {
        var rows = new ArrayList<SnifflesVcf.Variant>();
        var summary = parse(HEAD + RECORD, SnifflesVcf.Edition.V2_4, SnifflesVcf.Limits.bounded(), rows);
        check(summary.records() == 1 && summary.passRecords() == 1, "complete counts");
        check(summary.header().samples().equals(List.of("sample B", "sample B")), "duplicate labels retained positionally");
        check(summary.phasedGenotypes() == 1 && summary.phaseInfoRecords() == 1, "GT and PHASE tracked separately");
        var row = rows.get(0);
        check(row.rawLine().equals(RECORD.stripTrailing()), "all original fields retained");
        check(row.infoInteger("SVLEN").orElseThrow() == -120 && row.infoInteger("END").orElseThrow() == 220, "signed deletion length");
        check(row.infoValue("OTHER").equals("nan"), "opaque source value preserved without numeric repair");
        check(row.sampleInteger(0, "PS").orElseThrow() == 10 && row.sampleValue(1,"PS") == null, "trailing PS omission retained");
        check(row.samples().get(1).size() == 4 && row.samples().get(0).size() == 5, "no genotype padding");
        check(row.readSupport(SnifflesVcf.Edition.V2_4).orElseThrow() == 8 && row.infoInteger("DP").isEmpty(), "support not invented as depth");
        check(row.readSupport(SnifflesVcf.Edition.V1_0_7).isEmpty(), "legacy support not silently taken from SUPPORT");
        try { row.columns().add("changed"); throw new AssertionError("mutable columns"); } catch (UnsupportedOperationException expected) { checks++; }
        try { row.samples().get(0).add("changed"); throw new AssertionError("mutable genotype"); } catch (UnsupportedOperationException expected) { checks++; }
        String legacy = HEAD.replace("##source=Sniffles2_2.4", "##source=Sniffles\n##INFO=<ID=SUPTYPE,Number=1,Type=String,Description=\"Type\">\n##INFO=<ID=SUPTYPE,Number=1,Type=String,Description=\"Type\">") +
                "chr2\t0\t1\tN\t<TRA>\t.\tPASS\tIMPRECISE;SVTYPE=TRA;RE=4;STD_quant_start=0.500000;CHR2=chr3;END=900;SVLEN=0\tGT:DR:DV\t./.:.:4\t./.:.:.\n";
        rows.clear(); summary = parse(legacy,SnifflesVcf.Edition.V1_0_7,SnifflesVcf.Limits.bounded(),rows);
        check(rows.get(0).position() == 0 && rows.get(0).alternate().equals("<TRA>"), "literal legacy coordinate and symbolic allele");
        check(rows.get(0).readSupport(SnifflesVcf.Edition.V1_0_7).orElseThrow() == 4, "legacy RE support");
        check(summary.header().metadataLines().size() == 4, "duplicate legacy header declarations retained");
        check(rows.get(0).sampleInteger(0,"DR").isEmpty(), "missing reference support not imputed");
        String bnd = HEAD + RECORD.replace("<DEL>", "N]chr2:900]").replace("SVTYPE=DEL;SVLEN=-120;END=220", "SVTYPE=BND;CHR2=chr2");
        rows.clear(); summary = parse(bnd, SnifflesVcf.Edition.V2_4, SnifflesVcf.Limits.bounded(),rows);
        check(rows.get(0).alternate().equals("N]chr2:900]") && rows.get(0).infoInteger("SVLEN").isEmpty(), "BND retains absent END/length");
        check(summary.variantTypes().get("BND") == 1, "BND count");
        rows.clear(); summary = parse(HEAD,SnifflesVcf.Edition.V2_0_2,SnifflesVcf.Limits.bounded(),rows);
        check(summary.records() == 0 && rows.isEmpty(), "legitimate complete empty VCF");
        rows.clear(); summary = parse(HEAD + RECORD.replace("SUPPORT=8", "SUPPORT=.").replace("\tPASS\t", "\tSUPPORT_MIN\t"),SnifflesVcf.Edition.V2_3_2,SnifflesVcf.Limits.bounded(),rows);
        check(summary.supportMissing() == 1 && summary.passRecords() == 0, "missing support and filtered call retained");
        reject(HEAD + RECORD.replace("SUPPORT=8", "SUPPORT=-8"), "negative support");
        reject(HEAD + RECORD.replace("SUPPORT=8", "SUPPORT=1.0"), "noninteger support");
        reject(HEAD + RECORD.replace("SUPPORT=8", "SUPPORT=9223372036854775808"), "integer overflow");
        reject(HEAD + RECORD.replace("SUPPORT=8", "SUPPORT=8;SUPPORT=9"), "ambiguous named support");
        reject(HEAD + RECORD.replace("SVTYPE=DEL", "SVTYPE=DEL;SVTYPE=INS"), "ambiguous named type");
        reject(HEAD + RECORD.replace("SVTYPE=DEL;", ""), "absent SVTYPE");
        reject(HEAD + RECORD.replace("\t100\t", "\t-1\t"), "negative position");
        reject(HEAD + RECORD.replace("GT:GQ:DR:DV:PS", "GT:GT:DR:DV:PS"), "duplicate FORMAT key");
        reject(HEAD + RECORD.replace("0/1:20:4:3", "0/1:20:4:3:10:extra"), "excess genotype field");
        reject(HEAD + RECORD.replace("0/1:20:4:3", "0/1::4:3"), "empty genotype field");
        reject(HEAD + RECORD.stripTrailing(), "truncated final line");
        reject(HEAD + RECORD + "#CHROM\n", "late extra header");
        reject(HEAD + RECORD.replace("OTHER=nan", "OTHER=bad\u0000value"), "NUL");
        reject(HEAD + RECORD.replace("\t0/1:20:4:3", ""), "dropped sample column");
        reject("##fileformat=VCFv4.2\n", "truncated header");
        rejectWith(HEAD + RECORD,new SnifflesVcf.Limits(16,100,10000,100,10),"total byte capacity");
        rejectWith(HEAD + RECORD,new SnifflesVcf.Limits(100000,100,128,100,10),"line capacity");
        rejectWith(HEAD + RECORD + RECORD,new SnifflesVcf.Limits(100000,1,10000,100,10),"record capacity");
        rejectWith(HEAD + RECORD,new SnifflesVcf.Limits(100000,100,10000,1,10),"header capacity");
        rejectWith(HEAD + RECORD,new SnifflesVcf.Limits(100000,100,10000,100,1),"sample capacity");
        byte[] invalid = (HEAD + RECORD).getBytes(StandardCharsets.UTF_8); invalid[invalid.length - 2] = (byte) 0xff;
        try { SnifflesVcf.read(new ByteArrayInputStream(invalid),SnifflesVcf.Edition.V2_4,SnifflesVcf.Limits.bounded(),h->{},v->{}); throw new AssertionError("accepted invalid UTF8"); }
        catch (IOException expected) { checks++; }
        System.out.println("PASS " + checks + " scientific VCF format/boundary controls");
    }
    private static SnifflesVcf.Summary parse(String text, SnifflesVcf.Edition edition, SnifflesVcf.Limits limits,List<SnifflesVcf.Variant> rows) throws IOException {
        return SnifflesVcf.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),edition,limits,h->{},rows::add);
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); checks++; }
    private static void reject(String text,String detail) throws Exception { rejectWith(text,SnifflesVcf.Limits.bounded(),detail); }
    private static void rejectWith(String text,SnifflesVcf.Limits limits,String detail) throws Exception {
        try { parse(text,SnifflesVcf.Edition.V2_4,limits,new ArrayList<>()); throw new AssertionError("accepted " + detail); }
        catch (IOException expected) { checks++; }
    }
}
