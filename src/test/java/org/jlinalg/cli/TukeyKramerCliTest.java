/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TukeyKramerCliTest {
    @TempDir Path root;
    private static PrintStream stream(ByteArrayOutputStream bytes) { return new PrintStream(bytes, true, StandardCharsets.UTF_8); }
    private Path input(String text) throws IOException { Path path = root.resolve("input.tsv"); Files.writeString(path, text, StandardCharsets.UTF_8); return path; }
    private static String data() { return "group\tvalue\nB\t2\nB\t3\nA\t4\nA\t6\nC\t7\nC\t9\n"; }
    private int run(Path source, Path out, ByteArrayOutputStream errors) {
        return JLinAlgCli.run(new String[]{"posthoc-tools", "tukey", "--input", source.toString(), "--out", out.toString()}, stream(new ByteArrayOutputStream()), stream(errors));
    }

    @Test void rootDispatchHelpExplainsModelScopeAndRecovery() {
        var bytes = new ByteArrayOutputStream();
        assertEquals(0, JLinAlgCli.run(new String[]{"posthoc-tools", "--help"}, stream(bytes), stream(new ByteArrayOutputStream())));
        String text = bytes.toString(StandardCharsets.UTF_8);
        for (String required : new String[]{"Tukey-Kramer", "Gaussian", "common population variance", "one-way", "SKIP3", "FAIL2", "Prism are not required"}) assertTrue(text.contains(required), required);
    }

    @Test void successfulRunPreservesLiteralLabelsInputAndCompleteFamily() throws IOException {
        Path source = input("\ufeff" + data().replace("B\t", " Bα\t")); Path out = root.resolve("out");
        assertEquals(0, run(source, out, new ByteArrayOutputStream()));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(out.resolve("input.tsv")));
        assertTrue(Files.readString(out.resolve("groups.tsv")).startsWith("index\tgroup\tn\tmean\n0\t Bα\t"));
        assertEquals(4, Files.readAllLines(out.resolve("comparisons.tsv")).size());
        assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>) SimpleJson.parse(Files.readString(out.resolve("analysis.json")))).get("utf8_bom_present"));
        assertTrue(Files.readString(out.resolve("COMPLETED.json")).contains("PASS_NATIVE_TUKEY_KRAMER"));
    }

    @Test void missingDataRejectsWholeFamilyAndRetainsRawInput() throws IOException {
        Path source = input(data() + "C\tNaN\n"); Path out = root.resolve("failed");
        assertEquals(2, run(source, out, new ByteArrayOutputStream()));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(out.resolve("input.tsv")));
        assertFalse(Files.exists(out.resolve("COMPLETED.json")));
        assertFalse(Files.exists(out.resolve("comparisons.tsv")));
        assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("missing values are not removed"));
    }

    @Test void missingInputSkipsAndExistingOutputIsPreserved() throws IOException {
        var errors = new ByteArrayOutputStream(); Path out = root.resolve("missing");
        assertEquals(3, run(root.resolve("absent"), out, errors)); assertFalse(Files.exists(out));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("SKIP3"));
        Files.createDirectory(out); Files.writeString(out.resolve("sentinel"), "retain");
        assertEquals(2, run(input(data()), out, new ByteArrayOutputStream()));
        assertEquals("retain", Files.readString(out.resolve("sentinel")));
        try (var members = Files.list(out)) { assertEquals(1, members.count()); }
    }

    @Test void malformedUtf8HasAnActionableFailureAndNoCompletion() throws IOException {
        Path source = root.resolve("invalid.tsv"); Files.write(source, new byte[]{(byte) 0xff}); Path out = root.resolve("out");
        var errors = new ByteArrayOutputStream(); assertEquals(2, run(source, out, errors));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("not valid UTF-8"));
        assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }

    @Test void groupCapacityRefusesTheWholeCapturedInput() throws IOException {
        StringBuilder text = new StringBuilder("group\tvalue\n");
        for (int i = 0; i < 257; i++) text.append("g").append(i).append("\t").append(i).append('\n');
        Path source = input(text.toString()); Path out = root.resolve("out");
        assertEquals(2, run(source, out, new ByteArrayOutputStream()));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(out.resolve("input.tsv")));
        assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("256 groups"));
        assertFalse(Files.exists(out.resolve("COMPLETED.json")));
    }

    @Test void failedConsoleRecordingPreventsCompletion() throws IOException {
        Path source = input(data()); Path out = root.resolve("out");
        var broken = new PrintStream(new OutputStream() { @Override public void write(int value) throws IOException { throw new IOException("recording fixture"); } });
        assertEquals(2, TukeyKramerCli.run(new String[]{"tukey", "--input", source.toString(), "--out", out.toString()}, broken, stream(new ByteArrayOutputStream())));
        assertFalse(Files.exists(out.resolve("COMPLETED.json")));
        assertTrue(Files.readString(out.resolve("FAILURE.txt")).contains("Console recording failed"));
    }
}
