/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.workflow.NextflowCompletionAudit;
import org.jlinalg.workflow.NextflowTraceAudit;
import static org.jlinalg.cli.NextflowFiles.*;

/** Runs captured user workflows with the original engine and a stable owned resume cache. */
final class NextflowWorkflow {
    private NextflowWorkflow() { }
    static final String TRACE="task_id,hash,name,status,exit,attempt,process,tag,workdir";
    record Result(Path attempt,NextflowCompletionAudit.Result completion,NextflowOutputs.Family outputs) { }
    record Tool(String name,String path,String realPath,Identity identity) { }

    static void create(Path root)throws IOException {
        if(root.getParent()==null||!root.isAbsolute()||!root.normalize().equals(root)||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Require a new canonical Nextflow session directory");
        if(root.toString().chars().anyMatch(c->c>127||Character.isWhitespace(c)||c<32||c==127))throw new NextflowRuntime.Unavailable("Use a NEW ASCII session path without whitespace; original workflow/data names may be Unicode");
        Files.createDirectory(root);
    }

    static Result start(Path root,Path recipe,Path home,Path report,Runnable check)throws IOException {
        if(!System.getProperty("os.name").equals("Linux"))throw new NextflowRuntime.Unavailable("User workflow execution requires Linux Java, including WSL on Windows; portable verification remains available");
        directory(root);var original=identity(recipe,4194304,check);Files.copy(recipe,root.resolve("request.json"));
        if(!pin(root.resolve("request.json"),4194304,check).equals(original.pin()))throw new IOException("Recipe changed during complete capture");
        var request=NextflowRecipe.read(root.resolve("request.json"));var runtime=NextflowRuntime.open(home,request.profile(),report,check);
        for(Path external:List.of(runtime.home(),runtime.report()))if(root.startsWith(external)||external.startsWith(root))throw new IOException("Nextflow session and managed runtime/setup must be disjoint");
        var capture=NextflowCapture.create(root,request.sources(),request.capture(),check);writeJson(root.resolve("capture.json"),capture);
        Path workspace=Files.createDirectory(root.resolve("workspace"));
        NextflowCapture.materialize(root,capture,workspace.resolve("resources"),request.capture(),check);
        Files.createDirectory(workspace.resolve("execution"));Files.createDirectory(workspace.resolve("published"));Files.createDirectory(workspace.resolve("bin"));Files.createDirectory(root.resolve("attempts"));
        var tools=tools(request,check);writeJson(root.resolve("tools.json"),tools);installWrappers(root,tools);
        var session=new LinkedHashMap<String,Object>();session.put("schema_version",1);session.put("tool","Nextflow");session.put("producer_root",root.toString());session.put("profile",request.profile());session.put("recipe_source",recipe.toString());session.put("recipe_original",original);
        session.put("files",Map.of("request.json",pin(root.resolve("request.json"),4194304,check),"capture.json",pin(root.resolve("capture.json"),request.metadataBytes(),check),"tools.json",pin(root.resolve("tools.json"),request.metadataBytes(),check)));
        writeJson(root.resolve("session.json"),session);
        return attempt(root,home,report,false,check);
    }

    static Result attempt(Path root,Path home,Path report,boolean resume,Runnable check)throws IOException {
        if(!System.getProperty("os.name").equals("Linux"))throw new NextflowRuntime.Unavailable("Resume requires Linux Java and the original captured session path");
        directory(root);
        try(var channel=FileChannel.open(root.resolve(".session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            FileLock lock;
            try{lock=channel.tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("Nextflow session is already owned by a running attempt",busy);}
            if(lock==null)throw new IOException("Nextflow session is already owned by a running attempt");
            try(lock){return lockedAttempt(root,home,report,resume,check);}
        }
    }

    private static Result lockedAttempt(Path root,Path home,Path report,boolean resume,Runnable check)throws IOException {
        var request=NextflowRecipe.read(root.resolve("request.json"));Map<?,?> session=object(json(root.resolve("session.json"),request.metadataBytes()));
        keys(session,"schema_version","tool","producer_root","profile","recipe_source","recipe_original","files");integer(session.get("schema_version"),1,1);if(!"Nextflow".equals(session.get("tool"))||!object(session.get("files")).keySet().equals(Set.of("request.json","capture.json","tools.json")))throw new IOException("Incomplete session metadata/pin family");
        if(!root.toString().equals(session.get("producer_root"))||!request.profile().equals(session.get("profile")))throw new IOException("Resume requires the original canonical session path and captured recipe");
        for(var row:object(session.get("files")).entrySet())if(!pin(root.resolve(member(text(row.getKey()))),request.metadataBytes(),check).equals(fingerprint(row.getValue(),request.metadataBytes())))throw new IOException("Session request/capture/tool metadata changed");
        var capture=NextflowCapture.decode(json(root.resolve("capture.json"),request.metadataBytes()),request.capture());
        Map<?,?> previous=null;Path previousAttempt=null;int number=1;
        if(Files.exists(root.resolve("latest.json"))){previous=readLatest(root);if("RUNNING".equals(previous.get("status")))throw new IOException("Prior attempt has no terminal state; inspect its owned processes and retained evidence before starting a NEW session");String id=text(previous.get("attempt"));previousAttempt=root.resolve("attempts").resolve(id);number=Math.addExact(Integer.parseInt(id),1);if(!resume)throw new IOException("Session already has an attempt; use explicit --resume");}
        else if(resume)throw new IOException("No prior captured attempt exists to resume");
        if(number>999999)throw new IOException("Nextflow attempt capacity exceeded");
        String id=String.format(java.util.Locale.ROOT,"%06d",number);Path attempt=Files.createDirectory(root.resolve("attempts").resolve(id));latest(root,id,"RUNNING");
        Exception failed=null;Result result=null;NextflowCapture.Snapshot after=null;
        try{
            Files.copy(root.resolve("request.json"),attempt.resolve("request.json"));Files.copy(root.resolve("capture.json"),attempt.resolve("capture.json"));Files.copy(root.resolve("tools.json"),attempt.resolve("tools.json"));Files.copy(root.resolve("session.json"),attempt.resolve("session.json"));
            var runtime=NextflowRuntime.open(home,request.profile(),report,check);var boundTools=tools(request,check);
            if(!NextflowJson.json(boundTools).equals(NextflowJson.json(NextflowParameters.parse(utf8(bytes(root.resolve("tools.json"),request.metadataBytes())),request.metadataBytes(),1000000))))throw new IOException("Declared tool executable identity changed; do not silently substitute it on resume");
            verifyWrappers(root,boundTools);writeJson(attempt.resolve("runtime.json"),runtimeProof(root,runtime,check));
            var before=NextflowCapture.working(root,root.resolve("workspace"),request.capture(),check);writeJson(attempt.resolve("working-before.json"),before);
            if(previousAttempt!=null){
                Path baseline=resumeBaseline(root,previousAttempt,check);var prior=NextflowCapture.decode(json(baseline.resolve("working-after.json"),request.metadataBytes()),request.capture());if(!before.equals(prior))throw new IOException("Captured working resources changed since the prior known terminal state; resume cannot silently change workflow/input/cache/output bytes or file identities");
                String status=Files.exists(baseline.resolve("COMPLETED.json"))?"COMPLETED":"FAIL2";writeJson(attempt.resolve("resume-basis.json"),Map.of("attempt",baseline.getFileName().toString(),"working_after",pin(baseline.resolve("working-after.json"),request.metadataBytes(),check),"status",status));
            }
            var bindings=bindings(root.toString(),capture);String workflow=bindings.get("workflow");
            requireSourceFile(root,Path.of(workflow).resolve(request.workflow().entrypoint()));
            if(Files.exists(Path.of(workflow).resolve("nextflow.config"))&&!request.workflow().configs().contains("nextflow.config"))throw new IOException("Declare nextflow.config explicitly in workflow.configs; no author configuration is silently omitted");
            for(String config:request.workflow().configs())requireSourceFile(root,Path.of(workflow).resolve(config));
            String config=config(request.declared(),workflow);Files.writeString(attempt.resolve("adapter.config"),config,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Object effective=NextflowParameters.bindRecorded(request.parameters(),bindings,root+"/workspace/published");
            Files.writeString(attempt.resolve("params.json"),NextflowJson.parameters(effective)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var command=command(runtime.home().toString(),root.toString(),id,request.declared(),bindings,resume);
            var environment=new TreeMap<>(runtime.environment(root.resolve("workspace/execution/.nextflow"),request.heapMib(),request.cpus()));environment.put("PATH",root.resolve("workspace/bin")+":/usr/bin:/bin");
            writeJson(attempt.resolve("invocation.json"),Map.of("command",command,"environment",environment,"working_directory",root+"/workspace/execution","bindings",bindings,"resume",resume,"attempt",id,"producer_root",root.toString()));
            NextflowProcess.Result process;
            try{process=NextflowProcess.executeSession(command,environment,attempt.resolve("process"),root.resolve("workspace/execution"),root,request.process(),()->{runtime.verify(check);if(!tools(request,check).equals(boundTools))throw new IOException("Declared workflow executable changed during execution");verifyWrappers(root,boundTools);},check);}
            finally{after=NextflowCapture.working(root,root.resolve("workspace"),request.capture(),check);writeJson(attempt.resolve("working-after.json"),after);}
            var limits=new NextflowCompletionAudit.Limits(request.logBytes(),1048576,new NextflowTraceAudit.Limits(request.logBytes(),1048576,request.traceRows(),128));
            var completion=NextflowCompletionAudit.read(attempt.resolve("nextflow.log"),attempt.resolve("trace.tsv"),process.exitCode(),limits,check);
            if(!completion.executionSucceeded())throw new IOException("Nextflow completion has ignored failures, unfinished tasks or unreconciled retries; no output family is admitted");
            if(request.requireTasks()&&!completion.hasObservedTasks())throw new NextflowRuntime.Unavailable("The recipe requires task execution but the engine observed no tasks; skip dependent analysis and inspect workflow selection");
            var outputs=NextflowOutputs.capture(root,root.resolve("workspace/published"),request.outputs(),request.capture(),check);writeJson(attempt.resolve("completion.json"),completion);writeJson(attempt.resolve("outputs.json"),outputs);
            Files.writeString(attempt.resolve("WARNINGS.md"),warnings(runtime,completion),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(!resume){
                if(!NextflowJson.json(identity(Path.of(text(session.get("recipe_source"))),4194304,check)).equals(NextflowJson.json(session.get("recipe_original"))))throw new IOException("Original recipe changed during workflow execution");
                NextflowCapture.verifyOriginals(capture,request.capture(),check);
            }
            NextflowBundle.finish(root,attempt,capture,before,after,outputs,request,check);
            result=new Result(attempt,completion,outputs);latest(root,id,"COMPLETED");
        }catch(Exception failure){
            failed=failure;int code=failure instanceof NextflowRuntime.Unavailable||failure instanceof NextflowProcess.Unavailable?3:2;
            if(Files.exists(attempt.resolve("COMPLETED.json")))Files.delete(attempt.resolve("COMPLETED.json"));
            Pin afterPin=null;if(Files.exists(attempt.resolve("working-after.json")))try{afterPin=pin(attempt.resolve("working-after.json"),request.metadataBytes(),check);}catch(IOException|RuntimeException unobserved){failure.addSuppressed(unobserved);}
            var diagnostic=new LinkedHashMap<String,Object>(Map.of("status",code==3?"SKIP3-analysis-skipped":"FAIL2-no-result-admitted","exit_code",code,"error",NextflowToolsCli.detail(failure),"required_action",NextflowToolsCli.ACTION));diagnostic.put("working_after",afterPin);writeJson(attempt.resolve("FAILURE.json"),diagnostic);
            if(Files.exists(attempt.resolve("WARNINGS.md")))Files.move(attempt.resolve("WARNINGS.md"),attempt.resolve("WARNINGS-PREFINAL.md"));
            Files.writeString(attempt.resolve("WARNINGS.md"),"# Nextflow "+(code==3?"SKIP3 analysis skipped":"FAIL2 no result admitted")+"\n\n"+NextflowToolsCli.detail(failure)+"\n\n"+NextflowToolsCli.ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            latest(root,id,code==3?"SKIP3":"FAIL2");
        }
        if(failed instanceof IOException io)throw io;if(failed!=null)throw new IOException("Nextflow attempt failed; raw evidence retained",failed);return result;
    }
    static Path resumeBaseline(Path root,Path previous)throws IOException {
        return resumeBaseline(root,previous,()->{});
    }
    private static Path resumeBaseline(Path root,Path previous,Runnable check)throws IOException {
        directory(root);directory(previous);Path attempts=directory(root.resolve("attempts"));if(!previous.getParent().equals(attempts))throw new IOException("Resume baseline must be an owned attempt");
        var candidates=new ArrayList<Path>();try(var paths=Files.list(attempts)){for(Path path:paths.toList()){if(!path.getFileName().toString().matches("[0-9]{6}"))throw new IOException("Unexpected attempt name in resume history");if(path.getFileName().toString().compareTo(previous.getFileName().toString())<=0)candidates.add(directory(path));}}candidates.sort(java.util.Comparator.comparing((Path p)->p.getFileName().toString()).reversed());
        for(Path candidate:candidates){
            check.run();
            boolean completed=Files.exists(candidate.resolve("COMPLETED.json")),failed=Files.exists(candidate.resolve("FAILURE.json"));if(completed==failed)throw new IOException("Cannot resume across an attempt without one unambiguous terminal record");
            if(Files.exists(candidate.resolve("working-after.json"))){
                if(completed)NextflowBundle.verify(candidate,check);
                else{Map<?,?> failure=object(json(candidate.resolve("FAILURE.json"),2097152));if(!pin(candidate.resolve("working-after.json"),67108864,check).equals(fingerprint(failure.get("working_after"),67108864)))throw new IOException("Failed attempt's observed workspace hash is missing or changed; use a NEW session");}
                if(Files.exists(candidate.resolve("process"))){Map<?,?> ledger=object(json(candidate.resolve("process/process.json"),2097152));if(!list(ledger.get("owned_live_after_cleanup")).isEmpty()||!Set.of("completed-process-only","FAIL2-retained-no-result").contains(text(ledger.get("status"))))throw new IOException("Previous local process cleanup is not proven complete");}
                return candidate;
            }
            if(completed||Files.exists(candidate.resolve("process")))throw new IOException("An executed attempt lacks a complete after-state; preserve it and use a NEW session");
            // A terminal preflight failure launched no process and changed no
            // workspace. The next attempt still compares every working byte and
            // identity against the earlier observed state; tampering is not adopted.
        }
        throw new IOException("No complete observed workspace state is available for resume");
    }
    static Map<?,?> readLatest(Path root)throws IOException {
        directory(root);Map<?,?> latest=object(json(root.resolve("latest.json"),65536));keys(latest,"attempt","status");String id=text(latest.get("attempt"));if(!id.matches("[0-9]{6}")||!Set.of("RUNNING","COMPLETED","SKIP3","FAIL2").contains(text(latest.get("status"))))throw new IOException("Invalid latest attempt pointer");
        Path attempts=directory(root.resolve("attempts"));String newest="";try(var paths=Files.list(attempts)){var iterator=paths.iterator();int count=0;while(iterator.hasNext()){Path path=iterator.next();if(++count>999999||!path.getFileName().toString().matches("[0-9]{6}"))throw new IOException("Unexpected attempt directory family");directory(path);if(path.getFileName().toString().compareTo(newest)>0)newest=path.getFileName().toString();}}
        if(!newest.equals(id))throw new IOException("Latest pointer does not name the newest retained attempt; never substitute an older success");return latest;
    }

    static Map<String,String> bindings(String producer,NextflowCapture.Snapshot capture){var result=new TreeMap<String,String>();for(var row:capture.aliases().entrySet())result.put(row.getKey(),producer+"/workspace/resources/"+row.getValue());return Map.copyOf(result);}
    static List<String> command(String home,String root,String id,Map<?,?> recipe,Map<String,String> bindings,boolean resume)throws IOException {
        var workflow=object(recipe.get("workflow"));String entry=member(text(workflow.get("entrypoint"))),attempt=root+"/attempts/"+id;
        var result=new ArrayList<>(List.of("/usr/bin/bash",home+"/nextflow","-C",attempt+"/adapter.config","-log",attempt+"/nextflow.log","run",bindings.get("workflow")+"/"+entry,"-params-file",attempt+"/params.json","-with-trace",attempt+"/trace.tsv","-work-dir",root+"/workspace/execution/work","-ansi-log","false"));
        List<String> profiles=strings(workflow.get("profiles"));if(!profiles.isEmpty()){for(String value:profiles)if(!value.matches("[A-Za-z0-9_.-]+"))throw new IOException("Invalid declared profile");result.add("-profile");result.add(String.join(",",profiles));}if(resume)result.add("-resume");return List.copyOf(result);
    }
    static String config(Map<?,?> recipe,String workflow)throws IOException {
        var value=new StringBuilder();for(String name:strings(object(recipe.get("workflow")).get("configs"))){member(name);value.append("includeConfig '").append((workflow+"/"+name).replace("\\","\\\\").replace("'","\\'")).append("'\n");}
        return value.append("trace.fields='").append(TRACE).append("'\n").toString();
    }
    private static List<Tool> tools(NextflowRecipe.Request request,Runnable check)throws IOException {
        var result=new ArrayList<Tool>();for(var tool:request.tools()){
            if(!Files.exists(tool.executable())||!Files.isExecutable(tool.executable()))throw new NextflowRuntime.Unavailable("Declared required workflow executable is absent/forbidden: "+tool.name()+" at "+tool.executable()+"; install it only where permitted and skip dependent analysis until available");
            Path real=tool.executable().toRealPath();result.add(new Tool(tool.name(),tool.executable().toString(),real.toString(),identity(real,1073741824,check)));
        }return List.copyOf(result);
    }
    static String wrapper(Tool tool){return "#!/usr/bin/bash\nexec '"+tool.realPath().replace("'","'\\''")+"' \"$@\"\n";}
    private static void installWrappers(Path root,List<Tool> tools)throws IOException {for(Tool tool:tools){Path path=root.resolve("workspace/bin").resolve(tool.name());Files.writeString(path,wrapper(tool),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);if(!path.toFile().setExecutable(true,false)||!Files.isExecutable(path))throw new NextflowRuntime.Unavailable("Workflow helper wrapper execution is forbidden: "+tool.name());}}
    private static void verifyWrappers(Path root,List<Tool> tools)throws IOException {var names=new java.util.HashSet<String>();for(Tool tool:tools){names.add(tool.name());Path path=root.resolve("workspace/bin").resolve(tool.name());if(!utf8(bytes(path,65536)).equals(wrapper(tool))||!Files.isExecutable(path))throw new IOException("Changed workflow executable wrapper: "+tool.name());}try(var list=Files.list(root.resolve("workspace/bin"))){if(!list.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(names))throw new IOException("Unexpected helper wrapper family");}}
    private static Map<String,Object> runtimeProof(Path root,NextflowRuntime.Context runtime,Runnable check)throws IOException {var files=new TreeMap<String,Pin>();for(var row:runtime.setup().entrySet())if(row.getValue().kind().equals("file")){NextflowCapture.store(root.resolve("objects"),runtime.report().resolve(row.getKey()),row.getValue().pin(),NextflowRuntime.FILE_BYTES,check);files.put(row.getKey(),row.getValue().pin());}Pin marker=runtime.payload().get(NextflowRuntime.MARKER).pin();NextflowCapture.store(root.resolve("objects"),runtime.home().resolve(NextflowRuntime.MARKER),marker,NextflowRuntime.FILE_BYTES,check);return Map.of("context",runtime,"setup_files",files,"lock",NextflowRuntime.LOCK,"marker",marker);}
    private static void requireSourceFile(Path root,Path source)throws IOException {Path real=source.toRealPath();if(!real.startsWith(root.resolve("workspace/resources"))||!Files.isRegularFile(real))throw new IOException("Workflow entrypoint/config must be a captured regular file: "+source);}
    private static String warnings(NextflowRuntime.Context runtime,NextflowCompletionAudit.Result completion){return "# Nextflow declared workflow result\n\n"+NextflowToolsCli.ACTION+"\n\n"+(runtime.permissionDepartures()>0?"WARNING: Effective filesystem permission modes differ from requested archive modes for "+runtime.permissionDepartures()+" runtime members. Bytes and link targets remain exact; required executable access was checked. Use a permitted filesystem and fresh setup paths if exact POSIX modes are needed.\n\n":"")+(completion.outcome()==NextflowCompletionAudit.Outcome.COMPLETE_AFTER_RETRIES?"WARNING: Original tasks failed before successful retries. All failed attempts remain in trace.tsv; inspect them before downstream interpretation.\n\n":"")+(!completion.hasObservedTasks()?"WARNING: No engine tasks were observed. The recipe permits this; only declared outputs and execution metadata were admitted.\n\n":"");}
    private static void latest(Path root,String id,String status)throws IOException {Path temporary=root.resolve("latest.new.json");writeJson(temporary,Map.of("attempt",id,"status",status));try{Files.move(temporary,root.resolve("latest.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(java.nio.file.AtomicMoveNotSupportedException unsupported){throw new IOException("Nextflow session pointer requires an atomic move on this filesystem",unsupported);}}
}
