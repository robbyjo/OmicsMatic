/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SignacLsiRequestCliTest {
    @TempDir Path directory;
    private Path request(String text)throws IOException{Path p=directory.resolve("request.json");Files.writeString(p,text,StandardCharsets.UTF_8);return p;}
    @Test void UTF8PathsResolveAgainstTheRequestAndDoNotDependOnWorkingDirectory()throws Exception{
        Path p=request("{\"schema_version\":1,\"mode\":\"FIT\",\"options\":{\"counts\":\"Unicode α inputs/counts.mtx\",\"out\":\"Unicode β output/fit\",\"components\":5,\"scale-embeddings\":false,\"profile\":\"1.17.1-package-rspectra\"}}");
        List<String> args=List.of(SignacLsiRequestCli.arguments(p));assertEquals(directory.resolve("Unicode α inputs/counts.mtx").toString(),args.get(args.indexOf("--counts")+1));assertEquals(directory.resolve("Unicode β output/fit").toString(),args.get(args.indexOf("--out")+1));assertEquals("5",args.get(args.indexOf("--components")+1));assertEquals("false",args.get(args.indexOf("--scale-embeddings")+1));
    }
    @Test void VERIFYModePreservesUnicodeAnalysisPaths()throws Exception{
        assertArrayEquals(new String[]{"--verify",directory.resolve("Unicode β output/fit").toString()},SignacLsiRequestCli.arguments(request("{\"schema_version\":1,\"mode\":\"VERIFY\",\"analysis\":\"Unicode β output/fit\"}")));
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","{\"schema_version\":2,\"mode\":\"VERIFY\",\"analysis\":\"x\"}","{\"schema_version\":1,\"mode\":\"OTHER\"}","{\"schema_version\":1,\"mode\":\"FIT\",\"options\":[]}","{\"schema_version\":1,\"mode\":\"FIT\",\"options\":{\"counts\":1}}","{\"schema_version\":1,\"mode\":\"FIT\",\"options\":{\"counts\":null}}","{\"schema_version\":1,\"mode\":\"FIT\",\"options\":{\"counts\":{}}}","{\"schema_version\":1,\"mode\":\"VERIFY\",\"analysis\":\"x\",\"extra\":true}","{\"schema_version\":1,\"mode\":\"VERIFY\",\"analysis\":\"x\",\"analysis\":\"y\"}"})
    void ambiguousOrIncorrectSchemaFails(String json){assertThrows(IOException.class,()->SignacLsiRequestCli.arguments(request(json)));}
    @Test void invalidUTF8AndExcessiveFilesFailBeforeAnyAnalysis()throws Exception{
        Path p=directory.resolve("request.json");Files.write(p,new byte[]{(byte)0xff});assertThrows(IOException.class,()->SignacLsiRequestCli.arguments(p));Files.writeString(p," ".repeat(1_000_001));assertThrows(IOException.class,()->SignacLsiRequestCli.arguments(p));
    }
    @Test void helpAndUnknownOptionsNeverStartAProcess()throws Exception{
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();try(var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(errors,true,StandardCharsets.UTF_8)){
            assertEquals(0,SignacLsiRequestCli.run(new String[]{"--help"},out,err));assertTrue(output.toString(StandardCharsets.UTF_8).contains("ASCII-only"));
            Path p=request("{\"schema_version\":1,\"mode\":\"FIT\",\"options\":{\"unknown-option\":\"x\"}}");assertEquals(2,SignacLsiRequestCli.run(new String[]{"--request",p.toString()},out,err));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Unknown option"));
        }
    }
}
