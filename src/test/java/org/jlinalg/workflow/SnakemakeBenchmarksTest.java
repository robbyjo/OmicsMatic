/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeBenchmarksTest {
    @TempDir Path temp;
    private static final String HEADER = String.join("\t", SnakemakeBenchmarks.STANDARD) + "\n";
    private static final SnakemakeBenchmarks.Limits LIMITS = new SnakemakeBenchmarks.Limits(1_000_000, 1000, 10_000);
    private static String row(String seconds, String rss, String cpu) { return seconds+"\t0:00:01\t"+rss+"\t100\t10\t8\t0\t0\t0.5\t"+cpu+"\n"; }
    private static List<SnakemakeBenchmarks.Measurement> parse(String text) throws IOException { return SnakemakeBenchmarks.read(new StringReader(text), LIMITS); }
    private static SnakemakeBenchmarks.Assignment job(String pipeline, String repeat, String job, String file, int row) { return new SnakemakeBenchmarks.Assignment(pipeline,repeat,job,file,row); }
    private static void decimal(String expected, BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual)); }

    @Test void addsRecordedCpuExactlyAndUsesMaximumJobMemoryRatherThanSum() throws Exception {
        var first=parse(HEADER+row("1.5","32.25","0.10"));var second=parse(HEADER+row("4.25","17.50","0.20"));
        var summary=SnakemakeBenchmarks.aggregate(Map.of("a",first,"b",second),List.of(job("LaDDR","1","count","a",1),job("LaDDR","1","fit","b",1))).get(new SnakemakeBenchmarks.Group("LaDDR","1"));
        assertEquals(2,summary.jobs());decimal("0.30",summary.cpuSeconds());decimal("32.25",summary.maxJobRssMiB());decimal("5.75",summary.summedJobWallSeconds());
    }
    @Test void preservesBenchmarkRepeatsAndDifferentPipelineJobSets() throws Exception {
        var values=parse(HEADER+row("2","10","1")+row("4","20","3"));
        var result=SnakemakeBenchmarks.aggregate(Map.of("repeat.tsv",values),List.of(job("A","first","align","repeat.tsv",1),job("A","second","align","repeat.tsv",2)));
        assertEquals(2,result.size());decimal("1",result.get(new SnakemakeBenchmarks.Group("A","first")).cpuSeconds());decimal("3",result.get(new SnakemakeBenchmarks.Group("A","second")).cpuSeconds());
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("repeat.tsv",values),List.of(job("A","first","align","repeat.tsv",1))));
    }
    @Test void rejectsDoubleCountingTheSameRowAcrossGroupsAndDuplicateJobs() throws Exception {
        var single=parse(HEADER+row("1","20","0.3"));var twice=parse(HEADER+row("1","20","0.3")+row("1","20","0.4"));
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("a",single),List.of(job("A","1","j1","a",1),job("B","1","j2","a",1))));
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("a",twice),List.of(job("A","1","j1","a",1),job("A","1","j1","a",2))));
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("a",twice),List.of(job("A","1","j1","a",1),job("A","1","j2","a",2))));
    }
    @Test void absentRowTableAndEmptyInputCannotProduceAggregate() throws Exception {
        var one=parse(HEADER+row("1","2","3"));
        for(var assignment:List.of(job("A","1","j","absent",1),job("A","1","j","a",2)))assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("a",one),List.of(assignment)));
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of(),List.of()));
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("a",List.of()),List.of(job("A","1","j","a",1))));
    }
    @Test void originalNaMissingAndNonfiniteMetricsAreNeverZeroImputed() {
        for(String value:List.of("NA","-","NaN","nan","Inf","Infinity","-1","1e1000000"," 1")){
            assertThrows(IOException.class,()->parse(HEADER+row("1",value,"0")),value);
            assertThrows(IOException.class,()->parse(HEADER+row("1","1",value)),value);
            assertThrows(IOException.class,()->parse(HEADER+row(value,"1","0")),value);
        }
    }
    @Test void unusedUnavailableIoCanRemainUnavailableWithoutChangingUsedMetrics() throws Exception {
        var values=parse(HEADER+row("1","2","3").replace("\t0\t0\t0.5\t","\t-\tNA\t0.5\t"));decimal("2",values.get(0).maxRssMiB());
    }
    @Test void standardExtendedAndCrLfFormatsKeepTheSameMetrics() throws Exception {
        String body=row("2","11.22","1.23");var regular=parse(HEADER+body);
        String extended=String.join("\t",SnakemakeBenchmarks.EXTENDED)+"\n"+body.stripTrailing()+"\t(1,)\t('align',)\t{}\t{}\t(2,)\t0.0\t{}\t{}\n";
        assertEquals(regular,parse(extended));assertEquals(regular,parse((HEADER+body).replace("\n","\r\n")));
    }
    @Test void unsupportedHeadersUnterminatedRowsAndLateMalformedRowsFailWholeTable() {
        for(String text:List.of("",HEADER,HEADER.replace("cpu_time","cpu"),HEADER+row("1","2","3").stripTrailing(),HEADER+row("1","2","3")+"1\tbroken\n",HEADER+"\n",HEADER+row("1","2","3").replace("0:00:01\t","0:00:01\textra\t")))assertThrows(IOException.class,()->parse(text));
    }
    @Test void inputRowLineAndByteCapacitiesNeverTruncate() throws Exception {
        String text=HEADER+row("1","2","3")+row("1","2","3");
        assertThrows(IOException.class,()->SnakemakeBenchmarks.read(new StringReader(text),new SnakemakeBenchmarks.Limits(10000,1,1000)));
        assertThrows(IOException.class,()->SnakemakeBenchmarks.read(new StringReader(text),new SnakemakeBenchmarks.Limits(10000,10,8)));
        Path file=Files.writeString(temp.resolve("benchmark.tsv"),text);assertThrows(IOException.class,()->SnakemakeBenchmarks.read(file,new SnakemakeBenchmarks.Limits(8,10,1000)));
        assertThrows(IOException.class,()->SnakemakeBenchmarks.read(temp,LIMITS));
    }
    @Test void malformedUtf8IsRejectedRatherThanReplaced() throws Exception {
        byte[] raw=(HEADER+row("1","2","3")).getBytes(java.nio.charset.StandardCharsets.UTF_8);byte[] invalid=java.util.Arrays.copyOf(raw,raw.length+2);invalid[raw.length]=(byte)0xc3;invalid[raw.length+1]=10;
        Path file=Files.write(temp.resolve("bad.tsv"),invalid);assertThrows(IOException.class,()->SnakemakeBenchmarks.read(file,LIMITS));
    }
    @Test void cancellationDoesNotReturnAPrefix() {
        Thread.currentThread().interrupt();try { assertThrows(java.util.concurrent.CancellationException.class,()->parse(HEADER+row("1","2","3"))); }finally { assertTrue(Thread.interrupted()); }
    }
    @Test void negativeMetricsControlIdentifiersAndNonpositiveRowsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new SnakemakeBenchmarks.Measurement(BigDecimal.ZERO,BigDecimal.ONE,new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,()->job("A\tB","1","job","a",1));assertThrows(IllegalArgumentException.class,()->job("A","1","job","a",0));
        assertThrows(IllegalArgumentException.class,()->new SnakemakeBenchmarks.Limits(0,1,1));
    }
}
