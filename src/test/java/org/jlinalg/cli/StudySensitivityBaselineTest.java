/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySensitivityBaselineTest {
    @TempDir Path project;
    private static final String PRIMARY = "feature_id\tbeta\tse\tp_value\tn_cohorts\ti_squared\n"
        + "g1\t1.644853626951\t1\t.1\t3\t0\ng2\t1.281551565545\t1\t.2\t3\t60\ng3\t1.036433389494\t1\t.3\t3\t90\n";
    private static final String DISCOVERY = "feature_id\tp_value\ti_squared\n"
        + "discovery-only\t.000001\t0\n";

    private Path producer(String name, String type, String state,
            String filename, String table) throws Exception {
        Path folder = Files.createDirectory(project.resolve(name));
        Files.writeString(folder.resolve(filename), table);
        StudyPublicEvidence.write(folder.resolve("manifest.yaml"), Map.of(
            "operation_id", name, "hypothesis", "H", "operation_type", type, "state", state, "output_sha256",
            Map.of(filename, FollowupSupport.hash(folder.resolve(filename)))));
        return folder;
    }

    private Context context(String name, Map<String, Path> supplied,
            boolean omissions) throws Exception {
        Path inputs = project.resolve("inputs");
        Files.createDirectories(inputs.resolve("standard-results"));
        var datasets = new ArrayList<Object>();
        for (String id : List.of("a", "b", "c")) {
            Files.writeString(inputs.resolve("standard-results/" + id + ".tsv"),
                "feature_id\tbeta\tse\tp_value\ng1\t.1\t.1\t.3\n");
            datasets.add(Map.of("id", id, "cohort", "cohort-" + id,
                "type", "generic", "stage", "association_results"));
        }
        StudyTestSources.seal(inputs, "validate_harmonize", "inputs", "H");
        var dependencies = new LinkedHashMap<>(supplied);
        dependencies.put("inputs", inputs);
        return new Context(project, Files.createDirectory(project.resolve(name)),
            Map.of("type", "sensitivity_analysis", "hypothesis", "H",
                "datasets", List.of("a", "b", "c"), "configuration",
                Map.of("automatic_leave_one_cohort_out", omissions)),
            Map.of("datasets", datasets, "hypotheses", List.of(
                Map.of("id", "H", "analysis", Map.of()))), Map.of(), dependencies);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(Object value) {
        return (Map<String, Object>) value;
    }

    @Test void allCohortBaselineIsStableDespiteOtherPoolsAndDependencyOrder() throws Exception {
        Path primary = producer("all", "meta_analysis", "complete", "meta-analysis.tsv", PRIMARY);
        Path discovery = producer("discovery", "discovery_meta_analysis", "complete", "meta-analysis.tsv", DISCOVERY);
        Path supplementary = producer("supplementary", "sensitivity_analysis", "complete", "meta-analysis.tsv", DISCOVERY);
        var entries = new ArrayList<>(List.of(Map.entry("all", primary),
            Map.entry("discovery", discovery), Map.entry("supplementary", supplementary)));
        String original = FollowupSupport.hash(primary.resolve("meta-analysis.tsv"));
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt == 1) Collections.reverse(entries);
            var dependencies = new LinkedHashMap<String, Path>();
            entries.forEach(entry -> dependencies.put(entry.getKey(), entry.getValue()));
            Context context = context("order-" + attempt, dependencies, false);
            var result = new StudyCliExecutor().execute(context);
            assertEquals(State.COMPLETE, result.state());
            var metrics = mapping(result.details().get("primary_metrics"));
            assertEquals(3, metrics.get("meta_tested_features"));
            assertEquals(0, metrics.get("meta_BH_FDR_0_05_features"));
            assertEquals(50.0, metrics.get("mean_i_squared"));
            assertEquals(2.0 / 3.0, metrics.get("high_i_squared_fraction"));
            var identity = mapping(mapping(result.details().get("primary_artifacts")).get("meta_analysis"));
            assertEquals("all/meta-analysis.tsv", identity.get("artifact"));
            assertEquals(original, identity.get("sha256"));
            var decision = StudyYaml.loadArtifact(context.outputDirectory().resolve("decision.yaml"));
            assertEquals(result.details().get("primary_artifacts"), decision.get("primary_artifacts"));
        }
        assertEquals(original, FollowupSupport.hash(primary.resolve("meta-analysis.tsv")));
    }

    @Test void discoveryOnlyPoolCannotAuthorizeCohortOmissions() throws Exception {
        Path discovery = producer("discovery", "discovery_meta_analysis", "complete", "meta-analysis.tsv", DISCOVERY);
        Context context = context("no-primary", Map.of("discovery", discovery), true);
        var result = new StudyCliExecutor().execute(context);
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        assertTrue(mapping(result.details().get("primary_artifacts")).isEmpty());
        assertEquals(0, mapping(result.details().get("primary_metrics")).get("meta_tested_features"));
        assertFalse(Files.exists(context.outputDirectory().resolve("leave_one_cohort_out")));
    }

    @Test void incompletePrimaryCannotAuthorizeCohortOmissions() throws Exception {
        Path primary = producer("all", "meta_analysis", "waiting_for_input", "meta-analysis.tsv", PRIMARY);
        Context context = context("incomplete", Map.of("all", primary), true);
        assertEquals(State.WAITING_FOR_INPUT, new StudyCliExecutor().execute(context).state());
        assertFalse(Files.exists(context.outputDirectory().resolve("leave_one_cohort_out")));
    }

    @Test void changedPrimaryFailsBeforeAnySensitivityFit() throws Exception {
        Path primary = producer("all", "meta_analysis", "complete", "meta-analysis.tsv", PRIMARY);
        Files.writeString(primary.resolve("meta-analysis.tsv"), DISCOVERY);
        Context context = context("changed", Map.of("all", primary), true);
        var operation = new LinkedHashMap<>(context.operation());
        operation.put("configuration", Map.of("automatic_leave_one_cohort_out", true,
            "automatic_unfiltered_effect_qc", true));
        Context unfiltered = new Context(context.project(), context.outputDirectory(),
            operation, context.study(), context.plan(), context.dependencyDirectories());
        IOException error = assertThrows(IOException.class, () -> new StudyCliExecutor().execute(unfiltered));
        assertTrue(error.getMessage().contains("Missing or changed finding evidence"));
        assertFalse(Files.exists(context.outputDirectory().resolve("leave_one_cohort_out")));
        assertFalse(Files.exists(context.outputDirectory().resolve("effect-qc-unfiltered")));
    }

    @Test void ambiguousAllCohortProducersAreRejected() throws Exception {
        Path first = producer("first", "meta_analysis", "complete", "meta-analysis.tsv", PRIMARY);
        Path second = producer("second", "meta_analysis", "complete", "meta-analysis.tsv", DISCOVERY);
        Context context = context("ambiguous", Map.of("first", first, "second", second), true);
        IOException error = assertThrows(IOException.class, () -> new StudyCliExecutor().execute(context));
        assertTrue(error.getMessage().contains("Ambiguous primary meta_analysis"));
        assertFalse(Files.exists(context.outputDirectory().resolve("leave_one_cohort_out")));
    }

    @Test void replicationBaselineRequiresItsOwnCompleteProducer() throws Exception {
        String header = "feature_id\tdiscovery_selected\treplicated\n";
        Path validation = producer("validation", "discovery_replication", "complete", "replication.tsv", header + "g1\ttrue\tfalse\n");
        Path decoy = producer("decoy", "sensitivity_analysis", "complete", "replication.tsv", header + "g1\ttrue\ttrue\n");
        Context context = context("validation-selected", Map.of("validation", validation, "decoy", decoy), false);
        var result = new StudyCliExecutor().execute(context);
        var metrics = mapping(result.details().get("primary_metrics"));
        assertEquals(1, metrics.get("discovery_selected_pairwise_tests"));
        assertEquals(0, metrics.get("replicated_pairwise_tests"));
        var identity = mapping(mapping(result.details().get("primary_artifacts")).get("discovery_replication"));
        assertEquals("validation/replication.tsv", identity.get("artifact"));
        assertEquals(FollowupSupport.hash(validation.resolve("replication.tsv")), identity.get("sha256"));
    }

    @Test void unlistedTableCannotServeAsPrimary() throws Exception {
        Path primary = producer("all", "meta_analysis", "complete", "meta-analysis.tsv", PRIMARY);
        StudyPublicEvidence.write(primary.resolve("manifest.yaml"), Map.of(
            "operation_id", "all", "hypothesis", "H", "operation_type", "meta_analysis", "state", "complete", "output_sha256", Map.of()));
        Context context = context("unlisted", Map.of("all", primary), true);
        assertEquals(State.WAITING_FOR_INPUT, new StudyCliExecutor().execute(context).state());
        assertFalse(Files.exists(context.outputDirectory().resolve("leave_one_cohort_out")));
    }
}
