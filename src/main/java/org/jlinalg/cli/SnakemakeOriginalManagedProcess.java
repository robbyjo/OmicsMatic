/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.equal;

/** Original managed-process/policy execution bound to the actual Java supervisor; not workflow admission. */
final class SnakemakeOriginalManagedProcess {
    static final String RESOURCE="/org/jlinalg/omics/snakemake-managed-v1/";
    static final List<String> HELPERS=List.of("controller.py","deployment_policy.py","omicsmatic_policy_runtime.py","omicsmatic_logger_base.py","snakemake_logger_plugin_omicsmanaged/__init__.py","original_driver.py","managed_parser_policy.py");
    static final String ACTION="This verifies a supervised original process and its installation policy only. Complete workflow/software/input/output capture, replay, public CLI and paper-role admission remain unfinished. No user analysis or catalog credit is admitted. Missing/forbidden prerequisites require skipping the dependent analysis and restoring the named permitted prerequisite in NEW paths. Retain all original diagnostics, failed attempts and software evidence; never refresh historical hashes or silently substitute deployment software.";
    record Invocation(Path root,Path working,Path runtime,Path providers,Path controllerRequest,Map<String,String> environment,SnakemakeOriginalProcess.Limits limits) {
        Invocation {environment=Map.copyOf(environment);}
    }
    record Bound(int exitCode,long controllerPid,SnakemakeOriginalInstallationPolicy.Audit policy,Map<String,SnakemakeOriginalProcess.Identity> policyProcesses,Pin processLedger,Pin controllerReceipt,Map<String,Pin> helpers,List<Path> softwareData,SnakemakeOriginalManagedPolicy.Audit parserPolicy) {
        Bound {policyProcesses=Map.copyOf(policyProcesses);helpers=Map.copyOf(helpers);softwareData=List.copyOf(softwareData);}
    }
    private SnakemakeOriginalManagedProcess() { }
    static byte[] resource(String name)throws IOException {
        if(name.equals("omicsmatic_logger_base.py"))return SnakemakeOriginalRuntime.resource("snakemake_logger_plugin_omicsmatic/__init__.py");
        if(name.equals("original_driver.py"))return SnakemakeOriginalRuntime.resource("driver.py");
        if(!HELPERS.contains(name)&&!name.equals("PROVENANCE.json"))throw new IOException("Unknown managed-process helper");
        try(var stream=SnakemakeOriginalManagedProcess.class.getResourceAsStream(RESOURCE+name)){if(stream==null)throw new IOException("Bundled managed-process helper unavailable: "+name);byte[] bytes=stream.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Managed-process helper exceeds complete metadata capacity");return bytes;}
    }
    static Map<String,Pin> helperPins()throws IOException {var result=new TreeMap<String,Pin>();for(String name:HELPERS){byte[] raw=resource(name);result.put(name,new Pin(raw.length,sha(raw)));}return Map.copyOf(result);}
    private static void materialize(Path directory,Runnable check)throws IOException {
        if(!Files.exists(directory))Files.createDirectory(directory);directory(directory);
        for(String name:HELPERS){Path target=directory.resolve(name);Files.createDirectories(target.getParent());byte[] raw=resource(name);if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS))Files.write(target,raw,StandardOpenOption.CREATE_NEW);if(!pin(target,1_048_576,check).equals(new Pin(raw.length,sha(raw))))throw new IOException("Existing managed helper differs; preserve it and retry NEW paths: "+name);}
        verifyHelpers(directory,check);
    }
    private static void verifyHelpers(Path directory,Runnable check)throws IOException {
        var files=new HashSet<String>();try(var stream=Files.walk(directory)){for(Path path:stream.toList()){if(path.equals(directory))continue;if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))files.add(directory.relativize(path).toString().replace('\\','/'));else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unexpected managed helper link/special member");}}
        if(!files.equals(new HashSet<>(HELPERS)))throw new IOException("Complete managed helper census differs");
        for(var row:helperPins().entrySet())if(!pin(directory.resolve(row.getKey()),1_048_576,check).equals(row.getValue()))throw new IOException("Managed helper bytes changed: "+row.getKey());
    }
    static Bound execute(Invocation invocation,Runnable check)throws IOException {
        Path root=directory(invocation.root().toAbsolutePath().normalize()),working=directory(invocation.working().toAbsolutePath().normalize()),request=invocation.controllerRequest().toAbsolutePath().normalize();
        if(!working.startsWith(root)||!request.startsWith(root)||request.getParent()==null)throw new IOException("Managed process cwd/request must remain inside its explicit owned root");
        Path records=request.getParent();if(Files.exists(records.resolve("SUPERVISED-POLICY.json"),LinkOption.NOFOLLOW_LINKS)||Files.exists(records.resolve("SUPERVISION_FAILURE.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Retain previous managed-process records and retry NEW paths");
        Files.writeString(records.resolve("SUPERVISION_WARNINGS.md"),"# WARNING: supervised process and policy only\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        SnakemakeOriginalRuntime.Context runtime=null;SnakemakeOriginalDeployment.Context providers=null;Exception failed=null;Bound result=null;
        try {
            Pin requestPin=pin(request,4_194_304,check);Map<?,?> raw=object(json(request,4_194_304));long schema=integer(raw.get("schema_version"),1,2);if(schema==1)keys(raw,"schema_version","helpers","argv","installation_policy","receipt");else keys(raw,"schema_version","helpers","argv","installation_policy","receipt","execution_policy");
            Path helpers=inside(root,text(raw.get("helpers"))),configuration=inside(root,text(raw.get("installation_policy"))),receipt=inside(root,text(raw.get("receipt")));if(Files.exists(receipt,LinkOption.NOFOLLOW_LINKS))throw new IOException("Source controller receipt must be NEW");
            materialize(helpers,check);Files.write(records.resolve("MANAGED-HELPER-PROVENANCE.json"),resource("PROVENANCE.json"),StandardOpenOption.CREATE_NEW);
            Pin configPin=pin(configuration,1_048_576,check);Map<?,?> config=object(json(configuration,1_048_576));keys(config,"schema_version","run_id","allow_install","records_directory","logger");integer(config.get("schema_version"),1,1);
            if(!(config.get("allow_install") instanceof Boolean allow)||!"omicsmanaged".equals(config.get("logger")))throw new IOException("Require the qualified explicit managed-process permission/logger profile");
            String run=text(config.get("run_id"));if(!run.equals(invocation.environment().get("OMICS_SNAKEMAKE_RUN_ID")))throw new IOException("Caller run and propagated policy differ");Path policyRecords=directory(inside(root,text(config.get("records_directory")))),events=directory(inside(root,invocation.environment().get("OMICS_SNAKEMAKE_EVENTS")));
            List<String> argv=strings(raw.get("argv"));if(!option(argv,"--executor").equals("local"))throw new IOException("Only the qualified original local executor may use this process path");String conda=option(argv,"--conda-prefix"),images=option(argv,"--apptainer-prefix");SnakemakeOriginalRecorded.absolute(conda);SnakemakeOriginalRecorded.absolute(images);
            if(allow&&(!Path.of(conda).startsWith(root)||!Path.of(images).startsWith(root)))throw new IOException("Permitted installation must use owned fixture/workflow cache paths; external caches are immutable inputs");
            if(schema==2)SnakemakeOriginalManagedPolicy.validate(raw.get("execution_policy"),allow,working.toString(),conda,images);
            runtime=SnakemakeOriginalRuntime.open(invocation.runtime(),check);providers=SnakemakeOriginalDeployment.open(invocation.providers(),check);String python=runtime.python().toRealPath().toString();
            writeJson(records.resolve("engine-context.json"),runtime);writeJson(records.resolve("provider-context.json"),providers);
            var environment=new TreeMap<>(invocation.environment());String prefix=providers.providers().get("apptainer").executable().getParent()+":"+providers.providers().get("conda").executable().getParent()+":"+runtime.environment().resolve("bin");environment.put("PATH",prefix+":"+environment.getOrDefault("PATH","/usr/bin:/bin"));environment.put("PYTHONPATH",helpers.toString());environment.put("PYTHONSAFEPATH","1");environment.put("PYTHONDONTWRITEBYTECODE","1");environment.put("PYTHONNOUSERSITE","1");
            if(environment.containsKey("OMICS_SNAKEMAKE_TOOL_BIN")){Path toolBin=directory(inside(root,environment.get("OMICS_SNAKEMAKE_TOOL_BIN")));environment.put("PATH",toolBin+":"+environment.get("PATH"));}
            var softwareData=new ArrayList<Path>();
            for(String key:List.of("APPTAINER_TMPDIR","CONDA_PKGS_DIRS")) {
                String declared=environment.get(key);if(declared==null||declared.isEmpty())throw new IOException("Require an explicit owned software staging/cache directory: "+key);
                for(String item:declared.split(":",-1)){Path data=inside(root,item);if(data.equals(root)||working.startsWith(data)||helpers.startsWith(data)||records.startsWith(data)||configuration.startsWith(data)||request.startsWith(data)||receipt.startsWith(data))throw new IOException("Software-data scope overlaps workflow/control evidence: "+data);Path parent=data;while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS))parent=parent.getParent();directory(parent);Files.createDirectories(data);directory(data);if(!softwareData.contains(data))softwareData.add(data);}
            }
            List<String> command=List.of(python,"-I","-B",helpers.resolve("controller.py").toString(),request.toString());writeJson(records.resolve("supervised-invocation.json"),Map.of("command",command,"environment",environment,"working_directory",working.toString(),"owned_root",root.toString(),"request",requestPin,"configuration",configPin,"helpers",helperPins()));
            int sourceExit=0;
            try{SnakemakeOriginalProcess.executeManagedSession(command,environment,records.resolve("process"),working,root,invocation.limits(),softwareData,()->{
                if(!pin(request,4_194_304,check).equals(requestPin)||!pin(configuration,1_048_576,check).equals(configPin))throw new IOException("Managed invocation/policy changed");verifyHelpers(helpers,check);
            },check);}catch(SnakemakeOriginalProcess.ExitFailure exit){if(exit.exitCode()!=3||exit.getSuppressed().length!=0)throw exit;sourceExit=3;}
            Path ledger=records.resolve("process/process.json");Pin ledgerPin=pin(ledger,67_108_864,check);Map<?,?> process=object(json(ledger,67_108_864));integer(process.get("exit_code"),sourceExit,sourceExit);
            if(!list(process.get("owned_live_after_cleanup")).isEmpty()||process.containsKey("cleanup_state")||process.containsKey("suppressed")&&!list(process.get("suppressed")).isEmpty())throw new IOException("Managed process cleanup/integrity is incomplete");
            if(sourceExit==0&&!"completed-process-only".equals(process.get("status"))||sourceExit==3&&!"FAIL2-retained-no-result".equals(process.get("status")))throw new IOException("Unexpected original supervisor completion state");
            var owned=new TreeMap<Long,SnakemakeOriginalProcess.Identity>();var ambiguous=new HashSet<Long>();
            for(Object value:list(process.get("owned_processes"))) {Map<?,?> item=object(value);keys(item,"pid","startTicks","parent","group","session","state");long pid=integer(item.get("pid"),1,Long.MAX_VALUE),ticks=integer(item.get("startTicks"),1,Long.MAX_VALUE);var identity=new SnakemakeOriginalProcess.Identity(pid,ticks,integer(item.get("parent"),0,Long.MAX_VALUE),integer(item.get("group"),1,Long.MAX_VALUE),integer(item.get("session"),1,Long.MAX_VALUE),text(item.get("state")));var prior=owned.putIfAbsent(pid,identity);if(prior!=null&&prior.startTicks()!=ticks)ambiguous.add(pid);}
            Pin receiptPin=pin(receipt,67_108_864,check);Map<?,?> controller=object(json(receipt,67_108_864));if(!"ORIGINAL_API_WITH_PROPAGATED_INSTALLATION_POLICY_NOT_ANALYSIS".equals(controller.get("status"))||integer(controller.get("schema_version"),schema,schema)!=schema||integer(controller.get("exit_code"),sourceExit,sourceExit)!=sourceExit||!list(controller.get("integrity_errors")).isEmpty()||schema==1&&!Boolean.TRUE.equals(controller.get("upstream_api_called"))||!Boolean.FALSE.equals(controller.get("user_analysis_admitted"))||integer(controller.get("new_catalog_credit"),0,0)!=0)throw new IOException("Original source controller did not close with a valid process/policy outcome");
            SnakemakeOriginalManagedPolicy.Audit parserPolicy=null;
            if(schema==2){parserPolicy=SnakemakeOriginalManagedPolicy.audit(raw.get("execution_policy"),controller,allow,working.toString(),conda,images);SnakemakeOriginalEvidence.health(object(controller.get("health")),python);if(!list(controller.get("collector_errors")).isEmpty())throw new IOException("Managed source recorder failed");}
            equal(helperPins(),controller.get("helpers_before"),"managed bundled helpers before");equal(helperPins(),controller.get("helpers_after"),"managed bundled helpers after");if(!configPin.equals(fingerprint(controller.get("configuration"),1_048_576)))throw new IOException("Controller/configuration bytes differ");
            long controllerPid=integer(controller.get("controller_pid"),1,Long.MAX_VALUE);var audit=SnakemakeOriginalInstallationPolicy.audit(policyRecords,configuration,controllerPid,owned.keySet(),run,allow,python,conda,images,invocation.limits().processes(),100000,4_194_304,67_108_864,check);
            boolean parserRejected=parserPolicy!=null&&!parserPolicy.accepted();
            if((sourceExit==3)!=(audit.blockedCalls()>0||parserRejected)||sourceExit==0&&!Boolean.TRUE.equals(controller.get("upstream_success")))throw new IOException("Source exit and independently audited parser/installation decisions disagree");
            if(parserRejected&&(audit.deploymentCalls()!=0||audit.spawnCalls()!=0||audit.processes().size()!=1||!audit.processes().contains(controllerPid)))throw new IOException("A rejected managed profile started software deployment or a child policy");
            var policyIdentities=new TreeMap<String,SnakemakeOriginalProcess.Identity>();var reported=new HashSet<Long>();
            for(Object value:list(controller.get("policy_processes"))) {Map<?,?> item=object(value);long pid=integer(item.get("pid"),1,Long.MAX_VALUE);if(!reported.add(pid)||!audit.processes().contains(pid)||ambiguous.contains(pid))throw new IOException("Unowned/reused/repeated installation-policy process identity");equal(item,json(policyRecords.resolve("policy-"+pid+".json"),4_194_304),"controller and independently audited process policy");policyIdentities.put(Long.toString(pid),owned.get(pid));if(pid==controllerPid)equal(command.subList(3,5),item.get("arguments"),"actual supervised source controller arguments");}
            if(!reported.equals(audit.processes()))throw new IOException("Controller policy census differs from actual supervised/audited processes");
            var eventPids=new HashSet<Long>();try(var stream=Files.list(events)){for(Path path:stream.toList()){String name=path.getFileName().toString();if(!name.matches("events-[1-9][0-9]*\\.jsonl"))throw new IOException("Unexpected managed source event member");eventPids.add(Long.parseLong(name.substring(7,name.length()-6)));}}
            if(parserRejected?!eventPids.isEmpty():!eventPids.contains(controllerPid)||!audit.processes().containsAll(eventPids))throw new IOException("Source event processes differ from the actual supervised parser/policy outcome");
            if(schema==2){Map<?,?> streams=object(controller.get("collector_streams"));var names=new HashSet<String>();for(long pid:eventPids){String name="events-"+pid+".jsonl";names.add(name);if(!pin(events.resolve(name),67_108_864,check).equals(fingerprint(streams.get(name),67_108_864)))throw new IOException("Managed source event bytes differ");}if(!streams.keySet().equals(names))throw new IOException("Managed source event declaration differs");}
            if(!pin(ledger,67_108_864,check).equals(ledgerPin)||!pin(receipt,67_108_864,check).equals(receiptPin))throw new IOException("Actual supervisor/controller evidence changed during binding");
            equal(softwareData,process.get("link_data_roots"),"explicit software staging/package data scopes");result=new Bound(sourceExit,controllerPid,audit,policyIdentities,ledgerPin,receiptPin,helperPins(),softwareData,parserPolicy);
        }catch(Exception error){failed=error;}
        finally {
            if(runtime!=null)try{runtime.verify(check);}catch(Exception error){if(failed==null)failed=error;else failed.addSuppressed(error);}
            if(providers!=null)try{providers.verify(check);}catch(Exception error){if(failed==null)failed=error;else failed.addSuppressed(error);}
        }
        if(failed!=null){int code=failed instanceof SnakemakeOriginalRuntime.Unavailable&&failed.getSuppressed().length==0?3:2;writeJson(records.resolve("SUPERVISION_FAILURE.json"),Map.of("status",code==3?"SKIP3-dependent-analysis-skipped":"FAIL2-no-result-admitted","exit_code",code,"error",failed.toString(),"suppressed",java.util.Arrays.stream(failed.getSuppressed()).map(Throwable::toString).toList(),"required_action",ACTION));Files.writeString(records.resolve("SUPERVISION_WARNINGS.md"),"\n## "+(code==3?"SKIP3 dependent analysis skipped":"FAIL2 no result admitted")+"\n\n"+failed+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);if(failed instanceof IOException io)throw io;throw new IOException("Supervised original managed process failed",failed);}
        writeJson(records.resolve("SUPERVISED-POLICY.json"),Map.of("status",result.exitCode()==3?"SUPERVISED_ORIGINAL_INSTALLATION_POLICY_SKIP_NOT_ANALYSIS":"SUPERVISED_ORIGINAL_PROCESS_POLICY_BOUND_NOT_ANALYSIS","schema_version",1,"bound",result,"user_analysis_admitted",false,"new_catalog_credit",0,"whole_goal_complete",false,"required_action",ACTION));
        if(result.exitCode()==3)Files.writeString(records.resolve("SUPERVISION_WARNINGS.md"),"\n## SKIP3: dependent analysis skipped\n\n"+(result.parserPolicy()!=null&&!result.parserPolicy().accepted()?"The effective original workflow profile conflicts with the captured execution policy. No original API, software deployment or worker was called. Inspect the conflicting setting and remove it or select a separately qualified mode in NEW output paths.":"The actual supervised source process refused installation. Its original lower-level nonzero-exit diagnostics remain retained; inspect every blocked entrypoint and use permitted complete software or permitted installation in NEW paths.")+" No source result is admitted.\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        return result;
    }
    private static Path inside(Path root,String value)throws IOException {Path path=Path.of(value);if(!path.isAbsolute()||!path.normalize().equals(path)||!path.startsWith(root))throw new IOException("Require a canonical owned managed-process path: "+value);return path;}
    private static String option(List<String> argv,String name)throws IOException {int index=argv.indexOf(name);if(index<0||index+1>=argv.size()||argv.lastIndexOf(name)!=index)throw new IOException("Declare one explicit original managed-process option: "+name);return argv.get(index+1);}
}
