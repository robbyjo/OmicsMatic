/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.StringJoiner;

/** Standalone reference/contract probe, not a user-data workflow or JUnit suite. */
public final class MrMegaRegressionReferenceProbe {
    private MrMegaRegressionReferenceProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--contracts")) { contracts(); return; }
        if (args.length != 2) throw new IllegalArgumentException("Use CONTROL_INPUT NEW_OUTPUT or --contracts");
        var text = new StringBuilder(); int count = 0;
        try (BufferedReader in = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] header = line.trim().split("\\s+");
                if (header.length != 3) throw new IllegalArgumentException("Bad control header");
                int n = Integer.parseInt(header[1]), axes = Integer.parseInt(header[2]);
                if (n < 3 || n > 1000 || axes < 0 || axes > 20) throw new IllegalArgumentException("Control capacity exceeded");
                double[] y = new double[n], w = new double[n]; double[][] x = new double[n][axes];
                for (int i = 0; i < n; i++) {
                    String[] fields = in.readLine().trim().split("\\s+");
                    if (fields.length != axes + 2) throw new IllegalArgumentException("Bad complete control row");
                    y[i] = Double.parseDouble(fields[0]); w[i] = Double.parseDouble(fields[1]);
                    for (int j = 0; j < axes; j++) x[i][j] = Double.parseDouble(fields[j + 2]);
                }
                var r = MrMegaRegression.fitWeighted(y, w, x); var row = new StringJoiner("\t");
                row.add(header[0]).add(Integer.toString(n)).add(Integer.toString(axes));
                row.add(vector(r.coefficients())).add(vector(r.standardErrors()));
                var covariance = new StringJoiner(","); for (double[] values : r.covariance()) for (double value : values) covariance.add(Double.toString(value));
                row.add(covariance.toString());
                for (double value : new double[]{r.weightedMean(),r.centeredTotalSquares(),r.zeroTotalSquares(),r.residualSquares(),r.residualScale(),
                        r.associationChiSquare(),r.ancestryChiSquare(),r.residualChiSquare(),r.associationDegrees(),r.ancestryDegrees(),r.residualDegrees(),
                        r.stableAssociationP(),r.stableAncestryP(),r.stableResidualP(),r.logBayesFactor()}) row.add(Double.toString(value));
                text.append(row).append('\n'); count++;
            }
        }
        if (count == 0) throw new IllegalArgumentException("No complete controls");
        Files.writeString(Path.of(args[1]), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("PASS_MRMEGA_KERNEL_PROBE_ONLY " + count);
    }

    private static String vector(double[] values) {
        var text = new StringJoiner(","); for (double value : values) text.add(Double.toString(value)); return text.toString();
    }

    private static void contracts() {
        double[] y = {1,2,3,4}, w = {1,1,1,1}; double[][] x = {{},{},{},{}};
        var r = MrMegaRegression.fitWeighted(y, w, x);
        near(r.coefficients()[0], 2.5); near(r.residualSquares(), 5); near(r.centeredTotalSquares(), 5);
        near(r.zeroTotalSquares(), 30); near(r.residualScale(), 5.0 / 3); near(r.covariance()[0][0], 5.0 / 12);
        near(r.associationChiSquare(), 25); near(r.logBayesFactor(), (25 - Math.log(4)) / 2);
        if (!Double.isNaN(r.stableAncestryP()) || r.residualDegrees() != 3) throw new AssertionError("Zero ancestry test or degrees changed");
        r.coefficients()[0] = 999; r.covariance()[0][0] = 999; r.fitted()[0] = 999; r.residuals()[0] = 999;
        near(r.coefficients()[0], 2.5); near(r.covariance()[0][0], 5.0 / 12); near(r.fitted()[0], 2.5); near(r.residuals()[0], 1.5);
        if (!Arrays.equals(y,new double[]{1,2,3,4}) || !Arrays.equals(w,new double[]{1,1,1,1})) throw new AssertionError("Input mutation");
        near(MrMegaRegression.fit(y,w,x).coefficients()[0],r.coefficients()[0]);
        var zero = MrMegaRegression.fitWeighted(new double[4],w,x);
        if (!Double.isNaN(zero.stableAssociationP()) || !Double.isNaN(zero.stableResidualP())) throw new AssertionError("Zero statistic was silently repaired");
        reject(() -> MrMegaRegression.fitWeighted(null,w,x));
        reject(() -> MrMegaRegression.fitWeighted(y,new double[3],x));
        reject(() -> MrMegaRegression.fitWeighted(y,new double[]{1,0,1,1},x));
        reject(() -> MrMegaRegression.fitWeighted(y,new double[]{1,-1,1,1},x));
        reject(() -> MrMegaRegression.fitWeighted(new double[]{1,Double.NaN,3,4},w,x));
        reject(() -> MrMegaRegression.fitWeighted(y,new double[]{1,Double.POSITIVE_INFINITY,1,1},x));
        reject(() -> MrMegaRegression.fitWeighted(y,w,new double[][]{{1},{1},{1},{1}}));
        reject(() -> MrMegaRegression.fitWeighted(y,w,new double[][]{{1},{2},{3},{}}));
        reject(() -> MrMegaRegression.fitWeighted(y,w,new double[][]{{1,0},{2,1},{3,4},{4,7}}));
        reject(() -> MrMegaRegression.fit(y,new double[]{1,0,1,1},x));
        reject(() -> MrMegaRegression.fit(y,new double[]{1,1e-300,1,1},x));
        System.out.println("PASS_MRMEGA_ANALYTIC_INPUT_IMMUTABILITY_AND_ELEVEN_REFUSAL_CONTRACTS_ONLY");
    }

    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-12 * Math.max(1,Math.abs(expected))) throw new AssertionError(actual + " != " + expected);
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid/unsupported complete input was admitted");
    }
}
