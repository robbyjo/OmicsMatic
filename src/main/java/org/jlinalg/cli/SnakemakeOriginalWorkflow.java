/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Captured original-engine execution under qualification; no public CLI or final bundle admission yet. */
final class SnakemakeOriginalWorkflow {
    static final String ACTION="DEVELOPMENT EXECUTION ONLY: final portable bundle verification/replay and software deployment qualification remain unfinished. No user analysis, paper reproduction or catalog integration is admitted. Keep captured inputs, original events, partial outputs and every failed attempt. Missing/forbidden prerequisites skip dependent analysis; install only where permitted and retry NEW paths. This runs trusted upstream workflow code, not a security sandbox.";
    record Tool(String name,String supplied,String resolved,Identity identity) { }
    record Published(String root,String path,String kind,String comparison,Map<String,SnakemakeOriginalOutputs.Value> members,long bytes,int files) {
        Published { members=Map.copyOf(members); }
    }
    record Result(Path root,SnakemakeOriginalEvents.Result events,List<Published> outputs) {Result {outputs=List.copyOf(outputs);}}
    private SnakemakeOriginalWorkflow() { }
    static Result execute(Path suppliedRecipe,Path runtimeHome,Path suppliedOutput,Runnable check)throws IOException {
        Path root=suppliedOutput.toAbsolutePath().normalize(),recipe=suppliedRecipe.toAbsolutePath().normalize();
        if(root.getParent()==null)throw new IOException("Require a NEW named workflow output directory");directory(root.getParent());Files.createDirectory(root);
        Files.writeString(root.resolve("WARNINGS.md"),"# Unfinished original Snakemake adapter\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        SnakemakeOriginalRuntime.Context runtime=null;SnakemakeOriginalRecipe.Request request=null;Exception failed=null;Result result=null;
        try {
            Identity recipeBefore=identity(recipe,4_194_304,check);byte[] raw=bytes(recipe,4_194_304);Files.write(root.resolve("REQUEST.json"),raw,StandardOpenOption.CREATE_NEW);
            request=SnakemakeOriginalRecipe.read(root.resolve("REQUEST.json"));if(!recipeBefore.equals(identity(recipe,4_194_304,check)))throw new IOException("Original request changed during initial capture");
            writeJson(root.resolve("request-source.json"),Map.of("path",recipe.toString(),"identity",recipeBefore));
            if(!request.execution().deployment().equals("preinstalled"))throw new SnakemakeOriginalRuntime.Unavailable("Managed Conda/Apptainer execution is not yet qualified in this development stage. Preserve this skip; use a separately qualified permitted deployment or wait for its adapter qualification. There is no deployer/PATH fallback.");
            Files.writeString(root.resolve("WARNINGS.md"),"\nWARNING: The recipe explicitly selects preinstalled software. Under the original engine's selected mode, Conda/Apptainer rule directives remain inactive. The recorded runtime and declared executable paths do not seal arbitrary additional software or data loaded by trusted workflow code. No installation/network sandbox is claimed.\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
            runtime=SnakemakeOriginalRuntime.open(runtimeHome,check);writeJson(root.resolve("runtime-context.json"),runtime);
            Path runtimeRecords=Files.createDirectory(root.resolve("runtime"));for(var entry:runtime.metadata().entrySet()){Path target=runtimeRecords.resolve(entry.getKey());Files.copy(runtime.home().resolve(entry.getKey()),target);if(!pin(target,67_108_864,check).equals(entry.getValue()))throw new IOException("Runtime evidence changed during capture");}
            var tools=tools(request,check);writeJson(root.resolve("tools.json"),tools);
            var captured=SnakemakeOriginalCapture.create(root,request.sources(),request.capture(),check);writeJson(root.resolve("capture.json"),captured);
            Path workspace=Files.createDirectory(root.resolve("workspace")),published=Files.createDirectory(workspace.resolve("published"));Files.createDirectory(workspace.resolve("cache"));Path bin=Files.createDirectory(workspace.resolve("bin"));
            var bindings=SnakemakeOriginalCapture.materialize(root,captured,workspace.resolve("resources"),request.capture(),check);Path working=directory(bindings.get("workflow"));
            source(root,working.resolve(request.workflow().snakefile()));for(String name:request.workflow().configfiles())source(root,working.resolve(name));
            for(Tool tool:tools){Path path=bin.resolve(tool.name());Files.writeString(path,wrapper(tool),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);if(!path.toFile().setExecutable(true,false)||!Files.isExecutable(path))throw new SnakemakeOriginalRuntime.Unavailable("Declared tool wrapper execution forbidden: "+tool.name());}
            Object effective=SnakemakeOriginalParameters.bind(request.parameters(),bindings,published);Files.writeString(root.resolve("parameters.json"),SnakemakeOriginalJson.parameters(effective)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Path helpers=Files.createDirectory(root.resolve("helpers"));for(String name:SnakemakeOriginalRuntime.HELPERS){Path target=helpers.resolve(name);Files.createDirectories(target.getParent());Files.write(target,SnakemakeOriginalRuntime.resource(name),StandardOpenOption.CREATE_NEW);}
            Files.createDirectory(root.resolve("events"));var argv=arguments(root,working,request);String run=java.util.UUID.randomUUID().toString();
            writeJson(root.resolve("COMMAND.json"),Map.of("schema_version",1,"run_id",run,"events_directory",root.resolve("events").toString(),"receipt",root.resolve("DRIVER.json").toString(),"argv",argv));
            var command=List.of(runtime.python().toString(),"-I","-B",helpers.resolve("driver.py").toString(),"--command",root.resolve("COMMAND.json").toString());
            var environment=new TreeMap<>(request.environment());environment.putAll(Map.of("PATH",bin+":"+runtime.environment().resolve("bin")+":/usr/bin:/bin","LANG","C.UTF-8","LC_ALL","C.UTF-8","TZ","UTC","XDG_CACHE_HOME",workspace.resolve("cache").toString(),"PYTHONDONTWRITEBYTECODE","1","PYTHONNOUSERSITE","1"));
            writeJson(root.resolve("invocation.json"),Map.of("command",command,"environment",environment,"working_directory",working.toString(),"bindings",bindings,"forced_complete_target_dag",true,"selected_preinstalled_deployment",true));
            SnakemakeOriginalCapture.verifyOriginals(captured,request.capture(),check);writeJson(root.resolve("working-before.json"),SnakemakeOriginalCapture.working(root,workspace,request.capture(),check));writeJson(root.resolve("code.json"),code());
            var selected=request;
            SnakemakeOriginalProcess.executeSession(command,environment,root.resolve("process"),working,root,request.process(),()->{
                if(!tools(selected,check).equals(tools))throw new IOException("Declared workflow executable changed");
                for(Tool tool:tools)if(!utf8(bytes(bin.resolve(tool.name()),65536)).equals(wrapper(tool))||!Files.isExecutable(bin.resolve(tool.name())))throw new IOException("Tool wrapper changed");
                for(String name:SnakemakeOriginalRuntime.HELPERS)if(!pin(helpers.resolve(name),1_048_576,check).sha256().equals(SnakemakeOriginalRuntime.helperPins().get(name)))throw new IOException("Captured original-engine helper changed");
            },check);
            var events=SnakemakeOriginalEvents.audit(root.resolve("events"),object(json(root.resolve("DRIVER.json"),request.metadataBytes())),request.eventRows(),request.metadataBytes(),request.logBytes(),request.execution().requireJobs(),check);writeJson(root.resolve("completion-events.json"),eventsAsJson(events));
            if(events.errorDiagnostics()>0)Files.writeString(root.resolve("WARNINGS.md"),"\nWARNING: Original diagnostics contain "+events.errorDiagnostics()+" error records although all observed jobs eventually completed. Inspect the retained events and source stderr, including every failed retry, before interpreting outputs.\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
            List<Published> outputs=outputs(root,bindings,published,request,check);writeJson(root.resolve("outputs.json"),outputs);
            if(!recipeBefore.equals(identity(recipe,4_194_304,check)))throw new IOException("Original request changed during workflow execution");
            SnakemakeOriginalCapture.verifyOriginals(captured,request.capture(),check);result=new Result(root,events,outputs);
        }catch(Exception failure){failed=failure;}
        finally {
            if(request!=null&&Files.isDirectory(root.resolve("workspace"))&&Files.isDirectory(root.resolve("objects")))try{writeJson(root.resolve("working-after.json"),SnakemakeOriginalCapture.working(root,root.resolve("workspace"),request.capture(),check));}catch(Exception failure){if(failed==null)failed=failure;else failed.addSuppressed(failure);}
            if(runtime!=null)try{runtime.verify(check);}catch(Exception failure){if(failed==null)failed=failure;else failed.addSuppressed(failure);}
            if(failed!=null) {
                int exit=failed instanceof SnakemakeOriginalRuntime.Unavailable||failed instanceof SnakemakeOriginalProcess.Unavailable?3:2;
                try{writeJson(root.resolve("FAILURE.json"),Map.of("status",exit==3?"SKIP3-analysis-skipped":"FAIL2-no-result-admitted","exit_code",exit,"error",failed.toString(),"suppressed",java.util.Arrays.stream(failed.getSuppressed()).map(Throwable::toString).toList(),"required_action",ACTION));Files.writeString(root.resolve("FAILURE_WARNINGS.md"),"# Snakemake "+(exit==3?"SKIP3 analysis skipped":"FAIL2 no result admitted")+"\n\n"+failed+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
                catch(IOException diagnostic){failed.addSuppressed(diagnostic);System.err.println("SNAKEMAKE DIAGNOSTIC WRITE FAILED: "+diagnostic+". "+ACTION);}
            }
        }
        if(failed instanceof IOException io)throw io;if(failed!=null)throw new IOException("Original Snakemake development execution failed; retain diagnostics",failed);
        writeJson(root.resolve("EXECUTED.json"),Map.of("status","SOURCE_EXECUTED_WITH_CAPTURED_INPUTS_NOT_FINAL_WORKFLOW_ADMISSION","run_id",result.events().runId(),"logical_jobs",result.events().jobs().size(),"error_diagnostics",result.events().errorDiagnostics(),"outputs",result.outputs().size(),"user_analysis_admitted",false,"catalog_credit",0,"required_action",ACTION));return result;
    }
    static List<String> arguments(Path root,Path working,SnakemakeOriginalRecipe.Request request)throws IOException {
        var x=request.execution();var result=new ArrayList<>(List.of("--snakefile",working.resolve(request.workflow().snakefile()).toString(),"--directory",working.toString(),"--executor","local","--cores",Integer.toString(x.cores()),"--scheduler",x.scheduler(),"--retries",Integer.toString(x.retries()),"--latency-wait",Integer.toString(x.latencyWait()),"--forceall","--keep-incomplete"));
        if(x.notemp())result.add("--notemp");if(x.extended())result.add("--benchmark-extended");
        result.add("--configfile");for(String config:request.workflow().configfiles())result.add(working.resolve(config).toString());result.add(root.resolve("parameters.json").toString());result.add("--");result.addAll(request.workflow().targets());return List.copyOf(result);
    }
    static Map<String,Object> eventsAsJson(SnakemakeOriginalEvents.Result events) {
        return Map.of("controller_pid",events.controllerPid(),"run_id",events.runId(),"streams",events.streams(),"source_records",events.sourceRecords(),"started_batches",events.startedBatches(),"final_done",events.finalDone(),"final_total",events.finalTotal(),"error_diagnostics",events.errorDiagnostics(),"jobs",events.jobs().values().stream().sorted(java.util.Comparator.comparingLong(SnakemakeOriginalEvents.Job::id)).toList());
    }
    private static List<Published> outputs(Path root,Map<String,Path> bindings,Path published,SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        var result=new ArrayList<Published>();long bytes=0;int count=0;
        for(var output:request.outputs()) {
            Path base=output.root().equals("published")?published:bindings.get(output.root());Path path=base.resolve(output.path()).normalize();if(!path.startsWith(base)||!path.toRealPath().startsWith(root))throw new IOException("Declared output escapes captured resources");
            Map<String,SnakemakeOriginalOutputs.Value> members;long size;int files;
            if(output.kind().equals("directory")) {
                var bound=new SnakemakeOriginalRecipe.Output(output.root(),"","directory",output.allowEmpty(),output.comparison());var family=SnakemakeOriginalOutputs.capture(root,path,List.of(bound),request.capture(),check);members=family.members();size=family.bytes();files=family.files();
            }else {
                Path real=path.toRealPath();Identity before=identity(real,request.capture().fileBytes(),check);if(!output.allowEmpty()&&before.pin().bytes()==0)throw new IOException("Declared output file is empty");
                if(output.comparison().equals("benchmark-measurement"))org.jlinalg.workflow.SnakemakeBenchmarks.read(real,new org.jlinalg.workflow.SnakemakeBenchmarks.Limits(request.capture().fileBytes(),request.eventRows(),request.metadataBytes()));
                SnakemakeOriginalCapture.store(root.resolve("objects"),real,before.pin(),request.capture().fileBytes(),check);if(!before.equals(identity(real,request.capture().fileBytes(),check)))throw new IOException("Declared output changed during capture");
                members=Map.of("",new SnakemakeOriginalOutputs.Value("file",before.pin(),real.toString(),before.mode()));size=before.pin().bytes();files=1;
            }
            bytes=Math.addExact(bytes,size);count=Math.addExact(count,members.size());if(bytes>request.capture().bytes()||count>request.capture().files())throw new IOException("Complete combined declared output family exceeds capacities");
            result.add(new Published(output.root(),output.path(),output.kind(),output.comparison(),members,size,files));
        }
        return List.copyOf(result);
    }
    private static List<Tool> tools(SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        var result=new ArrayList<Tool>();for(var tool:request.tools()){if(!Files.isExecutable(tool.executable()))throw new SnakemakeOriginalRuntime.Unavailable("Required executable missing or forbidden: "+tool.name()+" at "+tool.executable()+". Install only where permitted or skip this dependent analysis.");Path real=tool.executable().toRealPath();result.add(new Tool(tool.name(),tool.executable().toString(),real.toString(),identity(real,1_073_741_824,check)));}return List.copyOf(result);
    }
    private static String wrapper(Tool tool){return "#!/usr/bin/bash\nexec '"+tool.resolved().replace("'","'\\''")+"' \"$@\"\n";}
    private static void source(Path root,Path path)throws IOException {if(!Files.isRegularFile(path)||!path.toRealPath().startsWith(root.resolve("workspace/resources")))throw new IOException("Snakefile/config must be a complete captured regular file: "+path);}
    private static Map<String,Pin> code()throws IOException {
        var result=new TreeMap<String,Pin>();
        for(Class<?> outer:List.of(SnakemakeOriginalWorkflow.class,SnakemakeOriginalCapture.class,SnakemakeOriginalEvents.class,SnakemakeOriginalFiles.class,SnakemakeOriginalJson.class,SnakemakeOriginalOutputs.class,SnakemakeOriginalParameters.class,SnakemakeOriginalProcess.class,SnakemakeOriginalRecipe.class,SnakemakeOriginalRuntime.class,org.jlinalg.workflow.SnakemakeBenchmarks.class))for(Class<?> type:outer.getNestMembers()){String name=type.getName().replace('.','/')+".class";try(var input=type.getResourceAsStream('/'+name)){if(input==null)throw new IOException("Executing adapter class unavailable: "+name);byte[] raw=input.readAllBytes();result.put(name,new Pin(raw.length,sha(raw)));}}
        for(String name:SnakemakeOriginalRuntime.HELPERS){byte[] raw=SnakemakeOriginalRuntime.resource(name);result.put("org/jlinalg/omics/snakemake-original-v1/"+name,new Pin(raw.length,sha(raw)));}return Map.copyOf(result);
    }
}
