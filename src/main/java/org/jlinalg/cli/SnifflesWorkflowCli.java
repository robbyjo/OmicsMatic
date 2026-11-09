/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.SnifflesVcf;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Original caller execution with complete native VCF views and whole-batch admission. */
public final class SnifflesWorkflowCli {
    private SnifflesWorkflowCli() { }
    public static void main(String[] args) { System.exit(run(args)); }
    public static int run(String[] args) {
        Path out = null, active = null; int code = 2;
        var state = new LinkedHashMap<String, Object>(); state.put("status", "FAIL2_SNIFFLES_WORKFLOW"); state.put("analysis_admitted", false);
        var completed = new ArrayList<Object>(); var warnings = new ArrayList<String>();
        try {
            require(args.length == 2 && args[0].equals("--request"), "Use --request ABSOLUTE_TYPED_REQUEST.json");
            SnifflesRuntimeProfile.platform(); var request = SnifflesWorkflowRequest.read(Path.of(args[1]));
            require(!request.out().startsWith(request.runtime().getParent()), "Output must be disjoint from immutable binding evidence");
            out = Files.createDirectory(request.out()); warnings(out, state, warnings);
            Path root = out; var limits = request.limits(); long capacity = Math.max(Math.max(limits.inputBytes(), limits.process().fileBytes()), 67108864);
            var pins = new LinkedHashMap<Path, Pin>(); var populations = new LinkedHashMap<Path, java.util.Set<String>>();
            pins.put(request.source(), request.sourcePin()); Files.write(out.resolve("request.original.json"), bytes(request.source(), 16777216), StandardOpenOption.CREATE_NEW); pins.put(out.resolve("request.original.json"), request.sourcePin());
            write(out.resolve("effective-limits.json"), limits.document()); freeze(out.resolve("effective-limits.json"), pins, capacity);
            SnifflesProcess.Check early = () -> { cancel(root); stable(pins, capacity, root); budget(root, limits.process()); };
            var runtime = SnifflesRuntimeBinding.prepare(request.runtime(), request.runtimePin(), out.resolve("host-before"), early);
            require(!out.startsWith(runtime.root()) && !runtime.root().startsWith(out), "Runtime and workflow paths must be disjoint");
            Files.write(out.resolve("runtime.original.json"), bytes(request.runtime(), 67108864), StandardOpenOption.CREATE_NEW); pins.put(out.resolve("runtime.original.json"), request.runtimePin());
            freeze(out.resolve("host-before"), pins, capacity); populations.put(out.resolve("host-before"), population(out.resolve("host-before")));
            Path inputs = Files.createDirectory(out.resolve("inputs")), helpers = Files.createDirectory(out.resolve("helpers"));
            Path emptyReferenceCache = Files.createDirectory(inputs.resolve("no-external-reference"));
            for (String name : List.of("bootstrap.py", "worker.py", "profiles.json")) Files.write(helpers.resolve(name), SnifflesRuntimeProfile.resource(name), StandardOpenOption.CREATE_NEW);
            freeze(helpers, pins, capacity); populations.put(helpers, population(helpers));
            var captured = new LinkedHashMap<String, Path>(); var inputRows = new ArrayList<Object>();
            for (var input : request.inputs()) {
                early.verify(); regular(input.path()); require(input.pin().equals(pin(input.path(), limits.inputBytes(), () -> {})), "Original input differs from its declared complete pin: " + input.id());
                require(input.pin().bytes() <= limits.process().workspaceBytes() - budget(root, limits.process()), "Declared input copy exceeds remaining complete-batch byte allowance");
                Path copy = inputs.resolve(input.id() + "." + (input.kind().equals("fasta") ? "fa" : input.kind())); copy(input.path(), copy, input.pin().bytes(), root);
                require(input.pin().equals(pin(copy, limits.inputBytes(), () -> {})), "Captured input differs from the declared original"); pins.put(input.path(), input.pin()); pins.put(copy, input.pin()); captured.put(input.id(), copy);
                if (input.kind().equals("bed") || input.kind().equals("vcf")) inspectText(copy, input.kind(), limits);
                var row = new LinkedHashMap<String, Object>(); row.put("id", input.id()); row.put("kind", input.kind()); row.put("original_spelling", input.originalSpelling()); row.put("original_path", input.path().toString()); row.put("captured_path", copy.toString()); row.put("pin", input.pin()); row.put("reference_id", input.reference()); inputRows.add(row);
            }
            write(out.resolve("input-mapping.json"), inputRows); freeze(out.resolve("input-mapping.json"), pins, capacity);
            Path processes = Files.createDirectory(out.resolve("processes")), prepared = Files.createDirectory(out.resolve("prepared")), cases = Files.createDirectory(out.resolve("analyses"));
            SnifflesProcess.Check check = () -> {
                cancel(root); stable(pins, capacity, root);
                for (var entry : populations.entrySet()) require(entry.getValue().equals(population(entry.getKey())), "Previously completed evidence population changed: " + entry.getKey());
                budget(root, limits.process());
            };
            // Full source/library checks surround every upstream process. Pure
            // Java view/freezing steps consume only the frozen data checked above.
            SnifflesProcess.Check upstreamCheck = () -> { check.verify(); runtime.verify(); };
            Runnable cancellation = () -> { try { cancel(root); budget(root, limits.process()); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); } };
            var groups = new LinkedHashMap<String, List<SnifflesWorkflowRequest.Input>>();
            for (var input : request.inputs()) if (input.kind().equals("bam") || input.kind().equals("cram")) groups.computeIfAbsent(input.reference() == null ? "" : input.reference(), key -> new ArrayList<>()).add(input);
            var inspections = new LinkedHashMap<String, Map<?, ?>>(); var referenceCaches = new LinkedHashMap<String, Path>(); int group = 0;
            for (var entry : groups.entrySet()) {
                String name = "prepare-" + (++group); Path home = Files.createDirectory(prepared.resolve(name));
                var alignmentRows = new ArrayList<Object>();
                for (var input : entry.getValue()) alignmentRows.add(Map.of("id", input.id(), "kind", input.kind(), "path", captured.get(input.id()).toString(), "legacy_requested", request.analyses().stream().anyMatch(a -> a.input().equals(input.id()) && a.edition() == SnifflesVcf.Edition.V1_0_7)));
                Path cache = entry.getValue().stream().anyMatch(i -> i.kind().equals("cram")) ? inputs.resolve("reference-cache-" + entry.getKey()) : null;
                if (cache != null) referenceCaches.put(entry.getKey(), cache);
                var payload = new LinkedHashMap<String, Object>(); payload.put("reference", entry.getKey().isEmpty() ? null : captured.get(entry.getKey()).toString()); payload.put("reference_cache", cache == null ? null : cache.toString()); payload.put("alignments", alignmentRows); payload.put("limits", limits.worker());
                Path data = home.resolve("request.json"); write(data, payload); freeze(data, pins, capacity); active = processes.resolve(name);
                var environment = environment(cache == null ? emptyReferenceCache : cache); write(home.resolve("environment.json"), environment); freeze(home.resolve("environment.json"), pins, capacity);
                var result = SnifflesProcess.executeOwned(worker(runtime.root(), helpers, "prepare", home, data), environment, active, limits.process(), upstreamCheck, cancellation, List.of(inputs, helpers, prepared, cases));
                require(result.exitCode() == 0 && result.ownedLiveAfterCleanup().isEmpty(), "Original-format preparation failed");
                var report = read(home.resolve("PREPARED.json"), 16777216); require(text(report.get("status")).equals("PASS_COMPLETE_ORIGINAL_PYSAM_PREPARATION_ONLY"), "Missing complete upstream format/index preparation");
                preparationDiagnostics(result.stderr(), entry.getValue(), captured, warnings, name, limits.vcf().maximumLineCharacters());
                for (Object item : list(report.get("inputs"))) { var value = object(item); if (value.containsKey("id")) inspections.put(text(value.get("id")), value); }
                freeze(home, pins, capacity); populations.put(home, population(home)); freeze(active, pins, capacity); populations.put(active, population(active)); check.verify();
            }
            freeze(inputs, pins, capacity); populations.put(inputs, population(inputs));
            for (var analysis : request.analyses()) {
                Path home = Files.createDirectory(cases.resolve(analysis.id())), original = Files.createDirectory(home.resolve("original")), exports = Files.createDirectory(home.resolve("native"));
                var input = request.input(analysis.input()); var inspection = inspections.get(analysis.input()); require(inspection != null, "Missing complete captured alignment inspection");
                if (analysis.edition() == SnifflesVcf.Edition.V1_0_7 && analysis.genotype() != null)
                    warnings.add(analysis.id() + ": Original Sniffles 1.0.7 force-genotyping emitted SVTYPE=INV for a declared DUP in a constructed control, including zero read support and genotype 0/0. Inspect raw output SVTYPE, read support and genotypes; do not relabel records to match supplied sites. Calling completion does not certify source accuracy.");
                if (analysis.phase() && (integer(inspection.get("HP_tagged_records"), 0, Long.MAX_VALUE) == 0 || integer(inspection.get("PS_tagged_records"), 0, Long.MAX_VALUE) == 0))
                    warnings.add(analysis.id() + ": --phase requested with absent HP or PS assignments. The original caller may return unphased or missing-phase values. Provide haplotagged reads when phased SVs are required; this adapter does not phase untagged reads.");
                Path vcf = original.resolve("calls.vcf"); var arguments = arguments(analysis, input, captured, vcf);
                List<String> command;
                if (analysis.edition() == SnifflesVcf.Edition.V1_0_7) { var invocation = new ArrayList<String>(); invocation.add(runtime.root().resolve("legacy-sniffles").toString()); invocation.addAll(arguments); command = invocation; }
                else { Path data = home.resolve("original-request.json"); write(data, Map.of("edition", analysis.edition().version(), "original_arguments", arguments)); freeze(data, pins, capacity); command = worker(runtime.root(), helpers, "call", home, data); }
                write(home.resolve("command.json"), command); freeze(home.resolve("command.json"), pins, capacity); active = processes.resolve("case-" + analysis.id());
                var environment = environment(referenceCaches.getOrDefault(input.reference(), emptyReferenceCache)); write(home.resolve("environment.json"), environment); freeze(home.resolve("environment.json"), pins, capacity);
                System.out.println("Sniffles " + analysis.edition().version() + " " + analysis.id() + " (provisional)");
                var result = SnifflesProcess.executeOwned(command, environment, active, limits.process(), upstreamCheck, cancellation, List.of(inputs, helpers, prepared, cases));
                require(result.exitCode() == 0 && result.ownedLiveAfterCleanup().isEmpty(), "Original caller or owned cleanup failed");
                diagnostics(result.stdout(), result.stderr(), limits.vcf().maximumLineCharacters());
                if (analysis.edition() != SnifflesVcf.Edition.V1_0_7) require(text(read(home.resolve("PROGRAM-RETURNED.json"), 1048576).get("status")).equals("ORIGINAL_SNIFFLES2_PROGRAM_RETURNED_ONLY"), "Original source did not return normally");
                var summary = export(vcf, exports, analysis.edition(), limits.vcf(), root, limits.process());
                var expectedContigs = new ArrayList<String>();
                for (Object item : list(inspection.get("dictionary"))) { var pair = list(item); expectedContigs.add("##contig=<ID=" + text(pair.get(0)) + ",length=" + integer(pair.get(1), 1, Integer.MAX_VALUE) + ">"); }
                require(summary.header().metadataLines().stream().filter(line -> line.startsWith("##contig=")).toList().equals(expectedContigs), "Original output changed the complete alignment contig dictionary or order");
                String source = analysis.edition() == SnifflesVcf.Edition.V1_0_7 ? "##source=Sniffles" : "##source=Sniffles2_" + analysis.edition().version();
                require(summary.header().metadataLines().stream().filter(line -> line.startsWith("##source=")).toList().equals(List.of(source)), "Output source edition differs from the bound original");
                check.verify(); var report = new LinkedHashMap<String, Object>();
                report.put("id", analysis.id()); report.put("edition", analysis.edition().version()); report.put("input_id", analysis.input()); report.put("records", summary.records()); report.put("phased_genotypes", summary.phasedGenotypes()); report.put("phase_requested", analysis.phase()); report.put("analysis_admitted", false); report.put("status", "PROVISIONAL_COMPLETE_ORIGINAL_OUTPUT_AND_NATIVE_VIEW_ONLY");
                write(home.resolve("PROVISIONAL.json"), report); completed.add(report);
                freeze(home, pins, capacity); populations.put(home, population(home)); freeze(active, pins, capacity); populations.put(active, population(active)); check.verify();
            }
            // Library probes do not consume completed scientific result files.
            // Rehash all frozen results before and after this complete census;
            // each probe still checks cancellation and its native helper pins.
            // No hash is cached across original caller/preparation processes.
            upstreamCheck.verify(); var after = SnifflesHost.capture(runtime.root(), out.resolve("host-after"), () -> cancel(root));
            require(runtime.host().equals(after), "Original host/library/search bindings changed across the complete batch"); upstreamCheck.verify();
            write(out.resolve("result-files.json"), inventory(out, capacity)); budget(out, limits.process());
            state.put("status", "PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW"); state.put("analysis_admitted", true); state.put("analyses", completed);
            state.put("result_files_pin", pin(out.resolve("result-files.json"), 67108864, () -> {})); state.put("raw_inputs_unchanged", true); state.put("source_namespace_patched", false); state.put("analysis_installed_software", false); state.put("owned_live_after_cleanup", List.of());
            state.put("independent_SV_calling_or_biological_reproduction_certified", false); code = 0;
            state.put("final_library_census_integrity_scope", "Full frozen input/helper/result and runtime checks immediately before and after the complete final library census. Each library probe checks cancellation and native helper/configuration pins. Original caller/preparation processes retain full source/runtime/data checks before and after every execution; pure Java view/freezing stages check frozen data/populations/capacities. No checksum cache crosses original processes.");
        } catch (SnifflesProcess.UnavailableDependency unavailable) { state.put("status", completed.isEmpty() ? "SKIP3_SNIFFLES_DEPENDENCY_OR_MODE_UNAVAILABLE" : "FAIL2_SNIFFLES_RUNTIME_CHANGED_AFTER_PROVISIONAL_CASES"); details(state, unavailable, active); code = completed.isEmpty() ? 3 : 2; }
        catch (Exception failure) { details(state, failure, active); }
        finally {
            state.put("earlier_cases_remain_provisional_unless_complete_batch_passes", completed);
            state.put("warnings", List.copyOf(warnings));
            if (out != null) try { warnings(out, state, warnings); state.put("warnings_pin", pin(out.resolve("WARNINGS.md"), 16777216, () -> {})); write(out.resolve("COMPLETED.json"), state); }
            catch (IOException failure) { System.err.println("FAIL2: final complete admission receipt unavailable: " + failure); code = 2; }
            if (code != 0) System.err.println(state.get("status") + ": " + state.get("error") + "\n" + state.getOrDefault("original_diagnostic_tail", "") + "\nACTION: " + SnifflesRuntimeProfile.ACTION);
        }
        if (code == 0) System.out.println(state.get("status") + ": " + out);
        return code;
    }
    private static List<String> worker(Path root, Path helpers, String mode, Path home, Path data) {
        return SnifflesRuntimeCli.python(root, helpers.resolve("worker.py"), mode, home, List.of(data.toString()));
    }
    private static Map<String, String> environment(Path referenceCache) {
        var result = new LinkedHashMap<>(SnifflesRuntimeProfile.environment());
        result.put("REF_PATH", referenceCache + "/%s"); result.put("REF_CACHE", referenceCache + "/%s"); return result;
    }
    private static List<String> arguments(SnifflesWorkflowRequest.Analysis analysis, SnifflesWorkflowRequest.Input input, Map<String, Path> captured, Path vcf) {
        boolean legacy = analysis.edition() == SnifflesVcf.Edition.V1_0_7;
        var result = new ArrayList<>(List.of(legacy ? "--mapped_reads" : "--input", captured.get(input.id()).toString(), "--vcf", vcf.toString()));
        option(result, "--threads", analysis.threads()); option(result, legacy ? "--min_length" : "--minsvlen", analysis.minSvlen());
        option(result, legacy ? "--min_support" : "--minsupport", analysis.minSupport()); option(result, legacy ? "--minmapping_qual" : "--mapq", analysis.mapq());
        option(result, "--min-alignment-length", analysis.minAlignmentLength());
        option(result, "--min_seq_size", analysis.legacyMinSegmentSize());
        if (analysis.phase()) result.add("--phase"); if (analysis.legacyGenotype()) result.add("--genotype");
        if (analysis.sampleId() != null) result.add("--sample-id=" + analysis.sampleId());
        if (input.reference() != null) result.addAll(List.of("--reference", captured.get(input.reference()).toString()));
        if (analysis.tandem() != null) result.addAll(List.of("--tandem-repeats", captured.get(analysis.tandem()).toString()));
        if (analysis.genotype() != null) result.addAll(List.of(legacy ? "--Ivcf" : "--genotype-vcf", captured.get(analysis.genotype()).toString()));
        return result;
    }
    private static void option(List<String> values, String name, Object value) { if (value != null) values.addAll(List.of(name, value.toString())); }
    private static void diagnostics(Path stdout, Path stderr, int maximumLine) throws IOException {
        for (Path file : List.of(stdout, stderr)) try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var line = new StringBuilder(); int ch;
            do {
                ch = input.read();
                if (ch >= 0 && ch != '\n') { require(line.length() < maximumLine, "Original diagnostic line exceeds explicit capacity"); line.append((char)ch); continue; }
                String lower = line.toString().stripLeading().toLowerCase(java.util.Locale.ROOT);
                require(!lower.startsWith("sniffles2 error:") && !lower.startsWith("sniffles error:") && !lower.startsWith("traceback (most recent call last)")
                    && !lower.contains("unhandled error while running sniffles") && !lower.startsWith("[e::"), "Original caller diagnostic rejects admission despite exit0: " + line);
                line.setLength(0);
            } while (ch >= 0);
        }
    }
    private static void preparationDiagnostics(Path stderr, List<SnifflesWorkflowRequest.Input> inputs, Map<String, Path> captured, List<String> warnings, String stage, int maximumLine) throws IOException {
        var resolved = new HashSet<String>();
        for (var input : inputs) if (input.kind().equals("cram")) {
            Path copy = captured.get(input.id()); require(Files.isRegularFile(Path.of(copy + ".crai"), LinkOption.NOFOLLOW_LINKS), "New captured CRAM index missing after complete preparation");
            resolved.add("[E::cram_index_load] Could not retrieve index file for '" + copy + "'");
        }
        try (var reader = Files.newBufferedReader(stderr, StandardCharsets.UTF_8)) {
            var text = new StringBuilder(); int ch; int retained = 0;
            do {
                ch = reader.read();
                if (ch >= 0 && ch != '\n') { require(text.length() < maximumLine, "Preparation diagnostic line exceeds capacity"); text.append((char)ch); continue; }
                String line = text.toString().stripTrailing(); text.setLength(0);
                if (resolved.contains(line)) warnings.add(stage + ": resolved optional CRAM-index lookup during full-stream intake; the NEW owned CRAI was generated and opened successfully. Original diagnostic retained: " + line);
                else if (!line.isEmpty()) {
                    require(!line.startsWith("[E::") && !line.startsWith("Traceback (most recent call last)"), "Original format-preparation error despite exit0: " + line);
                    if (retained++ < 32) warnings.add(stage + ": original preparation diagnostic (complete stderr retained): " + line);
                }
            } while (ch >= 0);
            if (retained > 32) warnings.add(stage + ": " + retained + " original preparation diagnostic lines retained in complete stderr; the first 32 are displayed here.");
        }
    }
    private static SnifflesVcf.Summary export(Path vcf, Path out, SnifflesVcf.Edition edition, SnifflesVcf.Limits limits, Path job, SnifflesProcess.Limits process) throws IOException {
        SnifflesVcf.Summary summary;
        var allowance = new ViewAllowance(process.workspaceBytes() - budget(job, process), process.fileBytes());
        Path rowFile = out.resolve("variants.jsonl");
        try (var input = new CancellableInput(Files.newInputStream(vcf), job);
             var rows = new BufferedOutputStream(Files.newOutputStream(rowFile, StandardOpenOption.CREATE_NEW), 65536)) {
            summary = SnifflesVcf.read(input, edition, limits,
                header -> allowance.write(out.resolve("header.json"), Map.of("metadata_lines", header.metadataLines(), "column_line", header.columnLine(), "samples", header.samples())),
                variant -> {
                    var row = new LinkedHashMap<String, Object>(); row.put("row", variant.row()); row.put("raw_line", variant.rawLine()); row.put("columns", variant.columns());
                    var info = new ArrayList<Object>(); for (var field : variant.info()) { var value = new LinkedHashMap<String, Object>(); value.put("key", field.key()); value.put("value", field.value()); info.add(value); }
                    row.put("info", info); row.put("format", variant.format()); row.put("samples", variant.samples()); row.put("read_support_info_field", edition.readSupportInfoField());
                    var support = variant.readSupport(edition); row.put("read_support", support.isPresent() ? support.getAsLong() : null);
                    byte[] encoded = SnifflesWorkflowJson.bytes(row); allowance.reserve(rowFile, encoded.length); rows.write(encoded);
                });
        }
        allowance.write(out.resolve("summary.json"), Map.of("records", summary.records(), "variant_types", summary.variantTypes(), "pass_records", summary.passRecords(), "support_present", summary.supportPresent(), "support_missing", summary.supportMissing(), "phased_genotypes", summary.phasedGenotypes(), "phase_info_records", summary.phaseInfoRecords(), "samples", summary.header().samples(), "read_support_info_field", edition.readSupportInfoField()));
        return summary;
    }
    private static final class ViewAllowance {
        private long remaining; private final long fileBytes; private final Map<Path, Long> sizes = new LinkedHashMap<>();
        ViewAllowance(long remaining, long fileBytes) { this.remaining = remaining; this.fileBytes = fileBytes; }
        void reserve(Path file, long bytes) throws IOException {
            long size = Math.addExact(sizes.getOrDefault(file, 0L), bytes);
            require(bytes <= remaining && size <= fileBytes, "Native view exceeds remaining whole-batch or per-file byte allowance; no record is dropped to hide the failure");
            remaining -= bytes; sizes.put(file, size);
        }
        void write(Path file, Object value) throws IOException { byte[] encoded = SnifflesWorkflowJson.bytes(value); reserve(file, encoded.length); Files.write(file, encoded, StandardOpenOption.CREATE_NEW); }
    }
    private static final class CancellableInput extends java.io.FilterInputStream {
        private final Path root;
        CancellableInput(java.io.InputStream input, Path root) { super(input); this.root = root; }
        @Override public int read() throws IOException { cancel(root); return in.read(); }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { cancel(root); return in.read(bytes, offset, length); }
    }
    private static void copy(Path source, Path target, long expected, Path root) throws IOException {
        long bytes = 0;
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS); var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[1048576];
            while (bytes < expected) {
                cancel(root); int read = input.read(buffer, 0, (int)Math.min(buffer.length, expected - bytes));
                require(read >= 0, "Original input shrank during bounded copy"); output.write(buffer, 0, read); bytes += read;
            }
            require(input.read() == -1, "Original input grew beyond its declared complete pin; excess bytes were not copied");
        }
    }
    private static void inspectText(Path path, String kind, SnifflesWorkflowRequest.Limits limits) throws IOException {
        long lines = 0; int columns = 0; boolean header = false;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)))) {
            StringBuilder text = new StringBuilder(); int ch;
            do {
                ch = reader.read();
                if (ch >= 0 && ch != '\n') { require(ch != 0 && text.length() < limits.vcf().maximumLineCharacters(), "Text line/NUL capacity exceeded"); text.append((char)ch); }
                else if (ch == '\n' || text.length() > 0) {
                    String line = text.toString(); if (line.endsWith("\r")) line = line.substring(0, line.length() - 1); text.setLength(0);
                    require(++lines <= limits.alignmentRecords(), "Complete annotation/known-VCF text population exceeds capacity");
                    if (kind.equals("vcf")) {
                        if (line.startsWith("#CHROM\t")) { require(!header, "Repeated known-VCF header"); header = true; columns = line.split("\t", -1).length; require(columns >= 8, "Incomplete known-VCF columns"); }
                        else if (!line.startsWith("##")) require(header && !line.isEmpty() && line.split("\t", -1).length == columns, "Known-VCF record/header column mismatch");
                    }
                }
            } while (ch >= 0);
        }
        require(!kind.equals("vcf") || header, "Known VCF has no complete column header");
    }
    private static void stable(Map<Path, Pin> pins, long capacity, Path root) throws IOException {
        Runnable cancellation = () -> { try { cancel(root); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); } };
        for (var entry : pins.entrySet()) require(entry.getValue().equals(pin(entry.getKey(), capacity, cancellation)), "Previously bound input, request, helper or completed output changed: " + entry.getKey());
    }
    private static void freeze(Path root, Map<Path, Pin> pins, long capacity) throws IOException {
        if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) { var actual = pin(root, capacity, () -> {}); var prior = pins.putIfAbsent(root, actual); require(prior == null || prior.equals(actual), "Previously frozen evidence changed"); return; }
        try (var tree = Files.walk(root)) { for (Path path : tree.toList()) { require(!Files.isSymbolicLink(path), "Evidence contains a link"); if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue; require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Evidence contains a nonregular node"); freeze(path, pins, capacity); } }
    }
    private static java.util.Set<String> population(Path root) throws IOException {
        var result = new HashSet<String>(); try (var tree = Files.walk(root)) { for (Path path : tree.toList()) { require(!Files.isSymbolicLink(path) && (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)), "Evidence population contains a linked/nonregular node"); result.add(root.relativize(path).toString()); } } return Set.copyOf(result);
    }
    private static Map<String, Object> inventory(Path root, long capacity) throws IOException {
        var result = new LinkedHashMap<String, Object>(); try (var tree = Files.walk(root)) { for (Path path : tree.sorted().toList()) { require(!Files.isSymbolicLink(path), "Final output contains a link"); if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || path.equals(root.resolve("WARNINGS.md"))) continue; result.put(root.relativize(path).toString().replace('\\', '/'), pin(path, capacity, () -> {})); } } return result;
    }
    private static long budget(Path root, SnifflesProcess.Limits limits) throws IOException {
        long[] bytes = {0}; int[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException { require(++count[0] <= limits.fileCount() && !attrs.isSymbolicLink(), "Complete batch directory/link capacity exceeded"); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException { require(attrs.isRegularFile() && ++count[0] <= limits.fileCount(), "Complete batch contains nonregular files or exceeds count capacity"); bytes[0] = Math.addExact(bytes[0], attrs.size()); require(bytes[0] <= limits.workspaceBytes(), "Complete batch byte capacity exceeded"); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFileFailed(Path path, IOException failure) throws IOException { if (failure instanceof java.nio.file.NoSuchFileException) return FileVisitResult.CONTINUE; throw failure; }
        });
        return bytes[0];
    }
    private static void cancel(Path root) throws IOException { SnifflesRuntimeCli.cancel(root); }
    private static void details(Map<String, Object> state, Exception failure, Path process) {
        state.put("analysis_admitted", false); state.put("error", failureText(failure));
        if (process != null) {
            state.put("active_original_process", process.toString()); var tail = new StringBuilder();
            for (String name : List.of("process.stderr", "process.stdout")) { Path file = process.resolve(name); if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var input = new java.io.RandomAccessFile(file.toFile(), "r")) { long start = Math.max(0, input.length() - 32768); input.seek(start); byte[] raw = new byte[(int)(input.length() - start)]; input.readFully(raw); tail.append(name).append(" (diagnostic tail; original bytes retained):\n").append(new String(raw, StandardCharsets.UTF_8)).append('\n'); } catch (IOException diagnostic) { tail.append(diagnostic).append('\n'); }
            }
            state.put("original_diagnostic_tail", tail.toString());
        }
    }
    private static String failureText(Throwable failure) {
        var pending = new java.util.ArrayDeque<Throwable>();
        var labels = new java.util.ArrayDeque<String>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        var text = new StringBuilder(); pending.add(failure); labels.add(""); int remaining = 32;
        while (!pending.isEmpty() && remaining-- > 0) {
            Throwable item = pending.remove(); String label = labels.remove();
            if (!seen.add(item)) continue;
            if (!text.isEmpty()) text.append('\n');
            text.append(label).append(item);
            if (item.getCause() != null) { pending.add(item.getCause()); labels.add("CAUSE: "); }
            for (Throwable suppressed : item.getSuppressed()) {
                if (pending.size() >= 32) break;
                pending.add(suppressed); labels.add("SUPPRESSED: ");
            }
        }
        if (!pending.isEmpty()) text.append("\nAdditional diagnostic causes omitted from this bounded summary; preserve complete process logs.");
        return text.toString();
    }
    private static void warnings(Path out, Map<String, Object> state, List<String> warnings) throws IOException {
        var text = new StringBuilder("# " + state.get("status") + "\n\n" + SnifflesRuntimeProfile.ACTION + "\n\n");
        text.append("**Only PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW with analysis_admitted=true admits this entire batch. Every per-case PROVISIONAL receipt remains subordinate to it; partial earlier cases from a failed, skipped, cancelled or incomplete batch must not be used.**\n\n");
        text.append("Original Sniffles performs calling, clustering, consensus, filtering and genotyping. Native views retain every returned VCF record and all original sample columns. Empty call sets can be legitimate. Constructed controls showed source 1.0.7 omitting an initial deletion; no missing call is fabricated. Supporting reads (RE or SUPPORT) are not an inferred paper read-depth filter. Caller flags do not perform WhatsHap phasing/haplotagging, SURVIVOR/Jasmine merging, Iris polishing or bcftools consensus. No biological correctness or paper reproduction is certified.\n\n");
        text.append("Inputs are copied unchanged. Bound original pysam scans every alignment and creates NEW indexes in the owned input directory; no supplied index is consumed/repaired and no sorting/filtering occurs. CRAM uses captured matching FASTA and an owned HTSlib-format MD5 reference cache, with local-only REF_PATH/REF_CACHE settings recorded per command. The cache contains unformatted upper-case sequence as required by HTSlib; raw FASTA bytes remain unchanged. Reference dictionaries and available M5 values are checked; matching names/lengths alone does not authenticate an assembly. Effective capacities are in effective-limits.json. Hard per-process AS/CPU/file/nofile limits and sampled process/thread/RSS/workspace checks are not an OS sandbox or hard aggregate RSS reservation. Analysis installs nothing; external SNF/pickle input is unsupported.\n\n");
        if (state.containsKey("error")) text.append("**ACTION REQUIRED: ").append(state.get("error")).append("**\n\nRead the complete original stdout/stderr and process ledger at ").append(state.getOrDefault("active_original_process", "the named binding/preparation path")).append(". Preserve this attempt, correct the named cause and retry a NEW output directory.\n\n").append(state.getOrDefault("original_diagnostic_tail", "")).append('\n');
        for (String warning : warnings) text.append("- ").append(warning).append('\n');
        Files.writeString(out.resolve("WARNINGS.md"), text.toString(), StandardCharsets.UTF_8);
    }
}
