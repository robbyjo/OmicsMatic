package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomerMotifsTest {
    @TempDir Path directory;
    private HomerMotifs.Motif motif(String consensus,double threshold){
        double[][] rows=new double[consensus.length()][4];
        for(int i=0;i<rows.length;i++)for(int j=0;j<4;j++)rows[i][j]="ACGT".charAt(j)==consensus.charAt(i)?.97:.01;
        return new HomerMotifs.Motif(1,consensus,"M",threshold,0.0,List.of(),rows);
    }
    private HomerMotifs.Options options(HomerMotifs.Strands strands,Integer offset,boolean mask){
        return new HomerMotifs.Options(strands,offset,mask,10000,1000000,100000);
    }
    private List<HomerMotifs.Hit> scan(String sequence,HomerMotifs.Motif motif,HomerMotifs.Options options)throws Exception{
        var hits=new ArrayList<HomerMotifs.Hit>();HomerMotifs.scan(sequence,motif,options,hits::add);return hits;
    }
    private Path input(String text)throws Exception{Path p=Files.createTempFile(directory,"motif-",".motif");Files.writeString(p,text);return p;}
    private List<HomerMotifs.Motif> read(String text)throws Exception{return HomerMotifs.read(input(text),new HomerMotifs.Limits(10,100,1000,100000,100000,10000));}
    @Test void originalNaturalLogModelUsesInclusiveThresholdAndBothOrientedAnchors()throws Exception{
        var m=motif("AC",2*StrictMath.log(.97/.25));var h=scan("ACNNGT",m,options(HomerMotifs.Strands.BOTH,0,false));
        assertEquals(2,h.size());assertEquals(new HomerMotifs.Hit(0,2,0,'+',"AC",m.threshold()),h.get(0));
        assertEquals(new HomerMotifs.Hit(4,6,5,'-',"GT",m.threshold()),h.get(1));
    }
    @Test void reverseMatchedTextStaysInForwardOrientation()throws Exception{
        var h=scan("TTGTAA",motif("AC",2),options(HomerMotifs.Strands.REVERSE,0,false));assertEquals(1,h.size());assertEquals("GT",h.get(0).forwardWord());assertEquals(3,h.get(0).reportedPosition());
    }
    @Test void midpointUsesWholeSequenceAndTruncatesOddLengthTowardZero()throws Exception{
        var h=scan("ACNGT",motif("AC",2),options(HomerMotifs.Strands.BOTH,null,false));
        assertEquals(List.of(-2L,2L),h.stream().map(HomerMotifs.Hit::reportedPosition).toList());
    }
    @Test void explicitNegativeAndLargeOffsetsHaveNoImplicitGenomicMeaning()throws Exception{
        var h=scan("AC",motif("AC",2),options(HomerMotifs.Strands.FORWARD,Integer.MAX_VALUE,false));
        assertEquals(Integer.MAX_VALUE,h.get(0).reportedPosition());
        assertEquals(-100,scan("AC",motif("AC",2),options(HomerMotifs.Strands.FORWARD,-100,false)).get(0).reportedPosition());
    }
    @Test void canonicalLowercaseIsPromotedAndAmbiguousWindowsAreExcluded()throws Exception{
        var hits=new ArrayList<HomerMotifs.Hit>();var s=HomerMotifs.scan("acNgt",motif("AC",2),options(HomerMotifs.Strands.BOTH,0,false),hits::add);
        assertEquals(4,s.lowercasePromoted());assertEquals(4,s.windows());assertEquals(2,s.ambiguousWindows());assertEquals(4,s.scoredSites());assertEquals(2,s.hits());
    }
    @Test void uracilAndOtherPrintableNonDnaAreMaskedOnlyUnderExplicitPolicy()throws Exception{
        assertThrows(IOException.class,()->scan("AUCG",motif("AC",2),options(HomerMotifs.Strands.BOTH,0,false)));
        var s=HomerMotifs.scan("AUR.XCG",motif("AC",2),options(HomerMotifs.Strands.BOTH,0,true),h->{});
        assertEquals(4,s.maskedResidues());assertEquals(5,s.ambiguousWindows());assertEquals(0,s.hits());
    }
    @Test void whitespaceControlsAndUnicodeResiduesAreNeverQuietlyMasked(){
        for(String sequence:List.of("A C","A\nC","A\u0000C","AαC"))assertThrows(IOException.class,()->scan(sequence,motif("AC",2),options(HomerMotifs.Strands.BOTH,0,true)));
    }
    @Test void sourceZeroProbabilitiesAreFlooredWithoutRenormalizingRows()throws Exception{
        var m=new HomerMotifs.Motif(1,"N","zero",0,null,List.of(),new double[][]{{1,0,0,0}});
        assertEquals(0,m.probabilities()[0][1]);var h=scan("A",m,options(HomerMotifs.Strands.FORWARD,0,false));
        assertEquals(StrictMath.log(4),h.get(0).logOdds());assertEquals(1,m.probabilities()[0][0]);
        var low=new HomerMotifs.Motif(1,"N","zero",-100,null,List.of(),new double[][]{{1,0,0,0}});
        assertEquals(StrictMath.log(.001/.25),scan("C",low,options(HomerMotifs.Strands.FORWARD,0,false)).get(0).logOdds());
    }
    @Test void rowsAndAllMetadataAreDefensivelyCopied(){
        double[][] p={{.97,.01,.01,.01}};var extra=new ArrayList<>(List.of("source-only"));
        var m=new HomerMotifs.Motif(1,"A","M",1,0.0,extra,p);p[0][0]=0;extra.clear();m.probabilities()[0][0]=0;
        assertEquals(.97,m.probabilities()[0][0]);assertEquals(List.of("source-only"),m.extraHeader());assertThrows(UnsupportedOperationException.class,()->m.extraHeader().clear());
    }
    @Test void invalidProbabilitiesAndUnsupportedWeightsFailRatherThanNormalize(){
        for(double[][] p:new double[][][]{{{0,0,0,0}},{{2,0,0,0}},{{-.1,.3,.4,.4}},{{Double.NaN,0,0,1}},{{.2,.2,.2,.2}}})
            assertThrows(IllegalArgumentException.class,()->new HomerMotifs.Motif(1,"A","M",0,null,List.of(),p));
    }
    @Test void roundedSourceRowsAreAcceptedUnchanged()throws Exception{
        var m=read(">NN\tname with space\t0\t-3\tT:1,B:0\n.333\t.333\t.333\t0\n.250\t.250\t.250\t.251\n").get(0);
        assertEquals(.333,m.probabilities()[0][0]);assertEquals(-3,m.sourceLogP());assertEquals(List.of("T:1,B:0"),m.extraHeader());assertEquals(2,m.width());
    }
    @Test void duplicateNamesHaveSeparateOriginalOrdinals()throws Exception{
        var m=read(">A\tsame\t1\n1\t0\t0\t0\n>C\tsame\t1\n0\t1\t0\t0\n");assertEquals(List.of(1,2),m.stream().map(HomerMotifs.Motif::ordinal).toList());assertEquals("same",m.get(1).name());
    }
    @Test void plainGzipAndCrlfPreserveIdenticalModels()throws Exception{
        String text=">A\tM\t1\t0\n1\t0\t0\t0\n";Path gz=directory.resolve("compressed.dat");
        try(var out=new GZIPOutputStream(Files.newOutputStream(gz))){out.write(text.replace("\n","\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        var m=HomerMotifs.read(gz,new HomerMotifs.Limits(2,2,2,100000,100000,1000));
        assertEquals(read(text).get(0).threshold(),m.get(0).threshold());assertArrayEquals(read(text).get(0).probabilities()[0],m.get(0).probabilities()[0]);
    }
    @Test void malformedTruncatedAndNonUtf8FilesCannotProduceACompleteModel()throws Exception{
        for(String text:List.of("",">A\tM\t1\n","1\t0\t0\t0\n",">A\tM\tNaN\n1\t0\t0\t0\n",">A\tM\t1\n1\t0\t0\n"))
            assertThrows(IOException.class,()->read(text));
        Path p=directory.resolve("invalid");Files.write(p,new byte[]{(byte)255});assertThrows(IOException.class,()->HomerMotifs.read(p,new HomerMotifs.Limits(2,2,2,100,100,100)));
    }
    @Test void inputAndDecodedLineRowAndMotifBoundsAreIndependent()throws Exception{
        Path p=input(">A\tM\t1\n1\t0\t0\t0\n>C\tN\t1\n0\t1\t0\t0\n");
        for(var limit:List.of(new HomerMotifs.Limits(1,2,10,1000,1000,100),new HomerMotifs.Limits(2,2,1,1000,1000,100),new HomerMotifs.Limits(2,2,10,5,1000,100),new HomerMotifs.Limits(2,2,10,1000,5,100)))
            assertThrows(IOException.class,()->HomerMotifs.read(p,limit));
    }
    @Test void sequenceAndWorstCaseWorkAreCheckedBeforeCallbacks()throws Exception{
        int[] calls={0};var m=motif("AC",2);
        assertThrows(IOException.class,()->HomerMotifs.scan("ACGT",m,new HomerMotifs.Options(HomerMotifs.Strands.BOTH,0,false,3,100,10),h->calls[0]++));
        assertThrows(IOException.class,()->HomerMotifs.scan("ACGT",m,new HomerMotifs.Options(HomerMotifs.Strands.BOTH,0,false,10,11,10),h->calls[0]++));assertEquals(0,calls[0]);
    }
    @Test void denseTwoStrandHitsStreamSafelyWithAnExplicitFailureAtTheHitBound()throws Exception{
        var m=new HomerMotifs.Motif(1,"N","uniform",0,null,List.of(),new double[][]{{.25,.25,.25,.25}});
        assertEquals(20,scan("ACGTACGTAC",m,options(HomerMotifs.Strands.BOTH,0,false)).size());int[] n={0};
        assertThrows(IOException.class,()->HomerMotifs.scan("ACGT",m,new HomerMotifs.Options(HomerMotifs.Strands.BOTH,0,false,10,100,3),h->n[0]++));assertEquals(3,n[0]);
    }
    @Test void shortOrEmptySequenceHasACompleteZeroSiteSummary()throws Exception{
        for(String sequence:List.of("","A")){
            var s=HomerMotifs.scan(sequence,motif("AC",2),options(HomerMotifs.Strands.BOTH,0,false),h->fail("Unexpected hit"));
            assertEquals(0,s.windows());assertEquals(0,s.scoredSites());assertEquals(0,s.hits());
        }
    }
    @Test void callbackFailureIsPropagatedWithoutClaimingCompletion(){
        IOException sentinel=new IOException("reject provisional output");
        assertSame(sentinel,assertThrows(IOException.class,()->HomerMotifs.scan("AC",motif("AC",2),options(HomerMotifs.Strands.FORWARD,0,false),h->{throw sentinel;})));
    }
    @Test void reverseScanIsDescendingAfterEveryForwardHit()throws Exception{
        var m=new HomerMotifs.Motif(1,"NN","all",0,null,List.of(),new double[][]{{.25,.25,.25,.25},{.25,.25,.25,.25}});
        var h=scan("ACGT",m,options(HomerMotifs.Strands.BOTH,0,false));
        assertEquals(List.of(0,1,2,2,1,0),h.stream().map(HomerMotifs.Hit::left0).toList());assertEquals(List.of('+','+','+','-','-','-'),h.stream().map(HomerMotifs.Hit::strand).toList());
    }
    @Test void cachedModelsKeepSourceStateAndAllRepeatedScanResults()throws Exception{
        var m=motif("AC",2);var compiled=HomerMotifs.compile(m);m.probabilities()[0][0]=0;
        for(String s:List.of("ACGTAC","NNACNN","GTAC")){
            var h=new ArrayList<HomerMotifs.Hit>();HomerMotifs.scan(s,compiled,options(HomerMotifs.Strands.BOTH,0,false),h::add);
            assertEquals(scan(s,m,options(HomerMotifs.Strands.BOTH,0,false)),h);
        }
        assertSame(m,compiled.motif());
    }
    @Test void originalMidpointSentinelAndUnicodeLineBreakNamesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->options(HomerMotifs.Strands.BOTH,-12345678,false));
        assertThrows(IllegalArgumentException.class,()->new HomerMotifs.Motif(1,"A","bad\u2028name",1,null,List.of(),new double[][]{{1,0,0,0}}));
    }
    @Test void cumulativeModelMetadataAllocationIsBoundedBeforeHeaderFieldCreation()throws Exception{
        Path p=input(">A\t"+"large".repeat(1000)+"\t1\n1\t0\t0\t0\n");
        assertTrue(assertThrows(IOException.class,()->HomerMotifs.read(p,new HomerMotifs.Limits(2,2,2,100000,100000,10000,1024))).getMessage().contains("allocation budget"));
    }
}

