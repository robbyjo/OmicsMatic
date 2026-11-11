/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.NextflowFiles.*;

/** Resolve complete immutable v2 task/input/publication objects, never producer work paths. */
final class NfcoreChipseq200Files {
    record Task(String process, String tag, String status, String directory) { }
    final NfcoreScientificFiles files;
    private final NfcoreEngineBundle.Verified verified;
    private final NextflowCapture.Snapshot working;
    private final List<Task> tasks;
    NfcoreChipseq200Files(NfcoreEngineBundle.Verified verified) throws IOException {
        this.verified = verified; files = new NfcoreScientificFiles(verified); working = NextflowCapture.decode(json(verified.attempt().resolve("working-after.json"), 67108864), new NextflowCapture.Limits(1000000, 1000000000000L, 1000000000000L));
        String producer = text(object(json(verified.attempt().resolve("session.json"), 67108864)).get("producer_root")); var rows = utf8(bytes(verified.attempt().resolve("trace.tsv"), 67108864)).lines().toList();
        if (rows.isEmpty() || !rows.get(0).equals("task_id\thash\tname\tstatus\texit\tattempt\tprocess\ttag\tworkdir")) throw new IOException("Complete original v2 trace contract differs");
        var result = new ArrayList<Task>();
        for (String row : rows.subList(1, rows.size())) {
            String[] f = row.split("\t", -1); if (f.length != 9 || row.length() > 1048576) throw new IOException("Malformed complete v2 task row");
            if (!f[3].equals("COMPLETED") && !f[3].equals("CACHED")) continue;
            if (!f[4].equals("0") || !f[8].startsWith(producer + "/workspace/execution/work/")) throw new IOException("Completed task exit/work binding differs");
            result.add(new Task(f[6], f[7], f[3], f[8]));
        }
        if (result.isEmpty()) throw new IOException("No original v2 tasks observed"); tasks = List.copyOf(result);
    }
    List<Task> tasks() { return tasks; }
    Task task(String suffix, String tag) throws IOException { Task found = null; for (var task : tasks) if (task.process().endsWith(":" + suffix) && task.tag().equals(tag)) { if (found != null) throw new IOException("Ambiguous original task for " + suffix + " / " + tag); found = task; } if (found == null) throw new IOException("Missing complete original task for " + suffix + " / " + tag); return found; }
    Path work(Task task, String member) throws IOException { return workPath(task.directory() + "/" + member(member)); }
    private Path workPath(String path) throws IOException {
        var seen = new HashSet<String>();
        for (int hop = 0; hop < 1000; hop++) {
            if (!seen.add(path)) throw new IOException("Cyclic original working link"); NextflowCapture.Item item = null;
            for (var root : working.roots()) if (path.startsWith(root.source() + "/")) { item = root.items().get(member(path.substring(root.source().length() + 1))); break; }
            if (item == null) throw new IOException("Complete immutable working member is absent: " + path);
            if (item.kind().equals("symlink")) { if (item.resolved() == null) throw new IOException("Unresolved original working link"); path = item.resolved(); continue; }
            if (!item.kind().equals("file") || item.original() == null) throw new IOException("Original working member is not a complete regular file");
            Pin expected = item.original().pin(); Path file = verified.attempt().resolve("objects").resolve(expected.sha256()); if (!pin(file, 1000000000000L, () -> { }).equals(expected)) throw new IOException("Immutable original working object changed"); return file;
        }
        throw new IOException("Original working link population exceeds capacity");
    }
    void verifySource() throws IOException {
        byte[] raw; try (var input = NfcoreChipseq200Files.class.getResourceAsStream("/org/jlinalg/omics/nfcore/nfcore-source-lock.json")) { if (input == null) throw new IOException("Missing locked original source"); raw = input.readNBytes(134642); }
        if (raw.length != 134641 || !sha(raw).equals("f5ef123c1d212061c202a1b5ef2d9fef62aa3f7cc764834d40c0c94799bf8646")) throw new IOException("Original source-lock resource differs");
        Map<?, ?> profile = object(object(object(SimpleJson.parse(utf8(raw))).get("pipelines")).get("chipseq-2.0.0")); String source = files.alias("workflow");
        for (var row : object(profile.get("files")).entrySet()) if (!pin(files.inputMember(source + "/" + member(text(row.getKey()))), 1000000000L, () -> { }).equals(fingerprint(row.getValue(), 1000000000L))) throw new IOException("Original v2 source differs: " + row.getKey());
    }
}
