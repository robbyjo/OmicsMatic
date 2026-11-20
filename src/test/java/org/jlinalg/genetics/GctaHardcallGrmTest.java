/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class GctaHardcallGrmTest {
    private static final GctaHardcallGrm.Limits LIMITS = GctaHardcallGrm.Limits.defaults();
    private static final List<GctaHardcallGrm.SampleId> IDS = List.of(
        new GctaHardcallGrm.SampleId("家族", "same"),
        new GctaHardcallGrm.SampleId("F2", "same"),
        new GctaHardcallGrm.SampleId("F3", "I3"));

    @Test void matchesCapturedOriginalsAndRetainsExplicitNoEstimateBoundaries() throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        try (var source = getClass().getResourceAsStream("gcta1941-hardcall-originals.zip")) {
            assertNotNull(source);
            try (var zip = new ZipInputStream(source)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                    assertNull(files.put(entry.getName(), zip.readAllBytes()));
            }
        }
        int pinned = 0;
        for (String line : text(files.get("SHA256SUMS")).lines().toList()) {
            var fields = line.split("  ", 2);
            assertEquals(fields[0], HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(files.get(fields[1]))), fields[1]);
            pinned++;
        }
        assertEquals(pinned + 1, files.size());
        int success = 0, refused = 0, fieldsChecked = 0;
        for (String line : text(files.get("CASES.tsv")).lines().skip(1).toList()) {
            String[] row = line.split("\t"); String name = row[0];
            Prepared p = prepared(files.get(name + "/input.bin"));
            float[] expected = floats(files.get(name + "/result.grm.bin"));
            float[] counts = floats(files.get(name + "/result.grm.N.bin"));
            assertArrayEquals(expected, floats(files.get(name + "/linux.grm.bin")), name);
            assertArrayEquals(counts, floats(files.get(name + "/linux.grm.N.bin")), name);
            var ids = p.ids.stream().flatMap(id -> java.util.stream.Stream.of(id.familyId(), id.individualId())).toArray(String[]::new);
            assertArrayEquals(ids, text(files.get(name + "/result.grm.id")).strip().split("\\s+"), name);
            assertArrayEquals(ids, text(files.get(name + "/linux.grm.id")).strip().split("\\s+"), name);
            if (row[1].equals("SUCCESS")) {
                var result = p.compute();
                assertEquals(Integer.parseInt(row[2]), result.includedMarkers(), name);
                assertEquals(p.ids, result.samples(), name);
                float[] actual = result.lowerTriangle(); int[] actualCounts = result.pairCounts();
                assertEquals(expected.length, actual.length);
                for (int i = 0; i < expected.length; i++) {
                    assertEquals(Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(actual[i]), name + " GRM " + i);
                    assertEquals(counts[i], (float) actualCounts[i], name + " N " + i);
                }
                String[] dispositions = text(files.get(name + "/dispositions.txt")).lines().toArray(String[]::new);
                assertEquals(dispositions.length, result.markers().size());
                for (int i = 0; i < dispositions.length; i++)
                    assertEquals(dispositions[i], result.markers().get(i).disposition().name(), name + " marker " + i);
                fieldsChecked += 2 * expected.length; success++;
            } else {
                var error = assertThrows(IllegalArgumentException.class, p::compute, name);
                assertTrue(error.getMessage().contains(row[1].equals("REFUSE_EMPTY_PAIR")
                    ? "no jointly called retained markers" : "no source-valid polymorphic markers"), name);
                boolean hasEmpty = false;
                for (int i = 0; i < counts.length; i++) {
                    assertTrue(Float.isFinite(expected[i]), name);
                    if (counts[i] == 0) { assertEquals(0.0f, expected[i], name); hasEmpty = true; }
                }
                assertTrue(hasEmpty, "Original zero fallback must be evidenced: " + name); refused++;
            }
        }
        assertEquals(29, success); assertEquals(6, refused);
        assertEquals(190428, fieldsChecked);
    }

    @Test void preservesIndefinitePairwiseMatrixAndDistinguishesConstantHeterozygotes() {
        double nan = Double.NaN;
        for (var algorithm : GctaHardcallGrm.Algorithm.values()) {
            var result = compute(IDS, List.of("a", "b", "c"),
                new double[][] {{0, 2, nan}, {nan, 0, 2}, {2, nan, 0}}, algorithm);
            // Eigenvalues -2,4,4: a valid source matrix, no PSD repair.
            assertArrayEquals(new float[] {2, -2, 2, -2, -2, 2}, result.lowerTriangle());
            assertArrayEquals(new int[] {2, 1, 2, 1, 1, 2}, result.pairCounts());
        }
        var heterozygote = compute(IDS, List.of("het", "zero", "two", "missing"),
            new double[][] {{1,1,1}, {0,0,0}, {2,2,2}, {nan,nan,nan}}, GctaHardcallGrm.Algorithm.SOURCE_0);
        assertEquals(1, heterozygote.includedMarkers());
        assertArrayEquals(new int[] {1,1,1,1,1,1}, heterozygote.pairCounts());
        assertArrayEquals(new float[6], heterozygote.lowerTriangle());
        assertEquals(GctaHardcallGrm.Disposition.INCLUDED, heterozygote.markers().get(0).disposition());
        assertEquals(0.5, heterozygote.markers().get(0).hweVariance());
        assertEquals(GctaHardcallGrm.Disposition.MONOMORPHIC, heterozygote.markers().get(1).disposition());
        assertEquals(GctaHardcallGrm.Disposition.NO_CALLED_ALLELES, heterozygote.markers().get(3).disposition());
        assertNull(heterozygote.markers().get(3).suppliedAlleleMean());
        assertNull(heterozygote.markers().get(3).hweVariance());
    }

    @Test void snapshotsInputsBeforeCallbacksAndDefendsResultStorage() {
        var ids = new ArrayList<>(IDS); var names = new ArrayList<>(List.of("a", "b"));
        double[][] g = {{0,1,2}, {2,1,0}};
        var result = GctaHardcallGrm.compute(ids, names, g, GctaHardcallGrm.Algorithm.SOURCE_0, LIMITS,
            (done,total,included) -> { ids.clear(); names.clear(); Arrays.fill(g[0], 99); });
        assertEquals(IDS, result.samples()); assertEquals("a", result.markers().get(0).id());
        float original = result.relationship(0,0); int count = result.jointlyCalledMarkers(0,0);
        float[] a = result.lowerTriangle(); int[] n = result.pairCounts(); a[0]=99; n[0]=99;
        assertEquals(original, result.relationship(0,0)); assertEquals(count, result.jointlyCalledMarkers(0,0));
        assertEquals(result.relationship(1,0), result.relationship(0,1));
        assertThrows(UnsupportedOperationException.class, () -> result.samples().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.markers().clear());
        assertThrows(IndexOutOfBoundsException.class, () -> result.relationship(-1,0));
        assertThrows(IndexOutOfBoundsException.class, () -> result.jointlyCalledMarkers(0,3));
    }

    @Test void refusesMalformedHardcallsDimensionsAndLiteralIdentities() {
        for (double value : new double[] {.5, -1, 3, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> compute(IDS,List.of("x"),new double[][]{{0,value,2}},GctaHardcallGrm.Algorithm.SOURCE_0));
        for (String value : new String[] {"", "a b", "a\tb", "a\nb", "\u007f", "\u0000", "\ud800", "\udc00"})
            assertThrows(IllegalArgumentException.class, () -> new GctaHardcallGrm.SampleId(value,"x"));
        assertThrows(IllegalArgumentException.class, () -> new GctaHardcallGrm.SampleId(null,"x"));
        assertThrows(IllegalArgumentException.class, () -> compute(List.of(IDS.get(0),IDS.get(0)),List.of("x"),new double[][]{{0,2}},GctaHardcallGrm.Algorithm.SOURCE_0));
        assertThrows(IllegalArgumentException.class, () -> compute(IDS,List.of("x","x"),new double[][]{{0,1,2},{2,1,0}},GctaHardcallGrm.Algorithm.SOURCE_0));
        assertThrows(IllegalArgumentException.class, () -> compute(IDS,List.of("x"),new double[][]{{0,2}},GctaHardcallGrm.Algorithm.SOURCE_0));
        assertThrows(IllegalArgumentException.class, () -> compute(IDS,List.of("x"),new double[][]{null},GctaHardcallGrm.Algorithm.SOURCE_0));
        assertThrows(IllegalArgumentException.class, () -> compute(IDS,List.of("x"),new double[0][],GctaHardcallGrm.Algorithm.SOURCE_0));
        assertThrows(IllegalArgumentException.class, () -> compute(IDS,List.of("x"),new double[][]{{0,1,2}},null));
    }

    @Test void enforcesAllCapacitiesBeforeMaterializingCallerCollections() {
        var huge = new AbstractList<GctaHardcallGrm.SampleId>() {
            public int size() { return Integer.MAX_VALUE; }
            public GctaHardcallGrm.SampleId get(int i) { throw new AssertionError("must refuse before access/allocation"); }
        };
        assertThrows(IllegalArgumentException.class, () -> GctaHardcallGrm.compute(huge,List.of("x"),new double[][]{{0}},GctaHardcallGrm.Algorithm.SOURCE_0,LIMITS));
        for (var limits : List.of(new GctaHardcallGrm.Limits(2,10,100),
                new GctaHardcallGrm.Limits(3,5,100),new GctaHardcallGrm.Limits(3,6,5)))
            assertThrows(IllegalArgumentException.class, () -> GctaHardcallGrm.compute(IDS,List.of("x"),new double[][]{{0,1,2}},GctaHardcallGrm.Algorithm.SOURCE_0,limits));
        assertThrows(IllegalArgumentException.class, () -> new GctaHardcallGrm.Limits(0,1,1));
    }

    @Test void cancellationAtEntryAndFinalProgressCallbackCannotReturnAResult() {
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, () -> compute(IDS,List.of("x"),new double[][]{{0,1,2}},GctaHardcallGrm.Algorithm.SOURCE_0));
        } finally { Thread.interrupted(); }
        try {
            assertThrows(CancellationException.class, () -> GctaHardcallGrm.compute(IDS,List.of("x"),new double[][]{{0,1,2}},GctaHardcallGrm.Algorithm.SOURCE_0,LIMITS,
                (done,total,included) -> Thread.currentThread().interrupt()));
        } finally { Thread.interrupted(); }
    }

    @Test void reportsRawBlockProgressWithExcludedMarkers() {
        var names = new ArrayList<String>(); double[][] g = new double[129][3];
        for (int i=0;i<129;i++) names.add("m"+i);
        g[127]=new double[]{0,1,2}; g[128]=new double[]{2,1,0};
        var progress = new ArrayList<String>();
        var result = GctaHardcallGrm.compute(IDS,names,g,GctaHardcallGrm.Algorithm.SOURCE_1,LIMITS,
            (done,total,included) -> progress.add(done+":"+total+":"+included));
        assertEquals(List.of("128:129:1","129:129:2"),progress);
        assertEquals(2,result.includedMarkers());
    }

    private record Prepared(List<GctaHardcallGrm.SampleId> ids,List<String> markers,
            double[][] values,GctaHardcallGrm.Algorithm algorithm) {
        GctaHardcallGrm.Result compute() { return GctaHardcallGrmTest.compute(ids,markers,values,algorithm); }
    }
    private static Prepared prepared(byte[] bytes) throws Exception {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(0x47434731,in.readInt()); int n=in.readInt(),m=in.readInt(),algorithm=in.readInt();
            var ids=new ArrayList<GctaHardcallGrm.SampleId>(); var names=new ArrayList<String>(); double[][] values=new double[m][n];
            for (int i=0;i<n;i++) ids.add(new GctaHardcallGrm.SampleId(string(in),string(in)));
            for (int v=0;v<m;v++) { names.add(string(in)); for(int i=0;i<n;i++) { int b=in.readUnsignedByte(); assertTrue(b<=3); values[v][i]=b==3?Double.NaN:b; } }
            assertEquals(-1,in.read()); assertTrue(algorithm==0||algorithm==1);
            return new Prepared(ids,names,values,GctaHardcallGrm.Algorithm.values()[algorithm]);
        }
    }
    private static String string(DataInputStream in) throws Exception { return new String(in.readNBytes(in.readInt()),StandardCharsets.UTF_8); }
    private static float[] floats(byte[] bytes) { var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); float[] f=new float[bytes.length/4]; for(int i=0;i<f.length;i++)f[i]=b.getFloat(); return f; }
    private static String text(byte[] bytes) { return new String(bytes,StandardCharsets.UTF_8); }
    private static GctaHardcallGrm.Result compute(List<GctaHardcallGrm.SampleId> ids,List<String> names,double[][] g,GctaHardcallGrm.Algorithm algorithm) { return GctaHardcallGrm.compute(ids,names,g,algorithm,LIMITS); }
}
