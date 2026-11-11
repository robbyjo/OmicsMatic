/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.SeuratMarkerSummary;
import static org.jlinalg.sequence.SeuratMarkerSummary.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SeuratMarkerSummaryTest {
    private static final Map<String,Object> INPUT=fixture("CASES.json"),OUTPUT=fixture("ORIGINAL.json");
    @SuppressWarnings("unchecked") private static Map<String,Object> fixture(String file) {
        try(var stream=SeuratMarkerSummaryTest.class.getResourceAsStream("/org/jlinalg/sequence/seurat-marker-summary/original-controls.zip")) {
            assertNotNull(stream);byte[] bytes=stream.readAllBytes();
            assertEquals("4d9a35b25d9f9c7457f93bdafcd6b890847d51d1fba04c0666707942f7e0972d",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry e;
                while((e=zip.getNextEntry())!=null)if(e.getName().equals(file)) {
                    Object value=SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8));
                    return file.equals("CASES.json")?Map.of("cases",value):(Map<String,Object>)value;
                }
            }
            throw new IOException("Missing fixture: "+file);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object x){return (Map<String,Object>)x;}
    private static List<?> list(Object x){return (List<?>)x;}
    private static double number(Object x){return switch(x.toString()){case "NaN","NA"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(x.toString());};}
    private static int[] indices(Object x){return list(x).stream().mapToInt(v->((Number)v).intValue()).toArray();}
    private static Slot slot(Object x){return switch(x.toString()){case "counts"->Slot.COUNTS;case "data"->Slot.DATA;case "scale.data"->Slot.SCALE_DATA;default->throw new IllegalArgumentException();};}
    private static Matrix matrix(Map<String,Object> c) {
        var dense=list(c.get("matrix"));int n=dense.size(),m=list(dense.get(0)).size();List<Integer> rows=new ArrayList<>();List<Double> values=new ArrayList<>();int[] ptr=new int[m+1];
        for(int col=0;col<m;col++) {
            for(int row=0;row<n;row++){double v=number(list(dense.get(row)).get(col));if(v!=0||(row+col)%5==0){rows.add(row);values.add(v);}}
            ptr[col+1]=rows.size();
        }
        return new Matrix(Stream.iterate(1,i->i+1).limit(n).map(i->"f"+i).toList(),Stream.iterate(1,i->i+1).limit(m).map(i->"c"+i).toList(),ptr,rows.stream().mapToInt(i->i).toArray(),values.stream().mapToDouble(v->v).toArray());
    }
    private static void same(double a,double b) {
        if(!Double.isFinite(a))assertEquals(a,b);
        else assertEquals(a,b,Math.max(3e-12,Math.abs(a)*3e-12));
    }
    static Stream<Map<String,Object>> originalCases(){assertEquals(162,list(INPUT.get("cases")).size());return list(INPUT.get("cases")).stream().map(SeuratMarkerSummaryTest::object);}
    @ParameterizedTest(name="six-edition original summary {index}") @MethodSource("originalCases")
    void literalOriginalFoldSummariesFiltersAndControlledPValuePostprocessingAgree(Map<String,Object> c) {
        String id=c.get("id").toString();var expected=list(OUTPUT.get("cases")).stream().map(SeuratMarkerSummaryTest::object).filter(x->x.get("id").equals(id)).findFirst().orElseThrow();
        var options=new Options(Profile.valueOf("V"+c.get("version").toString().replace('.','_')),slot(c.get("slot")),Normalization.valueOf(c.get("normalization").toString()),number(c.get("pseudocount")),number(c.get("base")));
        var result=summarize(matrix(c),indices(c.get("group1")),indices(c.get("group2")),options);
        var source=list(expected.get("summary"));assertEquals(source.size(),result.features().size());
        for(int i=0;i<source.size();i++) {
            var row=object(source.get(i));var values=object(row.get("values"));var actual=result.features().get(i);assertEquals(row.get("id"),actual.id());
            String fc=values.keySet().stream().filter(k->k.startsWith("avg_")).findFirst().orElseThrow();same(number(values.get(fc)),actual.foldChange());same(number(values.get("pct.1")),actual.fraction1());same(number(values.get("pct.2")),actual.fraction2());
        }
        var filters=new Filters(number(c.get("min_pct")),number(c.get("min_diff_pct")),number(c.get("logfc_threshold")),(Boolean)c.get("only_pos"));var selected=prefilter(result,filters,slot(c.get("testing_slot")));
        assertEquals(list(expected.get("tested")),selected.stream().map(Feature::id).toList());
        double[] p=selected.stream().mapToDouble(f->number(list(c.get("p_values")).get(f.originalIndex()))).toArray();
        var markers=list(expected.get("markers"));
        if(markers.stream().anyMatch(r->!object(r).get("id").toString().startsWith("f"))) {
            assertThrows(IllegalArgumentException.class,()->finish(result,selected,p,filters.onlyPositive()));return;
        }
        var finalized=finish(result,selected,p,filters.onlyPositive());assertEquals(markers.size(),finalized.size());
        for(int i=0;i<markers.size();i++) {
            var row=object(markers.get(i));var values=object(row.get("values"));var a=finalized.get(i);assertEquals(row.get("id"),a.feature().id());same(number(values.get("p_val")),a.pValue());same(number(values.get("p_val_adj")),a.adjustedP());
        }
    }
    @Test void originalRThreeDigitRoundingCoversAllEnumeratedDetectionFractions() {
        int compared=0;for(Object r:list(OUTPUT.get("rounding"))) {
            var row=object(r);int n=((Number)row.get("n")).intValue();var values=list(row.get("values"));assertEquals(n+1,values.size());
            for(int k=0;k<=n;k++){assertEquals(number(values.get(k)),roundedDetection(k,n),0,"n="+n+",k="+k);compared++;}
        }
        assertEquals(2638,compared);
    }
    @Test void invalidPopulationsMatricesSettingsAndWorkFailWithoutRepair() {
        var m=new Matrix(List.of("peak_日本"),List.of("cell_α","cell_β"),new int[]{0,1,2},new int[]{0,0},new double[]{2,1});
        assertEquals("peak_日本",summarize(m,new int[]{0},new int[]{1},Options.current(Slot.COUNTS)).features().get(0).id());
        for(int[][] groups:new int[][][]{ {new int[]{},new int[]{1}}, {new int[]{0},new int[]{0}}, {new int[]{0,0},new int[]{1}}, {new int[]{2},new int[]{1}} })assertThrows(IllegalArgumentException.class,()->summarize(m,groups[0],groups[1],Options.current(Slot.COUNTS)));
        assertThrows(IllegalArgumentException.class,()->summarize(m,new int[]{0},new int[]{1},Options.current(Slot.COUNTS),0,()->false));
        assertThrows(CancellationException.class,()->summarize(m,new int[]{0},new int[]{1},Options.current(Slot.COUNTS),100,()->true));
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("x"),List.of("a"),new int[]{0,2},new int[]{0,0},new double[]{1,2}));
        assertThrows(IllegalArgumentException.class,()->new Matrix(List.of("x"),List.of("a"),new int[]{0,1},new int[]{0},new double[]{Double.NaN}));
        assertThrows(IllegalArgumentException.class,()->new Options(Profile.V5_6_0,Slot.COUNTS,Normalization.UNRECORDED,1,1));
        assertThrows(IllegalArgumentException.class,()->roundedDetection(2,1));
    }
    @Test void inputArraysAndSummaryRecordsAreDefensiveAndCorrectionUsesFullAssayFamily() {
        int[] ptr={0,2,4},rows={0,1,0,1};double[] values={2,0,1,0};var m=new Matrix(List.of("x","zero"),List.of("a","b"),ptr,rows,values);values[0]=100;rows[0]=1;ptr[1]=0;
        var result=summarize(m,new int[]{0},new int[]{1},Options.current(Slot.COUNTS));same(Math.log(1.5)/Math.log(2),result.features().get(0).foldChange());
        var tested=List.of(result.features().get(0));same(.04,finish(result,tested,new double[]{.02},false).get(0).adjustedP());
        assertThrows(UnsupportedOperationException.class,()->result.features().clear());assertThrows(IllegalArgumentException.class,()->finish(result,tested,new double[]{1.1},false));
        assertThrows(IllegalArgumentException.class,()->finish(result,List.of(tested.get(0),tested.get(0)),new double[]{.1,.2},false));
    }
}
