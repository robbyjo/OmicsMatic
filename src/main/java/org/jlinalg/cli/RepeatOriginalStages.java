/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.genetics.RepeatAnnotations;

/** Separately supervised original search/postprocessing; this primitive does not admit a runtime. */
final class RepeatOriginalStages {
    private RepeatOriginalStages() { }
    private static final long RESERVE_BYTES=262144;
    private static final int RESERVE_MEMBERS=9;
    record Commands(List<String> search,List<String> postprocess,String rawName) {
        Commands { search=List.copyOf(search);postprocess=List.copyOf(postprocess); }
    }
    record ProtectedFile(Path source,Path copy,long bytes,String sha256) {
        void verify(Runnable check)throws IOException{
            for(Path path:List.of(source,copy))if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)
                    ||Files.size(path)!=bytes||!RepeatInputs.hash(path,bytes,check).equals(sha256))throw new IOException("Protected original stage file changed: "+path);
        }
    }
    record OutputFence(Path root,RepeatBundle.Limits limits,RepeatBundle.Inventory inventory) {
        void verify(Runnable check)throws IOException{
            if(!inventory.equals(RepeatBundle.inventory(root,limits,true,check)))throw new IOException("Complete original stage output inventory changed during scientific audit: "+root);
        }
    }
    record Result(Path root,Path annotation,Path masked,Path rawSearch,RepeatProcess.Result search,
            RepeatProcess.Result postprocess,RepeatAnnotations.Table annotations,RepeatMaskAudit.Report audit,
            boolean originalNoHitOutputAbsence,Map<String,RepeatPayload.Fingerprint> generatedCache,Map<String,String> outputSha256,RepeatRawSearch.Report rawCensus) {
        Result { generatedCache=Map.copyOf(generatedCache);outputSha256=Map.copyOf(outputSha256); }
    }
    static Commands commands(RepeatRecipe.Spec spec,Path sourceRoot,Path query,Path library,Path outputRoot,long queryBases)throws IOException{
        if(!spec.search()||spec.operation()!=RepeatRecipe.Operation.SEARCH||spec.parameters()==null||spec.process()==null)
            throw new IOException("Original stages require an explicit typed SEARCH request");
        if(!List.of(RepeatRecipe.CURRENT,RepeatRecipe.HISTORICAL).contains(spec.profile()))throw new RepeatRecipe.UnavailableCapability("Original stage profile is unavailable;no edition is substituted");
        if(spec.profile().equals(RepeatRecipe.CURRENT)&&spec.mode()!=RepeatRecipe.Mode.CUSTOM_LIBRARY)throw new RepeatRecipe.UnavailableCapability("Current original stages require the admitted custom-library profile");
        var search=new ArrayList<String>(List.of("/usr/bin/perl",sourceRoot.resolve("RepeatMasker").toString()));
        search.addAll(spec.parameters().flags(spec.mode(),spec.mask()));
        var post=new ArrayList<String>(List.of("/usr/bin/perl",sourceRoot.resolve("ProcessRepeats").toString(),"-a","-gff","-source"));
        if(spec.mode()==RepeatRecipe.Mode.CUSTOM_LIBRARY){
            if(library==null)throw new IOException("Original custom stages require the protected staged library");
            search.addAll(List.of("-lib",library.toString()));post.addAll(List.of("-lib",library.toString()));
        }else{
            if(library!=null||spec.species()==null)throw new IOException("Species/simple stages require an explicit taxon and no custom library");
            search.addAll(List.of("-species",spec.species()));post.addAll(List.of("-species",spec.species()));
            if(spec.mode()==RepeatRecipe.Mode.SIMPLE_ONLY)post.add("-noint");
        }
        search.addAll(List.of("-dir",".","-gff","-a","-nopost",query.toString()));
        post.addAll(List.of("-orifile",query.toString(),"-maskSource",outputRoot.resolve("post-input/query.fa").toString()));
        if(spec.mask()==RepeatAnnotations.Mask.SOFT)post.add("-xsmall");
        if(spec.mask()==RepeatAnnotations.Mask.X)post.add("-x");
        String rawName=rawName(queryBases);post.add(outputRoot.resolve("post-input/"+rawName).toString());
        return new Commands(search,post,rawName);
    }
    static Result execute(RepeatInputs.Prepared input,RepeatResources.Prepared resources,Path suppliedRoot,Runnable check)throws IOException{
        var spec=input.spec();
        if(!System.getProperty("os.name").equals("Linux"))throw new RepeatProcess.UnavailableDependency("Original two-stage RepeatMasker execution needs Linux Java");
        Path root=suppliedRoot.toAbsolutePath().normalize();RepeatPayload.originalPath(root);
        if(root.getParent()==null||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Original stages require a canonical existing parent and NEW result directory");
        for(Path protectedRoot:List.of(input.work(),resources.work(),resources.payload().home()))
            if(root.startsWith(protectedRoot)||protectedRoot.startsWith(root))throw new IOException("Original stage results must be disjoint from protected inputs/resources/payload");
        if(!spec.profile().equals(resources.payload().profile()))throw new IOException("Original stage request and payload profiles differ");
        for(var snapshot:input.snapshots())if(snapshot.original().startsWith(root))throw new IOException("Protected original source lies inside the new stage result directory");
        var limits=RepeatProcess.Limits.from(spec);var processLimits=reserve(limits);
        Commands command=commands(spec,resources.sourceRoot(),input.query().path(),input.library()==null?null:input.library().path(),root,input.query().staged().residues());
        input.verify(check);resources.verify(check);check.run();Files.createDirectory(root);
        try{
            Path metadata=Files.createDirectory(root.resolve("metadata")),postInput=Files.createDirectory(root.resolve("post-input"));
            var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("profile",spec.profile());record.put("commands",command);
            record.put("scope","Original -nopost search and original ProcessRepeats execute as separately owned processes;whole output audit remains required. This primitive alone does not admit a runtime or public SEARCH.");
            record.put("process_limits",processLimits);record.put("capacity_policy","Combined protected inputs,owned resources,stage copies,process logs and metadata. Reserve256KiB/nine members for root/final records and retained diagnostics;full logical hardlink bytes count.");
            record.put("stage_wall_policy","Wall/AS/CPU/file/nofile/process/thread/RSS limits apply separately to each original stage. Logical workspace/member limits are shared. Hashing and native output audits occur outside the watched child execution;the per-stage wall limit is not a whole-workflow deadline.");
            record.put("raw_result_limits",Map.of("decoded_bytes",limits.workspaceBytes(),"retained_metadata_characters",RepeatRawSearch.METADATA_CHARACTERS,"other_lines","Read and validate through complete EOF without retaining whole alignment/batch-boundary lines"));
            write(root,metadata.resolve("commands.json"),record,input,resources,limits,65536,check);
            long available=limits.workspaceBytes()-RepeatProcess.workspaceBytes(root,limits,List.of(input.work(),resources.work()))-RESERVE_BYTES;
            var maskSource=copyProtected(input.query().path(),postInput.resolve("query.fa"),Math.min(available,spec.limits().workspaceBytes()),check);
            RepeatProcess.Check integrity=()->{input.verify(check);resources.verify(check);maskSource.verify(check);};
            Path searchWork=root.resolve("search"),postWork=root.resolve("postprocess");
            var search=RepeatProcess.executeOwned(command.search(),resources.environment(searchWork),searchWork,processLimits,integrity,check,List.of(input.work(),resources.work(),metadata,postInput));
            if(Files.exists(searchWork.resolve("query.fa.out"),LinkOption.NOFOLLOW_LINKS)||Files.exists(searchWork.resolve("query.fa.masked"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Original -nopost search unexpectedly produced postprocessed annotations/mask");
            available=limits.workspaceBytes()-RepeatProcess.workspaceBytes(root,limits,List.of(input.work(),resources.work()))-RESERVE_BYTES;
            var raw=copyProtected(searchWork.resolve(command.rawName()),postInput.resolve(command.rawName()),Math.min(available,processLimits.fileBytes()),check);
            var rawCensus=RepeatRawSearch.read(raw.copy(),command.rawName().endsWith(".gz"),resources.payload().version(),spec.parameters().speed(),input.query().staged().sequences().size(),input.query().staged().residues(),limits.workspaceBytes(),RepeatRawSearch.METADATA_CHARACTERS,check);
            var engineFence=fence(searchWork,limits,check);
            RepeatProcess.Check completeIntegrity=()->{integrity.verify();raw.verify(check);};
            var post=RepeatProcess.executeOwned(command.postprocess(),resources.environment(postWork),postWork,processLimits,completeIntegrity,check,List.of(input.work(),resources.work(),metadata,postInput,searchWork));
            var outputFence=fence(postInput,limits,check);engineFence.verify(check);
            Path annotation=postInput.resolve("query.fa.out"),masked=postInput.resolve("query.fa.masked");
            RepeatAnnotations.Table table=annotations(annotation,spec,check);
            RepeatMaskAudit.validate(table.hits(),RepeatMaskAudit.Selection.all(),input.query().staged(),spec.limits().indexBytes(),check);
            boolean noMask=!Files.exists(masked,LinkOption.NOFOLLOW_LINKS);RepeatMaskAudit.Report audit=null;
            if(noMask){
                if(!table.noHitsMessage()||!table.hits().isEmpty()||Files.exists(postInput.resolve("query.fa.tbl"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Original mask/table absence requires the complete explicit no-hit contract");
            }else{
                if(!Files.isRegularFile(masked,LinkOption.NOFOLLOW_LINKS)||!masked.toRealPath().equals(masked)||table.noHitsMessage()||table.hits().isEmpty())throw new IOException("Original mask and complete annotation population disagree");
                audit=RepeatMaskAudit.compare(input.query().path(),RepeatFasta.Encoding.PLAIN_UTF8,masked,RepeatFasta.Encoding.PLAIN_UTF8,table.hits(),RepeatMaskAudit.Selection.all(),spec.mask(),RepeatInputs.stageLimits(spec.limits()),spec.limits().indexBytes(),check);
                if(!Files.isRegularFile(postInput.resolve("query.fa.tbl"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Original positive postprocessing is missing its complete summary table");
            }
            try(var pass=RepeatFingerprint.enter()){
            pass.requireActive();var caches=resources.generatedCache(check);completeIntegrity.verify();
            var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("status","ORIGINAL_STAGES_OUTPUT_AUDITED_RUNTIME_NOT_ADMITTED");result.put("profile",spec.profile());result.put("annotation_rows",table.hits().size());
            result.put("query_records",input.query().staged().sequences().size());result.put("query_bases",input.query().staged().residues());result.put("original_no_hit_mask_and_table_absence",noMask);
            result.put("original_annotation",annotation.toString());result.put("original_mask",noMask?null:masked.toString());result.put("raw_search",raw.copy().toString());result.put("search_process",search.ledger().toString());result.put("postprocess_process",post.ledger().toString());
            result.put("scientific_runtime_admitted",false);result.put("biological_absence_certified",false);
            result.put("original_output_sha256",outputFence.inventory().hashes());
            result.put("raw_search_census",rawCensus);
            if(audit!=null)result.put("mask_audit",Map.of("compared_bases",audit.comparedBases(),"covered_bases",audit.coveredBases(),"changed_characters",audit.changedCharacters()));
            write(root,metadata.resolve("results.json"),result,input,resources,limits,65536,check);
            write(root,metadata.resolve("generated-cache.json"),caches,input,resources,limits,65536,check);
            write(root,metadata.resolve("WARNINGS.md"),("# Original stages only: runtime admission still required\n\nBoth original processes and complete output audits passed. This primitive alone does not admit a runtime or public SEARCH.\n\nRequired action: retain both process ledgers,all logs,protected raw annotations and complete native audits. A parent exit0 is insufficient;both independently supervised stages must complete. No-hit masks/tables remain absent and are never fabricated. No biological absence or whole-paper accuracy is certified. Correct any dependency/input/integrity/execution/capacity failure and retry to NEW paths.\n").getBytes(StandardCharsets.UTF_8),input,resources,limits,0,check);
            completeIntegrity.verify();if(!caches.equals(resources.generatedCache(check)))throw new IOException("Complete original cache population changed while recording final provenance");
            outputFence.verify(check);engineFence.verify(check);RepeatProcess.workspaceBytes(root,limits,List.of(input.work(),resources.work()));
            return new Result(root,annotation,noMask?null:masked,raw.copy(),search,post,table,audit,noMask,caches,outputFence.inventory().hashes(),rawCensus);
            }
        }catch(RepeatProcess.UnavailableDependency unavailable){
            try{Files.writeString(root.resolve("ORIGINAL_STAGES_SKIPPED.txt"),"RepeatMasker SKIP3: required process dependency is unavailable;dependent analysis skipped.\n"+unavailable+"\nRequired action: supply the named executable on a permitted Linux host,retain these diagnostics and use a NEW result directory. No installation or edition substitution occurs during analysis.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostic){unavailable.addSuppressed(diagnostic);}throw unavailable;
        }catch(IOException|RuntimeException failure){
            String message=failure.toString();if(message.length()>8192)message=message.substring(0,8192);
            try{Files.writeString(root.resolve("ORIGINAL_STAGES_FAILED.txt"),"RepeatMasker FAIL2: original search/postprocessing or output audit failed;no result admitted.\n"+message+"\nRequired action: retain both process directories,stdout/stderr,raw source and partial outputs. Correct the cause and rerun to a NEW directory;never infer success from the parent search exit or fabricate missing masks.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
    static RepeatProcess.Limits reserve(RepeatProcess.Limits source)throws IOException{
        if(source.workspaceBytes()<=RESERVE_BYTES||source.fileCount()<=RESERVE_MEMBERS)throw new IOException("Original stage capacity cannot cover256KiB/nine reserved root/finalization members");
        return new RepeatProcess.Limits(source.wallMillis(),source.addressSpaceBytes(),source.cpuSeconds(),source.fileBytes(),source.openFiles(),source.workspaceBytes()-RESERVE_BYTES,source.fileCount()-RESERVE_MEMBERS,source.processes(),source.threads(),source.sampledRssBytes(),source.monitorMillis());
    }
    static OutputFence fence(Path root,RepeatProcess.Limits limits,Runnable check)throws IOException{
        var capacity=new RepeatBundle.Limits(limits.workspaceBytes(),limits.fileCount());
        return new OutputFence(root.toAbsolutePath().normalize(),capacity,RepeatBundle.inventory(root,capacity,true,check));
    }
    static String rawName(long queryBases)throws IOException{
        if(queryBases<1)throw new IOException("Original search requires a nonempty complete query population");
        return queryBases>10000000?"query.fa.cat.gz":"query.fa.cat";
    }
    static void verifyRaw(Path path,boolean gzip,long maximum,Runnable check)throws IOException{
        if(maximum<0||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||Files.size(path)>maximum)throw new IOException("Raw original result exceeds its complete byte capacity or is not a canonical regular file");
        try(var raw=Files.newInputStream(path);var input=gzip?new RepeatGzip(raw,check):raw){
            byte[] buffer=new byte[65536];long total=0;int count;while((count=input.read(buffer))>=0){check.run();total=Math.addExact(total,count);if(total>maximum)throw new IOException("Complete decoded raw original result exceeds its byte capacity");}
        }
    }
    static ProtectedFile copyProtected(Path source,Path target,long maximum,Runnable check)throws IOException{
        if(maximum<0||!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||!source.toRealPath().equals(source))throw new IOException("Original stage copy requires a canonical regular source and available complete byte capacity");
        long size=Files.size(source);if(size>maximum)throw new IOException("Complete original stage copy exceeds reviewed byte capacity");
        String hash=RepeatInputs.hash(source,maximum,check);
        try(var input=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];long total=0;int count;while((count=input.read(buffer))>=0){check.run();total=Math.addExact(total,count);if(total>maximum)throw new IOException("Original stage copy grew beyond complete byte capacity");output.write(buffer,0,count);}
        }
        ProtectedFile result=new ProtectedFile(source,target,size,hash);result.verify(check);return result;
    }
    private static RepeatAnnotations.Table annotations(Path path,RepeatRecipe.Spec spec,Runnable check)throws IOException{
        check.run();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||Files.size(path)>spec.limits().workspaceBytes())throw new IOException("Missing/nonregular/oversized original annotation output");
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){return RepeatAnnotations.read(reader,path.toString(),RepeatAnnotations.Format.OUT_WITH_ID,spec.limits().annotations());}
    }
    private static void write(Path root,Path path,Object value,RepeatInputs.Prepared input,RepeatResources.Prepared resources,RepeatProcess.Limits limits,long reserve,Runnable check)throws IOException{
        byte[] raw=value instanceof byte[] data?data.clone():RepeatBundle.json(value);long used=RepeatProcess.workspaceBytes(root,limits,List.of(input.work(),resources.work()));
        if(raw.length>RESERVE_BYTES||Math.addExact(used,Math.addExact(raw.length,reserve))>limits.workspaceBytes())throw new IOException("Complete original stage metadata exceeds its byte capacity");
        Files.write(path,raw,StandardOpenOption.CREATE_NEW);RepeatProcess.workspaceBytes(root,limits,List.of(input.work(),resources.work()));check.run();
    }
}
