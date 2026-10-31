/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatSetupTest {
    @TempDir Path directory;
    @Test void exportsEveryExactBundledResourceAndCompleteChecksums()throws Exception{
        Path root=directory.resolve("export");RepeatSetup.export(root,()->{});
        for(String name:RepeatSetup.RESOURCES)try(var input=RepeatSetup.class.getResourceAsStream("/org/jlinalg/omics/"+name)){
            assertNotNull(input);assertArrayEquals(input.readAllBytes(),Files.readAllBytes(root.resolve(name)));
        }
        var lines=Files.readAllLines(root.resolve("SHA256SUMS"),StandardCharsets.UTF_8);assertEquals(7,lines.size());
        for(String line:lines){assertEquals("  ",line.substring(64,66));assertEquals(line.substring(0,64),RepeatInputs.hash(root.resolve(line.substring(66)),4194304,()->{}));}
        var metadata=RepeatBundle.object(root.resolve("setup.json"),1048576);assertEquals("EXPORTED_NOT_INSTALLED_OR_SEARCH_ADMITTED",metadata.get("status"));
        String readme=Files.readString(root.resolve("README.md"));assertTrue(readme.contains("SKIP3"));assertTrue(readme.contains("FAIL2"));assertTrue(readme.contains("--offline-archives"));
    }
    @Test void existingExportsAndFilesAreNeverOverwritten()throws Exception{
        Path root=directory.resolve("export");RepeatSetup.export(root,()->{});byte[] sums=Files.readAllBytes(root.resolve("SHA256SUMS"));
        assertThrows(IOException.class,()->RepeatSetup.export(root,()->{}));assertArrayEquals(sums,Files.readAllBytes(root.resolve("SHA256SUMS")));
        Path file=directory.resolve("existing");Files.writeString(file,"keep");assertThrows(IOException.class,()->RepeatSetup.export(file,()->{}));assertEquals("keep",Files.readString(file));
    }
    @Test void unicodeExportPathsPreserveEveryResource()throws Exception{
        Path root=directory.resolve("setup-\u03b1-\u4e2d");RepeatSetup.export(root,()->{});assertTrue(Files.isRegularFile(root.resolve("repeat-runtime-setup/install_repeat_payload.py")));
    }
    @Test void cancellationBeforeExportCreatesNothing()throws Exception{
        Path root=directory.resolve("cancelled");assertThrows(CancellationException.class,()->RepeatSetup.export(root,()->{throw new CancellationException("controlled setup cancellation");}));assertFalse(Files.exists(root));
    }
    @Test void publicExportReportsTheExactLimitedScope()throws Exception{
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();Path root=directory.resolve("public");
        int code=RepeatToolsCli.run(new String[]{"--export-runtime-setup",root.toString()},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        assertEquals(0,code,err.toString(StandardCharsets.UTF_8));assertTrue(err.toString(StandardCharsets.UTF_8).contains("SEARCH NOT ADMITTED"));assertTrue(out.toString(StandardCharsets.UTF_8).contains(root.toString()));
        assertFalse(Files.exists(root.resolve("COMPLETED.json")));assertTrue(RepeatToolsCli.help().contains("--export-runtime-setup"));
    }
}
