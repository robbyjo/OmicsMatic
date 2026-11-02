/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowFilesTest {
    @TempDir Path temp;
    @Test void completeFilePinMatchesIndependentDigest()throws Exception{
        byte[] raw="gene\tα😀\n".getBytes(StandardCharsets.UTF_8);Path path=Files.write(temp.resolve("data.tsv"),raw);var got=NextflowFiles.identity(path,raw.length,()->{});assertEquals(raw.length,got.pin().bytes());assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),got.pin().sha256());
    }
    @Test void aChangedFileCannotReturnTheEarlierReadPrefix()throws Exception{
        Path path=Files.write(temp.resolve("data"),new byte[1024]);var calls=new AtomicInteger();assertThrows(IOException.class,()->NextflowFiles.identity(path,4096,()->{if(calls.incrementAndGet()==2)try{Files.writeString(path,"changed");}catch(IOException failure){throw new UncheckedIOException(failure);}}));
    }
    @Test void cancellationDoesNotAdmitAFilePrefix()throws Exception{
        Path path=Files.writeString(temp.resolve("data"),"original");assertThrows(CancellationException.class,()->NextflowFiles.identity(path,100,()->{throw new CancellationException("controlled");}));assertEquals("original",Files.readString(path));
    }
    @Test void invalidUtf8AndCompleteByteCapacitiesAreRejected()throws Exception{
        assertThrows(IOException.class,()->NextflowFiles.utf8(new byte[]{(byte)0xc3,40}));Path path=Files.writeString(temp.resolve("data"),"12345");assertThrows(IOException.class,()->NextflowFiles.bytes(path,4));assertThrows(IOException.class,()->NextflowFiles.bytes(path,Integer.MAX_VALUE));assertThrows(IOException.class,()->NextflowFiles.identity(path,4,()->{}));
    }
    @Test void ordinaryHardlinkedReadOnlyInputsCanBeCaptured()throws Exception{
        Path path=Files.writeString(temp.resolve("data"),"unchanged"),linked=Files.createLink(temp.resolve("alias"),path);assertEquals(NextflowFiles.pin(path,100,()->{}),NextflowFiles.pin(linked,100,()->{}));assertEquals("unchanged",Files.readString(path));
    }
    @Test void emptyMetadataHasACompleteIdentity()throws Exception{
        Path path=Files.write(temp.resolve("empty"),new byte[0]);assertEquals(0,NextflowFiles.bytes(path,0).length);assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",NextflowFiles.pin(path,0,()->{}).sha256());
    }
    @Test void inventoryShapesAndTraversalAreChecked()throws Exception{
        String bad="{\"../outside\":{\"kind\":\"symlink\",\"target\":\"a\"}}";assertThrows(IOException.class,()->NextflowFiles.entries(SimpleJson.parse(bad),10,100));assertThrows(IOException.class,()->NextflowFiles.entries(SimpleJson.parse("{\"a\":{\"kind\":\"device\"}}"),10,100));assertThrows(IOException.class,()->NextflowFiles.directory(temp.resolve("missing")));
    }
}
