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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Complete portable evidence of a declared local workflow, independent of producer paths and runtime. */
final class SnakemakeOriginalBundle {
    static final Set<String> TOP_FILES=Set.of("REQUEST.json","request-source.json","runtime-context.json","tools.json","capture.json","parameters.json","COMMAND.json","DRIVER.json","invocation.json","working-before.json","working-after.json","code.json","completion-events.json","DIAGNOSTICS.json","outputs.json","WARNINGS.md","EXECUTED.json");
    record Verified(Path root,Pin manifest,Path executionRoot,String producer,SnakemakeOriginalRecipe.Request request,SnakemakeOriginalCapture.Snapshot inputs,List<SnakemakeOriginalWorkflow.Published> outputs,SnakemakeOriginalEvents.Result events) {Verified {outputs=List.copyOf(outputs);}}
    record Inventory(Map<String,Pin> files,Set<String> directories) { }
    private SnakemakeOriginalBundle() { }
    static Verified finish(Path session,SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        directory(session);if(Files.exists(session.resolve("FAILURE.json")))throw new IOException("A failed workflow cannot be sealed");
        var inputs=SnakemakeOriginalCapture.decode(json(session.resolve("capture.json"),request.metadataBytes()),request.capture());var before=SnakemakeOriginalCapture.decode(json(session.resolve("working-before.json"),request.metadataBytes()),request.capture());var after=SnakemakeOriginalCapture.decode(json(session.resolve("working-after.json"),request.metadataBytes()),request.capture());
        if(!after.equals(SnakemakeOriginalCapture.working(session,session.resolve("workspace"),request.capture(),check)))throw new IOException("Working state changed after the terminal capture");
        Path root=Files.createDirectory(session.resolve("bundle"));var closure=new TreeMap<String,Pin>();capturePins(inputs,closure);capturePins(before,closure);capturePins(after,closure);
        for(Object output:list(json(session.resolve("outputs.json"),request.metadataBytes())))for(Object value:object(object(output).get("members")).values()){Map<?,?> member=object(value);if(member.get("pin")!=null)add(closure,fingerprint(member.get("pin"),request.capture().fileBytes()));}
        var metadata=new TreeMap<String,Node>();
        Files.walkFileTree(session,new SimpleFileVisitor<>(){
            private String name(Path path){return session.relativize(path).toString();}
            private void charge()throws IOException {check.run();if(metadata.size()>=request.process().fileCount())throw new IOException("Complete recorded owned-state capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {
                if(path.equals(root))return FileVisitResult.SKIP_SUBTREE;charge();metadata.put(name(path),new Node("directory",null,mode(path),null,null));
                return path.equals(session.resolve("workspace"))||path.equals(session.resolve("objects"))?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {
                charge();String name=name(path);member(name);
                if(attrs.isRegularFile()){var identity=identity(path,request.capture().fileBytes(),check);SnakemakeOriginalCapture.store(session.resolve("objects"),path,identity.pin(),request.capture().fileBytes(),check);add(closure,identity.pin());metadata.put(name,new Node("file",identity.pin(),identity.mode(),null,null));}
                else if(attrs.isSymbolicLink()){String resolved;try{resolved=path.toRealPath().toString();}catch(java.nio.file.NoSuchFileException missing){resolved=null;}metadata.put(name,new Node("symlink",null,-1,Files.readSymbolicLink(path).toString(),resolved));}
                else {int bits=((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue(),kind=bits&0170000;if(kind!=0010000&&kind!=0140000)throw new IOException("Unknown owned special object");metadata.put(name,new Node(kind==0010000?"fifo":"socket",null,bits&0777,null,null));}
                return FileVisitResult.CONTINUE;
            }
        });
        var actualObjects=new HashSet<String>();try(var paths=Files.list(session.resolve("objects"))){for(Path object:paths.toList()){String name=object.getFileName().toString();if(!name.matches("[a-f0-9]{64}")||!Files.isRegularFile(object,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected captured object-store member");actualObjects.add(name);}}
        if(!actualObjects.equals(closure.keySet()))throw new IOException("Unreferenced or missing captured object; preserve the run for review");
        Path objects=Files.createDirectory(root.resolve("objects"));for(var entry:closure.entrySet()){Path source=session.resolve("objects").resolve(entry.getKey());if(!pin(source,request.capture().fileBytes(),check).equals(entry.getValue()))throw new IOException("Captured blob changed before sealing");SnakemakeOriginalCapture.store(objects,source,entry.getValue(),request.capture().fileBytes(),check);metadata.put("objects/"+entry.getKey(),new Node("file",entry.getValue(),mode(source),null,null));}
        for(String name:TOP_FILES)copy(session,root,name,metadata,request,check);
        for(String directory:List.of("events","runtime","process","helpers"))for(var entry:metadata.entrySet())if(entry.getKey().startsWith(directory+"/")&&entry.getValue().kind().equals("file"))copy(session,root,entry.getKey(),metadata,request,check);
        writeJson(root.resolve("file-records.json"),Map.of("schema_version",1,"producer_root",session.toString(),"nodes",metadata));writeJson(root.resolve("blob-index.json"),closure);
        Inventory inventory=inventory(root,request,Set.of(),check);writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"tool","Snakemake","files",inventory.files(),"directories",inventory.directories()));Pin manifest=pin(root.resolve("manifest.json"),request.metadataBytes(),check);
        writeJson(root.resolve("CANDIDATE.json"),marker(manifest,"candidate-declared-local-workflow"));Verified verified=verifyLocal(root,"CANDIDATE.json",check);
        byte[] complete=(SnakemakeOriginalJson.json(marker(manifest,"completed-declared-local-workflow"))+"\n").getBytes(StandardCharsets.UTF_8);SnakemakeOriginalProcess.checkCapacity(session,request.process(),complete.length,1);
        Files.write(root.resolve("COMPLETED.new.json"),complete,StandardOpenOption.CREATE_NEW);Files.move(root.resolve("COMPLETED.new.json"),root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);Files.delete(root.resolve("CANDIDATE.json"));return verified;
    }
    private static Map<String,Object> marker(Pin manifest,String status){return Map.of("schema_version",1,"tool","Snakemake","kind","declared-local-workflow","status",status,"manifest",manifest,"biological_accuracy_certified",false,"whole_paper_reproduced",false);}
    private static void copy(Path session,Path root,String name,Map<String,Node> records,SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        Node node=records.get(name);if(node==null||!node.kind().equals("file"))throw new IOException("Missing complete workflow metadata: "+name);Path target=root.resolve(name);Files.createDirectories(target.getParent());Files.copy(session.resolve(name),target);if(!pin(target,request.capture().fileBytes(),check).equals(node.pin()))throw new IOException("Workflow metadata changed while sealing: "+name);
    }
    static Verified verify(Path supplied,Runnable check)throws IOException {
        return verify(supplied,check,0);
    }
    static Verified verify(Path supplied,Runnable check,int depth)throws IOException {
        if(depth>64)throw new IOException("Replay lineage exceeds the explicit 64-generation verification capacity");
        Path root=supplied.toAbsolutePath().normalize();directory(root);if(Files.exists(root.resolve("FAILURE.json")))throw new IOException("Failed workflow/replay root cannot substitute an earlier nested success");
        if(Files.isDirectory(root.resolve("bundle"),LinkOption.NOFOLLOW_LINKS))root=root.resolve("bundle");Map<?,?> marker=object(json(root.resolve("COMPLETED.json"),65536));
        return "genuine-rerun".equals(marker.get("kind"))?SnakemakeOriginalReplay.verify(root,"COMPLETED.json",check,depth):verifyLocal(root,"COMPLETED.json",check);
    }
    static Verified verifyLocal(Path root,String markerName,Runnable check)throws IOException {
        directory(root);Map<?,?> raw=object(json(root.resolve("REQUEST.json"),4_194_304));var request=SnakemakeOriginalRecipe.recorded(raw);
        Map<?,?> complete=object(json(root.resolve(markerName),65536));keys(complete,"schema_version","tool","kind","status","manifest","biological_accuracy_certified","whole_paper_reproduced");integer(complete.get("schema_version"),1,1);
        String status=markerName.equals("CANDIDATE.json")?"candidate-declared-local-workflow":"completed-declared-local-workflow";
        if(!"Snakemake".equals(complete.get("tool"))||!"declared-local-workflow".equals(complete.get("kind"))||!status.equals(complete.get("status"))||!Boolean.FALSE.equals(complete.get("biological_accuracy_certified"))||!Boolean.FALSE.equals(complete.get("whole_paper_reproduced")))throw new IOException("Invalid workflow completion claim");
        Pin manifest=pin(root.resolve("manifest.json"),request.metadataBytes(),check);if(!manifest.equals(fingerprint(complete.get("manifest"),request.metadataBytes())))throw new IOException("Completion manifest binding differs");Map<?,?> declared=object(json(root.resolve("manifest.json"),request.metadataBytes()));keys(declared,"schema_version","tool","files","directories");integer(declared.get("schema_version"),1,1);if(!"Snakemake".equals(declared.get("tool")))throw new IOException("Wrong workflow manifest tool");
        Inventory actual=inventory(root,request,Set.of("manifest.json",markerName),check);var expectedFiles=new TreeMap<String,Pin>();for(var row:object(declared.get("files")).entrySet())expectedFiles.put(member(text(row.getKey())),fingerprint(row.getValue(),request.capture().fileBytes()));
        if(!actual.files().equals(expectedFiles)||!actual.directories().equals(new TreeSet<>(strings(declared.get("directories")))))throw new IOException("Complete standalone bundle file/directory census or bytes differ");
        for(String name:TOP_FILES)if(!actual.files().containsKey(name))throw new IOException("Missing required execution record: "+name);
        equal(SnakemakeOriginalWorkflow.code(),json(root.resolve("code.json"),request.metadataBytes()),"executing producer/reader code edition");
        Map<?,?> invocation=object(json(root.resolve("invocation.json"),request.metadataBytes()));keys(invocation,"command","environment","working_directory","bindings","producer_root","forced_complete_target_dag","selected_preinstalled_deployment");String producer=absolute(text(invocation.get("producer_root")));
        if(!request.execution().deployment().equals("preinstalled")||!Boolean.TRUE.equals(invocation.get("forced_complete_target_dag"))||!Boolean.TRUE.equals(invocation.get("selected_preinstalled_deployment")))throw new IOException("Unqualified or incomplete recorded execution mode");
        var inputs=SnakemakeOriginalCapture.decode(json(root.resolve("capture.json"),request.metadataBytes()),request.capture());var before=SnakemakeOriginalCapture.decode(json(root.resolve("working-before.json"),request.metadataBytes()),request.capture());var after=SnakemakeOriginalCapture.decode(json(root.resolve("working-after.json"),request.metadataBytes()),request.capture());
        for(var state:List.of(before,after))if(state.roots().size()!=1||!state.roots().get(0).source().equals(producer+"/workspace")||!state.roots().get(0).leaf().equals("workspace")||!state.aliases().equals(Map.of("working","r0/workspace")))throw new IOException("Captured working state is not the owned workspace");
        sources(request,inputs);initial(producer,inputs,before);var bindings=bindings(producer,inputs);equal(bindings,invocation.get("bindings"),"captured resource bindings");String working=bindings.get("workflow");if(!working.equals(invocation.get("working_directory")))throw new IOException("Original engine cwd differs");
        Map<?,?> source=object(json(root.resolve("request-source.json"),request.metadataBytes()));keys(source,"path","identity");absolute(text(source.get("path")));if(!pin(root.resolve("REQUEST.json"),4_194_304,check).equals(fingerprint(object(source.get("identity")).get("pin"),4_194_304)))throw new IOException("Original request and captured bytes differ");
        Map<?,?> context=SnakemakeOriginalEvidence.runtime(root,request,check);String python=text(context.get("python"));
        List<String> argv=SnakemakeOriginalEvidence.arguments(producer,working,request);Map<?,?> command=object(json(root.resolve("COMMAND.json"),request.metadataBytes()));keys(command,"schema_version","run_id","events_directory","receipt","argv");integer(command.get("schema_version"),1,1);equal(argv,command.get("argv"),"declared original-engine arguments");
        if(!(producer+"/events").equals(command.get("events_directory"))||!(producer+"/DRIVER.json").equals(command.get("receipt")))throw new IOException("Original event/receipt paths differ");
        List<String> executable=List.of(python,"-I","-B",producer+"/helpers/driver.py","--command",producer+"/COMMAND.json");equal(executable,invocation.get("command"),"original engine process");equal(SnakemakeOriginalEvidence.environment(producer,text(context.get("environment")),request),invocation.get("environment"),"declared clean environment");SnakemakeOriginalEvidence.process(root,producer,working,executable,request);
        String parameters=SnakemakeOriginalJson.parameters(SnakemakeOriginalParameters.bindRecorded(request.parameters(),bindings,producer+"/workspace/published"))+"\n";if(!parameters.equals(utf8(bytes(root.resolve("parameters.json"),request.metadataBytes()))))throw new IOException("Lossless bound parameter bytes or order differ");
        Map<?,?> driver=object(json(root.resolve("DRIVER.json"),request.metadataBytes()));if(!command.get("run_id").equals(driver.get("run_id")))throw new IOException("Original command/run identity differs");var driverArgv=new ArrayList<>(List.of("--logger","omicsmatic"));driverArgv.addAll(argv);equal(driverArgv,driver.get("argv"),"original parser invocation");SnakemakeOriginalEvidence.health(object(driver.get("health")),python);
        var events=SnakemakeOriginalEvents.audit(root.resolve("events"),driver,request.eventRows(),request.metadataBytes(),request.logBytes(),request.execution().requireJobs(),check);equal(SnakemakeOriginalWorkflow.eventsAsJson(events),json(root.resolve("completion-events.json"),request.metadataBytes()),"independent complete controller audit");equal(events.diagnostics(),json(root.resolve("DIAGNOSTICS.json"),request.metadataBytes()),"complete original warnings/errors");
        var ownedPids=new HashSet<Long>();for(Object row:list(object(json(root.resolve("process/process.json"),request.metadataBytes())).get("owned_processes")))ownedPids.add(integer(object(row).get("pid"),1,Long.MAX_VALUE));if(!ownedPids.contains(events.controllerPid()))throw new IOException("Original controller was not a registered owned process");
        var softwarePaths=new HashSet<String>();for(String row:utf8(bytes(root.resolve("runtime/runtime-files.tsv"),67_108_864)).split("\n")){String[] fields=row.split("\t",-1);if(fields.length==8&&!fields[0].equals("root")){softwarePaths.add(fields[6]);softwarePaths.add(fields[0].equals("env")?context.get("environment")+"/"+fields[1]:fields[1]);}}
        for(var job:events.jobs().values()) {for(String input:job.inputs()){String path=input.startsWith("/")?link("",input):link(working,input);if(!within(path,producer)&&!softwarePaths.contains(path))throw new IOException("Observed job input is outside captured roots and adopted software: "+input+". Declare the input resource and use its captured binding; no external input is silently admitted.");}for(String output:job.outputs()){String path=output.startsWith("/")?link("",output):link(working,output);if(!within(path,producer))throw new IOException("Observed job output escapes the recorded owned execution: "+output);}}
        Map<?,?> index=object(json(root.resolve("file-records.json"),request.metadataBytes()));keys(index,"schema_version","producer_root","nodes");integer(index.get("schema_version"),1,1);if(!producer.equals(index.get("producer_root")))throw new IOException("Owned-state producer identity differs");Map<String,Node> metadata=nodes(index.get("nodes"),request.capture().fileBytes());
        var owned=new TreeMap<String,Node>();for(var row:metadata.entrySet())owned.put(join(producer,row.getKey()),row.getValue());for(var row:snapshot(after).entrySet()){Node old=owned.putIfAbsent(row.getKey(),row.getValue());if(old!=null&&!old.equals(row.getValue()))throw new IOException("Owned metadata and working state overlap inconsistently");}
        for(var row:owned.entrySet())if(row.getValue().kind().equals("symlink")){Located target=resolve(owned,row.getKey(),true);if(!within(target.path(),producer)||row.getValue().resolved()!=null&&!target.path().equals(row.getValue().resolved()))throw new IOException("Recorded owned symlink escapes or disagrees with its target");}
        var outputs=outputs(root,request,producer,bindings,owned,check);var closure=new TreeMap<String,Pin>();capturePins(inputs,closure);capturePins(before,closure);capturePins(after,closure);for(var output:outputs)for(var value:output.members().values())if(value.pin()!=null)add(closure,value.pin());
        for(var row:metadata.entrySet())if(row.getValue().kind().equals("file")&&!row.getKey().startsWith("objects/"))add(closure,row.getValue().pin());
        var recordedObjects=new TreeMap<String,Pin>();for(var row:metadata.entrySet())if(row.getKey().startsWith("objects/")){String name=row.getKey().substring(8);if(!name.matches("[a-f0-9]{64}")||!row.getValue().kind().equals("file"))throw new IOException("Invalid recorded object-store member");recordedObjects.put(name,row.getValue().pin());}
        equal(closure,json(root.resolve("blob-index.json"),request.metadataBytes()),"complete blob closure");if(!recordedObjects.equals(closure))throw new IOException("Recorded owned objects differ from required full capture closure");
        var objectNames=new HashSet<String>();for(String name:actual.files().keySet())if(name.startsWith("objects/"))objectNames.add(name.substring(8));if(!objectNames.equals(closure.keySet()))throw new IOException("Missing or unreferenced standalone object");for(var row:closure.entrySet())if(!pin(root.resolve("objects").resolve(row.getKey()),request.capture().fileBytes(),check).equals(row.getValue()))throw new IOException("Captured blob bytes differ");
        for(String name:actual.files().keySet())if(!name.startsWith("objects/")&&!Set.of("file-records.json","blob-index.json").contains(name)){Node node=metadata.get(name);if(node==null||!node.kind().equals("file")||!node.pin().equals(actual.files().get(name)))throw new IOException("Standalone metadata differs from its recorded owned file");}
        tools(request,root,before,after);if(!SnakemakeOriginalWorkflow.warningText(events).equals(utf8(bytes(root.resolve("WARNINGS.md"),request.metadataBytes()))))throw new IOException("Required complete software/source warnings differ");
        return new Verified(root,manifest,root,producer,request,inputs,outputs,events);
    }
    private static void tools(SnakemakeOriginalRecipe.Request request,Path root,SnakemakeOriginalCapture.Snapshot before,SnakemakeOriginalCapture.Snapshot after)throws IOException {
        List<?> rows=list(json(root.resolve("tools.json"),request.metadataBytes()));if(rows.size()!=request.tools().size())throw new IOException("Declared tool family differs");var names=new HashSet<String>();
        for(int i=0;i<rows.size();i++) {var expected=request.tools().get(i);Map<?,?> row=object(rows.get(i));keys(row,"name","supplied","resolved","identity");if(!expected.name().equals(row.get("name"))||!expected.executable().equals(row.get("supplied")))throw new IOException("Declared executable binding differs");String real=absolute(text(row.get("resolved")));Map<?,?> identity=object(row.get("identity"));fingerprint(identity.get("pin"),1_073_741_824);if((integer(identity.get("mode"),0,0777)&0111)==0)throw new IOException("Declared executable was not executable");String wrapper="#!/usr/bin/bash\nexec '"+real.replace("'","'\\''")+"' \"$@\"\n";byte[] raw=wrapper.getBytes(StandardCharsets.UTF_8);Pin wanted=new Pin(raw.length,sha(raw));names.add(expected.name());
            for(var state:List.of(before,after)){var item=state.roots().get(0).items().get("bin/"+expected.name());if(item==null||!item.kind().equals("file")||!item.original().pin().equals(wanted)||(item.mode()&0111)==0)throw new IOException("Declared tool wrapper changed");}
        }
        for(var state:List.of(before,after)){var observed=new HashSet<String>();for(String name:state.roots().get(0).items().keySet())if(name.startsWith("bin/"))observed.add(name.substring(4));if(!observed.equals(names))throw new IOException("Complete executable wrapper census differs");}
    }
    private static List<SnakemakeOriginalWorkflow.Published> outputs(Path root,SnakemakeOriginalRecipe.Request request,String producer,Map<String,String> bindings,Map<String,Node> owned,Runnable check)throws IOException {
        List<?> saved=list(json(root.resolve("outputs.json"),request.metadataBytes()));if(saved.size()!=request.outputs().size())throw new IOException("Declared output family differs");var result=new ArrayList<SnakemakeOriginalWorkflow.Published>();long total=0;int count=0;
        for(int i=0;i<saved.size();i++) {
            var expected=request.outputs().get(i);Map<?,?> item=object(saved.get(i));keys(item,"root","path","kind","comparison","members","bytes","files");if(!expected.root().equals(item.get("root"))||!expected.path().equals(item.get("path"))||!expected.kind().equals(item.get("kind"))||!expected.comparison().equals(item.get("comparison")))throw new IOException("Recorded output contract differs");
            String base=expected.root().equals("published")?producer+"/workspace/published":bindings.get(expected.root()),path=join(base,expected.path());var actual=new TreeMap<String,SnakemakeOriginalOutputs.Value>();walkOutput(path,"",owned,actual,new HashSet<>(),request.capture().files());var recorded=new TreeMap<String,SnakemakeOriginalOutputs.Value>();long bytes=0;int files=0;
            for(var member:object(item.get("members")).entrySet()){String name=text(member.getKey());if(!name.isEmpty())member(name);Map<?,?> value=object(member.getValue());keys(value,"kind","pin","resolved","mode");String kind=text(value.get("kind"));Pin pin=kind.equals("file")?fingerprint(value.get("pin"),request.capture().fileBytes()):null;if(!kind.equals("file")&&(!kind.equals("directory")||value.get("pin")!=null))throw new IOException("Output is not a complete file/directory");recorded.put(name,new SnakemakeOriginalOutputs.Value(kind,pin,absolute(text(value.get("resolved"))),(int)integer(value.get("mode"),0,0777)));if(pin!=null){bytes=Math.addExact(bytes,pin.bytes());files++;}}
            if(!recorded.equals(actual)||!actual.containsKey("")||!actual.get("").kind().equals(expected.kind())||bytes!=integer(item.get("bytes"),0,request.capture().bytes())||files!=integer(item.get("files"),0,request.capture().files()))throw new IOException("Complete output tree differs from recorded terminal working state");
            if(!expected.allowEmpty()&&(expected.kind().equals("file")?bytes==0:files==0))throw new IOException("Declared nonempty output family is empty");
            if(expected.comparison().equals("benchmark-measurement"))org.jlinalg.workflow.SnakemakeBenchmarks.read(root.resolve("objects").resolve(actual.get("").pin().sha256()),new org.jlinalg.workflow.SnakemakeBenchmarks.Limits(request.capture().fileBytes(),request.eventRows(),request.metadataBytes()));
            total=Math.addExact(total,bytes);count=Math.addExact(count,recorded.size());if(total>request.capture().bytes()||count>request.capture().files())throw new IOException("Combined output family exceeds capacity");result.add(new SnakemakeOriginalWorkflow.Published(expected.root(),expected.path(),expected.kind(),expected.comparison(),recorded,bytes,files));check.run();
        }return List.copyOf(result);
    }
    private static void walkOutput(String path,String member,Map<String,Node> owned,Map<String,SnakemakeOriginalOutputs.Value> result,Set<String> ancestors,int maximum)throws IOException {
        if(result.size()>=maximum)throw new IOException("Complete output traversal capacity exceeded");Located found=resolve(owned,path,false);Node value=found.node();if(!Set.of("file","directory").contains(value.kind()))throw new IOException("Special output cannot be admitted as captured file bytes");result.put(member,new SnakemakeOriginalOutputs.Value(value.kind(),value.pin(),found.path(),value.mode()));
        if(value.kind().equals("directory")){if(!ancestors.add(found.path()))throw new IOException("Output directory alias cycle");String prefix=found.path()+'/';for(String name:new TreeSet<>(owned.keySet()))if(name.startsWith(prefix)&&name.indexOf('/',prefix.length())<0){String leaf=name.substring(prefix.length());walkOutput(path+'/'+leaf,member.isEmpty()?leaf:member+'/'+leaf,owned,result,ancestors,maximum);}ancestors.remove(found.path());}
    }
    static Inventory inventory(Path root,SnakemakeOriginalRecipe.Request request,Set<String> excluded,Runnable check)throws IOException {
        var files=new TreeMap<String,Pin>();var directories=new TreeSet<String>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            private String name(Path path){String name=root.relativize(path).toString();return path.getFileSystem().getSeparator().equals("\\")?name.replace('\\','/'):name;}
            private void charge()throws IOException{check.run();if(++count[0]>request.process().fileCount())throw new IOException("Standalone bundle member capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException {charge();if(!path.equals(root))directories.add(member(name(path)));return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException {charge();String name=member(name(path));if(!attrs.isRegularFile())throw new IOException("Standalone bundles contain regular files/directories only");if(excluded.contains(name))return FileVisitResult.CONTINUE;Pin pin=pin(path,request.capture().fileBytes(),check);bytes[0]=Math.addExact(bytes[0],pin.bytes());if(bytes[0]>request.process().workspaceBytes())throw new IOException("Standalone bundle byte capacity exceeded");files.put(name,pin);return FileVisitResult.CONTINUE;}
        });return new Inventory(Map.copyOf(files),Set.copyOf(directories));
    }
}
