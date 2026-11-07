/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlinalg.workflow.NextflowCompletionAudit;
import org.jlinalg.workflow.NextflowTraceAudit;
import static org.jlinalg.cli.NextflowFiles.*;

/** Standalone immutable attempt evidence, verified without producer files or installed engines. */
final class NextflowBundle {
    private NextflowBundle() { }
    record Verified(Path attempt,Pin manifest,NextflowCompletionAudit.Result completion,int outputFiles,long outputBytes) { }
    private record Contract(NextflowCapture.Limits capture,NextflowProcess.Limits process,List<NextflowRecipe.Output> outputs,
                            int metadata,long logBytes,int traceRows,int heap,int cpus,boolean requireTasks) { }

    static void finish(Path session,Path attempt,NextflowCapture.Snapshot inputs,NextflowCapture.Snapshot before,
            NextflowCapture.Snapshot after,NextflowOutputs.Family outputs,NextflowRecipe.Request request,Runnable check)throws IOException {
        var closure=new TreeMap<String,Pin>();capturePins(inputs,closure);capturePins(before,closure);capturePins(after,closure);
        for(var value:outputs.members().values())if(value.pin()!=null)add(closure,value.pin());
        Map<?,?> runtime=object(json(attempt.resolve("runtime.json"),request.metadataBytes()));for(Object value:object(runtime.get("setup_files")).values())add(closure,fingerprint(value,NextflowRuntime.FILE_BYTES));add(closure,fingerprint(runtime.get("marker"),NextflowRuntime.META_BYTES));
        Files.createDirectory(attempt.resolve("objects"));
        for(Pin pin:closure.values()){check.run();NextflowCapture.store(attempt.resolve("objects"),session.resolve("objects").resolve(pin.sha256()),pin,request.capture().fileBytes(),check);}
        writeJson(attempt.resolve("blob-index.json"),closure);
        var files=new TreeMap<String,Pin>();var directories=new TreeSet<String>();inventory(attempt,request,check,files,directories,Set.of());
        writeJson(attempt.resolve("manifest.json"),Map.of("schema_version",1,"tool","Nextflow","files",files,"directories",directories));
        Pin manifest=pin(attempt.resolve("manifest.json"),request.metadataBytes(),check);
        writeJson(attempt.resolve("CANDIDATE.json"),Map.of("schema_version",1,"tool","Nextflow","status","candidate-declared-workflow-result","manifest",manifest,"biological_accuracy_certified",false,"whole_paper_reproduced",false));
        verifyAttempt(attempt,"CANDIDATE.json",check);
        byte[] complete=(NextflowJson.json(Map.of("schema_version",1,"tool","Nextflow","status","completed-declared-workflow-result","manifest",manifest,"biological_accuracy_certified",false,"whole_paper_reproduced",false))+"\n").getBytes(StandardCharsets.UTF_8);
        NextflowProcess.checkCapacity(session,request.process(),complete.length,1);
        Files.write(attempt.resolve("COMPLETED.new.json"),complete,StandardOpenOption.CREATE_NEW);
        Files.move(attempt.resolve("COMPLETED.new.json"),attempt.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
        Files.delete(attempt.resolve("CANDIDATE.json"));
    }

    static Verified verify(Path supplied,Runnable check)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);
        if(Files.exists(root.resolve("latest.json"))){Map<?,?> latest=NextflowWorkflow.readLatest(root);if(!"COMPLETED".equals(latest.get("status")))throw new IOException("Latest Nextflow attempt is not complete; inspect its failure and do not substitute an earlier success");root=root.resolve("attempts").resolve(text(latest.get("attempt")));}
        return verifyAttempt(root,"COMPLETED.json",check);
    }

    private static Verified verifyAttempt(Path root,String markerName,Runnable check)throws IOException {
        directory(root);if(Files.exists(root.resolve("FAILURE.json")))throw new IOException("A failed Nextflow attempt cannot admit results");
        Map<?,?> recipe=object(parse(root.resolve("request.json"),4194304));Contract contract=contract(recipe);
        Map<?,?> marker=object(json(root.resolve(markerName),65536));keys(marker,"schema_version","tool","status","manifest","biological_accuracy_certified","whole_paper_reproduced");integer(marker.get("schema_version"),1,1);
        if(!"Nextflow".equals(marker.get("tool"))||!Boolean.FALSE.equals(marker.get("biological_accuracy_certified"))||!Boolean.FALSE.equals(marker.get("whole_paper_reproduced"))||!(markerName.equals("CANDIDATE.json")?"candidate-declared-workflow-result":"completed-declared-workflow-result").equals(marker.get("status")))throw new IOException("Invalid Nextflow completion claim");
        Pin manifestPin=pin(root.resolve("manifest.json"),contract.metadata,check);if(!manifestPin.equals(fingerprint(marker.get("manifest"),contract.metadata)))throw new IOException("Nextflow completion/manifest binding differs");
        Map<?,?> manifest=object(json(root.resolve("manifest.json"),contract.metadata));keys(manifest,"schema_version","tool","files","directories");integer(manifest.get("schema_version"),1,1);if(!"Nextflow".equals(manifest.get("tool")))throw new IOException("Wrong workflow bundle tool");
        var expected=new TreeMap<String,Pin>();for(var row:object(manifest.get("files")).entrySet())expected.put(member(text(row.getKey())),fingerprint(row.getValue(),contract.capture.fileBytes()));
        var actual=new TreeMap<String,Pin>();var directories=new TreeSet<String>();inventory(root,contract,check,actual,directories,Set.of("manifest.json",markerName));
        if(!actual.equals(expected)||!directories.equals(new TreeSet<>(strings(manifest.get("directories")))))throw new IOException("Complete Nextflow bundle file/directory census or bytes differ");
        var capture=NextflowCapture.decode(json(root.resolve("capture.json"),contract.metadata),contract.capture);var before=NextflowCapture.decode(json(root.resolve("working-before.json"),contract.metadata),contract.capture);var after=NextflowCapture.decode(json(root.resolve("working-after.json"),contract.metadata),contract.capture);
        Map<?,?> invocation=object(json(root.resolve("invocation.json"),contract.metadata));keys(invocation,"command","environment","working_directory","bindings","resume","attempt","producer_root");String producer=absolute(text(invocation.get("producer_root"))),id=text(invocation.get("attempt"));if(!id.matches("[0-9]{6}"))throw new IOException("Invalid recorded attempt identifier");boolean resume=bool(invocation.get("resume"));
        Map<?,?> session=object(json(root.resolve("session.json"),contract.metadata));keys(session,"schema_version","tool","producer_root","profile","recipe_source","recipe_original","files");integer(session.get("schema_version"),1,1);
        if(!"Nextflow".equals(session.get("tool"))||!producer.equals(session.get("producer_root"))||!recipe.get("engine_profile").equals(session.get("profile")))throw new IOException("Captured original session identity differs");absolute(text(session.get("recipe_source")));
        Map<?,?> sessionFiles=object(session.get("files"));if(!sessionFiles.keySet().equals(Set.of("request.json","capture.json","tools.json")))throw new IOException("Incomplete original session pin family");
        for(var row:sessionFiles.entrySet())if(!pin(root.resolve(text(row.getKey())),contract.metadata,check).equals(fingerprint(row.getValue(),contract.metadata)))throw new IOException("Captured request/input/tool bytes differ from the original session pin: "+row.getKey());
        Map<?,?> original=object(session.get("recipe_original"));keys(original,"pin","fileKey","modified","mode","links");if(!pin(root.resolve("request.json"),4194304,check).equals(fingerprint(original.get("pin"),4194304)))throw new IOException("Original supplied recipe and captured request hash differ");text(original.get("fileKey"));text(original.get("modified"));integer(original.get("mode"),0,0777);integer(original.get("links"),1,Long.MAX_VALUE);
        var bindings=NextflowWorkflow.bindings(producer,capture);equal(bindings,invocation.get("bindings"),"captured parameter aliases");bindSources(recipe,capture);
        if(!before.roots().get(0).source().equals(producer+"/workspace")||!after.roots().get(0).source().equals(producer+"/workspace")||before.roots().size()!=1||after.roots().size()!=1)throw new IOException("Recorded working copies are not the stable owned workspace");
        tools(recipe,root,before,after,contract);
        Map<?,?> runtime=object(json(root.resolve("runtime.json"),contract.metadata));keys(runtime,"context","setup_files","lock","marker");Map<?,?> context=runtime(runtime,root,contract,check);if(!recipe.get("engine_profile").equals(context.get("profile")))throw new IOException("Recipe and recorded runtime profiles differ");
        equal(NextflowWorkflow.command(text(context.get("home")),producer,id,recipe,bindings,resume),invocation.get("command"),"original command");
        if(!text(invocation.get("working_directory")).equals(producer+"/workspace/execution"))throw new IOException("Recorded engine cwd differs");
        equal(environment(text(context.get("home")),producer,contract),invocation.get("environment"),"clean engine environment");
        String config=NextflowWorkflow.config(recipe,bindings.get("workflow"));if(!config.equals(utf8(bytes(root.resolve("adapter.config"),contract.metadata))))throw new IOException("Declared configuration chain differs");
        String parameters=NextflowJson.parameters(NextflowParameters.bindRecorded(recipe.get("parameters"),bindings,producer+"/workspace/published"))+"\n";
        if(!parameters.equals(utf8(bytes(root.resolve("params.json"),contract.metadata))))throw new IOException("Lossless effective parameters differ from the declared captured bindings");
        Map<?,?> ledger=object(json(root.resolve("process/process.json"),contract.metadata));if(!"completed-process-only".equals(ledger.get("status"))||integer(ledger.get("exit_code"),0,255)!=0||!list(ledger.get("owned_live_after_cleanup")).isEmpty())throw new IOException("Original process did not complete with zero live owned local processes");
        var guarded=new ArrayList<String>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+contract.process.addressSpaceBytes(),"--cpu="+contract.process.cpuSeconds(),"--fsize="+contract.process.fileBytes(),"--nofile="+contract.process.openFiles(),"--"));guarded.addAll(strings(invocation.get("command")));equal(guarded,ledger.get("command"),"guarded original invocation");equal(contract.process,ledger.get("limits"),"process capacities");
        if(!text(ledger.get("working_directory")).equals(producer+"/workspace/execution")||!text(ledger.get("workspace_root")).equals(producer))throw new IOException("Guarded working directory/capacity scope differs");
        Map<?,?> launch=object(json(root.resolve("process/launch.json"),contract.metadata));equal(guarded,launch.get("command"),"original launch receipt");equal(contract.process,launch.get("limits"),"launch capacities");if(integer(launch.get("owned_launcher_pid"),1,Long.MAX_VALUE)!=integer(ledger.get("owned_launcher_pid"),1,Long.MAX_VALUE))throw new IOException("Owned launch/process identities differ");
        var completion=NextflowCompletionAudit.read(root.resolve("nextflow.log"),root.resolve("trace.tsv"),0,new NextflowCompletionAudit.Limits(contract.logBytes,1048576,new NextflowTraceAudit.Limits(contract.logBytes,1048576,contract.traceRows,128)),check);
        if(!completion.executionSucceeded()||contract.requireTasks&&!completion.hasObservedTasks())throw new IOException("Incomplete/ignored/empty-required task family is not admitted");equal(completion,json(root.resolve("completion.json"),contract.metadata),"complete task audit");
        var outputs=NextflowOutputs.decode(json(root.resolve("outputs.json"),contract.metadata),contract.outputs,contract.capture);for(var value:outputs.members().values())if(!absolute(value.resolved()).startsWith(producer+"/"))throw new IOException("Recorded output resolves outside the owned session");
        var closure=new TreeMap<String,Pin>();capturePins(capture,closure);capturePins(before,closure);capturePins(after,closure);for(var value:outputs.members().values())if(value.pin()!=null)add(closure,value.pin());for(Object value:object(runtime.get("setup_files")).values())add(closure,fingerprint(value,NextflowRuntime.FILE_BYTES));add(closure,fingerprint(runtime.get("marker"),NextflowRuntime.META_BYTES));
        var stored=new TreeMap<String,Pin>();for(var row:object(json(root.resolve("blob-index.json"),contract.metadata)).entrySet()){Pin pin=fingerprint(row.getValue(),contract.capture.fileBytes());if(!pin.sha256().equals(row.getKey()))throw new IOException("Blob key differs from its complete content pin");stored.put(pin.sha256(),pin);}
        if(!closure.equals(stored))throw new IOException("Incomplete/extra Nextflow input/workspace/output/runtime blob closure");
        Set<String> physical=new HashSet<>();for(var row:actual.entrySet())if(row.getKey().startsWith("objects/")){String hash=row.getKey().substring(8);if(!row.getValue().equals(stored.get(hash)))throw new IOException("Captured blob bytes differ");physical.add(hash);}if(!physical.equals(stored.keySet()))throw new IOException("Complete physical blob family differs");
        if(resume){Map<?,?> basis=object(json(root.resolve("resume-basis.json"),65536));keys(basis,"attempt","working_after","status");if(!pin(root.resolve("working-before.json"),contract.metadata,check).equals(fingerprint(basis.get("working_after"),contract.metadata)))throw new IOException("Resume baseline differs from the prior captured workspace");}
        else initial(capture,before);
        if(!utf8(bytes(root.resolve("WARNINGS.md"),contract.metadata)).contains("Hashes establish integrity, not authenticity"))throw new IOException("Missing required result limitations/recovery instructions");
        return new Verified(root,manifestPin,completion,outputs.files(),outputs.bytes());
    }
    private static void tools(Map<?,?> recipe,Path root,NextflowCapture.Snapshot before,NextflowCapture.Snapshot after,Contract contract)throws IOException {
        List<?> declared=list(recipe.get("required_tools")),recorded=list(json(root.resolve("tools.json"),contract.metadata));if(declared.size()!=recorded.size())throw new IOException("Recorded required executable family differs");var names=new HashSet<String>();
        for(int i=0;i<declared.size();i++){
            Map<?,?> wanted=object(declared.get(i)),got=object(recorded.get(i));keys(wanted,"name","path");keys(got,"name","path","realPath","identity");String name=text(wanted.get("name"));if(!name.matches("[A-Za-z0-9_.+-]+")||name.equals(".")||name.equals("..")||!names.add(name)||!name.equals(got.get("name"))||!absolute(text(wanted.get("path"))).equals(got.get("path")))throw new IOException("Declared/recorded executable binding differs");
            String real=absolute(text(got.get("realPath")));Map<?,?> identity=object(got.get("identity"));keys(identity,"pin","fileKey","modified","mode","links");fingerprint(identity.get("pin"),1073741824);text(identity.get("fileKey"));text(identity.get("modified"));if((integer(identity.get("mode"),0,0777)&0111)==0)throw new IOException("Recorded required tool was not executable");integer(identity.get("links"),1,Long.MAX_VALUE);
            byte[] wrapper=("#!/usr/bin/bash\nexec '"+real.replace("'","'\\''")+"' \"$@\"\n").getBytes(StandardCharsets.UTF_8);Pin expected=new Pin(wrapper.length,sha(wrapper));
            for(var snapshot:List.of(before,after)){var member=snapshot.roots().get(0).items().get("bin/"+name);if(member==null||!member.kind().equals("file")||!expected.equals(member.original().pin())||(member.mode()&0111)==0)throw new IOException("Captured executable wrapper differs from its declared exact binding");}
        }
        for(var snapshot:List.of(before,after)){Set<String> observed=new HashSet<>();for(String path:snapshot.roots().get(0).items().keySet())if(path.startsWith("bin/"))observed.add(path.substring(4));if(!names.equals(observed))throw new IOException("Captured helper-wrapper census differs");}
    }

    private static Contract contract(Map<?,?> recipe)throws IOException {
        keys(recipe,"schema_version","engine_profile","workflow","resources","parameters","required_tools","outputs","require_tasks","limits");integer(recipe.get("schema_version"),1,1);if(!NextflowRuntime.profiles().contains(text(recipe.get("engine_profile"))))throw new IOException("Unsupported recorded exact engine");object(recipe.get("parameters"));
        Map<?,?> limits=object(recipe.get("limits"));if(!Set.of("capture_files","capture_bytes","file_bytes","workspace_bytes","wall_seconds","address_space_bytes","cpu_seconds","output_file_bytes","open_files","processes","threads","sampled_rss_bytes","poll_millis","trace_rows","metadata_bytes","log_bytes","java_heap_mib","java_cpus").containsAll(limits.keySet()))throw new IOException("Unknown recorded capacity field");long file=value(limits,"file_bytes",17179869184L,1,1000000000000L),total=value(limits,"capture_bytes",34359738368L,file,1000000000000L);int count=(int)value(limits,"capture_files",100000,4,1000000);
        var capture=new NextflowCapture.Limits(count,total,file);var process=new NextflowProcess.Limits(value(limits,"wall_seconds",3600,1,604800)*1000,value(limits,"address_space_bytes",17179869184L,16777216,1099511627776L),value(limits,"cpu_seconds",3600,1,604800),value(limits,"output_file_bytes",17179869184L,1,1000000000000L),(int)value(limits,"open_files",4096,8,1048576),value(limits,"workspace_bytes",274877906944L,1,1000000000000L),count,(int)value(limits,"processes",128,1,4096),(int)value(limits,"threads",4096,1,1048576),value(limits,"sampled_rss_bytes",17179869184L,1,1099511627776L),(int)value(limits,"poll_millis",100,1,1000));
        var outputs=new ArrayList<NextflowRecipe.Output>();var names=new HashSet<String>();for(Object row:list(recipe.get("outputs"))){Map<?,?> output=object(row);keys(output,"path","kind","allow_empty");String name=member(text(output.get("path"))),kind=text(output.get("kind"));if(!Set.of("file","directory").contains(kind)||!names.add(name))throw new IOException("Invalid recorded output declaration");outputs.add(new NextflowRecipe.Output(name,kind,bool(output.get("allow_empty"))));}if(outputs.isEmpty())throw new IOException("No declared output family");
        return new Contract(capture,process,List.copyOf(outputs),(int)value(limits,"metadata_bytes",8388608,1024,67108864),value(limits,"log_bytes",67108864,1024,1073741824),(int)value(limits,"trace_rows",1000000,1,10000000),(int)value(limits,"java_heap_mib",512,32,1048576),(int)value(limits,"java_cpus",2,1,1024),bool(recipe.get("require_tasks")));
    }
    private static Map<String,String> environment(String home,String producer,Contract contract){
        var env=new TreeMap<String,String>();env.put("PATH",producer+"/workspace/bin:/usr/bin:/bin");env.put("LANG","C.UTF-8");env.put("LC_ALL","C.UTF-8");env.put("TZ","UTC");env.put("NXF_HOME",producer+"/workspace/execution/.nextflow");env.put("CAPSULE_CACHE_DIR",home+"/capsule");env.put("NXF_JAVA_HOME",home+"/java");env.put("NXF_OFFLINE","true");env.put("NXF_DISABLE_CHECK_LATEST","true");env.put("NXF_ANSI_LOG","false");env.put("NXF_PLUGINS_DEFAULT","false");env.put("NXF_ENABLE_SECRETS","false");env.put("NXF_OPTS","-Xms32m -Xmx"+contract.heap+"m -XX:ReservedCodeCacheSize=128m -XX:ActiveProcessorCount="+contract.cpus);return Map.copyOf(env);
    }
    private static void bindSources(Map<?,?> recipe,NextflowCapture.Snapshot capture)throws IOException {
        for(var root:capture.roots())for(var item:root.items().values())if(!Set.of("file","directory","symlink").contains(item.kind())||item.kind().equals("symlink")&&item.resolved()==null)throw new IOException("Declared original inputs cannot contain special objects or unresolved links");
        var originals=new TreeMap<String,String>();for(var alias:capture.aliases().entrySet())for(var root:capture.roots()){String prefix=root.key()+"/"+root.leaf();if(alias.getValue().equals(prefix)||alias.getValue().startsWith(prefix+"/")){originals.put(alias.getKey(),absolute(root.source())+alias.getValue().substring(prefix.length()));break;}}
        var declared=new TreeMap<String,String>();Map<?,?> workflow=object(recipe.get("workflow"));keys(workflow,"path","entrypoint","configs","profiles");declared.put("workflow",absolute(text(workflow.get("path"))));member(text(workflow.get("entrypoint")));
        for(var row:object(recipe.get("resources")).entrySet()){Map<?,?> resource=object(row.getValue());keys(resource,"path");String id=text(row.getKey());if(id.equals("workflow"))throw new IOException("Reserved captured resource ID");declared.put(id,absolute(text(resource.get("path"))));}if(!declared.equals(originals))throw new IOException("Original declared source roots/aliases differ from the complete captured family");
    }
    private static void initial(NextflowCapture.Snapshot inputs,NextflowCapture.Snapshot before)throws IOException {
        Map<String,NextflowCapture.Item> actual=before.roots().get(0).items();var expected=new HashSet<String>();expected.add("resources");
        for(var root:inputs.roots()){String prefix="resources/"+root.key();expected.add(prefix);prefix+="/"+root.leaf();for(var row:root.items().entrySet()){String name=prefix+(row.getKey().isEmpty()?"":"/"+row.getKey());expected.add(name);var value=actual.get(name);if(value==null||!value.kind().equals(row.getValue().kind())||value.original()!=null&&!value.original().pin().equals(row.getValue().original().pin()))throw new IOException("Initial working bytes/types differ from the captured original family");}}
        Set<String> observed=new HashSet<>();for(String name:actual.keySet())if(name.equals("resources")||name.startsWith("resources/"))observed.add(name);if(!expected.equals(observed))throw new IOException("Initial resource file/directory census differs");
    }
    private static Map<?,?> runtime(Map<?,?> proof,Path root,Contract contract,Runnable check)throws IOException {
        if(!NextflowRuntime.LOCK.equals(fingerprint(proof.get("lock"),65536)))throw new IOException("Recorded runtime lock differs");Map<?,?> context=object(proof.get("context"));keys(context,"home","report","profile","version","kernel","payload","setup","helpers","permissionDepartures");absolute(text(context.get("home")));absolute(text(context.get("report")));
        Map<?,?> locked=NextflowRuntime.lock();Map<?,?> profile=object(object(locked.get("profiles")).get(text(context.get("profile"))));if(!text(profile.get("version")).equals(context.get("version")))throw new IOException("Recorded exact runtime edition differs");
        Map<?,?> payload=object(context.get("payload"));Map<?,?> engine=object(payload.get("nextflow"));if(!fingerprint(profile.get("engine"),NextflowRuntime.FILE_BYTES).equals(fingerprint(engine.get("pin"),NextflowRuntime.FILE_BYTES)))throw new IOException("Recorded original engine pin differs from the complete locked asset");
        var javaNames=new HashSet<String>();for(var row:object(object(locked.get("java")).get("members")).entrySet()){
            String name=text(row.getKey());javaNames.add("java/"+name);Map<?,?> wanted=object(row.getValue()),got=object(payload.get("java/"+name));if(!wanted.get("kind").equals(got.get("kind")))throw new IOException("Recorded Java member kind differs");
            if("file".equals(wanted.get("kind"))&&!fingerprint(wanted,NextflowRuntime.FILE_BYTES).equals(fingerprint(got.get("pin"),NextflowRuntime.FILE_BYTES))||"symlink".equals(wanted.get("kind"))&&!wanted.get("target").equals(got.get("target")))throw new IOException("Recorded Java bytes/link target differs");
        }
        var actualJava=new HashSet<String>();for(Object name:payload.keySet())if(text(name).startsWith("java/"))actualJava.add(text(name));if(!actualJava.equals(javaNames))throw new IOException("Recorded complete Java runtime family differs");
        Map<?,?> setup=object(context.get("setup")),files=object(proof.get("setup_files"));var required=new HashSet<String>();for(var row:setup.entrySet()){Map<?,?> item=object(row.getValue());if("file".equals(item.get("kind"))){String name=member(text(row.getKey()));required.add(name);if(!fingerprint(item.get("pin"),NextflowRuntime.FILE_BYTES).equals(fingerprint(files.get(name),NextflowRuntime.FILE_BYTES)))throw new IOException("Recorded setup source/file pin differs");}}
        if(!required.equals(files.keySet()))throw new IOException("Recorded setup file family differs");Pin marker=fingerprint(proof.get("marker"),NextflowRuntime.META_BYTES);Map<?,?> runtimeMarker=object(json(root.resolve("objects").resolve(marker.sha256()),contract.metadata));if(!NextflowRuntime.LOCK.sha256().equals(runtimeMarker.get("lock_sha256"))||!context.get("profile").equals(runtimeMarker.get("profile"))||!context.get("version").equals(runtimeMarker.get("version")))throw new IOException("Runtime marker identity differs");
        keys(runtimeMarker,"schema_version","tool","profile","version","lock_sha256","payload","permission_mode_departures","user_analysis_admitted");integer(runtimeMarker.get("schema_version"),1,1);if(!"Nextflow".equals(runtimeMarker.get("tool"))||!Boolean.FALSE.equals(runtimeMarker.get("user_analysis_admitted")))throw new IOException("Runtime marker incorrectly claims user analysis admission");
        var payloadEntries=recordEntries(payload);var markerEntries=new TreeMap<>(NextflowFiles.entries(runtimeMarker.get("payload"),NextflowRuntime.FILES,NextflowRuntime.FILE_BYTES));markerEntries.put(NextflowRuntime.MARKER,payloadEntries.get(NextflowRuntime.MARKER));if(!markerEntries.equals(payloadEntries)||!marker.equals(payloadEntries.get(NextflowRuntime.MARKER).pin()))throw new IOException("Runtime marker and complete recorded payload disagree");
        var departures=new TreeMap<String,Object>();var lockedJava=NextflowFiles.entries(object(locked.get("java")).get("members"),NextflowRuntime.FILES,NextflowRuntime.FILE_BYTES);
        for(var row:lockedJava.entrySet()){Entry got=payloadEntries.get("java/"+row.getKey()),want=row.getValue();if(want.kind().equals("file")&&want.mode()!=got.mode())departures.put("java/"+row.getKey(),Map.of("archive_mode",want.mode(),"effective_mode",got.mode()));}
        if(payloadEntries.get("nextflow").mode()!=0755)departures.put("nextflow",Map.of("requested_mode",0755,"effective_mode",payloadEntries.get("nextflow").mode()));equal(departures,runtimeMarker.get("permission_mode_departures"),"effective runtime permission audit");if(integer(context.get("permissionDepartures"),0,NextflowRuntime.FILES)!=departures.size())throw new IOException("Runtime permission departure count differs");
        NextflowRuntime.RecordedFiles records=name->{Pin pin=fingerprint(files.get(name),NextflowRuntime.FILE_BYTES);Path file=root.resolve("objects").resolve(pin.sha256());if(!pin(file,NextflowRuntime.FILE_BYTES,check).equals(pin))throw new IOException("Recorded setup blob changed: "+name);return file;};
        Map<?,?> setupComplete=object(json(records.get("COMPLETED.json"),65536));keys(setupComplete,"status","manifest","user_analysis_admitted");if(!"COMPLETE_SETUP_ONLY".equals(setupComplete.get("status"))||!Boolean.FALSE.equals(setupComplete.get("user_analysis_admitted"))||!fingerprint(files.get("manifest.json"),NextflowRuntime.META_BYTES).equals(fingerprint(setupComplete.get("manifest"),NextflowRuntime.META_BYTES)))throw new IOException("Recorded setup completion/manifest differs");
        Map<?,?> setupManifest=object(json(records.get("manifest.json"),NextflowRuntime.META_BYTES));keys(setupManifest,"schema_version","status","profile","version","home","host","lock_sha256","marker","helpers","files","user_analysis_admitted");integer(setupManifest.get("schema_version"),1,1);
        if(!"COMPLETE_SETUP_ONLY".equals(setupManifest.get("status"))||!Boolean.FALSE.equals(setupManifest.get("user_analysis_admitted"))||!NextflowRuntime.LOCK.sha256().equals(setupManifest.get("lock_sha256"))||!context.get("profile").equals(setupManifest.get("profile"))||!context.get("version").equals(setupManifest.get("version"))||!context.get("home").equals(setupManifest.get("home"))||!marker.equals(fingerprint(setupManifest.get("marker"),NextflowRuntime.META_BYTES)))throw new IOException("Recorded setup identity differs");
        Map<?,?> host=object(setupManifest.get("host"));keys(host,"os","architecture","kernel");if(!"Linux".equals(host.get("os"))||!Set.of("amd64","x86_64").contains(text(host.get("architecture")))||!context.get("kernel").equals(host.get("kernel")))throw new IOException("Recorded setup host/kernel differs");
        var setupEntries=recordEntries(setup);var expectedSetup=new TreeMap<>(NextflowFiles.entries(setupManifest.get("files"),NextflowRuntime.FILES,NextflowRuntime.FILE_BYTES));for(String name:List.of("manifest.json","COMPLETED.json"))expectedSetup.put(name,setupEntries.get(name));if(!expectedSetup.equals(setupEntries))throw new IOException("Recorded setup member census differs");
        Map<?,?> helpers=object(context.get("helpers")),setupHelpers=object(setupManifest.get("helpers"));if(!helpers.keySet().equals(Set.copyOf(strings(locked.get("required_helpers"))))||!helpers.keySet().equals(setupHelpers.keySet()))throw new IOException("Recorded setup helper family differs");
        for(var row:helpers.entrySet()){Map<?,?> got=object(row.getValue()),want=object(setupHelpers.get(row.getKey()));keys(got,"realPath","pin","mode");keys(want,"real_path","bytes","sha256","mode");if(!got.get("realPath").equals(want.get("real_path"))||!fingerprint(got.get("pin"),NextflowRuntime.FILE_BYTES).equals(fingerprint(want,NextflowRuntime.FILE_BYTES))||integer(got.get("mode"),0,0777)!=integer(want.get("mode"),0,0777))throw new IOException("Recorded required helper identity differs: "+row.getKey());}
        Map<?,?> setupRequest=object(json(records.get("request.json"),65536));if(!NextflowRuntime.INSTALLER.equals(fingerprint(setupRequest.get("installer"),65536))||!context.get("profile").equals(setupRequest.get("profile"))||!context.get("home").equals(setupRequest.get("home"))||!NextflowRuntime.LOCK.sha256().equals(setupRequest.get("lock_sha256"))||!Boolean.FALSE.equals(setupRequest.get("user_analysis_admitted")))throw new IOException("Recorded setup request/installer binding differs");
        NextflowRuntime.verifyRecordedHealth(text(context.get("home")),text(context.get("report")),records,text(context.get("version")),check);
        if(!departures.isEmpty()&&!utf8(bytes(records.get("WARNINGS.md"),65536)).contains("Effective filesystem modes differ"))throw new IOException("Recorded setup omits the effective permission warning");
        check.run();return context;
    }
    private static Map<String,Entry> recordEntries(Map<?,?> rows)throws IOException {
        if(rows.size()>NextflowRuntime.FILES)throw new IOException("Recorded runtime file capacity exceeded");var result=new TreeMap<String,Entry>();
        for(var row:rows.entrySet()){String name=member(text(row.getKey()));Map<?,?> value=object(row.getValue());keys(value,"kind","pin","mode","target");String kind=text(value.get("kind"));
            if(kind.equals("file")){if(value.get("target")!=null)throw new IOException("Unexpected regular runtime file target");result.put(name,new Entry(kind,fingerprint(value.get("pin"),NextflowRuntime.FILE_BYTES),(int)integer(value.get("mode"),0,0777),null));}
            else if(kind.equals("symlink")){if(value.get("pin")!=null||integer(value.get("mode"),-1,-1)!=-1)throw new IOException("Unexpected symlink file pin/mode");result.put(name,new Entry(kind,null,-1,text(value.get("target"))));}
            else throw new IOException("Unknown recorded runtime member kind");
        }return Map.copyOf(result);
    }
    private static void inventory(Path root,NextflowRecipe.Request request,Runnable check,Map<String,Pin> files,Set<String> directories,Set<String> excluded)throws IOException {inventory(root,new Contract(request.capture(),request.process(),request.outputs(),request.metadataBytes(),request.logBytes(),request.traceRows(),request.heapMib(),request.cpus(),request.requireTasks()),check,files,directories,excluded);}
    private static void inventory(Path root,Contract contract,Runnable check,Map<String,Pin> files,Set<String> directories,Set<String> excluded)throws IOException {
        long[] bytes={0};try(var paths=Files.walk(root)){var iterator=paths.iterator();int count=0;while(iterator.hasNext()){check.run();Path path=iterator.next();String name=root.relativize(path).toString().replace(path.getFileSystem().getSeparator(),"/");if(++count>contract.process.fileCount())throw new IOException("Complete bundle file capacity exceeded");if(Files.isSymbolicLink(path))throw new IOException("Portable Nextflow bundle must contain immutable ordinary files/directories only");if(Files.isDirectory(path)){directories.add(name);continue;}member(name);if(excluded.contains(name))continue;Pin pin=pin(path,contract.capture.fileBytes(),check);bytes[0]=Math.addExact(bytes[0],pin.bytes());if(bytes[0]>contract.process.workspaceBytes())throw new IOException("Complete bundle byte capacity exceeded");files.put(name,pin);}}
    }
    private static void capturePins(NextflowCapture.Snapshot snapshot,Map<String,Pin> pins)throws IOException {for(var root:snapshot.roots())for(var item:root.items().values())if(item.kind().equals("file"))add(pins,item.original().pin());}
    private static void add(Map<String,Pin> values,Pin pin)throws IOException {Pin old=values.putIfAbsent(pin.sha256(),pin);if(old!=null&&!old.equals(pin))throw new IOException("Inconsistent complete blob size/hash");}
    private static Object parse(Path path,int maximum)throws IOException{return NextflowParameters.parse(utf8(bytes(path,maximum)),maximum,2000000);}
    private static long value(Map<?,?> raw,String key,long fallback,long minimum,long maximum)throws IOException{return integer(raw.containsKey(key)?raw.get(key):fallback,minimum,maximum);}
    private static boolean bool(Object value)throws IOException{if(!(value instanceof Boolean flag))throw new IOException("Missing explicit boolean");return flag;}
    private static String absolute(String value)throws IOException {if(!value.startsWith("/")||value.length()<2)throw new IOException("Recorded producer path must be absolute Linux text");member(value.substring(1));return value;}
    private static void equal(Object expected,Object actual,String label)throws IOException {if(!NextflowJson.json(expected).equals(NextflowJson.json(actual)))throw new IOException("Nextflow "+label+" differs");}
}
