/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.pipeline.VariantFilterOptions;
import org.jlinalg.pipeline.VariantFilterReason;
import org.jlinalg.pipeline.VariantFilters;
import org.jlinalg.pipeline.VariantRecord;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyWorkflowIntegrationTest {
    @TempDir Path root;

    @Test void newResolutionCapturesGwasAndManuscriptDefaultsAndSeparatesDiscoveryPool() throws Exception {
        Path source = root.resolve("study.yaml");
        Files.writeString(source, """
            schema_version: 1
            project: {id: workflow, title: Workflow, description: Scientific workflow contract.}
            datasets:
              - {id: a, cohort: c1, type: gwas, stage: association_results, files: {results: a.tsv}}
              - {id: b, cohort: c2, type: gwas, stage: association_results, files: {results: b.tsv}}
              - {id: v, cohort: c3, type: gwas, stage: association_results, files: {results: v.tsv}}
            hypotheses:
              - id: H
                description: Pooled discovery and independent validation.
                inputs: {discovery: [a,b], replication: [v]}
                analysis: {mode: manual, goals: [discovery_replication, meta_analysis]}
            """);
        var resolved = StudyResolver.resolve(source);
        var study = resolved.document();
        assertEquals(8000, map(map(study.get("project")).get("manuscript_policy")).get("max_main_words"));
        var analysis = map(map(list(study.get("hypotheses")).get(0)).get("analysis"));
        assertEquals(20.0, map(analysis.get("association")).get("minimum_mac"));
        assertEquals(0.02, map(analysis.get("association")).get("maximum_marker_missing"));
        assertFalse(map(analysis.get("association")).containsKey("model"));
        assertEquals("less_than", map(analysis.get("replication")).get("threshold_comparison"));
        var operations = list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        var discovery = operations.stream().filter(op -> "discovery_meta_analysis".equals(op.get("type"))).findFirst().orElseThrow();
        assertEquals(List.of("a", "b"), discovery.get("datasets"));
        var validation = operations.stream().filter(op -> "discovery_replication".equals(op.get("type"))).findFirst().orElseThrow();
        assertEquals(true, validation.get("pooled_discovery"));
        assertTrue(list(validation.get("depends_on")).contains(discovery.get("id")));
        var meta = operations.stream().filter(op -> "meta_analysis".equals(op.get("type"))).findFirst().orElseThrow();
        assertEquals(List.of("a", "b", "v"), meta.get("datasets"));
        assertFalse(list(meta.get("depends_on")).contains(discovery.get("id")));
    }

    @Test void macTwentyBoundaryDoesNotDeleteRareSetMembers() {
        var options = CliOptions.parse(new String[] {"--help"});
        assertEquals(20, options.minimumMac);
        assertEquals(0, CliOptions.parse(new String[] {"--min-mac", "0", "--help"}).minimumMac);
        var single = VariantFilterOptions.builder().minimumMac(options.minimumMac).build();
        double[] nineteen = new double[40], twenty = new double[40];
        java.util.Arrays.fill(nineteen, 0, 19, 1);
        java.util.Arrays.fill(twenty, 0, 20, 1);
        var low = new VariantRecord("low", "1", 1, "A", "G", nineteen, .99);
        var boundary = new VariantRecord("boundary", "1", 2, "A", "G", twenty, .99);
        assertTrue(VariantFilters.evaluate(low, single).reasons().contains(VariantFilterReason.BELOW_MINIMUM_MAC));
        assertTrue(VariantFilters.evaluate(boundary, single).included());
        assertTrue(VariantFilters.evaluate(low, VariantFilterOptions.defaults()).included());
        assertArrayEquals(nineteen, low.dosages());
    }

    @Test void missingProcessedMatrixModelStopsAssociationAndRetainsOfflineReview() throws Exception {
        Path output = root.resolve("missing-model");
        Map<String, Object> dataset = Map.of("id", "a", "type", "expression", "stage", "processed_matrix", "files", Map.of("matrix", "never-read.tsv"));
        Map<String, Object> hypothesis = Map.of("id", "H", "outcome", "BMI", "analysis", Map.of());
        var study = Map.<String, Object>of("project", Map.of(), "resources", Map.of("network", "offline"),
            "datasets", List.of(dataset), "hypotheses", List.of(hypothesis));
        var context = new StudyOperationExecutor.Context(root, output,
            Map.of("type", "association", "dataset_id", "a", "hypothesis", "H"), study, Map.of(), Map.of());
        var result = new StudyCliExecutor().execute(context);
        assertEquals(StudyOperationExecutor.State.WAITING_FOR_INPUT, result.state());
        assertEquals("offline_policy", map(result.details().get("model_review")).get("search_status"));
        assertTrue(Files.readString(output.resolve("model-review.yaml")).contains("association_model_required"));
        assertFalse(Files.exists(output.resolve("association.tsv")));
        assertFalse(list(result.details().get("needs")).isEmpty());
    }

    @Test void studyGenotypeAssociationActuallyForwardsExplicitMacOverride() throws Exception {
        StringBuilder phenotype = new StringBuilder("id\ty\n");
        StringBuilder vcf = new StringBuilder("##fileformat=VCFv4.2\n##contig=<ID=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for (int i = 0; i < 64; i++) {
            phenotype.append("S").append(i).append('\t').append(Math.sin(i) + (i < 20 ? 1 : 0)).append('\n');
            vcf.append("\tS").append(i);
        }
        vcf.append('\n');
        for (int carriers : List.of(19, 20)) {
            vcf.append("1\t").append(carriers).append("\tv").append(carriers).append("\tA\tG\t.\tPASS\t.\tGT");
            for (int i = 0; i < 64; i++) vcf.append(i < carriers ? "\t0/1" : "\t0/0");
            vcf.append('\n');
        }
        Files.writeString(root.resolve("pheno.tsv"), phenotype);
        Files.writeString(root.resolve("cohort.vcf"), vcf);
        Map<String, Object> dataset = Map.of("id", "a", "type", "gwas", "stage", "processed_matrix",
            "files", Map.of("matrix", "cohort.vcf", "phenotype", "pheno.tsv"), "columns", Map.of("sample_id", "id"));
        for (int cutoff : List.of(19, 20, 21)) {
            Map<String, Object> hypothesis = Map.of("id", "H", "outcome", "y", "analysis",
                Map.of("association", Map.of("model", "ols", "minimum_mac", cutoff, "maximum_marker_missing", 0.0)));
            var study = Map.<String, Object>of("datasets", List.of(dataset), "hypotheses", List.of(hypothesis));
            Path output = root.resolve("scan-" + cutoff);
            var result = new StudyCliExecutor().execute(new StudyOperationExecutor.Context(root, output,
                Map.of("type", "association", "dataset_id", "a", "hypothesis", "H"), study, Map.of(), Map.of()));
            assertEquals(StudyOperationExecutor.State.COMPLETE, result.state());
            var table = DelimitedData.readResult(output.resolve("standard-results/a.tsv"));
            assertEquals(cutoff == 19 ? 2 : cutoff == 20 ? 1 : 0, table.rows().size());
            if (cutoff < 21) assertEquals(cutoff == 19 ? "v19" : "v20", table.rows().get(0)[table.column("feature_id")]);
            else assertEquals("not_estimable", result.details().get("evidence_disposition"));
        }
    }

    @Test void discoveryPoolIgnoresValidationEffectsAndDrivesFrozenSelection() throws Exception {
        Path upstream = root.resolve("upstream"), standard = upstream.resolve("standard-results");
        Files.createDirectories(standard);
        Files.writeString(standard.resolve("a.tsv"), "feature_id\tbeta\tse\tp_value\nG\t1\t1\t.317\n");
        Files.writeString(standard.resolve("b.tsv"), "feature_id\tbeta\tse\tp_value\nG\t3\t1\t.0027\n");
        Files.writeString(standard.resolve("v.tsv"), "feature_id\tbeta\tse\tp_value\nG\t100\t1\t1e-100\n");
        Map<String, Object> hypothesis = Map.of("id", "H", "analysis", Map.of("replication", Map.of("adjustment_scope", "discovery_subset")));
        Map<String, Object> study = Map.of("hypotheses", List.of(hypothesis));
        StudyTestSources.seal(upstream,"validate_harmonize","harmonize","H");
        Path pool = root.resolve("pool");
        var poolResult = new StudyCliExecutor().execute(new StudyOperationExecutor.Context(root, pool,
            Map.of("type", "discovery_meta_analysis", "hypothesis", "H", "datasets", List.of("a", "b")),
            study, Map.of(), Map.of("harmonize", upstream)));
        assertEquals(StudyOperationExecutor.State.COMPLETE, poolResult.state());
        var pooled = DelimitedData.read(pool.resolve("discovery-results.tsv"));
        assertEquals(2, Double.parseDouble(pooled.rows().get(0)[pooled.column("beta")]), 1e-12);
        assertEquals(false, poolResult.details().get("validation_used"));
        write(pool.resolve("manifest.yaml"), Map.of("operation_id", "discovery", "hypothesis", "H", "operation_type", "discovery_meta_analysis", "state", "complete",
            "output_sha256", Map.of("discovery-results.tsv", FollowupSupport.hash(pool.resolve("discovery-results.tsv")))));
        Path validation = root.resolve("validation");
        var compared = new StudyCliExecutor().execute(new StudyOperationExecutor.Context(root, validation,
            Map.of("type", "discovery_replication", "hypothesis", "H", "pooled_discovery", true,
                "roles", Map.of("discovery", List.of("a", "b"), "replication", List.of("v"))),
            study, Map.of(), Map.of("harmonize", upstream, "discovery", pool)));
        assertEquals(StudyOperationExecutor.State.COMPLETE, compared.state());
        var table = DelimitedData.read(validation.resolve("replication.tsv"));
        assertEquals(1, table.rows().size());
        assertEquals(2, Double.parseDouble(table.rows().get(0)[table.column("discovery_beta")]), 1e-12);
        assertEquals("true", table.rows().get(0)[table.column("discovery_selected")]);
    }

    @Test void fiveFindingsAndFlexibleTargetAreAcceptedButSixAndExplicitOverBudgetAreRejected() throws Exception {
        Map<String, Object> evidence = Map.of("operation_type", "association", "state", "complete", "key_findings", Map.of("tested_features", 5));
        List<Object> sections = new ArrayList<>();
        for (int i = 1; i <= 5; i++) sections.add(Map.of("id", "r" + i, "section", "results", "results_role", "association",
            "sources", List.of("H.association"), "text", "Observed finding " + i));
        for (int i = 1; i <= 5; i++) sections.add(Map.of("id", "d" + i, "section", "discussion", "discussion_role", "principal_finding",
            "impact_rank", i, "result_ids", List.of("r" + i), "impact_reason", "Independent replication and meaningful effect.", "text", "Supported interpretation."));
        var result = StudyResultsDiscussion.assess(Map.of("sections", sections), Map.of("H.association", evidence));
        assertEquals(5, result.get("principal_finding_paragraphs"));
        sections.add(Map.of("id", "d6", "section", "discussion", "discussion_role", "principal_finding", "impact_rank", 6,
            "result_ids", List.of("r5"), "impact_reason", "Extra finding.", "text", "Extra interpretation."));
        assertThrows(java.io.IOException.class, () -> StudyResultsDiscussion.assess(Map.of("sections", sections), Map.of("H.association", evidence)));
        Map<String, Object> paragraph = new LinkedHashMap<>(Map.of("section", "results", "text", "word ".repeat(7999)));
        assertEquals(8000, StudyWordBudget.assess(Map.of("sections", List.of(paragraph))).get("main_text_words"));
        paragraph.put("text", "word ".repeat(8000));
        assertEquals(false, StudyPublicEvidence.map(StudyResultsDiscussion.assess(
            Map.of("sections", List.of(paragraph)), Map.of()).get("word_budget")).get("blocks_publication"));
        assertThrows(java.io.IOException.class, () -> StudyResultsDiscussion.assess(Map.of("sections", List.of(paragraph),
            "manuscript_policy", Map.of("max_main_words", 8000)), Map.of()));
        assertEquals(true, StudyWordBudget.assess(Map.of("sections", List.of(paragraph),
            "manuscript_policy", Map.of("max_main_words", 8001))).get("within_limit"));
    }
}
