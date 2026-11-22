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

class CoordinateLiftCliTest {
    @TempDir Path directory;
    private String output;
    private Path write(String name,String text)throws Exception {Path path=directory.resolve(name);Files.writeString(path,text);return path;}
    private void inputs()throws Exception {
        write("forward chain with spaces.chain","chain 1000 chrS 1000 + 100 350 chrT 2000 + 500 740 1\n100 50 40\n100\n");write("source.sizes","chrS\t1000\n");write("target.sizes","chrT\t2000\n");
        write("input.tsv","chrom\tstart\tend\tid\tstrand\nchrS\t110\t120\tplus\t+\nchrS\t100\t350\tgap\t-\nchrS\t0\t1\tmissing\t.\nchrS\t200\t250\tallgap\t.\n");
    }
    private int run(String name,String policy,String... extra)throws Exception {
        var args=new ArrayList<>(List.of("coordinate-lift","--input",directory.resolve("input.tsv").toString(),"--chain",directory.resolve("forward chain with spaces.chain").toString(),"--source-sizes",directory.resolve("source.sizes").toString(),"--target-sizes",directory.resolve("target.sizes").toString(),"--source-build","FROM","--target-build","TO","--min-match","0.8","--unmapped",policy,"--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();if(code!=0&&Files.exists(directory.resolve(name+"/WARNINGS.md")))output+=Files.readString(directory.resolve(name+"/WARNINGS.md"));return code;
    }
    @Test void everyInputIsRetainedAndAcceptedBedUsesExactCoordinatesAndStrands()throws Exception {
        inputs();String original=Files.readString(directory.resolve("input.tsv"));assertEquals(0,run("lift","retain"),output);
        String results=Files.readString(directory.resolve("lift/results.tsv"));assertEquals(5,results.lines().count());assertTrue(results.contains("plus\tchrS\t110\t120\t+\tMAPPED\tchrT\t510\t520\t+"));assertTrue(results.contains("NO_CHAIN"));assertTrue(results.contains("NOT_UNIQUELY_MAPPED"));
        assertEquals("chrT\t510\t520\tplus\t0\t+\nchrT\t500\t740\tgap\t0\t-\n",Files.readString(directory.resolve("lift/mapped.bed")));assertTrue(output.contains("WARNINGS"));assertTrue(Files.readString(directory.resolve("lift/WARNINGS.md")).contains("not sequence identity"));assertEquals(original,Files.readString(directory.resolve("input.tsv")));
    }
    @Test void errorPolicyRetainsPartialTablesAndNoSuccessfulResults()throws Exception {
        inputs();assertEquals(2,run("fail","error"),output);assertFalse(Files.exists(directory.resolve("fail/results.tsv")));assertFalse(Files.exists(directory.resolve("fail/mapped.bed")));assertEquals(5,Files.readAllLines(directory.resolve("fail/partial/results.tsv")).size());assertTrue(output.contains("unmapped=error"));assertTrue(output.contains("Required action"));
    }
    @Test void exactReciprocalAndExplicitTargetLengthCapHaveDifferentOutcomes()throws Exception {
        inputs();Path reverse=write("reverse.chain","chain 1 chrT 2000 + 500 740 chrS 1000 + 100 350 1\n100 40 50\n100\n");
        assertEquals(0,run("roundtrip","retain","--reverse-chain",reverse.toString(),"--reverse-min-match","0.8","--roundtrip-policy","exact","--max-target-length","200"),output);
        String table=Files.readString(directory.resolve("roundtrip/results.tsv"));assertTrue(table.contains("MAPPED\tchrT\t510\t520\t+\t10\t1.0\tpassed\tchrS\t110\t120\t+"));assertTrue(table.contains("TARGET_SPAN_EXCEEDS_LIMIT"));assertTrue(table.contains("not-evaluated-forward-rejected"));assertEquals(1,Files.readAllLines(directory.resolve("roundtrip/mapped.bed")).size());
    }
    @Test void reciprocalOverlapThresholdAppliesToBothSpansAndDoesNotClaimPeakIdentity()throws Exception {
        inputs();write("input.tsv","chrom\tstart\tend\tid\tstrand\nchrS\t110\t120\tq\t+\n");Path reverse=write("shifted.chain","chain 1 chrT 2000 + 500 600 chrS 1000 + 105 205 1\n100\n");
        assertEquals(0,run("half","retain","--reverse-chain",reverse.toString(),"--reverse-min-match","1","--roundtrip-policy","overlap","--roundtrip-fraction","0.5"),output);assertTrue(Files.readString(directory.resolve("half/results.tsv")).contains("\tpassed\tchrS\t115\t125\t+\t0.5\t0.5"));
        assertEquals(0,run("over-half","retain","--reverse-chain",reverse.toString(),"--reverse-min-match","1","--roundtrip-policy","overlap","--roundtrip-fraction","0.50000001"),output);assertTrue(Files.readString(directory.resolve("over-half/results.tsv")).contains("REJECTED_ROUNDTRIP"));assertEquals(0,Files.size(directory.resolve("over-half/mapped.bed")));
    }
    @Test void wrongReferencesAndInvalidRowsFailWithProminentRecovery()throws Exception {
        inputs();write("target.sizes","chrT\t1999\n");assertEquals(2,run("wrong-length","retain"),output);assertTrue(output.contains("length differs"));assertFalse(Files.exists(directory.resolve("wrong-length/results.tsv")));
        write("target.sizes","chrT\t2000\n");write("input.tsv","chrom\tstart\tend\tid\nS\t110\t120\tx\n");assertEquals(2,run("alias","retain"),output);assertTrue(output.contains("exact dictionary"));
        write("input.tsv","chrom\tstart\tend\tid\nchrS\t110\t120\tx\nchrS\t120\t130\tx\n");assertEquals(2,run("duplicate","retain"),output);assertTrue(output.contains("unique"));
    }
}
