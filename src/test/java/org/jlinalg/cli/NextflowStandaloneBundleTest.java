/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Genuine completed user workflows; neither producer paths nor an original engine are opened. */
class NextflowStandaloneBundleTest {
    @TempDir Path temp;
    static Stream<String> cases(){return Stream.of("21.04.1/000001","21.04.1/000002","22.10.6/000001","22.10.6/000002","24.04.4/000001","24.04.4/000002");}
    private Path fixture()throws Exception{return fixture("24.04.4/000001");}
    private Path fixture(String name)throws Exception {
        byte[] raw;try(var input=getClass().getResourceAsStream("/org/jlinalg/cli/nextflow-workflow/initial-controls.zip")){assertNotNull(input);raw=input.readAllBytes();}
        assertEquals(761623,raw.length);assertEquals("e08effb5e4d3c1ecc92bf3f6aeca024fc6b041bb3618b28177a7c609138d185a",NextflowFiles.sha(raw));Path out=Files.createDirectory(temp.resolve("bundle")).toRealPath();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().startsWith(name+"/")){
            Path target=out.resolve(entry.getName().substring(name.length()+1)).normalize();assertTrue(target.startsWith(out));if(entry.isDirectory())Files.createDirectories(target);else{Files.createDirectories(target.getParent());Files.copy(zip,target);}
        }}assertTrue(Files.isRegularFile(out.resolve("COMPLETED.json")));return out;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> json(Path path)throws Exception{return (Map<String,Object>)SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));}
    private void write(Path path,Object value)throws Exception{Files.writeString(path,NextflowJson.json(value)+"\n",StandardCharsets.UTF_8);}
    @SuppressWarnings("unchecked") private Map<String,Object> child(Map<String,Object> root,String key){return (Map<String,Object>)root.get(key);}
    private void resign(Path root,String...names)throws Exception {
        var manifest=json(root.resolve("manifest.json"));var files=child(manifest,"files");for(String name:names){byte[] raw=Files.readAllBytes(root.resolve(name));assertTrue(files.containsKey(name));files.put(name,Map.of("bytes",raw.length,"sha256",NextflowFiles.sha(raw)));}
        write(root.resolve("manifest.json"),manifest);var marker=json(root.resolve("COMPLETED.json"));byte[] raw=Files.readAllBytes(root.resolve("manifest.json"));marker.put("manifest",Map.of("bytes",raw.length,"sha256",NextflowFiles.sha(raw)));write(root.resolve("COMPLETED.json"),marker);
    }
    private void reject(Path root){assertThrows(IOException.class,()->NextflowBundle.verify(root,()->{}));}

    @ParameterizedTest @MethodSource("cases") void allThreeOriginalEnginesAndActualCachedResumesArePortable(String name)throws Exception {
        Path root=fixture(name);var result=NextflowBundle.verify(root,()->{});assertEquals(1,result.outputFiles());assertEquals(19,result.outputBytes());assertEquals(name.endsWith("000002")?4:0,result.completion().stats().cached());assertEquals(name.endsWith("000002")?0:4,result.completion().stats().succeeded());
        var output=child(child(json(root.resolve("outputs.json")),"members"),"counts.tsv");String hash=(String)child(output,"pin").get("sha256");assertEquals("a\t8\t4\nb\t6\t0\nc\t10\t8\n",Files.readString(root.resolve("objects").resolve(hash)));
    }
    @Test void alteredLateBlobBytesInvalidateTheCompletePopulation()throws Exception {Path root=fixture();var output=child(child(json(root.resolve("outputs.json")),"members"),"counts.tsv");Path blob=root.resolve("objects").resolve((String)child(output,"pin").get("sha256"));Files.writeString(blob,"changed\n");reject(root);}
    @Test void anExtraEmptyDirectoryIsNotHiddenByTheManifest()throws Exception {Path root=fixture();Files.createDirectory(root.resolve("unrecorded"));reject(root);}
    @Test void absentCompletionCannotAdmitAResult()throws Exception {Path root=fixture();Files.delete(root.resolve("COMPLETED.json"));reject(root);}
    @Test void anExtraProvisionalMarkerIsNotACompletedNamespace()throws Exception {Path root=fixture();Files.copy(root.resolve("COMPLETED.json"),root.resolve("CANDIDATE.json"));reject(root);}
    @Test void missingSourceBlobsCannotBecomeAnAdmittedPrefix()throws Exception {Path root=fixture();var snapshot=NextflowCapture.decode(json(root.resolve("capture.json")),new NextflowCapture.Limits(100000,34359738368L,17179869184L));var item=snapshot.roots().get(0).items().values().stream().filter(row->row.kind().equals("file")).findFirst().orElseThrow();Files.delete(root.resolve("objects").resolve(item.original().pin().sha256()));reject(root);}
    @Test void reSignedEffectiveParametersStillHaveToMatchTheCapturedRecipe()throws Exception {Path root=fixture();var value=json(root.resolve("params.json"));value.put("large_integer",1);write(root.resolve("params.json"),value);resign(root,"params.json");reject(root);}
    @Test void reSignedEnvironmentCannotEnableUndeclaredOnlineBehavior()throws Exception {Path root=fixture();var value=json(root.resolve("invocation.json"));child(value,"environment").put("NXF_OFFLINE","false");write(root.resolve("invocation.json"),value);resign(root,"invocation.json");reject(root);}
    @Test void reSignedCommandCannotInjectAnotherEngineFlag()throws Exception {Path root=fixture();var value=json(root.resolve("invocation.json"));var command=new ArrayList<>(NextflowFiles.strings(value.get("command")));command.add("-resume");value.put("command",command);write(root.resolve("invocation.json"),value);resign(root,"invocation.json");reject(root);}
    @Test void reSignedNonterminalTraceStatusIsNotACompletedTask()throws Exception {Path root=fixture();Path trace=root.resolve("trace.tsv");String before=Files.readString(trace),after=before.replaceFirst("\tCOMPLETED\t","\tRUNNING\t");assertNotEquals(before,after);Files.writeString(trace,after);resign(root,"trace.tsv");reject(root);}
    @Test void reSignedSummaryMustReconcileWithOriginalLogAndTrace()throws Exception {Path root=fixture();var value=json(root.resolve("completion.json"));child(value,"stats").put("cached",4);write(root.resolve("completion.json"),value);resign(root,"completion.json");reject(root);}
    @Test void reSignedOutputKindsMustMatchTheDeclaredContract()throws Exception {Path root=fixture();var value=json(root.resolve("outputs.json"));var member=child(child(value,"members"),"counts.tsv");member.put("kind","directory");member.put("pin",null);value.put("bytes",0);value.put("files",0);write(root.resolve("outputs.json"),value);resign(root,"outputs.json");reject(root);}
    @Test void changingBothRecipeAndParametersCannotDiscardTheOriginalRequestPin()throws Exception {Path root=fixture();var request=json(root.resolve("request.json"));child(request,"parameters").put("large_integer",1);write(root.resolve("request.json"),request);var parameters=json(root.resolve("params.json"));parameters.put("large_integer",1);write(root.resolve("params.json"),parameters);resign(root,"request.json","params.json");reject(root);}
    @Test void reSignedRequiredToolMustStillMatchItsDeclaredBinding()throws Exception {Path root=fixture();var list=new ArrayList<Object>(NextflowFiles.list(SimpleJson.parse(Files.readString(root.resolve("tools.json")))));var tool=new LinkedHashMap<String,Object>();for(var row:NextflowFiles.object(list.get(0)).entrySet())tool.put((String)row.getKey(),row.getValue());tool.put("path","/usr/bin/another-tool");list.set(0,tool);write(root.resolve("tools.json"),list);resign(root,"tools.json");reject(root);}
    @Test void reSignedRuntimeVersionCannotSubstituteADifferentEdition()throws Exception {Path root=fixture();var runtime=json(root.resolve("runtime.json"));child(runtime,"context").put("version","21.04.1");write(root.resolve("runtime.json"),runtime);resign(root,"runtime.json");reject(root);}
    @Test void reSignedSpecialObjectMetadataCannotClaimCapturedStreamBytes()throws Exception {Path root=fixture();var after=json(root.resolve("working-after.json"));Map<?,?> first=NextflowFiles.object(NextflowFiles.list(after.get("roots")).get(0));@SuppressWarnings("unchecked") var items=(Map<String,Object>)first.get("items");var item=new LinkedHashMap<String,Object>();item.put("kind","fifo");item.put("original",Map.of("pin",Map.of("bytes",0,"sha256",NextflowFiles.sha(new byte[0])),"fileKey","opaque","modified","2026-11-02T00:00:00Z","mode",420,"links",1));item.put("target",null);item.put("resolved",null);item.put("mode",420);items.put("execution/pipe",item);write(root.resolve("working-after.json"),after);resign(root,"working-after.json");reject(root);}
    @Test void reSignedIgnoredFailuresCannotTurnAnEngineZeroIntoSuccess()throws Exception {Path root=fixture();Path log=root.resolve("nextflow.log");String before=Files.readString(log),after=before.replace("ignoredCount=0","ignoredCount=1");assertNotEquals(before,after);Files.writeString(log,after);resign(root,"nextflow.log");reject(root);}
}
