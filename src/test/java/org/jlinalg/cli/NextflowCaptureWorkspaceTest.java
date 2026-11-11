/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class NextflowCaptureWorkspaceTest {
    @TempDir Path temp;
    private final NextflowCapture.Limits limits=new NextflowCapture.Limits(100,10000,1000);
    private NextflowCapture.Snapshot special(NextflowFiles.Pin content){
        var identity=new NextflowFiles.Identity(content,"opaque-file-key","2026-11-02T00:00:00Z",0644,1);
        var root=new NextflowCapture.Root("r0","/producer/workspace","workspace","directory",Map.of("",new NextflowCapture.Item("directory",null,null,null,0755),"pipe",new NextflowCapture.Item("fifo",identity,null,null,0644)));
        return new NextflowCapture.Snapshot(List.of(root),Map.of("working","r0/workspace"));
    }
    @Test void portableSpecialRecordsExplicitlyHaveNoCapturedStreamBytes()throws Exception {
        var snapshot=special(null);var decoded=NextflowCapture.decode(SimpleJson.parse(NextflowJson.json(snapshot)),limits);
        assertEquals(snapshot,decoded);assertNull(decoded.roots().get(0).items().get("pipe").original().pin());
    }
    @Test void specialMetadataCannotPretendToContainAHashedStream()throws Exception {
        var snapshot=special(new NextflowFiles.Pin(0,NextflowFiles.sha(new byte[0])));
        assertThrows(IOException.class,()->NextflowCapture.decode(SimpleJson.parse(NextflowJson.json(snapshot)),limits));
    }
    @Test void internalHardlinksStayAliasedWithinCopiesWithoutMutatingOriginals()throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"),"Linux execution filesystem contract");
        Path source=Files.createDirectory(temp.resolve("project")).toRealPath();Files.writeString(source.resolve("a"),"original");Files.createLink(source.resolve("b"),source.resolve("a"));Path owned=Files.createDirectory(temp.resolve("owned")).toRealPath();
        var snapshot=NextflowCapture.create(owned,Map.of("workflow",source),limits,()->{});var copy=NextflowCapture.materialize(owned,snapshot,owned.resolve("copies"),limits,()->{}).get("workflow");
        assertTrue(Files.isSameFile(copy.resolve("a"),copy.resolve("b")));assertFalse(Files.isSameFile(source.resolve("a"),copy.resolve("a")));
        Files.writeString(copy.resolve("a"),"working");assertEquals("working",Files.readString(copy.resolve("b")));assertEquals("original",Files.readString(source.resolve("a")));NextflowCapture.verifyOriginals(snapshot,limits,()->{});
    }
    @Test void internalRelativeSymlinkSpellingAndChainsArePreserved()throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"),"Linux execution filesystem contract");
        Path source=Files.createDirectory(temp.resolve("project")).toRealPath();Files.writeString(source.resolve("real.fa"),"ACGT");Files.createSymbolicLink(source.resolve("relative"),Path.of("./real.fa"));Files.createSymbolicLink(source.resolve("chain"),Path.of("relative"));Path owned=Files.createDirectory(temp.resolve("owned")).toRealPath();
        var snapshot=NextflowCapture.create(owned,Map.of("workflow",source),limits,()->{});Path copy=NextflowCapture.materialize(owned,snapshot,owned.resolve("copies"),limits,()->{}).get("workflow");
        assertEquals("./real.fa",Files.readSymbolicLink(copy.resolve("relative")).toString());assertEquals("relative",Files.readSymbolicLink(copy.resolve("chain")).toString());assertEquals("ACGT",Files.readString(copy.resolve("chain")));NextflowCapture.verifyOriginals(snapshot,limits,()->{});
    }
    @Test void danglingAuxiliaryWorkspaceLinksAreMetadataButNotValidOriginalInputs()throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"),"Linux execution filesystem contract");
        Path owned=Files.createDirectory(temp.resolve("owned")).toRealPath(),work=Files.createDirectory(owned.resolve("workspace"));Files.createDirectory(owned.resolve("objects"));Files.createSymbolicLink(work.resolve("aux"),Path.of("not-produced"));
        var snapshot=NextflowCapture.working(owned,work,limits,()->{});assertNull(snapshot.roots().get(0).items().get("aux").resolved());assertEquals(snapshot,NextflowCapture.decode(SimpleJson.parse(NextflowJson.json(snapshot)),limits));
        Path other=Files.createDirectory(temp.resolve("other")).toRealPath();assertThrows(IOException.class,()->NextflowCapture.create(other,Map.of("workflow",work),limits,()->{}));
    }
    @Test @Timeout(10) void fifoAndSocketWorkspaceMetadataNeverOpenTheirStreams()throws Exception {
        assumeTrue(System.getProperty("os.name").equals("Linux"),"Linux execution filesystem contract");
        Path owned=Files.createDirectory(temp.resolve("owned")).toRealPath(),work=Files.createDirectory(owned.resolve("workspace"));Files.createDirectory(owned.resolve("objects"));
        Process make=new ProcessBuilder("/usr/bin/python3","-c","import os,socket;os.mkfifo('pipe');s=socket.socket(socket.AF_UNIX);s.bind('socket');s.close()").directory(work.toFile()).start();assertEquals(0,make.waitFor());
        var snapshot=NextflowCapture.working(owned,work,limits,()->{});var items=snapshot.roots().get(0).items();assertEquals("fifo",items.get("pipe").kind());assertEquals("socket",items.get("socket").kind());assertNull(items.get("pipe").original().pin());assertNull(items.get("socket").original().pin());assertEquals(snapshot,NextflowCapture.decode(SimpleJson.parse(NextflowJson.json(snapshot)),limits));
        try(var blobs=Files.list(owned.resolve("objects"))){assertEquals(0,blobs.count());}
        Path other=Files.createDirectory(temp.resolve("other")).toRealPath();assertThrows(IOException.class,()->NextflowCapture.create(other,Map.of("workflow",work),limits,()->{}));
    }
}
