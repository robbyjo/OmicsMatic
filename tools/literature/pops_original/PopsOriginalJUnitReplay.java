/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.Properties;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Exercises the original-profile contracts against the packaged artifact on Linux. */
public final class PopsOriginalJUnitReplay {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !System.getProperty("os.name").equals("Linux")) throw new IllegalArgumentException("New Linux contract-report directory required");
        Path out = Path.of(args[0]); Files.createDirectory(out);
        var builder = LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.PopsOriginalContractTest"));
        var listener = new SummaryGeneratingListener(); var launcher = LauncherFactory.create(); launcher.registerTestExecutionListeners(listener); launcher.execute(builder.build());
        var summary = listener.getSummary(); summary.printTo(new PrintWriter(System.out, true)); summary.printFailuresTo(new PrintWriter(System.err, true));
        boolean pass = summary.getTestsFoundCount() == 32 && summary.getTestsSucceededCount() == 32 && summary.getTestsFailedCount() == 0 && summary.getTestsSkippedCount() == 0 && summary.getTestsAbortedCount() == 0;
        Properties record = new Properties(); record.setProperty("status", pass ? "PASS_ORIGINAL_POPS_CONTRACTS_ON_LINUX" : "FAIL_ORIGINAL_POPS_CONTRACTS_ON_LINUX"); record.setProperty("java.version", System.getProperty("java.version")); record.setProperty("os.name", System.getProperty("os.name"));
        record.setProperty("tests_found", Long.toString(summary.getTestsFoundCount())); record.setProperty("tests_succeeded", Long.toString(summary.getTestsSucceededCount())); record.setProperty("tests_failed", Long.toString(summary.getTestsFailedCount())); record.setProperty("tests_skipped", Long.toString(summary.getTestsSkippedCount())); record.setProperty("tests_aborted", Long.toString(summary.getTestsAbortedCount()));
        try (var writer = Files.newBufferedWriter(out.resolve(pass ? "COMPLETED.properties" : "FAILURE.properties"))) { record.store(writer, "Actual JUnit contracts against frozen original-adapter artifact"); }
        if (!pass) System.exit(2);
    }
}
