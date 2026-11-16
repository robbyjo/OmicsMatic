/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.Properties;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Executes the complete native/workflow controls against the packaged artifact on Linux. */
public final class PopsWorkflowJUnitReplay {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("New report directory required");
        Path out = Path.of(args[0]); Files.createDirectory(out);
        var builder = LauncherDiscoveryRequestBuilder.request();
        for (String name : new String[] {"org.jlinalg.genetics.PopsOriginalTest", "org.jlinalg.genetics.PopsEigenvaluesTest", "org.jlinalg.genetics.PopsChunkedTest", "org.jlinalg.cli.PopsWorkflowNpyTest", "org.jlinalg.cli.PopsWorkflowCliTest"}) builder.selectors(DiscoverySelectors.selectClass(name));
        var listener = new SummaryGeneratingListener(); var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener); launcher.execute(builder.build());
        var summary = listener.getSummary(); summary.printTo(new PrintWriter(System.out, true)); summary.printFailuresTo(new PrintWriter(System.err, true));
        boolean pass = summary.getTestsFoundCount() == 219 && summary.getTestsSucceededCount() == 219 && summary.getTestsFailedCount() == 0 && summary.getTestsSkippedCount() == 0 && summary.getTestsAbortedCount() == 0;
        Properties result = new Properties(); result.setProperty("status", pass ? "PASS_COMPLETE_POPS_CONTROLS_ON_LINUX" : "FAIL_COMPLETE_POPS_CONTROLS_ON_LINUX");
        result.setProperty("java.version", System.getProperty("java.version")); result.setProperty("os.name", System.getProperty("os.name"));
        result.setProperty("tests_found", Long.toString(summary.getTestsFoundCount())); result.setProperty("tests_succeeded", Long.toString(summary.getTestsSucceededCount()));
        result.setProperty("tests_failed", Long.toString(summary.getTestsFailedCount())); result.setProperty("tests_skipped", Long.toString(summary.getTestsSkippedCount())); result.setProperty("tests_aborted", Long.toString(summary.getTestsAbortedCount()));
        try (var writer = Files.newBufferedWriter(out.resolve(pass ? "COMPLETED.properties" : "FAILURE.properties"))) { result.store(writer, "Actual JUnit execution against packaged candidate"); }
        if (!pass) System.exit(2);
    }
}
