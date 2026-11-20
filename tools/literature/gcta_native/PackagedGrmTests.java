/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Developer-only JUnit launcher: put the normal product JAR first on the classpath. */
public final class PackagedGrmTests {
    public static void main(String[] arguments) {
        if (arguments.length != 0) throw new IllegalArgumentException("no runtime arguments");
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GctaHardcallGrmTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GenomicRelationshipMatrixTest"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        var writer = new PrintWriter(System.out, true);
        summary.printTo(writer); summary.printFailuresTo(writer);
        if (summary.getTestsFoundCount() != 9 || summary.getTestsSucceededCount() != 9
                || summary.getTestsFailedCount() != 0 || summary.getTestsAbortedCount() != 0
                || summary.getTestsSkippedCount() != 0)
            throw new AssertionError("FAIL2: packaged GRM qualification did not complete all nine tests");
        System.out.println("GCTA_PACKAGED_SOURCE_CHECKS_PASS tests=9 original_cases=35 float32_fields=190428 explicit_source_refusals=6");
    }
}
