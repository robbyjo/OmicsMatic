/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Original private-runtime region-to-gene component; full eGRN admission is pending. */
public final class ScenicPlusToolsCli {
    private ScenicPlusToolsCli(){ }
    static final String PROFILE="scenicplus-1.0a2-r2g-v1";
    static final String COMPLETE="COMPLETE_SCOPED_SCENICPLUS_R2G";
    static final String RESOURCE="/org/jlinalg/omics/scenicplus-workflow/";
    static final List<String> SOURCES=List.of("worker.py","SOURCE-PROFILE.json");
    static final String ACTION="PRIVATE CAPTURE: licensed vendor traces may appear; do not publish or transfer outside accepted license terms. SKIP3: restore the exact permitted private runtime/input or retain the skipped dependent analysis. FAIL2: preserve raw/partial/failed evidence, fix the named cause and retry NEW output. Read field-ledger.json: successful calls do not make NaN/Inf fields reportable. This region-to-gene operation uses explicit prepared matrices and search-space candidates. Use the separate preparation, motif and full-network workflows as needed. A successful call supplies no paper-role, whole-tool or biological qualification. SHA256 is integrity, not execution authentication.";
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error){
        Path root=null;
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println("SCENIC+ development component: --request JSON | --inspect CAPTURE. Schema1 RUN: profile="+PROFILE+", operation=REGION_TO_GENE, python, out, inputs{expression,accessibility,search_space}, options{correlation:SR|PR,mask_expr_dropout:boolean}, optional limits. Original GBM defaults, one worker, finite literal cell-by-feature TSVs, identical ordered cells. VERIFY/REPLAY require schema_version,mode,analysis,out and optional python; they actually refit to a fresh disjoint witness. --inspect checks saved closure only. Requires a complete source-built JAR and an already permitted unmodified Linux runtime. The main scenicplus-tools command routes this exact profile; execution alone supplies no catalog or biological qualification.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved R2G closure checked; no new original computation executed. "+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --inspect CAPTURE or --help");
            Path requestFile=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(requestFile,16777216);var original=object(GprofilerJson.read(raw));
            String mode=text(original.get("mode"),"mode");Path parent=null;Map<String,Object> parentSeal=null;Map<String,Object> request;
            if(Set.of("VERIFY","REPLAY").contains(mode)){
                keys(original,Set.of("schema_version","mode","analysis","out","python"));integer(original.get("schema_version"),1,1);
                parent=resolve(requestFile,text(original.get("analysis"),"analysis"));request=new LinkedHashMap<>(inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));
                request.put("out",resolve(requestFile,text(original.get("out"),"out")).toString());
                if(original.containsKey("python"))request.put("python",resolve(requestFile,text(original.get("python"),"python")).toString());
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target),"Witness and read-only parent must be disjoint");
            }else request=validate(original,requestFile);
            root=newOutput(Path.of(request.get("out").toString()));Files.createDirectory(root.resolve("worker"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            write(root.resolve("producer.json"),producer());ScenicWorkflowFiles.text(root.resolve("WARNINGS.md"),"# PRIVATE SCENIC+ development capture\n\n"+ACTION+"\n");
            Files.createDirectory(root.resolve("inputs"));var inputs=map();var captures=map();
            for(String role:List.of("expression","accessibility","search_space")){
                Path source=parent==null?Path.of(text(object(request.get("inputs")).get(role),role)):parent.resolve("inputs/"+role+".tsv");
                capture(source,root.resolve("inputs/"+role+".tsv"),request,captures,"inputs/"+role+".tsv");inputs.put(role,"inputs/"+role+".tsv");
            }
            write(root.resolve("input-inventory.json"),captures);Files.createDirectory(root.resolve("source"));var sources=map();
            for(String name:SOURCES){byte[] body=resource(name);Files.write(root.resolve("source/"+name),body,StandardOpenOption.CREATE_NEW);sources.put(name,pin(body));}
            write(root.resolve("source-inventory.json"),sources);write(root.resolve("worker/request.json"),map("options",request.get("options"),"limits",request.get("limits")));
            var process=map();ScenicPlusWorkflowRuntime.execute(root,request,process);
            write(root.resolve("worker-process.json"),process);
            var summary=read(root.resolve("worker/out/SUMMARY.json"));
            String outcome="Nonfinite numerical fields: "+number(summary,"nonfinite_numerical_fields")+"; undefined rho rows: "+number(summary,"nonfinite_rho_rows")+"; unreturned candidates: "+number(summary,"unreturned_candidates")+". Read worker/out/field-ledger.json; preserve undefined outputs and do not infer per-target fit status.";
            ScenicWorkflowFiles.text(root.resolve("OUTCOME-WARNINGS.md"),"# Original R2G field outcomes\n\n"+outcome+"\n\n"+ACTION+"\n");
            if(number(summary,"nonfinite_numerical_fields")>0||number(summary,"unreturned_candidates")>0)out.println("WARNING: "+outcome);
            if(parent!=null){
                require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent manifest changed during refit");inspect(parent);
                for(String name:List.of("worker/out/scientific.json","worker/out/adjacencies.tsv","worker/out/field-ledger.json","worker/out/SUMMARY.json","worker/out/invocation.json","runtime-before.json"))
                    require(Arrays.equals(bytes(parent.resolve(name),67108864),bytes(root.resolve(name),67108864)),"Fresh "+mode+" comparison differs: "+name);
            }
            var manifest=map("schema_version",1,"status",COMPLETE,"profile",PROFILE,"mode",mode,"summary",summary,"private_capture",true,"new_family_credit",0,"new_reviews",0,"whole_goal_complete",false);
            manifest.put("inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("status",COMPLETE,"manifest",pin(root.resolve("manifest.json"))));
            inspect(root);out.println(COMPLETE+" "+root+"\n"+ACTION);return 0;
        }catch(Exception failure){
            int code=failure instanceof ScenicWorkflowContracts.Unavailable?3:2;
            if(root!=null)try{
                if(Files.exists(root.resolve("COMPLETED.json")))Files.move(root.resolve("COMPLETED.json"),root.resolve("UNADMITTED-COMPLETED.json"));
                write(root.resolve("analysis-status.json"),map("status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT","cause",failure.toString(),"private_capture",true,"required_action",ACTION));
                ScenicWorkflowFiles.text(root.resolve("FAILURE.md"),"# "+(code==3?"SKIP3: dependent analysis skipped":"FAIL2: no complete result")+"\n\n"+failure+"\n\n"+ACTION+"\n");
            }catch(Exception secondary){failure.addSuppressed(secondary);}
            error.println((code==3?"SKIP3: ":"FAIL2: ")+failure+"\n"+ACTION);return code;
        }
    }
    static Path resolve(Path source,String value){return source.getParent().resolve(value).toAbsolutePath().normalize();}
    static Map<String,Object> validate(Map<String,Object> raw,Path source)throws IOException{
        keys(raw,Set.of("schema_version","mode","profile","operation","python","out","inputs","options","limits"));integer(raw.get("schema_version"),1,1);
        require("RUN".equals(raw.get("mode"))&&PROFILE.equals(raw.get("profile"))&&"REGION_TO_GENE".equals(raw.get("operation")),"Require schema1 RUN REGION_TO_GENE and the exact SCENIC+ profile");
        var result=new LinkedHashMap<>(raw);for(String key:List.of("python","out"))result.put(key,resolve(source,text(raw.get(key),key)).toString());
        var input=object(raw.get("inputs"));require(input.keySet().equals(Set.of("expression","accessibility","search_space")),"Declare exactly expression/accessibility/search_space");var normalized=map();
        for(String key:input.keySet())normalized.put(key,resolve(source,text(input.get(key),key)).toString());result.put("inputs",normalized);
        var options=object(raw.get("options"));require(options.keySet().equals(Set.of("correlation","mask_expr_dropout")),"Declare exactly correlation and mask_expr_dropout; GBM source defaults are fixed");
        require(Set.of("SR","PR").contains(text(options.get("correlation"),"correlation"))&&options.get("mask_expr_dropout") instanceof Boolean,"Invalid explicit correlation/dropout options");
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();var defaults=map("maximum_input_bytes",1073741824L,"maximum_member_bytes",1073741824L,"maximum_output_bytes",2147483648L,"maximum_files",50000L,"timeout_seconds",1800L,"maximum_cells",1000000L,"maximum_genes",1000000L,"maximum_regions",10000000L,"maximum_pairs",10000000L,"maximum_matrix_values",100000000L);
        keys(limits,defaults.keySet());var bound=map();for(var entry:defaults.entrySet())bound.put(entry.getKey(),integer(limits.getOrDefault(entry.getKey(),entry.getValue()),1,Long.parseLong(entry.getValue().toString())));result.put("limits",bound);return result;
    }
    static byte[] resource(String name)throws IOException{
        try(var stream=ScenicPlusToolsCli.class.getResourceAsStream(RESOURCE+name)){require(stream!=null,"Missing packaged SCENIC+ caller/profile: "+name);return stream.readAllBytes();}
    }
    static Map<String,Object> producer()throws IOException{
        try{
            Path jar=Path.of(ScenicPlusToolsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new ScenicWorkflowContracts.Unavailable("Use a complete source-built project JAR; loose/mixed classes are unqualified");
            var seen=new HashSet<Class<?>>();for(Class<?> type:List.of(ScenicPlusToolsCli.class,ScenicPlusWorkflowRuntime.class,ScenicWorkflowProcess.class,ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,GprofilerWorkflowContracts.class,GprofilerJson.class,OwnedProcesses.class,MrPressoWorkflowContracts.class))origins(type,jar,seen);
            return map("kind","EXACT_PACKAGED_JAR","identity",pin(jar),"profile",PROFILE,"profile_identity",pin(resource("SOURCE-PROFILE.json")),"authentication",false);
        }catch(URISyntaxException failure){throw new IOException("Cannot resolve exact producer",failure);}
    }
    static void origins(Class<?> type,Path jar,Set<Class<?>> seen)throws IOException,URISyntaxException{
        if(!seen.add(type))return;require(jar.equals(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()),"Mixed controller/runtime class origin: "+type.getName());for(Class<?> nested:type.getNestMembers())origins(nested,jar,seen);
    }
    static Map<String,Object> inspect(Path folder)throws IOException{
        Path root=folder.toAbsolutePath().normalize();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),"Require a physical existing capture");
        var seal=read(root.resolve("COMPLETED.json"));require(COMPLETE.equals(seal.get("status"))&&same(seal.get("manifest"),pin(root.resolve("manifest.json"))),"Missing/changed completed manifest");
        var manifest=read(root.resolve("manifest.json"));require(COMPLETE.equals(manifest.get("status"))&&PROFILE.equals(manifest.get("profile"))&&Boolean.TRUE.equals(manifest.get("private_capture")),"Wrong capture kind/status");
        var request=read(root.resolve("canonical-request.json"));require(same(request,validate(request,root.resolve("canonical-request.json")))&&root.toString().equals(request.get("out")),"Noncanonical request/output binding");
        require(same(manifest.get("inventory"),inventory(root,request)),"Saved capture bytes/population changed");require(same(producer(),read(root.resolve("producer.json"))),"Restore the exact complete producer JAR; no hash refresh");
        for(String name:SOURCES)require(Arrays.equals(resource(name),bytes(root.resolve("source/"+name),67108864)),"Saved caller/profile differs from producer");
        require(same(manifest.get("summary"),read(root.resolve("worker/out/SUMMARY.json"))),"Changed scientific summary");return request;
    }
}
