/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyFrozenDiscoveryTest {
    @TempDir Path root;

    @Test void selectedOnlyDiscoveryRetainsFullFamilyQDuringValidation() throws Exception {
        var c = fixture("selected", "selected_only", 400000, "BH",
            "feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.000001\t.4\ng2\t.4\t.1\t.000002\t.02\n");
        var result = new StudyCliExecutor().execute(c);
        assertEquals(State.COMPLETE, result.state(), result.summary());
        var table = DelimitedData.readResult(c.outputDirectory().resolve("replication.tsv"));
        var first = table.rows().get(0);
        assertEquals(.4, Double.parseDouble(first[table.column("discovery_adjusted_p")]), 0);
        assertEquals("false", first[table.column("discovery_selected")]);
        assertEquals("false", first[table.column("replicated")]);
        var second = table.rows().get(1);
        assertEquals(.02, Double.parseDouble(second[table.column("discovery_adjusted_p")]), 0);
        assertEquals("true", second[table.column("discovery_selected")]);
        assertEquals("true", second[table.column("replicated")]);
        assertEquals(.002, Double.parseDouble(second[table.column("replication_adjusted_p")]), 0,
            "The validation family must use only the source-selected discovery subset");
        var receipt = StudyPublicEvidence.map(result.details().get("frozen_discovery_selection"));
        assertEquals("preserved_source_full_family", receipt.get("adjusted_values_origin"));
        assertEquals(400000, receipt.get("testing_denominator"));
        assertEquals(false, receipt.get("official_primary"));
        assertEquals("selected_only", StudyPublicEvidence.map(receipt.get("source_testing_family")).get("coverage"));
        assertTrue(Files.isRegularFile(c.outputDirectory().resolve("primary-result-selection.yaml")));
        assertEquals("feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.000001\t.4\ng2\t.4\t.1\t.000002\t.02\n",
            Files.readString(root.resolve("selected.tsv")));
    }

    @Test void completeSuppliedByFamilyOverridesValidationBhDefaults() throws Exception {
        var c = fixture("by", "complete", 2, "BY",
            "feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.02\ng2\t.4\t.1\t.8\n");
        var result = new StudyCliExecutor().execute(c);
        assertEquals(State.COMPLETE, result.state(), result.summary());
        var table = DelimitedData.readResult(c.outputDirectory().resolve("replication.tsv"));
        assertEquals(.06, Double.parseDouble(table.rows().get(0)[table.column("discovery_adjusted_p")]), 1e-14);
        assertTrue(table.rows().stream().allMatch(row -> row[table.column("discovery_selected")].equals("false")));
        assertEquals("BY", StudyPublicEvidence.map(result.details().get("frozen_discovery_selection")).get("adjustment"));
    }

    @Test void sourceAdjustedValuesCannotChangeAfterSealing() throws Exception {
        var c = fixture("tamper", "selected_only", 400000, "BH",
            "feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.000001\t.4\n");
        Path canonical = c.dependencyDirectories().get("pool").resolve("discovery-results.tsv");
        Files.writeString(canonical, Files.readString(canonical) + "changed\n");
        assertThrows(java.io.IOException.class, () -> new StudyCliExecutor().execute(c));
        assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")));
    }

    @Test void sourceFamilyMetadataCannotChangeAfterSealing() throws Exception {
        var c = fixture("metadata", "selected_only", 400000, "BH",
            "feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.000001\t.4\n");
        Path metadata = c.dependencyDirectories().get("pool").resolve("supplied-analysis.yaml");
        Files.writeString(metadata, Files.readString(metadata).replace("400000", "1"));
        assertThrows(java.io.IOException.class, () -> new StudyCliExecutor().execute(c));
        assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")));
    }

    @Test void nativeDiscoveryStillComputesItsCompleteObservedFamily() throws Exception {
        var c = fixture("native", "complete", 2, "BH",
            "feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\ng2\t.4\t.1\t.8\n");
        Path inputs = Files.createDirectory(root.resolve("native-inputs"));
        Files.createDirectory(inputs.resolve("standard-results"));
        for (String id : List.of("a", "b")) Files.writeString(inputs.resolve("standard-results/" + id + ".tsv"),
            "feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\ng2\t0\t.1\t1\n");
        StudyTestSources.seal(inputs, "validate_harmonize", "native-inputs", "H");
        Path pool = Files.createDirectory(root.resolve("actual-native-pool"));
        var pooled = new StudyCliExecutor().execute(new Context(root, pool,
            Map.of("type", "discovery_meta_analysis", "hypothesis", "H", "datasets", List.of("a", "b")),
            c.study(), Map.of(), Map.of("inputs", inputs)));
        assertEquals(State.COMPLETE, pooled.state(), pooled.summary());
        StudyTestSources.seal(pool, "discovery_meta_analysis", "native-pool", "H");
        c = new Context(root, c.outputDirectory(), c.operation(), c.study(), c.plan(),
            Map.of("pool", pool, "validation", c.dependencyDirectories().get("validation")));
        var result = new StudyCliExecutor().execute(c);
        assertEquals(State.COMPLETE, result.state(), result.summary());
        var table = DelimitedData.readResult(c.outputDirectory().resolve("replication.tsv"));
        var source = DelimitedData.readResult(pool.resolve("discovery-results.tsv"));
        assertEquals(2 * Double.parseDouble(source.rows().get(0)[source.column("p_value")]),
            Double.parseDouble(table.rows().get(0)[table.column("discovery_adjusted_p")]), 0);
        var receipt = StudyPublicEvidence.map(result.details().get("frozen_discovery_selection"));
        assertEquals("computed_from_complete_observed_scoped_input", receipt.get("adjusted_values_origin"));
        assertEquals(2, receipt.get("testing_denominator"));
    }

    @Test void incompleteDiscoveryCannotFallBackToValidationSelection() throws Exception {
        var c = fixture("incomplete", "selected_only", 400000, "BH",
            "feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.000001\t.4\n");
        Path manifest = c.dependencyDirectories().get("pool").resolve("manifest.yaml");
        var metadata = new LinkedHashMap<>(StudyYaml.loadArtifact(manifest));
        metadata.put("state", "waiting_for_input");
        StudyPublicEvidence.write(manifest, metadata);
        var result = new StudyCliExecutor().execute(c);
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")));
    }

    @Test void suppliedDuplicateFeatureContrastsAreRejectedBeforeValidation() {
        assertThrows(java.io.IOException.class, () -> fixture("contrasts", "selected_only", 400000, "BH",
            "feature_id\tterm\tbeta\tse\tp_value\tadjusted_p\n"
            + "g1\tpooled\t.4\t.1\t.000001\t.4\ng2\tpooled\t.4\t.1\t.000002\t.02\n"
            + "g1\tother\t10\t.1\t.000000001\t.00001\n"));
    }

    private Context fixture(String id, String coverage, int count, String adjustment, String contents) throws Exception {
        Files.writeString(root.resolve(id + ".tsv"), contents);
        var definition = new LinkedHashMap<String,Object>();
        definition.put("id", "discovery"); definition.put("kind", "discovery_meta_analysis");
        definition.put("files", Map.of("results", id + ".tsv"));
        definition.put("scope", Map.of("datasets", List.of("a", "b"), "phenotype", "synthetic"));
        definition.put("method", Map.of("name", "Synthetic external discovery pool"));
        definition.put("family", Map.of("id", "source-family", "coverage", coverage, "tested_count", count,
            "adjustment", adjustment, "adjusted_p_from_full_family", contents.contains("adjusted_p")));
        Map<String,Object> study = Map.of("cohort_relationships",List.of(Map.of("between",List.of("A","B","V"),"type","independent")),"datasets", List.of(Map.of("id","a","cohort","A","type","generic","files",Map.of()),Map.of("id","b","cohort","B","type","generic","files",Map.of()),Map.of("id","v","cohort","V","type","generic","files",Map.of())), "hypotheses", List.of(Map.of("id", "H", "outcome","synthetic","analysis",
            Map.of("supplied_results", List.of(definition), "replication", Map.of("adjustment_scope", "discovery_subset")))));
        Path imported = Files.createDirectory(root.resolve(id + "-pool"));
        var importContext = new Context(root, imported, Map.of("type", "discovery_meta_analysis", "hypothesis", "H",
            "supplied_result_id", "discovery"), study, Map.of(), Map.of());
        var importedResult = new StudyCliExecutor().execute(importContext);
        assertEquals(State.COMPLETE, importedResult.state(), importedResult.summary());
        StudyTestSources.seal(imported, "discovery_meta_analysis", "pool", "H");
        Path validation = Files.createDirectory(root.resolve(id + "-validation"));
        Files.createDirectory(validation.resolve("standard-results"));
        Files.writeString(validation.resolve("standard-results/v.tsv"),
            "feature_id\tbeta\tse\tp_value\ng1\t.3\t.1\t.001\ng2\t.3\t.1\t.002\n");
        StudyTestSources.seal(validation, "validate_harmonize", "validation", "H");
        return new Context(root, Files.createDirectory(root.resolve(id + "-comparison")),
            Map.of("type", "discovery_replication", "hypothesis", "H", "pooled_discovery", true,
                "roles", Map.of("discovery", List.of("a", "b"), "replication", List.of("v"))),
            study, Map.of(), Map.of("pool", imported, "validation", validation));
    }
}
