/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Real original-runtime composition plus independent source/identity/failure controls. */
class PeerWorkflowTest {
    private static Path owned()throws IOException {
        Path base=Path.of(System.getProperty("jlinalg.peer.testOutput","build/peer-tests")).toAbsolutePath();Files.createDirectories(base);Path root=Files.createTempDirectory(base,"owned-");
        try(var input=PeerWorkflowTest.class.getResourceAsStream("/org/jlinalg/cli/peer-workflow-originals.zip")) {
            assertNotNull(input);try(var archive=new ZipInputStream(input)) {
                java.util.zip.ZipEntry entry;while((entry=archive.getNextEntry())!=null){Path relative=Path.of(entry.getName());if(relative.isAbsolute()||entry.getName().contains("..")||entry.getName().contains("\\"))throw new IOException("Unsafe fixture member");Path target=root.resolve(relative);Files.createDirectories(target.getParent());Files.write(target,archive.readAllBytes(),StandardOpenOption.CREATE_NEW);}
            }
        }
        var fixture=read(root.resolve("PROVENANCE.json"));assertEquals(0L,((Number)fixture.get("participants")).longValue());
        for(var entry:object(fixture.get("files"),"fixture files").entrySet())assertEquals(object(entry.getValue(),"fixture pin").get("sha256"),FollowupSupport.hash(root.resolve(entry.getKey())));
        return root;
    }
    private static Map<String,Object> runtime() {
        String path=System.getProperty("jlinalg.peer.runtime","");assumeTrue(!path.isEmpty(),"Exact original PEER runtime not configured; actual-runtime tests explicitly skipped");
        var runtime=new LinkedHashMap<String,Object>();runtime.put("executable",path);runtime.put("sha256",System.getProperty("jlinalg.peer.runtimeSha256",""));String wsl=System.getProperty("jlinalg.peer.wslDistribution","");if(!wsl.isEmpty())runtime.put("wsl_distribution",wsl);return runtime;
    }
    private static Map<String,Object> request(Path root) {
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("profile",PeerWorkflowContracts.PROFILE);r.put("out",root.resolve("capture").toString());r.put("matrix",root.resolve("inputs/matrix.tsv").toString());r.put("factors",2);r.put("seed",17);
        r.put("runtime",Map.of("executable",root.resolve("absent-peer").toString(),"sha256","0".repeat(64)));r.put("preparation_note","Synthetic source-profile control with literal IDs and no participants.");return r;
    }
    private static Map<String,Object> model(Path root,String name) {
        var r=request(root);r.put("runtime",runtime());
        switch(name) {
            case "MEAN" -> r.put("add_mean",true);
            case "KNOWN" -> {r.put("add_mean",true);r.put("covariates",root.resolve("inputs/covariates.tsv").toString());}
            case "UNCERTAINTY" -> r.put("measurement_variance",root.resolve("inputs/variance.tsv").toString());
            case "PRIORS" -> {r.put("alpha_a",.125);r.put("alpha_b",.25);r.put("epsilon_a",.5);r.put("epsilon_b",1.25);r.put("covariate_precision",50);}
            case "PCA" -> r.put("initialization","pca");
            case "DENSE" -> { }
            default -> throw new IllegalArgumentException(name);
        }
        return r;
    }
    private static int invoke(Path root,Map<String,Object> request)throws IOException {
        String id=UUID.randomUUID().toString();Path source=root.resolve(id+".json");Files.write(source,RsemJson.bytes(request),StandardOpenOption.CREATE_NEW);
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();int result;
        try(var out=new PrintStream(stdout,true,StandardCharsets.UTF_8);var err=new PrintStream(stderr,true,StandardCharsets.UTF_8)){result=JLinAlgCli.run(new String[]{"peer-tools","--request",source.toString()},out,err);}
        Files.write(root.resolve(id+".stdout"),stdout.toByteArray(),StandardOpenOption.CREATE_NEW);Files.write(root.resolve(id+".stderr"),stderr.toByteArray(),StandardOpenOption.CREATE_NEW);return result;
    }
    private static int verify(Path root)throws IOException {
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();int result;
        try(var out=new PrintStream(stdout,true,StandardCharsets.UTF_8);var err=new PrintStream(stderr,true,StandardCharsets.UTF_8)){result=JLinAlgCli.run(new String[]{"peer-tools","--verify",root.toString()},out,err);}
        String id="verify-"+UUID.randomUUID();Files.write(root.getParent().resolve(id+".stdout"),stdout.toByteArray(),StandardOpenOption.CREATE_NEW);Files.write(root.getParent().resolve(id+".stderr"),stderr.toByteArray(),StandardOpenOption.CREATE_NEW);return result;
    }
    @Test void helpNamesExactPrecisionAndRequiredRecovery() {
        var out=new ByteArrayOutputStream();assertEquals(0,PeerWorkflowCli.run(new String[]{"--help"},new PrintStream(out),System.err));String help=out.toString(StandardCharsets.UTF_8);
        for(String word:List.of("FP32","SKIP3","FAIL2","REPLAY","no"))assertTrue(help.contains(word));
    }
    @Test void closedRequestRejectsIgnoredEngineBeforeCreatingOutput()throws Exception {
        Path root=owned();var r=request(root);r.put("engine","native");assertEquals(2,invoke(root,r));assertFalse(Files.exists(root.resolve("capture")));
    }
    @Test void missingOriginalRuntimeSkipsWithoutLegacyJavaFallback()throws Exception {
        Path root=owned();assertEquals(3,invoke(root,request(root)));var status=read(root.resolve("capture/analysis-status.json"));assertEquals(false,status.get("results_admitted"));assertTrue(Files.readString(root.resolve("capture/WARNINGS.md")).contains("absent-peer"));assertFalse(Files.exists(root.resolve("capture/exports")));
    }
    @Test void unknownEditionIsAnExplicitSkippedPrerequisite()throws Exception {
        Path root=owned();var r=request(root);r.put("profile","PEER-9.9-unknown");assertEquals(3,invoke(root,r));assertTrue(Files.readString(root.resolve("capture/WARNINGS.md")).contains("source profile is not supplied"));
    }
    @Test void covariateSampleOrderIsNotRepaired()throws Exception {
        Path root=owned();Path cov=root.resolve("inputs/covariates.tsv");var lines=new ArrayList<>(Files.readAllLines(cov));Collections.swap(lines,1,2);Files.write(cov,lines,StandardCharsets.UTF_8);
        var r=request(root);r.put("covariates",cov.toString());assertEquals(2,invoke(root,r));assertTrue(Files.readString(root.resolve("capture/WARNINGS.md")).contains("sample identities/order"));assertFalse(Files.exists(root.resolve("capture/worker/capture")));
    }
    @Test void nonfiniteInputIsRetainedAndRefusedBeforeExecution()throws Exception {
        Path root=owned();Path data=root.resolve("inputs/matrix.tsv");var lines=new ArrayList<>(Files.readAllLines(data));String[] row=lines.get(1).split("\t",-1);row[1]="NaN";lines.set(1,String.join("\t",row));Files.write(data,lines,StandardCharsets.UTF_8);
        assertEquals(2,invoke(root,request(root)));assertArrayEquals(Files.readAllBytes(data),Files.readAllBytes(root.resolve("capture/inputs/matrix.tsv")));assertFalse(Files.exists(root.resolve("capture/worker/capture")));
    }
    @Test void wrongRuntimeHashIsFailureNotMissingDependency()throws Exception {
        Path root=owned();var r=request(root);r.put("runtime",Map.of("executable",root.resolve("PROVENANCE.json").toString(),"sha256","0".repeat(64)));assertEquals(2,invoke(root,r));assertTrue(Files.readString(root.resolve("capture/WARNINGS.md")).contains("SHA-256 differs"));
    }
    @Test void existingOutputIsPreserved()throws Exception {
        Path root=owned();Files.createDirectory(root.resolve("capture"));Files.writeString(root.resolve("capture/keep.txt"),"original");assertEquals(2,invoke(root,request(root)));assertEquals("original",Files.readString(root.resolve("capture/keep.txt")));assertEquals(1,Files.list(root.resolve("capture")).count());
    }
    @ParameterizedTest @ValueSource(strings={"DENSE","MEAN","KNOWN","UNCERTAINTY","PRIORS","PCA"})
    void actualOriginalRunAndVerifyMatchEveryRawModelAndHistoryFile(String name)throws Exception {
        Path root=owned();assertEquals(0,invoke(root,model(root,name)));Path capture=root.resolve("capture");assertEquals(0,verify(capture));
        try(var expected=Files.list(root.resolve(name))){for(Path path:expected.toList())assertArrayEquals(Files.readAllBytes(path),Files.readAllBytes(capture.resolve("worker/capture/"+path.getFileName())),path.getFileName().toString());}
        assertEquals("IEEE754-binary32",read(capture.resolve("prepared.json")).get("source_matrix_scalar"));assertEquals(true,read(capture.resolve("analysis-status.json")).get("complete_result"));
    }
    private static Map<String,String> hashes(Path root)throws IOException {var result=new TreeMap<String,String>();try(var files=Files.walk(root)){for(Path path:files.filter(Files::isRegularFile).toList())result.put(root.relativize(path).toString(),FollowupSupport.hash(path));}return result;}
    @Test void actualReplayPreservesParentAndEveryScientificExport()throws Exception {
        Path root=owned();assertEquals(0,invoke(root,model(root,"KNOWN")));Path parent=root.resolve("capture");var before=hashes(parent);
        assertEquals(0,invoke(root,Map.of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",root.resolve("replay").toString())));assertEquals(before,hashes(parent));assertEquals(0,verify(root.resolve("replay")));
        try(var paths=Files.list(parent.resolve("exports"))){for(Path path:paths.toList())assertArrayEquals(Files.readAllBytes(path),Files.readAllBytes(root.resolve("replay/exports/"+path.getFileName())));}
    }
    @Test void actualNonconvergenceRetainsOriginalPartialModelAndHasNoAdmittedExports()throws Exception {
        Path root=owned();var r=model(root,"DENSE");r.put("max_iterations",1);assertEquals(2,invoke(root,r));Path capture=root.resolve("capture");assertTrue(Files.isRegularFile(capture.resolve("worker/capture/final-state.tsv")));assertFalse(Files.exists(capture.resolve("exports")));assertEquals(false,read(capture.resolve("analysis-status.json")).get("complete_result"));assertEquals(2,verify(capture));
    }
    private static void copy(Path source,Path target)throws IOException {try(var paths=Files.walk(source)){for(Path path:paths.toList()){Path destination=target.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectories(destination);else Files.copy(path,destination);}}}
    private static void alterField(Path path,String field,String value)throws IOException {
        var lines=new ArrayList<>(Files.readAllLines(path));boolean found=false;
        for(int i=1;i<lines.size();i++){String[] row=lines.get(i).split("\t",-1);if(row[0].equals(field)){row[3]=value;lines.set(i,String.join("\t",row));found=true;break;}}
        assertTrue(found);Files.write(path,lines,StandardCharsets.UTF_8);
    }
    @Test void resealedSemanticCorruptionsAreRefused()throws Exception {
        Path root=owned();assertEquals(0,invoke(root,model(root,"DENSE")));Path parent=root.resolve("capture");
        for(String kind:List.of("Y","residual","alphaA","initialX2","command","extra","source","counter","export")) {
            Path changed=root.resolve("changed-"+kind);copy(parent,changed);assertEquals(0,verify(changed));
            switch(kind) {
                case "Y","residual","alphaA" -> alterField(changed.resolve("worker/capture/final-state.tsv"),kind,"999");
                case "initialX2" -> alterField(changed.resolve("worker/capture/initial-state.tsv"),"X2","999");
                case "command" -> {var command=read(changed.resolve("worker/command.json"));var arguments=new ArrayList<Object>(list(command.get("original_arguments"),"arguments"));arguments.set(arguments.indexOf("--factors")+1,"3");command.put("original_arguments",arguments);json(changed.resolve("worker/command.json"),command);}
                case "extra" -> Files.writeString(changed.resolve("worker/undeclared.txt"),"extra");
                case "source" -> Files.writeString(changed.resolve("source/peer_capture.cpp"),"\n",StandardOpenOption.APPEND);
                case "counter" -> {Path status=changed.resolve("worker/capture/status.tsv");Files.writeString(status,Files.readString(status).replace("executed_updates\t3","executed_updates\t2"));}
                case "export" -> Files.writeString(changed.resolve("exports/factors.tsv"),"sample_id\tPEER1\tPEER2\nchanged\t0\t0\n");
                default -> throw new IllegalArgumentException(kind);
            }
            PeerWorkflowCli.seal(changed);assertEquals(2,verify(changed),kind);
        }
    }
    @Test void originalVerboseThreeUsesCompleteObservedDoubleBoundEvents()throws Exception {
        Path root=owned();var r=model(root,"KNOWN");r.put("verbose",3);assertEquals(0,invoke(root,r));assertEquals(0,verify(root.resolve("capture")));
    }
    @Test void actualOutputCapacityFailureKeepsRawDiagnostics()throws Exception {
        Path root=owned();var r=model(root,"DENSE");r.put("maximum_output_bytes",64);assertEquals(2,invoke(root,r));assertTrue(Files.isRegularFile(root.resolve("capture/worker/stdout.log")));assertEquals(false,read(root.resolve("capture/analysis-status.json")).get("results_admitted"));
    }
    @Test void actualTimeoutStopsOwnedWorkerAndPreservesItsLedger()throws Exception {
        Path root=owned();var r=model(root,"DENSE");Path matrix=root.resolve("large.tsv");var text=new StringBuilder("feature_id");for(int i=0;i<140;i++)text.append('\t').append('S').append(i);text.append('\n');
        for(int d=0;d<180;d++){text.append('G').append(d);for(int i=0;i<140;i++)text.append('\t').append(((i*7+d*3)%31-15)*.125);text.append('\n');}Files.writeString(matrix,text,StandardCharsets.UTF_8);
        r.put("matrix",matrix.toString());r.put("factors",10);r.put("max_iterations",1_000_000);r.put("tolerance",0);r.put("variance_tolerance",0);r.put("verbose",0);r.put("timeout_seconds",1);
        assertEquals(2,invoke(root,r));var manifest=read(root.resolve("capture/manifest.json"));assertTrue(text(manifest,"error").contains("timed out"));
        var cleanup=list(manifest.get("owned_process_cleanup"),"cleanup");for(Object item:cleanup)assertEquals(false,object(item,"owned process").get("alive_after_cleanup"));
        if(System.getProperty("os.name").startsWith("Windows"))assertEquals(true,object(manifest.get("owned_wsl_cleanup"),"WSL cleanup").get("complete_cleanup"));
        assertEquals(false,read(root.resolve("capture/analysis-status.json")).get("complete_result"));
    }
}
