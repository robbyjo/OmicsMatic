/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.FastaIndexes;
import static org.jlinalg.cli.FollowupSupport.*;

/** Native FASTA indexing without an external executable or alteration of the source. */
final class FastaIndexCli {
    private FastaIndexCli() { }
    static int run(Map<String, String> options, PrintStream output, PrintStream error) throws Exception {
        for (String key : options.keySet()) if (!Set.of("method", "input", "out").contains(key)) throw new IllegalArgumentException("--" + key + " does not apply to native fasta-index");
        Path input = Path.of(required(options, "input")).toAbsolutePath().normalize(); int[] exit = {0};
        externalTransaction(options, "native-fasta-index", (directory, manifest) -> {
            try {
                String referenceHash = hash(input);
                var index = FastaIndexes.build(input); Path result = directory.resolve(input.getFileName() + ".fai"); index.write(result);
                if (!index.equals(new htsjdk.samtools.reference.FastaSequenceIndex(result))) throw new java.io.IOException("Native FASTA index serialization mismatch");
                if (!hash(input).equals(referenceHash)) throw new java.io.IOException("Reference FASTA changed during native indexing");
                manifest.put("reference_sha256", referenceHash); long bases = 0;
                for (var entry : index) bases = Math.addExact(bases, entry.getSize());
                manifest.put("analysis_status", "completed"); manifest.put("engine", "Native Java / HTSJDK 5.0.0");
                table(directory.resolve("results.tsv"), List.of("metric", "value"), List.of(new String[]{"sequences", Integer.toString(index.size())}, new String[]{"bases", Long.toString(bases)}, new String[]{"index_file", result.getFileName().toString()}));
            } catch (Exception failure) {
                exit[0] = 2; manifest.put("analysis_status", "failed"); manifest.put("error", String.valueOf(failure.getMessage()));
                Files.deleteIfExists(directory.resolve(input.getFileName() + ".fai"));
                Files.deleteIfExists(directory.resolve("results.tsv"));
                Files.writeString(directory.resolve("WARNINGS.md"), "# NATIVE FASTA INDEXING FAILED\n\n" + failure.getMessage()
                    + "\n\nRequired action: supply a valid uncompressed FASTA with nonempty sequences and stable line wrapping, then rerun to a new output directory. Compressed references require a separate .gzi-aware workflow. The source and any existing index were not changed.\n");
            }
            Files.writeString(directory.resolve("analysis-status.json"), "{\"status\":\"" + manifest.get("analysis_status") + "\"}\n");
        });
        if (exit[0] == 0) output.println("Native FASTA index completed: " + options.get("out"));
        else error.println("ANALYSIS FAILED: see " + Path.of(options.get("out"), "WARNINGS.md") + " for the cause and required action.");
        return exit[0];
    }
}
