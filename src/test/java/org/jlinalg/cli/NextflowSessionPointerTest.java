/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowSessionPointerTest {
    @TempDir Path temp;
    private Path root(String id,String status)throws Exception {
        Path root=temp.toRealPath();Files.createDirectory(root.resolve("attempts"));Files.createDirectory(root.resolve("attempts").resolve(id));NextflowFiles.writeJson(root.resolve("latest.json"),Map.of("attempt",id,"status",status));return root;
    }
    @Test void latestPointerNamesTheNewestRetainedAttempt()throws Exception {Path root=root("000001","COMPLETED");assertEquals("000001",NextflowWorkflow.readLatest(root).get("attempt"));}
    @Test void anOlderSuccessCannotHideANewerAttempt()throws Exception {Path root=root("000001","COMPLETED");Files.createDirectory(root.resolve("attempts/000002"));assertThrows(IOException.class,()->NextflowWorkflow.readLatest(root));assertThrows(IOException.class,()->NextflowBundle.verify(root,()->{}));}
    @Test void unknownAttemptMembersAreRejected()throws Exception {Path root=root("000001","COMPLETED");Files.createDirectory(root.resolve("attempts/unrecorded"));assertThrows(IOException.class,()->NextflowWorkflow.readLatest(root));}
    @Test void noncompletedLatestStateNeverFallsBackToSuccess()throws Exception {Path root=root("000002","SKIP3");Files.createDirectory(root.resolve("attempts/000001"));assertEquals("SKIP3",NextflowWorkflow.readLatest(root).get("status"));assertThrows(IOException.class,()->NextflowBundle.verify(root,()->{}));}
    private void failed(Path attempt,boolean observed,boolean executed)throws Exception {
        if(executed)Files.createDirectory(attempt.resolve("process"));
        var record=new java.util.LinkedHashMap<String,Object>();record.put("status","FAIL2-no-result-admitted");record.put("exit_code",2);
        if(observed){Files.writeString(attempt.resolve("working-after.json"),"observed state\n");record.put("working_after",NextflowFiles.pin(attempt.resolve("working-after.json"),1000,()->{}));}else record.put("working_after",null);
        NextflowFiles.writeJson(attempt.resolve("FAILURE.json"),record);
    }
    @Test void terminalPreflightFailureCanUseAnEarlierHashedObservedState()throws Exception {Path root=root("000002","SKIP3"),first=Files.createDirectory(root.resolve("attempts/000001"));failed(first,true,false);failed(root.resolve("attempts/000002"),false,false);assertEquals(first,NextflowWorkflow.resumeBaseline(root,root.resolve("attempts/000002")));}
    @Test void anExecutedAttemptWithoutAnAfterStateCannotBeCrossed()throws Exception {Path root=root("000002","FAIL2"),first=Files.createDirectory(root.resolve("attempts/000001"));failed(first,true,false);failed(root.resolve("attempts/000002"),false,true);assertThrows(IOException.class,()->NextflowWorkflow.resumeBaseline(root,root.resolve("attempts/000002")));}
    @Test void changedAfterStateBytesCannotBecomeTheNewResumeBaseline()throws Exception {Path root=root("000001","FAIL2"),attempt=root.resolve("attempts/000001");failed(attempt,true,false);Files.writeString(attempt.resolve("working-after.json"),"changed state\n");assertThrows(IOException.class,()->NextflowWorkflow.resumeBaseline(root,attempt));}
    @Test void unknownTerminalStateCannotBeCrossedToFindAnOlderResult()throws Exception {Path root=root("000002","RUNNING"),first=Files.createDirectory(root.resolve("attempts/000001"));failed(first,true,false);assertThrows(IOException.class,()->NextflowWorkflow.resumeBaseline(root,root.resolve("attempts/000002")));}
}
