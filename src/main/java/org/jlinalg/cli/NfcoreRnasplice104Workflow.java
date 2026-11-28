/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Full exact-source FASTQ workflow, separate preparation and immutable terminal attempts. */
final class NfcoreRnasplice104Workflow {
    private NfcoreRnasplice104Workflow() { }
    static Path run(Path root,Path supplied,Path home,Path setup,boolean resume)throws Exception {
        if(!resume){NfcoreValidationWorkflow.create(root);Files.createDirectory(root.resolve("attempts"));}
        directory(root);
        try(var channel=FileChannel.open(root.resolve(".adapter.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            FileLock lock;try{lock=channel.tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){throw new IOException("Typed RNA session already has an owner",busy);}if(lock==null)throw new IOException("Typed RNA session already has an owner");
            try(lock){return locked(root,supplied,home,setup,resume);}
        }
    }
    private static Path locked(Path root,Path supplied,Path home,Path setup,boolean resume)throws Exception{
        int number=1;
        if(resume){Map<?,?> previous=latest(root);if(!Set.of("COMPLETED","FAIL2","SKIP3").contains(previous.get("status")))throw new IOException("Resume requires a known terminal attempt; preserve a live or indeterminate run and inspect its owned-process evidence");if(!Files.exists(root.resolve("init.json")))throw new IOException("Preparation did not finish; preserve its partial materialization and use NEW paths");number=Math.addExact(Integer.parseInt(text(previous.get("attempt"))),1);}
        if(number>999999)throw new IOException("Typed RNA attempt capacity exceeded");String id=String.format(java.util.Locale.ROOT,"%06d",number);Path attempt=Files.createDirectory(root.resolve("attempts").resolve(id));latest(root,id,"RUNNING");
        String phase="platform and prerequisite admission";
        try{
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new NextflowRuntime.Unavailable("Original full RNA execution requires x86_64 Linux Java, including WSL. Use --verify for portable saved results; dependent analysis skipped");
            if(home==null||setup==null)throw new NextflowRuntime.Unavailable("Exact managed --home and completed --setup-report are required. Complete permitted separate Nextflow24.04.4 setup and skip dependent analysis until available");
            Path jar=executable();Pin implementation=pin(jar,1000000000L,()->{});
            phase="capture request and producing JAR";if(!resume){capture(supplied,root.resolve("request.json"),4194304);capture(jar,root.resolve("adapter.jar"),1000000000L);}
            phase="validate complete biological design";var request=NfcoreRnasplice104Request.read(root.resolve("request.json"));phase="verify exact managed Nextflow runtime";var managed=NextflowRuntime.open(home,NfcoreValidationPlugin.ENGINE,setup,()->{});
            for(Path external:List.of(managed.home(),managed.report(),Path.of(request.containerHome())))if(root.startsWith(external)||external.startsWith(root))throw new IOException("Typed RNA session and required runtimes/setup reports must be disjoint");
            NextflowRecipe.Request recipe;
            phase=resume?"verify unchanged owned materialization and runtime":"verify runtime, FIFO capability and capture complete source/images/inputs";
            if(!resume){Path admission=Files.createDirectory(attempt.resolve("runtime-admission"));recipe=NfcoreRnasplice104Prepare.prepare(root,request,admission);var init=new LinkedHashMap<String,Object>();init.put("schema_version",1);init.put("pipeline_profile",NfcoreRnasplice104Profile.ID);for(var row:contextFiles().entrySet())init.put(row.getKey(),pin(root.resolve(row.getValue()),row.getKey().equals("implementation")?1000000000L:67108864L,()->{}));writeJson(root.resolve("init.json"),init);}
            else{
                verifyContext(root,implementation);recipe=NextflowRecipe.read(root.resolve("engine-recipe.json"));Map<?,?> prepared=object(json(root.resolve("prepared.json"),67108864));if(!root.toString().equals(prepared.get("producer_root")))throw new IOException("Resume requires the exact original producer path; portable verification does not resume execution");
                if(!NextflowJson.json(NfcoreRnasplice104Prepare.materializedIndex(root.resolve("materialized"),recipe.capture().fileBytes())).equals(NextflowJson.json(json(root.resolve("materialized-index.json"),67108864))))throw new IOException("Owned RNA materialization changed; resume never recaptures changed external inputs");
                Path admission=Files.createDirectory(attempt.resolve("runtime-admission"));new NfcoreContainerSet(Path.of(request.containerHome()),root.resolve("image-set.json"),admission,recipe.process(),NfcoreRnasplice104Profile.RESOURCE+"IMAGES.json",NfcoreRnasplice104Profile.IMAGES).health();
            }
            verifyContext(root,implementation);
            NextflowProcess.Check integrity=()->{managed.verify(()->{});if(!implementation.equals(pin(jar,1000000000L,()->{})))throw new IOException("Running typed RNA implementation changed");verifyContext(root,implementation);};
            Path inner=root.resolve("engine");boolean engineComplete=false;
            if(resume&&Files.exists(inner.resolve("latest.json")))engineComplete="COMPLETED".equals(NfcoreValidationWorkflow.readLatest(inner).get("status"));
            phase="execute complete original FASTQ graph";if(!engineComplete){
                var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx"+recipe.heapMib()+"m","-XX:ActiveProcessorCount="+recipe.cpus(),"-cp",jar.toString(),"org.jlinalg.cli.NfcoreValidationToolsCli"));
                if(resume&&Files.exists(inner.resolve("session.json")))command.addAll(List.of("--resume",inner.toString()));else{if(Files.exists(inner))throw new IOException("Partial original-engine initialization has no resumable session; preserve it and use NEW paths");command.addAll(List.of("--recipe",root.resolve("engine-recipe.json").toString(),"--out",inner.toString()));}command.addAll(List.of("--home",home.toString(),"--setup-report",setup.toString()));
                Path launch=attempt.resolve("engine-process");try{NfcoreProcess.executeSession(command,Map.of("PATH","/usr/bin:/bin","LANG","C.UTF-8","LC_ALL","C.UTF-8","TZ","UTC"),launch,root,root,recipe.process(),integrity,()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Typed RNA interrupted");});}
                catch(IOException failure){if(Files.exists(launch.resolve("process.json"))){Map<?,?> ledger=object(json(launch.resolve("process.json"),67108864));if(ledger.containsKey("exit_code")&&integer(ledger.get("exit_code"),0,255)==3&&list(ledger.get("owned_live_after_cleanup")).isEmpty()&&(!ledger.containsKey("suppressed")||list(ledger.get("suppressed")).isEmpty()))throw new NextflowProcess.Unavailable("Original RNA engine reported a missing/forbidden prerequisite. Skip analysis; inspect engine-process/process.stderr and the engine failure report. "+failure);}throw failure;}
            }
            phase="verify complete original engine capture";var engine=NfcoreValidationBundle.verify(inner,()->{});var files=new NfcoreRnasplice104Files(engine);var audit=new LinkedHashMap<String,Object>();phase="verify source and materialization";audit.put("source_and_materialization",files.verifyMaterialization(object(json(root.resolve("materialized-index.json"),67108864)),request,root.toString()));phase="verify complete cohort and actual biological pairing";audit.put("cohort_and_pairing",files.cohortAndPairing(request));phase="check complete Salmon quantification";audit.put("quantification",NfcoreRnasplice104Postfit.quantification(files,request));phase="stream complete rMATS tables and selections";audit.put("rmats",NfcoreRnasplice104Postfit.rmats(files,request,attempt.resolve("rmats"),true,recipe.capture().fileBytes()));audit.put("scientific_identifiers_repaired",false);audit.put("independent_whole_model_reimplementation",false);writeJson(attempt.resolve("audit.json"),audit);integrity.verify();phase="complete semantic replay and atomic finalization";
            Files.writeString(attempt.resolve("WARNINGS.md"),"# Original rnasplice1.0.4: selected source-output contracts\n\n"+NfcoreRnasplice104ToolsCli.ACTION+"\n\n"+utf8(bytes(engine.attempt().resolve("WARNINGS.md"),1048576)),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Map<?,?> init=object(json(root.resolve("init.json"),1048576));var report=new LinkedHashMap<String,Object>();report.put("schema_version",1);report.put("pipeline_profile",NfcoreRnasplice104Profile.ID);report.put("engine_manifest",engine.manifest());report.put("engine_attempt",engine.attempt().getFileName().toString());report.put("prepared",init.get("prepared"));report.put("implementation",implementation);report.put("scope","original-default-FASTQ-graph-with-declared-model-settings-and-selected-source-output-contracts");report.put("biological_accuracy_certified",false);report.put("whole_paper_reproduced",false);NfcoreRnasplice104Bundle.finish(root,id,report);integrity.verify();latest(root,id,"COMPLETED");return attempt;
        }catch(Exception|LinkageError failure){
            int code=exit(failure);String status=code==3?"SKIP3":"FAIL2";try{if(Files.exists(attempt.resolve("COMPLETED.json")))Files.move(attempt.resolve("COMPLETED.json"),attempt.resolve("COMPLETED-PREFINAL.json"));var trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));Files.writeString(attempt.resolve("failure.stacktrace"),trace.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);writeJson(attempt.resolve("FAILURE.json"),Map.of("status",status,"exit_code",code,"phase",phase,"error",NfcoreValidationToolsCli.detail(failure instanceof Exception e?e:new IOException(failure)),"stacktrace","failure.stacktrace","required_action",NfcoreRnasplice104ToolsCli.ACTION));if(Files.exists(attempt.resolve("WARNINGS.md")))Files.move(attempt.resolve("WARNINGS.md"),attempt.resolve("WARNINGS-PREFINAL.md"));Files.writeString(attempt.resolve("WARNINGS.md"),"# "+status+" - NO RESULT\n\nPhase: "+phase+"\n\n"+failure+"\n\nRead failure.stacktrace for complete causes and suppressed diagnostics.\n\n"+NfcoreRnasplice104ToolsCli.ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);latest(root,id,status);}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}String message="During "+phase+": "+failure+". Inspect "+attempt.resolve("FAILURE.json")+" and failure.stacktrace; no result admitted.";if(code==3)throw new NextflowRuntime.Unavailable(message);throw new IOException(message,failure);
        }
    }
    static int exit(Throwable failure){return failure instanceof NextflowRuntime.Unavailable||failure instanceof NextflowProcess.Unavailable||failure instanceof java.nio.file.AccessDeniedException?3:2;}
    static Map<String,String> contextFiles(){return Map.of("request","request.json","prepared","prepared.json","recipe","engine-recipe.json","materialized_index","materialized-index.json","supplied_image_set","supplied-image-set.json","owned_image_set","image-set.json","implementation","adapter.jar");}
    private static void verifyContext(Path root,Pin implementation)throws IOException{Map<?,?> init=object(json(root.resolve("init.json"),1048576));keys(init,"schema_version","pipeline_profile","request","prepared","recipe","materialized_index","supplied_image_set","owned_image_set","implementation");integer(init.get("schema_version"),1,1);if(!NfcoreRnasplice104Profile.ID.equals(init.get("pipeline_profile"))||!implementation.equals(fingerprint(init.get("implementation"),1000000000L)))throw new IOException("Resume requires the exact producing adapter JAR/source profile");for(var row:contextFiles().entrySet())if(!fingerprint(init.get(row.getKey()),row.getKey().equals("implementation")?1000000000L:67108864L).equals(pin(root.resolve(row.getValue()),row.getKey().equals("implementation")?1000000000L:67108864L,()->{})))throw new IOException("Captured typed RNA context changed: "+row.getKey());}
    private static Path executable()throws Exception{Path jar=Path.of(NfcoreRnasplice104Workflow.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();if(!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))throw new NextflowRuntime.Unavailable("Use the packaged self-contained OmicsMatic JAR; analysis skipped for an unbound development classpath");return jar;}
    private static void capture(Path source,Path target,long maximum)throws IOException{if(source==null||!Files.exists(source))throw new NextflowRuntime.Unavailable("Required exact input is missing: "+source+"; skip analysis until the permitted prerequisite is available");Pin before=pin(source,maximum,()->{});Files.copy(source,target);if(!before.equals(pin(target,maximum,()->{}))||!before.equals(pin(source,maximum,()->{})))throw new IOException("Typed RNA input changed during exact capture: "+source);}
    static Map<?,?> latest(Path root)throws IOException{Map<?,?> latest=object(json(root.resolve("latest.json"),1048576));keys(latest,"attempt","status");String id=text(latest.get("attempt"));if(!id.matches("[0-9]{6}"))throw new IOException("Invalid typed RNA attempt ID");String newest="";try(var paths=Files.list(directory(root.resolve("attempts")))){for(Path path:paths.toList()){directory(path);String name=path.getFileName().toString();if(!name.matches("[0-9]{6}"))throw new IOException("Unexpected typed RNA attempt entry");if(name.compareTo(newest)>0)newest=name;}}if(!id.equals(newest))throw new IOException("Typed RNA latest pointer omits a newer retained attempt; no fallback to earlier success");return latest;}
    private static void latest(Path root,String id,String status)throws IOException{Path staged=root.resolve("latest.new.json");writeJson(staged,Map.of("attempt",id,"status",status));Files.move(staged,root.resolve("latest.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
}
