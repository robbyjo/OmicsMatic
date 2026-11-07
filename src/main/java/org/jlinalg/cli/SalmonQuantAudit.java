/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.SalmonInferentialReplicates;
import org.jlinalg.sequence.SalmonQuantification;

/** Complete original output contract only; process, protected inputs and finalization remain separate gates. */
final class SalmonQuantAudit {
    private SalmonQuantAudit() { }
    record Specification(String version, boolean alignmentMode, boolean paired, String requestedLibrary, SalmonQuantRecipe.Optimizer optimizer,
                         boolean sequenceBias, boolean gcBias, int bootstraps, long processedFragments, long sourceDecoys,
                         List<SalmonQuantification.Target> targets, Map<String, Object> command, Map<?, ?> index,
                         int metadataBytes, long tableCharacters, int lineCharacters, long bootstrapValues, long retainedBytes) {
        Specification {
            targets = List.copyOf(targets); command = Map.copyOf(command); if (index != null) index = Map.copyOf(index);
            if (!Set.of("1.0.0", "1.1.0", "1.5.2", "1.8.0", "1.9.0").contains(version) || targets.isEmpty() || metadataBytes < 1 || tableCharacters < 1 || lineCharacters < 1
                || bootstrapValues < 1 || retainedBytes < 262144 || bootstraps < 0 || bootstraps > 1000 || processedFragments < 1 || processedFragments > 9007199254740991L
                || sourceDecoys < 0 || alignmentMode != (index == null)) throw new IllegalArgumentException("Invalid complete Salmon output specification");
        }
    }
    record Result(SalmonQuantification.Table table, SalmonQuantification.Audit arithmetic, SalmonInferentialReplicates.Summary replicates, Map<String, Object> document) { }
    /** Reconstructs original cmd_info names from the complete typed original argv, without reading producer paths. */
    static Map<String, Object> command(List<String> argv, String version) throws IOException {
        require(argv.size() >= 3 && argv.get(1).equals("--no-version-check") && argv.get(2).equals("quant"), "Expected complete original Salmon quantification argv");
        Map<String, String> values = Map.ofEntries(Map.entry("-i", "index"), Map.entry("-t", "targets"), Map.entry("-l", "libType"), Map.entry("-1", "mates1"), Map.entry("-2", "mates2"),
            Map.entry("-r", "unmatedReads"), Map.entry("-a", "alignments"), Map.entry("--fldMean", "fldMean"), Map.entry("--fldSD", "fldSD"), Map.entry("--numBootstraps", "numBootstraps"),
            Map.entry("--minAssignedFrags", "minAssignedFrags"), Map.entry("-p", "threads"), Map.entry("-o", "output"));
        Set<String> flags = Set.of("--validateMappings", "--useEM", "--useVBOpt", "--seqBias", "--gcBias", "--posBias", "--gencode");
        var result = new LinkedHashMap<String, Object>(); result.put("salmon_version", version); result.put("auxDir", "aux_info");
        for (int i = 3; i < argv.size(); i++) {
            String flag = argv.get(i), key = values.get(flag); Object value;
            if (key != null) { require(++i < argv.size(), "Missing typed original Salmon argument value"); value = argv.get(i); }
            else { require(flags.contains(flag), "Unsupported original Salmon argv option; no opaque flags are admitted"); key = flag.substring(2); value = List.of(); }
            require(result.putIfAbsent(key, value) == null, "Repeated typed original Salmon quantification option");
        }
        return Map.copyOf(result);
    }
    static Result audit(Path output, Specification expected, Runnable check) throws IOException {
        check.run(); long retained = Math.addExact(262144, Math.multiplyExact(8192L, expected.targets().size()));
        for (var target : expected.targets()) retained = Math.addExact(retained, Math.multiplyExact(16L, target.name().length()));
        require(retained <= expected.retainedBytes(), "Complete Salmon output-table/interval metadata estimate exceeds capacity; no successful prefix is admitted");
        Map<?, ?> meta = SalmonMetadata.read(output.resolve("aux_info/meta_info.json"), expected.metadataBytes(), 1000000);
        Map<?, ?> command = SalmonMetadata.read(output.resolve("cmd_info.json"), expected.metadataBytes(), 1000000);
        require(expected.version().equals(meta.get("salmon_version")) && List.of().equals(meta.get("quant_errors")) && "quant".equals(meta.get("call")), "Original Salmon output edition/call/errors contract differs");
        require(expected.command().equals(command), "Complete original Salmon quantification command metadata differs from the protected request");
        require(expected.requestedLibrary().equals(expected.command().get("libType")) && expected.command().containsKey(expected.optimizer() == SalmonQuantRecipe.Optimizer.EM ? "useEM" : "useVBOpt")
            && !expected.command().containsKey(expected.optimizer() == SalmonQuantRecipe.Optimizer.EM ? "useVBOpt" : "useEM")
            && expected.sequenceBias() == expected.command().containsKey("seqBias") && expected.gcBias() == expected.command().containsKey("gcBias"), "Salmon protected option/model specification is internally inconsistent");
        require(expected.bootstraps() > 0 ? Integer.toString(expected.bootstraps()).equals(expected.command().get("numBootstraps"))
            : !expected.command().containsKey("numBootstraps") || "0".equals(expected.command().get("numBootstraps")), "Salmon protected bootstrap specification is internally inconsistent");
        require((expected.alignmentMode() ? "alignment" : "mapping").equals(meta.get("mapping_type")), "Original Salmon output mapping/alignment mode differs");
        require((expected.optimizer() == SalmonQuantRecipe.Optimizer.EM ? "em" : "vb").equals(meta.get("opt_type")), "Original Salmon optimizer differs");
        require((expected.bootstraps() > 0 ? "bootstrap" : "none").equals(meta.get("samp_type")) && integer(meta, "num_bootstraps") == expected.bootstraps(), "Original Salmon bootstrap request differs");
        require(integer(meta, "num_libraries") == 1 && integer(meta, "num_processed") == expected.processedFragments() && integer(meta, "num_valid_targets") == expected.targets().size()
            && integer(meta, "num_decoy_targets") == expected.sourceDecoys(), "Complete original Salmon library/read/reference/decoy counters differ from protected populations");
        require(Boolean.valueOf(expected.sequenceBias()).equals(meta.get("seq_bias_correct")) && Boolean.valueOf(expected.gcBias()).equals(meta.get("gc_bias_correct")), "Original Salmon reported sequence/GC bias flags differ");
        List<String> libraries = NextflowFiles.strings(meta.get("library_types")); require(libraries.size() == 1, "Original Salmon reports an unexpected library population"); String library = libraries.get(0);
        require((expected.paired() ? Set.of("IU", "ISF", "ISR", "OU", "OSF", "OSR", "MU", "MSF", "MSR") : Set.of("U", "SF", "SR")).contains(library)
            && (expected.requestedLibrary().equals("A") || expected.requestedLibrary().equals(library)), "Original Salmon inferred/reported library is incompatible with the declared exact layout/code");
        var unavailable = new ArrayList<String>();
        for (String[] pair : List.of(new String[]{"SeqHash", "index_seq_hash"}, new String[]{"NameHash", "index_name_hash"}, new String[]{"SeqHash512", "index_seq_hash512"},
            new String[]{"NameHash512", "index_name_hash512"}, new String[]{"DecoySeqHash", "index_decoy_seq_hash"}, new String[]{"DecoyNameHash", "index_decoy_name_hash"})) {
            if (expected.alignmentMode()) require("".equals(meta.get(pair[1])), "Original alignment mode unexpectedly claims a mapping-index hash link");
            else {
                String hash = NextflowFiles.text(expected.index().get(pair[0])); require(hash.matches(pair[0].endsWith("512") ? "[0-9a-f]{128}" : "[0-9a-f]{64}"), "Invalid complete protected Salmon index hash");
                if (expected.version().equals("1.0.0") && pair[0].endsWith("512")) { require("".equals(meta.get(pair[1])), "Original1.0.0 absent SHA512 link changed"); unavailable.add(pair[1]); }
                else require(hash.equals(meta.get(pair[1])), "Original Salmon quantification/index hash link differs");
            }
        }
        long mapped = integer(meta, "num_mapped"); require(mapped <= expected.processedFragments(), "Original Salmon mapped fragments exceed the complete protected input population");
        long assigned; String massSource;
        if (expected.alignmentMode()) {
            assigned = mapped; massSource = "independent-original-alignment-meta-num_mapped-counter";
            require(!Files.exists(output.resolve("lib_format_counts.json"), LinkOption.NOFOLLOW_LINKS), "Unexpected mapping-format counter file in original alignment output family");
        } else {
            Map<?, ?> format = SalmonMetadata.read(output.resolve("lib_format_counts.json"), expected.metadataBytes(), 1000000);
            require(library.equals(format.get("expected_format")), "Original Salmon library-format population differs from reported library");
            assigned = integer(format, "num_assigned_fragments"); massSource = "independent-original-lib_format_counts-num_assigned_fragments-counter";
        }
        require(assigned > 0 && assigned <= mapped, "Original Salmon assigned-fragment mass is empty or exceeds the separately reported mapped counter");
        SalmonQuantification.Table table;
        try (var input = Files.newInputStream(output.resolve("quant.sf"), LinkOption.NOFOLLOW_LINKS);
             var text = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            table = SalmonQuantification.read(text, expected.targets(), 3, expected.tableCharacters(), expected.lineCharacters(), check);
        }
        var arithmetic = SalmonQuantification.audit(table, assigned, check); SalmonInferentialReplicates.Summary replicates = null;
        Path bootstrap = output.resolve("aux_info/bootstrap");
        if (expected.bootstraps() > 0) {
            try (var names = Files.newInputStream(bootstrap.resolve("names.tsv.gz"), LinkOption.NOFOLLOW_LINKS); var counts = Files.newInputStream(bootstrap.resolve("bootstraps.gz"), LinkOption.NOFOLLOW_LINKS)) {
                replicates = SalmonInferentialReplicates.read(names, counts, expected.targets().stream().map(SalmonQuantification.Target::name).toList(), expected.bootstraps(), expected.bootstrapValues(), (r, t, n, v) -> { }, check);
            }
        } else require(!Files.exists(bootstrap, LinkOption.NOFOLLOW_LINKS), "Unrequested Salmon inferential output family must not be admitted as zero-bootstrap output");
        var document = new LinkedHashMap<String, Object>(); document.put("status", "COMPLETE_ORIGINAL_OUTPUT_CONTRACT_ONLY"); document.put("version", expected.version()); document.put("input_mode", expected.alignmentMode() ? "alignment" : "mapping");
        document.put("complete_targets", table.rows().size()); document.put("processed_fragments", expected.processedFragments()); document.put("mapped_fragments", mapped); document.put("assigned_fragments", assigned); document.put("assigned_mass_source", massSource);
        document.put("requested_library_type", expected.requestedLibrary()); document.put("original_reported_library_type", library); document.put("source_decoys", expected.sourceDecoys());
        document.put("printed_tpm_sum", arithmetic.printedTpmSum()); document.put("printed_count_sum", arithmetic.printedCountSum()); document.put("rows_requiring_printed_rounding", arithmetic.rowsRequiringRounding());
        document.put("bootstraps", expected.bootstraps()); document.put("complete_bootstrap_values", replicates == null ? 0 : replicates.values()); document.put("estimated_retained_bytes", retained);
        document.put("unavailable_original_metadata_bindings", unavailable); document.put("position_bias_has_independent_meta_flag", meta.containsKey("pos_bias_correct"));
        document.put("calibrated_bias_model_certified", false); document.put("original_process_completion_certified_by_this_reader", false); document.put("user_analysis_admitted", false);
        check.run(); return new Result(table, arithmetic, replicates, Map.copyOf(document));
    }
    private static long integer(Map<?, ?> map, String name) throws IOException { return SalmonMetadata.integer(map.get(name), 9007199254740991L); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
