/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Load every production adapter/helper from the actual normal executable. */
public final class PackagedMonocleResponseTests {
    private PackagedMonocleResponseTests() { }
    public static void main(String[] args) throws Exception {
        Path jar=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"org.jlinalg.singlecell.MonocleResponsePreparation","org.jlinalg.cli.MonocleResponsePreparationCli","org.jlinalg.cli.MonocleToolsCli","org.jlinalg.cli.Monocle100GraphCli","org.jlinalg.cli.Monocle100GraphContracts","org.jlinalg.cli.Monocle100GraphFiles","org.jlinalg.cli.Monocle100GraphAudit","org.jlinalg.singlecell.Monocle3Pseudotime","org.jlinalg.cli.Monocle100NeighborsCli","org.jlinalg.cli.Monocle100NeighborsContracts","org.jlinalg.cli.Monocle100NeighborsFiles","org.jlinalg.cli.Monocle100NeighborsAudit","org.jlinalg.cli.Monocle100NeighborsRuntime","org.jlinalg.singlecell.Monocle100Weights","org.jlinalg.cli.Monocle100WeightsCli","org.jlinalg.spatial.RegressionMoran","org.jlinalg.cli.RegressionMoranCli","org.jlinalg.cli.JLinAlgCli"}) {
            Class<?> type=Class.forName(name);Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!origin.equals(jar))throw new IllegalStateException("Production class shadowed outside normal JAR: "+name);
        }
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);var request=LauncherDiscoveryRequestBuilder.request();
        for(String name:new String[]{"Monocle100GraphContractsTest","Monocle100GraphCaptureTest","Monocle100NeighborsContractsTest","Monocle100NeighborsCaptureTest","Monocle100WeightsTest","Monocle100WeightsCliTest","RegressionMoranTest","RegressionMoranCliTest","MonocleRootCliTest","JLinAlgCliTest","MonocleResponsePreparationTest","MonocleResponsePreparationCliTest","MonocleStageDispatchTest"})request.selectors(DiscoverySelectors.selectClass("org.jlinalg.cli."+name));
        launcher.execute(request.build());var result=listener.getSummary();result.printTo(new PrintWriter(System.out,true));result.printFailuresTo(new PrintWriter(System.err,true));int expected=Integer.parseInt(args[1]);
        if(expected<880||result.getTestsFoundCount()!=expected||result.getTestsSucceededCount()!=expected||result.getTestsSkippedCount()!=0||result.getTotalFailureCount()!=0)throw new IllegalStateException("Incomplete normal-JAR original/native/admission controls: expected "+expected);
    }
}
