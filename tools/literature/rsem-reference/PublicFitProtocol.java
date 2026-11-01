/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual public main-command controls against independently matched fresh original scientific files. */
public final class PublicFitProtocol {
    private PublicFitProtocol() { }
    private static String hash(Path p) throws IOException { return RepeatInputs.hash(p, 1073741824, () -> { }); }
    private static void require(boolean valid, String message) throws IOException { if (!valid) throw new IOException(message); }
    private static void write(Path p, Object value) throws IOException { Files.write(p, RsemJson.bytes(value), StandardOpenOption.CREATE_NEW); }
    /** @param args release, completed original baseline, baseline report SHA256, source build, source receipt SHA256, library, precreated NEW root, tested candidate SHA256
     * @throws Exception public command, source identity, complete scientific output or negative-control disagreement */
    public static void main(String[] args) throws Exception {
        if (args.length != 8) throw new IllegalArgumentException("VERSION BASELINE_ROOT BASELINE_REPORT_SHA BUILD_HOME BUILD_RECEIPT_SHA LIBRARY NEW_ROOT CANDIDATE_SHA");
        String version = args[0]; Path baseline = Path.of(args[1]), root = Path.of(args[6]);
        require(root.toRealPath().equals(root), "Require precreated canonical profile root");
        require(hash(Path.of(JLinAlgCli.class.getProtectionDomain().getCodeSource().getLocation().toURI())).equals(args[7]), "Public candidate differs from the exact-package test candidate");
        require(hash(baseline.resolve("observed.json")).equals(args[2]), "Fresh original baseline report changed");
        var original = RepeatBundle.object(baseline.resolve("observed.json"), 16777216);
        require("passed-fresh-typed-versus-independent-original-fitting".equals(original.get("status")) && version.equals(original.get("version")) && args[4].equals(original.get("source_build_receipt_sha256")), "Unreviewed original baseline/source identity");
        var references = new LinkedHashMap<String,Map<?,?>>(); for (Object item : (List<?>)original.get("completed")) { var row = RsemRecipe.object(item); references.put((String)row.get("case"), row); }
        List<String> names = version.equals("1.3.3") ? List.of("paired-sam", "paired-bam", "paired-cram", "single-sam", "paired-ci", "tagged-unmapped") : version.equals("1.2.28") ? List.of("paired-sam", "single-fixed") : List.of("paired-sam");
        for (String name : names) Files.createDirectory(root.resolve(name));
        Files.createDirectory(root.resolve("wrong-receipt")); Files.createDirectory(root.resolve("missing-build"));
        var completed = new ArrayList<Map<String,Object>>(); long files = 0, cells = 0;
        for (String name : names) {
            Path parent = root.resolve(name), recipe = parent.resolve("fit.json"), output = parent.resolve("run");
            var request = request(version, name, baseline, args[3], args[4], args[5]); write(recipe, request);
            System.out.println(version + " actual public fitting " + name);
            int status = invoke(recipe, output, parent); require(status == 0, "Public fitting failed: " + name + "; inspect " + parent.resolve("cli.stderr"));
            var marker = RepeatBundle.object(output.resolve("COMPLETED.json"), 1048576); var report = RepeatBundle.object(output.resolve("fit/RESULT_ADMITTED.json"), 1048576);
            require(Boolean.TRUE.equals(marker.get("scientific_result_admitted")) && "COMPLETED".equals(RepeatBundle.object(output.resolve("analysis-status.json"), 1048576).get("status")), "Public workflow lacks a completed status");
            require(hash(output.resolve("fit/RESULT_ADMITTED.json")).equals(marker.get("result_sha256")) && args[4].equals(report.get("source_build_receipt_sha256")), "Public completion does not bind the actual fitted result/source build");
            var actual = new java.util.TreeMap<String,String>();
            for (var row : RepeatBundle.inventory(output.resolve("fit"), new RepeatBundle.Limits(268435456, 4096), true, () -> { }).hashes().entrySet()) if (row.getKey().startsWith("sample")) actual.put(row.getKey(), row.getValue());
            require(actual.equals(references.get(name).get("scientific_sha256")), "Complete public scientific population differs from fresh independent originals: " + name);
            require(Files.readString(output.resolve("WARNINGS.md")).contains("review") && Files.isRegularFile(output.resolve("input-audit/INPUT_AUDIT.json")), "Public workflow lacks parent warnings or complete input audit");
            cells += ((Number)report.get("numeric_cells")).longValue(); files += actual.size();
            completed.add(Map.of("case", name, "scientific_files", actual.size(), "scientific_sha256", actual, "numeric_cells", report.get("numeric_cells"), "completion_sha256", hash(output.resolve("COMPLETED.json")), "fitting_record_sha256", hash(output.resolve("fit/RESULT_ADMITTED.json")), "recipe_sha256", hash(recipe)));
        }
        var negative = new ArrayList<Map<String,Object>>();
        for (String name : List.of("wrong-receipt", "missing-build")) {
            Path parent = root.resolve(name), recipe = parent.resolve("fit.json"), output = parent.resolve("run");
            var request = request(version, "paired-sam", baseline, name.equals("missing-build") ? root.resolve("absent-build").toString() : args[3], name.equals("wrong-receipt") ? "0".repeat(64) : args[4], args[5]); write(recipe, request);
            int code = invoke(recipe, output, parent); require(code == (name.equals("missing-build") ? 3 : 2), "Unexpected public negative-control exit: " + name);
            require(!Files.exists(output.resolve("COMPLETED.json")) && !Files.exists(output.resolve("fit")) && Files.isRegularFile(output.resolve("WARNINGS.md")), "Invalid runtime reached fitting or lacks prominent warnings");
            negative.add(Map.of("case", name, "exit_code", code, "warning_sha256", hash(output.resolve("WARNINGS.md")), "status_sha256", hash(output.resolve("analysis-status.json"))));
        }
        require(hash(baseline.resolve("observed.json")).equals(args[2]), "Original baseline report changed during public controls");
        write(root.resolve("observed.json"), Map.of("status", "passed-actual-public-fitting-and-runtime-negative-controls", "version", version, "candidate_sha256", args[7], "original_baseline_sha256", args[2], "completed", completed, "negative", negative, "scientific_files", files, "numeric_cells", cells, "actual_public_main_command_exercised", true));
        System.out.println(version + " PASS " + completed.size() + " complete public fits,2 runtime controls");
    }
    private static LinkedHashMap<String,Object> request(String version, String name, Path baseline, String build, String receipt, String library) {
        boolean single = name.startsWith("single"); Path source = baseline.resolve(name + "/inputs"); var settings = new LinkedHashMap<String,Object>(); settings.put("seed", 1618); settings.put("worker_threads", 1); settings.put("strand", "NONE"); settings.put("posterior", name.equals("paired-ci") ? "INTERVAL" : "NONE"); settings.put("estimate_read_start", name.equals("paired-ci")); settings.put("append_names", false); if (single) settings.put("single_end_fragment", Map.of("mean", 200, "standard_deviation", name.equals("single-fixed") ? 0 : 20));
        var request = new LinkedHashMap<String,Object>(); request.put("schema_version", 1); request.put("source_version", version); request.put("build_home", build); request.put("build_receipt_sha256", receipt); request.put("library_directories", List.of(library)); request.put("reference_prefix", source.resolve("reference/reference")); request.put("alignment", source.resolve("alignment/reads.input")); if (name.endsWith("cram")) request.put("cram_reference", source.resolve("cram-reference/reference.fa")); request.put("read_type", single ? 1 : 3); request.put("read_type_tag", name.equals("tagged-unmapped") ? "XM" : ""); request.put("settings", settings); return request;
    }
    private static int invoke(Path recipe, Path output, Path parent) throws IOException {
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        int code = JLinAlgCli.run(new String[]{"rsem-tools", "--method", "fit", "--recipe", recipe.toString(), "--out", output.toString()}, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
        Files.write(parent.resolve("cli.stdout"), stdout.toByteArray(), StandardOpenOption.CREATE_NEW); Files.write(parent.resolve("cli.stderr"), stderr.toByteArray(), StandardOpenOption.CREATE_NEW); return code;
    }
}
