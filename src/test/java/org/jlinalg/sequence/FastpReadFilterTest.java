/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class FastpReadFilterTest {
    @TempDir static Path temporary;
    private static Path controls;
    @BeforeAll static void stageOriginals() throws IOException {
        controls = temporary.resolve("controls.zip");
        try (var input = FastpReadFilterTest.class.getResourceAsStream("fastp-original/native-controls.zip")) {
            assertNotNull(input); Files.copy(input, controls);
        }
    }
    private static String entry(ZipFile zip, String name) throws IOException {
        try (var input = zip.getInputStream(zip.getEntry(name))) { return new String(input.readAllBytes(), StandardCharsets.US_ASCII); }
    }
    private record Read(String name, String sequence, String quality) { }
    private static List<Read> reads(String raw) {
        if (raw.isEmpty()) return List.of();
        String[] lines = raw.split("\n"); assertEquals(0, lines.length % 4);
        var result = new ArrayList<Read>();
        for (int i = 0; i < lines.length; i += 4) {
            assertEquals("+", lines[i + 2]); assertEquals(lines[i + 1].length(), lines[i + 3].length());
            result.add(new Read(lines[i], lines[i + 1], lines[i + 3]));
        }
        return result;
    }
    @TestFactory Stream<DynamicTest> freshOriginalFourEditionProtocols() throws IOException {
        List<String> names;
        try (var zip = new ZipFile(controls.toFile())) {
            names = zip.stream().map(java.util.zip.ZipEntry::getName).filter(n -> n.endsWith("/options.tsv")).map(n -> n.substring(0, n.length() - 11)).sorted().toList();
        }
        assertEquals(124, names.size());
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> compareProtocol(name)));
    }
    private static void compareProtocol(String prefix) throws IOException {
        try (var zip = new ZipFile(controls.toFile())) {
            String[] lines = entry(zip, prefix + "options.tsv").strip().split("\n"), keys = lines[0].split("\t"), values = lines[1].split("\t");
            var options = new HashMap<String, String>(); for (int i = 0; i < keys.length; i++) options.put(keys[i], values[i]);
            var trim = new FastpReadFilter.Trimming(integer(options, "front"), integer(options, "tail"), cut(options, "front"), cut(options, "right"), cut(options, "tail"));
            var filter = new FastpReadFilter.Filters(bool(options, "quality_on"), integer(options, "qualified"), integer(options, "unqualified_percent"), integer(options, "average"), integer(options, "maximum_n"), bool(options, "length_on"), integer(options, "minimum_length"), integer(options, "filter_max_length"), bool(options, "complexity_on"), integer(options, "complexity_percent"));
            var input = reads(entry(zip, prefix + "input.fq")); assertEquals(256, input.size());
            var passed = new LinkedHashMap<String, Read>(); for (var row : reads(entry(zip, prefix + "output.fq"))) assertNull(passed.put(row.name(), row));
            var failed = new LinkedHashMap<String, String>();
            for (var row : reads(entry(zip, prefix + "failed.fq"))) {
                int split = row.name().lastIndexOf(' '); assertTrue(split > 0);
                assertNull(failed.put(row.name().substring(0, split), row.name().substring(split + 1)));
            }
            assertEquals(input.size(), passed.size() + failed.size());
            var observed = new ArrayList<String>();
            for (Read row : input) {
                var processed = FastpReadFilter.maximumLength(FastpReadFilter.trimAndCut(row.sequence(), row.quality(), trim), integer(options, "max_length"));
                var result = FastpReadFilter.assess(processed, filter);
                if (passed.containsKey(row.name())) {
                    Read original = passed.get(row.name()); assertEquals(FastpReadFilter.Reason.PASS, result.reason(), row.name());
                    assertEquals(original.sequence(), processed.sequence(), row.name()); assertEquals(original.quality(), processed.quality(), row.name()); observed.add(row.name());
                    assertEquals(row.sequence().substring(processed.start(), processed.end()), processed.sequence());
                    assertEquals(row.quality().substring(processed.start(), processed.end()), processed.quality());
                } else assertEquals(failure(result.reason()), failed.get(row.name()), row.name());
            }
            assertEquals(new ArrayList<>(passed.keySet()), observed, "Complete source output population and order");
        }
    }
    private static int integer(Map<String,String> options, String key) { return Integer.parseInt(options.get(key)); }
    private static boolean bool(Map<String,String> options, String key) { return Boolean.parseBoolean(options.get(key)); }
    private static FastpReadFilter.Cut cut(Map<String,String> options, String name) { return new FastpReadFilter.Cut(bool(options, name + "_on"), integer(options, name + "_window"), integer(options, name + "_quality")); }
    private static String failure(FastpReadFilter.Reason reason) {
        return switch (reason) {
            case PASS -> "UNEXPECTED_PASS";
            case QUALITY -> "failed_quality_filter";
            case N_BASE -> "failed_too_many_n_bases";
            case TOO_SHORT -> "failed_too_short";
            case TOO_LONG -> "failed_too_long";
            case COMPLEXITY -> "failed_low_complexity";
        };
    }
    @Test void emptyReadStillFailsWhenFiltersDisabled() {
        var read = FastpReadFilter.trimAndCut("", "", FastpReadFilter.Trimming.defaults());
        assertTrue(read.present());
        assertEquals(FastpReadFilter.Reason.TOO_SHORT, FastpReadFilter.assess(read, new FastpReadFilter.Filters(false,15,40,0,5,false,0,0,false,30)).reason());
    }
    @Test void distinguishNullAndPresentEmptyTrimming() {
        var cut = FastpReadFilter.Cut.disabled();
        var empty = FastpReadFilter.trimAndCut("A", "I", new FastpReadFilter.Trimming(0,1,cut,cut,cut));
        var missing = FastpReadFilter.trimAndCut("A", "I", new FastpReadFilter.Trimming(0,2,cut,cut,cut));
        assertTrue(empty.present()); assertEquals(0,empty.end()); assertFalse(missing.present()); assertEquals(-1,missing.start());
    }
    @Test void finalMaximumLengthCannotResurrectNullRead() {
        var d=FastpReadFilter.Cut.disabled(); var missing=FastpReadFilter.trimAndCut("A","I",new FastpReadFilter.Trimming(1,1,d,d,d));
        assertSame(missing,FastpReadFilter.maximumLength(missing,10));
    }
    @Test void invalidStringsAreRejectedBeforeProcessing() {
        for (String[] pair : List.of(new String[]{"AC","I"},new String[]{"A ","II"},new String[]{"A"," "},new String[]{"é","I"}))
            assertThrows(IllegalArgumentException.class,()->FastpReadFilter.trimAndCut(pair[0],pair[1],FastpReadFilter.Trimming.defaults()));
    }
    @Test void invalidCuttingParametersAreRejected() {
        for (int[] pair : new int[][]{{0,20},{1001,20},{4,0},{4,31}}) assertThrows(IllegalArgumentException.class,()->new FastpReadFilter.Cut(true,pair[0],pair[1]));
    }
    @Test void invalidGlobalParametersAreRejected() {
        var d=FastpReadFilter.Cut.disabled();
        for (int[] pair : new int[][]{{-1,0},{31,0},{0,-1},{0,101}}) assertThrows(IllegalArgumentException.class,()->new FastpReadFilter.Trimming(pair[0],pair[1],d,d,d));
    }
    @Test void invalidMaximumLengthIsRejected() { assertThrows(IllegalArgumentException.class,()->FastpReadFilter.maximumLength(FastpReadFilter.trimAndCut("A","I",FastpReadFilter.Trimming.defaults()),-1)); }
    @Test void invalidResultCoordinatesAreRejected() { assertThrows(IllegalArgumentException.class,()->new FastpReadFilter.Trimmed(false,"A","I",0,1,0)); }
    @Test void directResultConstructionCannotBypassReadOrLedgerLimits() {
        assertThrows(IllegalArgumentException.class,()->new FastpReadFilter.Trimmed(true,"A"," ",0,1,0));
        assertThrows(IllegalArgumentException.class,()->new FastpReadFilter.Trimmed(true,"A","I",0,1,1));
        String oversized="A".repeat(FastpReadFilter.MAX_READ_LENGTH+1);
        assertThrows(IllegalArgumentException.class,()->new FastpReadFilter.Trimmed(true,oversized,oversized,0,oversized.length(),0));
    }
    @Test void oversizedStringsAreRefused() {
        String value="A".repeat(FastpReadFilter.MAX_READ_LENGTH+1);
        assertThrows(IllegalArgumentException.class,()->FastpReadFilter.trimAndCut(value,value,FastpReadFilter.Trimming.defaults()));
    }
}
