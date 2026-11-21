/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;
import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Original captures and source-statistic checks loaded from the normal producer JAR. */
public final class PackagedTwoSampleMrHistoricalTests {
    private PackagedTwoSampleMrHistoricalTests() { }
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"TwoSampleMrHistoricalCli","TwoSampleMrHistoricalContracts","TwoSampleMrHistoricalFiles","TwoSampleMrHistoricalAudit","TwoSampleMrHistoricalRuntime","TwoSampleMrClumpCli","TwoSampleMrClumpContracts","TwoSampleMrClumpFiles","TwoSampleMrClumpAudit","TwoSampleMrClumpRuntime","TwoSampleMrWorkflowCli","TwoSampleMrWorkflowContracts","TwoSampleMrWorkflowFiles","TwoSampleMrWorkflowAudit","TwoSampleMrWorkflowRuntime","JLinAlgCli"}) {
            Class<?> type=Class.forName("org.jlinalg.cli."+name);Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!origin.equals(jar))throw new IllegalStateException("Production class shadowed outside normal JAR: "+name);
        }
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.mr.TwoSampleMrStatisticsTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrWorkflowTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrClumpTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrHistoricalTest")).build());
        var result=listener.getSummary();result.printTo(new PrintWriter(System.out,true));result.printFailuresTo(new PrintWriter(System.err,true));
        if(result.getTestsFoundCount()!=769||result.getTestsSucceededCount()!=769||result.getTestsSkippedCount()!=0||result.getTotalFailureCount()!=0)throw new IllegalStateException("Expected 769 complete source-statistic, workflow and clumping tests");
    }
}
