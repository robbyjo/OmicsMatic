/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.sequence.PicardLibraryComplexity;

/** Complete original numerical-reference replay, including absent/nonfinite source estimates. */
public final class PicardComplexityControl {
    public static void main(String[] args) throws Exception {
        var lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8); int cases = 0, values = 0, missing = 0, nonfinite = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] f = line.split("\t", -1); if (f.length != 9) throw new AssertionError("Reference shape");
            var result = PicardLibraryComplexity.calculate(new PicardLibraryComplexity.Counts(Long.parseLong(f[1]), Long.parseLong(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4]), Long.parseLong(f[5])));
            same(result.duplicationFraction(), Double.parseDouble(f[6]));
            if (f[7].equals("NA")) { if (result.estimatedLibrarySize().isPresent() || result.roi().length != 0 || !f[8].isEmpty()) throw new AssertionError("Source absent estimate changed"); missing++; }
            else {
                if (result.estimatedLibrarySize().isEmpty() || result.estimatedLibrarySize().getAsLong() != Long.parseLong(f[7])) throw new AssertionError("Source estimate differs for " + f[0] + ": " + result.estimatedLibrarySize() + " expected " + f[7]);
                String[] roi = f[8].split(",", -1); if (roi.length != 100 || result.roi().length != 100) throw new AssertionError("Complete ROI population");
                double[] actual = result.roi(); for (int i = 0; i < 100; i++) { same(actual[i], Double.parseDouble(roi[i])); if (!Double.isFinite(actual[i])) nonfinite++; values++; }
                double[] copy = result.roi(); copy[0] = -123; same(result.roi()[0], actual[0]);
            }
            cases++;
        }
        expectFailure(() -> new PicardLibraryComplexity.Counts(-1, 1, 0, 0, 0));
        expectFailure(() -> new PicardLibraryComplexity.Counts(1, 1, 2, 0, 0));
        expectFailure(() -> new PicardLibraryComplexity.Counts(1, 1, 0, 0, 1));
        expectFailure(() -> new PicardLibraryComplexity.Counts(1, Long.MAX_VALUE, 0, 0, 0));
        expectFailure(() -> PicardLibraryComplexity.estimateLibrarySize(1, 2));
        expectFailure(() -> PicardLibraryComplexity.estimateRoi(1, Double.NaN, 1, 1));
        System.out.println("PASS_COMPLETE_PICARD2231_REFERENCE cases=" + cases + " ROI_values=" + values + " absent=" + missing + " nonfinite_preserved=" + nonfinite);
    }
    private static void same(double actual, double expected) {
        if (Double.isNaN(expected)) { if (!Double.isNaN(actual)) throw new AssertionError("Source NaN changed"); }
        else if (actual != expected && (!Double.isFinite(actual) || !Double.isFinite(expected) || Math.abs(actual - expected) > 4 * Math.ulp(expected))) throw new AssertionError("Source value differs: " + actual + " vs " + expected);
    }
    private static void expectFailure(Runnable action) { try { action.run(); } catch (IllegalArgumentException | ArithmeticException expected) { return; } throw new AssertionError("Invalid complete count/capacity inputs accepted"); }
}
