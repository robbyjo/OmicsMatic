/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** External consumer for original-source differential controls; no user analysis admission. */
public final class MtagReferenceProbe {
    private MtagReferenceProbe() { }
    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("Require complete INPUT and NEW_OUTPUT");
            var lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
            String[] header = lines.get(0).split("\t", -1);
            if (header.length != 3 || !header[0].equals("MTAG108")) throw new IllegalArgumentException("Wrong complete input schema");
            int m = Integer.parseInt(header[1]), t = Integer.parseInt(header[2]);
            if (m < 1 || m > 5000 || t < 1 || t > 32 || lines.size() != 3 + 2 * m + 2 * t)
                throw new IllegalArgumentException("Incomplete control or capacity exceeded");
            List<String> traits = List.of(lines.get(1).split("\t", -1)), variants = List.of(lines.get(2).split("\t", -1));
            if (traits.size() != t || variants.size() != m) throw new IllegalArgumentException("Identity dimensions differ");
            double[][] z = matrix(lines, 3, m, t), n = matrix(lines, 3 + m, m, t);
            double[][] omega = matrix(lines, 3 + 2 * m, t, t), sigma = matrix(lines, 3 + 2 * m + t, t, t);
            var limits = new MtagEstimator.Limits(5000, 32, 100_000_000);
            var result = MtagEstimator.estimate(variants, traits, z, n, omega, sigma, limits);
            double[][] gmm = MtagEstimator.unadjustedGmmOmega(variants, traits, z, n, sigma, limits);
            try (var writer = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                writer.write("kind\trow\tcolumn\tvalue_hex\n");
                String[] kinds = {"beta", "se", "factor", "z", "p", "gmm"};
                double[][][] matrices = {result.standardizedBeta(), result.standardizedSe(), result.factor(), result.z(), result.p(), gmm};
                for (int k = 0; k < kinds.length; k++) for (int i = 0; i < matrices[k].length; i++)
                    for (int j = 0; j < matrices[k][i].length; j++)
                        writer.write(kinds[k] + "\t" + i + "\t" + j + "\t" + Double.toHexString(matrices[k][i][j]) + "\n");
            }
        } catch (Exception error) {
            System.err.println("FAIL2_NO_NATIVE_MTAG_RESULT: " + error
                + "\nRequired action: preserve original inputs; resolve the reported unsupported covariance/data/capacity cause and retry a NEW output. No matrix or data repair is performed.");
            System.exit(2);
        }
    }
    private static double[][] matrix(List<String> lines, int start, int rows, int cols) {
        double[][] x = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            String[] f = lines.get(start + i).split("\t", -1);
            if (f.length != cols) throw new IllegalArgumentException("Complete numeric dimensions differ");
            for (int j = 0; j < cols; j++) x[i][j] = Double.parseDouble(f[j]);
        }
        return x;
    }
}
