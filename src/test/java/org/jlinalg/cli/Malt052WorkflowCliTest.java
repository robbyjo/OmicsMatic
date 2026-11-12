/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class Malt052WorkflowCliTest {
    @TempDir Path temp;
    @Test void savedLinuxPathsResolveIdenticallyOnWindowsWithoutRevivingOldPaths()throws Exception {
        assertEquals("/old/inputs/references.fa",Malt052WorkflowCli.resolveOriginal("/old/producer","../inputs/./references.fa"));
        assertEquals("/old/db/map.tsv",Malt052WorkflowCli.resolveOriginal("/old/producer","/old/db/ignored/../map.tsv"));
        assertEquals("/inputs/a.fa",Malt052WorkflowCli.resolveOriginal("/","inputs/a.fa"));
        for(String path:List.of("C:\\data\\query.fastq","../../../../escape","bad\npath"))assertThrows(IOException.class,()->Malt052WorkflowCli.resolveOriginal("/old/producer",path));
    }
    @Test void existingAttemptsRemainByteIdentical()throws Exception {
        Path out=Files.createDirectory(temp.resolve("prior")),keep=out.resolve("KEEP");Files.writeString(keep,"retain\n");var error=new ByteArrayOutputStream();
        assertEquals(2,Malt052WorkflowCli.run(new String[]{"--out",out.toString()},stream(new ByteArrayOutputStream()),stream(error)));
        assertEquals("retain\n",Files.readString(keep));assertTrue(error.toString(StandardCharsets.UTF_8).contains("Output must be NEW"));
        try(var paths=Files.list(out)){assertEquals(List.of(keep),paths.toList());}
    }
    @Test void helpStatesExplicitMethodAndProminentRecovery()throws Exception {
        var output=new ByteArrayOutputStream();assertEquals(0,Malt052WorkflowCli.run(new String[]{"--help"},stream(output),stream(new ByteArrayOutputStream())));
        String text=output.toString(StandardCharsets.UTF_8);for(String token:List.of("--method native-accession-alternative","--profile CUSTOM052|STANDALONE","SKIP3","FAIL2","Required action:","--reader-runtime"))assertTrue(text.contains(token),token);
    }
    @Test void malformedAndDuplicateOptionsDoNotCreateAnAttempt()throws Exception {
        Path out=temp.resolve("new");
        for(String[] args:List.of(new String[]{"--out",out.toString(),"--out",out.toString()},new String[]{"--out",out.toString(),"--profile"},new String[]{"--out",out.toString(),"--database","existing"})) {
            var error=new ByteArrayOutputStream();assertEquals(2,Malt052WorkflowCli.run(args,stream(new ByteArrayOutputStream()),stream(error)));assertFalse(Files.exists(out));assertTrue(error.toString(StandardCharsets.UTF_8).contains("NO RESULT ADMITTED"));
        }
    }
    private static PrintStream stream(ByteArrayOutputStream bytes){return new PrintStream(bytes,true,StandardCharsets.UTF_8);}
}
