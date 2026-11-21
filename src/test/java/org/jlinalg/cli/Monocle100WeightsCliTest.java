/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class Monocle100WeightsCliTest {
    @TempDir Path temp;
    static Stream<Arguments> controls() throws Exception { return Monocle100WeightsTest.controls(); }
    static Map<String,Object> prepared(Map<?,?> input) {
        var result = new LinkedHashMap<String,Object>(); result.put("schema_version", 1); result.put("source_profile", Monocle100WeightsCli.PROFILE);
        result.put("neighbor_graph", input.get("mode")); result.put("ranking_provenance", "Complete original RANN::nn2 ranking from the frozen synthetic source control");
        for (String key : List.of("cell_ids","original_ranking_zero_based")) result.put(key, input.get(key));
        if (input.get("mode").equals("principal_graph")) {
            for (String key : List.of("cell_to_principal_zero_based","principal_edges_zero_based","principal_ids")) result.put(key, input.get(key));
            result.put("principal_graph_provenance", "Explicit complete synthetic source principal graph and dp_mst order");
        }
        return result;
    }
    private Map<String,Object> sample() throws Exception {
        var control = NextflowFiles.object(Monocle100WeightsTest.fixture().get("12-5-knn")); return prepared(NextflowFiles.object(control.get("input")));
    }
    private Path request(Map<String,Object> input) throws Exception {
        NextflowFiles.writeJson(temp.resolve("neighbors.json"), input);
        NextflowFiles.writeJson(temp.resolve("request.json"), Map.of("schema_version",1,"neighbor_input","neighbors.json","out","result")); return temp.resolve("request.json");
    }
    private static PrintStream stream(OutputStream output) { return new PrintStream(output, true, StandardCharsets.UTF_8); }
    private int run(Path request) { return Monocle100WeightsCli.run(new String[]{"--request",request.toString()}, stream(new ByteArrayOutputStream()), stream(new ByteArrayOutputStream())); }
    private Map<?,?> analysis() throws Exception { return NextflowFiles.object(NextflowParameters.parse(Files.readString(temp.resolve("result/analysis.json")), 1_048_576, 100_000)); }
    @ParameterizedTest(name="{0}") @MethodSource("controls")
    void actualCliMatchesCompleteOriginalWeightTablesAndFailureAdmission(String name, Map<?,?> control) throws Exception {
        var originalInput = NextflowFiles.object(control.get("input")); var expected = NextflowFiles.object(control.get("reference"));
        boolean returned = Boolean.TRUE.equals(expected.get("returned"));
        assertEquals(returned ? 0 : 2, run(request(prepared(originalInput))), name);
        assertEquals(returned, analysis().get("weights_admitted")); assertEquals(Boolean.FALSE, analysis().get("scientific_test_admitted"));
        assertEquals(returned, Files.isRegularFile(temp.resolve("result/COMPLETED.json")));
        if (!returned) { assertFalse(Files.exists(temp.resolve("result/weights.tsv"))); return; }
        var ids = NextflowFiles.strings(originalInput.get("cell_ids")); var neighbors = Monocle100WeightsTest.originalNeighborRows(expected.get("neighbors_zero_based"), ids.size()); var weights = NextflowFiles.list(expected.get("weights_exact_tokens"));
        List<String> lines = Files.readAllLines(temp.resolve("result/weights.tsv")); assertEquals("from_cell\tto_cell\tweight", lines.get(0)); int index = 1;
        for (int from = 0; from < neighbors.size(); from++) {
            int[] row = Monocle100WeightsTest.vector(neighbors.get(from)); var originalWeights = NextflowFiles.list(weights.get(from));
            for (int column = 0; column < row.length; column++) {
                String[] fields = lines.get(index++).split("\t", -1); assertEquals(ids.get(from), fields[0]); assertEquals(ids.get(row[column]), fields[1]);
                assertEquals(Double.parseDouble(NextflowFiles.text(originalWeights.get(column))), Double.parseDouble(fields[2]), 0.0);
            }
        }
        assertEquals(index, lines.size());
        assertArrayEquals(Files.readAllBytes(temp.resolve("neighbors.json")), Files.readAllBytes(temp.resolve("result/prepared-neighbors.json")));
    }
    @Test void existingOutputIsPreserved() throws Exception {
        Path request = request(sample()); assertEquals(0, run(request)); byte[] before = Files.readAllBytes(temp.resolve("result/manifest.json"));
        assertEquals(2, run(request)); assertArrayEquals(before, Files.readAllBytes(temp.resolve("result/manifest.json")));
    }
    @Test void missingInputSkipsWithRequiredAction() throws Exception {
        Path request = request(sample()); Files.move(temp.resolve("neighbors.json"), temp.resolve("saved-neighbors.json"));
        var error = new ByteArrayOutputStream(); assertEquals(3, Monocle100WeightsCli.run(new String[]{"--request",request.toString()}, stream(new ByteArrayOutputStream()), stream(error)));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("SKIP3")); assertTrue(error.toString(StandardCharsets.UTF_8).contains("supply accessible permitted files"));
        assertFalse(Files.exists(temp.resolve("result")));
    }
    @Test void anotherEditionCannotBeSubstituted() throws Exception {
        var input = sample(); input.put("source_profile", "monocle3-1.3.3"); assertEquals(2, run(request(input))); assertEquals(Boolean.FALSE, analysis().get("weights_admitted"));
    }
    @Test void unknownFieldsAreRefused() throws Exception {
        var input = sample(); input.put("infer_missing_neighbors", true); assertEquals(2, run(request(input))); assertEquals(Boolean.FALSE, analysis().get("weights_admitted"));
    }
    @Test void brokenConsoleDoesNotAdmitResults() throws Exception {
        Path request = request(sample()); var broken = new OutputStream() { @Override public void write(int value) throws IOException { throw new IOException("Synthetic console failure"); } };
        assertEquals(2, Monocle100WeightsCli.run(new String[]{"--request",request.toString()}, stream(broken), stream(new ByteArrayOutputStream())));
        assertEquals(Boolean.FALSE, analysis().get("weights_admitted")); assertFalse(Files.exists(temp.resolve("result/COMPLETED.json"))); assertTrue(Files.exists(temp.resolve("result/unadmitted-analysis.json")));
    }
    @Test void lateSourceMutationRejectsAdmissionAndPreservesChangedInput() throws Exception {
        Path request = request(sample()); var changed = new OutputStream() {
            private boolean written;
            @Override public void write(int value) throws IOException { if (!written) { written = true; Files.writeString(temp.resolve("neighbors.json"), "foreign concurrent input\n"); } }
        };
        assertEquals(2, Monocle100WeightsCli.run(new String[]{"--request",request.toString()}, stream(changed), stream(new ByteArrayOutputStream())));
        assertEquals("foreign concurrent input\n", Files.readString(temp.resolve("neighbors.json"))); assertEquals(Boolean.FALSE, analysis().get("weights_admitted")); assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
    @Test void lateCapturedInputMutationCannotBeResealedAsComputedInput() throws Exception {
        Path request = request(sample()); var changed = new OutputStream() {
            private boolean written;
            @Override public void write(int value) throws IOException { if (!written) { written = true; Files.writeString(temp.resolve("result/prepared-neighbors.json"), "foreign captured bytes\n"); } }
        };
        assertEquals(2, Monocle100WeightsCli.run(new String[]{"--request",request.toString()}, stream(changed), stream(new ByteArrayOutputStream())));
        assertEquals("foreign captured bytes\n", Files.readString(temp.resolve("result/prepared-neighbors.json"))); assertEquals(Boolean.FALSE, analysis().get("weights_admitted")); assertFalse(Files.exists(temp.resolve("result/COMPLETED.json")));
    }
}
