/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Array;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SampleSizeWeightedMetaAnalysisTest {
    static Map<String,String> fixtures()throws Exception {
        var files=new HashMap<String,String>();try(var raw=SampleSizeWeightedMetaAnalysisTest.class.getResourceAsStream("/org/jlinalg/meta/sample-size-weighted-z/reference-fixtures.zip")){assertNotNull(raw);try(var z=new ZipInputStream(raw)){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry())files.put(e.getName(),new String(z.readAllBytes(),StandardCharsets.UTF_8));}}return files;
    }
    static double[] doubles(String value){return Arrays.stream(value.split(",")).mapToDouble(Double::parseDouble).toArray();}
    static SampleSizeWeightedMetaAnalysis.Result compute(String[] c){
        double[] values=doubles(c[2]),weights=doubles(c[4]);int[] signs=c[3].isEmpty()?new int[0]:Arrays.stream(c[3].split(",")).mapToInt(Integer::parseInt).toArray();
        return switch(c[1]){case "z"->SampleSizeWeightedMetaAnalysis.fitSignedZ(values,weights);case "p"->SampleSizeWeightedMetaAnalysis.fitPValues(values,signs,weights);case "logp"->SampleSizeWeightedMetaAnalysis.fitLogPValues(values,signs,weights);default->throw new IllegalArgumentException("Unknown reference mode");};
    }
    static Map<String,Object> flatten(SampleSizeWeightedMetaAnalysis.Result r)throws Exception {
        var out=new TreeMap<String,Object>();for(var c:r.getClass().getRecordComponents()){Object value=c.getAccessor().invoke(r);if(value!=null&&value.getClass().isArray()){for(int i=0;i<Array.getLength(value);i++)out.put(c.getName()+"."+i,Array.get(value,i));}else out.put(c.getName(),value);}return out;
    }
    @Test void independentR69FiniteModelsAndOneRetainedNoResultCase()throws Exception {
        var f=fixtures();var expected=new HashMap<String,Map<String,String>>();for(String line:f.get("expected.tsv").lines().skip(1).toList()){var c=line.split("\t");assertNull(expected.computeIfAbsent(c[0],k->new LinkedHashMap<>()).put(c[1],c[2]));}
        int finite=0,refused=0,compared=0,unrepresentableFields=0;
        for(String line:f.get("cases.tsv").lines().skip(1).toList()){
            String[] c=line.split("\t",-1);var oracle=expected.get(c[0]);assertNotNull(oracle);
            if(c[5].equals("refuse-unrepresentable")){var error=assertThrows(ArithmeticException.class,()->compute(c));assertTrue(error.getMessage().contains("Unrepresentable"));assertTrue(oracle.values().stream().anyMatch(v->v.equals("Inf")||v.equals("-Inf")));refused++;unrepresentableFields+=oracle.size();continue;}
            var actual=flatten(compute(c));for(var e:oracle.entrySet()){String label=c[0]+" "+e.getKey();assertTrue(actual.containsKey(e.getKey()),label);Object observed=actual.get(e.getKey());String wanted=e.getValue();compared++;if(wanted.equals("NA")){assertNull(observed,label);continue;}assertNotNull(observed,label);if(observed instanceof Boolean flag){assertEquals(wanted.equals("1"),flag,label);continue;}double a=Double.parseDouble(wanted),v=((Number)observed).doubleValue();assertTrue(Double.isFinite(a)&&Double.isFinite(v),label);double tolerance=a==0?2e-13:Math.max(4*Math.ulp(a),5e-11*Math.abs(a));if(Set.of("homogeneous","underflow").contains(c[0])&&(e.getKey().equals("heterogeneityQ")||e.getKey().startsWith("heterogeneityContributions.")))tolerance=Math.max(tolerance,2e-25);assertEquals(a,v,tolerance,label);}
            finite++;
        }
        assertEquals(69,finite);assertEquals(1,refused);assertEquals(1844,compared);assertEquals(20,unrepresentableFields);System.out.println("Independent R:69 finite models/1844 fields;1 retained unrepresentable model/20 raw diagnostics");
    }
    @Test void cancellationRetainsHeterogeneityAndCorrectDegreesOfFreedom(){
        var r=SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{2,-2},new double[]{25,25});assertEquals(0,r.z());assertEquals(1,r.pValue());assertEquals(50,r.totalWeight());assertEquals(8,r.heterogeneityQ(),1e-14);assertEquals(1,r.heterogeneityDegreesOfFreedom());assertEquals(87.5,r.iSquaredPercent(),1e-12);assertTrue(r.heterogeneityDefined());assertFalse(r.pValueUnderflow());
    }
    @Test void aSingleStudyDoesNotInventAHeterogeneityTest(){
        var r=SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{-3},new double[]{.5});assertEquals(-3,r.z());assertEquals(.5,r.totalWeight());assertEquals(0,r.heterogeneityQ());assertFalse(r.heterogeneityDefined());assertNull(r.heterogeneityPValue());assertNull(r.heterogeneityLogPValue());assertNull(r.iSquaredPercent());assertFalse(r.heterogeneityPValueUnderflow());
    }
    @Test void logProbabilitiesRetainUnderflowWithoutSubstitutingAPValueFloor(){
        var r=SampleSizeWeightedMetaAnalysis.fitLogPValues(new double[]{-10000},new int[]{-1},new double[]{100});assertEquals(0,r.pValue());assertTrue(r.pValueUnderflow());assertTrue(Double.isFinite(r.logPValue()));assertEquals(-10000,r.logPValue(),1e-8);assertTrue(r.z()<-100);assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitPValues(new double[]{0},new int[]{1},new double[]{100}));
        var smallest=SampleSizeWeightedMetaAnalysis.fitPValues(new double[]{Double.MIN_VALUE},new int[]{1},new double[]{100});assertTrue(Double.isFinite(smallest.studyZ()[0]));assertEquals(Math.log(Double.MIN_VALUE),smallest.logPValue(),1e-8);
    }
    @Test void invalidMissingAndUnrepresentableInputsNeverDropCohorts(){
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1})assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{1,2},new double[]{100,bad}));
        for(double bad:new double[]{Double.NaN,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{1,bad},new double[]{100,100}));
        assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[0],new double[0]));assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitPValues(new double[]{.05},new int[]{0},new double[]{100}));assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitLogPValues(new double[]{Double.NEGATIVE_INFINITY},new int[]{1},new double[]{100}));
        assertThrows(ArithmeticException.class,()->SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{1,1},new double[]{Double.MAX_VALUE,Double.MAX_VALUE}));assertThrows(ArithmeticException.class,()->SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{1e308,0},new double[]{Double.MIN_VALUE,1e308}));
    }
    @Test void capacityRefusesTheCompletePopulationBeforeStatistics(){
        assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitSignedZ(new double[]{1,2},new double[]{100,100},new SampleSizeWeightedMetaAnalysis.Limits(1,1<<20)));assertThrows(IllegalArgumentException.class,()->SampleSizeWeightedMetaAnalysis.fitPValues(new double[]{.5,.5},new int[]{1,-1},new double[]{100,100},new SampleSizeWeightedMetaAnalysis.Limits(100,8192)));
    }
    @Test void inputsAndAllReturnedDiagnosticArraysAreOwned()throws Exception {
        double[] z={1,-2,3},weights={100,200,300};var result=SampleSizeWeightedMetaAnalysis.fitSignedZ(z,weights);assertArrayEquals(new double[]{1,-2,3},z);assertArrayEquals(new double[]{100,200,300},weights);var before=flatten(result);for(var c:result.getClass().getRecordComponents()){Object value=c.getAccessor().invoke(result);if(value instanceof double[] a)a[0]=Double.NaN;}assertEquals(before,flatten(result));z[0]=99;weights[0]=0;assertEquals(1,result.studyZ()[0]);assertEquals(3,result.studies());
    }
}
