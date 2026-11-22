/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.jlinalg.singlecell.MonocleResponsePreparation;
import org.jlinalg.singlecell.MonocleResponsePreparation.Transform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonocleResponsePreparationTest {
    static Map<String,Object> references() throws IOException {
        try(var stream=MonocleResponsePreparationTest.class.getResourceAsStream("/org/jlinalg/singlecell/monocle-responses/original-controls.json.gz")) {
            assertNotNull(stream);byte[] compressed=stream.readNBytes(8562);assertEquals(8561,compressed.length);assertEquals("f602536f4ab822e5818dda4390b2a7f8bd598630efdeddeb7b45398dcd550257",NextflowFiles.sha(compressed));try(var gzip=new GZIPInputStream(new java.io.ByteArrayInputStream(compressed))){byte[] bytes=gzip.readNBytes(4194305);assertTrue(bytes.length<=4194304);return Monocle100NeighborsFiles.object(NextflowParameters.parse(new String(bytes,StandardCharsets.UTF_8),4194304,1000000));}
        }
    }
    static double[] vector(Object raw) throws IOException {return raw==null?null:RegressionMoranTest.vector(raw);}
    static double[][] matrix(Object raw) throws IOException {return RegressionMoranTest.matrix(raw);}
    static void close(double expected,double actual,String message){assertEquals(expected,actual,8*Math.ulp(expected)+2e-15,message);}
    @Test void matchesEveryOfficialMonocleResponseBranchAndSuppliedMetadataValue() throws Exception {
        var source=references();assertEquals(true,source.get("official_source_body_formals_identical"));assertEquals(true,source.get("official_namespace_unchanged"));assertEquals(false,source.get("author_code_executed"));int cases=0,values=0;
        for(Object item:NextflowFiles.list(source.get("cases"))){var row=NextflowFiles.object(item);double[][] input=matrix(row.get("expression"));var mode=row.get("transform").equals("as-is")?Transform.AS_IS:Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE;var result=MonocleResponsePreparation.prepare(input,vector(row.get("size_factors")),vector(row.get("detected_genes")),mode,500000000);double[][] expected=matrix(row.get("expected_responses"));assertEquals(expected.length,result.genes());
            for(int gene=0;gene<expected.length;gene++){double[] actual=result.response(gene);assertEquals(expected[gene].length,actual.length);for(int cell=0;cell<actual.length;cell++){close(expected[gene][cell],actual[cell],row.get("id")+" gene"+gene+" cell"+cell);if(mode==Transform.AS_IS)assertEquals(Double.doubleToRawLongBits(input[gene][cell]),Double.doubleToRawLongBits(actual[cell]));values++;}}
            double[] logged=vector(row.get("expected_log_n_genes"));if(logged==null)assertNull(result.logDetectedGenes());else{double[] actual=result.logDetectedGenes();assertEquals(logged.length,actual.length);for(int i=0;i<logged.length;i++)close(logged[i],actual[i],"supplied feature metadata");}cases++;
        }
        assertEquals(6,cases);assertTrue(values>900);
    }
    @Test void offsetFollowsSizeFactorDivisionAndSuppliedMetadataIsNotRecomputedFromSelectedGenes() {
        var result=MonocleResponsePreparation.prepare(new double[][]{{0,1,2}},new double[]{.5,2,4},new double[]{100,200,300},Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100);
        assertEquals(-1,result.response(0)[0]);assertEquals(StrictMath.log10(.6),result.response(0)[1]);assertEquals(StrictMath.log10(.6),result.response(0)[2]);assertEquals(StrictMath.log(100),result.logDetectedGenes()[0]);assertEquals(1,result.genes());
    }
    @Test void outputsOwnTheirArraysAndAsIsPreservesNegativeValuesAndSignedZero() {
        double[][] input={{-0.0,-2,3}};double[] detected={10,20,30};var result=MonocleResponsePreparation.prepare(input,null,detected,Transform.AS_IS,100);input[0][1]=99;detected[0]=999;double[][] copy=result.responses();copy[0][2]=99;double[] metadata=result.logDetectedGenes();metadata[1]=999;
        assertEquals(Double.doubleToRawLongBits(-0.0),Double.doubleToRawLongBits(result.response(0)[0]));assertArrayEquals(new double[]{-0.0,-2,3},result.response(0));assertEquals(StrictMath.log(20),result.logDetectedGenes()[1]);
    }
    @Test void jointScalingOfExpressionAndDeclaredFactorsPreservesTheRatioTransform() {
        double[][] input={{0,2,10},{4,6,8}};double[] factors={.5,2,4};var a=MonocleResponsePreparation.prepare(input,factors,null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100);double[][] larger=Arrays.stream(input).map(row->Arrays.stream(row).map(value->value*1024).toArray()).toArray(double[][]::new);var b=MonocleResponsePreparation.prepare(larger,Arrays.stream(factors).map(value->value*1024).toArray(),null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100);for(int i=0;i<2;i++)assertArrayEquals(a.response(i),b.response(i));
    }
    @Test void missingNonfiniteRaggedOrIgnoredInputsAreRefused() {
        for(double[][] input:new double[][][]{null,new double[0][],new double[][]{null},new double[][]{{}},new double[][]{{1,2},{3}},new double[][]{{1,Double.NaN}},new double[][]{{Double.POSITIVE_INFINITY,1}}})assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(input,null,null,Transform.AS_IS,100));
        assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1,2}},new double[]{1,1},null,Transform.AS_IS,100));assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1,2}},null,null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100));assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{-1,2}},new double[]{1,1},null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100));
    }
    @Test void invalidNormalizationAndFeatureCountsCannotBeRepairedOrRounded() {
        for(double[] factors:List.of(new double[]{1},new double[]{0,1},new double[]{-1,1},new double[]{Double.NaN,1},new double[]{Double.POSITIVE_INFINITY,1}))assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1,2}},factors,null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100));
        for(double[] metadata:List.of(new double[]{1},new double[]{0,2},new double[]{1.5,2},new double[]{Double.NaN,2},new double[]{Double.POSITIVE_INFINITY,2},new double[]{1e16,2}))assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1,2}},null,metadata,Transform.AS_IS,100));
    }
    @Test void arithmeticOverflowAndCompleteOperationBoundsRefuseAResult() {
        assertThrows(ArithmeticException.class,()->MonocleResponsePreparation.prepare(new double[][]{{Double.MAX_VALUE}},new double[]{Double.MIN_VALUE},null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,100));assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1,2,3}},new double[]{1,1,1},null,Transform.SIZE_FACTOR_LOG10_PLUS_POINT_ONE,11));assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1}},null,null,Transform.AS_IS,0));assertThrows(IllegalArgumentException.class,()->MonocleResponsePreparation.prepare(new double[][]{{1}},null,null,null,100));
    }
}
