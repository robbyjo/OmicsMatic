/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Original Monocle3 1.0.0 calculateLW neighbor selection and W-style weights
 * after an explicit original RANN nearest-neighbor ranking has been supplied.
 * This method does not calculate nearest neighbors or learn a principal graph.
 *
 * <p>The first ranking column is discarded literally, as in the selected source.
 * With coincident coordinates it is not necessarily the query cell: resulting
 * self-links are retained. KNN mode preserves ranking order. Principal-graph mode
 * retains only equal or adjacent principal-point memberships and returns neighbors
 * in ascending cell-index order, following the original sparse-row extraction.
 * Jaccard magnitudes are not weights in this path: the original passes FALSE to
 * jaccard_coeff and ultimately uses binary neighbors and nb2listw's W style.
 *
 * <p>Source: MIT-licensed cole-trapnell-lab/monocle3, commit
 * 004c096f3a3bdaf1e6709a65fb36f40ba775f1ab, R/graph_test.R and src/clustering.cpp.
 * Principal indices refer to the actual dp_mst column order used by that source,
 * not graph insertion order or a silently sorted set of principal-point labels.
 */
public final class Monocle100Weights {
    public static final long DEFAULT_MAXIMUM_OPERATIONS = 50_000_000L;
    private static final long MAXIMUM_RANK_ENTRIES = 16_000_000L;
    private static final int MAXIMUM_CELLS = 1_000_000;

    private Monocle100Weights() { }

    /** A zero-based undirected principal edge; parallel edges and loops add no extra weight. */
    public record PrincipalEdge(int from, int to) { }

    /** A zero-based directed cell link. Self-links, if selected, remain explicit. */
    public record Arc(int from, int to, double weight) { }

    /** Complete row population, including isolated rows; collections are immutable. */
    public record Result(int cells, int retainedRankingColumns, List<List<Integer>> neighbors,
                         List<Arc> arcs, int isolatedRows, int selfLinks,
                         long operations, String mode) {
        public Result {
            neighbors = neighbors.stream().map(List::copyOf).toList();
            arcs = List.copyOf(arcs);
        }
    }

    /** Preserve the supplied ranking after discarding column zero. Indices are zero based. */
    public static Result knn(int[][] originalRanking, long maximumOperations) {
        return prepare(originalRanking, null, 0, List.of(), maximumOperations);
    }

    /** Gate neighbors against the complete explicitly ordered principal graph. */
    public static Result principalGraph(int[][] originalRanking, int[] cellToPrincipal,
                                        int principalPoints, List<PrincipalEdge> principalEdges,
                                        long maximumOperations) {
        require(cellToPrincipal != null, "Supply complete cell-to-principal membership");
        return prepare(originalRanking, cellToPrincipal, principalPoints, principalEdges, maximumOperations);
    }

    private static Result prepare(int[][] ranks, int[] memberships, int points,
                                  List<PrincipalEdge> edges, long maximumOperations) {
        require(ranks != null && ranks.length >= 3 && ranks.length <= MAXIMUM_CELLS,
                "Supply at least three complete ranking rows within the declared capacity");
        require(maximumOperations > 0, "Declare a positive complete-work capacity");
        int cells = ranks.length;
        require(ranks[0] != null && ranks[0].length >= 3 && ranks[0].length <= cells,
                "Ranking needs its first column and at least two retained neighbors; original singleton extraction fails");
        int columns = ranks[0].length;
        require((long) cells * columns <= MAXIMUM_RANK_ENTRIES, "Complete ranking exceeds capacity; do not trim cells");
        boolean gated = memberships != null;
        require(edges != null, "Principal edge population must be explicit");
        if (gated) {
            require(points > 0 && points <= MAXIMUM_CELLS && memberships.length == cells,
                    "Complete principal point count and cell membership required");
        }
        long work = (long) cells * columns * 3L + (gated ? (long) edges.size() * 2L + cells : 0L);
        // Principal mode sorts each selected row; this conservative bound precedes the work.
        if (gated) work += (long) cells * columns * (32 - Integer.numberOfLeadingZeros(columns));
        require(work <= maximumOperations, "Complete weight construction exceeds operation capacity; no partial graph admitted");
        Set<Long> adjacent = new HashSet<>();
        if (gated) {
            Set<Integer> occupied = new HashSet<>();
            for (int point : memberships) require(point >= 0 && point < points, "Principal membership outside declared point axis");
            for (int point : memberships) occupied.add(point);
            require(occupied.size() >= 2,
                    "Original Monocle1.0.0 single occupied principal point fails factor contrasts; no graph admitted");
            for (PrincipalEdge edge : edges) {
                require(edge != null && edge.from() >= 0 && edge.from() < points && edge.to() >= 0 && edge.to() < points,
                        "Principal edge outside declared point axis");
                adjacent.add(pair(edge.from(), edge.to()));
                adjacent.add(pair(edge.to(), edge.from()));
            }
        }
        List<List<Integer>> rows = new ArrayList<>(cells);
        List<Arc> arcs = new ArrayList<>();
        int isolated = 0, selfLinks = 0, maximumDegree = 0;
        for (int from = 0; from < cells; from++) {
            int[] row = ranks[from];
            require(row != null && row.length == columns, "Ranking must be rectangular with every cell retained");
            Set<Integer> seen = new HashSet<>();
            for (int to : row) require(to >= 0 && to < cells && seen.add(to), "Ranking indices must be unique and on the complete cell axis");
            int[] selected = new int[columns - 1];
            int count = 0;
            for (int index = 1; index < columns; index++) {
                int to = row[index];
                if (!gated || memberships[from] == memberships[to]
                        || adjacent.contains(pair(memberships[from], memberships[to]))) selected[count++] = to;
            }
            if (gated) Arrays.sort(selected, 0, count);
            List<Integer> neighbors = new ArrayList<>(count);
            if (count == 0) isolated++;
            for (int index = 0; index < count; index++) {
                int to = selected[index];
                neighbors.add(to);
                arcs.add(new Arc(from, to, 1.0 / count));
                if (from == to) selfLinks++;
            }
            rows.add(neighbors);
            maximumDegree = Math.max(maximumDegree, count);
        }
        require(!gated || maximumDegree > 1,
                "Original Monocle1.0.0 all-scalar sparse-row conversion fails, including isolated/single-neighbor rows; no graph admitted");
        return new Result(cells, columns - 1, rows, arcs, isolated, selfLinks, work,
                gated ? "principal_graph" : "knn");
    }

    private static long pair(int from, int to) { return ((long) from << 32) | (to & 0xffffffffL); }
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
