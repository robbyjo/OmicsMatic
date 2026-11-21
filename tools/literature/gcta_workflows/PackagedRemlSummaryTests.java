/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Developer-only original REML export regression launcher; product JAR comes first. */
public final class PackagedRemlSummaryTests {
    public static void main(String[] arguments) {
        if(arguments.length!=0)throw new IllegalArgumentException("no arguments");
        var request=LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaRemlSummaryAuditTest"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled","false").build();
        var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);
        var summary=listener.getSummary();var writer=new PrintWriter(System.out,true);
        summary.printTo(writer);summary.printFailuresTo(writer);
        if(summary.getTestsFoundCount()!=2||summary.getTestsSucceededCount()!=2
                ||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0)
            throw new AssertionError("FAIL2: original REML summary regressions did not complete both tests");
        System.out.println("GCTA_REML_SUMMARY_REGRESSION_PASS tests=2 original_models=5 inverse_controls=9");
    }
}
