/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SvaWorkflowTest {
    @TempDir Path temporary;
    private Map<String,Object> request(){
        var r=new LinkedHashMap<String,Object>();
        r.put("schema_version",1);r.put("mode","RUN");r.put("profile",SvaWorkflowContracts.PROFILE);r.put("operation","SVASEQ");
        r.put("out","analysis");r.put("matrix","matrix.tsv");r.put("design","design.tsv");r.put("sva_source","sva.tar.gz");
        r.put("units","normalized-counts");r.put("method","irw");r.put("n_sv",2);r.put("seed",9);
        r.put("rscript","Rscript");r.put("r_libraries",List.of("library"));return r;
    }
    @Test void preservesFractionalNormalizedCountDeclaration()throws Exception{var r=SvaWorkflowContracts.validate(request());assertEquals("normalized-counts",r.get("units"));assertEquals(1.0,r.get("constant"));assertEquals(5L,r.get("iterations"));}
    @Test void automaticCountRequiresDeclaredOriginalEstimator(){var r=request();r.put("n_sv","auto");assertThrows(IOException.class,()->SvaWorkflowContracts.validate(r));r.put("num_sv_method","leek");assertDoesNotThrow(()->SvaWorkflowContracts.validate(r));}
    @Test void refusesGuessedSeedAndRCode(){var r=request();r.remove("seed");assertThrows(IOException.class,()->SvaWorkflowContracts.validate(r));var candidate=request();candidate.put("formula","~genotype");assertThrows(IOException.class,()->SvaWorkflowContracts.validate(candidate));}
    @Test void preservesControlOverrideButRefusesIgnoredOptions(){var r=request();r.put("controls","controls.tsv");assertDoesNotThrow(()->SvaWorkflowContracts.validate(r));r.put("iterations",5);assertThrows(IOException.class,()->SvaWorkflowContracts.validate(r));}
    @Test void wrapperDoesNotPretendToConfigurePermutationCount(){var r=request();r.put("n_sv","auto");r.put("num_sv_method","be");r.put("permutations",99);assertThrows(IOException.class,()->SvaWorkflowContracts.validate(r));}
    @Test void countOnlyIsSeparateAndHasOriginalB20()throws Exception{var r=request();r.put("operation","NUM_SV");r.put("units","continuous-values");r.put("num_sv_method","be");r.remove("method");r.remove("n_sv");assertEquals(20L,SvaWorkflowContracts.validate(r).get("permutations"));}
    @Test void missingExactInputSkipsAndCannotVerify()throws Exception{var r=request();r.put("out",temporary.resolve("output").toString());Path file=temporary.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();assertEquals(3,SvaWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(log),new PrintStream(log)));assertTrue(log.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertFalse(Boolean.TRUE.equals(SeuratDifferentialContracts.read(temporary.resolve("output/analysis-status.json")).get("results_admitted")));assertThrows(IOException.class,()->SvaWorkflowCli.verify(temporary.resolve("output")));}
    @Test void foreignProfileSkipsWithoutSubstitution()throws Exception{var r=request();r.put("profile","sva-newest");Path file=temporary.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();assertEquals(3,SvaWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(log),new PrintStream(log)));assertTrue(log.toString(StandardCharsets.UTF_8).contains("not qualified"));}
    @Test void packagedWorkerCallsUnchangedNamespace()throws Exception{String r=new String(SvaWorkflowRuntime.resource("sva_workflow.R"),StandardCharsets.UTF_8);assertTrue(r.contains("sva::svaseq"));assertTrue(r.contains("sva::sva"));assertTrue(r.contains("sva::num.sv"));assertFalse(r.contains("assignInNamespace"));assertFalse(r.contains("set.seed(1)"));}
    @Test void existingOutputIsPreserved()throws Exception{Path output=temporary.resolve("analysis");Files.createDirectory(output);Files.writeString(output.resolve("keep"),"unique");var r=request();r.put("out",output.toString());Path file=temporary.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();assertEquals(2,SvaWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(log),new PrintStream(log)));assertEquals("unique",Files.readString(output.resolve("keep")));}
}
