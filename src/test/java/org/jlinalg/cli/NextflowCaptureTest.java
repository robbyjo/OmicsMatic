/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowCaptureTest {
    @TempDir Path temp;
    private final NextflowCapture.Limits limits=new NextflowCapture.Limits(100,10000,1000);
    private Path owned()throws IOException {return Files.createDirectory(temp.resolve("owned")).toRealPath();}

    @Test void overlappingRootsKeepTheirBasenamesAndShareOneCapturedCopy()throws Exception {
        Path source=Files.createDirectory(temp.resolve("workflow")).toRealPath();
        Path data=Files.createDirectory(source.resolve("data"));Files.writeString(data.resolve("sample.tsv"),"gene\tcount\nA\t3\n");
        Path owned=owned();var snapshot=NextflowCapture.create(owned,Map.of("workflow",source,"data",data),limits,()->{});
        assertEquals(1,snapshot.roots().size());
        var materialized=NextflowCapture.materialize(owned,snapshot,owned.resolve("copies"),limits,()->{});
        assertEquals("workflow",materialized.get("workflow").getFileName().toString());
        assertEquals(materialized.get("workflow").resolve("data"),materialized.get("data"));
        assertEquals("gene\tcount\nA\t3\n",Files.readString(materialized.get("data").resolve("sample.tsv")));
        Files.writeString(materialized.get("data").resolve("sample.tsv"),"working change");
        NextflowCapture.verifyOriginals(snapshot,limits,()->{});
        assertEquals("gene\tcount\nA\t3\n",Files.readString(data.resolve("sample.tsv")));
    }

    @Test void capturedObjectsAreCheckedBeforeMaterialization()throws Exception {
        Path source=Files.writeString(temp.resolve("reads.fastq"),"ACGT").toRealPath(),owned=owned();
        var snapshot=NextflowCapture.create(owned,Map.of("reads",source),limits,()->{});
        String hash=snapshot.roots().get(0).items().get("").original().pin().sha256();
        Files.writeString(owned.resolve("objects").resolve(hash),"TGCA");
        assertThrows(IOException.class,()->NextflowCapture.materialize(owned,snapshot,owned.resolve("copies"),limits,()->{}));
        assertEquals("ACGT",Files.readString(source));
    }

    @Test void newOriginalMembersInvalidateTheCompleteCapture()throws Exception {
        Path source=Files.createDirectory(temp.resolve("workflow")).toRealPath(),owned=owned();Files.writeString(source.resolve("main.nf"),"workflow {}\n");
        var snapshot=NextflowCapture.create(owned,Map.of("workflow",source),limits,()->{});
        Files.writeString(source.resolve("new.config"),"changed");
        assertThrows(IOException.class,()->NextflowCapture.verifyOriginals(snapshot,limits,()->{}));
    }

    @Test void capacityAndCancellationNeverReturnAPartialSnapshot()throws Exception {
        Path source=Files.writeString(temp.resolve("input"),"complete").toRealPath(),owned=owned();
        assertThrows(IOException.class,()->NextflowCapture.create(owned,Map.of("input",source),new NextflowCapture.Limits(10,4,4),()->{}));
        Path second=Files.createDirectory(temp.resolve("second")).toRealPath();
        assertThrows(CancellationException.class,()->NextflowCapture.create(second,Map.of("input",source),limits,()->{throw new CancellationException("controlled");}));
        assertEquals("complete",Files.readString(source));
    }

    @Test void aSourceContainingTheOwnedSessionIsRejected()throws Exception {
        Path source=Files.createDirectory(temp.resolve("workflow")).toRealPath(),owned=Files.createDirectory(source.resolve("session"));
        assertThrows(IOException.class,()->NextflowCapture.create(owned,Map.of("workflow",source),limits,()->{}));
    }
}
