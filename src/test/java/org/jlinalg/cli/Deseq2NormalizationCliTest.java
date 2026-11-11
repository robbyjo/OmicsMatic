/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

final class Deseq2NormalizationCliTest {
    @TempDir Path temp;
    private static final String COUNTS="gene\tα\t样本\ngα\t1\t4\ngβ\t9\t4\n";
    private record Run(int code,String output) { }
    private Run run(Path directory,Map<String,Object> changes)throws Exception {
        Files.createDirectories(directory);
        if(!Files.exists(directory.resolve("counts.tsv")))Files.writeString(directory.resolve("counts.tsv"),COUNTS,StandardCharsets.UTF_8);
        Map<String,Object> request=new LinkedHashMap<>(Map.of("schema_version",1,"mode","NORMALIZE","type","ratio","counts","counts.tsv","out","analysis"));request.putAll(changes);
        Path path=directory.resolve("request.json");Files.write(path,RsemJson.bytes(request));
        var output=new ByteArrayOutputStream();
        int code;
        try(var stream=new PrintStream(output,true,StandardCharsets.UTF_8)) {code=Deseq2NormalizationCli.run(new String[]{"--request",path.toString()},stream,stream);}
        return new Run(code,output.toString(StandardCharsets.UTF_8));
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> json(Path path)throws IOException {
        return (Map<String,Object>)SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));
    }
    @Test void preservesExactUnicodeIdentitiesAndOriginalNumericalScale()throws Exception {
        var result=run(temp,Map.of());assertEquals(0,result.code,result.output);
        Path analysis=temp.resolve("analysis");var status=json(analysis.resolve("analysis-status.json"));
        assertEquals("completed-with-warnings",status.get("status"));assertEquals(true,status.get("usable_for_differential_fit"));
        assertEquals(false,status.get("differential_inference_performed"));
        List<String> factors=Files.readAllLines(analysis.resolve("size-factors.tsv"),StandardCharsets.UTF_8);
        assertEquals("α",factors.get(1).split("\t")[0]);assertEquals("样本",factors.get(2).split("\t")[0]);
        assertEquals(Math.sqrt(0.75),Double.parseDouble(factors.get(1).split("\t")[1]),1e-14);
        String normalized=Files.readString(analysis.resolve("normalized-counts.tsv"),StandardCharsets.UTF_8);
        assertTrue(normalized.startsWith("gene\tα\t样本\ngα\t"));assertTrue(normalized.contains("\ngβ\t"));
        assertEquals(COUNTS,Files.readString(analysis.resolve("inputs/counts.tsv"),StandardCharsets.UTF_8));
        String warnings=Files.readString(analysis.resolve("WARNINGS.md"));
        assertTrue(warnings.contains("NATIVE NORMALIZATION ONLY"));assertTrue(warnings.contains("Required action:"));
        assertTrue(Files.exists(analysis.resolve("manifest.yaml")));
        String before=FollowupSupport.hash(analysis.resolve("normalized-counts.tsv"));
        var second=run(temp,Map.of());assertEquals(2,second.code);assertTrue(second.output.contains("Output already exists"));
        assertEquals(before,FollowupSupport.hash(analysis.resolve("normalized-counts.tsv")));
    }
    @Test void emptyControlsPreserveSourceNAAndForbidDownstreamFit()throws Exception {
        Files.writeString(temp.resolve("controls.tsv"),"gene\n");
        var result=run(temp,Map.of("controls","controls.tsv"));assertEquals(0,result.code,result.output);
        var status=json(temp.resolve("analysis/analysis-status.json"));assertEquals("completed-with-unusable-factors",status.get("status"));
        assertEquals(false,status.get("usable_for_differential_fit"));
        assertTrue(Files.readString(temp.resolve("analysis/size-factors.tsv")).contains("\tNA\t0\n"));
        assertTrue(Files.readString(temp.resolve("analysis/WARNINGS.md")).contains("UNDEFINED_SIZE_FACTOR"));
    }
    @Test void controlsAndGeometricMeansMatchByExactOrderedIdentities()throws Exception {
        Files.writeString(temp.resolve("controls.tsv"),"gene\ngβ\ngα\ngβ\n",StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("means.tsv"),"gene\tgeometric_mean\ngα\t1\ngβ\t2\n",StandardCharsets.UTF_8);
        var result=run(temp,Map.of("controls","controls.tsv","geometric_means","means.tsv"));assertEquals(0,result.code,result.output);
        var factors=Files.readAllLines(temp.resolve("analysis/size-factors.tsv"));
        assertTrue(factors.get(1).endsWith("\t3"));assertTrue(factors.get(2).endsWith("\t3"));
        double a=Double.parseDouble(factors.get(1).split("\t")[1]),b=Double.parseDouble(factors.get(2).split("\t")[1]);assertEquals(1,Math.sqrt(a*b),1e-14);
    }
    @TestFactory Stream<DynamicTest> invalidCountsProduceRetainedProminentFailures() {
        var cases=List.of("gene\tx\ty\ng\t1.5\t2\n","gene\tx\ty\ng\t-1\t2\n","gene\tx\ty\ng\tNaN\t2\n","gene\tx\ty\ng\t2147483648\t2\n",
            "gene\tx\tx\ng\t1\t2\n","gene\tx\ty\ng\t1\t2\ng\t2\t3\n","gene\tx\ty\ng\t1\n","gene\tx\ty\n","feature\tx\ty\ng\t1\t2\n","gene\tx\ty\ng\t0\t0\n");
        List<DynamicTest> tests=new ArrayList<>();
        for(int i=0;i<cases.size();i++){int n=i;tests.add(DynamicTest.dynamicTest("invalid-counts-"+i,()->{
            Path dir=temp.resolve("invalid-"+n);Files.createDirectories(dir);Files.writeString(dir.resolve("counts.tsv"),cases.get(n));
            var result=run(dir,Map.of());assertEquals(2,result.code,result.output);
            var status=json(dir.resolve("analysis/analysis-status.json"));assertEquals("FAIL2_NO_NORMALIZATION_RESULT_ADMITTED",status.get("status"));
            String warnings=Files.readString(dir.resolve("analysis/WARNINGS.md"));assertTrue(warnings.startsWith("# FAIL2"));assertTrue(warnings.contains("Required action:"));assertTrue(warnings.contains("not admitted results"));
        }));}
        return tests.stream();
    }
    @TestFactory Stream<DynamicTest> schemaIdentityAndCapacityFailures() {
        List<Map<String,Object>> cases=List.of(Map.of("surprise",1),Map.of("type","iterate"),Map.of("maximum_entries",3),
            Map.of("maximum_input_bytes",1),Map.of("maximum_output_bytes",10),Map.of("maximum_work",1),Map.of("scratch_entries",1),
            Map.of("schema_version",2),Map.of("mode","WALD"),Map.of("maximum_entries",1.5),Map.of("geometric_means","means.tsv"),Map.of("controls","controls.tsv"));
        List<DynamicTest> tests=new ArrayList<>();
        for(int i=0;i<cases.size();i++){int n=i;tests.add(DynamicTest.dynamicTest("invalid-request-"+i,()->{
            Path dir=temp.resolve("request-"+n);Files.createDirectories(dir);
            Files.writeString(dir.resolve("means.tsv"),"gene\tgeometric_mean\ngβ\t1\ngα\t2\n",StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("controls.tsv"),"gene\tbad\ngα\n",StandardCharsets.UTF_8);
            var result=run(dir,cases.get(n));assertEquals(2,result.code,result.output);assertTrue(result.output.contains("FAIL2"));assertTrue(result.output.contains("NEW output path"));
        }));}
        return tests.stream();
    }
}
