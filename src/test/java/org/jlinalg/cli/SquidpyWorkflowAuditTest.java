/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;

final class SquidpyWorkflowAuditTest {
    private Map<String,Object> comparison(Map<String,Object> parent,Map<String,Object> replay,boolean equal){
        var result=new LinkedHashMap<String,Object>();result.put("parent",SquidpyWorkflowAudit.semanticExports(parent));result.put("replay",SquidpyWorkflowAudit.semanticExports(replay));
        result.put("typed_semantic_exports_byte_identical",equal);result.put("h5ad_transport_bytes_retained_but_not_compared",true);result.put("independent_refit",false);return result;
    }
    @Test void actualReplayInventoriesRetainTransportButCompareTypedScientificExports()throws IOException {
        Map<String,Object> parent=Map.of("state/X.mtx","same","state/original-state.h5ad","old-transport");
        Map<String,Object> replay=Map.of("state/X.mtx","same","state/original-state.h5ad","different-transport");
        SquidpyWorkflowAudit.validateReplayComparison(parent,replay,comparison(parent,replay,true),false);
        assertEquals(Map.of("state/X.mtx","same"),SquidpyWorkflowAudit.semanticExports(parent));
    }
    @Test void falseReplayClaimsAndUndeclaredDifferencesAreRefused()throws IOException {
        Map<String,Object> parent=Map.of("statistics/rows.jsonl","before"),replay=Map.of("statistics/rows.jsonl","after");
        var correct=comparison(parent,replay,false);
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateReplayComparison(parent,replay,correct,false));
        SquidpyWorkflowAudit.validateReplayComparison(parent,replay,correct,true);
        var forged=new LinkedHashMap<>(correct);forged.put("typed_semantic_exports_byte_identical",true);
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateReplayComparison(parent,replay,forged,true));
        forged.put("typed_semantic_exports_byte_identical",false);forged.put("replay",Map.of("statistics/rows.jsonl","invented"));
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateReplayComparison(parent,replay,forged,true));
        forged.put("replay",SquidpyWorkflowAudit.semanticExports(replay));forged.put("independent_refit",true);
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateReplayComparison(parent,replay,forged,true));
    }
    private Map<String,Object> context(double error,String dtype){
        return new LinkedHashMap<>(Map.of("source_derived_standard_error",Map.of("kind","float","value",Double.toString(error)),
            "source_standard_error_dtype",dtype,"score_dtype",dtype,"standardized_dtype",dtype));
    }
    @Test void normalityDiagnosticsMustAgreeWithOriginalPointAndVariance()throws IOException {
        double point=0.25,variance=0.04,error=Math.sqrt(variance),z=(point+1./35)/error;
        var context=context(error,"float64");SquidpyWorkflowAudit.validateStandardized(point,variance,z,context,"moran",36);
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateStandardized(point,variance,z+0.1,context,"moran",36));
        var changed=context(0.4,"float64");
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateStandardized(point,variance,z,changed,"moran",36));
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateStandardized(Double.NaN,variance,Double.NaN,context,"moran",36));
    }
    @Test void originalFloat32ArithmeticIsAdmittedAtItsRecordedPrecision()throws IOException {
        float point=0.23456789f,variance=0.01234567f,error=(float)Math.sqrt(variance),expected=(float)(-1./35),z=(point-expected)/error;
        SquidpyWorkflowAudit.validateStandardized(point,variance,z,context(error,"float32"),"moran",36);
        assertThrows(IOException.class,()->SquidpyWorkflowAudit.validateStandardized(point,variance,z+0.01,context(error,"float32"),"moran",36));
    }
}
