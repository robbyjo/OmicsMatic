/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.jlinalg.study.StudyGseaPolicy;

/** Actual ranking and collection contents enter native GSEA cache identity. */
public final class StudyGseaResources {
    private StudyGseaResources() { }
    public static Map<String,String> inputHashes(Path root,Map<String,Object> study,Map<String,Object> operation) throws IOException {
        var hashes=new LinkedHashMap<String,String>();if(!"ranked_gsea".equals(operation.get("type"))||operation.containsKey("supplied_result_id"))return hashes;
        var hypothesis=StudyPublicEvidence.list(study.get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(h->h.get("id").equals(operation.get("hypothesis"))).findFirst().orElseThrow();var p=StudyGseaPolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(hypothesis.get("analysis")).get("gsea")));
        if(p.containsKey("ranking")){Path file=root.resolve(String.valueOf(StudyPublicEvidence.map(p.get("ranking")).get("file")));hashes.put("gsea:ranking",Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}
        if(p.containsKey("null_rankings")){Path file=root.resolve(String.valueOf(StudyPublicEvidence.map(p.get("null_rankings")).get("file")));hashes.put("gsea:null_rankings",Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}
        if(p.containsKey("gene_sets")){Path file=root.resolve(String.valueOf(p.get("gene_sets")));hashes.put("gsea:gene_sets",Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");return hashes;}
        Path directory=StudyResourceManager.resourceRoot(root,study).resolve("enrichment/"+("Reactome".equals(p.get("collection"))?"reactome-human":"go-human")).toAbsolutePath().normalize();Path manifest=directory.resolve("manifest.properties");hashes.put("gsea:manifest",Files.isRegularFile(manifest)?FollowupSupport.hash(manifest):"missing");
        if(Files.isRegularFile(manifest)){var metadata=new Properties();try(var reader=Files.newBufferedReader(manifest)){metadata.load(reader);}for(String key:metadata.stringPropertyNames())if(key.startsWith("sha256.")){Path file=directory.resolve(key.substring(7)).normalize();if(!file.startsWith(directory))throw new IOException("Unsafe GSEA collection manifest path");hashes.put("gsea:"+key,Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}}
        return hashes;
    }
}
