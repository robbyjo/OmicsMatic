/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.validation;

import java.io.PrintWriter;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Tests the actual normal-JAR global route,reference arithmetic and legacy methods. */
public final class PackagedAcatTests {
    private PackagedAcatTests(){ }
    public static void main(String[] args)throws Exception {
        Path jar=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"org.jlinalg.cli.AcatToolsCli","org.jlinalg.cli.JLinAlgCli","org.jlinalg.settest.Acat091","org.jlinalg.settest.RLongDoubleSum","org.jlinalg.settest.Acat","org.jlinalg.settest.AcatTests"}){
            Path origin=Path.of(Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();if(!origin.equals(jar))throw new IllegalStateException("Production class shadowed outside normal JAR: "+name);
        }
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);var request=LauncherDiscoveryRequestBuilder.request();
        for(String name:new String[]{"org.jlinalg.cli.Acat091ReferenceTest","org.jlinalg.cli.AcatToolsCliTest","org.jlinalg.settest.RLongDoubleSumTest","org.jlinalg.settest.Acat091Test","org.jlinalg.settest.AcatTestsTest","org.jlinalg.settest.AcatLogTest"})request.selectors(DiscoverySelectors.selectClass(name));
        launcher.execute(request.build());var result=listener.getSummary();result.printTo(new PrintWriter(System.out,true));result.printFailuresTo(new PrintWriter(System.err,true));int expected=Integer.parseInt(args[1]);
        if(expected<490||result.getTestsFoundCount()!=expected||result.getTestsSucceededCount()!=expected||result.getTestsSkippedCount()!=0||result.getTotalFailureCount()!=0)throw new IllegalStateException("Incomplete packaged ACAT source-profile/native/global controls");
    }
}
