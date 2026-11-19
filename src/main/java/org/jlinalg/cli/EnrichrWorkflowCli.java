/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import org.jlinalg.enrichr.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** File-oriented current Enrichr RUN, offline VERIFY and offline REPLAY. */
public final class EnrichrWorkflowCli {
    private EnrichrWorkflowCli() { }
    private static final long JSON_CAP=2_000_000;
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){return run(args,out,err,EnrichrClient::new,"live-current-api");}
    static int run(String[] args,PrintStream out,PrintStream err,Function<EnrichrClient.Settings,EnrichrClient> factory,String origin){
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){
                Path root=Path.of(args[1]).toAbsolutePath().normalize();verify(root,true);showWarnings(root,err);emit(out,"Verified Enrichr complete saved protocol and all derived outputs offline: "+root);return 0;
            }
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_JSON, --verify COMPLETE_BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=EnrichrWorkflowFiles.bytes(source,JSON_CAP);
            Map<String,Object> raw=parse(original);
            String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){
                control(raw,false);Path root=resolve(source.getParent(),text(raw,"analysis"));verify(root,true);showWarnings(root,err);emit(out,"Verified Enrichr saved protocol offline: "+root);return 0;
            }
            if(mode.equals("REPLAY"))return replay(source,raw,original,out,err);
            if(!Set.of("live-current-api","injected-fixture").contains(origin))throw new IOException("Unknown explicit transport origin");
            return execute(source.getParent(),raw,original,factory,origin,out,err);
        }catch(EnrichrWorkflowFiles.Unavailable e){emit(err,"SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+e+"\n"+EnrichrWorkflowContracts.ACTION);return 3;}
        catch(OutOfMemoryError e){emit(err,"FAIL2 — NO COMPLETE RESULT: Java heap exhausted. Preserve partial outputs, choose suitable explicit heap/capacities and NEW output. "+EnrichrWorkflowContracts.ACTION);return 2;}
        catch(Exception e){emit(err,"FAIL2 — NO COMPLETE RESULT: "+e+"\n"+EnrichrWorkflowContracts.ACTION);return 2;}
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Function<EnrichrClient.Settings,EnrichrClient> factory,
            String origin,PrintStream out,PrintStream err)throws IOException{
        Map<String,Object> request=EnrichrWorkflowContracts.validate(raw);Path root=newOutput(resolve(parent,text(request,"out")));
        try{
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);
            Map<String,Object> portable=EnrichrWorkflowContracts.portable(request);
            EnrichrWorkflowFiles.json(root.resolve("request.json"),portable);
            var execution=Map.<String,Object>of("mode","RUN","transport_origin",origin,"captured_transport_origin",origin,"network_executed",origin.equals("live-current-api"));
            EnrichrWorkflowFiles.json(root.resolve("execution.json"),execution);EnrichrWorkflowFiles.json(root.resolve("producer.json"),EnrichrWorkflowFiles.producer());
            captureInputs(parent,request,root);
            long cap=outputCap(request);var budget=new EnrichrWorkflowFiles.Budget(cap,EnrichrWorkflowFiles.inventory(root).bytes());
            var generated=new EnrichrWorkflowFiles.Generated(root,budget);var wire=new EnrichrWorkflowModel.WireWriter(generated,origin);
            var result=EnrichrWorkflowModel.derive(root,portable,execution,factory.apply(EnrichrWorkflowContracts.settings(portable,false)),wire,generated,false);
            seal(root,portable);showWarnings(root,err);complete(root,portable);
            emit(out,"Completed Enrichr "+portable.get("operation")+": "+root+"; libraries="+result.libraries()+"; reported terms="+result.terms());return 0;
        }catch(EnrichrWorkflowFiles.Unavailable e){return failure(root,3,e,err);}
        catch(EnrichrClient.Failure e){return failure(root,e.suggestedExitCode(),e,err);}
        catch(OutOfMemoryError e){return failure(root,2,new IOException("Java heap exhausted; no complete result",e),err);}
        catch(Exception e){return failure(root,2,e,err);}
    }
    private static void captureInputs(Path parent,Map<String,Object> request,Path root)throws IOException{
        if(!request.containsKey("query"))return;
        Files.createDirectory(root.resolve("inputs"));long remaining=EnrichrWorkflowContracts.number(object(request.get("limits"),"limits"),"maximum_input_bytes");
        remaining-=EnrichrWorkflowFiles.capture(resolve(parent,text(object(request.get("query"),"query"),"file")),root.resolve("inputs/query.txt"),remaining);
        var bg=object(request.get("background"),"background");
        if(bg.get("kind").equals("explicit"))EnrichrWorkflowFiles.capture(resolve(parent,text(bg,"file")),root.resolve("inputs/background.txt"),remaining);
    }
    private static int replay(Path source,Map<String,Object> raw,byte[] original,PrintStream out,PrintStream err)throws IOException{
        control(raw,true);Path parent=resolve(source.getParent(),text(raw,"analysis"));Path target=resolve(source.getParent(),text(raw,"out"));
        if(target.startsWith(parent)||parent.startsWith(target))throw new IOException("Replay output must be NEW and separate from the parent bundle");
        verify(parent,true);
        Path realParent=parent.toRealPath(),realTarget=physicalNewPath(target);
        if(realTarget.startsWith(realParent)||realParent.startsWith(realTarget))throw new IOException("Replay output aliases the parent bundle; choose a separate NEW destination");
        byte[] parentManifest=EnrichrWorkflowFiles.bytes(parent.resolve("manifest.json"),JSON_CAP);var expected=object(parse(parentManifest).get("files"),"parent files");
        Map<String,Object> request=parse(parentBytes(parent,"request.json",expected,JSON_CAP));
        Map<String,Object> parentExecution=execution(parse(parentBytes(parent,"execution.json",expected,JSON_CAP)));
        Path root=newOutput(target);
        try{
            for(String name:List.of("source-request.json","request.json"))copyExpected(parent,name,root.resolve(name),expected,outputCap(request));
            EnrichrWorkflowFiles.json(root.resolve("control-request.json"),raw);
            Files.write(root.resolve("source-control-request.json"),original,StandardOpenOption.CREATE_NEW);
            var run=Map.<String,Object>of("mode","REPLAY","transport_origin","saved-protocol","captured_transport_origin",parentExecution.get("captured_transport_origin"),"network_executed",false);
            EnrichrWorkflowFiles.json(root.resolve("execution.json"),run);EnrichrWorkflowFiles.json(root.resolve("producer.json"),EnrichrWorkflowFiles.producer());
            EnrichrWorkflowFiles.json(root.resolve("replay-parent.json"),Map.of("manifest",EnrichrWorkflowFiles.pin(parentManifest),"parent_execution",parentExecution,"authentication",false));
            for(String folder:List.of("inputs","wire")){
                Path old=parent.resolve(folder);if(!Files.exists(old,LinkOption.NOFOLLOW_LINKS))continue;
                Files.createDirectory(root.resolve(folder));
                try(var list=Files.list(old)){for(Path file:list.toList())copyExpected(parent,folder+"/"+file.getFileName(),root.resolve(folder).resolve(file.getFileName()),expected,outputCap(request));}
            }
            verify(parent,true);
            if(!same(EnrichrWorkflowFiles.pin(parentManifest),EnrichrWorkflowFiles.pin(parent.resolve("manifest.json"))))throw new IOException("Parent changed during replay capture");
            var replay=new EnrichrWorkflowModel.WireReplay(root,wireCap(request),(String)run.get("captured_transport_origin"));
            var budget=new EnrichrWorkflowFiles.Budget(outputCap(request),EnrichrWorkflowFiles.inventory(root).bytes());
            var generated=new EnrichrWorkflowFiles.Generated(root,budget);
            var result=EnrichrWorkflowModel.derive(root,request,run,new EnrichrClient(EnrichrWorkflowContracts.settings(request,true),replay),replay,generated,true);replay.finish();
            seal(root,request);showWarnings(root,err);complete(root,request);
            emit(out,"Replayed Enrichr offline into NEW bundle: "+root+"; no network execution; reported terms="+result.terms());return 0;
        }catch(OutOfMemoryError e){return failure(root,2,new IOException("Java heap exhausted during offline replay",e),err);}
        catch(Exception e){return failure(root,e instanceof EnrichrWorkflowFiles.Unavailable?3:2,e,err);}
    }
    static void verify(Path root,boolean requireMarker)throws IOException{
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new EnrichrWorkflowFiles.Unavailable("Restore exact complete bundle: "+root,null);
        if(Files.isSymbolicLink(root)||!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular complete bundle directory");
        if(requireMarker&&!Files.isRegularFile(root.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Bundle is not admitted complete; missing completion marker");
        var manifest=EnrichrWorkflowFiles.read(root.resolve("manifest.json"),JSON_CAP);keys(manifest,Set.of("schema_version","profile","files","directories"));
        if(!Long.valueOf(1).equals(manifest.get("schema_version"))||!EnrichrWorkflowContracts.PROFILE.equals(manifest.get("profile")))throw new IOException("Manifest profile differs");
        var current=EnrichrWorkflowFiles.inventory(root);
        if(!same(current.files(),manifest.get("files"))||!same(current.directories(),manifest.get("directories")))throw new IOException("Complete file/directory/hash inventory differs");
        var request=EnrichrWorkflowFiles.read(root.resolve("request.json"),JSON_CAP);
        var normalized=EnrichrWorkflowContracts.portable(EnrichrWorkflowContracts.validate(request));
        if(!same(request,normalized))throw new IOException("Portable normalized request differs");
        var original=EnrichrWorkflowContracts.portable(EnrichrWorkflowContracts.validate(EnrichrWorkflowFiles.read(root.resolve("source-request.json"),JSON_CAP)));
        if(!same(request,original))throw new IOException("Original source request recipe differs");
        var producer=EnrichrWorkflowFiles.read(root.resolve("producer.json"),JSON_CAP);
        if(!same(producer.get("selected_class_sha256"),EnrichrWorkflowFiles.producer().get("selected_class_sha256")))throw new IOException("Restore exact original producer classes; current implementation differs");
        var run=execution(EnrichrWorkflowFiles.read(root.resolve("execution.json"),JSON_CAP));
        boolean replayMode=run.get("mode").equals("REPLAY");
        if(replayMode){
            var control=EnrichrWorkflowFiles.read(root.resolve("control-request.json"),JSON_CAP);control(control,true);
            if(!same(control,EnrichrWorkflowFiles.read(root.resolve("source-control-request.json"),JSON_CAP)))throw new IOException("Replay control differs from original control request");
            var parent=EnrichrWorkflowFiles.read(root.resolve("replay-parent.json"),JSON_CAP);keys(parent,Set.of("manifest","parent_execution","authentication"));
            var p=object(parent.get("manifest"),"parent manifest identity");keys(p,Set.of("bytes","sha256"));
            if(!(p.get("bytes") instanceof Number n)||n.longValue()<1||!text(p,"sha256").matches("[0-9a-f]{64}")||!Boolean.FALSE.equals(parent.get("authentication")))throw new IOException("Invalid parent identity");
            var previous=execution(object(parent.get("parent_execution"),"parent execution"));
            if(!Objects.equals(previous.get("captured_transport_origin"),run.get("captured_transport_origin")))throw new IOException("Replay origin chain differs");
        }
        long total=current.bytes()+Files.size(root.resolve("manifest.json"))+(requireMarker?Files.size(root.resolve("COMPLETED.json")):0);
        if(total>outputCap(request))throw new IOException("Complete bundle exceeds declared combined output byte capacity");
        var transport=new EnrichrWorkflowModel.WireReplay(root,wireCap(request),(String)run.get("captured_transport_origin"));
        var generated=new EnrichrWorkflowFiles.Generated(null,new EnrichrWorkflowFiles.Budget(outputCap(request),0));
        try{EnrichrWorkflowModel.derive(root,request,run,new EnrichrClient(EnrichrWorkflowContracts.settings(request,true),transport),transport,generated,true);transport.finish();}
        catch(EnrichrClient.Failure invalid){throw new IOException("Saved complete protocol cannot produce an admitted result: "+invalid,invalid);}
        var expected=new TreeSet<>(generated.pins().keySet());
        expected.addAll(List.of("source-request.json","request.json","producer.json","execution.json"));
        if(request.containsKey("query")){expected.add("inputs/query.txt");if(object(request.get("background"),"background").get("kind").equals("explicit"))expected.add("inputs/background.txt");}
        if(replayMode)expected.addAll(List.of("control-request.json","source-control-request.json","replay-parent.json"));
        for(String name:current.files().keySet())if(name.startsWith("wire/"))expected.add(name);
        if(!current.files().keySet().equals(expected))throw new IOException("Unexpected complete-bundle file family");
        var directories=new TreeSet<String>();for(String file:expected){int at=file.lastIndexOf('/');while(at>=0){directories.add(file.substring(0,at));at=file.lastIndexOf('/',at-1);}}
        if(!new TreeSet<>(current.directories()).equals(directories))throw new IOException("Unexpected bundle directories");
        for(var entry:generated.pins().entrySet())if(!same(current.files().get(entry.getKey()),entry.getValue()))throw new IOException("Scientific/protocol-derived output differs: "+entry.getKey());
        if(requireMarker){
            var marker=EnrichrWorkflowFiles.read(root.resolve("COMPLETED.json"),JSON_CAP);keys(marker,Set.of("schema_version","profile","manifest_sha256","authentication"));
            if(!Long.valueOf(1).equals(marker.get("schema_version"))||!EnrichrWorkflowContracts.PROFILE.equals(marker.get("profile"))||!Boolean.FALSE.equals(marker.get("authentication"))
                    ||!((Map<?,?>)EnrichrWorkflowFiles.pin(root.resolve("manifest.json"))).get("sha256").equals(marker.get("manifest_sha256")))throw new IOException("Completion marker differs");
        }
    }
    private static Map<String,Object> execution(Map<String,Object> run)throws IOException{
        keys(run,Set.of("mode","transport_origin","captured_transport_origin","network_executed"));
        String mode=choice(run,"mode","",Set.of("RUN","REPLAY"));
        String captured=choice(run,"captured_transport_origin","",Set.of("live-current-api","injected-fixture"));
        String origin=text(run,"transport_origin");boolean live=mode.equals("RUN")&&captured.equals("live-current-api");
        if(!Objects.equals(run.get("network_executed"),live)||!origin.equals(mode.equals("RUN")?captured:"saved-protocol"))throw new IOException("Execution/network origin disposition differs");
        return run;
    }
    private static void seal(Path root,Map<String,Object> request)throws IOException{
        var tree=EnrichrWorkflowFiles.inventory(root);
        byte[] manifest=EnrichrWorkflowFiles.json(Map.of("schema_version",1,"profile",EnrichrWorkflowContracts.PROFILE,"files",tree.files(),"directories",tree.directories()));
        byte[] marker=marker(manifest);
        if(tree.bytes()+manifest.length+marker.length>outputCap(request))throw new IOException("Final inventory/marker exceeds combined output capacity; no result admitted");
        Files.write(root.resolve("manifest.json"),manifest,StandardOpenOption.CREATE_NEW);verify(root,false);
    }
    private static byte[] marker(byte[] manifest)throws IOException{return EnrichrWorkflowFiles.json(Map.of("schema_version",1,"profile",EnrichrWorkflowContracts.PROFILE,"manifest_sha256",EnrichrWorkflowFiles.sha(manifest),"authentication",false));}
    private static void complete(Path root,Map<String,Object> request)throws IOException{
        byte[] bytes=marker(EnrichrWorkflowFiles.bytes(root.resolve("manifest.json"),JSON_CAP));
        if(EnrichrWorkflowFiles.inventory(root).bytes()+Files.size(root.resolve("manifest.json"))+bytes.length>outputCap(request))throw new IOException("Completion capacity exceeded");
        Files.write(root.resolve("COMPLETED.pending"),bytes,StandardOpenOption.CREATE_NEW);Files.move(root.resolve("COMPLETED.pending"),root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
    }
    private static Path newOutput(Path root)throws IOException{
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists; preserve it and use a NEW directory: "+root);
        root=physicalNewPath(root);
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output aliases an existing destination; use a NEW directory");
        Files.createDirectories(root.getParent());Files.createDirectory(root);return root;
    }
    private static Path physicalNewPath(Path value)throws IOException{
        Path existing=value;var missing=new ArrayDeque<Path>();
        while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)){missing.push(existing.getFileName());existing=existing.getParent();if(existing==null)throw new IOException("No existing output ancestor");}
        Path resolved=existing.toRealPath();while(!missing.isEmpty())resolved=resolved.resolve(missing.pop());return resolved.normalize();
    }
    private static byte[] parentBytes(Path parent,String name,Map<String,Object> expected,long cap)throws IOException{
        byte[] raw=EnrichrWorkflowFiles.bytes(parent.resolve(name),cap);
        if(!same(expected.get(name),EnrichrWorkflowFiles.pin(raw)))throw new IOException("Parent source differs from its saved manifest: "+name);return raw;
    }
    private static void copyExpected(Path parent,String name,Path target,Map<String,Object> expected,long cap)throws IOException{
        EnrichrWorkflowFiles.capture(parent.resolve(name),target,cap);
        if(!same(expected.get(name),EnrichrWorkflowFiles.pin(target)))throw new IOException("Replay copy differs from exact parent manifest: "+name);
    }
    private static long outputCap(Map<String,Object> request)throws IOException{return EnrichrWorkflowContracts.number(object(request.get("limits"),"limits"),"maximum_output_bytes");}
    private static long wireCap(Map<String,Object> request)throws IOException{var l=object(request.get("limits"),"limits");return Math.max(EnrichrWorkflowContracts.number(l,"maximum_request_bytes"),EnrichrWorkflowContracts.number(l,"maximum_response_bytes"));}
    private static Map<String,Object> parse(byte[] raw)throws IOException{return object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString()),"request");}
    private static void control(Map<String,Object> raw,boolean replay)throws IOException{
        keys(raw,replay?Set.of("schema_version","mode","analysis","out"):Set.of("schema_version","mode","analysis"));
        if(!Long.valueOf(1).equals(raw.get("schema_version"))||!(replay?"REPLAY":"VERIFY").equals(raw.get("mode")))throw new IOException("Require exact control schema/mode");
        text(raw,"analysis");if(replay)text(raw,"out");
    }
    private static int failure(Path root,int code,Exception error,PrintStream err){
        boolean interrupted=Thread.interrupted();String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        try{
            for(String name:List.of("analysis-status.json","WARNINGS.md","COMPLETED.pending"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("unadmitted-"+name));
            Files.write(root.resolve("analysis-status.json"),EnrichrWorkflowFiles.json(Map.of("schema_version",1,"status",status,"result_admitted",false,"error",error.toString())),StandardOpenOption.CREATE_NEW);
            String notice="# "+status+"\n\nNo complete result is admitted. Inputs, raw replies and any capacity-limited prefixes/partial tables are retained.\n\nCause: "+error+"\n\nRequired actions: "+EnrichrWorkflowContracts.ACTION+"\n";
            Files.writeString(root.resolve("WARNINGS.md"),notice,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            EnrichrWorkflowFiles.json(root.resolve("FAILURE.json"),Map.of("status",status,"exit_code",code,"error",error.toString(),"result_admitted",false));
        }catch(Exception record){emit(err,"Additional failure while saving diagnostics: "+record);}
        finally{if(interrupted)Thread.currentThread().interrupt();}
        emit(err,status+": "+root+"\n"+error+"\nWARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+EnrichrWorkflowContracts.ACTION);return code;
    }
    private static void showWarnings(Path root,PrintStream err)throws IOException{
        emit(err,"WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md"));
        var state=EnrichrWorkflowFiles.read(root.resolve("analysis-status.json"),JSON_CAP);var flags=object(state.get("scientific_flags"),"scientific_flags");
        if(Boolean.TRUE.equals(flags.get("custom_normalization_unobserved")))emit(err,"WARNING: Custom-background effective populations and complete server normalization are unobserved; inspect the submitted-ID ledgers and recorded acknowledgement.");
        if(Boolean.TRUE.equals(flags.get("server_echo_identity_changed")))emit(err,"WARNING: Server query identity/multiplicity changed; both populations are retained. No automatic identifier repair was performed.");
        if(((Number)flags.get("zero_p_value_terms")).longValue()>0)emit(err,"WARNING: Upstream P=0 retained for "+flags.get("zero_p_value_terms")+" terms; no floor, exact nonzero tail unavailable.");
        if(((Number)flags.get("positive_infinite_score_terms")).longValue()>0)emit(err,"WARNING: Positive infinite source scores retained for "+flags.get("positive_infinite_score_terms")+" terms; no cap or replacement.");
        if(((Number)flags.get("out_of_literal_input_hit_entries")).longValue()>0)emit(err,"WARNING: Returned hits outside literal submitted input occur; inspect hits.tsv and WARNINGS.md before interpretation.");
        try(var reader=Files.newBufferedReader(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8)){
            int lines=0;String line;while((line=reader.readLine())!=null&&lines<24){emit(err,line);lines++;}
            if(line!=null)emit(err,"Console excerpt only; all warnings remain in WARNINGS.md and summary.json.");
        }
        emit(err,EnrichrWorkflowContracts.ACTION);
    }
    private static void emit(PrintStream stream,String message){stream.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        Usage: enrichr-tools --request UTF8_JSON | --verify COMPLETE_BUNDLE | --help
        schema_version 1; modes RUN, VERIFY, REPLAY. RUN declares NEW out,
        service_profile=enrichr-current-human-mouse-workflow-v1, operation
        enrichment|library-download and 1..32 exact libraries. Enrichment requires
        allow_upload=true, query={file,format:lines}, and explicit background:
        {kind:server-default} or {kind:explicit,file,format:lines}. Custom mode also
        requires allow_unobserved_custom_normalization=true. Library download
        requires allow_upload=false and no gene inputs. UTF-8 inputs have one
        prepared literal unweighted symbol per row; no header/ID guessing.
        Policies identity_changes (default fail), empty_results/infinite_scores
        (default warn) are warn|fail. See documentation for explicit resource limits.
        VERIFY supplies analysis; REPLAY supplies analysis and a separate NEW out.
        Paths resolve against the request file. Verification/replay are offline.
        """+EnrichrWorkflowContracts.SCOPE+"\n"+EnrichrWorkflowContracts.ACTION;}
}
