/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.singlecell.ScanpyPearsonResiduals;
import org.jlinalg.singlecell.ScanpyPearsonResiduals.*;
import org.jlinalg.sequence.SeuratPreprocessing.Matrix;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Original complete-package float64 controls, with separate admission/boundary assertions. */
final class ScanpyPearsonResidualsTest {
    private static Map<String,Object> object(Object raw,String label)throws IOException {if(!(raw instanceof Map<?,?> m))throw new IOException("Require object: "+label);Map<String,Object> result=new LinkedHashMap<>();for(var e:m.entrySet()){if(!(e.getKey() instanceof String key))throw new IOException("Require text key");result.put(key,e.getValue());}return result;}
    private static List<?> list(Object raw,String label)throws IOException {if(!(raw instanceof List<?> value))throw new IOException("Require array: "+label);return value;}
    private static String text(Map<String,Object> raw,String key)throws IOException {if(!(raw.get(key) instanceof String s))throw new IOException("Require string: "+key);return s;}
    private static boolean bool(Map<String,Object> raw,String key,boolean fallback)throws IOException {if(!raw.containsKey(key))return fallback;if(!(raw.get(key) instanceof Boolean b))throw new IOException("Require boolean: "+key);return b;}
    private static Map<String,Object> documents;
    private static synchronized Map<String,Object> documents()throws IOException {
        if(documents!=null)return documents;Map<String,Object> result=new LinkedHashMap<>();
        try(var raw=ScanpyPearsonResidualsTest.class.getResourceAsStream("/org/jlinalg/singlecell/scanpy-pearson/reference-20261119.zip")) {
            if(raw==null)throw new IOException("Exact original Pearson reference fixture missing");
            try(var zip=new ZipInputStream(raw)){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())result.put(entry.getName(),SimpleJson.parse(new String(zip.readAllBytes(),StandardCharsets.UTF_8)));}
        }documents=Map.copyOf(result);return documents;
    }
    private static double value(Object raw){return raw instanceof Number n?n.doubleValue():Double.parseDouble(raw.toString());}
    private static double[] vector(Object raw)throws IOException {var list=list(raw,"vector");double[] result=new double[list.size()];for(int i=0;i<result.length;i++)result[i]=value(list.get(i));return result;}
    private static Matrix matrix(Map<String,Object> input,String storage)throws IOException {
        var cells=list(input.get("values"),"cells");int nc=cells.size(),nf=list(cells.get(0),"genes").size();
        List<Integer> rows=new ArrayList<>();List<Double> values=new ArrayList<>();int[] pointers=new int[nc+1];
        for(int c=0;c<nc;c++){var row=list(cells.get(c),"row");for(int g=0;g<nf;g++){double x=value(row.get(g));if(!storage.equals("csr")||x!=0){rows.add(g);values.add(x);}}pointers[c+1]=values.size();}
        List<String> names=new ArrayList<>(),ids=new ArrayList<>();for(int g=0;g<nf;g++)names.add("gene-"+g);for(int c=0;c<nc;c++)ids.add(c==0?"001":"cell-"+c);
        return new Matrix(names,ids,pointers,rows.stream().mapToInt(Integer::intValue).toArray(),values.stream().mapToDouble(Double::doubleValue).toArray());
    }
    private static void close(double expected,double actual,String label) {
        if(Double.isNaN(expected)){assertTrue(Double.isNaN(actual),label+" expected NaN, got "+actual);return;}
        if(!Double.isFinite(expected)){assertEquals(expected,actual,label);return;}
        assertTrue(Double.isFinite(actual),label+" finite expected, got "+actual);
        double tolerance=Math.max(64*Math.ulp(expected),8e-12*Math.max(1,Math.abs(expected)));
        assertEquals(expected,actual,tolerance,label);
    }
    private static void close(double[] expected,double[] actual,String label){assertEquals(expected.length,actual.length,label);for(int i=0;i<expected.length;i++)close(expected[i],actual[i],label+"["+i+"]");}
    @TestFactory Stream<DynamicTest> originalPackages()throws IOException {
        var fixture=object(documents().get("INPUTS.json"),"inputs");Map<String,Map<String,Object>> inputs=new LinkedHashMap<>();
        for(Object item:list(fixture.get("matrices"),"matrices")){var input=object(item,"input");inputs.put(text(input,"id"),input);}
        List<DynamicTest> tests=new ArrayList<>();
        for(String version:List.of("1.10.4","1.13.0")) {
            var capture=object(documents().get(version+".json"),"capture");var cases=list(capture.get("cases"),"cases");assertEquals(360,cases.size());
            for(Object item:cases){var c=object(item,"case");tests.add(DynamicTest.dynamicTest(version+"/"+text(c,"id"),()->{
                assertEquals("original-returned",c.get("status"));assertEquals(true,c.get("input_unchanged"));
                String storage=text(c,"storage");Matrix input=matrix(inputs.get(text(c,"input_id")),storage);double[] before=input.values();
                var settings=object(c.get("options"),"options");var options=new Options(value(settings.get("theta")),settings.get("clip")==null?null:value(settings.get("clip")),bool(settings,"check_values",true));
                var result=ScanpyPearsonResiduals.normalize(input,storage.equals("dense")?Storage.DENSE:Storage.CSR,version.equals("1.10.4")?Profile.SCANPY_1_10_4:Profile.SCANPY_1_13_0,options,Limits.defaults(),()->false);
                assertArrayEquals(before,input.values(),"Input must remain unchanged");close(vector(c.get("values")),result.diagnosticValues(),"source residual");
                close(vector(c.get("feature_totals")),result.featureTotals(),"source feature total");close(vector(c.get("cell_totals")),result.cellTotals(),"source cell total");close(value(c.get("grand_total")),result.grandTotal(),"source grand total");
                int nf=input.features().size(),nc=input.cells().size();double[] expectedMu=vector(c.get("mu"));for(int cell=0;cell<nc;cell++)for(int g=0;g<nf;g++)close(expectedMu[cell*nf+g],result.expectedCount(g,cell),"source expected count");
                assertEquals(c.get("project_arithmetic_admissible"),result.usable(),"Source terms define numerical admission separately from clipping");
                close(options.clip()==null?Math.sqrt(nc):options.clip(),result.effectiveClip(),"effective clipping");assertEquals(options,result.options());
                if(result.usable()){Matrix output=result.matrix();assertEquals(input.features(),output.features());assertEquals(input.cells(),output.cells());assertEquals("001",output.cells().get(0));close(vector(c.get("values")),output.values(),"materialized result");}
                else {assertFalse(result.warnings().isEmpty());assertThrows(ArithmeticException.class,result::matrix);}
            }));}
        }return tests.stream();
    }
    private static Matrix small(){return new Matrix(List.of("zero","gene"),List.of("001","002"),new int[]{0,1,2},new int[]{1,1},new double[]{2,3});}
    private static Result normalize(Matrix m,Profile profile){return ScanpyPearsonResiduals.normalize(m,Storage.CSR,profile,Options.defaults(),Limits.defaults(),()->false);}
    @Test void historicalZeroConventionIsNotReplacedWithCurrentZeroConvention(){var old=normalize(small(),Profile.SCANPY_1_10_4);var current=normalize(small(),Profile.SCANPY_1_13_0);assertFalse(old.usable());assertTrue(current.usable());assertTrue(Double.isNaN(old.diagnosticValues()[0]));assertEquals(0,current.diagnosticValues()[0]);assertTrue(current.warnings().stream().anyMatch(w->w.code().equals("SOURCE_ZERO_EXPECTATION_CONVENTION")));}
    @Test void nonfiniteVarianceHiddenByFiniteClippingStillRefusesMatrix(){var r=ScanpyPearsonResiduals.normalize(small(),Storage.CSR,Profile.SCANPY_1_13_0,new Options(Double.MIN_VALUE,0.,true),Limits.defaults(),()->false);assertFalse(r.usable());assertTrue(Arrays.stream(r.diagnosticValues()).allMatch(Double::isFinite));assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("NONFINITE_VARIANCE")));assertThrows(ArithmeticException.class,r::matrix);}
    @Test void positiveExpectationUnderflowIsNotAdmittedAsObservedZero(){Matrix m=new Matrix(List.of("gene"),List.of("cell"),new int[]{0,1},new int[]{0},new double[]{1e-200});var r=normalize(m,Profile.SCANPY_1_13_0);assertEquals(0,r.diagnosticValues()[0]);assertFalse(r.usable());assertTrue(r.warnings().stream().anyMatch(w->w.code().equals("POSITIVE_EXPECTATION_UNDERFLOW")));}
    @Test void denseDeclarationCannotOmitZeros(){assertThrows(IllegalArgumentException.class,()->ScanpyPearsonResiduals.normalize(small(),Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(),Limits.defaults(),()->false));}
    @Test void negativeCountsAreRefusedWithoutChangingInput(){Matrix m=new Matrix(List.of("gene"),List.of("cell"),new int[]{0,1},new int[]{0},new double[]{-1});assertThrows(IllegalArgumentException.class,()->normalize(m,Profile.SCANPY_1_13_0));assertEquals(-1,m.values()[0]);}
    @Test void invalidThetaAndClippingAreRefused(){for(double theta:new double[]{0,-1,Double.NaN,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new Options(theta,null,true));assertThrows(IllegalArgumentException.class,()->new Options(100,-1.,true));assertThrows(IllegalArgumentException.class,()->new Options(100,Double.NaN,true));}
    @Test void preallocationCapacityRefusesTheWholeMatrix(){var tiny=new Limits(10,10,10,1,1000000,1000000);assertThrows(IllegalArgumentException.class,()->ScanpyPearsonResiduals.normalize(small(),Storage.CSR,Profile.SCANPY_1_13_0,Options.defaults(),tiny,()->false));}
    @Test void memoryCapacityIsSeparateFromDenseEntryCapacity(){var tiny=new Limits(10,10,10,100,1,1000000);assertThrows(IllegalArgumentException.class,()->ScanpyPearsonResiduals.normalize(small(),Storage.CSR,Profile.SCANPY_1_13_0,Options.defaults(),tiny,()->false));}
    @Test void workCapacityIsCheckedBeforeCopies(){var tiny=new Limits(10,10,10,100,1000000,1);assertThrows(IllegalArgumentException.class,()->ScanpyPearsonResiduals.normalize(small(),Storage.CSR,Profile.SCANPY_1_13_0,Options.defaults(),tiny,()->false));}
    @Test void cancellationRefusesACompleteResult(){assertThrows(CancellationException.class,()->ScanpyPearsonResiduals.normalize(small(),Storage.CSR,Profile.SCANPY_1_13_0,Options.defaults(),Limits.defaults(),()->true));}
    @Test void cancellationDuringTheCompletePopulationIsObserved()throws IOException {var fixture=object(documents().get("INPUTS.json"),"inputs");var wide=list(fixture.get("matrices"),"matrices").stream().map(v->{try{return object(v,"matrix");}catch(IOException e){throw new UncheckedIOException(e);}}).filter(m->m.get("id").equals("wide")).findFirst().orElseThrow();Matrix m=matrix(wide,"dense");AtomicInteger calls=new AtomicInteger();assertThrows(CancellationException.class,()->ScanpyPearsonResiduals.normalize(m,Storage.DENSE,Profile.SCANPY_1_13_0,Options.defaults(),Limits.defaults(),()->calls.incrementAndGet()>=3));}
    @Test void threadInterruptionIsPreserved(){Matrix m=small();boolean before=Thread.currentThread().isInterrupted();try{Thread.currentThread().interrupt();assertThrows(CancellationException.class,()->normalize(m,Profile.SCANPY_1_13_0));assertTrue(Thread.currentThread().isInterrupted());}finally{if(!before)Thread.interrupted();}}
    @Test void resultAccessorsAndMatricesDoNotExposeMutableArrays(){var r=normalize(small(),Profile.SCANPY_1_13_0);double[] first=r.diagnosticValues();first[1]=12345;assertNotEquals(12345,r.diagnosticValues()[1]);double[] sums=r.featureTotals();sums[1]=12345;assertNotEquals(12345,r.featureTotals()[1]);var output=r.matrix();double[] copied=output.values();copied[1]=12345;assertNotEquals(12345,r.matrix().values()[1]);assertThrows(UnsupportedOperationException.class,()->r.warnings().clear());}
    @Test void nonintegerSourceWarningCanBeExplicitlyDisabled(){Matrix m=new Matrix(List.of("gene"),List.of("cell"),new int[]{0,1},new int[]{0},new double[]{.5});var yes=normalize(m,Profile.SCANPY_1_13_0);var no=ScanpyPearsonResiduals.normalize(m,Storage.CSR,Profile.SCANPY_1_13_0,new Options(100,null,false),Limits.defaults(),()->false);assertTrue(yes.warnings().stream().anyMatch(w->w.code().equals("NONINTEGER_COUNTS")));assertFalse(no.warnings().stream().anyMatch(w->w.code().equals("NONINTEGER_COUNTS")));}
    @Test void implicitZerosReceiveTheirNegativeResiduals(){Matrix m=new Matrix(List.of("gene-A","gene-B"),List.of("cell-A","cell-B"),new int[]{0,1,2},new int[]{0,1},new double[]{1,1});var r=normalize(m,Profile.SCANPY_1_13_0);assertTrue(r.usable());assertTrue(r.diagnosticValues()[1]<0);assertTrue(r.diagnosticValues()[2]<0);assertEquals(4,r.matrix().storedValues());}
}
