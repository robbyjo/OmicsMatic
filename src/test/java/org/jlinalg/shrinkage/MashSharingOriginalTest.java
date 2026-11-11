/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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

class MashSharingOriginalTest {
    private static Map<String,byte[]> evidence()throws Exception {
        byte[] raw;try(var input=MashSharingOriginalTest.class.getResourceAsStream("mashr-sharing/original-controls.zip")){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals(226555,raw.length);assertEquals("2f624e44d5eb0e52b2b5a608ab656e392eb36aa2bb05bfb4e2934bee7f6f85f5",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        var files=new HashMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){assertFalse(entry.isDirectory());assertFalse(entry.getName().contains(".."));byte[] bytes=zip.readNBytes(8_388_609);assertTrue(bytes.length<=8_388_608);total+=bytes.length;assertTrue(total<=33_554_432);assertNull(files.put(entry.getName(),bytes));}}
        assertEquals("ORIGINAL_MASHR_SHARING_AND_EXPLICIT_MARGINAL_PRODUCT_CONTROLS_COMPLETE",text(files,"original/STATUS.txt").strip());return files;
    }
    private static String text(Map<String,byte[]> f,String n){assertTrue(f.containsKey(n),n);return new String(f.get(n),StandardCharsets.UTF_8);}
    private static double number(String x){return switch(x){case "NA","NaN"->Double.NaN;case "-Inf"->Double.NEGATIVE_INFINITY;case "Inf"->Double.POSITIVE_INFINITY;default->Double.parseDouble(x);};}
    private static double[] vector(Map<String,byte[]> f,String n){String s=text(f,n).strip();return s.isEmpty()?new double[0]:Stream.of(s.split("\\s+")).mapToDouble(MashSharingOriginalTest::number).toArray();}
    private static double[][] matrix(Map<String,byte[]> f,String n,int rows,int cols){if(rows==0){assertTrue(text(f,n).isBlank());return new double[0][cols];}var lines=text(f,n).strip().lines().toList();assertEquals(rows,lines.size(),n);double[][] m=new double[rows][];for(int i=0;i<rows;i++){m[i]=Stream.of(lines.get(i).strip().split("\\s+")).mapToDouble(MashSharingOriginalTest::number).toArray();assertEquals(cols,m[i].length,n);}return m;}
    private static void near(double expected,double actual,String label){if(Double.isNaN(expected))assertTrue(Double.isNaN(actual),label);else if(Double.isInfinite(expected))assertEquals(expected,actual,label);else assertEquals(expected,actual,1e-14+2e-15*Math.abs(expected),label);}
    @TestFactory Stream<DynamicTest> originalFunctions()throws Exception {
        var files=evidence();var cases=text(files,"original/cases.tsv").strip().lines().skip(1).toList();assertEquals(32,cases.size());var tests=new ArrayList<DynamicTest>();
        for(String name:cases)tests.add(DynamicTest.dynamicTest(name,()->{
            String root="original/"+name+"/";var s=new HashMap<String,Double>();text(files,root+"settings.tsv").lines().skip(1).forEach(line->{var p=line.split("\t");s.put(p[0],Double.parseDouble(p[1]));});int n=s.get("rows").intValue(),c=s.get("conditions").intValue();
            var means=matrix(files,root+"means.tsv",n,c);var sig=matrix(files,root+"lfsr.tsv",n,c);var nominal=matrix(files,root+"nominal-p.tsv",n,c);int[] selection=java.util.Arrays.stream(vector(files,root+"selection.tsv")).mapToInt(v->(int)v-1).toArray();
            var limits=MashSharing.Limits.defaults();Runnable check=()->{};
            var strict=MashSharing.significance(sig,c,selection,s.get("threshold"),MashSharing.Boundary.STRICT,limits,check);
            assertArrayEquals(java.util.Arrays.stream(vector(files,root+"original-selected-rows.tsv")).mapToInt(v->(int)v-1).toArray(),strict.selectedRows());
            assertArrayEquals(java.util.Arrays.stream(vector(files,root+"original-significant-counts.tsv")).mapToInt(v->(int)v).toArray(),strict.significantConditions());
            double[] minimum=vector(files,root+"original-minimum.tsv");assertEquals(n,minimum.length);for(int r=0;r<n;r++)near(minimum[r],strict.minimumSignificance()[r],name+" minimum");
            var inclusive=MashSharing.significance(sig,c,selection,s.get("threshold"),MashSharing.Boundary.INCLUSIVE,limits,check);assertArrayEquals(java.util.Arrays.stream(vector(files,root+"inclusive-counts.tsv")).mapToInt(v->(int)v).toArray(),inclusive.significantConditions());
            var pairwise=MashSharing.pairwise(means,sig,c,s.get("factor"),s.get("threshold"),s.get("absolute")==1?MashSharing.Transform.ABSOLUTE:MashSharing.Transform.IDENTITY,limits,check);var original=matrix(files,root+"original-sharing.tsv",c,c);var states=text(files,root+"original-sharing-state.tsv").strip().lines().map(line->line.strip().split("\\s+")).toList();
            for(int i=0;i<c;i++)for(int j=0;j<c;j++){near(original[i][j],pairwise.cell(i,j).fraction(),name+" sharing");String state=states.get(i)[j];assertEquals(state.equals("EMPTY")?MashSharing.Status.EMPTY_SELECTED_POPULATION:state.equals("UNDEFINED")?MashSharing.Status.UNDEFINED_SELECTED_RATIO:MashSharing.Status.DEFINED,pairwise.cell(i,j).status());}
            List<int[]> groups=text(files,root+"groups.tsv").strip().lines().map(line->Stream.of(line.strip().split("\\s+")).mapToInt(x->Integer.parseInt(x)-1).toArray()).toList();
            var result=MashSignScores.calculate(sig,nominal,c,groups,s.get("nominal_threshold"),s.get("minimum_nominal").intValue(),s.get("score_threshold"),limits,check);
            var score=matrix(files,root+"composite-scores.tsv",n,groups.size());var log=matrix(files,root+"composite-logs.tsv",n,groups.size());var under=matrix(files,root+"composite-underflow.tsv",n,groups.size());var count=matrix(files,root+"composite-counts.tsv",n,groups.size());var eligible=matrix(files,root+"composite-eligible.tsv",n,groups.size());var qualifies=matrix(files,root+"composite-qualifies.tsv",n,groups.size());
            for(int r=0;r<n;r++)for(int g=0;g<groups.size();g++){var value=result.score(r,g);near(score[r][g],value.product(),name+" marginal product");near(log[r][g],value.logProduct(),name+" log product");assertEquals(under[r][g]==1,value.underflowToZero());assertEquals((int)count[r][g],value.nominallySignificant());assertEquals(eligible[r][g]==1,value.eligible());assertEquals(qualifies[r][g]==1,value.exceedsThreshold());}
        }));return tests.stream();
    }
}
