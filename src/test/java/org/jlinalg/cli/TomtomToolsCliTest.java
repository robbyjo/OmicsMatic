/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomtomToolsCliTest {
    @TempDir Path temporary;
    @Test void publicSetupExportIsPinnedStandaloneAndNeverOverwritesExistingWork()throws Exception {
        Path root=temporary.resolve("setup-kit");var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();String[] command={"tomtom-tools","--export-runtime-setup",root.toString()};assertEquals(0,JLinAlgCli.run(command,new PrintStream(output),new PrintStream(error)));assertEquals("",error.toString(StandardCharsets.UTF_8));assertTrue(output.toString(StandardCharsets.UTF_8).contains("no installation or analysis"));var sources=TomtomBundle.json(root.resolve("KIT-SOURCES.json"),65536);assertEquals(3,sources.size());for(var entry:sources.entrySet())assertEquals(NextflowFiles.fingerprint(entry.getValue(),2097152),NextflowFiles.pin(root.resolve(NextflowFiles.member(NextflowFiles.text(entry.getKey()))),2097152,()->{}));assertTrue(Files.readString(root.resolve("RUN-SETUP.txt")).contains("--allow-download"));assertFalse(Files.exists(root.resolve("COMPLETED.json")));Files.writeString(root.resolve("keep.txt"),"keep");assertEquals(2,JLinAlgCli.run(command,new PrintStream(output),new PrintStream(error)));assertEquals("keep",Files.readString(root.resolve("keep.txt")));
    }
    @Test void publicRootDispatchAndStandaloneHelpDescribeExactModesAndRecovery(){var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"tomtom-tools","--help"},new PrintStream(output),new PrintStream(error)));String text=output.toString(StandardCharsets.UTF_8);assertTrue(text.contains("FULL_REPORT"));assertTrue(text.contains("SKIP3"));assertTrue(text.contains("FAIL2"));assertTrue(text.contains("never installs"));assertEquals("",error.toString(StandardCharsets.UTF_8));}
    @Test void unknownAndDuplicateOptionsNeverLaunchOrCreateAResult(){var out=new PrintStream(new ByteArrayOutputStream());var err=new PrintStream(new ByteArrayOutputStream());assertEquals(2,TomtomToolsCli.run(new String[]{"--extra-flags","-text"},out,err));assertEquals(2,TomtomToolsCli.run(new String[]{"--recipe","x","--recipe","y","--out",temporary.resolve("not-created").toString()},out,err));assertFalse(Files.exists(temporary.resolve("not-created")));}
    @Test void missingRuntimeFlagsCreateAProminentNamedSkipWithNoOverallCompletion()throws Exception {
        Path recipe=recipe();Path root=temporary.resolve("skipped");var error=new ByteArrayOutputStream();assertEquals(3,TomtomToolsCli.run(new String[]{"--recipe",recipe.toString(),"--out",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error)));assertTrue(error.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertFalse(Files.exists(root.resolve("COMPLETED.json")));assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("ANALYSIS SKIPPED"));assertEquals("SKIP3_ANALYSIS_SKIPPED",TomtomBundle.json(root.resolve("FAILURE.json"),65536).get("status"));
    }
    @Test void unavailablePlatformOrExactHomeSkipsWithoutInventingAnAnalysis()throws Exception {
        Path recipe=recipe(),root=temporary.resolve("unavailable");var error=new ByteArrayOutputStream();assertEquals(3,TomtomToolsCli.run(new String[]{"--recipe",recipe.toString(),"--home",temporary.resolve("absent-home").toString(),"--build-evidence",temporary.resolve("absent-build").toString(),"--out",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error)));assertTrue(error.toString(StandardCharsets.UTF_8).contains("Required action"));assertFalse(Files.exists(root.resolve("process")));assertFalse(Files.exists(root.resolve("COMPLETED.json")));
    }
    @Test void existingOutputIsPreservedAndIncompleteBundleFailsVerification()throws Exception {
        Path root=temporary.resolve("existing");Files.createDirectory(root);Path sentinel=root.resolve("keep.txt");Files.writeString(sentinel,"keep");Path recipe=recipe();assertEquals(2,TomtomToolsCli.run(new String[]{"--recipe",recipe.toString(),"--out",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));assertEquals("keep",Files.readString(sentinel));assertFalse(Files.exists(root.resolve("FAILURE.json")));assertEquals(2,TomtomToolsCli.run(new String[]{"--verify",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
    }
    private Path recipe()throws Exception {Path source=temporary.resolve("recipe.json");var spec=TomtomPreparedInputsTest.recipe(temporary.resolve("query.meme"),List.of(temporary.resolve("targets.meme")));Files.writeString(source,RsemJson.json(spec));return source;}
}
