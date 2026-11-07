/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Arrays;
import java.util.List;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import org.jlinalg.singlecell.Monocle3Pseudotime.Edge;

/** A synthetic prepared graph, with no expression fitting or biological claim. */
public final class MonoclePseudotimeExample {
    private MonoclePseudotimeExample() { }

    public static void main(String[] args) {
        var cells = List.of("cell-a", "cell-b", "cell-c");
        var result = Monocle3Pseudotime.order(cells,
                new double[][]{{0,0}, {4,0}, {8,0}},
                List.of("Y_1"), new double[][]{{2.1,0}}, List.of("Y_1"),
                cells, List.of(new Edge(0,1,3), new Edge(1,2,5)));
        if (!Arrays.equals(new double[]{3,0,5}, result.pseudotime())
                || !result.selectedRoots().get(0).cellId().equals("cell-b")) {
            throw new AssertionError("Prepared pseudotime example differs from its exact original reference");
        }
        for (String warning : result.warnings()) {
            System.err.println(warning);
        }
        System.out.println("Principal root Y_1 selects nearest cell: " + result.selectedRoots().get(0).cellId());
        System.out.println("Cell order: " + result.cellIds());
        System.out.println("Pseudotime: " + Arrays.toString(result.pseudotime()));
    }
}
