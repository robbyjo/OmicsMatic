/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.loess;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/** Complete original R output comparisons, including the actual DESeq2 filter curve. */
public final class RLowessControl {
    private RLowessControl() { }
    private static int checks;
    private static void require(boolean value) { if (!value) throw new AssertionError("LOWESS contract " + checks); checks++; }
    private static void refused(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | ArithmeticException | CancellationException expected) { checks++; return; }
        throw new AssertionError("Unsupported LOWESS operation returned a result");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--portable")) {
            double[] x = {2, 1, 0}, y = {4, 2, 0}; double[] beforeX = x.clone(), beforeY = y.clone();
            var result = RLowess.fit(x, y); require(Arrays.equals(x, beforeX) && Arrays.equals(y, beforeY));
            require(Arrays.equals(result.originalIndices(), new int[]{2, 1, 0}));
            double[] getter = result.fitted(); getter[0] = 999; require(result.fitted()[0] != 999);
            refused(() -> RLowess.fit(new double[0], new double[0]));
            refused(() -> RLowess.fit(new double[]{1}, new double[]{1, 2}));
            refused(() -> RLowess.fit(new double[]{Double.NaN}, new double[]{1}));
            refused(() -> RLowess.fit(new double[]{1}, new double[]{Double.POSITIVE_INFINITY}));
            refused(() -> new RLowess.Options(0, 3, 0)); refused(() -> new RLowess.Options(Double.NaN, 3, 0));
            refused(() -> new RLowess.Options(1, -1, 0)); refused(() -> new RLowess.Options(1, Integer.MAX_VALUE, 0));
            refused(() -> new RLowess.Options(1, 0, -1)); refused(() -> new RLowess.Options(1, 0, Double.POSITIVE_INFINITY));
            var options = new RLowess.Options(0.5, 3, 0);
            refused(() -> RLowess.fit(x, y, options, new RLowess.Limits(2, 10000, 100), () -> false));
            refused(() -> RLowess.fit(x, y, options, new RLowess.Limits(3, 10000, 1), () -> false));
            refused(() -> RLowess.fit(x, y, options, new RLowess.Limits(3, 1, 100), () -> false));
            refused(() -> RLowess.fit(x, y, options, RLowess.Limits.defaults(), () -> true));
            var counter = new AtomicInteger();
            refused(() -> RLowess.fit(x, y, options, RLowess.Limits.defaults(), () -> counter.incrementAndGet() >= 3));
            require(Arrays.equals(x, beforeX) && Arrays.equals(y, beforeY));
            refused(() -> RLowess.fit(x, y, new RLowess.Options(1e100, 0, 0)));
            refused(() -> RLowess.fit(new double[]{-Double.MAX_VALUE, Double.MAX_VALUE}, new double[]{1, 2}, options));
            var signed = RLowess.fit(new double[]{0.0, -0.0, 0.0, 1}, new double[]{1, 2, 3, 4}, options);
            require(Arrays.equals(signed.originalIndices(), new int[]{0, 1, 2, 3}));
            System.out.println("PASS " + checks + " portable LOWESS refusal/immutability/order checks"); return;
        }
        Path root = Path.of(args[0]); int cases = 0;
        for (String name : Files.readAllLines(root.resolve("cases.txt"), StandardCharsets.UTF_8)) {
            Path directory = root.resolve(name); var lines = Files.readAllLines(directory.resolve("input.tsv"), StandardCharsets.UTF_8);
            double[] x = new double[lines.size() - 1], y = new double[x.length]; double scale = 0;
            for (int i = 0; i < x.length; i++) { String[] f = lines.get(i + 1).split("\t"); x[i] = Double.parseDouble(f[0]); y[i] = Double.parseDouble(f[1]); scale = Math.max(scale, Math.abs(y[i])); }
            var settings = Files.readAllLines(directory.resolve("options.tsv"), StandardCharsets.UTF_8); String[] options = settings.get(1).split("\t");
            var result = RLowess.fit(x, y, new RLowess.Options(Double.parseDouble(options[0]), Integer.parseInt(options[1]), Double.parseDouble(options[2])));
            var oracle = Files.readAllLines(directory.resolve("expected.tsv"), StandardCharsets.UTF_8); require(oracle.size() == x.length + 1);
            double[] fitted = result.fitted(), sorted = result.x(); int[] original = result.originalIndices(); double maximum = 0;
            for (int i = 0; i < fitted.length; i++) {
                String[] f = oracle.get(i + 1).split("\t"); double expectedX = Double.parseDouble(f[0]), expected = Double.parseDouble(f[1]);
                require(Double.doubleToLongBits(sorted[i]) == Double.doubleToLongBits(expectedX));
                require(original[i] == Integer.parseInt(f[2]));
                double error = Math.abs(expected - fitted[i]); maximum = Math.max(maximum, error);
                double tolerance = Math.max(16 * Math.ulp(expected), 2e-13 * scale);
                if (!Double.isFinite(fitted[i]) || (expected == 0 || fitted[i] == 0 ? expected != fitted[i] : error > tolerance))
                    throw new AssertionError(name + " point=" + i + " original=" + expected + " native=" + fitted[i] + " error=" + error + " tolerance=" + tolerance);
                checks++;
            }
            System.out.println("PASS_ORIGINAL_R362_LOWESS case=" + name + " complete_points=" + x.length + " maximum_absolute_error=" + maximum); cases++;
        }
        System.out.println("PASS_COMPLETE_ORIGINAL_R362_LOWESS cases=" + cases + " checks=" + checks);
    }
}
