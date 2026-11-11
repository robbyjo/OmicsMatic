/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IqtreeRuntimeTest {
    @TempDir Path temporary;
    @Test void exactProfilesAreImmutableAndNeverNormalizeTheUnresolvedEdition() {
        assertEquals(8,IqtreeRuntime.profiles().size());assertTrue(IqtreeRuntime.profiles().contains("linux-x86_64-iqtree-2.1.4-beta"));
        assertFalse(IqtreeRuntime.profiles().contains("linux-x86_64-iqtree-1.6.619"));assertFalse(IqtreeRuntime.profiles().contains("linux-x86_64-iqtree-3.0.1"));assertThrows(UnsupportedOperationException.class,()->IqtreeRuntime.profiles().clear());
    }
    @Test void missingRuntimeAndUnsupportedHostAreReadOnlyUnavailablePrerequisites() {
        assertThrows(IqtreeRuntime.Unavailable.class,()->IqtreeRuntime.open(temporary.resolve("absent"),"linux-x86_64-iqtree-2.3.6"));
        assertThrows(IqtreeRuntime.Unavailable.class,()->IqtreeRuntime.open(temporary.resolve("absent"),"linux-x86_64-iqtree-1.6.619"));assertFalse(Files.exists(temporary.resolve("absent")));
    }
    @Test void exportedSetupMatchesAllBundledBytesAndContainsNoUpstreamExecutable() throws Exception {
        Path output=temporary.resolve("setup");var files=IqtreeRuntime.exportSetup(output);
        assertEquals(Set.of("install_iqtree.py","iqtree-runtime-lock.json","iqtree-runtime/GPL-2.0.txt","iqtree-runtime/health/alignment.fasta","iqtree-runtime/health/fixed.nwk"),files.keySet());
        for(var entry:files.entrySet()) {
            byte[] actual=Files.readAllBytes(output.resolve(entry.getKey()));assertEquals(actual.length,entry.getValue().bytes());
            String resource="/org/jlinalg/omics/"+(entry.getKey().equals("iqtree-runtime-lock.json")?entry.getKey():"iqtree-runtime-setup/"+entry.getKey());
            try(var input=getClass().getResourceAsStream(resource)) { assertNotNull(input);assertArrayEquals(input.readAllBytes(),actual); }
            assertFalse(actual.length>4 && actual[0]==127 && actual[1]=='E' && actual[2]=='L' && actual[3]=='F');
        }
        assertTrue(Files.readString(output.resolve("README.txt")).contains("--allow-install --allow-download"));assertTrue(Files.readString(output.resolve("README.txt")).contains("COMPLETED.json"));
    }
    @Test void anExistingExportDirectoryIsPreservedWithoutReplacingFiles() throws Exception {
        Path output=temporary.resolve("setup");Files.createDirectory(output);Path sentinel=output.resolve("keep");Files.writeString(sentinel,"original",StandardOpenOption.CREATE_NEW);
        assertThrows(IOException.class,()->IqtreeRuntime.exportSetup(output));assertEquals("original",Files.readString(sentinel));try(var files=Files.list(output)) { assertEquals(1,files.count()); }
    }
    private static byte[] elf() {
        byte[] raw=new byte[256];var buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        raw[0]=127;raw[1]='E';raw[2]='L';raw[3]='F';raw[4]=2;raw[5]=1;raw[6]=1;
        buffer.putShort(16,(short)2).putShort(18,(short)62).putInt(20,1).putLong(24,128).putLong(32,64).putShort(52,(short)64).putShort(54,(short)56).putShort(56,(short)1);
        buffer.putInt(64,1).putInt(68,5).putLong(72,0).putLong(96,256).putLong(104,256).putLong(112,4096);return raw;
    }
    @Test void staticElfAdmissionRejectsDynamicSegmentsArchitectureChangesAndIncompleteExtents() throws Exception {
        assertEquals(1,IqtreeRuntime.staticElf(elf()).size());
        for(String mutation:List.of("magic","class","endian","machine","offset","size","count","dynamic","interpreter","file","memory","stack")) {
            byte[] raw=elf();var buffer=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            switch(mutation) {case "magic"->raw[0]=0;case "class"->raw[4]=1;case "endian"->raw[5]=2;case "machine"->buffer.putShort(18,(short)183);case "offset"->buffer.putLong(32,Long.MAX_VALUE);case "size"->buffer.putShort(54,(short)8);case "count"->buffer.putShort(56,(short)65);case "dynamic"->buffer.putInt(64,2);case "interpreter"->buffer.putInt(64,3);case "file"->buffer.putLong(96,257);case "memory"->buffer.putLong(104,128);case "stack"->{buffer.putInt(64,0x6474e551);buffer.putInt(68,7);}default->throw new AssertionError(mutation);}
            assertThrows(IOException.class,()->IqtreeRuntime.staticElf(raw),mutation);
        }
    }
    private static Path originalHomes() {
        String root=System.getenv("JLINALG_IQTREE_HOME_ROOT");assumeTrue(root!=null && !root.isBlank(),"Set JLINALG_IQTREE_HOME_ROOT for the actual eight-profile runtime controls");assumeTrue(System.getProperty("os.name").equals("Linux"),"Original runtimes require Linux Java");return Path.of(root);
    }
    @ParameterizedTest @ValueSource(strings={"1.6.12","2.1.2","2.1.4-beta","2.2.0.3","2.3.1","2.3.5","2.3.6","2.4.0"})
    void everyActualOriginalHomeHasCompleteNativeJc69ReplayAndStableBindings(String version) throws Exception {
        Path root=originalHomes(),home=root.resolve(version);var context=IqtreeRuntime.open(home,"linux-x86_64-iqtree-"+version);
        assertEquals(version,context.version());assertEquals(8,context.files().size());assertEquals(2,context.helpers().size());assertTrue(context.setupFiles().containsKey("health/result.sitelh"));
        assertEquals(List.of(home.resolve(version.equals("1.6.12")?"bin/iqtree":"bin/iqtree2").toString()),context.command());
        assertEquals("2",context.environment(2).get("OMP_NUM_THREADS"));assertThrows(IllegalArgumentException.class,()->context.environment(0));assertThrows(IllegalArgumentException.class,()->context.environment(257));context.verify();
        assertThrows(UnsupportedOperationException.class,()->context.files().clear());assertThrows(UnsupportedOperationException.class,()->context.setupFiles().clear());
    }
    @ParameterizedTest @ValueSource(strings={"extra-member","changed-mode","changed-program","changed-report","provisional"})
    void realRetainedIntegrityAndFinalizationFailuresCannotBeAdmitted(String name) {
        Path root=originalHomes();IOException error=assertThrows(IOException.class,()->IqtreeRuntime.open(root.resolve(name),"linux-x86_64-iqtree-2.3.6"));
        assertFalse(error instanceof IqtreeRuntime.Unavailable,"Existing corrupt/incomplete evidence is a failure, not a missing-runtime skip: "+error);
    }
}
