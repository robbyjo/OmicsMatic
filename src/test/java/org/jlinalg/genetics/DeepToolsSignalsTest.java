package org.jlinalg.genetics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepToolsSignalsTest {
    private static double numeric(String value) {
        return switch(value){case "nan"->Double.NaN;case "inf"->Double.POSITIVE_INFINITY;case "-inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value);};
    }
    @Test void everyActualOriginalComparisonReferenceMatches()throws Exception {
        var stream=getClass().getResourceAsStream("/deeptools/comparison-reference.tsv");assertNotNull(stream);int count=0;
        try(var in=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            assertEquals("case_id\toperation\tfirst\tsecond\tfirst_scale\tsecond_scale\tfirst_pseudo\tsecond_pseudo\texpected\tstatus",in.readLine());String line;
            while((line=in.readLine())!=null){var f=line.split("\t");var comparison=new DeepToolsSignals.Comparison(DeepToolsSignals.Operation.valueOf(f[1].toUpperCase(Locale.ROOT)),numeric(f[4]),numeric(f[5]),numeric(f[6]),numeric(f[7]));var actual=comparison.apply(numeric(f[2]),numeric(f[3]));assertEquals(DeepToolsSignals.Status.valueOf(f[9]),actual.status(),f[0]);double expected=numeric(f[8]);
                if(Double.isNaN(expected))assertTrue(Double.isNaN(actual.value()),f[0]);
                else if(Double.isInfinite(expected))assertEquals(expected,actual.value(),f[0]);
                else assertEquals(expected,actual.value(),Math.max(1e-12,Math.abs(expected)*2e-14),f[0]);count++;
            }
        }assertEquals(4064,count);
    }
    @Test void scaleThenPseudocountAndReciprocalEqualityAreExplicit() {
        var ratio=new DeepToolsSignals.Comparison(DeepToolsSignals.Operation.RATIO,2,.5,1,3);assertEquals(5.0/8,ratio.apply(2,10).value());
        var reciprocal=new DeepToolsSignals.Comparison(DeepToolsSignals.Operation.RECIPROCAL_RATIO,1,1,0,0);assertEquals(1,reciprocal.apply(1,1).value());assertEquals(-2,reciprocal.apply(1,2).value());assertEquals(2,reciprocal.apply(2,1).value());
        assertEquals(DeepToolsSignals.Status.NONFINITE_RESULT,ratio.apply(1,-6).status());
        var first=new DeepToolsSignals.Comparison(DeepToolsSignals.Operation.FIRST,1,1,10,20);assertEquals(2,first.apply(2,3).value());assertEquals(DeepToolsSignals.Status.MISSING_INPUT,first.apply(2,Double.NaN).status());
    }
    @Test void declaredAlignmentNormalizationHasIndependentKnownUnits() {
        assertEquals(.1,DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.CPM,10e6,50,0,0,1));
        assertEquals(2,DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.RPKM,10e6,50,0,0,1));
        assertEquals(.1,DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.BPM,10e6,50,0,0,1),1e-15);
        assertEquals(.4,DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.RPGC,100e6,50,3_000_000_000L,150,2),1e-15);
        assertEquals(7,DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.NONE,0,50,0,0,7));
    }
    @Test void invalidDeclarationsAndNonfiniteResultsAreNotHidden() {
        assertThrows(IllegalArgumentException.class,()->DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.CPM,0,50,0,0,1));
        assertThrows(IllegalArgumentException.class,()->DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.RPGC,10,50,0,150,1));
        assertThrows(IllegalArgumentException.class,()->DeepToolsSignals.coverageScale(DeepToolsSignals.Normalization.RPGC,10,50,3000,Double.NaN,1));
        assertThrows(IllegalArgumentException.class,()->new DeepToolsSignals.Comparison(DeepToolsSignals.Operation.RATIO,1,1,-1,0));
        var zero=new DeepToolsSignals.Comparison(DeepToolsSignals.Operation.RATIO,1,1,0,0);assertEquals(DeepToolsSignals.Status.NONFINITE_RESULT,zero.apply(0,0).status());assertEquals(Double.POSITIVE_INFINITY,zero.apply(1,0).value());assertThrows(IllegalArgumentException.class,()->zero.apply(Double.POSITIVE_INFINITY,1));
        assertThrows(IllegalArgumentException.class,()->new DeepToolsSignals.Result(1,DeepToolsSignals.Status.NONFINITE_RESULT));
    }
}
