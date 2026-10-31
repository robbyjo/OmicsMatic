/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
final class RepeatInputsTest {
    @TempDir Path parent;
    private int serial;
    private Path fixture()throws IOException{Path root=Files.createDirectory(parent.resolve("fixture-"+serial++));RepeatTestRecipes.inputs(root);return root;}
    private RepeatInputs.Prepared prepare(Path root,Map<String,Object> m)throws IOException{return RepeatInputs.prepare(RepeatRecipe.read(RepeatTestRecipes.write(root,m)),root.resolve("prepared"),()->{});}
    private static byte[] gzip(byte[] bytes)throws IOException{var out=new ByteArrayOutputStream();try(var gz=new GZIPOutputStream(out)){gz.write(bytes);}return out.toByteArray();}
    @Test void nativePreparationRetainsSnapshotsAllRowsAndExactLogicalFasta()throws Exception{
        Path root=fixture();byte[] query=Files.readAllBytes(root.resolve("query.fa"));var p=prepare(root,RepeatTestRecipes.nativeRequest());p.verify(()->{});
        assertEquals(3,p.snapshots().size());assertArrayEquals(query,Files.readAllBytes(p.snapshots().stream().filter(s->s.role().equals("query")).findFirst().orElseThrow().copy()));assertEquals(p.query().original().sequences(),p.query().staged().sequences());assertEquals(1,p.annotationValidation().selectedRows());
        var ledger=(Map<?,?>)SimpleJson.parse(Files.readString(p.work().resolve("preparation.json")));assertEquals(false,ledger.get("upstream_analysis_completed"));assertFalse(Files.exists(p.work().resolve("COMPLETED.json")));
    }
    @Test void plainAndGzipAuditRequestsMatchWholeMasksAndNegativeQueries()throws Exception{
        for(boolean compress:List.of(false,true)){Path root=fixture();var m=RepeatTestRecipes.nativeRequest();m.put("operation","AUDIT");m.put("masked",RepeatTestRecipes.source("masked.fa","PLAIN_UTF8"));
            if(compress)for(String role:List.of("query","annotations","masked")){var source=RepeatTestRecipes.map(m,role);Path path=root.resolve(source.get("path").toString());Files.write(path,gzip(Files.readAllBytes(path)));source.put("encoding","GZIP_UTF8");}
            var p=prepare(root,m);assertEquals(12,p.suppliedMaskAudit().comparedBases());assertEquals(4,p.suppliedMaskAudit().coveredBases());assertEquals(0,p.suppliedMaskAudit().sequences().get(1).coveredBases());p.verify(()->{});
        }
    }
    @Test void explicitOrdinalUppercaseStagingRetainsUnicodePathsHeadersAndOriginalCase()throws Exception{
        Path root=fixture();String name="入力 genome.fa";Files.writeString(root.resolve(name),">long.seq\tβ original\nAcgtNn\n>"+"A".repeat(30)+"\nTTaa\n");Files.writeString(root.resolve("library.fa"),">REP#DNA/hAT catalog\nAcgt\n");
        var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");m.put("profile",RepeatRecipe.CURRENT);m.put("query",RepeatTestRecipes.source(name,"PLAIN_UTF8"));m.put("staging_policy","ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA");var p=prepare(root,m);
        assertEquals("Q0000000001",p.query().staged().sequences().get(0).id());assertEquals("long.seq\tβ original",p.query().mappings().get(0).originalHeader());assertTrue(Files.readString(p.query().path()).contains("ACGTNN"));assertTrue(Files.readString(p.library().path()).contains(">REP#DNA/hAT catalog\nACGT"));assertTrue(Files.readString(root.resolve(name)).contains("AcgtNn"));p.verify(()->{});
    }
    @Test void exactSearchPolicyRefusesSilentOriginalParserTransformations()throws Exception{
        for(String text:List.of(">a.seq\nACGT\n",">"+"a".repeat(51)+"\nACGT\n",">a description "+"A".repeat(25)+"\nACGT\n",">a  two spaces\nACGT\n",">β\nACGT\n",">a\nacgt\n",">a\nACGU\n")){
            Path root=fixture();Files.writeString(root.resolve("query.fa"),text);var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");assertThrows(IOException.class,()->prepare(root,m));assertEquals(text,Files.readString(root.resolve("query.fa")));assertTrue(Files.readString(root.resolve("prepared/PREPARATION_FAILED.txt")).contains("Required action"));
        }
    }
    @Test void libraryBaseNameCollisionsAreNotHiddenByDifferentClassificationTags()throws Exception{
        Path root=fixture();Files.writeString(root.resolve("library.fa"),">REP#DNA/hAT\nACGT\n>REP#LINE/L1\nGTAC\n");var m=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");assertThrows(IOException.class,()->prepare(root,m));assertTrue(Files.exists(root.resolve("prepared/PREPARATION_FAILED.txt")));
    }
    @Test void excludedAnnotationsStillRequireKnownQueryBoundsAndHints()throws Exception{
        for(String row:List.of("50 0 0 0 Q 2 5 (3) + REP DNA/hAT 1 4 (0) 1\n","50 0 0 0 q 2 9 (0) + REP DNA/hAT 1 8 (0) 1\n","50 0 0 0 q 2 5 (2) + REP DNA/hAT 1 4 (0) 1\n")){
            Path root=fixture();Files.writeString(root.resolve("hits.out"),row);var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"selection").put("classes","EXACT");RepeatTestRecipes.map(m,"selection").put("class_names",List.of("absent"));assertThrows(IOException.class,()->prepare(root,m));
        }
    }
    @Test void sourceSnapshotStageAndUnexpectedMemberMutationAllFailVerification()throws Exception{
        for(String role:List.of("original","snapshot","stage","extra")){Path root=fixture();var p=prepare(root,RepeatTestRecipes.nativeRequest());
            Path path=role.equals("original")?root.resolve("query.fa"):role.equals("snapshot")?p.snapshots().stream().filter(s->s.role().equals("query")).findFirst().orElseThrow().copy():role.equals("stage")?p.query().path():p.work().resolve("unexpected.txt");Files.writeString(path,"changed");assertThrows(IOException.class,()->p.verify(()->{}));}
    }
    @Test void existingOutputsAndProtectedSourcesAreNeverOverwritten()throws Exception{
        Path root=fixture();var spec=RepeatRecipe.read(RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()));Path output=Files.createDirectory(root.resolve("existing"));Files.writeString(output.resolve("keep.txt"),"untouched");assertThrows(IOException.class,()->RepeatInputs.prepare(spec,output,()->{}));assertEquals("untouched",Files.readString(output.resolve("keep.txt")));assertFalse(Files.exists(output.resolve("PREPARATION_FAILED.txt")));
        var request=RepeatTestRecipes.nativeRequest();request.put("query",RepeatTestRecipes.source("existing/keep.txt","PLAIN_UTF8"));var protectedSpec=RepeatRecipe.read(RepeatTestRecipes.write(root,request));assertThrows(IOException.class,()->RepeatInputs.prepare(protectedSpec,output,()->{}));assertEquals("untouched",Files.readString(output.resolve("keep.txt")));
    }
    @Test void workspaceAndFileCapacityFailuresRetainBoundedProminentDiagnostics()throws Exception{
        for(boolean files:List.of(false,true)){Path root=fixture();var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"limits").put(files?"file_count":"workspace_bytes",files?8:65536);assertThrows(IOException.class,()->prepare(root,m));Path work=root.resolve("prepared");assertTrue(Files.readString(work.resolve("PREPARATION_FAILED.txt")).contains("FAIL2"));assertTrue(Files.readString(work.resolve("PREPARATION_FAILED.txt")).contains("Required action"));
            try(var paths=Files.walk(work)){assertTrue(paths.filter(Files::isRegularFile).mapToLong(path->{try{return Files.size(path);}catch(IOException error){throw new java.io.UncheckedIOException(error);}}).sum()<=((Number)RepeatTestRecipes.map(m,"limits").get("workspace_bytes")).longValue());}
            if(files)try(var paths=Files.walk(work)){assertTrue(paths.count()<=8);}
        }
    }
    @Test void cancellationAfterOwnershipRetainsIncompletePreparationAndSourceBytes()throws Exception{
        Path root=fixture();byte[] before=Files.readAllBytes(root.resolve("query.fa"));var spec=RepeatRecipe.read(RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()));var calls=new AtomicInteger();assertThrows(CancellationException.class,()->RepeatInputs.prepare(spec,root.resolve("prepared"),()->{if(calls.incrementAndGet()>7)throw new CancellationException("controlled stop");}));assertArrayEquals(before,Files.readAllBytes(root.resolve("query.fa")));assertTrue(Files.exists(root.resolve("prepared/PREPARATION_FAILED.txt")));assertFalse(Files.exists(root.resolve("prepared/preparation.json")));
    }
    @Test void gzipCrcAndPhysicalEofFailuresCannotProduceAPreparedResult()throws Exception{
        for(String role:List.of("query","annotations","masked")){Path root=fixture();var m=RepeatTestRecipes.nativeRequest();m.put("operation","AUDIT");m.put("masked",RepeatTestRecipes.source("masked.fa","PLAIN_UTF8"));var source=RepeatTestRecipes.map(m,role);Path path=root.resolve(source.get("path").toString());byte[] bad=gzip(Files.readAllBytes(path));bad[bad.length-8]^=1;Files.write(path,bad);source.put("encoding","GZIP_UTF8");assertThrows(IOException.class,()->prepare(root,m));assertArrayEquals(bad,Files.readAllBytes(path));assertTrue(Files.exists(root.resolve("prepared/PREPARATION_FAILED.txt")));}
    }
    @Test void recipeMutationAndExcessiveDeclaredHeapBudgetFailBeforeOwnership()throws Exception{
        Path root=fixture();var spec=RepeatRecipe.read(RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()));Files.writeString(spec.recipe(),"{}");assertThrows(IOException.class,()->RepeatInputs.prepare(spec,root.resolve("changed-recipe"),()->{}));assertFalse(Files.exists(root.resolve("changed-recipe")));
        var m=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(m,"limits").put("retained_bytes",1099511627776L);var large=RepeatRecipe.read(RepeatTestRecipes.write(root,m));assertThrows(IOException.class,()->RepeatInputs.prepare(large,root.resolve("excessive-heap"),()->{}));assertFalse(Files.exists(root.resolve("excessive-heap")));
    }
}
