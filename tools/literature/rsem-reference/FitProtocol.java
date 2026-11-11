/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemResults;
import org.jlinalg.sequence.RsemSamAlignments;

/** Fresh typed-versus-independent-original fitting controls; public CLI remains separate. */
public final class FitProtocol {
    private FitProtocol() { }
    private static final String INPUTS = "742c4d5feb9118d5bed6b9c6e2b6f5ae89b7924f083be490823f6045fe123338";
    private static final RsemProcess.Limits PROCESS = new RsemProcess.Limits(180000, 3221225472L, 180, 67108864, 256, 268435456, 4096, 8, 64, 2147483648L, 25);
    private static final RsemInputs.Limits INPUT_LIMITS = new RsemInputs.Limits(16777216, 67108864, 256, 1048576, new RsemReference.Limits(100, 100, 1000000, 1000, 16777216, 1000000));
    private static final RsemInputAudit.Limits AUDIT_LIMITS = new RsemInputAudit.Limits(
            new RsemSamAlignments.Limits(10000, 10000, 100000, 10000, 1000, 100000, 1000000, 10000000, 10000, 1000000, 10000, 16777216),
            new RsemCramReference.Limits(1000, 1000000, 1000000, 1000000, 1048576, 1048576), PROCESS, 1048576, 3600000);
    private static final RsemFit.Limits FIT_LIMITS = new RsemFit.Limits(PROCESS,
            new RsemFitOutputs.Limits(10000, 100000, 1000, 1000000, 100000,
                    new RsemModel.Limits(100, 10000, 10000, 10000, 100000, 1000000, 1000000),
                    new RsemResults.Limits(100, 100, 100, 10000, 1000000, 1000000, 1000000), new RepeatBundle.Limits(268435456, 4096)), 1048576, 3600000);
    private static final List<String> CASES = List.of("paired-sam", "paired-bam", "paired-cram", "single-sam", "single-bam", "single-cram", "reverse-sam", "noise-sam", "noise-cram", "paired-noqual", "single-noqual", "single-fixed", "tagged-unmapped", "paired-pme", "paired-ci");
    private static String hash(Path path) throws IOException { return RepeatInputs.hash(path, 1073741824, () -> { }); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
    private static void write(Path path, Object value) throws IOException { Files.write(path, RsemJson.bytes(value), StandardOpenOption.CREATE_NEW); }

    /** @param args release, prior build, explicit receipt hash, library, reference prefix, precreated NEW root, controlled URI
     * @throws Exception original execution, independent comparison or native admission disagreement */
    public static void main(String[] args) throws Exception {
        if (args.length != 7) throw new IllegalArgumentException("RELEASE BUILD_HOME EXPECTED_RECEIPT_SHA LIBRARY HEALTH_REFERENCE_PREFIX NEW_ROOT CONTROLLED_URI");
        String version = args[0], uri = args[6]; Path library = Path.of(args[3]), reference = Path.of(args[4]), root = Path.of(args[5]);
        RsemPayload.originalPath(root); require(Files.isDirectory(root) && root.toRealPath().equals(root), "Require precreated canonical profile root");
        var built = reopen(version, Path.of(args[1]), args[2], library, root.resolve("source-reverification.jsonl"));
        Path health = Files.createDirectory(root.resolve("health")), fixtures = Files.createDirectory(root.resolve("fixtures"));
        byte[] raw; try (var input = FitProtocol.class.getResourceAsStream("/org/jlinalg/omics/rsem-health/inputs.zip")) { require(input != null, "Missing health sources"); raw = input.readNBytes(1000001); }
        require(AdmixtureInputs.digest(raw).equals(INPUTS), "Synthetic source archive differs");
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry entry; int count = 0;
            while ((entry = zip.getNextEntry()) != null) { require(!entry.isDirectory() && entry.getName().matches("[A-Za-z0-9_.-]+") && ++count <= 8, "Unexpected source member"); byte[] data = zip.readNBytes(1000001); require(data.length <= 1000000, "Source capacity"); Files.write(health.resolve(entry.getName()), data, StandardOpenOption.CREATE_NEW); }
            require(count == 8, "Incomplete synthetic sources");
        }
        var healthPins = pins(health); var referencePins = new TreeMap<Path,String>();
        for (String extension : List.of(".grp", ".idx.fa", ".n2g.idx.fa", ".seq", ".ti", ".transcripts.fa")) referencePins.put(Path.of(reference + extension), hash(Path.of(reference + extension)));
        RsemProcess.Check immutable = () -> { require(pins(health).equals(healthPins), "Synthetic sources changed"); for (var row : referencePins.entrySet()) require(hash(row.getKey()).equals(row.getValue()), "Original reference changed"); };
        for (String name : CASES) Files.createDirectory(root.resolve(name));
        Files.createDirectory(root.resolve("changed-result"));
        var payload = RsemPayload.stageBuilt(built, List.of(library), root.resolve("payload"), () -> { });
        var host = new RsemHost.Limits(new RepeatHostFiles.Limits(536870912, 8589934592L, 100000, 128, 67108864),
                new RsemProcess.Limits(60000, 1073741824, 60, 16777216, 256, 1073741824, 100000, 8, 64, 536870912, 25), 1024);
        var runtime = RsemRuntime.bind(payload, root.resolve("binding"), host, 3600000, immutable, () -> { });
        RsemProcess.Check integrity = () -> { immutable.verify(); runtime.verify(() -> { }); };
        Path fasta = fixtures.resolve("reference.fa"); Files.copy(Path.of(reference + ".idx.fa"), fasta);
        String fastaSha = hash(fasta); RsemProcess.Check generation = () -> { integrity.verify(); require(hash(fasta).equals(fastaSha), "Generated reference changed"); };
        samtools(runtime, root.resolve("generate-fai"), List.of("faidx", fasta.toString()), generation, List.of(health, fixtures));
        var sources = new LinkedHashMap<String,Path>();
        for (String name : List.of("paired", "single", "reverse", "noise")) sources.put(name + "-sam", health.resolve(name + ".sam"));
        for (String name : List.of("paired", "single", "noise")) {
            if (!name.equals("noise")) { Path bam = fixtures.resolve(name + ".bam"); samtools(runtime, root.resolve("generate-bam-" + name), List.of("view", "-b", "-o", bam.toString(), sources.get(name + "-sam").toString()), generation, List.of(health, fixtures)); sources.put(name + "-bam", bam); }
            Path cram = fixtures.resolve(name + ".cram"); samtools(runtime, root.resolve("generate-cram-" + name), List.of("view", "-C", "-T", fasta.toString(), "-o", cram.toString(), sources.get(name + "-sam").toString()), generation, List.of(health, fixtures));
            var header = samtools(runtime, root.resolve("read-header-" + name), List.of("view", "-H", cram.toString()), generation, List.of(health, fixtures));
            Path replacement = fixtures.resolve(name + "-remote.sam"); Files.writeString(replacement, String.join("\n", Files.readString(header.stdout()).lines().map(line -> line.startsWith("@SQ\t") ? line.replaceAll("\\tUR:[^\\t]*", "") + "\tUR:" + uri : line).toList()) + "\n");
            var changed = samtools(runtime, root.resolve("reheader-" + name), List.of("reheader", replacement.toString(), cram.toString()), generation, List.of(health, fixtures)); sources.put(name + "-cram", changed.stdout());
        }
        Path tagged = fixtures.resolve("tagged.sam");
        Files.writeString(tagged, String.join("\n", Files.readString(health.resolve("paired.sam")).lines().map(line -> {
            if (line.startsWith("@")) return line; String[] fields = line.split("\t"); return (Integer.parseInt(fields[1]) & 4) != 0 ? line + "\tXM:i:1" : line;
        }).toList()) + "\n"); sources.put("tagged-unmapped", tagged);
        var release = RsemAbundance.Release.valueOf("V" + version.replace('.', '_'));
        var completed = new ArrayList<Map<String,Object>>(); var historicalFailures = new ArrayList<Map<String,Object>>(); var results = new ArrayList<RsemFit.Result>();
        long scientificFiles = 0, numericCells = 0;
        for (String name : CASES) {
            Path parent = root.resolve(name); boolean single = name.startsWith("single"), noqual = name.endsWith("noqual"), cram = name.endsWith("cram");
            int type = (single ? 0 : 2) + (noqual ? 0 : 1);
            Path source = sources.getOrDefault(name, sources.get(single ? "single-sam" : "paired-sam"));
            var input = RsemInputs.prepare(new RsemInputs.Spec(reference, source, cram ? fasta : null, new RsemSamAlignments.Options(release, type, name.equals("tagged-unmapped") ? "XM" : "", 25)), parent.resolve("inputs"), INPUT_LIMITS, () -> { });
            var audit = RsemInputAudit.inspect(runtime, input, parent.resolve("audit"), AUDIT_LIMITS, () -> { });
            var settings = new RsemFitRequest.Settings(1618, 1, name.equals("reverse-sam") ? RsemFitRequest.Strand.REVERSE : RsemFitRequest.Strand.NONE,
                    name.equals("paired-ci") ? RsemFitRequest.Posterior.INTERVAL : name.equals("paired-pme") ? RsemFitRequest.Posterior.MEAN : RsemFitRequest.Posterior.NONE,
                    name.equals("paired-ci"), false, single ? new RsemFitRequest.Fragment(200, name.equals("single-fixed") ? 0 : 20) : null);
            System.out.println(version + " typed/direct original fitting " + name);
            IOException typedError = null, directError = null; RsemFit.Result result = null;
            try { result = RsemFit.run(audit, settings, parent.resolve("typed"), FIT_LIMITS, () -> { }); } catch (IOException failure) { typedError = failure; }
            Path direct = parent.resolve("direct");
            try { RsemProcess.executeOwned(directCommand(version, payload, input, settings, direct), payload.environment(direct), direct, PROCESS, () -> audit.verify(() -> { }), () -> { }, List.of(input.home(), audit.home())); } catch (IOException failure) { directError = failure; }
            boolean expectedFailure = name.startsWith("noise") && List.of("1.2.28", "1.3.0", "1.3.1").contains(version);
            if (expectedFailure) {
                require(result == null && typedError != null && directError != null, "Historical zero-alignment failure behavior changed");
                require(!Files.exists(parent.resolve("typed/RESULT_ADMITTED.json")) && Files.isRegularFile(parent.resolve("typed/FIT_FAILED.txt")) && Files.isRegularFile(parent.resolve("typed/WARNINGS.md")), "Failed fitting lacks quarantine/diagnostics");
                historicalFailures.add(Map.of("case", name, "typed_error", typedError.toString(), "direct_error", directError.toString(), "typed_process_ledger_sha256", hash(parent.resolve("typed/process.json")), "direct_process_ledger_sha256", hash(direct.resolve("process.json")), "result_admitted", false));
                continue;
            }
            require(typedError == null && directError == null && result != null, "Actual fitting disagreement: typed=" + typedError + " direct=" + directError);
            var actual = science(parent.resolve("typed")); var original = science(direct); require(actual.equals(original), "Complete typed/direct original scientific populations differ: " + name);
            result.verify(() -> { }); results.add(result); scientificFiles += actual.size(); numericCells += result.audit().results().numericCells();
            var row = new LinkedHashMap<String,Object>(); row.put("case", name); row.put("read_type", type); row.put("complete_scientific_files", actual.size()); row.put("scientific_sha256", actual); row.put("numeric_cells", result.audit().results().numericCells()); row.put("transcripts", result.audit().results().transcripts()); row.put("genes", result.audit().results().genes()); row.put("learned_model", result.audit().model().learned()); row.put("actual_posterior", result.audit().results().actualPosterior()); row.put("input_audit_sha256", hash(parent.resolve("audit/INPUT_AUDIT.json"))); row.put("typed_completion_sha256", hash(parent.resolve("typed/RESULT_ADMITTED.json"))); completed.add(row);
        }
        for (var result : results) result.verify(() -> { });
        // An extra result is intentionally invalidated only after all positives are reverified.
        Path poison = root.resolve("changed-result");
        var poisonInput = RsemInputs.prepare(new RsemInputs.Spec(reference, health.resolve("paired.sam"), null, new RsemSamAlignments.Options(release, 3, "", 25)), poison.resolve("inputs"), INPUT_LIMITS, () -> { });
        var poisonAudit = RsemInputAudit.inspect(runtime, poisonInput, poison.resolve("audit"), AUDIT_LIMITS, () -> { });
        var poisonResult = RsemFit.run(poisonAudit, new RsemFitRequest.Settings(1618, 1, RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.NONE, false, false, null), poison.resolve("typed"), FIT_LIMITS, () -> { });
        Files.writeString(poison.resolve("typed/sample.genes.results"), "\n", StandardOpenOption.APPEND);
        IOException refused = null; try { poisonResult.verify(() -> { }); } catch (IOException expected) { refused = expected; } require(refused != null, "Changed fitted result was reusable");
        immutable.verify(); runtime.verify(() -> { });
        write(root.resolve("observed.json"), Map.ofEntries(Map.entry("status", "passed-fresh-typed-versus-independent-original-fitting"), Map.entry("version", version), Map.entry("candidate_sha256", hash(Path.of(RsemFit.class.getProtectionDomain().getCodeSource().getLocation().toURI()))), Map.entry("completed", completed), Map.entry("historical_failures", historicalFailures), Map.entry("complete_scientific_files", scientificFiles), Map.entry("numeric_cells", numericCells), Map.entry("changed_result_refused", refused.toString()), Map.entry("public_fitting_workflow_available", false), Map.entry("source_build_receipt_sha256", built.recordSha256()), Map.entry("fresh_compilation_performed", false)));
        System.out.println(version + " PASS " + completed.size() + " complete matched fits; " + historicalFailures.size() + " retained historical failures");
    }
    // Deliberately independent of RsemFitRequest.flags/command.
    private static List<String> directCommand(String version, RsemPayload.Context payload, RsemInputs.Prepared input, RsemFitRequest.Settings settings, Path work) {
        var command = new ArrayList<String>(List.of("/usr/bin/perl", payload.home().resolve("bin/rsem-calculate-expression").toString(), "--alignments", "--no-bam-output", "--keep-intermediate-files", "--seed", "1618", "--num-threads", "1", "--tag", input.spec().options().readTypeTag()));
        if (input.spec().options().paired()) command.add("--paired-end"); if (!input.spec().options().qualities()) command.add("--no-qualities");
        boolean reverse = settings.strand() == RsemFitRequest.Strand.REVERSE;
        command.addAll(version.equals("1.2.28") ? List.of("--forward-prob", reverse ? "0" : "0.5") : List.of("--strandedness", reverse ? "reverse" : "none"));
        if (settings.posterior() != RsemFitRequest.Posterior.NONE) command.add("--calc-pme"); if (settings.posterior() == RsemFitRequest.Posterior.INTERVAL) command.add("--calc-ci");
        if (settings.estimateReadStart()) command.add("--estimate-rspd");
        if (settings.singleEndFragment() != null) command.addAll(List.of("--fragment-length-mean", "200.0", "--fragment-length-sd", settings.singleEndFragment().standardDeviation() == 0 ? "0.0" : "20.0"));
        if (input.cramFasta() != null) command.addAll(List.of("--fai", input.cramFasta() + ".fai"));
        command.addAll(List.of(input.alignment().toString(), input.referencePrefix().toString(), work.resolve("sample").toString())); return List.copyOf(command);
    }
    private static Map<String,String> science(Path root) throws IOException { var result = new TreeMap<String,String>(); for (var row : RepeatBundle.inventory(root, new RepeatBundle.Limits(268435456, 4096), true, () -> { }).hashes().entrySet()) if (row.getKey().startsWith("sample")) result.put(row.getKey(), row.getValue()); return result; }
    private static Map<String,String> pins(Path root) throws IOException { return RepeatBundle.inventory(root, new RepeatBundle.Limits(67108864, 1000), true, () -> { }).hashes(); }
    private static RsemProcess.Result samtools(RsemRuntime.Context runtime, Path work, List<String> args, RsemProcess.Check check, List<Path> roots) throws IOException { var command = new ArrayList<String>(List.of(runtime.payload().home().resolve("bin/samtools-1.3/samtools").toString())); command.addAll(args); return RsemProcess.executeOwned(command, runtime.payload().environment(work), work, PROCESS, check, () -> { }, roots); }
    private static RsemBuild.Context reopen(String version, Path home, String expected, Path library, Path manifest) throws IOException {
        RsemPayload.originalPath(home); require(home.toRealPath().equals(home) && expected.matches("[0-9a-f]{64}"), "Require canonical prior build and explicit receipt pin");
        byte[] raw = AdmixtureInputs.raw(home.resolve("build.json"), 67108864); require(AdmixtureInputs.digest(raw).equals(expected), "Observed source receipt changed"); Map<?,?> record = RepeatBundle.object(raw); var profile = RsemPayload.profiles().get(version);
        require(profile != null && "COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD".equals(record.get("status")) && RsemPayload.LOCK.equals(record.get("source_profile_lock_sha256")), "Unreviewed source build");
        Map<?,?> sourceProfile = (Map<?,?>)record.get("profile"), tools = (Map<?,?>)record.get("tools"); require(profile.commit().equals(sourceProfile.get("commit")) && profile.archiveSha256().equals(sourceProfile.get("archiveSha256")) && List.of(library.toString()).equals(tools.get("libraries")), "Source/library declaration differs");
        Path source = home.resolve("RSEM-" + profile.commit()), decoder = home.resolve("outputs/omicsmatic-rsem-decode");
        var graph = RsemFileGraph.capture(List.of(source), List.of(decoder, home.resolve("inputs/decoder.cpp"), home.resolve("inputs/original-source.tar.gz")), RsemBuild.Limits.defaults().compiler(), manifest, () -> { });
        require(graph.sha256().equals(((Map<?,?>)record.get("source_graph")).get("sha256")), "Complete source graph differs from receipt");
        var evidence = new TreeMap<String,String>();
        for (var entry : ((Map<?,?>)record.get("evidence")).entrySet()) { String name = (String)entry.getKey(), pin = (String)entry.getValue(); require(!name.startsWith("/") && !name.contains("\\") && java.util.Arrays.stream(name.split("/", -1)).noneMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")) && pin.matches("[0-9a-f]{64}") && home.resolve(name).toRealPath().equals(home.resolve(name)), "Unreviewed evidence path"); evidence.put(name, pin); }
        var result = new RsemBuild.Context(home, profile, source, decoder, List.of(library), expected, graph, evidence); result.verify(() -> { }); return result;
    }
}
