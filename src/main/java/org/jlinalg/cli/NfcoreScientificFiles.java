/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;

/** Resolve exact input/output objects from an already verified standalone workflow attempt. */
final class NfcoreScientificFiles {
    private final Path attempt;
    private final NextflowCapture.Snapshot capture;
    private final Map<?, ?> outputs;
    private final String inputPrefix;
    NfcoreScientificFiles(NfcoreEngineBundle.Verified verified) throws IOException {
        attempt = verified.attempt();
        capture = NextflowCapture.decode(json(attempt.resolve("capture.json"), 67108864),
            new NextflowCapture.Limits(1000000, 1000000000000L, 1000000000000L));
        outputs = object(object(json(attempt.resolve("outputs.json"), 67108864)).get("members"));
        String producer = text(object(json(attempt.resolve("session.json"), 67108864)).get("producer_root"));
        inputPrefix = producer + "/workspace/resources/";
    }
    Path output(String name) throws IOException {
        member(name); Map<?, ?> row = object(outputs.get(name));
        if (!"file".equals(row.get("kind"))) throw new IOException("Required original pipeline output is not a file: " + name);
        return objectFile(fingerprint(row.get("pin"), 1000000000000L));
    }
    Path inputPath(String producerPath) throws IOException {
        return inputMember(capturedMember(producerPath));
    }
    private String capturedMember(String producerPath) throws IOException {
        if (!producerPath.startsWith(inputPrefix)) throw new IOException("Read/reference path is outside declared captured inputs; no producer-path access or URL download inferred");
        return producerPath.substring(inputPrefix.length());
    }
    String alias(String name) throws IOException {
        String alias = capture.aliases().get(name);
        if (alias == null) throw new IOException("Missing declared captured alias: " + name);
        return alias;
    }
    Path inputMember(String name) throws IOException {
        return objectFile(resolveMember(name).identity().pin());
    }
    private record Resolved(String member, Identity identity) { }
    private Resolved resolveMember(String name) throws IOException {
        member(name); var visited = new HashSet<String>();
        for (int hop = 0; hop < 1000; hop++) {
            if (!visited.add(name)) throw new IOException("Cyclic captured input alias");
            NextflowCapture.Item selected = null;
            for (var root : capture.roots()) {
                String prefix = root.key() + "/" + root.leaf();
                if (name.equals(prefix) || name.startsWith(prefix + "/")) {
                    selected = root.items().get(name.equals(prefix) ? "" : name.substring(prefix.length() + 1)); break;
                }
            }
            if (selected == null) throw new IOException("Required declared input member is missing: " + name);
            if (selected.kind().equals("file")) return new Resolved(name, selected.original());
            if (!selected.kind().equals("symlink") || selected.resolved() == null)
                throw new IOException("Required captured input is not a complete regular file");
            String target = null;
            for (var root : capture.roots()) {
                if (selected.resolved().equals(root.source()) || selected.resolved().startsWith(root.source() + "/")) {
                    target = root.key() + "/" + root.leaf() + selected.resolved().substring(root.source().length()); break;
                }
            }
            if (target == null) throw new IOException("Captured input link escapes the declared source family");
            name = target;
        }
        throw new IOException("Captured input alias exceeds complete hop capacity");
    }
    boolean sameInputFile(String first, String second) throws IOException {
        var a = resolveMember(capturedMember(first)); var b = resolveMember(capturedMember(second));
        return a.member().equals(b.member()) || !a.identity().fileKey().equals("null") && a.identity().fileKey().equals(b.identity().fileKey());
    }
    Map<?, ?> parameters() throws IOException { return object(json(attempt.resolve("params.json"), 67108864)); }
    private Path objectFile(Pin identity) throws IOException {
        Path file = attempt.resolve("objects").resolve(identity.sha256());
        if (!pin(file, 1000000000000L, () -> { }).equals(identity)) throw new IOException("Verified captured object changed before scientific intake");
        return file;
    }
    Map<String, Object> verifyChipseq121Source() throws IOException {
        byte[] raw;
        try (var input = NfcoreScientificFiles.class.getResourceAsStream("/org/jlinalg/omics/nfcore/nfcore-source-lock.json")) {
            if (input == null) throw new IOException("Missing original pipeline source lock");
            raw = input.readNBytes(134642);
        }
        if (raw.length != 134641 || !sha(raw).equals("f5ef123c1d212061c202a1b5ef2d9fef62aa3f7cc764834d40c0c94799bf8646"))
            throw new IOException("Original nf-core source-lock resource differs");
        Map<?, ?> lock = object(NextflowParameters.parse(utf8(raw), 1048576, 1000000));
        Map<?, ?> profile = object(object(lock.get("pipelines")).get("chipseq-1.2.1"));
        String workflow = alias("workflow"); Map<?, ?> files = object(profile.get("files"));
        for (var row : files.entrySet()) {
            String name = member(text(row.getKey())); Pin expected = fingerprint(row.getValue(), 1000000000000L);
            if (!pin(inputMember(workflow + "/" + name), 1000000000000L, () -> { }).equals(expected))
                throw new IOException("Original ChIP-seq1.2.1 source differs: " + name);
        }
        return Map.of("pipeline", "nf-core/chipseq", "version", "1.2.1", "tag_commit", profile.get("commit"),
            "original_source_files", files.size(), "every_original_source_file_matches", true,
            "historical_author_commit_inferred", false, "runtime_image_authenticated_by_this_component", false);
    }
    /** Preserve the original basename for extension-aware FASTQ readers without modifying immutable objects. */
    Path namedFastq(String producerPath, Path newViews, long[] copiedBytes, long maximumBytes) throws IOException {
        Path source = inputPath(producerPath); String name = producerPath.substring(producerPath.lastIndexOf('/') + 1); member(name);
        Path folder = newViews.resolve(sha(producerPath.getBytes(java.nio.charset.StandardCharsets.UTF_8))); Files.createDirectories(folder); Path target = folder.resolve(name);
        if (!Files.exists(target)) {
            copiedBytes[0] = Math.addExact(copiedBytes[0], Files.size(source));
            if (copiedBytes[0] > maximumBytes) throw new IOException("Complete named FASTQ object views exceed declared byte capacity");
            Files.copy(source, target);
        }
        if (!pin(source, maximumBytes, () -> { }).equals(pin(target, maximumBytes, () -> { })))
            throw new IOException("Named FASTQ view differs from its immutable original bytes");
        return target;
    }
}
