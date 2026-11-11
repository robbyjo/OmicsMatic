/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Path;

/** Request admission only: no workflow, container or analysis process is launched. */
public final class NfcoreChipseqRequestControl {
    private NfcoreChipseqRequestControl() { }
    public static void main(String[] args) {
        try {
            NfcoreChipseqRequest.validate(NfcoreChipseqRequest.read(Path.of(args[0])), Path.of(args[1]));
            System.out.println("PASS_EXACT_SOURCE_REQUEST_ADMISSION_ONLY");
        } catch (Exception failure) {
            System.err.println(failure);
            System.exit(failure instanceof NextflowProcess.Unavailable || failure instanceof NextflowRuntime.Unavailable ? 3 : 2);
        }
    }
}
