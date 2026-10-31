/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatFingerprintTest {
    @TempDir Path directory;
    private Path input()throws IOException{Path file=directory.resolve("input");Files.writeString(file,"AAAA");return file;}
    private static boolean memoized(){return java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("unix");}
    @Test void nestedPassesShareOnlyCurrentPhysicalFileReads()throws Exception{
        Path file=input();try(var outer=RepeatFingerprint.enter()){
            String first=RepeatFingerprint.hash(file,4,()->{});try(var inner=RepeatFingerprint.enter()){inner.requireActive();assertEquals(first,RepeatFingerprint.hash(file,4,()->{}));}
            outer.requireActive();assertEquals(memoized()?1:2,outer.physicalHashes());
        }
    }
    @Test void realRegularHardlinkAliasesKeepTheSameCompleteFingerprint()throws Exception{
        Path file=input(),alias=directory.resolve("alias");Files.createLink(alias,file);assertTrue(Files.isSameFile(file,alias));
        try(var scope=RepeatFingerprint.enter()){assertEquals(RepeatFingerprint.hash(file,4,()->{}),RepeatFingerprint.hash(alias,4,()->{}));assertEquals(memoized()?1:2,scope.physicalHashes());}
    }
    @Test void endingOnePassCannotReuseItsHashInALaterPass()throws Exception{
        Path file=input();try(var first=RepeatFingerprint.enter()){RepeatFingerprint.hash(file,4,()->{});assertEquals(1,first.physicalHashes());}
        try(var second=RepeatFingerprint.enter()){RepeatFingerprint.hash(file,4,()->{});assertEquals(1,second.physicalHashes());}
    }
    @Test void sameLengthMutationWithRestoredModificationTimeStillReadsNewBytes()throws Exception{
        Path file=input();var modified=Files.getLastModifiedTime(file);
        try(var scope=RepeatFingerprint.enter()){
            String before=RepeatFingerprint.hash(file,4,()->{});Files.writeString(file,"CCCC");Files.setLastModifiedTime(file,modified);
            assertNotEquals(before,RepeatFingerprint.hash(file,4,()->{}));assertEquals(2,scope.physicalHashes());
        }
    }
    @Test void aCachedFileCannotBypassASmallerCompleteByteCapacity()throws Exception{
        Path file=input();try(var scope=RepeatFingerprint.enter()){scope.requireActive();RepeatFingerprint.hash(file,4,()->{});assertThrows(IOException.class,()->RepeatFingerprint.hash(file,3,()->{}));}
    }
    @Test void directoriesAndNegativeBoundsAreNeverFingerprintedAsRegularFiles()throws Exception{
        Path file=input();assertThrows(IOException.class,()->RepeatFingerprint.hash(directory,4,()->{}));assertThrows(IOException.class,()->RepeatFingerprint.hash(file,-1,()->{}));
    }
    @Test void cancellationIsVisibleAndDoesNotLeakAPass()throws Exception{
        Path file=input();RepeatFingerprint.Scope scope=RepeatFingerprint.enter();
        try{assertThrows(CancellationException.class,()->RepeatFingerprint.hash(file,4,()->{throw new CancellationException("controlled stop");}));}
        finally{scope.close();}
        assertThrows(IllegalStateException.class,scope::requireActive);try(var next=RepeatFingerprint.enter()){RepeatFingerprint.hash(file,4,()->{});assertEquals(1,next.physicalHashes());}
    }
    @Test void parallelThreadsCannotShareOrCloseAnotherThreadsVerificationPass()throws Exception{
        Path file=input();var failure=new AtomicReference<Throwable>();
        try(var outer=RepeatFingerprint.enter()){
            RepeatFingerprint.hash(file,4,()->{});
            Thread thread=new Thread(()->{try{assertThrows(IllegalStateException.class,outer::requireActive);try(var worker=RepeatFingerprint.enter()){RepeatFingerprint.hash(file,4,()->{});assertEquals(1,worker.physicalHashes());}}catch(Throwable error){failure.set(error);}},"fingerprint-unit-worker");
            thread.start();thread.join(5000);assertFalse(thread.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get());outer.requireActive();assertEquals(1,outer.physicalHashes());
        }
    }
}
