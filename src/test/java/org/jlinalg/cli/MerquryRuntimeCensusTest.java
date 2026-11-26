/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class MerquryRuntimeCensusTest {
    @TempDir Path temporary;
    @Test void unusedDanglingLinkIsPinnedAndMustRemainUnresolved()throws Exception {
        if(!System.getProperty("os.name").equals("Linux")){assertThrows(MerquryRuntime.Unavailable.class,MerquryRuntime::platform);return;}
        Path root=temporary.toRealPath(),link=Files.createSymbolicLink(root.resolve("unused-sources.zip"),Path.of("absent-sources.zip"));
        Map<String,Object> nodes=Map.of(root.toString(),Map.of("kind","directory","mode",NextflowFiles.mode(root)),link.toString(),Map.of("kind","dangling-symlink","target","absent-sources.zip"));
        Map<String,Object> census=Map.of("roots",List.of(root.toString()),"files",List.of(),"nodes",nodes,"complete_bytes",0);
        MerquryRuntime.verifyInventory(census,()->{});Files.writeString(root.resolve("absent-sources.zip"),"new content");assertThrows(IOException.class,()->MerquryRuntime.verifyInventory(census,()->{}));
    }
    @Test void evenAnEmptyUnexpectedDirectoryChangesTheCompleteCensus()throws Exception {
        if(!System.getProperty("os.name").equals("Linux")){assertThrows(MerquryRuntime.Unavailable.class,MerquryRuntime::platform);return;}
        Path root=temporary.toRealPath();Map<String,Object> census=Map.of("roots",List.of(root.toString()),"files",List.of(),"nodes",Map.of(root.toString(),Map.of("kind","directory","mode",NextflowFiles.mode(root))),"complete_bytes",0);
        MerquryRuntime.verifyInventory(census,()->{});Files.createDirectory(root.resolve("extra"));assertThrows(IOException.class,()->MerquryRuntime.verifyInventory(census,()->{}));
    }
}
