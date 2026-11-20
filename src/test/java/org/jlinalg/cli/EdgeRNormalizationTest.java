/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.differential.EdgeRNormalization;
import org.jlinalg.differential.EdgeRNormalization.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Independent original sources/current-package references, not a mirrored Java oracle. */
final class EdgeRNormalizationTest {
    private static final Map<String,Object> REFERENCE=reference();
    private static final AtomicLong MAX_ABSOLUTE=new AtomicLong(),MAX_SCALED=new AtomicLong(),VALUES=new AtomicLong();
    private static final double TOLERANCE=4e-13;
    @TestFactory Stream<DynamicTest> originalNormalizationSourcesAcrossFiveEditions() {
        return list(REFERENCE.get("normalizations")).stream().map(EdgeRNormalizationTest::map).map(row->DynamicTest.dynamicTest((String)row.get("name"),()->{
            var d=dataset(row);double[][] x=matrix(d.get("counts"));double[] libs=nullableVector(d.get("libraries"));
            var o=map(row.get("options"));Method method=switch((String)o.get("method")) {
                case "TMM"->Method.TMM;case "TMMwsp"->Method.TMMWSP;case "RLE"->Method.RLE;
                case "upperquartile"->Method.UPPER_QUARTILE;case "none"->Method.NONE;default->throw new AssertionError();};
            Integer ref=o.get("reference_zero_based")==null?null:((Number)o.get("reference_zero_based")).intValue();
            var options=new Options(method,ref,number(o.get("logratioTrim")),number(o.get("sumTrim")),(Boolean)o.get("doWeighting"),number(o.get("Acutoff")),number(o.get("p")));
            var result=EdgeRNormalization.normalize(x,libs,Edition.valueOf((String)row.get("edition")),options);
            assertVector(vector(row.get("factors")),result.factors());
            if(Arrays.stream(vector(row.get("factors"))).anyMatch(v->!Double.isFinite(v))) {
                assertFalse(result.finite());assertTrue(result.warnings().stream().anyMatch(s->s.contains("NO COMPLETE RESULT")));
            }
            if(row.get("edition").equals("EDGER_4_10_7"))assertTrue(result.warnings().stream().anyMatch(s->s.contains("renamed")));
        }));
    }
    @TestFactory Stream<DynamicTest> actualOriginalPackageCpmAndRpkm() {
        return list(REFERENCE.get("transforms")).stream().map(EdgeRNormalizationTest::map).map(row->DynamicTest.dynamicTest((String)row.get("name"),()->{
            double[][] x=matrix(dataset(row).get("counts"));double[] libs=vector(row.get("libraries"));
            double[][] result=row.get("operation").equals("CPM")?
                EdgeRNormalization.cpm(x,libs,Edition.EDGER_4_10_7,(Boolean)row.get("log"),number(row.get("prior"))):
                EdgeRNormalization.rpkm(x,libs,vector(row.get("gene_lengths")),Edition.EDGER_4_10_7,(Boolean)row.get("log"),number(row.get("prior")));
            double[][] expected=matrix(row.get("values"));assertEquals(expected.length,result.length);
            for(int i=0;i<result.length;i++)assertVector(expected[i],result[i]);
        }));
    }
    @Test void fixtureHasActualIndependentCoverageAndRetainedOriginalConditions() {
        assertEquals(2350,list(REFERENCE.get("normalizations")).size());assertEquals(552,list(REFERENCE.get("transforms")).size());
        assertEquals(47,map(REFERENCE.get("datasets")).size());assertEquals(5,list(REFERENCE.get("versions")).size());
        assertEquals(false,REFERENCE.get("whole_tool_qualified"));assertEquals(false,REFERENCE.get("model_inference_run"));
        assertTrue(list(REFERENCE.get("normalizations")).stream().map(EdgeRNormalizationTest::map)
            .anyMatch(r->!list(r.get("warnings")).isEmpty()));
    }
    @Test void inputAndReturnedArraysAreNotModifiedOrExposed() {
        double[][] x={{1,2,3},{4,8,12}};double[][] original=Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);
        double[] libs={10,20,30},before=libs.clone();var r=EdgeRNormalization.normalize(x,libs,Edition.EDGER_3_42_4,Options.defaults(Method.TMM));
        assertTrue(Arrays.deepEquals(original,x));assertArrayEquals(before,libs);double[] wanted=r.factors();
        r.factors()[0]=77;r.librarySizes()[0]=77;r.effectiveLibrarySizes()[0]=77;
        assertArrayEquals(wanted,r.factors());assertArrayEquals(before,r.librarySizes());assertThrows(UnsupportedOperationException.class,()->r.warnings().add("changed"));
    }
    @Test void originalUndefinedRleIsReportedWithoutUnityRepair() {
        var r=EdgeRNormalization.normalize(new double[][]{{1,0},{0,2}},null,Edition.EDGER_3_42_4,Options.defaults(Method.RLE));
        assertFalse(r.finite());assertTrue(Arrays.stream(r.factors()).allMatch(Double::isNaN));assertFalse(r.warnings().isEmpty());
    }
    @Test void logCpmZeroWithZeroPriorRetainsOriginalNan() {
        var r=EdgeRNormalization.cpm(new double[][]{{0,1},{2,3}},null,Edition.EDGER_3_42_4,true,0);assertTrue(Double.isNaN(r[0][0]));
    }
    @Test void negativeNanInfiniteAndRaggedCountsAreRefused() {
        for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.normalize(new double[][]{{bad,1}},null,Edition.EDGER_3_42_4,Options.defaults(Method.TMM)));
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.cpm(new double[][]{{1},{1,2}},null,Edition.EDGER_3_42_4,false,2));
    }
    @Test void librarySizesAndLengthsAreNotRecycledOrInferred() {
        double[][] x={{1,2},{3,4}};
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.normalize(x,new double[]{10},Edition.EDGER_3_42_4,Options.defaults(Method.TMM)));
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.rpkm(x,null,new double[]{100},Edition.EDGER_3_42_4,false,2));
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.rpkm(x,null,new double[]{100,0},Edition.EDGER_3_42_4,false,2));
    }
    @Test void zeroLibraryIsNotSilentlyRepairedForNondegenerateCounts() {
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.normalize(new double[][]{{0,2},{0,3}},null,Edition.EDGER_3_42_4,Options.defaults(Method.TMM)));
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.cpm(new double[][]{{0,2},{0,3}},null,Edition.EDGER_3_42_4,false,2));
    }
    @Test void typedOptionsRefuseInvalidTrimsAndReference() {
        assertThrows(IllegalArgumentException.class,()->new Options(Method.TMM,null,.5,.05,true,-1e10,.75));
        assertThrows(IllegalArgumentException.class,()->new Options(Method.TMM,null,.3,-.1,true,-1e10,.75));
        assertThrows(IllegalArgumentException.class,()->new Options(Method.TMM,null,.3,.05,true,Double.NaN,.75));
        assertThrows(IllegalArgumentException.class,()->EdgeRNormalization.normalize(new double[][]{{1,2}},null,Edition.EDGER_3_42_4,new Options(Method.TMM,2,.3,.05,true,-1e10,.75)));
    }
    @Test void arbitraryTmmwspACutoffIsExplicitlyIgnoredAsInTheSource() {
        var r=EdgeRNormalization.normalize(new double[][]{{1,2},{3,9}},null,Edition.EDGER_3_42_4,new Options(Method.TMMWSP,null,.3,.05,true,0,.75));
        assertTrue(r.warnings().stream().anyMatch(s->s.contains("does not apply")));
    }
    @AfterAll static void audit()throws IOException {
        String path=System.getProperty("omicsmatic.edger.reference.audit");if(path==null)return;
        Map<String,Object> result=Map.of("original_normalizations",2350,"original_transforms",552,"numeric_values_compared",VALUES.get(),
            "maximum_absolute_error",Double.longBitsToDouble(MAX_ABSOLUTE.get()),"maximum_scaled_error",Double.longBitsToDouble(MAX_SCALED.get()),
            "relative_tolerance",TOLERANCE,"fresh_R_runs",false);
        Files.write(Path.of(path),RsemJson.bytes(result),StandardOpenOption.CREATE_NEW);
    }
    private static void assertVector(double[] expected,double[] actual) {
        assertEquals(expected.length,actual.length);
        for(int i=0;i<expected.length;i++) {
            double e=expected[i],a=actual[i];
            if(Double.isNaN(e)){assertTrue(Double.isNaN(a),"Expected original undefined outcome, got "+a);continue;}
            if(Double.isInfinite(e)){assertEquals(e,a);continue;}
            double error=Math.abs(a-e),scale=Math.max(1,Math.abs(e));
            assertEquals(e,a,Math.max(2e-14,Math.abs(e)*TOLERANCE),"Original source value at "+i);
            MAX_ABSOLUTE.accumulateAndGet(Double.doubleToLongBits(error),Math::max);MAX_SCALED.accumulateAndGet(Double.doubleToLongBits(error/scale),Math::max);VALUES.incrementAndGet();
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static List<?> list(Object value){return value==null?List.of():value instanceof List<?> l?l:List.of(value);}
    private static double number(Object value){return value instanceof Number n?n.doubleValue():switch(value.toString()){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value.toString());};}
    private static double[] vector(Object value){return list(value).stream().mapToDouble(EdgeRNormalizationTest::number).toArray();}
    private static double[] nullableVector(Object value){return value==null?null:vector(value);}
    private static double[][] matrix(Object value){return list(value).stream().map(EdgeRNormalizationTest::vector).toArray(double[][]::new);}
    private static Map<String,Object> dataset(Map<String,Object> row){return map(map(REFERENCE.get("datasets")).get(row.get("dataset")));}
    private static Map<String,Object> reference() {
        String resource="/org/jlinalg/differential/edger-normalization/reference-20261120-v2.zip";
        try(InputStream in=EdgeRNormalizationTest.class.getResourceAsStream(resource)) {
            if(in==null)throw new IOException("Required original edgeR reference is absent");byte[] raw=in.readAllBytes();
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
            if(!sha.equals("51fa06b23bc9530df7d0447e3391d54a237844c55001673316afcd2c3f4896e2"))throw new IOException("Original edgeR reference changed");
            try(var zip=new ZipInputStream(new ByteArrayInputStream(raw),StandardCharsets.UTF_8)) {
                java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("reference.json")) {
                    byte[] text=zip.readNBytes(32_000_001);if(text.length>32_000_000)throw new IOException("Reference capacity exceeded");
                    return map(SimpleJson.parse(new String(text,StandardCharsets.UTF_8)));
                }
            }
            throw new IOException("Original edgeR reference.json is absent");
        }catch(Exception error){throw new IllegalStateException("Cannot read the immutable original reference",error);}
    }
}
