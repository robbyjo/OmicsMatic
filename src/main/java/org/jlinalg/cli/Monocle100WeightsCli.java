/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.singlecell.Monocle100Weights;
import org.jlinalg.singlecell.Monocle100Weights.PrincipalEdge;

/** Native original-edition weight selection from a complete declared neighbor ranking. */
public final class Monocle100WeightsCli {
    public static final String PROFILE = "monocle3-1.0.0-004c096f3a3bdaf1e6709a65fb36f40ba775f1ab";
    private static final int MAX_INPUT_BYTES = 64 * 1024 * 1024;
    private static final String SCOPE = "Native Monocle3 1.0.0 neighbor selection and W-style weights from a complete caller-supplied original RANN ranking. The first ranking column is discarded literally, including tied-coordinate self-links. Principal mode gates equal/adjacent principal memberships and sorts retained cell indices. No nearest-neighbor calculation, graph learning, expression/covariate preparation or statistical test is performed. This is a standalone source-build component; it awards no new catalog family credit or whole-paper qualification.";
    private static final String ACTION = "SKIP3: weight preparation skipped for missing or forbidden input/output access; supply accessible permitted files and retry NEW output. This Java stage installs and launches no R software. FAIL2: no complete weight graph admitted; preserve raw inputs and partial outputs, read FAILURE.txt and WARNINGS.md, correct the named index, source-profile, population, capacity or integrity cause and retry NEW output. Do not drop cells, change tie order or delete source-selected self-links to make another method accept the graph. Current MORAN-LM refuses self-links; a returned weight graph alone is not an admitted residual-Moran test. Require exit0, COMPLETED.json and analysis.json with weights_admitted=true.";

    private Monocle100WeightsCli() { }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }

    public static int run(String[] args, PrintStream output, PrintStream error) {
        Path owned = null;
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                output.println("Monocle100WeightsCli --request ASCII_PATH.json\nRequest schema_version=1, neighbor_input JSON path, NEW out; optional maximum_operations. Prepared input schema_version=1, source_profile=" + PROFILE
                        + ", neighbor_graph=knn|principal_graph, ranking_provenance, cell_ids, original_ranking_zero_based. Principal mode also requires principal_ids, cell_to_principal_zero_based, principal_edges_zero_based and principal_graph_provenance. All axes are complete and literal; indices are zero-based.\n" + SCOPE + "\n" + ACTION);
                output.flush(); return output.checkError() ? 2 : 0;
            }
            require(args.length == 2 && args[0].equals("--request"), "Use --request JSON or --help");
            Path request = regular(Path.of(args[1])); byte[] requestRaw = NextflowFiles.bytes(request, 1_048_576);
            var requestPin = new NextflowFiles.Pin(requestRaw.length, NextflowFiles.sha(requestRaw));
            var parameters = parse(requestRaw, 1_048_576, 100_000);
            require(Set.of("schema_version","neighbor_input","out","maximum_operations").containsAll(parameters.keySet()), "Unknown request field");
            NextflowFiles.integer(parameters.get("schema_version"), 1, 1);
            long maximum = parameters.containsKey("maximum_operations") ? NextflowFiles.integer(parameters.get("maximum_operations"), 1, 2_000_000_000L) : Monocle100Weights.DEFAULT_MAXIMUM_OPERATIONS;
            Path input = regular(resolve(request.getParent(), parameters.get("neighbor_input")));
            Path destination = destination(resolve(request.getParent(), parameters.get("out")), List.of(request, input));
            Files.createDirectories(destination.getParent()); owned = Files.createDirectory(destination);
            write(owned.resolve("WARNINGS.md"), "# Scope and required actions\n\n" + SCOPE + "\n\n" + ACTION + "\n");
            Files.write(owned.resolve("source-request.json"), requestRaw, StandardOpenOption.CREATE_NEW);
            byte[] raw = NextflowFiles.bytes(input, MAX_INPUT_BYTES); var inputPin = new NextflowFiles.Pin(raw.length, NextflowFiles.sha(raw));
            Files.write(owned.resolve("prepared-neighbors.json"), raw, StandardOpenOption.CREATE_NEW);
            var prepared = parse(raw, MAX_INPUT_BYTES, 10_000_000);
            Set<String> allowed = Set.of("schema_version","source_profile","neighbor_graph","ranking_provenance","cell_ids","original_ranking_zero_based","principal_ids","cell_to_principal_zero_based","principal_edges_zero_based","principal_graph_provenance");
            require(allowed.containsAll(prepared.keySet()), "Unknown prepared-neighbor field");
            NextflowFiles.integer(prepared.get("schema_version"), 1, 1);
            require(PROFILE.equals(NextflowFiles.text(prepared.get("source_profile"))), "Declare the exact source profile; no edition substitution");
            String provenance = label(NextflowFiles.text(prepared.get("ranking_provenance")));
            List<String> ids = labels(prepared.get("cell_ids")); int[][] ranking = matrix(prepared.get("original_ranking_zero_based"));
            require(ranking.length == ids.size(), "Complete ranking and literal cell axis differ");
            String mode = NextflowFiles.text(prepared.get("neighbor_graph")); Monocle100Weights.Result result;
            if (mode.equals("knn")) {
                require(prepared.keySet().stream().noneMatch(name -> name.startsWith("principal_") || name.equals("cell_to_principal_zero_based")), "Principal fields require explicit principal_graph mode");
                result = Monocle100Weights.knn(ranking, maximum);
            } else {
                require(mode.equals("principal_graph"), "Choose knn or principal_graph explicitly");
                List<String> principalIds = labels(prepared.get("principal_ids")); label(NextflowFiles.text(prepared.get("principal_graph_provenance")));
                List<PrincipalEdge> edges = new ArrayList<>();
                for (int[] row : matrix(prepared.get("principal_edges_zero_based"))) {
                    require(row.length == 2, "Principal edges require exactly two endpoint indices"); edges.add(new PrincipalEdge(row[0], row[1]));
                }
                result = Monocle100Weights.principalGraph(ranking, vector(prepared.get("cell_to_principal_zero_based")), principalIds.size(), edges, maximum);
            }
            var report = new LinkedHashMap<String,Object>(); report.put("source_profile", PROFILE); report.put("weights_admitted", true); report.put("scientific_test_admitted", false);
            report.put("neighbor_graph", mode); report.put("ranking_provenance", provenance); report.put("cell_ids", ids); report.put("cells", result.cells());
            report.put("retained_ranking_columns", result.retainedRankingColumns()); report.put("neighbors_zero_based", result.neighbors()); report.put("isolated_rows", result.isolatedRows());
            report.put("self_links", result.selfLinks()); report.put("logical_operations", result.operations()); report.put("maximum_operations", maximum); report.put("scope", SCOPE);
            StringBuilder table = new StringBuilder("from_cell\tto_cell\tweight\n"); var arcs = new ArrayList<Map<String,Object>>();
            for (var arc : result.arcs()) {
                table.append(ids.get(arc.from())).append('\t').append(ids.get(arc.to())).append('\t').append(arc.weight()).append('\n');
                arcs.add(Map.of("from", arc.from(), "to", arc.to(), "weight", arc.weight()));
            }
            if (result.selfLinks() > 0) write(owned.resolve("SELF-LINK-WARNING.txt"), "Original tied-neighbor selection retains " + result.selfLinks() + " self-links. Preserve them. Current MORAN-LM refuses this graph; no statistical result is admitted here.\n");
            write(owned.resolve("weights.tsv"), table.toString()); NextflowFiles.writeJson(owned.resolve("weights.json"), arcs);
            verify(request, requestPin, 1_048_576); verify(input, inputPin, MAX_INPUT_BYTES);
            verify(owned.resolve("source-request.json"), requestPin, 1_048_576); verify(owned.resolve("prepared-neighbors.json"), inputPin, MAX_INPUT_BYTES);
            NextflowFiles.writeJson(owned.resolve("analysis.json"), report);
            var manifest = new LinkedHashMap<String,Object>(); manifest.put("request", Map.of("bytes", requestPin.bytes(), "sha256", requestPin.sha256()));
            manifest.put("input", Map.of("bytes", inputPin.bytes(), "sha256", inputPin.sha256()));
            var files = new LinkedHashMap<String,Object>();
            try (var paths = Files.list(owned)) {
                for (Path path : paths.sorted().toList()) { var pin = NextflowFiles.pin(path, MAX_INPUT_BYTES * 2L, () -> { }); files.put(path.getFileName().toString(), Map.of("bytes", pin.bytes(), "sha256", pin.sha256())); }
            }
            manifest.put("files", files); NextflowFiles.writeJson(owned.resolve("manifest.json"), manifest);
            output.println("PASS native Monocle1.0.0 weight graph; " + result.cells() + " cells, " + result.arcs().size() + " directed links. " + ACTION); output.flush();
            require(!output.checkError(), "Console recording failed; no completed weight result admitted");
            verify(request, requestPin, 1_048_576); verify(input, inputPin, MAX_INPUT_BYTES);
            for (var item : files.entrySet()) {
                var wanted = NextflowFiles.object(item.getValue());
                verify(owned.resolve(item.getKey()), new NextflowFiles.Pin(NextflowFiles.integer(wanted.get("bytes"), 0, MAX_INPUT_BYTES * 2L), NextflowFiles.text(wanted.get("sha256"))), MAX_INPUT_BYTES * 2L);
            }
            NextflowFiles.writeJson(owned.resolve("COMPLETED.json"), Map.of("status","PASS_NATIVE_MONOCLE100_WEIGHTS","weights_admitted",true,"scientific_test_admitted",false));
            return 0;
        } catch (Exception failure) {
            int code = failure instanceof NoSuchFileException || failure instanceof AccessDeniedException ? 3 : 2;
            String status = code == 3 ? "SKIP3_DEPENDENT_WEIGHT_PREPARATION_SKIPPED" : "FAIL2_NO_COMPLETE_WEIGHT_GRAPH_ADMITTED";
            if (owned != null) {
                try {
                    if (Files.exists(owned.resolve("analysis.json"))) Files.move(owned.resolve("analysis.json"), owned.resolve("unadmitted-analysis.json"));
                    NextflowFiles.writeJson(owned.resolve("analysis.json"), Map.of("status",status,"weights_admitted",false,"scientific_test_admitted",false,"cause",failure.toString()));
                    write(owned.resolve("FAILURE.txt"), failure + "\n" + ACTION + "\n");
                } catch (IOException recordingFailure) { error.println("FAIL2 could not finish failure recording: " + recordingFailure); }
            }
            error.println(status + ": " + failure + "\n" + ACTION); error.flush(); return code;
        }
    }
    private static Map<String,Object> parse(byte[] raw, int bytes, int nodes) throws IOException {
        var parsed = NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(raw), bytes, nodes));
        var result = new LinkedHashMap<String,Object>();
        for (var row : parsed.entrySet()) result.put(NextflowFiles.text(row.getKey()), row.getValue());
        return result;
    }
    private static int[] vector(Object value) throws IOException {
        var raw = NextflowFiles.list(value); int[] result = new int[raw.size()];
        for (int index = 0; index < result.length; index++) result[index] = (int) NextflowFiles.integer(raw.get(index), 0, 1_000_000);
        return result;
    }
    private static int[][] matrix(Object value) throws IOException {
        var raw = NextflowFiles.list(value); int[][] result = new int[raw.size()][];
        for (int index = 0; index < result.length; index++) result[index] = vector(raw.get(index));
        return result;
    }
    private static List<String> labels(Object value) throws IOException {
        var result = new ArrayList<String>(); Set<String> seen = new HashSet<>();
        for (String id : NextflowFiles.strings(value)) { label(id); require(seen.add(id), "Duplicate axis label"); result.add(id); }
        require(!result.isEmpty(), "Complete axis must be nonempty"); return List.copyOf(result);
    }
    private static String label(String value) throws IOException { require(!value.isBlank() && value.length() <= 32768 && value.chars().noneMatch(c -> c < 32 || c == 127), "Use literal nonblank labels/provenance without control characters"); return value; }
    private static Path resolve(Path parent, Object value) throws IOException { Path path = Path.of(label(NextflowFiles.text(value))); return (path.isAbsolute() ? path : parent.resolve(path)).toAbsolutePath().normalize(); }
    private static Path regular(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new NoSuchFileException(path.toString());
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Require a regular unlinked input file"); return path.toRealPath();
    }
    private static Path destination(Path absolute, List<Path> inputs) throws IOException {
        require(!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS), "Preserve existing output; choose NEW output"); Path parent = absolute.getParent(); require(parent != null, "Output needs a parent");
        while (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) parent = parent.getParent(); Path resolved = parent.toRealPath().resolve(parent.relativize(absolute)).normalize();
        for (Path input : inputs) require(!resolved.startsWith(input) && !input.startsWith(resolved), "Output overlaps protected input"); return resolved;
    }
    private static void verify(Path path, NextflowFiles.Pin wanted, long capacity) throws IOException { require(NextflowFiles.pin(path, capacity, () -> { }).equals(wanted), "Original input changed: " + path); }
    private static void write(Path path, String value) throws IOException { Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
    private static void require(boolean ok, String message) throws IOException { if (!ok) throw new IOException(message); }
}
