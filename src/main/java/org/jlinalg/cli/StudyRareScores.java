/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import static org.jlinalg.cli.StudyRareSupport.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.ols.OlsOptions;
import org.jlinalg.pipeline.*;
import org.jlinalg.raremetal.GaussianScoreWriter;
import org.jlinalg.study.StudyOperationExecutor.*;

/** Set-test score generation consumes prefit samples and an independent variant filter. */
final class StudyRareScores {
    Result execute(Context context) throws IOException {
        if(!org.jlinalg.study.StudyHumanPloidyPolicy.resolve(map(StudyResultTables.analysis(context).get("association"))).isEmpty())
            return waiting(context,"human-ploidy-set-model","Registered human-ploidy contrasts are supported for single-variant independent OLS/GLM only. Native rare-set scores need a separately validated chromosome/ploidy model; provide compatible reviewed set results or register a separate supported hypothesis.");
        var config = policy(context); var dataset = datasets(context).get(0);
        var files = map(dataset.get("files")); var columns = map(dataset.get("columns"));
        var hypothesis = hypothesis(context); var association = map(StudyResultTables.analysis(context).get("association"));
        String model = text(association.get("model"));
        if (model.isBlank()) return new StudyModelReview().execute(context, hypothesis, dataset);
        if(model.equals("glm")&&"binomial".equals(association.get("family")))return new StudyBinaryRareScores().execute(context);
        if (!Set.of("ols", "reml").contains(model) || association.containsKey("family") && !Set.of("gaussian", "normal").contains(association.get("family")))
            return waiting(context, "calibrated-model", "Native rare scores require an explicitly declared quantitative Gaussian OLS or GRM REML model. Binary, survival and other designs need a reviewed calibrated set-test adapter or suitable imported results; no ordinary Wald fallback is used.");
        if (association.containsKey("link") && !"identity".equals(association.get("link")))
            return waiting(context, "link", "Native Gaussian rare scores require the declared identity-scale model; a different link cannot be silently ignored.");
        boolean related = files.containsKey("grm") || files.containsKey("pedigree") || Boolean.TRUE.equals(map(dataset.get("metadata")).get("related_participants"));
        if (related && !model.equals("reml") || model.equals("reml") && !files.containsKey("grm"))
            return waiting(context, "relatedness", "Known related participants require the supported GRM REML score model and files.grm. Pedigree-only or unsupported dependence needs a separately calibrated adapter.");
        String units = text(config.getOrDefault("trait_units", map(dataset.get("metadata")).get("phenotype_units")));
        if (units.isBlank()) return waiting(context, "units", "Declare rare_variants.trait_units or dataset.metadata.phenotype_units for quantitative score compatibility. Genotype/assay units and regression-effect units cannot substitute for phenotype units.");
        Path membership = StudyResultTables.artifact(context, "rare_variant_masks", "mask-membership.tsv");
        Path masksFile = StudyResultTables.artifact(context, "rare_variant_masks", "masks.yaml");
        Path phenotype = StudyResultTables.artifact(context, "participant_qc", "analysis-phenotype.tsv");
        if (membership == null || masksFile == null || phenotype == null) return waiting(context, "prefit-masks", "Verified annotation masks and prefit-selected phenotype records must precede rare score generation.");
        var masks = StudyYaml.loadArtifact(masksFile);
        if (DelimitedData.readResult(membership).rows().isEmpty()) return emptyScores(context);
        long window = ((Number) config.get("covariance_window_bp")).longValue();
        if (((Number) masks.get("required_covariance_window_bp")).longValue() > window)
            return waiting(context, "covariance-window", "The declared covariance window does not span every set; increase covariance_window_bp with a recorded computational budget. Missing covariance is never replaced by zero.");
        var membershipTable = DelimitedData.readResult(membership);
        Set<String> requested = new HashSet<>(); Map<String, String> locus = new HashMap<>();
        for (String[] row : membershipTable.rows()) {
            Variant variant = Variant.parse(row[membershipTable.column("variant_id")]);
            requested.add(variant.toString()); locus.put(variant.chromosome() + ":" + variant.position(), variant.toString());
        }
        Path matrix = context.project().resolve(text(files.get("matrix")));
        String sampleRole = files.containsKey("sample") ? "sample" : "samples";
        List<String> external = files.containsKey(sampleRole) ? SampleFiles.read(context.project().resolve(text(files.get(sampleRole)))) : null;
        VariantSource source = VariantSources.open(matrix, DataFormat.infer(matrix), external);
        var table = DelimitedData.read(phenotype); String idName = text(columns.getOrDefault("sample_id", "id"));
        int idColumn = table.column(idName);
        String outcomeKey = text(hypothesis.get("outcome")); String outcome = text(map(columns.get("phenotypes")).getOrDefault(outcomeKey, outcomeKey));
        int response = table.column(outcome); List<String> covariateNames = new ArrayList<>();
        for (String name : strings(hypothesis.get("covariates"))) covariateNames.add(text(map(columns.get("covariates")).getOrDefault(name, name)));
        Map<String, String[]> people = new HashMap<>();
        for (String[] row : table.rows()) if (people.put(row[idColumn], row) != null) throw new IOException("Rare scores require unique prefit sample IDs");
        List<Integer> order = new ArrayList<>(); List<Double> y = new ArrayList<>(); List<double[]> x = new ArrayList<>(); List<String> aligned = new ArrayList<>();
        var sampleIds = source.metadata().sampleIds();
        for (int i = 0; i < sampleIds.size(); i++) {
            String[] row = people.get(sampleIds.get(i)); if (row == null) continue;
            double[] design = new double[covariateNames.size() + 1]; design[0] = 1;
            try {
                double value = Double.parseDouble(row[response]); if (!Double.isFinite(value)) throw new NumberFormatException();
                for (int j = 0; j < covariateNames.size(); j++) {
                    design[j + 1] = Double.parseDouble(row[table.column(covariateNames.get(j))]);
                    if (!Double.isFinite(design[j + 1])) throw new NumberFormatException();
                }
                order.add(i); aligned.add(sampleIds.get(i)); y.add(value); x.add(design);
            } catch (NumberFormatException error) { throw new IOException("Native Gaussian rare scores require finite numeric prefit outcomes/covariates; declare supported encoding and preprocessing", error); }
        }
        if (y.size() <= covariateNames.size() + 1) return waiting(context, "samples", "Insufficient prefit participants for the declared Gaussian null model");
        if (y.stream().distinct().count() < 2) return waiting(context, "outcome-variation", "The prefit phenotype has no variation; no rare-set association is estimable.");
        if (y.stream().allMatch(value -> value == 0 || value == 1)) return waiting(context, "binary-outcome", "The 0/1 outcome requires a calibrated binary-trait set-test workflow; native quantitative Gaussian score export is inapplicable.");
        VariantFilterOptions filters = VariantFilterOptions.builder().minimumMac(((Number) config.get("minimum_mac")).doubleValue())
            .maximumMissingRate(((Number) config.get("maximum_marker_missing")).doubleValue())
            .minimumImputationQuality(config.containsKey("minimum_info") ? ((Number) config.get("minimum_info")).doubleValue() : Double.NaN).build();
        double hweCutoff = ((Number) config.get("minimum_hwe_p")).doubleValue();
        Set<String> observed = new HashSet<>(); long[] counts = new long[4];
        Path qcFile = context.outputDirectory().resolve("variant-qc.tsv");
        try (BufferedWriter qc = Files.newBufferedWriter(qcFile)) {
            qc.write("variant_id\tretained\treason\tminor_allele_count\tminor_allele_frequency\tcall_rate\timputation_quality\thwe_p\n");
            VariantSource eligible = new VariantSource() {
                public VariantSourceMetadata metadata() { return source.metadata(); }
                public VariantBlockReader open(int[] samples) throws IOException {
                    var reader = source.open(samples);
                    return new VariantBlockReader() {
                        public VariantBlock read(int maximum) throws IOException {
                            while (true) {
                                VariantBlock block = reader.read(1); if (block == null) return null;
                                VariantRecord variant = block.variants().get(0); counts[0]++;
                                String position = org.jlinalg.raremetal.RareMetalStudy.chromosome(variant.chromosome()) + ":" + variant.position();
                                if (!locus.containsKey(position)) continue;
                                String key = Variant.parse(position + ":" + variant.referenceAllele() + ":" + variant.alternateAllele()).toString();
                                if (!requested.contains(key)) throw new IOException("Genotype alleles conflict with the declared annotation at " + position);
                                observed.add(key); counts[1]++;
                                var result = VariantFilters.evaluate(variant, filters); var stats = result.statistics();
                                List<String> reasons = new ArrayList<>(result.reasons().stream().map(Enum::name).toList());
                                double hwe = VariantQualityDiagnostics.calculate(variant,stats,filters.quality()).hweAll().pValue();
                                if (hweCutoff > 0 && (!Double.isFinite(hwe) || hwe < hweCutoff)) reasons.add("HWE_BELOW_THRESHOLD_OR_UNAVAILABLE");
                                boolean keep = reasons.isEmpty(); if (keep) { counts[2]++; if (stats.minorAlleleCount() < 20) counts[3]++; }
                                qc.write(key + "\t" + keep + "\t" + String.join(";", reasons) + "\t" + stats.minorAlleleCount() + "\t" + stats.minorAlleleFrequency()
                                    + "\t" + (1 - stats.missingRate()) + "\t" + numeric(variant.imputationQuality()) + "\t" + numeric(hwe) + "\n");
                                if (keep) return block;
                            }
                        }
                        public void close() throws IOException { reader.close(); }
                    };
                }
            };
            Path score = context.outputDirectory().resolve("cohort.score.txt.gz"), covariance = context.outputDirectory().resolve("cohort.cov.txt.gz");
            String trait = text(config.getOrDefault("trait_id", outcomeKey));
            double[] responseValues = y.stream().mapToDouble(Double::doubleValue).toArray(); double[][] design = x.toArray(double[][]::new);
            int[] indices = order.stream().mapToInt(Integer::intValue).toArray();
            if (model.equals("ols")) {
                var nullModel = org.jlinalg.settest.LinearSetTestNullModel.prepare(responseValues, design, OlsOptions.defaults(), BackendPolicy.CPU);
                GaussianScoreWriter.write(eligible, indices, nullModel, window, ((Number) config.get("maximum_variants")).intValue(), text(dataset.get("genome_build")), trait, units, score, covariance);
            } else {
                var grm = GrmReader.read(context.project().resolve(text(files.get("grm")))).matrix();
                var nullModel = org.jlinalg.gwas.RemlAssociationScanner.prepare(responseValues, design,
                    List.of(grm.varianceComponent("genetic", aligned), org.jlinalg.reml.VarianceComponent.identity("residual", y.size())),
                    org.jlinalg.reml.RemlOptions.defaults(), BackendPolicy.CPU);
                GaussianScoreWriter.write(eligible, indices, nullModel, window, ((Number) config.get("maximum_variants")).intValue(), text(dataset.get("genome_build")), trait, units, score, covariance);
            }
        }
        Set<String> absent = new java.util.TreeSet<>(requested); absent.removeAll(observed);
        FollowupSupport.table(context.outputDirectory().resolve("unobserved-variants.tsv"), List.of("variant_id", "disposition"),
            absent.stream().map(key -> new String[] {key, "not_observed_in_supplied_genotype_source"}).toList());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataset", dataset.get("id")); details.put("analyzed_samples", y.size()); details.put("retained_variants", counts[2]);
        details.put("retained_variants_with_mac_below_20", counts[3]);
        details.put("source_variants_examined", counts[0]); details.put("mask_variants_observed", counts[1]); details.put("unobserved_variants", absent.size());
        details.put("effective_policy", config); details.put("single_variant_mac_cutoff_applied", false);
        details.put("scope", "Quantitative Gaussian scores in declared phenotype units; prefit participants; predeclared annotation membership. Independent set MAC/call-rate/INFO/HWE filters, not the single-variant MAC 20 export. MAF masks are applied during compatible score pooling.");
        details.put("method_report", StudyMethods.record(model.equals("ols") ? "Gaussian OLS null scores" : "Gaussian GRM REML null scores",
            Map.of("trait_id", config.getOrDefault("trait_id", outcomeKey), "trait_units", units, "covariates", covariateNames, "analysis_samples", y.size(), "set_qc", config,
                "covariance_window_bp", window, "missing_dosages", "mean imputation on fixed prefit samples"), list(config.get("references")), config.get("rationale"), List.of("rare-variant score tests")));
        write(context.outputDirectory().resolve("scores.yaml"), details);
        return new Result(State.COMPLETE, "Exported independently eligible Gaussian rare-set scores", outputs(context), details);
    }
    private static String numeric(double value) { return Double.isFinite(value) ? Double.toString(value) : "NA"; }
}
