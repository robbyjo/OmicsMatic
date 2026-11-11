/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.jlinalg.sequence.SignacPeakOutput;
import org.jlinalg.sequence.SignacSequenceOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignacPeakOutputTest {
    @TempDir Path temp;
    private static Map<?,?> object(Object value){return (Map<?,?>)value;}
    private static List<?> list(Object value){return (List<?>)value;}
    private static int number(Object value){return ((Number)value).intValue();}
    private static Map<?,?> load(String name){try(var in=SignacPeakOutputTest.class.getResourceAsStream("/org/jlinalg/sequence/signac-peak-output/"+name)){assertNotNull(in);return object(SimpleJson.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8)));}catch(IOException e){throw new UncheckedIOException(e);}}
    private static final class Fixture {static final Map<?,?> ORDER=load("ordering.json"),PARSER=load("parser.json");}
    static Stream<Object> ranking(){return list(Fixture.ORDER.get("ranking")).stream().map(x->x);}
    @ParameterizedTest @MethodSource("ranking") void originalSequenceRanks(Object raw){var row=object(raw);List<String> names=list(row.get("names")).stream().map(String.class::cast).toList();var sex=switch((String)row.get("X_is_sexchrom")){case "auto"->SignacSequenceOrder.SexChromosomeX.AUTO;case "true"->SignacSequenceOrder.SexChromosomeX.TRUE;default->SignacSequenceOrder.SexChromosomeX.FALSE;};var ranked=SignacSequenceOrder.rank(names,sex);assertArrayEquals(list(row.get("ranks")).stream().mapToInt(SignacPeakOutputTest::number).toArray(),ranked.ranks());assertEquals(list(row.get("warnings")).isEmpty(),ranked.warnings().isEmpty());}
    static Stream<Object> combined(){return list(Fixture.ORDER.get("combined")).stream().map(x->x);}
    @ParameterizedTest @MethodSource("combined") void originalGroupedReduction(Object raw){var row=object(raw);var expected=object(row.get("result"));List<SignacPeakOutput.Group> groups=new ArrayList<>();
        for(Object g:list(row.get("groups"))){var group=object(g);List<SignacPeakOutput.Peak> peaks=new ArrayList<>();int i=0;for(Object p:list(group.get("peaks"))){var peak=list(p);peaks.add(new SignacPeakOutput.Peak((String)peak.get(0),number(peak.get(1)),number(peak.get(2)),"*","peak"+(++i),"0","1","1","1",null));}groups.add(new SignacPeakOutput.Group((String)group.get("name"),peaks));}
        if(expected.containsKey("error")){assertThrows(IllegalArgumentException.class,()->SignacPeakOutput.combine(groups,100000,()->false));return;}
        var result=SignacPeakOutput.combine(groups,100000,()->false);assertEquals(list(expected.get("seqlevels")),result.sequenceLevels());assertEquals(list(row.get("warnings")),result.warnings());var rows=list(expected.get("rows"));assertEquals(rows.size(),result.peaks().size());
        for(int i=0;i<rows.size();i++){var e=object(rows.get(i));var a=result.peaks().get(i);assertEquals(e.get("chromosome"),a.chromosome());assertEquals(number(e.get("start")),a.start());assertEquals(number(e.get("end")),a.end());assertEquals(e.get("groups"),String.join(",",a.groups()));}
    }
    static Stream<Arguments> parser(){return Fixture.PARSER.entrySet().stream().map(e->Arguments.of(e.getKey(),e.getValue()));}
    @ParameterizedTest @MethodSource("parser") void originalSignacPeakParser(String name,Object raw)throws Exception {var row=object(raw);Path file=temp.resolve("source.tsv");Files.writeString(file,(String)row.get("text"));var values=SignacPeakOutput.read(file,(Boolean)row.get("broad"),1000,100000,()->false);var expected=list(row.get("result"));assertEquals(expected.size(),values.size());for(int i=0;i<values.size();i++){var a=values.get(i);var e=object(expected.get(i));assertEquals(e.get("seqnames"),a.chromosome());assertEquals(number(e.get("start")),a.start());assertEquals(number(e.get("end")),a.end());assertEquals(e.get("strand"),a.strand());assertEquals(e.get("name"),a.name());for(var pair:List.of(Map.entry("score",a.score()),Map.entry("fold_change",a.foldChange()),Map.entry("neg_log10pvalue_summit",a.negativeLog10P()),Map.entry("neg_log10qvalue_summit",a.negativeLog10Q()))){Object x=e.get(pair.getKey());assertEquals(x instanceof Number n?n.doubleValue():SignacPeakOutput.number(x.toString()),SignacPeakOutput.number(pair.getValue()));}}}
    @Test void malformedPeakRowsAndBoundsAreRefused()throws Exception {for(String text:List.of("chr1\t0\t10\n","chr1\t-1\t10\tp\t0\t.\t1\t1\t1\t1\n","chr1\t0\t10\tp\t0\t.\t1e999\t1\t1\t1\n")){Path p=temp.resolve("bad.tsv");Files.writeString(p,text);assertThrows(IOException.class,()->SignacPeakOutput.read(p,false,1,1000,()->false));}Path p=temp.resolve("good.tsv");Files.writeString(p,"chr1\t0\t10\tp\t0\t.\t1\t1\t1\t1\n".repeat(2));assertThrows(IOException.class,()->SignacPeakOutput.read(p,false,1,1000,()->false));assertThrows(java.util.concurrent.CancellationException.class,()->SignacPeakOutput.read(p,false,2,1000,()->true));}
}
