/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Ordered original Scanpy CPU RUN, structural/integrity VERIFY and original REPLAY. */
public final class ScanpyWorkflowCli {
    private ScanpyWorkflowCli() { }
    static final String ENGINE="original-Scanpy-CPU-ordered-workflow";
    static final String SCOPE="DEVELOPMENT_SCOPE: explicit current Scanpy1.13.0 CPU operations and original outputs. VERIFY checks source/request/capture consistency and exported numeric structure without Python; it does not independently refit models, parse all H5AD semantics, authenticate coordinated fabrications or establish historical author-runtime/whole-paper equivalence. REPLAY executes the original source again and needs its exact permitted dependencies; stochastic/host-dependent byte equality is not promised.";
    static final String ACTION="Read WARNINGS.md, analysis-status.json, worker/report.json, worker/status.json and logs. SKIP3 means dependent analysis was skipped: restore the named exact permitted Python/package dependency, or leave it skipped. FAIL2 admits no complete result: preserve inputs, diagnostics and partials, correct the named input/source/numerical/process/capacity cause and retry NEW output. No silent ID, layer, dtype, nonfinite, inequality, population or algorithm repair. "+SCOPE;
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,"ScanpyWorkflowCli --request UTF8_JSON | --verify COMPLETE_BUNDLE\nRUN: explicit ordered original CPU operations from MatrixMarket/typed metadata or supplied H5AD.\nVERIFY: Python-free source, capture and exported structure checks. REPLAY: verify and run original models into NEW output.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));emit(out,"Verified saved Scanpy capture and exported structure offline. "+SCOPE);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=bytes(source,2000000);var raw=parse(original);
            String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){control(raw,false);verify(resolve(source.getParent(),text(raw,"analysis")));emit(out,"Verified saved Scanpy capture offline. "+SCOPE);return 0;}
            if(mode.equals("REPLAY")) {
                control(raw,true);Path prior=resolve(source.getParent(),text(raw,"analysis"));verify(prior);
                var request=read(prior.resolve("request.json"));var input=object(request.get("input"),"input");
                for(String key:ScanpyWorkflowContracts.INPUT_FILES)if(input.containsKey(key))input.put(key,resolve(prior,text(input,key)).toString());request.put("input",input);
                var binding=read(prior.resolve("worker/runtime-binding.json"));request.put("python",raw.containsKey("python")?resolve(source.getParent(),text(raw,"python")).toString():text(binding,"python"));
                request.put("out",resolve(source.getParent(),text(raw,"out")).toString());
                return execute(source.getParent(),request,RsemJson.bytes(request),Map.of("analysis",prior.toString(),"seal_sha256",FollowupSupport.hash(prior.resolve("capture-files.json"))),out,err);
            }
            return execute(source.getParent(),raw,original,Map.of(),out,err);
        }catch(ScanpyWorkflowRuntime.Unavailable e){emit(err,"SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+e+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError e){emit(err,"FAIL2 — NO COMPLETE RESULT: Java heap exhausted; retain diagnostics and choose appropriate explicit heap/capacities and NEW output. "+ACTION);return 2;}
        catch(Exception e){emit(err,"FAIL2 — NO COMPLETE RESULT: "+e+"\n"+ACTION);return 2;}
    }
    private static void control(Map<String,Object> raw,boolean replay)throws IOException {
        keys(raw,replay?Set.of("schema_version","mode","analysis","out","python"):Set.of("schema_version","mode","analysis"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version 1");text(raw,"analysis");if(replay)text(raw,"out");
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err)throws IOException {
        var request=ScanpyWorkflowContracts.validate(raw);Path root=newOutput(resolve(parent,text(request,"out")));
        var manifest=new LinkedHashMap<String,Object>();manifest.put("schema_version",1);manifest.put("engine",ENGINE);manifest.put("profile",ScanpyWorkflowContracts.PROFILE);
        manifest.put("scope",SCOPE);manifest.put("source_request_sha256",ScanpyWorkflowRuntimeHash.sha(original));
        int code=2;String error=null;
        json(root.resolve("analysis-status.json"),state("RUNNING_NO_RESULT_ADMITTED",false,null));
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);if(!replay.isEmpty())json(root.resolve("replay-source.json"),replay);
            Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));
            var captured=new LinkedHashMap<String,Object>(raw);captured.put("out",".");var input=object(raw.get("input"),"input");
            long remaining=((Number)request.get("maximum_input_bytes")).longValue();var pins=new TreeMap<String,Object>();
            for(String key:ScanpyWorkflowContracts.INPUT_FILES)if(input.containsKey(key)) {
                Path target=root.resolve("inputs/"+key);remaining-=capture(resolve(parent,text(input,key)),target,remaining);
                pins.put("inputs/"+key,FollowupSupport.hash(target));input.put(key,"inputs/"+key);
            }
            captured.put("input",input);json(root.resolve("request.json"),captured);var effective=ScanpyWorkflowContracts.validate(captured);json(root.resolve("effective-request.json"),effective);
            manifest.put("captured_input_sha256",pins);ScanpyWorkflowRuntime.sources(root,false);ScanpyWorkflowRuntime.run(root,parent,request,manifest);
            for(var e:pins.entrySet())if(!FollowupSupport.hash(root.resolve(e.getKey())).equals(e.getValue()))throw new IOException("Captured input changed during original execution");
            ScanpyWorkflowRuntime.sources(root,true);ScanpyWorkflowAudit.outputs(root,effective);code=0;
        }catch(ScanpyWorkflowRuntime.Unavailable e){code=3;error=e.toString();}
        catch(Exception e){error=e.toString();if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
        catch(OutOfMemoryError e){error="Java heap exhausted; choose appropriate explicit heap/capacities and NEW output";}
        String status=code==0?"completed-with-warnings":code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        manifest.put("analysis_status",status);manifest.put("results_admitted",code==0);if(error!=null)manifest.put("error",error);
        json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state(status,code==0,error));
        Files.writeString(root.resolve("WARNINGS.md"),warningText(status,error),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try {seal(root);if(code==0)verify(root);}
        catch(IOException e) {
            code=2;manifest.put("analysis_status","FAIL2_NO_COMPLETE_RESULT");manifest.put("results_admitted",false);manifest.put("verification_error",e.toString());
            json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state("FAIL2_NO_COMPLETE_RESULT",false,e.toString()));
            Files.writeString(root.resolve("WARNINGS.md"),warningText("FAIL2_NO_COMPLETE_RESULT",e.toString()),StandardCharsets.UTF_8);seal(root);
        }
        emit(code==0?out:err,(code==0?"Completed original Scanpy workflow: ":code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO COMPLETE RESULT: ")+root);
        emit(err,"WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }
    static void verify(Path root)throws IOException {
        root=root.toAbsolutePath().normalize();var seal=read(root.resolve("capture-files.json"),16000000);
        keys(seal,Set.of("schema_version","authentication","files"));if(integer(seal,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(seal.get("authentication")))throw new IOException("Invalid capture seal or false authentication claim");
        var expected=object(seal.get("files"),"pins");var actual=ScanpyWorkflowRuntime.footprint(root);actual.remove("capture-files.json");if(!actual.keySet().equals(expected.keySet()))throw new IOException("Capture file population changed");
        for(var e:expected.entrySet())if(!FollowupSupport.hash(safe(root,e.getKey())).equals(e.getValue()))throw new IOException("Capture hash differs: "+e.getKey());
        var manifest=read(root.resolve("manifest.json"));var state=read(root.resolve("analysis-status.json"));
        if(!ENGINE.equals(manifest.get("engine"))||!ScanpyWorkflowContracts.PROFILE.equals(manifest.get("profile"))||!SCOPE.equals(manifest.get("scope"))||!"completed-with-warnings".equals(manifest.get("analysis_status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted"))||!ACTION.equals(state.get("required_action")))throw new IOException("Failed/skipped/incomplete captures are not admitted results");
        var original=read(root.resolve("source-request.json"));ScanpyWorkflowContracts.validate(original);if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Original request hash differs");
        var normalized=new LinkedHashMap<String,Object>(original);normalized.put("out",".");var input=object(normalized.get("input"),"input");
        var inputPins=object(manifest.get("captured_input_sha256"),"input pins");Set<String> names=new HashSet<>();
        for(String key:ScanpyWorkflowContracts.INPUT_FILES)if(input.containsKey(key)){String name="inputs/"+key;input.put(key,name);names.add(name);if(!FollowupSupport.hash(root.resolve(name)).equals(inputPins.get(name)))throw new IOException("Captured input pin differs");}
        normalized.put("input",input);if(!names.equals(inputPins.keySet()))throw new IOException("Captured input population differs");
        var request=read(root.resolve("request.json"));if(!same(request,normalized))throw new IOException("Captured request differs from exact source settings");
        var effective=ScanpyWorkflowContracts.validate(request);if(!same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective request differs");
        ScanpyWorkflowRuntime.sources(root,true);ScanpyWorkflowAudit.outputs(root,effective);
        if(integer(manifest,"process_exit_code",-1,0,0)!=0||!text(manifest,"python_sha256").matches("[0-9a-f]{64}"))throw new IOException("Original process identity/exit is incomplete");
        var binding=read(root.resolve("worker/runtime-binding.json"));if(!ScanpyWorkflowContracts.PROFILE.equals(binding.get("profile"))||!same(List.of(text(binding,"python"),"-X","utf8","-I","-B","../source/worker.py",".."),manifest.get("command")))throw new IOException("Recorded command/binding differs");
        if(!warningText("completed-with-warnings",null).equals(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8)))throw new IOException("Warnings/recovery changed");
    }
    static void seal(Path root)throws IOException {
        Map<String,Object> files=new TreeMap<>();for(String name:ScanpyWorkflowRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))files.put(name,FollowupSupport.hash(safe(root,name)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",files));
    }
    static Path safe(Path root,String name)throws IOException {Path relative=Path.of(name);if(relative.isAbsolute()||!relative.normalize().equals(relative)||name.contains("\\")||name.contains(".."))throw new IOException("Unsafe capture path");return root.resolve(relative);}
    private static Map<String,Object> state(String status,boolean admitted,String error){var result=new LinkedHashMap<String,Object>();result.put("status",status);result.put("results_admitted",admitted);result.put("required_action",ACTION);if(error!=null)result.put("error",error);return result;}
    private static String warningText(String status,String error){return "# "+status+"\n\n"+(error==null?"Original numeric/library warnings and step settings are retained in worker/report.json; inspect them before downstream use.":error)+"\n\n"+ACTION+"\n";}
    private static Path newOutput(Path path)throws IOException {if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; preserve it and use NEW output");Files.createDirectories(path.getParent());return Files.createDirectory(path);}
    private static byte[] bytes(Path path,long cap)throws IOException {if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>cap)throw new IOException("Require bounded regular request");byte[] value=Files.readAllBytes(path);if(value.length>cap)throw new IOException("Request grew beyond capacity");return value;}
    private static Map<String,Object> parse(byte[] value)throws IOException {return object(SimpleJson.parse(new String(value,StandardCharsets.UTF_8)),"request");}
    private static long capture(Path source,Path target,long cap)throws IOException {
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new ScanpyWorkflowRuntime.Unavailable("Input unavailable/nonregular: "+source);
        if(Files.size(source)>cap)throw new IOException("Complete input exceeds maximum_input_bytes");String before=FollowupSupport.hash(source);long size=0;
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>=0){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture interrupted");if(n>cap-size)throw new IOException("Input grew beyond capacity");out.write(buffer,0,n);size+=n;}}
        if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(target)))throw new IOException("Input changed while capturing");return size;
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
}
