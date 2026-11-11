/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** Typed original fitting with complete native output admission. Public setup/CLI is separate. */
final class RsemFit {
    private RsemFit() { }
    record Limits(RsemProcess.Limits process, RsemFitOutputs.Limits output, long metadataBytes, long wholeWallMillis) {
        Limits {
            Objects.requireNonNull(process); Objects.requireNonNull(output);
            if (metadataBytes < 8192 || metadataBytes > 67108864 || wholeWallMillis < 1 || wholeWallMillis > 86400000
                    || process.workspaceBytes() < metadataBytes + 131072 || process.fileCount() < 32 || process.fileBytes() < 8192)
                throw new IllegalArgumentException("Require explicit fitting/output/metadata/process and whole-operation capacities");
        }
    }
    record Result(Path home, RsemInputAudit.Context input, RsemFitRequest.Settings request, RsemFitOutputs.Audit audit,
                  RsemFileGraph.Snapshot files, Limits limits) {
        void verify(Runnable check) throws IOException { input.verify(check); files.verify(check); input.verify(check); }
    }
    static Result run(RsemInputAudit.Context input, RsemFitRequest.Settings request, Path home, Limits limits, Runnable cancellation) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(request); Objects.requireNonNull(limits); limits.output().heap();
        Runnable check = deadline(limits.wholeWallMillis(), cancellation);
        RsemPayload.originalPath(home);
        if (home.getParent() == null || !home.getParent().toRealPath().equals(home.getParent())) throw new IOException("Original fitting requires a canonical NEW output directory");
        for (Path other : List.of(input.home(), input.inputs().home(), input.runtime().home(), input.runtime().payload().home()))
            if (home.startsWith(other) || other.startsWith(home)) throw new IOException("Fitting, protected input/audit and runtime workspaces must be disjoint");
        if (Files.exists(home, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Existing fitting output is preserved; choose a NEW directory");
        var command = RsemFitRequest.command(input, request, home, limits.process().threads(), check);
        var p = limits.process();
        var process = new RsemProcess.Limits(p.wallMillis(), p.addressSpaceBytes(), p.cpuSeconds(), p.fileBytes(), p.openFiles(),
                p.workspaceBytes() - limits.metadataBytes() - 65536, p.fileCount() - 8, p.processes(), p.threads(), p.sampledRssBytes(), p.monitorMillis());
        try {
            var executed = RsemProcess.executeOwned(command, input.runtime().payload().environment(home), home, process,
                    () -> input.verify(check), check, List.of(input.inputs().home(), input.home()));
            input.verify(check);
            var nativeAudit = RsemFitOutputs.audit(home, input.inputs().reference(), input.summary(), input.inputs().spec().options(), input.projection(), request, limits.output(), check);
            input.verify(check);
            var report = new LinkedHashMap<String,Object>();
            report.put("schema_version", 1); report.put("status", "COMPLETED_ORIGINAL_FIT_AND_NATIVE_AUDITS");
            report.put("source_version", input.runtime().payload().source().version()); report.put("request", request);
            report.put("original_binary_input", input.inputs().alignment()); report.put("input_audit", input.home());
            report.put("input_audit_sha256", RepeatInputs.hash(input.home().resolve("INPUT_AUDIT.json"), input.limits().metadataBytes(), check));
            report.put("source_build_receipt_sha256", input.runtime().payload().sourceBuild().recordSha256());
            report.put("runtime_binding_sha256", input.runtime().bindingSha256()); report.put("source_to_build_provenance_verified", true);
            report.put("original_process_ledger", executed.ledger()); report.put("process_exit", executed.exitCode()); report.put("owned_live_after_cleanup", executed.ownedLiveAfterCleanup());
            report.put("original_parser_files_compared", nativeAudit.originalParserFilesCompared());
            report.put("transcripts", nativeAudit.results().transcripts()); report.put("genes", nativeAudit.results().genes()); report.put("numeric_cells", nativeAudit.results().numericCells());
            report.put("requested_posterior", nativeAudit.results().requestedPosterior()); report.put("actual_posterior", nativeAudit.results().actualPosterior());
            report.put("learned_model", nativeAudit.model().learned()); report.put("precision_ambiguous_groups", nativeAudit.results().precisionAmbiguousGroups());
            report.put("native_warnings", nativeAudit.results().warnings()); report.put("original_stderr_bytes", Files.size(executed.stderr()));
            report.put("complete_scientific_output_sha256", nativeAudit.scientificFiles());
            report.put("fitted_result_admitted", true); report.put("public_workflow_certification_is_separate", true); report.put("biological_or_whole_paper_equivalence_certified", false);
            report.put("scope", "Actual typed original fitting over its retained binary input, matched native parser/read/hit/count projections, complete saved model/theta and independent full isoform/gene audit with complete owned output membership. Structural/numeric integrity is not biological appropriateness, convergence, a signature, or a reusable public runtime-admission ticket.");
            byte[] raw = RsemJson.bytes(report);
            if (raw.length > limits.metadataBytes() || raw.length > p.fileBytes()) throw new IOException("Complete fitted-result metadata exceeds capacity");
            capacity(home, input, limits, raw.length + 16384, 4, check);
            Files.copy(home.resolve("WARNINGS.md"), home.resolve("PROCESS_WARNINGS.md"));
            String warning = "# RSEM fitted result: review warnings and original logs\n\nNative warning count: " + nativeAudit.results().warnings().size()
                    + ". Full native warnings and all output hashes are retained in RESULT_ADMITTED.json.\nOriginal stderr bytes: " + Files.size(executed.stderr())
                    + ". Review process.stdout and process.stderr, including convergence and model diagnostics.\n\nRequired review: confirm the declared reference, release, alignment protocol and model settings suit this study. Complete numeric/population checks do not certify convergence or biological/whole-paper equivalence. When called through the CLI, also require its parent completion/status records and review its warnings.\n";
            Files.writeString(home.resolve("WARNINGS.md"), warning, StandardOpenOption.TRUNCATE_EXISTING);
            input.verify(check); capacity(home, input, limits, raw.length + 8192, 2, check);
            Files.write(home.resolve("RESULT_ADMITTED.json"), raw, StandardOpenOption.CREATE_NEW);
            var graph = RsemFileGraph.capture(List.of(home), List.of(), new RsemFileGraph.Limits(p.fileBytes(), p.workspaceBytes(), Math.min(1000000, p.fileCount() + 128), 8, limits.metadataBytes()), null, check);
            var result = new Result(home, input, request, nativeAudit, graph, limits); result.verify(check); return result;
        } catch (IOException | RuntimeException failure) {
            if (Files.isDirectory(home, LinkOption.NOFOLLOW_LINKS)) try {
                if (Files.exists(home.resolve("RESULT_ADMITTED.json"), LinkOption.NOFOLLOW_LINKS)) Files.move(home.resolve("RESULT_ADMITTED.json"), home.resolve("RESULT_UNADMITTED.json"));
                String message = failure.toString(); if (message.length() > 2048) message = message.substring(0, 2048);
                String diagnostic = "# RSEM FIT FAILED OR SKIPPED - NO RESULT ADMITTED\n\n" + message
                        + "\n\nRequired action: preserve original/staged data, source/version identity, stdout/stderr, ledgers and partial scientific files. Correct the named model/input/output/integrity/permission/capacity problem and rerun to NEW output paths. Keep the exact historical release and its failure behavior. If required installation/execution is unavailable or forbidden, skip dependent fitting.\n";
                Files.writeString(home.resolve("FIT_FAILED.txt"), diagnostic, StandardOpenOption.CREATE_NEW);
                Files.writeString(home.resolve("WARNINGS.md"), diagnostic, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException extra) { failure.addSuppressed(extra); }
            throw failure;
        }
    }
    private static void capacity(Path home, RsemInputAudit.Context input, Limits limits, long reserve, int members, Runnable check) throws IOException {
        var bound = new RepeatBundle.Limits(limits.process().workspaceBytes(), limits.process().fileCount());
        var a = RepeatBundle.inventory(home, bound, false, check); var b = RepeatBundle.inventory(input.home(), bound, false, check); var c = RepeatBundle.inventory(input.inputs().home(), bound, false, check);
        if (a.bytes() + b.bytes() + c.bytes() + reserve > bound.bytes() || (long)a.members() + b.members() + c.members() + members > bound.members())
            throw new IOException("Complete fitting/input/audit outputs with final diagnostic reserve exceed capacity");
    }
    private static Runnable deadline(long millis, Runnable cancellation) {
        Objects.requireNonNull(cancellation); long start = System.nanoTime();
        return () -> { cancellation.run(); if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > millis) throw new CancellationException("Whole original fitting/native output admission exceeded its wall-time capacity"); };
    }
}
