package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BroadGseaRecipeTest {
    @TempDir Path temporary;
    private static Map<String,Object> json(String source)throws IOException{return BroadGseaRecipe.object(BroadGseaFiles.parse(source.getBytes(StandardCharsets.UTF_8)),"test");}
    @Test void preservesFloatLexemesAndRejectsDuplicateKeysAndNonfiniteValues()throws Exception{
        var map=json("{\"x\":1.000000059604644775390625000000000000001,\"z\":-0}");
        assertEquals(Float.floatToRawIntBits(Math.nextUp(1f)),Float.floatToRawIntBits(BroadGseaRecipe.f32(map.get("x"),"x")));
        assertEquals(Float.floatToRawIntBits(-0f),Float.floatToRawIntBits(BroadGseaRecipe.f32(map.get("z"),"z")));
        assertThrows(IOException.class,()->json("{\"x\":1,\"x\":2}"));
        assertThrows(IOException.class,()->BroadGseaRecipe.f32(json("{\"x\":1e1000}").get("x"),"x"));
        assertThrows(IOException.class,()->BroadGseaRecipe.f32("NaN","x"));
    }
    @Test void distinguishesExternalPrerequisitesFromMissingCapturedEvidence()throws Exception{
        Path missing=temporary.resolve("missing.json");
        var external=assertThrows(BroadGseaFiles.Unavailable.class,()->BroadGseaFiles.bytes(missing,100));assertEquals(3,BroadGseaFiles.failure(external));
        var captured=assertThrows(IOException.class,()->BroadGseaFiles.captured(temporary,"missing.json",100));assertEquals(2,BroadGseaFiles.failure(captured));
        assertEquals(2,BroadGseaFiles.failure(new java.nio.file.AccessDeniedException("output")));
    }
    @Test void protectsPhysicalParentCapturesAndLeavesOrdinarySiblingsAvailable()throws Exception{
        Path capture=temporary.resolve("capture");java.nio.file.Files.createDirectory(capture);
        java.nio.file.Files.writeString(capture.resolve("capture-kind.json"),"{}");
        assertThrows(IOException.class,()->BroadGseaFiles.fresh(capture.resolve("child")));
        assertThrows(IOException.class,()->BroadGseaFiles.outside(capture.resolve("child"),capture));
        Path sibling=BroadGseaFiles.fresh(temporary.resolve("sibling"));assertTrue(java.nio.file.Files.isDirectory(sibling));
        assertThrows(IOException.class,()->BroadGseaFiles.fresh(sibling));
        assertTrue(java.nio.file.Files.exists(capture.resolve("capture-kind.json")));
    }
    @Test void countsLiveHeldOpenWorkerFilesAgainstTheSameOutputBudgetUsedByVerification()throws Exception{
        Path results=temporary.resolve("results");java.nio.file.Files.createDirectory(results);Path path=results.resolve("live.bin");
        var caps=new BroadGseaRecipe.Caps(10000,1000,100,60,128,10000,10000,10000,100);
        try(var output=java.nio.channels.FileChannel.open(path,java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){
            output.write(java.nio.ByteBuffer.wrap(new byte[2000]));
            assertThrows(IOException.class,()->BroadGseaRuntime.check(temporary,caps));
            assertEquals(2000,output.size());
        }
        assertEquals(2000,java.nio.file.Files.size(path));
    }
    @Test void progressLedgersUseCanonicalLfAndPreserveLiteralControlCharactersInJson()throws Exception{
        Path partial=temporary.resolve("partial");String name="set\r\né";
        try(var progress=new BroadGseaOutputs.Progress(partial)){
            progress.disposition(new org.jlinalg.enrichment.BroadGseaEngine.Disposition(name,1,List.of("gene\r\n"),List.of(),"RETAINED",false));
            progress.observed(0,name,new org.jlinalg.enrichment.BroadGseaStatistics.Score(.5f,0,1f,new int[]{0},new float[]{.5f},new float[]{.5f,0f}));
        }
        for(String file:List.of("dispositions.jsonl","observed.jsonl")){
            String content=java.nio.file.Files.readString(partial.resolve(file));assertTrue(content.endsWith("\n"));assertFalse(content.contains("\r"));
            assertEquals(name,BroadGseaRecipe.text(json(content),"name"));
        }
    }
}
