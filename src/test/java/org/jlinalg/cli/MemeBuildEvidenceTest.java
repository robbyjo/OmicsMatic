/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemeBuildEvidenceTest {
    @TempDir Path temporary;
    static final MemeRuntimeFiles.Limits LIMITS=new MemeRuntimeFiles.Limits(67108864,268435456,10000);
    @Test void completeOriginalSourceBuildLogsAndExactPrefixPassPortableEvidenceAudit()throws Exception {
        Path root=extract();var context=MemeBuildEvidence.read(root,LIMITS,()->{});context.verify(()->{});assertEquals(884,context.installed().size());assertEquals("/var/tmp/omicsmatic-meme-build-w4cgkokf/home-5.5.9",context.prefix());assertTrue(context.inventory().containsKey("."));
        assertEquals(615,NextflowFiles.object(MemeRuntimeFiles.lock().get("original_C_and_H")).size());assertEquals(MemeRuntimeFiles.LOCK_SHA,NextflowFiles.sha(MemeRuntimeFiles.resource("/org/jlinalg/omics/meme-runtime-lock.json",new NextflowFiles.Pin(88598,MemeRuntimeFiles.LOCK_SHA))));
    }
    @ParameterizedTest @ValueSource(strings={"version","status","source_url","intended_use","scientific_analysis_run","runtime_admitted","project_integration_credited"})
    void copiedReportsCannotRedefineVersionSourceUseOrAdmission(String field)throws Exception {
        Path root=extract();var doc=mutable(root.resolve("COMPLETED.json"));doc.put(field,field.endsWith("_run")||field.endsWith("_admitted")||field.endsWith("_credited")?true:"changed");rewrite(root.resolve("COMPLETED.json"),doc);assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
    }
    @Test void sourcePinsBuilderAndArchiveAreIndependentlyBoundToBundledOriginalLock()throws Exception {
        Path root=extract();Path completion=root.resolve("COMPLETED.json");String original=Files.readString(completion);
        for(String field:java.util.List.of("original_C_and_H","script_pin","original_archive")){
            Files.writeString(completion,original);var doc=mutable(completion);if(field.equals("original_C_and_H")){var source=new LinkedHashMap<>(NextflowFiles.object(doc.get(field)));source.remove(source.keySet().iterator().next());doc.put(field,source);}else doc.put(field,Map.of("bytes",1,"sha256","0".repeat(64)));rewrite(completion,doc);assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
        }
    }
    @Test void completeBuildLogTamperingAndMissingLastVersionCannotPass()throws Exception {
        Path root=extract();Path log=root.resolve("make.log");byte[] original=Files.readAllBytes(log);Files.writeString(log,"changed");assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));Files.write(log,original);Files.delete(root.resolve("centrimo-version.log"));assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
    }
    @Test void retainedSourceManifestUnknownPayloadLinksAndStepChangesFail()throws Exception {
        Path root=extract();Path completion=root.resolve("COMPLETED.json");String original=Files.readString(completion);var doc=mutable(completion);doc.put("installed_symlinks",Map.of("bin/tomtom","../elsewhere"));rewrite(completion,doc);assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
        Files.writeString(completion,original);doc=mutable(completion);doc.put("steps",java.util.List.of());rewrite(completion,doc);assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
        Files.writeString(completion,original);Files.writeString(root.resolve("ORIGINAL-SOURCE-PINS.json"),"{}");assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
    }
    @Test void evidenceVerifyDetectsChangedBytesAndAddedEmptyDirectories()throws Exception {
        Path root=extract();var context=MemeBuildEvidence.read(root,LIMITS,()->{});Path extra=root.resolve("unreported");Files.createDirectory(extra);assertThrows(IOException.class,()->context.verify(()->{}));Files.delete(extra);context.verify(()->{});Files.writeString(root.resolve("configure.log"),"changed");assertThrows(IOException.class,()->context.verify(()->{}));
    }
    @Test void compiledPrefixCannotBeReplacedByAnArbitraryCopiedHome()throws Exception {
        var context=MemeBuildEvidence.read(extract(),LIMITS,()->{});Path copied=temporary.resolve("copied-home");Files.createDirectory(copied);assertTrue(assertThrows(IOException.class,()->MemeBuildEvidence.verifyPayload(context,copied,()->{})).getMessage().contains("Rebuild into the intended NEW prefix"));
    }
    @Test void wholeEvidenceCapacitiesAndCancellationNeverReturnACompleteContext()throws Exception {
        Path root=extract();for(var limits:java.util.List.of(new MemeRuntimeFiles.Limits(1,268435456,10000),new MemeRuntimeFiles.Limits(67108864,1,10000),new MemeRuntimeFiles.Limits(67108864,268435456,1)))assertThrows(IOException.class,()->MemeBuildEvidence.read(root,limits,()->{}));
        var calls=new AtomicInteger();assertThrows(CancellationException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{if(calls.incrementAndGet()==10)throw new CancellationException("controlled");}));
    }
    @Test void missingMetadataIsAnExplicitFailureAndNotANullPointer()throws Exception {
        Path root=extract();var doc=mutable(root.resolve("COMPLETED.json"));doc.remove("status");rewrite(root.resolve("COMPLETED.json"),doc);assertThrows(IOException.class,()->MemeBuildEvidence.read(root,LIMITS,()->{}));
    }
    private Path extract()throws IOException {
        Path root=temporary.resolve("build-evidence");Files.createDirectory(root);byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/cli/meme-build-evidence/original-build-evidence.zip")){assertNotNull(in);raw=in.readAllBytes();}assertEquals("34450edabb2aee7ab9a9699ea0717b659f2dd9a3865133d77d9eaefeecc9e709",NextflowFiles.sha(raw));
        try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry()){assertFalse(e.isDirectory());Path path=root.resolve(NextflowFiles.member(e.getName()));Files.createDirectories(path.getParent());Files.write(path,z.readAllBytes());}}return root;
    }
    private static Map<String,Object> mutable(Path path)throws IOException {var doc=new LinkedHashMap<String,Object>();for(var entry:NextflowFiles.object(NextflowFiles.json(path,2097152)).entrySet())doc.put((String)entry.getKey(),entry.getValue());return doc;}
    private static void rewrite(Path path,Object value)throws IOException {Files.writeString(path,RsemJson.json(value),StandardCharsets.UTF_8);}
}
