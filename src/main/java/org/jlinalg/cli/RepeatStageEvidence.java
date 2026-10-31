/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Independent retained two-stage command, process, source-copy and raw-census checks. */
final class RepeatStageEvidence {
    private RepeatStageEvidence() { }
    static RepeatRawSearch.Report verify(RepeatRecipe.Spec spec,RepeatResources.Prepared resources,Path prepared,Path stage,Runnable check)throws IOException{
        Path query=prepared.resolve("stage/query.fa"),library=spec.library()==null?null:prepared.resolve("stage/library.fa");
        RepeatOriginalStages.originalPaths(resources.sourceRoot(),query,library,stage);
        var queryPair=verifyFasta(spec.query(),query,spec,true,check);var queryCensus=queryPair.staged();
        var snapshots=new ArrayList<Object>();snapshot(snapshots,"recipe",spec.recipe(),prepared.resolve("input-snapshots/recipe.json"),2097152,check);
        snapshot(snapshots,"query",spec.query().path(),prepared.resolve("input-snapshots/query.source"),spec.limits().inputBytes(),check);
        if(library!=null){
            verifyFasta(spec.library(),library,spec,false,check);
            snapshot(snapshots,"library",spec.library().path(),prepared.resolve("input-snapshots/library.source"),spec.limits().inputBytes(),check);
        }
        var preparation=RepeatBundle.object(prepared.resolve("preparation.json"),4194304);
        if(!Boolean.FALSE.equals(preparation.get("upstream_analysis_completed"))||!snapshots.equals(preparation.get("snapshots"))
                ||!mappingValues(queryPair.mappings()).equals(preparation.get("query_mappings")))throw new IOException("Original preparation source-copy or identity/case mapping differs");
        var commands=RepeatOriginalStages.commands(spec,resources.sourceRoot(),query,library,stage,queryCensus.residues());
        var metadata=RepeatBundle.object(stage.resolve("metadata/commands.json"),4194304);
        if(!Long.valueOf(1).equals(metadata.get("schema_version"))||!spec.profile().equals(metadata.get("profile"))
                ||!RepeatBundle.object(RepeatBundle.json(commands)).equals(metadata.get("commands")))throw new IOException("Retained original scientific command arguments differ");
        var process=limits(metadata.get("process_limits"),spec);
        verifyProcess(RepeatBundle.object(stage.resolve("search/process.json"),4194304),commands.search(),process,
                List.of(prepared,resources.work(),stage.resolve("metadata"),stage.resolve("post-input")));
        verifyProcess(RepeatBundle.object(stage.resolve("postprocess/process.json"),4194304),commands.postprocess(),process,
                List.of(prepared,resources.work(),stage.resolve("metadata"),stage.resolve("post-input"),stage.resolve("search")));
        same(query,stage.resolve("post-input/query.fa"),spec.limits().workspaceBytes(),check);
        Path engine=stage.resolve("search/"+commands.rawName()),raw=stage.resolve("post-input/"+commands.rawName());
        same(engine,raw,spec.process().fileBytes(),check);
        if(Files.exists(stage.resolve("search/query.fa.out"),LinkOption.NOFOLLOW_LINKS)||Files.exists(stage.resolve("search/query.fa.masked"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Retained -nopost search contains unexpected postprocessed outputs");
        var census=RepeatRawSearch.read(raw,commands.rawName().endsWith(".gz"),resources.payload().version(),spec.parameters().speed(),queryCensus.sequences().size(),queryCensus.residues(),spec.limits().workspaceBytes(),RepeatRawSearch.METADATA_CHARACTERS,check);
        var result=RepeatBundle.object(stage.resolve("metadata/results.json"),4194304);
        if(!Long.valueOf(1).equals(result.get("schema_version"))||!"ORIGINAL_STAGES_OUTPUT_AUDITED_RUNTIME_NOT_ADMITTED".equals(result.get("status"))
                ||!spec.profile().equals(result.get("profile"))||!Boolean.FALSE.equals(result.get("scientific_runtime_admitted"))||!Boolean.FALSE.equals(result.get("biological_absence_certified"))
                ||!Long.valueOf(queryCensus.sequences().size()).equals(result.get("query_records"))||!Long.valueOf(queryCensus.residues()).equals(result.get("query_bases"))
                ||!raw.toString().equals(result.get("raw_search"))||!stage.resolve("post-input/query.fa.out").toString().equals(result.get("original_annotation"))
                ||!RepeatBundle.object(RepeatBundle.json(census)).equals(result.get("raw_search_census")))throw new IOException("Retained complete raw census or original-stage result scope differs");
        var inventory=RepeatBundle.inventory(stage.resolve("post-input"),new RepeatBundle.Limits(spec.limits().workspaceBytes(),spec.limits().fileCount()),true,check);
        if(!inventory.hashes().equals(result.get("original_output_sha256")))throw new IOException("Retained complete original post-input/output inventory differs");
        return census;
    }
    record FastaPair(RepeatFasta.Census original,RepeatFasta.Census staged,List<RepeatInputs.Mapping> mappings){
        FastaPair {mappings=List.copyOf(mappings);}
    }
    static Object mappingValues(List<RepeatInputs.Mapping> mappings)throws IOException{
        var values=new ArrayList<Object>();for(var m:mappings)values.add(Map.of("ordinal",m.ordinal(),"original_id",m.originalId(),"original_header",m.originalHeader(),"staged_id",m.stagedId(),"staged_header",m.stagedHeader(),"length",m.length(),"original_sequence_sha256",m.originalSequenceSha256(),"staged_sequence_sha256",m.stagedSequenceSha256()));
        return RepeatBundle.object(RepeatBundle.json(Map.of("mappings",values))).get("mappings");
    }
    /** Complete character-by-character replay of the explicitly declared staging transform. */
    static FastaPair verifyFasta(RepeatRecipe.Source source,Path staged,RepeatRecipe.Spec spec,boolean query,Runnable check)throws IOException{
        boolean aliases=spec.staging()==RepeatRecipe.Staging.ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA;
        try(var before=RepeatFasta.open(source.path(),source.encoding(),spec.limits().fasta(),check);
                var after=RepeatFasta.open(staged,RepeatFasta.Encoding.PLAIN_UTF8,RepeatInputs.stageLimits(spec.limits()),check)){
            while(before.nextSequence()){
                if(!after.nextSequence())throw new IOException("Retained staging lost a FASTA record");
                String id=aliases&&query?String.format(java.util.Locale.ROOT,"Q%010d",before.ordinal()):before.id();
                String header=aliases&&query?id:before.header();
                if(!id.equals(after.id())||!header.equals(after.header()))throw new IOException("Retained FASTA staging changed its declared header/identity mapping");
                int value;while((value=before.base())>=0){
                    if((before.position()&65535)==0)check.run();
                    if(value=='U'||value=='u')throw new IOException("Original DNA staging never infers U-to-T substitution");
                    if(!aliases&&value>='a'&&value<='z')throw new IOException("EXACT_SOURCE original staging cannot silently uppercase bases");
                    if(aliases&&value>='a'&&value<='z')value-=32;
                    if(after.base()!=value)throw new IOException("Retained FASTA staging changed an undeclared base or case");
                }
                if(after.base()!=-1)throw new IOException("Retained FASTA staging added bases");
            }
            if(after.nextSequence())throw new IOException("Retained FASTA staging added records");
            var original=before.census();var result=after.census();var mappings=new ArrayList<RepeatInputs.Mapping>();
            for(int i=0;i<original.sequences().size();i++){
                var a=original.sequences().get(i);var b=result.sequences().get(i);
                mappings.add(new RepeatInputs.Mapping(a.ordinal(),a.id(),a.header(),b.id(),b.header(),a.length(),a.sequenceSha256(),b.sequenceSha256()));
            }
            return new FastaPair(original,result,mappings);
        }
    }
    private static void snapshot(List<Object> snapshots,String role,Path source,Path copy,long maximum,Runnable check)throws IOException{
        same(source,copy,maximum,check);
        snapshots.add(Map.of("bytes",Files.size(source),"original",source.toString(),"role",role,"copy",copy.toString(),"sha256",RepeatInputs.hash(source,maximum,check)));
    }
    private static void same(Path a,Path b,long maximum,Runnable check)throws IOException{
        for(Path path:List.of(a,b))if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||Files.size(path)>maximum)throw new IOException("Missing,linked or oversized retained original source/copy: "+path);
        if(Files.size(a)!=Files.size(b)||!RepeatInputs.hash(a,maximum,check).equals(RepeatInputs.hash(b,maximum,check)))throw new IOException("Retained original source/copy changed: "+b);
    }
    private static RepeatProcess.Limits limits(Object supplied,RepeatRecipe.Spec spec)throws IOException{
        if(!(supplied instanceof Map<?,?> values))throw new IOException("Missing retained original process limits");
        var locked=RepeatOriginalStages.reserve(RepeatProcess.Limits.from(spec));var expected=RepeatBundle.object(RepeatBundle.json(locked));
        if(!values.keySet().equals(expected.keySet()))throw new IOException("Original process capacity field inventory differs");
        for(var entry:expected.entrySet()){
            if(!(values.get(entry.getKey()) instanceof Long number))throw new IOException("Original process limits require literal integers");
            if(Set.of("workspaceBytes","fileCount").contains(entry.getKey())){
                if(number<1||number>(Long)entry.getValue())throw new IOException("Retained process workspace/member capacity exceeds its fixed maximum");
            }else if(!entry.getValue().equals(number))throw new IOException("Fixed health process capacity differs: "+entry.getKey());
        }
        return new RepeatProcess.Limits(whole(values,"wallMillis"),whole(values,"addressSpaceBytes"),whole(values,"cpuSeconds"),whole(values,"fileBytes"),Math.toIntExact(whole(values,"openFiles")),whole(values,"workspaceBytes"),Math.toIntExact(whole(values,"fileCount")),Math.toIntExact(whole(values,"processes")),Math.toIntExact(whole(values,"threads")),whole(values,"sampledRssBytes"),Math.toIntExact(whole(values,"monitorMillis")));
    }
    static void verifyProcess(Map<?,?> ledger,List<String> scientific,RepeatProcess.Limits limits,List<Path> roots)throws IOException{
        var invocation=new ArrayList<String>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+limits.addressSpaceBytes(),"--cpu="+limits.cpuSeconds(),"--fsize="+limits.fileBytes(),"--nofile="+limits.openFiles(),"--"));invocation.addAll(scientific);
        if(!"completed-process-only".equals(ledger.get("status"))||!Long.valueOf(0).equals(ledger.get("exit_code"))
                ||!List.of(0L).equals(ledger.get("accepted_process_exit_codes"))||!"strict-zero-exit-process".equals(ledger.get("process_purpose"))
                ||!List.of().equals(ledger.get("owned_live_after_cleanup"))||ledger.containsKey("error")||ledger.containsKey("suppressed")||ledger.containsKey("cleanup_state")
                ||!invocation.equals(ledger.get("command"))||!roots.stream().map(Path::toString).toList().equals(ledger.get("accounted_roots"))
                ||!RepeatBundle.object(RepeatBundle.json(limits)).equals(ledger.get("limits")))throw new IOException("Retained original process did not complete with the exact command,limits,zero exit and cleanup");
        if(whole(ledger,"owned_launcher_pid")<1||whole(ledger,"elapsed_millis")<0||!(ledger.get("owned_processes") instanceof List<?> identities)||identities.isEmpty())throw new IOException("Missing retained original process identity/timing evidence");
        for(Object identity:identities){
            if(!(identity instanceof Map<?,?> item)||!item.keySet().equals(Set.of("pid","startTicks","parent","group","session","state"))
                    ||whole(item,"pid")<1||whole(item,"startTicks")<0||whole(item,"parent")<0||whole(item,"group")<1||whole(item,"session")<1
                    ||!(item.get("state") instanceof String state)||!state.matches("[A-Za-z]"))throw new IOException("Malformed retained original process identity");
        }
        for(var bound:Map.of("peak_workspace_bytes",limits.workspaceBytes(),"peak_sampled_rss_bytes",limits.sampledRssBytes(),"peak_processes",(long)limits.processes(),"peak_threads",(long)limits.threads()).entrySet()){
            long value=whole(ledger,bound.getKey());if(value<0||value>bound.getValue())throw new IOException("Recorded original process peak exceeds admitted capacity: "+bound.getKey());
        }
    }
    private static long whole(Map<?,?> values,String key)throws IOException{if(!(values.get(key) instanceof Long value))throw new IOException("Require retained literal integer: "+key);return value;}
}
