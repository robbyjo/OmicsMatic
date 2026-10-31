package org.jlinalg.sequence;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HomerPeakAnnotationsTest {
    private static final Map<String,Long> LENGTHS=Map.of("chr1",1000000000L,"chr2",1000000000L);
    private static String exon(String id,String gene,String chr,int start,int end,String strand){return chr+"\tx\texon\t"+start+"\t"+end+"\t.\t"+strand+"\t.\tgene_id \""+gene+"\"; transcript_id \""+id+"\";\n";}
    private static HomerPeakAnnotations.Summary read(String text)throws IOException{return HomerPeakAnnotations.readGtf(new StringReader(text),LENGTHS,false,10000,10000,10000);}
    @Test void originalMetadataVersionsRemainSeparateFromFullGtfIds()throws Exception{var t=read(exon("A.2","GA.7","chr1",100,200,"+")).transcripts().get(0);assertEquals("A.2",t.id());assertEquals("GA.7",t.gene());assertEquals("GA",t.metadataGeneId());assertEquals("GA.7_PAR_Y",HomerPeakAnnotations.unversionedMetadataId("GA.7_PAR_Y"));}
    @Test void versionStrippedTranscriptGeneCollisionsFailBeforeOriginalConflation(){assertThrows(IOException.class,()->read(exon("A.1","GA","chr1",100,200,"+")+exon("A.2","GB","chr1",300,400,"+")));}
    @Test void oneBasedInclusiveMidpointAndTranscriptOrientedSigns()throws Exception{
        var index=new HomerPeakAnnotations.Index(read(exon("A","GA","chr1",10001,10400,"+")+exon("B","GB","chr1",15001,15400,"-")));
        assertTrue(index.nearest("chr1",10011,10080).accepts("A",44));assertTrue(index.nearest("chr1",15311,15380).accepts("B",55));assertTrue(index.nearest("chr1",16001,16100).accepts("B",-650));
    }
    @Test void tiesRetainAllEquallyNearestIdsAndBothSignedDistances()throws Exception{
        var index=new HomerPeakAnnotations.Index(read(exon("A","GA","chr1",100,130,"+")+exon("B","GB","chr1",170,200,"-")));
        var n=index.nearest("chr1",150,150);assertEquals(50,n.absoluteDistance());assertEquals(2,n.distinctAssignments());assertTrue(n.accepts("A",50));assertTrue(n.accepts("B",50));assertFalse(n.accepts("A",-50));
    }
    @Test void noncontiguousExonsFollowOriginalSortBeforeTranscriptAggregation()throws Exception{
        var summary=read(exon("A","GA","chr1",100,120,"+")+exon("B","GB","chr1",500,510,"+")+exon("A","GA","chr1",900,920,"+"));
        assertEquals(2,summary.transcripts().size());assertEquals(1,summary.noncontiguousSourceBlocks());assertEquals(2,summary.transcripts().get(0).sourceBlocks());var n=new HomerPeakAnnotations.Index(summary).nearest("chr1",901,902);assertTrue(n.accepts("B",401));assertFalse(n.accepts("A",1));
    }
    @Test void onlyLowercaseExonsDefineTssAndContiguousGroupMembership()throws Exception{
        String a=exon("A","GA","chr1",100,150,"+");var summary=read(a+a.replace("\texon\t","\tCDS\t").replace("\t100\t150\t","\t1\t999\t")+exon("A","GA","chr1",200,300,"+"));
        assertEquals(3,summary.records());assertEquals(2,summary.exons());assertEquals(1,summary.transcripts().size());assertEquals(100,summary.transcripts().get(0).tss1());
    }
    @Test void unknownContigsRetainNoCandidateRatherThanBorrowAnotherChromosome()throws Exception{
        var n=new HomerPeakAnnotations.Index(read(exon("A","GA","chr1",10,20,"+"))).nearest("chr2",10,20);assertEquals(-1,n.absoluteDistance());assertTrue(n.candidates().isEmpty());assertFalse(n.accepts("A",5));
    }
    @Test void dotStrandRequiresExplicitOriginalPlusPermission()throws Exception{
        String text=exon("A","GA","chr1",100,200,".");assertThrows(IOException.class,()->read(text));var summary=HomerPeakAnnotations.readGtf(new StringReader(text),LENGTHS,true,100,100,1000);assertEquals(1,summary.plusAssignedExons());assertEquals('+',summary.transcripts().get(0).strand());
    }
    @Test void inconsistentTranscriptContigStrandAndGeneFailBeforeOriginalLoss(){
        String first=exon("A","GA","chr1",100,200,"+");for(String other:List.of(exon("A","GB","chr1",200,300,"+"),exon("A","GA","chr2",200,300,"+"),exon("A","GA","chr1",200,300,"-")))assertThrows(IOException.class,()->read(first+other));
    }
    @Test void missingDuplicateOrUpstreamIncompatibleParentAttributesFail(){
        String text=exon("A","GA","chr1",100,200,"+");for(String invalid:List.of(text.replace("transcript_id","transcript_name"),text.replace("gene_id \"GA\";","gene_id \"GA\"; gene_id \"GB\";"),text.replace("transcript_id ","transcript_id  "),text.replace("\"A\"","\"A-HOMER1\"")))assertThrows(IOException.class,()->read(invalid));
    }
    @Test void badReferenceIntervalsAndNonfiniteCoordinateTextFail(){
        String text=exon("A","GA","chr1",100,200,"+");for(String invalid:List.of(text.replace("chr1","chrX"),text.replace("\t100\t","\t0\t"),text.replace("\t200\t","\t99\t"),text.replace("\t100\t","\t1e2\t"),text.replace("\t200\t","\t1000000001\t")))assertThrows(IOException.class,()->read(invalid));
    }
    @Test void crlfAndBoundedRecordsSegmentsAndLinesAreHandledExplicitly()throws Exception{
        String a=exon("A","GA","chr1",100,200,"+"),b=exon("B","GB","chr1",300,400,"+");assertEquals(2,read((a+b).replace("\n","\r\n")).exons());
        assertThrows(IOException.class,()->HomerPeakAnnotations.readGtf(new StringReader(a+b),LENGTHS,false,1,10,1000));assertThrows(IOException.class,()->HomerPeakAnnotations.readGtf(new StringReader(a+b),LENGTHS,false,10,1,1000));assertThrows(IOException.class,()->HomerPeakAnnotations.readGtf(new StringReader(a),LENGTHS,false,10,10,10));
    }
    @Test void indexedCandidatesMatchIndependentCompleteEnumeration()throws Exception{
        var random=new Random(119);var rows=new ArrayList<HomerPeakAnnotations.Transcript>();for(int i=0;i<500;i++){int start=1+random.nextInt(10000),end=start+random.nextInt(1000);rows.add(new HomerPeakAnnotations.Transcript("T"+i,"G"+i,i%3==0?"chr2":"chr1",i%2==0?'+':'-',start,end,1));}
        var index=new HomerPeakAnnotations.Index(new HomerPeakAnnotations.Summary(500,500,0,0,rows));
        for(int i=0;i<400;i++){String chr=i%4==0?"chr2":"chr1";int start=1+random.nextInt(11000),end=start+random.nextInt(300);long midpoint=((long)start+end)/2;long minimum=rows.stream().filter(t->t.contig().equals(chr)).mapToLong(t->Math.abs(midpoint-t.tss1())).min().orElseThrow();var actual=index.nearest(chr,start,end);assertEquals(minimum,actual.absoluteDistance());assertEquals(rows.stream().filter(t->t.contig().equals(chr)&&Math.abs(midpoint-t.tss1())==minimum).count(),actual.candidates().size());}
    }
    @Test void largeCoordinatesDoNotOverflowTheMidpoint()throws Exception{
        var index=new HomerPeakAnnotations.Index(read(exon("A","GA","chr1",999999900,1000000000,"-")));assertTrue(index.nearest("chr1",999999999,1000000000).accepts("A",1));assertThrows(IllegalArgumentException.class,()->index.nearest("chr1",0,1));
    }
}
