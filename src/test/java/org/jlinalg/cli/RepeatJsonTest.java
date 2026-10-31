/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
final class RepeatJsonTest {
    record Owned(String label, long count) { }
    @Test void canonicalKeysUnicodeAndEscapesRetainTheActualValues()throws Exception{
        var values=new LinkedHashMap<String,Object>();values.put("z","β\t項目\n\"quoted\"");values.put("a",true);
        String json=RepeatJson.json(values);assertTrue(json.indexOf("\"a\"")<json.indexOf("\"z\""));assertEquals(values,SimpleJson.parse(json));
    }
    @Test void exactLongAndDecimalValuesDoNotPassThroughDoubleRounding()throws Exception{
        assertEquals(Long.toString(Long.MAX_VALUE),RepeatJson.json(Long.MAX_VALUE));assertEquals("40.0500",RepeatJson.json(new BigDecimal("40.0500")));
        assertEquals(Long.MAX_VALUE,((Number)SimpleJson.parse(RepeatJson.json(Long.MAX_VALUE))).longValue());
    }
    @Test void nonfiniteAndUnsupportedNumericObjectsFailInsteadOfBecomingMissing()throws Exception{
        for(Object value:List.of(Double.NaN,Double.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,new AtomicLong(7)))assertThrows(IOException.class,()->RepeatJson.json(value));
    }
    @Test void nonstringKeysCannotCollapseIntoAnIdenticalStringKey()throws Exception{
        var values=new LinkedHashMap<Object,Object>();values.put(1,"numeric");values.put("1","text");assertThrows(IOException.class,()->RepeatJson.json(values));
    }
    @Test void setsAreCanonicalButSequenceOrderIsPreserved()throws Exception{
        assertEquals(List.of("alpha","zeta"),SimpleJson.parse(RepeatJson.json(Set.of("zeta","alpha"))));assertEquals(List.of("zeta","alpha"),SimpleJson.parse(RepeatJson.json(List.of("zeta","alpha"))));
    }
    @Test void ownedRecordsEnumsAndPathsAreExplicitForeignObjectsAreRefused()throws Exception{
        var json=(Map<?,?>)SimpleJson.parse(RepeatJson.json(new Owned("source",9)));assertEquals("source",json.get("label"));assertEquals(9L,json.get("count"));
        assertEquals("SOFT",SimpleJson.parse(RepeatJson.json(org.jlinalg.genetics.RepeatAnnotations.Mask.SOFT)));assertEquals(Path.of("source.fa").toString(),SimpleJson.parse(RepeatJson.json(Path.of("source.fa"))));assertThrows(IOException.class,()->RepeatJson.json(new Object()));
    }
}
