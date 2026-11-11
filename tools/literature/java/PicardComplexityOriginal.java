/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import picard.sam.DuplicationMetrics;

/** Serialization of original upstream results only; no native implementation in this reference. */
public final class PicardComplexityOriginal {
    public static void main(String[] args) throws Exception {
        List<String> output = new ArrayList<>(); output.add("case\tunpaired\tpaired\tunpaired_duplicates\tpaired_duplicates\toptical_duplicates\tfraction\tsize\tROI");
        List<String> input = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8);
        for (String line : input.subList(1, input.size())) {
            String[] f = line.split("\t", -1); DuplicationMetrics metrics = new DuplicationMetrics();
            metrics.UNPAIRED_READS_EXAMINED = Long.parseLong(f[1]); metrics.READ_PAIRS_EXAMINED = Long.parseLong(f[2]);
            metrics.UNPAIRED_READ_DUPLICATES = Long.parseLong(f[3]); metrics.READ_PAIR_DUPLICATES = Long.parseLong(f[4]); metrics.READ_PAIR_OPTICAL_DUPLICATES = Long.parseLong(f[5]);
            metrics.calculateDerivedFields(); StringBuilder roi = new StringBuilder();
            if (metrics.ESTIMATED_LIBRARY_SIZE != null) for (int i = 1; i <= 100; i++) { if (i > 1) roi.append(','); roi.append(Double.toString(DuplicationMetrics.estimateRoi(metrics.ESTIMATED_LIBRARY_SIZE, i, metrics.READ_PAIRS_EXAMINED, metrics.READ_PAIRS_EXAMINED - metrics.READ_PAIR_DUPLICATES))); }
            output.add(line + "\t" + Double.toString(metrics.PERCENT_DUPLICATION) + "\t" + (metrics.ESTIMATED_LIBRARY_SIZE == null ? "NA" : metrics.ESTIMATED_LIBRARY_SIZE.toString()) + "\t" + roi);
        }
        Files.write(Paths.get(args[1]), output, StandardCharsets.UTF_8);
    }
}
