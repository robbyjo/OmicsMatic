/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class RepeatPublicSearchTest {
    @TempDir Path root;
    private int serial;
    private RepeatInputs.Prepared prepared(boolean mapped,boolean gzip)throws Exception{
        Path source=Files.createDirectory(root.resolve("source-"+serial++));RepeatTestRecipes.inputs(source);
        String query=mapped?">β.seq 日本 original\nAcgtNn\n>second with description\nTTaa\n":">q original\nACGTNN\n>second description\nTTAA\n";
        Files.writeString(source.resolve("query.fa"),query);
        if(mapped)Files.writeString(source.resolve("library.fa"),">REP#DNA/hAT declared library\nAcgTaCgT\n");
        var request=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");
        if(mapped)request.put("staging_policy","ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA");
        if(gzip){byte[] raw=Files.readAllBytes(source.resolve("query.fa"));try(var out=new GZIPOutputStream(Files.newOutputStream(source.resolve("query.fa")))){out.write(raw);}RepeatTestRecipes.map(request,"query").put("encoding","GZIP_UTF8");}
        Path recipe=RepeatTestRecipes.write(source,request);return RepeatInputs.prepare(RepeatRecipe.read(recipe),root.resolve("prepared-"+serial++),()->{});
    }
    @Test void completeMappedReplayPreservesUnicodeSourceAndChecksEveryUppercaseBase()throws Exception{
        for(boolean gzip:List.of(false,true)){
            var input=prepared(true,gzip);var spec=input.spec();
            var replay=RepeatStageEvidence.verifyFasta(spec.query(),input.query().path(),spec,true,()->{});
            assertEquals(input.query().mappings(),replay.mappings());assertEquals(10,replay.staged().residues());
            assertEquals("Q0000000001",replay.staged().sequences().get(0).id());
            assertTrue(replay.original().sequences().get(0).header().contains("日本"));
            assertEquals(RepeatStageEvidence.mappingValues(replay.mappings()),RepeatBundle.object(input.work().resolve("preparation.json"),4194304).get("query_mappings"));
            var library=RepeatStageEvidence.verifyFasta(spec.library(),input.library().path(),spec,false,()->{});
            assertEquals("REP#DNA/hAT",library.staged().sequences().get(0).id());assertNotEquals(library.original().sequences().get(0).sequenceSha256(),library.staged().sequences().get(0).sequenceSha256());
        }
    }
    @Test void replayRejectsMissingExtraReorderedAndChangedRecordsOrBases()throws Exception{
        for(String change:List.of("missing","extra","order","header","base","case")){
            var input=prepared(true,false);Path staged=input.query().path();String original=Files.readString(staged);
            String changed=switch(change){
                case "missing"->">Q0000000001\nACGTNN\n";
                case "extra"->original+">Q0000000003\nA\n";
                case "order"->">Q0000000002\nTTAA\n>Q0000000001\nACGTNN\n";
                case "header"->original.replace("Q0000000001","Q0000000009");
                case "base"->original.replace("ACGTNN","ACGANN");
                case "case"->original.replace("ACGTNN","aCGTNN");
                default->throw new AssertionError();};
            Files.writeString(staged,changed);assertThrows(IOException.class,()->RepeatStageEvidence.verifyFasta(input.spec().query(),staged,input.spec(),true,()->{}),change);
        }
    }
    @Test void gzipReplayUsesDecodedStageCapacityRatherThanCompressedInputBytes()throws Exception{
        Path source=Files.createDirectory(root.resolve("gzip"));RepeatTestRecipes.inputs(source);
        byte[] raw=(">q\n"+("ACGT".repeat(20)+"\n").repeat(250)).getBytes(StandardCharsets.UTF_8);
        try(var stream=new GZIPOutputStream(Files.newOutputStream(source.resolve("query.fa")))){stream.write(raw);}
        var request=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");RepeatTestRecipes.map(request,"query").put("encoding","GZIP_UTF8");RepeatTestRecipes.map(request,"limits").put("input_bytes",1024);
        var spec=RepeatRecipe.read(RepeatTestRecipes.write(source,request));var input=RepeatInputs.prepare(spec,root.resolve("decoded"),()->{});
        assertTrue(Files.size(input.query().path())>spec.limits().inputBytes());
        assertEquals(20000,RepeatStageEvidence.verifyFasta(spec.query(),input.query().path(),spec,true,()->{}).staged().residues());
    }
    @Test void exactReplayRejectsUndeclaredCaseAndUracilNormalization()throws Exception{
        for(String source:List.of(">q original\nAcGTNN\n>second description\nTTAA\n",">q original\nACGTUN\n>second description\nTTAA\n")){
            var input=prepared(false,false);Files.writeString(input.spec().query().path(),source);
            assertThrows(IOException.class,()->RepeatStageEvidence.verifyFasta(input.spec().query(),input.query().path(),input.spec(),true,()->{}));
        }
    }
    @Test void bothHostCapturesAndFinalEvidenceAreReservedBeforeAnyOriginalProcess()throws Exception{
        var used=new RepeatBundle.Inventory(4096,8,Map.of());
        assertThrows(IOException.class,()->RepeatSearch.reserveHosts(used,new RepeatBundle.Limits(1073741824,100000)));
        assertThrows(IOException.class,()->RepeatSearch.reserveHosts(used,new RepeatBundle.Limits(68719476736L,65536)));
        RepeatSearch.reserveHosts(used,new RepeatBundle.Limits(68719476736L,100000));
    }
    @Test void rebudgetCannotChangeScientificIdentityOrIncreaseAdmittedCapacity()throws Exception{
        var input=prepared(false,false);var a=input.spec();var limits=a.limits();
        var reduced=new RepeatRecipe.Limits(limits.fasta(),limits.annotations(),limits.inputBytes(),limits.indexBytes(),limits.retainedBytes(),limits.workspaceBytes()-65536,limits.fileCount()-8);
        var good=new RepeatRecipe.Spec(a.recipe(),a.recipeSha256(),a.resourceRoot(),a.operation(),a.mode(),a.profile(),a.assembly(),a.review(),a.query(),a.library(),a.annotations(),a.masked(),a.species(),a.staging(),a.mask(),a.selection(),a.parameters(),reduced,a.process());
        assertEquals(input.query(),RepeatInputs.rebudget(input,good).query());
        var wrong=new RepeatRecipe.Spec(a.recipe(),a.recipeSha256(),a.resourceRoot(),a.operation(),a.mode(),RepeatRecipe.CURRENT,a.assembly(),a.review(),a.query(),a.library(),a.annotations(),a.masked(),a.species(),a.staging(),a.mask(),a.selection(),a.parameters(),reduced,a.process());
        assertThrows(IOException.class,()->RepeatInputs.rebudget(input,wrong));
        assertThrows(IOException.class,()->RepeatInputs.rebudget(RepeatInputs.rebudget(input,good),a));
    }
    @Test void closedAdmissionOptionsRejectUnknownFieldsAndMissingExplicitProvider()throws Exception{
        var err=new ByteArrayOutputStream();Path options=root.resolve("admit.json");
        for(Map<String,Object> values:List.of(new LinkedHashMap<String,Object>(Map.of("profile",RepeatRecipe.CURRENT,"payload_home",root.toString(),"out",root.resolve("new").toString())),new LinkedHashMap<String,Object>(Map.of("profile",RepeatRecipe.CURRENT,"payload_home",root.toString(),"out",root.resolve("new").toString(),"python","/usr/bin/python3","extra","flag")))){
            Files.write(options,RepeatBundle.json(values));assertEquals(2,RepeatAdmissionCli.run(options,System.out,new PrintStream(err,true,StandardCharsets.UTF_8),()->{}));assertFalse(Files.exists(root.resolve("new")));
        }
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("FAIL2"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("Required action"));
    }
    @Test void nativeCompletionCannotBeCertifiedAsOriginalSearch()throws Exception{
        Path source=Files.createDirectory(root.resolve("native"));RepeatTestRecipes.inputs(source);Path recipe=RepeatTestRecipes.write(source,RepeatTestRecipes.nativeRequest()),out=root.resolve("native-result");
        assertEquals(0,RepeatToolsCli.run(new String[]{"--recipe",recipe.toString(),"--out",out.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
        var before=RepeatBundle.inventory(out,new RepeatBundle.Limits(2097152,64),true,()->{});
        assertThrows(IOException.class,()->RepeatOriginalBundle.verify(out,()->{}));assertEquals(before,RepeatBundle.inventory(out,new RepeatBundle.Limits(2097152,64),true,()->{}));
    }
    @Test void interruptedOriginalEvidenceCannotBeBlessedByARehashedManifest()throws Exception{
        Files.writeString(root.resolve("ORIGINAL_STAGES_INTERRUPTED.txt"),"retained interrupted attempt");var capacity=new RepeatBundle.Limits(1048576,64);
        Files.write(root.resolve("manifest.json"),RepeatBundle.json(Map.of("schema_version",1,"tool",RepeatOriginalBundle.TOOL,"operation","SEARCH","directory",root,"scope",RepeatSearch.SCOPE,"completion_flags",RepeatSearch.flags(),"workspace_bytes",capacity.bytes(),"member_limit",capacity.members(),"managed_tree",RepeatOwnedTree.snapshot(root,capacity,Set.of("manifest.json","COMPLETED.json"),()->{}))));
        Files.write(root.resolve("COMPLETED.json"),RepeatBundle.json(Map.of("schema_version",1,"tool",RepeatOriginalBundle.TOOL,"operation","SEARCH","scope",RepeatSearch.SCOPE,"completion_flags",RepeatSearch.flags(),"manifest_sha256",RepeatInputs.hash(root.resolve("manifest.json"),1048576,()->{}))));
        assertTrue(assertThrows(IOException.class,()->RepeatOriginalBundle.verify(root,()->{})).getMessage().contains("Failed/interrupted"));
    }
}
