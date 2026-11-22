package org.jlinalg.cli;

import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerRuntimeExitMemoryTest {
    private static String stat(String flags,String virtual,String rss){String[] fields=new String[22];Arrays.fill(fields,"0");fields[0]="R";fields[6]=flags;fields[20]=virtual;fields[21]=rss;return "123 (command with ) parentheses) "+String.join(" ",fields);}
    @Test void missingRssRequiresBothExitingFlagAndZeroAddressSpace()throws Exception{assertTrue(HomerRuntime.exitingWithoutAddressSpace(stat("4194308","0","0")));assertFalse(HomerRuntime.exitingWithoutAddressSpace(stat("4194304","0","0")));assertFalse(HomerRuntime.exitingWithoutAddressSpace(stat("4194308","4096","0")));assertFalse(HomerRuntime.exitingWithoutAddressSpace(stat("4194308","0","1")));}
    @Test void exitingTaskWithNoAddressSpacePassesWithoutPretendingItIsAlreadyTerminal()throws Exception{assertEquals(0,HomerRuntime.resident(ProcessHandle.current(),p->"State:\tR (running)\n",p->stat("4194308","0","0")));assertFalse(HomerRuntime.terminalStatus("State:\tR (running)\n"));}
    @Test void malformedAndDeniedMemoryStatRemainFailures(){assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.exitingWithoutAddressSpace("truncated"));assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.exitingWithoutAddressSpace(stat("4","-1","0")));assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.resident(ProcessHandle.current(),p->"State:\tR (running)\n",p->{throw new AccessDeniedException("stat");}));}
}
