/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jlinalg.workflow.NfcoreInputBindings;

/** Exercise the native planner against the unchanged production capture implementation. */
public final class NfcoreBindingsControl {
    private NfcoreBindingsControl() { }
    private static int checks;
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Binding contract check " + checks);
        checks++;
    }
    private static void refused(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) { checks++; return; }
        throw new AssertionError("Invalid binding was accepted");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--portable")) {
            var plan = NfcoreInputBindings.plan("/owned/session", "/producer/materialized");
            require(plan.captured("").equals("/owned/session/workspace/resources/r0/materialized"));
            require(plan.captured("reads/α's data.fastq.gz").endsWith("/reads/α's data.fastq.gz"));
            require(plan.original("sample sheet.csv").equals("/producer/materialized/sample sheet.csv"));
            require(NfcoreInputBindings.plan("/ab", "/a").captured("").equals("/ab/workspace/resources/r0/a"));
            for (String bad : new String[]{"/a/", "/a//b", "/a/./b", "/a/../b", "a", "/", "/a\nb", "/a\\b"})
                refused(() -> NfcoreInputBindings.plan("/session", bad));
            for (String bad : new String[]{"/session space", "/session,comma", "/session-α", "/a", "/a/b"})
                refused(() -> NfcoreInputBindings.plan(bad, "/a"));
            for (String bad : new String[]{"/absolute", "a/", "a//b", "a/./b", "a/../b", "a\tb", "a\\b"})
                refused(() -> plan.captured(bad));
            refused(() -> new NfcoreInputBindings.Layout("/owned/session", "/producer/materialized", "/forged"));
            refused(() -> NfcoreInputBindings.plan(null, "/source"));
            System.out.println("PASS_PORTABLE_BINDINGS " + checks);
            return;
        }
        Path owned = Path.of(args[0]);
        Path source = owned.resolveSibling(owned.getFileName() + "-source").resolve("materialized-α's data");
        Files.createDirectories(source.resolve("workflow/conf"));
        Files.createDirectories(source.resolve("inputs"));
        Files.writeString(source.resolve("workflow/main.nf"), "// control, not biological analysis\n");
        Path reads = source.resolve("inputs/α's reads.fastq.gz");
        Files.writeString(reads, "unchanged control bytes\n");
        var plan = NfcoreInputBindings.plan(owned.toString(), source.toString());
        Files.writeString(source.resolve("paths.txt"), plan.captured("inputs/α's reads.fastq.gz") + "\n");
        Files.createDirectory(owned);
        var limit = new NextflowCapture.Limits(100, 1048576, 1048576);
        var snapshot = NextflowCapture.create(owned, Map.of("bundle", source,
            "workflow", source.resolve("workflow"), "reads", reads, "sheet", source.resolve("paths.txt")), limit, () -> { });
        require(snapshot.roots().size() == 1);
        Path workspace = Files.createDirectory(owned.resolve("workspace"));
        var aliases = NextflowCapture.materialize(owned, snapshot, workspace.resolve("resources"), limit, () -> { });
        require(aliases.get("bundle").toString().equals(plan.captured("")));
        require(aliases.get("reads").toString().equals(plan.captured("inputs/α's reads.fastq.gz")));
        Path literalInput = Path.of(Files.readString(aliases.get("sheet")).stripTrailing());
        require(literalInput.equals(aliases.get("reads")));
        require(Files.readString(literalInput).equals(Files.readString(reads)));
        require(!Files.isSameFile(literalInput, reads));
        NextflowCapture.verifyOriginals(snapshot, limit, () -> { });
        System.out.println("PASS_REAL_FROZEN_CAPTURE_BINDINGS " + checks);
    }
}
