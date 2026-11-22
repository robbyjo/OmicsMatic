/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

final class RLongDoubleSumTest {
    @TestFactory Stream<DynamicTest> actualOriginalRColumnArithmetic()throws Exception {
        var input=getClass().getResourceAsStream("/org/jlinalg/settest/acat091/original-sums.tsv");assertNotNull(input);var tests=new ArrayList<DynamicTest>();
        try(var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){String[] fields=line.split("\t",-1);tests.add(DynamicTest.dynamicTest(fields[0],()->{
            var sum=new RLongDoubleSum();String[] terms=fields[1].split(",");for(String term:terms)sum.add(switch(term){case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(term);});
            same(fields[2],sum.value());same(fields[3],sum.mean(terms.length));
        }));}}
        assertEquals(192,tests.size());return tests.stream();
    }
    private static void same(String hexadecimal,double actual){double expected=Double.longBitsToDouble(Long.parseUnsignedLong(hexadecimal,16));if(Double.isNaN(expected))assertTrue(Double.isNaN(actual));else assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(actual),"Exact R64-significand column arithmetic");}
}
