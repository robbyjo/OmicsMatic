/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdistlib.Normal;
import org.jlinalg.cli.LdakWorkflowRequest.Config;
import org.jlinalg.genetics.LdakBedDataset;
import org.jlinalg.genetics.LdakKinship;
import static org.jlinalg.cli.LdakWorkflowFiles.*;
import static org.jlinalg.cli.LdakWorkflowInputs.fields;
import static org.jlinalg.cli.LdakWorkflowInputs.finite;

/** Native kinship recomputation, original result audits and deterministic derived tables. */
final class LdakWorkflowResults {
    record Report(long analyses, long estimatedComponents, long comparedKinshipEntries,
            LdakWorkflowDiagnostics.Report diagnostics) { }
    private record Fit(String alpha, String h2, String sd, String samples, String nullLogl, String altLogl,
            String liabilityH2, String liabilitySd) { }
    private LdakWorkflowResults() { }
    static Report audit(Path root, Config c, Path out) throws IOException {
        Files.createDirectory(out.resolve("native"));
        var diagnostics = LdakWorkflowDiagnostics.collect(root, c);
        Files.writeString(out.resolve("DIAGNOSTICS.tsv"), diagnostics.table(), StandardCharsets.UTF_8);
        LdakWorkflowDiagnostics.requireAdmissible(c, diagnostics);
        for (var stage : LdakWorkflowProcess.plan(c)) LdakWorkflowProcess.validateStage(root, c, stage);
        var fits = new ArrayList<Fit>();
        long components = 0, entries = 0;
        if (c.sumher()) {
            var input = LdakWorkflowInputs.sumher(root, c);
            Path stage = root.resolve("stages/sumher");
            Map<String, String> extra = attributes(stage.resolve("result.extra"), false);
            requireYes(extra, "Converged");
            double count = finite(required(extra, "Num_Datapoints"), "reported summary datapoints");
            if (count < 1 || count != Math.rint(count) || count > Math.min(input.taggingRows(), input.summaryRows()))
                throw new IOException("Original SumHer datapoint count contradicts complete supplied inputs");
            String nullLogl = required(extra, "Null_logl"), altLogl = required(extra, "Alt_logl");
            finite(nullLogl, "SumHer null likelihood"); finite(altLogl, "SumHer alternative likelihood");
            var observed = hers(stage.resolve("result.hers"));
            if (observed.size() != input.categories() + 1) throw new IOException("Original category estimates do not match the complete supplied tagging model");
            String[] total = observed.get("Her_All");
            if (total == null) throw new IOException("Missing unique original Her_All row");
            var liability = new LinkedHashMap<String, String[]>();
            if (!c.get("prevalence").equals("-")) {
                liability.putAll(hers(stage.resolve("result.hers.liab")));
                if (!liability.keySet().equals(observed.keySet())) throw new IOException("Original liability component set differs");
                double factor = liabilityFactor(c.decimal("prevalence"), c.decimal("ascertainment"));
                for (var row : observed.entrySet()) {
                    String[] transformed = liability.get(row.getKey());
                    for (int i : new int[]{1, 2}) {
                        double expected = finite(row.getValue()[i], "observed estimate") * factor;
                        double actual = finite(transformed[i], "liability estimate");
                        double tolerance = 5.1e-7 * (1 + Math.abs(factor)) + 8 * Math.ulp(expected);
                        if (Math.abs(expected - actual) > tolerance) throw new IOException("Original liability conversion differs from the declared prevalence/ascertainment, at printed precision");
                    }
                }
            }
            StringBuilder table = new StringBuilder("component\tscale\theritability\tsd\tinfluence\tinfluence_sd\n");
            for (var row : observed.entrySet()) {
                appendHers(table, row.getValue(), "observed");
                if (!liability.isEmpty()) appendHers(table, liability.get(row.getKey()), "liability");
            }
            Files.writeString(out.resolve("native/sumher-estimates.tsv"), table, StandardCharsets.UTF_8);
            String[] liabTotal = liability.get("Her_All");
            fits.add(new Fit("-", total[1], total[2], required(extra, "Weighted_Sample_Size"), nullLogl, altLogl,
                liabTotal == null ? "-" : liabTotal[1], liabTotal == null ? "-" : liabTotal[2]));
            String stats = "metric\tvalue\ntagging_regression_rows\t" + input.taggingRows()
                + "\ndeclared_reference_predictors\t" + input.referencePredictors() + "\ndeclared_regression_predictors\t" + input.regressionPredictors()
                + "\ndeclared_heritability_predictors\t" + input.heritabilityPredictors() + "\nsupplied_summary_rows\t" + input.summaryRows()
                + "\nreference_categories\t" + input.categories() + "\nreported_datapoints\t" + extra.get("Num_Datapoints")
                + "\nraw_summary_zero_statistics\t" + input.rawZeroStatistics() + "\nraw_reference_taggings_below_one\t" + input.rawTaggingsBelowOne()
                + "\noriginal_zero_statistics_replaced\t" + diagnostics.zeroStatisticsReplaced() + "\noriginal_taggings_floored\t" + diagnostics.taggingsFloored() + "\n";
            Files.writeString(out.resolve("native/input-audit.tsv"), stats, StandardCharsets.UTF_8);
            components = observed.size() - 1L;
        } else {
            Set<String> selection = initialSelection(root, c);
            try (var all = LdakBedDataset.open(root.resolve("inputs/genotypes"), selection, LdakWorkflowInputs.limits(c))) {
                if (c.get("thinning").equals("YES")) selection = thinning(root, c, all, out);
            }
            Map<String, Double> weights = LdakWorkflowInputs.weights(root, c);
            try (var data = LdakBedDataset.open(root.resolve("inputs/genotypes"), selection, LdakWorkflowInputs.limits(c))) {
                String nullModel = null, sampleMetric = null;
                for (int i = 0; i < c.alphas().size(); i++) {
                    String id = LdakWorkflowProcess.index(i), alpha = c.alphas().get(i);
                    Path kinship = root.resolve("stages/kinship-" + id);
                    entries = Math.addExact(entries, kinship(data, weights, c, alpha, kinship, out.resolve("native/kinship-" + id + ".tsv")));
                    Path reml = root.resolve("stages/reml-" + id + "/result.reml");
                    Map<String, String> metadata = attributes(reml, true);
                    requireYes(metadata, "Converged");
                    if (!"1".equals(required(metadata, "Num_Kinships")) || !"0".equals(required(metadata, "Num_Regions"))) throw new IOException("Original REML model differs from the one-kinship profile");
                    long total = integer(required(metadata, "Total_Samples"), "REML total samples");
                    long phenotyped = integer(required(metadata, "With_Phenotypes"), "REML phenotyped samples");
                    if (total != data.samples().size() || phenotyped < 1 || phenotyped > total) throw new IOException("Original REML sample counts contradict captured kinship dimensions");
                    String nullLogl = required(metadata, "Null_Likelihood"), altLogl = required(metadata, "Alt_Likelihood");
                    double nullValue = finite(nullLogl, "REML null likelihood"), altValue = finite(altLogl, "REML alternative likelihood");
                    double lrt = finite(required(metadata, "LRT_Stat"), "REML LRT");
                    if (Math.abs(lrt - 2 * (altValue - nullValue)) > 0.00031) throw new IOException("Original REML likelihood/LRT fields disagree at printed precision");
                    if (nullModel == null) { nullModel = nullLogl; sampleMetric = Long.toString(phenotyped); }
                    else if (!nullModel.equals(nullLogl) || !sampleMetric.equals(Long.toString(phenotyped))) throw new IOException("Alpha-grid fits do not share the same reported null likelihood and phenotyped sample count; do not compare these likelihoods");
                    String[] heritability = remlTotal(reml);
                    fits.add(new Fit(alpha, heritability[1], heritability[2], Long.toString(phenotyped), nullLogl, altLogl, "-", "-"));
                }
            }
            components = fits.size();
        }
        int selected = -1;
        if (!c.sumher() && c.get("alpha_selection").equals("max-alt-likelihood-first-grid-tie")) {
            for (int i = 0; i < fits.size(); i++) if (selected < 0 || Double.parseDouble(fits.get(i).altLogl()) > Double.parseDouble(fits.get(selected).altLogl())) selected = i;
        }
        StringBuilder summary = new StringBuilder("analysis\talpha\treported_h2\treported_sd\treported_sample_metric\tnull_logl\talt_logl\tliability_h2\tliability_sd\tselected_by_declared_rule\n");
        for (int i = 0; i < fits.size(); i++) {
            Fit f = fits.get(i);
            summary.append(c.get("mode")).append('\t').append(f.alpha()).append('\t').append(f.h2()).append('\t').append(f.sd()).append('\t')
                .append(f.samples()).append('\t').append(f.nullLogl()).append('\t').append(f.altLogl()).append('\t')
                .append(f.liabilityH2()).append('\t').append(f.liabilitySd()).append('\t').append(i == selected).append('\n');
        }
        Files.writeString(out.resolve("summary.tsv"), summary, StandardCharsets.UTF_8);
        return new Report(fits.size(), components, entries, diagnostics);
    }
    private static Set<String> initialSelection(Path root, Config c) throws IOException {
        return c.get("predictors").equals("-") ? null : LdakWorkflowInputs.ids(root.resolve("inputs/predictors"), c.integer("max_variants"));
    }
    private static Set<String> thinning(Path root, Config c, LdakBedDataset data, Path out) throws IOException {
        var universe = new HashSet<String>(); for (var variant : data.selectedVariants()) universe.add(variant.id());
        var seen = new HashSet<String>(); Set<String> retained = null;
        StringBuilder report = new StringBuilder("disposition\tpredictor_count\n");
        for (String suffix : List.of("in", "out", "trivial")) {
            Set<String> ids = LdakWorkflowInputs.ids(root.resolve("stages/thin/result." + suffix), c.integer("max_variants"));
            if (!universe.containsAll(ids)) throw new IOException("Original thinning output contains an unknown predictor");
            for (String id : ids) if (!seen.add(id)) throw new IOException("Original thinning dispositions overlap");
            report.append(suffix).append('\t').append(ids.size()).append('\n');
            if (suffix.equals("in")) retained = ids;
        }
        if (!seen.equals(universe) || retained == null || retained.isEmpty()) throw new IOException("Original retained/lost/trivial sets do not form a complete nonempty thinning handoff");
        Files.writeString(out.resolve("native/thinning.tsv"), report, StandardCharsets.UTF_8);
        return retained;
    }
    private static long kinship(LdakBedDataset data, Map<String, Double> weights, Config c, String alpha, Path stage, Path report) throws IOException {
        var result = LdakKinship.calculate(data, new LdakKinship.Options(Double.parseDouble(alpha), c.get("hwe_stand").equals("YES"), c.integer("block_variants"), c.number("max_numeric_bytes")), weights);
        int n = result.samples().size();
        Path prefix = stage.resolve("result");
        try (var lines = new Lines(Path.of(prefix + ".grm.id"))) {
            for (int i = 0; i < n; i++) {
                String line = lines.next(); if (line == null) throw new IOException("Incomplete original GRM IDs");
                String[] f = fields(line); var s = result.samples().get(i);
                if (f.length != 2 || !f[0].equals(s.familyId()) || !f[1].equals(s.individualId())) throw new IOException("Original/native GRM sample identity/order differs");
            }
            if (lines.next() != null) throw new IOException("Unexpected additional original GRM samples");
        }
        long entries = (long) n * (n + 1) / 2;
        if (Files.size(Path.of(prefix + ".grm.bin")) != entries * 4) throw new IOException("Original lower-triangle float32 GRM dimensions differ");
        double maximum = 0;
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(prefix + ".grm.bin"))))) {
            for (int i = 0; i < n; i++) for (int j = 0; j <= i; j++) {
                double original = Float.intBitsToFloat(Integer.reverseBytes(input.readInt())), expected = result.value(i, j);
                if (!Double.isFinite(original) || Math.abs(original - expected) > 2e-7 + 2e-7 * Math.abs(original)) throw new IOException("Original GRM differs from native hard-call recomputation at " + i + "," + j);
                maximum = Math.max(maximum, Math.abs(original - expected));
            }
            if (input.read() != -1) throw new IOException("Trailing original GRM bytes");
        }
        var used = result.predictors().stream().filter(p -> p.status() == LdakKinship.Status.USED).toList();
        Map<String, LdakBedDataset.Variant> variants = new HashMap<>(); for (var v : data.selectedVariants()) variants.put(v.id(), v);
        try (var lines = new Lines(Path.of(prefix + ".grm.details"))) {
            if (!List.of(fields(requiredLine(lines, "original GRM header"))).equals(List.of("Predictor", "Index", "Centre", "Scaling", "Weight", "A1", "A2"))) throw new IOException("Unknown original GRM detail header");
            for (var p : used) {
                String[] f = fields(requiredLine(lines, "original GRM detail")); var v = variants.get(p.id());
                if (f.length != 7 || !f[0].equals(p.id()) || Long.parseLong(f[1]) != p.sourceOrdinal() + 1 || !f[5].equals(v.allele1()) || !f[6].equals(v.allele2())) throw new IOException("Original GRM predictor identity/alleles differ");
                comparePrinted(f[2], p.mean(), "original predictor mean"); comparePrinted(f[3], p.scaling(), "original predictor scaling"); comparePrinted(f[4], p.weight(), "original predictor weight");
            }
            if (lines.next() != null) throw new IOException("Unexpected additional original GRM predictor rows");
        }
        Map<String, String> adjust = attributes(Path.of(prefix + ".grm.adjust"), false);
        if (integer(required(adjust, "Num_Preds"), "original used predictors") != result.predictorsUsed()) throw new IOException("Original used-predictor count differs from native recomputation");
        double denominator = finite(required(adjust, "Denominator"), "original kinship denominator");
        if (Math.abs(denominator - result.denominator()) > 5.1e-5 + Math.abs(result.denominator()) * 1e-10) throw new IOException("Original kinship denominator differs from native recomputation");
        String text = "metric\tvalue\nsamples\t" + n + "\npredictors_used\t" + result.predictorsUsed() + "\ncompared_entries\t" + entries
            + "\nmaximum_absolute_float32_difference\t" + maximum + "\nnative_fp64\ttrue\noriginal_output_float32\ttrue\n";
        Files.writeString(report, text, StandardCharsets.UTF_8);
        return entries;
    }
    private static void comparePrinted(String raw, double expected, String label) throws IOException {
        if (Math.abs(finite(raw, label) - expected) > 5.1e-7 + 8 * Math.ulp(expected)) throw new IOException(label + " differs at six-decimal printed precision");
    }
    private static String[] remlTotal(Path path) throws IOException {
        boolean header = false; String[] total = null; int k1 = 0;
        try (var lines = new Lines(path)) {
            for (String line; (line = lines.next()) != null;) {
                if (line.startsWith("Component ")) { header = true; continue; }
                if (!header || line.isBlank()) continue;
                String[] f = fields(line);
                if (f.length != 6) throw new IOException("Unknown original REML component format");
                if (f[0].equals("Her_K1") || f[0].equals("Her_All")) {
                    finite(f[1], "REML heritability"); if (finite(f[2], "REML estimated SD") < 0) throw new IOException("Negative original REML SD");
                    if (f[0].equals("Her_K1")) k1++;
                }
                if (f[0].equals("Her_All")) { if (total != null) throw new IOException("Duplicate REML total"); total = f; }
            }
        }
        if (total == null || k1 != 1) throw new IOException("Incomplete original one-kinship REML components");
        return total;
    }
    private static Map<String, String[]> hers(Path path) throws IOException {
        var rows = new LinkedHashMap<String, String[]>();
        try (var lines = new Lines(path)) {
            if (!List.of(fields(requiredLine(lines, "SumHer estimates header"))).equals(List.of("Component", "Heritability", "SD", "Influence", "SD"))) throw new IOException("Unknown original SumHer estimates header");
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                String[] f = fields(line);
                if (f.length != 5 || rows.putIfAbsent(f[0], f) != null) throw new IOException("Original SumHer component shape/identity differs");
                finite(f[1], "SumHer heritability"); if (finite(f[2], "SumHer estimated SD") < 0) throw new IOException("Negative original SumHer SD");
            }
        }
        return rows;
    }
    private static void appendHers(StringBuilder table, String[] f, String scale) {
        table.append(f[0]).append('\t').append(scale); for (int i = 1; i < f.length; i++) table.append('\t').append(f[i]); table.append('\n');
    }
    static double liabilityFactor(double prevalence, double ascertainment) throws IOException {
        if (!(prevalence > 0 && prevalence < 1 && ascertainment > 0 && ascertainment < 1)) throw new IOException("Strictly interior prevalence/ascertainment required");
        double threshold = Normal.quantile(prevalence, 0, 1, false, false);
        double density = Normal.density(threshold, 0, 1, false);
        double factor = Math.pow(prevalence * (1 - prevalence) / density, 2) / (ascertainment * (1 - ascertainment));
        if (!(factor > 0) || !Double.isFinite(factor)) throw new IOException("Nonfinite liability conversion factor; no result");
        return factor;
    }
    private static Map<String, String> attributes(Path path, boolean stopAtComponents) throws IOException {
        var result = new LinkedHashMap<String, String>();
        try (var lines = new Lines(path)) {
            for (String line; (line = lines.next()) != null;) {
                if (line.isBlank()) continue;
                if (stopAtComponents && line.startsWith("Component ")) break;
                String[] f = line.trim().split("\\s+", 2);
                if (f.length != 2 || result.putIfAbsent(f[0], f[1]) != null) throw new IOException("Malformed/duplicate original summary attribute");
            }
        }
        return result;
    }
    private static String required(Map<String, String> values, String key) throws IOException {
        String value = values.get(key); if (value == null || value.isBlank()) throw new IOException("Missing original summary attribute " + key); return value;
    }
    private static void requireYes(Map<String, String> values, String key) throws IOException {
        if (!required(values, key).equals("YES")) throw new IOException("Original model did not converge; retain partials and choose justified settings in a NEW request");
    }
    private static long integer(String raw, String label) throws IOException {
        try { long n = Long.parseLong(raw); if (n < 0) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IOException("Invalid " + label, e); }
    }
    private static String requiredLine(Lines lines, String label) throws IOException {
        String value = lines.next(); if (value == null) throw new IOException("Missing " + label); return value;
    }
}
