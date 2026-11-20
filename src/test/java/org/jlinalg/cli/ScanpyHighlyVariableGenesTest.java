/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.singlecell.ScanpyHighlyVariableGenes;
import org.jlinalg.singlecell.ScanpyHighlyVariableGenes.*;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Complete original-package controls, with independent admission/capacity checks. */
final class ScanpyHighlyVariableGenesTest {
    private static Map<String,Object> object(Object raw)throws IOException {
        if(!(raw instanceof Map<?,?> map))throw new IOException("Require fixture object");
        Map<String,Object> result=new LinkedHashMap<>();for(var e:map.entrySet()){
            if(!(e.getKey() instanceof String key))throw new IOException("Require text fixture key");result.put(key,e.getValue());
        }return result;
    }
    private static List<?> list(Object raw)throws IOException {if(!(raw instanceof List<?> values))throw new IOException("Require fixture array");return values;}
    private static double value(Object raw){return raw instanceof Number n?n.doubleValue():Double.parseDouble(raw.toString());}
    private static String text(Map<String,Object> raw,String key){return (String)raw.get(key);}
    private static Map<String,Object> documents;
    private static synchronized Map<String,Object> documents()throws IOException {
        if(documents!=null)return documents;Map<String,Object> result=new LinkedHashMap<>();
        try(var raw=ScanpyHighlyVariableGenesTest.class.getResourceAsStream("/org/jlinalg/singlecell/scanpy-hvg/reference-20261119.zip")){
            if(raw==null)throw new IOException("Restore exact original HVG reference fixture");
            try(var zip=new ZipInputStream(raw)){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
                if(entry.getName().equals("INPUTS.json")||entry.getName().equals("1.9.3.json")||entry.getName().equals("1.13.0.json"))
                    result.put(entry.getName(),SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8)));
            }}
        }documents=Map.copyOf(result);return documents;
    }
    private static Map<String,Object> named(Object raw,String name)throws IOException {
        for(Object item:list(raw)){var record=object(item);if(name.equals(record.get("id")))return record;}
        throw new IOException("Missing original fixture identity: "+name);
    }
    private static Matrix matrix(Map<String,Object> input,String storage)throws IOException {
        var x=list(input.get("values"));int nc=x.size(),nf=list(x.get(0)).size();
        List<Integer> rows=new ArrayList<>();List<Double> values=new ArrayList<>();int[] pointers=new int[nc+1];
        for(int c=0;c<nc;c++){
            var row=list(x.get(c));for(int g=0;g<nf;g++){double v=value(row.get(g));if(!storage.equals("csr")||v!=0){rows.add(g);values.add(v);}}
            pointers[c+1]=values.size();
        }
        List<String> genes=new ArrayList<>(),cells=new ArrayList<>();
        for(Object v:list(input.get("features")))genes.add((String)v);for(Object v:list(input.get("cells")))cells.add((String)v);
        return new Matrix(genes,cells,pointers,rows.stream().mapToInt(Integer::intValue).toArray(),values.stream().mapToDouble(Double::doubleValue).toArray());
    }
    private static Options options(Map<String,Object> input,Map<String,Object> o,Map<String,Object> spec){
        return new Options(text(o,"flavor").equals("seurat")?Flavor.SEURAT:Flavor.CELL_RANGER,
            o.get("top")==null?null:((Number)o.get("top")).intValue(),((Number)o.get("bins")).intValue(),
            value(o.getOrDefault("min_mean",.0125)),value(o.getOrDefault("max_mean",3)),
            value(o.getOrDefault("min_disp",.5)),value(o.getOrDefault("max_disp","Infinity")),
            input.get("log_base")==null?null:value(input.get("log_base")),(Boolean)spec.get("filter"),
            text(spec,"export").equals("dataframe")?Export.DATAFRAME:Export.ANNDATA);
    }
    private static Batches grouping(Matrix m,Map<String,Object> spec){
        if(!Boolean.TRUE.equals(spec.get("batch")))return null;
        List<String> categories=List.of("Z","A","B"),labels=new ArrayList<>();
        for(int c=0;c<m.cells().size();c++)labels.add(categories.get(c%3));return new Batches(labels,categories);
    }
    private static void close(double expected,double actual,String label){
        if(Double.isNaN(expected)){assertTrue(Double.isNaN(actual),label+" expected NaN, got "+actual);return;}
        if(!Double.isFinite(expected)){assertEquals(expected,actual,label);return;}
        assertTrue(Double.isFinite(actual),label+" finite expected, got "+actual);
        assertEquals(expected,actual,Math.max(64*Math.ulp(expected),8e-12*Math.max(1,Math.abs(expected))),label);
    }
    private static void close(Object raw,double[] actual,String label)throws IOException {
        var expected=list(raw);assertEquals(expected.size(),actual.length,label);
        for(int g=0;g<actual.length;g++)close(value(expected.get(g)),actual[g],label+"["+g+"]");
    }
    private static void equalMask(Object raw,boolean[] actual,String label)throws IOException {
        var expected=list(raw);assertEquals(expected.size(),actual.length,label);
        for(int g=0;g<actual.length;g++)assertEquals(expected.get(g),actual[g],label+"["+g+"]");
    }
    @TestFactory Stream<DynamicTest> originalPackages()throws IOException {
        var inputs=object(documents().get("INPUTS.json"));Map<String,Map<String,Object>> specs=new HashMap<>();
        for(Object item:list(inputs.get("cases"))){var c=object(item);assertNull(specs.put(text(c,"id"),c));}
        assertEquals(1280,specs.size());List<DynamicTest> result=new ArrayList<>();
        for(String version:List.of("1.9.3","1.13.0")){
            var capture=object(documents().get(version+".json"));assertEquals(false,capture.get("source_patched"));
            var cases=list(capture.get("cases"));assertEquals(specs.size(),cases.size());Set<String> seen=new HashSet<>();
            for(Object item:cases){var original=object(item);String id=text(original,"id");assertTrue(seen.add(id));var spec=specs.get(id);assertNotNull(spec);
                var input=named(inputs.get("matrices"),text(spec,"matrix"));var o=named(inputs.get("options"),text(spec,"option"));
                result.add(DynamicTest.dynamicTest(version+"/"+id,()->{
                    Matrix m=matrix(input,text(spec,"storage"));double[] before=m.values();
                    Storage storage=text(spec,"storage").equals("dense")?Storage.DENSE:Storage.CSR;
                    Profile profile=version.equals("1.9.3")?Profile.SCANPY_1_9_3:Profile.SCANPY_1_13_0;
                    if(original.get("status").equals("original-error")){
                        assertThrows(RuntimeException.class,()->ScanpyHighlyVariableGenes.select(m,storage,profile,options(input,o,spec),grouping(m,spec),Limits.defaults(),()->false));
                    }else {
                        var selected=ScanpyHighlyVariableGenes.select(m,storage,profile,options(input,o,spec),grouping(m,spec),Limits.defaults(),()->false);
                        close(original.get("means"),selected.means(),"means");close(original.get("dispersions"),selected.dispersions(),"dispersions");
                        close(original.get("normalized"),selected.normalizedDispersions(),"normalized");equalMask(original.get("selected"),selected.diagnosticSelected(),"selected");
                        assertEquals(list(original.get("feature_order")),selected.features(),"original order");
                        if(Boolean.TRUE.equals(spec.get("batch"))){
                            var counts=list(original.get("batch_counts"));int[] actual=selected.highlyVariableBatchCounts();assertEquals(counts.size(),actual.length);
                            for(int g=0;g<actual.length;g++)assertEquals(((Number)counts.get(g)).intValue(),actual[g],"batch count "+g);
                            equalMask(original.get("intersection"),selected.highlyVariableIntersection(),"intersection");
                        }else assertEquals(0,selected.highlyVariableBatchCounts().length);
                        if(selected.usable())assertArrayEquals(selected.diagnosticSelected(),selected.selected());
                        else {assertFalse(selected.warnings().isEmpty());assertThrows(ArithmeticException.class,selected::selected);}
                        if(text(spec,"matrix").equals("rich")&&text(o,"flavor").equals("seurat"))assertTrue(selected.usable(),"Ordinary complete source case must be admitted");
                    }
                    assertArrayEquals(before,m.values(),"The Java API keeps its declared immutable input, including legacy source side-effect cases");
                }));
            }
        }return result.stream();
    }
    private static Matrix rich()throws IOException {return matrix(named(object(documents().get("INPUTS.json")).get("matrices"),"rich"),"dense");}
    private static Result simple(Matrix m){return ScanpyHighlyVariableGenes.select(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(Flavor.SEURAT),null,Limits.defaults(),()->false);}
    @Test void interruptionIsPreserved()throws IOException {Matrix m=rich();Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->simple(m));assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}}
    @Test void midAnalysisCancellationHasNoResult()throws IOException {Matrix m=rich();AtomicInteger calls=new AtomicInteger();assertThrows(CancellationException.class,()->ScanpyHighlyVariableGenes.select(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(Flavor.SEURAT),null,Limits.defaults(),()->calls.incrementAndGet()>2));assertTrue(calls.get()>2);}
    @Test void capacitiesRefuseCompleteInputBeforeArithmetic()throws IOException {Matrix m=rich();assertThrows(IllegalArgumentException.class,()->ScanpyHighlyVariableGenes.select(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(Flavor.SEURAT),null,new Limits(1000,1000,100000,10,1,Long.MAX_VALUE),()->false));}
    @Test void workLimitIsNotSilentlyExpanded()throws IOException {Matrix m=rich();assertThrows(IllegalArgumentException.class,()->ScanpyHighlyVariableGenes.select(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(Flavor.SEURAT),null,new Limits(1000,1000,100000,10,Long.MAX_VALUE,1),()->false));}
    @Test void returnedArraysCannotMutateSelection()throws IOException {var r=simple(rich());assertTrue(r.usable());boolean[] a=r.selected();a[0]=!a[0];assertNotEquals(a[0],r.selected()[0]);double[] means=r.means();means[0]=12345;assertNotEquals(12345,r.means()[0]);assertThrows(UnsupportedOperationException.class,()->r.warnings().clear());assertThrows(UnsupportedOperationException.class,()->r.batchDiagnostics().clear());}
    @Test void inputBatchLabelsAndCategoriesAreNotRepaired(){assertThrows(IllegalArgumentException.class,()->new Batches(List.of("missing"),List.of("known")));assertThrows(IllegalArgumentException.class,()->new Batches(List.of("known"),List.of("known","known")));}
    @Test void zeroRequestedGenesIsAnInvalidCount(){assertThrows(IllegalArgumentException.class,()->new Options(Flavor.SEURAT,0,20,0,3,.5,Double.POSITIVE_INFINITY,null,null,Export.DATAFRAME));}
    @Test void invalidLogBaseIsNotChanged(){assertThrows(IllegalArgumentException.class,()->new Options(Flavor.SEURAT,null,20,0,3,.5,Double.POSITIVE_INFINITY,1.0,null,Export.DATAFRAME));}
    @Test void emptyDeclaredBatchIsNotDropped()throws IOException {Matrix m=rich();List<String> labels=new ArrayList<>(Collections.nCopies(m.cells().size(),"known"));assertThrows(ArithmeticException.class,()->ScanpyHighlyVariableGenes.select(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(Flavor.SEURAT),new Batches(labels,List.of("known","unused")),Limits.defaults(),()->false));}
    @Test void denseStorageCannotBeAssumedFromSparseValues(){Matrix m=new Matrix(List.of("A","B"),List.of("001","002"),new int[]{0,1,2},new int[]{0,1},new double[]{1,2});assertThrows(IllegalArgumentException.class,()->simple(m));}
    @Test void sourceZeroVarianceMissingAnnotationsRemainExplicit()throws IOException {
        Matrix m=matrix(named(object(documents().get("INPUTS.json")).get("matrices"),"zero-genes"),"dense");
        var r=simple(m);assertTrue(r.usable());assertTrue(Double.isNaN(r.dispersions()[0]));
        assertTrue(r.batchDiagnostics().get(0).sourceMissingZeroVariance()[0]);
        assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("SOURCE_ZERO_VARIANCE_MISSING_DISPERSION")));
        assertFalse(r.selected()[0],"The current declared default cutoff excludes this zero-variance gene");
    }
    @Test void positiveMomentUnderflowDoesNotBecomeAnAdmittedMissingAnnotation()throws IOException {
        Matrix m=matrix(named(object(documents().get("INPUTS.json")).get("matrices"),"tiny"),"dense");
        var r=simple(m);assertFalse(r.usable());assertThrows(ArithmeticException.class,r::selected);
        assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("LOST_POSITIVE_MOMENT_TERM")));
    }
    @Test void negativeCellRangerExpressionRemainsDiagnostic()throws IOException {
        Matrix input=rich();double[] negative=input.values();for(int k=0;k<negative.length;k++)negative[k]=-negative[k];
        Matrix m=new Matrix(input.features(),input.cells(),input.columnPointers(),input.rowIndices(),negative);
        var r=ScanpyHighlyVariableGenes.select(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(Flavor.CELL_RANGER),null,Limits.defaults(),()->false);
        assertFalse(r.usable());assertThrows(ArithmeticException.class,r::selected);assertTrue(r.means()[0]<0);
        assertArrayEquals(negative,m.values(),"No absolute-value, shift or log-base repair");
    }
    @Test void sparseBatchCellScansConsumeTheDeclaredWorkBudget(){
        int n=30000;List<String> cells=new ArrayList<>(),labels=new ArrayList<>();int[] pointers=new int[n+1];
        for(int c=0;c<n;c++){cells.add("cell-"+c);labels.add(c%2==0?"A":"B");pointers[c]=Math.min(c,2);}pointers[n]=2;
        Matrix m=new Matrix(List.of("gene"),cells,pointers,new int[]{0,0},new double[]{Math.log1p(1),Math.log1p(3)});
        var options=new Options(Flavor.SEURAT,null,1,.0125,3,.5,Double.POSITIVE_INFINITY,null,null,Export.DATAFRAME);
        assertThrows(IllegalArgumentException.class,()->ScanpyHighlyVariableGenes.select(m,Storage.CSR,Profile.SCANPY_1_13_0,
            options,new Batches(labels,List.of("A","B")),new Limits(1000,100000,100000,10,1073741824L,5000),()->false));
    }
}
