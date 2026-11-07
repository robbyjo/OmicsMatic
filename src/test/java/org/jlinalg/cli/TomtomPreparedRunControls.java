/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.TomtomResults;

/** Synthetic integration controls only; deliberately not a runtime admission or public arbitrary-command API. */
public final class TomtomPreparedRunControls {
    private TomtomPreparedRunControls() { }
    /**
     * @param args exact tested JAR, existing original home, original build receipt, NEW Linux evidence root
     * @throws Exception on any incomplete input/process/output contract
     */
    public static void main(String[] args)throws Exception {
        if(args.length!=4||!System.getProperty("os.name").equals("Linux"))throw new IOException("Use Linux Java with exact candidate, existing original home/build receipt and NEW private evidence root");
        Path jar=Path.of(args[0]),home=Path.of(args[1]),build=Path.of(args[2]),root=Path.of(args[3]);
        for(Class<?> type:List.of(TomtomRecipe.class,TomtomInputs.class,TomtomResults.class))if(!type.getProtectionDomain().getCodeSource().getLocation().toURI().equals(jar.toUri()))throw new IOException("Wrong actual candidate class: "+type);
        TomtomRecipe.nativePath(home.toString());TomtomRecipe.nativePath(root.toString());NextflowFiles.directory(home);
        if(Files.exists(root)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Preserve all attempts; supply a NEW canonical Linux evidence directory");
        Files.createDirectory(root,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        try{run(jar,home,build,root);}catch(Exception failure){write(root.resolve("FAILURE.json"),Map.of("status","FAILED_COMPONENT_CONTROLS_NO_ADMISSION","error",failure.toString(),"required_action","Preserve this attempt and all original logs. Correct the named input/process/output failure and retry into NEW paths. No runtime or user analysis was admitted."));throw failure;}
    }
    private static void run(Path jar,Path home,Path build,Path root)throws Exception {
        Map<?,?> report=NextflowFiles.object(NextflowFiles.json(build,2000000));require(report.get("status").equals("built-original-Linux-MEME-Suite-reference-not-scientifically-admitted")&&report.get("version").equals("5.5.9")&&report.get("prefix").equals(home.toString()),"Original build receipt/version/prefix differs");
        Map<?,?> archive=NextflowFiles.object(report.get("original_archive"));require(archive.get("sha256").equals("0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12"),"Original source archive identity differs");
        var installed=NextflowFiles.tree(home,5000,1073741824,268435456,()->{});var expected=NextflowFiles.object(report.get("installed_files"));require(installed.keySet().equals(expected.keySet()),"Complete original installed-file population differs");
        for(var entry:installed.entrySet())require(entry.getValue().kind().equals("file")&&entry.getValue().pin().equals(NextflowFiles.fingerprint(expected.get(entry.getKey()),268435456)),"Installed original payload differs: "+entry.getKey());
        var hostLimits=new RepeatHostFiles.Limits(268435456,1073741824,5000,4,16777216);Set<Path> allowed=Set.of(home);
        var payload=RepeatHostFiles.capture(List.of(home),List.of(),allowed,allowed,hostLimits,()->{});
        write(root.resolve("ORIGINAL-PAYLOAD.json"),payload);write(root.resolve("OWNER.json"),Map.of("scope","SYNTHETIC_COMPONENT_EXECUTION_ONLY_NO_CURRENT_HOST_RUNTIME_TICKET","home",home.toString(),"build_receipt",Map.of("bytes",Files.size(build),"sha256",NextflowFiles.pin(build,2000000,()->{}).sha256()),"candidate",Map.of("bytes",Files.size(jar),"sha256",NextflowFiles.pin(jar,268435456,()->{}).sha256())));
        Path inputs=root.resolve("original-inputs");Files.createDirectory(inputs);var raw=TomtomPreparedInputsTest.archive();
        for(var entry:raw.entrySet())if(entry.getKey().startsWith("inputs/"))Files.write(inputs.resolve(entry.getKey().substring(7)),entry.getValue(),StandardOpenOption.CREATE_NEW);
        var cases=(List<?>)NextflowFiles.object(SimpleJson.parse(new String(raw.get("EXPECTED.json"),StandardCharsets.UTF_8))).get("cases");var receipts=new ArrayList<Map<String,Object>>();
        for(Object value:cases){var spec=(Map<?,?>)value;if(!spec.get("kind").equals("original-complete-input-and-output"))continue;String id=(String)spec.get("id"),member=(String)spec.get("query_input");
            for(String mode:List.of("FULL_REPORT","TEXT")){var recipe=TomtomPreparedInputsTest.recipe(inputs.resolve(member.substring(7)),List.of(inputs.resolve("targets.meme")));recipe.put("output_mode",mode);receipts.add(fit(id+"-"+mode,recipe,home,root,payload,allowed,hostLimits));}
        }
        for(String distance:List.of("kullback","pearson","allr","sandelin","blic1","blic5","llr1","llr5")){
            var recipe=TomtomPreparedInputsTest.recipe(inputs.resolve("mass-1.0.meme"),List.of(inputs.resolve("targets.meme")));recipe.put("distance",distance);recipe.put("output_mode","TEXT");receipts.add(fit("text-distance-"+distance,recipe,home,root,payload,allowed,hostLimits));
        }
        Map<String,byte[]> edge=edgeArchive();Path edgeInputs=root.resolve("edge-inputs");Files.createDirectory(edgeInputs);
        for(String name:List.of("query.meme","targets.meme")){String member="edges/inputs/"+name;if(!edge.containsKey(member)&&name.equals("query.meme"))member="edges/inputs/queries.meme";require(edge.containsKey(member),"Missing complete edge source input: "+member);Files.write(edgeInputs.resolve(name),edge.get(member),StandardOpenOption.CREATE_NEW);}
        var paper=TomtomPreparedInputsTest.recipe(edgeInputs.resolve("query.meme"),List.of(edgeInputs.resolve("targets.meme")));paper.put("distance","kullback");paper.put("output_mode","TEXT");paper.put("threshold",Map.of("kind","QVALUE","value",new java.math.BigDecimal("0.1")));receipts.add(fit("paper-P067-text-scoped-current-edition",paper,home,root,payload,allowed,hostLimits));
        require(receipts.size()==49,"Incomplete declared fresh original protocol family");RepeatHostFiles.verify(payload,allowed,allowed,hostLimits,()->{});
        write(root.resolve("COMPLETED.json"),Map.of("status","PASSED_SYNTHETIC_NATIVE_PREPARED_TOMTOM_COMPONENT_RUNS","protocols",receipts,"fits",49,"full_report_fits",20,"text_fits",29,"runtime_admitted",false,"user_analysis_admitted",false,"public_execution_adapter_available",false,"implementation_credit",false));
        System.out.println("COMPLETE_PREPARED_TOMTOM_RUNS 49 FULL20 TEXT29 NO_RUNTIME_OR_USER_ANALYSIS_ADMISSION");
    }
    private static Map<String,Object> fit(String id,Map<String,Object> recipe,Path home,Path root,RepeatHostFiles.Snapshot payload,Set<Path> allowed,RepeatHostFiles.Limits hostLimits)throws Exception {
        Path folder=root.resolve(id);Files.createDirectory(folder);Files.createDirectory(folder.resolve("spec"));Path source=folder.resolve("spec/request.json");Files.writeString(source,RsemJson.json(recipe)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var request=TomtomRecipe.read(source,()->{});var context=TomtomInputs.capture(folder.resolve("prepared"),request,()->{});Path output=request.mode()==TomtomRecipe.Mode.TEXT?null:folder.resolve("process/result");
        List<String> command=request.arguments(home.resolve("bin/tomtom").toString(),context.query().toString(),context.targets(),context.backgroundFile(),output==null?null:output.toString());var expected=context.expected(command);
        RsemProcess.Check integrity=()->{RepeatHostFiles.verify(payload,allowed,allowed,hostLimits,()->{});context.verify(true,()->{});};
        Map<String,String> environment=Map.of("PATH",home.resolve("bin")+":"+home.resolve("libexec/meme-5.5.9")+":/usr/bin:/bin","LC_ALL","C","LANG","C","TZ","UTC");
        var result=RsemProcess.executeOwned(command,environment,folder.resolve("process"),request.processLimits(),integrity,()->{},List.of(folder.resolve("prepared"),folder.resolve("spec")));
        require(result.exitCode()==0&&result.ownedLiveAfterCleanup().isEmpty(),"Original process/cleanup did not complete");long rows,pairs;String tableHash;
        if(output==null){var audit=TomtomResults.readText(result.stdout(),expected,request.outputLimits(),()->{});rows=audit.matches().size();pairs=audit.pairs().size();tableHash=audit.sha256();}
        else{var audit=TomtomResults.read(output.resolve("tomtom.xml"),output.resolve("tomtom.tsv"),expected,request.outputLimits(),()->{});rows=audit.matches().size();pairs=audit.pairs().size();tableHash=NextflowFiles.pin(output.resolve("tomtom.tsv"),request.capacity("table_bytes"),()->{}).sha256();
            String html=NextflowFiles.utf8(NextflowFiles.bytes(output.resolve("tomtom.html"),8000000));require(html.contains("@JSON_VAR data")&&html.contains("var data ="),"Incomplete requested original HTML report");
        }
        integrity.verify();var receipt=new LinkedHashMap<String,Object>();receipt.put("id",id);receipt.put("mode",request.mode().name());receipt.put("source_input_files",context.files().size());receipt.put("complete_declared_pairs",pairs);receipt.put("complete_reported_rows",rows);receipt.put("table_sha256",tableHash);receipt.put("command",command);receipt.put("owned_live_after_cleanup",result.ownedLiveAfterCleanup());receipt.put("native_output_census",TomtomInputs.tree(folder,request,()->{}));receipt.put("runtime_admitted",false);receipt.put("user_analysis_admitted",false);
        write(folder.resolve("CONTROL-COMPLETED.json"),receipt);Files.writeString(root.resolve("PROGRESS.json"),RsemJson.json(Map.of("last_completed",id,"status","RUNNING_SYNTHETIC_COMPONENT_CONTROLS_NO_ADMISSION"))+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);System.out.println(id+": complete rows="+rows+" pairs="+pairs);return receipt;
    }
    private static Map<String,byte[]> edgeArchive()throws Exception {
        byte[] raw;try(var in=TomtomPreparedRunControls.class.getResourceAsStream("/org/jlinalg/sequence/tomtom-contracts/original-comparison-contracts.zip")){if(in==null)throw new IOException("Missing complete original edge fixtures");raw=in.readAllBytes();}require(NextflowFiles.sha(raw).equals("62a6e0090ab1a6cbddc4ef2f0bbb5936863a04601efd66e27ba686bab6c7b765"),"Edge fixtures changed");var result=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var e=z.getNextEntry();e!=null;e=z.getNextEntry())result.put(e.getName(),z.readAllBytes());}return result;
    }
    private static void write(Path path,Object value)throws IOException {Files.writeString(path,RsemJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
