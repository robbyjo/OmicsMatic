/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FastpParametersTest {
    @Test void sourceMemoryPoliciesRetainEditionAndAccuracyDifferences()throws Exception {
        for(String version:new String[]{"0.22.0","0.23.2","0.25.0","1.0.1"})assertEquals(1L<<30,FastpParameters.memory(version,FastpParameters.Duplication.defaults()).bloomBufferBytes());
        assertEquals(4L<<30,FastpParameters.memory("0.22.0",new FastpParameters.Duplication(true,true,0)).bloomBufferBytes());
        for(int accuracy=1;accuracy<=6;accuracy++)assertEquals(new long[]{0,1,2,4,8,16,24}[accuracy]<<30,FastpParameters.memory("0.23.2",new FastpParameters.Duplication(true,true,accuracy)).bloomBufferBytes());
        assertEquals(0,FastpParameters.memory("0.23.2",new FastpParameters.Duplication(false,false,0)).bloomBufferBytes());
    }
    @Test void unknownPatchAndUnsupportedAccuracyAreNotSilentlySubstituted() {
        assertThrows(FastpRuntime.Unavailable.class,()->FastpParameters.memory("0.23",FastpParameters.Duplication.defaults()));
        assertThrows(FastpRuntime.Unavailable.class,()->FastpParameters.memory("0.22.0",new FastpParameters.Duplication(true,true,6)));
    }
    @Test void contradictoryDuplicationAndAdapterSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new FastpParameters.Duplication(false,true,0));
        assertThrows(IllegalArgumentException.class,()->new FastpParameters.Duplication(false,false,1));
        assertThrows(IllegalArgumentException.class,()->new FastpParameters.Adapters(false,true,"",""));
        assertThrows(IllegalArgumentException.class,()->new FastpParameters.Adapters(true,false,"ACGN",""));
    }
    @Test void onlySupportedWorkerCountsAreAdmitted() {
        var d=FastpParameters.Options.defaults(1);
        assertThrows(IllegalArgumentException.class,()->new FastpParameters.Options(17,d.adapters(),d.global(),d.cutting(),d.filters(),d.polyG(),d.polyGMinimum(),d.polyX(),d.polyXMinimum(),d.correction(),d.duplication(),d.overlap(),d.storeUnpaired()));
    }
    @Test void pairedOnlyChoicesCannotDisappearOnSingleEndData() {
        var d=FastpParameters.Options.defaults(1);var c=new FastpParameters.Options(1,d.adapters(),d.global(),d.cutting(),d.filters(),d.polyG(),d.polyGMinimum(),d.polyX(),d.polyXMinimum(),true,d.duplication(),d.overlap(),false);
        assertThrows(IOException.class,()->FastpParameters.arguments("0.23.2",false,FastpFastq.Encoding.PHRED33,c,"/owned/input.fq",null));
    }
    @Test void explicitMemoryCannotBeLoweredToMakeAccuracySixRun()throws Exception {
        var d=FastpParameters.Options.defaults(1);var c=new FastpParameters.Options(1,d.adapters(),d.global(),d.cutting(),d.filters(),d.polyG(),d.polyGMinimum(),d.polyX(),d.polyXMinimum(),false,new FastpParameters.Duplication(true,true,6),d.overlap(),false);
        var small=new FastpProcess.Limits(30000,3L<<30,30,1048576,128,8388608,64,8,32,2L<<30,10);
        assertThrows(FastpRuntime.Unavailable.class,()->FastpParameters.requireCapacity("0.23.2",c,small));
        assertEquals(24L<<30,FastpParameters.memory("0.23.2",c.duplication()).bloomBufferBytes());
    }
}
