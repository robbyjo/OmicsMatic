/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class SequenceUtilsCliTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path directory;
    private String output;
    private int run(String method,String name,String... extra) {
        var arguments = new ArrayList<>(List.of("sequence-tools","--method",method,"--out",directory.resolve(name).toString(),"--no-config")); arguments.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int status = JLinAlgCli.run(arguments.toArray(String[]::new),stream,stream); output = bytes.toString(); return status;
    }
    private static Map<?,?> reference() throws Exception {
        try (var stream=SequenceUtilsCliTest.class.getResourceAsStream("/seqtk/reference.json")) { return (Map<?,?>) new org.yaml.snakeyaml.Yaml().load(stream); }
    }
    private List<Path> paired(int count) throws Exception {
        Path first=directory.resolve("R1.fastq.gz"),second=directory.resolve("R2.fastq.gz");
        try (var a=new FastqIO.Writer(first);var b=new FastqIO.Writer(second)) {
            for(int i=0;i<count;i++) { a.write(new FastqIO.Read("@read"+i+"/1 original comment","ACGTN","IHFED"));b.write(new FastqIO.Read("@read"+i+"/2 another comment","TGCAN","ABCDE")); }
        }
        return List.of(first,second);
    }
    private List<Integer> ids(Path file) throws Exception {
        var ids=new ArrayList<Integer>(); try(var reader=new FastqIO.Reader(file)) { FastqIO.Read r;while((r=reader.next())!=null)ids.add(Integer.valueOf(r.pairId().substring(4))); }return ids;
    }
    @Test void pairedFractionAndCountMatchUpstreamMembershipAndPreserveCompleteRecords() throws Exception {
        var reads=paired(2049);
        for(String mode:List.of("fraction","count")) {
            String value=mode.equals("fraction")?"0.25":"7";
            assertEquals(0,run("sample",mode,"--reads",reads.get(0).toString(),"--reads2",reads.get(1).toString(),"--"+mode,value,"--seed","100"),output);
            Map<?,?> expected=((List<?>)reference().get("sampling")).stream().map(x->(Map<?,?>)x)
                .filter(x->((Number)x.get("seed")).longValue()==100&&x.get("mode").equals(mode)&&((Number)x.get("value")).doubleValue()==Double.parseDouble(value)).findFirst().orElseThrow();
            List<Integer> indices=((List<?>)expected.get("indices")).stream().map(x->((Number)x).intValue()).toList();
            assertEquals(indices,ids(directory.resolve(mode+"/reads_R1.fastq.gz")));assertEquals(indices,ids(directory.resolve(mode+"/reads_R2.fastq.gz")));
            try(var r=new FastqIO.Reader(directory.resolve(mode+"/reads_R2.fastq.gz"))) {var mate=r.next();assertEquals("TGCAN",mate.sequence());assertEquals("ABCDE",mate.quality());assertTrue(mate.header().endsWith("another comment"));}
        }
    }
    @Test void samplingBoundariesAndAboveInputRequestsHaveActionableEmptyOrShortfallWarnings() throws Exception {
        var reads=paired(3);
        assertEquals(0,run("sample","none","--reads",reads.get(0).toString(),"--fraction","0"),output);
        assertTrue(output.contains("SEQUENCE WARNINGS")); assertTrue(Files.readString(directory.resolve("none/WARNINGS.md")).contains("No reads/pairs retained"));
        assertEquals(List.of(),ids(directory.resolve("none/reads_R1.fastq.gz")));
        assertEquals(0,run("sample","all","--reads",reads.get(0).toString(),"--fraction","1"),output);assertEquals(List.of(0,1,2),ids(directory.resolve("all/reads_R1.fastq.gz")));
        assertEquals(0,run("sample","shortfall","--reads",reads.get(0).toString(),"--count","100"),output);
        assertTrue(Files.readString(directory.resolve("shortfall/WARNINGS.md")).contains("exceeds input"));
    }
    @Test void trimsLengthSimulationAndPoorQualityFallbackWithExplicitWarnings() throws Exception {
        Path first=directory.resolve("length1.fq"),second=directory.resolve("length2.fq");
        Files.writeString(first,"@one/1 comment\n"+"ACG".repeat(25)+"\n+\n"+"I".repeat(75)+"\n");
        Files.writeString(second,"@one/2 comment\n"+"TGC".repeat(25)+"\n+\n"+"J".repeat(75)+"\n");
        assertEquals(0,run("trimfq","length","--reads",first.toString(),"--reads2",second.toString(),"--length","50"),output);
        try(var r=new FastqIO.Reader(directory.resolve("length/reads_R1.fastq.gz"))) {var read=r.next();assertEquals("ACG".repeat(25).substring(0,50),read.sequence());assertEquals("I".repeat(50),read.quality());}
        Files.writeString(first,"@poor\n"+"A".repeat(80)+"\n+\n"+"!".repeat(80)+"\n");
        assertEquals(0,run("trimfq","poor","--reads",first.toString()),output);
        assertTrue(output.contains("SEQUENCE WARNINGS"));assertTrue(Files.readString(directory.resolve("poor/WARNINGS.md")).contains("minimum-length fallback"));
        assertEquals(30,FastqIO.inspect(directory.resolve("poor/reads_R1.fastq.gz"),null).read1().minLength());
        assertEquals(0,run("trimfq","empty-trim","--reads",first.toString(),"--trim-left","100"),output);
        assertTrue(Files.readString(directory.resolve("empty-trim/WARNINGS.md")).contains("zero-length"));
    }
    private Path fasta() throws Exception {
        Path path=directory.resolve("reference.fa");var text=new StringBuilder();
        for(var entry:((Map<?,?>)reference().get("chromosomes")).entrySet()) {
            text.append('>').append(entry.getKey()).append("\r\n");String s=(String)entry.getValue();
            for(int i=0;i<s.length();i+=57)text.append(s,i,Math.min(s.length(),i+57)).append("\r\n");
        }
        Files.writeString(path,text);return path;
    }
    @Test void indexedSubsequenceExtractionMatchesUpstreamAndPreservesOldIndex() throws Exception {
        Path fasta=fasta(),old=directory.resolve("reference.fa.fai"),bed=directory.resolve("regions.bed");Files.writeString(old,"user-owned old index");
        Map<?,?> fixture=(Map<?,?>)reference().get("subsequence");Files.writeString(bed,(String)fixture.get("bed3"));
        assertEquals(0,run("subseq","extract","--reference",fasta.toString(),"--regions",bed.toString()),output);
        assertEquals(fixture.get("expected_fasta"),Files.readString(directory.resolve("extract/sequences.fasta")));
        assertEquals("user-owned old index",Files.readString(old));
        Files.writeString(bed,"chrD\t60\t150\nchrA\t0\t20\n");
        assertEquals(0,run("subseq","ordered","--reference",fasta.toString(),"--regions",bed.toString()),output);
        assertTrue(Files.readString(directory.resolve("ordered/sequences.fasta")).startsWith(">chrD:61-150\n"));
        Files.writeString(bed,"chrA\t1\t999999\n");
        assertEquals(2,run("subseq","bounds","--reference",fasta.toString(),"--regions",bed.toString()),output);
        assertFalse(Files.exists(directory.resolve("bounds/results.tsv")));assertTrue(Files.readString(directory.resolve("bounds/WARNINGS.md")).contains("outside the exact reference"));
    }
    @Test void telomeresMatchIndependentBedCoordinatesAndChunkedAccess() throws Exception {
        Path fasta=fasta();
        assertEquals(0,run("telo","repeat","--reference",fasta.toString()),output);
        Map<?,?> fixture=(Map<?,?>)((List<?>)reference().get("telomeres")).get(0);
        String expected=((String)fixture.get("bed4")).lines().map(x->String.join("\t",List.of(x.split("\t")).subList(0,3))+"\n").collect(java.util.stream.Collectors.joining());
        assertEquals(expected,Files.readString(directory.resolve("repeat/telomeres.bed")));
        Path longFasta=directory.resolve("long.fa");String sequence="CCCTAA".repeat(12000)+"N".repeat(3000)+"TTAGGG".repeat(12000);
        var text=new StringBuilder(">long\n");for(int i=0;i<sequence.length();i+=60)text.append(sequence,i,Math.min(sequence.length(),i+60)).append('\n');Files.writeString(longFasta,text);
        assertEquals(0,run("telo","chunked","--reference",longFasta.toString()),output);
        assertEquals("long\t0\t72000\nlong\t75000\t147000\n",Files.readString(directory.resolve("chunked/telomeres.bed")));
    }
    @Test void invalidPairMalformedInputsAndUnsupportedOptionsFailWithoutSuccessfulResults() throws Exception {
        Path a=directory.resolve("a.fq"),b=directory.resolve("b.fq");Files.writeString(a,"@a/1\nACGT\n+\nIIII\n");Files.writeString(b,"@b/2\nACGT\n+\nIIII\n");
        assertEquals(2,run("sample","mismatch","--reads",a.toString(),"--reads2",b.toString(),"--fraction","0"),output);
        assertFalse(Files.exists(directory.resolve("mismatch/results.tsv")));assertTrue(Files.exists(directory.resolve("mismatch/partial/reads_R1.fastq.gz")));
        assertTrue(Files.readString(directory.resolve("mismatch/WARNINGS.md")).contains("Required action"));
        assertEquals(2,run("sample","options","--reference",a.toString()),output);assertFalse(Files.exists(directory.resolve("options")));
        assertEquals(2,run("sample","nan","--reads",a.toString(),"--fraction","NaN"),output);
        Files.writeString(a,"@bad\nACGT\n+\nIII\n");assertEquals(2,run("trimfq","malformed","--reads",a.toString()),output);
        assertFalse(Files.exists(directory.resolve("malformed/results.tsv")));
    }
}
