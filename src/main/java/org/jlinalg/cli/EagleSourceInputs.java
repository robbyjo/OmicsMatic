/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Protected BCF wire sources, opaque source-index provenance and audited VCF conversion. */
final class EagleSourceInputs {
    private EagleSourceInputs() { }
    record Role(String name,EagleRecipe.Source declared,Path data,Path protectedBinary,Path protectedIndex,
            boolean sourceIndexConsumed,boolean sourceIndexCorrespondenceCertified,EagleBcf.Report conversion) { }
    record Prepared(Path work,Map<String,Role> roles,List<EagleCohortInputs.Snapshot> snapshots,
            Map<Path,String> ownedSha256,List<String> warnings,long byteLimit,long sourceByteLimit) {
        Prepared { roles=Map.copyOf(roles);snapshots=List.copyOf(snapshots);ownedSha256=Map.copyOf(ownedSha256);warnings=List.copyOf(warnings); }
        Path data(String role) { Role value=roles.get(role);return value==null?null:value.data(); }
        void verify(Runnable check)throws IOException {
            for(var snapshot:snapshots)
                if(!EagleCohortInputs.digest(snapshot.original(),sourceByteLimit,check).equals(snapshot.sha256())
                        ||!EagleCohortInputs.digest(snapshot.copy(),sourceByteLimit,check).equals(snapshot.sha256()))throw new IOException("BCF original binary/index or protected snapshot changed: "+snapshot.original());
            for(var entry:ownedSha256.entrySet())if(!EagleCohortInputs.digest(entry.getKey(),byteLimit,check).equals(entry.getValue()))throw new IOException("BCF conversion or source ledger changed: "+entry.getKey());
            if(work!=null)EagleCohortInputs.workspace(work,byteLimit);check.run();
        }
    }
    static Prepared prepare(EagleRecipe.Spec recipe,Path newWork,Runnable check)throws IOException {
        var roles=new LinkedHashMap<String,Role>();var snapshots=new ArrayList<EagleCohortInputs.Snapshot>();var owned=new LinkedHashMap<Path,String>();var warnings=new ArrayList<String>();
        boolean required=recipe.inputSources().values().stream().anyMatch(source->source.format().equals("bcf"));Path work=null;
        if(required){work=newWork.toAbsolutePath().normalize();if(!work.getParent().toRealPath().equals(work.getParent()))throw new IOException("BCF intake requires canonical existing parent and new directory");Files.createDirectory(work);}
        var budget=new EagleCohortInputs.Budget(recipe.workspaceBytes());long used=0;
        for(String name:List.of("target","reference","exclusion")) {
            EagleRecipe.Source source=recipe.inputSources().get(name);if(source==null)continue;
            if(source.format().equals("vcf")){roles.put(name,new Role(name,source,source.path(),null,null,false,false,null));continue;}
            Path directory=Files.createDirectory(work.resolve(name));
            var binary=EagleCohortInputs.snapshot(source.path(),directory.resolve("source.bcf"),recipe.vcfLimits().text().inputBytes(),budget,check);snapshots.add(binary);used=Math.addExact(used,binary.bytes());
            Path index=null;
            if(source.index()!=null){var snapshot=EagleCohortInputs.snapshot(source.index(),directory.resolve("source-index"),recipe.vcfLimits().text().inputBytes(),budget,check);snapshots.add(snapshot);index=snapshot.copy();used=Math.addExact(used,snapshot.bytes());
                warnings.add("SOURCE INDEX PROVENANCE ONLY: "+name+" index "+source.index()+" was protected and hashed under snapshot-only-regenerate. It is not queried and its semantic correspondence is not certified. Required action: retain its provenance; the prepared VCF's CSI is independently generated and validated.");}
            long remaining=recipe.workspaceBytes()-used;if(remaining<1)throw new IOException("BCF source copies leave no conversion workspace capacity");Path converted=directory.resolve("decoded.vcf");
            EagleBcf.Report conversion=EagleBcf.convert(binary.copy(),converted,recipe.vcfLimits(),recipe.retainedBytes(),Math.min(remaining,recipe.vcfLimits().text().inputBytes()),check);
            budget.add(conversion.outputBytes());used=Math.addExact(used,conversion.outputBytes());EagleBcf.auditConverted(converted,conversion,recipe.vcfLimits(),check);
            Path audit=directory.resolve("conversion.json");byte[] json=(EagleJson.json(conversion)+"\n").getBytes(StandardCharsets.UTF_8);budget.add(json.length);used=Math.addExact(used,json.length);Files.write(audit,json,StandardOpenOption.CREATE_NEW);
            owned.put(converted,conversion.outputSha256());owned.put(audit,EagleCohortInputs.digest(audit,recipe.workspaceBytes(),check));
            warnings.add("BCF SOURCE PRESERVED: "+name+" is read sequentially from its protected binary; only the declared index is protected, and no original index is used for decoding. A complete independent VCF/GT audit passed. Required action: retain source-intake/, conversion warnings and snapshots alongside the prepared VCF/CSI stages.");warnings.addAll(conversion.warnings());
            roles.put(name,new Role(name,source,converted,binary.copy(),index,false,false,conversion));
        }
        var result=new Prepared(work,roles,snapshots,owned,warnings,recipe.workspaceBytes(),recipe.vcfLimits().text().inputBytes());result.verify(check);return result;
    }
    /** Enforce the combined byte footprint before launch as well as after output. */
    static void combinedWorkspace(Prepared sources,Path prepared,long maximum)throws IOException {
        long[] bytes={0};int[] files={0};
        var visitor=new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException{if(++files[0]>256)throw new IOException("Combined Eagle source/preparation file-count capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException{
                if(!attributes.isRegularFile()||++files[0]>256)throw new IOException("Combined Eagle source/preparation contains a linked/nonregular member or excessive files");bytes[0]=Math.addExact(bytes[0],attributes.size());if(bytes[0]>maximum)throw new IOException("Combined Eagle source/conversion/preparation workspace byte capacity exceeded");return FileVisitResult.CONTINUE;
            }
        };
        if(sources.work()!=null)Files.walkFileTree(sources.work(),visitor);Files.walkFileTree(prepared,visitor);
    }
}
