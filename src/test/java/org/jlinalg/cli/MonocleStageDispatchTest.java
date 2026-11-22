/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MonocleStageDispatchTest {
    private static PrintStream stream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes,true,StandardCharsets.UTF_8);
    }
    @ParameterizedTest
    @ValueSource(strings={"ORDER","MORAN-LM","GRAPH-1.0.0","NEIGHBORS-1.0.0","WEIGHTS-1.0.0","RESPONSES-1.0.0","RUN","MODELS-1.3.1","GRAPH-1.3.3","ALIGN-1.3.7","DIFFERENTIAL-2.26.0"})
    void everyGlobalActionDelegatesToExactlyTheSelectedStage(String action) {
        var direct=new ByteArrayOutputStream();var directErrors=new ByteArrayOutputStream();
        var root=new ByteArrayOutputStream();var rootErrors=new ByteArrayOutputStream();
        String[] args={"--help"};PrintStream out=stream(direct),err=stream(directErrors);
        int status=switch(action) {
            case "ORDER"->Monocle3OrderCli.run(args,out,err);
            case "MORAN-LM"->RegressionMoranCli.run(args,out,err);
            case "GRAPH-1.0.0"->Monocle100GraphCli.run(args,out,err);
            case "NEIGHBORS-1.0.0"->Monocle100NeighborsCli.run(args,out,err);
            case "WEIGHTS-1.0.0"->Monocle100WeightsCli.run(args,out,err);
            case "RESPONSES-1.0.0"->MonocleResponsePreparationCli.run(args,out,err);
            case "RUN"->Monocle3WorkflowCli.run(args,out,err);
            case "MODELS-1.3.1"->Monocle131ModelCli.run(args,out,err);
            case "GRAPH-1.3.3"->Monocle133GraphCli.run(args,out,err);
            case "ALIGN-1.3.7"->Monocle137WorkflowCli.run(args,out,err);
            case "DIFFERENTIAL-2.26.0"->Monocle2DifferentialCli.run(args,out,err);
            default->throw new AssertionError(action);
        };
        assertEquals(0,status);assertEquals(status,JLinAlgCli.run(new String[]{"monocle-tools",action,"--help"},stream(root),stream(rootErrors)));
        assertArrayEquals(direct.toByteArray(),root.toByteArray());assertArrayEquals(directErrors.toByteArray(),rootErrors.toByteArray());
    }
    @ParameterizedTest
    @ValueSource(strings={"graph-1.0.0","GRAPH-1.0.1","RESPONSES","NEIGHBORS","WEIGHTS","MODELS","unknown"})
    void editionAliasesAndUnlistedActionsNeverSelectAnApproximateWorkflow(String action) {
        var out=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();assertEquals(2,JLinAlgCli.run(new String[]{"monocle-tools",action,"--help"},stream(out),stream(errors)));assertEquals(0,out.size());assertTrue(errors.toString(StandardCharsets.UTF_8).contains("choose an explicit monocle-tools action"));
    }
    @Test void helpDistinguishesPreparationFromTestingAndNamesTheAdmissionFlag() {
        var out=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"monocle-tools","--help"},stream(out),stream(new ByteArrayOutputStream())));String text=out.toString(StandardCharsets.UTF_8);assertTrue(text.contains("response_preparation_admitted"));assertTrue(text.contains("RESPONSES-1.0.0 need no R runtime"));assertTrue(text.contains("modified paper graph tests and whole-paper reproduction remain separate"));
    }
}
