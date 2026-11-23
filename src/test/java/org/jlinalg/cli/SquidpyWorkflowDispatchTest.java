/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class SquidpyWorkflowDispatchTest {
    @Test void globalCommandReachesTheTypedOriginalWorkflowHelp(){
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"squidpy-tools","--help"},new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(errors,true,StandardCharsets.UTF_8)));
        String text=output.toString(StandardCharsets.UTF_8);assertTrue(text.contains("squidpy-tools --request"));assertTrue(text.contains("REPLAY"));assertTrue(text.contains("SKIP3"));assertTrue(text.contains("FAIL2"));assertEquals("",errors.toString(StandardCharsets.UTF_8));
    }
    @Test void workflowSyntaxFailuresReachItsExplicitNoResultDiagnostic(){
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        assertEquals(2,JLinAlgCli.run(new String[]{"squidpy-tools","--unknown"},new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(errors,true,StandardCharsets.UTF_8)));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("FAIL2: NO COMPLETE RESULT"));assertEquals("",output.toString(StandardCharsets.UTF_8));
    }
}
