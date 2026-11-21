/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import org.jlinalg.differential.LimmaVarianceModeration;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Developer-only launcher; the normal product JAR must supply scientific classes. */
public final class PackagedVarianceTests {
    private PackagedVarianceTests() { }
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) throw new IllegalArgumentException("no arguments");
        String origin = LimmaVarianceModeration.class.getProtectionDomain().getCodeSource().getLocation().toURI().toString();
        if (!origin.endsWith("/omicsmatic-0.4.0.jar")) throw new AssertionError("Scientific class not loaded from normal product JAR: " + origin);
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("org.jlinalg.differential.LimmaVarianceModerationTest"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false").build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener); var summary = listener.getSummary();
        var writer = new PrintWriter(System.out, true); summary.printTo(writer); summary.printFailuresTo(writer);
        if (summary.getTestsFoundCount() != 379 || summary.getTestsSucceededCount() != 379
                || summary.getTestsFailedCount() != 0 || summary.getTestsAbortedCount() != 0
                || summary.getTestsSkippedCount() != 0)
            throw new AssertionError("FAIL2: packaged limma variance controls did not all complete");
        System.out.println("LIMMA_VARIANCE_PACKAGED_PASS tests=379 original_cases=372 origin=" + origin);
    }
}
