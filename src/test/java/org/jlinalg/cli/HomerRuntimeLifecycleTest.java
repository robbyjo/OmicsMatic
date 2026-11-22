package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerRuntimeLifecycleTest {
    @Test void vanishedProcReadRetriesBeforeAdmittingValidLiveRss()throws Exception{var reads=new AtomicInteger();long rss=HomerRuntime.resident(ProcessHandle.current(),h->{if(reads.incrementAndGet()==1)throw new IOException("No such process");return "State:\tS (sleeping)\nVmRSS:\t7 kB\n";});assertEquals(7168,rss);assertEquals(2,reads.get());}
    @Test void transientForkStatusWithoutAddressSpaceRetries()throws Exception{var reads=new AtomicInteger();long rss=HomerRuntime.resident(ProcessHandle.current(),h->reads.incrementAndGet()==1?"State:\tR (running)\n":"State:\tR (running)\nVmRSS:\t4 kB\n");assertEquals(4096,rss);assertEquals(2,reads.get());}
    @Test void deniedMonitoringIsNotRetriedOrPretendedTerminal(){var reads=new AtomicInteger();assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.resident(ProcessHandle.current(),h->{reads.incrementAndGet();throw new AccessDeniedException("/proc/owned/status");}));assertEquals(1,reads.get());}
    @Test void corruptMemoryUnitsAreNotRetriedAsLifecycleAbsence(){var reads=new AtomicInteger();assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.resident(ProcessHandle.current(),h->{reads.incrementAndGet();return "State:\tR (running)\nVmRSS:\t4 bytes\n";}));assertEquals(1,reads.get());}
    @Test void persistentLiveProcReadFailureRemainsUnavailableWithCause(){var reads=new AtomicInteger();var failure=assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.resident(ProcessHandle.current(),h->{reads.incrementAndGet();throw new IOException("No such process");}));assertEquals(6,reads.get());assertNotNull(failure.getCause());assertEquals("No such process",failure.getCause().getMessage());}
    @Test void persistentLiveMissingMemoryFieldDoesNotBecomeZeroRss(){var reads=new AtomicInteger();assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.resident(ProcessHandle.current(),h->{reads.incrementAndGet();return "State:\tR (running)\n";}));assertEquals(6,reads.get());}
}
