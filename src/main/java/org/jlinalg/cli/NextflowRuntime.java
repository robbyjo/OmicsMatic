/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jlinalg.workflow.NextflowTraceAudit;
import static org.jlinalg.cli.NextflowFiles.*;

/** Independent Java admission of exact original engine, Java and completed setup. */
final class NextflowRuntime {
    private NextflowRuntime() { }
    static final String MARKER=".omicsmatic-nextflow.json";
    static final String RESOURCE="/org/jlinalg/cli/nextflow-setup/";
    static final Pin LOCK=new Pin(50215,"594b179c0bc08e63f6d06af92b7243b9a3dc1b42b03adb4646261dda1373e720");
    static final Pin INSTALLER=new Pin(21170,"92ab6bcf5be21f6fa36b1d101cc4eda5c47cc23274c9a26e3d115c83c943aed2");
    static final Pin HEALTH=new Pin(445,"78917a18d8f7720eb20692f1fad27091f90f3953418eeeb954371e1e41f5c769");
    static final Pin HEALTH_CONFIG=new Pin(121,"07807145d5acad9856ce0da0d91d501f80caada1a4e152a6ab1e3ed44df34edd");
    static final long FILE_BYTES=268435456,TREE_BYTES=1073741824;
    static final int FILES=10000,META_BYTES=2097152;
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }
    record Helper(String realPath,Pin pin,int mode) { }
    record Context(Path home,Path report,String profile,String version,String kernel,Map<String,Entry> payload,
                   Map<String,Entry> setup,Map<String,Helper> helpers,int permissionDepartures) {
        Context {payload=Map.copyOf(payload);setup=Map.copyOf(setup);helpers=Map.copyOf(helpers);}
        void verify(Runnable cancellation)throws IOException {
            if(!equals(open(home,profile,report,cancellation)))throw new IOException("Nextflow runtime/setup/helper binding changed during analysis");
        }
        List<String> command(){return List.of("/usr/bin/bash",home.resolve("nextflow").toString());}
        Map<String,String> environment(Path nxfHome,int heapMib,int cpus)throws IOException {
            if(!nxfHome.isAbsolute()||!nxfHome.normalize().equals(nxfHome)||heapMib<32||heapMib>1048576||cpus<1||cpus>1024)throw new IOException("Invalid explicit Nextflow Java/runtime capacities");
            privatePath(nxfHome);
            var values=new HashMap<String,String>();values.put("PATH","/usr/bin:/bin");values.put("LANG","C.UTF-8");values.put("LC_ALL","C.UTF-8");values.put("TZ","UTC");
            values.put("NXF_HOME",nxfHome.toString());values.put("CAPSULE_CACHE_DIR",home.resolve("capsule").toString());values.put("NXF_JAVA_HOME",home.resolve("java").toString());
            values.put("NXF_OFFLINE","true");values.put("NXF_DISABLE_CHECK_LATEST","true");values.put("NXF_ANSI_LOG","false");values.put("NXF_PLUGINS_DEFAULT","false");values.put("NXF_ENABLE_SECRETS","false");
            values.put("NXF_OPTS","-Xms32m -Xmx"+heapMib+"m -XX:ReservedCodeCacheSize=128m -XX:ActiveProcessorCount="+cpus);return Map.copyOf(values);
        }
    }
    static List<String> profiles(){return List.of("linux-x86_64-nextflow-21.04.1","linux-x86_64-nextflow-22.10.6","linux-x86_64-nextflow-24.04.4");}
    static byte[] resource(String name,Pin expected)throws IOException {
        byte[] raw;try(var input=NextflowRuntime.class.getResourceAsStream(RESOURCE+name)){if(input==null)throw new IOException("Missing bundled Nextflow setup resource: "+name);raw=input.readNBytes(Math.toIntExact(expected.bytes()+1));}
        if(raw.length!=expected.bytes()||!sha(raw).equals(expected.sha256()))throw new IOException("Bundled Nextflow setup resource differs: "+name);return raw;
    }
    static Map<?,?> lock()throws IOException {
        Map<?,?> data=object(SimpleJson.parse(utf8(resource("nextflow-managed-runtime-lock.json",LOCK))));
        if(integer(data.get("schema_version"),1,1)!=1||!object(data.get("profiles")).keySet().equals(Set.copyOf(profiles())))throw new IOException("Unexpected Nextflow runtime lock profile census");return data;
    }
    static Context open(Path supplied,String requested,Path suppliedReport,Runnable check)throws IOException {
        check.run();String architecture=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(architecture))throw new Unavailable("Original Nextflow execution needs Linux x86_64 Java; use Linux Java inside WSL on Windows");
        if(!profiles().contains(requested))throw new Unavailable("Exact requested Nextflow engine edition is unsupported; no inferred patch or edition substitution");
        Path home=supplied.toAbsolutePath().normalize(),report=suppliedReport.toAbsolutePath().normalize();
        if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS)||!Files.exists(report,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact managed Nextflow home or completed setup report is absent; skip dependent analysis until permitted setup completes");
        directory(home);directory(report);privatePath(home);privatePath(report);
        if(home.startsWith(report)||report.startsWith(home))throw new IOException("Nextflow runtime and setup report overlap");
        for(Path executable:List.of(home.resolve("nextflow"),home.resolve("java/bin/java"))){
            if(!Files.isRegularFile(executable,LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing Nextflow runtime is missing an expected executable: "+executable);
            if(!Files.isExecutable(executable))throw new Unavailable("Required Nextflow/Java execution is forbidden: "+executable);
        }
        if(Files.exists(report.resolve("FAILURE.json"))||Files.exists(report.resolve("FAILURE.md")))throw new IOException("Failed Nextflow setup evidence cannot be admitted");
        Map<?,?> locked=lock(),profile=object(object(locked.get("profiles")).get(requested));String version=text(profile.get("version"));
        Map<?,?> complete=object(json(report.resolve("COMPLETED.json"),65536));keys(complete,"status","manifest","user_analysis_admitted");setupOnly(complete);
        if(!pinObject(complete.get("manifest"),META_BYTES).equals(pin(report.resolve("manifest.json"),META_BYTES,check)))throw new IOException("Nextflow setup completion/manifest hash differs");
        Map<?,?> manifest=object(json(report.resolve("manifest.json"),META_BYTES));keys(manifest,"schema_version","status","profile","version","home","host","lock_sha256","marker","helpers","files","user_analysis_admitted");setupOnly(manifest);
        integer(manifest.get("schema_version"),1,1);
        if(!requested.equals(manifest.get("profile"))||!version.equals(manifest.get("version"))||!home.toString().equals(manifest.get("home"))||!LOCK.sha256().equals(manifest.get("lock_sha256")))throw new IOException("Nextflow setup home/profile/version/lock binding differs");
        Map<?,?> host=object(manifest.get("host"));keys(host,"os","architecture","kernel");String kernel=text(host.get("kernel"));
        if(!"Linux".equals(host.get("os"))||!Set.of("amd64","x86_64").contains(text(host.get("architecture")))||!System.getProperty("os.version").equals(kernel))throw new Unavailable("Nextflow setup host/kernel differs; perform a permitted health recheck on this host and use its new setup report");
        Map<String,Entry> setup=tree(report,FILES,TREE_BYTES,FILE_BYTES,check);var expectedSetup=new TreeMap<>(entries(manifest.get("files"),FILES,FILE_BYTES));
        for(String name:List.of("manifest.json","COMPLETED.json"))expectedSetup.put(name,setup.get(name));
        if(!setup.equals(expectedSetup)||!expectedSetup.keySet().containsAll(Set.of("request.json","health.json","WARNINGS.md","version.stdout","version.stderr","version.process.json","version.launch.json","health/main.nf","health/nextflow.config","health/trace.tsv","health/published/result.txt","health/health.process.json","health/health.launch.json")))throw new IOException("Incomplete/changed Nextflow setup tree or member census");
        if(!pinObject(manifest.get("marker"),META_BYTES).equals(pin(home.resolve(MARKER),META_BYTES,check)))throw new IOException("Nextflow runtime marker differs from completed setup");
        Map<?,?> marker=object(json(home.resolve(MARKER),META_BYTES));keys(marker,"schema_version","tool","profile","version","lock_sha256","payload","permission_mode_departures","user_analysis_admitted");integer(marker.get("schema_version"),1,1);
        if(!"Nextflow".equals(marker.get("tool"))||!requested.equals(marker.get("profile"))||!version.equals(marker.get("version"))||!LOCK.sha256().equals(marker.get("lock_sha256"))||!Boolean.FALSE.equals(marker.get("user_analysis_admitted")))throw new IOException("Nextflow runtime marker identity differs");
        Map<String,Entry> payload=tree(home,FILES,TREE_BYTES,FILE_BYTES,check);var expectedPayload=new TreeMap<>(entries(marker.get("payload"),FILES,FILE_BYTES));expectedPayload.put(MARKER,payload.get(MARKER));
        if(!payload.equals(expectedPayload))throw new IOException("Nextflow runtime payload contains changed/missing/unmanaged members");
        if(!fingerprint(profile.get("engine"),FILE_BYTES).equals(payload.get("nextflow").pin()))throw new IOException("Original Nextflow engine differs from its complete locked asset");
        Map<?,?> javaLock=object(locked.get("java"));Map<String,Entry> expectedJava=entries(javaLock.get("members"),FILES,FILE_BYTES);var actualJava=new TreeMap<String,Entry>();
        for(var row:payload.entrySet())if(row.getKey().startsWith("java/"))actualJava.put(row.getKey().substring(5),row.getValue());
        if(!actualJava.keySet().equals(expectedJava.keySet()))throw new IOException("Complete locked Java member family differs");
        var departures=new TreeMap<String,Object>();
        for(var row:expectedJava.entrySet()){
            Entry expected=row.getValue(),actual=actualJava.get(row.getKey());
            if(!expected.kind().equals(actual.kind())||!java.util.Objects.equals(expected.pin(),actual.pin())||!java.util.Objects.equals(expected.target(),actual.target()))throw new IOException("Original Java runtime bytes/link differ: "+row.getKey());
            if(expected.kind().equals("file")&&expected.mode()!=actual.mode())departures.put("java/"+row.getKey(),Map.of("archive_mode",(long)expected.mode(),"effective_mode",(long)actual.mode()));
            if(expected.kind().equals("file")&&(expected.mode()&0111)!=0&&!Files.isExecutable(home.resolve("java/"+row.getKey())))throw new Unavailable("Required Java runtime executable is forbidden: "+row.getKey());
        }
        if(payload.get("nextflow").mode()!=0755)departures.put("nextflow",Map.of("requested_mode",0755L,"effective_mode",(long)payload.get("nextflow").mode()));
        if(!departures.equals(object(marker.get("permission_mode_departures"))))throw new IOException("Nextflow effective filesystem permission audit differs");
        if(!departures.isEmpty()&&!utf8(bytes(report.resolve("WARNINGS.md"),65536)).contains("Effective filesystem modes differ"))throw new IOException("Missing prominent effective permission-mode warning");
        Map<?,?> requestedHelpers=object(manifest.get("helpers"));List<String> names=strings(locked.get("required_helpers"));if(!requestedHelpers.keySet().equals(Set.copyOf(names)))throw new IOException("Nextflow helper family differs");
        var helpers=new TreeMap<String,Helper>();
        for(String name:names){
            Path path=Path.of(name);if(!Files.exists(path)||!Files.isExecutable(path))throw new Unavailable("Required permitted Nextflow process helper is absent/forbidden: "+name);
            Path real=path.toRealPath();Map<?,?> expected=object(requestedHelpers.get(name));keys(expected,"real_path","bytes","sha256","mode");Helper helper=new Helper(real.toString(),pin(real,FILE_BYTES,check),mode(real));
            if(!helper.realPath.equals(expected.get("real_path"))||!helper.pin.equals(fingerprint(expected,FILE_BYTES))||helper.mode!=integer(expected.get("mode"),0,0777))throw new IOException("Nextflow setup helper changed: "+name);helpers.put(name,helper);
        }
        verifyHealth(home,report,version,check);
        Map<?,?> request=object(json(report.resolve("request.json"),65536));
        if(!INSTALLER.equals(pinObject(request.get("installer"),65536))||!requested.equals(request.get("profile"))||!home.toString().equals(request.get("home"))||!LOCK.sha256().equals(request.get("lock_sha256"))||!Boolean.FALSE.equals(request.get("user_analysis_admitted")))throw new IOException("Nextflow setup request/installer binding differs");
        check.run();return new Context(home,report,requested,version,kernel,payload,setup,helpers,departures.size());
    }
    private static void verifyHealth(Path home,Path report,String version,Runnable check)throws IOException {
        resource("install_nextflow.py",INSTALLER);resource("health.nf",HEALTH);resource("health.config",HEALTH_CONFIG);
        Path health=report.resolve("health");Map<?,?> meta=object(json(report.resolve("health.json"),65536));keys(meta,"status","version","task_count","workflow","config","trace","output","user_analysis_admitted");
        if(!"passed-three-task-original-Nextflow-DAG".equals(meta.get("status"))||!version.equals(meta.get("version"))||integer(meta.get("task_count"),3,3)!=3||!Boolean.FALSE.equals(meta.get("user_analysis_admitted")))throw new IOException("Nextflow health admission differs");
        Pin output=new Pin(10,"67252bba18242c39b82d8acef803f2b7d1441b697769582eba3049a65980bb34");
        for(var row:Map.of("workflow",Map.entry(health.resolve("main.nf"),HEALTH),"config",Map.entry(health.resolve("nextflow.config"),HEALTH_CONFIG),"trace",Map.entry(health.resolve("trace.tsv"),pinObject(meta.get("trace"),65536)),"output",Map.entry(health.resolve("published/result.txt"),output)).entrySet()){
            if(!row.getValue().getValue().equals(pinObject(meta.get(row.getKey()),65536))||!row.getValue().getValue().equals(pin(row.getValue().getKey(),65536,check)))throw new IOException("Complete Nextflow health source/trace/output bytes differ: "+row.getKey());
        }
        if(!NextflowTraceAudit.read(health.resolve("trace.tsv"),3,0,NextflowTraceAudit.Limits.defaults(),check).allObservedTasksSuccessful())throw new IOException("Nextflow health task family is incomplete/failed");
        String versionText=utf8(bytes(report.resolve("version.stdout"),65536))+utf8(bytes(report.resolve("version.stderr"),65536));
        if(!versionText.contains("\n      version "+version+" build "))throw new IOException("Exact original Nextflow version output differs");
        List<String> prefix=List.of("/usr/bin/prlimit","--as=8589934592","--cpu=180","--fsize=536870912","--nofile=4096","--","/usr/bin/bash",home.resolve("nextflow").toString());
        var versionCommand=new ArrayList<>(prefix);versionCommand.add("-version");verifyProcess(report,"version",versionCommand);
        var healthCommand=new ArrayList<>(prefix);healthCommand.addAll(List.of("-C",health.resolve("nextflow.config").toString(),"-log",health.resolve("nextflow.log").toString(),"run",health.resolve("main.nf").toString(),"-ansi-log","false","-with-trace",health.resolve("trace.tsv").toString(),"-work-dir",health.resolve("work").toString(),"--outdir",health.resolve("published").toString()));verifyProcess(health,"health",healthCommand);
    }
    private static void verifyProcess(Path root,String name,List<String> command)throws IOException {
        Map<?,?> process=object(json(root.resolve(name+".process.json"),65536)),launch=object(json(root.resolve(name+".launch.json"),65536));
        if(!"completed-process-only".equals(process.get("status"))||integer(process.get("exit_code"),0,0)!=0||!list(process.get("owned_live_after_cleanup")).isEmpty()||!command.equals(strings(process.get("command")))||!command.equals(strings(launch.get("command"))))throw new IOException("Nextflow setup process command/exit/owned cleanup differs: "+name);
        if(integer(launch.get("pid"),1,Long.MAX_VALUE)!=integer(launch.get("process_group"),1,Long.MAX_VALUE))throw new IOException("Nextflow setup process group differs");integer(launch.get("start_ticks"),0,Long.MAX_VALUE);
    }
    private static Pin pinObject(Object value,long maximum)throws IOException {Map<?,?> map=object(value);keys(map,"bytes","sha256");return fingerprint(map,maximum);}
    private static void setupOnly(Map<?,?> value)throws IOException {if(!"COMPLETE_SETUP_ONLY".equals(value.get("status"))||!Boolean.FALSE.equals(value.get("user_analysis_admitted")))throw new IOException("Nextflow setup is incomplete or misrepresented as user analysis");}
    private static void privatePath(Path path)throws IOException {String text=path.toString();if(text.chars().anyMatch(c->c>127||Character.isWhitespace(c)||c<32||c==127))throw new Unavailable("Nextflow private runtime/report paths need ASCII without whitespace; use new permitted paths");}
}
