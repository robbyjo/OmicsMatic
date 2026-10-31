/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EagleProcessTest {
    @Test void processBudgetsDistinguishHardAddressSpaceFromSampledRss() {
        var limits=EagleProcess.Limits.health();assertTrue(limits.addressSpaceBytes()>limits.sampledRssBytes());
        assertTrue(limits.processes()>1);assertTrue(limits.threads()>limits.processes());assertEquals(256,limits.openFiles());
    }
    @Test void unsafeAndUnboundedResourceRequestsAreRefusedBeforeExecution() {
        assertThrows(IllegalArgumentException.class,()->new EagleProcess.Limits(0,1073741824,60,16777216,256,134217728,256,8,64,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new EagleProcess.Limits(60000,0,60,16777216,256,134217728,256,8,64,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new EagleProcess.Limits(60000,1073741824,0,16777216,256,134217728,256,8,64,536870912,25));
        assertThrows(IllegalArgumentException.class,()->new EagleProcess.Limits(60000,1073741824,60,16777216,256,134217728,256,8,64,536870912,0));
    }
}
