/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MemeXml;
import org.jlinalg.sequence.StremeEnrichment;
import org.jlinalg.sequence.StremeFisherAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StremeEnrichmentTest {
    static final String RESOURCE = "/org/jlinalg/sequence/streme-enrichment/original-and-independent.zip";
    static final StremeFisherAudit.Limits LIMITS = StremeFisherAudit.Limits.defaults();
    @TempDir Path temporary;
    int serial;

    static Stream<String> tables() throws IOException {
        return new String(archive().get("independent-right-tails.tsv"), StandardCharsets.UTF_8).lines().skip(1);
    }
    @ParameterizedTest @MethodSource("tables")
    void completeIndependentCombinatorialTableMatchesExactFraction(String line) {
        String[] fields = line.split("\t", -1); assertEquals(8, fields.length);
        var table = new StremeEnrichment.Table(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
        var result = StremeEnrichment.fisher(table, StremeEnrichment.Limits.defaults(), () -> { });
        assertEquals(table, result.table());
        assertEquals(new BigInteger(fields[4]), result.numerator());
        assertEquals(new BigInteger(fields[5]), result.denominator());
        double expected = Double.parseDouble(fields[6]);
        assertEquals(expected, result.probability(), Math.max(1e-323, expected * 1e-14));
        assertEquals(Double.parseDouble(fields[7]), result.log10Probability(), 3e-10);
        assertEquals(Math.min(table.positives(), table.positivePass() + table.negativePass()) - table.positivePass() + 1, result.tailTerms());
        assertTrue(result.operations() > 0);
    }

    static Stream<String> fisherFiles() {
        return Stream.of("5.5.4", "5.5.9").flatMap(v -> Stream.of("streme-supplied", "streme-shuffled", "streme-no-holdout", "streme-no-motifs").map(n -> v + "/" + n + ".xml"));
    }
    @ParameterizedTest @MethodSource("fisherFiles")
    void everyOriginalMotifAndEveryReportedFisherAndEvalueFieldIsChecked(String name) throws Exception {
        byte[] original = archive().get(name); Path input = file(original);
        var result = StremeFisherAudit.read(input, name.substring(0, 5), LIMITS, () -> { });
        assertEquals(NextflowFiles.sha(original), result.source().sha256());
        assertEquals(name.contains("no-motifs") ? 1 : 2, result.motifs().size());
        assertEquals(!name.contains("no-holdout"), result.hasHoldout());
        assertTrue(result.operations() > 0);
        assertEquals(result.motifs().size(), result.source().root().one("motifs").children().size());
        for (var motif : result.motifs()) {
            assertSame(motif.raw(), result.source().root().one("motifs").named("motif").get(result.motifs().indexOf(motif)));
            assertTrue(motif.raw().named("pos").size() >= 6);
        }
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("STATISTICAL_COMPONENT_ONLY:")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith(result.hasHoldout() ? "HOLDOUT_DECLARED:" : "NO_HOLDOUT:")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("SKIP3")));
        assertThrows(UnsupportedOperationException.class, () -> result.motifs().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.motifs().get(0).raw().attributes().clear());
    }

    static Stream<String> otherTests() {
        return Stream.of("5.5.4", "5.5.9").flatMap(v -> Stream.of("streme-binomial", "streme-central-distance").map(n -> v + "/" + n + ".xml"));
    }
    @ParameterizedTest @MethodSource("otherTests")
    void actualBinomialAndCentralDistanceOutputsAreNamedUnsupported(String name) throws Exception {
        var failure = assertThrows(StremeFisherAudit.UnsupportedInput.class,
                () -> StremeFisherAudit.read(file(archive().get(name)), name.substring(0, 5), LIMITS, () -> { }));
        assertTrue(failure.getMessage().contains("skip this Fisher audit"));
    }

    static Stream<String> corruptions() {
        return Stream.of("train-count", "control-count", "training-p", "training-log", "heldout-p", "heldout-log", "evalue", "logevalue",
                "NaN", "Inf", "huge-exponent", "tiny-exponent", "integer-overflow", "negative-count", "duplicate-motif", "changed-family-size", "unknown-motif-node", "unpaired-holdout");
    }
    @ParameterizedTest @MethodSource("corruptions")
    void corruptedOriginalFieldsAndFamilyPopulationsRefuseAReceipt(String kind) throws Exception {
        String text = original(), changed;
        changed = switch (kind) {
            case "train-count" -> text.replaceFirst("train_pos_count=\"45\"", "train_pos_count=\"61\"");
            case "control-count" -> text.replaceFirst("train_neg_count=\"5\"", "train_neg_count=\"0\"");
            case "training-p" -> replace(text, "train_pvalue", "0.5");
            case "training-log" -> replace(text, "train_log_pvalue", "0");
            case "heldout-p" -> replace(text, "test_pvalue", "1");
            case "heldout-log" -> replace(text, "test_log_pvalue", "0");
            case "evalue" -> replace(text, "test_evalue", "0.5");
            case "logevalue" -> replace(text, "test_log_evalue", "0");
            case "NaN" -> replace(text, "train_pvalue", "NaN");
            case "Inf" -> replace(text, "train_pvalue", "inf");
            case "huge-exponent" -> replace(text, "train_pvalue", "1e9999999");
            case "tiny-exponent" -> replace(text, "test_evalue", "1e-9999999");
            case "integer-overflow" -> replace(text, "train_pos_count", "2147483648");
            case "negative-count" -> replace(text, "train_neg_count", "-1");
            case "duplicate-motif" -> text.replace("</motifs>", text.substring(text.indexOf("<motif "), text.indexOf("</motif>") + 8) + "</motifs>");
            case "changed-family-size" -> text.substring(0, text.lastIndexOf("<motif ")) + text.substring(text.lastIndexOf("</motif>") + 8);
            case "unknown-motif-node" -> text.replace("</motifs>", "<other/></motifs>");
            case "unpaired-holdout" -> text.replaceFirst("<test_negatives count=\"20\"", "<test_negatives count=\"0\"");
            default -> throw new AssertionError(kind);
        };
        assertNotEquals(text, changed, kind);
        assertThrows(IOException.class, () -> StremeFisherAudit.read(file(changed.getBytes(StandardCharsets.UTF_8)), "5.5.9", LIMITS, () -> { }), kind);
    }

    @Test void editionAndProgramAreExplicit() throws Exception {
        Path input = file(original().getBytes(StandardCharsets.UTF_8));
        assertThrows(StremeFisherAudit.UnsupportedInput.class, () -> StremeFisherAudit.read(input, "5.5.8", LIMITS, () -> { }));
        assertThrows(IOException.class, () -> StremeFisherAudit.read(input, "5.5.4", LIMITS, () -> { }));
        assertThrows(IOException.class, () -> StremeFisherAudit.read(file(original().replace("<STREME", "<MEME").replace("</STREME>", "</MEME>").getBytes(StandardCharsets.UTF_8)), "5.5.9", LIMITS, () -> { }));
    }
    @Test void noHoldoutSentinelsAreRetainedWithoutSignificanceClaims() throws Exception {
        var value = StremeFisherAudit.read(file(archive().get("5.5.9/streme-no-holdout.xml")), "5.5.9", LIMITS, () -> { });
        assertFalse(value.hasHoldout());
        for (var motif : value.motifs()) {
            assertEquals(new StremeEnrichment.Table(0, 0, 0, 0), motif.heldOut().table());
            assertEquals(1, motif.heldOut().probability());
            assertTrue(motif.training().probability() < 1);
        }
        assertTrue(value.warnings().stream().anyMatch(w -> w.contains("source sentinels")));
    }
    @Test void underflowKeepsExactFractionAndFiniteLog() {
        var value = StremeEnrichment.fisher(new StremeEnrichment.Table(4096, 4096, 4096, 0), StremeEnrichment.Limits.defaults(), () -> { });
        assertEquals(0, value.probability()); assertTrue(value.log10Probability() < -2000);
        assertEquals(BigInteger.ONE, value.numerator()); assertTrue(value.denominator().bitLength() > 8000);
    }
    @Test void completeTailLimitsNeverTruncate() {
        var table = new StremeEnrichment.Table(20, 20, 10, 10);
        assertThrows(IllegalArgumentException.class, () -> StremeEnrichment.fisher(table, new StremeEnrichment.Limits(39, 100, 1000, 10000), () -> { }));
        assertThrows(IllegalArgumentException.class, () -> StremeEnrichment.fisher(table, new StremeEnrichment.Limits(40, 10, 1000, 10000), () -> { }));
        assertThrows(IllegalArgumentException.class, () -> StremeEnrichment.fisher(table, new StremeEnrichment.Limits(40, 100, 10, 10000), () -> { }));
        assertThrows(IllegalArgumentException.class, () -> StremeEnrichment.fisher(table, new StremeEnrichment.Limits(40, 100, 1000, 1), () -> { }));
    }
    @Test void cancellationInsideACompleteExactTailPropagates() {
        var calls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> StremeEnrichment.fisher(new StremeEnrichment.Table(4096, 4096, 0, 4096),
                StremeEnrichment.Limits.defaults(), () -> { if (calls.incrementAndGet() == 4) throw new CancellationException("controlled"); }));
        assertEquals(4, calls.get());
    }
    @Test void cumulativeFamilyArithmeticAndMotifCapacitiesAreEnforced() throws Exception {
        Path input = file(original().getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> StremeFisherAudit.read(input, "5.5.9", new StremeFisherAudit.Limits(LIMITS.xml(), LIMITS.fisher(), 1, 20000000), () -> { }));
        assertThrows(IOException.class, () -> StremeFisherAudit.read(input, "5.5.9", new StremeFisherAudit.Limits(LIMITS.xml(), LIMITS.fisher(), 1000, 1), () -> { }));
    }
    @Test void sameLengthSameTimestampMutationDuringArithmeticIsRefused() throws Exception {
        String text = original(); Path path = file(text.getBytes(StandardCharsets.UTF_8)); FileTime time = Files.getLastModifiedTime(path);
        var initialCalls = new AtomicInteger(); MemeXml.read(path, LIMITS.xml(), initialCalls::incrementAndGet);
        var calls = new AtomicInteger();
        assertThrows(IOException.class, () -> StremeFisherAudit.read(path, "5.5.9", LIMITS, () -> {
            if (calls.incrementAndGet() == initialCalls.get() + 3) try {
                String changed = text.replace("User-PC", "Xser-PC"); assertNotEquals(text, changed);
                Files.writeString(path, changed, StandardCharsets.UTF_8); Files.setLastModifiedTime(path, time);
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        }));
    }
    @Test void impossibleTablesAndUnsupportedCapacitiesAreRefusedBeforeWork() {
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Table(-1, 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Table(1, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Table(1, 2, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Limits(1000001, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Limits(1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Limits(1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new StremeEnrichment.Limits(1, 1, 1, 0));
    }

    private Path file(byte[] bytes) throws IOException { Path path = temporary.resolve("input-" + serial++ + ".xml"); Files.write(path, bytes); return path; }
    private static String replace(String text, String attribute, String value) { return text.replaceFirst(attribute + "=\"[^\"]*\"", attribute + "=\"" + value + "\""); }
    private static String original() throws IOException { return new String(archive().get("5.5.9/streme-supplied.xml"), StandardCharsets.UTF_8); }
    static Map<String, byte[]> archive() throws IOException {
        byte[] bytes;
        try (var stream = StremeEnrichmentTest.class.getResourceAsStream(RESOURCE)) { assertNotNull(stream); bytes = stream.readAllBytes(); }
        assertEquals("356ed43efd808234e00b68fb14a2f655e4db82c7013fc40c22a100b1bf23af12", NextflowFiles.sha(bytes));
        var files = new LinkedHashMap<String, byte[]>();
        try (var archive = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                assertFalse(entry.isDirectory()); assertNull(files.put(entry.getName(), archive.readAllBytes()));
            }
        }
        assertEquals(14, files.size()); return files;
    }
}
