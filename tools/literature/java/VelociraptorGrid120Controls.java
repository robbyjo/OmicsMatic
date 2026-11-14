/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Arrays;
import org.jlinalg.singlecell.VelociraptorGrid120;

/** Independent boundary/domain and unchanged-input checks for the native arithmetic API. */
public final class VelociraptorGrid120Controls {
    private static int checks;
    private VelociraptorGrid120Controls() { }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void refuses(double[][] x, double[][] v, int resolution, boolean scale) {
        try {
            VelociraptorGrid120.grid(x, v, resolution, scale);
            throw new AssertionError("Unsupported complete input was accepted");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("original velociraptor"), "Actionable original-workflow instruction required");
        }
    }
    public static void main(String[] args) {
        double[][] x = {{0, 0}, {.1, .1}, {1, 1}, {.1, .1}, {.9, .1}, {0, 0}};
        double[][] v = {{1, 2}, {3, 4}, {-1, 2}, {2, -3}, {0, 1}, {5, 2}};
        double[][] originalX = Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);
        double[][] originalV = Arrays.stream(v).map(double[]::clone).toArray(double[][]::new);
        VelociraptorGrid120.Result value = VelociraptorGrid120.grid(x, v, 1, false);
        require(value.cells() == 6 && value.bins() == 2, "Complete cell/bin population required");
        int[] labels = {1, 1, 3, 1, 1, 1};
        for (int i = 0; i < labels.length; i++) require(value.groupOneBased(i) == labels[i], "Original first-matching-cell labels required");
        require(value.groupLabelOneBased(0) == 1 && value.groupLabelOneBased(1) == 3, "Sparse group labels must remain sparse");
        require(value.population(0) == 5 && value.population(1) == 1, "No duplicate event may be omitted");
        require(value.category(2, 0) == 1 && value.category(2, 1) == 1, "Terminal categories must be retained");
        require(Arrays.deepEquals(x, originalX) && Arrays.deepEquals(v, originalV), "Inputs changed during calculation");
        double before = value.start(0, 0);
        x[0][0] = 999;v[0][0] = 999;
        require(Double.doubleToRawLongBits(value.start(0, 0)) == Double.doubleToRawLongBits(before), "Returned snapshot aliases caller input");
        double[][] axes = {{0, 0}, {1, 1}};
        VelociraptorGrid120.Result zero = VelociraptorGrid120.grid(axes, new double[2][2], 40, true);
        require(zero.requiresNonFiniteReview(), "Zero-median nonfinite output must be prominent");
        for (int i = 0; i < zero.bins(); i++) for (int d = 0; d < 2; d++) require(Double.isNaN(zero.vector(i, d)) && Double.isNaN(zero.end(i, d)), "Original zero-median NaN must not be zero-filled");
        refuses(null, axes, 40, false);
        refuses(new double[0][2], new double[0][2], 40, false);
        refuses(axes, new double[1][2], 40, false);
        refuses(new double[][]{{0}, {1, 1}}, axes, 40, false);
        refuses(new double[][]{{0, 0}, {0, 1}}, axes, 40, false);
        refuses(new double[][]{{Double.NaN, 0}, {1, 1}}, axes, 40, false);
        refuses(axes, new double[][]{{0, Double.POSITIVE_INFINITY}, {1, 1}}, 40, false);
        refuses(axes, axes, 0, false);
        refuses(axes, axes, -1, false);
        refuses(new double[][]{{-Double.MAX_VALUE, 0}, {Double.MAX_VALUE, 1}}, axes, 40, false);
        refuses(new double[][]{{0, 0}, {Double.MIN_VALUE, Double.MIN_VALUE}}, axes, Integer.MAX_VALUE, false);
        refuses(new double[][]{{0, 0}, {0, 0}, {1, 1}}, new double[][]{{Double.MAX_VALUE, 0}, {Double.MAX_VALUE, 0}, {1, 1}}, 1, false);
        refuses(axes, new double[][]{{1e200, 0}, {0, 1e200}}, 40, true);
        VelociraptorGrid120.Result large = VelociraptorGrid120.grid(axes, new double[][]{{1e200, 0}, {0, 1e200}}, 40, false);
        require(!large.requiresNonFiniteReview() && large.vector(0, 0) == 1e200, "Unscaled arithmetic must not impose scaled-length requirements");
        System.out.println("PASS_NATIVE_GRID_GUARDS_AND_INPUT_IMMUTABILITY checks=" + checks);
    }
}
