/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NfcoreHic210AuditTest {
    @TempDir Path temp;
    private NfcoreHic210Request.Request request()throws Exception{return NfcoreHic210Request.parse(NfcoreHic210RequestTest.request());}
    @Test void gzipMagicIsDecodedEvenWhenBlobBasenameHasNoExtension()throws Exception{Path p=temp.resolve("source-sha256-blob");try(var stream=new GZIPOutputStream(Files.newOutputStream(p))){stream.write("key\t123\r\n".getBytes(StandardCharsets.UTF_8));}assertEquals(Map.of("key",123L),NfcoreHic210Audit.stats(p,request()));}
    @Test void repeatedOrNonfiniteSourceCountTokensFailWithoutRepair()throws Exception{for(String text:List.of("key\t1\nkey\t2\n","key\tNaN\n","key\t-1\n","key\t1.0\n","key\t9223372036854775808\n")){Path p=Files.createTempFile(temp,"source-",".tsv");Files.writeString(p,text);assertThrows(IOException.class,()->NfcoreHic210Audit.stats(p,request()));}}
    @Test void malformedUtf8AndDecodedCapacityAreNotTruncated()throws Exception{Path invalid=temp.resolve("invalid");Files.write(invalid,new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->NfcoreHic210Audit.stats(invalid,request()));Path complete=temp.resolve("complete");Files.writeString(complete,"key\t123\n");var raw=NfcoreHic210RequestTest.request();raw.put("maximum_decoded_characters",3);assertThrows(IOException.class,()->NfcoreHic210Audit.stats(complete,NfcoreHic210Request.parse(raw)));}
}
