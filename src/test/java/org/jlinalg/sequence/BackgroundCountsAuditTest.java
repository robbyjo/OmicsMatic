package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackgroundCountsAuditTest {
    @TempDir Path dir;
    private static final List<String> GENES=List.of("G1","G2"),CELLS=List.of("A","B","ZERO");
    private Path matrix(String name,int genes,int cells,String... rows)throws IOException{Path p=dir.resolve(name);Files.writeString(p,"%%MatrixMarket matrix coordinate integer general\n"+genes+" "+cells+" "+rows.length+"\n"+String.join("\n",rows)+(rows.length==0?"":"\n"));return p;}
    private List<BackgroundCountsAudit.Probability> probabilities(){return List.of(new BackgroundCountsAudit.Probability("A",.9,5/(13+.001)),new BackgroundCountsAudit.Probability("B",.1,2/(2+.001)));}
    private BackgroundCountsAudit.Result audit(String[] raw,String[] corrected,String[] filtered,List<BackgroundCountsAudit.Probability> probabilities,List<String> selected,List<String> csv)throws IOException{return BackgroundCountsAudit.audit(matrix("raw",2,3,raw),matrix("out",2,3,corrected),matrix("filtered",2,selected.size(),filtered),GENES,CELLS,probabilities,selected,csv);}
    private BackgroundCountsAudit.Result base()throws IOException{return audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"1 1 6","2 1 2"},new String[]{"1 1 6","2 1 2"},probabilities(),List.of("A"),List.of("A"));}
    @Test void distinguishesAllRawAndCalledPopulations()throws Exception{var r=base();assertEquals(15,r.raw());assertEquals(8,r.corrected());assertEquals(13,r.calledRaw());assertEquals(8,r.calledCorrected());assertEquals(2,r.analyzed());assertEquals(1,r.called());}
    @Test void retainsZeroAndUnanalyzedDropletsWithMissingProbability()throws Exception{var c=base().cells().get(2);assertEquals("ZERO",c.id());assertEquals(0,c.raw());assertFalse(c.analyzed());assertFalse(c.called());assertTrue(Double.isNaN(c.probability()));assertEquals(0,c.removedFraction());}
    @Test void preservesFeatureMarginalsAndDetectedFeatures()throws Exception{var r=base();assertEquals(12,r.features().get(0).raw());assertEquals(6,r.features().get(0).corrected());assertEquals(3,r.features().get(1).raw());assertEquals(2,r.cells().get(0).rawFeatures());assertEquals(2,r.cells().get(0).correctedFeatures());}
    @Test void filteredOrderMayDifferFromFullBarcodeOrder()throws Exception{var p=List.of(new BackgroundCountsAudit.Probability("A",.9,5/(13+.001)),new BackgroundCountsAudit.Probability("B",.8,1/(2+.001)));var r=audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"1 1 6","2 1 2","1 2 1"},new String[]{"1 1 1","1 2 6","2 2 2"},p,List.of("B","A"),List.of("A","B"));assertEquals(List.of("B","A"),r.filteredOrder());assertEquals(15,r.calledRaw());assertEquals(9,r.calledCorrected());}
    @Test void detectsFilteredContentsChangedDespiteEqualTotals(){assertThrows(IOException.class,()->audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"1 1 6","2 1 2"},new String[]{"1 1 5","2 1 3"},probabilities(),List.of("A"),List.of("A")));}
    @Test void rejectsCorrectedCountsGreaterThanOriginal(){assertThrows(IOException.class,()->audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"1 1 11"},new String[]{"1 1 11"},probabilities(),List.of("A"),List.of("A")));}
    @Test void rejectsNewCoordinates(){assertThrows(IOException.class,()->audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"2 2 1"},new String[]{},probabilities(),List.of("A"),List.of("A")));}
    @Test void rejectsNewCoordinatesAfterOriginalEnds(){assertThrows(IOException.class,()->audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"1 3 1"},new String[]{},probabilities(),List.of("A"),List.of("A")));}
    @Test void rejectsDuplicateOrNoncanonicalCoordinates(){assertThrows(IOException.class,()->audit(new String[]{"1 1 5","1 1 5","2 1 3","1 2 2"},new String[]{},new String[]{},List.of(),List.of(),List.of()));}
    @Test void rejectsNegativeOrFractionalCounts(){for(String value:List.of("-1","1.5","NaN"))assertThrows(IOException.class,()->audit(new String[]{"1 1 "+value},new String[]{},new String[]{},List.of(),List.of(),List.of()));}
    @Test void halfProbabilityIsNotAPublishedCellCall()throws Exception{var r=audit(new String[]{"1 1 10"},new String[]{},new String[]{},List.of(new BackgroundCountsAudit.Probability("A",.5,10/(10+.001))),List.of(),List.of());assertEquals(0,r.called());assertTrue(r.cells().get(0).analyzed());assertEquals(.5,r.cells().get(0).probability());}
    @Test void rejectsCsvCallsNotMatchingStrictThreshold(){assertThrows(IOException.class,()->audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{},new String[]{},probabilities(),List.of("A"),List.of("B")));}
    @Test void rejectsDuplicateModelIdsAndUnknownIds(){for(String id:List.of("A","MISSING")){var p=new ArrayList<>(probabilities());p.add(new BackgroundCountsAudit.Probability(id,.2,0));assertThrows(IOException.class,()->audit(new String[]{"1 1 10"},new String[]{},new String[]{},p,List.of(),List.of()));}}
    @Test void rejectsNonfiniteAndOutOfRangeProbability(){for(double p:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.01,1.01})assertThrows(IOException.class,()->audit(new String[]{},new String[]{},new String[]{},List.of(new BackgroundCountsAudit.Probability("A",p,0)),List.of(),List.of()));}
    @Test void detectsOriginalCsrMutationDiagnostic(){var p=List.of(new BackgroundCountsAudit.Probability("A",.9,5/(13+.001)),new BackgroundCountsAudit.Probability("B",.1,0));var failure=assertThrows(IOException.class,()->audit(new String[]{"1 1 10","2 1 3","1 2 2"},new String[]{"1 1 6","2 1 2"},new String[]{"1 1 6","2 1 2"},p,List.of("A"),List.of("A")));assertTrue(failure.getMessage().contains("immutable original"));}
    @Test void rejectsDuplicateFilteredAndCsvIdentifiers(){assertThrows(IOException.class,()->audit(new String[]{},new String[]{},new String[]{},List.of(),List.of("A","A"),List.of()));assertThrows(IOException.class,()->audit(new String[]{},new String[]{},new String[]{},List.of(),List.of(),List.of("A","A")));}
    @Test void auditsLargeExactIntegersWithoutDoubleSummaryRounding()throws Exception{long n=9007199254740991L;var p=List.of(new BackgroundCountsAudit.Probability("A",.9,1/(n+.001)));var r=audit(new String[]{"1 1 "+n},new String[]{"1 1 "+(n-1)},new String[]{"1 1 "+(n-1)},p,List.of("A"),List.of("A"));assertEquals(1,r.raw()-r.corrected());}
    @Test void countOverflowNeverProducesAWrappedSummary()throws Exception{var cells=new ArrayList<String>();var rows=new ArrayList<String>();for(int i=0;i<1025;i++){cells.add("C"+i);rows.add("1 "+(i+1)+" 9007199254740991");}assertThrows(IOException.class,()->BackgroundCountsAudit.audit(matrix("raw",1,cells.size(),rows.toArray(String[]::new)),matrix("out",1,cells.size()),matrix("filtered",1,0),List.of("G"),cells,List.of(),List.of(),List.of()));}
    @Test void detectsTruncatedCanonicalMatrix()throws Exception{Path raw=matrix("raw",2,3,"1 1 1");Files.writeString(raw,Files.readString(raw).replace("2 3 1","2 3 2"));assertThrows(IOException.class,()->BackgroundCountsAudit.audit(raw,matrix("out",2,3),matrix("filtered",2,0),GENES,CELLS,List.of(),List.of(),List.of()));}
    @Test void immutableResultLedgersDoNotExposeMutableState()throws Exception{var r=base();assertThrows(UnsupportedOperationException.class,()->r.cells().clear());assertThrows(UnsupportedOperationException.class,()->r.features().clear());assertThrows(UnsupportedOperationException.class,()->r.filteredCountSha256().clear());assertEquals(64,r.filteredCountSha256().get(0).length());}
}
