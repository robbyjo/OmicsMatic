/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.RepeatAnnotations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatToolsCliTest {
    @TempDir Path temporary;
    private int serial;
    private record Call(int code,String stdout,String stderr) { }
    private Path fixture() throws IOException { Path root=Files.createDirectory(temporary.resolve("fixture-"+serial++));RepeatTestRecipes.inputs(root);return root; }
    private Call call(String...args) { return call(()->{},args); }
    private Call call(Runnable check,String...args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=RepeatToolsCli.run(args,new PrintStream(out,true,StandardCharsets.US_ASCII),new PrintStream(err,true,StandardCharsets.US_ASCII),check);
        return new Call(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private static Map<?,?> read(Path path) throws IOException { return RepeatBundle.object(path,33554432); }
    private void completed(Path output,RepeatRecipe.Operation operation) throws Exception {
        var verified=RepeatBundle.verify(output,()->{});assertEquals(operation.name(),verified.operation());assertTrue(verified.verifiedFiles()>8);
        var marker=read(output.resolve("COMPLETED.json"));assertEquals(RepeatBundle.flags(operation),marker.get("completion_flags"));
        assertEquals(RepeatInputs.hash(output.resolve("manifest.json"),2097152,()->{}),marker.get("manifest_sha256"));
        assertEquals(0,call("--verify",output.toString()).code());
    }
    @Test void publicNativeMaskRunsEveryModeAndCompletesOnlyTheNativeScope() throws Exception {
        for(var mode:RepeatAnnotations.Mask.values()) {
            Path root=fixture();var request=RepeatTestRecipes.nativeRequest();request.put("mask",mode.name());Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");
            var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(0,actual.code(),actual.stderr());completed(output,RepeatRecipe.Operation.MASK);
            assertEquals(4L,read(output.resolve("results.json")).get("covered_bases"));assertTrue(Files.exists(output.resolve("native-mask/native-masked.fa")));assertTrue(actual.stderr().contains("WARNINGS.md"));
            assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("NATIVE MASK ONLY"));
        }
    }
    @Test void nativeAndKnownSearchChecksProduceOnlyStructuralCompletion() throws Exception {
        for(boolean search:List.of(false,true)) {
            Path root=fixture();var request=search?RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY"):RepeatTestRecipes.nativeRequest();request.put("operation","CHECK");Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");
            var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(0,actual.code(),actual.stderr());completed(output,RepeatRecipe.Operation.CHECK);
            assertFalse(Files.exists(output.resolve("native-mask")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("INPUT CHECK ONLY"));
        }
    }
    @Test void completeAuditAcceptsPlainAndGzipInputsAndExportsEveryQuery() throws Exception {
        for(boolean gzip:List.of(false,true)) {
            Path root=fixture();var request=RepeatTestRecipes.nativeRequest();request.put("operation","AUDIT");request.put("masked",RepeatTestRecipes.source("masked.fa","PLAIN_UTF8"));
            if(gzip)for(String role:List.of("query","annotations","masked")) {
                var source=RepeatTestRecipes.map(request,role);Path path=root.resolve(source.get("path").toString());byte[] bytes=Files.readAllBytes(path);
                try(var stream=new GZIPOutputStream(Files.newOutputStream(path))){stream.write(bytes);}source.put("encoding","GZIP_UTF8");
            }
            Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(0,actual.code(),actual.stderr());completed(output,RepeatRecipe.Operation.AUDIT);
            assertEquals(12L,read(output.resolve("results.json")).get("compared_bases"));assertEquals("query\tlength\tcovered_bases\tchanged_characters\nq\t8\t4\t4\nzero\t4\t0\t0\n",Files.readString(output.resolve("audit-coverage.tsv")));
        }
    }
    @Test void zeroRowsAndZeroSelectedRowsNeverCertifyBiologicalAbsence() throws Exception {
        for(boolean zeroRows:List.of(false,true)) {
            Path root=fixture();var request=RepeatTestRecipes.nativeRequest();
            if(zeroRows)Files.writeString(root.resolve("hits.out"),"There were no repetitive sequences detected in external-source\n");
            else {var selection=RepeatTestRecipes.map(request,"selection");selection.put("classes","EXACT");selection.put("class_names",List.of("absent"));}
            Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(0,actual.code(),actual.stderr());completed(output,RepeatRecipe.Operation.MASK);
            assertEquals(Files.readString(root.resolve("query.fa")),Files.readString(output.resolve("native-mask/native-masked.fa")));assertEquals(0L,read(output.resolve("results.json")).get("covered_bases"));
            assertEquals(false,((Map<?,?>)read(output.resolve("COMPLETED.json")).get("completion_flags")).get("biological_absence_certified"));
        }
    }
    @Test void invalidRecipeFastaAndSuppliedMaskFailWithRetainedDiagnostics() throws Exception {
        for(String kind:List.of("recipe","fasta","audit")) {
            Path root=fixture();var request=RepeatTestRecipes.nativeRequest();
            if(kind.equals("recipe"))request.put("extra_flags","--help");
            if(kind.equals("fasta"))Files.writeString(root.resolve("query.fa"),">q\nAC-GT\n");
            if(kind.equals("audit")){request.put("operation","AUDIT");request.put("masked",RepeatTestRecipes.source("masked.fa","PLAIN_UTF8"));Files.writeString(root.resolve("masked.fa"),">q description\nANNNNCGA\n>zero\nNNAC\n");}
            Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(2,actual.code(),actual.stderr());assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.exists(output.resolve("recipe-request.json")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("Required action"));
        }
    }
    @Test void unavailableOriginalSearchAndUnknownEditionAreProminentSkips() throws Exception {
        for(boolean unsupported:List.of(false,true)) {
            Path root=fixture();var request=RepeatTestRecipes.searchRequest("CUSTOM_LIBRARY");if(unsupported)request.put("profile","linux-unavailable-historical-edition");Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");
            var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(3,actual.code(),actual.stderr());assertTrue(actual.stderr().contains("SKIP3"));assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("SKIP3"));assertTrue(Files.exists(output.resolve("recipe-request.json")));
        }
    }
    @Test void preexistingOutputAndProtectedSourceDirectoriesAreNeverModified() throws Exception {
        Path root=fixture(),recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()),output=Files.createDirectory(root.resolve("existing"));Files.writeString(output.resolve("keep"),"retained evidence");
        assertEquals(2,call("--recipe",recipe.toString(),"--out",output.toString()).code());assertEquals("retained evidence",Files.readString(output.resolve("keep")));try(var paths=Files.list(output)){assertEquals(1,paths.count());}
        assertEquals(2,call("--recipe",recipe.toString(),"--out",root.toString()).code());assertFalse(Files.exists(root.resolve("WARNINGS.md")));assertEquals(">q description\nACGTACGT\n>zero\nNNAC\n",Files.readString(root.resolve("query.fa")));
    }
    @Test void utf8OptionsPreserveUnicodeNamesHeadersAndConsoleUnderAsciiStreams() throws Exception {
        Path root=fixture();Files.writeString(root.resolve("query.fa"),">β.seq\t日本 original\nAcgtNn\n");Files.writeString(root.resolve("hits.out"),"There were no repetitive sequences detected in external\n");Path recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()),output=root.resolve("native-α-日本"),options=root.resolve("options.json");Files.write(options,RepeatBundle.json(Map.of("recipe",recipe.toString(),"out",output.toString())));
        var actual=call("--options-file",options.toString());assertEquals(0,actual.code(),actual.stderr());completed(output,RepeatRecipe.Operation.MASK);assertTrue(actual.stdout().contains(output.toString()));assertEquals(Files.readString(root.resolve("query.fa")),Files.readString(output.resolve("native-mask/native-masked.fa")));
        assertArrayEquals(Files.readAllBytes(options),Files.readAllBytes(output.resolve("cli-options-source.json")));
        Path verification=root.resolve("verify-options.json");Files.write(verification,RepeatBundle.json(Map.of("directory",output.toString())));assertEquals(0,call("--verify-options-file",verification.toString()).code());
    }
    @Test void duplicateUnknownRecursiveAndNonstringCliOptionsAreRefusedBeforeOwnership() throws Exception {
        Path root=fixture(),recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()),output=root.resolve("result");
        for(String[] args:List.of(new String[]{"--recipe",recipe.toString(),"--out",output.toString(),"--out",output.toString()},new String[]{"--recipe",recipe.toString(),"--out",output.toString(),"--home",root.toString()},new String[]{"--recipe",recipe.toString()}))assertEquals(2,call(args).code());
        Path options=root.resolve("options.json");for(String text:List.of("{\"recipe\":1,\"out\":\"result\"}","{\"options-file\":\"another.json\"}","{\"recipe\":\"a\",\"recipe\":\"b\",\"out\":\"result\"}")){Files.writeString(options,text);assertEquals(2,call("--options-file",options.toString()).code());}
        for(String text:List.of("{\"directory\":1}","{\"directory\":\"a\",\"directory\":\"b\"}","{\"directory\":\"a\",\"extra\":\"b\"}")){Files.writeString(options,text);assertEquals(2,call("--verify-options-file",options.toString()).code());}
        assertFalse(Files.exists(output));
    }
    @Test void completeCapacityIncludesMetadataAndFailsWithoutExceedingTheDeclaredBudget() throws Exception {
        for(boolean members:List.of(false,true)) {
            Path root=fixture();var request=RepeatTestRecipes.nativeRequest();RepeatTestRecipes.map(request,"limits").put(members?"file_count":"workspace_bytes",members?16:65536);Path recipe=RepeatTestRecipes.write(root,request),output=root.resolve("result");var actual=call("--recipe",recipe.toString(),"--out",output.toString());assertEquals(2,actual.code());assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("Required action"));
            var used=RepeatBundle.inventory(output,new RepeatBundle.Limits(members?2097152:65536,members?16:64),false,()->{});assertTrue(used.bytes()<= (members?2097152:65536));assertTrue(used.members()<= (members?16:64));
        }
    }
    @Test void cancellationDuringNativeOutputRetainsPartialsAndHasNoCompletionMarker() throws Exception {
        Path root=fixture(),recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()),output=root.resolve("result");var actual=call(()->{if(Files.exists(output.resolve("native-mask/native-masked.fa")))throw new CancellationException("controlled stop");},"--recipe",recipe.toString(),"--out",output.toString());
        assertEquals(2,actual.code(),actual.stderr());assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.exists(output.resolve("native-mask/NATIVE_MASK_FAILED.txt")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("FAIL2"));
    }
    @Test void changedOptionsFileCannotCompleteEvenAfterAValidNativeComputation() throws Exception {
        Path root=fixture(),recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()),output=root.resolve("result"),options=root.resolve("options.json");Files.write(options,RepeatBundle.json(Map.of("recipe",recipe.toString(),"out",output.toString())));
        var actual=call(()->{if(Files.exists(output.resolve("prepared/preparation.json")))try{Files.writeString(options,"{}");}catch(IOException error){throw new UncheckedIOException(error);}},"--options-file",options.toString());
        assertEquals(2,actual.code(),actual.stderr());assertFalse(Files.exists(output.resolve("COMPLETED.json")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("options file changed"));
    }
    @Test void verifyDetectsChangedOrExtraFilesAndNeverMutatesTheBundle() throws Exception {
        for(boolean extra:List.of(false,true)) {
            Path root=fixture(),recipe=RepeatTestRecipes.write(root,RepeatTestRecipes.nativeRequest()),output=root.resolve("result");assertEquals(0,call("--recipe",recipe.toString(),"--out",output.toString()).code());
            Files.writeString(output.resolve(extra?"unexpected.txt":"native-mask/native-masked.fa"),"changed evidence");var before=RepeatBundle.inventory(output,new RepeatBundle.Limits(2097152,64),true,()->{});assertEquals(2,call("--verify",output.toString()).code());assertEquals(before,RepeatBundle.inventory(output,new RepeatBundle.Limits(2097152,64),true,()->{}));
        }
    }
    @Test void dispatcherHelpExposesNativeScopesCompletionAndFailureRecovery() throws Exception {
        var bytes=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int status=JLinAlgCli.run(new String[]{"repeat-tools","--help"},new PrintStream(bytes,true,StandardCharsets.US_ASCII),new PrintStream(err,true,StandardCharsets.US_ASCII));assertEquals(0,status,err.toString(StandardCharsets.UTF_8));String text=bytes.toString(StandardCharsets.UTF_8);assertTrue(text.contains("COMPLETED.json"));assertTrue(text.contains("SKIP3"));assertTrue(text.contains("FAIL2"));assertTrue(text.contains("--verify"));assertTrue(text.contains("Required action"));
    }
}
