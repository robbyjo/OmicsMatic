/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Exercise the actual unprivileged container lifecycle while retaining complete process refusals. */
public final class NfcoreContainerProcessControl {
    private NfcoreContainerProcessControl() { }
    public static void main(String[] args) throws Exception {
        Path owned = Path.of(args[0]); Files.createDirectory(owned);
        var limits = new NextflowProcess.Limits(180000, 8589934592L, 180, 33554432,
            1024, 536870912, 20000, 128, 4096, 2147483648L, 5);
        String churn = "for ((j=0;j<150;j++)); do for ((i=0;i<16;i++)); do true & done; wait; done";
        var command = List.of(args[1], "exec", "--userns", "--no-home", "--pid", args[2], "/bin/bash", "-c", churn);
        try {
            var result = NfcoreProcess.executeOwned(command, Map.of("PATH", "/usr/bin:/bin"), owned.resolve("churn"), owned, limits, () -> { }, () -> { });
            if (result.exitCode() != 0 || !result.ownedLiveAfterCleanup().isEmpty()) throw new AssertionError("Incomplete container process control");
            System.out.println("PASS_ORIGINAL_CONTAINER_LIFECYCLE_AND_2400_CHILD_EXITS");
        } catch (Exception failed) {
            System.err.println("FAIL2 container process control: " + failed + "\nRequired action: preserve raw proc readings and the failed work; no result admitted. Correct the named process-monitor cause before using a new attempt.");
            System.exit(2);
        }
    }
}
