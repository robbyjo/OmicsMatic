package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HomerGenomeAuditTest {
    @TempDir Path root;
    @Test void sourceForwardAdjustmentDiffersFromStandardBh(){assertArrayEquals(new double[]{.04,.08,.08,.2},HomerGenomeAudit.forwardAdjustment(new double[]{.01,.04,.041,.2}),1e-15);}
    @Test void sourceForwardTiesUseLastRankAndRetainOriginalOrder(){assertArrayEquals(new double[]{.04,.04,.04,.9},HomerGenomeAudit.forwardAdjustment(new double[]{.02,.01,.02,.9}),1e-15);}
    @Test void qAdjustmentClipsAtOneAndHandlesUnderflow(){assertArrayEquals(new double[]{0,0,1,1},HomerGenomeAudit.forwardAdjustment(new double[]{0,0,.9,1}),0);assertEquals(0,HomerGenomeAudit.forwardAdjustment(new double[0]).length);}
    @Test void invalidQInputsFail(){for(double p:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})assertThrows(IllegalArgumentException.class,()->HomerGenomeAudit.forwardAdjustment(new double[]{p}));}
    @Test void weightedCountsAreFlooredAtTheActualOriginalScorerBoundary()throws Exception{var result=HomerGenomeAudit.test(10.9,3.9,20.9,1.9,"binomial");assertEquals(new org.jlinalg.sequence.HomerEnrichment.Counts(10,3,20,1),result.counts());}
    @Test void noEpsilonPromotesNearIntegerPopulation()throws Exception{var result=HomerGenomeAudit.test(10,3,20-1e-12,1,"hypergeometric");assertEquals(19,result.counts().background());}
    @Test void invalidOrDegenerateWeightedPopulationsFail(){for(double n:new double[]{Double.NaN,-1,Double.POSITIVE_INFINITY,.5})assertThrows(IOException.class,()->HomerGenomeAudit.test(n,0,20,0,"binomial"));assertThrows(IOException.class,()->HomerGenomeAudit.test(10,11,20,0,"binomial"));assertThrows(IOException.class,()->HomerGenomeAudit.test(10,0,1.9,0,"binomial"));}
    @Test void knownLogPToleranceIncludesBothOriginalPrintStages()throws Exception{double nativeLp=-.02154465039327566;assertEquals(-.021545,HomerGenomeAudit.sourcePrintedLogP(nativeLp));assertTrue(Math.abs(-.02155-nativeLp)<HomerGenomeAudit.scientificTolerance("-2.155e-02"));}
    @Test void printedZeroLogPHasOnlySixDecimalIntermediateUncertainty()throws Exception{assertEquals(.000000501,HomerGenomeAudit.scientificTolerance("0.000e+00"));assertEquals(-0.0,HomerGenomeAudit.sourcePrintedLogP(-.0000001));}

    @Test void originalIntermediateLogPUsesBinaryHalfEvenTies(){assertEquals(-.007812,HomerGenomeAudit.sourcePrintedLogP(-.0078125));}

    @Test void legacyZeroPercentageReportFailureIsPromotedBeforeMissingTables()throws Exception{Files.writeString(root.resolve("original.stderr"),"Finished preparing sequence/group files\nIllegal division by zero at /private/bin/findKnownMotifs.pl line 152.\nJob finished\n");for(String version:new String[]{"4.11","4.11.1"}){var failure=assertThrows(IOException.class,()->HomerGenomeAudit.diagnostics(root,version));assertTrue(failure.getMessage().contains(version));assertTrue(failure.getMessage().contains("Illegal division by zero"));assertTrue(failure.getMessage().contains("Required action:"));}}
    @Test void zeroWrapperExitDoesNotOverrideNonfiniteDiagnostics()throws Exception{Files.writeString(root.resolve("original.stderr"),"Finished preparing sequence/group files\nscore = nan\nJob finished\n");var failure=assertThrows(IOException.class,()->HomerGenomeAudit.diagnostics(root,"5.1"));assertTrue(failure.getMessage().contains("nonfinite arithmetic"));}

}
