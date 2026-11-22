/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.sequence.FastaIndexes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastaIndexCliTest {
    @TempDir Path dir;
    @Test void nativeCliRequiresNoRuntimeAndPreservesExistingSourceIndex() throws Exception {
        Path source=dir.resolve("reference.fa"),existing=dir.resolve("reference.fa.fai"),out=dir.resolve("indexed");
        Files.writeString(source,">chr1\nACGT\n");Files.writeString(existing,"preserve existing index");
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);
        assertEquals(0,JLinAlgCli.run(new String[]{"hts-tools","--method","fasta-index","--input",source.toString(),"--out",out.toString(),"--no-config"},stream,stream),bytes.toString());
        assertEquals("preserve existing index",Files.readString(existing));FastaIndexes.validate(source,out.resolve("reference.fa.fai"));
        assertFalse(Files.exists(out.resolve("external.log")));assertTrue(Files.readString(out.resolve("manifest.yaml")).contains("Native Java"));
    }
}
