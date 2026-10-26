/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.plot.DiagramPlots;
import org.jlinalg.plot.LegendPosition;
import org.jlinalg.plot.PlotOptions;
import org.jlinalg.plot.Plots;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudyNeed;
import org.yaml.snakeyaml.Yaml;

/** Concrete adapters from study operations to existing JLinAlg CLI workflows. */
final class StudyCliExecutor implements StudyOperationExecutor {
    @Override public boolean usesNativeStageInputContracts() { return true; }
    private static final Set<String> BULK_ASSOCIATION_TYPES = Set.of(
        "generic", "transcriptomics", "expression", "mirna", "lincrna",
        "lncrna", "extracellular_rna", "epigenomics", "ewas", "gwas",
        "genomics", "proteomics", "metabolomics", "phenotype");
    static boolean supportsBulkAssociation(String type) {
        return BULK_ASSOCIATION_TYPES.contains(type.toLowerCase(Locale.ROOT));
    }
    private static final Set<String> CUSTOM_COMMANDS = Set.of(
        "enrichment", "variant-score", "susie", "coloc", "mr-estimate", "mr-xwas",
        "clump", "mr-instruments", "ldsc", "twas", "pwas", "genomic-factor", "network",
        "differential", "ewas-regions", "multiple-test",
        "multiple-impute", "mi-pool");

    @Override
    public Result execute(Context context) throws Exception {
        Files.createDirectories(context.outputDirectory());
        if (context.operation().containsKey("supplied_result_id")) return new StudySuppliedResults().execute(context);
        String type = text(context.operation().get("type"));
        try { return switch (type) {
            case "validate_harmonize" -> harmonize(context);
            case "association" -> association(context);
            case "finding_inventory" -> new StudyFindingInventory().execute(context);
            case "impact_assessment" -> new StudyImpactAssessment().execute(context);
            case "assay_qc" -> new StudyAssayQc().execute(context);
            case "participant_qc" -> new StudyPrefitQc().execute(context);
            case "network_preservation_null" -> new StudyNetworkNull().execute(context);
            case "gwas_diagnostics" -> new StudyGwasDiagnostics().execute(context);
            case "subgroup_interactions" -> new StudySubgroupInteractions().execute(context);
            case "rare_variant_masks", "rare_variant_scores", "rare_variant_analysis" -> new StudyRareVariants().execute(context);
            case "rare_set_followup" -> new StudyRareFollowup().execute(context);
            case "rare_cohort_overlap" -> new StudyRareCohortOverlap().execute(context);
            case "effect_comparison" -> effectComparison(context);
            case "cohort_overlap" -> new StudyCohortOverlap().execute(context);
            case "heterogeneity_review" -> new StudyHeterogeneity().execute(context);
            case "discovery_replication" -> replication(context);
            case "meta_analysis" -> meta(context);
            case "discovery_meta_analysis" -> discoveryMeta(context);
            case "diagnostic_review" -> diagnostics(context);
            case "report" -> report(context);
            case "participant_characteristics" -> new StudyParticipantCharacteristics().execute(context);
            case "irqtl_candidate_review" -> new StudyIrQtlReview().execute(context);
            case "candidate_prioritization" -> prioritization(context);
            case "source_discovery", "bioinformatics_investigation", "literature_search" -> new StudyBioinformatics().execute(context);
            case "literature_support", "advisor_review", "related_phenotype_advice" -> new StudyAssistance().execute(context);
            case "manuscript_draft" -> "study".equals(context.operation().get("scope")) ? StudyImpactAssessment.manuscript(context) : new StudyAssistance().execute(context);
            case "sensitivity_analysis" -> sensitivity(context);
            case "gene_ontology" -> automaticOrConfiguredEnrichment(
                context, "GO", "gene-ontology.tsv");
            case "pathway_enrichment" -> automaticOrConfiguredEnrichment(
                context, "Reactome", "pathway-enrichment.tsv");
            case "variant_annotation", "variant_scoring" -> optionalMap(context.operation().get("configuration"), "configuration").containsKey("command") ? configured(context) : new StudyVariantFollowup().execute(context);
            case "rare_set_omnibus" -> new StudyRareOmnibus().execute(context);
            case "mr_family_adjustment" -> new StudyMrFamilyAdjustment().execute(context);
            case "network_preservation" -> new StudyNetworkPreservation().execute(context);
            case "network_analysis", "pathway_network", "network_modules", "module_gene_ontology" ->
                optionalMap(context.operation().get("configuration"), "configuration").containsKey("command") ? configured(context) : new StudyNetworks().execute(context);
            case "gwas_enrichment" -> new StudyGwasEnrichment().execute(context);
            case "ranked_gsea" -> optionalMap(context.operation().get("configuration"),"configuration").containsKey("command")?configured(context):new StudyGsea().execute(context);
            case "risk_score_evaluation", "risk_score_related_phenotypes" -> optionalMap(context.operation().get("configuration"),"configuration").containsKey("command")?configured(context):new StudyRiskScores().execute(context);
            case "protein_structure", "protein_structure_assessment" ->
                optionalMap(context.operation().get("configuration"), "configuration").containsKey("command") ? configured(context)
                    : new StudyProteinStructures().execute(context);
            case "fine_mapping", "colocalization",
                    "mendelian_randomization" -> genetic(context);
            default -> throw new IllegalArgumentException(
                "Unsupported study operation: " + type);
        }; } catch (StudyPrimaryResults.Unavailable unavailable) { return StudyPrimaryResults.waiting(context, unavailable); }
    }

    private Result harmonize(Context context) throws IOException {
        Map<String, Map<String, Object>> datasets = datasets(context.study());
        List<String> selected = strings(context.operation().get("datasets"));
        List<String> outputs = new ArrayList<>();
        Map<String, Path> standardized = new LinkedHashMap<>();
        Map<String, Standardization> summaries = new LinkedHashMap<>();
        Map<String, Object> hypothesis = hypothesis(context.study(),
            text(context.operation().get("hypothesis")));
        Map<String, Object> analysis = map(
            hypothesis.get("analysis"), "hypothesis.analysis");
        Map<String, Object> harmonization = optionalMap(
            analysis.get("harmonization"), "analysis.harmonization");
        var referenceRows = new ArrayList<String[]>();
        String invalidRows = text(harmonization.getOrDefault(
            "invalid_rows", "error")).toLowerCase(Locale.ROOT);
        if (!Set.of("error", "exclude").contains(invalidRows))
            throw new IllegalArgumentException(
                "analysis.harmonization.invalid_rows must be error or exclude");
        String commonUniverse = text(harmonization.getOrDefault(
            "common_universe", "none")).toLowerCase(Locale.ROOT);
        if (!Set.of("none", "intersection").contains(commonUniverse))
            throw new IllegalArgumentException(
                "analysis.harmonization.common_universe must be none or intersection");
        Set<String> exactCommon = null;
        Set<String> validCommon = null;
        StringBuilder table = new StringBuilder(
            "dataset_id\tfile_role\tpath\tsha256\n");
        for (String id : selected) {
            Map<String, Object> dataset = required(datasets, id, "dataset");
            Map<String, Object> declaredFiles = map(dataset.get("files"), "dataset.files");
            Map<String, Object> files = org.jlinalg.study.StudyStageInputs.intakeFiles(dataset);
            if (org.jlinalg.study.StudyAnalysisReferences.isReference(dataset)) {
                if (!declaredFiles.isEmpty()) throw new IOException("An analysis reference cannot contain cohort data files");
                referenceRows.add(new String[]{id,text(dataset.get("supplied_result_id")),String.join(",",StudyPoolingAudit.cohorts(dataset)),"metadata_only_no_cohort_estimates"});
            }
            for (Map.Entry<String, Object> file : files.entrySet()) {
                Path path = context.project().resolve(text(file.getValue())).normalize();
                if (!Files.isRegularFile(path))
                    throw new IOException("Dataset file is absent: " + path);
                table.append(id).append('\t').append(file.getKey()).append('\t')
                    .append(portable(context.project(), path)).append('\t')
                    .append(FollowupSupport.hash(path)).append('\n');
            }
            String stage = text(dataset.get("stage"));
            if (stage.endsWith("_results") && !Set.of("association_results", "annotated_results").contains(stage))
                return waiting(context, "Dataset stage " + stage + " describes a completed analysis, not per-cohort association estimates",
                    Map.of("required_input", "Declare this scoped result in analysis.supplied_results with its kind, files, method, scope and testing family; it must not be normalized as another cohort"));
            if (stage.endsWith("_results")) {
                Path result = context.project().resolve(
                    text(files.get("results"))).normalize();
                Path standard = context.outputDirectory()
                    .resolve("standard-results").resolve(id + ".tsv");
                Files.createDirectories(standard.getParent());
                Standardization summary = standardize(result,
                    optionalMap(dataset.get("columns"), "dataset.columns"),
                    standard, invalidRows.equals("exclude"));
                outputs.addAll(normalizationOutputs(context, standard));
                summaries.put(id, summary);
                standardized.put(id, standard);
                if (exactCommon == null) {
                    exactCommon = new LinkedHashSet<>(summary.allFeatures());
                    validCommon = new LinkedHashSet<>(summary.eligibleFeatures());
                } else {
                    exactCommon.retainAll(summary.allFeatures());
                    validCommon.retainAll(summary.eligibleFeatures());
                }
                outputs.add("standard-results/" + id + ".tsv");
            }
        }
        StudyEffectQc.Result effectQc = StudyEffectQc.apply(standardized, harmonization, context.outputDirectory());
        outputs.addAll(effectQc.outputs());
        // QC precedes the common universe, all multiplicity adjustment and follow-up.
        validCommon = null;
        for (Path standard : standardized.values()) {
            Set<String> retained = new LinkedHashSet<>(estimates(standard).keySet());
            if (validCommon == null) validCommon = retained;
            else validCommon.retainAll(retained);
        }
        if (commonUniverse.equals("intersection")) {
            Path individual = context.outputDirectory().resolve("post-effect-qc-results");
            Files.createDirectories(individual);
            for (var entry : standardized.entrySet()) {
                Path snapshot = individual.resolve(entry.getKey() + ".tsv");
                Files.copy(entry.getValue(), snapshot);
                outputs.add("post-effect-qc-results/" + snapshot.getFileName());
                retainFeatures(entry.getValue(), validCommon == null ? Set.of() : validCommon);
            }
        }
        Files.writeString(context.outputDirectory().resolve("inputs.tsv"),
            table, StandardCharsets.UTF_8);
        outputs.add("inputs.tsv");
        if (!referenceRows.isEmpty()) {
            FollowupSupport.table(context.outputDirectory().resolve("analysis-references.tsv"),
                List.of("dataset_id","supplied_result_id","cohorts","disposition"),referenceRows);
            outputs.add("analysis-references.tsv");
        }
        if (!summaries.isEmpty()) {
            StringBuilder summary = new StringBuilder(
                "dataset_id\tinput_rows\teligible_rows\texcluded_invalid"
                    + "\texcluded_status\tretained_common\n");
            for (Map.Entry<String, Standardization> entry : summaries.entrySet()) {
                Standardization value = entry.getValue();
                summary.append(entry.getKey()).append('\t')
                    .append(value.inputRows()).append('\t')
                    .append(value.eligibleFeatures().size()).append('\t')
                    .append(value.excludedInvalid()).append('\t')
                    .append(value.excludedStatus()).append('\t')
                    .append(DelimitedData.readResult(standardized.get(entry.getKey())).rows().size())
                    .append('\n');
            }
            summary.append("# exact_common_features\t")
                .append(exactCommon == null ? 0 : exactCommon.size()).append('\n')
                .append("# strict_common_features\t")
                .append(validCommon == null ? 0 : validCommon.size()).append('\n')
                .append("# common_universe_policy\t").append(commonUniverse)
                .append('\n');
            Files.writeString(context.outputDirectory()
                .resolve("harmonization-summary.tsv"), summary,
                StandardCharsets.UTF_8);
            outputs.add("harmonization-summary.tsv");
        }
        outputs.addAll(StudyFeatureAnnotations.write(context, standardized));
        boolean retryable;
        try (var reader = Files.newBufferedReader(context.outputDirectory().resolve("annotation-summary.yaml"))) {
            retryable = list(map(StudyYaml.load(reader), "annotation summary").get("input_requests"), "input_requests")
                .stream().anyMatch(item -> Boolean.TRUE.equals(map(item, "request").get("retryable")));
        }
        return new Result(State.COMPLETE, "Validated and hashed " + selected.size()
            + " dataset inputs", outputs, Map.of("retryable", retryable,
                "effect_outlier_qc", effectQc.document(), "method_report", effectQc.document()));
    }

    private Result association(Context context) throws IOException {
        String datasetId = text(context.operation().get("dataset_id"));
        Map<String, Object> dataset = required(
            datasets(context.study()), datasetId, "dataset");
        Map<String, Object> hypothesis = hypothesis(context.study(),
            text(context.operation().get("hypothesis")));
        String datasetType = text(dataset.get("type")).toLowerCase(Locale.ROOT);
        if (!supportsBulkAssociation(datasetType))
            return waiting(context, "No automatic association adapter is registered for "
                + datasetType + "; configure a modality-specific operation");
        Map<String, Object> files = map(dataset.get("files"), "dataset.files");
        Map<String, Object> columns = optionalMap(
            dataset.get("columns"), "dataset.columns");
        Map<String, Object> analysis = map(
            hypothesis.get("analysis"), "hypothesis.analysis");
        Map<String, Object> options = optionalMap(
            analysis.get("association"), "analysis.association");
        org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(options);
        if (StudyPublicEvidence.text(options.get("model")).isBlank())
            return new StudyModelReview().execute(context, hypothesis, dataset);
        if ("cox".equals(options.get("model"))) return new StudySurvivalAssociation().execute(context,dataset,hypothesis,options);
        if (!files.containsKey("phenotype"))
            return waiting(context,
                "Matrix association requires files.phenotype");
        if (hypothesis.get("outcome") == null)
            return waiting(context,
                "Matrix association requires hypothesis.outcome");
        Path matrix = StudyAssayQcSources.matrix(context,dataset).normalize();
        Path phenotype = context.project().resolve(
            text(files.get("phenotype"))).normalize();
        Path selectedPhenotype = dependencyFile(context, "analysis-phenotype.tsv");
        if (selectedPhenotype != null) phenotype = selectedPhenotype;
        else if (Boolean.TRUE.equals(context.operation().get("requires_prefit_qc")))
            return waiting(context, "Participant QC must produce the analyzed phenotype table before association");
        if (!Files.isRegularFile(matrix) || !Files.isRegularFile(phenotype))
            throw new IOException("Association requires existing matrix and phenotype files");

        String outcomeKey = text(hypothesis.get("outcome"));
        Map<String, Object> phenotypes = optionalMap(
            columns.get("phenotypes"), "columns.phenotypes");
        String outcome = phenotypes.containsKey(outcomeKey)
            ? text(phenotypes.get(outcomeKey)) : outcomeKey;
        String id = text(columns.getOrDefault("sample_id", "id"));
        Map<String, Object> covariateColumns = optionalMap(
            columns.get("covariates"), "columns.covariates");
        List<String> covariates = new ArrayList<>();
        for (String logical : strings(hypothesis.get("covariates")))
            covariates.add(text(covariateColumns.getOrDefault(logical, logical)));
        String offset=options.containsKey("offset_column")?text(covariateColumns.getOrDefault(text(options.get("offset_column")),options.get("offset_column"))):"";
        String exactStrata=options.containsKey("exact_strata_column")?text(covariateColumns.getOrDefault(text(options.get("exact_strata_column")),options.get("exact_strata_column"))):"";
        if(!exactStrata.isEmpty()&&!covariates.contains(exactStrata))covariates.add(exactStrata);
        if(!offset.isEmpty()&&covariates.contains(offset))throw new IOException("Declare the offset separately from estimated covariates");
        String formula = outcome + " ~ "
            + (covariates.isEmpty() ? "" : String.join(" + ", covariates) + " + ")
            + (offset.isEmpty()?"":"offset("+offset+") + ")
            + "<omics>";
        String model = text(options.get("model"));
        Path raw = context.outputDirectory().resolve("association.tsv");
        List<String> arguments = new ArrayList<>(List.of(
            "--pheno", phenotype.toString(), "--omics", matrix.toString(),
            "--id", id, "--formula", formula, "--model", model,
            "--omics-type", omicsType(datasetType),
            "--out", raw.toString(), "--threads",
            text(options.getOrDefault("threads", "1")), "--no-log",
            "--no-config"));
        if (options.get("family") != null)
            arguments.addAll(List.of("--family", text(options.get("family"))));
        if (options.get("link") != null)
            arguments.addAll(List.of("--link", text(options.get("link"))));
        boolean genotype = Set.of("gwas", "genomics").contains(datasetType);
        if(!genotype&&Set.of("firth-lrt","exact-conditional").contains(String.valueOf(options.get("binary_test"))))throw new IOException("The registered Firth/exact study adapter requires participant-level genotype data");
        if (genotype) {
            var humanPloidy=org.jlinalg.study.StudyHumanPloidyPolicy.resolve(options);org.jlinalg.study.StudyHumanPloidyPolicy.dataset(dataset,humanPloidy);
            if(!humanPloidy.isEmpty())arguments.addAll(List.of("--human-ploidy-region",text(humanPloidy.get("region")),"--human-ploidy-build",text(humanPloidy.get("genome_build")),"--haploid-source-scale",text(humanPloidy.get("haploid_source_scale")),"--invalid-ploidy-calls",text(humanPloidy.get("invalid_calls")),"--unknown-ploidy-samples",text(humanPloidy.get("unknown_samples")),"--ploidy-samples",context.project().resolve(text(files.get("ploidy_samples"))).normalize().toString()));
            arguments.addAll(List.of("--min-mac", text(options.getOrDefault("minimum_mac", 20)),
                "--max-marker-missing", text(options.getOrDefault("maximum_marker_missing", 0.02))));
            Map<String, String> switches = Map.of("minimum_maf", "--min-maf", "maximum_maf", "--max-maf",
                "maximum_mac", "--max-mac", "minimum_info", "--min-info", "minimum_hwe_p", "--min-hwe-p",
                "hwe_filter_scope", "--hwe-filter-scope");
            for (var option : switches.entrySet()) if (options.containsKey(option.getKey()))
                arguments.addAll(List.of(option.getValue(), text(options.get(option.getKey()))));
            for(var option:Map.of("case_value","--case-value","control_value","--control-value","genotype_kind","--genotype-kind","minimum_missingness_p","--min-missingness-p","missingness_max_states","--missingness-max-states").entrySet())if(options.containsKey(option.getKey()))arguments.addAll(List.of(option.getValue(),text(options.get(option.getKey()))));
            if("human_autosomes".equals(options.get("chromosome_scope")))arguments.add("--autosomes-only");
            if(files.containsKey("hwe_samples"))arguments.addAll(List.of("--hwe-sample-file",context.project().resolve(text(files.get("hwe_samples"))).normalize().toString()));
            for(var option:Map.of("binary_test","--binary-test","firth_maximum_iterations","--firth-max-iterations","firth_tolerance","--firth-tolerance","exact_maximum_states","--exact-max-states","exact_maximum_work","--exact-max-work","exact_effect_maximum_parameters","--exact-effect-max-parameters").entrySet())if(options.containsKey(option.getKey()))arguments.addAll(List.of(option.getValue(),options.get(option.getKey()) instanceof Number n&&!option.getKey().equals("firth_tolerance")?Long.toString(n.longValue()):text(options.get(option.getKey()))));
            if(!exactStrata.isEmpty())arguments.addAll(List.of("--exact-strata-column",exactStrata));
        }
        for (String role : List.of("grm", "pedigree")) if (files.containsKey(role))
            arguments.addAll(List.of("--" + role, context.project().resolve(text(files.get(role))).normalize().toString()));
        if (files.containsKey("sample") || files.containsKey("samples")) {
            String role = files.containsKey("sample") ? "sample" : "samples";
            arguments.addAll(List.of("--sample-file", context.project().resolve(text(files.get(role))).normalize().toString()));
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = JLinAlgCli.run(arguments.toArray(String[]::new),
            new PrintStream(stdout), new PrintStream(stderr));
        Files.writeString(context.outputDirectory().resolve("console.txt"),
            stdout.toString(StandardCharsets.UTF_8)
                + stderr.toString(StandardCharsets.UTF_8));
        if (status != 0)
            throw new IOException("Association command failed: "
                + stderr.toString(StandardCharsets.UTF_8).trim());
        Path standard = context.outputDirectory()
            .resolve("standard-results").resolve(datasetId + ".tsv");
        Files.createDirectories(standard.getParent());
        var safeMetadata=new LinkedHashMap<String,String>();
        if(genotype)for(String label:List.of("hwe_method","hwe_filter_method","hwe_filter_scope","missingness_method","genotype_origin","ploidy_status","test_method","calibration","estimator","standard_error_method","statistic_type","convergence_reason","result_type","effect_estimation_status","exact_status","exact_reason","exact_alternative"))safeMetadata.put(label,label);
        if(genotype&&"exact-conditional".equals(options.get("binary_test"))) {
            safeMetadata.put("n","test_n_samples");safeMetadata.put("n_cases","test_n_cases");safeMetadata.put("n_controls","test_n_controls");
            safeMetadata.put("qc_n_samples","n_samples");safeMetadata.put("qc_n_cases","n_cases");safeMetadata.put("qc_n_controls","n_controls");
        }
        standardize(raw, Map.of("feature_id", "id", "effect", "beta",
            "standard_error", "standard_error", "p_value", "p_value",
            "status", "status", "result_metadata",safeMetadata), standard, false);
        List<String> outputs = new ArrayList<>(List.of("association.tsv",
            "standard-results/" + datasetId + ".tsv", "console.txt"));
        var humanPloidy=org.jlinalg.study.StudyHumanPloidyPolicy.resolve(options);
        if(!humanPloidy.isEmpty())for(String suffix:List.of(".human-ploidy.yaml",".human-ploidy-samples.tsv",".human-ploidy-variants.tsv",".human-ploidy-invalid-calls.tsv"))outputs.add("association.tsv"+suffix);
        outputs.addAll(normalizationOutputs(context, standard));
        StudyEffectQc.Result effectQc = StudyEffectQc.apply(Map.of(datasetId, standard),
            optionalMap(analysis.get("harmonization"), "analysis.harmonization"), context.outputDirectory());
        outputs.addAll(effectQc.outputs());
        outputs.addAll(StudyFeatureAnnotations.write(context, Map.of(datasetId, standard)));
        var method = new LinkedHashMap<>(StudyMethods.record(model, Map.of("dataset", datasetId, "formula", formula, "model", model,
            "covariates", covariates, "offset_column",offset,"command_arguments", arguments, "threads", options.getOrDefault("threads", "1")),
            StudyPublicEvidence.list(options.get("references")), options.get("rationale"),
            model.equals("ols") ? List.of("linear regression") : List.of(model, "regression")));
        method.put("effect_outlier_qc", effectQc.document());
        if(!humanPloidy.isEmpty()){method.put("human_ploidy",humanPloidy);method.put("human_ploidy_interpretation",org.jlinalg.study.StudyHumanPloidyPolicy.profile(humanPloidy).interpretation());method.put("human_ploidy_audit","association.tsv.human-ploidy.yaml");}
        if(genotype&&"firth-lrt".equals(options.get("binary_test"))) {
            method.put("method","Firth Jeffreys-penalized Bernoulli logit with full-design constrained likelihood-ratio inference");
            method.put("literature_terms",List.of("Firth 1993 bias reduction maximum likelihood","Heinze Schemper logistic regression separation 2002"));
        }
        boolean exact=genotype&&"exact-conditional".equals(options.get("binary_test"));
        int unresolvedExact=0;
        if(exact){
            method.put("method","Finite conditional additive hard-call genotype score test with optional separate approximate Firth effects");
            method.put("literature_terms",List.of("exact conditional logistic inference fixed stratum case counts","finite conditional score probability ordering"));
            method.put("exact_controls",Map.of("maximum_states",options.getOrDefault("exact_maximum_states",200000),"maximum_work",options.getOrDefault("exact_maximum_work",50000000),"effect_maximum_parameters",options.getOrDefault("exact_effect_maximum_parameters",50),"probability_ordering_relative_tolerance",1e-7,"strata_column",exactStrata,"missing_genotypes","omitted before variant-specific case margins; not imputed"));
            var rawTable=DelimitedData.readResult(raw);int exactStatus=rawTable.column("exact_status");
            for(String[] row:rawTable.rows())if(Set.of("budget_exceeded","numerical_unavailable").contains(row[exactStatus]))unresolvedExact++;
        }
        if(genotype) {
            var rawTable=DelimitedData.readResult(raw);var dispositions=new java.util.TreeMap<String,Integer>();
            for(String[] row:rawTable.rows())dispositions.merge(row[rawTable.column("status")],1,Integer::sum);
            method.put("genotype_qc",Map.of("performed",true,"variant_dispositions",dispositions,"full_qc_table","association.tsv",
                "count_scope","Trait/covariate complete analyzed samples, before marker mean imputation; group MAC uses the same cohort-minor allele",
                "hwe_scope",options.getOrDefault("hwe_filter_scope","all"),"hwe_reference",files.getOrDefault("hwe_samples","aligned analyzed samples"),
                "requested_unavailable_qc","Retained as an explicit variant exclusion; no passing P value substituted",
                "calibration_limit","QC does not establish sparse/imbalanced binary, relatedness, ancestry or sex/PAR model calibration; the fitted estimator and inferential method require the registered design"));
        }
        int retainedTests = DelimitedData.readResult(standard).rows().size();
        var retainedTable=DelimitedData.readResult(standard);
        long pOnly=retainedTable.rows().stream().filter(row->StudyResultShape.pOnly(retainedTable,row)).count();
        method.put("retained_p_value_only_tests",pOnly);method.put("retained_signed_effects",retainedTests-pOnly);
        method.put("retained_estimates", retainedTests);
        if(exact)method.put("binary_inference",Map.of("registered_test","exact-conditional","null_conditioning","Independent Bernoulli observations, declared stratum case counts, fixed known offsets, observed integer 0/1/2 hard calls","p_value","Finite conditional ALT case-allele score distribution; two-sided probability ordering, no mid-P or chi-square approximation","effect","Optional unconditional Firth stratum logit coefficient and ordinary-Fisher SE are separate approximate estimates; their failure retains the exact P value","sample_scope","n/n_cases/n_controls describe called test samples; qc_n_* and called/missing describe source QC before genotype omission"));
        else if(genotype)method.put("binary_inference",Map.of("registered_test",options.getOrDefault("binary_test","efficient-score"),
            "firth_scope","Independent individual Bernoulli logit, unit trial weights; full and constrained fits retain the full-design Jeffreys penalty",
            "firth_p_value","Penalized likelihood-ratio chi-square 1df asymptotic tail; not a Wald P value or finite-sample/relatedness guarantee",
            "firth_standard_error","Inverse ordinary Fisher information at the penalized estimate; meta-analysis Wald inference is a separate approximation"));
        if(genotype&&Set.of("firth-lrt","exact-conditional").contains(String.valueOf(options.get("binary_test"))))method.put("firth_controls",Map.of(
            "maximum_iterations",options.getOrDefault("firth_maximum_iterations",300),"tolerance",options.getOrDefault("firth_tolerance",1e-8),
            "maximum_step",5,"maximum_step_halvings",40,"column_scaling","max_abs; coefficient step/tolerance in scaled coordinates",
            "genotype_missingness",exact?"omitted before variant-specific test and optional effect fit":"analysis-sample mean imputation after QC; original called/missing counts retained","numerical_backend","portable_java_cpu"));
        if (retainedTests == 0) method.put("performed", false);
        String exactCaveat="Exact P values condition on declared stratum case counts and fixed known offsets under independent individual Bernoulli observations with observed hard calls. They do not adjust arbitrary continuous covariates or kinship. Missing genotypes are omitted before variant-specific margins. The centered ALT score direction is separate from an effect estimate. Optional Firth effects and ordinary-Fisher SE are approximations; P-only tests establish no effect magnitude or beta direction and cannot enter inverse-variance meta-analysis. Calibration requires the declared null and does not establish validity of estimated offsets, population transfer or missingness assumptions.";
        if(unresolvedExact>0){
            method.put("association_family_status","incomplete_exact_tests");method.put("unresolved_exact_tests",unresolvedExact);method.put("primary_selection_eligible",false);
            var pending=waiting(context,"Exact association family is incomplete; resolve resource/numerical failures before official FDR selection",Map.of("unresolved_exact_tests",unresolvedExact,"bh_suppressed",true),List.of(StudyNeed.review("exact-tests:"+datasetId,"Resolve exact conditional test budgets or numerical inputs and rerun the complete family","Retained partial P values cannot substitute for the registered complete testing family. Increase exact resource bounds only when feasible or register a reviewed alternative method; no approximate fallback was performed.",true)));
            outputs.addAll(pending.outputs());var details=new LinkedHashMap<String,Object>(pending.details());details.put("method_report",method);details.put("effect_outlier_qc",effectQc.document());details.put("required_manuscript_caveat",exactCaveat);details.put("primary_selection_eligible",false);details.put("evidence_disposition","incomplete_test_family");
            return new Result(State.WAITING_FOR_INPUT,pending.summary(),outputs,details);
        }
        return new Result(State.COMPLETE, retainedTests == 0 ? "No estimable association rows remained after declared QC for " + datasetId
            : "Fitted association model for " + datasetId, outputs,
            Map.of("method_report", method, "effect_outlier_qc", effectQc.document(),
                "required_manuscript_caveat",exact?exactCaveat:genotype?"Genotype QC and source ploidy metadata do not establish ancestry/kinship correctness or biological sex/PAR interpretation. The native estimator/test route is "+model+"/"+options.getOrDefault("family","gaussian")+"/"+options.getOrDefault("binary_test","efficient-score")+". "+("firth-lrt".equals(options.get("binary_test"))?"Firth uses full/constrained Jeffreys penalized fits for independent Bernoulli logit observations. Its chi-square LRT P value is not a Wald test; its ordinary-Fisher SE and subsequent meta-analysis Wald inference are separate approximations. No finite-sample tail, relatedness or new-population calibration guarantee is inferred.":"No Firth/SPA correction or specialized binary mixed-model calibration is supplied by this configured route.")+" Case/control MAC uses one cohort-minor allele. HWE and differential missingness are QC statistics, not phenotype association evidence.":"",
                "evidence_disposition", retainedTests == 0 ? "not_estimable" : "estimable"));
    }

    private Result replication(Context context) throws IOException {
        return replication(context, results(context));
    }

    private Result replication(Context context, Map<String, Path> results)
            throws IOException {
        return replication(context, results, null);
    }

    Result replication(Context context, Map<String, Path> results, Path suppliedDiscovery)
            throws IOException {
        var poolingAudit = StudyPoolingAudit.assess(context, results, true);
        if (!poolingAudit.needs().isEmpty()) return poolingAudit.waiting();
        Map<String, Object> roles = map(
            context.operation().get("roles"), "operation.roles");
        List<String> discovery = role(roles, "discovery");
        List<String> replication = role(roles, "replication");
        if (discovery.isEmpty() || replication.isEmpty())
            return waiting(context,
                "Discovery-replication comparison requires both discovery and replication roles");
        List<String> originalDiscovery = List.copyOf(discovery);
        StudyPrimaryResults.Selection frozenDiscovery = null;
        StudyDiscoveryScope.Audit discoveryScope = null;
        if (suppliedDiscovery != null || Boolean.TRUE.equals(context.operation().get("pooled_discovery"))) {
            Path pooled = suppliedDiscovery == null ? dependencyFile(context, "discovery-results.tsv") : suppliedDiscovery;
            if (pooled == null) return waiting(context, "Discovery-only meta-analysis must complete before validation selection");
            if (suppliedDiscovery == null) {
                frozenDiscovery = StudyPrimaryResults.discovery(context, pooled);
                discoveryScope = StudyDiscoveryScope.assess(context, frozenDiscovery, results, originalDiscovery, replication);
                if (!discoveryScope.needs().isEmpty()) return discoveryScope.waiting();
            }
            results = new LinkedHashMap<>(results);
            results.put("__pooled_discovery__", pooled);
            discovery = List.of("__pooled_discovery__");
        }
        Map<String, Object> hypothesis = hypothesis(context.study(),
            text(context.operation().get("hypothesis")));
        Map<String, Object> analysis = map(
            hypothesis.get("analysis"), "hypothesis.analysis");
        Map<String, Object> testing = optionalMap(
            analysis.get("multiple_testing"), "analysis.multiple_testing");
        String method = text(testing.getOrDefault("method", "BH"))
            .toUpperCase(Locale.ROOT);
        if (!Set.of("BH", "BY", "NONE").contains(method))
            throw new IllegalArgumentException(
                "Discovery-replication supports declared multiple_testing method BH, BY or NONE");
        double familyAlpha = number(testing.getOrDefault("alpha", 0.05),
            "multiple_testing.alpha");
        double discoveryAlpha = number(
            analysis.getOrDefault("discovery_alpha", familyAlpha),
            "discovery_alpha");
        double replicationAlpha = number(
            analysis.getOrDefault("replication_alpha", familyAlpha),
            "replication_alpha");
        Map<String, Object> replicationOptions = optionalMap(
            analysis.get("replication"), "analysis.replication");
        String adjustmentScope = text(replicationOptions.getOrDefault(
            "adjustment_scope", "all_results")).toLowerCase(Locale.ROOT);
        if (!Set.of("all_results", "discovery_subset").contains(adjustmentScope))
            throw new IllegalArgumentException(
                "analysis.replication.adjustment_scope must be all_results or discovery_subset");
        boolean requireSameSign = booleanValue(replicationOptions.getOrDefault(
            "require_same_sign", Boolean.TRUE),
            "analysis.replication.require_same_sign");
        String thresholdComparison = text(replicationOptions.getOrDefault(
            "threshold_comparison", "less_than_or_equal"))
            .toLowerCase(Locale.ROOT);
        if (!Set.of("less_than", "less_than_or_equal")
                .contains(thresholdComparison))
            throw new IllegalArgumentException(
                "analysis.replication.threshold_comparison must be less_than or less_than_or_equal");
        boolean strictThreshold = thresholdComparison.equals("less_than");
        probability(discoveryAlpha, "discovery_alpha");
        probability(replicationAlpha, "replication_alpha");
        Path output = context.outputDirectory().resolve("replication.tsv");
        Path vennDirectory = context.outputDirectory().resolve("replication-venn");
        Files.createDirectories(vennDirectory);
        List<String> outputs = new ArrayList<>(List.of("replication.tsv", "pooling-audit.yaml", "pooling-compatibility.tsv", "pooling-variant-compatibility.tsv"));
        StringBuilder vennSummary = new StringBuilder(
            "discovery_dataset\treplication_dataset\tdiscovery_only"
                + "\treplicated_overlap\treplication_criterion_only"
                + "\tdiscovery_set_size\treplication_criterion_set_size"
                + "\tadjustment_scope\trequire_same_sign\n");
        int vennIndex = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("feature_id\tdiscovery_dataset\treplication_dataset"
                + "\tdiscovery_beta\tdiscovery_p\tdiscovery_adjusted_p"
                + "\treplication_beta\treplication_p\treplication_adjusted_p"
                + "\tdiscovery_selected\tsame_sign\treplicated\n");
            for (String discoveryId : discovery) {
                Map<String, Estimate> left;
                Map<String, Double> leftAdjusted;
                if (frozenDiscovery != null) {
                    left = new LinkedHashMap<>();
                    var source = frozenDiscovery.table();
                    for (String[] row : source.rows()) {
                        String id = row[source.column("feature_id")];
                        if (!frozenDiscovery.probabilities().containsKey(id)) continue;
                        boolean pOnly = StudyResultShape.pOnly(source, row);
                        left.put(id, new Estimate(pOnly ? Double.NaN : finite(row[source.column("beta")], "beta", frozenDiscovery.source().path()),
                            pOnly ? Double.NaN : finite(row[source.column("se")], "se", frozenDiscovery.source().path()), frozenDiscovery.probabilities().get(id)));
                    }
                    leftAdjusted = frozenDiscovery.adjusted();
                } else {
                    left = estimates(required(results, discoveryId, "harmonized result"));
                    leftAdjusted = adjusted(left, method);
                }
                Set<String> selected = new LinkedHashSet<>();
                for (Map.Entry<String, Double> value : leftAdjusted.entrySet())
                    if (significant(value.getValue(), discoveryAlpha,
                            strictThreshold))
                        selected.add(value.getKey());
                for (String replicationId : replication) {
                    Map<String, Estimate> right = estimates(required(
                        results, replicationId, "harmonized result"));
                    Map<String, Estimate> adjustmentFamily = right;
                    if (adjustmentScope.equals("discovery_subset")) {
                        adjustmentFamily = new LinkedHashMap<>();
                        for (String feature : selected) {
                            Estimate estimate = right.get(feature);
                            if (estimate != null)
                                adjustmentFamily.put(feature, estimate);
                        }
                    }
                    Map<String, Double> rightAdjusted = adjusted(
                        adjustmentFamily, method);
                    Set<String> replicationCriterion = new LinkedHashSet<>();
                    Set<String> replicatedFeatures = new LinkedHashSet<>();
                    for (Map.Entry<String, Estimate> row : left.entrySet()) {
                        Estimate other = right.get(row.getKey());
                        if (other == null) continue;
                        Estimate first = row.getValue();
                        double discoveryQ = leftAdjusted.get(row.getKey());
                        double replicationQ = rightAdjusted.getOrDefault(
                            row.getKey(), Double.NaN);
                        boolean discoverySelected = significant(discoveryQ,
                            discoveryAlpha, strictThreshold);
                        boolean signed=Double.isFinite(first.beta)&&Double.isFinite(other.beta);
                        boolean same = signed&&Math.signum(first.beta)
                            == Math.signum(other.beta);
                        boolean replicationSelected = significant(replicationQ,
                            replicationAlpha, strictThreshold);
                        if (replicationSelected && (!requireSameSign || same))
                            replicationCriterion.add(row.getKey());
                        boolean replicated = discoverySelected
                            && replicationSelected && (!requireSameSign || same);
                        if (replicated) replicatedFeatures.add(row.getKey());
                        writer.write(row.getKey() + "\t" + discoveryId + "\t"
                            + replicationId + "\t" + first.beta + "\t"
                            + value(first.p) + "\t" + value(discoveryQ) + "\t"
                            + other.beta + "\t" + value(other.p) + "\t"
                            + value(replicationQ) + "\t" + discoverySelected + "\t"
                            + (signed?Boolean.toString(same):"NA") + "\t" + replicated + "\n");
                    }
                    Set<String> discoveryOnly = new LinkedHashSet<>(selected);
                    discoveryOnly.removeAll(replicationCriterion);
                    Set<String> replicationOnly = new LinkedHashSet<>(replicationCriterion);
                    replicationOnly.removeAll(selected);
                    vennSummary.append(discoveryId).append('\t')
                        .append(replicationId).append('\t')
                        .append(discoveryOnly.size()).append('\t')
                        .append(replicatedFeatures.size()).append('\t')
                        .append(replicationOnly.size()).append('\t')
                        .append(selected.size()).append('\t')
                        .append(replicationCriterion.size()).append('\t')
                        .append(adjustmentScope).append('\t')
                        .append(requireSameSign).append('\n');
                    if (!selected.isEmpty() || !replicationCriterion.isEmpty()) {
                        String stem = String.format(Locale.ROOT, "%02d-%s-vs-%s",
                            ++vennIndex, fileToken(discoveryId),
                            fileToken(replicationId));
                        PlotOptions plotOptions = PlotOptions.builder().size(850, 620)
                            .title("Discovery and replication overlap")
                            .build();
                        var venn = DiagramPlots.venn(new DiagramPlots.VennData(
                            discoveryId + " selected", replicationId
                                + " replication criterion",
                            discoveryOnly.size(), replicatedFeatures.size(),
                            replicationOnly.size()), plotOptions);
                        Path png = vennDirectory.resolve(stem + ".png");
                        Path pdf = vennDirectory.resolve(stem + ".pdf");
                        venn.save(png);
                        venn.save(pdf);
                        outputs.add(context.outputDirectory().relativize(png)
                            .toString().replace('\\', '/'));
                        outputs.add(context.outputDirectory().relativize(pdf)
                            .toString().replace('\\', '/'));
                    }
                }
            }
        }
        Files.writeString(context.outputDirectory().resolve("replication-venn.tsv"),
            vennSummary, StandardCharsets.UTF_8);
        outputs.add(1, "replication-venn.tsv");
        Map<String,Object> details = new LinkedHashMap<>();
        details.put("discovery_datasets", originalDiscovery); details.put("replication_datasets", replication);
        details.put("discovery_selection", discovery); details.put("adjustment_scope", adjustmentScope);
        details.put("discovery_alpha", discoveryAlpha); details.put("replication_alpha", replicationAlpha);
        details.put("replication_adjustment", method);
        details.put("threshold_comparison", thresholdComparison); details.put("validation_used_for_discovery_pool", false);
        details.put("direction_scope", "P-only pairs retain P-based selection but have same_sign=NA; a required sign-concordance rule cannot be satisfied by unavailable effects");
        if (frozenDiscovery != null) {
            details.put("frozen_discovery_selection", frozenDiscovery.receipt());
            details.put("required_manuscript_caveat", frozenDiscovery.receipt().get("required_manuscript_caveat"));
            Map<String,Object> methodReport = new LinkedHashMap<>();
            methodReport.put("performed", true);
            methodReport.put("discovery_selection_origin", frozenDiscovery.receipt().get("adjusted_values_origin"));
            methodReport.put("discovery_source_sha256", frozenDiscovery.source().sha256());
            methodReport.put("discovery_adjustment", frozenDiscovery.adjustment());
            methodReport.put("discovery_testing_denominator", frozenDiscovery.receipt().get("testing_denominator"));
            methodReport.put("discovery_family_coverage", frozenDiscovery.family().getOrDefault("coverage", "complete_observed_input"));
            methodReport.put("discovery_alpha", discoveryAlpha);
            methodReport.put("validation_adjustment", method); methodReport.put("validation_alpha", replicationAlpha);
            methodReport.put("validation_family_scope", adjustmentScope);
            methodReport.put("source_analysis_reproduced", false);
            if (discoveryScope != null) {
                methodReport.put("discovery_scope_status", discoveryScope.receipt().get("status"));
                methodReport.put("source_scope_checked", discoveryScope.receipt().get("external_scope_checked"));
                methodReport.put("shared_variant_rows_checked", discoveryScope.receipt().get("shared_variant_rows"));
                methodReport.put("variant_coding_fields_checked", discoveryScope.receipt().get("compared_variant_fields"));
                details.put("required_manuscript_caveat", details.get("required_manuscript_caveat") + " " + discoveryScope.receipt().get("required_manuscript_caveat"));
                outputs.add("discovery-scope-audit.yaml"); outputs.add("discovery-scope-compatibility.tsv");
            }
            details.put("method_report", methodReport);
            outputs.add("primary-result-selection.yaml");
        }
        return new Result(State.COMPLETE, "Compared frozen discovery and replication effects and generated overlap diagrams",
            outputs, details);
    }

    private Result effectComparison(Context context) throws IOException {
        return effectComparison(context, results(context));
    }

    Result effectComparison(Context context, Map<String, Path> resultPaths) throws IOException {
        if (resultPaths.size() < 2)
            return new Result(State.NOT_APPLICABLE,
                "Effect comparison requires at least two result tables",
                List.of(), Map.of("available_result_tables", resultPaths.size()));
        Map<String, Map<String, Object>> described = datasets(context.study());
        List<String> ids = new ArrayList<>(resultPaths.keySet());
        List<String> outputs = new ArrayList<>();
        List<Object> comparisons = new ArrayList<>();
        Path plotDirectory = context.outputDirectory().resolve("effect-scatterplots");
        Files.createDirectories(plotDirectory);
        StringBuilder summary = new StringBuilder(
            "left_dataset\tright_dataset\tleft_cohort\tright_cohort"
                + "\tcomparison_type\tcommon_features\tpearson_r"
                + "\tpearson_r_display\n");
        int plotIndex = 0;
        for (int leftIndex = 0; leftIndex < ids.size(); leftIndex++) {
            String leftId = ids.get(leftIndex);
            Map<String, Estimate> left = estimates(resultPaths.get(leftId));
            for (int rightIndex = leftIndex + 1; rightIndex < ids.size(); rightIndex++) {
                String rightId = ids.get(rightIndex);
                Map<String, Estimate> right = estimates(resultPaths.get(rightId));
                List<Double> xValues = new ArrayList<>(), yValues = new ArrayList<>();
                Moment moment = new Moment();
                for (Map.Entry<String, Estimate> entry : left.entrySet()) {
                    Estimate other = right.get(entry.getKey());
                    if (other == null||!Double.isFinite(entry.getValue().beta)||!Double.isFinite(other.beta)) continue;
                    xValues.add(entry.getValue().beta);
                    yValues.add(other.beta);
                    moment.add(entry.getValue().beta, other.beta, false, false);
                }
                String leftCohort = cohort(described.get(leftId));
                String rightCohort = cohort(described.get(rightId));
                String comparisonType = leftCohort.equals(rightCohort)
                    ? "within_cohort" : "cross_cohort";
                double correlation = moment.correlation();
                summary.append(leftId).append('\t').append(rightId).append('\t')
                    .append(leftCohort).append('\t').append(rightCohort).append('\t')
                    .append(comparisonType).append('\t').append(moment.n).append('\t')
                    .append(value(correlation)).append('\t')
                    .append(Double.isFinite(correlation)
                        ? String.format(Locale.ROOT, "%.4f", correlation) : "NA")
                    .append('\n');
                Map<String, Object> comparison = new LinkedHashMap<>();
                comparison.put("left_dataset", leftId);
                comparison.put("right_dataset", rightId);
                comparison.put("left_cohort", leftCohort);
                comparison.put("right_cohort", rightCohort);
                comparison.put("comparison_type", comparisonType);
                comparison.put("common_features", moment.n);
                comparison.put("effect_scope","Pairs with finite signed estimates only; P-only tests cannot establish effect correlation or concordance");
                comparison.put("pearson_r", Double.isFinite(correlation)
                    ? correlation : "NA");
                if (!xValues.isEmpty()) {
                    String stem = String.format(Locale.ROOT, "%02d-%s-vs-%s",
                        ++plotIndex, fileToken(leftId), fileToken(rightId));
                    Path png = plotDirectory.resolve(stem + ".png");
                    Path pdf = plotDirectory.resolve(stem + ".pdf");
                    double[] x = xValues.stream().mapToDouble(Double::doubleValue).toArray();
                    double[] y = yValues.stream().mapToDouble(Double::doubleValue).toArray();
                    double largest=0;for(double v:x)largest=Math.max(largest,Math.abs(v));for(double v:y)largest=Math.max(largest,Math.abs(v));
                    double displayUnit=largest>0&&(largest>=1e7||largest<1e-3)?Math.pow(10,Math.floor(Math.log10(largest))):1;
                    if(!(displayUnit>0)||!Double.isFinite(displayUnit))displayUnit=largest;
                    String unitLabel=displayUnit==1?"":" (x "+String.format(Locale.ROOT,"%.0e",displayUnit)+")";
                    if(displayUnit!=1){for(int i=0;i<x.length;i++){x[i]/=displayUnit;y[i]/=displayUnit;}}
                    comparison.put("plot_coordinate_divisor",displayUnit);comparison.put("plot_units","Displayed coordinates equal source effect divided by the common positive divisor; exported statistics and correlation retain source units");
                    double[] bounds = commonBounds(x, y);
                    String legend = "Pearson r = " + (Double.isFinite(correlation)
                        ? String.format(Locale.ROOT, "%.4f", correlation) : "NA")
                        + " (n = " + x.length + ")";
                    PlotOptions options = PlotOptions.builder().size(850, 700)
                        .title((comparisonType.equals("within_cohort")
                            ? "Within-cohort" : "Cross-cohort")
                            + " effect comparison")
                        .xLabel(leftId + " effect"+unitLabel).yLabel(rightId + " effect"+unitLabel)
                        .legend(LegendPosition.RIGHT).markerSize(7)
                        .xRange(bounds[0], bounds[1]).yRange(bounds[0], bounds[1])
                        .build();
                    var plot = Plots.xy().options(options)
                        .points(legend, x, y, new Color(0x00, 0x72, 0xB2))
                        .line("Identity (y = x)",
                            new double[] {bounds[0], bounds[1]},
                            new double[] {bounds[0], bounds[1]}, Color.DARK_GRAY)
                        .build();
                    plot.save(png);
                    plot.save(pdf);
                    String pngRelative = context.outputDirectory().relativize(png)
                        .toString().replace('\\', '/');
                    String pdfRelative = context.outputDirectory().relativize(pdf)
                        .toString().replace('\\', '/');
                    outputs.add(pngRelative);
                    outputs.add(pdfRelative);
                    comparison.put("plot_png", pngRelative);
                    comparison.put("plot_pdf", pdfRelative);
                }
                comparisons.add(comparison);
            }
        }
        Path table = context.outputDirectory().resolve("effect-comparisons.tsv");
        Files.writeString(table, summary, StandardCharsets.UTF_8);
        outputs.add(0, "effect-comparisons.tsv");
        return new Result(State.COMPLETE,
            "Generated " + comparisons.size() + " pairwise effect comparison(s)",
            outputs, Map.of("effect_comparisons", comparisons,
                "correlation_display", "Pearson correlation is shown to four decimal places in each plot legend"));
    }

    private Result meta(Context context) throws IOException {
        return meta(context, results(context));
    }

    private Result discoveryMeta(Context context) throws IOException {
        return discoveryMeta(context, results(context));
    }

    Result discoveryMeta(Context context, Map<String, Path> all) throws IOException {
        Map<String, Path> discovery = new LinkedHashMap<>();
        for (String id : strings(context.operation().get("datasets"))) {
            if (!all.containsKey(id)) return waiting(context, "Discovery result is unavailable: " + id);
            discovery.put(id, all.get(id));
        }
        if (discovery.size() < 2) return waiting(context, "Discovery pooling requires at least two discovery datasets");
        Result pooled = meta(context, discovery);
        if (pooled.state() != State.COMPLETE) return pooled;
        Path standardized = context.outputDirectory().resolve("discovery-results.tsv");
        standardize(context.outputDirectory().resolve("meta-analysis.tsv"),
            Map.of("feature_id", "feature_id", "effect", "beta", "standard_error", "se", "p_value", "p_value", "status", "status"),
            standardized, true);
        List<String> outputs = new ArrayList<>(pooled.outputs());
        outputs.add("discovery-results.tsv");
        outputs.addAll(normalizationOutputs(context, standardized));
        Map<String, Object> details = new LinkedHashMap<>(pooled.details());
        details.put("discovery_datasets", List.copyOf(discovery.keySet()));
        details.put("validation_used", false);
        details.put("scientific_role", "discovery_only");
        return new Result(State.COMPLETE, "Pooled discovery cohorts without validation estimates", outputs, details);
    }

    Result meta(Context context, Map<String, Path> results) throws IOException {
        if (results.isEmpty())
            return waiting(context, "Meta-analysis requires association results");
        for(var source:results.entrySet()){var table=DelimitedData.readResult(source.getValue());if(table.rows().stream().anyMatch(row->StudyResultShape.pOnly(table,row)))return waiting(context,"The registered inverse-variance meta-analysis needs signed effects/SE. P-only tests remain retained; no unregistered P-value combination or cohort omission was substituted.",Map.of("p_value_only_source",source.getKey()));}
        Map<String, Object> configuration = optionalMap(
            context.operation().get("configuration"), "operation.configuration");
        var poolingAudit = StudyPoolingAudit.assess(context, results, false);
        if (!poolingAudit.needs().isEmpty()) return poolingAudit.waiting();
        var metaOptions = StudyMetaOptions.resolve(configuration);
        if (org.jlinalg.study.StudyPoolingPolicy.correlated(StudyResultTables.analysis(context))) return StudyCorrelatedMeta.execute(context, results, configuration, poolingAudit);
        String model = text(configuration.getOrDefault("model", "fixed"))
            .toLowerCase(Locale.ROOT);
        if (!Set.of("fixed", "random").contains(model))
            throw new IllegalArgumentException(
                "meta_analysis model must be fixed or random");
        int defaultMinimum = results.size() > 1 ? 2 : 1;
        int minimum = positiveInteger(
            configuration.getOrDefault("min_cohorts", defaultMinimum),
            "meta_analysis.min_cohorts");
        List<String> arguments = new ArrayList<>();
        for (Map.Entry<String, Path> result : results.entrySet())
            arguments.addAll(List.of("--cohort",
                result.getKey() + "=" + result.getValue()));
        Path output = context.outputDirectory().resolve("meta-analysis.tsv");
        arguments.addAll(List.of("--out", output.toString(),
            "--id-column", "feature_id", "--effect-column", "beta",
            "--se-column", "se", "--min-cohorts", Integer.toString(minimum),
            "--model", model));
        StudyMetaOptions.arguments(configuration, arguments);
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int status = MetaCli.run("meta-analysis",
            arguments.toArray(String[]::new), new PrintStream(console),
            new PrintStream(error));
        Files.writeString(context.outputDirectory().resolve("console.txt"),
            console.toString(StandardCharsets.UTF_8)
                + error.toString(StandardCharsets.UTF_8));
        if (status != 0)
            throw new IOException("Meta-analysis failed: "
                + error.toString(StandardCharsets.UTF_8).trim());
        List<String> outputs = new ArrayList<>(List.of("meta-analysis.tsv", "meta-analysis.tsv.log", "console.txt", "pooling-audit.yaml", "pooling-compatibility.tsv", "pooling-variant-compatibility.tsv"));
        outputs.addAll(StudyMetaMetadata.attach(output, results));
        var defaults = metaOptions;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("model", model); parameters.put("weighting", "inverse variance"); parameters.put("min_cohorts", minimum);
        parameters.put("inference", defaults.inferenceMethod().toString()); parameters.put("confidence_level", defaults.confidenceLevel());
        parameters.put("tau_squared_estimator", model.equals("random") ? defaults.tauSquaredEstimator().toString() : "not applicable");
        parameters.put("maximum_iterations", defaults.maximumIterations()); parameters.put("tolerance", defaults.tolerance());
        parameters.put("command_arguments", arguments); parameters.put("effective_defaults", "Backend defaults captured at execution, not reconstructed for older runs.");
        parameters.put("overlap_handling", "No covariance-based correction; confirm cohort independence/overlap declarations.");
        var method = new LinkedHashMap<>(StudyMethods.record(model + "-effect inverse-variance meta-analysis", parameters,
            StudyPublicEvidence.list(configuration.get("references")), configuration.get("rationale"), List.of("meta-analysis", "inverse variance")));
        var pooledTable = DelimitedData.readResult(output);
        long fitted = pooledTable.rows().stream().filter(row -> Set.of("ok", "single_cohort").contains(row[pooledTable.column("status")])).count();
        method.put("performed", fitted > 0); method.put("retained_estimates", fitted);
        return new Result(State.COMPLETE, "Pooled " + results.size() + " cohort result tables",
            outputs, Map.of("method_report", method,
                "pooling_audit", poolingAudit.details(),
                "evidence_disposition", fitted > 0 ? "estimable" : "not_estimable"));
    }

    private Result sensitivity(Context context) throws IOException {
        Map<String, Path> results = results(context);
        Map<String, Object> assistance = optionalMap(
            context.study().get("assistance"), "assistance");
        Map<String, Object> configuration = optionalMap(
            context.operation().get("configuration"), "operation.configuration");
        Set<String> actions = new LinkedHashSet<>(
            strings(assistance.get("allow_analyses")));
        if (Boolean.TRUE.equals(configuration.get(
                "automatic_leave_one_cohort_out")))
            actions.add("leave_one_cohort_out");
        var heterogeneityPolicy=StudyHeterogeneity.policy(context);
        boolean conditionalOmissions="on_alert".equals(heterogeneityPolicy.get("leave_one_cohort_out"));
        boolean diagnosticAlert=StudyHeterogeneity.recordedAlert(context,"heterogeneity_review","heterogeneity-review.yaml")||StudyHeterogeneity.recordedAlert(context,"cohort_overlap","cohort-overlap.yaml");
        boolean expectedOverlap=Boolean.TRUE.equals(org.jlinalg.study.StudyCohortPolicy.overlap(optionalMap(StudyResultTables.analysis(context).get("cohort_overlap"),"cohort_overlap")).get("enabled"));
        boolean alertInputsComplete=(!Boolean.TRUE.equals(heterogeneityPolicy.get("enabled"))||StudyHeterogeneity.recordedComplete(context,"heterogeneity_review","heterogeneity-review.yaml"))&&(!expectedOverlap||StudyHeterogeneity.recordedComplete(context,"cohort_overlap","cohort-overlap.yaml"));
        boolean omissionNotTriggered=conditionalOmissions&&!diagnosticAlert&&alertInputsComplete;
        if(conditionalOmissions&&!diagnosticAlert||"disabled".equals(heterogeneityPolicy.get("leave_one_cohort_out")))actions.remove("leave_one_cohort_out");
        List<Object> dispositions = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        boolean incomplete = conditionalOmissions&&!diagnosticAlert&&!alertInputsComplete;
        if(incomplete)dispositions.add(Map.of("action","leave_one_cohort_out","status","waiting_for_input","required_input","The registered on_alert rule needs complete heterogeneity/overlap reviews; unavailable review evidence is not a negative alert"));
        // These are supplementary influence diagnostics, never a silent
        // replacement for the all-cohort primary analysis.
        // Discovery pools and supplementary fits also write meta-analysis.tsv.
        // A filename cannot identify the prespecified all-cohort baseline.
        Path primaryMeta = StudyPrimaryResults.metaBaseline(context);
        Path primaryReplication = StudyResultTables.artifact(context,
            "discovery_replication", "replication.tsv");
        boolean primaryReady = primaryMeta != null;
        ReplicationMetrics primaryReplicationMetrics = replicationMetrics(
            primaryReplication);
        MetaMetrics primaryMetaMetrics = metaMetrics(context,primaryMeta);
        String suppliedPrimaryIssue = StudySuppliedFamilies.sensitivityIssue(context, primaryMeta, results);
        Result effectSensitivity = null;
        if (Boolean.TRUE.equals(configuration.get("automatic_unfiltered_effect_qc"))) {
            effectSensitivity = suppliedPrimaryIssue == null ? new StudyEffectSensitivity().execute(context, this, results)
                : Result.waiting("Supplied primary is not yet matched to the native sensitivity protocol", Map.of("required_input", suppliedPrimaryIssue));
            outputs.addAll(effectSensitivity.outputs());
            incomplete = effectSensitivity.state() == State.WAITING_FOR_INPUT;
        }
        Map<String, Map<String, Object>> described = datasets(context.study());
        Map<String, List<String>> cohorts = new LinkedHashMap<>();
        boolean unambiguous = true;
        for (String id : results.keySet()) {
            Map<String, Object> dataset = described.get(id);
            Object cohort = dataset == null ? null : dataset.get("cohort");
            if (!(cohort instanceof String name) || name.isBlank()) { unambiguous = false; continue; }
            cohorts.computeIfAbsent((String) cohort, ignored -> new ArrayList<>()).add(id);
        }
        StringBuilder comparisonTable = new StringBuilder(
            "action\tomitted_cohort\tincluded_datasets"
                + "\tprimary_replication_fraction\tsensitivity_replication_fraction"
                + "\treplication_fraction_delta\tprimary_mean_i_squared"
                + "\tsensitivity_mean_i_squared\tmean_i_squared_delta"
                + "\tprimary_high_i_squared_fraction"
                + "\tsensitivity_high_i_squared_fraction"
                + "\todd_one_candidate\tmanuscript_disposition\n");
        List<Object> improvementCandidates = new ArrayList<>();
        for (String action : actions) {
            if (suppliedPrimaryIssue != null) {
                incomplete = true;
                dispositions.add(Map.of("action", action, "status", "waiting_for_input", "required_input", suppliedPrimaryIssue));
                continue;
            }
            if (!primaryReady || results.size() < 2 || action.equals("leave_one_cohort_out") && (!unambiguous || cohorts.size() < 3)) {
                incomplete = true;
                dispositions.add(Map.of("action", action, "status", "waiting_for_input", "required_input",
                    action.equals("leave_one_cohort_out") ? "Completed primary meta-analysis and at least three explicitly identified cohorts"
                        : "Completed primary meta-analysis with at least two comparable cohort result tables"));
                continue;
            }
            List<String> variants = action.equals("fixed_random_comparison") ? List.of("fixed", "random") : new ArrayList<>(cohorts.keySet());
            for (int i = 0; i < variants.size(); i++) {
                String variant = variants.get(i);
                Path folder = context.outputDirectory().resolve(action + "/analysis-" + (i + 1));
                Files.createDirectories(folder);
                Map<String, Path> selected = new LinkedHashMap<>(results);
                if (action.equals("leave_one_cohort_out")) cohorts.get(variant).forEach(selected::remove);
                if(action.equals("leave_one_cohort_out")&&cohorts.size()-1<((Number)heterogeneityPolicy.get("minimum_retained_cohorts")).intValue()){
                    dispositions.add(Map.of("action",action,"variant",variant,"status","not_applicable","reason","Registered minimum_retained_cohorts is not met; no cohort estimate is relabeled as a meta-analysis"));continue;
                }
                Map<String, Object> operation = new LinkedHashMap<>(context.operation());
                Map<String, Object> primary = optionalMap(map(hypothesis(context.study(), text(operation.get("hypothesis")))
                    .get("analysis"), "analysis").get("operations"), "operations");
                Map<String, Object> settings = new LinkedHashMap<>(optionalMap(primary.get("meta_analysis"), "meta_analysis"));
                if (action.equals("leave_one_cohort_out") && configuration.containsKey("min_cohorts"))
                    settings.put("min_cohorts", positiveInteger(configuration.get("min_cohorts"),
                        "sensitivity_analysis.min_cohorts"));
                if (positiveInteger(settings.getOrDefault("min_cohorts", 2), "min_cohorts") > selected.size()) {
                    incomplete = true;
                    dispositions.add(Map.of("action", action, "variant", variant, "status", "waiting_for_input",
                        "required_input", "The remaining cohorts do not meet the approved min_cohorts setting; changing that setting requires an amendment"));
                    continue;
                }
                if (action.equals("fixed_random_comparison")) settings.put("model", variant);
                operation.put("configuration", settings);
                Result result = meta(new Context(context.project(), folder, operation, context.study(), context.plan(), context.dependencyDirectories()), selected);
                for (String output : result.outputs()) outputs.add(context.outputDirectory().relativize(folder.resolve(output)).toString().replace('\\', '/'));
                if (result.state() != State.COMPLETE) {
                    incomplete = true;
                    dispositions.add(Map.of("action", action, "variant", variant, "status", result.state().value(), "details", result.details()));
                    continue;
                }
                Path replicationOutput = null;
                if (action.equals("leave_one_cohort_out")) {
                    Map<String, Object> sourceHypothesis = hypothesis(context.study(),
                        text(operation.get("hypothesis")));
                    Map<String, Object> sourceRoles = optionalMap(
                        sourceHypothesis.get("inputs"), "hypothesis.inputs");
                    Map<String, Object> filteredRoles = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> role : sourceRoles.entrySet()) {
                        List<String> retained = strings(role.getValue()).stream()
                            .filter(selected::containsKey).toList();
                        if (!retained.isEmpty()) filteredRoles.put(role.getKey(), retained);
                    }
                    if (!role(filteredRoles, "discovery").isEmpty()
                            && !role(filteredRoles, "replication").isEmpty()) {
                        Map<String, Object> replicationOperation = new LinkedHashMap<>(operation);
                        replicationOperation.put("type", "discovery_replication");
                        replicationOperation.put("roles", filteredRoles);
                        Path pooledDiscovery = null;
                        List<String> remainingDiscovery = role(filteredRoles, "discovery");
                        if (remainingDiscovery.size() > 1) {
                            Path poolFolder = folder.resolve("discovery");
                            Files.createDirectories(poolFolder);
                            Map<String, Object> poolOperation = new LinkedHashMap<>(replicationOperation);
                            poolOperation.put("datasets", remainingDiscovery);
                            Map<String, Object> poolSettings = optionalMap(primary.get("discovery_meta_analysis"), "discovery_meta_analysis");
                            if (poolSettings.isEmpty()) poolSettings = optionalMap(primary.get("meta_analysis"), "meta_analysis");
                            poolOperation.put("configuration", poolSettings);
                            Result poolResult = discoveryMeta(new Context(context.project(), poolFolder, poolOperation,
                                context.study(), context.plan(), context.dependencyDirectories()), selected);
                            for (String output : poolResult.outputs()) outputs.add(context.outputDirectory().relativize(poolFolder.resolve(output)).toString().replace('\\', '/'));
                            if (poolResult.state() != State.COMPLETE) {
                                incomplete = true;
                                dispositions.add(Map.of("action", action, "variant", variant, "stage", "discovery_pool", "status", poolResult.state().value(), "details", poolResult.details()));
                                continue;
                            }
                            pooledDiscovery = poolFolder.resolve("discovery-results.tsv");
                        }
                        Result replicationResult = replication(new Context(
                            context.project(), folder, replicationOperation,
                            context.study(), context.plan(),
                            context.dependencyDirectories()), selected, pooledDiscovery);
                        for (String output : replicationResult.outputs()) {
                            String relative = context.outputDirectory().relativize(
                                folder.resolve(output)).toString().replace('\\', '/');
                            if (!outputs.contains(relative)) outputs.add(relative);
                        }
                        if (replicationResult.state() != State.COMPLETE) {
                            incomplete = true;
                            dispositions.add(Map.of("action", action, "variant", variant, "stage", "validation", "status", replicationResult.state().value(), "details", replicationResult.details()));
                            continue;
                        }
                        replicationOutput = folder.resolve("replication.tsv");
                    }
                }
                MetaMetrics sensitivityMeta = metaMetrics(context,
                    folder.resolve("meta-analysis.tsv"));
                ReplicationMetrics sensitivityReplication = replicationMetrics(
                    replicationOutput);
                double replicationDelta = difference(
                    sensitivityReplication.fraction(),
                    primaryReplicationMetrics.fraction());
                double heterogeneityDelta = difference(
                    sensitivityMeta.meanISquared(),
                    primaryMetaMetrics.meanISquared());
                boolean replicationImproved = Double.isFinite(replicationDelta)
                    && replicationDelta > 1e-12;
                boolean heterogeneityImproved = Double.isFinite(heterogeneityDelta)
                    && heterogeneityDelta < -1e-12;
                boolean oddOneCandidate = false; // assigned only after paired common-universe review below
                String manuscriptDisposition = oddOneCandidate
                    ? "feature_in_sensitivity_section_primary_unchanged"
                    : "retain_in_supplementary_sensitivity_results";
                Map<String, Object> disposition = new LinkedHashMap<>();
                disposition.put("action", action);
                disposition.put("variant", variant);
                disposition.put("status", "complete");
                disposition.put("result", context.outputDirectory().relativize(
                    folder.resolve("meta-analysis.tsv")).toString().replace('\\', '/'));
                disposition.put("included_datasets", new ArrayList<>(selected.keySet()));
                disposition.put("primary_metrics", metrics(
                    primaryReplicationMetrics, primaryMetaMetrics));
                disposition.put("sensitivity_metrics", metrics(
                    sensitivityReplication, sensitivityMeta));
                disposition.put("replication_fraction_delta", numeric(replicationDelta));
                disposition.put("mean_i_squared_delta", numeric(heterogeneityDelta));
                disposition.put("replication_improved", replicationImproved);
                disposition.put("heterogeneity_improved", heterogeneityImproved);
                disposition.put("odd_one_candidate", oddOneCandidate);
                disposition.put("manuscript_disposition", manuscriptDisposition);
                dispositions.add(disposition);
                if (oddOneCandidate) improvementCandidates.add(Map.of(
                    "omitted_cohort", variant,
                    "replication_fraction_delta", replicationDelta,
                    "mean_i_squared_delta", heterogeneityDelta,
                    "manuscript_disposition", manuscriptDisposition));
                comparisonTable.append(action).append('\t')
                    .append(action.equals("leave_one_cohort_out") ? variant : "")
                    .append('\t').append(String.join(",", selected.keySet()))
                    .append('\t').append(value(primaryReplicationMetrics.fraction()))
                    .append('\t').append(value(sensitivityReplication.fraction()))
                    .append('\t').append(value(replicationDelta))
                    .append('\t').append(value(primaryMetaMetrics.meanISquared()))
                    .append('\t').append(value(sensitivityMeta.meanISquared()))
                    .append('\t').append(value(heterogeneityDelta))
                    .append('\t').append(value(primaryMetaMetrics.highISquaredFraction()))
                    .append('\t').append(value(sensitivityMeta.highISquaredFraction()))
                    .append('\t').append(oddOneCandidate)
                    .append('\t').append(manuscriptDisposition).append('\n');
            }
        }
        var influence=StudyCohortInfluence.write(context,primaryMeta,dispositions);outputs.addAll(influence.outputs());improvementCandidates.clear();improvementCandidates.addAll(influence.candidates());incomplete|=!influence.complete();
        StudyCohortInfluence.legacyTable(context,dispositions);
        outputs.add(0, "sensitivity-comparison.tsv");
        Map<String, Object> decision = new LinkedHashMap<>();
        Map<String, Object> primaryArtifacts = new LinkedHashMap<>();
        if (primaryMeta != null) primaryArtifacts.put("meta_analysis",
            primaryArtifact(context, primaryMeta));
        if (primaryReplication != null) primaryArtifacts.put("discovery_replication",
            primaryArtifact(context, primaryReplication));
        decision.put("primary_artifacts", primaryArtifacts);
        decision.put("primary_selection_rule",
            "Use only the unique completed meta_analysis and discovery_replication producer artifacts verified against their manifests; discovery-only and supplementary pools cannot become the all-cohort baseline.");
        if (effectSensitivity != null) {
            decision.put("effect_qc_unfiltered", effectSensitivity.details());
            decision.put("effect_qc_unfiltered_state", effectSensitivity.state().value());
        }
        decision.put("analyses", dispositions);
        decision.put("paired_meta_influence",influence.summary());decision.put("heterogeneity_policy",heterogeneityPolicy);decision.put("omission_alert_triggered",diagnosticAlert);decision.put("omission_not_triggered",omissionNotTriggered);decision.put("omission_alert_inputs_complete",alertInputsComplete);decision.put("replication_comparison_basis","Reselected discovery/validation families; fractions remain descriptive and are not used for cohort nomination");
        decision.put("primary_metrics", metrics(primaryReplicationMetrics,
            primaryMetaMetrics));
        decision.put("odd_one_candidates", improvementCandidates);
        decision.put("automatic_rule",
            "Review registered omissions only from paired common-feature influence: both mean/high I-squared reductions or a pooled direction change. Every attempted fit is retained; no Q-P-value, reselection-rate or minimum-heterogeneity primary optimization.");
        decision.put("authorization",
            "Automatic leave-one-cohort-out and unfiltered effect-QC diagnostics are part of the approved deterministic plan; other analyses require assistance.allow_analyses");
        decision.put("manuscript_guidance", improvementCandidates.isEmpty()
            ? "No omission improved both registered criteria; retain all-cohort primary results and report the complete sensitivity table in supplementary material."
            : "Feature flagged omissions in a labeled sensitivity subsection with exact deltas and full-cohort primary results alongside them. Promotion to a primary analysis requires a prespecified rationale or a reviewed study amendment; outcome-selected exclusion is not automatic.");
        decision.put("interpretation",
            "Exploratory influence diagnostics; primary results unchanged. Every cohort omission is retained, and no effect-scale correction, selective exclusion, or new hypothesis is inferred.");
        StudyPublicEvidence.write(context.outputDirectory().resolve("decision.yaml"),decision);
        outputs.add("decision.yaml");
        return new Result(incomplete ? State.WAITING_FOR_INPUT : State.COMPLETE,
            incomplete ? "Some preapproved sensitivity analyses need additional inputs" : "Completed preapproved sensitivity analyses", outputs, decision);
    }

    private static Map<String, Object> primaryArtifact(Context context,
            Path artifact) throws IOException {
        return Map.of("artifact", context.project().relativize(artifact).toString()
            .replace('\\', '/'), "sha256", FollowupSupport.hash(artifact));
    }

    private Result automaticOrConfiguredEnrichment(Context context,
            String collection, String outputName) throws Exception {
        Map<String, Object> configuration = optionalMap(
            context.operation().get("configuration"), "operation.configuration");
        if (configuration.containsKey("command")) return configured(context);
        String resultSource=text(configuration.getOrDefault("result_source","auto"));
        if(!Set.of("auto","association","rare_sets").contains(resultSource))throw new IllegalArgumentException("Enrichment result_source must be auto, association or rare_sets");
        if(resultSource.equals("rare_sets"))return new StudyRareEnrichment().execute(context,collection,outputName);
        var primary = StudyPrimaryResults.official(context);
        if(primary==null&&!resultSource.equals("association")&&StudyRareFollowup.planned(context))return new StudyRareEnrichment().execute(context,collection,outputName);
        if (primary == null)
            return waiting(context, collection + " enrichment needs a completed association or meta-analysis table",
                Map.of("required_input", "feature-level association results"));
        Path source = primary.source().path();
        if (!primary.completeObservedFamily())
            return waiting(context, collection + " enrichment needs the complete eligible tested background, beyond a selected-only supplied result table",
                Map.of("required_input", "Complete eligible feature universe and compatible gene mappings for enrichment; source-adjusted P values remain usable for scoped literature/genetic selection"));
        Map<String, Double> pValues = primary.probabilities();
        if (pValues.isEmpty())
            return new Result(State.NOT_APPLICABLE, "No eligible testing family remains for " + collection + " enrichment",
                List.of("primary-result-selection.yaml"), Map.of("source", source.toString(), "reason", "no_eligible_hypotheses", "method_report", Map.of("performed", false)));
        Map<String, Double> fdr = primary.adjusted();
        Map<String, Object> hypothesis = hypothesis(context.study(),
            text(context.operation().get("hypothesis")));
        Map<String, Object> analysis = map(
            hypothesis.get("analysis"), "hypothesis.analysis");
        Map<String, Object> testing = optionalMap(
            analysis.get("multiple_testing"), "analysis.multiple_testing");
        double alpha = number(testing.getOrDefault("alpha", 0.05),
            "multiple_testing.alpha");
        Set<String> selected = new LinkedHashSet<>();
        Path selection = context.outputDirectory().resolve(
            "feature-selection.tsv");
        try (BufferedWriter writer = Files.newBufferedWriter(selection)) {
            writer.write("feature_id\tp_value\tFDR\tselected\n");
            for (Map.Entry<String, Double> entry : pValues.entrySet()) {
                boolean keep = fdr.get(entry.getKey()) <= alpha;
                if (keep) selected.add(entry.getKey());
                writer.write(entry.getKey() + "\t" + entry.getValue()
                    + "\t" + fdr.get(entry.getKey()) + "\t" + keep + "\n");
            }
        }

        Map<String, GeneMapping> mappings = geneMappings(context,
            pValues.keySet());
        boolean symbols = collection.equals("GO");
        Set<String> backgroundGenes = new java.util.TreeSet<>();
        Set<String> selectedGenes = new java.util.TreeSet<>();
        Path mappingOutput = context.outputDirectory().resolve(
            "feature-to-gene.tsv");
        try (BufferedWriter writer = Files.newBufferedWriter(mappingOutput)) {
            writer.write("feature_id\tgene_id\tgene_symbol\tmapped\n");
            for (String id : pValues.keySet()) {
                GeneMapping mapping = mappings.get(id);
                String gene = mapping == null ? null
                    : symbols ? mapping.symbol() : mapping.ensembl();
                boolean mapped = gene != null && !gene.isBlank();
                writer.write(id + "\t" + (mapping == null ? "" : mapping.ensembl())
                    + "\t" + (mapping == null ? "" : mapping.symbol())
                    + "\t" + mapped + "\n");
                if (mapped) {
                    backgroundGenes.add(gene);
                    if (selected.contains(id)) selectedGenes.add(gene);
                }
            }
        }
        if (backgroundGenes.isEmpty())
            return waiting(context,
                "Feature identifiers could not be mapped to the gene namespace required by "
                    + collection,
                Map.of("required_input",
                    "dataset annotation_release (for GENCODE IDs) or files.annotation",
                    "unmapped_features", pValues.size()));

        Path selectedFile = context.outputDirectory().resolve(
            "selected-genes.txt");
        Path backgroundFile = context.outputDirectory().resolve(
            "background-genes.txt");
        Files.writeString(selectedFile, String.join("\n", selectedGenes)
            + (selectedGenes.isEmpty() ? "" : "\n"));
        Files.writeString(backgroundFile, String.join("\n", backgroundGenes)
            + "\n");
        if (selectedGenes.isEmpty()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("state", "complete");
            summary.put("collection", collection);
            summary.put("tested_features", pValues.size());
            summary.put("selected_features", selected.size());
            summary.put("selected_genes", 0);
            summary.put("interpretation",
                "No features passed the prespecified FDR threshold; enrichment was not tested");
            Files.writeString(context.outputDirectory().resolve(
                "enrichment-status.yaml"), new Yaml().dump(summary));
            return new Result(State.COMPLETE, "No genes met the prespecified enrichment selection threshold",
                List.of("feature-selection.tsv", "feature-to-gene.tsv",
                    "selected-genes.txt", "background-genes.txt",
                    "enrichment-status.yaml"), Map.of("method_report", Map.of("performed", false, "reason", "No selected genes; enrichment was not tested", "selection_alpha", alpha)));
        }

        String directoryName = collection.equals("GO")
            ? "go-human" : "reactome-human";
        Path database = StudyResourceManager.resourceRoot(
            context.project(), context.study()).resolve("enrichment")
            .resolve(directoryName);
        ByteArrayOutputStream download = new ByteArrayOutputStream();
        StudyPublicEvidence.write(context.outputDirectory().resolve("resource-acquisition-intent.yaml"), Map.of(
            "hypothesis", context.operation().get("hypothesis"), "collection", collection,
            "scientific_rationale", "Test whether the hypothesis-selected genes are overrepresented in " + collection
                + " using the mapped tested genes as background. This is functional context, not independent replication or causal evidence.",
            "selection_source_sha256", FollowupSupport.hash(source), "selected_genes", selectedGenes.size(),
            "background_genes", backgroundGenes.size(), "resource_directory", database.toString(),
            "request_needed", !Files.isRegularFile(database.resolve("manifest.properties"))));
        if (!Files.isRegularFile(database.resolve("manifest.properties"))) {
            if (!StudyResourceManager.automatic(context.study()))
                return waiting(context,
                    collection + " database is missing and automatic downloads are disabled",
                    Map.of("expected_resource", database.toString()));
            Files.createDirectories(database.getParent());
            List<String> downloadArguments = new ArrayList<>(List.of(
                "--enrichment", collection, "--download", database.toString(),
                "--species", "human", "--gene-id-type",
                symbols ? "symbol" : "ensembl"));
            int downloadStatus = EnrichmentCli.run(
                downloadArguments.toArray(String[]::new),
                new PrintStream(download), new PrintStream(download));
            if (downloadStatus != 0)
                throw new IOException(collection + " resource download failed: "
                    + download.toString(StandardCharsets.UTF_8).trim());
        }
        Files.writeString(context.outputDirectory().resolve(
            "resource-download.log"),
            download.toString(StandardCharsets.UTF_8));
        Files.copy(database.resolve("manifest.properties"),
            context.outputDirectory().resolve("reference-manifest.properties"));

        Path output = context.outputDirectory().resolve(outputName);
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int result = EnrichmentCli.run(new String[] {
            "--enrichment", collection,
            "--enrichment-db", database.toString(),
            "--input", selectedFile.toString(),
            "--background", backgroundFile.toString(),
            "--out", output.toString(),
            "--gene-id-type", symbols ? "symbol" : "ensembl"
        }, new PrintStream(console), new PrintStream(error));
        Files.writeString(context.outputDirectory().resolve("console.txt"),
            console.toString(StandardCharsets.UTF_8)
                + error.toString(StandardCharsets.UTF_8));
        if (result != 0)
            throw new IOException(collection + " enrichment failed: "
                + error.toString(StandardCharsets.UTF_8).trim());
        return new Result(State.COMPLETE, "Completed local " + collection
            + " enrichment for " + selectedGenes.size() + " selected genes",
            List.of("feature-selection.tsv", "feature-to-gene.tsv",
                "selected-genes.txt", "background-genes.txt", outputName,
                outputName + ".log", outputName + ".features.tsv",
                outputName + ".manifest.properties", "console.txt",
                "resource-download.log", "reference-manifest.properties"), Map.of("method_report",
                    StudyMethods.record(collection + " gene-set overrepresentation", Map.of("collection", collection, "selection_alpha", alpha,
                        "feature_selection", primary.adjustment() + " from the registered scoped primary testing family; see primary-result-selection.yaml", "background", "All mapped tested genes",
                        "feature_mapping", "feature-to-gene.tsv", "selected_gene_count", selectedGenes.size(), "background_gene_count", backgroundGenes.size(),
                        "database_release_and_hashes", "reference-manifest.properties", "test_settings", outputName + ".manifest.properties", "command_log", outputName + ".log"),
                        StudyPublicEvidence.list(configuration.get("references")), configuration.get("rationale"), List.of(collection.equals("GO") ? "Gene Ontology" : "Reactome", "enrichment"))));
    }

    private Result genetic(Context context) throws IOException {
        Map<String, Object> configuration = optionalMap(
            context.operation().get("configuration"), "operation.configuration");
        if (configuration.containsKey("command")) return configured(context);
        String operation = text(context.operation().get("type"));
        if (operation.equals("mendelian_randomization")) return new StudyGeneticEvidence().execute(context);
        if (!operation.equals("variant_scoring") && (!StudyPublicEvidence.list(StudyBioinformatics.config(context).get("genetic_pairs")).isEmpty()
                || operation.equals("mendelian_randomization") && !StudyPublicEvidence.map(StudyBioinformatics.config(context).get("mr")).isEmpty()
                || operation.equals("colocalization") && !StudyPublicEvidence.map(StudyBioinformatics.config(context).get("colocalization")).isEmpty()))
            return new StudyGeneticEvidence().execute(context);
        boolean variantInput = selectedDatasets(context).stream().anyMatch(dataset -> {
            String type = String.valueOf(dataset.get("type"))
                .toLowerCase(Locale.ROOT);
            Map<String, Object> columns = optionalMap(dataset.get("columns"),
                "dataset.columns");
            return Set.of("gwas", "genomics").contains(type)
                || columns.containsKey("variant_id")
                || columns.containsKey("rsid");
        });
        Map<String, Object> details = new LinkedHashMap<>();
        if (operation.equals("variant_scoring") && !variantInput)
            return new Result(State.NOT_APPLICABLE,
                "Variant scoring is not applicable to the declared non-variant omics results",
                List.of(), Map.of("reason", "No variant-level association input"));
        details.put("eligible_variant_summary_input", variantInput);
        details.put("annotation_requirements", annotationRequests(context));
        if (!variantInput) {
            details.put("molecular_followup", List.of(
                "Resolve probe/transcript coordinates, annotation release, and genome build",
                "Identify same-phenotype GWAS studies and nearby loci; nearby lead variants alone are insufficient",
                "Obtain tissue-appropriate molecular-QTL effects for the actual measured feature and full regional outcome-GWAS statistics",
                "Check allele/build/ancestry alignment, instrument validity, and sample overlap before fine mapping, colocalization, or MR"));
            details.put("catalog_search_status", context.dependencyDirectories().values().stream()
                .anyMatch(path -> Files.isRegularFile(path.resolve("source-discovery.yaml"))) ? "see_source_discovery" : "not_run");
            details.put("catalog_search_reason", "Inspect source_discovery evidence and citations. For supported public MR sources use concise analysis.bioinformatics.mr; use genetic_pairs for custom reviewed regional sources. Candidate catalog hits alone cannot identify a valid causal analysis.");
            details.put("ai_advisor_status", "not_configured");
        }
        details.put("default_ld_database", "1000g-phase3 (GRCh37); automatic public GRCh38 MR can select 1000g-30x-grch38 explicitly");
        details.put("ld_download_policy", "automatic_when_variant_inputs_are_eligible");
        details.put("privacy_note",
            "Reference downloads are inbound; no study result is uploaded");
        details.put("required_inputs", switch (operation) {
            case "fine_mapping" -> List.of(
                "regional variant-level summary statistics with beta/se or z",
                "sample size and genome build", "ancestry or LD population",
                "variant-matched LD matrix or extractable LD panel");
            case "colocalization" -> List.of(
                "two traits with regional variant-level summary statistics",
                "harmonized build, alleles, and overlapping variants",
                "fine-mapping effects for both traits");
            case "mendelian_randomization" -> List.of(
                "exposure and outcome variant-level summary statistics",
                "effect alleles and allele frequencies", "ancestry",
                "valid instrument definition and sample-overlap declaration");
            default -> List.of(
                "variant-level association results and annotation policy");
        });
        if (variantInput && !operation.equals("variant_scoring")) {
            ByteArrayOutputStream resourceLog = new ByteArrayOutputStream();
            try {
                StudyResourceManager.LdReference reference =
                    StudyResourceManager.ld(context.project(), context.study(),
                        new PrintStream(resourceLog));
                details.put("ld_reference", reference == null ? "not_installed"
                    : reference.directory().toString());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("LD reference download was interrupted",
                    interrupted);
            }
            Files.writeString(context.outputDirectory().resolve(
                "ld-reference-download.log"),
                resourceLog.toString(StandardCharsets.UTF_8));
            details.put("download_log", "ld-reference-download.log");
        }
        return waiting(context,
            operation + " cannot be inferred from the currently declared inputs",
            details, geneticNeeds(context, operation));
    }

    private static List<StudyNeed> geneticNeeds(Context context, String operation) throws IOException {
        List<StudyNeed> needs = new ArrayList<>();
        for (Object raw : annotationRequests(context)) {
            Map<String, Object> request = optionalMap(raw, "annotation request");
            String id = text(request.get("dataset"));
            if (id.isBlank()) {
                needs.add(StudyNeed.review("annotation-review:pooled:" + context.operation().get("hypothesis"),
                    "How should conflicting feature annotations across datasets be reconciled?",
                    "Inspect annotation-summary.yaml and resolve pooled identity, build and interval conflicts before genomic inference.", true));
                continue;
            }
            Map<String, Object> dataset = selectedDatasets(context).stream()
                .filter(item -> id.equals(item.get("id"))).findFirst().orElseThrow();
            Map<String, Object> files = optionalMap(dataset.get("files"), "files");
            if (!files.containsKey("annotation"))
                needs.add(StudyNeed.dataset(id, "files.annotation",
                    "Which local feature annotation file should be used for dataset " + id + "?",
                    "Genomic follow-up needs unambiguous feature coordinates, genome build, and coordinate convention. Supply the mapping source and release; do not infer them from nearby GWAS hits.",
                    "local_file", List.of()));
            else needs.add(StudyNeed.review("annotation-review:" + id,
                "How should missing or ambiguous annotation for dataset " + id + " be resolved?",
                "The supplied annotation leaves unresolved mappings. Inspect annotation-summary.yaml and document corrected mappings, source/release, build, and coordinate convention in an amendment.", true));
        }
        String hypothesis = text(context.operation().get("hypothesis"));
        needs.add(StudyNeed.review("genetic-inputs:" + hypothesis + ":" + operation,
            "Which scientifically relevant local GWAS/QTL inputs and analysis specification should support " + operation + " for " + hypothesis + "?",
            "Document phenotype and molecular feature matching, tissue relevance, source citations, ancestry, genome build, alleles, and sample overlap. Lead SNP proximity alone is insufficient; review request.yaml for operation-specific requirements. Acquisition and adapter configuration remain separate steps.", true));
        return needs;
    }

    private Result prioritization(Context context) throws IOException {
        var primary = StudyPrimaryResults.official(context);
        Path meta = primary == null ? null : primary.source().path();
        Path output = context.outputDirectory().resolve(
            "candidate-prioritization.tsv");
        Map<String, String> evidence = dependencyStates(context);
        Map<String, List<String>> featureEvidence = new LinkedHashMap<>();
        for (Path directory : context.dependencyDirectories().values()) {
            if (!Files.isRegularFile(directory.resolve("manifest.yaml"))) continue;
            var producer = StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
            if (!context.operation().get("hypothesis").equals(producer.get("hypothesis"))) continue;
            var sealed = StudyPublicEvidence.map(producer.get("output_sha256"));
            for (String name : List.of("bioinformatics-investigation.yaml", "literature-search.yaml", "genetic-evidence.yaml")) {
                if (!sealed.containsKey(name)) continue;
                Path path = StudyFindingInventory.verified(directory, name, String.valueOf(sealed.get(name)));
                Map<String, Object> summary;
                summary = StudyYaml.loadArtifact(path);
                for (Object raw : StudyPublicEvidence.list(summary.getOrDefault("evidence", summary.get("pairs")))) {
                    Map<String, Object> row = StudyPublicEvidence.map(raw);
                    String feature = row.get("feature_id") == null
                        ? "" : String.valueOf(row.get("feature_id"));
                    if (feature.isBlank()) continue;
                    String kind = row.get("kind") == null
                        ? "" : String.valueOf(row.get("kind"));
                    if (kind.isBlank()) kind = directory.getFileName().toString();
                    String status = row.get("status") == null
                        ? "unknown" : String.valueOf(row.get("status"));
                    if (status.isBlank()) status = "unknown";
                    featureEvidence.computeIfAbsent(feature, ignored -> new ArrayList<>()).add(kind + ":" + status);
                }
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("rank\tfeature_id\tbeta\tp_value\tFDR\ti_squared"
                + "\tevidence_layers_complete\tevidence_layers_blocked"
                + "\tfeature_evidence\tinterpretation\n");
            if (meta != null) {
                DelimitedData table = primary.table();
                int id = table.column("feature_id");
                int beta = table.header().contains("beta") ? table.column("beta") : -1;
                int i2 = table.header().contains("i_squared") ? table.column("i_squared") : -1;
                Map<String, Candidate> candidates = new LinkedHashMap<>();
                for (String[] row : table.rows()) {
                    Double probability = primary.probabilities().get(row[id]);
                    if (probability == null) continue;
                    candidates.put(row[id], new Candidate(row[id], beta < 0 ? "NA" : row[beta], probability, i2 < 0 ? "NA" : row[i2]));
                }
                Map<String, Double> adjusted = primary.adjusted();
                List<Candidate> ranked = candidates.values().stream()
                    .sorted(Comparator.comparingDouble(Candidate::pValue)
                        .thenComparing(Candidate::feature)).toList();
                String complete = evidence.entrySet().stream()
                    .filter(entry -> entry.getValue().equals("complete"))
                    .map(Map.Entry::getKey).sorted().collect(
                        java.util.stream.Collectors.joining(";"));
                String blocked = evidence.entrySet().stream()
                    .filter(entry -> !Set.of("complete", "not_applicable").contains(entry.getValue()))
                    .map(Map.Entry::getKey).sorted().collect(
                        java.util.stream.Collectors.joining(";"));
                int rank = 0;
                for (Candidate candidate : ranked)
                    writer.write(++rank + "\t" + candidate.feature() + "\t"
                        + candidate.beta() + "\t" + candidate.pValue() + "\t"
                        + adjusted.get(candidate.feature()) + "\t"
                        + candidate.iSquared() + "\t" + complete + "\t"
                        + blocked + "\t" + String.join(";", featureEvidence.getOrDefault(candidate.feature(), List.of())) + "\t"
                        + "association-ranked; operation completion is not feature-specific biological support; review feature_evidence and source records"
                        + "\n");
            } else {
                int rank = 0;
                for (StudyBioinformatics.Feature feature : StudyBioinformatics.features(context))
                    writer.write(++rank + "\t" + feature.id() + "\tNA\t" + feature.p() + "\t" + feature.fdr()
                        + "\tNA\t\t\t" + String.join(";", featureEvidence.getOrDefault(feature.id(), List.of()))
                        + "\tprimary-association-ranked exploratory candidate; no meta-analysis was available\n");
            }
        }
        String methodology = "# Candidate prioritization method\n\n"
            + "Candidates are ordered by the registered primary source P value and retain its scoped adjustment, "
            + "effect, heterogeneity, and evidence-layer availability as separate "
            + "columns. No opaque composite score is used. Association ranking "
            + "and feature-specific contextual evidence are kept separate from operation-level availability. The primary-result-selection.yaml receipt identifies the producer, source hash, contrast and correction family. A protein structure or keyword literature match is not target validation. Association ranking alone is not causal evidence, target validation, safety evidence, "
            + "or a clinical recommendation.\n";
        Files.writeString(context.outputDirectory().resolve(
            "prioritization-method.md"), methodology);
        return Result.complete("Generated a transparent candidate evidence table",
            List.of("candidate-prioritization.tsv", "prioritization-method.md"));
    }

    private Result configured(Context context) throws IOException {
        Map<String, Object> configuration = optionalMap(
            context.operation().get("configuration"), "operation.configuration");
        if (!configuration.containsKey("command"))
            return waiting(context, text(context.operation().get("type"))
                + " requires analysis.operations configuration and reference resources");
        String command = text(configuration.get("command"));
        if (!CUSTOM_COMMANDS.contains(command) || command.equals("study"))
            throw new IllegalArgumentException(
                "Command is not allowed for configured study execution: " + command);
        List<String> configuredArguments = strings(configuration.get("arguments"));
        if ("offline".equals(optionalMap(context.study().get("resources"), "resources").get("network"))
                && (!Set.of("susie", "coloc", "mr-estimate", "differential", "ewas-regions", "multiple-test", "multiple-impute", "mi-pool").contains(command)
                    || configuredArguments.stream().anyMatch(argument -> argument.contains("://")
                        || argument.startsWith("--download") || argument.startsWith("--config"))))
            return waiting(context, "Offline policy prevents a potentially network-enabled configured adapter",
                Map.of("connection_status", "offline_policy", "command", command,
                    "required_input", "Use an explicitly local supported adapter or repeat in an environment with permitted network access"));
        boolean needsLd = Boolean.TRUE.equals(configuration.get("requires_ld"))
            || configuredArguments.stream().anyMatch(argument ->
                argument.contains("@ld_database@")
                    || argument.contains("@ld_population@"));
        StudyResourceManager.LdReference ld = null;
        if (needsLd) {
            ByteArrayOutputStream resourceLog = new ByteArrayOutputStream();
            try {
                ld = StudyResourceManager.ld(context.project(), context.study(),
                    new PrintStream(resourceLog));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("LD reference download was interrupted",
                    interrupted);
            }
            Files.writeString(context.outputDirectory().resolve(
                "ld-reference-download.log"),
                resourceLog.toString(StandardCharsets.UTF_8));
            if (ld == null)
                return waiting(context,
                    "An LD reference is required and automatic downloads are disabled",
                    Map.of("default_database", "1000g-phase3",
                        "resource_policy", "manual_or_denied"));
            if ("auto".equalsIgnoreCase(ld.population())
                    && configuredArguments.stream().anyMatch(argument ->
                        argument.contains("@ld_population@")))
                return waiting(context,
                    "The 1000 Genomes reference is installed, but ancestry must be declared before LD analysis",
                    Map.of("installed_database", ld.database(),
                        "available_populations", List.of("AFR", "AMR", "EAS", "EUR", "SAS"),
                        "required_field", "resources.ld.population"));
        }
        List<String> arguments = new ArrayList<>();
        if (!command.equals("enrichment")) arguments.add(command);
        arguments.add("--no-config");
        for (String argument : configuredArguments) {
            String expanded = argument.replace("@project@",
                context.project().toString()).replace("@output@",
                context.outputDirectory().toString());
            if (expanded.contains("@upstream@"))
                expanded = expanded.replace("@upstream@", upstream(context));
            if (ld != null) expanded = expanded
                .replace("@ld_database@", ld.directory().toString())
                .replace("@ld_population@", ld.population());
            arguments.add(expanded);
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = JLinAlgCli.run(arguments.toArray(String[]::new),
            new PrintStream(stdout), new PrintStream(stderr));
        Files.writeString(context.outputDirectory().resolve("console.txt"),
            stdout.toString(StandardCharsets.UTF_8)
                + stderr.toString(StandardCharsets.UTF_8));
        if (status != 0)
            throw new IOException(command + " failed: "
                + stderr.toString(StandardCharsets.UTF_8).trim());
        List<String> outputs = strings(configuration.get("outputs"));
        outputs.add("console.txt");
        if (needsLd) outputs.add("ld-reference-download.log");
        return Result.complete("Executed configured " + command + " operation",
            outputs);
    }

    private static String upstream(Context context) {
        if (context.dependencyDirectories().size() != 1)
            throw new IllegalArgumentException(
                "@upstream@ requires exactly one immediate dependency; found "
                    + context.dependencyDirectories().keySet());
        return context.dependencyDirectories().values().iterator().next()
            .toString();
    }

    private Result diagnostics(Context context) throws IOException {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schema_version", 1);
        document.put("hypothesis", context.operation().get("hypothesis"));
        document.put("status", "standard_diagnostics_complete");
        List<Object> observations = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        Path replication = dependencyFile(context, "replication.tsv");
        if (replication != null) {
            DelimitedData table = DelimitedData.readResult(replication);
            int feature = table.column("feature_id");
            int discoveryDataset = table.column("discovery_dataset");
            int replicationDataset = table.column("replication_dataset");
            int discoveryBeta = table.column("discovery_beta");
            int replicationBeta = table.column("replication_beta");
            int selected = table.column("discovery_selected");
            int replicated = table.column("replicated");
            Map<String, Moment> moments = new LinkedHashMap<>();
            Set<String> selectedFeatures = new LinkedHashSet<>();
            Set<String> replicatedFeatures = new LinkedHashSet<>();
            for (String[] row : table.rows()) {
                String pair = row[discoveryDataset] + " -> "
                    + row[replicationDataset];
                Moment moment = moments.computeIfAbsent(pair,
                    ignored -> new Moment());
                boolean selectedValue = Boolean.parseBoolean(row[selected]);
                boolean replicatedValue = Boolean.parseBoolean(row[replicated]);
                moment.add(finite(row[discoveryBeta], "discovery_beta", replication),
                    finite(row[replicationBeta], "replication_beta", replication),
                    selectedValue, replicatedValue);
                if (selectedValue) selectedFeatures.add(row[feature]);
                if (replicatedValue) replicatedFeatures.add(row[feature]);
            }
            Path correlations = context.outputDirectory().resolve(
                "effect-correlations.tsv");
            List<Object> comparisons = new ArrayList<>();
            try (BufferedWriter writer = Files.newBufferedWriter(correlations)) {
                writer.write("comparison\tn\tpearson_r\tols_intercept"
                    + "\tols_slope\tsd_ratio\tsame_sign_fraction"
                    + "\tdiscovery_selected\treplicated"
                    + "\treplication_fraction\n");
                for (Map.Entry<String, Moment> entry : moments.entrySet()) {
                    Moment moment = entry.getValue();
                    double correlation = entry.getValue().correlation();
                    double slope = moment.slope();
                    double replicationFraction = moment.replicationFraction();
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("comparison", entry.getKey());
                    summary.put("n", moment.n);
                    summary.put("pearson_r", correlation);
                    summary.put("ols_intercept", moment.intercept());
                    summary.put("ols_slope", slope);
                    summary.put("sd_ratio", moment.standardDeviationRatio());
                    summary.put("same_sign_fraction", moment.sameSignFraction());
                    summary.put("discovery_selected", moment.selected);
                    summary.put("replicated", moment.replicated);
                    summary.put("replication_fraction", replicationFraction);
                    summary.replaceAll((key,entryValue)->entryValue instanceof Double numeric&&!Double.isFinite(numeric)?"NA":entryValue);
                    summary.put("coefficient_diagnostics_status",Double.isFinite(correlation)&&Double.isFinite(slope)?"estimable":"not_estimable");
                    comparisons.add(summary);
                    writer.write(entry.getKey() + "\t" + moment.n
                        + "\t" + value(correlation) + "\t"
                        + value(moment.intercept()) + "\t"
                        + value(slope) + "\t"
                        + value(moment.standardDeviationRatio()) + "\t"
                        + value(moment.sameSignFraction()) + "\t"
                        + moment.selected + "\t" + moment.replicated + "\t"
                        + value(replicationFraction) + "\n");
                    if (moment.n >= 100
                            && Double.isFinite(correlation)
                            && Math.abs(correlation) < 0.2) {
                        Map<String, Object> finding = new LinkedHashMap<>();
                        finding.put("type", "low_cross_cohort_effect_correlation");
                        finding.put("comparison", entry.getKey());
                        finding.put("pearson_r", correlation);
                        finding.put("interpretation",
                            "May reflect weak shared signal, phenotype or tissue heterogeneity, or processing artifacts; inspect plots and cohort metadata");
                        observations.add(finding);
                    }
                    if (moment.selected >= 100
                            && Double.isFinite(replicationFraction)
                            && replicationFraction < 0.01) {
                        Map<String, Object> finding = new LinkedHashMap<>();
                        finding.put("type", "near_zero_replication_rate");
                        finding.put("comparison", entry.getKey());
                        finding.put("discovery_selected", moment.selected);
                        finding.put("replicated", moment.replicated);
                        finding.put("replication_fraction",
                            replicationFraction);
                        finding.put("pearson_r", correlation);
                        finding.put("interpretation",
                            Math.abs(correlation) >= 0.8
                                ? "Effects are strongly correlated but almost no discovery-selected features replicate; review uncertainty, sample size, phenotype/tissue definitions, scale, and multiple-testing inputs"
                                : "Almost no discovery-selected features replicate; review power, phenotype/tissue definitions, harmonization, and processing");
                        observations.add(finding);
                    }
                    double absoluteSlope = Math.abs(slope);
                    if (moment.n >= 100 && Math.abs(correlation) >= 0.5
                            && Double.isFinite(absoluteSlope)
                            && (absoluteSlope > 4.0 || absoluteSlope < 0.25)) {
                        observations.add(Map.of(
                            "type", "cross_cohort_effect_scale_mismatch",
                            "comparison", entry.getKey(),
                            "pearson_r", correlation,
                            "ols_slope", slope,
                            "interpretation",
                            "Effects track one another but differ substantially in scale; verify phenotype scaling, units, transformations, and model parameterization before pooling"));
                    }
                }
            }
            document.put("discovery_replication_summary", Map.of(
                "discovery_selected_features", selectedFeatures.size(),
                "replicated_in_at_least_one", replicatedFeatures.size(),
                "replication_fraction_any", selectedFeatures.isEmpty()
                    ? "NA" : replicatedFeatures.size()
                        / (double) selectedFeatures.size()));
            document.put("discovery_replication", comparisons);
            outputs.add("effect-correlations.tsv");
        }
        Path meta = StudyPrimaryResults.metaBaseline(context);
        if(meta!=null){
            var registered=StudyHeterogeneity.policy(context);
            if(Boolean.TRUE.equals(registered.get("enabled"))){
                var profile=StudyHeterogeneity.assess(context,meta,registered);var metaSummary=new LinkedHashMap<String,Object>(profile.summary());metaSummary.put("registered_rule",registered);metaSummary.put("tested_features",profile.table().adjusted().size());double alpha=((Number)profile.summary().get("association_alpha")).doubleValue();long selected=profile.table().adjusted().values().stream().filter(q->q<alpha).count();metaSummary.put("significant_features",selected);metaSummary.put("association_adjustment",profile.table().adjustment());if(profile.table().adjustment().equals("BH")&&alpha==.05)metaSummary.put("BH_FDR_0_05_features",selected);document.put("meta_analysis",metaSummary);
                if(Boolean.TRUE.equals(profile.summary().get("widespread_alert")))observations.add(Map.of("type","widespread_meta_analysis_heterogeneity","metric",profile.metric(),"high_features",profile.summary().get("high_features"),"scope_features",profile.summary().get("scope_features"),"high_fraction",profile.summary().get("high_fraction"),"registered_rule",registered,"interpretation","Registered widespread-heterogeneity threshold met; inspect all prespecified influence fits, source assumptions and uncertainty without replacing the primary cohort set"));
            }
        }
        document.put("unusual_observations", observations);
        List<Object> nextSteps = new ArrayList<>();
        {
            List<?> requests = annotationRequests(context);
            if (!requests.isEmpty()) nextSteps.add(Map.of(
                "id", "resolve-feature-annotation", "status", "input_needed_for_genomic_followup",
                "trigger", "Missing or ambiguous feature annotation",
                "required_inputs", requests, "source", "annotation-summary.yaml"));
        }
        for (Object observation : observations) {
            Map<String, Object> finding = optionalMap(observation, "observation");
            String type = text(finding.get("type"));
            String action = switch (type) {
                case "cross_cohort_effect_scale_mismatch" -> "Verify phenotype units, transformations, outcome coding, and model parameterization; define a common estimand before pooling. Do not rescale using the fitted between-cohort slope.";
                case "near_zero_replication_rate", "low_cross_cohort_effect_correlation" -> "Audit standard errors, sample sizes, selection thresholds, tissue, and processing. Review cohort effect plots and assess replication power.";
                case "widespread_meta_analysis_heterogeneity" -> "After confirming comparable effect definitions, compare fixed and random effects and perform leave-one-cohort-out analysis; report changes in direction, uncertainty, FDR, and heterogeneity.";
                default -> "Review the diagnostic observation and propose a documented sensitivity analysis.";
            };
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("id", "review-" + (nextSteps.size() + 1));
            next.put("status", "proposed");
            next.put("trigger", finding);
            next.put("suggested_action", action);
            next.put("source", "diagnostics.yaml");
            next.put("required_inputs", List.of("cohort-level effect estimates and standard errors",
                "phenotype transformations, units, covariates, and cohort relationship metadata"));
            next.put("execution", "Record an analysis_requests amendment with rationale and multiplicity plan; approve a new plan before execution");
            nextSteps.add(next);
        }
        document.put("next_steps", nextSteps);
        Files.writeString(context.outputDirectory().resolve("next-steps.yaml"),
            new Yaml().dump(Map.of("schema_version", 1, "hypothesis", context.operation().get("hypothesis"),
                "producer", "jlinalg_deterministic_rules", "proposals", nextSteps)));
        outputs.add("next-steps.yaml");
        document.put("visual_review_boundary",
            "Aggregate screening cannot reliably classify streaks, clusters, or other plot morphology; record them with study observe and a reviewed amendment");
        document.put("note",
            "Automated flags are prompts for scientific review, not determinations of artifact or biology");
        StudyPublicEvidence.write(context.outputDirectory().resolve("diagnostics.yaml"),document);
        outputs.add("diagnostics.yaml");
        List<StudyNeed> needs = new ArrayList<>();
        for (Object raw : observations) {
            Map<String, Object> finding = optionalMap(raw, "observation");
            String kind = text(finding.get("type"));
            String hypothesis = text(context.operation().get("hypothesis"));
            String comparison = String.valueOf(finding.getOrDefault("comparison", "all cohorts"));
            needs.add(StudyNeed.review("diagnostic:" + hypothesis + ":" + kind + ":" + comparison,
                "What documented metadata or sensitivity analysis should be reviewed for " + kind + " in " + hypothesis + " (" + comparison + ")?",
                "See diagnostics.yaml and next-steps.yaml. The flag does not establish artifact or biology; examine units, transformations, model definitions, and study differences before proposing a new analysis. Record exploratory status and multiplicity considerations.", false));
        }
        return new Result(State.COMPLETE, "Completed registered deterministic diagnostics", outputs,
            needs.isEmpty() ? Map.of() : Map.of("needs", needs.stream().map(StudyNeed::document).toList()));
    }

    private Result report(Context context) throws IOException {
        String hypothesis = text(context.operation().get("hypothesis"));
        StringBuilder report = new StringBuilder("# Hypothesis ")
            .append(hypothesis).append(" evidence report\n\n")
            .append("This report separates completed evidence from unavailable "
                + "evidence. Every statement resolves to an operation manifest "
                + "with input and output hashes.\n\n")
            .append("## Evidence ledger\n\n");
        int complete = 0, blocked = 0, failed = 0, skipped = 0;
        for (Map.Entry<String, Path> dependency
                : context.dependencyDirectories().entrySet()) {
            Path manifestPath = dependency.getValue().resolve("manifest.yaml");
            Map<String, Object> manifest = Files.isRegularFile(manifestPath)
                ? loadYaml(manifestPath) : Map.of();
            String state = String.valueOf(manifest.getOrDefault(
                "state", "unknown"));
            if (state.equals("complete")) complete++;
            else if (state.equals("not_applicable")) skipped++;
            else if (state.equals("failed")) failed++;
            else blocked++;
            report.append("- `").append(manifest.getOrDefault(
                    "operation_type", dependency.getKey()))
                .append("` — **").append(state).append("**: ")
                .append(manifest.getOrDefault("summary", "No summary"))
                .append(". Result ID: `")
                .append(manifest.getOrDefault("run_id", "unknown")).append('/')
                .append(dependency.getKey()).append("`; manifest: `")
                .append(portable(context.project(), manifestPath)).append("`\n");
        }
        report.append("\n## Readiness\n\n")
            .append("- Completed evidence operations: ").append(complete).append("\n")
            .append("- Blocked or waiting evidence operations: ").append(blocked).append("\n")
            .append("- Failed evidence operations: ").append(failed).append("\n\n")
            .append("- Inapplicable operations: ").append(skipped).append("\n\n")
            .append("A blocked MR, fine-mapping, colocalization, or literature "
                + "branch is not negative evidence. It means the required input "
                + "or approved source was unavailable.\n\n")
            .append("## Reuse\n\nUse `index/LLM_CONTEXT.md` for a compact, "
                + "machine-readable drafting handoff and `index/index.json` for "
                + "artifact-level retrieval. Verify the project before relying "
                + "on either.\n");
        Files.writeString(context.outputDirectory().resolve("report.md"),
            report, StandardCharsets.UTF_8);
        String publication = "# Publication evidence package for " + hypothesis
            + "\n\n" + report.substring(report.indexOf("## Evidence ledger"))
            + "\nThis is an evidence inventory, not manuscript prose. Effect "
            + "definitions, cohort comparability, model assumptions, and any "
            + "post hoc amendments must be reviewed by the scientific team.\n";
        Files.writeString(context.outputDirectory().resolve(
            "publication-evidence.md"), publication, StandardCharsets.UTF_8);
        List<String> outputs = new ArrayList<>(List.of("report.md",
            "publication-evidence.md"));
        outputs.addAll(StudyPublication.write(context));
        if (objectives(context.study()).stream().anyMatch(
                value -> value.contains("drug"))) {
            String drug = "# Drug-discovery evidence package for " + hypothesis
                + "\n\nThe candidate table, when present, ranks association "
                + "evidence transparently. Genetic causality, colocalization, "
                + "tractability, safety, tissue relevance, and literature are "
                + "separate evidence layers; missing layers are shown as blockers "
                + "and must not be interpreted as support.\n\n"
                + report.substring(report.indexOf("## Evidence ledger"));
            Files.writeString(context.outputDirectory().resolve(
                "drug-discovery-evidence.md"), drug, StandardCharsets.UTF_8);
            outputs.add("drug-discovery-evidence.md");
        }
        return Result.complete("Generated traceable hypothesis report",
            outputs);
    }

    Map<String, GeneMapping> geneMappings(Context context,
            Set<String> features) throws IOException, InterruptedException {
        Map<String, GeneMapping> resolved = new LinkedHashMap<>();
        Set<String> excluded = new LinkedHashSet<>();
        var assessedFiles = new ArrayList<>(dependencyFiles(context, "feature-annotation.tsv"));
        // A mapping produced within this running operation is sealed when the operation completes.
        Path localMapping = context.outputDirectory().resolve("feature-annotation.tsv");
        if (Files.isRegularFile(localMapping) && !assessedFiles.contains(localMapping)) assessedFiles.add(localMapping);
        for (Path assessed : assessedFiles) {
            DelimitedData table = DelimitedData.readResult(assessed);
            int id = table.column("feature_id"), gene = table.column("gene_id");
            int symbol = table.column("gene_symbol"), status = table.column("mapping_status");
            for (String[] row : table.rows()) {
                if (!features.contains(row[id])) continue;
                if (!Set.of("located", "missing_location").contains(row[status])) {
                    excluded.add(row[id]);
                    continue;
                }
                GeneMapping mapping = new GeneMapping(row[gene].replaceFirst("\\.\\d+$", ""), row[symbol]);
                GeneMapping previous = resolved.putIfAbsent(row[id], mapping);
                if (previous != null && !previous.equals(mapping)) excluded.add(row[id]);
            }
        }
        excluded.forEach(resolved::remove);
        return resolved;
    }

    private static List<Object> annotationRequests(Context context) throws IOException {
        List<Object> requests = new ArrayList<>();
        for (Path path : dependencyFiles(context, "annotation-summary.yaml")) {
            Object values = loadYaml(path).get("input_requests");
            if (values instanceof List<?> list) requests.addAll(list);
        }
        return requests;
    }

    private static List<Path> dependencyFiles(Context context, String name) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path directory : context.dependencyDirectories().values()) {
            if (!Files.isRegularFile(directory.resolve("manifest.yaml"))) continue;
            var producer = StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
            if (!context.operation().get("hypothesis").equals(producer.get("hypothesis")) || !"complete".equals(producer.get("state"))) continue;
            var hashes = StudyPublicEvidence.map(producer.get("output_sha256"));
            if (!hashes.containsKey(name)) continue;
            Path file = StudyFindingInventory.verified(directory, name, String.valueOf(hashes.get(name)));
            if (!files.contains(file)) files.add(file);
        }
        return files;
    }

    static Map<String, Double> bh(Map<String, Double> pValues) {
        List<Map.Entry<String, Double>> sorted = pValues.entrySet().stream()
            .sorted(Map.Entry.comparingByValue()).toList();
        Map<String, Double> result = new LinkedHashMap<>();
        double previous = 1.0;
        for (int index = sorted.size() - 1; index >= 0; index--) {
            Map.Entry<String, Double> entry = sorted.get(index);
            previous = Math.min(previous, Math.min(1.0,
                entry.getValue() * sorted.size() / (index + 1.0)));
            result.put(entry.getKey(), previous);
        }
        return result;
    }

    private static Path dependencyFile(Context context, String name)
            throws IOException {
        String producer = switch (name) {
            case "replication.tsv" -> "discovery_replication";
            case "meta-analysis.tsv" -> "meta_analysis";
            case "discovery-results.tsv" -> "discovery_meta_analysis";
            case "analysis-phenotype.tsv" -> "participant_qc";
            default -> throw new IllegalArgumentException("No registered dependency artifact role for " + name);
        };
        return StudyResultTables.artifact(context, producer, name);
    }

    private static List<Map<String, Object>> selectedDatasets(Context context) {
        Map<String, Map<String, Object>> all = datasets(context.study());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : strings(context.operation().get("datasets"))) {
            Map<String, Object> dataset = all.get(id);
            if (dataset != null) result.add(dataset);
        }
        return result;
    }

    private static Map<String, String> dependencyStates(Context context)
            throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Path> dependency
                : context.dependencyDirectories().entrySet()) {
            Path manifest = dependency.getValue().resolve("manifest.yaml");
            if (!Files.isRegularFile(manifest)) continue;
            Map<String, Object> document = loadYaml(manifest);
            String name = String.valueOf(document.getOrDefault(
                "operation_type", dependency.getKey()));
            if (result.containsKey(name)) name += ":" + dependency.getKey();
            result.put(name, String.valueOf(document.getOrDefault(
                "state", "unknown")));
        }
        return result;
    }

    private static Set<String> objectives(Map<String, Object> study) {
        Map<String, Object> project = map(study.get("project"), "project");
        Object value = project.get("objective");
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> values)
            values.forEach(item -> result.add(String.valueOf(item)
                .toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_')));
        else if (value != null) result.add(String.valueOf(value)
            .toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        return result;
    }

    private static Map<String, Object> loadYaml(Path path) throws IOException {
        // These callers read generated dependency manifests and annotation
        // summaries, not user contracts. Expanded MR manifests can exceed the
        // ordinary 16-million-code-point contract budget.
        return StudyYaml.loadArtifact(path);
    }

    private Result waiting(Context context, String message) throws IOException {
        return waiting(context, message, Map.of());
    }

    private Result waiting(Context context, String message,
            Map<String, Object> details) throws IOException {
        return waiting(context, message, details, List.of());
    }

    private Result waiting(Context context, String message,
            Map<String, Object> details, List<StudyNeed> needs) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("schema_version", 1);
        request.put("operation", context.operation().get("type"));
        request.put("hypothesis", context.operation().get("hypothesis"));
        request.put("state", "waiting_for_input");
        request.put("reason", message);
        request.putAll(details);
        if (!needs.isEmpty()) request.put("needs", needs.stream().map(StudyNeed::document).toList());
        String content = new Yaml().dump(request);
        Files.writeString(context.outputDirectory().resolve("request.yaml"),
            content, StandardCharsets.UTF_8);
        Map<String, Object> resultDetails = new LinkedHashMap<>(Map.of("request", "request.yaml", "requirements", details));
        if (!needs.isEmpty()) resultDetails.put("needs", needs.stream().map(StudyNeed::document).toList());
        return new Result(State.WAITING_FOR_INPUT, message, List.of("request.yaml"), resultDetails);
    }

    static void normalizeNativeAssociation(Context context,Path raw,Path standard,Map<String,Object> metadata) throws IOException {
        Files.createDirectories(standard.getParent());
        standardize(raw,Map.of("feature_id","id","effect","beta","standard_error","standard_error","p_value","p_value","status","status","result_metadata",metadata),standard,false);
    }

    private static Standardization standardize(Path input,
            Map<String, Object> columns, Path output, boolean excludeInvalid)
            throws IOException {
        DelimitedData table = DelimitedData.readResult(input);
        String resultMode=text(columns.getOrDefault("result_mode","signed_effect"));
        if(!Set.of("signed_effect","p_value_only").contains(resultMode))throw new IOException("columns.result_mode must be signed_effect or p_value_only");
        boolean filePOnly=resultMode.equals("p_value_only");
        String idName = configured(columns, "feature_id",
            table.header(), "feature_id", "id", "probe_id", "variant",
            "__row_name__");
        String betaName = filePOnly?optionalEffectColumn(columns,"effect",table,"beta","effect","estimate"):configured(columns, "effect",table.header(), "beta", "effect", "estimate");
        String seName = filePOnly?optionalEffectColumn(columns,"standard_error",table,"se","standard_error","stderr"):configured(columns, "standard_error",table.header(), "se", "standard_error", "stderr");
        String pName = configured(columns, "p_value",
            table.header(), "p_value", "p", "pvalue");
        String statusName = columns.get("status") == null ? null
            : text(columns.get("status"));
        int id = table.column(idName), beta = betaName==null?-1:table.column(betaName);
        int se = seName==null?-1:table.column(seName), p = table.column(pName);
        int status = statusName == null || !table.header().contains(statusName)
            ? -1 : table.column(statusName);
        StudyResultMetadata metadata = new StudyResultMetadata(table, columns);
        boolean syntheticType=filePOnly&&!metadata.header().contains("result_type");
        Set<String> seen = new LinkedHashSet<>();
        Set<String> eligible = new LinkedHashSet<>();
        Map<String, Object> validation = optionalMap(
            columns.get("validation"), "columns.validation");
        List<Integer> finiteColumns = columnIndexes(table,
            validation.get("finite"), "validation.finite");
        List<Integer> positiveColumns = columnIndexes(table,
            validation.get("positive"), "validation.positive");
        List<Integer> nonnegativeColumns = columnIndexes(table,
            validation.get("nonnegative"), "validation.nonnegative");
        List<Integer> probabilityColumns = columnIndexes(table,
            validation.get("probability"), "validation.probability");
        int inputRows = 0, excludedInvalid = 0, excludedStatus = 0,pOnlyRows=0;
        Path audit = normalizationAudit(output);
        Files.createDirectories(audit.getParent());
        Path exclusions = audit.resolveSibling(output.getFileName() + ".exclusions.tsv");
        try (BufferedWriter writer = Files.newBufferedWriter(output);
                BufferedWriter excluded = Files.newBufferedWriter(exclusions)) {
            List<String> header = new ArrayList<>(List.of("feature_id", "beta", "se", "p_value"));
            header.addAll(metadata.header());if(syntheticType)header.add("result_type");writer.write(String.join("\t", header) + "\n");
            excluded.write("feature_id\tdisposition\treason\n");
            for (String[] row : table.rows()) {
                inputRows++;
                String feature = row[id].trim();
                if (feature.isEmpty() || feature.matches("(?s).*[\\t\\r\\n].*") || !seen.add(feature))
                    throw new IOException("Blank or duplicate feature ID in " + input
                        + ": " + feature);
                if (status >= 0 && !Set.of("ok", "tested", "complete")
                        .contains(row[status].toLowerCase(Locale.ROOT))) {
                    excludedStatus++;
                    excluded.write(feature + "\texcluded_source_status\t" + row[status].replaceAll("[\\t\\r\\n]+", " ") + "\n");
                    continue;
                }
                try {
                    validate(row, finiteColumns, positiveColumns,
                        nonnegativeColumns, probabilityColumns, table, input);
                    List<String> values = metadata.values(row);int type=metadata.header().indexOf("result_type");
                    boolean pOnly=filePOnly||type>=0&&values.get(type).equals("p_value_only");
                    if(filePOnly&&type>=0&&!values.get(type).equals("p_value_only"))throw new IOException("P-only file declaration conflicts with source result_type");
                    if(pOnly&&(beta>=0&&!StudyResultShape.missing(row[beta])||se>=0&&!StudyResultShape.missing(row[se])))throw new IOException("P-only results must leave effect and standard error unavailable");
                    double effect = pOnly?Double.NaN:finite(row[beta], "effect", input);
                    double error = pOnly?Double.NaN:finite(row[se], "standard error", input);
                    if (!pOnly&&!(error > 0.0))
                        throw new IOException(
                            "Standard errors must be positive in " + input);
                    String probability = missing(row[p]) ? "NA"
                        : Double.toString(probability(row[p], input));
                    if(pOnly&&probability.equals("NA"))throw new IOException("P-only association rows need an actual P value");
                    writer.write(feature + "\t" + (pOnly?"NA":effect) + "\t" + (pOnly?"NA":error)
                        + "\t" + probability + (values.isEmpty() ? "" : "\t" + String.join("\t", values)) + (syntheticType?"\tp_value_only":"")+"\n");
                    if(pOnly)pOnlyRows++;
                    metadata.retained(values);
                    eligible.add(feature);
                } catch (IOException invalid) {
                    if (!excludeInvalid) throw invalid;
                    excludedInvalid++;
                    excluded.write(feature + "\texcluded_invalid_row\t" + invalid.getMessage().replaceAll("[\\t\\r\\n]+", " ") + "\n");
                }
            }
        }
        Map<String, Object> normalization = new LinkedHashMap<>(metadata.audit());
        normalization.put("stage", "numerically_valid_before_effect_qc_and_branch_universe_selection");
        normalization.put("input_sha256", FollowupSupport.hash(input));
        normalization.put("normalized_sha256", FollowupSupport.hash(output));
        normalization.put("exclusions_sha256", FollowupSupport.hash(exclusions));
        normalization.put("input_rows", inputRows); normalization.put("retained_rows", eligible.size());
        normalization.put("excluded_invalid_rows", excludedInvalid); normalization.put("excluded_status_rows", excludedStatus);
        normalization.put("result_mode",resultMode);normalization.put("p_value_only_rows",pOnlyRows);
        StudyPublicEvidence.write(audit, normalization);
        return new Standardization(inputRows, excludedInvalid, excludedStatus,
            Set.copyOf(seen), Set.copyOf(eligible));
    }

    private static String optionalEffectColumn(Map<String,Object> columns,String key,DelimitedData table,String... aliases)throws IOException {
        if(columns.containsKey(key)){String name=text(columns.get(key));table.column(name);return name;}
        var matches=table.header().stream().filter(name->java.util.Arrays.stream(aliases).anyMatch(name::equalsIgnoreCase)).toList();if(matches.size()>1)throw new IOException("Ambiguous effect column in P-only input");return matches.isEmpty()?null:matches.get(0);
    }

    private static Path normalizationAudit(Path output) {
        Path directory = output.getParent();
        if (directory.getFileName().toString().equals("standard-results")) directory = directory.getParent();
        return directory.resolve("result-normalization").resolve(output.getFileName() + ".yaml");
    }

    private static List<String> normalizationOutputs(Context context, Path output) {
        Path audit = normalizationAudit(output);
        return List.of(audit, audit.resolveSibling(output.getFileName() + ".exclusions.tsv")).stream()
            .map(path -> context.outputDirectory().relativize(path).toString().replace('\\', '/')).toList();
    }

    private static List<Integer> columnIndexes(DelimitedData table,
            Object names, String label) {
        List<Integer> result = new ArrayList<>();
        for (String name : strings(names)) {
            if (!table.header().contains(name))
                throw new IllegalArgumentException(label
                    + " column is absent: " + name);
            result.add(table.column(name));
        }
        return result;
    }

    private static void validate(String[] row, List<Integer> finiteColumns,
            List<Integer> positiveColumns, List<Integer> nonnegativeColumns,
            List<Integer> probabilityColumns, DelimitedData table, Path input)
            throws IOException {
        for (int column : finiteColumns)
            finite(row[column], table.header().get(column), input);
        for (int column : positiveColumns)
            if (!(finite(row[column], table.header().get(column), input) > 0.0))
                throw new IOException(table.header().get(column)
                    + " must be positive in " + input);
        for (int column : nonnegativeColumns)
            if (finite(row[column], table.header().get(column), input) < 0.0)
                throw new IOException(table.header().get(column)
                    + " must be nonnegative in " + input);
        for (int column : probabilityColumns)
            probability(row[column], input);
    }

    private static void retainFeatures(Path path, Set<String> retained)
            throws IOException {
        DelimitedData table = DelimitedData.readResult(path);
        int id = table.column("feature_id");
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(String.join("\t", table.header()));
            writer.write('\n');
            for (String[] row : table.rows())
                if (retained.contains(row[id])) {
                    writer.write(String.join("\t", row));
                    writer.write('\n');
                }
        }
    }

    private static Map<String, Estimate> estimates(Path path) throws IOException {
        DelimitedData table = DelimitedData.readResult(path);
        int id = table.column("feature_id"), beta = table.column("beta");
        int se = table.column("se"), p = table.column("p_value");
        Map<String, Estimate> result = new LinkedHashMap<>();
        for (String[] row : table.rows())
            result.put(row[id], new Estimate(
                StudyResultShape.pOnly(table,row)?Double.NaN:finite(row[beta], "beta", path),
                StudyResultShape.pOnly(table,row)?Double.NaN:finite(row[se], "se", path),
                missing(row[p]) ? Double.NaN : probability(row[p], path)));
        return result;
    }

    private static Map<String, Double> adjusted(
            Map<String, Estimate> estimates, String method) {
        Map<String, Double> result = new LinkedHashMap<>();
        estimates.forEach((id, estimate) -> result.put(id, estimate.p));
        if (method.equals("NONE")) return result;
        var finite = new LinkedHashMap<String,Double>();
        estimates.forEach((id,estimate)->{if(Double.isFinite(estimate.p))finite.put(id,estimate.p);});
        result.putAll(StudyCohortResults.adjust(finite,method));
        return result;
    }

    private static Map<String, Path> results(Context context) throws IOException {
        Map<String, Path> result = new LinkedHashMap<>();
        StudyPrimaryResults.cohorts(context).forEach((id, source) -> result.put(id, source.path()));
        return StudyResultTables.universe(context, result, "filtered-family-inputs");
    }

    private static Map<String, Map<String, Object>> datasets(
            Map<String, Object> study) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Object value : list(study.get("datasets"), "datasets")) {
            Map<String, Object> dataset = map(value, "dataset");
            result.put(text(dataset.get("id")), dataset);
        }
        return result;
    }

    private static String cohort(Map<String, Object> dataset) {
        if (dataset == null || dataset.get("cohort") == null
                || String.valueOf(dataset.get("cohort")).isBlank())
            return "unspecified";
        return String.valueOf(dataset.get("cohort"));
    }

    private static String fileToken(String value) {
        String token = value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("^-+|-+$", "");
        return token.isEmpty() ? "dataset" : token;
    }

    private static double[] commonBounds(double[] left, double[] right) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (double value : left) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        for (double value : right) {
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        minimum = Math.min(minimum, 0.0);
        maximum = Math.max(maximum, 0.0);
        double spread = maximum - minimum;
        if (!(spread > 0.0)) spread = Math.max(1.0, Math.abs(maximum) * 0.2);
        double padding = spread * 0.06;
        return new double[] {minimum - padding, maximum + padding};
    }

    private static ReplicationMetrics replicationMetrics(Path path)
            throws IOException {
        if (path == null || !Files.isRegularFile(path))
            return new ReplicationMetrics(0, 0, Double.NaN);
        DelimitedData table = DelimitedData.readResult(path);
        int selectedColumn = table.column("discovery_selected");
        int replicatedColumn = table.column("replicated");
        int selected = 0, replicated = 0;
        for (String[] row : table.rows()) {
            if (Boolean.parseBoolean(row[selectedColumn])) selected++;
            if (Boolean.parseBoolean(row[replicatedColumn])) replicated++;
        }
        return new ReplicationMetrics(selected, replicated,
            selected == 0 ? Double.NaN : replicated / (double) selected);
    }

    private static MetaMetrics metaMetrics(Context context,Path path) throws IOException {
        if(path==null||!Files.isRegularFile(path))return new MetaMetrics(0,0,Double.NaN,Double.NaN,"unavailable",Double.NaN);
        var profile=StudyHeterogeneity.assess(context,path,StudyHeterogeneity.policy(context));double alpha=((Number)profile.summary().get("association_alpha")).doubleValue();int significant=(int)profile.table().adjusted().values().stream().filter(q->q<alpha).count();double mean=0;int n=0,high=0;if(profile.metric().equals("i_squared"))for(String id:profile.scope()){n++;mean+=(profile.values().get(id)-mean)/n;if(Boolean.TRUE.equals(profile.high().get(id)))high++;}
        return new MetaMetrics(profile.table().adjusted().size(),significant,n==0?Double.NaN:mean,n==0?Double.NaN:high/(double)n,profile.table().adjustment(),alpha);
    }

    private static Map<String, Object> metrics(ReplicationMetrics replication,
            MetaMetrics meta) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("discovery_selected_pairwise_tests", replication.selected());
        result.put("replicated_pairwise_tests", replication.replicated());
        result.put("replication_fraction", numeric(replication.fraction()));
        result.put("meta_tested_features", meta.tested());
        result.put("meta_significant_features",meta.significant());result.put("meta_adjustment",meta.adjustment());result.put("meta_alpha",numeric(meta.alpha()));
        if(meta.adjustment().equals("BH")&&meta.alpha()==.05)result.put("meta_BH_FDR_0_05_features", meta.significant());
        result.put("mean_i_squared", numeric(meta.meanISquared()));
        result.put("high_i_squared_fraction", numeric(
            meta.highISquaredFraction()));
        return result;
    }

    private static double difference(double value, double reference) {
        return Double.isFinite(value) && Double.isFinite(reference)
            ? value - reference : Double.NaN;
    }

    private static Object numeric(double value) {
        return Double.isFinite(value) ? value : "NA";
    }

    private static Map<String, Object> hypothesis(Map<String, Object> study,
            String id) {
        for (Object value : list(study.get("hypotheses"), "hypotheses")) {
            Map<String, Object> hypothesis = map(value, "hypothesis");
            if (id.equals(hypothesis.get("id"))) return hypothesis;
        }
        throw new IllegalArgumentException("Unknown hypothesis: " + id);
    }

    private static List<String> role(Map<String, Object> roles, String name) {
        return roles.get(name) == null ? List.of() : strings(roles.get(name));
    }

    private static String configured(Map<String, Object> columns, String key,
            List<String> header, String... defaults) {
        if (columns.get(key) != null) return text(columns.get(key));
        for (String candidate : defaults)
            if (header.contains(candidate)) return candidate;
        throw new IllegalArgumentException("No column mapping for " + key
            + "; available columns: " + header);
    }

    private static String omicsType(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "transcriptomics", "expression", "mirna", "lincrna",
                    "lncrna", "extracellular_rna", "single_cell_rna",
                    "spatial_transcriptomics" -> "expression";
            case "epigenomics", "ewas" -> "ewas";
            case "gwas", "genomics" -> "gwas";
            case "proteomics", "single_cell_protein",
                    "spatial_proteomics" -> "proteomics";
            default -> "generic";
        };
    }

    private static double finite(String value, String name, Path path)
            throws IOException {
        try {
            double result = Double.parseDouble(value);
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid " + name + " in " + path + ": " + value);
        }
    }

    private static double probability(String value, Path path)
            throws IOException {
        double result = finite(value, "p-value", path);
        if (result < 0.0 || result > 1.0)
            throw new IOException("P-values must be in [0,1] in " + path);
        return result;
    }

    private static boolean missing(String value) {
        return Set.of("", ".", "na", "n/a", "null", "nan")
            .contains(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean significant(
            double adjusted, double alpha, boolean strict) {
        return Double.isFinite(adjusted)
            && (strict ? adjusted < alpha : adjusted <= alpha);
    }

    private static boolean booleanValue(Object value, String name) {
        if (value instanceof Boolean result) return result;
        if (value instanceof String text) {
            if (text.equalsIgnoreCase("true")) return true;
            if (text.equalsIgnoreCase("false")) return false;
        }
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static String value(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "NA";
    }

    private static double number(Object value, String name) {
        double result = Double.parseDouble(String.valueOf(value));
        if (!Double.isFinite(result))
            throw new IllegalArgumentException(name + " must be finite");
        return result;
    }

    private static void probability(double value, String name) {
        if (value < 0.0 || value > 1.0)
            throw new IllegalArgumentException(name + " must be in [0,1]");
    }

    private static int positiveInteger(Object value, String name) {
        try {
            int result = Integer.parseInt(String.valueOf(value));
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
    }

    private static Map<String, Object> map(Object value, String name) {
        if (!(value instanceof Map<?, ?> raw))
            throw new IllegalArgumentException(name + " must be a mapping");
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Map<String, Object> optionalMap(Object value, String name) {
        return value == null ? new LinkedHashMap<>() : map(value, name);
    }

    private static List<Object> list(Object value, String name) {
        if (!(value instanceof Collection<?> raw))
            throw new IllegalArgumentException(name + " must be a list");
        return new ArrayList<>(raw);
    }

    private static List<String> strings(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof String text) return new ArrayList<>(List.of(text));
        return list(value, "value").stream().map(String::valueOf)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank())
            throw new IllegalArgumentException("Required text is absent");
        return String.valueOf(value);
    }

    private static <T> T required(Map<String, T> values, String key,
            String name) {
        T result = values.get(key);
        if (result == null)
            throw new IllegalArgumentException("Unknown " + name + ": " + key);
        return result;
    }

    private static String portable(Path root, Path path) {
        try {
            return root.relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        } catch (IllegalArgumentException differentRoot) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private record Estimate(double beta, double se, double p) { }

    private record ReplicationMetrics(int selected, int replicated,
            double fraction) { }

    private record MetaMetrics(int tested, int significant,
            double meanISquared, double highISquaredFraction,String adjustment,double alpha) { }

    record GeneMapping(String ensembl, String symbol) { }

    private record Candidate(String feature, String beta, double pValue,
            String iSquared) { }

    static final class Moment {
        private long n, selected, replicated, sameSign;
        private double x, y, xx, yy, xy,scaleX,scaleY;

        void add(double left, double right, boolean selectedValue,
                boolean replicatedValue) {
            double nextX=Math.max(scaleX,Math.abs(left)),nextY=Math.max(scaleY,Math.abs(right));
            if(nextX>scaleX){double factor=scaleX/nextX;x*=factor;xx*=factor*factor;xy*=factor;scaleX=nextX;}
            if(nextY>scaleY){double factor=scaleY/nextY;y*=factor;yy*=factor*factor;xy*=factor;scaleY=nextY;}
            double a=scaleX>0?left/scaleX:0,b=scaleY>0?right/scaleY:0,dx=a-x,dy=b-y;
            n++;x+=dx/n;y+=dy/n;xx+=dx*(a-x);yy+=dy*(b-y);xy+=dx*(b-y);
            if (Math.signum(left) == Math.signum(right)) sameSign++;
            if (selectedValue) selected++;
            if (replicatedValue) replicated++;
        }

        double correlation() {
            return xx>0&&yy>0?Math.max(-1,Math.min(1,(xy/Math.sqrt(xx))/Math.sqrt(yy))):Double.NaN;
        }

        double replicationFraction() {
            return selected == 0 ? Double.NaN
                : replicated / (double) selected;
        }

        double slope() {
            return xx>0?restoreRatio(xy/xx):Double.NaN;
        }

        double intercept() {
            return n>0&&xx>0?(y-(xy/xx)*x)*scaleY:Double.NaN;
        }

        double standardDeviationRatio() {
            return xx>0&&yy>=0?restoreRatio(Math.sqrt(yy)/Math.sqrt(xx)):Double.NaN;
        }
        private double restoreRatio(double value){
            if(value==0)return 0;
            double ratio=scaleY/scaleX;if(Double.isFinite(ratio)&&ratio>0)return value*ratio;
            return Math.copySign(Math.exp(Math.log(Math.abs(value))+Math.log(scaleY)-Math.log(scaleX)),value);
        }

        double sameSignFraction() {
            return n == 0 ? Double.NaN : sameSign / (double) n;
        }
    }

    private record Standardization(int inputRows, int excludedInvalid,
            int excludedStatus, Set<String> allFeatures,
            Set<String> eligibleFeatures) { }
}
