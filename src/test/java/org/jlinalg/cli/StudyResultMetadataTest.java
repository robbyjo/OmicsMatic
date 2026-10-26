/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyResultMetadataTest {
    @TempDir Path root;
    private DelimitedData table(String header, String row) throws Exception {
        Path path = root.resolve("metadata.tsv"); Files.writeString(path, header + "\n" + row + "\n"); return DelimitedData.readResult(path);
    }
    @Test void schemaAndRuntimeExposeTheSameMetadataContract() throws Exception {
        Object parsed = SimpleJson.parse(Files.readString(Path.of("docs/schemas/study-v1.schema.json")));
        var schema = StudyPublicEvidence.map(parsed);
        var definition = StudyPublicEvidence.map(StudyPublicEvidence.map(schema.get("$defs")).get("resultMetadata"));
        assertEquals(org.jlinalg.study.StudyResultMetadataPolicy.names(), new java.util.LinkedHashSet<>(StudyPublicEvidence.list(StudyPublicEvidence.map(definition.get("propertyNames")).get("enum"))));
    }
    @Test void survivalEventAndCensorCountsStayTypedAndCannotExceedTheAnalysisPopulation()throws Exception{
        var input=table("n\tn_events\tn_censored\tsurvival_strata","10\t7\t3\t2");var metadata=new StudyResultMetadata(input,Map.of());var values=metadata.values(input.rows().get(0));assertEquals("7",values.get(metadata.header().indexOf("n_events")));assertFalse(metadata.header().contains("n_cases"));
        var invalid=table("n\tn_events\tn_censored","10\t7\t4");var bad=new StudyResultMetadata(invalid,Map.of());assertThrows(IOException.class,()->bad.values(invalid.rows().get(0)));
    }
    @Test void retainsTypedCountsDiagnosticsAndExplicitLabelsWithoutUnmappedProse() throws Exception {
        var table = table("N\tMAC\tMAF\tINFO\tEA\tprovider_model\tpatient_note", "1000\t2.5\t.00125\t1.02\tA\tlinear mixed model\tPRIVATE_SENTINEL");
        var metadata = new StudyResultMetadata(table, Map.of("result_metadata", Map.of("effect_allele", "EA", "model", "provider_model")));
        var values = metadata.values(table.rows().get(0)); metadata.retained(values);
        assertEquals("1000", values.get(metadata.header().indexOf("n")));
        assertEquals("2.5", values.get(metadata.header().indexOf("minor_allele_count")));
        assertEquals("1.02", values.get(metadata.header().indexOf("imputation_info")), "The provider's INFO definition is not silently changed");
        assertFalse(values.toString().contains("PRIVATE_SENTINEL")); assertFalse(metadata.header().contains("patient_note"));
        assertEquals("linear mixed model", values.get(metadata.header().indexOf("model")));
        assertEquals(1, ((Map<?, ?>) metadata.audit().get("nonmissing_retained")).get("n"));
    }
    @Test void ambiguousAliasesRequireAnExplicitSourceAndMissingMappingsNeverDisappear() throws Exception {
        var table = table("N\tOBS_CT", "1000\t800");
        assertThrows(IOException.class, () -> new StudyResultMetadata(table, Map.of()));
        var selected = new StudyResultMetadata(table, Map.of("result_metadata", Map.of("n", "OBS_CT")));
        assertEquals(List.of("800"), selected.values(table.rows().get(0)));
        assertThrows(IOException.class, () -> new StudyResultMetadata(table, Map.of("result_metadata", Map.of("n", "absent"))));
        assertThrows(IOException.class, () -> new StudyResultMetadata(table, Map.of("result_metadata", Map.of("participant_id", "N"))));
    }
    @Test void missingFieldsStayUnavailableAndInvalidNumericSemanticsAreRejected() throws Exception {
        var missing = table("N\tMAF\tMAC", "NA\t.\tNaN"); var normalizer = new StudyResultMetadata(missing, Map.of());
        assertEquals(List.of("NA", "NA", "NA"), normalizer.values(missing.rows().get(0)));
        for (String[] example : List.of(new String[]{"N", "-1"}, new String[]{"N", "1.5"}, new String[]{"N", "0"},
                new String[]{"MAF", ".6"}, new String[]{"EAF", "-0.01"}, new String[]{"MAC", "Infinity"}, new String[]{"df", "0"},new String[]{"log_p_value","0.01"})) {
            var bad = table(example[0], example[1]); var metadata = new StudyResultMetadata(bad, Map.of());
            assertThrows(IOException.class, () -> metadata.values(bad.rows().get(0)), example[0] + "=" + example[1]);
        }
        var log=table("log_p_value","-1500");assertEquals(List.of("-1500"),new StudyResultMetadata(log,Map.of()).values(log.rows().get(0)));
    }
    @Test void jointCountsAndIntervalsAreCheckedWithoutInferringMissingCounts() throws Exception {
        for (String row : List.of("100\t60\t50\t-1\t1", "100\t40\t50\t2\t1")) {
            var bad = table("N\tN_CASE\tN_CONTROL\tci_lower\tci_upper", row); var metadata = new StudyResultMetadata(bad, Map.of());
            assertThrows(IOException.class, () -> metadata.values(bad.rows().get(0)));
        }
        var valid = table("N\tN_CASE\tN_CONTROL", "NA\t60\t50"); var metadata = new StudyResultMetadata(valid, Map.of());
        assertEquals("NA", metadata.values(valid.rows().get(0)).get(0));
    }
    @Test void studyImportsPreserveMetadataThroughQcPoolingInventoryAndInputHashes() throws Exception {
        Path project = root.resolve("study"); Files.createDirectory(project);
        for (String cohort : List.of("a", "b")) {
            String n = cohort.equals("a") ? "100" : "200", beta = cohort.equals("a") ? "1" : "2";
            Files.writeString(project.resolve(cohort + ".tsv"), "feature_id\tbeta\tse\tp_value\tN\tMAC\tMAF\tprivate_note\n"
                + "g1\t" + beta + "\t.1\t1e-9\t" + n + "\t20\t.1\tPRIVATE_SENTINEL\n"
                + "g2\t" + beta + "\t.1\t1e-9\t" + (cohort.equals("b") ? "NA" : "90") + "\t19\t.1\tPRIVATE_SENTINEL\n"
                + "bad\t1\t.1\t1e-9\t-1\t20\t.1\tPRIVATE_SENTINEL\n");
        }
        String aHash = FollowupSupport.hash(project.resolve("a.tsv")), bHash = FollowupSupport.hash(project.resolve("b.tsv"));
        Files.writeString(project.resolve("study.yaml"), """
            schema_version: 1
            project: {id: metadata, title: Metadata propagation, description: Preserve source-reported aggregates without inventing unique sample sizes.}
            resources: {network: offline, downloads: deny}
            assistance: {enabled: true, impact: true, provider: browser}
            datasets:
              - {id: a, cohort: A, type: generic, stage: association_results, files: {results: a.tsv}}
              - {id: b, cohort: B, type: generic, stage: association_results, files: {results: b.tsv}}
            hypotheses:
              - id: H1
                description: Synthetic imported association metadata.
                inputs: {discovery: [a], validation: [b]}
                analysis:
                  mode: manual
                  goals: [meta_analysis, impact_assessment]
                  harmonization: {invalid_rows: exclude}
            """);
        var error = new ByteArrayOutputStream();
        assertEquals(0, StudyCli.run(new String[]{"auto", project.resolve("study.yaml").toString(), "--project", project.toString(), "--offline"},
            new PrintStream(new ByteArrayOutputStream()), new PrintStream(error)), error.toString());
        Path normalized, pooled, inventory, exclusions;
        try (var paths = Files.walk(project.resolve("runs"))) { normalized = paths.filter(path -> path.endsWith("standard-results/a.tsv")).findFirst().orElseThrow(); }
        try (var paths = Files.walk(project.resolve("runs"))) { pooled = paths.filter(path -> path.getFileName().toString().equals("meta-analysis.tsv")).findFirst().orElseThrow(); }
        try (var paths = Files.walk(project.resolve("runs"))) { inventory = paths.filter(path -> path.getFileName().toString().equals("finding-inventory.jsonl")).findFirst().orElseThrow(); }
        try (var paths = Files.walk(project.resolve("runs"))) { exclusions = paths.filter(path -> path.getFileName().toString().equals("a.tsv.exclusions.tsv")).findFirst().orElseThrow(); }
        var result = DelimitedData.readResult(normalized); assertEquals(2, result.rows().size());
        assertEquals("100", result.rows().get(0)[result.column("n")]); assertTrue(result.header().contains("minor_allele_count"));
        assertFalse(result.header().contains("private_note")); assertTrue(Files.readString(exclusions).contains("bad\texcluded_invalid_row"));
        var meta = DelimitedData.readResult(pooled); var first = meta.rows().get(0); var second = meta.rows().get(1);
        assertEquals(1.5, Double.parseDouble(first[meta.column("beta")]), 1e-12);
        assertEquals(300, Double.parseDouble(first[meta.column("reported_n_sum")]));
        assertEquals("0", first[meta.column("reported_n_missing_cohorts")]);
        assertEquals(90, Double.parseDouble(second[meta.column("reported_n_sum")]));
        assertEquals("1", second[meta.column("reported_n_missing_cohorts")]); assertFalse(meta.header().contains("n"));
        String dossiers = Files.readString(inventory); assertTrue(dossiers.contains("\"n\":\"100\"")); assertFalse(dossiers.contains("PRIVATE_SENTINEL"));
        assertEquals(aHash, FollowupSupport.hash(project.resolve("a.tsv"))); assertEquals(bHash, FollowupSupport.hash(project.resolve("b.tsv")));
        org.jlinalg.study.StudyProject.verify(project);
    }
}
