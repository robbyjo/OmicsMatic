/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScviCovariatesTest {
    @TempDir Path root;
    private int serial;
    private ScviMatrix.Prepared matrix()throws Exception{
        Path input=Files.createDirectory(root.resolve("matrix-source-"+serial++));Files.writeString(input.resolve("matrix"),"%%MatrixMarket matrix coordinate integer general\n2 2 2\n1 1 1\n2 2 2\n");Files.writeString(input.resolve("genes"),"g1\ng2\n");Files.writeString(input.resolve("cells"),"cell_β\ncell_日本\n");
        var l=new ScviMatrix.Limits(1_048_576,4_194_304,33_554_432,33_554_432,128,100,100,10000,2,4096);
        return ScviMatrix.prepare(new ScviMatrix.Request(source(input.resolve("matrix")),source(input.resolve("genes")),source(input.resolve("cells")),ScviMatrix.Kind.RAW_COUNTS,ScviMatrix.Precision.EXACT_FLOAT32,ScviMatrix.Duplicates.REJECT,l),root.resolve("matrix-prepared-"+serial++),()->{});
    }
    private static ScviText.Source source(Path p){return new ScviText.Source(p,ScviText.Encoding.PLAIN_UTF8);}
    private static ScviCovariates.Spec spec(){return new ScviCovariates.Spec("batch",List.of("site"),List.of("age"),"label","Unlabeled",ScviCovariates.Labels.REFERENCE,List.of(),ScviMatrix.Precision.REVIEWED_FLOAT32_ROUNDING,32);}
    private ScviCovariates.Prepared prepare(String text,ScviCovariates.Spec spec)throws Exception{var matrix=matrix();Path input=root.resolve("metadata-"+serial++);Files.writeString(input,text);return ScviCovariates.prepare(matrix,source(input),spec,root.resolve("metadata-prepared-"+serial++),()->{});}
    private static String header(){return "cell_id\tbatch\tsite\tage\tlabel\tnote\n";}
    @Test void joinsAllCellsByExactIdAndRetainsUnusedValuesAndLiteralNaCategories()throws Exception{
        var p=prepare(header()+"cell_日本\tb2\tNA\t31\tUnlabeled\t\"quoted literal\"\ncell_β\tb1\ts1\t30.1\ttype_B\tkept value\n",spec());
        var rows=Files.readAllLines(p.directory().resolve("metadata.tsv"));assertTrue(rows.get(1).startsWith("cell_β\tb1\ts1\t"));assertTrue(rows.get(2).endsWith("\"quoted literal\""));assertEquals(Map.of("NA",1L,"s1",1L),p.observedCategoryCounts().get("site"));assertEquals(1,p.roundedCovariates());assertTrue(Files.readString(p.directory().resolve("covariate-conversion.tsv")).contains("cell_β\tage\t30.1"));p.verify(()->{});
        assertTrue(Files.readString(p.directory().resolve("admission.json")).contains("model_class_order_not_inferred"));assertFalse(Files.exists(p.directory().resolve("COMPLETED.json")));
    }
    @Test void missingUnknownAndDuplicateMetadataCellsNeverRepairThePopulation()throws Exception{
        for(String rows:List.of("cell_β\tb\ts\t1\tA\tx\n","cell_β\tb\ts\t1\tA\tx\ncell_β\tb\ts\t1\tA\tx\n","unknown\tb\ts\t1\tA\tx\ncell_日本\tb\ts\t1\tA\tx\n"))assertThrows(IOException.class,()->prepare(header()+rows,spec()));
    }
    @Test void noLabeledReferenceCellsFailButOneObservedClassIsRetained()throws Exception{
        assertThrows(IOException.class,()->prepare(header()+"cell_β\tb\ts\t1\tUnlabeled\tx\ncell_日本\tb\ts\t1\tUnlabeled\tx\n",spec()));
        var p=prepare(header()+"cell_β\tb\ts\t1\tA\tx\ncell_日本\tb\ts\t1\tA\tx\n",spec());assertEquals(Map.of("A",2L),p.observedCategoryCounts().get("label"));
    }
    @Test void explicitQueryLabelPoliciesDoNotInventOrReorderClasses()throws Exception{
        var query=new ScviCovariates.Spec("batch",List.of(),List.of(),"label","Unlabeled",ScviCovariates.Labels.UNLABELED_QUERY,List.of(),ScviMatrix.Precision.EXACT_FLOAT32,32);
        String unlabeled=header()+"cell_日本\tb\ts\t1\tUnlabeled\tx\ncell_β\tb\ts\t1\tUnlabeled\tx\n";assertEquals(2,prepare(unlabeled,query).matrix().cells().size());assertThrows(IOException.class,()->prepare(unlabeled.replaceFirst("Unlabeled","A"),query));
        var known=new ScviCovariates.Spec("batch",List.of(),List.of(),"label","Unlabeled",ScviCovariates.Labels.KNOWN_REFERENCE_QUERY,List.of("type_B","type_A"),ScviMatrix.Precision.EXACT_FLOAT32,32);
        assertEquals(2,prepare(unlabeled.replaceFirst("Unlabeled","type_B"),known).matrix().cells().size());assertThrows(IOException.class,()->prepare(unlabeled.replaceFirst("Unlabeled","novel"),known));
    }
    @Test void covariateRoundingRequiresItsOwnExplicitPolicy()throws Exception{
        var exact=new ScviCovariates.Spec("batch",List.of("site"),List.of("age"),"label","Unlabeled",ScviCovariates.Labels.REFERENCE,List.of(),ScviMatrix.Precision.EXACT_FLOAT32,32);
        assertThrows(IOException.class,()->prepare(header()+"cell_β\tb\ts\t0.1\tA\tx\ncell_日本\tb\ts\t1\tA\tx\n",exact));
        for(String value:List.of("NaN","Infinity","1e400","1e-400","1e39","NA",""))assertThrows(IOException.class,()->prepare(header()+"cell_β\tb\ts\t"+value+"\tA\tx\ncell_日本\tb\ts\t1\tA\tx\n",spec()));
    }
    @Test void blankCategoriesReservedColumnsAndSchemaDriftFailExplicitly()throws Exception{
        for(String text:List.of(header()+"cell_β\t\ts\t1\tA\tx\ncell_日本\tb\ts\t1\tA\tx\n",header().replace("site","batch"),header().replace("note","_scvi_batch"),header().replace("cell_id","barcode"),header()+"cell_β\tb\ts\t1\tA\n"))assertThrows(IOException.class,()->prepare(text,spec()));
        assertThrows(IllegalArgumentException.class,()->new ScviCovariates.Spec("batch",List.of("batch"),List.of(),null,null,ScviCovariates.Labels.NONE,List.of(),ScviMatrix.Precision.EXACT_FLOAT32,32));
    }
    @Test void noBatchOrLabelsAreInventedForUnsupervisedInputs()throws Exception{
        var noRoles=new ScviCovariates.Spec(null,List.of(),List.of(),null,null,ScviCovariates.Labels.NONE,List.of(),ScviMatrix.Precision.EXACT_FLOAT32,8);
        var p=prepare("cell_id\tfree_text\ncell_日本\tNA\ncell_β\tUnlabeled\n",noRoles);assertTrue(p.observedCategoryCounts().isEmpty());assertTrue(Files.readString(p.directory().resolve("metadata.tsv")).contains("cell_β\tUnlabeled"));
    }
    @Test void metadataAndMatrixChangesInvalidateTheCombinedPreparation()throws Exception{
        String input=header()+"cell_β\tb\ts\t1\tA\tx\ncell_日本\tb\ts\t1\tA\tx\n";var p=prepare(input,spec());Files.writeString(p.source().original(),input.replace("A\tx","B\tx"));assertThrows(IOException.class,()->p.verify(()->{}));
        var q=prepare(input,spec());Files.createDirectory(q.directory().resolve("extra"));assertThrows(IOException.class,()->q.verify(()->{}));
        var r=prepare(input,spec());Files.writeString(r.matrix().directory().resolve("genes.tsv"),"changed\ng2\n");assertThrows(IOException.class,()->r.verify(()->{}));
    }
}
