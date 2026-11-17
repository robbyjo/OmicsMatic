/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnpEffAnnotationsTest {
    @TempDir Path temporary;
    private Path fixture(String name) throws Exception {
        Path path = temporary.resolve(name);
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/genetics/snpeff/" + name)) {
            assertNotNull(stream); Files.copy(stream, path);
        }
        return path;
    }
    private SnpEffAnnotations.Report audit(Path input, Path output) throws Exception {
        return SnpEffAnnotations.auditVcf(input, output, temporary.resolve("table.tsv"), temporary.resolve("diagnostics.tsv"));
    }
    @Test void originalModernControlsPreserveEveryAlleleTranscriptAndStrand() throws Exception {
        var report = audit(fixture("modern-input.vcf"), fixture("modern-output.vcf"));
        assertEquals(18, report.inputRecords()); assertTrue(report.annotationRows() > 18);
        assertEquals(0, report.errorRows());
        String table = Files.readString(temporary.resolve("table.tsv"));
        for (String expected : List.of("p.Ala2Thr", "p.Glu2*", "frameshift_variant", "splice_acceptor_variant&intron_variant", "txA1", "txA2", "synonymous_variant")) assertTrue(table.contains(expected), expected);
        assertTrue(table.contains("T\tmissense_variant\tMODERATE"));
        assertFalse(table.contains("deleterious_missense"));
    }
    @Test void originalLegacyTxtKeepsItsOwnRepresentation() throws Exception {
        var report = SnpEffAnnotations.auditTxt(fixture("legacy-input.txt"), fixture("legacy-output.txt"), temporary.resolve("table.tsv"), temporary.resolve("diagnostics.tsv"));
        assertEquals(14, report.inputRecords()); assertEquals(29, report.annotationRows());
        String table = Files.readString(temporary.resolve("table.tsv"));
        assertTrue(table.startsWith(SnpEffAnnotations.TXT_HEADER.substring(2)));
        assertTrue(table.contains("\nTutorial\t80\t"));
        assertFalse(table.contains("\nchrTutorial\t"));
        assertTrue(table.contains("NON_SYNONYMOUS_CODING"));
    }
    @Test void actualOriginalAnnErrorHasAnEmptyConsequenceAndIsStillAnError() throws Exception {
        var report = audit(fixture("modern-invalid-input.vcf"), fixture("modern-original-error.vcf"));
        assertEquals(1, report.errorRows());
        assertTrue(Files.readString(temporary.resolve("diagnostics.tsv")).contains("ERROR_CHROMOSOME_NOT_FOUND"));
    }
    @Test void actualLegacyZeroExitRejectionHasNoAcceptedAnnotation() throws Exception {
        var error = assertThrows(java.io.IOException.class, () -> SnpEffAnnotations.auditTxt(fixture("legacy-invalid-input.txt"), fixture("legacy-original-rejection.txt"), temporary.resolve("table.tsv"), temporary.resolve("diagnostics.tsv")));
        assertTrue(error.getMessage().contains("empty annotation row"));
        assertTrue(error.getMessage().contains("original.stderr"));
    }
    @Test void changedGenotypesAreRefused() throws Exception {
        Path output = fixture("modern-output.vcf");
        Files.writeString(output, Files.readString(output).replace("\t0/2", "\t0/1"));
        assertThrows(java.io.IOException.class, () -> audit(fixture("modern-input.vcf"), output));
    }
    @Test void qualityValuesAreNotAllowedToChangeOrUseGenericNumericEquivalence() throws Exception {
        Path output = fixture("modern-output.vcf");
        Files.writeString(output, Files.readString(output).replace("\t60.0\t", "\t60.00\t"));
        assertThrows(java.io.IOException.class, () -> audit(fixture("modern-input.vcf"), output));
    }
    @Test void missingInputRecordIsRefused() throws Exception {
        Path output = fixture("modern-output.vcf");
        var rows = Files.readAllLines(output); rows.remove(rows.size()-1); Files.write(output, rows);
        assertThrows(java.io.IOException.class, () -> audit(fixture("modern-input.vcf"), output));
    }
    @Test void lostMetadataIsRefused() throws Exception {
        Path output = fixture("modern-output.vcf");
        Files.writeString(output, Files.readString(output).replace("##contig=<ID=chrTutorial,length=1200>\n", ""));
        assertThrows(java.io.IOException.class, () -> audit(fixture("modern-input.vcf"), output));
    }
    @Test void diagnosticTokensAndCombinedEffectsRemainLiteral() {
        var annotation = SnpEffAnnotations.parseAnn("A|splice_acceptor_variant&intron_variant|HIGH|G|g|transcript|t|protein_coding|1/2|||||||WARNING_A&INFO_B").get(0);
        assertEquals("WARNING_A&INFO_B", annotation.diagnostics());
        assertEquals("splice_acceptor_variant&intron_variant", annotation.fields().get(1));
        assertEquals("", annotation.fields().get(9));
        assertThrows(IllegalArgumentException.class, () -> SnpEffAnnotations.parseAnn("A|missense_variant|MODERATE"));
    }
    @Test void errorsRemainVisibleToTheWorkflow() throws Exception {
        Path output = fixture("modern-output.vcf");
        String raw = Files.readString(output);
        int start = raw.indexOf("ANN="); int end = raw.indexOf('\n', start);
        // Put the diagnostic in the final field of one original annotation.
        String ann = raw.substring(start+4, end).split("\t", 2)[0];
        String[] annotations = ann.split(",", -1), fields = annotations[0].split("\\|", -1);
        assertEquals(16, fields.length); fields[15] = "ERROR_REF_DOES_NOT_MATCH_GENOME";
        annotations[0] = String.join("|", fields);
        Files.writeString(output, raw.substring(0, start+4) + String.join(",", annotations) + raw.substring(start+4+ann.length()), StandardCharsets.UTF_8);
        assertEquals(1, audit(fixture("modern-input.vcf"), output).errorRows());
    }
}
