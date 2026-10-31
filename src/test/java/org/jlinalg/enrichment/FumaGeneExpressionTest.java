package org.jlinalg.enrichment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FumaGeneExpressionTest {
    private static final FumaGeneExpression.Limits LIMITS=new FumaGeneExpression.Limits(100,10,1000);
    private static final List<FumaGeneExpression.Gene> GENES=List.of(new FumaGeneExpression.Gene("E1","First"),new FumaGeneExpression.Gene("E2","Second"),new FumaGeneExpression.Gene("E3","Third"));
    private static FumaGeneExpression.InputRow row(String gene,double a,double b){return new FumaGeneExpression.InputRow(gene,List.of(a,b));}
    private static FumaGeneExpression.Matrix matrix(){return new FumaGeneExpression.Matrix(List.of("Z","A"),List.of(row("E3",-2.5,0),row("E2",12,1.5),row("E1",0,9)));}
    private static FumaGeneExpression.Result select(List<String> query,FumaGeneExpression.Matrix left,FumaGeneExpression.Matrix right){return FumaGeneExpression.select(query,GENES,left,right,LIMITS,()->{});}
    @Test void exactResourceOrderLabelsAndValuesRemainUnchanged(){var r=select(List.of("E1","E3"),matrix(),matrix());assertTrue(r.sourceGateOpened());assertEquals(List.of("E3","E1"),r.log2Rows().stream().map(FumaGeneExpression.Row::gene).toList());assertEquals(List.of("Z","A"),r.log2Labels());assertEquals(List.of(-2.5,0.0),r.log2Rows().get(0).values());assertTrue(r.sameOrderedPopulation());}
    @Test void normalizedResourceIsSelectedIndependently(){var right=new FumaGeneExpression.Matrix(List.of("A","Z"),List.of(row("E1",99,-4),row("E3",6,3)));var r=select(List.of("E1","E3"),matrix(),right);assertEquals(List.of("E1","E3"),r.normRows().stream().map(FumaGeneExpression.Row::gene).toList());assertEquals(List.of(99.0,-4.0),r.normRows().get(0).values());assertFalse(r.sameOrderedPopulation());}
    @Test void firstAnnotationSymbolWinsWithoutRowDuplication(){var genes=new ArrayList<>(GENES);genes.add(0,new FumaGeneExpression.Gene("E3","Alias"));var r=FumaGeneExpression.select(List.of("E1","E3"),genes,matrix(),matrix(),LIMITS,()->{});assertEquals("Alias",r.log2Rows().get(0).symbol());assertEquals(2,r.log2Rows().size());}
    @Test void sourceGateUsesQueryEntryCountAndCanClose(){var r=select(List.of("E1","Absent"),matrix(),matrix());assertFalse(r.sourceGateOpened());assertEquals(1,r.queryEntriesInLog2());assertTrue(r.log2Rows().isEmpty());assertTrue(r.normRows().isEmpty());}
    @Test void queryDuplicatesCannotTurnOneResourceRowIntoAValidRMatrix(){assertThrows(IllegalArgumentException.class,()->select(List.of("E1","E1"),matrix(),matrix()));var r=select(List.of("E1","E1","E3"),matrix(),matrix());assertEquals(3,r.queryEntriesInLog2());assertEquals(2,r.log2Rows().size());}
    @Test void emptyNormSubsetRetainsHeadersButSingleRowIsRejected(){var empty=new FumaGeneExpression.Matrix(List.of("Z","A"),List.of(row("Other",1,2)));assertTrue(select(List.of("E1","E3"),matrix(),empty).normRows().isEmpty());assertThrows(IllegalArgumentException.class,()->select(List.of("E1","E3"),matrix(),new FumaGeneExpression.Matrix(List.of("Z","A"),List.of(row("E1",1,2)))));}
    @Test void invalidMatricesFailWithoutImputation(){assertThrows(IllegalArgumentException.class,()->row("E1",Double.NaN,0));assertThrows(IllegalArgumentException.class,()->new FumaGeneExpression.Matrix(List.of("A"),List.of()));assertThrows(IllegalArgumentException.class,()->new FumaGeneExpression.Matrix(List.of("A","A"),List.of()));assertThrows(IllegalArgumentException.class,()->new FumaGeneExpression.Matrix(List.of("A","B"),List.of(row("E1",1,2),row("E1",2,3))));assertThrows(IllegalArgumentException.class,()->new FumaGeneExpression.Matrix(List.of("A","B"),List.of(new FumaGeneExpression.InputRow("E1",List.of(1.0)))));}
    @Test void missingSelectedAnnotationFailsAndUnselectedMissingAnnotationDoesNot(){assertThrows(IllegalArgumentException.class,()->FumaGeneExpression.select(List.of("E1","E3"),GENES.subList(0,2),matrix(),matrix(),LIMITS,()->{}));assertEquals(2,FumaGeneExpression.select(List.of("E1","E2"),GENES.subList(0,2),matrix(),matrix(),LIMITS,()->{}).log2Rows().size());}
    @Test void boundsCoverCompleteInputsAndCombinedValues(){assertThrows(IllegalArgumentException.class,()->FumaGeneExpression.select(List.of("E1","E3"),GENES,matrix(),matrix(),new FumaGeneExpression.Limits(100,10,11),()->{}));var calls=new AtomicInteger();assertThrows(IllegalStateException.class,()->FumaGeneExpression.select(List.of("E1","E3"),GENES,matrix(),matrix(),LIMITS,()->{if(calls.incrementAndGet()==3)throw new IllegalStateException("cancelled");}));}
    @Test void inputAndResultAreImmutable(){var v=new ArrayList<>(List.of(1.0,2.0));var row=new FumaGeneExpression.InputRow("E1",v);v.set(0,9.0);assertEquals(1,row.values().get(0));assertThrows(UnsupportedOperationException.class,()->row.values().add(3.0));var r=select(List.of("E1","E3"),matrix(),matrix());assertThrows(UnsupportedOperationException.class,()->r.log2Rows().clear());}
}
