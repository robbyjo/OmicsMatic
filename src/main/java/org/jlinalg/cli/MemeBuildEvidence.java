/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Original source/build receipts establish provenance only; native runtime admission is separate. */
final class MemeBuildEvidence {
    private MemeBuildEvidence() { }
    record Context(Path root,String prefix,Map<String,NextflowFiles.Pin> installed,NextflowFiles.Pin completion,
                   Map<String,Object> inventory,MemeRuntimeFiles.Limits limits) {
        Context {installed=Map.copyOf(installed);inventory=Map.copyOf(inventory);}
        void verify(Runnable check)throws IOException {MemeRuntimeFiles.require(inventory.equals(MemeRuntimeFiles.tree(root,limits,check)),"Original MEME source/build evidence namespace, bytes or modes changed");}
    }
    static Context read(Path root,MemeRuntimeFiles.Limits limits,Runnable check)throws IOException {
        NextflowFiles.directory(root);check.run();Path completion=root.resolve("COMPLETED.json");var pin=NextflowFiles.pin(completion,2097152,check);var report=NextflowFiles.object(NextflowFiles.json(completion,2097152));var lock=MemeRuntimeFiles.lock();
        require(NextflowFiles.integer(report.get("schema_version"),1,1)==1&&java.util.Objects.equals(report.get("status"),"built-original-Linux-MEME-Suite-reference-not-scientifically-admitted")&&java.util.Objects.equals(report.get("version"),"5.5.9"),"Require completed exact original MEME 5.5.9 build evidence");
        require(Boolean.FALSE.equals(report.get("scientific_analysis_run"))&&Boolean.FALSE.equals(report.get("runtime_admitted"))&&Boolean.FALSE.equals(report.get("project_integration_credited")),"Original MEME build receipts cannot admit scientific runs or integrations");
        require(java.util.Objects.equals(report.get("intended_use"),"educational-research-nonprofit; directly confirmed by user in this goal"),"Original MEME reference evidence must retain its declared research/educational/nonprofit use; do not infer commercial permission");
        require(NextflowFiles.fingerprint(report.get("original_archive"),100000000).equals(NextflowFiles.fingerprint(lock.get("original_archive"),100000000))&&java.util.Objects.equals(report.get("source_url"),lock.get("source_url")),"Original MEME official archive/source identity differs");
        require(NextflowFiles.fingerprint(report.get("script_pin"),2097152).equals(NextflowFiles.fingerprint(lock.get("reference_builder"),2097152)),"Original MEME reference builder source differs");
        require(NextflowFiles.object(report.get("original_C_and_H")).equals(NextflowFiles.object(lock.get("original_C_and_H")))&&NextflowFiles.integer(report.get("unchanged_original_C_and_H"),615,615)==615,"Complete original MEME C/H source population differs from the official archive");
        require(!report.containsKey("changed_original_C_and_H")&&NextflowFiles.object(report.get("installed_symlinks")).isEmpty(),"Changed source or linked MEME installed payload is unsupported");
        String area=NextflowFiles.text(report.get("linux_area")),prefix=NextflowFiles.text(report.get("prefix"));TomtomRecipe.nativePath(area);TomtomRecipe.nativePath(prefix);require(prefix.equals(area+"/home-5.5.9"),"Original MEME compiled prefix/area relationship differs");
        var expectedSteps=NextflowFiles.list(lock.get("expected_build_steps"));var steps=NextflowFiles.list(report.get("steps"));require(steps.size()==expectedSteps.size(),"Complete original MEME build-step population differs");
        var names=new ArrayList<String>();
        for(int i=0;i<steps.size();i++){
            check.run();var step=NextflowFiles.object(steps.get(i));var spec=NextflowFiles.object(expectedSteps.get(i));String name=NextflowFiles.text(spec.get("name"));names.add(name);
            NextflowFiles.keys(step,"name","command","cwd","exit_code","timed_out","log_pin");
            require(java.util.Objects.equals(step.get("name"),name)&&NextflowFiles.integer(step.get("exit_code"),0,0)==0&&Boolean.FALSE.equals(step.get("timed_out")),"Original MEME build step did not complete successfully: "+name);
            var command=new ArrayList<String>();for(String token:NextflowFiles.strings(spec.get("command")))command.add(expand(token,area,prefix));
            require(NextflowFiles.strings(step.get("command")).equals(command)&&NextflowFiles.text(step.get("cwd")).equals(expand(NextflowFiles.text(spec.get("cwd")),area,prefix)),"Original MEME typed build invocation differs: "+name);
            Path log=root.resolve(name+".log");require(NextflowFiles.fingerprint(step.get("log_pin"),limits.fileBytes()).equals(NextflowFiles.pin(log,limits.fileBytes(),check)),"Complete original MEME build log changed: "+name);
            if(name.endsWith("-version"))require(NextflowFiles.utf8(NextflowFiles.bytes(log,1048576)).strip().equals("5.5.9"),"Original exact MEME program version output differs: "+name);
        }
        require(names.equals(List.of("configure","make","install","meme-version","tomtom-version","streme-version","centrimo-version")),"Unexpected original MEME reference step order");
        require(NextflowFiles.object(NextflowFiles.json(root.resolve("ORIGINAL-SOURCE-PINS.json"),2097152)).equals(NextflowFiles.object(lock.get("original_C_and_H"))),"Retained original MEME source-pin manifest differs");
        var installed=new TreeMap<String,NextflowFiles.Pin>();long bytes=0;
        for(var entry:NextflowFiles.object(report.get("installed_files")).entrySet()){
            String name=NextflowFiles.member(NextflowFiles.text(entry.getKey()));var value=NextflowFiles.fingerprint(entry.getValue(),268435456);bytes=Math.addExact(bytes,value.bytes());require(bytes<=2147483648L&&installed.size()<100000,"Original complete MEME payload exceeds source-evidence capacity");installed.put(name,value);
        }
        require(installed.keySet().containsAll(Set.of("bin/tomtom","libexec/meme-5.5.9/tomtom_xml_to_html","lib/meme-5.5.9/perl/TomtomSAX.pm","lib/meme-5.5.9/perl/HtmlMonolithWr.pm")),"Original complete MEME reference payload lacks required scoped program/report assets");
        require(pin.equals(NextflowFiles.pin(completion,2097152,check)),"Original MEME completion changed during audit");var inventory=MemeRuntimeFiles.tree(root,limits,check);var context=new Context(root,prefix,installed,pin,inventory,limits);context.verify(check);return context;
    }
    static void verifyPayload(Context evidence,Path home,Runnable check)throws IOException {
        require(home.toString().equals(evidence.prefix()),"Original MEME binaries embed their configured prefix. Rebuild into the intended NEW prefix; do not assume a copied home is relocatable");
        var inventory=NextflowFiles.tree(home,100000,2147483648L,268435456,check);require(inventory.keySet().equals(evidence.installed().keySet()),"Complete original MEME installed file namespace differs from source/build evidence");
        for(var entry:inventory.entrySet()){check.run();require(entry.getValue().kind().equals("file")&&entry.getValue().pin().equals(evidence.installed().get(entry.getKey())),"Original MEME installed payload differs: "+entry.getKey());}
        if(!Files.isExecutable(home.resolve("bin/tomtom"))||!Files.isReadable(home.resolve("bin/tomtom"))||!Files.isRegularFile(home.resolve("bin/tomtom"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Original Tomtom executable is unavailable or execution is forbidden");
    }
    private static String expand(String value,String area,String home){return value.replace("@HOME@",home).replace("@AREA@",area);}
    private static void require(boolean condition,String message)throws IOException {MemeRuntimeFiles.require(condition,message);}
}
