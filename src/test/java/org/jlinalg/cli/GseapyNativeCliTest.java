/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.enrichment.GseapyLocalEnrichment;
import org.jlinalg.enrichment.GseapyStatistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GseapyNativeCliTest {
    @TempDir Path directory;
    private static final Map<String,List<String>> SETS=Map.of("one",List.of("A","C","E"),"two",List.of("A","B"),"zero",List.of("F"));
    @Test void completeLocalFamilyMatchesExactCombinatoricsWithoutPython() {
        var result=GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_1_8,List.of("A","B","A"),SETS,
            List.of("A","B","C","D","E","F"),GseapyLocalEnrichment.Identifiers.LITERAL,0.05);
        assertEquals(List.of("one","two"),result.rows().stream().map(GseapyStatistics.Row::term).toList());
        assertEquals(0.8,result.rows().get(0).pValue(),1e-14); // 1 - choose(3,2)/choose(6,2)
        assertEquals(1.0/15,result.rows().get(1).pValue(),1e-14);
        assertEquals(2.0/15,result.rows().get(1).adjustedPValue(),1e-14);
        assertEquals(List.of("zero"),result.omittedTerms());assertEquals(3,result.query().size());
        assertEquals(2,result.rows().get(0).querySize());
    }
    @Test void sourceEditionControlsWhitespaceAndCaseConversion() {
        for(var edition:GseapyStatistics.Edition.values()) {
            if(edition!=GseapyStatistics.Edition.V1_1_8){assertThrows(IllegalArgumentException.class,()->GseapyLocalEnrichment.analyze(edition,List.of(" a ","b"),Map.of("path",List.of("A","B")),List.of("a","b","c"),GseapyLocalEnrichment.Identifiers.SOURCE,0.05));continue;}
            var result=GseapyLocalEnrichment.analyze(edition,List.of(" a ","b"),Map.of("path",List.of("A","B")),
                List.of("a","b","c"),GseapyLocalEnrichment.Identifiers.SOURCE,0.05);
            assertEquals(edition==GseapyStatistics.Edition.V1_1_8,result.uppercased());
            assertEquals(edition==GseapyStatistics.Edition.V1_1_8?1:0,result.rows().size());
            assertEquals(" a ",result.query().get(0).original());
        }
    }
    @Test void integerIdentityRetainsSourceBackgroundTypeAndEditionErrors() {
        var sets=Map.of("path",List.of("01","2"));
        var current=GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_1_2,List.of("+1","02"),sets,null,GseapyLocalEnrichment.Identifiers.SOURCE,0.05);
        assertEquals(List.of("1","2"),current.rows().get(0).hits());
        assertThrows(IllegalArgumentException.class,()->GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_1_2,List.of("1"),sets,List.of("1","2"),GseapyLocalEnrichment.Identifiers.SOURCE,0.05));
        assertThrows(IllegalArgumentException.class,()->GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_0_4,List.of("1"),sets,null,GseapyLocalEnrichment.Identifiers.SOURCE,0.05));
        assertThrows(IllegalArgumentException.class,()->GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_1_8,List.of("1"),sets,null,GseapyLocalEnrichment.Identifiers.SOURCE,0.05));
        assertThrows(IllegalArgumentException.class,()->GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_1_8,List.of("A"),Map.of("x",List.of("A")),3,GseapyLocalEnrichment.Identifiers.SOURCE,0.05));
    }
    @Test void literalPolicyPreservesWhitespaceAndUndefinedNumericMargins() {
        var result=GseapyLocalEnrichment.analyze(GseapyStatistics.Edition.V1_1_8,List.of(" a ","a"),Map.of("path",List.of(" a ","a")),1,GseapyLocalEnrichment.Identifiers.LITERAL,0.05);
        assertTrue(Double.isNaN(result.rows().get(0).pValue()));assertEquals(List.of(" a ","a"),result.rows().get(0).hits());
        assertFalse(result.rows().get(0).warnings().isEmpty());
    }
    private int run(Path input,String name,String edition,String policy) {
        var stream=new PrintStream(new ByteArrayOutputStream());
        return JLinAlgCli.run(new String[]{"gseapy-native","--input",input.toString(),"--edition",edition,"--identifier-policy",policy,"--out",directory.resolve(name).toString()},stream,stream);
    }
    @Test void rootCommandPublishesNativeResultsAndRefusesOverwrite()throws Exception {
        Path input=directory.resolve("input.json");Files.writeString(input,"""
            {"schema_version":1,"query":["A","B"],"gene_sets":{"hit":["A","B"],"zero":["C"]},"background":["A","B","C","D"]}
            """);
        assertEquals(0,run(input,"out","1.1.8","source"));
        assertTrue(Files.readString(directory.resolve("out/analysis-status.json")).contains("\"result_admitted\":true"));
        assertTrue(Files.readString(directory.resolve("out/manifest.yaml")).contains("engine: native-java"));
        assertTrue(Files.readString(directory.resolve("out/omitted-terms.tsv")).contains("zero\tzero overlap"));
        String before=Files.readString(directory.resolve("out/results.tsv"));assertEquals(2,run(input,"out","1.1.8","source"));
        assertEquals(before,Files.readString(directory.resolve("out/results.tsv")));
    }
    @Test void failedSourceAnalysisPublishesFailureAndNoSuccessfulTable()throws Exception {
        Path input=directory.resolve("input.json");Files.writeString(input,"{\"schema_version\":1,\"query\":[\"1\"],\"gene_sets\":{\"x\":[\"1\"]},\"background\":null}");
        assertEquals(2,run(input,"failure","1.1.8","source"));
        assertFalse(Files.exists(directory.resolve("failure/results.tsv")));
        assertTrue(Files.readString(directory.resolve("failure/analysis-status.json")).contains("\"result_admitted\":false"));
        assertTrue(Files.readString(directory.resolve("failure/WARNINGS.md")).contains("set slicing"));
    }
}
