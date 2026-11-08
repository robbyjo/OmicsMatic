/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Full content and directory identity properties required by the optimized runtime verifier. */
final class LofteeRuntimeTreeTest {
    @TempDir Path temporary;
    @Test void filesystemsWithoutStableDirectoryIdentitiesAreExplicitlyRefused()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("identity-contract"));
        var attrs=Files.readAttributes(root,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attrs.fileKey()==null)assertTrue(assertThrows(IOException.class,()->LofteeRuntimeTree.snapshot(root)).getMessage().contains("stable directory identities"));
        else LofteeRuntimeTree.snapshot(root).verify();
    }
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    @Test void completeDigestMatchesExistingStrictFileVerifier()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("runtime")),folder=Files.createDirectory(root.resolve("lib")),file=folder.resolve("original.pm");Files.writeString(file,"original source\ncutoff=-58\n",StandardCharsets.UTF_8);var tree=LofteeRuntimeTree.snapshot(root);var result=LofteeRuntimeTree.read(tree,file,1000,true);assertEquals(NextflowFiles.pin(file,1000,()->{}),result.pin());assertArrayEquals(Files.readAllBytes(file),result.content());tree.verify();
    }
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    @Test void changingAnyNonprefixByteChangesTheWholeDigest()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("runtime")),file=root.resolve("code.pm");Files.writeString(file,"scientific cutoff=-58\n");var tree=LofteeRuntimeTree.snapshot(root);var expected=LofteeRuntimeTree.read(tree,file,1000,false).pin();Files.writeString(file,"scientific cutoff=-57\n");assertNotEquals(expected,LofteeRuntimeTree.read(tree,file,1000,false).pin());
    }
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    @Test void anAddedFileCannotHideOutsideTheExpectedRuntimeSet()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("runtime"));Files.writeString(root.resolve("code.pm"),"source");var tree=LofteeRuntimeTree.snapshot(root);Files.writeString(root.resolve("new-code.pm"),"unexpected source");assertThrows(IOException.class,tree::verify);
    }
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    @Test void replacingAParentDirectoryWithSameNamedFilesIsDetected()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("runtime")),folder=Files.createDirectory(root.resolve("lib"));Files.writeString(folder.resolve("code.pm"),"same source");var tree=LofteeRuntimeTree.snapshot(root);Files.move(folder,temporary.resolve("retained-original-lib"));Files.createDirectory(folder);Files.writeString(folder.resolve("code.pm"),"same source");assertThrows(IOException.class,tree::verify);
    }
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    @Test void readingAnUncapturedOrOutsideFileIsRefused()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("runtime")),outside=temporary.resolve("outside.pm");Files.writeString(outside,"source");var tree=LofteeRuntimeTree.snapshot(root);assertThrows(IOException.class,()->LofteeRuntimeTree.read(tree,outside,1000,false));
    }
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    @Test void oversizedFilesFailInsteadOfReturningPartialHashes()throws Exception {
        Path root=Files.createDirectory(temporary.resolve("runtime")),file=root.resolve("code.pm");Files.writeString(file,"source larger than quota");var tree=LofteeRuntimeTree.snapshot(root);assertThrows(IOException.class,()->LofteeRuntimeTree.read(tree,file,1,false));
    }
}
