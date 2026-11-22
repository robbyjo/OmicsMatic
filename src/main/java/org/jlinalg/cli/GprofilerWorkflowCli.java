/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import org.jlinalg.gprofiler.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.GprofilerWorkflowFiles.*;

/** Global file RUN, offline VERIFY and offline REPLAY of original g:GOSt protocol. */
public final class GprofilerWorkflowCli {
    private GprofilerWorkflowCli() { }
    private static final long JSON_CAP=67108864;
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){return run(args,out,err,GprofilerGostClient::new,"live-original-api");}
    static int run(String[] args,PrintStream out,PrintStream err,
            Function<GprofilerGostClient.Settings,GprofilerGostClient> factory,String origin){
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){
                Path root=Path.of(args[1]).toAbsolutePath().normalize();verify(root,true);showWarnings(root,err);emit(out,"Verified original g:GOSt saved protocol offline: "+root);return 0;
            }
            require(args.length==2&&args[0].equals("--request"),"Require --request UTF8_JSON, --verify COMPLETE_BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=bytes(source,JSON_CAP);var raw=object(GprofilerJson.read(original));
            if("VERIFY".equals(raw.get("mode"))){control(raw,false);Path root=resolve(source.getParent(),text(raw.get("analysis"),"analysis"));verify(root,true);showWarnings(root,err);emit(out,"Verified g:GOSt protocol offline: "+root);return 0;}
            if("REPLAY".equals(raw.get("mode")))return replay(source,raw,original,out,err);
            require(Set.of("live-original-api","injected-fixture").contains(origin),"Unknown transport origin");
            var request=validate(raw);Path root=newOutput(resolve(source.getParent(),text(request.get("out"),"out")));
            return execute(root,request,original,factory,origin,out,err);
        }catch(Unavailable e){emit(err,"SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+e+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError e){emit(err,"FAIL2 — NO COMPLETE RESULT: Java heap exhausted; retain partial output, use suitable explicit heap/capacities and NEW output. "+ACTION);return 2;}
        catch(Exception e){emit(err,"FAIL2 — NO COMPLETE RESULT: "+e+"\n"+ACTION);return 2;}
    }
    private static int execute(Path root,Map<String,Object> request,byte[] original,
            Function<GprofilerGostClient.Settings,GprofilerGostClient> factory,String origin,PrintStream out,PrintStream err){
        try{
            var portable=portable(request);var execution=map("mode","RUN","transport_origin",origin,"captured_transport_origin",origin,"network_executed",origin.equals("live-original-api"));
            var output=new Outputs(root,new Budget(cap(request,"maximum_output_bytes"),0));
            output.bytes("source-request.json",original);output.json("request.json",portable);output.json("execution.json",execution);output.json("producer.json",producer());
            GprofilerWorkflowModel.Result result;
            try(var client=factory.apply(settings(portable,false))){result=GprofilerWorkflowModel.invoke(portable,client,new GprofilerWorkflowWire.Capture(output,origin));}
            var summary=GprofilerWorkflowModel.derive(portable,result,execution,output);
            seal(root,portable);showWarnings(root,err);complete(root,portable);
            emit(out,"Completed original g:GOSt "+request.get("operation")+": "+root+"; terms="+summary.get("reported_terms"));return 0;
        }catch(GprofilerGostClient.Failure e){return failure(root,e.suggestedExitCode(),e,err);}
        catch(Unavailable e){return failure(root,3,e,err);}
        catch(OutOfMemoryError e){return failure(root,2,new IOException("Java heap exhausted; no complete result",e),err);}
        catch(Exception e){return failure(root,2,e,err);}
    }
    private static int replay(Path source,Map<String,Object> raw,byte[] original,PrintStream out,PrintStream err)throws IOException{
        control(raw,true);Path parent=resolve(source.getParent(),text(raw.get("analysis"),"analysis")),target=resolve(source.getParent(),text(raw.get("out"),"out"));
        require(!target.startsWith(parent)&&!parent.startsWith(target),"Replay output must be NEW and separate from parent");
        verify(parent,true);Path realParent=parent.toRealPath(),realTarget=physicalNewPath(target);
        require(!realTarget.startsWith(realParent)&&!realParent.startsWith(realTarget),"Replay output aliases parent; choose a separate NEW destination");
        byte[] parentManifest=bytes(parent.resolve("manifest.json"),JSON_CAP);var manifest=object(GprofilerJson.read(parentManifest));var expected=object(manifest.get("files"));
        var request=read(parent.resolve("request.json"),JSON_CAP);var prior=execution(read(parent.resolve("execution.json"),JSON_CAP));
        Path root=newOutput(target);
        try{
            var output=new Outputs(root,new Budget(cap(request,"maximum_output_bytes"),0));
            for(String name:List.of("source-request.json","request.json"))copyExpected(parent,name,output,expected,JSON_CAP);
            output.bytes("source-control-request.json",original);output.json("control-request.json",raw);
            output.json("replay-parent.json",map("manifest",pin(parentManifest),"parent_execution",prior,"authentication",false));
            var execution=map("mode","REPLAY","transport_origin","saved-protocol","captured_transport_origin",prior.get("captured_transport_origin"),"network_executed",false);
            output.json("execution.json",execution);output.json("producer.json",producer());
            int calls="metadata".equals(request.get("operation"))?1:2;
            for(int i=1;i<=calls;i++)for(String suffix:List.of("-request.body","-response.body","-exchange.json"))copyExpected(parent,GprofilerWorkflowWire.prefix(i)+suffix,output,expected,
                suffix.equals("-exchange.json")?1048576:suffix.equals("-request.body")?cap(request,"maximum_request_bytes"):cap(request,"maximum_response_bytes"));
            verify(parent,true);require(same(pin(parentManifest),pin(parent.resolve("manifest.json"))),"Parent changed during replay capture");
            var wire=new GprofilerWorkflowWire.Replay(root,request,(String)prior.get("captured_transport_origin"));
            GprofilerWorkflowModel.Result result;
            try(var client=new GprofilerGostClient(settings(request,true),wire)){result=GprofilerWorkflowModel.invoke(request,client,null);wire.finish(calls);}
            GprofilerWorkflowModel.derive(request,result,execution,output);seal(root,request);showWarnings(root,err);complete(root,request);
            emit(out,"Replayed g:GOSt into NEW output offline: "+root+"; no network execution");return 0;
        }catch(OutOfMemoryError e){return failure(root,2,new IOException("Java heap exhausted during offline replay",e),err);}
        catch(Exception e){return failure(root,2,e,err);}
    }
    static void verify(Path root,boolean markerRequired)throws IOException{
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Restore the complete exact saved analysis: "+root,null);
        try{verifyExisting(root,markerRequired);}
        catch(Unavailable e){throw new IOException("Saved analysis is incomplete/unreadable; restore its original exact files",e);}
    }
    private static void verifyExisting(Path root,boolean markerRequired)throws IOException{
        require(!Files.exists(root.resolve("COMPLETED.pending"),LinkOption.NOFOLLOW_LINKS),"Unadmitted pending marker exists");
        var tree=inventory(root);byte[] manifestBytes=bytes(root.resolve("manifest.json"),JSON_CAP);var manifest=object(GprofilerJson.read(manifestBytes));
        require(manifest.keySet().equals(Set.of("schema_version","profile","files","directories"))&&integer(manifest.get("schema_version"),1,1)==1&&PROFILE.equals(manifest.get("profile")),"Changed manifest schema/profile");
        require(same(tree.files(),manifest.get("files"))&&same(tree.directories(),manifest.get("directories")),"Manifest differs from complete saved inventory");
        var source=read(root.resolve("source-request.json"),JSON_CAP);var request=portable(validate(source));
        require(Arrays.equals(json(request),bytes(root.resolve("request.json"),JSON_CAP)),"Portable request differs from original request and declared normalization");
        var produced=read(root.resolve("producer.json"),JSON_CAP);
        require(same(produced.get("selected_class_sha256"),producer().get("selected_class_sha256")),"Restore exact original producer classes; current workflow implementation differs");
        var execution=execution(read(root.resolve("execution.json"),JSON_CAP));boolean replay="REPLAY".equals(execution.get("mode"));
        if(replay){
            var c=read(root.resolve("control-request.json"),JSON_CAP);control(c,true);
            require(same(c,read(root.resolve("source-control-request.json"),JSON_CAP)),"Replay control differs from original control request");
            var p=read(root.resolve("replay-parent.json"),JSON_CAP);require(p.keySet().equals(Set.of("manifest","parent_execution","authentication"))&&Boolean.FALSE.equals(p.get("authentication")),"Changed parent schema/authentication claim");
            var identity=object(p.get("manifest"));require(identity.keySet().equals(Set.of("bytes","sha256"))&&number(identity,"bytes")>0&&text(identity.get("sha256"),"parent SHA").matches("[a-f0-9]{64}"),"Invalid parent manifest identity");
            var prior=execution(object(p.get("parent_execution")));require(Objects.equals(prior.get("captured_transport_origin"),execution.get("captured_transport_origin")),"Replay origin chain differs");
        }
        long total=Math.addExact(tree.bytes(),manifestBytes.length);
        if(markerRequired)total=Math.addExact(total,Files.size(root.resolve("COMPLETED.json")));
        require(total<=cap(request,"maximum_output_bytes"),"Complete saved bundle exceeds output byte capacity");
        var output=new Outputs(null,new Budget(cap(request,"maximum_output_bytes"),0));
        var wire=new GprofilerWorkflowWire.Replay(root,request,(String)execution.get("captured_transport_origin"));
        int calls="metadata".equals(request.get("operation"))?1:2;
        try(var client=new GprofilerGostClient(settings(request,true),wire)){
            var result=GprofilerWorkflowModel.invoke(request,client,null);wire.finish(calls);GprofilerWorkflowModel.derive(request,result,execution,output);
        }catch(GprofilerGostClient.Failure e){throw new IOException("Saved protocol cannot produce a complete admitted result: "+e,e);}
        var expected=new TreeSet<>(output.pins().keySet());expected.addAll(List.of("source-request.json","request.json","producer.json","execution.json"));
        for(int i=1;i<=calls;i++)for(String suffix:List.of("-request.body","-response.body","-exchange.json"))expected.add(GprofilerWorkflowWire.prefix(i)+suffix);
        if(replay)expected.addAll(List.of("source-control-request.json","control-request.json","replay-parent.json"));
        require(tree.files().keySet().equals(expected)&&tree.directories().equals(List.of("wire")),"Unexpected complete bundle file/directory family");
        for(var entry:output.pins().entrySet())require(same(tree.files().get(entry.getKey()),entry.getValue()),"Protocol/response-derived output differs: "+entry.getKey());
        if(markerRequired){var marker=read(root.resolve("COMPLETED.json"),1048576);
            require(marker.keySet().equals(Set.of("schema_version","profile","manifest_sha256","authentication"))&&integer(marker.get("schema_version"),1,1)==1&&PROFILE.equals(marker.get("profile"))
                &&Boolean.FALSE.equals(marker.get("authentication"))&&sha(manifestBytes).equals(marker.get("manifest_sha256")),"Invalid completion marker");}
    }
    private static Map<String,Object> execution(Map<String,Object> e)throws IOException{
        require(e.keySet().equals(Set.of("mode","transport_origin","captured_transport_origin","network_executed")),"Changed execution schema");
        String mode=choice(e,"mode",Set.of("RUN","REPLAY")),captured=choice(e,"captured_transport_origin",Set.of("live-original-api","injected-fixture"));
        boolean live=mode.equals("RUN")&&captured.equals("live-original-api");
        require(Objects.equals(e.get("network_executed"),live)&&Objects.equals(e.get("transport_origin"),mode.equals("RUN")?captured:"saved-protocol"),"Network/origin disposition differs");return e;
    }
    private static void control(Map<String,Object> raw,boolean replay)throws IOException{
        require(raw.keySet().equals(replay?Set.of("schema_version","mode","analysis","out"):Set.of("schema_version","mode","analysis")),"Changed control fields");
        require(integer(raw.get("schema_version"),1,1)==1&&(replay?"REPLAY":"VERIFY").equals(raw.get("mode")),"Invalid control schema/mode");text(raw.get("analysis"),"analysis");if(replay)text(raw.get("out"),"out");
    }
    private static void copyExpected(Path parent,String name,Outputs output,Map<String,Object> expected,long maximum)throws IOException{
        byte[] raw=bytes(parent.resolve(name),maximum);require(same(expected.get(name),pin(raw)),"Parent file differs from its manifest: "+name);output.bytes(name,raw);
    }
    private static byte[] marker(byte[] manifest)throws IOException{return json(map("schema_version",1,"profile",PROFILE,"manifest_sha256",sha(manifest),"authentication",false));}
    private static void seal(Path root,Map<String,Object> request)throws IOException{
        var tree=inventory(root);byte[] manifest=json(map("schema_version",1,"profile",PROFILE,"files",tree.files(),"directories",tree.directories()));
        require(tree.bytes()+manifest.length+marker(manifest).length<=cap(request,"maximum_output_bytes"),"Final inventory/marker exceeds combined output capacity");
        Files.write(root.resolve("manifest.json"),manifest,StandardOpenOption.CREATE_NEW);verify(root,false);
    }
    private static void complete(Path root,Map<String,Object> request)throws IOException{
        byte[] manifest=bytes(root.resolve("manifest.json"),JSON_CAP),marker=marker(manifest);
        require(inventory(root).bytes()+manifest.length+marker.length<=cap(request,"maximum_output_bytes"),"Completion capacity exceeded");
        require(!Files.exists(root.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS),"A completion marker already exists; preserve concurrent output");
        Files.write(root.resolve("COMPLETED.pending"),marker,StandardOpenOption.CREATE_NEW);
        Files.move(root.resolve("COMPLETED.pending"),root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
    }
    private static int failure(Path root,int code,Exception cause,PrintStream err){
        boolean interrupted=Thread.interrupted();String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_UNAVAILABLE":"FAIL2_NO_COMPLETE_RESULT";
        String privacy=recordedPrivacy(root);
        try{
            for(String name:List.of("analysis-status.json","WARNINGS.md","COMPLETED.pending"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("unadmitted-"+name));
            Files.write(root.resolve("analysis-status.json"),json(map("schema_version",1,"status",status,"result_admitted",false,"error",cause.toString())),StandardOpenOption.CREATE_NEW);
            String notice="# "+status+"\n\nNo complete result is admitted. Original calls may already have produced provisional work; inspect the retained wire trace.\n\n"+(privacy.isEmpty()?"":privacy+"\n\n")+"Cause: "+cause+"\n\n"+ACTION+"\n";
            Files.writeString(root.resolve("WARNINGS.md"),notice,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.write(root.resolve("FAILURE.json"),json(map("status",status,"exit_code",code,"result_admitted",false,"error",cause.toString())),StandardOpenOption.CREATE_NEW);
        }catch(Exception recording){emit(err,"Additional diagnostic recording failure: "+recording);}
        finally{if(interrupted)Thread.currentThread().interrupt();}
        if(!privacy.isEmpty())emit(err,privacy);
        emit(err,status+": "+cause+"\nWARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }
    private static String recordedPrivacy(Path root){
        if(!Files.isDirectory(root.resolve("wire")))return "";boolean partial=false;
        try(var entries=Files.list(root.resolve("wire"))){for(Path path:entries.filter(p->p.getFileName().toString().endsWith("-exchange.json")).toList())
            try{if(object(read(path,1048576).get("headers")).keySet().stream().anyMatch(k->k.equalsIgnoreCase("set-cookie")))return PRIVACY;}
            catch(IOException e){partial=true;}}
        catch(IOException e){partial=true;}
        return partial?"Wire-header diagnostics are partial/unreadable. Keep retained protocol private and review it before public sharing.":"";
    }
    private static void showWarnings(Path root,PrintStream err)throws IOException{
        emit(err,"WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md"));
        try(var reader=Files.newBufferedReader(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8)){
            String line;int count=0;while((line=reader.readLine())!=null&&count++<24)emit(err,line);
            if(line!=null)emit(err,"Console excerpt only; read all original warnings in WARNINGS.md and summary.json");
        }
    }
    private static void emit(PrintStream stream,String message){stream.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        Usage: gprofiler-tools --request UTF8_JSON | --verify COMPLETE_BUNDLE | --help
        schema_version=1; RUN, VERIFY or REPLAY. RUN declares NEW out,
        operation=enrichment|metadata, service={archive,required_build}, payload,
        and explicit allow_upload. Enrichment requires all 16 g:GOSt fields,
        allow_upload=true and a complete required build. Metadata accepts only
        payload.organism, allow_upload=false, and null build for discovery.
        Metadata preflight checks the exact build/organism before any gene upload.
        Policies mapping_changes/server_normalization/empty_results are warn|fail
        (default warn). Every original response, normalization and absence sentinel
        is retained. Resource limits bound time, bytes, identifiers and result rows.
        VERIFY: {schema_version:1,mode:VERIFY,analysis:PATH}.
        REPLAY additionally supplies a separate NEW out. Paths resolve against
        the request file; replay retains original protocol without network.
        """+SCOPE+"\n"+ACTION;}
}
