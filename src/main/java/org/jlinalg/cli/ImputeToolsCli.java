/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.genetics.RoundedGenotypeProbabilities;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;
import static org.jlinalg.cli.ImputeWorkflowContracts.*;
import static org.jlinalg.cli.ImputeWorkflowContracts.ACTION;
import static org.jlinalg.cli.ImputeWorkflowContracts.PROFILE;

/** Actual original IMPUTE2 operations with exact producer, captured inputs and fresh refits. */
public final class ImputeToolsCli {
    private ImputeToolsCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        Path root=null;
        try {
            if(Arrays.equals(args,new String[]{"--help"})){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved IMPUTE2 closure checked. No new original computation executed. "+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --inspect CAPTURE or --help");
            Path file=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(file,16777216);var source=object(GprofilerJson.read(raw));
            String mode=text(source.get("mode"),"mode");Path parent=null;Map<String,Object> parentSeal=null,request;
            if(Set.of("VERIFY","REPLAY").contains(mode)) {
                keys(source,Set.of("schema_version","mode","analysis","out","runtime_home"));integer(source.get("schema_version"),1,1);
                parent=ImputeWorkflowContracts.resolve(file,text(source.get("analysis"),"analysis"));request=new LinkedHashMap<>(inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));
                request.put("out",ImputeWorkflowContracts.resolve(file,text(source.get("out"),"out")).toString());
                if(source.containsKey("runtime_home"))request.put("runtime_home",ImputeWorkflowContracts.resolve(file,text(source.get("runtime_home"),"runtime_home")).toString());
                Path target=Path.of(text(request.get("out"),"out"));require(!target.startsWith(parent)&&!parent.startsWith(target),"Read-only parent and fresh refit must be disjoint");
            }else request=validate(source,file);
            Path target=Path.of(text(request.get("out"),"out")),home=Path.of(text(request.get("runtime_home"),"runtime_home"));
            require(!target.startsWith(home)&&!home.startsWith(target),"Output and private runtime must be disjoint");
            for(Object path:object(request.get("inputs")).values())require(!Path.of(text(path,"input")).startsWith(target),"Original input must not lie inside output");
            root=newOutput(target);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            ScenicWorkflowFiles.text(root.resolve("WARNINGS.md"),"# IMPUTE2 PRIVATE CAPTURE — REVIEW REQUIRED\n\n"+ACTION+"\n\nThe original default MCMC is retained (30 total iterations, 10 burn-in, k=80, k_hap=500, 250kb buffer); other source defaults also apply. No wrapper threshold removes variants. PREPHASE retains study-strand coding; apply the explicit strand file once in a later imputation call. Source default typed calls, including missing ones, remain unchanged unless the request explicitly chooses a reimputation policy. `fill-missing-at-source-threshold` uses the original call threshold (0.9) and carries the original documentation's downstream association caution. Read worker/result_warnings and worker/result_summary.\n");
            write(root.resolve("producer.json"),producer());var captures=map();
            for(String role:roles(operation(request))) {
                Path input=parent==null?Path.of(text(object(request.get("inputs")).get(role),role)):ImputeWorkflowContracts.input(parent,role);
                capture(input,ImputeWorkflowContracts.input(root,role),request,captures,"inputs/"+role);
            }
            write(root.resolve("input-inventory.json"),captures);verifyInputs(root,captures);var attributes=attributes(root,captures);
            Map<String,Object> audit=ImputeWorkflowData.inputs(root,request);write(root.resolve("input-audit.json"),audit);
            Path capturedRoot=root;var process=map();ImputeWorkflowRuntime.execute(root,request,process,()->unchanged(capturedRoot,attributes));
            verifyInputs(root,captures);var scientific=ImputeWorkflowData.outputs(root,request,audit);write(root.resolve("scientific-audit.json"),scientific);ImputeWorkflowProcess.footprint(root,request);
            String outcome="CONDITIONAL BOUNDS ONLY: unit mass is not established by printed GEN tokens. The original v2.3.2 precision controls support censoring probabilities below one output unit to zero, followed by nearest rounding of nonzero values; nearest-only bounds were rejected. Typed input may intentionally carry subunit mass. Never use these bounds without establishing their premises for the intended analysis. No Java renormalization or repair occurs. Read scientific-audit.json and unreportable-genotypes.tsv. "+new String(GprofilerJson.write(scientific,1048576),java.nio.charset.StandardCharsets.UTF_8);
            ScenicWorkflowFiles.text(root.resolve("OUTCOME-WARNINGS.md"),"# IMPUTE2 OUTPUT REVIEW REQUIRED\n\n"+outcome+"\n\n"+ACTION+"\n");err.println("IMPUTE2 OUTPUT REVIEW: "+outcome);
            if(parent!=null) {
                inspect(parent);require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent changed during fresh refit");var comparison=map();
                var compare=new ArrayList<>(List.of("input-audit.json","scientific-audit.json","runtime-before.json"));
                if(!operation(request).equals("MERGE_REFERENCE")){compare.add("variant-audit.tsv");compare.add("unreportable-genotypes.tsv");if("rounding-bounds".equals(object(request.get("options")).get("genotype_audit")))compare.add("genotype-bounds.tsv");}
                for(String name:ImputeWorkflowData.scientificFiles(operation(request)))compare.add("worker/"+name);
                for(String name:compare){var pin=pin(root.resolve(name));require(same(pin,pin(parent.resolve(name))),"Fresh "+mode+" differs: "+name);comparison.put(name,pin);}
                write(root.resolve("fresh-comparison.json"),map("mode",mode,"parent_manifest",parentSeal,"matched_files",comparison,"dynamic_command_paths_and_timings_compared",false));
            }
            var manifest=map("schema_version",1,"status",COMPLETE,"profile",PROFILE,"operation",operation(request),"mode",mode,"private_capture",true,"scientific_summary",scientific,"new_family_credit",0,"new_reviews",0,"whole_goal_complete",false);
            manifest.put("inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("status",COMPLETE,"manifest",pin(root.resolve("manifest.json"))));
            inspect(root);err.println("IMPUTE2 WARNINGS REQUIRE REVIEW: "+root.resolve("WARNINGS.md"));out.println(COMPLETE+" "+root+"\n"+new String(GprofilerJson.write(scientific,1048576),java.nio.charset.StandardCharsets.UTF_8));return 0;
        }catch(Exception failure) {
            int code=failure instanceof ImputeWorkflowRuntime.Unavailable||failure instanceof ScenicWorkflowContracts.Unavailable?3:2;
            String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
            if(root!=null)try {
                if(Files.exists(root.resolve("COMPLETED.json")))Files.move(root.resolve("COMPLETED.json"),root.resolve("UNADMITTED-COMPLETED.json"));
                write(root.resolve("analysis-status.json"),map("status",status,"cause",failure.toString(),"private_capture",true,"required_action",ACTION));
                ScenicWorkflowFiles.text(root.resolve("FAILURE.md"),"# "+status+"\n\n"+failure+"\n\n"+ACTION+"\n");
                err.println(status+": "+root.resolve("FAILURE.md"));
            }catch(Exception secondary){failure.addSuppressed(secondary);}
            err.println(status+": "+failure+"\n"+ACTION);return code;
        }
    }
    private static Map<String,BasicFileAttributes> attributes(Path root,Map<String,Object> captures)throws IOException {
        var result=new TreeMap<String,BasicFileAttributes>();for(String name:captures.keySet())result.put(name,Files.readAttributes(root.resolve(relative(name)),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS));return result;
    }
    private static void unchanged(Path root,Map<String,BasicFileAttributes> expected)throws IOException {
        for(var entry:expected.entrySet()) {Path path=root.resolve(entry.getKey());var now=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);var before=entry.getValue();
            require(now.isRegularFile()&&!now.isSymbolicLink()&&now.size()==before.size()&&now.lastModifiedTime().equals(before.lastModifiedTime())&&Objects.equals(now.fileKey(),before.fileKey()),"Captured input changed while original worker was running: "+path);
        }
    }
    private static void verifyInputs(Path root,Map<String,Object> captures)throws IOException {for(var entry:captures.entrySet())require(same(entry.getValue(),pin(root.resolve(relative(entry.getKey())))),"Changed captured input: "+entry.getKey());}
    static Map<String,Object> producer()throws IOException {
        try {
            Path jar=Path.of(ImputeToolsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new ImputeWorkflowRuntime.Unavailable("Use a complete source-built project executable JAR; loose/mixed classes are unqualified");
            var seen=new HashSet<Class<?>>();for(Class<?> type:List.of(ImputeToolsCli.class,ImputeWorkflowContracts.class,ImputeWorkflowData.class,ImputeWorkflowRuntime.class,ImputeWorkflowProcess.class,RoundedGenotypeProbabilities.class,JLinAlgCli.class,GprofilerWorkflowContracts.class,GprofilerJson.class,ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,OwnedProcesses.class))origins(type,jar,seen);
            return map("kind","EXACT_COMPLETE_PROJECT_JAR","identity",pin(jar),"profile",PROFILE,"authentication",false);
        }catch(URISyntaxException e){throw new IOException("Cannot resolve exact producer",e);}
    }
    private static void origins(Class<?> type,Path jar,Set<Class<?>> seen)throws IOException,URISyntaxException {
        if(!seen.add(type))return;require(jar.equals(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()),"Mixed class origin: "+type.getName());for(Class<?> nested:type.getNestMembers())origins(nested,jar,seen);
    }
    static Map<String,Object> inspect(Path folder)throws IOException {
        Path root=folder.toAbsolutePath().normalize();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),"Require a physical completed IMPUTE2 capture");
        var seal=read(root.resolve("COMPLETED.json"));var manifest=read(root.resolve("manifest.json"));
        require(COMPLETE.equals(seal.get("status"))&&same(seal.get("manifest"),pin(root.resolve("manifest.json"))),"Missing/changed completed manifest");
        require(COMPLETE.equals(manifest.get("status"))&&PROFILE.equals(manifest.get("profile"))&&Boolean.TRUE.equals(manifest.get("private_capture")),"Wrong capture kind/status");
        var request=read(root.resolve("canonical-request.json"));require(same(request,validate(request,root.resolve("canonical-request.json")))&&root.toString().equals(request.get("out")),"Noncanonical saved request/output binding");
        require(same(producer(),read(root.resolve("producer.json"))),"Restore the exact complete producer JAR; do not refresh historical hashes");
        require(same(manifest.get("inventory"),inventory(root,request)),"Saved capture bytes/file population changed");verifyInputs(root,read(root.resolve("input-inventory.json")));
        require(same(manifest.get("scientific_summary"),read(root.resolve("scientific-audit.json"))),"Scientific summary differs");return request;
    }
    static String help(){return """
        impute-tools --request JSON | --inspect CAPTURE | --help
        Schema1 RUN, profile=impute2-2.3.2-static-autosomal-v1, operation:
        PREPHASE / IMPUTE_GENOTYPES / IMPUTE_PREPHASED / MERGE_REFERENCE.
        Declare runtime_home, NEW out, assembly, human autosome chromosome,
        license_accepted=true, exact inputs/options and optional bounded limits.
        Original Linux x86_64 v2.3.2 static executable and bundled Oxford LICENCE
        are pinned. Accept the actual bundled terms first; no runtime is included,
        installed or replaced. On Windows run the complete project JAR in WSL.
        PREPHASE inputs: map,study_genotypes. Imputation: map,reference_haps,
        reference_legend,strand and study_genotypes or study_haps. Panel merge:
        map,reference_haps,reference_legend,second_reference_haps,
        second_reference_legend. Plain or gzip input is detected by file bytes.
        Options: seed,Ne,interval_start,interval_end,parameter_policy:
        original-v2.3.2-defaults,sample_identity_policy:unlabelled-ordered-columns,
        input_reviewed:true,genotype_audit:summary|rounding-bounds. Unphased
        imputation also requires typed_genotype_policy:preserve-input|
        reimpute-all|fill-missing-at-source-threshold. No arbitrary CLI flags.
        VERIFY/REPLAY requests require schema_version,mode,analysis,NEW out and
        optional runtime_home. Both actually refit the parent's captured inputs
        and compare all declared scientific files, preserving the parent.
        --inspect checks saved closure only, with the exact producer JAR.
        Exit0 completed operation with review warnings; exit3 unavailable or
        forbidden dependency/capability skipped; exit2 input/process/audit failure.
        Read WARNINGS.md and FAILURE.md before using any output. No failed,
        skipped or partial capture supplies an admitted result. IMPUTE4/5,
        chromosome X, genotype/reference conversions and whole-paper workflows
        are not supplied by this profile. Rounding bounds audit existing GP;
        they assume unit mass and explicit subunit-zero censoring; they do not
        establish these premises or reconstruct original posteriors.
        """+ACTION;
    }
}
