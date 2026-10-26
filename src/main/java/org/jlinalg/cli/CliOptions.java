/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jlinalg.compute.BackendPolicy;

/** Parsed command-line configuration with deliberately dependency-free syntax. */
final class CliOptions {
    Path omics;
    Path phenotype;
    Path output;
    Path log;
    Path annotation;
    Path pedigree;
    Path grm;
    Path bgenSamples;
    Path hweSampleFile;
    Path ploidySamples;
    String humanPloidyRegion,humanPloidyBuild;
    String haploidSourceScale="observed",invalidPloidyCalls="error",unknownPloidySamples="error";
    String idColumn;
    String formula;
    String model = "auto";
    String family = "gaussian";
    String link;
    String ties = "efron";
    String degreesOfFreedom = "auto";
    String omicsType = "auto";
    String varianceComponents = "auto";
    BackendPolicy backend = BackendPolicy.PREFERRED;
    String annotationId;
    String individualId;
    String pedigreeId;
    String sireId;
    String damId;
    String pedigreeFamilyId;
    String caseValue;
    String controlValue;
    String hweSamples = "all";
    String hweFilterScope = "all";
    final List<String> annotationColumns = new ArrayList<>();
    final List<String> transforms = new ArrayList<>();
    final List<Path> transformPlugins = new ArrayList<>();
    double minimumMaf;
    double maximumMaf = 0.5;
    double minimumMac = 20;
    double maximumMac = Double.POSITIVE_INFINITY;
    double maximumMissingRate = 1.0;
    double minimumInfo = Double.NaN;
    double minimumHweP;
    double minimumMissingnessP;
    int maximumMissingnessStates=100000;
    String genotypeKind="auto";
    boolean autosomesOnly;
    String binaryTest="efficient-score";
    int firthMaximumIterations=300;
    double firthTolerance=1e-8;
    boolean explicitBinaryTest,explicitFirthControls;
    String exactStrataColumn;
    int exactMaximumStates=200000,exactEffectMaximumParameters=50;
    long exactMaximumWork=50000000;
    boolean explicitExactControls;
    int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
    int blockSize;
    int checkpointEvery = 1000;
    boolean resume;
    boolean dryRun;
    boolean explain;
    boolean overwrite;
    boolean noLog;
    boolean help;
    boolean version;
    boolean conditionalGwasSummary;
    int scoreBlockSize = 64;
    boolean explicitScoreBlockSize;
    String scoreGenomeBuild;
    final List<String> conditionOn = new ArrayList<>();

    static CliOptions parse(String[] arguments) {
        CliOptions result = new CliOptions();
        for (int index = 0; index < arguments.length; index++) {
            String option = arguments[index];
            switch (option) {
                case "--ploidy-samples" -> result.ploidySamples=path(value(arguments,++index,option));
                case "--human-ploidy-region" -> result.humanPloidyRegion=value(arguments,++index,option);
                case "--human-ploidy-build" -> result.humanPloidyBuild=value(arguments,++index,option);
                case "--haploid-source-scale" -> result.haploidSourceScale=value(arguments,++index,option);
                case "--invalid-ploidy-calls" -> result.invalidPloidyCalls=value(arguments,++index,option);
                case "--unknown-ploidy-samples" -> result.unknownPloidySamples=value(arguments,++index,option);
                case "--conditional-gwas-summary" -> result.conditionalGwasSummary = true;
                case "--score-block-size" -> {
                    result.scoreBlockSize = integer(arguments, ++index, option);
                    result.explicitScoreBlockSize = true;
                }
                case "--score-genome-build" -> result.scoreGenomeBuild = value(arguments, ++index, option);
                case "--condition-on" -> addList(result.conditionOn, value(arguments, ++index, option));
                case "--help", "-h" -> result.help = true;
                case "--version" -> result.version = true;
                case "--resume" -> result.resume = true;
                case "--dry-run" -> result.dryRun = true;
                case "--explain" -> result.explain = true;
                case "--explains" -> throw new IllegalArgumentException(
                    "--explains is not an option; use --explain (singular)");
                case "--overwrite" -> result.overwrite = true;
                case "--no-log" -> result.noLog = true;
                case "--omics" -> result.omics = path(value(arguments, ++index, option));
                case "--pheno" -> result.phenotype = path(value(arguments, ++index, option));
                case "--out" -> result.output = path(value(arguments, ++index, option));
                case "--log" -> result.log = path(value(arguments, ++index, option));
                case "--annot" -> result.annotation = path(value(arguments, ++index, option));
                case "--pedigree" ->
                    result.pedigree = path(value(arguments, ++index, option));
                case "--grm" ->
                    result.grm = path(value(arguments, ++index, option));
                case "--sample-file" ->
                    result.bgenSamples = path(value(arguments, ++index, option));
                case "--id" -> result.idColumn = value(arguments, ++index, option);
                case "--formula" -> result.formula = value(arguments, ++index, option);
                case "--model" -> result.model = lower(value(arguments, ++index, option));
                case "--family" -> result.family = lower(value(arguments, ++index, option));
                case "--link" -> result.link = lower(value(arguments, ++index, option));
                case "--ties" ->
                    result.ties = lower(value(arguments, ++index, option));
                case "--df" ->
                    result.degreesOfFreedom = lower(value(arguments, ++index, option));
                case "--omics-type" ->
                    result.omicsType = lower(value(arguments, ++index, option));
                case "--variance-components" ->
                    result.varianceComponents = lower(value(arguments, ++index, option));
                case "--backend" -> result.backend = backend(
                    value(arguments, ++index, option));
                case "--annot-id" ->
                    result.annotationId = value(arguments, ++index, option);
                case "--individual-id" ->
                    result.individualId = value(arguments, ++index, option);
                case "--pedigree-id" ->
                    result.pedigreeId = value(arguments, ++index, option);
                case "--sire-id" ->
                    result.sireId = value(arguments, ++index, option);
                case "--dam-id" ->
                    result.damId = value(arguments, ++index, option);
                case "--pedigree-family-id" ->
                    result.pedigreeFamilyId = value(arguments, ++index, option);
                case "--annot-cols" -> addList(result.annotationColumns,
                    value(arguments, ++index, option));
                case "--transform" ->
                    result.transforms.add(value(arguments, ++index, option));
                case "--transform-plugin" ->
                    result.transformPlugins.add(
                        path(value(arguments, ++index, option)));
                case "--case-value" ->
                    result.caseValue = value(arguments, ++index, option);
                case "--control-value" ->
                    result.controlValue = value(arguments, ++index, option);
                case "--hwe-samples" ->
                    result.hweSamples = lower(value(arguments, ++index, option));
                case "--hwe-filter-scope" ->
                    result.hweFilterScope = lower(value(arguments, ++index, option));
                case "--hwe-sample-file" -> result.hweSampleFile=path(value(arguments,++index,option));
                case "--genotype-kind" -> result.genotypeKind=lower(value(arguments,++index,option));
                case "--autosomes-only" -> result.autosomesOnly=true;
                case "--min-missingness-p" -> result.minimumMissingnessP=number(arguments,++index,option);
                case "--missingness-max-states" -> result.maximumMissingnessStates=positiveInteger(value(arguments,++index,option),option);
                case "--min-maf" ->
                    result.minimumMaf = number(arguments, ++index, option);
                case "--max-maf" ->
                    result.maximumMaf = number(arguments, ++index, option);
                case "--min-mac" ->
                    result.minimumMac = number(arguments, ++index, option);
                case "--max-mac" ->
                    result.maximumMac = number(arguments, ++index, option);
                case "--max-marker-missing" ->
                    result.maximumMissingRate = number(arguments, ++index, option);
                case "--min-info" ->
                    result.minimumInfo = number(arguments, ++index, option);
                case "--min-hwe-p" ->
                    result.minimumHweP = number(arguments, ++index, option);
                case "--binary-test" -> {result.binaryTest=lower(value(arguments,++index,option));result.explicitBinaryTest=true;}
                case "--exact-strata-column" -> {result.exactStrataColumn=value(arguments,++index,option);result.explicitExactControls=true;}
                case "--exact-max-states" -> {result.exactMaximumStates=integer(arguments,++index,option);result.explicitExactControls=true;}
                case "--exact-max-work" -> {result.exactMaximumWork=Long.parseLong(value(arguments,++index,option));result.explicitExactControls=true;}
                case "--exact-effect-max-parameters" -> {result.exactEffectMaximumParameters=Integer.parseInt(value(arguments,++index,option));result.explicitExactControls=true;}
                case "--firth-max-iterations" -> {result.firthMaximumIterations=integer(arguments,++index,option);result.explicitFirthControls=true;}
                case "--firth-tolerance" -> {result.firthTolerance=number(arguments,++index,option);result.explicitFirthControls=true;}
                case "--threads" ->
                    result.threads = integer(arguments, ++index, option);
                case "--checkpoint-every" ->
                    result.checkpointEvery = integer(arguments, ++index, option);
                case "--block-size" -> {
                    String requested = lower(value(arguments, ++index, option));
                    result.blockSize = requested.equals("auto") ? 0
                        : positiveInteger(requested, option);
                }
                default -> throw new IllegalArgumentException(
                    "unknown option: " + option);
            }
        }
        return result;
    }

    void validateForRun() {
        if (phenotype == null || idColumn == null || formula == null
                || output == null)
            throw new IllegalArgumentException(
                "--pheno, --id, --formula, and --out are required");
        if (threads < 1 || checkpointEvery < 1)
            throw new IllegalArgumentException(
                "--threads and --checkpoint-every must be positive");
        if(!Double.isFinite(minimumHweP)||!Double.isFinite(minimumMissingnessP)||minimumMissingnessP<0||minimumMissingnessP>1||maximumMissingnessStates<1)
            throw new IllegalArgumentException("QC probabilities must be finite in [0,1] and the exact-state bound positive");
        if(!java.util.Set.of("auto","hard_calls","imputed","sequenced").contains(genotypeKind))throw new IllegalArgumentException("--genotype-kind must be auto, hard_calls, imputed or sequenced");
        if(omics==null&&(minimumHweP>0||minimumMissingnessP>0||hweSampleFile!=null||autosomesOnly||!genotypeKind.equals("auto")))throw new IllegalArgumentException("Genotype QC controls require --omics genotype input");
        if (minimumMaf < 0 || maximumMaf > 0.5
                || minimumMaf > maximumMaf || minimumMac < 0
                || minimumMac > maximumMac
                || maximumMissingRate < 0 || maximumMissingRate > 1
                || minimumHweP < 0 || minimumHweP > 1)
            throw new IllegalArgumentException("filter thresholds are invalid");
        if (!varianceComponents.equals("auto")
                && !varianceComponents.equals("null-model")
                && !varianceComponents.equals("refit"))
            throw new IllegalArgumentException(
                "--variance-components must be auto, null-model, or refit");
        validateFamilyAndLink();
        if(!java.util.Set.of("efficient-score","firth-lrt","exact-conditional").contains(binaryTest))throw new IllegalArgumentException("--binary-test must be efficient-score, firth-lrt or exact-conditional");
        if((explicitBinaryTest||explicitFirthControls||explicitExactControls)&&(omics==null||!family.equals("binomial")))throw new IllegalArgumentException("Binary-test controls require genotype input with --family binomial");
        if(explicitFirthControls&&binaryTest.equals("efficient-score"))throw new IllegalArgumentException("Firth controls require a Firth effect/inference route");
        if(explicitExactControls&&!binaryTest.equals("exact-conditional"))throw new IllegalArgumentException("Exact controls require --binary-test exact-conditional");
        if(exactStrataColumn!=null&&!exactStrataColumn.matches("[A-Za-z_][A-Za-z0-9_.]{0,199}"))throw new IllegalArgumentException("Exact stratum column must be a bounded column identifier");
        if(exactEffectMaximumParameters<0||exactEffectMaximumParameters>10000||exactMaximumWork>9007199254740991L)throw new IllegalArgumentException("Exact effect/operation bounds are invalid");
        new org.jlinalg.regression.ExactConditionalLogit.Options(exactMaximumStates,exactMaximumWork,1e-7);
        new org.jlinalg.regression.FirthLogistic.Options(firthMaximumIterations,40,firthTolerance,5);
        if(!binaryTest.equals("efficient-score")&&(conditionalGwasSummary||resume))throw new IllegalArgumentException("Firth/exact inference does not supply ordinary null score exports or resumable checkpoint state");
        if(conditionalGwasSummary) {
            if(omics==null || scoreGenomeBuild==null || scoreGenomeBuild.isBlank())
                throw new IllegalArgumentException("--conditional-gwas-summary requires genotype --omics and --score-genome-build");
            if(resume)throw new IllegalArgumentException("conditional score export does not support --resume");
            if(scoreBlockSize>512)throw new IllegalArgumentException("--score-block-size must be at most 512");
            if(conditionOn.size()>512)throw new IllegalArgumentException("--condition-on supports at most 512 variants");
            if(model.equals("ols") && !family.equals("gaussian"))throw new IllegalArgumentException("OLS score export requires --family gaussian");
        } else if(!conditionOn.isEmpty() || scoreGenomeBuild!=null || explicitScoreBlockSize)
            throw new IllegalArgumentException("score export controls require --conditional-gwas-summary");
        if(new java.util.HashSet<>(conditionOn).size()!=conditionOn.size())
            throw new IllegalArgumentException("--condition-on variants must be distinct");
        if (!hweSamples.equals("all"))
            throw new IllegalArgumentException(
                "--hwe-samples currently supports only its default, all");
        if (!hweFilterScope.equals("all")
                && !hweFilterScope.equals("controls")
                && !hweFilterScope.equals("cases"))
            throw new IllegalArgumentException(
                "--hwe-filter-scope must be all, controls, or cases");
        if (pedigree != null && (pedigreeId == null
                || sireId == null || damId == null))
            throw new IllegalArgumentException(
                "--pedigree requires --pedigree-id, --sire-id, and --dam-id");
        humanPloidyProfile();
        PipelinePaths.validate(this);
    }
    org.jlinalg.pipeline.HumanPloidyProfile humanPloidyProfile(){if(humanPloidyRegion==null){if(ploidySamples!=null||humanPloidyBuild!=null||!haploidSourceScale.equals("observed")||!invalidPloidyCalls.equals("error")||!unknownPloidySamples.equals("error"))throw new IllegalArgumentException("Ploidy controls require --human-ploidy-region");return null;}if(ploidySamples==null||humanPloidyBuild==null)throw new IllegalArgumentException("Human ploidy requires --ploidy-samples and --human-ploidy-build");var profile=new org.jlinalg.pipeline.HumanPloidyProfile(humanPloidyRegion,humanPloidyBuild,haploidSourceScale,invalidPloidyCalls,unknownPloidySamples);if(autosomesOnly||conditionalGwasSummary||resume||grm!=null||pedigree!=null)throw new IllegalArgumentException("Registered human ploidy contrasts use independent OLS/GLM without autosome-only, mixed, resume or conditional-summary controls");if(profile.haploid()&&minimumHweP>0)throw new IllegalArgumentException("Diploid HWE cannot filter a haploid contrast");return profile;}

    private void validateFamilyAndLink() {
        String canonical = switch (family) {
            case "gaussian" -> "identity";
            case "binomial", "quasi-binomial" -> "logit";
            case "probit", "binomial-probit" -> "probit";
            case "poisson", "gamma", "inverse-gaussian", "quasi-poisson" ->
                "log";
            default -> throw new IllegalArgumentException(
                "unsupported family: " + family);
        };
        if (link != null && !link.equals(canonical))
            throw new IllegalArgumentException(
                "--family " + family + " uses the fixed " + canonical
                    + " link; noncanonical --link overrides are not available"
                    + " in this CLI");
    }

    Path logPath() {
        return log != null ? log
            : Path.of(output.toString() + ".log");
    }

    Path manifestPath() {
        return Path.of(output.toString() + ".manifest.json");
    }
    Path scoreCovariancePath() { return Path.of(output + ".score-cov.tsv"); }
    Path scoreManifestPath() { return Path.of(output + ".score-manifest.json"); }

    private static Path path(String value) { return Path.of(value); }
    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
    private static BackendPolicy backend(String value) {
        try {
            return BackendPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                "--backend must be preferred, cholmod, gpu, cuda, opencl, "
                    + "vulkan, onemkl, openblas, auto, or cpu: " + value,
                failure);
        }
    }
    private static void addList(List<String> target, String text) {
        for (String value : text.split(",", -1)) {
            if (!value.isBlank()) target.add(value.trim());
        }
    }
    private static double number(String[] values, int index, String option) {
        String token = value(values, index, option);
        try {
            double parsed = Double.parseDouble(token);
            if (!Double.isFinite(parsed))
                throw new NumberFormatException("not finite");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                option + " requires a finite number: " + token, exception);
        }
    }
    private static int integer(String[] values, int index, String option) {
        return positiveInteger(value(values, index, option), option);
    }
    private static int positiveInteger(String token, String option) {
        try {
            int parsed = Integer.parseInt(token);
            if (parsed < 1) throw new NumberFormatException("not positive");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                option + " requires a positive integer: " + token, exception);
        }
    }
    private static String value(String[] values, int index, String option) {
        if (index >= values.length)
            throw new IllegalArgumentException(option + " requires a value");
        return values[index];
    }
}
