/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemSamAlignments;

/** Actual original header/decoder/parser controls for the scientific input layer; not a public fitter. */
public final class InputAuditProtocol {
    private InputAuditProtocol() { }
    private static final String INPUTS = "742c4d5feb9118d5bed6b9c6e2b6f5ae89b7924f083be490823f6045fe123338";
    private static final RsemProcess.Limits PROCESS = new RsemProcess.Limits(180000, 3221225472L, 180, 67108864, 256, 268435456, 4096, 8, 64, 2147483648L, 25);
    private static final RsemInputs.Limits INPUT_LIMITS = new RsemInputs.Limits(16777216, 67108864, 256, 1048576, new RsemReference.Limits(100, 100, 1000000, 1000, 16777216, 1000000));
    private static final RsemInputAudit.Limits AUDIT_LIMITS = new RsemInputAudit.Limits(
            new RsemSamAlignments.Limits(10000, 10000, 100000, 10000, 1000, 100000, 1000000, 10000000, 10000, 1000000, 10000, 16777216),
            new RsemCramReference.Limits(1000, 1000000, 1000000, 1000000, 1048576, 1048576), PROCESS, 1048576, 3600000);
    private static String hash(Path path) throws IOException { return RepeatInputs.hash(path, 1073741824, () -> { }); }
    private static void require(boolean condition, String message) throws IOException { if (!condition) throw new IOException(message); }
    private static void write(Path path, Object value) throws IOException { Files.write(path, RsemJson.bytes(value), StandardOpenOption.CREATE_NEW); }

    /** @param args release, prior build, explicit observed receipt hash, library, original health reference prefix, NEW profile root, controlled URI
     * @throws Exception source, process, lookup, native projection or control disagreement */
    public static void main(String[] args) throws Exception {
        if (args.length != 7) throw new IllegalArgumentException("RELEASE BUILD_HOME EXPECTED_RECEIPT_SHA LIBRARY HEALTH_REFERENCE_PREFIX NEW_ROOT CONTROLLED_URI");
        String version = args[0], expected = args[2], uri = args[6]; Path buildHome = Path.of(args[1]), library = Path.of(args[3]), reference = Path.of(args[4]), root = Path.of(args[5]);
        RsemPayload.originalPath(root); require(Files.isDirectory(root) && root.toRealPath().equals(root), "Require a precreated canonical profile root");
        var built = reopen(version, buildHome, expected, library, root.resolve("source-reverification.jsonl"));
        Path health = Files.createDirectory(root.resolve("health")), fixtures = Files.createDirectory(root.resolve("fixtures"));
        byte[] raw; try (var input = InputAuditProtocol.class.getResourceAsStream("/org/jlinalg/omics/rsem-health/inputs.zip")) { require(input != null, "Missing pinned original health data"); raw = input.readNBytes(1000001); }
        require(AdmixtureInputs.digest(raw).equals(INPUTS), "Original synthetic input archive differs");
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry entry; int count = 0;
            while ((entry = zip.getNextEntry()) != null) { require(!entry.isDirectory() && entry.getName().matches("[A-Za-z0-9_.-]+") && ++count <= 8, "Unreviewed synthetic source member"); byte[] data = zip.readNBytes(1000001); require(data.length <= 1000000, "Synthetic source capacity"); Files.write(health.resolve(entry.getName()), data, StandardOpenOption.CREATE_NEW); }
            require(count == 8, "Incomplete synthetic sources");
        }
        var healthPins = pins(health); var referencePins = new TreeMap<Path,String>();
        for (String suffix : List.of(".ti", ".seq", ".grp", ".idx.fa", ".n2g.idx.fa", ".transcripts.fa")) { Path path = Path.of(reference + suffix); referencePins.put(path, hash(path)); }
        RsemProcess.Check immutable = () -> { require(pins(health).equals(healthPins), "Original health sources changed"); for (var entry : referencePins.entrySet()) require(hash(entry.getKey()).equals(entry.getValue()), "Original reference changed"); };
        // All case parent namespaces exist before any provider capture.
        for (String name : List.of("paired-sam", "paired-bam", "paired-cram", "single-sam", "single-bam", "single-cram", "reverse-sam", "noise-sam", "noise-cram", "bad-fai", "bad-md5", "bad-length", "noncontiguous", "changed-input", "changed-projection")) Files.createDirectory(root.resolve(name));
        var payload = RsemPayload.stageBuilt(built, List.of(library), root.resolve("payload"), () -> { });
        var host = new RsemHost.Limits(new RepeatHostFiles.Limits(536870912, 8589934592L, 100000, 128, 67108864),
                new RsemProcess.Limits(60000, 1073741824, 60, 16777216, 256, 1073741824, 100000, 8, 64, 536870912, 25), 1024);
        var runtime = RsemRuntime.bind(payload, root.resolve("binding"), host, 3600000, immutable, () -> { });
        RsemProcess.Check integrity = () -> { immutable.verify(); runtime.verify(() -> { }); };
        Path fasta = fixtures.resolve("reference.fa"); Files.copy(Path.of(reference + ".idx.fa"), fasta);
        String fastaSha = hash(fasta); RsemProcess.Check generationIntegrity = () -> { integrity.verify(); require(hash(fasta).equals(fastaSha), "Generated reference source changed"); };
        run(runtime, root.resolve("generate-fai"), List.of("faidx", fasta.toString()), PROCESS, generationIntegrity, List.of(health, fixtures));
        var sources = new LinkedHashMap<String,Path>(); sources.put("paired-sam", health.resolve("paired.sam")); sources.put("single-sam", health.resolve("single.sam")); sources.put("reverse-sam", health.resolve("reverse.sam")); sources.put("noise-sam", health.resolve("noise.sam"));
        for (String name : List.of("paired", "single", "noise")) {
            if (!name.equals("noise")) {
                Path bam = fixtures.resolve(name + ".bam"); run(runtime, root.resolve("generate-bam-" + name), List.of("view", "-b", "-o", bam.toString(), health.resolve(name + ".sam").toString()), PROCESS, generationIntegrity, List.of(health, fixtures)); sources.put(name + "-bam", bam);
            }
            Path cram = fixtures.resolve(name + ".cram"); run(runtime, root.resolve("generate-cram-" + name), List.of("view", "-C", "-T", fasta.toString(), "-o", cram.toString(), health.resolve(name + ".sam").toString()), PROCESS, generationIntegrity, List.of(health, fixtures));
            var header = run(runtime, root.resolve("read-header-" + name), List.of("view", "-H", cram.toString()), PROCESS, generationIntegrity, List.of(health, fixtures));
            Path replacement = fixtures.resolve(name + "-remote.sam"); Files.writeString(replacement, remote(Files.readString(header.stdout()), uri, false), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            var changed = run(runtime, root.resolve("reheader-" + name), List.of("reheader", replacement.toString(), cram.toString()), PROCESS, generationIntegrity, List.of(health, fixtures)); sources.put(name + "-cram", changed.stdout());
        }
        var release = RsemAbundance.Release.valueOf("V" + version.replace('.', '_')); var positive = new ArrayList<Map<String,Object>>(); long physical = 0, groups = 0, expectedFiles = 0;
        for (String name : List.of("paired-sam", "paired-bam", "paired-cram", "single-sam", "single-bam", "single-cram", "reverse-sam", "noise-sam", "noise-cram")) {
            Path parent = root.resolve(name); int readType = name.startsWith("single") ? 1 : 3;
            var input = RsemInputs.prepare(new RsemInputs.Spec(reference, sources.get(name), name.endsWith("cram") ? fasta : null, new RsemSamAlignments.Options(release, readType, "", 25)), parent.resolve("inputs"), INPUT_LIMITS, () -> { });
            System.out.println(version + " actual input audit " + name);
            var audit = RsemInputAudit.inspect(runtime, input, parent.resolve("audit"), AUDIT_LIMITS, () -> { });
            Path parser = parent.resolve("parser"), sample = parser.resolve("sample");
            var command = new ArrayList<String>(List.of(payload.home().resolve("bin/rsem-parse-alignments").toString(), input.referencePrefix().toString(), sample.toString(), sample.toString(), input.alignment().toString(), Integer.toString(readType)));
            if (input.cramFasta() != null) command.addAll(List.of("-t", input.cramFasta() + ".fai"));
            RsemProcess.executeOwned(command, payload.environment(parser), parser, PROCESS, () -> audit.verify(() -> { }), () -> { }, List.of(input.home(), audit.home()));
            int compared = compare(audit.projection(), parser); audit.verify(() -> { });
            physical += audit.summary().records(); groups += audit.summary().groups(); expectedFiles += compared;
            positive.add(Map.of("case", name, "physical_records", audit.summary().records(), "groups", audit.summary().groups(), "expected_original_files_compared", compared, "input_audit_sha256", hash(audit.home().resolve("INPUT_AUDIT.json")), "original_binary_sha256", hash(input.alignment())));
        }
        var negative = new ArrayList<Map<String,Object>>();
        Path wrongFaiFasta = fixtures.resolve("wrong-index.fa"); Files.copy(fasta, wrongFaiFasta); var indexLines = Files.readAllLines(Path.of(fasta + ".fai")); String[] index = indexLines.get(0).split("\t"); index[2] = Long.toString(Long.parseLong(index[2]) + 1); indexLines.set(0, String.join("\t", index)); Files.write(Path.of(wrongFaiFasta + ".fai"), indexLines);
        negative.add(refusal(runtime, reference, sources.get("paired-cram"), wrongFaiFasta, release, root.resolve("bad-fai"), "bad-fai", false, false));
        Path wrongMd5Fasta = fixtures.resolve("wrong-md5.fa"); String bases = Files.readString(fasta); int firstBase = bases.indexOf('\n') + 1; char changed = bases.charAt(firstBase) == 'A' ? 'C' : 'A'; Files.writeString(wrongMd5Fasta, bases.substring(0, firstBase) + changed + bases.substring(firstBase + 1)); Files.copy(Path.of(fasta + ".fai"), Path.of(wrongMd5Fasta + ".fai"));
        negative.add(refusal(runtime, reference, sources.get("paired-cram"), wrongMd5Fasta, release, root.resolve("bad-md5"), "bad-md5", true, false));
        Path badLengthHeader = fixtures.resolve("bad-length.sam"); var originalHeader = run(runtime, root.resolve("read-bad-length-header"), List.of("view", "-H", sources.get("paired-cram").toString()), PROCESS, generationIntegrity, List.of(health, fixtures)); Files.writeString(badLengthHeader, remote(Files.readString(originalHeader.stdout()), uri, true));
        var badLength = run(runtime, root.resolve("reheader-bad-length"), List.of("reheader", badLengthHeader.toString(), sources.get("paired-cram").toString()), PROCESS, generationIntegrity, List.of(health, fixtures));
        negative.add(refusal(runtime, reference, badLength.stdout(), fasta, release, root.resolve("bad-length"), "bad-length", true, true));
        Path noncontiguous = fixtures.resolve("noncontiguous.sam"); String paired = Files.readString(health.resolve("paired.sam")); var rows = paired.lines().filter(line -> !line.startsWith("@")).limit(4).toList(); Files.writeString(noncontiguous, paired + String.join("\n", rows) + "\n");
        negative.add(refusal(runtime, reference, noncontiguous, null, release, root.resolve("noncontiguous"), "noncontiguous", false, true));
        var changedInput = RsemInputs.prepare(new RsemInputs.Spec(reference, health.resolve("paired.sam"), null, new RsemSamAlignments.Options(release, 3, "", 25)), root.resolve("changed-input/inputs"), INPUT_LIMITS, () -> { }); Files.writeString(changedInput.alignment(), "\n", StandardOpenOption.APPEND);
        IOException changedInputError = null; try { RsemInputAudit.inspect(runtime, changedInput, root.resolve("changed-input/audit"), AUDIT_LIMITS, () -> { }); } catch (IOException expectedError) { changedInputError = expectedError; }
        require(changedInputError != null && !Files.exists(root.resolve("changed-input/audit")), "Changed protected input reached original execution"); negative.add(Map.of("case", "changed-input", "error", changedInputError.toString(), "refused_before_audit_directory", true));
        var extraInput = RsemInputs.prepare(new RsemInputs.Spec(reference, health.resolve("paired.sam"), null, new RsemSamAlignments.Options(release, 3, "", 25)), root.resolve("changed-projection/inputs"), INPUT_LIMITS, () -> { }); var extra = RsemInputAudit.inspect(runtime, extraInput, root.resolve("changed-projection/audit"), AUDIT_LIMITS, () -> { }); Files.writeString(extra.projection().resolve("expected.cnt"), "\n", StandardOpenOption.APPEND);
        IOException changedProjection = null; try { extra.verify(() -> { }); } catch (IOException expectedError) { changedProjection = expectedError; } require(changedProjection != null, "Changed expected parser projection was reusable"); negative.add(Map.of("case", "changed-projection", "error", changedProjection.toString(), "invalidated_after_controlled_change", true));
        immutable.verify(); runtime.verify(() -> { });
        Path jar = Path.of(RsemInputAudit.class.getProtectionDomain().getCodeSource().getLocation().toURI()); require(Files.isRegularFile(jar), "Must execute packaged candidate");
        write(root.resolve("observed.json"), Map.ofEntries(Map.entry("status", "passed-actual-source-linked-input-audits-and-original-parser-comparisons"), Map.entry("version", version), Map.entry("candidate_sha256", hash(jar)), Map.entry("source_build_receipt_sha256", expected), Map.entry("fresh_compilation_performed", false), Map.entry("positive", positive), Map.entry("negative", negative), Map.entry("physical_records", physical), Map.entry("groups", groups), Map.entry("expected_original_files_compared", expectedFiles), Map.entry("controlled_uri", uri), Map.entry("source_to_build_provenance_verified", true), Map.entry("fitted_result_admitted", false), Map.entry("public_fitting_workflow_available", false)));
        System.out.println(version + " PASS9 positive input audits,6 negative controls; no fitting/public workflow claimed");
    }
    private static Map<String,String> pins(Path root) throws IOException { return RepeatBundle.inventory(root, new RepeatBundle.Limits(67108864, 1000), true, () -> { }).hashes(); }
    private static RsemProcess.Result run(RsemRuntime.Context runtime, Path work, List<String> arguments, RsemProcess.Limits limits, RsemProcess.Check check, List<Path> roots) throws IOException {
        var command = new ArrayList<String>(List.of(runtime.payload().home().resolve("bin/samtools-1.3/samtools").toString())); command.addAll(arguments);
        return RsemProcess.executeOwned(command, runtime.payload().environment(work), work, limits, check, () -> { }, roots);
    }
    private static String remote(String header, String uri, boolean badLength) {
        return String.join("\n", header.lines().map(line -> { if (!line.startsWith("@SQ\t")) return line; String result = line.replaceAll("\\tUR:[^\\t]*", ""); if (badLength && result.contains("\tSN:tA1.1\t")) result = result.replace("\tLN:800", "\tLN:799"); return result + "\tUR:" + uri; }).toList()) + "\n";
    }
    private static Map<String,Object> refusal(RsemRuntime.Context runtime, Path reference, Path alignment, Path fasta, RsemAbundance.Release release, Path parent, String name, boolean header, boolean decode) throws IOException {
        var input = RsemInputs.prepare(new RsemInputs.Spec(reference, alignment, fasta, new RsemSamAlignments.Options(release, 3, "", 25)), parent.resolve("inputs"), INPUT_LIMITS, () -> { }); IOException refused = null;
        try { RsemInputAudit.inspect(runtime, input, parent.resolve("audit"), AUDIT_LIMITS, () -> { }); } catch (IOException expected) { refused = expected; }
        require(refused != null && !Files.exists(parent.resolve("audit/INPUT_AUDIT.json")), "Invalid input was admitted: " + name);
        require(Files.exists(parent.resolve("audit/header")) == header && Files.exists(parent.resolve("audit/decode")) == decode, "Invalid input reached an unexpected stage: " + name + " error=" + refused);
        require(Files.isRegularFile(parent.resolve("audit/WARNINGS.md")), "Failure lacks prominent parent warnings");
        return Map.of("case", name, "error", refused.toString(), "header_launched", header, "decoder_launched", decode, "no_input_completion", true);
    }
    private static int compare(Path expected, Path actual) throws IOException {
        int compared = 0;
        try (var files = Files.list(expected)) { for (Path path : files.toList()) { if (!path.getFileName().toString().startsWith("expected")) continue; String name = "sample" + path.getFileName().toString().substring("expected".length()); require(hash(path).equals(hash(actual.resolve(name))), "Actual original parser differs from complete native projection: " + name); compared++; } }
        var actualPins = pins(actual);
        for (var entry : actualPins.entrySet()) if (entry.getKey().startsWith("sample") && !Files.exists(expected.resolve("expected" + entry.getKey().substring("sample".length())))) require(Files.size(actual.resolve(entry.getKey())) == 0, "Unexpected nonempty original parser output: " + entry.getKey());
        return compared;
    }
    private static RsemBuild.Context reopen(String version, Path home, String expected, Path library, Path manifest) throws IOException {
        RsemPayload.originalPath(home); require(home.toRealPath().equals(home) && expected.matches("[0-9a-f]{64}"), "Require canonical prior build and explicit receipt pin");
        byte[] raw = AdmixtureInputs.raw(home.resolve("build.json"), 67108864); require(AdmixtureInputs.digest(raw).equals(expected), "Observed prior source receipt changed"); Map<?,?> record = RepeatBundle.object(raw); var profile = RsemPayload.profiles().get(version);
        require(profile != null && "COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD".equals(record.get("status")) && RsemPayload.LOCK.equals(record.get("source_profile_lock_sha256")), "Unreviewed source build");
        Map<?,?> sourceProfile = (Map<?,?>)record.get("profile"), tools = (Map<?,?>)record.get("tools"); require(profile.commit().equals(sourceProfile.get("commit")) && profile.archiveSha256().equals(sourceProfile.get("archiveSha256")) && List.of(library.toString()).equals(tools.get("libraries")), "Source/library declaration differs");
        Path source = home.resolve("RSEM-" + profile.commit()), decoder = home.resolve("outputs/omicsmatic-rsem-decode");
        var graph = RsemFileGraph.capture(List.of(source), List.of(decoder, home.resolve("inputs/decoder.cpp"), home.resolve("inputs/original-source.tar.gz")), RsemBuild.Limits.defaults().compiler(), manifest, () -> { });
        require(graph.sha256().equals(((Map<?,?>)record.get("source_graph")).get("sha256")), "Current complete compiled-source graph differs from its observed receipt");
        var evidence = new TreeMap<String,String>();
        for (var entry : ((Map<?,?>)record.get("evidence")).entrySet()) { String name = (String)entry.getKey(), hash = (String)entry.getValue(); require(!name.startsWith("/") && !name.contains("\\") && java.util.Arrays.stream(name.split("/", -1)).noneMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")) && hash.matches("[0-9a-f]{64}") && home.resolve(name).toRealPath().equals(home.resolve(name)), "Unreviewed build evidence path"); evidence.put(name, hash); }
        var context = new RsemBuild.Context(home, profile, source, decoder, List.of(library), expected, graph, evidence); context.verify(() -> { }); return context;
    }
}
