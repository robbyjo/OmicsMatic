/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Development-only typed contract probe. Does not install, execute or admit analysis. */
public final class RequestProbe {
    private RequestProbe() { }
    public static void main(String[] args) {
        try {
            if (args.length != 3) throw new IllegalArgumentException("Require request, intended engine root and captured expected overlay file");
            NfcoreAtacseq121Request.validate(NfcoreAtacseq121Request.read(Path.of(args[0])), Path.of(args[1]), Files.readString(Path.of(args[2]), StandardCharsets.UTF_8));
            System.out.println("PASS_TYPED_SOURCE_INPUT_CONTRACT_ONLY_NO_ANALYSIS");
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            System.exit(failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2);
        }
    }
}
