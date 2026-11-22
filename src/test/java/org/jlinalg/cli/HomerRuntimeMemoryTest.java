package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class HomerRuntimeMemoryTest {
    @Test void terminalTaskStateIsDistinctFromZeroRssOrMissingState(){assertTrue(HomerRuntime.terminalStatus("State:\tZ (zombie)\n"));assertTrue(HomerRuntime.terminalStatus("State:\tX (dead)\n"));assertFalse(HomerRuntime.terminalStatus("State:\tS (sleeping)\nVmRSS:\t0 kB\n"));assertFalse(HomerRuntime.terminalStatus("VmRSS:\t0 kB\n"));}
    @Test void malformedOrNonterminalStateDoesNotPretendProcessHasExited(){for(String value:new String[]{"State:\tR (running)\n","State:\tI (idle)\n","State:\t\n","","Name:\tZ\n"})assertFalse(HomerRuntime.terminalStatus(value));}
    @Test void liveResidentMemoryUsesKernelKilobytes()throws Exception{assertEquals(123*1024,HomerRuntime.residentStatus("Name:\thomer2\nState:\tR (running)\nVmRSS:\t123 kB\n"));}
    @Test void terminalZombieHasNoAddressSpaceAndDoesNotImplyUnavailableDependency()throws Exception{assertEquals(0,HomerRuntime.residentStatus("Name:\thomer2\nState:\tZ (zombie)\nThreads:\t1\n"));}
    @Test void terminalDeadStateDoesNotRequireVmRss()throws Exception{assertEquals(0,HomerRuntime.residentStatus("State:\tX (dead)\n"));}
    @Test void liveZeroResidentMemoryIsValid()throws Exception{assertEquals(0,HomerRuntime.residentStatus("State:\tS (sleeping)\nVmRSS:\t0 kB\n"));}
    @Test void missingLiveMemoryStillFailsExplicitly(){assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.residentStatus("State:\tR (running)\n"));}
    @Test void invalidUnitsNegativeAndOverflowValuesFailExplicitly(){
        for(String value:new String[]{"123 bytes","-1 kB","9223372036854775807 kB","NaN kB"})assertThrows(HomerRuntime.Unavailable.class,()->HomerRuntime.residentStatus("State:\tR (running)\nVmRSS:\t"+value+"\n"));
    }
}
