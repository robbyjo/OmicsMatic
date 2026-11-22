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

final class RSourceColumnSumTest {
    @TestFactory Stream<DynamicTest> publicStreamingColumnSumsMatchActualOriginalR()throws Exception {
        var resource=getClass().getResourceAsStream("/org/jlinalg/settest/acat091/original-sums.tsv");assertNotNull(resource);var result=new ArrayList<DynamicTest>();
        try(var reader=new BufferedReader(new InputStreamReader(resource,StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){String[] row=line.split("\t",-1);result.add(DynamicTest.dynamicTest(row[0],()->{
            var sum=new RSourceColumnSum();for(String token:row[1].split(","))sum.add(switch(token){case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(token);});
            double expected=Double.longBitsToDouble(Long.parseUnsignedLong(row[2],16));if(Double.isNaN(expected))assertTrue(Double.isNaN(sum.value()));else assertEquals(Double.doubleToRawLongBits(expected),Double.doubleToRawLongBits(sum.value()));
        }));}}assertEquals(192,result.size());return result.stream();
    }
}
