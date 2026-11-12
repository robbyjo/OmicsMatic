/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jlinalg.sequence.FastqQualityMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeQcReportsTest {
    @TempDir Path directory;
    private String diagnostics;
    private int run(String method,String name,String... flags)throws Exception {
        var args=new ArrayList<>(List.of("sequence-qc","--method",method,"--engine","native","--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(flags));
        return command(args);
    }
    private int command(List<String> args)throws Exception {
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);diagnostics=bytes.toString();return code;
    }
    private Path fastq()throws Exception {
        Path path=directory.resolve("reads.fastq");Files.writeString(path,"@one\nACGN\n+\n!+5?\n@two\ntg\n+\nI5\n@three\nN\n+\n!\n");return path;
    }
    @Test void everyBaseContributesToRawCountsAndEmpiricalQualityDistributions()throws Exception {
        var report=FastqQualityMetrics.inspect(fastq(),4);
        assertEquals(3,report.reads());assertEquals(7,report.bases());assertEquals(1,report.minLength());assertEquals(4,report.maxLength());
        assertEquals(Map.of(1,1L,2,1L,4,1L),report.lengths());assertEquals(Map.of(0,1L,15,1L,30,1L),report.meanQualities());
        assertEquals(Map.of(50,1L,67,1L),report.gcPercent());
        var p=report.positions().get(0);assertEquals(3,p.observations());assertEquals(40.0/3,p.mean(),1e-14);assertEquals(0,p.median());assertEquals(40,p.q90());
        assertEquals(1,p.a());assertEquals(1,p.t());assertEquals(1,p.n());
        assertThrows(UnsupportedOperationException.class,()->report.positions().clear());
    }
    @Test void nativeQcPublishesExplicitInfoAndCanBeAggregatedWithoutPython()throws Exception {
        Path reads=fastq();assertEquals(0,run("fastqc","qc","--reads",reads.toString()),diagnostics);
        assertTrue(Files.readString(directory.resolve("qc/results.tsv")).contains("read1\treads.fastq\t3\t7\t1\t4\tINFO"));
        Path manifest=directory.resolve("reports.tsv");Files.writeString(manifest,"sample_id\tfastqc_zip\tcutadapt_json\nS1\tqc/read1_native_qc.zip\t\n");
        assertEquals(0,run("multiqc","aggregate","--reports",manifest.toString()),diagnostics);
        assertTrue(Files.readString(directory.resolve("aggregate/results.tsv")).contains("S1\tnative-fastq-qc\t3"));
        String html=Files.readString(directory.resolve("aggregate/multiqc_report.html"));assertTrue(html.contains("per-base.tsv"));assertTrue(html.contains("INFO"));assertFalse(html.contains("<script"));
    }
    @Test void completeSourceWarningsAndHtmlSensitiveDataSurviveAggregation()throws Exception {
        Path zip=directory.resolve("source.zip");String filename="<script>alert(1)</script>.fastq";
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("source/fastqc_data.txt"));out.write(("##FastQC\t0.13.0\n>>Basic Statistics\tpass\nFilename\t"+filename+"\nTotal Sequences\t2\n>>END_MODULE\n>>Per base sequence quality\tfail\n1\t0\n>>END_MODULE\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            out.putNextEntry(new ZipEntry("source/summary.txt"));out.write(("PASS\tBasic Statistics\t"+filename+"\nFAIL\tPer base sequence quality\t"+filename+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
        }
        Path cutadapt=directory.resolve("trim.json");Files.writeString(cutadapt,"{\"tag\":\"Cutadapt report\",\"cutadapt_version\":\"5.2\",\"read_counts\":{\"input\":2,\"output\":0}}");
        Path mapping=directory.resolve("reports.tsv");Files.writeString(mapping,"sample_id\tfastqc_zip\tcutadapt_json\nS1\tsource.zip\ttrim.json\n");
        assertEquals(0,run("multiqc","aggregate","--reports",mapping.toString()),diagnostics);
        String warnings=Files.readString(directory.resolve("aggregate/WARNINGS.md"));assertTrue(warnings.contains("FAIL - Per base sequence quality"));assertTrue(warnings.contains("retained no reads/pairs"));
        String report=Files.readString(directory.resolve("aggregate/multiqc_report.html"));assertTrue(report.contains("&lt;script&gt;alert(1)"));assertFalse(report.contains("<script>"));
        assertEquals(Files.readAllBytes(zip).length,Files.readAllBytes(directory.resolve("aggregate/source-reports/S1.qc.zip")).length);
    }
    @Test void capacityMalformedPairsAndDuplicateSamplesFailWithoutAdmittedTables()throws Exception {
        Path reads=fastq();assertEquals(2,run("fastqc","small","--reads",reads.toString(),"--maximum-positions","2"),diagnostics);assertFalse(Files.exists(directory.resolve("small/results.tsv")));
        Path mate=directory.resolve("mate.fastq");Files.writeString(mate,"@different\nA\n+\nI\n");
        assertEquals(2,run("fastqc","pair","--reads",reads.toString(),"--reads2",mate.toString()),diagnostics);assertFalse(Files.exists(directory.resolve("pair/results.tsv")));
        assertEquals(0,run("fastqc","qc","--reads",reads.toString()),diagnostics);
        Path mapping=directory.resolve("reports.tsv");Files.writeString(mapping,"sample_id\tfastqc_zip\tcutadapt_json\nS1\tqc/read1_native_qc.zip\t\ns1\tqc/read1_native_qc.zip\t\n");
        assertEquals(2,run("multiqc","duplicate","--reports",mapping.toString()),diagnostics);assertFalse(Files.exists(directory.resolve("duplicate/results.tsv")));
        assertTrue(Files.readString(directory.resolve("duplicate/analysis-status.json")).contains("\"result_admitted\":false"));
    }
    @Test void zeroLengthAndEmptyFilesHaveNoFabricatedQualityOrGcValues()throws Exception {
        Path reads=directory.resolve("zero.fastq");Files.writeString(reads,"@empty\n\n+\n\n");var report=FastqQualityMetrics.inspect(reads,10);
        assertEquals(1,report.reads());assertEquals(0,report.bases());assertTrue(report.meanQualities().isEmpty());assertTrue(report.gcPercent().isEmpty());assertTrue(report.positions().isEmpty());
        Files.writeString(reads,"");assertEquals(0,FastqQualityMetrics.inspect(reads,10).reads());
    }
    @Test void originalFastqcAndMultiqcAgreeOnSampleCountsAndPreservedFindings()throws Exception {
        String python=System.getenv("JLINALG_SEQUENCE_PYTHON"),home=System.getenv("JLINALG_FASTQC_HOME");assumeTrue(python!=null&&home!=null,"Optional actual FastQC/MultiQC reference");
        Path reads=fastq();
        assertEquals(0,command(List.of("sequence-qc","--method","fastqc","--fastqc-home",home,"--reads",reads.toString(),"--out",directory.resolve("original-qc").toString(),"--no-config")),diagnostics);
        Path mapping=directory.resolve("reports.tsv");Files.writeString(mapping,"sample_id\tfastqc_zip\tcutadapt_json\nS1\toriginal-qc/reads_fastqc.zip\t\n");
        assertEquals(0,command(List.of("sequence-qc","--method","multiqc","--python",python,"--reports",mapping.toString(),"--out",directory.resolve("original-aggregate").toString(),"--no-config")),diagnostics);
        assertEquals(0,run("multiqc","native-aggregate","--reports",mapping.toString()),diagnostics);
        var original=DelimitedData.read(directory.resolve("original-aggregate/results.tsv"));var nativeResult=DelimitedData.read(directory.resolve("native-aggregate/results.tsv"));
        assertEquals(original.rows().get(0)[2],nativeResult.rows().get(0)[2]);
        assertEquals(Files.readString(directory.resolve("original-aggregate/source-qc.tsv")),Files.readString(directory.resolve("native-aggregate/source-qc.tsv")));
    }
}
