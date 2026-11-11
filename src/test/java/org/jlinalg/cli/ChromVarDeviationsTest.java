/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.ChromVarDeviations;
import org.jlinalg.sequence.ChromVarDeviations.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Every original value/kind, including intermediates, plus meaningful capacity/immutability gates. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChromVarDeviationsTest {
    @TempDir Path root;
    private static final Limits LIMITS=Limits.defaults();
    private record Control(String family,Map<String,Object> data,Map<String,Object> request,Map<String,Object> original) { }
    private final List<Control> controls=new ArrayList<>();
    @BeforeAll void fixture()throws Exception {
        byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/sequence/chromvar-deviations/original-controls.zip")){assertNotNull(in);raw=in.readAllBytes();}
        assertEquals("000d1b32b92fcc9c5d2d55885df5520b3b2ed5084b81acf738ebf52bade44d80",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Map<String,byte[]> files=new HashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null)if(e.getName().matches("(basic|extended)/(cases|original)\\.json"))files.put(e.getName(),zip.readAllBytes());}
        for(String family:List.of("basic","extended")){
            var input=object(SimpleJson.parse(new String(files.get(family+"/cases.json"),StandardCharsets.UTF_8)));var original=object(SimpleJson.parse(new String(files.get(family+"/original.json"),StandardCharsets.UTF_8)));
            for(Object v:list(original.get("results"))){var record=object(v);String id=text(record.get("id"));var request=list(input.get("cases")).stream().map(ChromVarDeviationsTest::object).filter(c->c.get("id").equals(id)).findFirst().orElseThrow();controls.add(new Control(family,object(object(input.get("datasets")).get(request.get("dataset"))),request,record));}
        }
        assertEquals(102,controls.size());
    }
    @TestFactory Stream<DynamicTest> originalExpectations(){return controls.stream().map(c->DynamicTest.dynamicTest(name(c),()->{
        Object expected=object(c.original.get("expectations")).get("value");if(error(expected)){assertThrows(IllegalArgumentException.class,()->expectation(c));return;}
        vector(expected,expectation(c));
    }));}
    @TestFactory Stream<DynamicTest> originalSingleAndIntermediates(){return controls.stream().map(c->DynamicTest.dynamicTest(name(c),()->{
        Object expected=object(c.original.get("single")).get("value");if(error(expected)){assertThrows(IllegalArgumentException.class,()->single(c,true,LIMITS));return;}
        var got=single(c,true,LIMITS);var want=object(expected);vector(want.get("z"),got.z());vector(want.get("dev"),got.deviations());number(scalar(want.get("matches")),got.fractionMatches());number(scalar(want.get("overlap")),got.fractionBackgroundOverlap());
        if(!want.containsKey("observed")){assertNull(got.intermediates());return;}
        var d=got.intermediates();assertNotNull(d);vector(want.get("observed"),d.observed());vector(want.get("expected"),d.expected());vector(want.get("observed_deviation"),d.observedDeviation());sampled(want.get("sampled"),d.sampled(),d.observed().length);matrix(want.get("sampled_expected"),d.sampledExpected(),d.observed().length);matrix(want.get("sampled_deviation"),d.sampledDeviation(),d.observed().length);assertEquals(!(want.get("sampled") instanceof Map<?,?>),d.sampledWasSourceVector());
    }));}
    @TestFactory Stream<DynamicTest> originalCompleteCores(){return controls.stream().map(c->DynamicTest.dynamicTest(name(c),()->{
        Object expected=object(c.original.get("core")).get("value");if(error(expected)){assertThrows(IllegalArgumentException.class,()->core(c,LIMITS));return;}
        var got=core(c,LIMITS);var want=object(expected);matrix(want.get("z"),got.z(),got.cells().size());matrix(want.get("deviations"),got.deviations(),got.cells().size());vector(want.get("fractionMatches"),got.fractionMatches());vector(want.get("fractionBackgroundOverlap"),got.fractionBackgroundOverlap());assertEquals(list(want.get("motif_ids")),got.motifs());assertEquals(list(want.get("cell_ids")),got.cells());
    }));}

    @Test void countInputAndGettersAreImmutable(){double[] v={.25,2};int[] rows={0,1},p={0,2};Counts c=new Counts(List.of("p1","p2"),List.of("c"),p,rows,v,LIMITS,()->false);v[0]=99;rows[0]=1;p[0]=2;c.values()[0]=88;c.rows()[0]=1;c.pointers()[0]=2;assertArrayEquals(new double[]{.25,2},c.values());assertEquals(.25,c.peakTotals()[0]);assertThrows(UnsupportedOperationException.class,()->c.peaks().add("x"));}
    @Test void negativeCountsRejected(){assertThrows(IllegalArgumentException.class,()->new Counts(List.of("p"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{-.5},LIMITS,()->false));}
    @Test void duplicateCountCoordinatesRejected(){assertThrows(IllegalArgumentException.class,()->new Counts(List.of("p"),List.of("c"),new int[]{0,2},new int[]{0,0},new double[]{1,2},LIMITS,()->false));}
    @Test void fractionalCountsAreNotTruncated(){Counts c=new Counts(List.of("p"),List.of("c"),new int[]{0,1},new int[]{0},new double[]{.25},LIMITS,()->false);assertEquals(.25,c.values()[0]);assertEquals(.25,c.total());}
    @Test void constructorCancellationStops(){assertThrows(CancellationException.class,()->new Counts(List.of("p"),List.of("c"),new int[]{0,0},new int[0],new double[0],LIMITS,()->true));}
    @Test void operationCancellationStops(){assertThrows(CancellationException.class,()->ChromVarDeviations.expectations(counts(baseline()),false,null,Profile.SOURCE_1_34_1,LIMITS,()->true));}
    @Test void workBudgetAppliesToSparseZeros(){Counts c=new Counts(List.of("p1","p2"),List.of("c1","c2"),new int[]{0,0,0},new int[0],new double[0],LIMITS,()->false);assertThrows(IllegalArgumentException.class,()->ChromVarDeviations.expectations(c,true,null,Profile.SOURCE_1_34_1,limits(1,100,100,100),()->false));}
    @Test void raggedBackgroundRejected(){var c=baseline();int[][] bg=background(c);bg[0]=new int[]{1};assertThrows(IllegalArgumentException.class,()->ChromVarDeviations.single(counts(c),new int[]{1},bg,expectation(c),1,false,profile(c),LIMITS,()->false));}
    @Test void invalidUnusedBackgroundRowRejected(){var c=baseline();int[][] bg=background(c);bg[19][0]=21;assertThrows(IllegalArgumentException.class,()->ChromVarDeviations.single(counts(c),new int[]{1},bg,expectation(c),1,false,profile(c),LIMITS,()->false));}
    @Test void outputCapacityNeverSubsets(){assertThrows(IllegalArgumentException.class,()->core(baseline(),limits(1000000,1,10000,10000)));}
    @Test void backgroundMembershipCapacityNeverSamples(){assertThrows(IllegalArgumentException.class,()->single(baseline(),false,limits(1000000,1000,1,10000)));}
    @Test void intermediatesHaveIndependentCapacity(){var c=baseline();var l=limits(1000000,1000,10000,1);assertThrows(IllegalArgumentException.class,()->single(c,true,l));assertNotNull(single(c,false,l));}
    @Test void resultArraysAreImmutable(){var c=baseline();var got=single(c,true,LIMITS);double a=got.z()[0],b=got.intermediates().sampled()[0][0];got.z()[0]=123;got.intermediates().sampled()[0][0]=987;assertEquals(a,got.z()[0]);assertEquals(b,got.intermediates().sampled()[0][0]);}
    @Test void sourceValueKindsRemainDistinct(){assertEquals("NA",ChromVarDeviations.format(ChromVarDeviations.missingValue()));assertEquals("NaN",ChromVarDeviations.format(Double.NaN));assertEquals("Inf",ChromVarDeviations.format(Double.POSITIVE_INFINITY));assertEquals("-Inf",ChromVarDeviations.format(Double.NEGATIVE_INFINITY));}

    private Control baseline(){return controls.stream().filter(c->c.family.equals("basic")&&c.request.get("id").equals("baseline")).findFirst().orElseThrow();}
    private static Limits limits(long work,long output,long memberships,long intermediate){return new Limits(100,100,10000,100,10000,10000,memberships,output,intermediate,work);}
    private static String name(Control c){return c.family+"/"+text(c.original.get("profile"))+"/"+c.request.get("id");}
    private static Profile profile(Control c){return text(c.original.get("profile")).startsWith("1.16")?Profile.SOURCE_1_16_0:Profile.SOURCE_1_34_1;}
    private static Counts counts(Control c){var data=list(c.data.get("counts"));int p=data.size(),cols=list(data.get(0)).size();List<Integer> rows=new ArrayList<>();List<Double> values=new ArrayList<>();int[] pointers=new int[cols+1];for(int col=0;col<cols;col++){for(int row=0;row<p;row++){double v=((Number)list(data.get(row)).get(col)).doubleValue();if(v!=0){rows.add(row);values.add(v);}}pointers[col+1]=rows.size();}return new Counts(java.util.stream.IntStream.rangeClosed(1,p).mapToObj(i->"peak"+i).toList(),java.util.stream.IntStream.rangeClosed(1,cols).mapToObj(i->"cell"+i).toList(),pointers,rows.stream().mapToInt(Integer::intValue).toArray(),values.stream().mapToDouble(Double::doubleValue).toArray(),LIMITS,()->false);}
    private static Grouping grouping(Control c){if(c.request.get("group")==null)return null;List<String> levels=list(c.request.get("group_levels")).stream().map(String.class::cast).toList();return new Grouping(levels,list(c.request.get("group")).stream().mapToInt(v->levels.indexOf(v)).toArray());}
    private static double[] expectation(Control c){return ChromVarDeviations.expectations(counts(c),Boolean.TRUE.equals(c.request.get("norm")),grouping(c),profile(c),LIMITS,()->false).values();}
    private static double[] effective(Control c){return c.request.get("expectation")==null?expectation(c):list(c.request.get("expectation")).stream().mapToDouble(ChromVarDeviationsTest::numeric).toArray();}
    private static int[][] background(Control c){return list(c.data.get("background")).stream().map(v->list(v).stream().mapToInt(x->((Number)x).intValue()).toArray()).toArray(int[][]::new);}
    private static List<int[]> peakSets(Control c){return list(c.request.get("peak_sets")).stream().map(v->list(v).stream().mapToInt(x->((Number)x).intValue()).toArray()).toList();}
    private static Single single(Control c,boolean details,Limits limits){return ChromVarDeviations.single(counts(c),peakSets(c).get(0),background(c),effective(c),((Number)c.request.get("threshold")).doubleValue(),details,profile(c),limits,()->false);}
    private static Result core(Control c,Limits limits){var sets=peakSets(c);return ChromVarDeviations.compute(counts(c),java.util.stream.IntStream.rangeClosed(1,sets.size()).mapToObj(i->"motif"+i).toList(),sets,background(c),effective(c),profile(c),limits,()->false);}
    private static void sampled(Object expected,double[][] got,int cells){if(expected instanceof Map<?,?>){matrix(expected,got,cells);return;}var flat=list(expected);assertEquals((long)got.length*cells,flat.size());for(int cell=0;cell<cells;cell++)for(int i=0;i<got.length;i++)number(flat.get(cell*got.length+i),got[i][cell]);}
    private static void matrix(Object expected,double[][] got,int cells){var matrix=object(expected);var dims=list(matrix.get("dim"));assertEquals(got.length,((Number)dims.get(0)).intValue());assertEquals(cells,((Number)dims.get(1)).intValue());var rows=list(matrix.get("rows"));assertEquals(got.length,rows.size());for(int i=0;i<got.length;i++)vector(rows.get(i),got[i]);}
    private static void vector(Object expected,double[] got){var values=list(expected);assertEquals(values.size(),got.length);for(int i=0;i<got.length;i++)number(values.get(i),got[i]);}
    private static void number(Object expected,double got){String text=text(expected);if(List.of("NA","NaN","Inf","-Inf").contains(text)){assertEquals(text,ChromVarDeviations.format(got));return;}double v=Double.parseDouble(text);assertEquals(Kind.FINITE,ChromVarDeviations.kind(got),text+" vs "+ChromVarDeviations.format(got));assertEquals(v,got,Math.max(2e-12,Math.abs(v)*2e-11));}
    private static double numeric(Object value){String text=String.valueOf(value);return switch(text){case "NA"->ChromVarDeviations.missingValue();case "NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(text);};}
    private static boolean error(Object v){return v instanceof Map<?,?>&&object(v).containsKey("error");}
    private static Object scalar(Object v){return v instanceof List<?>?list(v).get(0):v;}
    private static String text(Object v){return String.valueOf(scalar(v));}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v){return (Map<String,Object>)v;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object v){return (List<Object>)v;}
}
