/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Reconstruct managed process bindings from raw captured evidence, without consulting producer paths. */
final class SnakemakeOriginalManagedEvidence {
    private SnakemakeOriginalManagedEvidence() { }

    static Map<String,String> softwareRoots(String producer,SnakemakeOriginalRecipe.Request request)throws IOException {
        if(request.managed()==null||request.execution().deployment().equals("preinstalled"))throw new IOException("Require an explicit managed recipe");
        return Map.of("environments",request.managed().environments()==null?producer+"/managed/environments":absolute(request.managed().environments()),
                "images",request.managed().images()==null?producer+"/managed/images":absolute(request.managed().images()),
                "payload",producer+"/managed/payload","configuration",producer+"/managed/configuration");
    }

    static Map<String,String> environment(String producer,String adopted,SnakemakeOriginalRecipe.Request request,String run)throws IOException {
        var roots=softwareRoots(producer,request);String payload=roots.get("payload");
        var env=new TreeMap<>(SnakemakeOriginalEvidence.environment(producer,adopted,request));
        env.put("CONDARC",roots.get("configuration")+"/condarc");env.put("CONDA_SOLVER","libmamba");
        env.put("CONDA_PKGS_DIRS",payload+"/packages");env.put("APPTAINER_TMPDIR",payload);env.put("APPTAINER_CACHEDIR",payload+"/download-cache");
        env.put("APPTAINER_BINDPATH",producer+","+roots.get("environments")+","+roots.get("images"));
        env.put("OMICS_SNAKEMAKE_EVENTS",producer+"/events");env.put("OMICS_SNAKEMAKE_RUN_ID",run);env.put("OMICS_SNAKEMAKE_TOOL_BIN",producer+"/workspace/bin");
        return Map.copyOf(env);
    }

    static List<String> arguments(String producer,String working,SnakemakeOriginalRecipe.Request request)throws IOException {
        var roots=softwareRoots(producer,request);var argv=new ArrayList<>(SnakemakeOriginalEvidence.arguments(producer,working,request));
        var deployment=new ArrayList<String>(List.of("--software-deployment-method"));deployment.addAll(List.of(request.execution().deployment().split("-")));
        deployment.addAll(List.of("--conda-prefix",roots.get("environments"),"--apptainer-prefix",roots.get("images"),"--conda-frontend","conda"));
        argv.addAll(argv.indexOf("--"),deployment);return List.copyOf(argv);
    }

    static SnakemakeOriginalManagedProcess.Bound audit(Path root,String producer,String working,SnakemakeOriginalRecipe.Request request,Map<?,?> runtime,Map<?,?> providers,Runnable check)throws IOException {
        int maximum=request.metadataBytes();var roots=softwareRoots(producer,request);String conda=roots.get("environments"),images=roots.get("images");
        Map<?,?> config=object(json(root.resolve("INSTALLATION-POLICY.json"),maximum));keys(config,"schema_version","run_id","allow_install","records_directory","logger");integer(config.get("schema_version"),1,1);
        String run=text(config.get("run_id"));
        if(!run.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||!Boolean.valueOf(request.execution().allowInstall()).equals(config.get("allow_install"))||!(producer+"/policy-records").equals(config.get("records_directory"))||!"omicsmanaged".equals(config.get("logger")))throw new IOException("Captured installation permission/run/path differs");
        var policy=SnakemakeOriginalManagedPolicy.request(producer,working,request,conda,images,"");
        Map<?,?> command=object(json(root.resolve("COMMAND.json"),maximum));keys(command,"schema_version","helpers","argv","installation_policy","receipt","execution_policy");integer(command.get("schema_version"),2,2);
        equal(arguments(producer,working,request),command.get("argv"),"managed original command arguments");equal(policy,command.get("execution_policy"),"managed recipe/parser policy");
        if(!(producer+"/helpers").equals(command.get("helpers"))||!(producer+"/INSTALLATION-POLICY.json").equals(command.get("installation_policy"))||!(producer+"/CONTROLLER.json").equals(command.get("receipt")))throw new IOException("Captured managed command/control path differs");
        equal(runtime,json(root.resolve("engine-context.json"),maximum),"managed process/runtime adoption");equal(providers,json(root.resolve("provider-context.json"),maximum),"managed process/provider adoption");
        String python=null;
        for(String line:utf8(bytes(root.resolve("runtime/runtime-files.tsv"),67_108_864)).split("\n")){String[] row=line.split("\t",-1);if(row.length==8&&row[0].equals("env")&&row[1].equals("bin/python"))python=absolute(row[6]);}
        if(python==null)throw new IOException("Missing captured canonical selected interpreter");
        var invocationEnvironment=environment(producer,text(runtime.get("environment")),request,run);
        var actualEnvironment=new TreeMap<>(invocationEnvironment);Map<?,?> selected=object(providers.get("providers"));
        String prefix=parent(text(object(selected.get("apptainer")).get("executable")))+":"+parent(text(object(selected.get("conda")).get("executable")))+":"+runtime.get("environment")+"/bin";
        actualEnvironment.put("PATH",producer+"/workspace/bin:"+prefix+":"+invocationEnvironment.get("PATH"));actualEnvironment.put("PYTHONPATH",producer+"/helpers");actualEnvironment.put("PYTHONSAFEPATH","1");actualEnvironment.put("PYTHONDONTWRITEBYTECODE","1");actualEnvironment.put("PYTHONNOUSERSITE","1");
        var executable=List.of(python,"-I","-B",producer+"/helpers/controller.py",producer+"/COMMAND.json");
        Map<?,?> supervised=object(json(root.resolve("supervised-invocation.json"),maximum));keys(supervised,"command","environment","working_directory","owned_root","request","configuration","helpers");
        equal(executable,supervised.get("command"),"managed actual supervised command");equal(actualEnvironment,supervised.get("environment"),"managed actual supervised environment");
        if(!working.equals(supervised.get("working_directory"))||!producer.equals(supervised.get("owned_root"))||!pin(root.resolve("COMMAND.json"),maximum,check).equals(fingerprint(supervised.get("request"),maximum))||!pin(root.resolve("INSTALLATION-POLICY.json"),maximum,check).equals(fingerprint(supervised.get("configuration"),maximum)))throw new IOException("Managed actual supervised cwd/request/configuration differs");
        var helperPins=SnakemakeOriginalManagedProcess.helperPins();equal(helperPins,supervised.get("helpers"),"managed supervised helper edition");
        SnakemakeOriginalEvidence.process(root,producer,working,executable,actualEnvironment,request);
        Map<?,?> process=object(json(root.resolve("process/process.json"),maximum));
        if(process.containsKey("cleanup_state")||process.containsKey("suppressed")&&!list(process.get("suppressed")).isEmpty())throw new IOException("Managed process retained uncertain cleanup or suppressed failures");
        var data=List.of(roots.get("payload"),roots.get("payload")+"/packages");equal(data,process.get("link_data_roots"),"managed owned software-data scopes");
        var owned=new TreeMap<Long,SnakemakeOriginalProcess.Identity>();var ambiguous=new HashSet<Long>();
        for(Object value:list(process.get("owned_processes"))) {
            Map<?,?> item=object(value);keys(item,"pid","startTicks","parent","group","session","state");
            long pid=integer(item.get("pid"),1,Long.MAX_VALUE),ticks=integer(item.get("startTicks"),1,Long.MAX_VALUE);
            var identity=new SnakemakeOriginalProcess.Identity(pid,ticks,integer(item.get("parent"),0,Long.MAX_VALUE),integer(item.get("group"),1,Long.MAX_VALUE),integer(item.get("session"),1,Long.MAX_VALUE),text(item.get("state")));
            var prior=owned.putIfAbsent(pid,identity);if(prior!=null&&prior.startTicks()!=ticks)ambiguous.add(pid);
        }
        Map<?,?> controller=object(json(root.resolve("CONTROLLER.json"),maximum));
        keys(controller,"schema_version","status","controller_pid","exit_code","upstream_api_called","upstream_success","failure","integrity_errors","policy_processes","configuration","helpers_before","helpers_after","user_analysis_admitted","new_catalog_credit","execution_policy","effective_policy","option_sources","policy_status","health","collector_streams","collector_errors");
        integer(controller.get("schema_version"),2,2);integer(controller.get("exit_code"),0,0);integer(controller.get("new_catalog_credit"),0,0);
        if(!"ORIGINAL_API_WITH_PROPAGATED_INSTALLATION_POLICY_NOT_ANALYSIS".equals(controller.get("status"))||!Boolean.TRUE.equals(controller.get("upstream_api_called"))||!Boolean.TRUE.equals(controller.get("upstream_success"))||controller.get("failure")!=null||!Boolean.FALSE.equals(controller.get("user_analysis_admitted"))||!list(controller.get("integrity_errors")).isEmpty()||!list(controller.get("collector_errors")).isEmpty())throw new IOException("Managed source controller did not succeed with complete original records");
        SnakemakeOriginalEvidence.health(object(controller.get("health")),python);
        var parser=SnakemakeOriginalManagedPolicy.audit(policy,controller,request.execution().allowInstall(),working,conda,images);
        if(!parser.accepted()||!parser.upstreamApiCalled())throw new IOException("Managed effective original parser did not admit the recorded full target DAG");
        long pid=integer(controller.get("controller_pid"),1,Long.MAX_VALUE);
        var audit=SnakemakeOriginalInstallationPolicy.audit(root.resolve("policy-records"),root.resolve("INSTALLATION-POLICY.json"),pid,owned.keySet(),run,request.execution().allowInstall(),python,conda,images,request.process().processes(),100000,4_194_304,67_108_864,check);
        if(audit.blockedCalls()!=0)throw new IOException("A blocked installation cannot be a complete managed result");
        equal(helperPins,controller.get("helpers_before"),"managed source helpers before");equal(helperPins,controller.get("helpers_after"),"managed source helpers after");
        if(!audit.configuration().equals(fingerprint(controller.get("configuration"),maximum)))throw new IOException("Managed source/configuration binding differs");
        var identities=new TreeMap<String,SnakemakeOriginalProcess.Identity>();var reported=new HashSet<Long>();
        for(Object value:list(controller.get("policy_processes"))) {
            Map<?,?> item=object(value);long child=integer(item.get("pid"),1,Long.MAX_VALUE);
            if(!reported.add(child)||!audit.processes().contains(child)||ambiguous.contains(child))throw new IOException("Managed policy process is repeated, unowned or has ambiguous kernel identity");
            equal(item,json(root.resolve("policy-records/policy-"+child+".json"),4_194_304),"managed independently closed policy");
            if(!(producer+"/INSTALLATION-POLICY.json").equals(item.get("config_file")))throw new IOException("Managed policy process used a different producer configuration");
            identities.put(Long.toString(child),owned.get(child));
            if(child==pid){equal(executable.subList(3,5),item.get("arguments"),"managed actual source controller arguments");if(!working.equals(item.get("working_directory")))throw new IOException("Managed source controller cwd differs");}
        }
        if(!reported.equals(audit.processes()))throw new IOException("Managed complete policy/controller process census differs");
        for(Object value:object(controller.get("collector_streams")).keySet()) {
            String name=text(value);if(!name.matches("events-[1-9][0-9]*\\.jsonl")||!audit.processes().contains(Long.parseLong(name.substring(7,name.length()-6))))throw new IOException("Managed event stream is not a supervised policy process");
        }
        var bound=new SnakemakeOriginalManagedProcess.Bound(0,pid,audit,identities,pin(root.resolve("process/process.json"),maximum,check),pin(root.resolve("CONTROLLER.json"),maximum,check),helperPins,data,parser);
        equalBound(bound,json(root.resolve("managed-process-binding.json"),maximum));
        Map<?,?> seal=object(json(root.resolve("SUPERVISED-POLICY.json"),maximum));keys(seal,"status","schema_version","bound","user_analysis_admitted","new_catalog_credit","whole_goal_complete","required_action");integer(seal.get("schema_version"),1,1);integer(seal.get("new_catalog_credit"),0,0);
        if(!"SUPERVISED_ORIGINAL_PROCESS_POLICY_BOUND_NOT_ANALYSIS".equals(seal.get("status"))||!Boolean.FALSE.equals(seal.get("user_analysis_admitted"))||!Boolean.FALSE.equals(seal.get("whole_goal_complete"))||!SnakemakeOriginalManagedProcess.ACTION.equals(seal.get("required_action")))throw new IOException("Managed process/policy seal scope differs");
        equalBound(bound,seal.get("bound"));return bound;
    }

    private static String parent(String path)throws IOException {absolute(path);return path.substring(0,path.lastIndexOf('/'));}
    private static void equalBound(SnakemakeOriginalManagedProcess.Bound expected,Object saved)throws IOException {
        var normalized=copy(object(saved));var policy=copy(object(normalized.get("policy")));var processes=new HashSet<Long>();
        for(Object value:list(policy.get("processes")))if(!processes.add(integer(value,1,Long.MAX_VALUE)))throw new IOException("Repeated policy process in saved managed binding");
        if(!processes.equals(expected.policy().processes()))throw new IOException("Saved managed binding process census differs");
        // Audit.processes is a set; its JSON array order is not process identity.
        policy.put("processes",expected.policy().processes());normalized.put("policy",policy);equal(expected,normalized,"independently reconstructed complete managed binding");
    }
}
