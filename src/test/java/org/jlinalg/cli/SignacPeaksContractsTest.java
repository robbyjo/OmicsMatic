/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SignacPeaksContractsTest {
    @TempDir Path temp;
    private static Map<String,Object> request(){var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","POOLED");r.put("out","new-out");r.put("source_profile","Signac-1.9.0");r.put("macs2_version","2.2.7.1");r.put("macs2_home","absent-home");r.put("genome_build","synthetic");r.put("effective_genome_size",1000000);r.put("sources",List.of(Map.of("fragments","input.tsv")));return r;}
    static Stream<Arguments> invalid(){return Stream.of(Arguments.of("schema_version",1.5),Arguments.of("mode","ANALYZE"),Arguments.of("source_profile","latest"),Arguments.of("macs2_version","3.0.0"),Arguments.of("effective_genome_size",0),Arguments.of("qvalue",null),Arguments.of("qvalue",2),Arguments.of("qvalue","0.05"),Arguments.of("extsize",201),Arguments.of("shift",.5),Arguments.of("format","BAM"),Arguments.of("keep_duplicates",null),Arguments.of("keep_duplicates","1"),Arguments.of("keep_duplicates",0),Arguments.of("broad_cutoff",.1),Arguments.of("SPMR",true),Arguments.of("maximum_groups",0),Arguments.of("timeout_seconds",0),Arguments.of("maximum_memory_bytes",1),Arguments.of("cell_groups","ignored"),Arguments.of("combine_peaks",true),Arguments.of("additional_args","--outdir /elsewhere"),Arguments.of("control","untracked.bam"));}
    @ParameterizedTest @MethodSource("invalid") void invalidOrIgnoredSettingsAreRefused(String key,Object value){var r=request();r.put(key,value);assertThrows(IOException.class,()->SignacPeaksContracts.validate(r));}
    @Test void sourceDefaultsAreExplicitAndIdempotent()throws Exception{var r=SignacPeaksContracts.validate(request());assertTrue(same(r,SignacPeaksContracts.validate(r)));assertEquals("BED",r.get("format"));assertEquals(200L,r.get("extsize"));assertEquals(-100L,r.get("shift"));assertFalse(r.containsKey("keep_duplicates"));assertFalse(r.containsKey("qvalue"));}
    @Test void bedpeRefusesUnusedExtensionAndShift()throws Exception{var r=request();r.put("format","BEDPE");var normalized=SignacPeaksContracts.validate(r);assertFalse(normalized.containsKey("shift"));assertTrue(same(normalized,SignacPeaksContracts.validate(normalized)));r.put("extsize",200);assertThrows(IOException.class,()->SignacPeaksContracts.validate(r));}
    @Test void modelOptionsHaveClosedCompatibleCombinations()throws Exception{var r=request();r.put("qvalue",.05);r.put("pvalue",.01);assertThrows(IOException.class,()->SignacPeaksContracts.validate(r));r.remove("pvalue");r.put("broad",true);r.put("call_summits",true);assertThrows(IOException.class,()->SignacPeaksContracts.validate(r));r.put("call_summits",false);r.put("broad_cutoff",.1);assertDoesNotThrow(()->SignacPeaksContracts.validate(r));}
    @Test void groupedSourceUsesSelectedCellsAndRefusesIgnoredName()throws Exception{var r=request();r.put("mode","GROUPED");r.put("cell_groups","groups.tsv");r.put("sources",List.of(Map.of("fragments","fragments.tsv","map","map.tsv")));var normalized=SignacPeaksContracts.validate(r);assertEquals(true,normalized.get("combine_peaks"));assertTrue(same(normalized,SignacPeaksContracts.validate(normalized)));r.put("name","unused");assertThrows(IOException.class,()->SignacPeaksContracts.validate(r));}
    @Test void namesNeverBecomeShellArgumentsAndNoOpaqueFlagsAreAccepted()throws Exception{var r=request();r.put("name","label ' quote; $(command)");var args=SignacPeaksContracts.arguments(SignacPeaksContracts.validate(r),List.of("/captured/s1.fragments"),"g1","/captured/out");assertEquals(List.of("callpeak","-t","/captured/s1.fragments","-g","1000000.0","-f","BED","--nomodel","--extsize","200","--shift","-100","-n","g1","--outdir","/captured/out"),args);}
    @Test void nativePreparationAndRuntimeSkipPreserveExistingOutput()throws Exception{Path data=temp.resolve("input.tsv");Files.writeString(data,"chr1\t0\t30\ta\t1\nchr1\t30\t60\tb\t1\n");var r=request();r.put("sources",List.of(Map.of("fragments",data.toString())));Path out=temp.resolve("out");r.put("out",out.toString());r.put("macs2_home",temp.resolve("absent").toString());Path p=temp.resolve("request.json");json(p,r);var log=new ByteArrayOutputStream();int code=SignacPeaksCli.run(new String[]{"--request",p.toString()},new PrintStream(log,true,StandardCharsets.UTF_8),new PrintStream(log,true,StandardCharsets.UTF_8));assertEquals(3,code,log.toString(StandardCharsets.UTF_8));assertEquals(false,read(out.resolve("analysis-status.json")).get("results_admitted"));assertThrows(IOException.class,()->SignacPeaksCli.verify(out));String before=FollowupSupport.hash(out.resolve("capture-files.json"));assertEquals(2,SignacPeaksCli.run(new String[]{"--request",p.toString()},System.out,System.err));assertEquals(before,FollowupSupport.hash(out.resolve("capture-files.json")));}
}
