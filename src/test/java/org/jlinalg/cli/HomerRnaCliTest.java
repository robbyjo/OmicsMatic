/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.sequence.HomerSamTags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerRnaCliTest {
    @TempDir Path root;
    private Map<String,String> options() {
        var o=new LinkedHashMap<String,String>();o.put("home",root.resolve("missing-runtime").toString());o.put("profile","linux-x86_64-rna-5.1-v7.0");o.put("out",root.resolve("result").toString());o.put("sam-list",root.resolve("unread.tsv").toString());o.put("genome","hg38");o.put("reference-provenance","Synthetic unit test; no original process");o.put("mapping","unique");o.put("minimum-mapq","30");o.put("mates","both");o.put("flip-strands","false");o.put("strand-specific-paired","false");o.put("relative-strand","both");o.put("condensation","copy-families");o.put("normalization","both");o.put("reference-irregularities","retain-original");o.put("short-exon-parts","retain-original");
        for(String key:List.of("duplicate-alignments","supplementary-alignments","unpaired-alignments","improper-pairs"))o.put(key,"error");o.put("identical-replicates","error");return o;
    }
    private int run(Map<String,String> o,ByteArrayOutputStream errors)throws Exception {
        Path file=root.resolve("options.json");Files.writeString(file,RegenieRuntime.json(o),StandardCharsets.UTF_8);return HomerRnaCli.run(new String[]{"--options-file",file.toString()},new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8),new PrintStream(errors,true,StandardCharsets.UTF_8));
    }
    @Test void unavailableRuntimeProducesProminentSkipAndNoProcess()throws Exception {
        var errors=new ByteArrayOutputStream();assertEquals(3,run(options(),errors));Path result=root.resolve("result");assertTrue(Files.isDirectory(result.resolve("partial")));assertTrue(Files.readString(result.resolve("analysis-status.json")).contains("skipped-unavailable-dependency-or-capability"));String warning=Files.readString(result.resolve("WARNINGS.md"));assertTrue(warning.contains("ANALYSIS SKIPPED"));assertTrue(warning.contains("Required action:"));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("ANALYSIS SKIPPED"));assertFalse(Files.readString(result.resolve("session.json")).contains("\"process_"));assertFalse(Files.exists(result.resolve("raw.stdout")));
    }
    @Test void unspecifiedStrandFailsBeforeAnyRuntimeOrScience()throws Exception {
        var o=options();o.remove("relative-strand");var errors=new ByteArrayOutputStream();assertEquals(2,run(o,errors));assertTrue(Files.readString(root.resolve("result/WARNINGS.md")).contains("relative-strand"));assertTrue(Files.isDirectory(root.resolve("result/partial")));assertFalse(Files.readString(root.resolve("result/session.json")).contains("\"process_"));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Required action:"));
    }
    @Test void existingOutputAndRuntimeContentsArePreserved()throws Exception {
        Path output=Files.createDirectory(root.resolve("result"));Files.writeString(output.resolve("preserve.txt"),"user-data\n");assertEquals(2,run(options(),new ByteArrayOutputStream()));assertEquals("user-data\n",Files.readString(output.resolve("preserve.txt")));try(var entries=Files.list(output)){assertEquals(1,entries.count());}
    }
    @Test void outputCannotPopulateAdmittedHome()throws Exception {
        Path home=Files.createDirectory(root.resolve("home"));Files.writeString(home.resolve("marker"),"user-runtime\n");var o=options();o.put("home",home.toString());o.put("out",home.resolve("analysis").toString());assertEquals(2,run(o,new ByteArrayOutputStream()));assertFalse(Files.exists(home.resolve("analysis")));assertEquals("user-runtime\n",Files.readString(home.resolve("marker")));
    }
    @Test void arbitraryFlagsAndOtherProfileOptionsAreRejected() {
        for(String option:List.of("raw-flags","effective-genome-size","zero-template-length","reference-lengths")){var errors=new ByteArrayOutputStream();assertEquals(2,HomerRnaCli.run(new String[]{"--"+option,"bad"},new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors,true,StandardCharsets.UTF_8)));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Unknown option"));}assertFalse(Files.exists(root.resolve("result")));
    }
    @Test void mapqCannotBeSilentlyIgnoredByOtherMappingModes()throws Exception {
        for(String mapping:List.of("keep-one","keep-all")){var o=options();o.put("mapping",mapping);assertThrows(java.io.IOException.class,()->HomerRnaCli.policies(o));o.put("minimum-mapq","0");assertDoesNotThrow(()->HomerRnaCli.policies(o));}
    }
    @Test void explicitMateAndStrandOptionsReachBothNativeAndOriginal()throws Exception {
        var o=options();o.put("mates","second");o.put("flip-strands","true");o.put("strand-specific-paired","true");HomerRnaCli.policies(o);var nativeOptions=HomerRnaCli.samOptions(o);assertEquals(HomerSamTags.Mates.SECOND,nativeOptions.mates());assertEquals(HomerSamTags.Length.MATCHING_BLOCK,nativeOptions.length());assertTrue(nativeOptions.flipStrands());assertTrue(nativeOptions.strandSpecificPaired());var command=HomerRnaCli.tagArguments(o,Path.of("tags"),Path.of("input.sam"));assertTrue(command.containsAll(List.of("-unique","-mapq","30","-read2","-flip","-sspe")));assertFalse(command.contains("-pe"));assertTrue(command.contains("150"));
    }
    @Test void fixedScientificChoicesMustBeValidAndExplicit() {
        for(String key:List.of("genome","mates","flip-strands","strand-specific-paired","condensation","normalization","reference-irregularities","short-exon-parts","duplicate-alignments","supplementary-alignments","unpaired-alignments","improper-pairs","identical-replicates")){var o=options();o.put(key,"infer");assertThrows(java.io.IOException.class,()->HomerRnaCli.policies(o),key);}
    }
    @Test void countAuditPreservesHalfWeightsAndRefusesNonfiniteOrFractionalHalfUnits()throws Exception {
        assertEquals(0,HomerRnaQuantification.half("0.000000"));assertEquals(3,HomerRnaQuantification.half("1.5"));assertEquals(10,HomerRnaQuantification.half("5e0"));for(String bad:List.of("NaN","Infinity","-0.5","0.25","4503599627370496"))assertThrows(java.io.IOException.class,()->HomerRnaQuantification.half(bad));
    }
    @Test void jsonOptionsPreserveUnicodeAndDirectOverridesAndRejectIgnoredValues()throws Exception {
        Path file=root.resolve("options.json");Files.writeString(file,"{\"genome\":\"mm10\",\"reference-provenance\":\"参考版\"}");var o=HomerRnaCli.parse(new String[]{"--options-file",file.toString(),"--genome","hg38"});assertEquals("hg38",o.get("genome"));assertEquals("参考版",o.get("reference-provenance"));assertTrue(o.get("options-sha256").matches("[0-9a-f]{64}"));for(String bad:List.of("{\"timeout\":1}","{\"raw-flags\":\"x\"}","{\"options-file\":\"other\"}")){Files.writeString(file,bad);assertThrows(Exception.class,()->HomerRnaCli.parse(new String[]{"--options-file",file.toString()}));}
    }
    @Test void safeWorkAndSeparateProfileAreRequired() {
        var failure=assertThrows(HomerRuntime.Unavailable.class,()->HomerRnaRuntime.work(root.resolve("unsafe parent")));assertTrue(failure.getMessage().contains("ASCII"));assertEquals("linux-x86_64-rna-5.1-v7.0",HomerRnaRuntime.profile());assertTrue(HomerRnaRuntime.lockHash().matches("[0-9a-f]{64}"));
    }
    @Test void replicateAliasesProduceSafeDistinctSharedRunnerLogLabels()throws Exception {
        assertEquals("tags-r0001",HomerRnaCli.processLabel("tags","R0001"));assertEquals("parts-r0032",HomerRnaCli.processLabel("parts","R0032"));assertNotEquals(HomerRnaCli.processLabel("tags","R0001"),HomerRnaCli.processLabel("tags","R0002"));assertThrows(java.io.IOException.class,()->HomerRnaCli.processLabel("../tags","R0001"));assertThrows(java.io.IOException.class,()->HomerRnaCli.processLabel("tags","user-alias"));
    }
    @Test void originalRawAndRpkmHeadersRetainDistinctSuffixesAndExactSampleIdentity()throws Exception {
        var raw=org.jlinalg.sequence.HomerRnaExpression.Normalization.RAW;var rpkm=org.jlinalg.sequence.HomerRnaExpression.Normalization.RPKM;Path directory=Path.of("tags-R0001");HomerRnaQuantification.sampleHeader(directory+" (5.0 total)",directory,raw,5);HomerRnaQuantification.sampleHeader(directory+" FPKM",directory,rpkm,5);HomerRnaQuantification.sampleHeader(directory+" (6.5 total)",directory,raw,6.5);
        for(String bad:List.of(directory+" (5.0 total)","tags-R0002 FPKM",directory+" FPKM altered"))assertThrows(java.io.IOException.class,()->HomerRnaQuantification.sampleHeader(bad,directory,rpkm,5));assertThrows(java.io.IOException.class,()->HomerRnaQuantification.sampleHeader(directory+" (6.0 total)",directory,raw,5));
    }
}
