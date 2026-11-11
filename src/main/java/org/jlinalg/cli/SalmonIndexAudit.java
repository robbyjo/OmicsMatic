/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.sequence.SalmonIndexReferences;

/** Full input/reference/source-decoy ledger; no graph-equivalence or expression certificate. */
final class SalmonIndexAudit {
    private SalmonIndexAudit() { }
    record Result(SalmonIndexReferences.Summary tables, SalmonReferences.Ledger ledger, Map<String, Object> summary) { }
    static Result read(SalmonIndexRecipe.Request request, SalmonReferences.Context inputs, Path index, Runnable check) throws IOException {
        var limits = request.limits();
        Map<?, ?> info = SalmonMetadata.read(index.resolve("info.json"), limits.metadataBytes(), limits.metadataNodes());
        Map<?, ?> version = SalmonMetadata.read(index.resolve("versionInfo.json"), limits.metadataBytes(), limits.metadataNodes());
        require(SalmonMetadata.integer(info.get("index_version"), 100) == 4 && "dense".equals(info.get("sampling_type")) && Boolean.TRUE.equals(info.get("have_ref_seq")), "Original Salmon dense reference-containing index contract differs");
        require(SalmonMetadata.integer(info.get("k"), 31) == request.k(), "Original Salmon index k differs from the captured recipe");
        if (info.containsKey("keep_duplicates")) require(Boolean.valueOf(request.keepDuplicates()).equals(info.get("keep_duplicates")), "Original Salmon index duplicate policy differs from the captured recipe");
        require(SalmonMetadata.integer(version.get("indexVersion"), 100) == 5 && SalmonMetadata.integer(version.get("indexType"), 100) == 2
            && SalmonMetadata.integer(version.get("auxKmerLength"), 31) == request.k() && Boolean.FALSE.equals(version.get("hasAuxIndex")), "Original Salmon index version/type/k contract differs");
        if (version.containsKey("salmonVersion")) require(request.version().equals(version.get("salmonVersion")), "Original Salmon index edition differs");
        for (String field : new String[]{"SeqHash", "NameHash", "DecoySeqHash", "DecoyNameHash", "SeqHash512", "NameHash512"})
            require(info.get(field) instanceof String value && value.matches(field.endsWith("512") ? "[0-9a-f]{128}" : "[0-9a-f]{64}"), "Missing/malformed original Salmon index hash: " + field);
        SalmonIndexReferences.Summary tables;
        try (var table = Files.newInputStream(index.resolve("ctable.bin")); var complete = Files.newInputStream(index.resolve("complete_ref_lens.bin")); var normalized = Files.newInputStream(index.resolve("reflengths.bin"))) {
            tables = SalmonIndexReferences.read(table, complete, normalized, limits.tables(), (o, c, f, n, p, fw) -> { }, check);
        }
        var ledger = SalmonReferences.bind(inputs, tables, request.k(), SalmonMetadata.integer(info.get("num_decoys"), limits.tables().references()), SalmonMetadata.unsigned(info.get("first_decoy_index")), check);
        var summary = new LinkedHashMap<String, Object>(); summary.put("schema_version", 1); summary.put("status", "COMPLETE_INDEX_REFERENCE_LEDGER_ONLY");
        summary.put("profile", request.profile()); summary.put("k", request.k()); summary.put("raw_references", inputs.references().size()); summary.put("raw_residues", inputs.census().residues());
        summary.put("index_metadata_reports_duplicate_policy", info.containsKey("keep_duplicates")); summary.put("index_metadata_reports_salmon_edition", version.containsKey("salmonVersion"));
        summary.put("retained_references", ledger.retained()); summary.put("not_retained_references", ledger.notRetained()); summary.put("contig_references", tables.contigReferences()); summary.put("positions", tables.positions());
        summary.put("declared_decoys", ledger.decoys().indexDecoys()); summary.put("original_runtime_decoys", ledger.decoys().originalRuntimeDecoys());
        summary.put("declared_source_disagreements", ledger.decoys().disagreements().size()); summary.put("declared_and_original_runtime_populations_agree", ledger.decoys().matchesDeclaredPopulation());
        summary.put("active_decoy_boundary_has_contig_id", ledger.decoys().activeBoundaryHasContigId()); summary.put("first_decoy_index_uint64", ledger.decoys().firstGraphDecoy().toString());
        summary.put("ctable", receipt(tables.contigTable())); summary.put("original_lengths", receipt(tables.originalLengths())); summary.put("normalized_lengths", receipt(tables.normalizedLengths()));
        summary.put("graph_mathematical_equivalence_certified", false); summary.put("expression_analysis_admitted", false);
        check.run(); return new Result(tables, ledger, Map.copyOf(summary));
    }
    /** Streams every raw input record, including all records not retained by the original index. */
    static NextflowFiles.Pin ledger(Result audit, OutputStream output, long maximumBytes, Runnable check) throws IOException {
        MessageDigest digest; try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        long size = 0;
        // Caller owns the output; the digest wrapper is deliberately not closed here.
        var encoded = new DigestOutputStream(output, digest);
        for (var row : audit.ledger().inputs()) {
            check.run(); var value = new LinkedHashMap<String, Object>(); value.put("raw_ordinal", row.input().raw().ordinal()); value.put("raw_id", row.input().raw().id());
            value.put("index_id", row.input().indexName()); value.put("original_length", row.input().raw().length()); value.put("raw_sequence_sha256", row.input().raw().sequenceSha256());
            value.put("declared_decoy", row.input().declaredDecoy()); value.put("status", row.status());
            if (row.retained() != null) {
                var retained = row.retained(); var assignment = audit.ledger().decoys().assignments().get(retained.ordinal());
                value.put("full_index_ordinal", retained.ordinal()); value.put("normalized_length", retained.normalizedLength()); value.put("contig_reference_id", retained.contigReferenceId());
                value.put("original_runtime_decoy", assignment.originalRuntimeDecoy()); value.put("short_reference", assignment.shortReference());
            }
            byte[] bytes = (NextflowJson.json(value) + "\n").getBytes(StandardCharsets.UTF_8); size = Math.addExact(size, bytes.length);
            require(size <= maximumBytes, "Complete Salmon reference-ledger byte capacity exceeded"); encoded.write(bytes);
        }
        encoded.flush(); check.run(); return new NextflowFiles.Pin(size, HexFormat.of().formatHex(digest.digest()));
    }
    private static Map<String, Object> receipt(SalmonIndexReferences.FileReceipt receipt) { return Map.of("bytes", receipt.bytes(), "sha256", receipt.sha256()); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
