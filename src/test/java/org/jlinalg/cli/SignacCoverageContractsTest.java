/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SignacCoverageContractsTest {
    @TempDir Path temp;
    static Map<String,Object> request(){var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","COVERAGE");r.put("source_profile","Signac-1.9.0");r.put("genome_build","synthetic-not-biological");r.put("out","result");r.put("region",Map.of("chromosome","chr1","start",100,"end",120));r.put("cell_metadata","cells.tsv");r.put("sources",List.of(Map.of("fragments","fragments.tsv")));return r;}
    static Stream<Arguments> invalid(){return Stream.of(Arguments.of("schema_version",1.5),Arguments.of("mode","ANALYZE"),Arguments.of("source_profile","latest"),Arguments.of("genome_build",""),Arguments.of("sources",List.of()),Arguments.of("region",Map.of("chromosome","chr1","start",120,"end",100)),Arguments.of("window",0),Arguments.of("window",1.1),Arguments.of("downsample_rate",1.1),Arguments.of("maximum_downsample",-1),Arguments.of("group_levels",List.of("A","A")),Arguments.of("scale_factor",-1),Arguments.of("ymax","p95"),Arguments.of("ymax","q101"),Arguments.of("ymax",-1),Arguments.of("annotation_mode","transcript"),Arguments.of("maximum_output_bytes",0),Arguments.of("maximum_smoothing_operations",0),Arguments.of("plot_width",500),Arguments.of("extra",true));}
    @ParameterizedTest @MethodSource("invalid") void invalidOrIgnoredSettingsAreRefused(String key,Object value){var r=request();r.put(key,value);assertThrows(IOException.class,()->SignacCoverageRequest.validate(r));}
    @Test void captureReplayAndTamperedVectorRefusal()throws Exception{
        Files.writeString(temp.resolve("fragments.tsv"),"chr1\t98\t103\ta\t1\nchr1\t99\t104\tb\t99\nchr1\t102\t107\ta\t3\n");Files.writeString(temp.resolve("cells.tsv"),"cell\tgroup\tn_count\na\tA\t17\nb\tB\t34\n");
        Files.writeString(temp.resolve("annotation.tsv"),"chromosome\tstart\tend\tstrand\tgene_name\ttx_id\tgene_biotype\ttype\nchr1\t101\t108\t+\tGene&A\ttx1\tprotein_coding\texon\nchr1\t111\t119\t+\tGene&A\ttx1\tprotein_coding\texon\n");
        var r=request();r.put("window",1);r.put("downsample_rate",1);r.put("annotation","annotation.tsv");Path file=temp.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();var stream=new PrintStream(log,true,StandardCharsets.UTF_8);
        assertEquals(0,SignacCoverageCli.run(new String[]{"--request",file.toString()},stream,stream),log.toString(StandardCharsets.UTF_8));Path output=temp.resolve("result");assertTrue(Files.readString(output.resolve("coverage.svg")).contains("Gene&amp;A"));assertTrue(Files.size(output.resolve("coverage.png"))>1000);SignacCoverageCli.verify(output);
        assertEquals(2,SignacCoverageCli.run(new String[]{"--request",file.toString()},stream,stream));assertTrue(Files.readString(output.resolve("analysis-status.json")).contains("completed-with-warnings"));
        Path svg=output.resolve("coverage.svg");Files.writeString(svg,Files.readString(svg).replace("Gene&amp;A","Altered"));SignacPeaksCli.seal(output);assertThrows(IOException.class,()->SignacCoverageCli.verify(output));
    }
    @Test void missingInputsRetainFailureAndRefuseVerification()throws Exception{Path file=temp.resolve("bad.json");SeuratDifferentialContracts.json(file,request());var log=new ByteArrayOutputStream();var stream=new PrintStream(log,true,StandardCharsets.UTF_8);assertEquals(2,SignacCoverageCli.run(new String[]{"--request",file.toString()},stream,stream));assertTrue(Files.readString(temp.resolve("result/analysis-status.json")).contains("FAIL2_NO_RESULT_ADMITTED"));assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));assertThrows(IOException.class,()->SignacCoverageCli.verify(temp.resolve("result")));assertTrue(log.toString(StandardCharsets.UTF_8).contains("REQUIRED ACTIONS"));}
}
