/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class RepeatOwnedTreeTest {
    @TempDir Path root;
    private Map<?,?> snapshot()throws Exception{return RepeatOwnedTree.snapshot(root,new RepeatBundle.Limits(1048576,128),Set.of("manifest.json","COMPLETED.json"),()->{});}
    @Test void completeBytesAndEmptyDirectoryNamesArePartOfTheSeal()throws Exception{
        Files.writeString(root.resolve("sequence"),"ACGT");Files.createDirectory(root.resolve("empty"));var before=snapshot();
        assertEquals(Set.of(".","sequence","empty"),before.keySet());assertEquals(4L,((Map<?,?>)before.get("sequence")).get("bytes"));
        Files.move(root.resolve("empty"),root.resolve("other"));assertNotEquals(before,snapshot());
    }
    @Test void sameSizeBytesWithRestoredMtimeCannotRetainTheSeal()throws Exception{
        Path file=root.resolve("data");Files.writeString(file,"ACGT");FileTime time=Files.getLastModifiedTime(file);var before=snapshot();
        Files.writeString(file,"TGCA");Files.setLastModifiedTime(file,time);assertNotEquals(before,snapshot());
    }
    @Test void excludedMetadataStillConsumesTheWholeByteAndMemberCapacity()throws Exception{
        Files.writeString(root.resolve("data"),"ACGT");var before=snapshot();Files.writeString(root.resolve("manifest.json"),"bound separately");assertEquals(before,snapshot());
        Files.write(root.resolve("COMPLETED.json"),new byte[65536]);assertThrows(IOException.class,()->RepeatOwnedTree.snapshot(root,new RepeatBundle.Limits(65536,128),Set.of("manifest.json","COMPLETED.json"),()->{}));
    }
    @Test void directoryCannotMasqueradeAsExcludedMetadata()throws Exception{
        Files.createDirectory(root.resolve("manifest.json"));assertThrows(IOException.class,this::snapshot);
    }
    @Test void completeMemberCountIncludesDirectoriesAndTheRoot()throws Exception{
        for(int i=0;i<8;i++)Files.createDirectory(root.resolve("empty"+i));assertThrows(IOException.class,()->RepeatOwnedTree.snapshot(root,new RepeatBundle.Limits(1048576,8),Set.of(),()->{}));
    }
    @Test void cancellationIsNotConvertedIntoACompletedCensus(){assertThrows(CancellationException.class,()->RepeatOwnedTree.snapshot(root,new RepeatBundle.Limits(1048576,128),Set.of(),()->{throw new CancellationException("stop");}));}
    @Test @EnabledOnOs(OS.LINUX) void hardlinkAliasesCountFullLogicalBytesAndKeepBothNames()throws Exception{
        Path file=root.resolve("data");Files.write(file,new byte[40000]);Files.createLink(root.resolve("alias"),file);assertEquals(Set.of(".","data","alias"),snapshot().keySet());
        assertThrows(IOException.class,()->RepeatOwnedTree.snapshot(root,new RepeatBundle.Limits(65536,128),Set.of(),()->{}));
    }
    @Test @EnabledOnOs(OS.LINUX) void symlinksAndModeChangesCannotBeHiddenInTheTree()throws Exception{
        Path file=root.resolve("data");Files.writeString(file,"ACGT");var before=snapshot();Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rwx------"));assertNotEquals(before,snapshot());
        Files.createSymbolicLink(root.resolve("alias"),Path.of("data"));assertThrows(IOException.class,this::snapshot);
    }
}
