/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MemeDiscoveryContractsTest {
    @TempDir Path temporary;
    int serial;
    private MemeDiscoveryRequest.Request request(String profile, String fasta, String background) throws IOException {
        Path folder = Files.createDirectory(temporary.resolve("case-" + serial++));
        Path fa = folder.resolve("séquences original.fa"), bg = folder.resolve("background.bg");
        Files.writeString(fa, fasta, StandardCharsets.UTF_8); Files.writeString(bg, background, StandardCharsets.UTF_8);
        var raw = MemeDiscoveryRequest.template(profile, fa.toString(), bg.toString()); raw.put("minimum_width", 2); raw.put("maximum_width", 4);
        Path recipe = folder.resolve("recipe.json"); MemeRuntimeFiles.write(recipe, raw);
        return MemeDiscoveryRequest.read(recipe, false, () -> { });
    }
    private static final String FASTA = ">a description retained\nACGTACGT\n>b\nTGCATGCA\n";
    private static final String BG = "A 0.1\nC 0.2\nG 0.3\nT 0.4\n";
    static Stream<String> profiles() { return MemeDiscoveryRequest.PROFILES.stream(); }
    @ParameterizedTest @MethodSource("profiles") void exactEditionArgumentsNeverAlias(String profile) throws Exception {
        var r = request(profile, FASTA, BG);
        var args = r.argumentsRecorded("/private/home", "/private/input.fa", "/private/bg", "/private/new-result", 2);
        assertEquals("/private/home/bin/meme", args.get(0)); assertEquals("zoops", args.get(args.indexOf("-mod") + 1));
        assertEquals(r.legacy(), !args.contains("-seed")); assertEquals(r.legacy(), !args.contains("-searchsize"));
        assertEquals(r.legacy(), !args.contains("-brief"));
        if (!r.legacy()) assertEquals("2", args.get(args.indexOf("-brief") + 1));
        assertThrows(IOException.class, () -> r.argumentsRecorded("/private/with space", "/input", "/bg", "/out", 2));
    }
    @Test void captureAndPortableReplayKeepRawNamesWeightsAndBackground() throws Exception {
        String fasta = ">WEIGHTS 0.25 1\n>séq description\nACGTACGT\n>b\nTGCATGCA\n";
        var r = request("MEME_5_5_9", fasta, BG);
        var capture = MemeDiscoveryInputs.capture(temporary.resolve("captured"), r, () -> { });
        assertEquals(fasta, Files.readString(capture.primary(), StandardCharsets.UTF_8));
        assertEquals("séq", capture.population().records().get(0).name());
        assertEquals(0.25, capture.population().records().get(0).weight());
        assertArrayEquals(new double[]{0.25,0.25,0.25,0.25}, capture.population().background(), 1e-15);
        assertEquals(capture.population().records(), MemeDiscoveryInputs.open(capture.root(), r, () -> { }).population().records());
        Files.writeString(capture.primary(), ">changed\nACGTACGT\n>b\nTGCATGCA\n");
        assertThrows(IOException.class, () -> capture.verify(false, () -> { }));
        assertThrows(IOException.class, () -> MemeDiscoveryInputs.open(capture.root(), r, () -> { }));
    }
    static Stream<String> invalidFasta() { return Stream.of("", ">a\nACGTACGT\n", ">a\nACGT\n>a\nTGCA\n", ">a\nACGT\n>b\n\n",
        ">a\nACG\n>b\nTGCA\n", "ACGT\n>b\nTGCA\n", ">WEIGHTS\n" + FASTA, ">WEIGHTS 1\n" + FASTA,
        ">WEIGHTS 0 1\n" + FASTA, ">WEIGHTS -1 1\n" + FASTA, ">WEIGHTS 2 1\n" + FASTA,
        ">WEIGHTS 1 1 1\n" + FASTA, ">a\nACGT\n>WEIGHTS 1 1\n>b\nTGCA\n",
        ">WEIGHTS 1 1\n>WEIGHTS 1 1\n" + FASTA, "> a\nACGT\n>b\nTGCA\n",
        ">a\nACGT\rTGCA\n>b\nTGCA\n"); }
    @ParameterizedTest @MethodSource("invalidFasta") void invalidCompleteRecordFamiliesAreRefused(String fasta) throws Exception {
        var r = request("MEME_5_5_9", fasta, BG);
        assertThrows(IOException.class, () -> MemeDiscoveryInputs.read(Path.of(r.primary()), Path.of(r.background()), r, () -> { }));
    }
    static Stream<String> unsupportedResidues() { return Stream.of("acgt", "ACGN", "ACG-", "ACG T", "ACG\tT", "\ufeffACGT"); }
    @ParameterizedTest @MethodSource("unsupportedResidues") void noSilentResidueRewriting(String residues) throws Exception {
        var r = request("MEME_5_5_9", ">a\n" + residues + "\n>b\nTGCA\n", BG);
        assertThrows(MemeDiscoveryInputs.Unsupported.class, () -> MemeDiscoveryInputs.read(Path.of(r.primary()), Path.of(r.background()), r, () -> { }));
    }
    static Stream<String> invalidBackground() { return Stream.of("A 0 C 0.3 G 0.3 T 0.4", "A 0.1 C 0.2 G 0.3 T 0.3",
        "A 0.1 C 0.2 G 0.3 G 0.4", "A NaN C 0.2 G 0.3 T 0.4", "A 0.1 C 0.2 G 0.3 T 0.4 AA 0.01"); }
    @ParameterizedTest @MethodSource("invalidBackground") void backgroundNeverSilentlyNormalizesOrChangesOrder(String bg) throws Exception {
        var r = request("MEME_5_5_9", FASTA, bg);
        assertThrows(IOException.class, () -> MemeDiscoveryInputs.read(Path.of(r.primary()), Path.of(r.background()), r, () -> { }));
    }
    @Test void recipeChangeAndExtraKeysAreRefused() throws Exception {
        var r = request("MEME_5_5_9", FASTA, BG);
        String old = Files.readString(r.source()); Files.writeString(r.source(), old.replace("ZOOPS", "ANR"));
        assertThrows(IOException.class, () -> r.verify(() -> { }));
        Files.writeString(r.source(), old.replace("\"schema_version\"", "\"arbitrary_command\":\"touch /tmp/x\",\"schema_version\""));
        assertThrows(IOException.class, () -> MemeDiscoveryRequest.read(r.source(), false, () -> { }));
    }
    @Test void unknownOperationHasProminentFailureAndRecovery() throws Exception {
        Path path = temporary.resolve("operation.json"); MemeRuntimeFiles.write(path, Map.of("schema_version", 1, "operation", "SHELL"));
        var output = new ByteArrayOutputStream(); var errors = new ByteArrayOutputStream();
        assertEquals(2, MemeToolsCli.run(new String[]{"--request",path.toString()}, new PrintStream(output, true, StandardCharsets.UTF_8), new PrintStream(errors, true, StandardCharsets.UTF_8)));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("FAIL2: MEME NO RESULT ADMITTED"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("REQUIRED ACTION:")); assertEquals(0, output.size());
    }
    @Test void textReportedPossibleSitesPreserveOriginalMixedWeightFormula() {
        var records = java.util.List.of(new org.jlinalg.sequence.MemeDiscoverySites.Input("a", "A".repeat(96), 0.25),
            new org.jlinalg.sequence.MemeDiscoverySites.Input("b", "A".repeat(97), 0.5),
            new org.jlinalg.sequence.MemeDiscoverySites.Input("c", "A".repeat(98), 0.75),
            new org.jlinalg.sequence.MemeDiscoverySites.Input("d", "A".repeat(99), 1));
        assertEquals(209, MemeDiscoveryOutput.reportedPossibleSites(records, 10));
        assertNotEquals(222, MemeDiscoveryOutput.reportedPossibleSites(records, 10));
    }
    @Test void originalReportedPossibleSiteFloorIsTwo() {
        var records = java.util.List.of(new org.jlinalg.sequence.MemeDiscoverySites.Input("a", "ACGT", 0.001),
            new org.jlinalg.sequence.MemeDiscoverySites.Input("b", "TGCA", 0.001));
        assertEquals(2, MemeDiscoveryOutput.reportedPossibleSites(records, 4));
    }
    static Stream<String> protectedTrees() { return Stream.of("installation", "build-evidence"); }
    @ParameterizedTest @MethodSource("protectedTrees") void outputCannotWriteIntoManagedSourceTrees(String kind) throws Exception {
        var request = request("MEME_5_5_9", FASTA, BG);
        Path protectedRoot = Files.createDirectory(temporary.resolve("protected"));
        Path sentinel = protectedRoot.resolve("sentinel"); Files.writeString(sentinel, "original source evidence\n");
        Path output = protectedRoot.resolve("must-not-create");
        Path home = kind.equals("installation") ? protectedRoot : temporary.resolve("missing-home");
        Path evidence = kind.equals("build-evidence") ? protectedRoot : temporary.resolve("missing-build");
        assertThrows(IOException.class, () -> MemeDiscoveryWorkflow.run(request.source(), home, evidence, output, () -> { }, () -> { }));
        assertFalse(Files.exists(output)); assertEquals("original source evidence\n", Files.readString(sentinel));
    }
    @Test void healthProjectionKeepsExplicitAnnotationAndDropsOnlyTiming() throws Exception {
        var node = new org.jlinalg.sequence.MemeXml.Node("motif", Map.of("url", "https://example.invalid/motif", "p_value", "1e-20", "elapsed_time", "12.3"), "", java.util.List.of());
        var projected = NextflowFiles.object(MemeDiscoveryOutput.node(node).get("attributes"));
        assertEquals(Map.of("url", "https://example.invalid/motif", "p_value", "1e-20"), projected);
    }
    @ParameterizedTest @MethodSource("profiles") void completeOriginalHealthReferencesMatchExplicitXmlFields(String profile) throws Exception {
        Path primary = temporary.resolve("primary.fa"), background = temporary.resolve("background.bg"), source = temporary.resolve("health.json");
        Files.write(primary, MemeRuntimeFiles.resource(MemeDiscoveryHealth.RESOURCE + "health-primary.fa", MemeDiscoveryHealth.PRIMARY));
        Files.write(background, MemeRuntimeFiles.resource(MemeDiscoveryHealth.RESOURCE + "health-background.bg", MemeDiscoveryHealth.BACKGROUND));
        MemeRuntimeFiles.write(source, MemeDiscoveryHealth.recipe(profile, primary.toString(), background.toString()));
        var request = MemeDiscoveryRequest.read(source, false, () -> { });
        var inputs = MemeDiscoveryInputs.capture(temporary.resolve("inputs"), request, () -> { });
        byte[] original;
        try (var in = getClass().getResourceAsStream("/org/jlinalg/sequence/meme-discovery-sites/original-site-contracts.zip")) {
            assertNotNull(in); original = in.readNBytes(750721);
        }
        assertEquals(750720, original.length); assertEquals("111d5adedd0dadadb08744dcf83175554864613341ec71cfdb6614d2cef0d816", NextflowFiles.sha(original));
        String edition = profile.equals("MEME_4_9_1_BASE") ? "4.9.1-base" : profile.equals("MEME_4_9_1_PATCH2") ? "4.9.1-patch2" : request.version();
        String member = "xml/sites-original-" + edition + "-1/edge-both.xml";
        Path xml = temporary.resolve("original.xml");
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(original))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) if (entry.getName().equals(member)) { Files.write(xml, zip.readAllBytes()); break; }
        }
        assertTrue(Files.isRegularFile(xml));
        var audit = org.jlinalg.sequence.MemeDiscoverySites.read(xml, inputs.expected(), request.outputLimits(), () -> { });
        var references = NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(MemeRuntimeFiles.resource(MemeDiscoveryHealth.RESOURCE + "health-reference.json", MemeDiscoveryHealth.REFERENCE))));
        var expected = NextflowFiles.object(references.get(profile)).get("scientific_projection");
        assertEquals(NextflowJson.json(expected), NextflowJson.json(MemeDiscoveryOutput.projection(audit)));
    }

    @ParameterizedTest @MethodSource("profiles") void cutoffAndEffectiveMotifLimitRequireAnExplicitSourceChoice(String profile) throws Exception {
        var original = request(profile, FASTA, BG);
        var raw = MemeDiscoveryRequest.template(profile, original.primary(), original.background());
        raw.put("maximum_motif_evalue", new java.math.BigDecimal("1e-300"));
        Files.writeString(original.source(), NextflowJson.json(raw));
        if (original.legacy()) assertEquals(2, MemeDiscoveryRequest.read(original.source(), false, () -> { }).motifs());
        else {
            assertTrue(assertThrows(IOException.class, () -> MemeDiscoveryRequest.read(original.source(), false, () -> { })).getMessage().contains("overrides -nmotifs to 1000"));
            raw.put("motifs", 1000); Files.writeString(original.source(), NextflowJson.json(raw));
            assertEquals(1000, MemeDiscoveryRequest.read(original.source(), false, () -> { }).motifs());
        }
    }

    private record EmptyFixture(Path root, MemeDiscoveryRequest.Request request, MemeDiscoveryInputs.Captured inputs, List<String> command) { }
    private EmptyFixture emptyFixture(String profile) throws Exception {
        byte[] bytes;
        try (var in = getClass().getResourceAsStream("/org/jlinalg/sequence/meme-discovery-empty/original-empty-contracts.zip")) {
            assertNotNull(in); bytes = in.readNBytes(4194305);
        }
        assertEquals(254359, bytes.length); assertEquals("a0fdddbf140b42d049d3284371aecb57633aa363763707686ee96a551efefe26", NextflowFiles.sha(bytes));
        Path destination = Files.createDirectory(temporary.resolve("empty-" + serial++));
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.getName().startsWith(profile + "/")) continue;
                Path path = destination.resolve(entry.getName()).normalize(); assertTrue(path.startsWith(destination));
                Files.createDirectories(path.getParent()); Files.write(path, zip.readAllBytes());
            }
        }
        Path root = destination.resolve(profile);
        var recipe = MemeDiscoveryRequest.read(root.resolve("prepared/request.json"), true, () -> { });
        var inputs = MemeDiscoveryInputs.open(root.resolve("prepared/inputs"), recipe, () -> { });
        var trace = NextflowFiles.object(NextflowFiles.json(root.resolve("TRACE.json"), 2097152));
        var command = new ArrayList<String>(); for (Object value : NextflowFiles.list(trace.get("command"))) command.add(NextflowFiles.text(value));
        return new EmptyFixture(root.resolve("process/result"), recipe, inputs, command);
    }
    @ParameterizedTest @MethodSource("profiles") void originalCompleteEmptyFamiliesAreAcceptedWithoutInventedScores(String profile) throws Exception {
        var fixture = emptyFixture(profile);
        var audit = MemeDiscoveryOutput.read(fixture.root(), fixture.request(), fixture.inputs(), fixture.command(), () -> { });
        assertTrue(audit.scientific().motifs().isEmpty()); assertTrue(audit.scientific().scans().isEmpty());
        assertEquals(0, audit.document().get("motifs"));
        assertEquals(false, MemeDiscoveryOutput.projection(audit.scientific()).get("scanned_sites_summary_present"));
        assertTrue(audit.warnings().stream().anyMatch(w -> w.contains("not evidence that binding sites")));
    }
    static Stream<org.junit.jupiter.params.provider.Arguments> emptyMutations() {
        return profiles().flatMap(p -> Stream.of("model", "training", "prior", "reason", "diagram", "motif", "text-matrix", "text-reason", "html", "extra-figure")
            .map(m -> org.junit.jupiter.params.provider.Arguments.of(p, m)));
    }
    @ParameterizedTest @MethodSource("emptyMutations") void emptyAdmissionCannotBypassScienceOrCarrierChecks(String profile, String mutation) throws Exception {
        var f = emptyFixture(profile); Path path = f.root().resolve("meme.xml"); String original = Files.readString(path), changed;
        switch (mutation) {
            case "model" -> changed = original.replaceAll("<strands>\\s*both\\s*</strands>", "<strands>forward</strands>");
            case "training" -> changed = original.replaceFirst("(<sequence\\s[^>]*length=\")[0-9]+", "$1999");
            case "prior" -> changed = original.replaceAll("<prior>\\s*dirichlet\\s*</prior>", "<prior>unsupported</prior>");
            case "reason" -> changed = original.replace("1.00e-300", "2.00e-300");
            case "diagram" -> changed = original.replace("</MEME>", "<scanned_sites_summary/></MEME>");
            case "motif" -> changed = original.replace("</motifs>", "<motif/></motifs>");
            case "text-matrix", "text-reason" -> {
                path = f.root().resolve("meme.txt"); original = Files.readString(path);
                changed = mutation.equals("text-matrix") ? original.replace("CPU:", "letter-probability matrix: alength= 4 w= 1\nCPU:") : original.replace("1.00e-300", "2.00e-300");
            }
            case "html" -> { path = f.root().resolve("meme.html"); original = Files.readString(path); changed = original.substring(0, original.length()/2); }
            case "extra-figure" -> { path = f.root().resolve("logo1.eps"); original = ""; changed = "unexpected figure\n"; }
            default -> throw new AssertionError(mutation);
        }
        assertNotEquals(original, changed, "Control must actually mutate its original carrier"); Files.writeString(path, changed);
        assertThrows(IOException.class, () -> MemeDiscoveryOutput.read(f.root(), f.request(), f.inputs(), f.command(), () -> { }));
    }
}
