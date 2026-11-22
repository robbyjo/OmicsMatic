/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Confirms production origin before native,full-export and actual workflow contract tests. */
public final class PackagedQtltoolsWorkflowTests {
    private PackagedQtltoolsWorkflowTests() { }
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"org.jlinalg.cli.QtltoolsWorkflowCli","org.jlinalg.cli.QtltoolsWorkflowContracts","org.jlinalg.cli.QtltoolsWorkflowRuntime","org.jlinalg.cli.QtltoolsWorkflowAudit","org.jlinalg.cli.QtltoolsWorkflowFiles","org.jlinalg.cli.JLinAlgCli","org.jlinalg.genetics.QtltoolsFunctionalDensity","org.jlinalg.genetics.GenomicIntervals"}){
            Class<?> type=Class.forName(name);Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();if(!origin.equals(jar))throw new IllegalStateException("Production class shadowed outside normal JAR: "+name);
        }
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
        var request=LauncherDiscoveryRequestBuilder.request();for(String name:new String[]{"org.jlinalg.cli.QtltoolsWorkflowCliTest","org.jlinalg.cli.QtltoolsOriginalExportsTest","org.jlinalg.genetics.QtltoolsFunctionalDensityTest","org.jlinalg.genetics.GenomicIntervalsTest"})request.selectors(DiscoverySelectors.selectClass(name));
        launcher.execute(request.build());var result=listener.getSummary();result.printTo(new PrintWriter(System.out,true));result.printFailuresTo(new PrintWriter(System.err,true));int expected=Integer.parseInt(args[1]);
        if(expected<678||result.getTestsFoundCount()!=expected||result.getTestsSucceededCount()!=expected||result.getTestsSkippedCount()!=0||result.getTotalFailureCount()!=0)throw new IllegalStateException("Incomplete packaged QTLtools workflow controls");
    }
}
