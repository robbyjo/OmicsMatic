/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TrimGaloreAuditTest {
    @Test void sourceSpanRequiresCompleteHeaderAndPairedSequenceQualityPositions() throws Exception {
        var source = new FastqIO.Read("@A opaque comment", "ACGTACGT", "!#$%&()*");
        assertEquals(new TrimGaloreAudit.Span(4, false), TrimGaloreAudit.span(source, new FastqIO.Read(source.header(), "ACGT", "&()*")));
        assertThrows(IOException.class, () -> TrimGaloreAudit.span(source, new FastqIO.Read(source.header(), "ACGT", "!#$*")));
        assertThrows(IOException.class, () -> TrimGaloreAudit.span(source, new FastqIO.Read("@A", "ACGT", "!#$%")));
        assertThrows(IOException.class, () -> TrimGaloreAudit.span(source, new FastqIO.Read(source.header(), "AGGT", "!#$%")));
    }
    @Test void repeatedAndEmptySpansAreReportedAsAmbiguousWithoutInventingCoordinates() throws Exception {
        var source = new FastqIO.Read("@A", "AAAAA", "IIIII");
        assertEquals(new TrimGaloreAudit.Span(0, true), TrimGaloreAudit.span(source, new FastqIO.Read("@A", "AAA", "III")));
        assertEquals(new TrimGaloreAudit.Span(0, true), TrimGaloreAudit.span(source, new FastqIO.Read("@A", "", "")));
        assertEquals(new TrimGaloreAudit.Span(0, false), TrimGaloreAudit.span(new FastqIO.Read("@A", "", ""), new FastqIO.Read("@A", "", "")));
    }
    @Test @Timeout(value = 5, unit = TimeUnit.SECONDS) void repetitiveMegabaseReadAuditAvoidsQuadraticSpanSearch() throws Exception {
        var source = new FastqIO.Read("@A", "A".repeat(1_000_000) + "C", "I".repeat(1_000_001));
        var retained = new FastqIO.Read("@A", "A".repeat(500_000) + "C", "I".repeat(500_001));
        assertEquals(new TrimGaloreAudit.Span(500_000, false), TrimGaloreAudit.span(source, retained));
    }
}
