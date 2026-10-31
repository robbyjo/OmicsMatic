/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jlinalg.genetics.FavorCsvJoin;
import org.jlinalg.genetics.FavorCsvReader;
import org.jlinalg.genetics.FavorVcfReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorAnnotatorWorkflowTest {
    @TempDir Path directory;
    private static final String HASH="a".repeat(64);
    private static final FavorCsvReader.Limits CSV=FavorCsvReader.Limits.defaults();
    private static final FavorCsvReader.BlankRecords BLANKS=FavorCsvReader.BlankRecords.IGNORE;
    private Path text(String name,String content)throws IOException {return Files.writeString(directory.resolve(name),content);}
    private static FavorAnnotatorWorkflow.Input input(String name){return new FavorAnnotatorWorkflow.Input(Path.of(name),HASH);}
    private static FavorAnnotatorWorkflow.Reference reference(){return new FavorAnnotatorWorkflow.Reference("resource","GRCh38",input("split.csv"),List.of(input("chr22_1.csv"),input("chr22_2.csv")));}
    private static FavorCsvJoin.Result joined()throws IOException {
        var q=List.of(new FavorCsvJoin.Variant(22,100,"A","G"),new FavorCsvJoin.Variant(22,600,"C","T"));
        var r=new FavorCsvJoin.Reference("resource","GRCh38",List.of("variant_vcf","label"),List.of(new FavorCsvJoin.Shard("first",22,1,500,c->c.accept(List.of("22-100-A-G","Gene,α\nline"))),new FavorCsvJoin.Shard("second",22,501,1000,c->c.accept(List.of("22-600-C-T","NA")))));
        return FavorCsvJoin.join(q,r,new FavorCsvJoin.Options("GRCh38",22,FavorCsvJoin.Limits.defaults()),()->{});
    }
    @Test void exactRawJoinedCellsPreserveQuotesUnicodeLineBreaksAndLiteralNa()throws Exception {
        Path p=text("joined.csv","VarInfo,variant_vcf,label\n22-100-A-G,22-100-A-G,\"Gene,α\nline\"\n22-600-C-T,22-600-C-T,NA\n");FavorAnnotatorWorkflow.compareJoined(p,joined(),List.of("VarInfo","variant_vcf","label"),CSV,BLANKS,()->{});
    }
    @Test void reorderedRowsCannotBeRepairedBeforeAttachment()throws Exception {
        Path p=text("reordered.csv","VarInfo,variant_vcf,label\n22-600-C-T,22-600-C-T,NA\n22-100-A-G,22-100-A-G,\"Gene,α\nline\"\n");assertThrows(IOException.class,()->FavorAnnotatorWorkflow.compareJoined(p,joined(),List.of("VarInfo","variant_vcf","label"),CSV,BLANKS,()->{}));
    }
    @Test void missingAndExtraSourceRowsAreRejected()throws Exception {
        String correct="VarInfo,variant_vcf,label\n22-100-A-G,22-100-A-G,\"Gene,α\nline\"\n22-600-C-T,22-600-C-T,NA\n";
        for(String value:List.of(correct.substring(0,correct.lastIndexOf("22-600")),correct+"22-600-C-T,22-600-C-T,NA\n")){Path p=text("count-"+java.util.UUID.randomUUID()+".csv",value);assertThrows(IOException.class,()->FavorAnnotatorWorkflow.compareJoined(p,joined(),List.of("VarInfo","variant_vcf","label"),CSV,BLANKS,()->{}));}
    }
    @Test void headerChangesAndCellNormalizationAreRejected()throws Exception {
        for(String value:List.of("VarInfo,label,variant_vcf\n","VarInfo,variant_vcf,label\n22-100-A-G,22-100-A-G,\"Gene,α\nline\"\n22-600-C-T,22-600-C-T,\n")){Path p=text("changed-"+java.util.UUID.randomUUID()+".csv",value);assertThrows(IOException.class,()->FavorAnnotatorWorkflow.compareJoined(p,joined(),List.of("VarInfo","variant_vcf","label"),CSV,BLANKS,()->{}));}
    }
    @Test void rawComparisonMustFinishTheCompleteBoundedScan()throws Exception {
        Path p=text("bounded.csv","VarInfo,variant_vcf,label\n22-100-A-G,22-100-A-G,\"Gene,α\nline\"\n22-600-C-T,22-600-C-T,NA\n");var cap=new FavorCsvReader.Limits(10000,1,100,10,100,1000);assertThrows(IOException.class,()->FavorAnnotatorWorkflow.compareJoined(p,joined(),List.of("VarInfo","variant_vcf","label"),cap,BLANKS,()->{}));assertThrows(IllegalStateException.class,()->FavorAnnotatorWorkflow.compareJoined(p,joined(),List.of("VarInfo","variant_vcf","label"),CSV,BLANKS,()->{throw new IllegalStateException("cancelled");}));
    }
    @Test void originalSplitOrderAndUnselectedChromosomesRemainExplicit()throws Exception {
        Path p=text("splits.csv","Chr,Start_Pos,End_Pos\n1,1,100\n22,501,1000\n22,1,500\n");assertEquals(2,FavorAnnotatorWorkflow.splits(p,22,CSV,BLANKS,()->{}).size());
    }
    @Test void singleShardAndMissingSelectedChromosomesAreRefused()throws Exception {
        for(String content:List.of("Chr,Start_Pos,End_Pos\n22,1,100\n","Chr,Start_Pos,End_Pos\n1,1,100\n1,101,200\n")){Path p=text("single-"+java.util.UUID.randomUUID()+".csv",content);assertThrows(IOException.class,()->FavorAnnotatorWorkflow.splits(p,22,CSV,BLANKS,()->{}));}
    }
    @Test void splitColumnsCannotBeReorderedOrInvented()throws Exception {
        for(String header:List.of("Chr,End_Pos,Start_Pos","Chr,Start_Pos,End_Pos,extra","chr,Start_Pos,End_Pos")){Path p=text("header-"+java.util.UUID.randomUUID()+".csv",header+"\n");assertThrows(IOException.class,()->FavorAnnotatorWorkflow.splits(p,22,CSV,BLANKS,()->{}));}
    }
    @Test void splitCoordinatesAreExactPositiveSigned32AndClosed()throws Exception {
        for(String value:List.of("0","-1","1.5","01","1e3","2147483648"," 1","NA")){Path p=text("coordinate-"+java.util.UUID.randomUUID()+".csv","Chr,Start_Pos,End_Pos\n22,"+value+",500\n22,501,1000\n");assertThrows(IOException.class,()->FavorAnnotatorWorkflow.splits(p,22,CSV,BLANKS,()->{}));}Path inverted=text("inverted.csv","Chr,Start_Pos,End_Pos\n22,100,1\n22,501,1000\n");assertThrows(IOException.class,()->FavorAnnotatorWorkflow.splits(inverted,22,CSV,BLANKS,()->{}));Path chr=text("chr.csv","Chr,Start_Pos,End_Pos\n23,1,100\n22,101,200\n22,201,300\n");assertThrows(IOException.class,()->FavorAnnotatorWorkflow.splits(chr,22,CSV,BLANKS,()->{}));
    }
    @Test void explicitBlankPolicyAndCancellationApplyToAllSplitRows()throws Exception {
        Path p=text("blank.csv","Chr,Start_Pos,End_Pos\n\n22,1,500\n22,501,1000\n");assertEquals(2,FavorAnnotatorWorkflow.splits(p,22,CSV,BLANKS,()->{}).size());assertThrows(IOException.class,()->FavorAnnotatorWorkflow.splits(p,22,CSV,FavorCsvReader.BlankRecords.ERROR,()->{}));assertThrows(IllegalStateException.class,()->FavorAnnotatorWorkflow.splits(p,22,CSV,BLANKS,()->{throw new IllegalStateException("cancelled");}));
    }
    @Test void protectedPinsAndReferenceIdentitiesCannotBeImplicit() {
        for(String hash:List.of("","A".repeat(64),"a".repeat(63)))assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Input(Path.of("input"),hash));for(String identity:List.of("","has whitespace","../path","label\n"))assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Reference(identity,"GRCh38",input("split"),List.of(input("a"),input("b"))));assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Reference("resource","GRCh38",input("split"),List.of(input("a"))));
    }
    @Test void queryAndResourceBuildsMustAgreeBeforeExecution() {
        var runtime=new FavorAnnotatorWorkflow.RuntimeInput(input("inventory"),Path.of("source"));assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Request(runtime,input("input.vcf"),"GRCh37",22,FavorVcfReader.Mode.DIPLOID_GT,FavorVcfReader.Chromosomes.EXACT,BLANKS,reference(),Path.of("output"),FavorAnnotatorWorkflow.Limits.defaults()));assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Request(runtime,input("input.vcf"),"GRCh38",23,FavorVcfReader.Mode.DIPLOID_GT,FavorVcfReader.Chromosomes.EXACT,BLANKS,reference(),Path.of("output"),FavorAnnotatorWorkflow.Limits.defaults()));
    }
    @Test void typedReferencesCopyTheirOrderedShardLists() {var reference=reference();assertThrows(UnsupportedOperationException.class,()->reference.shards().clear());assertEquals("chr22_1.csv",reference.shards().get(0).path().toString());}
    @Test void unsupportedOrExistingOutputPathsCannotOverwriteAnything()throws Exception {
        Path existing=Files.createDirectory(directory.resolve("retained"));Path marker=Files.writeString(existing.resolve("marker"),"retain");assertThrows(IOException.class,()->FavorAnnotatorWorkflow.newOutput(existing));assertEquals("retain",Files.readString(marker));assertThrows(IOException.class,()->FavorAnnotatorWorkflow.newOutput(directory.resolve("has whitespace")));assertThrows(IOException.class,()->FavorAnnotatorWorkflow.newOutput(directory.resolve("missing-parent/out")));
        Path fresh=directory.resolve("new-ascii");if(System.getProperty("os.name").equals("Linux")){assertEquals(fresh,FavorAnnotatorWorkflow.newOutput(fresh));assertTrue(Files.isDirectory(fresh));}else {assertThrows(IOException.class,()->FavorAnnotatorWorkflow.newOutput(fresh));assertFalse(Files.exists(fresh));}
    }
    @Test void executionCapacitiesAreExplicitAndCannotBeZeroOrUnbounded() {
        var l=FavorAnnotatorWorkflow.Limits.defaults();assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Limits(l.runtime(),l.vcf(),l.csv(),l.join(),l.gds(),l.scalar(),0,l.xsvTimeoutMillis(),l.workBytes(),l.upstreamRssBytes(),l.chunkRows()));assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Limits(l.runtime(),l.vcf(),l.csv(),l.join(),l.gds(),l.scalar(),1,0,l.workBytes(),l.upstreamRssBytes(),1));assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Limits(l.runtime(),l.vcf(),l.csv(),l.join(),l.gds(),l.scalar(),1,1,l.workBytes(),0,1));assertThrows(IllegalArgumentException.class,()->new FavorAnnotatorWorkflow.Limits(l.runtime(),l.vcf(),l.csv(),l.join(),l.gds(),l.scalar(),1,1,l.workBytes(),l.upstreamRssBytes(),1000001));
    }
}
