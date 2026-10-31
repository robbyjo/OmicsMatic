package org.jlinalg.sequence;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerRnaTranscriptsTest {
    private static final HomerRnaTranscripts.Policy KEEP=HomerRnaTranscripts.Policy.RETAIN_ORIGINAL;
    private static final HomerRnaTranscripts.Policy ERROR=HomerRnaTranscripts.Policy.ERROR;
    private static final HomerRnaTranscripts.Limits LIMITS=new HomerRnaTranscripts.Limits(100,1000,100,1000,10000);
    private static List<HomerRnaTranscripts.Transcript> scan(String text)throws IOException {
        var rows=new ArrayList<HomerRnaTranscripts.Transcript>();
        HomerRnaTranscripts.scan(new StringReader(text),Map.of("chr1",1000L),KEEP,KEEP,LIMITS,rows::add,(n,s,d)->{},()->{});
        return rows;
    }
    @Test void originalExonExpansionKeepsIntronsOutAndEndMinusStartLength()throws Exception {
        var row=scan("A\tchr1\t100\t120\t+\tE1:100,I1:110,E2:115\n").get(0);
        assertEquals(14,row.sourceEffectiveLength());
        assertEquals(List.of(109,120),row.exons().stream().map(p->p.feature().end1()).toList());
        assertEquals(List.of("A--Part1","A--Part2"),row.exons().stream().map(HomerRnaTranscripts.Part::id).toList());
        assertEquals(3,row.features().size());
    }
    @Test void minusTranscriptNumericSortDoesNotReverseTheSourcePartOrder()throws Exception {
        var row=scan("NM\tchr1\t100\t160\t-\tE1:150,I1:130,E2:100\n").get(0);
        assertEquals(List.of(100,150),row.exons().stream().map(p->p.feature().start1()).toList());
        assertEquals(List.of(3,1),row.exons().stream().map(p->p.feature().inputOrdinal()).toList());
    }
    @Test void utrTokensBelongToExonsAndEqualBreakpointsKeepEmptyParts()throws Exception {
        var row=scan("NM\tchr1\t100\t130\t+\tE1_5UTR:100,E1:100,E1_3UTR:120\n").get(0);
        assertEquals(3,row.exons().size());
        assertEquals(-1,row.exons().get(0).effectiveLength());
        assertEquals(28,row.sourceEffectiveLength());
        assertEquals(1,row.emptyExonParts());
        assertEquals(99,row.exons().get(0).exon('+').end1());
    }
    @Test void oneBasePartRetainsZeroDenominatorContribution()throws Exception {
        var row=scan("One\tchr1\t100\t100\t+\tE1:100\n").get(0);
        assertEquals(0,row.sourceEffectiveLength());
        assertEquals(1,row.oneBaseExonParts());
    }
    @Test void duplicateRawIdAndExistingCopySuffixKeepSeparateFamilies()throws Exception {
        var rows=scan("A\tchr1\t100\t120\t+\tE1:100\nA\tchr1\t200\t220\t+\tE1:200\nA-HOMER2\tchr1\t300\t320\t+\tE1:300\n");
        assertEquals(List.of("A","A-HOMER1","A-HOMER2"),rows.stream().map(HomerRnaTranscripts.Transcript::executionId).toList());
        assertEquals(List.of("A","A","A"),rows.stream().map(HomerRnaTranscripts.Transcript::condensedId).toList());
    }
    @Test void generatedExecutionIdCollisionCannotSilentlyOverwriteAResourceRow() {
        assertThrows(IOException.class,()->scan("A\tchr1\t100\t120\t+\tE1:100\nA-HOMER1\tchr1\t200\t220\t+\tE1:200\nA\tchr1\t300\t320\t+\tE1:300\n"));
    }
    @Test void everyLineAndRawCrLfRecordHaveAnExplicitDisposition()throws Exception {
        String text="# resource\r\n\nα\tchr1\t100\t120\t0\tE1:100\r\n";
        var dispositions=new ArrayList<HomerRnaTranscripts.Disposition>();var rows=new ArrayList<HomerRnaTranscripts.Transcript>();
        var summary=HomerRnaTranscripts.scan(new StringReader(text),Map.of("chr1",1000L),KEEP,KEEP,LIMITS,rows::add,(n,s,d)->dispositions.add(d),()->{});
        assertEquals(List.of(HomerRnaTranscripts.Disposition.COMMENT,HomerRnaTranscripts.Disposition.BLANK,HomerRnaTranscripts.Disposition.TRANSCRIPT),dispositions);
        assertEquals(text.length(),summary.decodedCharacters());assertTrue(rows.get(0).rawLine().endsWith("\r"));
        assertEquals('+' ,rows.get(0).strand());
        assertThrows(UnsupportedOperationException.class,()->rows.get(0).exons().clear());
    }
    @Test void missingContigsAndOverrunsAreRetainedOnlyByExplicitPolicy()throws Exception {
        String text="A\tchrOther\t100\t120\t-\tE1:100\n";
        assertTrue(scan(text).get(0).missingReferenceContig());
        assertThrows(IOException.class,()->HomerRnaTranscripts.scan(new StringReader(text),Map.of("chr1",1000L),ERROR,KEEP,LIMITS,t->{},(n,s,d)->{},()->{}));
        assertTrue(scan("B\tchr1\t990\t1020\t+\tE1:990\n").get(0).outsideReference());
    }
    @Test void sourceShortPartPolicyRefusesWithoutImplicitClipping() {
        assertThrows(IOException.class,()->HomerRnaTranscripts.scan(new StringReader("A\tchr1\t100\t100\t+\tE1:100\n"),Map.of("chr1",1000L),KEEP,ERROR,LIMITS,t->{},(n,s,d)->{},()->{}));
    }
    @Test void malformedFeaturesUnsupportedStrandsAndTranscriptGeometryFail() {
        for(String row:List.of("A\tchr1\t100\t120\t.\tE1:100\n","A\tchr1\t100\t120\t+\tE1:99\n","A\tchr1\t100\t120\t+\tE1:100:extra\n","A\tchr1\t100\t120\t+\tI1:100\n"))assertThrows(IOException.class,()->scan(row));
    }
    @Test void streamingLimitsRejectWholeAnalysisInsteadOfReturningAPrefix() {
        String row="A\tchr1\t100\t120\t+\tE1:100\n";
        for(var limit:List.of(new HomerRnaTranscripts.Limits(1,100,100,100,1000),new HomerRnaTranscripts.Limits(100,100,100,5,1000),new HomerRnaTranscripts.Limits(100,100,100,100,5))) {
            assertThrows(IOException.class,()->HomerRnaTranscripts.scan(new StringReader(row+row),Map.of("chr1",1000L),KEEP,KEEP,limit,t->{},(n,s,d)->{},()->{}));
        }
    }
    @Test void callerFailuresAndFinalLineWithoutLfArePreserved()throws Exception {
        String row="A\tchr1\t100\t120\t1\tE1:100";
        assertEquals('-',scan(row).get(0).strand());
        assertThrows(IOException.class,()->HomerRnaTranscripts.scan(new StringReader(row),Map.of("chr1",1000L),KEEP,KEEP,LIMITS,t->{throw new IOException("sink");},(n,s,d)->{},()->{}));
        assertThrows(IOException.class,()->HomerRnaTranscripts.scan(new StringReader(row),Map.of("chr1",1000L),KEEP,KEEP,LIMITS,t->{},(n,s,d)->{},()->{throw new IOException("deadline");}));
    }
    @Test void identityByteBoundIsCheckedBeforeRepeatedPartNameAllocation()throws Exception {
        assertEquals("A".repeat(254),scan("A".repeat(254)+"\tchr1\t100\t120\t+\tE1:100\n").get(0).rawId());
        assertThrows(IOException.class,()->scan("α".repeat(128)+"\tchr1\t100\t120\t+\tE1:100\n"));
    }
}
