/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;
import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Saved-workflow and source-statistic tests using the actual normal producer JAR. */
public final class PackagedTwoSampleMrWorkflowTests {
    private PackagedTwoSampleMrWorkflowTests() { }
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"TwoSampleMrWorkflowCli","TwoSampleMrWorkflowContracts","TwoSampleMrWorkflowFiles","TwoSampleMrWorkflowAudit","TwoSampleMrWorkflowRuntime","JLinAlgCli"}) {
            Class<?> type=Class.forName("org.jlinalg.cli."+name);Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!origin.equals(jar))throw new IllegalStateException("Production class shadowed outside normal JAR: "+name);
        }
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.mr.TwoSampleMrStatisticsTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrWorkflowTest")).build());
        var result=listener.getSummary();result.printTo(new PrintWriter(System.out,true));result.printFailuresTo(new PrintWriter(System.err,true));
        if(result.getTestsFoundCount()!=667||result.getTestsSucceededCount()!=667||result.getTestsSkippedCount()!=0||result.getTotalFailureCount()!=0)throw new IllegalStateException("Expected 667 complete source-statistic and workflow tests");
    }
}
