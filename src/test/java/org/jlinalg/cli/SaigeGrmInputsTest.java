package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaigeGrmInputsTest {
    @TempDir Path temp;
    private Map<String,Object> recipe() {
        return new LinkedHashMap<>(Map.of("version",1,"operation","sparse-grm","genotypes","cohort","genotype_coding","diploid-autosomal-plink-bed","assembly","explicit synthetic fixture",
            "parameters",new LinkedHashMap<>(Map.of("random_markers",1,"relatedness_cutoff",.125,"min_maf",.1,"max_missing",.5,"diagonal","observed","seed",1)),
            "resources",new LinkedHashMap<>(Map.of("threads",1,"memory_chunk_gib",.1))));
    }
    private Path write(Map<String,Object> recipe)throws Exception {Path path=temp.resolve("recipe.json");Files.writeString(path,RegenieRuntime.json(recipe));return path;}
    private SaigeGrmInputs.Spec spec()throws Exception {return SaigeGrmInputs.read(write(recipe()));}
    private void bed()throws Exception {
        Files.write(temp.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,0x78,0x2f});
        Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 A G\n2 v2 0 2 C T\n");
        Files.writeString(temp.resolve("cohort.fam"),"f a 0 0 1 -9\nf b 0 0 2 -9\nf c 0 0 0 -9\nf d 0 0 0 -9\n");
    }
    private Path work()throws Exception {return Files.createDirectory(temp.resolve("work"));}
    private Path matrix(String body)throws Exception {Path file=temp.resolve("matrix.mtx");Files.writeString(file,"%%MatrixMarket matrix coordinate real symmetric\n% original-style\n"+body);return file;}
    @Test void explicitRecipeResolvesRelativePathAndBounds()throws Exception {
        var spec=spec();assertEquals(temp.resolve("cohort"),spec.prefix());assertEquals(1,spec.randomMarkers());assertEquals(1_000_000,spec.limits().samples());assertEquals(64,spec.recipeSha256().length());
    }
    @Test void unknownAndMissingScientificChoicesAndAlternateSeedFail()throws Exception {
        var root=recipe();root.put("script","unreviewed.R");assertThrows(IOException.class,()->SaigeGrmInputs.read(write(root)));root.remove("script");
        var p=new LinkedHashMap<>(Map.of("random_markers",1,"relatedness_cutoff",.125,"min_maf",.1,"max_missing",.5,"diagonal","observed","seed",2));root.put("parameters",p);assertThrows(IOException.class,()->SaigeGrmInputs.read(write(root)));p.put("seed",1);p.remove("diagonal");assertThrows(IOException.class,()->SaigeGrmInputs.read(write(root)));
    }
    @Test void fractionalAndNonfiniteResourceAndInvalidUtf8Fail()throws Exception {
        var root=recipe();root.put("resources",Map.of("threads",1.5,"memory_chunk_gib",.1));assertThrows(IOException.class,()->SaigeGrmInputs.read(write(root)));root.put("resources",Map.of("threads",1,"memory_chunk_gib",257));assertThrows(IOException.class,()->SaigeGrmInputs.read(write(root)));Path file=temp.resolve("bad.json");Files.write(file,new byte[]{(byte)0xc3,0x28});assertThrows(IOException.class,()->SaigeGrmInputs.read(file));
    }
    @Test void snapshotsCompleteGenotypesAndSeparatelyCountsObservedQc()throws Exception {
        bed();var prepared=SaigeGrmInputs.prepare(spec(),work());assertEquals(List.of("a","b","c","d"),prepared.sampleIds());assertEquals(2,prepared.variants());assertEquals(2,prepared.exactEligibleMarkers());assertEquals(6,prepared.immutable().size());prepared.verify();assertArrayEquals(Files.readAllBytes(temp.resolve("cohort.bed")),Files.readAllBytes(Path.of(prepared.prefix()+".bed")));
    }
    @Test void changedSourceOrRecipeCannotBeAdmitted()throws Exception {
        bed();var prepared=SaigeGrmInputs.prepare(spec(),work());Files.writeString(temp.resolve("cohort.bim"),"changed");assertThrows(IOException.class,prepared::verify);Files.writeString(temp.resolve("recipe.json"),"{}");assertThrows(IOException.class,prepared::verify);
    }
    @Test void duplicateIidAcrossDifferentFamiliesFails()throws Exception {
        bed();Files.writeString(temp.resolve("cohort.fam"),"f a 0 0 1 -9\ng a 0 0 2 -9\nf c 0 0 0 -9\nf d 0 0 0 -9\n");assertThrows(IllegalArgumentException.class,()->SaigeGrmInputs.prepare(spec(),work()));
    }
    @Test void nonautosomalDuplicateVariantAndAllMissingMarkersFail()throws Exception {
        bed();Files.writeString(temp.resolve("cohort.bim"),"X v1 0 1 A G\n2 v2 0 2 C T\n");assertThrows(IOException.class,()->SaigeGrmInputs.prepare(spec(),work()));
        Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 A G\n2 v1 0 2 C T\n");assertThrows(IOException.class,()->SaigeGrmInputs.prepare(spec(),Files.createDirectory(temp.resolve("work2"))));
        Files.writeString(temp.resolve("cohort.bim"),"1 v1 0 1 A G\n2 v2 0 2 C T\n");Files.write(temp.resolve("cohort.bed"),new byte[]{0x6c,0x1b,1,0x55,0x2f});assertThrows(IOException.class,()->SaigeGrmInputs.prepare(spec(),Files.createDirectory(temp.resolve("work3"))));
    }
    @Test void declaredInputAndGenotypeWorkBoundsApply()throws Exception {
        bed();var root=recipe();root.put("resources",Map.of("threads",1,"memory_chunk_gib",.1,"max_genotype_cells",7));assertThrows(IOException.class,()->SaigeGrmInputs.prepare(SaigeGrmInputs.read(write(root)),work()));
    }
    @Test void originalCommandHasNoUserExecutableOrInferredDiagonal()throws Exception {
        bed();Path work=work();var prepared=SaigeGrmInputs.prepare(spec(),work);var command=SaigeGrmInputs.command(prepared,work);assertTrue(command.contains("--isDiagofKinSetAsOne=FALSE"));assertTrue(command.contains("--numRandomMarkerforSparseKin=1"));assertTrue(command.contains("--nThreads=1"));assertTrue(command.stream().noneMatch(x->x.contains("seed")));
    }
    @Test void rowMajorAndColumnMajorLowerTrianglesAreAudited()throws Exception {
        var row=SaigeGrmInputs.auditMatrix(matrix("3 3 5\n1 1 1\n2 1 .25\n2 2 1.2\n3 2 .3\n3 3 .8\n"),3,.125,10);assertEquals("row-major",row.get("ordering"));assertEquals(2L,row.get("off_diagonal_pairs"));
        var column=SaigeGrmInputs.auditMatrix(matrix("3 3 6\n1 1 1\n2 1 .25\n3 1 .4\n2 2 1.2\n3 2 .3\n3 3 .8\n"),3,.125,10);assertEquals("column-major",column.get("ordering"));
    }
    @Test void implicitZeroDiagonalsAndEmptySparseMatrixRemainExplicit()throws Exception {
        var partial=SaigeGrmInputs.auditMatrix(matrix("3 3 2\n1 1 0\n3 3 1\n"),3,.125,10);assertEquals(1L,partial.get("unstored_zero_diagonals"));assertEquals(2L,partial.get("total_zero_diagonals"));assertEquals(0.0,partial.get("diagonal_min"));
        var empty=SaigeGrmInputs.auditMatrix(matrix("3 3 0\n"),3,.125,10);assertEquals(3L,empty.get("total_zero_diagonals"));assertNull(empty.get("off_diagonal_min"));
    }
    @Test void exactOriginalFloatCutoffIsUsedWithoutArbitraryTolerance()throws Exception {
        double boundary=(double)(float).1;SaigeGrmInputs.auditMatrix(matrix("2 2 1\n2 1 "+boundary+"\n"),2,.1,10);assertThrows(IOException.class,()->SaigeGrmInputs.auditMatrix(matrix("2 2 1\n2 1 "+Math.nextDown(boundary)+"\n"),2,.1,10));
    }
    @Test void duplicatesMixedTrianglesAndOutOfOrderEntriesFail()throws Exception {
        for(String body:List.of("2 2 2\n1 1 1\n1 1 1\n","2 2 2\n1 2 .2\n2 1 .2\n","3 3 3\n3 2 .2\n1 1 1\n2 2 1\n"))assertThrows(IOException.class,()->SaigeGrmInputs.auditMatrix(matrix(body),body.startsWith("3")?3:2,.125,10));
    }
    @Test void malformedCountNonfiniteNegativeAndDimensionsFail()throws Exception {
        for(String body:List.of("2 2 2\n1 1 1\n","2 2 1\n1 1 NaN\n","2 2 1\n1 1 -1\n","3 3 0\n","2 2 1\n3 1 .2\n"))assertThrows(Exception.class,()->SaigeGrmInputs.auditMatrix(matrix(body),2,.125,10));
    }
    @Test void textLineLimitHandlesLfCrLfCrAndEof()throws Exception {
        try(var reader=new BufferedReader(new StringReader("a\r\nb\rc\nd"))){assertEquals("a",SaigeGrmInputs.line(reader,2));assertEquals("b",SaigeGrmInputs.line(reader,2));assertEquals("c",SaigeGrmInputs.line(reader,2));assertEquals("d",SaigeGrmInputs.line(reader,2));assertNull(SaigeGrmInputs.line(reader,2));}
        assertThrows(IOException.class,()->SaigeGrmInputs.line(new BufferedReader(new StringReader("abc")),2));
    }
}
