/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;
import static org.jlinalg.cli.GenomicsemContracts.ACTION;
import static org.jlinalg.cli.GenomicsemContracts.COMPLETE;
import static org.jlinalg.cli.GenomicsemContracts.PROFILE;

/** Original prepared GenomicSEM fits, fresh VERIFY/REPLAY and portable saved inspection. */
public final class GenomicsemToolsCli {
    private GenomicsemToolsCli() { }
    public static void main(String[] args){int c=run(args,System.out,System.err);if(c!=0)System.exit(c);}
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved original GenomicSEM capture checked;no statistical refit. "+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON,--inspect CAPTURE or --help");
            Path file=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(file,16777216);var source=object(GprofilerJson.read(raw));String mode=text(source.get("mode"),"mode");var request=new LinkedHashMap<String,Object>();Path parent=null;Map<String,Object> parentSeal=null;
            if(Set.of("VERIFY","REPLAY").contains(mode)){
                keys(source,Set.of("schema_version","mode","analysis","out","runtime_home"));integer(source.get("schema_version"),1,1);parent=resolve(file.getParent(),text(source.get("analysis"),"analysis"));request.putAll(inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));
                request.put("out",resolve(file.getParent(),text(source.get("out"),"out")).toString());String home=source.containsKey("runtime_home")?resolve(file.getParent(),text(source.get("runtime_home"),"runtime_home")).toString():text(read(parent.resolve("runtime-binding.json")).get("runtime_home"),"original runtime home");request.put("runtime_home",home);
                var inputs=map();for(String role:GenomicsemContracts.roles(text(request.get("operation"),"operation")))inputs.put(role,GenomicsemContracts.input(parent,role).toString());request.put("inputs",inputs);
            }else request.putAll(GenomicsemContracts.validate(source));
            var validated=GenomicsemContracts.validate(request);Path target=resolve(file.getParent(),text(validated.get("out"),"out")),home=resolve(file.getParent(),text(validated.get("runtime_home"),"runtime_home"));require(!target.startsWith(home)&&!home.startsWith(target),"Runtime and NEW output must be disjoint");if(parent!=null)require(!target.startsWith(parent)&&!parent.startsWith(target),"Parent evidence and fresh output must be disjoint");
            root=newOutput(target);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            var canonical=GenomicsemContracts.canonical(validated);write(root.resolve("canonical-request.json"),canonical);write(root.resolve("source-origin.json"),map("source_request_address",file.toString(),"creation_address",root.toString(),"execution_mode",mode));
            ScenicWorkflowFiles.text(root.resolve("WARNINGS.md"),"# GENOMIC SEM: REVIEW BEFORE USING RESULTS\n\n"+ACTION+"\n\nUse results.tsv only with a valid COMPLETED.json,manifest and results_admitted=true status. inference.tsv is the selected typed audit view;original-result.rds,worker/tables and conditions retain original fields. The saved reader interprets typed exports,not opaque RDS contents,and does not independently refit statistics. Saturated models can legitimately have unavailable fit indices;their original NA fields remain unavailable. Some upstream fits use optim.force.converged;finite fields and warning checks are not a global-optimum or complete convergence certificate. Requested heterogeneity with no usable degrees of freedom leaves the operation unadmitted. A bounded P string is not an exact probability.\n");
            if(parent!=null)for(var pair:Map.of("canonical-request.json","parent-canonical-request.json","manifest.json","parent-manifest.json","COMPLETED.json","parent-COMPLETED.json","producer.json","parent-producer.json").entrySet())Files.copy(parent.resolve(pair.getKey()),root.resolve(pair.getValue()));
            write(root.resolve("RUNNING.json"),map("status","RUNNING_NO_RESULT_ADMITTED","mode",mode));write(root.resolve("producer.json"),producer());GenomicsemRuntime.sources(root,false);
            var captures=map();var bindings=map();for(String role:GenomicsemContracts.roles(text(validated.get("operation"),"operation"))){
                Path input=resolve(file.getParent(),text(object(validated.get("inputs")).get(role),role)),destination=GenomicsemContracts.input(root,role);require(!input.startsWith(root),"Original input must not reside under NEW output");capture(input,destination,canonical,captures,"inputs/"+GenomicsemContracts.filename(role));bindings.put(role,map("declared",object(validated.get("inputs")).get(role),"resolved",input.toRealPath().toString(),"captured","inputs/"+GenomicsemContracts.filename(role),"identity",pin(destination)));
            }
            write(root.resolve("input-binding.json"),bindings);write(root.resolve("input-inventory.json"),captures);var attributes=attributes(root,captures);var input=GenomicsemData.inputs(root,canonical);write(root.resolve("input-audit.json"),input.summary());
            Path captured=root;GenomicsemRuntime.execute(root,home,canonical,()->unchanged(captured,attributes));for(var e:captures.entrySet())require(same(e.getValue(),pin(root.resolve(e.getKey()))),"Captured input changed during original computation");
            var audit=GenomicsemAudit.evaluate(root,canonical,input,false);write(root.resolve("scientific-audit.json"),audit);err.println("GENOMIC SEM REVIEW: "+new String(json(audit),java.nio.charset.StandardCharsets.UTF_8)+ACTION);require(Boolean.TRUE.equals(audit.get("results_admitted")),"Original output did not satisfy the complete requested scientific/warning/smoothing policy;read scientific-audit.json");
            GenomicsemProcess.footprint(root,canonical);
            if(parent!=null){
                inspect(parent);require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent changed during fresh computation");var matched=map();var names=new ArrayList<>(List.of("input-audit.json","scientific-audit.json","inference.tsv","results.tsv","runtime-before.json","worker/table-index.json","worker/model-paths.json"));
                for(Object row:list(read(root.resolve("worker/table-index.json")).get("tables")))names.add(text(object(row).get("file"),"table"));
                for(String n:names){var v=pin(root.resolve(n));require(same(v,pin(parent.resolve(n))),"Fresh "+mode+" scientific/runtime bytes differ: "+n);matched.put(n,v);}
                write(root.resolve("fresh-comparison.json"),map("mode",mode,"parent_manifest",parentSeal,"matched_files",matched,"new_original_computation_executed",true,"logs_paths_timings_or_opaque_RDS_compared",false));
            }
            write(root.resolve("analysis-status.json"),map("status",COMPLETE,"results_admitted",true,"profile",PROFILE));var manifest=map("schema_version",1,"status",COMPLETE,"profile",PROFILE,"operation",canonical.get("operation"),"mode",mode,"scientific_summary",audit,"private_capture",true,"new_family_credit",0,"new_reviews",0,"whole_goal_complete",false,"inventory",inventory(root,canonical));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("status",COMPLETE,"manifest",pin(root.resolve("manifest.json"))));inspect(root);out.println(COMPLETE+" "+root);return 0;
        }catch(Exception|OutOfMemoryError failure){
            int code=failure instanceof GenomicsemRuntime.Unavailable||failure instanceof ScenicWorkflowContracts.Unavailable||failure instanceof NoSuchFileException?3:2;String state=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
            if(root!=null)try{
                for(String name:List.of("COMPLETED.json","analysis-status.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("UNADMITTED-"+name));
                write(root.resolve("analysis-status.json"),map("status",state,"results_admitted",false,"cause",failure.toString(),"required_action",ACTION));ScenicWorkflowFiles.text(root.resolve("FAILURE.md"),"# "+state+"\n\n"+failure+"\n\n"+ACTION+"\n");
            }catch(Exception second){failure.addSuppressed(second);}
            err.println(state+": "+failure+"\n"+ACTION);return code;
        }
    }
    static Map<String,Object> producer()throws IOException {
        try{Path p=Path.of(GenomicsemToolsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();if(!Files.isRegularFile(p)||!p.getFileName().toString().endsWith(".jar"))throw new GenomicsemRuntime.Unavailable("Build/use the complete executable JAR;scientific workflows do not run from loose classes");return map("complete_executable_JAR",pin(p),"source_descriptor",pin(GenomicsemRuntime.resource("SOURCE.json")));}
        catch(java.net.URISyntaxException e){throw new IOException("Cannot identify complete producer JAR",e);}
    }
    private static Map<String,BasicFileAttributes> attributes(Path root,Map<String,Object> captures)throws IOException {var a=new TreeMap<String,BasicFileAttributes>();for(String n:captures.keySet())a.put(n,Files.readAttributes(root.resolve(n),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS));return a;}
    private static void unchanged(Path root,Map<String,BasicFileAttributes> expected)throws IOException {
        for(var e:expected.entrySet()){var now=Files.readAttributes(root.resolve(e.getKey()),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);var before=e.getValue();require(now.isRegularFile()&&!now.isSymbolicLink()&&now.size()==before.size()&&now.lastModifiedTime().equals(before.lastModifiedTime())&&Objects.equals(now.fileKey(),before.fileKey()),"Captured input attributes changed during original fit");}
    }
    static Map<String,Object> inspect(Path path)throws IOException {
        Path root=path.toAbsolutePath().normalize();require(root.toRealPath().equals(root)&&Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Use a physical saved capture");require(!Files.exists(root.resolve("FAILURE.md")),"Retained failed/skipped capture is not a complete result");var seal=read(root.resolve("COMPLETED.json"));var manifest=read(root.resolve("manifest.json"));require(COMPLETE.equals(seal.get("status"))&&COMPLETE.equals(manifest.get("status"))&&PROFILE.equals(manifest.get("profile"))&&same(seal.get("manifest"),pin(root.resolve("manifest.json"))),"Missing/changed completed GenomicSEM seal");
        var request=read(root.resolve("canonical-request.json"));require(same(request,GenomicsemContracts.canonical(GenomicsemContracts.validate(request))),"Noncanonical saved scientific request");require(same(producer(),read(root.resolve("producer.json"))),"Restore exact complete producer JAR;never refresh its old hash");require(same(manifest.get("inventory"),inventory(root,request)),"Capture bytes or namespace changed");GenomicsemRuntime.sources(root,true);GenomicsemRuntime.namespace(root,"namespace-before.tsv");GenomicsemRuntime.namespace(root,"namespace-after.tsv");
        var status=read(root.resolve("analysis-status.json"));require(COMPLETE.equals(status.get("status"))&&Boolean.TRUE.equals(status.get("results_admitted")),"Saved result is unadmitted");
        var origin=read(root.resolve("source-origin.json"));var original=object(GprofilerJson.read(bytes(root.resolve("source-request.json"),16777216)));String mode=text(origin.get("execution_mode"),"mode");
        if(mode.equals("RUN"))require(same(GenomicsemContracts.canonical(GenomicsemContracts.validate(original)),request),"Source/canonical scientific request differs");else{
            require(Set.of("VERIFY","REPLAY").contains(mode)&&mode.equals(original.get("mode"))&&Files.isRegularFile(root.resolve("fresh-comparison.json")),"Missing fresh replay/verify provenance");
            require(same(request,read(root.resolve("parent-canonical-request.json")))&&same(read(root.resolve("parent-producer.json")),read(root.resolve("producer.json")))&&same(read(root.resolve("parent-COMPLETED.json")).get("manifest"),pin(root.resolve("parent-manifest.json"))),"Fresh operation changed captured parent scientific request/producer/seal");
            var comparison=read(root.resolve("fresh-comparison.json"));require(mode.equals(comparison.get("mode"))&&Boolean.TRUE.equals(comparison.get("new_original_computation_executed"))&&same(comparison.get("parent_manifest"),pin(root.resolve("parent-manifest.json"))),"Changed fresh original execution provenance");for(var e:object(comparison.get("matched_files")).entrySet()){relative(e.getKey());require(same(e.getValue(),pin(root.resolve(e.getKey()))),"Changed compared fresh scientific file");}
            var expectedCompared=new HashSet<>(Set.of("input-audit.json","scientific-audit.json","inference.tsv","results.tsv","runtime-before.json","worker/table-index.json","worker/model-paths.json"));for(Object row:list(read(root.resolve("worker/table-index.json")).get("tables")))expectedCompared.add(text(object(row).get("file"),"table"));require(expectedCompared.equals(object(comparison.get("matched_files")).keySet()),"Incomplete fresh scientific/runtime comparison population");
        }
        var captures=read(root.resolve("input-inventory.json"));require(captures.keySet().equals(GenomicsemContracts.roles(text(request.get("operation"),"operation")).stream().map(r->"inputs/"+GenomicsemContracts.filename(r)).collect(java.util.stream.Collectors.toSet())),"Changed input role inventory");for(var e:captures.entrySet())require(same(e.getValue(),pin(root.resolve(e.getKey()))),"Changed captured input");
        var bindings=read(root.resolve("input-binding.json"));require(bindings.keySet().equals(GenomicsemContracts.roles(text(request.get("operation"),"operation"))),"Changed source-input binding roles");for(var entry:bindings.entrySet()){var b=object(entry.getValue());String captured="inputs/"+GenomicsemContracts.filename(entry.getKey());require(captured.equals(b.get("captured"))&&same(b.get("identity"),captures.get(captured)),"Changed source/captured input identity binding");if(mode.equals("RUN"))require(Objects.equals(b.get("declared"),object(original.get("inputs")).get(entry.getKey())),"Changed original declared input path");}
        var input=GenomicsemData.inputs(root,request);require(same(input.summary(),read(root.resolve("input-audit.json"))),"Changed derived input audit");var audit=GenomicsemAudit.evaluate(root,request,input,true);require(Boolean.TRUE.equals(audit.get("results_admitted"))&&same(audit,read(root.resolve("scientific-audit.json")))&&same(audit,manifest.get("scientific_summary")),"Changed or unreportable derived scientific audit");require(same(read(root.resolve("runtime-before.json")),read(root.resolve("runtime-after.json"))),"Observed runtime changed during original fit");return request;
    }
    static String help(){return """
        genomicsem-tools --request JSON | --inspect CAPTURE | --help
        Original pinned GenomicSEM prepared COMMONFACTOR,USERMODEL,
        COMMONFACTOR_GWAS,USER_GWAS,COMMONFACTOR_TSEM,USER_TSEM.
        RUN schema1: mode,profile,operation,runtime_home,NEW out,inputs,options,
        optional monitored limits. Matrices: id then ordered column labels;
        S/I use trait labels,V uses T1:T1,T1:T2,...,T2:T2,... lower-column vech.
        GWAS first columns SNP,CHR,BP,MAF,A1,A2;TSEM Gene,Panel,HSQ;
        relative beta.<trait> and se.<trait> orders must each equal S order.
        VERIFY/REPLAY schema1: mode,analysis,NEW out,optional runtime_home.
        Both execute a fresh original fit and compare scientific/runtime bytes.
        --inspect is read only and checks typed exports,not opaque RDS contents.
        Exact full producer JAR required. Windows R4.6.1 original execution;
        portable Java input/output checks;Linux upstream dependency skips3.
        results.tsv is the human-readable selected inference table;use it only
        with a valid completed seal and results_admitted=true. Raw NA/bounded
        fields remain literal;no missing fit statistic is filled in.
        Exit0 scoped complete;3 unavailable dependency/capability skipped;
        2 failure with no complete result. Existing output is never overwritten.
        No munging,LDSC,allele alignment,FUSION preparation,stratified enrichment,
        analytic-mode GWAS,historical author edition or whole-paper claim here.
        See docs/GENOMICSEM_WORKFLOW.md for full schema,installation and examples.
        """+ACTION;}
}
