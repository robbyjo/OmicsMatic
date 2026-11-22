package org.jlinalg.sequence;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerGeneratedSequencesTest {
    private static SeqtkMethods.Bases reference(String dna){return new SeqtkMethods.Bases(){public long length(){return dna.length();}public char base(long i){return dna.charAt(Math.toIntExact(i));}};}
    @Test void oldLeftBoundaryPreservesOriginalExtendedBody()throws Exception{String dna="ACGT".repeat(50000);assertEquals("N".repeat(26)+dna.substring(0,119),HomerGeneratedSequences.oldPreparsedExtraction(reference(dna),-25,93,'+',false,200004));}
    @Test void oldRightBoundaryTruncatesWithoutPadding()throws Exception{String dna="ACGT".repeat(50000);assertEquals(dna.substring(199894),HomerGeneratedSequences.oldPreparsedExtraction(reference(dna),199895,200013,'+',false,200004));assertNull(HomerGeneratedSequences.oldPreparsedExtraction(reference(dna),200047,200165,'+',false,200004));}
    @Test void oldLoadedLineBoundaryCanProduceOnlyNs()throws Exception{assertEquals("N".repeat(100),HomerGeneratedSequences.oldPreparsedExtraction(reference("A".repeat(1000)),-60,58,'+',false,200004));}
    @Test void oldMaskAndReverseComplementUseExactOriginalBody()throws Exception{assertEquals("NNACGT",HomerGeneratedSequences.oldPreparsedExtraction(reference("ACGTac"+"G".repeat(100)),1,6,'-',true,100));}
    @Test void oldExtractionRejectsUnsafeCoordinatesAndOutputCapacity(){assertThrows(java.io.IOException.class,()->HomerGeneratedSequences.oldPreparsedExtraction(reference("A".repeat(100)),1,20,'+',false,10));assertThrows(java.io.IOException.class,()->HomerGeneratedSequences.oldPreparsedExtraction(reference("A".repeat(100)),20,1,'+',false,100));}
    @Test void oldQuotaCarriesShortfallTowardLowerSupportedBins(){assertEquals(Map.of(3,1L,2,8L),HomerGeneratedSequences.oldSelectionQuotas(Map.of(3,2L,2,1L,1,100L),Map.of(3,1L,2,10L),9));}
    @Test void oldQuotaPreservesSourceRoundingAndCpGBinEleven(){assertEquals(Map.of(11,1L,0,1L),HomerGeneratedSequences.oldSelectionQuotas(Map.of(11,1L,0,1L),Map.of(11,10L,0,10L),3));}
    @Test void oldQuotaRejectsInvalidBinsAndCounts(){assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.oldSelectionQuotas(Map.of(12,1L),Map.of(),2));assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.oldSelectionQuotas(Map.of(1,1L),Map.of(1,-1L),2));}
    @Test void newFilterRetainsShortSequencesAndExactHalfNs(){assertEquals(HomerGenomicSequences.Disposition.RETAINED,HomerGeneratedSequences.newSelectionDisposition("AN"));assertEquals(HomerGenomicSequences.Disposition.EXCESS_N,HomerGeneratedSequences.newSelectionDisposition("ANN"));assertEquals(HomerGenomicSequences.Disposition.EXCESS_N,HomerGeneratedSequences.newSelectionDisposition("NN"));}
    @Test void newGcDoesNotUseOldSixDecimalRounding(){assertEquals(1.0/3,HomerGeneratedSequences.newGc("ACANN"));assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.newGc("NN"));}
    @Test void newRankThresholdUsesIndexAfterCeilingGroupAndInclusiveAssignment(){double[] thresholds=HomerGeneratedSequences.newBinThresholds(new double[]{.1,.2,.3,.4},2);assertArrayEquals(new double[]{-.2,Float.MAX_VALUE},thresholds);assertEquals(0,HomerGeneratedSequences.newBin(.2,thresholds));assertEquals(1,HomerGeneratedSequences.newBin(.1,thresholds));}
    @Test void newRepeatedThresholdsFailBeforeLastSentinelReplacement(){assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.newBinThresholds(new double[]{.5,.5,.5,.5},2));assertArrayEquals(new double[]{Float.MAX_VALUE},HomerGeneratedSequences.newBinThresholds(new double[]{.5},1));}
    @Test void newKmerColumnsAreLittleEndianAndExcludeNWindows(){double[][] f=HomerGeneratedSequences.newKmerProfile(List.of("ACGTNAC"),2,false,7);assertEquals(.5,f[0][4]);assertEquals(.25,f[0][9]);assertEquals(.25,f[0][14]);assertEquals(1,java.util.Arrays.stream(f[0]).sum());}
    @Test void newPositionalProfileUsesWindowSpecificDenominatorsAndZeroTail(){double[][] f=HomerGeneratedSequences.newKmerProfile(List.of("ACG","ANG"),2,true,3);assertEquals(1,f[0][4]);assertEquals(1,f[1][9]);assertEquals(0,java.util.Arrays.stream(f[2]).sum());}
    @Test void newProfilesRejectUnboundedAndUnnormalizedInputs(){assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.newKmerProfile(List.of("ACGT"),2,true,1));assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.newKmerProfile(List.of("acgt"),2,false,4));assertThrows(IllegalArgumentException.class,()->HomerGeneratedSequences.newKmerProfile(List.of(),2,false,4));}
}
