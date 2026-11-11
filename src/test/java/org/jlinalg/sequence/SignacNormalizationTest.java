/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignacNormalizationTest {
    private static final Map<String,String> FIXTURE = fixture();
    private static Map<String,String> fixture() {
        try {
            byte[] raw;
            try (var input = SignacNormalizationTest.class.getResourceAsStream("/org/jlinalg/sequence/signac-normalization/original-controls.zip")) {
                assertNotNull(input); raw = input.readAllBytes();
            }
            assertEquals("35eaad67d07731cab7da2cf98f9d724e34bb7e1ad5ccc2930721dd7f32c9c74f", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            Map<String,String> files = new HashMap<>();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(raw))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    assertFalse(entry.isDirectory()); assertFalse(entry.getName().contains(".."));
                    byte[] data = zip.readNBytes(8_000_001); assertTrue(data.length <= 8_000_000);
                    assertNull(files.put(entry.getName(), new String(data, StandardCharsets.UTF_8)));
                }
            }
            assertEquals(350, files.size()); return Map.copyOf(files);
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static List<String> ids() { return FIXTURE.get("matrix-ids.tsv").lines().toList(); }
    private static double number(String s) { return switch (s) { case "Inf" -> Double.POSITIVE_INFINITY; case "-Inf" -> Double.NEGATIVE_INFINITY; case "NaN" -> Double.NaN; default -> Double.parseDouble(s); }; }
    private static List<String> csv(String s) { return s.isEmpty() ? List.of() : List.of(s.split(",", -1)); }
    private static double[] values(String s) { return csv(s.strip()).stream().mapToDouble(SignacNormalizationTest::number).toArray(); }
    private static int[] indices(String s) { return csv(s.strip()).stream().mapToInt(Integer::parseInt).toArray(); }
    private static SignacNormalization.Counts counts(String id) {
        return new SignacNormalization.Counts(FIXTURE.get(id + "/features.tsv").lines().toList(), FIXTURE.get(id + "/cells.tsv").lines().toList(),
                indices(FIXTURE.get(id + "/pointers.tsv")), indices(FIXTURE.get(id + "/indices.tsv")), values(FIXTURE.get(id + "/values.tsv")));
    }
    private static void same(double expected, double actual) {
        if (!Double.isFinite(expected)) assertEquals(expected, actual);
        else assertEquals(expected, actual, Math.max(16 * Math.ulp(expected), Math.abs(expected) * 3e-13));
    }
    static Stream<Arguments> normalizationCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String id : ids()) for (String row : FIXTURE.get(id + "/normalized.tsv").lines().toList()) cases.add(Arguments.of(id, row));
        assertEquals(1152, cases.size()); return cases.stream();
    }
    @ParameterizedTest(name="original normalization {0} {index}") @MethodSource("normalizationCases")
    void fullOriginalPackageAndFiveExtractedEditionArithmeticAgree(String id, String row) {
        String[] fields = row.split("\t", -1); assertEquals(5, fields.length);
        var input = counts(id);
        var result = SignacNormalization.runTfidf(input, SignacNormalization.Method.values()[Integer.parseInt(fields[0]) - 1], number(fields[1]),
                Boolean.parseBoolean(fields[2]) ? values(FIXTURE.get(id + "/idf.tsv")) : null);
        double[] expected = values(fields[3]), actual = result.matrix().values(); assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) same(expected[i], actual[i]);
        assertEquals(input.features(), result.matrix().features()); assertEquals(input.cells(), result.matrix().cells());
        assertArrayEquals(input.columnPointers(), result.matrix().columnPointers()); assertArrayEquals(input.rowIndices(), result.matrix().rowIndices());
        assertEquals(csv(fields[4]), result.warnings().stream().map(SignacNormalization.Warning::code).toList());
        for (var warning : result.warnings()) { assertFalse(warning.message().isBlank()); assertFalse(warning.requiredAction().isBlank()); }
    }
    static Stream<Arguments> metadataCases() { return ids().stream().map(Arguments::of); }
    @ParameterizedTest(name="original matrix metadata {0}") @MethodSource("metadataCases")
    void allFeaturesAndStableTiesMatchOriginalMetadata(String id) {
        var input = counts(id); var result = SignacNormalization.featureMetadata(input, () -> false);
        List<String> expected = FIXTURE.get(id + "/metadata.tsv").lines().toList(); assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            String[] fields = expected.get(i).split("\t", -1); var actual = result.get(i);
            assertEquals(fields[0], actual.id()); same(number(fields[1]), actual.count()); same(number(fields[2]), actual.percentile());
            assertEquals(input.features().indexOf(actual.id()), actual.originalIndex());
        }
    }
    static Stream<Arguments> selectionCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String id : ids()) for (String row : FIXTURE.get(id + "/selections.tsv").lines().toList()) cases.add(Arguments.of(id, row));
        assertEquals(396, cases.size()); return cases.stream();
    }
    @ParameterizedTest(name="original current assay selection {0} {index}") @MethodSource("selectionCases")
    void numericPercentileAllAndKeepSelectionsMatchCurrentWholePackage(String id, String row) {
        String[] fields = row.split("\t", -1); assertEquals(4, fields.length);
        var cutoff = new SignacNormalization.Cutoff(SignacNormalization.CutoffKind.valueOf(fields[0]), number(fields[1]));
        var result = SignacNormalization.findTopFeatures(counts(id), cutoff, csv(fields[2]));
        assertEquals(csv(fields[3]), result.selected()); assertEquals(counts(id).features().size(), result.metadata().size());
    }
    private static SignacNormalization.Counts small() { return counts("hand-count-versus-document-frequency"); }

    @Test void independentCountIdfAndSuppliedIdfAreNotDocumentFrequencyOrRelogged() {
        var input = small();
        var a = SignacNormalization.runTfidf(input, SignacNormalization.Method.RAW_COUNT_IDF, 1e4, null);
        assertArrayEquals(new double[]{1,1,0.25}, a.effectiveIdf());
        assertArrayEquals(new double[]{1,3,2,1,2}, a.matrix().values());
        var b = SignacNormalization.runTfidf(input, SignacNormalization.Method.TF_LOG_IDF, 1e4, new double[]{2,3,4});
        assertArrayEquals(new double[]{2,3,4}, b.effectiveIdf()); same(0.5, b.matrix().values()[0]); same(2.25, b.matrix().values()[1]);
    }
    @Test void metadataCutoffsAreStrictAndTiesRetainOriginalOrder() {
        var input = counts("tied-feature-totals");
        var meta = SignacNormalization.featureMetadata(input, () -> false);
        assertEquals(List.of(3,0,1,2,4), meta.stream().map(SignacNormalization.Feature::originalIndex).toList());
        assertEquals(0.8, meta.get(1).percentile());
        assertTrue(SignacNormalization.findTopFeatures(input, SignacNormalization.Cutoff.count(5), null).selected().isEmpty());
        assertEquals(List.of(input.features().get(3)), SignacNormalization.findTopFeatures(input, SignacNormalization.Cutoff.percentile(80), null).selected());
    }
    @Test void storedZerosAndEmptyCellsRemainVisibleAndInputIsUnchanged() {
        var input = counts("zero-row-and-cell-stored"); double[] before = input.values();
        var result = SignacNormalization.runTfidf(input, SignacNormalization.Method.LOG_TF_IDF, 1e4, null);
        assertEquals(4, result.matrix().values().length); assertArrayEquals(before, input.values());
        assertEquals(List.of("ZERO_CELL_COUNTS","ZERO_FEATURE_COUNTS"), result.warnings().stream().map(SignacNormalization.Warning::code).toList());
        assertEquals(0, result.matrix().values()[0]); assertEquals(0, result.matrix().values()[2]); assertEquals(0, result.matrix().values()[3]);
    }
    @Test void defensiveCopiesCoverInputsOutputsAndSelections() {
        List<String> features = new ArrayList<>(List.of("f")); int[] p = {0,1}, i = {0}; double[] x = {2};
        var input = new SignacNormalization.Counts(features, List.of("c"), p, i, x);
        features.set(0,"altered"); p[0]=1; i[0]=9; x[0]=90;
        assertEquals(List.of("f"), input.features()); assertArrayEquals(new int[]{0,1}, input.columnPointers()); assertArrayEquals(new int[]{0}, input.rowIndices()); assertArrayEquals(new double[]{2}, input.values());
        double[] idf={3}; var output=SignacNormalization.runTfidf(input,SignacNormalization.Method.RAW_COUNT_IDF,1,idf); idf[0]=9;
        output.effectiveIdf()[0]=9; output.matrix().values()[0]=9; output.matrix().columnPointers()[0]=9;
        assertArrayEquals(new double[]{3},output.effectiveIdf()); assertArrayEquals(new double[]{6},output.matrix().values());
        input.featureTotals()[0]=99; input.cellTotals()[0]=99; assertArrayEquals(new long[]{2},input.featureTotals()); assertArrayEquals(new long[]{2},input.cellTotals());
        var top=SignacNormalization.findTopFeatures(input,SignacNormalization.Cutoff.all(),null);
        assertThrows(UnsupportedOperationException.class,()->input.features().add("x"));
        assertThrows(UnsupportedOperationException.class,()->top.metadata().clear()); assertThrows(UnsupportedOperationException.class,()->top.selected().clear());
    }
    @Test void infinitiesAreNotSilentlyClippedAndRequireSkippingFiniteOnlyAnalysis() {
        var output=SignacNormalization.runTfidf(small(),SignacNormalization.Method.LOG_TF_LOG_IDF,1e308,new double[]{1e308,1e308,1e308});
        assertTrue(Arrays.stream(output.matrix().values()).anyMatch(Double::isInfinite));
        var warning=output.warnings().stream().filter(w->w.code().equals("NONFINITE_NORMALIZED_VALUES")).findFirst().orElseThrow();
        assertTrue(warning.requiredAction().startsWith("Skip downstream analysis"));
    }
    @Test void scalesDoNotAffectMethodsTwoOrFourAndSuppliedIdfSuppressesFeatureWarning() {
        for(var method:List.of(SignacNormalization.Method.TF_LOG_IDF,SignacNormalization.Method.RAW_COUNT_IDF)) {
            assertArrayEquals(SignacNormalization.runTfidf(small(),method,1,null).matrix().values(),SignacNormalization.runTfidf(small(),method,1e308,null).matrix().values());
        }
        var result=SignacNormalization.runTfidf(counts("zero-row-and-cell-stored"),SignacNormalization.Method.LOG_TF_IDF,1e4,new double[]{1,2});
        assertEquals(List.of("ZERO_CELL_COUNTS"),result.warnings().stream().map(SignacNormalization.Warning::code).toList());
    }
    @Test void callerCancellationAndThreadInterruptionDoNotPublishPartialResults() {
        assertThrows(CancellationException.class,()->SignacNormalization.runTfidf(small(),SignacNormalization.Method.LOG_TF_IDF,1e4,null,()->true));
        assertThrows(CancellationException.class,()->SignacNormalization.featureMetadata(small(),()->true));
        assertThrows(CancellationException.class,()->SignacNormalization.findTopFeatures(small(),SignacNormalization.Cutoff.all(),null,()->true));
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class,()->SignacNormalization.runTfidf(small(),SignacNormalization.Method.LOG_TF_IDF,1,null)); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
        int n=6000; List<String> f=new ArrayList<>(); int[] rows=new int[n]; double[] values=new double[n];
        for(int k=0;k<n;k++){f.add("f"+k);rows[k]=k;values[k]=1;}
        var calls=new AtomicInteger();
        assertThrows(CancellationException.class,()->new SignacNormalization.Counts(f,List.of("c"),new int[]{0,n},rows,values,new SignacNormalization.Limits(n,1,n),()->calls.incrementAndGet()>=3));
        assertArrayEquals(new double[]{1,1},new double[]{values[0],values[n-1]});
    }
    @Test void sparseMatrixDoesNotAllocateFeatureTimesCellStorage() {
        int n=50_000; List<String> f=new ArrayList<>(),c=new ArrayList<>(); int[] p=new int[n+1],i=new int[n];double[] x=new double[n];
        for(int k=0;k<n;k++){f.add("f"+k);c.add("c"+k);p[k]=k;i[k]=k;x[k]=1;}p[n]=n;
        var input=new SignacNormalization.Counts(f,c,p,i,x,new SignacNormalization.Limits(n,n,n),()->false);
        var out=SignacNormalization.runTfidf(input,SignacNormalization.Method.LOG_TF_IDF,1e4,null);
        assertEquals(n,out.matrix().values().length);same(Math.log1p(5e8),out.matrix().values()[n-1]);
        assertEquals(n,SignacNormalization.findTopFeatures(input,SignacNormalization.Cutoff.percentile(5),null).selected().size());
    }
    @Test void keepExistingRequiresUniqueKnownFeatureIdentities() {
        var input=small();String id=input.features().get(0);
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.findTopFeatures(input,SignacNormalization.Cutoff.keepExisting(),List.of("unknown")));
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.findTopFeatures(input,SignacNormalization.Cutoff.keepExisting(),List.of(id,id)));
        assertThrows(NullPointerException.class,()->SignacNormalization.findTopFeatures(input,SignacNormalization.Cutoff.keepExisting(),null));
        assertEquals(List.of(),SignacNormalization.findTopFeatures(input,SignacNormalization.Cutoff.keepExisting(),List.of()).selected());
    }
    @Test void exactIntegerLimitCannotBeExceededByFeatureOrCellTotals() {
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("a","b"),List.of("c"),new int[]{0,2},new int[]{0,1},new double[]{9_007_199_254_740_991d,1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("a"),List.of("c","d"),new int[]{0,1,2},new int[]{0,0},new double[]{9_007_199_254_740_991d,1}));
    }
    static Stream<Arguments> invalidNumbers() { return Stream.of(-1d,0.5,Double.NaN,Double.POSITIVE_INFINITY,9_007_199_254_740_992d).map(Arguments::of); }
    @ParameterizedTest @MethodSource("invalidNumbers") void invalidCountDomainFails(double value) {
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{value}));
    }
    static Stream<Arguments> invalidParameters() { return Stream.of(-1d,0d,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY).map(Arguments::of); }
    @ParameterizedTest @MethodSource("invalidParameters") void invalidScaleAndSuppliedIdfFailExplicitly(double value) {
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.runTfidf(small(),SignacNormalization.Method.LOG_TF_IDF,value,null));
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.runTfidf(small(),SignacNormalization.Method.LOG_TF_IDF,1,new double[]{value,1,1}));
    }
    @Test void malformedSparseDimensionsCoordinatesIdentitiesAndBoundsFailBeforeAnalysis() {
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0,2},new int[]{0,0},new double[]{1,1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f","g"),List.of("c"),new int[]{0,2},new int[]{1,0},new double[]{1,1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0,1},new int[]{-1},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0,1},new int[]{1},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c","d"),new int[]{0,2,1},new int[]{0},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{1,1},new int[]{0},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0},new int[]{0},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0,1},new int[]{},new double[]{1}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of(),List.of("c"),new int[]{0,0},new int[]{},new double[]{}));
        for(String id:List.of(""," ","a\tb","a\nb","a\rb","a\0b","a".repeat(4097))) {
            assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of(id),List.of("c"),new int[]{0,0},new int[]{},new double[]{}));
        }
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f","f"),List.of("c"),new int[]{0,0},new int[]{},new double[]{}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c","c"),new int[]{0,0,0},new int[]{},new double[]{}));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{1},new SignacNormalization.Limits(1,1,0),()->false));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Counts(List.of("f","g"),List.of("c"),new int[]{0,0},new int[]{},new double[]{},new SignacNormalization.Limits(1,1,0),()->false));
        assertThrows(IllegalArgumentException.class,()->new SignacNormalization.Limits(0,1,0));
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.Cutoff.percentile(101));
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.Cutoff.count(-1));
        assertThrows(IllegalArgumentException.class,()->SignacNormalization.runTfidf(small(),SignacNormalization.Method.LOG_TF_IDF,1,new double[]{1}));
    }
}
