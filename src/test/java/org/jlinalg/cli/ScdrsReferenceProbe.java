/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.ScdrsScores;

/** External consumer for source differential controls; not an analysis CLI. */
public final class ScdrsReferenceProbe {
    private ScdrsReferenceProbe() { }
    public static void main(String[] args) {
        try {
            if (args.length != 2 || Files.size(Path.of(args[0])) > 32_000_000)
                throw new IllegalArgumentException("Require bounded INPUT and NEW_OUTPUT");
            Object parsed = SimpleJson.parse(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> m)) throw new IllegalArgumentException("Expected fixture object");
            double[] raw = vector(m.get("raw")), ratio = vector(m.get("ratio"));
            double[][] ctrl = matrix(m.get("controls"));
            var limits = new ScdrsScores.Limits(10_000, 2000, 1_000_000_000L);
            List<String> cells = new ArrayList<>(); for (int i = 0; i < raw.length; i++) cells.add("cell-" + i);
            var background = ScdrsScores.correctBackground(raw, ctrl, ratio, limits);
            var scores = ScdrsScores.score(cells, raw, ctrl, ratio, limits);
            var group = ScdrsScores.groupAssociation(background.trait(), background.controls(), limits);
            var corr = ScdrsScores.correlation(vector(m.get("annotation")), background.trait(), background.controls(), limits);
            double[] slotValues = vector(m.get("slots")); int[] slots = new int[slotValues.length];
            for (int i = 0; i < slots.length; i++) { slots[i] = (int) slotValues[i]; if (slots[i] != slotValues[i]) throw new IllegalArgumentException("Integer slot required"); }
            var rawResult = ScdrsScores.rawScore(matrix(m.get("expression")), slots, vector(m.get("weights")),
                vector(m.get("variance")), vector(m.get("var_tech")), ScdrsScores.Weighting.valueOf((String) m.get("weighting")), 1_000_000_000L);
            // Open the output only after all computations succeeded.
            try (var out = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                out.write("kind\trow\tcolumn\tvalue_hex\n");
                write(out, "trait", background.trait()); write(out, "controls", background.controls());
                float[][] table = scores.values(); double[][] converted = new double[table.length][table[0].length];
                for (int i = 0; i < table.length; i++) for (int j = 0; j < table[i].length; j++) converted[i][j] = table[i][j];
                write(out, "scores", converted); write(out, "raw", rawResult.scores()); write(out, "weights", rawResult.normalizedWeights());
                write(out, "independent_variance", new double[]{rawResult.independentVariance()});
                write(out, "group", new double[]{group.statistic(), group.p(), group.z()});
                write(out, "correlation", new double[]{corr.statistic(), corr.p(), corr.z()});
                write(out, "bh", ScdrsScores.benjaminiHochberg(vector(m.get("pvalues"))));
                write(out, "empirical", ScdrsScores.empiricalP(vector(m.get("empirical_observed")), vector(m.get("empirical_null"))));
            }
        } catch (Exception error) {
            System.err.println("FAIL2_NO_NATIVE_SCDRS_RESULT: " + error
                + "\nRequired action: preserve inputs and this attempt; resolve the reported input, numerical or capacity cause and retry a NEW output. No repair is performed.");
            System.exit(2);
        }
    }
    private static double[] vector(Object value) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("Expected numeric vector");
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) { if (!(values.get(i) instanceof Number n)) throw new IllegalArgumentException("Expected number"); result[i] = n.doubleValue(); }
        return result;
    }
    private static double[][] matrix(Object value) {
        if (!(value instanceof List<?> rows)) throw new IllegalArgumentException("Expected matrix");
        double[][] result = new double[rows.size()][]; for (int i = 0; i < result.length; i++) result[i] = vector(rows.get(i)); return result;
    }
    private static void write(java.io.Writer out, String kind, double[] values) throws java.io.IOException {
        for (int i = 0; i < values.length; i++) out.write(kind + "\t" + i + "\t0\t" + Double.toHexString(values[i]) + "\n");
    }
    private static void write(java.io.Writer out, String kind, double[][] values) throws java.io.IOException {
        for (int i = 0; i < values.length; i++) for (int j = 0; j < values[i].length; j++) out.write(kind + "\t" + i + "\t" + j + "\t" + Double.toHexString(values[i][j]) + "\n");
    }
}
