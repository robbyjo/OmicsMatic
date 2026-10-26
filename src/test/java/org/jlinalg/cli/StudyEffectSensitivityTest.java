/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyEffectSensitivityTest {
    @TempDir Path project;

    private void cli(String... args) {
        var output = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        assertEquals(0, JLinAlgCli.run(args, new PrintStream(output), new PrintStream(error)),
            () -> output + "\n" + error);
    }
    private Path operation(String type) throws Exception {
        try (var paths = Files.walk(project.resolve("runs"))) {
            return paths.filter(Files::isDirectory).filter(path -> path.getFileName().toString().endsWith("-" + type)).findFirst().orElseThrow();
        }
    }
    private static List<String> ids(Path path) throws Exception {
        var table = DelimitedData.readResult(path);
        return table.rows().stream().map(row -> row[table.column("feature_id")]).toList();
    }

    @Test void automaticBranchRetainsFullFamiliesAndDiscoveryPoolWithoutChangingPrimary() throws Exception {
        Map<Path, String> originals = new LinkedHashMap<>();
        for (String id : List.of("a", "b", "c", "v")) {
            StringBuilder table = new StringBuilder("feature_id\tbeta\tse\tp_value\n");
            for (int i = 0; i < 30; i++) table.append("g").append(i).append('\t').append((i - 15) * .01).append("\t.1\t.5\n");
            table.append("extreme\t").append(id.equals("a") ? "600000\t200000" : ".1\t.1").append("\t.0027\n");
            if (!id.equals("v")) table.append("discoveryOnly\t.4\t.1\t.0001\n");
            table.append("invalid\t.4\t-1\t.001\n");
            Path input = project.resolve(id + ".tsv"); Files.writeString(input, table);
            originals.put(input, FollowupSupport.hash(input));
        }
        Path study = project.resolve("study.yaml");
        Files.writeString(study, """
            schema_version: 1
            project: {id: unfiltered, title: Unfiltered sensitivity, description: Preserve primary and both QC families.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, cohort: ca, type: generic, stage: association_results, files: {results: a.tsv}}
              - {id: b, cohort: cb, type: generic, stage: association_results, files: {results: b.tsv}}
              - {id: c, cohort: cc, type: generic, stage: association_results, files: {results: c.tsv}}
              - {id: v, cohort: cv, type: generic, stage: association_results, files: {results: v.tsv}}
            hypotheses:
              - id: H
                description: Pooled discovery with independent validation and effect QC sensitivity.
                inputs: {discovery: [a,b,c], replication: [v]}
                analysis:
                  mode: manual
                  goals: [meta_analysis, discovery_replication]
                  harmonization: {invalid_rows: exclude, common_universe: intersection}
            """);
        cli("study", "auto", study.toString(), "--offline");
        Path sensitivity = operation("sensitivity_analysis").resolve("effect-qc-unfiltered");
        Path primary = operation("meta_analysis").resolve("meta-analysis.tsv");
        assertFalse(ids(primary).contains("extreme"));
        assertTrue(ids(sensitivity.resolve("meta/meta-analysis.tsv")).contains("extreme"));
        assertFalse(ids(sensitivity.resolve("meta/meta-analysis.tsv")).contains("invalid"));
        assertTrue(ids(operation("discovery_meta_analysis").resolve("discovery-results.tsv")).contains("discoveryOnly"),
            "Validation coverage must not define the discovery-only testing family");
        assertTrue(ids(sensitivity.resolve("discovery/discovery-results.tsv")).contains("discoveryOnly"));
        String unfilteredReplication = Files.readString(sensitivity.resolve("replication/replication.tsv"));
        assertTrue(unfilteredReplication.contains("__pooled_discovery__"));
        assertTrue(Files.isRegularFile(sensitivity.resolve("effect-comparison/effect-scatterplots/01-a-vs-b.pdf")));
        try (var paths = Files.walk(operation("sensitivity_analysis").resolve("leave_one_cohort_out"))) {
            var replications = paths.filter(path -> path.getFileName().toString().equals("replication.tsv")).toList();
            assertEquals(3, replications.size());
            for (Path replication : replications) assertTrue(Files.readString(replication).contains("__pooled_discovery__"),
                "Each omission must pool the remaining discovery cohorts before validation");
        }
        var compared = DelimitedData.readResult(sensitivity.resolve("feature-sensitivity.tsv"));
        String[] effect = compared.rows().stream().filter(row -> row[0].equals("all_cohort_meta") && row[1].equals("extreme")).findFirst().orElseThrow();
        assertEquals("false", effect[compared.column("filtered_present")]);
        assertEquals("true", effect[compared.column("unfiltered_present")]);
        var decision = StudyYaml.loadArtifact(sensitivity.resolve("decision.yaml"));
        assertEquals(true, decision.get("primary_unchanged"));
        assertEquals(true, decision.get("validation_repeated"));
        var cohortDecision = StudyYaml.loadArtifact(operation("sensitivity_analysis").resolve("decision.yaml"));
        var primaryMetrics = StudyPublicEvidence.map(cohortDecision.get("primary_metrics"));
        assertEquals(30, primaryMetrics.get("meta_tested_features"),
            "The discovery-only pool has 31 features and cannot define the all-cohort baseline");
        var primaryArtifact = StudyPublicEvidence.map(StudyPublicEvidence.map(
            cohortDecision.get("primary_artifacts")).get("meta_analysis"));
        assertEquals(FollowupSupport.hash(primary), primaryArtifact.get("sha256"));
        originals.forEach((path, hash) -> { try { assertEquals(hash, FollowupSupport.hash(path)); } catch (Exception error) { throw new RuntimeException(error); } });
        cli("study", "verify", "--project", project.toString());
        String first = FollowupSupport.hash(primary);
        cli("study", "auto", study.toString(), "--offline");
        assertEquals(first, FollowupSupport.hash(primary));
        cli("study", "verify", "--project", project.toString());
    }

    @Test void entirelyFilteredPrimaryIsRetainedAndUnfilteredResultsStaySupplementary() throws Exception {
        Files.writeString(project.resolve("a.tsv"), "feature_id\tbeta\tse\tp_value\na\t2\t.1\t.001\nb\t3\t.1\t.001\n");
        Path study = project.resolve("study.yaml");
        Files.writeString(study, """
            schema_version: 1
            project: {id: empty-primary, title: Empty primary, description: Do not replace an entirely excluded primary family.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, cohort: ca, type: generic, stage: association_results, files: {results: a.tsv}}
            hypotheses:
              - id: H
                description: Retain both dispositions.
                analysis:
                  mode: manual
                  goals: [meta_analysis]
                  harmonization: {common_universe: intersection, effect_outliers: {max_abs_effect: 1}}
            """);
        cli("study", "auto", study.toString(), "--offline");
        assertTrue(ids(operation("meta_analysis").resolve("meta-analysis.tsv")).isEmpty());
        assertEquals(2, ids(operation("sensitivity_analysis").resolve("effect-qc-unfiltered/meta/meta-analysis.tsv")).size());
        var manifest = StudyYaml.loadArtifact(operation("meta_analysis").resolve("manifest.yaml"));
        assertEquals("not_estimable", StudyPublicEvidence.map(manifest.get("details")).get("evidence_disposition"));
        cli("study", "verify", "--project", project.toString());
    }

    @Test void missingOrChangedSnapshotsCannotSilentlyDropCohorts() throws Exception {
        Path dependency = project.resolve("upstream"); Files.createDirectories(dependency.resolve("pre-effect-qc-results"));
        Path table = dependency.resolve("pre-effect-qc-results/a.tsv");
        Files.writeString(table, "feature_id\tbeta\tse\tp_value\ng\t1\t.1\t.001\n");
        StudyPublicEvidence.write(dependency.resolve("manifest.yaml"), Map.of("operation_id", "upstream", "hypothesis", "H", "operation_type", "validate_harmonize", "state", "complete",
            "output_sha256", Map.of("pre-effect-qc-results/a.tsv", FollowupSupport.hash(table))));
        Context context = new Context(project, project.resolve("missing"), Map.of("hypothesis", "H", "datasets", List.of("a", "b")),
            Map.of("hypotheses", List.of(Map.of("id", "H", "analysis", Map.of()))), Map.of(), Map.of("upstream", dependency));
        assertEquals(State.WAITING_FOR_INPUT, new StudyEffectSensitivity().execute(context, new StudyCliExecutor(), Map.of("a", table)).state());
        Files.writeString(table, "feature_id\tbeta\tse\tp_value\ng\t2\t.1\t.001\n");
        assertThrows(java.io.IOException.class, () -> new StudyEffectSensitivity().execute(context, new StudyCliExecutor(), Map.of("a", table)));
    }
    @Test void byCorrectionReachesReplicationAndUnfilteredSensitivityWithoutBhSubstitution()throws Exception {
        for(String id:List.of("a","b"))Files.writeString(project.resolve(id+".tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.1\t.1\t.01\ng2\t.2\t.1\t.04\ng3\t.3\t.1\t.2\n");Path study=project.resolve("by.yaml");Files.writeString(study,"""
            schema_version: 1
            project: {id: by-family, title: BY families, description: Preserve BY in replication and unfiltered sensitivity.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, cohort: ca, type: generic, stage: association_results, files: {results: a.tsv}}
              - {id: b, cohort: cb, type: generic, stage: association_results, files: {results: b.tsv}}
            hypotheses:
              - id: H
                description: No silent BH substitution.
                inputs: {discovery: [a], replication: [b]}
                analysis:
                  mode: manual
                  goals: [discovery_replication]
                  multiple_testing: {method: BY, alpha: 0.05}
            """);cli("study","auto",study.toString(),"--offline");var replication=DelimitedData.readResult(operation("discovery_replication").resolve("replication.tsv"));var row=replication.rows().stream().filter(r->r[replication.column("feature_id")].equals("g1")).findFirst().orElseThrow();assertEquals(.055,Double.parseDouble(row[replication.column("discovery_adjusted_p")]),1e-14);assertEquals("false",row[replication.column("discovery_selected")]);var sensitivity=operation("sensitivity_analysis").resolve("effect-qc-unfiltered");var decision=StudyYaml.loadArtifact(sensitivity.resolve("decision.yaml"));assertEquals("BY",decision.get("adjustment"));var comparison=DelimitedData.readResult(sensitivity.resolve("feature-sensitivity.tsv"));for(String[] value:comparison.rows())if(value[comparison.column("feature_id")].equals("g1"))assertEquals(.055,Double.parseDouble(value[comparison.column("filtered_adjusted_p")]),1e-14);cli("study","verify","--project",project.toString());
    }
}
