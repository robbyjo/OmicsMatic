/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.Properties;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Replays the same packaged native PoPS controls on a second JVM/OS. */
public final class PopsJUnitReplay {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("New report directory required");
        Path out = Path.of(args[0]); Files.createDirectory(out);
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass("org.jlinalg.genetics.PopsOriginalTest"),
                       DiscoverySelectors.selectClass("org.jlinalg.genetics.PopsEigenvaluesTest")).build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create(); launcher.registerTestExecutionListeners(listener); launcher.execute(request);
        var summary = listener.getSummary(); summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.err, true));
        Properties report = new Properties();
        report.setProperty("java.version", System.getProperty("java.version")); report.setProperty("os.name", System.getProperty("os.name"));
        report.setProperty("tests_found", Long.toString(summary.getTestsFoundCount()));
        report.setProperty("tests_succeeded", Long.toString(summary.getTestsSucceededCount()));
        report.setProperty("tests_skipped", Long.toString(summary.getTestsSkippedCount()));
        report.setProperty("tests_failed", Long.toString(summary.getTestsFailedCount()));
        report.setProperty("tests_aborted", Long.toString(summary.getTestsAbortedCount()));
        boolean passed = summary.getTestsFoundCount() == 17 && summary.getTestsSucceededCount() == 17
            && summary.getTestsFailedCount() == 0 && summary.getTestsSkippedCount() == 0 && summary.getTestsAbortedCount() == 0;
        report.setProperty("status", passed ? "PASS_NATIVE_POPS_ORIGINAL_CONTROLS_ON_LINUX_JAVA21" : "FAIL_NATIVE_POPS_ORIGINAL_CONTROLS");
        try (var writer = Files.newBufferedWriter(out.resolve(passed ? "COMPLETED.properties" : "FAILURE.properties"))) { report.store(writer, "Actual JUnit execution"); }
        if (!passed) System.exit(2);
    }
}
