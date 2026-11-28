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

class WgcnaToolsTest {
    @TempDir Path temporary;
    private Map<String,Object> request()throws Exception{Files.writeString(temporary.resolve("matrix.tsv"),"id\ta\tb\tc\ndonor ' one\t1\t3\tNA\nδ two\t2\t2\t3\nthird\t4\t1\t2\n");return new LinkedHashMap<>(Map.of("schema_version",1,"mode","RUN","backend","NATIVE","operation","RANK_PVALUE","matrix","matrix.tsv","out","result","calculate_qvalue",false));}
    private int run(String...args){return WgcnaToolsCli.run(args,new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8),new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8));}
    private int execute(Map<String,Object> r)throws Exception{Path path=temporary.resolve("request.json");json(path,r);return run("--request",path.toString());}
    @Test void capturesLiteralUnicodeInputsAndVerifiesWithoutR()throws Exception{var r=request();assertEquals(0,execute(r));Path root=temporary.resolve("result");assertArrayEquals(Files.readAllBytes(temporary.resolve("matrix.tsv")),Files.readAllBytes(root.resolve("inputs/matrix")));assertTrue(Files.readString(root.resolve("results/rank-pvalues.tsv")).contains("donor ' one"));assertEquals(0,run("--verify",root.toString()));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("REQUIRED"));}
    @Test void refusesDerivedTamperingEvenAfterResealing()throws Exception{assertEquals(0,execute(request()));Path root=temporary.resolve("result");Files.writeString(root.resolve("results/rank-pvalues.tsv"),Files.readString(root.resolve("results/rank-pvalues.tsv")).replaceFirst("0\\.[0-9]+","0.123"));SvaWorkflowCli.seal(root);assertEquals(2,run("--verify",root.toString()));}
    @Test void missingInputHasProminentSkipAndAction()throws Exception{var r=request();r.put("matrix","missing.tsv");assertEquals(3,execute(r));assertTrue(Files.readString(temporary.resolve("result/WARNINGS.md")).contains("SKIP3_DEPENDENT_ANALYSIS_SKIPPED"));assertEquals(2,run("--verify",temporary.resolve("result").toString()));}
    @Test void malformedInputFailsWithoutAdmittingResults()throws Exception{var r=request();Files.writeString(temporary.resolve("matrix.tsv"),"id\ta\na\tbogus\nb\t2\n");assertEquals(2,execute(r));assertFalse((Boolean)read(temporary.resolve("result/analysis-status.json")).get("results_admitted"));}
    @Test void preservesExistingOutputBytes()throws Exception{var r=request();Files.createDirectory(temporary.resolve("result"));Files.writeString(temporary.resolve("result/unique"),"keep");assertEquals(2,execute(r));assertEquals("keep",Files.readString(temporary.resolve("result/unique")));}
    @Test void rejectsUnknownUnusedAndUnqualifiedOptions()throws Exception{var r=request();r.put("power",6);assertThrows(IOException.class,()->WgcnaToolsRequest.validate(r));r.remove("power");r.put("calculate_qvalue",true);assertThrows(IOException.class,()->WgcnaToolsRequest.validate(r));r.put("calculate_qvalue",false);r.put("ties","random");assertThrows(IOException.class,()->WgcnaToolsRequest.validate(r));}
    @Test void refusesDuplicateIdsRatherThanMakingNamesUnique()throws Exception{var r=request();Files.writeString(temporary.resolve("matrix.tsv"),"id\ta\nduplicate\t1\nduplicate\t2\n");assertEquals(2,execute(r));}
    @Test void entryCapacityFailsWithoutTruncatingRows()throws Exception{var r=request();r.put("maximum_entries",8);assertEquals(2,execute(r));assertFalse(Files.exists(temporary.resolve("result/results/rank-pvalues.tsv")));}
    @Test void missingOriginalRuntimeSkipsBeforeAnalysis()throws Exception{var r=request();r.put("backend","ORIGINAL_R");r.put("profile",WgcnaToolsRequest.PROFILE);r.put("rscript","unavailable-Rscript");r.put("r_libraries",List.of(temporary.toString()));assertEquals(3,execute(r));assertFalse(Files.exists(temporary.resolve("result/worker/objects.rds")));}
    @Test void refusesIncorrectAdjacencyIdentityOrder()throws Exception{var r=request();r.remove("calculate_qvalue");r.put("operation","CONNECTIVITY");r.put("modules","modules.tsv");Files.writeString(temporary.resolve("matrix.tsv"),"id\ta\tb\nb\t1\t2\na\t2\t1\n");Files.writeString(temporary.resolve("modules.tsv"),"id\tmodule\nb\tx\na\tx\n");assertEquals(2,execute(r));}
    @Test void nativeConnectivityRoundTripPreservesMissingness()throws Exception{var r=request();r.remove("calculate_qvalue");r.put("operation","CONNECTIVITY");r.put("modules","modules.tsv");r.put("scale_by_max",true);Files.writeString(temporary.resolve("matrix.tsv"),"id\ta\tb\nc\t1\t2\na\t2\t1\n");Files.writeString(temporary.resolve("matrix.tsv"),"id\ta\tb\na\t1\t2\nb\t2\t1\n");Files.writeString(temporary.resolve("modules.tsv"),"id\tmodule\na\tx\nb\tx\n");assertEquals(0,execute(r));assertTrue(Files.readString(temporary.resolve("result/results/connectivity.tsv")).contains("\tNA\tNA"));assertEquals(0,run("--verify",temporary.resolve("result").toString()));}
    @Test void invalidUtf8AndNumericOverflowAreNotRepaired()throws Exception{var r=request();Files.write(temporary.resolve("matrix.tsv"),new byte[]{(byte)0xc3,0x28});assertEquals(2,execute(r));assertThrows(IOException.class,()->WgcnaToolsRequest.numeric("1e999"));assertThrows(IOException.class,()->WgcnaToolsRequest.numeric("0x1p1"));}
}
