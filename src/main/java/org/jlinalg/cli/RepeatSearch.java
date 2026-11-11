/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Admitted original SEARCH with complete before/after host and shared owned-budget accounting. */
final class RepeatSearch {
    private RepeatSearch() { }
    static final long FINAL_BYTES=2*RepeatRuntime.METADATA;
    static final int FINAL_MEMBERS=16;
    record Result(RepeatRuntime.Context runtime,RepeatOriginalStages.Result stages,RepeatInputs.Prepared input,
            RepeatResources.Prepared resources) { }
    static Result execute(RepeatInputs.Prepared input,Path runtimeHome,Path root,RepeatBundle.Limits capacity,Runnable check)throws IOException{
        RepeatPayload.originalPath(root);var spec=input.spec();if(spec.operation()!=RepeatRecipe.Operation.SEARCH)throw new IOException("Original workflow requires SEARCH");
        var initial=RepeatBundle.inventory(root,capacity,false,check);reserveHosts(initial,capacity);
        var runtime=RepeatRuntime.open(runtimeHome,spec.profile(),root.resolve("runtime-before"),check);
        var before=new RepeatOriginalStages.OutputFence(root.resolve("runtime-before"),capacity,RepeatBundle.inventory(root.resolve("runtime-before"),capacity,true,check));
        var used=RepeatBundle.inventory(root,capacity,false,check);
        long resourceBytes=capacity.bytes()-used.bytes()-RepeatRuntime.HOST.process().workspaceBytes()-FINAL_BYTES;
        int resourceMembers=capacity.members()-used.members()-RepeatRuntime.HOST.process().fileCount()-FINAL_MEMBERS;
        if(resourceBytes<65536||resourceMembers<128)throw new IOException("Whole original workflow cannot reserve source resources,second host capture and final evidence");
        var resources=RepeatResources.prepare(runtime.payload(),root.resolve("resources"),new RepeatPayload.Limits(resourceBytes,resourceMembers),check);
        used=RepeatBundle.inventory(root,capacity,false,check);
        var inputs=RepeatBundle.inventory(input.work(),capacity,false,check);var resourceUse=RepeatBundle.inventory(resources.work(),capacity,false,check);
        var component=component(spec,used,inputs,resourceUse,capacity);input=RepeatInputs.rebudget(input,component);
        var stages=RepeatOriginalStages.execute(input,resources,root.resolve("original"),check);
        input.verify(check);resources.verify(check);before.verify(check);spec.verify();
        var prefix=RepeatBundle.inventory(root,capacity,false,check);
        if(prefix.bytes()+RepeatRuntime.HOST.process().workspaceBytes()+FINAL_BYTES>capacity.bytes()
                ||prefix.members()+RepeatRuntime.HOST.process().fileCount()+FINAL_MEMBERS>capacity.members())throw new IOException("Original workflow cannot retain its final full host verification and evidence");
        runtime.verify(root.resolve("runtime-after"),check);
        before.verify(check);input.verify(check);resources.verify(check);RepeatBundle.inventory(root,capacity,false,check);
        return new Result(runtime,stages,input,resources);
    }
    static void reserveHosts(RepeatBundle.Inventory used,RepeatBundle.Limits capacity)throws IOException{
        if(used.bytes()+2*RepeatRuntime.HOST.process().workspaceBytes()+FINAL_BYTES>capacity.bytes()
                ||used.members()+2*RepeatRuntime.HOST.process().fileCount()+FINAL_MEMBERS+128>capacity.members())
            throw new IOException("Original SEARCH needs room for two complete host captures plus inputs/resources/final evidence;increase reviewed limits.workspace_bytes and limits.file_count (normally at least100000 members),then use a NEW output. No original search started.");
    }
    static RepeatRecipe.Spec component(RepeatRecipe.Spec spec,RepeatBundle.Inventory used,RepeatBundle.Inventory input,RepeatBundle.Inventory resource,RepeatBundle.Limits capacity)throws IOException{
        long unrelated=used.bytes()-input.bytes()-resource.bytes();int unrelatedMembers=used.members()-input.members()-resource.members();
        long bytes=capacity.bytes()-unrelated-RepeatRuntime.HOST.process().workspaceBytes()-FINAL_BYTES;
        int members=capacity.members()-unrelatedMembers-RepeatRuntime.HOST.process().fileCount()-FINAL_MEMBERS;
        if(bytes<1048576||members<128)throw new IOException("Original search component cannot reserve full final host/metadata capacity");
        var l=spec.limits();var limits=new RepeatRecipe.Limits(l.fasta(),l.annotations(),l.inputBytes(),l.indexBytes(),l.retainedBytes(),bytes,members);
        return new RepeatRecipe.Spec(spec.recipe(),spec.recipeSha256(),spec.resourceRoot(),spec.operation(),spec.mode(),spec.profile(),spec.assembly(),spec.review(),spec.query(),spec.library(),spec.annotations(),spec.masked(),spec.species(),spec.staging(),spec.mask(),spec.selection(),spec.parameters(),limits,spec.process());
    }
    static Map<String,Boolean> flags(){return Map.of("upstream_search_completed",true,"original_postprocessing_completed",true,"complete_query_population_audited",true,"biological_absence_certified",false,"whole_paper_certified",false);}
    static final String SCOPE="completed-pinned-original-search-and-whole-query-output-audit";
}
