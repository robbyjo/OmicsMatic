/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyRareVariantsTest {
    @TempDir Path project;
    private String cli(String... args) throws Exception {
        var out = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        int code = JLinAlgCli.run(args, new PrintStream(out), new PrintStream(error));
        String diagnostic = out + "\n" + error;
        if (code != 0 && Files.isDirectory(project.resolve("runs"))) try (var paths = Files.walk(project.resolve("runs"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("manifest.yaml")).toList()) {
                var manifest = StudyYaml.loadArtifact(path);
                if ("failed".equals(manifest.get("state"))) diagnostic += "\n" + manifest.get("operation_type") + ": " + manifest.get("details");
            }
        }
        assertEquals(0, code, diagnostic); return out.toString();
    }
    private Path operation(String type) throws Exception {
        try (var paths = Files.walk(project.resolve("runs"))) {
            return paths.filter(Files::isDirectory).filter(path -> path.getFileName().toString().endsWith("-" + type)).findFirst().orElseThrow();
        }
    }
    private List<Path> operations(String type) throws Exception {
        try (var paths = Files.walk(project.resolve("runs"))) {
            return paths.filter(Files::isDirectory).filter(path -> path.getFileName().toString().endsWith("-" + type)).toList();
        }
    }
    private Path nativeStudy(String model, String chromosome) throws Exception {
        List<String> datasetYaml = new ArrayList<>();
        for (String cohort : List.of("a", "b", "v")) {
            StringBuilder vcf = new StringBuilder("##fileformat=VCFv4.2\n##contig=<ID=" + chromosome + ">\n##INFO=<ID=R2,Number=1,Type=Float,Description=\"Quality\">\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
            StringBuilder phenotype = new StringBuilder("id\ty\tage\n");
            for (int i = 0; i < 1000; i++) {
                vcf.append('\t').append(cohort).append(i);
                phenotype.append(cohort).append(i).append('\t').append(Math.sin(i * .77) + (i < 19 ? 1.5 : 0) + (i == 0 ? .5 : 0)).append('\t').append(30 + i % 73).append('\n');
            }
            vcf.append('\n');
            int[] mac = {1, 19, 20, 100, 5};
            for (int j = 0; j < mac.length; j++) {
                vcf.append(chromosome).append('\t').append(100 + j * 10).append("\tv").append(mac[j]).append("\tA\tG\t.\tPASS\tR2=").append(j == 4 ? ".1" : ".99").append("\tGT");
                for (int i = 0; i < 1000; i++) vcf.append(i < mac[j] ? "\t0/1" : "\t0/0");
                vcf.append('\n');
            }
            Files.writeString(project.resolve(cohort + ".vcf"), vcf); Files.writeString(project.resolve(cohort + "-pheno.tsv"), phenotype);
            Files.writeString(project.resolve(cohort + "-annotation.tsv"), "variant_id\tset_id\tconsequence\n" + chromosome + ":100:A:G\tGENE2\tlof\n"
                + chromosome + ":110:A:G\tGENE1\tlof\n" + chromosome + ":120:A:G\tGENE1\tlof\n"
                + chromosome + ":130:A:G\tGENE1\tlof\n" + chromosome + ":140:A:G\tGENE2\tlof\n");
            datasetYaml.add("  - {id: " + cohort + ", cohort: c" + cohort + ", type: gwas, stage: processed_matrix, genome_build: GRCh38, annotation_release: fixture-v1, units: synthetic, files: {matrix: "
                + cohort + ".vcf, phenotype: " + cohort + "-pheno.tsv, variant_annotation: " + cohort + "-annotation.tsv}}\n");
        }
        Path study = project.resolve("study.yaml");
        Files.writeString(study, """
            schema_version: 1
            project: {id: rare-native, title: Rare native, description: Independently eligible set and single-marker tests.}
            resources: {network: offline, downloads: deny}
            datasets:
            %s
            hypotheses:
              - id: H
                description: Gaussian rare masks with discovery-only pooling.
                outcome: y
                covariates: [age]
                inputs: {discovery: [a,b], replication: [v]}
                analysis:
                  mode: manual
                  goals: [association, rare_variant_analysis]
                  association: {model: %s, minimum_info: 0.8}
                  rare_variants:
                    trait_units: synthetic
                    simulations: 32
                    masks: [{id: lof, consequences: [lof], maximum_maf: 0.01}]
            """.formatted(String.join("", datasetYaml), model));
        return study;
    }

    @Test void nativeMasksKeepMacOneAndNineteenAndProduceAllTypedTests() throws Exception {
        Path study = nativeStudy("ols", "1");
        String sourceHash = FollowupSupport.hash(project.resolve("a.vcf"));
        var resolved = StudyResolver.resolve(study);
        var analysis = map(map(list(resolved.document().get("hypotheses")).get(0)).get("analysis"));
        assertEquals(20.0, map(analysis.get("association")).get("minimum_mac"));
        assertEquals(0.0, map(analysis.get("rare_variants")).get("minimum_mac"));
        var plan = list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        var score = plan.stream().filter(op -> "rare_variant_scores".equals(op.get("type"))).findFirst().orElseThrow();
        assertTrue(list(score.get("depends_on")).stream().anyMatch(id -> id.toString().endsWith("participant_qc")));
        assertFalse(list(score.get("depends_on")).stream().anyMatch(id -> id.toString().endsWith("-association")));
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: completed"));
        for (Path association : operations("association")) {
            try (var files = Files.list(association.resolve("standard-results"))) {
                var table = DelimitedData.readResult(files.findFirst().orElseThrow());
                assertEquals(List.of("v20", "v100"), table.rows().stream().map(row -> row[0]).toList());
            }
        }
        for (Path folder : operations("rare_variant_scores")) {
            var qc = DelimitedData.readResult(folder.resolve("variant-qc.tsv"));
            for (String key : List.of("1:100:A:G", "1:110:A:G", "1:120:A:G"))
                assertEquals("true", qc.rows().stream().filter(row -> row[0].equals(key)).findFirst().orElseThrow()[1]);
            assertEquals("false", qc.rows().stream().filter(row -> row[0].equals("1:140:A:G")).findFirst().orElseThrow()[1]);
        }
        Path rare = operation("rare_variant_analysis"); var results = DelimitedData.readResult(rare.resolve("rare-results.tsv"));
        assertEquals(50, results.rows().size()); // 3 cohort scopes + discovery + primary; 2 genes x 5 tests.
        for (String[] row : results.rows()) if (!row[results.column("test")].equals("burden")) {
            assertEquals("unsigned_set", row[results.column("result_type")]);
            for (String field : List.of("beta", "se", "direction")) assertEquals("NA", row[results.column(field)]);
        }
        var metadata = StudyYaml.loadArtifact(rare.resolve("rare-analysis.yaml"));
        assertEquals(10, ((Number) map(list(metadata.get("families")).get(0)).get("testing_denominator")).intValue());
        var influence = map(metadata.get("cohort_influence"));
        assertEquals(true, influence.get("performed"));
        assertEquals(30, ((Number) influence.get("comparison_rows")).intValue());
        assertEquals(FollowupSupport.hash(rare.resolve("rare-results.tsv")), influence.get("primary_results_sha256"));
        for (Object raw : list(influence.get("scopes"))) assertEquals(10, ((Number) map(raw).get("testing_denominator")).intValue());
        var sensitivity = DelimitedData.readResult(rare.resolve("rare-sensitivity-results.tsv"));
        assertEquals(30, sensitivity.rows().size());
        for (String[] row : sensitivity.rows()) {
            assertEquals("false", row[sensitivity.column("is_primary_test")]);
            assertEquals("2", row[sensitivity.column("n_cohorts")]);
        }
        var comparisons = DelimitedData.readResult(rare.resolve("rare-influence.tsv"));
        for (String[] row : comparisons.rows()) if (!row[comparisons.column("test")].equals("burden"))
            assertEquals("NA", row[comparisons.column("effect_direction_changed")]);
        assertTrue(Files.readString(rare.resolve("native/primary/lof.qc.tsv")).contains("1:130:A:G"));
        assertEquals(sourceHash, FollowupSupport.hash(project.resolve("a.vcf")));
        assertTrue(Files.readString(project.resolve("index/LLM_CONTEXT.md")).contains("tested_primary_sets"));
        cli("study", "verify", "--project", project.toString());
        Path imported = project.resolve("imported-scores"); Files.createDirectories(imported);
        List<String> sources = new ArrayList<>();
        for (Path folder : operations("rare_variant_scores")) {
            String id = text(map(StudyYaml.loadArtifact(folder.resolve("manifest.yaml")).get("details")).get("dataset"));
            for (String suffix : List.of("score.txt.gz", "cov.txt.gz", "score.txt.gz.tbi", "cov.txt.gz.tbi"))
                Files.copy(folder.resolve("cohort." + suffix), imported.resolve(id + "." + suffix));
            Files.copy(project.resolve(id + "-annotation.tsv"), imported.resolve(id + "-annotation.tsv"));
            sources.add("  - {id: " + id + ", cohort: c" + id + ", type: gwas, stage: rare_score_summary, genome_build: GRCh38, annotation_release: fixture-v1, units: synthetic, files: {scores: "
                + id + ".score.txt.gz, covariance: " + id + ".cov.txt.gz, variant_annotation: " + id + "-annotation.tsv}}\n");
        }
        // Stable ordering keeps the cohort-direction strings comparable as well as P values.
        sources.sort(String::compareTo);
        Path importedStudy = imported.resolve("study.yaml");
        Files.writeString(importedStudy, """
            schema_version: 1
            project: {id: rare-import, title: Import scores, description: Reproduce native pooled tests from strict summary files.}
            resources: {network: offline, downloads: deny}
            datasets:
            %s
            hypotheses:
              - id: H
                description: Same model and masks from imported scores.
                inputs: {discovery: [a,b], replication: [v]}
                analysis:
                  mode: manual
                  goals: [rare_variant_analysis]
                  rare_variants: {simulations: 32, masks: [{id: lof, consequences: [lof], maximum_maf: 0.01}]}
            """.formatted(String.join("", sources)));
        cli("study", "auto", importedStudy.toString(), "--project", imported.toString(), "--offline");
        Path importedResult;
        try (var paths = Files.walk(imported.resolve("runs"))) { importedResult = paths.filter(path -> path.getFileName().toString().equals("rare-results.tsv")).findFirst().orElseThrow(); }
        assertEquals(Files.readString(rare.resolve("rare-results.tsv")), Files.readString(importedResult));
        cli("study", "verify", "--project", imported.toString());
    }

    private Path importedStudy(boolean complete, boolean approvePool) throws Exception {
        Files.writeString(project.resolve("a.tsv"), "feature_id\tmask_id\ttest\tp_value\nG1\tlof\tskat\t.001\nG2\tlof\tskat\t.02\nG3\tlof\tskat\t.3\n");
        Files.writeString(project.resolve("b.tsv"), "feature_id\tmask_id\ttest\tp_value\nG1\tlof\tskat\t.002\nG2\tlof\tskat\t.03\nG3\tlof\tskat\t.4\n");
        Files.writeString(project.resolve("v.tsv"), "feature_id\tmask_id\ttest\tp_value\nG1\tlof\tskat\t.004\nG3\tlof\tskat\t.5\n");
        Path study = project.resolve("study.yaml");
        Files.writeString(study, """
            schema_version: 1
            project: {id: unsigned, title: Unsigned, description: Preserve P-only result semantics and full testing families.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, cohort: ca, type: gwas, stage: set_association_results, genome_build: GRCh38, files: {results: a.tsv}, metadata: {complete_testing_family: %s, set_definition_id: lof-v1, trait_id: BMI}}
              - {id: b, cohort: cb, type: gwas, stage: set_association_results, genome_build: GRCh38, files: {results: b.tsv}, metadata: {complete_testing_family: true, set_definition_id: lof-v1, trait_id: BMI}}
              - {id: v, cohort: cv, type: gwas, stage: set_association_results, genome_build: GRCh38, files: {results: v.tsv}, metadata: {complete_testing_family: true, set_definition_id: lof-v1, trait_id: BMI}}
            hypotheses:
              - id: H
                description: Discovery-only unsigned pooling and frozen validation.
                inputs: {discovery: [a,b], replication: [v]}
                analysis:
                  mode: manual
                  goals: [rare_variant_analysis]
                  rare_variants: {tests: [skat], primary_test: skat, unsigned_pooling: %s}
            """.formatted(complete, approvePool ? "acat" : "disabled"));
        return study;
    }

    @Test void pOnlyInputsAvoidBetaAdaptersAndUseCompleteDiscoveryAndValidationFamilies() throws Exception {
        Path study = importedStudy(true, true);
        Files.writeString(study, Files.readString(study).replace("primary_test: skat,", "primary_test: skat, minimum_cohorts: 2,"));
        var operations = list(StudyPlanner.compile(StudyResolver.resolve(study)).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertFalse(operations.stream().anyMatch(op -> List.of("meta_analysis", "effect_comparison", "discovery_replication").contains(op.get("type"))));
        assertFalse(operations.stream().anyMatch(op -> "rare_set_omnibus".equals(op.get("type"))), "Manual rare-only goals do not acquire an unrequested supplemental family");
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: completed"));
        Path output = operation("rare_variant_analysis"); var table = DelimitedData.readResult(output.resolve("rare-results.tsv"));
        for (String[] row : table.rows()) for (String column : List.of("beta", "se", "direction")) assertEquals("NA", row[table.column(column)]);
        String[] cohort = table.rows().stream().filter(row -> row[0].equals("cohort:a") && row[1].equals("G1")).findFirst().orElseThrow();
        assertEquals(.003, Double.parseDouble(cohort[table.column("adjusted_p")]), 1e-12);
        var replication = DelimitedData.readResult(output.resolve("rare-replication.tsv"));
        String[] missing = replication.rows().stream().filter(row -> row[0].equals("G2")).findFirst().orElseThrow();
        assertEquals("not_measured_or_unavailable", missing[replication.column("coverage")]);
        assertEquals("false", missing[replication.column("replicated")]);
        assertEquals("2", missing[replication.column("validation_testing_denominator")]);
        var influence = DelimitedData.readResult(output.resolve("rare-influence.tsv"));
        String[] unestimable = influence.rows().stream().filter(row -> row[0].equals("a") && row[2].equals("G2")).findFirst().orElseThrow();
        for (String field : List.of("sensitivity_p", "sensitivity_adjusted_p", "effect_direction_changed", "significance_changed"))
            assertEquals("NA", unestimable[influence.column(field)]);
        assertEquals("not_estimable_in_both", unestimable[influence.column("coverage")]);
        var details = StudyYaml.loadArtifact(output.resolve("rare-analysis.yaml"));
        for (Object raw : list(map(details.get("cohort_influence")).get("scopes"))) {
            var family = map(raw);
            assertEquals(family.get("omitted_dataset").equals("v") ? 3 : 2, ((Number) family.get("testing_denominator")).intValue());
        }
        cli("study", "verify", "--project", project.toString());
    }

    @Test void disablingCohortInfluenceKeepsThePrespecifiedPrimaryExactlyUnchanged() throws Exception {
        Path study = importedStudy(true, true);
        cli("study", "auto", study.toString(), "--offline");
        Path enabled = operation("rare_variant_analysis");
        String primary = Files.readString(enabled.resolve("rare-results.tsv"));
        Path separate = project.resolve("no-influence"); Files.createDirectories(separate);
        for (String file : List.of("a.tsv", "b.tsv", "v.tsv")) Files.copy(project.resolve(file), separate.resolve(file));
        Path disabledStudy = separate.resolve("study.yaml");
        Files.writeString(disabledStudy, Files.readString(study).replace("primary_test: skat,", "primary_test: skat, leave_one_cohort_out: false,"));
        assertTrue(cli("study", "auto", disabledStudy.toString(), "--project", separate.toString(), "--offline").contains("state: completed"));
        Path disabled;
        try (var paths = Files.walk(separate.resolve("runs"))) { disabled = paths.filter(p -> p.getFileName().toString().endsWith("-rare_variant_analysis")).findFirst().orElseThrow(); }
        assertEquals(primary, Files.readString(disabled.resolve("rare-results.tsv")));
        assertTrue(DelimitedData.readResult(disabled.resolve("rare-influence.tsv")).rows().isEmpty());
        var summary = map(StudyYaml.loadArtifact(disabled.resolve("rare-analysis.yaml")).get("cohort_influence"));
        assertEquals(false, summary.get("attempted")); assertEquals(false, summary.get("performed"));
        assertTrue(text(summary.get("reason")).contains("Disabled"));
        cli("study", "verify", "--project", separate.toString());
    }

    @Test void cohortInfluenceCannotReduceTheDeclaredMinimumNumberOfCohorts() throws Exception {
        Path study = importedStudy(true, true);
        Files.writeString(study, Files.readString(study).replace("primary_test: skat,", "primary_test: skat, minimum_cohorts: 3,"));
        cli("study", "auto", study.toString(), "--offline");
        var summary = map(StudyYaml.loadArtifact(operation("rare_variant_analysis").resolve("rare-analysis.yaml")).get("cohort_influence"));
        assertEquals(false, summary.get("attempted")); assertEquals(false, summary.get("performed"));
        assertTrue(text(summary.get("reason")).contains("minimum_cohorts"));
        assertTrue(DelimitedData.readResult(operation("rare_variant_analysis").resolve("rare-sensitivity-results.tsv")).rows().isEmpty());
    }

    @Test void nativeOmissionsRecomputeFrequencyEligibilityAndExposeChangedVariantCounts() throws Exception {
        Path study = nativeStudy("ols", "1");
        Files.writeString(study, Files.readString(study).replace("goals: [association, rare_variant_analysis]", "goals: [rare_variant_analysis]")
            .replace("simulations: 32", "tests: [burden]\n        primary_test: burden\n        simulations: 32"));
        for (String cohort : List.of("b", "v")) {
            Path vcf = project.resolve(cohort + ".vcf"); List<String> lines = new ArrayList<>(Files.readAllLines(vcf));
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith("1\t130\t")) {
                String[] columns = lines.get(i).split("\t");
                for (int sample = 9; sample < columns.length; sample++) columns[sample] = sample == 9 ? "0/1" : "0/0";
                lines.set(i, String.join("\t", columns));
            }
            Files.write(vcf, lines);
        }
        String inputHash = FollowupSupport.hash(project.resolve("b.vcf"));
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: completed"));
        assertTrue(operations("cohort_overlap").isEmpty(), "A rare-set-only workflow has no single-marker cohort tables to compare");
        var comparisons = DelimitedData.readResult(operation("rare_variant_analysis").resolve("rare-influence.tsv"));
        String[] omitted = comparisons.rows().stream().filter(row -> row[0].equals("a") && row[2].equals("GENE1")).findFirst().orElseThrow();
        // 102/6000 > .01 in all cohorts; 2/4000 < .01 after excluding a.
        assertEquals("2", omitted[comparisons.column("primary_n_variants")]);
        assertEquals("3", omitted[comparisons.column("sensitivity_n_variants")]);
        assertEquals("tested_both", omitted[comparisons.column("coverage")]);
        assertEquals(inputHash, FollowupSupport.hash(project.resolve("b.vcf")));
        cli("study", "verify", "--project", project.toString());
    }

    @Test void incompleteFamiliesAndUnapprovedUnsignedPoolingAreVisiblePrerequisites() throws Exception {
        Path study = importedStudy(false, true);
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: waiting_for_input"));
        assertTrue(Files.readString(operation("rare_variant_analysis").resolve("request.yaml")).contains("complete_testing_family"));
    }
    @Test void unapprovedUnsignedPoolingDoesNotInventAnEffectMetaAnalysis() throws Exception {
        Path study = importedStudy(true, false);
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: waiting_for_input"));
        assertTrue(Files.readString(operation("rare_variant_analysis").resolve("request.yaml")).contains("unsigned_pooling"));
        assertFalse(Files.exists(operation("rare_variant_analysis").resolve("rare-results.tsv")));
    }
    @Test void repeatedCohortSourcesCannotBecomeIndependentSetReplication() throws Exception {
        Path study = importedStudy(true, true);
        Files.writeString(study, Files.readString(study).replace("cohort: cb", "cohort: ca"));
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: waiting_for_input"));
        var request = StudyYaml.loadArtifact(operation("rare_variant_analysis").resolve("request.yaml"));
        assertTrue(text(request.get("reason")).contains("cannot be counted as independent"));
        assertFalse(Files.exists(operation("rare_variant_analysis").resolve("rare-results.tsv")));
    }
    @Test void unsupportedPloidyBlocksRareMasksButLeavesTheSourceUntouched() throws Exception {
        Path study = nativeStudy("ols", "X");
        String hash = FollowupSupport.hash(project.resolve("a.vcf"));
        cli("study", "auto", study.toString(), "--offline");
        assertTrue(Files.readString(operation("rare_variant_masks").resolve("request.yaml")).contains("ploidy"));
        assertEquals(hash, FollowupSupport.hash(project.resolve("a.vcf")));
    }
    @Test void emptyDeclaredMasksAreNotPresentedAsNegativeAssociations() throws Exception {
        Path study = nativeStudy("ols", "1");
        for (String cohort : List.of("a", "b", "v")) {
            Path annotation = project.resolve(cohort + "-annotation.tsv");
            Files.writeString(annotation, Files.readString(annotation).replace("\tlof", "\tsynonymous"));
        }
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: completed"));
        Path output = operation("rare_variant_analysis");
        assertTrue(DelimitedData.readResult(output.resolve("rare-results.tsv")).rows().isEmpty());
        var summary = StudyYaml.loadArtifact(output.resolve("rare-analysis.yaml"));
        assertEquals("not_estimable", summary.get("evidence_disposition"));
        assertEquals(false, map(summary.get("method_report")).get("performed"));
        cli("study", "verify", "--project", project.toString());
    }
    @Test void quantitativeExporterDoesNotApplyGaussianCalibrationToBinaryOutcomes() throws Exception {
        Path study = nativeStudy("ols", "1");
        for (String cohort : List.of("a", "b", "v")) {
            StringBuilder phenotype = new StringBuilder("id\ty\tage\n");
            for (int i = 0; i < 1000; i++) phenotype.append(cohort).append(i).append('\t').append(i % 2).append('\t').append(30 + i % 73).append('\n');
            Files.writeString(project.resolve(cohort + "-pheno.tsv"), phenotype);
        }
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: waiting_for_input"));
        for (Path folder : operations("rare_variant_scores")) {
            assertTrue(Files.readString(folder.resolve("request.yaml")).contains("binary"));
            assertFalse(Files.exists(folder.resolve("cohort.score.txt.gz")));
        }
    }
    @Test void correctionIncludesEveryRegisteredTestAndMaskRatherThanOnlyPrimaryPreview() throws Exception {
        Files.writeString(project.resolve("sets.tsv"), "feature_id\tmask_id\ttest\tp_value\tbeta\tse\tresult_type\nG\tlof\tskat\t.001\t10\t1\tunsigned_set\nG\tlof\tacat-o\t.002\t10\t1\tunsigned_set\nG\tmissense\tskat\t.003\t10\t1\tunsigned_set\nG\tmissense\tacat-o\t.004\t10\t1\tunsigned_set\n");
        Path study = project.resolve("study.yaml");
        Files.writeString(study, """
            schema_version: 1
            project: {id: masks, title: Mask multiplicity, description: Joint test and mask family.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, type: gwas, stage: set_association_results, files: {results: sets.tsv}, metadata: {complete_testing_family: true}}
            hypotheses:
              - id: H
                description: Preserve primary selection and the complete correction denominator.
                analysis: {mode: manual, goals: [rare_variant_analysis], rare_variants: {tests: [skat, acat-o], primary_test: acat-o}}
            """);
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: completed"));
        var table = DelimitedData.readResult(operation("rare_variant_analysis").resolve("rare-results.tsv"));
        for (String[] row : table.rows()) {
            assertEquals(.004, Double.parseDouble(row[table.column("adjusted_p")]), 1e-12);
            assertEquals("NA", row[table.column("beta")], "An unrelated effect column cannot give an unsigned statistic a beta");
        }
        String index = Files.readString(project.resolve("index/LLM_CONTEXT.md"));
        assertTrue(index.contains("joint gene/mask/test family"));
        cli("study", "verify", "--project", project.toString());
    }
    @Test void explicitlySignedImportsRequireAnEstimableBurdenEffect() throws Exception {
        Files.writeString(project.resolve("invalid.tsv"), "feature_id\tmask_id\ttest\tp_value\tbeta\tse\tresult_type\nG\tlof\tburden\t.001\t1\t0\tsigned_burden\n");
        var dataset = Map.<String, Object>of("id", "a", "stage", "set_association_results", "files", Map.of("results", "invalid.tsv"), "metadata", Map.of("complete_testing_family", true));
        var hypothesis = Map.<String, Object>of("id", "H", "analysis", Map.of("rare_variants", Map.of("tests", List.of("burden"), "primary_test", "burden")));
        Path output = project.resolve("invalid-output");
        var context = new org.jlinalg.study.StudyOperationExecutor.Context(project, output,
            Map.of("type", "rare_variant_analysis", "hypothesis", "H", "datasets", List.of("a")), Map.of("datasets", List.of(dataset), "hypotheses", List.of(hypothesis)), Map.of(), Map.of());
        assertThrows(java.io.IOException.class, () -> new StudyRareVariants().execute(context));
        assertFalse(Files.exists(output.resolve("rare-results.tsv")));
    }
    @Test void relatedStudyScoresMatchAnIndependentBalancedRandomInterceptReference() throws Exception {
        double[] y = {-2.2,-1.8,-1.3,-.7,-.1,.1,.6,1.4,1.8,2.2,2.7,3.3};
        int[][] genotype = {{0,1,0,0,1,0,0,2,0,1,0,0}, {1,0,0,1,0,0,0,0,1,0,0,1}};
        StringBuilder vcf = new StringBuilder("##fileformat=VCFv4.2\n##contig=<ID=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");
        for (int i = 0; i < 12; i++) vcf.append("\ts").append(i);
        vcf.append('\n');
        for (int j = 0; j < 2; j++) {
            vcf.append("1\t").append(10 + j * 10).append("\tv").append(j).append("\tA\tG\t.\tPASS\t.\tGT");
            for (int call : genotype[j]) vcf.append('\t').append(call == 0 ? "0/0" : call == 1 ? "0/1" : "1/1");
            vcf.append('\n');
        }
        StringBuilder phenotype = new StringBuilder("id\ty\n"), grm = new StringBuilder("id");
        for (int i = 11; i >= 0; i--) { phenotype.append("s").append(i).append('\t').append(y[i]).append('\n'); grm.append("\ts").append(i); }
        grm.append('\n');
        for (int i = 11; i >= 0; i--) {
            grm.append("s").append(i);
            for (int j = 11; j >= 0; j--) grm.append('\t').append(i / 2 == j / 2 ? 1 : 0);
            grm.append('\n');
        }
        Files.writeString(project.resolve("cohort.vcf"), vcf); Files.writeString(project.resolve("pheno.tsv"), phenotype); Files.writeString(project.resolve("grm.tsv"), grm);
        Files.writeString(project.resolve("annotation.tsv"), "variant_id\tset_id\tconsequence\n1:10:A:G\tG\tlof\n1:20:A:G\tG\tlof\n");
        Path study = project.resolve("study.yaml");
        Files.writeString(study, """
            schema_version: 1
            project: {id: related, title: Related, description: Exact Gaussian random-intercept reference after sample alignment.}
            resources: {network: offline, downloads: deny}
            datasets:
              - {id: a, type: gwas, stage: processed_matrix, genome_build: GRCh38, annotation_release: test, files: {matrix: cohort.vcf, phenotype: pheno.tsv, grm: grm.tsv, variant_annotation: annotation.tsv}}
            hypotheses:
              - id: H
                description: GRM score export.
                outcome: y
                analysis:
                  mode: manual
                  goals: [rare_variant_analysis]
                  association: {model: reml}
                  rare_variants: {trait_units: synthetic, tests: [burden], primary_test: burden, masks: [{id: lof, consequences: [lof], maximum_maf: 0.5}]}
            """);
        assertTrue(cli("study", "auto", study.toString(), "--offline").contains("state: completed"));
        Path folder = operation("rare_variant_scores");
        double residual = .86 / 6, genetic = 3.5 - residual / 2, a = genetic + residual, b = genetic, determinant = a * a - b * b;
        double[][] inverse = new double[12][12];
        for (int i = 0; i < 12; i++) { inverse[i][i] = a / determinant; inverse[i][i ^ 1] = -b / determinant; }
        try (var reader = new org.jlinalg.raremetal.RareMetalStudy(folder.resolve("cohort.score.txt.gz"), folder.resolve("cohort.cov.txt.gz"), 0, 0, true)) {
            assertEquals("related-Gaussian-REML", reader.metadata().value("NullModel"));
            var variants = reader.region("1", 10, 20); var records = List.of(variants.get(10L), variants.get(20L));
            double[] covariance = reader.covariance(records);
            for (int h = 0; h < 2; h++) {
                double expectedU = 0;
                for (int i = 0; i < 12; i++) for (int j = 0; j < 12; j++) expectedU += genotype[h][i] * inverse[i][j] * (y[j] - .5);
                assertEquals(expectedU, records.get(h).score(), Math.max(1, Math.abs(expectedU)) * 2e-6);
                for (int j = 0; j < 2; j++) {
                    double expectedV = 0, meanH = java.util.Arrays.stream(genotype[h]).average().orElseThrow(), meanJ = java.util.Arrays.stream(genotype[j]).average().orElseThrow();
                    for (int left = 0; left < 12; left++) for (int right = 0; right < 12; right++) expectedV += (genotype[h][left] - meanH) * inverse[left][right] * (genotype[j][right] - meanJ);
                    assertEquals(expectedV, covariance[h * 2 + j], Math.max(1, Math.abs(expectedV)) * 2e-6);
                }
            }
        }
        cli("study", "verify", "--project", project.toString());
    }
}
