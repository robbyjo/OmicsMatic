/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CellEmbeddingCliTest {
    @TempDir Path directory;
    String output;
    private int run(String method, String python, String name, String... extra) throws Exception {
        Path matrix = directory.resolve("pca.tsv");
        if (!Files.exists(matrix)) {
            Random random = new Random(91);
            StringBuilder data = new StringBuilder("obs_id\tPC1\tPC2\tPC3\tPC4\n");
            for (int i = 0; i < 120; i++) {
                int group = i / 40;
                data.append("C").append(i);
                for (int j = 0; j < 4; j++) data.append('\t').append(20 * group + random.nextGaussian());
                data.append('\n');
            }
            Files.writeString(matrix, data);
        }
        List<String> args = new ArrayList<>(List.of("cell-embed", "--method", method, "--matrix", matrix.toString(),
            "--python", python, "--out", directory.resolve(name).toString(), "--seed", "1", "--neighbors", "10", "--no-config"));
        args.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int status = JLinAlgCli.run(args.toArray(String[]::new), stream, stream);
        output = bytes.toString(); return status;
    }
    @Test void unavailableExecutablePreservesFailureAndActionWithoutResults() throws Exception {
        assertEquals(2, run("umap", directory.resolve("missing-python").toString(), "failed"));
        assertTrue(output.contains("ANALYSIS FAILED"));
        assertTrue(Files.readString(directory.resolve("failed/WARNINGS.md")).contains("Required action"));
        assertTrue(Files.readString(directory.resolve("failed/manifest.yaml")).contains("analysis_status: failed"));
        assertFalse(Files.exists(directory.resolve("failed/results.tsv")));
    }
    @Test void nativeTsneRunsWithoutPythonAndRecordsSeedObjectiveAndObservationOrder() throws Exception {
        Path matrix = directory.resolve("native.tsv");
        Random random = new Random(3807);
        StringBuilder data = new StringBuilder("obs_id\tPC1\tPC2\tPC3\n");
        for (int i = 0; i < 60; i++) data.append("cell_").append(i).append('\t')
            .append(15 * (i / 20) + random.nextGaussian()).append('\t')
            .append(15 * (i / 20) + random.nextGaussian()).append('\t')
            .append(random.nextGaussian()).append('\n');
        Files.writeString(matrix, data);
        for (String name : List.of("native", "repeat")) {
            var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
            assertEquals(0, JLinAlgCli.run(new String[]{"cell-embed", "--method", "tsne", "--matrix", matrix.toString(),
                "--perplexity", "8", "--iterations", "750", "--seed", "17", "--out", directory.resolve(name).toString(), "--no-config"}, stream, stream), bytes.toString());
        }
        assertEquals(Files.readString(directory.resolve("native/results.tsv")), Files.readString(directory.resolve("repeat/results.tsv")));
        var results = DelimitedData.read(directory.resolve("native/results.tsv"));
        assertEquals(List.of("obs_id", "TSNE1", "TSNE2"), results.header());
        for (int i = 0; i < 60; i++) assertEquals("cell_" + i, results.rows().get(i)[0]);
        String session = Files.readString(directory.resolve("native/session.yaml"));
        assertTrue(session.contains("native exact 2D t-SNE") && session.contains("seed: 17"));
        assertTrue(session.contains("final_kl:") && session.contains("optimizer_stop_reason:"));
        assertTrue(Files.isRegularFile(directory.resolve("native/diagnostics.tsv")));
        assertFalse(Files.exists(directory.resolve("native/cell_embedding.py")));
    }
    @Test void nativeTsneInvalidDataPersistsProminentActionableFailure() throws Exception {
        for (String reason : List.of("duplicate", "nonfinite", "tied")) {
            Path matrix = directory.resolve(reason + ".tsv");
            Files.writeString(matrix, switch (reason) {
                case "duplicate" -> "obs_id\tPC1\nA\t0\nA\t1\nC\t3\n";
                case "nonfinite" -> "obs_id\tPC1\nA\t0\nB\tNaN\nC\t3\n";
                default -> "obs_id\tPC1\nA\t0\nB\t0\nC\t0\nD\t0\nE\t1\n";
            });
            Path out = directory.resolve(reason);
            var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
            assertEquals(2, JLinAlgCli.run(new String[]{"cell-embed", "--method", "tsne", "--matrix", matrix.toString(),
                "--perplexity", "2", "--out", out.toString(), "--no-config"}, stream, stream));
            assertTrue(bytes.toString().contains("ANALYSIS FAILED"));
            assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action"));
            assertTrue(Files.readString(out.resolve("manifest.yaml")).contains("analysis_status: failed"));
            assertFalse(Files.exists(out.resolve("results.tsv")));
        }
    }
    @Test void realPinnedUmapIsFiniteSeededAndKeepsObservationOrder() throws Exception {
        String python = System.getenv("JLINALG_CELL_PYTHON");
        assumeTrue(python != null, "Optional upstream validation: set JLINALG_CELL_PYTHON to the private pinned environment");
        assertEquals(0, run("umap", python, "umap"), output);
        assertEquals(0, run("umap", python, "umap-repeat"), output);
        assertEquals(Files.readString(directory.resolve("umap/results.tsv")), Files.readString(directory.resolve("umap-repeat/results.tsv")));
        assertEquals(120, DelimitedData.read(directory.resolve("umap/results.tsv")).rows().size());
        assertTrue(Files.readString(directory.resolve("umap/session.json")).contains("0.5.12"));
    }
    @Test void realLeidenAndLouvainRecoverSeparatedNeighborhoods() throws Exception {
        String python = System.getenv("JLINALG_CELL_PYTHON");
        assumeTrue(python != null, "Optional upstream validation: set JLINALG_CELL_PYTHON");
        for (String method : List.of("leiden", "louvain")) {
            assertEquals(0, run(method, python, method), output);
            var result = DelimitedData.read(directory.resolve(method).resolve("results.tsv"));
            Map<String, int[]> clusters = new HashMap<>();
            for (String[] row : result.rows()) {
                int group = Integer.parseInt(row[0].substring(1)) / 40;
                clusters.computeIfAbsent(row[1], key -> new int[3])[group]++;
            }
            int correct = 0;
            for (int[] group : clusters.values()) correct += Math.max(group[0], Math.max(group[1], group[2]));
            assertTrue(correct >= 117, method + " graph communities should separate the known three-group fixture");
            assertTrue(Files.exists(directory.resolve(method).resolve("graph.tsv")));
        }
    }
    private int graphRun(String method, String python, String name, Path nodes, Path edges, String... extra) {
        var args = new ArrayList<>(List.of("cell-embed", "--method", method, "--nodes", nodes.toString(), "--graph", edges.toString(),
            "--python", python, "--out", directory.resolve(name).toString(), "--seed", "17", "--no-config"));
        args.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int code = JLinAlgCli.run(args.toArray(String[]::new), stream, stream);
        output = bytes.toString(); return code;
    }
    @Test void realSuppliedWeightedGraphsPreserveWeightsIsolatesAndKnownCommunities() throws Exception {
        String python = System.getenv("JLINALG_CELL_PYTHON");
        assumeTrue(python != null, "Set JLINALG_CELL_PYTHON for actual upstream graph validation");
        Path nodes = directory.resolve("nodes.tsv"), edges = directory.resolve("edges.tsv");
        Files.writeString(nodes, "obs_id\nA\nB\nC\nD\nE\nF\nisolate\n");
        Files.writeString(edges, "source\ttarget\tweight\nB\tA\t7\nA\tC\t8\nB\tC\t9\nD\tE\t10\nF\tD\t11\nE\tF\t12\nC\tD\t0.001\n");
        for (String method : List.of("leiden", "louvain")) {
            assertEquals(0, graphRun(method, python, method + "-graph", nodes, edges), output);
            assertEquals(0, graphRun(method, python, method + "-graph-repeat", nodes, edges), output);
            Path result = directory.resolve(method + "-graph");
            assertEquals(Files.readString(result.resolve("results.tsv")), Files.readString(directory.resolve(method + "-graph-repeat/results.tsv")));
            var rows = DelimitedData.read(result.resolve("results.tsv")).rows();
            assertEquals(List.of("A", "B", "C", "D", "E", "F", "isolate"), rows.stream().map(r -> r[0]).toList());
            assertEquals(rows.get(0)[1], rows.get(2)[1]); assertEquals(rows.get(3)[1], rows.get(5)[1]);
            assertNotEquals(rows.get(0)[1], rows.get(3)[1]);
            assertNotEquals(rows.get(6)[1], rows.get(0)[1]); assertNotEquals(rows.get(6)[1], rows.get(3)[1]);
            var graphRows = DelimitedData.read(result.resolve("graph.tsv")).rows();
            assertEquals(7, graphRows.size()); assertArrayEquals(new String[]{"A", "B", "7.0"}, graphRows.get(0));
            assertTrue(Files.readString(result.resolve("session.json")).contains("\"isolated_nodes\": 1"));
            assertTrue(Files.readString(result.resolve("manifest.yaml")).contains("nodes_sha256:"));
        }
    }
    @Test void suppliedGraphRejectsAmbiguousOrInvalidEdgesAndKeepsActionableFailures() throws Exception {
        String python = System.getenv("JLINALG_CELL_PYTHON");
        assumeTrue(python != null, "Set JLINALG_CELL_PYTHON");
        Path nodes = directory.resolve("nodes.tsv"), edges = directory.resolve("edges.tsv");
        Files.writeString(nodes, "obs_id\nA\nB\nC\n");
        var badEdges = List.of("A\tB\t1\nB\tA\t2\n", "A\tA\t1\n", "A\tB\t-1\n", "A\tB\tNaN\n", "A\tmissing\t1\n", "");
        for (int i = 0; i < badEdges.size(); i++) {
            Files.writeString(edges, "source\ttarget\tweight\n" + badEdges.get(i));
            String name = "invalid-graph-" + i;
            assertEquals(2, graphRun("leiden", python, name, nodes, edges), output);
            assertTrue(Files.readString(directory.resolve(name + "/WARNINGS.md")).contains("Required action"));
            assertFalse(Files.exists(directory.resolve(name + "/results.tsv")));
        }
        assertEquals(2, graphRun("leiden", python, "invalid-neighbors", nodes, edges, "--neighbors", "2"));
        assertFalse(Files.exists(directory.resolve("invalid-neighbors")));
        assertEquals(2, graphRun("leiden", python, "invalid-timeout", nodes, edges, "--timeout", "0"));
        assertFalse(Files.exists(directory.resolve("invalid-timeout")));
    }
    @Test void unavailableHarmonyIsSkippedWithPersistentDependencyInstructions() throws Exception {
        String python = System.getenv("JLINALG_CELL_PYTHON");
        assumeTrue(python != null, "Optional upstream dependency validation: set JLINALG_CELL_PYTHON");
        Path metadata = directory.resolve("cells.tsv");
        StringBuilder text = new StringBuilder("obs_id\tbatch\n");
        for (int i = 0; i < 120; i++) text.append("C").append(i).append('\t').append(i % 2 == 0 ? "A" : "B").append('\n');
        Files.writeString(metadata, text);
        int status = run("harmony", python, "harmony", "--metadata", metadata.toString(), "--batch", "batch");
        if (status == 0) {
            assertEquals(120, DelimitedData.read(directory.resolve("harmony/results.tsv")).rows().size());
        } else {
            assertEquals(3, status, output); assertTrue(output.contains("SKIPPED ANALYSIS"));
            assertTrue(Files.readString(directory.resolve("harmony/WARNINGS.md")).contains("Required action"));
            assertTrue(Files.readString(directory.resolve("harmony/analysis-status.json")).contains("skipped-dependency-unavailable"));
            assertFalse(Files.exists(directory.resolve("harmony/results.tsv")));
        }
    }
}
