/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Original prepared SCENIC+ TF2G/R2G/eGRN/activity workflow; family admission is pending. */
public final class ScenicPlusNetworkCli {
    private ScenicPlusNetworkCli(){ }
    static final String PROFILE="scenicplus-1.0a2-network-v1";
    static final String COMPLETE="COMPLETE_SCOPED_SCENICPLUS_NETWORK";
    static final String RESOURCE="/org/jlinalg/omics/scenicplus-network/";
    static final List<String> SOURCES=List.of("worker.py","r2g_runtime.py","SOURCE-PROFILE.json");
    static final Map<String,String> INPUT_FILES=Map.of("expression","expression.tsv","accessibility","accessibility.tsv","search_space","search_space.tsv","tfs","tfs.txt","cistromes","cistromes.h5ad","ranking_db","motifs.regions_vs_motifs.rankings.feather");
    static final String ACTION="PRIVATE CAPTURE: licensed vendor traces may appear; follow accepted private academic use and copying/transfer terms. SKIP3: restore the exact permitted private runtime/reference/input or retain the skipped dependent analysis. FAIL2: preserve raw/partial/failed evidence, fix the named cause and retry NEW output. Read OUTCOME-WARNINGS.md, worker/report.json and worker/out/field-ledger.tsv; successful public calls do not establish every inner fit or make NaN/Inf reportable. The known masked source failure is not repaired or silently disabled. Original unpaired/metacell preparation, motif discovery/resource scanning, global dispatch, catalog admission and whole-paper reproduction remain unfinished; zero new family/review credit. SHA256 is integrity, not execution authentication.";
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error){
        Path root=null;
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println("SCENIC+ prepared network: --request JSON | --inspect CAPTURE. Schema1 RUN: profile="+PROFILE+", operation=NETWORK, python, NEW out, inputs{expression,accessibility,search_space,tfs,cistromes,ranking_db}, explicit options.interface=CLI_TSV|IN_MEMORY_API, options.tf_seed and options.egrn.seed; optional source egrn.merge_eRegulons. Prepared finite cell-by-feature TSVs and original local Boolean AnnData cistromes plus Feather-v2 ranking references. Original TF2G, GBM R2G, GSEA eGRN, triplet scoring and gene/region AUCell; one worker. Full VERIFY and REPLAY perform fresh scientific computation into a NEW disjoint witness; --inspect checks saved closure only. Requires complete source-built JAR and already permitted unchanged Linux runtime. No whole-tool/catalog/biological qualification is implied.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--inspect")){inspect(Path.of(args[1]));out.println("Saved prepared-network closure checked; no new original computation executed. "+ACTION);return 0;}
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
            var process=map();ScenicPlusNetworkRuntime.execute(root,request,process);
            write(root.resolve("worker-process.json"),process);
            var summary=read(root.resolve("worker/out/SUMMARY.json"));
            long unavailableStatistics=number(summary,"module_statistics_unavailable_fields");
            String moduleOutcome="Unavailable/nonfinite source module GSEA-statistic fields: "+unavailableStatistics+". Read worker/out/module-ledger.jsonl; no NES or p-value is inferred from a merged regulon or a successful call.";
            ScenicWorkflowFiles.text(root.resolve("MODULE-WARNINGS.md"),"# Source module statistics\n\n"+moduleOutcome+"\n\n"+ACTION+"\n");
            if(unavailableStatistics>0)out.println("WARNING: "+moduleOutcome);
            String outcome="Nonfinite numerical fields: "+number(summary,"nonfinite_numerical_fields")+"; undefined R2G rho rows: "+number(summary,"nonfinite_rho_rows")+"; unreturned candidates: "+number(summary,"unreturned_candidates")+". Read worker/out/field-ledger.tsv; preserve undefined outputs and do not infer per-target fit status.";
            ScenicWorkflowFiles.text(root.resolve("OUTCOME-WARNINGS.md"),"# Original SCENIC+ network field outcomes\n\n"+outcome+"\n\n"+ACTION+"\n");
            if(number(summary,"nonfinite_numerical_fields")>0||number(summary,"unreturned_candidates")>0)out.println("WARNING: "+outcome);
            if(parent!=null){
                require(same(parentSeal,pin(parent.resolve("manifest.json"))),"Read-only parent manifest changed during refit");inspect(parent);
                for(String name:List.of("worker/out/scientific.jsonl","worker/out/tf2g.tsv","worker/out/r2g.tsv","worker/out/eRegulons.tsv","worker/out/gene-activity.tsv","worker/out/region-activity.tsv","worker/out/module-ledger.jsonl","worker/out/field-ledger.tsv","worker/out/tf-selection.json","worker/out/interface-boundaries.json","worker/out/interface-boundaries-complete.json","worker/out/unreturned-candidates.tsv","worker/out/SUMMARY.json","worker/out/invocation.json","runtime-before.json"))
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
        require("RUN".equals(raw.get("mode"))&&PROFILE.equals(raw.get("profile"))&&"NETWORK".equals(raw.get("operation")),"Require schema1 RUN NETWORK and the exact SCENIC+ profile");
        var result=new LinkedHashMap<>(raw);for(String key:List.of("python","out"))result.put(key,resolve(source,text(raw.get(key),key)).toString());
        var input=object(raw.get("inputs"));require(input.keySet().equals(INPUT_FILES.keySet()),"Declare exactly expression/accessibility/search_space/tfs/cistromes/ranking_db");var normalized=map();
        for(String key:input.keySet())normalized.put(key,resolve(source,text(input.get(key),key)).toString());result.put("inputs",normalized);
        var options=object(raw.get("options"));keys(options,Set.of("interface","tf_method","tf_seed","r2g_correlation","mask_expr_dropout","egrn","activity"));
        String sourceInterface=text(options.get("interface"),"explicit interface: CLI_TSV or IN_MEMORY_API");require(Set.of("CLI_TSV","IN_MEMORY_API").contains(sourceInterface),"Choose explicit original interface CLI_TSV or IN_MEMORY_API; author interfaces are not inferred");
        require(options.containsKey("tf_seed"),"Declare explicit uint32 TF2G seed; unknown author seeds are not inferred");
        var e=object(options.get("egrn"));keys(e,Set.of("seed","is_extended","order_regions_to_genes_by","order_TFs_to_genes_by","gsea_n_perm","quantiles","top_n_regionTogenes_per_gene","top_n_regionTogenes_per_region","binarize_using_basc","min_regions_per_gene","rho_dichotomize_tf2g","rho_dichotomize_r2g","rho_dichotomize_eregulon","keep_only_activating","rho_threshold","NES_thr","adj_pval_thr","min_target_genes","merge_eRegulons"));
        require(e.containsKey("seed"),"Declare explicit uint32 eGRN seed; unknown author seeds are not inferred");
        var egrn=map("seed",integer(e.get("seed"),0,0xffffffffL),"gsea_n_perm",integer(e.getOrDefault("gsea_n_perm",1000),1,10000000),"min_regions_per_gene",integer(e.getOrDefault("min_regions_per_gene",0),0,10000000),"min_target_genes",integer(e.getOrDefault("min_target_genes",5),2,10000000));
        for(String flag:List.of("is_extended","binarize_using_basc","keep_only_activating"))egrn.put(flag,ScenicWorkflowContracts.bool(e,flag,false));
        for(String flag:List.of("rho_dichotomize_tf2g","rho_dichotomize_r2g","rho_dichotomize_eregulon"))egrn.put(flag,ScenicWorkflowContracts.bool(e,flag,true));
        egrn.put("merge_eRegulons",ScenicWorkflowContracts.bool(e,"merge_eRegulons",true));
        egrn.put("quantiles",ScenicWorkflowContracts.decimals(e.getOrDefault("quantiles",List.of(.85,.9)),1));
        egrn.put("top_n_regionTogenes_per_gene",ScenicWorkflowContracts.integers(e.getOrDefault("top_n_regionTogenes_per_gene",List.of(5,10,15))));
        egrn.put("top_n_regionTogenes_per_region",ScenicWorkflowContracts.integers(e.getOrDefault("top_n_regionTogenes_per_region",List.of())));
        egrn.put("rho_threshold",fraction(e.getOrDefault("rho_threshold",.03)));egrn.put("adj_pval_thr",fraction(e.getOrDefault("adj_pval_thr",1)));
        double nes=decimal(e.getOrDefault("NES_thr",0)).doubleValue();require(Double.isFinite(nes),"Finite original NES threshold required");egrn.put("NES_thr",nes);
        for(String order:List.of("order_regions_to_genes_by","order_TFs_to_genes_by")){String value=text(e.getOrDefault(order,"importance"),order);require(Set.of("importance","rho","importance_x_rho","importance_x_abs_rho").contains(value),"Unknown source ranking field");egrn.put(order,value);}
        String method=text(options.getOrDefault("tf_method","GBM"),"tf_method"),correlation=text(options.getOrDefault("r2g_correlation","SR"),"r2g_correlation");require(Set.of("GBM","RF").contains(method)&&Set.of("SR","PR").contains(correlation),"Unknown original TF method/R2G correlation");
        var a=options.containsKey("activity")?object(options.get("activity")):map();keys(a,Set.of("auc_threshold","normalize"));var activity=map("auc_threshold",fraction(a.getOrDefault("auc_threshold",.05)),"normalize",ScenicWorkflowContracts.bool(a,"normalize",false));
        result.put("options",map("interface",sourceInterface,"tf_method",method,"tf_seed",integer(options.get("tf_seed"),0,0xffffffffL),"r2g_correlation",correlation,"mask_expr_dropout",ScenicWorkflowContracts.bool(options,"mask_expr_dropout",false),"egrn",egrn,"activity",activity));
        var limits=raw.containsKey("limits")?object(raw.get("limits")):map();
        var defaults=map("maximum_input_bytes",2147483648L,"maximum_member_bytes",2147483648L,"maximum_output_bytes",8589934592L,"maximum_files",50000L,"timeout_seconds",1800L,"maximum_cells",1000000L,"maximum_genes",1000000L,"maximum_regions",10000000L,"maximum_pairs",10000000L,"maximum_matrix_values",100000000L);
        var ceilings=map("maximum_input_bytes",68719476736L,"maximum_member_bytes",34359738368L,"maximum_output_bytes",274877906944L,"maximum_files",1000000L,"timeout_seconds",86400L,"maximum_cells",10000000L,"maximum_genes",10000000L,"maximum_regions",100000000L,"maximum_pairs",1000000000L,"maximum_matrix_values",10000000000L);
        keys(limits,defaults.keySet());var bound=map();for(var entry:defaults.entrySet())bound.put(entry.getKey(),integer(limits.getOrDefault(entry.getKey(),entry.getValue()),1,Long.parseLong(ceilings.get(entry.getKey()).toString())));result.put("limits",bound);return result;
    }
    static double fraction(Object value)throws IOException{double v=decimal(value).doubleValue();require(Double.isFinite(v)&&v>=0&&v<=1,"Require an original threshold in [0,1]");return v;}
    static byte[] resource(String name)throws IOException{
        try(var stream=ScenicPlusNetworkCli.class.getResourceAsStream(RESOURCE+name)){require(stream!=null,"Missing packaged SCENIC+ caller/profile: "+name);return stream.readAllBytes();}
    }
    static Map<String,Object> producer()throws IOException{
        try{
            Path jar=Path.of(ScenicPlusNetworkCli.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new ScenicWorkflowContracts.Unavailable("Use a complete source-built project JAR; loose/mixed classes are unqualified");
            var seen=new HashSet<Class<?>>();for(Class<?> type:List.of(ScenicPlusNetworkCli.class,ScenicPlusNetworkRuntime.class,ScenicWorkflowProcess.class,ScenicWorkflowFiles.class,ScenicWorkflowContracts.class,GprofilerWorkflowContracts.class,GprofilerJson.class,OwnedProcesses.class,MrPressoWorkflowContracts.class))origins(type,jar,seen);
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
