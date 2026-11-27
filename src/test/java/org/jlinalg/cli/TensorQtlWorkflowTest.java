/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TensorQtlWorkflowTest {
    @TempDir Path temporary;
    private Map<String,Object> request(){
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("profile","tensorqtl-1.0.9-py3.11.17-torch2.3.1+cu121");
        r.put("operation","TRANS_NOMINAL");r.put("out",temporary.resolve("analysis").toString());r.put("python","missing-python");r.put("source_directory","original");r.put("device","CPU");r.put("seed",9);r.put("acknowledge_original_processing",true);r.put("genotypes","genotypes.tsv");r.put("phenotypes","phenotypes.tsv");return r;
    }
    private Map<String,Object> fixture()throws Exception{
        Files.createDirectory(temporary.resolve("worker"));Files.writeString(temporary.resolve("genotypes.tsv"),"variant_id\ts1\ts2\ts3\ts4\ts5\ts6\nv1\t0\t0.375\t1\t2\t-9\t0\nv2\t1\t2\t0\t1\t0\t2\n");
        Files.writeString(temporary.resolve("phenotypes.tsv"),"phenotype_id\ts1\ts2\ts3\ts4\ts5\ts6\np1\t1\t2\t4\t3\t2\t0\np2\t2\t1\t3\t4\t0\t1\n");return request();
    }
    @Test void seedAndOriginalProcessingMustBeExplicit(){var r=request();r.remove("seed");assertThrows(IOException.class,()->TensorQtlWorkflowContracts.validate(r));var other=request();other.remove("acknowledge_original_processing");assertThrows(IOException.class,()->TensorQtlWorkflowContracts.validate(other));}
    @Test void doesNotInventTransCoordinates()throws Exception{var r=TensorQtlWorkflowContracts.validate(request());assertFalse(r.containsKey("variants"));assertEquals(.05,r.get("maf_threshold"));assertEquals(20000L,r.get("batch_size"));}
    @Test void preservesFractionalDosagesAndOriginalMissingSentinel()throws Exception{var r=TensorQtlWorkflowContracts.validate(fixture());TensorQtlWorkflowFiles.prepare(temporary,r,false);String text=Files.readString(temporary.resolve("worker/genotypes.tsv"));assertTrue(text.contains("0.375"));assertTrue(text.contains("-9.0"));}
    @Test void rejectsReorderedSamples()throws Exception{var r=fixture();String text=Files.readString(temporary.resolve("phenotypes.tsv"));Files.writeString(temporary.resolve("phenotypes.tsv"),text.replace("s1\ts2","s2\ts1"));assertThrows(IOException.class,()->TensorQtlWorkflowFiles.prepare(temporary,TensorQtlWorkflowContracts.validate(r),false));}
    @Test void rejectsDuplicateVariantIdentity()throws Exception{var r=fixture();Path p=temporary.resolve("genotypes.tsv");Files.writeString(p,Files.readString(p).replace("v2\t","v1\t"));assertThrows(IOException.class,()->TensorQtlWorkflowFiles.prepare(temporary,TensorQtlWorkflowContracts.validate(r),false));}
    @Test void regionCenterRequiresExplicitMeaning()throws Exception{var r=fixture();r.put("phenotype_positions","positions.tsv");r.put("trans_filter_positions","centers.tsv");r.put("trans_filter_position_kind","declared-region-center");Files.writeString(temporary.resolve("positions.tsv"),"phenotype_id\tchr\tpos\np1\t6\t25000000\np2\t1\t123\n");Files.writeString(temporary.resolve("centers.tsv"),"variant_id\tchr\tpos\nv1\t6\t29563353.5\nv2\t6\t29563353.5\n");TensorQtlWorkflowFiles.prepare(temporary,TensorQtlWorkflowContracts.validate(r),false);assertTrue(Files.readString(temporary.resolve("worker/trans_filter_positions.tsv")).contains("29563353.5"));r.put("trans_filter_position_kind","variant");assertThrows(IOException.class,()->TensorQtlWorkflowFiles.prepare(temporary,TensorQtlWorkflowContracts.validate(r),true));}
    @Test void unusedInputAndFormulaAreRefused(){var r=request();r.put("variants","unused.tsv");assertThrows(IOException.class,()->TensorQtlWorkflowContracts.validate(r));var other=request();other.put("formula","~genotype");assertThrows(IOException.class,()->TensorQtlWorkflowContracts.validate(other));}
    @Test void qvalueDoesNotClaimGpuComputation(){var r=request();r.put("operation","QVALUES");r.put("cis_results","cis.tsv");r.put("r_home","R");r.put("device","CUDA");r.remove("genotypes");r.remove("phenotypes");assertThrows(IOException.class,()->TensorQtlWorkflowContracts.validate(r));}
    @Test void mappingCapacityIsCheckedBeforeRuntime()throws Exception{var r=fixture();r.put("maximum_entries",1);assertThrows(IOException.class,()->TensorQtlWorkflowFiles.prepare(temporary,TensorQtlWorkflowContracts.validate(r),false));}
    @Test void unknownProfileSkipsWithoutSubstitution()throws Exception{var r=request();r.put("profile","tensorqtl-newest");Path file=temporary.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();assertEquals(3,TensorQtlWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(log),new PrintStream(log)));assertTrue(log.toString(StandardCharsets.UTF_8).contains("not qualified"));}
    @Test void missingInputSkipsAndCannotVerify()throws Exception{var r=request();Path file=temporary.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();assertEquals(3,TensorQtlWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(log),new PrintStream(log)));assertFalse(Boolean.TRUE.equals(SeuratDifferentialContracts.read(temporary.resolve("analysis/analysis-status.json")).get("results_admitted")));assertThrows(IOException.class,()->TensorQtlWorkflowCli.verify(temporary.resolve("analysis")));}
    @Test void existingOutputIsPreserved()throws Exception{Path output=temporary.resolve("analysis");Files.createDirectory(output);Files.writeString(output.resolve("keep"),"unique");var r=request();Path file=temporary.resolve("request.json");SeuratDifferentialContracts.json(file,r);var log=new ByteArrayOutputStream();assertEquals(2,TensorQtlWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(log),new PrintStream(log)));assertEquals("unique",Files.readString(output.resolve("keep")));}
}
