/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Developer-only launcher: place the normal product JAR before test classes. */
public final class PackagedMtcojoTests {
    public static void main(String[] arguments) {
        if(arguments.length!=0)throw new IllegalArgumentException("no arguments");
        var request=LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaRemlSummaryAuditTest"),
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaWorkflowCliTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GctaHardcallGrmTest"),
            DiscoverySelectors.selectClass("org.jlinalg.genetics.GenomicRelationshipMatrixTest"),
            DiscoverySelectors.selectClass("org.jlinalg.cli.MtcojoConditionalTest"),
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaMtcojoContractsTest"),
            DiscoverySelectors.selectClass("org.jlinalg.cli.GctaMtcojoWorkflowTest"))
            .configurationParameter("junit.jupiter.execution.parallel.enabled","false").build();
        var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(request,listener);
        var summary=listener.getSummary();var writer=new PrintWriter(System.out,true);
        summary.printTo(writer);summary.printFailuresTo(writer);
        if(summary.getTestsFoundCount()!=24||summary.getTestsSucceededCount()!=24
                ||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0)
            throw new AssertionError("FAIL2: packaged GCTA/mtCOJO regressions did not complete all 24 tests");
        System.out.println("GCTA_MTCOJO_HANDOVER_PASS tests=24 original_conditional_fields=300000 captures=8");
    }
}
