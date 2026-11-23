/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipInputStream;
import org.jlinalg.gprofiler.GprofilerJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PyScenicAucellReferenceTest {
    private static Map<String,byte[]> files()throws Exception {
        var files=new LinkedHashMap<String,byte[]>();
        try(var stream=PyScenicAucellReferenceTest.class.getResourceAsStream("/org/jlinalg/enrichment/pyscenic-original-aucell-reference.zip")){
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)files.put(entry.getName(),zip.readAllBytes());}
        }return files;
    }
    @Test void matches114OriginalNumpyPermutationsIncludingUint32SeedBoundaries()throws Exception {
        var cases=list(GprofilerJson.read(files().get("PERMUTATIONS.json")));assertEquals(114,cases.size());
        for(Object value:cases){var row=object(value);assertArrayEquals(ints(row.get("permutation")),PyScenicAucell.permutation(integer(row.get("n")),Long.parseLong(token(row.get("seed")))));}
    }
    @Test void matchesEveryOriginalSeededRankingIncludingTiesNanInfinityAndLiteralIds()throws Exception {
        var files=files();int checked=0;
        for(var entry:files.entrySet())if(entry.getKey().endsWith("/INPUT.json")){
            String prefix=entry.getKey().substring(0,entry.getKey().lastIndexOf('/'));var input=object(GprofilerJson.read(entry.getValue()));var result=object(GprofilerJson.read(files.get(prefix+"/RESULT.json")));var expected=object(result.get("ranks"));
            var actual=PyScenicAucell.rank(strings(input.get("cells")),strings(input.get("genes")),doubles(input.get("expression")),Long.parseLong(token(input.get("seed"))),PyScenicAucell.Limits.defaults());
            assertEquals(strings(expected.get("cells")),actual.cells(),prefix);assertEquals(strings(expected.get("columns")),actual.genes(),prefix);int[][] ranks=actual.ranks();var rows=list(expected.get("values"));
            for(int i=0;i<ranks.length;i++)assertArrayEquals(ints(rows.get(i)),ranks[i],prefix);checked++;
        }assertEquals(37,checked);
    }
    @Test void matchesPreparedAndExpressionApisAndRefusesAllEightUnreportableOrFailedWorkerCases()throws Exception {
        var files=files();int returned=0,refused=0,sourceRefusals=0,comparedValues=0;
        for(var entry:files.entrySet())if(entry.getKey().endsWith("/INPUT.json")){
            String prefix=entry.getKey().substring(0,entry.getKey().lastIndexOf('/'));var input=object(GprofilerJson.read(entry.getValue()));var reference=object(GprofilerJson.read(files.get(prefix+"/RESULT.json")));var rank=object(reference.get("ranks"));
            var signatures=signatures(input);var options=options(input);var cells=strings(input.get("cells"));var genes=strings(input.get("genes"));double[][] expression=doubles(input.get("expression"));
            var prepared=PyScenicAucell.prepared(strings(rank.get("cells")),strings(rank.get("columns")),integers(rank.get("values")),options.limits());
            if((Boolean)input.get("expected_native_refusal")){
                assertThrows(IllegalArgumentException.class,()->PyScenicAucell.score(prepared,signatures,options),prefix);
                assertThrows(IllegalArgumentException.class,()->PyScenicAucell.scoreExpression(cells,genes,expression,signatures,options),prefix);refused++;
                if(reference.get("status").equals("ORIGINAL_REFUSED"))sourceRefusals++;else assertTrue(prefix.equals("nonfinite-weight")||prefix.equals("parallel-failed-workers"),prefix);
                continue;
            }
            var actual=PyScenicAucell.score(prepared,signatures,options);var direct=PyScenicAucell.scoreExpression(cells,genes,expression,signatures,options);
            comparedValues+=compareFrame(object(reference.get("raw")),actual,actual.raw(),prefix);
            comparedValues+=compareFrame(object(reference.get("scores")),actual,actual.scores(),prefix);
            compareFrame(object(reference.get("expression_call")),direct,direct.scores(),prefix);returned++;
        }
        assertEquals(29,returned);assertEquals(8,refused);assertEquals(6,sourceRefusals);assertTrue(comparedValues>1000);
        assertTrue(new String(files.get("parallel-failed-workers/stderr"),StandardCharsets.UTF_8).contains("AssertionError"),"Original child failure is retained despite returned parent zero values");
    }
    @Test void missingGeneZerosAndZeroMaximumNormalizationNeverEstablishEligibleInference() {
        var cells=List.of("z","NA");var genes=List.of("NaN","Inf","g2","g3");int[][] ranks={{0,1,2,3},{3,2,1,0}};
        var prepared=PyScenicAucell.prepared(cells,genes,ranks,PyScenicAucell.Limits.defaults());
        var missing=new PyScenicAucell.Signature("absent",Map.of("absent",1.));
        var result=PyScenicAucell.score(prepared,List.of(missing),new PyScenicAucell.Options(Double.NaN,false,true,17,PyScenicAucell.Layout.SERIAL_UNSTACK,PyScenicAucell.Limits.defaults()));
        assertEquals(List.of("NA","z"),result.cells());assertFalse(result.diagnostics().get(0).cutoffApplied());assertNull(result.diagnostics().get(0).reducedRankCutoff());
        assertEquals(PyScenicAucell.Status.SOURCE_ZERO_INSUFFICIENT_GENE_COVERAGE,result.diagnostics().get(0).status());
        for(int i=0;i<2;i++){assertEquals(0,result.raw()[i][0]);assertTrue(Double.isNaN(result.scores()[i][0]));assertFalse(result.eligible(i,0));}
        var covered=new PyScenicAucell.Signature("covered",Map.of("NaN",1.));
        result=PyScenicAucell.score(prepared,List.of(covered),new PyScenicAucell.Options(.25,false,true,17,PyScenicAucell.Layout.SERIAL_UNSTACK,PyScenicAucell.Limits.defaults()));
        assertEquals(0,result.diagnostics().get(0).reducedRankCutoff());assertEquals(PyScenicAucell.Status.NONFINITE_NORMALIZATION,result.diagnostics().get(0).status());
        assertTrue(Double.isNaN(result.scores()[0][0]));assertFalse(result.eligible(0,0));
        result=PyScenicAucell.score(prepared,List.of(covered),new PyScenicAucell.Options(.25,false,false,17,PyScenicAucell.Layout.SERIAL_UNSTACK,PyScenicAucell.Limits.defaults()));
        assertEquals(PyScenicAucell.Status.SOURCE_ZERO_EMPTY_RECOVERY_WINDOW,result.diagnostics().get(0).status());
        assertEquals(0,result.scores()[0][0]);assertFalse(result.eligible(0,0),"An empty source recovery window contains no activity information");
    }
    @Test void negativeWeightsAndFiniteOriginalScoresAreRetainedWithoutClamping()throws Exception {
        var fs=files();var input=object(GprofilerJson.read(fs.get("negative-weights-False/INPUT.json")));
        var result=PyScenicAucell.scoreExpression(strings(input.get("cells")),strings(input.get("genes")),doubles(input.get("expression")),signatures(input),options(input));
        assertTrue(Arrays.stream(result.raw()).anyMatch(r->r[0]<0));assertEquals(PyScenicAucell.Status.NEGATIVE_WEIGHT_DIAGNOSTIC,result.diagnostics().get(0).status());
        assertTrue(result.diagnostics().get(0).negativeEffectiveWeights());for(int i=0;i<result.cells().size();i++)assertFalse(result.eligible(i,0));
    }
    @Test void literalAxesRanksWeightsAndReturnedArraysRemainImmutable() {
        var cells=new ArrayList<>(List.of("NA","NaN"));var genes=new ArrayList<>(List.of("Inf","g1","g2","g3"));double[][] matrix={{4,3,2,1},{1,2,3,4}};var weights=new LinkedHashMap<String,Double>();weights.put("Inf",1.);
        var signature=new PyScenicAucell.Signature("NA",weights);var options=new PyScenicAucell.Options(.5,false,false,17,PyScenicAucell.Layout.MULTIPROCESS_INPUT_ORDER,PyScenicAucell.Limits.defaults());
        var ranked=PyScenicAucell.rank(cells,genes,matrix,17,options.limits());var result=PyScenicAucell.score(ranked,List.of(signature),options);double score=result.scores()[0][0];
        cells.set(0,"changed");genes.set(0,"changed");weights.put("Inf",99.);matrix[0][0]=99.;int original=ranked.ranks()[0][0];ranked.ranks()[0][0]=999;ranked.permutation()[0]=999;result.scores()[0][0]=999.;result.raw()[0][0]=999.;
        assertEquals(original,ranked.ranks()[0][0]);assertEquals(score,result.scores()[0][0]);assertEquals(1.,signature.geneWeights().get("Inf"));assertEquals(List.of("NA","NaN"),result.cells());
        assertThrows(UnsupportedOperationException.class,()->ranked.genes().clear());assertThrows(UnsupportedOperationException.class,()->signature.geneWeights().clear());
    }
    @Test void resourceInvalidInputAndInterruptionRefuseBeforeReturningAnyResult() {
        var cells=List.of("a");var genes=List.of("g1","g2","g3","g4");double[][] matrix={{0,1,2,3}};
        for(var limits:List.of(new PyScenicAucell.Limits(1,1_000_000,1_000_000),new PyScenicAucell.Limits(100,1,1_000_000),new PyScenicAucell.Limits(100,1_000_000,1)))assertThrows(IllegalArgumentException.class,()->PyScenicAucell.rank(cells,genes,matrix,17,limits));
        assertThrows(IllegalArgumentException.class,()->PyScenicAucell.rank(cells,List.of("g","g"),new double[][]{{0,1}},17,PyScenicAucell.Limits.defaults()));
        assertThrows(IllegalArgumentException.class,()->PyScenicAucell.prepared(cells,genes,new int[][]{{0,1,1,3}},PyScenicAucell.Limits.defaults()));
        assertThrows(IllegalArgumentException.class,()->PyScenicAucell.permutation(3,-1));assertThrows(IllegalArgumentException.class,()->PyScenicAucell.permutation(3,4294967296L));
        var joint=new PyScenicAucell.Options(.5,false,false,17,PyScenicAucell.Layout.SERIAL_UNSTACK,new PyScenicAucell.Limits(100,12,1_000_000));
        assertThrows(IllegalArgumentException.class,()->PyScenicAucell.scoreExpression(cells,genes,matrix,List.of(new PyScenicAucell.Signature("r",Map.of("g1",1.))),joint));
        Thread.currentThread().interrupt();try{assertThrows(CancellationException.class,()->PyScenicAucell.rank(cells,genes,matrix,17,PyScenicAucell.Limits.defaults()));}finally{Thread.interrupted();}
    }
    private static int compareFrame(Map<String,Object> expected,PyScenicAucell.Result result,double[][] actual,String name){
        assertEquals(strings(expected.get("cells")),result.cells(),name);assertEquals(strings(expected.get("columns")),result.regulons(),name);var rows=list(expected.get("values"));assertEquals(rows.size(),actual.length,name);int n=0;
        for(int i=0;i<rows.size();i++){var row=list(rows.get(i));assertEquals(row.size(),actual[i].length,name);for(int j=0;j<row.size();j++){close(number(row.get(j)),actual[i][j],name);n++;}}return n;
    }
    private static PyScenicAucell.Options options(Map<String,Object> input){return new PyScenicAucell.Options(number(input.get("auc_fraction")),(Boolean)input.get("no_weights"),(Boolean)input.get("normalize"),Long.parseLong(token(input.get("seed"))),integer(input.get("workers"))==1?PyScenicAucell.Layout.SERIAL_UNSTACK:PyScenicAucell.Layout.MULTIPROCESS_INPUT_ORDER,PyScenicAucell.Limits.defaults());}
    private static List<PyScenicAucell.Signature> signatures(Map<String,Object> input){var list=new ArrayList<PyScenicAucell.Signature>();for(Object value:list(input.get("signatures"))){var row=object(value);var weights=new LinkedHashMap<String,Double>();for(var entry:object(row.get("weights")).entrySet())weights.put(entry.getKey(),number(entry.getValue()));list.add(new PyScenicAucell.Signature(row.get("name").toString(),weights));}return list;}
    private static void close(double expected,double actual,String label){if(Double.isNaN(expected)){assertTrue(Double.isNaN(actual),label);return;}if(Double.isInfinite(expected)||expected==0){assertEquals(expected,actual,label);return;}assertEquals(expected,actual,2e-13*Math.abs(expected)+8*Math.ulp(expected),label);}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object value){return (List<Object>)value;}
    private static String token(Object value){return value instanceof GprofilerJson.Numeric n?n.token():value.toString();}
    private static int integer(Object value){return Integer.parseInt(token(value));}
    private static double number(Object value){return Double.parseDouble(token(value));}
    private static List<String> strings(Object value){return list(value).stream().map(Object::toString).toList();}
    private static int[] ints(Object value){return list(value).stream().mapToInt(PyScenicAucellReferenceTest::integer).toArray();}
    private static int[][] integers(Object value){return list(value).stream().map(PyScenicAucellReferenceTest::ints).toArray(int[][]::new);}
    private static double[][] doubles(Object value){return list(value).stream().map(row->list(row).stream().mapToDouble(PyScenicAucellReferenceTest::number).toArray()).toArray(double[][]::new);}
    public static void main(String[] args)throws Exception {var instance=new PyScenicAucellReferenceTest();int n=0;for(var method:PyScenicAucellReferenceTest.class.getDeclaredMethods())if(method.isAnnotationPresent(Test.class)){method.invoke(instance);System.out.println("PASS "+method.getName());n++;}assertEquals(7,n);System.out.println("PASS all7 pySCENIC AUCell original-reference methods");}
}
