/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.jlinalg.genetics.RepeatAnnotations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatNativeMaskTest {
    @TempDir Path directory;
    private int serial;
    private Path fixture() throws IOException { Path root=Files.createDirectory(directory.resolve("fixture-"+serial++));RepeatTestRecipes.inputs(root);return root; }
    private static RepeatInputs.Prepared prepare(Path root,Map<String,Object> request) throws IOException {
        return RepeatInputs.prepare(RepeatRecipe.read(RepeatTestRecipes.write(root,request)),root.resolve("prepared"),()->{});
    }
    @Test void allModesProduceCompleteCoverageAndExplicitNativeProvenanceWithVerifiedHashes() throws Exception {
        for(var mode:RepeatAnnotations.Mask.values()) {
            Path root=fixture();var request=RepeatTestRecipes.nativeRequest();request.put("mask",mode.name());var input=prepare(root,request);
            var result=RepeatNativeMask.execute(input,root.resolve("result"),()->{});assertEquals(12,result.audit().comparedBases());assertEquals(4,result.audit().coveredBases());
            assertEquals("query\tlength\tcovered_bases\tchanged_characters\nq\t8\t4\t4\nzero\t4\t0\t0\n",Files.readString(result.coverage()));
            var ledger=(Map<?,?>)SimpleJson.parse(Files.readString(result.ledger()));assertEquals(false,ledger.get("upstream_search_completed"));assertEquals(false,ledger.get("external_annotation_completeness_certified"));assertEquals("complete-native-annotation-mask-only",ledger.get("scope"));
            for(var entry:result.outputSha256().entrySet())assertEquals(entry.getValue(),RepeatInputs.hash(Path.of(entry.getKey()),2097152,()->{}));
            var serialized=(Map<?,?>)SimpleJson.parse(RepeatJson.json(result));assertEquals(result.outputSha256(),serialized.get("outputSha256"));
            assertEquals(4,result.outputSha256().size());assertTrue(Files.readString(result.work().resolve("WARNINGS.md")).contains("Required action"));assertFalse(Files.exists(result.work().resolve("COMPLETED.json")));input.verify(()->{});
        }
    }
    @Test void zeroRowsWriteUnchangedNativeMaskWithNoBiologicalAbsenceClaim() throws Exception {
        Path root=fixture();Files.writeString(root.resolve("hits.out"),"There were no repetitive sequences detected in externally-supplied-query\n");var input=prepare(root,RepeatTestRecipes.nativeRequest());
        var result=RepeatNativeMask.execute(input,root.resolve("result"),()->{});assertEquals(Files.readString(input.query().path()),Files.readString(result.masked()));assertEquals(0,result.audit().annotationRows());assertEquals(0,result.audit().coveredBases());
        assertTrue(Files.readString(result.work().resolve("WARNINGS.md")).contains("biological absence are not inferred"));assertEquals(2,result.audit().sequences().size());
    }
    @Test void zeroSelectedRowsKeepOriginalRowsInTheAuditAndPreserveEveryQuery() throws Exception {
        Path root=fixture();var request=RepeatTestRecipes.nativeRequest();var selection=RepeatTestRecipes.map(request,"selection");selection.put("classes","EXACT");selection.put("class_names",List.of("absent"));var input=prepare(root,request);
        var result=RepeatNativeMask.execute(input,root.resolve("result"),()->{});assertEquals(1,result.audit().annotationRows());assertEquals(0,result.audit().selectedRows());assertEquals(0,result.audit().coveredBases());assertEquals(Files.readString(input.query().path()),Files.readString(result.masked()));
    }
    @Test void checkRequestsCannotBeMistakenForNativeMaskExecution() throws Exception {
        Path root=fixture();var request=RepeatTestRecipes.nativeRequest();request.put("operation","CHECK");var input=prepare(root,request);Path work=root.resolve("result");assertThrows(IOException.class,()->RepeatNativeMask.execute(input,work,()->{}));assertFalse(Files.exists(work));
    }
    @Test void combinedPreparedAndResultByteCapacityRetainsBoundedFailureDiagnostics() throws Exception {
        Path root=fixture();String bases="ACGT".repeat(20000);var text=new StringBuilder(">q\n");for(int i=0;i<bases.length();i+=60)text.append(bases,i,Math.min(i+60,bases.length())).append('\n');Files.writeString(root.resolve("query.fa"),text);Files.writeString(root.resolve("hits.out"),"There were no repetitive sequences detected in q\n");var request=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(request,"limits").put("workspace_bytes",262144);var input=prepare(root,request);Path work=root.resolve("result");
        assertThrows(IOException.class,()->RepeatNativeMask.execute(input,work,()->{}));assertTrue(Files.readString(work.resolve("NATIVE_MASK_FAILED.txt")).contains("Required action"));assertFalse(Files.exists(work.resolve("native-mask.json")));
        long total=0;for(Path folder:List.of(input.work(),work))try(var paths=Files.walk(folder)){for(Path path:paths.filter(Files::isRegularFile).toList())total+=Files.size(path);}assertTrue(total<=262144,total+" bytes");
    }
    @Test void combinedMemberCapacityIncludesPreparationResultAndDiagnosticSlot() throws Exception {
        Path root=fixture();var request=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(request,"limits").put("file_count",13);var input=prepare(root,request);Path work=root.resolve("result");assertThrows(IOException.class,()->RepeatNativeMask.execute(input,work,()->{}));assertTrue(Files.exists(work.resolve("NATIVE_MASK_FAILED.txt")));assertFalse(Files.exists(work.resolve("native-mask.json")));
        long members=0;for(Path folder:List.of(input.work(),work))try(var paths=Files.walk(folder)){members+=paths.count();}assertTrue(members<=13,members+" members");
    }
    @Test void preexistingAndProtectedOutputsAndChangedPreparationsAreRefused() throws Exception {
        Path root=fixture();var input=prepare(root,RepeatTestRecipes.nativeRequest());Path existing=Files.createDirectory(root.resolve("existing"));Files.writeString(existing.resolve("keep"),"retained evidence");assertThrows(IOException.class,()->RepeatNativeMask.execute(input,existing,()->{}));assertEquals("retained evidence",Files.readString(existing.resolve("keep")));assertFalse(Files.exists(existing.resolve("NATIVE_MASK_FAILED.txt")));
        Path nested=input.work().resolve("result");assertThrows(IOException.class,()->RepeatNativeMask.execute(input,nested,()->{}));assertFalse(Files.exists(nested));
        Files.writeString(input.query().path(),">q\nCCCC\n");Path work=root.resolve("changed");assertThrows(IOException.class,()->RepeatNativeMask.execute(input,work,()->{}));assertFalse(Files.exists(work));
    }
    @Test void cancellationDuringOwnedOutputRetainsPartialEvidenceWithoutResultLedger() throws Exception {
        Path root=fixture();var input=prepare(root,RepeatTestRecipes.nativeRequest());Path work=root.resolve("result");assertThrows(CancellationException.class,()->RepeatNativeMask.execute(input,work,()->{if(Files.exists(work.resolve("native-masked.fa")))throw new CancellationException("controlled stop");}));
        assertTrue(Files.readString(work.resolve("NATIVE_MASK_FAILED.txt")).contains("FAIL2"));assertFalse(Files.exists(work.resolve("native-mask.json")));assertTrue(Files.exists(work.resolve("native-masked.fa")));input.verify(()->{});
    }
}
