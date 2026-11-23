package org.jlinalg.cli;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.enrichment.PyScenicAucell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Streaming composition must equal the qualified full-matrix kernel, including flags. */
class ScenicWorkflowActivityTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path temporary;
    private static final List<String> CELLS=List.of("z","\ud800\udc00","\ue000","NA");
    private static final List<String> GENES=List.of("a","b","c","d","e");
    private static final double[][] EXPRESSION={{5,4,3,2,1},{1,2,3,4,5},{0,-0.,Double.NaN,Double.POSITIVE_INFINITY,-2},{1,1,1,1,1}};
    private Map<String,Object> request(boolean normalized,boolean noWeights,PyScenicAucell.Layout layout)throws Exception {
        var raw=ScenicWorkflowContractsTest.raw();raw.put("options",map("seed",17,"activity",map("auc_threshold",.8,"normalize",normalized,"no_weights",noWeights,"layout",layout.name())));
        raw.put("limits",map("maximum_activity_batch_cells",2));
        return ScenicWorkflowContractsTest.valid(raw);
    }
    private Path prepare(String name,List<PyScenicAucell.Signature> signatures)throws Exception {
        Path root=temporary.resolve(name);Files.createDirectory(root);Files.createDirectories(root.resolve("inputs"));Files.createDirectories(root.resolve("worker/out"));
        var matrix=new StringBuilder("cell\t"+String.join("\t",GENES)+"\n");for(int row=0;row<CELLS.size();row++){matrix.append(CELLS.get(row));for(double value:EXPRESSION[row])matrix.append('\t').append(Double.toString(value));matrix.append('\n');}
        Files.writeString(root.resolve("inputs/activity.tsv"),matrix,StandardCharsets.UTF_8);
        var encoded=new ArrayList<Object>();for(var signature:signatures){var weights=new ArrayList<Object>();for(var e:signature.geneWeights().entrySet())weights.add(List.of(e.getKey(),ScenicWorkflowActivity.tagged(e.getValue())));encoded.add(map("name",signature.name(),"gene_weights",weights));}
        var records=new StringBuilder();for(Object value:encoded)records.append(new String(ScenicWorkflowFiles.json(value),StandardCharsets.UTF_8));Files.writeString(root.resolve("worker/out/regulons.jsonl"),records,StandardCharsets.UTF_8);return root;
    }
    private void check(boolean normalized,boolean noWeights,PyScenicAucell.Layout layout,List<PyScenicAucell.Signature> signatures,String name)throws Exception {
        Path root=prepare(name,signatures);var request=request(normalized,noWeights,layout);var summary=ScenicWorkflowActivity.compute(root,request,Long.MAX_VALUE);
        var options=new PyScenicAucell.Options(.8,noWeights,normalized,17,layout,PyScenicAucell.Limits.defaults());
        var expected=PyScenicAucell.scoreExpression(CELLS,GENES,EXPRESSION,signatures,options);var raw=expected.raw();var scores=expected.scores();
        var lines=Files.readAllLines(root.resolve("activity/scores.tsv"),StandardCharsets.UTF_8);assertEquals(CELLS.size()+1,lines.size());
        assertEquals("cell\t"+String.join("\t",expected.regulons()),lines.get(0));byte[] flags=Files.readAllBytes(root.resolve("activity/eligibility.bin"));long bad=0;
        for(int row=0;row<CELLS.size();row++){
            String[] values=lines.get(row+1).split("\t",-1);assertEquals(expected.cells().get(row),values[0]);
            for(int col=0;col<expected.regulons().size();col++){
                assertEquals(scores[row][col],Double.parseDouble(values[col+1]));boolean eligible=expected.eligible(row,col);assertEquals(eligible?1:0,flags[row*expected.regulons().size()+col]);if(!eligible)bad++;
            }
        }assertEquals(bad,number(summary,"ineligible_fields"));
        try(var input=new DataInputStream(Files.newInputStream(root.resolve("activity/raw-scores.bin")))){
            for(String cell:CELLS){int row=expected.cells().indexOf(cell);for(int col=0;col<expected.regulons().size();col++)assertEquals(raw[row][col],input.readDouble());}assertEquals(-1,input.read());
        }
        var ranked=PyScenicAucell.rank(CELLS,GENES,EXPRESSION,17,PyScenicAucell.Limits.defaults());
        try(var input=new DataInputStream(Files.newInputStream(root.resolve("activity/ranks.bin")))){for(int[] row:ranked.ranks())for(int rank:row)assertEquals(rank,input.readInt());assertEquals(-1,input.read());}
        var actualDiagnostics=list(ScenicWorkflowFiles.read(root.resolve("activity/diagnostics.json")).get("regulons"));
        for(int col=0;col<expected.regulons().size();col++)assertEquals(expected.diagnostics().get(col).status().name(),object(actualDiagnostics.get(col)).get("status"));
    }
    @Test void streamedWeightedRowsAndUnicodeAxesMatchFullMatrixAcrossLayouts()throws Exception {
        var signatures=List.of(new PyScenicAucell.Signature("B",new LinkedHashMap<>(Map.of("a",2.,"c",1.))),new PyScenicAucell.Signature("A",new LinkedHashMap<>(Map.of("b",.5,"e",3.))));
        for(var layout:PyScenicAucell.Layout.values())for(boolean normalize:List.of(false,true))check(normalize,false,layout,signatures,layout.name()+normalize);
    }
    @Test void sourceCoverageZerosAndNonfiniteNormalizationKeepAllAdmissionFlags()throws Exception {
        var signatures=List.of(new PyScenicAucell.Signature("missing",Map.of("absent",Double.NaN)),new PyScenicAucell.Signature("covered",Map.of("a",1.,"e",1.)));
        for(boolean normalize:List.of(false,true))check(normalize,false,PyScenicAucell.Layout.SERIAL_UNSTACK,signatures,"coverage"+normalize);
    }
    @Test void ignoredNonfiniteWeightsAndNegativeWeightDiagnosticsMatchNativeKernel()throws Exception {
        check(true,true,PyScenicAucell.Layout.SERIAL_UNSTACK,List.of(new PyScenicAucell.Signature("ignored",Map.of("a",Double.POSITIVE_INFINITY,"b",1.))),"ignored");
        var weights=new LinkedHashMap<String,Double>();weights.put("a",2.);weights.put("b",-.5);check(false,false,PyScenicAucell.Layout.MULTIPROCESS_INPUT_ORDER,List.of(new PyScenicAucell.Signature("negative",weights)),"negative");
    }
    @Test void absenceOfRegulonsIsExplicitAndCannotInventZeroActivity()throws Exception {
        Path root=prepare("empty",List.of());var result=ScenicWorkflowActivity.compute(root,request(false,false,PyScenicAucell.Layout.SERIAL_UNSTACK),Long.MAX_VALUE);
        assertEquals("NO_SCORABLE_REGULONS",result.get("outcome"));assertEquals(false,result.get("activity_computed"));assertFalse(Files.exists(root.resolve("activity/scores.tsv")));assertFalse(Files.exists(root.resolve("activity/eligibility.bin")));
    }
    @Test void nativeDeadlineInterruptsAndSettlesOnlyItsOwnedCalculation()throws Exception {
        var stopped=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(1);long start=System.nanoTime();
        try(var owner=new ScenicWorkflowActivity.MathOwner()){
            IOException error=assertThrows(IOException.class,()->owner.execute(()->{entered.countDown();try{Thread.sleep(60_000);}finally{stopped.countDown();}return null;},start+java.util.concurrent.TimeUnit.SECONDS.toNanos(1)));
            assertTrue(error.getMessage().contains("deadline expired"));assertTrue(entered.await(1,java.util.concurrent.TimeUnit.SECONDS));assertTrue(stopped.await(2,java.util.concurrent.TimeUnit.SECONDS));
        }assertTrue(System.nanoTime()-start<java.util.concurrent.TimeUnit.SECONDS.toNanos(8));assertFalse(Thread.currentThread().isInterrupted());
    }
}
