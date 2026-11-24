/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;
import static org.jlinalg.cli.FastqtlWorkflowContracts.*;
import static org.jlinalg.cli.FastqtlWorkflowContracts.ACTION;
import static org.jlinalg.cli.FastqtlWorkflowContracts.COMPLETE;
import static org.jlinalg.cli.FastqtlWorkflowContracts.PROFILE;

/** Actual prepared FastQTL fits, exact producer, retained source outputs and fresh verification. */
public final class FastqtlToolsCli {
    private FastqtlToolsCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        Path root=null;
        try {
            if(Arrays.equals(args,new String[]{"--help"})){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved FastQTL closure checked; no new original computation executed. "+ACTION);return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --inspect CAPTURE or --help");
            Path file=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(file,16777216);var source=object(GprofilerJson.read(raw));String mode=text(source.get("mode"),"mode");Path parent=null;Map<String,Object> parentSeal=null,request;
            if(Set.of("VERIFY","REPLAY").contains(mode)) {
                keys(source,Set.of("schema_version","mode","analysis","out","runtime_home"));integer(source.get("schema_version"),1,1);parent=FastqtlWorkflowContracts.resolve(file,text(source.get("analysis"),"analysis"));request=new LinkedHashMap<>(inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));request.put("out",FastqtlWorkflowContracts.resolve(file,text(source.get("out"),"out")).toString());
                if(source.containsKey("runtime_home"))request.put("runtime_home",FastqtlWorkflowContracts.resolve(file,text(source.get("runtime_home"),"runtime_home")).toString());
                Path target=Path.of(text(request.get("out"),"out"));require(!target.startsWith(parent)&&!parent.startsWith(target),"Read-only parent and fresh computation must be disjoint");
            }else request=validate(source,file);
            Path target=Path.of(text(request.get("out"),"out")),home=Path.of(text(request.get("runtime_home"),"runtime_home"));require(!target.startsWith(home)&&!home.startsWith(target),"Output and runtime must be disjoint");for(Object v:object(request.get("inputs")).values())require(!Path.of(text(v,"input")).startsWith(target),"Original inputs must not lie inside output");
            root=newOutput(target);Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);ScenicWorkflowFiles.text(root.resolve("WARNINGS.md"),"# FASTQTL: REVIEW BEFORE USING RESULTS\n\n"+ACTION+"\n\nNominal uses the source's supplied covariate count; permutation uses its residualizer's retained covariate count and fitted effective DF. These are not necessarily the matrix rank. Nominal threshold filters pairs, not genes, and omitted pairs can include undefined P values; absence is not evidence of no association. Original empirical and beta-adjusted P values are not FDR/q-values. No-cis rows are explicit no-result rows. Any printed nonfinite source statistic leaves the capture failed and unadmitted; its original bytes remain intact. Limits are periodically monitored, not hard OS RAM/process quotas. Original stdout is bounded at 64 MiB for completion review.\n");
            write(root.resolve("producer.json"),producer());FastqtlWorkflowRuntime.identity(request);var captures=map();
            for(String role:object(request.get("inputs")).keySet()){Path input=parent==null?Path.of(text(object(request.get("inputs")).get(role),role)):FastqtlWorkflowContracts.input(parent,role);Path destination=FastqtlWorkflowContracts.input(root,role);capture(input,destination,request,captures,root.relativize(destination).toString().replace('\\','/'));}
            write(root.resolve("input-inventory.json"),captures);verifyInputs(root,captures);var attributes=attributes(root,captures);var audit=FastqtlWorkflowData.inputs(root,request);write(root.resolve("input-audit.json"),audit.summary());
            Path captured=root;FastqtlWorkflowRuntime.execute(root,request,()->unchanged(captured,attributes));verifyInputs(root,captures);var scientific=FastqtlWorkflowData.outputs(root,request,audit);err.println("FASTQTL OUTPUT REVIEW: "+new String(json(scientific),java.nio.charset.StandardCharsets.UTF_8)+ACTION);
            require(number(scientific,"nonfinite_rows")==0,"Original FastQTL returned nonfinite statistics; no result is admitted. Read result-audit.tsv; original tokens are preserved");FastqtlWorkflowProcess.footprint(root,request);
            if(parent!=null) {
                inspect(parent);require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent changed during fresh computation");var matches=map();
                for(String name:List.of("input-audit.json","scientific-audit.json","result-audit.tsv","runtime-before.json","worker/result.txt")){var p=pin(root.resolve(name));require(same(p,pin(parent.resolve(name))),"Fresh "+mode+" differs: "+name);matches.put(name,p);}
                write(root.resolve("fresh-comparison.json"),map("mode",mode,"parent_manifest",parentSeal,"matched_files",matches,"dynamic_paths_timings_and_logs_compared",false,"new_original_computation_executed",true));
            }
            var manifest=map("schema_version",1,"status",COMPLETE,"profile",PROFILE,"operation",request.get("operation"),"mode",mode,"private_capture",true,"scientific_summary",scientific,"new_family_credit",0,"new_reviews",0,"whole_goal_complete",false,"inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);write(root.resolve("COMPLETED.json"),map("status",COMPLETE,"manifest",pin(root.resolve("manifest.json"))));inspect(root);err.println("FASTQTL WARNINGS: "+root.resolve("WARNINGS.md"));out.println(COMPLETE+" "+root);return 0;
        }catch(Exception failure) {
            int code=failure instanceof FastqtlWorkflowRuntime.Unavailable||failure instanceof ScenicWorkflowContracts.Unavailable?3:2;String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
            if(root!=null)try {if(Files.exists(root.resolve("COMPLETED.json")))Files.move(root.resolve("COMPLETED.json"),root.resolve("UNADMITTED-COMPLETED.json"));write(root.resolve("analysis-status.json"),map("status",status,"cause",failure.toString(),"required_action",ACTION));ScenicWorkflowFiles.text(root.resolve("FAILURE.md"),"# "+status+"\n\n"+failure+"\n\n"+ACTION+"\n");err.println(status+": "+root.resolve("FAILURE.md"));}catch(Exception secondary){failure.addSuppressed(secondary);}
            err.println(status+": "+failure+"\n"+ACTION);return code;
        }
    }
    private static Map<String,BasicFileAttributes> attributes(Path root,Map<String,Object> captures)throws IOException {var result=new TreeMap<String,BasicFileAttributes>();for(String name:captures.keySet())result.put(name,Files.readAttributes(root.resolve(relative(name)),BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS));return result;}
    private static void unchanged(Path root,Map<String,BasicFileAttributes> expected)throws IOException {for(var entry:expected.entrySet()){Path file=root.resolve(entry.getKey());var before=entry.getValue();var now=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(now.isRegularFile()&&!now.isSymbolicLink()&&now.size()==before.size()&&now.lastModifiedTime().equals(before.lastModifiedTime())&&Objects.equals(now.fileKey(),before.fileKey()),"Captured input changed while original worker was running: "+file);}}
    private static void verifyInputs(Path root,Map<String,Object> captures)throws IOException {for(var e:captures.entrySet())require(same(e.getValue(),pin(root.resolve(relative(e.getKey())))),"Changed captured input: "+e.getKey());}
    static Map<String,Object> producer()throws IOException {
        try {
            Path jar=Path.of(FastqtlToolsCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new FastqtlWorkflowRuntime.Unavailable("Use the complete source-built executable project JAR; loose classes are not a qualified analysis producer");
            var seen=new HashSet<Class<?>>();for(Class<?> c:List.of(FastqtlToolsCli.class,FastqtlWorkflowContracts.class,FastqtlWorkflowData.class,FastqtlWorkflowRuntime.class,FastqtlWorkflowProcess.class,OwnedProcesses.class,JLinAlgCli.class,GprofilerWorkflowContracts.class,GprofilerJson.class,ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,htsjdk.tribble.readers.TabixReader.class,htsjdk.samtools.util.BlockCompressedInputStream.class))origins(c,jar,seen);return map("kind","EXACT_COMPLETE_PROJECT_JAR","identity",pin(jar),"profile",PROFILE,"authentication",false);
        }catch(URISyntaxException error){throw new IOException("Cannot resolve exact producer",error);}
    }
    private static void origins(Class<?> c,Path jar,Set<Class<?>> seen)throws IOException,URISyntaxException {if(!seen.add(c))return;require(jar.equals(Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()),"Mixed class origin: "+c.getName());for(Class<?> n:c.getNestMembers())origins(n,jar,seen);}
    static Map<String,Object> inspect(Path folder)throws IOException {
        Path root=folder.toAbsolutePath().normalize();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),"Require physical completed capture");var seal=read(root.resolve("COMPLETED.json"));var manifest=read(root.resolve("manifest.json"));require(COMPLETE.equals(seal.get("status"))&&same(seal.get("manifest"),pin(root.resolve("manifest.json")))&&COMPLETE.equals(manifest.get("status"))&&PROFILE.equals(manifest.get("profile")),"Missing/changed completed FastQTL manifest");
        var request=read(root.resolve("canonical-request.json"));require(same(request,validate(request,root.resolve("canonical-request.json")))&&root.toString().equals(request.get("out")),"Noncanonical request/output binding");require(same(producer(),read(root.resolve("producer.json"))),"Restore exact complete producer JAR; do not refresh saved hashes");require(same(manifest.get("inventory"),inventory(root,request)),"Capture bytes/file population changed");var captures=map();for(String role:object(request.get("inputs")).keySet()){Path path=FastqtlWorkflowContracts.input(root,role);captures.put(root.relativize(path).toString().replace('\\','/'),pin(path));}require(same(captures,read(root.resolve("input-inventory.json"))),"Changed input inventory roles/identities");var inputs=FastqtlWorkflowData.inputs(root,request);require(same(inputs.summary(),read(root.resolve("input-audit.json"))),"Changed derived input audit");var scientific=FastqtlWorkflowData.inspectOutputs(root,request,inputs);require(same(scientific,read(root.resolve("scientific-audit.json")))&&same(manifest.get("scientific_summary"),scientific)&&number(scientific,"nonfinite_rows")==0,"Unreportable/changed scientific summary");return request;
    }
    static String help(){return """
        fastqtl-tools --request JSON | --inspect CAPTURE | --help
        Schema1 RUN: profile=fastqtl-2.184-v7-ubuntu-noble-prepared-v1,
        operation=NOMINAL|PERMUTATION, runtime_home, NEW out, assembly,
        region=chromosome:start-end (1-based inclusive), inputs and options.
        Inputs: vcf,vcf_index,phenotypes,phenotype_index,optional covariates.
        VCF/BED must be BGZF/TBI single-chromosome prepared matrices with the
        same unique sample ID sets. VCF: biallelic SNVs, DS or diploid GT.
        BED: #chrom/start/end/id then samples. Covariates: ID then samples;
        numeric finite rows only. No input conversion or ID repair occurs.
        Options (all explicit): seed,window,normal,maf_threshold,
        ma_sample_threshold,anchor_policy=bed-start-plus-one,input_reviewed=true,
        plus threshold for NOMINAL or permutations=[N]|[better,max]|[min,better,max].
        Linux x86_64 Java required for upstream execution (Windows: WSL).
        The pinned Ubuntu executable, COPYRIGHT and closed private ELF bundle
        must already exist; analysis never installs or downloads dependencies.
        VERIFY/REPLAY: schema_version=1,mode,analysis,NEW out,optional runtime_home.
        Both refit captured inputs upstream and compare exact scientific bytes.
        --inspect checks saved closure only with the exact complete producer.
        Exit0 scoped completed operation; exit3 unavailable dependency/capability
        skipped; exit2 validation/process/scientific failure. No-cis rows are
        explicit no results; nonfinite source statistics fail without repair.
        See docs/FASTQTL_WORKFLOW.md for installation, examples and restrictions.
        """+ACTION;}
}
