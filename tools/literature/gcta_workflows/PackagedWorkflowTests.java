/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Developer launcher; put the exact normal product JAR first on the classpath. */
public final class PackagedWorkflowTests {
    public static void main(String[] arguments) {
        if (arguments.length != 0) throw new IllegalArgumentException("no runtime arguments");
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaWorkflowCliTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GctaHardcallGrmTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GenomicRelationshipMatrixTest"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        var writer = new PrintWriter(System.out, true);
        summary.printTo(writer); summary.printFailuresTo(writer);
        if (summary.getTestsFoundCount() != 16 || summary.getTestsSucceededCount() != 16
                || summary.getTestsFailedCount() != 0 || summary.getTestsAbortedCount() != 0
                || summary.getTestsSkippedCount() != 0)
            throw new AssertionError("FAIL2: packaged workflow checks did not complete all sixteen tests");
        System.out.println("GCTA_WORKFLOW_PACKAGED_CHECKS_PASS tests=16");
    }
}
