/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Developer-only launcher; exact normal product JAR must precede test classes. */
public final class PackagedQualificationTests {
    public static void main(String[] arguments) {
        if(arguments.length!=0)throw new IllegalArgumentException("no arguments");
        var request=LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaRemlSummaryAuditTest"),
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaWorkflowCliTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GctaHardcallGrmTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GenomicRelationshipMatrixTest"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled","false").build();
        var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);
        var summary=listener.getSummary();var writer=new PrintWriter(System.out,true);
        summary.printTo(writer);summary.printFailuresTo(writer);
        if(summary.getTestsFoundCount()!=18||summary.getTestsSucceededCount()!=18
                ||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0)
            throw new AssertionError("FAIL2: packaged GCTA regressions did not complete all eighteen tests");
        System.out.println("GCTA_QUALIFICATION_REGRESSION_PASS tests=18 original_REML_models=5 summary_inverse_controls=9");
    }
}
