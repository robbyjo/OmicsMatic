/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FcsGatingInputTest {
    @TempDir Path temporary;
    private Path file(String id)throws Exception {
        byte[] raw;try(var input=getClass().getResourceAsStream("/org/jlinalg/cytometry/gating-input-controls.tsv")){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals("551a488cb64e24b277131558ed929c4dfe5dee6020277dac3430d79fced63ee6",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        String line=new String(raw,StandardCharsets.UTF_8).lines().filter(s->s.startsWith(id+"\t")).findFirst().orElseThrow();Path path=temporary.resolve(id+".fcs");Files.write(path,Base64.getDecoder().decode(line.split("\t",-1)[1]));
        return path;
    }
    private FcsReader reader(String id)throws Exception {return FcsReader.open(file(id),FcsReader.Limits.defaults(),StandardCharsets.UTF_8);}
    @Test void numericSpacePaddingRequiresExplicitPolicyAndNeverChangesRawMetadata()throws Exception {
        Path file=file("numeric-space-padding");byte[] original=Files.readAllBytes(file);
        assertThrows(IOException.class,()->FcsReader.open(file,FcsReader.Limits.defaults(),StandardCharsets.UTF_8));
        try(var reader=FcsReader.open(file,FcsReader.Limits.defaults(),StandardCharsets.UTF_8,FcsReader.TextPolicy.EXACT,FcsReader.NumericPolicy.ALLOW_ASCII_SPACE_PADDING)) {
            assertEquals(" 2 ",reader.keyword("$TOT"));assertEquals(" 1024 ",reader.info().parameters().get(0).range());assertEquals(List.of("X","Y"),reader.info().parameters().stream().map(FcsReader.Parameter::name).toList());
            var bridge=FcsGatingInput.prepare(reader,"AUTO",false,1e-12);double[] stored=new double[2];assertTrue(reader.readEvent(stored));assertArrayEquals(new double[]{1,8},bridge.scale(stored));assertNotNull(bridge.compensation().matrix());assertTrue(reader.readEvent(stored));assertFalse(reader.readEvent(stored));assertEquals(2,reader.summary().eventsRead());
        }
        assertArrayEquals(original,Files.readAllBytes(file));Path tab=file("numeric-tab-padding");assertThrows(IOException.class,()->FcsReader.open(tab,FcsReader.Limits.defaults(),StandardCharsets.UTF_8,FcsReader.TextPolicy.EXACT,FcsReader.NumericPolicy.ALLOW_ASCII_SPACE_PADDING));
    }
    @Test void completeRawFileGainAndAsymmetricSpillHaveAnExplicitDirection()throws Exception {
        try(var reader=reader("gain-and-spill")) {
            var bridge=FcsGatingInput.prepare(reader,"AUTO",false,1e-12);assertEquals(List.of("X","Y"),bridge.names());assertEquals("SPILL",bridge.compensationKeyword());
            double[] stored=new double[2];assertTrue(reader.readEvent(stored));assertArrayEquals(new double[]{2,8},stored);double[] scale=bridge.scale(stored);assertArrayEquals(new double[]{1,2},scale);assertArrayEquals(new double[]{2,8},stored);
            assertArrayEquals(new double[]{.6/.98,1.9/.98},bridge.compensation().matrix().apply(scale),1e-14);
            assertTrue(reader.readEvent(stored));assertArrayEquals(new double[]{-1,1},bridge.scale(stored));assertFalse(reader.readEvent(stored));assertEquals(2,reader.summary().eventsRead());
            assertThrows(IllegalArgumentException.class,()->bridge.scale(new double[]{Double.NaN,0}));assertThrows(IllegalArgumentException.class,()->bridge.scale(new double[]{0}));
        }
    }
    @Test void ambiguousAndLegacyDescriptionsDoNotSelectTheFirstModel()throws Exception {
        try(var reader=reader("two-compensations")) {
            var unresolved=FcsGatingInput.prepare(reader,"AUTO",false,1e-12);assertNotNull(unresolved.compensation().unavailableReason());assertNull(unresolved.compensationKeyword());
            assertNotNull(FcsGatingInput.prepare(reader,"$SPILLOVER",false,1e-12).compensation().matrix());assertNotNull(FcsGatingInput.prepare(reader,"SPILL",false,1e-12).compensation().matrix());
            assertThrows(IOException.class,()->FcsGatingInput.prepare(reader,"NONE",false,1e-12));
        }
        try(var reader=reader("legacy-compensation")){assertNotNull(FcsGatingInput.prepare(reader,"AUTO",false,1e-12).compensation().unavailableReason());}
        try(var reader=reader("no-compensation")) {assertTrue(FcsGatingInput.prepare(reader,"AUTO",false,1e-12).compensation().explicitlyAbsent());assertThrows(FcsReader.UnsupportedFormatException.class,()->FcsGatingInput.prepare(reader,"SPILL",false,1e-12));}
    }
    @Test void legacyLogInterpretationRequiresARecordedExplicitChoice()throws Exception {
        try(var reader=reader("legacy-zero-log-minimum")) {
            assertThrows(FcsReader.UnsupportedFormatException.class,()->FcsGatingInput.prepare(reader,"AUTO",false,1e-12));var bridge=FcsGatingInput.prepare(reader,"AUTO",true,1e-12);
            assertEquals(Math.pow(10,4.*2/1024),bridge.scale(new double[]{2,8})[0]);assertTrue(bridge.warnings().stream().anyMatch(s->s.startsWith("Explicit legacy policy")));assertEquals("4,0",reader.keyword("$P1E"));
        }
        try(var reader=reader("legacy-log-positive")) {var bridge=FcsGatingInput.prepare(reader,"AUTO",false,1e-12);assertEquals(2*Math.pow(10,4.*2/1024),bridge.scale(new double[]{2,8})[0]);}
    }
    @Test void invalidModernLogMetadataSingularCompensationAndGainAreRefused()throws Exception {
        for(String id:List.of("float31-log","bad-standard-diagonal","invalid-gain"))try(var reader=reader(id)) {assertThrows(IOException.class,()->FcsGatingInput.prepare(reader,"AUTO",true,1e-12),id);}
        try(var reader=reader("singular-square")){assertThrows(IllegalArgumentException.class,()->FcsGatingInput.prepare(reader,"AUTO",false,1e-12));}
    }
}
