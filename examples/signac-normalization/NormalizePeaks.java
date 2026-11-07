/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import java.util.Locale;
import org.jlinalg.sequence.SignacNormalization;

/** Tiny native API example; no upstream process or biological analysis is run. */
public final class NormalizePeaks {
    private NormalizePeaks() { }
    public static void main(String[] args) {
        boolean empty = args.length == 1 && args[0].equals("--empty-cell");
        if (args.length > 0 && !empty) {
            System.err.println("Usage: NormalizePeaks [--empty-cell]"); System.exit(2);
        }
        // Rows: peak A=(1,2,0), B=(3,0,0), C=(0,4,8).
        // CSC pointers delimit columns; row indices are sorted within each column.
        var counts = new SignacNormalization.Counts(List.of("peak-A","peak-B","peak-C"),
                empty ? List.of("cell-1","cell-2","cell-3","empty-cell") : List.of("cell-1","cell-2","cell-3"),
                empty ? new int[]{0,2,4,5,5} : new int[]{0,2,4,5},
                new int[]{0,1,0,2,2}, new double[]{1,3,2,4,8});
        var result = SignacNormalization.runTfidf(counts, SignacNormalization.Method.LOG_TF_IDF, 1e4, null);
        for (var warning : result.warnings()) {
            System.err.println("WARNING " + warning.code() + ": " + warning.message());
            System.err.println("Required action: " + warning.requiredAction());
        }
        int[] p=result.matrix().columnPointers(), rows=result.matrix().rowIndices();
        double[] values=result.matrix().values();
        for (double value : values) if (!Double.isFinite(value)) {
            System.err.println("SKIP3: finite-only downstream analysis skipped. Review the warnings and parameters above."); System.exit(3);
        }
        System.out.println("feature\tcell\ttfidf");
        for(int cell=0;cell<counts.cells().size();cell++) for(int k=p[cell];k<p[cell+1];k++) {
            System.out.printf(Locale.ROOT,"%s\t%s\t%.12g%n",counts.features().get(rows[k]),counts.cells().get(cell),values[k]);
        }
        var selected=SignacNormalization.findTopFeatures(counts,SignacNormalization.Cutoff.count(5),null);
        System.out.println("selected(count>5)\t"+String.join(",",selected.selected()));
    }
}
