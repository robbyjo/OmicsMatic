package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RepeatProcessProcTest {
    private static final long ABSENT_PID=2000000000L;
    private String stat(String state,long start) {
        String[] fields=new String[20];Arrays.fill(fields,"0");fields[0]=state;fields[1]="1";fields[2]="12";fields[3]="12";fields[19]=Long.toString(start);
        return "12 (controlled worker) "+String.join(" ",fields)+"\n";
    }
    @Test void completeProcMetricsRetainTheKernelIdentityAndUnits() throws Exception {
        var value=RepeatProcess.sample(ABSENT_PID,(path,max)->path.getFileName().toString().equals("stat")?stat("S",123):"VmRSS:\t12 kB\nThreads:\t3\n");
        assertEquals(123,value.identity().startTicks());assertEquals(12288,value.rssBytes());assertEquals(3,value.threads());
    }
    @Test void aDescriptorEsrchIsGoneWhenTheIdentityRecheckFindsNoProcess() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        assertNull(RepeatProcess.sample(ABSENT_PID,(path,max)->{
            if(path.getFileName().toString().equals("stat")){
                if(reads.getAndIncrement()==0)return stat("S",123);throw new NoSuchFileException(path.toString());
            }
            throw new IOException("No such process");
        }));assertEquals(2,reads.get());
    }
    @Test void aTerminalIdentityAlsoProvesThatTheRacingSampleHasEnded() throws Exception {
        for(String terminal:Arrays.asList("Z","X")){
            AtomicInteger reads=new AtomicInteger();assertNull(RepeatProcess.sample(ABSENT_PID,(path,max)->{
                if(path.getFileName().toString().equals("stat"))return stat(reads.getAndIncrement()==0?"S":terminal,123);
                throw new IOException("No such process");
            }));
        }
    }
    @Test void aReusedPidIsNotCountedAsTheOriginalOwnedIdentity() throws Exception {
        AtomicInteger reads=new AtomicInteger();assertNull(RepeatProcess.sample(ABSENT_PID,(path,max)->{
            if(path.getFileName().toString().equals("stat"))return stat("S",reads.getAndIncrement()==0?123:124);
            throw new IOException("No such process");
        }));
    }
    @Test void errorTextAloneCannotHideAStillLiveIdentity() {
        IOException expected=new IOException("No such process");
        IOException actual=assertThrows(IOException.class,()->RepeatProcess.sample(ABSENT_PID,(path,max)->{
            if(path.getFileName().toString().equals("stat"))return stat("S",123);throw expected;
        }));assertSame(expected,actual);
    }
    @Test void livePermissionErrorsRemainFailures() {
        IOException expected=new IOException("Permission denied");assertSame(expected,assertThrows(IOException.class,()->RepeatProcess.sample(ABSENT_PID,(path,max)->{
            if(path.getFileName().toString().equals("stat"))return stat("S",123);throw expected;
        })));
    }
    @Test void anUnreadableLiveRecheckCannotBeTreatedAsVerifiedDisappearance() {
        long live=ProcessHandle.current().pid();AtomicInteger reads=new AtomicInteger();IOException expected=new IOException("Permission denied");
        IOException actual=assertThrows(IOException.class,()->RepeatProcess.sample(live,(path,max)->{
            if(path.getFileName().toString().equals("stat")&&reads.getAndIncrement()==0)return stat("S",123);throw expected;
        }));assertSame(expected,actual);
    }
    @Test void twoFailedProcReadsNeedIndependentNativeLivenessEvidence() throws Exception {
        assertTrue(ProcessHandle.of(ABSENT_PID).isEmpty());
        assertNull(RepeatProcess.sample(ABSENT_PID,(path,max)->{throw new IOException("No such process");}));
    }
    @Test void unknownRssUnitsAndMissingThreadMetricsRemainInvalidForLiveIdentities() {
        for(String status:Arrays.asList("VmRSS:\t12 MB\nThreads:\t2\n","VmRSS:\t12 kB\n"))
            assertThrows(IOException.class,()->RepeatProcess.sample(ABSENT_PID,(path,max)->path.getFileName().toString().equals("stat")?stat("S",123):status));
    }
    @Test void malformedLiveIdentityDataDoesNotProduceASample() {
        for(String value:Arrays.asList("12 (unfinished)","12 (worker) S 1 2","12 (worker) "))
            assertThrows(IOException.class,()->RepeatProcess.sample(ProcessHandle.current().pid(),(path,max)->value));
    }
    @Test void statusDataCannotBeAssignedAcrossAReuseBetweenTheTwoIdentityReads() throws Exception {
        AtomicInteger reads=new AtomicInteger();assertNull(RepeatProcess.sample(ABSENT_PID,(path,max)->
            path.getFileName().toString().equals("stat")?stat("S",reads.getAndIncrement()==0?123:124):"VmRSS:\t12 kB\nThreads:\t3\n"));
    }
}
