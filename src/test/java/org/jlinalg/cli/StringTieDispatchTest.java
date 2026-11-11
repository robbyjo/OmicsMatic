/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StringTieDispatchTest {
    @TempDir Path temporary;
    private record Reply(int code, String out, String error) { }
    private Reply run(String... args)throws Exception {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();
        int code=JLinAlgCli.run(args,new PrintStream(output,true,StandardCharsets.UTF_8),new PrintStream(error,true,StandardCharsets.UTF_8));
        return new Reply(code,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    @Test void earlyRootRoutePreservesTypedHelp()throws Exception {
        var reply=run("stringtie-tools","--help");assertEquals(0,reply.code());assertEquals(StringTieToolsCli.help()+"\n",reply.out());assertEquals("",reply.error());
    }
    @Test void rootHelpAdvertisesTheNewCommand()throws Exception {var reply=run("--help");assertEquals(0,reply.code());assertTrue(reply.out().contains("stringtie-tools --help"));}
    @Test void allSevenExactProfilesAreVisibleWithoutSetup()throws Exception {
        var reply=run("stringtie-tools","--runtime-profiles");assertEquals(0,reply.code());assertEquals(7,NextflowFiles.list(SimpleJson.parse(reply.out())).size());assertTrue(reply.out().contains("1.3.0"));assertTrue(reply.out().contains("3.0.3"));
    }
    @Test void missingRuntimeIsProminentAndCreatesNoOutput()throws Exception {
        Path out=temporary.resolve("not-created");var reply=run("stringtie-tools","--recipe",temporary.resolve("not-opened.json").toString(),"--out",out.toString());assertEquals(3,reply.code());assertTrue(reply.error().contains("SKIP3"));assertTrue(reply.error().contains("skip dependent analysis"));assertFalse(Files.exists(out));
    }
    @Test void opaqueFlagsAreRejectedByTypedDispatcher()throws Exception {var reply=run("stringtie-tools","--flags","-rf");assertEquals(2,reply.code());assertTrue(reply.error().contains("StringTie FAIL2"));}
    @Test void badCapturedBundleFailsWithoutCreatingAnAnalysis()throws Exception {Path path=temporary.resolve("not-a-bundle");var reply=run("stringtie-tools","--verify",path.toString());assertEquals(2,reply.code());assertTrue(reply.error().contains("FAIL2"));assertFalse(Files.exists(path));}
    @Test void rootExportIsCompleteAndDoesNotInstall()throws Exception {
        Path kit=temporary.resolve("kit");var reply=run("stringtie-tools","--export-runtime-setup",kit.toString());assertEquals(0,reply.code());assertTrue(reply.out().contains("no installation or analysis"));assertTrue(Files.exists(kit.resolve("export.json")));assertFalse(Files.exists(kit.resolve("bin")));assertFalse(Files.exists(kit.resolve("INCOMPLETE.txt")));
    }
    @Test void earlierDedicatedRoutesRemainAvailable()throws Exception {for(String command:new String[]{"bigwig-tools","salmon-tools","tomtom-tools","nextflow-tools"})assertEquals(0,run(command,"--help").code(),command);}
}
