/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatOriginalStagesTest {
    @TempDir Path directory;
    private int serial;
    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void everyUpstreamPathIsCheckedBeforeAnOriginalProcessCanStart()throws Exception{
        Path safe=Path.of("/tmp/repeat-safe");RepeatOriginalStages.originalPaths(safe,safe,safe,safe);
        for(int index=0;index<4;index++)for(String bad:List.of("/tmp/has space","/tmp/Unicode-\u03bb","/tmp/shell;token","relative")){
            Path[] paths={safe,safe,safe,safe};paths[index]=Path.of(bad);
            assertThrows(RepeatPayload.Unavailable.class,()->RepeatOriginalStages.originalPaths(paths[0],paths[1],paths[2],paths[3]));
        }
    }
    private RepeatRecipe.Spec spec(String mode,String profile,String mask)throws Exception{
        Path root=Files.createDirectory(directory.resolve("request-"+serial++));RepeatTestRecipes.inputs(root);var request=RepeatTestRecipes.searchRequest(mode);request.put("profile",profile);request.put("mask",mask);return RepeatRecipe.read(RepeatTestRecipes.write(root,request));
    }
    private RepeatOriginalStages.Commands command(RepeatRecipe.Spec spec,long bases)throws Exception{
        return RepeatOriginalStages.commands(spec,directory.resolve("original"),directory.resolve("query.fa"),spec.library()==null?null:directory.resolve("library.fa"),directory.resolve("output"),bases);
    }
    @Test void customSearchAndPostprocessingAreDistinctImmutableArgumentPopulations()throws Exception{
        var commands=command(spec("CUSTOM_LIBRARY",RepeatRecipe.CURRENT,"N"),8);
        assertEquals("/usr/bin/perl",commands.search().get(0));assertTrue(commands.search().contains("-nopost"));assertTrue(commands.search().contains("-lib"));assertTrue(commands.search().contains("-norna"));assertTrue(commands.search().contains("-nolow"));
        assertTrue(commands.postprocess().get(1).endsWith("ProcessRepeats"));assertTrue(commands.postprocess().containsAll(List.of("-a","-gff","-source","-lib","-maskSource","-orifile")));
        assertFalse(commands.postprocess().contains("-nopost"));assertFalse(commands.postprocess().contains("-norna"));assertFalse(commands.postprocess().contains("-nolow"));
        assertThrows(UnsupportedOperationException.class,()->commands.search().add("unreviewed"));assertThrows(UnsupportedOperationException.class,()->commands.postprocess().clear());
        assertEquals(directory.resolve("output/post-input/query.fa.cat").toString(),commands.postprocess().get(commands.postprocess().size()-1));
    }
    @Test void explicitTaxonIsOneArgumentAndSimpleOnlyReachesBothOriginalStages()throws Exception{
        for(String mode:List.of("SPECIES","SIMPLE_ONLY")){
            var commands=command(spec(mode,RepeatRecipe.HISTORICAL,"N"),8);
            for(var population:List.of(commands.search(),commands.postprocess())){
                assertEquals("Mus musculus",population.get(population.indexOf("-species")+1));assertFalse(population.contains("-lib"));assertEquals(mode.equals("SIMPLE_ONLY"),population.contains("-noint"));
            }
        }
    }
    @Test void everyMaskModeUsesTheSameExplicitOriginalPostprocessingChoice()throws Exception{
        for(String mask:List.of("N","SOFT","X")){
            var commands=command(spec("CUSTOM_LIBRARY",RepeatRecipe.HISTORICAL,mask),8);
            for(var population:List.of(commands.search(),commands.postprocess())){assertEquals(mask.equals("SOFT"),population.contains("-xsmall"));assertEquals(mask.equals("X"),population.contains("-x"));}
        }
    }
    @Test void originalTenMillionBaseCompressionBoundaryIsExact()throws Exception{
        assertEquals("query.fa.cat",RepeatOriginalStages.rawName(10000000));assertEquals("query.fa.cat.gz",RepeatOriginalStages.rawName(10000001));assertThrows(IOException.class,()->RepeatOriginalStages.rawName(0));assertThrows(IOException.class,()->RepeatOriginalStages.rawName(-1));
        var commands=command(spec("CUSTOM_LIBRARY",RepeatRecipe.CURRENT,"N"),10000001);assertEquals("query.fa.cat.gz",commands.rawName());assertTrue(commands.postprocess().get(commands.postprocess().size()-1).endsWith("query.fa.cat.gz"));
    }
    @Test void unusedOrMissingCustomLibrariesAreRefusedBeforeExecution()throws Exception{
        var custom=spec("CUSTOM_LIBRARY",RepeatRecipe.CURRENT,"N");var species=spec("SPECIES",RepeatRecipe.HISTORICAL,"N");Path path=directory.resolve("input");
        assertThrows(IOException.class,()->RepeatOriginalStages.commands(custom,path,path,null,path,8));assertThrows(IOException.class,()->RepeatOriginalStages.commands(species,path,path,path,path,8));
    }
    @Test void completeStageCopiesRetainAndRecheckBothSourceAndOwnedBytes()throws Exception{
        Path source=directory.resolve("source"),copy=directory.resolve("copy");Files.writeString(source,"complete raw result\n");var protectedFile=RepeatOriginalStages.copyProtected(source,copy,100,()->{});protectedFile.verify(()->{});assertArrayEquals(Files.readAllBytes(source),Files.readAllBytes(copy));
        Files.writeString(source,"changed! raw result\n");assertThrows(IOException.class,()->protectedFile.verify(()->{}));
    }
    @Test void completeCopiesNeverOverwriteExistingFilesOrAdmitInsufficientCapacity()throws Exception{
        Path source=directory.resolve("source"),copy=directory.resolve("existing");Files.writeString(source,"AAAA");Files.writeString(copy,"KEEP");
        assertThrows(IOException.class,()->RepeatOriginalStages.copyProtected(source,copy,4,()->{}));assertEquals("KEEP",Files.readString(copy));
        Path small=directory.resolve("insufficient");assertThrows(IOException.class,()->RepeatOriginalStages.copyProtected(source,small,3,()->{}));assertFalse(Files.exists(small));
    }
    @Test void cancellationCannotCreateAnAdmittedStageCopy()throws Exception{
        Path source=directory.resolve("source"),copy=directory.resolve("cancelled");Files.writeString(source,"AAAA");assertThrows(CancellationException.class,()->RepeatOriginalStages.copyProtected(source,copy,4,()->{throw new CancellationException("controlled stop");}));assertFalse(Files.exists(copy));
    }
    private static byte[] gzip(byte[] data)throws IOException{var raw=new ByteArrayOutputStream();try(var out=new GZIPOutputStream(raw)){out.write(data);}return raw.toByteArray();}
    @Test void compressedRawSearchRequiresCompleteCrcTrailerAndPhysicalEof()throws Exception{
        Path source=directory.resolve("raw.gz");byte[] original=gzip("complete raw original search\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(source,original);RepeatOriginalStages.verifyRaw(source,true,1024,()->{});
        byte[] crc=original.clone();crc[crc.length-8]^=1;byte[] trailing=java.util.Arrays.copyOf(original,original.length+1);
        for(byte[] invalid:List.of(crc,trailing,java.util.Arrays.copyOf(original,original.length-1))){Files.write(source,invalid);assertThrows(IOException.class,()->RepeatOriginalStages.verifyRaw(source,true,1024,()->{}));}
    }
    @Test void compressedRawExpansionCannotBypassItsDecodedCapacity()throws Exception{
        Path source=directory.resolve("expanded.gz");byte[] raw=new byte[8192];java.util.Arrays.fill(raw,(byte)'A');byte[] compressed=gzip(raw);assertTrue(compressed.length<128);Files.write(source,compressed);assertThrows(IOException.class,()->RepeatOriginalStages.verifyRaw(source,true,128,()->{}));
        RepeatOriginalStages.verifyRaw(source,true,8192,()->{});
    }
    @Test void processLimitsReserveTheCompleteRootAndFinalDiagnostics()throws Exception{
        var original=RepeatProcess.Limits.health();var reserved=RepeatOriginalStages.reserve(original);assertEquals(original.workspaceBytes()-262144,reserved.workspaceBytes());assertEquals(original.fileCount()-9,reserved.fileCount());assertEquals(original.wallMillis(),reserved.wallMillis());assertEquals(original.addressSpaceBytes(),reserved.addressSpaceBytes());
        var tooSmall=new RepeatProcess.Limits(1000,16777216,1,1024,16,262144,9,1,1,16777216,50);assertThrows(IOException.class,()->RepeatOriginalStages.reserve(tooSmall));
    }
    @Test void completeOutputFencesDetectSameLengthChangesEvenWhenModificationTimeIsRestored()throws Exception{
        Path root=Files.createDirectory(directory.resolve("outputs")),path=root.resolve("query.fa.out");Files.writeString(path,"AAAA");var modified=Files.getLastModifiedTime(path);var fence=RepeatOriginalStages.fence(root,RepeatProcess.Limits.health(),()->{});fence.verify(()->{});
        Files.writeString(path,"CCCC");Files.setLastModifiedTime(path,modified);assertThrows(IOException.class,()->fence.verify(()->{}));
    }
    @Test void outputFencesIncludeNewOrMissingFilesAndEmptyDirectories()throws Exception{
        for(String change:List.of("new-file","removed-file","empty-directory")){
            Path root=Files.createDirectory(directory.resolve(change));Files.writeString(root.resolve("original"),"retained output");var fence=RepeatOriginalStages.fence(root,RepeatProcess.Limits.health(),()->{});
            if(change.equals("new-file"))Files.writeString(root.resolve("extra"),"extra");else if(change.equals("removed-file"))Files.move(root.resolve("original"),directory.resolve("retained-removed-output"));else Files.createDirectory(root.resolve("extra"));
            assertThrows(IOException.class,()->fence.verify(()->{}));
        }
    }
    @Test void outputFenceHashesAreImmutableAndPreserveEveryOutputMember()throws Exception{
        Path root=Files.createDirectory(directory.resolve("outputs"));Files.writeString(root.resolve("query.fa.out"),"complete annotations");Files.writeString(root.resolve("query.fa.masked"),"complete mask");var fence=RepeatOriginalStages.fence(root,RepeatProcess.Limits.health(),()->{});
        assertEquals(2,fence.inventory().hashes().size());assertThrows(UnsupportedOperationException.class,()->fence.inventory().hashes().clear());
    }
}
