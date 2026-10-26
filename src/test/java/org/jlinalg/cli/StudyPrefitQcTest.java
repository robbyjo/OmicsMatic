/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyPrefitQcTest {
    @TempDir Path root;

    @Test void descriptiveWelchProbabilitiesRemainFiniteAndUnitInvariantAtExtremeScales()throws Exception{
        for(double scale:new double[]{1e100,1e-100}){
            root=Files.createTempDirectory(root,"welch-scale-");StringBuilder matrix=new StringBuilder("feature_id"),values=new StringBuilder("\nG"),phenotype=new StringBuilder("id\ty\tSex\n");
            for(int i=0;i<8;i++){matrix.append("\tS"+i);values.append('\t').append(i);phenotype.append("S"+i+"\t"+(scale*i)+"\t"+(i%2)+"\n");}
            Files.writeString(root.resolve("matrix.tsv"),matrix.append(values).append('\n'));Files.writeString(root.resolve("phenotype.tsv"),phenotype);
            var r=run("expression",Map.of(),Map.of("covariates",List.of("Sex")));assertEquals(StudyOperationExecutor.State.COMPLETE,r.state());var comparison=map(list(r.details().get("subgroup_comparisons")).get(0));
            assertEquals(.6036450565101361,((Number)comparison.get("p_value")).doubleValue(),1e-12);assertTrue(Double.isFinite(((Number)comparison.get("adjusted_p")).doubleValue()));
            assertFalse(Files.readString(root.resolve("qc/prefit-qc.yaml")).contains(".NaN"));
        }
    }

    @Test void actualAnalysisSelectionAndSubgroupPlotsAreProducedWithoutDeletingOutliers() throws Exception {
        StringBuilder matrix = new StringBuilder("feature_id"), values = new StringBuilder("\nG");
        StringBuilder phenotype = new StringBuilder("id\ty\tSex\n");
        for (int i = 0; i < 10; i++) {
            matrix.append("\tS").append(i); values.append('\t').append(i);
            phenotype.append("S").append(i).append('\t').append(i == 1 ? "NA" : Integer.toString(i == 9 ? 10000 : i))
                .append('\t').append(i == 2 ? "NA" : Integer.toString(i % 2)).append('\n');
        }
        phenotype.append("OUTSIDE\t1\t0\n");
        Files.writeString(root.resolve("matrix.tsv"), matrix.append(values).append('\n'));
        Files.writeString(root.resolve("phenotype.tsv"), phenotype);
        var result = run("expression", Map.of(), Map.of("covariates", List.of("Sex")));
        assertEquals(StudyOperationExecutor.State.COMPLETE, result.state());
        assertEquals(8, result.details().get("analysis_samples"));
        assertEquals(2, map(result.details().get("excluded_by_reason")).get("missing_outcome_or_covariate"));
        assertEquals(1, map(result.details().get("excluded_by_reason")).get("not_in_matrix"));
        var selected = DelimitedData.read(root.resolve("qc/analysis-phenotype.tsv"));
        assertTrue(selected.rows().stream().anyMatch(row -> row[0].equals("S9") && row[1].equals("10000")));
        assertFalse(selected.rows().stream().anyMatch(row -> List.of("S1", "S2", "OUTSIDE").contains(row[0])));
        assertTrue(Files.exists(root.resolve("qc/distribution-0.png")));
        assertTrue(Files.exists(root.resolve("qc/distribution-0.pdf")));
        assertEquals(1, list(result.details().get("subgroup_comparisons")).size());
        assertTrue(map(list(result.details().get("subgroup_comparisons")).get(0)).containsKey("adjusted_p"));
        assertEquals(11, DelimitedData.read(root.resolve("phenotype.tsv")).rows().size());
    }

    @Test void duplicateParticipantIdentifiersBlockBeforeFitting() throws Exception {
        Files.writeString(root.resolve("matrix.tsv"), "feature_id\tS1\tS2\nG\t1\t2\n");
        Files.writeString(root.resolve("phenotype.tsv"), "id\ty\nS1\t1\nS1\t2\n");
        assertThrows(java.io.IOException.class, () -> run("expression", Map.of(), Map.of()));
        assertFalse(Files.exists(root.resolve("qc/analysis-phenotype.tsv")));
    }

    @Test void genotypeCallRateUsesTheSelectedPopulationAndKeepsRareVariantsInSource() throws Exception {
        Files.writeString(root.resolve("matrix.tsv"), "variant_id\tS1\tS2\tS3\tS4\nrare\tNA\t1\t0\t0\ncommon\tNA\t0\t1\t2\n");
        Files.writeString(root.resolve("phenotype.tsv"), "id\ty\nS1\t1\nS2\t2\nS3\t3\nS4\t4\n");
        var result = run("gwas", Map.of("minimum_sample_call_rate", .98), Map.of());
        assertEquals(StudyOperationExecutor.State.COMPLETE, result.state());
        assertEquals(3, result.details().get("analysis_samples"));
        assertEquals(1, map(result.details().get("excluded_by_reason")).get("sample_call_rate"));
        assertEquals(2L, result.details().get("examined_genotype_variants"));
        assertTrue(Files.readString(root.resolve("matrix.tsv")).contains("rare"));
    }

    @Test void explicitThresholdSubgroupsKeepBoundaryAndCircularTestsDescriptive() throws Exception {
        Files.writeString(root.resolve("matrix.tsv"), "feature_id\tS1\tS2\tS3\tS4\nG\t1\t2\t3\t4\n");
        Files.writeString(root.resolve("phenotype.tsv"), "id\ty\nS1\t29\nS2\t30\nS3\t31\nS4\t32\n");
        var result = run("expression", Map.of(), Map.of("analysis", Map.of("subgroups", List.of(
            Map.of("id", "obesity", "column", "y", "cutoff", 30, "comparison", "greater_than")))));
        var comparison = map(list(result.details().get("subgroup_comparisons")).get(0));
        assertEquals(List.of(2, 2), comparison.get("group_N"));
        assertEquals(List.of(29.5, 31.5), comparison.get("group_means"));
        assertEquals("NA", comparison.get("p_value"));
        assertEquals(false, comparison.get("sensitivity_candidate"));
        assertTrue(comparison.get("method").toString().contains("groups are defined by the variable"));
        assertTrue(Files.exists(root.resolve("qc/distribution-0-0-1.png")));
    }

    private StudyOperationExecutor.Result run(String type, Map<String, Object> options, Map<String, Object> extraHypothesis) throws Exception {
        Map<String, Object> dataset = Map.of("id", "a", "type", type, "stage", "processed_matrix", "files",
            Map.of("matrix", "matrix.tsv", "phenotype", "phenotype.tsv"), "columns", Map.of("sample_id", "id"));
        Map<String, Object> hypothesis = new java.util.LinkedHashMap<>(Map.of("id", "H", "outcome", "y", "analysis", Map.of("association", options)));
        hypothesis.putAll(extraHypothesis);
        return new StudyCliExecutor().execute(new StudyOperationExecutor.Context(root, root.resolve("qc"),
            Map.of("type", "participant_qc", "dataset_id", "a", "datasets", List.of("a"), "hypothesis", "H"),
            Map.of("datasets", List.of(dataset), "hypotheses", List.of(hypothesis)), Map.of(), Map.of()));
    }
}
