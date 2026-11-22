/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import htsjdk.samtools.*;
import htsjdk.variant.vcf.VCFFileReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtsToolsCliTest {
    @TempDir Path dir;
    String output;
    private String samtools() { String s = System.getenv("JLINALG_SAMTOOLS"); assumeTrue(s != null, "Set JLINALG_SAMTOOLS to pinned 1.24"); return s; }
    private String bcftools() { String s = System.getenv("JLINALG_BCFTOOLS"); assumeTrue(s != null, "Set JLINALG_BCFTOOLS to pinned 1.24"); return s; }
    private int run(String method, Path input, String executable, String name, String... extra) throws Exception {
        var args = new ArrayList<>(List.of("hts-tools", "--method", method, "--input", input.toString(), method.startsWith("sam-") ? "--samtools" : "--bcftools", executable,
            "--out", dir.resolve(name).toString(), "--no-config")); args.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int code = JLinAlgCli.run(args.toArray(String[]::new), stream, stream); output = bytes.toString();
        Path warnings = dir.resolve(name + "/WARNINGS.md"); if (code != 0 && Files.exists(warnings)) output += Files.readString(warnings);
        Path log = dir.resolve(name + "/external.log"); if (code != 0 && Files.exists(log)) output += Files.readString(log);
        return code;
    }
    private Path sam() throws Exception {
        Path file = dir.resolve("reads.sam");
        Files.writeString(file, "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg\tSM:S1\tPL:ILLUMINA\n"
            + "read10\t0\tchr1\t200\t60\t10M\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\tNM:i:0\n"
            + "read2\t1024\tchr1\t100\t50\t10M\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\tNM:i:0\n"
            + "read3\t512\tchr1\t110\t10\t10M\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\tNM:i:0\n"
            + "unmapped\t4\t*\t0\t0\t*\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\tRG:Z:rg\n"); return file;
    }
    private Path reference() throws Exception {
        Path fasta = dir.resolve("ref.fa"); Files.writeString(fasta, ">chr1\n" + "A".repeat(1000) + "\n");
        Files.writeString(dir.resolve("ref.fa.fai"), "chr1\t1000\t6\t1000\t1001\n"); return fasta;
    }
    @Test void missingRuntimeSkipsAndInvalidTimeoutDoesNotLaunch() throws Exception {
        Path input = sam();
        assertEquals(3, run("sam-sort", input, dir.resolve("missing.exe").toString(), "missing"), output);
        assertTrue(output.contains("SKIPPED ANALYSIS")); assertTrue(output.contains("install_hts_dependencies.py --allow-download"));
        assertFalse(Files.exists(dir.resolve("missing/results.tsv")));
        assertEquals(2, run("sam-sort", input, "missing", "bad-timeout", "--timeout", "0")); assertFalse(Files.exists(dir.resolve("bad-timeout")));
    }
    @Test void realSamtoolsSortSelectRegionIndexAndFlagstatMatchIndependentKnownRecords() throws Exception {
        String exe = samtools(); Path input = sam();
        assertEquals(0, run("sam-sort", input, exe, "sorted", "--create-index", "true"), output);
        Path bam = dir.resolve("sorted/alignment.bam");
        assertTrue(Files.isRegularFile(dir.resolve("sorted/alignment.bai")));
        assertEquals(List.of("read2", "read3", "read10", "unmapped"), readNames(bam));
        assertEquals(0, run("sam-sort", input, exe, "query", "--sort-order", "queryname"), output);
        assertEquals(List.of("read10", "read2", "read3", "unmapped"), readNames(dir.resolve("query/alignment.bam")));
        assertEquals(0, run("sam-index", bam, exe, "index"), output);
        assertEquals(0, run("sam-view", bam, exe, "selected", "--min-mapq", "20", "--exclude-flags", "1024"), output);
        assertEquals(List.of("read10"), readNames(dir.resolve("selected/alignment.bam")));
        assertEquals(0, run("sam-view", bam, exe, "region", "--region", "chr1:109-110"), output);
        assertEquals(List.of("read2", "read3"), readNames(dir.resolve("region/alignment.bam")));
        assertEquals(0, run("sam-flagstat", input, exe, "flags"), output);
        var groups = (Map<?, ?>) SimpleJson.parse(Files.readString(dir.resolve("flags/flagstat.json")));
        var pass = (Map<?, ?>) groups.get("QC-passed reads"); var fail = (Map<?, ?>) groups.get("QC-failed reads");
        assertEquals(3, ((Number) pass.get("total")).intValue()); assertEquals(1, ((Number) fail.get("total")).intValue());
        assertEquals(1, ((Number) pass.get("duplicates")).intValue());
        assertEquals(0, run("sam-view", bam, exe, "empty", "--require-flags", "2048"), output);
        assertTrue(Files.readString(dir.resolve("empty/WARNINGS.md")).contains("retained no records"));
        Path ref = reference();
        assertEquals(0, run("sam-mpileup", bam, exe, "pileup", "--reference", ref.toString(), "--min-mapq", "30", "--min-baseq", "30", "--baq", "false", "--exclude-flags", "0"), output);
        List<String> pileup = Files.readAllLines(dir.resolve("pileup/pileup.tsv")); assertEquals(20, pileup.size());
        assertEquals("100", pileup.get(0).split("\t")[1]); assertEquals("209", pileup.get(19).split("\t")[1]);
        assertTrue(pileup.stream().allMatch(line -> line.split("\t")[3].equals("1")));
    }
    @Test void realSamtoolsFixmateMarkdupPipelineKeepsAndRemovesCompleteDuplicatePairs() throws Exception {
        String exe = samtools(); Path input = dir.resolve("paired.sam");
        StringBuilder data = new StringBuilder("@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n@RG\tID:rg\tSM:S1\tPL:ILLUMINA\n");
        for (int read = 0; read < 2; read++) for (int mate = 0; mate < 2; mate++) {
            int position = mate == 0 ? 100 : 200;
            data.append("INST:1:FC:1:1101:").append(100 + read*100).append(':').append(100+read*100)
                .append('\t').append(mate==0?99:147).append("\tchr1\t").append(position).append("\t60\t20M\t=\t").append(mate==0?200:100)
                .append('\t').append(mate==0?120:-120).append('\t').append("A".repeat(20)).append('\t').append((read==0?"I":"H").repeat(20)).append("\tRG:Z:rg\n");
        }
        Files.writeString(input,data);
        for (boolean remove : List.of(false,true)) {
            String name = remove ? "removed" : "marked";
            assertEquals(0, run("sam-markdup",input,exe,name,"--remove-duplicates",Boolean.toString(remove)),output);
            int count=0,duplicates=0,first=0,second=0;
            try(var reader=SamReaderFactory.makeDefault().open(dir.resolve(name+"/alignment.bam"))) {
                for(var record:reader) { count++; if(record.getDuplicateReadFlag())duplicates++;if(record.getFirstOfPairFlag())first++;if(record.getSecondOfPairFlag())second++; }
            }
            assertEquals(remove?2:4,count);assertEquals(remove?0:2,duplicates);assertEquals(remove?1:2,first);assertEquals(first,second);
            assertFalse(Files.exists(dir.resolve(name+"/work-name.bam")));assertTrue(Files.readString(dir.resolve(name+"/session.yaml")).contains("validated_stage_sha256"));
        }
    }
    private List<String> readNames(Path bam) throws Exception {
        var names = new ArrayList<String>(); try (var reader = SamReaderFactory.makeDefault().open(bam)) { reader.forEach(r -> names.add(r.getReadName())); } return names;
    }
    private Path vcf() throws Exception {
        Path p = dir.resolve("input.vcf");
        Files.writeString(p, "##fileformat=VCFv4.2\n##contig=<ID=chr1,length=1000>\n##FILTER=<ID=Existing,Description=\"Previous flag\">\n"
            + "##INFO=<ID=AC,Number=A,Type=Integer,Description=\"Alt counts\">\n##INFO=<ID=AN,Number=1,Type=Integer,Description=\"Allele count\">\n"
            + "##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n"
            + "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\tS2\n"
            + "chr1\t100\tv1\tA\tC,G\t50\tPASS\tAC=1,2;AN=4\tGT\t0/2\t1/2\n"
            + "chr1\t200\tv2\tAA\tA\t10\tExisting\tAC=1;AN=4\tGT\t0/1\t0/0\n"
            + "chr1\t300\tv3\tA\tT\t.\tPASS\tAC=1;AN=2\tGT\t./.\t0/1\n"); return p;
    }
    @Test void realBcftoolsNormalizeFilterSelectAndStatsHaveKnownAllelesAndGenotypes() throws Exception {
        String exe = bcftools(); Path input = vcf(); Path ref = reference();
        assertEquals(0, run("bcf-norm", input, exe, "normalized", "--reference", ref.toString()), output);
        try (var reader = new VCFFileReader(dir.resolve("normalized/variants.vcf"), false)) {
            var rows = reader.iterator().stream().toList(); assertEquals(4, rows.size());
            assertEquals(1, rows.get(0).getStart()); assertEquals("AA", rows.get(0).getReference().getDisplayString()); // homopolymer deletion left-aligns to base 1
            assertEquals("C", rows.get(1).getAlternateAllele(0).getDisplayString()); assertEquals("G", rows.get(2).getAlternateAllele(0).getDisplayString());
        }
        assertEquals(0, run("bcf-filter", input, exe, "filtered", "--min-qual", "30"), output);
        try (var reader = new VCFFileReader(dir.resolve("filtered/variants.vcf"), false)) {
            var rows = reader.iterator().stream().toList(); assertFalse(rows.get(0).isFiltered());
            assertEquals(Set.of("Existing", "JL_LOW_QUAL"), rows.get(1).getFilters()); assertEquals(Set.of("JL_LOW_QUAL"), rows.get(2).getFilters());
        }
        Path list = dir.resolve("samples.txt"); Files.writeString(list, "S2\n");
        assertEquals(0, run("bcf-select", input, exe, "selected", "--samples-list", list.toString()), output);
        try (var reader = new VCFFileReader(dir.resolve("selected/variants.vcf"), false)) {
            assertEquals(List.of("S2"), reader.getHeader().getGenotypeSamples()); var rows = reader.iterator().stream().toList();
            assertEquals(3, rows.size()); assertEquals(List.of(1, 1), rows.get(0).getAttributeAsIntList("AC", 0));
            assertEquals(2, rows.get(0).getAttributeAsInt("AN", 0));
        }
        assertEquals(0, run("bcf-stats", input, exe, "stats"), output);
        assertTrue(Files.readString(dir.resolve("stats/variant-stats.txt")).contains("number of records:\t3"));
    }
    @Test void realReferenceMismatchAndUnknownSamplesFailWithoutPublishingSuccessfulVariants() throws Exception {
        String exe = bcftools(); Path input = vcf(), reference = reference();
        Files.writeString(reference, ">chr1\n" + "C".repeat(1000) + "\n");
        assertEquals(2, run("bcf-norm", input, exe, "mismatch", "--reference", reference.toString()), output);
        assertFalse(Files.exists(dir.resolve("mismatch/results.tsv"))); assertFalse(Files.exists(dir.resolve("mismatch/variants.vcf")));
        assertTrue(Files.exists(dir.resolve("mismatch/partial/variants.vcf")));
        Path list = dir.resolve("missing-sample.txt"); Files.writeString(list, "unknown\n");
        assertEquals(2, run("bcf-select", input, exe, "unknown", "--samples-list", list.toString()), output);
        assertTrue(Files.readString(dir.resolve("unknown/WARNINGS.md")).contains("Required action"));
    }
    @Test void realBcftoolsPileupAndCallRecoverKnownDiploidSnvAndExplicitHaploidCall() throws Exception {
        String exe = bcftools(); Path reference = reference(); Path bam = dir.resolve("calling.bam");
        var header = new SAMFileHeader(); header.addSequence(new SAMSequenceRecord("chr1", 1000)); header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        var group = new SAMReadGroupRecord("rg"); group.setSample("S1"); group.setPlatform("ILLUMINA"); header.addReadGroup(group);
        var random = new Random(8731); // Nonrepetitive reference avoids ambiguous local alleles.
        StringBuilder sequence = new StringBuilder(); for (int i = 0; i < 1000; i++) sequence.append("ACGT".charAt(random.nextInt(4)));
        Files.writeString(reference, ">chr1\n" + sequence + "\n");
        char refBase = sequence.charAt(149), alternate = refBase == 'A' ? 'C' : 'A';
        try (var writer = new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header, true, bam)) {
            for (int start = 100; start <= 120; start++) for (int repeat = 0; repeat < 4; repeat++) {
                var read = new SAMRecord(header); read.setReadName("R" + start + "_" + repeat); read.setReferenceName("chr1"); read.setAlignmentStart(start); read.setMappingQuality(60); read.setCigarString("100M");
                StringBuilder bases = new StringBuilder(sequence.substring(start-1,start+99));
                if (repeat >= 2) bases.setCharAt(150-start, alternate);
                read.setReadString(bases.toString()); read.setBaseQualityString("I".repeat(100)); read.setAttribute("RG", "rg"); writer.addAlignment(read);
            }
        }
        assertEquals(0, run("bcf-call", bam, exe, "diploid", "--reference", reference.toString(), "--region", "chr1:100-220", "--ploidy", "2"), output);
        try (var reader = new VCFFileReader(dir.resolve("diploid/variants.vcf"), false)) {
            var rows = reader.iterator().stream().toList(); assertEquals(1, rows.size()); assertEquals(150, rows.get(0).getStart());
            assertEquals(Character.toString(refBase), rows.get(0).getReference().getDisplayString()); assertEquals(Character.toString(alternate), rows.get(0).getAlternateAllele(0).getDisplayString());
            assertTrue(rows.get(0).getGenotype("S1").isHet());
        }
        Path haploidBam = dir.resolve("haploid.bam");
        try (var reader = SamReaderFactory.makeDefault().open(bam); var writer = new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header, true, haploidBam)) {
            for (var read : reader) {
                var bases = new StringBuilder(read.getReadString()); bases.setCharAt(150-read.getAlignmentStart(), alternate);
                read.setReadString(bases.toString()); writer.addAlignment(read);
            }
        }
        assertEquals(0, run("bcf-call", haploidBam, exe, "haploid", "--reference", reference.toString(), "--region", "chr1:100-220", "--ploidy", "1"), output);
        try (var reader = new VCFFileReader(dir.resolve("haploid/variants.vcf"), false)) {
            var rows = reader.iterator().stream().toList(); assertEquals(1, rows.size());
            assertEquals(150, rows.get(0).getStart()); assertEquals(1, rows.get(0).getGenotype("S1").getPloidy()); assertTrue(rows.get(0).getGenotype("S1").isHomVar());
        }
        assertEquals(2, run("bcf-call", bam, exe, "no-ploidy", "--reference", reference.toString()), output);
    }
}
