/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import java.util.stream.IntStream;
import org.jlinalg.singlecell.ScanpyPreprocessing;
import org.jlinalg.singlecell.ScanpyPreprocessing.*;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** All original values/metadata and deliberate source errors, not resealed result hashes. */
class ScanpyPreprocessingTest {
    private static final Limits LIMITS=Limits.defaults();
    private static final Map<String,Map<String,Object>> references=new LinkedHashMap<>();
    private static Map<String,Object> expectations;
    private static final Map<String,Map<String,Object>> rangeReferences=new LinkedHashMap<>();
    private static Map<String,Object> rangeExpectations;
    @BeforeAll static void load() throws Exception {
        InputStream source=ScanpyPreprocessingTest.class.getResourceAsStream("/org/jlinalg/singlecell/scanpy-preprocessing/reference-20261119.zip");
        assertNotNull(source);
        try(ZipInputStream zip=new ZipInputStream(source)) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                if(entry.getName().endsWith("/REFERENCE.json"))references.put(entry.getName().split("/")[0],map(SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8))));
                else if(entry.getName().equals("CONTRACT-EXPECTATIONS.json"))expectations=map(SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8)));
            }
        }
        assertEquals(Set.of("1.9.3","1.13.0"),references.keySet());assertEquals(402,expectations.size());
        for(var e:references.entrySet()) {assertEquals(201,list(e.getValue().get("cases")).size());assertEquals(e.getKey().equals("1.9.3")?10:0,((Number)e.getValue().get("source_errors")).intValue());}
        InputStream ranges=ScanpyPreprocessingTest.class.getResourceAsStream("/org/jlinalg/singlecell/scanpy-preprocessing/range-boundaries-20261119.zip");assertNotNull(ranges);
        try(ZipInputStream zip=new ZipInputStream(ranges)) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                if(entry.getName().endsWith("/REFERENCE.json"))rangeReferences.put(entry.getName().split("/")[0],map(SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8))));
                else if(entry.getName().equals("CONTRACT-EXPECTATIONS.json"))rangeExpectations=map(SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8)));
            }
        }
        assertEquals(16,rangeExpectations.size());assertEquals(Set.of("1.9.3","1.13.0"),rangeReferences.keySet());
    }

    @TestFactory List<DynamicTest> all_402_original_profile_cases() {
        List<DynamicTest> tests=new ArrayList<>();
        for(var group:references.entrySet()) {
            String version=group.getKey();Profile profile=version.equals("1.9.3")?Profile.SCANPY_1_9_3:Profile.SCANPY_1_13_0;List<?> cases=list(group.getValue().get("cases"));
            for(int i=0;i<cases.size();i++) {
                Map<String,Object> row=map(cases.get(i));String id=version+"/"+i;String label=id+" "+row.get("name")+" "+row.get("storage")+" "+row.get("operation")+" "+row.get("options");
                tests.add(DynamicTest.dynamicTest(label,()->check(profile,row,map(expectations.get(id)),label)));
            }
        }
        return tests;
    }
    @TestFactory List<DynamicTest> all_16_original_numeric_range_boundaries() {
        List<DynamicTest> tests=new ArrayList<>();
        for(var group:rangeReferences.entrySet()) {
            String version=group.getKey();Profile profile=version.equals("1.9.3")?Profile.SCANPY_1_9_3:Profile.SCANPY_1_13_0;List<?> cases=list(group.getValue().get("cases"));assertEquals(8,cases.size());
            for(int i=0;i<cases.size();i++) {
                Map<String,Object> row=map(cases.get(i));String id=version+"/"+i;String label="range "+id+" "+row.get("name")+" "+row.get("storage");
                tests.add(DynamicTest.dynamicTest(label,()->check(profile,row,map(rangeExpectations.get(id)),label)));
            }
        }
        return tests;
    }
    private static void check(Profile profile,Map<String,Object> row,Map<String,Object> expectedContract,String label) {
        List<?> shape=list(row.get("shape"));int cells=((Number)shape.get(0)).intValue(),genes=((Number)shape.get(1)).intValue();
        List<String> features=IntStream.range(0,genes).mapToObj(i->"gene α:"+i).toList(),identities=IntStream.range(0,cells).mapToObj(i->"cell β:"+i).toList();
        Matrix input=new Matrix(features,identities,ints(row.get("indptr")),ints(row.get("indices")),numbers(row.get("data")));double[] before=input.values();
        Storage storage=row.get("storage").equals("csr")?Storage.CSR:Storage.DENSE;Map<String,Object> options=map(row.get("options"));
        if(row.get("status").equals("ERROR")) {
            assertEquals("scale",row.get("operation"));assertEquals(Profile.SCANPY_1_9_3,profile);assertEquals("ZeroDivisionError",row.get("error_class"));assertEquals(1,cells);
            assertThrows(ArithmeticException.class,()->ScanpyPreprocessing.scale(input,storage,profile,scaleOptions(options),LIMITS,()->false),label);return;
        }
        Map<String,Object> expected=map(row.get("result"));String op=(String)row.get("operation");
        if(op.startsWith("filter_")) {
            String key=options.keySet().iterator().next();Criterion criterion=key.equals("min_counts")?Criterion.MIN_COUNTS:key.equals("max_counts")?Criterion.MAX_COUNTS:key.startsWith("min_")?Criterion.MIN_DETECTED:Criterion.MAX_DETECTED;
            FilterResult r=ScanpyPreprocessing.filter(input,storage,op.equals("filter_cells")?Axis.CELLS:Axis.GENES,criterion,number(options.get(key)),LIMITS,()->false);
            boolean[] mask=new boolean[list(expected.get("mask")).size()];for(int i=0;i<mask.length;i++)mask[i]=(Boolean)list(expected.get("mask")).get(i);
            assertArrayEquals(mask,r.retained(),label);compare(numbers(expected.get("metric")),r.metric(),label+" metric");
        } else {
            Result r=switch(op) {
                case "normalize_total" -> ScanpyPreprocessing.normalizeTotal(input,storage,profile,new NormalizeOptions(nullable(options.get("target_sum")),(Boolean)options.getOrDefault("exclude_highly_expressed",false),number(options.getOrDefault("max_fraction",.05))),LIMITS,()->false);
                case "log1p" -> ScanpyPreprocessing.log1p(input,storage,profile,nullable(options.get("base")),LIMITS,()->false);
                case "scale" -> ScanpyPreprocessing.scale(input,storage,profile,scaleOptions(options),LIMITS,()->false);
                default -> throw new AssertionError(op);
            };
            double[] dense=r.denseOutput()?r.diagnosticValues():dense(input,r.diagnosticValues());
            compare(flat(expected.get("values")),dense,label+" values");
            if(expected.containsKey("norm_factor")) {
                compare(numbers(expected.get("norm_factor")),r.returnedNormFactors(),label+" norm_factor");
                compare(new double[]{number(expectedContract.get("effective_target"))},new double[]{r.effectiveTargetSum()},label+" target");
                boolean[] excluded=new boolean[genes];for(int i=0;i<genes;i++)excluded[i]=(Boolean)list(expectedContract.get("excluded_features")).get(i);
                assertArrayEquals(excluded,r.excludedFeatures(),label);
            }
            if(expected.containsKey("mean")){compare(numbers(expected.get("mean")),r.means(),label+" mean");compare(numbers(expected.get("std")),r.standardDeviations(),label+" std");}
            assertEquals(expectedContract.get("analysis_admissible"),r.usable(),label+" admission");
            if(r.usable()) {Matrix output=r.matrix();assertEquals(features,output.features());assertEquals(identities,output.cells());compare(dense,dense(output,output.values()),label+" admitted");}
            else {assertThrows(ArithmeticException.class,r::matrix,label);assertFalse(r.warnings().isEmpty(),label);}
        }
        assertArrayEquals(before,input.values(),label+" input must remain exact");
    }

    @Test void strict_options_do_not_silently_coerce_invalid_parameters() {
        for(double v:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new NormalizeOptions(v,false,.05));
        for(double v:new double[]{-.1,1.1,Double.NaN})assertThrows(IllegalArgumentException.class,()->new NormalizeOptions(null,true,v));
        assertThrows(IllegalArgumentException.class,()->new ScaleOptions(true,Double.NaN));
        Matrix x=small();for(double v:new double[]{0,-1,1,Double.NaN})assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.log1p(x,Storage.DENSE,Profile.SCANPY_1_9_3,v,LIMITS,()->false));
    }
    @Test void sparse_input_cannot_be_relabelled_dense_without_all_zeros() {
        Matrix x=new Matrix(List.of("a","b"),List.of("c","d"),new int[]{0,1,1},new int[]{0},new double[]{2});
        assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.normalizeTotal(x,Storage.DENSE,Profile.SCANPY_1_13_0,NormalizeOptions.defaults(),LIMITS,()->false));
    }
    @Test void capacities_apply_to_the_complete_population_before_dense_allocation() {
        Matrix x=small();Limits dense=new Limits(20,20,100,1,100000,100000);
        assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.scale(x,Storage.DENSE,Profile.SCANPY_1_13_0,ScaleOptions.defaults(),dense,()->false));
        Limits memory=new Limits(20,20,100,100,1,100000);assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.log1p(x,Storage.DENSE,Profile.SCANPY_1_13_0,null,memory,()->false));
        Limits work=new Limits(20,20,100,100,100000,0);assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.normalizeTotal(x,Storage.DENSE,Profile.SCANPY_1_13_0,NormalizeOptions.defaults(),work,()->false));
    }
    @Test void cancellation_and_thread_interruption_return_no_partial_result() {
        Matrix x=small();assertThrows(CancellationException.class,()->ScanpyPreprocessing.log1p(x,Storage.DENSE,Profile.SCANPY_1_13_0,null,LIMITS,()->true));
        Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->ScanpyPreprocessing.scale(x,Storage.DENSE,Profile.SCANPY_1_13_0,ScaleOptions.defaults(),LIMITS,()->false));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
        int n=40;List<String> ids=IntStream.range(0,n).mapToObj(i->"id"+i).toList();int[] p=new int[n+1],r=new int[n*n];double[] v=new double[n*n];Arrays.fill(v,1);
        for(int c=0;c<n;c++){p[c]=c*n;for(int g=0;g<n;g++)r[c*n+g]=g;}p[n]=v.length;Matrix many=new Matrix(ids,ids,p,r,v);AtomicInteger polls=new AtomicInteger();
        assertThrows(CancellationException.class,()->ScanpyPreprocessing.scale(many,Storage.DENSE,Profile.SCANPY_1_13_0,ScaleOptions.defaults(),LIMITS,()->polls.incrementAndGet()>1));assertArrayEquals(v,many.values());
    }
    @Test void result_buffers_and_identity_order_are_immutable() {
        Matrix x=small();Result r=ScanpyPreprocessing.normalizeTotal(x,Storage.DENSE,Profile.SCANPY_1_13_0,new NormalizeOptions(10.0,false,.05),LIMITS,()->false);
        double first=r.matrix().values()[0];r.diagnosticValues()[0]=999;r.returnedNormFactors()[0]=999;r.normalizationTotals()[0]=999;
        assertEquals(first,r.matrix().values()[0]);assertEquals(List.of("gene α","gene β"),r.matrix().features());assertEquals(List.of("cell 1","cell 2"),r.matrix().cells());
        FilterResult f=ScanpyPreprocessing.filter(x,Storage.DENSE,Axis.CELLS,Criterion.MIN_COUNTS,1,LIMITS,()->false);f.retained()[0]=false;f.metric()[0]=999;assertTrue(f.retained()[0]);assertEquals(3,f.metric()[0]);
    }
    @Test void filter_overflow_never_admits_a_complete_finite_looking_mask() {
        Matrix x=new Matrix(List.of("a","b"),List.of("c"),new int[]{0,2},new int[]{0,1},new double[]{1e308,1e308});
        assertThrows(ArithmeticException.class,()->ScanpyPreprocessing.filter(x,Storage.DENSE,Axis.CELLS,Criterion.MIN_COUNTS,1,LIMITS,()->false));
    }
    @Test void negative_count_inputs_and_fractional_detected_thresholds_are_not_repaired() {
        Matrix x=new Matrix(List.of("a"),List.of("c","d"),new int[]{0,1,2},new int[]{0,0},new double[]{-1,2});
        assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.normalizeTotal(x,Storage.DENSE,Profile.SCANPY_1_9_3,NormalizeOptions.defaults(),LIMITS,()->false));
        assertThrows(IllegalArgumentException.class,()->ScanpyPreprocessing.filter(small(),Storage.DENSE,Axis.CELLS,Criterion.MIN_DETECTED,1.5,LIMITS,()->false));
    }
    @Test void empty_filter_selection_is_retained_as_an_explicit_mask() {
        FilterResult f=ScanpyPreprocessing.filter(small(),Storage.DENSE,Axis.CELLS,Criterion.MIN_COUNTS,100,LIMITS,()->false);
        assertArrayEquals(new boolean[]{false,false},f.retained());assertArrayEquals(new double[]{3,7},f.metric());
    }
    private static Matrix small(){return new Matrix(List.of("gene α","gene β"),List.of("cell 1","cell 2"),new int[]{0,2,4},new int[]{0,1,0,1},new double[]{1,2,3,4});}
    private static ScaleOptions scaleOptions(Map<String,Object> options){return new ScaleOptions((Boolean)options.get("zero_center"),nullable(options.get("max_value")));}
    private static Double nullable(Object o){return o==null?null:number(o);}
    private static double[] dense(Matrix input,double[] data){double[] out=new double[input.features().size()*input.cells().size()];int[] p=input.columnPointers(),r=input.rowIndices();for(int c=0;c<input.cells().size();c++)for(int k=p[c];k<p[c+1];k++)out[c*input.features().size()+r[k]]=data[k];return out;}
    private static double[] flat(Object rows){return list(rows).stream().flatMap(v->list(v).stream()).mapToDouble(ScanpyPreprocessingTest::number).toArray();}
    private static double number(Object o){return Double.parseDouble(o.toString());}
    private static double[] numbers(Object o){return list(o).stream().mapToDouble(ScanpyPreprocessingTest::number).toArray();}
    private static int[] ints(Object o){return list(o).stream().mapToInt(v->((Number)v).intValue()).toArray();}
    private static List<?> list(Object o){return (List<?>)o;}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    private static void compare(double[] expected,double[] actual,String label){assertEquals(expected.length,actual.length,label);for(int i=0;i<expected.length;i++){double e=expected[i],a=actual[i];if(Double.isNaN(e))assertTrue(Double.isNaN(a),label+" field "+i);else if(!Double.isFinite(e))assertEquals(e,a,label+" field "+i);else{assertTrue(Double.isFinite(a),label+" field "+i);assertEquals(e,a,Math.max(64*Math.ulp(e),4e-12*Math.abs(e)),label+" field "+i);}}}
}
