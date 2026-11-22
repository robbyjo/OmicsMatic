/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.sequence.FlashFastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlashReadsCliTest {
    @TempDir Path root;
    private String console;
    private static String record(String id, String sequence) { return "@" + id + "\n" + sequence + "\n+\n" + "I".repeat(sequence.length()) + "\n"; }
    private int run(String name, String... extra) {
        var args = new ArrayList<>(List.of("flash-reads", "--reads", root.resolve("r1.fq").toString(), "--reads2", root.resolve("r2.fq").toString(), "--phred-offset", "33", "--out", root.resolve(name).toString()));
        args.addAll(List.of(extra)); return execute(args.toArray(String[]::new));
    }
    private int execute(String... args) {
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        int code = JLinAlgCli.run(args, stream, stream); console = bytes.toString(StandardCharsets.UTF_8); return code;
    }
    private void pair(String id1, String id2) throws Exception {
        Files.writeString(root.resolve("r1.fq"), record(id1, "CTGACTGCTACGTGTCACAGACGTG"));
        Files.writeString(root.resolve("r2.fq"), record(id2, "GGTCAGTTCATCACGTCTGTGACAC"));
    }

    @Test void actualGoldenMergeKeepsBothPartitionsProvenanceAndActionableHeaderMapping() throws Exception {
        pair("A/1 comment", "A/2 comment");
        Files.writeString(root.resolve("r1.fq"), record("B/1", "A".repeat(25)), java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(root.resolve("r2.fq"), record("B/2", "A".repeat(25)), java.nio.file.StandardOpenOption.APPEND);
        assertEquals(0, run("golden", "--max-overlap", "200"), console);
        try (var reader = new FlashFastqIO.Reader(root.resolve("golden/merged.fastq.gz"), 33)) {
            var value = reader.next(); assertEquals("@A", value.header()); assertEquals("CTGACTGCTACGTGTCACAGACGTGATGAACTGACC", value.sequence()); assertNull(reader.next());
        }
        try (var reader = new FlashFastqIO.Reader(root.resolve("golden/unmerged_R1.fastq.gz"), 33)) { assertEquals("@B/1", reader.next().header()); assertNull(reader.next()); }
        assertTrue(Files.readString(root.resolve("golden/results.tsv")).contains("merged_pairs\t1"));
        assertTrue(Files.readString(root.resolve("golden/length-histogram.tsv")).contains("36\t1\t1\t0"));
        String ledger = Files.readString(root.resolve("golden/read-status.tsv")); assertTrue(ledger.contains("\"@A/1 comment\"\t\"@A/2 comment\"\t\"@A\"\tINNIE")); assertTrue(ledger.contains("UNMERGED"));
        assertTrue(Files.readString(root.resolve("golden/manifest.yaml")).contains("input_sha256"));
        assertTrue(Files.readString(root.resolve("golden/session.yaml")).contains("maximum_scoring_overlap: 200"));
        assertTrue(Files.readString(root.resolve("golden/WARNINGS.md")).contains("Required action")); assertTrue(console.contains("QC WARNINGS"));
    }

    @Test void normalizationBarcodeWhitespaceAndIgnoredPlusIdsAreCounted() throws Exception {
        Files.writeString(root.resolve("r1.fq"), "@id/1#BC comment \t\nacGTnRU?. \t\n+different\nIIIIIIIII \t\n");
        Files.writeString(root.resolve("r2.fq"), record("id/2#BC comment", "NNNNNACGT"));
        assertEquals(0, run("cleanup", "--min-overlap", "4", "--gzip", "false"), console);
        try (var reader = new FlashFastqIO.Reader(root.resolve("cleanup/merged.fastq"), 33)) { assertEquals("@id#BC comment", reader.next().header()); }
        String warnings = Files.readString(root.resolve("cleanup/WARNINGS.md"));
        assertTrue(warnings.contains("converted 5 other ASCII characters")); assertTrue(warnings.contains("trailing ASCII whitespace")); assertTrue(warnings.contains("repeated plus identifiers"));
        assertTrue(Files.readString(root.resolve("cleanup/results.tsv")).contains("case_changes\t2"));
    }

    @Test void strictMismatchQuarantinesAllScientificOutputs() throws Exception {
        pair("A/1", "wrong/2"); assertEquals(2, run("bad-pair"));
        assertFalse(Files.exists(root.resolve("bad-pair/results.tsv"))); assertFalse(Files.exists(root.resolve("bad-pair/merged.fastq.gz")));
        assertTrue(Files.exists(root.resolve("bad-pair/partial/merged.fastq.gz"))); assertTrue(Files.exists(root.resolve("bad-pair/partial/read-status.tsv")));
        assertTrue(Files.readString(root.resolve("bad-pair/WARNINGS.md")).contains("identity/order mismatch"));
        assertTrue(Files.readString(root.resolve("bad-pair/analysis-status.json")).contains("failed")); assertTrue(console.contains("FAILED"));
    }

    @Test void originalPositionalPairingRequiresExplicitChoiceAndProminentAction() throws Exception {
        pair("A/1", "wrong/2"); assertEquals(0, run("positional", "--pair-policy", "positional"), console);
        assertTrue(Files.readString(root.resolve("positional/WARNINGS.md")).contains("1 pairs had different normalized identifiers"));
        assertTrue(Files.readString(root.resolve("positional/results.tsv")).contains("pair_identity_mismatches\t1"));
    }

    @Test void emptyAndUnmergeableInputsHaveDifferentFailureAndWarningStates() throws Exception {
        Files.writeString(root.resolve("r1.fq"), ""); Files.writeString(root.resolve("r2.fq"), ""); assertEquals(2, run("empty"));
        assertFalse(Files.exists(root.resolve("empty/results.tsv")));
        Files.writeString(root.resolve("r1.fq"), record("A/1", "AAAA")); Files.writeString(root.resolve("r2.fq"), record("A/2", "AAAA"));
        assertEquals(0, run("none"), console); assertTrue(Files.readString(root.resolve("none/WARNINGS.md")).contains("No read pairs merged"));
        assertTrue(Files.readString(root.resolve("none/analysis-status.json")).contains("completed-with-qc-warnings"));
    }

    @Test void extraMateAndInvalidQualityArePersistedFailures() throws Exception {
        pair("A/1", "A/2"); Files.writeString(root.resolve("r2.fq"), record("B/2", "ACGT"), java.nio.file.StandardOpenOption.APPEND);
        assertEquals(2, run("extra")); assertTrue(Files.readString(root.resolve("extra/WARNINGS.md")).contains("extra records"));
        pair("A/1", "A/2"); Files.writeString(root.resolve("r1.fq"), "@A/1\nACGT\n+\n!III\n");
        assertEquals(2, execute("flash-reads", "--reads", root.resolve("r1.fq").toString(), "--reads2", root.resolve("r2.fq").toString(), "--phred-offset", "64", "--out", root.resolve("encoding").toString()));
        assertTrue(Files.readString(root.resolve("encoding/WARNINGS.md")).contains("Phred+64"));
    }

    @Test void interleavedGzipAndDeclaredPhred64WorkAndOddMateFails() throws Exception {
        Path input = root.resolve("interleaved.gz");
        try (var writer = new FlashFastqIO.Writer(input, 64)) { writer.write("@A/1", "ACGTACGTACGT", "@I~IIIIIIIII"); writer.write("@A/2", "ACGTACGTACGT", "IIIIIIIIIIII"); }
        assertEquals(0, execute("flash-reads", "--reads", input.toString(), "--interleaved", "true", "--phred-offset", "64", "--allow-outies", "true", "--cap-mismatch-quals", "true", "--out", root.resolve("interleaved").toString()), console);
        assertTrue(Files.readString(root.resolve("interleaved/results.tsv")).contains("merged_pairs\t1"));
        Files.writeString(root.resolve("odd.fq"), record("A/1", "ACGT"));
        assertEquals(2, execute("flash-reads", "--reads", root.resolve("odd.fq").toString(), "--interleaved", "true", "--phred-offset", "33", "--out", root.resolve("odd").toString()));
        assertTrue(Files.readString(root.resolve("odd/WARNINGS.md")).contains("Missing second mate"));
    }

    @Test void optionsRecipePreservesUnicodePathsHeadersSnapshotAndExplicitOverrides() throws Exception {
        Path a = root.resolve("汉一.fq"), b = root.resolve("汉二.fq"), destination = root.resolve("结果");
        Files.writeString(a, record("汉/1", "ACGTACGTACGT")); Files.writeString(b, record("汉/2", "ACGTACGTACGT"));
        Path recipe = root.resolve("recipe.json");
        Files.writeString(recipe, RegenieRuntime.json(java.util.Map.of("reads", a.toString(), "reads2", b.toString(), "phred-offset", "33", "out", destination.toString(), "max-overlap", "200", "gzip", "true")));
        assertEquals(0, execute("flash-reads", "--options-file", recipe.toString(), "--gzip", "false"), console);
        assertEquals(Files.readString(recipe), Files.readString(destination.resolve("options-input.json")));
        try (var reader = new FlashFastqIO.Reader(destination.resolve("merged.fastq"), 33)) { assertEquals("@汉", reader.next().header()); }
        assertTrue(Files.readString(destination.resolve("manifest.yaml")).contains("recipe.json"));
        Files.writeString(recipe, "{\"unknown\":\"x\"}"); assertEquals(2, execute("flash-reads", "--options-file", recipe.toString())); assertTrue(console.contains("Unknown"));
        Files.writeString(recipe, "{\"phred-offset\":33}"); assertEquals(2, execute("flash-reads", "--options-file", recipe.toString()));
    }

    @Test void derivedCapUsesOriginalDefaultsAndWarnsWhenExplicitCapOverrides() throws Exception {
        pair("A", "A"); assertEquals(0, run("derived", "--read-length", "100", "--fragment-length", "180", "--fragment-length-sd", "20"), console);
        assertTrue(Files.readString(root.resolve("derived/session.yaml")).contains("maximum_scoring_overlap: 70"));
        assertEquals(0, run("override", "--max-overlap", "200", "--read-length", "100"), console);
        assertTrue(Files.readString(root.resolve("override/WARNINGS.md")).contains("overrides all fragment/read length"));
        assertEquals(2, run("bad-cap", "--min-overlap", "11", "--max-overlap", "10"));
        assertFalse(Files.exists(root.resolve("bad-cap/results.tsv")));
    }

    @Test void refusesExistingDestinationDuplicateOptionsAndMissingEncodingWithoutOverwrite() throws Exception {
        pair("A", "A"); Path existing = root.resolve("existing"); Files.createDirectory(existing); Files.writeString(existing.resolve("keep"), "sentinel");
        assertEquals(2, run("existing")); assertEquals("sentinel", Files.readString(existing.resolve("keep")));
        assertEquals(2, run("duplicate", "--phred-offset", "64")); assertFalse(Files.exists(root.resolve("duplicate")));
        assertEquals(2, execute("flash-reads", "--reads", root.resolve("r1.fq").toString(), "--reads2", root.resolve("r2.fq").toString(), "--out", root.resolve("no-encoding").toString()));
        assertTrue(Files.readString(root.resolve("no-encoding/WARNINGS.md")).contains("phred-offset is required"));
        assertEquals(0, execute("flash-reads", "--help")); assertTrue(console.contains("CAPS THE SCORE DENOMINATOR"));
    }
}
