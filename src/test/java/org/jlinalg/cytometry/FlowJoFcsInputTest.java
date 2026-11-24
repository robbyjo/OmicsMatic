/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cytometry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FlowJoFcsInputTest {
    @TempDir Path temporary;
    private FcsReader reader(String id) throws Exception {
        byte[] bytes;try(var input=getClass().getResourceAsStream("/org/jlinalg/cytometry/gating-input-controls.tsv")){assertNotNull(input);bytes=input.readAllBytes();}
        assertEquals("551a488cb64e24b277131558ed929c4dfe5dee6020277dac3430d79fced63ee6",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        String row=new String(bytes,StandardCharsets.UTF_8).lines().filter(s->s.startsWith(id+"\t")).findFirst().orElseThrow();Path p=temporary.resolve(id+".fcs");Files.write(p,Base64.getDecoder().decode(row.split("\t",-1)[1]));return FcsReader.open(p,FcsReader.Limits.defaults(),StandardCharsets.UTF_8);
    }
    @Test void sourceGainsAreAppliedWithoutApplyingFcsCompensationInAdditionToWorkspaceCompensation() throws Exception {
        try(var reader=reader("gain-and-spill")) {var bridge=new FlowJoFcsInput(reader,FlowJoFcsInput.LabelPolicy.EXACT,FlowJoFcsInput.TimePolicy.NONE,null,false);double[] row=new double[2];assertTrue(reader.readEvent(row));assertArrayEquals(new double[]{1,2},bridge.scale(row));assertArrayEquals(new double[]{2,8},row);assertTrue(bridge.warnings().stream().anyMatch(s->s.contains("not applied in addition")));assertThrows(IllegalArgumentException.class,()->bridge.scale(new double[]{Double.NaN,0}));assertThrows(IllegalArgumentException.class,()->bridge.scale(new double[]{0}));}
    }
    @Test void missingExplicitTimeChannelIsRefusedInsteadOfGuessingFromChannelPosition() throws Exception {
        try(var reader=reader("gain-and-spill")) {assertThrows(IOException.class,()->new FlowJoFcsInput(reader,FlowJoFcsInput.LabelPolicy.EXACT,FlowJoFcsInput.TimePolicy.APPLY_FCS_TIMESTEP_IGNORE_TIME_GAIN,"Time",false));assertThrows(IOException.class,()->new FlowJoFcsInput(reader,FlowJoFcsInput.LabelPolicy.EXACT,FlowJoFcsInput.TimePolicy.NONE,"X",false));}
    }
    @Test void invalidModernLogDataAndNonpositiveGainAreRefused() throws Exception {
        for(String id:new String[]{"float31-log","invalid-gain"})try(var reader=reader(id)){assertThrows(IOException.class,()->new FlowJoFcsInput(reader,FlowJoFcsInput.LabelPolicy.EXACT,FlowJoFcsInput.TimePolicy.NONE,null,true));}
    }
}
