/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ShapeitGeneticMapTest {
    private static final Map<String,byte[]> CAPTURE = capture();
    private static final ShapeitGeneticMap.Limits LIMITS = ShapeitGeneticMap.Limits.defaults();
    private record Case(String path, String profile, String arithmetic, String name) { }
    private static Stream<Case> cases() {
        var pattern = Pattern.compile("capture/(4\\.1\\.3|4\\.2\\.2|5-c34d)-(fused|split|checked)-(.+)\\.tsv");
        var result = new ArrayList<Case>();
        for (String path : CAPTURE.keySet().stream().sorted().toList()) {
            var match = pattern.matcher(path);
            if (match.matches()) result.add(new Case(path, match.group(1), match.group(2), match.group(3)));
        }
        assertEquals(127, result.size()); return result.stream();
    }
    private static String text(String name) {return new String(CAPTURE.get(name), StandardCharsets.UTF_8);}
    private static ShapeitGeneticMap.Data data(Case item) {
        return ShapeitGeneticMap.parse(text("capture/"+item.name()+".map").lines().toList(), LIMITS);
    }
    private static int[] positions(Case item) {
        return text("capture/"+item.name()+".positions").lines().mapToInt(Integer::parseInt).toArray();
    }
    private static ShapeitGeneticMap.Profile profile(Case item) {
        return switch (item.profile()) {
            case "4.1.3" -> ShapeitGeneticMap.Profile.SHAPEIT4_4_1_3;
            case "4.2.2" -> ShapeitGeneticMap.Profile.SHAPEIT4_4_2_2;
            default -> ShapeitGeneticMap.Profile.SHAPEIT5_C34D;
        };
    }
    private static ShapeitGeneticMap.Arithmetic arithmetic(Case item) {
        return item.arithmetic().equals("split") ? ShapeitGeneticMap.Arithmetic.SPLIT_BINARY64 : ShapeitGeneticMap.Arithmetic.FUSED_BINARY64;
    }

    @ParameterizedTest @MethodSource("cases")
    void everyOriginalCompleteResultAndCheckedFailureHasAnExplicitNativeOutcome(Case item) {
        String name = item.name(); var oracle = text(item.path()).lines().toList();
        if (Set.of("bad-columns","decreasing-bp","decreasing-cm").contains(name)) {
            assertThrows(IllegalArgumentException.class, () -> data(item)); assertFalse(oracle.stream().anyMatch(s -> s.startsWith("E\t"))); return;
        }
        if (name.equals("whitespace-line")) {
            var error = assertThrows(ShapeitGeneticMap.SourceBoundaryException.class, () -> data(item));
            assertEquals("WHITESPACE_SPLITTER", error.code()); assertFalse(oracle.stream().anyMatch(s -> s.startsWith("E\t"))); return;
        }
        var parsed = data(item); int[] variants = positions(item);
        String undefined = switch (name) {
            case "right-boundary" -> "RIGHT_INTERVAL_READ";
            case "empty-map" -> "EMPTY_MAP";
            case "empty-variants" -> "EMPTY_VARIANTS";
            case "all-before" -> item.profile().equals("4.1.3") ? "ALL_BEFORE_4_1_3" : "";
            default -> "";
        };
        if (!undefined.isEmpty()) {
            var error = assertThrows(ShapeitGeneticMap.SourceBoundaryException.class,
                    () -> ShapeitGeneticMap.interpolate(parsed, variants, profile(item), arithmetic(item), LIMITS));
            assertEquals(undefined,error.code()); assertFalse(oracle.stream().anyMatch(s -> s.startsWith("E\t"))); return;
        }
        boolean readOnly = Set.of("nan-payloads","negative-zero").contains(name);
        var mapped = readOnly ? null : name.equals("default") ? ShapeitGeneticMap.uniform(variants, LIMITS)
                : ShapeitGeneticMap.interpolate(parsed, variants, profile(item), arithmetic(item), LIMITS);
        double[] absolute = mapped == null ? new double[0] : mapped.absoluteCentiMorgan();
        double[] relative = mapped == null ? new double[0] : mapped.relativeCentiMorgan();
        int knots = 0, rows = 0; boolean complete = false;
        for (String line : oracle) {
            String[] f = line.split("\t",-1);
            switch (f[0]) {
                case "K" -> {
                    int index = Integer.parseInt(f[1]); var point = parsed.points().get(index);
                    assertEquals(Integer.parseInt(f[2]),point.position()); assertEquals(Long.parseUnsignedLong(f[3],16),Double.doubleToRawLongBits(point.centiMorgan()),item.path()+"/knot/"+index); knots++;
                }
                case "C" -> {assertNotNull(mapped);assertEquals(Integer.parseInt(f[1]),mapped.directAssignments());assertEquals(Integer.parseInt(f[2]),mapped.interpolatedVariants());}
                case "V" -> {
                    assertNotNull(mapped); int index=Integer.parseInt(f[1]); assertEquals(Integer.parseInt(f[2]),variants[index]);
                    if (!f[3].equals("-")) assertEquals(Long.parseUnsignedLong(f[3],16),Double.doubleToRawLongBits(absolute[index]),item.path()+"/absolute/"+index);
                    assertEquals(Long.parseUnsignedLong(f[4],16),Double.doubleToRawLongBits(relative[index]),item.path()+"/relative/"+index); rows++;
                }
                case "E" -> {complete=true;assertEquals(Integer.parseInt(f[1]),readOnly ? knots : rows);}
                default -> fail("Unknown original map observation: "+f[0]);
            }
        }
        assertTrue(complete); if (!name.equals("default")) assertEquals(parsed.points().size(),knots);
        if (!readOnly) assertEquals(variants.length,rows);
    }

    @Test void duplicatesRemainDistinctAndExactWritesUseTheLastDeclaredCm() {
        var data=ShapeitGeneticMap.parse(List.of("ignored","100 unused 0","100 anything 0.5","100 unused 1"),LIMITS);
        var mapped=ShapeitGeneticMap.interpolate(data,new int[]{100,100},ShapeitGeneticMap.Profile.SHAPEIT4_4_2_2,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,LIMITS);
        assertEquals(3,data.points().size());assertEquals(6,mapped.directAssignments());assertEquals(0,mapped.interpolatedVariants());
        assertArrayEquals(new double[]{1,1},mapped.absoluteCentiMorgan());assertArrayEquals(new double[]{0,0},mapped.relativeCentiMorgan());
    }

    @Test void permissiveNumericCoercionsAreVisibleAndNonfiniteDataCannotAdmitANumericalMap() {
        var data=ShapeitGeneticMap.parse(text("capture/coercions.map").lines().toList(),LIMITS);
        assertEquals(4,data.coercedNumericTokens());assertEquals(0,data.points().get(0).position());assertEquals(0,data.points().get(0).centiMorgan());
        var nonfinite=ShapeitGeneticMap.parse(text("capture/nan-payloads.map").lines().toList(),LIMITS);
        var error=assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.interpolate(nonfinite,new int[]{1,2},ShapeitGeneticMap.Profile.SHAPEIT5_C34D,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,LIMITS));
        assertTrue(error.getMessage().contains("not admitted"));
    }

    @Test void arraysAndPointPopulationsRemainImmutable() {
        var source=new ArrayList<>(List.of(new ShapeitGeneticMap.Point(0,0),new ShapeitGeneticMap.Point(100,1)));
        var data=new ShapeitGeneticMap.Data("header",source,0);source.clear();assertEquals(2,data.points().size());
        assertThrows(UnsupportedOperationException.class,()->data.points().clear());
        int[] positions={1,50,100}; var mapped=ShapeitGeneticMap.interpolate(data,positions,ShapeitGeneticMap.Profile.SHAPEIT5_C34D,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,LIMITS);
        double[] original=mapped.relativeCentiMorgan();positions[0]=90;double[] copy=mapped.relativeCentiMorgan();copy[1]=999;
        assertArrayEquals(original,mapped.relativeCentiMorgan());double[] absolute=mapped.absoluteCentiMorgan();absolute[1]=999;assertEquals(0.5,mapped.absoluteCentiMorgan()[1]);
    }

    @Test void completePopulationCapacitiesNeverProducePrefixSuccess() {
        var map=List.of("header","0 unused 0","100 unused 1","200 unused 2");
        assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.parse(map,new ShapeitGeneticMap.Limits(2,100,1000,10000,100000)));
        assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.parse(map,new ShapeitGeneticMap.Limits(100,100,5,10000,100000)));
        assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.parse(map,new ShapeitGeneticMap.Limits(100,100,1000,8,100000)));
        var data=ShapeitGeneticMap.parse(map,LIMITS);
        for(var limits:List.of(new ShapeitGeneticMap.Limits(2,100,1000,10000,100000),new ShapeitGeneticMap.Limits(100,2,1000,10000,100000),new ShapeitGeneticMap.Limits(100,100,1000,10000,1)))
            assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.interpolate(data,new int[]{1,100,200},ShapeitGeneticMap.Profile.SHAPEIT5_C34D,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,limits));
    }

    @Test void originalSignedCounterOverflowIsRefusedWithoutBillionsOfWrites() {
        var points=new ArrayList<ShapeitGeneticMap.Point>();for(int i=0;i<65536;i++)points.add(new ShapeitGeneticMap.Point(100,i/65536.0));
        int[] positions=new int[65536];Arrays.fill(positions,100);
        var error=assertThrows(ShapeitGeneticMap.SourceBoundaryException.class,()->ShapeitGeneticMap.interpolate(new ShapeitGeneticMap.Data("header",points,0),positions,ShapeitGeneticMap.Profile.SHAPEIT5_C34D,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,LIMITS));
        assertEquals("DIRECT_COUNT_OVERFLOW",error.code());
    }

    @Test void invalidCoordinatesUnorderedTargetsAndZeroSpanInterpolationCannotBecomeAnalysis() {
        var data=ShapeitGeneticMap.parse(List.of("header","100 ignored 0","100 ignored 1"),LIMITS);
        for(int[] positions:List.of(new int[]{0,100},new int[]{100,99},new int[]{1,99}))
            assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.interpolate(data,positions,ShapeitGeneticMap.Profile.SHAPEIT5_C34D,ShapeitGeneticMap.Arithmetic.FUSED_BINARY64,LIMITS));
        var overflow=assertThrows(ShapeitGeneticMap.SourceBoundaryException.class,()->ShapeitGeneticMap.parse(List.of("header","2147483648 ignored 0"),LIMITS));
        assertEquals("ATOI_OVERFLOW",overflow.code());
        assertThrows(IllegalArgumentException.class,()->ShapeitGeneticMap.parse(List.of("header","-1 ignored 0"),LIMITS));
    }

    private static Map<String,byte[]> capture() {
        try(var stream=ShapeitGeneticMapTest.class.getResourceAsStream("/org/jlinalg/genetics/shapeit-map/original-controls.zip")) {
            assertNotNull(stream);var result=new LinkedHashMap<String,byte[]>();int total=0;
            try(var zip=new ZipInputStream(stream)) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    assertTrue(result.size()<1024);assertFalse(entry.isDirectory());assertFalse(result.containsKey(entry.getName()));
                    var output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                    while((count=zip.read(buffer))>=0){total+=count;assertTrue(total<=8388608);output.write(buffer,0,count);}
                    result.put(entry.getName(),output.toByteArray());
                }
            }
            var checks=new String(result.get("SHA256SUMS"),StandardCharsets.UTF_8).lines().toList();assertEquals(result.size()-1,checks.size());
            for(String line:checks)assertEquals(line.substring(0,64),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.get(line.substring(66)))),line.substring(66));
            return Map.copyOf(result);
        } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
}
