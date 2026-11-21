/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;
import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Checks actual normal-JAR classes with no production-class shadow. */
public final class PackagedTwoSampleMrTests {
    private PackagedTwoSampleMrTests() { }
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toRealPath();
        for (String name : new String[] {"org.jlinalg.mr.TwoSampleMrStatistics", "org.jlinalg.mr.TwoSampleMrSmallQr",
                "org.jlinalg.mr.TwoSampleMrStatistics$Value", "org.jlinalg.mr.TwoSampleMrStatistics$Result",
                "org.jlinalg.mr.MendelianRandomization", "org.jlinalg.cli.JLinAlgCli"}) {
            Class<?> type = Class.forName(name);
            Path origin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!origin.equals(jar)) throw new IllegalStateException("Production class did not load from the normal JAR: " + name);
        }
        var listener = new SummaryGeneratingListener(); var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass("org.jlinalg.mr.TwoSampleMrStatisticsTest")).build());
        var summary = listener.getSummary(); summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.err, true));
        if (summary.getTestsFoundCount() != 638 || summary.getTestsSucceededCount() != 638
                || summary.getTestsSkippedCount() != 0 || summary.getTotalFailureCount() != 0)
            throw new IllegalStateException("Expected 638 complete source-statistics tests");
    }
}
