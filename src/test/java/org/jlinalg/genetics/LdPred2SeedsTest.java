/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class LdPred2SeedsTest {
    @Test void actualRIntegerSeedsAndAll64SuccessiveStreamsMatchForEightBoundarySeeds() throws Exception {
        String text=null;
        try(var stream=getClass().getResourceAsStream("ldpred2-whole-grid-r433.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals("streams.tsv"))text=new String(zip.readAllBytes(),StandardCharsets.UTF_8);}
        }
        assertNotNull(text);int previous=0;int[] current=null;int count=0;
        for(String row:text.lines().skip(1).toList()) {
            String[] columns=row.split("\t");int seed=Integer.parseInt(columns[0]),index=Integer.parseInt(columns[1]);int[] expected=Arrays.stream(columns[2].split(",")).mapToInt(Integer::parseInt).toArray();
            if(index==0){current=LdPred2Seeds.fromInteger(seed);previous=seed;}else {assertEquals(previous,seed);current=LdPred2Seeds.nextStream(current);}
            assertArrayEquals(expected,current,"seed="+seed+" stream="+index);count++;
        }
        assertEquals(512,count);
    }
    @Test void streamJumpsDoNotMutateInputsAndMissingRSeedIsRejected() {
        int[] initial=LdPred2Seeds.fromInteger(123),before=initial.clone();
        int[] next=LdPred2Seeds.nextStream(initial);assertArrayEquals(before,initial);assertFalse(Arrays.equals(initial,next));
        assertThrows(IllegalArgumentException.class,()->LdPred2Seeds.fromInteger(Integer.MIN_VALUE));
    }
}
