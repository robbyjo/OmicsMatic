/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.fusion;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

final class FusionAssociationTest {
    private static Map<String,byte[]> fixture() throws IOException {
        var out = new HashMap<String,byte[]>();
        try (var stream = FusionAssociationTest.class.getResourceAsStream("/org/jlinalg/fusion/fusion-9346c122-original.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    if (!entry.isDirectory()) assertNull(out.put(entry.getName(),zip.readAllBytes()));
            }
        }
        return out;
    }
    private static List<Map<String,String>> rows(byte[] raw) {
        var lines = new String(raw,StandardCharsets.UTF_8).lines().toList();
        String[] keys = lines.get(0).split("\t",-1);
        var out = new ArrayList<Map<String,String>>();
        for (String line : lines.subList(1,lines.size())) {
            String[] values = line.split("\t",-1); assertEquals(keys.length,values.length);
            var row = new HashMap<String,String>();
            for(int i=0;i<keys.length;i++)row.put(keys[i],values[i]);
            out.add(row);
        }
        return out;
    }
    private static double number(String value) { return value.equals("NA")||value.equals("NaN") ? Double.NaN : Double.parseDouble(value); }
    private static double[] vector(String value) { return Arrays.stream(value.split(",",-1)).mapToDouble(FusionAssociationTest::number).toArray(); }
    private static String allele(String value) { return value.equals("<NA>") ? null : value; }
    private static FusionAssociation.Logical logical(String value) {
        return switch(value) { case "TRUE" -> FusionAssociation.Logical.TRUE; case "FALSE" -> FusionAssociation.Logical.FALSE; case "<NA>" -> FusionAssociation.Logical.NA; default -> throw new IllegalArgumentException(value); };
    }
    private static void close(double expected,double actual,String context) {
        if(Double.isNaN(expected)) assertTrue(Double.isNaN(actual),context);
        else assertEquals(expected,actual,1e-11+1e-10*Math.abs(expected),context);
    }
    private static void close(double[] expected,double[] actual,String context) {
        assertEquals(expected.length,actual.length,context);
        for(int i=0;i<expected.length;i++)close(expected[i],actual[i],context+" ["+i+"]");
    }

    @Test void all6561OriginalAlleleLogicalCasesIncludingUnknownAndMissingValues() throws IOException {
        var values = rows(fixture().get("alleles.tsv")); assertEquals(6561,values.size());
        for(var row:values) {
            var actual = FusionAssociation.alleleQc(allele(row.get("a1")),allele(row.get("a2")),allele(row.get("ref1")),allele(row.get("ref2")));
            assertEquals(new FusionAssociation.AlleleFlags(logical(row.get("keep")),logical(row.get("flip"))),actual,row.toString());
        }
    }

    @Test void all136OriginalPreparedAssociationsRetain118CompleteAnd18NoResultCases() throws IOException {
        var values = rows(fixture().get("associations.tsv")); assertEquals(136,values.size());
        int complete = 0, absent = 0;
        for(var row:values) {
            String id = row.get("id");
            var actual = FusionAssociation.testPrepared(vector(row.get("z_in")),vector(row.get("weights_in")),vector(row.get("ld")),
                new FusionAssociation.Options(number(row.get("maximum")),number(row.get("minimum")),row.get("top1").equals("TRUE")));
            boolean admitted = row.get("source_status").equals("COMPLETE");
            assertEquals(admitted,actual.complete(),id+" "+actual.status());
            if(admitted)complete++;else absent++;
            close(number(row.get("z")),actual.z(),id+" z");
            close(number(row.get("p")),actual.pValue(),id+" p");
            close(number(row.get("logp")),actual.logPValue(),id+" logp");
            close(number(row.get("variance")),actual.predictedVariance(),id+" variance");
            close(vector(row.get("completed_z")),actual.completedZ(),id+" imputed Z");
            close(vector(row.get("imputation_r2")),actual.imputationR2(),id+" original r2");
            close(vector(row.get("effective_weights")),actual.effectiveWeights(),id+" effective weights");
            int[] changed = row.get("zeroed_positions").isEmpty()?new int[0]:Arrays.stream(row.get("zeroed_positions").split(",")).mapToInt(Integer::parseInt).toArray();
            assertArrayEquals(changed,actual.missingWeightsZeroed(),id);
            if(changed.length>0)assertTrue(actual.warnings().stream().anyMatch(s->s.contains("NA/NaN weights")),id);
        }
        assertEquals(118,complete);assertEquals(18,absent);
    }

    @Test void missingZeroWeightMarkerStillPropagatesOriginalNaNInsteadOfBeingDropped() {
        var fit = FusionAssociation.testPrepared(new double[]{2,Double.NaN},new double[]{1,0},new double[]{1,0,0,1},new FusionAssociation.Options(.5,0,false));
        assertFalse(fit.complete());assertEquals(FusionAssociation.Status.NONFINITE_ASSOCIATION,fit.status());
        assertTrue(Double.isNaN(fit.completedZ()[1]));assertTrue(Double.isNaN(fit.pValue()));
        assertTrue(fit.warnings().stream().anyMatch(s->s.contains("No complete")));
    }

    @Test void callerInputsAndReturnedArraysAreNotMutableAliases() {
        double[] z={2,3},weights={1,2},ld={1,.3,.3,1};
        var fit=FusionAssociation.testPrepared(z,weights,ld,FusionAssociation.Options.defaults());
        fit.completedZ()[0]=100;fit.imputationR2()[0]=100;fit.effectiveWeights()[0]=100;
        assertArrayEquals(new double[]{2,3},z);assertArrayEquals(new double[]{1,2},weights);assertArrayEquals(new double[]{1,.3,.3,1},ld);
        assertEquals(2,fit.completedZ()[0]);assertEquals(1,fit.imputationR2()[0]);assertEquals(1,fit.effectiveWeights()[0]);
    }

    @Test void invalidMatricesCapacitiesAndInfiniteInputsFailExplicitly() {
        assertThrows(IllegalArgumentException.class,()->FusionAssociation.testPrepared(new double[]{1,2},new double[]{1,1},new double[]{1,2,2,1},FusionAssociation.Options.defaults()));
        assertThrows(IllegalArgumentException.class,()->FusionAssociation.testPrepared(new double[]{Double.POSITIVE_INFINITY},new double[]{1},new double[]{1},FusionAssociation.Options.defaults()));
        assertThrows(IllegalArgumentException.class,()->FusionAssociation.testPrepared(new double[FusionAssociation.MAX_VARIANTS+1],new double[FusionAssociation.MAX_VARIANTS+1],null,FusionAssociation.Options.defaults()));
        assertThrows(IllegalArgumentException.class,()->new FusionAssociation.Options(Double.NaN,.7,false));
        assertThrows(IllegalArgumentException.class,()->new FusionAssociation.Options(.5,2,false));
    }
}
