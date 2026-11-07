/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SignacToolsDispatchTest {
    static Stream<String> workflows(){return Stream.of("normalize","lsi","fragments","annotations","motifs","scan","links","markers","peaks","coverage","chromvar");}
    @ParameterizedTest @MethodSource("workflows") void publicWorkflowHelpRoutesWithoutExternalAnalysis(String workflow){var log=new ByteArrayOutputStream();var out=new PrintStream(log,true,StandardCharsets.UTF_8);assertEquals(0,JLinAlgCli.run(new String[]{"signac-tools","--workflow",workflow,"--help"},out,out));assertTrue(log.toString(StandardCharsets.UTF_8).contains("--request"));}
    static Stream<Arguments> invalid(){return Stream.of(new String[]{},new String[]{"--workflow"},new String[]{"--workflow","missing","--help"},new String[]{"--request","unopened.json"},new String[]{"--workflow","normalize","--request","unopened.json","--extra"},new String[]{"--workflow","normalize","--unknown"}).map(a->Arguments.of((Object)a));}
    @ParameterizedTest @MethodSource("invalid") void malformedDispatchCannotGuessOrExecuteAnotherWorkflow(String[] args){var log=new ByteArrayOutputStream();var out=new PrintStream(log,true,StandardCharsets.UTF_8);assertEquals(2,SignacToolsCli.run(args,out,out));assertTrue(log.toString(StandardCharsets.UTF_8).contains("FAIL2"));}
    @Test void mainDiscoveryListsNewRouteAndExactWorkflowPopulation()throws Exception{var log=new ByteArrayOutputStream();var out=new PrintStream(log,true,StandardCharsets.UTF_8);assertEquals(0,JLinAlgCli.run(new String[]{"--help"},out,out));assertTrue(log.toString(StandardCharsets.UTF_8).contains("signac-tools --help"));log.reset();assertEquals(0,JLinAlgCli.run(new String[]{"signac-tools","--source-report"},out,out));var index=SeuratDifferentialContracts.object(SimpleJson.parse(log.toString(StandardCharsets.UTF_8)),"index");var rows=SeuratDifferentialContracts.list(index.get("workflows"),"workflows");assertEquals(11,rows.size());Set<String> names=new HashSet<>();for(Object o:rows)names.add((String)SeuratDifferentialContracts.object(o,"workflow").get("name"));assertEquals(workflows().collect(java.util.stream.Collectors.toSet()),names);assertEquals(Boolean.FALSE,index.get("reinterprets_request_paths_or_scientific_settings"));assertEquals(Boolean.FALSE,index.get("analysis_installs_software"));}
}
