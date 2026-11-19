/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.sequence.SeuratPreprocessing;
import org.jlinalg.sequence.SeuratPreprocessing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Every cell of independently captured original R matrices, including refused/undefined outcomes. */
class SeuratPreprocessingTest {
    private static final Limits LIMITS=Limits.defaults();
    private static Map<String,Object> reference;
    private static Map<String,Matrix> inputs;
    @BeforeAll static void load() throws Exception {
        try(InputStream in=SeuratPreprocessingTest.class.getResourceAsStream("/org/jlinalg/sequence/seurat-preprocessing/reference.json")) {
            assertNotNull(in);reference=map(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)));
        }
        assertEquals(SeuratPreprocessing.PROFILE,reference.get("profile"));
        assertEquals(SeuratPreprocessing.SOURCE_COMMIT,reference.get("source_commit"));inputs=new LinkedHashMap<>();
        for(Object object:list(reference.get("datasets"))) {
            Map<String,Object> row=map(object);inputs.put((String)row.get("id"),new Matrix(strings(row.get("features")),strings(row.get("cells")),ints(row.get("pointers")),ints(row.get("rows")),numbers(row.get("values"))));
        }
    }

    @Test void all_original_normalization_cells_coordinates_and_failures_match() {
        long fields=0;int sourceErrors=0,usable=0,forensicOnly=0;
        for(Object object:list(reference.get("normalizations"))) {
            Map<String,Object> row=map(object);String id=(String)row.get("id");Matrix input=inputs.get(row.get("dataset"));
            if(row.containsKey("error")) {assertThrows(IllegalArgumentException.class,()->normalize(row,input),id);sourceErrors++;continue;}
            NormalizationResult result=normalize(row,input);double[] raw=result.diagnosticValues();
            double[] dense=dense(input,raw);compare(numbers(row.get("values")),dense,id);fields+=dense.length;
            if(row.containsKey("stored_values")) {compare(numbers(row.get("stored_values")),raw,id+" stored");assertArrayEquals(ints(row.get("pointers")),input.columnPointers(),id);assertArrayEquals(ints(row.get("rows")),input.rowIndices(),id);}
            if(result.usable()) {Matrix matrix=result.matrix();assertEquals(input.features(),matrix.features());assertEquals(input.cells(),matrix.cells());assertArrayEquals(input.columnPointers(),matrix.columnPointers());assertArrayEquals(input.rowIndices(),matrix.rowIndices());usable++;}
            else {assertThrows(ArithmeticException.class,result::matrix,id);assertFalse(result.warnings().isEmpty());forensicOnly++;}
        }
        assertEquals(90,list(reference.get("normalizations")).size());assertEquals(3,sourceErrors);
        System.out.println("SEURAT_ORIGINAL_NORMALIZATION models=90 source_errors="+sourceErrors+" usable="+usable+" diagnostic_only="+forensicOnly+" dense_fields="+fields);
    }

    @Test void all_original_sparse_scaling_cells_and_selected_order_match() {
        long fields=0;int usable=0,forensicOnly=0;
        for(Object object:list(reference.get("scalings"))) {
            Map<String,Object> row=map(object);String id=(String)row.get("id");Matrix input=inputs.get(row.get("dataset"));assertFalse(row.containsKey("error"),id);
            ScaleOptions options=new ScaleOptions((Boolean)row.get("center"),(Boolean)row.get("scale"),number(row.get("cap")));
            ScaleResult result=SeuratPreprocessing.scaleData(input,strings(row.get("features")),options,LIMITS,()->false);
            double[] raw=result.diagnosticValuesColumnMajor();compare(numbers(row.get("values")),raw,id);fields+=raw.length;
            if(result.usable()) {DenseMatrix matrix=result.matrix();assertEquals(strings(row.get("features")),matrix.features());assertEquals(strings(row.get("cells")),matrix.cells());assertArrayEquals(raw,matrix.valuesColumnMajor());usable++;}
            else {assertThrows(ArithmeticException.class,result::matrix,id);assertTrue(result.warnings().stream().anyMatch(w->w.code().equals("UNDEFINED_SOURCE_SCALE_STATISTICS")||w.code().equals("NONFINITE_SOURCE_VALUES")));forensicOnly++;}
            if(row.get("dataset").equals("single_cell")&&(Boolean)row.get("scale"))assertFalse(result.usable(),id);
        }
        assertEquals(208,list(reference.get("scalings")).size());
        System.out.println("SEURAT_ORIGINAL_SCALE models=208 usable="+usable+" diagnostic_only="+forensicOnly+" dense_fields="+fields);
    }

    @Test void native_normalization_then_subset_scaling_matches_the_original_pipeline() {
        List<String> selected=List.of("feature_17","feature_3","feature_7");
        for(String method:List.of("prepared_log","prepared_clr")) {
            Matrix nativeInput=method.equals("prepared_log")?SeuratPreprocessing.logNormalize(inputs.get("counts"),10000,LIMITS,()->false).matrix():SeuratPreprocessing.clr(inputs.get("counts"),2,LIMITS,()->false).matrix();
            Matrix original=inputs.get(method);compare(dense(original,original.values()),dense(nativeInput,nativeInput.values()),method+" normalized input");
            ScaleResult result=SeuratPreprocessing.scaleData(nativeInput,selected,ScaleOptions.defaults(),LIMITS,()->false);
            Map<String,Object> model=list(reference.get("scalings")).stream().map(SeuratPreprocessingTest::map).filter(row->row.get("dataset").equals(method)&&(Boolean)row.get("center")&&(Boolean)row.get("scale")&&row.get("cap").equals("10")).findFirst().orElseThrow();
            compare(numbers(model.get("values")),result.matrix().valuesColumnMajor(),method+" native pipeline");assertEquals(selected,result.matrix().features());
        }
    }

    @Test void fractional_log_scale_is_not_silently_replaced_by_relative_count_scale() {
        Matrix input=inputs.get("fractional");NormalizationResult log=SeuratPreprocessing.logNormalize(input,2.75,LIMITS,()->false);
        assertEquals(2.75,log.declaredScale());assertEquals(2,log.effectiveScale());assertTrue(log.warnings().stream().anyMatch(w->w.code().equals("SOURCE_INTEGER_LOG_SCALE")));
        assertArrayEquals(log.matrix().values(),SeuratPreprocessing.logNormalize(input,2,LIMITS,()->false).matrix().values());
        NormalizationResult rc=SeuratPreprocessing.relativeCounts(input,2.75,LIMITS,()->false);assertEquals(2.75,rc.effectiveScale());assertTrue(rc.warnings().stream().noneMatch(w->w.code().equals("SOURCE_INTEGER_LOG_SCALE")));
        assertThrows(IllegalArgumentException.class,()->SeuratPreprocessing.logNormalize(input,2147483648d,LIMITS,()->false));
    }

    @Test void explicit_zero_over_zero_is_retained_but_cannot_be_admitted_as_a_matrix() {
        NormalizationResult result=SeuratPreprocessing.logNormalize(inputs.get("explicit_zeros"),10000,LIMITS,()->false);
        assertFalse(result.usable());assertTrue(Arrays.stream(result.diagnosticValues()).anyMatch(Double::isNaN));assertThrows(ArithmeticException.class,result::matrix);
        assertTrue(result.warnings().stream().anyMatch(w->w.code().equals("ZERO_TOTAL_COLUMNS")));
        NormalizationResult implicit=SeuratPreprocessing.logNormalize(inputs.get("all_zero"),10000,LIMITS,()->false);
        assertTrue(implicit.usable());assertEquals(0,implicit.matrix().storedValues());assertFalse(implicit.warnings().isEmpty());
    }

    @Test void overflowed_sums_refuse_even_when_original_output_looks_finite() {
        NormalizationResult result=SeuratPreprocessing.relativeCounts(inputs.get("overflow"),10000,LIMITS,()->false);
        assertTrue(Arrays.stream(result.diagnosticValues()).allMatch(Double::isFinite));assertFalse(result.usable());assertThrows(ArithmeticException.class,result::matrix);
        assertTrue(result.warnings().stream().anyMatch(w->w.code().equals("NONFINITE_TOTALS")));
    }

    @Test void zero_sigma_is_the_explicit_source_rule_and_undefined_sigma_is_not_a_result() {
        ScaleResult zeros=SeuratPreprocessing.scaleData(inputs.get("all_zero"),null,ScaleOptions.defaults(),LIMITS,()->false);
        assertTrue(zeros.usable());assertTrue(Arrays.stream(zeros.matrix().valuesColumnMajor()).allMatch(x->x==0));assertTrue(zeros.warnings().stream().anyMatch(w->w.code().equals("SOURCE_ZERO_SIGMA_ROWS")));
        ScaleResult undefined=SeuratPreprocessing.scaleData(inputs.get("single_cell"),null,ScaleOptions.defaults(),LIMITS,()->false);
        assertFalse(undefined.usable());assertTrue(Arrays.stream(undefined.diagnosticValuesColumnMajor()).allMatch(x->x==0));assertThrows(ArithmeticException.class,undefined::matrix);
    }

    @Test void upper_clipping_remains_upper_only_even_without_scaling_or_centering() {
        ScaleResult result=SeuratPreprocessing.scaleData(inputs.get("signed"),null,new ScaleOptions(false,false,.3),LIMITS,()->false);
        double[] values=result.matrix().valuesColumnMajor();assertTrue(Arrays.stream(values).anyMatch(x->x<-.3));assertTrue(Arrays.stream(values).allMatch(x->x<=.3));
        ScaleResult negative=SeuratPreprocessing.scaleData(inputs.get("signed"),null,new ScaleOptions(false,false,-.2),LIMITS,()->false);assertTrue(negative.warnings().stream().anyMatch(w->w.code().equals("NONPOSITIVE_UPPER_CAP")));
    }

    @Test void input_and_result_buffers_and_unicode_identities_are_immutable() {
        var features=new ArrayList<>(List.of("gene α","gene:二"));var cells=new ArrayList<>(List.of("cell β","cell:三"));int[] p={0,2,3},r={0,1,0};double[] x={1,2,3};
        Matrix matrix=new Matrix(features,cells,p,r,x);features.set(0,"changed");cells.set(0,"changed");p[1]=0;r[0]=1;x[0]=100;
        assertEquals("gene α",matrix.features().get(0));assertEquals("cell β",matrix.cells().get(0));assertArrayEquals(new double[]{1,2,3},matrix.values());
        NormalizationResult result=SeuratPreprocessing.clr(matrix,2,LIMITS,()->false);double before=result.matrix().values()[0];double[] values=result.matrix().values();values[0]=999;result.diagnosticValues()[0]=888;assertEquals(before,result.matrix().values()[0]);assertThrows(UnsupportedOperationException.class,()->matrix.features().set(0,"x"));
        ScaleResult scale=SeuratPreprocessing.scaleData(matrix,null,new ScaleOptions(false,false,Double.POSITIVE_INFINITY),LIMITS,()->false);scale.matrix().valuesColumnMajor()[0]=999;assertEquals(1,scale.matrix().value(0,0));
    }

    @Test void malformed_sparse_data_and_negative_normalization_are_refused() {
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("a","b"),List.of("c"),new int[]{0,2},new int[]{1,0},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("a"),List.of("c"),new int[]{0,2},new int[]{0,0},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("a"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{Double.NaN}));
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("a","a"),List.of("c"),new int[]{0,0},new int[]{},new double[]{}));
        assertThrows(IllegalArgumentException.class,()->SeuratPreprocessing.relativeCounts(inputs.get("signed"),10000,LIMITS,()->false));
    }

    @Test void unknown_or_duplicate_selected_features_are_not_silently_dropped() {
        Matrix input=inputs.get("counts");
        for(List<String> selected:List.of(List.<String>of(),List.of("unknown"),List.of("feature_1","feature_1")))
            assertThrows(IllegalArgumentException.class,()->SeuratPreprocessing.scaleData(input,selected,ScaleOptions.defaults(),LIMITS,()->false));
    }

    @Test void capacities_cover_the_full_selected_population_and_work_before_dense_allocation() {
        Matrix input=inputs.get("counts");Limits dense=new Limits(100,100,10000,390,1000000);
        assertThrows(IllegalArgumentException.class,()->SeuratPreprocessing.scaleData(input,null,ScaleOptions.defaults(),dense,()->false));
        Limits work=new Limits(100,100,10000,1000000,0);
        assertThrows(IllegalArgumentException.class,()->SeuratPreprocessing.relativeCounts(input,10000,work,()->false));
        assertThrows(IllegalArgumentException.class,()->SeuratPreprocessing.scaleData(input,null,ScaleOptions.defaults(),work,()->false));
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("a"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{1},work,()->false));
    }

    @Test void cancellation_before_or_during_work_returns_no_partial_result() {
        assertThrows(CancellationException.class,()->SeuratPreprocessing.clr(inputs.get("counts"),1,LIMITS,()->true));
        int n=64;List<String> ids=new ArrayList<>();for(int i=0;i<n;i++)ids.add("id"+i);int[] p=new int[n+1],r=new int[n*n];double[] x=new double[n*n];Arrays.fill(x,1);
        for(int c=0;c<n;c++){p[c]=c*n;for(int k=0;k<n;k++)r[c*n+k]=k;}p[n]=x.length;Matrix input=new Matrix(ids,ids,p,r,x);AtomicInteger calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->SeuratPreprocessing.scaleData(input,null,ScaleOptions.defaults(),LIMITS,()->calls.getAndIncrement()>0));assertTrue(calls.get()>=2);assertArrayEquals(x,input.values());
    }

    private static NormalizationResult normalize(Map<String,Object> row,Matrix input) {
        return switch((String)row.get("method")) {
            case "LogNormalize" -> SeuratPreprocessing.logNormalize(input,number(row.get("scale")),LIMITS,()->false);
            case "RC" -> SeuratPreprocessing.relativeCounts(input,number(row.get("scale")),LIMITS,()->false);
            default -> SeuratPreprocessing.clr(input,((Number)row.get("margin")).intValue(),LIMITS,()->false);
        };
    }
    private static double[] dense(Matrix input,double[] values) {
        double[] out=new double[input.features().size()*input.cells().size()];int[] p=input.columnPointers(),r=input.rowIndices();
        for(int c=0;c<input.cells().size();c++)for(int k=p[c];k<p[c+1];k++)out[c*input.features().size()+r[k]]=values[k];return out;
    }
    private static void compare(double[] expected,double[] actual,String context) {
        assertEquals(expected.length,actual.length,context);
        for(int i=0;i<expected.length;i++) {
            double e=expected[i],a=actual[i];String message=context+" field "+i;
            if(Double.isNaN(e))assertTrue(Double.isNaN(a),message);
            else if(!Double.isFinite(e))assertEquals(e,a,message);
            else {assertTrue(Double.isFinite(a),message);assertEquals(e,a,Math.max(64*Math.ulp(e),3e-12*Math.abs(e)),message);}
        }
    }
    private static double number(Object value) {
        String s=value.toString();return switch(s) {case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;case "NA"->throw new AssertionError("R NA is distinct from NaN; no scalar transport substitution");default->Double.parseDouble(s);};
    }
    private static double[] numbers(Object value) {return list(value).stream().mapToDouble(SeuratPreprocessingTest::number).toArray();}
    private static int[] ints(Object value) {return list(value).stream().mapToInt(v->((Number)v).intValue()).toArray();}
    private static List<String> strings(Object value) {return list(value).stream().map(Object::toString).toList();}
    private static List<?> list(Object value) {if(value instanceof List<?> list)return list;if(value instanceof Map<?,?> map&&map.isEmpty())return List.of();return List.of(value);}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) {return (Map<String,Object>)value;}
}
