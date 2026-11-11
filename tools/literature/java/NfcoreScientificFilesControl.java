/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.sequence.FastqIO;

/** Actual verified capture: distinguish equal bytes from original physical file identity. */
public final class NfcoreScientificFilesControl {
    private NfcoreScientificFilesControl() { }
    private static int checks;
    private static void require(boolean value) {
        if (!value) throw new AssertionError("Scientific-file contract " + checks);
        checks++;
    }
    @FunctionalInterface private interface Checked { void run() throws IOException; }
    private static void refused(Checked action) throws IOException {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError("Invalid scientific-file intake accepted");
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Require actual complete engine attempt and new view path");
        var verified = NfcoreEngineBundle.verify(Path.of(args[0]), () -> { });
        var files = new NfcoreScientificFiles(verified);
        String producer = NextflowFiles.text(NextflowFiles.object(NextflowFiles.json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root"));
        String prefix = producer + "/workspace/resources/r0/materialized/identity/";
        String first = prefix + "a/reads.fq.gz", second = prefix + "b/reads.fq.gz";
        require(files.inputPath(first).equals(files.inputPath(second))); // one content-addressed object
        require(!files.sameInputFile(first, second)); // two original files
        require(files.sameInputFile(first, prefix + "a/alias.fq.gz"));
        require(files.sameInputFile(first, prefix + "a/hard.fq.gz"));
        require(files.sameInputFile(first, first));
        Path views = Files.createDirectory(Path.of(args[1])); long[] copied = {0};
        Path a = files.namedFastq(first, views, copied, 65536), b = files.namedFastq(second, views, copied, 65536);
        require(a.getFileName().toString().equals("reads.fq.gz") && b.getFileName().equals(a.getFileName()));
        require(!Files.isSameFile(a, b));
        require(java.util.Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b)));
        require(FastqIO.inspect(a, b).read1().reads() == 1);
        require(copied[0] == 2 * Files.size(files.inputPath(first)));
        require(files.namedFastq(first, views, copied, 65536).equals(a));
        require(copied[0] == 2 * Files.size(a));
        refused(() -> files.inputPath("/etc/passwd"));
        refused(() -> files.inputPath(prefix + "../escape.fq.gz"));
        refused(() -> files.inputPath(prefix + "missing.fq.gz"));
        Path low = Files.createDirectory(views.resolve("low-capacity"));
        refused(() -> files.namedFastq(first, low, new long[]{0}, 1));
        try (var children = Files.walk(low)) { require(children.noneMatch(Files::isRegularFile)); }
        require(NfcoreEngineBundle.verify(Path.of(args[0]), () -> { }).manifest().equals(verified.manifest()));
        System.out.println("PASS " + checks + " actual complete-capture scientific-file checks");
    }
}
