/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.Map;
import org.jlinalg.genetics.EagleReferenceJoin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EagleJsonTest {
    @Test void ownedRecordsAndExactSharedTextLimitsRoundTripAsStructuredAuditValues()throws Exception {
        var source=Map.of("marker",new EagleReferenceJoin.Marker(1,"v","A","C"),"limits",new MinimacVcf.Limits(10,20,200,10000,20000,1024,10000,100));
        String json=EagleJson.json(source);var value=(Map<?,?>)SimpleJson.parse(json);var marker=(Map<?,?>)value.get("marker");var limits=(Map<?,?>)value.get("limits");
        assertEquals("A",marker.get("reference"));assertEquals("C",marker.get("alternate"));assertEquals(200,((Number)limits.get("genotypeCells")).longValue());
    }
    @Test void nonfiniteScientificNumbersAndUnknownValueTypesCannotBeCoercedIntoSuccess() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertThrows(IOException.class,()->EagleJson.json(Map.of("value",value)));
        assertThrows(IOException.class,()->EagleJson.json(new Object()));
    }
}
