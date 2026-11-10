/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.SnprelateRequest.need;

/** Explicit GENESIS2.42.0 scientific requests; paths and model choices never imply a paper's settings. */
final class GenesisRequest {
    private GenesisRequest() { }
    record MatrixInput(Path values, Path samples, String name) { }
    record Inputs(Path bfile, Path phenotypes, MatrixInput kinship, MatrixInput divergence,
                  Path selected, Path forced, Path pcs, Path training, List<MatrixInput> covariance) { }
    record Pca(String handle, double kinshipThreshold, double divergenceThreshold, int components, String algorithm, String solver, boolean bayesian,
               int auxiliary, int iterations, int seed, boolean removeMono, double maf, double missing) { }
    record Relatedness(List<String> pcColumns, String scale, boolean ibd, int sampleBlock, int snpBlock,
                       double maf, String bound, boolean smallSample, int seed,
                       boolean covariance, Double covarianceThreshold, double scaleKin) { }
    record Model(String outcome, List<String> covariates, String group, String family, boolean twoStage,
                 String normalisation, String rescale, List<Double> start, double tolerance, int iterations,
                 int emIterations, boolean dropZeros, String alignment, String test, String coding,
                 double recalcThreshold, String interaction, boolean maleDiploid, int snpBlock) { }
    record Request(String mode, Path profile, Path out, String layout, Inputs inputs, Pca pca,
                   Relatedness relatedness, Model model, SnprelateRequest.Limits limits) { }

    static Path path(Path base, Object value) throws IOException {
        String text = text(value);
        need(!text.isEmpty() && text.chars().noneMatch(c -> c < 32 || c == 127), "Invalid explicit GENESIS path");
        return base.resolve(text).toAbsolutePath().normalize();
    }
    static Path optionalPath(Path base, Object value) throws IOException { return value == null ? null : path(base, value); }
    static String choice(Object value, String... choices) throws IOException {
        String chosen = text(value); need(Set.of(choices).contains(chosen), "Unsupported GENESIS scientific choice: " + chosen); return chosen;
    }
    static boolean bool(Object value) throws IOException { return SnprelateRequest.bool(value); }
    static double number(Object value) throws IOException { return SnprelateRequest.numeric(value); }
    static double finite(Object value) throws IOException { double d = number(value); need(Double.isFinite(d), "Finite explicit GENESIS numeric choice required"); return d; }
    static String identifier(Object value) throws IOException {
        String name = text(value); need(name.matches("[A-Za-z][A-Za-z0-9_]*"), "Use a literal R-safe model/column name; formulas, interactions and renaming are not inferred"); return name;
    }
    static List<String> names(Object value) throws IOException {
        need(value instanceof List<?>, "Explicit JSON column-name array required"); List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) result.add(identifier(item));
        need(new java.util.HashSet<>(result).size() == result.size(), "Duplicate declared model/PC columns"); return List.copyOf(result);
    }
    static MatrixInput matrix(Path base, Object value, boolean named) throws IOException {
        if (value == null) return null;
        Map<?, ?> input = object(value); if (named) keys(input, "values", "samples", "name"); else keys(input, "values", "samples");
        return new MatrixInput(path(base, input.get("values")), path(base, input.get("samples")), named ? identifier(input.get("name")) : "");
    }
    static Request parse(Object value, Path base) throws IOException {
        Map<?, ?> top = object(value); keys(top, "schema_version", "version", "mode", "runtime_profile", "out", "threads", "layout", "inputs", "options", "limits");
        integer(top.get("schema_version"), 1, 1); need(text(top.get("version")).equals("2.42.0"), "Require explicit GENESIS2.42.0; historical paper versions are not substituted");
        integer(top.get("threads"), 1, 1); String mode = choice(top.get("mode"), "pcair", "pcrelate", "null-model", "association");
        String layout = choice(top.get("layout"), "snp-first", "sample-first"); Map<?, ?> input = object(top.get("inputs")), options = object(top.get("options"));
        Path bfile = null, phenotypes = null, selected = null, forced = null, pcs = null, training = null;
        MatrixInput kinship = null, divergence = null; List<MatrixInput> covariance = new ArrayList<>(); Pca pca = null; Relatedness relatedness = null; Model model = null;
        if (mode.equals("pcair")) {
            keys(input, "bfile", "kinship", "divergence", "sample_selection", "forced_unrelated");
            bfile = path(base, input.get("bfile")); kinship = matrix(base, input.get("kinship"), false); divergence = matrix(base, input.get("divergence"), false);
            selected = optionalPath(base, input.get("sample_selection")); forced = optionalPath(base, input.get("forced_unrelated"));
            need(kinship != null || divergence == null, "Divergence matrix without declared kinship is not interpreted as a partition model");
            keys(options, "gds_handle", "kinship_threshold", "divergence_threshold", "eigen_count", "algorithm", "eigen_method", "bayesian", "aux_dim", "iterations", "seed", "remove_monomorphic", "maf", "missing_rate");
            pca = new Pca(choice(options.get("gds_handle"), "gds.class", "SNPGDSFileClass"), finite(options.get("kinship_threshold")), finite(options.get("divergence_threshold")), (int) integer(options.get("eigen_count"), 0, 5000),
                choice(options.get("algorithm"), "exact", "randomized"), choice(options.get("eigen_method"), "DSPEVX", "DSPEV"), bool(options.get("bayesian")),
                (int) integer(options.get("aux_dim"), 1, 5000), (int) integer(options.get("iterations"), 1, 100), (int) integer(options.get("seed"), 0, Integer.MAX_VALUE),
                bool(options.get("remove_monomorphic")), number(options.get("maf")), number(options.get("missing_rate")));
            if (pca.algorithm().equals("randomized")) need(!pca.bayesian() && pca.solver().equals("DSPEVX") && pca.components() > 0,
                "Randomized SNPRelate PCA requires positive components, bayesian=false and the explicit unused DSPEVX solver sentinel");
        } else if (mode.equals("pcrelate")) {
            keys(input, "bfile", "pcs", "training_samples", "sample_selection"); bfile = path(base, input.get("bfile")); pcs = path(base, input.get("pcs"));
            training = optionalPath(base, input.get("training_samples")); selected = optionalPath(base, input.get("sample_selection"));
            keys(options, "pc_columns", "scale", "ibd_probabilities", "sample_block_size", "snp_block_size", "maf_threshold", "maf_bound_method", "small_sample_correct", "seed", "covariance_output");
            List<String> columns = names(options.get("pc_columns")); need(!columns.isEmpty(), "Declare ancestry PC columns explicitly; none are inferred or recomputed");
            double maf = finite(options.get("maf_threshold")); need(maf >= 0 && maf < .5, "PC-Relate MAF cutoff must be explicit in0..0.5");
            boolean export = options.get("covariance_output") != null; Double threshold = null; double scaleKin = 0;
            if (export) {
                Map<?, ?> c = object(options.get("covariance_output")); keys(c, "threshold", "scale_kin");
                if (c.get("threshold") != null) threshold = finite(c.get("threshold")); scaleKin = finite(c.get("scale_kin"));
                need(scaleKin > 0, "Declare a positive covariance scaling; threshold applies after source scaling");
            }
            relatedness = new Relatedness(columns, choice(options.get("scale"), "overall", "variant", "none"), bool(options.get("ibd_probabilities")),
                (int) integer(options.get("sample_block_size"), 2, 5000), (int) integer(options.get("snp_block_size"), 1, 1000000), maf,
                choice(options.get("maf_bound_method"), "filter", "truncate"), bool(options.get("small_sample_correct")), (int) integer(options.get("seed"), 0, Integer.MAX_VALUE), export, threshold, scaleKin);
        } else {
            if (mode.equals("association")) { keys(input, "bfile", "phenotypes", "covariances"); bfile = path(base, input.get("bfile")); }
            else keys(input, "phenotypes", "covariances");
            phenotypes = path(base, input.get("phenotypes")); need(input.get("covariances") instanceof List<?>, "Explicit covariance array required; use [] for the original regression model");
            Set<String> seen = new java.util.HashSet<>();
            for (Object item : (List<?>) input.get("covariances")) {
                MatrixInput c = matrix(base, item, true); need(c != null && seen.add(c.name()), "Duplicate or null covariance input"); covariance.add(c);
            }
            need(covariance.size() <= 8, "At most eight explicitly supplied covariance matrices in this profile");
            Map<?, ?> fit = options;
            String test = "", coding = "", interaction = null; boolean maleDiploid = true; double recalc = 0; int snpBlock = 0;
            if (mode.equals("association")) {
                keys(options, "model", "test", "coding", "recalc_p_threshold", "interaction", "male_diploid", "snp_block_size"); fit = object(options.get("model"));
                test = choice(options.get("test"), "Score", "Score.SPA", "BinomiRare", "CMP"); coding = choice(options.get("coding"), "additive", "dominant", "recessive");
                recalc = finite(options.get("recalc_p_threshold")); need(recalc > 0 && recalc <= 1, "Explicit recalculation P cutoff must be in0..1");
                if (options.get("interaction") != null) interaction = identifier(options.get("interaction"));
                maleDiploid = bool(options.get("male_diploid")); snpBlock = (int) integer(options.get("snp_block_size"), 1, 1000000);
            }
            keys(fit, "outcome", "covariates", "group_variable", "family", "two_stage", "normalisation", "rescale", "start", "tolerance", "max_iterations", "EM_iterations", "drop_zeros", "covariance_alignment");
            String outcome = identifier(fit.get("outcome")); List<String> covariates = names(fit.get("covariates")); need(!covariates.contains(outcome), "Outcome cannot also be a declared covariate");
            String group = fit.get("group_variable") == null ? null : identifier(fit.get("group_variable"));
            String family = choice(fit.get("family"), "gaussian", "binomial", "poisson"); boolean twoStage = bool(fit.get("two_stage"));
            need(!twoStage || family.equals("gaussian"), "GENESIS two-stage transformation requires the declared Gaussian family");
            List<Double> start = null;
            if (fit.get("start") != null) {
                need(fit.get("start") instanceof List<?>, "Explicit source variance-start vector or null required"); List<Double> parsed = new ArrayList<>();
                for (Object v : (List<?>) fit.get("start")) { double d = finite(v); need(d >= 0, "Negative variance start is outside this typed profile"); parsed.add(d); }
                need(!parsed.isEmpty() && parsed.size() <= 100, "Declared variance-start capacity exceeded"); start = List.copyOf(parsed);
            }
            double tolerance = finite(fit.get("tolerance")); need(tolerance > 0 && tolerance <= 1, "Declare a positive model convergence tolerance at most1");
            int iterations = (int) integer(fit.get("max_iterations"), 1, 10000), em = (int) integer(fit.get("EM_iterations"), 0, 10000);
            need(em <= iterations, "EM iteration count exceeds the declared maximum");
            if (interaction != null) need(covariates.contains(interaction), "Explicit GxE column must be in the declared null-model design");
            model = new Model(outcome, covariates, group, family, twoStage, choice(fit.get("normalisation"), "all", "by.group"),
                choice(fit.get("rescale"), "residSD", "none", "model"), start, tolerance, iterations, em, bool(fit.get("drop_zeros")),
                choice(fit.get("covariance_alignment"), "require-order", "phenotype-order"), test, coding, recalc, interaction, maleDiploid, snpBlock);
        }
        Map<?, ?> l = object(top.get("limits")); keys(l, "input_bytes", "output_bytes", "output_files", "samples", "variants", "rows", "pair_variant_operations", "working_bytes", "wall_seconds");
        var limits = new SnprelateRequest.Limits(integer(l.get("input_bytes"), 1, 1000000000), integer(l.get("output_bytes"), 1, 10000000000L),
            (int) integer(l.get("output_files"), 1, 100000), (int) integer(l.get("samples"), 2, 5000), (int) integer(l.get("variants"), 1, 1000000),
            (int) integer(l.get("rows"), 1, 100000000), integer(l.get("pair_variant_operations"), 1, Long.MAX_VALUE), integer(l.get("working_bytes"), 1, Long.MAX_VALUE),
            (int) integer(l.get("wall_seconds"), 1, 604800));
        return new Request(mode, path(base, top.get("runtime_profile")), path(base, top.get("out")), layout,
            new Inputs(bfile, phenotypes, kinship, divergence, selected, forced, pcs, training, List.copyOf(covariance)), pca, relatedness, model, limits);
    }

    static Map<String, String> parameters(Request request) {
        var result = new LinkedHashMap<String, String>(); result.put("mode", request.mode()); result.put("layout", request.layout()); result.put("threads", "1");
        var inputs = request.inputs(); result.put("has_genotypes", Boolean.toString(inputs.bfile() != null)); result.put("has_phenotypes", Boolean.toString(inputs.phenotypes() != null));
        result.put("has_kinship", Boolean.toString(inputs.kinship() != null)); result.put("has_divergence", Boolean.toString(inputs.divergence() != null));
        result.put("has_selection", Boolean.toString(inputs.selected() != null)); result.put("has_forcing", Boolean.toString(inputs.forced() != null));
        result.put("has_training", Boolean.toString(inputs.training() != null)); result.put("covariance_names", inputs.covariance().stream().map(MatrixInput::name).collect(java.util.stream.Collectors.joining(",")));
        if (request.pca() != null) {
            var p = request.pca(); result.put("gds_handle", p.handle()); result.put("kinship_threshold", SnprelateRequest.number(p.kinshipThreshold()));
            result.put("divergence_threshold", SnprelateRequest.number(p.divergenceThreshold())); result.put("eigen_count", Integer.toString(p.components()));
            result.put("algorithm", p.algorithm()); result.put("eigen_method", p.solver()); result.put("bayesian", Boolean.toString(p.bayesian()));
            result.put("aux_dim", Integer.toString(p.auxiliary())); result.put("iterations", Integer.toString(p.iterations())); result.put("seed", Integer.toString(p.seed()));
            result.put("remove_monomorphic", Boolean.toString(p.removeMono())); result.put("maf", SnprelateRequest.number(p.maf())); result.put("missing_rate", SnprelateRequest.number(p.missing()));
        }
        if (request.relatedness() != null) {
            var p = request.relatedness(); result.put("pc_columns", String.join(",", p.pcColumns())); result.put("scale", p.scale()); result.put("ibd_probabilities", Boolean.toString(p.ibd()));
            result.put("sample_block_size", Integer.toString(p.sampleBlock())); result.put("snp_block_size", Integer.toString(p.snpBlock())); result.put("maf_threshold", SnprelateRequest.number(p.maf()));
            result.put("maf_bound_method", p.bound()); result.put("small_sample_correct", Boolean.toString(p.smallSample())); result.put("seed", Integer.toString(p.seed()));
            result.put("covariance_output", Boolean.toString(p.covariance())); result.put("covariance_threshold", p.covarianceThreshold() == null ? "NULL" : SnprelateRequest.number(p.covarianceThreshold()));
            result.put("scale_kin", SnprelateRequest.number(p.scaleKin()));
        }
        if (request.model() != null) {
            var p = request.model(); result.put("outcome", p.outcome()); result.put("covariates", String.join(",", p.covariates())); result.put("group_variable", p.group() == null ? "NULL" : p.group());
            result.put("family", p.family()); result.put("two_stage", Boolean.toString(p.twoStage())); result.put("normalisation", p.normalisation()); result.put("rescale", p.rescale());
            result.put("start", p.start() == null ? "NULL" : p.start().stream().map(SnprelateRequest::number).collect(java.util.stream.Collectors.joining(",")));
            result.put("tolerance", SnprelateRequest.number(p.tolerance())); result.put("max_iterations", Integer.toString(p.iterations())); result.put("EM_iterations", Integer.toString(p.emIterations()));
            result.put("drop_zeros", Boolean.toString(p.dropZeros())); result.put("covariance_alignment", p.alignment()); result.put("test", p.test()); result.put("coding", p.coding());
            result.put("recalc_p_threshold", SnprelateRequest.number(p.recalcThreshold())); result.put("interaction", p.interaction() == null ? "NULL" : p.interaction());
            result.put("male_diploid", Boolean.toString(p.maleDiploid())); result.put("snp_block_size", Integer.toString(p.snpBlock()));
        }
        return result;
    }
}
