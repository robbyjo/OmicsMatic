/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LinkedMrCliTest {
    @TempDir Path root;
    private static final String HEADER = "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp\teaf\tn\tqc_pass\tmeasurement\tquality_metric\tquality\n";
    private Map<String, Object> fixture() throws Exception {
        Files.writeString(root.resolve("x.tsv"), HEADER + "lead\t1\t100\tA\tG\t0.4\t0.02\t1e-30\t0.2\t1000\ttrue\tsequenced\tNA\tNA\n"
            + "proxy\t1\t200\tA\tG\t0.3\t0.02\t1e-12\t0.2\t1000\ttrue\tsequenced\tNA\tNA\n");
        Files.writeString(root.resolve("y.tsv"), HEADER + "lead\t1\t100\tA\tG\t0\t0.1\t1\t0.2\t100000\ttrue\timputed\tminimac_rsq\t0.95\n"
            + "proxy\t1\t200\tA\tG\t0.3\t0.1\t0.003\t0.2\t100000\ttrue\timputed\tminimac_rsq\t0.99\n");
        Files.writeString(root.resolve("ld.tsv"), "target_id\tproxy_id\tr_squared\nlead\tproxy\t0.95\n");
        var test = new LinkedHashMap<String, Object>();
        test.put("id", "ratio-trait"); test.put("feature_id", "ENST-fixture.1"); test.put("chromosome", "1");
        test.put("cis_start", 1); test.put("cis_end", 1000); test.put("sample_overlap", "none"); test.put("sample_overlap_basis", "Independent synthetic samples");
        test.put("exposure", source("x.tsv", "qtl", "ENST-fixture.1")); test.put("outcome", source("y.tsv", "gwas", "trait"));
        test.put("ld", Map.of("file", "ld.tsv", "source_id", "reference", "genome_build", "GRCh38", "ancestry", "fixture-population", "sample_size", 500, "reference_basis", "Synthetic known LD"));
        var request = new LinkedHashMap<String, Object>();
        request.put("schema_version", 1); request.put("family_id", "synthetic-family"); request.put("family_size", 10000);
        request.put("alpha", .05); request.put("selection_basis", "exposure_only");
        request.put("policy", Map.of("instrument_p", 5e-8, "minimum_f", 10, "imputation_metric", "minimac_rsq", "minimum_quality", .8, "minimum_proxy_r2", .8, "confidence_level", .95));
        request.put("tests", new ArrayList<>(List.of(test))); return request;
    }
    private Map<String, Object> source(String file, String id, String trait) {
        return new LinkedHashMap<>(Map.of("file", file, "source_id", id, "trait_id", trait, "units", "declared trait units",
            "genome_build", "GRCh38", "ancestry", "fixture-population", "effect_model", "additive_per_allele"));
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> test(Map<String, Object> request) { return (Map<String, Object>) ((List<?>) request.get("tests")).get(0); }
    private int execute(Map<String, Object> request, String name) throws Exception {
        StudyPublicEvidence.write(root.resolve("request.yaml"), request);
        var bytes = new ByteArrayOutputStream();
        try (var stream = new PrintStream(bytes)) {
            return LinkedMrCli.run(new String[]{"--request", root.resolve("request.yaml").toString(), "--out", root.resolve(name).toString()}, stream, stream);
        }
    }
    private int verify(String name) {
        var bytes = new ByteArrayOutputStream();
        try (var stream = new PrintStream(bytes)) { return LinkedMrCli.run(new String[]{"--verify", root.resolve(name).toString()}, stream, stream); }
    }

    @Test void endToEndRetainsNullRecordsFamilyAndVerifiesAllFiles() throws Exception {
        var request = fixture(); assertEquals(0, execute(request, "result"));
        var table = DelimitedData.read(root.resolve("result/results.tsv"));
        String[] row = table.rows().get(0);
        assertEquals("lead", row[table.column("selected_variant")]); assertEquals("1.0", row[table.column("mr_p")]);
        assertEquals("false", row[table.column("association_supported")]); assertEquals("not_established", row[table.column("causal_mediation")]);
        assertEquals(10000, StudyYaml.loadMap(root.resolve("result/report.yaml")).get("family_size"));
        assertEquals(0, verify("result"));
        Files.writeString(root.resolve("result/ratio-trait/inputs/outcome.tsv"), "tampered\n"); assertEquals(2, verify("result"));
        assertEquals(2, execute(request, "result"));
    }

    @Test void unreliableLeadUsesProxyAndNoOutcomeRemainsInDenominator() throws Exception {
        var request = fixture();
        Files.writeString(root.resolve("y.tsv"), Files.readString(root.resolve("y.tsv")).replace("minimac_rsq\t0.95", "minimac_rsq\t0.2"));
        assertEquals(0, execute(request, "proxy-result"));
        var table = DelimitedData.read(root.resolve("proxy-result/results.tsv"));
        assertEquals("proxy", table.rows().get(0)[table.column("selected_variant")]);
        Files.writeString(root.resolve("y.tsv"), HEADER);
        assertEquals(0, execute(request, "missing-result"));
        table = DelimitedData.read(root.resolve("missing-result/results.tsv"));
        assertEquals("not_testable_no_reliable_outcome", table.rows().get(0)[table.column("status")]);
        assertEquals("NA", table.rows().get(0)[table.column("mr_p")]);
        assertEquals(10000, StudyYaml.loadMap(root.resolve("missing-result/report.yaml")).get("family_size"));
        assertEquals(0, verify("missing-result"));
    }

    @Test void strictSensitivityCannotReplacePrimaryWithMoreFavorableResult() throws Exception {
        var request = fixture();
        Files.writeString(root.resolve("y.tsv"), Files.readString(root.resolve("y.tsv")).replace("minimac_rsq\t0.95", "minimac_rsq\t0.85"));
        assertEquals(0, execute(request, "sensitivity"));
        var result = StudyYaml.loadMap(root.resolve("sensitivity/ratio-trait/result.yaml"));
        assertEquals("lead", result.get("selected_variant")); assertEquals(true, result.get("sensitivity_selection_changed"));
        assertEquals("proxy", StudyPublicEvidence.map(result.get("strict_quality_ld_sensitivity")).get("selected_variant"));
    }

    @Test void completeRegionalColocRunsAllDeclaredPriorsWithoutCausalClaim() throws Exception {
        var request = fixture();
        Files.writeString(root.resolve("effects.tsv"), "effect_index\tvariant_id\tlog_bayes_factor\n0\tlead\t20\n0\tproxy\t1\n");
        Files.writeString(root.resolve("susie.log"), "converged=true\n");
        var coloc = new LinkedHashMap<String, Object>();
        coloc.put("trait1_effects", "effects.tsv"); coloc.put("trait2_effects", "effects.tsv");
        coloc.put("trait1_log", "susie.log"); coloc.put("trait2_log", "susie.log");
        coloc.put("exposure_source_id", "qtl"); coloc.put("outcome_source_id", "gwas");
        coloc.put("exposure_trait_id", "ENST-fixture.1"); coloc.put("outcome_trait_id", "trait");
        coloc.put("genome_build", "GRCh38"); coloc.put("ancestry", "fixture-population");
        coloc.put("complete_region", true); coloc.put("regional_inputs_reviewed", true);
        coloc.put("p1", 1e-4); coloc.put("p2", 1e-4); coloc.put("shared_priors", List.of(5e-6, 1e-6)); coloc.put("minimum_overlap", .5);
        test(request).put("colocalization", coloc);
        assertEquals(0, execute(request, "coloc"));
        assertTrue(Files.isRegularFile(root.resolve("coloc/ratio-trait/coloc-0.tsv")));
        assertTrue(Files.isRegularFile(root.resolve("coloc/ratio-trait/coloc-1.tsv")));
        var result = StudyYaml.loadMap(root.resolve("coloc/ratio-trait/result.yaml"));
        assertEquals("not_established", result.get("causal_mediation")); assertEquals(false, result.get("association_supported"));
        assertEquals(0, verify("coloc"));
        coloc.put("complete_region", false); assertEquals(2, execute(request, "partial"));
        assertFalse(Files.exists(root.resolve("partial/COMPLETED"))); assertTrue(Files.exists(root.resolve("partial/FAILED.txt")));
    }

    @Test void invalidScopeDoesNotAdmitCompletedOutput() throws Exception {
        var request = fixture(); test(request).put("sample_overlap", "unknown");
        assertEquals(2, execute(request, "overlap")); assertFalse(Files.exists(root.resolve("overlap/COMPLETED")));
        test(request).put("sample_overlap", "none");
        var wrong = source("y.tsv", "gwas", "trait"); wrong.put("genome_build", "GRCh37"); test(request).put("outcome", wrong);
        assertEquals(2, execute(request, "build"));
        test(request).put("outcome", source("y.tsv", "gwas", "trait"));
        request.put("family_size", 0); assertEquals(2, execute(request, "family"));
        request.put("family_size", 1); request.put("outcome_p_threshold", .05); assertEquals(2, execute(request, "selection"));
    }

    @Test void malformedNumericAndUnknownQualityAreDistinct() throws Exception {
        var request = fixture();
        Files.writeString(root.resolve("y.tsv"), Files.readString(root.resolve("y.tsv")).replace("minimac_rsq\t0.95", "NA\tNA").replace("minimac_rsq\t0.99", "NA\tNA"));
        assertEquals(0, execute(request, "unknown"));
        var result = StudyYaml.loadMap(root.resolve("unknown/ratio-trait/result.yaml"));
        assertEquals("imputation_quality_unknown", result.get("original_outcome_disposition"));
        Files.writeString(root.resolve("y.tsv"), Files.readString(root.resolve("y.tsv")).replace("\t0.1\t", "\tNaN\t"));
        assertEquals(2, execute(request, "invalid"));
    }

    @Test void dispatcherExposesWorkflow() {
        var bytes = new ByteArrayOutputStream();
        try (var stream = new PrintStream(bytes)) {
            assertEquals(0, JLinAlgCli.run(new String[]{"mr-linked", "--help"}, stream, stream));
            assertTrue(bytes.toString().contains("high-LD"));
        }
    }

    @Test void offlineStudyCustomStepRunsRequestAndCapturesBundle() throws Exception {
        var request = fixture(); StudyPublicEvidence.write(root.resolve("request.yaml"), request);
        var configuration = Map.<String, Object>of("command", "mr-linked", "arguments",
            List.of("--out", "@output@/mr", "--request", "@project@/request.yaml", "--no-log"),
            "outputs", List.of("mr/report.yaml", "mr/results.tsv", "mr/manifest.yaml", "mr/COMPLETED"));
        var operation = Map.<String, Object>of("id", "custom-mr", "type", "mendelian_randomization", "configuration", configuration);
        var context = new org.jlinalg.study.StudyOperationExecutor.Context(root, root.resolve("study-step"), operation,
            Map.of("resources", Map.of("network", "offline")), Map.of(), Map.of());
        var result = new StudyCliExecutor().execute(context);
        assertEquals(org.jlinalg.study.StudyOperationExecutor.State.COMPLETE, result.state(), result.summary());
        assertTrue(Files.isRegularFile(root.resolve("study-step/mr/COMPLETED")));
        assertEquals(0, verify("study-step/mr"));
    }

    @Test void duplicateOptionsAreRejectedWithoutOutputs() throws Exception {
        fixture(); var bytes = new ByteArrayOutputStream();
        try (var stream = new PrintStream(bytes)) {
            assertEquals(2, LinkedMrCli.run(new String[]{"--out", root.resolve("x").toString(), "--out", root.resolve("y").toString()}, stream, stream));
        }
        assertFalse(Files.exists(root.resolve("x"))); assertFalse(Files.exists(root.resolve("y")));
    }
}
