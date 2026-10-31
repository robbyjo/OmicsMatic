/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.jlinalg.singlecell.ScviGeneProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScviProjectionTest {
    @TempDir Path root;
    private int serial;
    private static final ScviMatrix.Limits DATA=new ScviMatrix.Limits(4_194_304,8_388_608,33_554_432,33_554_432,256,100,100,10000,2,4096);
    private static final ScviProjection.Limits LIMITS=new ScviProjection.Limits(134_217_728,1024,134_217_728,DATA);
    private ScviMatrix.Prepared source(List<String> genes,String body,ScviMatrix.Kind kind)throws Exception{
        Path dir=Files.createDirectory(root.resolve("source-"+serial++));Files.writeString(dir.resolve("genes"),String.join("\n",genes)+"\n");Files.writeString(dir.resolve("cells"),"cell_β\ncell_日本\n");Files.writeString(dir.resolve("matrix"),"%%MatrixMarket matrix coordinate real general\n"+genes.size()+" 2 "+body.lines().count()+"\n"+body);
        return ScviMatrix.prepare(new ScviMatrix.Request(text(dir.resolve("matrix")),text(dir.resolve("genes")),text(dir.resolve("cells")),kind,ScviMatrix.Precision.EXACT_FLOAT32,ScviMatrix.Duplicates.REJECT,DATA),root.resolve("input-"+serial++),()->{});
    }
    private static ScviText.Source text(Path p){return new ScviText.Source(p,ScviText.Encoding.PLAIN_UTF8);}
    private ScviText.Source genes(String text)throws Exception{Path p=root.resolve("ref-genes-"+serial++);Files.writeString(p,text);return text(p);}
    private Path out(){return root.resolve("projected-"+serial++);}
    private ScviMatrix.Prepared query()throws Exception{return source(List.of("gc","extra","ga"),"1 1 3\n2 1 4\n3 1 2\n1 2 1\n3 2 1\n",ScviMatrix.Kind.RAW_COUNTS);}
    @Test void fixedReferencePadsReordersAndNamesEveryRemovedGeneAndCellImpact()throws Exception{
        var original=query();var p=ScviProjection.query(genes("ga\ngb\ngc\n"),original,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{});
        assertEquals(List.of("ga","gb","gc"),p.query().genes());assertEquals(original.cells(),p.query().cells());assertTrue(Files.readString(p.query().directory().resolve("matrix.mtx")).endsWith("1 1 2\n3 1 3\n1 2 1\n3 2 1\n"));
        assertTrue(Files.readString(p.directory().resolve("query-gene-map.tsv")).contains("1\tgb\t-1\tPADDED_ZERO"));assertTrue(Files.readString(p.directory().resolve("query-removed-genes.tsv")).contains("1\textra"));assertTrue(Files.readString(p.directory().resolve("query-cell-impact.tsv")).contains("cell_β\t9.0\t5.0\t4.0"));p.verify(()->{});
        assertTrue(Files.readString(p.directory().resolve("projection.json")).contains("PREPARED_GENE_PROJECTION_ONLY"));assertFalse(Files.exists(p.directory().resolve("COMPLETED.json")));
    }
    @Test void sharedGenesAreAppliedToBothMatricesBeforeReferenceTraining()throws Exception{
        var ref=source(List.of("b","a","ref_only"),"1 1 1\n2 1 2\n3 1 4\n1 2 1\n2 2 1\n",ScviMatrix.Kind.RAW_COUNTS);
        var q=source(List.of("a","query_only","b"),"1 1 3\n2 1 5\n3 1 2\n1 2 1\n3 2 1\n",ScviMatrix.Kind.RAW_COUNTS);
        var p=ScviProjection.pair(ref,q,ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING,LIMITS,out(),()->{});
        assertEquals(List.of("b","a"),p.reference().genes());assertEquals(p.reference().genes(),p.query().genes());assertTrue(Files.readString(p.reference().directory().resolve("matrix.mtx")).endsWith("1 1 1\n2 1 2\n1 2 1\n2 2 1\n"));assertTrue(Files.readString(p.query().directory().resolve("matrix.mtx")).endsWith("1 1 2\n2 1 3\n1 2 1\n2 2 1\n"));p.verify(()->{});
    }
    @Test void fixedReferenceCannotBeShrunkAndExactPolicyDoesNotSilentlyReorder()throws Exception{
        var q=query();var ref=genes("ga\ngb\ngc\n");assertThrows(IOException.class,()->ScviProjection.query(ref,q,ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING,LIMITS,out(),()->{}));assertThrows(IOException.class,()->ScviProjection.query(ref,q,ScviGeneProjection.Policy.EXACT_ORDER,LIMITS,out(),()->{}));
    }
    @Test void droppingTheOnlyRawCountsFailsWithCellIdentityAndNoFiltering()throws Exception{
        var q=source(List.of("shared","extra"),"2 1 8\n1 2 3\n",ScviMatrix.Kind.RAW_COUNTS);Path target=out();var error=assertThrows(IOException.class,()->ScviProjection.query(genes("shared\n"),q,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,target,()->{}));assertTrue(error.getMessage().contains("cell_β"));assertFalse(Files.exists(target.resolve("projection.json")));assertFalse(Files.exists(target.resolve("query/admission.json")));q.verify(()->{});
    }
    @Test void sharedSelectionAlsoRejectsAnEmptyReferenceLibrary()throws Exception{
        var ref=source(List.of("shared","ref_only"),"2 1 8\n1 2 3\n",ScviMatrix.Kind.RAW_COUNTS);var q=source(List.of("shared"),"1 1 1\n1 2 1\n",ScviMatrix.Kind.RAW_COUNTS);var error=assertThrows(IOException.class,()->ScviProjection.pair(ref,q,ScviGeneProjection.Policy.SHARED_BEFORE_TRAINING,LIMITS,out(),()->{}));assertTrue(error.getMessage().contains("cell_β"));
    }
    @Test void signedExpressionMayRetainZeroRowsAfterAnExplicitProjection()throws Exception{
        var q=source(List.of("extra"),"1 1 -2\n1 2 3\n",ScviMatrix.Kind.SIGNED_EXPRESSION);var p=ScviProjection.query(genes("model_gene\n"),q,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{});assertEquals(0,p.query().summary().canonicalEntries());assertEquals(2,p.query().cells().size());p.verify(()->{});
    }
    @Test void parentAndReferenceIdentityChangesInvalidateProjection()throws Exception{
        var ref=genes("ga\ngb\ngc\n");var p=ScviProjection.query(ref,query(),ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{});Files.writeString(ref.path(),"ga\nchanged\ngc\n");assertThrows(IOException.class,()->p.verify(()->{}));
        var q=ScviProjection.query(genes("ga\ngb\ngc\n"),query(),ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{});Files.writeString(q.parents().get(0).directory().resolve("genes.tsv"),"changed\nextra\nga\n");assertThrows(IOException.class,()->q.verify(()->{}));
    }
    @Test void unexpectedOwnedMembersAndProjectionBytesAreDetected()throws Exception{
        var p=ScviProjection.query(genes("ga\ngb\ngc\n"),query(),ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{});Files.createDirectory(p.directory().resolve("extra"));assertThrows(IOException.class,()->p.verify(()->{}));
        var q=ScviProjection.query(genes("ga\ngb\ngc\n"),query(),ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{});Files.writeString(q.directory().resolve("query-gene-map.tsv"),"forged\n");assertThrows(IOException.class,()->q.verify(()->{}));
    }
    @Test void completeParentWorkspaceCapacityAndCancellationAreNotBypassed()throws Exception{
        var q=query();assertThrows(IOException.class,()->ScviProjection.query(genes("ga\ngb\ngc\n"),q,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,new ScviProjection.Limits(1,1024,134_217_728,DATA),out(),()->{}));
        assertThrows(CancellationException.class,()->ScviProjection.query(genes("ga\ngb\ngc\n"),q,ScviGeneProjection.Policy.PAD_MISSING_AND_DROP_EXTRA,LIMITS,out(),()->{throw new CancellationException("stop");}));
    }
    @Test void differentLikelihoodInputKindsNeverBecomeAnAutomaticConversion()throws Exception{
        var raw=source(List.of("a"),"1 1 1\n1 2 1\n",ScviMatrix.Kind.RAW_COUNTS);var signed=source(List.of("a"),"1 1 -1\n1 2 1\n",ScviMatrix.Kind.SIGNED_EXPRESSION);assertThrows(IOException.class,()->ScviProjection.pair(raw,signed,ScviGeneProjection.Policy.EXACT_ORDER,LIMITS,out(),()->{}));
    }
}
