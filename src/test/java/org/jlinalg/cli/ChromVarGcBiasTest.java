/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.jlinalg.sequence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Original Biostrings formula controls and real indexed FASTA admission checks. */
final class ChromVarGcBiasTest {
    @TempDir Path temp;
    private static final Path FIXTURE=Path.of(System.getProperty("chromvar.gc.fixture","src/test/resources/org/jlinalg/sequence/chromvar-gc"));
    @SuppressWarnings("unchecked")
    private static Map<String,Object> data(String name)throws IOException{return (Map<String,Object>)SimpleJson.parse(Files.readString(FIXTURE.resolve(name),StandardCharsets.UTF_8));}
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> objects(Object value){return (List<Map<String,Object>>)value;}
    private static double value(Object v){return Double.parseDouble(v.toString());}
    @TestFactory Stream<DynamicTest> originalSequences()throws IOException {
        var input=data("cases.json");var original=data("original.json");
        @SuppressWarnings("unchecked") var profiles=(Map<String,Object>)original.get("profiles");
        List<DynamicTest> tests=new ArrayList<>();var cases=objects(input.get("cases"));
        for(var profile:profiles.entrySet()){
            @SuppressWarnings("unchecked") var result=(Map<String,Object>)profile.getValue();
            @SuppressWarnings("unchecked") var expected=(List<Object>)result.get("direct");
            @SuppressWarnings("unchecked") var frequencies=(List<List<Number>>)result.get("frequencies");
            assertEquals(cases.size(),expected.size());
            for(int index=0;index<cases.size();index++){
                final int i=index;tests.add(DynamicTest.dynamicTest(profile.getKey()+"-"+cases.get(i).get("id"),()->{
                    byte[] sequence=cases.get(i).get("sequence").toString().getBytes(StandardCharsets.US_ASCII);
                    var actual=ChromVarGcBias.count(sequence,()->false);var f=frequencies.get(i);
                    long canonical=f.stream().mapToLong(Number::longValue).sum();
                    assertEquals(canonical,actual.canonicalBases());assertEquals(f.get(1).longValue()+f.get(2).longValue(),actual.gcBases());
                    assertEquals(sequence.length-canonical,actual.ambiguousBases());assertEquals(value(expected.get(i)),actual.fraction());
                }));
            }
        }
        return tests.stream();
    }
    private List<SignacFragments.Region> ranges()throws IOException {
        List<SignacFragments.Region> result=new ArrayList<>();for(var r:objects(data("cases.json").get("ranges")))result.add(new SignacFragments.Region(r.get("chromosome").toString(),((Number)r.get("start")).intValue(),((Number)r.get("end")).intValue()));return result;
    }
    @Test void indexedReferenceMatchesBothOriginalEditions()throws IOException {
        var result=ChromVarGcBias.calculate(FIXTURE.resolve("reference.fa"),FIXTURE.resolve("reference.fa.fai"),ranges(),SignacReferenceStats.Limits.defaults(),()->false);
        @SuppressWarnings("unchecked") var profiles=(Map<String,Object>)data("original.json").get("profiles");
        for(Object p:profiles.values()){
            @SuppressWarnings("unchecked") var expected=(List<Object>)((Map<String,Object>)p).get("ranges");
            assertEquals(expected.size(),result.rows().size());for(int i=0;i<expected.size();i++)assertEquals(value(expected.get(i)),result.rows().get(i).bias().fraction());
        }
        assertTrue(result.warnings().stream().anyMatch(s->s.startsWith("UNDEFINED_GC_BIAS")));
        assertThrows(UnsupportedOperationException.class,()->result.rows().clear());
    }
    @Test void ambiguityMustNotReuseRegionStatsDenominator(){var b=ChromVarGcBias.count("GNC".getBytes(StandardCharsets.US_ASCII),()->false);assertEquals(1,b.fraction());assertNotEquals(2.0/3,b.fraction());}
    @Test void cancellationAndInvalidBytes(){assertThrows(java.util.concurrent.CancellationException.class,()->ChromVarGcBias.count(new byte[0],()->true));for(String s:List.of("U",".","A C","A\nC","é"))assertThrows(IllegalArgumentException.class,()->ChromVarGcBias.count(s.getBytes(StandardCharsets.UTF_8),()->false));}
    @Test void missingAndOutOfRangeFail()throws IOException {
        var limits=SignacReferenceStats.Limits.defaults();Path fasta=FIXTURE.resolve("reference.fa");
        assertThrows(IOException.class,()->ChromVarGcBias.calculate(fasta,null,List.of(new SignacFragments.Region("missing",1,2)),limits,()->false));
        assertThrows(IOException.class,()->ChromVarGcBias.calculate(fasta,null,List.of(new SignacFragments.Region("chr1",1,500000)),limits,()->false));
        var peak=new SignacFragments.Region("chr1",1,2);assertThrows(IllegalArgumentException.class,()->ChromVarGcBias.calculate(fasta,null,List.of(peak,peak),limits,()->false));
    }
    @Test void capacityAndWrongIndexFail()throws IOException {
        var l=SignacReferenceStats.Limits.defaults();var tight=new SignacReferenceStats.Limits(l.bytes(),l.residues(),l.sequences(),l.lineBases(),1,l.peaks(),l.queriedBases());Path fasta=FIXTURE.resolve("reference.fa");
        assertThrows(IOException.class,()->ChromVarGcBias.calculate(fasta,null,ranges(),tight,()->false));
        Path bad=temp.resolve("bad.fai");Files.writeString(bad,"chr1\t3\t6\t3\t4\n");assertThrows(IOException.class,()->ChromVarGcBias.calculate(fasta,bad,ranges(),l,()->false));
        assertThrows(java.util.concurrent.CancellationException.class,()->ChromVarGcBias.calculate(fasta,null,ranges(),l,()->true));
    }
}
