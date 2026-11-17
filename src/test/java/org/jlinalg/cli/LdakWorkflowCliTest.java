/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LdakWorkflowCliTest {
    @TempDir Path root;
    private Properties sumher() throws IOException {
        Properties p = new Properties();
        for (String key : List.of("executable", "license", "tagging", "summary")) {
            Path file = root.resolve(key); Files.writeString(file, "policy fixture; no engine is executed\n"); p.setProperty(key, file.toString());
        }
        p.setProperty("mode", "sumher"); p.setProperty("execution", "allow"); p.setProperty("wall_ms", "30000");
        p.setProperty("max_bytes", "100000000"); p.setProperty("max_files", "1000"); p.setProperty("upstream_adjustments", "forbid");
        p.setProperty("max_iterations", "100"); p.setProperty("tolerance", "0.000001"); p.setProperty("max_predictors", "100000");
        p.setProperty("check_sums", "YES"); p.setProperty("allow_ambiguous", "NO"); p.setProperty("inflation", "none");
        p.setProperty("solver", "maximum-likelihood"); p.setProperty("prevalence", "-"); p.setProperty("ascertainment", "-");
        p.setProperty("cutoff", "-");
        return p;
    }
    private static PrintStream stream(ByteArrayOutputStream bytes) { return new PrintStream(bytes, true, StandardCharsets.UTF_8); }

    @Test void helpStatesIndependentVerificationGenuineReplayAndProminentOriginalSubstitutions() {
        var bytes = new ByteArrayOutputStream();
        assertEquals(0, JLinAlgCli.run(new String[]{"ldak-tools", "--help"}, stream(bytes), stream(new ByteArrayOutputStream())));
        String text = bytes.toString(StandardCharsets.UTF_8);
        for (String expected : List.of("ldak-tools run", "ldak-tools verify", "ldak-tools replay", "without rewriting input", "seed option", "SKIP3", "FAIL2")) assertTrue(text.contains(expected), expected);
    }
    @Test void forbiddingExecutionSkipsBeforeRuntimeCaptureOrChildLaunch() throws Exception {
        Properties p = sumher(); p.setProperty("execution", "forbid");
        Path request = root.resolve("request.properties"); LdakWorkflowFiles.store(request, p);
        Path out = root.resolve("skipped"); var errors = new ByteArrayOutputStream();
        assertEquals(3, LdakWorkflowCli.run(new String[]{"run", "--request", request.toString(), "--out", out.toString()}, stream(new ByteArrayOutputStream()), stream(errors)));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("SKIP3"));
        assertTrue(Files.readString(out.resolve("SKIPPED.txt")).contains("forbidden"));
        assertFalse(Files.exists(out.resolve("runtime"))); assertFalse(Files.exists(out.resolve("stages"))); assertFalse(Files.exists(out.resolve("COMPLETED.properties")));
    }
    @Test void refusesDuplicatePropertiesAndExistingOutputsWithoutChangingThem() throws Exception {
        Path request = root.resolve("duplicate.properties"); Files.writeString(request, "mode=sumher\nmode=sumher\n");
        Path out = root.resolve("existing"); Files.createDirectory(out); Files.writeString(out.resolve("owned-by-user"), "retain");
        assertEquals(2, LdakWorkflowCli.run(new String[]{"run", "--request", request.toString(), "--out", out.toString()}, stream(new ByteArrayOutputStream()), stream(new ByteArrayOutputStream())));
        Path valid = root.resolve("valid.properties"); LdakWorkflowFiles.store(valid, sumher());
        assertEquals(2, LdakWorkflowCli.run(new String[]{"run", "--request", valid.toString(), "--out", out.toString()}, stream(new ByteArrayOutputStream()), stream(new ByteArrayOutputStream())));
        assertEquals("retain", Files.readString(out.resolve("owned-by-user")));
        try (var files = Files.list(out)) { assertEquals(1, files.count()); }
    }
    @Test void requestRejectsUnknownChoicesAndInconsistentLiabilityWithoutGuessingDefaults() throws Exception {
        Properties p = sumher();
        assertTrue(LdakWorkflowRequest.parse(p).sumher());
        p.setProperty("prevalence", "0.004"); assertThrows(IllegalArgumentException.class, () -> LdakWorkflowRequest.parse(p));
        p.setProperty("ascertainment", "0.5"); assertTrue(LdakWorkflowRequest.parse(p).sumher());
        p.setProperty("unexpected_option", "YES"); assertThrows(IllegalArgumentException.class, () -> LdakWorkflowRequest.parse(p)); p.remove("unexpected_option");
        p.setProperty("truncate", "0.01"); assertThrows(IllegalArgumentException.class, () -> LdakWorkflowRequest.parse(p)); p.remove("truncate");
        p.setProperty("tolerance", "NaN"); assertThrows(IllegalArgumentException.class, () -> LdakWorkflowRequest.parse(p));
    }
    @Test void originalCommandUsesQualifiedFiveTwoFlagsAndExplicitAlleleAndInflationChoices() throws Exception {
        Properties p = sumher(); p.setProperty("inflation", "additive"); p.setProperty("allow_ambiguous", "YES");
        var stages = LdakWorkflowProcess.plan(LdakWorkflowRequest.parse(p)); assertEquals(1, stages.size());
        var args = stages.get(0).arguments();
        assertTrue(args.contains("--tolerance")); assertFalse(args.contains("--tol")); assertFalse(args.contains("--seed")); assertFalse(args.contains("--random-seed"));
        assertEquals("YES", args.get(args.indexOf("--intercept") + 1)); assertEquals("NO", args.get(args.indexOf("--genomic-control") + 1));
        assertEquals("YES", args.get(args.indexOf("--allow-ambiguous") + 1));
    }
    @Test void sourceWarningsRetainExactMessagesAndGateEvenZeroExitErrorsAndSubstitutions() throws Exception {
        var c = LdakWorkflowRequest.parse(sumher()); Path folder = root.resolve("stages/sumher"); Files.createDirectories(folder);
        String text = "Warning, 18 SNPs have test statistic 0; these have been replaced with 1e-6\n"
            + "Warning, 4 SNPs have tagging less than 1 (0 less than 0); these have been replaced with 1\n"
            + "In total, 53 predictors have ambiguous alleles\nError, invalid original input\n";
        Files.writeString(folder.resolve("original.stdout"), text);
        var report = LdakWorkflowDiagnostics.collect(root, c);
        assertEquals(2, report.warnings()); assertEquals(1, report.errors()); assertEquals(18, report.zeroStatisticsReplaced()); assertEquals(4, report.taggingsFloored()); assertEquals(53, report.ambiguousIgnored());
        assertThrows(IOException.class, () -> LdakWorkflowDiagnostics.requireAdmissible(c, report));
        Files.writeString(folder.resolve("original.stdout"), text.substring(0, text.indexOf("Error,")));
        var replacementsOnly = LdakWorkflowDiagnostics.collect(root, c);
        assertThrows(IOException.class, () -> LdakWorkflowDiagnostics.requireAdmissible(c, replacementsOnly));
    }
    @Test void independentLiabilityConversionAgreesWithAnalyticAndOriginalPrintedControls() throws Exception {
        assertEquals(Math.PI / 2, LdakWorkflowResults.liabilityFactor(.5, .5), 1e-14);
        double factor = LdakWorkflowResults.liabilityFactor(.004, .5);
        assertEquals(.329947, .729400 * factor, 8e-7); assertEquals(.060811, .134432 * factor, 8e-7);
        assertThrows(IOException.class, () -> LdakWorkflowResults.liabilityFactor(0, .5));
    }
    @Test void taggingRowsAreRegressionPredictorsAndDoNotReplaceReferenceOrHeritabilityPopulations() throws Exception {
        var c = LdakWorkflowRequest.parse(sumher()); Files.createDirectory(root.resolve("inputs"));
        String tagging = "Predictor A1 A2 Neighbours Tagging Weight MAF Categories Exp_Heritability Base\n"
            + "rs1 A C 1 0.8 1 0.2 1 0.32 0.32\n"
            + "The relative contribution of the Base to each category 3.2\n"
            + "There are 10 reference 1 regression 8 heritability predictors 8\n";
        Files.writeString(root.resolve("inputs/reference.tagging"), tagging);
        Files.writeString(root.resolve("inputs/summary"), "Predictor A1 A2 Direction Stat n\nrs1 A C 1 0 100\n");
        var stats = LdakWorkflowInputs.sumher(root, c);
        assertEquals(1, stats.taggingRows()); assertEquals(10, stats.referencePredictors());
        assertEquals(1, stats.regressionPredictors()); assertEquals(8, stats.heritabilityPredictors());
        assertEquals(1, stats.rawZeroStatistics()); assertEquals(1, stats.rawTaggingsBelowOne());
        Files.writeString(root.resolve("inputs/reference.tagging"), tagging.replace("10 reference 1 regression", "10 reference 2 regression"));
        assertThrows(IOException.class, () -> LdakWorkflowInputs.sumher(root, c));
    }
}
