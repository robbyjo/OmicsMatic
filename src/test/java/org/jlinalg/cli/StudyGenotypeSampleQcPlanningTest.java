/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGenotypeSampleQcPlanningTest {
    @TempDir Path root;
    private org.jlinalg.study.StudyContract resolve(String policy,String extra)throws Exception {
        Path path=root.resolve("study.yaml");Files.writeString(path,"""
            schema_version: 1
            project: {id: qc-plan, title: Synthetic QC plan, description: No scientific evidence.}
            datasets:
              - {id: a, type: gwas, stage: processed_matrix, files: {matrix: main.vcf, phenotype: phenotype.tsv, qc_genotypes: hardcalls.vcf}}
            """+extra+"""
            hypotheses:
              - id: H
                description: Registered genotype QC only.
                outcome: y
                covariates: [age]
                inputs: {discovery: [a]}
                analysis:
                  mode: manual
                  goals: [impact_assessment]
                  association: {model: ols}
                  genotype_sample_qc:
            """+policy);return StudyResolver.resolve(path);
    }
    @Test void generatedPcsAreResolvedBeforePlanningAndQcCanRunWithoutPrimaryAssociation()throws Exception {
        var resolved=resolve("        genome_build: GRCh38\n        rationale: Prespecified synthetic panel\n        include_pcs: true\n        pca_components: 2\n","");var hypothesis=map(list(resolved.document().get("hypotheses")).get(0));assertEquals(List.of("age","GPC1","GPC2"),hypothesis.get("covariates"));assertFalse(Files.readString(root.resolve("study.yaml")).contains("GPC1"));var operations=list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();var qc=operations.stream().filter(o->"participant_qc".equals(o.get("type"))).findFirst().orElseThrow();assertTrue(operations.stream().noneMatch(o->"association".equals(o.get("type"))));var inventory=operations.stream().filter(o->"finding_inventory".equals(o.get("type"))).findFirst().orElseThrow();assertTrue(list(inventory.get("depends_on")).contains(qc.get("id")));
    }
    @Test void diagnosticPcsDoNotSilentlyChangeTheRequestedRegression()throws Exception {
        var resolved=resolve("        genome_build: GRCh38\n        rationale: Synthetic diagnostic PCs only\n        pca_components: 2\n","");var hypothesis=map(list(resolved.document().get("hypotheses")).get(0));assertEquals(List.of("age"),hypothesis.get("covariates"));
    }
    @Test void disabledProfileDoesNotCreateAnIndependentQcNode()throws Exception {
        var resolved=resolve("        enabled: false\n","");var operations=list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();assertTrue(operations.stream().noneMatch(o->"participant_qc".equals(o.get("type"))));
    }
    @Test void sourceOverridesCannotSilentlyTargetUnknownOrNongenotypeDatasets()throws Exception {
        String policy="        genome_build: GRCh38\n        rationale: Synthetic cohort source validation\n";
        assertThrows(IllegalArgumentException.class,()->resolve(policy+"        dataset_sources: {missing: {source: supplied}}\n",""));
        resolve(policy,"  - {id: b, type: proteomics, stage: processed_matrix, files: {matrix: proteins.tsv, phenotype: phenotype.tsv}}\n");Path path=root.resolve("study.yaml");String doc=Files.readString(path).replace("inputs: {discovery: [a]}","inputs: {discovery: [a, b]}");Files.writeString(path,doc+"        dataset_sources: {b: {source: supplied}}\n");assertThrows(IllegalArgumentException.class,()->StudyResolver.resolve(path));
    }
}
