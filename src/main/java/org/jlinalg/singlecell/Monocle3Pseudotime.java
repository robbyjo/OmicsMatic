/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Native prepared-graph counterpart of Monocle3's extract_general_graph_ordering.
 * The caller supplies cell/principal coordinates, principal roots and the actual
 * projected cell graph. This method does not infer a graph, embedding, root cell,
 * trajectory, expression model or biological direction.
 *
 * <p>Each selected principal point is mapped to the first nearest cell in the
 * supplied cell order, using the source's Euclidean-distance ties. Pseudotime is
 * the minimum weighted undirected graph distance from these cells. In particular,
 * it is not the distance from the principal point itself. Infinite pseudotime is
 * retained. Returned cell values follow the supplied cell order; graph values
 * follow the supplied graph vertex order, including any extra projection nodes.
 *
 * <p>Based on MIT-licensed Monocle3 functions by Cole Trapnell and contributors:
 * https://github.com/cole-trapnell-lab/monocle3, source profiles 1.0.0, 1.3.1 and
 * 1.4.27. Their exact commits, source bytes and notices accompany the reference
 * controls. This component does not establish complete Monocle package parity.
 */
public final class Monocle3Pseudotime {
    public static final long DEFAULT_MAX_OPERATIONS = 50_000_000L;
    private static final int MAX_CELLS = 1_000_000;
    private static final int MAX_VERTICES = 1_000_000;
    private static final int MAX_EDGES = 5_000_000;
    private static final int MAX_PRINCIPAL_POINTS = 9_999;
    private static final int MAX_DIMENSIONS = 1_024;

    private Monocle3Pseudotime() { }

    /** Zero-based endpoints in graph vertex order; loops and parallel edges are retained. */
    public record Edge(int from, int to, double weight) {
        /** Source igraph distances use unit weights when the weight attribute is absent. */
        public static Edge unweighted(int from, int to) {
            return new Edge(from, to, 1.0);
        }
    }

    /** A supplied principal root and the actual source-selected nearest cell. */
    public record SelectedRoot(String principalPoint, int zeroBasedCellIndex,
                               String cellId, double euclideanDistance) { }

    /** Immutable output; double arrays are copied on access and retain positive infinity. */
    public static final class Result {
        private final List<String> cellIds;
        private final List<String> graphVertexIds;
        private final List<SelectedRoot> selectedRoots;
        private final double[] pseudotime;
        private final double[] graphPseudotime;
        private final int infiniteCellCount;
        private final long operations;
        private final List<String> warnings;

        private Result(List<String> cellIds, List<String> graphVertexIds,
                       List<SelectedRoot> selectedRoots, double[] pseudotime,
                       double[] graphPseudotime, long operations, List<String> warnings) {
            this.cellIds = List.copyOf(cellIds);
            this.graphVertexIds = List.copyOf(graphVertexIds);
            this.selectedRoots = List.copyOf(selectedRoots);
            this.pseudotime = pseudotime.clone();
            this.graphPseudotime = graphPseudotime.clone();
            this.operations = operations;
            this.warnings = List.copyOf(warnings);
            int count = 0;
            for (double value : pseudotime) {
                if (Double.isInfinite(value)) {
                    count++;
                }
            }
            infiniteCellCount = count;
        }

        public List<String> cellIds() { return cellIds; }
        public List<String> graphVertexIds() { return graphVertexIds; }
        public List<SelectedRoot> selectedRoots() { return selectedRoots; }
        public double[] pseudotime() { return pseudotime.clone(); }
        public double[] graphPseudotime() { return graphPseudotime.clone(); }
        public int infiniteCellCount() { return infiniteCellCount; }
        public long operations() { return operations; }
        public List<String> warnings() { return warnings; }
    }

    public static Result order(List<String> cellIds, double[][] cellCoordinates,
                               List<String> principalIds, double[][] principalCoordinates,
                               List<String> rootPrincipalIds, List<String> graphVertexIds,
                               List<Edge> edges) {
        return order(cellIds, cellCoordinates, principalIds, principalCoordinates,
                rootPrincipalIds, graphVertexIds, edges, DEFAULT_MAX_OPERATIONS);
    }

    /**
     * Order a caller-prepared graph. Coordinates must be finite, with one common
     * dimension count. Edge weights must be finite and nonnegative. Identifiers
     * are literal, unique and nonempty within each population; root selections
     * may repeat. Every cell must have a graph vertex. These explicit input
     * restrictions refuse ambiguous or unsupported source domains without repair.
     * One-dimensional coordinates with multiple root selections are refused:
     * the original helper drops matrix dimensions and fails in that domain.
     * The work limit is a logical budget, not a process or heap quota.
     *
     * @throws IllegalArgumentException for invalid input or an exhausted work limit
     */
    public static Result order(List<String> cellIds, double[][] cellCoordinates,
                               List<String> principalIds, double[][] principalCoordinates,
                               List<String> rootPrincipalIds, List<String> graphVertexIds,
                               List<Edge> edges, long maxOperations) {
        Budget budget = new Budget(maxOperations);
        Map<String, Integer> cells = identifiers(cellIds, MAX_CELLS, "cell", budget);
        Map<String, Integer> principals = identifiers(principalIds, MAX_PRINCIPAL_POINTS,
                "principal point", budget);
        Map<String, Integer> vertices = identifiers(graphVertexIds, MAX_VERTICES,
                "graph vertex", budget);
        if (rootPrincipalIds == null || rootPrincipalIds.isEmpty()
                || rootPrincipalIds.size() > MAX_PRINCIPAL_POINTS) {
            throw bad("declare 1..9999 principal roots; interactive root selection is unavailable");
        }
        if (edges == null || edges.size() > MAX_EDGES) {
            throw bad("supply at most " + MAX_EDGES + " graph edges");
        }
        int dimensions = coordinates(cellCoordinates, cells.size(), -1, "cell", budget);
        coordinates(principalCoordinates, principals.size(), dimensions, "principal point", budget);
        if (dimensions == 1 && rootPrincipalIds.size() > 1) {
            throw bad("the original helper fails on one-dimensional coordinates with multiple roots; no repaired result is supplied");
        }
        int[] cellVertices = new int[cells.size()];
        for (int i = 0; i < cellIds.size(); i++) {
            budget.take(1);
            Integer vertex = vertices.get(cellIds.get(i));
            if (vertex == null) {
                throw bad("cell is absent from the prepared graph: " + cellIds.get(i));
            }
            cellVertices[i] = vertex;
        }

        int vertexCount = vertices.size();
        int[] degree = new int[vertexCount];
        for (Edge edge : edges) {
            budget.take(1);
            if (edge == null || edge.from() < 0 || edge.from() >= vertexCount
                    || edge.to() < 0 || edge.to() >= vertexCount) {
                throw bad("graph edge endpoint is outside the supplied vertex order");
            }
            if (!Double.isFinite(edge.weight()) || edge.weight() < 0.0) {
                throw bad("graph weights must be finite and nonnegative; do not repair missing weights");
            }
            degree[edge.from()]++;
            degree[edge.to()]++;
        }
        int[] offsets = new int[vertexCount + 1];
        for (int i = 0; i < vertexCount; i++) {
            budget.take(1);
            offsets[i + 1] = offsets[i] + degree[i];
        }
        int[] next = offsets.clone();
        int[] neighbors = new int[2 * edges.size()];
        double[] weights = new double[neighbors.length];
        for (Edge edge : edges) {
            budget.take(2);
            int a = next[edge.from()]++;
            neighbors[a] = edge.to();
            weights[a] = edge.weight();
            int b = next[edge.to()]++;
            neighbors[b] = edge.from();
            weights[b] = edge.weight();
        }

        List<SelectedRoot> selectedRoots = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::distance)
                .thenComparingInt(Visit::vertex));
        double[] distances = new double[vertexCount];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        boolean rootDistanceOverflow = false;
        for (String root : rootPrincipalIds) {
            budget.take(1);
            Integer principal = principals.get(root);
            if (principal == null) {
                throw bad("root principal point is absent: " + root);
            }
            double[] point = principalCoordinates[principal];
            int closest = -1;
            double nearest = Double.POSITIVE_INFINITY;
            for (int i = 0; i < cellCoordinates.length; i++) {
                budget.take(dimensions + 1L);
                double squared = 0.0;
                for (int j = 0; j < dimensions; j++) {
                    double delta = point[j] - cellCoordinates[i][j];
                    squared += delta * delta;
                }
                // Compare the square roots as proxy::dist + which.min do. Taking
                // a stable hypot or comparing only squares can change source ties.
                double distance = Math.sqrt(squared);
                if (closest < 0 || distance < nearest) {
                    closest = i;
                    nearest = distance;
                }
            }
            rootDistanceOverflow |= Double.isInfinite(nearest);
            selectedRoots.add(new SelectedRoot(root, closest, cellIds.get(closest), nearest));
            int vertex = cellVertices[closest];
            if (distances[vertex] != 0.0) {
                distances[vertex] = 0.0;
                queue.add(new Visit(vertex, 0.0));
            }
        }

        boolean pathSumOverflow = false;
        while (!queue.isEmpty()) {
            budget.take(1);
            Visit visit = queue.remove();
            if (visit.distance() != distances[visit.vertex()]) {
                continue;
            }
            for (int i = offsets[visit.vertex()]; i < offsets[visit.vertex() + 1]; i++) {
                budget.take(1);
                double candidate = visit.distance() + weights[i];
                pathSumOverflow |= Double.isInfinite(candidate);
                if (candidate < distances[neighbors[i]]) {
                    distances[neighbors[i]] = candidate;
                    queue.add(new Visit(neighbors[i], candidate));
                }
            }
        }
        double[] pseudotime = new double[cellIds.size()];
        boolean infinite = false;
        for (int i = 0; i < pseudotime.length; i++) {
            budget.take(1);
            pseudotime[i] = distances[cellVertices[i]];
            infinite |= Double.isInfinite(pseudotime[i]);
        }
        if (rootDistanceOverflow) {
            warnings.add("ROOT_DISTANCE_OVERFLOW: source Euclidean distances overflowed for a principal root; first-cell Inf ties were retained.");
        }
        if (pathSumOverflow) {
            warnings.add("PATH_SUM_OVERFLOW: a finite graph path sum overflowed; original double infinity was retained without rescaling.");
        }
        if (infinite) {
            warnings.add("INFINITE_PSEUDOTIME: retain Inf for disconnected cells or overflowed path distances; this is not negative biological evidence.");
        }
        return new Result(cellIds, graphVertexIds, selectedRoots, pseudotime,
                distances, budget.used, warnings);
    }

    private static Map<String, Integer> identifiers(List<String> ids, int maximum,
                                                   String label, Budget budget) {
        if (ids == null || ids.isEmpty() || ids.size() > maximum) {
            throw bad("supply 1.." + maximum + " " + label + " identifiers");
        }
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            budget.take(1);
            String id = ids.get(i);
            if (id == null || id.isEmpty() || result.putIfAbsent(id, i) != null) {
                throw bad(label + " identifiers must be literal, nonempty and unique");
            }
        }
        return result;
    }

    private static int coordinates(double[][] values, int rows, int dimensions,
                                   String label, Budget budget) {
        if (values == null || values.length != rows) {
            throw bad(label + " coordinates must match their complete identifier population");
        }
        int result = dimensions;
        for (double[] row : values) {
            if (row == null || row.length < 1 || row.length > MAX_DIMENSIONS) {
                throw bad(label + " coordinates require 1.." + MAX_DIMENSIONS + " finite dimensions");
            }
            if (result < 0) {
                result = row.length;
            }
            if (row.length != result) {
                throw bad("all cell and principal coordinates must have the same dimension count");
            }
            budget.take(row.length);
            for (double value : row) {
                if (!Double.isFinite(value)) {
                    throw bad(label + " coordinates must be finite; missing values are not imputed");
                }
            }
        }
        return result;
    }

    private static IllegalArgumentException bad(String detail) {
        return new IllegalArgumentException("Monocle3 prepared pseudotime: " + detail);
    }

    private record Visit(int vertex, double distance) { }

    private static final class Budget {
        private final long limit;
        private long used;

        Budget(long limit) {
            if (limit < 1) {
                throw bad("logical work budget must be positive");
            }
            this.limit = limit;
        }

        void take(long count) {
            if (count < 0 || count > limit - used) {
                throw bad("logical work budget exhausted; increase the explicit limit or reduce the complete prepared population");
            }
            used += count;
        }
    }
}
