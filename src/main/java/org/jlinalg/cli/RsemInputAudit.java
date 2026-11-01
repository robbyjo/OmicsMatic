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
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.jlinalg.sequence.RsemSamAlignments;

/** Bound original decoding and complete native projections; fitting/completion remain separate. */
final class RsemInputAudit {
    private RsemInputAudit() { }
    record Limits(RsemSamAlignments.Limits alignment, RsemCramReference.Limits cram, RsemProcess.Limits process,
                  long metadataBytes, long wholeWallMillis) {
        Limits {
            Objects.requireNonNull(alignment); Objects.requireNonNull(cram); Objects.requireNonNull(process);
            if (metadataBytes < 8192 || metadataBytes > 67108864 || wholeWallMillis < 1 || wholeWallMillis > 86400000
                    || process.workspaceBytes() < metadataBytes + 131072 || process.fileCount() < 32 || process.fileBytes() < 8192)
                throw new IllegalArgumentException("Require explicit complete native input, metadata, process and whole-operation capacities");
        }
        void heap() throws IOException {
            try {
                long estimate = Math.addExact(Math.multiplyExact(alignment.maxNameCharacters(), 4), Math.multiplyExact((long)alignment.maxDistinctNames(), 128));
                estimate = Math.addExact(estimate, Math.multiplyExact((long)alignment.maxHitsPerGroup(), 128));
                estimate = Math.addExact(estimate, Math.multiplyExact((long)alignment.maxReferences(), 256));
                estimate = Math.addExact(estimate, Math.multiplyExact((long)alignment.maxReadLength() + alignment.maxLineCharacters(), 8));
                estimate = Math.addExact(estimate, Math.multiplyExact((long)alignment.maxHistogramBins(), 64));
                estimate = Math.addExact(estimate, Math.multiplyExact(metadataBytes, 4) + 1048576);
                if (estimate > Runtime.getRuntime().maxMemory() / 4 * 3) throw new IOException("Declared complete alignment/metadata retained capacities exceed75%of this Java heap; use a reviewed larger -Xmx or smaller complete limits without discarding records");
            } catch (ArithmeticException invalid) { throw new IOException("Declared alignment retained capacities overflow the supported heap estimate", invalid); }
        }
    }
    record Context(Path home, RsemInputs.Prepared inputs, RsemRuntime.Context runtime, RsemSamAlignments.Summary summary,
                   RsemCramReference.Reference cram, RsemFileGraph.Snapshot files, Limits limits) {
        void verify(Runnable check) throws IOException {
            inputs.verify(check); runtime.verify(check); if (cram != null) cram.verify(check);
            files.verify(check); inputs.verify(check);
        }
        Path projection() { return home.resolve("native"); }
    }

    static Context inspect(RsemRuntime.Context runtime, RsemInputs.Prepared inputs, Path home, Limits limits, Runnable cancellation) throws IOException {
        if (!"Linux".equals(System.getProperty("os.name"))) throw new RsemProcess.UnavailableDependency("Original RSEM decoding requires Linux Java and a permitted exact runtime; run inside supported Linux/WSL or skip dependent fitting");
        Objects.requireNonNull(runtime); Objects.requireNonNull(inputs); Objects.requireNonNull(limits); limits.heap();
        Runnable check = deadline(limits.wholeWallMillis(), cancellation);
        String version = inputs.spec().options().release().name().substring(1).replace('_', '.');
        if (!version.equals(runtime.payload().source().version())) throw new IOException("Native alignment request and actual original source release differ");
        if (runtime.payload().sourceBuild() == null) throw new RsemProcess.UnavailableDependency("Original decoder lacks a verified source-build context. Supply permitted exact source setup; skip dependent fitting while unavailable.");
        for (Path path : List.of(home, inputs.home(), inputs.alignment(), inputs.referencePrefix())) RsemPayload.originalPath(path);
        if (home.getParent() == null || !home.getParent().toRealPath().equals(home.getParent())) throw new IOException("Input auditing requires a canonical NEW directory");
        for (Path other : List.of(inputs.home(), runtime.home(), runtime.payload().home()))
            if (home.startsWith(other) || other.startsWith(home)) throw new IOException("Scientific input, runtime and audit workspaces must be disjoint");
        inputs.verify(check); runtime.verify(check); check.run(); Files.createDirectory(home);
        try {
            RsemCramReference.Reference cram = null; RsemCramReference.Header header = null;
            try (var input = Files.newInputStream(inputs.alignment(), LinkOption.NOFOLLOW_LINKS)) {
                if (java.util.Arrays.equals(input.readNBytes(4), new byte[]{'C','R','A','M'})) cram = RsemCramReference.audit(inputs, limits.cram(), check);
            }
            var protectedCram = cram;
            RsemProcess.Check integrity = () -> { inputs.verify(check); runtime.verify(check); if (protectedCram != null) protectedCram.verify(check); inputs.verify(check); };
            if (cram != null) {
                var originalHeader = execute(runtime, inputs, home, "header", List.of(runtime.payload().home().resolve("bin/samtools-1.3/samtools").toString(), "view", "-H", inputs.alignment().toString()), limits, integrity, check);
                requireNoDiagnostic(originalHeader, "header preflight");
                header = RsemCramReference.header(originalHeader.stdout(), cram, check);
            }
            Path decoded = home.resolve("decode/canonical.sam"), metadata = home.resolve("decode/records.metadata");
            var command = new ArrayList<String>(List.of(runtime.payload().home().resolve("bin/omicsmatic-rsem-decode").toString(), inputs.alignment().toString(), decoded.toString(), metadata.toString()));
            if (cram != null) command.add(cram.fasta().toString());
            var decoder = execute(runtime, inputs, home, "decode", command, limits, integrity, check);
            requireNoDiagnostic(decoder, "original alignment decoding");
            var bundle = new RepeatBundle.Limits(limits.process().workspaceBytes(), limits.process().fileCount());
            var before = RepeatBundle.inventory(home, bundle, false, check); var source = RepeatBundle.inventory(inputs.home(), bundle, false, check);
            long reserve = limits.metadataBytes() + 65536;
            long available = limits.process().workspaceBytes() - before.bytes() - source.bytes() - reserve;
            int members = limits.process().fileCount() - before.members() - source.members() - 8;
            RsemSamAlignments.Summary summary;
            try (var projection = new RsemAlignmentProjection(home.resolve("native"), inputs.spec().options(), available, limits.process().fileBytes(), members, check)) {
                summary = project(decoded, metadata, inputs, limits.alignment(), projection, check);
                if (!summary.safeForFitting()) throw new IOException("Native alignment findings block fitting: " + summary.findings() + ". Read complete blocking record identities/details at " + projection.home().resolve("blocking-findings.jsonl") + "; resolve the named source conditions. No sorting, mate repair, hit removal or quality imputation is performed.");
                projection.finish(summary);
            }
            integrity.verify(); check.run();
            var record = new LinkedHashMap<String,Object>();
            record.put("schema_version", 1); record.put("status", "COMPLETE_BOUND_ORIGINAL_DECODING_AND_NATIVE_INPUT_AUDIT_ONLY");
            record.put("source_version", version); record.put("runtime_binding_sha256", runtime.bindingSha256());
            record.put("source_build_receipt_sha256", runtime.payload().sourceBuild().recordSha256());
            record.put("payload_manifest_sha256", runtime.payload().manifestSha256()); record.put("protected_inputs", inputs.home());
            record.put("protected_input_record_sha256", RepeatInputs.hash(inputs.home().resolve("inputs.json"), inputs.limits().metadataBytes(), check));
            record.put("original_binary_fitting_input", inputs.alignment()); record.put("canonical_SAM_is_not_replacement_fitting_input", true);
            record.put("source_to_build_provenance_verified", true); record.put("native_alignment_summary", summary(summary));
            record.put("CRAM_header", header); record.put("explicit_CRAM_reference_used", cram != null);
            if (cram != null) record.put("CRAM_reference_sequences", new ArrayList<>(cram.sequences().values()));
            record.put("expected_original_parser_files", RepeatBundle.inventory(home.resolve("native"), bundle, true, check).hashes());
            record.put("scientific_runtime_admitted", false); record.put("fitted_result_admitted", false); record.put("public_fitting_workflow_available", false);
            record.put("scope", "Actual bound original header/decoder processes, exact protected binary input, complete source-linked per-record metadata and native parser/read/hit/count projections. This context is not a fitted result, public fitting workflow, signature or OS sandbox. Actual fitting and complete output admission remain required.");
            byte[] raw = RsemJson.bytes(record);
            if (raw.length > limits.metadataBytes() || raw.length > limits.process().fileBytes()) throw new IOException("Complete native input audit metadata exceeds capacity");
            capacity(home, inputs, limits, raw.length + 8192, 3, check);
            Files.write(home.resolve("INPUT_AUDIT.json"), raw, StandardOpenOption.CREATE_NEW);
            Files.writeString(home.resolve("WARNINGS.md"), "# Input audit complete; fitting remains pending\n\nThe original binary input is retained. Canonical SAM is an audit artifact, not replacement fitting input.\n"
                    + (header == null || header.warnings().isEmpty() ? "" : "CRAM REFERENCE WARNINGS: " + header.warnings().size() + ". Read all retained header warnings and target identities in INPUT_AUDIT.json. Missing M5 requires reference-provenance review; any header/decoder length disagreement must be resolved before fitting.\n")
                    + "\nRequired action: execute a matched typed fitting request, compare all original parser projections and audit complete model/results and output membership before admitting expression results.\n", StandardOpenOption.CREATE_NEW);
            capacity(home, inputs, limits, 4096, 1, check);
            RepeatBundle.inventory(home, bundle, false, check);
            var graph = RsemFileGraph.capture(List.of(home), List.of(), graphLimits(limits), null, check);
            var result = new Context(home, inputs, runtime, summary, cram, graph, limits); result.verify(check); return result;
        } catch (IOException | RuntimeException failure) {
            try {
                if (Files.exists(home.resolve("INPUT_AUDIT.json"), LinkOption.NOFOLLOW_LINKS)) Files.move(home.resolve("INPUT_AUDIT.json"), home.resolve("INPUT_AUDIT_UNADMITTED.json"));
                String message = failure.toString(); if (message.length() > 2048) message = message.substring(0, 2048);
                String diagnostic = "RSEM INPUT AUDIT FAILED OR SKIPPED: no alignment or fitted result admitted.\n" + message
                        + "\nRequired action: preserve original/staged data, stdout/stderr, process ledgers and partial projections. Supply the named permitted runtime/reference, correct integrity/format/diagnostic/capacity problems and retry to NEW paths. If required installation/execution is forbidden, skip dependent fitting.\n";
                Files.writeString(home.resolve("INPUT_AUDIT_FAILED.txt"), diagnostic, StandardOpenOption.CREATE_NEW);
                if (Files.exists(home.resolve("WARNINGS.md"), LinkOption.NOFOLLOW_LINKS)) Files.move(home.resolve("WARNINGS.md"), home.resolve("WARNINGS_UNADMITTED.md"));
                Files.writeString(home.resolve("WARNINGS.md"), "# " + diagnostic, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
            throw failure;
        }
    }
    private static RsemProcess.Result execute(RsemRuntime.Context runtime, RsemInputs.Prepared inputs, Path home, String name,
            List<String> command, Limits limits, RsemProcess.Check integrity, Runnable check) throws IOException {
        var roots = new ArrayList<Path>(); roots.add(inputs.home());
        try (var entries = Files.newDirectoryStream(home)) { for (Path path : entries) if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) roots.add(path); }
        Path work = home.resolve(name);
        var p = limits.process();
        var reserved = new RsemProcess.Limits(p.wallMillis(), p.addressSpaceBytes(), p.cpuSeconds(), p.fileBytes(), p.openFiles(),
                p.workspaceBytes() - limits.metadataBytes() - 65536, p.fileCount() - 8, p.processes(), p.threads(), p.sampledRssBytes(), p.monitorMillis());
        return RsemProcess.executeOwned(command, runtime.payload().environment(work), work, reserved, integrity, check, roots);
    }
    private static void requireNoDiagnostic(RsemProcess.Result process, String stage) throws IOException {
        if (Files.size(process.stderr()) != 0) throw new IOException("Original " + stage + " emitted diagnostics requiring resolution before fitting: " + process.stderr() + ". Preserve and inspect complete stderr; no diagnostic is silently suppressed.");
    }
    static RsemSamAlignments.Summary project(Path decoded, Path metadata, RsemInputs.Prepared input, RsemSamAlignments.Limits limits,
            RsemAlignmentProjection projection, Runnable check) throws IOException {
        input.verify(check);
        String samPin = RepeatInputs.hash(decoded, limits.maxSourceBytes(), check), metadataPin = RepeatInputs.hash(metadata, limits.maxSourceBytes(), check);
        MessageDigest samDigest = digest(), metadataDigest = digest(); RsemSamAlignments.Summary summary;
        try (var samRaw = Files.newInputStream(decoded, LinkOption.NOFOLLOW_LINKS); var metadataRaw = Files.newInputStream(metadata, LinkOption.NOFOLLOW_LINKS);
             var sam = reader(new DigestInputStream(samRaw, samDigest)); var description = reader(new DigestInputStream(metadataRaw, metadataDigest))) {
            var binding = RsemSamAlignments.decodedRecordMetadata(description, limits, check::run);
            summary = RsemSamAlignments.read(sam, input.reference(), input.spec().options(), limits, projection, check::run, binding);
        }
        if (!samPin.equals(java.util.HexFormat.of().formatHex(samDigest.digest())) || !metadataPin.equals(java.util.HexFormat.of().formatHex(metadataDigest.digest()))
                || !samPin.equals(RepeatInputs.hash(decoded, limits.maxSourceBytes(), check)) || !metadataPin.equals(RepeatInputs.hash(metadata, limits.maxSourceBytes(), check)))
            throw new IOException("Complete decoded SAM/metadata bytes consumed changed from their protected output evidence");
        input.verify(check); return summary;
    }
    private static BufferedReader reader(java.io.InputStream input) { return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))); }
    private static MessageDigest digest() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static Map<String,Object> summary(RsemSamAlignments.Summary summary) {
        var fields = new LinkedHashMap<String,Object>(); var findings = new LinkedHashMap<String,Long>(); summary.findings().forEach((key, value) -> findings.put(key.name(), value));
        fields.put("records", summary.records()); fields.put("groups", summary.groups()); fields.put("decoded_characters", summary.decodedCharacters());
        fields.put("ignored_trailing_records", summary.ignoredTrailingRecords()); fields.put("safe_for_fitting", summary.safeForFitting()); fields.put("findings", findings);
        fields.put("targets", summary.targets().stream().map(t -> Map.of("name", t.name(), "declared_length", t.declaredLength(), "internal_sid", t.internalSid())).toList());
        fields.put("omitted_sids", summary.omittedSids()); return fields;
    }
    private static RsemFileGraph.Limits graphLimits(Limits limits) { return new RsemFileGraph.Limits(limits.process().fileBytes(), limits.process().workspaceBytes(), Math.min(1000000, limits.process().fileCount() + 128), 8, limits.metadataBytes()); }
    private static void capacity(Path home, RsemInputs.Prepared inputs, Limits limits, long extra, int members, Runnable check) throws IOException {
        var bundle = new RepeatBundle.Limits(limits.process().workspaceBytes(), limits.process().fileCount());
        var actual = RepeatBundle.inventory(home, bundle, false, check); var source = RepeatBundle.inventory(inputs.home(), bundle, false, check);
        if (actual.bytes() + source.bytes() + extra > limits.process().workspaceBytes() || (long)actual.members() + source.members() + members > limits.process().fileCount())
            throw new IOException("Complete scientific input/audit output footprint with diagnostics exceeds capacity");
    }
    private static Runnable deadline(long millis, Runnable cancellation) {
        Objects.requireNonNull(cancellation); long start = System.nanoTime();
        return () -> { cancellation.run(); if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > millis) throw new CancellationException("Whole RSEM input audit exceeded its wall-time capacity"); };
    }
}
