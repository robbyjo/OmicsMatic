/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Portable managed execution evidence. Literal software payloads are data, never host-resolved links. */
final class SnakemakeOriginalManagedBundle {
    static final String SCOPE="Complete captured declared managed workflow evidence, independently checked against original parser, process, installation, job and output records. Software payload links and devices are literal data; no producer path is accessed by verification. This is not a dependency sandbox, biological/author-environment certification or whole-paper reproduction. Genuine managed replay, public analysis CLI and paper-role admission remain under qualification. No user analysis or catalog credit is admitted.";
    static final Set<String> TOP_FILES=Set.of("REQUEST.json","request-source.json","runtime-context.json","deployment-context.json","tools.json","capture.json","parameters.json","COMMAND.json","CONTROLLER.json","invocation.json","working-before.json","working-after.json","software-before.json","software-after.json","code.json","managed-code.json","completion-events.json","DIAGNOSTICS.json","outputs.json","WARNINGS.md","EXECUTED.json","INSTALLATION-POLICY.json","managed-process-binding.json","engine-context.json","provider-context.json","supervised-invocation.json","MANAGED-HELPER-PROVENANCE.json","SUPERVISED-POLICY.json","SUPERVISION_WARNINGS.md");
    private static final Set<String> FAMILIES=Set.of("runtime","deployment","events","process","helpers","policy-records");
    private SnakemakeOriginalManagedBundle() { }

    static SnakemakeOriginalBundle.Verified run(Path recipe,Path runtime,Path output,Runnable check)throws IOException {
        var result=SnakemakeOriginalWorkflow.execute(recipe,runtime,output,check);
        try{return finish(result.root(),SnakemakeOriginalRecipe.read(result.root().resolve("REQUEST.json")),check);}
        catch(IOException|RuntimeException error){
            try{writeJson(result.root().resolve("FAILURE.json"),Map.of("status","FAIL2-managed-bundle-not-admitted","exit_code",2,"error",error.toString(),"required_action","Retain the complete original execution and provisional bundle. Resolve the discrepancy, then retry NEW output; no completed managed bundle is admitted."));Files.writeString(result.root().resolve("FAILURE_WARNINGS.md"),"# FAIL2 managed bundle not admitted\n\n"+error+"\n\nRetain every original input, software capture and diagnostic. Resolve the named discrepancy and retry NEW paths.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){error.addSuppressed(diagnostic);}
            if(error instanceof IOException io)throw io;throw new IOException("Managed bundle incomplete; no result admitted",error);
        }
    }

    static SnakemakeOriginalBundle.Verified finish(Path session,SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        directory(session);if(Files.exists(session.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS)||request.managed()==null)throw new IOException("Failed/nonmanaged execution cannot become a managed bundle");
        var after=SnakemakeOriginalCapture.decode(json(session.resolve("working-after.json"),request.metadataBytes()),request.capture());
        if(!after.equals(SnakemakeOriginalCapture.working(session,session.resolve("workspace"),request.capture(),check)))throw new IOException("Owned workflow state changed after terminal capture");
        var software=software(session,"software-after.json",request);SnakemakeOriginalSoftwareCapture.verifyLive(software,request.capture(),check);
        var closure=closure(session,request);var metadata=new TreeMap<String,Pin>();
        Files.walkFileTree(session,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {
                check.run();if(path.equals(session))return FileVisitResult.CONTINUE;
                String name=member(session.relativize(path).toString());
                if(Set.of("workspace","managed","objects").contains(name))return FileVisitResult.SKIP_SUBTREE;
                if(!FAMILIES.contains(name.split("/",2)[0]))throw new IOException("Unexpected managed execution metadata directory: "+name);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                check.run();if(!attrs.isRegularFile())throw new IOException("Control evidence must be ordinary files; software payloads have separate literal capture");
                String name=member(session.relativize(path).toString());if(metadata.size()>=request.process().fileCount())throw new IOException("Managed metadata member capacity exceeded");
                metadata.put(name,pin(path,request.capture().fileBytes(),check));return FileVisitResult.CONTINUE;
            }
        });
        var observed=new HashSet<String>();try(var stream=Files.list(session.resolve("objects"))){for(Path path:stream.toList()){if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.getFileName().toString().matches("[a-f0-9]{64}"))throw new IOException("Unexpected managed captured object-store member");observed.add(path.getFileName().toString());}}
        if(!observed.equals(closure.keySet()))throw new IOException("Managed source capture has missing or unreferenced objects; preserve it for review");
        Path root=Files.createDirectory(session.resolve("bundle")),objects=Files.createDirectory(root.resolve("objects"));
        for(var row:closure.entrySet())SnakemakeOriginalCapture.store(objects,session.resolve("objects").resolve(row.getKey()),row.getValue(),request.capture().fileBytes(),check);
        for(var row:metadata.entrySet()){Path target=root.resolve(row.getKey());Files.createDirectories(target.getParent());Files.copy(session.resolve(row.getKey()),target);if(!pin(target,request.capture().fileBytes(),check).equals(row.getValue()))throw new IOException("Managed control evidence changed while copying: "+row.getKey());}
        writeJson(root.resolve("blob-index.json"),closure);Files.writeString(root.resolve("BUNDLE_WARNINGS.md"),warningText(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var inventory=SnakemakeOriginalBundle.inventory(root,request,Set.of(),check);writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"tool","Snakemake","files",inventory.files(),"directories",inventory.directories()));
        Pin manifest=pin(root.resolve("manifest.json"),request.metadataBytes(),check);writeJson(root.resolve("CANDIDATE.json"),marker(manifest,false));
        verify(root,"CANDIDATE.json",check);
        // Recheck all selected live cache roots before atomic completion; no installed prefix is moved.
        SnakemakeOriginalSoftwareCapture.verifyLive(software,request.capture(),check);
        byte[] complete=(SnakemakeOriginalJson.json(marker(manifest,true))+"\n").getBytes(StandardCharsets.UTF_8);SnakemakeOriginalProcess.checkManagedCapacity(session,request.process(),complete.length,1,List.of(session.resolve("managed")));
        Files.write(root.resolve("COMPLETED.new.json"),complete,StandardOpenOption.CREATE_NEW);Files.move(root.resolve("COMPLETED.new.json"),root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);Files.delete(root.resolve("CANDIDATE.json"));
        return verify(root,"COMPLETED.json",check);
    }

    static SnakemakeOriginalBundle.Verified verify(Path root,String markerName,Runnable check)throws IOException {
        directory(root);var request=SnakemakeOriginalRecipe.recorded(object(json(root.resolve("REQUEST.json"),4_194_304)));
        if(request.managed()==null||request.execution().deployment().equals("preinstalled"))throw new IOException("A managed bundle requires the complete schema-2 managed request");
        Map<?,?> marker=object(json(root.resolve(markerName),65536));
        keys(marker,"schema_version","tool","kind","status","manifest","user_analysis_admitted","biological_accuracy_certified","whole_paper_reproduced");integer(marker.get("schema_version"),1,1);
        Pin manifest=pin(root.resolve("manifest.json"),request.metadataBytes(),check);equal(marker(manifest,markerName.equals("COMPLETED.json")),marker,"managed completion claim");
        Map<?,?> declared=object(json(root.resolve("manifest.json"),request.metadataBytes()));keys(declared,"schema_version","tool","files","directories");integer(declared.get("schema_version"),1,1);if(!"Snakemake".equals(declared.get("tool")))throw new IOException("Wrong managed manifest tool");
        var actual=SnakemakeOriginalBundle.inventory(root,request,Set.of("manifest.json",markerName),check);var files=new TreeMap<String,Pin>();for(var row:object(declared.get("files")).entrySet())files.put(member(text(row.getKey())),fingerprint(row.getValue(),request.capture().fileBytes()));
        if(!actual.files().equals(files)||!actual.directories().equals(new TreeSet<>(strings(declared.get("directories")))))throw new IOException("Complete managed bundle file/directory census or bytes differ");
        for(String name:TOP_FILES)if(!files.containsKey(name))throw new IOException("Missing managed execution record: "+name);
        equal(SnakemakeOriginalWorkflow.code(),json(root.resolve("code.json"),request.metadataBytes()),"managed original producer/reader edition");equal(SnakemakeOriginalManagedWorkflow.code(),json(root.resolve("managed-code.json"),request.metadataBytes()),"complete managed producer/reader/helper edition");
        var inputs=SnakemakeOriginalCapture.decode(json(root.resolve("capture.json"),request.metadataBytes()),request.capture());
        var before=SnakemakeOriginalCapture.decode(json(root.resolve("working-before.json"),request.metadataBytes()),request.capture());
        var after=SnakemakeOriginalCapture.decode(json(root.resolve("working-after.json"),request.metadataBytes()),request.capture());
        Map<?,?> invocation=object(json(root.resolve("invocation.json"),request.metadataBytes()));keys(invocation,"owned_root","working_directory","environment","bindings","runtime","providers","software_roots","borrowed_roots","selected_deployment");String producer=absolute(text(invocation.get("owned_root")));
        for(var state:List.of(before,after))if(state.roots().size()!=1||!state.roots().get(0).source().equals(producer+"/workspace")||!state.roots().get(0).leaf().equals("workspace")||!state.aliases().equals(Map.of("working","r0/workspace")))throw new IOException("Managed working capture is not the complete owned workspace");
        sources(request,inputs);initial(producer,inputs,before);var bindings=bindings(producer,inputs);String working=bindings.get("workflow");equal(bindings,invocation.get("bindings"),"managed complete source/resource bindings");
        if(!working.equals(invocation.get("working_directory"))||!request.execution().deployment().equals(invocation.get("selected_deployment")))throw new IOException("Managed original cwd or deployment differs");
        Map<?,?> source=object(json(root.resolve("request-source.json"),request.metadataBytes()));keys(source,"path","identity");absolute(text(source.get("path")));if(!pin(root.resolve("REQUEST.json"),4_194_304,check).equals(fingerprint(object(source.get("identity")).get("pin"),4_194_304)))throw new IOException("Managed captured request differs from original request bytes");
        String parameters=SnakemakeOriginalJson.parameters(SnakemakeOriginalParameters.bindRecorded(request.parameters(),bindings,producer+"/workspace/published"))+"\n";
        if(!parameters.equals(utf8(bytes(root.resolve("parameters.json"),request.metadataBytes()))))throw new IOException("Lossless managed parameter binding/precision/order differs");
        var runtime=SnakemakeOriginalEvidence.runtime(root,request,check);var providers=SnakemakeOriginalDeployment.recorded(root,check);
        if(!runtime.get("home").equals(invocation.get("runtime"))||!providers.get("home").equals(invocation.get("providers"))||!request.managed().providers().equals(providers.get("home")))throw new IOException("Managed declared/adopted provider or runtime path differs");
        var roots=SnakemakeOriginalManagedEvidence.softwareRoots(producer,request);equal(roots,invocation.get("software_roots"),"managed software root bindings");var borrowed=new TreeMap<String,String>();if(request.managed().environments()!=null)borrowed.put("environments",roots.get("environments"));if(request.managed().images()!=null)borrowed.put("images",roots.get("images"));equal(borrowed,invocation.get("borrowed_roots"),"immutable borrowed software roots");
        var softwareBefore=software(root,"software-before.json",request);var softwareAfter=software(root,"software-after.json",request);
        for(var state:List.of(softwareBefore,softwareAfter)){var paths=new TreeMap<String,String>();for(var row:state.roots().entrySet())paths.put(row.getKey(),row.getValue().source());if(!paths.equals(roots))throw new IOException("Complete managed software capture roots differ from the recipe");}
        if(borrowed.isEmpty()){if(files.containsKey("borrowed-software-before.json"))throw new IOException("Unexpected borrowed software capture");}
        else {var saved=software(root,"borrowed-software-before.json",request);if(!saved.roots().keySet().equals(borrowed.keySet()))throw new IOException("Incomplete immutable borrowed software family");for(String key:borrowed.keySet())if(!saved.roots().get(key).equals(softwareBefore.roots().get(key))||!saved.roots().get(key).equals(softwareAfter.roots().get(key)))throw new IOException("Borrowed software/cache bytes, links, identities, metadata or complete census changed");}
        Map<?,?> config=object(json(root.resolve("INSTALLATION-POLICY.json"),request.metadataBytes()));String run=text(config.get("run_id"));equal(SnakemakeOriginalManagedEvidence.environment(producer,text(runtime.get("environment")),request,run),invocation.get("environment"),"managed declared clean environment");
        var bound=SnakemakeOriginalManagedEvidence.audit(root,producer,working,request,runtime,providers,check);
        Map<?,?> controller=object(json(root.resolve("CONTROLLER.json"),request.metadataBytes()));var events=SnakemakeOriginalEvents.managed(root.resolve("events"),controller,bound,request.eventRows(),request.metadataBytes(),request.logBytes(),request.execution().requireJobs(),check);
        equal(SnakemakeOriginalWorkflow.eventsAsJson(events),json(root.resolve("completion-events.json"),request.metadataBytes()),"complete independent managed job/event audit");equal(events.diagnostics(),json(root.resolve("DIAGNOSTICS.json"),request.metadataBytes()),"complete original managed warnings/errors");
        jobPaths(root,producer,working,runtime,softwareAfter,events);
        var owned=snapshot(after);for(var row:owned.entrySet())if(row.getValue().kind().equals("symlink")){var target=resolve(owned,row.getKey(),true);if(!within(target.path(),producer+"/workspace")||row.getValue().resolved()!=null&&!target.path().equals(row.getValue().resolved()))throw new IOException("Managed workflow link escapes or disagrees with captured working state");}
        var outputs=SnakemakeOriginalBundle.outputs(root,request,producer,bindings,owned,check);SnakemakeOriginalBundle.tools(request,root,before,after);
        var expected=new TreeSet<>(TOP_FILES);expected.addAll(List.of("blob-index.json","BUNDLE_WARNINGS.md"));if(!borrowed.isEmpty())expected.add("borrowed-software-before.json");
        addNames(expected,"runtime",SnakemakeOriginalEvidence.RUNTIME_FILES);addNames(expected,"deployment",object(providers.get("metadata")).keySet());addNames(expected,"helpers",SnakemakeOriginalManagedProcess.HELPERS);addNames(expected,"events",object(controller.get("collector_streams")).keySet());addNames(expected,"policy-records",bound.policy().files().keySet());addNames(expected,"process",List.of("launch.json","process.json","process.stdout","process.stderr","WARNINGS.md"));
        for(String name:SnakemakeOriginalManagedProcess.HELPERS){byte[] raw=SnakemakeOriginalManagedProcess.resource(name);if(!pin(root.resolve("helpers").resolve(name),1_048_576,check).equals(new Pin(raw.length,sha(raw))))throw new IOException("Managed helper bytes differ: "+name);}
        byte[] provenance=SnakemakeOriginalManagedProcess.resource("PROVENANCE.json");if(!pin(root.resolve("MANAGED-HELPER-PROVENANCE.json"),1_048_576,check).equals(new Pin(provenance.length,sha(provenance))))throw new IOException("Managed helper provenance differs");
        var closure=closure(root,request);equal(closure,json(root.resolve("blob-index.json"),request.metadataBytes()),"complete managed capture blob closure");
        for(var row:closure.entrySet()){expected.add("objects/"+row.getKey());if(!row.getValue().equals(files.get("objects/"+row.getKey())))throw new IOException("Managed input/software/output object bytes differ");}
        if(!expected.equals(files.keySet())||!parentDirectories(expected).equals(actual.directories()))throw new IOException("Managed complete metadata/object/directory family differs");
        if(!warningText().equals(utf8(bytes(root.resolve("BUNDLE_WARNINGS.md"),request.metadataBytes())))||!SnakemakeOriginalManagedWorkflow.warningText(events).equals(utf8(bytes(root.resolve("WARNINGS.md"),request.metadataBytes())))||!("# WARNING: supervised process and policy only\n\n"+SnakemakeOriginalManagedProcess.ACTION+"\n").equals(utf8(bytes(root.resolve("SUPERVISION_WARNINGS.md"),request.metadataBytes())))||!SnakemakeOriginalProcess.warning(null).equals(utf8(bytes(root.resolve("process/WARNINGS.md"),request.metadataBytes()))))throw new IOException("Managed original/source/process/bundle warnings are missing or changed");
        Map<?,?> executed=object(json(root.resolve("EXECUTED.json"),request.metadataBytes()));keys(executed,"status","run_id","logical_jobs","error_diagnostics","outputs","user_analysis_admitted","catalog_credit","required_action");
        if(!"SOURCE_EXECUTED_WITH_CAPTURED_INPUTS_NOT_FINAL_WORKFLOW_ADMISSION".equals(executed.get("status"))||!run.equals(executed.get("run_id"))||integer(executed.get("logical_jobs"),0,Integer.MAX_VALUE)!=events.jobs().size()||integer(executed.get("error_diagnostics"),0,Integer.MAX_VALUE)!=events.errorDiagnostics()||integer(executed.get("outputs"),0,Integer.MAX_VALUE)!=outputs.size()||!Boolean.FALSE.equals(executed.get("user_analysis_admitted"))||integer(executed.get("catalog_credit"),0,0)!=0||!SnakemakeOriginalWorkflow.ACTION.equals(executed.get("required_action")))throw new IOException("Managed execution declaration differs from independently audited raw records");
        return new SnakemakeOriginalBundle.Verified(root,manifest,root,producer,request,inputs,outputs,events);
    }

    private static Map<String,Object> marker(Pin manifest,boolean complete){return Map.of("schema_version",1,"tool","Snakemake","kind","declared-managed-workflow","status",complete?"completed-declared-managed-workflow-evidence":"candidate-declared-managed-workflow-evidence","manifest",manifest,"user_analysis_admitted",false,"biological_accuracy_certified",false,"whole_paper_reproduced",false);}
    private static String warningText(){return "# WARNING: managed workflow evidence scope\n\n"+SCOPE+"\n";}
    private static SnakemakeOriginalSoftwareCapture.Snapshot software(Path root,String name,SnakemakeOriginalRecipe.Request request)throws IOException {return SnakemakeOriginalSoftwareCapture.decode(SnakemakeOriginalParameters.parse(utf8(bytes(root.resolve(name),request.metadataBytes())),request.metadataBytes(),10_000_000),request.capture());}
    private static Map<String,Pin> closure(Path root,SnakemakeOriginalRecipe.Request request)throws IOException {
        var result=new TreeMap<String,Pin>();for(String name:List.of("capture.json","working-before.json","working-after.json"))capturePins(SnakemakeOriginalCapture.decode(json(root.resolve(name),request.metadataBytes()),request.capture()),result);
        for(String name:List.of("software-before.json","software-after.json","borrowed-software-before.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))for(var tree:software(root,name,request).roots().values())for(var node:tree.nodes().values())if(node.content()!=null)add(result,node.content());
        for(Object value:list(json(root.resolve("outputs.json"),request.metadataBytes())))for(Object entry:object(object(value).get("members")).values()){Object pin=object(entry).get("pin");if(pin!=null)add(result,fingerprint(pin,request.capture().fileBytes()));}return Map.copyOf(result);
    }
    private static void addNames(Set<String> names,String prefix,Iterable<?> entries)throws IOException {for(Object name:entries)names.add(prefix+'/'+member(text(name)));}
    private static Set<String> parentDirectories(Set<String> files){var result=new TreeSet<String>();for(String file:files){int slash=file.lastIndexOf('/');while(slash>=0){result.add(file.substring(0,slash));slash=file.lastIndexOf('/',slash-1);}}return result;}
    private static void jobPaths(Path root,String producer,String working,Map<?,?> runtime,SnakemakeOriginalSoftwareCapture.Snapshot software,SnakemakeOriginalEvents.Result events)throws IOException {
        var selected=new HashSet<String>();
        for(String line:utf8(bytes(root.resolve("runtime/runtime-files.tsv"),67_108_864)).split("\n")){String[] row=line.split("\t",-1);if(row.length==8&&!row[0].equals("root")){selected.add(row[6]);selected.add(row[0].equals("env")?runtime.get("environment")+"/"+row[1]:row[1]);}}
        for(var tree:software.roots().values())for(String name:tree.nodes().keySet())selected.add(join(tree.source(),name));
        for(var job:events.jobs().values()){
            for(String input:job.inputs()){if(input.matches("[A-Za-z][A-Za-z0-9+.-]*://.*"))throw new IOException("Observed remote job input is not a captured local resource; download where permitted, declare the captured binding and retry NEW output");String path=link(working,input);if(!within(path,producer+"/workspace")&&!selected.contains(path))throw new IOException("Observed managed job input is outside captured resources/software: "+input+". Declare the input and use its captured binding; no external input is silently admitted.");}
            for(String output:job.outputs())if(!within(link(working,output),producer+"/workspace"))throw new IOException("Observed managed job output escapes the complete captured workflow workspace: "+output);
        }
    }
}
