/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.MemeDiscoveryScoring;
import org.jlinalg.sequence.MemeDiscoverySites;
import org.jlinalg.sequence.MemeXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MemeDiscoverySitesTest {
    static final MemeDiscoverySites.Limits LIMITS = MemeDiscoverySites.Limits.defaults();
    static final String EDGE = "sites-original-5.5.9-1/edge-both";
    @TempDir Path temporary;
    int serial;
    private static final class Data {
        static final Map<String, byte[]> FILES;
        static final Map<String, Map<?, ?>> FAMILIES;
        static {
            try {
                byte[] raw;
                try (var input = MemeDiscoverySitesTest.class.getResourceAsStream("/org/jlinalg/sequence/meme-discovery-sites/original-site-contracts.zip")) {
                    assertNotNull(input); raw = input.readAllBytes();
                }
                assertEquals("111d5adedd0dadadb08744dcf83175554864613341ec71cfdb6614d2cef0d816", NextflowFiles.sha(raw));
                var files = new LinkedHashMap<String, byte[]>();
                try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
                    for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                        assertFalse(e.isDirectory()); assertNull(files.put(e.getName(), zip.readAllBytes()));
                    }
                }
                FILES = Map.copyOf(files); var rows = new LinkedHashMap<String, Map<?, ?>>();
                var document = NextflowFiles.object(SimpleJson.parse(new String(files.get("EXPECTED.json"), StandardCharsets.UTF_8)));
                for (Object x : list(document.get("families"))) { var row = NextflowFiles.object(x); assertNull(rows.put((String) row.get("name"), row)); }
                assertEquals(42, rows.size()); FAMILIES = java.util.Collections.unmodifiableMap(rows);
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
    static Stream<String> originals() { return Data.FAMILIES.keySet().stream(); }
    @ParameterizedTest @MethodSource("originals")
    void allOriginalGeometryWeightedMatricesScoresNullTailsAndDiagramsAgree(String name) throws Exception {
        var oracle = Data.FAMILIES.get(name); var expected = expected(oracle); byte[] xml = xml(name);
        var audit = MemeDiscoverySites.read(file(xml), expected, LIMITS, () -> { });
        assertEquals(NextflowFiles.sha(xml), audit.source().sha256()); assertEquals(expected, audit.expected());
        List<?> motifs = list(oracle.get("motifs")); assertEquals(motifs.size(), audit.motifs().size());
        for (int m = 0; m < motifs.size(); m++) {
            var o = NextflowFiles.object(motifs.get(m)); var actual = audit.motifs().get(m);
            assertEquals(o.get("id"), actual.raw().attribute("id")); assertEquals(integer(o.get("sites")), actual.sites().size());
            assertEquals(number(o.get("total_weight")), actual.totalWeight());
            var counts = list(o.get("independent_counts")); assertEquals(counts.size(), actual.weightedCounts().size());
            for (int i = 0; i < counts.size(); i++) for (int b = 0; b < 4; b++) assertEquals(number(list(counts.get(i)).get(b)), actual.weightedCounts().get(i).get(b));
            var score = NextflowFiles.object(o.get("score_model")); var model = actual.scoreModel();
            assertEquals(integer(score.get("offset")), model.offset());
            assertEquals(number(score.get("scale")), model.scale(), Math.abs(model.scale()) * 1e-12);
            var scaled = list(score.get("scaled")); int[][] matrix = model.integerMatrix();
            for (int i = 0; i < matrix.length; i++) for (int b = 0; b < 4; b++) assertEquals(integer(list(scaled.get(i)).get(b)), matrix[i][b]);
            var tails = list(score.get("tails")); double[] actualTails = model.tailProbabilities(); assertEquals(tails.size(), actualTails.length);
            for (int i = 0; i < tails.size(); i++) assertEquals(number(tails.get(i)), actualTails[i], Math.max(1e-323, Math.abs(number(tails.get(i))) * 2e-13));
            assertTrue(model.buildOperations() > 0);
            for (var site : actual.sites()) {
                String inputWord = expected.inputs().get(site.inputIndex()).bases().substring(site.start(), site.start() + model.width());
                assertEquals(model.score(inputWord, expected.bothStrands(), () -> { }), site.positionScore());
            }
        }
        var scans = list(oracle.get("scans")); assertEquals(expected.inputs().size(), audit.scans().size());
        for (int i = 0; i < scans.size(); i++) {
            var o = NextflowFiles.object(scans.get(i)); var actual = audit.scans().get(i); assertEquals(i, actual.inputIndex());
            assertEquals(number(o.get("combined_p")), actual.independent().combinedPValue(), Math.max(1e-323, number(o.get("combined_p")) * 2e-12));
            var selected = list(o.get("selected")); assertEquals(selected.size(), actual.independent().hits().size());
            for (int j = 0; j < selected.size(); j++) {
                var want = NextflowFiles.object(selected.get(j)); var hit = actual.independent().hits().get(j);
                assertEquals(integer(want.get("motif")), hit.motifIndex()); assertEquals(integer(want.get("start")), hit.start());
                assertEquals(integer(want.get("score")), hit.score()); assertEquals(want.get("reverse"), hit.reverse());
                assertEquals(number(want.get("p")), hit.pValue(), hit.pValue() * 2e-13);
            }
        }
        assertTrue(audit.operations() > 0);
        assertTrue(audit.warnings().stream().anyMatch(w -> w.contains("motif significance")));
        assertTrue(audit.warnings().stream().anyMatch(w -> w.contains("100-point lattice")));
        assertThrows(UnsupportedOperationException.class, () -> audit.motifs().clear());
        assertThrows(UnsupportedOperationException.class, () -> audit.motifs().get(0).weightedCounts().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> audit.scans().clear());
    }

    static Stream<String> corruptions() {
        return Stream.of("position", "strand", "sequence-reference", "name", "weight", "length", "positions-total", "sequence-order",
                "left-flank", "right-flank", "word", "site-p", "missing-site", "overlapping-site", "matrix", "negative-matrix", "large-matrix",
                "centibit", "matrix-order", "matrix-row-missing", "score-row-missing", "prior", "background-order", "background", "beta", "seed",
                "strands", "width", "requested-count", "motif-order", "missing-scan", "duplicate-scan", "scan-p", "scan-p-outside",
                "scan-strand", "scan-position", "scan-motif", "scan-hit-p", "scan-threshold", "scan-count", "NaN");
    }
    @ParameterizedTest @MethodSource("corruptions")
    void completeScientificMutationsRefuseAComponentReceipt(String kind) throws Exception {
        var raw = parsed(EDGE); Predicate<MemeXml.Node> target; UnaryOperator<MemeXml.Node> operation;
        switch (kind) {
            case "position" -> { target = n -> n.name().equals("contributing_site"); operation = n -> attribute(n, "position", "999999"); }
            case "strand" -> { target = n -> n.name().equals("contributing_site"); operation = n -> attribute(n, "strand", n.attributes().get("strand").equals("plus") ? "minus" : "plus"); }
            case "sequence-reference" -> { target = n -> n.name().equals("contributing_site"); operation = n -> attribute(n, "sequence_id", "sequence_999999"); }
            case "name" -> { target = n -> n.name().equals("sequence"); operation = n -> attribute(n, "name", "other-input"); }
            case "weight" -> { target = n -> n.name().equals("sequence"); operation = n -> attribute(n, "weight", "0.75"); }
            case "length" -> { target = n -> n.name().equals("sequence"); operation = n -> attribute(n, "length", "99"); }
            case "positions-total" -> { target = n -> n.name().equals("num_positions"); operation = n -> text(n, "1"); }
            case "sequence-order" -> { target = n -> n.name().equals("training_set"); operation = n -> { var c = new ArrayList<>(n.children()); int a = -1, b = -1; for (int i = 0; i < c.size(); i++) if (c.get(i).name().equals("sequence")) { if (a < 0) a = i; else { b = i; break; } } java.util.Collections.swap(c, a, b); return children(n, c); }; }
            case "left-flank", "right-flank" -> { target = n -> n.name().equals(kind.equals("left-flank") ? "left_flank" : "right_flank"); operation = n -> text(n, "AAAAAAAAAAA"); }
            case "word" -> { target = n -> n.name().equals("letter_ref"); operation = n -> attribute(n, "letter_id", "N"); }
            case "site-p" -> { target = n -> n.name().equals("contributing_site"); operation = n -> attribute(n, "pvalue", "0.5"); }
            case "missing-site" -> { target = n -> n.name().equals("contributing_sites"); operation = n -> children(n, n.children().subList(1, n.children().size())); }
            case "overlapping-site" -> { target = n -> n.name().equals("contributing_sites"); operation = n -> { var c = new ArrayList<>(n.children()); c.set(1, c.get(0)); return children(n, c); }; }
            case "matrix", "negative-matrix", "large-matrix" -> { target = n -> n.name().equals("probabilities"); operation = n -> editFirstValue(n, kind.equals("negative-matrix") ? "-0.0000001" : kind.equals("large-matrix") ? "1.0000001" : "0.5"); }
            case "centibit" -> { target = n -> n.name().equals("scores"); operation = n -> editFirstValue(n, "12345"); }
            case "matrix-order" -> { target = n -> n.name().equals("probabilities"); operation = n -> transform(n, x -> x.name().equals("alphabet_array"), x -> { var c = new ArrayList<>(x.children()); java.util.Collections.swap(c, 0, 1); return children(x, c); }, new AtomicBoolean()); }
            case "matrix-row-missing", "score-row-missing" -> { target = n -> n.name().equals(kind.equals("score-row-missing") ? "scores" : "probabilities"); operation = n -> transform(n, x -> x.name().equals("alphabet_matrix"), x -> children(x, x.children().subList(1, x.children().size())), new AtomicBoolean()); }
            case "prior" -> { target = n -> n.name().equals("prior"); operation = n -> text(n, "dmix"); }
            case "background-order" -> { target = n -> n.name().equals("background_frequencies"); operation = n -> attribute(n, "order", "1"); }
            case "background" -> { target = n -> n.name().equals("background_frequencies"); operation = n -> editFirstValue(n, "0.1"); }
            case "beta" -> { target = n -> n.name().equals("beta"); operation = n -> text(n, "0.02"); }
            case "seed" -> { target = n -> n.name().equals("seed"); operation = n -> text(n, "42"); }
            case "strands" -> { target = n -> n.name().equals("strands"); operation = n -> text(n, "forward"); }
            case "width" -> { target = n -> n.name().equals("motif"); operation = n -> attribute(n, "width", "11"); }
            case "requested-count" -> { target = n -> n.name().equals("nmotifs"); operation = n -> text(n, "3"); }
            case "motif-order" -> { target = n -> n.name().equals("motifs"); operation = n -> { var c = new ArrayList<>(n.children()); java.util.Collections.swap(c, 0, 1); return children(n, c); }; }
            case "missing-scan", "duplicate-scan" -> { target = n -> n.name().equals("scanned_sites_summary"); operation = n -> { var c = new ArrayList<>(n.children()); if (kind.equals("missing-scan")) c.remove(0); else c.add(c.get(0)); return children(n, c); }; }
            case "scan-p", "scan-p-outside" -> { target = n -> n.name().equals("scanned_sites"); operation = n -> attribute(n, "pvalue", kind.equals("scan-p") ? "0.5" : "1.00001"); }
            case "scan-strand", "scan-position", "scan-motif", "scan-hit-p" -> { target = n -> n.name().equals("scanned_site"); operation = n -> switch (kind) { case "scan-strand" -> attribute(n, "strand", n.attributes().get("strand").equals("plus") ? "minus" : "plus"); case "scan-position" -> attribute(n, "position", "99999"); case "scan-motif" -> attribute(n, "motif_id", "motif_99999"); default -> attribute(n, "pvalue", "0.5"); }; }
            case "scan-threshold" -> { target = n -> n.name().equals("scanned_sites_summary"); operation = n -> attribute(n, "p_thresh", "0.0002"); }
            case "scan-count" -> { target = n -> n.name().equals("scanned_sites"); operation = n -> attribute(n, "num_sites", "99999"); }
            case "NaN" -> { target = n -> n.name().equals("contributing_site"); operation = n -> attribute(n, "pvalue", "NaN"); }
            default -> throw new AssertionError(kind);
        }
        var touched = new AtomicBoolean(); var changed = transform(raw, target, operation, touched); assertTrue(touched.get(), kind);
        assertThrows(IOException.class, () -> MemeDiscoverySites.read(file(serialize(changed).getBytes(StandardCharsets.UTF_8)), expected(Data.FAMILIES.get(EDGE)), LIMITS, () -> { }), kind);
    }

    @Test void opaqueMotifSignificanceIsPreservedWithoutIndependentSignificanceClaim() throws Exception {
        var changed = transform(parsed(EDGE), n -> n.name().equals("motif"), n -> attribute(n, "e_value", "0.5"), new AtomicBoolean());
        var audit = MemeDiscoverySites.read(file(serialize(changed).getBytes(StandardCharsets.UTF_8)), expected(Data.FAMILIES.get(EDGE)), LIMITS, () -> { });
        assertEquals("0.5", audit.motifs().get(0).raw().attribute("e_value"));
        assertTrue(audit.warnings().stream().anyMatch(w -> w.contains("motif significance") && w.contains("separate")));
    }
    @Test void oopsAndZoopsConstraintsAreDistinctFromAnr() throws Exception {
        for (String name : List.of(EDGE, "discovery-original-5.5.9-1/meme-anr-both")) {
            var old = expected(Data.FAMILIES.get(name)); var mode = name.equals(EDGE) ? MemeDiscoverySites.Mode.OOPS : MemeDiscoverySites.Mode.ZOOPS;
            var want = new MemeDiscoverySites.Expected(old.version(), mode, old.bothStrands(), old.requestedMotifs(), old.minimumWidth(), old.maximumWidth(), old.seed(), old.beta(), old.background(), old.inputs());
            var changed = transform(parsed(name), n -> n.name().equals("type"), n -> text(n, mode.name().toLowerCase(java.util.Locale.ROOT)), new AtomicBoolean());
            assertThrows(IOException.class, () -> MemeDiscoverySites.read(file(serialize(changed).getBytes(StandardCharsets.UTF_8)), want, LIMITS, () -> { }));
        }
    }
    @Test void supportedEditionIsExplicitAndCannotBeInferredFromAPath() throws Exception {
        var old = expected(Data.FAMILIES.get(EDGE));
        for (String version : List.of("5.5.8", "4.9.1")) {
            var want = new MemeDiscoverySites.Expected(version, old.mode(), old.bothStrands(), old.requestedMotifs(), old.minimumWidth(), old.maximumWidth(), old.seed(), old.beta(), old.background(), old.inputs());
            assertThrows(IOException.class, () -> MemeDiscoverySites.read(file(xml(EDGE)), want, LIMITS, () -> { }));
        }
    }
    @Test void ambiguitiesAliasesAndCaseAreNeverSilentlyRepaired() throws Exception {
        var old = expected(Data.FAMILIES.get(EDGE));
        for (String replacement : List.of("N", "U", "a")) {
            var inputs = new ArrayList<>(old.inputs()); var first = inputs.get(0);
            inputs.set(0, new MemeDiscoverySites.Input(first.name(), replacement + first.bases().substring(1), first.weight()));
            var want = new MemeDiscoverySites.Expected(old.version(), old.mode(), old.bothStrands(), old.requestedMotifs(), old.minimumWidth(), old.maximumWidth(), old.seed(), old.beta(), old.background(), inputs);
            assertThrows(MemeDiscoverySites.UnsupportedInput.class, () -> MemeDiscoverySites.read(file(xml(EDGE)), want, LIMITS, () -> { }));
        }
    }
    @Test void completeFamilyBoundsNeverTruncate() throws Exception {
        var expected = expected(Data.FAMILIES.get(EDGE)); Path input = file(xml(EDGE));
        assertThrows(IOException.class, () -> MemeDiscoverySites.read(input, expected, new MemeDiscoverySites.Limits(LIMITS.xml(), LIMITS.scoring(), 1, 8000000, 1000000, 1000000000), () -> { }));
        assertThrows(IOException.class, () -> MemeDiscoverySites.read(input, expected, new MemeDiscoverySites.Limits(LIMITS.xml(), LIMITS.scoring(), 100000, 1, 1000000, 1000000000), () -> { }));
        assertThrows(IOException.class, () -> MemeDiscoverySites.read(input, expected, new MemeDiscoverySites.Limits(LIMITS.xml(), LIMITS.scoring(), 100000, 8000000, 1, 1000000000), () -> { }));
        assertThrows(IOException.class, () -> MemeDiscoverySites.read(input, expected, new MemeDiscoverySites.Limits(LIMITS.xml(), LIMITS.scoring(), 100000, 8000000, 1000000, 1), () -> { }));
    }
    @Test void sourceChangesDuringArithmeticCannotReturnAReceipt() throws Exception {
        Path input = file(xml(EDGE)); String text = Files.readString(input); FileTime time = Files.getLastModifiedTime(input);
        var initial = new AtomicInteger(); MemeXml.read(input, LIMITS.xml(), initial::incrementAndGet); var calls = new AtomicInteger();
        assertThrows(IOException.class, () -> MemeDiscoverySites.read(input, expected(Data.FAMILIES.get(EDGE)), LIMITS, () -> {
            if (calls.incrementAndGet() == initial.get() + 50) try {
                String changed = text.replace("User-PC", "Xser-PC"); assertNotEquals(text, changed); Files.writeString(input, changed); Files.setLastModifiedTime(input, time);
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        }));
    }
    @Test void canonicalScoreModelArraysAreDefensivelyOwnedAndTiesUsePlus() {
        int[][] source = new int[8][4]; for (int i = 0; i < source.length; i++) source[i] = new int[]{200, -1297, -1297, 200};
        double[] bg = {.25, .25, .25, .25}; var model = MemeDiscoveryScoring.build(source, bg, LIMITS.scoring(), () -> { });
        var before = model.score("AAAAAAAA", true, () -> { }); assertEquals(before.forward(), before.reverse()); assertFalse(before.reverseSelected());
        source[0][0] = -500; bg[0] = .5; model.integerMatrix()[0][0] = 0; model.tailProbabilities()[before.selected()] = .5; model.roundedBackground()[0] = .5;
        assertEquals(before, model.score("AAAAAAAA", true, () -> { }));
        var tiled = MemeDiscoveryScoring.tile("AAAAAAAA", List.of(model, model), true, LIMITS.scoring(), () -> { });
        assertTrue(tiled.hits().isEmpty()); // Two allowed letters per column have null tail 1/256, above 1e-4.
    }
    @Test void zeroInformationModelsAndShortSequencesRemainExplicit() {
        var model = MemeDiscoveryScoring.build(new int[4][4], new double[]{.25, .25, .25, .25}, LIMITS.scoring(), () -> { });
        assertEquals(1, model.score("ACGT", true, () -> { }).pValue()); assertEquals(0, model.scale());
        assertEquals(1, MemeDiscoveryScoring.tile("AC", List.of(model), true, LIMITS.scoring(), () -> { }).combinedPValue());
        assertThrows(IllegalArgumentException.class, () -> MemeDiscoveryScoring.build(new int[][]{{1, 1, 1, 1}}, new double[]{.25, .25, .25, .25}, LIMITS.scoring(), () -> { }));
    }
    @Test void equalSignificantOverlapsRetainTheEarlierMotifAndCoordinate() {
        int[][] scores = new int[8][4]; for (int i = 0; i < 8; i++) scores[i] = new int[]{200, -1297, -1297, -1297};
        var model = MemeDiscoveryScoring.build(scores, new double[]{.25, .25, .25, .25}, LIMITS.scoring(), () -> { });
        var result = MemeDiscoveryScoring.tile("AAAAAAAAA", List.of(model, model), false, LIMITS.scoring(), () -> { });
        assertEquals(1, result.hits().size()); assertEquals(0, result.hits().get(0).start()); assertEquals(0, result.hits().get(0).motifIndex());
    }
    @Test void positiveFactorsThatUnderflowKeepTheOriginalZeroAndWarningFlag() {
        int[][] scores = new int[10][4]; for (int i = 0; i < 10; i++) scores[i] = new int[]{200, -1297, -1297, -1297};
        var model = MemeDiscoveryScoring.build(scores, new double[]{.25, .25, .25, .25}, LIMITS.scoring(), () -> { });
        var result = MemeDiscoveryScoring.tile("AAAAAAAAAA", java.util.Collections.nCopies(100, model), false, LIMITS.scoring(), () -> { });
        assertTrue(result.productUnderflow()); assertEquals(0, result.combinedPValue()); assertEquals(1, result.hits().size());
    }
    @Test void scoringWorkMemoryAndCancellationBoundsAreEnforced() {
        int[][] scores = new int[10][4]; for (int i = 0; i < 10; i++) scores[i] = new int[]{200, -1297, -1297, -1297};
        var d = LIMITS.scoring();
        assertThrows(IllegalArgumentException.class, () -> MemeDiscoveryScoring.build(scores, new double[]{.25, .25, .25, .25}, new MemeDiscoveryScoring.Limits(1, d.maximumOperations(), 512, 1000, 1000000, 100000), () -> { }));
        assertThrows(IllegalArgumentException.class, () -> MemeDiscoveryScoring.build(scores, new double[]{.25, .25, .25, .25}, new MemeDiscoveryScoring.Limits(d.maximumWorkspaceBytes(), 1, 512, 1000, 1000000, 100000), () -> { }));
        var calls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> MemeDiscoveryScoring.build(scores, new double[]{.25, .25, .25, .25}, d, () -> { if (calls.incrementAndGet() == 3) throw new CancellationException("controlled"); }));
    }

    private Path file(byte[] bytes) throws IOException { Path p = temporary.resolve("case-" + serial++ + ".xml"); Files.write(p, bytes); return p; }
    private MemeXml.Node parsed(String name) throws IOException { return MemeXml.read(file(xml(name)), LIMITS.xml(), () -> { }).root(); }
    static byte[] xml(String name) { return Data.FILES.get("xml/" + name + ".xml"); }
    static MemeDiscoverySites.Expected expected(Map<?, ?> row) throws IOException {
        var o = NextflowFiles.object(row.get("expected")); var inputs = new ArrayList<MemeDiscoverySites.Input>();
        for (Object v : list(o.get("input_records"))) { var x = NextflowFiles.object(v); inputs.add(new MemeDiscoverySites.Input((String) x.get("name"), (String) x.get("bases"), number(x.get("weight")))); }
        double[] bg = list(o.get("resolved_background")).stream().mapToDouble(MemeDiscoverySitesTest::number).toArray();
        return new MemeDiscoverySites.Expected((String) o.get("version"), MemeDiscoverySites.Mode.valueOf(((String) o.get("mode")).toUpperCase(java.util.Locale.ROOT)),
                (Boolean) o.get("both_strands"), integer(o.get("motifs")), integer(o.get("minw")), integer(o.get("maxw")), integer(o.get("seed")), number(o.get("beta")), bg, inputs);
    }
    private static List<?> list(Object value) { if (value instanceof List<?> values) return values; throw new AssertionError("Require list"); }
    private static double number(Object value) { return ((Number) value).doubleValue(); }
    private static int integer(Object value) { return ((Number) value).intValue(); }
    private static MemeXml.Node attribute(MemeXml.Node n, String key, String value) { var a = new LinkedHashMap<>(n.attributes()); a.put(key, value); return new MemeXml.Node(n.name(), a, n.text(), n.children()); }
    private static MemeXml.Node text(MemeXml.Node n, String value) { return new MemeXml.Node(n.name(), n.attributes(), value, n.children()); }
    private static MemeXml.Node children(MemeXml.Node n, List<MemeXml.Node> value) { return new MemeXml.Node(n.name(), n.attributes(), n.text(), value); }
    private static MemeXml.Node editFirstValue(MemeXml.Node root, String value) { return transform(root, n -> n.name().equals("value"), n -> text(n, value), new AtomicBoolean()); }
    private static MemeXml.Node transform(MemeXml.Node n, Predicate<MemeXml.Node> predicate, UnaryOperator<MemeXml.Node> edit, AtomicBoolean applied) {
        if (!applied.get() && predicate.test(n)) { applied.set(true); return edit.apply(n); }
        var values = new ArrayList<MemeXml.Node>(); for (var child : n.children()) values.add(transform(child, predicate, edit, applied)); return children(n, values);
    }
    private static String escaped(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static String serialize(MemeXml.Node node) {
        var text = new StringBuilder("<" + node.name()); node.attributes().forEach((key, value) -> text.append(' ').append(key).append("=\"").append(escaped(value)).append('"'));
        text.append('>').append(escaped(node.text())); for (var child : node.children()) text.append(serialize(child)); return text.append("</").append(node.name()).append('>').toString();
    }
}
