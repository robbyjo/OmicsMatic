/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.genetics.GenesisPcairPartition;

/** Complete project-owned API example; it fits no PCA or association model. */
public final class GenesisPartitionDemo {
    private GenesisPartitionDemo() { }
    public static void main(String[] args) {
        var matrix = new GenesisPcairPartition.Matrix(List.of("A", "B", "C", "D"), new double[][] {
            {.5, .25, 0, 0}, {.25, .5, 0, 0}, {0, 0, .5, .25}, {0, 0, .25, .5}
        });
        var options = new GenesisPcairPartition.Options(.02, -.02, null, null);
        var limits = new GenesisPcairPartition.Limits(100, 1_000_000, 10_000_000);
        var result = GenesisPcairPartition.partition(matrix, null, options, limits);
        for (String warning : result.warnings()) System.err.println("ORIGINAL SOURCE WARNING: " + warning);
        if (!result.unrelatedSetPassesDeclaredKinshipCutoff()) {
            System.err.println("FAIL2: skip dependent use; source unrelated set contains related pairs. Preserve the source finding and review the explicit input/forcing choices.");
            System.exit(2);
        }
        System.out.println("Native matrix partition component only; no PCA, PC-Relate or model fit.");
        System.out.println("Related: " + result.related());
        System.out.println("Unrelated: " + result.unrelated());
    }
}
