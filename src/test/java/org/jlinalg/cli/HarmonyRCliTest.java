/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class HarmonyRCliTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path directory;
    private String output;
    private int run(String name, String rscript, String library, String... extra) {
        List<String> args = new ArrayList<>(List.of("cell-embed", "--method", "harmony", "--engine", "r", "--rscript", rscript,
            "--r-library", library, "--matrix", directory.resolve("pca.tsv").toString(), "--metadata", directory.resolve("cells.tsv").toString(),
            "--batch", "batch", "--clusters", "2", "--iterations", "20", "--seed", "17", "--out", directory.resolve(name).toString(), "--no-config"));
        args.addAll(List.of(extra));
        var bytes = new ByteArrayOutputStream(); var stream = new PrintStream(bytes);
        int status = JLinAlgCli.run(args.toArray(String[]::new), stream, stream); output = bytes.toString(); return status;
    }
    private void fixture(boolean twoBatches) throws Exception {
        Random random = new Random(707);
        StringBuilder x = new StringBuilder("obs_id\tPC1\tPC2\tPC3\tPC4\n"), metadata = new StringBuilder("obs_id\tbatch\n");
        for (int i = 0; i < 200; i++) {
            x.append("C").append(i).append('\t').append((i / 100 == 0 ? -6 : 6) + random.nextGaussian() * 0.7)
                .append('\t').append((i % 2 == 0 ? -3 : 3) + random.nextGaussian() * 0.7)
                .append('\t').append(random.nextGaussian() * 0.7).append('\t').append(random.nextGaussian() * 0.7).append('\n');
        }
        // Reverse metadata deliberately: the adapter must align by obs_id.
        for (int i = 199; i >= 0; i--) metadata.append("C").append(i).append('\t').append(twoBatches && i % 2 == 0 ? "A" : "B").append('\n');
        Files.writeString(directory.resolve("pca.tsv"), x); Files.writeString(directory.resolve("cells.tsv"), metadata);
    }
    @Test void unavailableRscriptSkipsWithRetainedActionsAndNoSuccessfulCoordinates() throws Exception {
        fixture(true);
        assertEquals(3, run("missing", directory.resolve("absent-Rscript").toString(), directory.resolve("absent-library").toString()), output);
        assertTrue(output.contains("SKIPPED ANALYSIS"));
        assertTrue(Files.readString(directory.resolve("missing/WARNINGS.md")).contains("Required action"));
        assertFalse(Files.exists(directory.resolve("missing/results.tsv")));
        assertEquals(2, run("invalid-timeout", directory.resolve("absent-Rscript").toString(), directory.resolve("absent-library").toString(), "--timeout", "0"), output);
        assertTrue(Files.readString(directory.resolve("invalid-timeout/WARNINGS.md")).contains("timeout must be in"));
        assertFalse(Files.exists(directory.resolve("invalid-timeout/external.log")), "Invalid process options must fail before launching Rscript");
    }
    @Test void realPinnedHarmonyReducesBalancedBatchShiftWhilePreservingTypeSignalAndSeed() throws Exception {
        String rscript = System.getenv("JLINALG_HARMONY_RSCRIPT"), library = System.getenv("JLINALG_HARMONY_R_LIBRARY");
        assumeTrue(rscript != null && library != null, "Optional real R Harmony validation requires the private pinned R runtime");
        fixture(true);
        assertEquals(0, run("corrected", rscript, library), output + " " + directory);
        assertEquals(0, run("repeat", rscript, library), output);
        assertEquals(Files.readString(directory.resolve("corrected/results.tsv")), Files.readString(directory.resolve("repeat/results.tsv")));
        var original = DelimitedData.read(directory.resolve("pca.tsv")); var corrected = DelimitedData.read(directory.resolve("corrected/results.tsv"));
        assertEquals(200, corrected.rows().size());
        double[] beforeBatch = new double[2], afterBatch = new double[2], beforeType = new double[2], afterType = new double[2];
        for (int i = 0; i < 200; i++) {
            assertEquals("C" + i, corrected.rows().get(i)[0]);
            beforeBatch[i % 2] += Double.parseDouble(original.rows().get(i)[2]) / 100;
            afterBatch[i % 2] += Double.parseDouble(corrected.rows().get(i)[2]) / 100;
            beforeType[i / 100] += Double.parseDouble(original.rows().get(i)[1]) / 100;
            afterType[i / 100] += Double.parseDouble(corrected.rows().get(i)[1]) / 100;
        }
        assertTrue(Math.abs(afterBatch[1] - afterBatch[0]) < Math.abs(beforeBatch[1] - beforeBatch[0]) * 0.3);
        assertTrue(Math.abs(afterType[1] - afterType[0]) > Math.abs(beforeType[1] - beforeType[0]) * 0.8);
        assertTrue(Files.readString(directory.resolve("corrected/effective-settings.tsv")).contains("2.0.5"));
        assertTrue(Files.isRegularFile(directory.resolve("corrected/batch-centroids.tsv")));
    }
    @Test void singleBatchInputFailsWithPersistentWarning() throws Exception {
        String rscript = System.getenv("JLINALG_HARMONY_RSCRIPT"), library = System.getenv("JLINALG_HARMONY_R_LIBRARY");
        assumeTrue(rscript != null && library != null, "Optional real R Harmony validation"); fixture(false);
        assertEquals(2, run("invalid", rscript, library), output);
        assertTrue(Files.readString(directory.resolve("invalid/WARNINGS.md")).contains("at least two batches"));
        assertFalse(Files.exists(directory.resolve("invalid/results.tsv")));
    }
}
