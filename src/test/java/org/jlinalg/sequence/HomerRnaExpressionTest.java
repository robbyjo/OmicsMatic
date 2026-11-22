package org.jlinalg.sequence;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerRnaExpressionTest {
    private static final String RESOURCE="A\tchr1\t100\t120\t+\tE1:100,I1:110,E2:115\nA\tchr1\t180\t200\t+\tE1:180\n";
    private static List<HomerRnaTranscripts.Transcript> rows(String resource)throws IOException {
        var rows=new ArrayList<HomerRnaTranscripts.Transcript>();
        HomerRnaTranscripts.scan(new StringReader(resource),Map.of("chr1",1000L),HomerRnaTranscripts.Policy.RETAIN_ORIGINAL,HomerRnaTranscripts.Policy.RETAIN_ORIGINAL,new HomerRnaTranscripts.Limits(100,1000,100,1000,10000),rows::add,(n,s,d)->{},()->{});
        return rows;
    }
    private static HomerRnaExpression.Builder builder(HomerRnaExpression.Mode mode) {
        return new HomerRnaExpression.Builder(2,mode,100,1000,1000,100,()->{});
    }
    private static void addReference(HomerRnaExpression.Builder b)throws IOException {
        var rows=rows(RESOURCE);b.add(rows.get(0),List.of(new long[]{8,4},new long[]{6,3}));b.add(rows.get(1),List.of(new long[]{4,2}));
    }
    @Test void actualSourceDefaultCopyFamilySumsCountsButAveragesLengths()throws Exception {
        var b=builder(HomerRnaExpression.Mode.TRANSCRIPT_COPY_FAMILIES);addReference(b);
        var table=b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW);var row=table.rows().get(0);
        assertEquals("A",row.id());assertEquals(2,row.copies());assertEquals(17,row.effectiveLength());
        assertEquals(List.of("9.000","4.500"),row.printedValues());assertEquals(2,row.contributors().size());
    }
    @Test void originalRpkmUsesEachWholeDirectoryMassAndAveragedCopyLength()throws Exception {
        var b=builder(HomerRnaExpression.Mode.TRANSCRIPT_COPY_FAMILIES);addReference(b);
        assertEquals(List.of("44117647.059","44117647.059"),b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RPKM).rows().get(0).printedValues());
    }
    @Test void noCondensingRetainsExecutionSuffixAndEachTranscriptLength()throws Exception {
        var b=builder(HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS);addReference(b);var table=b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW);
        assertEquals(List.of("A","A-HOMER1"),table.rows().stream().map(HomerRnaExpression.Row::id).toList());
        assertEquals(List.of(14.,20.),table.rows().stream().map(HomerRnaExpression.Row::effectiveLength).toList());
    }
    @Test void separatePartsPreserveBothPartAndCopyIdentities()throws Exception {
        var b=builder(HomerRnaExpression.Mode.EXON_PARTS);addReference(b);var table=b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW);
        assertEquals(List.of("A--Part1","A--Part2","A-HOMER1--Part1"),table.rows().stream().map(HomerRnaExpression.Row::id).toList());
        assertEquals(List.of(9.,5.,20.),table.rows().stream().map(HomerRnaExpression.Row::effectiveLength).toList());
    }
    @Test void sourceMetadataContributorsAreKeptInsteadOfInventingHighestExpressionCoordinates()throws Exception {
        var b=builder(HomerRnaExpression.Mode.TRANSCRIPT_COPY_FAMILIES);addReference(b);var row=b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW).rows().get(0);
        assertEquals(List.of(100,180),row.contributors().stream().map(HomerRnaExpression.Contributor::start1).toList());
        assertThrows(UnsupportedOperationException.class,()->row.contributors().clear());
    }
    @Test void inputArraysCannotMutatePreviouslyAcceptedCounts()throws Exception {
        var b=builder(HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS);var row=rows("A\tchr1\t100\t120\t+\tE1:100\n").get(0);long[] values={2,1};b.add(row,List.of(values));values[0]=100;
        assertEquals(List.of("1.000","0.500"),b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW).rows().get(0).printedValues());
    }
    @Test void missingPartCountsLatchFailureAndPreventPrefixSuccess()throws Exception {
        var b=builder(HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS);var rows=rows(RESOURCE);
        assertThrows(IllegalArgumentException.class,()->b.add(rows.get(0),List.of(new long[]{2,1})));
        assertThrows(IllegalStateException.class,()->b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW));
    }
    @Test void duplicateTranscriptSubmissionAndOutputCapacityCannotPass()throws Exception {
        var row=rows("A\tchr1\t100\t120\t+\tE1:100\n").get(0);var b=builder(HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS);b.add(row,List.of(new long[]{2,1}));
        assertThrows(IllegalArgumentException.class,()->b.add(row,List.of(new long[]{2,1})));
        var capped=new HomerRnaExpression.Builder(2,HomerRnaExpression.Mode.EXON_PARTS,10,10,1,10,()->{});
        assertThrows(IOException.class,()->capped.add(rows(RESOURCE).get(0),List.of(new long[]{2,1},new long[]{2,1})));
        assertThrows(IllegalStateException.class,()->capped.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW));
    }
    @Test void originalInvalidLengthFallbackCannotBecomeValidRpkm()throws Exception {
        var row=rows("A\tchr1\t100\t100\t+\tE1:100\n").get(0);var b=builder(HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS);b.add(row,List.of(new long[]{2,1}));
        assertThrows(IOException.class,()->b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RPKM));
    }
    @Test void finishedAccumulatorIsImmutableAndCannotBeReused()throws Exception {
        var b=builder(HomerRnaExpression.Mode.TRANSCRIPT_COPY_FAMILIES);addReference(b);var table=b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW);
        assertThrows(UnsupportedOperationException.class,()->table.rows().clear());
        assertThrows(IllegalStateException.class,()->b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW));
    }
    @Test void inconsistentTranscriptLengthCannotContaminateAnAggregation()throws Exception {
        var t=rows("A\tchr1\t100\t120\t+\tE1:100\n").get(0);
        var changed=new HomerRnaTranscripts.Transcript(t.lineNumber(),t.rawId(),t.executionId(),t.condensedId(),t.contig(),t.start1(),t.end1(),t.strand(),t.features(),t.exons(),99,t.missingReferenceContig(),t.outsideReference(),t.emptyExonParts(),t.oneBaseExonParts(),t.rawLine());
        var b=builder(HomerRnaExpression.Mode.DISTINCT_TRANSCRIPTS);
        assertThrows(IllegalArgumentException.class,()->b.add(changed,List.of(new long[]{2,1})));
        assertThrows(IllegalStateException.class,()->b.finish(new double[]{12,6},HomerRnaExpression.Normalization.RAW));
    }
}
