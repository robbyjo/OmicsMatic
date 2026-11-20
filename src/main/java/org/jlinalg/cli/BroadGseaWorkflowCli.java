/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.BroadGseaRecipe.*;
import static org.jlinalg.cli.BroadGseaFiles.*;

/** Native prepared-data RUN, semantic VERIFY and parent-preserving REPLAY. */
public final class BroadGseaWorkflowCli {
    private BroadGseaWorkflowCli(){ }
    private record Parent(Path root,Run request,Map<String,Object> producer,Map<String,Object> science,Map<String,Object> manifestPin){ }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(args.length==1&&args[0].equals("--help")){
                out.println("BroadGseaWorkflowCli --request RECIPE.json\nModes: RUN, VERIFY, REPLAY. All require NEW out and explicit limits. VERIFY/REPLAY additionally require analysis.\n"+SCOPE+"\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--worker"))return BroadGseaWorker.run(Path.of(args[1]).toAbsolutePath().normalize(),out,err);
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Use --help or --request RECIPE.json");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=bytes(source,RECIPE_BYTES);
            return execute(source,original,object(parse(original),"request"),out,err);
        }catch(Exception error){int code=failure(error);err.println((code==3?"SKIP3: ":"FAIL2: NO COMPLETE RESULT: ")+error.getMessage()+"\n"+ACTION);return code;}
    }
    private static int execute(Path source,byte[] original,Map<String,Object> raw,PrintStream out,PrintStream err)throws Exception{
        String mode=text(raw,"mode");if(!Set.of("RUN","VERIFY","REPLAY").contains(mode))throw new IOException("Declare RUN, VERIFY or REPLAY");
        Caps caps=caps(raw.get("limits"));Path target=resolve(source.getParent(),text(raw,"out"));
        Path prior=null;
        if(!mode.equals("RUN")){control(raw);prior=resolve(source.getParent(),text(raw,"analysis"));outside(target,prior);}
        Path root=fresh(target);var receipt=new LinkedHashMap<String,Object>();receipt.put("requested_mode",mode);receipt.put("engine",ENGINE);
        try{
            json(root.resolve("capture-kind.json"),Map.of("schema_version",1,"engine",ENGINE,"kind","native-gsea-workflow"));
            Files.write(root.resolve("original-request.json"),original,StandardOpenOption.CREATE_NEW);
            Map<String,Object> producer=producer();json(root.resolve("producer.json"),producer);
            Run request;Parent parent=null;
            if(mode.equals("RUN"))request=BroadGseaRecipe.run(raw);
            else{
                parent=inspect(prior,producer,caps);var replay=new LinkedHashMap<String,Object>(parent.request.raw());replay.put("limits",raw.get("limits"));request=BroadGseaRecipe.run(replay);
                json(root.resolve("lineage.json"),Map.of("schema_version",1,"mode",mode,"parent_analysis",parent.root.toString(),"parent_manifest",parent.manifestPin,
                    "parent_effective_request",parent.request.raw(),"parent_producer",parent.producer,"parent_scientific_files",parent.science));
            }
            Map<String,Object> effective=effective(request);json(root.resolve("effective-request.json"),effective);
            Run captured=BroadGseaRecipe.run(effective);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("results"));Files.createDirectory(root.resolve("worker"));
            var bindings=new TreeMap<String,Object>();long total=0;
            for(int i=0;i<request.analyses().size();i++){
                Analysis a=request.analyses().get(i);Path input=mode.equals("RUN")?resolve(source.getParent(),a.input()):parent.root.resolve(inputName(i));
                byte[] bytes=mode.equals("RUN")?bytes(input,caps.inputBytes()):captured(parent.root,inputName(i),caps.inputBytes());
                if(bytes.length>caps.inputBytes()-total)throw new IOException("Combined prepared inputs exceed maximum_input_bytes");total+=bytes.length;
                Files.write(root.resolve(inputName(i)),bytes,StandardOpenOption.CREATE_NEW);
                bindings.put(inputName(i),Map.of("declared",a.input(),"resolved_at_capture",input.toRealPath().toString(),"captured",pin(bytes)));
            }
            json(root.resolve("input-bindings.json"),bindings);BroadGseaRuntime.launch(root,caps,receipt);
            validateInputs(root,captured,mode.equals("RUN")?request:captured);
            var inventory=inventory(root,10000);var actual=scientific(inventory);shape(root,captured,mode,false,inventory.keySet());workerStatus(root,captured);
            if(parent!=null&&!same(actual,parent.science))throw new IOException("Saved scientific outputs differ from recomputation of captured inputs; resealed checksums do not admit the parent result");
            // Recheck the parent after copying and computing, before admitting this child.
            if(parent!=null){Parent after=inspect(parent.root,producer,caps);if(!same(after.manifestPin,parent.manifestPin)||!same(after.science,parent.science))throw new IOException("Parent capture changed during VERIFY/REPLAY");}
            finish(root,0,mode,mode.equals("RUN")?"Completed every requested analysis and complete gene-set family.":"Recomputed captured inputs and matched every scientific output byte; parent preserved.",caps,receipt);
            out.println("PASS_"+mode+": "+root+"\n"+SCOPE);return 0;
        }catch(Exception error){
            int code=failure(error);receipt.put("error",error.toString());
            try{finish(root,code,mode,error.toString(),caps,receipt);}catch(Exception closure){error.addSuppressed(closure);err.println("FAIL2: could not seal all failure evidence: "+closure+". Preserve "+root);}
            err.println((code==3?"SKIP3: ":"FAIL2: NO COMPLETE RESULT: ")+error.getMessage()+"\nEvidence: "+root+"\n"+ACTION);return code;
        }
    }
    private static void control(Map<String,Object> raw)throws IOException{
        keys(raw,Set.of("schema_version","mode","analysis","out","limits"),Set.of());integer(raw,"schema_version",1,1);
        if(!Set.of("VERIFY","REPLAY").contains(text(raw,"mode")))throw new IOException("Require VERIFY or REPLAY control");text(raw,"analysis");text(raw,"out");caps(raw.get("limits"));
    }
    private static Parent inspect(Path original,Map<String,Object> current,Caps limits)throws IOException{
        if(!Files.isDirectory(original))throw new Unavailable("Saved parent capture directory is unavailable: "+original);
        Path root=original.toRealPath();var marker=capturedObject(root,"capture-kind.json",RECIPE_BYTES);
        if(!same(marker,Map.of("schema_version",1,"engine",ENGINE,"kind","native-gsea-workflow")))throw new IOException("Parent capture kind differs");
        var status=capturedObject(root,"analysis-status.json",RECIPE_BYTES);
        keys(status,Set.of("schema_version","status","complete_result","mode","engine","manifest","scope","detail","required_action"),Set.of());integer(status,"schema_version",1,1);
        String mode=text(status,"mode");if(!Set.of("RUN","VERIFY","REPLAY").contains(mode)||!text(status,"engine").equals(ENGINE)||!text(status,"scope").equals(SCOPE)||!text(status,"required_action").equals(ACTION))throw new IOException("Parent completion contract differs");
        if(!bool(status,"complete_result")||!text(status,"status").equals("PASS_"+mode))throw new IOException("Parent has NO COMPLETE RESULT; preserve its recorded status and resolve its named cause");
        byte[] seal=captured(root,"capture-files.json",RECIPE_BYTES);if(!same(pin(seal),status.get("manifest")))throw new IOException("Parent seal differs from completion record");
        var manifest=object(parse(seal),"saved manifest");
        Map<String,Long> sizes=footprint(root,limits.files());long inputBytes=0,outputBytes=0;
        for(var e:sizes.entrySet()){
            if(e.getKey().startsWith("inputs/")){if(e.getValue()>limits.inputBytes()-inputBytes)throw new IOException("Parent inputs exceed controller maximum_input_bytes");inputBytes+=e.getValue();}
            else if(e.getKey().startsWith("results/")||e.getKey().startsWith("worker/")){if(e.getValue()>limits.outputBytes()-outputBytes)throw new IOException("Parent generated files exceed controller maximum_output_bytes");outputBytes+=e.getValue();}
            else if(e.getValue()>RECIPE_BYTES)throw new IOException("Parent metadata capacity exceeded");
        }
        var inventory=inventory(root,limits.files());if(!same(manifest,inventory))throw new IOException("Parent has a changed, missing or extra capture payload");
        var producer=capturedObject(root,"producer.json",RECIPE_BYTES);sameProducer(producer,current);
        Run request=BroadGseaRecipe.run(capturedObject(root,"effective-request.json",RECIPE_BYTES));
        if(!same(request.raw(),effective(request)))throw new IOException("Parent effective request has unbound paths");
        var raw=capturedObject(root,"original-request.json",RECIPE_BYTES);if(!text(raw,"mode").equals(mode))throw new IOException("Parent original and completion modes differ");
        Run declared=request;
        if(mode.equals("RUN")){
            declared=BroadGseaRecipe.run(raw);if(!same(effective(declared),request.raw()))throw new IOException("Original and captured scientific recipe differ");
        }else{
            control(raw);var lineage=capturedObject(root,"lineage.json",RECIPE_BYTES);
            keys(lineage,Set.of("schema_version","mode","parent_analysis","parent_manifest","parent_effective_request","parent_producer","parent_scientific_files"),Set.of());integer(lineage,"schema_version",1,1);
            if(!text(lineage,"mode").equals(mode))throw new IOException("Lineage mode differs");text(lineage,"parent_analysis");
            sameProducer(object(lineage.get("parent_producer"),"parent producer"),current);
            var previous=BroadGseaRecipe.run(object(lineage.get("parent_effective_request"),"parent effective request"));
            if(!same(previous.raw(),effective(previous)))throw new IOException("Lineage parent effective request differs");
            var expected=new LinkedHashMap<String,Object>(previous.raw());expected.put("limits",raw.get("limits"));
            if(!same(expected,request.raw())||!same(scientific(inventory),lineage.get("parent_scientific_files")))throw new IOException("Replay/verification lineage and captured result differ");
        }
        validateInputs(root,request,declared);shape(root,request,mode,true,sizes.keySet());workerStatus(root,request);
        var process=capturedObject(root,"process.json",RECIPE_BYTES);
        if(integer(process,"process_exit_code",0,0)!=0||!text(process,"requested_mode").equals(mode)||process.containsKey("error"))throw new IOException("Parent owned process did not complete successfully");
        return new Parent(root,request,producer,scientific(inventory),pin(seal));
    }
    private static void validateInputs(Path root,Run captured,Run declared)throws IOException{
        var bindings=capturedObject(root,"input-bindings.json",RECIPE_BYTES);var expected=new TreeSet<String>();long total=0;
        for(int i=0;i<captured.analyses().size();i++){
            String name=inputName(i);expected.add(name);var binding=object(bindings.get(name),"input binding "+name);
            keys(binding,Set.of("declared","resolved_at_capture","captured"),Set.of());text(binding,"resolved_at_capture");
            if(!text(binding,"declared").equals(declared.analyses().get(i).input()))throw new IOException("Declared input binding differs: "+name);
            byte[] bytes=captured(root,name,captured.caps().inputBytes());
            if(bytes.length>captured.caps().inputBytes()-total)throw new IOException("Combined captured input capacity exceeded");total+=bytes.length;
            if(!same(pin(bytes),binding.get("captured")))throw new IOException("Captured input pin differs: "+name);
        }
        if(!bindings.keySet().equals(expected))throw new IOException("Captured input binding population differs");
    }
    private static void workerStatus(Path root,Run request)throws IOException{
        var status=capturedObject(root,"worker/status.json",RECIPE_BYTES);keys(status,Set.of("status","complete_analyses","requested_analyses"),Set.of());
        int count=request.analyses().size();if(!text(status,"status").equals("PASS_NATIVE_ENGINE")||integer(status,"complete_analyses",count,count)!=count||integer(status,"requested_analyses",count,count)!=count)throw new IOException("Worker did not complete every analysis");
    }
    private static void shape(Path root,Run request,String mode,boolean sealed,Set<String> files)throws IOException{
        var expected=new TreeSet<>(Set.of("capture-kind.json","original-request.json","producer.json","effective-request.json","input-bindings.json","worker/runtime.json","worker/stdout.log","worker/stderr.log","worker/status.json"));
        if(!mode.equals("RUN"))expected.add("lineage.json");if(sealed)expected.addAll(Set.of("WARNINGS.md","process.json","capture-files.json","analysis-status.json"));
        for(int i=0;i<request.analyses().size();i++){
            expected.add(inputName(i));String output=outputName(i)+"/";
            for(String name:List.of("ranking.tsv","scores.tsv","dispositions.json","result.bin","result-summary.json","partial/real-ranking.tsv","partial/dispositions.jsonl","partial/observed.jsonl","partial/null-prefix.tsv"))expected.add(output+name);
        }
        if(!files.equals(expected))throw new IOException("Capture contains an undeclared, missing or incomplete payload; expected "+expected.size()+" files, found "+files.size());
        var directories=new HashSet<>(Set.of("","worker/home","worker/tmp"));for(String name:expected){int slash=name.lastIndexOf('/');while(slash>0){directories.add(name.substring(0,slash));slash=name.lastIndexOf('/',slash-1);}}
        try(var paths=Files.walk(root)){for(Path path:paths.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList())if(!directories.contains(root.relativize(path).toString().replace('\\','/')))throw new IOException("Undeclared directory inside capture: "+path);}
    }
}
