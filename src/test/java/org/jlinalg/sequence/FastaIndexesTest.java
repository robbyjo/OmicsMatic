/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FastaIndexesTest {
    @TempDir Path dir;
    @Test void exactLfAndCrlfOffsetsAreIndexedAndStaleIndexesAreRejected() throws Exception {
        for (String newline : new String[]{"\n", "\r\n"}) {
            Path source = dir.resolve(newline.length()==1?"lf.fa":"crlf.fa");
            Files.writeString(source, String.join(newline,">chr1","ACGT","AC",">chr2","TTT", ""));
            var index = FastaIndexes.build(source); assertEquals(2,index.size());
            var first = index.getIndexEntry("chr1"); var second = index.getIndexEntry("chr2");
            assertEquals(6,first.getSize());assertEquals(newline.length()==1?6:7,first.getLocation());
            assertEquals(4,first.getBasesPerLine());assertEquals(4+newline.length(),first.getBytesPerLine());
            assertEquals(newline.length()==1?20:24,second.getLocation());assertEquals(3,second.getSize());
            Path fai = dir.resolve(source.getFileName()+".fai"); index.write(fai);FastaIndexes.validate(source,fai);
            Files.writeString(fai,"chr1\t6\t999\t4\t5\nchr2\t3\t20\t3\t4\n");
            assertTrue(assertThrows(IOException.class,()->FastaIndexes.validate(source,fai)).getMessage().contains("does not match"));
        }
    }
    @Test void emptyAndCompressedReferencesHaveClearNativeFailures() throws Exception {
        Path empty=dir.resolve("empty.fa");Files.writeString(empty,"");assertThrows(IOException.class,()->FastaIndexes.build(empty));
        Path gz=dir.resolve("ref.fa.gz");try(var stream=new GZIPOutputStream(Files.newOutputStream(gz))){stream.write(">chr1\nACGT\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertTrue(assertThrows(IOException.class,()->FastaIndexes.build(gz)).getMessage().contains("uncompressed"));
    }
}
