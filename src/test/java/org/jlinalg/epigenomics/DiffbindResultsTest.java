/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.epigenomics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DiffbindResultsTest {
    private static final List<String> CASES=List.of("basic","missing","signed_zero_ties","infinities","single","empty");

    @Test void completeOriginalStatementReferencePreservesOrderAndEveryFloatingBit()throws Exception {
        var input=fixture("projection-input.tsv");var expected=fixture("projection-output.tsv");int rows=0;
        for(String name:CASES) {
            var actual=DiffbindResults.fromDeseq2(input.get(name));var want=expected.get(name);
            assertEquals(want.size(),actual.size(),name);
            for(int index=0;index<want.size();index++) {
                var a=actual.get(index);var b=want.get(index);assertEquals(b.id(),a.id(),name);
                assertEquals(Double.doubleToRawLongBits(b.pvalue()),Double.doubleToRawLongBits(a.pvalue()),name);
                assertEquals(Double.doubleToRawLongBits(b.adjustedPvalue()),Double.doubleToRawLongBits(a.adjustedPvalue()),name);
                assertEquals(Double.doubleToRawLongBits(b.log2FoldChange()),Double.doubleToRawLongBits(a.log2FoldChange()),name);rows++;
            }
        }
        assertEquals(17,rows);
    }

    @Test void duplicateSourceIdentitiesAreUnreportable() {
        assertThrows(IllegalArgumentException.class,()->DiffbindResults.fromDeseq2(List.of(
                new DiffbindResults.Row(1,.1,.2,1),new DiffbindResults.Row(1,.2,.3,-1))));
    }

    private static Map<String,List<DiffbindResults.Row>> fixture(String name)throws Exception {
        Map<String,List<DiffbindResults.Row>> result=new LinkedHashMap<>();for(String key:CASES)result.put(key,new ArrayList<>());
        try(var stream=DiffbindResultsTest.class.getResourceAsStream("/org/jlinalg/epigenomics/diffbind/"+name)) {
            assertNotNull(stream);try(var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                assertEquals("case\tordinal\tid\tp_bits\tpadj_bits\tfold_bits",reader.readLine());String line;
                while((line=reader.readLine())!=null) {
                    String[] f=line.split("\t",-1);assertEquals(6,f.length);assertTrue(result.containsKey(f[0]));
                    var rows=result.get(f[0]);assertEquals(rows.size()+1,Integer.parseInt(f[1]));
                    rows.add(new DiffbindResults.Row(Long.parseLong(f[2]),number(f[3]),number(f[4]),number(f[5])));
                }
            }
        }
        return result;
    }
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
}
