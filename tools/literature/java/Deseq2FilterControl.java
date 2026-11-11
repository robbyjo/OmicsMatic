/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Complete comparisons to original DESeq2 pvalueAdjustment and genefilter filtered_p. */
public final class Deseq2FilterControl {
    private Deseq2FilterControl() { }
    private static int checks;
    private static void require(boolean value) { if (!value) throw new AssertionError("Independent-filter check " + checks); checks++; }
    private static void refused(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | CancellationException expected) { checks++; return; }
        throw new AssertionError("Invalid independent filtering returned a result");
    }
    private static double number(String value) {
        return value.equals("NA") ? Deseq2SizeFactors.MISSING : value.equals("NaN") ? Double.NaN : Double.parseDouble(value);
    }
    private static void close(double expected, double actual, String label) {
        if (Double.isNaN(expected)) { require(Double.isNaN(actual) && Deseq2SizeFactors.isMissing(expected) == Deseq2SizeFactors.isMissing(actual)); return; }
        double tolerance = Math.max(16 * Math.ulp(expected), 4e-12 * Math.abs(expected));
        if (!Double.isFinite(actual) || (expected == 0 || actual == 0 ? expected != actual : Math.abs(expected - actual) > tolerance))
            throw new AssertionError(label + " original=" + expected + " native=" + actual + " tolerance=" + tolerance);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--portable")) {
            double[] means = {1, 2, 3}, p = {0.1, Deseq2SizeFactors.MISSING, Double.NaN};
            var result = Deseq2IndependentFiltering.filter(means, p, 0.1);
            require(result.selectedIndex() == 0 && result.selectionThreshold().isEmpty());
            require(Deseq2SizeFactors.isMissing(result.adjusted()[1]) && Double.isNaN(result.adjusted()[2]) && !Deseq2SizeFactors.isMissing(result.adjusted()[2]));
            double[] copy = result.adjusted(); copy[0] = 999; require(result.adjusted()[0] != 999);
            require(Arrays.equals(means, new double[]{1, 2, 3})); require(p[0] == 0.1 && Deseq2SizeFactors.isMissing(p[1]));
            refused(() -> Deseq2IndependentFiltering.filter(new double[0], new double[0], 0.1));
            refused(() -> Deseq2IndependentFiltering.filter(means, new double[]{0.2}, 0.1));
            refused(() -> Deseq2IndependentFiltering.filter(new double[]{Double.NaN}, new double[]{0.1}, 0.1));
            refused(() -> Deseq2IndependentFiltering.filter(new double[]{-1}, new double[]{0.1}, 0.1));
            refused(() -> Deseq2IndependentFiltering.filter(new double[]{1}, new double[]{Double.POSITIVE_INFINITY}, 0.1));
            refused(() -> Deseq2IndependentFiltering.filter(new double[]{1}, new double[]{1.01}, 0.1));
            refused(() -> Deseq2IndependentFiltering.filter(means, p, 0)); refused(() -> Deseq2IndependentFiltering.filter(means, p, 1));
            refused(() -> Deseq2IndependentFiltering.filter(means, p, 0.1, new Deseq2IndependentFiltering.Limits(2, 10000, 10000, org.jlinalg.loess.RLowess.Limits.defaults()), () -> false));
            refused(() -> Deseq2IndependentFiltering.filter(means, p, 0.1, new Deseq2IndependentFiltering.Limits(10, 1, 10000, org.jlinalg.loess.RLowess.Limits.defaults()), () -> false));
            refused(() -> Deseq2IndependentFiltering.filter(means, p, 0.1, new Deseq2IndependentFiltering.Limits(10, 10000, 1, org.jlinalg.loess.RLowess.Limits.defaults()), () -> false));
            refused(() -> Deseq2IndependentFiltering.filter(means, p, 0.1, Deseq2IndependentFiltering.Limits.defaults(), () -> true));
            refused(() -> Deseq2IndependentFiltering.quantile7(new double[]{Double.NaN}, 0.5));
            refused(() -> Deseq2IndependentFiltering.quantile7(new double[]{1}, -0.1));
            close(3, Deseq2IndependentFiltering.quantile7(new double[]{5, 1}, 0.5), "Type7 midpoint");
            System.out.println("PASS " + checks + " independent-filter portable checks"); return;
        }
        Path root = Path.of(args[0]); int populations = 0;
        for (String name : Files.readAllLines(root.resolve("cases.txt"), StandardCharsets.UTF_8)) {
            Path directory = root.resolve(name); var lines = Files.readAllLines(directory.resolve("input.tsv"), StandardCharsets.UTF_8);
            int n = lines.size() - 1; double[] means = new double[n], p = new double[n];
            for (int i = 0; i < n; i++) { String[] f = lines.get(i + 1).split("\t"); means[i] = number(f[0]); p[i] = number(f[1]); }
            double alpha = Double.parseDouble(Files.readString(directory.resolve("alpha.txt")).trim());
            var result = Deseq2IndependentFiltering.filter(means, p, alpha);
            var curve = Files.readAllLines(directory.resolve("curve.tsv"), StandardCharsets.UTF_8); require(curve.size() == 51);
            double[] theta = result.theta(), cutoffs = result.cutoffs(), smoothed = result.smoothed(); int[] rejections = result.rejections();
            for (int i = 0; i < 50; i++) {
                String[] f = curve.get(i + 1).split("\t");
                close(number(f[0]), theta[i], name + " theta"); close(number(f[1]), cutoffs[i], name + " Type7 cutoff");
                require(Integer.parseInt(f[2]) == rejections[i]); close(number(f[3]), smoothed[i], name + " original LOWESS");
            }
            String[] selection = Files.readAllLines(directory.resolve("selected.tsv"), StandardCharsets.UTF_8).get(1).split("\t");
            require(result.selectedIndex() == Integer.parseInt(selection[0]));
            close(number(selection[1]), result.selectedTheta(), name + " selected theta"); close(number(selection[2]), result.selectedCutoff(), name + " selected cutoff");
            require(result.selectionThreshold().isPresent() == !selection[3].equals("NOT_CALCULATED"));
            if (result.selectionThreshold().isPresent()) close(number(selection[3]), result.selectionThreshold().getAsDouble(), name + " LOWESS residual threshold");
            var expected = Files.readAllLines(directory.resolve("adjusted.tsv"), StandardCharsets.UTF_8); require(expected.size() == n + 1);
            double[] adjusted = result.adjusted();
            for (int i = 0; i < n; i++) close(number(expected.get(i + 1)), adjusted[i], name + " adjusted row " + i);
            var matrix = Files.readAllLines(directory.resolve("all-families.tsv"), StandardCharsets.UTF_8); require(matrix.size() == n + 1);
            for (int column = 0; column < 50; column++) {
                double[] family = Deseq2IndependentFiltering.adjustAtCutoff(means, p, cutoffs[column]);
                for (int i = 0; i < n; i++) close(number(matrix.get(i + 1).split("\t", -1)[column]), family[i], name + " family " + column + " row " + i);
            }
            System.out.println("PASS_ORIGINAL_DESEQ126_FILTER case=" + name + " full_rows=" + n + " families=50 selected_index=" + result.selectedIndex()); populations++;
        }
        System.out.println("PASS_COMPLETE_ORIGINAL_DESEQ126_FILTER populations=" + populations + " checks=" + checks);
    }
}
