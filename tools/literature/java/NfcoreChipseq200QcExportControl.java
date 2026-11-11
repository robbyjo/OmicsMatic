/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;
/** Read-only original package/source fact intake; no model refitting and no tool credit. */
public final class NfcoreChipseq200QcExportControl {
    private NfcoreChipseq200QcExportControl() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Complete immutable engine result, image declaration, private home, NEW output");
        var verified = NfcoreEngineBundle.verify(Path.of(args[0]), () -> { }); var data = new NfcoreChipseq200Files(verified); data.verifySource(); var mappings = NfcoreChipseq200Peaks.mappings(data); var peaks = new NfcoreChipseq200Peaks(data, mappings); Path root = Path.of(args[3]); Files.createDirectory(root);
        var runtime = new NfcoreChipseq200QcRuntime(Path.of(args[2]), Path.of(args[1]), root, new NextflowProcess.Limits(1800000, 68719476736L, 1800, 8589934592L, 4096, 17179869184L, 100000, 128, 4096, 17179869184L, 100)); runtime.health(); int count = 0;
        for (var group : peaks.groups) if (group.replicates() && group.multipleGroups()) {
            data.task("DESEQ2_QC", group.antibody()); Path target = root.resolve("case-" + (++count)); Files.createDirectory(target); String prefix = group.prefix().substring(group.prefix().lastIndexOf('/') + 1); String base = group.prefix().substring(0, group.prefix().lastIndexOf('/') + 1) + "deseq2/" + prefix;
            Path result = runtime.inspect(data.files.output(base + ".dds.RData"), data.files.output(base + ".rds"), data.files.output(group.prefix() + ".featureCounts.txt"), data.files.output(base.substring(0, base.lastIndexOf('/') + 1) + "size_factors/" + prefix + ".size_factors.RData"), "vst", target);
            writeJson(target.resolve("INTAKE_COMPLETED.json"), Map.of("status", "complete-original-QC-object-fact-export-only", "antibody", group.antibody(), "source_functions", List.of("DESeq2_1.28.0", "R4.0.3"), "worker_marker", pin(result.resolve("WORKER_COMPLETED.txt"), 1024, () -> { }), "new_tool_credit", 0));
        }
        if (count == 0) throw new IllegalStateException("No selected QC branch; no positive fixture admitted"); runtime.verify(); System.out.println("Complete original QC object export passed for " + count + " group(s)");
    }
}
