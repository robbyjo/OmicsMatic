/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NextflowCompletionAuditTest {
    @TempDir Path temp;
    private static final String HEADER="task_id\thash\tname\tstatus\texit\n";
    private static final String SUCCESS="1\taa/123456\tCOUNT\tCOMPLETED\t0\n";
    private static String vector(long succeeded,long failed,long ignored,long retries){return "Jan-01 [main] DEBUG nextflow.trace.WorkflowStatsObserver - Workflow completed > WorkflowStats[succeededCount="+succeeded+"; failedCount="+failed+"; ignoredCount="+ignored+"; cachedCount=0; pendingCount=0; submittedCount=0; runningCount=0; retriesCount="+retries+"; abortedCount=0; peakMemory=64 MB; ]\n";}
    private static final String END="Jan-01 [main] DEBUG n.script.ScriptRunner - > Execution complete -- Goodbye\n";
    private NextflowCompletionAudit.Result read(String log,String trace)throws IOException{
        Files.writeString(temp.resolve("nextflow.log"),log);Files.writeString(temp.resolve("trace.tsv"),trace);return NextflowCompletionAudit.read(temp.resolve("nextflow.log"),temp.resolve("trace.tsv"),0,NextflowCompletionAudit.Limits.defaults(),()->{});
    }
    @Test void recoveredRetryKeepsFailedAttemptAndMarksExecutionRecovery()throws Exception{
        var result=read(vector(1,1,0,1)+END,HEADER+"1\taa/123456\tCOUNT\tFAILED\t7\n2\tbb/abcdef\tCOUNT\tCOMPLETED\t0\n");assertTrue(result.executionSucceeded());assertEquals(NextflowCompletionAudit.Outcome.COMPLETE_AFTER_RETRIES,result.outcome());assertEquals(1,result.trace().other());assertFalse(result.trace().allObservedTasksSuccessful());
    }
    @Test void ignoredFailureRemainsIncompleteDespiteExitZero()throws Exception{
        var result=read(vector(0,1,1,0)+END,HEADER+"1\taa/123456\tCOUNT\tFAILED\t7\n");assertFalse(result.executionSucceeded());assertEquals(NextflowCompletionAudit.Outcome.FAILED_OR_INCOMPLETE,result.outcome());
    }
    @Test void zeroTasksAreExplicitAndDoNotEstablishAnAnalysis()throws Exception{
        var result=read(vector(0,0,0,0)+END,HEADER);assertEquals(NextflowCompletionAudit.Outcome.EMPTY,result.outcome());assertFalse(result.hasObservedTasks());
    }
    @Test void duplicateMissingAndTruncatedTerminalReceiptsAreRejected(){
        for(String log:new String[]{vector(1,0,0,0)+vector(1,0,0,0)+END,END,vector(1,0,0,0),END+vector(1,0,0,0),(vector(1,0,0,0)+END).stripTrailing()})assertThrows(IOException.class,()->read(log,HEADER+SUCCESS));
    }
    @Test void nonterminalOrImpossibleFailedRowsCannotMasqueradeAsRecoveredRetries(){
        for(String row:new String[]{"1\taa/123456\tCOUNT\tRUNNING\t-\n","1\taa/123456\tCOUNT\tFAILED\t0\n"})assertThrows(IOException.class,()->read(vector(1,1,0,1)+END,HEADER+row+"2\tbb/abcdef\tCOUNT\tCOMPLETED\t0\n"));
    }
    @Test void completeTraceCounterMismatchIsRejected(){assertThrows(IOException.class,()->read(vector(2,0,0,0)+END,HEADER+SUCCESS+"2\tbb/abcdef\tOTHER\tFAILED\t7\n"));}
    @Test void malformedOrOverflowingCountersCannotReturnAResult(){
        for(String log:new String[]{vector(1,0,0,0).replace("succeededCount=1","succeededCount=1.0")+END,vector(1,0,0,0).replace("cachedCount=0","succeededCount=2; cachedCount=0")+END,vector(0,0,1,0)+END,vector(Long.MAX_VALUE,1,0,1)+END})assertThrows(IOException.class,()->read(log,HEADER+SUCCESS));
    }
    @Test void completeLogByteLineAndCancellationCapacitiesAreEnforced()throws Exception{
        read(vector(1,0,0,0)+END,HEADER+SUCCESS);Path log=temp.resolve("nextflow.log"),trace=temp.resolve("trace.tsv");assertThrows(IOException.class,()->NextflowCompletionAudit.read(log,trace,0,new NextflowCompletionAudit.Limits(8,32768,NextflowTraceAudit.Limits.defaults()),()->{}));assertThrows(IOException.class,()->NextflowCompletionAudit.read(log,trace,0,new NextflowCompletionAudit.Limits(1048576,8,NextflowTraceAudit.Limits.defaults()),()->{}));assertThrows(CancellationException.class,()->NextflowCompletionAudit.read(log,trace,0,NextflowCompletionAudit.Limits.defaults(),()->{throw new CancellationException("controlled");}));
    }
    record Original(String id,int exit,int rows,String outcome,byte[] log,byte[] trace){@Override public String toString(){return id;}}
    static Stream<Original> originals()throws Exception{
        byte[] raw;try(var stream=NextflowCompletionAuditTest.class.getResourceAsStream("nextflow/completion-controls.zip")){assertNotNull(stream);raw=stream.readNBytes(1048577);}
        assertEquals("38b449db6099c06554e24e3c7eb6812b0cd297512a1496fd33cab49812764278",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Map<String,byte[]> files=new HashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())files.put(entry.getName(),zip.readNBytes(1048577));}
        String[] lines=new String(files.get("expected.tsv"),StandardCharsets.UTF_8).split("\n");assertEquals(22,lines.length);var cases=new ArrayList<Original>();for(int i=1;i<lines.length;i++){String[] fields=lines[i].split("\t");cases.add(new Original(fields[0],Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),fields[3],files.get(fields[0]+"/nextflow.log"),files.get(fields[0]+"/trace.tsv")));}return cases.stream();
    }
    @ParameterizedTest(name="{0}") @MethodSource("originals")
    void originalEngineCompletionMatchesItsCompleteTrace(Original original)throws Exception{
        Path log=Files.write(temp.resolve("nextflow.log"),original.log),trace=Files.write(temp.resolve("trace.tsv"),original.trace);
        if(original.exit!=0){assertThrows(IOException.class,()->NextflowCompletionAudit.read(log,trace,original.exit,NextflowCompletionAudit.Limits.defaults(),()->{}));return;}
        var result=NextflowCompletionAudit.read(log,trace,0,NextflowCompletionAudit.Limits.defaults(),()->{});assertEquals(NextflowCompletionAudit.Outcome.valueOf(original.outcome),result.outcome());assertEquals(original.rows,result.trace().taskRows());
        assertEquals(!original.outcome.equals("FAILED_OR_INCOMPLETE"),result.executionSucceeded());if(original.outcome.equals("COMPLETE_AFTER_RETRIES")){assertEquals(1,result.stats().retries());assertEquals(1,result.trace().other());}if(original.outcome.equals("EMPTY"))assertFalse(result.hasObservedTasks());
    }
}
