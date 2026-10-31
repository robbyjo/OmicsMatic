/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatBundleTest {
    @TempDir Path root;
    @Test void inventoryCountsUtf8BytesDirectoriesAndEveryMember() throws Exception {
        Files.createDirectory(root.resolve("sub"));Files.writeString(root.resolve("a"),"β");Files.writeString(root.resolve("sub/b"),"abc");
        var inventory=RepeatBundle.inventory(root,new RepeatBundle.Limits(65536,8),true,()->{});assertEquals(5,inventory.bytes());assertEquals(4,inventory.members());assertEquals(2,inventory.hashes().size());assertTrue(inventory.hashes().containsKey("sub/b"));
    }
    @Test void exactByteCapacityAndPrewriteDiagnosticReservesCannotOverrun() throws Exception {
        var limits=new RepeatBundle.Limits(131072,8);RepeatBundle.write(root,"payload",new byte[65536],limits,65536,6,()->{});
        assertThrows(IOException.class,()->RepeatBundle.write(root,"more",new byte[]{1},limits,65536,0,()->{}));assertFalse(Files.exists(root.resolve("more")));assertEquals(65536,Files.size(root.resolve("payload")));
        Files.write(root.resolve("one-more"),new byte[]{1});assertThrows(IOException.class,()->RepeatBundle.inventory(root,new RepeatBundle.Limits(65536,8),true,()->{}));
    }
    @Test void metadataPathsAndPreexistingFilesCannotEscapeOrOverwrite() throws Exception {
        var limits=new RepeatBundle.Limits(65536,8);Files.writeString(root.resolve("keep"),"old evidence");assertThrows(IOException.class,()->RepeatBundle.write(root,"keep",new byte[]{1},limits,0,0,()->{}));assertEquals("old evidence",Files.readString(root.resolve("keep")));
        for(String name:List.of("../elsewhere","sub/data","", "a\\b"))assertThrows(IOException.class,()->RepeatBundle.write(root,name,new byte[]{1},limits,0,0,()->{}));
    }
    @Test void completionFlagsDistinguishEveryNativeOperationAndForbidSearch() throws Exception {
        for(var operation:List.of(RepeatRecipe.Operation.CHECK,RepeatRecipe.Operation.MASK,RepeatRecipe.Operation.AUDIT)) {
            var flags=RepeatBundle.flags(operation);assertEquals(1,flags.values().stream().filter(Boolean::booleanValue).count());assertEquals(false,flags.get("upstream_search_completed"));assertEquals(false,flags.get("biological_absence_certified"));assertFalse(RepeatBundle.scope(operation).isBlank());
        }
        assertThrows(IOException.class,()->RepeatBundle.flags(RepeatRecipe.Operation.SEARCH));assertThrows(IOException.class,()->RepeatBundle.scope(RepeatRecipe.Operation.SEARCH));
    }
    @Test void jsonSnapshotDecoderRejectsDuplicateKeysAndMalformedUtf8() {
        assertThrows(IOException.class,()->RepeatBundle.object("{\"a\":1,\"a\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertThrows(IOException.class,()->RepeatBundle.object(new byte[]{'{','"',(byte)0xff,'"',':','1','}'}));
    }
    @Test void missingCompletionAndForgedOriginalCompletionAreRefusedReadOnly() throws Exception {
        assertThrows(IOException.class,()->RepeatBundle.verify(root,()->{}));try(var files=Files.list(root)){assertEquals(0,files.count());}
        completed();var marker=copy(RepeatBundle.object(root.resolve("out/COMPLETED.json"),65536));var flags=copy((Map<?,?>)marker.get("completion_flags"));flags.put("upstream_search_completed",true);marker.put("completion_flags",flags);Files.write(root.resolve("out/COMPLETED.json"),RepeatBundle.json(marker));assertThrows(IOException.class,()->RepeatBundle.verify(root.resolve("out"),()->{}));
    }
    @Test void operationFilesRemainMandatoryEvenIfAnUnsignedManifestIsRecomputed() throws Exception {
        completed();Path output=root.resolve("out");Files.delete(output.resolve("native-mask/native-masked.fa"));var manifest=copy(RepeatBundle.object(output.resolve("manifest.json"),2097152));var files=copy((Map<?,?>)manifest.get("output_sha256"));files.remove("native-mask/native-masked.fa");manifest.put("output_sha256",files);byte[] raw=RepeatBundle.json(manifest);Files.write(output.resolve("manifest.json"),raw);var marker=copy(RepeatBundle.object(output.resolve("COMPLETED.json"),65536));marker.put("manifest_sha256",AdmixtureInputs.digest(raw));Files.write(output.resolve("COMPLETED.json"),RepeatBundle.json(marker));IOException failure=assertThrows(IOException.class,()->RepeatBundle.verify(output,()->{}));assertTrue(failure.getMessage().contains("operation-specific"),failure.toString());
    }
    private void completed() throws Exception {
        RepeatTestRecipes.inputs(root);Path recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest());var sink=new java.io.PrintStream(java.io.OutputStream.nullOutputStream());assertEquals(0,RepeatToolsCli.run(new String[]{"--recipe",recipe.toString(),"--out",root.resolve("out").toString()},sink,sink));
    }
    private static Map<String,Object> copy(Map<?,?> source) { var copy=new LinkedHashMap<String,Object>();source.forEach((key,value)->copy.put(key.toString(),value));return copy; }
}
