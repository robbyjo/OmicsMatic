/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowJoToolsCliTest {
    @Test void dispatcherHelpStatesScientificScopeAndRequiredActions() {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=JLinAlgCli.run(new String[]{"flowjo-tools","--help"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertEquals(0,code);String text=out.toString(StandardCharsets.UTF_8);assertTrue(text.contains("WSP20.0"));assertTrue(text.contains("SKIP3"));assertTrue(text.contains("FAIL2"));assertTrue(text.contains("REPLAY requires the exact original producer JAR"));assertEquals("",err.toString(StandardCharsets.UTF_8));
    }
    @Test void malformedArgumentsRefuseWithoutStartingAnAnalysis() {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();assertEquals(2,FlowJoToolsCli.run(new String[]{"GATE"},new PrintStream(out),new PrintStream(err)));assertTrue(err.toString(StandardCharsets.UTF_8).contains("NO COMPLETE RESULT ADMITTED"));assertEquals("",out.toString(StandardCharsets.UTF_8));
    }
}
