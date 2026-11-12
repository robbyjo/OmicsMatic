/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import htsjdk.samtools.SAMRecord;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete SAMtools 1.24 flagstat integer categories, separated by vendor QC. */
public final class SamFlagStatistics {
    private static final String[] NAMES={"total","primary","secondary","supplementary","duplicates","primary duplicates",
        "mapped","primary mapped","paired in sequencing","read1","read2","properly paired","with itself and mate mapped",
        "singletons","with mate mapped to a different chr","with mate mapped to a different chr (mapQ >= 5)"};
    private final long[][] counts=new long[2][NAMES.length];
    /** Add one record. Pair categories include primary records only, as in the original. */
    public void add(SAMRecord record) {
        int flags=record.getFlags();long[] c=counts[(flags&512)==0?0:1];increment(c,0);
        if((flags&256)!=0)increment(c,2);
        else if((flags&2048)!=0)increment(c,3);
        else {
            increment(c,1);
            if((flags&1)!=0) {
                increment(c,8);if((flags&2)!=0&&(flags&4)==0)increment(c,11);
                if((flags&64)!=0)increment(c,9);if((flags&128)!=0)increment(c,10);
                if((flags&8)!=0&&(flags&4)==0)increment(c,13);
                if((flags&12)==0) {
                    increment(c,12);
                    if(!record.getReferenceIndex().equals(record.getMateReferenceIndex())) {
                        increment(c,14);if(record.getMappingQuality()>=5)increment(c,15);
                    }
                }
            }
            if((flags&4)==0)increment(c,7);if((flags&1024)!=0)increment(c,5);
        }
        if((flags&4)==0)increment(c,6);if((flags&1024)!=0)increment(c,4);
    }
    private static void increment(long[] counts,int i){counts[i]=Math.addExact(counts[i],1);}
    /** Source-named JSON-compatible groups. Undefined percentages are null. */
    public Map<String,Object> groups() {
        var result=new LinkedHashMap<String,Object>();
        for(int group=0;group<2;group++) {
            var values=new LinkedHashMap<String,Object>();long[] c=counts[group];
            for(int i=0;i<NAMES.length;i++)values.put(NAMES[i],c[i]);
            values.put("mapped %",percent(c[6],c[0]));values.put("primary mapped %",percent(c[7],c[1]));
            values.put("properly paired %",percent(c[11],c[8]));values.put("singletons %",percent(c[13],c[8]));
            result.put(group==0?"QC-passed reads":"QC-failed reads",values);
        }
        return result;
    }
    private static Double percent(long n,long denominator) {
        if(denominator==0)return null;
        double percentage=((float)n/(float)denominator)*100.0;
        // C printf uses nearest-even at decimal ties; Java Formatter uses half-up.
        return java.math.BigDecimal.valueOf(percentage).setScale(2,java.math.RoundingMode.HALF_EVEN).doubleValue();
    }
}
