/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

class VepToolsTest {
    @TempDir Path temporary;
    static final String BEFORE="##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tα sample\nchr1\t1\tliteral-id\tA\tC,G\t.\tPASS\tK=NaN\tGT\t1|2\n";
    static final String AFTER=BEFORE.replace("#CHROM","##VEP=\"v105\"\n##INFO=<ID=CSQ,Number=.,Type=String,Description=\"Format: Allele|Consequence|IMPACT|Gene|Feature|ALLELE_NUM\">\n#CHROM").replace("K=NaN","K=NaN;CSQ=C|intron_variant|MODIFIER|G1|T1|1,G|missense_variant|MODERATE|G2|T2|2");
    private Map<String,Object> request()throws IOException{Files.writeString(temporary.resolve("before.vcf"),BEFORE);Files.writeString(temporary.resolve("after.vcf"),AFTER);return new LinkedHashMap<>(Map.of("schema_version",1,"mode","RUN","backend","NATIVE","out","result","original_vcf","before.vcf","annotated_vcf","after.vcf"));}
    private int run(String...args){return VepToolsCli.run(args,new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8),new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8));}
    private int execute(Map<String,Object> request)throws IOException{Path p=temporary.resolve("request.json");json(p,request);return run("--request",p.toString());}
    @Test void nativeCompleteCaptureAndOfflineRecomputation()throws Exception{assertEquals(0,execute(request()));Path root=temporary.resolve("result");assertArrayEquals(Files.readAllBytes(temporary.resolve("after.vcf")),Files.readAllBytes(root.resolve("inputs/annotated.vcf")));Files.delete(temporary.resolve("before.vcf"));Files.delete(temporary.resolve("after.vcf"));assertEquals(0,run("--verify",root.toString()));assertTrue(Files.readString(root.resolve("results/native/variants.tsv")).contains("1|2"));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("unassessable"));}
    @Test void derivedTamperIsRefusedAfterResealing()throws Exception{assertEquals(0,execute(request()));Path root=temporary.resolve("result");Path ledger=root.resolve("results/native/genes.tsv");Files.writeString(ledger,Files.readString(ledger).replace("MODERATE","HIGH"));VepToolsCli.seal(root);assertEquals(2,run("--verify",root.toString()));}
    @Test void extraResultFileFailsAfterResealing()throws Exception{assertEquals(0,execute(request()));Path root=temporary.resolve("result");Files.writeString(root.resolve("results/native/extra.tsv"),"extra");VepToolsCli.seal(root);assertEquals(2,run("--verify",root.toString()));}
    @Test void modifiedInputIdentityNeverAdmitted()throws Exception{var r=request();Files.writeString(temporary.resolve("after.vcf"),AFTER.replace("1|2","1/2"));assertEquals(2,execute(r));assertEquals(false,read(temporary.resolve("result/analysis-status.json")).get("results_admitted"));assertTrue(Files.readString(temporary.resolve("result/WARNINGS.md")).contains("FAIL2_NO_COMPLETE_RESULT"));assertEquals(2,run("--verify",temporary.resolve("result").toString()));}
    @Test void missingInputHasProminentSkipAndConcreteRemedy()throws Exception{var r=request();r.put("annotated_vcf","missing.vcf");assertEquals(3,execute(r));String warning=Files.readString(temporary.resolve("result/WARNINGS.md"));assertTrue(warning.contains("SKIP3_DEPENDENT_ANALYSIS_SKIPPED"));assertTrue(warning.contains("missing.vcf"));assertTrue(warning.contains("REQUIRED ACTION"));}
    @Test void preservesExistingOutputAndUniqueBytes()throws Exception{var r=request();Files.createDirectory(temporary.resolve("result"));Files.writeString(temporary.resolve("result/unique"),"preserve");assertEquals(2,execute(r));assertEquals("preserve",Files.readString(temporary.resolve("result/unique")));}
    @Test void completeInputAndOutputCapacitiesFailWithoutTruncation()throws Exception{var r=request();r.put("maximum_input_bytes",10);assertEquals(2,execute(r));r.put("out","result2");r.put("maximum_input_bytes",100000);r.put("maximum_output_bytes",64);assertEquals(2,execute(r));assertEquals(false,read(temporary.resolve("result2/analysis-status.json")).get("results_admitted"));}
    @Test void unknownAndUnusedOptionsAreNotSilentlyIgnored()throws Exception{var r=request();r.put("threads",1);assertThrows(IOException.class,()->VepToolsRequest.validate(r));r.remove("threads");r.put("everything",true);assertThrows(IOException.class,()->VepToolsRequest.validate(r));}
    @Test void presenceFlagsAndOriginalSummaryIncompatibilitiesAreExplicit(){assertThrows(IOException.class,()->VepToolsRequest.options(Map.of("canonical",false),"human-cache105-GRCh38-ensembl"));assertThrows(IOException.class,()->VepToolsRequest.options(Map.of("transcript_mode","most_severe","canonical",true),"human-cache105-GRCh38-ensembl"));assertThrows(IOException.class,()->VepToolsRequest.options(Map.of("sift","b"),"gtf"));assertThrows(IOException.class,()->VepToolsRequest.options(Map.of("pick_order",List.of("canonical")),"human-cache105-GRCh38-ensembl"));}
    @Test void omittedPresenceFlagRetainsOriginalDefaultAndDeclaredPickOrder()throws Exception{var r=VepToolsRequest.options(Map.of("transcript_mode","pick_allele_gene","pick_order",List.of("mane","canonical","appris","tsl","biotype","ccds","rank","length","ensembl","refseq")),"human-cache105-GRCh38-ensembl");assertFalse(r.containsKey("canonical"));assertEquals(10,((List<?>)r.get("pick_order")).size());assertEquals("none",r.get("sift"));}
    @Test void emptyOrFlagCsqDoesNotBecomeAnUnannotatedSuccess()throws Exception{var r=request();Files.writeString(temporary.resolve("after.vcf"),AFTER.substring(0,AFTER.indexOf("K=NaN"))+"K=NaN;CSQ\tGT\t1|2\n");assertEquals(2,execute(r));}
    @Test void savedSourceBindingCannotBeResealedAsCurrent()throws Exception{assertEquals(0,execute(request()));Path root=temporary.resolve("result");Files.writeString(root.resolve("source/NATIVE-CONTRACT.json"),"{}");VepToolsCli.seal(root);assertEquals(2,run("--verify",root.toString()));}
}
