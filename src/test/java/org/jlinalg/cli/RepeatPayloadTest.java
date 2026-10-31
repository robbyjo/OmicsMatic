/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatPayloadTest {
    @TempDir Path directory;
    static boolean linux(){return System.getProperty("os.name").equals("Linux");}
    static RepeatPayload.Context context(Path home,Map<String,RepeatPayload.Fingerprint> files){return new RepeatPayload.Context(home,RepeatRecipe.CURRENT,"4.2.6",null,files,Map.of(),new RepeatPayload.Limits(1073741824,4096),"0".repeat(64));}
    @Test void completePayloadCapacitiesArePositiveBoundedAndExactLongValues(){
        assertThrows(IllegalArgumentException.class,()->new RepeatPayload.Limits(0,1));assertThrows(IllegalArgumentException.class,()->new RepeatPayload.Limits(1,0));assertThrows(IllegalArgumentException.class,()->new RepeatPayload.Limits(1000000000001L,1));assertThrows(IllegalArgumentException.class,()->new RepeatPayload.Limits(1,1000001));assertEquals(5861387976L,new RepeatPayload.Limits(5861387976L,2048).bytes());
    }
    @Test void wrapperSelectsIsolatedNoSiteNoBytecodeAndPreservesArgumentBoundaries(){
        Path home=Path.of("/tmp/private-repeat"),python=Path.of("/usr/bin/python3.12");String text=new String(RepeatPayload.wrapper(home,python),StandardCharsets.UTF_8);
        assertTrue(text.startsWith("#!/bin/sh\nexec "+python));assertTrue(text.contains("\"-I\" \"-S\" \"-B\""));assertTrue(text.contains(home.resolve("python/repeat_python_bootstrap.py").toString()));assertTrue(text.endsWith("\"$@\"\n"));
    }
    @Test void originalPathRefusesWhitespaceShellMetacharactersAndImplicitNormalization(){
        for(String value:List.of("relative/run","/tmp/new run","/tmp/α","/tmp/$HOME","/tmp/a/../b","/tmp/a;false"))assertThrows(RepeatPayload.Unavailable.class,()->RepeatPayload.originalPath(Path.of(value)));
        if(linux())assertDoesNotThrow(()->RepeatPayload.originalPath(Path.of("/tmp/new-ascii_1+2")));
        else assertThrows(RepeatPayload.Unavailable.class,()->RepeatPayload.originalPath(directory));
    }
    @Test void unsupportedHostOrEditionIsUnavailableWithoutCreatingAHome()throws Exception{
        Path absent=directory.resolve("unavailable");assertThrows(RepeatPayload.Unavailable.class,()->RepeatPayload.open(absent,"unknown-original-edition",null,new RepeatPayload.Limits(1,1),()->{}));assertFalse(java.nio.file.Files.exists(absent));
    }
    @Test void currentPayloadRejectsAnUnusedPythonProviderBeforeAnyAdmission()throws Exception{
        Path home=java.nio.file.Files.createDirectory(directory.resolve("home"));
        IOException failure=assertThrows(IOException.class,()->RepeatPayload.open(home,RepeatRecipe.CURRENT,directory.resolve("python"),new RepeatPayload.Limits(1073741824,4096),()->{}));
        if(linux())assertTrue(failure.getMessage().contains("unused interpreter"),failure.toString());else assertInstanceOf(RepeatPayload.Unavailable.class,failure);
    }
    @Test void contextOwnsImmutableFileAndExternalProviderMaps(){
        var values=new HashMap<String,RepeatPayload.Fingerprint>();values.put("original",new RepeatPayload.Fingerprint(1,"0".repeat(64)));var context=context(directory,values);values.clear();assertEquals(1,context.files().size());assertThrows(UnsupportedOperationException.class,()->context.files().clear());assertThrows(UnsupportedOperationException.class,()->context.external().clear());
    }
    @Test void environmentUsesPrivateProvidersOwnedHomeAndDisabledBlastReporting()throws Exception{
        var context=context(directory,Map.of());Path work=directory.resolve("new-run");
        if(!linux()){assertThrows(RepeatPayload.Unavailable.class,()->context.environment(work));return;}
        var env=context.environment(work);assertEquals(work.toString(),env.get("HOME"));assertEquals(work.toString(),env.get("TMPDIR"));assertEquals(directory.resolve("bin").toString(),env.get("RMBLAST_DIR"));assertEquals("",env.get("FAMDB_DIR"));assertEquals("",env.get("FAMDB_DATA_DIR"));assertEquals("false",env.get("BLAST_USAGE_REPORT"));assertEquals("false",env.get("NCBI_USAGE_REPORT"));assertEquals("1",env.get("OPENBLAS_NUM_THREADS"));assertFalse(env.containsKey("PERL5LIB"));assertFalse(env.containsKey("PYTHONPATH"));
    }
}
