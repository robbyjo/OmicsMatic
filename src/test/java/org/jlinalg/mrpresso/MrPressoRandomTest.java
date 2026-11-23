/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mrpresso;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MrPressoRandomTest {
    @Test void matchesOriginalR461UniformNormalsSamplingAndEveryContinuationState()throws Exception {
        int cases=0;
        try(var source=MrPressoRandomTest.class.getResourceAsStream("/org/jlinalg/mrpresso/r461-random-reference.zip")){
            assertNotNull(source);
            try(var zip=new ZipInputStream(source)){
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    if(!entry.getName().endsWith(".tsv"))continue;
                    var text=new String(zip.readAllBytes(),StandardCharsets.UTF_8);MrPressoRandom stream=null;int operation=0;
                    for(String line:text.split("\\R")){
                        String[] fields=line.split("\\t");
                        switch(fields[0]){
                            case "PROFILE" -> {stream=new MrPressoRandom(Integer.parseInt(fields[3]),MrPressoRandom.Sampling.valueOf(fields[2].toUpperCase(java.util.Locale.ROOT)));cases++;}
                            case "STATE" -> {
                                assertNotNull(stream);int[] expected=Arrays.stream(fields).skip(2).mapToInt(Integer::parseInt).toArray();
                                assertEquals(626,Integer.parseInt(fields[1]));assertArrayEquals(expected,stream.state(),entry.getName()+" after operation "+operation);
                                // Resume from actual R states throughout the sequence, not just set.seed.
                                if(++operation%3==0)stream=new MrPressoRandom(expected);
                            }
                            case "UNIFORM","NORMAL" -> {
                                assertNotNull(stream);int count=Integer.parseInt(fields[1]);assertEquals(count+2,fields.length);
                                for(int i=0;i<count;i++){
                                    double expected=parse(fields[i+2]),actual=fields[0].equals("UNIFORM")?stream.uniform():stream.standardNormal();
                                    if(fields[0].equals("UNIFORM"))assertEquals(expected,actual,entry.getName()+" uniform "+i);
                                    else assertEquals(expected,actual,Math.max(2e-15,Math.abs(expected)*2e-15),entry.getName()+" normal "+i);
                                }
                            }
                            case "SCALAR" -> {
                                assertNotNull(stream);double expected=parse(fields[3]),actual=stream.normal(parse(fields[1]),parse(fields[2]));
                                if(Double.isNaN(expected))assertTrue(Double.isNaN(actual));
                                else assertEquals(expected,actual,Double.isFinite(expected)?2e-15:0);
                            }
                            case "SAMPLE" -> {
                                assertNotNull(stream);int n=Integer.parseInt(fields[1]),size=Integer.parseInt(fields[2]);
                                assertArrayEquals(Arrays.stream(fields).skip(3).mapToInt(Integer::parseInt).toArray(),stream.sampleWithoutReplacement(n,size),entry.getName()+" sample n="+n);
                            }
                            default -> fail("Unknown original fixture row: "+fields[0]);
                        }
                    }
                }
            }
        }
        assertEquals(12,cases);
    }

    @Test void explicitProfileAndStateAreRequiredAndDefensivelyCopied() {
        assertThrows(IllegalArgumentException.class,()->new MrPressoRandom(Integer.MIN_VALUE,MrPressoRandom.Sampling.REJECTION));
        assertThrows(IllegalArgumentException.class,()->new MrPressoRandom(new int[626]));
        var stream=new MrPressoRandom(19,MrPressoRandom.Sampling.REJECTION);int[] state=stream.state();
        var continued=new MrPressoRandom(state);state[2]^=1;assertArrayEquals(stream.state(),continued.state());
        assertThrows(IllegalArgumentException.class,()->stream.sampleWithoutReplacement(2_000_001,1));
        assertThrows(IllegalArgumentException.class,()->stream.sampleWithoutReplacement(3,4));
    }

    private static double parse(String value) {
        return switch(value){case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;case "NA","NaN"->Double.NaN;default->Double.parseDouble(value);};
    }
}
