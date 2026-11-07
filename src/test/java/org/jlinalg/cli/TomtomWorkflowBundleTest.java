/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TomtomWorkflowBundleTest {
    @TempDir Path temporary;
    @ParameterizedTest @ValueSource(strings={"text","full"})
    void completePublicBundlesVerifyWithoutOpeningAnyOriginalProducerPath(String label)throws Exception {
        Path root=fixture(label);var result=TomtomWorkflow.verify(root,()->{});assertEquals(9,result.output().matches().size());assertEquals(9,result.output().pairs().size());assertEquals(label.equals("text")?TomtomRecipe.Mode.TEXT:TomtomRecipe.Mode.FULL_REPORT,result.request().mode());
    }
    static Stream<String> mutations(){return Stream.of("attempt-mode","runtime-build-path","runtime-health-row-count","runtime-payload-hash","producer-command","producer-environment","producer-unknown-option","process-command","process-accounting","pair-number","pair-terminal-newline","output-audit","html-match","warnings");}
    @ParameterizedTest @MethodSource("mutations")
    void semanticInconsistenciesFailEvenAfterEveryEnclosingHashIsRecomputed(String mutation)throws Exception {
        Path root=fixture("full");String expected;
        switch(mutation){
            case "attempt-mode"->{edit(root,"metadata/attempt.json","output_mode","TEXT");expected="attempt/recipe identity differs";}
            case "runtime-build-path"->{edit(root,"runtime-admission/metadata/request.json","build_evidence","/different/build-evidence");reseal(root.resolve("runtime-admission"),root);expected="native admission request differs";}
            case "runtime-health-row-count"->{var doc=map(root.resolve("runtime-admission/metadata/health.json"));var fits=NextflowFiles.list(doc.get("fits"));var row=mutable(NextflowFiles.object(fits.get(0)));row.put("complete_reported_rows",8);var changed=new java.util.ArrayList<Object>(fits);changed.set(0,row);doc.put("fits",changed);replace(root.resolve("runtime-admission/metadata/health.json"),doc);reseal(root.resolve("runtime-admission"),root);expected="outside its declared range";}
            case "runtime-payload-hash"->{Path path=root.resolve("runtime-admission/metadata/host.json");var doc=map(path);var snapshot=mutable(NextflowFiles.object(doc.get("complete_host_files")));var trees=mutable(NextflowFiles.object(snapshot.get("trees")));String home=NextflowFiles.text(doc.get("home"));var payload=mutable(NextflowFiles.object(trees.get(home)));var node=mutable(NextflowFiles.object(payload.get("bin/tomtom")));var file=mutable(NextflowFiles.object(node.get("file")));String token=NextflowFiles.text(file.get("sha256"));file.put("sha256",(token.charAt(0)=='0'?"1":"0")+token.substring(1));node.put("file",file);payload.put("bin/tomtom",node);trees.put(home,payload);snapshot.put("trees",trees);doc.put("complete_host_files",snapshot);replace(path,doc);reseal(root.resolve("runtime-admission"),root);expected="payload hashes differ";}
            case "producer-command"->{var doc=map(root.resolve("metadata/producer.json"));var command=new java.util.ArrayList<>(NextflowFiles.strings(doc.get("command")));int index=command.indexOf("-dist");assertTrue(index>0);command.set(index+1,"kullback");doc.put("command",command);replace(root.resolve("metadata/producer.json"),doc);expected="scientific command differs";}
            case "producer-environment"->{var doc=map(root.resolve("metadata/producer.json"));var env=mutable(NextflowFiles.object(doc.get("environment")));env.put("LANG","en_US.UTF-8");doc.put("environment",env);replace(root.resolve("metadata/producer.json"),doc);expected="environment differs";}
            case "producer-unknown-option"->{edit(root,"metadata/producer.json","extra_flags","-text");expected="metadata fields";}
            case "process-command"->{edit(root,"process/process.json","command",List.of("/usr/bin/true"));expected="command";}
            case "process-accounting"->{edit(root,"process/process.json","accounted_roots",List.of());expected="account";}
            case "pair-number"->{Path path=root.resolve("PAIR-LEDGER.tsv");String text=Files.readString(path);String[] rows=text.split("\n");String[] row=rows[1].split("\t");row[5]="0.987654321";rows[1]=String.join("\t",row);Files.writeString(path,String.join("\n",rows)+"\n");expected="pair disposition";}
            case "pair-terminal-newline"->{Path path=root.resolve("PAIR-LEDGER.tsv");String text=Files.readString(path);Files.writeString(path,text.substring(0,text.length()-1));expected="terminal newline";}
            case "output-audit"->{edit(root,"OUTPUT-AUDIT.json","complete_reported_matches",8);expected="output audit differs";}
            case "html-match"->{Path path=root.resolve("process/result/tomtom.html");String text=Files.readString(path),changed=text.replaceFirst("\"pv\": \"[^\"]+\"","\"pv\": \"0.987654321\"");assertNotEquals(text,changed);Files.writeString(path,changed);expected="HTML";}
            case "warnings"->{Files.writeString(root.resolve("WARNINGS.md"),"Everything is fine.\n");expected="warnings/required actions";}
            default->throw new AssertionError(mutation);
        }
        reseal(root,root);IOException failure=assertThrows(IOException.class,()->TomtomWorkflow.verify(root,()->{}));assertTrue(failure.toString().toLowerCase(java.util.Locale.ROOT).contains(expected.toLowerCase(java.util.Locale.ROOT)),mutation+": "+failure);
    }
    @Test void componentCompletionNeverSubstitutesForMissingOverallCompletion()throws Exception {
        Path root=fixture("text");Files.delete(root.resolve("COMPLETED.json"));assertTrue(Files.exists(root.resolve("runtime-admission/COMPLETED.json")));assertThrows(IOException.class,()->TomtomWorkflow.verify(root,()->{}));
    }
    @Test void retainedFailurePreventsAdmissionEvenWithACompletedRuntimeAndFreshHashes()throws Exception {
        Path root=fixture("full");Files.writeString(root.resolve("FAILURE.json"),"{}\n");reseal(root,root);IOException failure=assertThrows(IOException.class,()->TomtomWorkflow.verify(root,()->{}));assertTrue(failure.getMessage().contains("retains a failure/skip"));
    }
    @Test void verificationCancellationDoesNotRewriteTheCapturedBundle()throws Exception {
        Path root=fixture("full");var request=TomtomRecipe.readCaptured(root.resolve("prepared/recipe.json"),()->{});var before=TomtomBundle.inventory(root,request,()->{});assertThrows(CancellationException.class,()->TomtomWorkflow.verify(root,()->{throw new CancellationException("controlled");}));assertEquals(before,TomtomBundle.inventory(root,request,()->{}));
    }
    private static void edit(Path root,String member,String key,Object value)throws IOException {Path path=root.resolve(member);var doc=map(path);doc.put(key,value);replace(path,doc);}
    private static Map<String,Object> map(Path path)throws IOException {return mutable(TomtomBundle.json(path,67108864));}
    private static Map<String,Object> mutable(Map<?,?> source)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:source.entrySet())result.put(NextflowFiles.text(e.getKey()),e.getValue());return result;}
    private static void replace(Path path,Object value)throws IOException {Files.writeString(path,RsemJson.json(value)+"\n",StandardCharsets.UTF_8);}
    private static void reseal(Path folder,Path overall)throws IOException {
        var request=TomtomRecipe.readCaptured(overall.resolve("prepared/recipe.json"),()->{});var inventory=new java.util.TreeMap<>(TomtomBundle.inventory(folder,request,()->{}));for(String name:Set.of("manifest.json","COMPLETED.json"))assertNotNull(inventory.remove(name));var manifest=map(folder.resolve("manifest.json"));manifest.put("files",inventory);replace(folder.resolve("manifest.json"),manifest);var completion=map(folder.resolve("COMPLETED.json"));completion.put("manifest",MemeRuntimeFiles.pin(NextflowFiles.pin(folder.resolve("manifest.json"),67108864,()->{})));replace(folder.resolve("COMPLETED.json"),completion);
    }
    private Path fixture(String label)throws IOException {
        byte[] raw;try(var in=getClass().getResourceAsStream("/org/jlinalg/cli/tomtom-workflows/original-public-workflows.zip")){assertNotNull(in);raw=in.readAllBytes();}assertEquals("b3f5a895d1d96258b118d98481f4f4b7a8c1389e2fd32931d7ae58ae09fb1751",NextflowFiles.sha(raw));var files=new LinkedHashMap<String,byte[]>();try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){assertFalse(entry.isDirectory());assertNull(files.put(entry.getName(),zip.readAllBytes()));}}
        Path root=temporary.resolve(label);Files.createDirectory(root);var expected=NextflowFiles.object(SimpleJson.parse(new String(files.get("EXPECTED.json"),StandardCharsets.UTF_8)));for(String name:NextflowFiles.strings(expected.get("directories")))if(name.startsWith(label+"/"))Files.createDirectories(root.resolve(NextflowFiles.member(name.substring(label.length()+1))));for(var e:files.entrySet())if(e.getKey().startsWith(label+"/")){Path path=root.resolve(NextflowFiles.member(e.getKey().substring(label.length()+1)));Files.createDirectories(path.getParent());Files.write(path,e.getValue());}return root;
    }
}
