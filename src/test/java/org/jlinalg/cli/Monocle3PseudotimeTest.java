/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.jlinalg.singlecell.Monocle3Pseudotime;
import org.jlinalg.singlecell.Monocle3Pseudotime.Edge;
import org.jlinalg.singlecell.Monocle3Pseudotime.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Monocle3PseudotimeTest {
    private static final class Reference {
        static final Map<?, ?> VALUE = read();
        private static Map<?, ?> read() {
            try (var in = Monocle3PseudotimeTest.class.getResourceAsStream(
                    "/org/jlinalg/singlecell/monocle3-pseudotime/original-controls-v1.json.gz")) {
                assertNotNull(in);
                byte[] zipped = in.readNBytes(96_106);
                assertEquals(96_105, zipped.length);
                assertEquals("4551d4fc706fdc8374fef33b60288a73030c7f5bb1a367caca0ce83161e5cb3d", NextflowFiles.sha(zipped));
                try (var gzip = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(zipped))) {
                    byte[] raw = gzip.readNBytes(625_522);
                    assertEquals(625_521, raw.length);
                    return NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw), 1_048_576, 1_000_000));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Original Monocle3 helper reference intake failed", e);
            }
        }
    }

    static Stream<Arguments> references() throws Exception {
        var fixture = Reference.VALUE;
        assertEquals(false, fixture.get("original_whole_package_loaded"));
        assertEquals(false, fixture.get("original_fit_performed"));
        var byId = new java.util.HashMap<String, Map<?, ?>>();
        for (Object raw : NextflowFiles.list(NextflowFiles.object(fixture.get("plan")).get("cases"))) {
            var row = NextflowFiles.object(raw);
            byId.put(NextflowFiles.text(row.get("id")), row);
        }
        var controls = NextflowFiles.list(fixture.get("controls"));
        assertEquals(519, controls.size());
        return controls.stream().map(raw -> {
            try {
                var row = NextflowFiles.object(raw);
                String id = NextflowFiles.text(row.get("id"));
                return Arguments.of(row.get("version") + " / " + id, byId.get(id), row);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static double number(Object raw) {
        assertInstanceOf(NextflowParameters.NumberToken.class, raw);
        return Double.parseDouble(((NextflowParameters.NumberToken) raw).text());
    }

    private static double[][] coordinates(Object raw) throws Exception {
        var rows = NextflowFiles.list(raw);
        double[][] result = new double[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            var row = NextflowFiles.list(rows.get(i));
            result[i] = new double[row.size()];
            for (int j = 0; j < row.size(); j++) {
                result[i][j] = number(row.get(j));
            }
        }
        return result;
    }

    private static Result run(Map<?, ?> source) throws Exception {
        List<Edge> edges = new ArrayList<>();
        for (Object raw : NextflowFiles.list(source.get("edges"))) {
            var row = NextflowFiles.object(raw);
            edges.add(new Edge((int) NextflowFiles.integer(row.get("from"), 0, 1_000_000),
                    (int) NextflowFiles.integer(row.get("to"), 0, 1_000_000), number(row.get("weight"))));
        }
        return Monocle3Pseudotime.order(NextflowFiles.strings(source.get("cell_ids")),
                coordinates(source.get("cell_coordinates")), NextflowFiles.strings(source.get("principal_ids")),
                coordinates(source.get("principal_coordinates")), NextflowFiles.strings(source.get("roots")),
                NextflowFiles.strings(source.get("graph_vertex_ids")), edges);
    }

    @ParameterizedTest(name="{0}") @MethodSource("references")
    void matchesEveryOriginalHelperOutcome(String label, Map<?, ?> input, Map<?, ?> reference) throws Exception {
        if (!(Boolean) reference.get("returned")) {
            assertEquals("one-dimensional-multiple-roots", input.get("id"));
            assertEquals("x and y must be conform in columns.", NextflowFiles.object(reference.get("source_error")).get("message"));
            var failure = assertThrows(IllegalArgumentException.class, () -> run(input));
            assertTrue(failure.getMessage().contains("original helper fails"));
            return;
        }
        Result result = run(input);
        assertEquals(NextflowFiles.strings(input.get("cell_ids")), result.cellIds());
        assertEquals(NextflowFiles.strings(reference.get("graph_vertex_order")), result.graphVertexIds());
        var roots = NextflowFiles.list(reference.get("selected_cell_indices_zero_based"));
        assertEquals(roots.size(), result.selectedRoots().size());
        for (int i = 0; i < roots.size(); i++) {
            assertEquals(NextflowFiles.integer(roots.get(i), 0, 1_000_000), result.selectedRoots().get(i).zeroBasedCellIndex(), label);
            assertEquals(NextflowFiles.strings(input.get("roots")).get(i), result.selectedRoots().get(i).principalPoint());
            assertEquals(result.cellIds().get(result.selectedRoots().get(i).zeroBasedCellIndex()), result.selectedRoots().get(i).cellId());
        }
        compare(reference.get("graph_pseudotime"), result.graphPseudotime(), label);
        compare(reference.get("input_cell_pseudotime"), result.pseudotime(), label);
        assertEquals(Arrays.stream(result.pseudotime()).filter(Double::isInfinite).count(), result.infiniteCellCount());
    }

    private static void compare(Object tokens, double[] actual, String label) throws Exception {
        var values = NextflowFiles.strings(tokens);
        assertEquals(values.size(), actual.length);
        for (int i = 0; i < values.size(); i++) {
            double expected = values.get(i).equals("Inf") ? Double.POSITIVE_INFINITY : Double.parseDouble(values.get(i));
            assertEquals(expected, actual[i], 0.0, label + " / " + i);
        }
    }

    private static Result basic(List<String> cells, double[][] positions, List<String> vertices,
                                List<Edge> edges, long budget) {
        return Monocle3Pseudotime.order(cells, positions, List.of("root"), new double[][]{{2.1,0}},
                List.of("root"), vertices, edges, budget);
    }

    @Test void geodesicStartsAtNearestCellAndUsesSuppliedWeights() {
        var ids = List.of("a", "b", "c");
        var result = basic(ids, new double[][]{{0,0},{4,0},{8,0}}, ids,
                List.of(new Edge(0,1,3), new Edge(1,2,5)), 1000);
        assertEquals("b", result.selectedRoots().get(0).cellId());
        assertArrayEquals(new double[]{3,0,5}, result.pseudotime(), 0);
    }

    @Test void literalIdentifiersAndBothOrdersRemainExact() {
        var cells = List.of("NA", "重复\"λ", " ");
        var vertices = List.of(" ", "projection", "NA", "重复\"λ");
        var result = basic(cells, new double[][]{{2.1,0},{4,0},{8,0}}, vertices,
                List.of(new Edge(2,1,3),new Edge(1,3,4),new Edge(3,0,2)),1000);
        assertEquals(cells,result.cellIds());assertEquals(vertices,result.graphVertexIds());
        assertArrayEquals(new double[]{0,7,9},result.pseudotime(),0);
        assertArrayEquals(new double[]{9,3,0,7},result.graphPseudotime(),0);
    }

    @Test void outputsCannotBeMutatedThroughPublicCollectionsOrArrays() {
        var ids = new ArrayList<>(List.of("a","b"));
        var result = basic(ids,new double[][]{{2.1,0},{4,0}},ids,List.of(Edge.unweighted(0,1)),1000);
        ids.set(0,"changed");result.pseudotime()[0]=99;result.graphPseudotime()[0]=88;
        assertEquals("a",result.cellIds().get(0));assertEquals(0,result.pseudotime()[0]);assertEquals(0,result.graphPseudotime()[0]);
        assertThrows(UnsupportedOperationException.class,()->result.selectedRoots().clear());
        assertThrows(UnsupportedOperationException.class,()->result.cellIds().clear());
        assertThrows(UnsupportedOperationException.class,()->result.graphVertexIds().clear());
        assertThrows(UnsupportedOperationException.class,()->result.warnings().add("changed"));
    }

    @Test void ambiguousOrMissingIdentifiersAreRefusedWithoutNormalization() {
        var xy = new double[][]{{0,0},{1,0}};
        for (List<String> ids : List.of(List.of("a","a"),List.of("","b"))) {
            assertThrows(IllegalArgumentException.class,()->basic(ids,xy,List.of("a","b"),List.of(),1000));
        }
        assertThrows(IllegalArgumentException.class,()->basic(Arrays.asList(null,"b"),xy,List.of("a","b"),List.of(),1000));
        assertThrows(IllegalArgumentException.class,()->basic(List.of("a","b"),xy,List.of("a","a"),List.of(),1000));
        assertThrows(IllegalArgumentException.class,()->basic(List.of("a","b"),xy,List.of("a","c"),List.of(),1000));
        assertThrows(IllegalArgumentException.class,()->Monocle3Pseudotime.order(List.of("a"),new double[][]{{0,0}},
                List.of("r","r"),new double[][]{{0,0},{1,0}},List.of("r"),List.of("a"),List.of()));
    }

    @Test void coordinatesAreNotImputedOrReshaped() {
        var ids = List.of("a","b");
        for (double[][] bad : Arrays.asList(null,new double[][]{{0,0}},new double[][]{{0},{1,2}},
                new double[][]{{Double.NaN,0},{1,0}},new double[][]{{0,0},{Double.POSITIVE_INFINITY,0}})) {
            assertThrows(IllegalArgumentException.class,()->basic(ids,bad,ids,List.of(),1000));
        }
        assertThrows(IllegalArgumentException.class,()->basic(List.of("a"),new double[][]{new double[1025]},List.of("a"),List.of(),10000));
    }

    @Test void invalidWeightsAndEndpointsAreRefused() {
        var ids = List.of("a","b");
        for (Edge bad : Arrays.asList(null,new Edge(-1,0,1),new Edge(0,2,1),new Edge(0,1,-1),
                new Edge(0,1,Double.NaN),new Edge(0,1,Double.POSITIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class,()->basic(ids,new double[][]{{0,0},{1,0}},ids,Arrays.asList(bad),1000));
        }
    }

    @Test void budgetsIncludePreparationAndGraphWorkAndNeverReturnPartialValues() {
        var ids = List.of("a","b","c");var xy = new double[][]{{0,0},{4,0},{8,0}};
        var edges = List.of(Edge.unweighted(0,1),Edge.unweighted(1,2));
        var complete = basic(ids,xy,ids,edges,1000);
        assertArrayEquals(complete.pseudotime(),basic(ids,xy,ids,edges,complete.operations()).pseudotime());
        assertThrows(IllegalArgumentException.class,()->basic(ids,xy,ids,edges,complete.operations()-1));
        assertThrows(IllegalArgumentException.class,()->basic(ids,xy,ids,edges,0));
        assertThrows(IllegalArgumentException.class,()->basic(ids,xy,ids,edges,1));
    }

    @Test void rootSelectionMustBeExplicitAndPresent() {
        for (List<String> roots : Arrays.asList(null,List.<String>of(),List.of("absent"))) {
            assertThrows(IllegalArgumentException.class,()->Monocle3Pseudotime.order(List.of("a"),new double[][]{{0,0}},
                    List.of("r"),new double[][]{{0,0}},roots,List.of("a"),List.of()));
        }
        assertThrows(IllegalArgumentException.class,()->Monocle3Pseudotime.order(List.of("a"),new double[][]{{0,0}},
                List.of("r"),new double[][]{{0,0}},java.util.Collections.nCopies(10000,"r"),List.of("a"),List.of()));
    }

    @Test void sourceOverflowValuesAndWarningsAreRetained() {
        var result = Monocle3Pseudotime.order(List.of("a","b","c"),new double[][]{{-1e308,0},{0,0},{1e308,0}},
                List.of("r"),new double[][]{{1e308,1e308}},List.of("r"),List.of("a","b","c"),
                List.of(new Edge(0,1,1e308),new Edge(1,2,1e308)));
        assertEquals(0,result.selectedRoots().get(0).zeroBasedCellIndex());
        assertEquals(Double.POSITIVE_INFINITY,result.selectedRoots().get(0).euclideanDistance());
        assertArrayEquals(new double[]{0,1e308,Double.POSITIVE_INFINITY},result.pseudotime(),0);
        assertEquals(1,result.infiniteCellCount());
        assertTrue(result.warnings().stream().anyMatch(s->s.startsWith("ROOT_DISTANCE_OVERFLOW:")));
        assertTrue(result.warnings().stream().anyMatch(s->s.startsWith("PATH_SUM_OVERFLOW:")));
        assertTrue(result.warnings().stream().anyMatch(s->s.startsWith("INFINITE_PSEUDOTIME:")));
    }

    @Test void graphDistancesAlsoMatchIndependentMinPlusClosure() {
        var random = new Random(20261107);
        for (int iteration=0;iteration<40;iteration++) {
            int n=9;var ids=new ArrayList<String>();double[][] positions=new double[n][2];
            double[][] expected=new double[n][n];var edges=new ArrayList<Edge>();
            for(int i=0;i<n;i++){ids.add("v"+i);positions[i][0]=i;Arrays.fill(expected[i],Double.POSITIVE_INFINITY);expected[i][i]=0;}
            for(int i=0;i<n*2;i++){int a=random.nextInt(n),b=random.nextInt(n);double w=random.nextInt(12)/8.0;edges.add(new Edge(a,b,w));expected[a][b]=Math.min(expected[a][b],w);expected[b][a]=Math.min(expected[b][a],w);}
            for(int k=0;k<n;k++)for(int i=0;i<n;i++)for(int j=0;j<n;j++)expected[i][j]=Math.min(expected[i][j],expected[i][k]+expected[k][j]);
            var actual=Monocle3Pseudotime.order(ids,positions,List.of("left","right"),new double[][]{{0,0},{8,0}},List.of("left","right"),ids,edges);
            for(int i=0;i<n;i++)assertEquals(Math.min(expected[0][i],expected[8][i]),actual.pseudotime()[i],0);
        }
    }
}
