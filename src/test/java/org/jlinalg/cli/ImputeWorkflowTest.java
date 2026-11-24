/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

class ImputeWorkflowTest {
    @TempDir Path temp;
    private Map<String,Object> request(String operation)throws Exception {
        var inputs=map();for(String role:ImputeWorkflowContracts.roles(operation))inputs.put(role,role);
        var options=map("seed",1729,"Ne",20000,"interval_start",100,"interval_end",200,"parameter_policy","original-v2.3.2-defaults","sample_identity_policy","unlabelled-ordered-columns","input_reviewed",true,"genotype_audit","rounding-bounds");
        if(operation.equals("IMPUTE_GENOTYPES"))options.put("typed_genotype_policy","preserve-input");
        return ImputeWorkflowContracts.validate(map("schema_version",1,"mode","RUN","profile",ImputeWorkflowContracts.PROFILE,"operation",operation,"runtime_home","runtime","out","result","assembly","GRCh37","chromosome","22","license_accepted",true,"inputs",inputs,"options",options),temp.resolve("request.json"));
    }
    private void file(String path,String value)throws Exception {Path p=temp.resolve(path);Files.createDirectories(p.getParent());Files.writeString(p,value);}
    private void fixture()throws Exception {
        file("inputs/map","position COMBINED_rate.cM.Mb. Genetic_Map.cM.\n90 0.1 0\n210 0.1 .01\n");
        file("inputs/reference_legend","rsID position a0 a1 annotation\nrs1 100 A G kept\nrs2 101 A T kept\n");
        file("inputs/reference_haps","0 1 0 1\n1 0 1 0\n");
        file("inputs/study_genotypes","--- sameID 100 A G .999 0 0 0 0 0\n--- sameID 100 C T 0 1 0 0 0 1\n");
        file("inputs/strand","100 +\n101 -\n");
    }
    @Test void inputAuditPreservesNonunitMissingCodesAndDuplicatePositions()throws Exception {
        fixture();var r=request("IMPUTE_GENOTYPES");byte[] before=Files.readAllBytes(temp.resolve("inputs/study_genotypes"));var audit=ImputeWorkflowData.inputs(temp,r);
        var s=object(audit.get("study"));assertEquals(1L,number(s,"all_zero_missing_genotypes"));assertEquals(1L,number(s,"nonunit_nonmissing_probability_sums"));assertEquals(2L,number(s,"samples"));assertArrayEquals(before,Files.readAllBytes(temp.resolve("inputs/study_genotypes")));
    }
    @Test void literalGzipByMagicDoesNotRequireAnExtension()throws Exception {
        fixture();Path file=temp.resolve("inputs/study_genotypes");byte[] plain=Files.readAllBytes(file);try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write(plain);}
        assertEquals(2L,number(object(ImputeWorkflowData.inputs(temp,request("IMPUTE_GENOTYPES")).get("study")),"rows"));
    }
    @Test void malformedWidthsReferenceRowsAndUnphasedHapsAreRefused()throws Exception {
        fixture();file("inputs/reference_haps","0 1\n");assertThrows(Exception.class,()->ImputeWorkflowData.inputs(temp,request("IMPUTE_GENOTYPES")));
        fixture();file("inputs/study_haps","--- id 100 A G 0 .5\n");assertThrows(Exception.class,()->ImputeWorkflowData.inputs(temp,request("IMPUTE_PREPHASED")));
        fixture();file("inputs/study_genotypes","--- id 100 A G 0 1\n");assertThrows(Exception.class,()->ImputeWorkflowData.inputs(temp,request("IMPUTE_GENOTYPES")));
    }
    @Test void mapsDoNotInventCoverageOrAcceptDecreasingGeneticPositions()throws Exception {
        fixture();file("inputs/map","position COMBINED_rate.cM.Mb. Genetic_Map.cM.\n120 .1 3\n130 .1 4\n");assertNotNull(ImputeWorkflowData.inputs(temp,request("IMPUTE_GENOTYPES")));
        file("inputs/map","120 .1 4\n130 .1 3\n");assertThrows(Exception.class,()->ImputeWorkflowData.inputs(temp,request("IMPUTE_GENOTYPES")));
    }
    private void outputs()throws Exception {
        file("worker/result","s r 100 A G .333 .333 .333 0 0 0\ns r2 101 A T .334 .334 .334 NaN 0 1\n");
        file("worker/result_info","snp_id rs_id position a0 a1 exp_freq_a1 info certainty type info_type0 concord_type0 r2_type0\ns r 100 A G .5 -1 NaN 0 -1 -1 -1\ns r2 101 A T .5 .9 .8 2 -1 -1 -1\n");
        file("worker/result_info_by_sample","concord_type0 r2_type0\n-1 -1\n.5 NaN\n");
    }
    @Test void roundingNonfiniteAndMissingOutputsAreKeptWithEligibility()throws Exception {
        fixture();outputs();var request=request("IMPUTE_GENOTYPES");var audit=ImputeWorkflowData.outputs(temp,request,ImputeWorkflowData.inputs(temp,request));
        assertEquals(1L,number(audit,"all_zero_missing_GP"));assertEquals(2L,number(audit,"nonunit_GP_sums"));assertEquals(1L,number(audit,"infeasible_conditional_unit_mass_GP"));assertEquals(1L,number(audit,"invalid_GP"));assertEquals(1L,number(audit,"nonfinite_INFO_fields"));
        String exceptions=Files.readString(temp.resolve("unreportable-genotypes.tsv"));assertTrue(exceptions.contains("MISSING_ALL_ZERO_NO_DOSAGE"));assertTrue(exceptions.contains("UNREPORTABLE_CONDITIONAL_BOUNDS_INFEASIBLE"));assertTrue(exceptions.contains("NaN"));
        String bounds=Files.readString(temp.resolve("genotype-bounds.tsv"));assertEquals(2,bounds.lines().count());assertTrue(bounds.contains(".333\t.333\t.333"));
        assertTrue(bounds.contains("conditional_unit_mass_dosage_lower"));assertEquals(false,audit.get("unit_mass_established_by_output_tokens"));
    }
    @Test void genotypeInfoPopulationMismatchIsNotJoinedByAnInferredId()throws Exception {
        fixture();outputs();file("worker/result_info","snp_id rs_id position a0 a1 exp_freq_a1 info certainty type\ns different 100 A G .5 .9 .9 0\n");var r=request("IMPUTE_GENOTYPES");assertThrows(Exception.class,()->ImputeWorkflowData.outputs(temp,r,ImputeWorkflowData.inputs(temp,r)));
    }
    @Test void explicitlySmallCapRefusesInputsAndOversizedLines()throws Exception {
        fixture();var r=request("IMPUTE_GENOTYPES");var limits=object(r.get("limits"));limits.put("maximum_genotypes",1);r.put("limits",limits);var capped=r;assertThrows(Exception.class,()->ImputeWorkflowData.inputs(temp,capped));
        r=request("IMPUTE_GENOTYPES");limits=object(r.get("limits"));limits.put("maximum_line_characters",10);r.put("limits",limits);var tooLong=r;assertThrows(Exception.class,()->ImputeWorkflowData.inputs(temp,tooLong));
    }
    @Test void sourceSpecificFlagsAreClosedAndIntegerTokensCanonicalized()throws Exception {
        var r=request("IMPUTE_GENOTYPES");var options=object(r.get("options"));options.put("seed",new org.jlinalg.gprofiler.GprofilerJson.Numeric("1.729e3"));options.put("typed_genotype_policy","fill-missing-at-source-threshold");r.put("options",options);var command=ImputeWorkflowRuntime.command(temp,r);
        assertEquals("1729",command.get(command.indexOf("-seed")+1));assertTrue(command.contains("-pgs_miss"));assertFalse(command.contains("-pgs"));assertFalse(command.contains("-iter"));
        assertTrue(ImputeWorkflowRuntime.command(temp,request("PREPHASE")).contains("-prephase_g"));assertFalse(ImputeWorkflowRuntime.command(temp,request("PREPHASE")).contains("-strand_g"));
        assertTrue(ImputeWorkflowRuntime.command(temp,request("MERGE_REFERENCE")).contains("-merge_ref_panels_output_ref"));
        assertTrue(ImputeWorkflowRuntime.command(temp,request("IMPUTE_PREPHASED")).contains("-use_prephased_g"));
    }
    @Test void unsupportedEditionsPloidiesFlagsAndUnacceptedTermsAreRefused()throws Exception {
        for(var pair:List.of(Map.entry("profile","impute5"),Map.entry("chromosome","X"),Map.entry("license_accepted",false),Map.entry("operation","IMPUTE5"))) {
            var r=request("IMPUTE_GENOTYPES");r.put(pair.getKey(),pair.getValue());assertThrows(Exception.class,()->ImputeWorkflowContracts.validate(r,temp.resolve("request.json")));
        }
        var r=request("IMPUTE_GENOTYPES");var o=object(r.get("options"));o.put("extra_flags","-iter 1");r.put("options",o);assertThrows(Exception.class,()->ImputeWorkflowContracts.validate(r,temp.resolve("request.json")));
    }
}
