/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.map;
import static org.jlinalg.cli.GprofilerWorkflowContracts.object;

/** Protocol and refusal tests; original R fits are qualified separately. */
final class FusionWorkflowContractsTest {
    @TempDir Path root;

    private Map<String,Object> request() {
        return map("schema_version",1,"mode","RUN","profile",FusionWorkflowContracts.PROFILE,
                "operation","TRAIN","out","result","rscript","missing-Rscript",
                "r_libraries",List.of("library"),"expected_R","4.5.2",
                "expected_packages",map("here","1.0.2","optparse","1.8.2","jsonlite","2.0.0","glmnet","5.2"),
                "inputs",map("bfile","train","phenotype","phenotype","covariates","covariates"),
                "options",map("models","top1","noclean",true,"hsq_set",0.4),
                "binaries",map("plink","plink"),"seed",1);
    }

    private Map<String,Object> checked(Map<String,Object> raw)throws IOException {
        return FusionWorkflowContracts.validate(raw,root.toAbsolutePath().normalize());
    }
    private Map<String,Object> edit(Map<String,Object> parent,String key)throws IOException {
        var child=object(parent.get(key));parent.put(key,child);return child;
    }

    @Test void optInAdaptersDefaultToStrictAndMatrixBoundIsDeclared()throws Exception {
        var r=checked(request());var flags=object(r.get("runtime_compatibility"));
        assertEquals(Boolean.FALSE,flags.get("gcta_no_analysis_probe_exit1"));
        assertEquals(Boolean.FALSE,flags.get("gemma_working_directory_output"));
        assertEquals(16000000L,FusionWorkflowContracts.cap(r,"maximum_matrix_cells"));
        FusionWorkflowContracts.validateSaved(r);
    }

    @Test void gemmaRoutingRequiresAnActualSelectedOriginalGemmaModel()throws Exception {
        var r=request();r.put("runtime_compatibility",map("gemma_working_directory_output",true));
        assertThrows(IOException.class,()->checked(r));
        edit(r,"options").put("models","blup,top1");edit(r,"binaries").put("gemma","gemma");
        var c=checked(r);var args=FusionWorkflowRuntime.arguments(c);
        assertEquals("./gemma-entry.sh",args.get(args.indexOf("--PATH_gemma")+1));
        assertTrue(FusionWorkflowCli.compatibilityWarnings(c).contains("DECLARED GEMMA OUTPUT ROUTING"));
    }

    @Test void gctaProbeFlagCannotSilentlyAffectFixedHeritabilityTraining()throws Exception {
        var r=request();r.put("runtime_compatibility",map("gcta_no_analysis_probe_exit1",true));edit(r,"binaries").put("gcta","gcta");
        assertThrows(IOException.class,()->checked(r));edit(r,"options").remove("hsq_set");
        var c=checked(r);var args=FusionWorkflowRuntime.arguments(c);
        assertEquals("./gcta-entry.sh",args.get(args.indexOf("--PATH_gcta")+1));
        assertTrue(FusionWorkflowCli.compatibilityWarnings(c).contains("only for the startup probe"));
    }

    @Test void unknownCompatibilityAndNonBooleanValuesAreRejected()throws Exception {
        for(var bad:List.of(map("guess_runtime",true),map("gemma_working_directory_output","true"))){
            var r=request();r.put("runtime_compatibility",bad);assertThrows(IOException.class,()->checked(r));
        }
    }

    @Test void originalSourceUnknownProfileIsUnavailableRatherThanSubstituted() {
        var r=request();r.put("profile","fusion-any-current-version");assertThrows(FusionWorkflowContracts.Unavailable.class,()->checked(r));
    }

    @Test void predictionModelCannotBeSubstitutedBySusieColocalization()throws Exception {
        var r=request();edit(r,"options").put("models","susie");assertThrows(IOException.class,()->checked(r));
    }

    @Test void trainingRequiresEvidenceRetentionAndOriginalSeed()throws Exception {
        var r=request();edit(r,"options").put("noclean",false);assertThrows(IOException.class,()->checked(r));
        edit(r,"options").put("noclean",true);r.put("seed",2);assertThrows(IOException.class,()->checked(r));
    }

    @Test void originalArgumentOrderDoesNotDependOnMapOfIteration()throws Exception {
        var args=FusionWorkflowRuntime.arguments(checked(request()));
        assertTrue(args.indexOf("--pheno")<args.indexOf("--covar"));
        assertEquals(List.of("--bfile","workdata/train","--tmp","tmp/train","--pheno","workdata/phenotype","--covar","workdata/covariates"),args.subList(0,8));
    }

    @Test void literalToolPathsCannotBecomeShellSyntax()throws Exception {
        var r=request();edit(r,"binaries").put("plink","plink;touch-danger");var c=checked(r);
        assertThrows(IOException.class,()->FusionWorkflowRuntime.arguments(c));
    }

    @Test void matrixLimitsAreTypedAndHaveHardCeiling()throws Exception {
        for(Object value:List.of(0,-1,250000001,"4096",1.5)){
            var r=request();r.put("limits",map("maximum_matrix_cells",value));assertThrows(IOException.class,()->checked(r));
        }
    }

    @Test void savedCanonicalRequestCannotOmitNewDefaultsOrContainUnknownSettings()throws Exception {
        var first=checked(request());edit(first,"limits").remove("maximum_matrix_cells");assertThrows(IOException.class,()->FusionWorkflowContracts.validateSaved(first));
        var second=checked(request());second.put("guessed_setting",true);assertThrows(IOException.class,()->FusionWorkflowContracts.validateSaved(second));
    }

    @Test void recordedWindowsRequestsValidateOfflineOnEveryJvm()throws Exception {
        var raw=request();var canonical=FusionWorkflowContracts.recordedRequest(raw,"D:\\synthetic\\analysis");
        assertEquals("D:\\synthetic\\analysis\\result",canonical.get("out"));
        assertEquals("D:\\synthetic\\analysis\\train",object(canonical.get("inputs")).get("bfile"));
        FusionWorkflowContracts.validateSaved(canonical);
        assertTrue(FusionWorkflowFiles.same(canonical,FusionWorkflowContracts.recordedRequest(raw,"D:\\synthetic\\analysis")));
    }

    @Test void recordedPosixRequestsValidateOfflineOnEveryJvm()throws Exception {
        var raw=request();raw.put("out","../new-result");var canonical=FusionWorkflowContracts.recordedRequest(raw,"/synthetic/analysis");
        assertEquals("/synthetic/new-result",canonical.get("out"));FusionWorkflowContracts.validateSaved(canonical);
        assertEquals("/a/b\\literal",FusionWorkflowContracts.recordedPath("/a","b\\literal"));
    }

    @Test void recordedHostNormalizationMatchesTheRealHostPathRules()throws Exception {
        String base=root.toAbsolutePath().normalize().toString();
        for(String value:List.of("a","a/../b","../c","./d","x/y/../../z",root.resolve("absolute").toString(),Path.of(base).getRoot().toString())){
            Path path=Path.of(value);String expected=(path.isAbsolute()?path:Path.of(base).resolve(path)).toAbsolutePath().normalize().toString();
            assertEquals(expected,FusionWorkflowContracts.recordedPath(base,value),value);
        }
    }

    @Test void recordedDriveAndUncPathsKeepTheirRoots()throws Exception {
        assertEquals("D:\\x",FusionWorkflowContracts.recordedPath("D:\\a\\b","..\\..\\..\\x"));
        assertEquals("D:\\x",FusionWorkflowContracts.recordedPath("D:\\a","/x"));
        assertEquals("\\\\server\\share\\x",FusionWorkflowContracts.recordedPath("\\\\server\\share\\a","..\\x"));
        assertThrows(IOException.class,()->FusionWorkflowContracts.recordedPath("relative","x"));
        assertThrows(IOException.class,()->FusionWorkflowContracts.recordedPath("D:\\a","C:relative"));
    }

    @Test void canonicalScientificSettingsRemainBoundToTheOriginalRequest()throws Exception {
        var raw=request();var canonical=FusionWorkflowContracts.recordedRequest(raw,"/synthetic/analysis");
        edit(raw,"options").put("hsq_set",0.9);
        assertFalse(FusionWorkflowFiles.same(canonical,FusionWorkflowContracts.recordedRequest(raw,"/synthetic/analysis")));
    }

    @Test void originalIgnoredOmnibusOptionIsRejectedBeforeAnyRun()throws Exception {
        var r=request();r.put("operation","POSTPROCESS");r.put("inputs",map("association","association","sumstats","sumstats","ref_ld_prefix","ref.","chromosome","1","weights_base","weights"));
        r.put("binaries",map());r.put("options",map("omnibus_corr","by-identity"));edit(r,"expected_packages").remove("glmnet");
        assertThrows(IOException.class,()->checked(r));edit(r,"options").put("omnibus",true);assertNotNull(checked(r));
    }

    @Test void replayDifferencesRequireProminentExplicitWarning()throws Exception {
        var r=request();r.put("admission",map("allow_replay_differences",true));var c=checked(r);
        assertTrue(FusionWorkflowCli.compatibilityWarnings(c).contains("REPLAY DIFFERENCES ALLOWED"));
        assertFalse(FusionWorkflowCli.compatibilityWarnings(checked(request())).contains("REPLAY DIFFERENCES ALLOWED"));
    }

    @Test void missingRuntimeSkipsAndPreservesANewFailureCaptureWithUtf8()throws Exception {
        var r=request();r.put("rscript","missing-β-Rscript");Path file=root.resolve("request.json");FusionWorkflowFiles.write(file,r);
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();
        int code=FusionWorkflowCli.run(new String[]{"--request",file.toString()},new PrintStream(stdout),new PrintStream(stderr,true,StandardCharsets.ISO_8859_1));
        assertEquals(3,code);assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("missing-β-Rscript"));
        Path capture=root.resolve("result");assertTrue(Files.isRegularFile(capture.resolve("FAILURE.json")));assertFalse(Files.exists(capture.resolve("COMPLETED.json")));assertFalse(Files.exists(capture.resolve("admitted")));
        assertTrue(Files.readString(capture.resolve("WARNINGS.md")).contains("SKIP3: DEPENDENT ANALYSIS SKIPPED"));
    }

    @Test void invalidSavedCaptureFailsInsteadOfBeingTreatedAsMissingRuntime() {
        var stderr=new ByteArrayOutputStream();int code=FusionWorkflowCli.run(new String[]{"--verify",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(stderr));
        assertEquals(2,code);assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Saved FUSION capture is invalid"));
    }

    @Test void outputReusePreservesExistingEvidence()throws Exception {
        Path capture=root.resolve("old");Files.createDirectory(capture);Path evidence=capture.resolve("original");Files.writeString(evidence,"retained");
        assertThrows(IOException.class,()->FusionWorkflowFiles.newOutput(capture));assertEquals("retained",Files.readString(evidence));
    }

    @Test void completionMarkerAndManifestCountAgainstTheFinalFootprint()throws Exception {
        var request=checked(request());var limits=edit(request,"limits");limits.put("maximum_output_bytes",256);limits.put("maximum_member_bytes",1024);
        Files.write(root.resolve("original"),new byte[200]);FusionWorkflowProcess.footprint(root,request);
        Files.write(root.resolve("COMPLETED.json"),new byte[100]);assertThrows(IOException.class,()->FusionWorkflowProcess.footprint(root,request));
    }

    @Test void capturedPortableAddressesRefuseTraversalAndAliases() {
        for(String value:List.of("../x","x/../y","x//y","x/./y","/x","C:/x","x\\y","x/\u0000y"))assertThrows(IOException.class,()->FusionWorkflowFiles.relative(value));
        assertDoesNotThrow(()->FusionWorkflowFiles.relative("β/weights.RDat"));
    }
    private Map<String,Object> runtimeFixture()throws Exception {
        Files.createDirectory(root.resolve("worker"));var pin=map("bytes",1,"sha256","0".repeat(64));
        var addresses=map("C:\\R\\Rscript.exe","C:\\R\\Rscript.exe","C:\\library\\here","C:\\library\\here");
        var files=map("C:\\R\\Rscript.exe",pin,"C:\\library\\here\\R\\here.rdb",pin);
        FusionWorkflowFiles.write(root.resolve("runtime-before.json"),map("files",files,"addresses",addresses));
        FusionWorkflowFiles.write(root.resolve("worker/runtime.json"),map("packages",List.of(map("path","C:/library/here"))));
        return map("rscript","C:/R/Rscript.exe","binaries",map(),"runtime_compatibility",map());
    }
    @Test void runtimeClosureAcceptsRForwardSlashesAndJavaWindowsSeparators()throws Exception {
        var r=runtimeFixture();assertDoesNotThrow(()->FusionWorkflowAudit.runtimeClosure(root,r));
    }
    @Test void runtimeClosureRefusesAnUnfingerprintedActualExecutable()throws Exception {
        var r=runtimeFixture();r.put("rscript","C:/other/Rscript.exe");assertThrows(IOException.class,()->FusionWorkflowAudit.runtimeClosure(root,r));
    }
    @Test void runtimeClosureRefusesAnUnfingerprintedLoadedPackage()throws Exception {
        var r=runtimeFixture();Files.write(root.resolve("worker/runtime.json"),FusionWorkflowFiles.json(map("packages",List.of(map("path","C:/library/other")))));
        assertThrows(IOException.class,()->FusionWorkflowAudit.runtimeClosure(root,r));
    }
    @Test void originalCorrelationPlotCanRunWithoutLocusPlotOrAnnotation()throws Exception {
        var r=request();r.put("operation","POSTPROCESS");r.put("inputs",map("association","association","sumstats","sumstats","ref_ld_prefix","ref.","chromosome","1","weights_base","weights"));r.put("binaries",map());r.put("options",map("plot_corr",true));
        edit(r,"expected_packages").remove("glmnet");edit(r,"expected_packages").put("corrplot","0.95");assertNotNull(checked(r));
        edit(r,"options").put("ldsc",true);assertThrows(IOException.class,()->checked(r));
    }
    @Test void atomicReadyPublicationDoesNotInvalidateALiveFootprint()throws Exception {
        var r=checked(request());int observations=0;
        for(int i=0;i<200;i++){
            Path capture=Files.createDirectory(root.resolve("publication-"+i));Path worker=Files.createDirectory(capture.resolve("worker"));
            Path pending=worker.resolve("READY.pending");Files.writeString(pending,"FUSION_PREFLIGHT_9346C122_V1\n");
            var go=new java.util.concurrent.CountDownLatch(1);var finished=new java.util.concurrent.atomic.AtomicBoolean();var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread writer=new Thread(()->{try{go.await();Files.move(pending,worker.resolve("READY"),java.nio.file.StandardCopyOption.ATOMIC_MOVE);}catch(Throwable error){failure.set(error);}finally{finished.set(true);}},"fusion-ready-publication-fixture");writer.start();go.countDown();
            try{do{FusionWorkflowProcess.footprint(capture,r);observations++;}while(!finished.get());}
            finally{writer.join(2000);}
            assertFalse(writer.isAlive());assertNull(failure.get());FusionWorkflowProcess.footprint(capture,r);
        }
        assertTrue(observations>=200);
    }
    @Test void admittedSourceAbsencesAndZeroPValuesMustRemainProminent()throws Exception {
        String message=FusionWorkflowCli.resultWarnings(map("no_result_rows",3,"missing_requested_coloc_rows",2,"original_p_zero",1));
        assertTrue(message.contains("no_result_rows=3"));assertTrue(message.contains("missing_requested_coloc_rows=2"));assertTrue(message.contains("not finite association/colocalization estimates"));assertTrue(message.contains("ZERO P-VALUES"));
        assertEquals("",FusionWorkflowCli.resultWarnings(map("no_result_rows",0,"missing_requested_coloc_rows",0,"original_p_zero",0)));
    }
    @Test void adaptivePermutationAnalyticalAbsenceIsReportedWithoutRepair()throws Exception {
        String message=FusionWorkflowCli.resultWarnings(map("no_result_rows",0,"missing_requested_coloc_rows",0,"original_p_zero",0,"permutation_analytical_missing_rows",4));
        assertTrue(message.contains("absent in 4 rows"));assertTrue(message.contains("empirical p-values and counts are retained"));
    }
}
