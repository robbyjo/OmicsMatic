package org.jlinalg.sequence;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerRnaCountsTest {
    private static HomerCoverage.Tag tag(int position, char strand, float weight) {
        return new HomerCoverage.Tag(position, strand, 100, weight);
    }
    private static HomerRnaCounts.Exon exon(String id, int start, int end, char strand) {
        return new HomerRnaCounts.Exon(id, start, end, strand);
    }
    private static List<HomerCoverage.Tag> referenceTags() {
        return List.of(tag(100,'+',1),tag(100,'-',1),tag(101,'+',1),tag(109,'-',1),
                tag(110,'+',1),tag(115,'+',1),tag(119,'-',1),tag(120,'+',1),tag(121,'-',1),
                tag(180,'+',1),tag(200,'+',1),tag(201,'-',1));
    }
    private static List<HomerRnaCounts.Exon> referenceExons() {
        return List.of(exon("A1",100,109,'+'),exon("B1",100,130,'+'),exon("C1",100,140,'-'),
                exon("A2",115,120,'+'),exon("Adup",180,200,'+'),exon("One",200,200,'+'));
    }
    private static HomerRnaCounts.Counts count(List<HomerCoverage.Tag> tags,
            List<HomerRnaCounts.Exon> exons,HomerRnaCounts.Strand strand)throws IOException {
        return HomerRnaCounts.count(tags,exons,strand,1000,1000,1000,()->{});
    }
    @Test void actualOriginalBothStrandReferenceRetainsBoundaryUndercount()throws Exception {
        var result=count(referenceTags(),referenceExons(),HomerRnaCounts.Strand.BOTH);
        HomerRnaCounts.audit(result,Map.of("A1",4.,"B1",7.,"C1",7.,"A2",3.,"Adup",2.,"One",1.));
        assertEquals(9,result.exons().get(1).literalHalfUnits()/2.0);
        assertEquals(7,result.exons().get(1).maximumSourceTags());
    }
    @Test void actualOriginalSameStrandReferenceUsesRelativePeakStrands()throws Exception {
        var result=count(referenceTags(),referenceExons(),HomerRnaCounts.Strand.SAME);
        HomerRnaCounts.audit(result,Map.of("A1",2.,"B1",4.,"C1",4.,"A2",2.,"Adup",2.,"One",1.));
    }
    @Test void actualOriginalOppositeStrandReferenceKeepsSeparateBoundaryOwners()throws Exception {
        var result=count(referenceTags(),referenceExons(),HomerRnaCounts.Strand.OPPOSITE);
        HomerRnaCounts.audit(result,Map.of("A1",2.,"B1",3.,"C1",5.,"A2",1.,"Adup",0.,"One",0.));
    }
    @Test void exactComparatorTiesRequireOneCompleteCorrelatedOwner()throws Exception {
        var result=count(List.of(tag(100,'+',1)),List.of(exon("a",100,120,'+'),exon("b",100,120,'+')),HomerRnaCounts.Strand.BOTH);
        assertEquals(2,result.boundaryFamilies().get(0).members().size());
        HomerRnaCounts.audit(result,Map.of("a",1.,"b",0.));
        HomerRnaCounts.audit(result,Map.of("a",0.,"b",1.));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of("a",1.,"b",1.)));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of("a",.5,"b",.5)));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of("a",0.,"b",0.)));
    }
    @Test void missingExtraAndInteriorAlterationsCannotPassCompleteAudit()throws Exception {
        var result=count(List.of(tag(110,'+',.5f)),List.of(exon("a",100,120,'+')),HomerRnaCounts.Strand.BOTH);
        HomerRnaCounts.audit(result,Map.of("a",.5));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of()));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of("extra",.5)));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of("a",1.)));
    }
    @Test void emptyChromosomeAndUnsortedExonInputsKeepTheirCompleteIdentities()throws Exception {
        var result=count(List.of(),List.of(exon("last",200,300,'-'),exon("first",20,40,'+')),HomerRnaCounts.Strand.SAME);
        assertEquals(List.of("last","first"),result.exons().stream().map(c->c.exon().id()).toList());
        HomerRnaCounts.audit(result,Map.of("last",0.,"first",0.));
        assertThrows(UnsupportedOperationException.class,()->result.exons().clear());
    }
    @Test void halfWeightsAreExactAndFragmentLengthDoesNotMoveAnchors()throws Exception {
        var result=count(List.of(new HomerCoverage.Tag(100,'+',356,.5f),tag(120,'-',.5f)),List.of(exon("a",100,120,'+')),HomerRnaCounts.Strand.BOTH);
        HomerRnaCounts.audit(result,Map.of("a",1.));
        assertEquals(2,result.exons().get(0).literalHalfUnits());
    }
    @Test void malformedPopulationsAndCallerFailuresAreRejected() {
        assertThrows(IllegalArgumentException.class,()->count(List.of(tag(100,'+',.1f)),List.of(),HomerRnaCounts.Strand.BOTH));
        assertThrows(IllegalArgumentException.class,()->count(List.of(tag(200,'+',1),tag(100,'+',1)),List.of(),HomerRnaCounts.Strand.BOTH));
        assertThrows(IllegalArgumentException.class,()->count(List.of(),List.of(exon("a",1,2,'+'),exon("a",3,4,'+')),HomerRnaCounts.Strand.BOTH));
        assertThrows(IOException.class,()->HomerRnaCounts.count(List.of(),List.of(),HomerRnaCounts.Strand.BOTH,100,1,1,()->{throw new IOException("deadline");}));
    }
    @Test void originalRpkmUsesWholeMassAndSourceEndMinusStartOrAveragedLength() {
        assertEquals("19444444.444",HomerRnaCounts.printed(HomerRnaCounts.rpkm(7,12,30)));
        assertEquals("44117647.059",HomerRnaCounts.printed(HomerRnaCounts.rpkm(9,12,17)));
        assertEquals("0.000",HomerRnaCounts.printed(HomerRnaCounts.rpkm(0,12,20)));
    }
    @Test void invalidLengthFallbackIsNeverLabeledAsValidRpkm() {
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.rpkm(1,12,0));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.rpkm(1,0,10));
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.printed(Double.NaN));
    }
    @Test void firstEmptySourcePartIsSkippedByCompletedPrefixBeforeBoundaryScan()throws Exception {
        var result=count(List.of(tag(100,'+',.5f),tag(101,'+',1)),
                List.of(exon("empty",100,99,'+'),exon("valid",100,120,'+')),HomerRnaCounts.Strand.BOTH);
        HomerRnaCounts.audit(result,Map.of("empty",0.,"valid",1.5));
        assertEquals(0,result.boundaryFamilies().get(0).discardedHalfUnits());
        assertEquals(3,result.exons().get(1).literalHalfUnits());
        assertEquals(0,result.exons().get(0).literalHalfUnits());
        assertThrows(IllegalArgumentException.class,()->HomerRnaCounts.audit(result,Map.of("empty",.5,"valid",1.)));
    }
    @Test void emptyPartBlocksOnlyItsCompatibleStrand()throws Exception {
        var tags=List.of(tag(100,'+',1),tag(100,'-',.5f));
        var exons=List.of(exon("cover",90,130,'-'),exon("empty",100,99,'+'),exon("plus",100,120,'+'),exon("minus",100,120,'-'));
        HomerRnaCounts.audit(count(tags,exons,HomerRnaCounts.Strand.SAME),Map.of("cover",.5,"empty",0.,"plus",0.,"minus",.5));
        HomerRnaCounts.audit(count(tags,exons,HomerRnaCounts.Strand.OPPOSITE),Map.of("cover",1.,"empty",0.,"plus",0.,"minus",1.));
    }
    @Test void largerInversionsAreNotAnOriginalEmptyFeaturePart() {
        assertThrows(IllegalArgumentException.class,()->exon("bad",100,98,'+'));
    }
}
