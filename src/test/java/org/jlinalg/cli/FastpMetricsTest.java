/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class FastpMetricsTest {
    @TempDir static Path temporary;
    static Path controls;
    @BeforeAll static void stage()throws IOException {controls=temporary.resolve("controls.zip");try(var input=FastpMetricsTest.class.getResourceAsStream("fastp-output/original-output-controls.zip")){assertNotNull(input);Files.copy(input,controls);}}
    private static byte[] entry(ZipFile z,String name)throws IOException {try(var in=z.getInputStream(z.getEntry(name))){return in.readAllBytes();}}
    private static Map<?,?> object(byte[] value)throws IOException {return FastpRuntime.object(SimpleJson.parse(FastpFiles.utf8(value)));}
    @TestFactory Stream<DynamicTest> completeOriginalStatisticsForAll57NewProcessingControls()throws Exception {
        List<?> cases;try(var z=new ZipFile(controls.toFile())){cases=(List<?>)object(entry(z,"CASES.json")).get("cases");}assertEquals(57,cases.size());
        return cases.stream().map(value->{try{Map<?,?> row=FastpRuntime.object(value);String name=FastpRuntime.text(row.get("path")),version=FastpRuntime.text(row.get("version"));return DynamicTest.dynamicTest(name,()->checkCase(name,version));}catch(IOException invalid){throw new IllegalArgumentException(invalid);}});
    }
    static void checkCase(String name,String version)throws Exception {
        Path root=Files.createTempDirectory(temporary,"case-");String prefix=name+"/result/";
        try(var z=new ZipFile(controls.toFile())){
            Map<?,?> request=object(entry(z,prefix+"request.json")),report=object(entry(z,prefix+"original/fastp.json"));List<?> captures=(List<?>)SimpleJson.parse(FastpFiles.utf8(entry(z,prefix+"input-captures.json")));
            var before=new ArrayList<FastpMetrics.Model>();var after=new ArrayList<FastpMetrics.Model>();
            var fq=new FastpFastq.Limits(1048576,4194304,100000,1000000,10000,1000);var capacity=new FastpMetrics.Limits(10000,2_097_152);
            for(int i=0;i<captures.size();i++){
                Map<?,?> capture=FastpRuntime.object(captures.get(i));String member=FastpRuntime.text(capture.get("snapshot"));Path input=root.resolve("before-"+i);Files.write(input,entry(z,prefix+member));Path output=root.resolve("after-"+i);Files.write(output,entry(z,prefix+"original/read"+(i+1)+".fastq"));
                before.add(FastpMetrics.collect(input,fq,FastpFastq.Encoding.valueOf(FastpRuntime.text(request.get("encoding"))),FastpFastq.Alphabet.valueOf(FastpRuntime.text(request.get("alphabet"))),FastpFastq.Plus.valueOf(FastpRuntime.text(request.get("plus_policy"))),capacity,false,()->{}));
                after.add(FastpMetrics.collect(output,fq,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.LITERAL_ASCII,FastpFastq.Plus.SOURCE_IGNORED,capacity,true,()->{}));
                assertTrue(before.get(i).verifyDetail(FastpRuntime.object(report.get("read"+(i+1)+"_before_filtering")),version)>1024);
                assertTrue(after.get(i).verifyDetail(FastpRuntime.object(report.get("read"+(i+1)+"_after_filtering")),version)>1024);
            }
            Map<?,?> summary=FastpRuntime.object(report.get("summary"));assertEquals(version,summary.get("fastp_version"));
            FastpMetrics.verifySummary(FastpRuntime.object(summary.get("before_filtering")),before.get(0),before.size()==1?null:before.get(1));FastpMetrics.verifySummary(FastpRuntime.object(summary.get("after_filtering")),after.get(0),after.size()==1?null:after.get(1));
        }
    }
    @Test void sixSignificantDigitsAreCheckedWithoutTreatingMissingTrailingZerosAsLowPrecision()throws Exception {
        FastpMetrics.rounded(0.571429,4.0/7,"rate");assertThrows(IOException.class,()->FastpMetrics.rounded(0.57143,4.0/7,"rate"));
        assertThrows(IOException.class,()->FastpMetrics.rounded(0.500002,0.5,"rate"));assertThrows(IOException.class,()->FastpMetrics.rounded(1e-10,0,"source-zero"));assertThrows(IOException.class,()->FastpMetrics.rounded(Double.NaN,0,"invalid"));
    }
    @Test void completeCycleAndKmerPopulationsCannotBeShortenedOrRehashedToHideChanges()throws Exception {
        Path input=temporary.resolve("native.fq");Files.writeString(input,"@a\nACGTAC\n+\nIIIIII\n");var model=FastpMetrics.collect(input,new FastpFastq.Limits(1000,1000,100,100,100,100),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,new FastpMetrics.Limits(100,65536+144*100),false,()->{});
        try(var z=new ZipFile(controls.toFile())){
            Map<?,?> data=object(entry(z,"0.23.2/se-default/result/original/fastp.json"));assertThrows(IOException.class,()->model.verifyDetail(FastpRuntime.object(data.get("read1_before_filtering")),"0.23.2"));
        }
    }
    @Test void cycleCapacityFailsInsteadOfReturningPrefixStatistics()throws Exception {
        Path input=temporary.resolve("over.fq");Files.writeString(input,"@a\nACGTAC\n+\nIIIIII\n");assertThrows(IOException.class,()->FastpMetrics.collect(input,new FastpFastq.Limits(1000,1000,100,100,100,100),FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,new FastpMetrics.Limits(5,65536+144*5),false,()->{}));
    }
}
