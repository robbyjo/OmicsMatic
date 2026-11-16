/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.PopsWorkflowFiles.*;

/** Parent-side checks of original report identities and predictions from saved raw features. */
final class PopsOriginalResults {
    private PopsOriginalResults() { }
    static Map<?, ?> report(Path root, PopsOriginalFiles.Config c) throws IOException {
        Path file = root.resolve("audit/summary.json"); if (Files.size(file) > c.i("max_header_bytes")) throw new IOException("Source binding summary exceeds declared metadata capacity");
        Object parsed = SimpleJson.parse(Files.readString(file));
        if (!(parsed instanceof Map<?, ?> map) || !"original-pops-audit-v1".equals(map.get("profile")) || !c.get("edition").equals(map.get("edition")) || !c.get("task").equals(map.get("task")) || !Boolean.FALSE.equals(map.get("numerical_model_source_modified"))) throw new IOException("Original result binding receipt differs");
        return map;
    }
    static Map<String, String> audit(Path root, PopsOriginalFiles.Config c) throws IOException {
        Map<?, ?> report = report(root, c); Object value = report.get("models"); if (!(value instanceof List<?> models)) throw new IOException("Original model list missing");
        boolean fits = c.get("task").equals("fit") || c.predicts(); int expectedModels = fits ? c.legacy() ? c.chromosomes().size() : 1 : 0;
        if (models.size() != expectedModels) throw new IOException("Requested/returned original model count differs");
        Set<String> expectedFiles = new HashSet<>(strings(report.get("result_members"))); expectedFiles.addAll(Set.of("declarations.properties", "warnings.tsv"));
        if (!PopsOriginalFiles.inventory(root.resolve("results"), c).keySet().equals(expectedFiles)) throw new IOException("Complete original result family membership differs");
        Map<String, String> declarations = PopsWorkflowResults.strings(load(root.resolve("results/declarations.properties")));
        Map<String, String> expectedDeclarations = Map.of("profile", PopsOriginalFiles.PROFILE, "edition", c.get("edition"), "task", c.get("task"), "genome_build", c.get("genome_build"), "feature_set_id", c.get("feature_set_id"), "target_source_id", c.get("target_source_id"), "original_source_modified", "false", "author_environment_reproduced", "false");
        if (!declarations.equals(expectedDeclarations)) throw new IOException("Original source/request declarations differ");
        int predictions = 0, selectedFeatures = 0;
        if (fits) {
            List<String> ids = PopsWorkflowInputs.tokens(root.resolve("audit/features.rows.txt"), c.i("max_prediction_genes"), c.i("max_identifier_chars"));
            List<String> columns = PopsWorkflowInputs.tokens(root.resolve("audit/features.cols.txt"), c.i("max_selected_features"), c.i("max_identifier_chars"));
            if (ids.isEmpty() || columns.isEmpty()) throw new IOException("No original prediction genes/model features");
            Map<String, Integer> positions = new HashMap<>(); for (int i = 0; i < ids.size(); i++) positions.put(ids.get(i), i);
            var matrix = PopsWorkflowNpy.inspect(root.resolve("audit/features.npy"), c.i("max_header_bytes"), c.n("max_work_bytes"));
            if (matrix.dimensions() != 2 || matrix.dimension(0) != ids.size() || matrix.dimension(1) != columns.size()) throw new IOException("Original selected feature audit matrix dimensions differ");
            double[][] coefficients = new double[models.size()][columns.size()]; List<Map<?, ?>> descriptions = new ArrayList<>();
            for (int k = 0; k < models.size(); k++) {
                if (!(models.get(k) instanceof Map<?, ?> model)) throw new IOException("Model description must be a mapping"); descriptions.add(model);
                String expectedId = c.legacy() ? "chr-" + String.format(Locale.ROOT, "%02d", c.chromosomes().get(k)) : "fit";
                if (!expectedId.equals(model.get("id")) || number(model.get("features")) != columns.size()) throw new IOException("Original model identity/feature count differs");
                try (var in = Files.newBufferedReader(result(root, model.get("coefficient_file")))) {
                    List<String> header = row(in.readLine(), c); if (!header.equals(List.of(text(model.get("coefficient_name_column")), text(model.get("coefficient_value_column"))))) throw new IOException("Original coefficient schema differs");
                    int metadata = number(model.get("coefficient_metadata_rows"));
                    for (int i = 0; i < metadata; i++) { List<String> fields = row(in.readLine(), c); if (fields.size() != 2) throw new IOException("Original coefficient metadata row invalid"); if (i == 0 && (!fields.get(0).equals("METHOD") || !fields.get(1).equals(model.get("source_method")))) throw new IOException("Original method marker differs"); if (i > 0) finite(fields.get(1)); }
                    for (int j = 0; j < columns.size(); j++) { List<String> fields = row(in.readLine(), c); if (fields.size() != 2 || !fields.get(0).equals(columns.get(j))) throw new IOException("Original feature coefficient order differs"); coefficients[k][j] = finite(fields.get(1)); }
                    if (in.readLine() != null) throw new IOException("Extra original coefficient rows");
                }
            }
            double[][] expectedScores = new double[models.size()][ids.size()];
            for (int first = 0; first < columns.size();) {
                int width = Math.min(c.i("batch_columns"), columns.size() - first); double[][] batch = matrix.columns(first, width);
                for (int i = 0; i < ids.size(); i++) for (int j = 0; j < width; j++) {
                    double x = batch[i][j]; if (!Double.isFinite(x)) throw new IOException("Selected original feature data are nonfinite; no accepted prediction");
                    for (int k = 0; k < models.size(); k++) expectedScores[k][i] += x * coefficients[k][first + j];
                }
                first += width;
            }
            for (int k = 0; k < models.size(); k++) {
                Map<?, ?> model = descriptions.get(k); List<String> wanted = c.legacy() ? strings(model.get("gene_ids")) : ids;
                if (wanted.isEmpty() || new HashSet<>(wanted).size() != wanted.size() || wanted.size() != number(model.get("prediction_genes"))) throw new IOException("Original model gene identity/count invalid");
                try (var in = Files.newBufferedReader(result(root, model.get("score_file")))) {
                    List<String> header = row(in.readLine(), c); int id = header.indexOf("ENSGID"), score = header.indexOf(text(model.get("score_column"))); if (id < 0 || score < 0 || new HashSet<>(header).size() != header.size()) throw new IOException("Original prediction schema differs");
                    for (String gene : wanted) {
                        List<String> fields = row(in.readLine(), c); Integer position = positions.get(gene);
                        if (fields.size() != header.size() || position == null || !fields.get(id).equals(gene)) throw new IOException("Original prediction gene order/identity differs");
                        double actual = finite(fields.get(score)), expected = expectedScores[k][position];
                        if (!Double.isFinite(expected) || Math.abs(expected - actual) > 5e-8 * Math.max(1, Math.abs(actual))) throw new IOException("Original prediction differs from independently accumulated captured features and coefficients");
                    }
                    if (in.readLine() != null) throw new IOException("Extra original prediction genes");
                }
                predictions = Math.addExact(predictions, wanted.size());
            }
            selectedFeatures = columns.size();
        } else if (c.get("task").equals("munge")) {
            if (!Boolean.TRUE.equals(report.get("preparation_only")) || !c.get("nan_policy").equals(report.get("nan_policy"))) throw new IOException("Prepared artifact incorrectly described as a fitted model");
            int chunks = number(report.get("prepared_chunks")), features = number(report.get("prepared_features")); List<String> rows = PopsWorkflowInputs.tokens(root.resolve("results/features.rows.txt"), c.i("max_prediction_genes"), c.i("max_identifier_chars")); int total = 0; Set<String> names = new HashSet<>();
            if (rows.size() != number(report.get("prepared_genes"))) throw new IOException("Prepared row count differs");
            for (int i = 0; i < chunks; i++) {
                var cols = PopsWorkflowInputs.tokens(root.resolve("results/features.cols." + i + ".txt"), c.i("max_features"), c.i("max_identifier_chars"));
                var array = PopsWorkflowNpy.inspect(root.resolve("results/features.mat." + i + ".npy"), c.i("max_header_bytes"), c.n("max_work_bytes"));
                if (array.dimensions() != 2 || array.dimension(0) != rows.size() || array.dimension(1) != cols.size() || cols.stream().anyMatch(name -> !names.add(name))) throw new IOException("Prepared shape/feature identity differs"); total = Math.addExact(total, cols.size());
            }
            if (total != features || total > c.i("max_features")) throw new IOException("Prepared complete feature count differs");
        } else if (!Boolean.TRUE.equals(report.get("selection_only")) || PopsWorkflowInputs.tokens(root.resolve("results/selected.features"), c.i("max_features"), c.i("max_identifier_chars")).size() != number(report.get("feature_selection_count"))) throw new IOException("Original standalone selection artifact differs");
        return Map.of("profile", PopsOriginalFiles.PROFILE, "edition", c.get("edition"), "task", c.get("task"), "models", Integer.toString(models.size()), "prediction_genes", Integer.toString(predictions), "model_features", Integer.toString(selectedFeatures), "fit_performed", Boolean.toString(fits));
    }
    private static Path result(Path root, Object value) throws IOException { Path relative = Path.of(text(value)), results = root.resolve("results"); Path path = results.resolve(relative).normalize(); if (relative.isAbsolute() || !path.startsWith(results) || !Files.isRegularFile(path)) throw new IOException("Unqualified original result path"); return path; }
    private static int number(Object value) throws IOException { if (!(value instanceof Number n) || n.longValue() < 0 || n.longValue() > Integer.MAX_VALUE || n.doubleValue() != n.longValue()) throw new IOException("Nonnegative bounded original count required"); return n.intValue(); }
    private static String text(Object value) throws IOException { if (!(value instanceof String s)) throw new IOException("Original record string required"); return s; }
    private static List<String> strings(Object value) throws IOException { if (!(value instanceof List<?> list)) throw new IOException("Original string list required"); List<String> result = new ArrayList<>(); for (Object v : list) result.add(text(v)); return List.copyOf(result); }
    private static List<String> row(String line, PopsOriginalFiles.Config c) throws IOException {
        if (line == null || line.length() > c.i("max_header_bytes")) throw new IOException("Missing or over-capacity original table row");
        List<String> result = new ArrayList<>(); int index = 0;
        do {
            StringBuilder field = new StringBuilder();
            if (index < line.length() && line.charAt(index) == '"') {
                index++; boolean closed = false;
                while (index < line.length()) { char ch = line.charAt(index++); if (ch == '"') { if (index < line.length() && line.charAt(index) == '"') { field.append('"'); index++; } else { closed = true; break; } } else field.append(ch); }
                if (!closed || index < line.length() && line.charAt(index) != '\t') throw new IOException("Malformed quoted original TSV field");
            } else while (index < line.length() && line.charAt(index) != '\t') field.append(line.charAt(index++));
            result.add(field.toString()); if (index == line.length()) break; index++;
            if (index == line.length()) { result.add(""); break; }
        } while (true);
        return result;
    }
}
