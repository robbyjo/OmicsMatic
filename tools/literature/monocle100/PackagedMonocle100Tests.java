/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Original-output weight fixtures and preceding controls loaded from the normal JAR. */
public final class PackagedMonocle100Tests {
    private PackagedMonocle100Tests() { }
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toRealPath();
        for (String name : new String[]{"org.jlinalg.singlecell.Monocle100Weights","org.jlinalg.singlecell.Monocle100Weights$Arc","org.jlinalg.singlecell.Monocle100Weights$PrincipalEdge","org.jlinalg.singlecell.Monocle100Weights$Result","org.jlinalg.cli.Monocle100WeightsCli","org.jlinalg.spatial.RegressionMoran","org.jlinalg.cli.RegressionMoranCli","org.jlinalg.cli.JLinAlgCli"}) {
            Class<?> type = Class.forName(name);
            Path origin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!origin.equals(jar)) throw new IllegalStateException("Production class shadowed outside normal JAR: " + name);
        }
        var listener = new SummaryGeneratingListener(); var launcher = LauncherFactory.create(); launcher.registerTestExecutionListeners(listener);
        var request = LauncherDiscoveryRequestBuilder.request();
        for (String name : new String[]{"Monocle100WeightsTest","Monocle100WeightsCliTest","RegressionMoranTest","RegressionMoranCliTest","MonocleRootCliTest","JLinAlgCliTest"}) request.selectors(DiscoverySelectors.selectClass("org.jlinalg.cli." + name));
        launcher.execute(request.build()); var result = listener.getSummary();
        result.printTo(new PrintWriter(System.out, true)); result.printFailuresTo(new PrintWriter(System.err, true));
        int expected = Integer.parseInt(args[1]);
        if (expected < 600 || result.getTestsFoundCount() != expected || result.getTestsSucceededCount() != expected || result.getTestsSkippedCount() != 0 || result.getTotalFailureCount() != 0)
            throw new IllegalStateException("Incomplete original-output/native/CLI control population: expected " + expected);
    }
}
