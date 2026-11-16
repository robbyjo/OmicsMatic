/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Literal argument arrays for the unchanged selected CLI programs. */
final class PopsOriginalCommands {
    record Job(String name, String script, List<String> arguments) { Job { arguments = List.copyOf(arguments); } }
    private PopsOriginalCommands() { }
    static List<Job> jobs(Path root, PopsOriginalFiles.Config c) throws IOException {
        List<Job> jobs = new ArrayList<>();
        if (c.legacy()) {
            if (!c.get("task").equals("predict")) jobs.add(new Job("select", "0.1/pops.feature_selection.py", List.of("--features", input(root, c, "features_text"), "--gene_results", input(root, c, "magma_prefix"), "--out", root.resolve("raw/selection").toString())));
            if (c.predicts()) for (int chromosome : c.chromosomes()) {
                String selected = c.get("task").equals("select-predict") ? root.resolve("raw/selection.features").toString() : input(root, c, "selected_features");
                jobs.add(new Job("predict-" + String.format(Locale.ROOT, "%02d", chromosome), "0.1/pops.predict_scores.py", List.of("--gene_loc", input(root, c, "gene_location"), "--gene_results", input(root, c, "magma_prefix"), "--features", input(root, c, "features_text"), "--selected_features", selected, "--control_features", input(root, c, "control_features"), "--chromosome", Integer.toString(chromosome), "--out", root.resolve("raw/prediction").toString())));
            }
        } else if (c.get("task").equals("munge")) jobs.add(new Job("munge", "0.2/munge_feature_directory.py", List.of("--gene_annot_path", input(root, c, "gene_annotation"), "--feature_dir", input(root, c, "feature_directory"), "--nan_policy", c.get("nan_policy"), "--max_cols", c.get("chunk_columns"), "--save_prefix", root.resolve("raw/features").toString())));
        else {
            List<String> args = new ArrayList<>(List.of("--gene_annot_path", input(root, c, "gene_annotation"), "--feature_mat_prefix", input(root, c, "feature_prefix"), "--num_feature_chunks", c.get("feature_chunks"), "--method", c.get("method"), "--random_seed", c.get("random_seed"), "--feature_selection_p_cutoff", c.get("p_cutoff"), "--out_prefix", root.resolve("raw/original").toString(), "--verbose"));
            if (c.get("target_type").equals("magma")) {
                args.addAll(List.of("--magma_prefix", input(root, c, "magma_prefix"), c.b("use_magma_covariates") ? "--use_magma_covariates" : "--ignore_magma_covariates", c.b("use_magma_error_covariance") ? "--use_magma_error_cov" : "--ignore_magma_error_cov"));
            } else {
                add(args, "--y_path", input(root, c, "scores"));
                if (c.get("score_covariates") != null) add(args, "--y_covariates_path", input(root, c, "score_covariates"));
                if (c.get("error_covariance") != null) add(args, "--y_error_cov_path", input(root, c, "error_covariance"));
            }
            for (var entry : Map.of("subset_features", "--subset_features_path", "control_features", "--control_features_path").entrySet()) if (c.get(entry.getKey()) != null) add(args, entry.getValue(), input(root, c, entry.getKey()));
            if (!c.get("max_selected_by_p").equals("none")) add(args, "--feature_selection_max_num", c.get("max_selected_by_p"));
            if (!c.get("forward_steps").equals("none")) add(args, "--feature_selection_fss_num_features", c.get("forward_steps"));
            for (var entry : Map.of("projection", "project_out_covariates", "selection", "feature_selection", "training", "training").entrySet()) {
                String chromosomes = c.get(entry.getKey() + "_chromosomes");
                if (!chromosomes.equals("all")) { args.add("--" + entry.getValue() + "_chromosomes"); args.addAll(Arrays.asList(chromosomes.split(",", -1))); }
                args.add("--" + entry.getValue() + (c.b(entry.getKey() + "_remove_hla") ? "_remove_hla" : "_keep_hla"));
            }
            args.add(c.b("save_matrix_files") ? "--save_matrix_files" : "--no_save_matrix_files"); jobs.add(new Job("fit", "0.2/pops.py", args));
        }
        return List.copyOf(jobs);
    }
    private static void add(List<String> args, String option, String value) { args.add(option); args.add(value); }
    private static String input(Path root, PopsOriginalFiles.Config c, String key) throws IOException {
        Path path = PopsWorkflowFiles.resolve(root, c.get(key)), inputs = root.resolve("inputs");
        if (!path.startsWith(inputs) || path.equals(inputs)) throw new IOException("Original command input must reference captured inputs: " + key);
        return path.toString();
    }
}
