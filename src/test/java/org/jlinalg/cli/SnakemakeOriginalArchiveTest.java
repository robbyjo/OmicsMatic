/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeOriginalArchiveTest {
    @TempDir Path temp;
    private Path zip(String name,String entry)throws IOException {Path file=temp.resolve(name);try(var output=new ZipOutputStream(Files.newOutputStream(file))){output.putNextEntry(new ZipEntry(entry));output.write("literal bytes\n".getBytes(StandardCharsets.UTF_8));output.closeEntry();}return file;}
    @Test void archiveNamespaceReadsAreScopedAndDoNotExtractFiles()throws Exception {
        Path source=zip("evidence.zip","data/value");byte[] before=Files.readAllBytes(source);String value=SnakemakeOriginalArchive.read(source,root->new String(SnakemakeOriginalFiles.bytes(root.resolve("data/value"),100),StandardCharsets.UTF_8),()->{});assertEquals("literal bytes\n",value);assertArrayEquals(before,Files.readAllBytes(source));assertFalse(Files.exists(temp.resolve("data")));
    }
    @Test void traversalAndAbsoluteEntriesAreRejectedBeforeAnyCallback()throws Exception {
        int index=0;for(String name:java.util.List.of("../outside","/absolute","data/../outside","data\\outside")){Path source=zip("bad-"+index+++".zip",name);var called=new AtomicBoolean();assertThrows(IOException.class,()->SnakemakeOriginalArchive.read(source,root->{called.set(true);return null;},()->{}));assertFalse(called.get());assertFalse(Files.exists(temp.resolve("outside")));}
    }
    @Test void archiveObjectsCanBeCapturedAcrossFilesystemProviders()throws Exception {
        Path source=zip("objects.zip","data/value"),objects=Files.createDirectory(temp.resolve("objects"));byte[] before=Files.readAllBytes(source);
        SnakemakeOriginalArchive.read(source,root->{Path member=root.resolve("data/value");var expected=SnakemakeOriginalFiles.pin(member,100,()->{});String key=SnakemakeOriginalCapture.store(objects,member,expected,100,()->{});assertEquals(expected.sha256(),key);assertEquals("literal bytes\n",Files.readString(objects.resolve(key)));Path copy=temp.resolve("copied-value");SnakemakeOriginalFiles.copyBytes(member,copy,expected.bytes(),()->{});assertEquals(expected,SnakemakeOriginalFiles.pin(copy,100,()->{}));return null;},()->{});
        assertArrayEquals(before,Files.readAllBytes(source));
    }
    @Test void copyCapacityAndExistingDestinationsAreEnforced()throws Exception {
        Path source=Files.writeString(temp.resolve("source"),"literal bytes\n"),shortCopy=temp.resolve("short-copy"),existing=Files.writeString(temp.resolve("existing"),"preserve\n");
        assertThrows(IOException.class,()->SnakemakeOriginalFiles.copyBytes(source,shortCopy,3,()->{}));assertTrue(Files.size(shortCopy)<=3);
        assertThrows(IOException.class,()->SnakemakeOriginalFiles.copyBytes(source,existing,100,()->{}));assertEquals("preserve\n",Files.readString(existing));assertEquals("literal bytes\n",Files.readString(source));
    }
}
