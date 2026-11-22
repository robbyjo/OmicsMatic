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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenomicSignalCliTest {
    @TempDir Path directory;
    private String output;
    private void fixture()throws Exception{
        Files.writeString(directory.resolve("sizes"),"chr1\t100\nchr2\t50\n");
        Files.writeString(directory.resolve("signal"),"track type=bedGraph name=fixture\nchr1\t20\t30\t4\nchr1\t0\t10\t2\n");
    }
    private int run(String method,String name,String missing,String... extra)throws Exception{
        var args=new ArrayList<>(List.of("genomic-signal","--method",method,"--input",directory.resolve("signal").toString(),"--regions",directory.resolve("regions").toString(),"--sizes",directory.resolve("sizes").toString(),"--genome-build","fixture-v1","--missing",missing,"--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();if(code!=0&&Files.exists(directory.resolve(name+"/WARNINGS.md")))output+=Files.readString(directory.resolve(name+"/WARNINGS.md"));return code;
    }
    @Test void nativeSummariesPreserveRegionOrderAndSeparateGapsFromExplicitZeros()throws Exception{
        fixture();Files.writeString(directory.resolve("regions"),"chr1\t5\t25\tpartial\nchr2\t0\t50\tabsent\nchr1\t0\t10\tcomplete\n");
        assertEquals(0,run("summarize","summary","zero"),output);var lines=Files.readAllLines(directory.resolve("summary/results.tsv"));
        assertTrue(lines.get(1).startsWith("partial\tchr1\t5\t25\t20\t10\t0.5\t30.0\t1.5\t3.0"));assertTrue(lines.get(2).endsWith("\t0.0\t0.0\t0.0\tNA"));assertTrue(lines.get(3).endsWith("\t1.0\t20.0\t2.0\t2.0"));
        assertTrue(output.contains("GENOMIC SIGNAL WARNINGS"));assertTrue(Files.readString(directory.resolve("summary/WARNINGS.md")).contains("unrepresented bases from explicit zero"));
        assertEquals(2,run("summarize","missing-fails","error"),output);assertTrue(output.contains("Unrepresented signal"));assertFalse(Files.exists(directory.resolve("missing-fails/results.tsv")));
    }
    @Test void stateMeansAreWeightedByBasesAndGenomeBackgroundIncludesAbsentContigs()throws Exception{
        fixture();Files.writeString(directory.resolve("regions"),"chr1\t0\t10\tSTATE_A\nchr1\t20\t25\tSTATE_A\nchr1\t10\t20\tSTATE_ZERO\n");
        assertEquals(0,run("state-enrichment","enrichment","zero","--pseudocount","0"),output);var lines=Files.readAllLines(directory.resolve("enrichment/results.tsv"));String[] state=lines.get(1).split("\t");
        assertEquals("STATE_A",state[0]);assertEquals("15",state[2]);assertEquals(40.0/15,Double.parseDouble(state[4]),1e-15);assertEquals(.4,Double.parseDouble(state[5]),1e-15);assertEquals(Math.log((40.0/15)/.4)/Math.log(2),Double.parseDouble(state[7]),1e-14);assertTrue(lines.get(2).endsWith("\tNA"));
        String warnings=Files.readString(directory.resolve("enrichment/WARNINGS.md"));assertTrue(warnings.contains("ZERO-SIGNAL STATES"));assertTrue(warnings.contains("SPARSE GENOME BACKGROUND"));
        assertEquals(0,run("state-enrichment","pseudocount","zero","--pseudocount","1"),output);assertTrue(Files.readString(directory.resolve("pseudocount/WARNINGS.md")).contains("PSEUDOCOUNT CHANGES"));
        assertEquals(2,run("state-enrichment","missing-genome","error","--pseudocount","0"),output);assertTrue(output.contains("Genome background"));
    }
    @Test void invalidOverlappingOrSignedDataFailWithActionableDiagnostics()throws Exception{
        fixture();Files.writeString(directory.resolve("regions"),"chr1\t0\t20\ta\nchr1\t10\t25\tb\n");
        assertEquals(2,run("state-enrichment","overlapping-states","zero","--pseudocount","0"),output);assertTrue(output.contains("State intervals overlap"));assertFalse(Files.exists(directory.resolve("overlapping-states/results.tsv")));
        Files.writeString(directory.resolve("regions"),"chr1\t0\t10\ta\n");Files.writeString(directory.resolve("signal"),"chr1\t0\t10\t-2\n");assertEquals(2,run("state-enrichment","signed-enrichment","zero","--pseudocount","0"),output);assertTrue(output.contains("nonnegative"));assertEquals(0,run("summarize","signed-summary","zero"),output);assertTrue(Files.readString(directory.resolve("signed-summary/results.tsv")).contains("-20.0\t-2.0\t-2.0"));
        Files.writeString(directory.resolve("signal"),"chr1\t0\t10\t2\nchr1\t5\t15\t3\n");assertEquals(2,run("summarize","overlapping-signal","zero"),output);assertTrue(output.contains("Overlapping bedGraph"));
        Files.writeString(directory.resolve("signal"),"chrOther\t0\t10\t2\n");assertEquals(2,run("summarize","wrong-contig","zero"),output);assertTrue(output.contains("exact reference"));
    }
    @Test void emptyTracksAndZeroGenomeAreNotRelabeledAsBiologicalAbsence()throws Exception{
        fixture();Files.writeString(directory.resolve("regions"),"chr1\t0\t10\ta\n");Files.writeString(directory.resolve("signal"),"# empty\n");
        assertEquals(0,run("summarize","empty-summary","zero"),output);assertTrue(Files.readString(directory.resolve("empty-summary/WARNINGS.md")).contains("EMPTY SIGNAL TRACK"));
        assertEquals(2,run("state-enrichment","empty-enrichment","zero","--pseudocount","1"),output);assertTrue(output.contains("undefined even with a pseudocount"));
        assertEquals(2,run("state-enrichment","missing-explicit-pseudocount","zero"),output);assertFalse(Files.exists(directory.resolve("missing-explicit-pseudocount")));
    }
}
