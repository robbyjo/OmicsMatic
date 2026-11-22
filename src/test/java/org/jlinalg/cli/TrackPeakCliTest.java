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

class TrackPeakCliTest {
    @TempDir Path directory;
    private String output;
    private void fixture()throws Exception{
        Files.writeString(directory.resolve("sizes"),"chr1\t100\n");
        Files.writeString(directory.resolve("signal"),"chr1\t70\t100\t0\nchr1\t10\t30\t3\nchr1\t0\t10\t0\nchr1\t40\t70\t3\nchr1\t30\t40\t0\n");
    }
    private int run(String name,String missing,String precision,String cutoff,String... extra)throws Exception{
        var args=new ArrayList<>(List.of("track-peaks","--input",directory.resolve("signal").toString(),"--sizes",directory.resolve("sizes").toString(),"--genome-build","fixture","--missing",missing,"--precision",precision,"--cutoff",cutoff,"--min-length","60","--max-gap","10","--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();if(Files.exists(directory.resolve(name+"/WARNINGS.md")))output+=Files.readString(directory.resolve(name+"/WARNINGS.md"));return code;
    }
    @Test void executableContractProducesKnownBedAndSummitWithoutFabricatedSignificance()throws Exception{
        fixture();assertEquals(0,run("peaks","error","float32","3"),output);
        assertEquals("chr1\t10\t70\tpeak_1\n",Files.readString(directory.resolve("peaks/peaks.bed")));
        var lines=Files.readAllLines(directory.resolve("peaks/results.tsv"));assertEquals("peak_1\tchr1\t10\t70\t60\t20\t10\t3.0\t50",lines.get(1));
        assertFalse(lines.get(0).contains("pvalue"));assertFalse(Files.exists(directory.resolve("peaks/WARNINGS.md")));
        String before=Files.readString(directory.resolve("peaks/manifest.yaml"));assertEquals(2,run("peaks","error","float32","3"));assertEquals(before,Files.readString(directory.resolve("peaks/manifest.yaml")));
    }
    @Test void sparseZeroPrecisionAndEmptyFindingsRemainProminent()throws Exception{
        fixture();Files.writeString(directory.resolve("signal"),"chr1\t10\t30\t3.00000001\n");
        assertEquals(2,run("missing","error","float32","3"),output);assertTrue(output.contains("Required action"));assertFalse(Files.exists(directory.resolve("missing/results.tsv")));assertFalse(Files.exists(directory.resolve("missing/peaks.bed")));
        assertEquals(0,run("zero","zero","float32","3.00000002"),output);assertTrue(output.contains("ZERO-FILLED SIGNAL"));assertTrue(output.contains("FLOAT32 ROUNDING"));assertTrue(output.contains("NO THRESHOLD PEAKS"));assertEquals("",Files.readString(directory.resolve("zero/peaks.bed")));
        assertEquals(0,run("background","zero","float64","0"),output);assertTrue(output.contains("NONPOSITIVE CUTOFF"));assertTrue(Files.readString(directory.resolve("background/analysis-status.json")).contains("completed-with-warnings"));assertTrue(Files.readString(directory.resolve("background/session.yaml")).contains("zero_filled_bases: 80"));
    }
    @Test void referenceOverlapAndNumericFailuresKeepRecoveryAndNoSuccessfulResults()throws Exception{
        fixture();Files.writeString(directory.resolve("signal"),"chr1\t0\t70\t3\nchr1\t60\t100\t4\n");
        assertEquals(2,run("overlap","zero","float32","3"),output);assertTrue(output.contains("Overlapping"));assertFalse(Files.exists(directory.resolve("overlap/peaks.bed")));
        Files.writeString(directory.resolve("signal"),"chrAlias\t0\t70\t3\n");assertEquals(2,run("alias","zero","float32","3"),output);assertTrue(output.contains("exact reference"));
        Files.writeString(directory.resolve("signal"),"chr1\t0\t100\t1e300\n");assertEquals(2,run("range","zero","float32","3"),output);assertTrue(output.contains("float32 range"));assertFalse(Files.exists(directory.resolve("range/results.tsv")));
        assertEquals(2,run("precision-choice","zero","guess","3"));assertFalse(Files.exists(directory.resolve("precision-choice")));
    }
}
