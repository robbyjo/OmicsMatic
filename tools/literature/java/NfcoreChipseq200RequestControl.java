/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;
/** Original-schema/input/path request admission only. No runtime, container or workflow is executed. */
public final class NfcoreChipseq200RequestControl {
    private NfcoreChipseq200RequestControl() { }
    public static void main(String[] args) {
        try {
            var request = NfcoreChipseq200Request.read(Path.of(args[0])); Path engine = Path.of(args[1]);
            Map<?, ?> images = object(object(json(request.imageSet(), 1048576)).get("images")), profile;
            try (var input = NfcoreChipseq200RequestControl.class.getResourceAsStream("/org/jlinalg/omics/nfcore-chipseq200/images.json")) { if (input == null) throw new IOException("Missing exact source image declarations"); profile = object(SimpleJson.parse(utf8(input.readNBytes(1048577)))); }
            var expected = new StringBuilder("process.executor = 'local'\nexecutor.cpus = ").append(request.executorCpus()).append("\nexecutor.memory = '").append(request.executorMemoryBytes()).append(" B'\nsingularity.cacheDir = ").append(quote(engine + "/workspace/execution/container-cache")).append("\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nprocess {\n");
            for (var row : object(profile.get("images")).entrySet()) for (String name : strings(object(row.getValue()).get("process_names"))) expected.append("  withName: ").append(quote(name)).append(" { container = ").append(quote(text(images.get(row.getKey())))).append(" }\n");
            expected.append("}\n"); NfcoreChipseq200Request.validate(request, engine, expected.toString()); System.out.println("PASS_COMPLETE_SOURCE_REQUEST_ADMISSION_ONLY");
        } catch (Exception failure) { System.err.println(failure); System.exit(failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2); }
    }
    private static String quote(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"; }
}
