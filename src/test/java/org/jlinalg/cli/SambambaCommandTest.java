/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SambambaCommandTest {
    @TempDir Path temp;
    @Test void normalCommandRunsTheExactStandaloneStreamingHelp(){
        var expected=new ByteArrayOutputStream();var expectedErrors=new ByteArrayOutputStream();
        var actual=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        assertEquals(0,SambambaStreamingWorkflowCli.run(new String[]{"--help"},new PrintStream(expected),new PrintStream(expectedErrors)));
        assertEquals(0,JLinAlgCli.run(new String[]{"sambamba-tools","--help"},new PrintStream(actual),new PrintStream(errors)));
        assertArrayEquals(expected.toByteArray(),actual.toByteArray());assertArrayEquals(expectedErrors.toByteArray(),errors.toByteArray());
        assertTrue(actual.toString(StandardCharsets.UTF_8).contains("Generated BAI remains unqualified collateral"));
    }
    @Test void globalHelpAdvertisesTheNormalCommand(){
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"--help","--no-config"},new PrintStream(output),new PrintStream(errors)));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("sambamba-tools --help"));assertEquals(0,errors.size());
    }
    @Test void originalArgumentFailuresAndExistingOutputsArePreserved()throws Exception {
        Path out=Files.createDirectory(temp.resolve("existing"));Path sentinel=out.resolve("retained.txt");Files.writeString(sentinel,"retained original data\n");
        var expected=new ByteArrayOutputStream();var expectedErrors=new ByteArrayOutputStream();var actual=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        String[] raw={"--request",temp.resolve("missing.properties").toString(),"--out",out.toString()};
        int original=SambambaStreamingWorkflowCli.run(raw,new PrintStream(expected),new PrintStream(expectedErrors));
        String[] routed={"sambamba-tools",raw[0],raw[1],raw[2],raw[3]};
        assertEquals(original,JLinAlgCli.run(routed,new PrintStream(actual),new PrintStream(errors)));assertEquals(2,original);
        assertArrayEquals(expected.toByteArray(),actual.toByteArray());assertArrayEquals(expectedErrors.toByteArray(),errors.toByteArray());
        assertEquals("retained original data\n",Files.readString(sentinel));
        try(var files=Files.list(out)){assertEquals(1,files.count());}
    }
}
