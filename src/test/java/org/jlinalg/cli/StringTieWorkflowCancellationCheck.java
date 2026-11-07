/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

/** Controlled actual-process cancellation; not a packaged analysis entry point. */
public final class StringTieWorkflowCancellationCheck {
    private StringTieWorkflowCancellationCheck() { }
    public static void main(String[] args)throws Exception {
        Path output = Path.of(args[3]);
        try {
            StringTieWorkflow.start(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), output, () -> { if (Files.exists(output.resolve("fit/launch.json"))) throw new CancellationException("controlled cancellation after actual user fit launch"); });
            throw new AssertionError("Cancelled original user fit was admitted");
        } catch (IOException | CancellationException expected) {
            if (!Files.exists(output.resolve("fit/launch.json")) || !Files.exists(output.resolve("INCOMPLETE.json")) || Files.exists(output.resolve("COMPLETED.json"))) throw new AssertionError("Actual cancelled launch was not retained safely", expected);
            var failure = NextflowFiles.object(NextflowFiles.json(output.resolve("FAILURE.json"), 1048576));
            var process = NextflowFiles.object(NextflowFiles.json(output.resolve("fit/process.json"), 1048576));
            if (!failure.get("status").toString().startsWith("FAIL2") || !NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty()) throw new AssertionError("Cancelled user fit lacks explicit failure/complete cleanup", expected);
            System.out.println("PASSED_ACTUAL_USER_FIT_CANCELLATION_RETAINED_FAIL2_NO_LIVE_OWNED_WORKERS");
        }
    }
}
