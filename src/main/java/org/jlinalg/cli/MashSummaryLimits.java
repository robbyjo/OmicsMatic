/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.jlinalg.shrinkage.MashSharing;

/** Current caller ceilings also constrain every declared native-source replay limit. */
record MashSummaryLimits(MashLimits nativeReplay,MashSharing.Limits summary,long requestBytes,long bundleBytes,int entries) {
    MashSummaryLimits {
        if(nativeReplay==null||summary==null||requestBytes<65536||requestBytes>1_073_741_824L||bundleBytes<65536||bundleBytes>(8L<<30)||entries<16||entries>20000)throw new IllegalArgumentException("Invalid complete mash summary capacities");
    }
    static MashSummaryLimits defaults(){return new MashSummaryLimits(MashLimits.defaults(),MashSharing.Limits.defaults(),16_777_216,536_870_912,20000);}
    MashLimits files(){return new MashLimits(nativeReplay.execution(),bundleBytes,entries);}
    MashLimits requestFiles(){return new MashLimits(nativeReplay.execution(),requestBytes,entries);}
    static MashSummaryLimits parse(Object input)throws IOException {
        try {
            var m=MashLimits.keys(input,Set.of("nativeReplay","summary","requestBytes","bundleBytes","entries"));
            var s=MashLimits.keys(m.get("summary"),Set.of("rows","conditions","inputCells","work","ownedBytes"));
            return new MashSummaryLimits(MashLimits.parse(m.get("nativeReplay")),new MashSharing.Limits(MashLimits.i(s,"rows"),MashLimits.i(s,"conditions"),MashLimits.n(s,"inputCells"),MashLimits.n(s,"work"),MashLimits.n(s,"ownedBytes")),MashLimits.n(m,"requestBytes"),MashLimits.n(m,"bundleBytes"),MashLimits.i(m,"entries"));
        }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("Invalid mash summary capacities",error);}
    }
    static void within(Object requested,Object ceiling)throws IOException {
        if(requested instanceof Map<?,?> a&&ceiling instanceof Map<?,?> b){if(!a.keySet().equals(b.keySet()))throw new IOException("Replay capacity schema differs");for(Object key:a.keySet())within(a.get(key),b.get(key));}
        else if(requested instanceof Number a&&ceiling instanceof Number b){if(a.longValue()>b.longValue())throw new IOException("Declared replay capacity exceeds current caller ceiling");}
        else throw new IOException("Invalid replay capacity value");
    }
}
