package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.jlinalg.genetics.PlinkBedDataset;
import org.junit.jupiter.api.Test;

class SaigeGenotypesTest {
    @Test void rawQcAndPostImputationCountsAreDistinct() {
        var calls=SaigeGenotypes.calculate(new double[]{0,1,Double.NaN,2},new int[]{0,0,1,1},SaigeGenotypes.Missing.MEAN);var s=calls.summary();
        assertEquals(3,s.rawAlleleCount());assertEquals(.5,s.rawFrequency());assertEquals(3,s.rawMinorCount());assertEquals(4,s.alleleCount());assertEquals(.25,s.missingRate());assertEquals(.75,s.caseFrequency());assertEquals(.25,s.controlFrequency());assertEquals(1,s.caseHom());assertEquals(1,s.caseHet());assertEquals(1,s.controlHet());assertFalse(s.flipped());assertArrayEquals(new double[]{0,1,1,2},calls.minorDosages());
    }
    @Test void imputationOccursAfterMinorOrientationAndReportedCountsReturnToEffectOrientation() {
        var calls=SaigeGenotypes.calculate(new double[]{2,2,1,Double.NaN},new int[]{0,0,1,1},SaigeGenotypes.Missing.MEAN);var s=calls.summary();assertTrue(s.flipped());assertEquals(5.0/6,s.rawFrequency());assertEquals(1,s.rawMinorCount());assertEquals(1.0/3,s.imputedMinorDosage(),1e-15);assertEquals(20.0/3,s.alleleCount(),1e-14);assertEquals(1,s.controlFrequency());assertEquals(2,s.controlHom());assertEquals(1,s.caseHom());assertEquals(1,s.caseHet());assertEquals(2.0/3,s.caseFrequency(),1e-15);
    }
    @Test void originalMinorMethodMeansZeroMinorDosageDespiteItsHelpWording() {
        double[] g={2,2,1,Double.NaN};var s=SaigeGenotypes.calculate(g,null,SaigeGenotypes.Missing.MAJOR_HOMOZYGOTE).summary();assertEquals("minor",SaigeGenotypes.Missing.MAJOR_HOMOZYGOTE.upstream);assertEquals(0,s.imputedMinorDosage());assertEquals(7,s.alleleCount());assertTrue(Double.isNaN(s.caseFrequency()));assertEquals(0,s.cases());assertEquals(0,s.controls());
    }
    @Test void bestGuessUsesOriginalPositiveHalfRounding() {
        var calls=SaigeGenotypes.calculate(new double[]{0,1,Double.NaN},null,SaigeGenotypes.Missing.BEST_GUESS);assertEquals(1,calls.summary().imputedMinorDosage());assertArrayEquals(new double[]{0,1,1},calls.minorDosages());
    }
    @Test void returnedDosagesAreImmutableAndCallerInputIsUnchanged() {
        double[] input={0,1,Double.NaN};var calls=SaigeGenotypes.calculate(input,null,SaigeGenotypes.Missing.MEAN);double[] first=calls.minorDosages();first[0]=100;assertEquals(0,calls.minorDosages()[0]);assertTrue(Double.isNaN(input[2]));
    }
    @Test void invalidCallsAllMissingAndImplicitBinaryCodingFail() {
        for(double bad:new double[]{-.1,.5,3,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.calculate(new double[]{0,bad},null,SaigeGenotypes.Missing.MEAN));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.calculate(new double[]{Double.NaN},null,SaigeGenotypes.Missing.MEAN));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.calculate(new double[]{0,1},new int[]{1,2},SaigeGenotypes.Missing.MEAN));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.calculate(new double[]{0,1},new int[]{1,1},SaigeGenotypes.Missing.MEAN));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.calculate(new double[]{0,1},new int[]{0},SaigeGenotypes.Missing.MEAN));
    }
    @Test void originalIidAloneSemanticsRejectCrossFamilyCollisionsAndMissingOrRepeatedModelSamples() {
        var a=new PlinkBedDataset.Sample("family1","one",true,1);var b=new PlinkBedDataset.Sample("family2","two",true,2);assertArrayEquals(new int[]{1,0},SaigeGenotypes.cohortIndices(List.of(a,b),List.of("two","one")));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.cohortIndices(List.of(a,new PlinkBedDataset.Sample("other","one",true,0)),List.of("one")));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.cohortIndices(List.of(a,b),List.of("two","two")));assertThrows(IllegalArgumentException.class,()->SaigeGenotypes.cohortIndices(List.of(a,b),List.of("absent")));
    }
}
