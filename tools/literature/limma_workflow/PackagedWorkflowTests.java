/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Executes only workflow tests with scientific classes loaded from the product JAR. */
public final class PackagedWorkflowTests {
    private PackagedWorkflowTests() { }
    public static void main(String[] args)throws Exception {
        var scientific=Class.forName("org.jlinalg.cli.LimmaWorkflowCli");
        var origin=Path.of(scientific.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(!origin.toString().endsWith(".jar"))throw new AssertionError("Scientific classes must come from the normal JAR");
        String resource=scientific.getResource("/org/jlinalg/omics/limma_workflow.R").toString();
        if(!resource.startsWith("jar:"))throw new AssertionError("Worker must come from the normal JAR");
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.LimmaWorkflowTest")).build());
        var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out,true));
        for(var failure:summary.getFailures())failure.getException().printStackTrace(System.err);
        if(summary.getTestsSucceededCount()!=72||summary.getTestsFailedCount()!=0||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0)throw new AssertionError("Require all 72 workflow tests");
        System.out.println("LIMMA_WORKFLOW_PACKAGED_PASS tests=72 original_cases=46 origin="+origin+" worker="+resource);
    }
}
