/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Qualifies root routing and unchanged scientific workflows from the normal JAR. */
public final class PackagedLimmaGlobalTests {
    private PackagedLimmaGlobalTests() { }
    public static void main(String[] args)throws Exception {
        var engine=Class.forName("org.jlinalg.cli.LimmaWorkflowCli");var root=Class.forName("org.jlinalg.cli.JLinAlgCli");
        var origin=Path.of(engine.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(!origin.toString().endsWith(".jar")||!engine.getProtectionDomain().getCodeSource().getLocation().equals(root.getProtectionDomain().getCodeSource().getLocation()))throw new AssertionError("Root and scientific classes must come from the same normal JAR");
        if(!engine.getResource("/org/jlinalg/omics/limma_workflow.R").toString().startsWith("jar:"))throw new AssertionError("Worker must come from the product JAR");
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.LimmaWorkflowTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.LimmaGlobalCliTest")).build());
        var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out,true));
        for(var failure:summary.getFailures())failure.getException().printStackTrace(System.err);
        if(summary.getTestsSucceededCount()!=78||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0)throw new AssertionError("Require all 78 limma global/workflow tests");
        System.out.println("LIMMA_GLOBAL_PACKAGED_PASS tests=78 original_cases=46 origin="+origin);
    }
}
