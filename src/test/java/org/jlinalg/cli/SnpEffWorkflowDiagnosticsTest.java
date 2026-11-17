/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.genetics.SnpEffAnnotations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnpEffWorkflowDiagnosticsTest {
    @TempDir Path temporary;
    private void diagnostics() throws Exception {
        Files.writeString(temporary.resolve("diagnostics.tsv"), "record\tchrom\tposition\toriginal_diagnostic\n1\t1\t10\tWARNING_TRANSCRIPT_INCOMPLETE\n1\t1\t10\tWARNING_TRANSCRIPT_INCOMPLETE\n1\t1\t10\tWARNING_TRANSCRIPT_INCOMPLETE&INFO_REALIGN_3_PRIME\n2\t1\t20\tINFO_REALIGN_3_PRIME\n");
    }
    @Test void countsAnnotationRowsWithoutSplittingCombinedCodesOrCallingThemUniqueVariants() throws Exception {
        diagnostics();
        SnpEffWorkflowDiagnostics.write(temporary, new SnpEffAnnotations.Report(2, 4, 4, 0), "Retain source warnings\n");
        String warning = Files.readString(temporary.resolve("WARNINGS.md"));
        assertTrue(warning.startsWith("# Observed original annotation diagnostics"));
        assertTrue(warning.contains("**4 annotation rows")); assertTrue(warning.contains("0 contain ERROR tokens"));
        assertTrue(warning.contains("Combined diagnostic strings remain combined"));
        String counts = Files.readString(temporary.resolve("SOURCE-DIAGNOSTICS.tsv"));
        assertTrue(counts.contains("WARNING_TRANSCRIPT_INCOMPLETE\t2\n"));
        assertTrue(counts.contains("WARNING_TRANSCRIPT_INCOMPLETE&INFO_REALIGN_3_PRIME\t1\n"));
        assertTrue(counts.contains("INFO_REALIGN_3_PRIME\t1\n"));
    }
    @Test void inconsistentSummaryCannotBePresentedAsComplete() throws Exception {
        diagnostics();
        assertThrows(java.io.IOException.class, () -> SnpEffWorkflowDiagnostics.write(temporary, new SnpEffAnnotations.Report(2, 4, 3, 0), "notice"));
        assertFalse(Files.exists(temporary.resolve("SOURCE-DIAGNOSTICS.tsv")));
    }
}
