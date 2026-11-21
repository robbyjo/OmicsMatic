/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jlinalg.singlecell.Monocle100Weights;
import org.jlinalg.singlecell.Monocle100Weights.PrincipalEdge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class Monocle100WeightsTest {
    static Map<?,?> fixture() throws Exception {
        try (var stream = Monocle100WeightsTest.class.getResourceAsStream("/org/jlinalg/singlecell/monocle100/weights.json")) {
            assertNotNull(stream);
            byte[] raw = stream.readNBytes(1_048_577);
            assertTrue(raw.length <= 1_048_576);
            assertEquals("42716d01109b94bc1e836b76da5f47400df59da67bdf9764fd56e6a82c3ae0fa", NextflowFiles.sha(raw));
            return NextflowFiles.object(NextflowParameters.parse(new String(raw, StandardCharsets.UTF_8), 1_048_576, 500_000));
        }
    }
    static Stream<Arguments> controls() throws Exception {
        var rows = new ArrayList<Arguments>();
        for (var item : fixture().entrySet()) rows.add(Arguments.of(item.getKey(), NextflowFiles.object(item.getValue())));
        assertEquals(33, rows.size());
        return rows.stream();
    }
    static int integer(Object value) throws Exception { return (int) NextflowFiles.integer(value, 0, 1_000_000); }
    static int[] vector(Object value) throws Exception {
        var raw = NextflowFiles.list(value); int[] result = new int[raw.size()];
        for (int index = 0; index < result.length; index++) result[index] = integer(raw.get(index));
        return result;
    }
    static int[][] matrix(Object value) throws Exception {
        var raw = NextflowFiles.list(value); int[][] result = new int[raw.size()][];
        for (int index = 0; index < result.length; index++) result[index] = vector(raw.get(index));
        return result;
    }
    static List<?> originalNeighborRows(Object value, int cells) throws Exception {
        var named = NextflowFiles.object(value); assertEquals(cells, named.size());
        var result = new ArrayList<Object>(); int index = 1;
        for (var row : named.entrySet()) { assertEquals(Integer.toString(index++), row.getKey()); result.add(row.getValue()); }
        return result;
    }
    static Monocle100Weights.Result construct(Map<?,?> input) throws Exception {
        int[][] ranking = matrix(input.get("original_ranking_zero_based"));
        if (input.get("mode").equals("knn")) return Monocle100Weights.knn(ranking, 50_000_000);
        List<PrincipalEdge> edges = new ArrayList<>();
        for (int[] row : matrix(input.get("principal_edges_zero_based"))) edges.add(new PrincipalEdge(row[0], row[1]));
        return Monocle100Weights.principalGraph(ranking, vector(input.get("cell_to_principal_zero_based")),
                integer(input.get("principal_points")), edges, 50_000_000);
    }
    @ParameterizedTest(name="{0}") @MethodSource("controls")
    void matchesOriginalNeighborOrderingWeightsOrRefusesOriginalFailure(String name, Map<?,?> control) throws Exception {
        var input = NextflowFiles.object(control.get("input")); var expected = NextflowFiles.object(control.get("reference"));
        assertEquals(Boolean.TRUE, expected.get("RNG_identical"));
        if (Boolean.FALSE.equals(expected.get("returned"))) {
            assertThrows(IllegalArgumentException.class, () -> construct(input), name); return;
        }
        var result = construct(input); var neighbors = originalNeighborRows(expected.get("neighbors_zero_based"), result.cells());
        var weights = NextflowFiles.list(expected.get("weights_exact_tokens"));
        assertEquals(input.get("mode"), result.mode()); assertEquals(neighbors.size(), result.cells());
        int arcs = 0, isolated = 0, loops = 0;
        for (int from = 0; from < result.cells(); from++) {
            int[] row = vector(neighbors.get(from)); var originalWeights = NextflowFiles.list(weights.get(from));
            assertEquals(row.length, result.neighbors().get(from).size(), name); assertEquals(row.length, originalWeights.size());
            if (row.length == 0) isolated++;
            for (int column = 0; column < row.length; column++) {
                assertEquals(row[column], result.neighbors().get(from).get(column));
                var arc = result.arcs().get(arcs++); assertEquals(from, arc.from()); assertEquals(row[column], arc.to());
                assertEquals(Double.parseDouble(NextflowFiles.text(originalWeights.get(column))), arc.weight(), 0.0);
                if (from == row[column]) loops++;
            }
        }
        assertEquals(arcs, result.arcs().size()); assertEquals(isolated, result.isolatedRows()); assertEquals(loops, result.selfLinks());
        assertEquals(Boolean.TRUE, expected.get("save_reload_identical"));
    }
    @Test void tiedOriginalRankingsRetainSelfLinksRatherThanRepairingThem() throws Exception {
        int loops = 0;
        for (var item : fixture().entrySet()) if (item.getKey().toString().startsWith("ties")) loops += construct(NextflowFiles.object(NextflowFiles.object(item.getValue()).get("input"))).selfLinks();
        assertTrue(loops > 0);
    }
    @Test void resultOwnsItsPopulation() {
        int[][] rank = {{0,2,1},{1,0,2},{2,1,0}};
        var result = Monocle100Weights.knn(rank, 1_000); rank[0][1] = 0;
        assertEquals(List.of(2,1), result.neighbors().get(0));
        assertThrows(UnsupportedOperationException.class, () -> result.neighbors().get(0).set(0, 0));
        assertThrows(UnsupportedOperationException.class, () -> result.arcs().clear());
    }
    @Test void completeWorkCapacityRefusesBeforeAdmittingAnyGraph() {
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.knn(new int[][]{{0,2,1},{1,0,2},{2,1,0}}, 1));
    }
    @Test void invalidOrIncompleteAxesAreRefused() {
        int[][] rank = {{0,2,1},{1,0,2},{2,1,0}};
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.knn(new int[][]{{0,0,1},{1,0,2},{2,1,0}}, 1_000));
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.knn(new int[][]{{0,2,1},{1,0},{2,1,0}}, 1_000));
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.knn(new int[][]{{0,3,1},{1,0,2},{2,1,0}}, 1_000));
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.principalGraph(rank, new int[]{0,1}, 2, List.of(), 1_000));
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.principalGraph(rank, new int[]{0,1,2}, 2, List.of(), 1_000));
        assertThrows(IllegalArgumentException.class, () -> Monocle100Weights.principalGraph(rank, new int[]{0,1,0}, 2, List.of(new PrincipalEdge(0,2)), 1_000));
    }
}
