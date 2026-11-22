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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real global route/dependency refusals. Original successful R executions are qualified separately. */
final class SkatWorkflowCliTest {
    @TempDir Path directory;
    record Call(int code,String out,String err) { }
    static Call call(String...args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Call(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    Map<String,Object> request(String name)throws Exception {
        Files.writeString(directory.resolve("phenotype.tsv"),"sample_id\ttrait\ns1\t0.1\ns2\t0.5\ns3\t0.2\ns4\t0.9\n");Files.writeString(directory.resolve("genotypes.tsv"),"sample_id\tv1\ns1\t0\ns2\t1\ns3\t0\ns4\t2\n");Files.writeString(directory.resolve("sets.tsv"),"set_id\tvariant_id\n literal gene \tv1\n");
        var raw=new LinkedHashMap<String,Object>();raw.put("schema_version",1);raw.put("mode","RUN");raw.put("engine","original");raw.put("profile",SkatWorkflowContracts.PROFILE);raw.put("operation","ASSOCIATE");raw.put("out",directory.resolve(name).toString());raw.put("rscript",directory.resolve("unavailable/Rscript.exe").toString());raw.put("r_libraries",List.of(directory.resolve("unavailable-library").toString()));raw.put("seed",17);raw.put("phenotype","phenotype.tsv");raw.put("genotypes","genotypes.tsv");raw.put("sets","sets.tsv");raw.put("null_model",Map.of("out_type","C"));return raw;
    }
    Path write(String name,Object request)throws Exception {Path path=directory.resolve(name);SkatWorkflowFiles.json(path,request);return path;}
    Call execute(Map<String,Object> raw)throws Exception {return call("skat-tools","--request",write("request.json",raw).toString());}
    static Map<String,Object> read(Path p)throws Exception{return SkatWorkflowFiles.read(p,16_777_216);}
    static void reseal(Path root)throws Exception {var request=SkatWorkflowContracts.validate(read(root.resolve("request.json")));SkatWorkflowFiles.json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"admission_limits_enforced",false,"files",SkatWorkflowFiles.inventory(root,request,false)));}
    @Test void globalHelpStatesFinalCandidateAndVerificationBoundaries(){Call c=call("skat-tools","--help");assertEquals(0,c.code());assertTrue(c.out().contains("admitted/p-values.tsv"));assertTrue(c.out().contains("SKIP3"));assertTrue(c.out().contains("No RDS deserialization"));}
    @Test void unknownRuntimeProfileSkipsBeforeCreatingOutput()throws Exception {var raw=request("out");raw.put("profile","2.2.4-r4.6.1");Call c=execute(raw);assertEquals(3,c.code(),c.err());assertTrue(c.err().contains("SKIP3"));assertFalse(Files.exists(directory.resolve("out")));}
    @Test void closedScientificSettingsRefuseUnusedFieldsBeforeOutput()throws Exception {var raw=request("out");raw.put("test",Map.of("invented_option",true));Call c=execute(raw);assertEquals(2,c.code(),c.err());assertFalse(Files.exists(directory.resolve("out")));}
    @Test void unavailableRscriptProducesSealedVisibleSkipAndReadOnlyVerification()throws Exception {
        var raw=request("out");Call c=execute(raw);assertEquals(3,c.code(),c.err());Path root=directory.resolve("out");assertEquals("SKIP3_DEPENDENT_ANALYSIS_SKIPPED",read(root.resolve("analysis-status.json")).get("status"));assertEquals(false,read(root.resolve("analysis-status.json")).get("results_admitted"));assertFalse(Files.exists(root.resolve("admitted/p-values.tsv")));assertFalse(read(root.resolve("manifest.json")).containsKey("worker_pid"));
        byte[] seal=Files.readAllBytes(root.resolve("capture-files.json"));Call verified=call("skat-tools","--verify",root.toString());assertEquals(0,verified.code(),verified.err());assertTrue(verified.out().contains("results_admitted=false"));assertTrue(verified.err().contains("no complete result"));assertArrayEquals(seal,Files.readAllBytes(root.resolve("capture-files.json")));
    }
    @Test void missingInputIsAnUnexecutedSkipWithPartialCaptureRetained()throws Exception {var raw=request("out");raw.put("phenotype","missing.tsv");Call c=execute(raw);assertEquals(3,c.code(),c.err());Path root=directory.resolve("out");assertEquals(0,call("skat-tools","--verify",root.toString()).code());assertFalse(read(root.resolve("manifest.json")).containsKey("worker_pid"));}
    @Test void literalUnicodeWhitespaceIdsAreCapturedWithoutNativeEncodingConversion()throws Exception {
        var raw=request("out");Files.writeString(directory.resolve("phenotype.tsv"),"sample_id\ttrait\n β-1 \t0.1\n β-2 \t0.5\n β-3 \t0.2\n β-4 \t0.9\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("genotypes.tsv"),"sample_id\tvariant-β#1\n β-1 \t0\n β-2 \t1\n β-3 \t0\n β-4 \t2\n",StandardCharsets.UTF_8);Files.writeString(directory.resolve("sets.tsv"),"set_id\tvariant_id\n gene-β \tvariant-β#1\n",StandardCharsets.UTF_8);
        Call c=execute(raw);assertEquals(3,c.code(),c.err());Path root=directory.resolve("out");assertArrayEquals(Files.readAllBytes(directory.resolve("phenotype.tsv")),Files.readAllBytes(root.resolve("inputs/phenotype.tsv")));
        assertTrue(Files.readString(root.resolve("worker/input-metadata.json"),StandardCharsets.UTF_8).contains(" β-1 "));assertEquals(0,call("skat-tools","--verify",root.toString()).code());
    }
    @Test void nonfiniteGenotypeIsFailureRatherThanRepairOrDependencySkip()throws Exception {var raw=request("out");Files.writeString(directory.resolve("genotypes.tsv"),"sample_id\tv1\ns1\tNaN\ns2\t1\ns3\t0\ns4\t2\n");Call c=execute(raw);assertEquals(2,c.code(),c.err());Path root=directory.resolve("out");assertEquals("FAIL2_NO_COMPLETE_RESULT",read(root.resolve("analysis-status.json")).get("status"));assertTrue(Files.readString(root.resolve("inputs/genotypes.tsv")).contains("NaN"));assertEquals(0,call("skat-tools","--verify",root.toString()).code());}
    @Test void constantContinuousOutcomeWithInterceptFailsBeforeOriginalModel()throws Exception {
        var raw=request("out");Files.writeString(directory.resolve("phenotype.tsv"),"sample_id\ttrait\ns1\t1\ns2\t1.0\ns3\t1e0\ns4\t1\n");Call c=execute(raw);assertEquals(2,c.code(),c.err());Path root=directory.resolve("out");assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("outcome is constant"));assertFalse(read(root.resolve("manifest.json")).containsKey("worker_pid"));assertFalse(Files.exists(root.resolve("admitted/p-values.tsv")));assertEquals(0,call("skat-tools","--verify",root.toString()).code());
    }
    @Test void constantBinaryOutcomeFailsEvenWithoutIntercept()throws Exception {
        var raw=request("out");raw.put("null_model",Map.of("out_type","D","intercept",false));Files.writeString(directory.resolve("phenotype.tsv"),"sample_id\ttrait\ns1\t0\ns2\t-0\ns3\t0.0\ns4\t0e0\n");Call c=execute(raw);assertEquals(2,c.code(),c.err());assertTrue(Files.readString(directory.resolve("out/WARNINGS.md")).contains("outcome is constant"));
    }
    @Test void outcomeVariationIsCheckedAfterDeclaredCompleteCases()throws Exception {
        var raw=request("out");raw.put("null_model",Map.of("out_type","C","missing_model","complete-cases"));raw.put("covariates","covariates.tsv");Files.writeString(directory.resolve("phenotype.tsv"),"sample_id\ttrait\ns1\t2\ns2\t1\ns3\t1\ns4\t1\n");Files.writeString(directory.resolve("covariates.tsv"),"sample_id\tx\ns1\tNA\ns2\t0\ns3\t1\ns4\t2\n");Call c=execute(raw);assertEquals(2,c.code(),c.err());assertTrue(Files.readString(directory.resolve("out/WARNINGS.md")).contains("outcome is constant"));
    }
    @Test void constantContinuousOutcomeWithoutInterceptIsNotRejectedAsInterceptModel()throws Exception {
        var raw=request("out");raw.put("null_model",Map.of("out_type","C","intercept",false));raw.put("covariates","covariates.tsv");Files.writeString(directory.resolve("phenotype.tsv"),"sample_id\ttrait\ns1\t1\ns2\t1\ns3\t1\ns4\t1\n");Files.writeString(directory.resolve("covariates.tsv"),"sample_id\tx\ns1\t1\ns2\t2\ns3\t3\ns4\t4\n");Call c=execute(raw);assertEquals(3,c.code(),c.err());assertFalse(c.err().contains("outcome is constant"));
    }
    @Test void existingOutputAndUnrelatedFileArePreserved()throws Exception {var raw=request("out");Path root=Files.createDirectory(directory.resolve("out")),marker=root.resolve("keep");Files.writeString(marker,"keep");Call c=execute(raw);assertEquals(2,c.code());assertEquals("keep",Files.readString(marker));assertEquals(1,Files.list(root).count());}
    @Test void resealedWarningsCannotOmitRecoveryAndScope()throws Exception {var raw=request("out");assertEquals(3,execute(raw).code());Path root=directory.resolve("out");Files.writeString(root.resolve("WARNINGS.md"),"all fine\n");reseal(root);Call c=call("skat-tools","--verify",root.toString());assertEquals(2,c.code(),c.err());}
    @Test void resealedRawRequestCannotChangeScientificSeed()throws Exception {var raw=request("out");assertEquals(3,execute(raw).code());Path root=directory.resolve("out");var source=read(root.resolve("source-request.json"));source.put("seed",19);SkatWorkflowFiles.json(root.resolve("source-request.json"),source);var manifest=read(root.resolve("manifest.json"));manifest.put("source_request_sha256",SkatWorkflowFiles.hash(Files.readAllBytes(root.resolve("source-request.json"))));SkatWorkflowFiles.json(root.resolve("manifest.json"),manifest);reseal(root);assertEquals(2,call("skat-tools","--verify",root.toString()).code());}
    @Test void failedOrSkippedParentCannotBeReplayedAsSuccessful()throws Exception {var raw=request("out");assertEquals(3,execute(raw).code());Path root=directory.resolve("out");byte[] before=Files.readAllBytes(root.resolve("capture-files.json"));var replay=Map.of("schema_version",1,"mode","REPLAY","analysis",root.toString(),"out",directory.resolve("replay").toString());Call c=call("skat-tools","--request",write("replay.json",replay).toString());assertEquals(2,c.code(),c.err());assertFalse(Files.exists(directory.resolve("replay")));assertArrayEquals(before,Files.readAllBytes(root.resolve("capture-files.json")));}
    @Test void verifyControlCannotSmuggleRuntimeSettings()throws Exception {var raw=request("out");assertEquals(3,execute(raw).code());Call c=call("skat-tools","--request",write("verify.json",Map.of("schema_version",1,"mode","VERIFY","analysis",directory.resolve("out").toString(),"rscript","unused")).toString());assertEquals(2,c.code(),c.err());}
    @Test void latePerSetFailuresAreDecodedAndCannotHideBehindEarlierCleanRows()throws Exception {
        Path root=Files.createDirectory(directory.resolve("warnings")),worker=Files.createDirectory(root.resolve("worker"));var text=new StringBuilder("set_index\tset_id\toriginal_call_attempted\tsource_returned\traw_p\texport_p\tp_kind\tsource_test_type_hex\tinput_variants\ttested_variants\tresult_admitted\tissues_hex\tnotes_hex\tbound_hex\n");
        for(int i=1;i<=150;i++)text.append(i).append("\tgene-").append(i).append("\tTRUE\tTRUE\t.5\t.5\tnumeric-pvalue\t\t1\t1\tTRUE\t\t\t\n");
        String issue="Original test substitution was not accepted";String hex=java.util.HexFormat.of().formatHex(issue.getBytes(StandardCharsets.UTF_8));text.append("151\tlate-β\tTRUE\tTRUE\t.5\tNA\tunadmitted\t\t1\t1\tFALSE\t").append(hex).append("\t\t\n");Files.writeString(worker.resolve("set-results.tsv"),text.toString(),StandardCharsets.UTF_8);
        String warning=SkatWorkflowCli.warnings(root,"FAIL2_NO_COMPLETE_RESULT","one set failed",Map.of());assertTrue(warning.contains("id=late-β"));assertTrue(warning.contains(issue));assertTrue(warning.contains("Set admission failures (1;"));assertTrue(warning.contains("explicit admission flag"));
    }
}
