/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;

/** Read-only complete saved-object export in the original v2 R4.0.3/DESeq2_1.28.0 image. */
final class NfcoreChipseq200QcRuntime {
    private static final Pin WORKER = new Pin(6611, "b6f6121568079d61d8e33b6fb3b920e4072639625aa3076a6cc33b94e23867a6");
    private final Path home, image, declaration, root, worker;
    private final Pin declarationPin;
    private final NfcoreContainerSet containers;
    private final NextflowProcess.Limits limits;
    NfcoreChipseq200QcRuntime(Path home, Path declaration, Path root, NextflowProcess.Limits limits) throws IOException {
        this.home = home; this.declaration = declaration; this.root = directory(root); this.limits = limits;
        for (Path path : List.of(home, root)) if (!path.isAbsolute() || !path.normalize().equals(path) || path.toString().chars().anyMatch(c -> c < 32 || c == 127 || c == ':' || c == ',')) throw new IOException("Declare canonical QC paths representable by original bind arguments; no path repair");
        declarationPin = pin(declaration, 1048576, () -> { });
        containers = new NfcoreContainerSet(home, declaration, root, limits);
        Map<?, ?> profile = object(SimpleJson.parse(utf8(resource("/org/jlinalg/omics/nfcore-chipseq200/images.json"))));
        String selected = null;
        for (var row : object(profile.get("images")).entrySet()) if (strings(object(row.getValue()).get("modules")).contains("modules/local/deseq2_qc.nf")) {
            if (selected != null) throw new IOException("Ambiguous original QC image binding"); selected = text(row.getKey());
        }
        if (selected == null) throw new IOException("Missing original QC image binding");
        image = Path.of(text(object(object(json(declaration, 1048576)).get("images")).get(selected)));
        byte[] script = resource("/org/jlinalg/omics/nfcore-chipseq200-qc/inspect.R");
        if (!new Pin(script.length, sha(script)).equals(WORKER)) throw new IOException("Complete saved-QC worker bytes differ");
        worker = root.resolve("inspect.R"); Files.write(worker, script, StandardOpenOption.CREATE_NEW); verify();
    }
    void health() throws IOException {
        var version = NfcoreProcess.executeSession(List.of(home.resolve("bin/apptainer").toString(), "version"), containers.environment(), root.resolve("version-process"), root, root, limits, this::verify, () -> { });
        if (!utf8(bytes(version.stdout(), 1024)).equals("1.5.5-1\n")) throw new IOException("Literal private Apptainer provider version differs");
        Path record = root.resolve("qc-image-health");
        try { NfcoreProcess.executeSession(container(List.of("/bin/true")), containers.environment(), record, root, root, limits, this::verify, () -> { }); }
        catch (IOException failure) {
            Path ledger = record.resolve("process.json"), stderr = record.resolve("process.stderr");
            if (Files.exists(ledger) && Files.exists(stderr)) {
                Map<?, ?> saved = object(json(ledger, 67108864)); String error = utf8(bytes(stderr, 1048576));
                if (saved.containsKey("exit_code") && integer(saved.get("exit_code"), 0, 255) != 0 && list(saved.get("owned_live_after_cleanup")).isEmpty() && !saved.containsKey("cleanup_state") && (!saved.containsKey("suppressed") || list(saved.get("suppressed")).isEmpty()) && text(saved.get("error")).contains("Original Nextflow process failed, exit ") && (error.contains("Failed to create user namespace") || error.contains("user namespace disabled") || error.contains("Unprivileged user namespace") && error.contains("not permitted"))) throw new NextflowProcess.Unavailable("Operating environment forbids the required original QC namespace. Use an allowed runtime/environment; dependent analysis skipped. Read " + stderr);
            }
            throw failure;
        }
    }
    Path inspect(Path originalDds, Path originalRds, Path originalCounts, Path originalFactors, String transform, Path caseRoot) throws IOException {
        directory(caseRoot);
        if (!caseRoot.startsWith(root) || !List.of("vst", "rlog").contains(transform)) throw new IOException("Require an owned case and explicit original transform");
        var inputs = new LinkedHashMap<Path, Pin>(); var staged = new ArrayList<Path>();
        for (var entry : Map.of("original.dds.RData", originalDds, "original.rds", originalRds, "featureCounts.txt", originalCounts, "size_factors.RData", originalFactors).entrySet()) {
            Pin before = pin(entry.getValue(), 1000000000L, () -> { }); Path copy = caseRoot.resolve(entry.getKey()); Files.copy(entry.getValue(), copy); inputs.put(entry.getValue(), before); inputs.put(copy, before);
        }
        staged.addAll(List.of(caseRoot.resolve("original.dds.RData"), caseRoot.resolve("original.rds"), caseRoot.resolve("featureCounts.txt"), caseRoot.resolve("size_factors.RData")));
        NextflowProcess.Check integrity = () -> { verify(); for (var entry : inputs.entrySet()) if (!pin(entry.getKey(), 1000000000L, () -> { }).equals(entry.getValue())) throw new IOException("Complete saved-QC input changed: " + entry.getKey()); };
        Path result = caseRoot.resolve("worker"); var command = new ArrayList<>(List.of("Rscript", "--vanilla", worker.toString())); for (Path path : staged) command.add(path.toString()); command.add(transform); command.add(result.toString());
        NfcoreProcess.executeSession(container(command), containers.environment(), caseRoot.resolve("process"), caseRoot, root, limits, integrity, () -> { });
        if (!utf8(bytes(result.resolve("WORKER_COMPLETED.txt"), 1024)).equals("COMPLETE_ORIGINAL_DESEQ128_QC_FACT_EXPORT_ONLY\n")) throw new IOException("Complete original QC export marker differs");
        integrity.verify(); return result;
    }
    void verify() throws IOException {
        containers.verify(); if (!pin(declaration, 1048576, () -> { }).equals(declarationPin) || !pin(worker, 1048576, () -> { }).equals(WORKER)) throw new IOException("QC image declaration or worker changed");
    }
    private List<String> container(List<String> command) {
        var args = new ArrayList<>(List.of(home.resolve("bin/apptainer").toString(), "exec", "--userns", "--no-home", "--pid", "--cleanenv", "-B", root + ":" + root, image.toString())); args.addAll(command); return args;
    }
    private static byte[] resource(String name) throws IOException { try (var input = NfcoreChipseq200QcRuntime.class.getResourceAsStream(name)) { if (input == null) throw new IOException("Missing exact QC resource: " + name); byte[] raw = input.readNBytes(1048577); if (raw.length > 1048576) throw new IOException("QC resource exceeds capacity"); return raw; } }
}
