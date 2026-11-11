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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NextflowTraceAuditTest {
    @TempDir Path temp;
    private int sequence;
    private static final String HEADER="task_id\thash\tname\tstatus\texit\n";
    private static final String FIRST="1\taa/123456\tCOUNT (a)\tCOMPLETED\t0\n";
    private static final NextflowTraceAudit.Limits LIMITS=NextflowTraceAudit.Limits.defaults();
    private Path trace(String text)throws IOException{return trace(text.getBytes(StandardCharsets.UTF_8));}
    private Path trace(byte[] raw)throws IOException{Path path=temp.resolve("trace-"+(sequence++)+".tsv");return Files.write(path,raw);}
    private NextflowTraceAudit.Summary read(String text,long rows,int exit)throws IOException{return NextflowTraceAudit.read(trace(text),rows,exit,LIMITS,()->{});}
    private void invalid(String text,long rows){assertThrows(IOException.class,()->read(text,rows,0));}

    @Test void completedAndCachedRowsHaveCompleteDigest()throws Exception{
        String text=HEADER+FIRST+"2\tbb/abcdef\tGATHER\tCACHED\t0\n";var result=read(text,2,0);
        assertEquals(2,result.taskRows());assertEquals(1,result.completed());assertEquals(1,result.cached());assertEquals(0,result.other());assertTrue(result.allObservedTasksSuccessful());
        assertEquals(text.getBytes(StandardCharsets.UTF_8).length,result.bytes());assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))),result.sha256());
    }
    @Test void ignoredFailedTaskIsNotSuccessDespiteZeroEngineExit()throws Exception{
        var result=read(HEADER+FIRST+"2\tbb/abcdef\tGATHER\tFAILED\t7\n",2,0);
        assertEquals(1,result.other());assertEquals(NextflowTraceAudit.Outcome.FAILED_OR_INCOMPLETE,result.outcome());assertFalse(result.allObservedTasksSuccessful());
    }
    @Test void emptyTraceDoesNotEstablishAnAnalysis()throws Exception{var result=read(HEADER,0,0);assertEquals(NextflowTraceAudit.Outcome.EMPTY,result.outcome());assertFalse(result.allObservedTasksSuccessful());}
    @Test void nonzeroEngineExitOverridesSuccessfulRows()throws Exception{assertEquals(NextflowTraceAudit.Outcome.FAILED_OR_INCOMPLETE,read(HEADER+FIRST,1,1).outcome());assertEquals(NextflowTraceAudit.Outcome.FAILED_OR_INCOMPLETE,read(HEADER,0,1).outcome());}
    @Test void abortedAndRunningRowsStayIncomplete()throws Exception{for(String status:new String[]{"ABORTED","RUNNING","SUBMITTED","NEW"})assertFalse(read(HEADER+"1\t-\tCOUNT\t"+status+"\t-\n",1,0).allObservedTasksSuccessful());}
    @Test void missingAndExtraRowsRefuseWholeRequest(){invalid(HEADER+FIRST,2);invalid(HEADER+FIRST,0);invalid(HEADER,1);}
    @Test void repeatedTaskIdRetainsAttemptBoundary(){invalid(HEADER+FIRST+FIRST,2);}
    @Test void malformedLateRowAndUnterminatedPrefixAreRejected(){invalid(HEADER+FIRST+"2\tbroken\n",2);invalid(HEADER+FIRST.substring(0,FIRST.length()-1),1);invalid(HEADER+FIRST+"\n",1);}
    @Test void malformedUtf8AfterAValidRowIsRejected()throws Exception{byte[] prefix=(HEADER+FIRST).getBytes(StandardCharsets.UTF_8),raw=java.util.Arrays.copyOf(prefix,prefix.length+2);raw[prefix.length]=(byte)0xc3;raw[prefix.length+1]=10;assertThrows(IOException.class,()->NextflowTraceAudit.read(trace(raw),2,0,LIMITS,()->{}));}
    @Test void duplicateMissingAndExtraColumnsAreRejected(){invalid("task_id\thash\tname\tstatus\tstatus\texit\n",0);invalid("task_id\thash\tstatus\texit\n",0);invalid(HEADER+FIRST.stripTrailing()+"\textra\n",1);}
    @Test void invalidIdentitiesStatusesAndExitsAreRejected(){for(String row:new String[]{"0\taa/123456\tA\tCOMPLETED\t0\n","9223372036854775808\taa/123456\tA\tCOMPLETED\t0\n","1\t-\tA\tCOMPLETED\t0\n","1\taa/123456\tA\tCOMPLETED\t7\n","1\taa/123456\tA\tCACHED\t-\n","1\taa/123456\tA\tUNKNOWN\t0\n","1\taa/123456\tA\tFAILED\t-2\n"})invalid(HEADER+row,1);}
    @Test void crlfAndNonAsciiNamesArePreserved()throws Exception{var result=read((HEADER+FIRST.replace("COUNT (a)","COUNT (α)")).replace("\n","\r\n"),1,0);assertTrue(result.allObservedTasksSuccessful());invalid(HEADER+FIRST.replace("COUNT (a)","COUNT\r(a)"),1);}
    @Test void completeInputCapacitiesAreEnforced()throws Exception{
        Path path=trace(HEADER+FIRST);
        assertThrows(IOException.class,()->NextflowTraceAudit.read(path,1,0,new NextflowTraceAudit.Limits(8,32768,10,128),()->{}));
        assertThrows(IOException.class,()->NextflowTraceAudit.read(path,1,0,new NextflowTraceAudit.Limits(1000,8,10,128),()->{}));
        Path many=trace("task_id\thash\tname\tstatus\texit\textra\n");assertThrows(IOException.class,()->NextflowTraceAudit.read(many,0,0,new NextflowTraceAudit.Limits(1000,100,10,5),()->{}));
        assertThrows(IllegalArgumentException.class,()->NextflowTraceAudit.read(path,2,0,new NextflowTraceAudit.Limits(1000,100,1,5),()->{}));
    }
    @Test void cancellationDoesNotReturnAPrefixSummary()throws Exception{Path path=trace(HEADER+FIRST+"2\tbb/abcdef\tGATHER\tCACHED\t0\n");var calls=new AtomicInteger();assertThrows(CancellationException.class,()->NextflowTraceAudit.read(path,2,0,LIMITS,()->{if(calls.incrementAndGet()==3)throw new CancellationException("controlled");}));}
    @Test void directoryAndUndeclaredExpectedCountAreRejected(){assertThrows(IOException.class,()->NextflowTraceAudit.read(temp,0,0,LIMITS,()->{}));assertThrows(IllegalArgumentException.class,()->NextflowTraceAudit.read(temp,-1,0,LIMITS,()->{}));}

    record Original(String key,int rows,int exit,NextflowTraceAudit.Outcome outcome,byte[] trace){@Override public String toString(){return key;}}
    static Stream<Original> originals()throws Exception{
        byte[] raw;
        try(var stream=NextflowTraceAuditTest.class.getResourceAsStream("nextflow/original-engine-controls.zip")){assertNotNull(stream);raw=stream.readNBytes(1048577);}
        assertEquals("d1777e588dddb99d68e345b2184345ca511b7b64a44d639166df167cb7896093",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Map<String,byte[]> files=new HashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().equals("expected.tsv")||entry.getName().endsWith("/trace.tsv"))files.put(entry.getName(),zip.readNBytes(1048577));}
        var cases=new ArrayList<Original>();String[] lines=new String(files.get("expected.tsv"),StandardCharsets.UTF_8).split("\n");assertEquals(16,lines.length);
        for(int i=1;i<lines.length;i++){String[] cell=lines[i].split("\t");String key=cell[0]+"/"+cell[1];assertNotNull(files.get(key+"/trace.tsv"));cases.add(new Original(key,Integer.parseInt(cell[2]),Integer.parseInt(cell[3]),NextflowTraceAudit.Outcome.valueOf(cell[4]),files.get(key+"/trace.tsv")));}
        return cases.stream();
    }
    @ParameterizedTest(name="{0}") @MethodSource("originals")
    void originalEngineTraceHasExpectedOutcome(Original original)throws Exception{
        var summary=NextflowTraceAudit.read(trace(original.trace),original.rows,original.exit,LIMITS,()->{});
        assertEquals(original.outcome,summary.outcome());assertEquals(original.rows,summary.taskRows());
        if(original.key.endsWith("/success"))assertEquals(4,summary.completed());
        if(original.key.endsWith("/resume"))assertEquals(4,summary.cached());
        if(original.key.endsWith("task-failure"))assertEquals(1,summary.other());
    }
}
