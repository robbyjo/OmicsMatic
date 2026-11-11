/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlinalg.sequence.SalmonInferentialReplicates;
import org.jlinalg.sequence.SalmonQuantification;

/** Exact synthetic health audit for a freshly supervised original runtime, never a user fit. */
final class SalmonHealth {
    private SalmonHealth() { }
    record Receipt(String label, SalmonQuantification.Table table, SalmonQuantification.Audit arithmetic,
                   SalmonInferentialReplicates.Summary replicates, List<String> libraryTypes, List<String> unavailableBindings) {
        Receipt { libraryTypes = List.copyOf(libraryTypes); unavailableBindings = List.copyOf(unavailableBindings); }
        Map<String, Object> document() {
            return Map.of("label", label, "complete_targets", table.rows().size(), "complete_bootstrap_values", replicates.values(),
                "printed_tpm_sum", arithmetic.printedTpmSum(), "printed_count_sum", arithmetic.printedCountSum(),
                "rows_requiring_printed_rounding", arithmetic.rowsRequiringRounding(), "reported_library_types", libraryTypes,
                "unavailable_metadata_bindings", unavailableBindings, "user_analysis_admitted", false);
        }
    }
    static Receipt audit(Path root, String version, Map<?, ?> spec, List<SalmonQuantification.Target> targets, Runnable check) throws IOException {
        return audit(root, root.toString(), version, spec, targets, check);
    }
    static Receipt audit(Path root, String commandRoot, String version, Map<?, ?> spec, List<SalmonQuantification.Target> targets, Runnable check) throws IOException {
        String label = text(spec.get("label")), relative = NextflowFiles.member(text(spec.get("output"))); Path folder = root.resolve(relative);
        Map<?, ?> expected = object(spec.get("expected")), meta = json(folder.resolve("aux_info/meta_info.json")), command = json(folder.resolve("cmd_info.json"));
        equal(meta.get("salmon_version"), version, "health version"); equal(meta.get("quant_errors"), List.of(), "health errors");
        equal(meta.get("samp_type"), "bootstrap", "health inference kind"); equal(meta.get("opt_type"), expected.get("optimizer"), "health optimizer");
        for (String[] pair : List.of(new String[]{"num_processed", "processed_fragments"}, new String[]{"num_mapped", "mapped_fragments"},
            new String[]{"num_valid_targets", "valid_targets"}, new String[]{"num_decoy_targets", "decoy_targets"}, new String[]{"num_bootstraps", "bootstraps"}))
            equal(meta.get(pair[0]), expected.get(pair[1]), "complete health counter " + pair[0]);
        equal(meta.get("num_libraries"), 1L, "health library population"); equal(meta.get("mapping_type"), "mapping", "health input mode");
        equal(meta.get("seq_bias_correct"), true, "sequence bias"); equal(meta.get("gc_bias_correct"), true, "GC bias");
        List<String> libraryTypes = NextflowFiles.strings(meta.get("library_types"));
        require(libraryTypes.equals(List.of("ISR")) || label.equals("health-em") && libraryTypes.equals(List.of("IU")), "Unexpected original health library classification");
        int bootstraps = (int) NextflowFiles.integer(expected.get("bootstraps"), 1, 1000);
        var desired = new LinkedHashMap<String, Object>(); desired.put("salmon_version", version); desired.put("index", commandRoot + "/health/index");
        desired.put("libType", label.equals("health-em") ? "A" : "ISR"); desired.put("mates1", commandRoot + "/inputs/reads_1.fastq"); desired.put("mates2", commandRoot + "/inputs/reads_2.fastq");
        for (String flag : List.of(label.equals("health-em") ? "useEM" : "useVBOpt", "seqBias", "gcBias", "posBias")) desired.put(flag, List.of());
        desired.put("numBootstraps", Integer.toString(bootstraps)); desired.put("threads", "2"); desired.put("output", commandRoot + "/" + relative); desired.put("auxDir", "aux_info");
        equal(command, desired, "complete original health options");
        Map<?, ?> index = json(root.resolve("health/index/info.json")), format = json(folder.resolve("lib_format_counts.json"));
        equal(index.get("k"), 31L, "health index k"); equal(index.get("num_decoys"), 1L, "health decoy population"); equal(index.get("first_decoy_index"), 40L, "health decoy boundary");
        var unavailable = new ArrayList<String>();
        for (String[] pair : List.of(new String[]{"SeqHash", "index_seq_hash"}, new String[]{"NameHash", "index_name_hash"},
            new String[]{"SeqHash512", "index_seq_hash512"}, new String[]{"NameHash512", "index_name_hash512"},
            new String[]{"DecoySeqHash", "index_decoy_seq_hash"}, new String[]{"DecoyNameHash", "index_decoy_name_hash"})) {
            String value = text(index.get(pair[0])); require(value.matches(pair[0].endsWith("512") ? "[a-f0-9]{128}" : "[a-f0-9]{64}"), "Invalid original health index hash");
            if (version.equals("1.0.0") && pair[0].endsWith("512")) { equal(meta.get(pair[1]), "", "original 1.0.0 unavailable SHA-512 link"); unavailable.add(pair[1]); }
            else equal(meta.get(pair[1]), value, "original health index/quantification hash link");
        }
        equal(format.get("expected_format"), libraryTypes.get(0), "expected library class");
        equal(format.get("num_assigned_fragments"), 8520L, "independent assigned-fragment health mass"); equal(format.get("ISR"), 8520L, "observed health orientation");
        var table = SalmonQuantification.read(new StringReader(utf8(folder.resolve("quant.sf"))), targets, 3, 8388608, 8192, check);
        var arithmetic = SalmonQuantification.audit(table, 8520, check); Path samples = folder.resolve("aux_info/bootstrap");
        SalmonInferentialReplicates.Summary replicates;
        try (var names = Files.newInputStream(samples.resolve("names.tsv.gz"), LinkOption.NOFOLLOW_LINKS);
             var counts = Files.newInputStream(samples.resolve("bootstraps.gz"), LinkOption.NOFOLLOW_LINKS)) {
            replicates = SalmonInferentialReplicates.read(names, counts, targets.stream().map(SalmonQuantification.Target::name).toList(), bootstraps, 6000,
                (sample, target, name, count) -> { }, check);
        }
        return new Receipt(label, table, arithmetic, replicates, libraryTypes, unavailable);
    }
    static Map<?, ?> json(Path path) throws IOException { return object(SimpleJson.parse(utf8(path))); }
    static String utf8(Path path) throws IOException { return NextflowFiles.utf8(NextflowFiles.bytes(path, 8388608)); }
    private static Map<?, ?> object(Object value) throws IOException { return NextflowFiles.object(value); }
    private static String text(Object value) throws IOException { return NextflowFiles.text(value); }
    private static void equal(Object actual, Object expected, String name) throws IOException { require(Objects.equals(actual, expected), "Salmon " + name + " differs"); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
