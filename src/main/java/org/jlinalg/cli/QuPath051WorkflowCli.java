/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import static org.jlinalg.cli.QuPath051Request.*;

/** Runs the unchanged full original QuPath script engine with complete declared-output admission. */
public final class QuPath051WorkflowCli {
    private QuPath051WorkflowCli() { }
    private static final String RESOURCE="/org/jlinalg/omics/qupath051/";
    private static final Pattern CONDITION=Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[[^\\r\\n\\]]+\\] \\[(ERROR|WARN)\\s*\\] [A-Za-z_$][A-Za-z0-9_.$]* - .*$");
    private static final String SCOPE="Runs the complete unchanged original QuPath0.5.1 release and its embedded Java21.0.2 through the original headless Groovy script engine. The caller's exact script receives the owned work directory as args[0]; captured literal files are in inputs/ and every result must be declared under results/. This is the original engine, not a reduced interpreter or replacement cell detector. Source/OS editions, separate extensions/models, stain vectors, ROIs and manual thresholds remain explicit. Original errors, warnings, returns and all result bytes are retained. Structural completion and file integrity do not independently establish scientific/model correctness, producer authenticity or paper reproduction.";
    private static final String ACTION="Required action: supply the documented standalone development JAR, matching complete private original0.5.1 provider, a trusted UTF-8 Groovy script, explicit input provenance/population, every expected output, a literal expected return marker, ALLOW execution/trusted-code policies and NEW output. Run provider.py with permitted install/download flags when needed. SKIP3 skips dependent analysis if execution, trusted code, source, runtime or a required class is unavailable/forbidden. Other editions/extensions need separate qualification; never modify a pinned provider in place. FAIL2 admits no complete result: retain complete inputs/outputs, original process logs and ORIGINAL_CONDITIONS.tsv; resolve the named cause and retry NEW paths. Exit0 alone is insufficient: original ERROR logs, missing/extra outputs or missing/duplicate completion markers refuse admission. Review every original warning and justify/correct the model/input choice. Failed/partial runs retain every completed file but are not complete analyses. Trusted scripts run with account permissions; this is not an OS security sandbox or CPU/RSS containment guarantee. The original script must report the declared complete population, not hide caught failures or return early.";
    private static final class Skipped extends IOException {private static final long serialVersionUID=1L;Skipped(String message){super(message);}}
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        Path out=null;boolean owned=false,admitted=false;int code=2;String cause="FAIL2: invalid original-script request";int warnings=0,errors=0;
        try {
            if(args.length==1&&args[0].equals("--help")){stdout.println("qupath051-workflow --request UTF8_JSON\n"+SCOPE+"\n"+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Require --request JSON or --help");
            Path request=Path.of(args[1]).toAbsolutePath().normalize();var requestPin=NextflowFiles.pin(request,1048576,()->{});byte[] raw=NextflowFiles.bytes(request,1048576);
            require(NextflowFiles.sha(raw).equals(requestPin.sha256()),"Request changed during complete capture");var r=parse(object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,200000)),request.getParent());out=r.out();
            var protectedPaths=new ArrayList<Path>(List.of(request,r.script(),r.provider()));protectedPaths.addAll(r.inputs().values());
            require(!Files.exists(out,LinkOption.NOFOLLOW_LINKS),"Output must be NEW; retain earlier evidence");NextflowFiles.directory(out.getParent());
            for(Path path:protectedPaths)require(!out.startsWith(path)&&!path.startsWith(out),"Output overlaps protected request/script/input/provider");
            require(!out.toString().contains("\""),"The owned output path must not contain a quote; choose a NEW supported path without renaming identifiers inside input files");
            Files.createDirectory(out);owned=true;Files.write(out.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            if(!r.execute())throw new Skipped("Execution FORBID; no original process launched");
            if(!r.trusted())throw new Skipped("Trusted-code policy FORBID; no original process launched");
            if(!Files.isRegularFile(r.provider().resolve("READY.json"),LinkOption.NOFOLLOW_LINKS))throw new Skipped("Complete original provider is unavailable; run the permitted version-specific setup first");
            byte[] profileRaw=resource("source-profile.json");var profile=object(NextflowParameters.parse(NextflowFiles.utf8(profileRaw),16777216,2000000));
            var build=object(NextflowParameters.parse(NextflowFiles.utf8(resource("build-profile.json")),1048576,200000));
            Path readyFile=r.provider().resolve("READY.json");var readyPin=NextflowFiles.pin(readyFile,1048576,()->{});
            var ready=object(NextflowFiles.json(readyFile,1048576));same(readyFile,readyPin);
            require(text(ready.get("status")).equals("READY_PRIVATE_ORIGINAL_QUPATH051_NOT_ANALYSIS")&&text(ready.get("version")).equals("0.5.1")&&text(ready.get("source_commit")).equals("4e6bfc31904be7ee39c10d457149cb962002e1f2"),"Original complete provider identity differs");
            require(NextflowFiles.fingerprint(ready.get("source_profile"),16777216).equals(new NextflowFiles.Pin(profileRaw.length,NextflowFiles.sha(profileRaw))),"Provider/source profile differs from packaged exact version");
            require(NextflowFiles.fingerprint(ready.get("provider_source"),1048576).equals(NextflowFiles.fingerprint(build.get("provider_source"),1048576)),"Provider installer edition differs from this development build");
            String platform=text(ready.get("platform"));String expectedPlatform=System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")?"windows-x86_64":"linux-x86_64";
            if(!platform.equals(expectedPlatform))throw new Skipped("The declared provider belongs to another OS; install the selected native-platform release separately");
            String architecture=System.getProperty("os.arch").toLowerCase(Locale.ROOT);if(!Set.of("amd64","x86_64").contains(architecture))throw new Skipped("Only selected original x86_64 release profiles are available");
            var selected=object(object(profile.get("platforms")).get(platform));Path runtime=r.provider().resolve("runtime"),source=r.provider().resolve("source");
            verifyTree(runtime,object(selected.get("files")));verifyTree(source,object(profile.get("source_members")));
            Path launcher=runtime.resolve(NextflowFiles.member(text(selected.get("launcher"))));require(text(ready.get("launcher")).equals(launcher.toString()),"Declared original launcher differs");
            if(!Files.isExecutable(launcher))throw new Skipped("Original native launcher is not executable; supply permitted filesystem/native runtime prerequisites");
            checkClasses(runtime,object(selected.get("files")).keySet(),r.classes());
            if(!Files.isRegularFile(r.script(),LinkOption.NOFOLLOW_LINKS))throw new Skipped("Declared trusted Groovy script is unavailable");
            var scriptPin=NextflowFiles.pin(r.script(),16777216,()->{});byte[] script=NextflowFiles.bytes(r.script(),16777216);NextflowFiles.utf8(script);require(NextflowFiles.sha(script).equals(scriptPin.sha256()),"Trusted script changed during capture");Files.write(out.resolve("script.groovy"),script,StandardOpenOption.CREATE_NEW);
            Path inputs=out.resolve("inputs"),results=out.resolve("results");Files.createDirectory(inputs);Files.createDirectory(results);long bytes=0;var inputPins=new LinkedHashMap<String,NextflowFiles.Pin>();
            for(var entry:r.inputs().entrySet()) {
                Path original=entry.getValue();if(!Files.isRegularFile(original,LinkOption.NOFOLLOW_LINKS))throw new Skipped("Declared complete input is unavailable: "+original);
                var expected=NextflowFiles.pin(original,r.inputBytes(),()->{});bytes=Math.addExact(bytes,expected.bytes());require(bytes<=r.inputBytes(),"Complete input population exceeds declared capacity");Path captured=inputs.resolve(entry.getKey());Files.createDirectories(captured.getParent());Files.copy(original,captured);
                same(captured,expected);same(original,expected);inputPins.put(entry.getKey(),expected);
            }
            var capturedPins=new LinkedHashMap<String,Object>();for(var entry:inputPins.entrySet())capturedPins.put(entry.getKey(),Map.of("bytes",entry.getValue().bytes(),"sha256",entry.getValue().sha256()));
            NextflowFiles.writeJson(out.resolve("input-pins.json"),capturedPins);NextflowFiles.writeJson(out.resolve("original-provider.json"),ready);
            Path home=out.resolve("owned-home"),prefs=out.resolve("owned-preferences");Files.createDirectory(home);Files.createDirectory(prefs);String node="omicsmatic/qupath051/"+UUID.randomUUID();
            String options="-Xmx"+r.heap()+"m -Djava.awt.headless=true -Duser.home=\""+home+"\" -Djava.util.prefs.userRoot=\""+prefs+"\" -Dqupath.prefs.name="+node+" -Doffline=true";
            NextflowFiles.writeJson(out.resolve("preferences.json"),Map.of("qupath_preference_node",node,"user_home",home.toString(),"java_preferences_root",prefs.toString(),"java_tool_options",options,"windows_preferences_note","Java Preferences may store this fresh named node in the current-user registry; the existing QuPath node is not selected."));
            List<String> command=List.of(launcher.toString(),"script","--args",out.toString(),out.resolve("script.groovy").toString());
            int exit=execute(command,out,r,options);
            verifyTree(runtime,object(selected.get("files")));verifyTree(source,object(profile.get("source_members")));same(readyFile,readyPin);same(r.script(),scriptPin);same(out.resolve("script.groovy"),scriptPin);same(request,requestPin);
            for(var entry:inputPins.entrySet()){same(r.inputs().get(entry.getKey()),entry.getValue());same(inputs.resolve(entry.getKey()),entry.getValue());}
            int[] conditions=conditions(out,r.expectedReturn());warnings=conditions[0];errors=conditions[1];
            require(exit==0,"Original process failed with exit "+exit+"; full outputs/logs are retained");
            require(errors==0,"Original ERROR-level diagnostics refuse complete analysis even with exit0");
            require(conditions[2]==1,"Require exactly one original ScriptCommand return matching the declared complete-population marker");
            var files=NextflowFiles.tree(results,r.files(),r.outputBytes(),r.outputBytes(),()->{});require(files.keySet().equals(r.outputs().keySet()),"Missing/extra complete original result files; no prefix/subset admitted");
            for(var entry:files.entrySet())require(entry.getValue().kind().equals("file")&&entry.getValue().pin().bytes()>=r.outputs().get(entry.getKey()),"Nonregular/incomplete expected original output: "+entry.getKey());
            NextflowFiles.writeJson(out.resolve("result-files.json"),NextflowFiles.manifest(files));
            NextflowFiles.writeJson(out.resolve("completion.json"),Map.of("version","0.5.1","expected_return",r.expectedReturn(),"original_return_markers",conditions[2],"input_provenance",r.provenance(),"analysis_population",r.population(),"original_warning_headers",warnings,"original_error_headers",errors,"all_declared_result_files_present",true,"scientific_refit_or_authenticity_established",false));
            admitted=true;code=0;cause="PASS_COMPLETE_DECLARED_ORIGINAL_QUPATH051_SCRIPT_OUTPUTS";
        }catch(Skipped error){cause="SKIP3: "+error.getMessage();code=3;}catch(Exception error){cause="FAIL2: "+error;code=2;}catch(OutOfMemoryError error){cause="FAIL2: Java heap exhausted; no partial analysis admitted";code=2;}
        try {
            if(owned) {
                String warningText="# "+cause+"\n\nOriginal warning headers: "+warnings+"; original error headers: "+errors+". Full multiline conditions remain in process.stdout/process.stderr; ORIGINAL_CONDITIONS.tsv locates header lines. Review every warning and justify or correct the cause.\n\n"+SCOPE+"\n\n"+ACTION+"\n";
                Files.writeString(out.resolve("WARNINGS.md"),warningText,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                var all=NextflowFiles.tree(out,1000000,137438953472L,137438953472L,()->{});
                NextflowFiles.writeJson(out.resolve("MANIFEST.json"),Map.of("files",NextflowFiles.manifest(all),"excluded_final_records",List.of("MANIFEST.json","analysis-status.json")));
                // Write the admission record only after the complete data/warnings manifest exists.
                NextflowFiles.writeJson(out.resolve("analysis-status.json"),Map.of("status",cause,"exit_code",code,"result_admitted",admitted,"original_warning_headers",warnings,"original_error_headers",errors,"warning_review_required",warnings>0,"required_action",ACTION,"whole_tool_qualified",false,"paper_reproduction",false,"whole_goal_complete",false));
            }
        }catch(IOException error){code=2;admitted=false;cause="FAIL2: Could not write complete status/inventory: "+error;}
        (code==0?stdout:stderr).println(cause+(out==null?"":"\nOutput: "+out));stderr.println("WARNINGS: original headers="+warnings+", errors="+errors+"\n"+ACTION);return code;
    }
    private static byte[] resource(String name)throws IOException {try(var input=QuPath051WorkflowCli.class.getResourceAsStream(RESOURCE+name)){if(input==null)throw new Skipped("Missing packaged original runtime profile; use the standalone development JAR");return input.readNBytes(16777217);}}
    private static void same(Path path,NextflowFiles.Pin expected)throws IOException {require(NextflowFiles.pin(path,expected.bytes(),()->{}).equals(expected),"Complete original/captured file changed: "+path);}
    private static void verifyTree(Path root,Map<String,Object> expected)throws IOException {
        var actual=NextflowFiles.tree(root,20000,2147483648L,1073741824,()->{});require(actual.keySet().equals(expected.keySet()),"Complete original source/runtime file population differs: "+root);
        for(var entry:actual.entrySet())require(entry.getValue().kind().equals("file")&&entry.getValue().pin().equals(NextflowFiles.fingerprint(expected.get(entry.getKey()),1073741824)),"Original source/runtime member differs: "+entry.getKey());
    }
    private static void checkClasses(Path runtime,Set<String> members,List<String> required)throws IOException {
        var remaining=new HashSet<String>();for(String name:required)remaining.add(name.replace('.','/')+".class");
        for(String name:members) {
            if(remaining.isEmpty())break;if(!name.endsWith(".jar"))continue;
            try(var jar=new ZipFile(runtime.resolve(name).toFile())){remaining.removeIf(n->jar.getEntry(n)!=null);}
        }
        if(!remaining.isEmpty())throw new Skipped("Required original classes are missing: "+remaining+"; external extensions/models need separate permitted qualification");
    }
    private static int[] conditions(Path out,String expected)throws IOException {
        int warnings=0,errors=0,markers=0;
        try(var index=Files.newBufferedWriter(out.resolve("ORIGINAL_CONDITIONS.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            index.write("log\tline\tlevel\theader_json\n");
            for(String name:List.of("process.stdout","process.stderr")) {
                require(Files.size(out.resolve(name))<=67108864,"Complete original log exceeds64MiB diagnostic parser capacity; raw logs retained, no analysis admitted");long number=0;
                try(var reader=Files.newBufferedReader(out.resolve(name),StandardCharsets.UTF_8)) {
                    String line;while((line=reader.readLine())!=null){number++;var match=CONDITION.matcher(line);
                        if(match.matches()){String level=match.group(1);if(level.equals("WARN"))warnings++;else errors++;index.write(name+"\t"+number+"\t"+level+"\t"+NextflowJson.json(line)+"\n");}
                        if(line.contains("] qupath.ScriptCommand - Script result: ")&&line.endsWith("Script result: "+expected))markers++;
                    }
                }
            }
        }
        return new int[]{warnings,errors,markers};
    }
    private static int execute(List<String> command,Path out,Request request,String options)throws IOException,InterruptedException {
        capacity(out,request);var builder=new ProcessBuilder(command).directory(out.toFile()).redirectInput(ProcessBuilder.Redirect.PIPE).redirectOutput(out.resolve("process.stdout").toFile()).redirectError(out.resolve("process.stderr").toFile());
        for(String key:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH"))builder.environment().remove(key);builder.environment().put("JAVA_TOOL_OPTIONS",options);
        Process child=builder.start();child.getOutputStream().close();long start=System.nanoTime();var descendants=new LinkedHashMap<Long,ProcessHandle>();
        try {
            NextflowFiles.writeJson(out.resolve("process-live.json"),Map.of("pid",child.pid(),"command",command,"started",java.time.Instant.now().toString()));
            while(!child.waitFor(200,TimeUnit.MILLISECONDS)) {
                child.descendants().forEach(p->descendants.put(p.pid(),p));require(Duration.ofNanos(System.nanoTime()-start).toSeconds()<request.seconds(),"Original script wall timeout exceeded; no partial result admitted");capacity(out,request);
            }
            int code=child.exitValue();capacity(out,request);NextflowFiles.writeJson(out.resolve("process-exit.json"),Map.of("pid",child.pid(),"exit_code",code,"elapsed_millis",Duration.ofNanos(System.nanoTime()-start).toMillis(),"command",command));return code;
        }finally {
            child.descendants().forEach(p->descendants.put(p.pid(),p));for(ProcessHandle p:descendants.values())if(p.isAlive())p.destroy();if(child.isAlive())child.destroy();if(!child.waitFor(1500,TimeUnit.MILLISECONDS))child.destroyForcibly();for(ProcessHandle p:descendants.values())if(p.isAlive())p.destroyForcibly();if(child.isAlive())child.waitFor(1500,TimeUnit.MILLISECONDS);
            if(!child.isAlive()&&!Files.exists(out.resolve("process-exit.json")))NextflowFiles.writeJson(out.resolve("process-exit.json"),Map.of("pid",child.pid(),"exit_code",child.exitValue(),"elapsed_millis",Duration.ofNanos(System.nanoTime()-start).toMillis(),"command",command,"terminated_after_failure",true));
        }
    }
    private static void capacity(Path out,Request request)throws IOException {
        long bytes=0;int files=0;
        Path cache=out.resolve("owned-home/.javacpp/cache");
        try(var stream=Files.walk(out)){var paths=stream.iterator();while(paths.hasNext()){Path path=paths.next();require(++files<=request.files(),"Complete original workspace file/directory capacity exceeded");
            if(Files.isSymbolicLink(path)) {
                Path target=Files.readSymbolicLink(path);
                require(path.startsWith(cache)&&path.getParent().resolve(target).normalize().startsWith(cache)&&path.toRealPath().startsWith(cache),"Workspace link is outside the owned native cache or escapes its cache root: "+path);
            }
            if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){bytes=Math.addExact(bytes,Files.size(path));require(bytes<=request.outputBytes(),"Complete original workspace byte capacity exceeded");}}}
    }
}
