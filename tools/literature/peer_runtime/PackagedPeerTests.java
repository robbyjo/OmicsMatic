/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Runs test classes against the normal JAR first, with no production-class shadow. */
public final class PackagedPeerTests {
    private PackagedPeerTests() { }
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"PeerWorkflowCli","PeerWorkflowFiles","PeerWorkflowContracts","PeerWorkflowRuntime","PeerWorkflowAudit","JLinAlgCli"}) {
            Class<?> type=Class.forName("org.jlinalg.cli."+name);Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!origin.equals(jar))throw new IllegalStateException("Production class did not load from the normal JAR: "+name);
        }
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.PeerWorkflowTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.PeerGlobalCliTest")).build());
        var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.err,true));
        if(summary.getTestsFoundCount()!=26||summary.getTestsSucceededCount()!=26||summary.getTestsSkippedCount()!=0||summary.getTestsFailedCount()!=0||summary.getTotalFailureCount()!=0)throw new IllegalStateException("Expected26 complete configured PEER tests");
    }
}
