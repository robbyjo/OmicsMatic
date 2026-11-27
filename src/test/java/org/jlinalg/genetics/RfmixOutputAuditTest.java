/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.genetics.RfmixOutputAudit.*;

class RfmixOutputAuditTest {
    @TempDir(cleanup = CleanupMode.NEVER) Path root;
    private final Limits limits = new Limits(1048576, 10000, 1000, 100, 10, 100000);
    private Contract contract(boolean em, boolean reanalyze) {
        return new Contract("22", List.of("Q"), List.of(new Reference("RA", "A"), new Reference("RB", "B")),
                List.of(new Locus(100, 0), new Locus(200, .02), new Locus(300, .04), new Locus(400, .06)),
                1, em ? 1 : 0, reanalyze, false);
    }
    private Path file(String name, String text) throws IOException {
        Path path = root.resolve(name); Files.writeString(path, text, StandardCharsets.UTF_8); return path;
    }
    private void fixture(boolean em) throws IOException {
        file("msp", "#Subpopulation order/codes: A=0\tB=1\n#chm\tspos\tepos\tsgpos\tegpos\tn snps\tQ.0\tQ.1\n"
                + "22\t100\t300\t0.00\t0.04\t2\t0\t1\n22\t300\t400\t0.04\t0.06\t2\t1\t0\n");
        file("fb", "#reference_panel_population:\tA\tB\nchromosome\tphysical_position\tgenetic_position\tgenetic_marker_index\tQ:::hap1:::A\tQ:::hap1:::B\tQ:::hap2:::A\tQ:::hap2:::B\n"
                + "22\t100\t0.00000\t0\t0.8\t0.2\t0.2\t0.8\n22\t200\t0.02000\t1\t0.6\t0.4\t0.4\t0.6\n"
                + "22\t300\t0.04000\t2\t0.4\t0.6\t0.6\t0.4\n22\t400\t0.06000\t3\t0.1\t0.9\t0.9\t0.1\n");
        file("Q", "#rfmix diploid global ancestry .Q format output\n#sample\tA\tB\nQ\t0.50000\t0.50000\n"
                + (em ? "RA\t1.00000\t0.00000\nRB\t0.00000\t1.00000\n" : ""));
        file("sis", "#chm\tpos\tgpos\tsnp idx\tQ.0\tQ.1\tRA.0\tRA.1\tRB.0\tRB.1\n"
                + "22\t100\t0.00000\t0\t0.7\t0.9\n22\t200\t0.02000\t1\t0.8\t0.8\n22\t300\t0.04000\t2\t0.9\t0.7\n");
    }
    private Report audit(Contract contract) throws IOException { return RfmixOutputAudit.audit(root.resolve("msp"), root.resolve("fb"), root.resolve("Q"), root.resolve("sis"), contract, limits); }
    private void replace(String name, String before, String after) throws IOException {
        Path path = root.resolve(name); String value = Files.readString(path); assertTrue(value.contains(before)); Files.writeString(path, value.replace(before, after));
    }
    @Test void completeCoreCountsAndOriginalSisDefectAreExplicit() throws Exception {
        fixture(false); var report = audit(contract(false, false));
        assertEquals(List.of("A", "B"), report.populations()); assertEquals(4, report.markers()); assertEquals(2, report.segments());
        assertEquals(16, report.posteriorCells()); assertEquals(List.of(.5, .5), report.queryFractionsFromMspMarkers().get("Q"));
        assertEquals(new SisDiagnostic(3, 10, 6, true, false), report.sis()); assertEquals(4, report.files().size());
        assertTrue(report.warnings().stream().anyMatch(s -> s.contains("not certified")));
    }
    @Test void geneticGridKeepsSourceCeilingInsteadOfNearestRepair() {
        var loci = List.of(new Locus(10, 0), new Locus(20, .09), new Locus(30, .14), new Locus(40, .35));
        assertEquals(List.of(0, 2, 3), markerIndices(loci, .1));
    }
    @Test void geneticGridRetainsEndCollapseAndPlateaus() {
        var loci = List.of(new Locus(10, 0), new Locus(20, .09), new Locus(30, .101), new Locus(40, .13));
        assertEquals(List.of(0, 3), markerIndices(loci, .1));
        assertEquals(List.of(0, 1, 2), markerIndices(List.of(new Locus(1, 0), new Locus(2, 0), new Locus(3, 0)), .1));
    }
    @Test void originalFractionalSnpIncrementIsTruncatedExplicitly() {
        var loci = contract(false, false).retainedLoci(); assertEquals(List.of(0, 1, 2, 3), markerIndices(loci, 1.9)); assertEquals(List.of(0, 2), markerIndices(loci, 2));
    }
    @Test void gridUsesOriginalFloat32SnpStorageBeforeDoubleSpacingArithmetic() {
        List<Locus> loci = new ArrayList<>();
        for (double cm : new double[]{0, .05, .1, .15, .2, .202, .25, .3}) loci.add(new Locus(loci.size() + 1, cm));
        assertEquals(List.of(0, 1, 2, 3, 5, 7), markerIndices(loci, .05));
        assertEquals(.15, loci.get(3).geneticCm()); // Original supplied values remain unchanged.
    }
    @Test void wrongQCannotPassMerelyBecauseItsFractionsSumToOne() throws Exception {
        fixture(false); replace("Q", "0.50000\t0.50000", "0.60000\t0.40000"); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void omittedPosteriorMarkerFailsEvenForBalancedGlobalAncestry() throws Exception {
        fixture(false); replace("fb", "22\t200\t0.02000\t1\t0.6\t0.4\t0.4\t0.6\n", ""); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void omittedSisDiagnosticRowIsNotSilentlyAccepted() throws Exception {
        fixture(false); replace("sis", "22\t300\t0.04000\t2\t0.9\t0.7\n", ""); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void nonfinitePosteriorFailsWithoutProbabilityRepair() throws Exception {
        fixture(false); replace("fb", "0.8\t0.2", "NaN\t0.2"); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void incorrectPosteriorFamilyIsNotRenormalized() throws Exception {
        fixture(false); replace("fb", "0.8\t0.2", "0.8\t0.8"); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void unknownAncestryCodeFails() throws Exception {
        fixture(false); replace("msp", "2\t0\t1\n", "2\t0\t2\n"); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void fullContractLocusPopulationIsRequired() throws Exception {
        fixture(false); var c = contract(false, false); var loci = new ArrayList<>(c.retainedLoci()); loci.set(1, new Locus(210, .02));
        var changed = new Contract(c.chromosome(), c.querySamples(), c.references(), loci, 1, 0, false, false);
        assertThrows(IOException.class, () -> audit(changed));
    }
    @Test void completeNumericCapacityRefusesInsteadOfTruncating() throws Exception {
        fixture(false); var tiny = new Limits(1048576, 10000, 1000, 100, 10, 15);
        assertThrows(IOException.class, () -> RfmixOutputAudit.audit(root.resolve("msp"), root.resolve("fb"), root.resolve("Q"), root.resolve("sis"), contract(false, false), tiny));
    }
    @Test void wrongUtf8IsNotReplacedDuringParsing() throws Exception {
        fixture(false); byte[] bytes = Files.readAllBytes(root.resolve("fb")); bytes[bytes.length - 2] = (byte) 0xff; Files.write(root.resolve("fb"), bytes);
        assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
    @Test void positiveEmRequiresAllReferenceQRowsAndFixedReferenceTruth() throws Exception {
        fixture(true); assertEquals(3, audit(contract(true, false)).qRows()); replace("Q", "RA\t1.00000\t0.00000", "RA\t0.90000\t0.10000");
        assertThrows(IOException.class, () -> audit(contract(true, false)));
    }
    @Test void reanalyzedReferenceQIsExplicitlyNotCrossValidatedByQueryOnlyPaths() throws Exception {
        fixture(true); replace("Q", "RA\t1.00000\t0.00000", "RA\t0.90000\t0.10000"); var result = audit(contract(true, true));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("finite/population/printing checks only")));
    }
    @Test void nativeSourceDomainRefusesDuplicateIdentityAndInvalidMap() {
        var c = contract(false, false);
        assertThrows(IllegalArgumentException.class, () -> new Contract("22", List.of("Q", "Q"), c.references(), c.retainedLoci(), 1, 0, false, false));
        assertThrows(IllegalArgumentException.class, () -> markerIndices(List.of(new Locus(1, -.5)), .1));
        assertThrows(IllegalArgumentException.class, () -> markerIndices(List.of(new Locus(1, .2), new Locus(2, .1)), .1));
    }
    @Test void longLineAndByteCapsFailBeforeReturningAReport() throws Exception {
        fixture(false);
        assertThrows(IOException.class, () -> RfmixOutputAudit.audit(root.resolve("msp"), root.resolve("fb"), root.resolve("Q"), root.resolve("sis"), contract(false, false), new Limits(1048576, 3, 1000, 100, 10, 10000)));
        assertThrows(IOException.class, () -> RfmixOutputAudit.audit(root.resolve("msp"), root.resolve("fb"), root.resolve("Q"), root.resolve("sis"), contract(false, false), new Limits(1, 10000, 1000, 100, 10, 10000)));
    }
    @Test void cancellationPreservesTheInterruptAndReturnsNoAudit() throws Exception {
        fixture(false); Thread.currentThread().interrupt();
        try { assertThrows(IOException.class, () -> audit(contract(false, false))); assertTrue(Thread.currentThread().isInterrupted()); }
        finally { Thread.interrupted(); }
    }
    @Test void hexadecimalProbabilityIsNotAnOriginalDecimalToken() throws Exception {
        fixture(false); replace("fb", "0.8\t0.2", "0x1.0p-1\t0.5"); assertThrows(IOException.class, () -> audit(contract(false, false)));
    }
}
