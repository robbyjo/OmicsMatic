/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.genetics.Pops;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Deterministic scientific tables and a separate parent-side identity/prediction audit. */
final class PopsWorkflowResults {
    private static final String PREDICTIONS = "ENSGID\tPoPS_Score\tY\tY_projected\tprojection\tselection\ttraining";
    private static final String COEFFICIENTS = "feature\tcoefficient\tselected";
    private static final String MARGINALS = "feature\tbeta\tse\tpval\tr2\tselected\tfss_rank";
    private static final String CV = "alpha\tnegative_loo_mse";
    private static final Map<String, String> ACTIONS = Map.of(
        "custom_covariance_cross_chromosome_nonzero_ignored", "PoPS uses chromosome blocks and ignores these cross-chromosome entries. Confirm this source behavior is appropriate before interpreting the model.",
        "custom_covariance_cross_chromosome_nonfinite_ignored", "Nonfinite cross-chromosome entries were ignored by the source block operation. Inspect the original covariance; no global covariance model is claimed.",
        "control_names_absent_from_matrix", "The original control-feature intersection excludes these absent names. Inspect the declared feature set and control list.",
        "feature_selection_files_ignored_by_forward_selection", "Forward selection overrides subset and control settings. These declared files were captured for provenance and were not used for feature selection.");
    private PopsWorkflowResults() { }
    static void write(Path root, Config c, PopsWorkflowInputs.Data d, Pops.Result fit) throws IOException {
        Path result = root.resolve("results");
        double[] predictions = fit.predictions(), projected = fit.projectedTarget(), coefficients = fit.coefficients();
        int[] targetAt = new int[predictions.length]; Arrays.fill(targetAt, -1);
        for (int i = 0; i < d.targetRows().length; i++) targetAt[d.targetRows()[i]] = i;
        boolean[] selected = new boolean[coefficients.length]; for (int feature : fit.selectedFeatures()) selected[feature] = true;
        try (var out = table(result.resolve("predictions.tsv"), PREDICTIONS)) {
            for (int i = 0; i < predictions.length; i++) {
                int t = targetAt[i];
                row(out, d.predictions().get(i), Double.toString(predictions[i]), t < 0 ? "NA" : Double.toString(d.target()[t]), t < 0 ? "NA" : Double.toString(projected[t]),
                    t < 0 ? "NA" : bit(d.masks()[0][t]), t < 0 ? "NA" : bit(d.masks()[1][t]), t < 0 ? "NA" : bit(d.masks()[2][t]));
            }
        }
        try (var out = table(result.resolve("coefficients.tsv"), COEFFICIENTS)) {
            for (int j = 0; j < coefficients.length; j++) row(out, d.features().names.get(j), Double.toString(coefficients[j]), bit(selected[j]));
        }
        Pops.Marginal[] marginals = fit.marginals(); int[] ranks = fit.forwardSelectionRanks();
        try (var out = table(result.resolve("marginals.tsv"), MARGINALS)) {
            for (int j = 0; j < marginals.length; j++) {
                var m = marginals[j]; row(out, d.features().names.get(j), Double.toString(m.beta()), Double.toString(m.standardError()), Double.toString(m.pValue()), Double.toString(m.rSquared()), bit(selected[j]), Integer.toString(ranks[j]));
            }
        }
        double[] scores = fit.cvScores();
        try (var out = table(result.resolve("cv.tsv"), CV)) { for (int i = 0; i < scores.length; i++) row(out, Double.toString(Math.pow(10, -2 + .5 * i)), Double.toString(scores[i])); }
        Map<String, String> model = expected(c, d); model.put("backend", fit.backend()); model.put("selected_features", Integer.toString(fit.selectedFeatures().length));
        model.put("alpha", Double.toString(fit.selectedAlpha())); model.put("best_cv_score", Double.toString(fit.bestCvScore()));
        properties(result.resolve("fit.properties"), model); properties(result.resolve("diagnostics.properties"), d.diagnostics());
        try (var out = table(result.resolve("warnings.tsv"), "code\tcount\taction")) {
            for (var entry : warnings(d.diagnostics()).entrySet()) row(out, entry.getKey(), d.diagnostics().get(entry.getKey()), entry.getValue());
        }
    }
    private static String bit(boolean value) { return value ? "1" : "0"; }
    private static int count(boolean[] values) { int n = 0; for (boolean value : values) if (value) n++; return n; }
    private static Map<String, String> expected(Config c, PopsWorkflowInputs.Data d) {
        Map<String, String> result = new TreeMap<>();
        for (String key : Set.of("profile", "edition", "method", "target_type", "genome_build", "feature_set_id", "target_source_id")) result.put(key, c.get(key));
        result.put("engine", "native-java-cpu"); result.put("target_genes", Integer.toString(d.targets().size())); result.put("prediction_genes", Integer.toString(d.predictions().size()));
        result.put("features", Integer.toString(d.features().columns()));
        String[] roles = {"projection", "selection", "training"}; for (int k = 0; k < roles.length; k++) result.put(roles[k] + "_genes", Integer.toString(count(d.masks()[k])));
        return result;
    }
    static Map<String, String> warnings(Map<String, String> diagnostics) throws IOException {
        Map<String, String> result = new TreeMap<>();
        for (var entry : ACTIONS.entrySet()) if (diagnostics.containsKey(entry.getKey())) {
            long n = Long.parseLong(diagnostics.get(entry.getKey())); if (n < 0) throw new IOException("Negative warning count");
            if (n > 0) result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
    static Map<String, String> audit(Path root, Config c) throws IOException {
        var d = PopsWorkflowInputs.layout(root, c); Path results = root.resolve("results");
        if (!inventory(results, c).keySet().equals(Set.of("predictions.tsv", "coefficients.tsv", "marginals.tsv", "cv.tsv", "fit.properties", "diagnostics.properties", "warnings.tsv"))) throw new IOException("Scientific result file membership differs");
        var model = strings(load(results.resolve("fit.properties"))); Map<String, String> expected = expected(c, d);
        Set<String> extra = new java.util.HashSet<>(model.keySet()); extra.removeAll(expected.keySet());
        if (!extra.equals(Set.of("backend", "selected_features", "alpha", "best_cv_score"))) throw new IOException("Fit metadata fields differ");
        for (var e : expected.entrySet()) if (!e.getValue().equals(model.get(e.getKey()))) throw new IOException("Fit metadata differs: " + e.getKey());
        if (model.get("backend").isEmpty()) throw new IOException("Numerical backend identity missing");
        int p = d.features().columns(), selectedCount = 0; double[] coefficients = new double[p]; boolean[] selected = new boolean[p];
        try (var in = reader(results.resolve("coefficients.tsv"), COEFFICIENTS)) {
            for (int j = 0; j < p; j++) {
                String[] row = fields(in.readLine(), 3); if (!row[0].equals(d.features().names.get(j))) throw new IOException("Coefficient feature identity/order differs");
                coefficients[j] = finite(row[1]); selected[j] = flag(row[2]); if (selected[j]) selectedCount++; else if (coefficients[j] != 0) throw new IOException("Unselected feature has a nonzero coefficient");
            }
            end(in);
        }
        if (selectedCount < 1 || selectedCount > c.integer("max_selected_features") || selectedCount != Integer.parseInt(model.get("selected_features"))) throw new IOException("Selected-feature accounting differs");
        int steps = c.get("forward_steps").equals("none") ? 0 : Integer.parseInt(c.get("forward_steps")); boolean[] ranks = new boolean[steps + 1];
        try (var in = reader(results.resolve("marginals.tsv"), MARGINALS)) {
            for (int j = 0; j < p; j++) {
                String[] row = fields(in.readLine(), 7); if (!row[0].equals(d.features().names.get(j)) || flag(row[5]) != selected[j]) throw new IOException("Marginal feature/selection identity differs");
                for (int k = 1; k <= 4; k++) Double.parseDouble(row[k]);
                double pv = Double.parseDouble(row[3]); if ((!Double.isNaN(pv) && !(pv >= 0 && pv <= 1)) || (selected[j] && Double.isNaN(pv))) throw new IOException("Selected/invalid marginal p-value");
                int rank = Integer.parseInt(row[6]); if (rank < 0 || rank > steps || (steps == 0 && rank != 0) || (steps > 0 && selected[j] != (rank > 0))) throw new IOException("Forward-selection rank invalid");
                if (rank > 0) { if (ranks[rank]) throw new IOException("Duplicate forward-selection rank"); ranks[rank] = true; }
            }
            end(in);
        }
        for (int rank = 1; rank <= steps; rank++) if (!ranks[rank]) throw new IOException("Missing forward-selection rank");
        try (var in = reader(results.resolve("cv.tsv"), CV)) {
            if (c.get("method").equals("ridge")) {
                double best = Double.NEGATIVE_INFINITY, alpha = Double.NaN;
                for (int i = 0; i < 25; i++) { String[] row = fields(in.readLine(), 2); double a = finite(row[0]), score = finite(row[1]); same(Math.pow(10, -2 + .5 * i), a, "Ridge penalty order"); if (score > best) { best = score; alpha = a; } }
                same(best, finite(model.get("best_cv_score")), "Selected CV score"); same(alpha, finite(model.get("alpha")), "Selected ridge penalty");
            } else if (!model.get("alpha").equals("NaN") || !model.get("best_cv_score").equals("NaN")) throw new IOException("OLS must retain absent ridge diagnostics as NaN");
            end(in);
        }
        double[] recomputed = new double[d.predictions().size()];
        for (int batch = 0; batch < d.features().batches(); batch++) {
            var values = d.features().read(batch);
            for (int i = 0; i < recomputed.length; i++) for (int j = 0; j < values.values()[i].length; j++) {
                double value = values.values()[i][j]; if (!Double.isFinite(value)) throw new IOException("Nonfinite captured feature during prediction audit");
                if (selected[values.firstColumn() + j]) recomputed[i] += value * coefficients[values.firstColumn() + j];
            }
        }
        int[] targetAt = new int[recomputed.length]; Arrays.fill(targetAt, -1); for (int i = 0; i < d.targetRows().length; i++) targetAt[d.targetRows()[i]] = i;
        try (var in = reader(results.resolve("predictions.tsv"), PREDICTIONS)) {
            for (int i = 0; i < recomputed.length; i++) {
                String[] row = fields(in.readLine(), 7); if (!row[0].equals(d.predictions().get(i))) throw new IOException("Prediction gene identity/order differs"); same(recomputed[i], finite(row[1]), "Prediction from captured features and coefficients");
                int target = targetAt[i];
                if (target < 0) { for (int k = 2; k < 7; k++) if (!row[k].equals("NA")) throw new IOException("Prediction-only gene has an invented target or mask"); }
                else { same(d.target()[target], finite(row[2]), "Unprojected target"); finite(row[3]); for (int k = 0; k < 3; k++) if (flag(row[k + 4]) != d.masks()[k][target]) throw new IOException("Target mask differs from annotation/request"); }
            }
            end(in);
        }
        Map<String, String> diagnostics = strings(load(results.resolve("diagnostics.properties")));
        for (var entry : d.diagnostics().entrySet()) if (!entry.getValue().equals(diagnostics.get(entry.getKey()))) throw new IOException("Input-selection diagnostic differs");
        try (var in = reader(results.resolve("warnings.tsv"), "code\tcount\taction")) {
            for (var entry : warnings(diagnostics).entrySet()) { String[] row = fields(in.readLine(), 3); if (!Arrays.equals(row, new String[] {entry.getKey(), diagnostics.get(entry.getKey()), entry.getValue()})) throw new IOException("Warning code/count/action differs"); }
            end(in);
        }
        Map<String, String> receipt = strings(load(root.resolve("WORKER_DONE.properties")));
        Map<String, String> expectedReceipt = Map.of("profile", PROFILE, "target_genes", model.get("target_genes"), "prediction_genes", model.get("prediction_genes"), "features", model.get("features"), "selected_features", model.get("selected_features"));
        if (!receipt.equals(expectedReceipt)) throw new IOException("Worker receipt differs from independently audited results");
        return model;
    }
    static Map<String, String> strings(java.util.Properties properties) { Map<String, String> result = new TreeMap<>(); for (String key : properties.stringPropertyNames()) result.put(key, properties.getProperty(key)); return result; }
    private static void same(double a, double b, String label) throws IOException { if (!Double.isFinite(a) || Double.doubleToLongBits(a) != Double.doubleToLongBits(b)) throw new IOException(label + " differs"); }
    private static boolean flag(String text) throws IOException { if (!text.equals("0") && !text.equals("1")) throw new IOException("Literal 0/1 flag required"); return text.equals("1"); }
    private static String[] fields(String line, int n) throws IOException { if (line == null || line.length() > 1048576) throw new IOException("Missing or overlong result row"); String[] row = line.split("\t", -1); if (row.length != n) throw new IOException("Result field count differs"); return row; }
    private static void end(BufferedReader in) throws IOException { if (in.readLine() != null) throw new IOException("Unexpected extra result row"); }
    private static BufferedReader reader(Path path, String header) throws IOException { var in = Files.newBufferedReader(path); try { if (!header.equals(in.readLine())) throw new IOException("Result header differs: " + path); return in; } catch (Throwable e) { in.close(); throw e; } }
    private static BufferedWriter table(Path path, String header) throws IOException { var out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); try { out.write(header); out.write('\n'); return out; } catch (Throwable e) { out.close(); throw e; } }
    private static void row(BufferedWriter out, String... fields) throws IOException { out.write(String.join("\t", fields)); out.write('\n'); }
}
