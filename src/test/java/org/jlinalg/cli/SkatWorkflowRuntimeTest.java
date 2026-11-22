/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SkatWorkflowRuntimeTest {
    @TempDir Path directory;
    @Test void capturedSourcePopulationAndProducerBytesAreGuarded()throws Exception {
        SkatWorkflowRuntime.sources(directory,false);SkatWorkflowRuntime.sources(directory,true);
        Path extra=directory.resolve("source/extra.txt");Files.writeString(extra,"extra");assertThrows(IOException.class,()->SkatWorkflowRuntime.sources(directory,true));Files.delete(extra);
        Path worker=directory.resolve("source/skat_worker.R");Files.writeString(worker,"# changed\n",StandardCharsets.UTF_8);assertThrows(IOException.class,()->SkatWorkflowRuntime.sources(directory,true));
    }
    @Test void missingCapturedSourceIsIntegrityFailureRatherThanDependencySkip()throws Exception {
        SkatWorkflowRuntime.sources(directory,false);Files.delete(directory.resolve("source/SOURCE.json"));IOException error=assertThrows(IOException.class,()->SkatWorkflowRuntime.sources(directory,true));assertFalse(error instanceof SkatWorkflowContracts.Unavailable);
    }
    @Test void changedJavaProducerFingerprintIsRefused()throws Exception {
        SkatWorkflowRuntime.sources(directory,false);Files.writeString(directory.resolve("source/java-class-fingerprints.json"),"{}\n");assertThrows(IOException.class,()->SkatWorkflowRuntime.sources(directory,true));
    }
    @Test void fingerprintLengthCannotBeFractionalNegativeOrWrong()throws Exception {
        Map<String,Object> actual=Map.of("bytes",2,"sha256","abc");SkatWorkflowRuntime.matches(Map.of("bytes",2L,"sha256","abc"),actual,"test");
        for(Object bytes:List.of(2.5,-1,3))assertThrows(IOException.class,()->SkatWorkflowRuntime.matches(Map.of("bytes",bytes,"sha256","abc"),actual,"test"));
        assertThrows(IOException.class,()->SkatWorkflowRuntime.matches(Map.of("bytes",2,"sha256","different"),actual,"test"));
    }
    @Test void invalidUtf8AndOddHexAreRefused()throws Exception {
        assertEquals("a\tβ",SkatWorkflowRuntime.unhex("6109ceb2"));for(String value:List.of("0","GG","c0af","ff"))assertThrows(IOException.class,()->SkatWorkflowRuntime.unhex(value));
    }
    @Test void missingFinalWorkerStatusIsNotAnUnexecutedDependencySkip()throws Exception {
        Files.createDirectory(directory.resolve("worker"));IOException error=assertThrows(IOException.class,()->SkatWorkflowRuntime.outcome(directory));assertFalse(error instanceof SkatWorkflowContracts.Unavailable);
    }
    @Test void normalizedRequestJsonRoundTripCannotReorderGeneratedParameters()throws Exception {
        Map<String,Object> normalized=request(5);Path path=directory.resolve("normalized.json");Files.write(path,RsemJson.bytes(normalized));Map<String,Object> reread=SkatWorkflowFiles.read(path,1_048_576);assertEquals(SkatWorkflowFiles.parameters(normalized),SkatWorkflowFiles.parameters(reread));
    }
    @Test void invalidCallCountsAndUnknownStatusAreRefused()throws Exception {
        Path worker=Files.createDirectory(directory.resolve("worker"));Path status=worker.resolve("worker-status.tsv");String header="status\tdetail_hex\toriginal_null_fits\toriginal_set_calls\tgenotype_diagnostic_calls\n";
        for(String row:List.of("unknown\t\t0\t0\t0","COMPLETED_WITH_WARNINGS\t\t-1\t1\t0","COMPLETED_WITH_WARNINGS\t\t0.5\t1\t0","COMPLETED_WITH_WARNINGS\t\t1\t9999999999\t0")){Files.writeString(status,header+row+"\n");assertThrows(IOException.class,()->SkatWorkflowRuntime.outcome(directory));}
    }
    @Test void originalLogicalMissingAndDuplicateFieldIndexesRemainDistinct()throws Exception {
        Path fields=directory.resolve("fields.tsv");String header="path\ttype\tindex\telement_name_hex\tvalue\tencoding\tdimensions\n";Files.writeString(fields,header+"/Test.Type\tlogical\t1\t\tNA\tlogical\t\n");var result=SkatWorkflowAudit.fields(fields,request(5),java.util.Set.of("/Test.Type"),false);assertEquals("logical",result.get("/Test.Type").get(0).encoding());
        Files.writeString(fields,header+"/p.value\tdouble\t1\t\t.5\tnumber\t\n/p.value\tdouble\t1\t\t.7\tnumber\t\n");assertThrows(IOException.class,()->SkatWorkflowAudit.fields(fields,request(5),java.util.Set.of("/p.value"),false));
    }
    @Test void realOwnedRootAndObservedChildCloseOnCallbackFailure()throws Exception {
        Path classes=controlClasses();Path root=caseRoot("callback");Map<String,Object> manifest=new LinkedHashMap<>();
        IOException error=assertThrows(IOException.class,()->SkatWorkflowProcess.execute(command(classes,"spawn",root),SkatWorkflowRuntime.environment(),root,request(10),manifest,()->{if(Files.exists(root.resolve("worker/child.pid")))throw new IOException("Deliberate owned-process control refusal");}));assertTrue(error.getMessage().contains("Deliberate"));assertDead(manifest,2);
    }
    @Test void realTimeoutClosesOwnedProcess()throws Exception {
        Path classes=controlClasses();Path root=caseRoot("timeout");Map<String,Object> manifest=new LinkedHashMap<>();
        IOException error=assertThrows(IOException.class,()->SkatWorkflowProcess.execute(command(classes,"sleep",root),SkatWorkflowRuntime.environment(),root,request(1),manifest,()->{}));assertTrue(error.getMessage().contains("timed out"));assertDead(manifest,1);
    }
    @Test void realNormalExitWithObservedLiveChildIsRefusedAndChildCloses()throws Exception {
        Path classes=controlClasses();Path root=caseRoot("orphan");Map<String,Object> manifest=new LinkedHashMap<>();
        IOException error=assertThrows(IOException.class,()->SkatWorkflowProcess.execute(command(classes,"orphan",root),SkatWorkflowRuntime.environment(),root,request(10),manifest,()->{}));assertTrue(error.getMessage().contains("live observed child"));assertDead(manifest,2);
    }
    @Test void memberCapacityRefusesBeforeLaunchingAProcess()throws Exception {
        Path root=caseRoot("capacity");Files.write(root.resolve("worker/oversized"),new byte[2048]);var r=request(10);var limits=new LinkedHashMap<>(SkatWorkflowFiles.map(r.get("limits")));limits.put("maximum_member_bytes",1024);r.put("limits",limits);var manifest=new LinkedHashMap<String,Object>();
        assertThrows(IOException.class,()->SkatWorkflowProcess.execute(List.of("never-launch-this-control"),Map.of(),root,r,manifest,()->{}));assertFalse(manifest.containsKey("worker_pid"));
    }
    @SuppressWarnings("unchecked") private static void assertDead(Map<String,Object> manifest,int minimum){var rows=(List<Map<String,Object>>)manifest.get("owned_processes");assertNotNull(rows);assertTrue(rows.size()>=minimum);for(var row:rows)assertEquals(false,row.get("same_identity_alive"));}
    private static Map<String,Object> request(int timeout)throws IOException {
        var raw=new LinkedHashMap<String,Object>();raw.put("schema_version",1);raw.put("mode","RUN");raw.put("engine","original");raw.put("profile",SkatWorkflowContracts.PROFILE);raw.put("operation","ASSOCIATE");for(String key:List.of("out","rscript","phenotype","genotypes","sets"))raw.put(key,"unused");raw.put("r_libraries",List.of("unused"));raw.put("seed",1);raw.put("null_model",Map.of("out_type","C"));raw.put("limits",Map.of("timeout_seconds",timeout));return SkatWorkflowContracts.validate(raw);
    }
    private Path caseRoot(String name)throws IOException {Path path=Files.createDirectory(directory.resolve(name));Files.createDirectory(path.resolve("worker"));return path;}
    private static List<String> command(Path classes,String mode,Path root){return List.of(javaBinary(),"-cp",classes.toString(),"SkatOwnedControl",mode,root.resolve("worker").toString(),javaBinary());}
    private static String javaBinary(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
    private Path controlClasses()throws Exception {
        Path folder=Files.createDirectory(directory.resolve("control-classes")),source=folder.resolve("SkatOwnedControl.java");
        String code="import java.nio.file.*;public final class SkatOwnedControl{public static void main(String[]a)throws Exception{Path w=Path.of(a[1]);if(a[0].equals(\"child\")){Files.writeString(w.resolve(\"child.pid\"),Long.toString(ProcessHandle.current().pid()));Thread.sleep(60000);return;}if(a[0].equals(\"sleep\")){Thread.sleep(60000);return;}new ProcessBuilder(a[2],\"-cp\",System.getProperty(\"java.class.path\"),\"SkatOwnedControl\",\"child\",a[1],a[2]).redirectOutput(w.resolve(\"child.stdout\").toFile()).redirectError(w.resolve(\"child.stderr\").toFile()).start();while(!Files.exists(w.resolve(\"child.pid\")))Thread.sleep(10);Thread.sleep(150);if(a[0].equals(\"orphan\"))return;Thread.sleep(60000);}}";
        Files.writeString(source,code);var compiler=ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);assertEquals(0,compiler.run(null,null,null,"-d",folder.toString(),source.toString()));return folder;
    }
}
