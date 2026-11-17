/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Typed original-5.2 deployment/model choices; no paper parameters are inferred. */
final class LdakWorkflowRequest {
    private static final Set<String> COMMON = Set.of("mode", "executable", "license", "execution",
        "wall_ms", "max_bytes", "max_files", "upstream_adjustments", "max_iterations", "tolerance");
    private static final Set<String> SUMHER = Set.of("tagging", "summary", "check_sums", "allow_ambiguous",
        "inflation", "solver", "prevalence", "ascertainment", "cutoff", "max_predictors");
    private static final Set<String> REML = Set.of("bfile", "phenotype", "covariates", "weights", "predictors",
        "thinning", "window_r2", "window_kb", "alpha_grid", "alpha_selection", "constrain", "hwe_stand",
        "block_variants", "max_numeric_bytes", "max_samples", "max_variants", "max_metadata_bytes", "max_line_bytes");
    record Config(Properties values) {
        Config { Properties copy = new Properties(); copy.putAll(values); values = copy; }
        @Override public Properties values() { Properties copy = new Properties(); copy.putAll(values); return copy; }
        String get(String key) { return values.getProperty(key); }
        long number(String key) { return Long.parseLong(get(key)); }
        int integer(String key) { return Math.toIntExact(number(key)); }
        double decimal(String key) { return Double.parseDouble(get(key)); }
        boolean sumher() { return get("mode").equals("sumher"); }
        List<String> alphas() { return List.of(get("alpha_grid").split(",", -1)); }
        List<String> inputKeys() {
            var result = new ArrayList<>(List.of("executable", "license"));
            if (sumher()) result.addAll(List.of("tagging", "summary"));
            else for (String key : List.of("phenotype", "covariates", "weights", "predictors"))
                if (!get(key).equals("-")) result.add(key);
            return List.copyOf(result);
        }
    }
    private LdakWorkflowRequest() { }
    static Config parse(Properties values) {
        boolean sumher = "sumher".equals(values.getProperty("mode"));
        if (!sumher && !"kinship-reml".equals(values.getProperty("mode")))
            throw new IllegalArgumentException("mode must be sumher or kinship-reml");
        if (sumher && values.containsKey("truncate"))
            throw new IllegalArgumentException("Original LDAK5.2 supports --truncate only for HE/PCGC, not SumHer; do not substitute newer-version semantics");
        var keys = new TreeSet<>(COMMON); keys.addAll(sumher ? SUMHER : REML);
        if (!values.stringPropertyNames().equals(keys)) throw new IllegalArgumentException("Request must contain exactly: " + keys);
        for (String key : keys) if (values.getProperty(key).isBlank()) throw new IllegalArgumentException("Blank property: " + key);
        var requiredPaths = new ArrayList<>(List.of("executable", "license"));
        requiredPaths.addAll(sumher ? List.of("tagging", "summary") : List.of("bfile", "phenotype"));
        for (String key : requiredPaths) if (values.getProperty(key).equals("-"))
            throw new IllegalArgumentException("A real input path is required for " + key);
        choice(values, "execution", "allow", "forbid");
        choice(values, "upstream_adjustments", "forbid", "allow-recorded");
        integer(values, "wall_ms", 1, 86400000);
        integer(values, "max_bytes", 1, 1L << 40);
        integer(values, "max_files", 1, 1_000_000);
        integer(values, "max_iterations", 1, 1_000_000);
        decimal(values, "tolerance", 0, 1, false);
        if (sumher) {
            integer(values, "max_predictors", 1, Integer.MAX_VALUE);
            choice(values, "check_sums", "YES", "NO"); choice(values, "allow_ambiguous", "YES", "NO");
            choice(values, "inflation", "none", "additive", "multiplicative");
            choice(values, "solver", "maximum-likelihood", "weighted-least-squares");
            if (values.getProperty("prevalence").equals("-") != values.getProperty("ascertainment").equals("-"))
                throw new IllegalArgumentException("prevalence and ascertainment must both be '-' or both explicit proportions");
            for (String key : List.of("prevalence", "ascertainment", "cutoff"))
                if (!values.getProperty(key).equals("-")) decimal(values, key, 0, 1, false);
        } else {
            choice(values, "thinning", "YES", "NO"); choice(values, "constrain", "YES", "NO");
            choice(values, "hwe_stand", "YES", "NO");
            choice(values, "alpha_selection", "all", "max-alt-likelihood-first-grid-tie");
            if (values.getProperty("thinning").equals("YES")) {
                decimal(values, "window_r2", 0, 1, true); decimal(values, "window_kb", 0, 1e9, true);
                if (!values.getProperty("weights").equals("-"))
                    throw new IllegalArgumentException("The LDAK-Thin profile uses unit weights on retained predictors; use thinning=NO for caller-supplied weights");
            } else if (!values.getProperty("window_r2").equals("-") || !values.getProperty("window_kb").equals("-"))
                throw new IllegalArgumentException("Without thinning, window_r2 and window_kb must be '-'");
            String[] alphas = values.getProperty("alpha_grid").split(",", -1);
            if (alphas.length < 1 || alphas.length > 256) throw new IllegalArgumentException("Supply 1..256 explicit alpha values; no grid is inferred");
            var seen = new HashSet<Double>();
            for (String alpha : alphas) {
                double x = finite(alpha, "alpha_grid");
                if (!seen.add(x == 0 ? 0.0 : x)) throw new IllegalArgumentException("Duplicate numeric alpha in declared grid");
            }
            for (String key : List.of("block_variants", "max_samples", "max_variants")) integer(values, key, 1, Integer.MAX_VALUE);
            integer(values, "max_line_bytes", 16, 16L * 1024 * 1024);
            integer(values, "max_numeric_bytes", 1, 1L << 40);
            integer(values, "max_metadata_bytes", 1, 1L << 40);
        }
        return new Config(values);
    }
    private static void choice(Properties values, String key, String... allowed) {
        if (!Set.of(allowed).contains(values.getProperty(key))) throw new IllegalArgumentException(key + " must be one of " + List.of(allowed));
    }
    private static void integer(Properties p, String key, long minimum, long maximum) {
        String raw = p.getProperty(key);
        try {
            long value = Long.parseLong(raw);
            if (!raw.matches("[1-9][0-9]*") || value < minimum || value > maximum) throw new NumberFormatException();
        } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid positive integer " + key, error); }
    }
    private static void decimal(Properties p, String key, double minimum, double maximum, boolean upperInclusive) {
        double value = finite(p.getProperty(key), key);
        if (!(value > minimum && (upperInclusive ? value <= maximum : value < maximum)))
            throw new IllegalArgumentException("Out-of-range " + key);
    }
    private static double finite(String raw, String key) {
        if (!raw.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))
            throw new IllegalArgumentException("Require a literal decimal for " + key);
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite " + key);
        return value;
    }
}
