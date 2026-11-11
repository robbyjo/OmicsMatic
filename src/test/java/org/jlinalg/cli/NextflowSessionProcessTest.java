/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowSessionProcessTest {
    @TempDir Path temp;
    private static final Map<String,String> ENV=Map.of("PATH","/usr/bin:/bin","LANG","C.UTF-8");
    @Test void separateAttemptRecordsUseTheSameOriginalWorkingDirectory()throws Exception {
        Path root=temp.toRealPath(),cwd=Files.createDirectory(root.resolve("execution"));
        var command=List.of("/usr/bin/python3","-c","from pathlib import Path;p=Path('counter');p.write_text(str(int(p.read_text())+1) if p.exists() else '1')");
        if(!System.getProperty("os.name").equals("Linux")){assertThrows(NextflowProcess.Unavailable.class,()->NextflowProcess.executeSession(command,ENV,root.resolve("attempt1"),cwd,root,NextflowProcess.Limits.health(),()->{},()->{}));return;}
        var first=NextflowProcess.executeSession(command,ENV,root.resolve("attempt1"),cwd,root,NextflowProcess.Limits.health(),()->{},()->{});
        assertEquals("1",Files.readString(cwd.resolve("counter")));byte[] receipt=Files.readAllBytes(first.ledger());
        var second=NextflowProcess.executeSession(command,ENV,root.resolve("attempt2"),cwd,root,NextflowProcess.Limits.health(),()->{},()->{});
        assertEquals("2",Files.readString(cwd.resolve("counter")));assertArrayEquals(receipt,Files.readAllBytes(first.ledger()));assertTrue(second.ownedLiveAfterCleanup().isEmpty());
        assertEquals(cwd.toString(),NextflowFiles.object(NextflowFiles.json(second.ledger(),65536)).get("working_directory"));
    }
    @Test void sessionCapacityIncludesFilesOutsideTheAttemptRecordDirectory()throws Exception {
        Path root=temp.toRealPath(),cwd=Files.createDirectory(root.resolve("execution"));Files.write(root.resolve("cache"),new byte[16384]);
        var limits=new NextflowProcess.Limits(10000,1073741824,30,1048576,128,8192,128,8,64,536870912,25);
        assertThrows(IOException.class,()->NextflowProcess.executeSession(List.of("/usr/bin/python3","-c","pass"),ENV,root.resolve("attempt"),cwd,root,limits,()->{},()->{}));
        assertEquals(16384,Files.size(root.resolve("cache")));
        if(System.getProperty("os.name").equals("Linux")){var ledger=NextflowFiles.object(NextflowFiles.json(root.resolve("attempt/process.json"),65536));assertEquals("FAIL2-retained-no-result",ledger.get("status"));assertTrue(NextflowFiles.list(ledger.get("owned_live_after_cleanup")).isEmpty());}
    }
}
