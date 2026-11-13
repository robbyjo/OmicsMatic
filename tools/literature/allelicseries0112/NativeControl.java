/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.genetics.AllelicSeries0112;

/** Full retained-vector comparison to independently executed original R functions. */
public final class NativeControl {
    private NativeControl() { }
    private static double number(String s) {
        return switch (s) { case "NA", "NaN" -> Double.NaN;
            case "Inf" -> Double.POSITIVE_INFINITY; case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> Double.parseDouble(s); };
    }
    private static double[] vector(String s) {
        if (s.equals("NULL")) return null;
        String[] parts = s.split(",", -1);double[] a = new double[parts.length];
        for (int i = 0; i < a.length; i++) a[i] = number(parts[i]);return a;
    }
    private static void compare(double actual, double expected, String context) {
        if (Double.isNaN(expected) ? Double.isNaN(actual) : actual == expected) return;
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) throw new AssertionError(
            context + " actual=" + actual + " original=" + expected);
        double tolerance = 2e-8 * Math.abs(expected) + 2e-12;
        if (context.endsWith("p-value") && expected > 0 && expected < 1e-8) {
            tolerance = 2e-7 * expected;
        }
        if (Math.abs(actual - expected) > tolerance) throw new AssertionError(
            context + " actual=" + actual + " original=" + expected + " tolerance=" + tolerance);
    }
    private static int compare(double[] actual, double[] expected, String context) {
        if (actual == null || expected == null) {
            if (actual != expected) throw new AssertionError(context + " null differs");return 0;
        }
        if (actual.length != expected.length) throw new AssertionError(context + " length differs");
        for (int i = 0; i < actual.length; i++) compare(actual[i], expected[i], context + "[" + i + "]");
        return actual.length;
    }
    public static void main(String[] args) throws Exception {
        List<String> input = Files.readAllLines(Path.of(args[0]));
        List<String> reference = Files.readAllLines(Path.of(args[1]));
        if (input.size() != reference.size() || input.size() != 539) throw new AssertionError(
            "Require complete original539-case population");
        int errors = 0, scalars = 0, values = 0;List<String> mismatches = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            String[] c = input.get(index).split("\t", -1), r = reference.get(index).split("\t", -1);
            if (!c[0].equals(r[0])) throw new AssertionError("Original case identity differs");
            AllelicSeries0112.BurdenResult result = null;Exception failure = null;
            try {
                double[] labels = vector(c[2]);int[] anno = new int[labels.length];
                for (int i = 0; i < anno.length; i++) anno[i] = (int) labels[i];
                double[] beta = vector(c[3]), se = vector(c[4]), weights = vector(c[6]);
                double[] flat = vector(c[5]);double[][] ld = null;
                if (flat != null) {
                    ld = new double[anno.length][anno.length];
                    if (flat.length != anno.length * anno.length) throw new AssertionError("LD length");
                    for (int i = 0; i < flat.length; i++) ld[i / anno.length][i % anno.length] = flat[i];
                }
                result = switch (c[1]) {
                    case "wrapper" -> AllelicSeries0112.burden(anno,beta,se,ld,weights,c[7],number(c[8]),number(c[9]));
                    case "baseline" -> AllelicSeries0112.baseline(anno,beta,se,ld,weights.length);
                    case "sum" -> AllelicSeries0112.sumCount(anno,beta,se,ld,weights);
                    default -> throw new AssertionError("Unknown call kind");
                };
            } catch (Exception e) { failure = e; }
            try {
                if (r[1].equals("error")) {
                    if (failure == null) throw new AssertionError(c[0] + " original failed but native returned");
                    errors++;continue;
                }
                if (failure != null) throw new AssertionError(c[0] + " native failed but original returned", failure);
                if (result == null) throw new AssertionError("Missing result");
                if (result.sourceScalarZero() != r[1].equals("scalar")) throw new AssertionError("Scalar-zero edge differs");
                if (result.sourceScalarZero()) scalars++;
                compare(result.pValue(),number(r[2]),c[0] + " p-value");values++;
                values += compare(result.beta(),vector(r[3]),c[0] + " beta");
                values += compare(result.standardError(),vector(r[4]),c[0] + " se");
            } catch (AssertionError e) { mismatches.add(c[0] + ": " + e); }
        }
        if (!mismatches.isEmpty()) {
            for (String mismatch : mismatches) System.err.println(mismatch);
            throw new AssertionError("Original/native mismatches=" + mismatches.size() + "/" + input.size());
        }
        System.out.println("PASS complete_original_cases="+input.size()+" values="+values+" original_errors="+errors+" scalar_zero_returns="+scalars);
    }
}
