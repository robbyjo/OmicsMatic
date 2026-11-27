/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.confounding;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** Complete numeric outputs from unchanged original R functions, not fitted examples. */
class ComBatSva346Test {
    private static final Map<String,String> FILES=fixtures();
    private static Map<String,String> fixtures() {
        var result=new LinkedHashMap<String,String>();
        try(var input=new ZipInputStream(Objects.requireNonNull(ComBatSva346Test.class.getResourceAsStream("sva346-original-reference.zip")))) {
            for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()) {
                assertFalse(entry.isDirectory());assertNull(result.put(entry.getName(),new String(input.readAllBytes(),StandardCharsets.UTF_8)));
            }
        }catch(IOException error){throw new UncheckedIOException(error);}
        return result;
    }
    private static List<String[]> table(String name) {
        return FILES.get(name).lines().map(line->line.split("\t",-1)).toList();
    }
    private static double number(String x){return x.equals("NA")||x.equals("NaN")?Double.NaN:Double.parseDouble(x);}
    private static double[][] matrix(String name) {
        var rows=table(name);double[][] result=new double[rows.size()-1][rows.get(0).length-1];
        for(int i=1;i<rows.size();i++)for(int j=1;j<rows.get(i).length;j++)result[i-1][j-1]=number(rows.get(i)[j]);return result;
    }
    private static String[] batch(String name){return table(name+"/batch.tsv").stream().skip(1).map(row->row[1]).toArray(String[]::new);}
    private static ComBat.Options options(String name,int iterations) {
        var settings=new HashMap<String,String>();table(name+"/settings.tsv").stream().skip(1).forEach(row->settings.put(row[0],row[1]));
        return new ComBat.Options(settings.get("parametric").equals("TRUE"),settings.get("mean_only").equals("TRUE"),settings.get("reference_batch").isEmpty()?null:settings.get("reference_batch"),1e-4,iterations);
    }
    private static ComBat.Result fit(String name){return fit(name,10000);}
    private static ComBat.Result fit(String name,int iterations) {
        return ComBat.adjustSva346(matrix(name+"/matrix.tsv"),batch(name),FILES.containsKey(name+"/design.tsv")?matrix(name+"/design.tsv"):null,options(name,iterations));
    }
    private static void compare(double expected,double actual,String label) {
        if(Double.isNaN(expected))assertTrue(Double.isNaN(actual),label+" expected original missing/undefined value");
        else {assertTrue(Double.isFinite(actual),label+" unexpected nonfinite result");assertEquals(expected,actual,2e-10*Math.max(1,Math.abs(expected)),label);}
    }
    @Test void allSixteenOriginalFiniteCasesMatchEveryAdjustedValueAndEstimatedPosterior() {
        int cases=0,values=0;
        for(String[] row:table("cases.tsv").subList(1,table("cases.tsv").size())) {
            if(!row[1].equals("COMPLETE_FINITE_OBSERVED_VALUES"))continue;
            String name=row[0];var result=fit(name);double[][] expected=matrix(name+"/adjusted.tsv");
            for(int f=0;f<expected.length;f++)for(int s=0;s<expected[f].length;s++){compare(expected[f][s],result.adjusted()[f][s],name+" adjusted "+f+","+s);values++;}
            var gamma=matrix(name+"/gamma.tsv");var delta=matrix(name+"/delta.tsv");var statuses=table(name+"/feature-status.tsv");var excluded=new ArrayList<Integer>();
            assertEquals(result.batches(),table(name+"/gamma.tsv").stream().skip(1).map(r->r[0]).toList(),name+" batch levels");
            for(int f=0;f<expected.length;f++) {
                if(statuses.get(f+1)[1].equals("FALSE")){excluded.add(f);continue;}
                for(int b=0;b<gamma.length;b++){compare(gamma[b][f],result.gamma()[b][f],name+" gamma "+b+","+f);compare(delta[b][f],result.delta()[b][f],name+" delta "+b+","+f);values+=2;}
            }
            assertArrayEquals(excluded.stream().mapToInt(Integer::intValue).toArray(),result.unadjustedFeatures(),name+" retained original rows");cases++;
        }
        assertEquals(16,cases);assertTrue(values>29000,"Compare full returned matrices and fitted posteriors, not summaries");
    }
    @Test void originalUnderflowRemainsUndefinedInsteadOfUsingTheLegacyStabilizedLikelihood() {
        String name="NONPARAMETRIC_UNDERFLOW";var original=fit(name);var expected=matrix(name+"/adjusted.tsv");
        for(int i=0;i<expected.length;i++)for(int j=0;j<expected[i].length;j++){assertTrue(Double.isNaN(expected[i][j]));assertTrue(Double.isNaN(original.adjusted()[i][j]));}
        var legacy=ComBat.adjust(matrix(name+"/matrix.tsv"),batch(name),null,options(name,10000));
        assertTrue(Arrays.stream(legacy.adjusted()).flatMapToDouble(Arrays::stream).allMatch(Double::isFinite));
        name="MISSING_VARIANCE_NONPARAMETRIC";original=fit(name);expected=matrix(name+"/adjusted.tsv");
        for(int i=0;i<expected.length;i++)for(int j=0;j<expected[i].length;j++)compare(expected[i][j],original.adjusted()[i][j],"Missing original posterior variance "+i+","+j);
    }
    @Test void originalRankAndDegeneratePriorFailuresAndNativeCapacityAdmitNoFit() {
        for(String name:List.of("CONFOUNDED","ALL_CONSTANT","SINGLE_FEATURE","MISSING_VARIANCE_PARAMETRIC"))assertThrows(IllegalArgumentException.class,()->fit(name),name);
        assertThrows(IllegalArgumentException.class,()->fit("PARAMETRIC",1));
        assertThrows(IllegalArgumentException.class,()->ComBat.adjustSva346(matrix("PARAMETRIC/matrix.tsv"),batch("PARAMETRIC"),matrix("PARAMETRIC/design.tsv"),new ComBat.Options(true,false,null,1e-3,10000)));
    }
    @Test void missingDataAndOriginalSignedStoppingAreDistinguishableFromLegacyBehavior() {
        for(String name:List.of("MISSING","SIGNED_STOPPING")) {
            var original=fit(name);var legacy=ComBat.adjust(matrix(name+"/matrix.tsv"),batch(name),FILES.containsKey(name+"/design.tsv")?matrix(name+"/design.tsv"):null,options(name,10000));double maximum=0;
            for(int i=0;i<original.adjusted().length;i++)for(int j=0;j<original.adjusted()[i].length;j++)if(Double.isFinite(original.adjusted()[i][j]))maximum=Math.max(maximum,Math.abs(original.adjusted()[i][j]-legacy.adjusted()[i][j]));
            assertTrue(maximum>1e-12,name+" must exercise its specific source difference");
        }
        assertEquals(0,fit("MISSING_CONSTANT").unadjustedFeatures().length);
    }
    @Test void inputAndReferenceBatchRemainBitwiseUnchanged() {
        String name="REFERENCE";double[][] data=matrix(name+"/matrix.tsv"),copy=Arrays.stream(data).map(double[]::clone).toArray(double[][]::new);String[] batches=batch(name),batchCopy=batches.clone();double[][] design=matrix(name+"/design.tsv"),designCopy=Arrays.stream(design).map(double[]::clone).toArray(double[][]::new);
        var result=ComBat.adjustSva346(data,batches,design,options(name,10000));
        for(int i=0;i<data.length;i++){assertArrayEquals(copy[i],data[i]);for(int j=0;j<data[i].length;j++)if(batches[j].equals("a"))assertEquals(Double.doubleToLongBits(data[i][j]),Double.doubleToLongBits(result.adjusted()[i][j]));}
        assertArrayEquals(batchCopy,batches);for(int i=0;i<design.length;i++)assertArrayEquals(designCopy[i],design[i]);
        var unadjusted=fit("ZERO_VARIANCE");var originalData=matrix("ZERO_VARIANCE/matrix.tsv");assertEquals(4,unadjusted.unadjustedFeatures().length);for(int feature:unadjusted.unadjustedFeatures())assertArrayEquals(originalData[feature],unadjusted.adjusted()[feature]);
    }
    @Test void callerCancellationPropagatesWithoutReturningAnUnfinishedFit() {
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.util.concurrent.CancellationException.class,()->ComBat.adjustSva346(matrix("PARAMETRIC/matrix.tsv"),batch("PARAMETRIC"),matrix("PARAMETRIC/design.tsv"),options("PARAMETRIC",10000),()->{if(checks.incrementAndGet()==20)throw new java.util.concurrent.CancellationException("caller cancelled");}));
        assertEquals(20,checks.get());
    }
}
