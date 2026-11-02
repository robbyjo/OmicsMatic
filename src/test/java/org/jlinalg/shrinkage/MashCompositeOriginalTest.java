/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class MashCompositeOriginalTest {
    private static Map<String,byte[]> evidence()throws Exception {
        byte[] raw;try(var input=MashCompositeOriginalTest.class.getResourceAsStream("mashr-composite/original-controls.zip")){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals(123711,raw.length);assertEquals("81599e89b3e56a97e1de74bc0c8132fa96e58e38aa838ccef307a27aa3527e8e",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var files=new HashMap<String,byte[]>();long total=0;try(var z=new ZipInputStream(new ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());assertFalse(e.getName().contains(".."));byte[] b=z.readNBytes(4_194_305);total+=b.length;assertTrue(b.length<=4_194_304&&total<=16_777_216);assertNull(files.put(e.getName(),b));}}return files;
    }
    private static String text(Map<String,byte[]> f,String n){assertTrue(f.containsKey(n),n);return new String(f.get(n),StandardCharsets.UTF_8);}
    private static double number(String x){return switch(x){case "NA","NaN"->Double.NaN;case "-Inf"->Double.NEGATIVE_INFINITY;case "Inf"->Double.POSITIVE_INFINITY;default->Double.parseDouble(x);};}
    private static double[] vector(Map<String,byte[]> f,String n){String s=text(f,n).strip();return s.isEmpty()?new double[0]:Stream.of(s.split("\\s+")).mapToDouble(MashCompositeOriginalTest::number).toArray();}
    private static double[][] matrix(Map<String,byte[]> f,String n,int rows,int cols){if(rows==0||cols==0){assertTrue(text(f,n).isBlank());return new double[rows][cols];}var lines=text(f,n).strip().lines().toList();assertEquals(rows,lines.size());double[][] m=new double[rows][];for(int r=0;r<rows;r++){m[r]=Stream.of(lines.get(r).strip().split("\\s+")).mapToDouble(MashCompositeOriginalTest::number).toArray();assertEquals(cols,m[r].length);}return m;}
    @TestFactory Stream<DynamicTest> unmodifiedOriginalFunctions()throws Exception {
        var f=evidence();assertEquals("ORIGINAL_DREAMLET_COMPOSITE_CONTROLS_COMPLETE",text(f,"original/STATUS.txt").strip());assertFalse(text(f,"original/unknown-regex-is-not-a-selector.txt").isBlank());var names=text(f,"original/cases.tsv").strip().lines().skip(1).toList();assertEquals(30,names.size());var tests=new ArrayList<DynamicTest>();
        for(String name:names)tests.add(DynamicTest.dynamicTest(name,()->{
            String root="original/"+name+"/";var settings=new HashMap<String,String>();text(f,root+"settings.tsv").lines().skip(1).forEach(line->{var row=line.split("\t");settings.put(row[0],row[1]);});int rows=Integer.parseInt(settings.get("rows")),c=Integer.parseInt(settings.get("conditions"));
            var input=matrix(f,root+"input.tsv",rows,c);int[] include=java.util.Arrays.stream(vector(f,root+"include.tsv")).mapToInt(x->(int)x-1).toArray(),exclude=java.util.Arrays.stream(vector(f,root+"exclude.tsv")).mapToInt(x->(int)x-1).toArray();
            var pattern=new MashCompositeScores.Pattern(name,include,exclude,settings.get("mode").equals("all")?MashCompositeScores.Mode.ALL:MashCompositeScores.Mode.AT_LEAST_ONE);
            var result=MashCompositeScores.calculate(input,c,List.of(pattern),settings.get("scale").equals("lfsr")?MashCompositeScores.Scale.LFSR:MashCompositeScores.Scale.SIGN_PROBABILITY,MashCompositeScores.Missing.SOURCE_ZERO_PROBABILITY,MashCompositeScores.Assumption.INDEPENDENT_MARGINAL_PROXIES,MashSharing.Limits.defaults(),()->{});
            double[] scores=vector(f,root+"original-score.tsv"),logs=vector(f,root+"stable-log.tsv"),missing=vector(f,root+"missing-occurrences.tsv"),inclusive=vector(f,root+"inclusive-005.tsv"),strict05=vector(f,root+"strict-05.tsv"),strict08=vector(f,root+"strict-08.tsv");assertEquals(rows,scores.length);assertEquals(rows,logs.length);
            for(int r=0;r<rows;r++){
                var actual=result.score(r,0);assertEquals(scores[r],actual.sourceScore(),1e-15+2e-15*Math.abs(scores[r]),name+" original score");if(Double.isInfinite(logs[r]))assertEquals(logs[r],actual.stableLogScore());else assertEquals(logs[r],actual.stableLogScore(),1e-12+2e-14*Math.abs(logs[r]),name+" stable log");assertEquals((int)missing[r],actual.missingSelectedOccurrences());assertEquals(inclusive[r]==1,actual.passes(.05,MashSharing.Boundary.INCLUSIVE));assertEquals(strict05[r]==1,actual.passes(.5,MashSharing.Boundary.STRICT));assertEquals(strict08[r]==1,actual.passes(.8,MashSharing.Boundary.STRICT));
            }
        }));return tests.stream();
    }
}
