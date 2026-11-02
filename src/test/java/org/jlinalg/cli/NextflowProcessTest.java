/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real Linux processes; Windows checks the explicit unavailable contract. */
class NextflowProcessTest {
    @TempDir Path temp;
    private static final Map<String,String> ENV=Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C");
    private static NextflowProcess.Limits limits(long wall,long bytes,int processes){return new NextflowProcess.Limits(wall,1073741824,30,16777216,256,bytes,256,processes,128,268435456,25);}
    private NextflowProcess.Result run(String script)throws IOException{return run(script,limits(10000,67108864,32),()->{});}
    private NextflowProcess.Result run(String script,NextflowProcess.Limits limits,Runnable cancellation)throws IOException{return NextflowProcess.executeOwned(List.of("/usr/bin/python3","-c",script),ENV,temp.resolve("work"),temp,limits,()->{},cancellation);}
    private boolean linux()throws IOException{
        if(System.getProperty("os.name").equals("Linux"))return true;
        assertThrows(NextflowProcess.Unavailable.class,()->run("pass"));assertFalse(Files.exists(temp.resolve("work")));return false;
    }
    private void failedLedger()throws IOException{
        String text=Files.readString(temp.resolve("work/process.json"));assertTrue(text.contains("FAIL2-retained-no-result"));assertTrue(text.contains("\"owned_live_after_cleanup\":[]"));
    }
    @Test void realSuccessRetainsOriginalOutputAndTerminalOwnership()throws Exception{
        if(!linux())return;var result=run("print('original-control')");assertEquals(0,result.exitCode());assertTrue(result.ownedLiveAfterCleanup().isEmpty());assertEquals("original-control\n",Files.readString(result.stdout()));assertTrue(Files.readString(result.ledger()).contains("completed-process-only"));
    }
    @Test void capturedInputSymlinkWorksWithoutFollowingItsBytesForCapacity()throws Exception{
        if(!linux())return;Path data=Files.writeString(temp.resolve("data.fa"),">test\nACGT\n");
        var result=run("import os,pathlib;os.symlink("+NextflowJson.json(data.toString())+",'staged.fa');pathlib.Path('result.fa').write_bytes(pathlib.Path('staged.fa').read_bytes())");
        assertEquals(Files.readString(data),Files.readString(result.work().resolve("result.fa")));assertTrue(Files.isSymbolicLink(result.work().resolve("staged.fa")));
    }
    @Test void outsideBundleLinkRefusesTheCompleteProcessResult()throws Exception{
        if(!linux())return;assertThrows(IOException.class,()->run("import os;os.symlink('/etc/hosts','outside')"));failedLedger();
    }
    @Test void indirectOutsideLinkIsAlsoRejected()throws Exception{
        if(!linux())return;Files.createSymbolicLink(temp.resolve("outside-parent"),Path.of("/etc"));
        assertThrows(IOException.class,()->run("import os;os.symlink("+NextflowJson.json(temp.resolve("outside-parent/hosts").toString())+",'staged')"));failedLedger();
    }
    @Test void fifoIsObservedAsMetadataWithoutBlocking()throws Exception{
        if(!linux())return;var result=run("import os,pathlib;os.mkfifo('channel');pathlib.Path('done').write_text('complete')");assertEquals("complete",Files.readString(result.work().resolve("done")));assertFalse(Files.isRegularFile(result.work().resolve("channel")));assertTrue(result.ownedLiveAfterCleanup().isEmpty());
    }
    @Test void localSocketIsObservedWithoutAdmittingItAsAResultFile()throws Exception{
        if(!linux())return;var result=run("import socket,pathlib;s=socket.socket(socket.AF_UNIX);s.bind('channel');s.close();pathlib.Path('done').write_text('complete')");assertTrue(Files.exists(result.work().resolve("channel")));assertFalse(Files.isRegularFile(result.work().resolve("channel")));assertTrue(result.ownedLiveAfterCleanup().isEmpty());
    }
    @Test void nonzeroOriginalExitDoesNotReturnAResult()throws Exception{
        if(!linux())return;assertThrows(IOException.class,()->run("import sys;sys.exit(7)"));failedLedger();assertTrue(Files.readString(temp.resolve("work/process.json")).contains("\"exit_code\":7"));
    }
    @Test void cancellationAfterLaunchCleansTheOwnedProcess()throws Exception{
        if(!linux())return;var calls=new AtomicInteger();assertThrows(IOException.class,()->run("import time;time.sleep(30)",limits(10000,67108864,32),()->{if(calls.incrementAndGet()>=3)throw new CancellationException("controlled cancellation");}));failedLedger();
    }
    @Test void wallTimeCapacityDoesNotAdmitAPrefix()throws Exception{
        if(!linux())return;assertThrows(IOException.class,()->run("import time;time.sleep(30)",limits(200,67108864,32),()->{}));failedLedger();
    }
    @Test void workspaceCapacityDoesNotAdmitAPrefix()throws Exception{
        if(!linux())return;assertThrows(IOException.class,()->run("import pathlib;pathlib.Path('too-large').write_bytes(b'x'*1048576)",limits(10000,8192,32),()->{}));failedLedger();
    }
    @Test void processCountCapacityCleansTheWholeFamily()throws Exception{
        if(!linux())return;assertThrows(IOException.class,()->run("import os,time;os.fork();time.sleep(30)",limits(10000,67108864,1),()->{}));failedLedger();
    }
    @Test void orphanIgnoringTermIsStillOwnedAndCleaned()throws Exception{
        if(!linux())return;
        String script="import os,signal,time,pathlib;pid=os.fork();\nif pid==0:\n signal.signal(signal.SIGTERM,signal.SIG_IGN);pathlib.Path('orphan.pid').write_text(str(os.getpid()));time.sleep(30)\nelse:\n while not pathlib.Path('orphan.pid').exists():time.sleep(.01)\n time.sleep(.15)\n";
        var result=run(script);assertTrue(result.ownedLiveAfterCleanup().isEmpty());assertTrue(result.ownedProcesses().size()>=2);assertTrue(Files.exists(result.work().resolve("orphan.pid")));
    }
}
