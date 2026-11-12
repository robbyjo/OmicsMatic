/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.enrichment.GseapyLocalEnrichment;
import org.jlinalg.enrichment.GseapyStatistics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GseapyNativeSourceTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object v){return (Map<String,Object>)v;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object v){return (List<Object>)v;}
    @SuppressWarnings("unchecked") private static List<String> strings(Object v){return (List<String>)v;}
    static Stream<Arguments> controls() {
        var result=new java.util.ArrayList<Arguments>();
        for(String edition:List.of("1.0.4","1.1.2","1.1.3","1.1.8")) {
            try(var raw=GseapyNativeSourceTest.class.getResourceAsStream("/org/jlinalg/sequence/gseapy-native/"+edition+".json.gz")) {
                assertNotNull(raw);try(var gzip=new GZIPInputStream(raw)) {
                    var reference=object(SimpleJson.parse(new String(gzip.readAllBytes(),StandardCharsets.UTF_8)));assertEquals(edition,reference.get("edition"));var cases=list(reference.get("cases"));assertEquals(100,cases.size());
                    for(int i=0;i<cases.size();i++)result.add(Arguments.of(edition+" #"+i,object(cases.get(i))));
                }
            }catch(Exception failure){throw new IllegalStateException(failure);}
        }
        return result.stream();
    }
    @ParameterizedTest(name="{0}") @MethodSource("controls") void completeSourcePreparationErrorsAndStatisticsMatch400ActualClassCalls(String label,Map<String,Object> source) {
        var edition=GseapyStatistics.Edition.valueOf("V"+source.get("edition").toString().replace('.','_'));var sets=new LinkedHashMap<String,List<String>>();object(source.get("gene_sets")).forEach((key,value)->sets.put(key,strings(value)));
        Object background=source.get("background");if(background instanceof Long n)background=n.intValue();final Object bg=background;
        if(!Boolean.TRUE.equals(source.get("success"))) {
            assertThrows(IllegalArgumentException.class,()->GseapyLocalEnrichment.analyze(edition,strings(source.get("query")),sets,bg,GseapyLocalEnrichment.Identifiers.SOURCE,0.05),label+" original: "+source.get("error"));return;
        }
        var result=GseapyLocalEnrichment.analyze(edition,strings(source.get("query")),sets,background,GseapyLocalEnrichment.Identifiers.SOURCE,0.05);var expected=list(source.get("rows"));assertEquals(expected.size(),result.rows().size(),label);
        for(int i=0;i<expected.size();i++) {
            var row=object(expected.get(i));var actual=result.rows().get(i);assertEquals(row.get("term"),actual.term(),label);assertEquals(row.get("overlap"),actual.overlap()+"/"+actual.termSize(),label);
            number(row.get("p"),actual.pValue(),label+" P");number(row.get("q"),actual.adjustedPValue(),label+" q");number(row.get("odds"),actual.oddsRatio(),label+" odds");
            assertEquals(row.get("combined_available"),actual.combinedScoreAvailable(),label);if(actual.combinedScoreAvailable())number(row.get("combined"),actual.combinedScore(),label+" combined");
            assertEquals(strings(row.get("hits")),actual.hits(),label);
        }
    }
    private static void number(Object expected,double actual,String label) {
        double value=expected instanceof Number n?n.doubleValue():switch(expected.toString()){case "nan"->Double.NaN;case "inf"->Double.POSITIVE_INFINITY;case "-inf"->Double.NEGATIVE_INFINITY;default->throw new AssertionError(expected);};
        if(Double.isFinite(value))assertEquals(value,actual,Math.max(1e-14,Math.abs(value)*5e-12),label);else assertEquals(value,actual,label);
    }
}
