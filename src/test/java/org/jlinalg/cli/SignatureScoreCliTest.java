/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignatureScoreCliTest {
    @TempDir Path directory;
    String message;

    private int run(String... arguments) {
        var bytes = new ByteArrayOutputStream();
        var stream = new PrintStream(bytes);
        List<String> all = new ArrayList<>(List.of(arguments)); all.add("--no-config");
        int status = JLinAlgCli.run(all.toArray(String[]::new), stream, stream);
        message = bytes.toString(); return status;
    }

    @Test void cliOutputsFrozenScoresCoverageAndHashesForEachMethod() throws Exception {
        Path fixture = Path.of("src/test/resources/signature-scores");
        for (String method : List.of("ucell", "aucell", "ssgsea")) {
            Path out = directory.resolve(method);
            List<String> args = new ArrayList<>(List.of("signature-score", "--method", method,
                "--matrix", fixture.resolve("expression.tsv").toString(), "--gene-sets",
                fixture.resolve("signatures.gmt").toString(), "--out", out.toString()));
            if (!method.equals("ssgsea")) args.addAll(List.of("--max-rank", "10"));
            assertEquals(0, run(args.toArray(String[]::new)), message);
            var scores = DelimitedData.read(out.resolve("scores.tsv"));
            var reference = DelimitedData.read(fixture.resolve("reference.tsv"));
            for (String[] row : reference.rows()) {
                if (!row[0].equals(method) || !row[4].equals("0.25") || !method.equals("ssgsea") && !row[3].equals("10")) continue;
                String[] sample = scores.rows().stream().filter(r -> r[0].equals(row[1])).findFirst().orElseThrow();
                assertEquals(Double.parseDouble(row[5]), Double.parseDouble(sample[scores.column(row[2])]), 2e-12);
            }
            assertEquals(4, DelimitedData.read(out.resolve("coverage.tsv")).rows().size());
            String manifest = Files.readString(out.resolve("manifest.yaml"));
            assertTrue(manifest.contains("input_sha256")); assertTrue(manifest.contains("output_sha256"));
            assertEquals(2, run(args.toArray(String[]::new))); // publication is immutable
        }
    }

    @Test void absentGenesAreExplicitAndFailureDoesNotPublishDirectory() throws Exception {
        Path matrix = directory.resolve("expression.tsv"), sets = directory.resolve("signatures.gmt");
        Files.writeString(matrix, "sample_id\tA\tB\tC\nS\t10\t3\t1\n");
        Files.writeString(sets, "set\tdescription\tA\tABSENT\n");
        String[] base = {"signature-score", "--method", "ucell", "--matrix", matrix.toString(),
            "--gene-sets", sets.toString(), "--out", directory.resolve("strict").toString()};
        assertEquals(2, run(base)); assertTrue(message.contains("absent")); assertFalse(Files.exists(directory.resolve("strict")));
        List<String> args = new ArrayList<>(List.of(base)); args.set(args.size() - 1, directory.resolve("imputed").toString());
        args.addAll(List.of("--missing", "impute"));
        assertEquals(0, run(args.toArray(String[]::new)), message);
        assertTrue(Files.readString(directory.resolve("imputed/coverage.tsv")).contains("set\t2\t1\t1"));
        Files.writeString(matrix, "sample_id\tA\tB\tC\nS\t10\t3\t1\nS\t0\t1\t2\n");
        args.set(args.indexOf("--out") + 1, directory.resolve("duplicate").toString());
        assertEquals(2, run(args.toArray(String[]::new))); assertFalse(Files.exists(directory.resolve("duplicate")));
    }
}
