/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jlinalg.sequence.HornetAlleles;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

class HornetAllelesTest {
    @TestFactory Stream<DynamicTest> exactOriginalSourceSequencesCountersAndErrors() throws Exception {
        Map<?,?> oracle;
        try (var input = getClass().getResourceAsStream("/org/jlinalg/sequence/hornet-alleles/oracle.json")) {
            assertNotNull(input); oracle = object(SimpleJson.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        assertEquals(HornetAlleles.REVISION, object(oracle.get("source")).get("revision"));
        var tests = new ArrayList<DynamicTest>();
        for (Object item : list(oracle.get("cases"))) {
            var row = object(item);
            tests.add(DynamicTest.dynamicTest(text(row.get("id")), () -> {
                var request = request(row);
                if (row.containsKey("source_error")) {
                    // Unsupported source execution must not silently produce a completed native result.
                    assertThrows(RuntimeException.class, () -> HornetAllelesCli.compute(encode(request)));
                } else {
                    var actual = HornetAllelesCli.compute(encode(request));
                    var expected = object(row.get("expected"));
                    assertEquals(NextflowJson.json(expected.get("sequences")), NextflowJson.json(actual.get("sequences")));
                    assertEquals(NextflowJson.json(expected.get("dispositions")), NextflowJson.json(actual.get("dispositions")));
                    assertEquals(false, actual.get("mapping_workflow_admitted"));
                    assertEquals(0, actual.get("native_family_credit"));
                }
            }));
        }
        for (Object item : list(oracle.get("reverse_complements"))) {
            var row = object(item);
            tests.add(DynamicTest.dynamicTest("original-reverse-complement-" + row.get("sequence"),
                    () -> assertEquals(row.get("expected"), HornetAlleles.reverseComplement(text(row.get("sequence"))))));
        }
        return tests.stream();
    }
    private static Map<String,Object> request(Map<?,?> row) {
        return map("schema_version", 1, "profile", HornetAllelesCli.PROFILE, "input_domain", "explicit-zero-based-BAM-orientation-reads",
                "preparation", "Original Hornet oracle on synthetic controls, seed896", "reads", row.get("reads"), "variants", row.get("variants"), "phased", row.get("phased"));
    }
    private static byte[] encode(Object value) throws Exception { return NextflowJson.json(value).getBytes(StandardCharsets.UTF_8); }

    @Test void singleReadFlagDoesNotSilentlyRemoveAnExistingAlignment() {
        var read = new HornetAlleles.Read("chr1", 100, "AAAAAA", "6M", true);
        var data = new HornetAlleles.Variants(List.of(new HornetAlleles.Variant("chr1", 100, "A", "C")));
        assertEquals(List.of(List.of("AAAAAA", "CAAAAA")), HornetAlleles.single(read, data, false).sequences());
        assertEquals(Map.of("unmapped read", 1L), HornetAlleles.paired(read, read, data, false).dispositions());
    }
    @Test void invalidCigarCoordinatesAndCapacityDoNotBecomeBiologicalDiscards() {
        for (String cigar : List.of("5M", "7M", "2M1P4M", "0M6M", "*", "2M1B4M"))
            assertThrows(RuntimeException.class, () -> new HornetAlleles.Read("chr1", 100, "AAAAAA", cigar, false));
        assertThrows(IllegalArgumentException.class, () -> new HornetAlleles.Read("chr1", -1, "A", "1M", false));
        var first = new HornetAlleles.Read("chr1", 100, "A", "1M", false);
        var other = new HornetAlleles.Read("chr2", 100, "A", "1M", false);
        assertThrows(IllegalArgumentException.class, () -> HornetAlleles.paired(first, other, new HornetAlleles.Variants(List.of()), false));
        var exploding = new HornetAlleles.Variants(List.of(new HornetAlleles.Variant("chr1", 100, "A", "C".repeat(20))));
        assertThrows(IllegalArgumentException.class, () -> HornetAlleles.single(first, exploding, false));
    }
    @Test void failedRequestsCannotBeCoercedIntoTheExplicitInputDomain() throws Exception {
        var example = object(HornetAllelesCli.example());
        for (String field : List.of("schema_version", "profile", "input_domain", "preparation", "reads", "variants", "phased")) {
            var invalid = new java.util.LinkedHashMap<Object,Object>(example); invalid.remove(field);
            assertThrows(Exception.class, () -> HornetAllelesCli.compute(encode(invalid)));
        }
        var invalid = new java.util.LinkedHashMap<Object,Object>(example); invalid.put("phased", "true");
        assertThrows(Exception.class, () -> HornetAllelesCli.compute(encode(invalid)));
    }
    @Test void missingRequestHasProminentSkipAndRecoveryInstructions() {
        var output = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        int status = HornetAllelesCli.run(new String[]{"--request", "no-such-hornet-input.json", "--out", "unused-capture"},
                new PrintStream(output, true, StandardCharsets.UTF_8), new PrintStream(error, true, StandardCharsets.UTF_8));
        assertEquals(3, status); assertTrue(error.toString(StandardCharsets.UTF_8).contains("SKIP3 DEPENDENT HORNET COMPONENT SKIPPED"));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("WARNINGS AND REQUIRED ACTION")); assertEquals(0, output.size());
    }
}
