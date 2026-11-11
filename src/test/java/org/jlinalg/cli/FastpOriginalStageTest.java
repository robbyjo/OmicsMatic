/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;
import org.jlinalg.sequence.FastpReadFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FastpOriginalStageTest {
    @TempDir static Path temporary;
    private static Path controls;
    @BeforeAll static void stageGolden()throws Exception {
        controls=temporary.resolve("golden.zip");try(var input=FastpOriginalStageTest.class.getResourceAsStream("/org/jlinalg/sequence/fastp-original/native-controls.zip")){assertNotNull(input);Files.copy(input,controls);}
    }
    private static boolean available(){return System.getProperty("os.name").equals("Linux")&&System.getenv("JLINALG_FASTP_MANAGED_ROOT")!=null;}
    private static Path caseRoot(String prefix)throws IOException {
        String saved=System.getenv("JLINALG_FASTP_EVIDENCE_ROOT");Path root=saved==null?temporary:Path.of(saved);return Files.createTempDirectory(root,prefix).toRealPath();
    }
    private static FastpRuntime.Context runtime(String version)throws Exception {
        assumeTrue(available(),"Actual original processing requires Linux managed fastp controls");Path root=Path.of(System.getenv("JLINALG_FASTP_MANAGED_ROOT"));return FastpRuntime.open(root.resolve("homes").resolve(version),"linux-x86_64-fastp-"+version,root.resolve("reports").resolve(version),()->{});
    }
    private static FastpOriginalStage.Limits limits(){return new FastpOriginalStage.Limits(new FastpFastq.Limits(1048576,4194304,10000,1000000,10000,1000),new FastpProcess.Limits(60000,3L<<30,60,16777216,128,33554432,128,8,32,2L<<30,20),67108864,256);}
    private static byte[] read(ZipFile z,String name)throws IOException{try(var in=z.getInputStream(z.getEntry(name))){return in.readAllBytes();}}
    private static FastpParameters.Options isolated(Map<String,String> c){
        var filters=new FastpReadFilter.Filters(b(c,"quality_on"),n(c,"qualified"),n(c,"unqualified_percent"),n(c,"average"),n(c,"maximum_n"),b(c,"length_on"),n(c,"minimum_length"),n(c,"filter_max_length"),b(c,"complexity_on"),n(c,"complexity_percent"));
        return new FastpParameters.Options(1,new FastpParameters.Adapters(false,false,"",""),new FastpParameters.Global(n(c,"front"),n(c,"tail"),0,0,n(c,"max_length"),0),new FastpParameters.Cutting(cut(c,"front"),cut(c,"right"),cut(c,"tail")),filters,FastpParameters.PolyG.DISABLE,10,false,10,false,new FastpParameters.Duplication(false,false,0),FastpParameters.Overlap.defaults(),false);
    }
    private static FastpReadFilter.Cut cut(Map<String,String> c,String key){return new FastpReadFilter.Cut(b(c,key+"_on"),n(c,key+"_window"),n(c,key+"_quality"));}
    private static int n(Map<String,String> c,String name){return Integer.parseInt(c.get(name));}
    private static boolean b(Map<String,String> c,String name){return Boolean.parseBoolean(c.get(name));}
    @TestFactory Stream<DynamicTest> all124IndependentGoldenProtocolsThroughCompleteCapturedOwnedStage()throws Exception {
        List<String> names;try(var z=new ZipFile(controls.toFile())){names=z.stream().map(java.util.zip.ZipEntry::getName).filter(name->name.endsWith("/options.tsv")).map(name->name.substring(0,name.length()-11)).sorted().toList();}assertEquals(124,names.size());
        return names.stream().map(name->DynamicTest.dynamicTest(name,()->{
            assumeTrue(available(),"Fresh original stage matrix requires real Linux runtimes");String version=name.substring(0,name.indexOf('/'));Path root=caseRoot("case-");
            try(var z=new ZipFile(controls.toFile())){
                Path input=root.resolve("input.fq");Files.write(input,read(z,name+"input.fq"));String[] lines=new String(read(z,name+"options.tsv"),StandardCharsets.US_ASCII).strip().split("\n"),keys=lines[0].split("\t"),values=lines[1].split("\t");var choices=new HashMap<String,String>();for(int i=0;i<keys.length;i++)choices.put(keys[i],values[i]);
                var stage=FastpOriginalStage.run(runtime(version),input,null,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.LITERAL_ASCII,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.EXACT_FIRST_TOKEN,isolated(choices),root.resolve("result"),limits(),()->{});
                assertEquals(256,stage.inputs().read1().reads());assertArrayEquals(read(z,name+"output.fq"),Files.readAllBytes(stage.process().work().resolve("read1.fastq")));assertArrayEquals(read(z,name+"failed.fq"),Files.readAllBytes(stage.process().work().resolve("failed.fastq")));
                Map<?,?> report=FastpRuntime.object(FastpFiles.json(stage.process().work().resolve("fastp.json"),1048576));Map<?,?> summary=FastpRuntime.object(report.get("summary"));
                Map<?,?> before=FastpRuntime.object(summary.get("before_filtering"));assertEquals(stage.inputs().read1().bases(),((Number)before.get("total_bases")).longValue());assertEquals(stage.inputs().read1().q20Bases(),((Number)before.get("q20_bases")).longValue());assertEquals(stage.inputs().read1().q30Bases(),((Number)before.get("q30_bases")).longValue());
                assertFalse(Files.exists(stage.root().resolve("COMPLETED.json")));assertTrue(stage.process().ownedLiveAfterCleanup().isEmpty());
            }
        }));
    }
    @ParameterizedTest @ValueSource(strings={"0.22.0","0.23.2","0.25.0","1.0.1"})
    void pairedAdapterAndLowQualityOverlapCorrectionRetainExpectedFragments(String version)throws Exception {
        var runtime=runtime(version);Path root=caseRoot("paired-");String fragment="ACGCTAGCATCGACTGATCGACTAGCATAGCTACGATCGATGCTAGCTAGCATGCATCGATGCTAGCATCGATGCATCGATGC";String a1="AGATCGGAAGAGCACACGTCT",a2="AGATCGGAAGAGCGTCGTGTAG";
        String original=fragment+a1,reverse=reverseComplement(fragment)+a2;int position=30;String changed=original.substring(0,position)+(original.charAt(position)=='A'?'C':'A')+original.substring(position+1);String quality="I".repeat(position)+"#"+"I".repeat(original.length()-position-1);
        var f1=new StringBuilder();var f2=new StringBuilder();var expected1=new StringBuilder();var expected2=new StringBuilder();
        for(int i=1;i<=12;i++){f1.append(fq("@fixture:"+i+"/1",changed,quality));f2.append(fq("@fixture:"+i+"/2",reverse,"I".repeat(reverse.length())));expected1.append(fq("@fixture:"+i+"/1",fragment,"I".repeat(fragment.length())));expected2.append(fq("@fixture:"+i+"/2",reverseComplement(fragment),"I".repeat(fragment.length())));}
        Path r1=root.resolve("r1.fq"),r2=root.resolve("r2.fq");Files.writeString(r1,f1);Files.writeString(r2,f2);var d=FastpParameters.Options.defaults(1);var options=new FastpParameters.Options(1,d.adapters(),d.global(),d.cutting(),d.filters(),FastpParameters.PolyG.DISABLE,10,false,10,true,new FastpParameters.Duplication(false,false,0),d.overlap(),false);
        var stage=FastpOriginalStage.run(runtime,r1,r2,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.TERMINAL_MATE_SUFFIX,options,root.resolve("result"),limits(),()->{});
        assertEquals(expected1.toString(),Files.readString(stage.process().work().resolve("read1.fastq")));assertEquals(expected2.toString(),Files.readString(stage.process().work().resolve("read2.fastq")));assertEquals(0,Files.size(stage.process().work().resolve("failed.fastq")));assertTrue(stage.inputs().mateIdentityVerified());
    }
    @ParameterizedTest @ValueSource(strings={"0.22.0","0.23.2","0.25.0","1.0.1"})
    void gzipPhred64IsCapturedUnchangedAndOriginalConvertsOutputToPhred33(String version)throws Exception {
        var runtime=runtime(version);Path root=caseRoot("encoded-");String seq="ACGCTAGCATCGACTGATCGACTAGCATAGCTACGATCGATGCTAGCTAGCATGCATCGATGCTAGCATCGATGCATCGATGCTAGCATCGATGCATCGATG";String raw=fq("@fixture",seq,"h".repeat(seq.length()));Path input=root.resolve("unusual.input");try(var out=new GZIPOutputStream(Files.newOutputStream(input))){out.write(raw.getBytes(StandardCharsets.US_ASCII));}
        var d=FastpParameters.Options.defaults(1);var options=new FastpParameters.Options(1,new FastpParameters.Adapters(false,false,"",""),d.global(),d.cutting(),d.filters(),FastpParameters.PolyG.DISABLE,10,false,10,false,new FastpParameters.Duplication(false,false,0),d.overlap(),false);
        var stage=FastpOriginalStage.run(runtime,input,null,FastpFastq.Encoding.PHRED64,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.EXACT_FIRST_TOKEN,options,root.resolve("result"),limits(),()->{});
        assertArrayEquals(Files.readAllBytes(input),Files.readAllBytes(stage.root().resolve(stage.captures().get(0).snapshot())));assertTrue(stage.captures().get(0).snapshot().endsWith(".gz"));assertEquals(fq("@fixture",seq,"I".repeat(seq.length())),Files.readString(stage.process().work().resolve("read1.fastq")));
    }
    @Test void insufficientAccuracySixMemoryIsRetainedSkipBeforeAnyOriginalLaunch()throws Exception {
        var runtime=runtime("0.23.2");Path root=caseRoot("memory-");Path input=root.resolve("input.fq");Files.writeString(input,fq("@a","ACGT","IIII"));var d=FastpParameters.Options.defaults(1);var o=new FastpParameters.Options(1,d.adapters(),d.global(),d.cutting(),d.filters(),d.polyG(),d.polyGMinimum(),d.polyX(),d.polyXMinimum(),false,new FastpParameters.Duplication(true,true,6),d.overlap(),false);
        Path result=root.resolve("result");assertThrows(FastpRuntime.Unavailable.class,()->FastpOriginalStage.run(runtime,input,null,FastpFastq.Encoding.PHRED33,FastpFastq.Alphabet.UPPER_ACGTN,FastpFastq.Plus.EMPTY_ONLY,FastpFastq.Pairing.EXACT_FIRST_TOKEN,o,result,limits(),()->{}));assertFalse(Files.exists(result.resolve("original")));assertTrue(Files.readString(result.resolve("FAILURE.json")).contains("SKIP3"));
    }
    private static String fq(String header,String sequence,String quality){return header+"\n"+sequence+"\n+\n"+quality+"\n";}
    private static String reverseComplement(String sequence){var out=new StringBuilder();for(int i=sequence.length()-1;i>=0;i--)out.append(switch(sequence.charAt(i)){case 'A'->'T';case 'C'->'G';case 'G'->'C';case 'T'->'A';default->throw new IllegalArgumentException();});return out.toString();}
}
