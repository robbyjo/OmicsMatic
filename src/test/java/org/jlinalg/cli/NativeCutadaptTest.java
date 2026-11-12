/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.CutadaptTrimmer;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeCutadaptTest {
    @TempDir Path directory;
    private String diagnostics;
    @Test void nativeCoordinatesScoresAndEveryTrimmedBaseMatch7152OriginalControls()throws Exception {
        int cases=0;
        try(var raw=getClass().getResourceAsStream("/cutadapt-native-controls.tsv.gz")) {
            assertNotNull(raw);try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(raw),StandardCharsets.UTF_8))) {
                assertTrue(reader.readLine().startsWith("adapter\terror_rate"));String line;
                while((line=reader.readLine())!=null) {
                    String[] row=line.split("\t",-1);assertEquals(10,row.length);
                    var trimmer=new CutadaptTrimmer(row[0],Double.parseDouble(row[1]),Integer.parseInt(row[2]));
                    var result=trimmer.trim(new FastqIO.Read("@source",row[3],row[4]),Integer.parseInt(row[5]),Integer.parseInt(row[6]),cells->{});
                    assertEquals(row[7],result.read().sequence(),"source case"+cases+": "+line);assertEquals(row[8],result.read().quality(),"source case"+cases);
                    var m=result.match();String actual=m==null?".":m.adapterStart()+","+m.adapterEnd()+","+m.readStart()+","+m.readEnd()+","+m.score()+","+m.errors();
                    assertEquals(row[9],actual,"source case"+cases+": "+line);cases++;
                }
            }
        }
        assertEquals(7152,cases);
    }
    private int run(String name,Path first,Path second,String... flags)throws Exception {
        var args=new ArrayList<>(List.of("sequence-qc","--engine","native","--method","cutadapt","--reads",first.toString(),"--adapter3","AGATCGGAAGAGC","--out",directory.resolve(name).toString(),"--no-config"));
        if(second!=null)args.addAll(List.of("--reads2",second.toString(),"--adapter3-2","ACGT"));args.addAll(List.of(flags));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);diagnostics=bytes.toString();
        Path warning=directory.resolve(name+"/WARNINGS.md");if(code!=0&&Files.exists(warning))diagnostics+=Files.readString(warning);return code;
    }
    private static String record(String id,String sequence){return "@"+id+"\n"+sequence+"\n+\n"+"I".repeat(sequence.length())+"\n";}
    @Test void pairedFilteringPreservesIdentityAndNativeTrimReportsAggregate()throws Exception {
        Path a=directory.resolve("R1.fastq"),b=directory.resolve("R2.fastq");
        Files.writeString(a,record("one/1","TTAGATCGGAAGAGC")+record("two/1","GGCCTTAGATCGGAAGAGC"));Files.writeString(b,record("one/2","GGGGACGT")+record("two/2","TTTTACGT"));
        assertEquals(0,run("trim",a,b,"--minimum-length","4","--error-rate","0"),diagnostics);
        try(var r1=new FastqIO.Reader(directory.resolve("trim/trimmed_R1.fastq.gz"));var r2=new FastqIO.Reader(directory.resolve("trim/trimmed_R2.fastq.gz"))) {
            assertEquals("GGCCTT",r1.next().sequence());assertEquals("TTTT",r2.next().sequence());assertNull(r1.next());assertNull(r2.next());
        }
        assertTrue(Files.readString(directory.resolve("trim/read-dispositions.tsv")).contains("false"));
        Path mapping=directory.resolve("reports.tsv");Files.writeString(mapping,"sample_id\tfastqc_zip\tcutadapt_json\nS1\t\ttrim/cutadapt.json\n");
        var stream=new PrintStream(new ByteArrayOutputStream());assertEquals(0,JLinAlgCli.run(new String[]{"sequence-qc","--engine","native","--method","multiqc","--reports",mapping.toString(),"--out",directory.resolve("report").toString(),"--no-config"},stream,stream));
        assertTrue(Files.readString(directory.resolve("report/results.tsv")).contains("native-adapter-trimming\t2\t1"));
    }
    @Test void operationCapacityOrMismatchedPairsAdmitNoOutputFastq()throws Exception {
        Path a=directory.resolve("a.fastq"),b=directory.resolve("b.fastq");Files.writeString(a,record("one","TTTTAGATCGGAAGAGC"));Files.writeString(b,record("different","AAAA"));
        assertEquals(2,run("capacity",a,null,"--alignment-operations","1"),diagnostics);assertFalse(Files.exists(directory.resolve("capacity/trimmed_R1.fastq.gz")));assertTrue(Files.exists(directory.resolve("capacity/partial/trimmed_R1.fastq.gz")));
        assertEquals(2,run("pair",a,b),diagnostics);assertFalse(Files.exists(directory.resolve("pair/results.tsv")));
        assertTrue(Files.readString(directory.resolve("pair/analysis-status.json")).contains("\"result_admitted\":false"));
    }
    @Test void actualOriginalCliAndNativePipelineProduceTheSameRetainedReads()throws Exception {
        String python=System.getenv("JLINALG_SEQUENCE_PYTHON");assumeTrue(python!=null,"Optional Cutadapt5.2 CLI reference");
        Path input=directory.resolve("input.fastq");Files.writeString(input,record("one","TTTAGATCGGAAGAGC")+record("two","AGATC")+"@quality\nACGTTTT\n+\nIII!!!!\n");
        assertEquals(0,run("native",input,null,"--minimum-length","2","--quality-cutoff","20","--cut-left","1","--error-rate","0"),diagnostics);
        var stream=new PrintStream(new ByteArrayOutputStream());assertEquals(0,JLinAlgCli.run(new String[]{"sequence-qc","--method","cutadapt","--python",python,"--reads",input.toString(),"--adapter3","AGATCGGAAGAGC","--minimum-length","2","--quality-cutoff","20","--cut-left","1","--error-rate","0","--out",directory.resolve("original").toString(),"--no-config"},stream,stream));
        try(var nativeReads=new FastqIO.Reader(directory.resolve("native/trimmed_R1.fastq.gz"));var originalReads=new FastqIO.Reader(directory.resolve("original/trimmed_R1.fastq.gz"))) {
            FastqIO.Read actual;while((actual=originalReads.next())!=null)assertEquals(actual,nativeReads.next());assertNull(nativeReads.next());
        }
        assertEquals(Files.readString(directory.resolve("original/results.tsv")),Files.readString(directory.resolve("native/results.tsv")));
    }
}
