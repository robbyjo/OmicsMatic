/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Closed saved captures and independent fresh-fit comparison; qualification is separate. */
final class ScenicWorkflowAudit {
    private ScenicWorkflowAudit(){ }
    static final List<String> SCIENTIFIC=List.of("worker/out/invocation.json","worker/out/tf-selection.json","worker/out/adjacencies.json","worker/out/modules.jsonl","worker/out/motif-enrichment.json","worker/out/regulons.jsonl","worker/out/SUMMARY.json","activity/SUMMARY.json");
    static void fieldOutcomes(Path root,Map<String,Object> request)throws IOException {
        var summary=read(root.resolve("worker/out/SUMMARY.json"));
        require(number(summary,"targets")==number(summary,"fitted_targets"),"Original target ledger contains an incomplete fit");
        var targetRows=jsonLines(root.resolve("worker/out/target-ledger.jsonl"));var targets=new HashSet<String>();
        var invocation=read(root.resolve("worker/out/invocation.json"));var originalOptions=object(request.get("options"));
        require(same(invocation.get("modules"),originalOptions.get("modules"))&&same(invocation.get("ctx"),originalOptions.get("ctx")),"Source module/CTX invocation differs from declared options");
        var grn=object(invocation.get("grn"));var expectedRegressor="GRNBOOST2".equals(originalOptions.get("method"))?map("learning_rate",.01,"n_estimators",5000,"max_features",.1,"subsample",.9):map("n_jobs",1,"n_estimators",1000,"max_features","sqrt");
        require("arboreto.core.infer_partial_network".equals(grn.get("function"))&&Boolean.TRUE.equals(grn.get("include_meta"))&&number(grn,"early_stop_window_length")==25&&same(grn.get("seed"),originalOptions.get("seed"))&&originalOptions.get("method").equals(grn.get("method"))&&same(grn.get("regressor_kwargs"),expectedRegressor),"Original GRN invocation differs from declared exact method/defaults/seed");
        require(targetRows.size()==number(summary,"targets"),"Target ledger population differs");
        long edges=0;for(int index=0;index<targetRows.size();index++){
            var row=targetRows.get(index);require(number(row,"index")==index&&targets.add(text(row.get("target"),"target"))&&"ORIGINAL_FITTED".equals(row.get("status"))&&number(row,"n_estimators")>0,"Invalid or incomplete original target fit");edges=Math.addExact(edges,number(row,"edge_count"));
        }
        require(edges==number(summary,"adjacency_rows"),"Adjacency row count differs from target fit ledger");
        require(targetRows.stream().map(r->r.get("target")).toList().equals(list(invocation.get("target_genes"))),"Target ledger order differs from original gene population");
        var contextRows=jsonLines(root.resolve("worker/out/ctx-ledger.jsonl"));
        require(contextRows.size()==number(summary,"ctx_tasks")&&contextRows.size()==number(summary,"modules")*list(object(request.get("inputs")).get("databases")).size(),"CTX task population differs");
        long enriched=0;var tasks=new HashSet<String>();
        for(var row:contextRows){String status=text(row.get("status"),"CTX outcome");require(Set.of("ORIGINAL_ENRICHED","SOURCE_SKIPPED_GENE_COVERAGE","SOURCE_NO_ANNOTATED_ENRICHED_MOTIFS").contains(status),"Original CTX task failed");
            require(tasks.add(text(row.get("database"),"database")+"\u0000"+number(row,"index")),"Duplicate CTX task identity");enriched=Math.addExact(enriched,number(row,"enriched_rows"));
            for(Object entry:list(row.get("logs"))){String level=text(object(entry).get("level"),"log level");require(!Set.of("ERROR","CRITICAL").contains(level),"Original CTX logged an error; output is unadmitted");}
        }
        require(enriched==number(summary,"enriched_rows"),"CTX enrichment row count differs from complete task ledger");
        require(modelCount(root.resolve("worker/out/modules.jsonl"))==number(summary,"modules"),"Module export differs");
        require(ScenicWorkflowActivity.signatures(root).size()==number(summary,"regulons"),"Regulon export differs");
        var activity=read(root.resolve("activity/SUMMARY.json"));var policies=object(request.get("policies"));
        if(number(summary,"regulons")==0)require(Boolean.TRUE.equals(policies.get("allow_empty_regulons"))&&Boolean.FALSE.equals(activity.get("activity_computed"))&&"NO_SCORABLE_REGULONS".equals(activity.get("outcome")),"No scorable regulons: activity skipped. Inspect motif/database/annotation results, or explicitly set policies.allow_empty_regulons=true to retain this negative outcome in NEW output; do not infer biological absence");
        else {
            require(Boolean.TRUE.equals(activity.get("activity_computed"))&&number(activity,"regulons")==number(summary,"regulons")&&number(activity,"cells")>0,"Activity population differs from source regulons");
            require(number(activity,"ineligible_fields")==0||Boolean.TRUE.equals(policies.get("allow_unreportable_activity")),"Unreportable activity fields: inspect activity/diagnostics.json and eligibility.bin. Resolve the gene-coverage/window/normalization cause, or explicitly set policies.allow_unreportable_activity=true to retain unreportable values in NEW output");
            var axes=read(root.resolve("activity/axes.json"));long fields=Math.multiplyExact(number(activity,"cells"),number(activity,"regulons"));
            require(list(axes.get("cells")).size()==number(activity,"cells")&&list(axes.get("regulons")).size()==number(activity,"regulons"),"Activity axes differ");
            require(Files.size(root.resolve("activity/raw-scores.bin"))==Math.multiplyExact(fields,8)&&Files.size(root.resolve("activity/eligibility.bin"))==fields,"Activity binary dimensions differ");
        }
    }
    static List<Map<String,Object>> jsonLines(Path file)throws IOException {
        var rows=new ArrayList<Map<String,Object>>();try(var lines=new ScenicWorkflowActivity.Lines(file,16_777_216)){
            for(String line;(line=lines.next())!=null;){require(rows.size()<1_000_000,"Ledger population capacity exceeded");rows.add(object(GprofilerJson.read(line.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
        }return rows;
    }
    static long modelCount(Path file)throws IOException {
        long count=0;try(var lines=new ScenicWorkflowActivity.Lines(file,67_108_864)){
            for(String line;(line=lines.next())!=null;){var value=object(GprofilerJson.read(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));ScenicWorkflowContracts.label(value.get("name"));require(!list(value.get("gene_weights")).isEmpty()&&++count<=1_000_000,"Invalid or over-capacity complete signature export");}
        }return count;
    }
    static Map<String,Object> inspect(Path root)throws IOException {
        root=existing(root);for(String name:List.of("COMPLETED.json","manifest.json","canonical-request.json"))require(Files.isRegularFile(root.resolve(name),LinkOption.NOFOLLOW_LINKS),"Saved capture is incomplete or damaged: "+name+" missing; retain FAILURE.json and logs, resolve the cause and retry NEW output");
        var completion=read(root.resolve("COMPLETED.json"));var request=read(root.resolve("canonical-request.json"));
        require(ScenicWorkflowContracts.COMPLETE.equals(completion.get("status"))&&Boolean.TRUE.equals(completion.get("capture_complete")),"Unknown completion/admission scope");
        var saved=read(root.resolve("manifest.json"));require(same(completion.get("manifest_identity"),pin(root.resolve("manifest.json")))&&same(saved,inventory(root,request)),"Closed capture inventory changed");namespace(root,request,saved);
        ScenicWorkflowSource.sameProducer(read(root.resolve("producer.json")));
        for(String name:ScenicWorkflowRuntime.SOURCE_FILES)require(Arrays.equals(ScenicWorkflowRuntime.resource(name),bytes(root.resolve("source/"+name),67_108_864)),"Captured original worker/profile differs from producer");
        unchangedBindings(root,request);
        require(same(read(root.resolve("runtime-before.json")),read(root.resolve("runtime-after.json"))),"Saved runtime closure changed during original call");
        for(var item:read(root.resolve("input-inventory.json")).entrySet())require(same(item.getValue(),pin(root.resolve(relative(item.getKey())))),"Captured scientific input changed");
        var worker=read(root.resolve("worker/report.json"));require("ORIGINAL_RETURNED_PENDING_JAVA_AUDIT".equals(worker.get("status"))&&Boolean.TRUE.equals(worker.get("original_call_returned"))&&Boolean.FALSE.equals(worker.get("results_admitted")),"Original worker did not return complete provisional output");
        require(Arrays.equals(bytes(root.resolve("worker/READY"),128),"SCENIC_PREFLIGHT_V1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))&&Arrays.equals(bytes(root.resolve("worker/ACK"),128),"SCENIC_ACK_V1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),"Pre-call handshake missing");
        fieldOutcomes(root,request);var activity=read(root.resolve("activity/SUMMARY.json"));boolean admitted=eligibleFields(activity)>0;var status=read(root.resolve("analysis-status.json"));
        require(Boolean.valueOf(admitted).equals(completion.get("results_admitted"))&&Boolean.valueOf(admitted).equals(status.get("results_admitted"))&&ScenicWorkflowContracts.COMPLETE.equals(status.get("status"))&&Boolean.TRUE.equals(status.get("capture_complete"))&&ScenicWorkflowContracts.PROFILE.equals(status.get("profile")),"Completion/status activity admission differs from the computed field outcomes");
        require(same(activity,status.get("native_activity"))&&number(status,"eligible_activity_fields")==eligibleFields(activity)&&number(status,"unreportable_activity_fields")==number(activity,"ineligible_fields")&&ScenicWorkflowContracts.ADMISSION.equals(status.get("admission_scope")),"Reported activity admission population or scope differs");return request;
    }
    static Path existing(Path root)throws IOException {root=root.toAbsolutePath().normalize();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&root.toRealPath().equals(root),"Require a physical existing capture without aliases");return root;}
    static void unchangedBindings(Path root,Map<String,Object> request)throws IOException {
        binding(root,request);require(same(read(root.resolve("worker/request.json")),ScenicWorkflowRuntime.workerRequest(request)),"Captured worker options/inputs differ from canonical request");
        for(var item:read(root.resolve("input-inventory.json")).entrySet())require(same(item.getValue(),pin(root.resolve(relative(item.getKey())))),"Captured scientific input changed before completion");
        var sources=read(root.resolve("source-inventory.json"));
        for(String name:ScenicWorkflowRuntime.SOURCE_FILES){byte[] expected=ScenicWorkflowRuntime.resource(name);require(Arrays.equals(expected,bytes(root.resolve("source/"+name),67_108_864))&&same(sources.get(name),pin(expected)),"Worker source/profile or saved source identity changed before completion");}
    }
    static void namespace(Path root,Map<String,Object> request,Map<String,Object> manifest)throws IOException {
        var expected=new TreeSet<String>(List.of("source-request.json","canonical-request.json","request-binding.json","producer.json","source-inventory.json","input-inventory.json","input-layout.json","runtime-before.json","runtime-after.json","worker-process.json","analysis-status.json","WARNINGS.md",
            "worker/request.json","worker/stdout.log","worker/stderr.log","worker/runtime.json","worker/runtime-after-info.json","worker/READY","worker/ACK","worker/report.json",
            "worker/out/invocation.json","worker/out/tf-selection.json","worker/out/target-ledger.jsonl","worker/out/adjacencies.json","worker/out/modules.jsonl","worker/out/ctx-ledger.jsonl","worker/out/motif-enrichment.json","worker/out/regulons.jsonl","worker/out/SUMMARY.json","activity/SUMMARY.json"));
        for(String name:ScenicWorkflowRuntime.SOURCE_FILES)expected.add("source/"+name);
        var inputs=new TreeSet<String>(List.of("inputs/fit.tsv","inputs/activity.tsv","inputs/tfs.txt","inputs/motifs.tbl"));
        for(int at=0;at<list(object(request.get("inputs")).get("databases")).size();at++)inputs.add("inputs/db-"+at+".genes_vs_motifs.rankings.feather");expected.addAll(inputs);
        var files=object(manifest.get("files"));require(files.keySet().containsAll(expected),"Capture lacks a required source/input/worker/scientific/status member");
        if(Boolean.TRUE.equals(read(root.resolve("activity/SUMMARY.json")).get("activity_computed")))for(String name:List.of("raw-scores.bin","ranks.bin","input-cell-axis.json","scores.tsv","eligibility.bin","rank-axes.json","axes.json","diagnostics.json"))expected.add("activity/"+name);
        String mode=text(read(root.resolve("source-request.json")).get("mode"),"mode");if(Set.of("VERIFY","REPLAY").contains(mode)){
            expected.add("fresh-comparison.json");for(String name:List.of("manifest.json","COMPLETED.json","canonical-request.json","input-inventory.json","producer.json"))expected.add("parent-proof/"+name);
        }
        var actual=new TreeSet<>(files.keySet());actual.removeIf(name->name.startsWith("worker/cache/"));require(actual.equals(expected),"Unexpected/missing member in closed SCENIC capture");
        require(read(root.resolve("input-inventory.json")).keySet().equals(inputs),"Scientific input-role population differs from declared matrices/resources");
        require(read(root.resolve("source-inventory.json")).keySet().equals(new HashSet<>(ScenicWorkflowRuntime.SOURCE_FILES)),"Source-resource population differs");
        var directories=new TreeSet<String>();for(String name:expected){int slash=name.lastIndexOf('/');while(slash>=0){directories.add(name.substring(0,slash));slash=name.lastIndexOf('/',slash-1);}}
        directories.add("worker/cache");var actualDirectories=new TreeSet<String>();for(Object name:list(manifest.get("directories")))actualDirectories.add(text(name,"directory"));actualDirectories.removeIf(name->name.startsWith("worker/cache/"));
        require(actualDirectories.equals(directories),"Unexpected/missing directory in closed SCENIC capture");
    }
    static String outcomeWarnings(Path root,Map<String,Object> activity)throws IOException {
        var source=read(root.resolve("worker/out/SUMMARY.json"));var selection=read(root.resolve("worker/out/tf-selection.json"));var text=new StringBuilder();
        long skipped=number(source,"skipped_gene_coverage"),unreportable=number(activity,"ineligible_fields");
        if(Boolean.FALSE.equals(activity.get("activity_computed")))text.append("WARNING: NO_SCORABLE_REGULONS. Activity was not computed. Empty output is not biological absence; inspect motif enrichment, database/annotation provenance and the full CTX ledger.\n");
        if(unreportable>0)text.append("WARNING: ").append(unreportable).append(" activity fields are UNREPORTABLE. Preserve their original placeholder/nonfinite values and zero eligibility flags. Check gene coverage, rank window and normalization diagnostics; resolve the cause in NEW output before interpreting these fields.\n");
        if(skipped>0)text.append("WARNING: ").append(skipped).append(" coexpression-module/database tasks were omitted by the original missing-gene rule (missing fraction >=20%). Check species, genome build, gene-ID namespace and database edition; do not silently repair identifiers or lower thresholds. Every omitted task is in worker/out/ctx-ledger.jsonl.\n");
        long absent=list(selection.get("absent_from_expression")).size();if(absent>0)text.append("Source TF intersection selected ").append(list(selection.get("selected_in_expression_order")).size()).append(" of ").append(list(selection.get("requested")).size()).append(" supplied TFs; ").append(absent).append(" absent from fitting expression. Literal identifiers and the complete list are retained in worker/out/tf-selection.json.\n");
        if(!text.isEmpty())text.append("Retain complete original warnings in worker/report.json, worker/out/target-ledger.jsonl, worker/out/ctx-ledger.jsonl and worker/stderr.log.\n");return text.toString();
    }
    static long eligibleFields(Map<String,Object> activity)throws IOException {
        if(!Boolean.TRUE.equals(activity.get("activity_computed")))return 0;long fields=Math.multiplyExact(number(activity,"cells"),number(activity,"regulons"));long invalid=number(activity,"ineligible_fields");require(invalid<=fields,"Unreportable field count exceeds activity dimensions");return fields-invalid;
    }
    static void compare(Path parent,Path child)throws IOException {
        require(same(read(parent.resolve("runtime-before.json")),read(child.resolve("runtime-before.json"))),"Fresh computation used a different exact runtime closure; retain both captures. Full VERIFY/REPLAY requires the original runtime at its recorded physical paths");
        var files=new ArrayList<>(SCIENTIFIC);try(var paths=Files.list(parent.resolve("activity"))){for(Path file:paths.toList()){String name="activity/"+file.getFileName();if(!files.contains(name))files.add(name);}}
        for(String name:files)require(same(pin(parent.resolve(name)),pin(child.resolve(name))),"Fresh complete computation differs from saved scientific output: "+name);
        for(String name:List.of("target-ledger.jsonl","ctx-ledger.jsonl")){
            var a=jsonLines(parent.resolve("worker/out/"+name));var b=jsonLines(child.resolve("worker/out/"+name));require(a.size()==b.size(),"Fresh task ledger population differs");
            for(int index=0;index<a.size();index++){
                var left=new LinkedHashMap<>(a.get(index));var right=new LinkedHashMap<>(b.get(index));
                // Raw warnings/tracebacks remain bound in each closed capture; source paths differ on relocation.
                for(String key:List.of("warnings","logs","traceback")){left.remove(key);right.remove(key);}
                require(same(left,right),"Fresh target/module outcome differs: "+name+" row"+index);
            }
        }
    }
    static void binding(Path root,Map<String,Object> request)throws IOException {
        var binding=read(root.resolve("request-binding.json"));var raw=read(root.resolve("source-request.json"));
        keys(binding,Set.of("source_base","path_style","mode","source_request","canonical_request"));
        require(same(raw,binding.get("source_request"))&&same(request,binding.get("canonical_request")),"Source/canonical request binding changed");
        String mode=text(raw.get("mode"),"mode");require(mode.equals(binding.get("mode")),"Captured mode binding differs");
        String base=text(binding.get("source_base"),"source base"),style=text(binding.get("path_style"),"path style");
        Map<String,Object> expected;
        if(mode.equals("RUN"))expected=ScenicWorkflowContracts.validate(raw,base,style);
        else {
            require(Set.of("VERIFY","REPLAY").contains(mode),"Unknown saved mode");keys(raw,Set.of("schema_version","mode","analysis","out","python"));integer(raw.get("schema_version"),1,1);
            Path proof=root.resolve("parent-proof");var parentManifest=read(proof.resolve("manifest.json"));var members=object(parentManifest.get("files"));
            for(String name:List.of("canonical-request.json","input-inventory.json","producer.json"))require(same(members.get(name),pin(proof.resolve(name))),"Portable parent proof does not match its saved inventory: "+name);
            var parentCompletion=read(proof.resolve("COMPLETED.json"));require(same(parentCompletion.get("manifest_identity"),pin(proof.resolve("manifest.json")))&&ScenicWorkflowContracts.COMPLETE.equals(parentCompletion.get("status"))&&Boolean.TRUE.equals(parentCompletion.get("capture_complete")),"Parent completion proof differs");
            var comparison=read(root.resolve("fresh-comparison.json"));
            require(ScenicWorkflowContracts.resolve(base,text(raw.get("analysis"),"analysis"),style).equals(comparison.get("parent_address")),"Raw parent address differs from the declared fresh-computation proof");
            require(mode.equals(comparison.get("mode"))&&Boolean.TRUE.equals(comparison.get("all_declared_scientific_outputs_equal"))&&Boolean.TRUE.equals(comparison.get("original_GRN_and_CTX_reexecuted"))&&Boolean.TRUE.equals(comparison.get("native_activity_recomputed"))&&Boolean.FALSE.equals(comparison.get("parent_modified")),"Fresh computation proof is incomplete");
            require(same(comparison.get("parent_manifest_identity"),pin(proof.resolve("manifest.json")))&&same(read(proof.resolve("input-inventory.json")),read(root.resolve("input-inventory.json"))),"Parent/child scientific inputs differ");
            require(same(read(proof.resolve("producer.json")).get("identity"),read(root.resolve("producer.json")).get("identity")),"Parent producer proof differs");
            expected=new LinkedHashMap<>(read(proof.resolve("canonical-request.json")));expected.put("out",ScenicWorkflowContracts.resolve(base,text(raw.get("out"),"out"),style));
            if(raw.containsKey("python"))expected.put("python",ScenicWorkflowContracts.resolve(base,text(raw.get("python"),"python"),style));
        }
        require(same(expected,request),"Canonical request does not follow its source/parent binding");
    }
}
