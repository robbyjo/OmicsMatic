/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.RepeatAnnotations;

/** Original completion has its own schema and independent full scientific evidence verification. */
final class RepeatOriginalBundle {
    private RepeatOriginalBundle() { }
    static final String TOOL="RepeatMasker-original-search";
    record Verification(String operation,String scope,long bytes,int members,long annotations,long queryBases,boolean originalNoHitMaskAbsent) { }
    static Map<String,Object> result(RepeatRecipe.Spec requested,RepeatSearch.Result run)throws IOException{
        var result=new LinkedHashMap<String,Object>();var stages=run.stages();
        result.put("operation","SEARCH");result.put("scope",RepeatSearch.SCOPE);result.put("completion_flags",RepeatSearch.flags());
        result.put("profile",requested.profile());result.put("assembly",requested.assembly());result.put("original_recipe_path",requested.recipe());result.put("recipe_sha256",requested.recipeSha256());
        result.put("runtime_home",run.runtime().home());result.put("runtime_manifest_sha256",run.runtime().manifestSha256());result.put("runtime_marker_sha256",run.runtime().markerSha256());
        result.put("host_identity_sha256",AdmixtureInputs.digest(run.runtime().hostIdentity()));result.put("resource_limits",run.resources().limits());
        result.put("query_records",run.input().query().staged().sequences().size());result.put("query_bases",run.input().query().staged().residues());result.put("annotation_rows",stages.annotations().hits().size());
        result.put("original_no_hit_mask_and_table_absence",stages.originalNoHitOutputAbsence());result.put("original_annotation","original/post-input/query.fa.out");
        result.put("original_mask",stages.masked()==null?null:"original/post-input/query.fa.masked");result.put("raw_search","original/post-input/"+stages.rawSearch().getFileName());
        result.put("compared_bases",stages.audit()==null?run.input().query().staged().residues():stages.audit().comparedBases());
        result.put("covered_bases",stages.audit()==null?0:stages.audit().coveredBases());result.put("changed_characters",stages.audit()==null?0:stages.audit().changedCharacters());return result;
    }
    static void complete(Path root,RepeatBundle.Limits capacity,Runnable check)throws IOException{
        var manifest=new LinkedHashMap<String,Object>();manifest.put("schema_version",1);manifest.put("tool",TOOL);manifest.put("operation","SEARCH");manifest.put("directory",root);
        manifest.put("scope",RepeatSearch.SCOPE);manifest.put("completion_flags",RepeatSearch.flags());manifest.put("workspace_bytes",capacity.bytes());manifest.put("member_limit",capacity.members());
        manifest.put("managed_tree",RepeatOwnedTree.snapshot(root,capacity,Set.of("manifest.json","COMPLETED.json"),check));byte[] raw=RepeatBundle.json(manifest);
        if(raw.length>RepeatRuntime.METADATA)throw new IOException("Complete original search manifest exceeds metadata capacity");
        RepeatBundle.write(root,"manifest.json",raw,capacity,RepeatBundle.FAILURE_RESERVE,2,check);
        RepeatBundle.write(root,"COMPLETED.json",RepeatBundle.json(Map.of("schema_version",1,"tool",TOOL,"operation","SEARCH","scope",RepeatSearch.SCOPE,"completion_flags",RepeatSearch.flags(),"manifest_sha256",AdmixtureInputs.digest(raw))),capacity,RepeatBundle.FAILURE_RESERVE,1,check);
        verify(root,check);
    }
    static Verification verify(Path supplied,Runnable check)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toRealPath().equals(root))throw new IOException("Original completed directory must be canonical");
        String markerHash=RepeatInputs.hash(root.resolve("COMPLETED.json"),65536,check);byte[] manifestRaw=AdmixtureInputs.raw(root.resolve("manifest.json"),RepeatRuntime.METADATA);
        var marker=RepeatBundle.object(root.resolve("COMPLETED.json"),65536);var manifest=RepeatBundle.object(manifestRaw);
        if(!marker.keySet().equals(Set.of("schema_version","tool","operation","scope","completion_flags","manifest_sha256"))
                ||!manifest.keySet().equals(Set.of("schema_version","tool","operation","directory","scope","completion_flags","workspace_bytes","member_limit","managed_tree")))throw new IOException("Unknown original completion schema");
        for(Map<?,?> value:java.util.List.of(marker,manifest))if(!Long.valueOf(1).equals(value.get("schema_version"))||!TOOL.equals(value.get("tool"))||!"SEARCH".equals(value.get("operation"))
                ||!RepeatSearch.SCOPE.equals(value.get("scope"))||!RepeatSearch.flags().equals(value.get("completion_flags")))throw new IOException("Native/partial/incorrect completion cannot certify original SEARCH");
        if(!root.toString().equals(manifest.get("directory"))||!AdmixtureInputs.digest(manifestRaw).equals(marker.get("manifest_sha256")))throw new IOException("Original completion path/manifest binding differs");
        var capacity=new RepeatBundle.Limits(whole(manifest,"workspace_bytes"),Math.toIntExact(whole(manifest,"member_limit")));
        var tree=RepeatOwnedTree.snapshot(root,capacity,Set.of("manifest.json","COMPLETED.json"),check);
        if(!tree.equals(manifest.get("managed_tree")))throw new IOException("Original bundle bytes,modes,files or complete directory population differ");
        for(Object name:tree.keySet())if(name.toString().endsWith("FAILED.txt")||name.toString().endsWith("INTERRUPTED.txt")||name.toString().endsWith("INTERRUPTED.md")||name.toString().endsWith("FINALIZATION_FAILED.md"))throw new IOException("Failed/interrupted original evidence cannot be completed");
        var result=RepeatBundle.object(root.resolve("results.json"),4194304);var status=RepeatBundle.object(root.resolve("analysis-status.json"),65536);var session=RepeatBundle.object(root.resolve("session.json"),4194304);
        if(!"COMPLETED_ORIGINAL".equals(status.get("status"))||!Long.valueOf(0).equals(status.get("exit_code"))||!RepeatSearch.flags().equals(status.get("completion_flags"))
                ||!"COMPLETED_ORIGINAL".equals(session.get("analysis_status"))||!Long.valueOf(0).equals(session.get("exit_code"))||!TOOL.equals(session.get("tool"))
                ||!RepeatSearch.SCOPE.equals(result.get("scope"))||!"SEARCH".equals(result.get("operation"))||!RepeatSearch.flags().equals(result.get("completion_flags")))throw new IOException("Original status/session/result scopes do not agree");
        Path originalRecipe=Path.of(text(result,"original_recipe_path"));var spec=RepeatRecipe.read(originalRecipe);
        if(spec.operation()!=RepeatRecipe.Operation.SEARCH||!spec.profile().equals(result.get("profile"))||!spec.assembly().equals(result.get("assembly"))
                ||!spec.recipeSha256().equals(result.get("recipe_sha256"))||!spec.recipeSha256().equals(RepeatInputs.hash(root.resolve("recipe-request.json"),2097152,check))
                ||spec.limits().workspaceBytes()!=capacity.bytes()||spec.limits().fileCount()!=capacity.members())throw new IOException("Original request/profile/capacity differs;retain the exact declared recipe and inputs");
        var runtime=RepeatRuntime.evidence(Path.of(text(result,"runtime_home")),spec.profile(),check);
        if(!runtime.manifestSha256().equals(result.get("runtime_manifest_sha256"))||!runtime.markerSha256().equals(result.get("runtime_marker_sha256"))||!AdmixtureInputs.digest(runtime.hostIdentity()).equals(result.get("host_identity_sha256")))throw new IOException("Recorded original runtime admission binding differs");
        for(String name:java.util.List.of("runtime-before","runtime-after")){
            byte[] identity=RepeatBundle.json(RepeatHost.identity(RepeatBundle.object(root.resolve(name+"/metadata/snapshot.json"),RepeatRuntime.METADATA)));
            if(!java.util.Arrays.equals(identity,runtime.hostIdentity()))throw new IOException("Recorded before/after original host identity differs");
        }
        if(!(result.get("resource_limits") instanceof Map<?,?> limits))throw new IOException("Missing original resource capacities");
        long resourceBytes=whole(limits,"bytes");int resourceMembers=Math.toIntExact(whole(limits,"members"));
        if(resourceBytes>capacity.bytes()||resourceMembers>capacity.members())throw new IOException("Original resource capacities exceed whole-bundle budget");
        var resources=RepeatResources.open(runtime.payload(),root.resolve("resources"),new RepeatPayload.Limits(resourceBytes,resourceMembers),check);
        var raw=RepeatStageEvidence.verify(spec,resources,root.resolve("prepared"),root.resolve("original"),check);
        Path annotation=root.resolve("original/post-input/query.fa.out"),query=root.resolve("prepared/stage/query.fa"),mask=root.resolve("original/post-input/query.fa.masked");
        RepeatAnnotations.Table table;
        if(!Files.isRegularFile(annotation,LinkOption.NOFOLLOW_LINKS)||Files.size(annotation)>capacity.bytes())throw new IOException("Missing complete original annotations");
        try(var reader=Files.newBufferedReader(annotation,StandardCharsets.UTF_8)){table=RepeatAnnotations.read(reader,annotation.toString(),RepeatAnnotations.Format.OUT_WITH_ID,spec.limits().annotations());}
        var census=RepeatFasta.read(query,RepeatFasta.Encoding.PLAIN_UTF8,RepeatInputs.stageLimits(spec.limits()),check);boolean noMask=!Files.exists(mask,LinkOption.NOFOLLOW_LINKS);long covered=0,changed=0;
        RepeatMaskAudit.validate(table.hits(),RepeatMaskAudit.Selection.all(),census,spec.limits().indexBytes(),check);
        if(noMask){if(!table.hits().isEmpty()||!table.noHitsMessage()||Files.exists(root.resolve("original/post-input/query.fa.tbl"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Original no-hit mask/table absence is not genuine");}
        else{
            if(!Files.isRegularFile(root.resolve("original/post-input/query.fa.tbl"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing positive original summary");
            var audit=RepeatMaskAudit.compare(query,RepeatFasta.Encoding.PLAIN_UTF8,mask,RepeatFasta.Encoding.PLAIN_UTF8,table.hits(),RepeatMaskAudit.Selection.all(),spec.mask(),RepeatInputs.stageLimits(spec.limits()),spec.limits().indexBytes(),check);covered=audit.coveredBases();changed=audit.changedCharacters();
        }
        if(whole(result,"annotation_rows")!=table.hits().size()||whole(result,"query_records")!=census.sequences().size()||whole(result,"query_bases")!=census.residues()||whole(result,"compared_bases")!=census.residues()
                ||whole(result,"covered_bases")!=covered||whole(result,"changed_characters")!=changed||!Boolean.valueOf(noMask).equals(result.get("original_no_hit_mask_and_table_absence"))
                ||!java.util.Objects.equals(noMask?null:"original/post-input/query.fa.masked",result.get("original_mask"))||!"original/post-input/query.fa.out".equals(result.get("original_annotation"))
                ||!("original/post-input/"+RepeatOriginalStages.rawName(census.residues())).equals(result.get("raw_search")))throw new IOException("Original result counters/output/no-hit claims differ from complete native audits");
        var finalCache=RepeatBundle.object(root.resolve("original/metadata/generated-cache.json"),4194304);
        if(!RepeatBundle.object(RepeatBundle.json(resources.generatedCache(check))).equals(finalCache))throw new IOException("Final complete original resource cache changed");
        if(!markerHash.equals(RepeatInputs.hash(root.resolve("COMPLETED.json"),65536,check))||!AdmixtureInputs.digest(manifestRaw).equals(RepeatInputs.hash(root.resolve("manifest.json"),RepeatRuntime.METADATA,check)))throw new IOException("Original completion metadata changed during verification");
        var inventory=RepeatBundle.inventory(root,capacity,false,check);return new Verification("SEARCH",RepeatSearch.SCOPE,inventory.bytes(),inventory.members(),table.hits().size(),raw.bases(),noMask);
    }
    private static long whole(Map<?,?> map,String key)throws IOException{if(!(map.get(key) instanceof Long value))throw new IOException("Require original literal integer: "+key);return value;}
    private static String text(Map<?,?> map,String key)throws IOException{if(!(map.get(key) instanceof String value)||value.isBlank())throw new IOException("Require original evidence text: "+key);return value;}
}
