/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NextflowRecordedBindingsTest {
    @Test void linuxProducerPathsReplayOnAnyHostWithoutOpeningThem()throws Exception {
        Object input=NextflowParameters.parse("{\"path\":{\"$input\":\"reads\",\"path\":\"sample.fastq\"},\"out\":{\"$output\":\"counts.tsv\"},\"n\":9007199254740993,\"x\":1.2300e-400}",1000,100);
        assertEquals("{\"path\":\"/producer/resources/reads/sample.fastq\",\"out\":\"/producer/published/counts.tsv\",\"n\":9007199254740993,\"x\":1.2300e-400}",NextflowJson.parameters(NextflowParameters.bindRecorded(input,Map.of("reads","/producer/resources/reads"),"/producer/published")));
    }
    @Test void literalReservedObjectsAreNotRebound()throws Exception {
        Object input=NextflowParameters.parse("{\"$literal\":{\"$input\":\"not-an-input\"}}",1000,100);
        assertEquals("{\"$input\":\"not-an-input\"}",NextflowJson.parameters(NextflowParameters.bindRecorded(input,Map.of(),"/producer/published")));
    }
    @Test void recordedBindingsRejectTraversalAndNoncanonicalProducerRoots()throws Exception {
        for(String root:new String[]{"relative","/x/../y","/x//y","/x\\y","/"})assertThrows(IOException.class,()->NextflowParameters.bindRecorded(Map.of("$output",""),Map.of(),root),root);
        for(String relative:new String[]{"../x","a/../x","/x","a//x","a\\x"})assertThrows(IOException.class,()->NextflowParameters.bindRecorded(Map.of("$output",relative),Map.of(),"/producer"),relative);
    }
    @Test void absentInputsAndAmbiguousBindingObjectsAreRejected()throws Exception {
        assertThrows(IOException.class,()->NextflowParameters.bindRecorded(Map.of("$input","absent"),Map.of(),"/producer"));
        assertThrows(IOException.class,()->NextflowParameters.bindRecorded(Map.of("$input","reads","extra",true),Map.of("reads","/producer/reads"),"/producer"));
    }
}
