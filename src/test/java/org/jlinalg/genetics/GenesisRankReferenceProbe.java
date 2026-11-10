/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** External consumer for complete source controls; no model or project analysis admission. */
public final class GenesisRankReferenceProbe {
    private GenesisRankReferenceProbe() { }
    public static void main(String[] args) {
        try { run(args); }
        catch (Exception error) { System.err.println("FAIL2_NO_NATIVE_RANK_RESULT: " + error); System.exit(2); }
    }
    private static void run(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("batch")) { batch(Path.of(args[1]), Path.of(args[2])); return; }
        if (args.length != 3 || !(args[0].equals("all") || args[0].equals("by-group"))) throw new IllegalArgumentException("Require all|by-group INPUT NEW_OUTPUT");
        var lines = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("input_row\tgroup\tvalue_hex")) throw new IllegalArgumentException("Complete typed input required");
        double[] values = new double[lines.size() - 1]; List<String> groups = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            String[] f = lines.get(i + 1).split("\t", -1);
            if (f.length != 3 || !f[0].equals(Integer.toString(i + 1))) throw new IllegalArgumentException("Complete original row order differs");
            groups.add(f[1]); values[i] = switch (f[2]) { case "NA", "NaN" -> Double.NaN; case "Inf" -> Double.POSITIVE_INFINITY; case "-Inf" -> Double.NEGATIVE_INFINITY; default -> Double.parseDouble(f[2]); };
        }
        var limits = new GenesisRankNormalisation.Limits(1_000_000, 300_000_000);
        var result = args[0].equals("all") ? GenesisRankNormalisation.normalize(values, limits) : GenesisRankNormalisation.normalizeByGroup(values, groups, limits);
        double[] ranks = result.averageRanks(), scores = result.scores(); int[] sizes = result.groupSizes();
        try (var writer = Files.newBufferedWriter(Path.of(args[2]), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            writer.write("input_row\tgroup\tgroup_size\taverage_rank_hex\tscore_hex\n");
            for (int i = 0; i < values.length; i++) writer.write((i + 1) + "\t" + groups.get(i) + "\t" + sizes[i] + "\t" + Double.toHexString(ranks[i]) + "\t" + Double.toHexString(scores[i]) + "\n");
        }
    }
    private static void batch(Path manifest, Path out) throws Exception {
        Files.createDirectory(out); var records = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        if (records.isEmpty() || !records.get(0).equals("case\tmode\tinput")) throw new IllegalArgumentException("Complete batch manifest required");
        var seen = new java.util.HashSet<String>();
        try (var writer = Files.newBufferedWriter(out.resolve("native-dispositions.tsv"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            writer.write("case\tstatus\tcause_utf8_hex\n");
            for (String line : records.subList(1, records.size())) {
                String[] f = line.split("\t", -1);
                if (f.length != 3 || !f[0].matches("[a-z0-9-]+") || !seen.add(f[0]) || !f[2].equals(f[0] + ".tsv") || !List.of("all", "by-group").contains(f[1]))
                    throw new IllegalArgumentException("Invalid original batch identity/path");
                try {
                    run(new String[]{f[1], manifest.getParent().resolve(f[2]).toString(), out.resolve(f[0] + ".tsv").toString()});
                    writer.write(f[0] + "\tNATIVE_SUCCESS\t\n");
                } catch (Exception failure) {
                    writer.write(f[0] + "\tNATIVE_REFUSAL\t" + java.util.HexFormat.of().formatHex(failure.toString().getBytes(StandardCharsets.UTF_8)) + "\n");
                }
            }
        }
    }
}
