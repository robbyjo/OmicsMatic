/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Original unpaired SCENIC+ annotation-group metacell preparation; family admission is pending. */
public final class ScenicPlusPrepareCli {
    private ScenicPlusPrepareCli(){ }
    static final String PROFILE="scenicplus-1.0a2-prepare-v1";
    static final String COMPLETE="COMPLETE_SCOPED_SCENICPLUS_PREPARATION";
    static final String RESOURCE="/org/jlinalg/omics/scenicplus-prepare/";
    static final List<String> SOURCES=List.of("worker.py","r2g_runtime.py","SOURCE-PROFILE.json");
    static final Map<String,String> INPUT_FILES=Map.of("rna","RNA.h5ad","cistopic","TRUSTED-CISTOPIC.pkl");
    static final String ACTION="PRIVATE LICENSED CAPTURE: keep source-bearing traces, fitted objects, runtime and captures private under accepted internal academic terms. Supply only trusted original cisTopic pickles: deserialization executes code. SKIP3: restore the exact permitted runtime/input, or retain the skipped analysis. FAIL2: preserve raw and failed evidence, correct the named cause and retry NEW output. Per-group cells-per-metacell mappings fail in this original version and are unsupported; group labels containing the separator are refused to prevent original metadata truncation. Declare raw versus processed RNA explicitly; no normalization or raw fallback. Original internal metacell ordinal seeds are preserved, not user-controlled. Sampling membership is not returned or reconstructed. Read PREPARATION-WARNINGS.md and worker/report.json. No model fit, biological validity, complete SCENIC+ family, paper reproduction or catalog credit is inferred. SHA256 is integrity, not execution authentication.";
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error){
        Path root=null;
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println("Original SCENIC+ unpaired preparation: --request JSON | --inspect CAPTURE. Schema1 RUN: profile="+PROFILE+", operation=UNPAIRED, python, NEW out, inputs{rna,cistopic}, options{group_key,use_raw,nr_metacells,cells_per_metacell,separator,scale_factor}. Requires original RNA AnnData and trusted original cisTopic object with selected fitted topic model. No shared barcodes are required; shared annotation groups are sampled by the unmodified original API. nr_metacells may be null, a positive integer or an exact shared-group mapping; cells_per_metacell must be a positive integer. VERIFY and REPLAY compute fresh preparation into a NEW disjoint witness. --inspect checks saved closure only. Requires the complete source-built JAR and permitted unchanged Linux runtime.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved unpaired-preparation closure checked; no new original computation executed. "+ACTION);return 0;}
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
            for(String role:INPUT_FILES.keySet()){
                Path source=parent==null?Path.of(text(object(request.get("inputs")).get(role),role)):parent.resolve("inputs/"+INPUT_FILES.get(role));
                capture(source,root.resolve("inputs/"+INPUT_FILES.get(role)),request,captures,"inputs/"+INPUT_FILES.get(role));inputs.put(role,"inputs/"+INPUT_FILES.get(role));
            }
            write(root.resolve("input-inventory.json"),captures);Files.createDirectory(root.resolve("source"));var sources=map();
            for(String name:SOURCES){byte[] body=resource(name);Files.write(root.resolve("source/"+name),body,StandardOpenOption.CREATE_NEW);sources.put(name,pin(body));}
            write(root.resolve("source-inventory.json"),sources);write(root.resolve("worker/request.json"),map("options",request.get("options"),"limits",request.get("limits")));
            var process=map();ScenicPlusPrepareRuntime.execute(root,request,process);
            write(root.resolve("worker-process.json"),process);
            var summary=read(root.resolve("worker/out/SUMMARY.json"));
            ScenicWorkflowFiles.text(root.resolve("PREPARATION-WARNINGS.md"),"# Original preparation scope and outcomes\n\n"+ACTION+"\n\nShared groups and omitted assay-only groups are recorded in worker/out/SUMMARY.json. Raw and processed RNA are different scientific inputs. The selected model is supplied, not fitted by this adapter.\n");
            if(parent!=null){
                require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent manifest changed during refit");inspect(parent);
                for(String name:List.of("worker/out/scientific.jsonl","worker/out/expression.tsv","worker/out/accessibility.tsv","worker/out/SUMMARY.json","worker/out/invocation.json","runtime-before.json"))
                    require(Files.mismatch(parent.resolve(name),root.resolve(name))==-1,"Fresh "+mode+" comparison differs: "+name);
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
        require("RUN".equals(raw.get("mode"))&&PROFILE.equals(raw.get("profile"))&&"UNPAIRED".equals(raw.get("operation")),"Require schema1 RUN UNPAIRED and exact preparation profile");
        var result=new LinkedHashMap<>(raw);for(String key:List.of("python","out"))result.put(key,resolve(source,text(raw.get(key),key)).toString());
        var input=object(raw.get("inputs"));require(input.keySet().equals(INPUT_FILES.keySet()),"Declare exactly rna and trusted cistopic inputs");var normalized=map();
        for(String key:input.keySet())normalized.put(key,resolve(source,text(input.get(key),key)).toString());result.put("inputs",normalized);
        var options=object(raw.get("options"));keys(options,Set.of("group_key","use_raw","nr_metacells","cells_per_metacell","separator","scale_factor"));
        require(options.containsKey("use_raw")&&options.get("use_raw") instanceof Boolean,"Explicit Boolean use_raw required; processed RNA is never substituted");
        String group=text(options.get("group_key"),"group_key"),separator=text(options.getOrDefault("separator","_"),"separator");
        require(group.length()<=4096&&separator.length()<=128&&group.chars().noneMatch(c->c<32||c==127)&&separator.chars().noneMatch(c->c<32||c==127),"Invalid grouping key/separator");
        Object count=options.get("nr_metacells");
        if(count instanceof Map<?,?>){var counts=map();for(var e:object(count).entrySet())counts.put(text(e.getKey(),"group"),integer(e.getValue(),1,10000000));require(!counts.isEmpty(),"Empty metacell count mapping");count=counts;}
        else if(count!=null)count=integer(count,1,10000000);
        Object cellCount=options.getOrDefault("cells_per_metacell",10);
        require(!(cellCount instanceof Map<?,?>),"Per-group cells_per_metacell mappings fail in original v1.0a2 (TypeError); use an explicit supported integer or skip this analysis");
        long scale=integer(options.getOrDefault("scale_factor",1000000),1,2147483647);
        result.put("options",map("group_key",group,"use_raw",options.get("use_raw"),"nr_metacells",count,"cells_per_metacell",integer(cellCount,1,10000000),"separator",separator,"scale_factor",scale));
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        var defaults=map("maximum_input_bytes",2147483648L,"maximum_member_bytes",2147483648L,"maximum_output_bytes",8589934592L,"maximum_files",50000L,"timeout_seconds",1800L,"maximum_cells",1000000L,"maximum_genes",1000000L,"maximum_regions",10000000L,"maximum_pairs",10000000L,"maximum_matrix_values",100000000L);
        var ceilings=map("maximum_input_bytes",68719476736L,"maximum_member_bytes",34359738368L,"maximum_output_bytes",274877906944L,"maximum_files",1000000L,"timeout_seconds",86400L,"maximum_cells",10000000L,"maximum_genes",10000000L,"maximum_regions",100000000L,"maximum_pairs",1000000000L,"maximum_matrix_values",10000000000L);
        keys(limits,defaults.keySet());var bound=map();for(var entry:defaults.entrySet())bound.put(entry.getKey(),integer(limits.getOrDefault(entry.getKey(),entry.getValue()),1,Long.parseLong(ceilings.get(entry.getKey()).toString())));result.put("limits",bound);return result;
    }
    static double fraction(Object value)throws IOException{double v=decimal(value).doubleValue();require(Double.isFinite(v)&&v>=0&&v<=1,"Require an original threshold in [0,1]");return v;}
    static byte[] resource(String name)throws IOException{
        try(var stream=ScenicPlusPrepareCli.class.getResourceAsStream(RESOURCE+name)){require(stream!=null,"Missing packaged SCENIC+ caller/profile: "+name);return stream.readAllBytes();}
    }
    static Map<String,Object> producer()throws IOException{
        try{
            Path jar=Path.of(ScenicPlusPrepareCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new ScenicWorkflowContracts.Unavailable("Use a complete source-built project JAR; loose/mixed classes are unqualified");
            var seen=new HashSet<Class<?>>();for(Class<?> type:List.of(ScenicPlusPrepareCli.class,ScenicPlusPrepareRuntime.class,ScenicWorkflowProcess.class,ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,GprofilerWorkflowContracts.class,GprofilerJson.class,OwnedProcesses.class,MrPressoWorkflowContracts.class))origins(type,jar,seen);
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
