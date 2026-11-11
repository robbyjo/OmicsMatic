/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Reviewed immutable saved-model bytes. This class never deserializes or executes a model. */
final class ScviModelBytes {
    private ScviModelBytes() { }
    static final class TrustRequired extends IOException {private static final long serialVersionUID=1L;TrustRequired(String m){super(m);}}
    record Review(String reviewer,String origin,String decision,String expectedSha256,boolean permitsPickleCodeExecution) {
        Review{for(String s:List.of(reviewer,origin,decision))if(s.isBlank()||s.length()>16_384||s.indexOf('\0')>=0)throw new IllegalArgumentException("Explicit bounded model-source review required");Objects.requireNonNull(expectedSha256);if(!expectedSha256.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Explicit reviewed model SHA256 required");expectedSha256=expectedSha256.toLowerCase(Locale.ROOT);}
    }
    record Limits(long modelBytes,long workspaceBytes,int members,long retainedBytes) {
        Limits{if(modelBytes<1||workspaceBytes<1||members<5||retainedBytes<131_072)throw new IllegalArgumentException("Positive complete saved-model preparation capacities required");}
        static Limits standard(){return new Limits(2_147_483_648L,4_294_967_296L,64,4_194_304);}
    }
    record Prepared(Path original,Path directory,Review review,ScviModelRequest.Release declaredRelease,
                    ScviModelRequest.Family declaredFamily,long modelBytes,Limits limits,Map<String,String> fileHashes) {
        Prepared{fileHashes=Map.copyOf(fileHashes);}
        Path model(){return directory.resolve("model.pt");}
        void verify(Runnable check)throws IOException{if(!review.permitsPickleCodeExecution)throw new IOException("Model trust review is absent");if(!ScviText.hash(original,limits.modelBytes,check).equals(review.expectedSha256)||!ScviText.hash(model(),limits.modelBytes,check).equals(review.expectedSha256)||!inventory(directory,modelBytes,limits,check).equals(fileHashes))throw new IOException("Reviewed model source,protected bytes or complete member population changed");}
    }
    static Prepared prepare(Path supplied,Review review,ScviModelRequest.Release release,ScviModelRequest.Family family,Path output,Limits limits,Runnable check)throws IOException{
        if(review==null||!review.permitsPickleCodeExecution)throw new TrustRequired("Saved scVI model trust is not established. Required action: review the exact source and SHA256 and explicitly permit upstream pickle code execution,or leave dependent model loading skipped. A checksum is integrity,not authenticity.");
        Objects.requireNonNull(release);Objects.requireNonNull(family);Objects.requireNonNull(limits);Objects.requireNonNull(check);
        long retained=131_072L+16L*(review.reviewer.length()+review.origin.length()+review.decision.length()+supplied.toString().length()+output.toString().length());if(retained>limits.retainedBytes)throw new IOException("Complete saved-model review/copy/manifest retained capacity exceeded");
        Path source=ScviText.ordinary(supplied);long bytes=Files.size(source);if(bytes<1||bytes>limits.modelBytes)throw new IOException("Selected saved-model byte population exceeds declared capacity");if(!ScviText.hash(source,limits.modelBytes,check).equals(review.expectedSha256))throw new IOException("Selected model SHA256 differs from the explicit source review");
        Path out=output.toAbsolutePath().normalize();if(out.equals(source)||source.startsWith(out))throw new IOException("Protected model needs a NEW directory separate from its source");var work=new ScviWorkspace(out,limits.workspaceBytes,limits.members,bytes,1,check);
        long copied=0;try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var dest=work.output("model.pt")){byte[] buffer=new byte[65_536];int n;while((n=in.read(buffer))>=0){check.run();copied=Math.addExact(copied,n);if(copied>limits.modelBytes)throw new IOException("Saved model grew beyond declared capacity");dest.write(buffer,0,n);}}
        if(copied!=bytes||!ScviText.hash(source,limits.modelBytes,check).equals(review.expectedSha256)||!ScviText.hash(out.resolve("model.pt"),limits.modelBytes,check).equals(review.expectedSha256))throw new IOException("Reviewed source or protected model changed during copying");
        var receipt=new TreeMap<String,Object>();receipt.put("status","PREPARED_REVIEWED_MODEL_BYTES_ONLY");receipt.put("format","UPSTREAM_TORCH_PICKLE");receipt.put("original",source.toString());receipt.put("model_bytes",bytes);receipt.put("model_sha256",review.expectedSha256);receipt.put("declared_release",release.version);receipt.put("declared_family",family.name());receipt.put("review",Map.of("reviewer",review.reviewer,"origin",review.origin,"decision",review.decision,"permits_pickle_code_execution",true));receipt.put("source_review_is_authentication",false);receipt.put("runtime_admission_required",true);receipt.put("upstream_model_semantics_verified",false);receipt.put("model_analysis_completed",false);
        work.text("model-review.json",ScviJson.json(receipt)+"\n");work.text("WARNINGS.md","# REVIEWED MODEL BYTES ONLY\n\nThe original PyTorch/scVI pickle loader can execute code. The named source review explicitly permits loading exactly the recorded SHA256; a checksum does not establish authenticity.\n\nRequired action: admit the exact runtime,inspect the original model's actual family,release,gene order,registries and parameters,and compare them with the request before neural analysis. This byte preparation does not verify those semantics or complete an analysis. If trust cannot be established,keep dependent model loading skipped.\n");work.refresh();
        Prepared result=new Prepared(source,out,review,release,family,bytes,limits,inventory(out,bytes,limits,check));result.verify(check);return result;
    }
    private static Map<String,String> inventory(Path root,long sourceBytes,Limits limits,Runnable check)throws IOException{
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.toRealPath().equals(root))throw new IOException("Protected model directory is not canonical");var hashes=new TreeMap<String,String>();long bytes=sourceBytes;int members=2;
        try(var entries=Files.list(root)){for(var it=entries.iterator();it.hasNext();){Path p=it.next();check.run();if(++members>limits.members)throw new IOException("Complete source/protected-model member capacity exceeded");if(!Set.of("model.pt","model-review.json","WARNINGS.md").contains(p.getFileName().toString()))throw new IOException("Unexpected protected-model member: "+p);ScviText.ordinary(p);bytes=Math.addExact(bytes,Files.size(p));if(bytes>limits.workspaceBytes)throw new IOException("Complete source/protected-model workspace capacity exceeded");hashes.put(p.getFileName().toString(),ScviText.hash(p,limits.workspaceBytes,check));}}
        if(!hashes.keySet().equals(Set.of("model.pt","model-review.json","WARNINGS.md")))throw new IOException("Incomplete protected-model member population");return hashes;
    }
}
