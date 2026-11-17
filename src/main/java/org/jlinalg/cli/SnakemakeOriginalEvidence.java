/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Independent structural audit of saved software, command and process evidence; no host execution. */
final class SnakemakeOriginalEvidence {
    static final Set<String> RUNTIME_FILES=Set.of("READY.json","WARNINGS.md","OFFICIAL-PACKAGE-FILES.json","DRIVER-CONTROLS.json","HEALTH-PROCESS.json","health.stdout","health.stderr","ELF-DEPENDENCIES.json","runtime-files.tsv","pixi.lock");
    private SnakemakeOriginalEvidence() { }
    static Map<String,String> stringsMap(Object value)throws IOException {var result=new TreeMap<String,String>();for(var row:object(value).entrySet())result.put(text(row.getKey()),text(row.getValue()));return Map.copyOf(result);}
    static Map<?,?> runtime(Path root,SnakemakeOriginalRecipe.Request request,Runnable check)throws IOException {
        Map<?,?> context=object(json(root.resolve("runtime-context.json"),request.metadataBytes()));keys(context,"home","environment","python","metadata","manifest","entries");
        String environment=absolute(text(context.get("environment"))),python=absolute(text(context.get("python")));absolute(text(context.get("home")));
        if(!python.equals(environment+"/bin/python"))throw new IOException("Recorded adopted Python binding differs");
        Map<?,?> pins=object(context.get("metadata"));if(!pins.keySet().equals(RUNTIME_FILES))throw new IOException("Incomplete recorded runtime metadata family");
        for(String name:RUNTIME_FILES)if(!pin(root.resolve("runtime").resolve(name),67_108_864,check).equals(fingerprint(pins.get(name),67_108_864)))throw new IOException("Recorded runtime metadata bytes differ: "+name);
        Map<?,?> ready=object(json(root.resolve("runtime/READY.json"),4_194_304));
        keys(ready,"status","schema_version","profile","environment","python","kernel","architecture","versions","entries","external_paths","logical_environment_file_bytes","runtime_manifest","health_sha256","official_package_sha256","elf_dependencies_sha256","driver_controls_sha256","lock_sha256","adopter_sha256","project_helpers","metadata","project_java_adapter_qualified","user_analysis_admitted","author_environment_reproduced","whole_goal_complete");
        integer(ready.get("schema_version"),2,2);
        if(!"READY_ADOPTED_SELECTED_SNAKEMAKE_RUNTIME_NOT_USER_ANALYSIS".equals(ready.get("status"))||!request.profile().equals(ready.get("profile"))||!"x86_64".equals(ready.get("architecture"))||!environment.equals(ready.get("environment"))||!python.equals(ready.get("python"))||!SnakemakeOriginalRuntime.VERSIONS.equals(stringsMap(ready.get("versions"))))throw new IOException("Recorded selected runtime profile/version/bindings differ");
        text(ready.get("kernel"));for(String field:List.of("project_java_adapter_qualified","user_analysis_admitted","author_environment_reproduced","whole_goal_complete"))if(!Boolean.FALSE.equals(ready.get(field)))throw new IOException("Runtime setup incorrectly claims analysis/paper/goal admission");
        if(!SnakemakeOriginalRuntime.helperPins().equals(stringsMap(ready.get("project_helpers"))))throw new IOException("Recorded runtime helper edition differs");
        var setupPins=new TreeMap<String,Object>();for(var row:pins.entrySet())if(!row.getKey().equals("READY.json"))setupPins.put(text(row.getKey()),row.getValue());equal(setupPins,ready.get("metadata"),"setup metadata census");
        for(var field:Map.of("health_sha256","health.stdout","official_package_sha256","OFFICIAL-PACKAGE-FILES.json","elf_dependencies_sha256","ELF-DEPENDENCIES.json","driver_controls_sha256","DRIVER-CONTROLS.json","lock_sha256","pixi.lock").entrySet())if(!fingerprint(pins.get(field.getValue()),67_108_864).sha256().equals(ready.get(field.getKey())))throw new IOException("Recorded software metadata binding differs: "+field.getKey());
        if(!SnakemakeOriginalRuntime.OFFICIAL.equals(fingerprint(pins.get("OFFICIAL-PACKAGE-FILES.json"),1_048_576).sha256())||!SnakemakeOriginalRuntime.LOCK.equals(fingerprint(pins.get("pixi.lock"),1_048_576).sha256()))throw new IOException("Recorded official wheel map or dependency lock differs");
        Pin manifest=pin(root.resolve("runtime/runtime-files.tsv"),67_108_864,check);if(!manifest.equals(fingerprint(context.get("manifest"),67_108_864))||!manifest.equals(fingerprint(ready.get("runtime_manifest"),67_108_864)))throw new IOException("Recorded complete software manifest differs");
        int count=(int)integer(ready.get("entries"),1,200000);if(count!=integer(context.get("entries"),1,200000))throw new IOException("Runtime manifest counts disagree");
        String[] lines=utf8(bytes(root.resolve("runtime/runtime-files.tsv"),67_108_864)).split("\n",-1);if(lines.length!=count+2||!lines[0].equals("root\tpath\tkind\tmode\tbytes\ttarget\tresolved\tsha256")||!lines[lines.length-1].isEmpty())throw new IOException("Incomplete recorded software manifest");
        var env=new TreeMap<String,String[]>();var systems=new HashSet<String>();var duplicate=new HashSet<String>();var packageFiles=new TreeMap<String,Pin>();long logical=0;
        for(int i=1;i<lines.length-1;i++) {
            check.run();String[] row=lines[i].split("\t",-1);if(row.length!=8||!duplicate.add(row[0]+'\t'+row[1]))throw new IOException("Malformed/duplicate recorded software row");
            int mode;long size;try{mode=Integer.parseInt(row[3]);size=Long.parseLong(row[4]);}catch(NumberFormatException invalid){throw new IOException("Invalid recorded software mode/size",invalid);}if(mode<0||mode>0777||size<0)throw new IOException("Recorded software mode/size out of range");absolute(row[6]);
            if(!Set.of("file","directory","link").contains(row[2])||!row[2].equals("link")&&!row[5].equals("-"))throw new IOException("Recorded software kind/link metadata differs");
            if(row[2].equals("directory")||row[7].equals("-")){if(size!=0||!row[7].equals("-")||row[2].equals("file"))throw new IOException("Recorded software directory pin differs");}else {try{new Pin(size,row[7]);}catch(IllegalArgumentException invalid){throw new IOException("Invalid complete recorded software file pin",invalid);}}
            if(row[0].equals("env")) {
                member(row[1]);env.put(row[1],row);if(!row[2].equals("link")&&!row[6].equals(environment+'/'+row[1]))throw new IOException("Recorded unlinked software member is not canonical");
                if(!row[7].equals("-"))logical=Math.addExact(logical,size);
                String prefix="lib/python3.12/site-packages/snakemake/";if(row[1].startsWith(prefix)&&row[2].equals("file"))packageFiles.put(row[1].substring(prefix.length()),new Pin(size,row[7]));
            }else if(row[0].equals("system")){absolute(row[1]);systems.add(row[1]);if(row[2].equals("directory"))throw new IOException("Unsealed system directory dependency");}
            else throw new IOException("Unknown recorded software root");
        }
        for(String name:env.keySet()){int slash=name.lastIndexOf('/');if(slash>=0){String[] parent=env.get(name.substring(0,slash));if(parent==null||!parent[2].equals("directory"))throw new IOException("Incomplete installed software directory census");}}
        if(logical!=integer(ready.get("logical_environment_file_bytes"),0,1_000_000_000_000L)||systems.size()!=integer(ready.get("external_paths"),1,200000))throw new IOException("Recorded software byte/path totals disagree");
        Map<?,?> original=object(SnakemakeOriginalParameters.parse(utf8(SnakemakeOriginalRuntime.resource("official-package.json")),1_048_576,100000));var expected=new TreeMap<String,Pin>();for(var row:object(original.get("files")).entrySet())expected.put(text(row.getKey()),fingerprint(row.getValue(),67_108_864));if(!expected.equals(packageFiles))throw new IOException("Recorded whole original Snakemake package differs from selected wheel");
        Map<?,?> elf=object(json(root.resolve("runtime/ELF-DEPENDENCIES.json"),4_194_304));if(!systems.equals(new HashSet<>(strings(elf.get("external_paths")))))throw new IOException("Recorded loader dependency path census differs");
        for(String helper:List.of("/usr/bin/setsid","/usr/bin/prlimit","/usr/bin/bash","/usr/bin/ldd","/usr/bin/env","/bin/sh"))if(!systems.contains(helper))throw new IOException("Recorded system helper omitted: "+helper);
        Map<?,?> health=object(json(root.resolve("runtime/health.stdout"),1_048_576));if(!"PASS_SELECTED_SOURCE_IMPORT_HEALTH_NOT_WORKFLOW_ADMISSION".equals(health.get("status")))throw new IOException("Recorded import health did not succeed");health(health,python);return context;
    }
    static void health(Map<?,?> value,String python)throws IOException {
        if(!SnakemakeOriginalRuntime.PROFILE.equals(value.get("profile"))||!python.equals(value.get("executable"))||!SnakemakeOriginalRuntime.VERSIONS.equals(stringsMap(value.get("versions"))))throw new IOException("Original driver/runtime import profile differs");
        Map<?,?> files=object(value.get("source_files"));equal(Map.of("cli.py","8e48111da41539e82ebfa54a41e9da26e72c3604be8415e7bf87f7e122018236","benchmark.py","2d584d8aa2ad44e4f9b776d787a9454a31704a91fdd6fac00d1430cd066468d7"),files,"selected imported source files");
        if(!text(value.get("python")).startsWith("3.12.12 "))throw new IOException("Recorded original Python edition differs");
    }
    static List<String> arguments(String producer,String working,SnakemakeOriginalRecipe.Request request)throws IOException {
        var x=request.execution();var result=new ArrayList<>(List.of("--snakefile",join(working,request.workflow().snakefile()),"--directory",working,"--executor","local","--cores",Integer.toString(x.cores()),"--scheduler",x.scheduler(),"--retries",Integer.toString(x.retries()),"--latency-wait",Integer.toString(x.latencyWait()),"--forceall","--keep-incomplete"));
        if(x.notemp())result.add("--notemp");if(x.extended())result.add("--benchmark-extended");result.add("--configfile");for(String config:request.workflow().configfiles())result.add(join(working,config));result.add(producer+"/parameters.json");result.add("--");result.addAll(request.workflow().targets());return List.copyOf(result);
    }
    static Map<String,String> environment(String producer,String adopted,SnakemakeOriginalRecipe.Request request){var env=new TreeMap<>(Map.of("PATH",producer+"/workspace/bin:"+adopted+"/bin:/usr/bin:/bin","LANG","C.UTF-8","LC_ALL","C.UTF-8","TZ","UTC","XDG_CACHE_HOME",producer+"/workspace/cache","PYTHONDONTWRITEBYTECODE","1","PYTHONNOUSERSITE","1"));env.putAll(request.environment());return Map.copyOf(env);}
    static void process(Path root,String producer,String working,List<String> command,SnakemakeOriginalRecipe.Request request)throws IOException {
        Map<?,?> ledger=object(json(root.resolve("process/process.json"),request.metadataBytes())),launch=object(json(root.resolve("process/launch.json"),request.metadataBytes()));
        if(!"completed-process-only".equals(ledger.get("status"))||integer(ledger.get("exit_code"),0,255)!=0||!list(ledger.get("owned_live_after_cleanup")).isEmpty())throw new IOException("Original process did not finish successfully with complete owned cleanup");
        List<String> guarded=strings(ledger.get("command"));var limit=request.process();if(guarded.size()!=14+command.size())throw new IOException("Guarded original command length differs");
        var expected=new ArrayList<>(List.of("/usr/bin/setsid","--fork","--wait","/usr/bin/bash","-c","IFS= read -r omics_gate || exit 125; omics_expected=$1; shift; [ \"$omics_gate\" = \"$omics_expected\" ] || exit 125; exec \"$@\"","snakemake-owned-launcher",guarded.get(7),"/usr/bin/prlimit","--as="+limit.addressSpaceBytes(),"--cpu="+limit.cpuSeconds(),"--fsize="+limit.fileBytes(),"--nofile="+limit.openFiles(),"--"));
        // There are fourteen supervisor arguments before the original argv.
        expected.addAll(command);if(!guarded.get(7).matches("[0-9a-f-]{36}"))throw new IOException("Invalid owned launch gate identity");equal(expected,guarded,"owned process invocation");
        var environment=new TreeMap<>(environment(producer,text(object(json(root.resolve("runtime-context.json"),request.metadataBytes())).get("environment")),request));environment.put("HOME",working);environment.put("TMPDIR",working);
        for(Map<?,?> record:List.of(ledger,launch)){equal(guarded,record.get("command"),"process/launch command");equal(environment,record.get("environment"),"effective process environment");equal(limit,record.get("limits"),"process capacities");if(!working.equals(record.get("working_directory"))||!producer.equals(record.get("workspace_root")))throw new IOException("Recorded owned execution directories differ");}
        if(!"running".equals(launch.get("status")))throw new IOException("Missing original running launch state");
        integer(ledger.get("peak_workspace_bytes"),0,limit.workspaceBytes());integer(ledger.get("peak_sampled_rss_bytes"),0,limit.sampledRssBytes());integer(ledger.get("peak_processes"),1,limit.processes());integer(ledger.get("peak_threads"),1,limit.threads());
        if(integer(launch.get("owned_launcher_pid"),1,Long.MAX_VALUE)!=integer(ledger.get("owned_launcher_pid"),1,Long.MAX_VALUE))throw new IOException("Owned launcher identities differ");
        if(list(ledger.get("owned_processes")).isEmpty())throw new IOException("Missing registered owned process family");
        for(Object row:list(ledger.get("owned_processes"))){Map<?,?> value=object(row);keys(value,"pid","startTicks","parent","group","session","state");integer(value.get("pid"),1,Long.MAX_VALUE);integer(value.get("startTicks"),1,Long.MAX_VALUE);integer(value.get("session"),1,Long.MAX_VALUE);}
    }
}
