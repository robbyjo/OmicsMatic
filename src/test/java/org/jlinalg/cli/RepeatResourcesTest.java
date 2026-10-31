/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatResourcesTest {
    @TempDir Path directory;
    private static byte[] configuration(){return "prefix\n'LIBDIR' => {\n 'description' => 'library directory',\n 'value' => ''\n },\nsuffix\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    @Test void historicalWorkingLibraryConfigurationChangesOnlyTheEmptyValue()throws Exception{
        Path library=Path.of("/tmp/owned-resources/repeatmasker/Libraries");
        if(!RepeatPayloadTest.linux()){assertThrows(RepeatPayload.Unavailable.class,()->RepeatResources.configureLibraryDirectory(configuration(),library));return;}
        byte[] original=configuration();String text=new String(original,java.nio.charset.StandardCharsets.UTF_8),expected=text.replace("'value' => ''","'value' => '"+library+"'");
        assertEquals(expected,new String(RepeatResources.configureLibraryDirectory(original,library),java.nio.charset.StandardCharsets.UTF_8));assertArrayEquals(configuration(),original);
    }
    @Test void missingDuplicateAndAlreadyConfiguredLibraryBlocksAreRefused()throws Exception{
        Path library=Path.of("/tmp/owned/Libraries");String original=new String(configuration(),java.nio.charset.StandardCharsets.UTF_8);
        for(String text:java.util.List.of("missing LIBDIR",original+original,original.replace("'value' => ''","'value' => '/old/path'")))assertThrows(IOException.class,()->RepeatResources.configureLibraryDirectory(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),library));
    }
    @Test void originalLibraryConfigurationCannotIntroduceShellOrPerlMetacharacters(){
        for(String path:java.util.List.of("/tmp/with space/Libraries","/tmp/quote'/Libraries","/tmp/$value/Libraries"))assertThrows(RepeatPayload.Unavailable.class,()->RepeatResources.configureLibraryDirectory(configuration(),Path.of(path)));
    }
    @Test void unsupportedOriginalRunPathsFailBeforeResourceOwnership()throws Exception{
        var context=RepeatPayloadTest.context(directory.resolve("home"),Map.of());Path work=directory.resolve("unsupported α run");assertThrows(RepeatPayload.Unavailable.class,()->RepeatResources.prepare(context,work,new RepeatPayload.Limits(1,1),()->{}));assertFalse(Files.exists(work));
    }
    @Test void resourcesCannotBePreparedInsideTheSealedPayload()throws Exception{
        Path home=Files.createDirectory(directory.resolve("home")),work=home.resolve("resources");var context=RepeatPayloadTest.context(home,Map.of());assertThrows(IOException.class,()->RepeatResources.prepare(context,work,new RepeatPayload.Limits(1073741824,4096),()->{}));assertFalse(Files.exists(work));
    }
    @Test void preparedResourceMembersAreImmutableAndLibraryOverrideIsAnalysisOwned()throws Exception{
        Path work=directory.resolve("resources"),source=work.resolve("repeatmasker");var context=RepeatPayloadTest.context(directory.resolve("home"),Map.of());var values=new HashMap<String,RepeatResources.Member>();values.put("repeatmasker/original",new RepeatResources.Member("repeatmasker/original","COPY",new RepeatPayload.Fingerprint(1,"0".repeat(64))));var prepared=new RepeatResources.Prepared(context,work,source,values,"0".repeat(64),new RepeatPayload.Limits(1073741824,4096));values.clear();assertEquals(1,prepared.members().size());assertThrows(UnsupportedOperationException.class,()->prepared.members().clear());
        if(RepeatPayloadTest.linux())assertEquals(source.resolve("Libraries").toString(),prepared.environment(directory.resolve("run")).get("LIBDIR"));else assertThrows(RepeatPayload.Unavailable.class,()->prepared.environment(directory.resolve("run")));
    }
}
