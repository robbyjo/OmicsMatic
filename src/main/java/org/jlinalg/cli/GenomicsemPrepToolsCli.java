/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;
import static org.jlinalg.cli.GenomicsemPrepContracts.ACTION;
import static org.jlinalg.cli.GenomicsemPrepContracts.COMPLETE;
import static org.jlinalg.cli.GenomicsemPrepContracts.PROFILE;
import static org.jlinalg.cli.GenomicsemPrepContracts.validate;
import static org.jlinalg.cli.GenomicsemPrepContracts.canonical;

/** Source-bound original preparation, fresh execution and portable saved inspection. */
public final class GenomicsemPrepToolsCli {
    private GenomicsemPrepToolsCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved original preparation checked; no new upstream computation. "+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --inspect CAPTURE or --help");
            Path control=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(control,16777216);var source=object(GprofilerJson.read(raw));String mode=text(source.get("mode"),"mode");var request=new LinkedHashMap<String,Object>();Path parent=null;Map<String,Object> parentSeal=null;
            if(Set.of("VERIFY","REPLAY").contains(mode)){
                keys(source,Set.of("schema_version","mode","analysis","out","runtime_home","runtime_addons"));integer(source.get("schema_version"),1,1);parent=resolve(control.getParent(),text(source.get("analysis"),"analysis"));request.putAll(inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));var binding=read(parent.resolve("runtime-binding.json"));
                request.put("bundle",parent.resolve("inputs/bundle").toString());request.put("out",resolve(control.getParent(),text(source.get("out"),"out")).toString());
                request.put("runtime_home",source.containsKey("runtime_home")?resolve(control.getParent(),text(source.get("runtime_home"),"runtime_home")).toString():binding.get("runtime_home"));
                Object extra=source.containsKey("runtime_addons")?source.get("runtime_addons"):binding.get("runtime_addons");request.put("runtime_addons",extra==null?null:resolve(control.getParent(),text(extra,"runtime_addons")).toString());
            }else request.putAll(validate(source));
            var validated=validate(request);var canonical=canonical(validated);Path target=resolve(control.getParent(),text(validated.get("out"),"out")),home=resolve(control.getParent(),text(validated.get("runtime_home"),"runtime_home")),bundle=resolve(control.getParent(),text(validated.get("bundle"),"bundle"));Path extra=validated.get("runtime_addons")==null?null:resolve(control.getParent(),text(validated.get("runtime_addons"),"runtime_addons"));
            disjoint(target,home,"Runtime and NEW output");if(extra!=null)disjoint(target,extra,"Optional runtime and NEW output");if(parent!=null)disjoint(target,parent,"Parent evidence and fresh output");
            for(String selected:GenomicsemPrepBundle.selected(bundle,canonical))require(!bundle.resolve(selected).startsWith(target),"Selected original input resides under NEW output");
            var producer=producer();root=newOutput(target);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),canonical);write(root.resolve("source-origin.json"),map("source_request_address",control.toString(),"creation_address",root.toString(),"execution_mode",mode));
            ScenicWorkflowFiles.text(root.resolve("WARNINGS.md"),"# GENOMICSEM PREPARATION: REVIEW BEFORE USING PRODUCTS\n\n"+ACTION+"\n\nUse products only with a valid COMPLETED.json seal and products_admitted=true in scientific-audit.json. The status tables identify unavailable source units. Original RDS, typed fields and logs are retained. Java does not interpret opaque RDS. No missing field, invalid identifier or nonfinite value is repaired. Input tables must be rectangular UTF-8 whitespace/tab separated records; multiline quoted records are outside this profile. The optional Rmpfr/GMP binaries were built under R4.6.2; selected arithmetic is qualified under the pinned R4.6.1 and startup warnings remain visible.\n");
            if(parent!=null)for(String name:List.of("canonical-request.json","manifest.json","COMPLETED.json","producer.json"))Files.copy(parent.resolve(name),root.resolve("parent-"+name));
            write(root.resolve("RUNNING.json"),map("status","RUNNING_NO_PRODUCT_ADMITTED","mode",mode));write(root.resolve("producer.json"),producer);GenomicsemPrepRuntime.sources(root,false);var captures=GenomicsemPrepBundle.capture(root,bundle,canonical);write(root.resolve("input-inventory.json"),captures);write(root.resolve("input-binding.json"),map("bundle_declared",validated.get("bundle"),"bundle_resolved",bundle.toRealPath().toString(),"captured","inputs/bundle","files",captures));
            var attributes=GenomicsemPrepBundle.attributes(root,captures);write(root.resolve("input-audit.json"),GenomicsemPrepAudit.input(root,canonical));Path captured=root;GenomicsemPrepRuntime.execute(root,home,extra,canonical,()->GenomicsemPrepBundle.unchanged(captured,attributes));GenomicsemPrepBundle.verify(root,canonical,captures);
            var audit=GenomicsemPrepAudit.evaluate(root,canonical,false);write(root.resolve("scientific-audit.json"),audit);err.println("GENOMICSEM PREPARATION REVIEW: "+new String(json(audit),java.nio.charset.StandardCharsets.UTF_8)+ACTION);require(Boolean.TRUE.equals(audit.get("products_admitted")),"Original output did not meet complete requested warning/unavailable/smoothing policies; read scientific-audit.json and products/*status.tsv");GenomicsemProcess.footprint(root,canonical);
            if(parent!=null){
                inspect(parent);require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent changed during fresh computation");var matched=map();for(String name:compared(root,canonical)){var identity=pin(root.resolve(name));require(same(identity,pin(parent.resolve(name))),"Fresh "+mode+" scientific/runtime bytes differ: "+name);matched.put(name,identity);}
                write(root.resolve("fresh-comparison.json"),map("mode",mode,"parent_manifest",parentSeal,"matched_files",matched,"new_original_computation_executed",true,"opaque_RDS_logs_paths_or_timings_compared",false));
            }
            write(root.resolve("analysis-status.json"),map("status",COMPLETE,"profile",PROFILE,"products_admitted",true));write(root.resolve("manifest.json"),map("schema_version",1,"status",COMPLETE,"profile",PROFILE,"operation",canonical.get("operation"),"mode",mode,"scientific_summary",audit,"private_capture",true,"new_family_credit",0,"new_reviews",0,"whole_goal_complete",false,"inventory",inventory(root,canonical)));write(root.resolve("COMPLETED.json"),map("status",COMPLETE,"manifest",pin(root.resolve("manifest.json"))));inspect(root);out.println(COMPLETE+" "+root);return 0;
        }catch(Exception|OutOfMemoryError failure){
            int code=failure instanceof GenomicsemRuntime.Unavailable||failure instanceof ScenicWorkflowContracts.Unavailable||failure instanceof NoSuchFileException?3:2;String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_PRODUCT";
            if(root!=null)try{for(String name:List.of("COMPLETED.json","analysis-status.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("UNADMITTED-"+name));write(root.resolve("analysis-status.json"),map("status",status,"products_admitted",false,"cause",failure.toString(),"required_action",ACTION));ScenicWorkflowFiles.text(root.resolve("FAILURE.md"),"# "+status+"\n\n"+failure+"\n\n"+ACTION+"\n");}catch(Exception second){failure.addSuppressed(second);}
            err.println(status+": "+failure+"\n"+ACTION);return code;
        }
    }
    private static void disjoint(Path a,Path b,String label)throws IOException {require(!a.startsWith(b)&&!b.startsWith(a),label+" must be disjoint");}
    static Map<String,Object> producer()throws IOException {
        try{
            Path jar=Path.of(GenomicsemPrepToolsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();if(!Files.isRegularFile(jar)||!jar.getFileName().toString().endsWith(".jar"))throw new GenomicsemRuntime.Unavailable("Use the complete executable JAR; loose-class scientific execution is skipped");
            try(var archive=new JarFile(jar.toFile())){require(archive.getManifest()!=null&&"org.jlinalg.cli.JLinAlgCli".equals(archive.getManifest().getMainAttributes().getValue("Main-Class")),"Use the complete OmicsMatic executable JAR");for(String name:List.of("org/jlinalg/cli/JLinAlgCli.class","jdistlib/ChiSquare.class","jdistlib/accelerator/CpuComputeBackend.class","org/jlinalg/omics/genomicsem-preparation/SOURCE.json"))require(archive.getJarEntry(name)!=null,"Incomplete executable producer JAR: "+name);}
            return map("complete_executable_JAR",pin(jar),"source_descriptor",pin(GenomicsemPrepRuntime.resource("SOURCE.json")));
        }catch(java.net.URISyntaxException e){throw new IOException("Cannot identify producer JAR",e);}
    }
    private static Set<String> compared(Path root,Map<String,Object>request)throws IOException {
        var names=new TreeSet<>(Set.of("input-audit.json","scientific-audit.json","runtime-before.json","worker/conditions.json","worker/initial-conditions.json","worker/product-projection.json"));
        Path typed=GenomicsemPrepAudit.typed(root,text(request.get("operation"),"operation"));try(var stream=Files.walk(typed)){for(Path p:stream.filter(Files::isRegularFile).toList())names.add(root.relativize(p).toString().replace('\\','/'));}
        try(var stream=Files.walk(root.resolve("products"))){for(Path p:stream.filter(Files::isRegularFile).toList())if(!p.getFileName().toString().endsWith(".gz"))names.add(root.relativize(p).toString().replace('\\','/'));}
        // Original gzip envelopes may encode creation time. Compare a canonical
        // uncompressed byte identity, never rewrite or normalize original files.
        if("MUNGE".equals(request.get("operation")))names.add("worker/munged-content.json");
        if("ENRICH".equals(request.get("operation")))names.addAll(Set.of("worker/enrichment-selection.json","worker/enrichment-input-audit.json"));return names;
    }
    static Map<String,Object> inspect(Path path)throws IOException {
        Path root=path.toAbsolutePath().normalize();require(root.toRealPath().equals(root)&&Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Use a physical saved capture");require(!Files.exists(root.resolve("FAILURE.md")),"Failed/skipped attempts remain unadmitted");var seal=read(root.resolve("COMPLETED.json"));var manifest=read(root.resolve("manifest.json"));require(COMPLETE.equals(seal.get("status"))&&COMPLETE.equals(manifest.get("status"))&&PROFILE.equals(manifest.get("profile"))&&same(seal.get("manifest"),pin(root.resolve("manifest.json"))),"Missing/changed completed preparation seal");var request=read(root.resolve("canonical-request.json"));require(same(request,canonical(validate(request))),"Noncanonical saved request");require(same(producer(),read(root.resolve("producer.json"))),"Restore exact producer JAR; do not refresh old hashes");require(same(manifest.get("inventory"),inventory(root,request)),"Capture bytes/namespace changed");GenomicsemPrepRuntime.sources(root,true);GenomicsemRuntime.namespace(root,"namespace-before.tsv");GenomicsemRuntime.namespace(root,"namespace-after.tsv");
        var state=read(root.resolve("analysis-status.json"));require(COMPLETE.equals(state.get("status"))&&Boolean.TRUE.equals(state.get("products_admitted")),"Saved output is unadmitted");var original=object(GprofilerJson.read(bytes(root.resolve("source-request.json"),16777216)));String mode=text(read(root.resolve("source-origin.json")).get("execution_mode"),"mode");require(mode.equals(manifest.get("mode")),"Changed execution mode");
        if(mode.equals("RUN"))require(same(request,canonical(validate(original))),"Source/canonical request differs");else{
            require(Set.of("VERIFY","REPLAY").contains(mode)&&mode.equals(original.get("mode")),"Unknown fresh execution mode");require(same(request,read(root.resolve("parent-canonical-request.json")))&&same(read(root.resolve("producer.json")),read(root.resolve("parent-producer.json")))&&same(read(root.resolve("parent-COMPLETED.json")).get("manifest"),pin(root.resolve("parent-manifest.json"))),"Changed parent scientific request, producer or seal");var comparison=read(root.resolve("fresh-comparison.json"));require(mode.equals(comparison.get("mode"))&&Boolean.TRUE.equals(comparison.get("new_original_computation_executed"))&&same(comparison.get("parent_manifest"),pin(root.resolve("parent-manifest.json"))),"Changed fresh execution provenance");var matches=object(comparison.get("matched_files"));require(matches.keySet().equals(compared(root,request)),"Incomplete fresh scientific/runtime comparison population");for(var e:matches.entrySet())require(same(e.getValue(),pin(root.resolve(e.getKey()))),"Changed fresh compared file");
        }
        var captures=read(root.resolve("input-inventory.json"));GenomicsemPrepBundle.verify(root,request,captures);var binding=read(root.resolve("input-binding.json"));require("inputs/bundle".equals(binding.get("captured"))&&same(binding.get("files"),captures),"Changed input binding");if(mode.equals("RUN"))require(Objects.equals(binding.get("bundle_declared"),original.get("bundle")),"Changed original declared bundle");require(same(GenomicsemPrepAudit.input(root,request),read(root.resolve("input-audit.json"))),"Changed derived input audit");var audit=GenomicsemPrepAudit.evaluate(root,request,true);require(Boolean.TRUE.equals(audit.get("products_admitted"))&&same(audit,read(root.resolve("scientific-audit.json")))&&same(audit,manifest.get("scientific_summary")),"Changed/unadmitted derived product audit");require(same(read(root.resolve("runtime-before.json")),read(root.resolve("runtime-after.json"))),"Observed declared runtime changed during original preparation");return request;
    }
    static String help(){return """
        genomicsem-prep --request JSON | --inspect CAPTURE | --help
        Pinned original MUNGE, LDSC, SUMSTATS, READ_FUSION, S_LDSC, ENRICH.
        RUN schema1: mode,profile,operation,runtime_home,runtime_addons,
        NEW out,bundle,paths,options,optional limits. All scientific options
        are explicit. Inputs are literal relative paths under the bundle.
        VERIFY/REPLAY schema1: mode,analysis,NEW out,optional runtime_home
        and runtime_addons. Both execute a fresh original preparation call.
        --inspect checks saved bytes and derived audits without refitting.
        Original values and unavailable fields are preserved. Read status
        tables and scientific-audit.json before using admitted products.
        Exit0 scoped complete;3 prerequisite unavailable, analysis skipped;
        2 no complete admitted product. Existing outputs are preserved.
        See docs/GENOMICSEM_PREPARATION.md for source behavior and examples.
        """+ACTION;}
}
