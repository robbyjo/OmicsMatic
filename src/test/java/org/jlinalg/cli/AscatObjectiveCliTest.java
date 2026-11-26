/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class AscatObjectiveCliTest {
    @TempDir Path temp;
    @Test void helpAndExampleExposeComponentScopeAndRecoveryAction()throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        assertEquals(0,AscatObjectiveCli.run(new String[]{"--help"},new PrintStream(out),new PrintStream(err)));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("does not fit a tumor model"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("SKIP3"));
        assertEquals(0,AscatObjectiveCli.run(new String[]{"--example"},new PrintStream(out),new PrintStream(err)));
        assertEquals(0,err.size());assertEquals(5,AscatObjectiveBundle.parse(NextflowJson.json(AscatObjectiveCli.example()).getBytes(StandardCharsets.UTF_8)).logR().length);
    }
    @Test void missingRequestSkipsDependentCalculationAndExplainsRecovery() {
        var err=new ByteArrayOutputStream();int status=AscatObjectiveCli.run(new String[]{"--request",temp.resolve("missing.json").toString(),"--out",temp.resolve("attempt").toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(err));
        assertEquals(3,status);String text=err.toString(StandardCharsets.UTF_8);assertTrue(text.contains("SKIP3"));assertTrue(text.contains("WARNINGS AND REQUIRED ACTION"));assertFalse(java.nio.file.Files.exists(temp.resolve("attempt")));
    }
    @Test void explicitMissingPairsAndCapacitiesSurviveTypedParsing()throws Exception {
        var value=example();var pair=new ArrayList<Object>();pair.add(null);pair.add(.5);value.put("loci",List.of(pair,List.of(0,.5)));
        var s=AscatObjectiveBundle.parse(NextflowJson.json(value).getBytes(StandardCharsets.UTF_8));assertTrue(Double.isNaN(s.logR()[0]));assertEquals(.5,s.baf()[0]);assertEquals(2,s.logR().length);
    }
    @SuppressWarnings("unchecked") @Test void unknownFieldsUnsupportedDomainAndCompletePopulationOverflowFail()throws Exception {
        var unknown=example();unknown.put("ignore_bad_rows",true);assertThrows(java.io.IOException.class,()->parse(unknown));
        var wrongDomain=example();wrongDomain.put("input_domain","raw-unsegmented-reads");assertThrows(java.io.IOException.class,()->parse(wrongDomain));
        var duplicate=example();duplicate.put("rho",List.of(.5,.5));assertThrows(java.io.IOException.class,()->parse(duplicate));
        var wrongWidth=example();wrongWidth.put("loci",List.of(List.of(0,.5,1)));assertThrows(java.io.IOException.class,()->parse(wrongWidth));
        var tooMany=example();var capacity=new LinkedHashMap<String,Object>((Map<String,Object>)tooMany.get("capacity"));capacity.put("cells",1);tooMany.put("capacity",capacity);assertThrows(java.io.IOException.class,()->parse(tooMany));
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> example()throws Exception {return new LinkedHashMap<>((Map<String,Object>)SimpleJson.parse(NextflowJson.json(AscatObjectiveCli.example())));}
    private static void parse(Object value)throws Exception {AscatObjectiveBundle.parse(NextflowJson.json(value).getBytes(StandardCharsets.UTF_8));}
}
